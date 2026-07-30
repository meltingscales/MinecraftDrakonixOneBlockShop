# Bumps mod_version (gradle.properties) and the modpack's own version (modpack/pack.toml) to
# the same value together, so they can't quietly drift apart (see CLAUDE.md's Git/commits
# section) - nothing in CI or tooling actually checks the two match, packwiz's version field is
# just a display string in packwiz-aware launchers, so a stale one is easy to miss otherwise.
bump-version version:
    #!/usr/bin/env bash
    set -euo pipefail
    sed -i 's/^mod_version=.*/mod_version={{ version }}/' gradle.properties
    sed -i 's/^version = ".*"/version = "{{ version }}"/' modpack/pack.toml
    echo "Bumped mod_version and modpack/pack.toml version to {{ version }}"

# Tag and push a GitHub release for the current mod_version (gradle.properties).
# release.yml builds the jar and publishes a GitHub Release once the tag lands on GitHub -
# its version check fails the build if the tag doesn't match mod_version exactly, so this
# recipe reads mod_version rather than taking a version argument to avoid that mismatch.
release:
    #!/usr/bin/env bash
    set -euo pipefail
    version=$(grep -oP '(?<=^mod_version=).*' gradle.properties)
    tag="v${version}"
    if [ -n "$(git status --porcelain)" ]; then
        echo "Working tree not clean - commit or stash first." >&2
        exit 1
    fi
    if git rev-parse "$tag" >/dev/null 2>&1; then
        echo "Tag $tag already exists." >&2
        exit 1
    fi
    git tag "$tag"
    git push origin "$tag"
    echo "Pushed $tag - release build: https://github.com/meltingscales/MinecraftDrakonixOneBlockShop/actions"

# Regenerates modpack/mods/*.pw.toml from build.gradle's localRuntime mods (see
# tools/generate_modpack.py) and validates the pack. Rerun after adding/removing/repinning a
# localRuntime mod - requires the packwiz CLI on PATH.
modpack-sync:
    python3 tools/generate_modpack.py

# Serves the modpack locally for testing with a packwiz-compatible launcher (e.g. Prism).
modpack-serve:
    cd modpack && packwiz serve

# Copies FTB Quests chapter/task/reward definitions into modpack/config/ftbquests/quests and
# re-syncs the pack (see scripts/quest-sync.sh and README.md's "Adding the quest line to the
# modpack"). With no argument, auto-picks the most recently modified
# drakonix-one-block-shop-* PrismLauncher instance - pass a path explicitly to override, e.g.:
#   just quest-sync ~/.local/share/PrismLauncher/instances/<instance>/minecraft/config/ftbquests/quests
quest-sync source_dir="":
    ./scripts/quest-sync.sh "{{ source_dir }}"

# Regenerates the 14 token item textures/models/lang/recipes (see tools/generate_tokens.py).
# Rerun after changing the denomination list or the coin sprite design.
tokens-sync:
    python3 tools/generate_tokens.py

# Regenerates the "Expedition" status-effect icon and lang entry (see
# tools/generate_expedition_icon.py). Rerun if the effect's color or icon design changes.
expedition-icon-sync:
    python3 tools/generate_expedition_icon.py

# Regenerates the Border Expansion Trophy's item texture/model/lang entry (see
# tools/generate_border_trophy_texture.py). Rerun if the trophy's design changes.
border-trophy-sync:
    python3 tools/generate_border_trophy_texture.py

# Regenerates GeOre cluster loot table overrides (see tools/generate_geore_overrides.py) so
# every geode cluster drops its full item with or without Silk Touch. Rerun after bumping the
# pinned GeOre version in build.gradle in case its ore list changed.
geore-overrides-sync:
    python3 tools/generate_geore_overrides.py
