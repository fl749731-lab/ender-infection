"""Verify runtime rift meshes, UVs, glow and animation bindings against MCP sources."""
import base64
from collections import Counter
import io
import json
import math

import numpy as np
from PIL import Image

from export_rift_assets import ASSETS, PACK, ROOT, ROWS, groups
from export_legacy_creature_glow import cube_signature


def validate():
    rows=[]
    for source,runtime,height,design_height,hit,duration in ROWS:
        d=json.loads((PACK/'models'/f'{source}.bbmodel').read_text('utf-8'))
        key='infected_'+runtime;scale=height*16/design_height
        geometry=json.loads((ASSETS/'geo'/f'{key}.geo.json').read_text())['minecraft:geometry'][0]
        names={b['name'] for b in geometry['bones']}
        assert len(names)==len(geometry['bones'])==len(groups(d)),key
        for (g,parent),b in zip(groups(d),geometry['bones']):
            assert b['name']==g['name'] and b.get('parent')==parent,key
            assert b['pivot']==[-g['origin'][0]*scale,g['origin'][1]*scale,g['origin'][2]*scale],key
            r=g.get('rotation',[0,0,0]);assert b['rotation']==[-r[0],-r[1],r[2]],key
        source_cubes=[];expected=np.zeros((512,512),dtype=bool);other=np.zeros_like(expected)
        for e in d['elements']:
            if not e.get('export',True):continue
            uv={}
            for face,f in e['faces'].items():
                if f.get('texture') is None:continue
                rect=f['uv'];assert 0<=rect[0]<rect[2]<=512 and 0<=rect[1]<rect[3]<=512,key
                # Refined shell facets have fractional UVs. Select texel centers,
                # preserving the exact floating-point rectangles in the mesh.
                x,y,X,Y=[math.ceil(n-.5) for n in rect]
                emissive=d['textures'][f['texture']].get('render_mode')=='emissive'
                (expected if emissive else other)[y:Y,x:X]=True
                uv[{'east':'west','west':'east'}.get(face,face)]=rect
            lo,hi=e['from'],e['to'];p=e.get('origin',[0,0,0]);r=e.get('rotation',[0,0,0])
            source_cubes.append(cube_signature([-hi[0]*scale,lo[1]*scale,lo[2]*scale],
                [(hi[i]-lo[i])*scale for i in range(3)],uv,[-r[0],-r[1],r[2]],[-p[0]*scale,p[1]*scale,p[2]*scale]))
        actual=[]
        for b in geometry['bones']:
            assert not b.get('parent') or b['parent'] in names,key
            for c in b.get('cubes',[]):
                uv={f:v['uv']+[v['uv'][i]+v['uv_size'][i] for i in range(2)] for f,v in c['uv'].items()}
                actual.append(cube_signature(c['origin'],c['size'],uv,c.get('rotation',[0,0,0]),c.get('pivot',[0,0,0])))
        assert Counter(source_cubes)==Counter(actual),(key,'geometry/UV mismatch')
        assert not (expected&other).any(),(key,'glow and non-glow UV overlap')
        pixels={}
        for t in d['textures']:
            glow=t.get('render_mode')=='emissive';suffix='_glowmask' if glow else ''
            raw=base64.b64decode(t['source'].split(',',1)[1])
            assert (ASSETS/'textures/entity'/f'{key}{suffix}.png').read_bytes()==raw,(key,'texture changed')
            pixels[glow]=np.array(Image.open(io.BytesIO(raw)).convert('RGBA'))
        mask=pixels[True][:,:,3]>0
        assert not (expected&~mask).any(),(key,'emissive UV coverage missing')
        assert not (other&mask).any(),(key,'non-emissive faces glow')
        assert np.array_equal(pixels[True][expected],pixels[False][expected]),(key,'emissive color changed')
        animation=json.loads((ASSETS/'animations'/f'{key}.animation.json').read_text())['animations']
        prefix='animation.'+key+'.'
        required={'idle','walk','special','infection','death'}|({'closed','exposed','deploy'} if runtime=='anchor_guard' else {'phase'})
        assert {prefix+n for n in required}<=animation.keys(),key
        for name,a in animation.items():
            assert a['animation_length']>0 and set(a.get('bones',{}))<=names,(key,name)
        assert animation[prefix+'special']['animation_length']==duration/20,key
        rows.append({'runtime':key,'cubes':len(actual),'bones':len(names),'emissive_pixels':int(expected.sum()),'unused_emissive_texels':int((mask&~expected).sum()),'height':height})
    return rows


if __name__=='__main__':
    result={'status':'PASS','models':validate()}
    (ROOT/'ender_infection_colored/verification/rift_runtime_validation.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))
