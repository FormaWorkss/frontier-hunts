"""[economy] Value model data for tools/economy/value_model.py.

Unit: 1 pt (effort point) ~ 6 seconds of focused survival effort. Calibration: 1 iron ingot = 10 pt (find + mine +
smelt, mid-game cave/strip mining yields ~60 ingots an hour), so an hour of material gathering ~ 600 pt.

BASE    raw / non-crafted materials (vanilla and mod drops), with the reason for the value.
TARGET  every crafted output of the mod: (tier, target pt per crafted unit, vanilla analog / reason).
        Tier bands (pt): starter <= 15 (first 15 minutes) · early 5-60 (first hour, <= ~3 iron) · mid 30-150
        (iron age, copper, redstone, amethyst) · late 100-350 (gold, diamond, nether, iron blocks) · endgame 250+.
        'decor' and 'block' (building blocks, furniture, conversions) are judged loosely (x3), 'conv' are exact
        conversions (logs, cooking) and are not judged.
"""

# ------------------------------------------------------------------------------------------------ raw materials
BASE = {
    # wood / ground
    'minecraft:stick': (0.25, '2 planks -> 4'),
    '#minecraft:planks': (0.5, '1 log -> 4'),
    'minecraft:oak_planks': (0.5, ''), 'minecraft:spruce_planks': (0.5, ''), 'minecraft:dark_oak_planks': (0.5, ''),
    '#minecraft:logs': (2, 'chopping ~12 s'),
    '#minecraft:wooden_slabs': (0.25, '3 planks -> 6'),
    '#c:rods/wooden': (0.25, 'stick'),
    'minecraft:oak_sapling': (1, 'leaf drop'),
    'minecraft:oak_leaves': (0.3, 'shears'), '#minecraft:leaves': (0.3, 'shears'),
    'minecraft:cobblestone': (0.3, ''), 'minecraft:gravel': (0.5, ''), 'minecraft:dirt': (0.1, ''),
    'minecraft:granite': (0.5, ''), 'minecraft:moss_block': (2, 'lush caves / bone meal'),
    'minecraft:short_grass': (0.2, 'shears'), 'minecraft:fern': (0.3, 'shears'),
    'minecraft:flint': (1.5, '~10 % per gravel block'),
    'minecraft:crafting_table': (2, '4 planks'), 'minecraft:chest': (4, '8 planks'),
    'minecraft:furnace': (2.4, '8 cobblestone'), 'minecraft:smoker': (10.4, 'furnace + 4 logs'),
    'minecraft:blast_furnace': (55, '5 iron + furnace + 3 smooth stone'),
    'minecraft:ladder': (0.6, '7 sticks -> 3'), 'minecraft:torch': (0.6, 'coal + stick -> 4'),
    'minecraft:lever': (0.55, ''), 'minecraft:stone_button': (0.3, ''),
    'minecraft:hay_block': (4.5, '9 wheat'), 'minecraft:wheat': (0.5, 'farm'),
    'minecraft:dried_kelp_block': (3, '9 dried kelp'),
    # animals
    'minecraft:feather': (1.5, 'chickens, 0-2 each'), 'minecraft:string': (2, 'spiders at night / cobwebs'),
    'minecraft:leather': (4, 'cows, 0-2 each (or deer hide + flint)'), 'minecraft:bone': (1.5, 'skeletons'),
    '#c:bones': (1.5, 'bone'), 'minecraft:bone_meal': (0.5, '1 bone -> 3'),
    'minecraft:rotten_flesh': (0.5, 'zombies'), 'minecraft:beef': (2, 'cows'),
    'minecraft:cod': (3, 'fishing'), '#minecraft:fishes': (3, 'fishing'),
    '#minecraft:wool': (2, 'shears'), 'minecraft:white_wool': (2, ''), 'minecraft:green_wool': (3, '+ dye'),
    'minecraft:brown_wool': (3, '+ dye'), 'minecraft:orange_wool': (3, '+ dye'), 'minecraft:yellow_wool': (3, '+ dye'),
    'minecraft:slime_ball': (15, 'slime chunks / swamps at night'), 'minecraft:gunpowder': (6, 'creepers'),
    'minecraft:phantom_membrane': (20, 'phantoms after 3 sleepless nights, 0-1 each'),
    'minecraft:apple': (1, 'oak leaves'), '#c:foods/berry': (0.5, 'sweet berries'),
    'minecraft:lead': (11.5, '4 string + slime -> 2'),
    # dyes
    'minecraft:black_dye': (2, 'ink sac'), 'minecraft:white_dye': (0.5, 'bone meal'), 'minecraft:red_dye': (1, 'flower'),
    'minecraft:green_dye': (1.5, 'smelt cactus'), 'minecraft:yellow_dye': (1, 'flower'), 'minecraft:orange_dye': (1, ''),
    'minecraft:brown_dye': (2, 'cocoa'), 'minecraft:gray_dye': (1.5, ''), 'minecraft:light_gray_dye': (1, ''),
    'minecraft:lime_dye': (2, ''), 'minecraft:light_blue_dye': (1, ''),
    # minerals
    '#minecraft:coals': (2, 'coal ore / charcoal'), 'minecraft:charcoal': (2.5, 'smelt a log'),
    'minecraft:copper_ingot': (3, 'abundant ore, ~3 ingots a block'), 'minecraft:iron_ingot': (10, 'calibration unit'),
    'minecraft:iron_nugget': (1.1, '1/9 ingot'), 'minecraft:iron_block': (90, '9 ingots'),
    'minecraft:gold_ingot': (20, 'deeper, rarer ore'), 'minecraft:gold_nugget': (2.2, '1/9 ingot'),
    'minecraft:redstone': (3, 'deep ore, 4-5 dust a block'), 'minecraft:redstone_block': (27, '9 dust'),
    'minecraft:amethyst_shard': (6, 'geodes'), 'minecraft:diamond': (60, 'deepslate, rare'),
    'minecraft:quartz': (8, 'nether'), 'minecraft:glowstone_dust': (10, 'nether'), 'minecraft:obsidian': (12, 'lava + water + diamond pick'),
    'minecraft:glass': (1.2, 'sand + smelt'), 'minecraft:glass_pane': (0.45, '6 glass -> 16'),
    'minecraft:glass_bottle': (1.2, '3 glass -> 3'), 'minecraft:paper': (1, 'sugar cane'), 'minecraft:book': (7, '3 paper + leather'),
    'minecraft:lantern': (9.6, '8 nuggets + torch'), 'minecraft:piston': (15.7, '3 planks, 4 cobble, iron, redstone'),
    'minecraft:tripwire_hook': (5.4, 'iron + stick + plank -> 2'), 'minecraft:repeater': (5.5, '3 stone, 2 torches, redstone'),
    'minecraft:redstone_lamp': (52, '4 redstone + glowstone block'), 'minecraft:bucket': (30, '3 iron'),
    'minecraft:lava_bucket': (31, 'bucket'), 'minecraft:note_block': (7, '8 planks + redstone'),
    'minecraft:compass': (43, '4 iron + redstone'),
    # mod drops and station products (code sources)
    'frontierhunts:deer_hide': (6, 'one per whitetail (2-3 elk/moose); a hunt is ~150 pt, hides are a by-product'),
    'frontierhunts:tanned_hide': (8, 'deer hide + Tanning Rack time'),
    'frontierhunts:tanned_fur': (8, 'furbearer pelt + Tanning Rack'),
    'frontierhunts:bear_fur': (20, 'bear pelt (dangerous quarry) + Tanning Rack'),
    'frontierhunts:tanned_heavy_hide': (15, 'bison hide + Tanning Rack'),
    '#frontierhunts:tanned_furs': (8, 'cheapest member'), '#frontierhunts:tanned_hides': (8, 'cheapest member'),
    'frontierhunts:jerky': (4, 'meat + Drying Rack'), 'frontierhunts:tallow': (2, 'game fat smelted'),
    'frontierhunts:pine_log': (2, 'log'), 'frontierhunts:cedar_log': (2, 'log'),
    'frontierhunts:venison': (3, 'whitetail ~4-6 a deer'), 'frontierhunts:backstrap': (4, '2 a deer'),
    'frontierhunts:bear_meat': (4, ''), 'frontierhunts:game_meat': (3, ''), 'frontierhunts:organ_meat': (2, ''),
    'frontierhunts:wild_fowl': (3, ''), 'frontierhunts:game_fat': (1.5, ''),
    # [licence] camp-cooking produce: farm / forage crops, eggs, honey, smoked fish, salt
    'minecraft:carrot': (0.5, 'farm'), 'minecraft:potato': (0.5, 'farm'), 'minecraft:beetroot': (0.5, 'farm'),
    'minecraft:brown_mushroom': (0.6, 'forage'), 'minecraft:red_mushroom': (0.6, 'forage'), 'minecraft:egg': (0.8, 'chickens'),
    'minecraft:sweet_berries': (0.5, 'forage'), 'minecraft:cocoa_beans': (1, 'jungle'), 'minecraft:honey_bottle': (3, 'hive + bottle'),
    'minecraft:cooked_cod': (3.5, 'fish + fuel'), 'minecraft:cooked_salmon': (3.5, 'fish + fuel'), 'minecraft:salmon': (3, 'fishing'),
    'frontierhunts:salt': (1.5, 'water bottle evaporated'),
}
for _l in ('alpine_alder_log', 'alpine_cottonwood_log', 'alpine_maple_log', 'alpine_pine_log', 'alpine_rowan_log',
           'alpine_spruce_log', 'aspen_log', 'birch_log'):
    BASE['frontierhunts:' + _l] = (2, 'log')

# ------------------------------------------------------------------------------------------------ targets
S, E, M, L, X, D, B, C = 'starter', 'early', 'mid', 'late', 'endgame', 'decor', 'block', 'conv'
TARGET = {
    # --- first-hour essentials (Handbook steps 1-7)
    'frontier_handbook': (S, 8, 'a book with notes (vanilla book-and-quill ~9)'),
    'primitive_arrow': (S, 0.8, 'vanilla arrow: flint + stick + feather -> 4'),
    'field_arrow': (S, 0.8, 'vanilla arrow; iron-nugget broadhead instead of flint'),
    'field_bow': (S, 8, 'vanilla bow 6.75 pt'),
    'bow_tuning_rack': (S, 10, 'fletching table ~5 pt'),
    'skinning_tool': (E, 14, 'one iron blade: field dressing is needed from the first deer'),
    'field_knife': (E, 14, 'half an iron sword (20 pt)'),
    'wind_checker': (S, 5, 'a puffer bottle'),
    'hunter_journal': (S, 5, 'book-and-quill'),
    'tanning_rack': (S, 6, 'a frame of sticks'), 'drying_rack': (S, 5, 'a frame of sticks'),
    'game_pole': (S, 6, 'logs and sticks'),
    'clothing_workbench': (E, 12, 'loom/cartography table class station'),
    'tent_bench': (E, 12, 'loom class station'),
    'fishing_station': (E, 12, 'station'),
    'pup_tent': (E, 18, 'first tent: a bed (7.5) plus shelter'),
    'solo_ridge_tent': (E, 15, 'one-person tent'),
    'hide_bedroll': (E, 25, 'a bed made of hides'),
    # --- arrow heads (Bow Tuning Rack), per head
    'field_point': (S, 0.6, 'practice point: a pinch of iron'),
    'flint_point': (S, 1.2, 'knapped flint'), 'bone_point': (S, 0.9, 'bone shaped with flint'),
    'judo_point': (S, 0.8, 'small-game point'), 'obsidian_point': (M, 2.5, 'obsidian flake'),
    'fixed_broadhead': (E, 2.8, 'steel blades, reusable'), 'cut_on_contact_broadhead': (E, 3.2, 'steel + flint edge'),
    'mechanical_broadhead': (M, 3.5, 'deploying blades'),
    'tracer_broadhead': (L, 6, 'glowstone nock (nether)'), 'tracer_field_point': (L, 3.5, 'glowstone nock'),
    'tracer_ice_broadhead': (L, 6, 'glowstone nock'),
    # --- ammunition, per round
    'rifle_round': (M, 1.4, 'brass case, lead bullet, powder'), 'reserve_308': (M, 1.4, 'brass case, bullet, powder'),
    'pistol_round': (M, 1.3, 'brass, powder'), 'shotgun_shell': (M, 1.0, 'paper hull, shot, powder'),
    'tranquilizer_dart': (M, 2.2, 'glass vial + slime sedative'), 'flare_round': (E, 1.3, 'paper, powder, colour'),
    'bowfishing_arrow': (E, 0.5, 'retrievable fishing arrow'), 'bait': (S, 1.5, 'grain and scraps'),
    # --- bows and weapons
    'recurve_bow': (E, 15, 'laminated recurve, a step above the field bow (vanilla bow 6.75)'),
    'compound_bow': (M, 50, 'steel riser, cams and cables (vanilla crossbow 22)'),
    'crossbow': (M, 50, 'hunting crossbow, strongest bow (vanilla crossbow 22)'),
    'bowfishing_bow': (E, 22, 'bow + reel'), 'hunting_spear': (E, 22, 'iron sword class (20)'),
    'flare_gun': (E, 28, 'signal pistol'), 'bait_launcher': (E, 28, 'spring launcher'),
    'tranquilizer_rifle': (M, 50, 'CO2 dart rifle, no damage'),
    'double_barrel': (M, 95, 'break-action shotgun: barrels (iron block), stock, trigger'),
    'pump_shotgun': (M, 115, 'pump action (piston), 4 shells'),
    'lever_rifle': (M, 110, 'lever action, 5 rounds'),
    'ridgeline_rifle': (M, 125, 'bolt rifle: barrel (iron block), bolt, trigger, stock'),
    'revolver': (M, 70, 'six-shot handgun'),
    'field_pistol': (L, 100, 'semi-auto 2011: slide (piston) + match trigger (gold)'),
    'semi_auto_shotgun': (L, 175, 'gas-operated autoloader'),
    'semi_auto_rifle': (L, 230, '20-round automatic rifle'),
    # --- optics and attachments
    'binoculars': (E, 22, 'two spyglasses (vanilla spyglass 12)'),
    'rangefinder': (M, 32, 'laser electronics: redstone'),
    'field_camera': (M, 30, 'lens + redstone shutter'), 'trail_camera': (M, 40, 'lens, redstone trigger, iron case'),
    'camera_base_station': (M, 85, 'receiver'), 'field_battery_pack': (M, 8, 'copper cells + redstone'),
    'field_flashlight': (M, 22, 'lens, copper, redstone'), 'field_spotlight': (L, 60, 'redstone lamp floodlight'),
    'fish_finder': (M, 45, 'sonar electronics'),
    'night_vision_binoculars': (L, 90, 'image intensifier: gold, redstone, battery'),
    'thermal_binoculars': (X, 150, 'thermal imager: diamond sensor, gold, redstone'),
    'reflex_sight': (M, 28, 'illuminated 1x dot: glass + redstone'),
    'micro_red_dot': (M, 32, 'illuminated 1x dot'), 'holographic_sight': (M, 55, 'laser holographic: gold + redstone'),
    'two_power_prism': (M, 38, 'prism optic'), 'ridgeline_scope': (M, 42, '3-9x hunting scope: amethyst lens'),
    'four_power_optic': (M, 50, '4-12x variable'), 'six_power_scope': (M, 45, '6x: iron tube, glass, amethyst lens'),
    'eight_power_scope': (L, 75, '8x: + gold'), 'twelve_power_scope': (L, 130, '12x: + diamond-ground lens'),
    'thermal_scope': (X, 160, 'thermal sight: diamond sensor, gold, battery'),
    'suppressor': (M, 65, 'steel baffle tube'), 'muzzle_brake': (M, 30, 'ported steel'),
    'extended_magazine': (M, 35, 'steel box + spring'), 'sniper_magazine': (M, 18, '5-round box'),
    'pistol_magazine': (M, 18, 'pistol box'), 'steady_stock': (M, 25, 'wood + steel + leather'),
    'bipod': (M, 24, 'steel legs'), 'angled_foregrip': (M, 25, 'steel grip'),
    'attachment_tool': (M, 20, 'gunsmith tool'), 'fishing_drag_kit': (E, 12, 'reel drag'),
    'field_fishing_rod': (S, 7, 'vanilla fishing rod 5 pt + copper reel'),
    # --- calls, lures, field gear
    'deer_call': (M, 25, 'electronic caller (pulls every deer in 64 blocks): note block + redstone'),
    'grunt_tube': (S, 8, 'mouth call'), 'bleat_call': (S, 6, 'mouth call'), 'duck_call': (E, 6, 'reed call'),
    'predator_call': (E, 8, 'mouth call'), 'rattling_antlers': (E, 8, 'bones'),
    'scent_cover': (E, 4, 'consumable spray'), 'medkit': (E, 9, 'consumable bandage kit (regen II 8 s)'),
    'expedition_guide': (E, 10, 'a book'), 'glow_lure': (L, 15, 'glowstone'), 'landing_net': (E, 8, 'net'),
    'chum_bucket': (E, 34, 'bucket of chum'), 'horse_whistle': (E, 4, 'whistle'),
    'hound_lead': (E, 20, 'lead + leather'), 'whitetail_scent_decoy': (E, 8, 'scent wick and bait on a sapling'),
    'mallard_decoy': (E, 2, 'carved decoy'), 'shooting_target': (E, 16, 'target'),
    'hunter_pack': (M, 35, 'backpack storage (chest + leather)'), 'hunters_quiver': (E, 15, 'leather quiver'),
    # --- clothing (armour analogs: leather chestplate 32, iron chestplate 80)
    **{f'{c}_camo_coveralls': (E, 30, 'leather chest piece + camo') for c in
       ('autumn', 'blaze', 'digital', 'marsh', 'prairie', 'snow', 'timber')},
    'scent_suit': (M, 35, 'carbon suit'), 'carbon_hood': (M, 22, ''), 'carbon_jacket': (M, 32, ''),
    'carbon_trousers': (M, 30, ''),
    **{f'ghillie{v}_{p}': (M, t, 'netting + hides + dye') for v in ('', '_grassland', '_snow', '_wetland')
       for p, t in (('hood', 20), ('jacket', 26), ('trousers', 26))},
    'fur_hat': (M, 34, 'pelts'), 'buckskin_coat': (M, 55, 'leather chest x1.7 (warmth)'),
    'buckskin_leggings': (M, 50, ''), 'bear_fur_coat': (L, 100, 'bear pelts: the warmest coat'),
    'hide_robe': (L, 70, 'heavy hides'), 'fur_mukluks': (M, 34, ''), 'fur_lining': (M, 24, ''),
    'fur_mittens': (M, 18, ''), 'wingsuit': (L, 110, 'elytra-class glider: phantom membrane wings'),
    # --- tents, blinds, stands
    'canvas_wall': (E, 3.7, 'hide-and-string panel'), 'backpacker_dome_tent': (E, 15, ''),
    'trail_dome_tent': (E, 24, ''), 'hunters_canvas_tent': (E, 26, ''), 'canvas_wall_tent': (E, 26, ''),
    'bell_tent': (E, 25, ''), 'woodland_camp_tent': (M, 40, 'large tent, iron poles'),
    'family_cabin_tent': (M, 40, 'large tent, iron poles'),
    'field_blind': (E, 22, 'portable ground blind'), 'tower_blind': (M, 60, 'steel tower'),
    'tree_stand': (M, 50, 'steel hang-on stand'), 'camp_cot': (E, 18, 'wood frame + hides'),
    # --- stations
    'weapons_workbench': (E, 40, 'smithing-table class gate to firearms (22)'),
    'ammo_reloader': (M, 35, 'reloading press: iron + piston'),
    'attachment_workbench': (M, 28, 'gunsmith bench'), 'lodge_stores': (E, 14, '3 chests'),
    'smokehouse': (E, 20, 'smoker+'), 'contract_board': (S, 5, ''), 'expedition_board': (S, 6, ''),
    'camp_post': (S, 4, ''), 'gun_rack': (D, 8, 'display'), 'big_buck_board': (D, 12, 'display'),
    'trophy_plinth': (D, 3, 'display'), 'bow_stand': (D, 2, 'display'),
    # --- vehicle
    'atv': (L, 180, 'engine (blast furnace + piston), iron frame, tyres'),
    'atv_cargo_box': (M, 25, 'a chest with straps'), 'atv_can_carrier': (M, 28, 'steel rack'),
    'jerry_can': (M, 40, 'steel can (bucket 30)'),
    # --- survival food
    'pemmican': (E, 3.5, 'jerky + tallow'),
    # --- [licence] camp cooking: per serving ~ hunger x 0.35 + a little for the buff; the oven is a cast-iron pot
    'dutch_oven': (E, 50, 'cast-iron pot: 5 iron + a nugget knob (vanilla cauldron 70)'),
    'venison_stew': (E, 3.3, '8 hunger, bowl, Camp Warmth'), 'venison_chili': (E, 3.3, '8 hunger, bowl, Trail Stamina'),
    'bear_pot_roast': (E, 3.65, '9 hunger, Camp Warmth'), 'backstrap_mushrooms': (E, 3.3, '8 hunger, Steady Aim'),
    'fowl_berry_roast': (E, 2.95, '7 hunger, Keen Tracker'), 'fowl_wild_rice': (E, 3.3, '8 hunger, bowl, Quiet Step'),
    'heart_liver_fry': (E, 2.6, '6 hunger, Hearty'), 'hunters_breakfast': (E, 4.0, '10 hunger, Steady Aim'),
    'fish_chowder': (E, 2.95, '7 hunger, bowl, Camp Warmth'), 'camp_bannock': (E, 1.6, 'bread analog (3 wheat = 1.5 pt)'),
    'honey_pemmican': (E, 3.8, 'pemmican + honey'), 'raw_game_sausage': (E, 2.2, 'ground game + fat + salt'),
    'smoked_game_sausage': (E, 2.4, 'smoked: keeps two weeks, Smoke-Masked'),
    # --- building blocks / furniture / decor
    'lodge_stove': (D, 60, 'iron stove'), 'stove_flue': (B, 1.6, ''), 'stove_roof_flashing': (B, 12, ''),
    'roof_shingles': (B, 0.8, ''), 'cabin_lantern': (D, 6, 'lantern 9.6'), 'cabin_door': (B, 1, ''),
    'lit_lodge_table': (D, 9, ''), 'lodge_table': (D, 2.5, ''), 'lodge_chair': (D, 2, ''),
    'trail_sign': (D, 2, ''), 'ranger_window': (B, 0.6, ''), 'rope_ladder': (B, 2.2, ''),
    # [onboard2] seats
    'log_stump_seat': (D, 5.5, 'a log with a leather pad'), 'camp_chair': (D, 15, 'folding canvas chair, steel frame'),
    'trail_bench': (D, 1.5, 'planks and sticks, 2 a craft (lodge chair 2)'),
    'blind_chair': (D, 18, 'swivel bearing (an iron ingot) + padded seat'),
    'tower_chair': (D, 26, 'swivel + gas lift (2 iron) + padded seat'),
    'stacked_firewood': (B, 1.5, ''), 'fallen_branch': (B, 0.4, ''), 'tent_end_ridge': (B, 5, 'ridge + panel'),
    **{k: (B, v, '') for k, v in (('tent_gable', 0.7), ('tent_ridge', 0.7), ('tent_slope', 0.7),
                                  ('timber_brace', 0.4), ('timber_cross_brace', 0.8), ('timber_stair_guard', 1.3),
                                  ('lookout_brace', 0.25), ('lookout_cross_brace', 0.4), ('lookout_deck_rail', 0.4),
                                  ('lookout_stair_rail', 0.3), ('fieldstone', 0.4), ('fieldstone_stairs', 0.6),
                                  ('forest_loam', 0.2), ('moss_floor', 1), ('pine_planks', 0.5), ('cedar_planks', 0.5),
                                  ('pine_slab', 0.25), ('pine_stairs', 0.75), ('pine_fence', 1),
                                  ('weathered_planks', 1), ('reserve_granite', 0.5), ('alpine_pasture', 0.2),
                                  ('dark_oak_lattice_window', 0.6), ('spruce_casement_window', 0.6),
                                  ('spruce_picture_window', 1.1))},
    **{f'roof_{k}': (B, 1, '') for k in ('gable_high', 'gable_low', 'ridge_high', 'ridge_low', 'slope_high',
                                         'slope_low', 'slab', 'stairs')},
}
TIER_ORDER = {S: 0, E: 1, M: 2, L: 3, X: 4, D: 5, B: 6, C: 7}

# Expected token income (documented model, see docs/ws/economy.md) - pt per reserve token.
TOKENS_PER_HUNT = 80     # assignment ~45 + contract share ~20 + provisions ~15 (casual 50, everything 110)
HUNTS_PER_HOUR = 3       # find, stalk, shoot, track, dress, carry back: ~20 min
PT_PER_HOUR = 600        # material gathering effort an hour (60 iron ingots)
PT_PER_TOKEN = PT_PER_HOUR / (TOKENS_PER_HUNT * HUNTS_PER_HOUR)  # = 2.5
