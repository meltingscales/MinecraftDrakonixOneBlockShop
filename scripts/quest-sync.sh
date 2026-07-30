#!/usr/bin/env bash
# Copies FTB Quests chapter/task/reward definitions into modpack/config/ftbquests/quests and
# re-syncs the pack - see README.md's "Adding the quest line to the modpack". Invoked via
# `just quest-sync`, not meant to be run standalone (assumes cwd is the repo root).
#
# Takes the instance's config/ftbquests/quests folder (NOT saves/<world>/ftbquests - that's
# per-world progress, not quest definitions, and must never be bundled). With no argument,
# defaults to the most recently modified drakonix-one-block-shop-* PrismLauncher instance found,
# so a fresh mrpack import under a new version-numbered instance name doesn't need this script
# (or the justfile) updated by hand.
set -euo pipefail

source_dir="${1:-}"

if [ -z "$source_dir" ]; then
    source_dir=$(ls -dt "$HOME"/.local/share/PrismLauncher/instances/drakonix-one-block-shop-*/minecraft/config/ftbquests/quests 2>/dev/null | head -1 || true)
    if [ -z "$source_dir" ]; then
        echo "No drakonix-one-block-shop-* PrismLauncher instance with config/ftbquests/quests found under ~/.local/share/PrismLauncher/instances/ - pass the path explicitly: just quest-sync <path>" >&2
        exit 1
    fi
    echo "No path given - using most recently modified instance: $source_dir"
fi

if [ ! -f "$source_dir/data.snbt" ]; then
    echo "$source_dir doesn't look like a config/ftbquests/quests folder (no data.snbt found)." >&2
    echo "Point this at the instance's config/ftbquests/quests, not a save's ftbquests/ (that's progress data, not quest definitions)." >&2
    exit 1
fi

rm -rf modpack/config/ftbquests/quests
mkdir -p modpack/config/ftbquests
cp -r "$source_dir" modpack/config/ftbquests/quests
just modpack-sync
echo "Synced quest definitions from $source_dir into modpack/config/ftbquests/quests"
