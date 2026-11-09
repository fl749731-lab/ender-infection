"""Generate the NeoForge mineral textures, loot, recipes and worldgen data."""
import json
from random import Random

from PIL import Image, ImageDraw

from generate_workshop_assets import NS, RES, scoped, recipe_unlock, write

MINERALS = (
    # Mineral, element, depth range, attempts per chunk, vein size, drops, palette.
    ('cassiterite', 'tin', -24, 64, 6, 7, 'raw_tin', ('#343b4a', '#8694a6', '#e0e6e7')),
    ('sphalerite', 'zinc', -56, 24, 5, 6, 'raw_zinc', ('#44363b', '#91674d', '#d1ad78')),
    ('sulfur', 'sulfur', -40, 48, 4, 8, 'sulfur', ('#716229', '#d6aa32', '#f9e17c')),
)


def ore_loot(block, drop):
    return {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:alternatives',
                'children': [
                    {'type': 'minecraft:item', 'name': f'{NS}:{block}', 'conditions': [{
                        'condition': 'minecraft:match_tool',
                        'predicate': {'enchantments': [{'enchantment': 'minecraft:silk_touch', 'levels': {'min': 1}}]},
                    }]},
                    {'type': 'minecraft:item', 'name': f'{NS}:{drop}', 'functions': [
                        {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                        {'function': 'minecraft:explosion_decay'},
                    ]},
                ],
            }],
        }],
        'random_sequence': f'{NS}:blocks/{block}',
    }


def ore_assets(name, drop, palette, deep):
    block = f'deepslate_{name}_ore' if deep else f'{name}_ore'
    texture = f'{NS}:block/{block}'
    scoped(f'assets/{NS}/blockstates/{block}.json', {'variants': {'': {'model': texture}}})
    scoped(f'assets/{NS}/models/block/{block}.json', {
        'parent': 'minecraft:block/cube_all', 'textures': {'all': texture},
    })
    scoped(f'assets/{NS}/models/item/{block}.json', {'parent': texture})
    scoped(f'data/{NS}/loot_tables/blocks/{block}.json', ore_loot(block, drop))
    rng = Random(block)
    base = 76 if deep else 121
    image = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            bedding = (y % 4 - 1) * 3 if deep else 0
            value = base + bedding + rng.randint(-11, 11)
            image.putpixel((x, y), (value, value, value + (4 if deep else 1)))
    draw = ImageDraw.Draw(image)
    shade, face, glint = palette
    for x, y in ((2, 3), (9, 2), (6, 8), (12, 10), (2, 12)):
        draw.polygon([(x, y), (x + 2, y - 1), (x + 3, y + 1), (x + 2, y + 3), (x, y + 2)], fill=shade)
        draw.line([(x + 1, y + 1), (x + 2, y), (x + 2, y + 2)], fill=face, width=2)
        draw.point((x + 1, y), fill=glint)
    save_texture('block', block, image)
    return block


def save_texture(kind, name, image):
    path = RES / f'neoforge_only/assets/{NS}/textures/{kind}/{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def icon(name, palette, shape):
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    shade, face, glint = palette
    shapes = {
        'raw': [(2, 9), (5, 4), (8, 6), (11, 3), (14, 8), (13, 12), (7, 14), (2, 12)],
        'ingot': [(1, 11), (4, 6), (12, 4), (15, 9), (12, 12), (4, 14)],
        'plate': [(1, 9), (6, 4), (14, 6), (14, 9), (9, 13), (1, 11)],
    }
    if shape == 'seal':
        draw.ellipse([2, 3, 13, 13], outline=shade, width=4)
        draw.arc([2, 3, 13, 12], 180, 340, fill=face, width=2)
        draw.arc([3, 4, 12, 11], 190, 320, fill=glint)
    else:
        corners = shapes[shape]
        draw.polygon(corners, fill=shade)
        draw.polygon([(x + (1 if x < 8 else -1), y + (1 if y < 9 else -1)) for x, y in corners], fill=face)
        draw.line(corners[:3], fill=glint)
    save_texture('item', name, image)
    scoped(f'assets/{NS}/models/item/{name}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'},
    })


def vein(mineral, minimum, maximum, count, size, blocks):
    file = f'{mineral}_ore'
    scoped(f'data/{NS}/worldgen/configured_feature/{file}.json', {
        'type': 'minecraft:ore',
        'config': {
            'size': size, 'discard_chance_on_air_exposure': 0.0,
            'targets': [
                {'target': {'predicate_type': 'minecraft:tag_match', 'tag': f'minecraft:{layer}_ore_replaceables'},
                 'state': {'Name': f'{NS}:{block}'}}
                for layer, block in zip(('stone', 'deepslate'), blocks)
            ],
        },
    })
    scoped(f'data/{NS}/worldgen/placed_feature/{file}.json', {
        'feature': f'{NS}:{file}', 'placement': [
            {'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {
                'type': 'minecraft:trapezoid',
                'min_inclusive': {'absolute': minimum}, 'max_inclusive': {'absolute': maximum},
            }},
            {'type': 'minecraft:biome'},
        ],
    })
    # The existing shared resource converter maps the Forge namespace to NeoForge.
    scoped(f'data/{NS}/forge/biome_modifier/{file}.json', {
        'type': 'forge:add_features', 'biomes': '#minecraft:is_overworld',
        'features': f'{NS}:{file}', 'step': 'underground_ores',
    })


def tag(namespace, location, values):
    scoped(f'data/{namespace}/tags/{location}.json', {'replace': False, 'values': values})


def common_mining_tag(location, blocks):
    path = f'data/minecraft/tags/blocks/{location}.json'
    data = json.loads((RES / path).read_text('utf-8'))
    for block in blocks:
        # Forge loads the common tag even though these blocks are NeoForge-only.
        entry = {'id': f'{NS}:{block}', 'required': False}
        if entry not in data['values']:
            data['values'].append(entry)
    write(path, data)


def metallurgy(element, blocks, raw):
    tag(NS, f'items/smeltable_{element}', [f'{NS}:{name}' for name in (raw, *blocks)])
    for method, duration in (('smelting', 200), ('blasting', 100)):
        name = f'{element}_ingot_from_{method}'
        scoped(f'data/{NS}/recipes/{name}.json', {
            'type': f'minecraft:{method}', 'category': 'misc', 'group': f'{element}_ingot',
            'cookingtime': duration, 'experience': 0.7,
            'ingredient': {'tag': f'{NS}:smeltable_{element}'}, 'result': {'item': f'{NS}:{element}_ingot'},
        })
        recipe_unlock(name, f'{NS}:{raw}')
    tag('c', f'items/ingots/{element}', [f'{NS}:{element}_ingot'])
    tag('c', f'items/raw_materials/{element}', [f'{NS}:{raw}'])
    tag('c', f'items/plates/{element}', [f'{NS}:{element}_plate'])


def seal_recipes():
    scoped(f'data/{NS}/recipes/sulfur_rubber_mix.json', {
        'type': 'minecraft:crafting_shapeless',
        'ingredients': [{'item': f'{NS}:sulfur'}, {'item': 'minecraft:slime_ball'}],
        'result': {'item': f'{NS}:sulfur_rubber_mix', 'count': 2},
    })
    recipe_unlock('sulfur_rubber_mix', f'{NS}:sulfur')
    scoped(f'data/{NS}/recipes/tank_from_seals.json', {
        'type': 'minecraft:crafting_shaped', 'pattern': ['GSG', 'C C', ' S '],
        'key': {'G': {'item': 'minecraft:glass'}, 'S': {'item': f'{NS}:vulcanized_seal'}, 'C': {'item': f'{NS}:casing'}},
        'result': {'item': f'{NS}:tank', 'count': 2},
    })
    recipe_unlock('tank_from_seals', f'{NS}:vulcanized_seal')


def languages():
    names = {
        'block.cassiterite_ore': ('Cassiterite Ore', '锡石矿'),
        'block.deepslate_cassiterite_ore': ('Deepslate Cassiterite Ore', '深层锡石矿'),
        'block.sphalerite_ore': ('Sphalerite Ore', '闪锌矿'),
        'block.deepslate_sphalerite_ore': ('Deepslate Sphalerite Ore', '深层闪锌矿'),
        'block.sulfur_ore': ('Native Sulfur Deposit', '自然硫矿'),
        'block.deepslate_sulfur_ore': ('Deepslate Native Sulfur Deposit', '深层自然硫矿'),
        'item.raw_tin': ('Raw Tin Ore', '粗锡矿'),
        'item.raw_zinc': ('Raw Zinc Ore', '粗锌矿'),
        'item.tin_ingot': ('Tin Ingot', '锡锭'),
        'item.zinc_ingot': ('Zinc Ingot', '锌锭'),
        'item.tin_plate': ('Tin Plate', '锡板'),
        'item.zinc_plate': ('Zinc Plate', '锌板'),
        'item.sulfur': ('Sulfur', '硫磺'),
        'item.sulfur_rubber_mix': ('Sulfur Rubber Mix', '含硫胶坯'),
        'item.vulcanized_seal': ('Vulcanized Seal', '硫化密封圈'),
        'material.cassiterite': ('Cassiterite · SnO₂', '锡石 · SnO₂'),
        'material.sphalerite': ('Sphalerite · ZnS', '闪锌矿 · ZnS'),
        'material.tin': ('Tin · Sn · 50', '锡 · Sn · 50'),
        'material.zinc': ('Zinc · Zn · 30', '锌 · Zn · 30'),
        'material.sulfur': ('Sulfur · S · 16', '硫 · S · 16'),
    }
    entries = {}
    for key, values in names.items():
        category, name = key.split('.')
        entries[f'{category}.{NS}.{name}'] = values
    entries[f'chemistry.{NS}.experiment.seal_vulcanization'] = ('Seal Vulcanization', '密封圈硫化')
    entries[f'guide.{NS}.minerals.title'] = ('Minerals and Elements', '矿物与元素')
    entries[f'guide.{NS}.minerals.body'] = (
        'Overworld veins. Cassiterite (SnO₂) Y -24 to 64, stone pick. Sphalerite (ZnS) Y -56 to 24, iron pick. Native sulfur (S) Y -40 to 48, stone pick. Smelt and press tin and zinc for pumps and motors. Cure sulfur and slime mix in a hot plate with an empty vessel. Use the seal on a pump to prevent oxidation. Replace a worn head to repair it.',
        '主世界矿脉。锡石（SnO₂）Y -24 至 64，用石镐。闪锌矿（ZnS）Y -56 至 24，用铁镐。自然硫（S）Y -40 至 48，用石镐。锡板可做计量泵，锌板可做电机。硫磺与黏液球制成胶坯，放空器皿，在加热台硫化。用密封圈右键计量泵可以防氧化，用精密泵头维修。',
    )
    for index, language in enumerate(('en_us', 'zh_cn')):
        path = f'assets/{NS}/lang/{language}.json'
        data = json.loads((RES / path).read_text('utf-8'))
        data.update({key: values[index] for key, values in entries.items()})
        write(path, data)


def main():
    all_blocks, stone_tier = [], []
    for name, element, low, high, count, size, raw, palette in MINERALS:
        blocks = [ore_assets(name, raw, palette, deep) for deep in (False, True)]
        all_blocks.extend(blocks)
        if element == 'zinc':
            tag('minecraft', 'blocks/needs_iron_tool', [f'{NS}:{block}' for block in blocks])
        else:
            stone_tier.extend(blocks)
        for kind in ('blocks', 'items'):
            tag('c', f'{kind}/ores/{element}', [f'{NS}:{block}' for block in blocks])
        vein(name, low, high, count, size, blocks)
        icon(raw, palette, 'raw')
        if element != 'sulfur':
            metallurgy(element, blocks, raw)
            for shape in ('ingot', 'plate'):
                icon(f'{element}_{shape}', palette, shape)
    common_mining_tag('mineable/pickaxe', all_blocks)
    common_mining_tag('needs_stone_tool', stone_tier)
    tag(NS, 'items/motor_plates', [f'{NS}:iron_plate', f'{NS}:zinc_plate'])
    tag(NS, 'items/pump_plates', [f'{NS}:copper_plate', f'{NS}:tin_plate'])
    seal_recipes()
    icon('sulfur_rubber_mix', ('#31352a', '#67754d', '#b9bb65'), 'raw')
    icon('vulcanized_seal', ('#21262b', '#4a4e51', '#9b8455'), 'seal')
    languages()


if __name__ == '__main__':
    main()
