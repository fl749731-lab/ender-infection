"""Seal actual movement, native combat/death and unlit glow captures of the spider."""
from __future__ import annotations

import hashlib
import json
import re
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from verify_gametest_log import verify as verify_native

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'previews/creature-rework-v2'
PHASES = ('idle_front', 'idle_back', 'walk_a', 'walk_b', 'attack_windup',
          'attack_impact', 'infection', 'death_start', 'death_end')


def digest(path: Path, normalize_text: bool = False) -> str:
    raw = path.read_bytes()
    if normalize_text:
        raw = raw.replace(b'\r\n', b'\n')
    return hashlib.sha256(raw).hexdigest()


def frame(path: Path) -> dict:
    with Image.open(path) as source:
        im = source.convert('RGB')
    pixels = np.asarray(im).astype(float)
    if min(im.size) < 400 or float(pixels.std()) < .1:
        raise ValueError('Blank or truncated capture: ' + str(path))
    center = pixels[int(im.height * .15):int(im.height * .85),
                    int(im.width * .3):int(im.width * .7)]
    purple = ((center[:, :, 0] > center[:, :, 1] * 1.18)
              & (center[:, :, 2] > center[:, :, 1] * 1.18)
              & (center.max(axis=2) > 25))
    return dict(name=path.stem, size=im.size, sha256=digest(path),
                purple_pixels=int(purple.sum()))


def seal_loader(loader: str, project: str) -> dict:
    logs = ROOT / 'build/creature-rework-checks'
    motion = logs / (project + '-motion.log')
    glow = logs / (project + '-glow.log')
    ai = logs / (project + '-ai.log')
    mt, gt = (p.read_text('utf-8', errors='replace') for p in (motion, glow))
    if mt.count('CREATURE_MOTION_PASS screenshots=9') != 1:
        raise ValueError('Motion completion missing: ' + loader)
    if gt.count('CRYSTAL_GLOW_VISUAL_PASS screenshots=2') != 1:
        raise ValueError('Glow completion missing: ' + loader)
    for text in (mt, gt):
        if re.search(r'OutOfMemoryError|Exception in thread|Failed to parse animation|'
                     r'Unable to load (?:model|texture)|CREATURE_MOTION_FAIL', text, re.I):
            raise ValueError('Native resource/client failure: ' + loader)
    pattern = (r'(?m)^CREATURE_MOTION_CAPTURE spider_(\w+) moved=([\d.Ee+-]+) '
               r'attacking=(true|false) emerging=(true|false) death_time=(\d+) '
               r'target_health=([\d.Ee+-]+)\s*$')
    found = re.findall(pattern, mt)
    if len(found) != len(PHASES) or {x[0] for x in found} != set(PHASES):
        raise ValueError('Missing or repeated motion phase: ' + loader)
    states = {name: dict(moved=float(moved), attacking=attack == 'true',
                        emerging=emerge == 'true', death_time=int(death),
                        target_health=float(health))
              for name, moved, attack, emerge, death, health in found}
    for phase in ('walk_a', 'walk_b'):
        if states[phase]['moved'] <= .05:
            raise ValueError('Walk capture did not actually move: ' + loader + '/' + phase)
    if not all(states[p]['attacking'] for p in ('attack_windup', 'attack_impact')):
        raise ValueError('Attack was not driven by native combat')
    if not 0 <= states['attack_impact']['target_health'] < states['attack_windup']['target_health']:
        raise ValueError('Native target was not damaged')
    if not states['infection']['emerging']:
        raise ValueError('Emergence was not active')
    for phase, minimum in (('death_start', 4), ('death_end', 16)):
        if not minimum <= states[phase]['death_time'] < 20:
            raise ValueError('Invalid native death timing')
    probes = re.findall(r'(?m)^CRYSTAL_GLOW_LIGHT (\w+) block=(\d+) sky=(\d+) probes=(\d+)', gt)
    if (len(probes) != 2 or {p[0] for p in probes} != {'spider_front', 'spider_back'}
            or any(int(b) or int(s) or int(n) < 10 for _, b, s, n in probes)):
        raise ValueError('Glow room did not have zero block and sky light')
    native = verify_native(ai, 36)
    target = DEST / 'runtime' / loader
    target.mkdir(parents=True, exist_ok=True)
    frames = []
    for mode, names, source_dir in (
            ('motion', ['spider_' + p for p in PHASES], 'creature-motion-check'),
            ('glow', ['spider_front', 'spider_back'], 'crystal-glow-check')):
        folder = target / mode
        folder.mkdir(exist_ok=True)
        for name in names:
            source = ROOT / project / 'build' / source_dir / 'screenshots' / (name + '.png')
            row = frame(source)
            if mode == 'glow' and row['purple_pixels'] < 2:
                raise ValueError('No visible crystal emission: ' + loader + '/' + name)
            shutil.copy2(source, folder / source.name)
            frames.append(dict(mode=mode, **row))
    sealed_logs = {}
    for name, source in (('motion', motion), ('glow', glow), ('ai', ai)):
        shutil.copy2(source, target / (name + '.log'))
        sealed_logs[name] = digest(source)
    result = dict(status='PASS', loader=loader, screenshots=len(frames),
                  native_required_passed=native['required_passed'], zero_light_views=2,
                  logs_sha256=sealed_logs, motion_states=states, frames=frames)
    (target / 'verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    return result


def main() -> None:
    DEST.mkdir(parents=True, exist_ok=True)
    receipt = DEST / 'verification.json'
    receipt.unlink(missing_ok=True)
    results = [seal_loader('forge', 'ender_infection_colored'),
               seal_loader('neo', 'ender_infection_neoforge')]
    build_dir = ROOT / 'build/dual-version-validation'
    build = json.loads((build_dir / 'verified-builds.json').read_text('utf-8'))
    if build.get('status') != 'PASS' or build.get('version') != '0.12.0-alpha.8':
        raise ValueError('A matching dual developer build is required')
    for row in build['jars']:
        if digest(ROOT / row['file']) != row['sha256']:
            raise ValueError('Built JAR changed: ' + row['file'])
    build_target = DEST / 'runtime/development'
    build_target.mkdir(exist_ok=True)
    build_logs = {}
    for source in sorted(build_dir.glob('*-build.log')):
        shutil.copy2(source, build_target / source.name)
        build_logs[source.name] = digest(source)
    shutil.copy2(build_dir / 'verified-builds.json', build_target / 'verified-builds.json')
    paths = sorted(set(
        [p for p in (ROOT / 'colored_units/creature_rework_v2').rglob('*') if p.is_file()]
        + [ROOT / 'tools/export_creature_reworks.py']
        + [ROOT / project / 'src/main/java/dev/hui/enderinfection' / tail
           for project in ('ender_infection_colored', 'ender_infection_neoforge')
           for tail in ('client/CreatureMotionCheck.java', 'client/InfectedRenderer.java',
                        'entity/InfectedMob.java')]
        + [ROOT / 'ender_infection_colored/src/main/resources/assets/ender_infection' / tail
           for tail in ('geo/infected_spider.geo.json', 'animations/infected_spider.animation.json',
                        'textures/entity/infected_spider.png', 'textures/entity/infected_spider_glowmask.png')]))
    sources = {p.relative_to(ROOT).as_posix(): dict(
        sha256=digest(p, p.suffix in ('.py', '.java', '.json', '.bbmodel', '.md')),
        scheme='git-lf' if p.suffix in ('.py', '.java', '.json', '.bbmodel', '.md') else 'raw')
        for p in paths}
    result = dict(status='PASS', release_gate=False, screenshots=22,
                  native_required_passed=sum(r['native_required_passed'] for r in results),
                  build_sha256=digest(build_target / 'verified-builds.json'), build_logs_sha256=build_logs,
                  source_hashes=sources, loaders=[{k: v for k, v in r.items() if k != 'frames'} for r in results])
    receipt.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    cells = [('motion', 'idle_front'), ('motion', 'idle_back'), ('motion', 'walk_a'),
             ('motion', 'walk_b'), ('motion', 'attack_impact'), ('motion', 'death_end'),
             ('glow', 'front'), ('glow', 'back')]
    sheet = Image.new('RGB', (4 * 400, 4 * 276), (25, 27, 33))
    draw = ImageDraw.Draw(sheet)
    for li, loader in enumerate(('forge', 'neo')):
        for i, (mode, phase) in enumerate(cells):
            p = DEST / 'runtime' / loader / mode / ('spider_' + phase + '.png')
            with Image.open(p) as source:
                im = source.convert('RGB'); im.thumbnail((400, 250))
            x, y = (i % 4) * 400, (li * 2 + i // 4) * 276
            sheet.paste(im, (x + (400 - im.width) // 2, y))
            draw.text((x + 9, y + 253), loader + ' / ' + mode + ' / ' + phase, fill=(225, 223, 238))
    sheet.save(DEST / 'comparison.jpg', quality=92)
    print(json.dumps({k: v for k, v in result.items() if k not in ('source_hashes', 'loaders')}))


if __name__ == '__main__':
    main()
