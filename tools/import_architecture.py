"""Convert attributed upstream structures into native 1.20.1 templates.
HBM assets retain their upstream GPL-3.0 license. Lua inputs stay local.
"""
import argparse,gzip,json,struct,hashlib,math
from pathlib import Path
from collections import Counter
from nbt_codec import load
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'ender_infection_colored/src/main/resources/data/ender_infection'
COLORS='white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split()
DIRECTIONS={0:'east',1:'west',2:'south',3:'north'}
def state(name,**props):
 if ':' not in name:name='minecraft:'+name
 return dict(Name=name,**({'Properties':{k:str(v) for k,v in props.items()}} if props else {}))
def payload(v):
 if isinstance(v,dict):return 10,b''.join(bytes([t])+string(k)+b for k,val in v.items() for t,b in [payload(val)])+b'\0'
 if isinstance(v,str):return 8,string(v)
 if isinstance(v,bool):return 1,struct.pack('>b',v)
 if isinstance(v,int):return 3,struct.pack('>i',v)
 if isinstance(v,float):return 6,struct.pack('>d',v)
 if isinstance(v,list):
  tags=[payload(x) for x in v];t=tags[0][0] if tags else 10
  assert all(q[0]==t for q in tags)
  return 9,bytes([t])+struct.pack('>i',len(tags))+b''.join(b for _,b in tags)
 raise TypeError(type(v))
def string(s):
 b=s.encode('utf8');return struct.pack('>H',len(b))+b
def save(path,root):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(b'\x0a\0\0'+payload(root)[1],mtime=0))
def write(path,root):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(root,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def stairs(name,m):return state(name,facing=DIRECTIONS[m&3],half='top' if m&4 else 'bottom',shape='straight',waterlogged='false')
def slab(name,m):return state(name,type='top' if m&8 else 'bottom',waterlogged='false')
def facing(m):return {2:'north',3:'south',4:'west',5:'east'}.get(m&7,'north')
def legacy(s):
 n=s['Name'];m=int(s.get('Properties',{}).get('meta',0));k=n.split(':')[-1].removeprefix('tile.')
 if n.startswith('hbm:'):
  if k.endswith('_stairs'):
   material='stone_brick_stairs' if 'reinforced' in k else 'smooth_quartz_stairs' if 'asbestos' in k else 'smooth_stone_stairs'
   return stairs('stone_brick_stairs' if material=='smooth_stone_stairs' else material,m)
  if k in ('concrete_slab','brick_slab'):return slab('smooth_stone_slab' if k=='concrete_slab' else 'brick_slab',m)
  if k=='concrete_double_slab':return state('smooth_stone_slab',type='double',waterlogged='false')
  if k in ('door_bunker','door_metal'):
   if m&8:return state('ender_infection:lab_fixture_part',facing='north',part_x=1,part_y=1)
   return state('ender_infection:lab_door',facing={0:'east',1:'south',2:'west',3:'north'}[m&3],open='false')
  if k.startswith('deco_pipe') or k in ('fluid_duct_gauge','red_cable_gauge'):return state('ender_infection:lab_pipe',facing='east' if m in (4,5,8) else 'north')
  if k.startswith('steel_grate'):return state('ender_infection:lab_floor_grate',facing='north')
  if k in ('deco_crt','deco_computer','radiorec','tape_recorder','geiger','charger'):return state('ender_infection:lab_monitor' if k in ('deco_crt','deco_computer') else 'ender_infection:lab_sample_rack',facing=facing(m))
  if k.startswith('crate'):return state('barrel',facing='up',open='false')
  if 'barrel' in k:return state('barrel',facing='up',open='false')
  if k=='reinforced_lamp_off':return state('redstone_lamp',lit='false')
  if k.startswith('spotlight'):return state('air') if k=='spotlight_beam' else state('ender_infection:lab_light_panel',facing=facing(m))
  if k in ('tile_lab','concrete_asbestos'):return state('ender_infection:lab_tile_light')
  if k in ('tile_lab_broken','tile_lab_cracked'):return state('cracked_stone_bricks')
  if k in ('concrete_smooth','reinforced_stone'):return state('smooth_stone')
  if k=='concrete_colored':return state(COLORS[m%16]+'_concrete')
  if k in ('steel_scaffold','steel_wall','steel_poles','steel_corner','pole_top','fence_metal'):return state('iron_bars',north='false',south='false',east='false',west='false',waterlogged='false')
  if k=='pole_satellite_receiver':return state('lightning_rod',facing='up',powered='false',waterlogged='false')
  if k=='dungeon_chain':return state('chain',axis='y',waterlogged='false')
  if k=='rail_narrow':return state('rail',shape='east_west' if m&1 else 'north_south',waterlogged='false')
  if k in ('deco_steel','platemetal','deco_rusty_steel','steel_roof'):return state('ender_infection:lab_tile_dark')
  if k in ('deco_aluminium','deco_lead','deco_tungsten'):return state('iron_block')
  if k in ('gravel_obsidian','block_slag','ore_coal_oil'):return state('deepslate_coal_ore' if k=='ore_coal_oil' else 'gravel')
  if k=='machine_diesel':return state('blast_furnace',facing=facing(m),lit='false')
  if k=='anvil_lead':return state('anvil',facing='north')
  if k=='cable_switch':return state('lever',face='wall',facing=facing(m),powered='false')
  if k=='stalactite':return state('pointed_dripstone',vertical_direction='down',thickness='tip',waterlogged='false')
  if k=='reinforced_glass':return state('gray_stained_glass')
  if k=='bobblehead':return state('skeleton_skull',rotation=m%16)
  if k in ('wand_loot','wand_logic'):return state('air')
  raise ValueError('Unmapped HBM block '+n)
 if k.endswith('_stairs'):return stairs(k,m)
 if k in ('planks','wooden_slab'):
  wood=['oak','spruce','birch','jungle','acacia','dark_oak'][m%8 if m%8<6 else 0];return state(wood+'_planks') if k=='planks' else slab(wood+'_slab',m)
 if k in ('stone_slab','double_stone_slab'):
  mat=['smooth_stone','sandstone','oak','cobblestone','brick','stone_brick','nether_brick','quartz'][m&7]
  return state(mat+'_slab',type='double' if k=='double_stone_slab' else 'top' if m&8 else 'bottom',waterlogged='false')
 if k=='stonebrick':return state(['stone_bricks','mossy_stone_bricks','cracked_stone_bricks','chiseled_stone_bricks'][m%4])
 if k=='brick_block':return state('bricks')
 if k=='web':return state('cobweb')
 if k=='snow_layer':return state('snow',layers=min(8,m+1))
 if k=='skull':return state('skeleton_skull',rotation=0)
 if k in ('stained_glass','stained_glass_pane'):return state(COLORS[m%16]+'_'+k,**({'north':'false','south':'false','east':'false','west':'false','waterlogged':'false'} if k.endswith('pane') else {}))
 if k=='bed':return state('white_bed',facing={0:'south',1:'west',2:'north',3:'east'}[m&3],part='head' if m&8 else 'foot',occupied='false')
 if k=='lever':return state('lever',face='floor' if m%8 in (0,5,6,7) else 'wall',facing={1:'east',2:'west',3:'south',4:'north'}.get(m%8,'north'),powered='true' if m&8 else 'false')
 if k in ('water','lava'):return state(k,level=m)
 if k=='iron_bars':return state(k,north='false',south='false',east='false',west='false',waterlogged='false')
 if k=='cobblestone_wall':return state(k,north='none',south='none',east='none',west='none',up='true',waterlogged='false')
 return state(n)
def connect(grid):
 # Reconstruct connected props absent from the old 1.7 metadata format.
 for (x,y,z),s in list(grid.items()):
  n=s['Name'];props=s.get('Properties',{})
  if n.endswith('_pane') or n.endswith('iron_bars'):
   for side,dx,dz in [('north',0,-1),('south',0,1),('east',1,0),('west',-1,0)]:props[side]='true' if grid.get((x+dx,y,z+dz),{}).get('Name','minecraft:air')!='minecraft:air' else 'false'
  if n=='ender_infection:lab_door':grid[x,y+1,z]=state('ender_infection:lab_fixture_part',facing=props['facing'],part_x=1,part_y=1)

def convert(src,key,title,ground,biomes,local=False):
 r=load(src);old='version' in r and 'DataVersion' not in r
 if 'Regions' in r:return convert_litematic(src,key,title,ground,biomes,local)
 w,h,d=r['size'];grid={tuple(b['pos']):(legacy(r['palette'][b['state']]) if old else r['palette'][b['state']]) for b in r['blocks']};nbt={}
 if old:connect(grid)
 for p,s in grid.items():
  if s['Name']=='minecraft:bedrock':s['Name']='minecraft:cobbled_deepslate'
  if s['Name']=='minecraft:spawner':s.clear();s.update(state('crying_obsidian'))
  if s['Name'] in ('minecraft:chest','minecraft:trapped_chest','minecraft:barrel'):
   nbt[p]={'id':'minecraft:barrel' if s['Name'].endswith('barrel') else 'minecraft:chest','LootTable':'ender_infection:chests/lab_research' if 'hbm' in key else 'minecraft:chests/simple_dungeon'}
 # Keep upstream architecture; do not add random cubes over its silhouette.
 palette=[];lookup={};blocks=[]
 for (x,y,z),s in sorted(grid.items(),key=lambda p:(p[0][1],p[0][0],p[0][2])):
  encoded=json.dumps(s,sort_keys=True)
  if encoded not in lookup:lookup[encoded]=len(palette);palette.append(s)
  b={'pos':[x,y,z],'state':lookup[encoded]}
  if (x,y,z) in nbt:b['nbt']=nbt[x,y,z]
  blocks.append(b)
 dest=(ROOT/'imports/generated' if local else DATA/'structures/imported')/f'{key}.nbt'
 save(dest,dict(DataVersion=3465,size=[w,h,d],palette=palette,blocks=blocks,entities=[]))
 report=dict(key=key,title=title,size=[w,h,d],ground_offset=ground,biomes=biomes,source_sha256=hashlib.sha256(Path(src).read_bytes()).hexdigest(),blocks=len(blocks),palette=len(palette),source_entities_omitted=len(r.get('entities',[])),local=local)
 return report

def convert_litematic(src,key,title,ground,biomes,local=True):
 r=load(src);grid={}
 for region in r['Regions'].values():
  size=region['Size'];pos=region['Position'];sx,sy,sz=[abs(size[k]) for k in 'xyz'];pal=region['BlockStatePalette'];bits=max(2,(len(pal)-1).bit_length());mask=(1<<bits)-1;words=[w&((1<<64)-1) for w in region['BlockStates']]
  for index in range(sx*sy*sz):
   bit=index*bits;slot=bit>>6;shift=bit&63;value=words[slot]>>shift
   if shift+bits>64:value|=words[slot+1]<<(64-shift)
   s=pal[value&mask]
   if s['Name']=='minecraft:air':continue
   x=index%sx;y=index//(sx*sz);z=index//sx%sz
   p=tuple(pos[k]+(min(size[k]+1,0))+q for k,q in zip('xyz',(x,y,z)));grid[p]=s
 minimum=[min(p[i] for p in grid) for i in range(3)];maximum=[max(p[i] for p in grid) for i in range(3)];dims=[maximum[i]-minimum[i]+1 for i in range(3)]
 pal=[];lookup={};blocks=[]
 for p,s in grid.items():
  encoded=json.dumps(s,sort_keys=True)
  if encoded not in lookup:lookup[encoded]=len(pal);pal.append(s)
  blocks.append({'pos':[p[i]-minimum[i] for i in range(3)],'state':lookup[encoded]})
 dest=ROOT/'imports/generated'/f'{key}.nbt';save(dest,dict(DataVersion=3465,size=dims,palette=pal,blocks=blocks,entities=[]))
 return dict(key=key,title=title,size=dims,ground_offset=ground,biomes=biomes,blocks=len(blocks),local=True,source_sha256=hashlib.sha256(Path(src).read_bytes()).hexdigest())

def build():
 entries=[];base=ROOT/'third_party/architecture'
 for filename,key,title,ground,biomes in [
  ('laboratory.nbt','hbm_laboratory','废弃工业实验室 · HBM',5,['plains','flower_forest','mushroom_fields','swamp','dark_forest']),
  ('factory.nbt','hbm_factory','废弃工厂 · HBM',9,['badlands','wooded_badlands','stony_peaks']),
  ('abandoned_arcticstation.nbt','hbm_arctic','极地科考站 · HBM',1,['snowy_plains','snowy_taiga','ice_spikes'])]:
  entries.append(convert(base/'hbm'/filename,key,title,ground,biomes))
 entries.append(convert(base/'church/church_of_sin.nbt','church','废弃教堂 · Church of Sin',2,['birch_forest','old_growth_birch_forest','meadow']))
 for entry in entries:
  key=entry['key'];ident='imported/'+key
  write(DATA/'worldgen/template_pool'/f'{ident}.json',{'name':'ender_infection:'+ident,'fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':'ender_infection:'+ident,'processors':'minecraft:empty','projection':'rigid'}}]})
  write(DATA/'worldgen/structure'/f'{ident}.json',{'type':'ender_infection:surface_building','biomes':['minecraft:'+b for b in entry['biomes']],'step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'beard_thin','start_pool':'ender_infection:'+ident,'ground_offset':entry['ground_offset']})
  write(DATA/'worldgen/structure_set'/f'{ident}.json',{'structures':[{'structure':'ender_infection:'+ident,'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':40,'separation':14,'salt':745919+len(entries)+sum(map(ord,key))}})
 write(DATA/'imported_buildings.json',{'buildings':entries})
 write(ROOT/'ender_infection_colored/verification/imported_buildings.json',{'buildings':entries})
 for e in entries:print(e['key'],e['size'],e['blocks'],'blocks')
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--lua',type=Path);args=parser.parse_args()
 if args.lua:
  r=convert(args.lua,'lua_lab','Lua Lab · LuaKatt / ChaparritaStudios',0,[],True);write(ROOT/'imports/generated/lua_lab.json',r);print(json.dumps(r,ensure_ascii=False))
 else:build()
