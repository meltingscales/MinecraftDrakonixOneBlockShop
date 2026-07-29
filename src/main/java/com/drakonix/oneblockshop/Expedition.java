package com.drakonix.oneblockshop;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
    private static final long PORTAL_DURATION_TICKS = 30L * 20L;
    // How often the vanilla potion-icon countdown gets forced back in sync with END_TICK - see
    // onPlayerTick.
    private static final long RESYNC_INTERVAL_TICKS = 5L * 20L;
    // Countdown warnings before auto-return, seconds remaining, descending - each fires once as
    // the remaining time crosses it.
    private static final int[] WARNING_SECONDS = {300, 180, 120, 60};
    private static final long NOT_ON_EXPEDITION = Long.MIN_VALUE;
    private static final long PORTAL_INACTIVE = Long.MIN_VALUE;
    // Deliberately equal to PORTAL_DURATION_TICKS: returnHome puts the player right back where
    // they teleported from, which is often still inside (or right next to) the very portal that's
    // still open - without immunity lasting at least that long, walking away from a drunk return
    // potion could immediately suck them right back through it. See RETURN_EFFECT/
    // PORTAL_IMMUNITY_EFFECT and the onLevelTick walk-in check below.
    private static final long PORTAL_IMMUNITY_TICKS = 30L * 20L;
    private static final long RESUME_INVINCIBILITY_TICKS = 30L * 20L;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);
    // .copyOnDeath() on all these: a serializable attachment is NOT copied onto the new player
    // entity a death/respawn creates by default (only .serialize() alone, which is just for
    // surviving a chunk unload/server restart on the *same* entity) - without it, dying
    // mid-expedition would silently reset isAway() to false on respawn (the attachment's default
    // value), leaving Border.EXPEDITIONS_ACTIVE permanently incremented (same bug class
    // onPlayerLogout's watchdog exists for) and losing the "away" status.
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> END_TICK = ATTACHMENTS.register(
            "expedition_end_tick", () -> AttachmentType.builder(() -> NOT_ON_EXPEDITION).serialize(Codec.LONG).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> RETURN_X = ATTACHMENTS.register(
            "expedition_return_x", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> RETURN_Y = ATTACHMENTS.register(
            "expedition_return_y", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> RETURN_Z = ATTACHMENTS.register(
            "expedition_return_z", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> NEXT_WARNING = ATTACHMENTS.register(
            "expedition_next_warning", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());
    // Where the player actually died - captured by onPlayerDeath (fires on the *old*, still-valid
    // entity) so RESUME_EFFECT can send them back there later. Deliberately separate from
    // RETURN_X/Y/Z (the expedition's *origin*, at home) - resuming should mean "back to where I
    // died", not "back to base".
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> DEATH_X = ATTACHMENTS.register(
            "expedition_death_x", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> DEATH_Y = ATTACHMENTS.register(
            "expedition_death_y", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> DEATH_Z = ATTACHMENTS.register(
            "expedition_death_z", () -> AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build());

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
    // Just a marker, same idiom as EFFECT above - checked in onLevelTick's portal walk-in test so
    // a player who just drank a return potion can't be immediately sucked back through the still-
    // open portal they may still be standing in/next to.
    public static final DeferredHolder<MobEffect, MobEffect> PORTAL_IMMUNITY_EFFECT = EFFECTS.register(
            "portal_immunity", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xAAAAFF) {});
    // Instantaneous (applies once, immediately, like vanilla's Instant Health) rather than a
    // regular timed effect - drinking the return potion should end the expedition right away, not
    // over time. Real vanilla base class (Instant Health/Harm use the same one), not guessed.
    public static final DeferredHolder<MobEffect, MobEffect> RETURN_EFFECT = EFFECTS.register(
            "expedition_return", () -> new InstantenousMobEffect(MobEffectCategory.BENEFICIAL, 0x55AAFF)
            {
                @Override
                public boolean applyEffectTick(LivingEntity livingEntity, int amplifier)
                {
                    if (livingEntity instanceof ServerPlayer player && isAway(player))
                        returnHome(player, true);
                    return true;
                }
            });
    // Also instantaneous - drinking this sends the player back to exactly where they died, so
    // they can carry on the same expedition instead of it silently ending. Checks isAway() at
    // drink time (not just at death) since the expedition may have since expired on its own
    // (onPlayerTick's normal timeout) or been ended manually (/expedition end, the return
    // potion) - in either case there's nothing to resume, so this just says so and does nothing,
    // rather than teleporting into a stale/out-of-sync state.
    public static final DeferredHolder<MobEffect, MobEffect> RESUME_EFFECT = EFFECTS.register(
            "expedition_resume", () -> new InstantenousMobEffect(MobEffectCategory.BENEFICIAL, 0x55FFAA)
            {
                @Override
                public boolean applyEffectTick(LivingEntity livingEntity, int amplifier)
                {
                    if (livingEntity instanceof ServerPlayer player)
                    {
                        if (isAway(player))
                        {
                            player.teleportTo(player.getData(DEATH_X), player.getData(DEATH_Y), player.getData(DEATH_Z));
                            // Landing right back where they just died is otherwise a near-guaranteed
                            // repeat death (still in the mob/fall/lava that killed them) - amplifier 4
                            // is Resistance V, which zeroes out damage entirely per vanilla's own
                            // getDamageAfterMagicAbsorb (confirmed in decompiled source: reduction
                            // is (amplifier+1)*5%, capped at 100% at amplifier 4) for anything not
                            // tagged BYPASSES_RESISTANCE (void, starvation, etc. still apply).
                            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) RESUME_INVINCIBILITY_TICKS, 4, false, true));
                        }
                        else
                            player.sendSystemMessage(Component.literal(
                                    "That expedition has already ended.").withStyle(ChatFormatting.RED));
                    }
                    return true;
                }
            });

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
    public static boolean openPortal(ServerLevel overworld, BlockPos shopPos, boolean caveOnly)
    {
        if (isPortalOpen(overworld))
            return false;

        BlockPos destination = rollDestination(overworld, overworld.getRandom(), caveOnly);

        overworld.setData(PORTAL_DEST_X, destination.getX() + 0.5);
        overworld.setData(PORTAL_DEST_Y, (double) destination.getY());
        overworld.setData(PORTAL_DEST_Z, destination.getZ() + 0.5);
        overworld.setData(PORTAL_POS, shopPos.asLong());
        overworld.setData(PORTAL_END_TICK, overworld.getGameTime() + PORTAL_DURATION_TICKS);
        return true;
    }

    // Lands on the surface most of the time, but sometimes drops the traveler into a cave
    // instead (or always, in caveOnly mode) - "resource gathering" should be able to mean ore,
    // not just overworld terrain.
    private static final double CAVE_CHANCE = 0.35;
    // Rerolls the whole column if it's ocean ("resource gathering" shouldn't dump you in open
    // water with nothing to stand on) or, in caveOnly mode, if this particular column just
    // doesn't have a cave under it. Bounded so a bad RNG streak - or a genuinely all-ocean seed,
    // or a caveOnly roll over a stretch of world with nothing underground nearby - still
    // terminates instead of hanging; the last column rolled is used as a fallback rather than
    // failing outright.
    private static final int MAX_LOCATION_ATTEMPTS = 20;

    private static BlockPos rollDestination(ServerLevel overworld, RandomSource random, boolean caveOnly)
    {
        int range = Config.EXPLORE_RANGE.get();
        BlockPos lastSurface = BlockPos.ZERO;
        for (int attempt = 0; attempt < MAX_LOCATION_ATTEMPTS; attempt++)
        {
            int x = random.nextInt(range * 2 + 1) - range;
            int z = random.nextInt(range * 2 + 1) - range;
            // A random spot this far out is essentially guaranteed to be in an unloaded chunk -
            // Level.getHeight/getHeightmapPos silently falls back to getMinBuildHeight() (the
            // void floor) for a chunk that isn't loaded yet, rather than generating it, which is
            // what was dropping players out of the world. Force it to ChunkStatus.FULL first so
            // the heightmap reflects real generated terrain.
            overworld.getChunk(x >> 4, z >> 4);
            BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            lastSurface = surface;
            if (overworld.getBiome(surface).is(BiomeTags.IS_OCEAN))
                continue;

            if (!caveOnly)
            {
                if (random.nextDouble() >= CAVE_CHANCE)
                    return surface;
                BlockPos cave = findCaveLanding(overworld, x, z, surface.getY(), random);
                return cave != null ? cave : surface;
            }

            BlockPos cave = findCaveLanding(overworld, x, z, surface.getY(), random);
            if (cave != null)
                return cave;
        }
        return lastSurface;
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
            // blocksMotion() alone isn't enough - water doesn't block motion (vanilla's own
            // MOTION_BLOCKING heightmap predicate ORs in a fluid check for exactly this reason),
            // so without also requiring empty fluid state here a flooded underwater cave passage
            // would look "open" and drop the player straight into open water.
            boolean hereOpen = !overworld.getBlockState(pos).blocksMotion() && overworld.getFluidState(pos).isEmpty();
            boolean aboveOpen = !overworld.getBlockState(pos.above()).blocksMotion() && overworld.getFluidState(pos.above()).isEmpty();
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
            // A player who just drank their return potion carries PORTAL_IMMUNITY_EFFECT for a
            // while specifically so this check can't immediately grab them again - returnHome
            // puts them right back where they teleported from, often still inside this very
            // portal's hitbox.
            if (!isAway(player) && !player.hasEffect(PORTAL_IMMUNITY_EFFECT) && isStandingInPortal(player, portalPos))
                enterPortal(player, overworld, destX, destY, destZ);
        }
    }

    // Margin beyond the configured range so landing near the edge of the roll still can't trip
    // the 5-block stray-safety-net in Border.onPlayerTick. Computed fresh (not cached) since
    // Config.EXPLORE_RANGE can change via a config reload without a restart.
    private static double safeBorderSize()
    {
        return 2.0 * (Config.EXPLORE_RANGE.get() + 64) + 1.0;
    }

    // PlayerSettings.getExpeditionMinutes (configurable per player on the shop GUI's Settings
    // tab, default 10) rather than a fixed constant.
    private static long durationTicks(Player player)
    {
        return PlayerSettings.getExpeditionMinutes(player) * 60L * 20L;
    }

    private static void enterPortal(ServerPlayer player, ServerLevel overworld, double destX, double destY, double destZ)
    {
        Border.beginExpeditionHold(overworld, safeBorderSize());

        player.setData(RETURN_X, player.getX());
        player.setData(RETURN_Y, player.getY());
        player.setData(RETURN_Z, player.getZ());

        long durationTicks = durationTicks(player);
        player.teleportTo(destX, destY, destZ);
        player.setData(END_TICK, overworld.getGameTime() + durationTicks);
        player.setData(NEXT_WARNING, 0);
        // Duration matches durationTicks so the vanilla potion-icon countdown and this class's
        // own GUI/chat countdown always agree.
        player.addEffect(new MobEffectInstance(EFFECT, (int) durationTicks, 0, false, true));
        player.getInventory().add(returnPotion(player));

        player.sendSystemMessage(Component.literal(
                "Through the portal! You'll be returned to base in " + PlayerSettings.getExpeditionMinutes(player)
                        + " minutes. Drink the potion in your inventory to come back early.").withStyle(ChatFormatting.AQUA));
    }

    // A game-friendly alternative to typing "/drakonixoneblockshop expedition end": drinking this
    // ends the expedition immediately (RETURN_EFFECT, instantaneous) and grants
    // PORTAL_IMMUNITY_TICKS of immunity from re-entering a portal (PORTAL_IMMUNITY_EFFECT) so
    // landing back next to a still-open one can't suck the player straight back through it.
    // Unsellable, same curse as the starter kit's items, so it can't be sold/hoppered away by
    // accident while it's still needed.
    private static ItemStack returnPotion(ServerPlayer player)
    {
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(
                new MobEffectInstance(RETURN_EFFECT, 1, 0),
                new MobEffectInstance(PORTAL_IMMUNITY_EFFECT, (int) PORTAL_IMMUNITY_TICKS, 0))));
        // Without this, an empty PotionContents.potion() falls back to vanilla's own
        // "item.minecraft.potion.effect.empty" ("Uninteresting Potion") - ITEM_NAME (not
        // CUSTOM_NAME, which renders italic like a real anvil rename) is the supported way to
        // give a vanilla-item-based stack its own plain localized display name.
        potion.set(DataComponents.ITEM_NAME, Component.translatable("item.drakonixoneblockshop.expedition_return_potion"));
        EnchantmentHelper.updateEnchantments(potion, mutable ->
                mutable.set(player.level().registryAccess().holderOrThrow(OneBlockShopMod.UNSELLABLE), 1));
        return potion;
    }

    // Given on death instead of forcing a teleport (a forced respawn-time teleport turned out
    // unreliable in practice - see TODO.md). The player just respawns normally; drinking this
    // sends them back to their death spot (RESUME_EFFECT), checking the expedition's still valid
    // at that moment rather than assuming it still is.
    private static ItemStack resumePotion(ServerPlayer player)
    {
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(
                new MobEffectInstance(RESUME_EFFECT, 1, 0))));
        potion.set(DataComponents.ITEM_NAME, Component.translatable("item.drakonixoneblockshop.expedition_resume_potion"));
        EnchantmentHelper.updateEnchantments(potion, mutable ->
                mutable.set(player.level().registryAccess().holderOrThrow(OneBlockShopMod.UNSELLABLE), 1));
        return potion;
    }

    // For /drakonixoneblockshop devcheat expedition teleport - skips the portal (roll + walk-in)
    // entirely, straight to the same "you're now away" state a real portal entry produces.
    public static boolean devTeleport(ServerPlayer player)
    {
        if (isAway(player))
            return false;

        ServerLevel overworld = player.serverLevel().getServer().overworld();
        BlockPos destination = rollDestination(overworld, overworld.getRandom(), false);
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

        // Forces the vanilla potion-icon countdown back in sync with END_TICK. Needed because
        // MobEffectInstance.update() (what addEffect uses to merge with an existing instance of
        // the same effect) only ever extends a duration, never shrinks it - so anything that
        // moves END_TICK earlier without touching the applied effect directly (devcheat's
        // fastforward, an admin /effect command, milk bucket or death wiping the effect
        // entirely) would otherwise leave the visible icon wrong indefinitely. Remove-then-
        // reapply forces an exact match regardless of which direction it drifted.
        if (player.level().getGameTime() % RESYNC_INTERVAL_TICKS == 0L)
        {
            player.removeEffect(EFFECT);
            player.addEffect(new MobEffectInstance(EFFECT, (int) remainingTicks, 0, false, true));
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

    // Captures exactly where a player died (their own bounding position, still valid here - this
    // fires on the *old* entity before it's replaced) so RESUME_EFFECT can send them back to it
    // later. Deliberately doesn't touch isAway()/END_TICK/Border.EXPEDITIONS_ACTIVE at all - the
    // expedition just keeps running in the background exactly as if the player were still there,
    // same as if they'd logged out (except onPlayerLogout still applies if they then disconnect
    // instead of respawning).
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isAway(player))
            return;
        player.setData(DEATH_X, player.getX());
        player.setData(DEATH_Y, player.getY());
        player.setData(DEATH_Z, player.getZ());
    }

    // A forced teleport straight out of the respawn event turned out unreliable in practice (see
    // TODO.md) - instead, just let vanilla's normal respawn stand, and hand the player a potion
    // that sends them back to their death spot on demand. isAway() still reads correctly here
    // thanks to END_TICK's .copyOnDeath() - the expedition itself was never touched by dying, so
    // there's nothing to "resume" in the data model, only in where the player physically is.
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && isAway(player))
        {
            player.getInventory().add(resumePotion(player));
            player.sendSystemMessage(Component.literal(
                    "You died on your expedition. Drink the potion in your inventory to go back to"
                            + " where you died, or wait it out - you'll be auto-returned when your"
                            + " time runs out either way.").withStyle(ChatFormatting.AQUA));
        }
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
