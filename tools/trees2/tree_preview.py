"""[trees2] Offline renders of realistic trees from `TreeLook dump` (NEAR level), with the ground blocks.

usage: python3 tree_preview.py <quads.txt> <realistic pack textures/block dir> <out.png> [mode]
mode: base  - close-ups of trunk feet (wood + ground, foliage hidden) from the downhill and uphill side
      tree  - whole trees (side view) with foliage
      both  - both rows per fixture (default)
"""
import sys, os, math, collections
import numpy as np
from PIL import Image

quads_file, tex_dir, out = sys.argv[1:4]
mode = sys.argv[4] if len(sys.argv) > 4 else 'both'

BARK = {'alpine_spruce': 'alpine_spruce_log', 'alpine_fir': 'alpine_spruce_log', 'alpine_larch': 'alpine_pine_log',
        'alpine_pine': 'alpine_pine_log', 'alpine_aspen': 'aspen_log', 'alpine_birch': 'birch_log', 'alpine_maple': 'alpine_maple_log',
        'wild_pine': 'pine_log', 'wild_fir': 'cedar_log', 'wild_aspen': 'aspen_log', 'wild_birch': 'birch_log', 'wild_maple': 'aspen_log',
        'oak': None, 'cherry': None}
FRINGE = {'alpine_spruce': 'spruce_boughs', 'alpine_fir': 'fir_needles', 'alpine_larch': 'larch_needles', 'alpine_pine': 'pine_needles',
          'alpine_aspen': 'aspen_leaves', 'alpine_birch': 'birch_leaves', 'alpine_maple': 'maple_leaves', 'wild_pine': 'pine_needles',
          'wild_fir': 'fir_needles', 'wild_aspen': 'aspen_leaves', 'wild_birch': 'birch_leaves', 'wild_maple': 'maple_leaves',
          'oak': 'rw/oak_leaves', 'cherry': 'rw/cherry_leaves'}
TINT = np.array((0.36, 0.62, 0.24), np.float32)

fx = collections.OrderedDict()
for line in open(quads_file):
    p = line.split()
    kind, fid = p[0], p[1]
    f = fx.setdefault(fid, {'quads': [], 'ground': {}, 'origin': None})
    if kind == 'T':
        f['origin'] = tuple(int(v) for v in p[2:5])
    elif kind == 'G':
        f['ground'][(int(p[2]), int(p[3]))] = int(p[4])
    else:
        f['quads'].append((int(p[2]), np.array(p[3:], dtype=np.float32).reshape(4, 8)))

_tex = {}
def tex(name, grey_tint=False):
    if name in _tex:
        return _tex[name]
    path = os.path.join(tex_dir, name + '.png')
    if not os.path.exists(path):
        _tex[name] = None
        return None
    im = np.asarray(Image.open(path).convert('RGBA'), dtype=np.float32) / 255.0
    h = im.shape[1]
    im = im[:h]  # first animation frame
    rgb = im[..., :3]
    if grey_tint and rgb[im[..., 3] > 0.5].mean() > 0.6:
        rgb = rgb * TINT
    _tex[name] = (rgb, im[..., 3])
    return _tex[name]


def ground_quads(ground, x0, z0, rad):
    """Top and side faces of the ground blocks (grass-green top, dirt sides) around the trunk."""
    out = []
    for (x, z), h in ground.items():
        if abs(x - x0) > rad or abs(z - z0) > rad:
            continue
        y = h + 1
        out.append(('top', np.array([[x, y, z], [x + 1, y, z], [x + 1, y, z + 1], [x, y, z + 1]], np.float32), (0, 1, 0)))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = (x + dx, z + dz)
            hn = ground.get(nb, h) if abs(nb[0] - x0) <= rad and abs(nb[1] - z0) <= rad else h - 2
            if hn >= h:
                continue
            for yy in range(hn + 1, h + 1):
                if dx == 1:
                    c = [[x + 1, yy, z], [x + 1, yy + 1, z], [x + 1, yy + 1, z + 1], [x + 1, yy, z + 1]]
                elif dx == -1:
                    c = [[x, yy, z], [x, yy + 1, z], [x, yy + 1, z + 1], [x, yy, z + 1]]
                elif dz == 1:
                    c = [[x, yy, z + 1], [x, yy + 1, z + 1], [x + 1, yy + 1, z + 1], [x + 1, yy, z + 1]]
                else:
                    c = [[x, yy, z], [x, yy + 1, z], [x + 1, yy + 1, z], [x + 1, yy, z]]
                out.append(('side', np.array(c, np.float32), (dx, 0, dz)))
    return out


def raster(img, zbuf, sx, sy, d, colfn, W, H):
    for a, b, c in ((0, 1, 2), (0, 2, 3)):
        xs = np.array([sx[a], sx[b], sx[c]]); ys = np.array([sy[a], sy[b], sy[c]])
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
        inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
        if not inside.any():
            continue
        dep = l0 * d[a] + l1 * d[b] + l2 * d[c]
        ok, col = colfn((a, b, c), (l0, l1, l2), inside)
        region = zbuf[y0:y1 + 1, x0:x1 + 1]
        ok &= dep < region
        region[ok] = dep[ok]
        img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]


def render(f, name, yaw, pitch, scale, size, centre, foliage=True, ground_rad=6):
    W, H = size
    img = np.zeros((H, W, 3), np.float32) + np.array([0.66, 0.77, 0.9], np.float32)
    zbuf = np.full((H, W), np.inf, np.float32)
    cy, syaw, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    light = np.array([0.45, 0.8, 0.35]); light /= np.linalg.norm(light)

    def project(pts):
        p = pts - centre
        x = p[:, 0] * cy - p[:, 2] * syaw
        z0 = p[:, 0] * syaw + p[:, 2] * cy
        y = p[:, 1] * cp - z0 * sp
        d = -(p[:, 1] * sp + z0 * cp)  # nearer = smaller (camera above, looking down)
        return W / 2 + x * scale, H / 2 - y * scale, d

    bark = tex(BARK.get(name) or 'aspen_log')
    leaf = tex(FRINGE[name] + '_fringe', True)
    ox, oy, oz = f['origin']
    for kind, c, n in ground_quads(f['ground'], ox, oz, ground_rad):
        sx, sy, d = project(c)
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, light))) if kind == 'side' else 0.95
        base = np.array([0.33, 0.5, 0.2] if kind == 'top' else [0.45, 0.33, 0.22], np.float32) * shade
        # block outline: darker edges
        def colfn(tri, l, inside, base=base):
            col = np.broadcast_to(base, l[0].shape + (3,)).copy()
            return inside, col
        raster(img, zbuf, sx, sy, d, colfn, W, H)
    for t, v in f['quads']:
        if t == 2 and not foliage:
            continue
        sx, sy, d = project(v[:, :3])
        n = v[:, 5:8]
        lam = np.clip(n @ light, 0, 1)
        shade = 0.45 + 0.55 * lam
        if t == 2:
            shade = 0.72 + 0.28 * shade
        def colfn(tri, l, inside, v=v, shade=shade, t=t):
            a, b, c = tri
            sh = l[0] * shade[a] + l[1] * shade[b] + l[2] * shade[c]
            u = l[0] * v[a, 3] + l[1] * v[b, 3] + l[2] * v[c, 3]
            w = l[0] * v[a, 4] + l[1] * v[b, 4] + l[2] * v[c, 4]
            texd = leaf if t == 2 else bark
            if texd is None:
                col = np.zeros(l[0].shape + (3,), np.float32) + (np.array([0.3, 0.5, 0.2]) if t == 2 else np.array([0.4, 0.3, 0.2]))
                return inside, col * sh[..., None]
            rgb, al = texd
            th, tw = al.shape
            ti = np.clip((np.mod(w, 1.0) * th).astype(int), 0, th - 1); tj = np.clip((np.mod(u, 1.0) * tw).astype(int), 0, tw - 1)
            ok = inside & (al[ti, tj] > 0.5) if t == 2 else inside
            return ok, rgb[ti, tj] * sh[..., None]
        raster(img, zbuf, sx, sy, d, colfn, W, H)
    return img


def label(img, text):
    from PIL import ImageDraw
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    ImageDraw.Draw(im).text((4, 3), text, fill=(0, 0, 0))
    return np.asarray(im, dtype=np.float32) / 255.0


TREE_W, TREE_H = (220, 240) if mode == 'both' else (420, 560)
rows = []
for fid, f in fx.items():
    name = fid.split('/')[0]
    ox, oy, oz = f['origin']
    row = []
    if mode in ('base', 'both'):
        c = np.array([ox + 0.5, oy + 0.3, oz + 0.5], np.float32)
        for yaw, pitch in ((0.2, 0.25), (-math.pi / 2 + 0.3, 0.3), (math.pi / 2 + 0.5, 0.75), (0.25, 1.2)):  # side, downhill, uphill, above
            row.append(label(render(f, name, yaw, pitch, 95.0, (260, 240), c, foliage=False, ground_rad=3), fid if not row else ''))
    if mode in ('tree', 'both'):
        top = max(v[:, 1].max() for _, v in f['quads'])
        hgt = top - oy + 1
        c = np.array([ox + 0.5, oy + hgt / 2 - 0.5, oz + 0.5], np.float32)
        sc = TREE_H * 0.88 / max(8.0, hgt)
        row.append(label(render(f, name, 0.5, 0.12, sc, (TREE_W, TREE_H), c, foliage=True), fid if not row else ''))
        row.append(render(f, name, 0.5, 0.12, sc, (TREE_W, TREE_H), c, foliage=False))
    if mode == 'trunk':  # lower trunk and first boughs, wood only, three yaws
        top = max(v[:, 1].max() for _, v in f['quads'])
        for k, frac in enumerate((0.18, 0.45)):
            c = np.array([ox + 0.5, oy + (top - oy) * frac, oz + 0.5], np.float32)
            for yaw in (0.3, 2.4):
                row.append(label(render(f, name, yaw, 0.1, 34.0, (300, 420), c, foliage=False, ground_rad=4), fid if not row else ''))
    if mode == 'wood':  # whole tree, wood only, four yaws
        top = max(v[:, 1].max() for _, v in f['quads'])
        hgt = top - oy + 1
        c = np.array([ox + 0.5, oy + hgt / 2 - 0.5, oz + 0.5], np.float32)
        for yaw in (0.3, 1.9, 3.5, 5.1):
            row.append(label(render(f, name, yaw, 0.25, 380.0 / max(6.0, hgt), (300, 420), c, foliage=False, ground_rad=3), fid if not row else ''))
    rows.append(np.concatenate(row, 1))
if mode == 'tree' and len(rows) > 3:  # grid: three trees per sheet row
    rows = [np.concatenate(rows[i:i + 3] + [np.ones_like(rows[0])] * (3 - len(rows[i:i + 3])), 1) for i in range(0, len(rows), 3)]
w = max(r.shape[1] for r in rows)
rows = [np.pad(r, ((0, 0), (0, w - r.shape[1]), (0, 0)), constant_values=1) for r in rows]
sheet = np.concatenate(rows, 0)
Image.fromarray((np.clip(sheet, 0, 1) * 255).astype(np.uint8)).save(out)
print('wrote', out, sheet.shape)
