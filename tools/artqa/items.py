"""[artqa] Offline GUI item-icon renderer: resolves an item model (parents, textures, block-atlas sprite sources) from an
extracted jar and draws it the way the inventory does - 16 GUI px, nearest-sampled, at a given GUI scale, with the
model's display.gui transform and approximate GUI 3D lighting. Builtin/entity (code-mesh) items return None.

python3 tools/artqa/items.py <extracted jar root> <out.png> [names...]   -> contact sheet at GUI scale 2/3/4
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw

ROOT = None
_SPR = None


def setroot(r):
    global ROOT, _SPR
    ROOT = r
    _SPR = None


def split(ref, default_ns='minecraft'):
    if ':' in ref:
        ns, p = ref.split(':', 1)
    else:
        ns, p = default_ns, ref
    return ns, p


def model(ref):
    ns, p = split(ref)
    f = os.path.join(ROOT, 'assets', ns, 'models', p + '.json')
    if not os.path.exists(f):
        return None
    return json.load(open(f))


def sprites():
    global _SPR
    if _SPR is not None:
        return _SPR
    _SPR = {}
    f = os.path.join(ROOT, 'assets/minecraft/atlases/blocks.json')
    if os.path.exists(f):
        for s in json.load(open(f))['sources']:
            if s['type'] == 'minecraft:single':
                _SPR[s.get('sprite', s['resource'])] = (s['resource'], None)
            elif s['type'] == 'minecraft:unstitch':
                for r in s['regions']:
                    _SPR[r['sprite']] = (s['resource'], (r['x'] / s['divisor_x'], r['y'] / s['divisor_y'],
                                                          r['width'] / s['divisor_x'], r['height'] / s['divisor_y']))
    return _SPR


_TEX = {}
VANILLA_FLAT = {'minecraft:block/dirt': (134, 96, 67), 'minecraft:block/grass_block_top': (110, 160, 70),
                'minecraft:block/spruce_planks': (115, 85, 49), 'minecraft:block/dark_oak_planks': (66, 43, 20),
                'minecraft:block/glass': (200, 225, 235), 'minecraft:block/oak_planks': (162, 130, 78)}


def vanilla_flat(ref):
    ns, p = split(ref)
    c = VANILLA_FLAT.get(ns + ':' + p)
    if c is None:
        return None
    r = np.random.default_rng(len(p))
    a = np.ones((16, 16, 4), np.float32)
    a[..., :3] = (np.array(c, np.float32) / 255) * r.uniform(0.85, 1.1, (16, 16, 1))
    if p.endswith('glass'):
        a[..., 3] = 0.0
        a[0, :, 3] = a[-1, :, 3] = a[:, 0, 3] = a[:, -1, 3] = 1
    return a.clip(0, 1)


def texture(ref):
    """RGBA float array of a block-atlas sprite, or None (vanilla / missing)."""
    if ref in _TEX:
        return _TEX[ref]
    ns, p = split(ref)
    full = ns + ':' + p
    img = None
    f = os.path.join(ROOT, 'assets', ns, 'textures', p + '.png')
    if os.path.exists(f):
        img = Image.open(f).convert('RGBA')
        mc = f + '.mcmeta'
        if os.path.exists(mc):  # animated: first frame
            w = img.width
            img = img.crop((0, 0, w, w))
    elif full in sprites():
        res, box = sprites()[full]
        rns, rp = split(res)
        g = os.path.join(ROOT, 'assets', rns, 'textures', rp + '.png')
        if os.path.exists(g):
            img = Image.open(g).convert('RGBA')
            if box:
                W, H = img.size
                img = img.crop((round(box[0] * W), round(box[1] * H), round((box[0] + box[2]) * W), round((box[1] + box[3]) * H)))
    if img is None and ns == 'minecraft':
        _TEX[ref] = vanilla_flat(ref)
        return _TEX[ref]
    _TEX[ref] = None if img is None else np.asarray(img).astype(np.float32) / 255.0
    return _TEX[ref]


VANILLA_MODELS = {
    'block/block': {'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}}},
    'block/cube_all': {'parent': 'block/block', 'textures': {'particle': '#all', 'down': '#all', 'up': '#all', 'north': '#all',
                       'east': '#all', 'south': '#all', 'west': '#all'}},
    'block/cube_column': {'parent': 'block/block', 'textures': {'particle': '#side', 'down': '#end', 'up': '#end', 'north': '#side',
                          'east': '#side', 'south': '#side', 'west': '#side'}},
}
_CUBE = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {f: {'texture': '#' + f} for f in ('down', 'up', 'north', 'south', 'west', 'east')}}]
VANILLA_MODELS['block/cube_all']['elements'] = _CUBE
VANILLA_MODELS['block/cube_column']['elements'] = _CUBE


def load_any(ref):
    ns, p = split(ref)
    if ns == 'minecraft' and p in VANILLA_MODELS:
        return VANILLA_MODELS[p]
    return model(ref)


def resolve_dict(m):
    chain = [m]
    cur = m
    parents = []
    while cur is not None and 'parent' in cur:
        parents.append(cur['parent'])
        nx = load_any(cur['parent'])
        if nx is None:
            break
        chain.append(nx)
        cur = nx
    out = {'textures': {}, 'display': {}, 'elements': None, 'gui_light': None, 'shelter': None, 'obj': None, 'loader': None}
    for c in reversed(chain):
        if c.get('loader') == 'neoforge:obj':
            out['obj'] = (c['model'], c.get('flip_v', False))
        elif c.get('loader') and c.get('loader') not in ('frontierhunts:field_shelter',):
            out['loader'] = c['loader']
        out['textures'].update(c.get('textures', {}))
        for k, v in c.get('display', {}).items():
            out['display'][k] = v
        if 'elements' in c:
            out['elements'] = c['elements']
        if 'shelter_faces' in c:
            out['shelter'] = c['shelter_faces']
        if 'gui_light' in c:
            out['gui_light'] = c['gui_light']
    return out, parents


def resolve(name, ns='frontierhunts'):
    m = model(ns + ':item/' + name)
    if m.get('loader') == 'neoforge:separate_transforms':
        g = m.get('perspectives', {}).get('gui') or m.get('base')
        g = dict(g)
        if 'gui_light' in m and 'gui_light' not in g:
            g['gui_light'] = m['gui_light']
        m = g
    return resolve_dict(m)


def tref(textures, key, depth=0):
    v = textures.get(key.lstrip('#'))
    if v is None or depth > 8:
        return None
    if v.startswith('#'):
        return tref(textures, v, depth + 1)
    return v


VANILLA_GUI = {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}


def rot_xyz(rx, ry, rz):
    a, b, c = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    Y = np.array([[math.cos(b), 0, math.sin(b)], [0, 1, 0], [-math.sin(b), 0, math.cos(b)]])
    Z = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return X @ Y @ Z


def face_quads(e):
    f, t = np.array(e['from'], float), np.array(e['to'], float)
    fx, fy, fz = f
    tx, ty, tz = t
    Q = {
        'up': [(fx, ty, fz), (tx, ty, fz), (tx, ty, tz), (fx, ty, tz)],
        'down': [(fx, fy, tz), (tx, fy, tz), (tx, fy, fz), (fx, fy, fz)],
        'north': [(tx, ty, fz), (fx, ty, fz), (fx, fy, fz), (tx, fy, fz)],
        'south': [(fx, ty, tz), (tx, ty, tz), (tx, fy, tz), (fx, fy, tz)],
        'west': [(fx, ty, fz), (fx, ty, tz), (fx, fy, tz), (fx, fy, fz)],
        'east': [(tx, ty, tz), (tx, ty, fz), (tx, fy, fz), (tx, fy, tz)],
    }
    UV = {'down': [fx, 16 - tz, tx, 16 - fz], 'up': [fx, fz, tx, tz], 'north': [16 - tx, 16 - ty, 16 - fx, 16 - fy],
          'south': [fx, 16 - ty, tx, 16 - fy], 'west': [fz, 16 - ty, tz, 16 - fy], 'east': [16 - tz, 16 - ty, 16 - fz, 16 - fy]}
    return Q, UV


L0 = np.array([-0.55, 0.75, 0.45]); L0 /= np.linalg.norm(L0)
L1 = np.array([0.35, 0.6, 0.75]); L1 /= np.linalg.norm(L1)


def shade(n, light):
    n = n if n[2] >= 0 else -n
    if light == 'front':
        return min(1.0, 0.45 + 0.75 * max(0.0, n[2]))
    return min(1.0, 0.4 + 0.6 * (max(0.0, n @ L0) + max(0.0, n @ L1)))


MAGENTA = np.ones((16, 16, 4), np.float32) * np.array([1, 0, 1, 1], np.float32)


def quads_of(m):
    """[(pts 4x3 in block units 0..1, uv 4x2 in 0..1, tex array, tint rgb)], vanilla_flag"""
    out = []
    vanilla = False
    tex = m['textures']
    if m['shelter']:
        for f in m['shelter']:
            ref = tref(tex, f['m'])
            T = texture(ref) if ref else None
            if T is None:
                vanilla = True
                T = MAGENTA
            v = np.array(f['v'], float)
            c = f.get('color', 0xFFFFFF)
            out.append((v[:, :3], v[:, 3:5], T, np.array([(c >> 16 & 255), (c >> 8 & 255), c & 255], np.float32) / 255))
        return out, vanilla
    if m['obj']:
        ref, flip = m['obj']
        ns, p = split(ref)
        base = os.path.join(ROOT, 'assets', ns, p)
        mats = {}
        V, VT = [], []
        cur = None
        for line in open(base):
            k = line.split()
            if not k:
                continue
            if k[0] == 'mtllib':
                mn = None
                for ml in open(os.path.join(os.path.dirname(base), k[1])):
                    q = ml.split()
                    if q and q[0] == 'newmtl':
                        mn = q[1]
                    elif q and q[0] == 'map_Kd':
                        r = q[1]
                        mats[mn] = tref(tex, r) if r.startswith('#') else r
            elif k[0] == 'usemtl':
                cur = k[1]
            elif k[0] == 'v':
                V.append([float(x) for x in k[1:4]])
            elif k[0] == 'vt':
                VT.append([float(x) for x in k[1:3]])
            elif k[0] == 'f':
                idx = [tuple(int(t) - 1 for t in s.split('/')[:2]) for s in k[1:]]
                if len(idx) == 3:
                    idx.append(idx[2])
                T = texture(mats.get(cur)) if mats.get(cur) else None
                if T is None:
                    vanilla = True
                    T = MAGENTA
                pts = np.array([V[a] for a, b in idx[:4]])
                uv = np.array([VT[b] for a, b in idx[:4]])
                if not flip:
                    uv[:, 1] = 1 - uv[:, 1]
                out.append((pts, uv, T, np.ones(3, np.float32)))
        return out, vanilla
    for e in m['elements'] or []:
        Q, UV = face_quads(e)
        er = e.get('rotation')
        for fname, face in e.get('faces', {}).items():
            ref = tref(tex, face['texture'])
            T = texture(ref) if ref else None
            if T is None:
                vanilla = True
                T = MAGENTA
            pts = np.array(Q[fname], float)
            if er:
                o = np.array(er['origin'], float)
                ang = er['angle']
                ax = {'x': (1, 0, 0), 'y': (0, 1, 0), 'z': (0, 0, 1)}[er['axis']]
                Rm = rot_xyz(*(np.array(ax) * ang))
                pts = (pts - o) @ Rm.T
                if er.get('rescale'):
                    k = 1 / math.cos(math.radians(abs(ang)))
                    pts = pts * np.array([1 if a else k for a in ax])
                pts = pts + o
            u0, v0, u1, v1 = face.get('uv', UV[fname])
            r90 = (face.get('rotation', 0) // 90) % 4
            uvc = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            uvc = uvc[r90:] + uvc[:r90]
            tint = np.array([0.47, 0.66, 0.29], np.float32) if 'tintindex' in face else np.ones(3, np.float32)
            out.append((pts / 16.0, np.array(uvc, float) / 16.0, T, tint))
    return out, vanilla


def render(name, px, ns='frontierhunts'):
    """Returns (RGBA uint8 array px x px, kind) for GUI size px (16 GUI px * scale)."""
    m, parents = resolve(name, ns)
    if any('builtin/entity' in p for p in parents):
        return None, 'code'
    if any('template_spawn_egg' in p for p in parents):
        return None, 'egg'
    tex = m['textures']
    gen = any(p.endswith('item/generated') or p.endswith('item/handheld') for p in parents)
    if gen and m['elements'] is None:
        out = np.zeros((px, px, 4), np.float32)
        tw = th = 0
        for i in range(8):
            r = tex.get('layer%d' % i)
            if r is None:
                break
            T = texture(r)
            if T is None:
                return None, 'missing:' + r
            th, tw = T.shape[:2]
            ys = ((np.arange(px) + 0.5) / px * th).astype(int).clip(0, th - 1)
            xs = ((np.arange(px) + 0.5) / px * tw).astype(int).clip(0, tw - 1)
            L = T[ys][:, xs]
            a = L[..., 3:4]
            out[..., :3] = L[..., :3] * a + out[..., :3] * (1 - a)
            out[..., 3:4] = a + out[..., 3:4] * (1 - a)
        return (out * 255).round().astype(np.uint8), 'flat %dx%d' % (tw, th)
    if m['loader'] and not m['elements'] and not m['shelter'] and not m['obj']:
        return None, 'code-loader ' + m['loader']
    quads, vanilla = quads_of(m)
    if not quads:
        return None, 'noelements'
    d = m['display'].get('gui', VANILLA_GUI if any('block/block' in p for p in parents) else {})
    R = rot_xyz(*d.get('rotation', [0, 0, 0]))
    tr = np.array(d.get('translation', [0, 0, 0]), float) / 16.0
    sc = np.array(d.get('scale', [1, 1, 1]), float)
    light = m['gui_light'] or 'side'
    col = np.zeros((px, px, 3), np.float32)
    alp = np.zeros((px, px), np.float32)
    zb = np.full((px, px), -1e9, np.float32)
    yy, xx = np.mgrid[0:px, 0:px]
    PX = (xx + 0.5) / px - 0.5
    PY = 0.5 - (yy + 0.5) / px
    for pts, uv, T, tint in quads:
        p = ((pts - 0.5) * sc) @ R.T + tr
        n = np.cross(p[1] - p[0], p[2] - p[0])
        if np.linalg.norm(n) < 1e-12:
            n = np.cross(p[2] - p[0], p[3] - p[0])
        if np.linalg.norm(n) < 1e-12:
            continue
        n = n / np.linalg.norm(n)
        b = shade(n, light)
        th, tw = T.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, bb, c = (p[i] for i in tri)
            ua, ub, uc = (uv[i] for i in tri)
            det = (bb[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (bb[1] - a[1])
            if abs(det) < 1e-14:
                continue
            x0 = max(0, int(math.floor((min(a[0], bb[0], c[0]) + 0.5) * px)))
            x1 = min(px, int(math.ceil((max(a[0], bb[0], c[0]) + 0.5) * px)) + 1)
            y0 = max(0, int(math.floor((0.5 - max(a[1], bb[1], c[1])) * px)))
            y1 = min(px, int(math.ceil((0.5 - min(a[1], bb[1], c[1])) * px)) + 1)
            if x0 >= x1 or y0 >= y1:
                continue
            X = PX[y0:y1, x0:x1]
            Y = PY[y0:y1, x0:x1]
            w1 = ((X - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (Y - a[1])) / det
            w2 = ((bb[0] - a[0]) * (Y - a[1]) - (X - a[0]) * (bb[1] - a[1])) / det
            w0 = 1 - w1 - w2
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * a[2] + w1 * bb[2] + w2 * c[2]
            u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
            v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
            ti = (v * th).astype(int).clip(0, th - 1)
            tj = (u * tw).astype(int).clip(0, tw - 1)
            S = T[ti, tj]
            zs = zb[y0:y1, x0:x1]
            ok = inside & (S[..., 3] > 0.1) & (z > zs)
            cs = col[y0:y1, x0:x1]
            cs[ok] = S[ok][:, :3] * tint * b
            alp[y0:y1, x0:x1][ok] = 1.0
            zs[ok] = z[ok]
    out = np.dstack([col, alp])
    return (out.clip(0, 1) * 255).round().astype(np.uint8), '3d' + (' +vanilla-tex' if vanilla else '')


SLOT = (0x8B, 0x8B, 0x8B)


def slot_tile(icon, scale):
    """An 18x18 GUI px inventory slot at the given scale with the icon composited."""
    s = 18 * scale
    t = Image.new('RGBA', (s, s), (0xC6, 0xC6, 0xC6, 255))
    d = ImageDraw.Draw(t)
    d.rectangle([0, 0, s - 1, s - 1], fill=(0x37, 0x37, 0x37, 255))
    d.rectangle([scale, scale, s - 1, s - 1], fill=(255, 255, 255, 255))
    d.rectangle([scale, scale, s - 1 - scale, s - 1 - scale], fill=SLOT + (255,))
    if icon is not None:
        t.alpha_composite(Image.fromarray(icon), (scale, scale))
    return t


def sheet(names, out, scales=(2, 3, 4), cols=6, ns='frontierhunts', label=True):
    from PIL import ImageFont
    tiles = []
    for n in names:
        row = []
        kind = ''
        for sc in scales:
            ic, kind = render(n, 16 * sc, ns)
            row.append(slot_tile(ic, sc))
        tiles.append((n, kind, row))
    cw = sum(18 * s for s in scales) + 8 * len(scales) + 8
    ch = 18 * max(scales) + 22
    rows = (len(tiles) + cols - 1) // cols
    S = Image.new('RGBA', (cols * cw, rows * ch), (0xC6, 0xC6, 0xC6, 255))
    d = ImageDraw.Draw(S)
    for i, (n, kind, row) in enumerate(tiles):
        x0, y0 = (i % cols) * cw + 4, (i // cols) * ch + 2
        x = x0
        for t in row:
            S.alpha_composite(t, (x, y0))
            x += t.width + 8
        if label:
            d.text((x0, y0 + 18 * max(scales) + 2), (n + ' ' + kind)[:44], fill=(30, 30, 30))
    S.save(out)
    return tiles


if __name__ == '__main__':
    setroot(sys.argv[1])
    names = sys.argv[3:]
    if not names:
        names = sorted(os.path.basename(p)[:-5] for p in os.listdir(os.path.join(ROOT, 'assets/frontierhunts/models/item')))
    sheet(names, sys.argv[2])
