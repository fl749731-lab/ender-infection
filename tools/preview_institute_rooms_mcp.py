"""Render cut-away room previews from the actual generated NeoForge structures."""
from hashlib import sha256
import json
from pathlib import Path
import sys

from mcp_stdio_client import McpClient
from nbt_codec import load

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'ender_infection_neoforge/build/generated/sharedResources/data/ender_infection'
OUTPUT = ROOT / 'previews/institute-architecture-v1/room-previews'


def preview(ids):
    manifest_path = DATA / 'central_institute.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    rooms = {r['id']: r for r in manifest['rooms']}
    cache, results = {}, []
    with McpClient('minecraft-structure') as client:
        for ident in ids:
            room = rooms[ident]
            x, y, z = room['x'], room['y'], room['z']
            w, h, d = room['width'], room['height'], room['length']
            entries = []
            for tile in manifest['tiles']:
                tx, tz = tile['x'], tile['z']
                if tx >= x + w or tx + tile['width'] <= x or tz >= z + d or tz + tile['length'] <= z:
                    continue
                key = tile['template'].split(':', 1)[1]
                if key not in cache:
                    cache[key] = load(DATA / 'structure' / (key + '.nbt'))
                nbt = cache[key]
                for item in nbt['blocks']:
                    a, b, c = item['pos']; a += tx; c += tz
                    if not (x <= a < x + w and y <= b < y + h and z <= c < z + d):
                        continue
                    block = nbt['palette'][item['state']]
                    state = block['Name']
                    if state in ('minecraft:air', 'minecraft:structure_void'):
                        continue
                    props = block.get('Properties', {})
                    if props:
                        state += '[' + ','.join(k + '=' + v for k, v in sorted(props.items())) + ']'
                    entries.append({'x': a - x, 'y': b - y, 'z': c - z, 'state': state})
            client.call('new_structure', name=ident, size_x=w, size_y=h, size_z=d, minecraft_version='1.20.1')
            for start in range(0, len(entries), 1000):
                client.call('set_blocks', name=ident, blocks=entries[start:start + 1000])
            client.render_to(OUTPUT / (ident + '.png'), name=ident, view='se', cut_y=3, scale=26, max_size=1400)
            results.append({'room': ident, 'name': room['name'], 'size': [w, h, d], 'file': ident + '.png'})
            print('Rendered actual room ' + ident, flush=True)
    report = {'source': manifest_path.relative_to(ROOT).as_posix(),
              'manifest_sha256': sha256(manifest_path.read_bytes()).hexdigest(),
              'render_assets': 'Minecraft 1.21.1', 'limits': 'MCP geometry cut-away; entity-rendered furniture is simplified, not a game screenshot.',
              'rooms': results}
    (OUTPUT / 'receipt.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    preview(sys.argv[1:] or ['B2', 'B4', 'A7'])
