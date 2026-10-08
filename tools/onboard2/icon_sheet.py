#!/usr/bin/env python3
"""[onboard2] Renders the seat items (and their creative-tab neighbours) exactly like tools/outfitter/gui_audit.py does
(display.gui, 16 px slot at GUI scale 2) and reports the fill and the centre offset of each icon.
python3 tools/onboard2/icon_sheet.py <repo> <jar> <out.png>"""
import os
import sys

R, JAR, OUTP = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = ['gui_audit.py', R, '/tmp/claude-0/ob2/gui_tmp', '--jar', JAR]
sys.path.insert(0, os.path.join(R, 'tools/outfitter'))
import gui_audit as G  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

NAMES = ['lodge_chair', 'log_stump_seat', 'camp_chair', 'trail_bench', 'blind_chair', 'tower_chair', 'lodge_table', 'tower_blind', 'field_blind']
P = 36
Z = 4
sheet = Image.new('RGBA', (len(NAMES) * (P * Z // 2 + 12) + 12, P * Z // 2 + 40), (198, 198, 198, 255))
d = ImageDraw.Draw(sheet)
for i, n in enumerate(NAMES):
    im, e = G.render_item(n)
    x = 12 + i * (P * Z // 2 + 12)
    cell = Image.new('RGBA', (P, P), (139, 139, 139, 255))
    if im is not None:
        cell.alpha_composite(im, (2, 2))
        a = np.asarray(im)[..., 3] > 30
        ys, xs = np.nonzero(a)
        cx, cy = (xs.min() + xs.max() + 1) / 4 - 8, (ys.min() + ys.max() + 1) / 4 - 8
        print('%-16s fill %4.1f x %4.1f px of 16   centre offset (%+.2f, %+.2f) px' % (n, e['w'], e['h'], cx, cy))
    sheet.alpha_composite(cell.resize((P * Z // 2, P * Z // 2), Image.NEAREST), (x, 8))
    d.text((x, P * Z // 2 + 14), n, fill=(30, 30, 30, 255))
sheet.save(OUTP)
