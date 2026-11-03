"""Inset adjacent coplanar components through native MCP Undo transactions."""
import json
from pathlib import Path
from client import BlockbenchMCP
from review_models import data,ROOT

reports=json.loads((ROOT/'ender_infection_colored/verification/mcp_surface_candidates.json').read_text())
client=BlockbenchMCP();client.call('set_mode',{'mode_id':'edit'})
log=[]
for report in reports:
    if not report['overlap_faces']:continue
    graph={}
    for pair in report['candidates']:
        a,b=pair['first'],pair['second'];graph.setdefault(a,set()).add(b);graph.setdefault(b,set()).add(a)
    colors={}
    for node in sorted(graph,key=lambda n:(-len(graph[n]),n)):
        unavailable={colors[n] for n in graph[node] if n in colors};color=0
        while color in unavailable:color+=1
        colors[node]=color
    changes={name:color*.012 for name,color in colors.items() if color}
    name=Path(report['file']).stem
    code='''(()=>{
      const name=NAME,changes=CHANGES;
      const p=ModelProject.all.find(p=>p.name===name&&p.save_path.includes('monsters'));
      if(!p||!p.saved)throw Error('Canonical model not saved');p.select();
      const elements=Cube.all.filter(c=>changes[c.name]);
      if(elements.length!==Object.keys(changes).length)throw Error('Ambiguous component names');
      for(const c of elements)if(c.to.some((v,i)=>v-c.from[i]<=changes[c.name]*2))throw Error('Inset exceeds component thickness');
      Undo.initEdit({elements});
      for(const c of elements)for(let i=0;i<3;i++){c.from[i]+=changes[c.name];c.to[i]-=changes[c.name];}
      Undo.finishEdit('Inset overlapping shell joints by hundredths of a model unit to prevent flickering');
      Canvas.updateAll();return {name,components:elements.length,largestInset:Math.max(...Object.values(changes))};
    })()'''.replace('NAME',json.dumps(name)).replace('CHANGES',json.dumps(changes))
    log.append(data(client.call('risky_eval',{'code':code})))
    client.call('trigger_action',{'action':'save_project','confirmDialog':False})
    print('MCP_SURFACE_SAVED',name,log[-1],flush=True)
(ROOT/'ender_infection_colored/verification/blockbench_surface_edits.json').write_text(json.dumps(log,indent=2)+'\n',encoding='utf-8')
