"""Validate the actual exported structure, including room reachability and large prop parts."""
import hashlib,json
from collections import deque,Counter
from design_catalog import DATA,PROJECT
from nbt_codec import load
from lab_rooms import THEMES,LABELS
root=load(DATA/'structures/research_lab.nbt');palette=root['palette'];grid={tuple(b['pos']):(palette[b['state']],b.get('nbt')) for b in root['blocks']};errors=[]
def state(p):return grid.get(p,({'Name':'minecraft:air'},None))[0]
def name(p):return state(p)['Name'].split(':')[-1]
def props(p):return state(p).get('Properties',{})
fixture_sizes={'lab_vent':(1,0),'lab_hatch':(1,0),'lab_door':(2,0),'lab_cabinet':(2,0),'incubator':(3,0),'surgery_table':(2,1),'lab_workbench':(2,1),'lab_window':(2,1)}
dirs={'north':(1,0),'south':(-1,0),'east':(0,1),'west':(0,-1)}
def anchor(p):
 s=props(p);a=int(s['part_x'])-1;b=int(s['part_y']);dx,dz=dirs[s['facing']];return (p[0]-dx*a,p[1]-b,p[2]-dz*a)
for p,(s,nbt) in grid.items():
 k=name(p).replace('_eroded','')
 if k=='lab_fixture_part':
  a=anchor(p)
  if name(a).replace('_eroded','') not in fixture_sizes:errors.append('Orphan fixture part '+str(p)+' -> '+str(a))
 if k in fixture_sizes:
  h,w=fixture_sizes[k];dx,dz=dirs[props(p)['facing']]
  for x in range(-w,w+1):
   for y in range(h):
    if x==0 and y==0:continue
    q=(p[0]+dx*x,p[1]+y,p[2]+dz*x)
    if name(q)!='lab_fixture_part' or anchor(q)!=p:errors.append('Missing fixture part '+str(p)+' -> '+str(q))
rooms=[]
def passable(p):
 k=name(p)
 if k=='lab_fixture_part':return name(anchor(p)).replace('_eroded','')=='lab_door'
 return k in ('air','lab_door','lab_door_eroded','ladder','birch_wall_sign','amethyst_cluster','infected_tendrils','brewing_stand')
for floor,types in enumerate(THEMES):
 for index,theme in enumerate(types):
  row,side=divmod(index,2);base=floor*5;z0=1+row*9
  def pos(u,h,v):return (1+u if side==0 else 30-u,base+h,z0+v)
  blocked={(u,v) for u in range(12) for v in range(8) if name(pos(u,0,v))=='air' or not passable(pos(u,1,v)) or not passable(pos(u,2,v))}
  start=(11,2);seen=set();queue=deque([start])
  while queue:
   q=queue.popleft()
   if q in seen or q in blocked:continue
   seen.add(q)
   for du,dv in ((1,0),(-1,0),(0,1),(0,-1)):
    u,v=q[0]+du,q[1]+dv
    if 0<=u<12 and 0<=v<8:queue.append((u,v))
  if len(seen)<12:errors.append('Poor room access '+theme+' reachable='+str(len(seen)))
  chests=[];objects=[]
  for u in range(12):
   for v in range(8):
    for h in range(1,5):
     p=pos(u,h,v);k=name(p)
     if k=='chest':chests.append(p)
     if k not in ('air','lab_tile','lab_tile_light','lab_tile_dark','lab_checker','lab_tile_light_eroded','lab_checker_eroded','lab_tile_dark_eroded','iron_bars'):
      objects.append((u,h,v,k.replace('_eroded','')))
  if len(chests)!=1:errors.append('Expected one themed loot chest in '+theme+', got '+str(len(chests)))
  elif not any((u,v) in seen for u in range(12) for v in range(8) if abs(pos(u,1,v)[0]-chests[0][0])+abs(pos(u,1,v)[2]-chests[0][2])==1):errors.append('Loot chest unreachable in '+theme)
  rooms.append({'id':theme,'reachable_columns':len(seen),'signature':hashlib.sha256(json.dumps(objects).encode()).hexdigest(),'objects':len(objects)})
if len({r['signature'] for r in rooms})!=24:errors.append('Repeated actual room layout')
for floor in range(3):
 for step in range(5):
  for x in (2,3):
   p=(x,floor*5+1+step,25-step)
   if name(p)!='lab_stair':errors.append('Missing stair '+str(p))
   for dy in (1,2):
    if not passable((p[0],p[1]+dy,p[2])):errors.append('Blocked stair headroom '+str(p))
if name((16,21,23))!='lab_door' or props((16,21,23)).get('open')!='false':errors.append('Entrance door is not closed by default')
for x in (15,17):
 for y in (21,22):
  if name((x,y,23))=='air':errors.append('Unfilled entrance side '+str((x,y,23)))
for y in (23,24):
 if name((16,y,23))=='air':errors.append('Missing entrance lintel')
for y in range(21,25):
 if name((16,y,25))=='ladder':errors.append('Useless ladder above landing')
result={'status':'FAIL' if errors else 'PASS','unique_rooms':len({r['signature'] for r in rooms}),'rooms':rooms,'errors':errors}
(PROJECT/'verification/laboratory_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'status':result['status'],'unique_rooms':result['unique_rooms'],'reachable_columns':{r['id']:r['reachable_columns'] for r in rooms},'errors':errors},ensure_ascii=False));raise SystemExit(bool(errors))
