#!/usr/bin/env python3
"""Generates the 14 Drakonix OneBlockShop Token denominations (1..8192, powers of two):
item models, a lang entry each, combine/split recipes, and a 16x16 metal-coin sprite per
denomination stamped with a dragon wing, hue-shifted per denomination (pure stdlib PNG
writer - no Pillow dependency).

Rerun this whenever the denomination list or sprite design changes; it overwrites its own
output files under src/main/resources deterministically, nothing here is hand-edited after
generation.
"""
import colorsys
import json
import math
from pathlib import Path

from png_writer import write_png

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
MODID = "drakonixoneblockshop"

DENOMINATIONS = [1 << i for i in range(14)]  # 1, 2, 4, ..., 8192

# Wing fan: bones radiate from a shoulder point near the coin's lower-left rim out toward
# the upper right, each as (dx, dy, length). Membrane between adjacent bones tapers with a
# sine dip (scalloped, bat/dragon-wing trailing edge) instead of a straight line.
WING_SHOULDER = (4.0, 12.0)
WING_FINGERS = [
    (math.cos(math.radians(a)), math.sin(math.radians(a)), length)
    for a, length in [(-85, 7.5), (-58, 9.5), (-31, 9.5), (-4, 7.5)]
]
WING_SCALLOP_DEPTH = 2.2
WING_BONE_HALF_WIDTH = 0.45


def _wing_stamp(x: float, y: float):
    """None outside the wing, else 'bone' (raised) or 'membrane' (recessed) for that pixel."""
    vx, vy = x - WING_SHOULDER[0], y - WING_SHOULDER[1]
    angle = math.atan2(vy, vx)
    angles = [math.atan2(dy, dx) for dx, dy, _ in WING_FINGERS]
    if angle < angles[0] or angle > angles[-1]:
        return None

    r = math.hypot(vx, vy)
    for i in range(len(angles) - 1):
        if not (angles[i] <= angle <= angles[i + 1]):
            continue
        t = (angle - angles[i]) / (angles[i + 1] - angles[i])
        base_radius = WING_FINGERS[i][2] + (WING_FINGERS[i + 1][2] - WING_FINGERS[i][2]) * t
        edge_radius = base_radius - math.sin(math.pi * t) * WING_SCALLOP_DEPTH
        if r > edge_radius:
            return None
        for dx, dy, length in (WING_FINGERS[i], WING_FINGERS[i + 1]):
            proj = vx * dx + vy * dy
            if 0 <= proj <= length:
                perp = abs(vx * dy - vy * dx)
                if perp < WING_BONE_HALF_WIDTH:
                    return "bone"
        return "membrane"
    return None


def coin_sprite(hue: float):
    """16x16 metal coin (dark rim groove, matte metal fill) stamped with a dragon wing
    fanning from the lower-left rim: raised bone highlights, recessed membrane shadow."""
    size = 16
    cx = cy = (size - 1) / 2
    fill = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.25, 0.9))
    rim = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.4, 0.5))
    bone = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.15, 1.0))
    membrane = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.4, 0.55))

    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if dist > 7.3:
                row.append((0, 0, 0, 0))
            elif dist > 6.3:
                row.append((*rim, 255))
            else:
                stamp = _wing_stamp(x, y)
                color = bone if stamp == "bone" else membrane if stamp == "membrane" else fill
                row.append((*color, 255))
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
            # "components" on the result forces the Unsellable curse onto every crafted token,
            # same as a minted one (Wallet.cursedToken) - without it, combining/splitting was a
            # loophole that produced a clean, sellable token (crafting results don't inherit
            # ingredient components automatically).
            unsellable = {"minecraft:enchantments": {f"{MODID}:unsellable": 1}}
            write_json(RESOURCES / f"data/{MODID}/recipe/token_combine_{value}.json", {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": [
                    {"item": f"{MODID}:token_{half_value}"},
                    {"item": f"{MODID}:token_{half_value}"},
                ],
                "result": {"id": f"{MODID}:{item_id}", "count": 1, "components": unsellable},
            })
            write_json(RESOURCES / f"data/{MODID}/recipe/token_split_{value}.json", {
                "type": "minecraft:crafting_shapeless",
                "category": "misc",
                "ingredients": [{"item": f"{MODID}:{item_id}"}],
                "result": {"id": f"{MODID}:token_{half_value}", "count": 2, "components": unsellable},
            })

    write_json(lang_path, dict(sorted(lang.items())))
    print(f"Generated {len(DENOMINATIONS)} token denominations: {DENOMINATIONS}")


if __name__ == "__main__":
    main()
