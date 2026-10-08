#!/usr/bin/env python3
"""[ecology] Software preview of the bone block models (orthographic, textured, lit, z-buffered) on a patch of
grass, to check the art without a game client.

python3 tools/ecology/preview_bones.py <repo> out.png [classic|real] [yaw_deg] [pitch_deg]
"""
import json, math, os, sys
import numpy as np
from PIL import Image

R = os.path.abspath(sys.argv[1])
OUTP = sys.argv[2]
LOOK = sys.argv[3] if len(sys.argv) > 3 else 'classic'
YAW = float(sys.argv[4]) if len(sys.argv) > 4 else 35.0
PITCH = float(sys.argv[5]) if len(sys.argv) > 5 else 32.0
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')
REAL = os.path.join(R, 'patch', 'resourcepacks', 'realistic_world', 'assets', 'frontierhunts')
TEX = {}


def tex(rid):
    if rid in TEX:
        return TEX[rid]
    path = rid.split(':', 1)[1]
    p = os.path.join(REAL if LOOK == 'real' else A, 'textures', path + '.png')
    if not os.path.exists(p):
        p = os.path.join(A, 'textures', path + '.png')
    im = np.asarray(Image.open(p).convert('RGBA')).astype(np.float32) / 255.0
    TEX[rid] = im
    return im


def rot(axis, deg, v):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v
    if axis == 'x':
        return np.array([x, y * c - z * s, y * s + z * c])
    if axis == 'y':
        return np.array([x * c + z * s, y, -x * s + z * c])
    return np.array([x * c - y * s, x * s + y * c, z])


def load(name):
    m = json.load(open(os.path.join(A, 'models', 'block', 'bones', name + '.json')))
    t = json.load(open(os.path.join(A, 'models', m['parent'].split(':')[1] + '.json')))
    return t['elements'], m['textures']


FACES = {
    'north': [(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)],
    'south': [(1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)],
    'east': [(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)],
    'west': [(0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)],
    'up': [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)],
    'down': [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
}


def render(name, size=360, span=float(os.environ.get('SPAN', '40')), ground=True):
    els, texs = load(name)
    img = np.zeros((size, size, 3), np.float32) + np.array([0.53, 0.66, 0.82])
    zb = np.full((size, size), -1e9, np.float32)
    cy, sy = math.cos(math.radians(YAW)), math.sin(math.radians(YAW))
    cp, sp = math.cos(math.radians(PITCH)), math.sin(math.radians(PITCH))
    light = np.array([0.45, 0.8, -0.35])
    light /= np.linalg.norm(light)

    def proj(p):
        x, y, z = p[0] - 8, p[1], p[2] - 8
        x1 = x * cy - z * sy
        z1 = x * sy + z * cy
        y2 = y * cp - z1 * sp
        z2 = y * sp + z1 * cp
        s = size / span
        return np.array([size / 2 + x1 * s, size * 0.62 - y2 * s, z2])

    quads = []
    if ground:
        g = [np.array(v, float) for v in ((-8, 0, -8), (24, 0, -8), (24, 0, 24), (-8, 0, 24))]
        quads.append((g, None, None, np.array([0, 1, 0])))
    for e in els:
        f, t = np.array(e['from'], float), np.array(e['to'], float)
        r = e.get('rotation')
        for fname, corners in FACES.items():
            fd = e['faces'].get(fname)
            if fd is None:
                continue
            pts = []
            for c in corners:
                p = np.array([f[i] if c[i] == 0 else t[i] for i in range(3)])
                if r:
                    o = np.array(r['origin'], float)
                    p = rot(r['axis'], r['angle'], p - o) + o
                pts.append(p)
            n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
            ln = np.linalg.norm(n)
            if ln < 1e-9:
                continue
            n = n / ln
            tid = fd['texture'].lstrip('#')
            rid = texs.get(tid)
            quads.append((pts, fd['uv'], rid, n))
    for pts, uv, rid, n in quads:
        P = [proj(p) for p in pts]
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, light)))
        if uv is None:
            col_fn = lambda u, v: np.array([0.33, 0.52, 0.2]) * (0.9 + 0.1 * math.sin(u * 40) * math.cos(v * 30))
            uvq = [(0, 0), (1, 0), (1, 1), (0, 1)]
            T = None
        else:
            T = tex(rid)
            u0, v0, u1, v1 = uv
            uvq = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (P[i] for i in tri)
            ua, ub, uc = (uvq[i] for i in tri)
            minx, maxx = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(size - 1, math.ceil(max(a[0], b[0], c[0]))))
            miny, maxy = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(size - 1, math.ceil(max(a[1], b[1], c[1]))))
            if minx > maxx or miny > maxy:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(d) < 1e-9:
                continue
            w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
            w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not inside.any():
                continue
            z = w0 * a[2] + w1 * b[2] + w2 * c[2]
            u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
            v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
            iy, ix = np.nonzero(inside)
            gy, gx = iy + miny, ix + minx
            zz = z[iy, ix]
            ok = zz > zb[gy, gx] + (0.0 if T is not None else -0.0)
            gy, gx, zz = gy[ok], gx[ok], zz[ok]
            uu, vv = u[iy, ix][ok], v[iy, ix][ok]
            if T is None:
                col = np.stack([np.full(len(uu), 0.33), np.full(len(uu), 0.52), np.full(len(uu), 0.2)], 1)
                col *= (0.85 + 0.15 * np.sin(uu * 90)[:, None] * np.cos(vv * 70)[:, None])
            else:
                th, tw = T.shape[:2]
                tx = np.clip((uu / 16.0 * tw).astype(int), 0, tw - 1)
                ty = np.clip((vv / 16.0 * th).astype(int), 0, th - 1)
                col = T[ty, tx, :3]
            # ground is drawn as a plane at y=0: bones below it are hidden
            img[gy, gx] = col * shade
            zb[gy, gx] = zz
    return np.clip(img * 255, 0, 255).astype(np.uint8)


names = ['whitetail_skull_plain', 'whitetail_skull_bare_mossy', 'elk_skull_plain', 'elk_skull_mossy', 'moose_skull_plain', 'moose_skull_fresh',
         'bison_skull_plain', 'bison_skull_mossy', 'scattered_bones_ribs_plain', 'scattered_bones_ribs_fresh', 'scattered_bones_spine_mossy',
         'scattered_bones_legs_plain', 'shed_antler_plain', 'shed_antler_mossy', 'elk_skull_bare_plain', 'whitetail_skull_fresh']
if len(sys.argv) > 6:
    names = sys.argv[6].split(',')
cols = 4
tiles = [render(n) for n in names]
h = (len(tiles) + cols - 1) // cols
out = Image.new('RGB', (cols * 360, h * 360), (40, 40, 40))
for i, t in enumerate(tiles):
    out.paste(Image.fromarray(t), ((i % cols) * 360, (i // cols) * 360))
out.save(OUTP)
print('wrote', OUTP)
