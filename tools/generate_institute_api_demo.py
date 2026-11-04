"""Rebuild the small API demonstration template (not the 48-room institute)."""
from pathlib import Path
import gzip
import struct

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "ender_infection_colored/src/main/resources/data/ender_infection/structures/institute/api_demo_room.nbt"


def utf(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def payload(kind, value):
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 8:
        return utf(value)
    if kind == 9:
        subtype, values = value
        return bytes([subtype]) + struct.pack(">i", len(values)) + b"".join(payload(subtype, v) for v in values)
    if kind == 10:
        return b"".join(bytes([t]) + utf(k) + payload(t, v) for k, (t, v) in value.items()) + b"\0"
    raise ValueError(kind)


def generate():
    palette, blocks = [], []
    keys = {}
    for y in range(7):
        for z in range(9):
            for x in range(9):
                name, props = "minecraft:air", {}
                if y in (0, 6):
                    name = "ender_infection:lab_tile_light"
                elif x in (0, 8) or z in (0, 8):
                    name = "ender_infection:lab_tile_dark"
                    if x in (0, 8) and 2 <= z <= 6 and y in (2, 3):
                        name = "minecraft:glass"
                    if z == 0 and 3 <= x <= 5 and 1 <= y <= 3:
                        name = "minecraft:air"
                if y == 1 and z in (5, 6) and x in (1, 2, 6, 7):
                    name = "minecraft:polished_andesite"
                if y == 2 and z == 6 and x in (2, 6):
                    name, props = "ender_infection:lab_monitor", {"facing": "north"}
                if y == 1 and z == 4 and x in (2, 6):
                    name, props = "ender_infection:lab_chair", {"facing": "south"}
                if y == 2 and z == 5 and x == 1:
                    name, props = "ender_infection:lab_sample_rack", {"facing": "east"}
                if y == 1 and z == 2 and x == 7:
                    name, props = "ender_infection:lab_storage_crate", {"facing": "west"}
                if y == 5 and x in (2, 6) and z in (2, 6):
                    name, props = "ender_infection:lab_light_panel", {"facing": "north"}
                key = name, tuple(sorted(props.items()))
                if key not in keys:
                    keys[key] = len(palette)
                    entry = {"Name": (8, name)}
                    if props:
                        entry["Properties"] = (10, {k: (8, v) for k, v in props.items()})
                    palette.append(entry)
                blocks.append({"pos": (9, (3, [x, y, z])), "state": (3, keys[key])})
    root = {"DataVersion": (3, 3465), "size": (9, (3, [9, 7, 9])),
            "palette": (9, (10, palette)), "blocks": (9, (10, blocks)), "entities": (9, (10, []))}
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_bytes(gzip.compress(bytes([10]) + utf("") + payload(10, root), mtime=0))
    print(f"{DEST.relative_to(ROOT)}: {len(blocks)} positions, {len(palette)} states")


if __name__ == "__main__":
    generate()
