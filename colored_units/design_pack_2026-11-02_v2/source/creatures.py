from modelkit import Model
import math

def body(m,c,size):m.group('body',c);m.beveled('body',c,size,'tissue','body')
def head(m,c,size,mat='shell',parent='body',face=None):
    m.group('head',c,parent,'head')
    if face:m.centered('head',c,size,mat,'head',faces={'north':face})
    else:m.beveled('head',c,size,mat,'head')
def ribs(m,c,w,h,d,bone='body',count=4):
    x,y,z=c
    for i in range(count):
        yy=y-h/2+(i+.5)*h/count
        for s in (-1,1):
            m.beam('rib',(x+s*w*.26,yy,z-d*.52),(x+s*w*.52,yy+.7,z),1.1,mat='bone',bone=bone)
            m.beam('rib_side',(x+s*w*.52,yy+.7,z),(x+s*w*.35,yy,z+d*.44),1,mat='bone',bone=bone)
def spine(m,start,step,count,bone='body',size=3):
    for i in range(count):
        x,y,z=[start[j]+step[j]*i for j in range(3)];m.centered('spine_plate',(x,y,z),(size,size*.65,size*.9),'bone',bone)
def pair_eyes(m,c,spacing=3,bone='head',size=1.5):
    for s in (-1,1):m.eye('eye',(c[0]+s*spacing,c[1],c[2]),size,bone)
def claw(m,name,base,length,bone='root',sign=1):
    x,y,z=base;m.beam(name,base,(x+sign*length*.35,y-length*.6,z-length*.55),1.5,mat='crystal',bone=bone)
    m.beam(name+'_tip',(x+sign*length*.35,y-length*.6,z-length*.55),(x+sign*length*.15,y-length*.85,z-length),.7,mat='glow',bone=bone)

def biped(key,label,skeleton=False):
    m=Model(key,label,'monsters');body(m,(0,18,0),(7 if skeleton else 8,11,3.8 if skeleton else 5))
    if skeleton:
        # The chest is an open rib cage, not a skin-colored solid slab.
        body_ids=set(m.groups['body']['children']);m.remove(lambda e:e['uuid'] in body_ids)
        m.centered('spinal_column',(0,18,1),(1.8,11,1.8),'bone','body');ribs(m,(0,19,0),8,8,4.2,count=4)
        m.centered('pelvis',(0,12,0),(6,2.2,3),'bone','body')
    else:
        m.centered('shirt',(0,19,0),(8.2,10,5.2),'shirt','body');m.centered('waist',(0,12.8,0),(8.2,2.8,5.3),'pants','body')
        for y in (15.5,18.5,21.5):m.centered('shirt_tear',(2.8,y,-2.68),(1.5,1,.2),'tissue','body')
    head(m,(0,28,0),(8,8,8),'skull' if skeleton else 'zskin',face='face_skull' if skeleton else 'face_zombie')
    for s in (-1,1):
        hip=(s*2.2,12,0);knee=(s*2.7,6,-.3);foot=(s*3,1,-.5)
        m.limb('leg_'+str(s),hip,knee,foot,1.6 if skeleton else 3.3,'bone' if skeleton else 'pants',0 if s==1 else math.pi)
        shoulder=(s*5.2,23,0);elbow=(s*6.2,16.8,-.4);hand=(s*6.3,12,-1.2)
        arm=m.group('arm_'+str(s),shoulder,'body','arm',s)
        m.beam('upper_arm',shoulder,elbow,1.5 if skeleton else 3.6,mat='bone' if skeleton else 'zskin',bone=arm,joint=True)
        m.group('hand_'+str(s),elbow,arm)
        m.beam('forearm',elbow,hand,1.4 if skeleton else 3.2,mat='bone' if skeleton else 'zskin',bone='hand_'+str(s))
        m.centered('palm',hand,(2,2,2),'bone' if skeleton else 'zskin','hand_'+str(s))
    return m

def hunter():
    m=biped('crystal_hunter','晶蚀追猎者');m.role='追猎 / 扑击'
    for s in (-1,1):
        arm='arm_'+str(s);m.centered('shoulder_armor',(s*5.2,23,0),(4.5,3.2,5.3),'shell',arm)
        for i in range(2 if s==1 else 3):m.crystal('shoulder_shard',(s*(4.5+i*.7),24,-.5+i),1.1,3+i,arm,rot=(0,0,-s*18))
        hand='hand_'+str(s)
        for i in range(3):claw(m,'forearm_claw',(s*6.3+(i-1)*1,12,-1.5),4.3,hand,s)
    m.centered('temple_lesion',(4.05,28,-1),(0.6,4,3),'tissue','head');m.crystal('temple_shard',(4.2,29,-1),1,2.8,'head',rot=(0,0,-20))
    m.creature_animations();return m

def caster():
    m=biped('rib_crystal_caster','棱骨术士',True);m.role='远程 / 晶刺法阵'
    m.centered('chest_resonator',(1,18,-2.2),(2.6,3.6,1.6),'crystal','body')
    for i in range(3):m.crystal('shoulder_crystal',(-5+i,24,0),1.5,3+i*.8,'arm_-1',rot=(0,0,15))
    m.group('cast_focus',(9,21,-7),'body');m.crystal('floating_lance',(9,16,-7),2.4,13,'cast_focus')
    for s in (-1,1):m.crystal('lance_barb',(9+s*1.2,19,-7),.8,3,'cast_focus',rot=(0,0,-s*30))
    m.animation('cast_focus_orbit',2,True,{'cast_focus':{'position':[(0,(0,0,0)),(1,(0,.6,0)),(2,(0,0,0))],'rotation':[(0,(0,0,0)),(2,(0,360,0))]}})
    m.creature_animations();return m

def siege():
    m=Model('ravager_siege_concept','裂地攻城兽','monsters','冲撞 / 地刺');body(m,(0,24,7),(24,22,34))
    m.centered('upper_shell',(0,33,8),(25,5,31),'shell','body')
    for z in (-3,4,11,18):m.centered('back_plate',(0,35,z),(22,3.5,5),'bone','body')
    m.group('neck',(0,27,-8),'body');m.centered('neck',(0,25,-11),(17,15,13),'tissue','neck')
    head(m,(0,25,-22),(18,15,16),'shell',parent='neck');m.centered('snout',(0,20,-31),(14,9,9),'shell','head')
    m.centered('nose_bridge',(0,26,-30),(6,6,6),'bone','head');pair_eyes(m,(0,28,-30.2),6,size=2.5)
    m.group('jaw',(0,17,-23),'head','jaw');m.centered('jaw',(0,15,-28),(16,4,13),'bone','jaw')
    for s in (-1,1):
        m.centered('nostril',(s*4.5,21,-35.6),(2,1.7,.5),'black','head')
        m.beam('horn_base',(s*9,30,-22),(s*12,34,-28),3.4,mat='bone',bone='head')
        m.beam('forward_horn',(s*12,34,-28),(s*12,38,-34),2.5,mat='bone',bone='head')
        m.crystal('horn_tip',(s*12,38,-34),2,4,'head',rot=(-35,0,-s*10))
        m.centered('shoulder_guard',(s*12,28,-1),(5,14,16),'bone','body')
        for j,z in enumerate((0,9,17)):m.crystal('dorsal_shard',(s*4,37,z),2.7,7+(2-j),'body',rot=(5,0,-s*14))
        for i,z in enumerate((-3,19)):
            m.limb('leg_'+str(s)+'_'+str(i),(s*10,20,z),(s*12,10,z),(s*12,1,z-2),5.5,'shell',math.pi*((i+(s==1))%2))
    ribs(m,(0,25,6),24,14,28,count=3);m.creature_animations();return m

def mother(key='brood_mother',label='巢心母体',scale=1):
    m=Model(key,label,'monsters','指挥 / 孵化 / 回防');m.group('body',(0,22,0))
    m.centered('central_stalk',(0,18,0),(10,28,10),'tissue','body')
    for i in range(8):
        a=i*math.tau/8;px,pz=math.cos(a)*13,math.sin(a)*13
        m.beam('rib_cage',(px,13,pz),(px*.65,38,pz*.65),2.8,mat='bone',bone='body')
        if i%2==0:m.beam('roof_rib',(px*.65,38,pz*.65),(0,43,0),2.5,mat='bone',bone='body')
    for s in (-1,1):
        for z in (-1,1):m.limb('anchor_'+str(s)+'_'+str(z),(s*9,23,z*9),(s*20,12,z*16),(s*25,1,z*22),4,'shell',parent='body',role='anchor')
    m.group('core',(0,29,-1),'body','sac');m.sac('core_cage',(0,29,-1),(11,13,10),'core','shell',False);m.crystal('heart',(0,24,-5.5),6,11,'core')
    for x in (-7,7):
        m.group('brood_sac_'+str(x),(x,18,0),'body','sac');m.sac('hanging_sac',(x,15,0),(9,14,10),'brood_sac_'+str(x))
    for i,x in enumerate((-9,-5,0,6,10)):m.crystal('crown',(x,37,3-abs(x)*.2),2.4,7+(i%3)*2,'body',rot=(0,0,-x))
    for z in (-3,1,5):m.centered('gill',(0,12,z),(18,.8,1.2),'membrane','body')
    if scale!=1:
        for e in m.data['elements']:
            for attr in ('from','to','origin'):e[attr]=[v*scale for v in e[attr]]
        for g in m.groups.values():g['origin']=[v*scale for v in g['origin']]
    m.creature_animations(stationary=True);return m

def beetle():
    m=Model('lurking_forager','潜伏觅食虫','monsters','侦察 / 猎物标记');body(m,(0,7,2),(9,5,13))
    for s in (-1,1):m.centered('elytra',(s*2.8,10,3),(5.5,3.5,13),'bone','body',rot=(0,0,s*10),pivot=(0,10,3))
    head(m,(0,7,-6),(7,4,5));pair_eyes(m,(0,7.3,-8.7),2,size=1)
    for s in (-1,1):
        m.beam('antenna',(s*2,9,-7),(s*4,12,-11),.65,mat='tissue',bone='head');m.centered('antenna_tip',(s*4,12,-11),(.9,.9,.9),'glow','head')
        for i,z in enumerate((-3,2,7)):m.limb('leg_'+str(s)+'_'+str(i),(s*3.7,7,z),(s*7.5,5,z-2),(s*9.2,1,z-3),1,'tissue',i*math.pi+s,parent='body',toes=False)
        for i in range(4):m.centered('underside_gill',(s*3.4,5,0+i*2),(2,.6,1.2),'membrane','body')
    m.creature_animations();return m

def flyer():
    m=Model('sporewing_scout','孢翼侦察者','monsters','飞行 / 传播标记');body(m,(0,15,1),(5,9,5));head(m,(0,20,-1),(6,4,5));pair_eyes(m,(0,20,-3.7),1.8,size=1)
    for s in (-1,1):
        wing=m.group('wing_'+str(s),(s*2,18,0),'body','wing',s)
        m.beam('leading_spar',(s*2,18,0),(s*12,24,1),1.3,mat='bone',bone=wing)
        m.beam('outer_spar',(s*12,24,1),(s*22,20,4),1.1,mat='bone',bone=wing)
        # Stepped membrane fan; no triangle meshing dependency in the .bbmodel.
        for j in range(6):
            x=s*(4+j*3);z=2+j*.35;yy=18+min(j,3)*1.3
            m.centered('wing_membrane',(x,yy,z+3),(3.4,.7,9-j*.5),'membrane',wing,rot=(0,-s*9,s*10),pivot=(x,yy,z))
            m.beam('finger_spar',(x,yy,z-1),(x+s*1.2,yy-1,z+7-j*.5),.6,mat='bone',bone=wing)
        m.beam('hind_claw',(s*2,11,2),(s*3,8,0),.8,mat='bone',bone='body')
    m.group('cargo_sac',(0,12,4),'body','sac');m.sac('spore_sac',(0,12,4),(5,5,5),'cargo_sac')
    for i in range(3):m.beam('trailing_root',(i-1,11,3),(i-1,5,8+i),.55,mat='tissue',bone='body')
    m.creature_animations(flying=True);return m

def worm(key='mycelial_burrower',label='菌丝掘地者',boss=False):
    m=Model(key,label,'monsters','地下突袭 / 环形地刺' if boss else '掘地 / 前线开拓');m.group('body',(0,5,0));s=2.7 if boss else 1
    points=[(0,5,20),(0,5,14),(0,7,8),(0,11,3),(0,16,0),(0,22,-1),(0,28,-3),(0,33,-6),(0,35,-11)]
    points=[tuple(v*s for v in p) for p in points];parent='body'
    for i,(a,b) in enumerate(zip(points,points[1:])):
        g=m.group('segment_'+str(i),a,parent,'chain',i*.4);w=(4.5+min(i,4)*.55)*s
        m.beam('muscle_segment',a,b,w,mat='tissue',bone=g)
        m.centered('collar',a,(w*1.25,w*.55,w*1.25),'bone',g)
        for side in (-1,1):m.centered('segment_side',(a[0]+side*w*.54,a[1],a[2]),(w*.25,w*.65,w*.75),'shell',g)
        if boss and i in (2,5):m.crystal('crystal_node',(a[0]+w*.5,a[1]+w*.4,a[2]),s*1.5,s*4,g,rot=(0,0,-25))
        parent=g
    c=points[-1];head(m,c,(9*s,8*s,8*s),'shell',parent=parent)
    # Open drilling mouth with four jaws framing an empty center.
    for side in (-1,1):
        m.centered('head_side',(c[0]+side*4.4*s,c[1],c[2]-4*s),(2.6*s,9*s,5*s),'bone','head')
        for yy in (-1,1):m.beam('jaw_tooth',(c[0]+side*4*s,c[1]+yy*3*s,c[2]-5*s),(c[0]+side*2.3*s,c[1]+yy*1.8*s,c[2]-8*s),1.2*s,mat='bone',bone='head')
        m.beam('digging_claw',(c[0]+side*5*s,c[1]-2*s,c[2]),(c[0]+side*9*s,c[1]-5*s,c[2]-3*s),1.5*s,mat='bone',bone='head')
    m.centered('mouth_dark',(c[0],c[1],c[2]-4.1*s),(5*s,5*s,.5*s),'black','head');m.centered('resonance_organ',(c[0],c[1],c[2]-4.5*s),(2*s,2*s,.3*s),'glow','head')
    m.creature_animations();return m

def carrier():
    m=Model('crystalback_carrier','晶背搬运兽','monsters','回收 / 运送生物质');body(m,(0,13,5),(14,11,23));head(m,(0,12,-10),(12,7,11));m.centered('snout',(0,9,-16),(11,4,4),'bone','head');pair_eyes(m,(0,13,-15.8),4,size=1.5)
    for s in (-1,1):
        for i,z in enumerate((-1,13)):m.limb('leg_'+str(s)+'_'+str(i),(s*6,11,z),(s*8,6,z),(s*8.5,1,z-1),3.8,'shell',math.pi*((i+(s==1))%2))
        for j,z in enumerate((0,8,15)):
            g=m.group('cargo_'+str(s)+'_'+str(j),(s*5.5,18,z),'body','sac');m.sac('cargo',(s*5.5,19,z),(8.5,9+j%2*2,9),g)
        m.centered('shoulder_guard',(s*7,17,-3),(4,9,9),'bone','body')
    for z in (-4,9):m.centered('cargo_strap',(0,22,z),(15,1.3,1.4),'bone','body')
    m.crystal('cargo_node',(-6,19,-3),2.1,5,'body',rot=(0,0,20));m.crystal('cargo_node',(6,19,-3),2.1,5,'body',rot=(0,0,-20))
    m.creature_animations();return m

def spider():
    m=Model('parasitic_spider_mother','寄生蛛母','monsters','结网 / 孵化幼体');body(m,(0,10,3),(10,7,17));head(m,(0,9,-8),(8,5,6),'bone')
    for x in (-2.5,0,2.5):m.eye('eye',(x,10,-11.3),.9)
    for s in (-1,1):
        for i,z in enumerate((-5,0,5,10)):
            m.limb('leg_'+str(s)+'_'+str(i),(s*4.5,10,z),(s*(12+(i%2)*2),8,z+(i-1.5)*3),(s*(15+(i%2)*2),1,z+(i-1.5)*5),1.8,'tissue',i*math.pi+s*.7,parent='body',toes=False)
        for j,z in enumerate((2,9)):
            g=m.group('brood_'+str(s)+'_'+str(j),(s*4,14,z),'body','sac');m.sac('brood_sac',(s*4,16,z),(6,7,8),g)
    for z in (0,5,10):m.centered('dorsal_plate',(0,14,z),(9,1.5,3),'bone','body')
    m.beam('silk_tether',(0,15,11),(0,9,19),.7,mat='skull',bone='body');m.sac('silk_bundle',(0,7,19),(5,6,5),'body','skull',False)
    for x in (-1,1):m.beam('mandible',(x*2,7,-10),(x*3,5,-14),1,mat='bone',bone='head')
    m.creature_animations();return m

def incubating_pod():
    m=Model('pulsing_hatch_sac','脉动孵化囊','monsters','驻扎 / 孵化支援');m.group('body',(0,12,0))
    for sx in (-1,1):
        for sz in (-1,1):
            m.beam('anchor',(sx*7,15,sz*7),(sx*11,2,sz*11),2,mat='bone',bone='body',joint=True)
            m.beam('root_toe',(sx*11,2,sz*11),(sx*15,1,sz*12),1.5,mat='shell',bone='body')
    for i in range(8):
        a=i*math.tau/8;x,z=math.cos(a)*8,math.sin(a)*8
        m.beam('cage_rib',(x,4,z),(x*.3,24,z*.3),1.5,mat='bone',bone='body')
        if z>0:m.centered('rear_membrane',(x*.85,13,z*.85),(3,15,1),'membrane','body',rot=(0,-math.degrees(a),0),pivot=(x*.85,13,z*.85))
    m.centered('nest_floor',(0,4,0),(15,2,15),'shell','body')
    for i,x in enumerate((-4,0,4)):
        g=m.group('larva_'+str(i),(x,8,0),'body','sac');m.sac('larval_sac',(x,8,0),(4.5,7,5),g,'skull',False);m.centered('sac_glint',(x,8,-2.55),(1,1,.2),'glow',g)
    m.beam('antenna',(0,24,0),(2,30,0),1.2,mat='tissue',bone='body');m.centered('antenna_light',(2,30,0),(2,2,2),'crystal','body')
    m.creature_animations(stationary=True);return m

def sentinel():
    m=biped('crystal_shield_sentinel','晶盾卫士');m.role='格挡 / 回防'
    for e in m.data['elements']:
        for face in e['faces'].values():
            mat,w,h,v=face['_tile']
            if mat in ('zskin','shirt','pants','face_zombie'):face['_tile']=m.atlas.get('shell' if mat!='face_zombie' else 'black',w,h,v)
    for side in (-1,1):m.centered('shoulder_plate',(side*6,23,0),(6,4,7),'bone','arm_'+str(side))
    shield='arm_-1';m.centered('living_shield',(-8,17,-3),(7,18,3.5),'shell',shield)
    for i in range(4):m.centered('shield_lamella',(-8,10+i*4,-5.1),(7.7,3.5,1.6),'bone',shield)
    for s in (-1,1):m.beam('shield_frame',(-8+s*4,8,-5),(-8+s*3,26,-5),1.2,mat='bone',bone=shield)
    m.crystal('shield_core',(-8,15,-6),1.5,5,shield);claw(m,'sword',(7,13,-2),8,'hand_1',1)
    pair_eyes(m,(0,28,-4.2),2.5);m.creature_animations();return m

def spitter():
    m=Model('rift_throat_spitter','裂喉喷吐者','monsters','远程喷吐 / 压制');body(m,(0,19,3),(9,12,9))
    for s in (-1,1):
        m.limb('leg_'+str(s),(s*3,15,4),(s*5,8,7),(s*4.5,1,-1),2.4,'tissue',math.pi*(s==1))
        arm=m.group('arm_'+str(s),(s*4.5,22,0),'body','arm',s);m.beam('short_arm',(s*4.5,22,0),(s*7,15,-3),1.4,mat='bone',bone=arm);claw(m,'small_claw',(s*7,15,-3),3,arm,s)
        g=m.group('throat_'+str(s),(s*3.5,22,-5),'body','sac');m.sac('throat_sac',(s*3.5,18,-5),(6,10,7),g)
    m.beam('bent_neck',(0,23,1),(0,28,-4),4.2,mat='tissue',bone='body')
    head(m,(0,29,-7),(8,6,10),'bone');m.group('jaw',(0,26,-4),'head','jaw');m.centered('lower_jaw',(0,25.5,-9),(7,1.5,9),'bone','jaw')
    pair_eyes(m,(0,30,-12.2),2.5,size=1.2)
    for s in (-1,1):
        for z in (-8,-11):m.centered('fang',(s*2.4,26.5,z),(1,2,1),'bone','head')
    for y in (19,22,25):m.centered('exhaust_gill',(0,y,8),(7,.8,3),'membrane','body')
    m.creature_animations();return m

def ambusher():
    m=Model('leapblade_ambusher','跃刃伏击者','monsters','跳跃 / 镰刃伏击');body(m,(0,21,2),(7,12,5));ribs(m,(0,22,2),8,9,6)
    for s in (-1,1):
        m.limb('leg_'+str(s),(s*3,17,3),(s*6,10,9),(s*6,1,-1),2.5,'tissue',math.pi*(s==1))
        arm=m.group('arm_'+str(s),(s*4,26,1),'body','arm',s);m.beam('arm',(s*4,26,1),(s*10,22,-2),2.1,mat='tissue',bone=arm,joint=True)
        m.beam('blade_base',(s*10,22,-2),(s*12,15,-8),3,mat='crystal',bone=arm)
        m.beam('blade_edge',(s*12,15,-8),(s*10,9,-14),1.7,mat='crystal',bone=arm)
        m.beam('blade_tip',(s*10,9,-14),(s*7,8,-16),.7,mat='glow',bone=arm)
        m.centered('shoulder_spur',(s*4.5,28,1),(4,4,6),'bone',arm,rot=(0,0,-s*22))
    head(m,(0,31,-1),(6,5,8),'bone');pair_eyes(m,(0,31,-5.2),1.8,size=1)
    m.group('tail',(0,18,4),'body','chain',1);m.beam('tail',(0,18,4),(0,16,16),1.8,mat='tissue',bone='tail');m.beam('tail_blade',(0,16,16),(0,20,24),1.4,mat='crystal',bone='tail')
    m.creature_animations();return m

def turret():
    m=Model('rootspike_turret','根刺炮台','monsters','地刺封锁 / 驻地防守');body(m,(0,10,1),(12,9,12))
    for sx in (-1,1):
        for sz in (-1,1):m.limb('root_'+str(sx)+'_'+str(sz),(sx*5,10,sz*4),(sx*10,5,sz*8),(sx*15,1,sz*11),2.4,'tissue',role='anchor',toes=False)
    m.group('head',(0,15,0),'body','head')
    for i,x in enumerate((-5,0,5)):
        m.centered('barrel_base',(x,15,0),(4,5,10),'bone','head',rot=(-30,0,0),pivot=(x,15,0))
        m.crystal('launch_organ',(x,15,-4),2.5,14,'head',rot=(-58,0,0))
        m.beam('barrel_rib',(x-1.8,13,-2),(x-1.8,19,-9),.9,mat='bone',bone='head')
    m.group('ammo_sac',(7,11,6),'body','sac');m.sac('ammo_sac',(7,11,6),(7,8,7),'ammo_sac')
    for s in (-1,1):m.beam('rear_support',(s*5,6,7),(s*4,20,5),1.6,mat='bone',bone='body')
    m.creature_animations(stationary=True);return m

def priest():
    m=Model('resonance_priest','共鸣祭司','monsters','强化集群 / 共鸣支援');body(m,(0,26,0),(6,12,4));ribs(m,(0,26,0),7,9,5)
    head(m,(0,37,-1),(5,7,4),'bone');pair_eyes(m,(0,37,-3.2),1.3,size=.8)
    for s in (-1,1):
        arm=m.group('arm_'+str(s),(s*3.5,31,0),'body','arm',s);m.beam('upper_arm',(s*3.5,31,0),(s*7,25,-1),1.4,mat='bone',bone=arm);m.beam('forearm',(s*7,25,-1),(s*10,28,-4),1,mat='bone',bone=arm);m.centered('open_hand',(s*10,28,-4),(2,1.3,2),'bone',arm)
    for i in range(8):
        a=i*math.tau/8;x,z=math.cos(a)*3,math.sin(a)*2
        m.beam('root_skirt',(x,21,z),(x*2.1,7+(i%3),z*2.1),1.2,mat='membrane',bone='body')
    m.group('halo',(0,34,4),'body')
    for i in range(8):
        if i==6:continue
        a=i*math.tau/8;x,y=math.sin(a)*11,34+math.cos(a)*11
        m.centered('halo_segment',(x,y,4),(1.6,4.3,2),'crystal','halo',rot=(0,0,-math.degrees(a)),pivot=(x,y,4))
    m.centered('heart',(0,27,-2.4),(2,3,1),'glow','body');m.creature_animations(flying=True)
    m.animation('halo_orbit',4,True,{'halo':{'rotation':[(0,(0,0,0)),(4,(0,0,360))]}});return m

def colossus():
    m=Model('siegebreaker_colossus','破城巨像','monsters','砸墙 / 震地');body(m,(0,29,4),(20,24,17));ribs(m,(0,29,3),22,18,18,count=5)
    for s in (-1,1):
        m.limb('leg_'+str(s),(s*7,20,10),(s*9,10,13),(s*10,1,8),4.5,'tissue',math.pi*(s==1))
        arm=m.group('arm_'+str(s),(s*11,39,0),'body','arm',s)
        m.beam('massive_upper_arm',(s*11,39,0),(s*19,23,-3),7,mat='tissue',bone=arm,joint=True)
        m.beam('massive_forearm',(s*19,23,-3),(s*20,6,-8),8,mat='shell',bone=arm)
        m.centered('knuckle',(s*20,4,-10),(11,7,12),'bone',arm)
        for j in range(3):m.centered('knuckle_pad',(s*20+(j-1)*3,4,-16),(2.6,5,2.5),'shell',arm)
        m.centered('shoulder_plate',(s*12,38,0),(12,8,15),'bone',arm,rot=(0,0,-s*10))
        m.crystal('shoulder_node',(s*8,41,3),2.2,6,'body',rot=(0,0,-s*15))
    head(m,(0,39,-9),(9,8,8),'bone');pair_eyes(m,(0,40,-13.2),2.5,size=1.8)
    m.centered('lower_face',(0,35,-12),(8,3,4),'shell','head');spine(m,(0,40,11),(0,-4,0),5,size=4)
    m.creature_animations();return m

def walking_nest():
    m=Model('walking_brood_crab','行巢巨蟹','monsters','移动巢穴 / 释放护卫');body(m,(0,37,0),(43,23,49))
    # Broad layered carapace, with open hanging brood cavities beneath it.
    m.centered('shell_lower',(0,48,0),(58,6,63),'shell','body');m.centered('shell_step',(0,53,0),(50,5,56),'bone','body');m.centered('shell_top',(0,57,0),(38,4,42),'shell','body')
    for s in (-1,1):
        for i,z in enumerate((-20,0,20)):
            m.limb('leg_'+str(s)+'_'+str(i),(s*21,40,z),(s*37,25,z+(i-1)*10),(s*44,1,z+(i-1)*17),6,'shell',i*math.pi+s,parent='body',toes=False)
            m.centered('knee_plate',(s*37,25,z+(i-1)*10),(9,10,11),'bone','leg_'+str(s)+'_'+str(i)+'_lower')
        claw_arm=m.group('claw_'+str(s),(s*14,30,-24),'body','arm',s)
        m.beam('crusher_arm',(s*14,30,-24),(s*21,15,-42),5,mat='bone',bone=claw_arm)
        for offset in (-3,3):m.beam('crusher_pincer',(s*21+offset,15,-42),(s*21+offset*.4,11,-53),2.5,mat='bone',bone=claw_arm)
        for i,z in enumerate((-13,5,20)):
            sac=m.group('side_brood_'+str(s)+'_'+str(i),(s*18,38,z),'body','sac');m.sac('hatch_sac',(s*18,35,z),(13,18,13),sac)
        for z in (-24,24):m.crystal('corner_node',(s*21,52,z),3.3,10,'body',rot=(0,0,-s*10))
    for x,z in ((-12,-8),(12,-8),(0,16)):
        sac=m.group('roof_brood_'+str(x),(x,61,z),'body','sac');m.sac('roof_brood',(x,65,z),(17,15,19),sac)
        m.beam('brood_arch',(x-10,57,z),(x-5,74,z),2.2,mat='bone',bone='body');m.beam('brood_arch',(x+10,57,z),(x+5,74,z),2.2,mat='bone',bone='body')
    head(m,(0,31,-26),(16,10,8),'bone');pair_eyes(m,(0,33,-30.2),4,size=2)
    m.creature_animations();return m

def calamity_mother():
    m=Model('calamity_brood_mother','灾厄母巢','monsters','高阶指挥 / 紧急孵化');m.group('body',(0,55,0))
    for sx in (-1,1):
        for sz in (-1,1):
            m.limb('buttress_'+str(sx)+'_'+str(sz),(sx*16,77,sz*15),(sx*31,34,sz*28),(sx*42,1,sz*38),7,'shell',role='anchor',toes=False)
            m.beam('outer_rib',(sx*18,77,sz*15),(sx*38,12,sz*32),2.5,mat='bone',bone='body')
    # Structural ring + open central body. The core is held by ribs, not a floating solid box.
    for i in range(8):
        a=i*math.tau/8;x,z=math.sin(a)*22,math.cos(a)*22
        m.beam('inner_rib',(x,18,z),(x*.60,77,z*.60),3.4,mat='bone',bone='body')
        m.beam('crown_arch',(x*.6,77,z*.6),(0,91,0),2.5,mat='bone',bone='body')
        m.centered('equator_plate',(x,47,z),(9,5,6),'bone','body',rot=(0,-math.degrees(a),0),pivot=(x,47,z))
    for sx in (-1,1):
        for sz in (-1,1):
            sac=m.group('hatch_chamber_'+str(sx)+'_'+str(sz),(sx*13,32,sz*11),'body','sac');m.sac('brood_chamber',(sx*13,30,sz*11),(19,26,19),sac)
            for j in range(3):m.centered('chamber_gill',(sx*13,18+j*2,sz*11),(18,.8,15),'membrane',sac)
    m.group('core',(0,65,0),'body','sac');m.sac('core_shell',(0,65,0),(19,22,18),'core','shell',False);m.crystal('core_crystal',(0,56,-9),10,20,'core')
    for i in range(5):
        a=i*math.tau/5;x,z=math.sin(a)*11,math.cos(a)*11
        m.beam('antenna',(x,82,z),(x*1.7,100+(i%2)*6,z*1.7),2.2,mat='tissue',bone='body');m.crystal('crown_receiver',(x*1.7,100+(i%2)*6,z*1.7),2.8,6,'body')
    for s in (-1,1):m.beam('lower_opening_rib',(s*7,5,-20),(s*10,22,-20),2,mat='bone',bone='body')
    m.creature_animations(stationary=True);return m

def build_creatures():
    return [hunter(),caster(),siege(),mother(),beetle(),flyer(),worm(),carrier(),spider(),incubating_pod(),sentinel(),spitter(),ambusher(),turret(),priest(),colossus(),walking_nest(),worm('abyssal_burrow_king','深渊掘王',True),calamity_mother()]
