#!/usr/bin/env python3
"""[economy] Frontier Hunts recipe value audit (offline, runs on a built jar).

usage: python3 tools/economy/value_model.py <jar> [--repo <repo>] [--vanilla <minecraft resources jar>]
                                              [--md <out.md>] [--json <out.json>] [--before <before.json>]

  * Reads every recipe of frontierhunts + frontierstructures from the jar (crafting, Clothing Table, cooking,
    stonecutting) and prices each one with the effort model in tools/economy/model.py: raw materials have a BASE value
    in effort points (pt, 1 iron ingot = 10 pt), crafted intermediates are priced from their own cheapest recipe.
  * Compares the cost of one crafted unit with the item's TARGET (tier + vanilla analog):
      ratio < 0.6 -> TOO CHEAP, ratio > 1.7 -> TOO EXPENSIVE (decor/building blocks: < 0.33 / > 3).
    Early-game essentials additionally must not need anything beyond wood/stone/flint/string/leather/feathers/
    copper and <= 1 iron ingot (STARTER/EARLY rules below).
  * Crafting-grid conflicts: two shapeless recipes with the same ingredients, or two shaped recipes with the same
    pattern (incl. mirror), between mod recipes and against vanilla recipes (--vanilla, default: the QA server's
    minecraft resources jar). A conflict means the grid can only ever make one of them.
  * Token economy: reads the expedition shop prices (src/.../economy/ShopPrices.java) and the ranger board trades
    (src/.../camp/ContractMenu.java) from --repo and compares them with crafted value / PT_PER_TOKEN.
Exit status 1 when a mod-mod or mod-vanilla grid conflict or an unrated crafted item is found.
"""
import json, os, re, sys, zipfile
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from model import BASE, TARGET, TIER_ORDER, PT_PER_TOKEN, TOKENS_PER_HUNT, HUNTS_PER_HOUR  # noqa: E402
try:
    from gen_recipes import NEW as _NEW  # noqa: E402
    WHY = {k: v[1] for k, v in _NEW.items()}
except Exception:  # pragma: no cover
    WHY = {}

NS = ('frontierhunts', 'frontierstructures')
VANILLA_DEFAULT = '/home/claude/qaserver/libs/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar'
NOT_JUDGED = re.compile(r'(_to_vanilla_|jerry_can_from_lava|clothing_table/sew_|^frontierhunts:(arrow_refit|butcher)$|'
                        r'survival/salt_cure|survival/cooked_|survival/tallow|survival/salt|cooked_|smoked_venison|'
                        r'campfire_venison|reserve_(pine|cedar)_planks$|wild_leather|tanned_hide_to_leather)')
# early essentials: what they may use (first hour, before the iron age proper)
EARLY_OK = {'minecraft:stick', '#minecraft:planks', '#minecraft:logs', 'minecraft:flint', 'minecraft:feather',
            'minecraft:string', 'minecraft:leather', 'minecraft:bone', 'minecraft:bone_meal', 'minecraft:glass_bottle',
            'minecraft:book', 'minecraft:paper', 'minecraft:iron_nugget', 'minecraft:iron_ingot', 'minecraft:cobblestone',
            '#minecraft:wool', 'minecraft:copper_ingot', 'frontierhunts:deer_hide', 'frontierhunts:canvas_wall',
            'minecraft:wheat', 'minecraft:rotten_flesh', '#minecraft:coals', 'frontierhunts:tanned_hide',
            '#frontierhunts:tanned_hides', 'minecraft:oak_planks', 'minecraft:orange_wool', 'minecraft:gravel'}
# never used to price an item (refills, sewing, special recipes)
NO_PRICE = re.compile(r'(jerry_can_from_lava|clothing_table/sew_|^frontierhunts:(arrow_refit|butcher)$|survival/salt_cure|_to_vanilla_)')
ESSENTIAL = ('frontierhunts:frontier_handbook', 'frontierhunts:primitive_arrow', 'frontierhunts:field_arrow',
             'frontierhunts:field_bow', 'frontierhunts:bow_tuning_rack', 'frontierhunts:skinning_tool',
             'frontierhunts:field_point', 'frontierhunts:flint_point', 'frontierhunts:pup_tent',
             'frontierhunts:solo_ridge_tent', 'frontierhunts:survival/hide_bedroll', 'frontierhunts:tanning_rack',
             'frontierhunts:wind_checker', 'frontierhunts:reserve_canvas_wall',  # [onebook] hunter_journal retired
             'frontierhunts:fixed_broadhead', 'frontierhunts:field_knife')


def norm(x):
    if isinstance(x, list):
        return '|'.join(sorted(norm(i) for i in x))
    if 'item' in x:
        return x['item']
    if 'tag' in x:
        return '#' + x['tag']
    if x.get('type') == 'neoforge:components':
        return x.get('items', '?') + '{}'
    return json.dumps(x, sort_keys=True)


def short(i):
    return i.replace('minecraft:', '').replace('frontierhunts:', 'fh:')


class Recipe:
    def __init__(self, rid, j):
        self.id, self.j = rid, j
        self.type = j.get('type', '')
        res = j.get('result')
        self.out = res.get('id') if isinstance(res, dict) else (res if isinstance(res, str) else None)
        self.count = res.get('count', 1) if isinstance(res, dict) else 1
        self.ings = Counter()
        if 'pattern' in j:
            for row in j['pattern']:
                for c in row:
                    if c != ' ':
                        self.ings[norm(j['key'][c])] += 1
        elif 'ingredients' in j:
            for i in j['ingredients']:
                self.ings[norm(i)] += 1
        elif 'ingredient' in j:
            self.ings[norm(j['ingredient'])] += 1

    @property
    def grid(self):
        return self.type in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless')

    def shape_key(self):
        """Comparable form for grid-conflict checks (None = not a grid recipe)."""
        if self.type == 'minecraft:crafting_shapeless':
            return ('shapeless', tuple(sorted(self.ings.elements())))
        if self.type == 'minecraft:crafting_shaped':
            rows = [[('' if c == ' ' else norm(self.j['key'][c])) for c in r] for r in self.j['pattern']]
            return ('shaped', tuple(tuple(r) for r in rows))
        return None

    def text(self):
        s = ', '.join(f'{n} {short(k)}' for k, n in sorted(self.ings.items(), key=lambda kv: (-kv[1], kv[0])))
        st = {'minecraft:crafting_shaped': 'grid', 'minecraft:crafting_shapeless': 'grid',
              'frontierhunts:clothing_table': 'Clothing Table', 'minecraft:stonecutting': 'stonecutter',
              'minecraft:smelting': 'furnace', 'minecraft:smoking': 'smoker',
              'minecraft:campfire_cooking': 'campfire'}.get(self.type, self.type)
        return f'{s} -> {self.count}' + ('' if st == 'grid' else f' ({st})')


def load(jar, namespaces):
    z = zipfile.ZipFile(jar)
    out = []
    for n in sorted(z.namelist()):
        m = re.fullmatch(r'data/([a-z0-9_]+)/recipe/(.+)\.json', n)
        if not m or m.group(1) not in namespaces:
            continue
        try:
            j = json.loads(z.read(n))
        except Exception:
            continue
        out.append(Recipe(f'{m.group(1)}:{m.group(2)}', j))
    return out


def tag_members(jar_paths):
    """minecraft/c/frontierhunts item tags (only used to match tag ingredients in conflict checks)."""
    tags = {}
    for p in jar_paths:
        if not p or not os.path.exists(p):
            continue
        z = zipfile.ZipFile(p)
        for n in z.namelist():
            m = re.fullmatch(r'data/([a-z0-9_.-]+)/tags/items?/(.+)\.json', n)
            if m:
                try:
                    vals = json.loads(z.read(n)).get('values', [])
                except Exception:
                    continue
                t = f'{m.group(1)}:{m.group(2)}'
                tags.setdefault(t, set()).update(v if isinstance(v, str) else v.get('id') for v in vals)
    return tags


def expand(ing, tags, depth=0):
    """Set of item ids an ingredient string can be."""
    out = set()
    for alt in ing.split('|'):
        if alt.startswith('#') and depth < 6:
            for v in tags.get(alt[1:], ()):
                out |= expand(v, tags, depth + 1) if v.startswith('#') else {v}
            if not tags.get(alt[1:]):
                out.add(alt)
        else:
            out.add(alt)
    return out


def overlaps(a, b, tags):
    return a == b or bool(expand(a, tags) & expand(b, tags))


def conflicts(mod, vanilla, tags):
    found = []
    grid = [r for r in mod if r.grid]
    pool = grid + [r for r in vanilla if r.grid]
    for i, a in enumerate(grid):
        ka = a.shape_key()
        for b in pool[i + 1:]:
            if b is a or b.out == a.out:
                continue
            kb = b.shape_key()
            if ka[0] != kb[0]:
                continue
            if ka[0] == 'shapeless':
                if len(ka[1]) != len(kb[1]):
                    continue
                # greedy matching of ingredient alternatives
                left = list(kb[1])
                ok = True
                for x in ka[1]:
                    hit = next((y for y in left if overlaps(x, y, tags)), None)
                    if hit is None:
                        ok = False
                        break
                    left.remove(hit)
                if ok:
                    found.append((a, b))
            else:
                for rows in (kb[1], tuple(tuple(reversed(r)) for r in kb[1])):
                    if len(rows) == len(ka[1]) and all(len(r1) == len(r2) and all(
                            (x == y == '') or (x and y and overlaps(x, y, tags)) for x, y in zip(r1, r2))
                            for r1, r2 in zip(ka[1], rows)):
                        found.append((a, b))
                        break
    return found


class Pricer:
    def __init__(self, recipes):
        self.by_out = {}
        for r in recipes:
            if r.out and not NO_PRICE.search(r.id):
                self.by_out.setdefault(r.out, []).append(r)
        self.cache, self.missing = {}, set()

    def value(self, ing, stack=()):
        alts = ing.split('|')
        vals = [self.one(a, stack) for a in alts]
        vals = [v for v in vals if v is not None]
        return min(vals) if vals else None

    def one(self, item, stack):
        if item in BASE:
            return BASE[item][0]
        if item in self.cache:
            return self.cache[item]
        if item in stack:
            return None
        best = None
        for r in self.by_out.get(item, []):
            v = self.cost(r, stack + (item,))
            if v is not None and (best is None or v < best):
                best = v
        if best is None:
            self.missing.add(item)
        self.cache[item] = best
        return best

    def cost(self, r, stack=()):
        total = 0.0
        for ing, n in r.ings.items():
            v = self.value(ing, stack)
            if v is None:
                return None
            total += v * n
        return total / max(1, r.count)


def judge(tier, ratio):
    lo, hi = (0.33, 3.0) if tier in ('decor', 'block') else (0.6, 1.7)
    if ratio is None:
        return 'n/a'
    return 'TOO CHEAP' if ratio < lo else ('TOO EXPENSIVE' if ratio > hi else 'ok')


def early_rule(r):
    """First-hour essentials: only early materials, at most one iron ingot per craft."""
    bad = [k for k in r.ings if not any(a in EARLY_OK for a in k.split('|'))]
    iron = r.ings.get('minecraft:iron_ingot', 0)
    msg = []
    if bad:
        msg.append('needs ' + ', '.join(short(b) for b in bad))
    if iron > 1:
        msg.append(f'{iron} iron ingots')
    return '; '.join(msg)


def shop_prices(repo):
    shop, board = {}, {}
    p = os.path.join(repo or '', 'src/com/formaworks/frontierhunts/economy/ShopPrices.java')
    if os.path.exists(p):
        for m in re.finditer(r'price\("([a-z_0-9]+)",\s*(\d+)\)', open(p).read()):
            shop[m.group(1)] = (int(m.group(2)), 0)
    else:  # master: ExpeditionService.SHOP (dec62g8)
        shop = {k: (v, 0) for k, v in {
            'rifle_round': 8, 'shotgun_shell': 8, 'pistol_round': 6, 'tranquilizer_dart': 12, 'bowfishing_arrow': 8,
            'medkit': 10, 'bait': 4, 'scent_cover': 8, 'suppressor': 45, 'extended_magazine': 35, 'steady_stock': 30,
            'bipod': 25, 'six_power_scope': 40, 'eight_power_scope': 55, 'twelve_power_scope': 70, 'thermal_scope': 85,
            'fishing_drag_kit': 20}.items()}
    c = os.path.join(repo or '', 'src/com/formaworks/frontierhunts/camp/ContractMenu.java')
    if os.path.exists(c):
        t = open(c).read()
        for m in re.finditer(r'BOARD_([A-Z_]+)\s*=\s*(\d+)', t):
            board[m.group(1).lower()] = int(m.group(2))
    return shop, board


def main(argv):
    if not argv or argv[0].startswith('-'):
        print(__doc__)
        return 2
    jar, opts = argv[0], {}
    i = 1
    while i < len(argv):
        opts[argv[i]] = argv[i + 1] if i + 1 < len(argv) else ''
        i += 2
    vanilla_jar = opts.get('--vanilla', VANILLA_DEFAULT)
    mod = load(jar, NS)
    vanilla = load(vanilla_jar, ('minecraft',)) if os.path.exists(vanilla_jar) else []
    tags = tag_members([vanilla_jar, jar])
    pricer = Pricer(mod + [r for r in vanilla if r.out and r.out not in BASE])
    rows, unrated = [], []
    for r in mod:
        if not r.out:
            continue
        path = r.out.split(':', 1)[1]
        judged = not NOT_JUDGED.search(r.id) and r.out.split(':')[0] in NS
        tier, target, why = TARGET.get(path, (None, None, ''))
        v = pricer.cost(r)
        if judged and tier is None:
            unrated.append(r.id)
        ratio = (v / target) if (v is not None and target) else None
        verdict = judge(tier, ratio) if judged and tier else ('conversion' if not judged else 'unrated')
        rule = early_rule(r) if r.id in ESSENTIAL else ''
        if rule:
            verdict = 'NOT FIRST-HOUR'
        shape = '/'.join(r.j['pattern']) if 'pattern' in r.j else ''
        rows.append(dict(id=r.id, item=path, shape=shape, tier=tier or ('conv' if not judged else '?'), recipe=r.text(),
                         value=None if v is None else round(v, 2), target=target, ratio=None if ratio is None else round(ratio, 2),
                         verdict=verdict, rule=rule, why=why))
    conf = conflicts(mod, vanilla, tags)
    shop, board = shop_prices(opts.get('--repo'))
    before = {}
    if opts.get('--before') and os.path.exists(opts['--before']):
        before = {r['id']: r for r in json.load(open(opts['--before']))['rows']}

    bad = [r for r in rows if r['verdict'] not in ('ok', 'conversion')]
    print(f'recipes: {len(rows)}  judged: {sum(1 for r in rows if r["verdict"] != "conversion")}  '
          f'outliers: {len(bad)}  grid conflicts: {len(conf)}  unrated: {len(unrated)}')
    for r in sorted(bad, key=lambda r: (TIER_ORDER.get(r['tier'], 9), r['id'])):
        print(f"  {r['verdict']:14s} {r['id']:52s} {r['value']!s:>7} pt / target {r['target']!s:>6} ({r['ratio']}) {r['rule']}")
    for a, b in conf:
        print(f'  CONFLICT {a.id} <-> {b.id}: {a.text()}')
    for u in unrated:
        print('  UNRATED', u)
    if pricer.missing:
        print('  (no value for: ' + ', '.join(sorted(short(m) for m in pricer.missing)) + ')')

    # token economy
    item_value = {}
    for r in rows:
        if r['value'] is not None and r['verdict'] != 'conversion':
            item_value.setdefault(r['item'], r['value'])
    shop_rows = []
    for k, (price, count) in shop.items():
        cnt = count or (12 if any(s in k for s in ('round', 'shell', 'dart', 'arrow')) else 1)
        v = item_value.get(k)
        fair = None if v is None else v * cnt / PT_PER_TOKEN
        tier = TARGET.get(k, ('?',))[0]
        shop_rows.append(dict(item=k, count=cnt, price=price, crafted_pt=None if v is None else round(v * cnt, 1),
                              fair=None if fair is None else round(fair, 1),
                              ratio=None if not fair else round(price / fair, 2), tier=tier))
    print(f'token model: {TOKENS_PER_HUNT} tokens/hunt x {HUNTS_PER_HOUR} hunts/h -> 1 token = {PT_PER_TOKEN:.2f} pt')
    for s in shop_rows:
        print(f"  shop {s['item']:20s} x{s['count']:<3d} {s['price']:>4d} tokens  crafted {s['crafted_pt']} pt = fair {s['fair']} ({s['ratio']}x) [{s['tier']}]")
    if board:
        print('  board', board)

    if '--json' in opts:
        json.dump(dict(rows=rows, conflicts=[[a.id, b.id] for a, b in conf], shop=shop_rows, board=board),
                  open(opts['--json'], 'w'), indent=1)
    if '--md' in opts:
        md = ['| recipe | tier | before | pt | after | pt | target | ratio | verdict | why |', '|---|---|---|---|---|---|---|---|---|---|'] \
            if before else ['| recipe | tier | recipe | pt | target | ratio | verdict |', '|---|---|---|---|---|---|---|']
        for r in sorted(rows, key=lambda r: (TIER_ORDER.get(r['tier'], 9), r['id'])):
            if r['verdict'] == 'conversion':
                continue
            if before:
                b = before.get(r['id'], {})
                changed = b.get('recipe') != r['recipe'] or b.get('shape', r['shape']) != r['shape']
                why = WHY.get(r['id'].split(':', 1)[1], '')
                v0 = b.get('verdict', 'new')
                md.append(f"| `{r['id'].split(':', 1)[1]}` | {r['tier']} | {b.get('recipe', '(new)')} | {b.get('value')} | "
                          f"{'**' + r['recipe'] + '**' + (' `' + r['shape'] + '`' if r['shape'] and b.get('shape') != r['shape'] else '') if changed else '='} | {r['value'] if changed else ''} | {r['target']} | "
                          f"{r['ratio']} | {('was ' + v0 + ' → ' if v0 != r['verdict'] else '') + r['verdict']} | {why if changed else ''} |")
            else:
                md.append(f"| `{r['id'].split(':', 1)[1]}` | {r['tier']} | {r['recipe']} | {r['value']} | {r['target']} | "
                          f"{r['ratio']} | {r['verdict']}{(' (' + r['rule'] + ')') if r['rule'] else ''} |")
        open(opts['--md'], 'w').write('\n'.join(md) + '\n')
    return 1 if (conf or unrated) else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
