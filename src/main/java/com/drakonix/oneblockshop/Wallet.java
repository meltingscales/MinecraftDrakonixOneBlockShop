package com.drakonix.oneblockshop;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Balance persists via NeoForge's data attachment system, saved with the player's own NBT.
public final class Wallet
{
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> BALANCE = ATTACHMENTS.register(
            "balance", () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    private Wallet() {}

    public static long get(Player player)
    {
        return player.getData(BALANCE);
    }

    // amount may be negative to spend (see Border.tryExpand) - callers are expected to check
    // affordability first, this doesn't clamp at zero.
    public static long add(Player player, long amount)
    {
        long newBalance = get(player) + amount;
        player.setData(BALANCE, newBalance);
        return newBalance;
    }

    // ponytail: only reads the balance if the owner is currently online (attachments live on
    // the Player object); an offline owner just reads as 0 here. Fine for singleplayer/LAN.
    public static long get(MinecraftServer server, @Nullable UUID uuid)
    {
        if (uuid == null)
            return 0L;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player == null ? 0L : get(player);
    }
}
