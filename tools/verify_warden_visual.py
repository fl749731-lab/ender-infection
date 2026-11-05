"""Verify and collect an opt-in native Warden client run without touching user instances."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT=Path(__file__).resolve().parents[1]
FRAMES=['warning','front','side','back','night_glow','walk','claw_windup','claw_hit','stomp_windup','stomp_hit','burst','console_one','core_exposed','death_mid','death_kneel','reward_claimed','inventory','core_cooldown']

def verify(loader: str, log: Path, screenshots: Path, collect: Path) -> dict:
    text=log.read_text('utf-8',errors='replace')
    captured=re.findall(r'WARDEN_VISUAL_CAPTURE ([a-z_]+)',text)
    if captured!=FRAMES or 'WARDEN_VISUAL_PASS step=23 shots=18' not in text or 'WARDEN_VISUAL_FAIL' in text:
        raise ValueError(f'Incomplete native Warden run: {log}')
    if re.search(r'(?:ERROR.*(?:containment_warden|animation)|(?:animation|model).*containment_warden.*not found)',text,re.I):
        raise ValueError('Runtime model/animation error in client log')
    collect.mkdir(parents=True,exist_ok=True);frames=[]
    for name in FRAMES:
        source=screenshots/f'warden_{name}.png';data=source.read_bytes()
        if not data.startswith(b'\x89PNG\r\n\x1a\n') or len(data)<1024:
            raise ValueError(f'Invalid native frame: {source}')
        target=collect/source.name;shutil.copyfile(source,target)
        frames.append({'name':name,'file':target.relative_to(ROOT).as_posix(),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    result={'loader':loader,'status':'PASS','native_frames':len(frames),'actual_F8_tiles':6,
            'scope':'Real game renderer, synced AI animations, native terminal packets, reward inventory and item cooldown; research unlock, stationary combat capture and final kill are acceptance fixture actions.',
            'frames':frames,'log':log.relative_to(ROOT).as_posix()}
    (collect/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--loader',required=True,choices=['forge','neoforge']);p.add_argument('--log',required=True,type=Path);p.add_argument('--screenshots',required=True,type=Path);p.add_argument('--collect',required=True,type=Path);a=p.parse_args()
    r=verify(a.loader,a.log.resolve(),a.screenshots.resolve(),a.collect.resolve());print(json.dumps({k:v for k,v in r.items() if k!='frames'},ensure_ascii=False))
