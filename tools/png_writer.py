"""Minimal stdlib-only RGBA PNG writer, shared by this repo's asset generator scripts
(generate_tokens.py, generate_expedition_icon.py) - no Pillow dependency available in this
environment.
"""
import struct
import zlib
from pathlib import Path


def write_png(path: Path, pixels) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    width = len(pixels[0])
    height = len(pixels)
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0 (none) per scanline
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)
