package com.drakonix.oneblockshop;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Backs both the right-click GUI slot and hopper insertion, so an item dropped in from either
// path sells through the same logic. Owner is whoever placed the block; hopper sales are
// credited to them even while they're elsewhere, as long as they're still online.
// ponytail: if the owner is offline, hopper insertion is refused (item stays put upstream)
// rather than sold to nobody or queued; fine for singleplayer, revisit for dedicated servers.
public class ShopBlockEntity extends BlockEntity implements Container, MenuProvider
{
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    @Nullable
    private UUID ownerUUID;

    public ShopBlockEntity(BlockPos pos, BlockState state)
    {
        super(OneBlockShopMod.SHOP_BLOCK_ENTITY.get(), pos, state);
    }

    public void setOwner(@Nullable UUID ownerUUID)
    {
        this.ownerUUID = ownerUUID;
        this.setChanged();
    }

    @Nullable
    public UUID getOwnerUUID()
    {
        return this.ownerUUID;
    }

    private void trySell(ItemStack stack)
    {
        if (stack.isEmpty() || this.level == null || this.level.isClientSide || this.ownerUUID == null)
            return;
        if (isUnsellable(stack))
            return;

        ServerPlayer owner = this.level.getServer() == null ? null : this.level.getServer().getPlayerList().getPlayer(this.ownerUUID);
        if (owner == null)
            return;

        long unitPrice = Pricing.priceOf(stack.getItem(), this.level.getRecipeManager(), this.level.registryAccess());
        Wallet.add(owner, unitPrice * stack.getCount());
        this.items.set(0, ItemStack.EMPTY);
    }

    private boolean isUnsellable(ItemStack stack)
    {
        return EnchantmentHelper.getItemEnchantmentLevel(
                this.level.registryAccess().holderOrThrow(OneBlockShopMod.UNSELLABLE), stack) > 0;
    }

    @Override
    public int getContainerSize()
    {
        return 1;
    }

    @Override
    public boolean isEmpty()
    {
        return this.items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return this.items.get(0);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        return ContainerHelper.removeItem(this.items, 0, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(this.items, 0);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        this.items.set(0, stack);
        if (stack.getCount() > this.getMaxStackSize())
            stack.setCount(this.getMaxStackSize());
        this.trySell(this.items.get(0));
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack)
    {
        return this.level == null || !isUnsellable(stack);
    }

    @Override
    public void clearContent()
    {
        this.items.set(0, ItemStack.EMPTY);
    }

    @Override
    public boolean stillValid(Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container.drakonixoneblockshop.shop");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player)
    {
        return new ShopMenu(containerId, playerInventory, this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        if (tag.hasUUID("Owner"))
            this.ownerUUID = tag.getUUID("Owner");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        if (this.ownerUUID != null)
            tag.putUUID("Owner", this.ownerUUID);
    }
}
