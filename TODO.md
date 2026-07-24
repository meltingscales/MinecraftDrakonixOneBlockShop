# TODO

Tracked against README.md's feature list.

## Not built yet

- **Buy GUI tab** — README says the shop should let you buy items at a high price when you
  can't produce them yet (sugarcane, lava buckets, etc). Design decided, not yet coded:
  - Small fixed `BUY_OFFERS` list in `ShopMenu` (record of `Item` + flat price) - unlike the
    sell side, this doesn't need to be dynamic/recipe-derived, README frames it as a short
    curated list. Seed with sugarcane, cactus, lava bucket, water bucket.
  - Button IDs via `clickMenuButton`: one per offer (`BUY_ITEM_BUTTON_BASE + index`), handled
    server-side (`tryBuy`: check `Wallet.get(player) >= price`, deduct, `player.getInventory().add(stack)`,
    drop at feet if inventory full).
- **GUI tab-switching rework** (blocks the buy tab, do this first): current tabs are hand-rolled
  text hitboxes in `ShopScreen.mouseClicked`, and tab state (`borderTabActive`) only lives
  client-side in the Screen - so the sell slot underneath is still a real, always-active
  `Slot` regardless of which tab is showing. That's the reported bug: switch to Border/Buy
  tab, the Sell tab's slot is still clickable/interactable, just visually unhighlighted.
  Planned fix (confirmed against decompiled vanilla source, not yet implemented):
  - Move tab state into `ShopMenu` itself (`enum Tab { SELL, BORDER, BUY }`, `activeTab` field),
    switched via `clickMenuButton` (client predicts + sends packet, server confirms - same
    dual-execution idiom vanilla's `EnchantmentScreen` uses for its selectable slots, no new
    DataSlot needed since both sides run the same button handler).
  - Override `Slot.isActive()` on the sell slot (anonymous subclass) to return
    `activeTab == Tab.SELL`. `AbstractContainerScreen` already gates rendering, hover, and
    click routing on `Slot.isActive()` (verified in `AbstractContainerScreen.mouseClicked`/
    `findSlot`) - this is the actual native mechanism, no manual hit-testing needed.
  - Replace the hand-rolled tab/buy-button hitboxes with real `net.minecraft.client.gui.components.Button`
    widgets added via `Screen.addRenderableWidget` in `init()`, toggling `.visible` per tab.
    `AbstractContainerScreen.containerTick()` (called every client tick while the menu's open)
    is the right place to sync button visibility/label/color off `this.menu` state (balance,
    border size, current tab) - same idiom other multi-state vanilla screens use.
  - This directly answers "shouldn't we use native GUI libs" - yes, this was the ad-hoc part
    of the implementation, not a fundamental design compromise.

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
