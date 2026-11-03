"""Rebuilt boss silhouettes from the 2026-11-02 concept sheet.

The carapace is a curved collection of overlapping plates, not a platform.
The burrow king's armoured rings follow one continuous centreline and share
one body bone, so animation cannot separate adjacent rings.
"""
from __future__ import annotations

import math
import numpy as np
from modelkit import rotation_matrix
from concept_detail import ConceptModel as Model


def _lerp(a, b, t):
    return np.asarray(a, dtype=float) * (1 - t) + np.asarray(b, dtype=float) * t


def _euler(matrix):
    ry = math.asin(max(-1.0, min(1.0, -matrix[2, 0])))
    if abs(math.cos(ry)) > 1e-7:
        rx = math.atan2(matrix[2, 1], matrix[2, 2])
        rz = math.atan2(matrix[1, 0], matrix[0, 0])
    else:
        rx = 0.0
        rz = math.atan2(-matrix[0, 1], matrix[1, 1])
    return tuple(map(math.degrees, (rx, ry, rz)))


def _box(m, name, center, size, material, bone, matrix=None):
    rotation = (0, 0, 0) if matrix is None else _euler(matrix)
    return m.centered(name, center, size, material, bone, rot=rotation, pivot=center)


def _segment_frame(a, b):
    d = np.asarray(b) - np.asarray(a)
    return rotation_matrix((math.degrees(math.atan2(d[2], d[1])), 0, 0))


def _beam_frame(a, b):
    d = np.asarray(b) - np.asarray(a)
    return rotation_matrix((math.degrees(math.atan2(d[2], math.hypot(d[0], d[1]))),
                            0, math.degrees(math.atan2(-d[0], d[1]))))


def _frond(m, name, start, end, width=.65, bone='body', light=False):
    middle = _lerp(start, end, .55) + np.array([.6, 0, .4])
    m.beam(name, start, middle, width, mat='membrane', bone=bone)
    m.beam(name + '_tip', middle, end, width * .65,
           mat='crystal' if light else 'membrane', bone=bone)


def _crab_leg(m, side, index, z):
    hip = (side * 24, 42, z)
    knee = (side * (43 + (index == 1) * 4), 33, z * 1.32)
    ankle = (side * (54 + (index == 1) * 4), 7, z * 1.72)
    toe = (side * (58 + (index == 1) * 4), 1, z * 1.86 - 2)
    name = f'leg_{side}_{index}'
    phase = math.pi * ((index + (side == 1)) % 2)
    m.group(name, hip, 'body', 'leg', phase)
    lower = m.group(name + '_lower', knee, name)
    m.beam('thigh_muscle', hip, knee, 8, 9, 'tissue', name, True)
    m.beam('shin_muscle', knee, ankle, 6.5, 8, 'tissue', lower, True)
    for a, b, width, bone in ((hip, knee, 10, name), (knee, ankle, 8.4, lower)):
        frame = _beam_frame(a, b)
        outward = frame @ np.array([0, 0, -1.0])
        for j in range(3):
            start = _lerp(a, b, max(0, j / 3 - .04))
            end = _lerp(a, b, min(1.08, (j + 1) / 3 + .08))
            start[2] -= 2.5
            end[2] -= 2.5
            # Collinear shingles need separate front planes, including their
            # overlapping side faces; equal-depth beams otherwise z-fight.
            start += outward * (.17 + j * .12) - frame[:, 1] * .09
            end += outward * (.17 + j * .12)
            m.beam('overlap_limb_plate', start, end, width - j * .7,
                   4.6, 'bone', bone)
            edge = _lerp(start, end, .15) + outward * 2.48
            _box(m, 'plate_edge', edge, (width - j * .7 - .15, 1.1, .28), 'skull', bone, frame)
    # Wrap the knee and shin flanks, leaving visible organic gaps. The previous
    # front-only lamellae looked like thin boards in a true side/back viewport.
    for a, b, muscle_width, depth, bone, stations in (
            (hip, knee, 8, 9, name, (.72,)),
            (knee, ankle, 6.5, 8, lower, (.18, .63))):
        frame = _beam_frame(a, b)
        length = float(np.linalg.norm(np.asarray(b) - np.asarray(a)))
        for j, t in enumerate(stations):
            for side_edge in (-1, 1):
                center = _lerp(a, b, t) + frame @ np.array([
                    side_edge * (muscle_width * .5 + .42), .04 * side_edge, .37 + j * .09])
                _box(m, 'limb_flank_wrap', center,
                     (1.3, length * (.46 if bone == name else .31), depth * .76),
                     'bone', bone, frame)
    m.beam('pointed_tarsus', ankle, toe, 4.5, 5, 'bone', lower)
    m.beam('tarsus_tip', toe, (toe[0] + side * 1.6, .2, toe[2] - 2), 2, 2.4, 'shell', lower)


def _brood_chamber(m, name, c, size):
    x, y, z = c
    w, h, d = size
    sac = m.group(name, c, 'body', 'sac')
    m.sac(name + '_egg', c, size, sac, 'brood', False)
    # Sparse bright embryonic nodules on the exposed front, not a solid glowing egg.
    for j, (dx, dy) in enumerate(((-.22, -.12), (.18, -.2), (0, .15), (-.2, .27), (.24, .2))):
        m.centered('embryonic_nodule', (x + dx * w, y + dy * h, z - d * .44),
                   (2.2 + j % 2, 2.2, 1.1), 'crystal', sac, rot=(0, 0, j * 17),
                   pivot=(x + dx * w, y + dy * h, z - d * .44))
    radius = w * .67
    front = z - d * .52 - .5
    for layer in range(2):
        zz = front + layer * d * .77
        for j in range(8):
            arc_z = zz + (j + .7) * .07
            a, b = j * math.pi / 8, (j + 1) * math.pi / 8
            p = (x + radius * math.cos(a), y - 2 + h * .69 * math.sin(a), arc_z)
            q = (x + radius * math.cos(b), y - 2 + h * .69 * math.sin(b), arc_z)
            m.beam('chamber_bone_arch', p, q, 3.0 if layer == 0 else 2.5, 4.2,
                   'bone', 'body')
        for side in (-1, 1):
            m.beam('chamber_jamb', (x + side * radius, y - h * .5, zz),
                   (x + side * radius, y - 2, zz), 3.1, 4.4, 'bone', 'body')
    # Join both arches into a thick, open-front hood. Small depth staggering
    # prevents the neighbouring curved panels sharing overlapping end planes.
    for j in range(8):
        angle_a, angle_b = j * math.pi / 8, (j + 1) * math.pi / 8
        cap_z = front + d * .77 * .5 + .067 * j
        p = np.array((x + radius * math.cos(angle_a), y - 2 + h * .69 * math.sin(angle_a), cap_z))
        q = np.array((x + radius * math.cos(angle_b), y - 2 + h * .69 * math.sin(angle_b), cap_z))
        direction = (q - p) / np.linalg.norm(q - p)
        p -= direction * .11
        q += direction * .13
        m.beam('brood_chamber_hood', p, q, 3.8, d * .77 - .8 + j * .03, 'bone', 'body')
    for side in (-1, 1):
        m.centered('brood_chamber_flank',
                   (x + side * (radius + .18), y - h * .27, front + d * .77 * .52),
                   (3.6, h * .42, d * .68), 'bone', 'body')
    for j in range(3):
        xx = x + (j - 1) * w * .24
        _frond(m, 'chamber_drip', (xx, y - h * .2, front - .7),
               (xx + .5, y - h * .75 - j * 1.5, front - 1), .65, light=j == 1)


def walking_brood_crab():
    m = Model('walking_brood_crab', '行巢巨蟹', 'monsters', '移动巢穴 / 释放护卫')
    m.group('body', (0, 40, 0))
    # A narrow suspended organic centre leaves readable empty space between ribs.
    m.beveled('suspended_viscera', (0, 36, 2), (17, 25, 32), 'tissue', 'body')
    for side in (-1, 1):
        for z in (-23, -9, 6, 22):
            points = [(side * 30, 52, z), (side * 28, 40, z * .94),
                      (side * 20, 29, z * .76), (side * 9, 25, z * .6)]
            for a, b in zip(points, points[1:]):
                m.beam('open_belly_rib', a, b, 2.3, 2.7, 'bone', 'body')
        for z in (-10, 15):
            c = (side * 15, 38, z)
            chamber = m.group(f'belly_brood_{side}_{z}', c, 'body', 'sac')
            m.sac('hanging_brood_organ', c, (10, 15, 12), chamber, 'tissue', False)
    # Seven rows of tilted lamellae form a domed, scalloped shell.
    for iz, row_z in enumerate((-33, -23, -12, 0, 12, 23, 33)):
        half = 36 * math.sqrt(max(.1, 1 - (row_z / 45) ** 2))
        for ix in range(-3, 4):
            # Slightly stagger neighbouring tiles so their thin end caps do not
            # share a plane while overlapping along the curved roof.
            z = row_z + ix * .09
            x = ix * half / 3.5
            y = 57 + 13 * math.sqrt(max(.1, 1 - (z / 43) ** 2)) - .012 * x * x
            slope_x = math.degrees(math.atan(-.024 * x))
            slope_z = math.degrees(math.atan(.009 * z))
            m.centered('domed_carapace_lamella', (x, y, z), (half / 3 + 2.4, 3.3, 15),
                       'bone', 'body', rot=(slope_z, 0, slope_x), pivot=(x, y, z), variant=(iz + ix) % 3)
            m.centered('lamella_dark_overlap', (x, y - 1.2, z + 2.2),
                       (half / 3 + 2.8, 2.6, 15.5), 'shell', 'body',
                       rot=(slope_z, 0, slope_x), pivot=(x, y, z))
    for side in (-1, 1):
        for j, z in enumerate((-28, -18, -8, 3, 14, 25)):
            x = side * (32 - abs(z) * .13)
            start_y = 54 + (1 - abs(z) / 35) * 3
            for k in range(2):
                _frond(m, 'eave_mycelium', (x, start_y, z + k * 2),
                       (x + side * .8, 31 + (j % 3) * 3 + k * 3, z + k * 2 - .7),
                       .7, light=(j + k) % 5 == 0)
    for j, x in enumerate((-24, -16, -8, 0, 8, 16, 24)):
        _frond(m, 'front_hanging_mycelium', (x, 58 - abs(x) * .13, -36),
               (x + .7, 37 + (j % 3) * 3, -38), .75, light=j % 3 == 0)
    _brood_chamber(m, 'front_left_chamber', (-17, 70, -17), (19, 23, 19))
    _brood_chamber(m, 'front_right_chamber', (17, 70, -14), (19, 23, 19))
    _brood_chamber(m, 'high_rear_chamber', (0, 83, 14), (23, 27, 23))
    for side in (-1, 1):
        for j, z in enumerate((-29, 26)):
            base = (side * 29, 56 if j == 0 else 59, z)
            m.centered('crystal_tower_socket', base, (6, 5, 6), 'shell', 'body')
            m.centered('crystal_tower_bone_collar', (base[0], base[1] + 3, base[2]), (5.5, 2, 5.5), 'bone', 'body')
            m.crystal('canopy_crystal', (base[0], base[1] + 3, base[2]), 3.7, 13 + j * 3, 'body', (0, 0, -side * 7))
        for i, z in enumerate((-23, 0, 23)):
            _crab_leg(m, side, i, z)
        claw = m.group(f'claw_{side}', (side * 12, 34, -26), 'body', 'arm', side)
        a, b, c = (side * 12, 34, -26), (side * 19, 22, -43), (side * 18, 14, -53)
        m.beam('crusher_upper', a, b, 7, 8, 'bone', claw, True)
        m.beam('crusher_wrist', b, c, 6.8, 8, 'tissue', claw, True)
        m.beveled('crusher_palm', c, (11.2, 9, 13.2), 'bone', claw)
        for finger in (-1, 1):
            p = (c[0] + finger * 4.2, c[1], c[2] - 3)
            q = (c[0] + finger * 5.8, c[1] - 1.5, c[2] - 10)
            hook = (c[0] + finger * 3, c[1] - 3.1, c[2] - 15)
            tip = (c[0] + finger * .8, c[1] - 3.6, c[2] - 16.5)
            m.beam('crusher_finger', p, q, 4, 5.3, 'bone', claw)
            m.beam('crusher_hook', q, hook, 2.8, 3.8, 'bone', claw)
            m.beam('crusher_hook_point', hook, tip, 1.2, 1.8, 'bone', claw)
    m.group('head', (0, 30, -28), 'body', 'head')
    m.beveled('small_crab_skull', (0, 30, -30), (14, 9, 10), 'bone', 'head')
    for side in (-1, 1):
        m.eye('low_crab_eye', (side * 4, 31, -35.2), 2.2)
        m.beam('mouth_feeler', (side * 4, 27, -34), (side * 6, 22, -39), 1.2, mat='tissue', bone='head')
    _crab_animations(m)
    return m


def _crab_animations(m):
    m.creature_animations()
    # Restrained arthropod gait preserves planted, heavy limbs rather than a biped swing.
    walk = m.data['animations'][1]
    m.data['animations'].remove(walk)
    tracks = {'body': {'position': [(0, (0, 0, 0)), (.5, (0, .45, 0)), (1, (0, 0, 0))]}}
    for bone, (role, phase) in m.roles.items():
        if role != 'leg':
            continue
        side = -1 if bone.startswith('leg_-1') else 1
        tracks[bone] = {'rotation': [(t, (math.sin(t * math.tau + phase) * 7,
                                         math.cos(t * math.tau + phase) * 7,
                                         side * max(0, math.sin(t * math.tau + phase)) * 4))
                                     for t in (0, .25, .5, .75, 1)]}
    m.animation('walk', 1, True, tracks)


def _catmull(points, count):
    p = [np.asarray(v, dtype=float) for v in points]
    result = []
    for n in range(count):
        u = n / (count - 1) * (len(p) - 1)
        i = min(len(p) - 2, int(u))
        t = u - i
        a, b, c, d = p[max(0, i - 1)], p[i], p[i + 1], p[min(len(p) - 1, i + 2)]
        result.append(.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t
                            + (-a + 3 * b - 3 * c + d) * t * t * t))
    return result


def abyssal_burrow_king():
    m = Model('abyssal_burrow_king', '深渊掘王', 'monsters', '地下突袭 / 环形地刺')
    m.group('body', (0, 9, 11))
    curve = _catmull([(0, 5, -46), (0, 7, -24), (0, 16, 0), (0, 36, 23),
                     (0, 59, 23), (0, 79, 5), (0, 87, -19), (0, 84, -39)], 21)
    tail = [(0, 5, -46), (0, 3.8, -49), (0, 2.4, -52), (0, 1.3, -54)]
    for i, (a, b) in enumerate(zip(tail, tail[1:])):
        width = (6.6, 3.6, 1.5)[i]
        m.beam('tapered_tail_tip', a, b, width, width, 'tissue', 'body')
    radii = []
    # Continuous overlapping octagonal muscle links; all share the body bone.
    for i, (a, b) in enumerate(zip(curve, curve[1:])):
        radius = 4.0 + min(1, i / 5) * 7.5 + math.sin(i / 20 * math.pi) * 1.8 + i * .013
        radii.append(radius)
        c, length = (a + b) / 2, float(np.linalg.norm(b - a)) + 2.2
        frame = _segment_frame(a, b)
        if i < len(curve) - 2:
            for sizes in ((radius * 1.48, length, radius * 1.48),
                          (radius * 1.92, length + .12, radius * .7),
                          (radius * .7, length + .24, radius * 1.92)):
                _box(m, 'continuous_muscle', c, sizes, 'tissue', 'body', frame)
        else:
            # The final link is a hollow throat. A solid tube cap would hide the
            # recessed purple core even though its emissive material was correct.
            for j in range(12):
                angle = j * math.tau / 12
                local = np.array([math.sin(angle) * radius * .9, (j % 3 - 1) * .09,
                                  math.cos(angle) * radius * .9])
                orientation = frame @ rotation_matrix((0, math.degrees(angle), 0))
                _box(m, 'hollow_throat_muscle', c + frame @ local,
                     (radius * .5, length, 2.3), 'tissue', 'body', orientation)
        # Bone plates form an actual circular collar perpendicular to this link.
        for j in range(12):
            angle = j * math.tau / 12
            local = np.array([math.sin(angle) * radius, (j % 3 - 1) * .09,
                              math.cos(angle) * radius])
            orientation = frame @ rotation_matrix((0, math.degrees(angle), 0))
            axial = 3.4 + (i % 3) * .4
            center = c + frame @ local
            _box(m, 'radial_bone_collars', center,
                 (radius * .54 + .5, axial, 2.4), 'bone', 'body', orientation)
            # The narrower ridge gives a stepped edge without a thick square cuff.
            ridge = center + frame @ np.array([0, -axial * .32, 0])
            _box(m, 'collar_overlapping_edge', ridge,
                 (radius * .48, 1.2, 3.2), 'bone', 'body', orientation)
        if i in (6, 10, 14, 17):
            point = c + frame @ np.array([radius * .72, 0, -radius * .82])
            m.crystal('dorsal_crystal', point, 2.7, 8, 'body', (0, 0, -17))
    # Four curved distal hooks surround the broad, forward facing mouth.
    neck = curve[-1]
    mouth_frame = _segment_frame(curve[-2], curve[-1])
    tangent = mouth_frame @ np.array([0, 1, 0])
    m.group('head', neck, 'body')
    ring_center = neck + tangent * 2.0
    for ring, radius in enumerate((12.8, 11.2)):
        center = ring_center + tangent * (ring * 4)
        for j in range(12):
            angle = j * math.tau / 12
            orientation = mouth_frame @ rotation_matrix((0, math.degrees(angle), 0))
            radial = np.array([math.sin(angle), 0, math.cos(angle)])
            offset = radial * radius + np.array([0, (j % 3 - 1) * .09, 0])
            _box(m, 'open_throat_bone_ring', center + mouth_frame @ offset,
                 (radius * .55, 4.6, 3), 'bone' if ring == 0 else 'shell', 'head', orientation)
    # Recess the purple organ; the open jaw ring stays visibly hollow around it.
    core_center = ring_center - tangent * 2
    core = m.group('resonance_core', core_center, 'head', 'sac')
    m.sac('deep_resonance_core', core_center, (10.5, 10.5, 10.5), core, 'glow', False)
    for j in range(8):
        angle = j * math.tau / 8
        radial = mouth_frame @ np.array([math.sin(angle), 0, math.cos(angle)])
        base = ring_center + radial * 12.2
        joint = base + tangent * 8 + radial * 4.0
        hook = joint + tangent * 7 - radial * 3.3
        tip = hook + tangent * 3.8 - radial * 3.5
        jaw = m.group('jaw_' + str(j), base, 'head')
        m.beam('splayed_drilling_jaw', base, joint, 3.8, 4.6, 'bone', jaw)
        m.beam('curved_drilling_hook', joint, hook, 3.1, 3.6, 'bone', jaw)
        m.beam('drilling_tooth_tip', hook, tip, 1.5, 2.0, 'skull', jaw)
        # Internal teeth stop outside the glowing centre, preserving its silhouette.
        m.beam('inner_tooth', base + tangent * 4 - radial * 2,
               base + tangent * 7 - radial * 4.6, 1.4, 1.8, 'skull', jaw)
    for side in (-1, 1):
        anchor = ring_center + np.array([side * 12, -7, 0])
        elbow = anchor + np.array([side * 7, -10, -4])
        tip = elbow + np.array([-side * 1, -9, -7])
        m.beam('outer_mouth_claw', anchor, elbow, 3.6, 4, 'bone', 'head')
        m.beam('outer_mouth_claw_tip', elbow, tip, 2.2, 2.8, 'bone', 'head')
        m.beam('outer_mouth_claw_point', tip, tip + np.array([-side * 1.2, -3.5, -3]),
               .8, 1.1, 'skull', 'head')
    _worm_animations(m)
    return m


def _worm_animations(m):
    # No individually rotating ring bones: the continuous body cannot tear apart.
    m.animation('idle', 3, True, {
        'body': {'rotation': [(0, (0, -.7, 0)), (1.5, (0, .7, 0)), (3, (0, -.7, 0))]},
        'resonance_core': {'scale': [(0, (1, 1, 1)), (1.5, (1.08, 1.04, 1.08)), (3, (1, 1, 1))]}})
    m.animation('walk', 1.8, True, {'body': {
        'rotation': [(0, (0, -2, 0)), (.9, (0, 2, 0)), (1.8, (0, -2, 0))],
        'position': [(0, (0, 0, 0)), (.9, (0, .6, 0)), (1.8, (0, 0, 0))]}})
    attack = {'body': {'rotation': [(0, (0, 0, 0)), (.5, (-3, 0, 0)), (.8, (4, 0, 0)),
                                    (1.4, (0, 0, 0)), (2, (0, 0, 0))]},
              'resonance_core': {'scale': [(0, (1, 1, 1)), (.5, (1.2, 1.15, 1.2)), (.8, (.9, .9, .9)),
                                          (1.4, (1, 1, 1)), (2, (1, 1, 1))]}}
    for j in range(8):
        a = j * math.tau / 8
        attack['jaw_' + str(j)] = {'rotation': [(0, (0, 0, 0)),
            (.5, (math.cos(a) * -12, math.sin(a) * 12, 0)), (.8, (0, 0, 0)), (2, (0, 0, 0))]}
    m.animation('attack', 2, False, attack)
    m.animation('emerge', 1.5, False, {'root': {'position': [(0, (0, -85, 0)), (1.1, (0, 2, 0)), (1.5, (0, 0, 0))]}})
    m.animation('death', 1.5, False, {'body': {'rotation': [(0, (0, 0, 0)), (1.5, (0, 0, 65))],
                                                        'position': [(0, (0, 0, 0)), (1.5, (0, -7, 0))]}})


def build_models():
    return [walking_brood_crab(), abyssal_burrow_king()]


if __name__ == '__main__':
    from pathlib import Path
    from PIL import Image
    from modelkit import prepare_render
    from concept_materials import concept_palette
    from render_models import render

    folder = Path(__file__).resolve().parents[3] / 'ender_infection_colored/build/concept-review/bosses'
    for model in build_models():
        with concept_palette():
            data, texture, record = model.save(folder)
        for label, camera in [('front', (1, .45, -1.7)), ('side', (1, .22, -.2)), ('back', (1, .45, 1.5))]:
            picture = render(prepare_render(data), texture, width=900, height=900, camera=camera, margin=45)
            backdrop = Image.new('RGBA', picture.size, (29, 30, 35, 255))
            backdrop.alpha_composite(picture)
            backdrop.convert('RGB').save(folder / f'{model.key}_{label}.png')
        for action, time in [('walk', .25), ('attack', .5)]:
            animation = next(a for a in data['animations'] if a['name'].endswith('.' + action))
            picture = render(prepare_render(data, (animation, time)), texture, width=700, height=700,
                             camera=(1, .45, -1.7), margin=40)
            backdrop = Image.new('RGBA', picture.size, (29, 30, 35, 255))
            backdrop.alpha_composite(picture)
            backdrop.convert('RGB').save(folder / f'{model.key}_{action}.png')
        print(model.key, record['cubes'], record['bones'], record['atlas'], record['animations'], flush=True)
