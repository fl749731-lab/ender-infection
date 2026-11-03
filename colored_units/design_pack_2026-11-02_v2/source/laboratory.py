from modelkit import Model
import math

COMPONENTS={
 'white_wall_tile':'白色实验室墙砖','dark_wall_tile':'深灰墙砖','lab_brick_tile':'实验室砖墙','checker_floor':'棋盘地砖',
 'lab_slab':'实验室半砖','lab_stair':'实验室楼梯','vent_panel':'通风百叶板','service_hatch':'检修口',
 'reinforced_door':'加固隔离门','lab_cabinet':'实验储柜','crystal_incubator':'晶体培养舱','surgery_table':'手术台',
 'equipment_bench':'设备工作台','observation_window':'观察隔离窗'}

def frame(m,name,c,size,bar=1,mat='steel',bone='root'):
    x,y,z=c;w,h,d=size
    for s in (-1,1):
        m.centered(name+'_post',(x+s*(w-bar)/2,y,z),(bar,h,d),mat,bone)
        m.centered(name+'_cross',(x,y+s*(h-bar)/2,z),(w,bar,d),mat,bone)
def glass(m,name,c,size,bone='root'):m.centered(name,c,size,'glass',bone)
def erode(m,key):
    # Original localized living growth, placed along actual seams, not an image pasted on every face.
    paths={
      'white_wall_tile':[(-6,1,-8.15),(-4,5,-8.15),(0,6,-8.15),(1,10,-8.15),(4,12,-8.15),(5,16,-8.15)],
      'dark_wall_tile':[(-6,1,-8.15),(-2,4,-8.15),(-1,8,-8.15),(3,10,-8.15),(5,16,-8.15)],
      'lab_brick_tile':[(-7,2,-8.15),(-2,3,-8.15),(-1,7,-8.15),(4,8,-8.15),(6,14,-8.15)],
      'checker_floor':[(-7,16.16,-6),(-3,16.16,-3),(-2,16.16,1),(2,16.16,3),(6,16.16,4)],
      'lab_slab':[(-7,8.16,-6),(-3,8.16,-3),(-2,8.16,1),(2,8.16,3),(6,8.16,4)],
      'lab_stair':[(-5,1,-8.2),(-3,4,-8.2),(-2,8,-4.2),(1,9,-4.2),(2,12,.2),(5,16,4.2)],
      'vent_panel':[(-7,2,-.8),(-4,5,-.8),(-2,7,-.8),(1,9,-.8),(2,13,-.8),(5,16,-.8)],
      'service_hatch':[(-7,1,-1.2),(-5,5,-1.2),(-6,9,-1.2),(-4,13,-1.2),(-3,16,-1.2)],
      'reinforced_door':[(-7,1,-2.3),(-5,5,-2.3),(-6,10,-2.3),(-5,15,-2.3),(-7,21,-2.3)],
      'lab_cabinet':[(-7,2,-5.3),(-5,7,-5.3),(-6,13,-5.3),(-5,20,-5.3),(-3,27,-5.3)],
      'crystal_incubator':[(-7,2,-7.8),(-5,5,-7.8),(-6,12,-7.8),(-4,20,-7.8),(-5,29,-7.8),(-2,34,-7.8)],
      'surgery_table':[(-14,14.2,-5),(-8,14.2,-3),(-4,14.2,0),(2,14.2,1),(5,14.2,5),(12,14.2,5)],
      'equipment_bench':[(-15,16.2,-4),(-9,16.2,-2),(-4,16.2,1),(2,16.2,2),(6,16.2,5),(12,16.2,5)],
      'observation_window':[(-14,1,-2.2),(-11,6,-2.2),(-12,12,-2.2),(-9,18,-2.2),(-10,25,-2.2),(-7,31,-2.2)]}
    m.group('invasion_growth',(0,0,0));path=paths[key]
    for i,(a,b) in enumerate(zip(path,path[1:])):
        m.beam('seam_root',a,b,.75,mat='tissue',bone='invasion_growth')
        if i%2==0:
            q=(b[0]+2,b[1]+1,b[2]);m.beam('branch',b,q,.45,mat='membrane',bone='invasion_growth')
    base=path[1];base=(base[0],base[1],base[2]-.4)
    if key in ('checker_floor','lab_slab','lab_stair','surgery_table','equipment_bench'):
        for i in range(2):m.crystal('crystal_sprout',(base[0]+i*1.4,base[1]+.2,base[2]+i),1.1,2.5+i,'invasion_growth',rot=(0,0,-i*12))
    else:
        for i in range(2):m.crystal('crystal_sprout',(base[0]+i*1.3,base[1]-1,base[2]-.6),1,2.7+i,'invasion_growth',rot=(-10,0,-i*15))

def component(key,infected=False):
    suffix='_eroded' if infected else '_intact';m=Model(key+suffix,COMPONENTS[key]+(' · 侵蚀' if infected else ' · 完好'),'laboratory','独立组件 / 16× 纹素密度')
    if key in ('white_wall_tile','dark_wall_tile','lab_brick_tile'):
        m.box('tile',(-8,0,-8),(8,16,8),'white' if key=='white_wall_tile' else 'darkmetal' if key=='dark_wall_tile' else 'brick')
    elif key=='checker_floor':m.box('floor',(-8,0,-8),(8,16,8),'white',faces={'up':'checker','down':'brick','north':'steel','south':'steel','east':'steel','west':'steel'})
    elif key=='lab_slab':m.box('slab',(-8,0,-8),(8,8,8),'white',faces={'down':'brick'})
    elif key=='lab_stair':
        m.box('lower_step',(-8,0,-8),(8,8,8),'white');m.box('upper_step',(-8,8,0),(8,16,8),'white')
    elif key=='vent_panel':
        frame(m,'frame',(0,8,0),(16,16,1),1.2)
        m.centered('dark_cavity',(0,8,.45),(13,13,.3),'black')
        for i in range(6):m.centered('louver',(0,2.6+i*2.1,-.35),(13,.65,1.1),'steel',rot=(18,0,0),pivot=(0,2.6+i*2.1,-.35))
    elif key=='service_hatch':
        frame(m,'outer_frame',(0,8,0),(16,16,1.5),1.2);m.group('hatch_hinge',(-6.5,8,0))
        m.centered('hatch_door',(0,8,-.2),(12.5,12.5,1.2),'steel','hatch_hinge')
        m.centered('handle',(4,8,-1.2),(1,3,1),'darkmetal','hatch_hinge')
        for y in (4,12):m.centered('hinge',(-6.3,y,-.5),(1.2,2,1.5),'darkmetal')
        m.animation('open',.8,False,{'hatch_hinge':{'rotation':[(0,(0,0,0)),(.8,(0,-95,0))]}})
        m.animation('close',.8,False,{'hatch_hinge':{'rotation':[(0,(0,-95,0)),(.8,(0,0,0))]}})
    elif key=='reinforced_door':
        frame(m,'door_frame',(0,16,0),(16,32,4),1,'white');m.group('door_hinge',(-6.1,16,0))
        m.centered('lower_door',(0,10,0),(12,17,2.4),'darkmetal','door_hinge')
        m.centered('upper_door',(0,28,0),(12,5,2.4),'darkmetal','door_hinge')
        frame(m,'view_frame',(0,22,0),(12,8,2.4),1.5,'steel','door_hinge');glass(m,'view_glass',(0,22,-.2),(8.8,5,.3),'door_hinge')
        for x in (-3,0,3):m.centered('slit_bar',(x,22,-1.5),(.6,6,.7),'steel','door_hinge')
        for y in (7,26):m.centered('hinge',(-6.4,y,-1.6),(1.3,3,1.3),'steel')
        m.centered('door_handle',(4.2,14,-1.9),(.8,3.5,1.1),'steel','door_hinge')
        m.centered('warning_plate',(0,9,-1.4),(4,1,.4),'warning','door_hinge')
        m.animation('open',1,False,{'door_hinge':{'rotation':[(0,(0,0,0)),(1,(0,-95,0))]}})
        m.animation('close',1,False,{'door_hinge':{'rotation':[(0,(0,-95,0)),(1,(0,0,0))]}})
    elif key=='lab_cabinet':
        m.centered('back',(0,16,4.4),(16,25,1.2),'steel')
        for s in (-1,1):
            m.centered('side',(s*7.5,16,0),(1,25,9.5),'white')
            for z in (-3.4,3.4):m.centered('foot',(s*6.3,1.5,z),(1.5,3,1.5),'darkmetal')
            hinge=m.group('door_'+str(s),(s*7,16,-4.9));frame(m,'cabinet_door',(s*3.5,16,-4.9),(6.7,23,1),.7,'steel',hinge)
            glass(m,'door_glass',(s*3.5,16,-4.9),(5.2,21,.25),hinge)
            m.centered('handle',(s*.8,15,-5.7),(.5,3,.9),'darkmetal',hinge)
            m.animation('open_'+str(s),1,False,{hinge:{'rotation':[(0,(0,0,0)),(1,(0,-s*95,0))]}})
        for y in (3.5,10,17,23.5,29):m.centered('shelf',(0,y,0),(15.7,.8,9),'steel')
        for x,y,z in ((-4,12,0),(3,12,0),(-3,19,0),(3,25,0)):
            m.centered('sample_jar',(x,y,z),(2.4,3,2.4),'glass');m.centered('sample_lid',(x,y+1.6,z),(2.7,.5,2.7),'steel');m.centered('jar_content',(x,y-.3,z),(1.3,1.5,1.3),'liquid')
    elif key=='crystal_incubator':
        m.centered('base',(0,3,0),(17,6,17),'white');m.centered('base_trim',(0,6.3,0),(17.8,1,17.8),'steel')
        m.centered('lid',(0,33,0),(17,4,17),'steel');m.centered('lid_cap',(0,35.5,0),(12,1.5,12),'darkmetal')
        for sx in (-1,1):
            for sz in (-1,1):m.centered('corner_post',(sx*7.4,19,sz*7.4),(1.2,25,1.2),'steel')
            glass(m,'side_glass',(sx*7.45,19,0),(.25,24,13.5));glass(m,'front_glass',(0,19,sx*7.45),(13.5,24,.25))
        m.group('specimen',(0,16,0),role='sac');m.centered('specimen_tray',(0,7.5,0),(10,1,10),'darkmetal','specimen');m.crystal('specimen',(0,8,0),4.5,13,'specimen')
        for i in range(4):m.centered('indicator',(8.1,12+i*4,-3),(.5,1.5,1.5),'glow')
        m.centered('control',(8.1,9,2),(.8,3,3),'darkmetal');m.animation('pulse',2,True,{'specimen':{'scale':[(0,(1,1,1)),(1,(1.04,1.04,1.04)),(2,(1,1,1))]}})
    elif key=='surgery_table':
        m.centered('table_frame',(0,11.5,0),(32,2,12),'steel')
        for x in (-12,12):
            for z in (-4,4):m.centered('table_leg',(x,5,z),(1.5,10,1.5),'steel');m.centered('table_foot',(x,.6,z),(2.5,1.2,3),'darkmetal')
        m.centered('lower_shelf',(0,4,0),(27,1,9),'steel');m.centered('instrument_tray',(-4,5.2,0),(8,1,5),'white')
        m.centered('mattress',(3,13.2,0),(23,2,10),'pad');m.centered('headrest',(-12,14.3,0),(7,2,10),'pad',rot=(0,0,17),pivot=(-8.5,14,0))
        m.beam('lamp_post',(-13,11,5),(-13,30,5),1.2,mat='steel');m.beam('lamp_arm',(-13,30,5),(-5,30,2),1,mat='steel')
        m.group('lamp',(-5,30,2));m.centered('lamp_head',(-5,30,2),(6,1.5,4),'steel','lamp');m.centered('lamp_panel',(-5,29.1,2),(4.5,.3,2.5),'white','lamp')
    elif key=='equipment_bench':
        m.centered('countertop',(0,15,0),(34,2,14),'white');m.centered('rear_panel',(0,9,5.5),(31,10,1),'steel')
        for s in (-1,1):
            m.centered('drawer_body',(s*11,8,0),(9,12,11),'steel')
            for y in (4,8,12):m.centered('drawer_front',(s*11,y,-5.8),(8,3.1,.6),'white');m.centered('drawer_handle',(s*11,y,-6.4),(3,.45,.6),'darkmetal')
            for z in (-4,4):m.centered('foot',(s*13,1,z),(1.2,2,1.2),'darkmetal')
        m.centered('monitor_stand',(-5,17,2),(4,2,4),'darkmetal');m.centered('crt_case',(-5,22,2),(10,9,6),'darkmetal');m.centered('crt_face',(-5,22,-1.1),(8,6,.3),'monitor')
        m.centered('keyboard',(-4,16.3,-3),(10,.7,3.5),'pad')
        for row in range(2):
            for col in range(5):m.centered('key',(-8+col*1.7,16.8,-3.8+row*1.3),(1.1,.25,.7),'steel')
        for x in (6,11):m.centered('sample_bottle',(x,19,1),(2,5,2),'glass');m.centered('bottle_fill',(x,17.8,1),(1.5,2.2,1.5),'liquid');m.centered('bottle_cap',(x,21.7,1),(2.4,.7,2.4),'steel')
        m.centered('tower',(13,21,4),(4,10,4),'steel')
        for y in (19,22,25):m.centered('status',(13,y,1.8),(1,.5,.4),'crystal')
        m.centered('stool_seat',(0,8,-12),(8,1.5,7),'pad');m.centered('stool_stem',(0,4,-12),(1,7,1),'steel')
        for s in (-1,1):m.centered('stool_feet',(s*2,1,-12),(5,1,1),'darkmetal')
    elif key=='observation_window':
        for s in (-1,1):m.centered('wall_post',(s*14,16,0),(4,32,5),'white')
        for y in (2,30):m.centered('wall_rail',(0,y,0),(32,4,5),'white')
        frame(m,'window_frame',(0,16,0),(25,25,3),1,'steel');glass(m,'window_pane',(-3.3,16,-.2),(15.5,22,.3))
        m.centered('separator',(5,16,0),(1,23,3),'steel')
        for x in (7,10):m.centered('isolation_bar',(x,16,0),(.7,23,.9),'darkmetal')
    if infected:
        if key=='crystal_incubator':
            m.remove(lambda e:e['name'].startswith('front_glass') and e['from'][2]<0)
            for x,y,w,h in ((-5,15,3,17),(4.8,26,3.5,9),(-2,30,3,3)):
                glass(m,'broken_glass',(x,y,-7.45),(w,h,.22))
            m.crystal('escaping_cluster',(-6,2,-9),2.6,8,rot=(-7,0,15))
        elif key=='lab_cabinet':
            m.groups['door_1']['rotation']=[0,-55,0]
            m.crystal('shelf_growth',(3,17,0),1.3,5)
        elif key=='observation_window':
            m.remove(lambda e:e['name'].startswith('window_pane'))
            for x,y,w,h in ((-9,20,3.2,14),(-3,26,8,3),(2,7,3,5)):
                glass(m,'broken_pane',(x,y,-.2),(w,h,.25))
        elif key=='surgery_table':
            m.remove(lambda e:e['name'].startswith('mattress'))
            m.centered('torn_pad_left',(-3,13.2,0),(9,2,10),'pad')
            m.centered('torn_pad_right',(10,13.2,.4),(9,2,9.2),'pad',rot=(0,0,-3),pivot=(10,13.2,.4))
            m.centered('exposed_padding',(3.2,12.8,0),(3.6,1.3,8.5),'tissue')
        elif key=='equipment_bench':
            for e in m.data['elements']:
                if e['name'].startswith('crt_face'):
                    for f in e['faces'].values():
                        _,w,h,v=f['_tile'];f['_tile']=m.atlas.get('rune',w,h,v)
        elif key=='reinforced_door':m.groups['door_hinge']['rotation']=[0,-12,0]
        elif key=='service_hatch':m.groups['hatch_hinge']['rotation']=[0,-30,0]
        erode(m,key)
    return m

def build_components():return [component(k,infected) for k in COMPONENTS for infected in (False,True)]

def complex_scene(parts,cutaway=True):
    key='laboratory_complex_cutaway' if cutaway else 'laboratory_complex_complete'
    m=Model(key,'末影实验室 · '+('四层剖视布局' if cutaway else '四层完整建筑'),'scenes','三层地下设施 + 地表入口层；整体分组可编辑')
    w,d=256,224;step=80
    for floor in range(4):
        y=floor*step;fg=m.group('level_'+str(floor+1), (0,y,0))
        if floor==0:m.box('floor_slab',(-w,y-4,-d),(w,y,d),'brick',fg,faces={'up':'checker'})
        else:
            for xa,xb,za,zb in ((-w,-236,-d,d),(-190,w,-d,d),(-236,-190,-d,62),(-236,-190,198,d)):
                m.box('floor_slab_with_stair_opening',(xa,y-4,za),(xb,y,zb),'brick',fg,faces={'up':'checker'})
        rear=m.group('rear_and_west_walls_'+str(floor),(0,y,0),fg)
        front=m.group('front_and_east_walls_'+str(floor),(0,y,0),fg,visible=not cutaway)
        # Back/west kept, front/east can be hidden as a coherent cutaway.
        m.box('rear_wall',(-w,y,d-4),(w,y+74,d),'white',rear)
        m.box('west_wall',(-w,y,-d),(-w+4,y+74,d),'white',rear)
        m.box('east_wall',(w-4,y,-d),(w,y+74,d),'white',front)
        for x1,x2 in ((-w,-20),(20,w)):m.box('front_wall',(x1,y,-d),(x2,y+74,-d+4),'white',front)
        m.box('front_lintel',(-20,y+36,-d),(20,y+74,-d+4),'white',front)
        # The central corridor is 4 blocks wide; perpendicular corridors connect six rooms per level.
        walls=m.group('room_partitions_'+str(floor),(0,y,0),fg)
        for s in (-1,1):
            x=s*40
            for za,zb in ((-220,-194),(-162,-146),(-114,-44),(-12,4),(36,106),(138,154),(186,220)):
                m.box('corridor_wall',(x-2,y,za),(x+2,y+70,zb),'white',walls)
            for center in (-150,0,150):
                door_z=center-28;window_z=center+20
                m.box('door_lintel',(x-2,y+32,door_z-16),(x+2,y+70,door_z+16),'white',walls)
                m.box('window_sill',(x-2,y,window_z-16),(x+2,y+25,window_z+16),'white',walls)
                m.box('window_lintel',(x-2,y+57,window_z-16),(x+2,y+70,window_z+16),'white',walls)
            for zz in (-64,64):
                xa,xb=(-252,-42) if s<0 else (42,252)
                m.box('room_divider',(xa,y,zz-2),(xb,y+70,zz+2),'white',walls)
            for zz in (-178,-28,122):
                for n in (-1,1):
                    door=parts['reinforced_door_intact'];m.append(door,(x,y,zz+n*8),scale=1,rot_y=90,parent=fg,prefix=f'door_{floor}_{s}_{zz}_{n}')
            # Six readable functional rooms; props vary by floor.
            for room,z in enumerate((-150,0,150)):
                x=s*145;infected=(floor==0 and room!=0)
                if floor==0:
                    selected='crystal_incubator';positions=[(x-30,z-25),(x+30,z+25)]
                elif floor==1:
                    selected='surgery_table' if room==0 else 'lab_cabinet' if room==1 else 'crystal_incubator';positions=[(x,z)]
                elif floor==2:
                    selected='equipment_bench' if room!=1 else 'lab_cabinet';positions=[(x,z)]
                else:
                    selected='equipment_bench' if room==0 else 'lab_cabinet';positions=[(x,z)]
                for n,(px,pz) in enumerate(positions):
                    src=parts[selected+('_eroded' if infected else '_intact')];m.append(src,(px,y,pz),parent=fg,prefix=f'{selected}_{floor}_{s}_{room}_{n}')
                for n,dz in enumerate((-34,34)):
                    m.append(parts['lab_cabinet'+('_eroded' if infected else '_intact')],(s*218,y,z+dz),rot_y=s*90,parent=fg,prefix=f'storage_{floor}_{s}_{room}_{n}')
                m.append(parts['equipment_bench'+('_eroded' if infected else '_intact')],(s*110,y,z+42),parent=fg,prefix=f'room_console_{floor}_{s}_{room}')
                if floor==1:
                    for n,dz in enumerate((-28,28)):
                        m.append(parts['surgery_table_intact'],(s*180,y,z+dz),parent=fg,prefix=f'ward_table_{s}_{room}_{n}')
                if floor==0:
                    for n,dx in enumerate((-25,25)):
                        m.append(parts['crystal_incubator'+('_eroded' if infected else '_intact')],(x+dx,y,z-45),parent=fg,prefix=f'culture_row_{s}_{room}_{n}')
                    for dx in (-24,24):
                        for k in range(6):m.centered('containment_bar',(x+dx,y+17,z-28+k*7),(.8,34,.8),'darkmetal',fg)
                # Equipment pipes and signs add functional wall detail without closing circulation.
                m.beam('wall_conduit',(s*244,y+52,z-45),(s*244,y+52,z+45),1.1,mat='steel',bone=fg)
                m.centered('room_sign',(s*44,y+49,z-28),(1,5,12),'darkmetal',fg)
                m.centered('door_indicator',(s*44.6,y+49,z-28),(.3,1.5,4),'warning' if floor==0 else 'crystal',fg)
                # Window and vent insert assemblies stand inside the room, not painted on the wall.
                m.append(parts['observation_window'+('_eroded' if infected else '_intact')],(s*40,y+25,z+20),rot_y=90,parent=fg,prefix=f'window_{floor}_{s}_{room}')
                m.append(parts['vent_panel_intact'],(s*80,y+36,z+50),parent=fg,prefix=f'vent_{floor}_{s}_{room}')
                for dz in (-35,35):m.centered('ceiling_light',(x,y+71,z+dz),(24,1,8),'white',fg)
        # Continuous staircase along the rear west corner. 10 half-block rises = 5 blocks per level.
        if floor<3:
            stair=m.group('stair_to_next_level_'+str(floor),(0,y,0),fg)
            for i in range(10):
                m.box('stair_step',(-230,y+i*8,180-i*12),(-194,y+(i+1)*8,194-i*12),'steel',stair)
                m.beam('handrail',(-192,y+22+i*8,186-i*12),(-192,y+30+i*8,174-i*12),.8,mat='steel',bone=stair)
    roof=m.group('roof_and_surface_pavilions',(0,320,0),visible=not cutaway)
    m.box('roof',(-w,316,-d),(w,320,d),'brick',roof,faces={'up':'white'})
    # A surface entry vestibule and ventilation plant establish an identifiable above-ground silhouette.
    m.box('entry_floor',(-64,320,-96),(64,324,32),'checker',roof)
    for s in (-1,1):m.box('entry_side',(s*61-3,324,-96),(s*61+3,372,32),'white',roof)
    m.box('entry_back',(-64,324,28),(64,372,32),'white',roof);m.box('entry_lintel',(-64,356,-96),(64,372,-92),'steel',roof)
    m.box('entry_roof',(-68,372,-100),(68,376,36),'steel',roof)
    for x in (-36,36):m.append(parts['observation_window_intact'],(x,326,-94),parent=roof,prefix='surface_window_'+str(x))
    for i,x in enumerate((-110,120)):
        m.centered('vent_duct',(x,340,120),(28,40,28),'steel',roof)
        m.append(parts['vent_panel_intact'],(x,353,105),parent=roof,prefix='surface_vent_'+str(i))
        m.centered('vent_cap',(x,363,120),(36,6,36),'darkmetal',roof)
    return m
