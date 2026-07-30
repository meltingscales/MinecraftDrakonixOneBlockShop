# TODO

Tracked against README.md's feature list.

## Modpack
- `./modpack/` is a real `packwiz` pack now (see "Fixed" in TODO-DONE.md for how it's wired up).
  Still open: it currently mirrors *every* `localRuntime` mod in `build.gradle` 1:1, including
  ones that were added purely for dev/playtesting convenience (Twerk Crop Growth, JEI, Xaero's
  Minimap, VeinMiner, TreeChop) rather than as something meant to ship to players. Worth a real
  decision on whether the shipped pack should be that full list, or a curated subset - if the
  latter, `tools/generate_modpack.py` would need a way to mark a `localRuntime` line as
  "dev-only, don't ship" (a comment convention it greps for?) rather than syncing 1:1.
- FTB Quests (+ its FTB Library/Teams dependencies) is now a `localRuntime` dev mod (see
  RECOMMENDED-MODS.md - CurseForge-exclusive, pulled via cursemaven) but no actual quest content
  pointed at this mod's own progression (border expansions, Explore-tab trips, starter packs) has
  been authored yet - a sizable content-writing task on its own, separate from just having the
  mod installed.
- Our own mod isn't in the pack yet - `packwiz modrinth add drakonix-one-block-shop` would work
  the same way once/if a given `mod_version` is actually published to Modrinth (the README
  already links a Modrinth project page), but that's a separate question from what this session
  built (syncing the *dependency* mods only).

## Not built yet

- **Tech-mod item pipe/pipe-equivalent compatibility** (AE2, IC2, GregTech, Thermal Expansion,
  EnderIO, etc.) - `ShopBlockEntity` only implements vanilla `WorldlyContainer`, which covers
  hoppers/droppers. Most modern tech mods push items via the NeoForge Capabilities API
  (`IItemHandler`) instead of touching `Container` directly - an AE2 import bus, EnderIO
  conduit, or Thermal/GregTech pipe likely can't see the shop block at all right now. Would
  need:
  - Register an `IItemHandler` capability for the block via `RegisterCapabilitiesEvent`,
    wrapping the existing single-slot `Container` (e.g. via `InvWrapper` or a small custom
    handler) so those mods' pipes/buses/conduits can insert automatically.
  - `HopperSalesTracker`'s hopper-vs-GUI detection (`canPlaceItemThroughFace`) won't fire for
    capability-based insertion - it's a third path, neither hopper nor GUI. Sales report would
    need a broader "automated insertion" category, or a separate one for capability-driven
    sales, to still catch these.

## Known shortcuts (fine for now, revisit if they bite)

- The new Settings tab (Randomize Prices / expedition-minutes stepper / Permanent Hard Mode) and
  the word-wrapped info/warning lines added right after it (`ShopScreen.drawWrapped`, covering
  every tab's description/warning text, not just Settings') boot-test clean but haven't been
  interactively verified - no computer-use tool for the game window to actually open the shop,
  click through each tab, and confirm nothing overlaps or reads wrong. Worth a real playtest to
  confirm the layout before trusting it fully, same caveat as the EnderIO conduit interop below.
- `ShopBlockEntity.trySell`'s randomized-price lookup (PlayerSettings' Randomize Prices toggle)
  only applies when the shop's owner is currently online - an offline owner's hopper-triggered
  sale falls back to the plain price for that one sale, since checking the toggle needs a live
  `Player` to read the attachment from and this mod doesn't have a way to peek at an offline
  player's saved data without loading them. Rare in practice (only matters for the exact sales
  that happen while randomization is on and the owner happens to be offline), and never loses
  value - just occasionally uses the un-randomized price instead.
- `StarterPacks.enderioConduit`'s generic runtime interop with EnderIO's `Conduit` registry
  (see TODO-DONE.md) boot-tests clean but hasn't been interactively verified - no computer-use
  tool for the game window to actually click "Claim: EnderIO" and inspect the resulting item.
  Should read as a properly-typed conduit (Energy/Item/Fluid/Redstone Conduit) rather than a
  blank "<MISSING> Conduit" - worth a real playtest to confirm before trusting it fully.
- Dying more than once on the same expedition before drinking any Expedition Resume Potion
  leaves the older potions in inventory pointing at the *newest* death spot, not the one they
  were handed at (`DEATH_X/Y/Z` is a single per-player attachment overwritten by each death, not
  baked into the potion item itself at creation time) - harmless (arguably the more useful
  behavior anyway, and the old potions just become redundant duplicates), just not literally
  "the spot each specific potion promised."
- Buy tab button labels ("Mangrove Propagule (3)", "Dark Oak Sapling (3)") are longer than the
  ~88px button width comfortably fits - vanilla `Button` just center-clips long text rather than
  erroring, so it's cosmetic, not broken. Would need shorter labels (icons instead of full names?)
  or a wider GUI to fix properly.
- `Pricing` derives recipe-based prices as ingredient-sum-over-yield only - no fuel cost,
  mining difficulty, or drop rarity modeling. The seed table (`pricing/seed_prices.json`) is
  still hand-set and may need tuning/expansion as gaps get noticed.
- Shop GUI background is a plain fill, no custom panel art (the block itself has a texture).
- Border-expansion mob wave is a fixed mob pool (zombie/skeleton/spider) and a flat linear size
  curve (`WAVE_BASE_SIZE + purchaseCount`, capped) - no scaling by world difficulty, day count,
  or distance from spawn, and no variety beyond those three types. Fine as a first pass; revisit
  if waves feel same-y at higher expansion counts. Spawns land inside the border now (not on an
  outside ring - vanilla's `WorldBorder` collision blocks any entity from crossing it, so an
  outside spawn could never reach the player) and only fire once the border is bigger than 6x6,
  see TODO-DONE.md.
- `PricingSeedTest` only validates `seed_prices.json`'s structure (parses, item-ID-shaped keys,
  positive prices) - it can't confirm those IDs actually resolve to real items, and it doesn't
  touch the recursive recipe-derived pricing at all. Both need a live `RecipeManager` +
  populated item registry, which plain JUnit can't get here: NeoForge patches
  `BlockBehaviour.Properties` to consult FML's `LoadingModList`, which is only populated when
  the game is launched through ModLauncher (`runClient`/`runServer`/`runGameTestServer`) -
  `Bootstrap.bootStrap()` throws NPE outside that. NeoGradle exposes a `testJunit` task/
  `writeMinecraftClasspathJunit` that looks built for exactly this, but its sourceSet wiring
  wasn't obvious from a quick look - worth another pass if this bites.
- Buy/Border/Sell tab buttons and the sell-slot highlight are laid out by hand-tuned pixel
  constants in `ShopScreen` (no layout system) - fine at 176x166 with 4 buy offers, would need
  re-tuning if the offer list or image size grows much.
  - Idea: ShopScreen should scroll or resize dynamically to fit the offer list and image size.
- Tag- and seed-based pricing was audited against a real registry dump (every item across
  vanilla + every `localRuntime` mod, cross-referenced against every recipe output vanilla and
  each tech mod ships, including EnderIO's jarjar-nested sub-jars) rather than guessed - see
  TODO-DONE.md. Covers vanilla ore blocks/stripped logs/pottery sherds/music discs/copper
  oxidation states/rare no-recipe loot, plus every GeOre material, Mekanism's full ore-processing
  chain (ore/raw/dust/shard/crystal/dirty_dust/clump), Create's crushed-raw ores, and AE2's
  certus quartz family. Not literally exhaustive - vanilla's ~250 remaining recipe-less items
  (flowers, corals, saplings, vines, spawn eggs, command/technical blocks) are cosmetic/creative-
  only and were deliberately left at the `DEFAULT_PRICE` fallback rather than hand-priced, along
  with EnderIO/Create/Mekanism/AE2 items whose names didn't look like a raw-resource root when
  audited. Re-run the same audit if a new mod's raw resource turns out to default to 1.
- The 26 token combine/split recipes (`tools/generate_tokens.py`) have no recipe-book unlock
  advancement - they still work fine in survival (a crafting-grid match doesn't require the
  recipe to be "known", only the recipe book's highlighting does), they just won't show up in
  the recipe book until/unless something else unlocks them. Add per-recipe unlock advancements
  (triggered on `minecraft:has_item` for each token) if that's ever noticed as friction.
- Only one Explore-tab portal can be open at a time, server-wide (`Expedition.PORTAL_END_TICK`
  is a single ServerLevel-scoped value, not per-shop-block) - clicking "Open Portal" on a second
  shop block while one's already open elsewhere just no-ops rather than queuing or replacing it.
  Fine at this mod's scale (a handful of players, one or two shop blocks); would need a
  `Map<BlockPos, PortalState>` instead of flat attachments if multiple simultaneous portals
  across different shop blocks ever matters.

## Done

Finished items have moved to [TODO-DONE.md](TODO-DONE.md) to keep this file focused on what's
still open.
