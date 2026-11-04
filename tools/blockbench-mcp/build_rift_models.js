// Executed inside Blockbench through MCP risky_eval. All model edits use native Undo.
(() => {
  const spec = __SPEC__;
  if (Project.name !== spec.name || Cube.all.length) throw Error('Expected a new empty project: ' + spec.name);
  const groups = {}, elements = [], textures = [];
  Undo.initEdit({elements, textures, outliner:true});
  Project.model_identifier = spec.name;
  Project.texture_width = Project.texture_height = 512;
  const atlas = document.createElement('canvas'); atlas.width = atlas.height = 512;
  const glow = document.createElement('canvas'); glow.width = glow.height = 512;
  const ctx = atlas.getContext('2d'), gx = glow.getContext('2d');
  const palettes = {
    dark:['#17151e','#211e29','#2c2735','#363040'],
    tissue:['#261d33','#352440','#493153','#5b3c69'],
    bone:['#817b76','#aaa49a','#c8c2b5','#e2ddcf'],
    spine:['#29252d','#3c3740','#514a52','#655d65'],
    crystal:['#663397','#9651c3','#bc78ec','#e6b6ff'],
    eye:['#a342d8','#c25bf4','#dc8cff','#f5c6ff'],
    socket:['#0d0a12','#16111c','#211728','#302137']
  };
  let ux=2, uy=2, row=0; const tiles=new Map();
  function tile(mat,w,h,face){
    w=Math.max(2,Math.ceil(w*2));h=Math.max(2,Math.ceil(h*2));
    const key=[mat,w,h,face].join('/'); if(tiles.has(key))return tiles.get(key);
    if(ux+w+2>512){ux=2;uy+=row+3;row=0;}
    if(uy+h+2>512)throw Error('UV atlas overflow');
    const p=palettes[mat], emissive=mat==='crystal'||mat==='eye';
    for(let y=0;y<h;y++)for(let x=0;x<w;x++){
      let index=((Math.floor(x/4)*7+Math.floor(y/5)*11)%19)<14?1:2;
      if(emissive)index=Math.min(3,Math.floor(x*3/w)+(y<Math.max(1,h/8)?1:0));
      else if(y===0||x===0)index=face==='down'?1:2;
      else if(y===h-1)index=0;
      ctx.fillStyle=p[index];ctx.fillRect(ux+x,uy+y,1,1);
      if(emissive){gx.fillStyle=p[index];gx.fillRect(ux+x,uy+y,1,1);}
    }
    const uv=[ux,uy,ux+w,uy+h];tiles.set(key,uv);ux+=w+3;row=Math.max(row,h);return uv;
  }
  function group(name,pivot,parent='root',rotation=[0,0,0]){
    const g=new Group({name,origin:pivot,rotation}).addTo(parent?groups[parent]:undefined).init();groups[name]=g;return g;
  }
  group('root',[0,0,0],null);
  const tex=new Texture({name:spec.name+'.png',width:512,height:512,uv_width:512,uv_height:512}).add();
  const lum=new Texture({name:spec.name+'_glowmask.png',width:512,height:512,uv_width:512,uv_height:512,render_mode:'emissive'}).add();
  textures.push(tex,lum);
  function box(name,c,size,mat='dark',bone='body',rotation=[0,0,0],origin=c){
    if(size.some(x=>x<=0))throw Error('Invalid cube '+name);
    const faces={},[w,h,d]=size;
    for(const [f,a,b] of [['north',w,h],['south',w,h],['east',d,h],['west',d,h],['up',w,d],['down',w,d]]){
      faces[f]={uv:tile(mat,a,b,f),texture:(mat==='crystal'||mat==='eye'?lum:tex).uuid};
    }
    const e=new Cube({name,from:c.map((v,i)=>v-size[i]/2),to:c.map((v,i)=>v+size[i]/2),origin,rotation,box_uv:false,faces}).addTo(groups[bone]).init();
    elements.push(e);return e;
  }
  function beam(name,a,b,width,depth,mat,bone){
    const v=new THREE.Vector3(...b).sub(new THREE.Vector3(...a));
    const q=new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),v.clone().normalize());
    const e=new THREE.Euler().setFromQuaternion(q,'ZYX');
    return box(name,[a[0],a[1]+v.length()/2,a[2]],[width,v.length(),depth],mat,bone,[e.x,e.y,e.z].map(n=>n*180/Math.PI),a);
  }
  function plate(name,c,size,bone,rotation=[0,0,0],mat='bone'){
    const [w,h,d]=size;
    box(name+'_main',c,[w,h*.68,d],mat,bone,rotation,c);
    box(name+'_upper',[c[0],c[1]+h*.405,c[2]],[w*.86,h*.13,d*.83],mat,bone,rotation,c);
    box(name+'_lower',[c[0],c[1]-h*.405,c[2]],[w*.9,h*.13,d*.86],mat,bone,rotation,c);
  }
  function crystal(name,base,w,h,bone,rot=[0,0,0]){
    for(const [i,lo,hi,r] of [[0,0,.62,.5],[1,.62,.8,.39],[2,.8,.92,.24],[3,.92,1,.09]]){
      box(name+'_'+i,[base[0],base[1]+h*(lo+hi)/2,base[2]],[w*r*2,h*(hi-lo),w*r*2],'crystal',bone,rot,base);
    }
  }
  function humanoid(evolved){
    const hip=evolved?27:24, shoulder=evolved?48:39, headBase=evolved?50:40;
    group('body',[0,hip,0]);group('head',[0,headBase,0],'body');group('core',[0,evolved?40:35,0],'body');
    if(!evolved){
      box('slender_torso',[0,32,0],[7,16,5.8]);
      box('pelvis',[0,24.5,0],[6,2.3,5.1]);
      box('neck',[0,39.6,0],[3.8,1.2,3.8]);
      box('rectangular_head',[0,44,0],[7,8,8],'dark','head');
      for(const s of [-1,1]){
        box('eye_socket_'+s,[s*1.8,43.9,-4.06],[2.2,.95,.15],'socket','head');
        box('eye_slit_'+s,[s*1.8,43.95,-4.15],[1.95,.6,.08],'eye','head');
      }
      box('chest_cleft',[0,34.7,-2.96],[1.5,5,.15],'socket');
      crystal('chest_seed',[0,33.2,-3.12],.8,2.6,'core');
      for(let i=0;i<3;i++)plate('left_rib_'+i,[-2.35,30.7-i*1.7,-3.12],[2.7,.9,.6],'body',[0,-10,-8]);
      for(let i=0;i<2;i++)crystal('back_bud_'+i,[-1.2+i*2.4,34.5+i*.9,3.1],.7,1.6,'body',[8,0,0]);
    }else{
      box('pelvic_core',[0,27,0],[6.2,4.8,5.6]);
      plate('hip_belt',[0,28.2,-3.25],[8.5,2.2,1.1],'body');
      for(const s of [-1,1])beam('pelvis_fork_'+s,[s*3.3,27.5,-3.3],[s*1.4,24.7,-3.5],1.1,.8,'bone','body');
      box('back_column',[0,39,3.25],[3.7,21,2.3]);
      for(let i=0;i<6;i++)plate('dorsal_lamella_'+i,[0,46.5-i*3.1,4.7],[5.6-i*.25,2.7,1.8],'body',[0,0,0],'spine');
      for(const s of [-1,1]){
        beam('rib_stem_'+s,[s*5.0,32,1.8],[s*5.0,47,1.8],1.7,2.3,'dark','body');
        for(let i=0;i<5;i++){
          const y=46.2-i*3.3;
          beam('rib_front_'+s+'_'+i,[s*5.8,y+.7,-3.4],[s*(i===4?1.7:2.4),y-.75,-4.7],1.2,1.35,'bone','body');
          beam('rib_wrap_'+s+'_'+i,[s*5.8,y+.7,-3.3],[s*5.2,y+.9,2.3],1.15,1.35,'bone','body');
        }
      }
      crystal('suspended_chest_core',[0,36.4,-1.6],2.8,7.4,'core');
      box('neck',[0,49.3,0],[4.2,2,4.2]);
      box('head_inner',[0,54,0],[7,8,7],'dark','head');
      for(const s of [-1,1]){
        plate('helmet_side_'+s,[s*3.7,54,.2],[1.1,7,6.3],'head');
        box('brow_'+s,[s*2.0,56.5,-3.85],[3.25,2.7,.9],'bone','head');
        box('jaw_mask_'+s,[s*2.0,52.1,-3.85],[3.25,3.4,.9],'bone','head');
        box('eye_well_'+s,[s*1.9,54.3,-3.9],[2.8,1.5,.8],'socket','head');
        box('eye_slit_'+s,[s*1.9,54.3,-4.34],[2,.55,.08],'eye','head');
        beam('swept_crest_'+s,[s*2.6,57.6,1.2],[s*2.6,59.6,4.6],.8,1.1,'bone','head');
      }
    }
    for(const [s,label] of [[1,'right'],[-1,'left']]){
      const x=s*(evolved?8:5.25), elbow=evolved?33:25, wrist=evolved?18.5:13.5;
      group('upper_arm_'+label,[x,shoulder,0],'body',[0,0,s*(evolved?6.5:7)]);
      group('forearm_'+label,[x,elbow,0],'upper_arm_'+label);
      group('hand_'+label,[x,wrist,0],'forearm_'+label);
      box('upper_arm_'+label,[x,(shoulder+elbow)/2,0],[evolved?2.65:2,shoulder-elbow,2.2],'dark','upper_arm_'+label);
      box('elbow_'+label,[x,elbow,0],[evolved?3.0:2.15,1.5,2.35],'spine','forearm_'+label);
      box('forearm_'+label,[x,(elbow+wrist)/2,0],[evolved?2.7:2,elbow-wrist,2.3],'dark','forearm_'+label);
      if(evolved){
        for(let i=0;i<3;i++)plate('pauldron_'+label+'_'+i,[x+s*(.5+i*.65),48.2-i*1.6,.2],[6.6,1.65,6.9-i*.5],'upper_arm_'+label,[0,0,s*-12]);
        plate('forearm_guard_'+label,[x,27,-1.55],[3.8,7.7,1.2],'forearm_'+label,[0,0,s*-4]);
        plate('wrist_cuff_'+label,[x,20,-.2],[3.65,2.7,3.2],'forearm_'+label);
        box('palm_'+label,[x,17.4,-.15],[3.25,2.6,2.3],'spine','hand_'+label);
        for(let i=0;i<3;i++){
          const fx=x+(i-1)*1.05;
          group('finger_'+label+'_'+i,[fx,16.7,-.25],'hand_'+label);
          beam('finger_proximal_'+label+'_'+i,[fx,16.7,-.25],[fx,14.25,-.9],.68,.8,'dark','finger_'+label+'_'+i);
          beam('finger_claw_'+label+'_'+i,[fx,14.25,-.9],[fx,12.7,-1.95],.7,.75,'bone','finger_'+label+'_'+i);
        }
        beam('thumb_'+label,[x-s*1.3,17.7,-.5],[x-s*2,15.8,-1.4],.85,.85,'dark','hand_'+label);
      }else{
        box('hand_'+label,[x,12.8,-.12],[2,1.6,2.45],'dark','hand_'+label);
        if(s===1){
          plate('right_shoulder_cap',[x,38.6,-.2],[4.4,2.9,4.6],'upper_arm_right',[0,0,-10]);
          plate('right_shoulder_tip',[x+s*.8,36.9,-.2],[2,2.0,3.9],'upper_arm_right',[0,0,-14]);
          crystal('right_shoulder_crystal',[x+.1,40,0],1.35,4.7,'upper_arm_right',[12,0,-12]);
          crystal('right_shoulder_bud',[x+1.4,39.7,.2],1.05,2.6,'upper_arm_right',[8,0,-23]);
        }else{
          box('left_forearm_crystal',[x,20,-1.24],[1.3,1.7,.35],'crystal','forearm_left');
        }
      }
      if(evolved&&s===1)for(let i=0;i<3;i++)crystal('right_pauldron_prong_'+i,[x-1+i*1.5,49.2,.1+i*.7],1.45,5.4+i*.55,'upper_arm_right',[18+i*8,0,-16-i*10]);
      const lx=s*(evolved?2.6:1.75), knee=evolved?14:12;
      group('thigh_'+label,[lx,evolved?26:24,0],'body');
      group('shin_'+label,[lx,knee,0],'thigh_'+label);group('foot_'+label,[lx,1.5,0],'shin_'+label);
      box('thigh_'+label,[lx,(hip+knee)/2,0],[evolved?3:2,hip-knee,2.6],'dark','thigh_'+label);
      box('shin_'+label,[lx,(knee+1.5)/2,0],[evolved?3:2,knee-1.5,2.5],'dark','shin_'+label);
      if(evolved){
        plate('knee_shell_'+label,[lx,knee,-1.65],[3.9,3.6,1.05],'shin_'+label);
        plate('ankle_shell_'+label,[lx,2.2,-.1],[3.9,2.3,3.5],'foot_'+label);
        for(const t of [-1,1])box('split_toe_'+label+'_'+t,[lx+t*.95,.65,-1.0],[1.65,1.3,4.1],'bone','foot_'+label);
      }else box('foot_'+label,[lx,.7,-.4],[2.3,1.4,3.25],'dark','foot_'+label);
    }
  }
  function anchor(){
    group('body',[0,24,0]);group('head',[0,36,0],'body');group('core',[0,28,0],'body');
    box('central_frame',[0,27,0],[23,14,15],'dark');
    crystal('anchor_core',[0,21.5,-2.0],6.6,13,'core');
    box('front_cross_vertical',[0,28,-10.5],[1.05,12.3,.24],'eye','core');
    box('front_cross_horizontal',[0,27.8,-10.52],[27,.8,.21],'eye','core');
    box('lower_frame',[0,21,0],[21,4.6,15.1],'spine');
    for(const [s,label] of [[1,'right'],[-1,'left']])for(const [z,face] of [[-1,'front'],[1,'back']]){
      const bone='shell_'+face+'_'+label;group(bone,[s*14.7,28,z*8.8],'body');
      for(let j=0;j<5;j++){
        const y=29.1+j*1.5,width=15.7-j*1.0;
        box('upper_petal_'+face+'_'+label+'_'+j,[s*(.65+width/2),y,z*(9.7-j*.15)],[width,1.48,2.05],'bone',bone);
      }
      for(let j=0;j<5;j++){
        const y=26.7-j*1.48,width=15.5-j*1.15;
        box('lower_petal_'+face+'_'+label+'_'+j,[s*(.65+width/2),y,z*(9.85-j*.22)],[width,1.46,2.05],'bone',bone);
      }
      box('petal_rim_'+face+'_'+label,[s*15.7,28,z*8.6],[2.0,10.4,2.8],'spine',bone,[0,0,s*-14]);
      box('hinge_mount_'+face+'_'+label,[s*15.8,28.2,z*11.0],[3.5,3.8,1.8],'spine','body');
      box('hinge_inset_'+face+'_'+label,[s*15.8,28.2,z*12.0],[1.7,1.9,.3],'bone','body');
    }
    box('neck_collar',[0,36.2,0],[15.8,2.1,13.5],'dark');
    box('head_dome_lower',[0,38.5,0],[18.5,3,14.5],'bone','head');
    box('head_dome_middle',[0,41,0],[15.5,2,11.7],'bone','head');
    box('head_dome_top',[0,43,0],[12,2,8.7],'bone','head');
    box('eye_recess',[0,36.8,-7.65],[9.5,1.8,.55],'socket','head');
    box('slit_eye',[0,36.9,-7.98],[5.2,.65,.15],'eye','head');
    for(const s of [-1,1]){
      box('horn_socket_'+s,[s*10.2,40.6,.2],[4.5,2.2,4.0],'spine','head');
      box('fork_crystal_stem_'+s,[s*15,40.8,.2],[7.8,1.6,1.8],'crystal','head');
      box('fork_crystal_vertical_'+s,[s*17.8,42.1,.2],[1.5,3.8,1.8],'crystal','head');
      box('fork_crystal_upper_'+s,[s*18.8,43.2,.2],[2.4,1.4,1.8],'crystal','head');
      box('fork_crystal_lower_'+s,[s*18.8,40.8,.2],[2.4,1.4,1.8],'crystal','head');
    }
    box('rear_socket_well',[0,33.2,10.6],[5.6,5.6,1.8],'socket');
    for(const s of [-1,1]){
      box('rear_socket_frame_x_'+s,[s*3.0,33.2,11.1],[1.0,6.4,1.4],'spine');
      box('rear_socket_frame_y_'+s,[0,33.2+s*3,11.1],[5.2,1.0,1.4],'spine');
    }
    box('rear_control_crystal',[0,33.2,11.65],[3.25,3.25,.4],'crystal');
    for(const [s,label] of [[1,'right'],[-1,'left']])for(const [z,fb] of [[-1,'front'],[1,'back']]){
      const n=fb+'_'+label, x=s*(z===-1?11.7:9.5), footX=s*(z===-1?17.0:12.9);
      const hip=[x,21.3,z*5.5],knee=[s*(z===-1?15.8:12.2),12.5,z*8.3],ankle=[footX,3,z*9.2];
      group('leg_'+n,hip,'body');group('shin_'+n,knee,'leg_'+n);group('foot_'+n,ankle,'shin_'+n);
      beam('upper_leg_'+n,hip,knee,4.2,4.0,'tissue','leg_'+n);
      plate('thigh_shell_'+n,[x+s*1.5,18,z*6.5],[4.2,6.8,4.9],'leg_'+n,[0,0,s*18]);
      box('knee_joint_'+n,knee,[4.5,3.8,4.3],'spine','shin_'+n);
      beam('lower_leg_'+n,knee,ankle,3.5,3.7,'tissue','shin_'+n);
      plate('shin_shell_'+n,[footX,7.6,z*9.4],[4.9,5.5,4.9],'shin_'+n,[z*-8,0,0]);
      box('foot_pad_'+n,[footX,1.15,z*9.5],[5.8,2.3,5.4],'spine','foot_'+n);
      for(const t of [-1,1])box('split_toe_'+n+'_'+t,[footX+t*1.45,.85,z===-1?-12.2:8],[2.6,1.7,2.6],'tissue','foot_'+n);
    }
    const tail=[[0,20,7.6],[0,13.5,13],[0,7,17.6],[0,2,20.4]];
    for(let i=0;i<3;i++){
      group('tail_'+i,tail[i],i===0?'body':'tail_'+(i-1));
      beam('tail_link_'+i,tail[i],tail[i+1],4.3-i*.35,4.5,'spine','tail_'+i);
      box('tail_joint_'+i,tail[i],[4.7-i*.35,3.5,4.8],'tissue','tail_'+i);
    }
    plate('ground_anchor_wedge',[0,1.8,20.8],[7.4,3.6,4.4],'tail_2');
  }
  if(spec.kind==='anchor')anchor();else humanoid(spec.kind==='mutated');
  tex.fromDataURL(atlas.toDataURL('image/png'));lum.fromDataURL(glow.toDataURL('image/png'));
  Project.name=spec.name;Project.save_path=spec.path;Project.export_path='';
  Project.credit='Original Ender Rift concepts, 2026-11-04. Authored through Blockbench MCP.';
  Undo.finishEdit('Build '+spec.label+' from orthographic concept through MCP: bones, cuboids, UV and emissive crystals');
  Canvas.updateAll();
  const bounds=new THREE.Box3().setFromObject(Project.model_3d);
  return {name:Project.name,cubes:Cube.all.length,bones:Group.all.length,textures:Texture.all.map(t=>({name:t.name,render_mode:t.render_mode})),bounds:{min:bounds.min.toArray(),max:bounds.max.toArray()},undo:Undo.history.slice(-1).map(h=>h.action),path:Project.save_path};
})()
