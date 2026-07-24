# TODO

Tracked against README.md's feature list.

## Not built yet

- **Buy GUI** — README says the shop should let you buy items at a high price when you can't
  produce them yet (sugarcane, lava buckets, etc). Only the sell side exists right now.

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


## other

❯ i noticed the player spawned outside of the 1x1 border - make sure that: 1. they start inside it, and 2. if they leave it by >5 blocks, they get teleported to the center

❯ i also noticed that "Balance" in the GUI doesn't automatically update, you need to close and re-open the GUI

❯ also, make the Drakonix Block Shop block breakable with an iron pickaxe.
