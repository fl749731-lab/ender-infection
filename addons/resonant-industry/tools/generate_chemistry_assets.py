"""Generate the starter chemistry stations and merge their bilingual text."""
import json

from generate_labware_assets import NS, RES, cube, write


STATIONS = {
    'hot_plate': ('minecraft:block/copper_block', 'minecraft:block/magma', 'minecraft:block/nether_bricks'),
    'magnetic_stirrer': ('minecraft:block/blue_terracotta', 'minecraft:block/redstone_block', 'minecraft:block/polished_andesite'),
}


def station_models(name, metal, glow, surface):
    prefix = f'{NS}:block/{name}'
    variants = {}
    for on in (False, True):
        suffix = '_running' if on else ''
        textures = {'body': metal, 'worktop': surface, 'edge': 'minecraft:block/iron_block',
                    'pad': 'minecraft:block/smooth_stone', 'lamp': glow if on else 'minecraft:block/deepslate',
                    'particle': metal}
        elements = [cube([0, 10, 0], [16, 13, 16], 'worktop'), cube([1, 4, 1], [15, 10, 15], 'body'),
                    cube([2, 0, 2], [5, 4, 5], 'edge'), cube([11, 0, 2], [14, 4, 5], 'edge'),
                    cube([2, 0, 11], [5, 4, 14], 'edge'), cube([11, 0, 11], [14, 4, 14], 'edge'),
                    cube([1, 13, 2], [6.5, 14, 7.5], 'lamp'), cube([6.5, 13, 2], [10.5, 14, 7.5], 'pad'),
                    cube([1, 7, .5], [4, 8.5, 1], 'lamp'), cube([11, 6, .5], [14, 9, 1], 'edge')]
        if name == 'hot_plate':
            elements.append(cube([0, 10, 10], [2, 12, 12], 'body'))
        write(f'assets/{NS}/models/block/{name}{suffix}.json', {'textures': textures, 'elements': elements})
        for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            variants[f'facing={facing},lit={str(on).lower()}'] = {'model': prefix + suffix, 'y': angle}
    write(f'assets/{NS}/blockstates/{name}.json', {'variants': variants})
    write(f'assets/{NS}/models/item/{name}.json', {'parent': prefix})
    write(f'data/{NS}/loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})


def recipes():
    for name, pattern, ingredients in (
        ('hot_plate', ['III', 'CFC', ' B '], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot',
                                             'F': 'minecraft:furnace', 'B': f'{NS}:lab_bench'}),
        ('magnetic_stirrer', ['III', 'CRC', ' B '], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot',
                                                   'R': 'minecraft:redstone', 'B': f'{NS}:lab_bench'}),
    ):
        write(f'data/{NS}/recipes/{name}.json', {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
              'key': {k: {'item': item} for k, item in ingredients.items()}, 'result': {'item': f'{NS}:{name}', 'count': 1}})
        write(f'data/{NS}/advancements/recipes/labware/{name}.json', {'parent': 'minecraft:recipes/root',
              'criteria': {'bench': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [f'{NS}:lab_bench']}]}}},
              'rewards': {'recipes': [f'{NS}:{name}']}})


def translations(lang, names, labels, statuses, experiments, pages):
    path = f'assets/{NS}/lang/{lang}.json'
    text = json.loads((RES / path).read_text('utf-8'))
    keys = ['block.hot_plate', 'block.magnetic_stirrer', 'item.alkali_ash', 'item.neutral_salt',
            'fluid.acid_extract', 'fluid.alkaline_extract', 'fluid.neutral_solution']
    for key, name in zip(keys, names):
        category, key = key.split('.')
        text[f'{category}.{NS}.{key}'] = name
    keys = ['start', 'stop', 'sample', 'reagent', 'ingredient', 'fuel', 'product', 'temperature', 'burn',
            'power_on', 'power_off', 'evaporated']
    for key, label in zip(keys, labels):
        text[f'chemistry.{NS}.{key}'] = label
    for key, label in zip('idle inputs fuel heating signal running output complete stopped'.split(), statuses):
        text[f'chemistry.{NS}.status.{key}'] = label
    for key, label in zip('alkali_ash fruit_extraction alkali_extraction neutralization crystallization tissue_wash'.split(), experiments):
        text[f'chemistry.{NS}.experiment.{key}'] = label
    for page, (title, body) in zip(('chemistry', 'experiments'), pages):
        text[f'guide.{NS}.{page}.title'] = title
        text[f'guide.{NS}.{page}.body'] = body
    write(path, text)


def main():
    for name, material in STATIONS.items():
        station_models(name, *material)
    for item, texture in [('alkali_ash', 'gunpowder'), ('neutral_salt', 'sugar')]:
        write(f'assets/{NS}/models/item/{item}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'minecraft:item/{texture}'}})
    recipes()
    tag_path = 'data/minecraft/tags/blocks/mineable/pickaxe.json'
    tag = json.loads((RES / tag_path).read_text('utf-8'))
    tag['values'] = list(dict.fromkeys([*tag['values'], *(f'{NS}:{name}' for name in STATIONS)]))
    write(tag_path, tag)
    translations('zh_cn', ['加热台', '磁力搅拌台', '碱灰', '中和盐晶', '果酸浸液', '碱性浸液', '中和液'],
                 ['开始', '停止', '样品 #1', '试液 #2', '原料', '燃料', '产物', '温度 %s°C', '燃烧 %s 刻', '红石已供电', '红石已断开', '蒸发 %s mB'],
                 ['待开始', '检查原料与液量', '等待燃料', '正在升温', '等待红石信号', '正在实验', '产物槽堵塞', '实验完成', '已停止'],
                 ['烧制碱灰', '果酸提取', '碱液浸取', '酸碱中和', '蒸发结晶', '组织清洗'], [
        ('实验设备 · 提取', '1 号位放烧杯或试管，2 号位放液体试剂。原料、燃料和固体产物在右栏。放好后点击开始。加热台用煤或木炭，搅拌台旁放拉杆并接通红石。\n空烧杯 + 干海带，在加热台烧制碱灰。\n250 mB 水 + 苹果，在搅拌台制得果酸浸液。\n250 mB 水 + 碱灰，在搅拌台制得碱性浸液。'),
        ('基础实验 · 中和与结晶', '在 1 号位试管放 25 mB 果酸浸液，2 号位滴瓶放至少 25 mB 碱性浸液。搅拌后得到 50 mB 中和液，滴瓶余液保留。可用滴管量取。\n把 50 mB 中和液移到加热台 1 号位，蒸发结晶得到中和盐晶。\n也可在搅拌台用 50 mB 中和液清洗原始组织样本，得到纯化组织和 50 mB 营养浆液。')])
    translations('en_us', ['Hot Plate', 'Magnetic Stirrer', 'Alkali Ash', 'Neutral Salt Crystals', 'Fruit Acid Extract', 'Alkaline Extract', 'Neutral Solution'],
                 ['Start', 'Stop', 'Sample #1', 'Liquid #2', 'In', 'Fuel', 'Out', 'Temp %s°C', 'Fuel %s t', 'Signal on', 'Signal off', 'Vented %s mB'],
                 ['Ready', 'Check inputs', 'Needs fuel', 'Warming up', 'Needs signal', 'Experimenting', 'Output blocked', 'Complete', 'Stopped'],
                 ['Alkali ash', 'Fruit extraction', 'Alkali extraction', 'Neutralization', 'Crystallization', 'Tissue wash'], [
        ('Lab equipment · Extraction', 'Place a beaker or test tube in #1 and liquid reagent in #2. Use the right panel for solids and fuel. Click Start. The heater needs coal or charcoal. Power the stirrer with a redstone signal.\nEmpty beaker + dried kelp on the heater makes alkali ash.\n250 mB water + apple in the stirrer yields fruit acid extract.\n250 mB water + alkali ash in the stirrer yields alkaline extract.'),
        ('Experiments · Neutralization', 'Measure 25 mB fruit acid into #1 and at least 25 mB alkaline extract into #2. The stirrer makes 50 mB neutral solution and keeps the unused reagent.\nMove 50 mB neutral solution to heater slot #1 to evaporate it into salt crystals.\nOr stir 50 mB neutral solution with a raw tissue sample to obtain purified tissue and 50 mB nutrient slurry.')])


if __name__ == '__main__':
    main()
