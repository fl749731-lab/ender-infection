"""Prepare read-only runtime inspection operations for the Blockbench MCP.

The script writes operation plans, never .bbmodel projects. Poses are sampled
from the canonical game assets so an older editable source cannot hide a bug.
"""
import argparse,base64,json,math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
WORK=ROOT/'build/public-model-integration/audit'
OUT=ROOT/'previews/public-model-adaptations-v1/audit'

def vec(v):
 if isinstance(v,dict):return vec(v.get('vector',v.get('post',v.get('pre'))))
 if not isinstance(v,list):return [v,v,v]
 return [float(x) for x in v]

def sample(track,t,default):
 if not isinstance(track,dict) or 'vector' in track:return vec(track)
 keys=sorted((float(k),v) for k,v in track.items())
 if not keys:return default
 if t<=keys[0][0]:return vec(keys[0][1])
 if t>=keys[-1][0]:return vec(keys[-1][1])
 for i,((a,x),(b,y)) in enumerate(zip(keys,keys[1:])):
  if a<=t<=b:
   f=(t-a)/(b-a);p,q=vec(x),vec(y)
   mode=y.get('lerp_mode') if isinstance(y,dict) else None
   if mode=='catmullrom':
    u=vec(keys[max(0,i-1)][1]);v=vec(keys[min(len(keys)-1,i+2)][1])
    return [.5*((2*p[j])+(-u[j]+q[j])*f+(2*u[j]-5*p[j]+4*q[j]-v[j])*f*f+(-u[j]+3*p[j]-3*q[j]+v[j])*f**3) for j in range(3)]
   return [p[j]+(q[j]-p[j])*f for j in range(3)]
 raise ValueError(t)

def prepare():
 WORK.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
 lang=json.loads((ASSETS/'lang/zh_cn.json').read_text('utf-8'))
 files=sorted((ASSETS/'geo').glob('infected_*.geo.json'))+[(ASSETS/'geo/ender_mutant_zombie.geo.json'),(ASSETS/'geo/containment_warden.geo.json')]
 rows=[]
 for file in files:
  key=file.name.removesuffix('.geo.json');geo=json.loads(file.read_text())['minecraft:geometry'][0]
  desc=geo['description'];ops=[]
  names={b['name'] for b in geo['bones']}
  for b in geo['bones']:
   p=b.get('pivot',[0,0,0]);r=b.get('rotation',[0,0,0])
   op=dict(op='add_group',name=b['name'],origin=[-p[0],p[1],p[2]],rotation=[-r[0],-r[1],r[2]])
   if b.get('parent') in names:op['parent']=b['parent']
   ops.append(op)
   for i,c in enumerate(b.get('cubes',[])):
    o,s=c['origin'],c['size'];p=c.get('pivot',[0,0,0]);r=c.get('rotation',[0,0,0]);uv=c.get('uv',[0,0])
    op=dict(op='add_cube',name=b['name']+'_cube_'+str(i),**{'from':[-o[0]-s[0],o[1],o[2]],'to':[-o[0],o[1]+s[1],o[2]+s[2]]},origin=[-p[0],p[1],p[2]],rotation=[-r[0],-r[1],r[2]],inflate=c.get('inflate',0),parent=b['name'])
    if isinstance(uv,list):op.update(box_uv=True,uv_offset=uv,mirror_uv=c.get('mirror',False),texture=0)
    else:
     faces={}
     for f,data in uv.items():
      f={'east':'west','west':'east'}.get(f,f);a=data['uv'];s=data.get('uv_size',[0,0]);rect=[a[0],a[1],a[0]+s[0],a[1]+s[1]]
      if f in ('up','down'):rect=[rect[2],rect[3],rect[0],rect[1]]
      faces[f]={'uv':rect,'texture':0}
     op.update(box_uv=False,faces=faces)
    ops.append(op)
  animfile=ASSETS/'animations'/f'{key}.animation.json';anims=json.loads(animfile.read_text())['animations']
  def clip(*suffixes):
   return next((a for suffix in suffixes for n,a in anims.items() if n.endswith('.'+suffix)),{})
  idle=clip('idle');walk=clip('walk','drag_walk');attack=clip('special','claw_sweep','claw')
  hit=.8
  if key=='containment_warden':hit=1
  poses=[idle,walk,attack];times=[0,min(.25,walk.get('animation_length',1)),min(hit,attack.get('animation_length',1))]
  ops.append(dict(op='add_animation',name='review',length=2,loop='once',snapping=20))
  for bone in names:
   for channel in ('rotation','position','scale'):
    if not any(channel in a.get('bones',{}).get(bone,{}) for a in poses):continue
    for index,(a,t) in enumerate(zip(poses,times)):
     default=[1,1,1] if channel=='scale' else [0,0,0]
     v=sample(a.get('bones',{}).get(bone,{}).get(channel,default),t,default)
     if channel=='rotation':v=[-v[0],-v[1],v[2]]
     if channel=='position':v=[-v[0],v[1],v[2]]
     ops.append(dict(op='set_keyframe',animation='review',bone=bone,channel=channel,time=index,value=v,interpolation='linear'))
  plan=WORK/f'{key}.json';plan.write_text(json.dumps(ops,separators=(',',':')))
  group='early' if key.endswith('_early') else 'mutated' if key in ['infected_'+x for x in ['cow','pig','sheep','skeleton','spider','enderman']]+['ender_mutant_zombie'] else 'boss' if 'core' in key or key=='containment_warden' else 'designed'
  rows.append(dict(key=key,name=lang.get('entity.ender_infection.'+key,key),group=group,bones=len(names),cubes=sum(len(b.get('cubes',[])) for b in geo['bones']),chunks=math.ceil(len(ops)/100),resolution=dict(width=desc['texture_width'],height=desc['texture_height']),file=(WORK/f'{key}.bbmodel').as_posix(),texture=(ASSETS/'textures/entity'/f'{key}.png').as_posix(),poses=times,operations=len(ops)))
 (WORK/'manifest.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
 print(json.dumps([dict(key=r['key'],cubes=r['cubes'],chunks=r['chunks']) for r in rows]))

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('key',nargs='?');p.add_argument('chunk',nargs='?',type=int);a=p.parse_args()
 if a.key:
  ops=json.loads((WORK/f'{a.key}.json').read_text());print(json.dumps(ops[a.chunk*100:(a.chunk+1)*100],separators=(',',':')))
 else:prepare()
