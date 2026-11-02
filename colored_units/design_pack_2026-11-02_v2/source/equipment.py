from modelkit import Model
import math

def armor(key,label,heavy=False):
    m=Model(key,label,'armor','四件套可编辑分组 / 人形骨架');m.group('body',(0,18,0));mat='bone';thick=1.2 if heavy else .7
    # Player reference guides are unexported and hidden in the project; the armor itself is hollow.
    m.group('reference_player',(0,0,0),visible=False)
    for name,c,size in [('head',(0,28,0),(8,8,8)),('torso',(0,18,0),(8,12,4)),('left_leg',(-2,6,0),(4,12,4)),('right_leg',(2,6,0),(4,12,4)),('left_arm',(-6.5,18,0),(4,12,4)),('right_arm',(6.5,18,0),(4,12,4))]:
        e=m.centered('guide_'+name,c,size,'pad','reference_player');e['visibility']=False;e['export']=False
    m.group('helmet',(0,28,0),'body')
    for s in (-1,1):
        m.centered('helmet_side',(s*4.4,28,.5),(thick,8.6,8.3),mat,'helmet')
        m.centered('brow',(s*2.4,30,-4.4),(3.7,2,thick),mat,'helmet',rot=(0,0,-s*7))
        m.centered('cheek_guard',(s*3,25.7,-4.4),(2,3,thick),mat,'helmet')
        m.centered('eye_slit',(s*2,28.2,-4.5),(2.1,.7,.3),'crystal','helmet')
    m.centered('helmet_top',(0,32.3,0),(9.2,thick,9),mat,'helmet');m.centered('helmet_back',(0,28,4.3),(9,8.5,thick),'shell','helmet')
    m.group('chestplate',(0,22,0),'body')
    for i in range(3):
        m.centered('chest_lamella',(0,22.5-i*2.8,-2.9),(8.8+(heavy*.8),2.6,thick),'bone' if i!=1 else 'shell','chestplate')
    m.centered('backplate',(0,19,2.8),(9,10,thick),'shell','chestplate')
    m.centered('belt',(0,12.3,0),(9.4,2.2,5.7),'darkmetal','chestplate')
    m.centered('chest_socket',(0,20.5,-3.7),(3,3,1.3),'darkmetal','chestplate');m.crystal('resonance_cell',(0,19.4,-4.2),1.8,3,'chestplate',rot=(0,0,0))
    for s in (-1,1):
        m.group('arm_'+str(s),(s*6.5,24,0),'body','arm',s)
        m.centered('pauldron',(s*6.3,24,0),(6.4 if heavy else 5.2,3,6.5),mat,'arm_'+str(s),rot=(0,0,-s*9))
        if heavy:m.centered('pauldron_second',(s*7.3,22.5,0),(5.8,2.5,6.2),'shell','arm_'+str(s))
        for y in (19,15):m.centered('bracer',(s*6.5,y,-2.4),(4.4,3,thick),'shell' if y==19 else mat,'arm_'+str(s))
        m.group('leg_'+str(s),(s*2.25,12,0),'root','leg',0 if s==1 else math.pi)
        for i,y in enumerate((10,7)):
            m.centered('thigh_guard',(s*2.3,y,-2.5),(4.2,2.8,thick),mat if i else 'shell','leg_'+str(s))
        m.centered('knee',(s*2.3,5.6,-2.9),(4.4,2.1,1.2),mat,'leg_'+str(s))
        m.centered('boot_front',(s*2.3,2.3,-2.6),(4.6,3.8,thick),mat,'leg_'+str(s));m.centered('boot_toe',(s*2.3,.8,-1),(4.7,1.8,6),'shell','leg_'+str(s))
        m.centered('boot_outer',(s*4.4,2.5,0),(thick,4,4.8),mat,'leg_'+str(s))
    if heavy:
        m.sac('back_resonator',(0,20,4.5),(5,7,3),'chestplate','shell',True)
        for y in (18,21):m.centered('back_glint',(0,y,6),(1.6,.8,.5),'glow','chestplate')
    else:
        for y in (15,18,21,24):m.centered('spinal_guard',(0,y,3.4),(2.5,2,1.5),'bone','chestplate')
    m.animation('inspection_pose',2,True,{'arm_-1':{'rotation':[(0,(0,0,0)),(1,(0,0,8)),(2,(0,0,0))]},'arm_1':{'rotation':[(0,(0,0,0)),(1,(0,0,-8)),(2,(0,0,0))]}})
    return m

def spear():
    m=Model('prism_spear','棱晶长枪','weapons','突刺武器');m.group('shaft',(0,0,0));m.centered('shaft',(0,13,0),(1.4,26,1.4),'darkmetal','shaft')
    for y in (4,7,10,13,16):m.centered('grip_ring',(0,y,0),(1.8,.7,1.8),'pad','shaft')
    m.centered('socket',(0,26,0),(3,4,3),'bone','shaft');m.crystal('spearhead',(0,27,0),4.5,16,'shaft')
    for s in (-1,1):m.crystal('barb',(s*1.5,26,0),1.3,5,'shaft',rot=(0,0,-s*30))
    m.crystal('butt_spike',(0,0,0),1.6,4,'shaft',rot=(180,0,0));weapon_animation(m);return m
def hammer():
    m=Model('earthsplitter_hammer','裂地战锤','weapons','震地 / 地刺');m.group('shaft');m.centered('haft',(0,12,0),(2,24,2),'darkmetal','shaft')
    for y in (4,7,10,13):m.centered('grip',(0,y,0),(2.6,1.2,2.6),'pad','shaft')
    m.centered('head_crossbar',(0,26,0),(14,4,5),'darkmetal','shaft')
    for s in (-1,1):
        m.centered('hammer_cheek',(s*6,26,0),(4.5,10,7),'bone','shaft');m.centered('striking_face',(s*8.6,26,0),(1.1,11,8),'shell','shaft')
    m.crystal('head_core',(0,23,-.5),3.2,7,'shaft');weapon_animation(m);return m
def crossbow():
    m=Model('crystal_spike_crossbow','晶刺弩','weapons','晶刺发射器');m.group('body',(0,0,0));m.centered('stock',(0,5,2),(3,4,16),'darkmetal','body')
    m.centered('grip',(0,1,7),(2.4,7,3),'wood','body',rot=(-15,0,0),pivot=(0,4,7));m.centered('rail',(0,8,-1),(2.5,2,17),'shell','body')
    m.group('bow',(0,6,-4),'body')
    for s in (-1,1):
        m.beam('bow_limb',(s*1,6,-4),(s*9,6,-2),1.7,mat='bone',bone='bow');m.beam('bow_limb_tip',(s*9,6,-2),(s*12,6,1),1.2,mat='shell',bone='bow')
        m.beam('string',(s*12,6,1),(0,6,6),.35,mat='membrane',bone='bow')
    m.group('ammo',(0,8,0),'body');m.crystal('loaded_shard',(0,8,-1),1.7,10,'ammo',rot=(-90,0,0))
    m.centered('rear_sight',(0,10,5),(3,2,1),'steel','body');weapon_animation(m)
    m.animation('reload',1.4,False,{'bow':{'scale':[(0,(1,1,1)),(.7,(.82,1,1)),(1.4,(1,1,1))]},'ammo':{'position':[(0,(0,0,8)),(1.4,(0,0,0))]}});return m
def staff():
    m=Model('resonance_staff','共鸣法杖','weapons','共鸣施法');m.group('shaft');m.centered('shaft',(0,15,0),(1.7,30,1.7),'darkmetal','shaft')
    for y in (5,9,13,17,23):m.centered('grip',(0,y,0),(2.2,.9,2.2),'bone','shaft')
    for i in range(8):
        if i==5:continue
        a=i*math.tau/8;x,y=math.sin(a)*6,35+math.cos(a)*6
        m.centered('resonance_ring',(x,y,0),(2.1,5,2.1),'bone','shaft',rot=(0,0,90-math.degrees(a)),pivot=(x,y,0))
    m.group('focus',(0,35,0),'shaft');m.crystal('focus',(0,32,0),2.8,7,'focus');m.animation('focus_orbit',3,True,{'focus':{'rotation':[(0,(0,0,0)),(3,(0,360,0))]}});weapon_animation(m);return m
def weapon_animation(m):
    m.animation('attack',.8,False,{'root':{'rotation':[(0,(0,0,0)),(.2,(-25,0,-10)),(.4,(40,0,10)),(.8,(0,0,0))]}})

def loot_models():
    result=[]
    m=Model('infected_tissue_pickup','感染组织','loot','基础组织');
    for i in range(3):
        m.centered('tissue_fold',((i-1)*3,2+i*.5,0),(5,3,6),'tissue',rot=(0,i*12,(i-1)*10))
        m.centered('fiber_band',((i-1)*3,3.7+i*.5,0),(1,1,5),'membrane')
    result.append(m)
    m=Model('mycelium_bundle','菌丝束','loot','纤维材料')
    for i in range(8):
        a=i*math.tau/8;p=(math.cos(a)*.7,1,math.sin(a)*.7);q=(math.cos(a)*4,8+(i%3),math.sin(a)*3);m.beam('filament',p,q,.65,mat='skull');m.beam('branch',q,(q[0]*1.5,q[1]+2,q[2]*1.4),.45,mat='bone')
    m.centered('binding',(0,3,0),(3,1.5,3),'membrane');result.append(m)
    m=Model('amethyst_fragment_cluster','晶体碎片','loot','晶体材料')
    for i,(x,z,h) in enumerate(((-3,0,6),(2,1,8),(0,-3,4))):m.crystal('shard',(x,0,z),2.8,h,rot=(i*8,0,(i-1)*15))
    result.append(m)
    m=Model('spore_pod_drop','孢子囊','loot','孢子材料');m.sac('pod',(0,4,0),(7,8,6))
    for x,y in ((-2,3),(1,5),(0,2)):m.centered('pore',(x,y,-3.1),(1,1,.5),'glow')
    result.append(m)
    m=Model('chitin_plate_drop','甲壳板','loot','护甲材料')
    for i in range(3):m.centered('plate',((i-1)*.7,1+i*1.6,i*.6),(8-i,1.6,7),'bone',rot=(0,i*9,(i-1)*4));m.centered('edge',((i-1)*.7,.5+i*1.6,-3+i*.6),(8-i,.8,1.2),'shell')
    result.append(m)
    m=Model('condensed_biomass_drop','浓缩生物质','loot','浓缩资源');m.sac('membrane',(0,4,0),(8,7,7),mat='tissue',ribs=False);m.centered('inner_glow',(0,4,-3.7),(2.6,2.6,.6),'crystal')
    for y in (2,5):m.centered('rib',(0,y,-3.8),(7,.65,.8),'membrane')
    result.append(m)
    m=Model('resonant_organ_drop','共鸣器官','loot','特化器官')
    m.beam('fiber_stem',(-4,0,0),(0,5,0),1.5,mat='tissue')
    for i,(x,y) in enumerate(((-1,5),(2,9))):m.sac('organ',(x,y,0),(5,5,4));m.crystal('node',(x,y-1,-1),1.9,4)
    result.append(m)
    m=Model('brood_core_drop','母体核心','loot','母体稀有材料');m.crystal('core',(0,1,0),4.2,9)
    for i in range(8):
        a=i*math.tau/8;x,z=math.sin(a)*3,math.cos(a)*3;m.beam('core_rib',(x,1,z),(x*.65,10,z*.65),1.1,mat='bone')
    for s in (-1,1):
        for z in (-1,1):m.beam('root_prong',(s*2,2,z*2),(s*6,0,z*5),.9,mat='tissue')
    result.append(m);return result

def build_equipment():return [armor('crystal_carapace_light_armor','晶壳轻甲'),armor('brood_heavy_armor','母巢重甲',True),spear(),hammer(),crossbow(),staff()]+loot_models()
