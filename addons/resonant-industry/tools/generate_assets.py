"""Native block/item JSON, crafting data and layered SVG blueprints. No Blockbench dependency."""
from pathlib import Path
import json, shutil, sys

BASE=Path(__file__).resolve().parents[1]
RES=BASE/'forge/src/main/resources'
NS='resonant_industry'
ROLES=['controller','casing','window','rotor','energy_port','item_input','item_output','fluid_input','fluid_output','cable','fluid_pipe','battery','tank','pump','rotor_blades']
ZH=['机器控制器','工业外壳','观察窗','动力转轴','能源接口','物品输入仓','物品输出仓','流体输入口','流体输出口','铜芯电缆','工业流体管','储能箱','储液罐','取水泵','转子叶片']
EN=['Machine Controller','Industrial Casing','Observation Window','Drive Spindle','Energy Port','Item Inlet','Item Outlet','Fluid Inlet','Fluid Outlet','Copper Cable','Fluid Pipe','Energy Cell','Fluid Tank','Water Pump','Rotor Blades']
def write(rel,value):
 p=RES/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n','utf-8')
def cube(a,b,tex):return {'from':a,'to':b,'faces':{f:{'texture':'#'+tex} for f in ['north','south','east','west','up','down']}}
def model(role,active=False,fill=0):
 tex={'case':'minecraft:block/iron_block','edge':'minecraft:block/gray_concrete','copper':'minecraft:block/copper_block','glass':'minecraft:block/glass','violet':'minecraft:block/purpur_block','light':'minecraft:block/sea_lantern' if active else 'minecraft:block/black_concrete','gold':'minecraft:block/gold_block'}
 if role in ['cable','fluid_pipe']:
  lo,hi=(6.5,9.5) if role=='cable' else (5.5,10.5)
  elements=[cube([lo,lo,0],[hi,hi,16],'edge'),cube([lo-.5,lo-.5,1],[hi+.5,hi+.5,3],'copper'),cube([lo-.5,lo-.5,13],[hi+.5,hi+.5,15],'copper')]
 elif role=='rotor_blades':elements=[cube([2,6.5,7],[14,9.5,9],'copper'),cube([6.5,2,7],[9.5,14,9],'copper'),cube([5.5,5.5,5],[10.5,10.5,11],'violet')]
 elif role in ['window','tank']:
  elements=[cube([1,1,1],[15,15,15],'glass')]
  for x in [0,14]:elements.append(cube([x,0,0],[x+2,16,16],'edge'))
  for y in [0,14]:elements.append(cube([2,y,0],[14,y+2,16],'copper'))
  if fill>0:elements.append(cube([2.1,1,2.1],[13.9,1+fill*3.3,13.9],'violet'))
 elif role=='rotor':elements=[cube([0,0,0],[2,16,16],'edge'),cube([14,0,0],[16,16,16],'edge'),cube([2,0,0],[14,2,16],'case'),cube([2,14,0],[14,16,16],'case')]
 else:
  elements=[cube([0,0,0],[16,16,16],'case' if role=='casing' else 'edge'),cube([1,1,-.05],[15,15,.05],'case')]
  if role=='casing':elements += [cube([3,3,-.15],[13,13,-.05],'edge'),cube([4,4,-.2],[12,12,-.15],'case')]
  elif role=='controller':elements += [cube([3,5,-.2],[13,13,-.05],'edge'),cube([4,7,-.3],[12,8,-.2],'light'),cube([4,10,-.3],[12,11,-.2],'light'),cube([4,2,-.3],[7,4,-.05],'violet'),cube([9,2,-.3],[12,4,-.05],'copper')]
  elif role in ['item_input','item_output']:elements += [cube([3,4,-.2],[13,12,-.05],'edge'),cube([4,5,-.3],[12,7,-.2],'copper'),cube([6,7,-.3],[10,11,-.2],'gold' if role=='item_output' else 'case')]
  elif role in ['energy_port','fluid_input','fluid_output']:elements += [cube([3,3,-.3],[13,13,-.05],'gold' if role=='energy_port' else 'copper'),cube([5,5,-.4],[11,11,-.3],'violet' if role=='energy_port' else 'edge')]
  elif role=='battery':elements += [cube([3,2,-.2],[13,14,-.05],'violet'),cube([5,3,-.3],[11,13,-.2],'light')]
  else:elements += [cube([3,4,-.2],[13,12,-.05],'copper'),cube([5,6,-.3],[11,10,-.2],'light')]
 return {'parent':'minecraft:block/block','textures':dict(tex,particle=tex['edge']),'elements':elements,**({'render_type':'minecraft:cutout'} if role in ['window','tank','rotor'] else {})}
def shaped(key,pattern,keys,result,count=1):
 write(f'data/{NS}/recipes/{key}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'item':NS+':'+result,'count':count}})

def main():
 zh={};en={}
 from formed_models import models
 for name,data in models().items():
  write(f'assets/{NS}/models/block/{name}.json',data)
  write(f'assets/{NS}/blockstates/{name}.json',{'variants':{'':{'model':NS+':block/'+name}}})
 for role,z,e in zip(ROLES,ZH,EN):
  zh[f'block.{NS}.{role}']=z;en[f'block.{NS}.{role}']=e
  variants={}
  for active in [False,True]:
   for fill in range(5):
    suffix=f'_{int(active)}_{fill}' if role in ['window','tank'] else f'_{int(active)}'
    if fill==0 or role in ['window','tank']:write(f'assets/{NS}/models/block/{role}{suffix}.json',model(role,active,fill))
    for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={facing},active={str(active).lower()},fill={fill}']={'model':NS+':block/'+role+suffix,'y':angle}
  write(f'assets/{NS}/blockstates/{role}.json',{'variants':variants})
  if role=='rotor_blades':continue
  suffix='_0_0' if role in ['window','tank'] else '_0';write(f'assets/{NS}/models/item/{role}.json',{'parent':NS+':block/'+role+suffix})
  write(f'data/{NS}/loot_tables/blocks/{role}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':NS+':'+role}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
 goods={'manual':('共振工业手册','Resonant Industry Handbook','book'),'slurry_canister':('营养浆液桶','Nutrient Slurry Canister','dragon_breath'),'fuel_residue':('燃料残渣','Fuel Residue','charcoal'),'purified_tissue':('纯化组织','Purified Tissue','phantom_membrane'),'generator_blueprint':('燃料发电机蓝图','Fuel Generator Blueprint','map'),'processor_blueprint':('生物质处理罐蓝图','Biomass Processor Blueprint','map'),'centrifuge_blueprint':('工业离心阵列蓝图','Industrial Centrifuge Blueprint','map'),'wrench':('装配扳手','Assembly Wrench','iron_axe')}
 for k,(z,e,tex) in goods.items():
  zh[f'item.{NS}.{k}']=z;en[f'item.{NS}.{k}']=e;write(f'assets/{NS}/models/item/{k}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/'+tex}})
 wrench={'parent':'minecraft:item/handheld','textures':{'metal':'minecraft:block/iron_block','handle':'minecraft:block/gray_concrete','particle':'minecraft:block/iron_block'},'elements':[cube([7,1,7],[10,13,9],'handle'),cube([4,11,7],[13,14,9],'metal'),cube([4,13,7],[7,16,9],'metal'),cube([10,13,7],[13,16,9],'metal')]};write(f'assets/{NS}/models/item/wrench.json',wrench)
 for k,z,e in [('generator','燃料发电机','Fuel Generator'),('processor','生物质处理罐','Biomass Processor'),('centrifuge','工业离心阵列','Industrial Centrifuge')]:zh[f'machine.{NS}.{k}']=z;en[f'machine.{NS}.{k}']=e
 zh[f'fluid.{NS}.nutrient_slurry']='营养浆液';en[f'fluid.{NS}.nutrient_slurry']='Nutrient Slurry'
 texts={'configured':('蓝图已装入。手持扳手对准控制器查看搭建预览，右键扳手检查成形。','Blueprint installed. Aim a wrench at the controller to preview; use the wrench to assemble.'),'configure_busy':('更换蓝图前，请拆解并取空物品、液体和进行中的燃料。','Disassemble and empty items, liquids and reserved fuel before changing blueprints.'),'unbound':('该接口尚未连接成形的机器。','This port is not connected to an assembled machine.'),'formed':('%s 已成形。','%s assembled.'),'unformed':('机器已拆解，库存和加工进度保留在控制器中。','Machine disassembled. Inventory and progress remain in the controller.'),'unloaded':('结构区块尚未载入，机器暂停。','A structure chunk is unloaded; the machine is paused.'),'missing':('结构检查：需要 %s，位置 %s / %s / %s。','Structure check: expected %s at %s / %s / %s.'),'fluid_rejected':('液体类型不符、空间不足，或不足一桶。','Wrong liquid, insufficient room, or less than one bucket available.'),'inventory_full':('背包需要空位放入装满的桶。','Make room for the filled bucket.'),'energy_status':('能量 %s / %s FE；液体 %s / %s mB。','Energy %s / %s FE; liquid %s / %s mB.')}
 for k,(z,e) in texts.items():zh[f'message.{NS}.{k}']=z;en[f'message.{NS}.{k}']=e
 for i,(z,e) in enumerate([('未成形：使用扳手检查','Unassembled: use a wrench'),('等待材料','Waiting for materials'),('能量不足','Insufficient energy'),('等待输入液体','Waiting for input fluid'),('产物或储液已满','Output storage is full'),('正在运行','Running'),('区块未载入，暂停','Chunk unloaded; paused')]):zh[f'gui.{NS}.status.{i}']=z;en[f'gui.{NS}.status.{i}']=e
 for k,z,e in [('input','投入','Input'),('output','产物 / 残渣','Products'),('fluids','输入 %s / 输出 %s mB','Input %s / output %s mB')]:zh[f'gui.{NS}.{k}']=z;en[f'gui.{NS}.{k}']=e
 for role,z,e in [('air','留空','Air'),('claim','未被其他机器占用的部件','Unclaimed component'),('blueprint','有效蓝图','Valid blueprint')]:zh[f'block.{NS}.{role}']=z;en[f'block.{NS}.{role}']=e
 pages={
 'intro':('共振工业：起步','Resonant Industry: Getting Started','先合成控制器、蓝图与扳手。手持蓝图右键控制器配置机器；手持扳手对准控制器查看结构。普通右键扳手成形；潜行右键扳手拆解，物品与进度保留。煤炭启动发电，感染材料加水制浆，再离心得到试剂和燃料残渣。','Craft a controller, blueprint and wrench. Use a blueprint on an empty controller. Aim a wrench to preview, use it to assemble, or sneak-use to disassemble while preserving inventory and progress. Burn coal, process biomass with water, then centrifuge slurry into reagents and fuel residue.'),
 'generator':('燃料发电机 3×3×3','Fuel Generator 3x3x3','煤炭或木炭：64 FE/t，1600 tick。浓缩生物质燃烧 800 tick，燃料残渣 400 tick。物品输入仓投入燃料；左侧能源接口输出电力。储能满时暂停燃烧。','Coal/charcoal: 64 FE/t for 1600 ticks. Condensed biomass lasts 800 ticks; fuel residue 400. Insert fuel through I; E exports energy. Fuel pauses when the buffer is full.'),
 'processor':('生物质处理罐 3×4×3','Biomass Processor 3x4x3','菌丝束、孢子囊或浓缩生物质 ×1，加水 1000 mB，160 tick 后得到浆液 1000 mB。每 tick 消耗 16 FE。W 输入水，F 输出浆液；I 输入材料。','1 mycelium bundle, spore pod or condensed biomass + 1000 mB water -> 1000 mB slurry in 160 ticks at 16 FE/t. W takes water; F exports slurry; I accepts biomass.'),
 'centrifuge':('工业离心阵列 3×3×5','Industrial Centrifuge 3x3x5','玻璃瓶 ×1、浆液 1000 mB：200 tick 后得到分离试剂 ×1、燃料残渣 ×1。每 tick 消耗 24 FE。组织样本配方产出纯化组织。O 自动输出两种产物。','1 glass bottle + 1000 mB slurry -> 1 separated reagent + 1 fuel residue in 200 ticks at 24 FE/t. Tissue samples instead yield purified tissue. O exports both products.'),
 'transport':('管道与储存','Transport and Storage','电缆双向传电；储能箱面向玩家的一面主动输出。流体管可接 W/F，储液罐正面主动输出。取水泵放在水源上方，接电后每秒最多抽一桶；相邻两格水源时维持原版无限水。空桶取液，水桶或浆液桶向输入端加液。','Cables move energy; an energy cell actively outputs at its front. Pipes connect W/F; tanks actively output at the front. Put a powered pump above water: up to 1000 mB/second, 100 FE/bucket. Two adjacent source blocks preserve vanilla renewable water. Use buckets to fill or drain.'),
 'assembly':('搭建、存档与联动','Assembly, Saves and Integration','图中 # 外壳，C 控制器，R 转轴，G 观察窗，E 能源，I 物品输入，O 物品输出，W 流体输入，F 流体输出，· 留空。层数从底部开始。朝向以控制器正面为准，支持四向旋转。结构损坏、区块卸载、断电或输出满时暂停。新工业网独立计量电力。','Legend: # casing, C controller, R spindle, G glass, E energy, I item inlet, O item outlet, W fluid inlet, F fluid outlet, dot air. Layers start at the bottom, with the controller at the front. Four rotations are supported. Structure damage, unloaded chunks, no power or full outputs pause processing.')}
 for key,(zt,et,zb,eb) in pages.items():
  zh[f'guide.{NS}.{key}.title']=zt;en[f'guide.{NS}.{key}.title']=et;zh[f'guide.{NS}.{key}.body']=zb;en[f'guide.{NS}.{key}.body']=eb
 zh[f'guide.{NS}.layer']='切换楼层';en[f'guide.{NS}.layer']='Next layer'
 ui={
 'stored':('存电 %s / %s FE','Stored %s / %s FE'),
 'generation':('当前发电 %s FE/t','Generating %s FE/t'),
 'consumption':('当前耗电 %s FE/t','Consuming %s FE/t'),
 'fuel_time':('剩余燃烧 %s 秒','Fuel remaining: %s s'),
 'no_recipe':('等待原料与有效配方','Waiting for a valid recipe'),
 'tank_in':('入 %s %s/%s mB','In %s %s/%s mB'),
 'tank_out':('出 %s %s/%s mB','Out %s %s/%s mB'),
 'empty':('空','Empty'),'short_slurry':('浆液','Slurry'),
 'idle_load':('待机耗电：0 FE/t','Idle load: 0 FE/t'),
 'coal_budget':('煤炭总量：102,400 FE/个','Coal budget: 102,400 FE'),
 'status.7':('原料与液体不匹配','No matching recipe'),
 'status.8':('储能已满，燃烧暂停','Buffer full; fuel paused')}
 for key,(z,e) in ui.items():zh[f'gui.{NS}.{key}']=z;en[f'gui.{NS}.{key}']=e
 write(f'assets/{NS}/lang/zh_cn.json',zh);write(f'assets/{NS}/lang/en_us.json',en)
 shaped('casing',['III','ICI','III'],{'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot'},'casing',8)
 shaped('controller',['CRC','IAI','CRC'],{'C':'minecraft:copper_ingot','R':'minecraft:redstone','I':'minecraft:iron_ingot','A':'minecraft:amethyst_shard'},'controller')
 for role,ingredient in {'window':'minecraft:glass','rotor':'minecraft:copper_ingot','energy_port':'minecraft:gold_ingot','item_input':'minecraft:hopper','item_output':'minecraft:chest','fluid_input':'minecraft:bucket','fluid_output':'minecraft:cauldron','battery':'minecraft:redstone_block','tank':'minecraft:glass','pump':'minecraft:piston'}.items():shaped(role,[' I ','ICI',' I '],{'I':ingredient,'C':NS+':casing'},role)
 shaped('cable',['CCC','RRR','CCC'],{'C':'minecraft:copper_ingot','R':'minecraft:redstone'},'cable',12)
 shaped('fluid_pipe',['CCC','GGG','CCC'],{'C':'minecraft:copper_ingot','G':'minecraft:glass'},'fluid_pipe',12)
 shaped('wrench',['I I','ICI',' C '],{'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot'},'wrench')
 shaped('manual',['BA'],{'B':'minecraft:book','A':'minecraft:amethyst_shard'},'manual')
 for name,item in [('generator','minecraft:coal'),('processor','ender_infection:mycelium_bundle'),('centrifuge','ender_infection:chitin_plate')]:shaped(name+'_blueprint',['PI'],{'P':'minecraft:paper','I':item},name+'_blueprint')
 for tag in ['mineable/pickaxe','needs_stone_tool']:write(f'data/minecraft/tags/blocks/{tag}.json',{'replace':False,'values':[NS+':'+r for r in ROLES]})
 dest=RES/f'data/{NS}/structures/empty_test.nbt';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(BASE.parents[1]/'examples/institute-addon/forge/src/main/resources/data/institute_addon/structures/empty_test.nbt',dest)
 print('Industry native assets and survival recipes generated')
if __name__=='__main__':main()
