package com.drakonix.oneblockshop;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

// Literally every item is sellable. Raw/base resources (things nothing crafts) get a
// hand-set seed price (pricing/seed_prices.json); everything else derives its price from its
// cheapest known recipe (crafting-table or furnace-family - anything that overrides
// Recipe.getIngredients()), recursively, so we don't hand-maintain a price for every item in
// the game.
// ponytail: ingredient-sum-over-yield only - no fuel cost, mining difficulty, or drop rarity
// modeling. Fine for a shop economy, not a market simulation. Unrecognized/unpriceable items
// (no seed, no usable recipe) fall back to DEFAULT_PRICE rather than refusing the sale.
public final class Pricing
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DEFAULT_PRICE = 1L;

    // JSON (not a hardcoded Item map) specifically so an optional/soft-dependency mod's items
    // can be seeded by ID string later without a compile-time dependency on that mod - an ID
    // for a mod that isn't installed just fails containsKey() and gets skipped, no crash.
    static final Map<Item, Long> SEED_PRICES = loadSeedPrices();

    private static Map<Item, Long> loadSeedPrices()
    {
        Map<Item, Long> prices = new HashMap<>();
        try (InputStream stream = Pricing.class.getResourceAsStream("/pricing/seed_prices.json"))
        {
            if (stream == null)
            {
                LOGGER.warn("pricing/seed_prices.json missing from jar - no seed prices loaded");
                return prices;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet())
            {
                ResourceLocation id = ResourceLocation.parse(entry.getKey());
                if (BuiltInRegistries.ITEM.containsKey(id))
                    prices.put(BuiltInRegistries.ITEM.get(id), entry.getValue().getAsLong());
                else
                    LOGGER.warn("Unknown item '{}' in pricing/seed_prices.json - skipped", entry.getKey());
            }
        }
        catch (IOException | RuntimeException e)
        {
            LOGGER.error("Failed to load pricing/seed_prices.json - no seed prices loaded", e);
        }
        return prices;
    }

    // Rebuilt whenever the RecipeManager instance changes (world load / datapack reload).
    private static RecipeManager indexedManager;
    private static Map<Item, List<RecipeHolder<?>>> recipesByOutput = Map.of();
    private static final Map<Item, Long> priceCache = new HashMap<>();

    private Pricing() {}

    public static long priceOf(Item item, RecipeManager recipeManager, HolderLookup.Provider registries)
    {
        ensureIndexed(recipeManager, registries);
        return priceOf(item, registries, new HashSet<>());
    }

    private static void ensureIndexed(RecipeManager recipeManager, HolderLookup.Provider registries)
    {
        if (indexedManager == recipeManager)
            return;
        indexedManager = recipeManager;
        priceCache.clear();

        Map<Item, List<RecipeHolder<?>>> byOutput = new HashMap<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes())
        {
            ItemStack result = holder.value().getResultItem(registries);
            if (!result.isEmpty())
                byOutput.computeIfAbsent(result.getItem(), i -> new ArrayList<>()).add(holder);
        }
        recipesByOutput = byOutput;
    }

    private static long priceOf(Item item, HolderLookup.Provider registries, Set<Item> visiting)
    {
        Long seeded = SEED_PRICES.get(item);
        if (seeded != null)
            return seeded;

        Long cached = priceCache.get(item);
        if (cached != null)
            return cached;

        // Cycle guard: an ingredient chain that loops back on itself just bottoms out here.
        if (!visiting.add(item))
            return DEFAULT_PRICE;

        long best = -1;
        for (RecipeHolder<?> holder : recipesByOutput.getOrDefault(item, List.of()))
        {
            Recipe<?> recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            if (result.getCount() <= 0)
                continue;

            List<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty())
                continue;

            long sum = 0;
            boolean usable = true;
            for (Ingredient ingredient : ingredients)
            {
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0)
                {
                    usable = false;
                    break;
                }
                long cheapest = Long.MAX_VALUE;
                for (ItemStack option : options)
                    cheapest = Math.min(cheapest, priceOf(option.getItem(), registries, visiting));
                sum += cheapest;
            }
            if (!usable)
                continue;

            long candidate = Math.max(1L, Math.round(sum / (double) result.getCount()));
            if (best == -1 || candidate < best)
                best = candidate;
        }

        visiting.remove(item);

        long price = best == -1 ? DEFAULT_PRICE : best;
        priceCache.put(item, price);
        return price;
    }
}
