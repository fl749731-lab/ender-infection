"""Validate final six facility NBTs, fixture integrity, biome routing and floor access."""
import json,hashlib
from collections import deque
from design_catalog import DATA,PROJECT
from nbt_codec import load
meta=json.loads((PROJECT/'verification/facilities.json').read_text('utf-8'));errors=[];reports=[]
sizes={'lab_vent':(1,0),'lab_hatch':(1,0),'lab_door':(2,0),'lab_cabinet':(2,0),'incubator':(3,0),'surgery_table':(2,1),'lab_workbench':(2,1),'lab_window':(2,1)}
directions={'north':(1,0),'south':(-1,0),'east':(0,1),'west':(0,-1)}
for spec in meta['facilities']:
 root=load(DATA/'structures'/f'{spec["key"]}.nbt');palette=root['palette'];grid={tuple(b['pos']):(palette[b['state']],b.get('nbt')) for b in root['blocks']}
 def state(p):return grid.get(p,({'Name':'minecraft:air'},None))[0]
 def name(p):return state(p)['Name'].split(':')[-1]
 def anchor(p):
  s=state(p)['Properties'];dx,dz=directions[s['facing']];u=int(s['part_x'])-1;v=int(s['part_y']);return p[0]-u*dx,p[1]-v,p[2]-u*dz
 def passable(p):
  n=name(p)
  if n=='lab_fixture_part':return name(anchor(p)).replace('_eroded','')=='lab_door'
  return n in {'air','cave_air','lab_door','lab_door_eroded','lab_access_ladder','birch_wall_sign','infected_tendrils','fern','red_mushroom','brown_mushroom','amethyst_cluster','brewing_stand'}
 for pos,(s,nbt) in grid.items():
  n=name(pos);base=n.replace('_eroded','')
  if n=='ladder':errors.append(spec['key']+' contains a wood ladder')
  if n=='lab_fixture_part' and name(anchor(pos)).replace('_eroded','') not in sizes:errors.append('Orphan part '+spec['key']+str(pos))
  if base in sizes:
   h,half=sizes[base];dx,dz=directions[s['Properties']['facing']]
   for u in range(-half,half+1):
    for v in range(h):
     if not (u or v):continue
     q=pos[0]+u*dx,pos[1]+v,pos[2]+u*dz
     if name(q)!='lab_fixture_part' or anchor(q)!=pos:errors.append('Missing part '+spec['key']+str(pos)+' -> '+str(q))
 w,height,d=spec['size'];room_access=[]
 for floor in range(spec['levels']):
  y=floor*5+1;start=(spec['lift'][0],spec['lift'][2]);seen=set();queue=deque([start])
  def walkable(x,z):return passable((x,y,z)) and passable((x,y+1,z)) and name((x,y-1,z)) not in ('air','water','lab_access_ladder')
  while queue:
   x,z=queue.popleft()
   if (x,z) in seen or not 0<=x<w or not 0<=z<d or not walkable(x,z):continue
   seen.add((x,z))
   for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):queue.append((x+dx,z+dz))
  for room in [r for r in spec['rooms'] if r['floor']==floor]:
   x,cy,z=room['chest'];reachable=any((x+dx,z+dz) in seen for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
   if name((x,cy,z))!='chest' or not reachable:errors.append(spec['key']+' inaccessible supply '+room['theme']+' floor '+str(floor)+' at '+str(room['chest']))
   room_access.append({'theme':room['theme'],'floor':floor,'reachable':reachable})
 for floor in range(spec['levels']+1):
  p=spec['lift'][0],floor*5,spec['lift'][2]
  if name(p)!='lab_lift' or not passable((p[0],p[1]+1,p[2])) or not passable((p[0],p[1]+2,p[2])):errors.append('Blocked lift '+spec['key']+str(p))
 worldgen=json.loads((DATA/'worldgen/structure'/f'{spec["key"]}.json').read_text('utf-8'))
 if worldgen['biomes']!='#ender_infection:legacy_debug_labs':errors.append('Incorrect biome route '+spec['key'])
 reports.append({'key':spec['key'],'biome':spec['biome'],'rooms':room_access,'damaged_rooms':len(spec['damage']),'sha256':spec['sha256']})
if len({r['sha256'] for r in reports})!=6 or len({r['biome'] for r in reports})!=6:errors.append('Repeated facility or biome assignment')
result={'status':'FAIL' if errors else 'PASS','facilities':reports,'zones':sum(len(r['rooms']) for r in reports),'errors':errors}
(PROJECT/'verification/laboratory_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8');print(json.dumps({'status':result['status'],'facilities':len(reports),'zones':result['zones'],'errors':errors},ensure_ascii=False));raise SystemExit(bool(errors))
