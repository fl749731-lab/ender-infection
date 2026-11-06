"""Seal real player equipment/pose screenshots against the final dual-loader JARs."""
import argparse,hashlib,json,re,shutil,zipfile
from pathlib import Path
from PIL import Image
from export_armor_reworks import ROOT,PACK,ASSETS,SPECS,build,validate
from sync_neoforge_resources import transform

DEST=ROOT/'previews/armor-rework-v1'
PHASES=['front','back','walk','crouch','bow','seated','helmet','chestplate','leggings','boots','mixed','inventory','dark']
SINGLES={'helmet','chestplate','leggings','boots'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(p):return json.loads(p.read_text('utf8'))

def collect():
    rows=validate();runtime={}
    for name in SPECS:runtime.update(build(name)[0])
    source_paths=[ASSETS/rel for rel in runtime]+[ASSETS/'third_party/model_credits.json']
    source_paths += [p for p in PACK.rglob('*') if p.is_file() and p.suffix!='.png']
    reports={}
    for lane,project in [('forge','ender_infection_colored'),('neo','ender_infection_neoforge')]:
        output=DEST/'runtime'/lane;output.mkdir(parents=True,exist_ok=True)
        log=ROOT/'build/armor-public-checks'/(project+'-visual.log');shutil.copyfile(log,output/'visual.log');text=log.read_text('utf8',errors='replace')
        if text.count('ARMOR_VISUAL_PASS screenshots=26')!=1 or 'ARMOR_VISUAL_FAIL' in text:raise ValueError('Incomplete client '+lane)
        captures=re.findall(r'ARMOR_VISUAL_CAPTURE (\w+) slots=(\d+) pose_error=([-\d.Ee]+) arm=([-\d.Ee]+) leg=([-\d.Ee]+) body=([-\d.Ee]+) block=(\d+) sky=(\d+)',text)
        expected=[name+'_'+phase for name in SPECS for phase in PHASES]
        if [r[0] for r in captures]!=expected:raise ValueError('Missing/duplicate native poses '+lane)
        frames=[]
        for name,slots,error,arm,leg,body,block,sky in captures:
            phase=next(p for p in PHASES if name.endswith('_'+p))
            if int(slots)!=(1 if phase in SINGLES else 4) or float(error)>.0001:raise ValueError('Incorrect equipment/pose contract '+name)
            if phase=='walk' and abs(float(leg))<.05:raise ValueError('Static walk')
            if phase=='crouch' and abs(float(body))<.2:raise ValueError('Static crouch')
            if phase=='bow' and abs(float(arm))<1:raise ValueError('Static bow')
            if phase=='seated' and abs(float(leg))<1:raise ValueError('Static riding pose')
            image=ROOT/project/'build/armor-visual-check/screenshots'/(name+'.png');shutil.copyfile(image,output/image.name)
            with Image.open(image) as frame:
                if frame.size!=(1280,800):raise ValueError('Unexpected screenshot dimensions')
                if phase=='dark':
                    if block!='0' or sky!='0':raise ValueError('Dark room is lit')
                    # World-only central player silhouette; no inventory/hotbar pixels.
                    count=sum(1 for r,g,b in frame.convert('RGB').crop((450,280,830,770)).getdata() if b>35 and b>g*1.35 and r>g*1.2)
                    if count<150:raise ValueError('No visible crystal in zero light '+name)
            frames.append(dict(name=name,file=image.name,sha256=sha(image),slots=int(slots),pose_error=float(error),arm=float(arm),leg=float(leg),body=float(body),block=int(block),sky=int(sky)))
        reports[lane]=dict(scenarios=26,frames=frames)
        source_paths += [ROOT/project/'build.gradle']
        source_paths += [ROOT/project/'src/main/java/dev/hui/enderinfection'/p for p in ['client/DesignArmorRenderer.java','client/ArmorVisualCheck.java','client/GuardedGlowLayer.java','design/DesignArmor.java','design/DesignArmorEffects.java','design/DesignContent.java']]
    development=DEST/'runtime/development';development.mkdir(parents=True,exist_ok=True)
    for name in ['verified-builds.json','ender_infection_colored-build.log','ender_infection_neoforge-build.log','forge-addon-build.log','neoforge-addon-build.log']:
        shutil.copyfile(ROOT/'build/dual-version-validation'/name,development/name)
    receipt=read(development/'verified-builds.json')
    if receipt['status']!='PASS' or len(receipt['jars'])!=2:raise ValueError('Missing dual build')
    for row in receipt['jars']:
        jar=ROOT/row['file'];neo='neoforge' in row['file']
        if sha(jar)!=row['sha256']:raise ValueError('Stale JAR '+str(jar))
        with zipfile.ZipFile(jar) as archive:
            for rel,data in runtime.items():
                actual=archive.read('assets/ender_infection/'+rel)
                if rel.endswith('.json'):
                    expected=transform(json.loads(data)) if neo else json.loads(data);equal=json.loads(actual)==expected
                else:equal=actual==data
                if not equal:raise ValueError('Wrong packaged armor '+rel)
            if json.loads(archive.read('assets/ender_infection/third_party/model_credits.json'))!=read(ASSETS/'third_party/model_credits.json'):raise ValueError('Wrong armor attribution')
    source_paths += [ROOT/'tools'/name for name in ['export_armor_reworks.py','export_design_assets.py','export_creature_reworks.py','validate_design_assets.py','verify_armor_runtime.py','sync_neoforge_resources.py']]
    evidence={p.relative_to(DEST).as_posix():sha(p) for p in (DEST/'runtime').rglob('*') if p.is_file()}
    result=dict(status='PASS',scope='Armor development validation using actual native player inputs and renderer callbacks; not full release gate',release_gate=False,native_gametests_requested=False,native_client_scenarios=52,screenshots=52,models=rows,platforms=reports,source_hashes={p.relative_to(ROOT).as_posix():sha(p) for p in source_paths},evidence_hashes=evidence)
    (DEST/'verification.json').write_bytes((json.dumps(result,ensure_ascii=False,indent=2)+'\n').encode());return result

def verify():
    validate();result=read(DEST/'verification.json')
    for rel,digest in result['source_hashes'].items():
        if sha(ROOT/rel)!=digest:raise ValueError('Changed source '+rel)
    for rel,digest in result['evidence_hashes'].items():
        if sha(DEST/rel)!=digest:raise ValueError('Changed native evidence '+rel)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--collect',action='store_true');a=p.parse_args();r=collect() if a.collect else verify();print(json.dumps({k:r[k] for k in ['status','native_client_scenarios','screenshots','release_gate']}))
