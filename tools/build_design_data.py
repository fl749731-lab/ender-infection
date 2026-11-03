"""Data pack content, recipes and native four-storey laboratory template."""
import json,gzip,struct
from design_catalog import *
from export_design_assets import write,SOURCES
allblocks=[b+suffix for b in BLOCKS.values() for suffix in ('','_eroded')]
for _,key,*_ in UNITS:write(ASSETS/'models/item'/f'infected_{key}_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
for lang in ('zh_cn','en_us'):
 p=ASSETS/'lang'/f'{lang}.json';d=json.loads(p.read_text('utf-8'))
 for _,key,source,label,*_ in UNITS:
  label=label if lang=='zh_cn' else source.replace('_',' ').title();d['entity.ender_infection.infected_'+key]=label;d['item.ender_infection.infected_'+key+'_spawn_egg']=label+('刷怪蛋' if lang=='zh_cn' else ' Spawn Egg')
 for source,key in {**MATERIALS,**WEAPONS}.items():d['item.ender_infection.'+key]=SOURCES[source]['name'] if lang=='zh_cn' else key.replace('_',' ').title()
 for source,key in ARMOR.items():
  for slot,zh in [('helmet','头盔'),('chestplate','胸甲'),('leggings','护腿'),('boots','靴子')]:d['item.ender_infection.'+key+'_'+slot]=(SOURCES[source]['name']+zh) if lang=='zh_cn' else (key+' '+slot).replace('_',' ').title()
 for source,key in BLOCKS.items():
  for suffix,stage in [('','intact'),('_eroded','eroded')]:d['block.ender_infection.'+key+suffix]=SOURCES[source+'_'+stage]['name'] if lang=='zh_cn' else (key+suffix).replace('_',' ').title()
 d['entity.ender_infection.design_crystal']='共鸣晶刺' if lang=='zh_cn' else 'Resonant Crystal';write(p,d)
for key in allblocks:
 write(DATA/'loot_tables/blocks'/f'{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'ender_infection:'+key}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(DATA/'loot_tables/blocks/lab_fixture_part.json',{'type':'minecraft:block','pools':[]})
write(ASSETS/'blockstates/lab_fixture_part.json',{'variants':{'':{'model':'ender_infection:block/lab_fixture_part'}}})
write(ASSETS/'models/block/lab_fixture_part.json',{'textures':{'particle':'ender_infection:block/design/lab_tile_light'},'elements':[]})
write(PROJECT/'src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':['ender_infection:'+key for key in allblocks]+['ender_infection:lab_fixture_part']})

def recipe(key,pattern,materials,count=1):
 def ingredient(v):return {'item':v if ':' in v else 'ender_infection:'+v}
 write(DATA/'recipes'/f'{key}.json',{'type':'minecraft:crafting_shaped','category':'equipment' if key in WEAPONS.values() or 'carapace' in key or 'brood_heavy' in key else 'building','pattern':pattern,'key':{k:ingredient(v) for k,v in materials.items()},'result':{'item':'ender_infection:'+key,'count':count}})
for key in BLOCKS.values():
 if key in ('lab_tile_light','lab_tile_dark','lab_tile','lab_checker'):recipe(key,['II','SS'],{'I':'minecraft:iron_ingot','S':'minecraft:stone'},8)
 elif key=='lab_slab':recipe(key,['TTT'],{'T':'lab_tile_light'},6)
 elif key=='lab_stair':recipe(key,['T  ','TT ','TTT'],{'T':'lab_tile_light'},4)
 else:recipe(key,['ITI','TCT','ITI'],{'I':'minecraft:iron_ingot','T':'lab_tile_light','C':'minecraft:glass' if key in ('incubator','lab_window') else 'minecraft:redstone'})
 write(DATA/'recipes'/f'{key}_eroded.json',{'type':'minecraft:crafting_shapeless','category':'building','ingredients':[{'item':'ender_infection:'+key},{'item':'ender_infection:infected_tissue'}],'result':{'item':'ender_infection:'+key+'_eroded'}})
recipe('prism_spear',['  S',' I ','I  '],{'S':'resonant_shard','I':'minecraft:iron_ingot'})
recipe('earthsplitter_hammer',['CCC','CSC',' I '],{'C':'chitin_plate','S':'resonance_organ','I':'minecraft:iron_ingot'})
recipe('crystal_spike_crossbow',['CSC','IBI',' S '],{'C':'chitin_plate','S':'resonant_shard','I':'minecraft:iron_ingot','B':'minecraft:crossbow'})
recipe('resonance_staff',[' HO',' IS','I  '],{'H':'mother_heart','O':'resonance_organ','I':'mycelium_bundle','S':'resonant_shard'})
for armor in ARMOR.values():
 for slot,pattern in {'helmet':['CCC','C C'],'chestplate':['C C','COC','CCC'],'leggings':['CCC','C C','C C'],'boots':['C C','C C']}.items():
  mats={'C':'chitin_plate'}
  if slot=='chestplate':mats['O']='resonance_organ'
  if armor=='brood_heavy':
   # Upgrade the matching light piece without duplicating armour.
   write(DATA/'recipes'/f'{armor}_{slot}.json',{'type':'minecraft:crafting_shapeless','category':'equipment','ingredients':[{'item':'ender_infection:crystal_carapace_'+slot},{'item':'ender_infection:mother_heart'},{'item':'ender_infection:condensed_biomass'},{'item':'ender_infection:chitin_plate'}],'result':{'item':f'ender_infection:{armor}_{slot}'}})
  else:recipe(armor+'_'+slot,pattern,mats)
loot=DATA/'loot_tables/chests/infected_lab.json';d=json.loads(loot.read_text('utf-8'));generated={'ender_infection:'+k for k in ['mycelium_bundle','chitin_plate','spore_pod','condensed_biomass','resonance_organ','resonant_shard']};d['pools']=[p for p in d['pools'] if {e.get('name') for e in p.get('entries',[])}!=generated];d['pools'].append({'rolls':{'min':2,'max':4},'entries':[{'type':'minecraft:item','name':'ender_infection:'+key,'weight':2 if key=='resonance_organ' else 5,'functions':[{'function':'minecraft:set_count','count':{'min':1,'max':3}}]} for key in ['mycelium_bundle','chitin_plate','spore_pod','condensed_biomass','resonance_organ','resonant_shard']]});write(loot,d)
from build_facilities import build_all
build_all()
from build_lab_details import export_details
export_details()
