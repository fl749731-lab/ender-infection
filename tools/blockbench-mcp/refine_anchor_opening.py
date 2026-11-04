"""One-time MCP refinement: continuous shield faces and a genuinely exposed core."""
import json
from client import BlockbenchMCP
from build_rift_models import PACK
from review_models import data

c=BlockbenchMCP()
code='''(()=>{
  const p=ModelProject.all.find(p=>p.name==='crystal_anchor_guard');if(!p||!p.saved)throw Error('Unsaved model');p.select();
  const elements=Cube.all.slice(),tex=Texture.all.find(t=>t.render_mode!=='emissive');
  Undo.initEdit({elements,textures:[tex],outliner:true});
  const groups=new Map(Group.all.map(g=>[g.name,g]));
  function clone(template,name,center,size,parent=template.parent){
    const entry=template.getSaveCopy();delete entry.uuid;entry.name=name;
    entry.from=center.map((v,i)=>v-size[i]/2);entry.to=center.map((v,i)=>v+size[i]/2);entry.origin=center;entry.rotation=[0,0,0];
    const cube=new Cube(entry).addTo(parent).init();elements.push(cube);return cube;
  }
  const frame=Cube.all.find(e=>e.name==='central_frame'),frameTemplate=frame.getSaveCopy();
  frame.from=[-11.5,20,4.6];frame.to=[11.5,34,6.8];frame.name='rear_frame_wall';
  for(const s of [-1,1])clone(frame,'frame_side_post_'+s,[s*10.2,27,-1],[2.6,14,11.5]);
  clone(frame,'frame_top_beam',[0,34,-1],[18,2,11.5]);
  clone(frame,'frame_bottom_beam',[0,20.8,-1],[18,2,11.5]);
  Cube.all.filter(e=>e.name.startsWith('front_cross_')).forEach(e=>e.remove());
  for(const e of Cube.all.filter(e=>e.name.startsWith('anchor_core_'))){
    e.from[1]=22+(e.from[1]-21.5)*10.5/13;e.to[1]=22+(e.to[1]-21.5)*10.5/13;e.origin[1]=22;
  }
  const atlas=document.createElement('canvas');atlas.width=atlas.height=512;
  const ctx=atlas.getContext('2d');ctx.drawImage(tex.img,0,0);
  for(let y=0;y<80;y++)for(let x=0;x<84;x++){
    const shade=((Math.floor(x/10)*3+Math.floor(y/9)*5)%17);
    ctx.fillStyle=shade<12?'#bdb6a9':shade<16?'#c6bfb2':'#afa89d';ctx.fillRect(x,300+y,1,1);
  }
  for(const [s,label] of [[1,'right'],[-1,'left']])for(const [z,face] of [[-1,'front'],[1,'back']]){
    const parent=groups.get('shell_'+face+'_'+label);
    const old=Cube.all.filter(e=>e.parent===parent&&/^(upper|lower)_petal/.test(e.name));
    const template=old[0];
    for(const upper of [true,false])for(let j=0;j<10;j++){
      const width=(upper?15.7:15.5)-j*(upper?.48:.53),y=upper?28.75+j*.75:27.1-j*.75;
      const cube=clone(template,(upper?'upper':'lower')+'_shield_facet_'+face+'_'+label+'_'+j,[s*(.65+width/2),y,z*9.7],[width,.75,2.05],parent);
      for(const f of ['north','south']){
        cube.faces[f].texture=tex.uuid;
        cube.faces[f].uv=[2+(cube.from[0]+20)*2,300+(36-cube.to[1])*2,2+(cube.to[0]+20)*2,300+(36-cube.from[1])*2];
      }
    }
    old.forEach(e=>e.remove());
  }
  for(const [s,label] of [[1,'right'],[-1,'left']])for(const [z,fb] of [[-1,'front'],[1,'back']]){
    const name=fb+'_'+label, footX=s*(z===-1?17:12.9),parent=groups.get('foot_'+name);
    const t=Cube.all.find(e=>e.name==='foot_pad_'+name);
    clone(t,'ankle_connector_'+name,[footX,2.75,z*9.2],[3.2,1.3,3.2],parent);
  }
  tex.fromDataURL(atlas.toDataURL('image/png'));
  Undo.finishEdit('Refine anchor shield surfaces, hollow frame and connected ankle joints via MCP');Canvas.updateAll();
  return {name:Project.name,cubes:Cube.all.length,bones:Group.all.length,undo:Undo.history.slice(-1)[0].action};
})()'''
result=data(c.call('risky_eval',{'code':code}));c.call('trigger_action',{'action':'save_project','confirmDialog':False})
(PACK/'verification/mcp_anchor_opening.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(result)
