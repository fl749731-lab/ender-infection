"""Editable cuboid detail and denser pixel textures for the concept creatures."""
import math
import random

from PIL import Image, ImageDraw

from modelkit import Atlas, Model, PALETTES, rgb, stable


class CreatureAtlas(Atlas):
    density = 3
    cluster = 4

    def get(self, material, w, h, variant=0):
        return super().get(material, math.ceil(w) * self.density,
                           math.ceil(h) * self.density, variant)

    def paint(self, key):
        material, w, h, variant = key
        if material not in ('bone', 'skull', 'shell', 'tissue', 'membrane', 'spore', 'brood'):
            return super().paint(key)
        colors = [(*rgb(c), 255) for c in PALETTES[material]]
        rng = random.Random(stable(str(key)))
        image = Image.new('RGBA', (w, h), colors[1])
        draw = ImageDraw.Draw(image)
        # Broken, connected pixel clusters follow the material; no per-pixel noise.
        step = self.cluster
        for row in range(0, h, step):
            for column in range(0, w, step):
                weights = (5, 30, 54, 11) if material in ('spore', 'brood') else (5, 62, 29, 4)
                choice = rng.choices((0, 1, 2, 3), weights)[0]
                draw.rectangle((column, row, min(w-1,column+rng.randint(step-1,step+2)),
                                min(h-1,row+rng.randint(step-2,step))), fill=colors[choice])
        if material in ('bone', 'skull', 'shell'):
            if w >= 6 and h >= 6:
                draw.line((1, 0, min(w-2,step+1), 0), fill=colors[3])
                for _ in range(w*h//450):
                    x, y = rng.randrange(1, w-1), rng.randrange(1, h-1)
                    draw.line(((x,y),(x,min(h-1,y+2)),(min(w-1,x+1),min(h-1,y+3))),fill=colors[0])
                    if x+1 < w: draw.point((x+1,y),fill=colors[3])
        elif material not in ('spore', 'brood'):
            for _ in range(w*h//240):
                x,y=rng.randrange(w),rng.randrange(h)
                points=[(x,y),(min(w-1,x+1),min(h-1,y+2)),(x,min(h-1,y+4))]
                draw.line(points,fill=colors[0])
        return image


class ConceptModel(Model):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.atlas = CreatureAtlas()
        if self.key in ('walking_brood_crab', 'abyssal_burrow_king'):
            self.atlas.cluster = 8
        self.data['meta']['texture_density'] = self.atlas.density
        self.data['notes'] = 'Concept creature. Editable cuboids, 3 texture pixels per model unit; gameplay size is normalized on export.'

    def plate(self, name, c, size, bone='body', rot=(0,0,0), mat='bone'):
        x,y,z=c;w,h,d=size
        self.centered(name+'_dark_joint',c,(w*.91,h*.94,d),'shell',bone,rot=rot,pivot=c)
        # Different widths at each end break the rectangular board silhouette.
        for i,(a,b,spread) in enumerate(((-.49,-.28,.73),(-.28,.25,1),(.25,.48,.83))):
            self.centered(name+'_scalloped_armor',
                          (x+(i-1)*w*.035,y+(a+b)*h/2,z-d*.18),
                          (w*spread,(b-a)*h,d*.86),mat,bone,rot=rot,pivot=c,variant=i)
        self.centered(name+'_raised_keel',(x+w*.04,y+h*.03,z-d*.64),
                      (max(.3,w*.12),h*.57,max(.16,d*.11)),mat,bone,rot=rot,pivot=c)

    def sac(self, name, c, size, bone='root', mat='membrane', ribs=True):
        x,y,z=c;w,h,d=size
        steps=11 if max(size)>=10 else 9
        for iy in range(steps):
            ny=((iy+.5)/steps-.5)*2
            # Hanging organs taper at the tether and swell below their midpoint.
            pear=1-.16*ny
            for iz in range(steps):
                nz=((iz+.5)/steps-.5)*2
                remaining=1-ny*ny-nz*nz
                if remaining <= .025: continue
                self.centered(name+'_curved_lobe',(x,y+ny*h/2,z+nz*d/2),
                              (w*math.sqrt(remaining)*pear,h/steps,d/steps),mat,bone,
                              variant=(iy*3+iz)%3)
        if ribs:
            for side in (-1,1):
                for branch in (-1,1):
                    points=[]
                    for i in range(7):
                        ny=-.72+i*.24
                        breadth=math.sqrt(max(.05,1-ny*ny))
                        points.append((x+branch*w*.25*breadth,y+ny*h*.5,z+side*d*.43*breadth))
                    for a,b in zip(points,points[1:]):
                        self.beam(name+'_tendon',a,b,max(.22,min(w,d)*.035),mat='tissue',bone=bone)
