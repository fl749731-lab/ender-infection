"""Data pack content, recipes and native four-storey laboratory template."""
import json,gzip,struct
from design_catalog import *
from export_design_assets import write,SOURCES
allblocks=[b+suffix for b in BLOCKS.values() for suffix in ('','_eroded')]
for _,key,*_ in UNITS:write(ASSETS/'models/item'/f'infected_{key}_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
for lang in ('zh_cn','en_us'):
 p=ASSETS/'lang'/f'{lang}.json';d=json.loads(p.read_text('utf-8'))
 for _,key,source,label,*_ in UNITS:
  label=label if lang=='zh_cn' else source.replace('_',' ').title();d['entity.ender_infection.infected_'+key]=label;d['item.ender_infection.infected_'+key+'_spawn_egg']=label+('刷怪蛋' if lang=='zh_cn' else ' Spawn Egg')
 for source,key in {**MATERIALS,**WEAPONS}.items():d['item.ender_infection.'+key]=SOURCES[source]['name'] if lang=='zh_cn' else key.replace('_',' ').title()
 for source,key in ARMOR.items():
  for slot,zh in [('helmet','头盔'),('chestplate','胸甲'),('leggings','护腿'),('boots','靴子')]:d['item.ender_infection.'+key+'_'+slot]=(SOURCES[source]['name']+zh) if lang=='zh_cn' else (key+' '+slot).replace('_',' ').title()
 for source,key in BLOCKS.items():
  for suffix,stage in [('','intact'),('_eroded','eroded')]:d['block.ender_infection.'+key+suffix]=SOURCES[source+'_'+stage]['name'] if lang=='zh_cn' else (key+suffix).replace('_',' ').title()
 d['entity.ender_infection.design_crystal']='共鸣晶刺' if lang=='zh_cn' else 'Resonant Crystal';write(p,d)
for key in allblocks:
 write(DATA/'loot_tables/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ender_infection:'+key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(DATA/'loot_tables/blocks/lab_fixture_part.json',{'type':'minecraft:block','pools':[]})
write(ASSETS/'blockstates/lab_fixture_part.json',{'variants':{'':{'model':'ender_infection:block/lab_fixture_part'}}})
write(ASSETS/'models/block/lab_fixture_part.json',{'textures':{'particle':'ender_infection:block/design/lab_tile_light'},'elements':[]})
write(PROJECT/'src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['ender_infection:'+key for key in allblocks]+['ender_infection:lab_fixture_part']})

def recipe(key,pattern,materials,count=1):
 def ingredient(v):return {'item':v if ':' in v else 'ender_infection:'+v}
 write(DATA/'recipes'/f'{key}.json',{'type':'minecraft:crafting_shaped','category':'equipment' if key in WEAPONS.values() or 'carapace' in key or 'brood_heavy' in key else 'building','pattern':pattern,'key':{k:ingredient(v) for k,v in materials.items()},'result':{'item':'ender_infection:'+key,'count':count}})
for key in BLOCKS.values():
 if key in ('lab_tile_light','lab_tile_dark','lab_tile','lab_checker'):recipe(key,['II','SS'],{'I':'minecraft:iron_ingot','S':'minecraft:stone'},8)
 elif key=='lab_slab':recipe(key,['TTT'],{'T':'lab_tile_light'},6)
 elif key=='lab_stair':recipe(key,['T  ','TT ','TTT'],{'T':'lab_tile_light'},4)
 else:recipe(key,['ITI','TCT','ITI'],{'I':'minecraft:iron_ingot','T':'lab_tile_light','C':'minecraft:glass' if key in ('incubator','lab_window') else 'minecraft:redstone'})
 write(DATA/'recipes'/f'{key}_eroded.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':'ender_infection:'+key},{'item':'ender_infection:infected_tissue'}],'result':{'item':'ender_infection:'+key+'_eroded'}})
recipe('prism_spear',['  S',' I ','I  '],{'S':'resonant_shard','I':'minecraft:iron_ingot'})
recipe('earthsplitter_hammer',['CCC','CSC',' I '],{'C':'chitin_plate','S':'resonance_organ','I':'minecraft:iron_ingot'})
recipe('crystal_spike_crossbow',['CSC','IBI',' S '],{'C':'chitin_plate','S':'resonant_shard','I':'minecraft:iron_ingot','B':'minecraft:crossbow'})
recipe('resonance_staff',[' HO',' IS','I  '],{'H':'mother_heart','O':'resonance_organ','I':'mycelium_bundle','S':'resonant_shard'})
for armor in ARMOR.values():
 for slot,pattern in {'helmet':['CCC','C C'],'chestplate':['C C','COC','CCC'],'leggings':['CCC','C C','C C'],'boots':['C C','C C']}.items():
  mats={'C':'chitin_plate'}
  if slot=='chestplate':mats['O']='resonance_organ'
  if armor=='brood_heavy':
   # Upgrade the matching light piece without duplicating armour.
   write(DATA/'recipes'/f'{armor}_{slot}.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':[{'item':'ender_infection:crystal_carapace_'+slot},{'item':'ender_infection:mother_heart'},{'item':'ender_infection:condensed_biomass'},{'item':'ender_infection:chitin_plate'}],'result':{'item':f'ender_infection:{armor}_{slot}'}})
  else:recipe(armor+'_'+slot,pattern,mats)
loot=DATA/'loot_tables/chests/infected_lab.json';d=json.loads(loot.read_text('utf-8'));generated={'ender_infection:'+k for k in ['mycelium_bundle','chitin_plate','spore_pod','condensed_biomass','resonance_organ','resonant_shard']};d['pools']=[p for p in d['pools'] if {e.get('name') for e in p.get('entries',[])}!=generated];d['pools'].append({'rolls':{'min':2,'max':4},'entries':[{'type':'minecraft:item','name':'ender_infection:'+key,'weight':2 if key=='resonance_organ' else 5,'functions':[{'function':'minecraft:set_count','count':{'min':1,'max':3}}]} for key in ['mycelium_bundle','chitin_plate','spore_pod','condensed_biomass','resonance_organ','resonant_shard']]});write(loot,d)
# Minimal NBT writer: explicit typed values, Java Edition structure format.
def string(s):b=s.encode('utf-8');return struct.pack('>H',len(b))+b
INT=lambda n:(3,n)
STR=lambda s:(8,s)
COM=lambda d:(10,d)
LST=lambda t,l:(9,(t,l))
def payload(tag):
 t,v=tag
 if t==3:return struct.pack('>i',v)
 if t==8:return string(v)
 if t==9:
  ty,values=v;return bytes([ty])+struct.pack('>i',len(values))+b''.join(payload((ty,value)) for value in values)
 if t==10:return b''.join(bytes([ty])+string(name)+payload((ty,value)) for name,(ty,value) in v.items())+b'\0'
 raise ValueError(t)
blocks={}
def put(x,y,z,name='air',props=None,nbt=None):
 if not 0<=x<32 or not 0<=y<26 or not 0<=z<28:raise ValueError((x,y,z))
 if ':' not in name:name=('minecraft:' if name in ('air','sea_lantern','glass','ladder','chest','spawner','stone_bricks','iron_bars') else 'ender_infection:')+name
 blocks[x,y,z]=(name,props or {},nbt)
def fixture(x,y,z,key,facing='north'):
 base=key.replace('_eroded','');h=3 if base=='incubator' else 2 if base in ('lab_door','lab_cabinet','surgery_table','lab_workbench','lab_window') else 1;half=1 if base in ('surgery_table','lab_workbench','lab_window') else 0
 put(x,y,z,key,{'facing':facing,'open':'false'})
 direction={'north':(1,0),'south':(-1,0),'east':(0,1),'west':(0,-1)}[facing]
 for dx in range(-half,half+1):
  for dy in range(h):
   if dx or dy:put(x+direction[0]*dx,y+dy,z+direction[1]*dx,'lab_fixture_part',{'facing':facing,'part_x':str(dx+1),'part_y':str(dy)})
for x in range(32):
 for y in range(26):
  for z in range(28):put(x,y,z)
for floor in range(4):
 y=floor*5
 for x in range(32):
  for z in range(28):
   eroded=(floor>=2 and (x*3+z*5+floor)%19<3)
   put(x,y,z,'lab_checker_eroded' if eroded else 'lab_checker')
   if x in (0,31) or z in (0,27) or x in (13,18) or z in (9,18) and (x<14 or x>17):
    for dy in range(1,5):put(x,y+dy,z,'lab_tile_light_eroded' if eroded else 'lab_tile_light')
 # Corridor access to the six rooms; one functional door plus window per room.
 for wall,face in [(13,'east'),(18,'west')]:
  for zi in (3,12,21):
   fixture(wall,y+1,zi,'lab_door_eroded' if floor>=2 else 'lab_door',face)
   for yy in (y+3,y+4):put(wall,yy,zi,'lab_tile_dark')
   for zz in (zi+3,zi+4):
    for yy in (y+2,y+3):put(wall,yy,zz,'glass')
 for z in (4,13,22):
  put(15,y+4,z,'sea_lantern');put(16,y+4,z,'sea_lantern')
for x in range(32):
 for z in range(28):put(x,20,z,'lab_tile_dark')
# Rooms have individual purposes and non-identical furniture/partition arrangements.
from lab_rooms import build_rooms
rooms=build_rooms({'put':put,'fixture':fixture,'STR':STR,'COM':COM,'LST':LST})
# Three connected stair flights; keep headroom through each upper floor.
for x in (2,3):
 for z in range(21,26):
  for yy in range(1,19):put(x,yy,z)
 for floor in range(3):
  for step in range(5):put(x,floor*5+1+step,25-step,'lab_stair',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
for floor in range(4):
 for z in range(21,25):
  for x in (1,4):put(x,floor*5+1,z,'iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
# Surface vestibule: a framed one-block door, sidelights and a solid lintel.
for y in range(1,21):
 put(16,y,25,'ladder',{'facing':'north','waterlogged':'false'});put(16,y,26,'lab_tile_light')
 put(15,y,25);put(17,y,25)
for x in range(14,19):
 for z in range(23,28):
  for y in range(21,25):
   if x in (14,18) or z==27:put(x,y,z,'lab_tile_light')
  put(x,25,z,'lab_tile_dark')
for x in (15,16,17):
 for y in range(21,25):
  if x==16 and y<23:continue
  put(x,y,23,'minecraft:light_gray_stained_glass' if x!=16 and y==22 else 'lab_tile_dark' if y==23 else 'lab_tile_light')
fixture(16,21,23,'lab_door','north')
# Flush ceiling lights, not hanging one-metre lantern cubes.
for x in (15,17):put(x,24,24,'lab_light_panel',{'facing':'north'})
put(16,24,26,'lab_monitor',{'facing':'north'})
put(16,24,22,'minecraft:birch_wall_sign',{'facing':'north','waterlogged':'false'},{'id':STR('minecraft:sign'),'front_text':COM({'messages':LST(8,[json.dumps({'text':t},ensure_ascii=False) for t in ['末影研究设施','入口 / ENTRY','门禁与隔离区','B1—B4']]),'color':STR('black'),'has_glowing_text':(1,0)}),'is_waxed':(1,1)})
# Safety rail around the ladder opening with an approach from the side.
for x in (15,17):put(x,21,25,'iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
# Limited native spawners: configurable vanilla mechanics, not unbounded tick spawning.
for x,y,z,mob in [(10,11,6,'infected_forager'),(22,16,15,'infected_throat_spitter')]:
 put(x,y,z,'spawner',{}, {'id':STR('minecraft:mob_spawner'),'Delay':(2,300),'MinSpawnDelay':(2,400),'MaxSpawnDelay':(2,800),'SpawnCount':(2,1),'MaxNearbyEntities':(2,3),'RequiredPlayerRange':(2,16),'SpawnRange':(2,3),'SpawnData':COM({'entity':COM({'id':STR('ender_infection:'+mob)})})})
# Additional primitive NBT types used by block entities.
oldpayload=payload
def payload(tag):
 if tag[0]==1:return struct.pack('>b',tag[1])
 if tag[0]==2:return struct.pack('>h',tag[1])
 return oldpayload(tag)
palette=[];ids={};records=[]
for pos,(name,props,nbt) in blocks.items():
 k=(name,tuple(sorted(props.items())))
 if k not in ids:
  ids[k]=len(palette);p={'Name':STR(name)}
  if props:p['Properties']=COM({key:STR(v) for key,v in props.items()})
  palette.append(p)
 r={'pos':LST(3,list(pos)),'state':INT(ids[k])}
 if nbt:r['nbt']=COM(nbt)
 records.append(r)
root={'DataVersion':INT(3465),'size':LST(3,[32,26,28]),'palette':LST(10,palette),'blocks':LST(10,records),'entities':LST(10,[])}
file=DATA/'structures/research_lab.nbt';file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(gzip.compress(b'\x0a\0\0'+payload(COM(root)),mtime=0))
write(DATA/'worldgen/template_pool/research_lab.json',{'name':'ender_infection:research_lab','fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':'ender_infection:research_lab','processors':'minecraft:empty','projection':'rigid'}}]})
write(DATA/'worldgen/structure/research_lab.json',{'type':'minecraft:jigsaw','biomes':['minecraft:mushroom_fields','minecraft:dark_forest','minecraft:swamp'],'step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'none','start_pool':'ender_infection:research_lab','size':1,'start_height':{'absolute':-20},'project_start_to_heightmap':'WORLD_SURFACE_WG','max_distance_from_center':80,'use_expansion_hack':False})
write(DATA/'worldgen/structure_set/research_lab.json',{'structures':[{'structure':'ender_infection:research_lab','weight':1}],'placement':{'type':'minecraft:random_spread','spacing':32,'separation':10,'salt':83264179}})
write(DATA/'forge/biome_modifier/lab_research.json',{'type':'forge:none'})
write(PROJECT/'verification/research_lab_layout.json',{'size':[32,26,28],'floors':4,'rooms':24,'unique_room_layouts':24,'detail_models':10,'floor_y':[0,5,10,15],'surface_entrance_y':21,'palette':len(palette),'blocks':len(records),'spawners':2,'loot_chests':24,'native_structure':'ender_infection:research_lab'})
print('Generated laboratory:',len(records),'blocks,',len(palette),'states;',len(allblocks),'lab blocks')

from build_lab_details import export_details
export_details()
