#!/usr/bin/env python3
"""Generates the 16x16 item textures for the two Expedition scroll items (Expedition.java's
ReturnHomeScrollItem / ExpeditionResumeScrollItem, replacing the old potion-based items) at
assets/drakonixoneblockshop/textures/item/{return_home_scroll,expedition_resume_scroll}.png, plus
their model jsons and lang entries.

Same ppm->png pipeline as generate_border_trophy_texture.py: pixel art built as an opaque P6 PPM
with a magenta sentinel background, written to a temp file, then read back and re-encoded as an
RGBA PNG with the sentinel keyed out to transparent.

Rerun this if either scroll's design changes, don't hand-edit the generated PNGs.
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

PARCHMENT_FILL = (232, 217, 176)
PARCHMENT_RING = (139, 111, 61)
ROD_FILL = (107, 68, 35)
ROD_RING = (74, 46, 20)

# Matches the color each item's teleport originally used as its MobEffectInstance icon color
# (RETURN_EFFECT / RESUME_EFFECT in Expedition.java) - keeps the same visual language.
SCROLLS = {
    "return_home_scroll": {"seal": (0x55, 0xAA, 0xFF), "name": "Return Home Scroll"},
    "expedition_resume_scroll": {"seal": (0x55, 0xFF, 0xAA), "name": "Expedition Resume Scroll"},
}


def classify(x: int, y: int) -> str | None:
    if 4 <= y <= 11 and (x in (0, 1) or x in (14, 15)):
        return "rod"
    if 6 <= y <= 9 and 2 <= x <= 13:
        return "seal" if 6 <= x <= 9 else "parchment"
    return None


def is_edge(x: int, y: int, region: str) -> bool:
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if not (0 <= nx < SIZE and 0 <= ny < SIZE) or classify(nx, ny) not in (region, "seal" if region == "parchment" else None):
            return True
    return False


def scroll_pixels(seal_color):
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            region = classify(x, y)
            if region == "rod":
                row.append(ROD_RING if is_edge(x, y, "rod") else ROD_FILL)
            elif region == "parchment":
                row.append(PARCHMENT_RING if is_edge(x, y, "parchment") else PARCHMENT_FILL)
            elif region == "seal":
                row.append(seal_color)
            else:
                row.append(TRANSPARENT_KEY)
        pixels.append(row)
    return pixels


def write_ppm(path: Path, pixels) -> None:
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
    lang_path = RESOURCES / f"assets/{MODID}/lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))

    for item_id, spec in SCROLLS.items():
        rgb_pixels = scroll_pixels(spec["seal"])

        with tempfile.TemporaryDirectory() as tmp:
            ppm_path = Path(tmp) / f"{item_id}.ppm"
            write_ppm(ppm_path, rgb_pixels)
            rgba_pixels = read_ppm_as_rgba(ppm_path, TRANSPARENT_KEY)

        write_png(RESOURCES / f"assets/{MODID}/textures/item/{item_id}.png", rgba_pixels)
        write_json(RESOURCES / f"assets/{MODID}/models/item/{item_id}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{MODID}:item/{item_id}"},
        })
        lang[f"item.{MODID}.{item_id}"] = spec["name"]

    write_json(lang_path, dict(sorted(lang.items())))
    print(f"Generated textures/models/lang for {len(SCROLLS)} expedition scroll items")


if __name__ == "__main__":
    main()
