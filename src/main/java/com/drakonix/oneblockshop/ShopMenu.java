package com.drakonix.oneblockshop;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Slot 0 is backed by a ShopBlockEntity (server side) which does the actual selling -
// see ShopBlockEntity.setItem, shared by this GUI and hopper insertion alike.
public class ShopMenu extends AbstractContainerMenu
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Tab { SELL, BORDER, BUY, EXPEDITION, PACKS }

    public static final int TAB_SELL_BUTTON = 0;
    public static final int TAB_BORDER_BUTTON = 1;
    public static final int TAB_BUY_BUTTON = 2;
    public static final int EXPAND_BORDER_BUTTON = 3;
    public static final int TAB_EXPEDITION_BUTTON = 4;
    public static final int TELEPORT_BUTTON = 5;
    public static final int TAB_PACKS_BUTTON = 6;
    public static final int TELEPORT_CAVE_BUTTON = 7;
    public static final int BUY_ITEM_BUTTON_BASE = 10;
    // Well clear of BUY_ITEM_BUTTON_BASE's range (pricing/buy_offers.json has room to grow) -
    // StarterPacks.PACKS is a short, fixed list so this only ever needs a handful of ids.
    public static final int PACK_CLAIM_BUTTON_BASE = 100;

    public record BuyOffer(Item item, int count, long price) {}

    // README frames this as a curated list of things you can't easily produce early on, not a
    // dynamic market - flat prices, hand-picked, one each per purchase. JSON (not a hardcoded
    // Java list) for the same reason as Pricing's seed tables: editable without recompiling,
    // and an unknown item ID just gets skipped rather than crashing.
    public static final List<BuyOffer> BUY_OFFERS = loadBuyOffers();

    private static List<BuyOffer> loadBuyOffers()
    {
        List<BuyOffer> offers = new ArrayList<>();
        try (InputStream stream = ShopMenu.class.getResourceAsStream("/pricing/buy_offers.json"))
        {
            if (stream == null)
            {
                LOGGER.warn("pricing/buy_offers.json missing from jar - no buy offers loaded");
                return offers;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet())
            {
                ResourceLocation id = ResourceLocation.parse(entry.getKey());
                if (BuiltInRegistries.ITEM.containsKey(id))
                    offers.add(new BuyOffer(BuiltInRegistries.ITEM.get(id), 1, entry.getValue().getAsLong()));
                else
                    LOGGER.warn("Unknown item '{}' in pricing/buy_offers.json - skipped", entry.getKey());
            }
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.error("Failed to load pricing/buy_offers.json - no buy offers loaded", e);
        }
        return offers;
    }

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, OneBlockShopMod.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> TYPE = MENUS.register(
            "shop", () -> new MenuType<>(ShopMenu::new, FeatureFlags.VANILLA_SET));

    private final Container container;
    @Nullable
    private final ShopBlockEntity blockEntity;
    private final Player viewingPlayer;
    private final DataSlot balance = DataSlot.standalone();
    private final DataSlot activeTab = DataSlot.standalone();
    // Whoever's clicking the expand button pays and waits out the cooldown (see
    // Border.tryExpand) - not necessarily this block's owner, so this tracks the viewing
    // player specifically rather than reusing the owner-scoped balance display.
    private final DataSlot expandCooldownSeconds = DataSlot.standalone();
    // Portal state is global (one at a time, see Expedition.java), not tied to the viewing
    // player - anyone can walk into whichever shop's portal is currently open.
    private final DataSlot portalRemainingSeconds = DataSlot.standalone();
    // One per StarterPacks.PACKS entry, same order - each pack has its own independent
    // per-player cooldown (see StarterPacks.cooldownRemainingSeconds).
    private final List<DataSlot> packCooldownSeconds = new ArrayList<>();

    // Client-side reconstruction from the network packet; slot 0's real contents get synced
    // over automatically regardless of which Container backs this local placeholder.
    public ShopMenu(int containerId, Inventory playerInventory)
    {
        super(TYPE.get(), containerId);
        this.container = new SimpleContainer(1);
        this.blockEntity = null;
        this.viewingPlayer = playerInventory.player;
        this.addDataSlot(this.balance);
        this.addDataSlot(this.activeTab);
        this.addDataSlot(this.expandCooldownSeconds);
        this.addDataSlot(this.portalRemainingSeconds);
        addPackCooldownSlots();
        addSlots(this.container, playerInventory);
    }

    public ShopMenu(int containerId, Inventory playerInventory, ShopBlockEntity blockEntity)
    {
        super(TYPE.get(), containerId);
        this.container = blockEntity;
        this.blockEntity = blockEntity;
        this.viewingPlayer = playerInventory.player;
        this.addDataSlot(this.balance);
        this.addDataSlot(this.activeTab);
        this.addDataSlot(this.expandCooldownSeconds);
        this.addDataSlot(this.portalRemainingSeconds);
        addPackCooldownSlots();
        refreshBalance();
        refreshCooldown();
        refreshExpedition();
        refreshPackCooldowns();
        addSlots(blockEntity, playerInventory);
    }

    private void addPackCooldownSlots()
    {
        for (int i = 0; i < StarterPacks.PACKS.size(); i++)
        {
            DataSlot slot = DataSlot.standalone();
            this.packCooldownSeconds.add(slot);
            this.addDataSlot(slot);
        }
    }

    // Neither is a slot, so nothing pushes them to the client on their own - refresh every
    // tick the menu's open. Cheap: this only runs server-side, once per open menu per tick.
    @Override
    public void broadcastChanges()
    {
        refreshBalance();
        refreshCooldown();
        refreshExpedition();
        refreshPackCooldowns();
        super.broadcastChanges();
    }

    private void refreshBalance()
    {
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel)
            this.balance.set((int) Math.min(Integer.MAX_VALUE, Wallet.get(serverLevel.getServer(), this.blockEntity.getOwnerUUID())));
    }

    private void refreshCooldown()
    {
        if (this.viewingPlayer instanceof ServerPlayer)
            this.expandCooldownSeconds.set((int) Math.min(Integer.MAX_VALUE, Border.cooldownRemainingSeconds(this.viewingPlayer)));
    }

    private void refreshExpedition()
    {
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel)
            this.portalRemainingSeconds.set((int) Math.min(Integer.MAX_VALUE, Expedition.portalRemainingSeconds(serverLevel)));
    }

    private void refreshPackCooldowns()
    {
        if (!(this.viewingPlayer instanceof ServerPlayer))
            return;
        for (int i = 0; i < StarterPacks.PACKS.size(); i++)
        {
            long remaining = StarterPacks.cooldownRemainingSeconds(this.viewingPlayer, StarterPacks.PACKS.get(i).id());
            this.packCooldownSeconds.get(i).set((int) Math.min(Integer.MAX_VALUE, remaining));
        }
    }

    private void addSlots(Container sellContainer, Inventory playerInventory)
    {
        this.addSlot(new Slot(sellContainer, 0, 80, 35)
        {
            @Override
            public boolean isActive()
            {
                return getActiveTab() == Tab.SELL;
            }
        });

        // Pushed down from vanilla's usual 84/142 to leave room above for the Buy tab's grid,
        // which now has enough offers (see pricing/buy_offers.json) to need real vertical space -
        // see PLAYER_INVENTORY_Y/HOTBAR_Y in ShopScreen for the matching background sizing.
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, ShopScreen.PLAYER_INVENTORY_Y + row * 18));

        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, ShopScreen.HOTBAR_Y));
    }

    public int getBalance()
    {
        return this.balance.get();
    }

    public int getExpandCooldownSeconds()
    {
        return this.expandCooldownSeconds.get();
    }

    public int getPortalRemainingSeconds()
    {
        return this.portalRemainingSeconds.get();
    }

    public int getPackCooldownSeconds(int packIndex)
    {
        return this.packCooldownSeconds.get(packIndex).get();
    }

    public Tab getActiveTab()
    {
        Tab[] tabs = Tab.values();
        int ordinal = this.activeTab.get();
        return ordinal >= 0 && ordinal < tabs.length ? tabs[ordinal] : Tab.SELL;
    }

    private void setActiveTab(Tab tab)
    {
        this.activeTab.set(tab.ordinal());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem())
        {
            ItemStack moved = slot.getItem();
            result = moved.copy();
            if (index == 0)
            {
                if (!this.moveItemStackTo(moved, 1, this.slots.size(), true))
                    return ItemStack.EMPTY;
            }
            else if (!this.moveItemStackTo(moved, 0, 1, false))
            {
                return ItemStack.EMPTY;
            }

            if (moved.isEmpty())
            {
                // Not just slot.set(ItemStack.EMPTY) unconditionally: selling a stack into slot 0
                // above runs ShopBlockEntity.setItem -> trySell -> Wallet.add synchronously, and
                // Wallet.add clears+remints every token stack in the player's own inventory by
                // index (see Wallet.removeAllTokens/mint) as a side effect - while this shift-
                // click is still mid-flight. If that remint happens to reuse this exact slot
                // index for a freshly-minted token stack, blindly clearing it here would destroy
                // those tokens the instant after they were paid out. Only clear if this slot
                // still holds the same (now fully moved) stack we started with.
                if (slot.getItem() == moved)
                    slot.set(ItemStack.EMPTY);
            }
            else
                slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        // Tab switches run identically on both sides (no server round-trip wait needed for a
        // pure UI toggle); the buy/expand actions are server-authoritative (ServerPlayer check).
        if (id == TAB_SELL_BUTTON) { setActiveTab(Tab.SELL); return true; }
        if (id == TAB_BORDER_BUTTON) { setActiveTab(Tab.BORDER); return true; }
        if (id == TAB_BUY_BUTTON) { setActiveTab(Tab.BUY); return true; }
        if (id == TAB_EXPEDITION_BUTTON) { setActiveTab(Tab.EXPEDITION); return true; }
        if (id == TAB_PACKS_BUTTON) { setActiveTab(Tab.PACKS); return true; }
        if (id == EXPAND_BORDER_BUTTON && player instanceof ServerPlayer serverPlayer)
            return Border.tryExpand(serverPlayer);
        if (id == TELEPORT_BUTTON && player instanceof ServerPlayer
                && this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel)
            return Expedition.openPortal(serverLevel, this.blockEntity.getBlockPos(), false);
        if (id == TELEPORT_CAVE_BUTTON && player instanceof ServerPlayer
                && this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel)
            return Expedition.openPortal(serverLevel, this.blockEntity.getBlockPos(), true);
        if (id >= PACK_CLAIM_BUTTON_BASE && id < PACK_CLAIM_BUTTON_BASE + StarterPacks.PACKS.size() && player instanceof ServerPlayer serverPlayer)
            return StarterPacks.tryClaim(serverPlayer, StarterPacks.PACKS.get(id - PACK_CLAIM_BUTTON_BASE).id());
        if (id >= BUY_ITEM_BUTTON_BASE && id < BUY_ITEM_BUTTON_BASE + BUY_OFFERS.size() && player instanceof ServerPlayer serverPlayer)
            return tryBuy(serverPlayer, id - BUY_ITEM_BUTTON_BASE);
        return super.clickMenuButton(player, id);
    }

    private boolean tryBuy(ServerPlayer player, int offerIndex)
    {
        if (offerIndex < 0 || offerIndex >= BUY_OFFERS.size())
            return false;
        BuyOffer offer = BUY_OFFERS.get(offerIndex);
        if (Wallet.get(player) < offer.price())
            return false;
        Wallet.add(player, -offer.price());
        ItemStack stack = new ItemStack(offer.item(), offer.count());
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
        return true;
    }
}
