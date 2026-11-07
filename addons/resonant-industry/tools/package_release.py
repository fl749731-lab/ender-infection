"""Package verified native builds. Does not contact GitHub or change release state."""
from pathlib import Path
import argparse,hashlib,html,json,re,sys,zipfile

from validation_contract import INDUSTRY_FRAMES, LABWARE_FRAMES, NATIVE_MINIMUM, PERSISTENCE_GROUPS

BASE=Path(__file__).resolve().parents[1];ROOT=BASE.parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from sync_neoforge_resources import transform
from verify_gametest_log import verify as verify_gametest

OUT=BASE/'build/release';ASSETS=OUT/'assets'
VERSION=re.search(r"^version\s*=\s*'([^']+)'",(BASE/'forge/build.gradle').read_text(),re.M).group(1)
TAG='resonant-industry-v'+VERSION
URL='https://github.com/fl749731-lab/ender-infection/releases/tag/'+TAG
def digest(data):return hashlib.sha256(data).hexdigest()
def read(p):return json.loads(p.read_text('utf-8'))
def jbytes(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
def source_digest(p):
 data=p.read_bytes()
 try:data.decode('utf-8')
 except UnicodeDecodeError:return digest(data)
 return digest(data.replace(b'\r\n',b'\n'))
def sources():
 files=[]
 for lane in ['common/src/main/java','forge/src/main','neoforge/src/main']:
  files.extend(p for p in (BASE/lane).rglob('*') if p.is_file())
 for lane in ['forge','neoforge']:
  files.extend(p for p in (BASE/lane).iterdir() if p.is_file())
  files.extend(p for p in (BASE/lane/'gradle').rglob('*') if p.is_file())
 files.extend(BASE/'tools'/name for name in ['sync_neoforge.py','generate_assets.py','formed_models.py','build_dual.py','validation_contract.py','package_release.py'])
 return {p.relative_to(ROOT).as_posix():source_digest(p) for p in sorted(set(files))}

def summary(validations):
 return {
  'addon_required_passed':sum(row['required_passed'] for row in validations.values()),
  'server_processes':sum(sum(row['persistence'].values()) for row in validations.values()),
  'client_screenshots':sum(row['client_screenshots'] for row in validations.values()),
 }

def evidence(result):
 loader=result['loader']
 log=BASE/'build/validation'/f'{loader}-native-tests.log'
 tests=verify_gametest(log,NATIVE_MINIMUM[loader])
 assert all(tests[key]==result['tests'][key] for key in ('status','total','required_passed')),loader
 assert 'INDUSTRY_ONE_COAL_FINITE_PASS produced=102400 post_fuel_ticks=200' in log.read_text('utf-8',errors='replace'),loader

 persistence={}
 for name in PERSISTENCE_GROUPS[loader]:
  group=result[name]
  logs=group['logs']
  assert group['status']=='PASS' and group['processes']==3 and len(set(logs))==len(logs)==group['processes'],(loader,name)
  assert all((ROOT/path).is_file() for path in logs),(loader,name)
  persistence[name]=len(logs)

 frames=[(result['client_screenshots'],INDUSTRY_FRAMES)]
 if loader=='neoforge':
  labware=result['labware_client']
  assert labware['status']=='PASS' and labware['conserved_volume_mb']==1000
  assert labware['chemistry']=={'input_mb':500,'remaining_mb':450,'evaporated_mb':50,'salt_crystals':1}
  assert labware['separation']=={'suspension_mb':50,'filtrate_mb':50,'distilled_mb':40,'concentrate_mb':10,'precipitate':1}
  frames.append((labware['screenshots'],LABWARE_FRAMES))
 shots=[]
 for records,names in frames:
  assert len(records)==len(names) and {Path(shot['path']).stem for shot in records}==set(names),(loader,names)
  for shot in records:
   path=ROOT/shot['path']
   assert digest(path.read_bytes())==shot['sha256'],path
   shots.append({'loader':loader,'file':f'screenshots/{loader}/{path.name}','sha256':shot['sha256'],'source':path})
 return {'required_passed':tests['required_passed'],'persistence':persistence,'client_screenshots':len(shots)},shots

def verify_assets(jar,loader,canonical):
 with zipfile.ZipFile(jar) as archive:
  assert archive.testzip() is None,jar
  for name,source in canonical.items():
   data=archive.read(name)
   if name.endswith('.json'):
    expected=json.loads(source)
    if loader=='neoforge':
     expected=transform(expected)
    assert json.loads(data)==expected,name
   else:
    assert data==source,name
  assert not any(name.startswith('dev/hui/enderinfection/') for name in archive.namelist()),jar

def verify():
 receipt=read(OUT/'package-receipt.json');assert receipt['version']==VERSION
 assert receipt['behavior_sources']==sources(),'Release behavior inputs changed'
 for row in receipt['assets']:
  p=ASSETS/row['file'];assert p.is_file() and digest(p.read_bytes())==row['sha256'] and p.stat().st_size==row['bytes'],p
 assert receipt['status']=='ready_for_release' and receipt['core_required_passed']>=147
 validations=receipt['loader_validation']
 assert set(validations)==set(NATIVE_MINIMUM)
 for loader,row in validations.items():
  assert row['required_passed']>=NATIVE_MINIMUM[loader],loader
  assert row['persistence']==dict.fromkeys(PERSISTENCE_GROUPS[loader],3),loader
  frames=INDUSTRY_FRAMES + (LABWARE_FRAMES if loader=='neoforge' else ())
  shots=[s['file'] for s in receipt['screenshots'] if s['loader']==loader]
  assert len(shots)==row['client_screenshots']==len(frames),loader
  assert set(shots)=={f'screenshots/{loader}/{frame}.png' for frame in frames},loader
 assert all(receipt[key]==value for key,value in summary(validations).items())
 assert len(receipt['screenshots'])==receipt['client_screenshots']
 print('INDUSTRY_RELEASE_VERIFY_PASS '+VERSION+' assets='+str(len(receipt['assets'])))
 return receipt
def package():
 builds=read(BASE/'build/validation/verified-builds.json');core=read(ROOT/'build/dual-version-validation/verified-builds.json')
 assert builds['status']=='PASS' and builds['version']==VERSION and builds['gametests'] and builds['clients']
 assert core['status']=='PASS' and core['native_gametests_requested']
 assert len(builds['artifacts'])==len(NATIVE_MINIMUM) and {r['loader'] for r in builds['artifacts']}==set(NATIVE_MINIMUM)
 assert read(BASE/'docs/blueprints/definitions.json')==read(BASE/'forge/build/verified-blueprints.json')==read(BASE/'neoforge/build/verified-blueprints.json'),'Blueprints do not match the native layouts'
 core_tests=sum(t['required_passed'] for t in core['tests']);assert core_tests>=147
 validations={};shots=[];jars=[]
 canonical={p.relative_to(BASE/'forge/src/main/resources').as_posix():p.read_bytes() for p in (BASE/'forge/src/main/resources/assets').rglob('*') if p.is_file()}
 for result in builds['artifacts']:
  loader=result['loader'];jar=ROOT/result['file']
  assert result['core']['version']==core['version'],loader
  assert digest(jar.read_bytes())==result['sha256'],jar
  verify_assets(jar,loader,canonical)
  validations[loader],captures=evidence(result)
  shots.extend(captures)
  jars.append((loader,jar))

 ASSETS.mkdir(parents=True,exist_ok=True)
 assert not any(ASSETS.iterdir()),'Use a fresh release asset directory'
 rows=[]
 def put(name,data):
  (ASSETS/name).write_bytes(data);row={'file':name,'bytes':len(data),'sha256':digest(data)};rows.append(row);return row
 art=[dict(put(jar.name,jar.read_bytes()),loader=loader,minecraft='1.20.1' if loader=='forge' else '1.21.1') for loader,jar in jars]
 forge_shots={Path(shot['file']).stem:shot['source'] for shot in shots if shot['loader']=='forge'}
 guide=(BASE/'docs/machine-panels.md').read_text('utf-8').replace('../build/validation/clients/','screenshots/')
 intro=(BASE/'README.md').read_text('utf-8').replace('docs/blueprints/','blueprints/').replace('docs/machine-panels.md','GUIDE.md')
 guide_name=f'resonant-industry-guide-{VERSION}.zip'
 gallery=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>共振工业 '+VERSION+'</title><style>body{background:#14232c;color:#dbe8ef;font:16px system-ui;margin:28px}a{color:#b8d9ff}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:18px}img{width:100%;height:auto}figure{margin:0;padding:12px;background:#253740}</style><h1>末影感染：共振工业 '+VERSION+'</h1><p>安装对应加载器的附属 JAR、末影感染核心 '+core['version']+' 与 GeckoLib。原生截图未经像素修改。<a href="GUIDE.md">界面说明</a></p><div class="grid">']
 with zipfile.ZipFile(ASSETS/guide_name,'w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('GUIDE.md',guide);z.writestr('README.md',intro)
  for s in shots:
   z.writestr(s['file'],s['source'].read_bytes());gallery.append('<figure><a href="'+s['file']+'"><img loading="lazy" src="'+s['file']+'"></a><figcaption>'+html.escape(s['loader']+' / '+Path(s['file']).stem)+'</figcaption></figure>')
  for p in sorted((BASE/'docs/blueprints').glob('*')):
   if p.is_file():z.writestr('blueprints/'+p.name,p.read_bytes())
  for name,frame in [('processor-panel.png','processor_running'),('production-line.png','production_line'),('cable-detail.png','cable_detail')]:
   z.writestr('images/'+name,forge_shots[frame].read_bytes())
  z.writestr('index.html',''.join(gallery)+'</div></html>')
 with zipfile.ZipFile(ASSETS/guide_name) as z:assert z.testzip() is None
 p=ASSETS/guide_name;rows.append({'file':p.name,'bytes':p.stat().st_size,'sha256':digest(p.read_bytes())})
 public={'status':'PASS','version':VERSION,'tag':TAG,'core_tested':core['version'],'core_required_passed':core_tests,**summary(validations),'loader_validation':validations,'coal_total_fe':102400,'post_fuel_ticks':200,'assets':art,'screenshots':[{k:v for k,v in s.items() if k!='source'} for s in shots],'source_hash_policy':'UTF-8 line endings normalized to LF; binary files exact','behavior_sources':sources()}
 standalone=intro+'\n\n'+guide
 raw='https://raw.githubusercontent.com/fl749731-lab/ender-infection/'+TAG+'/addons/resonant-industry/docs/'
 standalone=standalone.replace('(images/','('+raw+'images/').replace('(blueprints/','('+raw+'blueprints/').replace('(GUIDE.md)','(https://github.com/fl749731-lab/ender-infection/blob/'+TAG+'/addons/resonant-industry/docs/machine-panels.md)')
 put('verification-'+VERSION+'.json',jbytes(public));put('resonant-industry-'+VERSION+'.md',standalone.encode('utf-8'))
 sums=''.join(r['sha256']+'  '+r['file']+'\n' for r in rows);put('SHA256SUMS.txt',sums.encode('utf-8'))
 receipt=dict(public,status='ready_for_release',published=False,assets=rows)
 OUT.mkdir(parents=True,exist_ok=True);(OUT/'package-receipt.json').write_bytes(jbytes(receipt))
 verify()
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args();verify() if a.verify else package()
