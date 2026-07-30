# Quest line

A starter set of quests for pointing a questing mod (see [RECOMMENDED-MODS.md](RECOMMENDED-MODS.md)
for FTB Quests, currently a `localRuntime` dev mod but not yet wired up with real quest content)
at this mod's own progression. These are written as plain descriptions - title, goal, how to
detect completion, suggested reward - not an actual FTB Quests chapter file (SNBT under
`config/ftbquests/quests/`) yet. Whoever wires these up for real should treat this as the design,
not a stand-in for it.

## 1. Open for Business

**Goal:** Place your Drakonix Block Shop.

**Detection:** FTB Quests' "Placed Block" task, block = `drakonixoneblockshop:drakonix_block_shop`.

**Reward:** A stack of oak logs, or nothing - this one's really just a tutorial checkpoint.

## 2. First Sale

**Goal:** Sell any item at your shop (drop it in the Sell tab, or hopper it in).

**Detection:** No dedicated advancement/stat for "sold an item" exists yet - the practical proxy
is an "Item" task for holding any Drakonix OneBlockShop Token (`token_1` through `token_8192`,
see `OneBlockShopMod.TOKEN_DENOMINATIONS`), since selling is the only way to get one. A real
"sold an item" stat/advancement trigger would be a small follow-up if this proxy ever feels off
(e.g. a player who only ever buys/expands, never earning a token from a *sale* specifically -
not currently possible, since Buy/Border only spend tokens, never mint them).

**Reward:** A handful of tokens to jump-start their first Border purchase.

## 3. Room to Grow

**Goal:** Buy your first Border expansion.

**Detection:** An "Item" task for the Border Expansion Trophy (`drakonixoneblockshop:border_trophy`,
see `Border.giveTrophy`) - minted automatically on every successful expansion, so this needs no
new hook. Require a count of 1 (or more, for a repeatable/tiered version of this quest later -
the trophy's `expansion_number` custom data component records exactly which purchase minted it,
if a quest predicate ever needs to key off a specific tier instead of just "at least one").

**Reward:** A meaningful chunk of tokens, since the player just paid a real cost to get here.

## 4. Beyond the Border

**Goal:** Complete a full Explore-tab expedition (open a portal, walk through, survive until your
timer returns you home - or drink the early-return potion).

**Detection:** No dedicated stat/advancement exists for "completed an expedition" either - the
practical proxy is an "Item" task for the unsellable return potion the Explore tab hands out on
arrival (see `Expedition.java`), since receiving one only happens by actually going. A cleaner
signal (fired specifically on return, not just arrival) would need a small new hook in
`Expedition.endExpeditionHold`-adjacent code - worth adding if this proxy causes false positives
in practice.

**Reward:** A rare vanilla item the Buy tab doesn't sell (a totem of undying, say) - a "you
survived" prize.

## 5. Free Gear

**Goal:** Claim a starter pack from the shop's Packs tab (AE2, Mekanism, EnderIO, or the guide
book reissue).

**Detection:** An "Item" task matching any of the kit's contents is the simplest option (see
`StarterPacks.java` for what each pack actually hands out) - there's no per-pack "claimed" flag
exposed outside the mod to hook an advancement off of directly.

**Reward:** Nothing needed - the pack itself is the reward. This quest exists mainly to surface
the Packs tab's existence to players who might not have noticed it.
