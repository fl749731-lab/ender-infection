"""Native MCP viewport QA of the saved rift models; no offline render substitution."""
import argparse
import json
import math
from pathlib import Path

from build_rift_models import SPECS, PACK, ROOT
from client import BlockbenchMCP
from review_models import data, save_image


def capture(selected=None):
    output=ROOT/'previews/ender_rift_models_2026-11-04'
    output.mkdir(parents=True,exist_ok=True)
    client=BlockbenchMCP();view='rift_native_final'
    client.call('create_offscreen_view',{'id':view,'width':1100,'height':1100,'copy_view':'none'})
    records=[]
    try:
        for spec in SPECS:
            if selected and spec['kind'] not in selected:continue
            path=PACK/'models'/(spec['name']+'.bbmodel')
            code='(()=>{const p=ModelProject.all.find(p=>p.save_path.replaceAll("\\\\","/")===PATH);if(!p)throw Error("Saved project not open");p.select();return {name:Project.name,saved:Project.saved};})()'.replace('PATH',json.dumps(path.as_posix()))
            state=data(client.call('risky_eval',{'code':code}));assert state['saved']
            client.call('set_mode',{'mode_id':'edit'})
            bounds=data(client.call('risky_eval',{'code':'(()=>{Canvas.updateAll();const b=new THREE.Box3().setFromObject(Project.model_3d);return {min:b.min.toArray(),max:b.max.toArray()};})()'}))
            lo,hi=bounds['min'],bounds['max'];center=[(a+b)/2 for a,b in zip(lo,hi)]
            height=hi[1]-lo[1];radius=max(b-a for a,b in zip(lo,hi))*2.2
            # Blockbench uses height / 40 as its unzoomed orthographic extent.
            zoom=(1100/40)/(max(height,hi[0]-lo[0],hi[2]-lo[2])*1.15)
            shots=[]
            for name,direction in [('front',[0,0,-1]),('right',[1,0,0]),('back',[0,0,1])]:
                result=client.call('set_camera_angle',{'view':view,'position':[center[i]+direction[i]*radius for i in range(3)],'target':center,'projection':'orthographic','zoom':zoom,'format':'png'})
                save_image(result,output/(spec['name']+'_'+name+'.png'));shots.append(name)
            perspective=[center[i]+radius*[.65,.24,-1][i] for i in range(3)]
            save_image(client.call('set_camera_angle',{'view':view,'position':perspective,'target':center,'projection':'perspective','fov':33,'format':'png'}),output/(spec['name']+'_perspective.png'))
            shots.append('perspective')
            client.call('set_mode',{'mode_id':'animate'})
            for clip,t in [('walk',.25),('attack',.75),('phase' if spec['kind']!='anchor' else 'deploy',.24 if spec['kind']!='anchor' else .7)]:
                client.call('animation_timeline',{'animation_id':'animation.'+spec['name']+'.'+clip,'action':'set_time','time':t})
                save_image(client.call('set_camera_angle',{'view':view,'position':perspective,'target':center,'projection':'perspective','fov':33,'format':'png'}),output/(spec['name']+'_'+clip+'.png'))
                shots.append(clip)
            client.call('animation_timeline',{'action':'stop'});client.call('set_mode',{'mode_id':'edit'})
            records.append({'name':spec['name'],'state':state,'bounds':bounds,'zoom':zoom,'shots':shots})
            print('MCP_NATIVE_CAPTURED',spec['name'],len(shots),'views',flush=True)
    finally:
        try:client.call('delete_offscreen_view',{'view':view})
        except RuntimeError as e:
            if 'onContextMenu is not defined' not in str(e):raise
            print('Known Blockbench offscreen cleanup warning; captures preserved',flush=True)
    (PACK/'verification/mcp_views.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('kinds',nargs='*')
    args=parser.parse_args();capture(args.kinds)
