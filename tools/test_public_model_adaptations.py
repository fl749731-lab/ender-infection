"""Regression coverage for multi-texture packing and the authored rig contract."""
import base64,io,json,shutil,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
import export_public_model_adaptations as export

class AdaptationTests(unittest.TestCase):
 def test_priest_five_original_images_survive_exactly(self):
  d=export.read(export.PACK/'models/resonance_priest.bbmodel');files,row=export.build('resonance_priest');atlas=Image.open(io.BytesIO(files['textures/entity/infected_resonance_priest.png']))
  for i,rect in enumerate([(0,0,128,128),(128,0,256,128),(256,0,512,256),(0,256,256,512),(256,256,512,512)]):
   source=Image.open(io.BytesIO(base64.b64decode(d['textures'][i]['source'].split(',')[1]))).convert('RGBA')
   self.assertEqual(atlas.crop(rect).tobytes(),source.tobytes())

 def test_carrier_cargo_uses_actual_pixel_scale_and_only_crystals_glow(self):
  d=export.read(export.PACK/'models/crystalback_carrier.bbmodel');files,row=export.build('crystalback_carrier')
  atlas=Image.open(io.BytesIO(files['textures/entity/infected_carrier.png'])).convert('RGBA');glow=Image.open(io.BytesIO(files['textures/entity/infected_carrier_glowmask.png'])).convert('RGBA')
  body=Image.open(io.BytesIO(base64.b64decode(d['textures'][3]['source'].split(',')[1]))).convert('RGBA');cargo=Image.open(io.BytesIO(base64.b64decode(d['textures'][4]['source'].split(',')[1]))).convert('RGBA')
  self.assertEqual(atlas.crop((0,0,256,256)).tobytes(),body.tobytes())
  self.assertEqual(atlas.crop((257,1,263,4)).tobytes(),cargo.crop((329,163,335,166)).tobytes())
  self.assertIsNone(glow.crop((0,0,256,256)).getbbox())
  self.assertEqual(glow.crop((257,1,263,4)).tobytes(),cargo.crop((329,163,335,166)).tobytes())

 def test_five_actions_and_baked_tail_rig_survive(self):
  for name,spec in export.SPECS.items():
   files,_=export.build(name);clips=json.loads(files['animations/'+spec['runtime']+'.animation.json'])['animations'];prefix='animation.'+spec['runtime']+'.'
   self.assertEqual(set(clips),{prefix+s for s in ['idle','walk','special','infection','death']})
   self.assertEqual(clips[prefix+'special']['animation_length'],2)
   self.assertGreater(json.dumps(clips).count('catmullrom'),20)
   if name=='crystalback_carrier':
    for clip in ['idle','walk']:
     for bone in ['tail','tail1_1','tail1_2','tail1_3','tail1_4']:self.assertIn(bone,clips[prefix+clip]['bones'])

 def test_a_changed_source_requires_a_fresh_verified_mcp_export(self):
  with tempfile.TemporaryDirectory(dir=export.ROOT/'build',prefix='adaptation-lock-') as tmp:
   folder=Path(tmp);shutil.copytree(export.PACK,folder,dirs_exist_ok=True);file=folder/'models/resonance_priest.bbmodel';file.write_bytes(file.read_bytes()+b' ')
   with patch.object(export,'PACK',folder),self.assertRaisesRegex(ValueError,'re-export and verify'):export.build('resonance_priest')

 def test_locomotion_channels_close_without_pose_jumps(self):
  for name in export.SPECS:
   d=export.read(export.PACK/'models'/f'{name}.bbmodel')
   for a in d['animations']:
    if a['name'] not in ('idle','walk'):continue
    for animator in a['animators'].values():
     for channel in ('position','rotation','scale'):
      keys=sorted([k for k in animator.get('keyframes',[]) if k['channel']==channel],key=lambda k:k['time'])
      if not keys:continue
      value=lambda key:[float(key['data_points'][0][axis]) for axis in ('x','y','z')]
      self.assertEqual(value(keys[0]),value(keys[-1]),(name,a['name'],animator.get('name'),channel))

if __name__=='__main__':unittest.main()
