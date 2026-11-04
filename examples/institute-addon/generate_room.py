"""Generate both loader resource layouts from one small, deterministic NBT room.

Uses only Python's standard library. The output is checked into this example,
so building the addon does not require Python.
"""
from __future__ import annotations

import gzip
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent


def utf(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag(kind: int, name: str, payload: bytes) -> bytes:
    return bytes([kind]) + utf(name) + payload


def integer(name: str, value: int) -> bytes:
    return tag(3, name, struct.pack(">i", value))


def string(name: str, value: str) -> bytes:
    return tag(8, name, utf(value))


def compound(name: str, entries: bytes) -> bytes:
    return tag(10, name, entries + b"\x00")


def sequence(name: str, kind: int, values: list[bytes]) -> bytes:
    return tag(9, name, bytes([kind]) + struct.pack(">i", len(values)) + b"".join(values))


def int_list(name: str, values: tuple[int, ...]) -> bytes:
    return sequence(name, 3, [struct.pack(">i", value) for value in values])


def room(data_version: int) -> bytes:
    blocks: dict[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]] = {}

    def put(x: int, y: int, z: int, name: str, **properties: str) -> None:
        blocks[x, y, z] = name, tuple(sorted(properties.items()))

    # Air is explicit so a deliberate template placement clears the room interior.
    for y in range(6):
        for z in range(9):
            for x in range(9):
                put(x, y, z, "minecraft:air")
                if y == 0:
                    put(x, y, z, "ender_infection:lab_tile_light")
                elif y == 5:
                    put(x, y, z, "minecraft:smooth_quartz")
                elif x in (0, 8) or z in (0, 8):
                    put(x, y, z, "minecraft:white_concrete")
    for z in (0, 8):
        for x in range(3, 6):
            for y in range(1, 4):
                put(x, y, z, "minecraft:air")
    for x in (0, 8):
        for z in range(2, 7):
            for y in (2, 3):
                put(x, y, z, "minecraft:light_blue_stained_glass")
    for x in (2, 6):
        for z in (2, 6):
            put(x, 5, z, "minecraft:sea_lantern")
    # Cabinets and work surfaces leave the central three-wide route clear.
    for x in (1, 7):
        for z in range(2, 7):
            put(x, 1, z, "minecraft:smooth_stone")
        put(x, 1, 1, "minecraft:barrel", facing="up", open="false")
        put(x, 1, 7, "minecraft:barrel", facing="up", open="false")
    for x, facing in ((1, "east"), (7, "west")):
        put(x, 2, 3, "ender_infection:lab_monitor", facing=facing)
        put(x, 2, 5, "ender_infection:lab_sample_rack", facing=facing)
    put(1, 1, 6, "ender_infection:lab_sink", facing="east")
    put(7, 1, 6, "ender_infection:lab_storage_crate", facing="west")
    put(4, 1, 4, "institute_addon:research_console", powered="false")

    palette = sorted(set(blocks.values()))
    palette_index = {value: index for index, value in enumerate(palette)}
    palette_tags = []
    for name, properties in palette:
        entry = string("Name", name)
        if properties:
            entry += compound("Properties", b"".join(string(key, value) for key, value in properties))
        palette_tags.append(entry + b"\x00")
    block_tags = [int_list("pos", pos) + integer("state", palette_index[state]) + b"\x00"
                  for pos, state in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0]))]
    return compound("", integer("DataVersion", data_version) + int_list("size", (9, 6, 9))
                    + sequence("palette", 10, palette_tags) + sequence("blocks", 10, block_tags)
                    + sequence("entities", 10, []))


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    for loader, version, structures, loot_tables, tags in (
        ("forge", 3465, "structures", "loot_tables", "blocks"),
        ("neoforge", 3955, "structure", "loot_table", "block"),
    ):
        resources = ROOT / loader / "src/main/resources/data"
        destination = resources / "institute_addon" / structures / "analysis_room.nbt"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(gzip.compress(room(version), mtime=0))
        empty = compound("", integer("DataVersion", version) + int_list("size", (16, 10, 16))
                         + sequence("palette", 10, [string("Name", "minecraft:air") + b"\x00"])
                         + sequence("blocks", 10, []) + sequence("entities", 10, []))
        (destination.parent / "empty_test.nbt").write_bytes(gzip.compress(empty, mtime=0))
        write_json(resources / "institute_addon" / loot_tables / "blocks/research_console.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "institute_addon:research_console"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        for tag_name in ("mineable/pickaxe", "needs_stone_tool"):
            write_json(resources / "minecraft/tags" / tags / f"{tag_name}.json", {
                "replace": False, "values": ["institute_addon:research_console"]})
    print("Generated Forge and NeoForge analysis_room: 9x6x9, 486 blocks, two 3x3 entrances")


if __name__ == "__main__":
    main()
