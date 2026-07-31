# TODO — Done

Finished items, split out of `TODO.md` to keep that file focused on what's still open. Newest
entries at the top; oldest (original MVP build-out) at the bottom.

- Replaced the two Explore-tab potions (vanilla `Items.POTION` with a custom `PotionContents`
  effect, stuck at a stack size of 1) with real stackable custom items: Return Home Scroll
  (`OneBlockShopMod.RETURN_HOME_SCROLL`, ends the expedition early) and Expedition Resume Scroll
  (`EXPEDITION_RESUME_SCROLL`, teleports back to your death spot). Each is a small `Item`
  subclass overriding `use()` directly (`Expedition.ReturnHomeScrollItem`/
  `ExpeditionResumeScrollItem`) instead of routing through an instantaneous `MobEffect` triggered
  by drinking - `RETURN_EFFECT`/`RESUME_EFFECT` are gone, `PORTAL_IMMUNITY_EFFECT` (a real timed
  marker effect, unrelated to how it's triggered) is unchanged. Also now front-loads 2 Expedition
  Resume Scrolls at expedition start (previously only ever earned one at a time, on death) so a
  player always has some in hand even before their first death; still hands out one more on every
  actual death, on top of that. Textures generated via
  `tools/generate_expedition_scroll_textures.py` (`just expedition-scroll-textures-sync`), same
  PPM->PNG pipeline as the Border Expansion Trophy, using each old effect's original color
  (blue/green) as the seal accent so the visual language carries over.
- New recipe: 1x `minecraft:amethyst_block` -> 4x `minecraft:amethyst_shard`
  (`data/drakonixoneblockshop/recipe/amethyst_shard_uncraft.json`) - vanilla only has the forward
  direction (4 shards -> 1 block); this un-crafts a block back down for players who bought/farmed
  blocks in bulk (e.g. via the new Budding Amethyst recipe above) and need loose shards instead
  (spectral arrows, tinted glass, ...).
- New recipe: 9x `minecraft:amethyst_block` -> 1x `minecraft:budding_amethyst`
  (`data/drakonixoneblockshop/recipe/budding_amethyst.json`) - same idea as the Flawless Budding
  Certus Quartz recipe above, vanilla's own budding block is otherwise geode-only with no crafting
  recipe at all. Unlike AE2's budding quartz (which demotes a tier when broken), vanilla's own
  loot table for budding_amethyst drops nothing at all when mined - a placed one is permanent
  until you actually mean to lose it.
- Fixed a real multiplayer bug: any player can open any shop block's GUI (no owner check on
  opening), but `ShopBlockEntity.trySell`'s GUI-driven sale path unconditionally credited the
  block's `ownerUUID`, not whoever actually dropped the item in - anyone selling at someone
  else's shop silently paid that shop's owner instead of themselves. Fixed by threading the
  viewing player through (`ShopMenu`'s constructor now calls `ShopBlockEntity.setViewingPlayer`,
  a transient/non-persisted field) and crediting that player for GUI sales; hopper-triggered
  sales (no live player present) still credit the owner, unchanged. Also fixed the Sell GUI's
  "Balance" display, which had the same bug in reverse (always showed the owner's wallet, even
  though Buy/Border already correctly charged whoever was actually viewing) - now shows the
  viewing player's own balance, consistent with Buy/Border. Removed `Wallet.get(MinecraftServer,
  UUID)`, dead code once the balance display stopped needing an offline-tolerant owner lookup.
- Added QUEST-LINE.md (5 starter quest descriptions for FTB Quests, design doc only - no actual
  SNBT chapter authored yet) and a Border Expansion Trophy (`drakonixoneblockshop:border_trophy`)
  minted on every successful Border purchase (`Border.giveTrophy`) as an easy item-based proof
  for a questing mod to check. One item id for every expansion tier - which purchase earned it is
  recorded as a `custom_data` component (`expansion_number`, `border_size`) instead of needing a
  separate item per tier. Unsellable, same curse mechanism as starter kit items/tokens. Its 16x16
  texture (`tools/generate_border_trophy_texture.py`, `just border-trophy-sync`) is generated via
  an intermediate PPM (binary P6, magenta transparency key) converted to the final RGBA PNG,
  unlike this repo's other icon generators which write PNG pixels directly.
- New AE2 recipe: 9x `ae2:quartz_block` (Certus Quartz Block) -> 1 `ae2:flawless_budding_quartz`
  (`data/drakonixoneblockshop/recipe/flawless_budding_quartz.json`). Normally the top-tier
  budding quartz variant is meteorite-only (AE2's own transform chain only climbs
  damaged->chipped->flawed, never reaching flawless) - this gives players a farmable path to a
  permanent, non-decaying quartz crystal generator instead of relying on meteor RNG.
- Shop GUI polish, both from user feedback on the new Settings tab:
  - The "Enable Permanent Hard Mode" warning line now spells out exactly what it locks
    (Randomize Prices and Expedition Minutes) instead of just "hard mode is permanent" - the
    button alone was ambiguous about what committing actually meant.
  - Added `ShopScreen.drawWrapped` (real word-wrap via `Font.split`, `GuiGraphics.drawString`
    has no multi-line variant of its own) and switched every tab's info/warning text to it - the
    Packs tab's two-line description and the new hard-mode warning are both long enough to
    overflow the GUI's width otherwise. Lines that stack (Packs' two description lines) now
    position the second one dynamically off the first's actual wrapped height instead of a fixed
    Y guess.
  - Fixed the balance label ("Balance: N") clipping into the tab button row right below it -
    moved from y=16 up to y=6 (`TAB_Y` is 18).

- Added a new per-player `PlayerSettings` attachment (`price_randomization`, `expedition_minutes`,
  `hard_mode_locked`) and a 6th shop GUI tab, Settings, to expose them:
  - Randomize Prices toggles a seeded 0.25x-4x multiplier on every buy/sell price. Deterministic
    per (world seed, item) so it's the same "random" economy the whole game, computed
    identically on both client and server without a round trip: `ServerLevel.getSeed()` is
    synced to the client as a 32-bit hash (`ShopMenu.seedHash`) rather than the raw 64-bit seed,
    since `ClientLevel` has no `getSeed()` of its own to compute this independently - see
    `Pricing.randomizedMultiplier`/`applyRandomization`. Buy tab button labels and the Sell tab's
    hover tooltip both now show the live, exact randomized price this way, not an approximation.
  - Expedition time (previously a fixed `Expedition.DURATION_TICKS` constant, 10 minutes) is now
    per-player and adjustable in 5-minute steps (1-60 minute range) via a +/- stepper, replacing
    the constant with `PlayerSettings.getExpeditionMinutes`.
  - Permanent Hard Mode is a one-way lock a player can throw on themselves that freezes both
    settings above - `PlayerSettings.tryEnableHardMode` never undoes itself; the only way back is
    a new op-only `/drakonixoneblockshop hardmode unlock <player>` subcommand
    (`PlayerSettings.adminUnlock`), mirroring every other admin subcommand's `requires(level 2)`
    gate.
  - Known simplification: an offline shop owner's hopper-triggered sale uses the plain
    (unrandomized) price for that one sale rather than loading their saved player data just to
    check the toggle - see TODO.md.

- Fixed crafted tokens (combine/split recipes) losing the Unsellable curse - same class of bug
  as the shop block's loot table fix above: `tools/generate_tokens.py`'s recipe output was a
  plain `{"id", "count"}` result with no components, so combining/splitting a cursed token
  produced a clean, sellable one (crafting results don't inherit ingredient components
  automatically). Added a `"components": {"minecraft:enchantments": {...}}` block to the
  generator's result output (the same `ItemStack.STRICT_CODEC` "components" field EnderIO's own
  conduit recipe uses) and reran `just tokens-sync` to regenerate all 26 recipe files.

- Still a tiny bit of visual cutoff reported after the 250 trim - `PAGE_BUDGET` down to 230.
- Guide book pages were still running a bit tight visually even after the word-wrap pagination
  fix above - trimmed `StarterKit.PAGE_BUDGET` from 260 to 250 chars/page.

- Fixed the Drakonix Guide book's text getting visually cut off on some pages.
  `StarterKit.pages()` split `GUIDE.md` on blank lines and only checked whether a *whole*
  paragraph would fit before adding it to the current page - a paragraph longer than
  `PAGE_BUDGET` (260 chars) still went onto a single page in full instead of being split, and
  several of GUIDE.md's paragraphs had grown past 2x that budget (up to 544 chars) as more
  sentences got appended to them over time. Rewrote it to word-wrap instead: it now walks
  word-by-word and flushes to a new page as soon as the budget would be exceeded, so no single
  page can ever overflow regardless of how long a paragraph gets. Verified against the real
  GUIDE.md text (17 pages now, all ≤ 259 chars, versus the old algorithm producing pages up to
  544 chars).
- Fixed the Drakonix Block Shop losing its Unsellable curse when broken and picked back up.
  The curse was only ever applied to the ItemStack handed out at first login
  (`StarterKit.cursedUnsellable`) - breaking a placed block goes through its loot table instead,
  which just dropped a fresh, uncursed `ItemStack(this)` with no memory of the curse. Added a
  `minecraft:set_enchantments` function to the block's loot table entry
  (`data/drakonixoneblockshop/loot_table/blocks/drakonix_block_shop.json`) so every drop -
  however it was broken - always carries `drakonixoneblockshop:unsellable` at level 1, matching
  the curse it's given with at login.
- Added a "Drakonix Guide" entry to the shop GUI's Packs tab so a player who lost their guide
  book can pull a fresh (re-cursed, current-version-titled) one, same 1-hour cooldown as the
  tech-mod packs rather than a special free-for-all. `StarterPacks.tryClaim` special-cases this
  pack id before the generic `PackItem` loop since a written book with dynamic page/version
  content doesn't fit that model's plain itemId+count shape; `StarterKit.guideBook()` and
  `cursedUnsellable()` were widened from `private` to package-private so `StarterPacks` could
  reuse the exact same book-building logic instead of duplicating it.

- Audited Drakonix Shop Block sell pricing for real gaps instead of guessing which items needed
  seeding. Method: temporarily dumped `BuiltInRegistries.ITEM.keySet()` during a real boot (all
  4146 items across vanilla + every `localRuntime` mod), parsed every vanilla recipe JSON's
  `result` field plus the same for EnderIO/Mekanism/AE2/Create/GeOre (recursively unzipping
  EnderIO's jarjar-nested sub-jars, since its own top-level jar has no `data/` recipes at all),
  and diffed "items with neither a seed price nor a recipe output" against `seed_prices.json` -
  425 vanilla gaps, ~500 modded gaps once GeOre's own items were excluded. Fixed the ones that
  actually matter economically (left cosmetic/creative-only items like flowers, corals, spawn
  eggs, and command blocks at the harmless `DEFAULT_PRICE` fallback):
  - Vanilla ore blocks (`minecraft:iron_ores`, `gold_ores`, etc. - vanilla ships these as real
    tags, one per metal, each already bundling the deepslate/nether variant) were priced at 1
    despite requiring mining - now match their raw-material seed price via `seed_prices_by_tag.json`.
  - Stripped logs/wood/stems and both Nether stems were all defaulting to 1 despite being the
    same material as their already-priced non-stripped form (stripping is a player interaction,
    not a recipe, so the recursive pricer could never reach them) - one `minecraft:logs` tag
    entry (that vanilla tag already bundles every species' stripped/unstripped/log/wood forms)
    fixes all of them at once.
  - Pottery sherds (`minecraft:decorated_pot_sherds` tag) and the common creeper-drop music discs
    (`minecraft:creeper_drop_music_discs` tag) were undervalued at 1 despite being real loot,
    now priced as a group; the rarer structure-only discs and the Pigstep-disc fragment got
    individual seed prices instead since they're not in that tag.
  - Copper oxidation states (exposed/weathered/oxidized block + door + trapdoor) aren't reachable
    by recipe (oxidizing is a time-based world effect, not a recipe) - seeded to match their
    unoxidized counterparts.
  - Notable no-recipe rare loot (totem of undying, elytra, trident, heart of the sea, enchanted
    golden apple, saddle, name tag, goat horn, armadillo scute, heavy core, breeze rod, trial
    keys, disc fragment, sniffer egg, experience bottle, dragon breath, dragon egg, banner
    patterns) was defaulting to the same price as dirt - now priced by rarity.
  - GeOre: every one of its 29 ore materials' `_shard` and `_cluster` items got a real price
    (previously ALL defaulted to 1 - GeOre uses its own per-material item IDs, not any tag our
    existing table covered) - reused this project's existing per-metal tiers where the material
    overlaps a vanilla/common one, and added new tiers for GeOre-only fictional materials
    (allthemodium/vibranium/unobtainium roughly following All The Mods' own progression order,
    ruby/sapphire/topaz as mid gems, tungsten/monazite/uraninite/black_quartz as new real-mineral
    tiers).
  - Mekanism's entire ore-processing chain (ore/raw/dust/shard/crystal/dirty_dust/clump, for
    copper/gold/iron/lead/osmium/tin/uranium) plus its standalone materials (fluorite, salt,
    sulfur, lithium, bronze, steel, refined obsidian, netherite dust, sawdust) were all
    unreachable by recipe (its processing recipes are custom machine recipe types, not standard
    `Ingredient`-based crafting/smelting) - added via new `c:shards`/`c:crystals`/
    `c:dirty_dusts`/`c:clumps` tag categories (verified these `c:` tags actually exist in
    Mekanism's own jar before relying on them) plus new `c:dusts`/`c:ores` entries for the
    materials that didn't already have one.
  - Create's crusher-output "crushed raw <metal>" items (an alternate raw-ore processing stage,
    untagged even though the ore/raw-material forms already are) and AE2's certus quartz family
    (crystal/charged crystal/dust, via `c:gems` and `c:dusts`) got the same treatment.
- Follow-up fixes after a self-review of the three items just above (chest banking, obsidian
  recipe, difficulty config):
  - Removed the `modpack/config/dissolver-enhanced/dissolver_enhanced.properties` override -
    verified via `javap` disassembly of the mod's own `ModConfig.class` that it only writes
    defaults when the file doesn't already exist (`Files.exists` guard before generating), so
    the override could only ever help brand-new instances - anyone with an already-generated
    config from before this change would keep whatever `difficulty` they already had regardless
    of what we shipped. Not worth the false confidence; removed rather than kept as dead weight.
  - `dissolver_enhanced:crystal_frame_item` (the ingredient the new obsidian recipe still needs)
    now has its diamond swapped for an iron ingot via a same-path datapack override
    (`data/dissolver_enhanced/recipe/crystal_frame_item.json` bundled in our own mod - same
    override-by-exact-path mechanism already used for GeOre's loot tables) - the diamond was the
    real cost gatekeeper, not the amethyst/redstone. Keeps the recipe's shape and flavor, cuts
    the actual barrier.
  - `ShopBlockEntity.findAdjacentChest` now resolves neighbors via `ChestBlock.getContainer(...,
    override=true)` - the same vanilla helper real hoppers use (`HopperBlockEntity.getContainerAt`)
    - instead of a raw `getBlockEntity() instanceof ChestBlockEntity` check. Fixes two bugs: a
    double chest was only ever getting one 27-slot half banked into (the other half sat unused
    even when the first was full), and a "blocked" chest (mob/cat sitting on top) would've been
    treated as no-chest-found instead of still being usable, exactly like a real hopper can still
    use a blocked chest.
  - Documented in `GUIDE.md` that tokens stored inside a backpack/bag don't count toward the
    balance the shop can see or spend (`Wallet.get`/`removeAllTokens` only scan the player's
    main inventory slots) - not a bug, just needed to be said somewhere a player would read it.
- Added a new `drakonixoneblockshop:dissolver_block_obsidian` crafting recipe (data-driven, in
  our own mod's data folder even though the output is `dissolver_enhanced:dissolver_block`) as an
  extra way to craft the Dissolver Enhanced block: 8x `dissolver_enhanced:crystal_frame_item`
  around a `minecraft:obsidian` center, instead of the mod's own hard-coded `DynamicDissolverRecipe`
  (a Java `CustomRecipe`, not data-driven - confirmed via `javap` disassembly of the mod's own
  class, since no sources jar exists for it) which centers on a nether star at "hard" difficulty
  (default) or redstone/phantom membrane at "easy"/"normal". This is additive, not a replacement -
  the mod's own difficulty-tiered recipe still exists alongside it; players can use whichever
  ingredient they have on hand. Coexists safely if Dissolver Enhanced isn't installed - a missing-
  item recipe just fails to parse and gets skipped by vanilla's per-file recipe loading, no crash.
- Drakonix Shop Block sales triggered by a hopper now prefer physically banking their sale
  proceeds (real minted token items, via `HopperBlockEntity.addItem` - vanilla's own generic
  container-insertion helper, verified against decompiled source) into an adjacent vanilla chest
  if one exists next to the shop block, instead of minting straight into the owner's inventory.
  Falls back to the normal `Wallet.add`/`creditOffline` path for any shortfall (no chest, or chest
  full) so a sale is never silently lost. GUI-triggered sales are unaffected - only hopper-driven
  ones (`pendingHopperInsertion`) look for a chest. `Wallet.mint`'s denomination-decomposition
  logic was factored out into a public `Wallet.mintTokens(HolderLookup.Provider, long)` so both
  the player-inventory path and this new chest path share the same greedy minting.
- Dissolver Enhanced's crafting-difficulty config (`config/dissolver-enhanced/
  dissolver_enhanced.properties`, key `difficulty`) now ships overridden to `easy` in the modpack
  (`modpack/config/dissolver-enhanced/dissolver_enhanced.properties`, delivered via packwiz's
  `overrides/` mechanism, same as the existing VeinMiner `mustSneak` override) - the mod's default
  `hard` recipe was judged too punishing for this pack's early game. Verified end-to-end with a
  real `packwiz refresh` + `packwiz modrinth export` test that the file lands correctly inside the
  exported `.mrpack`.
- The Drakonix Guide book's title now includes the mod's own running version
  (`"Drakonix Guide v" + OneBlockShopMod.modVersion`) instead of a bare "Drakonix Guide" -
  `modVersion` is read once at mod construction from the real `ModContainer`'s own metadata
  (`modContainer.getModInfo().getVersion().toString()`, which resolves `neoforge.mods.toml`'s
  `version="${mod_version}"` templating), not a second hand-duplicated constant that could drift.
- Added Create - real deep automation via rotational "mechanical" power, a genuinely different
  playstyle than AE2/Mekanism/EnderIO's item-pipe style, and a good source of new sellable
  materials. Latest (6.0.10+mc1.21.1) needs `neoforge>=21.1.219`, above this project's pinned
  `neo_version` (21.1.176) - pinned to 6.0.0 instead, the oldest 1.21.1 Modrinth build, whose
  `neoforge.mods.toml` only needs `neoforge>=21.1.125`, satisfied. Its two hard-required
  dependencies (Flywheel, Ponder) plus Registrate are all jarjar'd inside Create's own jar
  (confirmed via its `META-INF/jarjar` contents) - no separate `localRuntime` lines needed for
  them, same mechanism already relied on for EnderIO's own nested jars. No version-number
  collision. Synced via `just modpack-sync` (28 mods total now).
- `GUIDE.md` (the in-game guide book, read live from this file at first login - no separate
  generator script) hadn't been touched since most of this session's features shipped. Added
  paragraphs covering: Cave Only expedition mode, the death-during-expedition resume potion,
  `mobGriefing` being off, the shop GUI's Packs tab (AE2/Mekanism/EnderIO starter kits), GeOre's
  ore clusters, Explorer's Compass/When Dungeons Arise/Artifacts, and Dissolver Enhanced's EMC
  economy - matching the book's existing conversational voice rather than a changelog tone.
  Comes out to ~13 pages, comfortably under vanilla's written-book page limit.
- ~~Exploration-encouraging mods TODO item's remaining half: baubles/accessory-slot mods and
  artifact mods with powerful gear that spawns in chests, not picked yet~~ — added Curios API
  (modern Baubles successor) and Artifacts, whose own Modrinth description is literally "Adds
  various treasure items that can be found through exploration." Artifacts only optionally
  integrates with Curios (and Cloth Config, already present from Building Wands) rather than
  hard-requiring either. "artifacts"'s version number collides across fabric/neoforge (both
  loaders share it) - pinned to the neoforge release's Modrinth version id instead; Curios has
  no collision. Both need neoforge versions well below our pin (21.1.60/21.0.133-beta vs
  21.1.176), satisfied. Synced via `just modpack-sync` (27 mods total now). This closes out the
  whole "exploration-encouraging mods" TODO item.
- `StarterKit.giveItems` now also gives an Explorer's Compass (`explorerscompass:
  explorerscompass`, confirmed via its recipe result) alongside the pickaxe/shop block/guide
  book/oak logs, same Unsellable curse as the rest - skips the gift gracefully (checked via
  `BuiltInRegistries.ITEM.containsKey`) if the mod isn't installed, since it's a dev-only/
  optional dependency, not a real one.
- Added When Dungeons Arise (whole new structures with real loot) and Explorer's Compass
  (points at the nearest structure of a chosen type) - gives the now-1,000,000-block Explore
  teleport range more worth actually finding. "when-dungeons-arise"'s version number "2.1.68"
  collides across fabric/neoforge (both loaders share it) - pinned to the neoforge release's
  Modrinth version id instead; Explorer's Compass has no collision. Both need
  neoforge>=21.0.0-beta, satisfied; no Modrinth-declared dependencies for either. Synced via
  `just modpack-sync` (25 mods total now).
- ~~Expedition's teleport range was a hardcoded 10,000-block constant, no config system existed
  in this mod at all~~ — bumped the default to 1,000,000 and built a standard NeoForge TOML
  config from scratch (new `Config.java`, registered as `ModConfig.Type.COMMON` in
  `OneBlockShopMod`'s constructor - it already received an unused `ModContainer` parameter),
  lands at `config/drakonixoneblockshop-common.toml` like any other mod's config, editable
  without recompiling. `exploreRange`'s max is capped at `Integer.MAX_VALUE / 4`, not
  `MAX_VALUE` itself - `Expedition.rollDestination` spreads it into `range * 2 + 1` to roll a
  coordinate, which would silently overflow int and wrap negative above that cap.
  `SAFE_BORDER_SIZE` (derived from the range) went from a static-final field to a method,
  computed fresh each teleport, so a config reload takes effect without a restart. Boot-tested
  and confirmed the generated TOML directly: `exploreRange = 1000000`, correct comment, correct
  `1 ~ 536870911` range.
- Added Lithium (general game-logic performance optimization - not a rendering mod like Sodium,
  which is Fabric-only; the NeoForge port keeps the same name). No collision, no Modrinth-
  declared dependencies, and its own `neoforge.mods.toml` doesn't even constrain neoforge's
  version (`loaderVersion="*"`) - only minecraft in `[1.21, 1.21.1]`, satisfied. Synced via
  `just modpack-sync` (23 mods total now).
- Added Building Wands (extends a placed block along a line/plane, consuming matching blocks
  from inventory - less tedious building of long runs) plus its two hard-required Modrinth
  dependencies, Architectury API and Cloth Config API, neither obvious from its own page - only
  surfaced via its declared Modrinth dependency list. No version-number collisions across any of
  the three; all satisfied by our neo_version (21.1.176). Synced via `just modpack-sync` (22
  mods total now).
- Added Sophisticated Backpacks (upgradeable backpacks with their own inventory tabs/filters/
  auto-sort - fits this mod's fetch-and-carry loop) plus its hard-required Sophisticated Core
  shared library, which isn't obvious from Sophisticated Backpacks' own Modrinth page - only
  surfaced via its declared Modrinth dependency list. Both need neoforge>=21.1.0, satisfied; no
  version-number collisions. Synced via `just modpack-sync` (19 mods total now). The boot-test
  for this one only got as far as confirming all 19 mods load/render cleanly (textures, shaders,
  mixins, no errors) - it never confirmed reaching an actual world join within the wait window,
  so unlike prior additions this wasn't verified end-to-end at the "joined the game" checkpoint.
- ~~ProjectE was requested as a dev-runtime mod~~ — the real ProjectE (and every fork/
  continuation checked) has no Modrinth listing at all, not just missing this version - confirmed
  by slug lookup, search, and a search by its original author (sinkillerj); likely
  CurseForge-exclusive, same class of gap as FastMove earlier. Added Dissolver Enhanced instead,
  a real actively-maintained NeoForge 1.21.1 mod explicitly described (in its own Modrinth
  listing) as "inspired by ProjectE and Equivalent Exchange." Pinned by Modrinth project id
  (`yEX3Y3na`) rather than its slug, which has literal parentheses that risk misparsing as a
  Gradle dependency coordinate - confirmed both actually resolve against Modrinth's maven repo
  before picking the id. Synced via `just modpack-sync`, boot-tested clean.
- Follow-up to the packs below: AE2's gained the full-block `ae2:energy_acceptor` alongside the
  small cable-part `ae2:cable_energy_acceptor` it already had (two distinct real items,
  confirmed via separate recipe result ids); EnderIO's Energetic Photovoltaic Module count went
  from 4 to 12.
- ~~The EnderIO starter pack skipped conduits entirely - granting a plain `enderio:conduit` item
  would've been a blank/untyped "<MISSING> Conduit" since EnderIO's conduits are all one shared
  item typed via a mod-specific data component set at craft time, and this mod has no
  compile-time handle on EnderIO's classes to construct that component~~ — decompiled EnderIO's
  actual registration classes to solve it for real instead of working around it: the component
  (`enderio:conduit`) holds a `Holder` into EnderIO's own `Registry<Conduit<?>>` (registry key,
  same id, confirmed via `EnderIOConduitsRegistries$Keys.CONDUIT`), so `StarterPacks.
  enderioConduit` looks up the `DataComponentType` via `BuiltInRegistries.DATA_COMPONENT_TYPE`
  and the registry via `RegistryAccess.registryOrThrow` at runtime, then sets the component with
  an unchecked generic cast (`ItemStack.set` needs a real type parameter this mod doesn't have
  compile-time access to) - a legitimate, if unusual, interop pattern since Java generics are
  fully erased at runtime. All four conduit-type entry ids (`enderio:energy`/`item`/`fluid`/
  `redstone`) were confirmed the same way every other pack item was - extracted from the real
  pinned jar's recipe JSON, not guessed. Boot-tested clean with the real EnderIO version loaded,
  but the actual claim-and-render path (clicking "Claim: EnderIO" and inspecting the resulting
  conduit item) couldn't be interactively verified - no computer-use tool for the game window -
  worth a real playtest to confirm it displays/behaves as a proper typed conduit.
- Border.initIfNeeded now also sets `mobGriefing` to `false` on first join, alongside the
  existing `keepInventory` - same fairness reasoning: a creeper/enderman griefing the 1-block
  starting border (or whatever's been built up since) is a much bigger deal than on a normal
  base, and not something the player can realistically wall off yet.
- ~~Playtest report: shift-clicking a sellable item into the Sell slot could rarely delete tokens
  from inventory or leave a smaller balance than expected~~ — root cause: `ShopMenu.
  quickMoveStack`'s post-transfer cleanup (`slot.set(ItemStack.EMPTY)` on the shift-click's
  source slot) ran *after* `moveItemStackTo` had already synchronously triggered the sale
  (`ShopBlockEntity.setItem` -> `trySell` -> `Wallet.add`), and `Wallet.add` clears+remints every
  token stack in the player's inventory by index (`removeAllTokens`/`mint`) as part of its
  design (see "Token currency" in README.md). If `mint`'s `Inventory.add` happened to reuse the
  exact slot the shift-click was clearing out from, the unconditional `slot.set(EMPTY)`
  afterward destroyed the just-minted tokens the instant after they were paid out - only
  reproducible when the timing/slot layout lined up, matching the "rarely" in the report. Fixed
  by only clearing that slot if it still holds the same `ItemStack` instance the shift-click
  started with (reference equality) - if `Wallet.add`'s remint already replaced it with a new
  stack, leave it alone instead of clobbering.
- ~~GeOre's ore geode clusters only dropped their real item with Silk Touch equipped, a lesser
  "shard" otherwise (same pattern as vanilla's Amethyst Cluster) - didn't fit this mod's own
  "sell raw resources" economy well~~ — `tools/generate_geore_overrides.py` (`just
  geore-overrides-sync`) generates a loot table override per ore GeOre supports (29 of them,
  extracted from its own jar, not guessed) that always drops the full cluster item regardless of
  tool. Lives in *this* mod's own `data/geore/...` folder rather than a real GeOre dependency or
  a per-world datapack - a mod's own `data/` folder is the only reliably always-on datapack
  contribution with no per-world "enable this" step, and it's inert/harmless if GeOre isn't
  installed.
- ~~No QoL mods in the dev-runtime/modpack beyond the tech-mod-compat and playtesting-speed
  set~~ — added five, each checked against real Modrinth NeoForge 1.21.1 releases first (not
  guessed): Inventory Tweaks: ReFoxed, AppleSkin, Clumps (version-number collided across
  forge/fabric/neoforge, pinned by Modrinth version id like twerk-crop-growth/JEI/VeinMiner
  before it), and GeOre. The fifth was going to be FastMove - Parkour Movement, but that mod has
  zero Forge/NeoForge releases in its entire history (Fabric/Quilt only, confirmed exhaustively
  across every version it's ever published) - added ParCool! instead as the closest real
  NeoForge substitute (wall-run/slide/mantle/roll). ParCool's own `neoforge.mods.toml` ships an
  unresolved `${neo_forge_version}` template placeholder instead of a literal version range
  (likely a build-config oversight on the author's end) rather than the usual verifiable range,
  so unlike the other four this one was trusted on a real boot-test alone, not the manifest -
  confirmed loading fine.
- Follow-up to the Expedition Resume Potion below: drinking it while an expedition's still
  active now also grants 30 seconds of Resistance V (`RESUME_INVINCIBILITY_TICKS`) - landing
  right back at a death spot is otherwise a near-guaranteed repeat death (still in whatever
  killed them). Amplifier 4 zeroes out damage entirely per vanilla's own
  `getDamageAfterMagicAbsorb` reduction formula (`(amplifier+1)*5%`, confirmed in decompiled
  source), for anything not tagged `BYPASSES_RESISTANCE` (void, starvation, etc. still apply) -
  the well-known "practical invincibility" trick, not a true invulnerability flag.
- ~~The defensive double-teleport below (immediate + delayed recheck) didn't fix the reported
  underground-landing bug - a second real playtest still hit it~~ — replaced the whole
  forced-teleport-on-respawn approach: `onPlayerRespawn` no longer touches the player's position
  at all, just lets vanilla's normal respawn stand. A new `onPlayerDeath` (`LivingDeathEvent`,
  fires on the *old* entity while its position is still valid) records where they died into new
  `DEATH_X/Y/Z` attachments (separate from `RETURN_X/Y/Z`, which stays the expedition's *origin*
  at home), and respawning now hands the player an "Expedition Resume Potion" instead - drinking
  it (`RESUME_EFFECT`, instantaneous) teleports back to that exact death spot. Global state is
  untouched by death itself (`isAway()`/`END_TICK`/`Border.EXPEDITIONS_ACTIVE` all keep running
  in the background exactly as if the player just weren't there, same as being logged out except
  `onPlayerLogout` doesn't apply since they respawned instead of disconnecting) - `RESUME_EFFECT`
  re-checks `isAway()` at drink time and just prints a chat message and does nothing if the
  expedition already ended (timed out, `/expedition end`, or the return potion) by then, rather
  than teleporting into a stale state.
- ~~Playtest report: dying in a cave expedition landed the player underground below their base
  instead of exactly on `RETURN_X/Y/Z`~~ — never root-caused (leading suspect is some vanilla
  post-respawn positioning step still running after `PlayerRespawnEvent` returns, since the
  block at the exact return spot could differ from expedition-start time if the player's built
  over it since); `onPlayerRespawn` now also schedules a second, delayed re-teleport to the same
  `RETURN_X/Y/Z` a few seconds later (`RESPAWN_RECHECK_TICK`, consumed in `onPlayerTick`, cheap
  and idempotent if the first teleport actually stuck) as a defensive mitigation - see the
  "Known shortcuts" entry in TODO.md, unconfirmed until a real death-in-a-cave playtest.
- ~~The Explore-tab return potion and its two custom effects (Expedition Return, Portal
  Immunity) had no lang entries - the potion in particular displayed as vanilla's own
  "Uninteresting Potion" (`item.minecraft.potion.effect.empty`, since `PotionContents.potion()`
  is empty for a customEffects-only potion)~~ — added `item.drakonixoneblockshop.
  expedition_return_potion` plus name/description keys for both effects, and set the potion
  stack's `DataComponents.ITEM_NAME` (not `CUSTOM_NAME`, which renders italic like a real anvil
  rename) to the new key. Portal Immunity also got a `GatherEffectScreenTooltipsEvent` hover
  line, extending the same pattern `ExpeditionClientEvents` already used for the Expedition
  effect; skipped for Expedition Return since it's instantaneous and never lingers in that HUD.
- ~~Dying mid-expedition respawned the player at their bed/world-spawn instead of the expedition's
  origin point, and silently corrupted shared state: `END_TICK`/`RETURN_X/Y/Z` are NeoForge
  attachments, which reset to their defaults on the *new* player entity a respawn creates unless
  explicitly marked `.copyOnDeath()` (confirmed in `AttachmentType`'s own docs) - so `isAway()`
  would already read false again post-respawn, permanently leaking `Border.EXPEDITIONS_ACTIVE`
  the same way `onPlayerLogout`'s watchdog exists to prevent for disconnects~~ — added
  `.copyOnDeath()` to all five per-player expedition attachments and a `PlayerRespawnEvent`
  handler that, now that `isAway()` still reads correctly, overrides vanilla's respawn position
  with `returnHome` (teleport to the expedition's origin, clear away-state, close out the
  border hold) exactly like a normal early return.
- ~~Expedition teleports could drop a player in open ocean, or (rarer) in a flooded underwater
  cave passage, with nothing to stand on~~ — `Expedition.rollDestination` rerolls the whole
  column (fresh x/z, up to `MAX_LOCATION_ATTEMPTS`) whenever the sampled surface's biome is
  tagged `BiomeTags.IS_OCEAN`, falling back to the last-rolled column rather than hanging if
  every attempt somehow lands in ocean. Separately, `findCaveLanding`'s open-space check was
  `!blocksMotion()` alone, which is true for water (confirmed against vanilla's own
  `MOTION_BLOCKING` heightmap predicate, which explicitly ORs in a fluid check for exactly this
  reason) - added an explicit `getFluidState(...).isEmpty()` check so a flooded cave passage no
  longer counts as valid footing.
  - Added a second Explore-tab button while touching this code: "Open Portal (Cave Only)"
    (`ShopMenu.TELEPORT_CAVE_BUTTON`) rerolls columns until one actually has a cave under it,
    instead of just biasing the normal button's coin-flip toward one.
  - Also added a game-friendlier way to end an expedition early than the `/drakonixoneblockshop
    expedition end` command: arriving now also grants an unsellable Potion (real vanilla
    `PotionContents` with two custom effects, not a hand-rolled item) that instantly ends the
    trip when drunk (`RETURN_EFFECT`, extends vanilla's own `InstantenousMobEffect` base class -
    the same one Instant Health/Harm use) and grants `PORTAL_IMMUNITY_EFFECT` for 30 seconds
    (deliberately equal to `PORTAL_DURATION_TICKS`) so returning right next to a still-open
    portal can't immediately suck the player back through it - `onLevelTick`'s walk-in check now
    also requires the absence of that effect.
- ~~No easier on-ramp for players who don't want to hand-build a tech mod's early automation
  chain~~ — new `StarterPacks.java` + shop GUI "Packs" tab: a free, no-cost bundle of
  useful blocks/machines/power/conduits for AE2, Mekanism, and EnderIO, gated by an
  independent 1-hour-per-pack cooldown (`ShopMenu`/`ShopScreen` mirror `Border`'s existing
  per-player cooldown attachment pattern, just keyed by pack id instead of a single value).
  Thermal Expansion and IC2 were skipped - neither has a NeoForge 1.21.1 release to draw items
  from (see RECOMMENDED-MODS.md), and GregTech was already removed. Every item id was checked
  against that mod's real recipe/lang data (extracted straight from its jar) before being
  hardcoded, not guessed - caught two real gaps in the process: modern Mekanism (10.x) has no
  power-generator block anymore (Heat/Solar/Bio/Wind Generators were cut years ago, Basic
  Energy Cube is the closest real substitute), and EnderIO's conduits are all one shared
  `enderio:conduit` item typed via a mod-specific data component set at craft time - granting a
  correctly-typed one needs that component, which isn't accessible without a compile-time
  dependency on EnderIO (the same open problem already tracked for EnderIO's item pipes in
  TODO.md), so both packs simply skip the slot they can't fill safely rather than hand out a
  broken/blank item.
- ~~Expedition teleports could drop a player in open ocean with nothing to stand on~~ —
  `Expedition.rollDestination` now rerolls the whole column (fresh x/z, up to
  `MAX_LOCATION_ATTEMPTS`) whenever the sampled surface's biome is tagged `BiomeTags.IS_OCEAN`,
  falling back to the last-rolled column if every attempt somehow lands in ocean rather than
  hanging. Also added a "cave only" Explore-tab option while touching this code: a second
  button (`ShopMenu.TELEPORT_CAVE_BUTTON`) that forces `rollDestination` to keep rerolling
  columns until one actually has a cave under it, instead of just biasing the coin-flip toward
  one like the normal button does.
- Follow-up to the above: AE2's pack gained a Crafting Terminal and four 1k storage cells;
  Mekanism's gained a Basic Induction Cell/Provider pair (its actual modular big-battery
  system - still no true generator block, see above); EnderIO's gained four Energetic
  Photovoltaic Modules, the mod's real solar generator (its tooltip literally says "Solar
  Power!" - found by grepping its lang data for "solar" after the initial pass had missed it
  under that block's actual `photovoltaic` name).
- ~~Token coin sprite was a flat hue-shifted circle with a plain highlight blob, didn't read as
  a coin or tie into the mod's theme~~ — `tools/generate_tokens.py`'s `coin_sprite` now shades
  the fill/rim toward a desaturated metallic tone and stamps a dragon wing (parametric fan of
  bones from a shoulder point, scalloped bat-wing membrane between them, computed with `math`/
  trig rather than a hand-placed pixel table) across every denomination's hue. Iterated by
  rendering upscaled previews and viewing them (no Pillow, still the stdlib `png_writer`) rather
  than guessing pixel placement blind.
- ~~`tools/generate_tokens.py` and `tools/generate_expedition_icon.py` had no `justfile` recipe,
  unlike `generate_modpack.py`'s `modpack-sync`~~ — added `tokens-sync` and
  `expedition-icon-sync`.
- ~~Border mob waves spawned on a ring outside the border, where vanilla's `WorldBorder`
  collision (`Entity.collectColliders`, entity-agnostic - confirmed in decompiled source) blocks
  any entity from crossing in, so they could never reach the player~~ — `spawnMobWave` now picks
  random points *inside* the border instead, at least `WAVE_MIN_DISTANCE_FROM_PLAYER` (3 blocks)
  from the player, retrying up to `WAVE_SPAWN_ATTEMPTS` (10) times per mob and skipping that mob
  if no spot qualifies. Also skips the wave entirely below `WAVE_MIN_BORDER_SIZE` (6) - border
  sizes only land on odd numbers, so 7x7 is the first size waves actually fire at, since a smaller
  border doesn't leave room to land a mob away from the player.
- ~~VeinMiner's `mustSneak` defaulted to `false`, so a normal punch on any connected ore vein
  (e.g. a GregTech ore patch) silently vein-mined the whole thing~~ — `modpack/config/Veinminer/
  settings.json` now ships as a modpack override with `mustSneak: true` pre-set (packwiz bundles
  any non-`.pw.toml` file under the pack root into the exported `.mrpack`'s `overrides/`, which
  Prism unpacks into the instance's `.minecraft/`) - schema copied from the mod's own generated
  default (booted a dev client once to capture it) rather than guessed, only that one field
  flipped.
- ~~Releases had the mod jar, but nothing a player could actually install in one step~~ —
  `release.yml` now also builds a `drakonix-one-block-shop-<version>.mrpack` and attaches it to
  every GitHub Release, importable directly into Prism Launcher (or any Modrinth-pack-compatible
  launcher). Regenerates `modpack/` the same way CI's `build.yml` validates it, then copies the
  just-built mod jar into `modpack/mods/` *after* that regeneration (`generate_modpack.py` wipes
  that folder first) and runs `packwiz modrinth export` - confirmed via a real export that any
  file under `modpack/` packwiz doesn't recognize as its own `.pw.toml` metadata gets carried
  straight into the exported `.mrpack`'s `overrides/` folder, which Prism unpacks verbatim into
  the instance's `.minecraft/` - so this needed no Modrinth listing or hosted download URL for
  our own mod, just dropping the jar in place before exporting.
- ~~No real modpack, just a TODO note asking how to approach one~~ — `./modpack/` is a real
  `packwiz` pack (`packwiz init`, NeoForge 1.21.1/21.1.176 to match `gradle.properties`). New
  `tools/generate_modpack.py` regenerates `modpack/mods/*.pw.toml` from `build.gradle`'s
  `localRuntime "maven.modrinth:..."` lines - those are already the single, vetted source of
  truth (see `RECOMMENDED-MODS.md`), so this avoids hand-maintaining a second mod list.
  - `packwiz modrinth add` takes Modrinth project+version *IDs*, not Maven-style
    slug:version-or-id coordinates, so the script resolves each pin against the live API first
    (tries treating the pinned string as a version id directly, falls back to scanning the
    project's version list for a matching `version_number` on a `neoforge` build - same
    disambiguation this project already does by hand when vetting a new mod).
  - Wipes and regenerates `modpack/mods/` every run rather than diffing in place - simpler, and
    matches how `tools/generate_tokens.py` treats its own output as fully generated, never
    hand-edited. Runs `packwiz refresh` immediately after the wipe (before re-adding anything),
    since otherwise `index.toml` still references the just-deleted files and packwiz's own
    dependency-tracking (AE2 pulling in GuideMe) warns trying to read them mid-loop.
  - `justfile` gained `modpack-sync` (runs the script) and `modpack-serve` (`packwiz serve`, for
    testing with a packwiz-compatible launcher like Prism) recipes.
  - `.github/workflows/build.yml` gained a `modpack` job: installs `packwiz` via `go install`
    (no prebuilt-binary GitHub Action for it), regenerates, then `git diff --exit-code` against
    the committed `modpack/` - fails CI if a mod's pin drifts from what's actually committed, or
    if a pinned version ever gets removed/yanked from Modrinth (the regenerate step itself would
    fail first, not just the diff).
  - Deliberately out of scope for this pass (see "Modpack" in TODO.md): which mods actually
    belong in a *shipped* pack vs. this dev-convenience list, light questing, and including this
    mod's own jar in the pack.
- ~~The "Expedition" potion-icon countdown could drift out of sync with the real `END_TICK` -
  e.g. `devcheat expedition fastforward` moving the return time earlier, an admin `/effect`
  command, or milk bucket/death wiping the applied effect entirely while `isAway()` still says
  yes~~ — `onPlayerTick` now force-resyncs every 5 seconds (`RESYNC_INTERVAL_TICKS`):
  `removeEffect` then `addEffect` with the exact remaining duration recomputed from `END_TICK`.
  Plain `addEffect` alone wouldn't have fixed the "moved earlier" cases -
  `MobEffectInstance.update()` (confirmed in decompiled source) only ever extends a duration
  when merging with an existing instance of the same effect, never shrinks it - so
  remove-then-reapply is what actually guarantees an exact match in both directions.
- ~~No way to test Expedition/hopper-report/border-wave/portal timing without waiting out their
  real timers~~ — added `/drakonixoneblockshop devcheat` (op-only, alongside
  `balance`/`border`/`starterkit`): `expedition teleport` (skips the portal, straight to
  `Expedition.enterPortal`'s "away" state), `expedition fastforward <seconds>` (jumps
  `END_TICK` directly - doesn't touch `NEXT_WARNING`, so `onPlayerTick`'s own catch-up loop
  fires every warning still ahead of the new value in one burst, useful for watching them all at
  once), `hopperreport` and `borderwave` (both just call the same private logic the real
  5-minute tick / real purchase already use, factored out as `HopperSalesTracker.devForceReport`
  / `Border.devSpawnMobWave` so there's exactly one implementation), and `closeportal` (recovers
  from the "one portal at a time" limitation during testing without waiting the full 30s).
- ~~"Expedition" status effect had no description, and Explore-tab destinations only ever
  landed on the overworld surface~~ — two independent fixes:
  - Added a hover-tooltip description via NeoForge's `GatherEffectScreenTooltipsEvent`
    (confirmed in decompiled `EffectRenderingInventoryScreen` - this is the actual, only hook
    for appending lines to a potion effect's tooltip; vanilla itself has no built-in
    "description" concept for effects, the inventory panel only ever shows name + duration).
    New `ExpeditionClientEvents` class, deliberately kept separate from `Expedition.java` so
    that class never has to import a client-only event type (referencing one from a class that
    also loads on a dedicated server risks `NoClassDefFoundError`). Lang key
    (`effect.drakonixoneblockshop.expedition.description`) added by
    `tools/generate_expedition_icon.py` alongside the icon/name, same as before.
  - `Expedition.rollDestination` now has a 35% chance (`CAVE_CHANCE`) of landing underground
    instead of on the surface - `findCaveLanding` scans a random Y in the column for an air
    pocket with solid footing below (`BlockState.blocksMotion()`, deprecated but confirmed
    still the exact predicate vanilla's own `Heightmap.MOTION_BLOCKING` uses internally, so no
    real replacement to migrate to), falling back to the surface roll if the sampled range
    turns out solid all the way through (e.g. no cave under that exact column).
- ~~"No hopper automation" and the periodic hopper-sales report weren't obviously from this
  mod~~ — both now prefixed `[Drakonix Shop] ` (`HopperSalesTracker.PREFIX`) so the recurring
  chat message isn't mistaken for a different mod or a server plugin.
- ~~Team expeditions had no spec - what would make an Explore-tab trip a shared "team" one
  rather than each player's own independent timer/destination?~~ — resolved by changing what
  the Teleport button *does*, not by adding a separate team concept. Clicking it (renamed "Open
  Portal") rolls one destination and opens a 30-second particle-only portal
  (`Expedition.openPortal`/`onLevelTick`, `ParticleTypes.PORTAL`, no real block placed) above
  that shop block instead of teleporting the clicker instantly; whoever walks into it before it
  closes goes there together (`Expedition.enterPortal`). A trip is "team" purely because more
  than one player happened to walk in - no roster, invite, or confirm command needed. Each
  traveler still gets their own 10-minute stay and personal auto-return from wherever they
  individually left, unchanged. Solo trips work identically (open the portal, walk in alone) -
  the earlier idea of a separate "Team Teleport" button was dropped as unnecessary once the
  portal itself is what's shared.

- ~~Shop block required an iron (or better) pickaxe to drop - a brand-new player who hasn't
  smelted iron yet couldn't move their own shop block if they ever needed to~~ — removed
  `requiresCorrectToolForDrops()` from the block's properties, so any tool (including bare
  hands) now breaks and drops it. `Player.hasCorrectToolForDrops` short-circuits true once a
  block's own `requiresCorrectToolForDrops` is false, regardless of tool tags (confirmed against
  decompiled `Player.java`), so the `needs_iron_tool` tag was dead config once this changed -
  removed that file entirely. Left `mineable/pickaxe` in place: it still gives pickaxes their
  normal mining-speed bonus (`Tool.getMiningSpeed`, evaluated independently of drop eligibility)
  without restricting which tools work.

- ~~"Expedition" status effect had no icon, just vanilla's generic "?" texture, and no
  translated name~~ — added `assets/drakonixoneblockshop/textures/mob_effect/expedition.png`
  (18x18, vanilla's mob-effect icon size, confirmed against a real one) and the
  `effect.drakonixoneblockshop.expedition` lang entry. Generated by a new
  `tools/generate_expedition_icon.py`, same pure-stdlib-PNG approach as the token sprites
  (`tools/generate_tokens.py`) - factored the shared PNG writer out into `tools/png_writer.py`
  so both scripts use one implementation. No atlas registration needed: vanilla's
  `assets/minecraft/atlases/mob_effects.json` uses a "directory" source that scans every
  namespace's `textures/mob_effect/` folder automatically.
- ~~No multiplayer accommodation for the shared world border - a second player joining a
  singleplayer-tuned 1x1 (or barely-expanded) border is an unfair start~~ —
  `Border.clampForMultiplayer`, called on every login, guarantees the one shared border can
  never be smaller than 17x17 once two or more players have ever been online together (never
  shrinks it if already bigger - a one-way ratchet). 17, not the requested 16: every real
  expansion adds 2 to an odd starting size (1, 3, 5, ...), so 17 is the nearest size on that
  same lattice that's still >= 16, keeping `purchaseCount()`'s `(size-1)/2` math exact instead
  of a fractional purchase count an even border size would produce. Deliberately still one
  shared border, not per-player ones (a real per-player border was attempted once and reverted -
  broke the client hanging at the title screen for a reason never root-caused; this clamp makes
  that whole approach unnecessary, so it's not being revisited) - "all players share the same
  upgrade status" was already true before this change, since the border's always been
  level-scoped, not player-scoped. Each player's Explore-tab trip already runs fully
  independently of everyone else's (see `Expedition.java`) - "team expeditions" specifically was
  a separate idea, since resolved (see the portal entry above).
  - Guarded against the one real edge case: `clampForMultiplayer` skips itself entirely while
    any Expedition hold has the border at its temporary safe-travel size
    (`Border.EXPEDITIONS_ACTIVE > 0`), so it can't clamp a placeholder value - and
    `endExpeditionHold` re-checks the clamp right after restoring the real size, in case a
    second player logged in mid-hold.
  - Added `/drakonixoneblockshop border simulatejoin` (op-only) so a solo tester can exercise
    this without a real second account - calls the same clamp (`Border.forceMultiplayerClamp`)
    the real `PlayerLoggedInEvent` player-count check would trigger.
- ~~"Money" was an invisible attachment counter, not the physical token items the TODO asked
  for~~ — `Wallet.get`/`add` now read/write the sum of Drakonix OneBlockShop Token items
  (`OneBlockShopMod.TOKEN_DENOMINATIONS`, powers of two 1..8192) in a player's inventory instead
  of a `Long` attachment. Every external call site (`Border`, `ShopMenu`, `AdminCommands`,
  `ShopBlockEntity`, `StarterKit`) kept the exact same four-method contract Wallet always had, so
  none of them needed to change - only `Wallet.java`'s internals did.
  - Change-making: `add()` doesn't track which specific token stacks a purchase "breaks" - it
    clears every token item from the inventory and re-mints the resulting total from scratch via
    greedy denomination decomposition. Greedy is always optimal (fewest tokens) for a canonical
    system like powers of two, so this satisfies the change-making problem without an actual
    solver, and guarantees minimal holdings after every transaction for free.
  - Offline sales (`Wallet.creditOffline`, hopper automation running while the owner's away)
    still can't mint into an inventory that isn't loaded, so that path is unchanged - stays a
    plain queued number, paid out as real tokens on next login.
  - Tokens carry the `Unsellable` curse (same mechanism as starter items,
    `StarterKit.cursedUnsellable`) so they can't be sold back through the shop for more tokens.
  - The greedy decomposition itself lives in a new `TokenDenominations` class, deliberately
    separate from `Wallet` - merely loading `Wallet` triggers its `DeferredRegister`/
    `AttachmentType` static fields, which throw outside a real ModLauncher launch (same
    limitation `PricingSeedTest` documents), so a plain-JUnit test couldn't touch a
    `Wallet.decompose` if it lived there. `WalletDenominationTest` covers it: reconstructs the
    original amount, checks every denomination below the largest is used at most once, and
    checks the result matches the closed-form optimal token count.
  - Assets (item models, hue-shifted 16x16 textures, lang entries) and all 26 combine/split
    recipes are generated by `tools/generate_tokens.py` (pure-stdlib PNG writer, no Pillow
    dependency available in this environment) rather than hand-authored - rerun it, don't
    hand-edit the generated files, if the denomination list or sprite design changes.
  - Breaking change to existing saves/worlds: a player's old numeric balance (the removed
    attachment) doesn't convert into tokens - by design, per project convention at this
    pre-release dev stage, not something worth a migration path for.
- ~~Ending an Explore-tab trip meant waiting out the full 10-minute countdown, no way to cut it
  short~~ — `/drakonixoneblockshop expedition end` (open to any player, not op-gated) calls the
  same `Expedition.tryEndEarly`/`returnHome` path the countdown itself uses.
- ~~No `/drakonixoneblockshop help`~~ — added, lists every subcommand with a one-line
  description, deliberately unfiltered by permission (same as vanilla `/help` listing commands
  you might not be able to run). Restructured `AdminCommands.java`'s command tree while at it:
  the op-only `requires(level 2)` check used to sit on the root `drakonixoneblockshop` literal,
  which would have gated the new player-facing `expedition`/`help` subcommands too - moved it
  down onto each of `balance`/`border`/`starterkit`'s own literals instead, since a Brigadier
  parent's `requires()` gates every child under it regardless of what the child itself declares.
- ~~No minimap/waypoints or block-tooltip HUD for dev playtesting~~ — added
  [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) and
  [Jade](https://modrinth.com/mod/jade) as `localRuntime` deps (see `RECOMMENDED-MODS.md`).
  Jade, not literally WAILA as asked - WAILA's own last release is 1.16.5-era Forge/Fabric, no
  NeoForge build exists; Jade is its actively-maintained modern successor and what current packs
  use in its place.
- ~~No VeinMiner dev dep~~ — added [VeinMiner](https://modrinth.com/mod/veinminer) (pinned to
  its NeoForge release's Modrinth version id, `syKekkIm` - same version-number-collision issue
  as JEI/twerk-crop-growth) plus its hard-required
  [KotlinLangForge](https://modrinth.com/mod/kotlin-lang-forge) language provider (its
  `neoforge.mods.toml` declares `modLoader="klf"`, not `javafml` - not obvious from VeinMiner's
  own Modrinth listing, only surfaced by actually reading the toml).
- ~~No way to reach distant biomes/resources from inside the tiny starting border~~ — new
  **Explore** tab (`Expedition.java`) does a free random teleport anywhere in a
  20,000x20,000-block square, auto-returns the player to wherever they left after 10 minutes
  (chat + GUI countdown warnings at 5/3/2/1 minute(s) left), and blocks re-use until they're
  back. The tricky part: this mod's world border is one real `WorldBorder` shared by the whole
  overworld (a real per-player border was tried once and reverted - see the multiplayer border
  clamp entry above for why it's not being revisited) - teleporting 10,000 blocks out would
  otherwise immediately trigger vanilla's own border push/damage plus this mod's 5-block
  stray-safety-net (`Border.onPlayerTick`). Fixed by temporarily growing that shared border for
  the expedition's duration (`Border.beginExpeditionHold`/`endExpeditionHold`, reference-counted
  so overlapping expeditions from multiple players don't stomp each other, snapshotting the real
  size once and restoring it when the last player gets back) rather than any mixin-based
  per-player exemption. `Border.tryExpand` now also refuses to run while any expedition is
  active, since the border's current size is a temporary placeholder, not real progress, during
  that window. Known shortcut: on a real multiplayer server, a player still at home sees their
  own tiny border balloon out for the duration of someone else's expedition - not worth mixins
  to fix given this mod's singleplayer-first design (same tradeoff already accepted for the one
  shared border in general).
  - Playtested and fixed once already: the first version dropped the player out of the world on
    landing. Root cause: `Level.getHeight`/`getHeightmapPos` silently falls back to
    `getMinBuildHeight()` (the void floor) for a chunk that isn't loaded yet, rather than
    generating it - and a random spot 10,000 blocks out is essentially guaranteed unloaded.
    Fixed by forcing `overworld.getChunk(x >> 4, z >> 4)` (blocks until `ChunkStatus.FULL`) right
    before the heightmap query, so it reflects real generated terrain. Still only boot-tested
    (client joins, no FATAL/Exception) beyond that one manual playtest - the countdown/warning
    timings and auto-return haven't been exercised, so treat those as unverified until played.
  - Added a watchdog for the abandoned-expedition case: `Border.EXPEDITIONS_ACTIVE` only ever
    decrements from `returnHome`, which only ever runs from that specific player's own
    `PlayerTickEvent` - a player who logs out mid-expedition and never comes back (crash, quits
    the world for good) would otherwise leave the shared border stuck enlarged and
    `Border.tryExpand` permanently refused, forever, for everyone else. Fixed by force-returning
    the player home (silently, no chat message) on `PlayerEvent.PlayerLoggedOutEvent` if they
    were away - covers every graceful disconnect; only a true crash/power-loss can still leave it
    stuck (no event fires for that case at all).
  - Added an "Expedition" `MobEffect` (`Expedition.EFFECT`, no inherent behavior - just a
    presence marker, same idiom as vanilla's plain effects like Confusion), applied for
    `DURATION_TICKS` when a player teleports out and removed on return, so the vanilla
    potion-icon countdown always agrees with the GUI/chat one. Blocks placing a *new* shop block
    while it's active (`BlockEvent.EntityPlaceEvent`, cancelled + chat explanation) - existing
    shop blocks are unaffected, this only stops confusion about which one is "home" if you drop
    a second one mid-trip.
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
- Added AE2, EnderIO, and GregTech CEu Modern as more dev-only `localRuntime` deps alongside
  Mekanism (see `build.gradle`), same reasoning and same verify-via-Modrinth-API-then-inspect
  process. Boot-tested clean to the title screen with all four loaded together (no FATAL, no
  missing-dependency errors) - proves boot-safety only, not that AE2's import bus or EnderIO's
  conduits actually interact with the shop block yet (that's the still-open item-pipe TODO
  above). Specifics:
  - `maven.modrinth:ae2:19.2.17` (latest) - needs `neoforge>=21.1.169`, satisfied as-is.
  - `maven.modrinth:guideme:21.1.17` - AE2 hard-requires this (its in-game guide book library)
    as a `REQUIRED` mod dependency; not obvious from AE2's own listing, only surfaced as a
    boot-time FATAL "requires guideme" until added.
  - `maven.modrinth:enderio:7.1.8-alpha` - NOT latest (`8.2.11-beta` needs
    `neoforge==21.1.216`, above our `neo_version`); pinned to the newest version whose
    `neoforge.mods.toml` only needs `neoforge>=21.1.58`.
  - `maven.modrinth:gregtechceu-modern:mc1.21.1-7.0.2` (latest) - needs `neoforge>=21.1`,
    satisfied as-is; its other required deps (`ldlib`, `configuration`) are embedded in the jar.
  - IC2 skipped, same as Thermal Expansion: no NeoForge/1.21.1 release exists on Modrinth for
    any of the IC2-lineage mods checked (`ic2classic`, `industrial-craft`) - both Forge-only,
    older MC versions.
- Added `maven.modrinth:twerk-crop-growth:txp9wDw2` as a fun dev-only `localRuntime` dep -
  sneak-spam speeds up crop/sapling growth, handy while playtesting farmable-goods pricing. No
  gameplay dependency, needs `neoforge>=21.1.145`, satisfied as-is. Pinned to the Modrinth
  version *id*, not the `3.0.0` semver string - that number is reused across separate
  fabric/forge/neoforge releases, so the plain string resolved ambiguously (picked the Fabric
  jar once, which NeoForge then refused to load with a "Skipping jar... is a Fabric mod"
  warning at boot). Added `RECOMMENDED-MODS.md` (linked from README, referenced from
  `.claude/CLAUDE.md`) as a quick-reference table of all `localRuntime` playtesting mods and the
  add-a-new-one checklist, including this version-collision gotcha.
- Added `maven.modrinth:jei:zHNxmOqp` (JEI 19.39.0.372) as a dev-only `localRuntime` dep for
  playtesting the shop's Buy/Sell tabs against real recipes. Same version-id pin as
  twerk-crop-growth above (JEI's forge/fabric/neoforge builds all share the same version
  number). Its own `neoforge.mods.toml` `minecraft` dependency range reads `[1.21, 1.21.1)` -
  looks like it excludes our exact `minecraft_version` (1.21.1) - but boot-tested clean anyway
  (JEI loaded, registered plugins for Mekanism/GTCEu, no FATAL/Exception); NeoForge's actual
  runtime version-range check evidently isn't as strict as the raw range text suggests, or
  treats it as inclusive-enough in practice. Noting this so a future range read isn't trusted
  as gospel without a boot-test to back it up.
