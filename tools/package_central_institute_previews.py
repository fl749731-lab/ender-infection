"""Package final client screenshots into portable, labelled review sheets.

No screenshot pixels are retouched: contact images are only proportionally
resized and letterboxed. Three representative originals are copied byte for
byte. The receipt records all 61 input SHA-256 values and package-relative
output paths, so the previews remain traceable to the client evidence.
"""
from __future__ import annotations

import argparse
from hashlib import sha256
import json
import os
from pathlib import Path
import shutil

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'ender_infection_colored/src/main/resources/data/ender_infection/central_institute.json'
LOADERS = {'neoforge': ('ender_infection_neoforge', 'Minecraft 1.21.1 / NeoForge'),
           'forge': ('ender_infection_colored', 'Minecraft 1.20.1 / Forge')}
FLOORS = [('A', 'G1', '接收与公共服务'), ('B', 'G2', '分析与研究'),
          ('C', 'G3', '总控与温室'), ('D', 'B1', '设施运维'),
          ('E', 'B2', '收容与隔离'), ('F', 'B3', '裂隙核心实验')]
OVERVIEW = [('00_exterior_front.png', '正面外观'),
            ('01_exterior_rear.png', '背面外观'),
            ('02_atrium.png', '中庭与立体交通'),
            ('night_reactor.png', '夜间裂隙实验大厅')]
BACKGROUND = '#eef0f2'
CARD = '#ffffff'
INK = '#202832'


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def font_path() -> Path | None:
    windows = Path(os.environ.get('WINDIR', 'C:/Windows')) / 'Fonts'
    candidates = [windows / 'msyh.ttc', windows / 'msyhbd.ttc', windows / 'simhei.ttf',
                  Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'),
                  Path('/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf'),
                  Path('/System/Library/Fonts/PingFang.ttc'),
                  Path('/usr/share/fonts/truetype/wqy/wqy-microhei.ttc')]
    return next((path for path in candidates if path.is_file()), None)


def font(size: int, path: Path | None):
    if path:
        return ImageFont.truetype(str(path), size=size)
    # Pillow can discover a platform-provided CJK font by name even if its
    # installed directory is outside the conventional paths above.
    for name in ('Microsoft YaHei', 'NotoSansCJK-Regular.ttc', 'Arial Unicode.ttf'):
        try:
            return ImageFont.truetype(name, size=size)
        except OSError:
            pass
    raise RuntimeError('A Chinese font is required. Install Noto Sans CJK or Microsoft YaHei.')


def put_label(draw: ImageDraw.ImageDraw, text: str, box, path, size=20):
    x, y, width, height = box
    current = size
    while current >= 12:
        selected = font(current, path)
        bounds = draw.textbbox((0, 0), text, font=selected)
        if bounds[2] - bounds[0] <= width - 16:
            break
        current -= 1
    draw.text((x + 8, y + (height - (bounds[3] - bounds[1])) / 2 - bounds[1]),
              text, font=selected, fill=INK)


def image_into(canvas: Image.Image, path: Path, x: int, y: int, width: int, height: int):
    with Image.open(path) as opened:
        source = opened.convert('RGB')
        thumbnail = ImageOps.contain(source, (width, height), Image.Resampling.LANCZOS)
    canvas.paste('#111318', (x, y, x + width, y + height))
    canvas.paste(thumbnail, (x + (width - thumbnail.width) // 2, y + (height - thumbnail.height) // 2))


def room_sheet(source, destination, rooms, floor, label, loader_label, selected_font):
    image_width, image_height, label_height, header = 320, 180, 34, 48
    sheet = Image.new('RGB', (image_width * 4, header + (image_height + label_height) * 2), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    put_label(draw, f'{floor}  {label}  |  {loader_label}', (0, 0, sheet.width, header), selected_font, 23)
    for index, room in enumerate(rooms):
        x, y = (index % 4) * image_width, header + (index // 4) * (image_height + label_height)
        image_into(sheet, source / f"room_{room['id']}.png", x, y, image_width, image_height)
        draw.rectangle((x, y + image_height, x + image_width, y + image_height + label_height), fill=CARD)
        put_label(draw, f"{room['id']}  {room['name']}", (x, y + image_height, image_width, label_height), selected_font)
    sheet.save(destination)


def overview_sheet(source, destination, loader_label, selected_font):
    width, height, label_height, header = 640, 360, 38, 48
    sheet = Image.new('RGB', (width * 2, header + (height + label_height) * 2), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    put_label(draw, f'中央研究所运行实景  |  {loader_label}', (0, 0, sheet.width, header), selected_font, 24)
    for index, (filename, label) in enumerate(OVERVIEW):
        x, y = index % 2 * width, header + index // 2 * (height + label_height)
        image_into(sheet, source / filename, x, y, width, height)
        draw.rectangle((x, y + height, x + width, y + height + label_height), fill=CARD)
        put_label(draw, label, (x, y + height, width, label_height), selected_font, 23)
    sheet.save(destination)


def image_receipt(path: Path, relative_to: Path):
    with Image.open(path) as image:
        width, height = image.size
        image.verify()
    return {'path': path.relative_to(relative_to).as_posix(), 'sha256': digest(path),
            'width': width, 'height': height, 'bytes': path.stat().st_size}


def package(loader: str):
    project, loader_label = LOADERS[loader]
    source = ROOT / project / 'build/central-institute-visual-check/screenshots'
    destination = ROOT / 'previews/central-institute-runtime' / loader
    manifest = json.loads(MANIFEST.read_text('utf-8'))
    rooms = {room['id']: room for room in manifest['rooms']}
    expected = {f'room_{room_id}.png' for room_id in rooms}
    expected.update(filename for filename, _ in OVERVIEW)
    expected.update(f'floor_{floor}.png' for _, floor, _ in FLOORS)
    expected.update(('night_exterior.png', 'stairs_1.png', 'stairs_10.png'))
    if len(rooms) != 48 or len(expected) != 61:
        raise ValueError('Expected the final 48-room / 61-screenshot evidence set')
    actual = {path.name for path in source.glob('*.png')}
    if actual != expected:
        raise ValueError(f'Final screenshots differ: missing={sorted(expected - actual)}, extra={sorted(actual - expected)}')
    selected_font = font_path()
    font(20, selected_font)  # Fail before writing a partial package if CJK is unavailable.
    destination.mkdir(parents=True, exist_ok=True)
    inputs = [image_receipt(source / filename, ROOT) for filename in sorted(expected)]
    generated = []
    for prefix, floor, label in FLOORS:
        group = [rooms[f'{prefix}{index}'] for index in range(1, 9)]
        if any(room['floor'] != floor for room in group):
            raise ValueError(f'Unexpected room/floor assignment in {prefix}')
        output = destination / f'rooms_{prefix}_{floor}.png'
        room_sheet(source, output, group, floor, label, loader_label, selected_font)
        generated.append(image_receipt(output, destination))
    overview = destination / 'overview.png'
    overview_sheet(source, overview, loader_label, selected_font)
    generated.append(image_receipt(overview, destination))
    copies = []
    for original, filename in [('00_exterior_front.png', 'exterior_front.png'),
                               ('02_atrium.png', 'atrium.png'),
                               ('night_reactor.png', 'night_reactor.png')]:
        output = destination / filename
        shutil.copyfile(source / original, output)
        entry = image_receipt(output, destination)
        entry['source'] = (source / original).relative_to(ROOT).as_posix()
        if entry['sha256'] != digest(source / original):
            raise ValueError(f'Original screenshot copy changed bytes: {filename}')
        copies.append(entry)
    receipt = {'format_version': 1, 'loader': loader, 'loader_label': loader_label,
               'layout_version': manifest['layout_version'],
               'source_directory': source.relative_to(ROOT).as_posix(),
               'manifest': {'path': MANIFEST.relative_to(ROOT).as_posix(), 'sha256': digest(MANIFEST)},
               'font': selected_font.name if selected_font else 'system-discovered CJK',
               'image_operations': 'Proportional resize and letterbox only; copied originals are byte-identical.',
               'input_path_base': 'workspace root', 'output_path_base': 'this receipt directory',
               'source_images': inputs, 'generated_images': generated, 'copied_originals': copies}
    (destination / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'loader': loader, 'inputs': len(inputs), 'room_sheets': 6, 'overview': 1,
                      'copied_originals': len(copies), 'directory': destination.relative_to(ROOT).as_posix()}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--loader', choices=tuple(LOADERS), required=True)
    package(parser.parse_args().loader)


if __name__ == '__main__':
    main()
