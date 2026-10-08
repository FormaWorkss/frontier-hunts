#!/usr/bin/env python3
"""Frontier Hunts / Frontier Structures survival-obtainability audit (offline, runs on a built jar).

usage: python3 tools/recipe_audit.py <jar> [--vanilla <neoforge or client jar>] [--md <out.md>] [--json <out.json>] [-v]

What it does
  1. Item list: every item model (assets/<ns>/models/item/*.json) of frontierhunts + frontierstructures that has a
     display name (item.<ns>.<id> or block.<ns>.<id>) - i.e. every registered item (the integration gate
     tools/check_jar.py already enforces model + lang for registered items).
  2. Validates every recipe JSON of both namespaces: parses, known type, 1.21.1 result format
     ("result": {"id": ..., "count": ...}), shaped patterns (equal row widths, keys used/defined), every referenced
     item exists (mod items / vanilla items) and every referenced tag exists (mod jar tags, vanilla + c: tags).
     Also loot tables (item entries / loot_table references resolve) and recipe-book unlock advancements
     (every recipe-book recipe of our namespaces should be granted by an advancement; missing = warning).
  3. Survival reachability, computed as a fix-point from things a survival player can reach without commands:
       - vanilla items (assumed obtainable),
       - natural world generation: blocks placed by data-driven configured features, blocks of structure templates
         used by worldgen structures (and the chest loot tables in those templates), plus the blocks placed by
         the mod's code-driven features (WORLDGEN_CODE below),
       - mob drops: entity loot tables, and the code drops listed in CODE_SOURCES (harvest, butchering...),
       - recipes: crafting (crafting table), furnace/smoker/campfire/stonecutter/smithing, the mod's Clothing Table
         recipes (frontierhunts:clothing_table, needs the Clothing Table), and the code-driven stations listed in
         CODE_SOURCES (tanning rack, game pole, drying rack, smokehouse, Attachment Workbench fitting ...),
       - block drops once the block can be placed or is generated,
       - CODE_SOURCES entries with their requirements (trades, rewards, transformations).
     An item is OK when reachable. STRUCTURE-ONLY (warning) when its only source is breaking a block out of a
     generated building. UNOBTAINABLE otherwise, unless it is listed in EXCEPTIONS (creative/admin-only, with reason).
  CODE_SOURCES entries carry evidence (a class in the jar and constant-pool tokens it must contain); if the
  class or token disappears the source is reported as STALE and not counted.

Exit status: 1 when any recipe/loot error or unobtainable item is found (exceptions excluded), else 0.
"""
import io, json, os, re, struct, sys, gzip, zipfile
from collections import defaultdict

NAMESPACES = ('frontierhunts', 'frontierstructures')
FH = 'frontierhunts'

# ----------------------------------------------------------------------------------------------------------------
# Intentionally not obtainable in survival (creative / admin / technical / legacy). Keep reasons short and honest.
EXCEPTIONS = {
    f'{FH}:camera_base_station': 'retired (1.3.1): replaced by the Field Phone; kept registered for old worlds, a placed one opens the phone\'s Trail Cams',
    f'{FH}:field_camera': 'retired (1.4.0): replaced by the Field Phone\'s Camera app; kept registered for old inventories, using one puts the phone camera up',
    # [1.3.0] retired workbenches: still registered so old worlds and inventories load; using one converts it
    **{f'{FH}:{b}': 'retired workbench (1.3.0): kept for old worlds, converts into its successor bench when used or placed'
       for b in ('weapons_workbench', 'attachment_workbench', 'ammo_reloader', 'bow_tuning_rack', 'fishing_station',
                 'clothing_workbench', 'tent_bench')},
    # spawn eggs: creative/admin only, like vanilla
    **{f'{FH}:{e}_spawn_egg': 'spawn egg (creative/admin only, like vanilla)' for e in (
        'whitetail', 'whitetail_buck', 'whitetail_doe', 'elk', 'elk_bull', 'elk_cow', 'moose', 'moose_bull', 'moose_cow',
        'bison', 'black_bear', 'boar', 'cheetah', 'cougar', 'coyote', 'duck', 'grizzly', 'grouse', 'lion', 'panther',
        'polar_bear', 'pronghorn', 'wolf')},
    f'{FH}:tracer_arrow': 'legacy item "Tracer Arrow (old)": kept registered so old stacks load; tracers are now arrow '
                          'tips fitted at the Bow Workshop (tracer broadhead / field point / glacier tracer)',
    f'{FH}:field_tent': 'legacy pre-tent-bench kit, hidden from creative and the workshops on purpose (tents are made '
                        'at the Tent Bench); kept registered for old worlds and as the Woodcraft skill icon',
    f'{FH}:paraglider': 'retired at the user\'s request (replaced by the Wingsuit + canopy); item kept registered so old '
                        'stacks still work, recipe retired, not in creative',
    # [onebook] one book: the Frontier Handbook is the only book item; these two screens open from inside it (and J / N)
    f'{FH}:hunter_journal': 'legacy/hidden: the Hunter\'s Journal lives inside the Frontier Handbook (button, or J); item kept '
                            'registered for old worlds, recipe + loot retired, not in creative, old stacks become the Handbook',
    f'{FH}:expedition_guide': 'legacy/hidden: the Expedition journal lives inside the Frontier Handbook (button, or N); item kept '
                              'registered for old worlds, recipe + loot retired, not in creative, old stacks become the Handbook',
}

# Blocks that the mod's code-driven world generation places (custom feature types / terrain code). Block id ->
# (feature, evidence class, tokens). Used only to know which blocks exist naturally in the world.
P = 'com/formaworks/frontierhunts/'


def _wg(blocks, feature, cls, tokens):
    return {f'{FH}:{b}': (feature, P + cls, tokens) for b in blocks}


WORLDGEN_CODE = {
    **_wg(['forest_duff', 'forest_litter', 'undergrowth'], 'ground cover', 'environment/GroundCover',
          ['FOREST_DUFF', 'FOREST_LITTER', 'UNDERGROWTH']),
    **_wg(['mossy_stone'], 'creek edges', 'environment/CreekEdge', ['MOSSY_STONE']),
    **_wg(['shed_antler', 'scattered_bones', 'whitetail_skull', 'elk_skull', 'moose_skull', 'bison_skull'],
          'bone sites', 'ecology/bones/BoneSites', ['SHED_ANTLER', 'SCATTERED_BONES', 'skull']),
    **_wg(['river_pebbles', 'river_stone', 'river_boulder', 'mossy_river_pebbles', 'mossy_river_stone',
           'mossy_river_boulder', 'weathered_river_rock'], 'alpine rivers', 'landscape/AlpineEcology',
          ['BOULDER_IDS', 'WEATHERED_ROCK']),
    **_wg(['alpine_rock'], 'alpine terrain columns', 'landscape/AlpineColumn', ['ROCK']),
    **_wg(['woodland_bush', 'spreading_fern', 'river_brush', 'flowering_bramble', 'arching_fern', 'broadleaf_thicket',
           'huckleberry_shrub', 'juniper_shrub', 'spruce_seedling', 'heather', 'sagebrush', 'cottongrass'],
          'alpine vegetation', 'landscape/AlpineVegetation', ['woodland_bush', 'sagebrush', 'heather']),
    **_wg(['fireweed', 'forest_sticks'], 'alpine vegetation', 'landscape/AlpineVegetation', ['fireweed', 'forest_sticks']),
    **_wg(['alpine_pasture'], 'alpine meadows', 'landscape/AlpineVegetation', ['PASTURE']),
    **_wg(['reeds'], 'alpine lakes', 'landscape/AlpineLakes', ['reeds']),
    **_wg(['branch_stub', 'sapling_pole'], 'alpine forest', 'landscape/AlpineForest', ['branch_stub', 'sapling_pole']),
    # trees (owned by the trees workstreams; listed only so their logs/leaves are known to be natural)
    **{f'{FH}:{b}': ('trees', P + 'landscape/AlpineTrees', [b]) for b in (
        'alpine_spruce_log', 'alpine_pine_log', 'alpine_maple_log', 'alpine_alder_log', 'alpine_rowan_log',
        'alpine_cottonwood_log', 'aspen_log', 'birch_log', 'spruce_boughs', 'golden_aspen_leaves',
        'autumn_maple_leaves', 'blue_spruce_boughs', 'larch_needles', 'alder_leaves', 'rowan_leaves', 'cottonwood_leaves',
        'aspen_leaves', 'birch_leaves', 'maple_leaves', 'pine_needles', 'fir_needles', 'willow_leaves') if b != 'pine_log'},
    f'{FH}:pine_log': ('hunting forest trees', P + 'environment/HuntingForest', ['pine_log']),
    # [wood] forest stands / alpine ecology wild trees (pine and fir/cedar trees, snags, leaners)
    f'{FH}:cedar_log': ('wild trees', P + 'environment/WildTrees', ['cedar_log']),
}

# Code-driven sources. item -> list of dicts: how (shown in the table), kind, needs (items that must be obtainable
# first, e.g. the station block or the input), evidence class + tokens that must be in its constant pool.
def _src(how, kind, cls, tokens, needs=()):
    return {'how': how, 'kind': kind, 'cls': P + cls, 'tokens': list(tokens), 'needs': list(needs)}


SKIN = (f'{FH}:skinning_tool',)
_DEER = ('field dressing a whitetail / elk / moose with the Skinning Tool', 'harvest', 'hunting/Whitetail')
_WILD = ('2026 wildlife drops (Frontier Survival harvest)', 'mob', 'survival/SurvivalHarvest')
_TAN = ('Tanning Rack', 'station', 'survival/SurvivalStations')
_SHOP = ('outfitter counter (expedition tokens)', 'trade', 'economy/ShopPrices')  # [1.1.7] the counter list is ShopPrices.ADDED
CODE_SOURCES = {
    f'{FH}:venison': [_src(_DEER[0], *_DEER[1:], ['VENISON'], [SKIN]),
                      _src('butchering a quarter with a knife (crafting grid)', 'craft', 'hunting/ButcherRecipe', ['VENISON'],
                           [(f'{FH}:venison_quarter', f'{FH}:aged_venison'), (f'{FH}:skinning_tool', f'{FH}:field_knife')])],
    f'{FH}:venison_quarter': [_src(_DEER[0], *_DEER[1:], ['VENISON_QUARTER'], [SKIN])],
    f'{FH}:backstrap': [_src(_DEER[0], *_DEER[1:], ['BACKSTRAP'], [SKIN])],
    f'{FH}:whitetail_trophy': [_src(_DEER[0] + ' (the trophy of the animal)', *_DEER[1:], ['WHITETAIL_TROPHY'], [SKIN])],
    f'{FH}:deer_hide': [_src('field dressing a whitetail (Skinning Tool)', 'harvest', 'survival/SurvivalHarvest', ['DEER_HIDE'], [SKIN]),
                        _src('pronghorn drop', 'mob', 'survival/SurvivalHarvest', ['DEER_HIDE'])],
    f'{FH}:heavy_hide': [_src('field dressing an elk / moose (Skinning Tool)', 'harvest', 'survival/SurvivalHarvest', ['HEAVY_HIDE'], [SKIN]),
                         _src('bison drop', 'mob', 'survival/SurvivalHarvest', ['HEAVY_HIDE'])],
    f'{FH}:organ_meat': [_src(_DEER[0] + ' / wildlife drops', 'harvest', 'survival/SurvivalHarvest', ['ORGAN_MEAT'])],
    f'{FH}:game_fat': [_src('fat trimmings from harvests and wildlife drops (by season)', 'harvest', 'survival/SurvivalHarvest', ['GAME_FAT'])],
    f'{FH}:game_meat': [_src('bison / boar / pronghorn / predators', *_WILD[1:], ['GAME_MEAT'])],
    f'{FH}:bear_meat': [_src('black bear / grizzly / polar bear', *_WILD[1:], ['BEAR_MEAT'])],
    f'{FH}:wild_fowl': [_src('ducks and grouse', *_WILD[1:], ['WILD_FOWL'])],
    f'{FH}:bear_pelt': [_src('black bear / grizzly / polar bear (always one pelt)', *_WILD[1:], ['BEAR_PELT'])],
    f'{FH}:fur_pelt': [_src('wolf / coyote / cougar / panther / lion / cheetah (always one pelt)', *_WILD[1:], ['FUR_PELT'])],
    f'{FH}:tanned_hide': [_src('Tanning Rack: whitetail hide + bone', 'station', 'camp/CampStation', ['TANNED_HIDE'],
                               [f'{FH}:tanning_rack', f'{FH}:deer_hide'])],
    f'{FH}:tanned_heavy_hide': [_src(_TAN[0] + ': heavy hide + bone', 'station', _TAN[2], ['TANNED_HEAVY_HIDE'],
                                     [f'{FH}:tanning_rack', f'{FH}:heavy_hide'])],
    f'{FH}:tanned_fur': [_src(_TAN[0] + ': fur pelt', 'station', _TAN[2], ['TANNED_FUR'], [f'{FH}:tanning_rack', f'{FH}:fur_pelt'])],
    f'{FH}:bear_fur': [_src(_TAN[0] + ': bear pelt -> 2', 'station', _TAN[2], ['BEAR_FUR'], [f'{FH}:tanning_rack', f'{FH}:bear_pelt'])],
    f'{FH}:aged_venison': [_src('Game Pole: hang a venison quarter in cool weather', 'station', 'camp/GamePole', ['AGED_VENISON'],
                                [f'{FH}:game_pole', f'{FH}:venison_quarter'])],
    f'{FH}:jerky': [_src('Drying Rack: hang strips of raw game', 'station', 'survival/block/DryingRackEntity', ['JERKY'],
                         [f'{FH}:drying_rack', (f'{FH}:venison', f'{FH}:game_meat', f'{FH}:backstrap')])],
    f'{FH}:spoiled_meat': [_src('any raw or cooked meat left past its shelf life', 'spoilage', 'survival/Perishable', ['SPOILED_MEAT'],
                                [(f'{FH}:venison', f'{FH}:game_meat', 'minecraft:beef')])],
    f'{FH}:cooked_venison': [_src('Smokehouse (venison + coal)', 'station', 'camp/CampStation', ['COOKED_VENISON'],
                                  [f'{FH}:smokehouse', f'{FH}:venison'])],
    **{f'{FH}:{i}': [_src(_SHOP[0], *_SHOP[1:], [i], [f'{FH}:expedition_board'])] for i in (
        # [1.1.7] the counter only sells what has no recipe (economy/ShopPrices.ADDED)
        'hound_lead', 'tracking_lamp', 'estrus_lure', 'milkweed_pods', 'flagging_tape', 'coffee_thermos',
        'hand_warmers')},
    # [licence] licence counter (journal page, at an expedition board / contract board / lodge stores) and tag filling
    **{f'{FH}:{i}': [_src('licence counter (tokens or emeralds, journal: Licence & Tags)', 'trade', 'licence/LicenceOffice',
                          ['expedition_board', 'contract_board'], [(f'{FH}:expedition_board', f'{FH}:contract_board', f'{FH}:lodge_stores', f'{FH}:ranger_window')])]
       for i in ('hunting_licence', 'deer_tag', 'elk_tag', 'moose_tag', 'pronghorn_tag', 'bison_tag', 'bear_tag', 'cat_tag',
                 'upland_stamp', 'waterfowl_stamp')},
    f'{FH}:filled_tag': [_src('a big-game tag filled when its animal is claimed', 'harvest', 'licence/Tagging', ['FILLED_TAG'],
                              [(f'{FH}:deer_tag', f'{FH}:bear_tag')])],
}

# made only at the Clothing Table (no crafting-table recipe allowed; also hidden from the creative tabs in code:
# recipes/ClothingTableItems)
STATION_ONLY = [f'{FH}:{i}' for i in ('fur_hat', 'buckskin_coat', 'bear_fur_coat', 'hide_robe', 'buckskin_leggings',
                                      'fur_mukluks', 'fur_lining', 'fur_mittens')]

RECIPE_BOOK_TYPES = {
    'minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:smelting', 'minecraft:smoking',
    'minecraft:blasting', 'minecraft:campfire_cooking', 'minecraft:stonecutting', 'minecraft:smithing_transform',
}
COOKING = {'minecraft:smelting': 'minecraft:furnace', 'minecraft:smoking': 'minecraft:smoker',
           'minecraft:blasting': 'minecraft:blast_furnace', 'minecraft:campfire_cooking': 'minecraft:campfire'}
# custom recipe types: type -> (station item, shaped?)  (data-driven station recipes)
STATION_TYPES = {f'{FH}:clothing_table': (f'{FH}:frontier_workbench', True),  # [1.3.0] Frontier Workbench, Clothing tab
                 f'{FH}:camp_cooking': (f'{FH}:dutch_oven', False)}  # [licence] Camp Dutch Oven (shapeless, bowls)
# [1.3.0] bench-only recipes: made at the Frontier Workbench, Gunsmith's Bench or Reloading Bench
BENCH_TYPES = {f'{FH}:bench_shaped', f'{FH}:bench_shapeless'}
BENCHES = [f'{FH}:frontier_workbench', f'{FH}:gunsmith_bench', f'{FH}:reloading_bench']
# special (code-defined) crafting recipes; their outputs are described in CODE_SOURCES
SPECIAL_TYPES = {f'{FH}:butcher', f'{FH}:arrow_refit', f'{FH}:fur_lining', f'{FH}:salt_cure'}
DEFAULT_VANILLA = '/home/claude/fh/lib/neoforge-21.1.248.jar'


# ----------------------------------------------------------------------------------------------------------------
def class_strings(data):
    """UTF8 constants of a class file."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return []
    n = struct.unpack('>H', data[8:10])[0]
    i, idx, out = 10, 1, []
    while idx < n:
        t = data[i]
        if t == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            out.append(data[i + 3:i + 3 + ln].decode('utf-8', 'replace'))
            i += 3 + ln
        elif t in (3, 4):
            i += 5
        elif t in (5, 6):
            i += 9; idx += 1
        elif t in (7, 8, 16, 19, 20):
            i += 3
        elif t in (9, 10, 11, 12, 17, 18):
            i += 5
        elif t == 15:
            i += 4
        else:
            raise ValueError(f'bad constant tag {t}')
        idx += 1
    return out


def nbt_parse(raw):
    d = gzip.decompress(raw)
    pos = [0]

    def u(fmt, n):
        v = struct.unpack('>' + fmt, d[pos[0]:pos[0] + n])[0]; pos[0] += n; return v

    def string():
        n = u('H', 2); s = d[pos[0]:pos[0] + n].decode('utf-8', 'replace'); pos[0] += n; return s

    def payload(t):
        if t == 1: return u('b', 1)
        if t == 2: return u('h', 2)
        if t == 3: return u('i', 4)
        if t == 4: return u('q', 8)
        if t == 5: return u('f', 4)
        if t == 6: return u('d', 8)
        if t == 7: n = u('i', 4); pos[0] += n; return None
        if t == 8: return string()
        if t == 9:
            et = u('b', 1); n = u('i', 4); return [payload(et) for _ in range(n)]
        if t == 10:
            o = {}
            while True:
                tt = u('b', 1)
                if tt == 0: return o
                k = string(); o[k] = payload(tt)
        if t == 11: n = u('i', 4); pos[0] += 4 * n; return None
        if t == 12: n = u('i', 4); pos[0] += 8 * n; return None
        raise ValueError(f'nbt tag {t}')
    t = u('b', 1); string()
    return payload(t)


def rid(s, default='minecraft'):
    s = s.strip().lstrip('#')
    return s if ':' in s else f'{default}:{s}'


class Audit:
    def __init__(self, jar, vanilla):
        self.z = zipfile.ZipFile(jar)
        self.names = set(self.z.namelist())
        self.vz = zipfile.ZipFile(vanilla) if vanilla and os.path.exists(vanilla) else None
        self.errors, self.warnings = [], []
        self.lang = {}
        for ns in NAMESPACES:
            self.lang.update(self.jload(f'assets/{ns}/lang/en_us.json') or {})
        self.items = self.collect_items()
        self.vanilla_items = self.collect_vanilla_items()
        self.tags = self.collect_tags()
        self.vanilla_tags = self.collect_vanilla_tags()
        self.sources = defaultdict(list)     # item -> [(kind, text)]
        self.rules = []                      # (output item, needs: list of alternatives-lists, kind, text)
        self.loot_tool = {}                  # (loot table, item) -> 'shears' / 'silk touch' when the drop needs it

    # -------------------------------------------------------------------------------------------- io
    def jload(self, path, z=None):
        z = z or self.z
        try:
            return json.loads(z.read(path).decode('utf-8'))
        except KeyError:
            return None
        except Exception as e:
            self.errors.append(f'[json] {path}: cannot parse ({e})')
            return None

    def name(self, item):
        ns, p = item.split(':', 1)
        return self.lang.get(f'item.{ns}.{p}') or self.lang.get(f'block.{ns}.{p}') or item

    # -------------------------------------------------------------------------------------------- inventories
    def collect_items(self):
        out = set()
        for n in self.names:
            m = re.fullmatch(r'assets/([a-z0-9_]+)/models/item/([a-z0-9_]+)\.json', n)
            if m and m.group(1) in NAMESPACES:
                ns, p = m.groups()
                if f'item.{ns}.{p}' in self.lang or f'block.{ns}.{p}' in self.lang:
                    out.add(f'{ns}:{p}')
        return out

    def collect_vanilla_items(self):
        ids = set()
        if not self.vz:
            self.warnings.append('[setup] vanilla jar not found: minecraft: item ids and vanilla tags are not verified')
            return None
        for cls in ('net/minecraft/world/item/Items.class', 'net/minecraft/world/level/block/Blocks.class'):
            try:
                ids |= {f'minecraft:{s}' for s in class_strings(self.vz.read(cls)) if re.fullmatch(r'[a-z0-9_]+', s)}
            except KeyError:
                pass
        return ids

    def collect_tags(self):
        raw = {}
        for z in (self.vz, self.z):
            if not z:
                continue
            for n in z.namelist():
                m = re.fullmatch(r'data/([a-z0-9_.-]+)/tags/items?/(.+)\.json', n)
                if not m:
                    continue
                t = f'{m.group(1)}:{m.group(2)}'
                j = self.jload(n, z) or {}
                vals = []
                for v in j.get('values', []):
                    if isinstance(v, dict):
                        vals.append((v.get('id'), v.get('required', True)))
                    else:
                        vals.append((v, True))
                if j.get('replace') or t not in raw:
                    raw[t] = vals
                else:
                    raw[t] = raw[t] + vals
        return raw

    def collect_vanilla_tags(self):
        """Vanilla item tag names (their contents are generated by the game, not shipped as data in the dev jar)."""
        if not self.vz:
            return set()
        try:
            return {f'minecraft:{s}' for s in class_strings(self.vz.read('net/minecraft/tags/ItemTags.class'))
                    if re.fullmatch(r'[a-z0-9_/]+', s)}
        except KeyError:
            return set()

    def tag_items(self, tag, seen=None):
        seen = seen or set()
        if tag in seen or tag not in self.tags:
            return set()
        seen.add(tag)
        out = {'#' + tag} if tag in self.vanilla_tags else set()  # vanilla part of a tag the mod extends
        for v, req in self.tags[tag]:
            if v.startswith('#'):
                if rid(v) not in self.tags and rid(v) in self.vanilla_tags:
                    out.add('#' + rid(v))
                else:
                    out |= self.tag_items(rid(v), seen)
            else:
                out.add(rid(v))
        return out

    def item_exists(self, i):
        ns = i.split(':')[0]
        if ns in NAMESPACES:
            return i in self.items
        if ns == 'minecraft':
            return self.vanilla_items is None or i in self.vanilla_items
        return False  # other mods: not available

    # -------------------------------------------------------------------------------------------- ingredients
    def ingredient(self, ing, where):
        """-> set of item ids that satisfy it (validates)."""
        if isinstance(ing, list):
            out = set()
            for x in ing:
                out |= self.ingredient(x, where)
            return out
        if not isinstance(ing, dict):
            if isinstance(ing, str):  # bare id string (accepted by some codecs) - treat as item
                ing = {'item': ing}
            else:
                self.errors.append(f'[recipe] {where}: bad ingredient {ing!r}')
                return set()
        if 'item' in ing:
            i = rid(ing['item'])
            if not self.item_exists(i):
                self.errors.append(f'[recipe] {where}: unknown item {i}')
                return set()
            return {i}
        if 'tag' in ing:
            t = rid(ing['tag'])
            if t not in self.tags and t in self.vanilla_tags:
                return {'#' + t}  # vanilla tag: contents are code-generated, only its existence is checked
            if t not in self.tags:
                if self.vz or t.split(':')[0] in NAMESPACES:
                    self.errors.append(f'[recipe] {where}: unknown item tag #{t}')
                return set()
            items = {i for i in self.tag_items(t) if i.startswith('#') or self.item_exists(i)}
            if not items:
                self.errors.append(f'[recipe] {where}: tag #{t} has no existing items')
            return items
        if ing.get('type') == 'neoforge:components':
            its = ing.get('items')
            its = [its] if isinstance(its, str) else (its or [])
            return self.ingredient([{'tag': x[1:]} if x.startswith('#') else {'item': x} for x in its], where)
        if ing.get('type') in ('neoforge:compound',):
            return self.ingredient(ing.get('children', []), where)
        self.errors.append(f'[recipe] {where}: unsupported ingredient {json.dumps(ing)[:120]}')
        return set()

    def result(self, j, where):
        r = j.get('result')
        if isinstance(r, dict):
            if 'item' in r and 'id' not in r:
                self.errors.append(f'[recipe] {where}: result uses "item" (1.20.4 format); 1.21.1 needs "id"')
                return None
            i = r.get('id')
            if not isinstance(i, str):
                self.errors.append(f'[recipe] {where}: result has no id')
                return None
            if 'count' in r and (not isinstance(r['count'], int) or not 1 <= r['count'] <= 99):
                self.errors.append(f'[recipe] {where}: bad result count {r["count"]!r}')
            i = rid(i)
            if not self.item_exists(i):
                self.errors.append(f'[recipe] {where}: result item {i} does not exist')
                return None
            return i
        self.errors.append(f'[recipe] {where}: result must be an object {{"id": ..., "count": ...}}')
        return None

    # -------------------------------------------------------------------------------------------- recipes
    def scan_recipes(self):
        self.recipe_ids = {}
        self.sewing = []
        for n in sorted(self.names):
            m = re.fullmatch(r'data/([a-z0-9_]+)/recipes?/(.+)\.json', n)
            if not m or m.group(1) not in NAMESPACES:
                continue
            if '/recipes/' in n:
                self.errors.append(f'[recipe] {n}: 1.21 reads data/<ns>/recipe/ (singular); this file is ignored')
                continue
            rid_ = f'{m.group(1)}:{m.group(2)}'
            j = self.jload(n)
            if j is None:
                continue
            t = rid(j.get('type', '?'))
            self.recipe_ids[rid_] = t
            where = f'recipe {rid_}'
            if 'neoforge:conditions' in j:
                self.warnings.append(f'[recipe] {where}: has load conditions (assumed met)')
            if t in SPECIAL_TYPES:
                continue
            if t in ('minecraft:crafting_shaped', f'{FH}:bench_shaped') or t in STATION_TYPES and STATION_TYPES[t][1]:
                pat, key = j.get('pattern'), j.get('key', {})
                if not pat or not isinstance(pat, list) or len(pat) > 3:
                    self.errors.append(f'[recipe] {where}: bad pattern {pat!r}'); continue
                if len({len(r) for r in pat}) != 1 or max(len(r) for r in pat) > 3:
                    self.errors.append(f'[recipe] {where}: pattern rows must have equal width <= 3: {pat}'); continue
                used = set(''.join(pat)) - {' '}
                if used - set(key):
                    self.errors.append(f'[recipe] {where}: pattern symbols without key {sorted(used - set(key))}')
                if set(key) - used:
                    self.errors.append(f'[recipe] {where}: unused keys {sorted(set(key) - used)}')
                slots = [self.ingredient(key[c], where) for row in pat for c in row if c != ' ' and c in key]
            elif t in ('minecraft:crafting_shapeless', f'{FH}:bench_shapeless'):
                ings = j.get('ingredients', [])
                if not 1 <= len(ings) <= 9:
                    self.errors.append(f'[recipe] {where}: {len(ings)} ingredients')
                slots = [self.ingredient(x, where) for x in ings]
            elif t in COOKING or t == 'minecraft:stonecutting':
                slots = [self.ingredient(j.get('ingredient'), where)]
            elif t == 'minecraft:smithing_transform':
                slots = [self.ingredient(j.get(k), where) for k in ('template', 'base', 'addition')]
            elif t in STATION_TYPES:
                slots = [self.ingredient(x, where) for x in j.get('ingredients', [])]
            else:
                self.errors.append(f'[recipe] {where}: unknown recipe type {t}')
                continue
            out = self.result(j, where)
            if out is None:
                continue
            if t in STATION_TYPES and j.get('sew'):
                if j['sew'] not in ('lining', 'mittens'):
                    self.errors.append(f'[recipe] {where}: unknown sew mode {j["sew"]!r}')
                self.sewing.append(rid_)
                continue  # sews into the held garment; its result is only the list icon
            if any(not s for s in slots):
                continue  # already reported
            station = {'minecraft:crafting_shaped': 'minecraft:crafting_table',
                       'minecraft:crafting_shapeless': 'minecraft:crafting_table',
                       'minecraft:stonecutting': 'minecraft:stonecutter',
                       'minecraft:smithing_transform': 'minecraft:smithing_table'}.get(t) or COOKING.get(t) \
                or STATION_TYPES.get(t, (None,))[0]
            needs = [s for s in slots] + ([{station}] if station and not station.startswith('minecraft:') else [])
            if t in BENCH_TYPES:  # [1.3.0] made at one of the three benches (each crafted from vanilla materials)
                needs.append(set(BENCHES))
            label = {'minecraft:crafting_shaped': 'crafting', 'minecraft:crafting_shapeless': 'crafting',
                     'minecraft:smelting': 'furnace', 'minecraft:smoking': 'smoker', 'minecraft:blasting': 'blast furnace',
                     'minecraft:campfire_cooking': 'campfire', 'minecraft:stonecutting': 'stonecutter',
                     'minecraft:smithing_transform': 'smithing', f'{FH}:clothing_table': 'Clothing Table',
                     f'{FH}:camp_cooking': 'Dutch oven', f'{FH}:bench_shaped': 'bench', f'{FH}:bench_shapeless': 'bench'}.get(t, t)
            self.rules.append((out, needs, 'recipe', f'{label} ({rid_})'))
            self.sources[out].append(('recipe', f'{label}: {rid_}'))

    def scan_advancements(self):
        granted = set()
        for n in self.names:
            m = re.fullmatch(r'data/([a-z0-9_]+)/advancements?/(.+)\.json', n)
            if not m:
                continue
            j = self.jload(n) or {}
            for r in (j.get('rewards') or {}).get('recipes', []):
                granted.add(rid(r))
            if m.group(1) in NAMESPACES:
                for cname, c in (j.get('criteria') or {}).items():
                    for pred in ((c.get('conditions') or {}).get('items') or []):
                        its = pred.get('items') if isinstance(pred, dict) else None
                        for x in ([its] if isinstance(its, str) else (its or [])):
                            if x.startswith('#'):
                                t = rid(x[1:])
                                if t not in self.tags and t not in self.vanilla_tags:
                                    self.errors.append(f'[advancement] {n} {cname}: unknown tag {x}')
                            elif not self.item_exists(rid(x)):
                                self.errors.append(f'[advancement] {n} {cname}: unknown item {x}')
        self.no_unlock = sorted(r for r, t in self.recipe_ids.items() if t in RECIPE_BOOK_TYPES and r not in granted)
        for r in sorted(granted):
            if r.split(':')[0] in NAMESPACES and r not in self.recipe_ids:
                self.errors.append(f'[advancement] grants missing recipe {r}')

    # -------------------------------------------------------------------------------------------- loot
    def loot_items(self, table, where, seen=None):
        """All items a loot table can produce (validating names)."""
        seen = seen or set()
        if table in seen:
            return set()
        seen.add(table)
        ns, p = table.split(':', 1)
        j = self.jload(f'data/{ns}/loot_table/{p}.json')
        if j is None:
            if ns in NAMESPACES:
                self.errors.append(f'[loot] {where}: loot table {table} missing')
            return set()
        out = set()

        def walk(o):
            if isinstance(o, dict):
                ty = rid(o.get('type', '')) if isinstance(o.get('type'), str) else ''
                if ty == 'minecraft:item' and 'name' in o:
                    i = rid(o['name'])
                    if self.item_exists(i):
                        out.add(i)
                        cj = json.dumps(o.get('conditions', []))
                        tool = ' or '.join(t for k, t in (('shears', 'shears'), ('silk_touch', 'silk touch')) if k in cj)
                        if tool:
                            self.loot_tool[(table, i)] = tool
                    else:
                        self.errors.append(f'[loot] {table}: unknown item {i}')
                elif ty == 'minecraft:tag' and 'name' in o:
                    out.update(self.tag_items(rid(o['name'])))
                elif ty == 'minecraft:loot_table' and isinstance(o.get('value'), str):
                    out.update(self.loot_items(rid(o['value']), table, seen))
                for k, v in o.items():
                    if k != 'conditions':
                        walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)
        walk(j)
        return out

    def scan_loot(self):
        self.block_drops = {}
        for n in sorted(self.names):
            m = re.fullmatch(r'data/([a-z0-9_]+)/loot_tables?/(.+)\.json', n)
            if not m:
                continue
            ns, p = m.groups()
            table = f'{ns}:{p}'
            if '/loot_tables/' in n:
                self.errors.append(f'[loot] {n}: 1.21 reads data/<ns>/loot_table/ (singular)')
                continue
            items = self.loot_items(table, n)
            if p.startswith('blocks/'):
                self.block_drops[f'{ns}:{p[7:]}'] = items
            elif p.startswith('entities/'):
                if ns in NAMESPACES or ns == 'minecraft':
                    for i in items:
                        self.rules.append((i, [], 'mob', f'mob drop ({table})'))
                        self.sources[i].append(('mob', f'mob drop: {table}'))
            elif p.startswith('chests/'):
                self.chest_tables = getattr(self, 'chest_tables', {})
                self.chest_tables[table] = items
            elif p.startswith('gameplay/'):
                for i in items:
                    self.rules.append((i, [], 'gameplay', f'{table}'))
                    self.sources[i].append(('gameplay', table))

    # -------------------------------------------------------------------------------------------- world generation
    def scan_worldgen(self):
        natural, why = set(), {}
        # data-driven configured features: every block state "Name" inside
        for n in self.names:
            m = re.fullmatch(r'data/([a-z0-9_]+)/worldgen/configured_feature/(.+)\.json', n)
            if not m:
                continue
            j = self.jload(n) or {}
            for b in re.findall(r'"Name"\s*:\s*"([a-z0-9_:/]+)"', json.dumps(j)):
                natural.add(rid(b)); why.setdefault(rid(b), f'worldgen feature {m.group(1)}:{m.group(2)}')
        # structures used by worldgen -> templates
        templates = set()
        for n in self.names:
            m = re.fullmatch(r'data/([a-z0-9_]+)/worldgen/structure/(.+)\.json', n)
            if m:
                t = (self.jload(n) or {}).get('template')
                if isinstance(t, str):
                    templates.add(rid(t))
        self.structure_blocks, chests = set(), set()
        for t in sorted(templates):
            ns, p = t.split(':', 1)
            path = f'data/{ns}/structure/{p}.nbt'
            if path not in self.names:
                self.errors.append(f'[worldgen] structure template {t} missing')
                continue
            nb = nbt_parse(self.z.read(path))
            pals = nb.get('palettes') or [nb.get('palette') or []]
            for pal in pals:
                for e in pal or []:
                    b = rid(e.get('Name', 'minecraft:air'))
                    self.structure_blocks.add(b); why.setdefault(b, f'generated building {t}')
            for b in nb.get('blocks', []):
                lt = (b.get('nbt') or {}).get('LootTable')
                if isinstance(lt, str):
                    chests.add(rid(lt))
        for b, (feature, cls, toks) in WORLDGEN_CODE.items():
            if self.evidence(cls, toks, f'worldgen {b}'):
                natural.add(b); why.setdefault(b, f'world generation ({feature})')
        self.natural_blocks, self.natural_why = natural, why
        for t in sorted(chests):
            for i in self.loot_items(t, 'structure chest'):
                self.rules.append((i, [], 'chest', f'settlement chest loot ({t})'))
                self.sources[i].append(('chest', f'settlement chest: {t}'))

    # -------------------------------------------------------------------------------------------- code sources
    def evidence(self, cls, tokens, what):
        path = cls if cls.endswith('.class') else cls.replace('.', '/') + '.class'
        if path not in self.names:
            self.errors.append(f'[code] {what}: evidence class {path} not in jar (source is STALE)')
            return False
        strs = set()
        for n in self.names:  # the whole class family (Name.class + Name$*.class)
            if n == path or n.startswith(path[:-len('.class')] + '$') and n.endswith('.class'):
                strs |= set(class_strings(self.z.read(n)))
        missing = [t for t in tokens if t not in strs]
        if missing:
            self.errors.append(f'[code] {what}: {path} no longer contains {missing} (source is STALE)')
            return False
        return True

    def scan_code(self):
        for item, lst in CODE_SOURCES.items():
            if item not in self.items and not item.startswith('minecraft:'):
                self.errors.append(f'[code] CODE_SOURCES lists unknown item {item}')
                continue
            for s in lst:
                if not self.evidence(s['cls'], s.get('tokens', []), f'{item} <- {s["how"]}'):
                    continue
                needs = [{n} if isinstance(n, str) else set(n) for n in s.get('needs', [])]
                self.rules.append((item, needs, s['kind'], s['how']))
                self.sources[item].append((s['kind'], s['how']))

    # -------------------------------------------------------------------------------------------- solve
    def solve(self):
        have = set(self.vanilla_items or ())
        have |= {'minecraft:crafting_table', 'minecraft:furnace', 'minecraft:smoker', 'minecraft:campfire',
                 'minecraft:stonecutter', 'minecraft:smithing_table', 'minecraft:blast_furnace'}
        have |= {'#' + t for t in self.vanilla_tags}
        how = {}
        # natural blocks -> drops (no placement needed); natural blocks that drop themselves count as terrain
        block_rules = []
        for b, drops in self.block_drops.items():
            for d in drops:
                tool = self.loot_tool.get((f'{b.split(":")[0]}:blocks/{b.split(":")[1]}', d))
                tool = f' with {tool}' if tool else ''
                if b in self.natural_blocks:
                    self.rules.append((d, [], 'worldgen', f'breaking natural {b.split(":")[1]}{tool} ({self.natural_why[b]})'))
                    self.sources[d].append(('worldgen', f'breaking natural {b}{tool} ({self.natural_why[b]})'))
                elif b in self.structure_blocks:
                    self.sources[d].append(('structure', f'breaking {b} found in {self.natural_why[b]}'))
                    block_rules.append((d, [], 'structure', f'salvaged from {self.natural_why[b]}'))
                if d != b:  # placed block -> other drops (leaves -> saplings, etc.)
                    self.rules.append((d, [{b}], 'block', f'breaking placed {b}'))
        changed = True
        rules = self.rules
        while changed:
            changed = False
            for out, needs, kind, text in rules:
                if out in have:
                    continue
                if all(any(x in have for x in alt) for alt in needs):
                    have.add(out); how[out] = (kind, text); changed = True
        self.have, self.how = have, how
        self.blocked = defaultdict(list)
        for out, needs, kind, text in rules:
            if out not in have:
                miss = [sorted(alt)[0] + (' (or other)' if len(alt) > 1 else '') for alt in needs
                        if not any(x in have for x in alt)]
                self.blocked[out].append(f'{text} needs {", ".join(miss)}')
        # structure salvage as a last resort (reported as a warning)
        self.structure_only = set()
        for out, needs, kind, text in block_rules:
            if out not in have:
                self.structure_only.add(out)
                how[out] = (kind, text)

    # -------------------------------------------------------------------------------------------- report
    def run(self):
        self.scan_recipes()
        self.scan_advancements()
        self.scan_loot()
        self.scan_worldgen()
        self.scan_code()
        self.solve()
        rows = []
        for i in sorted(self.items):
            if i in self.have:
                st = 'OK'
            elif i in EXCEPTIONS:
                st = 'EXCEPTION'
            elif i in self.structure_only:
                st = 'STRUCTURE-ONLY'
            else:
                st = 'UNOBTAINABLE'
            srcs = [t for k, t in self.sources.get(i, [])]
            rows.append({'item': i, 'name': self.name(i), 'status': st,
                         'via': self.how.get(i, ('', ''))[1] if st == 'OK' else (EXCEPTIONS.get(i, '') if st == 'EXCEPTION' else ''),
                         'sources': srcs})
        for i in STATION_ONLY:
            bad = [t for k, t in self.sources.get(i, []) if k == 'recipe' and not t.startswith('Clothing Table')]
            if bad:
                self.errors.append(f'[station-only] {i} must only be made at the Clothing Table but also has: {bad}')
            if not any(t.startswith('Clothing Table') for k, t in self.sources.get(i, [])):
                self.errors.append(f'[station-only] {i} has no Clothing Table recipe')
        for e in EXCEPTIONS:
            if e not in self.items:
                self.warnings.append(f'[exceptions] {e} is not a registered item any more')
            elif e in self.have:
                self.warnings.append(f'[exceptions] {e} is listed as an exception but is obtainable')
        self.rows = rows
        return rows


def main():
    args = [a for a in sys.argv[1:]]
    if not args or args[0].startswith('-'):
        sys.exit(__doc__)
    jar = args[0]
    opt = lambda k, d=None: args[args.index(k) + 1] if k in args else d
    a = Audit(jar, opt('--vanilla', DEFAULT_VANILLA))
    rows = a.run()
    counts = defaultdict(int)
    for r in rows:
        counts[r['status']] += 1
    bad = [r for r in rows if r['status'] in ('UNOBTAINABLE',)]
    so = [r for r in rows if r['status'] == 'STRUCTURE-ONLY']
    print(f'items: {len(rows)}  ' + '  '.join(f'{k}: {v}' for k, v in sorted(counts.items())))
    print(f'recipes: {len(a.recipe_ids)}  recipe-book recipes without an unlock advancement: {len(a.no_unlock)}')
    for e in a.errors:
        print('ERROR', e)
    for w in a.warnings:
        print('WARN ', w)
    for r in so:
        print('WARN  [structure-only]', r['item'], '-', r['name'], '|', '; '.join(a.blocked.get(r['item'], [])))
    for r in bad:
        print('UNOBTAINABLE', r['item'], '-', r['name'], '|', '; '.join(a.blocked.get(r['item'], [])) or 'no source')
    if '-v' in args:
        for r in a.no_unlock:
            print('NO-UNLOCK', r)
        for r in rows:
            print(f"{r['status']:14} {r['item']:50} {r['via']}")
    if opt('--json'):
        json.dump({'rows': rows, 'errors': a.errors, 'warnings': a.warnings, 'no_unlock': a.no_unlock,
                   'exceptions': EXCEPTIONS}, open(opt('--json'), 'w'), indent=1)
    if opt('--md'):
        with open(opt('--md'), 'w') as f:
            f.write('| item | name | status | survival source (first found) | all sources |\n|---|---|---|---|---|\n')
            for r in rows:
                f.write(f"| `{r['item']}` | {r['name']} | {r['status']} | {r['via']} | {'; '.join(r['sources'][:4])}"
                        f"{' …' if len(r['sources']) > 4 else ''} |\n")
    print('RESULT:', 'FAIL' if a.errors or bad else 'PASS', f'({len(bad)} unobtainable, {len(a.errors)} errors, '
          f'{len(so)} structure-only, {counts["EXCEPTION"]} documented exceptions)')
    sys.exit(1 if a.errors or bad else 0)


if __name__ == '__main__':
    main()
