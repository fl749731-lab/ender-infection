"""Combat silhouettes rebuilt from concept sheet 05, at native model density.

This module only produces editable models. Its optional CLI writes review assets
under build/concept-review/combat; publishing is handled by the pack exporter.
"""
from pathlib import Path
import json
import math

from concept_detail import ConceptModel as Model
from creatures import pair_eyes


def _path(m, name, points, widths, mat='bone', bone='body'):
    for i, (a, b) in enumerate(zip(points, points[1:])):
        m.beam(name, a, b, widths[min(i, len(widths) - 1)], mat=mat, bone=bone)


def _crystal_between(m, name, a, b, width, bone='body'):
    x, y, z = (b[i] - a[i] for i in range(3))
    rot = (math.degrees(math.atan2(z, math.hypot(x, y))), 0,
           math.degrees(math.atan2(-x, y)))
    m.crystal(name, a, width, math.sqrt(x*x+y*y+z*z), bone, rot)


def _ribs(m, center, width, height, depth, count=5, bone='body'):
    x, y, z = center
    for row in range(count):
        yy = y + height/2 - row*height/max(1, count-1)
        taper = 1 - .22*row/max(1, count-1)
        for s in (-1, 1):
            _path(m, 'exposed_rib', [(x+s*.8, yy, z+depth*.42),
                  (x+s*width*.44*taper, yy+.35, z+depth*.18),
                  (x+s*width*.47*taper, yy-.4, z-depth*.25),
                  (x+s*width*.16, yy-1.1, z-depth*.52)],
                  [1.0, 1.1, .85], 'bone', bone)


def _mask(m, center, width, height, depth, bone='head', eyes=True):
    x, y, z = center
    # Three uneven vertical plates leave a narrow dark central seam.
    m.centered('mask_underface', center, (width*.78, height*.88, depth*.65), 'black', bone)
    for s in (-1, 1):
        m.centered('mask_temporal_plate', (x+s*width*.29, y+.5, z+s*.04),
                   (width*.45, height*.82, depth), 'bone', bone,
                   rot=(0, 0, -s*7), pivot=(x+s*width*.29, y+.5, z+s*.04))
        _path(m, 'mask_cheek', [(x+s*width*.45, y+height*.1, z-depth*.25),
              (x+s*width*.35, y-height*.3, z-depth*.45),
              (x+s*width*.15, y-height*.58, z-depth*.55)], [1.15, .65], 'bone', bone)
    m.centered('mask_brow', (x, y+height*.42, z-depth*.13),
               (width*.75, 1.2, depth*.8), 'bone', bone)
    m.centered('mask_keel', (x, y-height*.2, z-depth*.45),
               (.95, height*.85, .85), 'bone', bone)
    if eyes:
        pair_eyes(m, (x, y+height*.12, z-depth*.54), width*.23, bone, .65)


def _toes(m, name, foot, width, bone, count=3, length=4):
    x, y, z = foot
    for i in range(count):
        xx = x+(i-(count-1)/2)*width/count
        _path(m, name, [(xx, y, z), (xx, y-.2, z-length*.6),
              (xx, max(.25, y-.5), z-length)], [width/count*.65, .55], 'bone', bone)


def _finish(m, stationary=False, floating=False, attack_overrides=None):
    idle = {'body': {'position': [(0,(0,0,0)), (1,(0,.24,0)), (2,(0,0,0))]}}
    walk = {'root': {'position': [(0,(0,0,0)), (.25,(0,.25,0)),
             (.5,(0,0,0)), (.75,(0,.25,0)), (1,(0,0,0))]}}
    attack = {'body': {'rotation': [(0,(0,0,0)), (.5,(-4,0,0)),
               (.8,(6,0,0)), (1.3,(0,0,0)), (2,(0,0,0))]}}
    for bone, (role, phase) in m.roles.items():
        if role == 'head':
            idle[bone] = {'rotation': [(0,(0,-1.5,0)), (1,(0,1.5,0)), (2,(0,-1.5,0))]}
        if role == 'sac':
            idle[bone] = {'scale': [(0,(1,1,1)), (1,(1.025,1.035,1.025)), (2,(1,1,1))]}
            attack[bone] = {'scale': [(0,(1,1,1)), (.5,(1.08,1.06,1.08)),
                              (.8,(.94,.95,.94)), (1.5,(1,1,1)), (2,(1,1,1))]}
        if role == 'jaw':
            # Blockbench animation X turns positive to open this forward jaw.
            attack[bone] = {'rotation': [(0,(0,0,0)), (.5,(28,0,0)),
                               (.8,(14,0,0)), (1.4,(0,0,0)), (2,(0,0,0))]}
        if role in ('leg','lower','arm','chain') and not stationary:
            amp = {'leg':16, 'lower':9, 'arm':9, 'chain':4}[role]
            walk[bone] = {'rotation': [(t, (math.cos(t*math.tau+phase)*amp,0,0)
                                if role != 'chain' else (0,math.cos(t*math.tau+phase)*amp,0))
                                for t in (0,.25,.5,.75,1)]}
        if role == 'arm':
            attack[bone] = {'rotation': [(0,(0,0,0)), (.5,(-35,0,0)),
                               (.8,(24,0,0)), (1.4,(0,0,0)), (2,(0,0,0))]}
    if stationary:
        walk = {'root': {'position': [(0,(0,0,0)), (1,(0,0,0))]}}
    if floating:
        idle['root'] = {'position': [(0,(0,.1,0)), (1,(0,.75,0)), (2,(0,.1,0))]}
        walk = {'root': {'position': [(0,(0,.1,0)), (.5,(0,.75,0)), (1,(0,.1,0))]}}
    if 'halo' in m.groups:
        idle['halo'] = {'rotation': [(0,(0,0,-4)), (1,(0,0,4)), (2,(0,0,-4))]}
        walk['halo'] = {'rotation': [(0,(0,0,-4)), (.5,(0,0,4)), (1,(0,0,-4))]}
        attack['halo'] = {'rotation': [(0,(0,0,0)), (.5,(0,0,25)), (.8,(0,0,90)), (2,(0,0,180))]}
    attack.update(attack_overrides or {})
    m.animation('idle',2,True,idle)
    m.animation('walk',1,True,walk)
    m.animation('attack',2,False,attack)
    m.animation('emerge',1.5,False,{'root':{'position':[(0,(0,-7,0)),(1,(0,.6,0)),(1.5,(0,0,0))]}})
    m.animation('death',1.5,False,{'root':{'rotation':[(0,(0,0,0)),(1.5,(0,0,8 if stationary else 70))],
                                                    'position':[(0,(0,0,0)),(1.5,(0,-2,0))]}})
    assert len(m.data['elements']) <= 1100, (m.key, len(m.data['elements']))
    return m


def sentinel():
    m = Model('crystal_shield_sentinel','晶盾卫士','monsters','格挡 / 回防')
    m.group('body',(0,21,1))
    m.beam('exposed_spine',(0,13,3),(0,31,3),1.8,mat='bone',bone='body')
    m.centered('deep_torso',(0,24,1.8),(5,11,3.5),'tissue','body')
    _ribs(m,(0,25,0),12,9,7,5)
    m.centered('pelvis',(0,14,1),(8,3,5),'shell','body')
    for s in (-1,1):
        m.plate('hip_flare',(s*4.4,14.5,.5),(4,5,4),'body',rot=(0,0,-s*23))
        m.limb('leg_'+str(s),(s*2.8,13,1),(s*3.8,7.4,-.6),(s*4.6,1.2,-1.2),2.9,'tissue',math.pi*(s<0))
        _toes(m,'toe',(s*4.6,1.1,-1.4),4.5,'leg_'+str(s)+'_lower',3,3.4)
        for i in range(3):
            m.plate('greave_plate',(s*4.1,6.7-i*1.5,-2.1-i*.12),(3.2,2.4,1.4),'leg_'+str(s)+'_lower',rot=(0,0,s*8))
        shoulder=(s*7.2,29.5,1);elbow=(s*9.3,22,-.4);hand=(s*9.7,17,-1.8)
        arm=m.group('arm_'+str(s),shoulder,'body','shield' if s==-1 else 'arm',s)
        m.beam('humerus',shoulder,elbow,2.2,mat='tissue',bone=arm,joint=True)
        fore=m.group('forearm_'+str(s),elbow,arm)
        m.beam('forearm',elbow,hand,2,mat='bone',bone=fore,joint=True)
        for i in range(3):m.plate('pauldron',(s*(7.1+i*.6),30.5-i*1.1,1+i*.16),(5.8-i*.6,2.6,6),arm,rot=(0,0,-s*21))
        for i in range(3):m.plate('vambrace',(s*9.6,21.5-i*1.5,-1.3-i*.1),(3,2.2,3),fore,rot=(0,0,s*8))
    m.group('head',(0,33.5,0),'body','head')
    _mask(m,(0,33.7,-.4),5.1,6.2,3.6)
    for s in (-1,1):
        _path(m,'crest',[(s*1.6,36,1),(s*2.7,39,1),(s*2.3,40.5,.3)],[1.2,.7],bone='head')
        _crystal_between(m,'collar_crystal',(s*4,30,2),(s*5.6,33.3,2),1.25)
    # Separate tapered segments make the broad shield outline pointed and jagged.
    shield=m.group('shield',(-10,22,-3.3),'forearm_-1')
    sections=[(3.5,2.6,-1),(7,4.9,0),(11,6.2,.2),(15,6.8,.4),(19,7.2,.5),(23,6.4,.2),(27,5.1,0),(31,3.8,-.5),(34,1.8,-.8)]
    for i,(y,w,shift) in enumerate(sections):
        w*=1.2
        x=-10+shift*1.2
        gap=min(y-sections[i-1][0] if i else 4,
                sections[i+1][0]-y if i+1<len(sections) else 4)
        m.centered('shield_dark_core',(x,y,-4.7),(w,gap-.08,2.2),'shell',shield)
        for s in (-1,1):
            m.plate('shield_bone_rim',(x+s*w*.49,y+.25,-5.9-i*.08+s*.03),(1.55,4.8,1.7),shield,rot=(0,0,s*(14 if i<3 else -9)))
    _path(m,'shield_crown_left',[(-12.4,31,-5.3),(-13.6,36,-5.3),(-11.2,39,-5.3)],[1.6,.8],bone=shield)
    _path(m,'shield_outer_prong',[(-13.6,22,-5.3),(-16,27,-5.5),(-15.4,30,-5.5)],[1.5,.65],bone=shield)
    _crystal_between(m,'shield_crystal',(-10,16,-6.2),(-10.5,26,-6.2),3.2,shield)
    _crystal_between(m,'shield_low_crystal',(-9.5,7,-6.2),(-9.5,12,-6.2),1.4,shield)
    _path(m,'sword_bone_guard',[(9.7,17,-2),(11,15,-4),(10.8,12,-5)],[2.2,1.8],bone='forearm_1')
    _crystal_between(m,'sword_blade',(10.8,13,-5),(12.5,2,-10),2.9,'forearm_1')
    return _finish(m)


def spitter():
    m=Model('rift_throat_spitter','裂喉喷吐者','monsters','远程喷吐 / 压制')
    m.group('body',(0,20,3))
    # Narrow lower chest and forward shoulder mass leave the twin sacs exposed.
    m.beveled('arched_back',(0,23,4),(10,13,10),'tissue','body')
    m.beam('curved_neck',(0,25,2),(0,31,-4),4.3,mat='tissue',bone='body')
    for s in (-1,1):
        m.limb('leg_'+str(s),(s*3.5,16,5),(s*6,9,9),(s*7,1.2,-1),2.7,'tissue',math.pi*(s<0))
        _toes(m,'talon',(s*7,1.2,-1.2),4.4,'leg_'+str(s)+'_lower',3,4.3)
        arm=m.group('arm_'+str(s),(s*5,25,1),'body','arm',s)
        _path(m,'thin_arm',[(s*5,25,1),(s*8.7,18,-1),(s*8.3,13,-4)],[1.9,1.3],'bone',arm)
        for i in range(3):_path(m,'claw',[(s*8.3+(i-1)*.8,13,-4),(s*9+(i-1)*.8,11,-5.5),(s*8.7+(i-1)*.8,10,-6.7)],[.6,.35],'bone',arm)
        sac=m.group('throat_sac_'+str(s),(s*3.7,29,-6),'body','sac')
        m.beam('sac_stalk',(s*3.7,29,-6),(s*4.8,23,-7.7),1.4,mat='tissue',bone=sac)
        m.sac('pendulous_throat_sac',(s*4.9,20.4,-7.8),(7.7,12.4,7.8),sac,'spore',False)
        for j in range(4):
            yy=16.5+j*2
            m.beam('sac_vein',(s*3.8,yy,-11.4),(s*4.4,yy+1.8,-11.4),.55,mat='tissue',bone=sac)
        # Purple sacs are tissue: only tiny mineral nodules emit.
        _crystal_between(m,'sac_nodule',(s*6.5,22,-10),(s*6.6,24.3,-10),.75,sac)
        for i in range(4):m.plate('back_carapace',(s*3.6,29-i*2.9,7),(5.1,3.6,3),'body',rot=(12,0,s*16))
    for j in range(5):
        z=7+j*.6;y=29-j*2.2
        _crystal_between(m,'dorsal_crystal',(0,y,z),(0,y+5.4-j*.5,z+3),1.8-j*.035)
    m.group('head',(0,31,-4),'body','head')
    m.centered('cranial_arch',(0,32,-6),(8.5,2.805,7),'bone','head')
    m.centered('upper_snout',(0,31.05,-11),(7.4,1.87,7),'bone','head')
    m.centered('black_palate',(0,29.9,-11.2),(6.4,.8,7),'black','head')
    m.centered('nasal_ridge',(0,33.1,-10.12),(2,1.9,9),'bone','head')
    pair_eyes(m,(0,32,-8.8),3.1,size=.9)
    jaw=m.group('jaw',(0,28.8,-4),'head','jaw')
    _path(m,'jaw_ramus',[(-3.8,28,-4),(-3.4,25,-11),(-2.6,25.3,-14)],[1.3,1.1],bone=jaw)
    _path(m,'jaw_ramus',[(3.8,28,-4),(3.4,25,-11),(2.6,25.3,-14)],[1.3,1.1],bone=jaw)
    m.centered('mandible_tip',(0,25.2,-13.8),(5.8,1.2,1.8),'bone',jaw)
    m.centered('mouth_cavity',(0,27.7,-5),(5.8,4.1,.8),'black','head')
    for s in (-1,1):
        for i in range(5):
            z=-6.7-i*1.6
            _path(m,'upper_tooth',[(s*3.1,30,z),(s*2.9,28.4-(i%2)*.5,z-.3)],[.7],bone='head')
            _path(m,'lower_tooth',[(s*2.8,25.7,z),(s*2.65,27.1+(i%2)*.4,z-.3)],[.65],bone=jaw)
        for i in range(3):m.plate('temple_fan',(s*(3.5+i*.5),33-i*.8,-4+i),(2.4,2.8,4),'head',rot=(0,0,-s*24))
    return _finish(m)


def ambusher():
    m=Model('leapblade_ambusher','跃刃伏击者','monsters','跳跃 / 镰刃伏击')
    m.group('body',(0,23,2))
    m.beam('slanted_spine',(0,16,6),(0,30,-.5),2.1,mat='bone',bone='body')
    m.beveled('thorax',(0,25,2),(6.4,11,5.2),'tissue','body')
    _ribs(m,(0,26,1.5),9,9,6,4)
    m.centered('pelvis',(0,16,6),(7.2,3.5,5),'shell','body')
    for s in (-1,1):
        m.limb('leg_'+str(s),(s*3,17,5),(s*10.5,12,9),(s*12,1.2,-4),2.5,'tissue',math.pi*(s<0))
        _toes(m,'foot_talon',(s*12,1.2,-4),4.4,'leg_'+str(s)+'_lower',3,4.6)
        shoulder=(s*4.5,29,0);elbow=(s*13,25,-2);wrist=(s*19,21,-5)
        arm=m.group('arm_'+str(s),shoulder,'body','arm',s)
        m.beam('spread_upper_arm',shoulder,elbow,2,mat='tissue',bone=arm,joint=True)
        fore=m.group('forearm_'+str(s),elbow,arm)
        m.beam('long_forearm',elbow,wrist,1.5,mat='bone',bone=fore,joint=True)
        for i in range(3):m.plate('scapular_fan',(s*(4.8+i*1.1),30+i*.6,1+i*.5),(3.6,3.5,5.4),arm,rot=(0,0,-s*(28+i*9)))
        blade=[wrist,(s*22,16,-7),(s*22.3,10,-10),(s*20.8,6,-13),(s*17,3.8,-16),(s*14,3.4,-17.4)]
        _path(m,'long_curved_crystal_scythe',blade,[3.5,3.2,2.5,1.7,.7],'crystal',fore)
        _path(m,'scythe_luminous_edge',[(x-s*.9,y+.1,z-.9) for x,y,z in blade[1:]], [.65,.6,.5,.3],'glow',fore)
        _path(m,'scythe_bone_back',[wrist,(s*22,16,-6.2),(s*22.6,10,-9.2)],[1.5,1.1],'bone',fore)
    m.group('head',(0,31,-2),'body','head')
    _mask(m,(0,31,-4),4.7,5.3,4.5)
    for s in (-1,1):_path(m,'head_horn',[(s*1.8,33,-2),(s*3,37,1),(s*4,39,2)],[1.4,.7],bone='head')
    points=[(0,19,6),(0,24,11),(0,30,16),(0,35,21),(0,38,26),(0,37.5,31),(0,34.5,36)]
    previous='body'
    for i,(a,b) in enumerate(zip(points,points[1:])):
        bone=m.group('tail_'+str(i),a,previous,'chain',i*.7)
        m.beam('tail_segment',a,b,2.3-i*.22,mat='tissue',bone=bone)
        m.centered('tail_vertebra',a,(2.7-i*.2,2,2.5-i*.15),'bone',bone)
        if i<5:_crystal_between(m,'tail_spur',(a[0],a[1]+.6,a[2]),(0,a[1]+3.5,a[2]+1),.85,bone)
        previous=bone
    # A thin vertical leaf blade, broad in side view, replaces the round spike.
    base=points[-1];tip=(0,30.5,47)
    cuts=(0,.16,.32,.48,.64,.79,.91,1)
    for i,(lo,hi) in enumerate(zip(cuts,cuts[1:])):
        a=tuple(base[j]+(tip[j]-base[j])*lo for j in range(3))
        b=tuple(base[j]+(tip[j]-base[j])*hi for j in range(3))
        m.beam('tail_blade',a,b,(1.4,1.3,1.15,1,.8,.6,.3)[i],
               depth=(5.8,5,4.2,3.3,2.4,1.4,.45)[i],
               mat='glow' if i==6 else 'crystal',bone=previous)
    overrides={}
    for s in (-1,1):overrides['arm_'+str(s)]={'rotation':[(0,(0,0,0)),(.5,(-18,-s*20,-s*12)),(.8,(16,s*31,s*8)),(1.4,(0,0,0)),(2,(0,0,0))]}
    return _finish(m,attack_overrides=overrides)


def turret():
    m=Model('rootspike_turret','根刺炮台','monsters','地刺封锁 / 驻地防守')
    m.group('body',(0,17,2))
    m.beam('central_stalk',(0,4,3),(0,24,3),3.2,mat='tissue',bone='body')
    for s in (-1,1):
        for zsign in (-1,1):
            name=f'anchor_{s}_{zsign}'
            root=(s*4,9,zsign*4);knee=(s*10,5,zsign*8);foot=(s*16,1,zsign*13)
            m.limb(name,root,knee,foot,2.3,'tissue',parent='body',role='anchor',toes=False)
            _path(m,'thin_root',[root,(s*9,2,zsign*7),(s*19,.4,zsign*8)],[1.1,.5],'membrane',name)
        _path(m,'tall_open_support',[(s*6,4,1),(s*8,13,2),(s*7,26,1),(s*4,31,-2)],[2.1,2.4,1.8],bone='body')
        for i in range(4):
            m.beam('hanging_fiber',(s*(3+i*.7),22,3),(s*(3+i*.7),9+i%2*2,3),.65,mat='membrane',bone='body')
    m.group('head',(0,24,0),'body','head')
    for i,x in enumerate((-8,0,8)):
        y=26+(3 if i==1 else 0);z=-1-(2 if i==1 else 0)
        m.beam('barrel_support',(x,14,4),(x,y,z),1.6,mat='bone',bone='head')
        # The three separate launch organs are held in thin skeletal cradles.
        m.beam('launch_socket',(x,y-3,z+3),(x,y+1,z-3),3.3,mat='shell',bone='head')
        _crystal_between(m,'launch_crystal',(x,y,z-1),(x*1.65,y+13,z-19),4.1,'head')
        for side in (-1,1):
            _path(m,'barrel_cradle',[(x+side*2.6,y-4,z+4),(x+side*3,y+2,z-2),(x+side*2.8,y+4,z-6)],[1.2,1.1],bone='head')
        m.centered('barrel_collar',(x,y+1,z-1),(5.7,1.2,4.5),'bone','head',rot=(-32,0,0),pivot=(x,y,z))
    sac=m.group('ammo_sac',(11,23,6),'body','sac')
    m.beam('sac_hanger',(6,27,5),(11,24,6),1.8,mat='bone',bone=sac)
    m.sac('side_ammo_sac',(11.8,20,6),(9.5,11.5,9),sac,'spore',True)
    for i in range(3):_crystal_between(m,'ammo_crystal',(11+(i-1)*2.4,19,1.9),(11+(i-1)*2.4,22,1.9),1.2,sac)
    for s in (-1,1):
        m.plate('basal_anchor',(s*5,3,1),(5,4,6),'body',rot=(0,0,s*20))
        _path(m,'root_rib',[(s*3,3,-3),(s*6,8,-4),(s*4,14,-2)],[1.5,1.3],bone='body')
    return _finish(m,stationary=True,attack_overrides={'head':{'rotation':[(0,(0,0,0)),(.5,(-9,0,0)),(.8,(5,0,0)),(1.5,(0,0,0)),(2,(0,0,0))]}})


def priest():
    m=Model('resonance_priest','共鸣祭司','monsters','强化集群 / 共鸣支援')
    m.group('body',(0,26,0))
    m.beam('spine',(0,18,1.5),(0,36,1.5),1.5,mat='bone',bone='body')
    m.centered('inner_torso',(0,28,.4),(4.2,10,3),'tissue','body')
    _ribs(m,(0,29,0),7.2,7,4.6,4)
    m.group('head',(0,39,0),'body','head')
    _mask(m,(0,39,-.8),4.4,9.5,3.1,eyes=False)
    m.centered('forehead_inset',(0,42,-2.5),(.5,1.3,.25),'black','head')
    for s in (-1,1):
        m.centered('mask_side_flange',(s*2.6,39.7,-.3),(1.7,2.4,2),'bone','head')
        arm=m.group('arm_'+str(s),(s*3.4,32,0),'body','arm',s)
        elbow=(s*8.4,26,-.3);hand=(s*12.5,29.5,-4.2)
        m.beam('upper_arm',(s*3.4,32,0),elbow,1.45,mat='bone',bone=arm,joint=True)
        fore=m.group('forearm_'+str(s),elbow,arm)
        m.beam('forearm',elbow,hand,1.1,mat='bone',bone=fore,joint=True)
        m.centered('palm',hand,(2.4,1.25,2.5),'bone',fore)
        for i in range(3):_path(m,'open_finger',[(hand[0]+(i-1)*.75,hand[1],hand[2]-1),(hand[0]+(i-1)*.9,hand[1]+2,hand[2]-1.9),(hand[0]+(i-1)*.8,hand[1]+2.6,hand[2]-1.6)],[.5,.35],'bone',fore)
        for i in range(2):m.plate('shoulder_mantle',(s*(3.3+i*.9),32.5-i,i*.17),(3.2,2.4,4),arm,rot=(0,0,-s*24))
        _crystal_between(m,'hand_focus',(s*14,33,-4),(s*14.4,36,-4),1.3,fore)
    # Overlapping long root blades, not a cylindrical robe or straight stick legs.
    for i in range(12):
        a=i*math.tau/12;x=math.cos(a);z=math.sin(a)
        bone=m.group('vestment_'+str(i),(x*3,23,z*2.2),'body','chain',a)
        length=17+(i%3)*2
        path=[(x*3,23,z*2.2),(x*5.4,16,z*4.2),(x*6.7,9,z*4.8),(x*3.4,23-length,z*3.4)]
        _path(m,'long_root_vestment',path,[1.8,1.55,.75],'bone' if i%3!=1 else 'membrane',bone)
        _path(m,'dark_root_underlay',[(x*2,22,z*2),(x*4.3,14,z*3.2),(x*3,3+(i%3),z*3)],[1.3,.65],'tissue',bone)
        if i%2==0:m.plate('vestment_shingle',(x*4.2,18,z*3.4),(2.4,5.4,1.6),bone,rot=(0,-math.degrees(a),-x*13))
    m.group('halo',(0,37,4.8),'body')
    # Discontinuous elongated arcs follow a vertical circle around the mask.
    for i in range(8):
        mid=math.radians(-65+i*41)
        points=[(math.sin(mid+d)*12.3,37+math.cos(mid+d)*12.3,4.8+depth)
                for d,depth in ((-.19,0),(0,.07),(.19,.24))]
        _path(m,'halo_crystal_arc',points,[1.5,1.5],'crystal','halo')
        _path(m,'halo_lit_edge',[(x*.96,37+(y-37)*.96,z-.75) for x,y,z in points],[.45,.45],'glow','halo')
        m.centered('halo_end_facet',points[0],(.8,1.1,1.2),'glow','halo')
    _crystal_between(m,'sternum_crystal',(0,25,-2.4),(0,29,-2.4),1.3)
    return _finish(m,floating=True)


def colossus():
    m=Model('siegebreaker_colossus','破城巨像','monsters','砸墙 / 震地')
    m.group('body',(0,27,5))
    m.beam('arched_spine',(0,17,10),(0,39,3),3,mat='bone',bone='body')
    m.beveled('shoulder_muscle',(0,33,5),(21,12,12),'tissue','body')
    m.beveled('narrow_abdomen',(0,22,6),(10,11,6),'tissue','body')
    _ribs(m,(0,31,3),23,14,14,6)
    m.centered('pelvis',(0,16,9),(12,4,8),'shell','body')
    for s in (-1,1):
        m.limb('leg_'+str(s),(s*5,16,9),(s*8,9,13),(s*9,1.3,5),3.7,'tissue',math.pi*(s<0))
        _toes(m,'hind_claw',(s*9,1.3,5),6,'leg_'+str(s)+'_lower',3,5)
        shoulder=(s*12,37,2);elbow=(s*18.5,23,-4);wrist=(s*21,8,-10)
        arm=m.group('arm_'+str(s),shoulder,'body','arm',s)
        m.beam('huge_humerus',shoulder,elbow,5.8,mat='tissue',bone=arm,joint=True)
        fore=m.group('forearm_'+str(s),elbow,arm)
        m.beam('weight_bearing_forearm',elbow,wrist,6.2,mat='shell',bone=fore,joint=True)
        for i in range(4):
            m.plate('layered_shoulder',(s*(12+i*1.1),38-i*1.4,2),(10.5-i*.9,3.8,12-i*.8),arm,rot=(0,0,-s*22))
        for i in range(4):
            m.plate('forearm_bone',(s*(19.3+i*.45),21-i*3.6,-5.3-i*1.2),(6.8,4.2,5.3),fore,rot=(0,0,-s*8))
        palm=m.group('hand_'+str(s),wrist,fore)
        m.beveled('palm',(s*21,6,-11),(8,6,7),'bone',palm)
        for i in range(4):
            xx=s*21+(i-1.5)*2.15
            _path(m,'separate_finger',[(xx,6,-12),(xx,4,-16-(i%2)),(xx,1.3,-16.5-(i%2))],[1.8,1.65],'bone',palm)
            m.centered('finger_knuckle',(xx,5.5,-14.2),(1.9,1.6,2),'shell',palm)
        _path(m,'opposed_thumb',[(s*17.3,7,-10),(s*15.7,4.3,-13),(s*16.8,2.5,-14)],[2.2,1.8],'bone',palm)
        _crystal_between(m,'shoulder_shard',(s*9.5,38,6),(s*11.4,44.5,7),2.6,arm)
        _crystal_between(m,'elbow_shard',(s*20,24,-2),(s*23,28,-1),1.6,fore)
    m.group('head',(0,35,-6),'body','head')
    _mask(m,(0,35,-8.6),8.5,6.8,5.7)
    m.plate('brow_armor',(0,38.5,-7.7),(8.4,2.2,7),'head',rot=(-14,0,0))
    for i in range(5):
        m.plate('back_lamella',(0,39-i*3,8+i*.7),(15-i*1.3,4,4),'body',rot=(15,0,0))
        if i<3:_crystal_between(m,'dorsal_crystal',(0,40-i*3,10+i*.7),(0,45-i*3,13+i*.7),2.2)
    for s in (-1,1):
        for i in range(3):m.plate('flank_plate',(s*(6.5-i*.4),24-i*3,5+i*.12),(3.8,3.6,6),'body',rot=(0,0,-s*14))
    slam={'body':{'rotation':[(0,(0,0,0)),(.5,(-8,0,0)),(.8,(0,0,0)),(1.4,(0,0,0)),(2,(0,0,0))]}}
    for s in (-1,1):
        slam['arm_'+str(s)]={'rotation':[(0,(0,0,0)),(.5,(-78,0,-s*6)),
                                          (.8,(0,0,0)),(1.3,(0,0,0)),(2,(0,0,0))]}
    return _finish(m,attack_overrides=slam)


def build_models():
    return [sentinel(),spitter(),ambusher(),turret(),priest(),colossus()]


def review(output):
    from PIL import Image, ImageDraw, ImageFont
    from modelkit import prepare_render
    from render_models import render
    from concept_materials import concept_palette
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    records=[];views=[];attack_views=[]
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',23)
    for model in build_models():
        with concept_palette():
            data,texture,record=model.save(output)
        records.append(record)
        for view,camera,frame in [('front',(1,.5,-1),None),('side',(1,.35,.22),None),
                                   ('attack',(1,.5,-1),(next(a for a in data['animations'] if a['name'].endswith('.attack')),.8))]:
            image=render(prepare_render(data,frame),texture,650,650,camera=camera,margin=28)
            base=Image.new('RGB',image.size,(32,32,39));base.paste(image,(0,0),image)
            ImageDraw.Draw(base).text((15,12),model.label+'  '+view,fill='#e9dfee',font=font)
            base.save(output/(model.key+'_'+view+'.png'))
            if view=='front':views.append(base)
            if view=='attack':attack_views.append(base)
        print(model.key,record['cubes'],record['bones'],record['atlas'],flush=True)
    sheet=Image.new('RGB',(1950,1300),(32,32,39))
    for i,view in enumerate(views):sheet.paste(view,((i%3)*650,(i//3)*650))
    sheet.save(output/'combat_contact_sheet.png')
    for i,view in enumerate(attack_views):sheet.paste(view,((i%3)*650,(i//3)*650))
    sheet.save(output/'combat_attack_sheet.png')
    (output/'manifest.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__=='__main__':
    review(Path(__file__).resolve().parents[3]/'ender_infection_colored/build/concept-review/combat')
