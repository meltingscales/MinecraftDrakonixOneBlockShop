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
// spawn point, which vanilla can nudge a few blocks away when picking safe ground). Expanding
// it is a deliberate purchase from the shop GUI's Border tab - see ShopMenu.clickMenuButton -
// each expansion costing more than the last.
// ponytail: one shared world border for the whole overworld, not per-player. Fine for
// singleplayer; real multiplayer would need virtual per-player borders (a packet-level trick).
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class Border
{
    private static final double MAX_STRAY_BLOCKS = 5.0;

    // Cost of the Nth expansion (0-indexed) is BASE * GROWTH_FACTOR^N, each purchase widens
    // the border by 2 (1 block each side). Tuned against SELL_PRICES: early purchases are a
    // few stacks of cheap goods, later ones need real automation.
    private static final long BASE_COST = 25L;
    private static final double GROWTH_FACTOR = 1.2;

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

    // How many expansions have already been bought, derived from the border's current size
    // rather than tracked separately - the WorldBorder itself is the single source of truth.
    public static int purchaseCount(WorldBorder border)
    {
        return (int) Math.round((border.getSize() - 1.0) / 2.0);
    }

    public static long costForNextExpansion(WorldBorder border)
    {
        return Math.round(BASE_COST * Math.pow(GROWTH_FACTOR, purchaseCount(border)));
    }

    // Returns whether the purchase went through (false if the player couldn't afford it).
    public static boolean tryExpand(ServerPlayer player)
    {
        WorldBorder border = player.serverLevel().getServer().overworld().getWorldBorder();
        long cost = costForNextExpansion(border);
        if (Wallet.get(player) < cost)
            return false;

        Wallet.add(player, -cost);
        border.setSize(border.getSize() + 2.0);
        return true;
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
