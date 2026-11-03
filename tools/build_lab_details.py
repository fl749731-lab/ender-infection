"""Original editable small laboratory props, with native-density texture atlases."""
import sys,json
from pathlib import Path
from design_catalog import ROOT,PACK,ASSETS,DATA,PROJECT
sys.path.insert(0,str(PACK/'source'))
from modelkit import Model,PALETTES
from export_design_assets import obj,write
PALETTES['coolant']=['#285359','#3d6b70','#54888a','#77aaaa']
DETAILS={
 'lab_monitor':'实验室监视器','lab_sample_rack':'试管样本架','lab_shelf':'实验室货架',
 'lab_pipe':'实验室管线','lab_light_panel':'嵌入式灯板','lab_chair':'实验室座椅',
 'lab_sink':'清洗槽','lab_storage_crate':'实验室器材箱','lab_freezer':'样本冷柜','lab_floor_grate':'通风地格','lab_access_ladder':'金属检修梯','lab_lift':'实验室升降平台'}
COMPONENTS={'lab_monitor':'glass_pane','lab_sample_rack':'glass_bottle','lab_shelf':'chest','lab_pipe':'copper_ingot','lab_light_panel':'glowstone_dust','lab_chair':'leather','lab_sink':'water_bucket','lab_storage_crate':'barrel','lab_freezer':'snowball','lab_floor_grate':'iron_bars','lab_access_ladder':'ladder','lab_lift':'ender_pearl'}
DEST=ROOT/'colored_units/lab_details_v3'
def model(key):return Model(key,DETAILS[key],'laboratory','功能房间细节 / 原创可编辑摆件')
def box(m,name,a,b,mat='steel'):m.box(name,a,b,mat)
def models():
 out=[]
 m=model('lab_monitor');box(m,'housing',(-7,4,1),(7,15,4),'darkmetal');box(m,'screen',(-5.8,6,.7),(5.8,13.8,1),'monitor');box(m,'status',(-5,4.8,.6),(-2,5.5,.9),'coolant');box(m,'bracket',(-2,5,4),(2,11,7),'steel');out.append(m)
 m=model('lab_sample_rack')
 for y in (1,6):box(m,'rack_shelf',(-6,y,-4),(6,y+.8,4),'white')
 for x in (-5.5,5):box(m,'rack_post',(x,1,-3.5),(x+.5,7,3.5))
 for x in (-4,0,4):
  for z in (-2,2):
   box(m,'glass_tube',(x-.7,2,z-.7),(x+.7,8,z+.7),'glass');box(m,'sample',(x-.45,2.5,z-.45),(x+.45,6.5,z+.45),'liquid' if x==0 else 'coolant');box(m,'cap',(x-.9,8,z-.9),(x+.9,8.8,z+.9),'darkmetal')
 out.append(m)
 m=model('lab_shelf')
 for x in (-7,6):
  for z in (-5,4):box(m,'upright',(x,0,z),(x+1,16,z+1),'darkmetal')
 for y in (1,7,14):box(m,'shelf',(-7,y,-5),(7,y+.7,5))
 for x in (-4,2):
  for y in (1.8,7.8):box(m,'stored_case',(x,y,-3),(x+3.5,y+3.8,3),'pad');box(m,'label',(x+.5,y+1.5,-3.15),(x+2,y+2,-3),'white')
 out.append(m)
 m=model('lab_pipe');box(m,'pipe',(-8,11,-2),(8,15,2),'steel');box(m,'seam',(-8,14.8,-.5),(8,15.3,.5),'darkmetal')
 for x in (-6,5):box(m,'clamp',(x,10,-3),(x+1,16,3),'darkmetal');box(m,'bolt',(x,12,-3.3),(x+1,13,-3),'white')
 box(m,'valve',(-1,8,-4),(1,12,-2),'warning');out.append(m)
 m=model('lab_light_panel');box(m,'rim',(-8,14,-8),(8,16,8),'darkmetal');box(m,'diffuser',(-6.5,13.8,-6.5),(6.5,14.1,6.5),'white')
 for x in (-4,0,4):box(m,'light_strip',(x-.6,13.6,-6),(x+.6,13.9,6),'coolant')
 out.append(m)
 m=model('lab_chair')
 for x in (-4,3):
  for z in (-4,3):box(m,'leg',(x,0,z),(x+1,7,z+1),'darkmetal')
 box(m,'seat',(-5,6,-5),(5,8,5),'pad');box(m,'back',(-5,8,3),(5,16,5),'pad');box(m,'back_trim',(-4,15,2.7),(4,15.5,3),'steel');out.append(m)
 m=model('lab_sink');box(m,'cabinet',(-7,0,-6),(7,11,6),'white');box(m,'basin',(-6.5,10,-5.5),(6.5,10.5,5.5),'darkmetal')
 for a,b in [((-8,11,-7),(-5,13,7)),((5,11,-7),(8,13,7)),((-5,11,-7),(5,13,-4)),((-5,11,4),(5,13,7))]:box(m,'rim',a,b,'steel')
 box(m,'water',(-4.8,10.6,-3.8),(4.8,10.8,3.8),'coolant');box(m,'tap_stem',(-.6,12,4.8),(.6,16,6));box(m,'tap',(-.6,15,1.5),(.6,16,5));box(m,'handle',(3,5,-6.3),(5,5.8,-6),'darkmetal');out.append(m)
 m=model('lab_storage_crate');box(m,'case',(-7,0,-7),(7,13,7),'darkmetal')
 for x in (-7,-.6,5.8):box(m,'strap',(x,.5,-7.3),(x+1.2,13,7.3),'steel')
 box(m,'lid',(-7.5,13,-7.5),(7.5,14.5,7.5),'steel');box(m,'handle',(-2,6,-7.8),(2,7,-7.4),'black');box(m,'label',(-4,9,-7.4),(-1,11,-7.2),'white');out.append(m)
 m=model('lab_freezer');box(m,'cabinet',(-7,0,-6),(7,16,6),'white');box(m,'seam',(-6,7.7,-6.2),(6,8.3,-6),'darkmetal')
 for y in (1,9):
  box(m,'front_panel',(-6,y,-6.4),(6,y+5.8,-6.1),'steel');box(m,'handle',(3,y+2,-7),(5,y+2.8,-6.5),'darkmetal');box(m,'sample_label',(-4,y+3,-6.6),(-1,y+4,-6.45),'coolant')
 out.append(m)
 m=model('lab_floor_grate')
 for a,b in [((-8,13,-8),(-6,16,8)),((6,13,-8),(8,16,8)),((-6,13,-8),(6,16,-6)),((-6,13,6),(6,16,8))]:box(m,'frame',a,b,'darkmetal')
 for x in range(-5,6,2):box(m,'bar',(x,14,-6),(x+.8,15.5,6),'steel')
 out.append(m)
 m=model('lab_access_ladder')
 for x in (-6,5):box(m,'steel_rail',(x,0,6),(x+1,16,8),'steel')
 for y in (1,4,7,10,13):box(m,'rung',(-6,y,5.6),(6,y+1.2,7),'steel')
 for x in (-6,5):box(m,'lamp',(x,14.5,5.3),(x+1,15.5,5.7),'coolant')
 out.append(m)
 m=model('lab_lift');box(m,'base',(-8,0,-8),(8,16,8),'darkmetal');box(m,'platform',(-6,15.8,-6),(6,16.1,6),'steel')
 for x in (-6,5):box(m,'edge_light',(x,16,-5),(x+1,16.2,5),'coolant')
 for z in (-6,5):box(m,'edge_light',(-5,16,z),(5,16.2,z+1),'coolant')
 box(m,'direction_indicator',(-2,16.15,-2),(2,16.3,2),'warning');out.append(m)
 return out

def export_details():
 report=[]
 for m in models():
  d,im,r=m.save(DEST);r['file']=str(Path(r['file']).relative_to(DEST));report.append(r)
  baked=obj(m.key,d,'block',normalize=False);baked['render_type']='minecraft:translucent' if m.key=='lab_sample_rack' else 'minecraft:cutout';write(ASSETS/'models/block'/f'{m.key}.json',baked)
  write(ASSETS/'blockstates'/f'{m.key}.json',{'variants':{f'facing={f}':{'model':f'ender_infection:block/{m.key}','y':angle} for f,angle in [('north',0),('east',90),('south',180),('west',270)]}} if m.key!='lab_lift' else {'variants':{'':{'model':f'ender_infection:block/{m.key}'}}})
  obj(m.key,d,'item')
  write(DATA/'loot_tables/blocks'/f'{m.key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ender_infection:'+m.key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
  write(DATA/'recipes'/f'{m.key}.json',{'type':'minecraft:crafting_shaped','category':'building','pattern':['IRI','IQI'],'key':{'I':{'item':'minecraft:iron_nugget'},'R':{'item':'minecraft:redstone'},'Q':{'item':'minecraft:'+COMPONENTS[m.key]}},'result':{'item':'ender_infection:'+m.key}})
 for lang in ('zh_cn','en_us'):
  p=ASSETS/'lang'/f'{lang}.json';data=json.loads(p.read_text('utf-8'));data.update({'block.ender_infection.'+k:v if lang=='zh_cn' else k.replace('_',' ').title() for k,v in DETAILS.items()});write(p,data)
 tag=PROJECT/'src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json';data=json.loads(tag.read_text('utf-8'));data['values']=sorted(set(data['values'])|{'ender_infection:'+k for k in DETAILS});write(tag,data)
 write(PROJECT/'src/main/resources/data/minecraft/tags/blocks/climbable.json',{'replace':False,'values':['ender_infection:lab_access_ladder']})
 for lang,texts in [('zh_cn',['已到最低层。','目标平台被堵住，无法升降。']),('en_us',['Lowest landing reached.','Destination landing is obstructed.'])]:
  p=ASSETS/'lang'/f'{lang}.json';d=json.loads(p.read_text('utf-8'));d.update(dict(zip(['message.ender_infection.lift_end','message.ender_infection.lift_blocked'],texts)));write(p,d)
 write(DEST/'manifest.json',{'units_per_block':16,'models':report,'count':len(report),'source':'original room detail props'})
 (DEST/'README.md').write_text('# 实验室细节模型\n\n12 个原创可编辑 `.bbmodel`；每个方块 16 模型单位，沿用实验室材料与逐面 UV。由 `tools/build_lab_details.py` 生成并导出游戏烘焙模型。\n',encoding='utf-8')
 print('Exported',len(report),'original laboratory detail models')
if __name__=='__main__':export_details()
