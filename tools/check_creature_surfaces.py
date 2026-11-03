"""Find positive-area overlaps between same-facing, coplanar BB model faces.

These are z-fighting candidates, not an occlusion proof: a third solid can hide
an overlap. Models are inspected in their authored resting pose. No files change.
"""
import argparse
from collections import defaultdict
from itertools import combinations
import json
from pathlib import Path
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'colored_units/design_pack_2026-11-02_v2/source'))
from modelkit import prepare_render

FACE_NAMES = {0: ('west', 'east'), 1: ('down', 'up'), 2: ('north', 'south')}


def signed_area(polygon):
    if len(polygon) < 3:
        return 0.0
    return sum(a[0]*b[1]-a[1]*b[0]
               for a, b in zip(polygon, polygon[1:]+polygon[:1])) / 2


def intersection(subject, boundary):
    """Clip a convex polygon against a counterclockwise convex boundary."""
    for a, b in zip(boundary, boundary[1:]+boundary[:1]):
        def side(point):
            return (b[0]-a[0])*(point[1]-a[1])-(b[1]-a[1])*(point[0]-a[0])
        result = []
        for p, q in zip(subject, subject[1:]+subject[:1]):
            sp, sq = side(p), side(q)
            if sp >= -1e-8:
                result.append(p)
            if (sp > 1e-8 and sq < -1e-8) or (sp < -1e-8 and sq > 1e-8):
                result.append(p+(q-p)*(sp/(sp-sq)))
        subject = result
        if not subject:
            break
    return subject


def inspect(path, min_area=.01):
    model = prepare_render(json.loads(path.read_text(encoding='utf-8')))
    planes = defaultdict(list)
    for element in model['elements']:
        lower, upper = np.array(element['from']), np.array(element['to'])
        pose = np.array(element['_pose'])
        for axis in range(3):
            j, k = [i for i in range(3) if i != axis]
            for side in (0, 1):
                face_name = FACE_NAMES[axis][side]
                if element['faces'].get(face_name, {}).get('texture') is None:
                    continue
                normal = np.zeros(3)
                normal[axis] = 1 if side else -1
                normal = np.linalg.inv(pose[:3, :3]).T @ normal
                normal /= np.linalg.norm(normal)
                points = []
                for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    point = lower.copy()
                    point[axis] = upper[axis] if side else lower[axis]
                    point[j] = upper[j] if u else lower[j]
                    point[k] = upper[k] if v else lower[k]
                    points.append(pose[:3, :3]@point+pose[:3, 3])
                distance = np.dot(normal, points[0])
                drop = int(np.argmax(abs(normal)))
                polygon = [np.delete(point, drop) for point in points]
                if signed_area(polygon) < 0:
                    polygon.reverse()
                key = tuple(np.round(normal, 5))+(round(distance, 5),)
                planes[key].append((element['name'], face_name, polygon, abs(normal[drop])))
    overlaps = []
    for plane, faces in planes.items():
        for (name, face, polygon, factor), (other, other_face, boundary, _) in combinations(faces, 2):
            if name == other:
                continue
            area = abs(signed_area(intersection(polygon, boundary)))/factor
            if area > min_area:
                overlaps.append({'first': name, 'first_face': face, 'second': other,
                                 'second_face': other_face, 'area': round(float(area), 6)})
    return {'file': str(path.resolve()), 'overlap_faces': len(overlaps), 'candidates': overlaps}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('paths', nargs='+', type=Path, help='BB model files or directories containing them')
    parser.add_argument('--min-area', type=float, default=.01, help='Minimum overlap in model-units squared')
    parser.add_argument('--max-examples', type=int, default=12)
    parser.add_argument('--json', action='store_true', help='Print all candidates as JSON')
    args = parser.parse_args()
    files = sorted({file for path in args.paths for file in
                    (path.glob('*.bbmodel') if path.is_dir() else [path])})
    if not files:
        parser.error('No .bbmodel files found')
    reports = [inspect(path, args.min_area) for path in files]
    if args.json:
        print(json.dumps(reports, ensure_ascii=False, indent=2))
    else:
        for report in reports:
            print(Path(report['file']).name, 'coplanar overlap faces =', report['overlap_faces'])
            for candidate in report['candidates'][:args.max_examples]:
                print(' ', candidate)
    return int(any(report['overlap_faces'] for report in reports))


if __name__ == '__main__':
    raise SystemExit(main())
