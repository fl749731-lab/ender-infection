from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math,numpy as np
from build_pack import ROOT
from modelkit import prepare_render
from render_models import render

def main():
    records=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'))['models'];entries=[
        ('crystal_hunter','walk','追猎者：手臂与双腿'),('ravager_siege_concept','attack','攻城兽：蓄力与下砸'),
        ('parasitic_spider_mother','walk','蛛母：八足步态'),('lab_cabinet_eroded','open_1','储柜：柜门开合'),
        ('crystal_incubator_eroded','pulse','培养舱：标本脉动'),('leapblade_ambusher','attack','伏击者：双刃攻击')]
    width=320;height=300;count=20;prepared=[];textures=[];fixes=[];font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',16)
    forward=np.array([1.,.8,-1.]);forward/=np.linalg.norm(forward);right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
    for key,clip,label in entries:
        rec=next(r for r in records if r['id']==key);p=ROOT/rec['file'];d=json.loads(p.read_text(encoding='utf-8'));texture=Image.open(p.with_suffix('.png')).convert('RGBA');a=next(a for a in d['animations'] if a['name'].endswith('.'+clip));frames=[];vertices=[]
        for i in range(count):
            phase=i/count
            t=a['length']*(1-abs(phase*2-1)) if key=='lab_cabinet_eroded' else (phase*2)%a['length'] if a['loop']=='loop' else phase*a['length']
            posed=prepare_render(d,(a,t));frames.append(posed)
            for e in posed['elements']:
                points=np.array([[x,y,z] for x in (e['from'][0],e['to'][0]) for y in (e['from'][1],e['to'][1]) for z in (e['from'][2],e['to'][2])]);m=np.asarray(e['_pose']);vertices.extend(points@m[:3,:3].T+m[:3,3])
        vertices=np.asarray(vertices);projected=np.column_stack((vertices@right,-vertices@up));lo=projected.min(axis=0);hi=projected.max(axis=0);scale=min((width-40)/max(1,hi[0]-lo[0]),(height-35)/max(1,hi[1]-lo[1]));offset=np.array([width/2,height/2])-(hi+lo)*scale/2
        prepared.append(frames);textures.append(texture);fixes.append((scale,offset))
    output=[]
    for i in range(count):
        sheet=Image.new('RGB',(960,700),(32,32,40));draw=ImageDraw.Draw(sheet);draw.text((16,10),'实际 .bbmodel 关键帧预览 · 非游戏截图',font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',22),fill='#e7dcf1')
        for j,(_,_,label) in enumerate(entries):
            x=j%3*320;y=45+j//3*325;view=render(prepared[j][i],textures[j],width,height,fixed=fixes[j]);sheet.paste(view,(x,y),view);draw.text((x+12,y+300),label,font=font,fill='#c9bdcf')
        output.append(sheet)
    output[0].save(ROOT/'previews'/'model_animation_check.gif',save_all=True,append_images=output[1:],duration=100,loop=0,optimize=False)
    print('Saved model_animation_check.gif',flush=True)
if __name__=='__main__':main()
