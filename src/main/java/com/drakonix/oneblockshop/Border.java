package com.drakonix.oneblockshop;

import com.mojang.serialization.Codec;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The "vertical border" from the README: a vanilla WorldBorder, starting at exactly 1 block
// wide and centered on wherever the first player actually spawns (not the world's nominal
// spawn point, which vanilla can nudge a few blocks away when picking safe ground). Selling
// grows it - see Wallet.add, the single place currency is earned.
// ponytail: one shared world border for the whole overworld, not per-player. Fine for
// singleplayer; real multiplayer would need virtual per-player borders (a packet-level trick).
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class Border
{
    private static final long CURRENCY_PER_EXPANSION = 50L;
    private static final double MAX_STRAY_BLOCKS = 5.0;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> INITIALIZED = ATTACHMENTS.register(
            "border_initialized", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    private Border() {}

    public static void initIfNeeded(ServerPlayer player)
    {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        if (overworld.getData(INITIALIZED))
            return;
        overworld.setData(INITIALIZED, true);

        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(player.getX(), player.getZ());
        border.setSize(1.0);
    }

    public static void onEarned(ServerPlayer player, long totalEarned)
    {
        WorldBorder border = player.serverLevel().getServer().overworld().getWorldBorder();
        double targetSize = 1.0 + 2.0 * (totalEarned / CURRENCY_PER_EXPANSION);
        if (targetSize > border.getSize())
            border.setSize(targetSize);
    }

    // Safety net against straying past the border (bugs, other mods, admin teleports) - vanilla
    // already pushes/damages players at the edge, this just guarantees they can't drift far.
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide)
            return;

        ServerLevel overworld = player.serverLevel().getServer().overworld();
        if (player.serverLevel() != overworld)
            return;

        WorldBorder border = overworld.getWorldBorder();
        if (border.getDistanceToBorder(player) < -MAX_STRAY_BLOCKS)
            player.teleportTo(border.getCenterX(), player.getY(), border.getCenterZ());
    }
}
