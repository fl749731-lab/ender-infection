"""Use qegg to publish a verified, fixed-version addon without altering core release metadata."""
from pathlib import Path
import argparse,datetime,json,os,subprocess,sys
from package_release import BASE,ROOT,OUT,ASSETS,VERSION,TAG,verify

def main():
 p=argparse.ArgumentParser();p.add_argument('--dry-run',action='store_true');args=p.parse_args()
 receipt=verify();env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='Never')
 # The caller must commit the release sources first; only temporary qegg intent differs.
 assert not subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip(),'Commit release sources before publishing'
 credential=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env,check=True)
 fields=dict(line.split('=',1) for line in credential.stdout.splitlines() if '=' in line)
 if not fields.get('password'):raise RuntimeError('Existing GitHub credential unavailable')
 env['GH_TOKEN']=env['GITHUB_TOKEN']=fields['password']
 now=datetime.datetime.now(datetime.timezone.utc).isoformat();key='java@addons/resonant-industry/forge'
 record=BASE/'.changes/resonant-industry-alpha1.md';body=record.read_text('utf-8').split('---',2)[-1].strip()
 marker={'schema_version':1,'version':VERSION,'created_at':now,'mode':'commit','channel':'alpha','repo':{'host':'github.com','owner':'fl749731-lab','name':'ender-infection','url':'https://github.com/fl749731-lab/ender-infection'},'verified':{'commands':['python tools/build_dual_version.py --clean --gametests','python addons/resonant-industry/tools/build_dual.py --clean --gametests --clients'],'skipped':False,'at':now},'packages':[{'name':key,'path':'addons/resonant-industry/forge','resolver':'java','current':VERSION,'next':VERSION,'bump':'minor','tag':TAG,'publish':False}],'releases':[{'version':VERSION,'tag':TAG,'packages':[key],'prerelease':'alpha'}],'changes':[{'file':record.relative_to(ROOT).as_posix(),'type':'feat','scope':'resonant-industry','bump':'minor','packages':[key],'body':body}],'qegg':{'version':'0.12.0'}}
 paths=[ROOT/'.quailegg/release.json',ROOT/'.quailegg/pre-release.lock'];backups={f:f.read_bytes() if f.exists() else None for f in paths}
 try:
  paths[0].write_text(json.dumps(marker,ensure_ascii=False,indent=2)+'\n','utf-8');paths[1].write_text(json.dumps({'schema_version':1,'channel':'alpha','version':VERSION})+'\n','utf-8')
  command=['qegg','--config',str(BASE/'qegg.toml'),'--cwd',str(ROOT),'release','--yes','--allow-dirty','--assets-dir',str(ASSETS)]
  if args.dry_run:command.append('--dry-run')
  subprocess.run(command,cwd=ROOT,env=env,check=True)
 finally:
  for f,data in backups.items():
   if data is None:f.unlink(missing_ok=True)
   else:f.write_bytes(data)
if __name__=='__main__':main()
