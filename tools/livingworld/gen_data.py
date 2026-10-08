#!/usr/bin/env python3
"""[livingworld] Generates the world-generation data of the living-world sites:
structures (one json per kind), structure sets (rarity / spacing), biome tags.
usage: gen_data.py <repo>

[villages] DO NOT RE-RUN AS IS: the building kinds were retired from world generation (living_posts / living_spots /
living_relics sets deleted, abandoned_camp dropped from living_camps, avoid lists point at frontierstructures:villages);
those JSON outputs were edited by hand after this generator."""
import json, os, sys
R = sys.argv[1] if len(sys.argv) > 1 else '.'
D = os.path.join(R, 'patch/data/frontierhunts')

ALPINE_LAND = ['valley_meadow', 'cedar_valley', 'pine_highlands', 'alpine_meadow', 'birch_grove', 'maple_woodland', 'aspen_woodland',
               'snowy_pine_forest', 'rocky_foothills', 'riverwood', 'mosswood', 'wildflower_glade', 'woodland_lakeshore', 'golden_aspen_grove',
               'autumn_maple_hollow', 'larch_highlands', 'fireweed_burn', 'heather_moor', 'sagebrush_bench', 'aspen_parkland', 'rowan_slope',
               'cottonwood_bottom', 'birch_heath', 'alder_carr', 'limestone_bluff', 'verdant_karst', 'marsh_meadow']
ALPINE_MOUNTAIN = ['pine_highlands', 'alpine_meadow', 'larch_highlands', 'rocky_foothills', 'alpine_fellfield', 'heather_moor', 'snowy_pine_forest',
                   'boulder_talus', 'rowan_slope', 'sagebrush_bench', 'limestone_bluff']
ALPINE_WET = ['marsh_meadow', 'fern_wetland', 'woodland_lakeshore', 'muskeg_bog', 'alder_carr', 'riverwood', 'cottonwood_bottom', 'valley_meadow',
              'wildflower_glade', 'aspen_parkland', 'birch_grove', 'cedar_valley']
ALPINE_OPEN = ['valley_meadow', 'alpine_meadow', 'wildflower_glade', 'aspen_parkland', 'sagebrush_bench', 'fireweed_burn', 'heather_moor', 'birch_heath',
               'golden_aspen_grove', 'autumn_maple_hollow', 'maple_woodland', 'aspen_woodland', 'cedar_valley', 'cottonwood_bottom', 'rowan_slope',
               'birch_grove', 'riverwood', 'pine_highlands']
V_LAND = ['#minecraft:is_forest', '#minecraft:is_taiga', 'minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow', 'minecraft:snowy_plains',
          'minecraft:grove', 'minecraft:windswept_forest', 'minecraft:windswept_hills', 'minecraft:cherry_grove']
V_MOUNTAIN = ['minecraft:meadow', 'minecraft:grove', 'minecraft:snowy_slopes', 'minecraft:windswept_hills', 'minecraft:windswept_gravelly_hills',
              'minecraft:windswept_forest', 'minecraft:taiga', 'minecraft:snowy_taiga', 'minecraft:old_growth_spruce_taiga', 'minecraft:old_growth_pine_taiga']
V_WET = ['minecraft:swamp', 'minecraft:river', 'minecraft:plains', 'minecraft:forest', 'minecraft:birch_forest', 'minecraft:taiga', 'minecraft:meadow',
         'minecraft:dark_forest', 'minecraft:flower_forest', 'minecraft:old_growth_birch_forest']
V_OPEN = ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:meadow', 'minecraft:forest', 'minecraft:birch_forest', 'minecraft:flower_forest',
          'minecraft:taiga', 'minecraft:snowy_plains', 'minecraft:windswept_forest', 'minecraft:old_growth_birch_forest', 'minecraft:cherry_grove']
OPT = [{'id': '#c:is_forest', 'required': False}, {'id': '#c:is_plains', 'required': False}]

TAGS = {
    'living_land': V_LAND + OPT + ['frontierhunts:' + b for b in ALPINE_LAND],
    'living_mountain': V_MOUNTAIN + ['frontierhunts:' + b for b in ALPINE_MOUNTAIN],
    'living_wet': V_WET + V_LAND + OPT + ['frontierhunts:' + b for b in sorted(set(ALPINE_WET + ALPINE_LAND + ['alpine_river', 'wild_coast']))],
    'living_open': V_OPEN + ['frontierhunts:' + b for b in ALPINE_OPEN],
}

EXTERNAL = ['frontierhunts:expedition_buildings', 'frontierhunts:frontier_hamlets', 'frontierhunts:expedition_landmarks',
            'frontierstructures:buildings', 'frontierstructures:hamlets', 'minecraft:villages']
# kind: (biome tag, set, weight)
KINDS = {
    'hunting_camp': ('living_land', 'living_camps', 7),
    'abandoned_camp': ('living_land', 'living_camps', 3),
    'elk_camp': ('living_mountain', 'living_camps', 3),
    'outfitter_post': ('living_land', 'living_posts', 2),
    'ranger_station': ('living_land', 'living_posts', 2),
    'trapper_cabin': ('living_land', 'living_posts', 3),
    'meat_shed': ('living_land', 'living_posts', 2),
    'trailhead': ('living_land', 'living_posts', 3),
    'stand_line': ('living_open', 'living_spots', 3),
    'ground_blind_plot': ('living_open', 'living_spots', 3),
    'duck_blind': ('living_wet', 'living_spots', 3),
    'glassing_point': ('living_mountain', 'living_spots', 2),
    'fence_line': ('living_open', 'living_relics', 3),
    'antler_cache': ('living_land', 'living_relics', 2),
}
SETS = {
    'living_camps': dict(spacing=15, separation=5, salt=410012601, avoid=EXTERNAL, r=3),
    'living_posts': dict(spacing=20, separation=7, salt=410012602, avoid=EXTERNAL + ['frontierhunts:living_camps'], r=3),
    'living_spots': dict(spacing=11, separation=4, salt=410012603, avoid=EXTERNAL + ['frontierhunts:living_camps', 'frontierhunts:living_posts'], r=2),
    'living_relics': dict(spacing=14, separation=5, salt=410012604,
                          avoid=EXTERNAL + ['frontierhunts:living_camps', 'frontierhunts:living_posts', 'frontierhunts:living_spots'], r=2),
}
ONLY = os.environ.get('LW_KINDS')
active = [k for k in KINDS if not ONLY or k in ONLY.split(',')]


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


for t, vals in TAGS.items():
    w(f'{D}/tags/worldgen/biome/has_structure/{t}.json', {'replace': False, 'values': vals})
for k in active:
    tag, s, _ = KINDS[k]
    w(f'{D}/worldgen/structure/{k}.json', {
        'type': 'frontierhunts:living_site', 'kind': k, 'biomes': '#frontierhunts:has_structure/' + tag,
        'step': 'top_layer_modification', 'spawn_overrides': {}, 'terrain_adaptation': 'none',
        'avoid': SETS[s]['avoid'], 'avoid_chunks': SETS[s]['r']})
for s, cfg in SETS.items():
    members = [{'structure': 'frontierhunts:' + k, 'weight': KINDS[k][2]} for k in active if KINDS[k][1] == s]
    if not members:
        continue
    w(f'{D}/worldgen/structure_set/{s}.json', {'structures': members, 'placement': {
        'type': 'minecraft:random_spread', 'spacing': cfg['spacing'], 'separation': cfg['separation'], 'salt': cfg['salt']}})
print('kinds', len(active))
