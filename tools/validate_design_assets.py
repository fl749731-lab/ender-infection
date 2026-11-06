"""Check resource graph, UV bounds, animation bone names and required source coverage."""
import json,math,re,sys
from pathlib import Path
import numpy as np
from PIL import Image
from design_catalog import *
from export_legacy_creature_glow import LEGACY_CREATURE_SOURCES,legacy_crystal_pixels
from validate_rift_runtime import validate as validate_rift
from export_public_model_adaptations import SPECS as ADAPTATIONS, validate as validate_adaptations
from export_creature_reworks import SPECS as REWORKS, validate as validate_reworks
from export_mutant_rework import RUNTIME as MUTANT_REWORK, validate as validate_mutant
from export_staff_rework import validate as validate_staff
sys.path.insert(0,str(PACK/'source'))
from modelkit import PALETTES,rgb
from concept_materials import CONCEPT_PALETTES
report=json.loads((PROJECT/'verification/design_asset_manifest.json').read_text('utf-8'));errors=[];checked_geo=checked_uv=checked_obj=0
for row in report['assets']:
 if row['kind'] in ('entity','mother','armor_set','block') or row.get('pipeline')=='tools/export_staff_rework.py':
  p=ASSETS/'geo'/f"{row['runtime']}.geo.json"
  if not p.exists():
   if row['kind']!='block':errors.append('Missing '+str(p))
   continue
  data=json.loads(p.read_text());g=data['minecraft:geometry'][0];names={b['name'] for b in g['bones']};checked_geo+=1
  if len(names)!=len(g['bones']):errors.append('Duplicate bone '+str(p))
  for bone in g['bones']:
   if bone.get('parent') and bone['parent'] not in names:errors.append('Missing parent '+str(p))
   for cube in bone.get('cubes',[]):
    if any(n<0 for n in cube['size']):errors.append('Negative size '+str(p))
    for face,v in cube['uv'].items():
     u,w=v['uv'];U,W=v['uv_size'];tw,th=g['description']['texture_width'],g['description']['texture_height'];checked_uv+=1
     if not (0<=u<=tw and 0<=u+U<=tw and 0<=w<=th and 0<=w+W<=th):errors.append('UV outside atlas '+str(p))
  anim=ASSETS/'animations'/f"{row['runtime']}.animation.json"
  if anim.exists():
   for key,a in json.loads(anim.read_text())['animations'].items():
    for bone in a.get('bones',{}):
     if bone not in names:errors.append('Animation references absent bone '+key+'/'+bone)
for p in (ASSETS/'models').rglob('*.mtl'):
 for line in p.read_text().splitlines():
  if line.startswith('map_Kd '):
   tex=line.split(' ',1)[1].split(':',1)[1]
   if not (ASSETS/'textures'/f'{tex}.png').exists():errors.append('Missing OBJ texture '+tex)
for p in (ASSETS/'models').rglob('*.obj'):
 vertices=uvs=normals=0
 for line in p.read_text().splitlines():
  if line.startswith('v '):vertices+=1
  elif line.startswith('vt '):uvs+=1
  elif line.startswith('vn '):normals+=1
  elif line.startswith('f '):
   for token in line.split()[1:]:
    v,t,n=map(int,token.split('/'))
    if not (0<v<=vertices and 0<t<=uvs and 0<n<=normals):errors.append('Invalid OBJ index '+str(p))
 checked_obj+=1
checked_glow=0
for p in (ASSETS/'textures').rglob('*_glowmask.png'):
 with Image.open(p) as source:
  mask=source.convert('RGBA')
  if mask.getchannel('A').getbbox() is None:errors.append('Empty glow mask '+str(p.relative_to(ASSETS)))
  base=p.with_name(p.name.replace('_glowmask.png','.png'))
  if not base.exists():errors.append('Glow mask has no base texture '+str(p))
  else:
   with Image.open(base) as texture:
    if texture.size!=mask.size:errors.append('Glow mask size mismatch '+str(p))
 checked_glow+=1
crystal_colors={rgb(c) for material in ('crystal','glow') for c in PALETTES[material]}
crystal_colors.update(((57,33,79),(100,59,133),(150,96,181),(206,149,226)))
non_crystal_colors={rgb(c) for material in ('tissue','membrane','shell','bone') for c in PALETTES[material]}
non_crystal_colors.update(rgb(c) for colors in CONCEPT_PALETTES.values() for c in colors)
# Other existing emissive accents, including relief brood sacs, remain compatible.
def color_pixels(pixels,colors):
 selected=np.zeros(pixels.shape[:2],dtype=bool)
 for color in colors:selected|=(pixels[:,:,:3]==color).all(axis=2)
 return selected&(pixels[:,:,3]>0)
creature_glow_textures=crystal_pixels=missing_crystal_pixels=non_crystal_glow_pixels=0
adaptations=[]
try:adaptations=validate_adaptations()
except (AssertionError,ValueError,OSError,KeyError) as error:errors.append('Public model adaptations: '+str(error))
reworks=[]
try:reworks=validate_reworks()
except (AssertionError,ValueError,OSError,KeyError) as error:errors.append('Creature reworks: '+str(error))
try:reworks+=validate_mutant()
except (AssertionError,ValueError,OSError,KeyError) as error:errors.append('Public mutant rework: '+str(error))
adaptations+=reworks
adapted_runtime={spec['runtime'] for spec in ADAPTATIONS.values()}|set(REWORKS)|{MUTANT_REWORK}
for row in report['assets']:
 if row['kind'] not in ('entity','mother'):continue
 if row['runtime'] in adapted_runtime:
  # Public source materials have a separate exact atlas/material glow contract,
  # verified above. Their palettes cannot use our legacy color-only classifier.
  creature_glow_textures+=1
  crystal_pixels+=next((r['emissive_pixels'] for r in adaptations if r['runtime']==row['runtime']),0)
  continue
 base=ASSETS/'textures/entity'/f"{row['runtime']}.png";mask=base.with_name(base.stem+'_glowmask.png')
 if not base.exists():errors.append('Missing creature texture '+str(base));continue
 with Image.open(base) as texture:pixels=np.array(texture.convert('RGBA'))
 expected=color_pixels(pixels,crystal_colors);crystal_pixels+=int(expected.sum());creature_glow_textures+=1
 if not mask.exists():
  errors.append('Missing creature glow mask '+str(mask));missing_crystal_pixels+=int(expected.sum());continue
 with Image.open(mask) as texture:glow=np.array(texture.convert('RGBA'))
 if glow.shape!=pixels.shape:
  errors.append('Creature glow mask size mismatch '+str(mask));missing_crystal_pixels+=int(expected.sum());continue
 missing=int((expected&(glow[:,:,3]!=pixels[:,:,3])).sum())
 recolored=int((expected&(glow[:,:,:3]!=pixels[:,:,:3]).any(axis=2)).sum())
 unwanted=int((color_pixels(pixels,non_crystal_colors)&(glow[:,:,3]>0)).sum())
 missing_crystal_pixels+=missing;non_crystal_glow_pixels+=unwanted
 if missing:errors.append(f'Incomplete crystal glow {row["runtime"]}: {missing} pixels')
 if recolored:errors.append(f'Recolored crystal glow {row["runtime"]}: {recolored} pixels')
 if unwanted:errors.append(f'Non-crystal material glows {row["runtime"]}: {unwanted} pixels')
legacy_glow=[]
for runtime,source in LEGACY_CREATURE_SOURCES.items():
 try:pixels,expected,elements=legacy_crystal_pixels(runtime)
 except (ValueError,OSError,KeyError) as error:errors.append('Legacy creature glow '+str(error));continue
 count=int(expected.sum());crystal_pixels+=count;creature_glow_textures+=1
 legacy_glow.append({'runtime':runtime,'source':source.relative_to(ROOT).as_posix(),'crystal_elements':elements,'crystal_pixels':count})
 mask=ASSETS/'textures/entity'/f'{runtime}_glowmask.png'
 if not mask.exists():errors.append('Missing legacy creature glow mask '+str(mask));missing_crystal_pixels+=count;continue
 with Image.open(mask) as texture:glow=np.array(texture.convert('RGBA'))
 if glow.shape!=pixels.shape:errors.append('Legacy creature glow mask size mismatch '+str(mask));missing_crystal_pixels+=count;continue
 missing=int((expected&(glow[:,:,3]!=pixels[:,:,3])).sum())
 recolored=int((expected&(glow[:,:,:3]!=pixels[:,:,:3]).any(axis=2)).sum())
 unwanted=int((~expected&(glow[:,:,3]>0)).sum())
 missing_crystal_pixels+=missing;non_crystal_glow_pixels+=unwanted
 if missing:errors.append(f'Incomplete legacy crystal glow {runtime}: {missing} pixels')
 if recolored:errors.append(f'Recolored legacy crystal glow {runtime}: {recolored} pixels')
 if unwanted:errors.append(f'Non-crystal legacy geometry glows {runtime}: {unwanted} pixels')
manifest=json.loads((PACK/'manifest.json').read_text());sources={r['source'] for r in report['assets']};missing={r['id'] for r in manifest['models'] if r['category']!='scenes'}-sources
if missing:errors.append('Unmapped source '+str(missing))
rift=[]
try:rift=validate_rift()
except (AssertionError,ValueError,OSError,KeyError) as error:errors.append('Rift runtime assets: '+str(error))
result={'status':'FAIL' if errors else 'PASS','geometry_files':checked_geo,'validated_faces':checked_uv,'obj_files':checked_obj,'glow_masks':checked_glow,'creature_glow_textures':creature_glow_textures+len(rift),'crystal_pixels':crystal_pixels+sum(r['emissive_pixels'] for r in rift),'missing_crystal_pixels':missing_crystal_pixels,'non_crystal_glow_pixels':non_crystal_glow_pixels,'legacy_creature_glow':legacy_glow,'rift_models':rift,'mapped_source_models':len(sources),'scene_source_models':2,'errors':errors}
try:result['public_weapon_adaptations']=validate_staff()
except (ValueError,OSError,KeyError) as error:errors.append('Staff runtime: '+str(error));result['status']='FAIL'
result['public_model_adaptations']=adaptations
(PROJECT/'verification/design_assets_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,ensure_ascii=False));raise SystemExit(bool(errors))
