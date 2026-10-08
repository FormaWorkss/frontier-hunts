"""[perf] Offline look at the tree impostor level next to the distant (FAR) crown it replaces.

usage: python3 impostor_preview.py <quads.txt from TreeLodBench dump> <textures dir> <out.png>

Rasterises FAR and IMPOSTOR quads of a few fixtures with the fringe sprites (alpha-tested like the
cutout-mipped layer, vanilla-style directional shade as TrunkModel bakes it without shaders) from a
side view and an elevated view, full size and downsampled as it would be seen from the impostor distance.
"""
import sys, os, math, collections
import numpy as np
from PIL import Image

quads_file, tex_dir, out = sys.argv[1:4]
PICK = [('alpine_spruce', 2), ('wild_pine', 1), ('wild_fir', 3), ('alpine_larch', 2), ('alpine_aspen', 2),
        ('alpine_birch', 3), ('wild_maple', 2), ('oak', 0), ('cherry', 1)]
FRINGE = {'alpine_spruce': 'spruce_boughs', 'wild_pine': 'pine_needles', 'wild_fir': 'fir_needles',
          'alpine_larch': 'larch_needles', 'alpine_aspen': 'aspen_leaves', 'alpine_birch': 'birch_leaves',
          'wild_maple': 'maple_leaves', 'oak': 'rw/oak_leaves', 'cherry': 'rw/cherry_leaves'}
TINT = (0.36, 0.62, 0.24)

data = collections.defaultdict(lambda: collections.defaultdict(list))
order = collections.defaultdict(list)
for line in open(quads_file):
    p = line.split()
    name, x, z, level, tex = p[0], p[1], p[2], p[3], int(p[4])
    key = (name, x + ',' + z)
    if key not in order[name]:
        order[name].append(key)
    v = np.array(p[5:], dtype=np.float32).reshape(4, 8)
    data[key][level].append((tex, v))


def texture(name):
    path = os.path.join(tex_dir, FRINGE[name] + '_fringe.png')
    im = np.asarray(Image.open(path).convert('RGBA'), dtype=np.float32) / 255.0
    rgb = im[..., :3]
    if rgb[im[..., 3] > 0.5].mean() > 0.7:  # grey sprite: coloured by the biome tint in game
        rgb = rgb * np.array(TINT, dtype=np.float32)
    return rgb, im[..., 3]


def render(quads, rgb, alpha, yaw, pitch, scale, size):
    W, H = size
    img = np.zeros((H, W, 3), np.float32) + np.array([0.62, 0.74, 0.9], np.float32)
    zbuf = np.full((H, W), np.inf, np.float32)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    allv = np.concatenate([q[1][:, :3] for q in quads])
    centre = allv.mean(0)
    th, tw = alpha.shape
    for tex, v in quads:
        p = v[:, :3] - centre
        x = p[:, 0] * cy - p[:, 2] * sy
        z0 = p[:, 0] * sy + p[:, 2] * cy
        y = p[:, 1] * cp - z0 * sp
        d = p[:, 1] * sp + z0 * cp
        sx = W / 2 + x * scale
        syy = H * 0.55 - y * scale
        n = v[:, 5:8]
        shade = 0.6 * n[:, 0] ** 2 + 0.8 * n[:, 2] ** 2 + np.where(n[:, 1] > 0, 1.0, 0.5) * n[:, 1] ** 2
        if tex == 2:
            shade = 0.72 + 0.28 * shade
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = tri
            xs = np.array([sx[a], sx[b], sx[c]]); ys = np.array([syy[a], syy[b], syy[c]])
            x0, x1 = int(max(0, np.floor(xs.min()))), int(min(W - 1, np.ceil(xs.max())))
            y0, y1 = int(max(0, np.floor(ys.min()))), int(min(H - 1, np.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l2 = 1 - l0 - l1
            inside = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
            if not inside.any():
                continue
            dep = l0 * d[a] + l1 * d[b] + l2 * d[c]
            sh = l0 * shade[a] + l1 * shade[b] + l2 * shade[c]
            if tex == 2:
                u = l0 * v[a, 3] + l1 * v[b, 3] + l2 * v[c, 3]
                w = l0 * v[a, 4] + l1 * v[b, 4] + l2 * v[c, 4]
                ti = np.clip((w * th).astype(int), 0, th - 1); tj = np.clip((u * tw).astype(int), 0, tw - 1)
                ok = inside & (alpha[ti, tj] > 0.5)
                col = rgb[ti, tj] * sh[..., None]
            else:
                ok = inside
                col = np.array([0.36, 0.27, 0.2], np.float32) * sh[..., None]
            region = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok &= dep < region
            region[ok] = dep[ok]
            img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]
    return img


def far_view(img, k):
    H, W, _ = img.shape
    small = img[:H // k * k, :W // k * k].reshape(H // k, k, W // k, k, 3).mean((1, 3))
    return np.kron(small, np.ones((k, k, 1)))


tiles = []
for name, idx in PICK:
    keys = order.get(name)
    if not keys:
        continue
    key = keys[min(idx, len(keys) - 1)]
    rgb, alpha = texture(name)
    row = []
    for yaw, pitch in ((0.4, 0.05), (1.3, 0.55)):
        for level in ('far', 'impostor'):
            img = render(data[key][level], rgb, alpha, yaw, pitch, 9.0, (240, 300))
            row.append(img)
    row.append(far_view(row[0], 6)); row.append(far_view(row[1], 6))
    tiles.append(np.concatenate(row, 1))
sheet = np.concatenate(tiles, 0)
Image.fromarray((np.clip(sheet, 0, 1) * 255).astype(np.uint8)).save(out)
print('columns: side FAR | side IMPOSTOR | elevated FAR | elevated IMPOSTOR | side FAR at 1/6 | side IMPOSTOR at 1/6')
