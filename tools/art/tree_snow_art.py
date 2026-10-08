"""[1.2.2] textures/block/tree_snow.png: four soft clumps of snow (one per quadrant) for the snow lying on realistic
tree sprays (client/tree/TreeSnow). Cutout alpha; white with cold blue in the hollows and a little sparkle."""
import os, math
import numpy as np
from PIL import Image

R = os.path.join(os.path.dirname(__file__), '../..')
OUT = os.path.join(R, 'patch/assets/frontierhunts/textures/block/tree_snow.png')
S = 64
rng = np.random.default_rng(1222)


def noise(n, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((n // scale + 3, n // scale + 3))
    ys, xs = np.mgrid[0:n, 0:n] / scale
    x0, y0 = xs.astype(int), ys.astype(int)
    tx, ty = xs - x0, ys - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b, c, d = g[y0, x0], g[y0, x0 + 1], g[y0 + 1, x0], g[y0 + 1, x0 + 1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


img = np.zeros((2 * S, 2 * S, 4), np.float32)
for q in range(4):
    ys, xs = np.mgrid[0:S, 0:S] + 0.5
    cx, cy = S / 2 + rng.uniform(-2, 2), S / 2 + rng.uniform(-2, 2)
    ang = np.arctan2(ys - cy, xs - cx)
    # a lumpy round edge: a few lobes plus fine crumble
    rad = S * 0.40
    for k, amp in ((3, 0.07), (5, 0.06), (7, 0.04), (11, 0.025)):
        rad = rad * (1 + amp * np.sin(k * ang + rng.uniform(0, 6.3)))
    n1, n2 = noise(S, 6, q * 7 + 1), noise(S, 2, q * 7 + 2)
    rad = rad * (0.93 + 0.12 * n1)
    d = np.hypot(xs - cx, ys - cy)
    inside = d < rad - (n2 - 0.5) * 2.2
    t = np.clip(d / rad, 0, 1)
    # dome: brighter on the crown of the clump, blue-grey toward the edge and in little hollows
    lum = 0.97 - 0.13 * t ** 2.2 - 0.05 * (noise(S, 4, q * 7 + 3) - 0.5) - 0.025 * (n2 - 0.5)
    r = lum * 0.97
    g = lum * 0.985
    b = np.minimum(1.0, lum * 1.02 + 0.03 * t)
    sparkle = rng.random((S, S)) > 0.985
    r, g, b = [np.where(sparkle, 1.0, c) for c in (r, g, b)]
    ox, oy = (q % 2) * S, (q // 2) * S
    img[oy:oy + S, ox:ox + S, 0] = r
    img[oy:oy + S, ox:ox + S, 1] = g
    img[oy:oy + S, ox:ox + S, 2] = b
    img[oy:oy + S, ox:ox + S, 3] = inside.astype(np.float32)

Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA').save(OUT)
print('wrote', OUT)
