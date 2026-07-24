package com.drakonix.oneblockshop;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Backs both the right-click GUI slot and hopper insertion, so an item dropped in from either
// path sells through the same logic. Owner is whoever placed the block; sales are credited to
// them whether they're online or not - see Wallet.creditOffline/flushPendingCredits.
//
// Implements WorldlyContainer (not just Container) so we can tell hopper-driven sales apart
// from GUI ones for HopperSalesTracker: only hopper/dropper automation ever calls
// canPlaceItemThroughFace (Slot's default mayPlace never does), so it's a reliable one-shot
// marker consumed by the very next setItem/trySell call - safe since the server is single-
// threaded and nothing else can interleave between the two calls.
public class ShopBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider
{
    private static final int[] SLOTS = { 0 };

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    @Nullable
    private UUID ownerUUID;
    private boolean pendingHopperInsertion;

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
        boolean viaHopper = this.pendingHopperInsertion;
        this.pendingHopperInsertion = false;

        if (stack.isEmpty() || !(this.level instanceof ServerLevel serverLevel) || this.ownerUUID == null)
            return;
        if (isUnsellable(stack))
            return;

        long unitPrice = Pricing.priceOf(stack.getItem(), this.level.getRecipeManager(), this.level.registryAccess());
        long total = unitPrice * stack.getCount();

        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerUUID);
        if (owner != null)
            Wallet.add(owner, total);
        else
            Wallet.creditOffline(serverLevel, this.ownerUUID, total);

        if (viaHopper)
            HopperSalesTracker.recordSale(this.ownerUUID, stack.getItem(), stack.getCount());
        playSaleFeedback(serverLevel);
        this.items.set(0, ItemStack.EMPTY);
    }

    // Cheap juice: a sale should feel like a sale. Broadcasts to everyone nearby, not just the
    // owner - fine for a single block's worth of noise/sparkle.
    private void playSaleFeedback(ServerLevel serverLevel)
    {
        serverLevel.playSound(null, this.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                this.getBlockPos().getX() + 0.5, this.getBlockPos().getY() + 1.0, this.getBlockPos().getZ() + 0.5,
                8, 0.3, 0.3, 0.3, 0.0);
    }

    private boolean isUnsellable(ItemStack stack)
    {
        return stack.getEnchantmentLevel(this.level.registryAccess().holderOrThrow(OneBlockShopMod.UNSELLABLE)) > 0;
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
    public int[] getSlotsForFace(Direction side)
    {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction)
    {
        this.pendingHopperInsertion = true;
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction)
    {
        return true;
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
