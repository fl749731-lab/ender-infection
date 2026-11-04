"""Validate the saved MCP-authored rift models and assemble a portable delivery ZIP."""
import base64
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'colored_units/ender_rift_pack_2026-11-04'
sys.path.insert(0,str(ROOT/'tools/blockbench-mcp'))
from build_rift_models import SPECS
sys.path.insert(0,str(ROOT/'colored_units/design_pack_2026-11-02_v2/source'))
from modelkit import resolve_groups
from check_creature_surfaces import inspect

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

rows=[]
for spec in SPECS:
    p=PACK/'models'/(spec['name']+'.bbmodel');d=json.loads(p.read_text('utf-8'))
    by_id={e['uuid']:e for e in d['elements']};assert len(by_id)==len(d['elements'])
    gs=resolve_groups(d)['outliner'];owners=[];bones={}
    def visit(g):
        assert g['uuid'] not in bones;bones[g['uuid']]=g
        for child in g.get('children',[]):
            if isinstance(child,str):assert child in by_id;owners.append(child)
            else:visit(child)
    for g in gs:visit(g)
    assert sorted(owners)==sorted(by_id)
    images=[];emissive=0
    for t in d['textures']:
        raw=base64.b64decode(t['source'].split(',',1)[1]);im=Image.open(io.BytesIO(raw)).convert('RGBA')
        assert im.size==(512,512);images.append(im)
        (PACK/'textures'/t['name']).write_bytes(raw)
        if t.get('render_mode')=='emissive':emissive+=1
    assert emissive==1 and len(images)==2
    crystal_faces=0
    for e in d['elements']:
        assert all(b>a for a,b in zip(e['from'],e['to'])),e['name']
        for face in e['faces'].values():
            index=face['texture'];assert isinstance(index,int) and 0<=index<2
            uv=face['uv'];assert all(0<=n<=512 for n in uv),e['name']
            assert uv[2]>uv[0] and uv[3]>uv[1]
            if d['textures'][index].get('render_mode')=='emissive':
                crystal_faces+=1
                x,y=int((uv[0]+uv[2])/2),int((uv[1]+uv[3])/2)
                assert images[index].getpixel((x,y))[3]>0,(e['name'],face)
    assert len(d['animations'])==4
    for animation in d['animations']:
        assert animation['length']>0
        for uid,animator in animation['animators'].items():
            assert uid in bones
            for k in animator.get('keyframes',[]):assert 0<=k['time']<=animation['length']
    surfaces=inspect(p);assert surfaces['overlap_faces']==0
    rows.append({'name':spec['name'],'label':spec['label'],'file':str(p.relative_to(PACK)),
                 'cubes':len(d['elements']),'bones':len(bones),'texture_size':[512,512],
                 'emissive_faces':crystal_faces,'animations':[a['name'] for a in d['animations']],
                 'coplanar_overlap_faces':0,'sha256':sha(p)})
views=json.loads((PACK/'verification/mcp_views.json').read_text('utf-8'))
assert len(views)==3 and all(len(v['shots'])==7 for v in views)
saved=json.loads((PACK/'verification/mcp_final_saved.json').read_text('utf-8'))
assert len(saved['projects'])==3 and all(p['saved'] for p in saved['projects'])
report={'status':'PASS','date':'2026-11-04','authored_with':'Blockbench MCP 1.10.1 / Blockbench 5.2.2',
        'models':rows,'native_view_samples':21,'native_saved_projects':3,
        'scope':'Editable MCP models, UV, emissive textures and source animations; integrated into v0.12.0-alpha.1',
        'runtime_export':'tools/export_rift_assets.py',
        'runtime_validation':'ender_infection_colored/verification/rift_runtime_validation.json',
        'limits':['Cuboid adaptation of generated orthographic references','Animation samples are visual checks, not exhaustive collision proof']}
(PACK/'manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
output=ROOT/'dist/ender-rift-models-2026-11-04.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(PACK.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(PACK).as_posix())
    previews=ROOT/'previews/ender_rift_models_2026-11-04'
    for p in sorted(previews.glob('*.png')):
        if p.name=='anchor_framing_probe.png':continue
        z.write(p,'previews/'+p.name)
    concepts=ROOT/'previews/ender_rift_concepts_2026-11-04'
    for p in sorted(concepts.glob('*')):
        if p.is_file():z.write(p,'concepts/'+p.name)
    for p in [ROOT/'tools/blockbench-mcp'/n for n in ['build_rift_models.py','build_rift_models.js','refine_rift_seams.py','refine_anchor_opening.py','review_rift_models.py']]:
        z.write(p,'authoring-history/'+p.name)
    z.write(ROOT/'tools/blockbench-mcp/client.py','authoring-history/client.py')
    z.write(ROOT/'tools/blockbench-mcp/review_models.py','authoring-history/review_models.py')
with zipfile.ZipFile(output) as z:assert z.testzip() is None
print(json.dumps({'status':'PASS','models':len(rows),'cubes':[r['cubes'] for r in rows],
                  'bones':[r['bones'] for r in rows],'native_views':21,
                  'zip':str(output),'bytes':output.stat().st_size,'sha256':sha(output)},ensure_ascii=False))
