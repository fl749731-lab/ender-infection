"""Package two MCP-authored armor rigs and their matching four-slot baked items.

Worn poses come from GeoArmorRenderer's actual humanoid model, not canned clips.
Public texture pixels remain intact; only the project crystal material emits.
"""
import argparse,base64,copy,hashlib,io,json,sys
import numpy as np
from pathlib import Path
from PIL import Image
from export_creature_reworks import build as pack_model,read,encoded,ROOT,ASSETS

PACK=ROOT/'colored_units/armor_rework_v1'
SPECS={name:dict(scale=1,offset_y=0,atlas=[256,128],placements={0:[0,0],1:[128,0]},emissive_materials=[1]) for name in ['brood_heavy','crystal_carapace']}
SLOTS={'helmet':{'armorHead'},'chestplate':{'armorBody','armorRightArm','armorLeftArm'},'leggings':{'armorRightLeg','armorLeftLeg'},'boots':{'armorRightBoot','armorLeftBoot'}}
PIVOTS={'armorHead':[0,24,0],'armorBody':[0,24,0],'armorRightArm':[-5,22,0],'armorLeftArm':[5,22,0],'armorRightLeg':[-2,12,0],'armorLeftLeg':[2,12,0],'armorRightBoot':[-2,12,0],'armorLeftBoot':[2,12,0]}

def item_files(runtime,model,texture):
    # These armor rigs contain eight unrotated roots; preserve cube rotations and
    # inflate when baking item meshes, then center each selected equipment slot.
    from math import sin,cos,radians
    defs={g['uuid']:g['name'] for g in model['groups']};owner={}
    for group in model['outliner']:
        for uid in group['children']:owner[uid]=defs[group['uuid']]
    result={}
    for slot,names in SLOTS.items():
        key=runtime+'_'+slot;quads=[]
        for e in model['elements']:
            if owner[e['uuid']] not in names:continue
            amount=e.get('inflate',0);a=np.array(e['from'])-amount;b=np.array(e['to'])+amount;x,y,z=a;X,Y,Z=b
            pivot=np.array(e.get('origin',[0,0,0]));rx,ry,rz=map(radians,e.get('rotation',[0,0,0]));cx,sx,cy,sy,cz,sz=cos(rx),sin(rx),cos(ry),sin(ry),cos(rz),sin(rz)
            matrix=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
            faces={'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],'up':[(x,Y,Z),(x,Y,z),(X,Y,z),(X,Y,Z)],'down':[(x,y,z),(x,y,Z),(X,y,Z),(X,y,z)]}
            for face,vertices in faces.items():
                f=e['faces'][face]
                if f.get('texture') is None:continue
                points=(matrix@(np.array(vertices)-pivot).T).T+pivot
                u,v,U,V=f['uv'];offset=128 if f['texture']==1 else 0
                uv=[((u+offset)/256,v/128),((U+offset)/256,v/128),((U+offset)/256,V/128),((u+offset)/256,V/128)]
                rotation=int(f.get('rotation',0))//90
                if rotation:uv=uv[-rotation:]+uv[:-rotation]
                normal=matrix@np.array({'north':[0,0,-1],'south':[0,0,1],'east':[1,0,0],'west':[-1,0,0],'up':[0,1,0],'down':[0,-1,0]}[face])
                if np.dot(np.cross(points[1]-points[0],points[2]-points[0]),normal)<0:points=points[::-1];uv=uv[::-1]
                quads.append((points,uv))
        points=np.concatenate([q[0] for q in quads]);low,high=points.min(0),points.max(0);center=(low+high)/2;scale=14/max(high-low);lines=[f'mtllib {key}.mtl','usemtl atlas'];index=0
        for points,uv in quads:
            normal=np.cross(points[1]-points[0],points[2]-points[0]);normal/=np.linalg.norm(normal);points=((points-center)*scale+8)/16
            for p in points:lines.append('v '+' '.join(f'{v:.7f}' for v in p))
            for u,v in uv:lines.append(f'vt {u:.8f} {v:.8f}')
            for _ in range(4):lines.append('vn '+' '.join(f'{v:.6f}' for v in normal))
            lines.append('f '+' '.join(f'{index+j}/{index+j}/{index+j}' for j in range(1,5)));index+=4
        result[f'models/item/design/{key}.obj']=('\n'.join(lines)+'\n').encode()
        result[f'models/item/design/{key}.mtl']=(f'newmtl atlas\nKd 1 1 1\nd 1\nmap_Kd ender_infection:item/design/{key}\n').encode()
        result[f'textures/item/design/{key}.png']=texture
        display={'gui':dict(rotation=[30,225,0],translation=[0,0,0],scale=[.9]*3),'thirdperson_righthand':dict(rotation=[0,-90,0],translation=[0,1,0],scale=[1]*3),'firstperson_righthand':dict(rotation=[0,-90,0],translation=[1,2,0],scale=[1]*3)}
        result[f'models/item/{key}.json']=encoded(dict(loader='forge:obj',model=f'ender_infection:models/item/design/{key}.obj',flip_v=False,automatic_culling=False,shade_quads=True,render_type='minecraft:cutout',textures=dict(particle=f'ender_infection:item/design/{key}'),parent='minecraft:item/handheld',display=display))
    return result

def build(runtime):
    provenance=read(PACK/'provenance.json');source=PACK/provenance['source_file']
    if hashlib.sha256(source.read_bytes()).hexdigest()!=provenance['source_json_sha256']:raise ValueError('Upstream armor source changed')
    model=read(PACK/'models'/(runtime+'.bbmodel'))
    if runtime=='brood_heavy' and model['textures'][0]['source']!=read(source)['textures'][0]['source']:raise ValueError('Original armor artwork changed')
    crystal=Image.open(io.BytesIO(base64.b64decode(model['textures'][1]['source'].split(',',1)[1]))).convert('RGBA')
    for e in model['elements']:
        for face in e['faces'].values():
            if face.get('texture')!=1:continue
            u,v,U,V=face['uv'];box=tuple(map(round,(min(u,U),min(v,V),max(u,U),max(v,V))))
            if crystal.crop(box).getchannel('A').getextrema()!=(255,255):raise ValueError('Crystal face has transparent holes: '+e['name'])
    files,row=pack_model(runtime,pack=PACK,spec=SPECS[runtime]);geo=json.loads(files[f'geo/{runtime}.geo.json'])['minecraft:geometry'][0]
    if {b['name'] for b in geo['bones']}!=set(PIVOTS):raise ValueError('Armor must have exactly the eight equipment bones')
    for b in geo['bones']:
        if b.get('parent') or b['pivot']!=PIVOTS[b['name']]:raise ValueError('Unmapped armor parent/pivot '+b['name'])
        for c in b.get('cubes',[]):
            for uv in c['uv'].values():
                if any(not 0<=uv['uv'][a]<=SPECS[runtime]['atlas'][a] or not 0<=uv['uv'][a]+uv['uv_size'][a]<=SPECS[runtime]['atlas'][a] for a in range(2)):raise ValueError('Armor UV outside atlas')
    if json.loads(files[f'animations/{runtime}.animation.json'])['animations']:raise ValueError('Armor must follow live humanoid poses')
    for suffix in ['', '_glowmask']:files[f'textures/armor/{runtime}{suffix}.png']=files.pop(f'textures/entity/{runtime}{suffix}.png')
    if row['emissive_pixels']==0:raise ValueError('Missing crystal mask')
    files.update(item_files(runtime,model,files[f'textures/armor/{runtime}.png']))
    row.update(source='brood_heavy_armor' if runtime=='brood_heavy' else 'crystal_carapace_light_armor',kind='armor_set',pipeline='tools/export_armor_reworks.py',pose_source='Native HumanoidModel through GeoArmorRenderer; no canned clips')
    return files,row

def export(update_manifest=True):
    rows=[]
    for runtime in SPECS:
        files,row=build(runtime);rows.append(row)
        for rel,data in files.items():p=ASSETS/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    p=ASSETS/'third_party/model_credits.json';credits=read(p);source=read(PACK/'provenance.json')
    credits['models']=[r for r in credits['models'] if r['runtime'] not in SPECS]+[dict(runtime='brood_heavy',author=source['author'],original=source['original'],url=source['page'],license=source['license'],license_url=source['license_url'],source_pack=PACK.name,creator_rights=source['creator_rights'],changes='; '.join(source['changes']))]
    credits['original_reworks']=[r for r in credits.get('original_reworks',[]) if r['runtime'] not in SPECS]+[dict(runtime='crystal_carapace',author='Ender Infection project',original_source=source['light_source'],source_pack=PACK.name,changes='; '.join(source['light_changes']))];p.write_bytes(encoded(credits))
    if update_manifest:
        p=ROOT/'ender_infection_colored/verification/design_asset_manifest.json';m=read(p);m['assets']=[r for r in m['assets'] if r['runtime'] not in SPECS]+rows;p.write_bytes(encoded(m))
    return rows

def validate():
    rows=[]
    for name in SPECS:
        files,row=build(name);rows.append(row)
        for rel,expected in files.items():
            if (ASSETS/rel).read_bytes()!=expected:raise ValueError('Armor differs from sealed source: '+rel)
    return rows

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--verify',action='store_true');a=p.parse_args();print(json.dumps(validate() if a.verify else export()))
