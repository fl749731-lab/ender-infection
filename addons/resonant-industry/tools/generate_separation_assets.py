"""Native station models, survival recipes, and the separation chapter."""
import json

from PIL import Image, ImageDraw

from generate_labware_assets import NS, RES, cube, write


def station(name, surface, attachments):
    prefix = f'{NS}:block/{name}'
    variants = {}
    for running in (False, True):
        model = prefix + ('_running' if running else '')
        textures = {
            'body': surface, 'metal': 'minecraft:block/iron_block', 'tube': 'minecraft:block/copper_block',
            'glass': 'minecraft:block/light_blue_stained_glass', 'paper': 'minecraft:block/quartz_block_top',
            'lamp': 'minecraft:block/redstone_block' if running else 'minecraft:block/deepslate', 'particle': surface,
        }
        elements = [cube([0, 11, 0], [16, 14, 16], 'body'), cube([1, 7, 1], [15, 11, 15], 'metal'),
                    cube([1, 8, .5], [4, 10, 1], 'lamp')]
        for x in (1, 12):
            for z in (1, 12):
                elements.append(cube([x, 0, z], [x + 3, 7, z + 3], 'metal'))
        elements.extend(cube(low, high, material) for low, high, material in attachments)
        write(f'assets/{NS}/models/block/{model.split("/")[-1]}.json', {
            'textures': textures, 'render_type': 'minecraft:translucent', 'elements': elements,
        })
        for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            variants[f'facing={facing},lit={str(running).lower()}'] = {'model': model, 'y': angle}
    write(f'assets/{NS}/blockstates/{name}.json', {'variants': variants})
    write(f'assets/{NS}/models/item/{name}.json', {'parent': prefix})
    write(f'data/{NS}/loot_tables/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}],
    }]})


def unlock(name, material):
    write(f'data/{NS}/advancements/recipes/labware/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {'material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [material]}]}}},
        'rewards': {'recipes': [f'{NS}:{name}']},
    })


def recipes():
    for name, pattern, keys in (
        ('filter_funnel', [' P ', 'GIG', ' B '], {'P': 'minecraft:paper', 'G': 'minecraft:glass_pane'}),
        ('condenser', ['CGC', 'GIG', ' B '], {'C': 'minecraft:copper_ingot', 'G': 'minecraft:glass_pane'}),
    ):
        keys.update(I='minecraft:iron_ingot', B=f'{NS}:lab_bench')
        write(f'data/{NS}/recipes/{name}.json', {'type': 'minecraft:crafting_shaped', 'pattern': pattern,
              'key': {k: {'item': v} for k, v in keys.items()}, 'result': {'item': f'{NS}:{name}', 'count': 1}})
        unlock(name, f'{NS}:lab_bench')
    write(f'data/{NS}/recipes/filter_paper.json', {'type': 'minecraft:crafting_shapeless',
          'ingredients': [{'item': 'minecraft:paper'}], 'result': {'item': f'{NS}:filter_paper', 'count': 2}})
    unlock('filter_paper', 'minecraft:paper')


def language(lang, names, panel, experiments, pages):
    path = f'assets/{NS}/lang/{lang}.json'
    text = json.loads((RES / path).read_text('utf-8'))
    entries = ['block.filter_funnel', 'block.condenser', 'item.filter_paper', 'fluid.calcium_extract',
               'fluid.calcium_suspension', 'fluid.clear_filtrate', 'fluid.distilled_water',
               'fluid.filtrate_concentrate', 'fluid.neutral_concentrate']
    for entry, name in zip(entries, names):
        category, key = entry.split('.')
        text[f'{category}.{NS}.{key}'] = name
    keys = ['receiver', 'paper', 'distill', 'distill_help', 'recovered', 'condenser_linked', 'condenser_unlinked',
            'status.condenser', 'status.receiver']
    for key, name in zip(keys, panel):
        text[f'chemistry.{NS}.{key}'] = name
    keys = ['calcium_extraction', 'precipitation', 'filtration', 'filtrate_distillation', 'neutral_distillation']
    for key, name in zip(keys, experiments):
        text[f'chemistry.{NS}.experiment.{key}'] = name
    for key, (title, body) in zip(['filtration', 'distillation'], pages):
        text[f'guide.{NS}.{key}.title'] = title
        text[f'guide.{NS}.{key}.body'] = body
    write(path, text)


def main():
    station('filter_funnel', 'minecraft:block/polished_andesite', [
        ([5.5, 14, 13], [6.5, 22, 14], 'metal'), ([6, 15, 11], [7, 18, 12], 'glass'),
        ([5.2, 18, 10.2], [7.8, 19, 12.8], 'glass'), ([4.5, 19, 9.5], [8.5, 20, 13.5], 'glass'),
        ([4, 20, 9], [9, 21, 14], 'glass'), ([4.4, 20.6, 9.4], [8.6, 21, 13.6], 'paper'),
    ])
    station('condenser', 'minecraft:block/cyan_terracotta', [
        ([14, 10, 10], [16, 12, 12], 'tube'), ([12, 12, 11], [14, 16, 13], 'tube'),
        ([5, 14, 13.5], [14, 23, 15.5], 'glass'), ([6, 21, 13], [13, 22, 15], 'tube'),
        ([6, 18, 13], [13, 19, 15], 'tube'), ([6, 15, 13], [13, 16, 15], 'tube'),
        ([6, 19, 13], [7, 22, 15], 'tube'), ([12, 16, 13], [13, 19, 15], 'tube'),
        ([4, 14, 7], [5, 16, 14], 'tube'), ([3, 16, 5], [5, 17, 8], 'tube'),
    ])
    path = RES / f'assets/{NS}/textures/item/filter_paper.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    icon = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(icon)
    draw.polygon([(2, 3), (10, 1), (14, 4), (11, 13), (6, 15)], fill='#f2e8c6', outline='#766c59')
    draw.line([(2, 3), (8, 6), (14, 4)], fill='#b3a489')
    draw.line([(8, 6), (6, 14)], fill='#d3c29e')
    icon.save(path)
    write(f'assets/{NS}/models/item/filter_paper.json', {'parent': 'minecraft:item/generated',
          'textures': {'layer0': f'{NS}:item/filter_paper'}})
    recipes()
    tag_path = 'data/minecraft/tags/blocks/mineable/pickaxe.json'
    tag = json.loads((RES / tag_path).read_text('utf-8'))
    tag['values'] = list(dict.fromkeys([*tag['values'], f'{NS}:filter_funnel', f'{NS}:condenser']))
    write(tag_path, tag)
    language('zh_cn', ['过滤漏斗', '冷凝装置', '滤纸', '含钙浸液', '碳酸钙悬液', '澄清滤液', '蒸馏水', '滤液浓缩液', '中和浓缩液'],
             ['接收 #2', '滤纸', '蒸馏', '冷凝装置紧贴加热台右侧，两者同向。冷凝装置 1 号位放接收器。',
              '回收 %s mB', '冷凝已连接', '冷凝未连接', '等待冷凝装置', '检查接收器'],
             ['溶钙提取', '碳酸钙沉淀', '过滤沉淀', '滤液蒸馏', '中和液蒸馏'], [
        ('分离实验 · 沉淀过滤', '搅拌台 1 号位放 25 mB 果酸浸液，原料放 1 块方解石，制成 25 mB 含钙浸液。再与 2 号位的 25 mB 碱性浸液搅拌，得到 50 mB 碳酸钙悬液。\n把悬液移到过滤漏斗 1 号位，2 号位放空试管或烧杯，滤纸槽放 1 张滤纸。点击开始，右栏收回方解石，2 号位得到 50 mB 澄清滤液。'),
        ('分离实验 · 蒸馏回收', '面向加热台，把冷凝装置紧贴右侧放置，两个台面同向。冷凝装置 1 号位放空试管或烧杯。\n加热台 1 号位放 50 mB 澄清滤液或中和液，装入煤或木炭，点左侧蒸馏。升到 100°C 后开始计时。\n完成后冷凝装置收到 40 mB 蒸馏水，原器皿保留 10 mB 浓缩液。接收器满时暂停。蒸馏水可用于果酸、碱液提取。')])
    language('en_us', ['Filter Funnel', 'Condenser', 'Filter Paper', 'Calcium Extract', 'Calcium Carbonate Suspension',
                        'Clear Filtrate', 'Distilled Water', 'Filtrate Concentrate', 'Neutral Concentrate'],
             ['Receiver #2', 'Paper', 'Distill', 'Place the condenser to the right of the heater as you face it. Face both devices the same way. Put a vessel in condenser slot #1.',
              'Saved %s mB', 'Connected', 'No condenser', 'Needs condenser', 'Check receiver'],
             ['Calcium extract', 'Precipitation', 'Filtration', 'Filtrate still', 'Neutral still'], [
        ('Separation · Filtration', 'Stir 25 mB fruit acid with a calcite block to make 25 mB calcium extract. Stir that sample with 25 mB alkaline extract in #2 to form 50 mB suspension.\nPut suspension in filter slot #1, a receiving beaker or test tube in #2, and filter paper in the right panel. Start to recover calcite and 50 mB clear filtrate.'),
        ('Separation · Distillation', 'Facing the heater, place a condenser against its right side. Face both devices the same way and put a receiving vessel in condenser slot #1.\nUse 50 mB filtrate or neutral solution in heater slot #1. Add coal or charcoal and press Distill. Processing starts at 100°C.\nCollect 40 mB distilled water and keep 10 mB concentrate in the heated vessel. A full receiver pauses the batch. Distilled water works in fruit and alkali extraction.')])


if __name__ == '__main__':
    main()
