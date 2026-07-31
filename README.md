# Minecraft: Drakonix One Block Shop

A NeoForge 1.21.1 challenge mod. You start on a single block inside a 1x1 vertical world
border. Sell items at your shop block to earn Drakonix OneBlockShop Tokens, then spend them to
expand the border.

## Links

- [Mod Guide](./GUIDE.md)
- [Quest Line](./QUEST-LINE.md) - starter quests to point a questing mod (FTB Quests) at
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/drakonix-one-block-shop)
- [Modrinth](https://modrinth.com/project/drakonix-one-block-shop)
- [Download the mod from GitHub releases](https://github.com/meltingscales/MinecraftDrakonixOneBlockShop/releases)
  - You can install it with NeoForge directly or import it with Prism Launcher.

## How it works

- You start with an iron pickaxe, a Drakonix Block Shop item, a guide book, 4 oak logs, and (if
  Explorer's Compass is installed) an Explorer's Compass, inside a 1x1 world border centered on
  your spawn point. Straying more than 5 blocks past the
  border teleports you back to its center. `keepInventory` and `mobGriefing` are set for you
  automatically on first join - a 1-block border makes both spawning over lava/void and a stray
  creeper/enderman a much bigger deal than usual, and not something you could realistically
  prepare for or wall off yet.
- The Drakonix Block Shop block (craftable from 8 gold ingots around a hopper) has a GUI with
  six tabs:
  - **Sell** — drop an item in to sell it instantly. Every item is sellable: raw resources use
    a hand-set price, crafted items derive their price recursively from their cheapest recipe.
    Hover an item for its sell price.
  - **Border** — spend your balance to expand the world border. Cost grows ~20% per purchase,
    there's a 30-second cooldown between purchases, and every expansion after the first calls
    in a monster wave sized to how many you've already bought. Every successful expansion also
    mints an unsellable Border Expansion Trophy straight into your inventory - one item id for
    every tier, which purchase earned it is just a data tag on the item - handy as an easy
    "has this player upgraded their border" check for a questing mod (see
    [QUEST-LINE.md](./QUEST-LINE.md)).
  - **Buy** — buy a short list of items you can't easily produce early on (sugarcane, cactus,
    lava bucket, water bucket) at a flat price.
  - **Packs** — free tech-mod starter kits (blocks/machines/power/conduits) for AE2, Mekanism,
    and EnderIO, an easier on-ramp for players who'd rather skip building up that mod's
    automation chain from scratch, plus a "Drakonix Guide" entry to reissue a fresh guide book
    if you lost yours. No cost, but each pack has its own 1-hour cooldown per player.
  - **Explore** — "Open Portal" and "Open Portal (Cave Only)" don't teleport you instantly:
    each opens a portal (particle effect, no real block) above that shop block for 30 seconds
    with one random destination up to 1,000,000 blocks out (configurable - `exploreRange` in
    `config/drakonixoneblockshop-common.toml`), rerolled if it would land in an ocean.
    Anyone who walks into it before it closes goes there together - a free team trip if more
    than one of you is nearby, purely by walking in at the same time, no separate team button or
    setup needed. Each traveler still gets their own stay (per-player, configurable on the
    Settings tab - default 10 minutes) and personal auto-return to wherever they individually
    left from, with chat/GUI warnings at 5, 3, 2, and 1 minute(s) left; can't walk into another
    portal while you're still away. The normal button lands on the surface most of the time with
    a chance of dropping you into a (dry - never underwater) cave instead; the Cave Only button
    always finds one. An "Expedition" status effect for the trip's duration (hover it for a
    tooltip explaining why) blocks placing a new shop block while you're away. You also get an
    unsellable potion on arrival - drink it any time to come back early instead of typing a
    command; it also grants 30 seconds of immunity to being pulled back through a still-open
    portal, since it returns you to right where you left from. Dying on an expedition doesn't end
    it - you just respawn normally and get handed a second potion that teleports you back to
    exactly where you died (with 30 seconds of near-total damage resistance, so you're not just
    dropped straight back into whatever killed you) so you can pick up where you left off - or
    wait it out; either way you're still auto-returned to base once your timer is up.
  - **Settings** — per-player difficulty options. "Randomize Prices" toggles a seed-based
    multiplier (0.25x-4x) on every item's buy and sell price, deterministic per world so it's
    the same "random" economy all game and identical for every player who turns it on; the
    expedition-length stepper adjusts your own stay in 5-minute steps (1-60 minute range); and
    "Enable Permanent Hard Mode" locks both of those settings for good - the only way back is an
    op running `/drakonixoneblockshop hardmode unlock <player>`.
- Hopper items into the shop block to auto-sell them. Every 5 minutes you get a chat report
  prefixed `[Drakonix Shop]` of what your hopper automation sold (or "No hopper automation" if
  none sold anything) - the prefix makes it obvious which mod that recurring message is from.
  If a vanilla chest sits next to the shop block, hopper-triggered sales bank their proceeds as
  physical tokens in that chest instead of your inventory - handy for an always-on sell line that
  piles up cash for you to collect later rather than needing you online to receive it.
- Starter items (pickaxe, shop block, guide book) carry an `Unsellable` curse so they can't be
  accidentally sold or hoppered away.
- Money is physical: Drakonix OneBlockShop Token items, denominations 1 through 8192 (doubling
  each step), minted straight into your inventory on a sale and taken back with correct change
  on a purchase - see "Token currency" below. Tokens are `Unsellable` too, same as starter items.
- The guide book (a written book, populated from `GUIDE.md`) suggests automation: mob farms,
  fishing, sugarcane/cactus redstone.
- `/drakonixoneblockshop help` lists every subcommand; `/drakonixoneblockshop expedition end`
  lets any player cut their own Explore-tab trip short instead of waiting out the countdown.
  `balance`/`border`/`starterkit`/`devcheat` subcommands are op-only, for testing/admin use.
  `devcheat` skips straight to things that otherwise take real time to test: an expedition
  teleport without the portal, fast-forwarding an active expedition's countdown, forcing the
  hopper-sales report or a border mob wave right now, or force-closing a stuck portal.
- Multiplayer-friendly by not trying to be per-player: once a second player's ever been online,
  the one shared world border can never be smaller than 17x17 again (never shrinks it if
  already bigger) - fair starting room without the mixin work real per-player borders would
  need. Everyone shares the same border/upgrade progress; each player's Explore-tab trip is
  still independent of everyone else's. `/drakonixoneblockshop border simulatejoin` (op-only)
  applies the same clamp without needing a real second account, for testing solo.

## Token currency

There's no numeric balance stored anywhere - `Wallet.get(player)` just sums the value of every
Drakonix OneBlockShop Token item sitting in a player's inventory (`Wallet.java`). Fourteen
denominations, powers of two from 1 to 8192 (`OneBlockShopMod.TOKEN_DENOMINATIONS`); the item
models, textures (hue-shifted per denomination), lang entries, and combine/split recipes are all
generated by `tools/generate_tokens.py`, not hand-authored - rerun it after changing the
denomination list or sprite design rather than hand-editing the generated files.

- Craft two identical tokens together to combine them into one of the next size up (1+1→2,
  2+2→4, ... 4096+4096→8192). Craft a single token alone to split it into two of the size below.
- A sale mints tokens for the total price; a purchase removes value and mints back the
  difference as change. Both directions work the same way under the hood: every `Wallet.add`
  call clears all token items from the player's inventory and re-mints the resulting total from
  scratch via greedy denomination decomposition - optimal (fewest tokens) for a canonical system
  like powers of two, so this "solves" the change-making problem without needing a real solver.
- An offline player's shop can still sell (hopper automation keeps running) - `Wallet.
  creditOffline` queues the amount as a plain number since there's no loaded inventory to mint
  into, paid out as real tokens the next time that player logs in.

## Building & running

Requires JDK 21.

    ./gradlew runClient   # launch a dev client
    ./gradlew build       # build the mod jar (build/libs/)

`runClient` also pulls in a handful of dev-only playtesting mods (tech mods for pricing
compatibility testing, etc.) - see `RECOMMENDED-MODS.md` for the current list and why each is
there, and `FUTURE-MOD-COMPAT.md` for ones verified compatible before but deliberately not
currently included (e.g. GregTech CEu Modern).

## Modpack

`./modpack/` is a [packwiz](https://packwiz.infra.link/) pack mirroring `build.gradle`'s
`localRuntime` mods - run `just modpack-sync` after adding/removing/repinning one there (or
`python3 tools/generate_modpack.py` directly) to regenerate `modpack/mods/*.pw.toml`, and
`just modpack-serve` to test it with a packwiz-compatible launcher (e.g. Prism). CI fails if the
committed `modpack/` ever drifts from `build.gradle`. See `TODO.md` for what's still undecided
(which mods actually belong in a *shipped* pack vs. this dev-convenience list, light questing).

Every [release](#releasing) attaches a ready-to-use `drakonix-one-block-shop-<version>.mrpack`
alongside the mod jar - download it and use Prism Launcher's "Import from zip" (or drag-and-drop
onto the instance list) to get the mod plus every dependency in one step, no manual mod-hunting.

This pack (30+ mods, several of them heavy tech mods) runs best with **at least 6-8 GB** of RAM
allocated. Neither `pack.toml` nor the exported `.mrpack` format (`modrinth.index.json`) has a
memory field at all - it's a per-instance launcher setting, not something a modpack file can
specify, so set it by hand after importing: in Prism, right-click the instance -> Edit -> Settings
-> Java -> check "Memory" and set Maximum to 8192 MB (or your launcher's equivalent).

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

See `TODO.md` for known shortcuts and open items, or `TODO-DONE.md` for what's already shipped.

## Adding the quest line to the modpack

[QUEST-LINE.md](./QUEST-LINE.md) is a design doc (5 starter quests, goal/detection/reward each) -
not an actual FTB Quests chapter file yet. Once it's built out for real in-game, here's how it
gets into `./modpack/`:

- Quest **definitions** (chapters, tasks, rewards) live in `config/ftbquests/quests/` -
  `data.snbt`, `chapter_groups.snbt`, `chapters/*.snbt`, `lang/en_us.snbt`. This is ordinary
  instance config, the same as any other mod's - it's *not* stored per-world.
- Quest **progress** (which player/team has completed what) is the part that's per-world, kept
  separate under `saves/<world>/ftbquests/` (team-UUID-named files) - never bundle this into the
  modpack, or every player starts with someone else's progress already claimed.

To author and ship a real quest line:

1. Launch the pack, join a world, and run `/ftbquests editing_mode` (op-only) to turn on the
   in-game quest editor.
2. Build out the quests from QUEST-LINE.md using FTB Quests' own GUI - e.g. quest 3 ("Room to
   Grow") as an "Item" task matching `drakonixoneblockshop:border_trophy`, quest 1 ("Open for
   Business") as a "Placed Block" task matching `drakonixoneblockshop:drakonix_block_shop`, etc.
   `/ftbquests reload` re-reads the `.snbt` files from disk without a restart if you hand-edit
   them directly instead.
3. Turn `editing_mode` back off (and optionally `/ftbquests locked` to lock the book) once done,
   so players can't accidentally edit the shipped quest line themselves.
4. Copy the resulting `config/ftbquests/quests/` folder into `modpack/config/ftbquests/quests/`
   in this repo - same mechanism already used for `modpack/config/Veinminer/settings.json`,
   packwiz ships any non-metafile placed under the pack root verbatim to every installed
   instance. Do **not** copy anything from `saves/<world>/ftbquests/` - that's the progress data
   from the previous step, not part of the pack.
5. Run `just modpack-sync` (its trailing `packwiz refresh` picks up new non-mod override files
   too, not just the `localRuntime` mod list) and commit the result.

## License

Public domain — [CC0 1.0](LICENSE).
