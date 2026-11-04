"""Export the canonical MCP models without changing their geometry or embedded UVs."""
import base64
import copy
import json
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from export_design_assets import geo, groups, write
from modelkit import resolve_groups

ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
PACK=ROOT/'colored_units/ender_rift_pack_2026-11-04'
ROWS=[('early_infected_enderman_rift','enderman_early',3.2,48,12,26),
      ('mutated_enderman_rift','enderman',3.6,60,18,36),
      ('crystal_anchor_guard','anchor_guard',2.75,44,16,40)]

def export_animations(d,key,scale,hit,duration):
    output={}
    for source in d['animations']:
        short=source['name'].split('.')[-1];name='special' if short=='attack' else short
        bones={}
        for animator in source['animators'].values():
            channels={}
            for k in animator.get('keyframes',[]):
                t=k['time'];v=[float(k['data_points'][0][a]) for a in ('x','y','z')];ch=k['channel']
                if ch=='rotation':v=[-v[0],-v[1],v[2]]
                if ch=='position':v=[-v[0]*scale,v[1]*scale,v[2]*scale]
                if name=='special':t=t/.75*(hit/20) if t<=.75 else hit/20+(t-.75)/(source['length']-.75)*(duration-hit)/20
                if name=='phase':t=t/.24*.6 if t<=.24 else .6+(t-.24)/.26*.4
                channels.setdefault(ch,{})[str(round(t,5))]=v
            bones[animator['name']]=channels
        length=duration/20 if name=='special' else 1 if name=='phase' else source['length']
        output['animation.'+key+'.'+name]={'loop':source.get('loop')=='loop','animation_length':length,'bones':bones}
        if short=='deploy':
            exposed={}
            for bone,chans in bones.items():
                if not bone.startswith('shell_'):continue
                exposed[bone]={ch:values['0.7'] for ch,values in chans.items()}
            output['animation.'+key+'.exposed']={'loop':True,'animation_length':1,'bones':exposed}
            output['animation.'+key+'.closed']={'loop':True,'animation_length':1,'bones':{b:{'rotation':[0,0,0],'position':[0,0,0]} for b in exposed}}
    output['animation.'+key+'.infection']={'loop':False,'animation_length':1.6,'bones':{'root':{'scale':{'0':[.2,.2,.2],'1.6':[1,1,1]}}}}
    output['animation.'+key+'.death']={'loop':False,'animation_length':1.5,'bones':{'root':{'rotation':{'0':[0,0,0],'1.5':[0,0,85]},'scale':{'0':[1,1,1],'1.5':[.75,.75,.75]}}}}
    write(ASSETS/'animations'/f'{key}.animation.json',{'format_version':'1.8.0','animations':output})

def export():
    report=[]
    for source,runtime,height,design_height,hit,duration in ROWS:
        d=resolve_groups(json.loads((PACK/'models'/f'{source}.bbmodel').read_text('utf-8')))
        key='infected_'+runtime;scale=height*16/design_height
        geo(key,d,scale)
        for t in d['textures']:
            suffix='_glowmask' if t.get('render_mode')=='emissive' else ''
            (ASSETS/'textures/entity'/f'{key}{suffix}.png').write_bytes(base64.b64decode(t['source'].split(',',1)[1]))
        export_animations(d,key,scale,hit,duration)
        report.append({'source':source,'runtime':runtime,'scale':scale,'height':height,'cubes':len(d['elements']),'bones':len(groups(d))})
    write(ROOT/'ender_infection_colored/verification/rift_asset_export.json',{'status':'PASS','models':report})
    print(json.dumps(report))

if __name__=='__main__':export()
