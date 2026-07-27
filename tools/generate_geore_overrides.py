#!/usr/bin/env python3
"""Overrides GeOre's `<ore>_cluster` loot tables (data/geore/loot_table/blocks/*.json) so every
geode cluster always drops its full block/item, with or without Silk Touch - GeOre's own tables
only do that with Silk Touch equipped, otherwise dropping a lesser "shard" item instead (same
pattern as vanilla's Amethyst Cluster). Lives in *this* mod's data folder (not GeOre's own, which
this mod has no compile dependency on) because a mod's own data/ folder is the only reliably
always-on datapack contribution - no per-world "enable this datapack" step needed, unlike files
dropped into a save's datapacks/ folder. Inert/harmless if GeOre isn't installed.

The ore list below was extracted from GeOre 6.2.3's own jar (every *_cluster.json filename it
ships) - rerun this after bumping the pinned GeOre version in build.gradle in case its ore list
changed, rather than hand-editing the generated overrides.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"

ORES = [
    "allthemodium", "aluminum", "ancient_debris", "black_quartz", "coal", "copper", "diamond",
    "emerald", "gold", "iron", "lapis", "lead", "monazite", "nickel", "osmium", "platinum",
    "quartz", "redstone", "ruby", "sapphire", "silver", "tin", "topaz", "tungsten",
    "unobtainium", "uraninite", "uranium", "vibranium", "zinc",
]


def cluster_loot_table(ore: str) -> dict:
    return {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": f"geore:{ore}_cluster"}],
            }
        ],
    }


def main():
    out_dir = RESOURCES / "data/geore/loot_table/blocks"
    out_dir.mkdir(parents=True, exist_ok=True)
    for ore in ORES:
        path = out_dir / f"{ore}_cluster.json"
        path.write_text(json.dumps(cluster_loot_table(ore), indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(ORES)} GeOre cluster loot table overrides")


if __name__ == "__main__":
    main()
