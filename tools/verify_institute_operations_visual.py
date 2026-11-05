"""Accept real repair/access/processing/reward client evidence, never a mere successful launch."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
from PIL import Image

NAMES = {"power_off", "power_on", "door_denied", "door_unlocked", "microscope_running",
         "microscope_done", "centrifuge_running", "centrifuge_done", "archive_reward", "medical_furniture"}


def verify(log: Path, folder: Path) -> dict:
    text = log.read_text("utf-8", errors="replace")
    if "INSTITUTE_OPERATIONS_VISUAL_FAIL" in text or text.count(
            "INSTITUTE_OPERATIONS_VISUAL_PASS screenshots=10 processing_menus=2 archive_once=true") != 1:
        raise ValueError("Incomplete native institute operations acceptance")
    captured = re.findall(r"INSTITUTE_OPERATIONS_CAPTURE (\w+)", text)
    if len(captured) != 10 or set(captured) != NAMES:
        raise ValueError("Missing or repeated institute operations cameras")
    for marker in (
        "INSTITUTE_OPERATIONS_REPAIR_PASS paid_fuse=1 copper=4 redstone=4",
        "INSTITUTE_OPERATIONS_DOOR_DENIED_PASS powered=true keycard=false",
        "INSTITUTE_OPERATIONS_EMERGENCY_PASS hand_empty=true containment=false",
    ):
        if text.count(marker) != 1:
            raise ValueError("Missing real interaction receipt: " + marker)
    menus = re.findall(r"INSTITUTE_OPERATIONS_MENU_PASS (lab_microscope|lab_centrifuge) machine_slots=3 player_slots=36 powered=true progress=(\d+) duration=(\d+) container_id=(\d+)", text)
    if len(menus) != 2 or {m[0] for m in menus} != {"lab_microscope", "lab_centrifuge"}:
        raise ValueError("The two real processor screens were not synchronized")
    if any(not (0 < int(progress) < int(duration)) or int(menu_id) <= 0 for _, progress, duration, menu_id in menus):
        raise ValueError("Processor screenshot was not taken during real synchronized processing")
    images = []
    for name in sorted(NAMES):
        path = folder / (name + ".png")
        with Image.open(path) as frame:
            frame.load()
            width, height = frame.size
            if frame.format != "PNG" or width < 640 or height < 360:
                raise ValueError("Invalid native PNG: " + name)
            sample = frame.convert("RGB").resize((96, 54))
            pixels = [sample.getpixel((x, y)) for y in range(54) for x in range(96)]
            if len(set(pixels)) < 128 or sum(sum(p)/3 > 40 for p in pixels) < len(pixels)*.08:
                raise ValueError("Blank or unreadably dark native screenshot: " + name)
        images.append({"file": path.name, "bytes": path.stat().st_size,
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "width": width, "height": height})
    return {"status": "PASS", "screenshots": 10, "processing_menus": 2,
            "native_network_interactions": True, "archive_once": True,
            "log_sha256": hashlib.sha256(log.read_bytes()).hexdigest(), "images": images}


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
