"""Independently verify the shipped central-institute tiles, not the drawing generator.

Only the standard library is required. The dense state-index array is under 2 MiB;
each tile's NBT tree is released before reading the next tile.
"""
from __future__ import annotations

import argparse
from array import array
from collections import Counter, deque
import json
import hashlib
from pathlib import Path
import sys

from nbt_codec import load

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "ender_infection_colored/src/main/resources"
FACINGS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
FACES_3D = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
STORAGE_FIXTURES = {"lab_cabinet", "lab_workbench", "lab_glass_cabinet", "lab_cryo_cabinet", "lab_server_rack", "lab_control_console"}
STORAGE_DETAILS = {"lab_freezer", "lab_storage_crate", "lab_shelf", "lab_rolling_cart", "lab_sink"}


class InvalidInstitute(AssertionError):
    pass


def require(value, message):
    if not value:
        raise InvalidInstitute(message)


def coord(item):
    return tuple(item[k] for k in ("x", "y", "z")) if isinstance(item, dict) else tuple(item)


class Model:
    def __init__(self, manifest, resources):
        self.manifest = manifest
        self.width, self.height, self.length = (int(manifest[k]) for k in ("width", "height", "length"))
        self.volume = self.width * self.height * self.length
        self.voxels = array("H", [0]) * self.volume
        self.covered = bytearray(self.volume)
        self.palette = [("<missing>", ())]
        self.indices = {self.palette[0]: 0}
        self.entities = {}
        self.tiles = []
        for tile in manifest["tiles"]:
            template = tile["template"]
            namespace, key = template.split(":", 1)
            file = resources / "data" / namespace / "structures" / (key + ".nbt")
            if not file.exists():
                file = resources / "data" / namespace / "structure" / (key + ".nbt")
            if "sha256" in tile:
                require(hashlib.sha256(file.read_bytes()).hexdigest() == tile["sha256"], f"Tile digest mismatch: {template}")
            nbt = load(file)
            size = tuple(nbt["size"])
            require(size == tuple(tile[k] for k in ("width", "height", "length")), f"Tile dimensions differ: {template}")
            remap = []
            for state in nbt["palette"]:
                entry = (state["Name"], tuple(sorted(state.get("Properties", {}).items())))
                if entry not in self.indices:
                    self.indices[entry] = len(self.palette)
                    self.palette.append(entry)
                remap.append(self.indices[entry])
            tx, ty, tz = coord(tile)
            require(0 <= tx < self.width and 0 <= ty < self.height and 0 <= tz < self.length, f"Tile outside site: {template}")
            require(tx + size[0] <= self.width and ty + size[1] <= self.height and tz + size[2] <= self.length, f"Tile exceeds site: {template}")
            local_seen = set()
            for block in nbt["blocks"]:
                local = tuple(block["pos"])
                require(all(0 <= p < size[i] for i, p in enumerate(local)), f"Out-of-bounds tile block: {template}/{local}")
                require(local not in local_seen, f"Duplicate block: {template}/{local}")
                local_seen.add(local)
                pos = tx + local[0], ty + local[1], tz + local[2]
                index = self.index(pos)
                require(not self.covered[index], f"Overlapping tile blocks: {pos}")
                self.covered[index] = 1
                self.voxels[index] = remap[block["state"]]
                if "nbt" in block:
                    self.entities[pos] = block["nbt"]
            require(len(local_seen) == size[0] * size[1] * size[2], f"Sparse tile omits AIR/terrain clearing: {template}")
            self.tiles.append((tx, ty, tz, *size))
        require(all(self.covered), "Tiles leave uncovered cells in the final structure")

    def index(self, pos):
        x, y, z = pos
        return (y * self.length + z) * self.width + x

    def inside(self, pos):
        x, y, z = pos
        return 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length

    def state(self, pos):
        if not self.inside(pos):
            return "<outside>", {}
        name, properties = self.palette[self.voxels[self.index(pos)]]
        return name, dict(properties)

    def part_anchor(self, pos, state):
        face = state["facing"]
        dx, dz = FACINGS[face]
        right = -dz, dx
        offset = 1 - int(state["part_x"])
        return pos[0] + right[0] * offset, pos[1] - int(state["part_y"]), pos[2] + right[1] * offset

    def empty_for_walking(self, pos):
        name, properties = self.state(pos)
        if name in AIR or name == "minecraft:light":
            return True
        if name.endswith("lab_fixture_part"):
            root_name = self.state(self.part_anchor(pos, properties))[0]
            return "lab_door" in root_name or "lab_privacy_curtain" in root_name
        if "lab_door" in name or name.endswith("_door") or "lab_privacy_curtain" in name:
            return True  # All controlled doors are evaluated in their open state.
        if name.endswith("_trapdoor"):
            return properties.get("open") == "true"
        return name in {"minecraft:redstone_wire", "minecraft:lever", "minecraft:wall_torch", "minecraft:torch", "minecraft:soul_torch", "minecraft:soul_wall_torch"}

    def standing(self, pos):
        x, y, z = pos
        return (self.empty_for_walking(pos) and self.empty_for_walking((x, y + 1, z))
                and not self.empty_for_walking((x, y - 1, z)) and self.state((x, y - 1, z))[0] not in {"<outside>", "minecraft:structure_void"})

    def reachable(self, start):
        """3-D support graph; rises need an actual stair, never a 1-block jump.

        Integer nodes conservatively round the stair's half-step upward. The native
        GameTest additionally exercises Minecraft's collision shapes/player movement.
        """
        require(self.standing(start), f"Entrance has no standing space/support: {start}, {self.state(start)}")
        visited = bytearray(self.volume)
        visited[self.index(start)] = 1
        queue = deque([start])
        while queue:
            x, y, z = queue.popleft()
            for dx, dz in FACINGS.values():
                for dy in (0, 1, -1):
                    at = x + dx, y + dy, z + dz
                    if not self.inside(at) or visited[self.index(at)] or not self.standing(at):
                        continue
                    if dy == 1 and not self.is_stair((at[0], at[1] - 1, at[2])):
                        continue
                    if dy == -1 and not self.is_stair((x, y - 1, z)):
                        continue
                    visited[self.index(at)] = 1
                    queue.append(at)
        return visited

    def is_stair(self, pos):
        name = self.state(pos)[0]
        return name.endswith("_stairs") or name in {"ender_infection:lab_stair", "ender_infection:lab_stair_eroded"}


def verify(manifest, resources):
    require(tuple(manifest[k] for k in ("width", "height", "length")) == (120, 55, 104), "Expected the 120 x 104 site / 55-block vertical envelope")
    require(manifest["ground_offset"] == 28, "Ground offset must preserve B3 foundation at -28")
    require(manifest["max_terrain_relief"] == 8, "Natural terrain relief must agree with the eight-tread access roads")
    require(len(manifest["tiles"]) == 56, "Expected 56 independently placeable horizontal tiles")
    rooms = manifest["rooms"]
    require(len(rooms) == 48 and len({r["id"] for r in rooms}) == 48, "Expected 48 unique furnished rooms")
    floors = Counter(r["floor"] for r in rooms)
    require(len(floors) == 6 and set(floors.values()) == {8}, f"Expected 8 rooms per floor: {floors}")
    model = Model(manifest, resources)
    counts = Counter(model.palette[value][0] for value in model.voxels)
    require(sum(counts[a] for a in AIR) > 100000, "Interior AIR volumes are missing")
    require(counts["minecraft:structure_void"] == manifest["preserve_mask"]["count"], "Outdoor preserve mask count differs from the actual NBT")
    for y in range(29):
        for z in range(8, 88):
            for x in range(8, 112):
                require(model.state((x, y, z))[0] != "minecraft:structure_void", f"Underground main-building interior was left as terrain: {(x, y, z)}")
    multipart, boundary_parts, beds = 0, 0, 0
    actual_storage = {}
    for y in range(model.height):
        for z in range(model.length):
            for x in range(model.width):
                pos = x, y, z
                name, props = model.state(pos)
                if name.endswith("lab_fixture_part"):
                    multipart += 1
                    root = model.part_anchor(pos, props)
                    root_name, root_props = model.state(root)
                    require(root_name.startswith("ender_infection:") and not root_name.endswith("lab_fixture_part"), f"Orphan furniture part {pos} -> {root}/{root_name}")
                    require(root_props.get("facing") == props.get("facing"), f"Furniture facing mismatch at {pos}")
                    if (x // 16, z // 16) != (root[0] // 16, root[2] // 16):
                        boundary_parts += 1
                elif name.startswith("ender_infection:"):
                    key = name.split(":", 1)[1].removesuffix("_eroded")
                    height = 3 if key == "incubator" else 2 if key in {"lab_door", "lab_cabinet", "surgery_table", "lab_workbench", "lab_window", "lab_glass_cabinet", "lab_cryo_cabinet", "lab_server_rack", "lab_control_console", "lab_privacy_curtain"} else 1
                    half_width = 1 if key in {"surgery_table", "lab_workbench", "lab_window", "lab_control_console"} else 0
                    if key in STORAGE_FIXTURES | STORAGE_DETAILS:
                        actual_storage[pos] = name
                    if height > 1 or half_width:
                        dx, dz = FACINGS[props["facing"]]
                        for horizontal in range(-half_width, half_width + 1):
                            for vertical in range(height):
                                if horizontal == vertical == 0:
                                    continue
                                part = x - dz * horizontal, y + vertical, z + dx * horizontal
                                part_name, part_props = model.state(part)
                                require(part_name == "ender_infection:lab_fixture_part" and model.part_anchor(part, part_props) == pos, f"Furniture footprint incomplete: {pos}/{name} -> {part}")
                elif name.endswith("_bed"):
                    beds += 1
                    dx, dz = FACINGS[props["facing"]]
                    sign = 1 if props["part"] == "foot" else -1
                    other, other_props = model.state((x + dx * sign, y, z + dz * sign))
                    require(other == name and other_props.get("facing") == props["facing"] and other_props.get("part") != props["part"], f"Broken paired bed at {pos}")
    require(multipart > 100, "Research furniture footprint parts are absent")
    # A content change may move every prop off a seam. Boundary behavior is also
    # explicitly exercised by the native tests, so report rather than require it.
    containers = manifest["loot_containers"]
    require(len(containers) == 48 and {c["room"] for c in containers} == {r["id"] for r in rooms}, "One themed supply container is required per room")
    for container in containers:
        pos = coord(container)
        name, _ = model.state(pos)
        require(name == container["block"], f"Missing room supply block at {pos}")
        require(model.entities.get(pos, {}).get("LootTable") == container["loot_table"], f"Missing/wrong room loot table at {pos}")
        namespace, key = container["loot_table"].split(":", 1)
        require((resources / "data" / namespace / "loot_tables" / (key + ".json")).is_file() or (resources / "data" / namespace / "loot_table" / (key + ".json")).is_file(), f"Unshipped loot table: {container['loot_table']}")
    storage = manifest["storage_containers"]
    require(storage and len({coord(item) for item in storage}) == len(storage), "Generated furniture containers are missing or duplicate")
    require({coord(item) for item in storage} == set(actual_storage), "A real storage furniture block was omitted from the deferred-loot manifest")
    for item in storage:
        at = coord(item)
        require(model.state(at)[0] == item["block"], f"Storage furniture identity differs: {item}")
        key = item["block"].split(":", 1)[1].removesuffix("_eroded")
        expected_id = "ender_infection:lab_fixture" if key in STORAGE_FIXTURES else "ender_infection:lab_storage"
        entity = model.entities.get(at, {})
        require(entity.get("id") == expected_id == item["block_entity_id"], f"Storage furniture has wrong block entity type: {item}")
        require(entity.get("LootTable") == item["loot_table"] and not entity.get("Items"), f"Storage loot must remain deferred in the shipped NBT: {item}")
        require(item["loot_table"].startswith("ender_infection:chests/central_institute/furniture/"), f"Furniture must use the room's low-value loot table: {item}")
        namespace, key = item["loot_table"].split(":", 1)
        table_file = resources / "data" / namespace / "loot_tables" / (key + ".json")
        if not table_file.is_file():
            table_file = resources / "data" / namespace / "loot_table" / (key + ".json")
        require(table_file.is_file(), f"Furniture loot table was not shipped: {item['loot_table']}")
    signs = manifest["room_signs"]
    require(len(signs) == 48 and {item["room"] for item in signs} == {room["id"] for room in rooms}, "Expected one real labelled sign for each room")
    for sign in signs:
        at = coord(sign)
        require(model.state(at)[0] == sign["block"] and model.entities.get(at, {}).get("id") == "minecraft:sign", f"Room label is not an actual sign block entity: {sign}")
        encoded = model.entities[at].get("front_text", {}).get("messages", [])
        text = [json.loads(line).get("text", "") for line in encoded]
        require(text == sign["text"] and text[0].startswith(sign["room"] + " "), f"Room sign NBT/text differs from its declared room: {sign}")
    start = coord(manifest["entrance"])
    visited = model.reachable(start)
    # Inspect actual final road geometry independently of the generator source.
    for tread in range(8):
        y = 28 - tread
        for x in range(53, 67):
            at = x, y, 96 + tread
            require(model.is_stair(at) and model.state(at)[1].get("facing") == "north", f"Front approach does not contain eight graded treads: {at}")
            require(model.state((x, y - 1, at[2]))[0] not in AIR | {"minecraft:structure_void"}, f"Unsupported front access tread: {at}")
            feet = x, y + 1, at[2]
            require(model.standing(feet) and visited[model.index(feet)], f"Front access tread is obstructed/disconnected: {at}")
        for z in range(45, 51):
            at = 112 + tread, y, z
            require(model.is_stair(at) and model.state(at)[1].get("facing") == "west", f"Loading approach does not contain eight graded treads: {at}")
            require(model.state((at[0], y - 1, z))[0] not in AIR | {"minecraft:structure_void"}, f"Unsupported loading access tread: {at}")
            feet = at[0], y + 1, z
            require(model.standing(feet) and visited[model.index(feet)], f"Loading access tread is obstructed/disconnected: {at}")
    for container in containers:
        at = coord(container)
        approaches = [(at[0] + dx, at[1], at[2] + dz) for dx, dz in FACINGS.values()]
        require(any(model.inside(p) and model.standing(p) and visited[model.index(p)] for p in approaches), f"Supply cannot be reached by a standing player: {container['room']}/{at}")
    for room in rooms:
        require(room["doors"], f"No room access: {room['id']}")
        accessible = False
        for door in room["doors"]:
            at = coord(door)
            require("door" in model.state(at)[0], f"Missing actual room door: {room['id']}/{at}")
            require(model.state((at[0], at[1] + 1, at[2]))[0].endswith("lab_fixture_part") or model.state((at[0], at[1] + 1, at[2]))[1].get("half") == "upper", f"Missing upper door: {room['id']}/{at}")
            if model.inside(at) and visited[model.index(at)]:
                dx, dz = FACINGS[door["facing"]]
                approaches = [(at[0] + sign * dx, at[1], at[2] + sign * dz) for sign in (-1, 1)]
                accessible |= all(model.standing(p) and visited[model.index(p)] for p in approaches)
        require(accessible, f"Room door approaches are disconnected/obstructed: {room['id']}")
    stairs = manifest["stairs"]
    require(len(stairs) == 10, "Expected two stairs across all five floor joins")
    for flight in stairs:
        lo, hi = coord(flight["lower_landing"]), coord(flight["upper_landing"])
        require(hi[1] - lo[1] == flight["rise"], f"Stair elevation mismatch: {flight}")
        for support in (lo, hi):
            feet = support[0], support[1] + 1, support[2]
            require(model.standing(feet) and visited[model.index(feet)], f"Stair landing disconnected: {flight['lower_floor']}->{flight['upper_floor']} {feet}")
    require(len(manifest["lift"]["landings"]) == 6, "Cargo lift needs all six floors")
    for landing in manifest["lift"]["landings"]:
        at = coord(landing)
        require("lift" in model.state(at)[0], f"Missing actual cargo lift: {at}")
        require(model.standing((at[0], at[1] + 1, at[2])), f"Obstructed lift landing: {at}")
    emissions = {"minecraft:sea_lantern": 15, "ender_infection:lab_light_panel": 14, "ender_infection:lab_task_lamp": 14, "minecraft:redstone_lamp": 15, "minecraft:light": 15}
    actual_sources = set()
    actual_crystals = set()
    for y in range(model.height):
        for z in range(model.length):
            for x in range(model.width):
                name, state = model.state((x, y, z))
                if name in emissions and (name != "minecraft:redstone_lamp" or state.get("lit") == "true"):
                    actual_sources.add((x, y, z))
                if "amethyst" in name or "crystal" in name:
                    actual_crystals.add((x, y, z))
    require({coord(s) for s in manifest["light_sources"]} == actual_sources, "Actual lamps and declared light sources differ")
    require({coord(s) for s in manifest["crystal_lighting"]} == actual_crystals, "An actual crystal was omitted from the glow checks")
    for source in manifest["light_sources"]:
        name, state = model.state(coord(source))
        require(name == source["block"] and emissions[name] == source["light_level"], f"Wrong physical light source: {source}")
        require(name != "minecraft:redstone_lamp" or state.get("lit") == "true", f"Alarm lamp is not lit: {source}")
        require(name != "minecraft:light" or state.get("level") == "15", f"Invisible fill-light has the wrong emission: {source}")
    for crystal in manifest["crystal_lighting"]:
        at, source = coord(crystal), coord(crystal["source"])
        name = model.state(source)[0]
        distance = sum(abs(a - b) for a, b in zip(at, source))
        require(name in emissions and distance == crystal["distance"] and emissions[name] > distance, f"Crystal has no nearby emitting source: {crystal}")
        probe = coord(crystal["probe"])
        require(model.state(at)[0] == crystal["block"], f"Crystal identity differs from the actual NBT: {crystal}")
        require(model.state(probe)[0] in AIR, f"The exposed crystal surface must keep its adjacent cell AIR: {crystal}")
        step = FACES_3D[crystal["exposed_face"]]
        require(probe == tuple(a + d for a, d in zip(at, step)), f"Crystal exposure direction/probe mismatch: {crystal}")
        require(crystal["path"] == [list(probe), list(source)] and sum(abs(a - b) for a, b in zip(probe, source)) == 1, f"Crystal illumination lacks an unobstructed AIR-to-light path: {crystal}")
        require(name == "minecraft:light" and distance == 2, f"Exposed crystals need an external physical fill-light at distance two: {crystal}")
        for dx, dy, dz in FACES_3D.values():
            require(model.state((at[0] + dx, at[1] + dy, at[2] + dz))[0] != "minecraft:light", f"Fill-light occupies a crystal's adjacent AIR/bud growth cell: {crystal}")
    return {"status": "PASS", "rooms": len(rooms), "room_signs": len(signs), "floors": dict(floors), "tiles": len(model.tiles), "explicit_voxels": model.volume, "multipart": multipart, "parts_crossing_tiles": boundary_parts, "bed_halves": beds, "loot_containers": len(containers), "storage_containers": len(storage), "storage_by_block": dict(Counter(actual_storage.values())), "reachable_standing_cells": sum(visited), "preserved_terrain_cells": counts["minecraft:structure_void"], "light_sources": len(actual_sources), "lit_crystals": len(actual_crystals)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=RESOURCES)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        manifest = json.loads((args.resources / "data/ender_infection/central_institute.json").read_text(encoding="utf-8"))
        report = verify(manifest, args.resources)
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("CENTRAL_INSTITUTE_VERIFY_PASS " + json.dumps(report, ensure_ascii=False))
        return 0
    except (OSError, ValueError, KeyError, AssertionError) as error:
        print(f"CENTRAL_INSTITUTE_VERIFY_FAIL {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
