# TODO

Tracked against README.md's feature list.

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
- **Tech-mod sell prices** (AE2, IC2, GregTech, Thermal Expansion, EnderIO, etc.) - separate
  from the pipe compatibility above. Craftable items from these mods already price correctly
  today: `Pricing` derives a price recursively from whatever recipe made them, and
  `RecipeManager` includes every loaded mod's recipes for free. The gap is their *raw*/base
  items with no recipe (ores, raw dusts, resources unique to a tech mod) - those fall back to
  `DEFAULT_PRICE` (1), which will feel wrong for anything meant to be a mid/late-game resource.
  Can't just add `Items.SOME_GREGTECH_ORE` to `SEED_PRICES` like the vanilla entries - these
  mods are optional, not a compile dependency, so their `Item` classes may not exist at all.
  Needs a data-driven seed table instead: a JSON mapping item ID strings (e.g.
  `"gregtech:copper_ore"`) to a price, resolved at runtime via
  `BuiltInRegistries.ITEM.get(ResourceLocation)` - an ID for an absent mod's item just resolves
  to nothing/air and is skipped, no hard dependency, no crash if the mod isn't installed.

## Known shortcuts (fine for now, revisit if they bite)

- World border is one shared border for the whole overworld, not per-player. Fine for
  singleplayer/LAN; real multiplayer would need virtual per-player borders.
- Hopper auto-sell refuses to sell if the block's owner is offline (item just stays put
  upstream) rather than crediting them anyway or queuing the sale.
- `Pricing` derives recipe-based prices as ingredient-sum-over-yield only - no fuel cost,
  mining difficulty, or drop rarity modeling. The ~40-item seed table (raw resources with no
  recipe) is still hand-set and may need tuning/expansion as gaps get noticed.
- Shop GUI background is a plain fill, no custom panel art (the block itself has a texture).
- Pricing wasn't verified with an automated test (no practical way to unit-test against live
  registries/RecipeManager outside a running client) - only compiled and boot-tested. Actual
  sell-price correctness across a range of items still wants a manual in-game pass.
- Buy/Border/Sell tab buttons and the sell-slot highlight are laid out by hand-tuned pixel
  constants in `ShopScreen` (no layout system) - fine at 176x166 with 4 buy offers, would need
  re-tuning if the offer list or image size grows much.
  - Idea: ShopScreen should scroll or resize dynamically to fit the offer list and image size.

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
