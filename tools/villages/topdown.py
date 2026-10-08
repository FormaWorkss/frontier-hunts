#!/usr/bin/env python3
"""[villages] top-down preview of a planned village (VillageHarness dump): graded terrain with hill shading, building
footprints, roads, green, water, furnishings. usage: topdown.py <dump.json> <out.png> [scale]"""
import json, sys
import numpy as np
from PIL import Image, ImageDraw
d = json.load(open(sys.argv[1])); S = int(sys.argv[3]) if len(sys.argv) > 3 else 4
w, h = d['w'], d['h']
c = np.array(d['cols']).reshape(h, w, 5)
kind, y, n, wet, lot = [c[:, :, i] for i in range(5)]
yy = y.astype(float)
gx = np.zeros_like(yy); gz = np.zeros_like(yy)
gx[:, 1:-1] = (yy[:, 2:] - yy[:, :-2]) / 2; gz[1:-1, :] = (yy[2:, :] - yy[:-2, :]) / 2
shade = np.clip(1.0 - 0.18 * gx - 0.18 * gz, 0.55, 1.35)
band = ((yy - yy.min()) % 4 == 0) * 0.06
img = np.zeros((h, w, 3))
grass = np.array([96, 140, 64]); nat = np.array([70, 110, 52]); road = np.array([176, 150, 104]); green = np.array([120, 168, 76])
roof = [np.array(v) for v in ([120, 72, 40], [104, 60, 36], [136, 84, 48], [96, 66, 44], [112, 80, 52], [128, 64, 40])]
for k, col in ((0, nat), (4, grass), (2, green), (3, road)):
    img[kind == k] = col
img[kind == 5] = [60, 96, 180]
for i in range(6):
    img[(kind == 1) & (lot == i)] = roof[i]
img = img * shade[:, :, None] - band[:, :, None] * 255 * (kind != 1)[:, :, None]
# regraded columns: tint cut (reddish) / fill (bluish) faintly
diff = (y - n) * (kind != 1) * (kind != 5)
img[:, :, 0] += np.clip(-diff, 0, 6) * 6
img[:, :, 2] += np.clip(diff, 0, 6) * 6
im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((w * S, h * S), Image.NEAREST)
dr = ImageDraw.Draw(im)
cols = {'lamp': (255, 220, 80), 'firepit': (255, 90, 30), 'flagpole': (230, 230, 230), 'notice_board': (200, 120, 60), 'well': (90, 160, 255),
        'bench': (160, 110, 70), 'woodpile': (110, 70, 40), 'hitch': (210, 190, 120), 'barrels': (150, 100, 60), 'garden': (60, 200, 60), 'signpost': (255, 255, 255)}
for k, x, z, yv, f in d['decos']:
    px, pz = (x - d['x0']) * S + S // 2, (z - d['z0']) * S + S // 2
    r = S * (2 if k in ('well', 'garden', 'firepit') else 1)
    dr.rectangle([px - r, pz - r, px + r, pz + r], outline=cols.get(k, (255, 0, 255)), width=2)
for i, l in enumerate(d['lots']):
    dr.text(((l[5] - d['x0']) * S + 3, (l[6] - d['z0']) * S + 3), l[0].split('_')[-1] + ' y%d' % l[4], fill=(255, 255, 255))
dr.text((4, 4), '%s  green y%d  (%dx%d)' % (d['type'], d['gy'], w, h), fill=(255, 255, 255))
im.save(sys.argv[2])
