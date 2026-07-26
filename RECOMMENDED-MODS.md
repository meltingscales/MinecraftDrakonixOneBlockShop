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
| [GregTech CEu Modern](https://modrinth.com/mod/gregtechceu-modern) | mc1.21.1-7.0.2 | Actively-maintained GregTech line for NeoForge/1.21.1. |
| [Twerk Crop Growth](https://modrinth.com/mod/twerk-crop-growth) | 3.0.0 | Sneak-spam speeds up crop/sapling growth - handy while playtesting farmable-goods pricing. |

Not added: IC2 (no NeoForge/1.21.1 release exists for any IC2-lineage mod as of writing) and
Thermal Expansion (last release is 1.20.1 Forge-only). Re-check Modrinth if either ships one.

## Adding another

Same process every time, don't guess compatibility:

1. Query Modrinth for real NeoForge/1.21.1 releases:
   `https://api.modrinth.com/v2/project/<slug>/version?loaders=["neoforge"]&game_versions=["1.21.1"]`
2. Download the candidate jar and inspect `META-INF/neoforge.mods.toml` for its `neoforge`
   dependency version range against this project's pinned `neo_version` (see `gradle.properties`).
3. Pick the newest version whose range is still satisfied - not necessarily the latest release.
4. Add a `localRuntime "maven.modrinth:<slug>:<version>"` line in `build.gradle` with a comment
   explaining the pin, and update the table above.
