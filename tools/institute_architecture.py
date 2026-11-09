"""Architectural refinement of the fixed institute without moving gameplay rooms.

The small committed NBT prefabs are authored with minecraft-structure MCP.
Normal generation stays dependency-free and retains the existing room graph.
"""
from collections import deque
from hashlib import sha256
import json
from pathlib import Path

from nbt_codec import load
import institute_room_spec as spec

ASSETS = Path(__file__).with_name('institute_architecture')
REVISION = 'institute-architecture-v1'


def paste(model, name, origin):
    path = ASSETS / (name + '.nbt')
    nbt = load(path)
    assert nbt['DataVersion'] == 3465, 'Shared prefabs must remain 1.20.1 compatible'
    palette = nbt['palette']
    x, y, z = origin
    for entry in nbt['blocks']:
        item = palette[entry['state']]
        if item['Name'] in ('minecraft:structure_void', 'minecraft:air'):
            continue
        assert 'nbt' not in entry, 'Gameplay block entities belong in the institute generator'
        a, b, c = entry['pos']
        model.put(x + a, y + b, z + c, item['Name'], item.get('Properties', {}))
    return {'prefab': name, 'origin': [x, y + 28, z], 'size': nbt['size'],
            'sha256': sha256(path.read_bytes()).hexdigest()}


def facade(model):
    # Projecting floor brows and paired vertical fins break the long flat wall.
    # The original window plane and all room envelopes remain intact.
    slab = {'type': 'top', 'waterlogged': 'false'}
    for y in (6, 13, 20):
        for x in range(7, 113):
            for z in (6, 89):
                model.put(x, y, z, 'minecraft:smooth_quartz_slab', slab)
        for z in range(7, 89):
            for x in (6, 113):
                if x == 113 and y == 6 and 45 <= z < 51:
                    continue  # Keep every loading-road tread's full headroom.
                model.put(x, y, z, 'minecraft:smooth_quartz_slab', slab)
    pillars = [(x, z) for x in (16, 32, 40, 80, 88, 104) for z in (6, 89)]
    pillars += [(x, z) for x in (6, 113) for z in (16, 32, 64, 80)]
    for x, z in pillars:
        model.box(x, -8, z, x + 1, 0, z + 1, 'minecraft:polished_deepslate')
        model.box(x, 0, z, x + 1, 24, z + 1, 'minecraft:white_concrete')
        for y in (3, 10, 17):
            model.put(x, y, z, 'minecraft:cyan_concrete')
    # A low stepped service enclosure adds a second roof silhouette. It sits
    # clear of the existing HVAC modules, greenhouse skylights and atrium cap.
    model.box(44, 22, 10, 53, 24, 26, 'minecraft:gray_concrete')
    model.box(45, 24, 11, 52, 25, 25, 'minecraft:light_gray_concrete')
    for z in (13, 18, 23):
        model.put(44, 23, z, 'minecraft:iron_trapdoor',
                  {'facing': 'west', 'half': 'top', 'open': 'true', 'powered': 'false', 'waterlogged': 'false'})


def room_prefabs(model, room, name, targets, services):
    """Fill empty work zones while preserving existing equipment and its access."""
    results = []
    asset = load(ASSETS / (name + '.nbt'))
    palette = asset['palette']
    occupied = {(a, c) for e in asset['blocks'] for a, b, c in [e['pos']]
                if 1 <= b <= 2 and palette[e['state']]['Name'] not in ('minecraft:air', 'minecraft:structure_void')}
    solid = [e['pos'] for e in asset['blocks'] if e['pos'][1] > 0
             and palette[e['state']]['Name'] not in ('minecraft:air', 'minecraft:structure_void')]
    if targets:
        x, y, z = room['x'], room['y'] - 28, room['z']
        w, d = room['width'], room['length']
        def air(u, v):
            return all(model.grid.get((x + u, y + h, z + v), ('minecraft:air',))[0] == 'minecraft:air' for h in (1, 2))
        walk = {(u, v) for u in range(1, w - 1) for v in range(1, d - 1) if air(u, v)}
        starts = set()
        protected = set()
        protected.update((X - x, Z - z) for X, Y, Z in model.architecture_approaches if Y == y + 1)
        for door in room['doors']:
            u, v = door['x'] - x, door['z'] - z
            du, dv = (1, 0) if u == 0 else (-1, 0) if u == w - 1 else (0, 1) if v == 0 else (0, -1)
            starts.add((u + du, v + dv))
            protected.update((u + du * step, v + dv * step) for step in (1, 2, 3))
        # Preserve the opening side of every existing cabinet, sink and machine.
        for (X, Y, Z), value in model.grid.items():
            if Y != y + 1 or not (x < X < x + w - 1 and z < Z < z + d - 1):
                continue
            base = value[0].split(':')[-1].replace('_eroded', '')
            if base not in spec.STORAGE_FIXTURES | spec.STORAGE_DETAILS | {'lab_microscope', 'lab_centrifuge'}:
                continue
            dx, dz = spec.FACES[dict(value[1]).get('facing', 'north')]
            lateral = (-dz, dx)
            spread = (-1, 0, 1) if base in ('lab_workbench', 'lab_control_console') else (0,)
            for step in (1, 2):
                for side in spread:
                    point = X - x + dx * step + lateral[0] * side, Z - z + dz * step + lateral[1] * side
                    if point in walk:
                        protected.add(point)
        # Public additions run after supplies and controls have been allocated.
        for row in model.loot:
            if row['room'] == room['id']:
                protected.update((row['x'] - x + dx, row['z'] - z + dz) for dx, dz in spec.FACES.values())
        for row in model.operations['controls']:
            if row['room'] == room['id']:
                X, _, Z = row['approach_pos']; protected.add((X - x, Z - z))
        # Existing accident and Boss spawn/control positions are gameplay space.
        def reserve(value, key=''):
            if isinstance(value, dict):
                for k, v in value.items(): reserve(v, k)
            elif isinstance(value, list):
                if key in ('pos', 'approach_pos', 'spawn_pos', 'spawn_points') and len(value) == 3 and all(isinstance(n, int) for n in value):
                    X, Y, Z = value
                    if Y == y + 29:
                        radius = 2 if key.startswith('spawn') else 1
                        protected.update((X - x + a, Z - z + b) for a in range(-radius,radius+1) for b in range(-radius,radius+1))
                else:
                    for v in value: reserve(v, key)
        for event in (model.incidents.get(room['id']), model.bosses.get(room['id'])):
            if event: reserve(event)
        def flood(blocked):
            reached = starts - blocked
            todo = deque(reached)
            while todo:
                u, v = todo.popleft()
                for at in ((u + 1, v), (u - 1, v), (u, v + 1), (u, v - 1)):
                    if at in walk and at not in blocked and at not in reached:
                        reached.add(at); todo.append(at)
            return reached
        original = flood(set())
        blocked = set()
        for target_u, target_v in targets:
            candidates = sorted((abs(u - target_u) + abs(v - target_v), v, u)
                                for v in range(2, d - asset['size'][2] - 1)
                                for u in range(2, w - asset['size'][0] - 1))
            for _, v, u in candidates:
                body = {(u + a, v + c) for a, c in occupied}
                work = {(u + a, v + c) for a, c in services}
                if body & (protected | work) or not (body | work) <= original - blocked:
                    continue
                if not all(air(U, V) for U, V in work):
                    continue
                if not all(model.grid.get((x + u + a, y + b, z + v + c), ('minecraft:air',))[0] == 'minecraft:air'
                           for a, b, c in solid):
                    continue
                if original - (blocked | body) != flood(blocked | body):
                    continue
                result = paste(model, name, (x + u, y, z + v))
                result['room'] = room['id']; results.append(result)
                blocked |= body
                protected |= work | {(U + du, V + dv) for U, V in body for du, dv in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1))}
                model.architecture_approaches.update((x + U, y + 1, z + V) for U, V in work)
                break
    return results


def room_benches(model):
    results = []
    model.architecture_approaches = set()
    for room in model.rooms:
        if room['id'] not in ('B1', 'B2', 'B4', 'B5', 'B8'):
            continue
        w, d = room['width'], room['length']
        columns = (.25, .5, .75) if room['id'] == 'B2' else (.32, .68)
        targets = [(w * col - 2.5, d * row - 1.5) for row in (.4, .72) for col in columns]
        services = [(a, c) for a in range(5) for c in (-1, 0, 3, 4)]
        results += room_prefabs(model, room, 'research_bench', targets, services)
    assert len(results) >= 15, 'Research rooms need groups of fully accessible double-sided benches'
    return results


def furnish_public(model):
    """Add full seating groups in the unused half of the entrance room."""
    room = next(r for r in model.rooms if r['id'] == 'A7')
    pieces = room_prefabs(model, room, 'atrium_bench', [(22, 11), (22, 15), (12, 3), (19, 3)],
                          [(a, c) for a in range(1, 4) for c in (0, 2, 3)])
    assert len(pieces) == 4, 'The foyer should have four usable seating groups'
    model.architecture['prefabs'] += pieces
    model.architecture['public_seating_groups'] = len(pieces)
    model.architecture['protected_work_positions'] = [[x, y + 28, z] for x, y, z in sorted(model.architecture_approaches)]


def furnish_rooms(model):
    """Room-specific groups supplement the existing wall equipment."""
    plans = {
        'A1': [('terminal_bank',3)], 'A2':[('office_desk',3)], 'A3':[('medical_bay',3)],
        'A4':[('medical_bay',1),('crate_pallet',2)], 'A5':[('crate_pallet',4)],
        'A6':[('dining_table',3)], 'A8':[('supply_rack',4)],
        'B3':[('research_bench',3)], 'B6':[('office_desk',4)], 'B7':[('archive_stack',4),('office_desk',2)],
        'C1':[('terminal_bank',3)], 'C2':[('terminal_bank',5)], 'C3':[('office_desk',4)],
        'C4':[('bunk_pair',4)], 'C5':[('dining_table',4)], 'C6':[('planter_bed',4)],
        'C7':[('archive_stack',4),('office_desk',2)], 'C8':[('fungus_bed',4)],
        'D1':[('terminal_bank',3)], 'D2':[('pump_skid',4)], 'D3':[('terminal_bank',4)],
        'D4':[('pump_skid',4)], 'D5':[('pump_skid',4)], 'D6':[('supply_rack',4)],
        'D7':[('crate_pallet',4),('supply_rack',2)], 'D8':[('office_desk',2),('crate_pallet',2)],
        'E1':[('cold_bank',4)], 'E2':[('terminal_bank',4)], 'E3':[('cold_bank',4)],
        'E4':[('medical_bay',4)], 'E5':[('cold_bank',3)], 'E6':[('cold_bank',4)],
        'E7':[('crate_pallet',3)], 'E8':[('pump_skid',1)],
        'F1':[('terminal_bank',4)], 'F2':[('cold_bank',4)], 'F3':[('pump_skid',4)],
        'F4':[('pump_skid',4)], 'F5':[('archive_stack',3),('office_desk',1)],
        'F6':[('crate_pallet',3)], 'F8':[('terminal_bank',2)],
    }
    counts = {}
    for room in model.rooms:
        groups=[]
        for name, number in plans.get(room['id'], []):
            w,d=room['width'],room['length']
            targets=[(w*col-2.5,d*row-1.5) for row in (.4,.7) for col in (.3,.68)]
            if number>4:targets += [(w*.5-2.5,d*.5)]*(number-4)
            targets=targets[:number]
            if name=='office_desk':services=[(a,b) for a,b in ((0,2),(2,2),(4,2),(1,4),(3,4))]
            elif name=='medical_bay':services=[(a,b) for a in (0,4) for b in (2,3,4)]+[(2,3),(2,4)]
            elif name=='bunk_pair':services=[(a,b) for a in (0,2,4) for b in (2,3,4)]
            elif name=='dining_table':services=[(a,b) for a in (0,4) for b in (1,2,3)]+[(2,0),(2,4)]
            else:services=[(a,b) for a in range(5) for b in (0,3)]
            groups += room_prefabs(model,room,name,targets,services)
        if room['id'] in plans:
            assert groups,('No accessible furnishing group fit room',room['id'])
            counts[room['id']]=len(groups)
        model.architecture['prefabs'] += groups
    model.architecture['furnished_rooms']=counts
    model.architecture['protected_work_positions']=[[x,y+28,z] for x,y,z in sorted(model.architecture_approaches)]


def mother_chamber(model):
    room=next(r for r in model.rooms if r['id']=='F7')
    x,y,z=room['x']+9,room['y']-28,room['z']+6
    asset=load(ASSETS/'mother_chamber.nbt')
    for e in asset['blocks']:
        a,b,c=e['pos']
        if b>0:
            assert model.grid.get((x+a,y+b,z+c),('minecraft:air',))[0]=='minecraft:air',('Chamber overlaps original equipment',e['pos'])
    for a in range(1,8):
        for b in range(1,6):
            for c in range(1,6):
                assert model.grid.get((x+a,y+b,z+c),('minecraft:air',))[0]=='minecraft:air','Mother chamber interior was not empty'
    piece=paste(model,'mother_chamber',(x,y,z));piece['room']='F7'
    anchor=(x+10,y+2,z+3)
    state={'id':(8,'ender_infection:rift_anchor'),'InstituteChamber':(10,{
        'MotherOffset':(11,[-6,-1,0]),'ShellMin':(11,[-10,-2,-3]),'ShellMax':(11,[-2,4,3]),
        'MotherStage':(3,2),'Initialized':(1,0)})}
    model.put(*anchor,'ender_infection:rift_anchor',{'active':'false','suppressed':'false'},state)
    model.architecture['prefabs'].append(piece)
    model.mother_chamber={'room':'F7','anchor_pos':[anchor[0],anchor[1]+28,anchor[2]],
        'mother_pos':[x+4,y+29,z+3],'mother_stage':2,'bounds_min':[x,y+28,z],
        'bounds_max':[x+8,y+34,z+6],'spawn_policy':'once; deferred to loaded server ticks; no replacement after death'}
    for a,b in ((11,3),(10,2),(10,4)):
        model.architecture_approaches.add((x+a,y+1,z+b))


def refine(model):
    facade(model)
    # Keep both original grid-controlled lamps at their existing offsets so
    # old v5 buildings retain their service-circuit lighting after an update.
    legacy_lamps = {point: model.grid[point] for point in ((57, 3, 90), (62, 3, 90))}
    # Replace the low old canopy, including its two middle-of-path columns.
    # Door roots at z=87, y=1 and the eight graded treads at z>=96 stay intact.
    model.clear(49, 1, 88, 71, 11, 96)
    model.box(49, -8, 88, 71, 0, 96, 'minecraft:polished_deepslate')
    pieces = [paste(model, 'entry_portico', (49, 0, 87)),
              paste(model, 'atrium_halo', (50, 20, 42)),
              paste(model, 'atrium_bench', (48, 0, 48)),
              paste(model, 'atrium_bench', (67, 0, 44))]
    for (x, y, z), value in legacy_lamps.items():
        model.grid[x, y, z] = value
        model.box(x, y + 1, z, x + 1, 6, z + 1, 'minecraft:iron_bars')
    # The foyer retains the three real storage desks and gains a deliberate
    # material island rather than more inventories or unrelated equipment.
    for x in range(54, 66):
        for z in range(47, 52):
            model.put(x, 0, z, 'minecraft:polished_andesite' if x in (54, 65) or z in (47, 51) else 'lab_tile_light')
    messages = [json.dumps({'text': text}) for text in ('CENTRAL', 'RESEARCH', 'INSTITUTE', 'ENTRY / 01')]
    sign = {'id': (8, 'minecraft:sign'), 'is_waxed': (1, 1),
            'front_text': (10, {'color': (8, 'white'), 'has_glowing_text': (1, 1), 'messages': (9, (8, messages))}),
            'back_text': (10, {'color': (8, 'white'), 'has_glowing_text': (1, 0), 'messages': (9, (8, ['{"text":""}'] * 4))})}
    model.put(59, 8, 90, 'minecraft:dark_oak_wall_sign', {'facing': 'south', 'waterlogged': 'false'}, sign)
    islands = room_benches(model)
    model.architecture = {'revision': REVISION, 'prefabs': pieces + islands,
                          'research_islands': len(islands), 'facade_fins': 20,
                          'entrance_clearance': {'min': [58, 29, 88], 'max': [62, 32, 96]},
                          'protected_work_positions': [[x, y + 28, z] for x, y, z in sorted(model.architecture_approaches)]}
