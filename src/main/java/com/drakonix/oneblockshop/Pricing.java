package com.drakonix.oneblockshop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

// Literally every item is sellable. Raw/base resources (things nothing crafts) get a
// hand-set seed price; everything else derives its price from its cheapest known recipe
// (crafting-table or furnace-family - anything that overrides Recipe.getIngredients()),
// recursively, so we don't hand-maintain a price for every item in the game.
// ponytail: ingredient-sum-over-yield only - no fuel cost, mining difficulty, or drop rarity
// modeling. Fine for a shop economy, not a market simulation. Unrecognized/unpriceable items
// (no seed, no usable recipe) fall back to DEFAULT_PRICE rather than refusing the sale.
public final class Pricing
{
    private static final long DEFAULT_PRICE = 1L;

    private static final Map<Item, Long> SEED_PRICES = new HashMap<>();
    static
    {
        seed(Items.DIRT, 1); seed(Items.COBBLESTONE, 1); seed(Items.SAND, 1); seed(Items.GRAVEL, 1);
        seed(Items.NETHERRACK, 1); seed(Items.SOUL_SAND, 1); seed(Items.OAK_LOG, 2); seed(Items.SPRUCE_LOG, 2);
        seed(Items.BIRCH_LOG, 2); seed(Items.JUNGLE_LOG, 2); seed(Items.ACACIA_LOG, 2); seed(Items.DARK_OAK_LOG, 2);
        seed(Items.MANGROVE_LOG, 2); seed(Items.CHERRY_LOG, 2);

        seed(Items.SUGAR_CANE, 2); seed(Items.CACTUS, 2); seed(Items.WHEAT, 2); seed(Items.CARROT, 2);
        seed(Items.POTATO, 2); seed(Items.BEETROOT, 2); seed(Items.PUMPKIN, 3); seed(Items.MELON_SLICE, 1);
        seed(Items.NETHER_WART, 3);

        seed(Items.RAW_IRON, 6); seed(Items.RAW_GOLD, 10); seed(Items.RAW_COPPER, 3); seed(Items.COAL, 3);
        seed(Items.DIAMOND, 50); seed(Items.EMERALD, 40); seed(Items.LAPIS_LAZULI, 3); seed(Items.REDSTONE, 2);
        seed(Items.QUARTZ, 3); seed(Items.AMETHYST_SHARD, 4);

        seed(Items.STRING, 2); seed(Items.BONE, 2); seed(Items.GUNPOWDER, 3); seed(Items.ROTTEN_FLESH, 1);
        seed(Items.SPIDER_EYE, 2); seed(Items.ENDER_PEARL, 15); seed(Items.SLIME_BALL, 3); seed(Items.FEATHER, 1);
        seed(Items.LEATHER, 3);

        seed(Items.LAVA_BUCKET, 20); seed(Items.WATER_BUCKET, 5); seed(Items.MILK_BUCKET, 5);
    }

    private static void seed(Item item, long price)
    {
        SEED_PRICES.put(item, price);
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
