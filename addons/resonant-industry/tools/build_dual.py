"""Build the independent addon against the local core, using separate, native loader runs."""
from __future__ import annotations
import argparse, hashlib, json, os, re, shutil, sys, time, zipfile
from pathlib import Path
BASE=Path(__file__).resolve().parents[1]
ROOT=BASE.parents[1]
sys.path.insert(0,str(ROOT/'tools'))
import build_dual_version as dual
OUT=BASE/'build/validation'
dual.OUTPUT=OUT

def persistence(loader,major):
    folder=(BASE/loader).relative_to(ROOT).as_posix()
    run=BASE/loader/'build'/f'persistence-{time.time_ns()}';run.mkdir(parents=True)
    (run/'eula.txt').write_text('eula=true\n','utf-8')
    (run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=0\n','utf-8')
    logs=[]
    for stage in ['seed','resume','finished']:
        log=dual.run_project('Industry '+loader,folder,major,['runServer',f'-PindustryPersistenceDirectory={run}',f'-PindustryPersistenceStage={stage}'],f'{loader}-persistence-{stage}.log')
        content=log.read_text('utf-8',errors='replace')
        if f'INDUSTRY_PERSISTENCE_PASS {stage}' not in content or 'INDUSTRY_PERSISTENCE_FAIL' in content:raise RuntimeError(f'Persistence failed: {log}')
        if stage=='resume' and not all(x in content for x in ['INDUSTRY_ACTIVE_RESTART_PASS','INDUSTRY_CHUNKS_UNLOADED_PASS','INDUSTRY_CHUNKS_RELOADED_PASS','INDUSTRY_TWO_BATCHES_PASS']):raise RuntimeError(f'Missing restart/unload proof: {log}')
        logs.append(log.relative_to(ROOT).as_posix())
    lines=(run/'industry-identities.txt').read_text().splitlines()
    assert len(lines)==4 and len(set(lines[:3]))==3 and int(lines[3])>=40
    return {'status':'PASS','processes':3,'world':run.relative_to(ROOT).as_posix(),'identities':lines[:3],'saved_progress':int(lines[3]),'logs':logs}

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
        result={'loader':loader,'java':major,'file':jar.relative_to(ROOT).as_posix(),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
        if args.gametests:
            blueprint=BASE/loader/'build/verified-blueprints.json';blueprint.unlink(missing_ok=True)
            log=dual.run_project('Industry '+loader,folder,major,['runGameTestServer'],f'{loader}-native-tests.log');result['tests']=dual.verify(log,12)
            data=json.loads(blueprint.read_text('utf-8'));assert len(data['machines'])>=3;result['blueprints_sha256']=hashlib.sha256(blueprint.read_bytes()).hexdigest()
            result['persistence']=persistence(loader,major)
        if args.clients:
            options=BASE/loader/'build/client-review/options.txt';options.parent.mkdir(parents=True,exist_ok=True)
            lines=options.read_text('utf-8').splitlines() if options.exists() else []
            # Inspect the actual Chinese and English layouts across the two clients.
            lines=[line for line in lines if not line.startswith('lang:')];lines.append('lang:'+('zh_cn' if loader=='forge' else 'en_us'));options.write_text('\n'.join(lines)+'\n','utf-8')
            log=dual.run_project('Industry '+loader,folder,major,['runClient','-PindustryVisualCheck'],f'{loader}-client.log');content=log.read_text('utf-8',errors='replace');assert 'INDUSTRY_VISUAL_PASS step=25 shots=12' in content and 'INDUSTRY_VISUAL_FAIL' not in content
            captures=[]
            for name in ['preview_missing','generator_running','processor_running','centrifuge_running','centrifuge_complete','production_line','generator_detail','processor_detail','centrifuge_detail','cable_detail','handbook','layer_blueprint']:
                path=BASE/loader/'build/client-review/screenshots'/f'{name}.png';assert path.is_file();dest=OUT/'clients'/loader/path.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest);captures.append({'path':dest.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
            result['client_screenshots']=captures
        reports.append(result)
    if args.gametests and args.loader=='both':
        a=json.loads((BASE/'forge/build/verified-blueprints.json').read_text());b=json.loads((BASE/'neoforge/build/verified-blueprints.json').read_text());assert a==b,'Loader layout mismatch'
    result={'status':'PASS','version':version,'published':False,'gametests':args.gametests,'clients':args.clients,'artifacts':reports}
    receipt.write_text(json.dumps(result,indent=2)+'\n','utf-8');print(json.dumps(result,indent=2),flush=True)

if __name__=='__main__':main()
