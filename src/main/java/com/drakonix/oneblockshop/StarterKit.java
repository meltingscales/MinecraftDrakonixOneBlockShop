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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
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
            Border.initIfNeeded(serverPlayer);
        if (player.getData(GIVEN))
            return;
        player.setData(GIVEN, true);

        player.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        player.getInventory().add(new ItemStack(OneBlockShopMod.SHOP_BLOCK_ITEM.get()));
        player.getInventory().add(guideBook());
    }

    private static ItemStack guideBook()
    {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Drakonix Guide"), "Drakonix", 0, pages(), true));
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
