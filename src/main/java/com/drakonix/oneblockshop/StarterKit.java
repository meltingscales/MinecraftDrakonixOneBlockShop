package com.drakonix.oneblockshop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// First-login kit: iron pickaxe, one shop block to place, and a guide book paginated from
// GUIDE.md (bundled into the jar - see the processResources block in build.gradle).
@EventBusSubscriber(modid = OneBlockShopMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class StarterKit
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PAGE_BUDGET = 260;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GIVEN = ATTACHMENTS.register(
            "starter_kit_given", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    private StarterKit() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide)
            return;
        if (player instanceof ServerPlayer serverPlayer)
        {
            Border.initIfNeeded(serverPlayer);
            Border.clampForMultiplayer(serverPlayer.serverLevel().getServer().overworld());
            Wallet.flushPendingCredits(serverPlayer);
        }
        if (player.getData(GIVEN))
            return;
        player.setData(GIVEN, true);
        giveItems(player);
    }

    // Also callable directly (see AdminCommands' "starterkit give") to re-issue the kit without
    // waiting for a fresh login - doesn't touch the GIVEN flag, so it can't skip a first-login
    // grant, only add extra.
    public static void giveItems(Player player)
    {
        player.getInventory().add(cursedUnsellable(new ItemStack(Items.IRON_PICKAXE), player));
        player.getInventory().add(cursedUnsellable(new ItemStack(OneBlockShopMod.SHOP_BLOCK_ITEM.get()), player));
        player.getInventory().add(cursedUnsellable(guideBook(), player));
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 4));

        ItemStack compass = explorersCompass();
        if (!compass.isEmpty())
            player.getInventory().add(cursedUnsellable(compass, player));
    }

    // Marked with the Unsellable curse so a stray hopper (or an absent-minded drop into the
    // shop's GUI slot) can't vanish your only pickaxe, guide, or shop block.
    static ItemStack cursedUnsellable(ItemStack stack, Player player)
    {
        EnchantmentHelper.updateEnchantments(stack, mutable ->
                mutable.set(player.level().registryAccess().holderOrThrow(OneBlockShopMod.UNSELLABLE), 1));
        return stack;
    }

    private static final ResourceLocation EXPLORERS_COMPASS_ID = ResourceLocation.fromNamespaceAndPath("explorerscompass", "explorerscompass");

    // Dev-only/optional mod (see RECOMMENDED-MODS.md) - a manual install without the modpack
    // won't have it, so this just skips the gift rather than crash if it's missing.
    private static ItemStack explorersCompass()
    {
        if (!BuiltInRegistries.ITEM.containsKey(EXPLORERS_COMPASS_ID))
            return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(EXPLORERS_COMPASS_ID));
    }

    // Package-visible so StarterPacks can re-issue a fresh copy from the shop's Packs tab
    // (e.g. a lost/dropped book) without duplicating the version-titled book-building logic.
    static ItemStack guideBook()
    {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Drakonix Guide v" + OneBlockShopMod.modVersion), "Drakonix", 0, pages(), true));
        return book;
    }

    private static List<Filterable<Component>> pages()
    {
        List<Filterable<Component>> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String paragraph : readGuideText().split("\n\n"))
        {
            if (paragraph.isBlank())
                continue;
            if (page.length() > 0 && page.length() + paragraph.length() > PAGE_BUDGET)
            {
                pages.add(Filterable.passThrough(Component.literal(page.toString())));
                page.setLength(0);
            }
            if (page.length() > 0)
                page.append("\n\n");
            page.append(paragraph.trim());
        }
        if (page.length() > 0)
            pages.add(Filterable.passThrough(Component.literal(page.toString())));
        return pages;
    }

    private static String readGuideText()
    {
        try (InputStream stream = StarterKit.class.getResourceAsStream("/GUIDE.md"))
        {
            if (stream == null)
                return "Sell items at the Drakonix Block Shop to expand your border.";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.warn("Failed to read GUIDE.md for the starter guide book", e);
            return "Sell items at the Drakonix Block Shop to expand your border.";
        }
    }
}
