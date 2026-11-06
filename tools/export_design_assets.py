"""Reproducible BB4 -> Gecko4 / Forge baked asset conversion. No re-atlasing."""
import argparse,base64,copy,io,json,math,sys,zipfile
from pathlib import Path
import numpy as np
from PIL import Image
from design_catalog import *
from export_legacy_creature_glow import export as export_legacy_glow
from export_public_model_adaptations import SPECS as ADAPTATIONS, build as build_adaptation, export as export_adaptations
sys.path.insert(0,str(PACK/'source'))
from modelkit import prepare_render,resolve_groups,PALETTES,rgb
MAN=json.loads((PACK/'manifest.json').read_text('utf-8'))
SOURCES={r['id']:r for r in MAN['models']}
REPORT=[]
def write(path,obj):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
def load(key):return resolve_groups(json.loads((PACK/SOURCES[key]['file']).read_text('utf-8')))
# Both source palettes and the alpha.11 crystal relief palette are emissive,
# including dark facets. Brightness thresholds alone cut holes in crystals.
CRYSTAL_COLORS={rgb(c) for name in ('crystal','glow') for c in PALETTES[name]}|{
 (57,33,79),(100,59,133),(150,96,181),(206,149,226)}

def crystal_pixels(pixels):
 mask=np.zeros(pixels.shape[:2],dtype=bool)
 for color in CRYSTAL_COLORS:mask|=np.all(pixels[:,:,:3]==color,axis=2)
 return mask&(pixels[:,:,3]>0)

def validate_crystal_mask(pixels,glow):
 expected=crystal_pixels(pixels)
 if not expected.any():raise ValueError('Native concept texture has no crystal pixels')
 if not np.array_equal(glow[:,:,3]>0,expected):raise ValueError('Incomplete crystal glow or non-crystal pixels glow')
 if not np.array_equal(glow[expected],pixels[expected]):raise ValueError('Crystal glow changed color or alpha')

def texture(d,path,glow=False,full_crystals=False,strict_crystals=False):
 im=Image.open(io.BytesIO(base64.b64decode(d['textures'][0]['source'].split(',')[1]))).convert('RGBA');path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
 if glow:
  a=np.array(im);mask=(a[:,:,0]>100)&(a[:,:,2]>130)&(a[:,:,0]>a[:,:,1]*1.2)&(a[:,:,2]>a[:,:,1]*1.2)
  if full_crystals:
   from concept_materials import CONCEPT_PALETTES
   for colors in CONCEPT_PALETTES.values():
    for color in colors:mask&=~np.all(a[:,:,:3]==rgb(color),axis=2)
   for color in CRYSTAL_COLORS:mask|=np.all(a[:,:,:3]==color,axis=2)
  if strict_crystals:mask=crystal_pixels(a)
  a[~mask]=0
  if strict_crystals:validate_crystal_mask(np.array(im),a)
  target=path.with_name(path.stem+'_glowmask.png')
  if (a[:,:,3]>0).any():Image.fromarray(a).save(target)
  else:target.unlink(missing_ok=True)
 return im

def groups(d):
 d=resolve_groups(d)
 out=[]
 def visit(g,parent=None):
  out.append((g,parent))
  for c in g['children']:
   if isinstance(c,dict):visit(c,g['name'])
 for g in d['outliner']:visit(g)
 return out

def geocube(e,s=1,offset=(0,0,0)):
 a,b=e['from'],e['to'];p=e.get('origin',[0,0,0]);r=e.get('rotation',[0,0,0]);o=offset
 cube={'origin':[(-b[0]+o[0])*s,(a[1]+o[1])*s,(a[2]+o[2])*s],'size':[(b[i]-a[i])*s for i in range(3)],'pivot':[(-p[0]+o[0])*s,(p[1]+o[1])*s,(p[2]+o[2])*s],'rotation':[-r[0],-r[1],r[2]],'uv':{}}
 for face,v in e['faces'].items():
  if v.get('texture') is None:continue
  u,w,U,W=v['uv'];face={'east':'west','west':'east'}.get(face,face)
  cube['uv'][face]={'uv':[u,w],'uv_size':[U-u,W-w]}
 return cube

def geo(key,d,scale=1,offset=(0,0,0),armor=False,fixture=False):
 elems={e['uuid']:e for e in d['elements'] if e.get('export',True)};bones=[]
 ren={'helmet':'armorHead','chestplate':'armorBody','arm_1':'armorRightArm','arm_-1':'armorLeftArm','leg_1':'armorRightLeg','leg_-1':'armorLeftLeg'}
 for g,parent in groups(d):
  if not g.get('export',True) or (armor and g['name'] not in ren):continue
  name=ren.get(g['name'],g['name']) if armor else g['name'];p=g['origin'];r=g.get('rotation',[0,0,0]);bone_offset=offset
  if armor:
   if 'Arm' in name:
    shift=1.5 if p[0]<0 else -1.5;bone_offset=(shift,-2,0);p=[p[0]+shift,22,0]
   elif 'Leg' in name:
    shift=.25 if p[0]<0 else -.25;bone_offset=(shift,0,0);p=[p[0]+shift,12,0]
   else:p=[0,24,0]
  if fixture and ('hinge' in name or name.startswith('door_')):r=[0,0,0]
  bone={'name':name,'pivot':[(-p[0]+offset[0])*scale,(p[1]+offset[1])*scale,(p[2]+offset[2])*scale],'rotation':[-r[0],-r[1],r[2]],'cubes':[]}
  if parent and not armor:bone['parent']=parent
  boots=[]
  for uid in g['children']:
   if isinstance(uid,dict) or uid not in elems:continue
   e=elems[uid];c=geocube(e,scale,(-bone_offset[0],bone_offset[1],bone_offset[2]) if armor else offset)
   if armor and 'Leg' in name and any(t in e['name'] for t in ('boot','heel')):boots.append(c)
   else:bone['cubes'].append(c)
  bones.append(bone)
  if armor and 'Leg' in name:bones.append({'name':name.replace('Leg','Boot'),'pivot':bone['pivot'],'cubes':boots})
 write(ASSETS/'geo'/f'{key}.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.'+key,'texture_width':d['resolution']['width'],'texture_height':d['resolution']['height'],'visible_bounds_width':32,'visible_bounds_height':32,'visible_bounds_offset':[0,4,0]},'bones':bones}]})
 return bones

def validate_native_attack(key,d,hit,ticks):
 source=NATIVE_ATTACK_SOURCES.get(key.removeprefix('infected_'))
 if source is None:return False
 if d.get('model_identifier')!=source or not d.get('meta',{}).get('format_version','').startswith('5.'):
  raise ValueError(f'{key}: native attack requires its MCP-authored BB5 source {source}')
 clips=[a for a in d.get('animations',[]) if a['name'].split('.')[-1]=='attack']
 if len(clips)!=1:raise ValueError(f'{key}: expected one native attack clip')
 clip=clips[0];end=ticks/20;impact=hit/20
 if not math.isclose(clip['length'],end,abs_tol=1e-6):
  raise ValueError(f'{key}: native attack length {clip["length"]} must equal {end} s; source is not ready')
 times=[k['time'] for a in clip.get('animators',{}).values() if a.get('type')=='bone' for k in a.get('keyframes',[])]
 if any(not math.isfinite(t) or t<0 or t>end+1e-6 for t in times):raise ValueError(f'{key}: native attack key outside clip')
 if not any(math.isclose(t,impact,abs_tol=1e-6) for t in times):
  raise ValueError(f'{key}: native attack needs an impact key at {impact} s')
 return True

def animations(key,d,scale=1,mutant=False,hit=16,ticks=40):
 native_attack=validate_native_attack(key,d,hit,ticks)
 out={};src={a['name'].split('.')[-1]:a for a in d.get('animations',[])}
 aliases={'idle':'idle','walk':'walk' if 'walk' in src else 'fly' if 'fly' in src else 'idle','special':'attack','infection':'emerge','death':'death'}
 if mutant:aliases={'idle':'idle','drag_walk':'walk','claw_sweep':'attack','heavy_slam':'attack','crystal_burst':'attack','infection':'emerge'}
 for name,origin in aliases.items():
  if origin not in src:continue
  a=src[origin];duration=a['length'];factor=1.6/duration if name=='infection' else 1
  def time_at(t):
   if mutant and name in ('claw_sweep','heavy_slam','crystal_burst'):
    impact={'claw_sweep':12,'heavy_slam':18,'crystal_burst':28}[name]/20;end={'claw_sweep':26,'heavy_slam':40,'crystal_burst':50}[name]/20
    return t/.8*impact if t<=.8 else impact+(t-.8)/(duration-.8)*(end-impact)
   if name=='special':
    if native_attack:return t
    impact=hit/20;end=ticks/20
    return t/.8*impact if t<=.8 else impact+(t-.8)/(duration-.8)*(end-impact)
   return t*factor
  if mutant and name in ('claw_sweep','heavy_slam','crystal_burst'):
   # Match AttackSpec impact and total duration independently.
   factor={'claw_sweep':12/16,'heavy_slam':18/16,'crystal_burst':28/16}[name]
  tracks={}
  bone_names={g['uuid']:g['name'] for g,parent in groups(d)}
  for bone_uuid,anim in a.get('animators',{}).items():
   if anim.get('type')!='bone' or not anim.get('keyframes'):continue
   channels={}
   for k in anim['keyframes']:
    channel=k['channel'];vals=[float(k['data_points'][0][axis]) for axis in ('x','y','z')]
    if channel=='rotation':vals=[-vals[0],-vals[1],vals[2]]
    elif channel=='position':vals=[-vals[0]*scale,vals[1]*scale,vals[2]*scale]
    channels.setdefault(channel,{})[str(round(time_at(k['time']),5))]=vals
   # BB5 animator display names can remain stale after an MCP group rename.
   # The UUID is the actual rig binding; export its current group name.
   tracks[bone_names.get(bone_uuid,anim['name'])]=channels
  out['animation.'+key+'.'+name]={'loop':a.get('loop')=='loop','animation_length':round(time_at(duration),5),'bones':tracks}
 write(ASSETS/'animations'/f'{key}.animation.json',{'format_version':'1.8.0','animations':out})

def bounds(d):
 pts=[]
 for e in prepare_render(d)['elements']:
  if not e.get('export',True):continue
  a,b=e['from'],e['to'];m=np.array(e['_pose'])
  for x in (a[0],b[0]):
   for y in (a[1],b[1]):
    for z in (a[2],b[2]):pts.append((m@np.array([x,y,z,1]))[:3])
 return np.min(pts,axis=0),np.max(pts,axis=0)

def obj(key,d,kind='item',filter_fn=lambda e:True,normalize=True,transform=None):
 im=texture(d,ASSETS/'textures'/kind/'design'/f'{key}.png');w,h=im.size
 lo,hi=bounds(d);size=hi-lo;s=14/max(size) if normalize else 1;center=(lo+hi)/2
 lines=[f'mtllib {key}.mtl','usemtl atlas'];idx=0
 for e in prepare_render(d)['elements']:
  if not e.get('export',True) or not filter_fn(e):continue
  a,b=e['from'],e['to'];x,y,z=a;X,Y,Z=b;m=np.array(e['_pose'])
  faces={'north':([(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],(0,0,-1)), 'south':([(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],(0,0,1)), 'west':([(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],(-1,0,0)), 'east':([(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],(1,0,0)), 'up':([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],(0,1,0)), 'down':([(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],(0,-1,0))}
  for face,(verts,normal) in faces.items():
   f=e['faces'].get(face)
   if not f or f.get('texture') is None:continue
   p=np.array([(m@np.array([*v,1]))[:3] for v in verts]);n=m[:3,:3]@normal;n=n/np.linalg.norm(n)
   uv=f['uv'];tex=[(uv[0]/w,uv[1]/h),(uv[2]/w,uv[1]/h),(uv[2]/w,uv[3]/h),(uv[0]/w,uv[3]/h)]
   if np.dot(np.cross(p[1]-p[0],p[2]-p[0]),n)<0:p=p[::-1];tex=tex[::-1]
   if normalize:p=(p-center)*s+8
   else:p=p+np.array([8,0,8])
   if transform:p=np.array([transform(v) for v in p]);n=np.cross(p[1]-p[0],p[2]-p[0]);n=n/np.linalg.norm(n)
   for v in p:lines.append('v '+' '.join(f'{a/16:.7f}' for a in v))
   for u,v in tex:lines.append(f'vt {u:.8f} {v:.8f}')
   for _ in range(4):lines.append('vn '+' '.join(f'{a:.6f}' for a in n))
   lines.append('f '+' '.join(f'{idx+j}/{idx+j}/{idx+j}' for j in range(1,5)));idx+=4
 path=ASSETS/'models'/kind/'design'/key;path.parent.mkdir(parents=True,exist_ok=True)
 path.with_suffix('.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8');path.with_suffix('.mtl').write_text(f'newmtl atlas\nKd 1 1 1\nd 1\nmap_Kd ender_infection:{kind}/design/{key}.png\n',encoding='utf-8')
 # Forge resolves material textures as atlas sprites; PNG suffix must be omitted.
 path.with_suffix('.mtl').write_text(f'newmtl atlas\nKd 1 1 1\nd 1\nmap_Kd ender_infection:{kind}/design/{key}\n',encoding='utf-8')
 model={'loader':'forge:obj','model':f'ender_infection:models/{kind}/design/{key}.obj','flip_v':False,'automatic_culling':False,'shade_quads':True,'render_type':'minecraft:cutout','textures':{'particle':f'ender_infection:{kind}/design/{key}'}}
 if kind=='item':
  model['parent']='minecraft:item/handheld';model['display']={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.9,.9,.9]},'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,1,0],'scale':[1,1,1]},'firstperson_righthand':{'rotation':[0,-90,0],'translation':[1,2,0],'scale':[1,1,1]}}
 write(ASSETS/'models'/kind/f'{key}.json',model)
 return model

def select_units(keys):
 if keys is None:return UNITS
 requested=[key.strip() for key in keys.split(',')]
 known={row[1] for row in UNITS}
 if not requested or any(not key or key not in known for key in requested):
  raise ValueError('Expected comma-separated unit keys from: '+', '.join(sorted(known)))
 return [row for row in UNITS if row[1] in requested]

def merge_unit_report(report,rows):
 merged=copy.deepcopy(report);pending={row['runtime']:row for row in rows}
 for row in merged['assets']:
  if row['runtime'] in pending:row.update(pending.pop(row['runtime']))
 merged['assets'].extend(pending.values())
 return merged

def export(units=None):
 rows=select_units(units);report_path=PROJECT/'verification/design_asset_manifest.json'
 # Preflight every selected source and the existing report before writing assets.
 # A partial export must never enter the legacy, furniture, or rift exporters.
 prior=json.loads(report_path.read_text('utf-8')) if units is not None else None
 if prior is not None and not isinstance(prior.get('assets'),list):raise ValueError('Invalid existing design export report')
 prepared=[]
 for row in rows:
  adaptation=next((name for name,spec in ADAPTATIONS.items() if spec['runtime']=='infected_'+row[1]),None)
  if adaptation:
   build_adaptation(adaptation) # Fail a stale MCP export before any asset write.
   prepared.append((row,adaptation,None));continue
  d=load(row[2]);native=validate_native_attack('infected_'+row[1],d,row[7],row[8])
  if native:
   pixels=np.array(Image.open(io.BytesIO(base64.b64decode(d['textures'][0]['source'].split(',')[1]))).convert('RGBA'))
   glow=pixels.copy();glow[~crystal_pixels(pixels)]=0;validate_crystal_mask(pixels,glow)
  prepared.append((row,d,native))
 REPORT.clear()
 for row,d,native in prepared:
  if isinstance(d,str):
   REPORT.extend(export_adaptations([d],assets=ASSETS));continue
  enum,key,source,label,hp,spd,dmg,hit,dur,reach,width,height,stage,price=row
  normalization=SOURCES[source].get('runtime_normalization')
  if normalization:
   s=normalization['scale'];offset=(0,normalization['offset_y'],0)
  else:
   lo,hi=bounds(d);s=height*16/(hi[1]-lo[1]);offset=(0,-lo[1],0)
  geo('infected_'+key,d,s,offset);texture(d,ASSETS/'textures/entity'/f'infected_{key}.png',True,full_crystals=True,strict_crystals=native);animations('infected_'+key,d,s,hit=hit,ticks=dur)
  REPORT.append({'source':source,'runtime':'infected_'+key,'kind':'entity','scale':s})
 if units is not None:
  write(report_path,merge_unit_report(prior,REPORT))
  print('Exported',len(REPORT),'selected units; preserved other runtime asset mappings')
  return
 for source,key,height in [('crystal_hunter','ender_mutant_zombie',2.2),('rib_crystal_caster','infected_skeleton',2.3),('ravager_siege_concept','infected_siege_beast',3.2)]:
  d=load(source);lo,hi=bounds(d);s=height*16/(hi[1]-lo[1]);geo(key,d,s,(0,-lo[1],0));texture(d,ASSETS/'textures/entity'/f'{key}.png',True,full_crystals=True);animations(key,d,s,key=='ender_mutant_zombie',20 if 'siege' in key else 16,64 if 'siege' in key else 32);REPORT.append({'source':source,'runtime':key,'kind':'entity'})
 for stage,height in [(1,1.4),(2,2.4),(3,3.1),(4,4),(5,4.8)]:
  source='calamity_brood_mother' if stage==5 else 'brood_mother';d=load(source);lo,hi=bounds(d);s=height*16/(hi[1]-lo[1]);key='infected_core' if stage==2 else f'infected_core_stage{stage}'
  geo(key,d,s,(0,-lo[1],0));texture(d,ASSETS/'textures/entity'/f'{key}.png',True,full_crystals=True);animations(key,d,s,False,24,56)
  # Controllers keep the core prefix at every stage.
  f=ASSETS/'animations'/f'{key}.animation.json';f.write_text(f.read_text('utf-8').replace('animation.'+key+'.','animation.infected_core.'),encoding='utf-8');REPORT.append({'source':source,'runtime':key,'kind':'mother'})
 for source,key in {**MATERIALS,**WEAPONS}.items():obj(key,load(source));REPORT.append({'source':source,'runtime':key,'kind':'item'})
 for source,key in ARMOR.items():
  d=load(source);geo(key,d,armor=True);texture(d,ASSETS/'textures/armor'/f'{key}.png',True);write(ASSETS/'animations'/f'{key}.animation.json',{'format_version':'1.8.0','animations':{}})
  slots={'helmet':['helmet'],'chestplate':['chestplate','arm_-1','arm_1'],'leggings':['leg_-1','leg_1'],'boots':['leg_-1','leg_1']}
  for slot,names in slots.items():
   dd=copy.deepcopy(d);ids=set()
   for g,_ in groups(d):
    if g['name'] in names:
     for uid in g['children']:
      if isinstance(uid,str):ids.add(uid)
   dd['elements']=[e for e in dd['elements'] if e['uuid'] in ids and (slot not in ('boots','leggings') or any(t in e['name'] for t in ('boot','heel'))==(slot=='boots'))]
   # prepare_render expects group references only; filtered element arrays are sufficient.
   obj(key+'_'+slot,dd)
  REPORT.append({'source':source,'runtime':key,'kind':'armor_set'})
 for source,key in BLOCKS.items():
  for state in ('intact','eroded'):
   d=load(source+'_'+state);runtime=key+('_eroded' if state=='eroded' else '')
   if source in ARCHITECTURE:
    model=obj(runtime,d,'block',normalize=False)
    if source in ('lab_slab','lab_stair'):
     # Native state geometry and UV tiling, with eroded growth baked as a separate OBJ component.
     base=next(e for e in d['elements'] if 'tile' in e['name'] or 'slab' in e['name'] or 'step' in e['name'])
     im=Image.open(ASSETS/'textures/block/design'/f'{runtime}.png');tile=im.crop(tuple(base['faces']['up']['uv'])).resize((16,16),Image.Resampling.NEAREST);tile.save(ASSETS/'textures/block/design'/f'{runtime}_tile.png')
     for suffix,parent in ([('','slab'),('_top','slab_top'),('_double','cube_all')] if source=='lab_slab' else [('','stairs'),('_inner','inner_stairs'),('_outer','outer_stairs')]):
      base_model={'parent':'minecraft:block/'+parent,'textures':{'all':f'ender_infection:block/design/{runtime}_tile','bottom':f'ender_infection:block/design/{runtime}_tile','top':f'ender_infection:block/design/{runtime}_tile','side':f'ender_infection:block/design/{runtime}_tile'}}
      if state=='eroded':
       growth=obj(runtime+'_growth'+suffix,d,'block',lambda e:any(t in e['name'] for t in ('root','crystal','vein','tissue')),False, (lambda v: [v[0],v[1]+8,v[2]]) if suffix=='_top' else None)
       base_model={'loader':'forge:composite','children':{'base':base_model,'growth':growth},'render_type':'minecraft:cutout','textures':{'particle':f'ender_infection:block/design/{runtime}_tile'}}
      write(ASSETS/'models/block'/f'{runtime+suffix}.json',base_model)
     if source=='lab_slab':statejson={'variants':{f'type={v}':{'model':f'ender_infection:block/{runtime+s}'} for v,s in [('bottom',''),('top','_top'),('double','_double')]}}
     else:
      vanilla=ROOT/'tools/stair_variants.json';statejson=json.loads(vanilla.read_text());statejson=json.loads(json.dumps(statejson).replace('minecraft:block/oak_stairs',f'ender_infection:block/{runtime}').replace('minecraft:block/oak_inner_stairs',f'ender_infection:block/{runtime}_inner').replace('minecraft:block/oak_outer_stairs',f'ender_infection:block/{runtime}_outer'))
     write(ASSETS/'blockstates'/f'{runtime}.json',statejson)
    else:write(ASSETS/'blockstates'/f'{runtime}.json',{'variants':{'':{'model':f'ender_infection:block/{runtime}'}}})
    obj(runtime,d,'item')
   else:
    geo(runtime,d,offset=(0,0,7.25) if source in ('vent_panel','service_hatch') else (0,0,0),fixture=True);texture(d,ASSETS/'textures/block/design'/f'{runtime}.png',True);write(ASSETS/'animations'/f'{runtime}.animation.json',{'format_version':'1.8.0','animations':{}})
    write(ASSETS/'models/block'/f'{runtime}.json',{'parent':'minecraft:block/block','textures':{'particle':f'ender_infection:block/design/{runtime}'}})
    write(ASSETS/'blockstates'/f'{runtime}.json',{'variants':{'':{'model':f'ender_infection:block/{runtime}'}}});obj(runtime,d,'item')
   REPORT.append({'source':source+'_'+state,'runtime':runtime,'kind':'block'})
 export_legacy_glow()
 from export_creature_reworks import export as export_reworks
 REPORT.extend(export_reworks(update_manifest=False))
 # Rift replacements have their own canonical MCP-authored BB5 sources.
 from export_rift_assets import export as export_rift
 export_rift()
 from export_lab_door_variants import export as export_doors
 export_doors()
 write(PROJECT/'verification/design_asset_manifest.json',{'source_pack':MAN['version'],'assets':REPORT,'scenes':'research_lab.nbt is current; complete/cutaway BB scenes retain the earlier schematic'})
 print('Exported',len(REPORT),'runtime asset mappings')
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--units',help='Only export these comma-separated runtime unit keys and merge the existing report')
 args=parser.parse_args()
 try:export(args.units)
 except ValueError as error:parser.error(str(error))
