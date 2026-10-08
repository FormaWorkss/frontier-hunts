#!/usr/bin/env python3
"""[polish] Creative-tab icon preview: renders item models the way Minecraft draws them in an inventory slot.

    python3 tools/polish/icon_preview.py <jar or assets dir> <out.png> <gui scale> <id> [id ...]
      ids: frontierhunts:foo, frontierstructures:bar, or bare names (frontierhunts); '|' starts a new row,
      '.' leaves an empty slot.  Also usable as a module: render_item(src, 'frontierhunts:foo', scale) -> RGBA image.

What it models (1.21.1 ItemRenderer / ItemTransform): GUI pose = slot centre, scale(16,-16,16), then the model's
display.gui (translate t/16, rotate XYZ, scale), then -0.5 offset; orthographic, z-buffered, back faces culled,
nearest-texel sampling; element rotations (+rescale), face uv / uv rotation; item/generated layers drawn flat over the
slot; neoforge:separate_transforms (gui perspective); neoforge:obj (v/vt/f + mtl map_Kd). Lighting: gui_light "side"
= two directional lights like Lighting.setupFor3DItems (top brightest, left side lighter than right), "front" = flat.
Vanilla parent models are built in (block/block, cube, cube_all, cube_column, item/generated); vanilla textures are not
available here, so any minecraft: texture is replaced by a flat-shaded stand-in colour (only the silhouette matters).
"""
import io, json, math, os, sys, zipfile
import numpy as np
from PIL import Image

VANILLA = {
    'minecraft:block/block': {
        'gui_light': 'side',
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.40, 0.40, 0.40]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.40, 0.40, 0.40]},
        },
    },
    'minecraft:block/cube': {
        'parent': 'minecraft:block/block',
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            'down': {'texture': '#down'}, 'up': {'texture': '#up'}, 'north': {'texture': '#north'},
            'south': {'texture': '#south'}, 'west': {'texture': '#west'}, 'east': {'texture': '#east'}}}],
    },
    'minecraft:block/cube_all': {'parent': 'minecraft:block/cube', 'textures': {
        'particle': '#all', 'down': '#all', 'up': '#all', 'north': '#all', 'east': '#all', 'south': '#all', 'west': '#all'}},
    'minecraft:block/leaves': {'parent': 'minecraft:block/cube_all'},
    'minecraft:block/cube_column': {'parent': 'minecraft:block/cube', 'textures': {
        'particle': '#side', 'down': '#end', 'up': '#end', 'north': '#side', 'east': '#side', 'south': '#side', 'west': '#side'}},
    'minecraft:item/generated': {'parent': 'minecraft:builtin/generated', 'gui_light': 'front'},
    'minecraft:item/handheld': {'parent': 'minecraft:item/generated'},
    'minecraft:builtin/generated': {},
}
STANDIN = {'dark_oak': (66, 43, 21), 'spruce': (104, 78, 47), 'oak': (162, 130, 78), 'stone': (125, 125, 125)}


class Src:
    """Resource lookup over a jar, an assets dir, or several of them (first match wins)."""

    def __init__(self, *paths):
        self.zips, self.dirs = [], []
        for p in paths:
            if p.endswith('.jar') or p.endswith('.zip'):
                self.zips.append(zipfile.ZipFile(p))
            else:
                self.dirs.append(p)

    def read(self, rel):
        for d in self.dirs:
            f = os.path.join(d, rel)
            if os.path.exists(f):
                return open(f, 'rb').read()
        for z in self.zips:
            try:
                return z.read(rel)
            except KeyError:
                pass
        return None


def rl(s, default_ns='minecraft'):
    if s is None:
        return None
    return s if ':' in s else default_ns + ':' + s


def load_model(src, name):
    name = rl(name)
    ns, path = name.split(':', 1)
    data = src.read(f'assets/{ns}/models/{path}.json')
    if data is not None:
        return json.loads(data)
    return VANILLA.get(name)


def resolve(src, name):
    """Flatten a model chain: elements, textures, gui transform, gui_light, loader info."""
    out = {'textures': {}, 'elements': None, 'gui': None, 'gui_light': None, 'generated': False, 'obj': None, 'loader': None}
    seen = 0
    while name and seen < 16:
        seen += 1
        m = load_model(src, name)
        if m is None:
            out['missing'] = name
            break
        if m.get('loader') == 'neoforge:separate_transforms':
            g = (m.get('perspectives') or {}).get('gui') or m.get('base')
            sub = resolve(src, None) if g is None else resolve_inline(src, g)
            if out['gui'] is not None:
                sub['gui'] = out['gui']
            return sub
        if m.get('loader') == 'neoforge:obj' and out['obj'] is None:
            out['obj'] = m['model']
        elif m.get('loader') and out['loader'] is None:
            out['loader'] = m['loader']
        for k, v in (m.get('textures') or {}).items():
            out['textures'].setdefault(k, v)
        if out['elements'] is None and 'elements' in m:
            out['elements'] = m['elements']
        if out['gui'] is None and 'gui' in (m.get('display') or {}):
            out['gui'] = m['display']['gui']
        if out['gui_light'] is None and 'gui_light' in m:
            out['gui_light'] = m['gui_light']
        if rl(name) == 'minecraft:builtin/generated':
            out['generated'] = True
        name = rl(m.get('parent'))
    return out


def resolve_inline(src, m):
    tmp = dict(m)
    parent = rl(tmp.pop('parent', None))
    out = resolve(src, parent) if parent else {'textures': {}, 'elements': None, 'gui': None, 'gui_light': None,
                                                    'generated': False, 'obj': None, 'loader': None}
    for k, v in (tmp.get('textures') or {}).items():
        out['textures'][k] = v
    if 'elements' in tmp:
        out['elements'] = tmp['elements']
    if 'gui' in (tmp.get('display') or {}):
        out['gui'] = tmp['display']['gui']
    if 'gui_light' in tmp:
        out['gui_light'] = tmp['gui_light']
    return out


def tex_ref(textures, ref, depth=0):
    while ref and ref.startswith('#') and depth < 10:
        ref = textures.get(ref[1:])
        depth += 1
    return ref


_TEX = {}


def texture(src, ref):
    ref = rl(ref)
    if ref in _TEX:
        return _TEX[ref]
    ns, path = ref.split(':', 1)
    data = src.read(f'assets/{ns}/textures/{path}.png')
    if data is None:
        key = next((k for k in STANDIN if k in path), 'stone')
        c = STANDIN[key]
        rng = np.random.default_rng(abs(hash(path)) % 1000)
        im = np.zeros((16, 16, 4), np.float32)
        for y in range(16):
            for x in range(16):
                f = 0.85 + 0.15 * rng.random() - (0.12 if y % 4 == 0 else 0)
                im[y, x] = [c[0] * f / 255, c[1] * f / 255, c[2] * f / 255, 1]
    else:
        im = np.asarray(Image.open(io.BytesIO(data)).convert('RGBA')).astype(np.float32) / 255.0
        h, w = im.shape[:2]
        if h > w:  # animated strip: first frame
            im = im[:w]
    _TEX[ref] = im
    return im


def rot_axis(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# face -> corner selectors (0 = from, 1 = to) ordered top-left, top-right, bottom-right, bottom-left as seen from outside
FACES = {
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}


def default_uv(face, f, t):
    if face == 'north':
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if face == 'south':
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if face == 'east':
        return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]
    if face == 'west':
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    if face == 'up':
        return [f[0], f[2], t[0], t[2]]
    return [f[0], 16 - t[2], t[0], 16 - f[2]]


def model_quads(src, m):
    """List of (4 points in model px space 0..16, 4 uv in 0..1 texture space, texture array, tint)."""
    quads = []
    if m['obj']:
        ns, path = rl(m['obj']).split(':', 1)
        data = src.read(f'assets/{ns}/{path}')
        if data is None:
            return quads
        mtl_tex = {}
        lines = data.decode().splitlines()
        base = os.path.dirname(path)
        for l in lines:
            if l.startswith('mtllib'):
                md = src.read(f'assets/{ns}/{base}/{l.split()[1]}')
                cur = None
                for ml in (md.decode().splitlines() if md else []):
                    ml = ml.strip()
                    if ml.startswith('newmtl'):
                        cur = ml.split()[1]
                    elif ml.startswith('map_Kd') and cur:
                        mtl_tex[cur] = ml.split()[1]
        V, VT = [], []
        cur_tex = None
        for l in lines:
            p = l.split()
            if not p:
                continue
            if p[0] == 'v':
                V.append([float(x) * 16 for x in p[1:4]])
            elif p[0] == 'vt':
                VT.append([float(p[1]), 1 - float(p[2])])
            elif p[0] == 'usemtl':
                ref = mtl_tex.get(p[1], '')
                ref = tex_ref(m['textures'], ref) if ref.startswith('#') else ref
                cur_tex = texture(src, ref) if ref else None
            elif p[0] == 'f':
                idx = [q.split('/') for q in p[1:]]
                pts = [np.array(V[int(i[0]) - 1]) for i in idx]
                uvs = [VT[int(i[1]) - 1] if len(i) > 1 and i[1] else (0, 0) for i in idx]
                if len(pts) == 3:
                    pts.append(pts[2]); uvs.append(uvs[2])
                # OBJ faces are counter-clockwise from outside; this renderer's quads are clockwise
                quads.append((pts[::-1], uvs[::-1], cur_tex, False))
        return quads
    for e in m['elements'] or []:
        f, t = e['from'], e['to']
        r = e.get('rotation')
        R = None
        if r:
            R = rot_axis(r['axis'], r['angle'])
            if r.get('rescale'):
                k = 1 / math.cos(math.radians(abs(r['angle'])))
                sc = np.array([k if a != r['axis'] else 1 for a in 'xyz'])
                R = R @ np.diag(sc)
        for face, corners in FACES.items():
            fd = (e.get('faces') or {}).get(face)
            if not fd:
                continue
            pts = []
            for c in corners:
                p = np.array([f[i] if c[i] == 0 else t[i] for i in range(3)], float)
                if R is not None:
                    o = np.array(r['origin'], float)
                    p = R @ (p - o) + o
                pts.append(p)
            uv = fd.get('uv') or default_uv(face, f, t)
            u0, v0, u1, v1 = [x / 16 for x in uv]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            rot = int(fd.get('rotation', 0)) // 90 % 4
            uvs = uvs[rot:] + uvs[:rot]
            ref = tex_ref(m['textures'], fd.get('texture', ''))
            T = texture(src, ref) if ref else None
            quads.append((pts, uvs, T, 'tintindex' in fd))
    return quads


def gui_matrix(gui):
    g = gui or {}
    rx, ry, rz = g.get('rotation', [0, 0, 0])
    R = rot_axis('x', rx) @ rot_axis('y', ry) @ rot_axis('z', rz)
    S = np.diag(g.get('scale', [1, 1, 1]))
    T = np.array(g.get('translation', [0, 0, 0]), float) / 16
    return R @ S, T


L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)
# Lighting.setupFor3DItems: lights rotated into the GUI's item view (top lit, left side brighter than right)
_VIEW = rot_axis('y', -22.5) @ rot_axis('x', 135)
L0v = np.diag([1, -1, 1]) @ (_VIEW @ L0)
L1v = np.diag([1, -1, 1]) @ (_VIEW @ L1)


def render_item(src, item, scale=3, tint=(0.45, 0.62, 0.3)):
    """RGBA numpy image (16*scale square) of one item as drawn in a slot."""
    size = 16 * scale
    img = np.zeros((size, size, 4), np.float32)
    item = rl(item, 'frontierhunts')
    ns, path = item.split(':', 1)
    m = resolve(src, f'{ns}:item/{path}')
    if m.get('missing') and m['elements'] is None and not m['generated']:
        return None, m
    if m['generated']:
        i = 0
        while f'layer{i}' in m['textures']:
            T = texture(src, tex_ref(m['textures'], m['textures'][f'layer{i}']))
            h, w = T.shape[:2]
            ys = (np.arange(size) * h // size)
            xs = (np.arange(size) * w // size)
            L = T[ys][:, xs]
            a = L[..., 3:4]
            img[..., :3] = img[..., :3] * (1 - a) + L[..., :3] * a
            img[..., 3:4] = np.maximum(img[..., 3:4], a)
            i += 1
        return img, m
    if m['loader'] and m['elements'] is None and not m['obj']:
        return None, m
    M, Tr = gui_matrix(m['gui'])
    side = (m['gui_light'] or 'side') == 'side'
    zb = np.full((size, size), -1e9, np.float32)
    for pts, uvs, T, tinted in model_quads(src, m):
        P = []
        for p in pts:
            q = M @ (p / 16 - 0.5) + Tr
            P.append(np.array([size / 2 + q[0] * size, size / 2 - q[1] * size, q[2]]))
        # corners run TL, TR, BR, BL seen from outside (clockwise): outward normal = (BL - TL) x (TR - TL)
        wn = np.cross(M @ (pts[3] - pts[0]), M @ (pts[1] - pts[0]))
        if np.linalg.norm(wn) < 1e-12:
            wn = np.cross(M @ (pts[2] - pts[1]), M @ (pts[0] - pts[1]))
        ln = np.linalg.norm(wn)
        if ln < 1e-12:
            continue
        wn = wn / ln
        if wn[2] <= 1e-6:  # camera looks down -z from +z: back face
            continue
        if side:
            shade = min(1.0, 0.4 + 0.6 * (max(0, float(wn @ L0v)) + max(0, float(wn @ L1v))))
        else:
            shade = 1.0
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (P[i] for i in tri)
            ua, ub, uc = (np.array(uvs[i]) for i in tri)
            minx = int(max(0, math.floor(min(a[0], b[0], c[0]))))
            maxx = int(min(size - 1, math.ceil(max(a[0], b[0], c[0]))))
            miny = int(max(0, math.floor(min(a[1], b[1], c[1]))))
            maxy = int(min(size - 1, math.ceil(max(a[1], b[1], c[1]))))
            if minx > maxx or miny > maxy:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(d) < 1e-12:
                continue
            w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
            w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-5) & (w1 >= -1e-5) & (w2 >= -1e-5)
            if not inside.any():
                continue
            iy, ix = np.nonzero(inside)
            gy, gx = iy + miny, ix + minx
            z = (w0 * a[2] + w1 * b[2] + w2 * c[2])[iy, ix]
            u = (w0 * ua[0] + w1 * ub[0] + w2 * uc[0])[iy, ix]
            v = (w0 * ua[1] + w1 * ub[1] + w2 * uc[1])[iy, ix]
            if T is None:
                col = np.tile(np.array([1, 0, 1, 1.0]), (len(z), 1))
            else:
                h, w = T.shape[:2]
                tx = np.clip((u * w).astype(int), 0, w - 1)
                ty = np.clip((v * h).astype(int), 0, h - 1)
                col = T[ty, tx].copy()
            keep = (col[:, 3] > 0.1) & (z > zb[gy, gx])
            gy, gx, z, col = gy[keep], gx[keep], z[keep], col[keep]
            if tinted:
                col[:, :3] *= np.array(tint)
            col[:, :3] *= shade
            zb[gy, gx] = z
            col[:, 3] = 1
            img[gy, gx] = col
    return img, m


def bbox(img):
    if img is None:
        return None
    a = img[..., 3] > 0.05
    if not a.any():
        return None
    ys, xs = np.nonzero(a)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def sheet(src, rows, scale=3, label=True):
    """Creative-inventory-like grid (18 px slot pitch, slot bg) of rows of item ids."""
    pitch = 18 * scale
    cols = max(len(r) for r in rows)
    W, H = cols * pitch + 2 * scale, len(rows) * pitch + 2 * scale
    out = Image.new('RGBA', (W, H), (198, 198, 198, 255))
    px = out.load()
    info = []
    for ri, row in enumerate(rows):
        for ci, item in enumerate(row):
            x0, y0 = scale + ci * pitch, scale + ri * pitch
            # slot: dark top-left border, white bottom-right, grey fill
            for y in range(pitch):
                for x in range(pitch):
                    if x < scale or y < scale:
                        c = (55, 55, 55, 255)
                    elif x >= pitch - scale or y >= pitch - scale:
                        c = (255, 255, 255, 255)
                    else:
                        c = (139, 139, 139, 255)
                    px[x0 + x, y0 + y] = c
            if item == '.':
                continue
            img, m = render_item(src, item, scale)
            if img is None:
                for y in range(16 * scale):
                    for x in range(16 * scale):
                        if (x // scale + y // scale) % 2 == 0:
                            px[x0 + scale + x, y0 + scale + y] = (200, 0, 200, 255)
                info.append((item, None, m.get('loader') or m.get('missing')))
                continue
            im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA')
            out.alpha_composite(im, (x0 + scale, y0 + scale))
            bb = bbox(img)
            info.append((item, tuple(round(v / scale, 1) for v in bb) if bb else None, None))
    return out, info


def main():
    src = Src(*sys.argv[1].split(','))
    out, scale = sys.argv[2], int(sys.argv[3])
    rows, row = [], []
    for a in sys.argv[4:]:
        if a == '|':
            rows.append(row); row = []
        else:
            row.append(a)
    if row:
        rows.append(row)
    im, info = sheet(src, rows, scale)
    im.save(out)
    for item, bb, note in info:
        if bb:
            w, h = bb[2] - bb[0], bb[3] - bb[1]
            cx, cy = (bb[0] + bb[2]) / 2 - 8, (bb[1] + bb[3]) / 2 - 8
            print(f'{item:40s} {w:5.1f} x {h:5.1f} px  centre offset ({cx:+.1f},{cy:+.1f})')
        else:
            print(f'{item:40s} not rendered ({note})')


if __name__ == '__main__':
    main()


def fit(src, model, rotation, target=14.5, max_w=None, max_h=None, lift=0.0):
    """display.gui transform that centres `model` (a model name) in the slot at `rotation`, its larger on-screen extent
    = target px (optionally capped per axis). Returns {'rotation','translation','scale'} rounded for JSON."""
    m = resolve(src, model)
    R = rot_axis('x', rotation[0]) @ rot_axis('y', rotation[1]) @ rot_axis('z', rotation[2])
    pts = [p for q in model_quads(src, m) for p in q[0]]
    P = np.array([R @ (p / 16 - 0.5) for p in pts]) * 16  # screen px at scale 1 (y up)
    lo, hi = P.min(0), P.max(0)
    w, h = hi[0] - lo[0], hi[1] - lo[1]
    s = target / max(w, h)
    if max_w:
        s = min(s, max_w / w)
    if max_h:
        s = min(s, max_h / h)
    cx, cy = (lo[0] + hi[0]) / 2 * s, (lo[1] + hi[1]) / 2 * s
    r = lambda v: round(float(v), 3)
    return {'rotation': list(rotation), 'translation': [r(-cx), r(-cy + lift), 0], 'scale': [r(s)] * 3}
