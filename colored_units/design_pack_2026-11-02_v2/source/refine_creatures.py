"""Second-pass silhouette and anatomy work driven by the illustrated concept sheets."""
from modelkit import rotation_matrix
import numpy as np,math

def scale_model(m,value):
    for e in m.data['elements']:
        for attr in ('from','to','origin'):e[attr]=[round(v*value,6) for v in e[attr]]
        w,h,d=[e['to'][i]-e['from'][i] for i in range(3)]
        sizes={'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}
        for face,f in e['faces'].items():
            mat,_,_,variant=f['_tile'];f['_tile']=m.atlas.get(mat,*sizes[face],variant)
    for g in m.groups.values():g['origin']=[round(v*value,6) for v in g['origin']]
    return m

def shingle_row(m,start,step,count,size,bone='body',slope=0):
    for i in range(count):
        c=[start[j]+step[j]*i for j in range(3)];c[1]-=i*.22;m.plate('overlap_carapace',c,size,bone,rot=(slope,0,0))

def grim_mask(m,c,size=(6,7,5),bone='head'):
    x,y,z=c;w,h,d=size
    m.beveled('cranial_mask',c,(w,h,d),'bone',bone)
    for s in (-1,1):
        m.plate('angular_brow',(x+s*w*.26,y+h*.22,z-d*.51),(w*.46,h*.15,1.2),bone,rot=(0,0,-s*16))
        m.centered('eye_cavity',(x+s*w*.24,y+h*.03,z-d*.54),(w*.3,h*.16,.6),'black',bone)
        m.centered('narrow_eye',(x+s*w*.24,y+h*.04,z-d*.59),(w*.22,max(.4,h*.07),.3),'glow',bone)
        m.plate('cheek_bone',(x+s*w*.35,y-h*.23,z-d*.39),(w*.24,h*.4,1.1),bone,rot=(0,0,s*12))
    m.centered('nasal_slot',(x,y-h*.13,z-d*.55),(max(.4,w*.13),h*.22,.6),'black',bone)

def reinforce(m):
    key=m.key
    if key=='crystal_hunter':
        # Localized infection keeps most of the zombie's familiar skin and clothing.
        for i in range(4):
            m.plate('infected_forearm',(-6.3,15+i*.85,-1.7),(3.6,1.1,1.2),'arm_-1',rot=(0,0,8))
        m.beam('forearm_tendon',(-7.5,21,-1),(-7.3,13,-2),.65,mat='membrane',bone='arm_-1')
        m.sac('shoulder_lesion',(-5.6,23,1),(4.2,3.8,4),'arm_-1',ribs=False)
        for i in range(3):m.centered('torn_shirt',(2+i*.6,15+i*2,-2.76),(1.1,1.8,.25),'tissue','body')
    elif key=='rib_crystal_caster':
        m.plate('collar_left',(-3,23,0),(3.5,2.2,5),'body',rot=(0,0,12))
        m.plate('collar_right',(3,23,0),(3.5,2.2,5),'body',rot=(0,0,-12))
        m.centered('lower_jaw',(0,24.5,-2.1),(6.2,1.7,3.6),'bone','head')
        for x in (-2,-.7,.7,2):m.centered('skull_tooth',(x,24.7,-4.15),(.65,1.3,.55),'skull','head')
        for x in (-1,1):m.beam('back_root',(x*2,19,2),(x*3,9,3),.6,mat='membrane',bone='body')
    elif key=='ravager_siege_concept':
        m.remove(lambda e:e['name'].startswith(('body_','upper_shell_','back_plate_','shoulder_guard_')))
        m.sac('organic_thorax',(0,24,7),(24,23,34),'body','tissue',False)
        for s in (-1,1):
            for col in range(3):
                for row,z in enumerate((-4,3,10,17)):
                    x=s*(2.8+col*4.3);yy=36-col*3.4-row*.18
                    m.plate('arched_dorsal_armor',(x,yy,z),(7.4,3.4,6.3),'body',rot=(0,0,-s*(10+col*14)))
            for j in range(3):m.plate('layered_shoulder',(s*12,33-j*4,-1+j*.6),(5.4,5.2,10-j),'body',rot=(0,0,-s*14))
        for s in (-1,1):
            shingle_row(m,(s*11.7,30,-4),(0,-1.3,5.5),5,(5,6.2,6),'body')
            m.plate('heavy_cheek',(s*8.4,26,-24),(3.5,9,8),'head',rot=(0,0,-s*10))
            m.plate('brow',(s*5,29,-29),(7,3.5,3),'head',rot=(0,0,-s*10))
            m.beam('jaw_tendon',(s*7.5,21,-21),(s*7.8,16,-30),1.3,mat='tissue',bone='head')
            for i in range(3):m.centered('tusk',(s*(3.5+i*1.8),18,-32),(1.1,3.3+i*.3,1.2),'bone','jaw')
            for z in (-3,19):
                g='leg_'+str(s)+'_'+str(0 if z==-3 else 1)
                m.plate('leg_cuirass',(s*11.7,14,z),(6.5,8,5),g)
        shingle_row(m,(0,36,-4),(0,0,5.5),5,(6,2.2,6),'body')
        m.centered('mouth_cavity',(0,17.7,-33),(11,2.2,.5),'black','head')
        for j in range(5):m.centered('snout_wrinkle',(0,21+j*.7,-35.1),(9-j*.8,.4,.45),'shell','head')
        m.sac('exposed_shoulder',(-13,27,3),(5.5,10,8),'body')
        for i in range(3):m.crystal('shoulder_fracture',(-14,29+i*.6,1+i*2),1.6,5+i*.5,'body',rot=(0,0,25))
    elif key=='brood_mother':
        # A continuous protective canopy and multiple full sacs replace the first spindly arch.
        for i in range(8):
            a=i*math.tau/8;x,z=math.cos(a)*11,math.sin(a)*11
            m.plate('canopy_plate',(x,35,z),(7.5,4,8),'body',rot=(0,-math.degrees(a),0))
            if i%2==1:m.sac('outer_sac',(x*.7,22,z*.7),(9,13,9),'body',ribs=False)
            for j in range(3):m.beam('hanging_gill',(x,30-j,z),(x*.9,16+j,z*.9),.75,mat='membrane',bone='body')
        for x in (-4,0,4):m.centered('frontal_heart_gap',(x,29,-6),(1,7,1),'bone','core')
        scale_model(m,1.2)
    elif key=='lurking_forager':
        m.remove(lambda e:e['name'].startswith('elytra_'))
        for s in (-1,1):
            for i,z in enumerate((-1.5,3,7.5)):
                m.plate('beetle_elytron',(s*2.4,9.8-i*.2,z),(4.7,2.7,5.4),'body',rot=(0,0,s*12))
            m.beam('mandible',(s*2.7,5.8,-7),(s*3.4,4.6,-10),.8,mat='bone',bone='head')
        m.plate('pronotum',(0,9,-3.5),(7,2.3,4),'body')
    elif key=='sporewing_scout':
        for s in (-1,1):
            wing='wing_'+str(s)
            for j in range(5):
                x=s*(6+j*3);yy=20+min(j,2)*1.1
                m.beam('wing_rib',(x,yy,.5),(x+s*1.4,yy-1,7-j*.3),.55,mat='bone',bone=wing)
                m.centered('wing_tip_joint',(x+s*1.4,yy-1,7-j*.3),(.9,.9,.9),'crystal',wing)
            m.beam('head_antenna',(s*2,21,-1),(s*3.5,24,0),.65,mat='bone',bone='head')
        m.sac('secondary_spore_pod',(0,13,4.6),(4,4.5,4),'cargo_sac')
    elif key in ('mycelial_burrower','abyssal_burrow_king'):
        boss=key=='abyssal_burrow_king';s=2.7 if boss else 1
        for i in range(8):
            g=m.groups['segment_'+str(i)];c=g['origin'];w=(4.5+min(i,4)*.55)*s
            for side in (-1,1):
                m.plate('segment_armor',(c[0]+side*w*.46,c[1],c[2]),(w*.48,w*.8,w*.9),g['name'],rot=(0,0,-side*12))
            m.plate('dorsal_lamella',(c[0],c[1]+w*.43,c[2]),(w,w*.25,w*.85),g['name'])
        hc=m.groups['head']['origin']
        for i in range(8):
            a=i*math.tau/8;x,y=hc[0]+math.sin(a)*3.5*s,hc[1]+math.cos(a)*3.5*s
            m.beam('drilling_mandible',(x,y,hc[2]-4*s),(hc[0]+math.sin(a)*2*s,hc[1]+math.cos(a)*2*s,hc[2]-8*s),1*s,mat='bone',bone='head')
        if boss:scale_model(m,1.7)
    elif key=='crystalback_carrier':
        for s in (-1,1):
            shingle_row(m,(s*5.5,18,-3),(0,-.35,3.8),6,(7,2.8,4.5))
            m.plate('cheek',(s*5.5,12,-11),(3,6,8),'head')
            for z in (-4,9):m.beam('cargo_binding',(s*6.8,22,z),(s*7.5,12,z),.8,mat='bone',bone='body')
        m.centered('muzzle_gap',(0,9.6,-18.1),(8,.8,.4),'black','head')
    elif key=='parasitic_spider_mother':
        m.sac('abdomen_hull',(0,12,6),(12,11,17),'body','tissue',False)
        shingle_row(m,(0,15,-4),(0,.3,3.2),6,(8.5,2.6,4))
        for s in (-1,1):
            for j in range(2):
                m.beam('spinner_root',(s*2,12,12),(s*3,10,17+j),.8,mat='skull',bone='body')
            m.beam('fang',(s*1.8,7,-10),(s*2.2,4,-13),1,mat='bone',bone='head')
            for i in range(2):m.eye('small_eye',(s*(1+i*2),11.1,-10.9),.65,'head')
        for yy in (0,1,2):m.beam('silk_strand',(-1+yy,14,10),(-1+yy,6,19),.35,mat='skull',bone='body')
    elif key=='pulsing_hatch_sac':
        for i in range(8):
            a=i*math.tau/8;x,z=math.cos(a)*8,math.sin(a)*8
            if z>-.1:
                for j in range(5):m.beam('gill_curtain',(x,22-j*.4,z),(x*.84,10+j*.3,z*.84),.55,mat='membrane',bone='body')
            m.plate('anchor_plate',(x,4,z),(4,2,4),'body',rot=(0,-math.degrees(a),0))
        for x in (-4,0,4):m.centered('larval_eye',(x,8,-2.7),(1,.5,.3),'crystal','larva_'+str(int(x/4)+1))
    elif key=='crystal_shield_sentinel':
        m.remove(lambda e:e['name'].startswith('head_'))
        grim_mask(m,(0,28,0),(5.8,7.5,5.5))
        for s in (-1,1):
            shingle_row(m,(s*2,22,-3),(0,-2.4,0),4,(4,2.7,1.6),'body',slope=s*7)
            m.plate('shoulder_overhang',(s*6,24,0),(6,4.5,7),'arm_'+str(s),rot=(0,0,-s*15))
        for i in range(3):m.plate('shield_chevron',(-8,12+i*4,-5.6),(7,3.7,1.5),'arm_-1',rot=(0,0,5))
        m.beam('shield_spine',(-8,8,-6.1),(-8,26,-6.1),.9,mat='crystal',bone='arm_-1')
    elif key=='rift_throat_spitter':
        for s in (-1,1):
            for j in range(4):m.plate('back_shingle',(s*3,24-j*2.2,7),(4.5,3,3),'body',rot=(10,0,s*8))
            m.beam('throat_tendon',(s*2.2,27,-5),(s*4.3,18,-8),.9,mat='tissue',bone='body')
            for z in (-7,-10):m.centered('hanging_fang',(s*3,25.8,z),(1.1,3.2,1),'bone','head')
        m.centered('open_throat',(0,27,-12.15),(4.5,2,.7),'black','head')
        m.plate('long_snout',(0,31,-10),(8,2.7,7),'head')
    elif key=='leapblade_ambusher':
        for s in (-1,1):
            arm='arm_'+str(s);m.plate('arm_blade_guard',(s*10,20,-3),(3.8,6,4),arm,rot=(0,0,s*17))
            m.beam('scythe_back',(s*10,23,-2),(s*12.8,15,-8),1.5,mat='bone',bone=arm)
            m.beam('sickle_inner_edge',(s*12,15,-8),(s*8.7,9,-15),1.1,mat='glow',bone=arm)
            shingle_row(m,(s*2.5,26,3),(0,-2.7,.3),3,(3.6,3,2.4))
        m.plate('crest',(0,33,0),(4,2.7,6),'head',rot=(-15,0,0))
        m.centered('mouth',(0,29.4,-5.1),(3.5,.5,.3),'black','head')
    elif key=='rootspike_turret':
        for s in (-1,1):
            for i in range(3):m.plate('launch_housing',(s*6,12+i*2,0),(3.5,3,10),'head',rot=(-24,0,s*10))
        for x in (-5,0,5):
            m.centered('loading_collar',(x,16.5,-2.7),(4.6,2,5),'shell','head',rot=(-30,0,0),pivot=(x,15,0))
        for i in range(4):m.beam('root_fiber',(-5+i*3,6,6),(-8+i*5,1,11),.8,mat='membrane',bone='body')
    elif key=='resonance_priest':
        m.remove(lambda e:e['name'].startswith('head_'));grim_mask(m,(0,37,-1),(4.7,8,4.5))
        for s in (-1,1):
            for i in range(4):m.plate('vestment',(s*(2+i*.6),20-i*2,0),(2.5,3.4,3.5),'body',rot=(0,0,-s*12))
            m.beam('arm_tendon',(s*4,30,0),(s*8,26,-2),.55,mat='membrane',bone='arm_'+str(s))
        for i in range(6):m.centered('vertebra',(0,23+i*2,2.5),(2.1,1.1,1.6),'bone','body')
    elif key=='siegebreaker_colossus':
        m.remove(lambda e:e['name'].startswith('head_'));grim_mask(m,(0,39,-9),(8.4,7.8,7))
        for s in (-1,1):
            arm='arm_'+str(s)
            for i in range(3):m.plate('layered_deltoid',(s*(11+i*1.1),39-i*2,0),(10-i,4,13-i),arm,rot=(0,0,-s*12))
            for i in range(3):m.plate('forearm_lamella',(s*20,9+i*4,-8),(9,4.5,8),arm)
            for i in range(3):m.centered('palm_digit',(s*20+(i-1)*3,2.6,-15.8),(2.2,4,3.2),'shell',arm)
        for i in range(5):m.centered('abdominal_plate',(0,22+i*2.2,-5.9),(8+i*.7,1.4,2),'shell','body')
    elif key=='walking_brood_crab':
        for s in (-1,1):
            for i,z in enumerate((-21,-10,1,12,23)):
                m.plate('shell_eave',(s*25,47,z),(10,4.5,12),'body',rot=(0,0,-s*18))
                for j in range(3):m.beam('gill_fringe',(s*25,45,z+j*2),(s*25.7,31+(j%2)*3,z+j*2),1,mat='membrane',bone='body')
            for j,z in enumerate((-20,0,20)):
                g='leg_'+str(s)+'_'+str(j)
                m.plate('hip_guard',(s*23,39,z),(10,10,12),g,rot=(0,0,-s*10))
        shingle_row(m,(0,58,-20),(0,0,8),6,(42,3.2,10),'body')
        scale_model(m,1.5)
    elif key=='calamity_brood_mother':
        for i in range(8):
            a=i*math.tau/8;x,z=math.sin(a)*22,math.cos(a)*22
            for j in range(3):m.plate('armored_canopy',(x*(1-j*.08),57+j*6,z*(1-j*.08)),(12,5.5,12),'body',rot=(0,-math.degrees(a),0))
            for j in range(5):m.beam('hanging_membrane',(x,52-j*.3,z),(x*.85,28+j*.7,z*.85),1.2,mat='membrane',bone='body')
        for sx in (-1,1):
            for sz in (-1,1):
                for j in range(4):m.plate('buttress_lamella',(sx*(22+j*4),58-j*12,sz*(20+j*4)),(11,10,10),'body',rot=(0,0,sx*10))
        for x in (-9,9):m.beam('core_frame',(x,53,-11),(x*.6,79,-9),2,mat='bone',bone='body')
        scale_model(m,1.5)
    return m
