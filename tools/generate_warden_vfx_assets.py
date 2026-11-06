"""Build original pixel VFX masks. Forge is canonical; Neo uses normal resource sync."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'ender_infection_colored/src/main/resources/assets/ender_infection'
NAMES=['warden_slash','warden_shard','warden_segment','warden_mote','warden_fan','warden_discharge','warden_wave']

def generate():
    target=ASSETS/'textures/particle';target.mkdir(parents=True,exist_ok=True)
    for index,name in enumerate(NAMES):
        size=64 if index==6 else 32 if index in (0,4) else 8 if index==3 else 16
        im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im)
        if index==0:
            d.polygon([(1,27),(4,17),(11,8),(21,3),(30,2),(23,6),(17,11),(12,17),(9,23),(7,29)],fill=(255,255,255,80))
            d.polygon([(3,27),(7,17),(13,10),(22,5),(29,3),(20,9),(14,16),(10,24),(7,28)],fill=(255,255,255,210))
            d.line([(6,25),(9,18),(15,11),(24,6)],fill=(255,255,255,255),width=2)
        elif index==1:
            d.polygon([(7,0),(12,4),(11,12),(6,15),(3,10),(4,4)],fill=(255,255,255,110))
            d.polygon([(7,1),(9,5),(8,12),(6,14),(5,9)],fill=(255,255,255,255))
            d.polygon([(9,5),(11,4),(10,11),(8,12)],fill=(255,255,255,180))
        elif index==2:
            d.rectangle((0,3,15,12),fill=(255,255,255,70));d.rectangle((0,5,15,10),fill=(255,255,255,220));d.rectangle((0,7,15,8),fill=(255,255,255,255))
        elif index==3:
            d.rectangle((1,1,6,6),fill=(255,255,255,60));d.rectangle((2,2,5,5),fill=(255,255,255,255))
        elif index==4:
            # A broad radial gradient, so adjacent fan cells join into a wave rather than bars.
            for y in range(size):
                envelope=math.sin(math.pi*(y+.5)/size)**1.3
                for x in range(size):
                    facet=.82+.18*math.cos((x//8)*1.9+(y//6)*.7)
                    im.putpixel((x,y),(255,255,255,round(215*envelope*facet)))
        elif index==5:
            pts=[(1,14),(5,10),(3,8),(10,5),(8,3),(14,1)]
            d.line(pts,fill=(255,255,255,75),width=4);d.line(pts,fill=(255,255,255,255),width=1)
        else:
            # Original asymmetric faceted wave mask. No upstream pixels or tracing are used.
            for y in range(size):
                for x in range(size):
                    dx=(x+0.5-32)/32;dy=(y+0.5-32)/32;r=math.hypot(dx,dy);a=math.atan2(dy,dx)
                    outer=.87+.013*math.sin(11*a)+.009*math.cos(7*a)
                    rim=math.exp(-((r-outer)/.019)**2)
                    tail=math.exp(-((r-(outer-.055))/.045)**2)*.30
                    interrupted=math.exp(-((r-.69-.018*math.sin(5*a))/.016)**2)*max(0,math.sin(3*a+.7))*.72
                    gap=1 if math.sin(13*a+.8)>-.84 else .18
                    alpha=round(255*min(1,(rim+tail+interrupted)*gap))
                    im.putpixel((x,y),(255,255,255,alpha))
        im.save(target/f'{name}.png',optimize=True)
    path=ASSETS/'particles/warden_vfx.json';path.parent.mkdir(parents=True,exist_ok=True)
    # Minecraft's particles atlas already uses textures/particle as its directory source.
    path.write_text(json.dumps({'textures':['ender_infection:'+name for name in NAMES]},indent=2)+'\n','utf-8')

if __name__=='__main__':generate()
