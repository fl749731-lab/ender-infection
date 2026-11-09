"""Build the independent addon against the local core, using separate, native loader runs."""
from __future__ import annotations
import argparse, hashlib, json, os, re, shutil, sys, time, zipfile
from pathlib import Path
from validation_contract import INDUSTRY_FRAMES, LABWARE_FRAMES, NATIVE_MINIMUM, verify_blueprints
BASE=Path(__file__).resolve().parents[1]
ROOT=BASE.parents[1]
sys.path.insert(0,str(ROOT/'tools'))
import build_dual_version as dual
OUT=BASE/'build/validation'
dual.OUTPUT=OUT

def test_world(loader, prefix, level_type='flat'):
    run = BASE / loader / 'build' / f'{prefix}-{time.time_ns()}'
    run.mkdir(parents=True)
    (run / 'eula.txt').write_text('eula=true\n', 'utf-8')
    (run / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nspawn-protection=0\n'
        f'view-distance=2\nsimulation-distance=2\nlevel-type=minecraft:{level_type}\n'
        'generate-structures=false\nmax-tick-time=0\n'
        + ('level-seed=40962177\n' if level_type == 'normal' else ''), 'utf-8')
    return run

def persistence(loader,major,compound=False):
    folder=(BASE/loader).relative_to(ROOT).as_posix()
    mode='compound-' if compound else ''
    run=test_world(loader, f'{mode}persistence')
    logs=[]
    for stage in ['seed','resume','finished']:
        tasks=['runServer',f'-PindustryPersistenceDirectory={run}',f'-PindustryPersistenceStage={stage}']
        if compound:tasks.append('-PindustryCompoundPersistence=true')
        log=dual.run_project('Industry '+loader,folder,major,tasks,f'{loader}-{mode}persistence-{stage}.log')
        content=log.read_text('utf-8',errors='replace')
        if f'INDUSTRY_PERSISTENCE_PASS {stage}' not in content or 'INDUSTRY_PERSISTENCE_FAIL' in content:raise RuntimeError(f'Persistence failed: {log}')
        if stage=='resume' and not all(x in content for x in ['INDUSTRY_ACTIVE_RESTART_PASS','INDUSTRY_CHUNKS_UNLOADED_PASS','INDUSTRY_CHUNKS_RELOADED_PASS','INDUSTRY_TWO_BATCHES_PASS']):raise RuntimeError(f'Missing restart/unload proof: {log}')
        if compound and stage!='seed' and 'INDUSTRY_COMPOUND_TWO_BATCHES_PASS' not in content:raise RuntimeError(f'Missing compound restart proof: {log}')
        logs.append(log.relative_to(ROOT).as_posix())
    lines=(run/'industry-identities.txt').read_text().splitlines()
    assert len(lines)==4 and len(set(lines[:3]))==3 and int(lines[3])>=40
    return {'status':'PASS','processes':3,'world':run.relative_to(ROOT).as_posix(),'identities':lines[:3],'saved_progress':int(lines[3]),'logs':logs}

def labware_persistence(loader,major):
    run=test_world(loader, 'labware-persistence')
    logs=[]
    for stage in ['seed','resume','finished']:
        log=dual.run_project('Labware '+loader,(BASE/loader).relative_to(ROOT).as_posix(),major,['runServer',f'-PlabwarePersistenceDirectory={run}',f'-PlabwarePersistenceStage={stage}'],f'{loader}-labware-persistence-{stage}.log')
        content=log.read_text('utf-8',errors='replace')
        assert f'LABWARE_PERSISTENCE_PASS {stage}' in content and 'LABWARE_PERSISTENCE_FAIL' not in content
        assert f'CHEMISTRY_PERSISTENCE_PASS {stage}' in content
        assert f'SEPARATION_PERSISTENCE_PASS {stage}' in content
        if stage=='resume':assert all(marker in content for marker in ['LABWARE_CHUNKS_UNLOADED_PASS','LABWARE_CHUNKS_RELOADED_PASS'])
        logs.append(log.relative_to(ROOT).as_posix())
    assert (run/'labware-persistence.txt').read_text().splitlines()==['seed','resume']
    return {'status':'PASS','processes':3,'world':run.relative_to(ROOT).as_posix(),'logs':logs}

def workshop_persistence():
    run = test_world('neoforge', 'workshop-persistence')
    folder = (BASE / 'neoforge').relative_to(ROOT).as_posix()
    logs = []
    for stage in ('seed', 'resume', 'finished'):
        tasks = ['runServer', f'-PworkshopPersistenceDirectory={run}', f'-PworkshopPersistenceStage={stage}']
        log = dual.run_project('NeoForge workshop', folder, 21, tasks, f'neoforge-workshop-persistence-{stage}.log')
        content = log.read_text('utf-8', errors='replace')
        assert f'WORKSHOP_PERSISTENCE_PASS {stage}' in content and 'WORKSHOP_PERSISTENCE_FAIL' not in content, log
        logs.append(log.relative_to(ROOT).as_posix())
    return {'status': 'PASS', 'processes': len(logs), 'world': run.relative_to(ROOT).as_posix(), 'logs': logs}

def mineral_worldgen():
    run = test_world('neoforge', 'mineral-worldgen', 'normal')
    folder = (BASE / 'neoforge').relative_to(ROOT).as_posix()
    log = dual.run_project('NeoForge minerals', folder, 21,
                           ['runServer', f'-PmineralWorldCheckDirectory={run}'], 'neoforge-mineral-worldgen.log')
    content = log.read_text('utf-8', errors='replace')
    assert 'MINERAL_WORLDGEN_PASS' in content and 'MINERAL_WORLDGEN_FAIL' not in content, log
    return {'status': 'PASS', 'world': run.relative_to(ROOT).as_posix(), 'log': log.relative_to(ROOT).as_posix()}

def labware_client(loader,major):
    options=BASE/loader/'build/client-review/options.txt'
    lines=[line for line in options.read_text('utf-8').splitlines() if not line.startswith('lang:')];lines.append('lang:zh_cn');options.write_text('\n'.join(lines)+'\n','utf-8')
    log=dual.run_project('Labware '+loader,(BASE/loader).relative_to(ROOT).as_posix(),major,['runClient','-PlabwareVisualCheck'],f'{loader}-labware-client.log')
    content=log.read_text('utf-8',errors='replace')
    assert f'LABWARE_VISUAL_PASS step=70 shots={len(LABWARE_FRAMES)}' in content and 'LABWARE_NATIVE_INTERACTIONS_PASS volume=1000' in content and 'LABWARE_VISUAL_FAIL' not in content
    assert 'CHEMISTRY_NATIVE_INTERACTIONS_PASS input=500 remaining=450 evaporated=50 crystals=1' in content
    assert 'SEPARATION_NATIVE_INTERACTIONS_PASS suspension=50 filtrate=50 distilled=40 concentrate=10 calcite=1' in content
    assert 'LABWARE_NARROW_INTERACTIONS_PASS gui=320x240 volume=100 calcite=2 coal=4' in content
    assert not re.search(r'Parsing error loading recipe resonant_industry:|Missing textures in model resonant_industry:',content),'Broken addon recipe/model in actual client'
    captures=[]
    for name in LABWARE_FRAMES:
        path=BASE/loader/'build/client-review/screenshots'/f'{name}.png';assert path.is_file();dest=OUT/'clients'/loader/path.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest);captures.append({'path':dest.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
    return {'status':'PASS','log':log.relative_to(ROOT).as_posix(),'screenshots':captures,'conserved_volume_mb':1000,
            'chemistry':{'input_mb':500,'remaining_mb':450,'evaporated_mb':50,'salt_crystals':1},
            'separation':{'suspension_mb':50,'filtrate_mb':50,'distilled_mb':40,'concentrate_mb':10,'precipitate':1}}

def manual_bookmark_client(loader, major):
    run = BASE / loader / 'build/manual-bookmark-review'
    run.mkdir(parents=True, exist_ok=True)
    options = run / 'options.txt'
    lines = options.read_text('utf-8').splitlines() if options.exists() else []
    lines = [line for line in lines if not line.startswith(('lang:', 'guiScale:', 'fullscreen:'))]
    options.write_text('\n'.join(lines + ['lang:zh_cn', 'guiScale:2', 'fullscreen:false']) + '\n', 'utf-8')
    logs, captures = [], []
    for stage, names in (
        ('seed', ('manual-bookmark-structure-seed', 'manual-distillation-layout', 'manual-bookmark-reading-seed')),
        ('resume', ('manual-bookmark-structure-resume', 'manual-bookmark-reading-resume')),
    ):
        log = dual.run_project('Manual bookmark ' + loader, (BASE / loader).relative_to(ROOT).as_posix(), major,
                               ['runClient', '-PmanualBookmarkCheck=' + stage], f'{loader}-manual-bookmark-{stage}.log')
        content = log.read_text('utf-8', errors='replace')
        assert f'MANUAL_BOOKMARK_PASS stage={stage} structure=processor:2 reading=distillation:2' in content, log
        assert 'MANUAL_BOOKMARK_FAIL' not in content, log
        if stage == 'seed':
            assert 'MANUAL_BOOKMARK_INVALID_FILE_PASS' in content, log
        logs.append(log.relative_to(ROOT).as_posix())
        for name in names:
            assert 'MANUAL_BOOKMARK_CAPTURE ' + name in content, log
            source = run / 'screenshots' / (name + '.png')
            assert source.is_file(), source
            destination = OUT / 'clients' / loader / source.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            captures.append({'path': destination.relative_to(ROOT).as_posix(),
                             'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
    return {'status': 'PASS', 'processes': 2, 'logs': logs, 'screenshots': captures}


def manual_locator_client(loader, major, language='zh_cn'):
    run = BASE / loader / 'build/manual-locator-review'
    run.mkdir(parents=True, exist_ok=True)
    options = run / 'options.txt'
    lines = options.read_text('utf-8').splitlines() if options.exists() else []
    lines = [line for line in lines if not line.startswith(('lang:', 'guiScale:', 'fullscreen:'))]
    options.write_text('\n'.join(lines + ['lang:' + language, 'guiScale:2', 'fullscreen:false']) + '\n', 'utf-8')
    log = dual.run_project('Manual locator ' + loader, (BASE / loader).relative_to(ROOT).as_posix(), major,
                           ['runClient', '-PmanualLocatorCheck'], f'{loader}-manual-locator-{language}.log')
    content = log.read_text('utf-8', errors='replace')
    assert 'MANUAL_LOCATOR_PASS shots=4 gui=320x240' in content and 'MANUAL_LOCATOR_FAIL' not in content, log
    captures = []
    for name in ('manual-locator-full', 'manual-locator-absent-layer', 'manual-locator-rotated-front', 'manual-locator-narrow'):
        assert 'MANUAL_LOCATOR_CAPTURE ' + name in content, log
        source = run / 'screenshots' / (name + '.png')
        assert source.is_file(), source
        destination = OUT / 'clients' / loader / language / source.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        captures.append({'path': destination.relative_to(ROOT).as_posix(),
                         'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
    return {'status': 'PASS', 'processes': 1, 'language': language, 'log': log.relative_to(ROOT).as_posix(),
            'screenshots': captures}


def build_progress_client(loader, major, language='zh_cn'):
    run = BASE / loader / 'build/build-progress-review'
    run.mkdir(parents=True, exist_ok=True)
    options = run / 'options.txt'
    lines = options.read_text('utf-8').splitlines() if options.exists() else []
    lines = [line for line in lines if not line.startswith(('lang:', 'guiScale:', 'fullscreen:'))]
    options.write_text('\n'.join(lines + ['lang:' + language, 'guiScale:2', 'fullscreen:false']) + '\n', 'utf-8')
    log = dual.run_project('Build progress ' + loader, (BASE / loader).relative_to(ROOT).as_posix(), major,
                           ['runClient', '-PbuildProgressCheck'], f'{loader}-build-progress-{language}.log')
    content = log.read_text('utf-8', errors='replace')
    for marker in ('BUILD_PROGRESS_PASS shots=5 step=9',
                   'BUILD_PROGRESS_UNLOADED_PASS unknown=12 placed=22 total=34 missing=0',
                   'BUILD_PROGRESS_LIFECYCLE_PASS distance=true disconnect=true'):
        assert marker in content, log
    assert 'BUILD_PROGRESS_FAIL' not in content, log
    captures = []
    for name in ('build-progress-missing', 'build-progress-narrow', 'build-progress-layer',
                 'build-progress-ready', 'build-progress-unloaded'):
        assert 'BUILD_PROGRESS_CAPTURE ' + name in content, log
        source = run / 'screenshots' / (name + '.png')
        assert source.is_file(), source
        destination = OUT / 'clients' / loader / language / source.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
        captures.append({'path': destination.relative_to(ROOT).as_posix(),
                         'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()})
    return {'status': 'PASS', 'processes': 1, 'language': language, 'log': log.relative_to(ROOT).as_posix(),
            'screenshots': captures, 'unloaded_cells': 12}


def main():
    p=argparse.ArgumentParser();p.add_argument('--loader',choices=['forge','neoforge','both'],default='both');p.add_argument('--clean',action='store_true');p.add_argument('--gametests',action='store_true');p.add_argument('--clients',action='store_true');args=p.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);receipt=OUT/'verified-builds.json';receipt.unlink(missing_ok=True)
    version=re.search(r"version\s*=\s*'([^']+)'",(BASE/'forge/build.gradle').read_text()).group(1)
    reports=[]
    for loader,major in [('forge',17),('neoforge',21)]:
        if args.loader not in ['both',loader]:continue
        core='ender_infection_colored' if loader=='forge' else 'ender_infection_neoforge'
        dual.run_project('Core '+loader,core,major,['apiDevJar'],f'{loader}-core-api.log')
        folder=(BASE/loader).relative_to(ROOT).as_posix()
        dual.run_project('Industry '+loader,folder,major,(['clean'] if args.clean else [])+['build'],f'{loader}-build.log')
        jar=BASE/loader/'build/libs'/f"resonant-industry-{loader}-{'1.20.1' if major==17 else '1.21.1'}-{version}.jar"
        with zipfile.ZipFile(jar) as z:
            assert z.testzip() is None
            assert 'dev/hui/resonantindustry/IndustryNode.class' in z.namelist()
            assert not any(x.startswith('dev/hui/enderinfection/') for x in z.namelist()),'Addon must not embed core classes'
            metadata='META-INF/mods.toml' if loader=='forge' else 'META-INF/neoforge.mods.toml';text=z.read(metadata).decode();assert 'modId="ender_infection"' in text and 'resonant_industry' in text
            assert f'Implementation-Version: {version}' in z.read('META-INF/MANIFEST.MF').decode()
        core_jar=ROOT/core/'build/api/ender-infection-api-dev.jar'
        with zipfile.ZipFile(core_jar) as core_zip:
            manifest=core_zip.read('data/ender_infection/central_institute.json')
            core_version=re.search(r'Implementation-Version: ([^\r\n]+)',core_zip.read('META-INF/MANIFEST.MF').decode())
        result={'loader':loader,'java':major,'file':jar.relative_to(ROOT).as_posix(),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
                'core':{'file':core_jar.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(core_jar.read_bytes()).hexdigest(),'version':core_version[1] if core_version else 'unknown','institute_manifest_sha256':hashlib.sha256(manifest).hexdigest()}}
        if args.gametests:
            blueprint=BASE/loader/'build/verified-blueprints.json';blueprint.unlink(missing_ok=True)
            log=dual.run_project('Industry '+loader,folder,major,['runGameTestServer'],f'{loader}-native-tests.log');result['tests']=dual.verify(log,NATIVE_MINIMUM[loader])
            common=json.loads((BASE/'docs/blueprints/definitions.json').read_text('utf-8'))
            verify_blueprints(common,json.loads(blueprint.read_text('utf-8')),loader)
            result['blueprints_sha256']=hashlib.sha256(blueprint.read_bytes()).hexdigest()
            result['persistence']=persistence(loader,major)
            result['compound_persistence']=persistence(loader,major,compound=True)
            if loader=='neoforge':
                result['labware_persistence']=labware_persistence(loader,major)
                result['workshop_persistence']=workshop_persistence()
                result['mineral_worldgen']=mineral_worldgen()
        if args.clients:
            options=BASE/loader/'build/client-review/options.txt';options.parent.mkdir(parents=True,exist_ok=True)
            lines=options.read_text('utf-8').splitlines() if options.exists() else []
            # Inspect the actual Chinese and English layouts across the two clients.
            lines=[line for line in lines if not line.startswith('lang:')];lines.append('lang:'+('zh_cn' if loader=='forge' else 'en_us'));options.write_text('\n'.join(lines)+'\n','utf-8')
            log=dual.run_project('Industry '+loader,folder,major,['runClient','-PindustryVisualCheck'],f'{loader}-client.log');content=log.read_text('utf-8',errors='replace');assert f'INDUSTRY_VISUAL_PASS step=44 shots={len(INDUSTRY_FRAMES)}' in content and 'INDUSTRY_VISUAL_FAIL' not in content
            assert 'INDUSTRY_MANUAL_FABULOUS_PASS item_entity_target=true translucent_target=true selection=item_output' in content, log
            captures=[]
            for name in INDUSTRY_FRAMES:
                path=BASE/loader/'build/client-review/screenshots'/f'{name}.png';assert path.is_file();dest=OUT/'clients'/loader/path.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest);captures.append({'path':dest.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
            result['client_screenshots']=captures
            result['manual_bookmark_client']=manual_bookmark_client(loader,major)
            result['manual_locator_client']=manual_locator_client(loader,major,'zh_cn' if loader=='forge' else 'en_us')
            result['build_progress_client']=build_progress_client(loader,major,'zh_cn' if loader=='forge' else 'en_us')
            if loader=='neoforge':result['labware_client']=labware_client(loader,major)
        reports.append(result)
    result={'status':'PASS','version':version,'published':False,'gametests':args.gametests,'clients':args.clients,'artifacts':reports}
    receipt.write_text(json.dumps(result,indent=2)+'\n','utf-8');print(json.dumps(result,indent=2),flush=True)

if __name__=='__main__':main()
