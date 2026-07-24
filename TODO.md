# TODO

Tracked against README.md's feature list.

## Not built yet

Nothing currently - see README.md's feature list, everything there is implemented.

## Known shortcuts (fine for now, revisit if they bite)

- World border is one shared border for the whole overworld, not per-player. Fine for
  singleplayer/LAN; real multiplayer would need virtual per-player borders.
- Hopper auto-sell refuses to sell if the block's owner is offline (item just stays put
  upstream) rather than crediting them anyway or queuing the sale.
- `Pricing` derives recipe-based prices as ingredient-sum-over-yield only - no fuel cost,
  mining difficulty, or drop rarity modeling. The ~40-item seed table (raw resources with no
  recipe) is still hand-set and may need tuning/expansion as gaps get noticed.
- No recipe-unlock advancement for the shop block recipe — it's craftable, just doesn't
  auto-unlock in the recipe book.
- Shop GUI background is a plain fill, no custom panel art (the block itself has a texture).
- Pricing wasn't verified with an automated test (no practical way to unit-test against live
  registries/RecipeManager outside a running client) - only compiled and boot-tested. Actual
  sell-price correctness across a range of items still wants a manual in-game pass.
- `Config.java`'s `defineListAllowEmpty` call is a deprecated NeoForge config API - compiles
  fine, pre-existing, unrelated to anything touched this round. Cosmetic, low priority.
- Buy/Border/Sell tab buttons and the sell-slot highlight are laid out by hand-tuned pixel
  constants in `ShopScreen` (no layout system) - fine at 176x166 with 4 buy offers, would need
  re-tuning if the offer list or image size grows much.

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
