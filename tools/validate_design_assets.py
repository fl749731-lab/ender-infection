"""Check resource graph, UV bounds, animation bone names and required source coverage."""
import json,math,re
from pathlib import Path
from PIL import Image
from design_catalog import *
report=json.loads((PROJECT/'verification/design_asset_manifest.json').read_text('utf-8'));errors=[];checked_geo=checked_uv=checked_obj=0
for row in report['assets']:
 if row['kind'] in ('entity','mother','armor_set','block'):
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
manifest=json.loads((PACK/'manifest.json').read_text());sources={r['source'] for r in report['assets']};missing={r['id'] for r in manifest['models'] if r['category']!='scenes'}-sources
if missing:errors.append('Unmapped source '+str(missing))
result={'status':'FAIL' if errors else 'PASS','geometry_files':checked_geo,'validated_faces':checked_uv,'obj_files':checked_obj,'glow_masks':checked_glow,'mapped_source_models':len(sources),'scene_source_models':2,'errors':errors}
(PROJECT/'verification/design_assets_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,ensure_ascii=False));raise SystemExit(bool(errors))
