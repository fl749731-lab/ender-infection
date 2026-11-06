"""Collect complete native room captures, bind them to the current manifest and index them."""
from __future__ import annotations
import argparse
from hashlib import sha256
import json
from pathlib import Path
import re
import shutil
import struct

ROOT=Path(__file__).resolve().parents[1]


def collect(log:Path,destination:Path):
    content=log.read_text('utf8',errors='replace')
    if 'INSTITUTE_ROOM_PREVIEW_FAIL' in content:raise ValueError('Native room capture failed')
    complete=re.search(r'INSTITUTE_ROOM_PREVIEW_PASS rooms=(\d+) screenshots=(\d+) natural=false',content)
    if not complete:raise ValueError('No completed native room preview')
    manifest=ROOT/'ender_infection_neoforge/build/generated/sharedResources/data/ender_infection/central_institute.json'
    digest=sha256(manifest.read_bytes()).hexdigest()
    if 'INSTITUTE_ROOM_PREVIEW_MANIFEST sha256='+digest not in content:raise ValueError('Native pictures use a different manifest')
    data=json.loads(manifest.read_text('utf8'))
    expected={f'room_{r["id"]}_{side}' for r in data['rooms'] for side in ('front','back')}
    captures=re.findall(r'INSTITUTE_ROOM_PREVIEW_CAPTURE (\w+)',content)
    if set(captures)!=expected or len(captures)!=len(expected) or int(complete[1])!=48 or int(complete[2])!=96:
        raise ValueError('Missing or duplicate room photographs')
    destination.mkdir(parents=True,exist_ok=True)
    entries=[]
    for name in sorted(expected):
        source=ROOT/'ender_infection_neoforge/build/institute-room-preview/screenshots'/(name+'.png')
        raw=source.read_bytes()
        if raw[:8]!=b'\x89PNG\r\n\x1a\n' or struct.unpack('>II',raw[16:24])!=(1280,800):raise ValueError('Invalid native frame '+name)
        target=destination/source.name;shutil.copy2(source,target)
        entries.append({'file':target.relative_to(ROOT).as_posix(),'sha256':sha256(raw).hexdigest(),'width':1280,'height':800})
    report={'status':'PASS','target':'NeoForge Minecraft 1.21.1','rooms':48,'screenshots':96,
            'natural_generation_acceptance':False,'manifest_sha256':digest,'log':log.relative_to(ROOT).as_posix(),'images':entries}
    (destination.parent/'native-room-preview.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    (destination/'capture-markers.txt').write_text('\n'.join(line for line in content.splitlines() if line.startswith('INSTITUTE_ROOM_PREVIEW'))+'\n',encoding='utf8')
    index=['# 全部 48 间房原生实景','', 'NeoForge Minecraft 1.21.1。独立新世界、当前原生模板、供电状态；每间两张，未经改绘。','']
    for floor in ('G1','G2','G3','B1','B2','B3'):
        index += ['## '+floor,'','| 房间 | 用途 | 前视 | 后视 |','|---|---|---|---|']
        for room in data['rooms']:
            if room['floor']==floor:
                key=room['id'];index.append(f'| {key} | {room["name"]} | [查看](neoforge/room_{key}_front.png) | [查看](neoforge/room_{key}_back.png) |')
        index.append('')
    (destination.parent/'全部房间.md').write_text('\n'.join(index).rstrip()+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS','rooms':48,'screenshots':96,'manifest_sha256':digest}))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('log',type=Path);parser.add_argument('destination',type=Path)
    args=parser.parse_args();collect(args.log.resolve(),args.destination.resolve())
