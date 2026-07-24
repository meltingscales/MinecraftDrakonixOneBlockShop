package com.drakonix.oneblockshop;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(OneBlockShopMod.MODID)
public class OneBlockShopMod
{
    public static final String MODID = "drakonixoneblockshop";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    // Breakable like a normal block (iron-block-ish toughness) - mine it, move it, automate around it.
    public static final DeferredBlock<Block> SHOP_BLOCK = BLOCKS.register("drakonix_block_shop",
            () -> new ShopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).requiresCorrectToolForDrops().strength(5.0F, 6.0F)));
    public static final DeferredItem<BlockItem> SHOP_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("drakonix_block_shop", SHOP_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopBlockEntity>> SHOP_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "drakonix_block_shop", () -> BlockEntityType.Builder.of(ShopBlockEntity::new, SHOP_BLOCK.get()).build(null));

    // Data-driven (see data/drakonixoneblockshop/enchantment/unsellable.json) - not registered
    // in code, just referenced by key and resolved against registry access where needed.
    public static final ResourceKey<Enchantment> UNSELLABLE = ResourceKey.create(
            Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(MODID, "unsellable"));

    public OneBlockShopMod(IEventBus modEventBus, ModContainer modContainer)
    {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ShopMenu.MENUS.register(modEventBus);
        Wallet.ATTACHMENTS.register(modEventBus);
        StarterKit.ATTACHMENTS.register(modEventBus);
        Border.ATTACHMENTS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
            event.accept(SHOP_BLOCK_ITEM);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event)
        {
            event.register(ShopMenu.TYPE.get(), ShopScreen::new);
        }
    }
}
