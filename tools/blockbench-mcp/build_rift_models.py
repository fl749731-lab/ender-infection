"""Author and save the three rift creatures in the live Blockbench MCP editor.

Geometry and texture painting execute in Blockbench, with native Undo transactions.
The saved .bbmodel files are written by the native save_project action.
This is a one-time creation entry point; existing output models are never overwritten.
"""
import argparse
import base64
import json
from pathlib import Path
import sys
import time

from client import BlockbenchMCP
from review_models import data

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / 'colored_units/ender_rift_pack_2026-11-04'
SPECS = [
    {'name':'early_infected_enderman_rift','label':'初期感染末影人','kind':'early','target_height':48},
    {'name':'mutated_enderman_rift','label':'畸变末影人','kind':'mutated','target_height':60},
    {'name':'crystal_anchor_guard','label':'晶壳锚卫','kind':'anchor','target_height':44},
]


def keys(values, times=None, channel='rotation'):
    times = times or [i/(len(values)-1) for i in range(len(values))]
    return [{'time':t,channel:v} for t,v in zip(times,values)]


def animations(spec):
    humanoid = spec['kind'] != 'anchor'
    idle = {'core':keys([[0,0,0],[0,.35,0],[0,0,0]],[0,1,2],'position'),
            'head':keys([[0,-2,0],[0,2,0],[0,-2,0]],[0,1,2])}
    walk = {'body':keys([[0,0,0],[0,.25,0],[0,0,0],[0,.25,0],[0,0,0]],[0,.25,.5,.75,1],'position')}
    if humanoid:
        for sign,label in [(1,'right'),(-1,'left')]:
            walk['thigh_'+label] = keys([[sign*20,0,0],[-sign*20,0,0],[sign*20,0,0]],[0,.5,1])
            walk['upper_arm_'+label] = keys([[-sign*13,0,0],[sign*13,0,0],[-sign*13,0,0]],[0,.5,1])
            walk['shin_'+label] = keys([[0,0,0],[8,0,0],[0,0,0]],[0,.5,1])
        attack = {'body':keys([[0,0,0],[-6,-10,0],[10,6,0],[0,0,0]],[0,.4,.75,1.4]),
                  'head':keys([[0,0,0],[-6,10,0],[6,-6,0],[0,0,0]],[0,.4,.75,1.4]),
                  'upper_arm_right':keys([[0,0,0],[-15,-15,-10],[80,-10,5],[0,0,0]],[0,.4,.75,1.4]),
                  'upper_arm_left':keys([[0,0,0],[-10,10,8],[65,10,-5],[0,0,0]],[0,.4,.75,1.4]),
                  'forearm_right':keys([[0,0,0],[35,0,0],[12,0,0],[0,0,0]],[0,.4,.75,1.4]),
                  'forearm_left':keys([[0,0,0],[25,0,0],[12,0,0],[0,0,0]],[0,.4,.75,1.4])}
        phase = {'root':keys([[1,1,1],[.8,1.02,.8],[.12,1.05,.12],[.8,1.02,.8],[1,1,1]],[0,.12,.24,.36,.5],'scale'),
                 'core':keys([[1,1,1],[1.2,1.1,1.2],[1,1,1]],[0,.24,.5],'scale')}
        if spec['kind']=='mutated':
            for side in ['right','left']:
                for i in range(3):
                    attack['finger_'+side+'_'+str(i)]=keys([[0,0,0],[15,0,0],[35,0,0],[0,0,0]],[0,.4,.75,1.4])
        return [('idle',True,2,idle),('walk',True,1,walk),('attack',False,1.4,attack),('phase',False,.5,phase)]
    for s,label in [(1,'right'),(-1,'left')]:
        for z,fb in [(-1,'front'),(1,'back')]:
            n=fb+'_'+label
            walk['leg_'+n]=keys([[s*z*7,0,0],[-s*z*7,0,0],[s*z*7,0,0]],[0,.5,1])
    deploy={'core':keys([[1,1,1],[1.1,1.1,1.1],[1,1,1]],[0,.7,1.4],'scale')}
    attack={'body':keys([[0,0,0],[0,-1.1,0],[0,.4,0],[0,0,0]],[0,.45,.8,1.5],'position'),
            'core':keys([[1,1,1],[1.15,1.15,1.15],[1,1,1]],[0,.6,1.5],'scale')}
    for s,label in [(1,'right'),(-1,'left')]:
        for z,fb in [(-1,'front'),(1,'back')]:
            n='shell_'+fb+'_'+label
            rot=keys([[0,0,0],[0,-s*z*42,0],[0,0,0]],[0,.7,1.4])
            pos=keys([[0,0,0],[s*3.4,0,z*1.4],[0,0,0]],[0,.7,1.4],'position')
            deploy[n]=rot+pos
            attack[n]=keys([[0,0,0],[0,-s*z*32,0],[0,-s*z*45,0],[0,0,0]],[0,.45,.8,1.5])
        for i in range(3):
            deploy['tail_'+str(i)]=keys([[0,0,0],[-6,0,0],[0,0,0]],[0,.7,1.4])
    return [('idle',True,2,idle),('walk',True,1,walk),('attack',False,1.5,attack),('deploy',False,1.4,deploy)]


def build(selected):
    client=BlockbenchMCP()
    template='\n'.join(line for line in Path(__file__).with_suffix('.js').read_text(encoding='utf-8').splitlines()
                       if not line.lstrip().startswith('//'))
    record_path=PACK/'verification/mcp_creation.json'
    records=json.loads(record_path.read_text('utf-8')) if record_path.exists() else []
    for spec in SPECS:
        if selected and spec['kind'] not in selected: continue
        output=PACK/'models'/(spec['name']+'.bbmodel')
        if output.exists():raise RuntimeError('Refusing to overwrite saved model: '+str(output))
        spec=dict(spec,path=output.as_posix())
        state=data(client.call('risky_eval',{'code':'(()=>({name:Project?.name,cubes:typeof Cube===\'undefined\'?0:Cube.all.length}))()'}))
        if state.get('name')!=spec['name'] or state.get('cubes'):
            client.call('create_project',{'name':spec['name'],'format':'free'})
        built=data(client.call('risky_eval',{'code':template.replace('__SPEC__',json.dumps(spec,ensure_ascii=False))}))
        print('MCP_NATIVE_GEOMETRY',spec['name'],built['cubes'],built['bones'],flush=True)
        client.call('set_mode',{'mode_id':'animate'})
        for suffix,loop,length,bones in animations(spec):
            client.call('create_animation',{'name':'animation.'+spec['name']+'.'+suffix,'loop':loop,'animation_length':length,'bones':bones})
        client.call('animation_timeline',{'action':'stop'})
        client.call('set_mode',{'mode_id':'edit'})
        client.call('trigger_action',{'action':'save_project','confirmDialog':False})
        for _ in range(20):
            if output.exists():break
            time.sleep(.2)
        if not output.exists():raise RuntimeError('Blockbench did not save '+str(output))
        saved=data(client.call('risky_eval',{'code':'(()=>({name:Project.name,saved:Project.saved,path:Project.save_path,cubes:Cube.all.length,bones:Group.all.length,animations:Animation.all.map(a=>({name:a.name,length:a.length})),history:Undo.history.map(h=>h.action)}))()'}))
        assert saved['saved'] and saved['name']==spec['name']
        source=json.loads(output.read_text('utf-8'))
        for texture in source['textures']:
            if texture.get('source','').startswith('data:image/png;base64,'):
                (PACK/'textures'/texture['name']).write_bytes(base64.b64decode(texture['source'].split(',',1)[1]))
        records.append({'server':client.info['serverInfo'],'spec':spec,'geometry':built,'saved':saved})
        record_path.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print('MCP_NATIVE_SAVED',spec['name'],output,flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kinds',nargs='*',choices=['early','mutated','anchor'])
    args=parser.parse_args()
    build(args.kinds)
