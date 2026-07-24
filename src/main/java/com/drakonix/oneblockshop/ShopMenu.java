package com.drakonix.oneblockshop;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Slot 0 is backed by a ShopBlockEntity (server side) which does the actual selling -
// see ShopBlockEntity.setItem, shared by this GUI and hopper insertion alike.
public class ShopMenu extends AbstractContainerMenu
{
    public enum Tab { SELL, BORDER, BUY }

    public static final int TAB_SELL_BUTTON = 0;
    public static final int TAB_BORDER_BUTTON = 1;
    public static final int TAB_BUY_BUTTON = 2;
    public static final int EXPAND_BORDER_BUTTON = 3;
    public static final int BUY_ITEM_BUTTON_BASE = 10;

    public record BuyOffer(Item item, int count, long price) {}

    // README frames this as a short curated list of things you can't easily produce early on,
    // not a dynamic market - flat prices, hand-picked.
    public static final List<BuyOffer> BUY_OFFERS = List.of(
            new BuyOffer(Items.SUGAR_CANE, 1, 5),
            new BuyOffer(Items.CACTUS, 1, 5),
            new BuyOffer(Items.LAVA_BUCKET, 1, 40),
            new BuyOffer(Items.WATER_BUCKET, 1, 10));

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, OneBlockShopMod.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> TYPE = MENUS.register(
            "shop", () -> new MenuType<>(ShopMenu::new, FeatureFlags.VANILLA_SET));

    private final Container container;
    @Nullable
    private final ShopBlockEntity blockEntity;
    private final DataSlot balance = DataSlot.standalone();
    private final DataSlot activeTab = DataSlot.standalone();

    // Client-side reconstruction from the network packet; slot 0's real contents get synced
    // over automatically regardless of which Container backs this local placeholder.
    public ShopMenu(int containerId, Inventory playerInventory)
    {
        super(TYPE.get(), containerId);
        this.container = new SimpleContainer(1);
        this.blockEntity = null;
        this.addDataSlot(this.balance);
        this.addDataSlot(this.activeTab);
        addSlots(this.container, playerInventory);
    }

    public ShopMenu(int containerId, Inventory playerInventory, ShopBlockEntity blockEntity)
    {
        super(TYPE.get(), containerId);
        this.container = blockEntity;
        this.blockEntity = blockEntity;
        this.addDataSlot(this.balance);
        this.addDataSlot(this.activeTab);
        refreshBalance();
        addSlots(blockEntity, playerInventory);
    }

    // Balance isn't a slot, so nothing pushes it to the client on its own - refresh it every
    // tick the menu's open. Cheap: this only runs server-side, once per open menu per tick.
    @Override
    public void broadcastChanges()
    {
        refreshBalance();
        super.broadcastChanges();
    }

    private void refreshBalance()
    {
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel)
            this.balance.set((int) Math.min(Integer.MAX_VALUE, Wallet.get(serverLevel.getServer(), this.blockEntity.getOwnerUUID())));
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

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    public int getBalance()
    {
        return this.balance.get();
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
                slot.set(ItemStack.EMPTY);
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
        if (id == EXPAND_BORDER_BUTTON && player instanceof ServerPlayer serverPlayer)
            return Border.tryExpand(serverPlayer);
        if (id >= BUY_ITEM_BUTTON_BASE && player instanceof ServerPlayer serverPlayer)
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
