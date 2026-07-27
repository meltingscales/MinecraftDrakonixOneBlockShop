# Recommended mods

Dev-only `localRuntime` mods declared in `build.gradle`, pulled automatically from Modrinth
on any fresh Gradle sync (no manual jar placement needed). They're playtesting aids only -
never a compile or published dependency of this mod. See `build.gradle` for the exact
version-pinning rationale next to each line; this file is just the quick-reference list.

| Mod | Version pinned | Why it's here |
|---|---|---|
| [Mekanism](https://modrinth.com/mod/mekanism) | 10.7.14.79 | Supplies osmium etc. for tag-based tech-mod pricing (`pricing/seed_prices_by_tag.json`). |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | 19.2.17 | Import bus is a named case for the tech-mod item-pipe TODO. |
| [GuideMe](https://modrinth.com/mod/guideme) | 21.1.17 | Hard-required by AE2 (its in-game guide book library). |
| [EnderIO](https://modrinth.com/mod/enderio) | 7.1.8-alpha | Conduits are the other named pipe-equivalent case. |
| [Twerk Crop Growth](https://modrinth.com/mod/twerk-crop-growth) | `txp9wDw2` (3.0.0) | Sneak-spam speeds up crop/sapling growth - handy while playtesting farmable-goods pricing. |
| [JEI](https://modrinth.com/mod/jei) | `zHNxmOqp` (19.39.0.372) | Recipe/item viewer, useful while playtesting the shop's Buy/Sell tabs. |
| [TreeChop](https://modrinth.com/mod/treechop) | 0.19.3 | Fells whole trees in one hit - handy for the log volume Sell/Explore playtesting wants. |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | neoforge-1.21.1-26.4.2 | Minimap + waypoints, handy for finding your way back to base after an Explore-tab teleport. |
| [Jade](https://modrinth.com/mod/jade) | 15.10.5+neoforge | WAILA-equivalent tooltip HUD - see note below, not literally WAILA. |
| [KotlinLangForge](https://modrinth.com/mod/kotlin-lang-forge) | 2.12.2-k2.4.10-3.0+neoforge | Kotlin mod-loader-language provider - hard-required by VeinMiner. |
| [VeinMiner](https://modrinth.com/mod/veinminer) | `syKekkIm` (2.11.2) | Mines a whole ore vein in one break - fast resources for Sell/Explore playtesting, same reasoning as TreeChop. |
| [Inventory Tweaks: ReFoxed](https://modrinth.com/mod/inventory-tweaks-refoxed) | 1.21.1-1.2.0 | Bulk inventory sort/move-into-storage QoL. |
| [AppleSkin](https://modrinth.com/mod/appleskin) | 3.0.9+mc1.21 | Hunger/saturation HUD - handy while playtesting hopper-automation Sell loops. |
| [Clumps](https://modrinth.com/mod/clumps) | `jo7lDoK4` (19.0.0.1) | Merges XP orbs - less lag/clutter around the guide book's suggested mob farms. |
| [ParCool!](https://modrinth.com/mod/parcool) | 3.4.3.3 | Real parkour movement (wall-run/slide/mantle/roll) - the closest NeoForge substitute for FastMove - Parkour Movement, which has no Forge/NeoForge release at all (Fabric/Quilt only, confirmed exhaustively). |
| [GeOre](https://modrinth.com/mod/geore) | 6.2.3 | Geode-style ore veins - fits this mod's own "sell raw resources" economy with more/better ore to gather. Its cluster loot tables are overridden (`tools/generate_geore_overrides.py`, `just geore-overrides-sync`) to always drop the full item regardless of Silk Touch - bump that script's ore list too if this pin ever changes. |
| [Dissolver Enhanced](https://modrinth.com/mod/dissolver-enhanced-item-transmutation-(emc)) | `yEX3Y3na` (1.5.31-neoforge-1.21.x) | EMC-style item transmutation, standing in for ProjectE - see note below, ProjectE itself has no Modrinth listing at all. Pinned by project id since the slug has literal parentheses that risk misparsing as a Gradle coordinate. |
| [Sophisticated Core](https://modrinth.com/mod/sophisticated-core) | 1.21.1-1.4.80.2194 | Shared library hard-required by Sophisticated Backpacks (not obvious from that mod's own page - only surfaced via its declared Modrinth dependency list). |
| [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | 1.21.1-3.25.73.2020 | Upgradeable backpacks with their own inventory tabs/filters/auto-sort - useful given this mod's whole loop is fetch-and-carry (Sell trips, Explore-tab expeditions). |

Not added: IC2 (no NeoForge/1.21.1 release exists for any IC2-lineage mod as of writing) and
Thermal Expansion (last release is 1.20.1 Forge-only). Re-check Modrinth if either ships one.
Also not literally WAILA: that mod's last real client-side release is 1.16.5-era Forge/Fabric
(`hwyla`/`waila-stages` on Modrinth) - Jade is its actively-maintained modern successor and
what current packs use in its place. Same story for ProjectE - no Modrinth listing exists at
all (not just missing this version), checked by slug, by search, and by a search for its
original author (sinkillerj); likely CurseForge-exclusive. Also not FastMove - Parkour Movement,
which is Fabric/Quilt-only with zero Forge/NeoForge releases ever - ParCool! (already in the
table above) is its NeoForge substitute.

Removed for now: GregTech CEu Modern - see [FUTURE-MOD-COMPAT.md](FUTURE-MOD-COMPAT.md) for the
version/compatibility notes to pick back up when it's re-added.

## Adding another

Same process every time, don't guess compatibility:

1. Query Modrinth for real NeoForge/1.21.1 releases:
   `https://api.modrinth.com/v2/project/<slug>/version?loaders=["neoforge"]&game_versions=["1.21.1"]`
2. Download the candidate jar and inspect `META-INF/neoforge.mods.toml` for its `neoforge`
   dependency version range against this project's pinned `neo_version` (see `gradle.properties`).
3. Pick the newest version whose range is still satisfied - not necessarily the latest release.
4. Check whether that version *number* is reused across other loaders (fetch
   `.../project/<slug>/version` unfiltered and look for duplicate `version_number`s across
   `loaders`). Several mods (twerk-crop-growth, JEI) publish separate fabric/forge/neoforge jars
   all sharing one version string - `maven.modrinth:<slug>:<version-number>` then resolves
   ambiguously (it picked the Fabric jar once, which NeoForge refused to load). If it collides,
   pin the Modrinth **version id** instead (`.../version` response's `"id"` field, not
   `"version_number"`) - `maven.modrinth:<slug>:<version-id>` always resolves the exact release.
5. Add a `localRuntime "maven.modrinth:<slug>:<version-or-id>"` line in `build.gradle` with a
   comment explaining the pin, and update the table above.
6. Boot-test (`./gradlew runClient`, watch the log for `joined the game` vs.
   `FATAL`/`Exception in thread`/`Skipping jar`) before trusting any of the above - a
   `neoforge.mods.toml` version range can look wrong (or look fine) and still not match how
   NeoForge actually resolves it at runtime.
