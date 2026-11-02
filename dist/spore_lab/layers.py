import sys, collections
exec(open(r'E:\mc\dist\spore_lab\parse.py').read().split("nbt=load")[0])
nbt=load(sys.argv[1])
size=nbt['size']; palette=nbt['palette']
grid={}
for b in nbt['blocks']:
    x,y,z=b['pos']; grid[(x,y,z)]=palette[b['state']]['Name'].split(':')[-1]
layers=collections.Counter()
for (x,y,z) in grid: layers[y]+=1
print('=== 每层方块数 ===')
for y in sorted(layers):
    bar='#'*int(layers[y]/60)
    print(f'y={y:3d} {layers[y]:6d} {bar}')
print()
names=collections.Counter(grid.values())
print('=== 特殊方块 ===')
for n in sorted(names):
    if any(k in n for k in ('spawner','cabinet','incubator','surgery','bed','door','torch','lantern','lamp','sign','button','lever','trapdoor','stairs','slab','pane','bars','vent','chest','barrel','hopper','cauldron','brewing','anvil','enchant')):
        print(f'  {names[n]:5d}  {n}')