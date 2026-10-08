#!/usr/bin/env python3
"""[onboard2] Writes every seat asset: textures (Vanilla + Ultra), block models, blockstates, item models (with GUI
display transforms), loot tables, recipes, recipe unlocks and the mineable tag fragment.

python3 tools/onboard2/seats.py <repo>
Then: python3 tools/onboard2/seat_check.py <repo> <out>   (clearance + previews)"""
import json
import os
import sys

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
sys.path.insert(0, os.path.join(R, 'tools/onboard2'))
import seat_models as SM  # noqa: E402
import seat_textures  # noqa: E402

A = os.path.join(R, 'patch/assets/frontierhunts')
D = os.path.join(R, 'patch/data/frontierhunts')
FACINGS = (('north', 0), ('east', 90), ('south', 180), ('west', 270))
written = []


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=1)
        f.write('\n')
    written.append(os.path.relpath(path, R))


def model(name, m, display=None):
    j = {'ambientocclusion': False, 'textures': m['textures'], 'elements': m['elements']}
    if display:
        j['display'] = display
    wjson(os.path.join(A, 'models/block/seat', name + '.json'), j)
    return 'frontierhunts:block/seat/' + name


def display(gui_scale, gui_y=0.0, gui_rot=(30, 225, 0), ground=0.25, fixed=0.5, hand=0.375, fp=0.4):
    return {
        'gui': {'rotation': list(gui_rot), 'translation': [0, gui_y, 0], 'scale': [gui_scale] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [ground] * 3},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [fixed] * 3},
        'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.6] * 3},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [hand] * 3},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [hand] * 3},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [fp] * 3},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [fp] * 3},
    }


# GUI fit, tuned with tools/outfitter/gui_audit.py (slot fill ~ like a vanilla block item)
GUI = {
    'log_stump_seat': display(0.80, 3.4),
    'camp_chair': display(0.59, 0.45),
    'trail_bench': display(0.62, -0.05),
    'blind_chair': display(0.73, 1.45),
    'tower_chair': display(0.64, 0.45),
}


def blockstate(id_, variants):
    wjson(os.path.join(A, 'blockstates', id_ + '.json'), {'variants': variants})


def facing_variants(model_of, extra=''):
    v = {}
    for f, y in FACINGS:
        key = 'facing=%s%s' % (f, extra)
        m = {'model': model_of}
        if y:
            m['y'] = y
        v[key] = m
    return v


def loot(id_):
    wjson(os.path.join(D, 'loot_table/blocks', id_ + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'frontierhunts:' + id_}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': 'frontierhunts:blocks/' + id_})


def recipe(id_, pattern, key, count, unlock_item):
    k = {}
    for c, v in key.items():
        k[c] = {'tag': v[1:]} if v.startswith('#') else {'item': v}
    wjson(os.path.join(D, 'recipe', id_ + '.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': k,
        'result': {'id': 'frontierhunts:' + id_, 'count': count}})
    crit = {'items': [{'items': [unlock_item]}]} if not unlock_item.startswith('#') else {'items': [{'items': unlock_item}]}
    wjson(os.path.join(D, 'advancement/recipes/unlock', id_ + '.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'frontierhunts:' + id_}},
                     'has_material_0': {'trigger': 'minecraft:inventory_changed', 'conditions': crit}},
        'requirements': [['has_material_0', 'has_the_recipe']],
        'rewards': {'recipes': ['frontierhunts:' + id_]}})


def main():
    seat_textures.write_all(R)
    # ------------------------------------------------------------------ log stump seat
    m = model('log_stump_seat', SM.stump(), GUI['log_stump_seat'])
    blockstate('log_stump_seat', facing_variants(m))
    # ------------------------------------------------------------------ camp chair
    m = model('camp_chair', SM.camp_chair(), GUI['camp_chair'])
    blockstate('camp_chair', facing_variants(m))
    # ------------------------------------------------------------------ trail bench (joins)
    names = {(False, False): 'single', (True, False): 'left', (False, True): 'right', (True, True): 'middle'}
    v = {}
    for (l, r), nm in names.items():
        mm = model('trail_bench_' + nm, SM.trail_bench(l, r), GUI['trail_bench'] if nm == 'single' else None)
        for f, y in FACINGS:
            e = {'model': mm}
            if y:
                e['y'] = y
            v['facing=%s,left=%s,right=%s' % (f, str(l).lower(), str(r).lower())] = e
    blockstate('trail_bench', v)
    # ------------------------------------------------------------------ blind swivel chair
    full = model('blind_chair', SM.blind_chair('full'), GUI['blind_chair'])
    base = model('blind_chair_base', SM.blind_chair('base'))
    model('blind_chair_top', SM.blind_chair('top'))
    v = {}
    for f, y in FACINGS:
        for occ, mm in ((False, full), (True, base)):
            e = {'model': mm}
            if y:
                e['y'] = y
            v['facing=%s,occupied=%s' % (f, str(occ).lower())] = e
    blockstate('blind_chair', v)
    # ------------------------------------------------------------------ tower swivel chair
    v = {}
    model('tower_chair_top', SM.tower_chair('top'))
    for raised in (False, True):
        for sunk in (False, True):
            suffix = ('_raised' if raised else '') + ('_sunk' if sunk else '')
            full = model('tower_chair' + suffix, SM.tower_chair('full', raised, sunk), GUI['tower_chair'] if suffix == '_raised' else None)
            base = model('tower_chair_base' + suffix, SM.tower_chair('base', raised, sunk))
            for f, y in FACINGS:
                for occ, mm in ((False, full), (True, base)):
                    e = {'model': mm}
                    if y:
                        e['y'] = y
                    v['facing=%s,occupied=%s,raised=%s,sunk=%s' % (f, str(occ).lower(), str(raised).lower(), str(sunk).lower())] = e
    blockstate('tower_chair', v)
    # ------------------------------------------------------------------ items
    for id_, parent in (('log_stump_seat', 'log_stump_seat'), ('camp_chair', 'camp_chair'), ('trail_bench', 'trail_bench_single'),
                        ('blind_chair', 'blind_chair'), ('tower_chair', 'tower_chair_raised')):  # the item shows the tall setting
        wjson(os.path.join(A, 'models/item', id_ + '.json'), {'parent': 'frontierhunts:block/seat/' + parent})
        loot(id_)
    # ------------------------------------------------------------------ recipes (priced in tools/economy/model.py)
    recipe('log_stump_seat', ['H', 'L'], {'H': 'minecraft:leather', 'L': '#minecraft:logs'}, 1, 'minecraft:leather')
    recipe('camp_chair', ['C  ', 'CCC', 'N N'], {'C': 'frontierhunts:canvas_wall', 'N': 'minecraft:iron_nugget'}, 1, 'frontierhunts:canvas_wall')
    recipe('trail_bench', ['P  ', 'PPP', 'S S'], {'P': '#minecraft:planks', 'S': 'minecraft:stick'}, 2, 'minecraft:stick')
    recipe('blind_chair', ['W  ', 'WIW', 'N N'], {'W': '#minecraft:wool', 'I': 'minecraft:iron_ingot', 'N': 'minecraft:iron_nugget'}, 1,
           'minecraft:iron_ingot')
    recipe('tower_chair', ['W  ', 'WIW', 'NIN'], {'W': '#minecraft:wool', 'I': 'minecraft:iron_ingot', 'N': 'minecraft:iron_nugget'}, 1,
           'minecraft:iron_ingot')
    # ------------------------------------------------------------------ tag fragment (axe breaks them fastest)
    wjson(os.path.join(R, 'patch/_merge/data/minecraft/tags/block/mineable/axe.json/onboard2.json'),
          {'values': ['frontierhunts:log_stump_seat', 'frontierhunts:camp_chair', 'frontierhunts:trail_bench', 'frontierhunts:blind_chair',
                      'frontierhunts:tower_chair']})
    print(len(written), 'files')


if __name__ == '__main__':
    main()
