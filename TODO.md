# TODO

Tracked against README.md's feature list.

## Not built yet

- **Buy GUI** — README says the shop should let you buy items at a high price when you can't
  produce them yet (sugarcane, lava buckets, etc). Only the sell side exists right now.
- Literally every item is sellable (and craftable items' prices are derived dynamically from their recipes, so we don't need to hardcode them)

## Known shortcuts (fine for now, revisit if they bite)

- World border is one shared border for the whole overworld, not per-player. Fine for
  singleplayer/LAN; real multiplayer would need virtual per-player borders.
- Hopper auto-sell refuses to sell if the block's owner is offline (item just stays put
  upstream) rather than crediting them anyway or queuing the sale.
- Sell prices are a hardcoded map in `ShopBlockEntity`. Move to a data-driven JSON/tag list
  if the sellable item list keeps growing.
- No recipe-unlock advancement for the shop block recipe — it's craftable, just doesn't
  auto-unlock in the recipe book.
- Shop GUI background is a plain fill, no custom panel art (the block itself has a texture).

## Fixed

- ~~Player could spawn outside the 1x1 border~~ — border now centers on the player's actual
  spawn position instead of the world's nominal spawn point.
- ~~No safety net for leaving the border~~ — straying more than 5 blocks past the edge now
  teleports the player back to center.
- ~~Balance in the shop GUI didn't update live~~ — `ShopMenu` now refreshes it every tick.
- ~~Shop block wasn't breakable with an iron pickaxe~~ — added to `mineable/pickaxe` and
  `needs_iron_tool` tags.
