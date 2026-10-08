#!/usr/bin/env python3
"""[smalls] The Blood-Tracking Lamp held in the hand: a real mesh (neoforge:obj) instead of crossed boxes.

A modern hand-held blood-tracking light, about 16 cm long (1 model px ~ 1.25 cm):
    tail      domed rubber push-switch in an anodized tailcap with a lanyard eyelet underneath
    grip      diamond-knurled hard-anodized aluminium
    sleeve    olive rubber armour with three moulded grip ribs, a rubber side switch on top and a moulded blood-drop logo
    head      the armour flares into a finned aluminium head
    bezel     bead-blasted steel strike bezel with six crenellations
    lens      teal tracking filter over an orange-peel reflector and the LED die (emissive: the lamp is on while held)
    lanyard   a blaze-orange paracord wrist loop through the eyelet, with a cord lock

Writes (deterministic):
    patch/assets/frontierhunts/models/item/tracking_lamp_3d.{obj,json}, tracking_lamp.mtl
    patch/assets/frontierhunts/textures/item/tracking_lamp/{grip,anodized,armour,bezel,lens,rubber,cord}.png
    patch/assets/frontierhunts/textures/item/tracking_lamp.png   (32x32 inventory icon, rendered from the mesh)

    python3 tools/smalls/lamp_art.py [repo]
"""
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts')
TEX = os.path.join(A, 'textures', 'item', 'tracking_lamp')
MOD = os.path.join(A, 'models', 'item')
FH = 'frontierhunts'
SEG = 24  # facets around the body

# ============================================================================================ textures


def clamp(a):
    return np.clip(a, 0, 255).astype(np.uint8)


def noise(h, w, seed, scale=1.0):
    r = np.random.default_rng(seed)
    return r.normal(0.0, 1.0, (h, w)) * scale


def blur(a, rad):
    im = Image.fromarray(clamp((a - a.min()) / max(1e-6, a.max() - a.min()) * 255))
    b = np.asarray(im.filter(ImageFilter.GaussianBlur(rad))).astype(np.float32) / 255.0
    return b * (a.max() - a.min()) + a.min()


def save(arr, name):
    os.makedirs(TEX, exist_ok=True)
    Image.fromarray(clamp(arr), 'RGBA' if arr.shape[2] == 4 else 'RGB').convert('RGBA').save(os.path.join(TEX, name + '.png'), optimize=True)


def rgb(h, w, c):
    a = np.zeros((h, w, 4), np.float32)
    a[..., 0], a[..., 1], a[..., 2], a[..., 3] = c[0], c[1], c[2], 255
    return a


def tex_grip():
    """diamond knurling cut into black hard-anodized aluminium: pyramids lit from above, bright flat tops, dark grooves"""
    h, w = 48, 64
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    p = 4.0  # knurl pitch in texels (u wraps: 64 / 4 = 16 diamonds round the grip)
    a = (x + y) / p
    b = (x - y) / p
    fa = np.abs(a - np.round(a)) * 2.0  # 0 at a groove, 1 mid-face
    fb = np.abs(b - np.round(b)) * 2.0
    height = np.minimum(fa, fb)
    # slope toward the light (light from the top of the texture = toward the tail)
    gy = np.gradient(height, axis=0)
    gx = np.gradient(height, axis=1)
    lit = 0.62 + 1.4 * (-gy * 0.8 + gx * 0.3)
    base = np.array([38, 39, 41], np.float32)
    col = base[None, None, :] * lit[..., None]
    col = np.where((height < 0.18)[..., None], np.array([16, 16, 17], np.float32)[None, None, :], col)
    tops = height > 0.82
    col = np.where(tops[..., None], np.array([74, 75, 77], np.float32)[None, None, :], col)
    col += noise(h, w, 11, 2.5)[..., None]
    out = np.concatenate([col, np.full((h, w, 1), 255, np.float32)], axis=2)
    # plain bands where the knurl runs out at both ends
    for yy in list(range(0, 2)) + list(range(h - 2, h)):
        out[yy, :, :3] = np.array([34, 35, 37]) + noise(1, w, 12 + yy, 2.0)[0][:, None]
    save(out, 'grip')


def tex_anodized():
    """matte black hard-anodize: fine grain and faint lathe rings (rows), a white laser-etched index line"""
    h, w = 32, 64
    a = rgb(h, w, (33, 34, 36))
    a[..., :3] += noise(h, w, 21, 2.2)[..., None]
    rings = np.sin(np.arange(h) * 2.1) * 1.6
    a[..., :3] += rings[:, None, None]
    save(a, 'anodized')


def tex_armour():
    """olive rubber armour: pebbled grain, a moulded blood drop and three dots on each side (u = 0.25 and 0.75)"""
    h, w = 32, 128
    base = np.array([72, 77, 54], np.float32)
    g = blur(noise(h, w, 31), 0.6)
    a = rgb(h, w, base)
    a[..., :3] *= (1.0 + 0.06 * g)[..., None]
    a[..., :3] += noise(h, w, 32, 1.6)[..., None]
    im = Image.fromarray(clamp(a), 'RGBA')
    d = ImageDraw.Draw(im)
    for cx in (32, 96):
        # moulded drop: lit upper rim, shadowed lower rim, body a shade darker than the rubber
        cy = 15
        drop = [(cx, cy - 6), (cx + 3, cy - 1), (cx + 3.6, cy + 2), (cx + 2.4, cy + 4.6), (cx, cy + 5.4), (cx - 2.4, cy + 4.6), (cx - 3.6, cy + 2),
                (cx - 3, cy - 1)]
        d.polygon([(px_, py_ + 0.6) for px_, py_ in drop], fill=(48, 52, 36, 255))
        d.polygon([(px_, py_ - 0.4) for px_, py_ in drop], fill=(96, 101, 74, 255))
        d.polygon(drop, fill=(64, 68, 48, 255))
        for k, dy in enumerate((-3, 0, 3)):
            d.ellipse([cx + 7, cy + dy - 1, cx + 9, cy + dy + 1], fill=(56, 60, 42, 255))
            d.point([(cx + 7, cy + dy - 1)], fill=(92, 97, 70, 255))
    a = np.asarray(im).astype(np.float32)
    save(a, 'armour')


def tex_bezel():
    """bead-blasted stainless strike bezel: light grey, sparkle grain, a darker machined lip on the front edge"""
    h, w = 16, 64
    a = rgb(h, w, (142, 145, 148))
    a[..., :3] += noise(h, w, 41, 6.0)[..., None]
    a[..., :3] *= (1.0 - 0.18 * (np.arange(h) / h))[:, None, None]
    a[h - 3:, :, :3] = np.array([92, 95, 99]) + noise(3, w, 42, 3.0)[..., None]
    save(a, 'bezel')


def tex_lens():
    """the front: black o-ring, teal tracking filter over an orange-peel reflector, the LED die, a glint on the glass"""
    n = 64
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    cx = cy = (n - 1) / 2.0
    r = np.hypot(x - cx, y - cy) / (n / 2.0)
    ang = np.arctan2(y - cy, x - cx)
    rng = np.random.default_rng(51)
    peel = blur(rng.normal(0, 1, (n, n)), 0.8)
    # reflector: bright near the emitter, falling off to the rim, orange-peel dimples
    refl = 0.50 + 0.50 * np.clip(1.0 - r, 0.0, 1.0) ** 0.8 + 0.035 * peel + 0.10 * np.exp(-((r - 0.45) / 0.08) ** 2)
    teal = np.array([108, 222, 214], np.float32)
    col = teal[None, None, :] * refl[..., None]
    # concentric facet rings of the reflector
    col *= (1.0 + 0.035 * np.sin(r * 30.0))[..., None]
    # LED: square die with a warm-white phosphor centre, a dark ring of the emitter dome round it
    die = (np.abs(x - cx) < 4.2) & (np.abs(y - cy) < 4.2)
    dome = r < 0.24
    col = np.where(dome[..., None], np.array([60, 120, 122], np.float32)[None, None, :] + 40 * (1 - r[..., None] / 0.24), col)
    col = np.where(die[..., None], np.array([250, 255, 236], np.float32)[None, None, :], col)
    core = (np.abs(x - cx) < 2.2) & (np.abs(y - cy) < 2.2)
    col = np.where(core[..., None], np.array([255, 255, 255], np.float32)[None, None, :], col)
    # glass glint: a soft white arc top-left
    glint = np.exp(-((r - 0.72) / 0.07) ** 2) * np.clip(np.cos(ang + 2.3), 0, 1) ** 6
    col += 140.0 * glint[..., None]
    # o-ring and bezel shadow at the rim
    rim = r > 0.9
    col = np.where(rim[..., None], np.array([14, 15, 16], np.float32)[None, None, :], col)
    shadow = (r > 0.8) & ~rim
    col *= np.where(shadow, 0.78, 1.0)[..., None]
    a = np.concatenate([col, np.full((n, n, 1), 255, np.float32)], axis=2)
    save(a, 'lens')


def tex_rubber():
    """black rubber for the tail switch and the side switch boot: soft grain and a fine stipple"""
    n = 32
    a = rgb(n, n, (30, 30, 31))
    a[..., :3] += blur(noise(n, n, 61), 0.5)[..., None] * 3.0
    a[..., :3] += noise(n, n, 62, 1.2)[..., None]
    save(a, 'rubber')


def tex_cord():
    """blaze-orange 550 paracord: a diamond weave along the cord (v) round its circumference (u)"""
    h, w = 16, 32
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    wv = np.sin((x * 0.785 + y * 1.57)) * np.sin((x * 0.785 - y * 1.57))
    base = np.array([222, 96, 28], np.float32)
    col = base[None, None, :] * (0.86 + 0.16 * wv)[..., None]
    col += noise(h, w, 71, 4.0)[..., None]
    # one black tracer yarn, as real 550 cord has
    col = np.where(((x + 2 * y) % 32 < 1.2)[..., None], np.array([40, 30, 26], np.float32)[None, None, :], col)
    a = np.concatenate([col, np.full((h, w, 1), 255, np.float32)], axis=2)
    save(a, 'cord')


# ============================================================================================ mesh


class Mesh:
    def __init__(self):
        self.v, self.vt, self.vn = [], [], []
        self.faces = {}  # material -> [(vi, ti, ni) x 3/4]

    def vert(self, p, uv, n):
        self.v.append(tuple(p))
        self.vt.append(tuple(uv))
        nn = np.asarray(n, float)
        ln = np.linalg.norm(nn)
        self.vn.append(tuple(nn / ln if ln > 1e-9 else (0.0, 1.0, 0.0)))
        return len(self.v)

    def face(self, mat, ids):
        self.faces.setdefault(mat, []).append(ids)


def lathe(m, mat, z0, r0, z1, r1, v0, v1, seg=SEG, ang0=0.0, ang1=2 * math.pi, inward=False):
    """one band of a surface of revolution about +z (local lamp axis), u round, v along; outward normals, CCW from outside"""
    dz, dr = z1 - z0, r1 - r0
    # outward normal in the (radial, z) plane: perpendicular to the profile direction
    nr, nz = dz, -dr
    if inward:
        nr, nz = -nr, -nz
    ids0, ids1 = [], []
    for i in range(seg + 1):
        t = ang0 + (ang1 - ang0) * i / seg
        s, c = math.sin(t), math.cos(t)  # angle from the top (+y) toward +x
        u = i / seg
        n = (nr * s, nr * c, nz)
        ids0.append(m.vert((r0 * s, r0 * c, z0), (u, v0), n))
        ids1.append(m.vert((r1 * s, r1 * c, z1), (u, v1), n))
    for i in range(seg):
        q = [ids0[i], ids1[i], ids1[i + 1], ids0[i + 1]]
        m.face(mat, q[::-1] if inward else q)


def disc(m, mat, z, r, facing, seg=SEG, planar=None):
    """a flat cap at z (facing +1 = +z, -1 = -z); planar=(u0,v0,size) maps it like a decal, else a centre fan in a strip"""
    n = (0.0, 0.0, float(facing))
    c = m.vert((0, 0, z), (0.5, 0.5) if planar else (0.5, 0.0), n)
    rim = []
    for i in range(seg + 1):
        t = 2 * math.pi * i / seg
        s, co = math.sin(t), math.cos(t)
        uv = (0.5 + 0.5 * s * facing, 0.5 - 0.5 * co) if planar else (i / seg, 1.0)
        rim.append(m.vert((r * s, r * co, z), uv, n))
    for i in range(seg):
        tri = [c, rim[i + 1], rim[i]] if facing > 0 else [c, rim[i], rim[i + 1]]
        m.face(mat, tri)


def annulus(m, mat, z, r_in, r_out, facing, v0=0.0, v1=1.0, seg=SEG):
    # a flat ring: going inward round the profile gives a +z normal, outward a -z one
    lathe(m, mat, z, r_out if facing > 0 else r_in, z, r_in if facing > 0 else r_out, v0, v1, seg)


def profile(m, mat, pts, v0, v1, seg=SEG):
    """a run of rings [(z, r), ...] as consecutive lathe bands, v spread by arc length over v0..v1"""
    lens = [math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]) for i in range(len(pts) - 1)]
    tot = sum(lens) or 1.0
    acc = 0.0
    for i in range(len(pts) - 1):
        a = v0 + (v1 - v0) * acc / tot
        acc += lens[i]
        b = v0 + (v1 - v0) * acc / tot
        (za, ra), (zb, rb) = pts[i], pts[i + 1]
        if abs(zb - za) < 1e-9 and abs(rb - ra) < 1e-9:
            continue
        lathe(m, mat, za, ra, zb, rb, a, b, seg)


def tube(m, mat, path, radius, sides=8, closed=False):
    """a round cord along a polyline (parallel-transport frames), u round, v along (repeats every 2 px)"""
    P = [np.asarray(p, float) for p in path]
    T = []
    for i in range(len(P)):
        a = P[(i - 1) % len(P)] if (closed or i > 0) else P[i]
        b = P[(i + 1) % len(P)] if (closed or i < len(P) - 1) else P[i]
        t = b - a
        T.append(t / np.linalg.norm(t))
    nrm = np.cross(T[0], np.array([1.0, 0.0, 0.0]))
    if np.linalg.norm(nrm) < 1e-3:
        nrm = np.cross(T[0], np.array([0.0, 1.0, 0.0]))
    nrm /= np.linalg.norm(nrm)
    frames = []
    for i in range(len(P)):
        if i > 0:
            nrm = nrm - np.dot(nrm, T[i]) * T[i]
            nrm /= np.linalg.norm(nrm)
        frames.append((nrm.copy(), np.cross(T[i], nrm)))
    acc = [0.0]
    for i in range(1, len(P)):
        acc.append(acc[-1] + np.linalg.norm(P[i] - P[i - 1]))
    rings = []
    for i in range(len(P)):
        n1, n2 = frames[i]
        ring = []
        for k in range(sides + 1):
            t = 2 * math.pi * k / sides
            d = n1 * math.cos(t) + n2 * math.sin(t)
            ring.append(m.vert(P[i] + d * radius, (k / sides, acc[i] / 2.0), d))
        rings.append(ring)
    count = len(P) if closed else len(P) - 1
    for i in range(count):
        a, b = rings[i], rings[(i + 1) % len(P)]
        for k in range(sides):
            m.face(mat, [a[k], a[k + 1], b[k + 1], b[k]])


def build():
    m = Mesh()
    # ---------------------------------------------------------------- tail: domed rubber switch, anodized tailcap
    profile(m, 'rubber', [(-0.40, 0.0001), (-0.38, 0.22), (-0.31, 0.45), (-0.18, 0.62), (-0.02, 0.70), (0.0, 0.70)], 0.0, 1.0)
    annulus(m, 'anodized', 0.0, 0.70, 0.98, -1, 0.0, 0.1)
    profile(m, 'anodized', [(0.0, 0.98), (0.10, 1.20), (1.10, 1.20), (1.18, 1.10)], 0.1, 0.55)
    # ---------------------------------------------------------------- knurled grip
    profile(m, 'grip', [(1.18, 1.10), (5.30, 1.10)], 0.0, 1.0)
    # ---------------------------------------------------------------- relief groove, then the rubber armour sleeve
    profile(m, 'anodized', [(5.30, 1.10), (5.36, 1.00), (5.58, 1.00), (5.64, 1.12)], 0.55, 0.7)
    ribs = [(5.64, 1.12), (5.76, 1.32)]
    z = 5.95
    for _ in range(3):  # three moulded grip ribs
        ribs += [(z, 1.32), (z + 0.06, 1.43), (z + 0.24, 1.43), (z + 0.30, 1.32)]
        z += 0.46
    ribs += [(8.55, 1.32), (9.10, 1.52), (9.75, 1.84), (9.95, 1.92)]
    profile(m, 'armour', ribs, 0.0, 1.0)
    # ---------------------------------------------------------------- finned aluminium head
    head = [(9.95, 1.92), (10.0, 1.98)]
    z = 10.15
    for _ in range(3):  # cooling grooves
        head += [(z, 1.98), (z + 0.04, 1.84), (z + 0.22, 1.84), (z + 0.26, 1.98)]
        z += 0.42
    head += [(11.45, 1.98)]
    profile(m, 'anodized', head, 0.7, 1.0)
    # ---------------------------------------------------------------- strike bezel with six crenellations
    profile(m, 'bezel', [(11.45, 1.98), (11.50, 2.06), (12.30, 2.06)], 0.0, 0.8)
    teeth = 8
    for k in range(teeth):
        a0 = 2 * math.pi * k / teeth + math.radians(4)
        a1 = a0 + 2 * math.pi / teeth * 0.58
        lathe(m, 'bezel', 12.30, 2.06, 12.50, 2.06, 0.8, 1.0, 4, a0, a1)  # outside
        lathe(m, 'bezel', 12.50, 1.74, 12.30, 1.74, 0.8, 1.0, 4, a0, a1, inward=False)  # inside face (normal toward the axis)
        lathe(m, 'bezel', 12.50, 2.06, 12.50, 1.74, 0.85, 1.0, 4, a0, a1)  # top of the tooth
        for ang, side in ((a0, -1), (a1, 1)):  # tooth ends
            s, c = math.sin(ang), math.cos(ang)
            nrm = (side * c, -side * s, 0.0)
            p = [(2.06 * s, 2.06 * c, 12.30), (2.06 * s, 2.06 * c, 12.50), (1.74 * s, 1.74 * c, 12.50), (1.74 * s, 1.74 * c, 12.30)]
            ids = [m.vert(q, (0.5, 0.9), nrm) for q in p]
            m.face('bezel', ids if side > 0 else ids[::-1])
    annulus(m, 'bezel', 12.30, 1.74, 2.06, 1, 0.8, 0.85)
    # inner wall down to the glass, and the lens (emissive)
    lathe(m, 'bezel', 12.30, 1.74, 12.05, 1.74, 0.8, 1.0, SEG, inward=True)
    disc(m, 'lens', 12.05, 1.74, 1, SEG, planar=True)
    # ---------------------------------------------------------------- rubber side switch boot on top of the sleeve
    W, Lb, H = 0.95, 1.10, 0.30
    zc, rb = 7.55, 1.30
    nu, nv = 8, 8
    grid = []
    for j in range(nv + 1):
        row = []
        for i in range(nu + 1):
            su = -1 + 2 * i / nu
            sv = -1 + 2 * j / nv
            hgt = H * max(0.0, 1 - abs(su) ** 4) ** 0.5 * max(0.0, 1 - abs(sv) ** 4) ** 0.5
            t = su * W / 2 / rb
            rr = rb + hgt
            row.append((rr * math.sin(t), rr * math.cos(t), zc + sv * Lb / 2, i / nu, j / nv))
        grid.append(row)
    ids = {}
    for j in range(nv + 1):
        for i in range(nu + 1):
            x, y, zz, u, v = grid[j][i]
            dxu = np.subtract(grid[j][min(nu, i + 1)][:3], grid[j][max(0, i - 1)][:3])
            dxv = np.subtract(grid[min(nv, j + 1)][i][:3], grid[max(0, j - 1)][i][:3])
            n = np.cross(dxv, dxu)
            if np.dot(n, (x, y, 0)) < 0:
                n = -n
            ids[(i, j)] = m.vert((x, y, zz), (u * 0.5 + 0.25, v * 0.5 + 0.25), n)
    for j in range(nv):
        for i in range(nu):
            q = [ids[(i, j)], ids[(i, j + 1)], ids[(i + 1, j + 1)], ids[(i + 1, j)]]
            m.face('rubber', q)
    # ---------------------------------------------------------------- lanyard: eyelet under the tailcap, a paracord wrist loop
    ey, ez = -1.20, 0.55
    eyelet = [(0.0, ey - 0.26 * math.cos(t), ez + 0.26 * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 13)[:-1]]
    tube(m, 'anodized', [(0.0, ey + 0.05, ez - 0.38)] + eyelet[3:10] + [(0.0, ey + 0.05, ez + 0.38)], 0.09, 6)
    loop = []
    steps = 44
    for k in range(steps):
        ph = 2 * math.pi * k / steps
        dn = (1 - math.cos(ph)) / 2  # 0 at the eyelet, 1 at the bottom of the loop
        yy = ey - 0.30 - 3.7 * dn ** 0.9
        zz = ez - 0.85 * math.sin(ph) * (0.35 + 0.65 * dn) + 0.55 * dn ** 2
        xx = 0.18 * math.sin(ph)  # the two strands sit side by side
        loop.append((xx, yy, zz))
    tube(m, 'cord', loop, 0.13, 8, closed=True)
    # cord lock: a small black barrel over both strands, a lathe about z turned upright (a proper rotation keeps the winding)
    tmp = Mesh()
    profile(tmp, 'rubber', [(-0.32, 0.0001), (-0.32, 0.20), (-0.26, 0.30), (0.26, 0.30), (0.32, 0.20), (0.32, 0.0001)], 0.1, 0.9, 12)
    lock = np.array([0.0, ey - 1.15, ez + 0.08])
    rot = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)  # (x, y, z) -> (x, z, -y): the lathe axis becomes +y
    base = len(m.v)
    for p, uv, n in zip(tmp.v, tmp.vt, tmp.vn):
        m.vert(rot @ np.asarray(p) + lock, uv, rot @ np.asarray(n))
    for f in tmp.faces['rubber']:
        m.face('lock', [base + i for i in f])
    return m


def lamp_mesh():
    m = build()
    return m


# ============================================================================================ placement and files

LENGTH = 12.98  # tail button (-0.40) to bezel teeth (12.50)


def to_model(p):
    """lamp local (axis +z tail -> lens, top +y) to model px: lens toward north (-Z), centred on the block, rotated 180
    about y (a rotation, so the winding stays outward)"""
    x, y, z = p
    zc = (12.50 - 0.40) / 2
    return (8.0 - x, 8.0 + y + 0.4, 8.0 - (z - zc))


def write_obj(m):
    os.makedirs(MOD, exist_ok=True)
    lines = ['# [smalls] Frontier Hunts blood-tracking lamp - original mesh, tools/smalls/lamp_art.py', 'mtllib tracking_lamp.mtl', 'o tracking_lamp']
    for p in m.v:
        X, Y, Z = to_model(p)
        lines.append('v %.5f %.5f %.5f' % (X / 16.0, Y / 16.0, Z / 16.0))
    for u, v in m.vt:
        lines.append('vt %.5f %.5f' % (u, v))
    for n in m.vn:
        lines.append('vn %.5f %.5f %.5f' % (-n[0], n[1], -n[2]))
    for mat in ('grip', 'anodized', 'armour', 'bezel', 'rubber', 'lock', 'cord', 'lens'):
        if mat not in m.faces:
            continue
        lines.append('usemtl ' + mat)
        for f in m.faces[mat]:
            lines.append('f ' + ' '.join('%d/%d/%d' % (i, i, i) for i in f))
    with open(os.path.join(MOD, 'tracking_lamp_3d.obj'), 'w', newline='\n') as fh:
        fh.write('\n'.join(lines) + '\n')
    mtl = []
    for mat in ('grip', 'anodized', 'armour', 'bezel', 'rubber', 'lock', 'cord', 'lens'):
        tex = 'rubber' if mat == 'lock' else mat  # the cord lock is the same black rubber (its own group so the icon can leave it out)
        mtl += ['newmtl ' + mat, 'Kd 1 1 1', 'Ka %s' % ('1 1 1' if mat == 'lens' else '0 0 0'), 'map_Kd %s:item/tracking_lamp/%s' % (FH, tex)]
    with open(os.path.join(MOD, 'tracking_lamp.mtl'), 'w', newline='\n') as fh:
        fh.write('\n'.join(mtl) + '\n')


DISPLAY = {
    # first person: low in the right of the view, lens forward and turned in toward the crosshair; the whole light shows,
    # tail and lanyard included, and it stays clear of the crosshair and the hotbar (left hand: mirrored by Minecraft)
    'firstperson_righthand': {'rotation': [10, 20, -5], 'translation': [-1.5, 3.0, -1.5], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_lefthand': {'rotation': [10, 20, -5], 'translation': [-1.5, 3.0, -1.5], 'scale': [0.5, 0.5, 0.5]},
    # third person: the grip in the fist, lens pointing where the player faces, lanyard hanging under the wrist
    'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 2.6, -1.0], 'scale': [0.55, 0.55, 0.55]},
    'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 2.6, -1.0], 'scale': [0.55, 0.55, 0.55]},
    # on the ground: on its side, the lanyard loop flat on the grass
    'ground': {'rotation': [0, 0, 90], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    # item frame: lying across the frame, lens to the left
    'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    'gui': {'rotation': [25, -35, 0], 'translation': [0, 0, 0], 'scale': [0.8, 0.8, 0.8]},
}


def write_json():
    model = {
        'loader': 'neoforge:obj',
        'model': '%s:models/item/tracking_lamp_3d.obj' % FH,
        'flip_v': False,
        'automatic_culling': False,
        'emissive_ambient': True,
        'shade_quads': True,
        'textures': {'particle': '%s:item/tracking_lamp/armour' % FH},
        'gui_light': 'front',
        'display': DISPLAY,
    }
    with open(os.path.join(MOD, 'tracking_lamp_3d.json'), 'w', newline='\n') as fh:
        json.dump(model, fh, indent=1)
        fh.write('\n')
    item = {
        'loader': 'neoforge:separate_transforms',
        'gui_light': 'front',
        'base': {'parent': '%s:item/tracking_lamp_3d' % FH},
        'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': '%s:item/tracking_lamp' % FH}}},
    }
    with open(os.path.join(MOD, 'tracking_lamp.json'), 'w', newline='\n') as fh:
        json.dump(item, fh, indent=1)
        fh.write('\n')


def main():
    tex_grip()
    tex_anodized()
    tex_armour()
    tex_bezel()
    tex_lens()
    tex_rubber()
    tex_cord()
    m = lamp_mesh()
    write_obj(m)
    write_json()
    print('tracking lamp: %d vertices, %d faces' % (len(m.v), sum(len(f) for f in m.faces.values())))


if __name__ == '__main__':
    main()
