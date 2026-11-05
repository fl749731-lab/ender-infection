"""Prepare numeric rigid-bone animation operations for Blockbench MCP."""
from pathlib import Path
import json,math
OUT=Path(__file__).resolve().parents[2]/'build/institute-boss-alpha7'
ops=[]
def clip(name,length,loop=False):ops.append({'op':'add_animation','name':'animation.containment_warden.'+name,'length':length,'loop':'loop' if loop else 'once','snapping':20})
def keys(name,bone,channel,frames):
 for time,value in frames:ops.append({'op':'set_keyframe','animation':'animation.containment_warden.'+name,'bone':bone,'channel':channel,'time':time,'value':value,'interpolation':'linear'})
def rot(name,bone,frames):keys(name,bone,'rotation',frames)
clip('idle',3,True)
rot('idle','head',[(0,[0,-2,0]),(1.5,[0,2,0]),(3,[0,-2,0])])
for side,s in [('left',1),('right',-1)]:
 rot('idle',side+'_chest',[(0,[0,s*1,0]),(1.5,[0,s*-1,0]),(3,[0,s*1,0])])
 rot('idle',side+'_arm',[(0,[0,0,s*.6]),(1.5,[0,0,s*-.6]),(3,[0,0,s*.6])])
clip('walk',1.2,True)
for side,s in [('left',1),('right',-1)]:
 rot('walk',side+'_leg',[(0,[0,0,0]),(.3,[s*12,0,0]),(.6,[0,0,0]),(.9,[-s*12,0,0]),(1.2,[0,0,0])])
 keys('walk',side+'_leg','position',[(0,[0,0,0]),(.3,[0,1.4,0]),(.6,[0,0,0]),(.9,[0,1.4,0]),(1.2,[0,0,0])])
 rot('walk',side+'_arm',[(0,[0,0,0]),(.3,[-s*9,0,0]),(.6,[0,0,0]),(.9,[s*9,0,0]),(1.2,[0,0,0])])
clip('claw',1.8)
rot('claw','right_arm',[(0,[0,0,0]),(.45,[-35,-10,5]),(.85,[92,5,-5]),(1.2,[50,10,-4]),(1.8,[0,0,0])])
rot('claw','right_forearm',[(0,[0,0,0]),(.45,[50,0,0]),(.85,[0,0,0]),(1.2,[-8,0,0]),(1.8,[0,0,0])])
rot('claw','head',[(0,[0,0,0]),(.45,[-5,8,0]),(.85,[5,-5,0]),(1.8,[0,0,0])])
for i in range(3):rot('claw','right_claw_'+str(i),[(0,[0,0,0]),(.5,[10,(i-1)*12,0]),(.85,[-12,(i-1)*-5,0]),(1.8,[0,0,0])])
clip('stomp',2.4)
keys('stomp','right_leg','position',[(0,[0,0,0]),(.55,[0,5.5,0]),(.95,[0,5.5,0]),(1.1,[0,0,0]),(2.4,[0,0,0])])
rot('stomp','right_leg',[(0,[0,0,0]),(.55,[18,0,0]),(.95,[18,0,0]),(1.1,[0,0,0]),(2.4,[0,0,0])])
rot('stomp','left_arm',[(0,[0,0,0]),(.7,[68,0,-8]),(1.1,[36,0,-12]),(1.65,[20,0,0]),(2.4,[0,0,0])])
rot('stomp','right_arm',[(0,[0,0,0]),(.7,[36,0,6]),(1.1,[22,0,8]),(2.4,[0,0,0])])
rot('stomp','head',[(0,[0,0,0]),(.7,[-8,0,0]),(1.1,[8,0,0]),(2.4,[0,0,0])])
clip('burst',2.6)
for side,s in [('left',1),('right',-1)]:rot('burst',side+'_chest',[(0,[0,0,0]),(.65,[0,s*28,0]),(1.8,[0,s*28,0]),(2.6,[0,0,0])])
keys('burst','core','scale',[(0,[1,1,1]),(.7,[1.06,1.06,1.06]),(1,[1.18,1.08,1.18]),(1.1,[1.06,1.06,1.06]),(1.3,[1.18,1.08,1.18]),(1.4,[1.06,1.06,1.06]),(1.6,[1.18,1.08,1.18]),(1.8,[1.06,1.06,1.06]),(2.6,[1,1,1])])
rot('burst','head',[(0,[0,0,0]),(.7,[-6,0,0]),(1.6,[-2,0,0]),(2.6,[0,0,0])])
clip('exposed',3,True)
for side,s in [('left',1),('right',-1)]:rot('exposed',side+'_chest',[(0,[0,s*30,0]),(1.5,[0,s*32,0]),(3,[0,s*30,0])])
keys('exposed','core','scale',[(0,[1,1,1]),(1.5,[1.12,1.04,1.12]),(3,[1,1,1])])
rot('exposed','head',[(0,[8,0,0]),(1.5,[11,0,0]),(3,[8,0,0])])
clip('death',3)
keys('death','body','position',[(0,[0,0,0]),(.5,[0,-.4,0]),(1,[0,-2.5,0]),(2.2,[0,-10.9,0]),(3,[0,-10.9,0])])
for side in ['left','right']:
 rot('death',side+'_leg',[(0,[0,0,0]),(1,[-35,0,0]),(2.2,[-80,0,0]),(3,[-80,0,0])])
 rot('death',side+'_shin',[(0,[0,0,0]),(1,[55,0,0]),(2.2,[120,0,0]),(3,[120,0,0])])
 rot('death',side+'_foot',[(0,[0,0,0]),(1,[-20,0,0]),(2.2,[-40,0,0]),(3,[-40,0,0])])
 rot('death',side+'_arm',[(0,[0,0,0]),(1,[20,0,0]),(2.2,[35,0,0]),(3,[35,0,0])])
rot('death','head',[(0,[0,0,0]),(1,[10,0,0]),(2.2,[22,0,0]),(3,[22,0,0])])
for n in range(0,len(ops),30):(OUT/f'animation-{n//30:02}.json').write_text(json.dumps(ops[n:n+30]),'utf-8')
(OUT/'animation-plan.json').write_text(json.dumps({'clips':7,'operations':len(ops),'batches':math.ceil(len(ops)/30)},indent=2),'utf-8')
print('WARDEN_ANIMATIONS_READY',len(ops),'operations',math.ceil(len(ops)/30),'batches')
