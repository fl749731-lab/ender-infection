"""Seal three real quadrupeds, their native combat states and zero-light emission."""
import json,re,shutil
from pathlib import Path
from PIL import Image,ImageDraw
from verify_creature_rework_runtime import ROOT,PHASES,digest,frame,verify_native

SUBJECTS=('cow','pig','sheep')
DEST=ROOT/'previews/quadruped-rework-v2'
CHECKS=ROOT/'build/quadruped-rework-checks'

def seal(loader,project):
 logs={name:CHECKS/(project+'-'+name+'.log') for name in ('ai','motion','glow')}
 text={name:path.read_text('utf-8',errors='replace') for name,path in logs.items()}
 if text['motion'].count('CREATURE_MOTION_PASS screenshots=27')!=1 or text['glow'].count('CRYSTAL_GLOW_VISUAL_PASS screenshots=6')!=1:raise ValueError('Incomplete native capture: '+loader)
 if re.search(r'OutOfMemoryError|Exception in thread|Failed to parse animation|Unable to load (?:model|texture)|CREATURE_MOTION_FAIL',text['motion']+text['glow'],re.I):raise ValueError('Native client/resource failure')
 rows=re.findall(r'(?m)^CREATURE_MOTION_CAPTURE (\w+) moved=([\d.Ee+-]+) attacking=(true|false) emerging=(true|false) death_time=(\d+) target_health=([\d.Ee+-]+)\s*$',text['motion'])
 expected={kind+'_'+phase for kind in SUBJECTS for phase in PHASES}
 if len(rows)!=27 or {r[0] for r in rows}!=expected:raise ValueError('Missing or duplicated motion phase')
 states={name:dict(moved=float(moved),attacking=attack=='true',emerging=emerge=='true',death_time=int(death),target_health=float(health)) for name,moved,attack,emerge,death,health in rows}
 for kind in SUBJECTS:
  s={phase:states[kind+'_'+phase] for phase in PHASES}
  if min(s[p]['moved'] for p in ('walk_a','walk_b'))<=.05:raise ValueError('Walker did not move: '+kind)
  if not all(s[p]['attacking'] for p in ('attack_windup','attack_impact')):raise ValueError('Combat was not active: '+kind)
  if not 0<=s['attack_impact']['target_health']<s['attack_windup']['target_health']:raise ValueError('Native attack did not damage cow: '+kind)
  if not s['infection']['emerging']:raise ValueError('Emergence was not active: '+kind)
  for phase,tick in [('death_start',4),('death_end',16)]:
   if not tick<=s[phase]['death_time']<20:raise ValueError('Death not within actual native lifetime')
 glow_names={kind+'_'+side for kind in SUBJECTS for side in ('front','back')}
 probes=re.findall(r'(?m)^CRYSTAL_GLOW_LIGHT (\w+) block=(\d+) sky=(\d+) probes=(\d+)',text['glow'])
 if len(probes)!=6 or {p[0] for p in probes}!=glow_names or any(int(b) or int(s) or int(n)<10 for _,b,s,n in probes):raise ValueError('Missing zero-light native probes')
 native=verify_native(logs['ai'],36);target=DEST/'runtime'/loader;target.mkdir(parents=True,exist_ok=True);frames=[]
 for mode,names,folder in [('motion',sorted(expected),'creature-motion-check'),('glow',sorted(glow_names),'crystal-glow-check')]:
  out=target/mode;out.mkdir(exist_ok=True)
  for name in names:
   source=ROOT/project/'build'/folder/'screenshots'/(name+'.png');row=frame(source)
   if mode=='glow' and row['purple_pixels']<2:raise ValueError('No visible crystal emission: '+name)
   shutil.copy2(source,out/source.name);frames.append(dict(mode=mode,**row))
 for name,path in logs.items():shutil.copy2(path,target/(name+'.log'))
 result=dict(status='PASS',loader=loader,screenshots=len(frames),native_required_passed=native['required_passed'],zero_light_views=6,motion_states=states,frames=frames,logs_sha256={name:digest(path) for name,path in logs.items()})
 (target/'verification.json').write_text(json.dumps(result,indent=2)+'\n','utf-8');return result

def main():
 DEST.mkdir(parents=True,exist_ok=True);receipt=DEST/'verification.json';receipt.unlink(missing_ok=True)
 results=[seal('forge','ender_infection_colored'),seal('neo','ender_infection_neoforge')]
 builds=ROOT/'build/dual-version-validation';build=json.loads((builds/'verified-builds.json').read_text('utf-8'))
 if build['status']!='PASS' or build['version']!='0.12.0-alpha.8':raise ValueError('Expected current dual developer build')
 for jar in build['jars']:
  if digest(ROOT/jar['file'])!=jar['sha256']:raise ValueError('Built JAR changed')
 out=DEST/'runtime/development';out.mkdir(exist_ok=True)
 shutil.copy2(builds/'verified-builds.json',out/'verified-builds.json');build_logs={}
 for path in sorted(builds.glob('*-build.log')):shutil.copy2(path,out/path.name);build_logs[path.name]=digest(path)
 assets=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
 files=[p for p in (ROOT/'colored_units/creature_rework_v2').rglob('*') if p.is_file()]
 files += [ROOT/'tools'/name for name in ['export_creature_reworks.py','export_public_model_adaptations.py','rebuild_creature_silhouettes.py','test_creature_reworks.py','verify_quadruped_runtime.py']]
 files += [ROOT/project/'src/main/java/dev/hui/enderinfection'/tail for project in ['ender_infection_colored','ender_infection_neoforge'] for tail in ['client/CreatureMotionCheck.java','client/InfectedRenderer.java','entity/InfectedKind.java','entity/InfectedMob.java']]
 files += [assets/relative for kind in [*SUBJECTS,'spider'] for relative in [f'geo/infected_{kind}.geo.json',f'animations/infected_{kind}.animation.json',f'textures/entity/infected_{kind}.png',f'textures/entity/infected_{kind}_glowmask.png']]
 files += [assets/'concept_visual_bounds.json',assets/'third_party/model_credits.json']
 sources={p.relative_to(ROOT).as_posix():dict(sha256=digest(p,p.suffix in ('.py','.java','.json','.bbmodel','.md')),scheme='git-lf' if p.suffix in ('.py','.java','.json','.bbmodel','.md') else 'raw') for p in sorted(set(files))}
 result=dict(status='PASS',release_gate=False,subjects=SUBJECTS,screenshots=sum(r['screenshots'] for r in results),native_required_passed=sum(r['native_required_passed'] for r in results),source_hashes=sources,build_sha256=digest(out/'verified-builds.json'),build_logs_sha256=build_logs,loaders=[{k:v for k,v in r.items() if k!='frames'} for r in results])
 receipt.write_text(json.dumps(result,indent=2)+'\n','utf-8')
 # Same four runtime views for each species and each loader.
 sheet=Image.new('RGB',(1600,6*276),(25,27,33));draw=ImageDraw.Draw(sheet)
 for li,loader in enumerate(('forge','neo')):
  for ki,kind in enumerate(SUBJECTS):
   for i,(mode,phase) in enumerate([('motion','idle_front'),('motion','walk_b'),('motion','attack_windup'),('glow','back')]):
    with Image.open(DEST/'runtime'/loader/mode/(kind+'_'+phase+'.png')) as source:im=source.convert('RGB');im.thumbnail((400,250))
    x,y=i*400,(li*3+ki)*276;sheet.paste(im,(x+(400-im.width)//2,y));draw.text((x+8,y+253),loader+' / '+kind+' / '+phase,fill=(225,223,238))
 sheet.save(DEST/'comparison.jpg',quality=92)
 print(json.dumps({k:v for k,v in result.items() if k not in ('source_hashes','loaders')}))

if __name__=='__main__':main()
