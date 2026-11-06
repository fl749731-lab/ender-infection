"""Package the licensed MCP-authored staff, including item display and crystal emission."""
import argparse,json,hashlib,io
from PIL import Image
from export_creature_reworks import build as pack_model,read,encoded,ASSETS,ROOT

PACK=ROOT/'colored_units/resonance_staff_public_v1'
RUNTIME='resonance_staff'
SPEC=dict(scale=.8,offset_y=0,atlas=[256,128],placements={0:[0,0],1:[128,0]},emissive_materials=[1])

def build():
    provenance=read(PACK/'provenance.json'); source=PACK/provenance['source_file']
    if hashlib.sha256(source.read_bytes()).hexdigest()!=provenance['source_json_sha256']:raise ValueError('Upstream staff source changed')
    model=read(PACK/'models/resonance_staff.bbmodel')
    if model['textures'][0]['source']!=read(source)['textures'][0]['source']:raise ValueError('Original staff artwork changed')
    files,row=pack_model(RUNTIME,pack=PACK,spec=SPEC)
    for suffix in ['', '_glowmask']:
        files[f'textures/item/design/{RUNTIME}{suffix}.png']=files.pop(f'textures/entity/{RUNTIME}{suffix}.png')
    def transform(rotation,translation,scale):return dict(rotation=rotation,translation=translation,scale=[scale]*3)
    display={
        'gui':transform([0,0,-30],[0,-3.6,0],.43),
        'ground':transform([0,0,0],[0,4,0],.4),
        'fixed':transform([0,180,0],[0,-3,0],.45),
        'thirdperson_righthand':transform([90,0,0],[0,1,0],.78),
        'thirdperson_lefthand':transform([90,0,0],[0,1,0],.78),
        'firstperson_righthand':transform([0,-20,-10],[-2,-1,-3],.45),
        'firstperson_lefthand':transform([0,-20,-10],[-2,-1,-3],.45),
        'head':transform([0,0,0],[0,14,0],.4),
    }
    files[f'models/item/{RUNTIME}.json']=encoded(dict(parent='builtin/entity',textures=dict(particle=f'ender_infection:item/design/{RUNTIME}'),display=display))
    animations=json.loads(files[f'animations/{RUNTIME}.animation.json'])['animations']
    if set(animations)!={'animation.resonance_staff.idle','animation.resonance_staff.cast'}:raise ValueError('Missing staff animations')
    if animations['animation.resonance_staff.cast']['animation_length']!=1:raise ValueError('Cast recovery must finish before 25-tick cooldown')
    if row['emissive_pixels']<=0:raise ValueError('Missing crystal emission')
    glow=Image.open(io.BytesIO(files['textures/item/design/resonance_staff_glowmask.png'])).convert('RGBA')
    geometry=json.loads(files['geo/resonance_staff.geo.json'])['minecraft:geometry'][0]
    for bone in geometry['bones']:
        if bone['name']!='crystal_core':continue
        for cube in bone.get('cubes',[]):
            for face in cube['uv'].values():
                u,v=face['uv'];du,dv=face['uv_size'];box=tuple(map(round,(min(u,u+du),min(v,v+dv),max(u,u+du),max(v,v+dv))))
                if glow.crop(box).getchannel('A').getextrema()!=(255,255):raise ValueError('Crystal UV contains transparent holes')
    row.update(source='resonance_staff',kind='weapon',pipeline='tools/export_staff_rework.py',public_base='Void Staff by @alexander')
    return files,row

def export(update_manifest=True):
    files,row=build()
    for relative,data in files.items():
        path=ASSETS/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    path=ASSETS/'third_party/model_credits.json';credits=read(path);source=read(PACK/'provenance.json')
    credits['models']=[e for e in credits['models'] if e['runtime']!=RUNTIME]+[dict(runtime=RUNTIME,author=source['author'],original=source['original'],url=source['page'],license=source['license'],license_url=source['license_url'],source_pack=PACK.name,changes='; '.join(source['changes']))]
    path.write_bytes(encoded(credits))
    if update_manifest:
        path=ROOT/'ender_infection_colored/verification/design_asset_manifest.json';m=read(path);m['assets']=[e for e in m['assets'] if e['runtime']!=RUNTIME]+[row];path.write_bytes(encoded(m))
    return [row]

def validate():
    files,row=build()
    for relative,expected in files.items():
        if (ASSETS/relative).read_bytes()!=expected:raise ValueError('Staff runtime differs from sealed MCP source: '+relative)
    return [row]

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--verify',action='store_true');a=p.parse_args();print(json.dumps(validate() if a.verify else export()))
