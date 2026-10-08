#!/usr/bin/env python3
"""[atvfuel] Procedural art for the ATV fuel / rear-rack rig workstream.

Generates (all original, procedural):
  patch/assets/frontierhunts/models/entity/atv_rig.fhrg      meshes (box body, lid, feet, carriers, can, strap)
  patch/assets/frontierhunts/textures/entity/atv_rig.png     atlas, realistic ATV look (tan lid / charcoal)
  patch/assets/frontierhunts/textures/entity/atv_rig_classic.png  atlas, classic woodland-camo ATV look
  patch/assets/frontierhunts/textures/item/atv_rig_3d.png    128 px atlas for the 3D item models
  patch/assets/frontierhunts/models/item/{jerry_can,atv_cargo_box,atv_can_carrier}.json  (field_shelter loader)

usage: python3 tools/atvfuel_art.py [repo_root] [preview_dir]

Mesh space: authored y-up in model pixels (1/16 block before the ATV renderer's 1.18 scale), origin at the
bottom centre of each attachment, +z towards the rear of the ATV. Exported y-down (ATV model space) with
counter-clockwise outward winding, which is what the ATV renderer's frontier_sculpt (culling) render type expects.

FHRG binary (big endian): 'FHRG', int version=1, int parts, per part: int tris, tris*3 x
  (float x,y,z, float u,v, float nx,ny,nz, int argb)
"""
import json
import os
import struct
import sys

import numpy as np
from PIL import Image
from scipy.spatial import ConvexHull

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))
PREV = sys.argv[2] if len(sys.argv) > 2 else None
ASSETS = os.path.join(ROOT, 'patch/assets/frontierhunts')

ATLAS = 512
REG = 128
DENS = 10.0  # texels per model pixel
REGIONS = {
    'body': (0, 0), 'lid': (1, 0), 'steel': (2, 0), 'zinc': (3, 0),
    'web': (0, 1), 'red': (1, 1), 'black': (2, 1), 'label': (3, 1),
    'rubber': (0, 2), 'redface': (1, 2), 'plate': (2, 2), 'reflector': (3, 2),
}
# part order shared with AtvRigRender.java
PARTS = ['box_body', 'box_lid', 'box_feet', 'carrier_l', 'carrier_r', 'carrier_feet', 'can', 'can_strap']


# ------------------------------------------------------------------------------------------------ geometry
class Mesh:
    def __init__(self):
        self.tris = []  # dict(v=3x3, mat, uv=None|3x2, shade)

    def hull(self, pts, mat, shade=1.0, uvo=None):
        pts = np.unique(np.round(np.asarray(pts, float), 6), axis=0)
        h = ConvexHull(pts)
        c = pts.mean(0)
        for simp in h.simplices:
            v = pts[simp]
            n = np.cross(v[1] - v[0], v[2] - v[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            if np.dot(n, v.mean(0) - c) < 0:
                v = v[[0, 2, 1]]
            self.tris.append(dict(v=v, mat=mat, uv=None, shade=shade, uvo=uvo))
        return self

    def quad(self, p0, p1, p2, p3, mat, uv=None, shade=1.0):
        """CCW seen from the front (outside). uv: 4 (u,v) in 0..1 of the material region."""
        P = [np.asarray(p, float) for p in (p0, p1, p2, p3)]
        U = uv if uv is not None else [None] * 4
        self.tris.append(dict(v=np.array([P[0], P[1], P[2]]), mat=mat, uv=None if uv is None else np.array([U[0], U[1], U[2]]), shade=shade, uvo=None))
        self.tris.append(dict(v=np.array([P[0], P[2], P[3]]), mat=mat, uv=None if uv is None else np.array([U[0], U[2], U[3]]), shade=shade, uvo=None))

    def extend(self, other):
        self.tris += other.tris
        return self

    def moved(self, dx=0, dy=0, dz=0):
        m = Mesh()
        for t in self.tris:
            t2 = dict(t)
            t2['v'] = t['v'] + np.array([dx, dy, dz])
            m.tris.append(t2)
        return m


def cbox(m, x0, y0, z0, x1, y1, z1, c=0.3, cb=None, taper=0.0, mat='body', shade=1.0, uvo=None):
    """Chamfered box (all edges c; bottom ring cb), optional bottom inset (draft)."""
    cb = c if cb is None else cb
    pts = []
    for Y, cc, ins, dy in ((y0, cb, taper, -1), (y1, c, 0.0, 1)):
        for X, dx in ((x0 + ins, -1), (x1 - ins, 1)):
            for Z, dz in ((z0 + ins, -1), (z1 - ins, 1)):
                pts += [(X, Y - dy * cc, Z - dz * cc), (X - dx * cc, Y, Z - dz * cc), (X - dx * cc, Y - dy * cc, Z)]
    return m.hull(pts, mat, shade, uvo)


def cyl(m, axis, center, r, h0, h1, seg=12, mat='black', shade=1.0, bevel=0.0):
    pts = []
    for hh, rr in ((h0, r - bevel), (h0 + bevel, r), (h1 - bevel, r), (h1, r - bevel)):
        for i in range(seg):
            a = 2 * np.pi * (i + 0.5) / seg
            ca, sa = np.cos(a) * rr, np.sin(a) * rr
            if axis == 'y':
                p = (center[0] + ca, hh, center[2] + sa)
            elif axis == 'x':
                p = (hh, center[1] + ca, center[2] + sa)
            else:
                p = (center[0] + ca, center[1] + sa, hh)
            pts.append(p)
    return m.hull(pts, mat, shade)


def obox(m, center, ax, ay, az, hx, hy, hz, mat, shade=1.0):
    """Oriented box (no chamfer)."""
    c = np.asarray(center, float)
    ax, ay, az = (np.asarray(a, float) / np.linalg.norm(a) for a in (ax, ay, az))
    pts = [c + sx * hx * ax + sy * hy * ay + sz * hz * az for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
    return m.hull(pts, mat, shade)


# ---- cargo box: 10.0 (x) x 5.2 (z) x 5.0 (y), hinge at the front (seat side, -z), latches at the rear
BW, BD, BH = 5.0, 2.6, 5.0
LID_Y = 3.55   # body/lid split
HINGE = (LID_Y, -BD - 0.05)  # (y, z) of the lid hinge axis, local y-up


def build_box():
    body = Mesh()
    cbox(body, -BW + 0.12, 0.0, -BD + 0.12, BW - 0.05, LID_Y, BD - 0.05, c=0.45, cb=0.35, taper=0.12, mat='body')
    # moulded reinforcement ribs (two bands)
    for y in (0.95, 2.35):
        cbox(body, -BW - 0.02, y, -BD - 0.02, BW + 0.02, y + 0.38, BD + 0.02, c=0.42, mat='body', shade=1.06)
    # lid seat lip
    cbox(body, -BW - 0.1, LID_Y - 0.42, -BD - 0.1, BW + 0.1, LID_Y, BD + 0.1, c=0.25, mat='body', shade=1.08)
    # side grab handles (x ends): mounts + bar
    for s in (-1, 1):
        x_in, x_out = s * BW, s * (BW + 0.42)
        lo, hi = min(x_in, x_out), max(x_in, x_out)
        for z in (-1.35, 1.05):
            cbox(body, lo, 2.0, z, hi, 2.95, z + 0.3, c=0.08, mat='black')
        xo0, xo1 = (lo + 0.12, hi) if s > 0 else (lo, hi - 0.12)
        cbox(body, xo0, 2.55, -1.35, xo1, 2.95, 1.35, c=0.12, mat='black', shade=1.05)
    # latches on the rear face (+z): zinc base + black lever
    for x in (-3.0, 3.0):
        cbox(body, x - 0.65, 2.85, BD + 0.05, x + 0.65, 3.55, BD + 0.28, c=0.08, mat='zinc')
        cbox(body, x - 0.5, 3.0, BD + 0.28, x + 0.5, 4.35, BD + 0.5, c=0.1, mat='black', shade=0.95)
    # padlock hasp between the latches
    cbox(body, -0.35, 2.7, BD + 0.05, 0.35, 3.5, BD + 0.22, c=0.06, mat='zinc', shade=0.95)
    # red reflectors in black bezels on the rear face, between the ribs
    for x0 in (-3.9, 2.5):
        cbox(body, x0 - 0.08, 1.45, BD - 0.3, x0 + 1.48, 2.17, BD + 0.04, c=0.06, mat='black')
        body.quad((x0, 1.53, BD + 0.045), (x0 + 1.4, 1.53, BD + 0.045), (x0 + 1.4, 2.09, BD + 0.045), (x0, 2.09, BD + 0.045),
                  'reflector', uv=[(0, 1), (1, 1), (1, 0), (0, 0)])

    lid = Mesh()
    cbox(lid, -BW - 0.12, LID_Y, -BD - 0.12, BW + 0.12, LID_Y + 0.55, BD + 0.12, c=0.2, mat='lid')
    cbox(lid, -BW + 0.05, LID_Y + 0.5, -BD + 0.05, BW - 0.05, BH, BD - 0.05, c=0.55, cb=0.05, mat='lid')
    # stiffening ribs moulded into the lid top (run left-right)
    for z in (-1.3, 0.0, 1.3):
        cbox(lid, -BW + 0.9, BH - 0.05, z - 0.22, BW - 0.9, BH + 0.16, z + 0.22, c=0.1, mat='lid', shade=1.07)
    # hinge knuckles at the front
    for x in (-3.4, 3.4):
        cyl(lid, 'x', (0, LID_Y + 0.05, -BD - 0.2), 0.22, x - 0.6, x + 0.6, seg=8, mat='zinc')
    # latch keepers on the lid edge
    for x in (-3.0, 3.0):
        cbox(lid, x - 0.45, LID_Y + 0.1, BD + 0.1, x + 0.45, LID_Y + 0.5, BD + 0.3, c=0.06, mat='zinc')

    feet = Mesh()  # unit height (y -1..0), scaled per ATV style to reach the rack
    for x in (-3.8, 3.8):
        for z in (-1.9, 1.9):
            cbox(feet, x - 0.55, -1.0, z - 0.45, x + 0.55, 0.0, z + 0.45, c=0.0, mat='steel')
        cbox(feet, x - 0.3, -1.0, -2.2, x + 0.3, -0.75, 2.2, c=0.0, mat='steel', shade=0.9)  # rail clamp bar
    return body, lid, feet


# ---- 10 L jerry can: 2.3 (x) x 3.5 (z) x 4.6 (y) + handles; spout to the rear (+z)
CX, CZ, CH = 1.15, 1.75, 4.6


def build_can():
    can = Mesh()
    cbox(can, -CX, 0.0, -CZ, CX, CH, CZ, c=0.32, cb=0.25, mat='red')
    # pressed X stiffeners + border bead on both big faces
    for s in (-1, 1):
        x = s * (CX + 0.04)
        a, b = np.array([0, 0.45, -CZ + 0.45]), np.array([0, CH - 0.5, CZ - 0.45])
        for p, q in ((a, b), (np.array([0, 0.45, CZ - 0.45]), np.array([0, CH - 0.5, -CZ + 0.45]))):
            d = q - p
            c = (p + q) / 2
            c[0] = x - s * 0.05
            obox(can, c, (0, d[1], d[2]), (1, 0, 0), np.cross((1, 0, 0), (0, d[1], d[2])), np.linalg.norm(d) / 2, 0.1, 0.17, 'redface', shade=1.08)
    # hazard label on both narrow ends
    for s in (-1, 1):
        z = s * (CZ + 0.012)
        hw, y0, y1 = 0.62, 1.7, 2.95
        if s > 0:
            can.quad((-hw, y0, z), (hw, y0, z), (hw, y1, z), (-hw, y1, z), 'label', uv=[(0, 1), (1, 1), (1, 0), (0, 0)])
        else:
            can.quad((hw, y0, z), (-hw, y0, z), (-hw, y1, z), (hw, y1, z), 'label', uv=[(0, 1), (1, 1), (1, 0), (0, 0)])
    # three carry handles side by side across the width, running front-back
    for x in (-0.72, 0.0, 0.72):
        for z in (-1.25, 0.55):
            cbox(can, x - 0.15, CH - 0.1, z, x + 0.15, CH + 0.55, z + 0.32, c=0.06, mat='red')
        cbox(can, x - 0.16, CH + 0.42, -1.3, x + 0.16, CH + 0.72, 0.92, c=0.1, mat='red', shade=1.05)
    # spout boss + cap + clamp lever (rear corner)
    cyl(can, 'y', (0, 0, 1.2), 0.5, CH - 0.2, CH + 0.12, seg=12, mat='red', bevel=0.05)
    cyl(can, 'y', (0, 0, 1.2), 0.42, CH + 0.12, CH + 0.6, seg=12, mat='black', bevel=0.06)
    cbox(can, -0.12, CH + 0.3, 1.45, 0.12, CH + 0.55, 2.0, c=0.04, mat='zinc')
    # air breather cap at the other end
    cyl(can, 'y', (0, 0, -1.45), 0.16, CH - 0.05, CH + 0.18, seg=8, mat='black')
    return can


def build_carrier(side):
    """side -1 = left (x<0, box to +x), +1 = right. Origin at the can's bottom centre."""
    m = Mesh()
    inner = -side  # direction towards the box
    # base tray with turned-up lips
    cbox(m, -CX - 0.25, -0.28, -CZ - 0.25, CX + 0.25, 0.0, CZ + 0.25, c=0.05, mat='steel')
    for z0, z1 in ((-CZ - 0.25, -CZ - 0.05), (CZ + 0.05, CZ + 0.25)):
        cbox(m, -CX - 0.25, 0.0, z0, CX + 0.25, 0.75, z1, c=0.04, mat='steel')
    # inner back plate (towards the box) with two lightening slots done as raised frame
    xi = inner * (CX + 0.05)
    x0, x1 = sorted((xi, xi + inner * 0.22))
    cbox(m, x0, 0.0, -CZ - 0.25, x1, 3.6, CZ + 0.25, c=0.06, mat='steel')
    # outer retaining bar
    xo = -inner * (CX + 0.05)
    x0, x1 = sorted((xo, xo - inner * 0.2))
    for z in (-CZ + 0.1, CZ - 0.4):
        cbox(m, x0, 0.0, z, x1, 1.3, z + 0.3, c=0.04, mat='steel')
    cbox(m, x0, 1.0, -CZ + 0.1, x1, 1.3, CZ - 0.1, c=0.05, mat='steel')
    # strap anchor loops on the plate
    x0, x1 = sorted((xi + inner * 0.22, xi + inner * 0.4))
    cbox(m, x0, 2.7, -0.5, x1, 3.25, 0.5, c=0.05, mat='zinc')

    feet = Mesh()
    for z in (-1.1, 1.1):
        cbox(feet, -0.9, -1.0, z - 0.3, 0.9, 0.0, z + 0.3, c=0.0, mat='steel')
    return m, feet


def build_strap():
    m = Mesh()
    y0, y1, t = 2.75, 3.25, 0.07
    # three faces of webbing around the can + over the inner plate side (both sides symmetric so one mesh fits both)
    cbox(m, -CX - 0.3 - t, y0, CZ - 0.05, CX + 0.3 + t, y1, CZ + t, c=0.0, mat='web')
    cbox(m, -CX - 0.3 - t, y0, -CZ - t, CX + 0.3 + t, y1, -CZ + 0.05, c=0.0, mat='web')
    for s in (-1, 1):
        x0, x1 = sorted((s * (CX - 0.05), s * (CX + t)))
        cbox(m, x0, y0, -CZ - t, x1, y1, CZ + t, c=0.0, mat='web')
    # cam buckles on both big faces
    for s in (-1, 1):
        x0, x1 = sorted((s * (CX + t), s * (CX + t + 0.16)))
        cbox(m, x0, y0 - 0.12, -0.45, x1, y1 + 0.12, 0.45, c=0.04, mat='zinc')
    return m


# ------------------------------------------------------------------------------------------------ textures
rng = np.random.default_rng(7)


def noise(size, cells, seed):
    r = np.random.default_rng(seed).random((cells, cells))
    img = Image.fromarray((r * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
    return np.asarray(img, float) / 255.0


def fbm(size, seed, octaves=((4, .45), (8, .25), (16, .15), (32, .1), (64, .05))):
    out = np.zeros((size, size))
    for i, (c, a) in enumerate(octaves):
        out += noise(size, c, seed * 31 + i) * a
    return out / sum(a for _, a in octaves)


def region_plastic(base, seed, grain=0.035, scuff=0.06, dust=True):
    s = REG
    n = fbm(s, seed)
    fine = np.random.default_rng(seed).random((s, s))
    col = np.ones((s, s, 3)) * np.array(base, float)
    col *= (1 + (n - 0.5) * 0.12)[..., None]
    col *= (1 + (fine - 0.5) * grain * 2)[..., None]
    # light scuffs: short horizontal-ish streaks
    r = np.random.default_rng(seed + 5)
    for _ in range(26):
        x, y = r.integers(0, s, 2)
        L = r.integers(4, 14)
        for k in range(L):
            xx = (x + k) % s
            yy = (y + int(k * r.uniform(-.3, .3))) % s
            col[yy, xx] *= 1 + scuff * r.uniform(.4, 1)
    if dust:
        v = np.linspace(0, 1, s)[:, None]
        d = np.clip((v - 0.55) / 0.45, 0, 1) ** 1.6 * (0.6 + 0.4 * fbm(s, seed + 9))
        dustc = np.array([118, 104, 82], float)
        col = col * (1 - 0.28 * d[..., None]) + dustc * 0.28 * d[..., None]
    return col


def region_camo(seed):
    s = REG
    pal = [np.array(c, float) for c in ((92, 94, 60), (45, 54, 32), (82, 70, 46), (30, 33, 24))]
    a, b = fbm(s, seed, ((3, .5), (6, .3), (12, .2))), fbm(s, seed + 3, ((3, .5), (6, .3), (12, .2)))
    col = np.zeros((s, s, 3))
    col[:] = pal[0]
    col[a > 0.52] = pal[1]
    col[(b > 0.56) & (a <= 0.52)] = pal[2]
    col[(a > 0.6) & (b > 0.55)] = pal[3]
    fine = np.random.default_rng(seed).random((s, s))
    col *= (1 + (fine - 0.5) * 0.08)[..., None]
    col *= (1 + (fbm(s, seed + 7) - 0.5) * 0.1)[..., None]
    return col


def region_steel(base, seed, wear=(96, 94, 90)):
    s = REG
    col = np.ones((s, s, 3)) * np.array(base, float)
    col *= (1 + (fbm(s, seed) - 0.5) * 0.16)[..., None]
    fine = np.random.default_rng(seed).random((s, s))
    col *= (1 + (fine - 0.5) * 0.06)[..., None]
    w = fbm(s, seed + 2, ((16, .5), (32, .3), (64, .2)))
    chips = w > 0.66
    col[chips] = col[chips] * 0.3 + np.array(wear, float) * 0.7
    rust = (fbm(s, seed + 4, ((8, .5), (16, .3), (32, .2))) > 0.68) & ~chips
    col[rust] = col[rust] * 0.75 + np.array([92, 58, 36], float) * 0.25
    return col


def region_zinc(seed):
    s = REG
    col = np.ones((s, s, 3)) * np.array([150, 152, 154], float)
    streak = np.repeat(np.random.default_rng(seed).random((1, s)), s, 0)
    col *= (1 + (streak - 0.5) * 0.12)[..., None]
    col *= (1 + (fbm(s, seed) - 0.5) * 0.2)[..., None]
    return col


def region_web(seed, base=(46, 48, 40)):
    s = REG
    y, x = np.mgrid[0:s, 0:s]
    weave = ((x // 2 + y // 2) % 2) * 0.12 + ((y % 2) * 0.05)
    col = np.ones((s, s, 3)) * np.array(base, float)
    col *= (0.9 + weave)[..., None]
    col *= (1 + (fbm(s, seed) - 0.5) * 0.15)[..., None]
    return col


def region_paint(base, seed, chips=True):
    s = REG
    col = np.ones((s, s, 3)) * np.array(base, float)
    col *= (1 + (fbm(s, seed, ((32, .5), (64, .5))) - 0.5) * 0.08)[..., None]  # orange peel
    col *= (1 + (fbm(s, seed + 1) - 0.5) * 0.14)[..., None]
    if chips:
        w = fbm(s, seed + 2, ((24, .45), (48, .35), (96, .2)))
        m = w > 0.71
        col[m] = np.array([62, 58, 54], float) * (0.9 + 0.2 * w[m, None])
        edge = (w > 0.675) & ~m
        col[edge] *= 0.8
    v = np.linspace(0, 1, s)[:, None]
    grime = np.clip((v - 0.6) / 0.4, 0, 1) ** 1.5 * fbm(s, seed + 3)
    col = col * (1 - 0.35 * grime[..., None]) + np.array([80, 66, 50], float) * 0.35 * grime[..., None]
    return col


def region_label():
    s = REG
    col = np.ones((s, s, 3)) * np.array([214, 178, 52], float)
    y, x = np.mgrid[0:s, 0:s]
    border = (x < 8) | (x >= s - 8) | (y < 8) | (y >= s - 8)
    col[border] = (28, 26, 22)
    # flame pictogram (black) inside a diamond
    cx, cy = s / 2, s / 2 + 4
    dia = (np.abs(x - cx) + np.abs(y - cy)) < 46
    dia_in = (np.abs(x - cx) + np.abs(y - cy)) < 40
    col[dia & ~dia_in] = (28, 26, 22)
    yy = (y - (cy + 18)) / 30.0
    xx = (x - cx) / 14.0
    flame = (yy < 0.2) & (yy > -1.45) & (np.abs(xx) < (1.0 - (yy + 0.4) ** 2 * 0.9) * (1 + 0.2 * np.sin(yy * 9)))
    col[flame & dia_in] = (28, 26, 22)
    base = (y > cy + 22) & (y < cy + 26) & (np.abs(x - cx) < 16)
    col[base] = (28, 26, 22)
    col *= (1 + (fbm(s, 77) - 0.5) * 0.12)[..., None]
    return col


def region_reflector():
    s = REG
    y, x = np.mgrid[0:s, 0:s]
    col = np.ones((s, s, 3)) * np.array([150, 28, 22], float)
    hexes = ((x // 8 + (y // 8) % 2) % 2) * 0.12
    col *= (0.88 + hexes)[..., None]
    rim = (x < 6) | (x >= s - 6) | (y < 6) | (y >= s - 6)
    col[rim] = (30, 30, 30)
    return col


def make_atlas(style):
    img = np.zeros((ATLAS, ATLAS, 3))

    def put(name, arr):
        cx, cy = REGIONS[name]
        img[cy * REG:(cy + 1) * REG, cx * REG:(cx + 1) * REG] = arr

    put('body', region_plastic((50, 51, 52), 11))
    if style == 'classic':
        put('lid', region_camo(21))
    else:
        put('lid', region_plastic((186, 171, 146), 21, grain=0.03, scuff=-0.05))
    put('steel', region_steel((40, 41, 43), 31))
    put('zinc', region_zinc(41))
    put('web', region_web(51, (44, 47, 36) if style == 'classic' else (38, 38, 38)))
    put('red', region_paint((158, 30, 22), 61))
    put('redface', region_paint((172, 36, 26), 62))
    put('black', region_plastic((30, 30, 31), 71, grain=0.05, scuff=0.12, dust=False))
    put('label', region_label())
    put('rubber', region_plastic((24, 24, 24), 81, grain=0.08, scuff=0.0))
    put('plate', region_steel((60, 62, 64), 91))
    put('reflector', region_reflector())
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


# ------------------------------------------------------------------------------------------------ export
def uv_for(t, bbmin):
    if t['uv'] is not None:
        cx, cy = REGIONS[t['mat']]
        uv = t['uv']
        u = (cx * REG + 1 + uv[:, 0] * (REG - 2)) / ATLAS
        v = (cy * REG + 1 + uv[:, 1] * (REG - 2)) / ATLAS
        return np.stack([u, v], 1)
    v = t['v']
    n = np.cross(v[1] - v[0], v[2] - v[0])
    a = np.argmax(np.abs(n))
    if a == 1:      # top / bottom: x, z
        A, B = v[:, 0] - bbmin[0], v[:, 2] - bbmin[2]
    elif a == 0:    # left / right: z, -y (down the image = down the part)
        A, B = v[:, 2] - bbmin[2], (bbmax_y - v[:, 1])
    else:           # front / back: x, -y
        A, B = v[:, 0] - bbmin[0], (bbmax_y - v[:, 1])
    cx, cy = REGIONS[t['mat']]
    u = (cx * REG + 1 + np.clip(A * DENS, 0, REG - 2)) / ATLAS
    w = (cy * REG + 1 + np.clip(B * DENS, 0, REG - 2)) / ATLAS
    return np.stack([u, w], 1)


bbmax_y = 0.0


def shade_for(t, ymin, ymax):
    v = t['v']
    n = np.cross(v[1] - v[0], v[2] - v[0])
    n = n / (np.linalg.norm(n) + 1e-12)
    h = np.clip((v[:, 1] - ymin) / max(ymax - ymin, 1e-6), 0, 1)
    ao = 0.8 + 0.2 * np.sqrt(h)
    if n[1] < -0.5:
        ao = ao * 0.82
    return np.clip(ao * t['shade'], 0, 1.15)


def export_part(mesh):
    """-> (list of tri vertex rows y-down [x,y,z,u,v,nx,ny,nz,argb]) and y-up quads for item models"""
    global bbmax_y
    allv = np.concatenate([t['v'] for t in mesh.tris])
    bbmin = allv.min(0)
    bbmax_y = allv[:, 1].max()
    ymin, ymax = bbmin[1], bbmax_y
    rows = []
    for t in mesh.tris:
        uv = uv_for(t, bbmin)
        sh = shade_for(t, ymin, ymax)
        v = t['v']
        n = np.cross(v[1] - v[0], v[2] - v[0])
        n = n / (np.linalg.norm(n) + 1e-12)
        # y-up -> y-down is a reflection: flip y and reverse the winding to stay CCW-outward
        order = [0, 2, 1]
        for i in order:
            c = int(np.clip(sh[i] * 255 / 1.15, 0, 255))
            argb = (255 << 24) | (c << 16) | (c << 8) | c
            rows.append([v[i, 0], -v[i, 1], v[i, 2], uv[i, 0], uv[i, 1], n[0], -n[1], n[2], argb])
        # check winding in y-down space
        p = np.array([[v[i, 0], -v[i, 1], v[i, 2]] for i in order])
        nn = np.cross(p[1] - p[0], p[2] - p[0])
        assert np.dot(nn, [n[0], -n[1], n[2]]) > 0
    return rows


def write_fhrg(path, parts):
    with open(path, 'wb') as f:
        f.write(b'FHRG')
        f.write(struct.pack('>ii', 1, len(parts)))
        for rows in parts:
            f.write(struct.pack('>i', len(rows) // 3))
            for r in rows:
                f.write(struct.pack('>8fi', *[float(x) for x in r[:8]], int(r[8]) - (1 << 32) if r[8] >= (1 << 31) else int(r[8])))


# [atv2] gui scales re-fit so the largest on-screen extent is ~0.86 of a slot (carrier overflowed at 1.10)
def item_model(mesh_list, path, gui_scale, gui_rot=(25, 135, 0), center=None, extra_display=None):
    """field_shelter item model: quads in 0..1 block space (y-up), texture = item/atv_rig_3d."""
    tris = []
    for mesh in mesh_list:
        allv = np.concatenate([t['v'] for t in mesh.tris])
        global bbmax_y
        bbmin = allv.min(0)
        bbmax_y = allv[:, 1].max()
        for t in mesh.tris:
            uv = uv_for(t, bbmin)
            sh = shade_for(t, bbmin[1], bbmax_y)
            tris.append((t['v'], uv, float(sh.mean())))
    allv = np.concatenate([t[0] for t in tris])
    lo, hi = allv.min(0), allv.max(0)
    c = (lo + hi) / 2 if center is None else np.asarray(center)
    size = (hi - lo).max()
    k = 0.92 / size   # fit the longest side into the block
    faces = []
    for v, uv, sh in tris:
        q = (v - c) * k + 0.5
        g = int(np.clip(sh / 1.15 * 255 + 30, 0, 255))
        verts = [[round(float(q[i, 0]), 5), round(float(q[i, 1]), 5), round(float(q[i, 2]), 5), round(float(uv[i, 0]), 5), round(float(uv[i, 1]), 5)] for i in (0, 1, 2, 2)]
        faces.append({'m': 't', 'color': (g << 16) | (g << 8) | g, 'v': verts})
    disp = {
        'gui': {'rotation': list(gui_rot), 'translation': [0, 0, 0], 'scale': [gui_scale] * 3},
        'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 90, 0], 'scale': [0.9, 0.9, 0.9]},
        'head': {'translation': [0, 10, 0], 'scale': [0.8, 0.8, 0.8]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [1, 1, 0], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 45, 0], 'translation': [1, 1, 0], 'scale': [0.6, 0.6, 0.6]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.55, 0.55, 0.55]},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.55, 0.55, 0.55]},
    }
    if extra_display:
        disp.update(extra_display)
    model = {'loader': 'frontierhunts:field_shelter', 'gui_light': 'side',
             'textures': {'t': 'frontierhunts:item/atv_rig_3d', 'particle': 'frontierhunts:item/atv_rig_3d'},
             'shelter_faces': faces, 'display': disp}
    assert len(faces) <= 8192
    with open(path, 'w') as f:
        json.dump(model, f, separators=(',', ':'))
    return faces


def main():
    body, lid, feet = build_box()
    can = build_can()
    car_l, car_feet = build_carrier(-1)
    car_r, _ = build_carrier(1)
    strap = build_strap()
    meshes = {'box_body': body, 'box_lid': lid, 'box_feet': feet, 'carrier_l': car_l, 'carrier_r': car_r,
              'carrier_feet': car_feet, 'can': can, 'can_strap': strap}
    os.makedirs(os.path.join(ASSETS, 'models/entity'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'textures/entity'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'textures/item'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'models/item'), exist_ok=True)
    parts = [export_part(meshes[n]) for n in PARTS]
    write_fhrg(os.path.join(ASSETS, 'models/entity/atv_rig.fhrg'), parts)
    real = make_atlas('real')
    classic = make_atlas('classic')
    real.save(os.path.join(ASSETS, 'textures/entity/atv_rig.png'), optimize=True)
    classic.save(os.path.join(ASSETS, 'textures/entity/atv_rig_classic.png'), optimize=True)
    real.resize((128, 128), Image.LANCZOS).save(os.path.join(ASSETS, 'textures/item/atv_rig_3d.png'), optimize=True)
    # items: icons fill the slot like a vanilla block item (~15 px)
    item_model([can], os.path.join(ASSETS, 'models/item/jerry_can.json'), 0.84, gui_rot=(20, 120, 0))
    item_model([body, lid], os.path.join(ASSETS, 'models/item/atv_cargo_box.json'), 0.88, gui_rot=(28, 215, 0))
    item_model([car_l.moved(dx=-1.6), car_r.moved(dx=1.6)], os.path.join(ASSETS, 'models/item/atv_can_carrier.json'), 0.78, gui_rot=(28, 215, 0))
    for n in PARTS:
        print(n, len(meshes[n].tris), 'tris')
    if PREV:
        os.makedirs(PREV, exist_ok=True)
        np.save(os.path.join(PREV, 'meshes.npy'), {n: [(t['v'], t['mat']) for t in meshes[n].tris] for n in PARTS}, allow_pickle=True)


if __name__ == '__main__':
    main()
