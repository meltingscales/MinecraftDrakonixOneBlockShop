package com.drakonix.oneblockshop;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Slot 0 is backed by a ShopBlockEntity (server side) which does the actual selling -
// see ShopBlockEntity.setItem, shared by this GUI and hopper insertion alike.
public class ShopMenu extends AbstractContainerMenu
{
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, OneBlockShopMod.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> TYPE = MENUS.register(
            "shop", () -> new MenuType<>(ShopMenu::new, FeatureFlags.VANILLA_SET));

    private final Container container;
    private final DataSlot balance = DataSlot.standalone();

    // Client-side reconstruction from the network packet; slot 0's real contents get synced
    // over automatically regardless of which Container backs this local placeholder.
    public ShopMenu(int containerId, Inventory playerInventory)
    {
        super(TYPE.get(), containerId);
        this.container = new SimpleContainer(1);
        this.addDataSlot(this.balance);
        addSlots(this.container, playerInventory);
    }

    public ShopMenu(int containerId, Inventory playerInventory, ShopBlockEntity blockEntity)
    {
        super(TYPE.get(), containerId);
        this.container = blockEntity;
        this.addDataSlot(this.balance);
        if (playerInventory.player.level() instanceof ServerLevel serverLevel)
            this.balance.set((int) Math.min(Integer.MAX_VALUE, Wallet.get(serverLevel.getServer(), blockEntity.getOwnerUUID())));
        addSlots(blockEntity, playerInventory);
    }

    private void addSlots(Container sellContainer, Inventory playerInventory)
    {
        this.addSlot(new Slot(sellContainer, 0, 80, 35));

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
}
