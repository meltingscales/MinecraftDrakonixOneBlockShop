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

# Regenerates the 14 token item textures/models/lang/recipes (see tools/generate_tokens.py).
# Rerun after changing the denomination list or the coin sprite design.
tokens-sync:
    python3 tools/generate_tokens.py

# Regenerates the "Expedition" status-effect icon and lang entry (see
# tools/generate_expedition_icon.py). Rerun if the effect's color or icon design changes.
expedition-icon-sync:
    python3 tools/generate_expedition_icon.py
