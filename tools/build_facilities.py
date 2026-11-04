"""Six original biome-specific facilities, with open halls, atria, pits and damaged bays."""
import json,gzip,struct,hashlib
from collections import Counter
from design_catalog import DATA,PROJECT
from export_design_assets import write
from lab_rooms import Room,BUILDERS,LABELS,furniture_loot_nbt
STR=lambda s:(8,s)
COM=lambda d:(10,d)
LST=lambda t,l:(9,(t,l))
INT=lambda n:(3,n)
def string(s):
 b=s.encode();return struct.pack('>H',len(b))+b
def payload(tag):
 t,v=tag
 if t==1:return struct.pack('>b',v)
 if t==2:return struct.pack('>h',v)
 if t==3:return struct.pack('>i',v)
 if t==8:return string(v)
 if t==9:return bytes([v[0]])+struct.pack('>i',len(v[1]))+b''.join(payload((v[0],n)) for n in v[1])
 if t==10:return b''.join(bytes([a])+string(k)+payload((a,b)) for k,(a,b) in v.items())+b'\0'
 raise ValueError(t)
# A different plan and height for each supported biome, not one template recoloured.
SPECS=[
 {'key':'research_lab','command':'research','name':'菌体温室研究所','biome':'mushroom_fields','size':[40,16,36],'levels':2,'layout':'greenhouse'},
 {'key':'wetland_lab','command':'swamp','name':'湿式收容与泵站','biome':'swamp','size':[42,16,34],'levels':2,'layout':'flooded_L'},
 {'key':'forest_lab','command':'forest','name':'地下断层研究站','biome':'dark_forest','size':[42,21,34],'levels':3,'layout':'broken_bridges'},
 {'key':'medical_lab','command':'medical','name':'庭院医疗隔离站','biome':'plains','size':[40,16,34],'levels':2,'layout':'courtyard'},
 {'key':'cryo_lab','command':'snow','name':'低温环形封存站','biome':'snowy_taiga','size':[32,16,40],'levels':2,'layout':'ring'},
 {'key':'badlands_lab','command':'badlands','name':'采掘竖井封存设施','biome':'badlands','size':[44,21,36],'levels':3,'layout':'excavation'}]
class Facility:
 def __init__(self,spec):
  self.spec=spec;self.w,self.h,self.d=spec['size'];self.depth=spec['levels']*5;self.grid={};self.rooms=[];self.damage=[]
  self.furniture_category={'research':'research','swamp':'engineering','forest':'engineering','medical':'medical','snow':'research','badlands':'engineering'}[spec['command']]
  self.entry=(self.w//2,self.depth+1,3);self.lift=(self.w//2,5)
 def put(self,x,y,z,key='air',props=None,nbt=None):
  assert 0<=x<self.w and 0<=y<self.h and 0<=z<self.d,(self.spec['key'],x,y,z,key)
  if ':' not in key:key='minecraft:'+key if key in {'air','glass','sea_lantern','iron_bars','chest','ladder','spawner'} else 'ender_infection:'+key
  if nbt is None:nbt=furniture_loot_nbt(key,self.furniture_category)
  self.grid[x,y,z]=(key,props or {},nbt)
 def fixture(self,x,y,z,key,face='north'):
  base=key.replace('_eroded','');height=3 if base=='incubator' else 2 if base in {'lab_door','lab_cabinet','surgery_table','lab_workbench','lab_window'} else 1;half=1 if base in {'surgery_table','lab_workbench','lab_window'} else 0
  self.put(x,y,z,key,{'facing':face,'open':'false'});dx,dz={'north':(1,0),'south':(-1,0),'east':(0,1),'west':(0,-1)}[face]
  for u in range(-half,half+1):
   for v in range(height):
    if u or v:self.put(x+dx*u,y+v,z+dz*u,'lab_fixture_part',{'facing':face,'part_x':str(u+1),'part_y':str(v)})
 def shell(self):
  for x in range(self.w):
   for y in range(self.h):
    for z in range(self.d):self.put(x,y,z)
  for floor in range(self.spec['levels']):
   y=floor*5
   for x in range(self.w):
    for z in range(self.d):
     self.put(x,y,z,'lab_tile_dark' if x in (0,self.w-1) or z in (0,self.d-1) else 'lab_tile_light')
     if x in (0,self.w-1) or z in (0,self.d-1):
      for h in range(1,5):self.put(x,y+h,z,'lab_tile_light')
   for x in range(4,self.w-2,7):
    for z in range(4,self.d-2,7):self.put(x,y+4,z,'lab_light_panel',{'facing':'north'})
  for x in range(self.w):
   for z in range(self.d):self.put(x,self.depth,z,'lab_tile_dark')
 def zone(self,key,x,z,floor,mirror=False,opened=False,damaged=False):
  y=floor*5
  # A closed service cell or an L-backed open work bay. The latter merges with public halls.
  for u in range(14):
   for v in range(10):
    boundary=u in (0,13) or v in (0,9)
    if not boundary:continue
    if opened and (v==9 or u==(0 if mirror else 13)):continue
    for h in range(1,5):self.put(x+u,y+h,z+v,'lab_tile_light')
  facility=self
  class Zone(Room):
   def pos(self,u,v,h=1):return (x+1+(11-u if self.side else u),y+h,z+1+v)
  r=Zone({'put':self.put,'fixture':self.fixture,'STR':STR,'COM':COM,'LST':LST},floor,0,int(mirror),key)
  r.common();BUILDERS[key](r)
  door=(x if mirror else x+13,y+1,z+3);face='west' if mirror else 'east'
  if not opened:
   self.fixture(*door,'lab_door_eroded' if damaged else 'lab_door',face)
   self.put(door[0],y+3,door[2],'lab_tile_dark');self.put(door[0],y+4,door[2],'lab_tile_dark')
  if damaged:
   # A breached side wall and a fractured floor. Never cut through a fixture or its reserved parts.
   for u in (5,6,7):
    for h in (2,3):self.put(x+u,y+h,z,'minecraft:air')
   for u,v in ((5,4),(6,4),(5,5)):
    above=self.grid.get((x+u,y+1,z+v),('minecraft:air',{},None))[0]
    if above=='minecraft:air':self.put(x+u,y,z+v,'minecraft:air' if y else 'minecraft:cracked_stone_bricks')
   self.put(x+4,y+1,z+8,'infected_tendrils');self.damage.append({'room':key,'floor':floor,'breach':[x+6,y+2,z]})
  self.rooms.append({'theme':key,'name':LABELS[key],'floor':floor,'origin':[x,y,z],'open_bay':opened,'damaged':damaged,'door':list(door) if not opened else None,'chest':r.chest,'features':r.features})
 def atrium(self,rect,kind):
  a,b,c,d=rect
  for floor in range(1,self.spec['levels']):
   y=floor*5
   for x in range(a,b+1):
    for z in range(c,d+1):self.put(x,y,z)
   for x in range(a-1,b+2):
    for z in (c-1,d+1):self.put(x,y+1,z,'minecraft:iron_bars',{'east':'true','west':'true','north':'false','south':'false','waterlogged':'false'})
   for z in range(c,d+1):
    for x in (a-1,b+1):self.put(x,y+1,z,'minecraft:iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
  if kind=='greenhouse':
   for x in range(a-1,b+2):
    for z in range(c-1,d+2):self.put(x,self.depth,z,'minecraft:glass')
   for x in (a,b):
    for z in range(c,d+1,3):self.put(x,1,z,'minecraft:mycelium');self.put(x,2,z,'minecraft:red_mushroom' if z%2 else 'minecraft:brown_mushroom')
  elif kind=='water':
   for x in range(a,b+1):
    for z in range(c,d+1):self.put(x,1,z,'minecraft:water',{'level':'0'})
   for x in range(a-1,b+2):
    for z in range(c-1,d+2):
     if x in (a-1,b+1) or z in (c-1,d+1):self.put(x,1,z,'minecraft:glass')
  elif kind=='excavation':
   for x in range(a,b+1):
    for z in range(c,d+1):self.put(x,0,z,'minecraft:deepslate' if (x+z)%5 else 'minecraft:amethyst_block')
   for x in range(a-2,b+3):self.put(x,self.depth-1,(c+d)//2,'lab_pipe',{'facing':'north'})
   for y in range(2,self.depth-1):self.put(a,self.depth-y,(c+d)//2,'minecraft:iron_bars',{'north':'false','south':'false','east':'false','west':'false','waterlogged':'false'})
  elif kind=='garden':pass
  else:
   for x in range(a,b+1):
    for z in range(c,d+1):
     if (x+z)%4==0:self.put(x,1,z,'minecraft:blue_ice' if kind=='ice' else 'crystal_crust')
 def access(self):
  x,z=self.lift
  # Clear landings first; an operable platform links all storeys and the surface vestibule.
  for floor in range(self.spec['levels']+1):
   y=floor*5
   for dx in (-1,0,1):
    for dz in (-1,0,1):
     for h in (1,2,3):self.put(x+dx,y+h,z+dz)
   self.put(x,y,z,'lab_lift')
  for y in range(1,self.depth+1):
   self.put(x+2,y,z,'lab_access_ladder',{'facing':'north','waterlogged':'false'});self.put(x+2,y,z+1,'lab_tile_dark')
  for dx in range(-3,4):
   for dz in range(-2,4):
    for h in range(1,5):
     if abs(dx)==3 or dz==3:self.put(x+dx,self.depth+h,z+dz,'lab_tile_light')
    self.put(x+dx,self.depth+5,z+dz,'lab_tile_dark')
  for dx in (-2,-1,0,1,2):
   for h in range(1,5):
    if dx==0 and h<3:continue
    self.put(x+dx,self.depth+h,z-2,'minecraft:light_gray_stained_glass' if abs(dx)==1 and h==2 else 'lab_tile_light')
  self.fixture(x,self.depth+1,z-2,'lab_door')
  for dx in (-2,2):self.put(x+dx,self.depth+4,z,'lab_light_panel',{'facing':'north'})
  self.put(x,self.depth+3,z+2,'lab_monitor',{'facing':'north'})
  for floor in range(self.spec['levels']+1):
   y=floor*5;self.put(x,y+3,z+1,'lab_tile_dark')
   texts=[self.spec['name'][:8],f'B{self.spec["levels"]-floor} / 升降平台','右键上行，顶层下行','潜行右键下行']
   self.put(x,y+3,z,'minecraft:birch_wall_sign',{'facing':'north','waterlogged':'false'},{'id':STR('minecraft:sign'),'front_text':COM({'messages':LST(8,[json.dumps({'text':t},ensure_ascii=False) for t in texts]),'color':STR('black'),'has_glowing_text':(1,0)}),'is_waxed':(1,1)})
 def save(self):
  palette=[];lookup={};blocks=[]
  for pos,(name,props,nbt) in self.grid.items():
   key=(name,tuple(sorted(props.items())))
   if key not in lookup:
    lookup[key]=len(palette);state={'Name':STR(name)}
    if props:state['Properties']=COM({k:STR(str(v)) for k,v in props.items()})
    palette.append(state)
   b={'pos':LST(3,list(pos)),'state':INT(lookup[key])}
   if nbt:b['nbt']=COM(nbt)
   blocks.append(b)
  data={'DataVersion':INT(3465),'size':LST(3,self.spec['size']),'palette':LST(10,palette),'blocks':LST(10,blocks),'entities':LST(10,[])}
  raw=b'\x0a\0\0'+payload(COM(data));path=DATA/'structures'/f'{self.spec["key"]}.nbt';path.write_bytes(gzip.compress(raw,mtime=0))
  return {**self.spec,'depth':self.depth,'entrance':list(self.entry),'lift':[self.lift[0],0,self.lift[1]],'rooms':self.rooms,'damage':self.damage,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
def populate(f):
 k=f.spec['command'];levels=f.spec['levels']
 if k=='research':
  for floor in range(levels):
   for theme,x,z,mirror,opened in [('botany',0,0,False,True),('culture',26,0,True,False),('microscopy' if floor else 'receiving',0,26,False,True),('archive' if floor else 'wet_lab',26,26,True,False)]:f.zone(theme,x,z,floor,mirror,opened,floor==0 and theme=='wet_lab')
  f.atrium((17,22,12,23),'greenhouse')
 elif k=='swamp':
  for floor in range(levels):
   for theme,x,z,mirror,opened in [('filtration' if not floor else 'microscopy',0,0,False,False),('pumps' if not floor else 'wet_lab',0,14,False,True),('decontamination' if not floor else 'containment',28,24,True,False)]:f.zone(theme,x,z,floor,mirror,opened,theme in ('pumps','containment'))
  f.atrium((20,26,12,22),'water')
 elif k=='forest':
  for floor in range(levels):
   for theme,x,z,mirror,opened in [('server_room',0,0,False,floor==1),('control',28,0,True,True),('robotics' if floor==1 else 'loading',0,24,False,True),('nexus' if not floor else 'security',28,24,True,False)]:f.zone(theme,x,z,floor,mirror,opened,(floor==2 and theme=='loading') or theme=='nexus')
  f.atrium((18,23,13,20),'crystal')
 elif k=='medical':
  for floor in range(levels):
   for theme,x,z,mirror,opened in [('triage' if not floor else 'pharmacy',0,0,False,True),('ward',0,12,False,True),('mortuary' if not floor else 'isolation',0,24,False,False),('surgery',26,0,True,False),('decontamination' if not floor else 'lounge',26,24,True,True)]:f.zone(theme,x,z,floor,mirror,opened,theme=='mortuary')
  f.atrium((18,21,13,20),'garden')
 elif k=='snow':
  for floor in range(levels):
   for theme,x,z,mirror,opened in [('airlock',0,0,False,False),('cryo_vault',18,0,True,False),('power' if not floor else 'control',0,30,False,True),('cold_store',18,30,True,False)]:f.zone(theme,x,z,floor,mirror,opened,floor==0 and theme=='cold_store')
  f.atrium((13,18,16,23),'ice')
 elif k=='badlands':
  for floor in range(levels):
   for theme,x,z,mirror,opened in [('loading',0,0,False,True),('robotics',30,0,True,True),('reactor' if not floor else 'containment',0,26,False,False),('nexus' if not floor else 'server_room',30,26,True,False)]:f.zone(theme,x,z,floor,mirror,opened,theme in ('nexus','loading') and floor==0)
  f.atrium((17,26,13,22),'excavation')
def furnish_public_spaces(f):
 k=f.spec['command']
 if k=='research':
  for z in (13,18,23):
   for x in (17,22):
    f.put(x,1,z,'minecraft:mycelium');f.put(x,2,z,'minecraft:red_mushroom')
    for dx in (-1,1):
     for y in (1,2,3):f.put(x+dx,y,z,'minecraft:glass')
    f.put(x,4,z,'lab_light_panel',{'facing':'north'})
  for z in (12,24):
   f.put(14,1,z,'lab_freezer',{'facing':'east'});f.put(14,2,z,'lab_sample_rack',{'facing':'east'});f.put(25,1,z,'lab_shelf',{'facing':'west'})
 elif k=='swamp':
  for x in (22,23,24):
   for z in range(12,23):f.put(x,5,z,'lab_floor_grate',{'facing':'north'})
   for z in (11,23):f.put(x,6,z)
  for x in (21,25):
   for z in range(12,23):f.put(x,6,z,'minecraft:iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
  for z in (11,15,19,23):
   for y in (1,2):f.put(18,y,z,'lab_pipe',{'facing':'east'})
   f.put(17,1,z,'lab_freezer',{'facing':'east'});f.put(17,3,z,'lab_monitor',{'facing':'east'})
  for z in range(11,24):f.put(19,3,z,'lab_pipe',{'facing':'east'})
  for x in range(20,27):f.put(x,4,11,'lab_pipe',{'facing':'north'})
 elif k=='forest':
  for x in range(17,25):
   for z in (16,17,18):f.put(x,5,z,'lab_tile_dark');f.put(x,6,z)
  for z in range(12,22):
   for x in (20,21,22):f.put(x,10,z,'lab_tile_dark');f.put(x,11,z)
  for x in range(18,24):
   for z in (15,19):f.put(x,6,z,'minecraft:iron_bars',{'east':'true','west':'true','north':'false','south':'false','waterlogged':'false'})
  for z in range(13,21):
   for x in (19,23):f.put(x,11,z,'minecraft:iron_bars',{'north':'true','south':'true','east':'false','west':'false','waterlogged':'false'})
  f.put(20,10,17);f.put(21,10,17,'minecraft:cracked_stone_bricks')
  for x,y,z in [(18,1,14),(19,1,14),(18,2,14),(24,1,19),(25,1,19)]:f.put(x,y,z,'minecraft:cracked_stone_bricks')
  for z in (11,22):
   f.put(26,1,z,'lab_storage_crate',{'facing':'west'});f.put(26,2,z,'lab_storage_crate',{'facing':'west'});f.put(26,3,z,'lab_monitor',{'facing':'west'})
 elif k=='medical':
  for x in (18,21):
   for z in range(13,21,3):f.put(x,1,z,'minecraft:moss_block');f.put(x,2,z,'minecraft:azalea')
  for x in (19,20,21):f.put(x,1,10,'lab_freezer',{'facing':'south'});f.put(x,2,10,'lab_monitor',{'facing':'south'})
  for x in (16,23):
   for z in (9,12,22):f.put(x,1,z,'lab_chair',{'facing':'east' if x==16 else 'west'})
  for x in (17,22):
   for z in (11,22):f.put(x,1,z,'minecraft:sea_lantern')
 elif k=='snow':
  for x in (14,17):
   for z in (18,21):
    for y in range(1,9):f.put(x,y,z,'lab_tile_dark' if y%3 else 'minecraft:blue_ice')
  for y in range(1,9):
   for x,z in [(15,18),(16,18),(15,21),(16,21),(14,19),(14,20),(17,19),(17,20)]:f.put(x,y,z,'minecraft:light_blue_stained_glass')
  f.put(15,1,19,'crystal_crust');f.put(16,1,20,'crystal_crust')
  for y in (2,4,6):f.put(15,y,19,'minecraft:amethyst_cluster',{'facing':'up','waterlogged':'false'})
  for x in range(12,20):f.put(x,9,19,'lab_pipe',{'facing':'north'})
  for z in (13,26):f.put(20,1,z,'lab_freezer',{'facing':'west'});f.put(20,2,z,'lab_monitor',{'facing':'west'})
 elif k=='badlands':
  for x in range(15,29):
   for y in (13,14):f.put(x,y,17,'minecraft:orange_concrete' if y==14 else 'lab_tile_dark')
  for y in range(3,13):f.put(19,y,17,'minecraft:chain',{'axis':'y','waterlogged':'false'})
  f.put(19,2,17,'lab_storage_crate',{'facing':'north'})
  for x,y,z in [(24,1,18),(25,1,18),(25,2,18),(24,1,19),(21,1,20)]:f.put(x,y,z,'minecraft:deepslate' if y==1 else 'minecraft:amethyst_block')
  for z in (11,24):
   f.put(29,1,z,'lab_storage_crate',{'facing':'west'});f.put(29,2,z,'lab_sample_rack',{'facing':'west'})

def build_all():
 reports=[]
 for spec in SPECS:
  f=Facility(spec);f.shell();populate(f);furnish_public_spaces(f);f.access();report=f.save();reports.append(report)
  key=spec['key'];write(DATA/'worldgen/template_pool'/f'{key}.json',{'name':'ender_infection:'+key,'fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':'ender_infection:'+key,'processors':'minecraft:empty','projection':'rigid'}}]})
  write(DATA/'worldgen/structure'/f'{key}.json',{'type':'minecraft:jigsaw','biomes':'#ender_infection:legacy_debug_labs','step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'none','start_pool':'ender_infection:'+key,'size':1,'start_height':{'absolute':-f.depth},'project_start_to_heightmap':'WORLD_SURFACE_WG','max_distance_from_center':80,'use_expansion_hack':False})
  print(key,spec['biome'],len(f.rooms),'zones',len(f.damage),'damaged')
 write(DATA/'tags/worldgen/biome/legacy_debug_labs.json',{'replace':False,'values':[]})
 for s in SPECS:write(DATA/'worldgen/structure_set'/f'{s["key"]}.json',{'structures':[{'structure':'ender_infection:'+s['key'],'weight':1}],'placement':{'type':'minecraft:random_spread','spacing':32,'separation':10,'salt':83264179}})
 for style in ('research','hospital','prison'):write(DATA/'forge/biome_modifier'/f'lab_{style}.json',{'type':'forge:none'})
 write(PROJECT/'verification/facilities.json',{'facilities':reports,'count':len(reports),'room_source_templates':len(BUILDERS),'total_zones':sum(len(r['rooms']) for r in reports)})
 write(PROJECT/'verification/research_lab_layout.json',reports[0])
 write(PROJECT/'verification/laboratory_rooms.json',{'facilities':len(reports),'room_source_templates':len(BUILDERS),'zones':[dict(zone,facility=r['key']) for r in reports for zone in r['rooms']]})
if __name__=='__main__':build_all()
