"""CPU previews of actual bbmodel geometry and UVs, with translucent glass."""
import numpy as np
from PIL import Image
from modelkit import rotation_matrix

def render(model,texture,width=380,height=365,camera=(1,.8,-1),margin=24,fixed=None):
    tex=np.asarray(texture.convert('RGBA'));color=np.zeros((height,width,4),dtype=np.uint8);depth=np.full((height,width),-np.inf)
    forward=np.asarray(camera,dtype=float);forward/=np.linalg.norm(forward)
    right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward);up/=np.linalg.norm(up)
    vertices=[];surfaces=[]
    light=np.array([.2,1,-.4]);light/=np.linalg.norm(light)
    for e in model['elements']:
        if not e.get('visibility',True):continue
        x,y,z=e['from'];X,Y,Z=e['to'];pose=np.asarray(e.get('_pose',np.eye(4)))
        r=rotation_matrix(e.get('rotation',[0,0,0]));pivot=np.asarray(e.get('origin',[0,0,0]))
        faces={'north':([(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],[0,0,-1]),'south':([(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],[0,0,1]),'east':([(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],[1,0,0]),'west':([(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],[-1,0,0]),'up':([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],[0,1,0]),'down':([(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],[0,-1,0])}
        for name,(points,normal) in faces.items():
            normal=pose[:3,:3]@r@normal
            if np.dot(normal,forward)<=0:continue
            xyz=(np.array(points)-pivot)@r.T+pivot;xyz=xyz@pose[:3,:3].T+pose[:3,3];vertices.extend(xyz)
            u,v,U,V=e['faces'][name]['uv'];uv=np.array([[u,v],[U,v],[U,V],[u,V]])
            surfaces.append((xyz,uv,.76+.24*max(0,np.dot(normal,light))))
    verts=np.asarray(vertices)
    if not len(verts):return Image.new('RGBA',(width,height))
    projected=np.column_stack((verts@right,-verts@up));lo=projected.min(axis=0);hi=projected.max(axis=0)
    scale=min((width-2*margin)/max(1,hi[0]-lo[0]),(height-2*margin)/max(1,hi[1]-lo[1]));offset=np.array([width/2,height/2])-(lo+hi)*scale/2
    if fixed is not None:scale,offset=fixed
    triangles=[]
    for xyz,uv,shade in surfaces:
        pp=np.column_stack((xyz@right,-xyz@up))*scale+offset;dd=xyz@forward
        for ids in ((0,1,2),(0,2,3)):
            ids=list(ids);p=pp[ids];dv=dd[ids];t=uv[ids]
            x0=max(0,int(p[:,0].min()));x1=min(width,int(p[:,0].max())+2);y0=max(0,int(p[:,1].min()));y1=min(height,int(p[:,1].max())+2)
            if x0>=x1 or y0>=y1:continue
            den=(p[1,1]-p[2,1])*(p[0,0]-p[2,0])+(p[2,0]-p[1,0])*(p[0,1]-p[2,1])
            if abs(den)<1e-8:continue
            yy,xx=np.mgrid[y0:y1,x0:x1];xx=xx+.5;yy=yy+.5
            aa=((p[1,1]-p[2,1])*(xx-p[2,0])+(p[2,0]-p[1,0])*(yy-p[2,1]))/den
            bb=((p[2,1]-p[0,1])*(xx-p[2,0])+(p[0,0]-p[2,0])*(yy-p[2,1]))/den;cc=1-aa-bb;zz=aa*dv[0]+bb*dv[1]+cc*dv[2]
            tu=np.clip(np.floor(aa*t[0,0]+bb*t[1,0]+cc*t[2,0]).astype(int),0,tex.shape[1]-1)
            tv=np.clip(np.floor(aa*t[0,1]+bb*t[1,1]+cc*t[2,1]).astype(int),0,tex.shape[0]-1)
            pixels=tex[tv,tu].copy();pixels[:,:,:3]=(pixels[:,:,:3]*shade).astype(np.uint8)
            inside=(aa>=-1e-6)&(bb>=-1e-6)&(cc>=-1e-6)
            triangles.append((float(dv.mean()),x0,x1,y0,y1,zz,pixels,inside))
    # Opaque objects establish depth; glass is blended over visible surfaces from back to front.
    for _,x0,x1,y0,y1,zz,pixels,inside in triangles:
        dep=depth[y0:y1,x0:x1];mask=inside&(zz>dep)&(pixels[:,:,3]>=250)
        color[y0:y1,x0:x1][mask]=pixels[mask];dep[mask]=zz[mask]
    for _,x0,x1,y0,y1,zz,pixels,inside in sorted(triangles,key=lambda t:t[0]):
        mask=inside&(zz>depth[y0:y1,x0:x1])&(pixels[:,:,3]>0)&(pixels[:,:,3]<250)
        if not mask.any():continue
        layer=color[y0:y1,x0:x1];src=pixels[mask].astype(float)/255;dst=layer[mask].astype(float)/255
        alpha=src[:,3:4]+dst[:,3:4]*(1-src[:,3:4]);out=np.zeros_like(src);out[:,:3]=(src[:,:3]*src[:,3:4]+dst[:,:3]*dst[:,3:4]*(1-src[:,3:4]))/np.maximum(alpha,1e-8);out[:,3:4]=alpha
        layer[mask]=np.clip(out*255,0,255).astype(np.uint8)
    return Image.fromarray(color)
