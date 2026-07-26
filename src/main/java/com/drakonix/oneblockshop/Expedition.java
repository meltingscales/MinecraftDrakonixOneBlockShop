package com.drakonix.oneblockshop;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Free (no Wallet cost) random long-range teleport from the shop GUI's Expedition tab, meant to
// make resource gathering easier without having to walk out from the tiny starting border.
// Clicking the button doesn't teleport instantly - it opens a portal (particles-only, no real
// block) above that shop block for PORTAL_DURATION_TICKS, with one destination rolled up front.
// Anyone who walks into it before it closes goes there together - this is what makes it a "team"
// trip when more than one player happens to be nearby, with no separate team concept needed.
// Each traveler still gets their own DURATION_TICKS stay and personal auto-return to wherever
// they individually were, same as before. See Border.beginExpeditionHold for how the shared
// WorldBorder is temporarily grown so vanilla doesn't push/damage a traveler for being "outside"
// the real border while away.
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class Expedition
{
    static final int RANGE = 10_000;
    private static final long DURATION_TICKS = 10L * 60L * 20L;
    private static final long PORTAL_DURATION_TICKS = 30L * 20L;
    // Countdown warnings before auto-return, seconds remaining, descending - each fires once as
    // the remaining time crosses it.
    private static final int[] WARNING_SECONDS = {300, 180, 120, 60};
    // Margin beyond RANGE so landing near the edge of the roll still can't trip the 5-block
    // stray-safety-net in Border.onPlayerTick.
    private static final double SAFE_BORDER_SIZE = 2.0 * (RANGE + 64) + 1.0;
    private static final long NOT_ON_EXPEDITION = Long.MIN_VALUE;
    private static final long PORTAL_INACTIVE = Long.MIN_VALUE;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> END_TICK = ATTACHMENTS.register(
            "expedition_end_tick", () -> AttachmentType.builder(() -> NOT_ON_EXPEDITION).serialize(Codec.LONG).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> RETURN_X = ATTACHMENTS.register(
            "expedition_return_x", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> RETURN_Y = ATTACHMENTS.register(
            "expedition_return_y", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> RETURN_Z = ATTACHMENTS.register(
            "expedition_return_z", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> NEXT_WARNING = ATTACHMENTS.register(
            "expedition_next_warning", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    // Portal state is ServerLevel-scoped (the overworld), not per-player - only one portal open
    // at a time across the whole server. Ponytail: simplest thing that works for this mod's
    // scale; a second Teleport click while one's already open just no-ops rather than queuing or
    // replacing it.
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> PORTAL_END_TICK = ATTACHMENTS.register(
            "portal_end_tick", () -> AttachmentType.builder(() -> PORTAL_INACTIVE).serialize(Codec.LONG).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> PORTAL_POS = ATTACHMENTS.register(
            "portal_pos", () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> PORTAL_DEST_X = ATTACHMENTS.register(
            "portal_dest_x", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> PORTAL_DEST_Y = ATTACHMENTS.register(
            "portal_dest_y", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> PORTAL_DEST_Z = ATTACHMENTS.register(
            "portal_dest_z", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).build());

    // Marks a player as away so other systems (currently: blocking placing a new shop block -
    // easy to lose track of which one is "home" if you drop a second one mid-expedition) can
    // check for it without needing their own attachment. Anonymous subclass because MobEffect's
    // constructor is protected - fine, plenty of vanilla effects (e.g. Confusion) are plain
    // no-behavior instances like this too.
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, OneBlockShopMod.MODID);
    public static final DeferredHolder<MobEffect, MobEffect> EFFECT = EFFECTS.register(
            "expedition", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x55FFFF) {});

    private Expedition() {}

    public static boolean isAway(Player player)
    {
        return player.getData(END_TICK) != NOT_ON_EXPEDITION;
    }

    // Whole seconds until auto-return, 0 if not currently away - mirrors
    // Border.cooldownRemainingSeconds for the GUI to show a countdown instead of a button that
    // silently fails.
    public static long remainingSeconds(Player player)
    {
        long end = player.getData(END_TICK);
        if (end == NOT_ON_EXPEDITION)
            return 0L;
        long remainingTicks = Math.max(0L, end - player.level().getGameTime());
        return (remainingTicks + 19L) / 20L;
    }

    public static boolean isPortalOpen(ServerLevel overworld)
    {
        return overworld.getData(PORTAL_END_TICK) != PORTAL_INACTIVE;
    }

    // Whole seconds until the current portal closes, 0 if none is open - for the GUI/button state.
    public static long portalRemainingSeconds(ServerLevel overworld)
    {
        long end = overworld.getData(PORTAL_END_TICK);
        if (end == PORTAL_INACTIVE)
            return 0L;
        long remainingTicks = Math.max(0L, end - overworld.getGameTime());
        return (remainingTicks + 19L) / 20L;
    }

    // From the shop GUI's Teleport button. Rolls one shared destination and opens a portal above
    // the shop block instead of teleporting the clicker immediately - onLevelTick below is what
    // actually moves whoever walks into it. False if a portal's already open somewhere.
    public static boolean openPortal(ServerLevel overworld, BlockPos shopPos)
    {
        if (isPortalOpen(overworld))
            return false;

        RandomSource random = overworld.getRandom();
        int x = random.nextInt(RANGE * 2 + 1) - RANGE;
        int z = random.nextInt(RANGE * 2 + 1) - RANGE;
        // A random spot this far out is essentially guaranteed to be in an unloaded chunk -
        // Level.getHeight/getHeightmapPos silently falls back to getMinBuildHeight() (the void
        // floor) for a chunk that isn't loaded yet, rather than generating it, which is what was
        // dropping players out of the world. Force it to ChunkStatus.FULL first so the heightmap
        // reflects real generated terrain.
        overworld.getChunk(x >> 4, z >> 4);
        BlockPos destination = rollDestination(overworld, x, z, random);

        overworld.setData(PORTAL_DEST_X, destination.getX() + 0.5);
        overworld.setData(PORTAL_DEST_Y, (double) destination.getY());
        overworld.setData(PORTAL_DEST_Z, destination.getZ() + 0.5);
        overworld.setData(PORTAL_POS, shopPos.asLong());
        overworld.setData(PORTAL_END_TICK, overworld.getGameTime() + PORTAL_DURATION_TICKS);
        return true;
    }

    // Lands on the surface most of the time, but sometimes drops the traveler into a cave
    // instead - "resource gathering" should be able to mean ore, not just overworld terrain.
    private static final double CAVE_CHANCE = 0.35;

    private static BlockPos rollDestination(ServerLevel overworld, int x, int z, RandomSource random)
    {
        BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (random.nextDouble() >= CAVE_CHANCE)
            return surface;

        BlockPos cave = findCaveLanding(overworld, x, z, surface.getY(), random);
        return cave != null ? cave : surface;
    }

    // Scans a random column height for an air pocket (room to stand, solid footing below) -
    // finds a cave floor without needing real pathing/cave-generation knowledge. Falls back to
    // null (caller lands on the surface instead) if the sampled range turns out solid all the
    // way through, e.g. no cave actually passes under this exact column.
    private static BlockPos findCaveLanding(ServerLevel overworld, int x, int z, int surfaceY, RandomSource random)
    {
        int minY = overworld.getMinBuildHeight() + 4;
        int maxY = surfaceY - 8;
        if (maxY <= minY)
            return null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int startY = minY + random.nextInt(maxY - minY);
        for (int y = startY; y > minY; y--)
        {
            pos.setY(y);
            boolean hereOpen = !overworld.getBlockState(pos).blocksMotion();
            boolean aboveOpen = !overworld.getBlockState(pos.above()).blocksMotion();
            boolean belowSolid = overworld.getBlockState(pos.below()).blocksMotion();
            if (hereOpen && aboveOpen && belowSolid)
                return pos.immutable();
        }
        return null;
    }

    private static void closePortal(ServerLevel overworld)
    {
        overworld.setData(PORTAL_END_TICK, PORTAL_INACTIVE);
    }

    // Ambient swirl above the shop block for as long as the portal's open - purely cosmetic, no
    // real block is placed, so there's nothing to clean up beyond clearing the attachment state.
    private static void spawnPortalParticles(ServerLevel overworld, BlockPos portalPos)
    {
        overworld.sendParticles(ParticleTypes.PORTAL,
                portalPos.getX() + 0.5, portalPos.getY() + 1.5, portalPos.getZ() + 0.5,
                8, 0.3, 0.7, 0.3, 0.0);
    }

    private static boolean isStandingInPortal(ServerPlayer player, BlockPos portalPos)
    {
        AABB portalBox = new AABB(portalPos.getX(), portalPos.getY() + 1, portalPos.getZ(),
                portalPos.getX() + 1, portalPos.getY() + 3, portalPos.getZ() + 1);
        return player.getBoundingBox().intersects(portalBox);
    }

    // Drives the one active portal: particles, timeout, and walk-in detection. LevelTickEvent
    // (not PlayerTickEvent) because this has to run even with nobody currently near the portal.
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event)
    {
        if (!(event.getLevel() instanceof ServerLevel overworld) || overworld != overworld.getServer().overworld())
            return;
        if (!isPortalOpen(overworld))
            return;

        if (portalRemainingSeconds(overworld) <= 0)
        {
            closePortal(overworld);
            return;
        }

        BlockPos portalPos = BlockPos.of(overworld.getData(PORTAL_POS));
        spawnPortalParticles(overworld, portalPos);

        double destX = overworld.getData(PORTAL_DEST_X);
        double destY = overworld.getData(PORTAL_DEST_Y);
        double destZ = overworld.getData(PORTAL_DEST_Z);
        for (ServerPlayer player : overworld.players())
        {
            if (!isAway(player) && isStandingInPortal(player, portalPos))
                enterPortal(player, overworld, destX, destY, destZ);
        }
    }

    private static void enterPortal(ServerPlayer player, ServerLevel overworld, double destX, double destY, double destZ)
    {
        Border.beginExpeditionHold(overworld, SAFE_BORDER_SIZE);

        player.setData(RETURN_X, player.getX());
        player.setData(RETURN_Y, player.getY());
        player.setData(RETURN_Z, player.getZ());

        player.teleportTo(destX, destY, destZ);
        player.setData(END_TICK, overworld.getGameTime() + DURATION_TICKS);
        player.setData(NEXT_WARNING, 0);
        // Duration matches DURATION_TICKS so the vanilla potion-icon countdown and this class's
        // own GUI/chat countdown always agree.
        player.addEffect(new MobEffectInstance(EFFECT, (int) DURATION_TICKS, 0, false, true));

        player.sendSystemMessage(Component.literal(
                "Through the portal! You'll be returned to base in 10 minutes.").withStyle(ChatFormatting.AQUA));
    }

    // For /drakonixoneblockshop devcheat expedition teleport - skips the portal (roll + walk-in)
    // entirely, straight to the same "you're now away" state a real portal entry produces.
    public static boolean devTeleport(ServerPlayer player)
    {
        if (isAway(player))
            return false;

        ServerLevel overworld = player.serverLevel().getServer().overworld();
        RandomSource random = overworld.getRandom();
        int x = random.nextInt(RANGE * 2 + 1) - RANGE;
        int z = random.nextInt(RANGE * 2 + 1) - RANGE;
        overworld.getChunk(x >> 4, z >> 4);
        BlockPos destination = rollDestination(overworld, x, z, random);
        enterPortal(player, overworld, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
        return true;
    }

    // For /drakonixoneblockshop devcheat expedition fastforward - jumps straight to any point in
    // the countdown (warnings, auto-return) without actually waiting. Doesn't touch NEXT_WARNING:
    // onPlayerTick's own catch-up loop already fires every threshold still ahead of the new
    // value on its very next tick, which is useful for testing (jump from full to "5 seconds
    // left" and every warning still prints in a burst, in order).
    public static boolean devFastForward(ServerPlayer player, long remainingSeconds)
    {
        if (!isAway(player))
            return false;
        player.setData(END_TICK, player.level().getGameTime() + remainingSeconds * 20L);
        return true;
    }

    // For /drakonixoneblockshop devcheat closeportal - recovers from the "one portal at a time"
    // limitation during testing without waiting out the full 30 seconds.
    public static boolean devClosePortal(ServerLevel overworld)
    {
        if (!isPortalOpen(overworld))
            return false;
        closePortal(overworld);
        return true;
    }

    // For /drakonixoneblockshop expedition end - lets a player cut their own trip short instead
    // of waiting out the full countdown.
    public static boolean tryEndEarly(ServerPlayer player)
    {
        if (!isAway(player))
            return false;
        returnHome(player, true);
        return true;
    }

    private static void returnHome(ServerPlayer player, boolean announce)
    {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        double x = player.getData(RETURN_X);
        double y = player.getData(RETURN_Y);
        double z = player.getData(RETURN_Z);

        player.teleportTo(x, y, z);
        player.setData(END_TICK, NOT_ON_EXPEDITION);
        player.removeEffect(EFFECT);
        Border.endExpeditionHold(overworld);

        if (announce)
            player.sendSystemMessage(Component.literal("Returned to base.").withStyle(ChatFormatting.AQUA));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide)
            return;
        if (!isAway(player))
            return;

        long remainingTicks = player.getData(END_TICK) - player.level().getGameTime();
        if (remainingTicks <= 0)
        {
            returnHome(player, true);
            return;
        }

        long remainingSeconds = (remainingTicks + 19L) / 20L;
        int warned = player.getData(NEXT_WARNING);
        while (warned < WARNING_SECONDS.length && remainingSeconds <= WARNING_SECONDS[warned])
        {
            int minutes = WARNING_SECONDS[warned] / 60;
            player.sendSystemMessage(Component.literal(
                    "Returning to base in " + minutes + " minute" + (minutes == 1 ? "" : "s") + "...")
                    .withStyle(ChatFormatting.YELLOW));
            warned++;
        }
        if (warned != player.getData(NEXT_WARNING))
            player.setData(NEXT_WARNING, warned);
    }

    // Watchdog for the abandoned-expedition case: onPlayerTick above only ever runs for a player
    // who's actually online, so a player who logs out mid-expedition and never comes back would
    // otherwise leave Border's EXPEDITIONS_ACTIVE counter permanently non-zero - the shared
    // border stuck enlarged, and Border.tryExpand permanently refused, for everyone else forever.
    // Returning them home right at logout (silently - they're disconnecting, no one to read a
    // chat message) means the only way to get stuck is a true crash/power-loss, not just quitting.
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && isAway(player))
            returnHome(player, false);
    }

    // Blocks placing a *new* shop block while away, so it's obvious which one is "home" - has no
    // effect on shop blocks already placed before the expedition started.
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getPlacedBlock().getBlock() != OneBlockShopMod.SHOP_BLOCK.get())
            return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasEffect(EFFECT))
            return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal(
                "Can't place a shop block while on an expedition.").withStyle(ChatFormatting.RED));
    }
}
