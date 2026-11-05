"""Draw from the definitions exported by successful native tests, rather than a second layout implementation."""
from pathlib import Path
import collections, html, json
from PIL import Image,ImageDraw,ImageFont

BASE=Path(__file__).resolve().parents[1]
NAMES={'generator':'燃料发电机','processor':'生物质处理罐','centrifuge':'工业离心阵列'}
ROLES={'casing':('#657580','#','工业外壳'),'controller':('#9569c2','C','控制器'),'rotor':('#c38248','R','动力转轴'),'window':('#4d9cba','G','观察窗'),'energy_port':('#c9a247','E','能源接口'),'item_input':('#569566','I','物品输入'),'item_output':('#b46852','O','物品输出'),'fluid_input':('#357ab0','W','流体输入'),'fluid_output':('#8557aa','F','流体输出'),'air':('#21323b','·','留空')}

def main():
 source=BASE/'forge/build/verified-blueprints.json';data=json.loads(source.read_text('utf-8'))
 out=BASE/'docs/blueprints';out.mkdir(parents=True,exist_ok=True)
 font=lambda size:ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',size)
 for m in data['machines']:
  key=m['id'].split(':')[1];w=1100;h=700;im=Image.new('RGB',(w,h),'#14232c');draw=ImageDraw.Draw(im);svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><rect width="100%" height="100%" fill="#14232c"/>']
  def text(x,y,t,size=18,color='#dbe8ef'):
   draw.text((x,y),t,font=font(size),fill=color);svg.append(f'<text x="{x}" y="{y+size}" font-size="{size}" fill="{color}" font-family="Microsoft YaHei,sans-serif">{html.escape(t)}</text>')
  def rect(x,y,width,height,color):
   draw.rectangle((x,y,x+width,y+height),fill=color);svg.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" fill="{color}"/>')
  text(30,20,f'{NAMES[key]}  {m["width"]}×{m["height"]}×{m["length"]}',30)
  text(30,66,'逐层俯视：北侧为控制器正面；从底层向上搭建。C 所在位置为相对原点 (0,0,0)。')
  for layer in range(m['height']):
   ox=30+layer*265;oy=125;text(ox,oy-28,f'第 {layer+1} 层  相对 Y={layer-1}',20)
   for c in m['cells']:
    if c['y']!=layer-1:continue
    color,glyph,label=ROLES[c['role']];xx=ox+(c['x']+m['width']//2)*58;yy=oy+c['z']*58
    rect(xx,yy,55,55,color);text(xx+19,yy+9,glyph,26);text(xx+3,yy+39,f'{c["x"]},{c["z"]}',10)
  counts=collections.Counter(c['role'] for c in m['cells'] if c['role']!='air')
  text(30,445,'材料与接口（端口位置为必需；蓝图还支持整体四向旋转）',22)
  for i,(role,(color,glyph,name)) in enumerate((r,ROLES[r]) for r in ROLES):
   xx=30+(i%5)*211;yy=490+(i//5)*75;rect(xx,yy,25,25,color);text(xx+34,yy-1,f'{glyph} {name}',17)
   text(xx+34,yy+26,'× '+str(counts[role]) if role!='air' else '内部保持空置',15)
  text(30,652,'图纸来自 Java 注册表的原生测试导出；机器配方、管线与操作说明见附属 README。',17)
  svg.append('</svg>');(out/f'{key}.svg').write_text('\n'.join(svg),'utf-8');im.save(out/f'{key}.png')
 (out/'definitions.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
 print('Rendered three exact blueprints from native definitions')
if __name__=='__main__':main()
