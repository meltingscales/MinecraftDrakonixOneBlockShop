# Directions for AI agents working on this repo

NeoForge 1.21.1 mod (Java 21, NeoGradle). Read TODO.md and README.md first - they're kept
up to date and describe what's actually implemented vs. known shortcuts.

## Verify against decompiled sources, don't guess signatures

Before using an unfamiliar Minecraft/NeoForge API, check the real source instead of guessing:

- Vanilla: `build/neoForm/neoFormJoined*/steps/unzipSources/unpacked/net/minecraft/...`
- NeoForge: unzip the cached `neoforge-*-sources.jar` from `~/.gradle/caches/...`
- Vanilla datapack files (recipes, advancements, loot tables) for reference: unzip
  `build/jars/extra/client/*/client-extra.jar`

This has repeatedly caught things that would've been wrong guesses, e.g.:
- `AbstractContainerScreen.render()` no longer calls `renderTooltip()` itself in 1.21.1 - every
  vanilla screen overrides `render()` and calls `this.renderTooltip(guiGraphics, mouseX, mouseY)`
  explicitly after `super.render(...)`. Miss this and no tooltip ever renders, silently.
- `Slot`'s default `mayPlace()` returns `true` unconditionally - it does NOT call
  `Container.canPlaceItem()`. Only hoppers/droppers actually call `canPlaceItem` (and
  `WorldlyContainer.canPlaceItemThroughFace`) before inserting.
- 1.21.1 datapack folders are singular: `recipe/`, `loot_table/`, `advancement/` - not the old
  plural forms.

## Finding deprecated-API replacements

Don't patch a deprecation warning by guessing. Temporarily add
`options.compilerArgs << '-Xlint:deprecation'` to the `tasks.withType(JavaCompile)` block in
`build.gradle`, rebuild to see the exact call site + suggested replacement in the Javadoc, fix
it, then remove the compiler arg again. Don't leave it in permanently - it's noisy for every
compile.

## Build / boot-test loop

- `./gradlew compileJava` for a fast correctness check.
- There's no computer-use tool for the native Minecraft window, so boot-testing means:
  launch `./gradlew runClient` in the background, redirect output to a log file, then poll
  the log for `joined the game` (success) vs. `FATAL|Exception in thread|Traceback` (failure).
  This proves "boots without crashing," not visual/interactive correctness - say so explicitly
  rather than claiming a feature works after only a boot-test.
- Always kill the client process after boot-testing (`pkill -f "BootstrapLauncher.*forgeclientdev"`
  and the `GradleWrapperMain runClient` wrapper) - it doesn't exit on its own when backgrounded.

## Adding dev-only playtesting mods

See `RECOMMENDED-MODS.md` for the full checklist (Modrinth API query -> inspect
`neoforge.mods.toml` -> pin the newest compatible version -> check for version-number collisions
across loaders -> boot-test). Add new `localRuntime` mods there, not just in `build.gradle`, so
the table stays the source of truth for what's already been added and why.

## Economy / pricing

Every item is sellable - never add a hardcoded per-item price map. `Pricing.priceOf` prices raw
resources from a small hand-set seed table and derives everything craftable recursively from its
cheapest known recipe. Extend the seed table only for genuinely raw/base resources with no
recipe; let crafted items fall out of recipe pricing automatically.

## GUI (ShopScreen / ShopMenu)

- Tab state lives server-authoritative in `ShopMenu` (synced `DataSlot`), not as a client-only
  field on the `Screen`. A `Slot`'s `isActive()` override is what actually gates
  rendering/hover/click routing per tab (verified in `AbstractContainerScreen`) - don't hand-roll
  hitbox testing for tab-gated widgets.
- Use real `net.minecraft.client.gui.components.Button` widgets
  (`Screen.addRenderableWidget` in `init()`), synced every client tick via `containerTick()` -
  not hand-drawn text + manual `mouseClicked` hitbox math.

## Docs hygiene

TODO.md is the running status doc - after any feature/fix, move it from "Not built yet" into
"Fixed" (or add a new "Known shortcuts" entry if it's a deliberate simplification, tagged with
why it's fine for now). Keep README.md's feature description matching what's actually
implemented, not aspirational.

## Git / commits

Only commit when explicitly asked. Prefer small, separately-reviewable commits over one big
one when a turn touches unrelated concerns (e.g. docs/CI vs. code changes go in separate
commits). Never `--no-verify`, never force-push, never amend a commit that's already pushed.

Bump `mod_version` in `gradle.properties` when committing real changes (features, fixes,
content) - it's the single source of truth `release.yml` checks a pushed tag against (see
README.md's Releasing section). Skip the bump for changes that don't affect the mod itself
(this file, CI config tweaks, typo fixes in comments).
