"""Check native capture completion, dark-room light probes and actual images."""
import argparse,hashlib,json,re,shutil
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
def verify(loader,mode):
 project=ROOT/('ender_infection_colored' if loader=='forge' else 'ender_infection_neoforge')
 log=ROOT/'build/public-model-integration/checks'/f'{loader}-runClient-{mode}.log';text=log.read_text('utf-8',errors='replace')
 token='CONCEPT' if mode=='concept' else 'CRYSTAL' if mode=='equipment' else 'CRYSTAL_GLOW';expected=8 if mode=='concept' else 20 if mode=='equipment' else 70
 if text.count(f'{token}_VISUAL_PASS screenshots={expected}')!=1:raise ValueError('Missing full native completion marker: '+str(log))
 if re.search(r'OutOfMemoryError|Exception in thread|Failed to parse animation|Unable to load (?:model|texture)',text,re.I):raise ValueError('Native model/resource error')
 names=re.findall(r'(?m)^'+token+r'_CAPTURE (\w+)\s*$',text)
 if len(names)!=expected or len(set(names))!=expected:raise ValueError('Missing or repeated capture')
 if mode=='concept':
  if set(names)!={f'{kind}_{view}' for kind in ['carrier','resonance_priest'] for view in ['front','back','windup','attack']}:raise ValueError('Unexpected subject set')
 elif mode=='equipment':
  from design_catalog import UNITS
  expected_names={row[1] for row in UNITS}|{f'armor_{material}_{view}' for material in ['resonant','ember','void'] for view in ['front','back']}
  if set(names)!=expected_names:raise ValueError('Missing armor or design-creature camera')
 elif mode=='glow':
  probes=re.findall(r'(?m)^CRYSTAL_GLOW_LIGHT (\w+) block=(\d+) sky=(\d+)',text)
  if len(probes)!=expected or {p[0] for p in probes}!=set(names) or any(int(b) or int(s) for _,b,s in probes):raise ValueError('Dark-room light probes are missing or not zero')
 target=ROOT/'previews/public-model-adaptations-v1/runtime'/loader/mode;target.mkdir(parents=True,exist_ok=True)
 source=project/'build'/('concept-check' if mode=='concept' else 'crystal-check' if mode=='equipment' else 'crystal-glow-check')/'screenshots';frames=[]
 for name in names:
  file=source/(name+'.png');image=Image.open(file).convert('RGB');a=np.asarray(image).astype(float)
  if min(image.size)<400 or float(a.std())<.1:raise ValueError('Blank capture: '+name)
  # Exclude other subjects at the back of the room. A flank crystal may be
  # occluded in one camera, so require emission for each front/back pair.
  center=a[int(a.shape[0]*.15):int(a.shape[0]*.85),int(a.shape[1]*.3):int(a.shape[1]*.7)]
  purple=(center[:,:,0]>center[:,:,1]*1.18)&(center[:,:,2]>center[:,:,1]*1.18)&(center.max(axis=2)>25)
  count=int(purple.sum())
  shutil.copy2(file,target/file.name);frames.append({'name':name,'size':image.size,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'purple_pixels':count})
 if mode=='glow':
  subjects={f['name'].rsplit('_',1)[0] for f in frames}
  for subject in subjects:
   pair=[f for f in frames if f['name'].rsplit('_',1)[0]==subject]
   if len(pair)!=2 or max(f['purple_pixels'] for f in pair)<2:raise ValueError('No visible crystal emission in either subject camera: '+subject)
 shutil.copy2(log,target/'client.log')
 result={'status':'PASS','loader':loader,'mode':mode,'screenshots':len(frames),'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest(),'zero_light_probes':expected if mode=='glow' else None,'frames':frames}
 if mode=='glow':result['occluded_views']=[f['name'] for f in frames if f['purple_pixels']<2]
 (target/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');return result

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('loader',choices=['forge','neo']);p.add_argument('mode',choices=['concept','glow','equipment']);a=p.parse_args();r=verify(a.loader,a.mode);print(json.dumps({k:v for k,v in r.items() if k!='frames'}))
