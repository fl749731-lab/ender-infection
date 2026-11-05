"""Package verified native builds. Does not contact GitHub or change release state."""
from pathlib import Path
import argparse,hashlib,html,json,re,shutil,subprocess,zipfile

BASE=Path(__file__).resolve().parents[1];ROOT=BASE.parents[1]
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
 files.extend(BASE/'tools'/name for name in ['sync_neoforge.py','generate_assets.py','formed_models.py'])
 return {p.relative_to(ROOT).as_posix():source_digest(p) for p in sorted(set(files))}
def verify():
 receipt=read(OUT/'package-receipt.json');assert receipt['version']==VERSION
 assert receipt['behavior_sources']==sources(),'Release behavior inputs changed'
 for row in receipt['assets']:
  p=ASSETS/row['file'];assert p.is_file() and digest(p.read_bytes())==row['sha256'] and p.stat().st_size==row['bytes'],p
 assert receipt['status']=='ready_for_release' and receipt['core_required_passed']>=147 and receipt['addon_required_passed']==24
 assert receipt['client_screenshots']==24 and receipt['server_processes']==6
 print('INDUSTRY_RELEASE_VERIFY_PASS '+VERSION+' assets='+str(len(receipt['assets'])))
 return receipt
def package():
 builds=read(BASE/'build/validation/verified-builds.json');core=read(ROOT/'build/dual-version-validation/verified-builds.json')
 assert builds['status']=='PASS' and builds['version']==VERSION and builds['gametests'] and builds['clients']
 assert core['status']=='PASS' and core['native_gametests_requested']
 assert {r['loader'] for r in builds['artifacts']}=={'forge','neoforge'}
 assert read(BASE/'docs/blueprints/definitions.json')==read(BASE/'forge/build/verified-blueprints.json')==read(BASE/'neoforge/build/verified-blueprints.json'),'Blueprints do not match the native layouts'
 ASSETS.mkdir(parents=True,exist_ok=True)
 assert not any(ASSETS.iterdir()),'Use a fresh release asset directory'
 rows=[]
 def put(name,data):
  (ASSETS/name).write_bytes(data);row={'file':name,'bytes':len(data),'sha256':digest(data)};rows.append(row);return row
 art=[];shots=[]
 canonical={p.relative_to(BASE/'forge/src/main/resources').as_posix():p.read_bytes().replace(b'\r\n',b'\n') for p in (BASE/'forge/src/main/resources/assets').rglob('*') if p.is_file()}
 for result in builds['artifacts']:
  loader=result['loader'];jar=ROOT/result['file'];assert digest(jar.read_bytes())==result['sha256']
  assert result['tests']['required_passed']==12 and result['persistence']['status']=='PASS' and result['persistence']['processes']==3
  text=(BASE/'build/validation'/f'{loader}-native-tests.log').read_text('utf-8',errors='replace')
  assert 'INDUSTRY_ONE_COAL_FINITE_PASS produced=102400 post_fuel_ticks=200' in text
  with zipfile.ZipFile(jar) as z:
   assert z.testzip() is None and all(z.read(n).replace(b'\r\n',b'\n')==data for n,data in canonical.items())
   assert not any(n.startswith('dev/hui/enderinfection/') for n in z.namelist())
  art.append(dict(put(jar.name,jar.read_bytes()),loader=loader,minecraft='1.20.1' if loader=='forge' else '1.21.1'))
  assert len(result['client_screenshots'])==12
  for shot in result['client_screenshots']:
   p=ROOT/shot['path'];assert digest(p.read_bytes())==shot['sha256'];shots.append({'loader':loader,'file':'screenshots/'+loader+'/'+p.name,'sha256':shot['sha256'],'source':p})
 core_tests=sum(t['required_passed'] for t in core['tests']);assert core_tests>=147
 images=BASE/'docs/images';images.mkdir(parents=True,exist_ok=True)
 for name,frame in [('processor-panel.png','processor_running.png'),('production-line.png','production_line.png'),('cable-detail.png','cable_detail.png')]:
  source=BASE/'build/validation/clients/forge'/frame;shutil.copyfile(source,images/name)
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
  for p in sorted(images.glob('*.png')):z.writestr('images/'+p.name,p.read_bytes())
  z.writestr('index.html',''.join(gallery)+'</div></html>')
 with zipfile.ZipFile(ASSETS/guide_name) as z:assert z.testzip() is None
 p=ASSETS/guide_name;rows.append({'file':p.name,'bytes':p.stat().st_size,'sha256':digest(p.read_bytes())})
 public={'status':'PASS','version':VERSION,'tag':TAG,'core_tested':core['version'],'core_required_passed':core_tests,'addon_required_passed':24,'server_processes':6,'client_screenshots':24,'coal_total_fe':102400,'post_fuel_ticks':200,'assets':art,'screenshots':[{k:v for k,v in s.items() if k!='source'} for s in shots],'source_hash_policy':'UTF-8 line endings normalized to LF; binary files exact','behavior_sources':sources()}
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
