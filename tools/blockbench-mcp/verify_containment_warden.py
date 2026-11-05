"""Verify the staged MCP export contract; this is not an in-game test."""
from __future__ import annotations

import base64
import hashlib
import io
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'colored_units/institute_boss_v1'
PREVIEW = ROOT / 'previews/institute-boss-alpha7'


def read(path):
    return json.loads(path.read_text('utf-8'))


def require(ok, message):
    if not ok:
        raise ValueError(message)


def verify():
    source = read(ASSET / 'containment_warden.bbmodel')
    geometry = read(ASSET / 'export/containment_warden.geo.json')['minecraft:geometry'][0]
    runtime = read(ASSET / 'export/containment_warden.animation.json')['animations']
    groups = {}
    group_names = {node['uuid']: node['name'] for node in source.get('groups', [])}

    def walk(nodes, parent=None):
        for node in nodes:
            if isinstance(node, dict):
                name = node.get('name') or group_names[node['uuid']]
                groups[node['uuid']] = (name, parent)
                walk(node.get('children', []), name)

    walk(source['outliner'])
    bones = {bone['name']: bone for bone in geometry['bones']}
    require(set(bones) == {name for name, _ in groups.values()}, 'Export bone set differs')
    for name, parent in groups.values():
        require(bones[name].get('parent') == parent, f'Changed bone parent: {name}')
    cubes = source['elements']
    require(all(cube['type'] == 'cube' for cube in cubes), 'Non-cube export needs another codec')
    require(sum(len(bone.get('cubes', [])) for bone in bones.values()) == len(cubes), 'Cube lost')
    description = geometry['description']
    require(description['texture_width'] == description['texture_height'] == 256, 'Export atlas size')
    require(description['identifier'] == 'geometry.containment_warden', 'Geometry identifier')

    images = []
    for texture, filename in zip(source['textures'], ['containment_warden.png', 'containment_warden_glow.png']):
        png = base64.b64decode(texture['source'].split(',', 1)[1])
        require(png == (ASSET / 'export' / filename).read_bytes(), f'PNG differs: {filename}')
        image = Image.open(io.BytesIO(png)).convert('RGBA')
        require(image.size == (256, 256), f'PNG size: {filename}')
        images.append(image)
    require(len(source['textures']) == 2, 'Unexpected texture layer')
    require(source['textures'][1]['render_mode'] == 'emissive', 'Glow preview material')
    glowing_faces = 0
    dimensions = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
    for cube in cubes:
        size = [b - a for a, b in zip(cube['from'], cube['to'])]
        for face_name, face in cube['faces'].items():
            uv = face['uv']
            require(all(0 <= value <= 256 for value in uv), f'UV outside atlas: {cube["name"]}')
            width, height = abs(uv[2] - uv[0]), abs(uv[3] - uv[1])
            x_axis, y_axis = dimensions[face_name]
            require(0 <= width - size[x_axis] * 2 < 1.001 and 0 <= height - size[y_axis] * 2 < 1.001,
                    f'UV density/stretch differs: {cube["name"]}/{face_name}')
            region = tuple(map(int, uv))
            alpha = images[1].getchannel('A').crop(region).getextrema()
            if face['texture'] == 1:
                require(alpha == (255, 255), f'Incomplete emission: {cube["name"]}/{face_name}')
                require(images[0].crop(region).tobytes() == images[1].crop(region).tobytes(), 'Emission color differs')
                glowing_faces += 1
            else:
                require(alpha == (0, 0), f'Non-crystal face glows: {cube["name"]}/{face_name}')
        if any(word in cube['name'] for word in ['crystal', 'cluster']) or cube['name'] == 'eye_slit':
            require(all(face['texture'] == 1 for face in cube['faces'].values()), f'Crystal face omitted: {cube["name"]}')

    require(set(runtime) == {clip['name'] for clip in source['animations']}, 'Clip set differs')
    keyframes = 0
    for clip in source['animations']:
        exported = runtime[clip['name']]
        require(exported['animation_length'] == clip['length'], 'Clip length changed')
        require(bool(exported.get('loop', False)) == (clip['loop'] == 'loop'), 'Clip loop changed')
        expected_bones = set()
        for uuid, animator in clip['animators'].items():
            require(uuid in groups and animator['type'] == 'bone', 'Unsupported animation target')
            bone = groups[uuid][0]
            expected_bones.add(bone)
            expected_channels = {}
            for key in animator['keyframes']:
                channel = key['channel']
                require(channel in ['position', 'rotation', 'scale'], 'Unsupported channel')
                require(key['interpolation'] == 'linear' and len(key['data_points']) == 1, 'Lossy interpolation')
                value = [float(key['data_points'][0][axis]) for axis in 'xyz']
                require(all(math.isfinite(n) for n in value), 'Non-finite transform')
                if channel == 'position':
                    value[0] *= -1
                elif channel == 'rotation':
                    value[0] *= -1
                    value[1] *= -1
                expected_channels.setdefault(channel, {})[key['time']] = value
                keyframes += 1
            actual_channels = exported['bones'][bone]
            require(set(actual_channels) == set(expected_channels), 'Channel set changed')
            for channel, values in expected_channels.items():
                actual = {float(time): [float(n) for n in vector] for time, vector in actual_channels[channel].items()}
                require(actual == values, f'Exported transform differs: {clip["name"]}/{bone}/{channel}')
                if clip['loop'] == 'loop':
                    require(values[0] == values[clip['length']], 'Loop endpoints differ')
        require(set(exported['bones']) == expected_bones, 'Exported animator set differs')

    files = [ASSET / 'containment_warden.bbmodel', *sorted((ASSET / 'export').glob('*'))]
    report = {
        'status': 'PASS',
        'scope': 'Editable source to staged numeric-linear MCP export; no Java runtime or gameplay validation.',
        'cubes': len(cubes), 'bones': len(bones), 'triangles_upper_bound': len(cubes) * 12,
        'clips': len(runtime), 'keyframes': keyframes, 'emissive_faces': glowing_faces,
        'texture_size': [256, 256], 'density_px_per_model_unit': 2,
        'geometry': read(PREVIEW / 'geometry-validation.json')['summary'],
        'geckolib_metadata': read(PREVIEW / 'geometry-validation.json')['geckolib']['summary'],
        'animation': read(PREVIEW / 'animation-validation.json')['summary'],
        'sha256': {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
    }
    require(report['geometry']['passed'] and report['animation']['passed'], 'MCP validation failed')
    (PREVIEW / 'export-validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'sha256'}, ensure_ascii=False))


if __name__ == '__main__':
    verify()
