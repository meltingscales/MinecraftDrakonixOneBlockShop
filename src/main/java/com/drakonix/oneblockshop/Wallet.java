package com.drakonix.oneblockshop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

    // Credits owed to a player who was offline when their shop block sold something (hopper
    // sales keep running while nobody's around). Level-scoped rather than a raw playerdata file
    // write - safer, and flushed into the real Wallet balance the next time that player logs in
    // (StarterKit.onLogin), through the same live-object Wallet.add path as any other credit.
    private static final Codec<Map<UUID, Long>> PENDING_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<UUID, Long>>> PENDING_CREDITS = ATTACHMENTS.register(
            "pending_credits", () -> AttachmentType.<Map<UUID, Long>>builder(() -> new HashMap<>()).serialize(PENDING_CODEC).build());

    private Wallet() {}

    // Called from ShopBlockEntity.trySell when the owner is offline - the sale still happens,
    // payment is just deferred instead of refusing the sale outright.
    public static void creditOffline(ServerLevel level, UUID owner, long amount)
    {
        Map<UUID, Long> pending = new HashMap<>(level.getData(PENDING_CREDITS));
        pending.merge(owner, amount, Long::sum);
        level.setData(PENDING_CREDITS, pending);
    }

    // Called on every login (StarterKit.onLogin) - pays out anything owed from sales made while
    // this player was offline.
    public static void flushPendingCredits(ServerPlayer player)
    {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        Map<UUID, Long> pending = overworld.getData(PENDING_CREDITS);
        Long owed = pending.get(player.getUUID());
        if (owed == null)
            return;

        Map<UUID, Long> remaining = new HashMap<>(pending);
        remaining.remove(player.getUUID());
        overworld.setData(PENDING_CREDITS, remaining);
        add(player, owed);
    }

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
