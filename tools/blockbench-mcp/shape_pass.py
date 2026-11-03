"""Bespoke proportion corrections performed through live native MCP edits."""
import json
from client import BlockbenchMCP
from review_models import NAMES,data,ROOT

client=BlockbenchMCP()
client.call('set_mode',{'mode_id':'edit'})
records={}
for name in NAMES:
    code='''(()=>{
      const name=NAME;
      const p=ModelProject.all.find(p=>p.name===name&&p.save_path.includes('monsters'));
      if(!p||!p.saved)throw Error('Missing saved canonical model '+name);p.select();
      const elements=Cube.all.slice();Undo.initEdit({elements,outliner:true});
      function scale(c,pivot,factors,shift=[0,0,0]){
        for(const v of [c.from,c.to,c.origin])for(let i=0;i<3;i++)v[i]=pivot[i]+(v[i]-pivot[i])*factors[i]+shift[i];
      }
      if(name!=='rift_throat_spitter'){
        for(const c of elements.filter(c=>c.name.includes('eye_')||c.name.includes('eye_socket')||c.name.includes('eye_glint'))){
          const middle=c.from.map((v,i)=>(v+c.to[i])/2);
          scale(c,middle,[1,.48,1]);
          const front=c.from[2];c.to[2]=front+.12;
          if(c.name.includes('glint')){c.from[2]+=.18;c.to[2]+=.18;}
        }
      }
      if(name==='crystalback_carrier'){
        for(const c of elements.filter(c=>c.parent.name.startsWith('cargo_'))){
          const pivot=c.parent.origin;
          scale(c,pivot,[1.12,.78,1.12],[0,c.parent.name==='cargo_2'?-2:0,0]);
        }
      }
      if(name==='parasitic_spider_mother'){
        for(const c of elements.filter(c=>c.parent.name==='abdomen'))scale(c,[0,13,7],[1.08,.83,1.10]);
      }
      if(name==='lurking_forager'||name==='sporewing_scout'){
        const head=Group.all.find(g=>g.name==='head');
        for(const c of elements.filter(c=>c.parent===head))scale(c,head.origin,[1,.78,1.05]);
      }
      if(name==='crystal_shield_sentinel'){
        const shield=Group.all.find(g=>g.name==='shield');
        for(const c of elements.filter(c=>c.parent===shield))scale(c,shield.origin,[1.25,1,1]);
        shield.rotation[1]=-12;
      }
      Undo.finishEdit('Match concept proportions: recessed eyes, low cargo and broader shield');
      Canvas.updateAll();return {name,cubes:Cube.all.length,action:Undo.history.slice(-1)[0].action};
    })()'''.replace('NAME',json.dumps(name))
    records[name]=data(client.call('risky_eval',{'code':code}))
    client.call('trigger_action',{'action':'save_project','confirmDialog':False})
    print('MCP_SHAPE_SAVED',name,flush=True)
(ROOT/'ender_infection_colored/verification/blockbench_shape_edits.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
