"""Export legacy creature glow masks from named crystal geometry and its UVs."""
import base64
from collections import Counter
import io
import json

import numpy as np
from PIL import Image

from design_catalog import ASSETS, ROOT
from export_creature_reworks import SPECS as REWORKS

LEGACY_CREATURE_SOURCES = {
    'infected_' + species: ROOT / 'colored_units' / f'infected_{species}_colored.bbmodel'
    for species in (
        'cow', 'pig', 'sheep', 'spider',
        'cow_early', 'pig_early', 'sheep_early', 'skeleton_early',
        'spider_early', 'zombie_early',
    ) if 'infected_' + species not in REWORKS
}


def cube_signature(origin, size, uv, rotation=(0, 0, 0), pivot=(0, 0, 0)):
    def rounded(values):
        return tuple(round(n, 6) for n in values)
    return (rounded(origin), rounded(size),
            tuple(sorted((face, rounded(rect)) for face, rect in uv.items())),
            rounded(rotation), rounded(pivot) if any(rotation) else ())


def legacy_crystal_pixels(runtime):
    """Return unchanged base pixels and an exact, non-overlapping crystal UV mask."""
    source = LEGACY_CREATURE_SOURCES[runtime]
    model = json.loads(source.read_text(encoding='utf-8'))
    geometry = json.loads((ASSETS / 'geo' / f'{runtime}.geo.json').read_text())['minecraft:geometry'][0]
    with Image.open(ASSETS / 'textures/entity' / f'{runtime}.png') as image:
        pixels = np.array(image.convert('RGBA'))
    embedded = base64.b64decode(model['textures'][0]['source'].split(',', 1)[1])
    with Image.open(io.BytesIO(embedded)) as image:
        if not np.array_equal(pixels, np.array(image.convert('RGBA'))):
            raise ValueError(f'{runtime}: source and runtime texture differ')
    height, width = pixels.shape[:2]
    if (width, height) != (geometry['description']['texture_width'], geometry['description']['texture_height']):
        raise ValueError(f'{runtime}: texture dimensions differ from runtime geometry')
    expected = np.zeros((height, width), dtype=bool)
    other = np.zeros_like(expected)
    source_cubes = []
    crystal_elements = 0
    for element in model['elements']:
        if not element.get('export', True):
            continue
        is_crystal = any(word in element['name'].lower() for word in ('crystal', 'shard'))
        crystal_elements += is_crystal
        selected = expected if is_crystal else other
        uv = {}
        for face, data in element['faces'].items():
            if data.get('texture') is None:
                continue
            rect = data['uv']
            if any(value != int(value) for value in rect):
                raise ValueError(f'{runtime}: non-integer UV on {element["name"]}')
            x, y, X, Y = map(int, rect)
            x, X = sorted((x, X)); y, Y = sorted((y, Y))
            if not (0 <= x < X <= width and 0 <= y < Y <= height):
                raise ValueError(f'{runtime}: UV outside texture on {element["name"]}')
            selected[y:Y, x:X] = True
            uv[{'east': 'west', 'west': 'east'}.get(face, face)] = rect
        lo, hi = element['from'], element['to']
        rotation, pivot = element.get('rotation', [0, 0, 0]), element.get('origin', [0, 0, 0])
        source_cubes.append(cube_signature(
            [-hi[0], lo[1], lo[2]], [hi[i] - lo[i] for i in range(3)], uv,
            [-rotation[0], -rotation[1], rotation[2]], [-pivot[0], pivot[1], pivot[2]]))
    runtime_cubes = []
    for bone in geometry['bones']:
        for cube in bone.get('cubes', []):
            uv = {face: data['uv'] + [data['uv'][i] + data['uv_size'][i] for i in range(2)]
                  for face, data in cube['uv'].items()}
            runtime_cubes.append(cube_signature(cube['origin'], cube['size'], uv,
                                               cube.get('rotation', [0, 0, 0]), cube.get('pivot', [0, 0, 0])))
    if Counter(source_cubes) != Counter(runtime_cubes):
        raise ValueError(f'{runtime}: source geometry/UVs differ from runtime')
    if (expected & other).any():
        raise ValueError(f'{runtime}: crystal UV overlaps non-crystal geometry')
    expected &= pixels[:, :, 3] > 0
    if not crystal_elements or not expected.any():
        raise ValueError(f'{runtime}: no visible crystal geometry')
    return pixels, expected, crystal_elements


def export():
    prepared = [(runtime, *legacy_crystal_pixels(runtime)) for runtime in LEGACY_CREATURE_SOURCES]
    for runtime, pixels, expected, elements in prepared:
        mask = np.zeros_like(pixels)
        mask[expected] = pixels[expected]
        Image.fromarray(mask).save(ASSETS / 'textures/entity' / f'{runtime}_glowmask.png')
        print(f'{runtime}: {elements} crystal elements, {int(expected.sum())} pixels')


if __name__ == '__main__':
    export()
