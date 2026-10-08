#!/usr/bin/env python3
"""[outfitter] Inventory-icon audit: renders every frontierhunts item model the way GuiGraphics.renderItem draws it
(display.gui transform, 16x16 slot, GUI scale 2 = 32 px) and measures how much of the slot it fills.

usage: python3 tools/outfitter/gui_audit.py <repo> <out_dir> [--jar merged.jar]
* item/generated + handheld: layer sprites; elements (block-style JSON, incl. element rotations); frontierhunts:field_shelter
  faces; neoforge:separate_transforms (gui perspective). builtin/entity items are drawn by code (BEWLR) and only listed.
Writes inventory_grid.png (vanilla inventory look, scale 2) and audit.json (extent per item, flags)."""
import os, sys, json, math, zipfile, io
import numpy as np
from PIL import Image, ImageDraw, ImageFont

REPO = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/claude-0/ofx/gui'
JAR = '/home/claude/fh/merged62g8.jar'
if '--jar' in sys.argv:
    JAR = sys.argv[sys.argv.index('--jar') + 1]
MCJAR = '/home/claude/qaserver/libs/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar'
zj = zipfile.ZipFile(JAR)
zm = zipfile.ZipFile(MCJAR)
PATCH = os.path.join(REPO, 'patch')


def read(path):
    p = os.path.join(PATCH, path)
    if os.path.exists(p):
        return open(p, 'rb').read()
    for z in (zj, zm):
        try:
            return z.read(path)
        except KeyError:
            pass
    return None


def model(ref):
    ns, p = ref.split(':') if ':' in ref else ('minecraft', ref)
    b = read('assets/%s/models/%s.json' % (ns, p))
    return json.loads(b) if b else None


_TEX = {}
_TINT = {}


def texture(ref):
    if ref in _TEX:
        return _TEX[ref]
    ns, p = ref.split(':') if ':' in ref else ('minecraft', ref)
    b = read('assets/%s/textures/%s.png' % (ns, p))
    if b is None:
        _TEX[ref] = None
        return None
    im = Image.open(io.BytesIO(b)).convert('RGBA')
    if im.height > im.width and im.height % im.width == 0:
        im = im.crop((0, 0, im.width, im.width))  # animated: first frame
    if im.width > 512:
        im = im.resize((512, 512 * im.height // im.width), Image.BOX)
    _TEX[ref] = np.asarray(im, np.uint8)
    return _TEX[ref]


def resolve(d):
    """Merge a model chain: returns dict(elements, textures, gui, kind, data)."""
    tex = {}
    gui = None
    elements = None
    kind = None
    chain = []
    cur = d
    while cur is not None:
        chain.append(cur)
        par = cur.get('parent')
        if par is None or par.startswith('builtin/') or par.startswith('minecraft:builtin/'):
            if par:
                kind = kind or ('entity' if 'entity' in par else 'generated')
            break
        if par in ('minecraft:item/generated', 'item/generated', 'minecraft:item/handheld', 'item/handheld'):
            kind = kind or 'generated'
        cur = model(par)
    for c in reversed(chain):
        tex.update(c.get('textures', {}))
        if 'display' in c and 'gui' in c['display']:
            gui = c['display']['gui']
        if 'elements' in c:
            elements = c['elements']
    shelter = next((c for c in chain if c.get('loader') == 'frontierhunts:field_shelter'), None)
    if shelter is not None:
        kind = 'shelter'
        d = dict(shelter, display=d.get('display', shelter.get('display')))
    elif elements is not None:
        kind = 'elements'
    if any(c.get('parent', '').endswith('template_spawn_egg') for c in chain):
        kind = 'spawn_egg'
    return dict(elements=elements, textures=tex, gui=gui, kind=kind or 'unknown', data=d,
                gui_light=next((c['gui_light'] for c in chain if 'gui_light' in c), 'side'))


def tref(tex, key):
    k = key
    seen = 0
    while k.startswith('#') and seen < 8:
        k = tex.get(k[1:], k); seen += 1
    return None if k.startswith('#') else k


def rot(axis, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def gui_matrix(g):
    g = g or {}
    r = g.get('rotation', [0, 0, 0]); t = g.get('translation', [0, 0, 0]); s = g.get('scale', [1, 1, 1])
    t = [max(-80, min(80, v)) / 16 for v in t]
    s = [max(-4, min(4, v)) for v in s]
    R = rot('x', r[0]) @ rot('y', r[1]) @ rot('z', r[2])
    M = np.eye(4)
    M[:3, :3] = R @ np.diag(s)
    M[:3, 3] = t
    return M


FACE_N = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}


def element_quads(e):
    f, t = np.array(e['from'], float), np.array(e['to'], float)
    x0, y0, z0 = f; x1, y1, z1 = t
    V = {
        'down': [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        'north': [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        'south': [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        'west': [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        'east': [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
    }
    out = []
    R = None
    if 'rotation' in e:
        ro = e['rotation']
        R = (np.array(ro.get('origin', [8, 8, 8]), float), rot(ro['axis'], ro['angle']), ro.get('rescale', False), ro['axis'], ro['angle'])
    for fn, fd in e.get('faces', {}).items():
        P = np.array(V[fn], float)
        if R is not None:
            o, Rm, rescale, ax, ang = R
            P = (P - o) @ Rm.T
            if rescale:
                k = 1 / math.cos(math.radians(abs(ang)))
                sc = np.array([k, k, k]); sc['xyz'.index(ax)] = 1
                P = P * sc
            P = P + o
        uv = fd.get('uv')
        if uv is None:
            # default uv from position
            if fn in ('up', 'down'):
                uv = [x0, z0, x1, z1]
            elif fn in ('north', 'south'):
                uv = [16 - x1 if fn == 'north' else x0, 16 - y1, 16 - x0 if fn == 'north' else x1, 16 - y0]
            else:
                uv = [z0 if fn == 'west' else 16 - z1, 16 - y1, z1 if fn == 'west' else 16 - z0, 16 - y0]
        u0, v0, u1, v1 = [c / 16 for c in uv]
        UV = np.array([(u0, v0), (u0, v1), (u1, v1), (u1, v0)])
        r = fd.get('rotation', 0)
        if r:
            UV = np.roll(UV, -(r // 90), axis=0)
        out.append((P / 16 - 0.5, UV, fd.get('texture', '#missing'), fd.get('tintindex')))
    return out


def raster(quads, M, size=32, light='side'):
    """quads: (P (4,3) in item space -0.5..0.5, UV, tex array) -> RGBA image of the 16x16 slot at size px."""
    ss = 3
    W = size * ss
    img = np.zeros((W, W, 4))
    zb = np.full((W, W), -np.inf)
    sc = W / 16.0
    for P, UV, tex in quads:
        if tex is None:
            continue
        Q = np.c_[P, np.ones(len(P))] @ M.T
        n = np.cross(Q[1, :3] - Q[0, :3], Q[2, :3] - Q[0, :3])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        if light == 'front':
            l = 1.0
        else:
            # Lighting.setupFor3DItems-ish: lights from upper left front
            L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
            L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)
            nn = n if n[2] >= 0 else -n
            l = min(1.0, 0.4 + 0.6 * (max(0, nn @ L0) + max(0, nn @ L1)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            if max(tri) >= len(P):
                continue
            q = Q[list(tri)]; uv = UV[list(tri)]
            sx = (8 + q[:, 0] * 16) * sc; sy = (8 - q[:, 1] * 16) * sc; z = q[:, 2]
            x0 = max(int(math.floor(sx.min())), 0); x1 = min(int(math.ceil(sx.max())), W - 1)
            y0 = max(int(math.floor(sy.min())), 0); y1 = min(int(math.ceil(sy.max())), W - 1)
            if x1 < x0 or y1 < y0:
                continue
            area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
            if abs(area) < 1e-9:
                continue
            ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
            w0 = ((sx[1] - xs) * (sy[2] - ys) - (sx[2] - xs) * (sy[1] - ys)) / area
            w1 = ((sx[2] - xs) * (sy[0] - ys) - (sx[0] - xs) * (sy[2] - ys)) / area
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            zz = w0 * z[0] + w1 * z[1] + w2 * z[2]
            m &= zz > zb[y0:y1 + 1, x0:x1 + 1]
            if not m.any():
                continue
            u = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
            v = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
            th, tw = tex.shape[:2]
            tx = tex[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)].astype(np.float32) / 255
            m &= tx[..., 3] > 0.1
            sub = img[y0:y1 + 1, x0:x1 + 1]
            sub[m, :3] = tx[m, :3] * l
            sub[m, 3] = 1
            zb[y0:y1 + 1, x0:x1 + 1][m] = zz[m]
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA')
    return im.resize((size, size), Image.LANCZOS) if ss > 1 else im


def generated_quads(r):
    quads = []
    for i in range(5):
        k = r['textures'].get('layer%d' % i)
        if not k:
            break
        t = texture(tref(r['textures'], k))
        P = np.array([(-0.5, 0.5, 0.0), (-0.5, -0.5, 0.0), (0.5, -0.5, 0.0), (0.5, 0.5, 0.0)])
        UV = np.array([(0, 0), (0, 1), (1, 1), (1, 0)], float)
        quads.append((P, UV, t))
    return quads


def render_item(name):
    d = model('frontierhunts:item/' + name)
    if d is None:
        return None, None
    if d.get('loader') == 'neoforge:separate_transforms':
        g = d.get('perspectives', {}).get('gui')
        d = g if g else d.get('base', {})
    r = resolve(d)
    kind = r['kind']
    quads = []
    if kind == 'generated':
        quads = generated_quads(r)
        M = np.eye(4)
        light = 'front'
    elif kind == 'elements':
        for P, UV, tk, tint in [q for e in r['elements'] for q in element_quads(e)]:
            quads.append((P, UV, texture(tref(r['textures'], tk) or 'missing')))
        M = gui_matrix(r['gui'])
        light = r['gui_light']
    elif kind == 'shelter':
        tx = r['textures']
        for f in r['data']['shelter_faces']:
            v = np.array(f['v'], float)
            P = v[:, :3] - 0.5
            UV = v[:, 3:5]
            ref = tref(tx, '#' + f['m']) or 'missing'
            t = texture(ref)
            c = f.get('color', 0xFFFFFF)
            if t is not None and c != 0xFFFFFF:
                key = (ref, c)
                if key not in _TINT:
                    tt = t.astype(np.float32); tt[..., :3] *= np.array([(c >> 16 & 255), (c >> 8 & 255), (c & 255)]) / 255
                    _TINT[key] = tt.astype(np.uint8)
                t = _TINT[key]
            quads.append((P, UV, t))
        M = gui_matrix(r['gui'])
        light = r['gui_light']
    else:
        return None, dict(kind=kind, gui=r['gui'])
    im = raster(quads, M, 32, light)
    a = np.asarray(im)[..., 3] > 30
    if a.any():
        ys, xs = np.nonzero(a)
        ext = dict(w=(xs.max() - xs.min() + 1) / 2, h=(ys.max() - ys.min() + 1) / 2,
                   clipped=bool(xs.min() == 0 or ys.min() == 0 or xs.max() == 31 or ys.max() == 31))
    else:
        ext = dict(w=0, h=0, clipped=False)
    # true extent including what falls outside the slot
    allP = np.concatenate([q[0] for q in quads]) if quads else np.zeros((1, 3))
    Q = np.c_[allP, np.ones(len(allP))] @ M.T
    ext['span_w'] = float((Q[:, 0].max() - Q[:, 0].min()) * 16)
    ext['span_h'] = float((Q[:, 1].max() - Q[:, 1].min()) * 16)
    ext['kind'] = kind
    ext['gui'] = r['gui']
    return im, ext


def items():
    names = set()
    for n in zj.namelist():
        if n.startswith('assets/frontierhunts/models/item/') and n.endswith('.json'):
            names.add(n.split('/')[-1][:-5])
    p = os.path.join(PATCH, 'assets/frontierhunts/models/item')
    if os.path.isdir(p):
        names |= {f[:-5] for f in os.listdir(p) if f.endswith('.json')}
    rm = set()
    rd = os.path.join(PATCH, '_remove')
    if os.path.isdir(rd):
        for f in os.listdir(rd):
            for line in open(os.path.join(rd, f)):
                line = line.strip()
                if line.startswith('assets/frontierhunts/models/item/'):
                    rm.add(line.split('/')[-1][:-5])
    return sorted(names - rm)


def flag(e):
    if e is None or e.get('kind') not in ('generated', 'elements', 'shelter'):
        return None
    big = max(e['span_w'], e['span_h'])
    if e['kind'] == 'generated':
        fill = max(e['w'], e['h'])
        return 'tiny' if fill < 9 else None
    if big > 16.6:
        return 'oversized'
    if big < 10.0:
        return 'undersized'
    return None


def main():
    os.makedirs(OUT, exist_ok=True)
    res = {}
    cells = []
    for n in items():
        im, e = render_item(n)
        res[n] = dict(e or {}, flag=flag(e))
        cells.append((n, im, res[n]['flag']))
    json.dump(res, open(os.path.join(OUT, 'audit.json'), 'w'), indent=1, default=str)
    # inventory-style grid, GUI scale 2: 36 px pitch, slot background like the vanilla inventory
    cols = 18
    rows = (len(cells) + cols - 1) // cols
    P = 36
    grid = Image.new('RGBA', (cols * P + 16, rows * P + 16), (198, 198, 198, 255))
    dr = ImageDraw.Draw(grid)
    for i, (n, im, fl) in enumerate(cells):
        x, y = 8 + (i % cols) * P, 8 + (i // cols) * P
        dr.rectangle([x, y, x + P - 1, y + P - 1], fill=(139, 139, 139, 255))
        dr.line([x, y, x + P - 1, y], fill=(55, 55, 55, 255), width=2)
        dr.line([x, y, x, y + P - 1], fill=(55, 55, 55, 255), width=2)
        dr.line([x, y + P - 1, x + P - 1, y + P - 1], fill=(255, 255, 255, 255), width=2)
        dr.line([x + P - 1, y, x + P - 1, y + P - 1], fill=(255, 255, 255, 255), width=2)
        if im is not None:
            grid.alpha_composite(im, (x + 2, y + 2))
        else:
            dr.text((x + 6, y + 12), 'BE', fill=(60, 60, 60, 255))
        if fl:
            dr.rectangle([x, y, x + P - 1, y + P - 1], outline=(220, 40, 40, 255), width=2)
    grid.save(os.path.join(OUT, 'inventory_grid.png'))
    for n, r in res.items():
        if r['flag']:
            print('FLAG %-34s %-10s span %5.1f x %5.1f  gui %s' % (n, r['flag'], r.get('span_w', 0), r.get('span_h', 0), json.dumps(r.get('gui'))))
    bad = [n for n, r in res.items() if r['flag']]
    print('items', len(res), 'flagged', len(bad), 'code-rendered', sum(1 for r in res.values() if r.get('kind') == 'entity'))


if __name__ == '__main__':
    main()
