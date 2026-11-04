"""Buildable room interiors using the mod's existing block registry.

Coordinates are local block cells, not decorative icon positions. Automatic
multiblock cells are recorded separately from the anchor item to place by hand.
"""
from collections import Counter,deque
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'previews/central_research_institute_2026-11-04'
OUT=ROOT/'previews/central_research_institute_interiors_v01'
LAYOUT=json.loads((BASE/'layout.json').read_text('utf-8'))
NS='ender_infection:'
DETAILS={'lab_monitor','lab_sample_rack','lab_shelf','lab_pipe','lab_light_panel','lab_chair','lab_sink','lab_storage_crate','lab_freezer','lab_floor_grate','lab_access_ladder','lab_rolling_cart','lab_microscope','lab_centrifuge','lab_cooling_pump','lab_task_lamp'}
FIXTURES={'lab_cabinet':(1,2),'incubator':(1,3),'surgery_table':(3,2),'lab_workbench':(3,2),'lab_window':(3,2),'lab_door':(1,2),'lab_hatch':(1,1),'lab_vent':(1,1),'lab_glass_cabinet':(1,2),'lab_cryo_cabinet':(1,2),'lab_server_rack':(1,2),'lab_control_console':(3,2),'lab_privacy_curtain':(1,2)}
STORAGE_FIXTURES={'lab_cabinet','lab_workbench','lab_glass_cabinet','lab_cryo_cabinet','lab_server_rack','lab_control_console'}
STORAGE_DETAILS={'lab_freezer','lab_storage_crate','lab_shelf','lab_rolling_cart','lab_sink'}
FACES={'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
FACEORDER=list(FACES)

PALETTES={
 'G1':dict(floor='lab_tile_light',border='lab_tile_dark',wall='minecraft:white_concrete',stripe='lab_wall_band_cyan',ceiling='lab_tile_light',accent='minecraft:cyan_concrete',note='白色公共区；深灰收边，青色入口导向'),
 'G2':dict(floor='lab_checker',border='lab_tile_dark',wall='minecraft:white_concrete',stripe='lab_wall_band_light_blue',ceiling='lab_tile_light',accent='minecraft:light_blue_concrete',note='棋盘科研地面；白墙，浅蓝实验分区'),
 'G3':dict(floor='lab_tile_light',border='lab_tile',wall='minecraft:white_concrete',stripe='lab_wall_band_green',ceiling='lab_tile_light',accent='minecraft:green_concrete',note='顶层白灰色；绿色温室与生活区导向'),
 'B1':dict(floor='lab_tile_dark',border='minecraft:polished_andesite',wall='minecraft:smooth_quartz',stripe='lab_wall_band_yellow',ceiling='lab_tile_dark',accent='minecraft:yellow_concrete',note='深灰机房；黄色设备安全边线'),
 'B2':dict(floor='lab_tile_light',border='lab_tile_dark',wall='minecraft:white_concrete',stripe='lab_wall_band_purple',ceiling='lab_tile_light',accent='minecraft:purple_concrete',note='白色收容区；紫色区划，事故房局部侵蚀'),
 'B3':dict(floor='lab_tile_dark',border='minecraft:polished_deepslate',wall='lab_tile_dark',stripe='lab_wall_band_purple',ceiling='lab_tile_dark',accent='minecraft:purple_concrete',note='深色核心区；紫晶发光，保留环形通路'),
}


def full(key):return key if ':' in key else NS+key


class Module:
    def __init__(self,name,w=8,d=6):self.name=name;self.w=w;self.d=d;self.blocks=[]
    def p(self,u,v,key,h=1,face='north',**states):
        base=key.replace('_eroded','');state=dict(states)
        if base in DETAILS or base in FIXTURES or key in ('minecraft:chest','minecraft:barrel','minecraft:furnace','minecraft:blast_furnace','minecraft:smoker','minecraft:lectern'):state['facing']=face
        if base in FIXTURES:state['open']='false'
        if key=='minecraft:grindstone':state.update(face='floor',facing=face)
        self.blocks.append(dict(u=u,v=v,h=h,block=full(key),state=state))
    def row(self,points,key,h=1,face='north'):
        for u,v in points:self.p(u,v,key,h,face)
    def stack(self,u,v,key,height=2,face='north'):
        for h in range(1,height+1):self.p(u,v,key,h,face)
    def counter(self,u,v,sample=False,monitor=False,face='south'):
        self.p(u,v,'lab_freezer',face=face)
        if sample:self.p(u,v,'lab_sample_rack',2,face)
        if monitor:self.p(u,v,'lab_monitor',2,face)
    def bed(self,u,v,color='white'):
        self.p(u,v,'minecraft:'+color+'_bed',face='north',part='foot',occupied='false')
        self.blocks[-1]['state']['facing']='north'
        self.p(u,v-1,'minecraft:'+color+'_bed',face='north',part='head',occupied='false')
        self.blocks[-1]['state']['facing']='north';self.blocks[-1]['manual_item']=False


def module(kind):
    labels={
     'surveillance':'三屏监控席','access':'双道安检模块','reception':'L形接待台','medical':'双床观察位','surgery':'手术与清洗位',
     'lockers':'四组更衣柜','decon':'消毒气闸与排水','pallets':'堆垛与封装货位','racks':'双排器材货架','receiving':'样本入库工作位',
     'waiting':'等候座椅与绿植','display':'样本展示柜','directory':'导览与查阅终端','botany':'植物样本种植槽','microscopy':'双席显微工作区',
     'crystal':'玻璃晶体分析箱','culture':'组织培养样本柜','culture_pods':'三座紫晶培养舱','reagents':'药剂配制与储柜','office':'双席研究办公位','library':'双排档案书架',
     'reading':'阅览与记录桌','wetbench':'湿法分析长台','radio':'通讯与值班席','control':'U形控制席','briefing':'小型简报桌',
     'bunks':'四床员工卧铺','kitchen':'餐厨后勤台','dining':'双列用餐座位','foodstore':'食物与餐具储存','greenhouse':'灌溉种植槽',
     'fungus':'菌体种植槽','switchgear':'配电柜列','generator':'应急动力机组','battery':'蓄能柜列','servers':'双排服务器柜',
     'pump':'冷却泵组','tank':'玻璃冷却水箱','filters':'空气过滤柜','ducts':'风道与维护阀组','tools':'工具与备件架',
     'packing':'器材拆装工作位','repair':'铁砧与修理台','freezers':'低温样本冷柜','containment':'玻璃收容隔间','specimens':'封装样本架',
     'damaged':'破损培养舱事故点','alarm':'报警与应急终端','safe':'核心档案保险柜','powercrystal':'晶体供能装置','infected':'受侵蚀隔离箱',
    }
    m=Module(labels[kind])
    if kind in ('surveillance','radio','control','alarm'):
        for u in (2,5):m.p(u,1,'lab_control_console',face='south');m.p(u,3,'lab_chair',face='north')
        if kind=='control':
            m.p(0,4,'lab_server_rack',face='east');m.p(7,4,'lab_server_rack',face='west')
            m.p(3,5,'lab_rolling_cart');m.p(4,5,'lab_rolling_cart')
        elif kind=='radio':
            m.p(7,4,'lab_server_rack',face='west');m.p(0,4,'lab_glass_cabinet',face='east')
            m.counter(3,5,sample=True);m.p(5,5,'lab_rolling_cart')
        elif kind=='alarm':
            m.p(7,0,'minecraft:redstone_lamp',2,lit='true');m.p(7,1,'minecraft:redstone_block',2);m.p(0,5,'lab_storage_crate');m.p(6,5,'lab_glass_cabinet')
        else:m.p(7,5,'lab_glass_cabinet');m.p(0,5,'lab_server_rack');m.p(3,5,'lab_rolling_cart')
    elif kind=='access':
        for u in (1,6):
            for v in range(6):
                if v==2:m.p(u,v,'lab_door',face='east')
                else:
                    for h in (1,2,3):m.p(u,v,'minecraft:light_gray_stained_glass',h)
        for u in (3,4):m.p(u,2,'lab_floor_grate',0)
        m.counter(3,5,monitor=True);m.p(5,5,'lab_rolling_cart')
    elif kind=='reception':
        for u in range(1,7):m.counter(u,1)
        for v in (2,3):m.counter(6,v)
        for u in (2,5):m.p(u,1,'lab_monitor',2,'south');m.p(u,3,'lab_chair',face='north')
        m.p(0,5,'lab_glass_cabinet');m.p(7,5,'lab_rolling_cart')
        m.p(3,1,'lab_task_lamp',2,'south');m.p(4,1,'minecraft:potted_fern',2)
    elif kind=='medical':
        for u in (1,5):m.bed(u,3);m.counter(u+1,3,monitor=True,face='north')
        m.p(0,0,'lab_cryo_cabinet',face='south');m.p(7,0,'lab_sink',face='south')
        for u in (0,4):m.p(u,4,'lab_privacy_curtain',face='east')
        m.p(7,5,'lab_rolling_cart');m.counter(3,0);m.p(3,0,'lab_task_lamp',2)
    elif kind=='surgery':
        m.p(3,3,'surgery_table');m.p(0,0,'lab_glass_cabinet',face='south');m.p(7,0,'lab_sink',face='south')
        m.counter(6,5,sample=True);m.counter(2,0,monitor=True);m.p(0,5,'lab_rolling_cart')
    elif kind=='lockers':
        for u in (0,2,4,6):m.p(u,0,'lab_cabinet',face='south')
        for u in (1,3,5):m.p(u,4,'lab_chair',face='north')
    elif kind=='decon':
        for u in (1,6):
            for v in range(6):
                if v==2:m.p(u,v,'lab_door',face='east')
                else:
                    for h in (1,2,3):m.p(u,v,'minecraft:light_blue_stained_glass',h)
        for u in (3,4):
            for v in (2,3):m.p(u,v,'lab_floor_grate',0)
        for u in (3,5):m.p(u,5,'lab_sink')
    elif kind=='pallets':
        for u in (0,1,2):
            for v in (0,1):m.stack(u,v,'lab_storage_crate',2)
        for u in (5,6):
            for v in (3,4):m.p(u,v,'lab_storage_crate',face='north')
        for u in range(4):m.p(u,2,'minecraft:yellow_concrete',0)
    elif kind in ('racks','library','servers','freezers','foodstore','safe'):
        if kind=='library':
            for u in (0,3,6):
                for v in range(5):m.stack(u,v,'minecraft:bookshelf',3)
            m.p(7,5,'lab_cabinet')
        elif kind=='racks':
            for v in (0,5):
                for u in range(7):m.stack(u,v,'lab_shelf',2,'south' if v==0 else 'north')
        else:
            key={'servers':'lab_server_rack','freezers':'lab_cryo_cabinet','foodstore':'lab_cryo_cabinet','safe':'lab_cabinet'}[kind]
            for v in (0,5):
                for u in (0,2,4,6):m.p(u,v,key,face='south' if v==0 else 'north')
            if kind=='freezers':m.p(7,2,'lab_rolling_cart');m.p(7,2,'lab_sample_rack',2)
            if kind=='safe':m.p(7,2,'lab_glass_cabinet',face='west')
    elif kind in ('receiving','packing','repair'):
        for u in (1,5):m.p(u,1,'lab_workbench',face='south')
        m.p(1,3,'lab_chair',face='north');m.p(5,3,'lab_chair',face='north')
        if kind=='receiving':m.counter(7,5,sample=True);m.p(0,5,'lab_rolling_cart')
        elif kind=='repair':m.p(7,5,'minecraft:anvil',facing='north');m.p(0,5,'minecraft:grindstone',face='north')
        else:m.stack(7,5,'lab_storage_crate',2);m.p(0,5,'lab_shelf')
    elif kind=='waiting':
        for v in (1,4):
            for u in (1,2,4,5):m.p(u,v,'lab_chair',face='south' if v==1 else 'north')
        for u in (2,3,4):m.p(u,2,'minecraft:spruce_slab',type='bottom',waterlogged='false')
        m.counter(7,2);m.p(7,2,'minecraft:potted_fern',2);m.p(0,0,'lab_glass_cabinet',face='south')
    elif kind in ('display','crystal','tank','containment','infected'):
        for u in range(2,6):
            for v in range(1,5):
                m.p(u,v,'minecraft:sea_lantern' if (u,v)==(3,2) else 'minecraft:polished_deepslate',1)
                if u in (2,5) or v in (1,4):
                    for h in (2,3,4):m.p(u,v,'minecraft:glass' if kind!='infected' else 'minecraft:purple_stained_glass',h)
        if kind in ('crystal','infected'):m.p(3,2,'minecraft:budding_amethyst',2);m.p(3,2,'minecraft:amethyst_cluster',3,facing='up',waterlogged='false')
        elif kind=='tank':
            for u in (3,4):
                for v in (2,3):
                    for h in (2,3):m.p(u,v,'minecraft:water',h,level='0')
                    m.p(u,v,'minecraft:glass',4)
        elif kind=='display':m.p(3,2,'lab_sample_rack',2)
        else:m.p(3,2,'incubator',2)
        m.counter(0,0,monitor=True);m.counter(7,5,sample=True)
        if kind=='infected':m.p(0,5,'minecraft:amethyst_block');m.p(0,5,'minecraft:amethyst_cluster',2,facing='up',waterlogged='false')
    elif kind in ('directory','reading','briefing'):
        if kind=='directory':
            for u in (1,3,5):m.counter(u,0,monitor=True)
            for u in (2,5):m.p(u,4,'minecraft:lectern',face='north',has_book='false',powered='false')
        else:
            for u in range(1,7):
                for v in (2,3):m.p(u,v,'minecraft:smooth_quartz')
            for u in (2,5):m.p(u,0,'lab_chair',face='south');m.p(u,5,'lab_chair',face='north')
            if kind=='reading':m.p(2,2,'lab_sample_rack',2);m.p(5,2,'lab_monitor',2,'south')
    elif kind in ('botany','greenhouse','fungus'):
        for offset in (0,4):
            for u in range(4):
                for v in range(4):
                    rim=u in (0,3) or v in (0,3)
                    m.p(offset+u,v,'minecraft:smooth_quartz' if rim else 'minecraft:mycelium' if kind=='fungus' else 'minecraft:grass_block')
                    if not rim:m.p(offset+u,v,'minecraft:brown_mushroom' if kind=='fungus' else 'minecraft:fern' if kind=='botany' else 'minecraft:azalea',2)
        m.p(1,5,'lab_sink');m.p(6,5,'lab_shelf')
    elif kind=='microscopy':
        for u in range(1,7):m.counter(u,1)
        for u in (2,5):m.p(u,1,'lab_microscope',2,'south');m.p(u,3,'lab_chair',face='north')
        m.p(3,1,'lab_task_lamp',2,'south');m.p(6,1,'lab_sample_rack',2,'south')
        m.p(0,5,'lab_glass_cabinet');m.p(3,5,'lab_rolling_cart');m.p(3,5,'lab_centrifuge',2);m.counter(7,5,sample=True)
    elif kind in ('culture','culture_pods','damaged'):
        for u,v in ((0,0),(3,0),(6,0)):m.p(u,v,'incubator_eroded' if kind=='damaged' and u==0 else 'incubator' if kind=='culture_pods' else 'lab_glass_cabinet',face='south')
        for u in (1,2,3,4,5):m.counter(u,4)
        for u in (1,3):m.p(u,4,'lab_sample_rack',2)
        m.p(5,4,'lab_centrifuge',2);m.p(7,4,'lab_rolling_cart');m.p(4,4,'lab_task_lamp',2)
        if kind=='damaged':m.p(6,3,'minecraft:amethyst_block');m.p(6,3,'minecraft:amethyst_cluster',2,facing='up',waterlogged='false')
    elif kind in ('reagents','wetbench','specimens'):
        for u in range(8):m.counter(u,0)
        for u in (1,3,5):m.p(u,0,'lab_sample_rack',2)
        if kind=='reagents':m.p(7,0,'minecraft:brewing_stand',2,has_bottle_0='true',has_bottle_1='false',has_bottle_2='true')
        elif kind=='wetbench':m.p(7,3,'lab_sink');m.p(1,4,'lab_sink')
        else:
            for u in (0,3,6):m.stack(u,5,'lab_shelf',2)
        for u in (2,5):m.p(u,3,'lab_chair',face='north')
        m.p(6,0,'lab_task_lamp',2);m.p(4,4,'lab_rolling_cart');m.p(4,4,'lab_centrifuge',2)
    elif kind=='office':
        for u in (1,5):m.counter(u,1,monitor=True);m.counter(u+1,1);m.p(u,3,'lab_chair',face='north')
        for u in range(7):m.p(u,5,'minecraft:bookshelf')
    elif kind=='bunks':
        for u in (0,2,4,6):m.bed(u,3,'gray')
        for u in (1,5):m.p(u,5,'lab_cabinet')
    elif kind=='kitchen':
        for u in (0,1):m.p(u,0,'minecraft:smoker',face='south',lit='false')
        m.p(2,0,'lab_sink',face='south')
        for u in (3,4,5,6):m.counter(u,0)
        m.p(0,5,'lab_cryo_cabinet');m.p(3,5,'lab_glass_cabinet');m.p(6,5,'lab_storage_crate')
    elif kind=='dining':
        for u in (1,5):
            for v in (1,2,3,4):m.p(u,v,'minecraft:smooth_quartz')
            for v in (1,3):m.p(u+1,v,'lab_chair',face='west');m.p(u-1,v,'lab_chair',face='east')
    elif kind in ('switchgear','generator','battery','filters','ducts','powercrystal'):
        for u in (0,2,4,6):m.p(u,0,'lab_server_rack',face='south')
        if kind=='generator':
            for u in (1,4):m.p(u,4,'lab_cooling_pump',face='north')
            m.p(7,4,'lab_storage_crate');m.p(0,4,'lab_rolling_cart')
        elif kind=='powercrystal':
            for u in (2,4):m.p(u,4,'minecraft:sea_lantern',0);m.p(u,4,'minecraft:amethyst_block')
            m.p(2,4,'minecraft:amethyst_cluster',2,facing='up',waterlogged='false')
        else:
            for u in range(8):m.p(u,5,'lab_pipe',3,'north')
            m.p(3,3,'lab_control_console',face='north')
    elif kind=='pump':
        for u in (1,5):m.p(u,2,'lab_cooling_pump',face='south')
        for u in range(8):m.p(u,4,'lab_pipe',2,'north')
        m.counter(7,0,monitor=True);m.p(0,0,'lab_server_rack',face='south')
    elif kind=='tools':
        for u in range(7):m.stack(u,0,'lab_shelf',2)
        for u in (1,5):m.p(u,4,'lab_workbench');m.p(u,2,'lab_chair',face='south')
    else:raise KeyError(kind)
    return m


RECIPES={
 'A1':('surveillance','lockers','directory','safe'),'A2':('access','reception','directory','lockers'),
 'A3':('medical','medical','freezers','wetbench'),'A4':('decon','lockers','wetbench','pallets'),
 'A5':('pallets','racks','receiving','packing'),'A6':('reception','display','waiting','botany'),
 'A7':('reception','waiting','display','directory'),'A8':('racks','tools','pallets','packing'),
 'B1':('botany','wetbench','specimens','microscopy'),'B2':('microscopy','microscopy','directory','specimens'),
 'B3':('crystal','microscopy','surveillance','specimens'),'B4':('culture','culture','freezers','wetbench'),
 'B5':('reagents','racks','wetbench','specimens'),'B6':('office','office','briefing','library'),
 'B7':('library','library','reading','safe'),'B8':('wetbench','wetbench','reagents','freezers'),
 'C1':('radio','control','surveillance','library'),'C2':('surveillance','control','briefing','directory'),
 'C3':('surveillance','briefing','lockers','safe'),'C4':('bunks','bunks','lockers','waiting'),
 'C5':('kitchen','dining','dining','foodstore'),'C6':('greenhouse','greenhouse','botany','wetbench'),
 'C7':('library','reading','office','safe'),'C8':('fungus','culture','wetbench','crystal'),
 'D1':('switchgear','battery','control','tools'),'D2':('generator','battery','control','pallets'),
 'D3':('servers','servers','radio','racks'),'D4':('pump','tank','control','tools'),
 'D5':('filters','ducts','control','racks'),'D6':('tools','racks','packing','pallets'),
 'D7':('pallets','racks','racks','pallets'),'D8':('packing','repair','switchgear','tools'),
 'E1':('freezers','freezers','directory','specimens'),'E2':('surveillance','control','lockers','safe'),
 'E3':('containment','containment','control','specimens'),'E4':('surgery','freezers','wetbench','medical'),
 'E5':('culture_pods','culture_pods','freezers','specimens'),'E6':('display','specimens','receiving','wetbench'),
 'E7':('decon','decon','lockers','racks'),'E8':('damaged','packing','infected','alarm'),
 'F1':('control','surveillance','alarm','library'),'F2':('crystal','display','freezers','specimens'),
 'F3':('powercrystal','battery','switchgear','control'),'F4':('pump','tank','tools','generator'),
 'F5':('safe','library','library','directory'),'F6':('access','decon','alarm','lockers'),
 'F7':('infected','damaged','wetbench','alarm'),
}


def footprint(block,state,u,v,h):
    base=block.split(':')[1].replace('_eroded','')
    if block.startswith(NS) and base in FIXTURES:
        width,height=FIXTURES[base];dx,dz=FACES.get(state.get('facing','north'),(0,-1));right=(-dz,dx)
        return [(u+right[0]*x,v+right[1]*x,h+y) for x in range(-(width//2),width//2+1) for y in range(height)]
    return [(u,v,h)]


def room_modules(room):
    """Named functional zones: wall equipment, working island and visitor space.

    The former density pass filled arbitrary free cells with repeated cabinets.
    Here the four room-specific functions retain their own furniture and their
    own clear service sides. Wider north/south bays get a real central island.
    """
    w,d=room['size'];kinds=RECIPES[room['id']]
    zones=[(1,1,0),(w-9,1,0),(1,d-7,2),(w-9,d-7,2)]
    layout='wall_bays_and_workstations'
    if w>=30:
        c=w//2-4
        if room['id']=='A7':
            zones=[(1,1,0),(1,d-7,2),(c,d//2-3,0),(w-9,1,0)]
            layout='reception_display_and_perimeter_loop'
        elif room['id']=='A2':
            zones=[(c,1,0),(1,1,0),(1,d-7,2),(w-9,1,0)]
            layout='paired_access_and_registration'
        elif room['id'] in ('B7','C7','D7','E7','F6'):
            zones=[(1,1,0),(w-9,1,0),(c,d//2-3,0),(1,d-7,2)]
            layout='storage_walls_and_central_work_island'
        else:
            zones=[(1,1,0),(w-9,1,0),(1,d-7,2),(c,2,0)]
            layout='equipment_walls_and_central_work_island'
    elif d>=24 and room['category'] in ('科研','医疗','机务','隔离'):
        zones=[(1,1,0),(w-9,1,0),(w//2-1,d//2+1,2),(1,d-7,2)]
        layout='service_walls_and_work_island'
    room['layout_type']=layout
    return [(kind,*zone) for kind,zone in zip(kinds,zones)]


def make_room(floor,source):
    level=floor['level'];x,z,X,Z=source['bounds_xz'];w=X-x;d=Z-z;high=9 if level=='B3' else 5;palette=PALETTES[level]
    r=dict(source,level=level,floor_y=floor['relative_floor_y'],origin_xz=[x,z],size=[w,d],clear_height=high,ceiling_h=high+1,palette=palette,placements=[],modules=[])
    cells={};seq=0
    def put(u,v,h,key,face='north',layer='furniture',module_id='',replace=False,state=None,manual_item=True):
        nonlocal seq
        block=full(key);base=block.split(':')[1].replace('_eroded','');state=dict(state or {})
        if block.startswith(NS) and (base in DETAILS or base in FIXTURES):state.setdefault('facing',face)
        if block.startswith(NS) and base in FIXTURES:state.setdefault('open','false')
        reserved=footprint(block,state,u,v,h)
        for U,V,H in reserved:
            assert 0<=U<w and 0<=V<d and 0<=H<=high+1,(r['id'],block,U,V,H)
            if (U,V,H) in cells and not replace:raise AssertionError((r['id'],'overlap',block,(U,V,H),cells[U,V,H]['block']))
        if replace:
            removed={cells[p]['seq'] for p in reserved if p in cells}
            for p,old in list(cells.items()):
                if old['seq'] in removed:del cells[p]
        seq+=1;entry=dict(seq=seq,u=u,v=v,h=h,block=block,state=state,layer=layer,module=module_id,manual_item=manual_item,reserved=reserved)
        for p in reserved:cells[p]=entry
    for u in range(w):
        for v in range(d):
            put(u,v,0,palette['border'] if u in (0,w-1) or v in (0,d-1) else palette['floor'],layer='floor')
            put(u,v,high+1,palette['ceiling'],layer='ceiling')
            if u in (0,w-1) or v in (0,d-1):
                for h in range(1,high+1):put(u,v,h,palette['stripe'] if h==3 else palette['wall'],layer='wall')
    # Two genuine three-cell observation fixtures occupy the wall, not the aisle.
    for u in (5,w-6):put(u,0,2,'lab_window_eroded' if r['id']=='E8' else 'lab_window','south','window',replace=True)
    put(w-3,0,high-1,'lab_vent','south','wall_equipment',replace=True)
    for u in range(2,w-2):put(u,1,high-1,'lab_pipe','north','overhead')
    for u in range(4,w-2,6):
        for v in range(4,d-2,6):put(u,v,high,'lab_light_panel',layer='lighting')
    # Double doors at the centre of every specified opening; trim the spare cells.
    door_approaches=set();r['door_openings']=[]
    for n,box in enumerate([source['door_xz'],*source.get('additional_doors_xz',[])]):
        a,b,A,B=box;a-=x;A-=x;b-=z;B-=z
        side='west' if a<=0 else 'east' if A>=w else 'north' if b<=0 else 'south'
        if side in ('west','east'):
            vals=list(range(max(1,b),min(d-1,B)));chosen=vals[(len(vals)-2)//2:(len(vals)-2)//2+2];anchors=[(0 if side=='west' else w-1,t) for t in chosen]
        else:
            vals=list(range(max(1,a),min(w-1,A)));chosen=vals[(len(vals)-2)//2:(len(vals)-2)//2+2];anchors=[(t,0 if side=='north' else d-1) for t in chosen]
        for U,V in anchors:
            put(U,V,1,'lab_door',{'west':'east','east':'west','north':'south','south':'north'}[side],'door',replace=True)
            du,dv={'west':(1,0),'east':(-1,0),'north':(0,1),'south':(0,-1)}[side]
            door_approaches.update((U+du*k,V+dv*k) for k in (1,2))
        r['door_openings'].append({'side':side,'anchors':anchors})
    # Colour stays a narrow guide in the floor and small wall headers; the
    # clinical rooms no longer have a one-block-high black dado or full colour wall.
    for opening in r['door_openings']:
        for U,V in opening['anchors']:
            put(U,V,4,palette['stripe'],layer='wall_header',replace=True)
    for U,V in door_approaches:put(U,V,0,palette['accent'],layer='floor_detail',replace=True)
    if r['id']!='F8':
        for index,(kind,ou,ov,turn) in enumerate(room_modules(r)):
            m=module(kind)
            group=f'M{index+1:02d}';r['modules'].append(dict(id=group,kind=kind,name=m.name,origin=[ou,ov],size=[m.w,m.d],turn=turn))
            for p in m.blocks:
                U=ou+(m.w-1-p['u'] if turn else p['u']);V=ov+(m.d-1-p['v'] if turn else p['v']);state=dict(p['state'])
                if state.get('facing') in FACEORDER:state['facing']=FACEORDER[(FACEORDER.index(state['facing'])+turn)%4]
                put(U,V,p['h'],p['block'],layer='furniture' if p['h'] else 'floor_detail',module_id=group,replace=p['h']==0,state=state,manual_item=p.get('manual_item',True))
    else:
        # Keep the two stairs and cargo shaft from the original plan completely clear.
        for index,(kind,ou,ov,turn) in enumerate([('control',12,2,0),('alarm',22,16,2),('pump',2,16,2),('powercrystal',30,8,0)]):
            m=module(kind);group=f'M{index+1:02d}';r['modules'].append(dict(id=group,kind=kind,name=m.name,origin=[ou,ov],size=[m.w,m.d],turn=turn))
            for p in m.blocks:
                U=ou+(7-p['u'] if turn else p['u']);V=ov+(5-p['v'] if turn else p['v']);state=dict(p['state'])
                if state.get('facing') in FACEORDER:state['facing']=FACEORDER[(FACEORDER.index(state['facing'])+turn)%4]
                put(U,V,p['h'],p['block'],module_id=group,state=state,replace=p['h']==0,layer='floor_detail' if p['h']==0 else 'furniture')
        r['layout_type']='reactor_ring_and_service_perimeter'
        r['modules'].append(dict(id='M05',kind='reactor',name='四柱环框晶体反应装置',origin=[15,8],size=[10,8],turn=0))
        for U in range(15,25):
            for V in range(8,16):
                if U in (15,24) or V in (8,15):put(U,V,0,'minecraft:polished_deepslate',layer='floor_detail',module_id='M05',replace=True)
        # Open framework, split-level crystal branches and an actual upper ring.
        wall={'up':'true','north':'none','south':'none','east':'none','west':'none','waterlogged':'false'}
        slab={'type':'bottom','waterlogged':'false'}
        for U,V in ((15,8),(24,8),(15,15),(24,15)):
            put(U,V,0,'minecraft:sea_lantern',layer='floor_detail',module_id='M05',replace=True)
            for h in range(1,9):put(U,V,h,'minecraft:polished_deepslate_wall',state=wall,module_id='M05')
        for U in range(15,25):
            for V in range(8,16):
                if U in (15,24) or V in (8,15):
                    if (U,V) not in ((15,8),(24,8),(15,15),(24,15)):
                        put(U,V,8,'minecraft:polished_deepslate_slab',state=slab,module_id='M05')
                    if (U,V) not in ((15,8),(24,8),(15,15),(24,15)) and ((U in (15,24) and V not in (11,12)) or (V in (8,15) and U not in (19,20))):
                        put(U,V,1,'minecraft:polished_deepslate_slab',state=slab,module_id='M05')
        for U in range(16,24):
            for V in (8,15):put(U,V,7,'lab_pipe',module_id='M05')
        for U,V,lo,hi in ((19,11,1,6),(18,11,2,4),(20,11,4,6),(19,10,1,3),(19,12,5,7)):
            for h in range(lo,hi+1):put(U,V,h,'minecraft:amethyst_block',module_id='M05')
        for U,V,h in ((18,10,3),(20,10,5),(20,12,6)):put(U,V,h,'minecraft:amethyst_block',module_id='M05')
        for U,V,h in ((19,11,7),(18,11,5),(20,11,7),(19,12,8)):
            put(U,V,h,'minecraft:amethyst_cluster',state={'facing':'up','waterlogged':'false'},module_id='M05')
    r['enrichment_added_floor_cells']=0
    # Surviving entries only: wall openings replace wall cells atomically.
    unique={e['seq']:e for e in cells.values()};r['placements']=sorted(unique.values(),key=lambda p:(p['h'],p['v'],p['u']))
    r['material_counts']=dict(Counter(p['block'] for p in r['placements'] if p['manual_item']))
    return r


def validate_room(r):
    w,d=r['size'];blocked=set()
    for p in r['placements']:
        if p['layer'] in ('floor','ceiling','floor_detail','lighting','overhead'):continue
        if p['block'].split(':')[1].replace('_eroded','') in ('lab_door','lab_privacy_curtain'):continue
        for u,v,h in p['reserved']:
            if 1<=h<=2:blocked.add((u,v))
    free={(u,v) for u in range(1,w-1) for v in range(1,d-1) if (u,v) not in blocked}
    # A clear centre spine is insufficient when a side bench blocks one leaf.
    # Both leaves must have two empty approach cells inside the room.
    for p in r['placements']:
        if p['layer']!='door':continue
        u,v=p['u'],p['v']
        du,dv=(1,0) if u==0 else (-1,0) if u==w-1 else (0,1) if v==0 else (0,-1)
        for step in (1,2):
            assert (u+du*step,v+dv*step) in free,(r['id'],'blocked door approach',u,v,step)
    start=(w//2,d//2)
    if start not in free:start=min(free,key=lambda q:abs(q[0]-w//2)+abs(q[1]-d//2))
    seen={start};todo=deque([start])
    while todo:
        u,v=todo.popleft()
        for p in ((u+1,v),(u-1,v),(u,v+1),(u,v-1)):
            if p in free and p not in seen:seen.add(p);todo.append(p)
    assert len(seen)>.35*(w-2)*(d-2),(r['id'],'insufficient accessible room area')
    if r['id']!='F8':
        # A real two-cell circulation loop may bend around a main island. Every
        # two-leaf door must join that same 2x2-clear-space graph.
        wide={(u,v) for u in range(1,w-2) for v in range(1,d-2)
              if all((u+du,v+dv) in free for du in (0,1) for dv in (0,1))}
        door_nodes=[]
        for opening in r['door_openings']:
            a=min(u for u,v in opening['anchors']);b=min(v for u,v in opening['anchors'])
            side=opening['side']
            node=(1,b) if side=='west' else (w-3,b) if side=='east' else (a,1) if side=='north' else (a,d-3)
            assert node in wide,(r['id'],'door does not reach a two-wide aisle',node)
            door_nodes.append(node)
        connected={door_nodes[0]};queue=deque(connected)
        while queue:
            u,v=queue.popleft()
            for q in ((u+1,v),(u-1,v),(u,v+1),(u,v-1)):
                if q in wide and q not in connected:connected.add(q);queue.append(q)
        assert all(q in connected for q in door_nodes),(r['id'],'disconnected two-wide door routes',door_nodes)
        assert len(connected)>=.18*(w-2)*(d-2),(r['id'],'insufficient two-wide working circulation',len(connected))
        r['two_wide_circulation_cells']=len(connected)
    else:
        for core in LAYOUT['circulation_cores']:
            x,z,X,Z=core['bounds_xz'];ox,oz=r['origin_xz']
            for u in range(x-ox,X-ox):
                for v in range(z-oz,Z-oz):assert (u,v) not in blocked,(r['id'],core['name'],'blocked')
    return dict(id=r['id'],placements=len(r['placements']),reachable_floor_cells=len(seen),two_cell_aisles=r['id']!='F8',overlapping_reserved_cells=0)


def generate():
    OUT.mkdir(parents=True,exist_ok=True);rooms=[];checks=[]
    for floor in LAYOUT['floors']:
        for source in floor['rooms']:
            r=make_room(floor,source);checks.append(validate_room(r));rooms.append(r)
    assert len(rooms)==48 and len({r['id'] for r in rooms})==48
    (OUT/'room_placements.json').write_text(json.dumps({'coordinate_system':'local U east, V south, H block Y above room floor; H0 is floor block','rooms':rooms},ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
    (OUT/'placement_validation.json').write_text(json.dumps({'status':'PASS','rooms':checks,'limits':'Planning checks use block reservations, not Minecraft client rendering or runtime navigation.'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return rooms


if __name__=='__main__':
    rooms=generate();print(json.dumps({'rooms':len(rooms),'placements':sum(len(r['placements']) for r in rooms),'status':'PASS'}))
