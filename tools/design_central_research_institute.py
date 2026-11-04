"""Render original, coordinate-based planning drawings, not game screenshots.

Illustrated concepts are made with imagegen separately. This script creates the
editable SVG plans, matching PNG diagrams and room schedule from one data source.
"""
from collections import deque
from html import escape
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'previews/central_research_institute_2026-11-04'
OUT.mkdir(parents=True,exist_ok=True)
INK='#24394A';MUTED='#617583';PAPER='#F3F6F7';CYAN='#367F95';PURPLE='#8345AD';GOLD='#B67721'
COLORS={'安保':'#E4E0EF','公共':'#E4EDF4','科研':'#DFEFE9','医疗':'#F1DFDF',
        '档案':'#E5E8F0','温室':'#DDEBD6','机务':'#F3E7D1','隔离':'#ECDEF0'}
ENVELOPE=[(8,8),(40,8),(40,12),(80,12),(80,8),(112,8),(112,36),(108,36),
          (108,60),(112,60),(112,88),(80,88),(80,84),(76,84),(76,88),(44,88),
          (44,84),(40,84),(40,88),(8,88),(8,60),(12,60),(12,36),(8,36)]
BAYS=[('NW',[12,12,36,32],[35,20,37,23]),('N',[44,12,76,32],[58,31,62,33]),
      ('NE',[84,12,108,32],[83,20,85,23]),('W',[12,36,36,60],[35,46,37,50]),
      ('E',[84,36,108,60],[83,46,85,50]),('SW',[12,64,36,84],[35,72,37,76]),
      ('S',[44,64,76,84],[58,63,62,65]),('SE',[84,64,108,84],[83,72,85,76])]
CORES=[('西楼梯',[41,39,47,47]),('东楼梯',[73,49,79,57]),('货运井',[73,37,79,43])]
FLOORS=[
 ('G1',0,'A','接收与公共服务',[
  ('门卫与监控','安保'),('门禁与登记','安保'),('医疗观察','医疗'),('消毒与更衣','医疗'),
  ('装卸与收发','机务'),('访客接待','公共'),('入口大厅','公共'),('器材储藏','机务')]),
 ('G2',7,'B','分析与研究',[
  ('植物菌体分析','科研'),('显微分析','科研'),('晶体分析','科研'),('组织培养','科研'),
  ('试剂准备','科研'),('研究员办公室','公共'),('档案与阅览','档案'),('湿法分析','科研')]),
 ('G3',14,'C','总控与温室',[
  ('通讯与值班','安保'),('主控制室','安保'),('安全调度','安保'),('员工宿舍','公共'),
  ('休息与餐厅','公共'),('植物温室','温室'),('研究记录室','档案'),('菌体温室','温室')]),
 ('B1',-7,'D','设施运维',[
  ('主配电室','机务'),('应急电源','机务'),('服务器机房','机务'),('冷却泵房','机务'),
  ('过滤与通风','机务'),('工具与备件','机务'),('货物仓库','机务'),('维修工场','机务')]),
 ('B2',-14,'E','收容与隔离',[
  ('低温封存','隔离'),('隔离监控','安保'),('实验体收容','隔离'),('解剖与检验','医疗'),
  ('培养舱阵列','隔离'),('样本隔离','隔离'),('消毒气闸','医疗'),('事故实验室','隔离')]),
 ('B3',-25,'F','裂隙核心实验',[
  (None,None),('核心控制','安保'),('裂隙样本库','隔离'),('晶体供能','机务'),
  ('冷却与应急','机务'),('核心档案库','档案'),('最终气闸','隔离'),('污染隔离','隔离')]),
]


class Canvas:
    def __init__(self,w,h):
        self.w=w;self.h=h;self.im=Image.new('RGB',(w,h),PAPER);self.d=ImageDraw.Draw(self.im)
        self.parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
                    f'<rect width="{w}" height="{h}" fill="{PAPER}"/>']
    def rect(self,box,fill,stroke=None,width=1):
        x,y,X,Y=box;self.d.rectangle(box,fill=fill,outline=stroke,width=max(1,round(width)))
        self.parts.append(f'<rect x="{x}" y="{y}" width="{X-x}" height="{Y-y}" fill="{fill or "none"}" stroke="{stroke or "none"}" stroke-width="{width}"/>')
    def poly(self,points,fill,stroke=None,width=1):
        self.d.polygon(points,fill=fill)
        if stroke:self.d.line(points+[points[0]],fill=stroke,width=round(width),joint='curve')
        self.parts.append('<polygon points="'+' '.join(f'{x},{y}' for x,y in points)+f'" fill="{fill}" stroke="{stroke or "none"}" stroke-width="{width}"/>')
    def line(self,points,color=INK,width=2,dash=False):
        if not dash:self.d.line(points,fill=color,width=max(1,round(width)),joint='curve')
        else:
            for (x,y),(X,Y) in zip(points,points[1:]):
                length=math.hypot(X-x,Y-y)
                if not length:continue
                for start in range(0,math.ceil(length),15):
                    end=min(start+8,length)
                    self.d.line([(x+(X-x)*start/length,y+(Y-y)*start/length),(x+(X-x)*end/length,y+(Y-y)*end/length)],fill=color,width=max(1,round(width)))
        self.parts.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in points)+f'" fill="none" stroke="{color}" stroke-width="{width}"'+(' stroke-dasharray="8 7"' if dash else '')+'/>')
    def text(self,x,y,s,size=24,color=INK,bold=False,anchor='left'):
        font=ImageFont.truetype('C:/Windows/Fonts/'+('msyhbd.ttc' if bold else 'msyh.ttc'),size)
        self.d.text((x,y),s,font=font,fill=color,anchor={'left':'lm','center':'mm','right':'rm'}[anchor])
        self.parts.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="Microsoft YaHei,Noto Sans CJK SC,sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" text-anchor="'+{'left':'start','center':'middle','right':'end'}[anchor]+f'" dominant-baseline="central">{escape(s)}</text>')
    def arrow(self,points,color=GOLD,width=4):
        self.line(points,color,width)
        (x,y),(X,Y)=points[-2:];a=math.atan2(Y-y,X-x);r=11
        self.poly([(X,Y),(X-r*math.cos(a-.5),Y-r*math.sin(a-.5)),(X-r*math.cos(a+.5),Y-r*math.sin(a+.5))],color)
    def save(self,name):
        self.im.save(OUT/(name+'.png'));(OUT/(name+'.svg')).write_text('\n'.join(self.parts+['</svg>']),encoding='utf-8')


def schedule():
    out=[]
    for level,y,prefix,theme,programs in FLOORS:
        rooms=[]
        for (bay,rect,door),(name,category) in zip(BAYS,programs):
            if name:rooms.append({'id':prefix+str(len(rooms)+1),'name':name,'category':category,'bay':bay,'bounds_xz':rect,'door_xz':door})
        if level=='G1':
            next(r for r in rooms if r['bay']=='S')['additional_doors_xz']=[[58,83,62,85]]
            next(r for r in rooms if r['bay']=='E')['additional_doors_xz']=[[107,46,110,50]]
        if level=='B3':rooms.append({'id':'F8','name':'裂隙实验大厅','category':'隔离','bay':'CORE','bounds_xz':[40,36,80,60],'door_xz':[39,47,41,51],
                                      'clear_encounter_floor':[48,38,72,58],'floor_to_floor':11})
        out.append({'level':level,'relative_floor_y':y,'theme':theme,'rooms':rooms})
    return out


def validate(data):
    results=[]
    for floor in data:
        mask=Image.new('1',(120,104),0);d=ImageDraw.Draw(mask)
        if floor['relative_floor_y']>=0:d.polygon(ENVELOPE,fill=1)
        else:d.rectangle((8,8,111,87),fill=1)
        for room in floor['rooms']:
            if room['bay']=='CORE':continue
            x,z,X,Z=room['bounds_xz'];d.rectangle((x,z,X-1,Z-1),fill=0);d.rectangle((x+1,z+1,X-2,Z-2),fill=1)
        if floor['level'] in ('G2','G3'):d.rectangle((49,39,70,56),fill=0)
        if floor['level']=='B3':d.rectangle((12,12,35,31),fill=0)
        if floor['level']=='G1':
            d.rectangle((58,83,61,103),fill=1)
            d.rectangle((107,46,119,49),fill=1)
        for room in floor['rooms']:
            for x,z,X,Z in [room['door_xz'],*room.get('additional_doors_xz',[])]:d.rectangle((x,z,X-1,Z-1),fill=1)
        start=(42,40);seen={start};queue=deque([start])
        while queue:
            x,z=queue.popleft()
            for X,Z in [(x+1,z),(x-1,z),(x,z+1),(x,z-1)]:
                if 0<=X<120 and 0<=Z<104 and (X,Z) not in seen and mask.getpixel((X,Z)):
                    seen.add((X,Z));queue.append((X,Z))
        for room in floor['rooms']:
            x,z,X,Z=room['bounds_xz'];point=((x+X)//2,(z+Z)//2)
            assert point in seen,(floor['level'],room['id'],'unreachable')
            assert 8<=x<X<=112 and 8<=z<Z<=88
        for name,(x,z,X,Z) in CORES:assert ((x+X)//2,(z+Z)//2) in seen,(floor['level'],name)
        if floor['level']=='G1':assert (60,101) in seen and (116,48) in seen,'Main entrance or loading dock disconnected'
        results.append({'level':floor['level'],'rooms':len(floor['rooms']),'rooms_reachable_in_2d':True,'both_stairs_and_cargo_accessible':True})
    assert sum(len(f['rooms']) for f in data)==48
    return {'status':'PASS','checks':'Planning grid only; no Minecraft structure or runtime pathfinding is implied','floors':results,'functional_rooms':48}


def plans(data):
    c=Canvas(3160,2220)
    c.text(62,62,'中央研究所  /  六层平面',52,bold=True)
    c.text(64,128,'园区 120×104 格  ·  主体包络 104×80 格  ·  每层 8 个功能房间，共 48 间',27,MUTED)
    c.text(64,174,'全部朝北、同尺度；房间标注为含墙包络。双楼梯与货运井上下对位。彩色虚线表示建议探索方向。',23,MUTED)
    for index,floor in enumerate(data):
        ox=60+(index%3)*1020;oy=218+(index//3)*910
        c.rect((ox,oy,ox+1000,oy+890),'#FFFFFF','#D6E0E5',2)
        level=floor['level'];y=floor['relative_floor_y'];accent=PURPLE if y<0 else CYAN
        c.text(ox+28,oy+42,f'{level}  |  标高 {y:+d}',32,accent,True)
        c.text(ox+28,oy+88,floor['theme'],25,bold=True)
        c.text(ox+965,oy+43,'N ↑',25,INK,True,'right')
        px=ox+80;py=oy+138;s=7
        def p(x,z):return (px+x*s,py+z*s)
        def rect(r,fill,stroke=None,w=1):c.rect((*p(r[0],r[1]),*p(r[2],r[3])),fill,stroke,w)
        rect([0,0,120,104],'#EFF3F0','#BAC8CD',1)
        for x in range(0,121,8):c.line([p(x,0),p(x,104)],'#DDE5E4',1)
        for z in range(0,105,8):c.line([p(0,z),p(120,z)],'#DDE5E4',1)
        if level=='G1':rect([108,42,120,58],'#DEE5E7','#BAC8CD',2)
        if y>=0:c.poly([p(x,z) for x,z in ENVELOPE],'#E8ECEE',INK,3)
        else:rect([8,8,112,88],'#E8ECEE',INK,3)
        for r in [[36,8,40,88],[80,8,84,88],[8,32,112,36],[8,60,112,64]]:rect(r,'#FFF2CF')
        corefill='#E4F3F6' if y>=0 else '#E5E8EA' if level=='B1' else '#EEE5F5'
        rect([40,36,80,60],corefill)
        if level in ('G2','G3'):rect([49,39,71,57],'#F6FBFC',CYAN,2)
        for room in floor['rooms']:
            if room['bay']=='CORE':continue
            r=room['bounds_xz'];rect(r,COLORS[room['category']],INK,3)
            for dx,dz,dX,dZ in [room['door_xz'],*room.get('additional_doors_xz',[])]:
                rect([dx,dz,dX,dZ],'#FFF2CF');c.line([p(dx,dz),p(dX,dZ)],GOLD,2)
            x,z,X,Z=r;cx,cz=p((x+X)/2,(z+Z)/2)
            c.text(cx,cz-17,room['id'],18,accent,True,'center')
            c.text(cx,cz+10,room['name'],19,INK,False,'center')
            c.text(cx,cz+37,f'{X-x}×{Z-z} 格',15,MUTED,False,'center')
        if level=='B3':
            rect([12,12,36,32],'#D8D8DC','#818791',2)
            c.text(*p(24,21),'塌陷封闭区',18,MUTED,False,'center')
            rect([48,38,72,58],'#DEC5F0',PURPLE,2)
            c.text(*p(60,43),'F8 裂隙实验大厅',18,PURPLE,True,'center')
            c.text(*p(60,48),'大厅 40×24 格',17,INK,False,'center')
            c.text(*p(60,53),'净场地 24×20 格',16,INK,False,'center')
        else:
            label={'G1':'中庭公共大厅','G2':'中庭挑空','G3':'中庭挑空','B1':'设备转运厅','B2':'玻璃观察廊'}[level]
            c.text(*p(60,48),label,18,accent,True,'center')
            if level=='B2':c.text(*p(60,53),'下望裂隙大厅',15,MUTED,False,'center')
        for name,r in CORES:
            rect(r,'#D1E8ED',CYAN,2)
            x,z,X,Z=r
            if '楼梯' in name:
                for step in range(z+1,Z):c.line([p(x+.7,step),p(X-.7,step)],CYAN,1)
                tag='西梯' if name=='西楼梯' else '东梯'
            else:
                c.line([p(x,z),p(X,Z)],CYAN,1);tag='货运'
            c.text(*p((x+X)/2,(z+Z)/2),tag,13,INK,True,'center')
        if level=='G1':
            rect([48,88,72,96],'#D8E2E5',CYAN,2)
            c.text(*p(60,92),'入口雨棚',16,CYAN,False,'center')
            c.arrow([p(60,103),p(60,96)],GOLD,4)
            c.text(*p(114,45),'装卸',15,MUTED,False,'center')
            c.text(*p(114,54),'货场',15,MUTED,False,'center')
        route=[(60,86),(82,86),(82,62),(82,34),(38,34),(38,48),(41,48)] if y>=0 else [(40,48),(38,48),(38,62),(82,62),(82,48)]
        if level=='G1':route=[(60,96),(60,82),(73,82),(73,66),(60,66),(60,63),(38,63),(38,48),(41,48)]
        c.line([p(x,z) for x,z in route],accent,3,True)
        for n in (0,40,80,120):c.text(*p(n,-2.4),str(n),14,MUTED,False,'center')
        c.text(ox+30,oy+862,'X →  /  Z ↓    1 格 = 1 个方块；家具和设备另作细化。',18,MUTED)
    c.text(64,2088,'图例',24,bold=True)
    for i,(name,color) in enumerate(COLORS.items()):
        x=165+i*350;c.rect((x,2073,x+34,2103),color,INK,1);c.text(x+47,2088,name,23)
    c.text(64,2153,'设计版 01  ·  2026-11-04  ·  尺寸与房间编号以本图及 layout.json 为准；概念图只表达外观和氛围。',24,MUTED)
    c.save('03_floorplans')


def section():
    c=Canvas(3200,1800)
    c.text(64,64,'中央研究所  /  A—A 标高剖面',52,bold=True)
    c.text(64,132,'南北向剖线 X=60  ·  地上三层 / 地下三层  ·  标高均相对于首层地坪',28,MUTED)
    c.text(64,184,'1 格 = 1 个方块；楼板与隔墙在结构细化阶段扣除。此图是尺寸示意，不是游戏内截图。',24,MUTED)
    x0=180;y0=840;s=21
    def p(z,y):return (x0+z*s,y0-y*s)
    def rect(z,Z,y,Y,fill,stroke=None,w=1):c.rect((*p(z,Y),*p(Z,y)),fill,stroke,w)
    # Earth and the three-level reinforced basement shell.
    rect(0,104,-28,0,'#D9CBB7')
    rect(8,88,-26,0,'#F1F2F3',INK,4)
    for y in (-28,-26):c.line([p(0,y),p(104,y)],'#BAA88D',2)
    # The public atrium sits over a sealed service and research basement.
    rect(8,36,0,21,'#E4EDF0',INK,4);rect(60,88,0,21,'#E4EDF0',INK,4)
    rect(36,60,0,21,'#DFF0F5',CYAN,3)
    c.poly([p(z,y) for z,y in [(36,21),(38,21),(38,23),(42,23),(42,25),(46,25),(46,26),(50,26),(50,25),(54,25),(54,23),(58,23),(58,21),(60,21)]],'#D8EDF3',CYAN,3)
    for z in range(38,60,4):c.line([p(z,0),p(z,21)],CYAN,2)
    for y in (7,14):
        c.line([p(8,y),p(36,y)],INK,7);c.line([p(60,y),p(88,y)],INK,7)
        c.line([p(36,y),p(40,y)],CYAN,5);c.line([p(56,y),p(60,y)],CYAN,5)
    for z in (8,36,60,88):c.line([p(z,0),p(z,21)],INK,4)
    for z in (8,88):c.line([p(z,-26),p(z,0)],INK,5)
    for y in (0,-7,-14,-25):
        c.line([p(8,y),p(88,y)],CYAN if y==-14 else INK,6)
    rect(36,60,-25,-14,'#DDC9EB',PURPLE,3)
    # Central B2 glass deck seals the 11-block-high experiment chamber below.
    for z in range(37,60,3):c.line([p(z,-14),p(z+1,-13)],CYAN,2)
    for y in (-7,-14):
        for z in (36,60):c.line([p(z,y),p(z,y+7)],INK,3)
    for i,(tag,y,north,south) in enumerate([
        ('G1',0,'门禁 / 登记','入口大厅'),('G2',7,'显微分析','档案 / 阅览'),('G3',14,'主控制室','研究记录'),
        ('B1',-7,'应急电源','货物仓库'),('B2',-14,'隔离监控','消毒气闸'),('B3',-25,'核心控制','最终气闸')]):
        h=11 if tag=='B3' else 7;cy=y+(h/2)
        c.text(*p(22,cy),north,27,INK,True,'center');c.text(*p(74,cy),south,27,INK,True,'center')
        c.line([p(1,y),p(103,y)],'#81939E',1,True)
        c.text(x0-28,p(0,y)[1],f'{tag}  {y:+d}',26,PURPLE if y<0 else CYAN,True,'right')
    c.text(*p(48,12),'三层通高中庭',29,CYAN,True,'center')
    c.text(*p(48,8),'玻璃顶 / 回廊',24,CYAN,False,'center')
    c.text(*p(48,-3.5),'设备转运',24,INK,False,'center')
    c.text(*p(48,-10.5),'玻璃观察廊',24,CYAN,True,'center')
    c.text(*p(48,-16.1),'F8 裂隙实验大厅',27,PURPLE,True,'center')
    # A diagrammatic voxel crystal and block-built containment platform.
    rect(40,56,-25,-23.5,'#6D5A7D',PURPLE,3)
    for z,h in [(43,2),(45,4),(47,6),(49,5),(51,3)]:
        c.poly([p(z,-23.5),p(z,-23.5+h-1),p(z+1,-23.5+h),p(z+2,-23.5+h-1),p(z+2,-23.5)],'#AC75D3',PURPLE,2)
    c.line([p(0,0),p(8,0)],'#718469',5);c.line([p(88,0),p(104,0)],'#718469',5)
    rect(88,96,0,4,'#E4EDF0',CYAN,2);c.text(*p(92,2),'入口',22,CYAN,False,'center')
    # Dimension rails with a consistent block scale.
    c.line([p(0,29),p(104,29)],INK,2)
    for z in (0,104):c.line([p(z,28.3),p(z,29.7)],INK,2)
    c.text(*p(52,30.6),'园区南北长 104 格',26,INK,True,'center')
    c.text(*p(0,1.7),'北 N',22,MUTED);c.text(*p(104,1.7),'南 S',22,MUTED,False,'right')
    c.line([p(108,-25),p(108,26)],INK,2)
    for y in (-25,26):c.line([p(107.3,y),p(108.7,y)],INK,2)
    c.text(p(108.8,0)[0],p(0,2)[1],'标高差',18,INK)
    c.text(p(108.8,0)[0],p(0,0)[1],'51 格',24,INK,True)
    # Explanatory panel.
    rx=2570
    c.text(rx,330,'关键尺寸',32,bold=True)
    notes=[('园区','120×104 格'),('主体包络','104×80 格'),('普通层层高','7 格（含楼板）'),('B3 层层高','11 格（含楼板）'),
           ('主楼屋面','+21 格'),('中庭玻璃顶','最高 +26 格'),('基础底面','-28 格'),('核心实验大厅','40×24 格'),('中央净场地','24×20 格')]
    for i,(a,b) in enumerate(notes):
        c.text(rx,391+i*58,a,23,MUTED);c.text(rx+560,391+i*58,b,24,INK,True,'right')
    c.text(rx,994,'竖向交通',30,bold=True)
    for i,t in enumerate(['西楼梯、东楼梯贯通六层','货运井另设，不穿过裂隙晶核','剖线不经过竖井，位置见平面图']):c.text(rx,1054+i*47,t,22,MUTED)
    c.text(rx,1250,'探索与侵蚀',30,bold=True)
    for i,t in enumerate(['地表：公共科研与接收','B1：恢复供电 / 取得维修工具','B2：收容警报 / 调查实验记录','B3：切断供能 / 核心实验事件']):c.text(rx,1310+i*48,t,22,PURPLE if i>1 else MUTED)
    c.text(64,1620,'A—A 剖线穿过中庭、北侧研究房和南侧入口；四角研究翼、温室及门路详见六层平面。',27,MUTED)
    c.text(64,1680,'实验大厅预留后续战斗空间，尚未实现首领；门禁、供电和警报均为拟定机制。',25,MUTED)
    c.text(64,1740,'设计版 01  ·  2026-11-04  ·  与 layout.json 同源；不包含自然生成或已建成的游戏结构。',24,MUTED)
    c.save('04_section')


if __name__=='__main__':
    data=schedule();result=validate(data)
    record={'status':'design','coordinate_system':'X east, Z south, Y relative to G1 floor','site':[120,104],
            'main_envelope':[104,80],'building_origin_xz':[8,8],'ground_y':0,'roof_y':21,'atrium_top_y':26,'foundation_y':-28,
            'atrium_xz':[40,36,80,60],'circulation_cores':[{'name':n,'bounds_xz':r} for n,r in CORES],
            'floor_to_floor':{'G1':7,'G2':7,'G3':7,'B1':7,'B2':7,'B3':11},'floors':data,
            'limits':['Concept art is illustrative; these coordinates govern the build plan.',
                      '2D connectivity does not validate Minecraft navigation, stair geometry or terrain generation.',
                      'Furniture, wall thickness details, structural modules and gameplay are not yet implemented.']}
    (OUT/'layout.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (OUT/'layout_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    plans(data);section();print(json.dumps(result,ensure_ascii=False))
