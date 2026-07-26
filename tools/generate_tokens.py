#!/usr/bin/env python3
"""Generates the 14 Drakonix OneBlockShop Token denominations (1..8192, powers of two):
item models, a lang entry each, combine/split recipes, and a hue-shifted 16x16 coin sprite
per denomination (pure stdlib PNG writer - no Pillow dependency).

Rerun this whenever the denomination list or sprite design changes; it overwrites its own
output files under src/main/resources deterministically, nothing here is hand-edited after
generation.
"""
import colorsys
import json
from pathlib import Path

from png_writer import write_png

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
MODID = "drakonixoneblockshop"

DENOMINATIONS = [1 << i for i in range(14)]  # 1, 2, 4, ..., 8192


def coin_sprite(hue: float):
    """16x16 flat-shaded coin: dark ring, hue-shifted fill, lighter highlight blob."""
    size = 16
    cx = cy = (size - 1) / 2
    fill = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.75, 0.95))
    ring = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.85, 0.55))
    highlight = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.35, 1.0))

    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            highlight_dist = ((x - (cx - 2.5)) ** 2 + (y - (cy - 2.5)) ** 2) ** 0.5
            if dist > 7.3:
                row.append((0, 0, 0, 0))
            elif dist > 6.3:
                row.append((*ring, 255))
            elif highlight_dist < 2.2:
                row.append((*highlight, 255))
            else:
                row.append((*fill, 255))
        pixels.append(row)
    return pixels


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    lang_path = RESOURCES / f"assets/{MODID}/lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))

    for i, value in enumerate(DENOMINATIONS):
        item_id = f"token_{value}"
        hue = i / len(DENOMINATIONS)

        write_png(RESOURCES / f"assets/{MODID}/textures/item/{item_id}.png", coin_sprite(hue))
        write_json(RESOURCES / f"assets/{MODID}/models/item/{item_id}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{MODID}:item/{item_id}"},
        })
        lang[f"item.{MODID}.{item_id}"] = f"Drakonix OneBlockShop Token ({value})"

        if i > 0:
            half_value = DENOMINATIONS[i - 1]
            write_json(RESOURCES / f"data/{MODID}/recipe/token_combine_{value}.json", {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": [
                    {"item": f"{MODID}:token_{half_value}"},
                    {"item": f"{MODID}:token_{half_value}"},
                ],
                "result": {"id": f"{MODID}:{item_id}", "count": 1},
            })
            write_json(RESOURCES / f"data/{MODID}/recipe/token_split_{value}.json", {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": [{"item": f"{MODID}:{item_id}"}],
                "result": {"id": f"{MODID}:token_{half_value}", "count": 2},
            })

    write_json(lang_path, dict(sorted(lang.items())))
    print(f"Generated {len(DENOMINATIONS)} token denominations: {DENOMINATIONS}")


if __name__ == "__main__":
    main()
