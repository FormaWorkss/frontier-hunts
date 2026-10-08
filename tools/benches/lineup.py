#!/usr/bin/env python3
"""[benchart] Distance lineup: the three benches side by side from ~3 and ~8 blocks (silhouette / colour check)."""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_bench import render_world  # noqa: E402

repo = os.path.abspath(sys.argv[1])
out = sys.argv[2]
A = os.path.join(repo, 'patch', 'assets', 'frontierhunts')
ids = ['frontier_workbench', 'gunsmith_bench', 'reloading_bench']
rows = []
for dist, w, h in ((60, 420, 330), (140, 420, 330)):
    row = Image.new('RGB', (w * 3, h))
    for i, b in enumerate(ids):
        row.paste(render_world(A, b, 18, 18, w, h, dist), (i * w, 0))
    rows.append(row)
sheet = Image.new('RGB', (rows[0].width, sum(r.height for r in rows)))
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height
sheet.save(out)
print('wrote', out)
