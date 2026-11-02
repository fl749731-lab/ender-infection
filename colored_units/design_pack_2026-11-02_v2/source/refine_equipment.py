import math

def refine_gear(m):
    key=m.key
    if m.category=='armor':
        heavy=key=='brood_heavy_armor'
        m.groups['reference_player']['visibility']=True
        for e in m.data['elements']:
            if e['name'].startswith('guide_'):e['visibility']=True
        for s in (-1,1):
            arm='arm_'+str(s);leg='leg_'+str(s)
            for i in range(3 if heavy else 2):
                m.plate('overlap_pauldron',(s*(6.1+i*.45),24-i*1.25,.2),(6.2-i*.4,2.4,7-i*.5),arm,rot=(0,0,-s*12))
            for y in (19,16):
                m.centered('bracer_outer',(s*8.4,y,0),(1,3.2,4.8),'shell',arm)
                m.centered('bracer_back',(s*6.5,y,2.4),(4.5,3.2,.9),'shell',arm)
            m.centered('gauntlet_palm',(s*6.5,12.6,-.1),(4.3,2.2,4.7),'darkmetal',arm)
            for j in range(3):m.centered('gauntlet_knuckle',(s*6.5+(j-1)*1.1,12.6,-2.65),(.8,1.4,.8),'bone',arm)
            for j in range(3):m.plate('leg_lamella',(s*2.3,9-j*2.6,-2.5),(4.3,2.7,1.4),leg)
            m.centered('heel_plate',(s*2.3,2.5,2.4),(4.5,3.9,1.1),'shell',leg)
            m.plate('temple_plate',(s*4.45,29.2,0),(1.5,5.8,6),'helmet',rot=(0,0,-s*6))
            m.centered('jaw_guard',(s*2.8,24.3,-3.5),(2.1,1.7,2.6),'shell','helmet')
            m.beam('shoulder_strap',(s*3,23,-2.6),(s*3,23,2.5),1,mat='darkmetal',bone='chestplate')
            if heavy:m.crystal('pauldron_crystal',(s*6.6,24.7,1),1.3,3.2,arm,rot=(0,0,-s*20))
        for j in range(4):m.centered('abdomen_overlap',(0,14.8+j*1.7,-3.1),(7.4+j*.2,1.2,.7),'bone','chestplate')
        m.centered('helmet_nose_guard',(0,28.5,-4.9),(1.2,4,1.4),'shell','helmet')
        m.plate('helmet_crest',(0,32.3,0),(3,1.5,7.3),'helmet')
        m.centered('heart_frame',(0,20.4,-3.9),(3.9,3.8,1),'bone','chestplate')
        m.crystal('heart_facet',(0,18.9,-4.8),2,4,'chestplate')
    elif key=='prism_spear':
        m.plate('spear_socket',(0,26,-.3),(3.3,4.5,2),'shaft')
        for s in (-1,1):m.beam('guard_prong',(s*.8,25,0),(s*3.5,28,0),.8,mat='bone',bone='shaft')
        for y in (3,7,11,15,19):m.centered('wrapped_grip',(0,y,0),(2.1,.6,2.1),'membrane','shaft')
    elif key=='earthsplitter_hammer':
        for s in (-1,1):
            m.plate('striker_lamella',(s*6.4,26,0),(4.7,9.2,7.4),'shaft',rot=(0,0,s*7))
            for y in (23,29):m.centered('head_brace',(s*5.6,y,-3.8),(5.2,1.1,.7),'darkmetal','shaft')
        m.centered('central_socket',(0,26,2.9),(4.2,7.4,2),'shell','shaft')
        m.crystal('hammer_focus',(0,24,-3.4),2.8,5,'shaft')
    elif key=='crystal_spike_crossbow':
        for s in (-1,1):
            m.plate('limb_housing',(s*7,6,-2),(5,2.5,4),'bow',rot=(0,-s*18,0))
            m.centered('receiver_side',(s*2.1,6,1),(1.1,4.3,9),'steel','body')
            for z in (-4,1,5):m.centered('rail_clamp',(s*1.4,8,z),(.65,1.7,1),'bone','body')
        m.centered('stock_butt',(0,5,10),(4.5,5,2),'pad','body')
        m.centered('trigger',(0,2.3,2),(1,2,1),'steel','body')
        m.beam('trigger_guard',(-1.2,3,1),(-1.2,0,4),.6,mat='darkmetal',bone='body')
    elif key=='resonance_staff':
        for s in (-1,1):
            m.crystal('staff_crown',(s*3,39,0),1.2,3,'shaft',rot=(0,0,-s*20))
            m.beam('head_binding',(0,27,0),(s*3,31,0),.8,mat='shell',bone='shaft')
        for y in (5,10,15):m.centered('grip_band',(0,y,0),(2.3,.7,2.3),'membrane','shaft')
    elif key=='infected_tissue_pickup':
        for j in range(6):
            x=-5+j*1.8;m.beam('tendon',(x,2,-2),(x+.6,4.5,2.4),.45,mat='membrane')
        for s in (-1,1):m.beam('frayed_edge',(s*5,2,0),(s*6.7,.6,-2),.6,mat='tissue')
    elif key=='mycelium_bundle':
        for i in range(4):
            a=i*math.tau/4;m.beam('fine_branch',(math.cos(a)*3,7,math.sin(a)*2),(math.cos(a)*6,9,math.sin(a)*5),.4,mat='skull')
    elif key=='chitin_plate_drop':
        for i in range(3):
            for s in (-1,1):m.centered('plate_chamfer',(s*(3.4-i*.4)+(i-1)*.7,1+i*1.6,.6*i),(1.4,1,5.5),'shell',rot=(0,i*9,s*12))
    elif key=='condensed_biomass_drop':
        for s in (-1,1):m.beam('binding_vein',(s*2,1,-1.8),(s*3,6,-2.1),.55,mat='shell')
        m.centered('bright_nucleus',(0,4,-3.95),(1.7,1.7,.4),'glow')
    elif key=='brood_core_drop':
        for i in range(8):
            a=i*math.tau/8;x,z=math.sin(a)*3.4,math.cos(a)*3.4
            m.centered('core_collar',(x,5,z),(2.1,2.3,1.4),'bone',rot=(0,-math.degrees(a),0),pivot=(x,5,z))
        m.centered('core_front_aperture',(0,5,-3.8),(1.7,4,.5),'glow')
    return m

def refine_lab(m):
    key=m.key;infected=key.endswith('_eroded');kind=key.removesuffix('_eroded').removesuffix('_intact')
    if kind=='lab_cabinet':
        for s in (-1,1):
            g='door_'+str(s)
            for y in (7,24):m.centered('cabinet_hinge',(s*6.8,y,-5.3),(1.1,2.2,1.1),'darkmetal',g)
            for y in (5,27):m.centered('door_brace',(s*3.5,y,-5.55),(5.6,.5,.4),'steel',g)
        for x in (-6,6):m.centered('cabinet_cap',(x,29.2,0),(2,1.1,10),'steel')
        m.centered('label_plate',(-3.5,6,-5.65),(3.5,1.7,.25),'white','door_-1')
    elif kind=='crystal_incubator':
        for sx in (-1,1):
            for sz in (-1,1):
                for y in (5,33):m.centered('bracket',(sx*7.5,y,sz*7.5),(2.6,2.2,2.6),'darkmetal')
        m.centered('control_box',(9.2,11,2),(2.6,6,5),'steel')
        for y in (9,12):m.centered('control_button',(10.6,y,1),(.3,.9,.9),'crystal')
        for i in range(3):
            g=m.group('bubble_'+str(i),(-3+i*2,11+i*5,1),'specimen')
            m.centered('bubble',(-3+i*2,11+i*5,1),(.6,.6,.6),'glow',g)
            m.animation('bubble_rise_'+str(i),2.4,True,{g:{'position':[(0,(0,0,0)),(1.2,(0,2,0)),(2.4,(0,0,0))]}})
        m.centered('inner_grate',(0,8.1,0),(9,.5,9),'shell')
        for x in (-3,0,3):m.centered('inner_rail',(x,8.5,0),(.45,.3,8.5),'steel')
    elif kind=='surgery_table':
        for x in (-12,12):
            for z in (-4,4):
                m.centered('caster_mount',(x,1,z),(1.7,2.1,1.9),'steel')
                m.centered('caster_wheel',(x,.6,z),(1.2,1.2,2.5),'darkmetal')
        m.beam('backrest_link',(-14,11,4),(-9,15,4),.7,mat='steel')
        for x in (-11,-7):m.centered('lamp_hinge',(x,30,3),(2,2,2),'darkmetal')
        m.centered('lamp_rim',(-5,29.8,2),(6.6,1.4,4.6),'white','lamp')
        m.centered('light_lens',(-5,28.9,2),(5,.25,3),'glass','lamp')
        m.centered('instrument_plate',(-4,5.9,0),(7,.35,4.5),'steel')
        for i in range(3):m.beam('tool',(-6+i*2,6.3,-1.4),(-5.6+i*2,6.3,1.4),.35,mat='bone')
        for x in (-6,4):m.centered('restraint_strap',(x,14.25,0),(1.1,.4,10.5),'darkmetal')
    elif kind=='equipment_bench':
        for x in (-8,-2):m.centered('monitor_button',(x,18.6,-1.4),(.7,.6,.4),'steel')
        m.beam('desk_cable',(-5,19,5),(0,11,5),.55,mat='darkmetal')
        m.beam('desk_cable_drop',(0,11,5),(3,5,4),.55,mat='darkmetal')
        for y in (18,20,22,24):m.centered('tower_vent',(13,y,1.95),(2.5,.35,.25),'darkmetal')
        for x in (-2,2):m.centered('stool_castor',(x,.7,-12),(1.2,1.2,1.8),'steel')
    elif kind in ('reinforced_door','service_hatch','observation_window'):
        width=14 if kind=='observation_window' else 6;height=30 if kind!='service_hatch' else 14
        for s in (-1,1):
            for y in (2,height):m.centered('fastener',(s*width,y,-2.6 if kind!='service_hatch' else -1.25),(.6,.6,.3),'darkmetal')
    if infected and kind in ('lab_cabinet','crystal_incubator','surgery_table'):
        base=(-5,8,-5.8) if kind=='lab_cabinet' else (-6,3,-8.8) if kind=='crystal_incubator' else (5,14,-3)
        for j in range(2):m.crystal('secondary_invasion',(base[0]+j*1.1,base[1],base[2]+j),.8,2.4+j,'invasion_growth',rot=(0,0,-12+j*18))
    return m
