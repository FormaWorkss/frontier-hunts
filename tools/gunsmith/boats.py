"""[1.1.0] The two fishing boats, as .fheq meshes in metres (1 block = 1 m), bow toward +z, waterline at y 0.

  rowboat  - an old lapstrake wooden rowboat (3.8 m): planked hull with visible lands, ribs under an inwale, risers,
             three thwarts, slatted floorboards, keel running into the stem, a shaped transom with a capping rail and
             bronze oarlocks; the two oars are parts 1 (port, +x) and 2 (starboard, -x), built along +x from their
             oarlock pivot so the renderer can swing, dip and feather them with the stroke.
  jon_boat - a flat-bottomed aluminium jon boat (4.0 m): chined hull with a raked bow, khaki-painted interior, side
             strakes, gunwale caps, three bench seats, a dry box on the bow bench, a portable red fuel tank with its
             line; a transom clamp bracket. The outboard (swivel tube, cowling, tiller, leg, lower unit, skeg) is part 3
             and turns about the swivel axis; the propeller is part 4 and spins about the prop shaft.

Every piece is checked to touch the piece it is fixed to (``check``), and both hulls get a water mask (``*_mask``):
a flat polygon just inside the hull above the waterline that the renderer draws depth-only, so the water surface
does not show inside the boat.

usage: python3 boats.py <out dir> [--check]
"""
import sys
import numpy as np
import gunkit as G
from gunkit import Part

WOOD = 0xB89A72
WOOD_D = 0x8A6A48
WOOD_L = 0xCDB28A
WOOD_FLOOR = 0xBFA27A
ALU = 0xD4D8DA
ALU_D = 0xA8ADB0
KHAKI = 0x9C9A7C
BLACK = 0x26282A
GREY = 0x6A6E72
BRONZE = 0xB08A50
RED = 0xB0302A
DRYBOX = 0x55603F


class Comp:
    """records which vertex range each named piece of a part occupies (for the touch check)"""

    def __init__(self):
        self.ranges = []

    def __call__(self, part, label, fn):
        a = len(part.v)
        fn()
        self.ranges.append((part.id, label, a, len(part.v)))


def pp(x):
    """ping-pong wrap into 0..1, so long hulls repeat the material cell without seams"""
    x = abs(x) % 2.0
    return 2.0 - x if x > 1.0 else x


def ring(outer, inner):
    """closed ring: the outer profile then the inner profile reversed (a shell with thickness)"""
    return list(outer) + list(reversed(inner))


def shell_loft(part, sections, cell, tint, uv_along=0.5, smooth_deg=40.0, style=None):
    """skin closed section rings (all in planes of constant z, same point count) with true face normals: smooth across
    gentle turns of the ring (the round of a hull), hard at sharp ones (the rim of a plank, a chine)"""
    S = np.array([np.asarray(r, float) for r in sections])          # m, k, 3
    m, k = S.shape[:2]
    cosl = np.cos(np.radians(smooth_deg))
    # orientation of each ring in its plane, so normals can be turned outward
    area = 0.5 * np.sum(S[:, :, 0] * np.roll(S[:, :, 1], -1, 1) - np.roll(S[:, :, 0], -1, 1) * S[:, :, 1], 1)
    dm = np.zeros_like(S)
    dm[1:-1] = S[2:] - S[:-2]
    dm[0] = S[1] - S[0]
    dm[-1] = S[-1] - S[-2]
    E = np.roll(S, -1, 1) - S                                        # segment j: point j -> j+1
    out2 = np.stack([E[:, :, 1], -E[:, :, 0], np.zeros((m, k))], -1) * np.sign(area)[:, None, None]
    Nseg = np.cross((dm + np.roll(dm, -1, 1)) / 2, E)                 # face normal of segment j at section i
    Nseg *= np.sign(np.sum(Nseg * out2, -1, keepdims=True) + 1e-30)
    Nseg = G.unit(Nseg)
    along = np.concatenate([[0], np.cumsum(np.linalg.norm(S[1:, 0] - S[:-1, 0], axis=1))])
    per = np.concatenate([np.zeros((m, 1)), np.cumsum(np.linalg.norm(E, axis=2), 1)], 1)
    for j in range(k):
        P, N, UV, T = [], [], [], []
        jn = (j + 1) % k
        for i in range(m):
            n0 = Nseg[i, j]
            na, nb = Nseg[i, (j - 1) % k], Nseg[i, jn]
            v0 = G.unit(n0 + na) if np.dot(n0, na) >= cosl else n0
            v1 = G.unit(n0 + nb) if np.dot(n0, nb) >= cosl else n0
            P += [S[i, j], S[i, jn]]
            N += [v0, v1]
            UV += [G.cell_uv(cell, pp(along[i] / uv_along), per[i, j] / max(per[i, -1], 1e-9)),
                   G.cell_uv(cell, pp(along[i] / uv_along), per[i, j + 1] / max(per[i, -1], 1e-9))]
        for i in range(m - 1):
            q = 2 * i
            T += [(q, q + 2, q + 3), (q, q + 3, q + 1)]
        c, tt = style(j) if style else (cell, tint)
        if style:
            UV = [G.cell_uv(c, *((np.array(uv) - np.array(G.cell_uv(cell, 0, 0))) / 0.238)) for uv in UV]
        part.add(P, N, UV, [tt] * len(P), T)


def rsweep(part, path, w, h, cell, tint, up=(0, 1, 0), side=None, caps=True, uv_along=0.4):
    """hard-edged rectangular sweep: width w along `side` (fixed vector, or cross(tangent, up)), height h across it.
    Each face carries its own normals so rails, slats and ribs light like squared timber, not like round tubes."""
    P = [np.asarray(p, float) for p in path]
    m = len(P)
    frames = []
    prev = None
    for i in range(m):
        t = G.unit(P[min(i + 1, m - 1)] - P[max(i - 1, 0)])
        s = np.asarray(side, float) if side is not None else np.cross(t, np.asarray(up, float))
        s = s - t * np.dot(s, t)
        if np.linalg.norm(s) < 1e-6:
            s = prev if prev is not None else np.array([1.0, 0, 0])
        s = G.unit(s)
        if prev is not None and np.dot(s, prev) < 0:
            s = -s
        prev = s
        n2 = G.unit(np.cross(s, t))
        frames.append((t, s, n2))
    along = np.concatenate([[0], np.cumsum([np.linalg.norm(P[i + 1] - P[i]) for i in range(m - 1)])])
    corners = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    for f in range(4):
        (a1, b1), (a2, b2) = corners[f], corners[(f + 1) % 4]
        pts, nrm, uv, tri = [], [], [], []
        for i in range(m):
            t, s, n2 = frames[i]
            fn = G.unit(s * (a1 + a2) / 2 + n2 * (b1 + b2) / 2)
            for (a, b), v in (((a1, b1), 0.0), ((a2, b2), 1.0)):
                pts.append(P[i] + s * a * w / 2 + n2 * b * h / 2)
                nrm.append(fn)
                uv.append(G.cell_uv(cell, pp(along[i] / uv_along), v))
        for i in range(m - 1):
            q = 2 * i
            tri += [(q, q + 2, q + 3), (q, q + 3, q + 1)]
        part.add(pts, nrm, uv, [tint] * len(pts), tri)
    if caps:
        for i, d in ((0, -1.0), (m - 1, 1.0)):
            t, s, n2 = frames[i]
            pts = [P[i] + s * a * w / 2 + n2 * b * h / 2 for a, b in corners]
            part.add(pts, [t * d] * 4, [G.cell_uv(cell, a * 0.5 + 0.5, b * 0.5 + 0.5) for a, b in corners], [tint] * 4, [(0, 1, 2), (0, 2, 3)])


# --------------------------------------------------------------------------------------------------------- rowboat
ROW_L = 3.8
ROW_SHELL = 0.028          # plank thickness
ROW_RIB = 0.02             # rib depth
ROW_SEAT_Y = 0.275         # thwart top
ROW_LOCK_Z = 0.30          # oarlocks (forward of the rower, who faces the bow)
ROW_LOCK_UP = 0.082        # oar pivot above the gunwale


def row_station(t):
    """half beam, keel y and sheer y at t (0 stern .. 1 bow)"""
    beam = 0.70 * np.sin(np.pi * (0.2 + 0.8 * t)) ** 0.85
    keel = -0.13 + 0.2 * max(0.0, t - 0.7) ** 2 / 0.09
    sheer = 0.47 + 0.11 * (2 * t - 1) ** 2 + 0.07 * t ** 3
    return beam, keel, sheer


def row_t(z):
    return (z + ROW_L / 2) / ROW_L


def row_surface(t, ins):
    """(half beam, keel y, sheer) of the surface `ins` inside the outer skin"""
    b, k, g = row_station(t)
    return max(b - ins, 0.004), k + ins * 0.9, g


def row_floor(t):
    """top of the cleats / underside of the floorboards: level amidships, rising with the keel at the bow"""
    b, k, g = row_surface(t, ROW_SHELL)
    return max(0.02, k + 0.1)


def row_profile(t, n, ins=0.0):
    b, k, g = row_surface(t, ins)
    return [(b * u, k + (g - k) * abs(u) ** 2.1) for u in np.linspace(-1, 1, n + 1)]


def row_y_at(t, x, ins):
    """height of the surface `ins` inside the skin at half-breadth x"""
    b, k, g = row_surface(t, ins)
    return k + (g - k) * min(1.0, abs(x) / b) ** 2.1


def row_x_at(t, y, ins):
    """half-breadth of the surface `ins` inside the skin at height y"""
    b, k, g = row_surface(t, ins)
    return b * max(0.0, min(1.0, (y - k) / (g - k))) ** (1 / 2.1)


def rowboat(lod, comp):
    close = lod == 'close'
    n = 18 if close else 10
    st = 32 if close else 16
    hull = Part(0)
    half = ROW_L / 2

    def shell():
        secs = []
        for i in range(st + 1):
            t = i / st
            z = -half + ROW_L * t
            secs.append([(x, y, z) for x, y in ring(row_profile(t, n), row_profile(t, n, ROW_SHELL))])
        shell_loft(hull, secs, G.BIRCH, WOOD, uv_along=0.5)
    comp(hull, 'shell', shell)

    if close:
        # the lands of the planks: thin proud strips along the outside at five heights each side
        for u in (0.2, 0.4, 0.58, 0.73, 0.87):
            for sgn in (-1, 1):
                def land(u=u, sgn=sgn):
                    path = []
                    for i in range(st + 1):
                        t = 0.012 + 0.97 * i / st
                        b, k, g = row_station(t)
                        x = b * u
                        path.append((sgn * (x + 0.003), k + (g - k) * u ** 2.1, -half + ROW_L * t))
                    rsweep(hull, path, 0.012, 0.008, G.BIRCH, WOOD_D)
                comp(hull, f'land{u}{sgn}', land)
        # steam-bent ribs inside, from inwale to inwale
        for i in range(1, 12):
            t = i / 12
            def rib(t=t):
                z = -half + ROW_L * t
                ins = ROW_SHELL + ROW_RIB / 2 - 0.003
                pts = [(x, y, z) for x, y in row_profile(t, 14, ins)]
                rsweep(hull, pts, ROW_RIB, 0.036, G.WALNUT, WOOD_D, up=(0, 0, 1))
            comp(hull, f'rib{i}', rib)
    # outwale (gunwale rail), inwale over the rib heads, risers under the thwarts
    for sgn in (-1, 1):
        def rails(sgn=sgn):
            out, inw, rise = [], [], []
            for i in range(st + 1):
                t = 0.008 + 0.984 * i / st
                b, k, g = row_station(t)
                z = -half + ROW_L * t
                out.append((sgn * (b + 0.004), g - 0.004, z))
                bi, ki, gi = row_surface(t, ROW_SHELL)
                inw.append((sgn * (bi - 0.012), g - 0.012, z))
            rsweep(hull, out, 0.044, 0.044, G.WALNUT, WOOD_D)
            rsweep(hull, inw, 0.044, 0.032, G.WALNUT, WOOD_D)
            for i in range(17):
                t = 0.13 + 0.71 * i / 16
                y = ROW_SEAT_Y - 0.022
                rise.append((sgn * (row_x_at(t, y, ROW_SHELL) - 0.008), y, -half + ROW_L * t))
            rsweep(hull, rise, 0.028, 0.024, G.WALNUT, WOOD_D)
        comp(hull, f'rails{sgn}', rails)

    # keel along the bottom, sweeping up into the stem at the bow
    def keel():
        pts = []
        for i in range(25):
            t = 0.0 + 0.94 * i / 24
            b, k, g = row_station(t)
            pts.append((0.0, k - 0.012, -half + ROW_L * t - (0.033 if i == 0 else 0.0)))
        b1, k1, g1 = row_station(1.0)
        pts += [(0.0, k1 - 0.03, half + 0.01), (0.0, (k1 + g1) / 2, half + 0.03), (0.0, g1 + 0.05, half + 0.035)]
        rsweep(hull, G.smooth_path(pts, 3), 0.052, 0.048, G.WALNUT, WOOD_D, side=(1, 0, 0))
    comp(hull, 'keel', keel)

    # shaped transom: the outer section at the stern, extruded through the end of the shell, with a capping rail
    def transom():
        b, k, g = row_station(0.0)
        sec = [(x, y) for x, y in row_profile(0.0, 16)] + [(0.0, g + 0.02)]
        hull.extrude(G.ccw(sec), -half - 0.035, -half + 0.03, G.BIRCH, WOOD_D, smooth_deg=50.0)
        hull.box((-b - 0.01, g - 0.005, -half - 0.05), (b + 0.01, g + 0.03, -half + 0.04), G.WALNUT, WOOD_D, r=0.008)
    comp(hull, 'transom', transom)

    # floorboards: five slats resting on the ribs, trimmed where the hull gets too narrow for them
    slats = (-0.24, -0.12, 0.0, 0.12, 0.24)
    for xc in slats:
        def slat(xc=xc):
            xo = abs(xc) + 0.048
            path = []
            for i in range(41):
                t = 0.06 + 0.86 * i / 40
                yb = row_floor(t)
                if row_x_at(t, yb, ROW_SHELL + ROW_RIB) < xo + 0.006:
                    continue
                path.append((xc, yb + 0.009, -half + ROW_L * t))
            if len(path) > 1:
                rsweep(hull, path, 0.096, 0.018, G.BIRCH, WOOD_FLOOR)
        comp(hull, f'slat{xc}', slat)
    # cross cleats under the slats, their ends let into the planking
    for t in (0.14, 0.32, 0.5, 0.68, 0.84):
        def cleat(t=t):
            yb = row_floor(t)
            z = -half + ROW_L * t
            x = row_x_at(t, yb - 0.025, ROW_SHELL) + 0.008
            hull.extrude(G.ccw([(-x, yb - 0.025), (x, yb - 0.025), (row_x_at(t, yb, ROW_SHELL) + 0.008, yb),
                                (-row_x_at(t, yb, ROW_SHELL) - 0.008, yb)]), z - 0.022, z + 0.022, G.WALNUT, WOOD_D, smooth_deg=5.0)
        comp(hull, f'cleat{t}', cleat)

    # thwarts (stern seat, rowing thwart, bow seat), ends let into the planking on the risers
    for t, d in ((0.2, 0.26), (0.5, 0.22), (0.76, 0.2)):
        def thwart(t=t, d=d):
            z = -half + ROW_L * t
            ts = (t - d / 2 / ROW_L, t, t + d / 2 / ROW_L)
            y0, y1 = ROW_SEAT_Y - 0.035, ROW_SEAT_Y
            x0 = min(row_x_at(q, y0, ROW_SHELL) for q in ts) + 0.01
            x1 = min(row_x_at(q, y1, ROW_SHELL) for q in ts) + 0.01
            hull.extrude(G.ccw([(-x0, y0), (x0, y0), (x1, y1), (-x1, y1)]), z - d / 2, z + d / 2, G.BIRCH, WOOD_L, smooth_deg=5.0)
        comp(hull, f'thwart{t}', thwart)

    # bronze oarlocks: a socket block on the gunwale (hull); the swivelling U horn with its pin is parts 5 / 6
    tl = row_t(ROW_LOCK_Z)
    b, k, g = row_station(tl)
    for sgn in (-1, 1):
        def lock(sgn=sgn):
            x = sgn * (b + 0.004)
            hull.box((x - 0.028, g - 0.02, ROW_LOCK_Z - 0.04), (x + 0.028, g + 0.032, ROW_LOCK_Z + 0.04), G.BRASS, BRONZE, r=0.01)
        comp(hull, f'lock{sgn}', lock)

    # bow breasthook and a painter ring on the stem
    def bowdeck():
        t0 = 0.94
        b0, k0, g0 = row_station(t0)
        z0 = -half + ROW_L * t0
        tri = G.ccw([(-b0 + 0.004, z0), (b0 - 0.004, z0), (0.0, half + 0.02)])
        g1 = row_station(1.0)[2]
        fr = np.array([[1, 0, 0, 0], [0, 0, 1, 0], [0, 1, 0, 0], [0, 0, 0, 1]], float)
        hull.extrude([(p[0], p[1]) for p in tri], g0 - 0.03, g0 + 0.0, G.WALNUT, WOOD_D, frame=fr)
    comp(hull, 'breasthook', bowdeck)

    oars = []
    for pid in (1, 2):
        o = Part(pid)
        def oar(o=o):
            seg = 10 if close else 6
            o.lathe([(-0.6, 0.0), (-0.6, 0.019), (-0.47, 0.021), (-0.43, 0.016), (1.2, 0.016), (1.3, 0.011)], G.WALNUT, WOOD_L,
                    seg=seg, axis=(1, 0, 0), ref=(0, 1, 0))
            o.box((1.22, -0.006, -0.068), (1.9, 0.006, 0.068), G.WALNUT, WOOD_L, r=0.004)
            o.lathe([(-0.07, 0.0), (-0.07, 0.021), (0.07, 0.021), (0.07, 0.0)], G.RUBBER, 0x5A3A22, seg=seg, axis=(1, 0, 0), ref=(0, 1, 0))
        comp(o, 'oar', oar)
        oars.append(o)
    for pid in (5, 6):
        h = Part(pid)
        def horn(h=h):
            # local to the oar pivot: pin down into the socket, base bar under the oar, two horns either side of it
            h.lathe([(0.0, 0.0), (0.0, 0.009), (0.05, 0.009), (0.05, 0.0)], G.BRASS, BRONZE, seg=8, origin=(0, -0.064, 0),
                    axis=(0, 1, 0), ref=(1, 0, 0))
            h.box((-0.008, -0.04, -0.035), (0.008, -0.026, 0.035), G.BRASS, BRONZE)
            for dz in (-0.028, 0.028):
                h.box((-0.008, -0.04, dz - 0.007), (0.008, 0.03, dz + 0.007), G.BRASS, BRONZE, r=0.003)
        comp(h, 'horn', horn)
        oars.append(h)
    return [hull] + oars


def row_mask():
    """depth-only cover just under the gunwale, inside the planking, from the transom to the stem"""
    y = 0.44  # [1.1.1] just under the gunwale, so a pitching hull never shows water inside
    left, right = [], []
    for i in range(41):
        t = 0.02 + 0.96 * i / 40
        x = row_x_at(t, y, ROW_SHELL) - 0.003
        if x < 0.004:
            continue
        z = -ROW_L / 2 + ROW_L * t
        left.append((x, z))
        right.append((-x, z))
    return flat_mask(left, right, y)


# --------------------------------------------------------------------------------------------------------- jon boat
JON_L = 4.0
JON_SHELL = 0.004
JON_SKIN = (0.006, 0.010)
SWIVEL = (0.0, -JON_L / 2 - 0.11)      # outboard swivel axis (x, z), just aft of the clamp bracket
PROP_AXIS_Y = -0.42


def jon_station(t):
    beam = 0.72 - 0.15 * max(0.0, t - 0.75) ** 1.5 / 0.25 ** 1.5
    bottom = -0.08 + 0.4 * max(0.0, (t - 0.72) / 0.28) ** 1.7
    sheer = 0.44 + 0.04 * max(0.0, t - 0.8) / 0.2
    return beam, bottom, sheer


def jon_profile(t, ins=0.0):
    b, k, g = jon_station(t)
    b -= ins
    k += ins
    c = 0.07
    return [(-b, g), (-b + 0.035, k + c), (-b + c + 0.01, k), (b - c - 0.01, k), (b - 0.035, k + c), (b, g)]


def jon_x_at(t, y, ins):
    """half-breadth of the side `ins` inside the skin at height y (y above the chine)"""
    b, k, g = jon_station(t)
    b -= ins
    k += ins
    f = (g - y) / max(g - (k + 0.07), 1e-6)
    return b - 0.035 * max(0.0, min(1.0, f))


def jon_t(z):
    return (z + JON_L / 2) / JON_L


def jon_boat(lod, comp):
    close = lod == 'close'
    st = 28 if close else 14
    half = JON_L / 2
    out = Part(0)

    def shells():
        # one ring: aluminium outside and on the rims, khaki-painted inside (segments 6..10 are the inner faces)
        secs = []
        n = st + 12
        for i in range(n + 1):
            t = 1.0 - (1.0 - i / n) ** 1.25      # denser toward the bow, where the bottom sweeps up
            z = -half + JON_L * t
            secs.append([(x, y, z) for x, y in ring(jon_profile(t), jon_profile(t, JON_SKIN[1]))])
        shell_loft(out, secs, G.BRIGHT, ALU, uv_along=0.6, smooth_deg=12.0,
                   style=lambda j: (G.TAN, KHAKI) if 6 <= j <= 10 else (G.BRIGHT, ALU))
    comp(out, 'shell', shells)

    # transom and bow plate: the outer section extruded through the ends of the shells
    def ends():
        b, k, g = jon_station(0.0)
        out.extrude(G.ccw(jon_profile(0.0)), -half - 0.035, -half + 0.03, G.BRIGHT, ALU_D, smooth_deg=10.0)
        b1, k1, g1 = jon_station(1.0)
        out.extrude(G.ccw(jon_profile(1.0)), half - 0.03, half + 0.03, G.BRIGHT, ALU_D, smooth_deg=10.0)
        # a wooden transom board inside, which the clamp bites on
        out.box((-0.24, 0.12, -half + 0.03), (0.24, g - 0.004, -half + 0.055), G.WALNUT, WOOD_D)
    comp(out, 'ends', ends)

    # gunwale caps and the pressed side strakes
    for sgn in (-1, 1):
        def caps(sgn=sgn):
            cap, strake = [], []
            for i in range(st + 1):
                t = 0.0 + 1.0 * i / st
                b, k, g = jon_station(t)
                z = -half - 0.035 + (JON_L + 0.065) * i / st
                cap.append((sgn * (b - 0.008), g + 0.01, z))
                yy = (k + 0.07 + g) / 2 + 0.02
                strake.append((sgn * (jon_x_at(t, yy, 0.0) + 0.006), yy, -half + JON_L * t))
            rsweep(out, cap, 0.044, 0.032, G.BRIGHT, ALU_D)
            if close:
                rsweep(out, strake[1:-2], 0.016, 0.024, G.BRIGHT, ALU)
        comp(out, f'caps{sgn}', caps)

    # interior floor (ribbed tread plate) and three bench seats with front/back panels
    def floor():
        bot = jon_station(0.0)[1] + JON_SKIN[1]
        out.box((-0.6, bot - 0.004, -half + 0.03), (0.6, bot + 0.012, 0.95), G.TAN, KHAKI)
        if close:
            for xr in np.linspace(-0.5, 0.5, 6):
                out.box((xr - 0.012, bot + 0.012, -half + 0.08), (xr + 0.012, bot + 0.018, 0.9), G.TAN, 0x8E8C70)
    comp(out, 'floor', floor)
    benches = ((-1.72, -1.34), (-0.3, 0.06), (0.82, 1.1))
    for z0, z1 in benches:
        def bench(z0=z0, z1=z1):
            ts = [jon_t(z) for z in np.linspace(z0, z1, 7)]
            x = min(jon_x_at(t, 0.24, JON_SKIN[1]) for t in ts) + 0.006
            k = max(jon_station(t)[1] for t in ts) + JON_SKIN[1]
            out.box((-x, 0.24, z0), (x, 0.28, z1), G.TAN, KHAKI, r=0.01)
            # front and back panels: the inside section of the hull up to the seat, so nothing pokes through the chines
            tn = min(ts, key=lambda q: jon_station(q)[0])
            sec = [(px, py) for px, py in jon_profile(tn, JON_SKIN[1] - 0.004) if py < 0.24]
            sec = [(-jon_x_at(tn, 0.245, JON_SKIN[1] - 0.004), 0.245)] + sec + [(jon_x_at(tn, 0.245, JON_SKIN[1] - 0.004), 0.245)]
            for za, zb in ((z0 + 0.01, z0 + 0.03), (z1 - 0.03, z1 - 0.01)):
                out.extrude(G.ccw(sec), za, zb, G.TAN, KHAKI, smooth_deg=5.0)
        comp(out, f'bench{z0}', bench)

    # clamp bracket over the transom: top bar, outer plate, inner jaw and the two T screws
    def clamp():
        g = jon_station(0.0)[2]
        zt = -half
        out.box((-0.075, g - 0.004, zt - 0.075), (0.075, g + 0.04, zt + 0.07), G.ANOD, GREY)
        out.box((-0.075, 0.26, zt - 0.075), (0.075, g + 0.04, zt - 0.035), G.ANOD, GREY)
        out.box((-0.06, 0.3, zt + 0.055), (0.06, g + 0.0, zt + 0.07), G.ANOD, GREY)
        for sx in (-0.045, 0.045):
            out.lathe([(0.0, 0.0), (0.0, 0.008), (0.06, 0.008), (0.06, 0.0)], G.BRIGHT, GREY, seg=6, origin=(sx, 0.34, zt + 0.065),
                      axis=(0, 0, 1), ref=(0, 1, 0))
            out.box((sx - 0.035, 0.332, zt + 0.12), (sx + 0.035, 0.348, zt + 0.135), G.BRIGHT, GREY)
        # the swivel bracket the motor turns in, bolted to the outer plate
        sx, sz = SWIVEL
        out.box((-0.05, 0.3, sz + 0.02), (0.05, 0.34, zt - 0.075), G.ANOD, GREY)
        out.box((-0.05, 0.46, sz + 0.02), (0.05, 0.5, zt - 0.075), G.ANOD, GREY)
    comp(out, 'clamp', clamp)

    # dry box on the bow bench (the boat's storage), portable fuel tank and its line to the motor
    def kit():
        z0, z1 = benches[2]
        out.box((-0.27, 0.28, z0 + 0.02), (0.27, 0.5, z1 - 0.02), G.POLY, DRYBOX, r=0.02)
        out.box((-0.28, 0.5, z0 + 0.01), (0.28, 0.54, z1 - 0.01), G.POLY, DRYBOX, r=0.02)
        for sx in (-0.15, 0.15):
            out.box((sx - 0.025, 0.42, z0 + 0.0), (sx + 0.025, 0.52, z0 + 0.02), G.POLY, BLACK)
        bot = jon_station(0.0)[1] + JON_SKIN[1] + 0.012
        tx, tz = -0.38, -1.83
        out.box((tx - 0.18, bot, tz - 0.1), (tx + 0.18, bot + 0.22, tz + 0.1), G.POLY, RED, r=0.03)
        out.lathe([(0.0, 0.0), (0.0, 0.03), (0.04, 0.03), (0.04, 0.0)], G.POLY, BLACK, seg=8, origin=(tx - 0.1, bot + 0.21, tz),
                  axis=(0, 1, 0), ref=(1, 0, 0))
        out.box((tx - 0.012, bot + 0.21, tz - 0.07), (tx + 0.012, bot + 0.26, tz + 0.07), G.POLY, BLACK)
        line = G.smooth_path([(tx + 0.17, bot + 0.12, tz), (-0.12, 0.3, -1.9), (-0.1, 0.51, -1.95), (-0.07, 0.515, -2.05),
                              (-0.03, 0.515, -2.1)], 4)
        out.sweep(line, [(0.008 * np.cos(a), 0.008 * np.sin(a)) for a in np.linspace(0, 2 * np.pi, 9)[:-1]], G.RUBBER, BLACK, caps=True)
    comp(out, 'kit', kit)
    if close:
        for sgn in (-1, 1):
            def rodholder(sgn=sgn):
                b, k, g = jon_station(jon_t(-0.8))
                x = sgn * (b - 0.03)
                out.lathe([(0.0, 0.0), (0.0, 0.02), (0.22, 0.02), (0.22, 0.0)], G.BRIGHT, GREY, seg=8,
                          origin=(x, g - 0.12, -0.8), axis=(0, 0.5, -0.866), ref=(1, 0, 0))
            comp(out, f'rod{sgn}', rodholder)

    # outboard motor (part 3): swivel tube, lower cowl, cowling, tiller, leg, cavitation plate, lower unit, skeg
    sx, sz = SWIVEL
    m = Part(3)
    def motor():
        m.lathe([(0.27, 0.0), (0.27, 0.035), (0.53, 0.035), (0.53, 0.0)], G.POLY, BLACK, seg=10, origin=(sx, 0.0, sz),
                axis=(0, 1, 0), ref=(0, 0, 1))
        cz0, cz1 = sz - 0.42, sz + 0.02
        m.box((-0.13, 0.5, cz0 + 0.04), (0.13, 0.6, cz1), G.POLY, 0x3A3C3E, r=0.03)          # lower cowl
        m.box((-0.155, 0.6, cz0), (0.155, 0.9, cz1 + 0.02), G.POLY, BLACK, r=0.06)           # cowling
        m.box((-0.159, 0.68, cz0 + 0.03), (0.159, 0.70, cz1 - 0.01), G.POLY, 0xC8CACC)       # trim stripe
        m.box((-0.03, 0.62, cz1 - 0.02), (0.03, 0.66, sz + 0.62), G.POLY, BLACK, r=0.01)     # tiller arm
        m.lathe([(0.0, 0.0), (0.0, 0.026), (0.15, 0.028), (0.16, 0.0)], G.RUBBER, 0x3A3A3A, seg=8,
                origin=(0.0, 0.64, sz + 0.6), axis=(0, 0, 1), ref=(0, 1, 0))                   # twist grip
        m.box((-0.03, -0.36, sz - 0.13), (0.03, 0.52, sz - 0.03), G.POLY, BLACK, r=0.012)    # leg, behind the tube
        m.box((-0.09, -0.1, sz - 0.21), (0.09, -0.075, sz + 0.02), G.POLY, BLACK, r=0.02)    # anti-cavitation plate
        m.lathe([(0.0, 0.0), (0.0, 0.045), (0.06, 0.058), (0.26, 0.05), (0.3, 0.0)], G.POLY, BLACK, seg=12,
                origin=(0.0, PROP_AXIS_Y, sz + 0.07), axis=(0, 0, -1), ref=(0, 1, 0))         # lower unit torpedo
        m.box((-0.01, -0.56, sz - 0.16), (0.01, PROP_AXIS_Y - 0.04, sz + 0.02), G.POLY, BLACK)  # skeg
    comp(m, 'motor', motor)
    p = Part(4)
    pz = sz + 0.07 - 0.3 - 0.035
    def prop():
        p.lathe([(0.0, 0.0), (0.0, 0.024), (0.06, 0.024), (0.08, 0.008), (0.085, 0.0)], G.BRIGHT, GREY, seg=10,
                origin=(0.0, PROP_AXIS_Y, pz + 0.045), axis=(0, 0, -1), ref=(0, 1, 0))
        for a in (0, 120, 240):
            fr = G.T(0, PROP_AXIS_Y, pz + 0.01) @ G.Rz(a) @ G.Rx(20)
            p.box((-0.032, 0.016, -0.005), (0.032, 0.112, 0.005), G.BRIGHT, GREY, frame=fr, r=0.012)
    comp(p, 'prop', prop)
    return [out, m, p]


def jon_mask():
    y = 0.40  # [1.1.1] just under the gunwale caps
    left, right = [], []
    for i in range(41):
        t = 0.012 + 0.975 * i / 40
        if jon_station(t)[1] + JON_SKIN[1] > y - 0.03:
            continue
        x = jon_x_at(t, y, JON_SKIN[1]) - 0.003
        z = -JON_L / 2 + 0.06 + (JON_L - 0.06) * t
        left.append((x, z))
        right.append((-x, z))
    return flat_mask(left, right, y)


def flat_mask(left, right, y):
    poly = G.ccw(left + right[::-1])
    P = Part(0)
    P.add([(x, y, z) for x, z in poly], [(0, 1, 0)] * len(poly), [(0.5, 0.5)] * len(poly), [0xFFFFFF] * len(poly),
          G.triangulate(poly))
    return P


# --------------------------------------------------------------------------------------------------------- checks
def _only(comp, *ids):
    c = Comp()
    c.ranges = [r for r in comp.ranges if r[0] in ids]
    return c


def check(parts, comp, joins, name):
    """every piece must touch (bounding boxes within 1.5 mm, then a vertex-to-triangle-free AABB proxy) another piece of
    the same assembly, and the assembly must be one connected group. Moving parts join the hull through `joins`."""
    boxes = []
    by_id = {q.id: q for q in parts}
    for pid, label, a, b in comp.ranges:
        V = np.array(by_id[pid].v[a:b], float)[:, :3]
        boxes.append((pid, label, V.min(0), V.max(0), V))
    n = len(boxes)
    adj = [set() for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            pi, pj = boxes[i][0], boxes[j][0]
            if pi != pj and (pi, pj) not in joins and (pj, pi) not in joins:
                continue
            lo = np.maximum(boxes[i][2], boxes[j][2])
            hi = np.minimum(boxes[i][3], boxes[j][3])
            if np.all(lo <= hi + 0.0015):
                # refine: some vertex of one lies inside the other's box (grown 1.5 mm) - catches L-shaped bounds
                a, b = boxes[i][4], boxes[j][4]
                ga, gb = boxes[j][2] - 0.0015, boxes[j][3] + 0.0015
                ha, hb = boxes[i][2] - 0.0015, boxes[i][3] + 0.0015
                if np.any(np.all((a >= ga) & (a <= gb), 1)) or np.any(np.all((b >= ha) & (b <= hb), 1)):
                    adj[i].add(j)
                    adj[j].add(i)
    seen = {0}
    todo = [0]
    while todo:
        i = todo.pop()
        for j in adj[i]:
            if j not in seen:
                seen.add(j)
                todo.append(j)
    lonely = [boxes[i][1] for i in range(n) if i not in seen]
    print(f'{name}: {n} pieces, connected {len(seen)}/{n}' + (f'  NOT CONNECTED: {lonely}' if lonely else ''))
    return not lonely


if __name__ == '__main__':
    import fheq
    o = sys.argv[1] if len(sys.argv) > 1 else '.'
    ok = True
    for lod in ('close', 'field'):
        for name, fn in (('rowboat', rowboat), ('jon_boat', jon_boat)):
            comp = Comp()
            parts = fn(lod, comp)
            fheq.write(f'{o}/{name}_{lod}.fheq', [q.to_fheq() for q in parts])
            print(name, lod, [(q.id, q.tris()) for q in parts])
            if name == 'rowboat':
                ok &= check(parts, _only(comp, 0), set(), f'{name}_{lod} hull')
            else:
                ok &= check(parts, _only(comp, 0, 3, 4), {(0, 3), (3, 4)}, f'{name}_{lod}')
    fheq.write(f'{o}/rowboat_mask.fheq', [row_mask().to_fheq()])
    fheq.write(f'{o}/jon_boat_mask.fheq', [jon_mask().to_fheq()])
    if not ok:
        sys.exit(1)
