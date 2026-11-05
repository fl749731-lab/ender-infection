"""Detailed assembled machinery and directional transport. Native Java block geometry.

Large machines are split into <=48px sections to obey Java element bounds.
These private render models have no item, recipe, tick or independent inventory.
"""
TEX={'steel':'minecraft:block/iron_block','dark':'minecraft:block/polished_deepslate',
     'black':'minecraft:block/black_concrete','copper':'minecraft:block/copper_block',
     'brass':'minecraft:block/gold_block','glass':'minecraft:block/glass',
     'violet':'minecraft:block/amethyst_block','light':'minecraft:block/sea_lantern',
     'water':'minecraft:block/blue_stained_glass','slurry':'minecraft:block/purple_stained_glass'}

class Model:
 def __init__(self):self.parts=[]
 def box(self,a,b,t='steel',axis=None,angle=0,origin=None):
  e={'from':list(a),'to':list(b),'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}}
  if axis:e['rotation']={'origin':list(origin or [(x+y)/2 for x,y in zip(a,b)]),'axis':axis,'angle':angle,'rescale':False}
  self.parts.append(e);return self
 def bolts(self,xs,ys,z):
  for x in xs:
   for y in ys:self.box((x-.6,y-.6,z-.35),(x+.6,y+.6,z+.35),'brass')
 def ring_y(self,y,r=18,thick=2):
  # Eight flat facets, supported by narrow external bands.
  for z in [-r,r-thick]:self.box((8-r*.42,y,8+z),(8+r*.42,y+2,8+z+thick),'dark')
  for x in [-r,r-thick]:self.box((8+x,y,8-r*.42),(8+x+thick,y+2,8+r*.42),'dark')
  for x in [-1,1]:
   for z in [-1,1]:
    cx,cz=8+x*r*.705,8+z*r*.705
    self.box((cx-r*.41,y,cz-thick/2),(cx+r*.41,y+2,cz+thick/2),'copper','y',45 if x*z<0 else -45)
 def ring_z(self,z,r=18):
  for y in [-r,r-2]:self.box((8-r*.42,7+y,z),(8+r*.42,9+y,z+2),'dark')
  for x in [-r,r-2]:self.box((8+x,7-r*.42,z),(10+x,7+r*.42,z+2),'dark')
  for x in [-1,1]:
   for y in [-1,1]:
    cx,cy=8+x*r*.705,7+y*r*.705
    self.box((cx-r*.41,cy-1,z),(cx+r*.41,cy+1,z+2),'copper','z',45 if x*y>0 else -45)
 def frame(self,z0=-16,z1=32):
  for x in [-15,25]:self.box((x,-15,z0),(x+6,-10,z1),'dark')
  for z in [z0+1,z1-5]:self.box((-15,-13,z),(31,-8,z+4),'steel')
  for x in [-14,26]:
   for z in [z0+2,z1-4]:self.box((x,-16,z),(x+4,-13,z+3),'black')
 def data(self):
  for e in self.parts:
   assert all(-16<=v<=32 for key in ['from','to'] for v in e[key]),e
   assert all(a<b for a,b in zip(e['from'],e['to'])),e
  return {'parent':'minecraft:block/block','textures':dict(TEX,particle=TEX['steel']),
          'render_type':'minecraft:cutout','elements':self.parts}

def models():
 out={}
 def done(name,m):out[name+'_visual']=m.data()
 # Fuel engine: separate crankcase, cylinder heads, radiator and exhaust.
 m=Model();m.frame();m.box((-8,-8,-8),(21,11,25),'dark').box((-6,-6,-6),(19,9,23),'steel')
 for z in [-5,3,11,19]:
  m.box((-6,9,z),(20,13,z+5),'dark').box((-4,13,z+.5),(18,19,z+4.5),'steel')
  m.box((-1,19,z+1),(15,21,z+4),'copper')
 for x in [-12,24]:
  m.box((x,-6,-9),(x+3,17,22),'copper')
  for z in [-7,1,9,17]:m.box((x-1,3,z),(x+4,5,z+2),'brass')
 m.box((-11,-7,24),(28,25,29),'dark')
 for x in range(-9,27,3):m.box((x,-5,23),(x+1.2,23,30),'steel')
 m.box((20,17,13),(26,29,19),'dark').box((19,27,12),(27,31,20),'copper')
 m.box((-6,-9,-13),(22,-5,-10),'steel');m.bolts([-6,22],[-7,8],-9)
 m.box((-2,22,-1),(16,25,4),'dark').box((0,23,-1.2),(14,24,-1),'violet')
 done('generator',m)
 # Processor: glass octagonal vessel, steel ribs, feet, side feed risers.
 m=Model();m.frame();m.box((-10,-10,-10),(26,-6,26),'steel')
 for y in [-7,-3,27,30]:m.ring_y(y)
 for x in [-1,1]:
  m.box((8+x*17-1,-1,1),(8+x*17+1,28,15),'glass')
 for z in [-1,1]:m.box((1,-1,8+z*17-1),(15,28,8+z*17+1),'glass')
 for x in [-1,1]:
  for z in [-1,1]:
   cx,cz=8+x*12,8+z*12
   m.box((cx-6.3,-1,cz-.5),(cx+6.3,28,cz+.5),'glass','y',45 if x*z<0 else -45)
   m.box((cx-1,-7,cz-1),(cx+1,32,cz+1),'steel')
 for x in [-14,28]:m.box((x,-7,6),(x+2,27,10),'copper')
 for y in [0,23]:m.box((-14,y,6),(30,y+2,10),'copper')
 m.box((-1,19,-11),(17,27,-9),'dark').box((1,21,-11.3),(15,25,-11),'steel')
 for x in [3,6,9,12]:m.box((x,22,-11.6),(x+.5,24,-11.3),'black')
 done('processor_lower',m)
 m=Model();m.ring_y(0);m.box((-6,0,-6),(22,3,22),'steel')
 m.box((0,3,0),(16,11,16),'dark').box((2,4,-1),(14,10,0),'copper')
 for z in [2,5,8,11]:m.box((-1,4,z),(17,10,z+1),'steel')
 m.box((5,11,5),(11,16,11),'copper').box((-10,1,3),(-6,8,13),'brass')
 done('processor_upper',m)
 # Centrifuge: longitudinal drum, removable cover, bearing cradles, rear motor.
 for back in [False,True]:
  m=Model();end=16 if back else 32;m.frame(-16,end)
  for z in [-12,12] if back else [-12,12,29]:m.ring_z(z)
  for y in [-10,23]:m.box((0,y,-16),(16,y+2,end),'steel')
  for x in [-9,24]:m.box((x,0,-16),(x+2,14,end),'steel')
  for x in [-1,1]:
   for y in [-1,1]:
    cx,cy=8+x*11.6,7+y*11.6
    m.box((cx-6.5,cy-.6,-16),(cx+6.5,cy+.6,end),'glass' if y>0 else 'steel','z',45 if x*y>0 else -45)
  for x in [-15,27]:m.box((x,-9,-6),(x+4,13,3),'dark')
  if back:
   m.box((-3,-3,14),(19,18,16),'dark')
   for x in [-1,4,9,14]:m.box((x,-1,14),(x+2,16,16),'copper')
  else:
   m.box((-5,-6,-16),(21,20,-13),'steel');m.bolts([-3,19],[-4,18],-16+.5)
  done('centrifuge_back' if back else 'centrifuge_front',m)
 # Dynamic rotor hubs. A single transform moves each whole rotating assembly.
 for kind in ['generator','centrifuge','processor']:
  m=Model()
  if kind=='processor':
   m.box((7,-2,7),(9,27,9),'copper')
   for y in [1,13]:m.box((-4,y,6),(20,y+2,10),'steel').box((6,y,-4),(10,y+2,20),'copper')
  else:
   m.box((5,5,5),(11,11,11),'brass')
   for angle in [-45,45]:m.box((-2,6,7),(18,10,9),'copper','z',angle,(8,8,8))
   m.box((6,-2,7),(10,18,9),'steel').box((-2,6,7),(18,10,9),'steel')
  done(kind+'_rotor',m)
 for name,t in [('processor_water','water'),('processor_slurry','slurry')]:
  m=Model();m.box((-3,0,-3),(19,27,19),t);done(name,m)
 m=Model();m.box((4,8,-.2),(12,9,0),'violet').box((4,11,-.2),(12,12,0),'light');done('energy_glow',m)
 # Transport is thin, insulated and rendered only toward connected neighbors.
 for kind,lo,hi in [('wire',6.5,9.5),('pipe',5.5,10.5)]:
  m=Model();m.box((lo,lo,lo),(hi,hi,hi),'black' if kind=='wire' else 'steel');done(kind+'_core',m)
  m=Model();m.box((lo,lo,0),(hi,hi,8),'black' if kind=='wire' else 'steel')
  m.box((lo-.5,lo-.5,.7),(hi+.5,hi+.5,2),'copper')
  if kind=='wire':m.box((7.4,9.5,2.1),(8.6,9.7,7.5),'copper')
  else:m.box((7.5,10.5,2.1),(8.5,10.7,7),'water')
  done(kind+'_arm',m)
  m=Model();m.box((lo-1,lo-1,0),(hi+1,hi+1,2.5),'dark').box((lo-.4,lo-.4,-.15),(hi+.4,hi+.4,.2),'copper');done(kind+'_plug',m)
 for role,tex in [('energy','brass'),('item_input','steel'),('item_output','copper'),('fluid_input','water'),('fluid_output','violet'),('controller','steel')]:
  m=Model();m.box((1.5,1.5,.1),(14.5,14.5,2),'dark').box((3,3,-.05),(13,13,.1),tex)
  if role=='controller':
   m.box((3.5,6,-.3),(12.5,13,-.1),'black').box((4,3,-.3),(6,5,-.1),'copper').box((9,3,-.3),(12,5,-.1),'brass')
  elif role.startswith('item'):
   m.box((4,5,-.3),(12,11,-.1),'black').box((5,4,-.4),(11,5,-.1),'brass')
  else:m.box((5.5,5.5,-.3),(10.5,10.5,.1),'black')
  m.bolts([2.5,13.5],[2.5,13.5],-.1);done('hatch_'+role,m)
 return out
