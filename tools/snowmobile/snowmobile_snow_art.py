#!/usr/bin/env python3
"""[1.2.5] Snow-coat cutout textures for the snowmobile (3 levels of coverage), tileable, generated here."""
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts', 'textures', 'entity')
N = 64
rng = np.random.default_rng(31)


def tile_noise(octaves):
    f = np.zeros((N, N))
    for k, amp in octaves:
        ph = rng.uniform(0, 2 * np.pi, (k, k))
        coef = rng.normal(0, 1, (k, k))
        y, x = np.mgrid[0:N, 0:N] / N
        for i in range(k):
            for j in range(k):
                f += amp * coef[i, j] * np.cos(2 * np.pi * (i * x + j * y) + ph[i, j]) / (1 + i + j)
    f -= f.min()
    return f / f.max()


base = tile_noise([(6, 1.0), (12, 0.5)])
grain = rng.random((N, N))
for lvl, cover in ((1, 0.30), (2, 0.58), (3, 0.85)):
    thr = np.quantile(base, 1 - cover)
    mask = base >= thr
    shade = 0.86 + 0.14 * (base - thr) / (1 - thr + 1e-9)
    shade = np.clip(shade - 0.06 * (grain < 0.08), 0, 1)
    rgb = np.stack([shade * 236, shade * 242, shade * 250], -1)
    a = np.where(mask, 255, 0)
    img = np.dstack([rgb, a]).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(os.path.join(OUT, f'snowmobile_snow_{lvl}.png'), optimize=True)
print('ok')
