#!/usr/bin/env python3
"""[benchart] Software preview of a two-block bench (left + right halves rendered together as placed for
facing=north) on a plain ground plane, from several angles, with Minecraft's directional face shading; plus the
inventory icon through the item model's gui display transform.

python3 tools/benches/preview_bench.py <repo> <bench id> out.png [--size 520] [--views front,fl,fr,back,top]
python3 tools/benches/preview_bench.py <repo> <bench id> out.png --gui      (gui icon at 32 / 64 / 128 px)
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image

# vertex order per face (Minecraft FaceInfo), each vertex as (xsel, ysel, zsel) 0=min 1=max
FACEINFO = {
    'down': [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    'up': [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    'north': [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    'south': [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    'west': [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    'east': [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
SHADE = {'down': 0.5, 'up': 1.0, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}
DIRS = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}


def rotm(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Tex:
    cache = {}

    @classmethod
    def get(cls, A, rid):
        if rid not in cls.cache:
            p = rid.split(':', 1)[1]
            cls.cache[rid] = np.asarray(Image.open(os.path.join(A, 'textures', p + '.png')).convert('RGBA')).astype(np.float32) / 255
        return cls.cache[rid]


def quads(A, model, dx=0.0):
    out = []
    texs = model['textures']
    for e in model['elements']:
        f = np.array(e['from'], float) + [dx, 0, 0]
        t = np.array(e['to'], float) + [dx, 0, 0]
        r = e.get('rotation')
        for fname, fd in e['faces'].items():
            pts = []
            for sel in FACEINFO[fname]:
                p = np.array([t[i] if sel[i] else f[i] for i in range(3)])
                if r:
                    o = np.array(r['origin'], float) + [dx, 0, 0]
                    p = rotm(r['axis'], r['angle']) @ (p - o) + o
                pts.append(p)
            uv = fd['uv']
            rot = fd.get('rotation', 0) // 90
            corners = []
            for i in range(4):
                j = (i + rot) % 4
                u = uv[0] if j in (0, 1) else uv[2]
                v = uv[1] if j in (0, 3) else uv[3]
                corners.append((u, v))
            n = np.array(DIRS[fname], float)
            if r:
                n = rotm(r['axis'], r['angle']) @ n
            # nearest axis direction for the shade (vanilla)
            best = max(DIRS, key=lambda k: float(np.dot(n, DIRS[k])))
            T = Tex.get(A, texs[fd['texture'].lstrip('#')])
            out.append((np.array(pts), corners, T, SHADE[best], n))
    return out


def raster(img, zb, P, uvs, T, shade, flat=None):
    th, tw = T.shape[:2] if T is not None else (1, 1)
    for tri in ((0, 1, 2), (0, 2, 3)):
        a, b, c = (P[i] for i in tri)
        ua, ub, uc = (uvs[i] for i in tri)
        W, H = img.shape[1], img.shape[0]
        minx, maxx = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(W - 1, math.ceil(max(a[0], b[0], c[0]))))
        miny, maxy = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(H - 1, math.ceil(max(a[1], b[1], c[1]))))
        if minx > maxx or miny > maxy:
            continue
        d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
        if abs(d) < 1e-9:
            continue
        xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
        w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-5) & (w1 >= -1e-5) & (w2 >= -1e-5)
        if not inside.any():
            continue
        # perspective-correct interpolation using 1/w stored in P[:,3]
        iw = w0 * a[3] + w1 * b[3] + w2 * c[3]
        z = w0 * a[2] + w1 * b[2] + w2 * c[2]
        u = (w0 * ua[0] * a[3] + w1 * ub[0] * b[3] + w2 * uc[0] * c[3]) / iw
        v = (w0 * ua[1] * a[3] + w1 * ub[1] * b[3] + w2 * uc[1] * c[3]) / iw
        iy, ix = np.nonzero(inside)
        gy, gx = iy + miny, ix + minx
        zz = z[iy, ix]
        ok = zz < zb[gy, gx]
        gy, gx, zz = gy[ok], gx[ok], zz[ok]
        uu, vv = u[iy, ix][ok], v[iy, ix][ok]
        if flat is not None:
            col = flat(uu, vv)
        else:
            tx = np.clip((uu / 16.0 * tw).astype(int), 0, tw - 1)
            ty = np.clip((vv / 16.0 * th).astype(int), 0, th - 1)
            col = T[ty, tx, :3]
            keep = T[ty, tx, 3] > 0.1
            gy, gx, zz, col = gy[keep], gx[keep], zz[keep], col[keep]
        img[gy, gx] = col * shade
        zb[gy, gx] = zz


def camera(yaw, pitch, dist, target, fov, W, H):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    # yaw 0 = camera north of the bench looking south (at its front); positive yaw orbits toward +x (east)
    fwd = np.array([-sy * cp, -sp, cy * cp])
    eye = np.array(target) - fwd * dist
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = (H / 2) / math.tan(math.radians(fov / 2))

    def proj(p):
        d = p - eye
        x, y, z = d @ right, d @ up, d @ fwd
        z = max(z, 0.1)
        return np.array([W / 2 + f * x / z, H / 2 - f * y / z, z, 1.0 / z])
    return proj


def render_world(A, bid, yaw, pitch, W=640, H=480, dist=70, fov=34, ss=2):
    L = json.load(open(os.path.join(A, 'models', 'block', bid + '_left.json')))
    R = json.load(open(os.path.join(A, 'models', 'block', bid + '_right.json')))
    qs = quads(A, L, 0) + quads(A, R, 16)
    Ws, Hs = W * ss, H * ss
    img = np.zeros((Hs, Ws, 3), np.float32)
    # sky gradient
    gy = np.linspace(0, 1, Hs)[:, None, None]
    img[:] = np.array([0.66, 0.78, 0.90]) * (1 - gy) + np.array([0.84, 0.88, 0.92]) * gy
    zb = np.full((Hs, Ws), 1e9, np.float32)
    proj = camera(yaw, pitch, dist, (16, 10, 8), fov, Ws, Hs)
    # ground: block-grid plane of packed-dirt / grass tone
    g0, g1 = -48, 80

    def ground(uu, vv):
        bx = np.floor(uu / 16)
        bz = np.floor(vv / 16)
        chk = ((bx + bz) % 2) * 0.03
        n = (np.sin(uu * 2.1) * np.sin(vv * 1.7) * 0.02)
        base = np.array([0.47, 0.55, 0.33])
        edge = ((uu % 16) < 0.3) | ((vv % 16) < 0.3)
        c = base[None, :] * (1 - chk - n)[:, None]
        c[edge] *= 0.9
        return c
    for gx0 in range(g0, g1, 16):
        for gz0 in range(g0, g1, 16):
            Pg = [np.array(p, float) for p in ((gx0, 0, gz0), (gx0 + 16, 0, gz0), (gx0 + 16, 0, gz0 + 16), (gx0, 0, gz0 + 16))]
            Q = [proj(p) for p in Pg]
            if min(q[2] for q in Q) <= 0.11:
                continue
            raster(img, zb, Q, [(p[0], p[2]) for p in Pg], None, 1.0, flat=ground)
    for pts, uvs, T, shade, n in qs:
        P = [proj(p) for p in pts]
        raster(img, zb, P, uvs, T, shade)
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    return im.resize((W, H), Image.LANCZOS)


def render_gui(A, bid, px):
    item = json.load(open(os.path.join(A, 'models', 'item', bid + '.json')))
    tr = item['display']['gui']
    M = json.load(open(os.path.join(A, 'models', 'block', bid + '_inventory.json')))
    qs = quads(A, M, 0)
    ss = 4
    S = px * ss
    img = np.zeros((S, S, 3), np.float32) + np.array([0.545, 0.545, 0.545])
    alpha = np.zeros((S, S), bool)
    zb = np.full((S, S), 1e9, np.float32)
    rx, ry, rz = tr.get('rotation', [0, 0, 0])
    Rm = rotm('x', rx) @ rotm('y', ry) @ rotm('z', rz)
    tl = np.array(tr.get('translation', [0, 0, 0])) / 16.0
    sc = np.array(tr.get('scale', [1, 1, 1]))
    # GUI light: two fixed directional lights (approximation of Lighting.setupFor3DItems)
    L0 = np.array([0.2, 1.0, -0.7])
    L0 /= np.linalg.norm(L0)
    L1 = np.array([-0.2, 1.0, 0.7])
    L1 /= np.linalg.norm(L1)

    def proj(p):
        q = (p / 16.0 - 0.5)
        q = Rm @ (q * sc) + tl
        # slot is 16 gui px, model unit block -> 16 gui px
        return np.array([S / 2 + q[0] * S, S / 2 - q[1] * S, -q[2], 1.0])
    for pts, uvs, T, shade, n in qs:
        nn = Rm @ n
        lit = min(1.0, 0.4 + 0.6 * (max(0, nn @ L0) + max(0, nn @ L1)))
        P = [proj(p) for p in pts]
        before = zb.copy()
        raster(img, zb, P, uvs, T, lit)
        alpha |= zb < before
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    return im.resize((px, px), Image.BOX)


VIEWS = {'front': (8, 22), 'fl': (-38, 30), 'fr': (40, 26), 'back': (200, 28), 'top': (15, 60), 'low': (-20, 10),
         'side': (90, 20)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('repo')
    ap.add_argument('bid')
    ap.add_argument('out')
    ap.add_argument('--views', default='front,fl,fr,back')
    ap.add_argument('--w', type=int, default=640)
    ap.add_argument('--h', type=int, default=480)
    ap.add_argument('--dist', type=float, default=70)
    ap.add_argument('--gui', action='store_true')
    ap.add_argument('--fov', type=float, default=34)
    ap.add_argument('--cols', type=int, default=2)
    a = ap.parse_args()
    A = os.path.join(os.path.abspath(a.repo), 'patch', 'assets', 'frontierhunts')
    if a.gui:
        ims = [render_gui(A, a.bid, p) for p in (32, 64, 128)]
        out = Image.new('RGB', (32 + 64 + 128 + 40, 140), (139, 139, 139))
        x = 10
        for im in ims:
            out.paste(im, (x, 6))
            x += im.size[0] + 10
        out.save(a.out)
        print('wrote', a.out)
        return
    vs = a.views.split(',')
    ims = []
    for v in vs:
        yaw, pitch = VIEWS[v] if v in VIEWS else tuple(float(x) for x in v.split(':'))
        ims.append(render_world(A, a.bid, yaw, pitch, a.w, a.h, a.dist, a.fov))
    cols = min(a.cols, len(ims))
    rows = (len(ims) + cols - 1) // cols
    out = Image.new('RGB', (cols * a.w, rows * a.h), (30, 30, 30))
    for i, im in enumerate(ims):
        out.paste(im, ((i % cols) * a.w, (i // cols) * a.h))
    out.save(a.out)
    print('wrote', a.out)


if __name__ == '__main__':
    main()
