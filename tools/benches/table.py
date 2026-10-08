"""[benches] The bench / tab / section of every bench recipe, read from BenchCatalog.java and tools/benches/recipes.json (the
Clothing Table recipes from the jar). Prints a Markdown table; exits 1 if a recipe has no explicit place or a tab is empty.

python3 tools/benches/table.py <repo> [base jar] > table.md
"""
import json, os, re, sys, zipfile

R = sys.argv[1] if len(sys.argv) > 1 else '.'
BASE = sys.argv[2] if len(sys.argv) > 2 else '/home/claude/fh2/.infra/orig62.jar'
src = open(os.path.join(R, 'src/com/formaworks/frontierhunts/benches/BenchCatalog.java')).read()
tabsrc = open(os.path.join(R, 'src/com/formaworks/frontierhunts/benches/BenchTab.java')).read()
LD = os.path.join(R, 'patch/_merge/assets/frontierhunts/lang/en_us.json')
lang = {}
for f in sorted(os.listdir(LD)):  # [clothing] every workstream's fragment (merged in name order, like the build)
    if f.endswith('.json'):
        lang.update(json.load(open(os.path.join(LD, f))))
TABS = [(m.group(1), m.group(2), m.group(3), m.group(4)) for m in
        re.finditer(r'\n   ([A-Z]+)\(Bench\.([A-Z]+), "([a-z_]+)", "[a-z_:]+", Kind\.([A-Z]+)\)', tabsrc)]
BENCH = {'FRONTIER': 'Frontier Workbench', 'GUNSMITH': "Gunsmith's Bench", 'RELOADING': 'Reloading Bench'}
PLACE, order = {}, 0
for m in re.finditer(r'put\(BenchTab\.([A-Z]+), "([a-z_]+)",((?:\s*"[a-z0-9_:]+",?)+)\);', src):
    for i in re.findall(r'"([a-z0-9_:]+)"', m.group(3)):
        PLACE[i if ':' in i else 'frontierhunts:' + i] = (m.group(1), m.group(2), order)
        order += 1
rows = [dict(r) for r in json.load(open(os.path.join(R, 'tools/benches/recipes.json')))]
z = zipfile.ZipFile(BASE)
ct = {n: z.read(n) for n in z.namelist() if n.startswith('data/frontierhunts/recipe/clothing_table/') and n.endswith('.json')}
pdir = os.path.join(R, 'patch/data/frontierhunts/recipe/clothing_table')
if os.path.isdir(pdir):
    for f in os.listdir(pdir):
        if f.endswith('.json'):
            ct['data/frontierhunts/recipe/clothing_table/' + f] = open(os.path.join(pdir, f), 'rb').read()
for n in sorted(ct):
    if True:
        d = json.loads(ct[n])
        rows.append({'recipe': 'frontierhunts:clothing_table/' + n.rsplit('/', 1)[1][:-5], 'result': d['result']['id'], 'count': d['result'].get('count', 1),
                     'type': 'frontierhunts:clothing_table', 'sew': d.get('sew')})
tab_of = {t[0]: t for t in TABS}
bad, used = [], {}
for r in rows:
    p = ('CLOTHING', 'sewing', 50000) if r.get('sew') else PLACE.get(r['result'])
    if p is None:
        bad.append(r['recipe'])
        p = ('MISC', 'other', 10 ** 6)
    r['tab'], r['section'], r['order'] = p
    used[p[0]] = used.get(p[0], 0) + 1
rows.sort(key=lambda r: ([t[0] for t in TABS].index(r['tab']), r['order'], r['recipe']))
print('| # | Bench | Tab | Section | Recipe | Result | Count |')
print('|---|---|---|---|---|---|---|')
for i, r in enumerate(rows, 1):
    t = tab_of[r['tab']]
    print('| %d | %s | %s | %s | `%s` | `%s` | %s |' % (i, BENCH[t[1]], lang.get('bench.frontierhunts.tab.' + t[2], t[2]),
                                                      lang.get('bench.frontierhunts.section.' + r['section'], r['section']), r['recipe'], r['result'], r['count']))
empty = [t[0] for t in TABS if t[3] == 'RECIPES' and t[0] != 'MISC' and not used.get(t[0])]
print()
print('Total: %d recipes (%d bench recipes + %d Clothing Table recipes). Per tab: %s' % (
    len(rows), sum(1 for r in rows if r['type'] != 'frontierhunts:clothing_table'), sum(1 for r in rows if r['type'] == 'frontierhunts:clothing_table'),
    ', '.join('%s %d' % (t[0], used.get(t[0], 0)) for t in TABS if t[3] == 'RECIPES')))
if bad or empty:
    print('PROBLEMS: unplaced %s, empty tabs %s' % (bad, empty), file=sys.stderr)
    sys.exit(1)
