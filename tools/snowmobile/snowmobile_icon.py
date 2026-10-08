#!/usr/bin/env python3
"""[1.2.5] The snowmobile's item icon: the real mesh rendered from a three-quarter view, shrunk to 32x32."""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import snowmobile_preview as P

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts')
KEY = (255, 0, 255)

parts = P.load(os.path.join(A, 'models', 'entity', 'snowmobile.fhvm'))
tex = [Image.open(os.path.join(A, 'textures', 'entity', p)) for p in ('snowmobile.png', 'snowmobile_track.png', 'snowmobile.png')]
im = P.render(parts, tex, 1.15, 0.28, W=256, H=256, bg=KEY)
a = np.asarray(im).astype(np.int32)
mask = ~((np.abs(a - np.array(KEY)).sum(-1)) < 40)
rgba = np.dstack([a, mask * 255]).astype(np.uint8)
img = Image.fromarray(rgba, 'RGBA')
bb = img.getbbox()
img = img.crop(bb)
s = max(img.size)
sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
sq.paste(img, ((s - img.size[0]) // 2, (s - img.size[1]) // 2))
icon = sq.resize((32, 32), Image.LANCZOS)
px = np.asarray(icon).astype(np.float64)
al = px[..., 3] > 90
rgb = px[..., :3] / np.maximum(px[..., 3:4] / 255.0, 1e-3)
rgb = np.clip((rgb / 255.0) ** 0.8 * 255.0 * 1.15 + 14, 0, 255)  # brighter: the inventory slot is dark grey
out_a = al.copy()
# a 1-pixel dark outline round the shape, as Minecraft item icons have
grow = al.copy()
for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    grow |= np.roll(np.roll(al, dy, 0), dx, 1)
edge = grow & ~al
rgb[edge] = (24, 18, 20)
res = np.dstack([rgb, (grow * 255)]).astype(np.uint8)
icon = Image.fromarray(res, 'RGBA')
out = os.path.join(A, 'textures', 'item', 'snowmobile.png')
icon.save(out)
icon.resize((256, 256), Image.NEAREST).save('/tmp/claude-0/sm_icon_big.png')
print(out)
