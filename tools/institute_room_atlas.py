"""Draw the complete room-by-room construction atlas from exact placements."""
import argparse,base64,csv,io,json,math,sys
from collections import Counter,defaultdict
from html import escape
from pathlib import Path
from PIL import Image,ImageDraw

from institute_room_spec import OUT,ROOT,LAYOUT,PALETTES,FIXTURES,generate,full
from institute_assets import Library,label
from design_central_research_institute import Canvas,INK,MUTED,CYAN,PURPLE
from PIL import ImageFont

FONTS={}
EXTRA=['ender_infection:lab_stair','ender_infection:lab_slab','ender_infection:lab_lift','ender_infection:lab_access_ladder','minecraft:iron_bars']


def concept_images(metadata=None):
    """Resolve portable source metadata; legacy author paths are never required."""
    path=Path(metadata) if metadata else OUT/'concepts/provenance.json'
    if not path.is_file():path=OUT/'concepts/generated_images.json'
    records=[]
    for row in json.loads(path.read_text('utf-8')):
        relative=row['file'] if 'file' in row else f"concepts/{row['sheet']}.png"
        records.append(dict(sheet=Path(relative).stem,rooms=row['rooms'],destination=OUT/relative))
    return records


class Sheet(Canvas):
    def text(self,x,y,s,size=24,color=INK,bold=False,anchor='left'):
        key=(size,bold)
        if key not in FONTS:FONTS[key]=ImageFont.truetype('C:/Windows/Fonts/'+('msyhbd.ttc' if bold else 'msyh.ttc'),size)
        self.d.text((x,y),s,font=FONTS[key],fill=color,anchor={'left':'lm','center':'mm','right':'rm'}[anchor])
        self.parts.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="Microsoft YaHei,Noto Sans CJK SC,sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" text-anchor="'+{'left':'start','center':'middle','right':'end'}[anchor]+f'" dominant-baseline="central">{escape(str(s))}</text>')
    def image(self,path,x,y,w,h,crop=None):
        im=Image.open(path).convert('RGBA') if isinstance(path,(Path,str)) else path.convert('RGBA')
        if crop:im=im.crop([round(v*n) for v,n in zip(crop,[im.width,im.height,im.width,im.height])])
        scale=min(w/im.width,h/im.height);W=round(im.width*scale);H=round(im.height*scale);x+=round((w-W)/2);y+=round((h-H)/2)
        im=im.resize((W,H),Image.Resampling.LANCZOS);self.im.paste(im,(round(x),round(y)),im)
        stream=io.BytesIO();im.save(stream,format='PNG');encoded=base64.b64encode(stream.getvalue()).decode()
        self.parts.append(f'<image x="{x}" y="{y}" width="{W}" height="{H}" href="data:image/png;base64,{encoded}"/>')
    def save_to(self,path,svg=True):
        path.parent.mkdir(parents=True,exist_ok=True);self.im.save(path)
        if svg:path.with_suffix('.svg').write_text('\n'.join(self.parts+['</svg>']),encoding='utf-8')


def shortname(block):return label(block).replace(' · 完好','').replace(' · 侵蚀','（侵蚀）')


def color(block):
    if any(t in block for t in ('amethyst','eroded','purple')):return '#D9B8EB'
    if any(t in block for t in ('glass','incubator','ice')):return '#CBE7ED'
    if any(t in block for t in ('bed','surgery','sink')):return '#EDD6D4'
    if any(t in block for t in ('fern','azalea','mushroom','grass','mycelium')):return '#CBDDBB'
    if any(t in block for t in ('chair','monitor','lectern')):return '#BDCEDC'
    if any(t in block for t in ('shelf','crate','barrel','cabinet','bookshelf')):return '#DDD6C7'
    return '#D6DDE1'


def heading(c,title,subtitle):
    c.text(60,62,title,48,bold=True);c.text(62,128,subtitle,24,MUTED)
    c.line([(60,174),(3140,174)],'#CFDADF',2)


def footer(c,s):
    c.text(60,c.h-48,s,22,MUTED)


def plan(c,r,x,y,maxw,maxh,codes,mini=False):
    w,d=r['size'];q=min(maxw/w,maxh/d);ox=x+(maxw-w*q)/2;oy=y+(maxh-d*q)/2
    def box(u,v,U,V,fill,stroke='#BBC8CD',width=1):c.rect((ox+u*q,oy+v*q,ox+U*q,oy+V*q),fill,stroke,width)
    box(0,0,w,d,'#F8FAFA',INK,3)
    # Two-block main circulation lanes remain visible beneath the furniture.
    if r['id']!='F8':
        box(w//2-1,1,w//2+1,d-1,'#E8F2F5',None);box(1,d//2-1,w-1,d//2+1,'#E8F2F5',None)
    stacks=defaultdict(list)
    for p in r['placements']:
        if p['layer'] in ('ceiling','lighting','overhead','floor','floor_detail'):continue
        for u,v,h in p['reserved']:
            if p['layer']=='wall':
                if h==1:box(u,v,u+1,v+1,'#627683','#FFFFFF',1)
            else:stacks[u,v].append(p)
    for u in range(w+1):c.line([(ox+u*q,oy),(ox+u*q,oy+d*q)],'#C8D2D6',1)
    for v in range(d+1):c.line([(ox,oy+v*q),(ox+w*q,oy+v*q)],'#C8D2D6',1)
    for (u,v),items in stacks.items():
        items=sorted({p['seq']:p for p in items}.values(),key=lambda p:-p['h']);top=items[0]
        box(u+.06,v+.06,u+.94,v+.94,color(top['block']),INK,1)
        if not mini:
            unique=[]
            for p in items:
                code=codes[p['block']]
                if code not in unique:unique.append(code)
            font=max(10,min(15,round(q*.27)))
            for i,code in enumerate(unique[:3]):c.text(ox+(u+.5)*q,oy+(v+.5)*q+(i-(len(unique[:3])-1)/2)*(font+1),code,font,INK,False,'center')
            # Anchor dots distinguish multi-cell props from automatically filled parts.
            if top['u']==u and top['v']==v:
                c.rect((ox+(u+.78)*q,oy+(v+.76)*q,ox+(u+.90)*q,oy+(v+.89)*q),CYAN)
            face=top['state'].get('facing')
            if face in ('north','south','east','west') and q>30:
                dx,dz={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}[face]
                c.line([(ox+(u+.16)*q,oy+(v+.18)*q),(ox+(u+.16+dx*.10)*q,oy+(v+.18+dz*.10)*q)],CYAN,2)
    if r['id']=='F8':
        for core in LAYOUT['circulation_cores']:
            a,b,A,B=core['bounds_xz'];a-=r['origin_xz'][0];A-=r['origin_xz'][0];b-=r['origin_xz'][1];B-=r['origin_xz'][1]
            box(a,b,A,B,'#DDEEF2',CYAN,2)
            if not mini:c.text(ox+(a+A)/2*q,oy+(b+B)/2*q,core['name'],18,CYAN,True,'center')
    for m in r['modules']:
        u,v=m['origin'];W,D=m['size'];c.line([(ox+u*q,oy+v*q),(ox+(u+W)*q,oy+v*q),(ox+(u+W)*q,oy+(v+D)*q),(ox+u*q,oy+(v+D)*q),(ox+u*q,oy+v*q)],'#B58741',2,True)
        if not mini:c.text(ox+u*q+3,oy+v*q-13,m['id'],18,'#986929',True)
    if not mini:
        for u in range(w):c.text(ox+(u+.5)*q,oy-20,str(u),15,MUTED,False,'center')
        for v in range(d):c.text(ox-16,oy+(v+.5)*q,str(v),15,MUTED,False,'right')
        c.text(ox,oy-55,'U → 东  /  V ↓ 南    每小格 1 个方块',22,CYAN,True)
    return ox,oy,q


def ceiling(c,r,x,y,w,h):
    W,D=r['size'];q=min((w-70)/W,(h-100)/D);ox=x+45;oy=y+75
    c.text(x+20,y+20,f'吊顶图  |  灯 H{r["clear_height"]} / 管 H{r["clear_height"]-1}',23,bold=True)
    c.rect((ox,oy,ox+W*q,oy+D*q),'#FFFFFF',INK,2)
    for u in range(W+1):c.line([(ox+u*q,oy),(ox+u*q,oy+D*q)],'#D3DDE1',1)
    for v in range(D+1):c.line([(ox,oy+v*q),(ox+W*q,oy+v*q)],'#D3DDE1',1)
    for p in r['placements']:
        if p['layer'] not in ('overhead','lighting'):continue
        u,v=p['u'],p['v'];c.rect((ox+u*q,oy+v*q,ox+(u+1)*q,oy+(v+1)*q),'#F1D690' if p['layer']=='lighting' else '#A9C2CF',INK,1)
    for u in range(0,W,4):c.text(ox+(u+.5)*q,oy-19,str(u),13,MUTED,False,'center')
    for v in range(0,D,4):c.text(ox-12,oy+(v+.5)*q,str(v),13,MUTED,False,'right')


def elevation(c,r,x,y,w,h,codes):
    W,D=r['size'];H=r['ceiling_h']+1;q=min((w-85)/W,(h-80)/H);ox=x+52;oy=y+56
    c.text(x+20,y+16,'北墙立面  |  从房内朝北看；横轴仍为 U',23,bold=True)
    cells={}
    for p in r['placements']:
        for u,v,z in p['reserved']:
            if v==0:cells[u,z]=p
    for (u,z),p in cells.items():
        col=color(p['block']) if p['layer']!='wall' else '#D8E1E5' if z!=4 else '#BFCBD1'
        c.rect((ox+u*q,oy+(H-1-z)*q,ox+(u+1)*q,oy+(H-z)*q),col,'#9FB0B9',1)
    for z in range(H):c.text(ox-12,oy+(H-.5-z)*q,str(z),14,MUTED,False,'right')
    for u in range(0,W,4):c.text(ox+(u+.5)*q,oy+H*q+16,str(u),14,MUTED,False,'center')


def room_sheet(r,lib,codes,allow_missing=False):
    c=Sheet(3200,2400);w,d=r['size']
    heading(c,f'{r["id"]}  {r["name"]}',f'{r["level"]}  |  外包 {w}×{d} 格，净高 {r["clear_height"]} 格  |  本室原点：建筑 X={r["origin_xz"][0]}，Z={r["origin_xz"][1]}，相对 Y={r["floor_y"]:+d}')
    c.text(70,212,'逐格内饰平面',30,bold=True);plan(c,r,130,305,1370,1170,codes)
    c.text(70,1540,'同一格内的编号由上到下排列；蓝点为多格家具锚点，虚框为设备组合。',21,MUTED)
    c.text(1670,212,'室内细节概念图  /  图像生成',30,bold=True)
    manifest=concept_images()
    info=next((g for g in manifest if r['id'] in g['rooms']),None)
    shot=Path(info['destination']) if info else OUT/'missing.png'
    if shot.exists():
        index=info['rooms'].index(r['id']);crop=[(index%2)*.5,(index//2)*.5,(index%2+1)*.5,(index//2+1)*.5]
        if info['sheet']=='G1_A5-A8':crop=[[0,0,.626,.49],[.626,0,1,.49],[0,.49,.5,1],[.5,.49,1,1]][index]
        c.image(shot,1650,270,1490,1050,crop)
    elif allow_missing:c.text(2390,700,'透视图待 MCP 截图',32,MUTED,False,'center')
    else:raise FileNotFoundError(shot)
    c.text(1670,1345,'右图表达陈设细节；左图与 CSV 为现有方块的基础摆放。小器具未全部建模，见落地对照页。',20,MUTED)
    c.text(1670,1410,'组合定位  /  原点为该组合包络的西北角',25,bold=True)
    for i,m in enumerate(r['modules']):
        yy=1465+i*48;c.text(1670,yy,m['id'],23,CYAN,True)
        c.text(1760,yy,m['name'],23)
        c.text(3120,yy,f'U{m["origin"][0]} V{m["origin"][1]}  /  {m["turn"]*90}°',22,MUTED,False,'right')
    ceiling(c,r,65,1650,520,565);elevation(c,r,620,1650,930,355,codes)
    roles=[('地板',r['palette']['floor'],'H0；边带另用 '+codes[full(r['palette']['border'])]),('墙裙',r['palette']['border'],'H1'),('墙面',r['palette']['wall'],f'H2-H{r["clear_height"]}，H4 改导向带'),('导向带',r['palette']['stripe'],'H4'),('顶板',r['palette']['ceiling'],f'H{r["ceiling_h"]}')]
    for i,(role,key,height) in enumerate(roles):
        block=full(key);yy=2050+i*45;c.image(lib.icon(block),635,yy-17,34,34)
        c.text(685,yy,f'{role}  {codes[block]}  {shortname(block)}',21)
        c.text(1530,yy,height,19,MUTED,False,'right')
    c.text(1670,1765,'本室用量  /  含外壳，多格家具按锚点物品计',24,bold=True)
    counts=sorted(r['material_counts'].items(),key=lambda kv:codes[kv[0]])
    rows=math.ceil(len(counts)/2)
    assert rows<=15,(r['id'],len(counts))
    for i,(block,count) in enumerate(counts):
        col=i//rows;row=i%rows;xx=1670+col*740;yy=1820+row*32
        c.rect((xx,yy-10,xx+20,yy+10),color(block),INK,1)
        c.text(xx+32,yy,codes[block],20,CYAN,True);c.text(xx+100,yy,shortname(block),20)
        c.text(xx+700,yy,str(count),21,INK,True,'right')
    footer(c,'坐标：建筑 X=本室原点X+U，Z=原点Z+V，相对Y=本层Y+H。完整方块ID、朝向与逐项坐标见本室 CSV。设计图，尚未放入游戏。')
    path=OUT/'rooms'/(r['id']+'.png');c.save_to(path);return path


def material_pages(lib,codes):
    files=[];items=sorted(codes,key=lambda b:codes[b])
    for page in range(math.ceil(len(items)/20)):
        c=Sheet(3200,2400);heading(c,f'方块与家具图例  {page+1}', '全部来自当前模组与 Minecraft 1.20.1；方块ID可直接用于物品搜索或后续结构摆放。')
        for i,block in enumerate(items[page*20:(page+1)*20]):
            xx=65+(i%2)*1550;yy=250+(i//2)*198
            c.rect((xx,yy,xx+1490,yy+178),'#FFFFFF','#CFDADF',1);c.image(lib.icon(block),xx+22,yy+24,116,116)
            c.text(xx+165,yy+35,codes[block]+'  '+shortname(block),29,bold=True);c.text(xx+165,yy+85,block,22,CYAN)
            base=block.split(':')[1].replace('_eroded','');size=FIXTURES.get(base,(1,1))
            note=f'占位：横向 {size[0]} × 深 1 × 高 {size[1]} 格；只摆锚点，其余部分自动生成。' if block.startswith('ender_infection:') and base in FIXTURES else '单格方块；堆叠高度与朝向以房间图和 CSV 为准。'
            if block.endswith('_bed'):note='原版床占 1×2 格；床尾为锚点，床头自动生成。模型透视中的床形为简化示意。'
            c.text(xx+165,yy+132,note,21,MUTED)
        footer(c,'图例编号 Bxx 在所有房间中保持一致。纹理色块是现有资产采样；概念中的细小仪器另按落地对照页区分。')
        f=OUT/'guides'/f'materials_{page+1:02d}.png';c.save_to(f);files.append(f)
    return files


def reader():
    c=Sheet(3200,2400);heading(c,'中央研究所  /  怎么照图摆放','120×104 格园区 · 六层 · 48 个编号房间 · 所有方块位置来自同一份摆放表')
    sections=[('01 先确定建筑基准','本图的 X/Z 是建筑坐标，不是玩家世界绝对坐标。G1 地板相对Y为0。实际搭建时，全部坐标统一加上你选择的建筑原点。'),
     ('02 看房间原点与局部网格','每室西北角为 U=0、V=0。U 向东增加，V 向南增加，H 表示该块底面相对于本室地板方块的高度。H0 本身是地板块。'),
     ('03 从地板向上摆','先摆 H0 地板与边带，再摆墙面、窗和门；之后按 H1、H2、H3 的顺序摆家具，最后装顶管、灯板和顶板。'),
     ('04 识别同格堆叠','平面每格的 Bxx 编号从上往下列出该列物件；精确 H 值与 facing 在房间 CSV 中。M01 等虚框只表示设备组合范围，不是墙。'),
     ('05 多格家具只放锚点','蓝点标识家具锚点。3格宽的工作台、观察窗和手术台会自动占用横向相邻格；培养舱自动占高3格。不要再手摆 lab_fixture_part。'),
     ('06 保持主通道','普通房间中央保留两格宽十字通道；门前同样留空。F8 按专门布局绕过反应装置，西梯、东梯和货运井区域全部留出。'),
     ('07 理解朝向','facing 是方块状态，不是玩家看向。north 朝北、south 朝南、east 朝东、west 朝西。上下朝向只用于紫晶簇等原版方块。'),
     ('08 看细节、再核坐标','室内细节图使用图像生成，展示较丰富的陈设目标。逐格图和CSV提供当前方块的基础搭建方案；部分小仪器还需新模型，不是现成游戏方块。')]
    for i,(title,body) in enumerate(sections):
        yy=250+i*235;c.text(80,yy,title,32,CYAN,True)
        # Deliberately wrap on a stable Chinese character budget.
        for j in range(0,len(body),91):c.text(90,yy+68+(j//91)*44,body[j:j+91],27)
    c.text(80,2220,'文件：rooms/ 逐室图 · floors/ 逐层图 · concepts/ 细节图 · csv/ 逐块坐标 · room_placements.json 同源数据',25,bold=True)
    footer(c,'当前交付为摆放设计图册，不代表已生成游戏建筑、自然生成或供电玩法。')
    f=OUT/'guides/00_reading_guide.png';c.save_to(f);return f


def core_guide(codes):
    c=Sheet(3200,2400);heading(c,'公共交通模块  /  楼梯、货运井与中庭','以下采用现有实验室楼梯、升降平台和金属检修梯；每层交通核心在原平面坐标上对位。')
    for index,rise in enumerate((7,11)):
        xx=90+index*1550;yy=285;first=4 if rise==7 else 6
        c.text(xx,yy,f'{rise} 格层高的折返楼梯',35,CYAN,True)
        c.text(xx,yy+65,'局部 U=0-5，V=0-7；左跑向南上，右跑向北上。',25)
        q=105;px=xx+150;py=yy+140
        for u in range(6):
            for v in range(8):
                c.rect((px+u*q,py+v*q,px+(u+1)*q,py+(v+1)*q),'#FFFFFF','#BAC8CD',2)
        for step in range(1,first+1):
            for u in (0,1):c.rect((px+u*q,py+step*q,px+(u+1)*q,py+(step+1)*q),'#D4E8EF',INK,2);c.text(px+(u+.5)*q,py+(step+.5)*q,'H'+str(step),25,INK,False,'center')
        landing_v=first+1
        for u in range(6):
            c.rect((px+u*q,py+landing_v*q,px+(u+1)*q,py+(landing_v+1)*q),'#E9D9AE',INK,2);c.text(px+(u+.5)*q,py+(landing_v+.5)*q,'H'+str(first),22,INK,False,'center')
        for i in range(rise-first):
            v=first-i;h=first+1+i
            for u in (4,5):c.rect((px+u*q,py+v*q,px+(u+1)*q,py+(v+1)*q),'#C3D9E4',INK,2);c.text(px+(u+.5)*q,py+(v+.5)*q,'H'+str(h),25,INK,False,'center')
        c.text(xx,yy+1060,f'楼梯 {codes["ender_infection:lab_stair"]}：左跑 facing=south，右跑 facing=north。',24)
        c.text(xx,yy+1110,'平台用 '+codes['ender_infection:lab_tile_dark']+'；顶端接上层地板。',24)
        c.text(xx,yy+1160,'两侧保留 2 格宽梯道，中间留空；护栏不得侵入踏步。',24,MUTED)
    c.text(90,1640,'建筑坐标中的交通位置',33,bold=True)
    for i,core in enumerate(LAYOUT['circulation_cores']):
        c.text(100,1710+i*60,core['name']+'：X/Z 包络 '+str(core['bounds_xz'])+'（后两个坐标不含）',28)
    c.text(90,1950,'货运井：在各层同一 X/Z 的 H0 放 '+codes['ender_infection:lab_lift']+' 升降平台，平台上方保留两格净空。',27)
    c.text(90,2010,'现有升降平台右键到上层，潜行右键到下层；同一竖线需要存在目标平台。检修梯用 '+codes['ender_infection:lab_access_ladder']+'。',26)
    c.text(90,2080,'地上中庭为通高空间：G2/G3 中央挑空区不得铺普通房间地板；B2 观察区用完整玻璃封闭楼板。',27,PURPLE)
    c.text(90,2140,'楼梯示意只列踏步与平台。生成结构前仍需实机检查护栏、净空、楼板开口和下行连接。',25,MUTED)
    footer(c,'普通层升高7格；B3 到 B2 升高11格。踏步 H 相对于较低一层的地板方块。')
    f=OUT/'guides/04_circulation.png';c.save_to(f);return f


def compatibility(codes):
    c=Sheet(3200,2400);heading(c,'概念细节与现有方块  /  落地对照','现有方块可直接摆放；组合装置是静态造型；待补小模型暂时没有独立物品。')
    entries=[
      ('现有','实验室台柜、椅子、水槽','lab_workbench / lab_cabinet / lab_chair / lab_sink','直接使用对应 Bxx；不要寻找独立的“操作席套装”。'),
      ('现有','培养舱、手术台、观察窗','incubator / surgery_table / lab_window','多格占位按图例与锚点放置。'),
      ('现有','监视器、样本架、货架、冷柜','lab_monitor / lab_sample_rack / lab_shelf / lab_freezer','样本架自身已有试管细节。'),
      ('现有','管线、通风、灯板、储物箱','lab_pipe / lab_vent / lab_light_panel / lab_storage_crate','管线模型包含小阀件；不等于独立流体系统。'),
      ('组合','监控墙与控制席','lab_monitor + lab_freezer + lab_chair','多个监视器拼墙，柜体作操作台；没有无缝大屏方块。'),
      ('组合','服务器柜 / 配电柜','gray_concrete / iron_block + lab_monitor + lab_vent','用现有方块表现设备外观，不自带服务器或供电逻辑。'),
      ('组合','冷却箱与泵组','glass + blue_ice + iron_block + lab_pipe','冷却液外观用蓝冰隔玻璃表达；泵组为静态设备组合。'),
      ('组合','晶体反应装置','amethyst_block + sea_lantern + polished_deepslate','晶体基座加入海晶灯；不是新增的发电机方块。'),
      ('组合','种植槽、档案架与餐桌','smooth_quartz + grass_block / mycelium / bookshelf','使用原版方块与模组家具；生长、储存沿用原版规则。'),
      ('待补','独立显微镜 / 精密分析仪','暂无独立方块 ID','可先用设备工作台近似；若要求与概念一致，需要新增模型。'),
      ('待补','轮式器械推车 / 运输推车','暂无独立方块 ID','可先用器材箱与工作台作静态替代。'),
      ('待补','完整扫描安检机 / 传送带','暂无独立方块 ID','可先用铁块、玻璃和监视器组合；没有扫描功能。'),
      ('待补','软帘 / 细小纸张 / 挂墙工具组','暂无对应成套摆件','可先用玻璃隔断、讲台与货架替代；图中小件是陈设目标。'),
      ('待补','整组无缝大屏 / 独立通讯机柜','暂无对应成套方块','先使用多个现有监视器与柜体组合。')]
    for i,(status,name,ids,note) in enumerate(entries):
        yy=240+i*143;col={'现有':CYAN,'组合':'#986C25','待补':PURPLE}[status]
        c.text(80,yy,status,28,col,True);c.text(200,yy,name,28,INK,True)
        c.text(200,yy+46,ids,24,CYAN);c.text(200,yy+88,note,24,MUTED)
    footer(c,'概念图不是“所有小件已在游戏中存在”的承诺。方块完整命名空间见图例和 CSV；本页用简写便于查阅。')
    f=OUT/'guides/05_compatibility.png';c.save_to(f);return f


def floor_sheet(level,rooms,lib,codes):
    items=[r for r in rooms if r['level']==level];f=next(f for f in LAYOUT['floors'] if f['level']==level)
    c=Sheet(3200,2400);heading(c,level+'  /  整层内饰与材料',f['theme']+f'  |  本层地板相对 Y={f["relative_floor_y"]:+d}  |  每间详细家具朝向、坐标另见逐室图。')
    ox=130;oy=300;q=17
    c.rect((ox,oy,ox+120*q,oy+104*q),'#FFFFFF',INK,3)
    for x in range(0,121,4):c.line([(ox+x*q,oy),(ox+x*q,oy+104*q)],'#E0E6E9',1)
    for z in range(0,105,4):c.line([(ox,oy+z*q),(ox+120*q,oy+z*q)],'#E0E6E9',1)
    for r in items:
        x,z=r['origin_xz'];w,d=r['size'];plan(c,r,ox+x*q,oy+z*q,w*q,d*q,codes,True)
        c.text(ox+(x+w/2)*q,oy+(z-1.4)*q,r['id']+' '+r['name'],19,INK,True,'center')
    for core in LAYOUT['circulation_cores']:
        a,b,A,B=core['bounds_xz'];c.rect((ox+a*q,oy+b*q,ox+A*q,oy+B*q),'#DFEDF2',CYAN,2);c.text(ox+(a+A)/2*q,oy+(b+B)/2*q,core['name'],17,CYAN,True,'center')
    rx=2300;c.text(rx,295,'本层材料与高度',31,bold=True)
    palette=PALETTES[level];high=9 if level=='B3' else 5
    for i,(role,key,H) in enumerate([('房间地板',palette['floor'],'H0'),('墙裙 / 收边',palette['border'],'H0边带 / H1'),('室内墙面',palette['wall'],f'H2-H{high}'),('导向色带',palette['stripe'],'H4'),('吊顶',palette['ceiling'],f'H{high+1}')]):
        yy=380+i*170;block=full(key);c.image(lib.icon(block),rx,yy-32,70,70);c.text(rx+100,yy,role+' '+codes[block],27,CYAN,True);c.text(rx+100,yy+50,shortname(block)+' / '+H,24)
    c.text(rx,1295,'灯光与管线',31,bold=True)
    for i,t in enumerate([f'灯板：{codes["ender_infection:lab_light_panel"]}，H{high}',f'顶管：{codes["ender_infection:lab_pipe"]}，H{high-1}', '房间灯位按局部 U/V 网格每6格布置', '具体灯位见各室吊顶图与 CSV', '培养舱与晶体展示箱单列照明方块']):c.text(rx,1370+i*57,t,24,MUTED)
    c.text(rx,1710,'本层房间',31,bold=True)
    for i,r in enumerate(items):c.text(rx,1770+i*48,r['id']+' '+r['name'],24)
    footer(c,'整层图用于确定位置关系与材质，具体摆放请打开对应房间图。公共走廊净宽4格；房内主通道净宽2格。')
    path=OUT/'floors'/(level+'.png');c.save_to(path);return path


def csv_files(rooms,codes):
    (OUT/'csv').mkdir(exist_ok=True);headers=['room','level','U','H','V','building_X','relative_Y','building_Z','code','block_id','facing','state','module','manual_item','automatic_reservation_UVH']
    all_rows=[]
    for r in rooms:
        rows=[]
        for p in r['placements']:
            rows.append([r['id'],r['level'],p['u'],p['h'],p['v'],r['origin_xz'][0]+p['u'],r['floor_y']+p['h'],r['origin_xz'][1]+p['v'],codes[p['block']],p['block'],p['state'].get('facing',''),json.dumps(p['state'],ensure_ascii=False,sort_keys=True),p['module'],p['manual_item'],json.dumps(p['reserved'])])
        with (OUT/'csv'/(r['id']+'.csv')).open('w',encoding='utf-8-sig',newline='') as file:
            writer=csv.writer(file);writer.writerow(headers);writer.writerows(rows)
        all_rows+=rows
    with (OUT/'csv/ALL_ROOMS.csv').open('w',encoding='utf-8-sig',newline='') as file:
        writer=csv.writer(file);writer.writerow(headers);writer.writerows(all_rows)


def create_pdf(pages):
    sys.path.insert(0,str(ROOT/'build/diagram-deps'))
    from reportlab.pdfgen import canvas
    from reportlab.lib.pagesizes import A3,landscape
    from reportlab.lib.utils import ImageReader
    pdf=OUT/'central-institute-interior-atlas-v01.pdf';W,H=landscape(A3);c=canvas.Canvas(str(pdf),pagesize=(W,H),pageCompression=1)
    c.setTitle('中央研究所 - 六层材料与48个房间逐格摆放图册');c.setAuthor('Ender Infection design workspace')
    for i,(title,path) in enumerate(pages):
        im=Image.open(path).convert('RGB');scale=min(W/im.width,H/im.height);w=im.width*scale;h=im.height*scale
        # Preserve original full-resolution PNG/SVG drawings separately. JPEG
        # encoding keeps the illustrated PDF portable without reducing pixels.
        encoded=io.BytesIO();im.save(encoded,format='JPEG',quality=90,subsampling=0,optimize=True);encoded.seek(0)
        c.drawImage(ImageReader(encoded), (W-w)/2,(H-h)/2,width=w,height=h)
        c.bookmarkPage('p'+str(i));c.addOutlineEntry(title,'p'+str(i),level=0,closed=False)
        c.showPage()
    c.save();return pdf


def build(selected=None,pdf=False,allow_missing=False):
    rooms=generate();lib=Library();ids={p['block'] for r in rooms for p in r['placements']}|set(EXTRA)
    codes={b:'B'+str(i+1).zfill(2) for i,b in enumerate(sorted(ids))}
    for b in codes:lib.icon(b)
    (OUT/'block_catalog.json').write_text(json.dumps({b:dict(code=code,name=label(b)) for b,code in codes.items()},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    csv_files(rooms,codes);pages=[]
    if not selected:
        pages+=[('使用说明',reader())];pages += [('方块图例 '+str(i+1),p) for i,p in enumerate(material_pages(lib,codes))]
        pages += [('公共交通与楼梯',core_guide(codes))]
        pages += [('概念细节落地对照',compatibility(codes))]
    for level in PALETTES:
        if not selected:pages.append((level+' 整层材料与内饰',floor_sheet(level,rooms,lib,codes)))
        if not selected:
            for info in concept_images():
                if info['sheet'].startswith(level+'_'):pages.append((level+' 细节图 '+','.join(info['rooms']),Path(info['destination'])))
        for r in rooms:
            if r['level']==level and (not selected or r['id'] in selected):pages.append((r['id']+' '+r['name'],room_sheet(r,lib,codes,allow_missing)))
    (OUT/'page_index.json').write_text(json.dumps([{'page':i+1,'title':t,'file':p.relative_to(OUT).as_posix()} for i,(t,p) in enumerate(pages)],ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    if pdf:print('PDF',create_pdf(pages),flush=True)
    print(json.dumps({'pages':len(pages),'rooms':len(rooms),'block_ids':len(codes),'status':'COMPLETE' if not allow_missing else 'DRAFT'}),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--rooms',default='');p.add_argument('--pdf',action='store_true');p.add_argument('--allow-missing',action='store_true');a=p.parse_args()
    build(set(a.rooms.split(',')) if a.rooms else None,a.pdf,a.allow_missing)
