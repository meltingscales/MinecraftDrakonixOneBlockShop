#!/usr/bin/env python3
"""Generates the 16x16 item texture for the Border Expansion Trophy (OneBlockShopMod.BORDER_TROPHY,
see Border.giveTrophy) at assets/drakonixoneblockshop/textures/item/border_trophy.png, plus its
model json and lang entry.

Unlike this repo's other icon generators (generate_tokens.py, generate_expedition_icon.py, which
write RGBA pixels straight to PNG via png_writer.py), this one goes through an intermediate PPM:
the pixel art is built as an opaque P6 PPM (no alpha channel in that format) with a magenta
sentinel background, written to a temp file, then read back and re-encoded as an RGBA PNG with
the sentinel color keyed out to transparent. Exercises a real ppm->png path rather than writing
the PNG directly.

Rerun this if the trophy's design changes, don't hand-edit the generated PNG.
"""
import json
import struct
import tempfile
from pathlib import Path

from png_writer import write_png

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
MODID = "drakonixoneblockshop"

SIZE = 16
TRANSPARENT_KEY = (255, 0, 255)  # magenta - PPM has no alpha, keyed out when converting to PNG

GOLD_FILL = (255, 200, 40)
GOLD_RING = (153, 115, 0)
GOLD_HILITE = (255, 245, 200)
BRONZE_FILL = (140, 90, 40)
BRONZE_RING = (85, 55, 20)


def bowl_half_width(y: float) -> float:
    """Linear taper from a wide rim (y=2) to a narrow throat (y=6) feeding the stem."""
    top, bottom = 5.5, 1.5
    t = max(0.0, min(1.0, (y - 2) / (6 - 2)))
    return top + (bottom - top) * t


def classify(x: int, y: int) -> str | None:
    cx = (SIZE - 1) / 2
    if 2 <= y <= 6 and abs(x - cx) <= bowl_half_width(y):
        return "bowl"
    if 3 <= y <= 4 and (x in (0, 1) or x in (SIZE - 2, SIZE - 1)):
        return "handle"
    if 7 <= y <= 9 and 7 <= x <= 8:
        return "stem"
    if y == 10 and 5 <= x <= 10:
        return "stem"
    if 11 <= y <= 12 and 4 <= x <= 11:
        return "base"
    return None


def is_edge(x: int, y: int, region: str) -> bool:
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if not (0 <= nx < SIZE and 0 <= ny < SIZE) or classify(nx, ny) != region:
            return True
    return False


def trophy_pixels():
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            region = classify(x, y)
            if region is None:
                row.append(TRANSPARENT_KEY)
                continue
            if region in ("bowl", "handle"):
                if region == "bowl" and 4 <= x <= 5 and y == 3:
                    row.append(GOLD_HILITE)
                elif is_edge(x, y, region):
                    row.append(GOLD_RING)
                else:
                    row.append(GOLD_FILL)
            else:
                row.append(BRONZE_RING if is_edge(x, y, region) else BRONZE_FILL)
        pixels.append(row)
    return pixels


def write_ppm(path: Path, pixels) -> None:
    """Plain binary PPM (P6): header + raw RGB triples, one row after another, no alpha."""
    width, height = len(pixels[0]), len(pixels)
    with open(path, "wb") as f:
        f.write(f"P6\n{width} {height}\n255\n".encode("ascii"))
        for row in pixels:
            for (r, g, b) in row:
                f.write(struct.pack("BBB", r, g, b))


def read_ppm_as_rgba(path: Path, transparent_key):
    with open(path, "rb") as f:
        magic = f.readline().strip()
        if magic != b"P6":
            raise ValueError(f"Unsupported PPM magic {magic!r}")
        width, height = map(int, f.readline().split())
        maxval = int(f.readline())
        assert maxval == 255
        data = f.read(width * height * 3)

    pixels = []
    for y in range(height):
        row = []
        for x in range(width):
            offset = (y * width + x) * 3
            r, g, b = data[offset], data[offset + 1], data[offset + 2]
            alpha = 0 if (r, g, b) == transparent_key else 255
            row.append((r, g, b, alpha))
        pixels.append(row)
    return pixels


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    rgb_pixels = trophy_pixels()

    with tempfile.TemporaryDirectory() as tmp:
        ppm_path = Path(tmp) / "border_trophy.ppm"
        write_ppm(ppm_path, rgb_pixels)
        rgba_pixels = read_ppm_as_rgba(ppm_path, TRANSPARENT_KEY)

    write_png(RESOURCES / f"assets/{MODID}/textures/item/border_trophy.png", rgba_pixels)

    write_json(RESOURCES / f"assets/{MODID}/models/item/border_trophy.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"{MODID}:item/border_trophy"},
    })

    lang_path = RESOURCES / f"assets/{MODID}/lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang[f"item.{MODID}.border_trophy"] = "Border Expansion Trophy"
    write_json(lang_path, dict(sorted(lang.items())))

    print("Generated border_trophy.png (16x16, via PPM), its model, and its lang entry")


if __name__ == "__main__":
    main()
