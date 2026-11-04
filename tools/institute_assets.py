"""Read existing BB models and vanilla block models for the room design atlas."""
import base64,copy,io,json,uuid,zipfile
from pathlib import Path
from PIL import Image
from institute_room_spec import ROOT,NS

ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
PACK=ROOT/'colored_units/design_pack_2026-11-02_v2/laboratory'
JAR=ROOT/'Minecraft1201/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar'
VANILLA=zipfile.ZipFile(JAR)
LABELS=json.loads((ASSETS/'lang/zh_cn.json').read_text('utf-8'))
LABELS.update({'block.minecraft.'+k:v for k,v in {
 'smooth_quartz':'平滑石英块','polished_deepslate':'磨制深板岩','polished_andesite':'磨制安山岩','iron_block':'铁块',
 'gray_concrete':'灰色混凝土','yellow_concrete':'黄色混凝土','cyan_concrete':'青色混凝土','light_blue_concrete':'淡蓝色混凝土','green_concrete':'绿色混凝土','purple_concrete':'紫色混凝土',
 'cyan_terracotta':'青色陶瓦','light_blue_terracotta':'淡蓝色陶瓦','green_terracotta':'绿色陶瓦','yellow_terracotta':'黄色陶瓦','purple_terracotta':'紫色陶瓦',
 'glass':'玻璃','light_blue_stained_glass':'淡蓝色玻璃','light_gray_stained_glass':'淡灰色玻璃','purple_stained_glass':'紫色玻璃',
 'barrel':'木桶','bookshelf':'书架','white_bed':'白色床','gray_bed':'灰色床','anvil':'铁砧','grindstone':'砂轮','lectern':'讲台','brewing_stand':'酿造台',
 'smoker':'烟熏炉','blast_furnace':'高炉','redstone_lamp':'红石灯','redstone_block':'红石块','grass_block':'草方块','fern':'蕨','potted_fern':'蕨盆栽',
 'mycelium':'菌丝体','brown_mushroom':'棕色蘑菇','azalea':'杜鹃花丛','budding_amethyst':'紫水晶母岩','amethyst_block':'紫水晶块','amethyst_cluster':'紫水晶簇','blue_ice':'蓝冰',
 }.items()})
SOURCE={'lab_tile_light':'white_wall_tile','lab_tile_dark':'dark_wall_tile','lab_tile':'lab_brick_tile','lab_checker':'checker_floor',
 'lab_slab':'lab_slab','lab_stair':'lab_stair','lab_vent':'vent_panel','lab_hatch':'service_hatch','lab_door':'reinforced_door',
 'lab_cabinet':'lab_cabinet','incubator':'crystal_incubator','surgery_table':'surgery_table','lab_workbench':'equipment_bench','lab_window':'observation_window'}


def label(block):return LABELS.get('block.'+block.replace(':','.'),block.split(':')[1])


class Library:
    def __init__(self):self.assets={};self.textures={};self.icons={};self.approximated=set()
    def tex(self,name,raw):
        key=str(uuid.uuid5(uuid.NAMESPACE_URL,name))
        if key not in self.textures:
            im=Image.open(io.BytesIO(raw));self.textures[key]=dict(uuid=key,name=name.split('/')[-1],source='data:image/png;base64,'+base64.b64encode(raw).decode(),width=im.width,height=im.height,uv_width=im.width,uv_height=im.height,render_mode='default')
        return key
    def bb(self,key):
        short=key.split(':')[1];base=short.replace('_eroded','')
        path=PACK/(SOURCE[base]+('_eroded' if short.endswith('_eroded') else '_intact')+'.bbmodel') if base in SOURCE else ROOT/'colored_units/lab_details_v3'/(short+'.bbmodel')
        d=json.loads(path.read_text('utf-8'));textures={i:self.tex(str(path)+':'+str(i),base64.b64decode(t['source'].split(',',1)[1])) for i,t in enumerate(d['textures'])}
        cubes=[]
        for e in d['elements']:
            if e.get('export',True) and e.get('type','cube')=='cube':
                c=copy.deepcopy(e);c.pop('uuid',None)
                for f in c['faces'].values():f['texture']=textures.get(f.get('texture'))
                cubes.append(c)
        # All these static source groups use zero rest rotations. Cube rotations
        # and origins are preserved below, including angled workbench apparatus.
        groups=d.get('groups',[])
        def walk(nodes):
            for g in nodes:
                if isinstance(g,dict):
                    assert not any(g.get('rotation',[0,0,0])),(path,g['name']);walk(g.get('children',[]))
        walk(groups or d.get('outliner',[]))
        return dict(cubes=cubes,rotation=[0,0,0],native_model=str(path.relative_to(ROOT)),approximate=False)
    def model(self,path):
        path=path.removeprefix('minecraft:');d=json.loads(VANILLA.read('assets/minecraft/models/'+path+'.json'))
        if d.get('parent','').startswith('builtin/'):return d
        if 'parent' in d:
            parent=self.model(d['parent']);d={**parent,**d,'textures':{**parent.get('textures',{}),**d.get('textures',{})}}
        return d
    def java(self,key,state):
        short=key.split(':')[1];d=json.loads(VANILLA.read('assets/minecraft/blockstates/'+short+'.json'))
        chosen=None
        if 'variants' in d:
            for conditions,val in d['variants'].items():
                props=dict(pair.split('=') for pair in conditions.split(',') if pair)
                if all(k not in state or str(state[k]).lower()==v for k,v in props.items()):chosen=val;break
        if chosen is None:chosen=d.get('multipart',[{}])[0].get('apply')
        if isinstance(chosen,list):chosen=chosen[0]
        model=self.model(chosen['model']);maps=model.get('textures',{})
        def texture(ref):
            while ref.startswith('#'):ref=maps[ref[1:]]
            ref=ref.removeprefix('minecraft:');return self.tex('minecraft:'+ref,VANILLA.read('assets/minecraft/textures/'+ref+'.png'))
        cubes=[]
        for e in model.get('elements',[]):
            a=e['from'];b=e['to'];c=dict(name=short,from_=[a[0]-8,a[1],a[2]-8],to=[b[0]-8,b[1],b[2]-8],origin=[0,8,0],rotation=[0,0,0],box_uv=False,faces={})
            c['from']=c.pop('from_')
            if 'rotation' in e:
                r=e['rotation'];c['origin']=[r['origin'][0]-8,r['origin'][1],r['origin'][2]-8];c['rotation'][['x','y','z'].index(r['axis'])]=r['angle']
            for f,v in e['faces'].items():c['faces'][f]=dict(uv=v.get('uv',[0,0,16,16]),texture=texture(v['texture']),rotation=v.get('rotation',0))
            cubes.append(c)
        approximate=False
        if not cubes:
            approximate=True;self.approximated.add(key)
            # Entity-rendered vanilla beds have no Java block elements. Preserve
            # their exact two-cell placement and render the expected low bed form.
            if short.endswith('_bed'):
                color=short.removesuffix('_bed');raw=VANILLA.read('assets/minecraft/textures/entity/bed/'+color+'.png');im=Image.open(io.BytesIO(raw));tile=im.crop((6,6,22,22));buf=io.BytesIO();tile.save(buf,format='PNG');t=self.tex('bed_'+color,buf.getvalue())
                cubes=[dict(name='bed_half',**{'from':[-8,3,-8],'to':[8,9,8]},origin=[0,0,0],rotation=[0,0,0],box_uv=False,faces={f:dict(uv=[0,0,16,16],texture=t) for f in ['up','down','north','south','east','west']})]
            else:raise ValueError(('No renderable vanilla model',key,chosen))
        return dict(cubes=cubes,rotation=[chosen.get('x',0),-chosen.get('y',0),0],native_model='minecraft:'+chosen['model'],approximate=approximate)
    def get(self,key,state=None):
        state=state or {};cache=key+'|'+json.dumps(state,sort_keys=True)
        if cache not in self.assets:
            if key.startswith(NS):
                assert (ASSETS/'blockstates'/(key.split(':')[1]+'.json')).exists(),key
                result=self.bb(key);face=state.get('facing','north');result['rotation']=[0,{'north':0,'east':-90,'south':180,'west':90}.get(face,0),0]
            else:result=self.java(key,state)
            self.assets[cache]=result
        return self.assets[cache]
    def icon(self,key):
        if key not in self.icons:
            asset=self.get(key);c=asset['cubes'][0];f=c['faces'].get('up') or next(iter(c['faces'].values()));texture=self.textures[f['texture']]
            im=Image.open(io.BytesIO(base64.b64decode(texture['source'].split(',',1)[1]))).convert('RGBA');u,v,U,V=f['uv'];rect=[min(u,U),min(v,V),max(u,U),max(v,V)]
            if rect[2]==rect[0] or rect[3]==rect[1]:rect=[0,0,im.width,im.height]
            self.icons[key]=im.crop(rect).resize((32,32),Image.Resampling.NEAREST)
        return self.icons[key]


if __name__=='__main__':
    from institute_room_spec import generate
    lib=Library();rooms=generate();ids=set()
    for r in rooms:
        for p in r['placements']:lib.get(p['block'],p['state']);ids.add(p['block'])
    print(json.dumps({'block_ids':len(ids),'state_models':len(lib.assets),'textures':len(lib.textures),'approximate_entity_models':sorted(lib.approximated)}))
