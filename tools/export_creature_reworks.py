"""Pack MCP-authored creature replacements while preserving upstream image pixels.

The MCP codecs own geometry/animation conversion. This stage packs materials,
scales geometry and position channels, and binds runtime IDs to sealed exports.
"""
import argparse,base64,hashlib,io,json,math
from pathlib import Path
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'colored_units/creature_rework_v2'
ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
SPECS={
 'infected_spider':dict(scale=1.05,offset_y=.566,atlas=[256,128],placements={0:[0,0],3:[128,0]},emissive_materials=[3],hit_tick=14,duration_ticks=30,death_ticks=18),
 'infected_cow':dict(scale=1.2,offset_y=0,atlas=[256,128],placements={1:[0,0],0:[128,0]},emissive_materials=[0],hit_tick=14,duration_ticks=30,death_ticks=18,project_original=True),
 'infected_pig':dict(scale=1.08,offset_y=0,atlas=[256,128],placements={1:[0,0],0:[128,0]},emissive_materials=[0],hit_tick=12,duration_ticks=28,death_ticks=18,project_original=True),
 'infected_sheep':dict(scale=1.3,offset_y=0,atlas=[256,128],placements={1:[0,0],0:[128,0]},emissive_materials=[0],hit_tick=20,duration_ticks=40,death_ticks=18,project_original=True),
 'infected_cow_early':dict(scale=1,offset_y=0,atlas=[256,128],placements={1:[0,0],0:[128,0]},emissive_materials=[0],hit_tick=14,duration_ticks=30,death_ticks=18,project_original=True),
 'infected_pig_early':dict(scale=.88,offset_y=0,atlas=[256,128],placements={1:[0,0],0:[128,0]},emissive_materials=[0],hit_tick=12,duration_ticks=28,death_ticks=18,project_original=True),
 'infected_sheep_early':dict(scale=1.02,offset_y=0,atlas=[256,128],placements={1:[0,0],0:[128,0]},emissive_materials=[0],hit_tick=20,duration_ticks=40,death_ticks=18,project_original=True),
 'infected_spider_early':dict(scale=.49,offset_y=0,atlas=[256,128],placements={0:[0,0],3:[128,0]},emissive_materials=[3],hit_tick=12,duration_ticks=26,death_ticks=18,provenance_file='early-provenance.json'),
}

def read(path):return json.loads(path.read_text('utf-8'))
def encoded(data):return (json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def png(im):
 buffer=io.BytesIO();im.save(buffer,format='PNG');return buffer.getvalue()

def source_cubes(model):
 defs={g['uuid']:g for g in model['groups']};elements={e['uuid']:e for e in model['elements']};result={}
 def visit(rows):
  for row in rows:
   if not isinstance(row,dict):continue
   group=defs[row['uuid']];result[group['name']]=[elements[uid] for uid in row.get('children',[]) if isinstance(uid,str) and elements[uid].get('export',True)]
   visit(row.get('children',[]))
 visit(model['outliner']);return result

def scale_position(value,scale):
 if isinstance(value,list):return [float(v)*scale for v in value]
 if isinstance(value,dict):
  return {k:scale_position(v,scale) if k in ['pre','post','vector'] or k.replace('.','',1).isdigit() else v for k,v in value.items()}
 raise ValueError('Unsupported position channel')

def build(runtime,*,pack=None,spec=None):
 pack=pack or PACK;spec=spec or SPECS[runtime];paths=[pack/'models'/(runtime+'.bbmodel'),pack/'mcp_exports'/(runtime+'.geo.json'),pack/'mcp_exports'/(runtime+'.animation.json')]
 lock=read(pack/'export-lock.json')['models'][runtime]
 for path in paths:
  if sha(path)!=lock[path.relative_to(pack).as_posix()]:raise ValueError('Stale MCP export: '+path.name)
 verification=read(pack/'mcp-verification.json')['models'][runtime]
 if verification['info']['revision']!=sha(paths[0])[:16] or verification['motion']['revision']!=sha(paths[0])[:16]:raise ValueError('Stale MCP verification')
 if verification['motion']['summary']['errors'] or verification['motion']['summary']['warnings']:raise ValueError('Unresolved animation gates')
 model=read(paths[0]);images=[Image.open(io.BytesIO(base64.b64decode(t['source'].split(',',1)[1]))).convert('RGBA') for t in model['textures']]
 atlas=Image.new('RGBA',tuple(spec['atlas']));glow=Image.new('RGBA',atlas.size);glow_selection=Image.new('1',atlas.size);draw=ImageDraw.Draw(glow_selection)
 for index,(x,y) in spec['placements'].items():
  image=images[index];assert x>=0 and y>=0 and x+image.width<=atlas.width and y+image.height<=atlas.height
  atlas.paste(image,(x,y))
 geometry=read(paths[1]);geo=geometry['minecraft:geometry'][0];by_bone=source_cubes(model)
 used_crystal_faces=0
 for bone in geo['bones']:
  cubes=bone.get('cubes',[]);original=by_bone.get(bone['name'],[])
  if len(cubes)!=len(original):raise ValueError('MCP cube order mismatch: '+bone['name'])
  for cube,element in zip(cubes,original):
   if not isinstance(cube.get('uv'),dict):raise ValueError('Per-face UV export required')
   for face,value in list(cube['uv'].items()):
    source_face=element['faces'][face];index=source_face.get('texture')
    if index is None:del cube['uv'][face];continue
    if index not in spec['placements']:raise ValueError('Unpacked source texture: '+str(index))
    texture=model['textures'][index];image=images[index];fx=image.width/texture.get('uv_width',model['resolution']['width']);fy=image.height/texture.get('uv_height',model['resolution']['height']);x,y=spec['placements'][index]
    value['uv']=[value['uv'][0]*fx+x,value['uv'][1]*fy+y];value['uv_size']=[value['uv_size'][0]*fx,value['uv_size'][1]*fy]
    if index in spec['emissive_materials']:
     if not element['name'].startswith('crystal_'):raise ValueError('Emissive material assigned to non-crystal geometry')
     u,v=value['uv'];U,V=u+value['uv_size'][0],v+value['uv_size'][1]
     left,right=sorted([u,U]);top,bottom=sorted([v,V])
     if any(not math.isclose(n,round(n),abs_tol=1e-6) for n in [left,right,top,bottom]):raise ValueError('Crystal UV must select exact pixels')
     if right>left and bottom>top:draw.rectangle((round(left),round(top),round(right)-1,round(bottom)-1),fill=1)
     used_crystal_faces+=1
  for target in [bone,*cubes]:
   for field in ['origin','pivot']:
    if field in target:target[field]=[(v+(spec['offset_y'] if i==1 else 0))*spec['scale'] for i,v in enumerate(target[field])]
   if 'size' in target:target['size']=[v*spec['scale'] for v in target['size']]
   if 'inflate' in target:target['inflate']*=spec['scale']
 glow.paste(atlas,(0,0),glow_selection)
 geo['description'].update(identifier='geometry.'+runtime,texture_width=atlas.width,texture_height=atlas.height,visible_bounds_width=6,visible_bounds_height=4,visible_bounds_offset=[0,1.3,0])
 animations=read(paths[2]);out={}
 for short,clip in animations['animations'].items():
  for channels in clip.get('bones',{}).values():
   if 'position' in channels:channels['position']=scale_position(channels['position'],spec['scale'])
  out['animation.'+runtime+'.'+short]=clip
 animations['animations']=out
 files={f'geo/{runtime}.geo.json':encoded(geometry),f'animations/{runtime}.animation.json':encoded(animations),f'textures/entity/{runtime}.png':png(atlas),f'textures/entity/{runtime}_glowmask.png':png(glow)}
 info=read(pack/'mcp-verification.json')['models'][runtime]['info'];bounds=info['bounds'];low,high=bounds['min'],bounds['max'];s=spec['scale']/16
 visual_bounds={'width':(high[0]-low[0])*s,'height':(high[1]-low[1])*s+.2,'depth':(high[2]-low[2])*s,'center_x':-(low[0]+high[0])/2*s,'center_z':(low[2]+high[2])/2*s}
 row={'source':'mcp_rework_'+runtime,'editable_source':paths[0].relative_to(ROOT).as_posix(),'runtime':runtime,'kind':'entity','pipeline':'tools/export_creature_reworks.py','scale':spec['scale'],'cubes':sum(len(b.get('cubes',[])) for b in geo['bones']),'bones':len(geo['bones']),'atlas':spec['atlas'],'emissive_pixels':sum(glow.getchannel('A').histogram()[1:]),'crystal_faces':used_crystal_faces,'visual_bounds':visual_bounds}
 return files,row

def export(update_manifest=True):
 rows=[]
 for runtime in SPECS:
  files,row=build(runtime)
  for relative,content in files.items():target=ASSETS/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
  rows.append(row)
 bounds=read(ASSETS/'concept_visual_bounds.json')
 for row in rows:bounds[row['runtime'].removeprefix('infected_')]=row['visual_bounds']
 (ASSETS/'concept_visual_bounds.json').write_bytes(encoded(bounds))
 provenance=read(PACK/'source-provenance.json');credits_path=ASSETS/'third_party/model_credits.json';credits=read(credits_path)
 credits['license_scope']='license and license_url apply to the third-party models collection; original_reworks records project-original assets separately.'
 credits['models']=[row for row in credits['models'] if row['runtime'] not in SPECS]
 original=read(PACK/'quadruped-provenance.json') if any(s.get('project_original') for s in SPECS.values()) else {}
 credits['original_reworks']=[row for row in credits.get('original_reworks',[]) if row['runtime'] not in SPECS]
 for runtime,spec in SPECS.items():
  if spec.get('project_original'):
   row=original['models'][runtime];credits['original_reworks'].append(dict(runtime=runtime,author='Ender Infection project',source_pack='creature_rework_v2',original_source=row['source'],original_source_sha256=row['source_sha256'],provenance='Existing project original, reworked with user authorization; no third-party artwork imported',changes='; '.join(row['changes'])))
  else:
   source=read(PACK/spec['provenance_file'])['models'][runtime] if spec.get('provenance_file') else provenance
   credits['models'].append(dict(runtime=runtime,author=source['author'],original=source['original'],url=source['page'],license=source['license'],license_url=source['license_url'],source_pack='creature_rework_v2',changes='; '.join(source['changes'])))
 credits_path.write_bytes(encoded(credits))
 if update_manifest:
  path=ROOT/'ender_infection_colored/verification/design_asset_manifest.json';manifest=read(path)
  manifest['assets']=[row for row in manifest['assets'] if row['runtime'] not in SPECS]+rows;path.write_bytes(encoded(manifest))
 return rows

def validate():
 rows=[]
 for runtime,spec in SPECS.items():
  files,row=build(runtime)
  for relative,content in files.items():
   path=ASSETS/relative
   if relative.endswith('.png'):
    with Image.open(path) as actual,Image.open(io.BytesIO(content)) as expected:equal=actual.size==expected.size and actual.convert('RGBA').tobytes()==expected.convert('RGBA').tobytes()
   else:equal=path.read_bytes()==content
   if not equal:raise ValueError('Runtime replacement differs from sealed MCP source: '+relative)
  geometry=json.loads(files[f'geo/{runtime}.geo.json'])['minecraft:geometry'][0];names={b['name'] for b in geometry['bones']}
  if len(names)!=len(geometry['bones']):raise ValueError('Duplicate runtime bone names')
  for bone in geometry['bones']:
   if bone.get('parent') and bone['parent'] not in names:raise ValueError('Absent parent')
   for cube in bone.get('cubes',[]):
    for uv in cube['uv'].values():
     for axis,limit in enumerate(row['atlas']):
      if not(0<=uv['uv'][axis]<=limit and 0<=uv['uv'][axis]+uv['uv_size'][axis]<=limit):raise ValueError('UV outside atlas')
  clips=json.loads(files[f'animations/{runtime}.animation.json'])['animations'];prefix='animation.'+runtime+'.'
  if set(clips)!={prefix+name for name in ['idle','walk','special','infection','death']}:raise ValueError('Missing native animation')
  if not math.isclose(clips[prefix+'special']['animation_length'],spec['duration_ticks']/20):raise ValueError('Special duration changed')
  if not math.isclose(clips[prefix+'death']['animation_length'],spec['death_ticks']/20):raise ValueError('Death clip exceeds the native lifetime')
  impact=spec['hit_tick']/20
  if not any(any(math.isclose(float(t),impact,abs_tol=1e-6) for t in channel if t.replace('.','',1).isdigit()) for bone in clips[prefix+'special']['bones'].values() for channel in bone.values() if isinstance(channel,dict)):raise ValueError('Missing impact key')
  for clip in clips.values():
   if set(clip['bones'])-names:raise ValueError('Animation references absent bone')
  if row['emissive_pixels']<=0:raise ValueError('Missing crystal emission')
  rows.append(row)
 return rows

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--verify',action='store_true');args=parser.parse_args();print(json.dumps(validate() if args.verify else export(),ensure_ascii=False))
