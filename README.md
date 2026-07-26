# Minecraft: Drakonix One Block Shop

A NeoForge 1.21.1 challenge mod. You start on a single block inside a 1x1 vertical world
border. Sell items at your shop block to earn money, then spend it to expand the border.

## Links

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/drakonix-one-block-shop)
- [Download the mod from GitHub releases](https://github.com/meltingscales/MinecraftDrakonixOneBlockShop/releases)

## How it works

- You start with an iron pickaxe, a Drakonix Block Shop item, a guide book, and 4 oak logs,
  inside a 1x1 world border centered on your spawn point. Straying more than 5 blocks past the
  border teleports you back to its center.
- The Drakonix Block Shop block (craftable from 8 gold ingots around a hopper) has a GUI with
  four tabs:
  - **Sell** — drop an item in to sell it instantly. Every item is sellable: raw resources use
    a hand-set price, crafted items derive their price recursively from their cheapest recipe.
    Hover an item for its sell price.
  - **Border** — spend your balance to expand the world border. Cost grows ~20% per purchase,
    there's a 30-second cooldown between purchases, and every expansion after the first calls
    in a monster wave sized to how many you've already bought.
  - **Buy** — buy a short list of items you can't easily produce early on (sugarcane, cactus,
    lava bucket, water bucket) at a flat price.
  - **Explore** — free random teleport up to 10,000 blocks out, for easier resource gathering.
    You're auto-returned to where you left after 10 minutes, with chat/GUI warnings at 5, 3, 2,
    and 1 minute(s) left; can't teleport again until you're back. An "Expedition" status effect
    for the trip's duration blocks placing a new shop block while you're away.
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

`runClient` also pulls in a handful of dev-only playtesting mods (tech mods for pricing
compatibility testing, etc.) - see `RECOMMENDED-MODS.md` for the list and why each is there.

## Releasing

`gradle.properties`' `mod_version` is the single source of truth for the mod's version. To cut
a release:

1. Bump `mod_version` in `gradle.properties`, commit.
2. Run `just release` (tags the commit `vX.Y.Z` from the current `mod_version` and pushes the
   tag), or do it by hand: tag the commit `vX.Y.Z` and push the tag.
3. CI builds the jar and publishes a GitHub Release with it attached — see
   `.github/workflows/release.yml`. The tag/version mismatch check fails the build if they
   don't match.

Every push and PR also runs `.github/workflows/build.yml` (compile + build, no release).

See `TODO.md` for known shortcuts and polish items.

## License

Public domain — [CC0 1.0](LICENSE).
