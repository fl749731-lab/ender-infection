"""24 distinct, purpose-led room layouts. All designs and placements are original."""
import hashlib,json
from collections import Counter,deque
from export_design_assets import write
from design_catalog import DATA,PROJECT

THEMES=[
 ['power','pumps','filtration','containment','reactor','nexus'],
 ['triage','ward','surgery','pharmacy','isolation','mortuary'],
 ['receiving','microscopy','wet_lab','culture','cold_store','decontamination'],
 ['reception','security','control','archive','lounge','kitchen']]
LABELS={'power':'配电与应急电源','pumps':'冷却泵与水处理','filtration':'通风与过滤机房','containment':'玻璃标本收容','reactor':'晶体共鸣反应室','nexus':'污染核心隔离室',
 'triage':'分诊与急救','ward':'住院观察病房','surgery':'手术与器械准备','pharmacy':'药剂配制与储存','isolation':'隔离观察病房','mortuary':'遗体暂存与检验',
 'receiving':'样本接收与登记','microscopy':'显微分析实验室','wet_lab':'湿法分析实验室','culture':'组织培养实验室','cold_store':'低温样本库','decontamination':'清洗与消毒间',
 'reception':'接待与入所登记','security':'门禁与安检','control':'中控与通讯','archive':'档案与阅览','lounge':'工作人员休息室','kitchen':'后勤餐厨'}
DETAILS={'lab_monitor','lab_sample_rack','lab_shelf','lab_pipe','lab_light_panel','lab_chair','lab_sink','lab_storage_crate','lab_freezer','lab_floor_grate'}
STORAGE_FIXTURES={'lab_cabinet','lab_workbench'}
STORAGE_DETAILS={'lab_freezer','lab_storage_crate','lab_shelf','lab_sink'}
FURNITURE_CATEGORIES={
 'power':'engineering','pumps':'engineering','filtration':'engineering','robotics':'engineering','loading':'engineering','server_room':'engineering',
 'containment':'containment','reactor':'containment','nexus':'containment','airlock':'containment',
 'triage':'medical','ward':'medical','surgery':'medical','pharmacy':'medical','isolation':'medical','mortuary':'medical','decontamination':'medical',
 'receiving':'research','microscopy':'research','wet_lab':'research','culture':'research','cold_store':'research','cryo_vault':'research',
 'security':'security','control':'security','archive':'archives','botany':'greenhouse',
 'reception':'public','lounge':'public','kitchen':'public'}


def furniture_loot_nbt(key,theme='public'):
 """Only generator-owned furniture receives loot; runtime defaults stay empty."""
 if ':' in key and not key.startswith('ender_infection:'):return None
 base=key.split(':')[-1].replace('_eroded','')
 if base not in STORAGE_FIXTURES|STORAGE_DETAILS:return None
 category=FURNITURE_CATEGORIES.get(theme,theme if theme in {'public','engineering','containment','medical','research','security','archives','greenhouse'} else 'public')
 entity='ender_infection:lab_fixture' if base in STORAGE_FIXTURES else 'ender_infection:lab_storage'
 return {'id':(8,entity),'LootTable':(8,'ender_infection:chests/central_institute/furniture/'+category)}

class Room:
 def __init__(self,api,floor,row,side,theme):
  self.api,self.floor,self.row,self.side,self.theme=api,floor,row,side,theme;self.y=floor*5;self.z=1+row*9;self.records=[];self.features=[];self.chest=None
 def pos(self,u,v,h=1):return (1+u if self.side==0 else 30-u,self.y+h,self.z+v)
 def facing(self,f):return {'east':'west','west':'east'}.get(f,f) if self.side else f
 def p(self,u,v,key,h=1,face='north',props=None):
  state=dict(props or {})
  if key in DETAILS:state['facing']=self.facing(face)
  pos=self.pos(u,v,h);self.api['put'](*pos,key,state,furniture_loot_nbt(key,self.theme));self.records.append((u,h,v,key,tuple(sorted(state.items()))))
 def v(self,u,v,key,h=1,props=None):self.p(u,v,'minecraft:'+key,h,props=props)
 def f(self,u,v,key,h=1,face='north'):
  world_face=self.facing(face);pos=self.pos(u,v,h);self.api['fixture'](*pos,key,world_face)
  nbt=furniture_loot_nbt(key,self.theme)
  if nbt:self.api['put'](*pos,key,{'facing':world_face,'open':'false'},nbt)
  self.records.append((u,h,v,key,('fixture',world_face)))
 def feature(self,name):self.features.append(name)
 def table(self,u,v,samples=False):
  self.p(u,v,'lab_freezer')
  if samples:self.p(u,v,'lab_sample_rack',2)
 def monitor(self,u,v,h=3,face='south'):self.p(u,v,'lab_monitor',h,face)
 def shelf(self,u,v,height=2):
  for h in range(1,height+1):self.p(u,v,'lab_shelf',h)
 def crate(self,u,v,height=1):
  for h in range(1,height+1):self.p(u,v,'lab_storage_crate',h)
 def bed(self,u,v,color='white'):
  self.v(u,v,color+'_bed',props={'facing':'north','part':'foot','occupied':'false'});self.v(u,v-1,color+'_bed',props={'facing':'north','part':'head','occupied':'false'})
 def partition(self,u,door_v=2,material='glass',eroded=False):
  for v in range(8):
   for h in range(1,4):
    if v==door_v and h<3:continue
    self.v(u,v,material,h)
  self.f(u,door_v,'lab_door_eroded' if eroded else 'lab_door',face='east')
 def chest_at(self,u,v,kind='general'):
  pos=self.pos(u,v);self.api['put'](*pos,'minecraft:chest',{'facing':self.facing('north'),'type':'single','waterlogged':'false'},{'id':self.api['STR']('minecraft:chest'),'LootTable':self.api['STR']('ender_infection:chests/lab_'+kind)})
  self.records.append((u,1,v,'minecraft:chest',('loot',kind)));self.chest=list(pos)
 def common(self):
  # Shared material language, with services on ceiling rather than repeating floor furniture.
  for u,v in ((3,3),(8,5)):self.p(u,v,'lab_light_panel',4)
  for u in (2,3,4,5,6,7,8,9):self.p(u,7,'lab_pipe',4)
  for u in range(12):
   for v in range(8):
    border=u in (0,11) or v in (0,7);key='lab_tile_dark' if border else 'lab_tile_light'
    if self.floor==0 and (u*3+v*5)%17<3:key='lab_checker_eroded'
    self.p(u,v,key,0)
  # One wall vent at a real service edge. It is not the room's main feature.
  self.f(0,6,'lab_vent_eroded' if self.floor==0 else 'lab_vent',3,face='east')

def reception(r):
 r.feature('L形登记台与等候座位')
 for u,v in [(7,5),(8,5),(9,5),(9,6)]:r.table(u,v)
 for u in (7,9):r.p(u,5,'lab_sample_rack',2)
 for u,v in [(2,1),(4,1),(2,5),(4,5)]:r.p(u,v,'lab_chair',face='south')
 r.monitor(3,0);r.f(1,7,'lab_cabinet');r.chest_at(10,7,'archive')
def security(r):
 r.feature('双门禁隔离线与观察屏')
 r.partition(6,material='light_gray_stained_glass');r.f(2,6,'lab_workbench');r.p(3,4,'lab_chair',face='south')
 r.monitor(4,0);r.v(1,1,'iron_bars',props={'east':'true','west':'true','north':'false','south':'false','waterlogged':'false'});r.shelf(1,4);r.crate(4,7);r.chest_at(2,0,'security')
def control(r):
 r.feature('成排墙面屏幕与U形操作席')
 for u in (2,5,8):r.monitor(u,0)
 for u in (2,5,8):r.table(u,1);r.p(u,3,'lab_chair',face='north')
 for v in (4,5,6):r.table(1,v)
 r.monitor(0,4,3,'east');r.f(8,6,'lab_cabinet');r.chest_at(10,7,'engineering')
def archive(r):
 r.feature('档案书架、阅览台和终端')
 for u in (1,3,5,7):
  for h in (1,2,3):r.v(u,7,'bookshelf',h)
 for u in (2,6):r.v(u,3,'lectern',props={'facing':'north','has_book':'false','powered':'false'});r.p(u,5,'lab_chair',face='north')
 r.f(9,6,'lab_cabinet');r.monitor(2,0);r.chest_at(10,7,'archive')
def lounge(r):
 r.feature('独立卧铺、衣柜与休息座位')
 for u in (5,8):r.bed(u,6,'gray')
 r.f(10,7,'lab_cabinet');r.p(6,0,'lab_chair',face='south');r.p(8,0,'lab_chair',face='south');r.table(7,0);r.monitor(6,7);r.chest_at(10,0,'supplies')
def kitchen(r):
 r.feature('烟熏炉、清洗槽与食物桶')
 for u in (1,2):r.v(u,6,'smoker',props={'facing':r.facing('north'),'lit':'false'})
 r.p(3,6,'lab_sink')
 for u in (5,6,7):r.table(u,6)
 for u in (1,3):
  for h in (1,2):r.v(u,0,'barrel',h,{'facing':'north','open':'false'})
 for u in (6,8):r.p(u,3,'lab_chair',face='south')
 r.chest_at(10,7,'supplies')
def receiving(r):
 r.feature('样本入库台、登记终端和封装箱')
 r.f(6,6,'lab_workbench');r.p(6,4,'lab_chair');r.table(2,1,True);r.table(3,1,True);r.crate(1,6,2);r.crate(3,6);r.shelf(1,3);r.monitor(2,0);r.chest_at(10,7,'research')
def microscopy(r):
 r.feature('双面分析台与遮光观察席')
 for u in (3,7):r.f(u,5,'lab_workbench');r.p(u,3,'lab_chair');r.table(u,0,True)
 for v in (0,1,2,3,4,5,6,7):
  if v!=2:r.v(5,v,'gray_stained_glass',2)
 r.monitor(8,0);r.chest_at(10,7,'research')
def wet_lab(r):
 r.feature('连续湿法台、双水槽和排水地格')
 for u in (1,2,3,4,6,7,8):r.table(u,6,u in (2,4,7))
 for u in (1,7):r.p(u,0,'lab_sink')
 for v in (3,4,5,6):r.p(5,v,'lab_floor_grate',0)
 r.v(2,6,'brewing_stand',2,{'has_bottle_0':'true','has_bottle_1':'false','has_bottle_2':'true'});r.monitor(4,0);r.chest_at(10,7,'research')
def culture(r):
 r.feature('玻璃分区、三座培养舱和样本台')
 r.partition(5,material='glass')
 for u,v in [(1,1),(3,4),(1,6)]:r.f(u,v,'incubator')
 r.table(8,6,True);r.table(9,6,True);r.monitor(8,0);r.p(8,4,'lab_chair');r.chest_at(10,7,'research')
def cold_store(r):
 r.feature('成组低温冷柜与窄服务过道')
 for u in (5,7,9):
  for v in (0,6):
   r.p(u,v,'lab_freezer');r.p(u,v,'lab_freezer',2)
 r.shelf(10,4);r.monitor(6,0,3);r.chest_at(10,7,'research')
def decontamination(r):
 r.feature('两道隔断、清洗槽与地面排水')
 r.partition(3,material='light_gray_stained_glass');r.partition(8,material='light_gray_stained_glass')
 for v in (1,3,5):r.p(5,v,'lab_floor_grate',0)
 r.p(6,6,'lab_sink');r.p(6,0,'lab_sink');r.shelf(1,6);r.crate(10,6);r.chest_at(10,0,'medical')
def triage(r):
 r.feature('急救床、分诊席与备品柜')
 r.bed(2,5,'white');r.bed(6,5,'white');r.f(9,6,'lab_workbench');r.p(9,4,'lab_chair');r.f(1,0,'lab_cabinet');r.p(4,0,'lab_sink');r.monitor(6,0);r.chest_at(10,7,'medical')
def ward(r):
 r.feature('四床独立病位与床旁柜')
 for u in (1,4,7,10):
  r.bed(u,6,'light_gray')
  if u<10:r.p(u+1,6,'lab_freezer')
 r.f(3,0,'lab_cabinet');r.monitor(8,0);r.p(7,1,'lab_chair',face='south');r.chest_at(10,0,'medical')
def surgery(r):
 r.feature('中央手术台、器械台、吊灯与清洗槽')
 r.f(5,4,'surgery_table');r.p(2,1,'lab_sink');r.table(1,6,True);r.table(2,6,True);r.f(9,6,'lab_cabinet');r.f(9,0,'lab_cabinet');r.p(5,4,'lab_light_panel',4);r.monitor(4,0);r.chest_at(10,7,'medical')
def pharmacy(r):
 r.feature('药剂配制长台与分层样本架')
 for u in (1,3,5,7):r.table(u,6,True)
 for u in (1,3):r.shelf(u,0,3)
 r.v(5,6,'brewing_stand',2,{'has_bottle_0':'true','has_bottle_1':'true','has_bottle_2':'true'});r.p(8,0,'lab_sink');r.monitor(6,0);r.chest_at(10,7,'medical')
def isolation(r):
 r.feature('玻璃观察隔间与独立病床')
 r.partition(7,material='glass')
 for u in (5,9):r.bed(u,6,'purple')
 r.monitor(9,0);r.p(10,4,'lab_chair',face='north');r.f(5,0,'lab_cabinet');r.chest_at(10,7,'medical')
def mortuary(r):
 r.feature('并排检验台、冷柜与器材推架')
 for u in (2,6):r.f(u,5,'surgery_table')
 for u in (1,3,5):r.p(u,0,'lab_freezer');r.p(u,0,'lab_freezer',2)
 r.crate(9,6);r.p(9,0,'lab_sink');r.chest_at(10,7,'medical')
def power(r):
 r.feature('配电柜列、维护地格和应急器材')
 for u in (1,3,5,7):r.f(u,6,'lab_cabinet')
 for u in (1,4,7):r.monitor(u,6,3,'north')
 for u in range(2,9):r.p(u,4,'lab_floor_grate',0)
 r.crate(1,0,2);r.crate(3,0);r.shelf(9,0);r.chest_at(10,7,'engineering')
def pumps(r):
 r.feature('冷却水槽、阀门管线和维护席')
 for u in (2,5,8):r.p(u,5,'lab_sink');r.p(u,5,'lab_pipe',3)
 for v in (0,1,3,4,5,6):r.p(0,v,'lab_pipe',2,'east')
 r.f(3,0,'lab_workbench');r.p(3,2,'lab_chair');r.p(7,0,'lab_storage_crate');r.chest_at(10,7,'engineering')
def filtration(r):
 r.feature('通风机架、纵向管廊与过滤组件')
 for u in (1,4,7):
  r.p(u,6,'lab_freezer');r.f(u,7,'lab_vent',2)
 for v in (1,3,5):r.p(2,v,'lab_pipe',3,'east')
 for u in (5,6,7):r.crate(u,0,2)
 r.monitor(9,0);r.chest_at(10,7,'engineering')
def containment(r):
 r.feature('高玻璃收容舱、悬挂晶体和外围观察线')
 r.partition(6,material='purple_stained_glass',eroded=True)
 for u,v in [(1,1),(3,4),(1,6)]:
  r.p(u,v,'living_tissue');r.p(u,v,'living_tissue',4);r.v(u,v,'amethyst_cluster',3,{'facing':'down','waterlogged':'false'})
 r.f(3,0,'incubator_eroded');r.monitor(9,0);r.p(9,5,'lab_chair');r.chest_at(10,7,'research')
def reactor(r):
 r.feature('多层晶体装置、仪表和环形维护线')
 for u,v in [(5,3),(6,3),(5,4),(6,4)]:r.p(u,v,'crystal_crust');r.v(u,v,'amethyst_cluster',2,{'facing':'up','waterlogged':'false'})
 for u in (4,8):r.p(u,1,'lab_pipe',3,'east');r.monitor(u,0)
 r.f(9,6,'lab_workbench_eroded');r.p(6,6,'lab_chair');r.chest_at(10,7,'engineering')
def nexus(r):
 r.feature('不对称污染组织、扩张节点与封存样本')
 r.partition(7,material='purple_stained_glass',eroded=True)
 for u,v in [(1,0),(2,0),(1,5),(2,5),(4,7),(5,7)]:r.p(u,v,'living_tissue');r.p(u,v,'infected_tendrils',2)
 r.p(3,3,'crystal_crust');r.v(3,3,'amethyst_cluster',2,{'facing':'up','waterlogged':'false'})
 pos=r.pos(4,4);r.api['put'](*pos,'infected_nest',{}, {'id':r.api['STR']('ender_infection:expansion_node'),'Wild':(1,1)});r.records.append((4,1,4,'infected_nest',()))
 r.f(5,0,'incubator_eroded');r.monitor(9,0);r.crate(9,6);r.chest_at(10,7,'research')

def botany(r):
 r.feature('开放种植槽、样本分区和培养记录')
 for u in (2,6):
  for v in (2,3,4,5):r.v(u,v,'moss_block');r.v(u,v,'fern',2)
 r.table(9,2,True);r.table(9,4,True);r.monitor(9,0);r.shelf(1,7);r.chest_at(10,7,'research')
def robotics(r):
 r.feature('拆检工位、器械架和维修线')
 r.f(3,0,'lab_workbench');r.v(5,4,'anvil');r.p(8,3,'lab_storage_crate');r.p(8,3,'lab_pipe',2);r.shelf(1,6);r.monitor(9,0);r.chest_at(10,7,'engineering')
def server_room(r):
 r.feature('双列服务器柜、后部线缆和监控席')
 for u in (2,5):
  for v in (1,3,5):r.p(u,v,'lab_freezer');r.p(u,v,'lab_freezer',2);r.p(u,v,'lab_monitor',3,'south')
 r.monitor(9,0);r.p(9,4,'lab_chair');r.chest_at(10,7,'engineering')
def cryo_vault(r):
 r.feature('低温柜阵列、结霜观察窗和样本架')
 for u in (1,4,7):r.p(u,6,'lab_freezer');r.p(u,6,'lab_freezer',2);r.v(u,7,'blue_ice',2)
 r.table(2,1,True);r.table(6,1,True);r.monitor(9,0);r.chest_at(10,7,'research')
def airlock(r):
 r.feature('双重隔离门、清洗槽和更衣柜')
 r.partition(4,2);r.partition(8,2,material='light_gray_stained_glass');r.p(1,5,'lab_sink');r.shelf(10,4);r.monitor(1,0);r.chest_at(10,7,'medical')
def loading(r):
 r.feature('货运通道、堆垛器材和收发终端')
 for u,v,n in [(1,1,2),(2,1,1),(5,6,2),(6,6,2),(9,1,1)]:r.crate(u,v,n)
 r.shelf(1,5);r.shelf(2,5);r.monitor(9,0);r.p(8,4,'lab_chair');r.chest_at(10,7,'supplies')
LABELS.update({'botany':'菌体与植物温室','robotics':'器械拆检工场','server_room':'服务器与监控机房','cryo_vault':'低温封存库','airlock':'双重消毒气闸','loading':'物资收发与装卸'})
BUILDERS={name:globals()[name] for name in list(LABELS)}


def build_rooms(api):
 reports=[]
 for floor,names in enumerate(THEMES):
  for i,name in enumerate(names):
   row,side=divmod(i,2);r=Room(api,floor,row,side,name);r.common();BUILDERS[name](r)
   # Labels face the central corridor and describe the room's actual use.
   x=14 if side==0 else 17;z=3+row*9;face='east' if side==0 else 'west'
   messages=[json.dumps({'text':LABELS[name][:8]},ensure_ascii=False),json.dumps({'text':LABELS[name][8:]},ensure_ascii=False),json.dumps({'text':f'B{4-floor}-{i+1:02d}'}),json.dumps({'text':'末影研究设施'},ensure_ascii=False)]
   api['put'](x,r.y+2,z,'minecraft:birch_wall_sign',{'facing':face,'waterlogged':'false'},{'id':api['STR']('minecraft:sign'),'front_text':api['COM']({'messages':api['LST'](8,messages),'color':api['STR']('black'),'has_glowing_text':(1,0)}),'is_waxed':(1,1)})
   normalized=[(u,h,v,k.replace('_eroded',''),state) for u,h,v,k,state in r.records if h>0]
   digest=hashlib.sha256(json.dumps(normalized,sort_keys=True).encode()).hexdigest()
   reports.append({'id':name,'name':LABELS[name],'floor':floor,'row':row,'side':side,'features':r.features,'loot_chest':r.chest,'layout_sha256':digest,'props':dict(Counter(k for u,h,v,k,state in r.records if h>0))})
 assert len({r['layout_sha256'] for r in reports})==24
 write(PROJECT/'verification/laboratory_rooms.json',{'rooms':reports,'count':24,'unique_layouts':24,'note':'Original layouts; room use and vertical dressing referenced from static Spore NBT observations.'})
 # Purpose-specific supply pools; generated deterministically.
 groups={'general':['minecraft:iron_ingot','ender_infection:resonant_shard'],'archive':['minecraft:paper','minecraft:book','minecraft:map'],'security':['minecraft:iron_sword','minecraft:arrow','minecraft:iron_ingot'],'supplies':['minecraft:bread','minecraft:carrot','minecraft:cooked_beef'],'research':['ender_infection:spore_pod','ender_infection:resonance_organ','ender_infection:resonant_shard'],'medical':['minecraft:golden_carrot','minecraft:glass_bottle','ender_infection:mycelium_bundle'],'engineering':['minecraft:redstone','minecraft:copper_ingot','minecraft:iron_ingot','ender_infection:chitin_plate']}
 for kind,items in groups.items():write(DATA/'loot_tables/chests'/f'lab_{kind}.json',{'type':'minecraft:chest','pools':[{'rolls':{'min':2,'max':4},'entries':[{'type':'minecraft:item','name':item,'functions':[{'function':'minecraft:set_count','count':{'min':1,'max':3}}]} for item in items]}]})
 return reports
