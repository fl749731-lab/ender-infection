"""Prepare cube/rig/UV operations for Blockbench MCP; never write a .bbmodel."""
from pathlib import Path
import json,math,sys
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'colored_units/design_pack_2026-11-02_v2/source'))
from modelkit import Atlas,PALETTES,rgb
OUT=ROOT/'build/institute-boss-alpha7';OUT.mkdir(parents=True,exist_ok=True)
PALETTES['plate']=['#77717e','#b5b0b7','#d0cbd1','#e4dfe4']
PALETTES['stripe']=PALETTES['plate'];PALETTES['label']=PALETTES['plate']
PALETTES['crystal']=['#6531a1','#9849cf','#c175ef','#edc1ff']
PALETTES['glow']=['#c175ef','#dc9dff','#edc1ff','#fff3ff']
class WardenAtlas(Atlas):
 def paint(self,k):
  mat,w,h,v=k;im=super().paint(k);d=ImageDraw.Draw(im)
  if mat in ('plate','stripe','label'):
   p=PALETTES['plate'];d.line((0,h-1,w-1,h-1),fill=rgb(p[0]),width=1);d.line((0,0,w-1,0),fill=rgb(p[3]),width=1)
   if w>10 and h>8:
    x=w//3+v;d.line([(x,2),(x,4),(x+2,5)],fill=rgb(p[0]),width=1)
   if mat=='stripe':
    for y in range(max(1,h//2),max(2,h-1)):
     for x in range(1,max(2,w-1)):
      if (x+y)//4%2==0:im.putpixel((x,y),(205,158,56,255))
   if mat=='label' and w>=19 and h>=9:
    glyph={'C':['111','100','100','100','111'],'-':['000','000','111','000','000'],'1':['010','110','010','010','111'],'7':['111','001','010','010','010']}
    for n,ch in enumerate('C-17'):
     for yy,row in enumerate(glyph[ch]):
      for xx,a in enumerate(row):
       if a=='1':d.point((max(1,(w-15)//2)+n*4+xx,max(1,(h-5)//2)+yy),fill=(46,39,53,255))
  return im
atlas=WardenAtlas();groups=[];cubes=[]
def group(name,origin,parent=None):groups.append({'op':'add_group','name':name,'origin':origin,**({'parent':parent} if parent else {})})
def cube(name,a,b,mat='plate',parent='body',rot=None,pivot=None):
 size=[b[i]-a[i] for i in range(3)];assert all(x>0 for x in size)
 uv={};dims={'north':(size[0],size[1]),'south':(size[0],size[1]),'east':(size[2],size[1]),'west':(size[2],size[1]),'up':(size[0],size[2]),'down':(size[0],size[2])}
 for face,(w,h) in dims.items():uv[face]=atlas.get(mat,w*2,h*2,0 if mat in ('crystal','glow') else len(cubes)%3)
 cubes.append({'name':name,'from':a,'to':b,'parent':parent,'rotation':rot or [0,0,0],'origin':pivot or [(a[i]+b[i])/2 for i in range(3)],'mat':mat,'uv':uv})
def box(name,center,size,mat='plate',parent='body',rot=None):cube(name,[center[i]-size[i]/2 for i in range(3)],[center[i]+size[i]/2 for i in range(3)],mat,parent,rot)
def crystal(name,center,size,parent='body',rot=None):
 box(name+'_shaft',center,size,'crystal',parent,rot)
 tip=[center[0],center[1]+size[1]*.52,center[2]];box(name+'_tip',tip,[size[0]*.66,size[0]*.66,size[2]*.66],'glow',parent,[0,0,45])
group('rig_root',[0,0,0]);group('body',[0,23,1],'rig_root');group('head',[0,42,-1],'body');group('core',[0,34,-6],'body')
for side,s in [('left',1),('right',-1)]:
 group(side+'_chest',[s*8,35,-5],'body');group(side+'_arm',[s*12.5,39,0],'body');group(side+'_forearm',[s*16,28,-.5],side+'_arm');group(side+'_hand',[s*18,18,-1],side+'_forearm')
 group(side+'_leg',[s*6,23,1],'body');group(side+'_shin',[s*7,12,1],side+'_leg');group(side+'_foot',[s*7,3,0],side+'_shin')
for i in range(3):group('right_claw_'+str(i),[-21+i*3,17,-3],'right_hand')
box('pelvis_tissue',[0,24,1],[15,6,10],'tissue')
box('torso_tissue',[0,33,1],[18,18,12],'tissue')
box('waist_core',[0,25,-4.7],[7,7,2],'black')
box('waist_plate',[0,24,-6],[6,5,2.4],'plate')
for s in [-1,1]:
 side='left' if s>0 else 'right'
 box(side+'_abdomen_plate',[s*5.2,27,-5.6],[5.7,7,2.2],'plate','body',[0,0,s*-12])
 box(side+'_hip_side',[s*8.7,22.5,1],[3.2,7,10],'stripe','body',[0,0,s*-9])
 # Six split sternum plates hinge outward to expose the central amethyst.
 for i,y in enumerate([31,35,39]):
  box(side+'_rib_'+str(i),[s*5.6,y,-6.6],[8,3,3],'stripe' if i==1 else 'plate',side+'_chest',[0,s*6,s*8])
 box(side+'_outer_rib',[s*9,35,-3.6],[3,12,5.6],'plate',side+'_chest',[0,s*20,0])
 # Upper arm organic volume and two-layer angular shoulder guard.
 box(side+'_upper_arm_muscle',[s*14.2,33.8,.4],[6.7,12,6.8],'tissue',side+'_arm',[0,0,s*-12])
 box(side+'_shoulder_under',[s*13.4,40,.7],[10,6.5,9],'darkmetal',side+'_arm',[0,0,s*-14])
 box(side+'_shoulder_outer',[s*17.2,39.7,.8],[7,6,10],'plate',side+'_arm',[0,0,s*-24])
 box(side+'_shoulder_cap',[s*13.3,43,.2],[10,3.7,8.5],'plate',side+'_arm',[0,0,s*-12])
 box(side+'_shoulder_front',[s*13.8,40,-4.3],[8.5,4,2.5],'plate',side+'_arm',[0,0,s*-15])
 for i in range(3):crystal(side+'_shoulder_crystal_'+str(i),[s*(11.7+i*2.1),45.5+(1 if i==1 else 0),.2-i*.4],[1.7,3.1+(i==1),1.7],side+'_arm',[0,0,s*-10])
 box(side+'_elbow_band',[s*16,28,-.1],[7.2,4,8],'plate',side+'_forearm',[0,0,s*-8])
 box(side+'_forearm_muscle',[s*17.6,22,-.5],[5,11.5,6],'tissue',side+'_forearm',[0,0,s*-9])
 box(side+'_hand_palm',[s*18.4,16,-1],[6.8,5,5],'tissue',side+'_hand')
 # Thighs, kneecaps, rearward shin and three broad grounded toes.
 box(side+'_thigh_muscle',[s*6.2,18.2,1],[7.2,11,7.5],'tissue',side+'_leg',[0,0,s*-7])
 box(side+'_thigh_front',[s*6.8,20.5,-3.2],[6.6,7,2.5],'stripe',side+'_leg',[0,0,s*-8])
 box(side+'_kneecap',[s*7.1,12.3,-3.3],[7.3,4.5,3],'plate',side+'_shin')
 box(side+'_shin_tendon',[s*7.1,7.2,2],[4.7,10,5.5],'tissue',side+'_shin',[-12,0,0])
 box(side+'_shin_front_strip',[s*7.1,6.3,-1.8],[3,8,1.8],'darkmetal',side+'_shin',[-12,0,0])
 box(side+'_ankle',[s*7.1,2.8,.6],[6.5,5.2,7],'tissue',side+'_foot')
 for i in range(3):box(side+'_toe_'+str(i),[s*(7.1+(i-1)*2.3),1.5,-4.3],[2,3,5.6],'plate',side+'_foot')
 box(side+'_heel',[s*7.1,1.3,4],[5.2,2.6,2.2],'darkmetal',side+'_foot')
# Recessed head, open mask and high asymmetric restraint collar.
box('neck_tissue',[0,42.5,1],[8,7,8],'tissue','head')
box('head_dark_mass',[0,47,-1],[10,10,9],'shell','head')
box('mask_brow',[0,49,-5.6],[10,3,2.2],'plate','head')
box('eye_socket',[0,46.8,-5.9],[8.5,2,1.8],'black','head')
box('eye_slit',[0,47,-6.9],[6.8,.9,.6],'glow','head')
box('mask_left_cheek',[3.8,44.2,-5.2],[3.2,5.4,2.5],'plate','head',[0,0,8])
box('mask_right_cheek',[-3.8,44.2,-5.2],[3.2,5.4,2.5],'plate','head',[0,0,-8])
box('mask_jaw',[0,42.2,-4.9],[6,2.4,2.4],'plate','head')
box('helmet_center',[0,52.1,-.1],[5,2.5,8.3],'plate','head')
box('helmet_left',[4.2,50.8,1],[3.2,3.5,8],'darkmetal','head',[0,0,-14])
box('helmet_right',[-4.2,50.8,1],[3.2,3.5,8],'plate','head',[0,0,14])
box('collar_back',[0,43,5.9],[15,3.5,3],'plate')
box('collar_back_left',[6.2,47.7,5.9],[3,9.5,3],'plate')
box('collar_back_right',[-6.2,47.7,5.9],[3,9.5,3],'plate')
box('collar_high_left',[8.3,47.8,1],[3.7,11.6,9.5],'plate','body',[0,0,12])
box('collar_marked_face',[8.5,45.8,-7.2],[10,5.5,2.4],'label','body',[0,0,12])
box('collar_right',[-8,43.7,.7],[4,6.5,9.4],'stripe','body',[0,0,-15])
box('collar_front_bridge',[-4,41,-5.4],[7,2.3,3],'plate')
# Connected crystal core, split armor and three broad back shingles.
box('core_bed',[0,34,-6.3],[5.5,14,3],'black','core')
for i in range(3):
 x=(i-1)*1.2
 box('chest_crystal_'+str(i)+'_shaft',[x,34,-8.2 if i==1 else -7.6],[2.4 if i==1 else 1.8,11,1.8 if i==1 else 2.8],'crystal','core',[0,(i-1)*22,0])
for i,(y,size) in enumerate([(28.5,[2.8,2.8,2.8]),(39.5,[2.8,2.8,2.8]),(34,[2.5,10,2])]):
 box('chest_crystal_'+str(i)+'_tip',[0,y,-7.3],size,'glow' if i<2 else 'crystal','core',[0,0,45 if i<2 else 0])
for i,y in enumerate([29.5,34.5,39.5]):
 box('back_shingle_'+str(i),[0,y,7],[14-i,6.2,2.8],'plate','body',[-8,0,0])
 box('back_shingle_bevel_'+str(i),[0,y-1.5,8.4],[10-i,3,1.6],'plate','body',[-17,0,0])
crystal('back_cluster_main',[0,43.5,7.3],[2.5,5,2.3])
crystal('back_cluster_left',[-2.4,42.6,7.5],[1.6,3,1.8],'body',[0,0,-20])
crystal('back_cluster_right',[2.4,42.8,7.4],[1.7,3.4,1.8],'body',[0,0,22])
for s in [-1,1]:
 box(('left' if s>0 else 'right')+'_restraint_anchor',[s*6,40.4,6.4],[2.3,3,3.2],'darkmetal')
 box(('left' if s>0 else 'right')+'_cable_upper',[s*6,38.5,7.6],[1.7,4,1.7],'black','body',[12,0,s*-5])
 box(('left' if s>0 else 'right')+'_cable_lower',[s*6.3,35.8,8],[1.7,2.8,1.7],'warning','body',[-8,0,s*10])
# Integrated left guard: a real frame around an emissive inset, not a held shield.
box('left_guard_back',[18,23.2,-1.8],[10.8,15.5,5.5],'darkmetal','left_forearm')
box('left_guard_outer',[22.8,23.5,-4.1],[3.6,15.5,3.2],'plate','left_forearm')
box('left_guard_inner',[13.3,23.5,-4.1],[3.2,14,3.2],'plate','left_forearm')
box('left_guard_top',[18,30,-4.3],[10.5,3.5,3.6],'plate','left_forearm')
box('left_guard_bottom',[18,16.5,-4.3],[10.5,4,3.6],'stripe','left_forearm')
box('left_guard_slot',[18,23.5,-5],[3.1,10,1.7],'black','left_forearm')
box('left_guard_crystal',[18,23.5,-6.1],[1.8,8.8,1.2],'crystal','left_forearm')
for i in range(3):box('left_knuckle_'+str(i),[16.1+i*2.1,14.9,-3.8],[1.9,3,2],'plate','left_hand')
# Right arm bracers and three bent claws, each on its own finger bone.
box('right_forearm_plate',[-17.8,23.3,-3.2],[6.3,9,2.3],'plate','right_forearm',[0,0,10])
box('right_wrist_ring',[-18.5,18,-.5],[7.2,3.5,7],'plate','right_forearm')
for i in range(3):
 x=-21+i*3;y=14.7-(i==1)*1.3;bone='right_claw_'+str(i)
 box('right_finger_'+str(i),[x,y,-3.8],[2.2,6,2.8],'tissue',bone,[12,0,(i-1)*-8])
 box('right_claw_upper_'+str(i),[x,y-2.9,-5],[2.3,3.6,3.6],'plate',bone,[-18,0,(i-1)*-9])
 box('right_claw_hook_'+str(i),[x,y-4.2,-5.9],[1.7,2.6,2.1],'plate',bone,[-35,0,(i-1)*-9])
# Match the technical sheet's front-view asymmetry in Blockbench's -Z camera.
for g in groups:g['origin'][0]*=-1
for c in cubes:
 if c['parent']=='head':
  for field in ['from','to','origin']:
   c[field][0]*=.85;c[field][1]=47+(c[field][1]-47)*.92
  for face,(u,v) in {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}.items():
   c['uv'][face]=atlas.get(c['mat'],(c['to'][u]-c['from'][u])*2,(c['to'][v]-c['from'][v])*2,0)
 a,b=c['from'][0],c['to'][0];c['from'][0]=-b;c['to'][0]=-a;c['origin'][0]*=-1
 c['rotation'][1]*=-1;c['rotation'][2]*=-1
image,positions=atlas.pack();image.save(OUT/'warden_atlas.png');glow=Image.new('RGBA',image.size)
for key,(x,y) in positions.items():
 if key[0] in ['crystal','glow']:
  w,h=key[1:3];glow.paste(image.crop((x-1,y-1,x+w+1,y+h+1)),(x-1,y-1))
glow.save(OUT/'warden_glow.png')
ops=[{'op':'set_model_properties','model_identifier':'geometry.containment_warden','resolution':{'width':image.width,'height':image.height}},*groups]
for c in cubes:
 faces={}
 texture='warden_glow' if c['mat'] in ['crystal','glow'] else 'warden_atlas'
 for f,key in c['uv'].items():
  x,y=positions[key];faces[f]={'uv':[x,y,x+key[1],y+key[2]],'texture':texture}
 ops.append({'op':'add_cube',**{k:c[k] for k in ['name','from','to','parent','rotation','origin']},'box_uv':False,'texture':texture,'faces':faces})
for i in range(0,len(ops),20):(OUT/f'geometry-{i//20:02}.json').write_text(json.dumps(ops[i:i+20]),'utf-8')
(OUT/'geometry-plan.json').write_text(json.dumps({'cube_count':len(cubes),'groups':len(groups),'triangles_upper_bound':len(cubes)*12,'texture_size':image.size,'density_px_per_model_unit':2,'geometry_batches':math.ceil(len(ops)/20)},indent=2),'utf-8')
print('WARDEN_MCP_OPERATIONS_READY',len(cubes),'cubes',len(groups),'bones',image.size)
