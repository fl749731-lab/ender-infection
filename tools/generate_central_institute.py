"""Assemble the six-storey, 48-room institute into chunk-sized vanilla NBT.

The editable room specification and site plan are the source of truth.  This
generator deliberately does not import old debug laboratories, load Minecraft,
or touch a player's instance.  All coordinates below use the site plan's Y;
only the NBT/manifest boundary adds GROUND_OFFSET.
"""
from collections import Counter, deque
from hashlib import sha256
from pathlib import Path
import gzip
import json
import struct
import base64
import zlib

import institute_room_spec as spec
from institute_architecture import refine, furnish_public, furnish_rooms, mother_chamber

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'ender_infection_colored/src/main/resources/data/ender_infection'
DEST = DATA / 'structures/central_institute'
REPORT = ROOT / 'build/central-institute/static-validation.json'
WIDTH, LENGTH, HEIGHT, GROUND_OFFSET = 120, 104, 55, 28
MAX_TERRAIN_RELIEF = 8
LAYOUT_VERSION = 'central-institute-v5'
FLOORS = [('B3', -25), ('B2', -14), ('B1', -7), ('G1', 0), ('G2', 7), ('G3', 14)]
AIR = ('minecraft:air', (), None)
VOID = ('minecraft:structure_void', (), None)
CATEGORIES = {'公共': 'public', '安保': 'security', '医疗': 'medical',
              '科研': 'research', '机务': 'engineering', '隔离': 'containment',
              '档案': 'archives', '温室': 'greenhouse'}


def state(block, props=None, loot=None):
    return spec.full(block), tuple(sorted((str(k), str(v)) for k, v in (props or {}).items())), loot


def xyz(x, y, z):
    return [x, y + GROUND_OFFSET, z]


class Institute:
    def __init__(self):
        self.grid = {}
        self.fixtures = []
        self.rooms = []
        self.stairs = []
        self.loot = []
        self.storage = []
        self.signs = []
        self.crystal_faces = []
        self.operations = {'controls': [], 'secured_doors': [], 'managed_lights': [], 'machines': []}
        self.incidents = {}
        self.bosses = {}
        self.lift = {'x': 75, 'z': 39, 'landings': []}
        self.entrance = {'x': 59, 'y': 29, 'z': 87, 'facing': 'south'}

    def put(self, x, y, z, block='minecraft:air', props=None, loot=None):
        assert 0 <= x < WIDTH and -28 <= y <= 26 and 0 <= z < LENGTH, (x, y, z)
        self.grid[x, y, z] = AIR if block == 'minecraft:air' else state(block, props, loot)

    def box(self, x, y, z, X, Y, Z, block='minecraft:air', props=None):
        value = AIR if block == 'minecraft:air' else state(block, props)
        for yy in range(y, Y):
            for zz in range(z, Z):
                for xx in range(x, X):
                    self.grid[xx, yy, zz] = value

    def fixture(self, x, y, z, block, facing='north', opened=False):
        props = {'facing': facing, 'open': str(opened).lower()}
        self.put(x, y, z, block, props)
        cells = spec.footprint(spec.full(block), props, x, z, y)
        dx, dz = spec.FACES[facing]
        right = (-dz, dx)
        for X, Z, Y in cells:
            if (X, Y, Z) == (x, y, z):
                continue
            offset = (X - x) * right[0] + (Z - z) * right[1]
            self.put(X, Y, Z, 'lab_fixture_part',
                     {'facing': facing, 'part_x': offset + 1, 'part_y': Y - y})
        self.fixtures.append({'anchor': (x, y, z), 'block': spec.full(block),
                              'facing': facing, 'cells': [(X, Y, Z) for X, Z, Y in cells]})

    def clear(self, x, y, z, X, Y, Z):
        # Erasing one automatic part must erase its whole root, never leave an
        # invisible collision cell or a renderer whose footprint was removed.
        for f in self.fixtures:
            if any(x <= a < X and y <= b < Y and z <= c < Z for a, b, c in f['cells']):
                for p in f['cells']:
                    self.grid[p] = AIR
        self.box(x, y, z, X, Y, Z)

    def shell(self):
        # Explicit interior air is required for a buried structure template.
        self.box(8, -28, 8, 112, 27, 88)
        self.box(8, -28, 8, 112, -25, 88, 'minecraft:polished_deepslate')
        for level, y in FLOORS:
            palette = spec.PALETTES[level]
            ceiling = y + (10 if level == 'B3' else 6)
            self.box(8, y, 8, 112, y + 1, 88, palette['floor'])
            self.box(8, ceiling, 8, 112, ceiling + 1, 88, palette['ceiling'])
            for z in range(8, 88):
                for x in range(8, 112):
                    if x in (8, 111) or z in (8, 87):
                        for h in range(1, ceiling - y):
                            bay = z if x in (8, 111) else x
                            window = y >= 0 and 2 <= h <= 4 and bay % 8 not in (0, 7)
                            column = y >= 0 and bay % 8 in (0, 7)
                            self.put(x, y + h, z, 'minecraft:light_gray_stained_glass' if window else 'lab_tile_dark' if column else palette['wall'])
            # Recessed overhead illumination of all four circulation spines.
            for x in (38, 81):
                for z in range(15, 86, 7):
                    self.put(x, ceiling - 1, z, 'lab_light_panel', {'facing': 'north'})
            for z in (33, 61):
                for x in range(16, 108, 7):
                    self.put(x, ceiling - 1, z, 'lab_light_panel', {'facing': 'north'})
            for x in (36, 83):
                for z in range(9, 87):
                    self.put(x, y, z, palette['accent'])
            for z in (32, 63):
                for x in range(9, 111):
                    self.put(x, y, z, palette['accent'])
        self.box(8, 21, 8, 112, 22, 88, 'lab_tile_dark')
        # Foundation plinth and roof parapet make the envelope visibly solid.
        for x in range(8, 112):
            for z in (8, 87):
                self.put(x, 22, z, 'lab_slab', {'type': 'bottom', 'waterlogged': 'false'})
        for z in range(9, 87):
            for x in (8, 111):
                self.put(x, 22, z, 'lab_slab', {'type': 'bottom', 'waterlogged': 'false'})

    def add_rooms(self):
        for floor in spec.LAYOUT['floors']:
            for source in floor['rooms']:
                r = spec.make_room(floor, source)
                spec.validate_room(r)
                ox, oz = r['origin_xz']
                fy = r['floor_y']
                doors = []
                for p in r['placements']:
                    x, y, z = ox + p['u'], fy + p['h'], oz + p['v']
                    base = p['block'].split(':')[1].replace('_eroded', '')
                    if p['block'].startswith(spec.NS) and base in spec.FIXTURES:
                        self.fixture(x, y, z, p['block'], p['state']['facing'], p['state'].get('open') == 'true')
                    else:
                        self.put(x, y, z, p['block'], p['state'])
                    if p['layer'] == 'door':
                        doors.append(dict(zip(('x', 'y', 'z'), xyz(x, y, z)), facing=p['state']['facing']))
                self.rooms.append({'id': r['id'], 'name': r['name'], 'floor': r['level'],
                                   'category': CATEGORIES[r['category']], 'x': ox, 'y': fy + 28,
                                   'z': oz, 'width': r['size'][0], 'height': r['ceiling_h'] + 1,
                                   'length': r['size'][1], 'doors': doors,
                                   'layout_type': r['layout_type'], 'furniture_zones': r['modules']})

    def atrium(self):
        self.clear(40, 1, 36, 80, 26, 60)
        self.box(40, 26, 36, 80, 27, 60, 'minecraft:glass')
        for y in range(21, 26):
            for x in range(40, 80):
                for z in (36, 59):
                    self.put(x, y, z, 'lab_tile_dark' if x % 8 == 0 else 'minecraft:glass')
            for z in range(37, 59):
                for x in (40, 79):
                    self.put(x, y, z, 'lab_tile_dark' if z % 8 == 0 else 'minecraft:glass')
        for y in (7, 14):
            for x in range(40, 80):
                for z in (36, 59):
                    self.put(x, y + 1, z, 'minecraft:iron_bars')
            for z in range(37, 59):
                for x in (40, 79):
                    self.put(x, y + 1, z, 'minecraft:iron_bars')
        # B2 observation floor is sealed glass, rather than an open fatal shaft.
        self.box(48, -14, 44, 72, -13, 56, 'minecraft:glass')
        self.box(48, -15, 44, 72, -14, 56, 'minecraft:glass')
        for floor_y in (-14, -7):
            for x in (49, 59, 69):
                for z in (45, 55):
                    self.put(x, floor_y + 5, z, 'lab_light_panel', {'facing': 'north'})
        # Ground-floor reception island stays outside the traffic cores.
        for x in range(55, 65):
            self.put(x, 0, 49, 'lab_tile_dark')
        for x in (56, 59, 62):
            self.put(x, 1, 49, 'lab_freezer', {'facing': 'south'})
            self.put(x, 2, 49, 'lab_monitor', {'facing': 'south'})
        for x, z in ((50, 41), (67, 41), (50, 54), (67, 54)):
            self.put(x, 0, z, 'minecraft:sea_lantern')
            self.put(x, 1, z, 'minecraft:potted_fern')
        for x in (54, 65):
            for z in (43, 52):
                self.put(x, 0, z, 'lab_tile_dark')
                self.box(x, 1, z, x + 1, 4, z + 1, 'minecraft:iron_bars')
                self.put(x, 4, z, 'minecraft:sea_lantern')

    def traffic(self):
        def guard(x, y, z):
            if self.grid.get((x, y, z), AIR)[0] in ('minecraft:air', 'minecraft:iron_bars'):
                self.put(x, y, z, 'minecraft:iron_bars')

        cores = [('west', 41, 39), ('east', 73, 49)]
        for name, x, z in cores:
            self.clear(x, -25, z, x + 6, 18, z + 8)
            for level, y in FLOORS:
                self.box(x, y, z, x + 6, y + 1, z + 2, 'lab_tile_dark')
                if y >= 0:
                    if name == 'west':
                        self.box(39, y, z - 1, x + 6, y + 1, z + 1, 'lab_tile_dark')
                        self.clear(40, y + 1, z - 1, 41, y + 3, z + 1)
                        for X in range(40, x + 6):
                            guard(X, y + 1, z - 2)
                    else:
                        self.box(x, y, z - 1, 81, y + 1, z + 1, 'lab_tile_dark')
                        self.clear(79, y + 1, z - 1, 80, y + 3, z + 1)
                        for X in range(x, 80):
                            guard(X, y + 1, z - 2)
                self.put(x + 2, y + 3, z, 'lab_light_panel', {'facing': 'north'})
            for (lower, y), (upper, Y) in zip(FLOORS, FLOORS[1:]):
                rise = Y - y
                first = 4 if rise == 7 else 6
                stair = {'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'}
                for h in range(1, first + 1):
                    for u in (0, 1):
                        self.put(x + u, y + h, z + h, 'lab_stair', dict(stair, facing='south'))
                    guard(x - 1, y + h + 1, z + h)
                self.box(x, y + first, z + first + 1, x + 6, y + first + 1, z + first + 2, 'lab_tile_dark')
                for i in range(rise - first):
                    for u in (4, 5):
                        self.put(x + u, y + first + 1 + i, z + first - i, 'lab_stair', dict(stair, facing='north'))
                    guard(x + 6, y + first + 2 + i, z + first - i)
                for u in range(6):
                    guard(x + u, y + first + 1, z + first + 2)
                # The centre guard separates flights without consuming either
                # two-block run.  It ends before the turning platform.
                for v in range(1, first + 1):
                    for u in (2, 3):
                        self.put(x + u, y + v + 1, z + v, 'minecraft:iron_bars')
                self.stairs.append({'core': name, 'x': x, 'y': y + 28, 'z': z,
                                    'width': 6, 'height': rise + 3, 'length': 8,
                                    'lower_floor': lower, 'upper_floor': upper, 'rise': rise,
                                    'lower_landing': xyz(x, y, z),
                                    'upper_landing': xyz(x + 4, Y, z + 1)})
        # One aligned cargo transfer platform per storey. Glass walls prevent
        # falling into the shaft, and east-facing openings lead to the gallery.
        self.clear(73, -25, 37, 79, 18, 43)
        for level, y in FLOORS:
            self.box(73, y, 37, 79, y + 1, 43, 'lab_tile_dark')
            self.put(75, y, 39, 'lab_lift')
            self.lift['landings'].append({'floor': level, 'x': 75, 'y': y + 28, 'z': 39})
            for h in (1, 2, 3):
                for X in range(72, 80):
                    for Z in (36, 43):
                        self.put(X, y + h, Z, 'minecraft:glass')
                for Z in range(37, 43):
                    for X in (72, 79):
                        if X == 79 and Z in (39, 40):
                            self.put(X, y + h, Z)
                        else:
                            self.put(X, y + h, Z, 'minecraft:glass')
            self.box(78, y, 39, 81, y + 1, 41, 'lab_tile_dark')
            self.clear(79, y + 1, 39, 80, y + 3, 41)
            self.put(75, y + 4, 39, 'lab_light_panel', {'facing': 'north'})

    def exterior(self):
        # Explicit air above paths clears foliage without excavating outdoor
        # terrain below the pad.  All paths are within the 120x104 site.
        for x, z, X, Z in ((53, 88, 67, 104), (108, 45, 120, 51)):
            self.box(x, 1, z, X, 7, Z)
            self.box(x, -MAX_TERRAIN_RELIEF, z, X, 0, Z, 'minecraft:polished_deepslate')
            self.box(x, 0, z, X, 1, Z, 'minecraft:polished_andesite')
        for x in (53, 66):
            for z in range(88, 104):
                self.put(x, 0, z, 'lab_tile_dark')
        # Eight half-step pairs grade the two road ends through the accepted
        # terrain-height range. Each tread also has an open side exit, so a
        # higher surrounding surface can be reached before the final tread.
        for i in range(MAX_TERRAIN_RELIEF):
            y, z = -i, 96 + i
            self.box(53, y + 1, z, 67, 7, z + 1)
            for x in range(53, 67):
                self.put(x, y, z, 'lab_stair', {'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
            x = 112 + i
            self.box(x, y + 1, 45, x + 1, 7, 51)
            for z in range(45, 51):
                self.put(x, y, z, 'lab_stair', {'facing': 'west', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
        # Main doors align with A7's two south doors; loading entrance aligns A5.
        for x in (59, 60):
            self.fixture(x, 1, 87, 'lab_door', 'south')
        for z in (47, 48):
            self.fixture(111, 1, z, 'lab_door', 'east')
        self.box(56, 4, 87, 64, 5, 93, 'lab_tile_dark')
        for x in (56, 63):
            self.box(x, 1, 91, x + 1, 4, 92, 'lab_tile')
        for x in (57, 62):
            self.put(x, 3, 90, 'lab_light_panel', {'facing': 'north'})
        for x in (51, 68):
            for z in (91, 99):
                self.box(x, -MAX_TERRAIN_RELIEF, z, x + 1, 0, z + 1, 'minecraft:polished_deepslate')
                self.put(x, 0, z, 'minecraft:polished_andesite')
                self.box(x, 1, z, x + 1, 4, z + 1, 'minecraft:iron_block')
                self.put(x, 4, z, 'minecraft:sea_lantern')
        # Roof plant and skylights follow the room envelopes, with the central
        # raised glass atrium kept clear. No new blocks or runtime API needed.
        for x, z in ((18, 16), (29, 16), (91, 16), (102, 16)):
            self.box(x, 22, z, x + 6, 24, z + 9, 'minecraft:gray_concrete')
            for u in (1, 4):
                self.fixture(x + u, 24, z + 3, 'lab_vent', 'north')
                self.put(x + u, 24, z + 6, 'lab_pipe', {'facing': 'east'})
            self.fixture(x + 2, 22, z - 1, 'lab_vent', 'north')
        for x, X in ((15, 33), (87, 105)):
            for y in (20, 21):
                self.box(x, y, 67, X, y + 1, 81, 'minecraft:glass')
            for X1 in range(x, X, 5):
                self.box(X1, 21, 67, X1 + 1, 22, 81, 'lab_tile_dark')
        self.box(58, 22, 18, 63, 23, 23, 'minecraft:gray_concrete')
        for y in range(23, 26):
            self.put(60, y, 20, 'minecraft:iron_bars')
        self.put(60, 26, 20, 'minecraft:sea_lantern')
        # Collapsed eighth B3 bay remains a deliberate non-room, keeping the
        # published 48-room count while giving the underground floor a history.
        for x in range(13, 35):
            for z in range(13, 31):
                if (x * 7 + z * 11) % 17 < 3:
                    self.put(x, -24, z, 'minecraft:polished_deepslate')
                if (x * 3 + z * 5) % 41 == 0:
                    self.put(x, -24, z, 'minecraft:amethyst_block')
                    self.put(x, -25, z, 'minecraft:sea_lantern')
        # Retain the recessed floor lanterns from the room design. Opaque
        # amethyst blocks shield them, so exposed-face lighting is added after
        # all furnishings and supply containers have their final positions.
        crystals = [p for p, value in self.grid.items() if value[0] in
                    ('minecraft:amethyst_block', 'minecraft:budding_amethyst')]
        for x, y, z in crystals:
            if y > -28 and self.grid.get((x, y - 1, z), AIR)[0] not in (
                    'minecraft:amethyst_block', 'minecraft:budding_amethyst'):
                self.put(x, y - 1, z, 'minecraft:sea_lantern')

    def add_loot(self):
        for r in self.rooms:
            x, y, z = r['x'], r['y'] - 28, r['z']
            w, d = r['width'], r['length']
            avoid = set()
            avoid.update((a - x, c - z) for a, b, c in getattr(self, 'architecture_approaches', ()) if b == y + 1)
            for door in r['doors']:
                u, v = door['x'] - x, door['z'] - z
                du, dv = (1, 0) if u == 0 else (-1, 0) if u == w - 1 else (0, 1) if v == 0 else (0, -1)
                avoid.update((u + du * k, v + dv * k) for k in (1, 2))
            occupied = {(u, v) for u in range(1, w - 1) for v in range(1, d - 1)
                        if any(self.grid.get((x + u, y + h, z + v), AIR)[0] != 'minecraft:air' for h in (1, 2))}
            free = {(u, v) for u in range(1, w - 1) for v in range(1, d - 1)
                    if (u, v) not in occupied and self.grid.get((x + u, y, z + v), AIR)[0] != 'minecraft:air'}
            starts = avoid & free

            def flood(excluded=None):
                reached = starts - ({excluded} if excluded else set())
                todo = deque(reached)
                while todo:
                    U, V = todo.popleft()
                    for at in ((U + 1, V), (U - 1, V), (U, V + 1), (U, V - 1)):
                        if at in free and at != excluded and at not in reached:
                            reached.add(at)
                            todo.append(at)
                return reached

            reachable = flood()
            candidates = []
            for v in range(1, d - 1):
                for u in range(1, w - 1):
                    if (u, v) not in reachable or (u, v) in avoid:
                        continue
                    if u in (w // 2 - 1, w // 2) or v in (d // 2 - 1, d // 2):
                        continue
                    if r['id'] == 'F8' and any(a <= x + u < A and b <= z + v < B for a, b, A, B in
                            [c['bounds_xz'] for c in spec.LAYOUT['circulation_cores']]):
                        continue
                    adjacent = [(u + du, v + dv) for du, dv in ((0, -1), (1, 0), (0, 1), (-1, 0))
                                if (u + du, v + dv) in reachable]
                    if not adjacent:
                        continue
                    candidates.append((min(u, v, w - 1 - u, d - 1 - v), v, u, adjacent))
            assert candidates, ('no loot position', r['id'])
            _, v, u, adjacent = min(candidates)
            final_reachable = flood((u, v))
            adjacent = [at for at in adjacent if at in final_reachable]
            assert adjacent, ('supply blocks its own access', r['id'], u, v)
            au, av = adjacent[0]
            face = {(0, -1): 'north', (1, 0): 'east', (0, 1): 'south', (-1, 0): 'west'}[au - u, av - v]
            table = 'ender_infection:chests/central_institute/' + r['category']
            self.put(x + u, y + 1, z + v, 'minecraft:barrel', {'facing': face, 'open': 'false'}, table)
            self.loot.append({'room': r['id'], 'floor': r['floor'], 'x': x + u, 'y': y + 29,
                              'z': z + v, 'block': 'minecraft:barrel', 'loot_table': table})

    def connect_bars(self):
        for (x, y, z), value in list(self.grid.items()):
            if value[0] != 'minecraft:iron_bars':
                continue
            props = {'waterlogged': 'false'}
            for face, (dx, dz) in spec.FACES.items():
                name = self.grid.get((x + dx, y, z + dz), AIR)[0]
                props[face] = str(name != 'minecraft:air' and name not in
                                  ('ender_infection:lab_light_panel', 'minecraft:potted_fern')).lower()
            self.put(x, y, z, 'minecraft:iron_bars', props)

    def add_room_signs(self):
        """One real, readable wall sign per room, mounted beside its entry."""
        for r in self.rooms:
            y = r['y'] - 28 + 3
            candidates = []
            for door in r['doors']:
                x, z = door['x'], door['z']
                if x in (r['x'], r['x'] + r['width'] - 1):
                    dx = 1 if x == r['x'] else -1
                    for offset in (-3, 3, -4, 4, -5, 5):
                        candidates.append(((x + dx, y, z + offset), (x, y, z + offset), 'east' if dx == 1 else 'west'))
                else:
                    dz = 1 if z == r['z'] else -1
                    for offset in (-3, 3, -4, 4, -5, 5):
                        candidates.append(((x + offset, y, z + dz), (x + offset, y, z), 'south' if dz == 1 else 'north'))
            chosen = next(((p, face) for p, support, face in candidates
                           if r['x'] < p[0] < r['x'] + r['width'] - 1 and r['z'] < p[2] < r['z'] + r['length'] - 1
                           and self.grid.get(p, VOID)[0] == 'minecraft:air'
                           and self.grid.get(support, AIR)[0] not in ('minecraft:air', 'ender_infection:lab_fixture_part')
                           and self.grid.get(support, AIR)[0].split(':')[1] not in spec.FIXTURES), None)
            assert chosen, ('room has no clear wall sign location', r['id'])
            p, face = chosen
            lines = [r['id'] + ' ' + r['name'], r['floor'] + ' / 中央研究所', '设备样本请归位', '保持通道畅通']
            nbt = {'id': (8, 'minecraft:sign'), 'front_text': (10, {
                    'messages': (9, (8, [json.dumps({'text': text}, ensure_ascii=False) for text in lines])),
                    'color': (8, 'black'), 'has_glowing_text': (1, 0)}), 'is_waxed': (1, 1)}
            self.put(*p, 'minecraft:birch_wall_sign', {'facing': face, 'waterlogged': 'false'}, nbt)
            self.signs.append(dict(zip(('x','y','z'), xyz(*p)), room=r['id'], block='minecraft:birch_wall_sign', text=lines))

    def assign_storage_loot(self):
        """All placed storage roots own data, including the shared reception."""
        for p, value in list(self.grid.items()):
            block = value[0]
            if not block.startswith(spec.NS):
                continue
            base = block.split(':')[1].replace('_eroded', '')
            if base not in spec.STORAGE_FIXTURES | spec.STORAGE_DETAILS:
                continue
            x, y, z = p
            room = next((r for r in self.rooms if r['x'] <= x < r['x'] + r['width']
                         and r['z'] <= z < r['z'] + r['length'] and r['y'] - 28 < y < r['y'] - 28 + r['height']), None)
            floor = room['floor'] if room else max((f for f in FLOORS if f[1] < y), key=lambda f: f[1])[0]
            category = room['category'] if room else 'public'
            table = 'ender_infection:chests/central_institute/furniture/' + category
            entity_id = 'ender_infection:lab_fixture' if base in spec.STORAGE_FIXTURES else 'ender_infection:lab_storage'
            self.grid[p] = state(block, dict(value[1]), table)
            self.storage.append(dict(zip(('x','y','z'), xyz(*p)), room=room['id'] if room else 'COMMON_' + floor,
                    floor=floor, category=category, block=block, block_entity_id=entity_id, loot_table=table))

    def configure_operations(self):
        """Bind only generated equipment; player-placed items remain unbound."""
        def zone(y):
            return 'containment' if y < -7 else 'service'

        def bound(p, entity_id):
            pos=xyz(*p)
            return {'id': (8,entity_id), 'InstituteBound': (1,1),
                    **{'InstituteOffset'+axis:(3,value) for axis,value in zip(('X','Y','Z'),pos)}}

        # Keep every existing supply neighbour: a visually open cell can still
        # be a barrel's only usable side. Candidate placement is also evaluated
        # after removal from the room's door-connected standing-space graph.
        supply_neighbours={(b['x']+dx,b['y']-28,b['z']+dz) for b in self.loot
                           for dx,dz in spec.FACES.values()}
        control_approaches=set()
        openable_cells={p for fixture in self.fixtures
                        if fixture['block'].split(':')[1].replace('_eroded','') in ('lab_door','lab_privacy_curtain')
                        for p in fixture['cells']}
        targets=[('D2','generator','lab_power_generator'),('D1','distribution','lab_distribution_panel'),
                 ('A7','terminal','lab_research_terminal'),('F5','archive','lab_archive_terminal'),
                 ('E8','incident','lab_research_terminal'),('E8','incident_cooling','lab_cooling_pump'),
                 ('E8','incident_sensor','lab_monitor'),('E8','incident_valve','lab_pipe'),
                 ('F8','boss_start','lab_research_terminal'),('F8','boss_console_a','lab_distribution_panel'),
                 ('F8','boss_console_b','lab_distribution_panel')]
        for room_id,role,key in targets:
            r=next(r for r in self.rooms if r['id']==room_id);y=r['y']-27
            protected=set()
            for door in r['doors']:
                dx,dz=spec.FACES[door['facing']]
                for step in (-2,-1,0,1,2):protected.add((door['x']+dx*step,door['z']+dz*step))
            protected.update((x,z) for x,Y,z in supply_neighbours|control_approaches if Y==y)
            def free(x,z):
                return self.grid.get((x,y,z),VOID)[0]=='minecraft:air' and self.grid.get((x,y+1,z),VOID)[0]=='minecraft:air'
            walkable={(x,z) for z in range(r['z'],r['z']+r['length']) for x in range(r['x'],r['x']+r['width'])
                      if all(self.grid.get((x,Y,z),VOID)[0] in ('minecraft:air','minecraft:light') or (x,Y,z) in openable_cells for Y in (y,y+1))}
            starts={(d['x'],d['z']) for d in r['doors']} & walkable
            def flood(excluded=None):
                reached=starts-{excluded};todo=deque(reached)
                while todo:
                    x,z=todo.popleft()
                    for dx,dz in spec.FACES.values():
                        at=x+dx,z+dz
                        if at!=excluded and at in walkable and at not in reached:reached.add(at);todo.append(at)
                return reached
            connected=flood()
            candidates=[]
            for z in range(r['z']+2,r['z']+r['length']-2):
                for x in range(r['x']+2,r['x']+r['width']-2):
                    if (x,z) in protected or (x,z) not in connected or not free(x,z):continue
                    if x in (r['x']+r['width']//2-1,r['x']+r['width']//2) or z in (r['z']+r['length']//2-1,r['z']+r['length']//2):continue
                    neighbours=[(face,dx,dz) for face,(dx,dz) in spec.FACES.items() if free(x+dx,z+dz)]
                    if len(neighbours)<3:continue
                    # Put operating side toward the room centre.
                    face,dx,dz=min(neighbours,key=lambda v:abs(x+v[1]-(r['x']+r['width']/2))+abs(z+v[2]-(r['z']+r['length']/2)))
                    wall_dist=min(x-r['x'],z-r['z'],r['x']+r['width']-1-x,r['z']+r['length']-1-z)
                    candidates.append((wall_dist,z,x,face))
            assert candidates,('no safe operations control location',room_id)
            chosen=None
            preferred={'boss_start':(r['x']+3,r['z']+r['length']//2),
                       'boss_console_a':(r['x']+r['width']-4,r['z']+3),
                       'boss_console_b':(r['x']+4,r['z']+r['length']-4)}.get(role)
            ordered=sorted(candidates,key=lambda c:abs(c[2]-preferred[0])+abs(c[1]-preferred[1])) if preferred else sorted(candidates)
            for _,z,x,_ in ordered:
                reached=flood((x,z))
                if any(not any((b['x']+dx,b['z']+dz) in reached for dx,dz in spec.FACES.values())
                       for b in self.loot if b['room']==room_id):continue
                neighbours=[(face,dx,dz) for face,(dx,dz) in spec.FACES.items() if (x+dx,z+dz) in reached and free(x+dx,z+dz)]
                if not neighbours:continue
                face,dx,dz=min(neighbours,key=lambda v:abs(x+v[1]-(r['x']+r['width']/2))+abs(z+v[2]-(r['z']+r['length']/2)))
                chosen=(x,z,face,dx,dz);break
            assert chosen,('control would disconnect supply access',room_id)
            x,z,face,dx,dz=chosen;p=(x,y,z);approach=(x+dx,y,z+dz)
            self.put(*p,key,{'facing':face,'powered':'false'},
                     bound(p,'ender_infection:lab_operations_control') if not role.startswith('incident_') else None)
            control_approaches.add(approach)
            row={'role':role,'pos':xyz(*p),'approach_pos':xyz(*approach),'room':room_id,'zone':zone(y),'block':spec.full(key)}
            if role=='boss_start':
                row['role']='terminal'
                self.bosses['F8']={'room':'F8','bounds_min':[r['x'],r['y']+1,r['z']],
                                  'bounds_max':[r['x']+r['width'],r['y']+r['height'],r['z']+r['length']],
                                  'control':row,'consoles':[]}
            elif role.startswith('boss_console_'):
                row['role']='distribution';self.bosses['F8']['consoles'].append(row)
            elif role=='incident':
                self.incidents['E8']={'room':'E8','bounds_min':[r['x'],r['y']+1,r['z']],
                                     'bounds_max':[r['x']+r['width'],r['y']+r['height'],r['z']+r['length']],
                                     'control':row,'faults':[],'spawn_points':[],'alarm_lights':[]}
            elif role.startswith('incident_'):
                row['role']=role.removeprefix('incident_');self.incidents['E8']['faults'].append(row)
            else:self.operations['controls'].append(row)

        for p,value in list(self.grid.items()):
            block,props,_=value;props=dict(props);base=block.split(':')[1].replace('_eroded','')
            if base in ('lab_light_panel','lab_task_lamp'):
                props['powered']='false';self.put(*p,block,props)
                self.operations['managed_lights'].append({'pos':xyz(*p),'zone':zone(p[1]),'block':block})
            elif base=='lab_door' and zone(p[1])=='containment':
                props.update(secured='true',open='false')
                self.put(*p,block,props,bound(p,'ender_infection:lab_fixture'))
                self.operations['secured_doors'].append({'pos':xyz(*p),'zone':'containment','block':block})
                for r in self.rooms:
                    for d in r['doors']:
                        if [d['x'],d['y'],d['z']]==xyz(*p):d['secured']=True
            elif base in ('lab_microscope','lab_centrifuge'):
                self.put(*p,block,props,bound(p,'ender_infection:lab_processing'))
                self.operations['machines'].append({'pos':xyz(*p),'device':block,'zone':zone(p[1])})

        r=next(r for r in self.rooms if r['id']=='E8'); y=r['y']-27; incident=self.incidents['E8']
        candidates=[]
        for z in range(r['z']+3,r['z']+r['length']-3):
            for x in range(r['x']+3,r['x']+r['width']-3):
                if all(self.grid.get((X,Y,Z),VOID)[0] in ('minecraft:air','minecraft:light')
                       for X in range(x-1,x+2) for Z in range(z-1,z+2) for Y in range(y,y+3)):
                    candidates.append((abs(x-r['x']-r['width']/2)+abs(z-r['z']-r['length']/2),x,z))
        chosen=[]
        for _,x,z in sorted(candidates):
            if all((x-X)**2+(z-Z)**2>=16 for X,Z in chosen):chosen.append((x,z))
            if len(chosen)==3:break
        assert len(chosen)==3,('E8 needs three clear spawn pads',chosen)
        incident['spawn_points']=[xyz(x,y,z) for x,z in chosen]
        for x,z in [(r['x']+3,r['z']+3),(r['x']+r['width']-4,r['z']+r['length']-4)]:
            p=x,y+4,z;self.put(*p,'minecraft:redstone_lamp',{'lit':'false'});incident['alarm_lights'].append(xyz(*p))

        # Keep the reactor and furniture; select a five-wide, four-high clear pad.
        r=next(r for r in self.rooms if r['id']=='F8');y=r['y']-27;candidates=[]
        for z in range(r['z']+3,r['z']+r['length']-3):
            for x in range(r['x']+3,r['x']+r['width']-3):
                if all(self.grid.get((X,Y,Z),VOID)[0] in ('minecraft:air','minecraft:light')
                       for X in range(x-2,x+3) for Z in range(z-2,z+3) for Y in range(y,y+4)):
                    candidates.append((abs(x-r['x']-r['width']*.65)+abs(z-r['z']-r['length']*.5),x,z))
        assert candidates,'F8 needs a five-wide, four-high boss pad'
        _,x,z=min(candidates);self.bosses['F8']['spawn_pos']=xyz(x,y,z)

    def lighting(self):
        sources = []
        for (x, y, z), value in self.grid.items():
            strength = int(dict(value[1]).get('level', 0)) if value[0] == 'minecraft:light' else 15 if value[0] == 'minecraft:sea_lantern' or (value[0] == 'minecraft:redstone_lamp' and dict(value[1]).get('lit') == 'true') else 14 if value[0] in ('ender_infection:lab_light_panel','ender_infection:lab_task_lamp') and dict(value[1]).get('powered','true')=='true' else 0
            if strength:
                sources.append({'x': x, 'y': y + 28, 'z': z, 'block': value[0], 'light_level': strength})
        actual_crystals = sum('amethyst' in value[0] or 'crystal' in value[0] for value in self.grid.values())
        assert len(self.crystal_faces) == actual_crystals, ('missing crystal face lighting', len(self.crystal_faces), actual_crystals)
        for c in self.crystal_faces:
            probe = c['probe'][0], c['probe'][1] - 28, c['probe'][2]
            source = c['source'][0], c['source'][1] - 28, c['source'][2]
            assert self.grid[probe][0] == 'minecraft:air', ('crystal probe obstructed', c)
            assert self.grid[source][0] == 'minecraft:light' and dict(self.grid[source][1])['level'] == '15', ('crystal source missing', c)
            assert sum(abs(source[i] - probe[i]) for i in range(3)) == 1, ('crystal source cannot directly illuminate face', c)
        return sources, self.crystal_faces

    def add_crystal_face_lights(self):
        """Illuminate an exposed face, rather than burying light under opacity.

        Keep every existing AIR neighbour of every crystal intact. In particular
        no invisible light block can consume a budding-amethyst growth cell.
        Five compact display cases need a right-angle path through their interior
        air; both steps still transmit light directly without crossing glass or
        any opaque crystal.
        """
        directions = [('up', (0, 1, 0)), ('north', (0, 0, -1)), ('east', (1, 0, 0)),
                      ('south', (0, 0, 1)), ('west', (-1, 0, 0)), ('down', (0, -1, 0))]

        def add(p, d):
            return tuple(p[i] + d[i] for i in range(3))

        crystals = [(p, value[0]) for p, value in self.grid.items()
                    if 'amethyst' in value[0] or 'crystal' in value[0]]
        protected_air = {add(p, d) for p, _ in crystals for _, d in directions
                         if self.grid.get(add(p, d), VOID)[0] == 'minecraft:air'}
        door_approaches = set()
        for (x, y, z), value in self.grid.items():
            if value[0].split(':')[1].replace('_eroded', '') != 'lab_door':
                continue
            dx, dz = spec.FACES[dict(value[1])['facing']]
            for step in (-2, -1, 1, 2):
                for h in (0, 1):
                    door_approaches.add((x + dx * step, y + h, z + dz * step))

        def usable(p):
            return (p not in protected_air and p not in door_approaches
                    and self.grid.get(p, VOID)[0] in ('minecraft:air', 'minecraft:light'))

        for p, block in crystals:
            choices = []
            for rank, (face, d) in enumerate(directions):
                probe = add(p, d)
                source = add(probe, d)
                if probe in protected_air and usable(source):
                    choices.append((rank, face, probe, source, 'straight'))
            if not choices:
                for rank, (face, d) in enumerate(directions):
                    probe = add(p, d)
                    if probe not in protected_air:
                        continue
                    for _, turn in directions:
                        if sum(d[i] * turn[i] for i in range(3)) != 0:
                            continue
                        source = add(probe, turn)
                        if usable(source):
                            choices.append((rank, face, probe, source, 'right_angle'))
            assert choices, ('no unobstructed crystal face light route', p, block)
            _, face, probe, source, route = min(choices,
                    key=lambda c: (c[0], self.grid.get(c[3], VOID)[0] != 'minecraft:light', c[3]))
            self.put(*source, 'minecraft:light', {'level': '15', 'waterlogged': 'false'})
            normalized_probe, normalized_source = xyz(*probe), xyz(*source)
            self.crystal_faces.append(dict(zip(('x', 'y', 'z'), xyz(*p)), block=block,
                    source=normalized_source, probe=normalized_probe, exposed_face=face,
                    path=[normalized_probe, normalized_source], path_type=route, distance=2))
        assert all(self.grid[p][0] == 'minecraft:air' for p in protected_air), 'Crystal growth AIR was replaced'

    def bake_powered_lamps(self):
        # Placement intentionally suppresses neighbour updates. The actual
        # redstone power state must therefore be part of the saved template.
        for (x, y, z), value in list(self.grid.items()):
            if value[0] != 'minecraft:redstone_lamp':
                continue
            powered = any(self.grid.get((x + dx, y + dy, z + dz), AIR)[0] == 'minecraft:redstone_block'
                          for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                             (0, -1, 0), (0, 0, 1), (0, 0, -1)))
            self.put(x, y, z, 'minecraft:redstone_lamp', {'lit': str(powered).lower()})

    def validate(self):
        assert len(self.rooms) == len({r['id'] for r in self.rooms}) == 48
        assert len(self.loot) == 48
        assert len(self.signs) == 48
        for storage in self.storage:
            at = storage['x'], storage['y'] - 28, storage['z']
            assert self.grid[at][0] == storage['block'] and self.grid[at][2] == storage['loot_table'], ('storage data lost', storage)
        active_fixtures = 0
        for f in self.fixtures:
            if self.grid.get(f['anchor'], AIR)[0] != f['block']:
                continue
            active_fixtures += 1
            for p in f['cells']:
                if p == f['anchor']:
                    continue
                assert self.grid.get(p, AIR)[0] == 'ender_infection:lab_fixture_part', ('lost part', f, p)
        door_checks = 0
        for r in self.rooms:
            for d in r['doors']:
                x, y, z = d['x'], d['y'] - 28, d['z']
                assert self.grid[x, y, z][0] == 'ender_infection:lab_door', ('lost door', r['id'], d)
                dx, dz = (1, 0) if x == r['x'] else (-1, 0) if x == r['x'] + r['width'] - 1 else (0, 1) if z == r['z'] else (0, -1)
                for step in (1, 2):
                    for h in (0, 1):
                        assert self.grid.get((x + dx * step, y + h, z + dz * step), AIR)[0] == 'minecraft:air', ('door approach', r['id'], d, step, h)
                door_checks += 1
        for landing in self.lift['landings']:
            x, y, z = landing['x'], landing['y'] - 28, landing['z']
            assert self.grid[x, y, z][0] == 'ender_infection:lab_lift'
            assert all(self.grid.get((x, y + h, z), AIR)[0] == 'minecraft:air' for h in (1, 2))
        for i in range(MAX_TERRAIN_RELIEF):
            y = -i
            for positions, facing in (([(x, 96 + i) for x in range(53, 67)], 'north'),
                                      ([(112 + i, z) for z in range(45, 51)], 'west')):
                for x, z in positions:
                    stair = self.grid[x, y, z]
                    assert stair[0] == 'ender_infection:lab_stair' and dict(stair[1])['facing'] == facing, ('missing terrain access stair', x, y, z)
                    assert all(self.grid[x, h, z][0] == 'minecraft:air' for h in range(y + 1, 7)), ('terrain access headroom', x, y, z)
                    assert all(self.grid[x, h, z][0] == 'minecraft:polished_deepslate' for h in range(-MAX_TERRAIN_RELIEF, y)), ('terrain access footing', x, y, z)
        for x in (51, 68):
            for z in (91, 99):
                assert all(self.grid[x, h, z][0] == 'minecraft:polished_deepslate' for h in range(-MAX_TERRAIN_RELIEF, 0)), ('lamp footing', x, z)
        facade_windows = {}
        for level, y in FLOORS:
            if y < 0:
                continue
            faces = {}
            for face, positions in {
                    'north': ((x, z) for x in range(8, 112) for z in (8,)),
                    'south': ((x, z) for x in range(8, 112) for z in (87,)),
                    'west': ((x, z) for x in (8,) for z in range(8, 88)),
                    'east': ((x, z) for x in (111,) for z in range(8, 88))}.items():
                faces[face] = sum(self.grid.get((x, y + h, z), AIR)[0] == 'minecraft:light_gray_stained_glass'
                                  for x, z in positions for h in (2, 3, 4))
                assert faces[face] > 0, ('missing facade window band', level, face)
            for x in (8, 111):
                for z in (8, 87):
                    assert all(self.grid[x, y + h, z][0] == 'ender_infection:lab_tile_dark' for h in (2, 3, 4)), ('lost facade corner column', level, x, z)
            facade_windows[level] = faces
        counts = Counter(value[0] for value in self.grid.values())
        beds = Counter(value[1] for value in self.grid.values() if value[0].endswith('_bed'))
        return {'status': 'PASS', 'layout_version': LAYOUT_VERSION, 'rooms': 48,
                'max_terrain_relief': MAX_TERRAIN_RELIEF,
                'door_leaves_with_two_deep_clear_approaches': door_checks,
                'active_fixture_roots': active_fixtures, 'fixture_parts': counts['ender_infection:lab_fixture_part'],
                'bed_blocks': sum(beds.values()), 'loot_containers': len(self.loot),
                'functional_storage_roots': len(self.storage), 'room_signs': len(self.signs),
                'stair_flights': len(self.stairs), 'lift_landings': len(self.lift['landings']),
                'explicit_air_blocks': counts['minecraft:air'], 'voxel_count': len(self.grid),
                'facade_window_blocks': facade_windows,
                'block_counts': dict(sorted(counts.items())),
                'limits': 'Static voxel checks; native placement, collision walking, rendering and natural terrain require the dual-version runtime checks.'}


def utf(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def payload(kind, value):
    if kind == 11:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>i', n) for n in value)
    if kind == 1:
        return struct.pack('>b', value)
    if kind == 3:
        return struct.pack('>i', value)
    if kind == 8:
        return utf(value)
    if kind == 9:
        subtype, values = value
        return bytes([subtype]) + struct.pack('>i', len(values)) + b''.join(payload(subtype, v) for v in values)
    if kind == 10:
        return b''.join(bytes([t]) + utf(k) + payload(t, v) for k, (t, v) in value.items()) + b'\0'
    raise ValueError(kind)


def write_tile(model, cx, cz):
    ox, oz = cx * 16, cz * 16
    width, length = min(16, WIDTH - ox), min(16, LENGTH - oz)
    palette, indices, blocks = [], {}, []
    for y in range(-28, 27):
        for z in range(oz, oz + length):
            for x in range(ox, ox + width):
                name, props, loot = model.grid.get((x, y, z), VOID)
                key = name, props
                if key not in indices:
                    indices[key] = len(palette)
                    entry = {'Name': (8, name)}
                    if props:
                        entry['Properties'] = (10, {k: (8, v) for k, v in props})
                    palette.append(entry)
                block = {'pos': (9, (3, [x - ox, y + 28, z - oz])), 'state': (3, indices[key])}
                if isinstance(loot, dict):
                    block['nbt'] = (10, loot)
                elif loot:
                    base = name.split(':')[1].replace('_eroded', '')
                    entity_id = 'ender_infection:lab_fixture' if base in spec.STORAGE_FIXTURES else 'ender_infection:lab_storage' if base in spec.STORAGE_DETAILS else 'minecraft:barrel'
                    block['nbt'] = (10, {'id': (8, entity_id), 'LootTable': (8, loot)})
                if 'nbt' in block:
                    tag = dict(block['nbt'][1])
                    marker = (10, {'InstituteOriginOffset': (11, [x, y + 28, z])})
                    tag['NeoForgeData'] = marker
                    tag['ForgeData'] = marker
                    block['nbt'] = (10, tag)
                blocks.append(block)
    root = {'DataVersion': (3, 3465), 'size': (9, (3, [width, HEIGHT, length])),
            'palette': (9, (10, palette)), 'blocks': (9, (10, blocks)), 'entities': (9, (10, []))}
    raw = gzip.compress(bytes([10]) + utf('') + payload(10, root), mtime=0)
    path = DEST / f'tile_{cx}_{cz}.nbt'
    path.write_bytes(raw)
    return {'template': f'ender_infection:central_institute/tile_{cx}_{cz}',
            'x': ox, 'y': 0, 'z': oz, 'width': width, 'height': HEIGHT, 'length': length,
            'blocks': len(blocks), 'sha256': sha256(raw).hexdigest()}


def loot_tables():
    categories = {
        'public': [('minecraft:bread', 8, 2, 5), ('minecraft:paper', 10, 3, 9), ('minecraft:torch', 8, 4, 12)],
        'security': [('minecraft:arrow', 10, 6, 18), ('minecraft:iron_ingot', 7, 1, 4), ('minecraft:shield', 2, 1, 1)],
        'medical': [('minecraft:golden_carrot', 7, 1, 3), ('minecraft:honey_bottle', 9, 1, 3), ('minecraft:glass_bottle', 10, 2, 6), ('ender_infection:raw_tissue_sample',10,1,3)],
        'research': [('minecraft:amethyst_shard', 9, 2, 7), ('minecraft:glass_bottle', 10, 2, 5), ('minecraft:redstone', 8, 3, 8), ('ender_infection:raw_tissue_sample',10,1,3), ('ender_infection:research_record',6,1,2)],
        'engineering': [('minecraft:iron_ingot', 10, 2, 6), ('minecraft:copper_ingot', 10, 3, 8), ('minecraft:redstone', 10, 4, 12)],
        'containment': [('minecraft:amethyst_shard', 10, 3, 8), ('minecraft:obsidian', 5, 1, 3), ('minecraft:ender_pearl', 2, 1, 2), ('ender_infection:raw_tissue_sample',10,1,3)],
        'archives': [('minecraft:paper', 10, 5, 16), ('minecraft:book', 9, 1, 4), ('minecraft:ink_sac', 6, 1, 4)],
        'greenhouse': [('minecraft:wheat_seeds', 10, 3, 10), ('minecraft:bone_meal', 10, 3, 8), ('minecraft:glow_berries', 6, 1, 5)],
    }
    dest = DATA / 'loot_tables/chests/central_institute'
    dest.mkdir(parents=True, exist_ok=True)
    for category, items in categories.items():
        entries = [{'type': 'minecraft:item', 'name': name, 'weight': weight,
                    'functions': [{'function': 'minecraft:set_count',
                                   'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}
                   for name, weight, lo, hi in items]
        table = {'type': 'minecraft:chest', 'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 5},
                                                   'entries': entries}]}
        if category=='public':
            # Reception supplies guarantee a first repair/research attempt;
            # neither a lucky furniture roll nor a locked room is required.
            for name,count in [('lab_fuse',2),('raw_tissue_sample',2),('research_log',1)]:
                table['pools'].append({'rolls':1,'entries':[{'type':'minecraft:item','name':'ender_infection:'+name,
                     'functions':[{'function':'minecraft:set_count','count':count}]}]})
        (dest / (category + '.json')).write_text(json.dumps(table, indent=2) + '\n', encoding='utf-8')
    # Functional drawers/cabinets are modest supply locations. Rich per-room
    # expedition barrels retain the separate tables above.
    furniture = {
        'public': ['minecraft:paper','minecraft:string','minecraft:stick'],
        'security': ['minecraft:iron_nugget','minecraft:flint','minecraft:string'],
        'medical': ['minecraft:glass_bottle','minecraft:sugar','minecraft:paper','ender_infection:raw_tissue_sample'],
        'research': ['minecraft:amethyst_shard','minecraft:glass_bottle','minecraft:redstone','ender_infection:raw_tissue_sample','ender_infection:research_record'],
        'engineering': ['minecraft:iron_nugget','minecraft:copper_ingot','minecraft:redstone'],
        'containment': ['minecraft:amethyst_shard','minecraft:glass_bottle','minecraft:string','ender_infection:raw_tissue_sample'],
        'archives': ['minecraft:paper','minecraft:book','minecraft:ink_sac'],
        'greenhouse': ['minecraft:wheat_seeds','minecraft:bone_meal','minecraft:oak_sapling'],
    }
    small = dest / 'furniture'
    small.mkdir(parents=True, exist_ok=True)
    for category, items in furniture.items():
        table = {'type':'minecraft:chest','pools':[{'rolls':{'type':'minecraft:uniform','min':1,'max':2},
                 'entries':[{'type':'minecraft:item','name':name,'weight':1,
                             'functions':[{'function':'minecraft:set_count','count':{'type':'minecraft:uniform','min':1,'max':2}}]}
                            for name in items]}]}
        (small / (category + '.json')).write_text(json.dumps(table, indent=2) + '\n', encoding='utf-8')


def generate():
    model = Institute()
    model.shell()
    model.add_rooms()
    model.atrium()
    model.traffic()
    model.exterior()
    refine(model)
    model.add_loot()
    model.add_room_signs()
    model.configure_operations()
    furnish_public(model)
    mother_chamber(model)
    furnish_rooms(model)
    model.assign_storage_loot()
    model.connect_bars()
    model.bake_powered_lamps()
    model.add_crystal_face_lights()
    report = model.validate()
    sources, crystals = model.lighting()
    DEST.mkdir(parents=True, exist_ok=True)
    tiles = [write_tile(model, cx, cz) for cz in range(7) for cx in range(8)]
    protection = bytearray((WIDTH * HEIGHT * LENGTH + 7) // 8)
    for (x, y, z), value in model.grid.items():
        if value[0] not in ('minecraft:air', 'minecraft:structure_void'):
            index = ((y + 28) * LENGTH + z) * WIDTH + x
            protection[index // 8] |= 1 << (index % 8)
    manifest = {'format_version': 1, 'layout_version': LAYOUT_VERSION,
                'width': WIDTH, 'height': HEIGHT, 'length': LENGTH, 'ground_offset': GROUND_OFFSET,
                'max_terrain_relief': MAX_TERRAIN_RELIEF,
                'coordinate_system': 'site-local XYZ; G1 floor Y28; all landings are support blocks, standing feet Y+1',
                'preserve_mask': {'block': 'minecraft:structure_void', 'semantics': 'leave existing world blocks unchanged',
                                  'count': WIDTH * HEIGHT * LENGTH - len(model.grid)},
                'tiles': tiles, 'rooms': model.rooms, 'stairs': model.stairs, 'lift': model.lift,
                'entrance': model.entrance, 'loot_containers': model.loot,
                'storage_containers': model.storage, 'room_signs': model.signs,
                'light_sources': sources, 'crystal_lighting': crystals, 'operations':model.operations,
                'incidents':model.incidents,'bosses':model.bosses, 'architecture':model.architecture,
                'mother_chamber':model.mother_chamber,
                'protection': {'encoding':'zlib-base64-bitset-le','index':'(y*length+z)*width+x',
                               'mask':base64.b64encode(zlib.compress(protection)).decode('ascii')}}
    (DATA / 'central_institute.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    loot_tables()
    report['tiles'] = len(tiles)
    report['preserved_structure_void_blocks'] = WIDTH * HEIGHT * LENGTH - len(model.grid)
    report['crystals_with_real_light_sources'] = len(crystals)
    report['compressed_bytes'] = sum((DEST / (tile['template'].split('/')[-1] + '.nbt')).stat().st_size for tile in tiles)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: report[k] for k in ('status', 'rooms', 'tiles', 'voxel_count', 'explicit_air_blocks', 'compressed_bytes')}))


if __name__ == '__main__':
    generate()
