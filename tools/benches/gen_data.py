"""[benches] Data for the three workbenches. Idempotent; run it again after merging a branch that adds recipes.

python3 tools/benches/gen_data.py <repo root> [base jar]

1. Every Frontier Hunts / Frontier Structures crafting_shaped / crafting_shapeless recipe becomes a bench recipe
   (frontierhunts:bench_shaped / bench_shapeless, same JSON otherwise), written to patch/ under the same path
   (fs/resources in place for Frontier Structures). Kept at the vanilla crafting table: recipes whose result is a vanilla
   item (log / leather conversions), the Frontier Handbook and the three benches. Only the "type" string changes: the
   rest of the file is byte-identical.
2. The retired benches' recipes, their unlock advancements and the crafting-grid arrow refit go to
   patch/_remove/benches.txt.
3. The three bench recipes (vanilla crafting table) + unlock advancements, blockstates, loot tables, the axe tag.
4. Structure templates that placed retired benches place their successors (palette swap; a single-block one gets its
   right half when the space beside it is free).
5. tools/benches/recipes.json: every converted recipe and its result (input of the docs table and the QA list).
"""
import glob, json, os, re, sys, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
BASE = sys.argv[2] if len(sys.argv) > 2 else '/home/claude/fh2/.infra/orig62.jar'
PATCH = os.path.join(R, 'patch')
BENCHES = ['frontier_workbench', 'gunsmith_bench', 'reloading_bench']
KEEP = {'frontierhunts:frontier_handbook'} | {'frontierhunts:' + b for b in BENCHES}
RETIRED = {'weapons_workbench': 'gunsmith_bench', 'attachment_workbench': 'gunsmith_bench', 'bow_tuning_rack': 'gunsmith_bench',
           'ammo_reloader': 'reloading_bench', 'fishing_station': 'frontier_workbench', 'clothing_workbench': 'frontier_workbench',
           'tent_bench': 'frontier_workbench'}
TYPES = {'minecraft:crafting_shaped': 'frontierhunts:bench_shaped', 'minecraft:crafting_shapeless': 'frontierhunts:bench_shapeless'}


def removed():
    out = set()
    for f in glob.glob(os.path.join(PATCH, '_remove', '*.txt')):
        if os.path.basename(f) == 'benches.txt':
            continue
        for line in open(f, encoding='utf-8'):
            line = line.split('#', 1)[0].strip()
            if line:
                out.add(line)
    return out


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
    if old != text:
        open(path, 'w', encoding='utf-8').write(text)
        return True
    return False


def convert(text):
    def sub(m):
        return '"type"' + m.group(1) + '"' + TYPES[m.group(2)] + '"'
    return re.sub(r'"type"(\s*:\s*)"(minecraft:crafting_shaped|minecraft:crafting_shapeless)"', sub, text, count=1)


zin = zipfile.ZipFile(BASE)
names = set(zin.namelist())
gone = removed()
summary = []
remove = ['# [benches] the seven retired workbenches are no longer craftable; the crafting-grid arrow refit moved to the Reloading Bench']

# ------------------------------------------------------------------ 1+2: frontierhunts recipes
paths = {}
for n in names:
    if n.startswith('data/frontierhunts/recipe/') and n.endswith('.json') and n not in gone:
        paths[n] = 'jar'
for f in glob.glob(os.path.join(PATCH, 'data/frontierhunts/recipe/**/*.json'), recursive=True):
    paths[os.path.relpath(f, PATCH).replace(os.sep, '/')] = 'patch'
changed = 0
for n in sorted(paths):
    src = os.path.join(PATCH, n)
    text = open(src, encoding='utf-8').read() if paths[n] == 'patch' else zin.read(n).decode('utf-8')
    d = json.loads(text)
    t = d.get('type', '')
    rel = n[len('data/frontierhunts/recipe/'):-5]
    if t == 'frontierhunts:arrow_refit':
        if n in names:
            remove.append(n)
        if os.path.exists(src):
            os.remove(src)
        continue
    res = d.get('result', {})
    rid = res.get('id') or res.get('item') or ''
    if rid.startswith('frontierhunts:') and rid.split(':', 1)[1] in RETIRED and t in TYPES:
        if n in names:
            remove.append(n)
        if os.path.exists(src):
            os.remove(src)
        continue
    if t in TYPES and not rid.startswith('minecraft:') and rid not in KEEP:
        changed += write(src, convert(text))
        summary.append({'recipe': 'frontierhunts:' + rel, 'result': rid, 'count': res.get('count', 1), 'type': TYPES[t]})
    elif t in TYPES.values():
        summary.append({'recipe': 'frontierhunts:' + rel, 'result': rid, 'count': res.get('count', 1), 'type': t})

for old in list(RETIRED) + ['arrow_refit']:
    for adv in ('data/frontierhunts/advancement/recipes/unlock/%s.json' % old, 'data/frontierhunts/advancement/recipes/workshop/%s.json' % old):
        if adv in names:
            remove.append(adv)
        if os.path.exists(os.path.join(PATCH, adv)):
            os.remove(os.path.join(PATCH, adv))
os.makedirs(os.path.join(PATCH, '_remove'), exist_ok=True)
write(os.path.join(PATCH, '_remove', 'benches.txt'), '\n'.join(remove) + '\n')

# ------------------------------------------------------------------ Frontier Structures recipes (in place)
for f in sorted(glob.glob(os.path.join(R, 'fs/resources/data/frontierstructures/recipe/**/*.json'), recursive=True)):
    text = open(f, encoding='utf-8').read()
    d = json.loads(text)
    rel = os.path.relpath(f, os.path.join(R, 'fs/resources/data/frontierstructures/recipe'))[:-5]
    res = d.get('result', {})
    rid = res.get('id') or res.get('item') or ''
    if d.get('type') in TYPES and not rid.startswith('minecraft:'):
        changed += write(f, convert(text))
        summary.append({'recipe': 'frontierstructures:' + rel, 'result': rid, 'count': res.get('count', 1), 'type': TYPES[d['type']]})
    elif d.get('type') in TYPES.values():
        summary.append({'recipe': 'frontierstructures:' + rel, 'result': rid, 'count': res.get('count', 1), 'type': d['type']})

# ------------------------------------------------------------------ 3: the benches themselves
RECIPES = {
    # one row of a bench's own material over a planks top on log legs: sticks (tools), flint (gunsmith), stone (the press)
    'frontier_workbench': (['SSS', 'PPP', 'L L'], {'S': {'item': 'minecraft:stick'}}),
    'gunsmith_bench': (['SFS', 'PPP', 'L L'], {'S': {'item': 'minecraft:stick'}, 'F': {'item': 'minecraft:flint'}}),
    'reloading_bench': (['CCC', 'PPP', 'L L'], {'C': {'tag': 'minecraft:stone_crafting_materials'}}),
}
for b, (pattern, key) in RECIPES.items():
    k = dict(key)
    k['P'] = {'tag': 'minecraft:planks'}
    k['L'] = {'tag': 'minecraft:logs'}
    rec = {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': k,
           'result': {'id': 'frontierhunts:' + b, 'count': 1}}
    write(os.path.join(PATCH, 'data/frontierhunts/recipe/%s.json' % b), json.dumps(rec, indent=2) + '\n')
    adv = {'parent': 'minecraft:recipes/root',
           'criteria': {'has_logs': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': '#minecraft:logs'}]}},
                        'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'frontierhunts:' + b}}},
           'requirements': [['has_logs', 'has_the_recipe']], 'rewards': {'recipes': ['frontierhunts:' + b]}}
    write(os.path.join(PATCH, 'data/frontierhunts/advancement/recipes/unlock/%s.json' % b), json.dumps(adv, indent=2) + '\n')
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for part in ('single', 'left', 'right'):
            v = {'model': 'frontierhunts:block/%s_%s' % (b, 'right' if part == 'right' else 'left')}
            if y:
                v['y'] = y
            variants['bench_part=%s,facing=%s' % (part, facing)] = v
    write(os.path.join(PATCH, 'assets/frontierhunts/blockstates/%s.json' % b), json.dumps({'variants': variants}, indent=2) + '\n')
    loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0,
                                                  'entries': [{'type': 'minecraft:item', 'name': 'frontierhunts:' + b}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': 'frontierhunts:blocks/' + b}
    write(os.path.join(PATCH, 'data/frontierhunts/loot_table/blocks/%s.json' % b), json.dumps(loot, indent=2) + '\n')
write(os.path.join(PATCH, '_merge/data/minecraft/tags/block/mineable/axe.json/zzzzzzz_benches.json'),
      json.dumps({'values': ['frontierhunts:' + b for b in BENCHES]}, indent=2) + '\n')

# ------------------------------------------------------------------ 4: structure templates
AIRISH = {'minecraft:air', 'minecraft:cave_air', 'minecraft:structure_void', 'frontierstructures:structure_space'}
CW = {'north': (1, 0), 'east': (0, 1), 'south': (-1, 0), 'west': (0, -1)}  # facing.getClockWise() offset (dx, dz)


def fix_template(src_bytes_or_path, out_path):
    name, root = nbt.load(src_bytes_or_path)
    pal = root['palette'][1][1]
    blocks = root['blocks'][1][1]
    size = root['size'][1][1]
    touched = 0
    for p in pal:
        n = p['Name'][1]
        if n.startswith('frontierhunts:') and n.split(':')[1] in RETIRED:
            p['Name'] = (nbt.STR, 'frontierhunts:' + RETIRED[n.split(':')[1]])
            touched += 1
    if not touched:
        return 0
    # a bench has no block entity: drop the old rack / workbench block-entity data of those positions
    bench_states = {i for i, p in enumerate(pal) if p['Name'][1].split(':')[-1] in BENCHES}
    for b in blocks:
        if b['state'][1] in bench_states and 'nbt' in b:
            del b['nbt']
    # single-block retired benches: add the right half where there is room
    at = {tuple(b['pos'][1][1]): b for b in blocks}
    index = {}
    for i, p in enumerate(pal):
        props = {k: v[1] for k, v in p.get('Properties', (nbt.COMP, {}))[1].items()}
        index[(p['Name'][1], tuple(sorted(props.items())))] = i
    for b in list(blocks):
        p = pal[b['state'][1]]
        nm = p['Name'][1]
        if nm.split(':')[-1] not in BENCHES:
            continue
        props = {k: v[1] for k, v in p.get('Properties', (nbt.COMP, {}))[1].items()}
        if props.get('bench_part') != 'single':
            continue
        x, y, z = b['pos'][1][1]
        dx, dz = CW[props.get('facing', 'north')]
        q = (x + dx, y, z + dz)
        if not (0 <= q[0] < size[0] and 0 <= q[2] < size[2]):
            continue
        other = at.get(q)
        if other is not None and pal[other['state'][1]]['Name'][1] not in AIRISH:
            continue

        def state(part):
            key = (nm, tuple(sorted(dict(props, bench_part=part).items())))
            if key not in index:
                pal.append({'Name': (nbt.STR, nm), 'Properties': (nbt.COMP, {k: (nbt.STR, v) for k, v in dict(props, bench_part=part).items()})})
                index[key] = len(pal) - 1
            return index[key]
        b['state'] = (nbt.INT, state('left'))
        if other is not None:
            other['state'] = (nbt.INT, state('right'))
        else:
            nb = {'pos': (nbt.LIST, (nbt.INT, list(q))), 'state': (nbt.INT, state('right'))}
            blocks.append(nb)
            at[q] = nb
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    nbt.save(out_path, name, root)
    return touched


nfix = 0
for f in sorted(glob.glob(os.path.join(R, 'fs/resources/data/frontierstructures/structure/**/*.nbt'), recursive=True)):
    nfix += fix_template(f, f)
import tempfile  # noqa: E402
for n in sorted(names):
    if n.startswith('data/frontierhunts/structure/') and n.endswith('.nbt') and n not in gone:
        target = os.path.join(PATCH, n)
        src = target if os.path.exists(target) else None
        if src is None:
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.nbt')
            tmp.write(zin.read(n))
            tmp.close()
            src = tmp.name
        nfix += fix_template(src, target)

json.dump(sorted(summary, key=lambda r: r['recipe']), open(os.path.join(R, 'tools/benches/recipes.json'), 'w'), indent=1)
print('bench recipes: %d (%d files changed); removed: %d; template palettes fixed: %d' % (len(summary), changed, len(remove) - 1, nfix))
