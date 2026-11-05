"""Verify the four native control-device views appended after operations acceptance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

from PIL import Image


NAMES = ("generator_front", "generator_rear_side", "distribution_front", "distribution_rear_side")
EQUIPMENT_PASS = "INSTITUTE_EQUIPMENT_VISUAL_PASS screenshots=4"
OPERATIONS_PASS = "INSTITUTE_OPERATIONS_VISUAL_PASS screenshots=10 processing_menus=2 archive_once=true"


def verify(log: Path, folder: Path) -> dict:
    raw = log.read_bytes()
    text = raw.decode("utf-8", errors="replace")
    if "INSTITUTE_OPERATIONS_VISUAL_FAIL" in text or "INSTITUTE_EQUIPMENT_VISUAL_FAIL" in text:
        raise ValueError("Native operations/equipment client failed")
    if text.count(EQUIPMENT_PASS) != 1 or text.count(OPERATIONS_PASS) != 1:
        raise ValueError("Expected the completed operations flow and four equipment views exactly once")
    captured = re.findall(r"(?m)^INSTITUTE_EQUIPMENT_CAPTURE (\w+)\s*$", text)
    if tuple(captured) != NAMES:
        raise ValueError("Missing, repeated or out-of-order equipment cameras")
    if text.index(EQUIPMENT_PASS) >= text.index(OPERATIONS_PASS):
        raise ValueError("Operations success must follow all equipment captures")
    if any(text.index("INSTITUTE_EQUIPMENT_CAPTURE " + name) >= text.index(EQUIPMENT_PASS) for name in NAMES):
        raise ValueError("Equipment success appeared before its images")
    if {p.stem for p in folder.glob("*.png")} != set(NAMES):
        raise ValueError("Expected exactly the four equipment PNGs in their independent directory")
    images = []
    for name in NAMES:
        path = folder / (name + ".png")
        with Image.open(path) as frame:
            frame.load()
            width, height = frame.size
            if frame.format != "PNG" or width < 640 or height < 360:
                raise ValueError("Invalid native equipment PNG: " + name)
            sample = frame.convert("RGB").resize((96, 54))
            pixels = [sample.getpixel((x, y)) for y in range(54) for x in range(96)]
            if len(set(pixels)) < 128 or sum(sum(p) / 3 > 40 for p in pixels) < len(pixels) * .08:
                raise ValueError("Blank or unreadably dark equipment screenshot: " + name)
        images.append({"file": path.name, "bytes": path.stat().st_size,
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "width": width, "height": height})
    return {"status": "PASS", "screenshots": 4, "independent_equipment_directory": True,
            "operations_flow_completed": True, "operations_screenshot_count_unchanged": 10,
            "log_sha256": hashlib.sha256(raw).hexdigest(), "images": images,
            "scope": "Native rendering views of the already repaired/powered manifest controls; no additional business-flow or animation claim."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("screenshots", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = verify(args.log, args.screenshots)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + "\n", "utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "images"}, indent=2))
