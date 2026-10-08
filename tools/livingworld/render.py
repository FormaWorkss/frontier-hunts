#!/usr/bin/env python3
"""[livingworld] Offline isometric renderer for voxel dumps of worldgen structures.

Renders real block models (blockstates -> models -> elements with textures) from the built mod jar and the vanilla
1.21.1 client jar (for preview only; nothing is shipped), with a z-buffer, alpha cutout, simple water translucency,
directional face shading and biome tints. BER-only blocks / entities are drawn as labelled stand-in boxes.

usage: render.py <dump.json> <out.png> [--mod JAR] [--vanilla JAR] [--view 0..3] [--scale 16]
dump: {"blocks": [[x,y,z,"ns:id[k=v,...]"], ...], "entities": [[x,y,z,"ns:id",yaw], ...], "title": "..."}
"""
import json, math, sys, zipfile, io, os, re, argparse
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ap = argparse.ArgumentParser()
ap.add_argument('dump'); ap.add_argument('out')
ap.add_argument('--mod', default=os.environ.get('LW_MODJAR', ''))
ap.add_argument('--vanilla', default='/mnt/user-data/uploads/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
ap.add_argument('--view', type=int, default=0)
ap.add_argument('--scale', type=float, default=16)
ap.add_argument('--crop', default='')
ap.add_argument('--noleaves', action='store_true', help='hide leaves (see into forests)')
ap.add_argument('--sheet', action='store_true', help='two views (south-east and north-west) side by side')
args = ap.parse_args()

JARS = [zipfile.ZipFile(p) for p in [args.mod, args.vanilla] if p]
NAMES = [set(z.namelist()) for z in JARS]


def read(path):
    for z, n in zip(JARS, NAMES):
        if path in n:
            return z.read(path)
    return None


_json = {}


def jload(path):
    if path not in _json:
        b = read(path)
        _json[path] = json.loads(b) if b else None
    return _json[path]


def split_id(s, default='minecraft'):
    if ':' in s:
        a, b = s.split(':', 1)
        return a, b
    return default, s


_tex = {}


def texture(ref):
    if ref in _tex:
        return _tex[ref]
    if ref.startswith('color:#'):
        c = ref[7:]
        im = np.zeros((4, 4, 4), np.float32); im[...] = (int(c[0:2], 16) / 255, int(c[2:4], 16) / 255, int(c[4:6], 16) / 255, 1.0)
        _tex[ref] = im
        return im
    ns, p = split_id(ref)
    b = read(f'assets/{ns}/textures/{p}.png')
    if b is None:
        im = np.zeros((16, 16, 4), np.float32); im[...] = (1.0, 0.0, 1.0, 1.0)
    else:
        img = Image.open(io.BytesIO(b)).convert('RGBA')
        w, h = img.size
        if h > w and h % w == 0:  # animated strip: first frame
            img = img.crop((0, 0, w, w))
        im = np.asarray(img).astype(np.float32) / 255.0
    _tex[ref] = im
    return im


_model = {}


def model(ref):
    """resolved model: (elements, textures, ambient)"""
    if ref in _model:
        return _model[ref]
    ns, p = split_id(ref)
    if not p.startswith('block/') and not p.startswith('item/') and '/' not in p:
        p = 'block/' + p
    m = jload(f'assets/{ns}/models/{p}.json')
    if m is None:
        _model[ref] = None
        return None
    tex = {}
    elements = None
    chain = []
    cur = m
    while cur is not None:
        chain.append(cur)
        par = cur.get('parent')
        if not par or par.startswith('builtin/'):
            if par:
                chain.append({'builtin': par})
            break
        pns, pp = split_id(par)
        cur = jload(f'assets/{pns}/models/{pp}.json')
    for c in reversed(chain):
        tex.update(c.get('textures', {}))
        if 'elements' in c:
            elements = c['elements']
    builtin = any('builtin' in c for c in chain)

    def resolve(t, depth=0):
        while t.startswith('#') and depth < 10:
            t = tex.get(t[1:], 'missing'); depth += 1
        return t
    out = []
    for e in elements or []:
        faces = {}
        for fn, f in e.get('faces', {}).items():
            faces[fn] = dict(f, texture=resolve(f['texture']))
        out.append(dict(e, faces=faces))
    res = (out, builtin, {k: resolve(v) for k, v in tex.items()})
    if m.get('loader') == 'neoforge:obj':
        res = ('poly', load_obj(m['model']), {})
    elif m.get('loader') == 'frontierhunts:reserve_roof':
        res = ('poly', roof_polys(m, p), {})
    if m.get('loader') == 'frontierhunts:field_shelter':
        res = ('shelter', m.get('shelter_faces', []), {k: resolve(v) for k, v in tex.items()})
    _model[ref] = res
    return res


def load_obj(ref):
    ns, p = split_id(ref)
    b = read(f'assets/{ns}/{p}')
    if b is None:
        return []
    mtl = {}
    vs, vts, faces = [], [], []
    cur = None
    for line in b.decode('utf-8', 'replace').splitlines():
        t = line.split()
        if not t:
            continue
        if t[0] == 'mtllib':
            base = p.rsplit('/', 1)[0]
            mb = read(f'assets/{ns}/{base}/{t[1]}')
            if mb:
                name = None
                for ml in mb.decode().splitlines():
                    mt = ml.split()
                    if not mt: continue
                    if mt[0] == 'newmtl': name = mt[1]; mtl[name] = {'tex': 'missing', 'tint': False}
                    elif mt[0] == 'map_Kd' and name: mtl[name]['tex'] = mt[1]
                    elif mt[0] == 'neoforge_TintIndex' and name: mtl[name]['tint'] = True
        elif t[0] == 'usemtl':
            cur = mtl.get(t[1], {'tex': 'missing', 'tint': False})
        elif t[0] == 'v':
            vs.append([float(a) for a in t[1:4]])
        elif t[0] == 'vt':
            vts.append([float(t[1]), 1 - float(t[2])])
        elif t[0] == 'f':
            pts, uvs = [], []
            for c in t[1:]:
                ix = c.split('/')
                pts.append(vs[int(ix[0]) - 1])
                uvs.append(vts[int(ix[1]) - 1] if len(ix) > 1 and ix[1] else [0, 0])
            faces.append((np.array(pts, np.float32) * 16.0, np.array(uvs, np.float32), (cur or {}).get('tex', 'missing'), (cur or {}).get('tint', False)))
    return faces


def roof_polys(m, path):
    tent = 'slope' in m or 'tent' in path
    high = m.get('high', False); ridge = m.get('ridge', False); gable = m.get('gable', False)
    f = 1.0 if tent else 0.5
    tex = 'color:#d9cfb4' if tent else 'color:#4b4038'
    side = 'color:#cfc3a3' if tent else 'color:#6b5a48'
    def h(x):
        i = min(15.0, max(0.0, x))
        return (8 if high else 0) + ((min(i + 1, 16 - i)) if ridge else (i + 1)) * f
    xs = [0, 8, 16] if ridge else [0, 16]
    out = []
    for a, b2 in zip(xs[:-1], xs[1:]):
        ha, hb = h(a if a < 16 else 15), h(b2 if b2 < 16 else 15)
        pts = np.array([[a, ha, 0], [b2, hb, 0], [b2, hb, 16], [a, ha, 16]], np.float32)
        out.append((pts, np.array([[0, 0], [1, 0], [1, 1], [0, 1]], np.float32), tex, False))
        if gable:
            for zz in (0, 16):
                tri = np.array([[a, 0, zz], [b2, 0, zz], [b2, hb, zz], [a, ha, zz]], np.float32)
                out.append((tri, np.array([[0, 0], [1, 0], [1, 1], [0, 1]], np.float32), side, False))
    return out


def parse_state(s):
    m = re.match(r'([^\[]+)(?:\[(.*)\])?$', s)
    bid = m.group(1)
    props = {}
    if m.group(2):
        for kv in m.group(2).split(','):
            if '=' in kv:
                k, v = kv.split('=', 1); props[k.strip()] = v.strip()
    if ':' not in bid:
        bid = 'minecraft:' + bid
    return bid, props


def when_ok(w, props):
    if 'OR' in w:
        return any(when_ok(x, props) for x in w['OR'])
    if 'AND' in w:
        return all(when_ok(x, props) for x in w['AND'])
    for k, v in w.items():
        if str(props.get(k, '')).lower() not in str(v).lower().split('|'):
            return False
    return True


def state_models(bid, props):
    ns, p = split_id(bid)
    bs = jload(f'assets/{ns}/blockstates/{p}.json')
    if bs is None:
        return None
    out = []
    if 'variants' in bs:
        best = None
        for key, v in bs['variants'].items():
            ok = True
            if key:
                for kv in key.split(','):
                    k, val = kv.split('=')
                    if props.get(k) is not None and props.get(k) != val:
                        ok = False; break
                    if props.get(k) is None:
                        pass
            if ok:
                best = v; break
        if best is None:
            best = list(bs['variants'].values())[0]
        if isinstance(best, list):
            best = best[0]
        out.append(best)
    else:
        for part in bs.get('multipart', []):
            if 'when' not in part or when_ok(part['when'], props):
                a = part['apply']
                out.append(a[0] if isinstance(a, list) else a)
    return out


def rot_axis(pts, axis, ang, origin, rescale=False):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    o = np.array(origin, np.float32)
    q = pts - o
    x, y, z = q[:, 0].copy(), q[:, 1].copy(), q[:, 2].copy()
    if axis == 'y':
        nx, nz = x * c + z * s, -x * s + z * c; x, z = nx, nz
        if rescale: x /= c; z /= c
    elif axis == 'x':
        ny, nz = y * c - z * s, y * s + z * c; y, z = ny, nz
        if rescale: y /= c; z /= c
    else:
        nx, ny = x * c - y * s, x * s + y * c; x, y = nx, ny
        if rescale: x /= c; y /= c
    return np.stack([x, y, z], 1) + o


FACE_DEF = {  # corner(u0,v0), u-dir, v-dir as functions of bounds
    'up': lambda a, b: ((a[0], b[1], a[2]), (b[0] - a[0], 0, 0), (0, 0, b[2] - a[2]), (0, 1, 0)),
    'down': lambda a, b: ((a[0], a[1], b[2]), (b[0] - a[0], 0, 0), (0, 0, -(b[2] - a[2])), (0, -1, 0)),
    'north': lambda a, b: ((b[0], b[1], a[2]), (-(b[0] - a[0]), 0, 0), (0, -(b[1] - a[1]), 0), (0, 0, -1)),
    'south': lambda a, b: ((a[0], b[1], b[2]), (b[0] - a[0], 0, 0), (0, -(b[1] - a[1]), 0), (0, 0, 1)),
    'west': lambda a, b: ((a[0], b[1], a[2]), (0, 0, b[2] - a[2]), (0, -(b[1] - a[1]), 0), (-1, 0, 0)),
    'east': lambda a, b: ((b[0], b[1], b[2]), (0, 0, -(b[2] - a[2])), (0, -(b[1] - a[1]), 0), (1, 0, 0)),
}


def default_uv(fn, a, b):
    if fn == 'down': return [a[0], 16 - b[2], b[0], 16 - a[2]]
    if fn == 'up': return [a[0], a[2], b[0], b[2]]
    if fn == 'north': return [16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]]
    if fn == 'south': return [a[0], 16 - b[1], b[0], 16 - a[1]]
    if fn == 'west': return [a[2], 16 - b[1], b[2], 16 - a[1]]
    return [16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]]


GRASS = np.array([0.47, 0.75, 0.35]); FOLIAGE = np.array([0.36, 0.62, 0.25]); SPRUCE = np.array([0.38, 0.6, 0.38]); WATER = np.array([0.25, 0.46, 0.89])


def tint_for(bid):
    p = bid.split(':')[1]
    if 'spruce_leaves' in p: return SPRUCE
    if 'birch_leaves' in p: return np.array([0.5, 0.65, 0.33])
    if 'leaves' in p or 'vine' in p: return FOLIAGE
    if 'water' in p: return WATER
    return GRASS


# ---------------------------------------------------------------- quads
POLYS = []  # (pts Nx3, uvs Nx2 normalized, tex, tint or None, translucent, double_sided)


def add_quad(c0, u, v, nrm, tex, uv, rot, tint, trans):
    st = [(0, 0), (1, 0), (1, 1), (0, 1)]
    pts = np.array([c0, c0 + u, c0 + u + v, c0 + v], np.float32)
    uvs = []
    r = int(rot) % 360
    for s, t in st:
        ss, tt = s, t
        if r == 90: ss, tt = t, 1 - s
        elif r == 180: ss, tt = 1 - s, 1 - t
        elif r == 270: ss, tt = 1 - t, s
        uvs.append(((uv[0] + ss * (uv[2] - uv[0])) / 16.0, (uv[1] + tt * (uv[3] - uv[1])) / 16.0))
    n = np.cross(u, v)
    if float(n @ nrm) < 0:
        pts = pts[::-1].copy(); uvs = uvs[::-1]
    POLYS.append((pts, np.array(uvs, np.float32), tex, tint, trans, False))
STANDINS = []  # (x,y,z, w,h,d, color, label)
FULL = {}


def is_full(bid, props):
    key = bid + str(sorted(props.items()))
    if key in FULL: return FULL[key]
    r = False
    ms = state_models(bid, props)
    if ms and len(ms) == 1:
        m = model(ms[0]['model'])
        if m and len(m[0]) == 1:
            e = m[0][0]
            if e['from'] == [0, 0, 0] and e['to'] == [16, 16, 16] and 'rotation' not in e:
                p = bid.split(':')[1]
                r = not any(k in p for k in ('leaves', 'glass', 'ice', 'needles', 'boughs', 'spawner', 'slime', 'honey'))
    FULL[key] = r
    return r


def add_block(x, y, z, state, solid_at):
    bid, props = parse_state(state)
    p = bid.split(':')[1]
    if p in ('air', 'cave_air', 'void_air', 'structure_void'):
        return
    if p in ('water', 'lava'):
        lvl = int(props.get('level', 0))
        top = 14 / 16 if lvl == 0 else max(2, 14 - 2 * lvl) / 16
        if solid_at(x, y + 1, z) != 'fluid':
            add_box_quads(x, y, z, [0, 0, 0], [16, top * 16, 16], 'minecraft:block/water_still' if p == 'water' else 'minecraft:block/lava_still', p == 'water', faces=('up',))
        for fn, (dx, dz) in (('south', (0, 1)), ('east', (1, 0)), ('north', (0, -1)), ('west', (-1, 0))):
            if solid_at(x + dx, y, z + dz) is None:
                add_box_quads(x, y, z, [0, 0, 0], [16, top * 16, 16], 'minecraft:block/water_still' if p == 'water' else 'minecraft:block/lava_still', p == 'water', faces=(fn,))
        return
    ms = state_models(bid, props)
    if not ms:
        STANDINS.append((x, y, z, 1, 1, 1, (255, 0, 255), p)); return
    drew = False
    for mv in ms:
        m = model(mv['model'])
        if not m:
            continue
        if m[0] == 'poly':
            rx, ry = mv.get('x', 0), mv.get('y', 0)
            for pts, uvs, tex, tnt in m[1]:
                vs = pts.copy()
                if rx: vs = rot_axis(vs, 'x', -rx, (8, 8, 8))
                if ry: vs = rot_axis(vs, 'y', -ry, (8, 8, 8))
                vs = vs / 16.0 + np.array([x, y, z], np.float32)
                POLYS.append((vs, uvs, tex, FOLIAGE if tnt else None, False, True))
            drew = True
            continue
        if m[0] == 'shelter':
            rx, ry = mv.get('x', 0), mv.get('y', 0)
            for f in m[1]:
                vs = np.array([q[:3] for q in f['v']], np.float32) * 16.0
                if rx: vs = rot_axis(vs, 'x', -rx, (8, 8, 8))
                if ry: vs = rot_axis(vs, 'y', -ry, (8, 8, 8))
                vs = vs / 16.0 + np.array([x, y, z], np.float32)
                uvs = np.array([q[3:5] for q in f['v']], np.float32)
                col = f.get('color', 0xFFFFFF)
                tint = np.array([(col >> 16 & 255) / 255.0, (col >> 8 & 255) / 255.0, (col & 255) / 255.0])
                POLYS.append((vs, uvs, m[2].get(f['m'], 'missing'), tint, False, True))
            drew = True
            continue
        elements, builtin, texs = m
        if not elements:
            if builtin or texs.get('particle'):
                special(x, y, z, bid, props, texs.get('particle', 'minecraft:block/oak_planks'))
                drew = True
            continue
        rx, ry = mv.get('x', 0), mv.get('y', 0)
        for e in elements:
            a, b = e['from'], e['to']
            for fn, f in e['faces'].items():
                cf = f.get('cullface')
                if cf:
                    d = {'up': (0, 1, 0), 'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}[cf]
                    # rotate cullface by blockstate rotation
                    v = np.array([d], np.float32)
                    if rx: v = rot_axis(v, 'x', -rx, (0, 0, 0))
                    if ry: v = rot_axis(v, 'y', -ry, (0, 0, 0))
                    dd = tuple(int(round(c)) for c in v[0])
                    if solid_at(x + dd[0], y + dd[1], z + dd[2]) == 'full':
                        continue
                corner, ud, vd, n = FACE_DEF[fn](a, b)
                pts = np.array([corner, np.add(corner, ud), np.add(corner, vd), np.add(corner, n)], np.float32)
                # normal handled as point offset from corner
                if 'rotation' in e:
                    r = e['rotation']
                    pts = rot_axis(pts, r['axis'], r['angle'], r['origin'], r.get('rescale', False))
                if rx: pts = rot_axis(pts, 'x', -rx, (8, 8, 8))
                if ry: pts = rot_axis(pts, 'y', -ry, (8, 8, 8))
                pts = pts / 16.0 + np.array([x, y, z], np.float32)
                c0 = pts[0]; u = pts[1] - c0; v = pts[2] - c0; nn = pts[3] - c0
                uv = f.get('uv') or default_uv(fn, a, b)
                tint = tint_for(bid) if 'tintindex' in f else None
                add_quad(c0, u, v, nn, f['texture'], uv, f.get('rotation', 0), tint, False)
                drew = True
    if not drew:
        STANDINS.append((x, y, z, 1, 1, 1, (255, 0, 255), p))


def add_box_quads(x, y, z, a, b, tex, translucent, faces=('up', 'down', 'north', 'south', 'west', 'east'), tint=None):
    for fn in faces:
        corner, ud, vd, n = FACE_DEF[fn](a, b)
        pts = np.array([corner, np.add(corner, ud), np.add(corner, vd), np.add(corner, n)], np.float32) / 16.0 + np.array([x, y, z], np.float32)
        c0 = pts[0]
        uv = default_uv(fn, a, b)
        add_quad(c0, pts[1] - c0, pts[2] - c0, pts[3] - c0, tex, uv, 0, WATER if translucent else tint, translucent)


def special(x, y, z, bid, props, particle):
    p = bid.split(':')[1]
    if 'chest' in p:
        add_box_quads(x, y, z, [1, 0, 1], [15, 14, 15], 'minecraft:block/spruce_planks', False)
        add_box_quads(x, y, z, [7, 7, 0.5], [9, 11, 1], 'minecraft:block/iron_block', False)
    elif 'wall_hanging_sign' in p or 'hanging_sign' in p:
        add_box_quads(x, y, z, [1, 0, 7], [15, 10, 9], particle, False)
    elif 'wall_sign' in p:
        f = props.get('facing', 'north')
        box = {'north': ([0, 4, 14], [16, 12, 16]), 'south': ([0, 4, 0], [16, 12, 2]), 'west': ([14, 4, 0], [16, 12, 16]), 'east': ([0, 4, 0], [2, 12, 16])}[f]
        add_box_quads(x, y, z, box[0], box[1], particle, False)
    elif p.endswith('_sign'):
        add_box_quads(x, y, z, [7, 0, 7], [9, 9, 9], particle, False)
        add_box_quads(x, y, z, [0, 9, 7], [16, 17, 9], particle, False)
    elif 'banner' in p:
        col = p.replace('_wall_banner', '').replace('_banner', '')
        add_box_quads(x, y, z, [1, 0, 7.5], [15, 28, 8.5], f'minecraft:block/{col}_wool', False)
        add_box_quads(x, y, z, [0, 28, 7], [16, 29, 9], 'minecraft:block/dark_oak_planks', False)
    elif p.endswith('_bed'):
        add_box_quads(x, y, z, [0, 3, 0], [16, 9, 16], f'minecraft:block/{p.replace("_bed", "")}_wool', False)
    elif 'skull' in p or 'head' in p:
        add_box_quads(x, y, z, [4, 0, 4], [12, 8, 12], 'minecraft:block/bone_block_side', False)
    elif p == 'decorated_pot':
        add_box_quads(x, y, z, [1, 0, 1], [15, 16, 15], 'minecraft:block/terracotta', False)
    else:
        STANDINS.append((x, y, z, 1, 1, 1, (200, 80, 200), p))


ENT = {'frontierhunts:atv': ((2.2, 1.3, 1.4), (70, 90, 60)), 'minecraft:mule': ((1.4, 1.6, 0.8), (110, 80, 50)),
       'minecraft:horse': ((1.4, 1.6, 0.8), (130, 90, 55)), 'minecraft:armor_stand': ((0.5, 1.9, 0.5), (200, 110, 40)),
       'minecraft:item_frame': ((0.8, 0.8, 0.1), (150, 120, 80)), 'minecraft:boat': ((2.6, 0.7, 1.3), (120, 90, 60)),
       'minecraft:chest_boat': ((2.6, 0.7, 1.3), (120, 90, 60))}


def render(dump, out, view, S):
    blocks = dump['blocks']
    if args.noleaves:
        blocks = [b for b in blocks if not any(k in b[3] for k in ('leaves', 'needles', 'boughs'))]
    occ = {}
    for x, y, z, s in blocks:
        bid, props = parse_state(s)
        pp = bid.split(':')[1]
        if pp in ('water', 'lava'):
            occ[(x, y, z)] = 'fluid'
        elif is_full(bid, props):
            occ[(x, y, z)] = 'full'
        elif pp not in ('air', 'cave_air'):
            occ[(x, y, z)] = 'part'

    def solid_at(x, y, z):
        return occ.get((x, y, z))
    for x, y, z, s in blocks:
        # skip fully enclosed blocks
        if occ.get((x, y, z)) == 'full' and all(occ.get(q) == 'full' for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))):
            continue
        add_block(x, y, z, s, solid_at)
    for e in dump.get('entities', []):
        x, y, z, eid = e[0], e[1], e[2], e[3]
        (w, h, d), col = ENT.get(eid, ((0.8, 0.8, 0.8), (220, 60, 220)))
        yaw = e[4] if len(e) > 4 else 0
        if int(round(yaw / 90)) % 2:
            w, d = d, w
        STANDINS.append((x - w / 2, y, z - d / 2, w, h, d, col, eid.split(':')[1]))
    # camera
    ang = math.radians(45 + 90 * view)
    cx, cz = math.sin(ang), math.cos(ang)  # camera horizontal direction (from scene to camera)
    elev = math.radians(30)
    cam = np.array([cx * math.cos(elev), math.sin(elev), cz * math.cos(elev)])
    right = np.array([cz, 0, -cx])
    up = np.cross(right, cam); up /= np.linalg.norm(up)
    if up[1] < 0: up = -up
    # bounds
    allp = [p for poly in POLYS for p in poly[0]]
    for (x, y, z, w, h, d, col, lab) in STANDINS:
        allp.append((x, y, z)); allp.append((x + w, y + h, z + d))
    allp = np.array(allp)
    sx = allp @ right * S; sy = -(allp @ up) * S
    x0, x1, y0, y1 = sx.min() - 20, sx.max() + 20, sy.min() - 40, sy.max() + 20
    W, H = int(x1 - x0), int(y1 - y0)
    img = np.zeros((H, W, 3), np.float32); img[...] = (0.62, 0.72, 0.82)
    zb = np.full((H, W), -1e9, np.float32)
    light = np.array([0.35, 1.0, 0.6]); light /= np.linalg.norm(light)
    order = sorted(range(len(POLYS)), key=lambda i: float(POLYS[i][0].mean(0) @ cam))
    for i in order:
        pts, uvs, tex, tint, trans, dbl = POLYS[i]
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
        if np.linalg.norm(n) < 1e-9 and len(pts) > 3:
            n = np.cross(pts[2] - pts[0], pts[3] - pts[0])
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n = n / nl
        facing = float(n @ cam)
        if facing <= 1e-6:
            if not dbl:
                continue
            n = -n; facing = -facing
        sh = 0.55 + 0.45 * max(0.0, float(n @ light)) + 0.08 * facing
        P = np.stack([pts @ right * S - x0, -(pts @ up) * S - y0], 1)
        D = pts @ cam
        T = texture(tex)
        th, tw = T.shape[:2]
        for k in range(1, len(pts) - 1):
            tri = (0, k, k + 1)
            A, B, C = P[tri[0]], P[tri[1]], P[tri[2]]
            bx0 = max(int(math.floor(min(A[0], B[0], C[0]))), 0); bx1 = min(int(math.ceil(max(A[0], B[0], C[0]))) + 1, W)
            by0 = max(int(math.floor(min(A[1], B[1], C[1]))), 0); by1 = min(int(math.ceil(max(A[1], B[1], C[1]))) + 1, H)
            if bx1 <= bx0 or by1 <= by0:
                continue
            den = (B[1] - C[1]) * (A[0] - C[0]) + (C[0] - B[0]) * (A[1] - C[1])
            if abs(den) < 1e-9:
                continue
            ys, xs = np.mgrid[by0:by1, bx0:bx1]
            px = xs + 0.5; py = ys + 0.5
            w0 = ((B[1] - C[1]) * (px - C[0]) + (C[0] - B[0]) * (py - C[1])) / den
            w1 = ((C[1] - A[1]) * (px - C[0]) + (A[0] - C[0]) * (py - C[1])) / den
            w2 = 1 - w0 - w1
            e = -1e-4
            mask = (w0 >= e) & (w1 >= e) & (w2 >= e)
            if not mask.any():
                continue
            depth = w0 * D[tri[0]] + w1 * D[tri[1]] + w2 * D[tri[2]] + 1e-4
            ok = mask & (depth > zb[by0:by1, bx0:bx1])
            if not ok.any():
                continue
            uu = w0 * uvs[tri[0]][0] + w1 * uvs[tri[1]][0] + w2 * uvs[tri[2]][0]
            vv = w0 * uvs[tri[0]][1] + w1 * uvs[tri[1]][1] + w2 * uvs[tri[2]][1]
            tx = np.clip((uu * tw).astype(int), 0, tw - 1); ty = np.clip((vv * th).astype(int), 0, th - 1)
            col = T[ty, tx]
            a = col[..., 3]
            rgb = col[..., :3].copy()
            if tint is not None:
                rgb *= tint
            rgb *= sh
            sub = img[by0:by1, bx0:bx1]
            if trans:
                ok2 = ok & (a > 0.05)
                sub[ok2] = sub[ok2] * 0.45 + rgb[ok2] * 0.55
                continue
            ok2 = ok & (a > 0.5)
            sub[ok2] = rgb[ok2]
            zsub = zb[by0:by1, bx0:bx1]
            zsub[ok2] = depth[ok2]
    out_img = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    dr = ImageDraw.Draw(out_img)
    # stand-ins on top (outlined boxes)
    for (x, y, z, w, h, d, col, lab) in STANDINS:
        pts = [np.array(p) for p in [(x, y, z), (x + w, y, z), (x, y, z + d), (x + w, y, z + d), (x, y + h, z), (x + w, y + h, z), (x, y + h, z + d), (x + w, y + h, z + d)]]
        sp = [(float(p @ right * S - x0), float(-(p @ up) * S - y0)) for p in pts]
        hull = [sp[i] for i in (4, 5, 7, 6)]
        dr.polygon([sp[0], sp[1], sp[3], sp[2]], outline=col)
        dr.polygon(hull, fill=col, outline=(20, 20, 20))
        for a_, b_ in ((0, 4), (1, 5), (2, 6), (3, 7)):
            dr.line([sp[a_], sp[b_]], fill=(20, 20, 20))
        dr.text((sp[6][0], sp[6][1] - 10), lab, fill=(255, 255, 255))
    if dump.get('title'):
        dr.rectangle([0, 0, W, 22], fill=(20, 24, 28))
        dr.text((8, 5), dump['title'], fill=(240, 230, 200))
    out_img.save(out)
    print(out, out_img.size, 'polys', len(POLYS), 'standins', len(STANDINS), sorted(set(s[7] for s in STANDINS))[:30])


if args.sheet:
    d = json.load(open(args.dump))
    render(d, args.out + '.a.png', 0, args.scale)
    POLYS.clear(); STANDINS.clear()
    render(d, args.out + '.b.png', 2, args.scale)
    a = Image.open(args.out + '.a.png'); b = Image.open(args.out + '.b.png')
    sheet = Image.new('RGB', (a.width + b.width, max(a.height, b.height)), (40, 44, 48))
    sheet.paste(a, (0, 0)); sheet.paste(b, (a.width, 0))
    sheet.save(args.out)
    os.remove(args.out + '.a.png'); os.remove(args.out + '.b.png')
else:
    render(json.load(open(args.dump)), args.out, args.view, args.scale)
