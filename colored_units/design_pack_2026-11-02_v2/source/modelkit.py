"""Original, portable Blockbench projects. 16 model units = one Minecraft block."""
from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw
import math, json, uuid, base64, io, copy, random, zlib
import numpy as np

FACES=('north','south','east','west','up','down')
PALETTES={
 'shell':['#272432','#35303f','#46404d','#56505b'],
 'bone':['#625b65','#7b737c','#968d94','#b1a6ac'],
 'tissue':['#2a1b35','#382342','#492d53','#5a3865'],
 'membrane':['#33213f','#462c53','#583664','#6a4479'],
 'crystal':['#43245e','#643586','#9350b8','#ba77d8'],
 'glow':['#8b4bb2','#b674d3','#d7a6ee','#eed6fb'],
 'white':['#bcbab7','#cecdca','#deded8','#e9e8e2'],
 'brick':['#626166','#777579','#85858a','#959297'],
 'steel':['#50545b','#666b70','#80858a','#999da0'],
 'darkmetal':['#252a31','#333840','#464c54','#5a6066'],
 'pad':['#34313f','#474351','#56515f','#67616d'],
 'wood':['#453b34','#584a3d','#6c5948','#806b55'],
 'zskin':['#3f562c','#506b36','#658145','#759050'],
 'shirt':['#216a72','#2c7f86','#3c969a','#55a8a9'],
 'pants':['#302656','#3e336c','#504283','#625294'],
 'skull':['#999389','#b3ada0','#cbc6b7','#d8d3c3'],
 'glass':['#778d99','#8da4af','#a9bec7','#cadae0'],
 'liquid':['#39244c','#4e3167','#674185','#8b60aa'],
 'black':['#15151d','#1e1d27','#282530','#37323d'],
 'warning':['#786747','#a08a55','#c0a467','#dac087'],
}

def rgb(s):return tuple(int(s[i:i+2],16) for i in (1,3,5))
def stable(s):return zlib.crc32(s.encode('utf-8'))
def ident(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'ender_design_pack_20261102/'+s))
def rotation_matrix(rot):
    ax,ay,az=map(math.radians,rot);cx,sx=math.cos(ax),math.sin(ax);cy,sy=math.cos(ay),math.sin(ay);cz,sz=math.cos(az),math.sin(az)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1.]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1.,0,0],[0,cx,-sx],[0,sx,cx]])
def transform_at(pivot,rot=(0,0,0),pos=(0,0,0),scale=(1,1,1)):
    r=rotation_matrix(rot)@np.diag(scale);p=np.asarray(pivot,dtype=float);t=np.eye(4);t[:3,:3]=r;t[:3,3]=p+np.asarray(pos)-r@p;return t

class Atlas:
    def __init__(self):self.tiles={}
    def get(self,material,w,h,variant=0):
        k=(material,max(1,math.ceil(w)),max(1,math.ceil(h)),variant%3)
        if k not in self.tiles:self.tiles[k]=None
        return k
    def paint(self,k):
        mat,w,h,v=k;p=PALETTES.get(mat,PALETTES['shell']);im=Image.new('RGBA',(w,h));d=ImageDraw.Draw(im)
        for yy in range(h):
            for xx in range(w):
                # Broad, low-contrast native pixel clusters; no photographic noise.
                cluster=((xx//3)*7+(yy//3)*11+v*5)%9
                index=1 if cluster<5 else 2 if cluster<8 else 0
                if mat in ('crystal','glow'):
                    index=min(3,(xx*3)//max(1,w)+(1 if yy<max(1,h//6) else 0))
                elif mat in ('tissue','membrane'):
                    index=0 if (xx+yy//4+v*2)%9<2 else 1 if cluster<6 else 2
                elif mat=='wood':index=(xx//2+v)%3
                elif mat=='glass':index=3 if (xx+yy)//2%15==0 else 1
                color=rgb(p[index]);alpha=76 if mat=='glass' else 220 if mat=='liquid' else 255
                im.putpixel((xx,yy),(*color,alpha))
        if mat in ('white','brick'):
            tile=16 if mat=='white' else 8
            for x in range(0,w,tile):d.line((x,0,x,h),fill=(*rgb(p[0]),255))
            for y in range(0,h,tile):d.line((0,y,w,y),fill=(*rgb(p[0]),255))
            if w>3 and h>3:d.point((1,1),fill=(*rgb(p[3]),255))
        if mat in ('steel','darkmetal') and w>=5 and h>=5:
            d.line((0,0,w-1,0),fill=(*rgb(p[3]),255));d.line((0,0,0,h-1),fill=(*rgb(p[2]),255))
            d.line((0,h-1,w-1,h-1),fill=(*rgb(p[0]),255))
            if w>=8 and h>=8:
                for x,y in ((2,2),(w-3,2),(2,h-3),(w-3,h-3)):d.point((x,y),fill=(*rgb(p[0]),255))
        if mat in ('bone','shell','skull') and w>=5 and h>=5:
            d.line((1,0,w-2,0),fill=(*rgb(p[3]),255));d.line((0,1,0,h-2),fill=(*rgb(p[2]),255))
            d.line((1,h-1,w-1,h-1),fill=(*rgb(p[0]),255))
            if w>=9 and h>=8:
                x=max(2,w//3+v);d.line([(x,2),(x,min(h-3,4)),(x+1,min(h-2,5))],fill=(*rgb(p[0]),255))
        if mat=='checker':
            for y in range(h):
                for x in range(w):
                    c=(207,207,198) if (x//8+y//8)%2==0 else (49,49,55)
                    v2=((x//3+y//4)%3-1)*3;im.putpixel((x,y),tuple(max(0,min(255,a+v2)) for a in c)+(255,))
        if mat in ('face_zombie','face_skull'):
            base='zskin' if mat=='face_zombie' else 'skull';im=self.paint((base,w,h,v));d=ImageDraw.Draw(im)
            for lo,hi in ((.12,.38),(.62,.88)):
                d.rectangle((int(w*lo),int(h*.40),max(int(w*lo),int(w*hi)-1),max(int(h*.4),int(h*.58)-1)),fill=(20,20,24,255))
            if mat=='face_skull':
                d.rectangle((w//2-1,int(h*.57),w//2,int(h*.74)),fill=(40,35,43,255))
                d.line((max(0,w//5),int(h*.81),w-1-w//5,int(h*.81)),fill=(56,46,60,255))
        if mat=='monitor':
            im=Image.new('RGBA',(w,h),(18,27,28,255));d=ImageDraw.Draw(im)
            for y in range(2,max(3,h-1),3):d.line((2,y,max(2,w-3),y),fill=(87,116,99,255))
            if w>=8:d.rectangle((w//2,2,w//2+1,max(2,h-3)),fill=(133,158,134,255))
        if mat=='rune':
            im=Image.new('RGBA',(w,h),(44,28,58,255));d=ImageDraw.Draw(im)
            d.line([(w//4,h//4),(w*3//4,h//4),(w*3//4,h//2),(w//2,h//2),(w//2,h*3//4)],fill=(178,107,215,255),width=1)
        return im
    def pack(self):
        tiles=sorted(self.tiles,key=lambda k:(-k[2],-k[1],k[0],k[3]));size=64
        while True:
            x=y=row=2;positions={};failed=False
            for k in tiles:
                _,w,h,_=k
                if w+4>size or h+4>size:failed=True;break
                if x+w+2>size:x=2;y+=row+4;row=0
                if y+h+2>size:failed=True;break
                positions[k]=(x,y);x+=w+4;row=max(row,h)
            if not failed:break
            size*=2
            if size>4096:raise ValueError('Texture atlas too large')
        image=Image.new('RGBA',(size,size))
        for k,(x,y) in positions.items():
            tile=self.paint(k);w,h=tile.size
            image.paste(tile,(x,y))
            image.paste(tile.crop((0,0,1,h)),(x-1,y));image.paste(tile.crop((w-1,0,w,h)),(x+w,y))
            image.paste(tile.crop((0,0,w,1)),(x,y-1));image.paste(tile.crop((0,h-1,w,h)),(x,y+h))
        return image,positions

class Model:
    def __init__(self,key,label,category,role='',scale_note='16 units = 1 block'):
        self.key,self.label,self.category,self.role=key,label,category,role;self.atlas=Atlas();self.groups={};self.parent={};self.roles={};self.count=0
        self.data={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':label+' | '+key,'model_identifier':key,
          'resolution':{'width':64,'height':64},'elements':[],'outliner':[],'textures':[],'animations':[],
          'notes':f'Original concept conversion. {scale_note}. Editable cuboids, embedded native-density UV atlas. Design source 2026-11-02. Game integration is separate.'}
        self.group('root',(0,0,0),None)
    def group(self,name,pivot=(0,0,0),parent='root',role=None,phase=0,visible=True):
        if name in self.groups:return name
        g={'name':name,'uuid':ident(self.key+'/bone/'+name),'origin':list(pivot),'rotation':[0,0,0],'children':[],'export':True,'isOpen':False,'visibility':visible}
        self.groups[name]=g;self.parent[name]=parent
        if parent is None:self.data['outliner'].append(g)
        else:self.groups[parent]['children'].append(g)
        if role:self.roles[name]=(role,phase)
        return name
    def box(self,name,a,b,mat='shell',bone='root',rot=(0,0,0),pivot=None,faces=None,variant=0):
        self.count+=1;name=name+'_'+str(self.count);a=list(map(float,a));b=list(map(float,b));assert all(b[i]>a[i] for i in range(3)),(name,a,b)
        w,h,d=[b[i]-a[i] for i in range(3)];sizes={'north':(w,h),'south':(w,h),'east':(d,h),'west':(d,h),'up':(w,d),'down':(w,d)}
        e={'name':name,'uuid':ident(self.key+'/cube/'+name),'type':'cube','from':a,'to':b,'origin':list(self.groups[bone]['origin'] if pivot is None else pivot),
           'rotation':list(rot),'box_uv':False,'visibility':True,'export':True,'faces':{f:{'uv':[0,0,1,1],'texture':0,'_tile':self.atlas.get((faces or {}).get(f,mat),*sizes[f],variant)} for f in FACES}}
        self.data['elements'].append(e);self.groups[bone]['children'].append(e['uuid']);return e
    def centered(self,name,c,size,mat='shell',bone='root',**kw):
        return self.box(name,[c[i]-size[i]/2 for i in range(3)],[c[i]+size[i]/2 for i in range(3)],mat,bone,**kw)
    def remove(self,predicate):
        ids={e['uuid'] for e in self.data['elements'] if predicate(e)}
        self.data['elements']=[e for e in self.data['elements'] if e['uuid'] not in ids]
        for g in self.groups.values():g['children']=[c for c in g['children'] if not isinstance(c,str) or c not in ids]
    def beveled(self,name,c,size,mat='shell',bone='root'):
        x,y,z=c;w,h,d=size
        self.centered(name+'_center',(x,y,z),(w,h*.6,d),mat,bone)
        for s in (-1,1):
            self.centered(name+'_chamfer',(x,y+s*h*.38,z),(w*.87,h*.16,d*.89),mat,bone)
            self.centered(name+'_cap',(x,y+s*h*.48,z),(w*.7,h*.04,d*.71),mat,bone)
    def plate(self,name,c,size,bone='body',rot=(0,0,0),mat='bone'):
        # Stepped carapace plates with a dark inset and a highlighted rim.
        x,y,z=c;w,h,d=size
        self.centered(name+'_underlay',c,(w*1.03,h*1.04,d*1.08),'shell',bone,rot=rot,pivot=c)
        self.centered(name+'_face',(x,y+h*.08,z-d*.12),(w*.94,h*.84,d*.91),mat,bone,rot=rot,pivot=c)
        self.centered(name+'_ridge',(x,y+h*.13,z-d*.52),(max(.55,w*.12),h*.62,max(.35,d*.16)),'bone',bone,rot=rot,pivot=c)
    def beam(self,name,a,b,width=2,depth=None,mat='bone',bone='root',joint=False):
        d=np.asarray(b,dtype=float)-np.asarray(a,dtype=float);length=float(np.linalg.norm(d));assert length>.01
        rx=math.degrees(math.atan2(d[2],math.hypot(d[0],d[1])));rz=math.degrees(math.atan2(-d[0],d[1]));dep=depth or width
        self.box(name,(a[0]-width/2,a[1],a[2]-dep/2),(a[0]+width/2,a[1]+length,a[2]+dep/2),mat,bone,(rx,0,rz),a)
        if joint:self.centered(name+'_joint',a,(width*1.3,)*3,mat,bone)
    def crystal(self,name,base,width,height,bone='root',rot=(0,0,0)):
        x,y,z=base
        for j,(lo,hi,r) in enumerate(((0,.38,.47),(.38,.52,.42),(.52,.65,.35),(.65,.76,.28),(.76,.86,.20),(.86,.94,.13),(.94,1,.045))):
            self.box(name+str(j),(x-width*r,y+height*lo,z-width*r),(x+width*r,y+height*hi,z+width*r),'glow' if j==6 else 'crystal',bone,rot,base)
        # A second narrower facet produces an asymmetric crystal rather than a smooth bottle.
        self.box(name+'_facet',(x-width*.21,y+height*.05,z-width*.52),(x+width*.08,y+height*.64,z-width*.33),'glow',bone,rot,base)
    def sac(self,name,c,size,bone='root',mat='membrane',ribs=True):
        x,y,z=c;w,h,d=size
        steps=7 if max(size)>=6 else 5
        for i in range(steps):
            ny=((i+.5)/steps-.5)*2
            for j in range(steps):
                nz=((j+.5)/steps-.5)*2;remaining=1-ny*ny-nz*nz
                if remaining<=.035:continue
                span=w*math.sqrt(remaining)
                self.centered(name+'_lobule',(x,y+ny*h/2,z+nz*d/2),(span,h/steps,d/steps),mat,bone,variant=(i+j)%3)
        if ribs:
            for side in (-1,1):
                for xx in (-.27,.27):
                    previous=None
                    for i in range(5):
                        yy=-.4+i*.2;f=math.sqrt(max(.1,1-(yy*1.8)**2));p=(x+xx*w*f,y+yy*h,z+side*d*f*.43)
                        if previous:self.beam(name+'_rib',previous,p,max(.45,min(w,d)*.065),mat='shell',bone=bone)
                        previous=p
    def eye(self,name,c,w=2,bone='head'):
        self.centered(name+'_socket',c,(w+1,2.1,.8),'black',bone);self.centered(name+'_glint',(c[0],c[1],c[2]-.5),(w,1,.3),'glow',bone)
    def limb(self,name,hip,knee,foot,width=2,mat='tissue',phase=0,parent='body',role='leg',toes=True):
        self.group(name,hip,parent,role,phase);self.beam(name+'_upper',hip,knee,width,mat=mat,bone=name,joint=True)
        self.group(name+'_lower',knee,name,'lower',phase);self.beam(name+'_lower',knee,foot,max(1,width*.75),mat=mat if mat=='pants' else 'bone',bone=name+'_lower',joint=True)
        direction=np.asarray(knee)-np.asarray(hip)
        for j,t in enumerate(() if mat=='pants' else (.23,.58)):
            p=np.asarray(hip)+direction*t
            self.plate(name+'_thigh_plate',p,(width*1.28,width*1.45,width*.75),name,rot=(0,0,phase*.5),mat='bone')
        direction=np.asarray(foot)-np.asarray(knee)
        for j,t in enumerate(() if mat=='pants' else (.2,.5,.78)):
            p=np.asarray(knee)+direction*t
            self.centered(name+'_shin_cuff',p,(width*1.05,max(.6,width*.48),width*1.05),'shell',name+'_lower')
        if mat!='pants':self.centered(name+'_knee_cap',(knee[0],knee[1],knee[2]-width*.48),(width*1.45,width*1.25,width*.6),'bone',name+'_lower')
        if toes:
            self.centered(name+'_foot',(foot[0],max(1,foot[1]),foot[2]-.8),(width*1.4,2.1,width*2.1),'shell',name+'_lower')
            for s in (-1,1):self.centered(name+'_toe',(foot[0]+s*width*.35,max(.6,foot[1]-.4),foot[2]-width),(max(.5,width*.35),1,max(1,width)), 'bone',name+'_lower')
        else:
            self.beam(name+'_tarsus',foot,(foot[0]*1.05,max(.1,foot[1]-.8),foot[2]-width*1.4),max(.45,width*.5),mat='shell',bone=name+'_lower')
    def append(self,other,offset=(0,0,0),scale=1,rot_y=0,parent='root',prefix='prop'):
        # Flatten assembled static scene transforms while preserving each component in its own group.
        mat=rotation_matrix((0,rot_y,0));off=np.asarray(offset,dtype=float);group=self.group(prefix,offset,parent)
        group_world={};cube_groups={}
        def collect(g,previous):
            value=previous@transform_at(g['origin'],g.get('rotation',(0,0,0)));group_world[g['uuid']]=value
            for child in g['children']:
                if isinstance(child,dict):collect(child,value)
                else:cube_groups[child]=value
        for g in other.data['outliner']:collect(g,np.eye(4))
        for e in other.data['elements']:
            a,b=np.asarray(e['from']),np.asarray(e['to']);center=(a+b)/2;size=(b-a)*scale
            whole=cube_groups[e['uuid']]@transform_at(e['origin'],e.get('rotation',(0,0,0)));source_r=whole[:3,:3];posed=source_r@center+whole[:3,3]
            c=mat@(posed*scale)+off
            # Matrix -> Euler for Rz*Ry*Rx, preserving already rotated cuboids.
            rr=mat@source_r;ry=math.asin(max(-1.,min(1.,-rr[2,0])));cy=math.cos(ry)
            rx=math.atan2(rr[2,1],rr[2,2]) if abs(cy)>1e-6 else 0
            rz=math.atan2(rr[1,0],rr[0,0]) if abs(cy)>1e-6 else math.atan2(-rr[0,1],rr[1,1])
            mats={f:e['faces'][f]['_tile'][0] for f in FACES}
            self.centered(prefix+'_'+e['name'],c,size,mats['north'],group,rot=tuple(map(math.degrees,(rx,ry,rz))),pivot=c,faces=mats)
        return group
    def animation(self,name,length,loop,tracks):
        anim={'name':'animation.'+self.key+'.'+name,'uuid':ident(self.key+'/animation/'+name),'length':length,'loop':'loop' if loop else 'once','snapping':20,'animators':{}}
        for bone,channels in tracks.items():
            assert bone in self.groups,bone;keyframes=[]
            for channel,frames in channels.items():
                for time,value in frames:
                    keyframes.append({'uuid':ident(self.key+name+bone+channel+str(time)),'channel':channel,'time':time,'data_points':[dict(zip(('x','y','z'),map(lambda n:str(round(n,5)),value)))],'interpolation':'linear','color':-1})
            anim['animators'][self.groups[bone]['uuid']]={'name':bone,'type':'bone','keyframes':keyframes}
        self.data['animations'].append(anim)
    def creature_animations(self,stationary=False,flying=False):
        idle={'body':{'position':[(0,(0,0,0)),(1,(0,.3,0)),(2,(0,0,0))]}} if 'body' in self.groups else {}
        for bone,(role,phase) in self.roles.items():
            if role=='sac':idle[bone]={'scale':[(0,(1,1,1)),(1,(1.03,1.04,1.03)),(2,(1,1,1))]}
            if role=='wing':idle[bone]={'rotation':[(0,(0,0,phase*12)),(.5,(0,0,-phase*14)),(1,(0,0,phase*12)),(1.5,(0,0,-phase*14)),(2,(0,0,phase*12))]}
            if role=='head':idle[bone]={'rotation':[(0,(0,-2,0)),(1,(0,2,0)),(2,(0,-2,0))]}
        if flying:idle['root']={'position':[(0,(0,0,0)),(1,(0,1,0)),(2,(0,0,0))]}
        self.animation('idle',2,True,idle)
        if not stationary:
            walk={'root':{'position':[(0,(0,0,0)),(.25,(0,.4,0)),(.5,(0,0,0)),(.75,(0,.4,0)),(1,(0,0,0))]}}
            for bone,(role,phase) in self.roles.items():
                frames=[]
                if role in ('leg','arm','wing','chain'):
                    for t in (0,.25,.5,.75,1):
                        v=math.cos(t*math.tau+phase)* (22 if role=='leg' else 15 if role=='arm' else 24 if role=='wing' else 6)
                        frames.append((t,(v,0,0) if role in ('leg','arm') else (0,0,v*(phase or 1)) if role=='wing' else (0,v,0)))
                    walk[bone]={'rotation':frames}
            self.animation('fly' if flying else 'walk',1,True,walk)
        attack={'root':{'rotation':[(0,(0,0,0)),(.5,(-5,0,0)),(.8,(7,0,0)),(1.3,(0,0,0)),(2,(0,0,0))]}}
        for bone,(role,phase) in self.roles.items():
            if role=='arm':attack[bone]={'rotation':[(0,(0,0,0)),(.5,(-55,0,phase*7)),(.8,(30,0,0)),(1.3,(0,0,0)),(2,(0,0,0))]}
            elif role=='jaw':attack[bone]={'rotation':[(0,(0,0,0)),(.5,(25,0,0)),(.8,(8,0,0)),(1.3,(0,0,0)),(2,(0,0,0))]}
            elif role=='head':attack[bone]={'rotation':[(0,(0,0,0)),(.5,(-12,0,0)),(.8,(15,0,0)),(1.5,(0,0,0)),(2,(0,0,0))]}
            elif role=='sac':attack[bone]={'scale':[(0,(1,1,1)),(.5,(1.12,1.08,1.12)),(.8,(.91,.94,.91)),(1.5,(1,1,1)),(2,(1,1,1))]}
            elif role=='chain':attack[bone]={'rotation':[(0,(0,0,0)),(.5,(-4,phase*3,0)),(.8,(8,-phase*2,0)),(1.5,(0,0,0)),(2,(0,0,0))]}
        self.animation('attack',2,False,attack)
        self.animation('emerge',1.5,False,{'root':{'position':[(0,(0,-8,0)),(1,(0,1,0)),(1.5,(0,0,0))]}})
        self.animation('death',1.5,False,{'root':{'rotation':[(0,(0,0,0)),(1.5,(0,0,8 if stationary else 70))],'position':[(0,(0,0,0)),(1.5,(0,-3,0))]}})
    def save(self,folder):
        folder=Path(folder);folder.mkdir(parents=True,exist_ok=True)
        used={f['_tile'] for e in self.data['elements'] for f in e['faces'].values()};self.atlas.tiles={k:None for k in self.atlas.tiles if k in used}
        image,positions=self.atlas.pack();data=copy.deepcopy(self.data)
        for e in data['elements']:
            for face in e['faces'].values():
                k=face.pop('_tile');x,y=positions[k];_,w,h,_=k;face['uv']=[x,y,x+w,y+h]
        b=io.BytesIO();image.save(b,format='PNG');size=image.size[0]
        data['resolution']={'width':size,'height':size};data['textures']=[{'name':self.key+'.png','uuid':ident(self.key+'/texture'),'id':'0','source':'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode(),'mode':'bitmap','width':size,'height':size,'uv_width':size,'uv_height':size,'render_mode':'default'}]
        data['texture_width']=size;data['texture_height']=size
        if self.category in ('weapons','loot'):
            data['display']={'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,2,0],'scale':[.75,.75,.75]},'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.7,.7,.7]}}
        file=folder/(self.key+'.bbmodel');file.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8');image.save(folder/(self.key+'.png'))
        return data,image,{'id':self.key,'name':self.label,'category':self.category,'role':self.role,'file':str(file),'cubes':len(data['elements']),'bones':len(self.groups),'animations':[a['name'].split('.')[-1] for a in data['animations']],'atlas':list(image.size),'uv_islands':len(positions)}

def prepare_render(data,frame=None):
    model=copy.deepcopy(data);world={};group_for={};tracks={}
    if frame:
        animation,time=frame
        for uid,a in animation.get('animators',{}).items():
            values={}
            for ch in ('position','rotation','scale'):
                keys=sorted([k for k in a['keyframes'] if k['channel']==ch],key=lambda k:k['time'])
                if not keys:continue
                lower=next((k for k in reversed(keys) if k['time']<=time),keys[0]);upper=next((k for k in keys if k['time']>=time),keys[-1]);span=upper['time']-lower['time'];factor=0 if span==0 else (time-lower['time'])/span
                av=np.array([float(lower['data_points'][0][k]) for k in ('x','y','z')]);bv=np.array([float(upper['data_points'][0][k]) for k in ('x','y','z')]);values[ch]=av+(bv-av)*factor
            tracks[uid]=values
    hidden=set()
    def visit(g,parent,invisible=False):
        t=tracks.get(g['uuid'],{});local=transform_at(g['origin'],np.asarray(g.get('rotation',(0,0,0)))+t.get('rotation',0),t.get('position',(0,0,0)),t.get('scale',(1,1,1)));matrix=parent@local;world[g['uuid']]=matrix
        invisible=invisible or not g.get('visibility',True)
        for child in g['children']:
            if isinstance(child,dict):visit(child,matrix,invisible)
            else:
                group_for[child]=matrix
                if invisible:hidden.add(child)
    for g in model['outliner']:visit(g,np.eye(4))
    model['elements']=[e for e in model['elements'] if e['uuid'] not in hidden and e.get('visibility',True)]
    for e in model['elements']:
        e['_pose']=(group_for[e['uuid']]@transform_at(e['origin'],e['rotation'])).tolist();e['rotation']=[0,0,0]
    return model
