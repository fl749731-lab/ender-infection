import sys, collections
exec(open(r'E:\mc\dist\spore_lab\parse.py').read().split("nbt=load")[0])
nbt=load(sys.argv[1]); size=nbt['size']; palette=nbt['palette']
grid={}
for b in nbt['blocks']:
    x,y,z=b['pos']; grid[(x,y,z)]=palette[b['state']]['Name']
solid=collections.Counter(); total=size[0]*size[2]
for (x,y,z),n in grid.items():
    if n!='minecraft:air': solid[y]+=1
print('=== 每层非空气占比（找楼层/天花板）===')
for y in range(size[1]):
    r=solid[y]/total
    mark=' <== 楼板' if r>0.55 else ''
    if r>0.02 or y<3: print(f'y={y:3d} {r*100:5.1f}% {"#"*int(r*40)}{mark}')