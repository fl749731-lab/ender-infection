"""Verify native storage screens and the real viewer-held cabinet model."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PROJECTS = {"forge": "ender_infection_colored", "neoforge": "ender_infection_neoforge"}
KEYS = ("lab_cabinet", "lab_cabinet_eroded", "lab_workbench", "lab_workbench_eroded",
        "lab_freezer", "lab_storage_crate", "lab_shelf", "lab_glass_cabinet", "lab_cryo_cabinet",
        "lab_server_rack", "lab_control_console", "lab_rolling_cart", "lab_sink")


def verify(loader: str, log: Path | None = None, screenshots: Path | None = None) -> dict:
    if loader not in PROJECTS:
        raise ValueError(f"Unknown loader: {loader}")
    short = "neo" if loader == "neoforge" else loader
    log = log or ROOT / f"build/central-institute/storage-{short}-visual.log"
    screenshots = screenshots or ROOT / PROJECTS[loader] / "build/storage-visual-check/screenshots"
    content = log.read_text("utf-8", errors="replace")
    expected = {"menu_" + key for key in KEYS} | {"model_open_lab_cabinet"}
    complete = "LAB_STORAGE_VISUAL_PASS menus=13 screenshots=14 storage_slots=27 player_slots=36 real_open_model=true"
    if "LAB_STORAGE_VISUAL_FAIL" in content or content.count(complete) != 1:
        raise ValueError(f"No successful real-menu/open-model acceptance: {log}")
    names = re.findall(r"LAB_STORAGE_CAPTURE ([a-zA-Z0-9_]+)", content)
    if len(names) != 14 or set(names) != expected:
        raise ValueError("Missing or repeated storage screenshots")
    menus = re.findall(r"LAB_STORAGE_MENU_PASS ([a-zA-Z0-9_]+) rows=3 storage_slots=27 player_slots=36 container_id=(\d+)", content)
    if len(menus) != 13 or {key for key, _ in menus} != set(KEYS) or any(int(mid) <= 0 for _, mid in menus):
        raise ValueError("Missing native server/client inventory bindings")
    lights = re.findall(r"LAB_STORAGE_SCENE_LIGHT_PASS ([a-zA-Z0-9_]+) server_front_block_light=(\d+)", content)
    if len(lights) != 13 or {key for key, _ in lights} != set(KEYS) or any(int(value) < 12 for _, value in lights):
        raise ValueError("Storage models lack native lighting acceptance")
    captures = []
    for name in sorted(expected):
        path = screenshots / (name + ".png")
        with Image.open(path) as frame:
            frame.load()
            width, height = frame.size
            if frame.format != "PNG" or width < 640 or height < 360:
                raise ValueError(f"Invalid/undersized PNG: {path}")
            sample = frame.convert("RGB").resize((128, 72))
            pixels = [sample.getpixel((x, y)) for y in range(72) for x in range(128)]
            if len(set(pixels)) < 128 or sum(max(p)-min(p)>12 for p in pixels) < len(pixels)*.005:
                raise ValueError(f"Blank native storage capture: {path}")
            if name == "model_open_lab_cabinet":
                # Sky can be colourful while the actual cabinet has no lighting.
                body = frame.convert("RGB").crop((int(width*.36), int(height*.35), int(width*.64), int(height*.93))).resize((48, 64))
                bright = sum(sum(body.getpixel((x, y)))/3 > 55 for y in range(64) for x in range(48))
                if bright < 48*64*.12:
                    raise ValueError(f"Cabinet model is too dark to review: {path}")
        captures.append({"name": name, "path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                         "width": width, "height": height})
    return {"ok": True, "status": "PASS", "loader": loader, "menus": 13, "screenshots": 14,
            "storage_slots": 27, "player_slots": 36, "real_open_model": True, "captures": captures,
            "log_sha256": hashlib.sha256(log.read_bytes()).hexdigest()}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader", choices=PROJECTS, required=True)
    parser.add_argument("--log", type=Path)
    parser.add_argument("--screenshots", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = verify(args.loader, args.log, args.screenshots)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", "utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "captures"}, indent=2))
