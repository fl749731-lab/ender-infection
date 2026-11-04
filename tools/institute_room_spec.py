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
DETAILS={'lab_monitor','lab_sample_rack','lab_shelf','lab_pipe','lab_light_panel','lab_chair','lab_sink','lab_storage_crate','lab_freezer','lab_floor_grate','lab_access_ladder'}
FIXTURES={'lab_cabinet':(1,2),'incubator':(1,3),'surgery_table':(3,2),'lab_workbench':(3,2),'lab_window':(3,2),'lab_door':(1,2),'lab_hatch':(1,1),'lab_vent':(1,1)}
FACES={'north':(0,-1),'east':(1,0),'south':(0,1),'west':(-1,0)}
FACEORDER=list(FACES)

PALETTES={
 'G1':dict(floor='lab_tile_light',border='lab_tile_dark',wall='lab_tile_light',stripe='minecraft:cyan_terracotta',ceiling='lab_tile_light',accent='minecraft:cyan_concrete',note='白色公共区；深灰收边，青色入口导向'),
 'G2':dict(floor='lab_checker',border='lab_tile_dark',wall='lab_tile_light',stripe='minecraft:light_blue_terracotta',ceiling='lab_tile_light',accent='minecraft:light_blue_concrete',note='棋盘科研地面；白墙，浅蓝实验分区'),
 'G3':dict(floor='lab_tile_light',border='lab_tile',wall='lab_tile_light',stripe='minecraft:green_terracotta',ceiling='lab_tile_light',accent='minecraft:green_concrete',note='顶层白灰色；绿色温室与生活区导向'),
 'B1':dict(floor='lab_tile_dark',border='minecraft:polished_andesite',wall='lab_tile',stripe='minecraft:yellow_terracotta',ceiling='lab_tile_dark',accent='minecraft:yellow_concrete',note='深灰机房；黄色设备安全边线'),
 'B2':dict(floor='lab_checker',border='lab_tile_dark',wall='lab_tile_light',stripe='minecraft:purple_terracotta',ceiling='lab_tile_dark',accent='minecraft:purple_concrete',note='白色收容区；紫色区划，事故房局部侵蚀'),
 'B3':dict(floor='lab_tile_dark',border='minecraft:polished_deepslate',wall='lab_tile_dark',stripe='minecraft:purple_terracotta',ceiling='lab_tile_dark',accent='minecraft:purple_concrete',note='深色核心区；紫晶发光，保留环形通路'),
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
     'crystal':'玻璃晶体分析箱','culture':'四座培养舱','reagents':'药剂配制与储柜','office':'双席研究办公位','library':'双排档案书架',
     'reading':'阅览与记录桌','wetbench':'湿法分析长台','radio':'通讯与值班席','control':'U形控制席','briefing':'小型简报桌',
     'bunks':'四床员工卧铺','kitchen':'餐厨后勤台','dining':'双列用餐座位','foodstore':'食物与餐具储存','greenhouse':'灌溉种植槽',
     'fungus':'菌体种植槽','switchgear':'配电柜列','generator':'应急动力机组','battery':'蓄能柜列','servers':'双排服务器柜',
     'pump':'冷却泵组','tank':'玻璃冷却水箱','filters':'空气过滤柜','ducts':'风道与维护阀组','tools':'工具与备件架',
     'packing':'器材拆装工作位','repair':'铁砧与修理台','freezers':'低温样本冷柜','containment':'玻璃收容隔间','specimens':'封装样本架',
     'damaged':'破损培养舱事故点','alarm':'报警与应急终端','safe':'核心档案保险柜','powercrystal':'晶体供能装置','infected':'受侵蚀隔离箱',
    }
    m=Module(labels[kind])
    if kind in ('surveillance','radio','control','alarm'):
        for u in (1,3,5):m.counter(u,0,monitor=True);m.p(u,2,'lab_chair',face='north')
        if kind=='control':
            for v in (3,4):m.counter(0,v);m.counter(7,v)
        elif kind=='radio':
            for h in (1,2,3):m.p(7,4,'minecraft:iron_block',h)
            m.p(7,4,'lab_monitor',4,'south');m.p(0,5,'lab_cabinet')
        elif kind=='alarm':
            m.p(7,0,'minecraft:redstone_lamp',2);m.p(7,1,'minecraft:redstone_block',2);m.p(0,5,'lab_storage_crate')
        else:m.p(7,5,'lab_cabinet');m.p(0,5,'lab_shelf')
    elif kind=='access':
        for u in (1,6):
            for v in range(6):
                if v==2:m.p(u,v,'lab_door',face='east')
                else:
                    for h in (1,2,3):m.p(u,v,'minecraft:light_gray_stained_glass',h)
        for u in (3,4):m.p(u,2,'lab_floor_grate',0)
        m.counter(7,0,monitor=True)
    elif kind=='reception':
        for u in range(1,7):m.counter(u,1)
        for v in (2,3):m.counter(6,v)
        for u in (2,5):m.p(u,1,'lab_monitor',2,'south');m.p(u,3,'lab_chair',face='north')
        m.p(0,5,'lab_cabinet');m.p(7,5,'minecraft:barrel',face='north')
    elif kind=='medical':
        for u in (1,5):m.bed(u,3);m.counter(u+1,3,monitor=True,face='north')
        m.p(0,0,'lab_cabinet',face='south');m.p(7,0,'lab_sink',face='south')
    elif kind=='surgery':
        m.p(3,3,'surgery_table');m.p(0,0,'lab_cabinet',face='south');m.p(7,0,'lab_sink',face='south')
        m.counter(6,5,sample=True);m.p(2,0,'lab_monitor',3,'south')
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
            for v in (3,4):m.p(u,v,'minecraft:barrel',face='north')
        for u in range(4):m.p(u,2,'minecraft:yellow_concrete',0)
    elif kind in ('racks','library','servers','freezers','foodstore','safe'):
        for v in (0,5):
            for u in range(7):
                key={'racks':'lab_shelf','library':'minecraft:bookshelf','servers':'minecraft:gray_concrete','freezers':'lab_freezer','foodstore':'minecraft:barrel','safe':'minecraft:iron_block'}[kind]
                m.stack(u,v,key,3 if kind in ('library','servers','safe') else 2,'south' if v==0 else 'north')
                if kind=='servers':m.p(u,v,'lab_monitor',4,'south' if v==0 else 'north')
        if kind=='safe':m.p(7,2,'lab_cabinet',face='west')
    elif kind in ('receiving','packing','repair'):
        for u in (1,5):m.p(u,1,'lab_workbench',face='south')
        m.p(1,3,'lab_chair',face='north');m.p(5,3,'lab_chair',face='north')
        if kind=='receiving':m.counter(7,5,sample=True);m.p(0,5,'lab_storage_crate')
        elif kind=='repair':m.p(7,5,'minecraft:anvil',facing='north');m.p(0,5,'minecraft:grindstone',face='north')
        else:m.stack(7,5,'lab_storage_crate',2);m.p(0,5,'lab_shelf')
    elif kind=='waiting':
        for v in (1,4):
            for u in (1,3,5):m.p(u,v,'lab_chair',face='south' if v==1 else 'north')
        m.counter(7,2);m.p(7,2,'minecraft:potted_fern',2)
    elif kind in ('display','crystal','tank','containment','infected'):
        for u in range(2,6):
            for v in range(1,5):
                m.p(u,v,'minecraft:sea_lantern' if (u,v)==(3,2) else 'minecraft:polished_deepslate',1)
                if u in (2,5) or v in (1,4):
                    for h in (2,3,4):m.p(u,v,'minecraft:glass' if kind!='infected' else 'minecraft:purple_stained_glass',h)
        if kind in ('crystal','infected'):m.p(3,2,'minecraft:budding_amethyst',2);m.p(3,2,'minecraft:amethyst_cluster',3,facing='up',waterlogged='false')
        elif kind=='tank':
            for u in (3,4):
                for v in (2,3):m.p(u,v,'minecraft:blue_ice',2)
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
        for u in (1,5):m.p(u,1,'lab_workbench',face='south');m.p(u,3,'lab_chair',face='north');m.p(u,0,'lab_monitor',3,'south')
        m.counter(7,5,sample=True)
    elif kind in ('culture','damaged'):
        for u,v in ((1,1),(4,1),(1,4),(4,4)):m.p(u,v,'incubator_eroded' if kind=='damaged' and u==1 else 'incubator')
        m.counter(7,0,monitor=True);m.counter(7,5,sample=True)
        if kind=='damaged':m.p(6,3,'minecraft:amethyst_block');m.p(6,3,'minecraft:amethyst_cluster',2,facing='up',waterlogged='false')
    elif kind in ('reagents','wetbench','specimens'):
        for u in range(8):m.counter(u,0)
        for u in (1,3,5):m.p(u,0,'lab_sample_rack',2)
        if kind=='reagents':m.p(7,0,'minecraft:brewing_stand',2,has_bottle_0='true',has_bottle_1='false',has_bottle_2='true')
        elif kind=='wetbench':m.p(7,3,'lab_sink');m.p(1,4,'lab_sink')
        else:
            for u in (0,3,6):m.stack(u,5,'lab_shelf',2)
        for u in (2,5):m.p(u,3,'lab_chair',face='north')
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
        for u in (0,2,4,6):m.p(u,5,'minecraft:barrel',face='north')
    elif kind=='dining':
        for u in (1,5):
            for v in (1,2,3,4):m.p(u,v,'minecraft:smooth_quartz')
            for v in (1,3):m.p(u+1,v,'lab_chair',face='west');m.p(u-1,v,'lab_chair',face='east')
    elif kind in ('switchgear','generator','battery','filters','ducts','powercrystal'):
        for u in (0,2,4,6):
            key='minecraft:iron_block' if kind in ('switchgear','filters') else 'minecraft:gray_concrete' if kind=='battery' else 'minecraft:polished_andesite'
            m.stack(u,0,key,3)
            m.p(u,0,'lab_monitor' if kind!='filters' else 'lab_vent',4,'south')
        if kind=='generator':
            for u in (1,2,3,4,5):m.p(u,4,'minecraft:blast_furnace',face='north',lit='false')
        elif kind=='powercrystal':
            for u in (2,4):m.p(u,4,'minecraft:sea_lantern',0);m.p(u,4,'minecraft:amethyst_block')
            m.p(2,4,'minecraft:amethyst_cluster',2,facing='up',waterlogged='false')
        else:
            for u in range(8):m.p(u,5,'lab_pipe',3,'north')
    elif kind=='pump':
        for u in (1,5):m.p(u,2,'minecraft:blast_furnace',face='south',lit='false');m.stack(u,1,'minecraft:iron_block',2)
        for u in range(8):m.p(u,4,'lab_pipe',2,'north')
        m.counter(7,0,monitor=True)
    elif kind=='tools':
        for u in range(7):m.stack(u,0,'lab_shelf',2)
        for u in (1,5):m.p(u,4,'lab_workbench');m.p(u,2,'lab_chair',face='south')
    else:raise KeyError(kind)
    return m


RECIPES={
 'A1':('surveillance','lockers','directory','safe'),'A2':('access','reception','directory','lockers'),
 'A3':('medical','surgery','freezers','wetbench'),'A4':('decon','lockers','wetbench','pallets'),
 'A5':('pallets','racks','receiving','packing'),'A6':('reception','waiting','reading','botany'),
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
 'E5':('culture','culture','freezers','specimens'),'E6':('display','specimens','receiving','wetbench'),
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
                for h in range(1,high+1):put(u,v,h,palette['border'] if h==1 else palette['stripe'] if h==4 else palette['wall'],layer='wall')
    # Two genuine three-cell observation fixtures occupy the wall, not the aisle.
    for u in (5,w-6):put(u,0,2,'lab_window_eroded' if r['id']=='E8' else 'lab_window','south','window',replace=True)
    put(w-3,0,high-1,'lab_vent','south','wall_equipment',replace=True)
    for u in range(2,w-2):put(u,1,high-1,'lab_pipe','north','overhead')
    for u in range(4,w-2,6):
        for v in range(4,d-2,6):put(u,v,high,'lab_light_panel',layer='lighting')
    # Double doors at the centre of every specified opening; trim the spare cells.
    for n,box in enumerate([source['door_xz'],*source.get('additional_doors_xz',[])]):
        a,b,A,B=box;a-=x;A-=x;b-=z;B-=z
        side='west' if a<=0 else 'east' if A>=w else 'north' if b<=0 else 'south'
        if side in ('west','east'):
            vals=list(range(max(1,b),min(d-1,B)));chosen=vals[(len(vals)-2)//2:(len(vals)-2)//2+2];anchors=[(0 if side=='west' else w-1,t) for t in chosen]
        else:
            vals=list(range(max(1,a),min(w-1,A)));chosen=vals[(len(vals)-2)//2:(len(vals)-2)//2+2];anchors=[(t,0 if side=='north' else d-1) for t in chosen]
        for U,V in anchors:put(U,V,1,'lab_door',{'west':'east','east':'west','north':'south','south':'north'}[side],'door',replace=True)
    if r['id']!='F8':
        for index,kind in enumerate(RECIPES[r['id']]):
            m=module(kind);turn=2 if index>=2 else 0;ou=2 if index%2==0 else w-10;ov=2 if index<2 else d-8
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
        r['modules'].append(dict(id='M05',kind='reactor',name='方形晶体反应装置',origin=[15,8],size=[10,8],turn=0))
        for U in range(15,25):
            for V in range(8,16):
                if U in (15,24) or V in (8,15):put(U,V,0,'minecraft:polished_deepslate',layer='floor_detail',module_id='M05',replace=True)
        for U,H in [(18,3),(19,6),(20,7),(21,5)]:
            put(U,11,0,'minecraft:sea_lantern',layer='floor_detail',module_id='M05',replace=True)
            for y in range(1,H+1):put(U,11,y,'minecraft:amethyst_block',module_id='M05')
            put(U,11,H+1,'minecraft:amethyst_cluster',state={'facing':'up','waterlogged':'false'},module_id='M05')
    # Enrich the broad room envelopes with coherent side benches and storage.
    # Every added row has two clear approach cells; the cross aisle stays free.
    if r['id']!='F8':
        occupied={(U,V) for U,V,H in cells if H in (1,2)};approaches=set();added=0
        zones=[(1,1,w//2-1,d//2-1),(w//2+1,1,w-1,d//2-1),
               (1,d//2+1,w//2-1,d-1),(w//2+1,d//2+1,w-1,d-1)]
        for index,(a,b,A,B) in enumerate(zones):
            r['modules'][index]['origin']=[a,b];r['modules'][index]['size']=[A-a,B-b]
            r['modules'][index]['name']+='＋补充陈设'
            for V in range(b,B):
                for U in range(a,A-2,4):
                    patch={(u,V) for u in range(U,U+3)};direction=1 if index<2 else -1
                    front={(u,V+direction*k) for u in range(U,U+3) for k in (1,2)}
                    if not all(1<=u<w-1 and 1<=v<d-1 for u,v in front):continue
                    if (patch&(occupied|approaches)) or (front&occupied):continue
                    group=f'M{index+1:02d}';face='south' if direction==1 else 'north'
                    for u in range(U,U+3):
                        if r['category'] in ('科研','医疗','安保','隔离'):
                            put(u,V,1,'lab_freezer',face,module_id=group)
                            put(u,V,2,'lab_monitor' if r['category']=='安保' and u==U+1 else 'lab_sample_rack',face,module_id=group)
                        elif r['category']=='温室':
                            put(u,V,1,'minecraft:mycelium' if r['id']=='C8' else 'minecraft:grass_block',module_id=group)
                            put(u,V,2,'minecraft:brown_mushroom' if r['id']=='C8' else 'minecraft:fern',module_id=group)
                        else:
                            key='minecraft:bookshelf' if r['category']=='档案' else 'lab_shelf' if r['category']=='机务' else 'lab_cabinet'
                            put(u,V,1,key,face,module_id=group)
                            if key!='lab_cabinet':put(u,V,2,key,face,module_id=group)
                    occupied|=patch;approaches|=front;added+=3
                    if added>=42:break
                if added>=42:break
        r['enrichment_added_floor_cells']=added
    # Surviving entries only: wall openings replace wall cells atomically.
    unique={e['seq']:e for e in cells.values()};r['placements']=sorted(unique.values(),key=lambda p:(p['h'],p['v'],p['u']))
    r['material_counts']=dict(Counter(p['block'] for p in r['placements'] if p['manual_item']))
    return r


def validate_room(r):
    w,d=r['size'];blocked=set()
    for p in r['placements']:
        if p['layer'] in ('floor','ceiling','floor_detail','lighting','overhead'):continue
        for u,v,h in p['reserved']:
            if 1<=h<=2:blocked.add((u,v))
    free={(u,v) for u in range(1,w-1) for v in range(1,d-1) if (u,v) not in blocked}
    start=(w//2,d//2)
    if start not in free:start=min(free,key=lambda q:abs(q[0]-w//2)+abs(q[1]-d//2))
    seen={start};todo=deque([start])
    while todo:
        u,v=todo.popleft()
        for p in ((u+1,v),(u-1,v),(u,v+1),(u,v-1)):
            if p in free and p not in seen:seen.add(p);todo.append(p)
    assert len(seen)>.35*(w-2)*(d-2),(r['id'],'insufficient accessible room area')
    if r['id']!='F8':
        # Reserve a two-cell-wide cross spine and enforce it against real props.
        for u in (w//2-1,w//2):
            for v in range(1,d-1):assert (u,v) in free,(r['id'],'blocked N-S aisle',u,v)
        for v in (d//2-1,d//2):
            for u in range(1,w-1):assert (u,v) in free,(r['id'],'blocked E-W aisle',u,v)
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
