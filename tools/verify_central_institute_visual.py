"""Require completed natural generation and every planned native screenshot."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import struct
from PIL import Image


def verify(log: Path, screenshots: Path) -> dict:
    content = log.read_text("utf-8", errors="replace")
    if "CENTRAL_INSTITUTE_VISUAL_FAIL" in content:
        raise ValueError(f"Native visual acceptance failed: {log}")
    natural = re.search(r"CENTRAL_INSTITUTE_NATURAL_PASS full_chunks=(\d+) rooms=48 pieces=56", content)
    complete = re.search(r"CENTRAL_INSTITUTE_VISUAL_PASS natural=true rooms=48 full_chunks=(\d+) screenshots=61", content)
    if not natural or not complete or int(natural[1]) < 90 or natural[1] != complete[1]:
        raise ValueError(f"No complete natural-generation/61-camera acceptance: {log}")
    expected = {f"room_{letter}{number}" for letter in "ABCDEF" for number in range(1, 9)}
    expected.update(f"floor_{floor}" for floor in ("G1", "G2", "G3", "B1", "B2", "B3"))
    expected.update(("00_exterior_front", "01_exterior_rear", "02_atrium", "stairs_1", "stairs_10", "night_exterior", "night_reactor"))
    captured = re.findall(r"CENTRAL_INSTITUTE_CAPTURE ([a-zA-Z0-9_]+)", content)
    if set(captured) != expected or len(captured) != len(expected):
        raise ValueError("Missing or duplicate camera captures")
    for name in expected:
        path = screenshots / f"{name}.png"
        with path.open("rb") as image:
            header = image.read(24)
        if header[:8] != b"\x89PNG\r\n\x1a\n" or len(header) != 24:
            raise ValueError(f"Invalid PNG: {path}")
        width, height = struct.unpack(">II", header[16:24])
        if width < 640 or height < 360:
            raise ValueError(f"Capture too small: {path}: {width}x{height}")
        with Image.open(path) as image:
            sample = image.convert("RGB").resize((96, 54))
            pixels = [sample.getpixel((x, y)) for y in range(54) for x in range(96)]
        # A teleport can produce a valid PNG of sky before terrain reaches the client.
        # Require both textured content and some terrain/interior shading.
        if len(set(pixels)) < 128 or sum(b < 170 for _, _, b in pixels) < len(pixels) * .015:
            raise ValueError(f"Blank/unloaded camera view: {path}")
    return {"status": "PASS", "natural": True, "rooms": 48, "pieces": 56,
            "full_chunks": int(natural[1]), "screenshots": len(expected),
            "log": str(log), "directory": str(screenshots)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("screenshots", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = verify(args.log, args.screenshots)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", "utf-8")
    print(json.dumps(report, indent=2))
