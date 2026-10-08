#!/usr/bin/env python3
"""[livingworld] Chest loot of the living-world sites: data/frontierhunts/loot_table/chests/living/*.json.
Balanced on purpose: food, ammunition in small counts, field consumables, hides, the Frontier Handbook and notes;
no firearms, scopes or other end-game gear.  usage: gen_loot.py <repo>"""
import json, os, sys
R = sys.argv[1] if len(sys.argv) > 1 else '.'
D = os.path.join(R, 'patch/data/frontierhunts/loot_table/chests/living')
FH = 'frontierhunts:'


def item(name, w, lo=1, hi=1, extra=None):
    e = {'type': 'minecraft:item', 'name': name if ':' in name else FH + name, 'weight': w}
    fn = []
    if hi > 1 or lo > 1:
        fn.append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}})
    if extra:
        fn += extra
    if fn:
        e['functions'] = fn
    return e


def note(group, lines, w):
    """a paper note with a translated title and lore lines (frontierhunts.lw.<group>.title / .1 ..)"""
    return item('minecraft:paper', w, extra=[
        {'function': 'minecraft:set_name', 'target': 'item_name', 'name': {'translate': f'frontierhunts.lw.{group}.title'}},
        {'function': 'minecraft:set_lore', 'mode': 'replace_all',
         'lore': [{'translate': f'frontierhunts.lw.{group}.{i}', 'italic': True, 'color': 'gray'} for i in range(1, lines + 1)]}])


def book(group, pages, title, author, w):
    return item('minecraft:written_book', w, extra=[
        {'function': 'minecraft:set_book_cover', 'title': title, 'author': author, 'generation': 1},
        {'function': 'minecraft:set_written_book_pages', 'mode': 'replace_all',
         'pages': [{'translate': f'frontierhunts.lw.{group}.{i}'} for i in range(1, pages + 1)]},
        {'function': 'minecraft:set_name', 'target': 'custom_name', 'name': {'translate': f'frontierhunts.lw.{group}.title', 'italic': False}}])


def handbook(w):
    return item('frontier_handbook', w)


def table(name, pools):
    os.makedirs(D, exist_ok=True)
    out = {'type': 'minecraft:chest', 'pools': [{'rolls': {'type': 'minecraft:uniform', 'min': lo, 'max': hi} if lo != hi else lo, 'bonus_rolls': 0,
                                                'entries': entries} for lo, hi, entries in pools],
           'random_sequence': f'frontierhunts:chests/living/{name}'}
    with open(os.path.join(D, name + '.json'), 'w') as f:
        json.dump(out, f, indent=1)


FOOD = [item('venison', 4, 1, 3), item('cooked_venison', 4, 1, 2), item('jerky', 5, 2, 5), item('aged_venison', 2), item('backstrap', 1),
        item('pemmican', 2, 1, 2), item('minecraft:bread', 3, 1, 3), item('minecraft:sweet_berries', 2, 2, 6), item('minecraft:apple', 2, 1, 3),
        item('salt', 3, 1, 3), item('game_fat', 1, 1, 2), item('minecraft:baked_potato', 2, 1, 3)]
CAMP_GEAR = [item('minecraft:torch', 4, 2, 6), item('minecraft:string', 4, 1, 4), item('minecraft:leather', 2, 1, 2), item('minecraft:flint', 3, 1, 3),
             item('field_arrow', 3, 2, 6), item('primitive_arrow', 3, 2, 6), item('field_point', 2, 1, 3), item('fixed_broadhead', 1, 1, 2),
             item('scent_cover', 2, 1, 2), item('bait', 2, 1, 2), item('wind_checker', 1), item('grunt_tube', 1), item('bleat_call', 1),
             item('deer_call', 1), item('medkit', 1), item('field_flashlight', 1), item('field_battery_pack', 1), item('rifle_round', 2, 2, 6),
             item('shotgun_shell', 1, 2, 5), item('minecraft:paper', 2, 1, 3), item('minecraft:candle', 1, 1, 2), item('minecraft:charcoal', 2, 1, 4),
             item('minecraft:lead', 1), handbook(1)]
NOTES_CAMP = [note('note.camp.0', 3, 2), note('note.camp.1', 3, 2), note('note.camp.2', 3, 2), note('note.camp.3', 3, 2)]

table('camp_cooler', [(3, 5, FOOD)])
table('camp_stores', [(4, 7, CAMP_GEAR), (1, 1, NOTES_CAMP + [{'type': 'minecraft:empty', 'weight': 6}])])
table('camp_tent', [(2, 4, [item('minecraft:bread', 3, 1, 2), item('jerky', 4, 1, 3), item('minecraft:white_wool', 2, 1, 2), item('minecraft:string', 2, 1, 3),
                            item('scent_cover', 2), item('fur_hat', 1), item('field_knife', 1), item('binoculars', 1), item('minecraft:candle', 2),
                            item('minecraft:book', 1), handbook(2)]),
                    (1, 1, NOTES_CAMP + [book('book.camplog', 4, 'Deer Camp Log', 'The camp', 2), {'type': 'minecraft:empty', 'weight': 5}])])
table('abandoned', [(3, 6, [item('minecraft:rotten_flesh', 3, 1, 3), item('minecraft:bone', 4, 1, 4), item('minecraft:string', 3, 1, 3),
                            item('spoiled_meat', 2, 1, 2), item('minecraft:leather', 2, 1, 2), item('minecraft:iron_nugget', 2, 1, 4),
                            item('minecraft:arrow', 2, 1, 4), item('field_arrow', 1, 1, 3), item('tanned_hide', 1), item('minecraft:glass_bottle', 2),
                            item('minecraft:coal', 2, 1, 3), item('minecraft:compass', 1), item('minecraft:torch', 2, 1, 3), handbook(1)]),
                    (1, 1, [note('note.abandoned.0', 3, 3), note('note.abandoned.1', 3, 3), note('note.abandoned.2', 3, 3),
                            book('book.lastentries', 4, 'Last Entries', 'unsigned', 3)])])
table('outfitter', [(4, 6, [item('field_arrow', 3, 4, 8), item('fixed_broadhead', 2, 2, 4), item('field_point', 3, 4, 8), item('rifle_round', 3, 4, 8),
                            item('shotgun_shell', 2, 4, 8), item('scent_cover', 2, 1, 2), item('bait', 2, 1, 2), item('wind_checker', 2),
                            item('grunt_tube', 1), item('rattling_antlers', 1), item('duck_call', 1), item('predator_call', 1), item('field_battery_pack', 2),
                            item('binoculars', 1), item('fur_hat', 1), item('fur_mittens', 1), item('buckskin_coat', 1),  # [onebook] no expedition_guide (legacy item)
                            item('skinning_tool', 1), item('minecraft:lead', 1), handbook(2)]),
                    (1, 1, [note('note.outfitter.0', 3, 2), note('note.outfitter.1', 3, 2), {'type': 'minecraft:empty', 'weight': 3}])])
table('ranger', [(3, 5, [item('minecraft:paper', 3, 2, 5), item('minecraft:map', 2), item('minecraft:compass', 1), item('minecraft:torch', 3, 2, 6),
                         item('minecraft:bread', 3, 1, 3), item('medkit', 2), item('field_flashlight', 1), item('wind_checker', 2), item('jerky', 2, 1, 3),
                         handbook(3)]),
                 (1, 1, [note('note.ranger.0', 3, 2), note('note.ranger.1', 3, 2), note('note.ranger.2', 3, 2)])])
table('ranger_handbook', [(1, 1, [handbook(1)]), (1, 2, [item('minecraft:paper', 3, 1, 3), item('minecraft:bread', 2, 1, 2), item('wind_checker', 1),
                                                          item('field_arrow', 2, 2, 4), item('minecraft:torch', 2, 2, 4)])])
table('trapper', [(3, 6, [item('fur_pelt', 4, 1, 3), item('bear_fur', 1), item('deer_hide', 3, 1, 2), item('tanned_hide', 2, 1, 2), item('tanned_fur', 1),
                          item('tallow', 2, 1, 2), item('game_fat', 2, 1, 2), item('minecraft:string', 3, 1, 4), item('salt', 3, 2, 4), item('minecraft:lead', 1),
                          item('minecraft:bone', 2, 1, 3), item('minecraft:rabbit_hide', 3, 1, 3), item('minecraft:leather', 2, 1, 2), item('field_knife', 1),
                          item('skinning_tool', 1)]),
                  (1, 1, [note('note.trapper.0', 3, 2), note('note.trapper.1', 3, 2), {'type': 'minecraft:empty', 'weight': 3}])])
table('meat_shed', [(4, 6, [item('salt', 4, 2, 6), item('game_meat', 3, 1, 3), item('venison', 3, 1, 3), item('aged_venison', 2, 1, 2), item('jerky', 4, 2, 6),
                            item('game_fat', 2, 1, 2), item('tallow', 2, 1, 2), item('organ_meat', 1), item('minecraft:bone', 3, 2, 5),
                            item('minecraft:string', 2, 1, 3), item('minecraft:charcoal', 3, 2, 5)])])
table('elk_camp', [(4, 6, [item('jerky', 4, 2, 5), item('pemmican', 3, 1, 3), item('game_meat', 2, 1, 2), item('salt', 2, 1, 3), item('rifle_round', 3, 2, 5),
                           item('field_arrow', 2, 2, 4), item('wind_checker', 2), item('medkit', 1), item('minecraft:bread', 2, 1, 3),
                           item('minecraft:torch', 2, 2, 4), item('hunter_pack', 1), handbook(1)]),
                   (1, 1, [note('note.elk.0', 3, 2), note('note.elk.1', 3, 2), book('book.elkcamp', 4, 'Elk Camp Journal', 'The outfit', 1),
                           {'type': 'minecraft:empty', 'weight': 2}])])
table('blind', [(2, 3, [item('scent_cover', 3), item('bait', 2), item('grunt_tube', 1), item('duck_call', 1), item('mallard_decoy', 2, 1, 2),
                        item('shotgun_shell', 3, 2, 6), item('field_arrow', 2, 1, 3), item('minecraft:bread', 2, 1, 2), item('jerky', 3, 1, 3)])])
table('trailhead', [(2, 4, [item('minecraft:map', 2), item('minecraft:paper', 3, 1, 3), item('minecraft:compass', 1), item('minecraft:bread', 3, 1, 2),
                            item('minecraft:torch', 3, 2, 4), item('field_flashlight', 1), handbook(3)]),
                    (1, 1, [note('note.trail.0', 3, 2), note('note.trail.1', 3, 2), {'type': 'minecraft:empty', 'weight': 2}])])
table('cache', [(2, 3, [item('minecraft:bone', 4, 1, 3), item('shed_antler', 3, 1, 2), item('deer_hide', 2), item('minecraft:string', 2, 1, 2),
                        item('jerky', 1, 1, 2)])])
print('ok')
