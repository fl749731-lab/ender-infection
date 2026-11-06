"""Author shared 1.20.1-compatible architectural prefabs through the installed MCP.

Only rerun when intentionally editing the prefabs. Normal resource generation
reads the committed NBT and needs neither an MCP server nor Python packages.
"""
from hashlib import sha256
import json
from pathlib import Path

from mcp_stdio_client import McpClient

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'tools/institute_architecture'
PREVIEWS = ROOT / 'previews/institute-architecture-v1/mcp'


def fill(client, name, box, state, mode='replace'):
    x, y, z, X, Y, Z = box
    client.call('fill', name=name, x1=x, y1=y, z1=z, x2=X, y2=Y, z2=Z, state=state, mode=mode)


def blocks(client, name, entries):
    client.call('set_blocks', name=name, blocks=[{'x': x, 'y': y, 'z': z, 'state': state} for x, y, z, state in entries])


def research_bench(client):
    """Two working faces, real under-counter storage and a lit extraction hood."""
    name = 'research_bench'
    client.call('new_structure', name=name, size_x=5, size_y=5, size_z=4, minecraft_version='1.20.1')
    fill(client, name, (0, 0, 0, 4, 0, 3), 'light_gray_concrete')
    for z, face in ((1, 'north'), (2, 'south')):
        fill(client, name, (0, 1, z, 4, 1, z), 'smooth_quartz_slab[type=top,waterlogged=false]')
        blocks(client, name, [(x, 1, z, 'ender_infection:lab_freezer[facing=' + face + ']') for x in (0, 4)])
        blocks(client, name, [(1, 2, z, 'ender_infection:lab_monitor[facing=' + face + ']'),
                             (3, 2, z, 'ender_infection:lab_sample_rack[facing=' + face + ']')])
        for x in (0, 4):
            fill(client, name, (x, 2, z, x, 3, z), 'iron_bars')
        fill(client, name, (2, 2, z, 2, 3, z), 'light_blue_stained_glass')
    fill(client, name, (0, 4, 1, 4, 4, 2), 'light_gray_concrete')
    blocks(client, name, [(1, 4, 1, 'sea_lantern'), (3, 4, 2, 'sea_lantern')])
    for z in (0, 3):
        fill(client, name, (0, 0, z, 4, 0, z), 'light_blue_concrete')


def create():
    DEST.mkdir(parents=True, exist_ok=True)
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    print('Connecting to minecraft-structure MCP...', flush=True)
    with McpClient('minecraft-structure') as client:
        print('MCP connected; authoring four native structures.', flush=True)
        name = 'entry_portico'
        client.call('new_structure', name=name, size_x=22, size_y=12, size_z=9, minecraft_version='1.20.1')
        fill(client, name, (0, 0, 0, 21, 0, 8), 'polished_andesite')
        for x in (0, 21):
            fill(client, name, (x, 0, 0, x, 0, 8), 'polished_deepslate')
        for x in (1, 20):
            fill(client, name, (x, 1, 1, x, 10, 2), 'white_concrete')
            fill(client, name, (x, 2, 3, x, 9, 3), 'cyan_stained_glass')
            fill(client, name, (x, 1, 7, x, 5, 7), 'polished_deepslate')
        fill(client, name, (0, 6, 0, 21, 6, 8), 'smooth_quartz_slab[type=top,waterlogged=false]', 'outline')
        fill(client, name, (3, 6, 1, 18, 6, 7), 'gray_stained_glass')
        for x in (2, 7, 14, 19):
            fill(client, name, (x, 6, 0, x, 6, 8), 'polished_deepslate')
        fill(client, name, (1, 10, 1, 20, 10, 2), 'white_concrete')
        fill(client, name, (4, 8, 1, 17, 9, 2), 'polished_deepslate')
        fill(client, name, (4, 7, 2, 17, 7, 2), 'cyan_concrete')
        for x in (3, 18):
            blocks(client, name, [(x, 5, 7, 'sea_lantern'), (x, 9, 2, 'sea_lantern')])
        for z in (2, 5, 8):
            blocks(client, name, [(4, 0, z, 'cyan_concrete'), (17, 0, z, 'cyan_concrete')])

        name = 'atrium_halo'
        client.call('new_structure', name=name, size_x=20, size_y=6, size_z=12, minecraft_version='1.20.1')
        fill(client, name, (0, 0, 0, 19, 0, 11), 'polished_deepslate', 'outline')
        fill(client, name, (2, 0, 2, 17, 0, 9), 'void')
        for z in (0, 11):
            fill(client, name, (1, 1, z, 18, 1, z), 'cyan_stained_glass')
        for x in (0, 19):
            fill(client, name, (x, 1, 1, x, 1, 10), 'cyan_stained_glass')
        for x in (1, 18):
            for z in (1, 10):
                blocks(client, name, [(x, 0, z, 'sea_lantern')])
                fill(client, name, (x, 1, z, x, 5, z), 'iron_bars')

        name = 'atrium_bench'
        client.call('new_structure', name=name, size_x=5, size_y=3, size_z=3, minecraft_version='1.20.1')
        fill(client, name, (0, 0, 0, 4, 0, 2), 'polished_andesite')
        fill(client, name, (1, 1, 1, 3, 1, 1), 'smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
        for x in (0, 4):
            blocks(client, name, [(x, 1, 1, 'gray_concrete'), (x, 2, 1, 'oak_leaves[persistent=true,distance=7,waterlogged=false]')])

        research_bench(client)

        assets = []
        for name in ('entry_portico', 'atrium_halo', 'atrium_bench', 'research_bench'):
            path = DEST / (name + '.nbt')
            client.call('save_structure', name=name, path=str(path))
            print('Saved ' + name + '; rendering preview.', flush=True)
            client.render_to(PREVIEWS / (name + '.png'), name=name, view='se', scale=28, max_size=1200)
            layer = client.call('layer_text', name=name, y=1)
            layer_text = '\n'.join(e.get('text', '') for e in layer['content'])
            (PREVIEWS / (name + '-layer.txt')).write_text('\n'.join(line.rstrip() for line in layer_text.splitlines()).rstrip() + '\n', encoding='utf-8')
            assets.append({'file': path.relative_to(ROOT).as_posix(), 'sha256': sha256(path.read_bytes()).hexdigest()})
        receipt = {'server': client.server.get('serverInfo'), 'target_data_version': 3465,
                   'render_assets': 'Minecraft 1.21.1', 'assets': assets, 'calls': client.calls}
        receipt['path_base'] = 'workspace root; authoring calls used resolved absolute paths'
        for call in receipt['calls']:
            if 'path' in call['arguments']:
                call['arguments']['path'] = Path(call['arguments']['path']).relative_to(ROOT).as_posix()
        (DEST / 'mcp-authoring.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'prefabs': len(assets), 'mcp_calls': len(client.calls), 'previews': str(PREVIEWS)}))


if __name__ == '__main__':
    create()
