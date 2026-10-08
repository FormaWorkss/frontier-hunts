#!/usr/bin/env python3
"""[hunts] Software preview of a block model JSON (orthographic, textured, lit, z-buffered) floating on a water plane
(water surface at y = -1.8 px, like a source block's surface below the decoy's block), from several angles.

python3 tools/hunts/preview_model.py <repo> <model path under models/block, no .json> out.png [yaw,yaw,...] [pitch]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

R = os.path.abspath(sys.argv[1])
MODEL = sys.argv[2]
OUTP = sys.argv[3]
YAWS = [float(v) for v in (sys.argv[4] if len(sys.argv) > 4 else '35,125,215,300').split(',')]
PITCH = float(sys.argv[5]) if len(sys.argv) > 5 else 28.0
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')
WATER_Y = -1.8

# corner order per face = (u0,v0), (u1,v0), (u1,v1), (u0,v1) in Minecraft's own UV convention (FaceBakery):
# north u -> west, south u -> east, east u -> north, west u -> south, up u -> east / v -> south, down v -> north
FACES = {
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}


def tex(rid):
    path = rid.split(':', 1)[1]
    return np.asarray(Image.open(os.path.join(A, 'textures', path + '.png')).convert('RGBA')).astype(np.float32) / 255.0


def rot(axis, deg, v):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v
    if axis == 'x':
        return np.array([x, y * c - z * s, y * s + z * c])
    if axis == 'y':
        return np.array([x * c + z * s, y, -x * s + z * c])
    return np.array([x * c - y * s, x * s + y * c, z])


def render(model, yaw, size=360, span=22.0):
    els = model['elements']
    texs = model['textures']
    img = np.zeros((size, size, 3), np.float32) + np.array([0.62, 0.72, 0.80])
    zb = np.full((size, size), -1e9, np.float32)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
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
        return np.array([size / 2 + x1 * s, size * 0.6 - y2 * s, z2])

    quads = []
    g = [np.array(v, float) for v in ((-6, WATER_Y, -6), (22, WATER_Y, -6), (22, WATER_Y, 22), (-6, WATER_Y, 22))]
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
            quads.append((pts, fd['uv'], texs.get(fd['texture'].lstrip('#')), n / ln))
    for pts, uv, rid, n in quads:
        P = [proj(p) for p in pts]
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, light)))
        T = None if uv is None else tex(rid)
        uvq = [(0, 0), (1, 0), (1, 1), (0, 1)] if uv is None else [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
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
            ok = zz > zb[gy, gx]
            gy, gx, zz = gy[ok], gx[ok], zz[ok]
            uu, vv = u[iy, ix][ok], v[iy, ix][ok]
            if T is None:
                col = np.stack([np.full(len(uu), 0.16), np.full(len(uu), 0.33), np.full(len(uu), 0.42)], 1)
                col *= (0.9 + 0.1 * np.sin(uu * 70)[:, None] * np.cos(vv * 50)[:, None])
            else:
                th, tw = T.shape[:2]
                tx = np.clip((uu / 16.0 * tw).astype(int), 0, tw - 1)
                ty = np.clip((vv / 16.0 * th).astype(int), 0, th - 1)
                col = T[ty, tx, :3]
                alpha = T[ty, tx, 3]
                keep = alpha > 0.1
                gy, gx, zz, col = gy[keep], gx[keep], zz[keep], col[keep]
            img[gy, gx] = col * shade
            zb[gy, gx] = zz
    return np.clip(img * 255, 0, 255).astype(np.uint8)


model = json.load(open(os.path.join(A, 'models', 'block', MODEL + '.json')))
tiles = [render(model, y) for y in YAWS]
out = Image.new('RGB', (len(tiles) * 360, 360), (40, 40, 40))
for i, t in enumerate(tiles):
    out.paste(Image.fromarray(t), (i * 360, 0))
out.save(OUTP)
print('wrote', OUTP)
