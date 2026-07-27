package com.drakonix.oneblockshop;

import net.neoforged.neoforge.common.ModConfigSpec;

// Standard NeoForge TOML config (registered as ModConfig.Type.COMMON in OneBlockShopMod's
// constructor) - lands at config/drakonixoneblockshop-common.toml, same place/format every
// other NeoForge mod's config lives, editable without recompiling and reloadable via /reload.
public final class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Default matches the README's "up to 1,000,000 blocks out". Max is Integer.MAX_VALUE / 4,
    // not MAX_VALUE itself - Expedition.rollDestination spreads this into "RANGE * 2 + 1" to
    // roll a coordinate, which would silently overflow int and wrap negative for a max value
    // any higher than this.
    public static final ModConfigSpec.IntValue EXPLORE_RANGE = BUILDER
            .comment("How far out (in blocks, in any direction from the border) an Explore-tab teleport can land.")
            .defineInRange("exploreRange", 1_000_000, 1, Integer.MAX_VALUE / 4);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
