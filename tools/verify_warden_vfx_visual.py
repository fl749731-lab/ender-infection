"""Collect real, isolated Forge/Neo Warden VFX captures and enforce event/quad coverage."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT=Path(__file__).resolve().parents[1]
FRAMES=['warning','front','side','back','night_glow','walk','claw_windup','claw_hit',
        'stomp_windup','stomp_hit','burst_warning','pulse_1','pulse_2','pulse_3','burst',
        'console_discharge','console_one','core_exposed','core_recovery','death_mid','death_kneel','reward_claimed','inventory','core_cooldown']
KINDS=['CLAW_WARNING','STOMP_WARNING','BURST_WARNING','CLAW','SHOCK','PULSE','CORE_OPEN','CORE','CORE_CLOSE','CONSOLE']

def verify(loader: str, log: Path, screenshots: Path, collect: Path) -> dict:
    text=log.read_text('utf-8',errors='replace')
    if re.findall(r'WARDEN_VFX_CAPTURE ([a-z_0-9]+)',text)!=FRAMES or 'WARDEN_VFX_PASS step=23 shots=24' not in text or 'WARDEN_VFX_FAIL' in text:
        raise ValueError(f'Incomplete native VFX run: {log}')
    stats=re.search(r'WARDEN_VFX_STATS events=\{([^}]+)\} peak_budget=(\d+) core_quads=(\d+) rendered_quads=(\d+)',text)
    if not stats:raise ValueError('Missing real client VFX counters')
    events={name:int(n) for name,n in re.findall(r'([A-Z_]+)=(\d+)',stats[1])}
    if any(events.get(kind,0)<(3 if kind=='PULSE' else 2 if kind=='CONSOLE' else 1) for kind in KINDS):raise ValueError(f'Missing VFX events: {events}')
    peak,core,draws=map(int,stats.groups()[1:])
    if peak>96 or not 0<core<=16 or draws<=0:raise ValueError('Invalid actual client geometry budget')
    if 'WARDEN_VFX_SETTINGS particles=MINIMAL case=stomp' not in text or 'WARDEN_VFX_SETTINGS particles=ALL case=burst' not in text:raise ValueError('Particle setting coverage missing')
    if re.search(r'(?:ERROR.*(?:warden|particle)|(?:animation|model).*containment_warden.*not found)',text,re.I):raise ValueError('Runtime Warden/particle error')
    if re.search(r'Missing particle sprites:.*warden_',text,re.I):raise ValueError('Warden sprite atlas contains missing textures')
    collect.mkdir(parents=True,exist_ok=True);frames=[]
    for name in FRAMES:
        source=screenshots/f'warden_vfx_{name}.png';data=source.read_bytes()
        if not data.startswith(b'\x89PNG\r\n\x1a\n') or len(data)<1024:raise ValueError(f'Invalid PNG: {source}')
        target=collect/source.name;shutil.copyfile(source,target)
        frames.append({'name':name,'file':target.relative_to(ROOT).as_posix(),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    result={'loader':loader,'status':'PASS','native_frames':len(frames),'events':events,
            'peak_simultaneous_quad_budget_per_boss':peak,'core_quads':core,'quad_submissions_over_entire_run':draws,
            'scope':'Actual F8 room, real AI timing and console packets; isolated fixture unlocks progression, holds movement for attack shots, uses a client-only overview camera, then kills boss for reward checks. Budget is geometry, not a GPU benchmark.',
            'frames':frames,'log':log.relative_to(ROOT).as_posix()}
    (collect/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--loader',required=True,choices=['forge','neoforge']);p.add_argument('--log',required=True,type=Path);p.add_argument('--screenshots',required=True,type=Path);p.add_argument('--collect',required=True,type=Path);a=p.parse_args()
    r=verify(a.loader,a.log.resolve(),a.screenshots.resolve(),a.collect.resolve());print(json.dumps({k:v for k,v in r.items() if k!='frames'},ensure_ascii=False))
