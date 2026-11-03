"""One-off live MCP sculpting pass after side-by-side concept review."""
import json
from client import BlockbenchMCP
from review_models import data, ROOT

client=BlockbenchMCP()
client.call('set_mode',{'mode_id':'edit'})
records=[]
for name in ['rift_throat_spitter','pulsing_hatch_sac','walking_brood_crab']:
    code='''(()=>{
      const name=NAME;
      const project=ModelProject.all.find(p=>p.name===name&&p.save_path.includes('monsters'));
      if(!project||!project.saved)throw Error('Canonical project not saved');project.select();
      const elements=Cube.all.slice();Undo.initEdit({elements,outliner:true});
      const body=Group.all.find(g=>g.name==='body');
      function beam(label,a,b,width,depth,template,parent=body){
        const v=new THREE.Vector3(...b).sub(new THREE.Vector3(...a));
        const q=new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),v.clone().normalize());
        const euler=new THREE.Euler().setFromQuaternion(q,'ZYX');
        const e=template.getSaveCopy();delete e.uuid;e.name=label;
        e.from=[a[0]-width/2,a[1],a[2]-depth/2];e.to=[a[0]+width/2,a[1]+v.length(),a[2]+depth/2];
        e.origin=a;e.rotation=[euler.x,euler.y,euler.z].map(x=>x*180/Math.PI);
        const c=new Cube(e).addTo(parent).init();elements.push(c);return c;
      }
      if(name==='rift_throat_spitter'){
        for(const c of elements){
          if(/upper_snout|cranial_arch|nasal_ridge/.test(c.name)){
            const center=(c.from[1]+c.to[1])/2;
            c.from[1]=center+(c.from[1]-center)*.68;c.to[1]=center+(c.to[1]-center)*.68;
          }
          if(c.name.startsWith('dorsal_crystal')){
            for(const p of [c.from,c.to])for(let i=0;i<3;i++)p[i]=c.origin[i]+(p[i]-c.origin[i])*(i===1?.75:1.5);
            c.rotation[0]+=20;c.rotation[2]=c.origin[0]>0?-23:23;
          }
          if(/^leg_.*_foot_/.test(c.name)){
            const center=c.from.map((v,i)=>(v+c.to[i])/2);
            for(const p of [c.from,c.to]){p[1]=center[1]+(p[1]-center[1])*.58;p[2]=center[2]+(p[2]-center[2])*.75;}
          }
        }
      }
      if(name==='pulsing_hatch_sac'){
        const membrane=elements.find(c=>c.name.startsWith('layered_drooping_membrane'));
        const dark=elements.find(c=>c.name.startsWith('membrane_fringe'));
        const bone=elements.find(c=>c.name.startsWith('radial_support_rib'));
        for(let i=-4;i<=4;i++){
          const x=i*2.85;
          const a=[x*.72,18.6-Math.abs(x)*.18,-3.3],b=[x,14.6-Math.abs(x)*.12,-7.3],end=[x,12.3-Math.abs(x)*.06,-11.5];
          beam('curved_front_awning',a,b,3.2,.62,membrane);
          beam('curved_eave_lip',b,end,3.3,.58,membrane);
          if(i%2===0){
            beam('torn_eave_filament',[x,12.1-Math.abs(x)*.06,-11.65],[x+.2,5.4+(Math.abs(i)%3)*.7,-11.7],.3,.3,dark);
          }
          if(Math.abs(i)===3){
            beam('awning_bone_rib',a,b,.45,.55,bone);
            beam('awning_bone_rib',b,end,.45,.55,bone);
          }
        }
      }
      if(name==='walking_brood_crab'){
        for(const c of elements){
          if(/chamber|embryonic_nodule/.test(c.name)||c.parent.name.includes('chamber')){
            for(const p of [c.from,c.to,c.origin])p[1]=60+(p[1]-60)*.72;
          }
          if(c.name.startsWith('domed_carapace_lamella')){
            const center=(c.from[1]+c.to[1])/2;
            c.from[1]=center+(c.from[1]-center)*1.55-1;c.to[1]=center+(c.to[1]-center)*1.55-1;
          }
        }
        for(const g of Group.all.filter(g=>g.name.includes('chamber')))g.origin[1]=60+(g.origin[1]-60)*.72;
        const flesh=elements.find(c=>c.name.startsWith('eave_mycelium'));
        for(let i=-8;i<=8;i++){
          const x=i*3.2,y=55-Math.abs(x)*.10,z=-36+(i%2)*.6;
          const endY=27+(Math.abs(i)%4)*3;
          beam('front_mycorrhizal_curtain',[x,y,z],[x+.5,(y+endY)/2,z-.6],.65,.8,flesh);
          beam('front_mycorrhizal_tip',[x+.5,(y+endY)/2,z-.6],[x+.2,endY,z-1],.4,.5,flesh);
        }
        for(const side of [-1,1])for(let i=0;i<10;i++){
          const z=-27+i*5.8,x=side*(32-Math.abs(z)*.13),y=54+(1-Math.abs(z)/35)*3;
          beam('side_hanging_mycelium',[x,y,z],[x+side*.8,31+i%3*2,z+.4],.65,.75,flesh);
        }
      }
      Undo.finishEdit('Sculpt concept details: backward crystals, curved awning and low brood hoods');
      Canvas.updateAll();return {name,cubes:Cube.all.length,bones:Group.all.length,last:Undo.history.slice(-1)[0].action};
    })()'''.replace('NAME',json.dumps(name))
    records.append(data(client.call('risky_eval',{'code':code})))
    client.call('trigger_action',{'action':'save_project','confirmDialog':False})
    state=data(client.call('risky_eval',{'code':'(()=>({name:Project.name,saved:Project.saved}))()'}))
    assert state['name']==name and state['saved']
    print('MCP_FOCAL_SAVED',name,records[-1]['cubes'],flush=True)
(ROOT/'ender_infection_colored/verification/blockbench_focal_edits.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
