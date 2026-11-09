"""Native Java models and pixel textures; merge translations without replacing existing content."""
from pathlib import Path
import json
from PIL import Image
BASE=Path(__file__).resolve().parents[1]
RES=BASE/'forge/src/main/resources'
NS='resonant_industry'
def slot_labels(data, lang):
    words = {
        'sample': ('样品', 'Sample'), 'reagent': ('液体试剂', 'Reagent'),
        'receiver': ('接收', 'Receiver'), 'spare': ('备用', 'Spare'), 'vessel': ('器皿位', 'Vessel'),
        'selection_help': ('蓝框为取液来源，橙框为加液目标。选好两者后点击定量转移。',
                           'Blue marks the source. Amber marks the destination. Select both, then Transfer dose.'),
    }
    positions = [('右前方（靠近玩家）', 'near right'), ('前排中间（靠近玩家）', 'near center'),
                 ('左前方（靠近玩家）', 'near left'), ('右后方（远离玩家）', 'far right'),
                 ('后排中间（远离玩家）', 'far center'), ('左后方（远离玩家）', 'far left')]
    for i, (zh, en) in enumerate(positions):
        words[f'position.{i}'] = (f'从实验台正面看，{zh}。', f'From the front of the bench, {en}.')
    for key, pair in words.items():
        data[f'lab.{NS}.slot.{key}'] = pair[0 if lang == 'zh_cn' else 1]

def write(rel,obj):
    p=RES/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def cube(a,b,tex,tint=None):
    face={'texture':'#'+tex}
    if tex=='glass':face['uv']=[0,0,16,16]
    if tint is not None:face['tintindex']=tint
    return {'from':a,'to':b,'faces':{s:dict(face) for s in ['north','south','east','west','up','down']}}
def main():
    tex=RES/f'assets/{NS}/textures/item';tex.mkdir(parents=True,exist_ok=True)
    glass=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            if x in (0,15) or y in (0,15) or (x==2 and y in range(2,13)):glass.putpixel((x,y),(100,156,179,255))
    glass.save(tex/'lab_glass.png')
    for name,color in [('lab_liquid',(255,255,255,255)),('lab_rubber',(41,48,58,255)),('lab_label',(235,228,199,255)),('lab_mark',(48,78,88,255))]:Image.new('RGBA',(16,16),color).save(tex/(name+'.png'))
    shapes={
        'reagent_bottle':([5,1,5],[11,11,11],[cube([6,11,6],[10,14,10],'glass'),cube([5.7,14,5.7],[10.3,16,10.3],'rubber'),cube([5.5,4,4.95],[10.5,8,5.05],'label')]),
        'dropper_bottle':([5,1,5],[11,10,11],[cube([6,10,6],[10,13,10],'rubber'),cube([6.5,13,6.5],[9.5,16,9.5],'rubber'),cube([6,3,4.95],[10,7,5.05],'label')]),
        'pipette':([7,2,7],[9,13,9],[cube([6,13,6],[10,16,10],'rubber'),cube([7.5,0,7.5],[8.5,2,8.5],'glass')]),
        'test_tube':([6,1,6],[10,14,10],[cube([5.5,14,5.5],[10.5,15,10.5],'glass'),cube([6,15,6],[10,16,10],'rubber')]),
        'beaker':([3,1,3],[13,12,13],[cube([2.5,12,2.5],[13.5,13,13.5],'glass'),cube([13,11,6],[15,12,10],'glass')])}
    for name,(a,b,extra) in shapes.items():
        texture={k:f'{NS}:item/lab_{k}' for k in ['glass','liquid','rubber','label','mark']}
        # Thick base and four transparent walls leave the top visibly open.
        elements=[cube(a,[b[0],a[1]+.6,b[2]],'glass'),cube(a,[a[0]+.4,b[1],b[2]],'glass'),cube([b[0]-.4,a[1],a[2]],b,'glass'),cube(a,[b[0],b[1],a[2]+.4],'glass'),cube([a[0],a[1],b[2]-.4],b,'glass')]+extra
        for y in range(a[1]+2,b[1]-1,2):elements.append(cube([a[0]+.7,y,a[2]-.05],[a[0]+2,y+.15,a[2]+.05],'mark'))
        model={'textures':texture,'render_type':'minecraft:cutout','elements':elements,'display':{'gui':{'rotation':[16,-30,0],'scale':[.85,.85,.85]},'firstperson_righthand':{'rotation':[0,-35,0],'translation':[1,1,0],'scale':[.7,.7,.7]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.5,.5,.5]},'fixed':{'rotation':[0,0,0]},'ground':{'translation':[0,3,0],'scale':[.5,.5,.5]}}}
        for level in range(1,5):
            variant=dict(model);variant['elements']=elements+[cube([a[0]+.45,a[1]+.65,a[2]+.45],[b[0]-.45,a[1]+.65+(b[1]-a[1]-1.2)*level/4,b[2]-.45],'liquid',0)]
            write(f'assets/{NS}/models/item/{name}_{level}.json',variant)
        model['overrides']=[{'predicate':{f'{NS}:fill':t},'model':f'{NS}:item/{name}_{i+1}'} for i,t in enumerate([.001,.251,.501,.751])]
        write(f'assets/{NS}/models/item/{name}.json',model)
    bench=[cube([0,12,0],[16,14,16],'top'),cube([1,9,1],[15,12,15],'body')]
    for x in [1,13]:
        for z in [1,13]:bench.append(cube([x,0,z],[x+2,12,z+2],'legs'))
    bench+=[cube([6,10,0.8],[10,10.6,1.2],'legs')]
    write(f'assets/{NS}/models/block/lab_bench.json',{'textures':{'top':'minecraft:block/quartz_block_top','body':'minecraft:block/white_concrete','legs':'minecraft:block/iron_block','particle':'minecraft:block/quartz_block_side'},'elements':bench})
    write(f'assets/{NS}/models/item/lab_bench.json',{'parent':f'{NS}:block/lab_bench'})
    write(f'assets/{NS}/blockstates/lab_bench.json',{'variants':{f'facing={f}':{'model':f'{NS}:block/lab_bench','y':y} for f,y in [('north',0),('east',90),('south',180),('west',270)]}})
    write(f'data/{NS}/loot_tables/blocks/lab_bench.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{NS}:lab_bench'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    recipes={
        'reagent_bottle':([' G ','G G',' G '],{'G':'minecraft:glass'},2),
        'dropper_bottle':([' R ',' G ','GGG'],{'R':'minecraft:dried_kelp','G':'minecraft:glass'},2),
        'pipette':([' R ',' G ',' G '],{'R':'minecraft:dried_kelp','G':'minecraft:glass_pane'},2),
        'test_tube':(['G G','G G',' G '],{'G':'minecraft:glass_pane'},4),
        'beaker':(['G G','G G','GGG'],{'G':'minecraft:glass'},2),
        'lab_bench':(['QQQ','I I','I I'],{'Q':'minecraft:smooth_quartz','I':'minecraft:iron_ingot'},1)}
    for key,(pattern,keys,count) in recipes.items():
        write(f'data/{NS}/recipes/{key}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'item':f'{NS}:{key}','count':1}})
        write(f'data/{NS}/advancements/recipes/labware/{key}.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[next(iter(keys.values()))]}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':f'{NS}:{key}'}}},'requirements':[['has_material','has_the_recipe']],'rewards':{'recipes':[f'{NS}:{key}']}})
    p=RES/'data/minecraft/tags/blocks/mineable/pickaxe.json';tag=json.loads(p.read_text());value=f'{NS}:lab_bench'
    if value not in tag['values']:tag['values'].append(value)
    write('data/minecraft/tags/blocks/mineable/pickaxe.json',tag)
    for lang,names,words in [
        ('zh_cn',['试剂瓶','滴瓶','滴管','试管','烧杯','实验台'],['空','%s · %s / %s mB','每剂 %s mB','空瓶右键取样；有液右键滴加。潜行右键补取一剂；潜行对空切换剂量。','来源','目标','切换剂量','定量转移','已转移 %s mB','无法转移：检查液体、余量、空位和剂量。']),
        ('en_us',['Reagent Bottle','Dropper Bottle','Pipette','Test Tube','Beaker','Laboratory Bench'],['Empty','%s · %s / %s mB','Dose: %s mB','Empty: sample. Filled: dispense. Sneak: sample one dose; sneak in air: change dose.','From','To','Change dose','Transfer dose','Transferred %s mB','Blocked: check fluid, contents, room and dose.'])]:
        p=RES/f'assets/{NS}/lang/{lang}.json';data=json.loads(p.read_text('utf-8'))
        for key,name in zip([*shapes,'lab_bench'],names):data[('block' if key=='lab_bench' else 'item')+f'.{NS}.{key}']=name
        for key,value in zip(['empty','contents','dose','vessel_help','from','to','change_dose','transfer','transferred','blocked'],words):data[f'lab.{NS}.{key}']=value
        data[f'guide.{NS}.labware.title']='实验器皿 · 取样与定量滴加' if lang=='zh_cn' else 'Labware · Sampling and measured doses'
        data[f'guide.{NS}.labware.body']=('容量：试剂瓶250、滴瓶100、滴管25、试管50、烧杯1000 mB。\n手持器皿右键台面空位摆放；水桶可装满烧杯。对已有器皿或储液罐右键：空器皿取样，有液则倒入一剂。潜行右键补取一剂，潜行对空右键切换剂量。\n空手右键实验台打开界面：选择来源、目标与剂量，再点定量转移。潜行空手右键台面器皿可取回。拆除台子会掉落保存原液量的器皿。不同液体或附加成分不能混装。' if lang=='zh_cn' else 'Capacities (mB): reagent bottle 250, dropper bottle 100, pipette 25, test tube 50, beaker 1000.\nUse a vessel on an empty bench position to place it. A water bucket fills a beaker. On a vessel or tank: empty tools sample; filled tools dispense one dose. Sneak to sample one dose; sneak in air to change dose.\nEmpty hand opens the bench: choose source, target and dose, then Transfer. Sneak with an empty hand retrieves a vessel. Breaking the bench drops filled vessels. Different liquids or chemical components cannot mix.')
        slot_labels(data, lang)
        write(f'assets/{NS}/lang/{lang}.json',data)
    print('Generated five lab vessels, fill models, bench, recipes and translations')
if __name__=='__main__':main()
