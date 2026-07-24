# Minecraft: Drakonix One Block Shop

A NeoForge 1.21.1 challenge mod. You start on a single block inside a 1x1 vertical world
border. Sell items at your shop block to earn money, then spend it to expand the border.

## How it works

- You start with an iron pickaxe, a Drakonix Block Shop item, a guide book, and 4 oak logs,
  inside a 1x1 world border centered on your spawn point. Straying more than 5 blocks past the
  border teleports you back to its center.
- The Drakonix Block Shop block (craftable from 8 gold ingots around a hopper) has a GUI with
  three tabs:
  - **Sell** — drop an item in to sell it instantly. Every item is sellable: raw resources use
    a hand-set price, crafted items derive their price recursively from their cheapest recipe.
    Hover an item for its sell price.
  - **Border** — spend your balance to expand the world border. Cost grows ~20% per purchase.
  - **Buy** — buy a short list of items you can't easily produce early on (sugarcane, cactus,
    lava bucket, water bucket) at a flat price.
- Hopper items into the shop block to auto-sell them. Every 5 minutes you get a chat report of
  what your hopper automation sold (or "No hopper automation" if none sold anything).
- Starter items (pickaxe, shop block, guide book) carry an `Unsellable` curse so they can't be
  accidentally sold or hoppered away.
- The guide book (a written book, populated from `GUIDE.md`) suggests automation: mob farms,
  fishing, sugarcane/cactus redstone.

## Building & running

Requires JDK 21.

    ./gradlew runClient   # launch a dev client
    ./gradlew build       # build the mod jar (build/libs/)

## Releasing

`gradle.properties`' `mod_version` is the single source of truth for the mod's version. To cut
a release:

1. Bump `mod_version` in `gradle.properties`, commit.
2. Tag the commit `vX.Y.Z` (matching `mod_version` exactly) and push the tag.
3. CI builds the jar and publishes a GitHub Release with it attached — see
   `.github/workflows/release.yml`. The tag/version mismatch check fails the build if they
   don't match.

Every push and PR also runs `.github/workflows/build.yml` (compile + build, no release).

See `TODO.md` for known shortcuts and polish items.
