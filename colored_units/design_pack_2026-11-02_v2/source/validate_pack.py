from pathlib import Path
from collections import Counter
import json,base64,io,math,hashlib
import numpy as np
from PIL import Image
from build_pack import ROOT
from modelkit import prepare_render

def main():
    manifest=json.loads((ROOT/'manifest.json').read_text(encoding='utf-8'));results=[];checks=Counter();files=list(ROOT.rglob('*.bbmodel'))
    assert len(files)==manifest['count']==63
    for record in manifest['models']:
        file=ROOT/record['file'];d=json.loads(file.read_text(encoding='utf-8'));assert d['meta']['format_version']=='4.10';assert d['meta']['model_format']=='free'
        cubes={e['uuid']:e for e in d['elements']};assert len(cubes)==len(d['elements'])==record['cubes'];groups={};refs=[]
        def walk(g):
            assert g['uuid'] not in groups;groups[g['uuid']]=g
            for child in g['children']:
                if isinstance(child,dict):walk(child)
                else:refs.append(child)
        for g in d['outliner']:walk(g)
        assert set(refs)==set(cubes) and len(refs)==len(set(refs));assert not(set(cubes)&set(groups));checks['outliner_ownership']+=1
        texture=d['textures'][0];assert texture['source'].startswith('data:image/png;base64,')
        raw=base64.b64decode(texture['source'].split(',',1)[1],validate=True);im=Image.open(io.BytesIO(raw)).convert('RGBA');png=Image.open(file.with_suffix('.png')).convert('RGBA')
        assert im.size==(d['resolution']['width'],d['resolution']['height'])==tuple(record['atlas']);assert np.array_equal(np.asarray(im),np.asarray(png));alpha=np.asarray(im)[:,:,3];checks['embedded_texture_match']+=1
        for e in cubes.values():
            for field in ('from','to','origin','rotation'):assert len(e[field])==3 and all(math.isfinite(x) for x in e[field])
            assert all(e['to'][i]>e['from'][i] for i in range(3));assert len(e['faces'])==6
            dims=np.asarray(e['to'])-np.asarray(e['from']);wh={'north':(dims[0],dims[1]),'south':(dims[0],dims[1]),'east':(dims[2],dims[1]),'west':(dims[2],dims[1]),'up':(dims[0],dims[2]),'down':(dims[0],dims[2])}
            for name,f in e['faces'].items():
                u,v,U,V=f['uv'];assert 0<=u<U<=im.width and 0<=v<V<=im.height;assert f['texture']==0
                assert np.all(alpha[int(v):int(V),int(u):int(U)]>0),(record['id'],e['name'],name)
                assert (U-u,V-v)==tuple(max(1,math.ceil(x)) for x in wh[name]),(record['id'],e['name'],name,'UV density')
                checks['painted_uv_faces']+=1
        for a in d['animations']:
            assert a['length']>0
            for gid,animator in a['animators'].items():
                assert gid in groups and animator['name']==groups[gid]['name']
                keys=animator['keyframes'];assert len(set(k['uuid'] for k in keys))==len(keys)
                for k in keys:
                    assert 0<=k['time']<=a['length'] and k['channel'] in ('rotation','position','scale')
                    vals=[float(k['data_points'][0][p]) for p in ('x','y','z')];assert all(math.isfinite(v) for v in vals)
                    if k['channel']=='scale':assert all(v>0 for v in vals)
            if a['loop']=='loop':
                for animator in a['animators'].values():
                    for ch in ('rotation','position','scale'):
                        keys=sorted((k for k in animator['keyframes'] if k['channel']==ch),key=lambda k:k['time'])
                        if not keys:continue
                        av=np.array([float(keys[0]['data_points'][0][p]) for p in ('x','y','z')]);bv=np.array([float(keys[-1]['data_points'][0][p]) for p in ('x','y','z')])
                        if ch=='rotation':assert np.allclose((bv-av+180)%360-180,0)
                        else:assert np.allclose(av,bv)
            # Sampled articulated world transforms verify nested bone/keyframe loading, not just key names.
            if record['category']!='scenes':
                for t in (0,a['length']*.4,a['length']*.8,a['length']):
                    posed=prepare_render(d,(a,t));assert all(np.isfinite(np.asarray(e['_pose'])).all() for e in posed['elements'])
            checks['animation_clips']+=1
        names={g['name'] for g in groups.values()}
        if record['id'] in ('crystal_hunter','rib_crystal_caster','crystal_shield_sentinel'):
            assert {'arm_-1','arm_1','leg_-1','leg_1'}<=names;checks['paired_humanoid_limbs']+=1
        if record['id']=='parasitic_spider_mother':assert len([n for n in names if n.startswith('leg_') and not n.endswith('lower')])==8;checks['eight_spider_legs']+=1
        if record['id']=='ravager_siege_concept':assert len([n for n in names if n.startswith('leg_') and not n.endswith('lower')])==4;checks['four_ravager_legs']+=1
        if record['id']=='walking_brood_crab':assert len([n for n in names if n.startswith('leg_') and not n.endswith('lower')])==6;checks['six_crab_legs']+=1
        if record['category']=='scenes':
            for i in range(1,5):assert 'level_'+str(i) in names
            assert any('stair_to_next_level' in n for n in names);checks['four_level_laboratory']+=1
        results.append({'id':record['id'],'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'cubes':len(cubes),'bones':len(groups),'animations':len(d['animations'])})
    report={'status':'passed','models':len(results),'by_category':dict(Counter(r['category'] for r in manifest['models'])),'checks':dict(checks),'results':results,
            'scope':'JSON ownership, embedded PNG, every visible UV face and texel density, animation references and sampled transforms; no gameplay integration test.'}
    (ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({k:v for k,v in report.items() if k!='results'},ensure_ascii=False,indent=2))
if __name__=='__main__':main()
