"""Pack verified Blockbench MCP exports for the two Java loaders.

Geometry and Catmull-Rom animations come from the MCP codec. This stage only
packs the existing pixels into one GeckoLib atlas, applies the declared model
scale, and adds runtime animation prefixes. It never edits a .bbmodel.
"""
import base64,copy,hashlib,io,json,math
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'colored_units/public_model_adaptations_v1'
ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
SPECS={
 'resonance_priest':dict(runtime='infected_resonance_priest',legacy_source='resonance_priest',scale=.52,offset_y=0,author='@anim',original='Skeleton Lord',url='https://blockbenchworkshop.com/model/anim/skeleton-lord',source='skeleton_lord.bbmodel',changes='Shortened and attached staff; added resonance crown and chest focus; retimed spell to tick 16; infection, death, and sampled ground-contact tracks; merged texture atlas.'),
 'crystalback_carrier':dict(runtime='infected_carrier',legacy_source='crystalback_carrier',scale=.7,offset_y=1.238,author='Breathfang (Drageon DB)',original='Leathern Drake',url='https://sketchfab.com/3d-models/leathern-drake-blockbench-021865367838492a9839c64c6f3cb984',source='leathern_drake.bbmodel',changes='Shortened tail; purple crystal cargo and clamps; retained armored variant; recovered five baked tail IK tracks from author GeckoLib export; attack, infection, death, and ground-contact tracks; merged texture atlas.'),
}

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text('utf-8'))
def encoded(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')
def png(image):
 out=io.BytesIO();image.save(out,format='PNG');return out.getvalue()

def group_cubes(d):
 defs={g['uuid']:g for g in d['groups']};elements={e['uuid']:e for e in d['elements']};result={}
 def visit(nodes):
  for n in nodes:
   if not isinstance(n,dict):continue
   name=defs[n['uuid']]['name'];result[name]=[elements[c] for c in n.get('children',[]) if isinstance(c,str) and elements[c].get('type','cube')=='cube' and elements[c].get('export',True)]
   visit(n.get('children',[]))
 visit(d['outliner']);return result

def build(name,check_frozen=True):
 spec=SPECS[name];source=PACK/'models'/f'{name}.bbmodel';d=read(source)
 paths=[source,PACK/'mcp_exports'/f'{name}.geo.json',PACK/'mcp_exports'/f'{name}.animation.json']
 if check_frozen:
  frozen=read(PACK/'export-lock.json')['models'][name]
  for path in paths:
   if sha(path)!=frozen[path.relative_to(PACK).as_posix()]:raise ValueError(f'{name}: changed source/export; re-export and verify with Blockbench MCP before packing')
 images=[Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA') for t in d['textures']]
 # Exact packing: no pixel recoloring or resizing.
 if name=='resonance_priest':
  atlas=Image.new('RGBA',(512,512));placements={0:(0,0,0,0,128,128),1:(128,0,0,0,128,128),2:(256,0,0,0,256,256),3:(0,256,0,0,256,256),4:(256,256,0,0,256,256)}
 else:
  # Only the opaque 6x3 material strip is used from our 512px cargo atlas.
  # Keep it at its exact pixel scale beside the original 256px body variant.
  atlas=Image.new('RGBA',(512,256));placements={3:(0,0,0,0,256,256),4:(257,1,329,163,335,166)}
 glow=Image.new('RGBA',atlas.size)
 for index,(x,y,left,top,right,bottom) in placements.items():
  crop=images[index].crop((left,top,right,bottom));atlas.paste(crop,(x,y))
  if (name=='resonance_priest' and index in (3,4)) or (name=='crystalback_carrier' and index==4):glow.paste(crop,(x,y))
 # The original skull's small luminous magenta eyes use texture 0.
 if name=='resonance_priest':
  original=images[0];mask=Image.new('RGBA',original.size)
  for y in range(original.height):
   for x in range(original.width):
    r,g,b,a=original.getpixel((x,y))
    if a and r>g*1.3 and b>g*1.3:mask.putpixel((x,y),(r,g,b,a))
  glow.paste(mask,(0,0))
 geometry=read(paths[1]);geo=geometry['minecraft:geometry'][0];children=group_cubes(d)
 for bone in geo['bones']:
  cubes=bone.get('cubes',[]);src=children.get(bone['name'],[])
  if len(cubes)!=len(src):raise ValueError('MCP/source cube order mismatch: '+bone['name'])
  for cube,element in zip(cubes,src):
   if isinstance(cube.get('uv'),list):
    cube['uv']={}
    for face,value in element['faces'].items():
     if value.get('texture') is None:continue
     u,v,U,V=value['uv']
     if face in ('up','down'):u,v,U,V=U,V,u,v
     cube['uv'][face]={'uv':[u,v],'uv_size':[U-u,V-v]}
   for face,uv in list(cube.get('uv',{}).items()):
    # MCP's codec retains its source face keys. Disabled faces sometimes still
    # carry a UV rectangle; explicitly remove those instead of assigning a tile.
    index=element['faces'][face].get('texture')
    if index is None:del cube['uv'][face];continue
    if index not in placements:raise ValueError(f'Unpacked material {index}')
    tex=d['textures'][index];fx=images[index].width/tex.get('uv_width',d['resolution']['width']);fy=images[index].height/tex.get('uv_height',d['resolution']['height']);x,y,left,top,_,_=placements[index]
    uv['uv']=[uv['uv'][0]*fx-left+x,uv['uv'][1]*fy-top+y];uv['uv_size']=[uv['uv_size'][0]*fx,uv['uv_size'][1]*fy]
  for target in [bone,*cubes]:
   for field in ('origin','pivot'):
    if field in target:target[field]=[v*spec['scale']+(spec['offset_y']*spec['scale'] if i==1 else 0) for i,v in enumerate(target[field])]
   if 'size' in target:target['size']=[v*spec['scale'] for v in target['size']]
   if 'inflate' in target:target['inflate']*=spec['scale']
 desc=geo['description'];desc.update(identifier='geometry.'+spec['runtime'],texture_width=atlas.width,texture_height=atlas.height,visible_bounds_width=8,visible_bounds_height=8,visible_bounds_offset=[0,2,0])
 animations=read(paths[2]);out={}
 def position_scale(value):
  if isinstance(value,list):return [str(float(v)*spec['scale']) for v in value]
  if isinstance(value,dict):return {k:position_scale(v) if k in ('pre','post','vector') or k.replace('.','',1).isdigit() else v for k,v in value.items()}
  raise ValueError('Unexpected position track')
 for short,a in animations['animations'].items():
  for channels in a['bones'].values():
   if 'position' in channels:channels['position']=position_scale(channels['position'])
  out['animation.'+spec['runtime']+'.'+short]=a
 animations['animations']=out
 runtime=spec['runtime'];files={f'geo/{runtime}.geo.json':encoded(geometry),f'animations/{runtime}.animation.json':encoded(animations),f'textures/entity/{runtime}.png':png(atlas),f'textures/entity/{runtime}_glowmask.png':png(glow)}
 return files,dict(source=spec['legacy_source'],editable_source=source.relative_to(ROOT).as_posix(),runtime=runtime,kind='entity',scale=spec['scale'],pipeline='tools/export_public_model_adaptations.py',cubes=sum(len(b.get('cubes',[])) for b in geo['bones']),bones=len(geo['bones']),atlas=list(atlas.size),emissive_pixels=sum(glow.getchannel('A').histogram()[1:]))

def export(names=None,assets=ASSETS):
 rows=[]
 for name in names or SPECS:
  files,row=build(name)
  for relative,content in files.items():p=assets/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(content)
  rows.append(row)
 credits={'license':'CC-BY-4.0','license_url':'https://creativecommons.org/licenses/by/4.0/','models':[{k:v for k,v in spec.items() if k in ('runtime','author','original','url','changes')} for spec in SPECS.values()]}
 p=assets/'third_party/model_credits.json'
 if p.exists():
  prior=read(p);credits['models'] += [row for row in prior.get('models',[]) if row['runtime'] not in {spec['runtime'] for spec in SPECS.values()}]
  credits={**prior,**credits}
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encoded(credits))
 bounds=read(assets/'concept_visual_bounds.json');verification=read(PACK/'mcp-verification.json')
 for name in names or SPECS:
  spec=SPECS[name];b=verification['models'][name]['info']['bounds'];lo=b['min'];hi=b['max'];s=spec['scale']/16
  bounds[spec['runtime'].removeprefix('infected_')]={'width':(hi[0]-lo[0])*s,'height':(hi[1]-lo[1])*s+.2,'depth':(hi[2]-lo[2])*s,'center_x':-(lo[0]+hi[0])/2*s,'center_z':(lo[2]+hi[2])/2*s}
 (assets/'concept_visual_bounds.json').write_bytes(encoded(bounds))
 return rows

def validate():
 rows=[]
 for name in SPECS:
  expected,row=build(name)
  for relative,content in expected.items():
   path=ASSETS/relative
   matches=path.exists()
   if matches and relative.endswith('.png'):
    with Image.open(path) as actual,Image.open(io.BytesIO(content)) as wanted:
     matches=actual.size==wanted.size and actual.convert('RGBA').tobytes()==wanted.convert('RGBA').tobytes()
   elif matches:matches=path.read_bytes()==content
   if not matches:raise ValueError('Public adaptation differs from verified MCP export: '+relative)
  geo=json.loads(expected[f'geo/{row["runtime"]}.geo.json'])['minecraft:geometry'][0];names={b['name'] for b in geo['bones']}
  for b in geo['bones']:
   if b.get('parent') and b['parent'] not in names:raise ValueError('Missing parent')
   for c in b.get('cubes',[]):
    for face in c['uv'].values():
     for axis,limit in enumerate(row['atlas']):
      if not (0<=face['uv'][axis]<=limit and 0<=sum([face['uv'][axis],face['uv_size'][axis]])<=limit):raise ValueError('Atlas UV outside bounds')
  clips=json.loads(expected[f'animations/{row["runtime"]}.animation.json'])['animations'];prefix='animation.'+row['runtime']+'.'
  if set(clips)!={prefix+s for s in ('idle','walk','special','infection','death')}:raise ValueError('Animation contract changed')
  if clips[prefix+'special']['animation_length']!=2:raise ValueError('Special animation must last 40 ticks')
  for a in clips.values():
   if set(a['bones'])-names:raise ValueError('Animation targets missing bones')
  if row['emissive_pixels']==0:raise ValueError('Missing crystal glow')
  rows.append(row)
 return rows

if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');a=p.parse_args()
 print(json.dumps(validate() if a.verify else export(),ensure_ascii=False))
