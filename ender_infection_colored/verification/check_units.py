import json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1];assets=root/'src/main/resources/assets/ender_infection'
source=(root/'src/main/java/dev/hui/enderinfection/entity/InfectedKind.java').read_text()
units=re.findall(r'^\s+[A-Z_]+\("([a-z_]+)",\s*[\d.]+,\s*[\d.]+,\s*[\d.]+,\s*(\d+),\s*(\d+),',source,re.M)
assert len(units)==len(re.findall(r'^\s+[A-Z_]+\("',source,re.M))
for kind,hit,length in units:
    geometry=json.loads((assets/f'geo/infected_{kind}.geo.json').read_text())['minecraft:geometry'][0]
    bones={b['name'] for b in geometry['bones']}
    aa=json.loads((assets/f'animations/infected_{kind}.animation.json').read_text())['animations']
    assert (assets/f'textures/entity/infected_{kind}.png').is_file()
    assert (assets/f'models/item/infected_{kind}_spawn_egg.json').is_file()
    assert all(a['bones'].keys()<=bones for a in aa.values())
    special=aa[f'animation.infected_{kind}.special']
    assert abs(special['animation_length']-int(length)/20)<1e-6
    assert any(abs(float(t)-int(hit)/20)<1e-6 for t in special['bones']['body']['rotation']),(kind,'impact timing')
    for b in geometry['bones']:
        assert b.get('parent') is None or b['parent'] in bones
        for cube in b.get('cubes',[]):
            assert all(s>0 for s in cube['size'])
            for face in cube['uv'].values():
                dimensions=[geometry['description']['texture_width'],geometry['description']['texture_height']]
                assert all(0<=face['uv'][i] and face['uv'][i]+face['uv_size'][i]<=dimensions[i] for i in (0,1))
    print('PASS:',kind,'model, animation, texture, egg, hit timing')
for name in ['infected_nest','infected_nest_rooted']:
    block=json.loads((assets/f'models/block/{name}.json').read_text())
    assert block['elements']
    for element in block['elements']:
        assert all(-16<=v<=32 for v in element['from']+element['to'])
        assert all(0<=v<=16 for f in element['faces'].values() for v in f['uv'])
print('PASS: both nest block geometries, UV and resource references')
