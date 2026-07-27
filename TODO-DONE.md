# TODO — Done

Finished items, split out of `TODO.md` to keep that file focused on what's still open. Newest
entries at the top; oldest (original MVP build-out) at the bottom.

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
