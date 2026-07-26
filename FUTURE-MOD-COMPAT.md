# Future mod compatibility

Mods that were verified compatible (via the process in `RECOMMENDED-MODS.md`) and added as
`localRuntime` dev deps at some point, then deliberately removed again - not because they
stopped working, just to trim the dev environment for now. Kept here so re-adding one later
doesn't mean re-doing the Modrinth-API-query-and-toml-inspection legwork from scratch.

| Mod | Last verified version | Notes |
|---|---|---|
| [GregTech CEu Modern](https://modrinth.com/mod/gregtechceu-modern) | mc1.21.1-7.0.2 | Actively-maintained GregTech line for NeoForge/1.21.1 (the classic IC2/GT5u/GT6 lines have no NeoForge or 1.21.1 release). Needs `neoforge>=21.1`, satisfied by this project's `neo_version` (21.1.176) as of the version above. Its other required deps (`ldlib`, `configuration`) are embedded in the jar - nothing else to add. |

## Re-adding one

1. Re-verify against the current `neo_version` first - don't assume the pin above still holds;
   a newer GregTech release may need a newer `neoforge` than this project has, same as happened
   with Mekanism and EnderIO (see `RECOMMENDED-MODS.md`'s history for that exact failure mode).
2. Add the `localRuntime "maven.modrinth:<slug>:<version>"` line back to `build.gradle` with a
   comment explaining the pin.
3. Move its row from this file into `RECOMMENDED-MODS.md`'s table.
4. Boot-test (`./gradlew runClient`, watch for `joined the game` vs.
   `FATAL`/`Exception in thread`/`Skipping jar`) before trusting the version range alone.
