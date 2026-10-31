import json, re
from pathlib import Path

root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/ender_infection'
geo=json.loads((assets/'geo/ender_mutant_zombie.geo.json').read_text())['minecraft:geometry'][0]
animations=json.loads((assets/'animations/ender_mutant_zombie.animation.json').read_text())['animations']
model=json.loads((root/'art/ender_mutant_zombie_combat.bbmodel').read_text())
names={b['name'] for b in geo['bones']}
assert len(names)==len(geo['bones'])
for b in geo['bones']:
    assert not b.get('parent') or b['parent'] in names
    for cube in b.get('cubes',[]):
        assert all(s>0 for s in cube['size'])
        for face in cube['uv'].values():
            assert all(0<=face['uv'][i]<128 and 0<face['uv_size'][i] and face['uv'][i]+face['uv_size'][i]<=128 for i in (0,1))
for a in animations.values():
    assert set(a['bones']).issubset(names)
    for tr in a['bones'].values():
        for frames in tr.values():
            assert all(0<=float(t)<=a['animation_length'] for t in frames)
spec=(root/'src/main/java/dev/hui/enderinfection/combat/AttackSpec.java').read_text()
for name,hit,duration in re.findall(r'\(\d+, "([a-z_]+)", (\d+), (\d+),',spec):
    a=animations['animation.ender_mutant_zombie.'+name]
    assert abs(a['animation_length']-int(duration)/20)<1e-6
    assert str(int(hit)/20) in a['bones']['torso']['rotation'], (name,hit)
    assert set(a['bones'])==names, 'actions must key the full rig'
# Check UV islands are disjoint in the editor source, not just inside the image.
occupied=set()
for element in model['elements']:
    for face in element['faces'].values():
        u,v,u2,v2=face['uv']
        pixels={(x,y) for x in range(int(u),int(u2)) for y in range(int(v),int(v2))}
        assert not pixels.intersection(occupied),element['name']
        occupied.update(pixels)
for path in (root/'src/main/java').rglob('*.java'):
    if '/client/' not in path.as_posix():
        assert 'import net.minecraft.client.' not in path.read_text()
print(f'PASS: {len(names)} bones, {len(model["elements"])} cubes, {len(animations)} animations; UV, resources, client isolation and hit-frame timing.')
