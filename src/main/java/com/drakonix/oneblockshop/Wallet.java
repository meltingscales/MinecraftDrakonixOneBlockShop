package com.drakonix.oneblockshop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Balance is physical: the sum of Drakonix OneBlockShop Token items (OneBlockShopMod.
// TOKEN_DENOMINATIONS, powers of two 1..8192) sitting in a player's inventory, not an attachment
// counter. Only the internals here changed to make that true - get()/add()/creditOffline()/
// flushPendingCredits() are the same four methods this class had when balance was a plain long
// attachment, so Border/ShopMenu/AdminCommands/ShopBlockEntity/StarterKit needed zero changes.
public final class Wallet
{
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);

    // Credits owed to a player who was offline when their shop block sold something (hopper
    // sales keep running while nobody's around) - can't mint items into an inventory that isn't
    // loaded, so this still has to be a plain number, exactly as before tokens existed. Flushed
    // into real tokens the next time that player logs in (StarterKit.onLogin), through the same
    // live-object add() path as any other credit.
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

    // Sum of every token's (denomination value * stack count) across the player's main inventory
    // (hotbar + main, not armor/offhand - tokens are never worn).
    public static long get(Player player)
    {
        long total = 0L;
        // .items specifically (hotbar + main, 36 slots) rather than getContainerSize()/getItem(),
        // which also walk the armor and offhand compartments - tokens sitting in either of those
        // are an edge case a player could contrive, not one worth counting as spendable balance.
        for (ItemStack stack : player.getInventory().items)
            total += OneBlockShopMod.tokenValue(stack.getItem()) * stack.getCount();
        return total;
    }

    // amount may be negative to spend (see Border.tryExpand) - callers are expected to check
    // affordability first (get(player) >= -amount), same contract as before this was
    // attachment-backed.
    //
    // Rather than tracking which specific token stacks a purchase "breaks" and solving
    // change-making against whatever mix the player happens to be holding, every call just
    // removes ALL token items from the inventory and re-mints the resulting total from scratch
    // via greedy denomination decomposition. For a canonical system like powers of two, greedy
    // is always the optimal (fewest-tokens) representation, so this guarantees minimal holdings
    // after every transaction without needing a real change-making solver.
    public static long add(Player player, long amount)
    {
        long newBalance = Math.max(0L, get(player) + amount);
        removeAllTokens(player);
        mint(player, newBalance);
        return newBalance;
    }

    private static void removeAllTokens(Player player)
    {
        NonNullList<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++)
            if (OneBlockShopMod.tokenValue(items.get(i).getItem()) > 0)
                items.set(i, ItemStack.EMPTY);
    }

    private static void mint(Player player, long amount)
    {
        for (ItemStack stack : mintTokens(player.level().registryAccess(), amount))
            giveOrDrop(player, stack);
    }

    // Also used by ShopBlockEntity to physically bank hopper-sale proceeds into an adjacent
    // chest instead of the owner's inventory - same greedy denomination decomposition either way.
    public static List<ItemStack> mintTokens(HolderLookup.Provider registries, long amount)
    {
        List<ItemStack> stacks = new ArrayList<>();
        long[] counts = TokenDenominations.decompose(amount);
        for (int i = 0; i < TokenDenominations.DESCENDING.length; i++)
        {
            long value = TokenDenominations.DESCENDING[i];
            int count = (int) counts[i];
            for (int given = 0; given < count; )
            {
                int stackSize = Math.min(64, count - given);
                stacks.add(cursedToken(registries, value, stackSize));
                given += stackSize;
            }
        }
        return stacks;
    }

    private static void giveOrDrop(Player player, ItemStack stack)
    {
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    // Unsellable, same as the starter kit's items (StarterKit.cursedUnsellable) - otherwise nothing
    // stops selling a token back through the shop for more tokens.
    private static ItemStack cursedToken(HolderLookup.Provider registries, long value, int count)
    {
        ItemStack stack = new ItemStack(OneBlockShopMod.tokenItem(value), count);
        EnchantmentHelper.updateEnchantments(stack, mutable ->
                mutable.set(registries.holderOrThrow(OneBlockShopMod.UNSELLABLE), 1));
        return stack;
    }

    // ponytail: only reads the balance if the owner is currently online (tokens live in their
    // inventory); an offline owner just reads as 0 here. Fine for singleplayer/LAN.
    public static long get(MinecraftServer server, @Nullable UUID uuid)
    {
        if (uuid == null)
            return 0L;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player == null ? 0L : get(player);
    }
}
