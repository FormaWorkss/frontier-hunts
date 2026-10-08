#!/usr/bin/env python3
"""[economy] Writes the rebalanced recipes into patch/ (overriding the base jar / earlier patch files).

usage: python3 tools/economy/gen_recipes.py <repo> [--base <orig jar>]

Every recipe keeps its id (so unlock advancements, the Handbook's live recipe cards, the workbench lists and the
recipe audit stay valid), its category and group; only pattern/key/ingredients/result count change.
Idempotent. The value of each recipe is checked by tools/economy/value_model.py on the built jar.
"""
import json, os, sys, zipfile

BASE_JAR = '/home/claude/fh/orig62.jar'


def i(x):
    return {'tag': x[1:]} if x.startswith('#') else {'item': x if ':' in x else 'minecraft:' + x}


def shaped(pattern, key, count=1):
    return {'type': 'minecraft:crafting_shaped', 'pattern': pattern, 'key': {k: i(v) for k, v in key.items()},
            'count': count}


def shapeless(items, count=1):
    return {'type': 'minecraft:crafting_shapeless', 'ingredients': [i(x) for x in items], 'count': count}


FH = 'frontierhunts:'
# id -> new recipe (why)
NEW = {
    # ---------------------------------------------------------------- first hour
    'primitive_arrow': (shaped(['F  ', ' S ', '  P'], {'F': 'flint', 'S': 'stick', 'P': 'feather'}, 4),
                        'laid corner to corner: straight down is the vanilla arrow recipe, the grid could only make one'),
    'field_arrow': (shaped(['N', 'S', 'P'], {'N': 'iron_nugget', 'S': 'stick', 'P': 'feather'}, 4),
                    'a stick shaft instead of coal'),
    'bone_point': (shapeless(['bone', 'flint'], 3), 'knapped with flint; a lone bone is vanilla bone meal'),
    'skinning_tool': (shaped([' I', 'SL'], {'I': 'iron_ingot', 'S': 'stick', 'L': 'leather'}),
                      'one iron blade: needed to field-dress the first deer'),
    'survival/hide_bedroll': (shaped(['WWW', 'HHH'], {'W': '#minecraft:wool', 'H': '#frontierhunts:tanned_hides'}),
                              'wool over hides instead of 3 pelts + 3 hides'),
    # ---------------------------------------------------------------- stations / camp
    'ammo_reloader': (shaped([' I ', 'CKC', 'LLL'], {'I': 'iron_ingot', 'C': 'copper_ingot', 'K': 'piston',
                                                    'L': '#minecraft:planks'}),
                      'a reloading press (piston); was identical to the Clothing Workbench recipe'),
    'reserve_camp_cot': (shaped(['HHH', 'SPS'], {'H': FH + 'deer_hide', 'S': 'stick', 'P': '#minecraft:planks'}),
                         'wooden frame instead of 2 iron ingots'),
    # ---------------------------------------------------------------- bows
    'expedition_recurve_bow': (shaped(['BPT', 'L T', 'BPT'], {'B': 'bone', 'P': '#minecraft:planks', 'T': 'string',
                                                             'L': 'leather'}),
                               'laminated wood limbs, bone tips, leather grip (no iron)'),
    'expedition_compound_bow': (shaped(['NIT', 'ILT', 'NIT'], {'N': 'iron_nugget', 'I': 'iron_ingot', 'T': 'string',
                                                              'L': 'leather'}),
                                'steel riser, cams (nuggets), cables'),
    'expedition_crossbow': (shaped(['III', 'THT', ' P '], {'I': 'iron_ingot', 'T': 'string', 'H': 'tripwire_hook',
                                                          'P': '#minecraft:planks'}),
                            'steel prod, trigger (tripwire hook), stock'),
    # ---------------------------------------------------------------- firearms: barrel = iron block, trigger = tripwire hook
    'expedition_lever_rifle': (shaped(['  B', 'PIV', 'PH '], {'B': 'iron_block', 'P': '#minecraft:planks',
                                                             'I': 'iron_ingot', 'V': 'lever', 'H': 'tripwire_hook'}),
                               'barrel, receiver, lever action, trigger, stock'),
    'ridgeline_rifle': (shaped(['  B', 'PII', 'PH '], {'B': 'iron_block', 'P': '#minecraft:planks', 'I': 'iron_ingot',
                                                      'H': 'tripwire_hook'}),
                        'one barrel (iron block) instead of three iron blocks + amethyst'),
    'expedition_pump_shotgun': (shaped(['  B', 'PIK', 'PH '], {'B': 'iron_block', 'P': '#minecraft:planks',
                                                              'I': 'iron_ingot', 'K': 'piston', 'H': 'tripwire_hook'}),
                                'pump action = piston'),
    'expedition_double_barrel': (shaped(['  B', ' P ', 'PH '], {'B': 'iron_block', 'P': '#minecraft:planks',
                                                               'H': 'tripwire_hook'}),
                                 'two barrels (iron block), stock, trigger'),
    'expedition_semi_auto_shotgun': (shaped(['G B', 'PKI', 'PH '], {'G': 'gold_ingot', 'B': 'iron_block',
                                                                   'P': '#minecraft:planks', 'K': 'piston',
                                                                   'I': 'iron_ingot', 'H': 'tripwire_hook'}),
                                     'gas system (piston), gold-plated action'),
    'expedition_semi_auto_rifle': (shaped(['IBB', 'PKR', 'PH '], {'I': 'iron_ingot', 'B': 'iron_block',
                                                                 'P': '#minecraft:planks', 'K': 'piston',
                                                                 'R': 'repeater', 'H': 'tripwire_hook'}),
                                   'automatic: barrel + receiver (2 iron blocks), gas piston, repeater timing'),
    'expedition_revolver': (shaped(['III', 'IHI', ' P '], {'I': 'iron_ingot', 'H': 'tripwire_hook',
                                                          'P': '#minecraft:planks'}),
                            'steel frame and cylinder, trigger, wooden grip'),
    'expedition_field_pistol': (shaped(['III', 'GHK', ' I '], {'I': 'iron_ingot', 'G': 'gold_ingot',
                                                              'H': 'tripwire_hook', 'K': 'piston'}),
                                'steel slide on a recoil spring (piston), match trigger (gold)'),
    'expedition_tranquilizer_rifle': (shaped(['  I', 'MIG', 'PH '], {'I': 'iron_ingot', 'M': 'slime_ball',
                                                                    'G': 'glass_bottle', 'P': '#minecraft:planks',
                                                                    'H': 'tripwire_hook'}),
                                      'gas dart rifle'),
    'expedition_flare_gun': (shaped(['II', 'CH'], {'I': 'iron_ingot', 'C': 'copper_ingot', 'H': 'tripwire_hook'}),
                             'no nether glowstone needed for a signal pistol'),
    'expedition_flare_round': (shapeless(['paper', 'gunpowder', 'redstone'], 8), 'red signal flare (redstone) instead of glowstone'),
    # ---------------------------------------------------------------- optics
    'expedition_binoculars': (shaped(['CIC', 'G G', ' L '], {'C': 'copper_ingot', 'I': 'iron_ingot', 'G': 'glass',
                                                            'L': 'leather'}),
                              'two copper tubes, two lenses, hinge, strap'),
    'expedition_rangefinder': (shaped(['CGC', 'IRI', ' R '], {'C': 'copper_ingot', 'G': 'glass', 'I': 'iron_ingot',
                                                             'R': 'redstone'}),
                               'laser electronics (redstone)'),
    'expedition_thermal_binoculars': (shaped(['ADA', 'RBR', ' K '], {'A': 'gold_ingot', 'D': 'diamond',
                                                                    'R': 'redstone', 'B': FH + 'binoculars',
                                                                    'K': FH + 'field_battery_pack'}),
                                      'binoculars + diamond sensor, gold, redstone, battery'),
    'night_vision_binoculars': (shaped(['ARA', 'GBG', 'ILI'], {'A': 'gold_ingot', 'R': 'redstone', 'G': 'glass',
                                                              'B': FH + 'field_battery_pack', 'I': 'iron_ingot',
                                                              'L': 'leather'}),
                                'image intensifier: gold'),
    'expedition_reflex_sight': (shaped(['IRI', 'CGC'], {'I': 'iron_ingot', 'R': 'redstone', 'C': 'copper_ingot',
                                                       'G': 'glass'}),
                                'illuminated dot (redstone)'),
    'holographic_sight': (shaped([' A ', 'IGI', ' R '], {'A': 'gold_ingot', 'I': 'iron_ingot', 'G': 'glass',
                                                        'R': 'redstone'}),
                          'laser holographic (gold); was identical to the micro red dot and the field camera'),
    'field_camera': (shaped(['CRC', 'IGI'], {'C': 'copper_ingot', 'R': 'redstone', 'I': 'iron_ingot', 'G': 'glass'}),
                     'was identical to the micro red dot'),
    'expedition_six_power_scope': (shaped([' C ', 'GIA', 'I I'], {'C': 'copper_ingot', 'G': 'glass', 'I': 'iron_ingot',
                                                                 'A': 'amethyst_shard'}),
                                   'iron tube, objective (glass), ocular (amethyst like a spyglass), turret'),
    'expedition_eight_power_scope': (shaped([' U ', 'GIA', 'IAI'], {'U': 'gold_ingot', 'G': 'glass', 'I': 'iron_ingot',
                                                                   'A': 'amethyst_shard'}),
                                     '+ gold turret, second amethyst lens'),
    'expedition_twelve_power_scope': (shaped(['DU ', 'GIA', 'IAI'], {'D': 'diamond', 'U': 'gold_ingot', 'G': 'glass',
                                                                    'I': 'iron_ingot', 'A': 'amethyst_shard'}),
                                      '+ diamond-ground lens'),
    'expedition_thermal_scope': (shaped(['RKR', 'GDA', 'UIU'], {'R': 'redstone', 'K': FH + 'field_battery_pack',
                                                               'G': 'glass', 'D': 'diamond', 'A': 'amethyst_shard',
                                                               'U': 'gold_ingot', 'I': 'iron_ingot'}),
                                 'thermal sensor (diamond), gold, battery'),
    # ---------------------------------------------------------------- attachments
    'expedition_suppressor': (shaped(['III', 'NWN', 'III'], {'I': 'iron_ingot', 'N': 'iron_nugget', 'W': '#minecraft:wool'}),
                              'steel tube and baffles'),
    'muzzle_brake': (shaped([' I ', 'INI', ' N '], {'I': 'iron_ingot', 'N': 'iron_nugget'}),
                     'all steel; was identical to the angled foregrip'),
    'expedition_extended_magazine': (shaped(['N N', 'ICI', ' I '], {'N': 'iron_nugget', 'I': 'iron_ingot',
                                                                   'C': 'copper_ingot'}),
                                     'steel box, copper spring'),
    'expedition_pistol_magazine': (shaped(['N N', 'NCN', ' I '], {'N': 'iron_nugget', 'C': 'copper_ingot',
                                                                 'I': 'iron_ingot'}),
                                   'was identical to the Ridgeline magazine'),
    'expedition_steady_stock': (shaped(['PI ', 'PIL'], {'P': '#minecraft:planks', 'I': 'iron_ingot', 'L': 'leather'}),
                                'wood, steel, leather cheek pad'),
    'expedition_bipod': (shaped([' I ', 'I I', 'N N'], {'I': 'iron_ingot', 'N': 'iron_nugget'}), 'steel legs'),
    'expedition_fishing_drag_kit': (shapeless(['iron_ingot', 'copper_ingot', 'string']), 'reel drag washers'),
    # ---------------------------------------------------------------- calls and field gear (were all leather + string + nugget)
    'expedition_deer_call': (shapeless(['note_block', 'redstone', 'copper_ingot', 'iron_ingot']),
                             'electronic caller: it pulls every deer within 64 blocks'),
    'expedition_predator_call': (shapeless(['bone', 'copper_ingot', 'string']), 'mouth call with a copper reed'),
    'expedition_scent_cover': (shapeless(['glass_bottle', 'charcoal', '#minecraft:leaves']), 'carbon and pine spray'),
    'expedition_medkit': (shapeless(['#minecraft:wool', '#minecraft:wool', 'paper', 'string', 'apple']),
                          'bandages, tape and a snack'),
    # [onebook] 'expedition_expedition_guide' retired: the Frontier Handbook is the only book item
    'expedition_bait': (shapeless(['wheat', 'wheat', 'rotten_flesh']), 'grain and scraps'),
    'expedition_bowfishing_arrow': (shapeless(['iron_nugget', 'stick', 'stick', 'string'], 8),
                                    'long shaft (2 sticks); was identical to the judo point'),
    # ---------------------------------------------------------------- vehicle
    'atv': (shaped(['KLK', 'BFP', 'K K'], {'K': 'dried_kelp_block', 'L': 'leather', 'B': 'iron_block',
                                           'F': 'blast_furnace', 'P': 'piston'}),
            'engine (blast furnace + piston), iron-block frame, tyres, seat'),
    'atv_cargo_box': (shaped(['LDL', 'NCN', 'LNL'], {'L': 'leather', 'D': 'black_dye', 'N': 'iron_nugget', 'C': 'chest'}),
                      'nugget fittings instead of 3 iron ingots'),
    'atv_can_carrier': (shaped(['N N', 'ILI', 'N N'], {'N': 'iron_nugget', 'I': 'iron_ingot', 'L': 'leather'}),
                        'a rack, not 6 iron ingots'),
    'jerry_can': (shaped([' N ', 'IRI', 'I I'], {'N': 'iron_nugget', 'I': 'iron_ingot', 'R': 'red_dye'}),
                  '4 iron ingots (a bucket is 3)'),
    'wingsuit': (shaped(['OLO', 'PTP', 'P P'], {'O': 'orange_wool', 'L': 'leather', 'P': 'phantom_membrane',
                                                'T': 'string'}),
                 'elytra-class glider: four phantom membranes'),
    # ---------------------------------------------------------------- building blocks: grid conflicts
    'reserve_roof_gable_low': (shaped(['SSS', 'P P', ' P '], {'S': FH + 'roof_shingles', 'P': FH + 'pine_planks'}, 6),
                               'six roof shapes shared one pattern'),
    'reserve_roof_ridge_high': (shaped([' S ', 'SPS', 'P P'], {'S': FH + 'roof_shingles', 'P': FH + 'pine_planks'}, 6), ''),
    'reserve_roof_ridge_low': (shaped(['S S', ' S ', 'PPP'], {'S': FH + 'roof_shingles', 'P': FH + 'pine_planks'}, 6), ''),
    'reserve_roof_slope_high': (shaped(['S  ', 'SP ', 'SPP'], {'S': FH + 'roof_shingles', 'P': FH + 'pine_planks'}, 6), ''),
    'reserve_roof_slope_low': (shaped(['  S', ' SS', 'PPP'], {'S': FH + 'roof_shingles', 'P': FH + 'pine_planks'}, 6), ''),
    'reserve_tent_ridge': (shaped([' C ', 'CSC'], {'C': FH + 'canvas_wall', 'S': 'stick'}, 6), 'three tent shapes shared one pattern'),
    'reserve_tent_gable': (shaped([' C ', 'C C', ' S '], {'C': FH + 'canvas_wall', 'S': 'stick'}, 6), ''),
    'reserve_trail_sign': (shaped([' P ', 'PPP', ' S '], {'P': FH + 'pine_planks', 'S': 'stick'}),
                           'was the wooden pickaxe pattern'),
    'timber_brace': (shaped(['  P', ' S ', 'P  '], {'P': '#minecraft:planks', 'S': 'stick'}, 4),
                     'was the Lookout Timber Brace pattern (dark oak planks)'),
    'lookout_brace': (shaped(['  L', ' L ', 'L  '], {'L': 'dark_oak_slab'}, 6),
                      'Timber set from dark oak slabs: the Lookout set (Frontier Structures) uses dark oak planks'),
    'lookout_cross_brace': (shaped(['L L', ' L ', 'L L'], {'L': 'dark_oak_slab'}, 6), ''),
    'lookout_deck_rail': (shaped(['S S', 'LLL', 'S S'], {'L': 'dark_oak_slab', 'S': 'stick'}, 6), ''),
    'lookout_stair_rail': (shaped(['  S', ' SL', 'SL '], {'L': 'dark_oak_slab', 'S': 'stick'}, 6), ''),
}


def original(repo, rid, base):
    p = os.path.join(repo, 'patch/data/frontierhunts/recipe', rid + '.json')
    if os.path.exists(p):
        return json.load(open(p))
    return json.loads(base.read(f'data/frontierhunts/recipe/{rid}.json'))


def main(argv):
    repo = os.path.abspath(argv[0] if argv else '.')
    base = zipfile.ZipFile(argv[argv.index('--base') + 1] if '--base' in argv else BASE_JAR)
    for rid, (spec, why) in NEW.items():
        old = original(repo, rid, base)
        res = dict(old.get('result') or {})
        out = {'type': spec['type']}
        for k in ('group', 'category'):
            if k in old:
                out[k] = old[k]
        if 'pattern' in spec:
            out['pattern'] = spec['pattern']
            out['key'] = spec['key']
        else:
            out['ingredients'] = spec['ingredients']
        res['count'] = spec['count']
        out['result'] = {'id': res['id'], 'count': res['count']}
        p = os.path.join(repo, 'patch/data/frontierhunts/recipe', rid + '.json')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'w') as f:
            json.dump(out, f, indent=2)
            f.write('\n')
    print(f'wrote {len(NEW)} recipes')


if __name__ == '__main__':
    main(sys.argv[1:])
