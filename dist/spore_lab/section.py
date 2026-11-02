import sys
exec(open(r'E:\mc\dist\spore_lab\parse.py').read().split("nbt=load")[0])
nbt=load(sys.argv[1]); size=nbt['size']; palette=nbt['palette']
grid={}
for b in nbt['blocks']:
    x,y,z=b['pos']; grid[(x,y,z)]=palette[b['state']]['Name'].split(':')[-1]
def sym(n):
    if n=='air': return ' '
    if n.startswith('lab_block') or n.startswith('lab_slab') or n.startswith('lab_stair'): return '#'
    if n.startswith('infested_laboratory'): return '%'
    if 'glass' in n: return 'G'
    if 'iron_bars' in n: return 'I'
    if 'vent' in n: return 'V'
    if n=='water': return '~'
    if 'door' in n: return 'D'
    if n in ('stone','andesite','stone_bricks','gray_concrete','birch_planks','stone_brick_slab','stone_brick_stairs','andesite_wall','andesite_slab'): return '='
    if 'fung' in n or 'growth' in n or 'mycel' in n or n in ('sculk','remains','crimson_nylium','rooted_mycelium','bloomfung2','blomfung'): return 'f'
    return '.'
print('=== 纵剖面 x=20（左 z=0 → 右 z=47，上 y=30 → 下 y=0）===')
for y in range(30,-1,-1):
    row=''.join(sym(grid.get((20,y,z),'air')) for z in range(size[2]))
    print(f'y={y:2d} |{row}|')