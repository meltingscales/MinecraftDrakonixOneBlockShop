package com.drakonix.oneblockshop;

import com.mojang.serialization.Codec;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Per-player economy/difficulty options exposed on the shop GUI's Settings tab. Permanent Hard
// Mode is a one-way lock a player can voluntarily throw on themselves - once set, price
// randomization and expedition-time can no longer be changed except by an op
// (AdminCommands' "hardmode unlock"), so a player who wants to commit to whatever they picked
// has a real way to remove the temptation to soften it later.
public final class PlayerSettings
{
    public static final int MIN_EXPEDITION_MINUTES = 1;
    public static final int MAX_EXPEDITION_MINUTES = 60;
    public static final int EXPEDITION_MINUTES_STEP = 5;
    private static final int DEFAULT_EXPEDITION_MINUTES = 10;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> PRICE_RANDOMIZATION = ATTACHMENTS.register(
            "price_randomization", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> EXPEDITION_MINUTES = ATTACHMENTS.register(
            "expedition_minutes", () -> AttachmentType.builder(() -> DEFAULT_EXPEDITION_MINUTES).serialize(Codec.INT).copyOnDeath().build());
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HARD_MODE_LOCKED = ATTACHMENTS.register(
            "hard_mode_locked", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private PlayerSettings() {}

    public static boolean isPriceRandomizationEnabled(Player player)
    {
        return player.getData(PRICE_RANDOMIZATION);
    }

    public static int getExpeditionMinutes(Player player)
    {
        return player.getData(EXPEDITION_MINUTES);
    }

    public static boolean isHardModeLocked(Player player)
    {
        return player.getData(HARD_MODE_LOCKED);
    }

    // False (no-op) if hard mode is locked - callers don't need their own separate lock check
    // before every settings mutation.
    public static boolean trySetPriceRandomization(ServerPlayer player, boolean enabled)
    {
        if (isHardModeLocked(player))
            return false;
        player.setData(PRICE_RANDOMIZATION, enabled);
        return true;
    }

    public static boolean tryAdjustExpeditionMinutes(ServerPlayer player, int delta)
    {
        if (isHardModeLocked(player))
            return false;
        int next = getExpeditionMinutes(player) + delta;
        next = Math.max(MIN_EXPEDITION_MINUTES, Math.min(MAX_EXPEDITION_MINUTES, next));
        player.setData(EXPEDITION_MINUTES, next);
        return true;
    }

    // One-way: a player can always turn this ON themselves (that's the whole point - a
    // voluntary commitment device), but never OFF - only adminUnlock can do that.
    public static boolean tryEnableHardMode(ServerPlayer player)
    {
        if (isHardModeLocked(player))
            return false;
        player.setData(HARD_MODE_LOCKED, true);
        return true;
    }

    // For AdminCommands' "hardmode unlock" subcommand only - the escape hatch the player
    // themselves can't reach anymore once locked.
    public static void adminUnlock(ServerPlayer player)
    {
        player.setData(HARD_MODE_LOCKED, false);
    }
}
