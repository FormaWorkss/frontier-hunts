#!/usr/bin/env python3
"""[survival] Generates every JSON resource of Frontier Survival: item/block models, blockstates, loot tables, recipes,
tags (own files + merge fragments for shared c: tags), damage type, compostables, the default nutrition tables and the
en_us lang fragment.

usage: python3 tools/survival/data_gen.py <repo>
"""
import json, os, sys

R = sys.argv[1] if len(sys.argv) > 1 else '.'
P = os.path.join(R, 'patch')
NS = 'frontierhunts'


def w(path, obj):
    full = os.path.join(P, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


ITEMS = ['game_meat', 'cooked_game', 'bear_meat', 'cooked_bear_meat', 'wild_fowl', 'cooked_wild_fowl', 'organ_meat', 'cooked_organ_meat',
         'game_fat', 'tallow', 'jerky', 'pemmican', 'salt', 'spoiled_meat', 'heavy_hide', 'fur_pelt', 'bear_pelt', 'tanned_heavy_hide',
         'tanned_fur', 'bear_fur', 'fur_lining', 'fur_mittens', 'fur_hat', 'buckskin_coat', 'bear_fur_coat', 'hide_robe', 'buckskin_leggings',
         'fur_mukluks', 'drying_rack', 'hide_bedroll']

# ------------------------------------------------------------------------------------------------ item models
for i in ITEMS:
    w(f'assets/{NS}/models/item/{i}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{i}'}})

# ------------------------------------------------------------------------------------------------ hide bedroll
BR = f'{NS}:block/survival/'


def el(fr, to, tex, faces='all', uv=None):
    f = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        t = tex[d] if isinstance(tex, dict) else tex
        if t is None:
            continue
        f[d] = {'texture': t}
        if d == 'down' and fr[1] == 0:
            f[d]['cullface'] = 'down'
    return {'from': fr, 'to': to, 'faces': f}


bed_tex = {'particle': BR + 'bedroll_fur', 'fur': BR + 'bedroll_fur', 'hide': BR + 'bedroll_hide', 'boughs': BR + 'bedroll_boughs'}
boughs = el([0, 0, 0], [16, 3, 16], '#boughs')
blanket_head = el([1, 3, 4], [15, 9, 16], {'north': '#hide', 'south': None, 'east': '#hide', 'west': '#hide', 'up': '#fur', 'down': None})
pillow = el([2, 3, 0.5], [14, 10, 4.5], '#hide')
pillow_fur = el([2.5, 9.6, 1], [13.5, 10.2, 4], {'north': None, 'south': None, 'east': None, 'west': None, 'up': '#fur', 'down': None})
blanket_foot = el([1, 3, 0], [15, 9, 15], {'north': None, 'south': '#hide', 'east': '#hide', 'west': '#hide', 'up': '#fur', 'down': None})
fold = el([1.5, 3, 15], [14.5, 8, 15.8], '#hide')
strap = el([0.8, 3, 9], [15.2, 9.3, 10.2], {'north': '#hide', 'south': '#hide', 'east': '#hide', 'west': '#hide', 'up': '#hide', 'down': None})
w(f'assets/{NS}/models/block/survival/hide_bedroll_head.json', {'parent': 'minecraft:block/block', 'textures': bed_tex,
                                                              'elements': [boughs, blanket_head, pillow, pillow_fur]})
w(f'assets/{NS}/models/block/survival/hide_bedroll_foot.json', {'parent': 'minecraft:block/block', 'textures': bed_tex,
                                                              'elements': [boughs, blanket_foot, fold, strap]})
rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
variants = {}
for f, r in rot.items():
    for part in ('head', 'foot'):
        v = {'model': f'{NS}:block/survival/hide_bedroll_{part}'}
        if r:
            v['y'] = r
        variants[f'facing={f},part={part}'] = v
w(f'assets/{NS}/blockstates/hide_bedroll.json', {'variants': variants})

# ------------------------------------------------------------------------------------------------ drying rack (bar along X)
LOG = 'minecraft:block/stripped_spruce_log'
frame = {'parent': 'minecraft:block/block', 'textures': {'particle': LOG, 'wood': LOG, 'end': 'minecraft:block/stripped_spruce_log_top'},
         'elements': [
             el([0, 0, 7], [2, 15, 9], {'north': '#wood', 'south': '#wood', 'east': '#wood', 'west': '#wood', 'up': '#end', 'down': '#end'}),
             el([14, 0, 7], [16, 15, 9], {'north': '#wood', 'south': '#wood', 'east': '#wood', 'west': '#wood', 'up': '#end', 'down': '#end'}),
             el([0, 0, 4.5], [2, 1.5, 11.5], '#wood'),
             el([14, 0, 4.5], [16, 1.5, 11.5], '#wood'),
             el([0, 12.5, 7.25], [16, 14, 8.75], {'north': '#wood', 'south': '#wood', 'east': '#end', 'west': '#end', 'up': '#wood', 'down': '#wood'}),
         ]}
w(f'assets/{NS}/models/block/survival/drying_rack.json', frame)
STRIPS = [(3.5, 8.0), (6.5, 6.5), (9.5, 8.5), (12.5, 6.0)]
for i, (cx, ln) in enumerate(STRIPS, 1):
    for stage, tex in enumerate(('meat_strip_raw', 'meat_strip_drying', 'meat_strip_dry')):
        shrink = [0, 0.4, 0.9][stage]   # strips shrivel as they dry
        L = ln - shrink * 2
        m = {'parent': 'minecraft:block/block', 'textures': {'particle': BR + tex, 'm': BR + tex},
             'elements': [
                 el([cx - 1 + shrink * 0.2, 12.5 - L, 7.55], [cx + 1 - shrink * 0.2, 12.5, 7.95], '#m'),
                 el([cx - 1 + shrink * 0.2, 13.0 - L * 0.6, 8.05], [cx + 1 - shrink * 0.2, 12.5, 8.45], '#m'),
                 el([cx - 1, 12.3, 7.1], [cx + 1, 14.3, 8.9], '#m'),
             ]}
        w(f'assets/{NS}/models/block/survival/drying_rack_strip{i}_{stage}.json', m)
parts = []
for axis_states, y in (('east|west', 0), ('north|south', 90)):
    a = {'model': f'{NS}:block/survival/drying_rack'}
    if y:
        a['y'] = y
    parts.append({'when': {'facing': axis_states}, 'apply': a})
    for i in range(1, 5):
        loads = '|'.join(str(n) for n in range(i, 5))
        for stage in range(3):
            a = {'model': f'{NS}:block/survival/drying_rack_strip{i}_{stage}'}
            if y:
                a['y'] = y
            parts.append({'when': {'facing': axis_states, 'load': loads, 'stage': str(stage)}, 'apply': a})
w(f'assets/{NS}/blockstates/drying_rack.json', {'multipart': parts})

# ------------------------------------------------------------------------------------------------ loot tables
w(f'data/{NS}/loot_table/blocks/drying_rack.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
    {'type': 'minecraft:item', 'name': f'{NS}:drying_rack'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
w(f'data/{NS}/loot_table/blocks/hide_bedroll.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
    {'type': 'minecraft:item', 'name': f'{NS}:hide_bedroll', 'conditions': [
        {'condition': 'minecraft:block_state_property', 'block': f'{NS}:hide_bedroll', 'properties': {'part': 'head'}}]}],
    'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

# ------------------------------------------------------------------------------------------------ recipes
REC = f'data/{NS}/recipe/survival/'


def item(i):
    return {'item': i if ':' in i else f'{NS}:{i}'}


def cook(raw, cooked, xp=0.35):
    for t, time, suffix in (('minecraft:smelting', 200, ''), ('minecraft:smoking', 100, '_from_smoking'), ('minecraft:campfire_cooking', 600, '_from_campfire')):
        w(REC + f'{cooked}{suffix}.json', {'type': t, 'category': 'food', 'ingredient': item(raw), 'result': {'id': f'{NS}:{cooked}'},
                                          'experience': xp, 'cookingtime': time})


cook('game_meat', 'cooked_game')
cook('bear_meat', 'cooked_bear_meat')
cook('wild_fowl', 'cooked_wild_fowl')
cook('organ_meat', 'cooked_organ_meat')
cook('game_fat', 'tallow', 0.1)
WATER = {'type': 'neoforge:components', 'items': 'minecraft:potion', 'components': {'minecraft:potion_contents': {'potion': 'minecraft:water'}}}
w(REC + 'salt.json', {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': WATER, 'result': {'id': f'{NS}:salt'}, 'experience': 0.1, 'cookingtime': 200})
w(REC + 'salt_from_campfire.json', {'type': 'minecraft:campfire_cooking', 'category': 'misc', 'ingredient': WATER, 'result': {'id': f'{NS}:salt'},
                                    'experience': 0.1, 'cookingtime': 600})


def shaped(name, pattern, key, result, count=1, cat='equipment'):
    w(REC + name + '.json', {'type': 'minecraft:crafting_shaped', 'category': cat, 'pattern': pattern,
                             'key': {k: (v if isinstance(v, dict) else item(v)) for k, v in key.items()},
                             'result': {'id': f'{NS}:{result}', 'count': count}})


def shapeless(name, ings, result, count=1, cat='misc'):
    w(REC + name + '.json', {'type': 'minecraft:crafting_shapeless', 'category': cat,
                             'ingredients': [(v if isinstance(v, dict) else item(v)) for v in ings],
                             'result': {'id': f'{NS}:{result}', 'count': count}})


FURS = {'tag': f'{NS}:tanned_furs'}
HIDES = {'tag': f'{NS}:tanned_hides'}
shapeless('pemmican', ['jerky', 'jerky', 'tallow', {'tag': 'c:foods/berry'}], 'pemmican', 3, 'misc')
shapeless('fur_lining', ['tanned_fur', 'tanned_fur', 'tanned_hide'], 'fur_lining')
shapeless('fur_lining_from_bear_fur', ['bear_fur', 'tanned_hide'], 'fur_lining')
shaped('fur_mittens', ['FSF'], {'F': 'tanned_fur', 'S': 'minecraft:string'}, 'fur_mittens')
shaped('fur_hat', [' F ', 'FHF'], {'F': FURS, 'H': 'tanned_hide'}, 'fur_hat')
shaped('buckskin_coat', ['H H', 'HHH', 'HSH'], {'H': 'tanned_hide', 'S': 'minecraft:string'}, 'buckskin_coat')
shaped('buckskin_leggings', ['HSH', 'H H', 'H H'], {'H': 'tanned_hide', 'S': 'minecraft:string'}, 'buckskin_leggings')
shaped('bear_fur_coat', ['B B', 'BHB', 'HHH'], {'B': 'bear_fur', 'H': 'tanned_hide'}, 'bear_fur_coat')
shaped('hide_robe', ['TFT', 'T T'], {'T': 'tanned_heavy_hide', 'F': FURS}, 'hide_robe')
shaped('fur_mukluks', ['F F', 'H H'], {'F': FURS, 'H': HIDES}, 'fur_mukluks')
shaped('hide_bedroll', ['FFF', 'HHH'], {'F': FURS, 'H': HIDES}, 'hide_bedroll', 1, 'misc')
shaped('drying_rack', ['SSS', 'STS', 'S S'], {'S': 'minecraft:stick', 'T': 'minecraft:string'}, 'drying_rack', 1, 'misc')
w(REC + 'salt_cure.json', {'type': f'{NS}:salt_cure', 'category': 'misc'})
w(REC + 'fur_lining_sewing.json', {'type': f'{NS}:fur_lining', 'category': 'equipment'})

# ------------------------------------------------------------------------------------------------ tags
w(f'data/{NS}/tags/item/tanned_furs.json', {'values': [f'{NS}:tanned_fur', f'{NS}:bear_fur']})
w(f'data/{NS}/tags/item/tanned_hides.json', {'values': [f'{NS}:tanned_hide', f'{NS}:tanned_heavy_hide']})
M = 'patch/_merge/'


def frag(path, obj):
    full = os.path.join(R, M, path, 'survival.json')
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


frag('data/c/tags/item/foods/raw_meat.json', {'values': [f'{NS}:game_meat', f'{NS}:bear_meat', f'{NS}:wild_fowl', f'{NS}:organ_meat']})
frag('data/c/tags/item/foods/cooked_meat.json', {'values': [f'{NS}:cooked_game', f'{NS}:cooked_bear_meat', f'{NS}:cooked_wild_fowl', f'{NS}:cooked_organ_meat']})
frag('data/c/tags/item/foods.json', {'values': [f'{NS}:{i}' for i in ITEMS[:14]]})
frag('data/minecraft/tags/damage_type/bypasses_armor.json', {'values': [f'{NS}:malnutrition']})
frag('data/minecraft/tags/damage_type/no_knockback.json', {'values': [f'{NS}:malnutrition']})
frag('data/minecraft/tags/damage_type/bypasses_shield.json', {'values': [f'{NS}:malnutrition']})
frag('data/neoforge/data_maps/item/compostables.json', {'values': {f'{NS}:spoiled_meat': {'chance': 0.5}}})
w(f'data/{NS}/damage_type/malnutrition.json', {'message_id': f'{NS}.malnutrition', 'exhaustion': 0.0, 'scaling': 'never'})

# ------------------------------------------------------------------------------------------------ nutrition tables
def f(p, fa, e, src, shelf=0, raw=False, dry=False, spoiled=False):
    o = {'protein': p, 'fat': fa, 'energy': e, 'source': src}
    if shelf:
        o['shelf_days'] = shelf
    if raw:
        o['raw'] = True
    if dry:
        o['dry'] = True
    if spoiled:
        o['spoiled'] = True
    return o


vanilla = {
    'minecraft:apple': f(0.5, 0.3, 10, 'crop'), 'minecraft:baked_potato': f(2, 0.2, 16, 'crop'), 'minecraft:beetroot': f(1, 0, 5, 'crop'),
    'minecraft:beetroot_soup': f(3, 1, 14, 'crop'), 'minecraft:bread': f(4, 1, 22, 'crop'), 'minecraft:carrot': f(0.5, 0, 7, 'crop'),
    'minecraft:golden_carrot': f(1, 0, 14, 'crop'), 'minecraft:golden_apple': f(1, 0.5, 16, 'crop'),
    'minecraft:enchanted_golden_apple': f(2, 1, 30, 'crop'), 'minecraft:chorus_fruit': f(0, 0, 6, 'forage'), 'minecraft:cookie': f(0.5, 2, 6, 'crop'),
    'minecraft:dried_kelp': f(1, 0, 3, 'forage'), 'minecraft:glow_berries': f(0, 0, 4, 'forage'), 'minecraft:sweet_berries': f(0, 0, 4, 'forage'),
    'minecraft:honey_bottle': f(0, 0, 14, 'crop'), 'minecraft:melon_slice': f(0, 0, 4, 'crop'), 'minecraft:mushroom_stew': f(4, 1, 12, 'forage'),
    'minecraft:poisonous_potato': f(0, 0, 2, 'crop'), 'minecraft:potato': f(1, 0, 8, 'crop'), 'minecraft:pumpkin_pie': f(3, 6, 18, 'crop'),
    'minecraft:rabbit_stew': f(14, 3, 14, 'game'), 'minecraft:suspicious_stew': f(3, 1, 10, 'forage'), 'minecraft:cake': f(4, 10, 40, 'crop'),
    'minecraft:milk_bucket': f(6, 6, 8, 'farm'),
    'minecraft:beef': f(13, 8, 3, 'farm', 3, True), 'minecraft:cooked_beef': f(16, 10, 4, 'farm', 5),
    'minecraft:porkchop': f(11, 12, 2, 'farm', 3, True), 'minecraft:cooked_porkchop': f(14, 14, 3, 'farm', 5),
    'minecraft:mutton': f(10, 9, 2, 'farm', 3, True), 'minecraft:cooked_mutton': f(12, 11, 3, 'farm', 5),
    'minecraft:chicken': f(10, 3, 1, 'farm', 2, True), 'minecraft:cooked_chicken': f(12, 4, 2, 'farm', 4),
    'minecraft:rabbit': f(12, 1, 1, 'game', 2, True, True), 'minecraft:cooked_rabbit': f(15, 1, 2, 'game', 4),
    'minecraft:cod': f(9, 1, 0, 'fish', 2, True), 'minecraft:cooked_cod': f(11, 2, 1, 'fish', 3),
    'minecraft:salmon': f(10, 6, 0, 'fish', 2, True), 'minecraft:cooked_salmon': f(12, 8, 1, 'fish', 3),
    'minecraft:tropical_fish': f(6, 1, 0, 'fish', 2, True), 'minecraft:pufferfish': f(4, 1, 0, 'fish'),
    'minecraft:rotten_flesh': f(4, 1, 1, 'farm', 0, False, False, True), 'minecraft:spider_eye': f(2, 0, 1, 'forage'),
}
mod = {
    'venison': f(16, 4, 4, 'game', 3, True, True), 'cooked_venison': f(22, 6, 8, 'game', 5),
    'venison_quarter': f(18, 5, 4, 'game', 4, True), 'aged_venison': f(20, 6, 5, 'game', 6, True),
    'backstrap': f(20, 4, 4, 'game', 3, True, True), 'cooked_backstrap': f(28, 6, 8, 'game', 5),
    'game_meat': f(16, 6, 4, 'game', 3, True, True), 'cooked_game': f(22, 9, 8, 'game', 5),
    'bear_meat': f(14, 16, 4, 'game', 3, True, True), 'cooked_bear_meat': f(18, 20, 8, 'game', 5),
    'wild_fowl': f(11, 3, 2, 'game', 2, True), 'cooked_wild_fowl': f(14, 4, 4, 'game', 4),
    'organ_meat': f(14, 6, 6, 'game', 1.5, True), 'cooked_organ_meat': f(18, 7, 8, 'game', 3),
    'game_fat': f(2, 22, 6, 'game', 6), 'tallow': f(0, 30, 10, 'game', 60),
    'jerky': f(18, 3, 6, 'game', 40), 'pemmican': f(16, 22, 16, 'game'),
    'spoiled_meat': f(4, 1, 1, 'game', 0, False, False, True),
}
w(f'data/{NS}/survival/foods/vanilla.json', {'entries': vanilla})
w(f'data/{NS}/survival/foods/frontierhunts.json', {'entries': {f'{NS}:{k}': v for k, v in mod.items()}})
w(f'data/{NS}/survival/foods/common_tags.json', {'entries': {
    '#c:foods/raw_meat': f(12, 8, 2, 'farm', 3, True), '#c:foods/cooked_meat': f(15, 10, 3, 'farm', 5),
    '#c:foods/raw_fish': f(9, 3, 0, 'fish', 2, True), '#c:foods/cooked_fish': f(12, 4, 1, 'fish', 3),
    '#c:foods/berry': f(0, 0, 4, 'forage'), '#c:foods/fruit': f(0.5, 0.2, 9, 'crop'), '#c:foods/vegetable': f(1, 0, 7, 'crop'),
    '#c:foods/bread': f(4, 1, 20, 'crop'), '#c:foods/cookie': f(0.5, 2, 6, 'crop'), '#c:foods/soup': f(4, 2, 12, 'crop'),
    '#c:foods/pie': f(3, 6, 18, 'crop'), '#c:foods/candy': f(0, 1, 10, 'crop'),
}})


# ------------------------------------------------------------------------------------------------ lang (en_us fragment)
L = {
    'item.frontierhunts.game_meat': 'Raw Wild Game',
    'item.frontierhunts.cooked_game': 'Roast Wild Game',
    'item.frontierhunts.bear_meat': 'Raw Bear Meat',
    'item.frontierhunts.cooked_bear_meat': 'Roast Bear',
    'item.frontierhunts.wild_fowl': 'Raw Wild Fowl',
    'item.frontierhunts.cooked_wild_fowl': 'Roast Wild Fowl',
    'item.frontierhunts.organ_meat': 'Heart & Liver',
    'item.frontierhunts.cooked_organ_meat': 'Seared Heart & Liver',
    'item.frontierhunts.game_fat': 'Fat Trimmings',
    'item.frontierhunts.tallow': 'Rendered Tallow',
    'item.frontierhunts.jerky': 'Jerky',
    'item.frontierhunts.pemmican': 'Pemmican',
    'item.frontierhunts.salt': 'Salt',
    'item.frontierhunts.spoiled_meat': 'Spoiled Meat',
    'item.frontierhunts.heavy_hide': 'Heavy Hide',
    'item.frontierhunts.fur_pelt': 'Fur Pelt',
    'item.frontierhunts.bear_pelt': 'Bear Pelt',
    'item.frontierhunts.tanned_heavy_hide': 'Tanned Heavy Hide',
    'item.frontierhunts.tanned_fur': 'Tanned Fur',
    'item.frontierhunts.bear_fur': 'Tanned Bear Fur',
    'item.frontierhunts.fur_lining': 'Fur Lining',
    'item.frontierhunts.fur_mittens': 'Fur Mittens',
    'item.frontierhunts.fur_hat': 'Fur Trapper Hat',
    'item.frontierhunts.buckskin_coat': 'Buckskin Coat',
    'item.frontierhunts.bear_fur_coat': 'Bear Fur Coat',
    'item.frontierhunts.hide_robe': 'Heavy Hide Robe',
    'item.frontierhunts.buckskin_leggings': 'Buckskin Leggings',
    'item.frontierhunts.fur_mukluks': 'Fur Mukluks',
    'block.frontierhunts.drying_rack': 'Drying Rack',
    'block.frontierhunts.hide_bedroll': 'Hide Bedroll',
    'item.frontierhunts.salt.note': 'Boil off a water bottle in a furnace or over a campfire. Cures meat in the crafting grid.',
    'item.frontierhunts.heavy_hide.note': 'From elk, moose and bison. Tan it on a tanning rack (bone) for robes and bedrolls.',
    'item.frontierhunts.fur_pelt.note': 'Wolf, coyote and big-cat fur. Tan it on a tanning rack.',
    'item.frontierhunts.bear_pelt.note': 'Thick and warm. Tan it on a tanning rack for two bear furs.',
    'item.frontierhunts.tanned_heavy_hide.note': 'Wind- and waterproof. Robes, mukluks, bedrolls.',
    'item.frontierhunts.tanned_fur.note': 'Hats, mittens, linings, bedrolls.',
    'item.frontierhunts.bear_fur.note': 'The warmest fur there is: bear fur coats.',
    'item.frontierhunts.fur_lining.note': 'Craft with any worn piece (camo, ghillie, coveralls, armour) to line it: +0.6 warmth.',
    'item.frontierhunts.fur_mittens.note': 'Craft with any chest piece: warmer hands, steadier aim in the cold.',
    'item.frontierhunts.jerky.note': 'Dried on a drying rack. Keeps for weeks; quick to eat.',
    'item.frontierhunts.pemmican.note': 'Jerky pounded with tallow and berries. Never spoils: the winter food.',
    'item.frontierhunts.tallow.note': 'Rendered fat. Keeps a long time; makes pemmican.',
    'item.frontierhunts.game_fat.note': 'Fat game carries fat in the fall. Render it into tallow.',
    'item.frontierhunts.spoiled_meat.note': 'Gone off. Eating it makes you sick. Composts.',
    'item.frontierhunts.organ_meat.note': 'Rich and spoils fast. Eat it at the kill or cook it.',
    'survival.frontierhunts.garment.fur_hat': 'Trapper hat with ear flaps',
    'survival.frontierhunts.garment.buckskin_coat': 'Fringed buckskin coat, beaded cuffs',
    'survival.frontierhunts.garment.bear_fur_coat': 'Heavy bear fur coat with a ruff',
    'survival.frontierhunts.garment.hide_robe': 'Elk or bison robe: blocks the wind',
    'survival.frontierhunts.garment.buckskin_leggings': 'Fringed buckskin leggings',
    'survival.frontierhunts.garment.fur_mukluks': 'Fur-cuffed hide boots, beadwork band',
    'survival.frontierhunts.tooltip.warmth': 'Warmth %s (%s)',
    'survival.frontierhunts.tooltip.weather': 'Windproof %s%% · Water-resistant %s%%',
    'survival.frontierhunts.tooltip.lined': 'Fur-lined',
    'survival.frontierhunts.tooltip.mittens': 'Fur mittens sewn on',
    'survival.frontierhunts.tooltip.protein': 'Protein %s',
    'survival.frontierhunts.tooltip.fat': 'Fat %s',
    'survival.frontierhunts.tooltip.energy': 'Energy %s',
    'survival.frontierhunts.source.game': 'Wild game',
    'survival.frontierhunts.source.fish': 'Fish',
    'survival.frontierhunts.source.farm': 'Farm food',
    'survival.frontierhunts.source.crop': 'Crops & baking',
    'survival.frontierhunts.source.forage': 'Foraged',
    'survival.frontierhunts.fresh.fresh': 'Fresh · keeps %s',
    'survival.frontierhunts.fresh.good': 'Good · %s left',
    'survival.frontierhunts.fresh.old': 'Getting old · %s left',
    'survival.frontierhunts.fresh.spoiling': 'Spoiling · %s left',
    'survival.frontierhunts.fresh.spoiled': 'Spoiled',
    'survival.frontierhunts.fresh.sick': 'Spoiled · it will make you sick',
    'survival.frontierhunts.cure.smoked': ' · smoked',
    'survival.frontierhunts.cure.salted': ' · salt-cured',
    'survival.frontierhunts.store.1': ' · in the heat',
    'survival.frontierhunts.store.2': ' · kept cool',
    'survival.frontierhunts.store.3': ' · kept cold',
    'survival.frontierhunts.store.4': ' · frozen',
    'survival.frontierhunts.time.days': '%s days',
    'survival.frontierhunts.time.day': 'a day',
    'survival.frontierhunts.time.hours': '%s h',
    'survival.frontierhunts.time.soon': 'under an hour',
    'survival.frontierhunts.msg.spoiled': 'Some of your meat has spoiled. Smoke, dry, salt or keep it cold.',
    'survival.frontierhunts.msg.spoiled_store': 'Some of the meat in here has spoiled.',
    'survival.frontierhunts.msg.freezing': 'You are freezing! Get to a fire or shelter now.',
    'survival.frontierhunts.msg.hypothermia': 'Hypothermia: your hands are numb and you are slowing down.',
    'survival.frontierhunts.msg.shiver': 'You are shivering. Find fire, shelter or warmer hides.',
    'survival.frontierhunts.msg.starving': 'You are wasting away. Eat meat and fat.',
    'survival.frontierhunts.msg.protein_empty': 'No protein left: you are weak. You need meat.',
    'survival.frontierhunts.msg.fat_empty': 'No fat left: you burn through food and feel the cold. Eat fat or fatty game.',
    'survival.frontierhunts.msg.energy_empty': 'Exhausted: no energy left. Eat something filling.',
    'survival.frontierhunts.msg.heatstroke': 'You are overheating. Shed layers, find shade or water.',
    'survival.frontierhunts.msg.meat_low': 'You are craving meat and fat.',
    'survival.frontierhunts.msg.energy_low': 'You are getting tired and hungry.',
    'survival.frontierhunts.msg.sick': 'That meat was bad. You feel sick.',
    'survival.frontierhunts.msg.queasy': 'That tasted off...',
    'survival.frontierhunts.msg.cold_night': 'You slept cold and woke up chilled to the bone. A fire, a roof or a hide bedroll would have helped.',
    'survival.frontierhunts.harvest.prime': 'Prime condition: fall-fat, a heavy carcass and %s fat trimmings.',
    'survival.frontierhunts.harvest.good': 'Good condition: %s fat trimmings.',
    'survival.frontierhunts.harvest.thin': 'Thin: little meat on it, %s fat trimmings.',
    'survival.frontierhunts.harvest.lean': 'Lean late-winter animal: little meat and no fat (%s).',
    'survival.frontierhunts.season.0': 'Fall: game is at its fattest. Stock up now: smoke, dry and cure meat, render fat, make pemmican for the winter.',
    'survival.frontierhunts.season.1': 'Early winter: animals are still in fair shape. Keep meat frozen outdoors or in an ice cellar.',
    'survival.frontierhunts.season.2': 'Late winter: the lean time. Herds are thin and animals carry no fat. Live off what you stored.',
    'survival.frontierhunts.season.3': 'Spring: the herds recover. Animals are still lean; fish and forage help.',
    'survival.frontierhunts.season.4': 'Summer: animals fill out. Meat spoils fast in the heat: eat fresh or dry it.',
    'survival.frontierhunts.rack.hung': 'Hung · %s/4 · %s',
    'survival.frontierhunts.rack.full': 'The rack is full.',
    'survival.frontierhunts.rack.empty': 'Hang raw game meat here to dry it into jerky.',
    'survival.frontierhunts.rack.status': 'Drying rack · %s hanging, %s dry · %s',
    'survival.frontierhunts.rack.cond.rain': 'rain is soaking the strips',
    'survival.frontierhunts.rack.cond.good': 'drying well',
    'survival.frontierhunts.rack.cond.slow': 'drying slowly',
    'survival.frontierhunts.rack.cond.poor': 'barely drying: needs sun, wind or a fire',
    'survival.frontierhunts.bedroll.unnatural': 'You cannot rest here.',
    'death.attack.frontierhunts.malnutrition': '%1$s wasted away for want of good food',
    'death.attack.frontierhunts.malnutrition.player': '%1$s wasted away while fleeing %2$s',
    'guide.frontierhunts.tip.hunger.title': 'Hungry for meat',
    'guide.frontierhunts.tip.hunger.body': 'Your body needs protein, fat and energy (bars above hunger). Wild game gives the most; farm food and bread are weaker. Eat game to earn Hunter\'s Vigor.',
    'guide.frontierhunts.tip.cold.title': 'You are getting cold',
    'guide.frontierhunts.tip.cold.body': 'Get out of the wind, dry off and sit by a fire. Tan hides and furs into coats, hats and mukluks; a fur lining fits under your camo.',
    'guide.frontierhunts.tip.spoiled.title': 'Meat spoils',
    'guide.frontierhunts.tip.spoiled.body': 'Fresh meat keeps a few days. Smoke it in the smokehouse, dry jerky on a drying rack, cure it with salt, or keep it in ice and snow.',
    'guide.frontierhunts.tip.stockup.title': 'Stock up for winter',
    'guide.frontierhunts.tip.stockup.body': 'Fall game is fattest. Late winter is lean: herds thin out and animals carry no fat. Put up jerky, tallow and pemmican now.',
}
full = os.path.join(R, M, 'assets/frontierhunts/lang/en_us.json', 'survival.json')
os.makedirs(os.path.dirname(full), exist_ok=True)
with open(full, 'w', encoding='utf-8') as fh:
    json.dump(L, fh, indent=2, ensure_ascii=False)
    fh.write('\n')

print('ok', len(L), 'lang keys')
