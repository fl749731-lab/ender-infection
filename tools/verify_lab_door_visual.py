"""Bind completed native fixture captures to the actual reviewed image bytes."""
import argparse,hashlib,json,re,shutil
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
def verify(loader):
 project='ender_infection_colored' if loader=='forge' else 'ender_infection_neoforge'
 log=ROOT/'build/door-fix-checks'/(project+'-client.log');text=log.read_text('utf-8',errors='replace')
 assert text.count('LAB_DOOR_VISUAL_PASS screenshots=22')==1,'Missing complete fixture capture run'
 assert not re.search(r'LAB_DOOR_VISUAL_FAIL|OutOfMemoryError|Unable to load (?:model|texture)|Failed to parse',text),'Client resource/capture failure'
 names=re.findall(r'(?m)^LAB_DOOR_CAPTURE (\w+)\s*$',text)
 expected={f'lab_door_{face}_double_{state}' for face in ['north','south','east','west'] for state in ['open','closed']}
 expected|={f'lab_door_eroded_north_double_{state}' for state in ['open','closed']}
 expected|={f'lab_door_north_single_{state}' for state in ['open','closed']}
 expected|={f'{key}_open' for key in ['lab_cabinet','lab_cabinet_eroded','lab_glass_cabinet','lab_cryo_cabinet','lab_server_rack']}
 expected|={'lab_cryo_cabinet_closed','lab_privacy_curtain_north_single_open','lab_privacy_curtain_north_single_closed','reception_front','reception_back'}
 assert len(names)==22 and len(set(names))==22 and set(names)==expected,'Missing fixture view'
 target=ROOT/'previews/lab-fixture-alpha7/runtime'/loader;target.mkdir(parents=True,exist_ok=True)
 frames=[]
 for name in names:
  path=ROOT/project/'build/lab-door-visual-check/screenshots'/(name+'.png')
  with Image.open(path) as image:
   assert min(image.size)>=700,'Undersized native capture'
   extrema=image.convert('RGB').getextrema();assert any(hi-lo>40 for lo,hi in extrema),'Blank native capture'
   size=list(image.size)
  shutil.copy2(path,target/path.name);frames.append({'name':name,'size':size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
 shutil.copy2(log,target/'client.log')
 source_paths=[f'{project}/src/main/java/dev/hui/enderinfection/{path}' for path in ['client/LabFixtureRenderer.java','design/LabFixtureBlock.java','design/LabPartBlock.java','client/LabDoorVisualCheck.java']]
 source_paths += ['ender_infection_colored/src/main/resources/assets/ender_infection/'+path for path in ['geo/'+key+'.geo.json' for key in ['lab_door','lab_door_eroded','lab_door_double_left','lab_door_double_right','lab_door_eroded_double_left','lab_door_eroded_double_right','lab_cabinet','lab_cabinet_eroded','lab_cryo_cabinet','lab_glass_cabinet','lab_server_rack']]+['models/block/design/lab_monitor.obj','textures/block/design/lab_monitor.png']]
 result={'status':'PASS','loader':loader,'screenshots':22,'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest(),'sources':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in source_paths},'frames':frames,'limits':'Fresh isolated test world; cabinet storage held open by a real server menu viewer; automated capture completeness and separate visual inspection. This does not substitute for the full clean release gate.'}
 (target/'verification.json').write_text(json.dumps(result,indent=2)+'\n','utf-8')
 print(json.dumps({'status':'PASS','loader':loader,'screenshots':22}))
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('loader',choices=['forge','neo']);verify(parser.parse_args().loader)
