from pathlib import Path
import json,math
from PIL import Image,ImageDraw,ImageFont
from build_pack import ROOT
from modelkit import prepare_render
from render_models import render

FONT='C:/Windows/Fonts/msyh.ttc'
def load(path):
    d=json.loads(path.read_text(encoding='utf-8'));return d,Image.open(path.with_suffix('.png')).convert('RGBA')
def sheet(records,name,title,cols=4,cell=(380,395)):
    rows=math.ceil(len(records)/cols);im=Image.new('RGB',(cols*cell[0],90+rows*cell[1]),(32,32,40));draw=ImageDraw.Draw(im)
    draw.text((22,12),title,font=ImageFont.truetype(FONT,27),fill='#e6dcf0');draw.text((24,53),'实际 .bbmodel 几何与 UV 的离线预览 · 非概念图 / 非游戏截图',font=ImageFont.truetype(FONT,17),fill='#aaa4b5')
    for i,r in enumerate(records):
        d,t=load(ROOT/r['file']);view=render(prepare_render(d),t,cell[0]-10,cell[1]-55);x=(i%cols)*cell[0];y=90+(i//cols)*cell[1]
        im.paste(view,(x+5,y),view);draw.text((x+12,y+cell[1]-48),r['name'],font=ImageFont.truetype(FONT,18),fill='#e6dcf0')
        draw.text((x+12,y+cell[1]-24),str(r['cubes'])+' cuboids · '+str(r['atlas'][0])+'px UV',font=ImageFont.truetype(FONT,13),fill='#918b9c')
    im.save(ROOT/'previews'/name);print(name,flush=True)
def main():
    (ROOT/'previews').mkdir(exist_ok=True);records=json.loads((ROOT/'manifest.json').read_text())['models']
    sheet([r for r in records if r['category']=='monsters'],'monsters_actual.png','19 种怪物 · 实际可编辑模型')
    sheet([r for r in records if r['category'] in ('armor','weapons','loot')],'equipment_actual.png','护甲、武器与掉落物 · 实际可编辑模型')
    blocks=('white_wall','dark_wall','lab_brick','checker_floor','lab_slab','lab_stair')
    sheet([r for r in records if r['category']=='laboratory' and r['id'].startswith(blocks)],'lab_blocks_actual.png','实验室建筑方块 · 完好 / 侵蚀')
    sheet([r for r in records if r['category']=='laboratory' and not r['id'].startswith(blocks)],'lab_furniture_actual.png','实验室门窗、通风与家具 · 完好 / 侵蚀')
    for r in records:
        if r['category']=='scenes':
            d,t=load(ROOT/r['file']);im=Image.new('RGB',(1400,1170),(32,32,40));draw=ImageDraw.Draw(im)
            view=render(prepare_render(d),t,1360,1060,camera=(1,.65,-1));im.paste(view,(20,70),view)
            draw.text((25,15),r['name']+' · 实际模型预览',font=ImageFont.truetype(FONT,28),fill='#e6dcf0')
            draw.text((25,1133),'16 单位 = 1 方块；四层与屋顶均可在 Outliner 单独隐藏 / 显示',font=ImageFont.truetype(FONT,19),fill='#aaa4b5')
            im.save(ROOT/'previews'/(r['id']+'.png'));print(r['id'],flush=True)
    for key in ('ravager_siege_concept','parasitic_spider_mother','lab_cabinet_intact','crystal_incubator_eroded','surgery_table_intact'):
        r=next(r for r in records if r['id']==key);d,t=load(ROOT/r['file']);view=render(prepare_render(d),t,900,850);base=Image.new('RGB',view.size,(34,32,41));base.paste(view,(0,0),view);base.save(ROOT/'previews'/(key+'.png'))
if __name__=='__main__':main()
