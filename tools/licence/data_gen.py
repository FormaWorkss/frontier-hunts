#!/usr/bin/env python3
"""[licence] Data for licences and camp cooking: recipes, survival food values, loot table, block tags, blockstate and
item models, the lang fragment and the sounds fragment.

usage: python3 tools/licence/data_gen.py <repo>
"""
import json, os, sys

R = sys.argv[1] if len(sys.argv) > 1 else '.'
P = os.path.join(R, 'patch')
NS = 'frontierhunts'


def w(path, obj):
    p = os.path.join(P, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def I(x):
    if isinstance(x, list):
        return [I(i) for i in x]
    return {'tag': x[1:]} if x.startswith('#') else {'item': x if ':' in x else 'minecraft:' + x}


MUSH = ['brown_mushroom', 'red_mushroom']
FAT = ['frontierhunts:game_fat', 'frontierhunts:tallow']
FISH = ['cooked_cod', 'cooked_salmon']

# ------------------------------------------------------------------------------------------------ Dutch oven recipes
# id: (ingredients, bowls, count, cookingtime ticks)
OVEN = {
    'venison_stew': (['frontierhunts:venison', 'frontierhunts:venison', 'carrot', 'potato', MUSH], 3, 3, 800),
    'venison_chili': (['frontierhunts:venison', 'frontierhunts:venison', 'beetroot', 'beetroot', 'cocoa_beans'], 3, 3, 800),
    'bear_pot_roast': (['frontierhunts:bear_meat', 'frontierhunts:bear_meat', 'potato', 'carrot', 'frontierhunts:salt'], 0, 3, 1000),
    'backstrap_mushrooms': (['frontierhunts:backstrap', MUSH, MUSH, FAT], 0, 2, 500),
    'fowl_berry_roast': (['frontierhunts:wild_fowl', 'frontierhunts:wild_fowl', 'sweet_berries', 'sweet_berries'], 0, 2, 600),
    'fowl_wild_rice': (['frontierhunts:wild_fowl', 'wheat', 'wheat', MUSH], 2, 2, 700),
    'heart_liver_fry': (['frontierhunts:organ_meat', FAT, MUSH], 0, 2, 400),
    'hunters_breakfast': ([['frontierhunts:venison', 'frontierhunts:game_meat'], 'egg', 'potato', FAT], 0, 2, 600),
    'fish_chowder': ([FISH, FISH, 'potato', FAT], 3, 3, 700),
    'camp_bannock': (['wheat', 'wheat', 'wheat', FAT, 'frontierhunts:salt'], 0, 4, 400),
}

# survival food table: protein, fat, energy, source, shelf days (0 = never)
FOOD = {
    'venison_stew': (18, 8, 20, 'game', 2),
    'venison_chili': (18, 9, 22, 'game', 2),
    'bear_pot_roast': (20, 24, 14, 'game', 4),
    'backstrap_mushrooms': (26, 8, 12, 'game', 4),
    'fowl_berry_roast': (16, 6, 14, 'game', 4),
    'fowl_wild_rice': (14, 5, 24, 'game', 2),
    'heart_liver_fry': (20, 10, 10, 'game', 3),
    'hunters_breakfast': (20, 16, 26, 'game', 3),
    'fish_chowder': (14, 10, 18, 'fish', 1.5),
    'camp_bannock': (3, 8, 24, 'crop', 6),
    'honey_pemmican': (14, 20, 22, 'game', 0),
    'smoked_game_sausage': (10, 10, 6, 'game', 15),
}


def recipes():
    for k, (ings, bowls, count, t) in OVEN.items():
        j = {'type': f'{NS}:camp_cooking', 'group': 'camp_cooking', 'ingredients': [I(i) for i in ings],
             'result': {'id': f'{NS}:{k}', 'count': count}, 'cookingtime': t}
        if bowls:
            j['bowls'] = bowls
        w(f'data/{NS}/recipe/campcook/{k}.json', j)
    # the oven: five iron ingots and a nugget knob (vanilla cauldron is 7 iron in a U)
    w(f'data/{NS}/recipe/dutch_oven.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': [' N ', 'I I', 'III'],
        'key': {'N': I('iron_nugget'), 'I': I('iron_ingot')},
        'result': {'id': f'{NS}:dutch_oven', 'count': 1}})
    # sausage: grind and case wild game with fat and salt, then smoke or roast it
    w(f'data/{NS}/recipe/campcook/raw_game_sausage.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [I('frontierhunts:game_meat'), I('frontierhunts:game_meat'), I(FAT), I('frontierhunts:salt')],
        'result': {'id': f'{NS}:raw_game_sausage', 'count': 4}})
    w(f'data/{NS}/recipe/campcook/smoked_game_sausage.json', {
        'type': 'minecraft:smoking', 'category': 'food', 'ingredient': I('frontierhunts:raw_game_sausage'),
        'result': {'id': f'{NS}:smoked_game_sausage'}, 'experience': 0.35, 'cookingtime': 200})
    w(f'data/{NS}/recipe/campcook/campfire_game_sausage.json', {
        'type': 'minecraft:campfire_cooking', 'category': 'food', 'ingredient': I('frontierhunts:raw_game_sausage'),
        'result': {'id': f'{NS}:smoked_game_sausage'}, 'experience': 0.35, 'cookingtime': 600})
    w(f'data/{NS}/recipe/campcook/honey_pemmican.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [I('frontierhunts:jerky'), I('frontierhunts:jerky'), I('frontierhunts:tallow'), I('honey_bottle')],
        'result': {'id': f'{NS}:honey_pemmican', 'count': 3}})


def food():
    e = {}
    for k, (p, f, en, src, shelf) in FOOD.items():
        d = {'protein': p, 'fat': f, 'energy': en, 'source': src}
        if shelf:
            d['shelf_days'] = shelf
        e[f'{NS}:{k}'] = d
    e[f'{NS}:raw_game_sausage'] = {'protein': 8, 'fat': 8, 'energy': 3, 'source': 'game', 'shelf_days': 2, 'raw': True}
    w(f'data/{NS}/survival/foods/camp_cooking.json', {'entries': e})


def blocks():
    w(f'data/{NS}/loot_table/blocks/dutch_oven.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:dutch_oven'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': f'{NS}:blocks/dutch_oven'})
    w('_merge/data/minecraft/tags/block/mineable/pickaxe.json/licence.json', {'values': [f'{NS}:dutch_oven']})
    variants = {}
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for f, y in rot.items():
        for grate in ('false', 'true'):
            for lit in ('false', 'true'):
                m = f'{NS}:block/dutch_oven_grate' if grate == 'true' else (f'{NS}:block/dutch_oven_lit' if lit == 'true' else f'{NS}:block/dutch_oven')
                v = {'model': m}
                if y:
                    v['y'] = y
                variants[f'facing={f},grate={grate},lit={lit}'] = v
    w(f'assets/{NS}/blockstates/dutch_oven.json', {'variants': variants})
    w(f'assets/{NS}/models/item/dutch_oven.json', {'parent': f'{NS}:block/dutch_oven'})


def item_models():
    flat = ['hunting_licence', 'upland_stamp', 'waterfowl_stamp', 'raw_game_sausage'] + list(FOOD)
    for t in ('deer', 'elk', 'moose', 'pronghorn', 'bison', 'bear', 'cat'):
        flat.append(f'{t}_tag')
    for n in flat:
        w(f'assets/{NS}/models/item/{n}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{n}'}})
    kinds = ('deer', 'elk', 'moose', 'pronghorn', 'bison', 'bear', 'cat')
    w(f'assets/{NS}/models/item/filled_tag.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/filled_tag'},
        'overrides': [{'predicate': {'custom_model_data': i + 1}, 'model': f'{NS}:item/filled_tag_{k}'} for i, k in enumerate(kinds)]})
    for k in kinds:
        w(f'assets/{NS}/models/item/filled_tag_{k}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/filled_tag_{k}'}})


def sounds():
    s = {
        'ui.licence_stamp': {'subtitle': f'subtitles.{NS}.licence_stamp', 'sounds': [f'{NS}:licence/stamp_{i}' for i in range(2)]},
        'ui.tag_punch': {'subtitle': f'subtitles.{NS}.tag_punch', 'sounds': [f'{NS}:licence/punch_{i}' for i in range(2)]},
        'ui.warden_notice': {'subtitle': f'subtitles.{NS}.warden_notice', 'sounds': [f'{NS}:licence/warden']},
        'amb_pot_simmer': {'subtitle': f'subtitles.{NS}.pot_simmer',
                           'sounds': [{'name': f'{NS}:campcook/simmer_{i}', 'attenuation_distance': 10} for i in range(3)]},
        'amb_pot_lid': {'subtitle': f'subtitles.{NS}.pot_lid', 'sounds': [{'name': f'{NS}:campcook/lid_{i}', 'attenuation_distance': 12} for i in range(2)]},
    }
    w(f'_merge/assets/{NS}/sounds.json/licence.json', s)


if __name__ == '__main__':
    recipes(); food(); blocks(); item_models(); sounds()
    print('ok')


# ------------------------------------------------------------------------------------------------ Dutch oven block models
WALL = [0, 0, 16, 9]
RIM = [0, 10, 16, 11]
LIDS = [0, 11, 16, 12]
COAL = [0, 12, 16, 14]
IRON = [0, 14, 16, 16]


def box(f, t, uv_side, uv_top=None, glow=False, faces=('north', 'south', 'east', 'west', 'up', 'down')):
    uv_top = uv_top or [2, 0, 14, 9]
    e = {'from': f, 'to': t, 'faces': {}}
    for fc in faces:
        e['faces'][fc] = {'texture': '#oven', 'uv': uv_top if fc in ('up', 'down') else uv_side}
    if glow:
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        e['shade'] = False
    return e


def pot(dy, legs):
    """A squat cast-iron pot: belly band, flanged lid with a loop knob, side lugs and a wire bail."""
    els = []
    if legs:
        for (x, z) in ((4, 4), (10.5, 4), (7.25, 11)):
            els.append(box([x, 0, z], [x + 1.5, 2, z + 1.5], WALL))
    els.append(box([3, 2 + dy, 3], [13, 3 + dy, 13], [0, 7, 16, 8]))            # rounded bottom
    els.append(box([2.25, 3 + dy, 2.25], [13.75, 7.5 + dy, 13.75], [0, 2, 16, 7]))   # belly
    els.append(box([2.75, 7.5 + dy, 2.75], [13.25, 8.5 + dy, 13.25], [0, 1, 16, 2]))  # shoulder
    els.append(box([2.25, 8.5 + dy, 2.25], [13.75, 9.25 + dy, 13.75], RIM, [0, 0, 16, 10]))  # rim
    els.append(box([3, 9.25 + dy, 3], [13, 10 + dy, 13], LIDS, [3, 0, 13, 9]))      # lid with flange
    els.append(box([7, 10 + dy, 7.5], [9, 11 + dy, 8.5], RIM, [6, 10, 10, 11]))     # knob
    for x in (1.25, 13.75):
        els.append(box([x, 6 + dy, 7.25], [x + 1, 7.5 + dy, 8.75], WALL))          # lugs
    els.append(box([1.5, 7.5 + dy, 7.75], [2, 12 + dy, 8.25], IRON))
    els.append(box([14, 7.5 + dy, 7.75], [14.5, 12 + dy, 8.25], IRON))
    els.append(box([1.5, 12 + dy, 7.75], [14.5, 12.5 + dy, 8.25], IRON, IRON))
    return els


def oven_models():
    tex = {'oven': f'{NS}:block/dutch_oven', 'particle': f'{NS}:block/dutch_oven'}
    ground = pot(0, True)
    w(f'assets/{NS}/models/block/dutch_oven.json', {'parent': 'minecraft:block/block', 'textures': tex, 'elements': ground})
    lit = pot(0, True)
    lit.append(box([3, 0, 3], [13, 0.6, 13], COAL, COAL, glow=True))
    for (x, z) in ((4.5, 4.5), (9.5, 5), (5.5, 9.5), (10, 10), (7.5, 7)):
        if (x, z) == (7.5, 7):
            continue
        lit.append(box([x, 10, z], [x + 2, 10.6, z + 2], COAL, COAL, glow=True))
    w(f'assets/{NS}/models/block/dutch_oven_lit.json', {'parent': 'minecraft:block/block', 'textures': tex, 'elements': lit})
    grate = []
    for (x, z) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        grate.append(box([x, -9, z], [x + 1, 0.5, z + 1], IRON, IRON))
    grate.append(box([1, 0, 1], [15, 1, 2], IRON, IRON))
    grate.append(box([1, 0, 14], [15, 1, 15], IRON, IRON))
    for x in (3, 7.5, 12):
        grate.append(box([x, 0.2, 2], [x + 1, 0.9, 14], IRON, IRON))
    grate += pot(-1, False)
    w(f'assets/{NS}/models/block/dutch_oven_grate.json', {'parent': 'minecraft:block/block', 'textures': tex, 'elements': grate})


oven_models()
