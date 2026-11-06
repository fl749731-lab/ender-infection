"""Verify the current development VFX build. This does not replace the clean release gate."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import zipfile
from verify_gametest_log import verify as verify_tests
from verify_warden_vfx_visual import verify as verify_client

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build/warden-vfx-v1'
OUTPUT=ROOT/'previews/containment-warden-vfx-v1/runtime'

def require(condition,message):
    if not condition:raise ValueError(message)

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    packaging=json.loads((BUILD/'build-check/verified-builds.json').read_text('utf-8'))
    require(packaging['status']=='PASS' and packaging['version']=='0.12.0-alpha.7','Wrong development build')
    source=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
    description=json.loads((source/'particles/warden_vfx.json').read_text('utf-8'))
    resources=['particles/warden_vfx.json']+[f"textures/particle/{name.split(':')[1]}.png" for name in description['textures']]
    require(len(resources)==8 and all('particle/' not in name for name in description['textures']),'Wrong sprite atlas identifiers')
    tests=[];clients=[];jars=[]
    for loader,runner,folder,major in [('forge','forge','ender_infection_colored',61),('neoforge','neo','ender_infection_neoforge',65)]:
        tests.append(verify_tests(BUILD/f'checks/{runner}-runGameTestServer.log',17))
        clients.append(verify_client(loader,BUILD/f'checks/{runner}-runClient.log',ROOT/folder/'build/warden-vfx-check/screenshots',OUTPUT/loader))
        candidates=list((ROOT/folder/'build/libs').glob('*-0.12.0-alpha.7.jar'))
        require(len(candidates)==1,'Wrong main JAR count');jar=candidates[0]
        require(any(r['sha256']==sha(jar) for r in packaging['jars']),'JAR changed after dual build')
        with zipfile.ZipFile(jar) as archive:
            entity=archive.read('dev/hui/enderinfection/entity/ContainmentWarden.class')
            require(int.from_bytes(entity[6:8],'big')==major and b'VFX_SEQUENCE' in entity,'Wrong JVM target or old entity synchronization')
            require('dev/hui/enderinfection/client/WardenParticle.class' in archive.namelist(),'Missing VFX renderer')
            for resource in resources:
                actual=archive.read('assets/ender_infection/'+resource);expected=(source/resource).read_bytes()
                require(json.loads(actual)==json.loads(expected) if resource.endswith('.json') else actual==expected,f'Stale JAR sprite: {resource}')
        jars.append({'loader':loader,'file':jar.relative_to(ROOT).as_posix(),'bytes':jar.stat().st_size,'sha256':sha(jar)})
    inputs={}
    for name in ('ender_infection_colored/src/main','ender_infection_neoforge/src/main/java','common/src/main/java'):
        for path in sorted((ROOT/name).rglob('*')):
            if path.is_file():inputs[path.relative_to(ROOT).as_posix()]=sha(path)
    for name in ('ender_infection_colored/build.gradle','ender_infection_neoforge/build.gradle','tools/generate_warden_vfx_assets.py','tools/verify_warden_vfx_visual.py','tools/verify_warden_vfx_integration.py','tools/sync_neoforge_resources.py'):
        inputs[name]=sha(ROOT/name)
    (BUILD/'source-freeze.json').write_text(json.dumps(inputs,sort_keys=True,indent=2)+'\n','utf-8')
    report={'status':'PASS','version':packaging['version'],'release_state':'development-not-published',
            'native_required_passed':sum(t['required_passed'] for t in tests),'native_client_frames':sum(c['native_frames'] for c in clients),
            'client_checks':[{k:v for k,v in c.items() if k!='frames'} for c in clients],
            'input_files':len(inputs),'input_set_sha256':hashlib.sha256(json.dumps(inputs,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'jars':jars,'release_gate_run_this_round':False,
            'scope':'Focused real Warden GameTests (17 per loader), real clients, dual main/API/example builds. Historical 197-test clean integration evidence remains separate. Run tools/build_dual_version.py --clean --gametests before publishing. No two-client multiplayer or GPU/FPS benchmark was performed.'}
    (OUTPUT/'final-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
