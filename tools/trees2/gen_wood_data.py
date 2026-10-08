"""[wood] Wood from Frontier Hunts trees: every tree gives its own log, and that log works like vanilla wood.

(trees2 made the mod's logs drop vanilla logs; the user did not want that: "the block needs to stay the same type as the
tree after breaking and the whole tree gives the same block ... there needs to be a way to get regular MC wood".)

Writes (run from the repo root: python3 tools/trees2/gen_wood_data.py):
  * loot tables of the mod's 10 log blocks: the log itself, like a vanilla log. Every tree generator builds a tree of
    ONE log block (WildTrees maples/willows were aspen logs; they now use the maple log), so every log of a tree
    drops the same item;
  * loot tables of the mod's 14 leaves: shears / Silk Touch keep the leaves, otherwise the sapling of the matching
    vanilla tree (the mod has no saplings of its own; vanilla oak-leaves chances, Fortune-boosted) and sticks at
    vanilla chances. Planting it grows a real vanilla oak / birch / spruce;
  * item tag fragments (merged by the build): every mod log in #minecraft:logs, #minecraft:logs_that_burn (fuel,
    charcoal, campfire), in the vanilla species tag of the planks it makes (#minecraft:oak_logs / spruce_logs /
    birch_logs / dark_oak_logs -> the vanilla planks recipe takes it: 1 log -> 4 planks) and in its own species tag
    #frontierhunts:<species>_logs;
  * recipes: 2 mod logs -> 2 of the matching vanilla log (shapeless; 2 in, so it never competes with the
    1 log -> 4 planks recipe). Pine and cedar planks are real building blocks of the reserve (stairs, slabs, fences,
    roofs, furniture need them), so they stay, but move to the stonecutter (1 pine/cedar log -> 4) so the crafting
    grid only ever turns a log into vanilla planks. The old 1 spruce/dark oak log -> 1 pine/cedar log recipes are
    removed (patch/_remove/wood.txt): they fought the vanilla planks recipe for the same single log;
  * realistic-pack item models: the round logs' items (vanilla and mod) are drawn as normal block cubes, with the
    same textures a placed log uses on Ultra (trees2).
Axe stripping (timber/WildLogStripping) uses the same vanilla family as the planks (the mod has no stripped logs).
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
P = lambda *a: os.path.join(ROOT, 'patch', *a)
FRAGMENT = 'wood.json'

# mod log -> (vanilla wood family: planks via #minecraft:<family>_logs, 2:2 log conversion, stripping; species tag)
LOGS = {
    'pine_log': ('spruce', 'pine_logs'),
    'cedar_log': ('dark_oak', 'cedar_logs'),
    'aspen_log': ('birch', 'aspen_logs'),
    'birch_log': ('birch', 'paper_birch_logs'),
    'alpine_spruce_log': ('spruce', 'mountain_spruce_logs'),
    'alpine_pine_log': ('spruce', 'lodgepole_pine_logs'),
    'alpine_maple_log': ('oak', 'maple_logs'),
    'alpine_alder_log': ('oak', 'alder_logs'),
    'alpine_rowan_log': ('oak', 'rowan_logs'),
    'alpine_cottonwood_log': ('birch', 'cottonwood_logs'),
}
# mod leaves -> vanilla sapling (no mod saplings exist; the sapling of the vanilla wood the tree's log makes)
LEAVES = {
    'pine_needles': 'spruce_sapling', 'fir_needles': 'spruce_sapling', 'spruce_boughs': 'spruce_sapling',
    'blue_spruce_boughs': 'spruce_sapling', 'larch_needles': 'spruce_sapling',
    'aspen_leaves': 'birch_sapling', 'golden_aspen_leaves': 'birch_sapling', 'birch_leaves': 'birch_sapling',
    'cottonwood_leaves': 'birch_sapling',
    'maple_leaves': 'oak_sapling', 'autumn_maple_leaves': 'oak_sapling', 'willow_leaves': 'oak_sapling',
    'alder_leaves': 'oak_sapling', 'rowan_leaves': 'oak_sapling',
}
# species planks the reserve builds with (real blocks): crafted from their log in the stonecutter
SPECIES_PLANKS = {'reserve_pine_planks': ('pine_log', 'pine_planks'), 'reserve_cedar_planks': ('cedar_log', 'cedar_planks')}
VANILLA_ROUND = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'cherry', 'mangrove']

SILK = {"condition": "minecraft:match_tool",
        "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}
KEEP = {"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=1)
        f.write('\n')


def remove(path):
    if os.path.exists(path):
        os.remove(path)


# ---- logs: drop themselves (vanilla log loot)
for log in LOGS:
    write(P('data', 'frontierhunts', 'loot_table', 'blocks', log + '.json'), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "frontierhunts:" + log}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "frontierhunts:blocks/" + log})

# ---- leaves: leaves with shears / Silk Touch, else the vanilla sapling + sticks (vanilla oak-leaves chances)
for leaf, sapling in LEAVES.items():
    write(P('data', 'frontierhunts', 'loot_table', 'blocks', leaf + '.json'), {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:alternatives", "children": [
                 {"type": "minecraft:item", "name": "frontierhunts:" + leaf, "conditions": [KEEP]},
                 {"type": "minecraft:item", "name": "minecraft:" + sapling,
                  "conditions": [{"condition": "minecraft:survives_explosion"},
                                 {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                  "chances": [0.05, 0.0625, 0.083333336, 0.1]}]}]}]},
            {"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:item", "name": "minecraft:stick",
                          "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                                        {"function": "minecraft:explosion_decay"}],
                          "conditions": [{"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                                          "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]}]}],
             "conditions": [{"condition": "minecraft:inverted", "term": KEEP}]}],
        "random_sequence": "frontierhunts:blocks/" + leaf})


# ---- tags (fragments deep-merged by the build; lists are unioned)
def tag(ns, name, values):
    write(P('_merge', 'data', ns, 'tags', 'item', name + '.json', FRAGMENT),
          {"replace": False, "values": ["frontierhunts:" + v for v in values]})


for old in ('logs', 'logs_that_burn', 'oak_logs', 'spruce_logs', 'birch_logs'):  # trees2's fragments (superseded)
    remove(P('_merge', 'data', 'minecraft', 'tags', 'item', old + '.json', 'trees2.json'))
tag('minecraft', 'logs', list(LOGS))
tag('minecraft', 'logs_that_burn', list(LOGS))
for family in sorted({f for f, _ in LOGS.values()}):
    tag('minecraft', family + '_logs', [log for log, (f, _) in LOGS.items() if f == family])
for log, (_, species) in LOGS.items():
    write(P('data', 'frontierhunts', 'tags', 'item', species + '.json'), {"values": ["frontierhunts:" + log]})

# ---- recipes
for log, (family, _) in LOGS.items():
    write(P('data', 'frontierhunts', 'recipe', f'{log}_to_vanilla_{family}_log.json'), {
        "type": "minecraft:crafting_shapeless", "category": "building", "group": f"{family}_log",
        "ingredients": [{"item": "frontierhunts:" + log}, {"item": "frontierhunts:" + log}],
        "result": {"id": f"minecraft:{family}_log", "count": 2}})
for rid, (log, planks) in SPECIES_PLANKS.items():
    write(P('data', 'frontierhunts', 'recipe', rid + '.json'), {
        "type": "minecraft:stonecutting",
        "ingredient": {"item": "frontierhunts:" + log},
        "result": {"id": "frontierhunts:" + planks, "count": 4}})
with open(P('_remove', 'wood.txt'), 'w', encoding='utf-8') as f:
    f.write('# [wood] 1 spruce / dark oak log -> 1 pine / cedar log: competed with the vanilla planks recipe for the same\n'
            '# single log in the grid; trees now drop their own pine / cedar logs, so the conversion is not needed\n'
            'data/frontierhunts/recipe/reserve_pine_log.json\n'
            'data/frontierhunts/recipe/reserve_cedar_log.json\n')
for rid in ('reserve_pine_log', 'reserve_cedar_log'):
    remove(P('data', 'frontierhunts', 'advancement', 'recipes', 'unlock', rid + '.json'))

# ---- realistic pack: log items are cubes (the pack draws log blocks round in the world)
pack = ('resourcepacks', 'realistic_world', 'assets')
for wood in VANILLA_ROUND:
    o = 'frontierhunts:block/original/minecraft/block/' + wood
    write(P(*pack, 'minecraft', 'models', 'item', wood + '_log.json'),
          {"parent": "minecraft:block/cube_column", "textures": {"side": o + '_log', "end": o + '_log_top'}})
for log in LOGS:
    write(P(*pack, 'frontierhunts', 'models', 'item', log + '.json'),
          {"parent": "minecraft:block/cube_column",
           "textures": {"side": "frontierhunts:block/" + log, "end": "frontierhunts:block/" + log + "_top"}})
print('wrote wood data under', P())
