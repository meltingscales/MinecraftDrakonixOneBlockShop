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
- No light-questing setup yet (e.g. FTB Quests / Better Questing pointed at this mod's
  progression) - deliberately deferred, a sizable feature on its own; revisit once the base pack
  above is confirmed working.
- Our own mod isn't in the pack yet - `packwiz modrinth add drakonix-one-block-shop` would work
  the same way once/if a given `mod_version` is actually published to Modrinth (the README
  already links a Modrinth project page), but that's a separate question from what this session
  built (syncing the *dependency* mods only).

## Not built yet

- Exploration-encouraging mods: added When Dungeons Arise + Explorer's Compass (see
  TODO-DONE.md). Still open: baubles/accessory-slot mods (Curios API is the modern successor,
  real and available for NeoForge 1.21.1) and artifact mods with powerful gear that spawns in
  chests - not picked yet, no candidates vetted.

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
- Tag-based tech-mod pricing (`pricing/seed_prices_by_tag.json`) only covers `c:raw_materials`,
  `c:ores`, and `c:dusts` for a bounded list of common metals (copper, tin, zinc, aluminum,
  lead, nickel, silver, osmium, platinum, uranium, iridium) - not gems, and not every metal a
  given tech mod might add. Deliberately narrow: those three tag families are the recipe-less
  root of an ore-processing chain (nothing crafts them), so they're the one tier the recursive
  pricer genuinely can't reach on its own. Expand the metal list or add gem tags if a real gap
  shows up in practice.
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
