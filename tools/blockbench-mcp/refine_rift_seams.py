"""One-time native MCP seam/proportion refinement after actual viewport review."""
import json
from client import BlockbenchMCP
from build_rift_models import PACK, SPECS
from review_models import data

c=BlockbenchMCP();records=[]
for spec in SPECS:
    path=PACK/'models'/(spec['name']+'.bbmodel')
    code='''(()=>{
      const p=ModelProject.all.find(p=>p.save_path.replaceAll('\\\\','/')===PATH);
      if(!p||!p.saved)throw Error('Expected saved canonical project');p.select();
      const elements=Cube.all.slice();Undo.initEdit({elements,outliner:true});
      for(const e of elements){
        if(KIND==='early'&&e.name.startsWith('hand_'))e.to[1]=Math.min(e.to[1],13.48);
        if(KIND==='mutated'&&e.name.startsWith('eye_well_')){e.from[2]+=.03;e.to[2]+=.03;}
        if(KIND==='mutated'&&e.name.startsWith('swept_crest_')){e.from[1]-=.075;e.to[1]-=.075;e.origin[1]-=.075;}
        if(KIND==='anchor'){
          for(const [part,inset] of [['fork_crystal_vertical',.018],['fork_crystal_upper',.036],['fork_crystal_lower',.054]])
            if(e.name.startsWith(part)){e.from[2]+=inset;e.to[2]-=inset;}
          if(e.name.startsWith('rear_socket_frame_y')){e.from[0]=-2.49;e.to[0]=2.49;}
          if(e.name.startsWith('foot_pad_'))e.from[1]+=.015;
          if(e.name.startsWith('ground_anchor_wedge')){e.from[2]-=.5;e.to[2]-=.5;e.origin[2]-=.5;}
        }
      }
      if(KIND==='early')for(const g of Group.all)if(g.name.startsWith('upper_arm_'))g.rotation[2]=g.name.endsWith('right')?6:-6;
      Undo.finishEdit('Refine turnaround proportions and separate coplanar seams via MCP');Canvas.updateAll();
      return {name:Project.name,cubes:Cube.all.length,undo:Undo.history.slice(-1)[0].action};
    })()'''.replace('PATH',json.dumps(path.as_posix())).replace('KIND',json.dumps(spec['kind']))
    records.append(data(c.call('risky_eval',{'code':code})))
    c.call('trigger_action',{'action':'save_project','confirmDialog':False})
    print('MCP_SEAMS_SAVED',spec['name'],flush=True)
(PACK/'verification/mcp_refinements.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
