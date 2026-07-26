package com.drakonix.oneblockshop;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Free (no Wallet cost) random long-range teleport from the shop GUI's Expedition tab, meant to
// make resource gathering easier without having to walk out from the tiny starting border. Lands
// somewhere in a +/-RANGE square, stays there for DURATION_TICKS, then auto-returns to wherever
// the player was standing when they left. See Border.beginExpeditionHold for how the shared
// WorldBorder is temporarily grown so vanilla doesn't push/damage the player for being "outside"
// the real border while away.
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class Expedition
{
    static final int RANGE = 10_000;
    private static final long DURATION_TICKS = 10L * 60L * 20L;
    // Countdown warnings before auto-return, seconds remaining, descending - each fires once as
    // the remaining time crosses it.
    private static final int[] WARNING_SECONDS = {300, 180, 120, 60};
    // Margin beyond RANGE so landing near the edge of the roll still can't trip the 5-block
    // stray-safety-net in Border.onPlayerTick.
    private static final double SAFE_BORDER_SIZE = 2.0 * (RANGE + 64) + 1.0;
    private static final long NOT_ON_EXPEDITION = Long.MIN_VALUE;

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

    public static boolean tryTeleport(ServerPlayer player)
    {
        if (isAway(player))
            return false;

        ServerLevel overworld = player.serverLevel().getServer().overworld();
        Border.beginExpeditionHold(overworld, SAFE_BORDER_SIZE);

        player.setData(RETURN_X, player.getX());
        player.setData(RETURN_Y, player.getY());
        player.setData(RETURN_Z, player.getZ());

        RandomSource random = overworld.getRandom();
        int x = random.nextInt(RANGE * 2 + 1) - RANGE;
        int z = random.nextInt(RANGE * 2 + 1) - RANGE;
        BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

        player.teleportTo(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
        player.setData(END_TICK, overworld.getGameTime() + DURATION_TICKS);
        player.setData(NEXT_WARNING, 0);

        player.sendSystemMessage(Component.literal(
                "Teleported to a random spot! You'll be returned to base in 10 minutes.").withStyle(ChatFormatting.AQUA));
        return true;
    }

    private static void returnHome(ServerPlayer player)
    {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        double x = player.getData(RETURN_X);
        double y = player.getData(RETURN_Y);
        double z = player.getData(RETURN_Z);

        player.teleportTo(x, y, z);
        player.setData(END_TICK, NOT_ON_EXPEDITION);
        Border.endExpeditionHold(overworld);

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
            returnHome(player);
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
}
