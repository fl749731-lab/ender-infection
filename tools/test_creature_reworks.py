"""Regression contracts for source fidelity, glow isolation and native clip timing."""
import json,shutil,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
import export_creature_reworks as rework

class CreatureReworkTests(unittest.TestCase):
 def test_polished_material_is_bound_and_critical_face_pixels_stay_exact(self):
  import io,base64
  before=Image.open(rework.PACK/'materials/quadruped-atlas-v1.png').convert('RGBA');after=Image.open(rework.PACK/'materials/quadruped-atlas-v2.png').convert('RGBA')
  self.assertNotEqual(before.crop((0,0,128,32)).tobytes(),after.crop((0,0,128,32)).tobytes())
  for region in [(64,32,128,64),(0,64,128,128)]:self.assertEqual(before.crop(region).tobytes(),after.crop(region).tobytes())
  for kind in ['cow','pig','sheep','cow_early','pig_early','sheep_early']:
   d=rework.read(rework.PACK/'models'/('infected_'+kind+'.bbmodel'));embedded=Image.open(io.BytesIO(base64.b64decode(d['textures'][1]['source'].split(',',1)[1]))).convert('RGBA');self.assertEqual(embedded.tobytes(),after.tobytes())

 def test_early_stage_has_anatomy_without_developed_mutation_and_exact_attack(self):
  for kind in ['cow','pig','sheep','spider']:
   runtime='infected_'+kind+'_early';files,row=rework.build(runtime);model=rework.read(rework.PACK/'models'/(runtime+'.bbmodel'));names={e['name'] for e in model['elements']};self.assertEqual(sum(n.startswith('crystal_early_') for n in names),4)
   self.assertFalse(any(n.startswith('exposed_rib') or n in ['ruptured_shoulder','parasitic_flank','central_throat'] for n in names))
   clips=json.loads(files['animations/'+runtime+'.animation.json'])['animations'];special=clips['animation.'+runtime+'.special'];self.assertEqual(special['animation_length'],rework.SPECS[runtime]['duration_ticks']/20)
   for a in model['animations']:
    if a['name'] not in ['idle','walk']:continue
    for tr in a['animators'].values():
     for channel in {k['channel'] for k in tr['keyframes']}:
      keys=sorted([k for k in tr['keyframes'] if k['channel']==channel],key=lambda k:k['time']);v=lambda k:[float(k['data_points'][0][axis]) for axis in ['x','y','z']]
      self.assertEqual(v(keys[0]),v(keys[-1]),(kind,a['name'],channel))

 def test_original_body_pixels_and_crystal_only_emission(self):
  files,row=rework.build('infected_spider');import io,base64
  d=rework.read(rework.PACK/'models/infected_spider.bbmodel');body=Image.open(io.BytesIO(base64.b64decode(d['textures'][0]['source'].split(',',1)[1]))).convert('RGBA')
  atlas=Image.open(io.BytesIO(files['textures/entity/infected_spider.png'])).convert('RGBA');mask=Image.open(io.BytesIO(files['textures/entity/infected_spider_glowmask.png'])).convert('RGBA')
  self.assertEqual(atlas.crop((0,0,128,128)).tobytes(),body.tobytes());self.assertEqual(mask.crop((0,0,128,128)).getchannel('A').getbbox(),None)
  self.assertEqual(row['emissive_pixels'],50);self.assertEqual(row['crystal_faces'],72)
  for y in range(mask.height):
   for x in range(mask.width):
    if mask.getpixel((x,y))[3]:self.assertGreaterEqual(x,128);self.assertEqual(mask.getpixel((x,y)),atlas.getpixel((x,y)))
 def test_native_impact_endpoints_and_rig_are_preserved(self):
  files,row=rework.build('infected_spider');geometry=json.loads(files['geo/infected_spider.geo.json'])['minecraft:geometry'][0];clips=json.loads(files['animations/infected_spider.animation.json'])['animations'];prefix='animation.infected_spider.'
  self.assertEqual((row['cubes'],row['bones']),(69,61));self.assertEqual(set(clips),{prefix+x for x in ['idle','walk','special','infection','death']})
  self.assertIn('0.7',clips[prefix+'special']['bones']['rig_root']['rotation']);self.assertEqual(clips[prefix+'special']['animation_length'],1.5);self.assertEqual(clips[prefix+'death']['animation_length'],.9)
  for name in ['idle','walk']:
   clip=clips[prefix+name]
   for bone in clip['bones'].values():
    for track in bone.values():
     keys=sorted(track,key=float);self.assertAlmostEqual(float(keys[0]),0);self.assertAlmostEqual(float(keys[-1]),clip['animation_length']);self.assertEqual(track[keys[0]],track[keys[-1]])
  names={b['name'] for b in geometry['bones']};self.assertTrue({'rig_root','head','abdomen','segmento1pierna1','segmento1pierna8'}<=names)
 def test_changed_source_cannot_use_old_mcp_export(self):
  original=rework.sha
  def mismatch(path):return 'changed' if path.name=='infected_spider.bbmodel' else original(path)
  with patch.object(rework,'sha',mismatch):
   with self.assertRaisesRegex(ValueError,'Stale MCP export'):rework.build('infected_spider')

 def test_quadruped_anatomy_joint_binding_and_native_timing(self):
  counts={'cow':(53,22),'pig':(49,19),'sheep':(57,22)}
  for kind,(cubes,bones) in counts.items():
   runtime='infected_'+kind;files,row=rework.build(runtime);spec=rework.SPECS[runtime]
   geo=json.loads(files['geo/'+runtime+'.geo.json'])['minecraft:geometry'][0];names={b['name'] for b in geo['bones']};clips=json.loads(files['animations/'+runtime+'.animation.json'])['animations'];prefix='animation.'+runtime+'.'
   self.assertEqual((row['cubes'],row['bones']),(cubes,bones));self.assertTrue({'ear_left','ear_right','tail_base'}<=names)
   for i in range(4):self.assertIn('leg_'+str(i)+'_lower_rig',names)
   self.assertEqual(clips[prefix+'special']['animation_length'],spec['duration_ticks']/20);self.assertEqual(clips[prefix+'death']['animation_length'],.9)
   self.assertTrue(any(float(t)==spec['hit_tick']/20 for t in clips[prefix+'special']['bones']['head']['rotation']))
   # An opening lower jaw moves away from the head, instead of rotating into it.
   jaw=clips[prefix+'special']['bones']['infected_jaw']['rotation'];self.assertTrue(any(float(v[0])>0 for v in jaw.values()))
   import io
   mask=Image.open(io.BytesIO(files['textures/entity/'+runtime+'_glowmask.png'])).convert('RGBA')
   self.assertIsNone(mask.crop((0,0,128,128)).getchannel('A').getbbox());self.assertEqual(row['emissive_pixels'],50)
   for clip_name in ['idle','walk']:
    clip=clips[prefix+clip_name]
    for channels in clip['bones'].values():
     for track in channels.values():
      keys=sorted(track,key=float);self.assertEqual(track[keys[0]],track[keys[-1]],(kind,clip_name))

 def test_legacy_silhouette_rebuild_preserves_replacements_and_unrelated_bounds(self):
  import rebuild_creature_silhouettes as silhouettes
  import export_design_assets as legacy
  import export_public_model_adaptations as public
  from contextlib import redirect_stdout
  import io
  with tempfile.TemporaryDirectory(dir=rework.ROOT/'build',prefix='silhouette-rebuild-') as tmp:
   project=Path(tmp);pack=project/'pack';assets=project/'assets';verification=project/'verification';verification.mkdir()
   # Only immutable inputs needed by this rebuild are copied into the scratch target.
   shutil.copytree(silhouettes.PACK/'monsters',pack/'monsters');shutil.copy2(silhouettes.PACK/'manifest.json',pack/'manifest.json')
   (assets/'third_party').mkdir(parents=True)
   shutil.copy2(rework.ASSETS/'concept_visual_bounds.json',assets/'concept_visual_bounds.json')
   shutil.copy2(rework.ASSETS/'third_party/model_credits.json',assets/'third_party/model_credits.json')
   shutil.copy2(rework.ROOT/'ender_infection_colored/verification/design_asset_manifest.json',verification/'design_asset_manifest.json')
   before=rework.read(assets/'concept_visual_bounds.json')
   with patch.object(silhouettes,'PACK',pack),patch.object(silhouettes,'PROJECT',project),patch.object(silhouettes,'ASSETS',assets),patch.object(legacy,'ASSETS',assets),patch.object(rework,'ASSETS',assets),patch.object(sys,'argv',['rebuild_creature_silhouettes.py']),redirect_stdout(io.StringIO()):
    silhouettes.main()
   for name in public.SPECS:
    files,_=public.build(name)
    for relative,content in files.items():self.assertEqual((assets/relative).read_bytes(),content)
   for runtime in rework.SPECS:
    files,_=rework.build(runtime)
    for relative,content in files.items():self.assertEqual((assets/relative).read_bytes(),content)
   after=rework.read(assets/'concept_visual_bounds.json');self.assertEqual(after['spider'],before['spider'])
   ids={row['runtime'] for row in rework.read(assets/'third_party/model_credits.json')['models']}
   self.assertTrue({'infected_carrier','infected_resonance_priest','infected_spider'}<=ids)
   credits=rework.read(assets/'third_party/model_credits.json');own={row['runtime'] for row in credits['original_reworks']}
   self.assertTrue({'infected_cow','infected_pig','infected_sheep'}<=own)

if __name__=='__main__':unittest.main()
