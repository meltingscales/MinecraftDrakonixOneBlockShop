package com.drakonix.oneblockshop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
    // conduitType is null for a plain item; set (to e.g. "enderio:energy") for an EnderIO
    // conduit, which needs special handling - see enderioConduit().
    public record PackItem(String itemId, int count, String conduitType)
    {
        public static PackItem item(String itemId, int count)
        {
            return new PackItem(itemId, count, null);
        }

        public static PackItem enderioConduit(String conduitType, int count)
        {
            return new PackItem("enderio:conduit", count, conduitType);
        }
    }

    public record Pack(String id, String label, List<PackItem> items) {}

    private static final long COOLDOWN_TICKS = 60L * 60L * 20L; // 1 hour

    // AE2, Mekanism, and EnderIO are this project's currently-supported tech mods (see
    // RECOMMENDED-MODS.md) - Thermal Expansion and IC2 have no NeoForge 1.21.1 release to draw
    // items from, and GregTech was removed (see FUTURE-MOD-COMPAT.md). Every item id below was
    // checked against that mod's real recipe/lang data (extracted from the actual jar) before
    // being added here, not guessed.
    // Not a tech-mod bundle - lets a player pull a fresh (re-cursed, current-version-titled)
    // Drakonix Guide from the Packs tab if they lost their original, same 1-hour cooldown as
    // every other pack rather than a special-cased free-for-all. Handled separately in
    // tryClaim() since its item isn't a plain registry id/count (StarterKit.guideBook() builds
    // a WRITTEN_BOOK with dynamic page/version content), so its PackItem list stays empty.
    private static final String GUIDE_PACK_ID = "guide";

    public static final List<Pack> PACKS = List.of(
            new Pack(GUIDE_PACK_ID, "Drakonix Guide", List.of()),
            new Pack("ae2", "Applied Energistics 2", List.of(
                    PackItem.item("ae2:controller", 1),
                    PackItem.item("ae2:drive", 1),
                    PackItem.item("ae2:interface", 1),
                    PackItem.item("ae2:crafting_terminal", 1),
                    // Both the full block and the small cable-part version - "ae2:energy_acceptor"
                    // and "ae2:cable_energy_acceptor" are two distinct real items, confirmed via
                    // their separate recipe result ids.
                    PackItem.item("ae2:energy_acceptor", 1),
                    PackItem.item("ae2:cable_energy_acceptor", 1),
                    PackItem.item("ae2:energy_cell", 2),
                    PackItem.item("ae2:item_storage_cell_1k", 4),
                    PackItem.item("ae2:fluix_smart_cable", 8))),
            // Modern Mekanism (10.x) no longer ships a dedicated power-generator block - the old
            // Heat/Solar/Bio/Wind Generators were cut from the mod years ago (confirmed: no
            // "generator" or "solar panel" block anywhere in this version's lang data). Basic
            // Energy Cube plus a Basic Induction Cell/Provider pair (the modular big-battery
            // system, real Mekanism blocks) are the closest substitutes - several different real
            // energy machines, just storage/buffering rather than generation.
            new Pack("mekanism", "Mekanism", List.of(
                    PackItem.item("mekanism:enrichment_chamber", 1),
                    PackItem.item("mekanism:crusher", 1),
                    PackItem.item("mekanism:basic_energy_cube", 1),
                    PackItem.item("mekanism:basic_induction_cell", 1),
                    PackItem.item("mekanism:basic_induction_provider", 1),
                    PackItem.item("mekanism:basic_universal_cable", 8))),
            // EnderIO's conduits are all one shared "enderio:conduit" item, typed via a
            // mod-specific data component (confirmed in its recipe JSON: result "id" is always
            // "enderio:conduit", with a "components": {"enderio:conduit": "enderio:<type>"}
            // entry picking the type) rather than being separate items - see enderioConduit().
            new Pack("enderio", "EnderIO", List.of(
                    PackItem.item("enderio:alloy_smelter", 1),
                    PackItem.item("enderio:sag_mill", 1),
                    PackItem.item("enderio:stirling_generator", 1),
                    // Base tier of EnderIO's real solar generator block (its tooltip literally
                    // reads "Solar Power!") - "pulsating"/"vibrant" are higher alloy tiers above
                    // this "energetic" one, same naming convention as its other machine tiers.
                    PackItem.item("enderio:energetic_photovoltaic_module", 12),
                    PackItem.item("enderio:basic_capacitor_bank", 1),
                    PackItem.enderioConduit("enderio:energy", 8),
                    PackItem.enderioConduit("enderio:item", 8),
                    PackItem.enderioConduit("enderio:fluid", 8),
                    PackItem.enderioConduit("enderio:redstone", 8))));

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

        if (packId.equals(GUIDE_PACK_ID))
        {
            ItemStack book = StarterKit.cursedUnsellable(StarterKit.guideBook(), player);
            if (!player.getInventory().add(book))
                player.drop(book, false);
            return true;
        }

        for (PackItem entry : pack.items())
        {
            ItemStack stack = entry.conduitType() != null
                    ? enderioConduit(player.level().registryAccess(), entry.conduitType(), entry.count())
                    : plainItem(entry.itemId(), entry.count());
            if (stack.isEmpty())
                continue; // that tech mod (or conduit type) isn't installed/available - skip rather than crash
            if (!player.getInventory().add(stack))
                player.drop(stack, false);
        }
        return true;
    }

    private static ItemStack plainItem(String itemId, int count)
    {
        ResourceLocation id = ResourceLocation.parse(itemId);
        if (!BuiltInRegistries.ITEM.containsKey(id))
            return ItemStack.EMPTY; // that tech mod isn't installed on this server
        return new ItemStack(BuiltInRegistries.ITEM.get(id), count);
    }

    // EnderIO's own registry for conduit types, confirmed by decompiling the class that
    // registers it (com.enderio.conduits.api.EnderIOConduitsRegistries$Keys.CONDUIT ->
    // ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("enderio",
    // "conduit"))) - same id as the data component below, different concept (one's the
    // registry of conduit *types*, the other's the per-stack component that names one).
    private static final ResourceLocation ENDERIO_CONDUIT_ID = ResourceLocation.fromNamespaceAndPath("enderio", "conduit");
    private static final ResourceKey<Registry<Object>> ENDERIO_CONDUIT_REGISTRY = ResourceKey.createRegistryKey(ENDERIO_CONDUIT_ID);

    // EnderIO's conduit item ("enderio:conduit") only becomes a specific conduit type (energy,
    // item, fluid, redstone, ...) via a data component whose value is a Holder into the registry
    // above (confirmed the same way - the component's declared generic signature is
    // DataComponentType<Holder<Conduit<?>>>). Neither type is on this mod's compile classpath
    // (EnderIO isn't a real dependency), so this works entirely through the runtime registries -
    // BuiltInRegistries.DATA_COMPONENT_TYPE for the component itself, then an unchecked generic
    // set() since we only ever have a raw DataComponentType<?> to work with. Returns
    // ItemStack.EMPTY (skip, not a broken/blank conduit) if EnderIO isn't installed, its
    // component/registry ever gets renamed, or conduitTypeId isn't a real entry.
    @SuppressWarnings("unchecked")
    private static ItemStack enderioConduit(RegistryAccess registryAccess, String conduitTypeId, int count)
    {
        if (!BuiltInRegistries.ITEM.containsKey(ENDERIO_CONDUIT_ID))
            return ItemStack.EMPTY;
        DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ENDERIO_CONDUIT_ID);
        if (componentType == null)
            return ItemStack.EMPTY;

        Registry<Object> conduitRegistry;
        try
        {
            conduitRegistry = registryAccess.registryOrThrow(ENDERIO_CONDUIT_REGISTRY);
        }
        catch (IllegalStateException e)
        {
            return ItemStack.EMPTY;
        }
        Optional<Holder.Reference<Object>> conduitType = conduitRegistry.getHolder(ResourceLocation.parse(conduitTypeId));
        if (conduitType.isEmpty())
            return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ENDERIO_CONDUIT_ID), count);
        stack.set((DataComponentType<Object>) componentType, conduitType.get());
        return stack;
    }
}
