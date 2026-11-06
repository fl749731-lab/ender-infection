"""Publish the licensed, MCP-authored mutant without changing its gameplay contract."""
import argparse,json,math,hashlib
from pathlib import Path
from export_creature_reworks import build as pack_model,read,encoded,ASSETS,ROOT

PACK=ROOT/'colored_units/mutant_zombie_public_v1'
RUNTIME='ender_mutant_zombie'
SPEC=dict(scale=1.06,offset_y=0,atlas=[256,128],placements={0:[0,0],1:[128,0]},emissive_materials=[1])
CONTRACT={'idle':3,'drag_walk':1.2,'claw_sweep':1.3,'heavy_slam':2,'crystal_burst':2.5,'infection':1.6,'death':.9}
IMPACTS={'claw_sweep':.6,'heavy_slam':.9,'crystal_burst':1.4}

def build():
 provenance=read(PACK/'provenance.json');source_path=PACK/provenance['source_file']
 if hashlib.sha256(source_path.read_bytes()).hexdigest()!=provenance['source_json_sha256']:raise ValueError('Upstream source changed')
 upstream=read(source_path);working=read(PACK/'models'/(RUNTIME+'.bbmodel'))
 if upstream['textures'][0]['source']!=working['textures'][0]['source']:raise ValueError('Original skin and clothing artwork changed')
 files,row=pack_model(RUNTIME,pack=PACK,spec=SPEC)
 row['pipeline']='tools/export_mutant_rework.py';row['source']='crystal_hunter';row['public_base']='Purple zomie by @elma_taburlas'
 animations=json.loads(files[f'animations/{RUNTIME}.animation.json'])['animations'];prefix='animation.'+RUNTIME+'.'
 if set(animations)!={prefix+name for name in CONTRACT}:raise ValueError('Incomplete mutant controller clips')
 names={b['name'] for b in json.loads(files[f'geo/{RUNTIME}.geo.json'])['minecraft:geometry'][0]['bones']}
 for name,duration in CONTRACT.items():
  clip=animations[prefix+name]
  if not math.isclose(clip['animation_length'],duration,abs_tol=1e-6):raise ValueError('Wrong mutant duration: '+name)
  if clip.get('loop',False)!=(name in ['idle','drag_walk']):raise ValueError('Wrong mutant loop: '+name)
  if set(clip['bones'])-names:raise ValueError('Missing mutant rig bone')
  if name in IMPACTS:
   if not any(any(math.isclose(float(t),IMPACTS[name],abs_tol=1e-5) for t in channel if t.replace('.','',1).isdigit()) for tr in clip['bones'].values() for channel in tr.values() if isinstance(channel,dict)):raise ValueError('Missing native impact time')
 if len({json.dumps(animations[prefix+name]['bones'],sort_keys=True) for name in IMPACTS})!=3:raise ValueError('Mutant attacks must be distinct')
 if row['emissive_pixels']<=0:raise ValueError('Missing crystal glow')
 return files,row

def export(update_manifest=True):
 files,row=build()
 for relative,content in files.items():
  target=ASSETS/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
 bounds=read(ASSETS/'concept_visual_bounds.json');bounds['mutant_zombie']=row['visual_bounds'];(ASSETS/'concept_visual_bounds.json').write_bytes(encoded(bounds))
 source=read(PACK/'provenance.json');path=ASSETS/'third_party/model_credits.json';credits=read(path)
 credits['models']=[entry for entry in credits['models'] if entry['runtime']!=RUNTIME]
 credits['models'].append(dict(runtime=RUNTIME,author=source['author'],original=source['original'],url=source['page'],license=source['license'],license_url=source['license_url'],source_pack='mutant_zombie_public_v1',changes='; '.join(source['changes'])))
 path.write_bytes(encoded(credits))
 if update_manifest:
  path=ROOT/'ender_infection_colored/verification/design_asset_manifest.json';manifest=read(path);manifest['assets']=[entry for entry in manifest['assets'] if entry['runtime']!=RUNTIME]+[row];path.write_bytes(encoded(manifest))
 return [row]

def validate():
 files,row=build()
 for relative,expected in files.items():
  if (ASSETS/relative).read_bytes()!=expected:raise ValueError('Runtime mutant differs from sealed MCP source: '+relative)
 return [row]

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--verify',action='store_true');args=p.parse_args();print(json.dumps(validate() if args.verify else export(),ensure_ascii=False))
