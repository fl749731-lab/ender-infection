"""Build the NeoForge workshop assets from the addon's Forge resource source."""
import json

from PIL import Image, ImageDraw

from generate_labware_assets import NS, RES, cube, write


def scoped(path, data):
    write(f'neoforge_only/{path}', data)


def pump(name, precision):
    model = f'{NS}:block/{name}'
    panel = 'minecraft:block/cyan_terracotta' if precision else 'minecraft:block/copper_block'
    scoped(f'assets/{NS}/models/block/{name}.json', {
        'parent': 'minecraft:block/block',
        'textures': {
            'particle': panel, 'housing': 'minecraft:block/smooth_stone',
            'base': 'minecraft:block/polished_deepslate', 'panel': panel,
            'light': 'minecraft:block/emerald_block' if precision else 'minecraft:block/lapis_block',
            'pipe': 'minecraft:block/iron_block',
        },
        'elements': [
            cube([1, 0, 1], [15, 3, 15], 'base'),
            cube([2, 3, 2], [14, 12, 14], 'housing'),
            cube([3, 12, 3], [13, 14, 13], 'panel'),
            cube([5, 5, 0], [11, 10, 2], 'panel'),
            cube([7, 6, 0], [9, 9, 0.5], 'light'),
            cube([5, 5, 14], [11, 10, 16], 'pipe'),
            cube([0, 5, 5], [2, 10, 11], 'pipe'),
            cube([14, 5, 5], [16, 10, 11], 'pipe'),
        ],
    })
    variants = {}
    for stage, copper in enumerate(('copper_block', 'exposed_copper', 'weathered_copper', 'oxidized_copper')):
        stage_model = model
        if stage:
            variant = f'{name}_oxidation_{stage}'
            stage_model = f'{NS}:block/{variant}'
            patina = f'minecraft:block/{copper}'
            scoped(f'assets/{NS}/models/block/{variant}.json', {
                'parent': model, 'textures': {'panel': patina, 'pipe': patina, 'particle': patina},
            })
        for face, angle in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            variants[f'facing={face},oxidation={stage}'] = {'model': stage_model, 'y': angle}
    scoped(f'assets/{NS}/blockstates/{name}.json', {'variants': variants})
    scoped(f'assets/{NS}/models/item/{name}.json', {'parent': model})
    scoped(f'data/{NS}/loot_tables/blocks/{name}.json', {
        'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{
                'type': 'minecraft:item', 'name': f'{NS}:{name}',
                'functions': [{
                    'function': 'minecraft:copy_components', 'source': 'block_entity',
                    'include': ['minecraft:custom_data'],
                }],
            }],
        }],
    })


def recipe(name, pattern, keys, unlock):
    result = {'item': f'{NS}:{name}', 'count': 1}
    scoped(f'data/{NS}/recipes/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'pattern': pattern,
        'key': {key: {'tag': item[1:]} if item.startswith('#') else {'item': item} for key, item in keys.items()},
        'result': result,
    })
    recipe_unlock(name, unlock)


def recipe_unlock(name, material):
    scoped(f'data/{NS}/advancements/recipes/workshop/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [material]}]}},
        },
        'rewards': {'recipes': [f'{NS}:{name}']},
    })


def languages():
    entries = {
        f'item.{NS}.plate_press_blueprint': ('Plate Press Blueprint', '压板机蓝图'),
        f'item.{NS}.iron_plate': ('Iron Plate', '铁板'),
        f'item.{NS}.copper_plate': ('Copper Plate', '铜板'),
        f'item.{NS}.copper_wire': ('Copper Wire', '铜线'),
        f'item.{NS}.basic_motor': ('Basic Motor', '基础电机'),
        f'item.{NS}.precision_pump_head': ('Precision Pump Head', '精密泵头'),
        f'block.{NS}.metering_pump': ('Metering Pump', '计量泵'),
        f'block.{NS}.precision_metering_pump': ('Precision Metering Pump', '精密计量泵'),
        f'machine.{NS}.plate_press': ('Plate Press', '压板机'),
        f'chemistry.{NS}.experiment.pump_head_rinse': ('Pump Head Rinse', '泵头清洗'),
        f'workshop.{NS}.dose': ('Dose %s mB', '单次 %s mB'),
        f'workshop.{NS}.mix': ('Automatic neutralization', '自动中和'),
        f'workshop.{NS}.info': ('%s · %s/%s FE · %s · %s · %s', '%s · %s/%s FE · %s · %s · %s'),
        f'workshop.{NS}.wear.0': ('Clean', '洁净'),
        f'workshop.{NS}.wear.1': ('Tarnished', '轻度氧化'),
        f'workshop.{NS}.wear.2': ('Weathered', '中度氧化'),
        f'workshop.{NS}.wear.3': ('Oxidized', '重度氧化'),
        f'workshop.{NS}.sealed.true': ('Sealed', '已密封'),
        f'workshop.{NS}.sealed.false': ('Unsealed', '未密封'),
        f'workshop.{NS}.status.signal': ('Waiting for redstone', '等待红石信号'),
        f'workshop.{NS}.status.power': ('Waiting for FE', '等待供电'),
        f'workshop.{NS}.status.layout': ('Check same-chunk tanks and bench orientation', '检查同区块液罐与实验台朝向'),
        f'workshop.{NS}.status.input': ('Missing input fluid', '缺少试剂或剂量不足'),
        f'workshop.{NS}.status.vessel': ('Check receiving vessels and ingredient slot', '检查器皿与固体原料槽'),
        f'workshop.{NS}.status.output': ('Output tank is blocked', '成品罐无空间或液体不相容'),
        f'workshop.{NS}.status.dosed': ('Ready for one dose per signal', '每次信号送液一次'),
        f'workshop.{NS}.status.mixing': ('Automatic mixing enabled', '自动混合已启用'),
        f'workshop.{NS}.status.busy': ('Finish or stop the current batch first', '请先完成或停止当前批次'),
        f'workshop.{NS}.status.oxidized': ('Replace the pump head', '更换泵头后恢复'),
        f'workshop.{NS}.status.ready': ('Ready to dispense', '可以送液'),
        f'chemistry.{NS}.status.oxidized': ('Pump jammed from oxidation', '计量泵重度氧化，等待维修'),
        f'guide.{NS}.plate_press.title': ('Electric Workshop — Plate Press', '电动工坊 · 压板机'),
        f'guide.{NS}.plate_press.body': (
            'Form the press with its blueprint. Feed iron or copper ingots and connect FE. Use plates for wire, motors and metering pumps.',
            '用压板机蓝图配置控制器并按图组装。投入铁锭或铜锭，接入 FE。板材用于制作铜线、电机和计量泵。'),
        f'guide.{NS}.metering.title': ('Metering and Automatic Mixing', '计量与自动混合'),
        f'guide.{NS}.metering.body': (
            'Face the pump and bench in the same direction. Feed the rear tank and pulse redstone to dose the selected vessel. Wrench cycles dose. Sneak-use with empty hands to enable mixing: rear acid, left alkali, right product, stirrer ahead with two empty vessels. Keep the fluid blocks in one chunk. Rinse a copper plate with 25 mB distilled water in the stirrer for a precision head.',
            '泵和实验台同向。后方放储液罐，红石脉冲向选中器皿送液。扳手切换剂量。潜行空手切换自动中和：后方酸、左侧碱、右侧成品罐，前方搅拌台放两个空器皿。液路须同一区块。搅拌台用 25 mB 蒸馏水清洗铜板可做精密泵头。'),
    }
    gui = {
        'dose_tab': ('Single Dose', '单次定量'),
        'auto_tab': ('Auto Mix', '自动中和'),
        'maintenance': ('Maintenance', '维护'),
        'change_dose': ('Change dose', '切换剂量'),
        'open_bench': ('Open bench', '打开实验台'),
        'install_seal': ('Fit seal', '安装密封圈'),
        'replace_head': ('Replace head', '更换泵头'),
        'seal_help': ('Install one vulcanized seal from your inventory to stop oxidation.', '从背包安装一枚硫化密封圈，阻止继续氧化。'),
        'repair_help': ('Use one precision pump head from your inventory. The installed seal stays in place.', '消耗背包中的一个精密泵头，已安装的密封圈保留。'),
        'input': ('Inputs', '进液'),
        'acid_input': ('Acid', '果酸'),
        'alkali_input': ('Alkali', '碱液'),
        'reaction': ('Batch', '中和批次'),
        'dose_label': ('Target dose', '目标剂量'),
        'stock': ('Rear tank', '后方进液罐'),
        'reagent': ('Left tank', '左侧试剂罐'),
        'product': ('Product', '成品'),
        'vessel': ('Vessel', '器皿'),
        'signal_on': ('RS on', '红石开'),
        'signal_off': ('RS off', '红石关'),
        'condition': ('%s · %s%%', '%s · %s%%'),
        'protected': ('Oxidation stopped', '氧化已停止'),
        'service_needed': ('Pump head needed', '需要更换泵头'),
        'next_stage': ('Next %s  ×%s', '下一级 %s · ×%s'),
        'disconnected_short': ('—', '—'),
        'disconnected': ('No connected container', '未连接容器'),
        'empty': ('Empty', '空'),
        'reason.signal': ('Waiting for redstone', '等待红石'),
        'reason.power': ('Insufficient FE', '电量不足'),
        'reason.layout': ('Check connections', '检查设备与接管'),
        'reason.input': ('Missing fluid', '液体不足或种类不符'),
        'reason.vessel': ('Check vessels', '检查器皿与原料槽'),
        'reason.output': ('Output blocked', '成品罐堵塞'),
        'reason.dosed': ('Pulse completed', '本次脉冲已送液'),
        'reason.mixing': ('Auto mix enabled', '自动中和已启用'),
        'reason.busy': ('Bench busy', '实验台正在处理'),
        'reason.oxidized': ('Oxidized · replace head', '氧化停机 · 更换泵头'),
        'reason.ready': ('Ready', '可送液'),
    }
    entries.update({f'workshop.{NS}.gui.{key}': pair for key, pair in gui.items()})
    for index, language in enumerate(('en_us', 'zh_cn')):
        path = f'assets/{NS}/lang/{language}.json'
        data = json.loads((RES / path).read_text('utf-8'))
        data.update({key: values[index] for key, values in entries.items()})
        write(path, data)


def item_sprites():
    directory = RES / f'neoforge_only/assets/{NS}/textures/item'
    directory.mkdir(parents=True, exist_ok=True)
    for metal, shade, face, edge in (
        ('iron', '#515e66', '#a6b7bf', '#e5eff2'),
        ('copper', '#71352a', '#c77348', '#f9ad76'),
    ):
        image = Image.new('RGBA', (16, 16))
        draw = ImageDraw.Draw(image)
        draw.polygon([(1, 9), (6, 4), (14, 6), (14, 9), (9, 13), (1, 11)], fill=shade)
        draw.polygon([(2, 9), (6, 5), (13, 7), (9, 11)], fill=face)
        draw.line([(2, 9), (6, 5), (13, 7)], fill=edge)
        image.save(directory / f'{metal}_plate.png')

    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.ellipse([2, 2, 13, 13], outline='#6f372a', width=3)
    draw.ellipse([2, 2, 13, 12], outline='#e79260', width=2)
    draw.line([(6, 10), (9, 14), (13, 14), (14, 11)], fill='#cf7244', width=2)
    image.save(directory / 'copper_wire.png')

    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 7, 4, 9], fill='#c2cdd0')
    draw.rectangle([4, 4, 13, 12], fill='#454b54')
    draw.rectangle([5, 5, 12, 11], fill='#a9b8c1')
    for x in (6, 8, 10):
        draw.line([(x, 5), (x, 10)], fill='#c2764f')
    draw.rectangle([4, 12, 13, 13], fill='#707985')
    image.save(directory / 'basic_motor.png')

    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 7, 5, 10], fill='#849ca4')
    draw.rectangle([5, 4, 11, 12], fill='#245a5c')
    draw.rectangle([6, 5, 10, 11], fill='#58b0a5')
    draw.line([(6, 5), (10, 5)], fill='#b6eadb')
    draw.rectangle([12, 6, 14, 10], fill='#869ca0')
    image.save(directory / 'precision_pump_head.png')


def main():
    pump('metering_pump', False)
    pump('precision_metering_pump', True)
    item_sprites()
    textures = {name: f'{NS}:item/{name}' for name in
                ('iron_plate', 'copper_plate', 'copper_wire', 'basic_motor', 'precision_pump_head')}
    textures['plate_press_blueprint'] = 'minecraft:item/map'
    for name, texture in textures.items():
        scoped(f'assets/{NS}/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': texture}})
    recipe('plate_press_blueprint', [' I ', 'CPC', ' R '], {
        'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot', 'P': 'minecraft:paper', 'R': 'minecraft:redstone',
    }, f'{NS}:manual')
    scoped(f'data/{NS}/recipes/copper_wire.json', {
        'type': 'minecraft:crafting_shapeless', 'ingredients': [{'item': f'{NS}:copper_plate'}],
        'result': {'item': f'{NS}:copper_wire', 'count': 4},
    })
    recipe_unlock('copper_wire', f'{NS}:copper_plate')
    recipe('basic_motor', [' W ', 'WIR', ' C '], {
        'W': f'{NS}:copper_wire', 'I': f'#{NS}:motor_plates', 'R': 'minecraft:redstone', 'C': f'{NS}:copper_plate',
    }, f'{NS}:iron_plate')
    recipe('metering_pump', [' G ', 'PMP', ' I '], {
        'G': 'minecraft:glass_pane', 'P': f'#{NS}:pump_plates', 'M': f'{NS}:basic_motor', 'I': f'{NS}:iron_plate',
    }, f'{NS}:basic_motor')
    scoped(f'data/{NS}/recipes/precision_metering_pump.json', {
        'type': 'minecraft:crafting_shapeless', 'ingredients': [
            {'item': f'{NS}:metering_pump'}, {'item': f'{NS}:precision_pump_head'},
        ], 'result': {'item': f'{NS}:precision_metering_pump', 'count': 1},
    })
    recipe_unlock('precision_metering_pump', f'{NS}:precision_pump_head')
    languages()


if __name__ == '__main__':
    main()
