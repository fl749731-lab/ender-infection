"""Seal actual mutant animation/damage, dark-room glow and dual JAR evidence."""
import json,re,shutil,zipfile
from pathlib import Path
from verify_creature_rework_runtime import ROOT,digest,frame,verify_native
from export_mutant_rework import PACK,RUNTIME,ASSETS,validate

DEST=ROOT/'previews/mutant-public-v1'
CHECKS=ROOT/'build/mutant-public-checks'
ATTACKS={'claw_sweep':90,'heavy_slam':84,'crystal_burst':88}
PHASES=['idle_front','idle_back','walk_a','walk_b']+[f'attack_{a}_{p}' for a in ATTACKS for p in ['windup','impact']]+['infection','death_start','death_end']

def seal(loader,project):
 logs={name:CHECKS/(project+'-'+name+'.log') for name in ['ai','motion','glow']};texts={name:p.read_text('utf8',errors='replace') for name,p in logs.items()}
 if texts['motion'].count('MUTANT_MOTION_PASS screenshots=13')!=1:raise ValueError('Missing completed 13-frame native run')
 expected={'mutant_zombie_'+p for p in PHASES}
 rows=re.findall(r'(?m)^MUTANT_MOTION_CAPTURE (\w+) moved=([\d.Ee+-]+) attacking=(\w+) emerging=(\w+) death_time=(\d+) target_health=([\d.Ee+-]+)',texts['motion'])
 if len(rows)!=13 or {r[0] for r in rows}!=expected:raise ValueError('Missing/duplicate native phase')
 states={name.removeprefix('mutant_zombie_'):dict(moved=float(move),attacking=attack=='true',emerging=emerge=='true',death_time=int(death),target_health=float(hp)) for name,move,attack,emerge,death,hp in rows}
 if min(states[p]['moved'] for p in ['walk_a','walk_b'])<=.05:raise ValueError('No real walking')
 for attack,hp in ATTACKS.items():
  windup,impact=[states[f'attack_{attack}_{phase}'] for phase in ['windup','impact']]
  if not windup['attacking'] or not impact['attacking'] or windup['target_health']!=100 or impact['target_health']!=hp:raise ValueError('Wrong native windup/damage: '+attack)
 if not states['infection']['emerging']:raise ValueError('No native emergence')
 for phase,minimum in [('death_start',4),('death_end',16)]:
  if not minimum<=states[phase]['death_time']<20:raise ValueError('Death is not within real removal lifetime')
 probes=re.findall(r'(?m)^CRYSTAL_GLOW_LIGHT (\w+) block=(\d+) sky=(\d+) probes=(\d+)',texts['glow'])
 glow_names={'mutant_zombie_front','mutant_zombie_back'}
 if texts['glow'].count('CRYSTAL_GLOW_VISUAL_PASS screenshots=2')!=1 or len(probes)!=2 or {p[0] for p in probes}!=glow_names or any(int(b) or int(s) or int(n)<10 for _,b,s,n in probes):raise ValueError('Invalid dark-room run')
 native=verify_native(logs['ai'],36);target=DEST/'runtime'/loader;target.mkdir(parents=True,exist_ok=True);frames=[]
 for mode,names,folder in [('motion',expected,'mutant-motion-check'),('glow',glow_names,'crystal-glow-check')]:
  out=target/mode;out.mkdir(exist_ok=True)
  for name in sorted(names):
   source=ROOT/project/'build'/folder/'screenshots'/(name+'.png');row=frame(source)
   if mode=='glow' and row['purple_pixels']<2:raise ValueError('No visible crystal emission in '+name)
   shutil.copy2(source,out/source.name);frames.append(dict(mode=mode,**row))
 for name,path in logs.items():shutil.copy2(path,target/(name+'.log'))
 result=dict(status='PASS',loader=loader,screenshots=15,native_required_passed=native['required_passed'],attack_selection='fixture invokes unchanged private startAttack; real server timing/damage; ordinary AI additionally covered by native AI suite',motion_states=states,frames=frames,logs_sha256={name:digest(path) for name,path in logs.items()})
 (target/'verification.json').write_text(json.dumps(result,indent=2)+'\n','utf8');return result

def main():
 validate();DEST.mkdir(parents=True,exist_ok=True);receipt=DEST/'verification.json';receipt.unlink(missing_ok=True)
 results=[seal('forge','ender_infection_colored'),seal('neo','ender_infection_neoforge')]
 builds=ROOT/'build/dual-version-validation';build=json.loads((builds/'verified-builds.json').read_text('utf8'))
 if build['status']!='PASS' or build['version']!='0.12.0-alpha.8':raise ValueError('No current dual development build')
 resources=[f'geo/{RUNTIME}.geo.json',f'animations/{RUNTIME}.animation.json',f'textures/entity/{RUNTIME}.png',f'textures/entity/{RUNTIME}_glowmask.png','concept_visual_bounds.json','third_party/model_credits.json']
 for jar in build['jars']:
  if digest(ROOT/jar['file'])!=jar['sha256']:raise ValueError('Built JAR changed')
  with zipfile.ZipFile(ROOT/jar['file']) as archive:
   for relative in resources:
    actual=archive.read('assets/ender_infection/'+relative);expected=(ASSETS/relative).read_bytes()
    if relative.endswith('.json'):actual=actual.replace(b'\r\n',b'\n');expected=expected.replace(b'\r\n',b'\n')
    if actual!=expected:raise ValueError('Stale JAR resource: '+relative)
 out=DEST/'runtime/development';out.mkdir(exist_ok=True);shutil.copy2(builds/'verified-builds.json',out/'verified-builds.json');build_logs={}
 for path in sorted(builds.glob('*-build.log')):shutil.copy2(path,out/path.name);build_logs[path.name]=digest(path)
 files=[p for p in PACK.rglob('*') if p.is_file()]+[ASSETS/r for r in resources]
 files += [ROOT/'tools'/p for p in ['export_creature_reworks.py','export_mutant_rework.py','export_design_assets.py','validate_design_assets.py','verify_mutant_runtime.py']]
 for project in ['ender_infection_colored','ender_infection_neoforge']:
  files += [ROOT/project/'src/main/java/dev/hui/enderinfection'/p for p in ['entity/MutantZombie.java','client/MutantZombieRenderer.java','client/MutantMotionCheck.java','combat/AttackSpec.java']]+[ROOT/project/'build.gradle']
 sources={p.relative_to(ROOT).as_posix():dict(sha256=digest(p,True),scheme='git-lf') if p.suffix in ['.py','.java','.json','.bbmodel','.md','.gradle'] else dict(sha256=digest(p),scheme='raw') for p in sorted(set(files))}
 result=dict(status='PASS',release_gate=False,subjects=['mutant_zombie'],screenshots=30,native_required_passed=sum(r['native_required_passed'] for r in results),source_hashes=sources,build_sha256=digest(out/'verified-builds.json'),build_logs_sha256=build_logs,loaders=[{k:v for k,v in row.items() if k!='frames'} for row in results])
 receipt.write_text(json.dumps(result,indent=2)+'\n','utf8');print(json.dumps({k:v for k,v in result.items() if k not in ['source_hashes','loaders']}))

if __name__=='__main__':main()
