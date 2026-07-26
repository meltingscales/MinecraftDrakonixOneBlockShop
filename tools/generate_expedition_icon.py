#!/usr/bin/env python3
"""Generates the status-effect icon for "Expedition" (Expedition.EFFECT): an 18x18 sprite at
assets/drakonixoneblockshop/textures/mob_effect/expedition.png - vanilla's mob-effect icon size
(confirmed against a real vanilla one, e.g. textures/mob_effect/absorption.png) and the
"directory" atlas source (assets/minecraft/atlases/mob_effects.json) that scans every
namespace's textures/mob_effect/ folder automatically, so no separate atlas registration is
needed here. Also writes the effect's lang entry.

Rerun this if the effect's color or icon design changes, don't hand-edit the generated PNG.
"""
import json
from pathlib import Path

from png_writer import write_png

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
MODID = "drakonixoneblockshop"

# Must match the color passed to `new MobEffect(MobEffectCategory.NEUTRAL, 0x55FFFF)` in
# Expedition.java, so the icon and the potion-swirl tint agree.
COLOR = (0x55, 0xFF, 0xFF)


def expedition_icon():
    """18x18 flat-shaded badge, same visual language as the token coins (generate_tokens.py):
    dark ring, flat fill, lighter highlight blob - just sized for the mob-effect icon slot."""
    size = 18
    cx = cy = (size - 1) / 2
    fill = COLOR
    ring = tuple(round(c * 0.6) for c in COLOR)
    highlight = tuple(round(c + (255 - c) * 0.6) for c in COLOR)

    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            highlight_dist = ((x - (cx - 3)) ** 2 + (y - (cy - 3)) ** 2) ** 0.5
            if dist > 8.3:
                row.append((0, 0, 0, 0))
            elif dist > 7.2:
                row.append((*ring, 255))
            elif highlight_dist < 2.5:
                row.append((*highlight, 255))
            else:
                row.append((*fill, 255))
        pixels.append(row)
    return pixels


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    write_png(RESOURCES / f"assets/{MODID}/textures/mob_effect/expedition.png", expedition_icon())

    lang_path = RESOURCES / f"assets/{MODID}/lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang[f"effect.{MODID}.expedition"] = "Expedition"
    write_json(lang_path, dict(sorted(lang.items())))

    print("Generated expedition.png (18x18) and its lang entry")


if __name__ == "__main__":
    main()
