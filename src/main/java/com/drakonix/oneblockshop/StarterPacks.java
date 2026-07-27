package com.drakonix.oneblockshop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Easy-mode assist, not part of the shop's economy: a free bundle of a tech mod's core
// early-game blocks/machines/power/conduits, once per hour per pack, no cost. Lives in the
// shop GUI's Starter Packs tab (see ShopMenu/ShopScreen) for players who'd rather skip the
// grind of building up that mod's automation chain from scratch by hand.
public final class StarterPacks
{
    public record PackItem(String itemId, int count) {}

    public record Pack(String id, String label, List<PackItem> items) {}

    private static final long COOLDOWN_TICKS = 60L * 60L * 20L; // 1 hour

    // AE2, Mekanism, and EnderIO are this project's currently-supported tech mods (see
    // RECOMMENDED-MODS.md) - Thermal Expansion and IC2 have no NeoForge 1.21.1 release to draw
    // items from, and GregTech was removed (see FUTURE-MOD-COMPAT.md). Every item id below was
    // checked against that mod's real recipe/lang data (extracted from the actual jar) before
    // being added here, not guessed.
    public static final List<Pack> PACKS = List.of(
            new Pack("ae2", "Applied Energistics 2", List.of(
                    new PackItem("ae2:controller", 1),
                    new PackItem("ae2:drive", 1),
                    new PackItem("ae2:interface", 1),
                    new PackItem("ae2:cable_energy_acceptor", 1),
                    new PackItem("ae2:energy_cell", 2),
                    new PackItem("ae2:fluix_smart_cable", 8))),
            // Modern Mekanism (10.x) no longer ships a dedicated power-generator block - the old
            // Heat/Solar/Bio/Wind Generators were cut from the mod years ago. Basic Energy Cube
            // (a power buffer) is the closest real substitute, so this pack has no separate
            // "generator" entry.
            new Pack("mekanism", "Mekanism", List.of(
                    new PackItem("mekanism:enrichment_chamber", 1),
                    new PackItem("mekanism:crusher", 1),
                    new PackItem("mekanism:basic_energy_cube", 1),
                    new PackItem("mekanism:basic_universal_cable", 8))),
            // EnderIO's conduits are all one shared "enderio:conduit" item, typed via a
            // mod-specific data component set at craft time (confirmed in its recipe JSON) -
            // granting a correctly-typed one needs that component, which this mod has no
            // compile-time handle on since EnderIO isn't a real dependency (the same open
            // problem TODO.md already tracks for EnderIO's item pipes). Skips a conduit entry
            // rather than hand out a blank/untyped one.
            new Pack("enderio", "EnderIO", List.of(
                    new PackItem("enderio:alloy_smelter", 1),
                    new PackItem("enderio:sag_mill", 1),
                    new PackItem("enderio:stirling_generator", 1),
                    new PackItem("enderio:basic_capacitor_bank", 1))));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OneBlockShopMod.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<String, Long>>> LAST_CLAIM_TICKS = ATTACHMENTS.register(
            "starter_pack_last_claim_ticks",
            () -> AttachmentType.builder(StarterPacks::emptyClaims).serialize(Codec.unboundedMap(Codec.STRING, Codec.LONG)).build());

    private StarterPacks() {}

    private static Map<String, Long> emptyClaims()
    {
        return new HashMap<>();
    }

    public static Pack byId(String id)
    {
        for (Pack pack : PACKS)
            if (pack.id().equals(id))
                return pack;
        return null;
    }

    // Whole seconds left before this player can claim this pack again, 0 if free to. Exposed
    // for the GUI to show a countdown instead of a button that silently fails.
    public static long cooldownRemainingSeconds(Player player, String packId)
    {
        Long last = player.getData(LAST_CLAIM_TICKS).get(packId);
        if (last == null)
            return 0L;
        long elapsed = player.level().getGameTime() - last;
        long remainingTicks = Math.max(0L, COOLDOWN_TICKS - elapsed);
        return (remainingTicks + 19L) / 20L; // ceil to whole seconds
    }

    public static boolean tryClaim(ServerPlayer player, String packId)
    {
        Pack pack = byId(packId);
        if (pack == null || cooldownRemainingSeconds(player, packId) > 0)
            return false;

        Map<String, Long> claims = new HashMap<>(player.getData(LAST_CLAIM_TICKS));
        claims.put(packId, player.level().getGameTime());
        player.setData(LAST_CLAIM_TICKS, claims);

        for (PackItem entry : pack.items())
        {
            ResourceLocation id = ResourceLocation.parse(entry.itemId());
            if (!BuiltInRegistries.ITEM.containsKey(id))
                continue; // that tech mod isn't installed on this server - skip rather than crash
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item, entry.count());
            if (!player.getInventory().add(stack))
                player.drop(stack, false);
        }
        return true;
    }
}
