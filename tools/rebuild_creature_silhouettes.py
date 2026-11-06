"""Rebuild only the 14 revised creatures, preserving unrelated design assets."""
import argparse
from collections import Counter
import json
import math
import shutil
import sys

import numpy as np

from design_catalog import ASSETS, PACK, PROJECT, ROOT, UNITS

sys.path.insert(0, str(PACK / 'source'))
from concept_materials import concept_palette
from modelkit import prepare_render, resolve_groups, rotation_matrix


def posed_bounds(data, frame=None):
    points = []
    for element in prepare_render(data, frame)['elements']:
        lo, hi = element['from'], element['to']
        corners = np.array([[x, y, z] for x in (lo[0], hi[0])
                            for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
        pivot = np.asarray(element.get('origin', [0, 0, 0]))
        rotation = rotation_matrix(element.get('rotation', [0, 0, 0]))
        corners = (corners - pivot) @ rotation.T + pivot
        pose = np.asarray(element['_pose'])
        points.extend(corners @ pose[:3, :3].T + pose[:3, 3])
    values = np.asarray(points)
    assert np.isfinite(values).all()
    return values.min(axis=0), values.max(axis=0)


def validate(data):
    data = resolve_groups(data)
    elements = {e['uuid']: e for e in data['elements']}
    assert len(elements) == len(data['elements'])
    groups, owners = {}, []
    def visit(group):
        assert group['uuid'] not in groups
        groups[group['uuid']] = group
        for child in group['children']:
            if isinstance(child, dict):
                visit(child)
            else:
                owners.append(child)
    for group in data['outliner']:
        visit(group)
    assert Counter(owners) == Counter(elements.keys())
    width, height = data['resolution']['width'], data['resolution']['height']
    density = data.get('meta', {}).get('texture_density', 1)
    for element in elements.values():
        size = np.asarray(element['to']) - element['from']
        assert (size > 0).all()
        face_sizes = {'north': size[[0, 1]], 'south': size[[0, 1]],
                      'east': size[[2, 1]], 'west': size[[2, 1]],
                      'up': size[[0, 2]], 'down': size[[0, 2]]}
        for name, face in element['faces'].items():
            u, v, U, V = face['uv']
            assert 0 <= u < U <= width and 0 <= v < V <= height
            # Native Blockbench edits intentionally preserve UVs when shaping a face.
            if data['meta'].get('format_version') != '5.0':
                assert (U-u, V-v) == tuple(max(1, math.ceil(n)) * density for n in face_sizes[name])
    clips = {a['name'].split('.')[-1] for a in data['animations']}
    assert {'idle', 'attack', 'emerge', 'death'} <= clips
    for animation in data['animations']:
        for uid, track in animation.get('animators', {}).items():
            # Blockbench keeps a cached animator name after a group rename.
            # UUID is the binding used by both prepare_render and our exporter.
            assert uid in groups, f"Animation references missing group: {uid}"
        for phase in (0, .25, .5, .75, 1):
            posed_bounds(data, (animation, phase * animation['length']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true', help='Render offline model previews after export')
    parser.add_argument('--regenerate', action='store_true', help='Explicitly replace native-authored files with generated base geometry')
    args = parser.parse_args()
    from concept_ecology import build_models as ecology
    from concept_combat import build_models as combat
    from concept_bosses import build_models as bosses
    models = ecology() + combat() + bosses()
    units = {row[2]: row for row in UNITS}
    assert {m.key for m in models} == set(units) and len(models) == 14
    manifest_path = PACK / 'manifest.json'
    manifest = json.loads(manifest_path.read_text('utf-8'))
    records = {r['id']: r for r in manifest['models']}
    from export_public_model_adaptations import SPECS as public_specs
    protected_sources = {spec['legacy_source'] for spec in public_specs.values()}
    backup = PROJECT / 'build/concept-before'
    backup.mkdir(parents=True, exist_ok=True)
    for model in models:
        for suffix in ('.bbmodel', '.png'):
            old = PACK / 'monsters' / (model.key + suffix)
            dest = backup / old.name
            if not dest.exists():
                shutil.copy2(old, dest)
    # Keep bounds for legacy species which are outside these 14 concept units.
    runtime_bounds = json.loads((ASSETS / 'concept_visual_bounds.json').read_text('utf-8'))
    validation = []
    preview_dir = PROJECT / 'build/concept-review/final'
    preview_dir.mkdir(parents=True, exist_ok=True)
    for model in models:
        if model.key in protected_sources:
            # Their canonical sources are the verified public-model MCP projects.
            # Neither the normal rebuild nor --regenerate may restore the old rig.
            continue
        if args.regenerate:
            with concept_palette():
                data, texture, record = model.save(PACK / 'monsters')
        else:
            import base64, io
            from PIL import Image
            data = json.loads((PACK / 'monsters' / (model.key + '.bbmodel')).read_text('utf-8'))
            texture = Image.open(io.BytesIO(base64.b64decode(data['textures'][0]['source'].split(',')[1])))
            texture.save(PACK / 'monsters' / (model.key + '.png'))
            record = dict(records[model.key])
            record.update(cubes=len(data['elements']), atlas=list(texture.size),
                          bones=len(data.get('groups', [])) or record['bones'],
                          animations=[a['name'].split('.')[-1] for a in data['animations']])
        validate(data)
        lo, hi = posed_bounds(data)
        row = units[model.key]
        scale = row[11] * 16 / (hi[1] - lo[1])
        record['file'] = 'monsters/' + model.key + '.bbmodel'
        record['runtime_normalization'] = {'scale': float(scale), 'offset_y': float(-lo[1])}
        record['revision'] = 'blockbench-mcp-detail-2026-11-03' if not args.regenerate else 'generated-concept-base'
        records[model.key].clear()
        records[model.key].update(record)
        size = (hi - lo) * scale / 16
        center = (hi + lo) * scale / 32
        runtime_bounds[row[1]] = {'width': float(size[0]), 'height': float(size[1]),
                                   'depth': float(size[2]), 'center_x': float(center[0]),
                                   'center_z': float(center[2])}
        validation.append({'source': model.key, 'runtime': row[1], 'cubes': len(data['elements']),
                           'bones': record['bones'], 'clips': record['animations'],
                           'runtime_bounds': runtime_bounds[row[1]]})
        if args.preview:
            from PIL import Image
            from render_models import render
            view = render(prepare_render(data), texture, 760, 700, camera=(1, .55, -1))
            background = Image.new('RGB', view.size, (32, 32, 40))
            background.paste(view, (0, 0), view)
            background.save(preview_dir / (row[1] + '.png'))
        print(model.key, record['cubes'], 'cubes', 'bounds', [round(float(n), 2) for n in size], flush=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ASSETS / 'concept_visual_bounds.json').write_text(json.dumps(runtime_bounds, indent=2) + '\n', encoding='utf-8')
    # Import after the manifest update so the exporter reads the new normalization.
    import export_design_assets as exporter
    report_path = PROJECT / 'verification/design_asset_manifest.json'
    report = json.loads(report_path.read_text('utf-8'))
    for source, row in units.items():
        if source in protected_sources:
            continue
        data = exporter.load(source)
        norm = records[source]['runtime_normalization']
        key = 'infected_' + row[1]
        exporter.geo(key, data, norm['scale'], (0, norm['offset_y'], 0))
        exporter.texture(data, ASSETS / 'textures/entity' / (key + '.png'), True, full_crystals=True)
        exporter.animations(key, data, norm['scale'], hit=row[7], ticks=row[8])
        for mapped in report['assets']:
            if mapped.get('source') == source:
                mapped['scale'] = norm['scale']
    from export_public_model_adaptations import export as export_public
    from export_creature_reworks import export as export_reworks
    replacement_rows = export_public(assets=ASSETS) + export_reworks(update_manifest=False)
    replacement_ids = {row['runtime'] for row in replacement_rows}
    report['assets'] = [row for row in report['assets'] if row['runtime'] not in replacement_ids] + replacement_rows
    exporter.write(report_path, report)
    validation.extend(replacement_rows)
    (PROJECT / 'verification/creature_silhouettes.json').write_text(
        json.dumps({'status': 'ASSETS_VALIDATED', 'models': validation,
                    'native_visual': 'pending'}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Exported 14 concept-matched creatures with original gameplay dimensions and attack timing.')


if __name__ == '__main__':
    main()
