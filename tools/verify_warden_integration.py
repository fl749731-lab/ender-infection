"""Check final dual-loader Warden artifacts against sources and native evidence."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import zipfile
from verify_gametest_log import verify

ROOT=Path(__file__).resolve().parents[1]
PREVIEW=ROOT/'previews/institute-boss-alpha7/runtime'
BUILD=ROOT/'build/institute-boss-alpha7'


def require(condition,message):
    if not condition:
        raise ValueError(message)


def portable(value):
    if isinstance(value,dict):return {k:portable(v) for k,v in value.items()}
    if isinstance(value,list):return [portable(v) for v in value]
    if isinstance(value,str):
        for prefix in (str(ROOT)+'\\',ROOT.as_posix()+'/'):
            if value.startswith(prefix):return value[len(prefix):].replace('\\','/')
        return value.replace('\\','/')
    return value


def main():
    gate=json.loads((ROOT/'build/dual-version-validation/verified-builds.json').read_text('utf-8'))
    require(gate['status']=='PASS' and gate['version']=='0.12.0-alpha.7','Wrong full dual-version gate')
    require(len(gate['tests'])==20,'Expected all twenty native suites')
    for result in gate['tests']:
        log=Path(result['log']);verify(log if log.is_absolute() else ROOT/log,result['required_passed'])
    require(sum(r['required_passed'] for r in gate['tests'])>=195,'Old tests or Warden tests missing')
    latest=[r for r in gate['tests'] if Path(r['log']).name.endswith('-warden.log')]
    # The initial integration gate had 16 cases. New full runs already include
    # auto-target coverage and need no machine-local follow-up receipt.
    if len(latest)!=2 or any(r['required_passed']<17 for r in latest):
        latest=json.loads((BUILD/'checks/final-warden-tests.json').read_text('utf-8'))
    require(len(latest)==2,'Expected two final Warden suites')
    for result in latest:verify(Path(result['log']),17)
    current=[r for r in gate['tests'] if not Path(r['log']).name.endswith('-warden.log')]+latest
    require(len(current)==20 and sum(r['required_passed'] for r in current)>=197,'Final native suite set incomplete')
    require(len(gate['persistence_checks'])==4 and all(r['status']=='PASS' and r['processes']==3 for r in gate['persistence_checks']),'Incomplete E8/F8 server restarts')
    sources=ROOT/'ender_infection_colored/src/main/resources'
    stage=ROOT/'colored_units/institute_boss_v1/export'
    mapping={'geo/containment_warden.geo.json':'containment_warden.geo.json','animations/containment_warden.animation.json':'containment_warden.animation.json','textures/entity/containment_warden.png':'containment_warden.png','textures/entity/containment_warden_glowmask.png':'containment_warden_glow.png'}
    for resource,export in mapping.items():
        require((sources/'assets/ender_infection'/resource).read_bytes()==(stage/export).read_bytes(),f'Runtime resource differs from MCP asset: {resource}')
    jars=[]
    for folder,major in [('ender_infection_colored',61),('ender_infection_neoforge',65)]:
        files=[p for p in (ROOT/folder/'build/libs').glob('*.jar') if p.name.endswith('-0.12.0-alpha.7.jar')]
        require(len(files)==1,f'Wrong main JAR count: {folder}');jar=files[0]
        with zipfile.ZipFile(jar) as archive:
            entity=archive.read('dev/hui/enderinfection/entity/ContainmentWarden.class')
            require(int.from_bytes(entity[6:8],'big')==major,'Wrong Java bytecode target')
            require(b'PEACEFUL' in entity,'JAR is older than the tested Peaceful guard; rebuild it')
            for resource in mapping:
                actual=archive.read('assets/ender_infection/'+resource)
                expected=(sources/'assets/ender_infection'/resource).read_bytes()
                # Shared-resource conversion compacts Neo JSON; compare its data, not whitespace.
                matches=json.loads(actual)==json.loads(expected) if resource.endswith('.json') else actual==expected
                require(matches,f'JAR contains stale asset: {folder}/{resource}')
            require('assets/ender_infection/models/item/containment_core.json' in archive.namelist(),'Core item model missing')
            data_root='loot_table' if major==65 else 'loot_tables'
            require(f'data/ender_infection/{data_root}/entities/containment_warden.json' in archive.namelist(),'Loader loot table path missing')
            manifest=json.loads(archive.read('data/ender_infection/central_institute.json'))
            require(manifest['layout_version']=='central-institute-v5' and len(manifest['rooms'])==48 and len(manifest['bosses']['F8']['consoles'])==2,'JAR layout stale')
        jars.append({'file':jar.relative_to(ROOT).as_posix(),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()})
    client_results=[]
    for loader in ['forge','neoforge']:
        record=json.loads((PREVIEW/loader/'verification.json').read_text('utf-8'))
        require(record['status']=='PASS' and record['native_frames']==18,'Native client incomplete')
        for frame in record['frames']:
            require(hashlib.sha256((ROOT/frame['file']).read_bytes()).hexdigest()==frame['sha256'],'Client frame changed')
        client_results.append({'loader':loader,'frames':18,'status':'PASS'})
    inputs={}
    for base in ['ender_infection_colored/src/main','ender_infection_neoforge/src/main/java','common/src/main/java']:
        for path in sorted((ROOT/base).rglob('*')):
            if path.is_file():inputs[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
    for name in ['ender_infection_colored/build.gradle','ender_infection_neoforge/build.gradle','tools/generate_central_institute.py','tools/sync_neoforge_resources.py','tools/build_dual_version.py','tools/check_warden_persistence.py']:
        inputs[name]=hashlib.sha256((ROOT/name).read_bytes()).hexdigest()
    BUILD.mkdir(parents=True,exist_ok=True)
    (BUILD/'final-source-freeze.json').write_text(json.dumps(inputs,sort_keys=True,indent=2)+'\n','utf-8')
    aggregate=hashlib.sha256(json.dumps(inputs,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    report={'status':'PASS','version':gate['version'],'release_state_at_validation':'local-development',
        'native_suites':len(current),'native_required_passed':sum(r['required_passed'] for r in current),
        'full_gate_required_passed':sum(r['required_passed'] for r in gate['tests']),
        'final_warden_suites':portable(latest),
        'persistence_checks':portable(gate['persistence_checks']),'dedicated_server_processes':12,
        'client_checks':client_results,'native_client_frames':36,'input_files':len(inputs),'input_set_sha256':aggregate,
        'jars':jars,'scope':'Full dual-version native gate plus final packaging and native-client checks. Research unlocks, stationary combat captures and final client kills are fixtures; server tests cover production combat and prerequisites.'}
    (PREVIEW/'full-gate.json').write_text(json.dumps(portable(gate),ensure_ascii=False,indent=2)+'\n','utf-8')
    (PREVIEW/'final-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':
    main()
