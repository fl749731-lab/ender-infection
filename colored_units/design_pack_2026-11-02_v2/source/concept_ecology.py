"""Ecology silhouettes rebuilt from the 04 ecology concept sheet, in model pixels."""
import math

from concept_detail import ConceptModel as Model
from creatures import head, pair_eyes


def _dorsal_plate(m, name, center, size, bone, rot=(0, 0, 0)):
    x, y, z = center
    w, h, d = size
    m.centered(name + '_seam', (x, y - h * .26, z), (w, h * .48, d),
               'shell', bone, rot=rot, pivot=center)
    m.centered(name + '_armor', (x, y + h * .09, z), (w * .96, h * .82, d * .96),
               'bone', bone, rot=rot, pivot=center)
    m.centered(name + '_crest', (x, y + h * .47, z), (w * .7, h * .16, d * .74),
               'bone', bone, rot=rot, pivot=center)


def _pod(m, name, center, size, bone, material='membrane', steps=5):
    """Rounded tissue volume with enough slices to avoid a stacked-box profile."""
    m.sac(name, center, size, bone, material, False)


def _jointed_leg(m, name, hip, knee, foot, width, phase, parent='body', hoof=False):
    leg = m.group(name, hip, parent, 'leg', phase)
    lower = m.group(name + '_lower', knee, leg)
    m.beam(name + '_muscle', hip, knee, width, mat='tissue', bone=leg)
    m.beam(name + '_shin', knee, foot, width * .66, mat='tissue', bone=lower)
    for a, b, g, factor in ((hip, knee, leg, 1), (knee, foot, lower, .7)):
        for index, (start, end) in enumerate(((.08, .35), (.38, .67), (.7, .91))):
            p = tuple(a[j] + (b[j] - a[j]) * start for j in range(3))
            q = tuple(a[j] + (b[j] - a[j]) * end for j in range(3))
            m.beam(name + '_overlap_plate', p, q, width * factor * 1.2,
                   depth=width * factor * 1.1, mat='bone', bone=g)
            m.centered(name + '_joint_seam', q, (width * factor * 1.1,) * 3, 'shell', g)
    m.centered(name + '_knee', knee, (width * 1.45,) * 3, 'bone', leg)
    if hoof:
        m.centered(name + '_foot', (foot[0], .85, foot[2] - .4),
                   (width * 1.7, 1.7, width * 2.1), 'shell', lower)
        for side in (-1, 1):
            m.centered(name + '_toe', (foot[0] + side * width * .42, .65, foot[2] - width),
                       (width * .7, 1.3, width * .9), 'bone', lower)
    else:
        m.beam(name + '_tarsus', foot, (foot[0] * 1.04, .25, foot[2] - 1.4),
               max(.45, width * .42), mat='shell', bone=lower)


def _finish(m, stationary=False, flying=False, leg_amplitude=.5):
    m.creature_animations(stationary=stationary, flying=flying)
    if flying:
        # Left/right are mirror signs, not different temporal phases.
        for animation in m.data['animations']:
            if not animation['name'].endswith('.fly'):
                continue
            for name, (role, side) in m.roles.items():
                if role == 'wing':
                    for frame in animation['animators'][m.groups[name]['uuid']]['keyframes']:
                        if frame['channel'] == 'rotation':
                            frame['data_points'][0]['z'] = str(side * 23 * math.sin(frame['time'] * math.tau))
    # The low, sprawling stance should not turn into an upright marching gait.
    ids = {m.groups[name]['uuid'] for name, (role, _) in m.roles.items() if role in ('leg', 'chain')}
    for animation in m.data['animations']:
        if animation['name'].endswith(('.walk', '.attack')):
            for uid, track in animation['animators'].items():
                if uid in ids:
                    for frame in track['keyframes']:
                        if frame['channel'] == 'rotation':
                            for point in frame['data_points']:
                                for axis in ('x', 'y', 'z'):
                                    point[axis] = str(float(point[axis]) * leg_amplitude)
    if stationary:
        # Root buttresses remain planted while the egg sacs pulse.
        for animation in m.data['animations']:
            if animation['name'].endswith('.idle'):
                animation['animators'].pop(m.groups['body']['uuid'], None)
            elif animation['name'].endswith('.attack'):
                animation['animators'].pop(m.groups['root']['uuid'], None)
        m.animation('walk', 1, True, {})
    return m


def forager():
    m = Model('lurking_forager', '潜伏觅食虫', 'monsters', '低伏侦察 / 标记猎物')
    m.group('body', (0, 4.8, 1))
    _pod(m, 'low_abdomen', (0, 4.5, 2), (12, 5.5, 18), 'body', 'tissue')
    # Three overlapping pairs of sloping elytra, with a visible center seam.
    for i, z in enumerate((-2, 3.5, 8)):
        for s in (-1, 1):
            _dorsal_plate(m, 'elytron', (s * 3, 7.5 - i * .25, z),
                          (5.8, 2.5, 7), 'body', rot=(6 + i * 3, 0, s * 16))
            shoulder = (s * (5.3 - i * .08), 6.2 - i * .2, z)
            # Slightly inset each overlapping row so its side faces are not coplanar.
            m.centered('gray_shell_shoulder', shoulder,
                       (2.5, 3.7, 5.8), 'bone', 'body', rot=(0, 0, s * 15),
                       pivot=shoulder)
    for z in (-2, 3, 8):
        m.centered('dorsal_seam', (0, 8.2, z), (.65, .5, 4.8), 'shell', 'body')
    head(m, (0, 4.5, -9), (7.5, 4.4, 6), 'bone')
    m.centered('brow_plate', (0, 6.6, -10), (8, 1.25, 5), 'bone', 'head')
    pair_eyes(m, (0, 4.4, -12.2), 2.3, size=.85)
    m.group('jaw', (0, 3.4, -10), 'head', 'jaw')
    for s in (-1, 1):
        m.beam('mandible', (s * 2.2, 3.3, -11), (s * 2.9, 2.2, -14), 1.1, mat='bone', bone='jaw')
        m.beam('mandible_tip', (s * 2.9, 2.2, -14), (s * 1.9, 2, -15), .65, mat='shell', bone='jaw')
        antenna = m.group('antenna_' + str(s), (s * 2.2, 6, -10), 'head')
        points = [(s * 2.2, 6, -10), (s * 3.1, 10.7, -12), (s * 5.2, 12.2, -14.5), (s * 6.2, 12.2, -15.4)]
        for j, (a, b) in enumerate(zip(points, points[1:])):
            m.beam('antenna_segment', a, b, .65, mat='tissue', bone=antenna)
            if j < len(points) - 2:
                m.centered('antenna_joint', b, (.8, .8, .8), 'shell', antenna)
        m.centered('antenna_glint', points[-1], (1.1, .8, 1), 'crystal', antenna)
        for i, z in enumerate((-5, 1.3, 7)):
            _jointed_leg(m, f'leg_{s}_{i}', (s * 4.7, 4.6, z),
                         (s * 9, 4.7, z - 1.6), (s * 13, .8, z - 3.3), 1.05,
                         math.pi * ((i + (s == 1)) % 2))
        for z in (-1, 1.3, 3.6, 5.9, 8.2):
            m.centered('abdominal_gill', (s * 5.2, 3.5, z), (2, 1.5, 1.45), 'membrane', 'body')
    return _finish(m, leg_amplitude=.35)


def scout():
    m = Model('sporewing_scout', '孢翼侦察者', 'monsters', '横向飞行 / 孢囊标记')
    m.group('body', (0, 18, 0))
    _pod(m, 'horizontal_thorax', (0, 18, 0), (5.5, 4.8, 10), 'body', 'tissue')
    for z in (-2, 1, 4):
        m.centered('thorax_carapace', (0, 20, z), (5.3, 1.2, 2.5), 'bone', 'body', rot=(8, 0, 0), pivot=(0, 20, z))
    head(m, (0, 18.5, -6.5), (5.7, 4.4, 5), 'bone')
    pair_eyes(m, (0, 19, -9.2), 1.7, size=1)
    for s in (-1, 1):
        wing = m.group('wing_' + str(s), (s * 2.4, 19, -1), 'body', 'wing', s)
        a, b, c = (s * 2.4, 19, -1), (s * 11, 27, 1), (s * 26, 30, 3)
        m.beam('wing_humerus', a, b, 1.4, mat='bone', bone=wing, joint=True)
        m.beam('wing_leading_edge', b, c, 1.1, mat='bone', bone=wing)
        m.centered('wing_elbow', b, (2.3, 2.3, 2.3), 'bone', wing)
        # Connected upright membranes make a wide bat fan, not narrow horizontal slats.
        for j in range(12):
            x = 3 + j * 2
            upper = 19 + (x - 3) if x < 11 else 27 + (x - 11) * .2
            lower = 14.3 + (x - 11) * .22 if x < 19 else 16 + (x - 19) * 1.75
            lower += (j % 3 == 1) * 1.15
            z = 2.2 + (x - 3) * .045
            m.centered('continuous_wing_membrane', (s * x, (upper + lower) / 2, z),
                       (2.16, upper - lower, .55), 'membrane', wing, variant=j % 3)
            m.centered('torn_wing_scallop', (s * x, lower - .7, z), (1.4, 1.7, .55), 'membrane', wing)
        for x, y in ((7, 14), (13, 14.7), (19, 16.6), (24, 25)):
            m.beam('wing_finger', b, (s * x, y, 2.6), .65, mat='bone', bone=wing)
            m.centered('finger_tip', (s * x, y, 2.6), (.9, .9, .85), 'crystal', wing)
        m.centered('wing_tip', c, (1.1, .9, 1.1), 'glow', wing)
        for z in (-2, 2):
            m.beam('small_flight_leg', (s * 2, 17, z), (s * 3, 13.8, z - 1), .8, mat='bone', bone='body')
            m.beam('small_flight_claw', (s * 3, 13.8, z - 1), (s * 2.8, 12.8, z - 2), .5, mat='bone', bone='body')
        m.beam('head_feeler', (s * 1.8, 20.5, -6), (s * 2.6, 23.6, -5), .55, mat='shell', bone='head')
    sac = m.group('spore_sac', (3.5, 13.5, 4), 'body', 'sac')
    _pod(m, 'rear_spore_sac', (3.5, 13.5, 4), (6.5, 6.5, 6.5), sac, 'spore')
    for i in range(8):
        angle = i * math.tau / 8
        m.centered('sac_spore_glint', (3.5 + math.sin(angle) * 2.1, 13.5 + math.cos(angle) * 2.1, 1.3),
                   (1.1, 1.3, .5), 'crystal' if i % 2 else 'glow', sac)
    for i in range(3):
        root = m.group('trailing_root_' + str(i), (2.5 + i, 11.5, 6.5), 'body', 'chain', i * .3)
        points = [(2.5 + i, 11.5, 6.5), (3.5 + (i - 1) * 2, 9, 12), (3.5 + (i - 1) * 3, 7, 17), (3.5 + (i - 1) * 3.8, 7.3, 21)]
        for a, b in zip(points, points[1:]):
            m.beam('trailing_filament', a, b, .55, mat='tissue', bone=root)
            m.centered('filament_joint', b, (.8, .8, .8), 'shell', root)
    return _finish(m, flying=True, leg_amplitude=.3)


def burrower():
    m = Model('mycelial_burrower', '菌丝掘地者', 'monsters', '弯曲分节钻地体')
    m.group('body', (0, 4, 16))
    points = [(0, 3, 20), (-2, 4, 14), (-4, 7.5, 8.5), (-4, 13, 5),
              (-2.7, 19, 3.8), (-.2, 25, 1.5), (1.5, 30.5, -2), (1.8, 33, -7)]
    parent = 'body'
    for i, (a, b) in enumerate(zip(points, points[1:])):
        bone = m.group('segment_' + str(i), a, parent, 'chain', i * .25)
        dx, dy, dz = [b[j] - a[j] for j in range(3)]
        length = math.sqrt(dx * dx + dy * dy + dz * dz)
        rot = (math.degrees(math.atan2(dz, math.hypot(dx, dy))), 0, math.degrees(math.atan2(-dx, dy)))
        width = 3.1 + min(i, 4) * .72
        m.beam('living_segment', a, b, width, mat='tissue', bone=bone)
        for band in (.24, .72):
            y = a[1] + length * band
            for side in (-1, 1):
                m.centered('square_ring_side', (a[0] + side * width * .49, y, a[2]),
                           (width * .23, length * .41, width * 1.03), 'bone', bone, rot=rot, pivot=a)
                m.centered('square_ring_front', (a[0], y, a[2] + side * width * .49),
                           (width * .77, length * .41, width * .23), 'bone', bone, rot=rot, pivot=a)
                m.centered('armored_ridge', (a[0] + side * width * .58, y, a[2]),
                           (width * .12, length * .3, width * .55), 'skull', bone, rot=rot, pivot=a)
        for side in (-1, 1):
            m.centered('joint_organ', (a[0] + side * width * .33, a[1] + length * .05, a[2] - width * .48),
                       (width * .25, .6, .5), 'membrane', bone, rot=rot, pivot=a)
        parent = bone
    c = (1.8, 33.3, -9)
    m.group('head', c, parent, 'head')
    m.centered('deep_mouth', c, (8, 8, 1.2), 'black', 'head')
    m.centered('resonance_core', (c[0], c[1], c[2] - .8), (1.7, 1.8, .35), 'glow', 'head')
    for i in range(8):
        angle = i * math.tau / 8
        x, y = c[0] + math.sin(angle) * 4.8, c[1] + math.cos(angle) * 4.8
        m.centered('ring_jaw', (x, y, c[2] - 1.5), (3.4, 2.8, 4.6), 'bone', 'head',
                   rot=(0, 0, -math.degrees(angle)), pivot=(x, y, c[2] - 1.5))
        m.centered('outer_jaw_plate', (x, y, c[2] - 1.1), (3, 1.1, 5), 'skull', 'head',
                   rot=(0, 0, -math.degrees(angle)), pivot=(x, y, c[2] - 1.1))
        m.beam('inward_tooth', (x, y, c[2] - 3.8),
               (c[0] + math.sin(angle) * 2.8, c[1] + math.cos(angle) * 2.8, c[2] - 5.5),
               1.3, mat='bone', bone='head')
    for s in (-1, 1):
        m.beam('short_digging_arm', (c[0] + s * 4.8, c[1] - 2, -6),
               (c[0] + s * 7.6, c[1] - 4.3, -9), 1.5, mat='bone', bone='head')
        m.beam('digging_tip', (c[0] + s * 7.6, c[1] - 4.3, -9),
               (c[0] + s * 6.6, c[1] - 6, -11), 1, mat='bone', bone='head')
    return _finish(m, leg_amplitude=.3)


def carrier():
    m = Model('crystalback_carrier', '晶背搬运兽', 'monsters', '背负储存囊 / 侧绑生物质')
    m.group('body', (0, 14, 4))
    _pod(m, 'heavy_trunk', (0, 14, 4), (17, 13, 25), 'body', 'tissue')
    for s in (-1, 1):
        for i, z in enumerate((-4, 11.5)):
            _jointed_leg(m, f'leg_{s}_{i}', (s * 6.4, 13, z), (s * 8.4, 7, z - 1),
                         (s * 9, 1.5, z - 2), 3.1, math.pi * ((i + (s == 1)) % 2), hoof=True)
        for z in (-4, 3, 10):
            m.centered('body_side_armor', (s * 7.5, 16.7, z), (4.1, 7.5, 7.6), 'bone', 'body',
                       rot=(0, 0, -s * 15), pivot=(s * 7.5, 16.7, z))
            m.centered('side_armor_crest', (s * 9.1, 17, z), (1, 5.2, 5.2), 'bone', 'body',
                       rot=(0, 0, -s * 15), pivot=(s * 7.5, 16.7, z))
    head(m, (0, 12.5, -13), (12, 8, 11), 'shell')
    for z, y, width in ((-10, 16, 12.5), (-14, 15, 11.7), (-18, 13.5, 10.5)):
        _dorsal_plate(m, 'overlapping_head_shield', (0, y, z), (width, 2.6, 5.6), 'head', rot=(12, 0, 0))
    m.centered('lower_snout', (0, 9.8, -19), (9.8, 3.1, 4), 'bone', 'head')
    pair_eyes(m, (0, 12.8, -21.35), 4, size=1.25)
    for s in (-1, 1):
        m.centered('mouth_corner', (s * 4, 9.4, -20.7), (1, .8, .35), 'shell', 'head')
        m.crystal('shoulder_energy_node', (s * 5.7, 20, -4), 2.6, 5.7, 'body', rot=(0, 0, -s * 13))
    # Three differently sized overlapping cargo sacs replace the former box cage.
    for i, (c, size) in enumerate((((-5, 24.3, 4), (10, 10.5, 12)),
                                    ((5, 23, 11), (11, 10, 12)),
                                    ((2, 28, 1), (8.5, 9.2, 10)))):
        sac = m.group('cargo_' + str(i), c, 'body', 'sac')
        _pod(m, 'bulging_cargo', c, size, sac)
        for side in (-1, 1):
            m.beam('pod_tendon', (c[0] + side * size[0] * .4, c[1] - size[1] * .3, c[2]),
                   (c[0] + side * size[0] * .25, c[1] + size[1] * .4, c[2]), .65,
                   mat='shell', bone=sac)
        for j in range(5):
            m.centered('cargo_spore_patch', (c[0] + (j % 3 - 1) * 1.6, c[1] + j * .65 - 1.2, c[2] - size[2] * .43),
                       (1.35, 1.5, .45), 'tissue', sac)
    # An actual side bundle and narrow retaining ropes make its worker role legible.
    cargo = m.group('side_bundle', (11, 12, 4), 'body')
    for i in range(5):
        m.centered('collected_bone_bundle', (10.2 + (i % 2) * 1.8, 12 + (i % 3) * .7, 1 + i * 1.7),
                   (1.7, 7 + (i % 2) * 2, 1.4), 'bone', cargo, rot=(0, 0, -12 + i * 5),
                   pivot=(11, 12, 4))
    for y in (9.5, 13.5):
        m.beam('cargo_binding', (9, y, 0), (13.3, y, 0), .6, mat='wood', bone=cargo)
        m.beam('cargo_binding', (13.3, y, 0), (13.3, y, 9), .6, mat='wood', bone=cargo)
        m.beam('cargo_binding', (13.3, y, 9), (8.8, y, 9), .6, mat='wood', bone=cargo)
    for z in (0, 9):
        m.beam('harness_tether', (9, 9, z), (7.8, 22.5, z), .7, mat='wood', bone='body')
    return _finish(m, leg_amplitude=.5)


def spider():
    m = Model('parasitic_spider_mother', '寄生蛛母', 'monsters', '八足 / 腹部孵化与吊丝囊')
    m.group('body', (0, 8.5, -3))
    _pod(m, 'low_cephalothorax', (0, 8.5, -3), (10, 5.8, 12), 'body', 'tissue')
    head(m, (0, 8, -10), (7.5, 4.7, 5), 'bone')
    for x, y in ((-2, 8.4), (0, 9), (2, 8.4), (-1, 6.9), (1, 6.9)):
        m.eye('tiny_violet_eye', (x, y, -12.7), .6, 'head')
    for s in (-1, 1):
        for i, z in enumerate((-7, -2.5, 2, 7)):
            knee_z = (-14, -6, 6, 14)[i]
            foot_z = (-20, -11, 9, 21)[i]
            _jointed_leg(m, f'leg_{s}_{i}', (s * 4.2, 8.5, z),
                         (s * (13 + (i % 2) * 1.4), 11.2, knee_z),
                         (s * (19 + (i % 2) * 1.5), .9, foot_z), 1.55,
                         math.pi * ((i + (s == 1)) % 2))
        m.beam('short_mandible', (s * 2, 6.7, -11.7), (s * 2.5, 4.8, -14), 1, mat='bone', bone='head')
        m.beam('small_fang', (s * 2.5, 4.8, -14), (s * 1.8, 4.2, -15), .6, mat='shell', bone='head')
    abdomen = m.group('abdomen', (0, 13, 7), 'body', 'sac')
    _pod(m, 'wide_brood_abdomen', (0, 14, 7), (14.5, 14, 18), abdomen)
    for i, (c, size) in enumerate((((-4.5, 20, 4), (5.6, 5.4, 6.5)), ((3, 21, 10), (6.5, 5.8, 7)), ((-4, 14.5, 14), (5, 6, 6)))):
        _pod(m, 'asymmetric_brood_lobe', c, size, abdomen, steps=3)
    for s in (-1, 1):
        for z in (1, 6, 11):
            m.plate('abdomen_side_plate', (s * 6.2, 13.6, z), (2.4, 4.3, 4.5), abdomen,
                    rot=(0, 0, -s * 23))
    for z in (-5, -1, 3):
        _dorsal_plate(m, 'thorax_armor', (0, 11, z), (8.5, 1.8, 4), 'body')
    silk = m.group('silk_bundle', (10, 14, 0), 'body', 'sac')
    _pod(m, 'pale_hanging_egg', (10, 14, 0), (7, 8, 6.5), silk, 'spore')
    for i in range(4):
        m.beam('hanging_silk', (2.5, 23, 6), (8.2 + i * 1.2, 16.8, -1.5 + (i % 2) * 2),
               .38, mat='skull', bone='body')
    for i in range(9):
        m.centered('brood_glint', (8.9 + (i % 3) * 1.1, 12.4 + (i // 3) * 1.6, -3.38),
                   (.9, 1.35, .35), 'crystal' if i % 2 else 'glow', silk)
    for i in range(3):
        m.beam('spinner_filament', (i - 1, 11, 14), ((i - 1) * 2, 6.2, 21 + i), .45,
               mat='tissue', bone=abdomen)
    return _finish(m, leg_amplitude=.32)


def hatch_sac():
    m = Model('pulsing_hatch_sac', '脉动孵化囊', 'monsters', '低垂膜囊 / 三卵前腔')
    m.group('body', (0, 10, 0))
    for z, width in ((-8, 18), (-4, 25), (0, 27), (4, 25), (8, 18)):
        m.centered('low_nest_floor', (0, 2, z), (width, 1.5, 4.2), 'shell', 'body')
    for sx in (-1, 1):
        for sz in (-1, 1):
            root = m.group(f'anchor_{sx}_{sz}', (sx * 10, 10, sz * 7), 'body')
            a, b, c = (sx * 10, 10, sz * 7), (sx * 13.7, 4, sz * 10), (sx * 18, 1, sz * 12)
            m.beam('short_root_buttress', a, b, 3.2, mat='bone', bone=root, joint=True)
            m.beam('root_foot', b, c, 2.5, mat='bone', bone=root)
            for i in range(3):
                m.beam('root_rib', (sx * (10.3 + i * .9), 9 - i * 1.8, sz * (7.3 + i)),
                       (sx * (12 + i * 1.4), 4.2 - i * .7, sz * (8.5 + i)), 1,
                       mat='skull', bone=root)
            for i in (-1, 1):
                m.beam('root_toe', c, (c[0] + sx * 2.5, .6, c[2] + i * 1.7), .9, mat='shell', bone=root)
    # Rear and side curtains widen downward; the entire front remains an open egg chamber.
    levels = [(20.3, 2.3), (17.8, 4.5), (14.5, 7.6), (11.2, 10.5), (7.5, 12.7), (3.7, 13.3)]
    for deg in range(-120, 121, 30):
        angle = math.radians(deg)
        for j, ((ya, ra), (yb, rb)) in enumerate(zip(levels, levels[1:])):
            y, radius = (ya + yb) / 2, (ra + rb) / 2
            x, z = math.sin(angle) * radius, math.cos(angle) * radius
            slope = -math.degrees(math.atan2(rb - ra, ya - yb))
            m.centered('layered_drooping_membrane', (x, y, z),
                       (max(2.3, radius * .59), math.hypot(ya - yb, rb - ra) + .12, .8),
                       'membrane', 'body', rot=(slope, deg, 0), pivot=(x, y, z), variant=j % 3)
        if deg % 60 == 0:
            for (ya, ra), (yb, rb) in zip(levels, levels[1:]):
                m.beam('radial_support_rib', (math.sin(angle) * ra, ya, math.cos(angle) * ra),
                       (math.sin(angle) * rb, yb, math.cos(angle) * rb), .9, mat='bone', bone='body')
        x, z = math.sin(angle) * 13, math.cos(angle) * 13
        m.beam('membrane_fringe', (x, 6, z), (x * 1.02, 3.3 + abs(deg) % 3, z * 1.02), .65,
               mat='tissue', bone='body')
    # Front canopy only at the upper corners, leaving three bright separate eggs readable.
    for s in (-1, 1):
        points = [(s * 1.8, 20, -1), (s * 6.5, 16.5, -6), (s * 11.3, 12, -9), (s * 13, 6, -10)]
        for a, b in zip(points, points[1:]):
            m.beam('front_canopy_edge', a, b, 1.05, mat='bone', bone='body')
        m.centered('front_canopy_inner', (s * 3.4, 18.5, -3.4), (7, 1.2, 7), 'membrane', 'body',
                   rot=(18, 0, -s * 34), pivot=(s * 3.4, 18.5, -3.4))
        m.centered('front_canopy_outer', (s * 8.7, 14.8, -6.8), (5.7, 1.1, 6), 'membrane', 'body',
                   rot=(8, 0, -s * 40), pivot=(s * 8.7, 14.8, -6.8))
        for j in range(3):
            m.beam('doorway_hanging_fiber', (s * (10.1 + j * .8), 12.4 - j * .6, -8.7),
                   (s * (10.3 + j * .8), 6.4 - j * .7, -9.1), .35,
                   mat='tissue' if j % 2 else 'membrane', bone='body')
    for i, x in enumerate((-6.5, 0, 6.5)):
        y = 6.1 if i == 1 else 6.8
        size = (5.8, 6.2, 6) if i == 1 else (6.2, 6.8, 6.2)
        egg = m.group('larva_' + str(i), (x, y, -5.2), 'body', 'sac')
        _pod(m, 'visible_larval_sac', (x, y, -5.2), size, egg, 'spore')
        for side in (-1, 1):
            m.beam('egg_tether', (x + side * .6, 16.4 - abs(x) * .13, -3.6),
                   (x + side * .9, y + size[1] * .39, -5.2), .27, mat='skull', bone='body')
        for j, (dx, dy) in enumerate(((-.9, -.6), (.65, .65), (.25, -1.35))):
            m.centered('larval_crystal_fleck', (x + dx, y + dy, -5.2 - size[2] / 2 - .05),
                       (.8, .85, .3), 'crystal' if j % 2 else 'glow', egg)
    m.centered('neck_collar', (0, 20.3, 0), (5.5, 2, 5.5), 'bone', 'body')
    for s in (-1, 1):
        m.beam('short_feeler', (s, 21, 0), (s * 2.4, 26 + (s == 1), .3), .7, mat='tissue', bone='body')
        m.beam('feeler_tip', (s * 2.4, 26 + (s == 1), .3), (s * 4, 27 + (s == 1), -.2), .85, mat='crystal', bone='body')
    return _finish(m, stationary=True)


def build_models():
    return [forager(), scout(), burrower(), carrier(), spider(), hatch_sac()]
