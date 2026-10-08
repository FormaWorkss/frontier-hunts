"""[meshes] Markdown table of the animation audit, master vs branch: python3 tables.py <before audit.csv> <after audit.csv> [lod]"""
import csv, sys
from collections import defaultdict

lod = sys.argv[3] if len(sys.argv) > 3 else 'ultra'


def worst(path):
    acc = {}
    for r in csv.DictReader(open(path)):
        if r['lod'] != lod:
            continue
        a = acc.setdefault(r['species'], dict(p999=0.0, mx=0.0, det=9.0, flip=0, buried=0, sink=0.0, tris=0, state=''))
        p = float(r['stretch_p999'])
        if p > a['p999']:
            a['p999'] = p; a['state'] = r['state']
        a['mx'] = max(a['mx'], float(r['stretch_max']))
        a['det'] = min(a['det'], float(r['det_min']))
        a['flip'] = max(a['flip'], int(r['flipped']))
        a['buried'] = max(a['buried'], int(r['buried']))
        a['sink'] = max(a['sink'], float(r['sink']))
    return acc


B, A = worst(sys.argv[1]), worst(sys.argv[2])
print('| species | stretch p99.9 (worst state) | stretch max | min skin det (twist/collapse) | flipped tris | buried verts |')
print('|---|---|---|---|---|---|')
for sp in B:
    b, a = B[sp], A.get(sp)
    if a is None:
        continue
    print('| %s | %.0f%% -> %.0f%% (%s) | %.0f%% -> %.0f%% | %.2f -> %.2f | %d -> %d | %d -> %d |' % (
        sp, 100 * b['p999'], 100 * a['p999'], a['state'], 100 * b['mx'], 100 * a['mx'], b['det'], a['det'], b['flip'], a['flip'],
        b['buried'], a['buried']))
