"""Derive paired leaves from the canonical door, preserving IDs and materials.

Local north-facing plan (16 model units per block): the outside jambs remain
at x=-8 and x=24. Leaves meet at x=8; no post divides the two-block opening.
Only runtime geometry is derived here; room layouts remain coordinate/NBT based.
"""
import argparse,copy,json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'

def variants(source):
 left=copy.deepcopy(source)
 geometry=left['minecraft:geometry'][0];bones=geometry['bones']
 frame=next(b for b in bones if b['name']=='root')
 # Bedrock X is mirrored by GeckoLib when it bakes into the Java coordinate system.
 frame['cubes']=[c for c in frame['cubes'] if not (c['origin'][0]<-6 and (c['size'][1]>30 or c['size'][0]<1))]
 leaf=next(b for b in bones if b['name']=='door_hinge');pivot=leaf['pivot'][0]
 factor=(pivot+8)/(pivot+6)
 for cube in leaf['cubes']:
  cube['origin'][0]=pivot+(cube['origin'][0]-pivot)*factor
  cube['size'][0]*=factor
  if 'pivot' in cube:cube['pivot'][0]=pivot+(cube['pivot'][0]-pivot)*factor
 right=copy.deepcopy(left)
 for bone in right['minecraft:geometry'][0]['bones']:
  bone['pivot'][0]*=-1
  if 'rotation' in bone:bone['rotation']=[bone['rotation'][0],-bone['rotation'][1],-bone['rotation'][2]]
  for cube in bone.get('cubes',[]):
   cube['origin'][0]=-cube['origin'][0]-cube['size'][0]
   if 'pivot' in cube:cube['pivot'][0]*=-1
   if 'rotation' in cube:cube['rotation']=[cube['rotation'][0],-cube['rotation'][1],-cube['rotation'][2]]
   if isinstance(cube.get('uv'),dict):
    uv=cube['uv'];uv['east'],uv['west']=uv['west'],uv['east']
 return left,right

def export(verify=False):
 for key in ['lab_door','lab_door_eroded']:
  original=json.loads((ASSETS/'geo'/(key+'.geo.json')).read_text('utf-8'))
  for side,data in zip(['left','right'],variants(original)):
   name=key+'_double_'+side
   data['minecraft:geometry'][0]['description']['identifier']='geometry.'+name
   target=ASSETS/'geo'/(name+'.geo.json');text=json.dumps(data,separators=(',',':'))+'\n'
   if verify:
    if target.read_text('utf-8')!=text:raise ValueError('Stale paired-door geometry: '+name)
   else:target.write_text(text,'utf-8')
 print(('Verified' if verify else 'Exported')+' four paired-door geometries')

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--verify',action='store_true');args=parser.parse_args();export(args.verify)
