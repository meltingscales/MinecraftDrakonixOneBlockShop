package com.drakonix.oneblockshop;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The "vertical border" from the README: a vanilla WorldBorder pinned to spawn, starting at
// exactly 1 block wide. Selling grows it - see Wallet.add, the single place currency is earned.
// ponytail: one shared world border for the whole overworld, not per-player. Fine for
// singleplayer; real multiplayer would need virtual per-player borders (a packet-level trick).
public final class Border
{
    private static final long CURRENCY_PER_EXPANSION = 50L;

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

        BlockPos spawn = overworld.getSharedSpawnPos();
        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(spawn.getX() + 0.5, spawn.getZ() + 0.5);
        border.setSize(1.0);
    }

    public static void onEarned(ServerPlayer player, long totalEarned)
    {
        WorldBorder border = player.serverLevel().getServer().overworld().getWorldBorder();
        double targetSize = 1.0 + 2.0 * (totalEarned / CURRENCY_PER_EXPANSION);
        if (targetSize > border.getSize())
            border.setSize(targetSize);
    }
}
