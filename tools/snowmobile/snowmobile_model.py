#!/usr/bin/env python3
"""[1.2.5] The snowmobile: procedural mesh + texture (original work, generated here; nothing imported).

    python3 tools/snowmobile/snowmobile_model.py [--preview out.png]

Writes
  patch/assets/frontierhunts/models/entity/snowmobile.fhvm   the mesh (same FHVM v2 format as the realistic ATV)
  patch/assets/frontierhunts/textures/entity/snowmobile.png  512x512 atlas

Model space while building: metres, x = the rider's left, y = up, z = forward, origin on the snow under the middle of
the machine. Exported in model pixels (x16). Parts (renderer indices):
  0 body   1 left ski + spindle   2 right ski + spindle   3 handlebar   4 track (v scrolls)   5 lights (full bright)
  6 windshield (translucent)
"""
import math
import os
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts')
TS = 512
rng = np.random.default_rng(1205)

# ============================================================================================ geometry
BODY, SKI_L, SKI_R, BAR, TRACK, LIGHTS, GLASS = range(7)
PIVOTS = {
    BODY: (0, 0, 0),
    SKI_L: (0.53, 0.0, 0.95),
    SKI_R: (-0.53, 0.0, 0.95),
    BAR: (0.0, 0.95, 0.40),
    TRACK: (0, 0, 0),
    LIGHTS: (0, 0, 0),
    GLASS: (0, 0, 0),
}
TEXIDX = {TRACK: 1, GLASS: 2}

tris = {k: [] for k in PIVOTS}  # part -> list of (p0, p1, p2, material, uvinfo)


def V(*a):
    return np.array(a, dtype=np.float64)


def tri(part, a, b, c, mat, uv=None):
    a, b, c = V(*a), V(*b), V(*c)
    n = np.cross(b - a, c - a)
    if np.linalg.norm(n) < 1e-10:
        return
    tris[part].append((a, b, c, mat, uv))


def quad(part, a, b, c, d, mat, uv=None):
    """a b c d counter-clockwise seen from outside"""
    if uv is None:
        tri(part, a, b, c, mat)
        tri(part, a, c, d, mat)
    else:
        tri(part, a, b, c, mat, (uv[0], uv[1], uv[2]))
        tri(part, a, c, d, mat, (uv[0], uv[2], uv[3]))


def oquad(part, a, b, c, d, mat, out, uv=None):
    """a quad turned to face along out"""
    a, b, c, d = V(*a), V(*b), V(*c), V(*d)
    n = np.cross(c - a, d - b)
    if n @ out < 0:
        a, b, c, d = d, c, b, a
        if uv is not None:
            uv = (uv[3], uv[2], uv[1], uv[0])
    quad(part, a, b, c, d, mat, uv)


def otri(part, a, b, c, mat, out):
    a, b, c = V(*a), V(*b), V(*c)
    if np.cross(b - a, c - a) @ out < 0:
        b, c = c, b
    tri(part, a, b, c, mat)


def box(part, x0, x1, y0, y1, z0, z1, mat, faces='xXyYzZ'):
    p = [V(x, y, z) for z in (z0, z1) for y in (y0, y1) for x in (x0, x1)]
    # index: x + 2y + 4z
    F = {
        'x': (0, 4, 6, 2), 'X': (1, 3, 7, 5),
        'y': (0, 1, 5, 4), 'Y': (2, 6, 7, 3),
        'z': (0, 2, 3, 1), 'Z': (4, 5, 7, 6),
    }
    for f in faces:
        i = F[f]
        quad(part, p[i[0]], p[i[1]], p[i[2]], p[i[3]], mat)


def loft(part, sections, mat, cap0=True, cap1=True, matfn=None):
    """sections: list of arrays (n,3) of the same length, each a closed loop; faces are turned outward (away from the
    middle of their sections) and the caps away from the run"""
    for s in range(len(sections) - 1):
        A0, A1 = sections[s], sections[s + 1]
        mid = (A0.mean(0) + A1.mean(0)) / 2
        n = len(A0)
        for j in range(n):
            k = (j + 1) % n
            m = matfn(s, j) if matfn else mat
            q = (A0[j] + A0[k] + A1[k] + A1[j]) / 4
            run = A1.mean(0) - A0.mean(0)
            out = q - mid
            out = out - run * (out @ run) / (run @ run + 1e-12)
            oquad(part, A0[j], A0[k], A1[k], A1[j], m, out)
    if cap0:
        S = sections[0]
        c = S.mean(0)
        back = sections[0].mean(0) - sections[1].mean(0)
        for j in range(len(S)):
            otri(part, c, S[(j + 1) % len(S)], S[j], matfn(-1, j) if matfn else mat, back)
    if cap1:
        S = sections[-1]
        c = S.mean(0)
        fwd = sections[-1].mean(0) - sections[-2].mean(0)
        for j in range(len(S)):
            otri(part, c, S[j], S[(j + 1) % len(S)], matfn(len(sections) - 1, j) if matfn else mat, fwd)


def frame(d):
    d = d / np.linalg.norm(d)
    up = V(0, 1, 0) if abs(d[1]) < 0.9 else V(1, 0, 0)
    u = np.cross(d, up)
    u /= np.linalg.norm(u)
    w = np.cross(u, d)
    return u, w


def tube(part, pts, r, mat, seg=8, caps=True):
    pts = [V(*p) for p in pts]
    secs = []
    for i, p in enumerate(pts):
        if i == 0:
            d = pts[1] - pts[0]
        elif i == len(pts) - 1:
            d = pts[-1] - pts[-2]
        else:
            d = (pts[i + 1] - pts[i - 1])
        u, w = frame(d)
        rr = r(i) if callable(r) else r
        secs.append(np.array([p + rr * (math.cos(a) * u + math.sin(a) * w) for a in np.linspace(0, 2 * math.pi, seg, endpoint=False)]))
    # loops run counter-clockwise looking back along d: flip so faces point out
    secs = [s[::-1] for s in secs]
    loft(part, secs, mat, caps, caps)


def cyl(part, c, axis, r, length, mat, seg=14, capmat=None):
    c = V(*c)
    axis = V(*axis) / np.linalg.norm(axis)
    a, b = c - axis * length / 2, c + axis * length / 2
    u, w = frame(axis)
    ring = lambda p: np.array([p + r * (math.cos(t) * u + math.sin(t) * w) for t in np.linspace(0, 2 * math.pi, seg, endpoint=False)])
    loft(part, [ring(a), ring(b)], mat, matfn=lambda si, j: (capmat or mat) if si < 0 or si >= 1 else mat)


def mirror_x(fn):
    for sgn in (1, -1):
        fn(sgn)


# ------------------------------------------------------------------------------------------------ hood / body
def hood_section(z):
    """half cross-section (x>=0) of the cowl at z: list of (x, y) from the belly centre round the side to the top
    centre, plus a material per edge"""
    # profiles along z (metres): belly height, crease height, top height, half-width
    def lerp_tab(tab):
        zs = [t[0] for t in tab]
        return float(np.interp(z, zs, [t[1] for t in tab]))
    w = lerp_tab([(0.30, 0.43), (0.55, 0.46), (0.95, 0.45), (1.25, 0.37), (1.48, 0.22), (1.62, 0.07)])
    bot = lerp_tab([(0.30, 0.24), (0.9, 0.24), (1.3, 0.27), (1.62, 0.33)])
    crease = lerp_tab([(0.30, 0.62), (0.8, 0.58), (1.2, 0.50), (1.62, 0.38)])
    top = lerp_tab([(0.30, 0.97), (0.55, 0.95), (0.95, 0.80), (1.3, 0.62), (1.62, 0.42)])
    pts = [
        (0.0, bot),
        (w * 0.70, bot),
        (w * 0.93, bot + 0.04),
        (w, crease - 0.06),
        (w * 0.98, crease),          # body crease: sharp
        (w * 0.86, crease + (top - crease) * 0.45),
        (w * 0.68, top - 0.035),
        (w * 0.40, top - 0.006),
        (0.0, top),
    ]
    return pts


def hood():
    zs = [0.30, 0.42, 0.55, 0.70, 0.85, 1.00, 1.12, 1.24, 1.36, 1.46, 1.55, 1.62]
    secs = []
    for z in zs:
        half = hood_section(z)
        right = [(-x, y) for x, y in reversed(half[1:-1])]
        loopxy = half + right  # bottom centre -> left side -> top centre -> right side
        secs.append(np.array([V(x, y, z) for x, y in loopxy]))
    n = len(secs[0])

    def mat(si, j):
        if si < 0 or si >= len(zs) - 1:
            return 'black'
        k = (j + 1) % n
        ym = (secs[si][j][1] + secs[si][k][1] + secs[si + 1][j][1] + secs[si + 1][k][1]) / 4
        bot = hood_section((zs[si] + zs[si + 1]) / 2)[0][1]
        return 'black' if ym < bot + 0.05 else 'paint'  # the belly pan
    loft(BODY, secs, 'paint', cap0=True, cap1=True, matfn=mat)


def tunnel():
    # the tunnel over the track, the running boards, the rear bumper, the snow flap
    box(BODY, -0.26, 0.26, 0.50, 0.56, -1.48, 0.32, 'black')                         # tunnel top
    mirror_x(lambda s: box(BODY, *sorted((s * 0.25, s * 0.27)), 0.28, 0.56, -1.48, 0.32, 'black'))  # tunnel sides
    # running boards with their outer rails
    def board(s):
        x0, x1 = sorted((s * 0.27, s * 0.46))
        box(BODY, x0, x1, 0.335, 0.36, -0.98, 0.30, 'alu')
        tube(BODY, [(s * 0.465, 0.35, -1.0), (s * 0.47, 0.35, -0.1), (s * 0.46, 0.36, 0.28), (s * 0.43, 0.40, 0.36)], 0.016, 'steel', seg=6)
        # toe holds
        box(BODY, *sorted((s * 0.30, s * 0.44)), 0.36, 0.40, 0.24, 0.30, 'black')
    mirror_x(board)
    # rear bumper / grab loop
    tube(BODY, [(0.27, 0.46, -1.40), (0.29, 0.47, -1.52), (0.20, 0.48, -1.58), (-0.20, 0.48, -1.58), (-0.29, 0.47, -1.52), (-0.27, 0.46, -1.40)],
         0.018, 'steel', seg=6)
    # snow flap
    quad(BODY, (-0.24, 0.10, -1.50), (0.24, 0.10, -1.50), (0.24, 0.50, -1.49), (-0.24, 0.50, -1.49), 'flap')
    quad(BODY, (0.24, 0.10, -1.505), (-0.24, 0.10, -1.505), (-0.24, 0.50, -1.495), (0.24, 0.50, -1.495), 'flap')
    # taillight housing
    box(BODY, -0.13, 0.13, 0.50, 0.58, -1.47, -1.40, 'black')
    # passenger grab handles along the back of the seat
    mirror_x(lambda sg: tube(BODY, [(sg * 0.17, 0.60, -1.30), (sg * 0.215, 0.70, -1.24), (sg * 0.22, 0.72, -1.05), (sg * 0.20, 0.62, -0.95)],
                             0.014, 'black', seg=6))


def seat():
    # long two-up seat, rounded top, a step up for the passenger
    zs = [-1.20, -1.10, -0.95, -0.75, -0.55, -0.35, -0.15, 0.00, 0.10]
    tops = [0.74, 0.79, 0.81, 0.80, 0.78, 0.77, 0.77, 0.79, 0.76]
    halfw = [0.13, 0.17, 0.185, 0.185, 0.18, 0.18, 0.175, 0.16, 0.12]
    secs = []
    for z, t, w in zip(zs, tops, halfw):
        b = 0.55
        pts = [(0.0, b), (w * 0.9, b), (w, b + 0.08), (w, t - 0.07), (w * 0.85, t - 0.015), (w * 0.45, t), (0.0, t + 0.004)]
        loop = pts + [(-x, y) for x, y in reversed(pts[1:-1])]
        secs.append(np.array([V(x, y, z) for x, y in loop])[::-1])
    loft(BODY, secs, 'seat')


def console():
    # tank / console between the seat and the cowl, rising to the steering post
    zs = [0.06, 0.18, 0.32, 0.42]
    secs = []
    for z in zs:
        t = float(np.interp(z, [0.06, 0.42], [0.80, 0.99]))
        w = float(np.interp(z, [0.06, 0.42], [0.16, 0.30]))
        pts = [(0.0, 0.50), (w, 0.50), (w, t - 0.08), (w * 0.7, t - 0.01), (0.0, t)]
        loop = pts + [(-x, y) for x, y in reversed(pts[1:-1])]
        secs.append(np.array([V(x, y, z) for x, y in loop])[::-1])
    loft(BODY, secs, 'black')
    # side panels under the seat front (painted), meeting the cowl
    def panel(s):
        x = s * 0.30
        a, b, c, d = (x, 0.36, 0.10), (x, 0.36, 0.40), (x, 0.70, 0.40), (x, 0.62, 0.10)
        if s > 0:
            quad(BODY, a, b, c, d, 'paint')
        else:
            quad(BODY, d, c, b, a, 'paint')
    mirror_x(panel)
    # fuel cap
    cyl(BODY, (0.0, 0.80, 0.30), (0, 1, 0.25), 0.045, 0.04, 'steel', seg=12)


def windshield():
    # a low mountain windshield: from the cowl at z 0.60 back and up to z 0.40, gently curved
    rows = 4
    cols = 7
    for i in range(rows):
        for j in range(cols):
            def P(ii, jj):
                t = ii / rows
                u = jj / cols * 2 - 1
                w = 0.31 - 0.07 * t
                x = u * w
                z = 0.62 - 0.22 * t - 0.06 * (u * u) * (1 - 0.3 * t)
                y = 0.93 + 0.30 * t - 0.02 * u * u
                return (x, y, z)
            a, b, c, d = P(i, j), P(i, j + 1), P(i + 1, j + 1), P(i + 1, j)
            quad(GLASS, b, a, d, c, 'glass')
            quad(GLASS, a, b, c, d, 'glass')
    # its black trim along the bottom
    tube(BODY, [(0.31, 0.93, 0.56), (0.0, 0.935, 0.62), (-0.31, 0.93, 0.56)], 0.018, 'black', seg=6)


def hood_y(x, z):
    """the top of the cowl over (x, z): cast straight down onto the body triangles"""
    best = None
    for (a, b, c, m, uv) in tris[BODY]:
        if m != 'paint':
            continue
        xs = (a[0], b[0], c[0])
        zs = (a[2], b[2], c[2])
        if not (min(xs) <= x <= max(xs) and min(zs) <= z <= max(zs)):
            continue
        den = (b[2] - c[2]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[2] - c[2])
        if abs(den) < 1e-12:
            continue
        l0 = ((b[2] - c[2]) * (x - c[0]) + (c[0] - b[0]) * (z - c[2])) / den
        l1 = ((c[2] - a[2]) * (x - c[0]) + (a[0] - c[0]) * (z - c[2])) / den
        l2 = 1 - l0 - l1
        if min(l0, l1, l2) < -1e-6:
            continue
        y = l0 * a[1] + l1 * b[1] + l2 * c[1]
        best = y if best is None else max(best, y)
    return best


def lights():
    # two angular headlights set into the nose, the taillight, the gauge screen
    def head(sg):
        corners = [(0.07, 1.40), (0.13, 1.44), (0.27, 1.30), (0.25, 1.24), (0.15, 1.30)]
        pts = [V(sg * x, hood_y(sg * x, z) + 0.008, z) for x, z in corners]
        c = sum(pts) / len(pts)
        for i in range(len(pts)):
            otri(LIGHTS, c, pts[i], pts[(i + 1) % len(pts)], 'lamp', V(0, 1, 0.6))
    mirror_x(head)
    quad(LIGHTS, (-0.12, 0.505, -1.4715), (0.12, 0.505, -1.4715), (0.12, 0.57, -1.4715), (-0.12, 0.57, -1.4715), 'tail')
    # gauge screen on the bar riser
    quad(LIGHTS, (-0.07, 1.075, 0.322), (0.07, 1.075, 0.322), (0.07, 1.13, 0.30), (-0.07, 1.13, 0.30), 'screen')


def handlebar():
    # steering post, riser, bar with a forward sweep, grips, hand guards, throttle lever, gauge pod
    tube(BAR, [(0, 0.86, 0.46), (0, 0.97, 0.40), (0, 1.06, 0.33)], 0.025, 'steel', seg=8)
    box(BAR, -0.05, 0.05, 1.03, 1.10, 0.27, 0.36, 'black')
    tube(BAR, [(-0.44, 1.12, 0.22), (-0.30, 1.10, 0.27), (-0.10, 1.09, 0.30), (0.10, 1.09, 0.30), (0.30, 1.10, 0.27), (0.44, 1.12, 0.22)],
         0.014, 'steel', seg=8)
    mirror_x(lambda s: tube(BAR, [(s * 0.33, 1.105, 0.255), (s * 0.45, 1.121, 0.215)], 0.022, 'grip', seg=8))
    # gauge pod
    box(BAR, -0.08, 0.08, 1.07, 1.14, 0.29, 0.34, 'black')

    def guard(s):
        # a curved shell in front of each grip
        for i in range(6):
            t0, t1 = i / 6, (i + 1) / 6
            def P(t, up):
                a = math.pi * (0.15 + 0.7 * t)
                return (s * (0.40 + 0.07 * math.cos(a) * 0.4), 1.11 + 0.075 * math.sin(a) * (1 if up else -1) * 0.8,
                        0.29 + 0.05 * math.sin(a))
            a, b = P(t0, True), P(t1, True)
            c, d = (s * 0.30, 1.10, 0.29 + 0.02 * t1), (s * 0.30, 1.10, 0.29 + 0.02 * t0)
            if s > 0:
                quad(BAR, a, b, c, d, 'paint')
                quad(BAR, d, c, b, a, 'black')
            else:
                quad(BAR, d, c, b, a, 'paint')
                quad(BAR, a, b, c, d, 'black')
    mirror_x(guard)
    # throttle lever (right) and brake lever (left)
    tube(BAR, [(-0.31, 1.10, 0.26), (-0.36, 1.085, 0.31)], 0.008, 'black', seg=5)
    tube(BAR, [(0.29, 1.11, 0.27), (0.40, 1.12, 0.31)], 0.007, 'steel', seg=5)


def ski(part, s):
    # a mountain ski: running from z 0.45 to the curled tip, keel underneath, a loop handle at the tip, the spindle
    cx = s * 0.53
    path = []
    for z in np.linspace(0.42, 1.48, 9):
        path.append((z, 0.035))
    for a in np.linspace(0.0, 1.0, 7)[1:]:
        ang = a * 1.25
        r = 0.32
        path.append((1.48 + r * math.sin(ang), 0.035 + r * (1 - math.cos(ang))))
    secs = []
    for i, (z, y) in enumerate(path):
        hw = 0.075 if i < len(path) - 3 else 0.075 - 0.02 * (i - (len(path) - 3))
        th = 0.022
        secs.append(np.array([V(cx - hw, y - th, z), V(cx + hw, y - th, z), V(cx + hw, y + th, z), V(cx - hw, y + th, z)]))
    # the tail end is a little bevelled
    secs.insert(0, np.array([V(cx - 0.06, 0.03, 0.38), V(cx + 0.06, 0.03, 0.38), V(cx + 0.06, 0.05, 0.38), V(cx - 0.06, 0.05, 0.38)]))
    loft(part, [p[::-1] for p in secs], 'ski')
    # keel / carbide runner
    box(part, cx - 0.012, cx + 0.012, 0.0, 0.016, 0.50, 1.40, 'steel', faces='xXyzZ')
    # top saddle and spindle
    box(part, cx - 0.04, cx + 0.04, 0.055, 0.11, 0.86, 1.04, 'black')
    tube(part, [(cx, 0.09, 0.95), (cx, 0.20, 0.95), (cx - s * 0.02, 0.33, 0.94)], 0.022, 'steel', seg=8)
    # ski loop
    tube(part, [(cx + 0.06, 0.24, 1.66), (cx + 0.07, 0.34, 1.72), (cx, 0.40, 1.76), (cx - 0.07, 0.34, 1.72), (cx - 0.06, 0.24, 1.66)],
         0.012, 'steel', seg=5)


def suspension():
    # double A-arms and a coil-over shock each side, front bumper tube
    def side(s):
        sp = (s * 0.51, 0.30, 0.94)
        tube(BODY, [(s * 0.22, 0.30, 0.80), sp], 0.016, 'steel', seg=6)
        tube(BODY, [(s * 0.22, 0.30, 1.10), sp], 0.016, 'steel', seg=6)
        tube(BODY, [(s * 0.24, 0.42, 0.86), (s * 0.49, 0.36, 0.94)], 0.014, 'steel', seg=6)
        tube(BODY, [(s * 0.24, 0.42, 1.04), (s * 0.49, 0.36, 0.94)], 0.014, 'steel', seg=6)
        # shock body and coil
        a, b = V(s * 0.20, 0.66, 0.84), V(s * 0.42, 0.34, 0.95)
        d = b - a
        tube(BODY, [a, a + d * 0.55], 0.022, 'shock', seg=8)
        tube(BODY, [a + d * 0.5, b], 0.011, 'steel', seg=6)
        u, w = frame(d)
        coil = []
        for t in np.linspace(0.15, 0.85, 40):
            ang = t * 2 * math.pi * 6
            coil.append(a + d * t + 0.036 * (math.cos(ang) * u + math.sin(ang) * w))
        tube(BODY, coil, 0.008, 'spring', seg=5, caps=False)
    mirror_x(side)
    tube(BODY, [(0.30, 0.34, 1.50), (0.22, 0.38, 1.66), (0.0, 0.40, 1.70), (-0.22, 0.38, 1.66), (-0.30, 0.34, 1.50)], 0.016, 'black', seg=6)


def track():
    # the track as an extruded side profile: flat on the snow, rising at the front over the drive sprocket, a long
    # top run back to the rear idlers
    prof = []
    # bottom run (z from rear to front) on the snow
    for z in np.linspace(-1.30, -0.05, 12):
        prof.append((z, 0.0))
    # front approach: up to the drive sprocket
    for a in np.linspace(0, 1, 6)[1:]:
        prof.append((-0.05 + 0.32 * a, 0.0 + 0.36 * a ** 1.3))
    # round the sprocket
    cz, cy, r = 0.20, 0.33, 0.10
    for ang in np.linspace(-0.3, math.pi * 0.62, 6):
        prof.append((cz + r * math.cos(ang), cy + r * math.sin(ang)))
    # top return
    for z in np.linspace(0.12, -1.25, 10):
        prof.append((z, 0.47))
    # round the rear idler
    cz, cy, r = -1.30, 0.235, 0.235
    for ang in np.linspace(math.pi * 0.5, math.pi * 1.5, 8)[1:-1]:
        prof.append((cz + r * math.cos(ang), cy + r * math.sin(ang)))
    prof = np.array(prof)
    # arc length along the loop (v of the tread texture)
    seg = np.r_[np.hypot(*np.diff(np.vstack([prof, prof[:1]]), axis=0).T)]
    s = np.r_[0.0, np.cumsum(seg)]
    hw = 0.19
    n = len(prof)
    for i in range(n):
        j = (i + 1) % n
        z0, y0 = prof[i]
        z1, y1 = prof[j]
        a, b = V(hw, y0, z0), V(hw, y1, z1)
        c, d = V(-hw, y1, z1), V(-hw, y0, z0)
        # outer surface: outward normal = (dz, -dy) rotated... check with the cross product order
        out = V(0.0, (y0 + y1) / 2 - 0.24, (z0 + z1) / 2 - (-0.55))
        oquad(TRACK, d, c, b, a, 'tread', out, uv=((0.0, s[i]), (0.0, s[i + 1]), (1.0, s[i + 1]), (1.0, s[i])))
    # the rubber side walls (a fan from the middle of the loop)
    cz, cy = prof[:, 0].mean(), prof[:, 1].mean()
    for i in range(n):
        j = (i + 1) % n
        otri(TRACK, (hw, cy, cz), (hw, prof[i][1], prof[i][0]), (hw, prof[j][1], prof[j][0]), 'rubber', V(1, 0, 0))
        otri(TRACK, (-hw, cy, cz), (-hw, prof[j][1], prof[j][0]), (-hw, prof[i][1], prof[i][0]), 'rubber', V(-1, 0, 0))
    return s[-1]


def idlers():
    # rear idler wheels and the slide rails seen through the sides
    mirror_x(lambda s: cyl(BODY, (s * 0.205, 0.235, -1.30), (1, 0, 0), 0.115, 0.03, 'wheel', seg=16, capmat='wheel'))
    mirror_x(lambda s: cyl(BODY, (s * 0.205, 0.10, -0.55), (1, 0, 0), 0.07, 0.03, 'wheel', seg=12, capmat='wheel'))
    mirror_x(lambda s: box(BODY, *sorted((s * 0.195, s * 0.215)), 0.08, 0.13, -1.25, -0.05, 'alu'))


def exhaust():
    cyl(BODY, (-0.40, 0.31, 0.38), (0.1, 0, -1), 0.04, 0.12, 'steel', seg=10, capmat='black')


# ============================================================================================ texture
REG = {
    # name: (x0, y0, x1, y1) in texels
    'paint_side': (0, 0, 320, 128),
    'paint_top': (0, 128, 160, 224),
    'paint_front': (160, 128, 256, 224),
    'black': (256, 128, 320, 192),
    'seat': (320, 0, 448, 128),
    'alu': (256, 192, 320, 256),
    'steel': (448, 0, 480, 32),
    'shock': (480, 0, 512, 32),
    'spring': (448, 32, 480, 64),
    'grip': (480, 32, 512, 64),
    'ski': (320, 128, 448, 192),
    'rubber': (448, 64, 512, 128),
    'wheel': (448, 128, 512, 192),
    'flap': (320, 192, 384, 256),
    'bag': (384, 192, 448, 256),
    'lamp': (0, 224, 64, 256),
    'tail': (64, 224, 96, 256),
    'screen': (96, 224, 128, 256),
    'glass': (128, 224, 256, 256),
}
PAINT = (178, 28, 34)
PAINT_DK = (120, 14, 20)


def noise_img(w, h, base, amp, blur=0.0, seed=0):
    r = np.random.default_rng(seed)
    n = r.normal(0, 1, (h, w, 1))
    im = np.clip(np.array(base, np.float64)[None, None, :] + n * amp, 0, 255)
    img = Image.fromarray(im.astype(np.uint8), 'RGB')
    if blur:
        img = img.filter(ImageFilter.GaussianBlur(blur))
    return img


def font(size):
    for p in ['/usr/share/fonts/truetype/dejavu/DejaVuSans-BoldOblique.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def paint_side(w, h):
    im = noise_img(w, h, PAINT, 3, 0.6, 1)
    d = ImageDraw.Draw(im)
    # the side graphic: a black sweep from the bottom rear rising toward the nose, a white pinstripe over it
    d.polygon([(0, h), (0, h * 0.62), (w * 0.35, h * 0.52), (w * 0.75, h * 0.36), (w, h * 0.30), (w, h * 0.52), (w * 0.72, h * 0.62),
               (w * 0.30, h * 0.80), (w * 0.08, h)], fill=(22, 22, 24))
    d.line([(0, h * 0.585), (w * 0.35, h * 0.49), (w * 0.75, h * 0.33), (w, h * 0.27)], fill=(236, 236, 232), width=3)
    d.line([(w * 0.10, h), (w * 0.32, h * 0.86), (w * 0.72, h * 0.67), (w, h * 0.58)], fill=(160, 160, 160), width=2)
    # name
    f = font(int(h * 0.17))
    d.text((w * 0.30, h * 0.60), 'FRONTIER', font=f, fill=(240, 240, 236))
    f2 = font(int(h * 0.09))
    d.text((w * 0.31, h * 0.79), 'MOUNTAIN 850', font=f2, fill=(200, 200, 196))
    return im


def track_texture():
    tr = noise_img(64, TRACK_H, (26, 26, 26), 3, 0.0, 15)
    d = ImageDraw.Draw(tr)
    for i, y in enumerate(range(0, TRACK_H, TREAD_PX)):
        d.rectangle([2, y, 61, y + 4], fill=(42, 42, 42))
        d.line([(2, y), (61, y)], fill=(62, 62, 62))
        if i % 2 == 0:
            for cx in (16, 48):
                d.rectangle([cx - 2, y + 6, cx + 2, y + 8], fill=(124, 126, 130))  # traction studs
    return tr


def build_texture():
    tex = Image.new('RGBA', (TS, TS), (0, 0, 0, 255))

    def put(name, img):
        x0, y0, x1, y1 = REG[name]
        tex.paste(img.resize((x1 - x0, y1 - y0)), (x0, y0))
    put('paint_side', paint_side(640, 256))
    top = noise_img(160, 96, PAINT, 3, 0.6, 2)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 40, 160, 56], fill=(22, 22, 24))
    d.rectangle([0, 46, 160, 49], fill=(236, 236, 232))
    put('paint_top', top)
    put('paint_front', noise_img(96, 96, PAINT, 3, 0.6, 3))
    put('black', noise_img(64, 64, (30, 30, 33), 5, 0.4, 4))
    seat = noise_img(128, 128, (26, 26, 28), 6, 0.3, 5)
    d = ImageDraw.Draw(seat)
    for y in range(0, 128, 16):
        d.line([(0, y), (128, y)], fill=(36, 36, 40), width=1)  # stitched pleats
    put('seat', seat)
    alu = noise_img(64, 64, (172, 176, 180), 7, 0.0, 6)
    d = ImageDraw.Draw(alu)
    for y in range(4, 64, 10):
        for x in range(4 + (y // 10 % 2) * 5, 64, 10):
            d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(52, 54, 58))  # traction holes
    put('alu', alu)
    put('steel', noise_img(32, 32, (150, 152, 156), 6, 0.6, 7))
    put('shock', noise_img(32, 32, (200, 160, 60), 5, 0.6, 8))
    put('spring', noise_img(32, 32, (190, 30, 34), 4, 0.6, 9))
    put('grip', noise_img(32, 32, (20, 20, 20), 4, 0.0, 10))
    put('ski', noise_img(128, 64, (28, 28, 31), 4, 0.4, 11))
    put('rubber', noise_img(64, 64, (22, 22, 22), 3, 0.6, 12))
    wheel = Image.new('RGB', (64, 64), (24, 24, 24))
    d = ImageDraw.Draw(wheel)
    d.ellipse([6, 6, 58, 58], fill=(170, 172, 176))
    d.ellipse([22, 22, 42, 42], fill=(80, 82, 86))
    put('wheel', wheel)
    put('flap', noise_img(64, 64, (18, 18, 18), 3, 0.0, 13))
    bag = noise_img(64, 64, (36, 36, 38), 8, 0.0, 14)
    d = ImageDraw.Draw(bag)
    d.rectangle([0, 28, 64, 34], fill=(150, 30, 34))
    put('bag', bag)
    lamp = Image.new('RGB', (64, 32), (240, 244, 255))
    d = ImageDraw.Draw(lamp)
    for x in range(4, 64, 12):
        d.ellipse([x, 8, x + 9, 24], fill=(255, 255, 238))
    put('lamp', lamp)
    put('tail', Image.new('RGB', (32, 32), (220, 24, 24)))
    scr = Image.new('RGB', (32, 32), (40, 120, 170))
    d = ImageDraw.Draw(scr)
    d.rectangle([3, 10, 28, 14], fill=(200, 230, 255))
    put('screen', scr)
    # tinted glass with a sky reflection, partly see-through
    g = Image.new('RGBA', (128, 32), (0, 0, 0, 0))
    gp = g.load()
    for y in range(32):
        for x in range(128):
            t = y / 31
            gp[x, y] = (int(40 + 30 * (1 - t)), int(46 + 34 * (1 - t)), int(56 + 40 * (1 - t)), int(150 + 40 * t))
    x0, y0, x1, y1 = REG['glass']
    tex.paste(g, (x0, y0))
    return tex


# ============================================================================================ export
TREAD_PERIOD_M = 0.076
TREAD_PX = 12
TRACK_H = 1024


def uv_for(part, a, b, c, mat, uvi, track_len):
    """per-corner UVs in 0..1 atlas coordinates"""
    n = np.cross(b - a, c - a)
    n /= np.linalg.norm(n)
    pts = (a, b, c)
    if mat == 'tread':
        # its own texture (snowmobile_track.png, 64 x TRACK_H): a whole number of lugs round the loop, so no seam
        lugs = round(track_len / TREAD_PERIOD_M)
        k = lugs * TREAD_PX / track_len
        return [((2 + u * 60) / 64.0, v * k / TRACK_H) for (u, v) in uvi]
    if mat == 'paint':
        ax = np.argmax(np.abs(n))
        if ax == 0 and abs(n[0]) > 0.55:
            x0, y0, x1, y1 = REG['paint_side']
            out = []
            for p in pts:
                zt = (p[2] - (-0.0)) / 1.75
                zt = 1.0 - zt if n[0] > 0 else zt
                yt = 1.0 - (p[1] - 0.22) / 0.82
                out.append(((x0 + np.clip(zt, 0, 1) * (x1 - x0)) / TS, (y0 + np.clip(yt, 0, 1) * (y1 - y0)) / TS))
            return out
        if n[1] > 0.5:
            x0, y0, x1, y1 = REG['paint_top']
            return [((x0 + np.clip((p[2] - 0.2) / 1.5, 0, 1) * (x1 - x0)) / TS, (y0 + np.clip(0.5 - p[0], 0, 1) * (y1 - y0)) / TS) for p in pts]
        x0, y0, x1, y1 = REG['paint_front']
        return [((x0 + np.clip(0.5 + p[0], 0, 1) * (x1 - x0)) / TS, (y0 + np.clip(1 - p[1], 0, 1) * (y1 - y0)) / TS) for p in pts]
    x0, y0, x1, y1 = REG[mat]
    ax = np.argmax(np.abs(n))
    axes = [(1, 2), (0, 2), (0, 1)][ax]
    return fix_tile(pts, axes, x0, y0, x1, y1)


def fix_tile(pts, axes, x0, y0, x1, y1):
    # one continuous projection per triangle, scaled into the region, offset so it stays inside
    us = np.array([p[axes[0]] * 3.1 for p in pts])
    vs = np.array([p[axes[1]] * 2.7 for p in pts])
    us -= math.floor(us.min())
    vs -= math.floor(vs.min())
    su = max(1.0, us.max())
    sv = max(1.0, vs.max())
    us /= su
    vs /= sv
    return [((x0 + 1 + u * (x1 - x0 - 2)) / TS, (y0 + 1 + v * (y1 - y0 - 2)) / TS) for u, v in zip(us, vs)]


def smooth_normals(part_tris):
    """auto-smooth: a corner takes the mean of the face normals around its position within 40 degrees"""
    fn = []
    acc = {}
    for i, (a, b, c, m, uv) in enumerate(part_tris):
        n = np.cross(b - a, c - a)
        area = np.linalg.norm(n)
        n = n / area
        fn.append(n)
        for p in (a, b, c):
            k = tuple(np.round(p, 4))
            acc.setdefault(k, []).append((n, area))
    out = []
    lim = math.cos(math.radians(40))
    for i, (a, b, c, m, uv) in enumerate(part_tris):
        ns = []
        for p in (a, b, c):
            k = tuple(np.round(p, 4))
            s = np.zeros(3)
            for n, ar in acc[k]:
                if n @ fn[i] > lim:
                    s += n * ar
            ns.append(s / (np.linalg.norm(s) + 1e-12))
        out.append(ns)
    return out


def export(path):
    track_len = None
    hood(); tunnel(); seat(); console(); windshield(); lights(); handlebar(); suspension(); idlers(); exhaust()
    ski(SKI_L, 1)
    ski(SKI_R, -1)
    track_len = track()
    order = [BODY, SKI_L, SKI_R, BAR, TRACK, LIGHTS, GLASS]
    buf = bytearray(b'FHVM')
    buf += struct.pack('>ii', 2, len(order))
    total = 0
    for part in order:
        T = tris[part]
        piv = PIVOTS[part]
        if part == TRACK:
            # the track's "pivot" carries its tread scroll instead: texels per metre of travel, lug period, height
            buf += struct.pack('>3f', round(track_len / TREAD_PERIOD_M) * TREAD_PX / track_len, TREAD_PX, TRACK_H)
        else:
            buf += struct.pack('>3f', *(v * 16.0 for v in piv))
        buf += struct.pack('>i', TEXIDX.get(part, 0))
        buf += struct.pack('>i', len(T))
        ns = smooth_normals(T)
        for (a, b, c, m, uvi), nn in zip(T, ns):
            uvs = uv_for(part, a, b, c, m, uvi, track_len)
            for p, uv, n in zip((a, b, c), uvs, nn):
                buf += struct.pack('>8f', p[0] * 16, p[1] * 16, p[2] * 16, uv[0], uv[1], n[0], n[1], n[2])
        total += len(T)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(bytes(buf))
    return total, track_len


def main():
    tex = build_texture()
    tp = os.path.join(A, 'textures', 'entity', 'snowmobile.png')
    os.makedirs(os.path.dirname(tp), exist_ok=True)
    tex.save(tp, optimize=True)
    track_texture().save(os.path.join(A, 'textures', 'entity', 'snowmobile_track.png'), optimize=True)
    total, tl = export(os.path.join(A, 'models', 'entity', 'snowmobile.fhvm'))
    print(f'{total} triangles, track loop {tl:.2f} m, texture {tp}')
    if '--preview' in sys.argv:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import snowmobile_preview
        snowmobile_preview.render_all(os.path.join(A, 'models', 'entity', 'snowmobile.fhvm'),
                                      [tp, os.path.join(A, 'textures', 'entity', 'snowmobile_track.png'), tp],
                                      sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
