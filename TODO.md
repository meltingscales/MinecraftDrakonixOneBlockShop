# TODO

Tracked against README.md's feature list.

## Not built yet

- **Per-player world borders** — so multiple people can play together, each starting ~10 blocks
  apart and merging once their individually-grown borders touch. Attempted once this session and
  reverted - broke the client (hung at the title screen instead of joining a world) for a reason
  not yet root-caused, so treat the design below as unverified, not a safe starting point.
  Vanilla only has one real `WorldBorder` per level, so "per-player" can't just reuse it as-is.
  What was tried:
  - Each player gets their own center/size (a `Player` attachment, growing via the existing
    purchase-cost curve), rather than the one `ServerLevel`-scoped border used today.
  - New players are placed on a square spiral (world spawn + `SPAWN_SPACING` per ring step) so
    they don't all land on the same point - teleported once at
    `PlayerEvent.PlayerLoggedInEvent`, landing on the heightmap surface.
  - For the client to actually *see* their own border (fog/wall rendering, `ShopScreen`'s
    border-tab math), the server-side real border is left alone (huge/inert) and each player is
    sent a fake `ClientboundInitializeBorderPacket` built from a throwaway `WorldBorder` holding
    their personal center/size - confirmed `ClientPacketListener` mutates the client's local
    `WorldBorder` object in place on receipt, so `Minecraft.level.getWorldBorder()` client-side
    needs zero code changes and just reflects whatever a player was personally sent.
  - Vanilla's actual push-back/damage is hardwired to the level's one real `WorldBorder`
    (`Entity.collectColliders`, `LivingEntity`'s damage tick) - not reachable per-player without
    mixins (this mod has none). So containment would have to be fully custom: per-tick, a
    player is "safe" if standing inside *any* currently-online player's border box, not just
    their own - which is what would make separate borders merge for free once they overlap, no
    extra bookkeeping needed.
  - Suspect areas for the hang, if picked back up: the mid-`PlayerLoggedInEvent` teleport (entity
    may not be fully ready for `teleportTo` at that exact point), or something about sending a
    raw packet via `player.connection.send(...)` outside the normal broadcast path. Worth testing
    each piece (spiral placement, then packet-send, then containment) in isolation rather than
    landing all three at once.
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
  if waves feel same-y at higher expansion counts.
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

## Fixed

- ~~Player could spawn outside the 1x1 border~~ — border now centers on the player's actual
  spawn position instead of the world's nominal spawn point.
- ~~No safety net for leaving the border~~ — straying more than 5 blocks past the edge now
  teleports the player back to center.
- ~~Balance in the shop GUI didn't update live~~ — `ShopMenu` now refreshes it every tick.
- ~~Shop block wasn't breakable with an iron pickaxe~~ — added to `mineable/pickaxe` and
  `needs_iron_tool` tags.
- ~~Sell prices were a hardcoded 6-item map~~ — `Pricing` now sells any item: raw resources
  from a seed table, everything craftable derived recursively from its cheapest recipe.
- ~~Border grew automatically from lifetime earnings~~ — replaced by a purchasable Border tab
  in the shop GUI (see previous commit).
- ~~Starting items (pickaxe, shop block, guide book) could be accidentally sold~~ — new
  `Unsellable` curse (`data/drakonixoneblockshop/enchantment/unsellable.json`, tagged into
  vanilla's `curse` tag so it gets red curse tooltip styling), applied to all three via
  `EnchantmentHelper.updateEnchantments` in `StarterKit`. `ShopBlockEntity` refuses to sell or
  even hopper-accept a cursed stack (`isUnsellable`/`canPlaceItem`). Not obtainable through
  normal enchanting/trading (deliberately left out of `in_enchanting_table`/`treasure` tags).
- ~~Player only started with pickaxe/block/book~~ — also gets 4 oak logs now.
- ~~Buy GUI tab~~ — README's "buy items you can't produce yet" is in: a `BUY_OFFERS` list in
  `ShopMenu` (sugarcane, cactus, lava bucket, water bucket, flat prices), one button per offer,
  `tryBuy` checks balance/deducts/adds to inventory (drops at feet if full).
- ~~GUI tab-switching bug (other tabs' elements stayed clickable)~~ — tab state moved server-
  authoritative into `ShopMenu` (`enum Tab`, synced `DataSlot`); the sell `Slot` now overrides
  `isActive()` to gate on the active tab, which `AbstractContainerScreen` already uses to block
  rendering/hover/clicks on inactive slots - no more manual hitbox testing.
- ~~Hand-rolled text-hitbox tabs/buttons~~ — replaced with real
  `net.minecraft.client.gui.components.Button` widgets (`Screen.addRenderableWidget`), synced
  every client tick via `AbstractContainerScreen.containerTick()`. Answers "shouldn't we use
  native GUI libs" - yes, and now we do.
- ~~Deprecation warning in `ShopBlockEntity.java`~~ — root-caused: `EnchantmentHelper.getItemEnchantmentLevel`
  is deprecated in favor of `ItemStack.getEnchantmentLevel(Holder)` for gameplay checks. Swapped
  over, warning's gone (confirmed via a temporary `-Xlint:deprecation` build).
- ~~Buy/sell GUI didn't show prices~~ — buy tab already labeled each button with its price; sell
  tab now shows a "Sell price: N each" (or "Not sellable" for cursed items) tooltip on hover,
  via `ShopScreen.getTooltipFromContainerItem` - same vanilla tooltip mechanism used for
  enchantment/durability lines, reuses `Pricing.priceOf` client-side (recipe manager and
  registry access are both synced to the client already).
- ~~No periodic hopper-sales report~~ — `HopperSalesTracker` chats every online player a
  per-item summary every 5 minutes ("No hopper automation" if nothing sold via hopper that
  window). Hopper vs GUI sales are told apart by making `ShopBlockEntity` a `WorldlyContainer`:
  only hopper/dropper automation ever calls `canPlaceItemThroughFace` (a plain `Slot`'s default
  `mayPlace` never does), so it's a reliable one-shot marker consumed by the very next
  `setItem`/`trySell` call.
- ~~No recipe-unlock advancement for the shop block recipe~~ — added
  `data/drakonixoneblockshop/advancement/recipes/drakonix_block_shop.json`, same pattern as
  vanilla's own recipe-unlock advancements (unlocks on picking up a gold ingot or already
  knowing the recipe).
- ~~`Config.java`'s deprecated `defineListAllowEmpty` call~~ — that whole class was unmodified
  MDK example boilerplate (dirt-block logging, a magic number) that nothing in the mod actually
  used except registering its (otherwise pointless) config spec. Deleted it instead of patching
  the deprecated call - removes the dead code and the warning together.
- README.md and GUIDE.md cleaned up to match what's actually implemented (was rough scaffold
  notes; GUIDE.md also had a stale line claiming unpriced items "just sit in the slot," no
  longer true since `Pricing` prices everything).
- Added `.github/workflows/release.yml`: tag push (`vX.Y.Z`) builds the jar and publishes a
  GitHub Release, after verifying the tag matches `gradle.properties`' `mod_version` (single
  source of truth for versioning). The existing `build.yml` still runs on every push/PR.
- ~~No fairness net for a rough 1-block spawn (lava, void, etc.)~~ — `Border.initIfNeeded` now
  sets `keepInventory` true on the overworld the first time a world's border is set up. Only
  softens item loss, not the run itself - dying still costs you the challenge. Documented in
  `GUIDE.md`.
- ~~Sell prices were a hardcoded Java `Item` map~~ — extracted to `pricing/seed_prices.json`
  (item ID string -> price), resolved at load against `BuiltInRegistries.ITEM` with an unknown-
  ID skipped rather than crashing. Same design the tech-mod-sell-prices TODO item above already
  called for - modded item IDs can now be added to the same file without a compile dependency
  on those mods. Also expanded the table itself (obsidian, ancient debris, nether star, raw
  meats/fish, mob drops, etc. that had no recipe and were quietly falling back to the 1-coin
  default).
- ~~Pricing had no automated test~~ — added `PricingSeedTest` (plain JUnit, `./gradlew test`,
  already part of `./gradlew build` so both CI workflows run it). Validates `seed_prices.json`
  structurally; see the "Known shortcuts" entry above for what it can't cover and why.
- ~~Hopper auto-sell refused to sell to an offline owner~~ — sale now always goes through.
  If the owner's offline, the amount is stashed in a level-scoped `Wallet.PENDING_CREDITS`
  map (not a raw playerdata file write - safer) and paid out via `Wallet.flushPendingCredits`
  the next time they log in, through the same live-object `Wallet.add` path as any other
  credit.
- ~~Tech-mod sell prices~~ — added `pricing/seed_prices_by_tag.json`, priced by NeoForge's
  common (`c:`) item tags instead of exact item IDs (the modern replacement for the old Forge
  OreDictionary). Covers raw ores/materials/dusts for a bounded list of common tech-mod metals
  (copper, tin, lead, nickel, silver, platinum, osmium, iridium, uranium, zinc, aluminum) -
  whichever mod actually supplies a given metal, its items just need to carry the shared tag to
  price correctly, no compile dependency on any specific mod needed. Deliberately didn't seed
  ingots/nuggets/storage blocks by tag - those are normally one crafting/smelting recipe away
  from their raw material in any mod that registers one, so the existing recursive pricer
  already reaches them without duplicating a second price table for the same tier.
  `PricingSeedTest` extended (now parameterized) to structurally validate this file too.
- ~~No admin/cheat commands~~ — added `/drakonixoneblockshop` (op level 2, same gate as
  `/gamemode`): `balance get/set/add <target> [amount]`, `border get/set/expand` (the shared
  border - `set`/`expand` bypass the shop's purchase cost entirely), `starterkit give <target>`
  (re-issues the kit; extracted `StarterKit.giveItems` out of the login handler so both paths
  share it, doesn't touch the `GIVEN` flag so it can't accidentally suppress a real first-login
  grant).
- ~~Border expansion had no cost beyond coins, and could be spam-clicked~~ — every expansion
  after the first (1x1 -> 3x3 is exempt - a brand-new player has nothing to defend with yet)
  now calls in a monster wave (zombies/skeletons/spiders in a ring just past the new edge,
  `Border.spawnMobWave`), sized to how many expansions have already happened
  (`WAVE_BASE_SIZE + purchaseCount`, capped at `WAVE_MAX_SIZE`). Also added a 30-second cooldown
  between expansions (`Border.cooldownRemainingSeconds`, tracked per the *clicking* player, not
  necessarily the shop's owner - matches who `tryExpand` actually charges). The Border tab shows
  a standing "summons a monster wave" warning and swaps the expand button's label/`active` state
  to a live countdown while on cooldown, synced via a new `ShopMenu` `DataSlot` refreshed every
  tick alongside balance.
- ~~Selling had no audio/visual feedback~~ — `ShopBlockEntity.playSaleFeedback` plays
  `EXPERIENCE_ORB_PICKUP` and pops a small `HAPPY_VILLAGER` particle burst at the block on every
  successful sale (GUI or hopper), broadcast to everyone nearby like any other level sound/
  particle.
- ~~Buy tab offers were a hardcoded Java list~~ — extracted to `pricing/buy_offers.json` (item
  ID -> price, same load-and-skip-unknown pattern as `Pricing`'s seed tables), and expanded from
  4 items to 15: all 8 sapling types + mangrove propagule, both mushrooms, and dirt, alongside
  the original sugarcane/cactus/lava bucket/water bucket. The Buy tab's grid grew from 2 rows to
  8 to fit them, so the whole GUI got taller to make room - `ShopScreen.PLAYER_INVENTORY_Y`/
  `HOTBAR_Y` are now derived from `BUY_OFFERS.size()` instead of hardcoded, and `ShopMenu`'s
  player-inventory slot positions reference those same constants, so the drawn grid and the
  actual slots can't drift apart. `PricingSeedTest` extended to cover this file too.
- Added Mekanism as a dev-only `localRuntime` dependency (see `build.gradle`) so `runClient`/
  `runServer` actually have a real tech mod loaded to playtest the tag-based pricing against -
  it supplies osmium, already in `seed_prices_by_tag.json`. Not a compile dependency, never
  published as a required mod dependency, boots clean alongside our mod. Pinned to `10.7.14.79`
  specifically (not latest) - that's the newest build that only needs `neoforge>=21.1.133`;
  everything from `10.7.15.81` on needs `>=21.1.194`, above this project's pinned `neo_version`
  (21.1.176). Bump the pin only alongside a real `neo_version` upgrade. (Thermal Expansion, the
  mod originally asked for, has no NeoForge release at all - last release is 1.20.1 Forge only.)
