package com.drakonix.oneblockshop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Every 5 minutes, chat each online player a summary of what their shop block(s) sold via
// hopper since the last report - separate from manual GUI sales, see ShopBlockEntity's
// WorldlyContainer.canPlaceItemThroughFace override (only hoppers/droppers call that).
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class HopperSalesTracker
{
    private static final int REPORT_INTERVAL_TICKS = 20 * 60 * 5;

    private static final Map<UUID, Map<Item, Integer>> pending = new HashMap<>();

    private HopperSalesTracker() {}

    public static void recordSale(UUID owner, Item item, int count)
    {
        pending.computeIfAbsent(owner, k -> new HashMap<>()).merge(item, count, Integer::sum);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick == 0 || tick % REPORT_INTERVAL_TICKS != 0)
            return;

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            Map<Item, Integer> sales = pending.remove(player.getUUID());
            if (sales == null || sales.isEmpty())
            {
                player.sendSystemMessage(Component.literal("No hopper automation").withStyle(ChatFormatting.GRAY));
                continue;
            }

            player.sendSystemMessage(Component.literal("Hopper sales (last 5 min):").withStyle(ChatFormatting.GOLD));
            for (Map.Entry<Item, Integer> entry : sales.entrySet())
                player.sendSystemMessage(Component.literal("  " + entry.getKey().getDescription().getString() + " x" + entry.getValue()));
        }
    }
}
