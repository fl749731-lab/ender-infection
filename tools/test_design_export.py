"""Focused exporter regressions; all generated assets stay in a temporary directory."""
import base64
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
from PIL import Image

import export_design_assets as exporter


def model(source, length=2, impact=.8):
    image = Image.new('RGBA', (2, 1))
    image.putdata([(67, 36, 94, 255), (180, 100, 190, 255)])
    encoded = io.BytesIO()
    image.save(encoded, format='PNG')
    frames = [{'channel': 'rotation', 'time': time,
               'data_points': [{'x': '4', 'y': '8', 'z': '12'}]}
              for time in (0, .2, impact, length)]
    return {
        'meta': {'format_version': '5.0'}, 'model_identifier': source,
        'resolution': {'width': 2, 'height': 1},
        'textures': [{'source': 'data:image/png;base64,' + base64.b64encode(encoded.getvalue()).decode()}],
        'elements': [{'uuid': 'cube', 'name': 'crystal', 'from': [0, 0, 0], 'to': [1, 1, 1],
                      'faces': {'north': {'texture': 0, 'uv': [0, 0, 1, 1]}}}],
        'outliner': [{'uuid': 'root', 'name': 'root', 'origin': [0, 0, 0], 'children': ['cube']}],
        'animations': [{'name': 'animation.' + source + '.attack', 'length': length,
                        'animators': {'root': {'type': 'bone', 'name': 'root', 'keyframes': frames}}}],
    }


class DesignExportTests(unittest.TestCase):
    def setUp(self):
        self.rows = [row for row in exporter.UNITS if row[1] in exporter.NATIVE_ATTACK_SOURCES]
        self.temp_root = (exporter.ROOT / 'build/concept-export-tests').resolve()
        self.assertTrue(self.temp_root.is_relative_to(exporter.ROOT.resolve()))
        self.temp_root.mkdir(parents=True, exist_ok=True)

    def animation_output(self, key, data, **kwargs):
        with patch.object(exporter, 'write') as write:
            exporter.animations(key, data, **kwargs)
            return write.call_args.args[1]['animations']

    def test_native_attack_preserves_intermediate_and_impact_times(self):
        for row in self.rows:
            with self.subTest(unit=row[1]):
                key = 'infected_' + row[1]
                data = model(row[2], row[8] / 20, row[7] / 20)
                result = self.animation_output(key, data, hit=row[7], ticks=row[8])['animation.' + key + '.special']
                self.assertEqual(result['animation_length'], row[8] / 20)
                self.assertEqual(set(map(float, result['bones']['root']['rotation'])),
                                 {0, .2, row[7] / 20, row[8] / 20})

    def test_old_unit_mutant_and_mother_remapping_is_preserved(self):
        data = model('older_concept')
        cases = [('infected_forager', False, 16, 40, {'special': (.8, 2)}),
                 ('infected_core', False, 24, 56, {'special': (1.2, 2.8)}),
                 ('ender_mutant_zombie', True, 16, 40,
                  {'claw_sweep': (.6, 1.3), 'heavy_slam': (.9, 2), 'crystal_burst': (1.4, 2.5)})]
        for key, mutant, hit, ticks, expected in cases:
            output = self.animation_output(key, data, mutant=mutant, hit=hit, ticks=ticks)
            for suffix, (impact, end) in expected.items():
                clip = output['animation.' + key + '.' + suffix]
                self.assertEqual(clip['animation_length'], end)
                self.assertIn(impact, set(map(float, clip['bones']['root']['rotation'])))
                self.assertIn(round(.2 / .8 * impact, 5), set(map(float, clip['bones']['root']['rotation'])))

    def test_invalid_native_clip_is_rejected_before_writing(self):
        row = self.rows[0]
        invalid = [model(row[2]), model(row[2], row[8] / 20, .8), model('wrong_source', row[8] / 20, row[7] / 20)]
        for data in invalid:
            with patch.object(exporter, 'write') as write, self.assertRaises(ValueError):
                exporter.animations('infected_' + row[1], data, hit=row[7], ticks=row[8])
            write.assert_not_called()

    def test_selected_export_preserves_unselected_files_and_report_fields(self):
        with tempfile.TemporaryDirectory(prefix='selected-', dir=self.temp_root) as directory:
            project = Path(directory)
            assets = project / 'assets'
            sentinel = assets / 'models/block/design/current_furniture.obj'
            sentinel.parent.mkdir(parents=True)
            sentinel.write_bytes(b'newer furniture must survive')
            report_path = project / 'verification/design_asset_manifest.json'
            prior = {'source_pack': 'keep-version', 'scenes': 'keep-scenes', 'extra': {'keep': True},
                     'assets': [{'runtime': 'current_furniture', 'source': 'furniture', 'kind': 'block'},
                                {'runtime': 'infected_' + self.rows[0][1], 'extra_unit_field': 19}]}
            exporter.write(report_path, prior)
            data = {row[2]: model(row[2], row[8] / 20, row[7] / 20) for row in self.rows}
            sources = {source: {'runtime_normalization': {'scale': 1, 'offset_y': 0}} for source in data}
            with patch.object(exporter, 'PROJECT', project), patch.object(exporter, 'ASSETS', assets), \
                 patch.object(exporter, 'SOURCES', sources), patch.object(exporter, 'load', side_effect=lambda source: data[source]), \
                 patch.object(exporter, 'export_legacy_glow', side_effect=AssertionError('legacy exporter must not run')):
                exporter.export(', '.join(row[1] for row in self.rows))
            self.assertEqual(sentinel.read_bytes(), b'newer furniture must survive')
            actual = {p.relative_to(assets).as_posix() for p in assets.rglob('*') if p.is_file()}
            expected = {'models/block/design/current_furniture.obj'}
            for row in self.rows:
                key = 'infected_' + row[1]
                expected.update(('geo/' + key + '.geo.json', 'animations/' + key + '.animation.json',
                                 'textures/entity/' + key + '.png', 'textures/entity/' + key + '_glowmask.png'))
                with Image.open(assets / 'textures/entity' / (key + '.png')) as base, \
                     Image.open(assets / 'textures/entity' / (key + '_glowmask.png')) as glow:
                    exporter.validate_crystal_mask(np.array(base), np.array(glow))
                    self.assertEqual(glow.getpixel((1, 0)), (0, 0, 0, 0))
            self.assertEqual(actual, expected)
            result = json.loads(report_path.read_text())
            for field in ('source_pack', 'scenes', 'extra'):
                self.assertEqual(result[field], prior[field])
            self.assertEqual(result['assets'][0], prior['assets'][0])
            self.assertEqual(result['assets'][1]['extra_unit_field'], 19)
            self.assertEqual(len(result['assets']), 5)

    def test_preflight_checks_every_source_before_any_asset_write(self):
        data = {row[2]: model(row[2], row[8] / 20, row[7] / 20) for row in self.rows}
        data[self.rows[-1][2]]['animations'][0]['length'] = 2
        with tempfile.TemporaryDirectory(prefix='preflight-', dir=self.temp_root) as directory:
            project = Path(directory)
            exporter.write(project / 'verification/design_asset_manifest.json', {'assets': []})
            with patch.object(exporter, 'PROJECT', project), patch.object(exporter, 'ASSETS', project / 'assets'), \
                 patch.object(exporter, 'load', side_effect=lambda source: data[source]), \
                 patch.object(exporter, 'write') as write, self.assertRaises(ValueError):
                exporter.export(','.join(row[1] for row in self.rows))
            write.assert_not_called()
            self.assertFalse((project / 'assets').exists())

    def test_selection_and_glow_fail_closed(self):
        for requested in ('', 'siegebreaker,', 'unknown'):
            with self.assertRaises(ValueError):
                exporter.select_units(requested)
        base = np.array([[[67, 36, 94, 255], [180, 100, 190, 255]]], dtype=np.uint8)
        for bad in (base.copy(), np.zeros_like(base)):
            with self.assertRaises(ValueError):
                exporter.validate_crystal_mask(base, bad)
        recolored = copy.deepcopy(base)
        recolored[0, 0, 0] += 1
        recolored[0, 1] = 0
        with self.assertRaises(ValueError):
            exporter.validate_crystal_mask(base, recolored)


if __name__ == '__main__':
    unittest.main()
