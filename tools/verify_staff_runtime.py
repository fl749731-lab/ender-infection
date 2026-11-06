"""Collect and verify real dual-client staff evidence against current packaged resources."""
import argparse,hashlib,json,re,shutil,zipfile
from pathlib import Path
from PIL import Image
from export_staff_rework import build,validate,PACK,ROOT,ASSETS

DEST=ROOT/'previews/staff-public-v1'
PHASES=['first_right_idle','first_right_cast','first_right_recover','first_right_recast','first_left_idle','first_left_cast','third_right','third_left','inventory','ground','dark_right','dark_cast']
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text('utf8'))

def collect():
    validate();files,row=build();hashes={};reports={}
    for lane,project in [('forge','ender_infection_colored'),('neo','ender_infection_neoforge')]:
        output=DEST/'runtime'/lane;output.mkdir(parents=True,exist_ok=True)
        for kind in ['visual','native']:
            source=ROOT/'build/staff-public-checks'/f'{project}-{kind}.log';shutil.copyfile(source,output/(kind+'.log'))
        text=(output/'visual.log').read_text('utf8',errors='replace')
        if text.count('STAFF_NATIVE_BREAK broken=true')!=1:raise ValueError('Missing final durability check '+lane)
        if text.count('STAFF_VISUAL_PASS screenshots=12')!=1:raise ValueError('Incomplete client '+lane)
        rows=re.findall(r'STAFF_VISUAL_CAPTURE (\w+) fork=([-\d.Ee]+) core_scale=([-\d.Ee]+) triggered=(true|false) block=(\d+) sky=(\d+)',text)
        if [r[0] for r in rows]!=PHASES:raise ValueError('Missing/duplicate staff phases '+lane)
        casts=re.findall(r'STAFF_NATIVE_CAST phase=(\w+) damage=(\d+) instance=(-?\d+) duplicate=blocked',text)
        if [c[0] for c in casts]!=['first_right_cast','first_right_recast','first_left_cast','dark_cast']:raise ValueError('Missing native uses')
        if [int(c[1]) for c in casts]!=[2,4,2,2] or casts[0][2]!=casts[1][2] or len({c[2] for c in casts})!=3:raise ValueError('Stack durability/identity mismatch')
        frames=[]
        for phase,angle,scale,triggered,block,sky in rows:
            if phase.endswith('cast') and (triggered!='true' or abs(float(angle))<.03 or float(scale)<=1):raise ValueError('Cast not animated')
            if phase=='first_right_recover' and (triggered!='false' or abs(float(angle))>.01):raise ValueError('Cast did not recover')
            image=ROOT/project/'build/staff-visual-check/screenshots'/(phase+'.png');shutil.copyfile(image,output/image.name)
            with Image.open(image) as frame:
                if frame.size!=(1280,800):raise ValueError('Unexpected screenshot size')
                if phase.startswith('dark'):
                    if block!='0' or sky!='0':raise ValueError('Unlit test room is lit')
                    # Only the world view, excluding hotbar / inventory glow.
                    pixels=sum(1 for r,g,b in frame.convert('RGB').crop((750,100,1270,650)).getdata() if b>35 and b>g*1.35 and r>g*1.2)
                    if pixels<100:raise ValueError('No visible crystal in zero light')
            frames.append(dict(phase=phase,file=image.name,sha256=sha(image),fork=float(angle),core_scale=float(scale),triggered=triggered=='true',block=int(block),sky=int(sky)))
        native=(output/'native.log').read_text('utf8',errors='replace')
        count=re.search(r'(\d+) required tests passed',native,re.I)
        if not count or int(count[1])!=36:raise ValueError('Dedicated server test count mismatch '+lane)
        reports[lane]=dict(frames=frames,casts=casts,native_tests=36)
    development=DEST/'runtime/development';development.mkdir(parents=True,exist_ok=True)
    source=ROOT/'build/dual-version-validation'
    for name in ['verified-builds.json','ender_infection_colored-build.log','ender_infection_neoforge-build.log','forge-addon-build.log','neoforge-addon-build.log']:shutil.copyfile(source/name,development/name)
    receipt=read(development/'verified-builds.json')
    jar_rows=receipt.get('jars',receipt.get('artifacts',[]))
    if len(jar_rows)!=2:raise ValueError('Expected two final development JARs')
    for project in ['ender_infection_colored','ender_infection_neoforge']:
        jars=[p for p in (ROOT/project/'build/libs').glob('*.jar') if not any(t in p.name for t in ['sources','dev','javadoc'])]
        jar=max(jars,key=lambda p:p.stat().st_mtime)
        if sha(jar) not in json.dumps(receipt):raise ValueError('JAR differs from final build receipt')
        with zipfile.ZipFile(jar) as z:
            for rel,data in files.items():
                actual=z.read('assets/ender_infection/'+rel)
                same=json.loads(actual)==json.loads(data) if rel.endswith('.json') else actual==data
                if not same:raise ValueError('Wrong packaged staff '+rel)
            if json.loads(z.read('assets/ender_infection/third_party/model_credits.json'))!=read(ASSETS/'third_party/model_credits.json'):raise ValueError('Missing staff attribution')
    paths=[ASSETS/rel for rel in files]+[ASSETS/'third_party/model_credits.json']
    paths += [PACK/'models/resonance_staff.bbmodel',PACK/'export-lock.json',PACK/'mcp-verification.json',PACK/'provenance.json',PACK/'source/void-staff.viewer.json']
    for project in ['ender_infection_colored','ender_infection_neoforge']:
        paths+=[ROOT/project/'build.gradle']
        paths += [ROOT/project/'src/main/java/dev/hui/enderinfection'/p for p in ['design/ResonanceStaff.java','design/DesignWeapon.java','design/DesignContent.java','client/ResonanceStaffRenderer.java','client/StaffVisualCheck.java']]
    paths += [ROOT/'tools'/p for p in ['export_staff_rework.py','export_design_assets.py','validate_design_assets.py','verify_staff_runtime.py']]
    hashes={p.relative_to(ROOT).as_posix():sha(p) for p in paths}
    evidence={p.relative_to(DEST).as_posix():sha(p) for p in (DEST/'runtime').rglob('*') if p.is_file()}
    result=dict(status='PASS',release_gate=False,scope='Licensed staff development validation; not full release gate',native_tests=72,screenshots=24,model=row,platforms=reports,source_hashes=hashes,evidence_hashes=evidence)
    DEST.mkdir(parents=True,exist_ok=True);(DEST/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    return result

def verify():
    validate();result=read(DEST/'verification.json')
    for rel,digest in result['source_hashes'].items():
        if sha(ROOT/rel)!=digest:raise ValueError('Changed source '+rel)
    for rel,digest in result['evidence_hashes'].items():
        if sha(DEST/rel)!=digest:raise ValueError('Changed evidence '+rel)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--collect',action='store_true');a=p.parse_args();r=collect() if a.collect else verify();print(json.dumps({k:r[k] for k in ['status','release_gate','native_tests','screenshots']}))
