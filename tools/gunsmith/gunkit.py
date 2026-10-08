"""[gunsmith] Small procedural modelling kit that writes .fheq parts (smooth-shaded lathes, knurling, rounded boxes and
extrusions, lofts) for the firearm atlas (firearms_v2.png, 4x4 material cells).

Coordinates are weapon / optic model space in metres (Minecraft block = 1 m); -z is the muzzle direction.
"""
import numpy as np

# atlas cells (col, row)
BLUED, PARKER, WALNUT, CHECKER = (0, 0), (1, 0), (2, 0), (3, 0)
POLY, BRIGHT, BRASS, RUBBER = (0, 1), (1, 1), (2, 1), (3, 1)
TAN, ANOD, ORANGE, CASE = (0, 2), (1, 2), (2, 2), (3, 2)
STIPPLE, GLOW, WHITE, BIRCH = (0, 3), (1, 3), (2, 3), (3, 3)


def cell_uv(cell, u, v):
    u = np.clip(u, 0.0, 1.0)
    v = np.clip(v, 0.0, 1.0)
    return cell[0] * 0.25 + 0.006 + u * 0.238, cell[1] * 0.25 + 0.006 + v * 0.238


def unit(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.maximum(n, 1e-12)


class Part:
    def __init__(self, pid):
        self.id = pid
        self.v = []      # rows: x y z nx ny nz u v
        self.c = []
        self.t = []

    def tris(self):
        return len(self.t)

    # --------------------------------------------------------------------------------------- raw
    def add(self, P, N, UV, cols, tris):
        base = len(self.v)
        for p, n, uv, c in zip(P, N, UV, cols):
            n = unit(n)
            self.v.append([p[0], p[1], p[2], n[0], n[1], n[2], uv[0], uv[1]])
            self.c.append(int(c) & 0xFFFFFF)
        for a, b, c in tris:
            self.t.append((a + base, b + base, c + base))

    def quad(self, p0, p1, p2, p3, cell, tint, n=None, uv=((0, 0), (1, 0), (1, 1), (0, 1))):
        P = [np.asarray(p, float) for p in (p0, p1, p2, p3)]
        if n is None:
            n = np.cross(P[1] - P[0], P[2] - P[0])
            if np.linalg.norm(n) < 1e-14:
                n = np.cross(P[2] - P[0], P[3] - P[0])
        UV = [cell_uv(cell, a, b) for a, b in uv]
        self.add(P, [n] * 4, UV, [tint] * 4, [(0, 1, 2), (0, 2, 3)])

    def merge(self, other):
        base = len(self.v)
        self.v += other.v
        self.c += other.c
        self.t += [(a + base, b + base, c + base) for a, b, c in other.t]

    def transformed(self, M):
        """Copy with a 4x4 affine applied (rotation part to normals)."""
        q = Part(self.id)
        V = np.array(self.v, float)
        P = np.c_[V[:, :3], np.ones(len(V))] @ np.asarray(M).T
        N = V[:, 3:6] @ np.asarray(M)[:3, :3].T
        q.v = np.c_[P[:, :3], unit(N), V[:, 6:8]].tolist()
        q.c = list(self.c)
        q.t = list(self.t)
        return q

    def to_fheq(self):
        return {'id': self.id, 'f': np.array(self.v, float), 'c': np.array(self.c, np.int64), 'idx': np.array(self.t, np.int64)}

    # --------------------------------------------------------------------------------------- lathe
    def lathe(self, profile, cell, tint, seg=48, origin=(0, 0, 0), axis=(0, 0, 1), ref=(0, 1, 0), smooth_deg=40.0, knurl=None,
              uv_len=None, tints=None, a0=0.0, a1=2 * np.pi):
        """Revolve profile [(h, r), ...] (h along axis, r radius) around the axis through origin.
        knurl: (count, depth, h0, h1) modulates r by a ridge pattern between h0 and h1.
        tints: optional per-profile-segment tint list (len(profile)-1). Hard creases where the profile turns more
        than smooth_deg; otherwise normals are averaged (smooth shading)."""
        o = np.asarray(origin, float)
        ax = unit(axis)
        e1 = unit(np.asarray(ref, float) - ax * (np.dot(ref, ax)))
        e2 = np.cross(ax, e1)
        prof = [(float(h), float(r)) for h, r in profile]
        nseg = len(prof) - 1
        # per-segment 2D normal (dh, dr) -> outward normal in (h, r) plane = (-dr, dh) normalised
        segn = []
        for i in range(nseg):
            dh = prof[i + 1][0] - prof[i][0]
            dr = prof[i + 1][1] - prof[i][1]
            L = np.hypot(dh, dr)
            if L < 1e-12:
                segn.append((0.0, 1.0))
                continue
            nh, nr = -dr / L, dh / L
            segn.append((nh, nr))
        lens = [np.hypot(prof[i + 1][0] - prof[i][0], prof[i + 1][1] - prof[i][1]) for i in range(nseg)]
        total = sum(lens) if uv_len is None else uv_len
        acc = np.concatenate([[0.0], np.cumsum(lens)])
        cosl = np.cos(np.radians(smooth_deg))
        full = abs((a1 - a0) - 2 * np.pi) < 1e-9
        for i in range(nseg):
            if lens[i] < 1e-12:
                continue
            # endpoint normals: average with neighbour segment if the turn is gentle
            def endn(j, side):
                n0 = np.array(segn[i])
                k = i - 1 if side == 0 else i + 1
                if 0 <= k < nseg and lens[k] > 1e-12:
                    n1 = np.array(segn[k])
                    if n0 @ n1 >= cosl:
                        m = n0 + n1
                        return m / np.linalg.norm(m)
                return n0
            nA, nB = endn(i, 0), endn(i, 1)
            tint_i = tints[i] if tints is not None else tint
            P, N, UV, C, T = [], [], [], [], []
            for j in range(seg + 1):
                a = a0 + (a1 - a0) * j / seg
                ca, sa = np.cos(a), np.sin(a)
                radial = e1 * ca + e2 * sa
                tang = -e1 * sa + e2 * ca
                for (h, r), n2, s in ((prof[i], nA, acc[i]), (prof[i + 1], nB, acc[i + 1])):
                    rr = r
                    nrm = ax * n2[0] + radial * n2[1]
                    if knurl is not None and knurl[2] <= h <= knurl[3] and r > 1e-6:
                        cnt, dep = knurl[0], knurl[1]
                        ph = (a * cnt / (2 * np.pi)) % 1.0
                        tri = abs(ph - 0.5) * 2               # 1 at ridge edges, 0 mid-groove
                        rr = r - dep * (1 - tri)
                        slope = (1 if ph < 0.5 else -1) * dep * 2 * cnt / (2 * np.pi) / max(r, 1e-6)
                        nrm = unit(nrm + tang * slope * 0.6)
                    P.append(o + ax * h + radial * rr)
                    N.append(nrm)
                    UV.append(cell_uv(cell, s / total if total > 0 else 0, j / seg))
                    C.append(tint_i)
            for j in range(seg):
                a = 2 * j
                T.append((a, a + 2, a + 3))
                T.append((a, a + 3, a + 1))
            self.add(P, N, UV, C, T)

    # --------------------------------------------------------------------------------------- extrusion
    def extrude(self, section, z0, z1, cell, tint, frame=None, smooth_deg=35.0, caps=True, uv_scale=0.15, cap_tint=None):
        """Extrude a closed CCW section [(x, y)] from z0 to z1 (local), frame = 4x4 local->model (default identity).
        Smooth normals around the section where the turn is gentle, hard caps."""
        S = np.asarray(section, float)
        n = len(S)
        M = np.eye(4) if frame is None else np.asarray(frame, float)
        R = M[:3, :3]
        def tp(x, y, z):
            return (M @ np.array([x, y, z, 1.0]))[:3]
        cosl = np.cos(np.radians(smooth_deg))
        edges_n = []
        for i in range(n):
            a, b = S[i], S[(i + 1) % n]
            d = b - a
            nn = np.array([d[1], -d[0]])
            nn /= max(np.linalg.norm(nn), 1e-12)
            edges_n.append(nn)
        # orientation: make normals point outward (polygon CCW -> (dy, -dx) is outward)
        area = 0.5 * np.sum(S[:, 0] * np.roll(S[:, 1], -1) - np.roll(S[:, 0], -1) * S[:, 1])
        if area < 0:
            edges_n = [-e for e in edges_n]
        per = np.concatenate([[0], np.cumsum([np.linalg.norm(S[(i + 1) % n] - S[i]) for i in range(n)])])
        for i in range(n):
            a, b = S[i], S[(i + 1) % n]
            n0 = edges_n[i]
            def vn(k):
                m = edges_n[k % n]
                return unit(n0 + m) if n0 @ m >= cosl else n0
            na, nb = vn(i - 1), vn(i + 1)
            P = [tp(a[0], a[1], z0), tp(b[0], b[1], z0), tp(b[0], b[1], z1), tp(a[0], a[1], z1)]
            N = [R @ np.array([na[0], na[1], 0]), R @ np.array([nb[0], nb[1], 0]), R @ np.array([nb[0], nb[1], 0]), R @ np.array([na[0], na[1], 0])]
            u0, u1 = per[i] * uv_scale * 4, per[i + 1] * uv_scale * 4
            UV = [cell_uv(cell, 0.5 + z0 / 0.5, u0), cell_uv(cell, 0.5 + z0 / 0.5, u1), cell_uv(cell, 0.5 + z1 / 0.5, u1), cell_uv(cell, 0.5 + z1 / 0.5, u0)]
            self.add(P, N, UV, [tint] * 4, [(0, 1, 2), (0, 2, 3)])
        if caps:
            cx, cy = S.mean(0)
            for z, d in ((z0, -1), (z1, 1)):
                P = [tp(cx, cy, z)] + [tp(p[0], p[1], z) for p in S]
                N = [R @ np.array([0, 0, d])] * (n + 1)
                UV = [cell_uv(cell, 0.5 + (p[0] - cx) * 4, 0.5 + (p[1] - cy) * 4) for p in [(cx, cy)] + list(S)]
                T = [(0, 1 + i, 1 + (i + 1) % n) for i in range(n)]
                self.add(P, N, UV, [cap_tint or tint] * (n + 1), T)

    def loft(self, sections, cell, tint, uv_along=0.3, closed=True, smooth=True, caps=True):
        """Skin a list of 3D section rings (each (k,3) array, same k, consistent order); smooth normals."""
        S = [np.asarray(s, float) for s in sections]
        m, k = len(S), len(S[0])
        G = np.stack(S)                                   # m, k, 3
        # normals from finite differences
        dk = np.roll(G, -1, 1) - np.roll(G, 1, 1)
        dm = np.zeros_like(G)
        dm[1:-1] = G[2:] - G[:-2]
        dm[0] = G[1] - G[0]
        dm[-1] = G[-1] - G[-2]
        N = unit(np.cross(dk, dm))
        cen = G.mean(1, keepdims=True)
        flip = np.sum(N * (G - cen), -1).mean() < 0
        if flip:
            N = -N
        alen = np.concatenate([[0], np.cumsum(np.linalg.norm(cen[1:, 0] - cen[:-1, 0], axis=1))])
        P, NN, UV, C, T = [], [], [], [], []
        for i in range(m):
            per = np.concatenate([[0], np.cumsum(np.linalg.norm(np.roll(G[i], -1, 0) - G[i], axis=1))])
            for j in range(k + 1):
                jj = j % k
                P.append(G[i, jj])
                NN.append(N[i, jj])
                UV.append(cell_uv(cell, alen[i] / uv_along, per[j] / max(per[-1], 1e-9)))
                C.append(tint)
        for i in range(m - 1):
            for j in range(k):
                a = i * (k + 1) + j
                b = a + k + 1
                T.append((a, b, b + 1))
                T.append((a, b + 1, a + 1))
        self.add(P, NN, UV, C, T)
        if caps:
            for i, d in ((0, -1), (m - 1, 1)):
                ring = G[i]
                c0 = ring.mean(0)
                nrm = unit(np.cross(ring[1] - ring[0], ring[k // 3] - ring[0]))
                if np.dot(nrm, cen[min(i + d, m - 1) if d < 0 else max(i - 1, 0), 0] - c0) > 0:
                    nrm = -nrm
                PP = [c0] + list(ring)
                UV2 = [cell_uv(cell, 0.5, 0.5)] + [cell_uv(cell, 0.5 + (p - c0)[0] * 4, 0.5 + (p - c0)[1] * 4) for p in ring]
                self.add(PP, [nrm] * (k + 1), UV2, [tint] * (k + 1), [(0, 1 + j, 1 + (j + 1) % k) for j in range(k)])

    def box(self, lo, hi, cell, tint, frame=None, r=0.0, seg=3):
        """Box (optionally with rounded vertical edges of radius r around local z) as an extrusion along z."""
        x0, y0, z0 = lo
        x1, y1, z1 = hi
        self.extrude(rounded_rect(x0, y0, x1, y1, r, seg), z0, z1, cell, tint, frame=frame)


def rounded_rect(x0, y0, x1, y1, r, seg=3):
    r = min(r, (x1 - x0) / 2 - 1e-6, (y1 - y0) / 2 - 1e-6)
    if r <= 1e-6:
        return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    pts = []
    for cx, cy, a0 in ((x1 - r, y0 + r, -np.pi / 2), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, np.pi / 2), (x0 + r, y0 + r, np.pi)):
        for i in range(seg + 1):
            a = a0 + (np.pi / 2) * i / seg
            pts.append((cx + r * np.cos(a), cy + r * np.sin(a)))
    return pts


def superellipse(cx, cy, ax, ay, n=4.0, k=24):
    pts = []
    for i in range(k):
        t = 2 * np.pi * i / k
        c, s = np.cos(t), np.sin(t)
        pts.append((cx + ax * np.sign(c) * abs(c) ** (2 / n), cy + ay * np.sign(s) * abs(s) ** (2 / n)))
    return pts


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def Rx(deg):
    t = np.radians(deg)
    c, s = np.cos(t), np.sin(t)
    m = np.eye(4)
    m[1:3, 1:3] = [[c, -s], [s, c]]
    return m


def Ry(deg):
    t = np.radians(deg)
    c, s = np.cos(t), np.sin(t)
    m = np.eye(4)
    m[0, 0] = c
    m[0, 2] = s
    m[2, 0] = -s
    m[2, 2] = c
    return m


def Rz(deg):
    t = np.radians(deg)
    c, s = np.cos(t), np.sin(t)
    m = np.eye(4)
    m[0:2, 0:2] = [[c, -s], [s, c]]
    return m


# ------------------------------------------------------------------------------------------------ rounded extrusion
def polygon_area(S):
    S = np.asarray(S, float)
    return 0.5 * np.sum(S[:, 0] * np.roll(S[:, 1], -1) - np.roll(S[:, 0], -1) * S[:, 1])


def ccw(S):
    S = [tuple(p) for p in S]
    return S if polygon_area(S) > 0 else S[::-1]


def vertex_normals_2d(S):
    """Outward per-vertex normals (miter direction) and per-edge normals of a CCW polygon."""
    S = np.asarray(S, float)
    n = len(S)
    e = np.roll(S, -1, 0) - S
    en = np.stack([e[:, 1], -e[:, 0]], 1)
    en /= np.maximum(np.linalg.norm(en, axis=1, keepdims=True), 1e-12)
    vn = en + np.roll(en, 1, 0)
    vn /= np.maximum(np.linalg.norm(vn, axis=1, keepdims=True), 1e-12)
    cosh = np.sum(vn * en, 1)
    return vn, en, np.clip(cosh, 0.35, 1.0)


def offset_polygon(S, d):
    """Offset a CCW polygon by d (negative = inwards) along miter normals."""
    S = np.asarray(S, float)
    vn, en, cosh = vertex_normals_2d(S)
    return S + vn * (d / cosh)[:, None]


def triangulate(S):
    """Ear-clipping triangulation of a simple CCW polygon -> list of index triples."""
    S = np.asarray(S, float)
    idx = list(range(len(S)))
    out = []
    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        n = len(idx)
        ear = False
        for k in range(n):
            i0, i1, i2 = idx[(k - 1) % n], idx[k], idx[(k + 1) % n]
            a, b, c = S[i0], S[i1], S[i2]
            if cross(a, b, c) <= 1e-14:
                continue
            inside = False
            for j in idx:
                if j in (i0, i1, i2):
                    continue
                p = S[j]
                if cross(a, b, p) >= 0 and cross(b, c, p) >= 0 and cross(c, a, p) >= 0:
                    inside = True
                    break
            if inside:
                continue
            out.append((i0, i1, i2))
            idx.pop(k)
            ear = True
            break
        if not ear:
            # degenerate remainder: fan it
            for k in range(1, len(idx) - 1):
                out.append((idx[0], idx[k], idx[k + 1]))
            return out
    if len(idx) == 3:
        out.append(tuple(idx))
    return out


def _extrude_round(self, section, z0, z1, r, cell, tint, frame=None, steps=3, smooth_deg=35.0, uv_scale=4.0, caps=True):
    """Extrusion with rounded (quarter-round) edges where the walls meet the caps. section: polygon in local XY
    (any winding, simple); extruded along local z; frame: 4x4 local->model."""
    S = np.asarray(ccw(section), float)
    n = len(S)
    M = np.eye(4) if frame is None else np.asarray(frame, float)
    R = M[:3, :3]
    r = min(r, abs(z1 - z0) / 2 * 0.95)
    vn, en, cosh = vertex_normals_2d(S)
    cosl = np.cos(np.radians(smooth_deg))
    # smooth or hard per vertex: hard corners get split normals
    def tp(p):
        return (M @ np.array([p[0], p[1], p[2], 1.0]))[:3]
    rings = []   # (z, offset, wall weight, cap sign)
    if r > 1e-6:
        for k in range(steps, 0, -1):
            a = np.pi / 2 * k / steps
            rings.append((z0 + r - r * np.sin(a), -r * (1 - np.cos(a)), np.cos(a), -np.sin(a)))
    rings.append((z0 + r, 0.0, 1.0, 0.0))
    rings.append((z1 - r, 0.0, 1.0, 0.0))
    if r > 1e-6:
        for k in range(1, steps + 1):
            a = np.pi / 2 * k / steps
            rings.append((z1 - r + r * np.sin(a), -r * (1 - np.cos(a)), np.cos(a), np.sin(a)))
    per = np.concatenate([[0], np.cumsum(np.linalg.norm(np.roll(S, -1, 0) - S, axis=1))])
    for i in range(n):
        a, b = i, (i + 1) % n
        e_n = en[i]
        # vertex normals: smooth with the neighbour edge if the turn is gentle
        na = vn[a] if en[(i - 1) % n] @ e_n >= cosl else e_n
        nb = vn[b] if en[b] @ e_n >= cosl else e_n
        sa, sb = S[a], S[b]
        P, N, UV, C, T = [], [], [], [], []
        for (z, off, ww, cs) in rings:
            for (s0, nn, vv) in ((sa, na, per[i]), (sb, nb, per[i + 1])):
                cosh_v = max(0.35, float(nn @ e_n)) if nn is not e_n else 1.0
                p2 = s0 + nn * (off / max(cosh_v, 0.35))
                P.append(tp((p2[0], p2[1], z)))
                nrm = np.array([nn[0] * ww, nn[1] * ww, cs])
                N.append(R @ nrm)
                UV.append(cell_uv(cell, 0.5 + (z - (z0 + z1) / 2) * uv_scale, vv * uv_scale * 0.5))
                C.append(tint)
        for k in range(len(rings) - 1):
            q = 2 * k
            T.append((q, q + 1, q + 3))
            T.append((q, q + 3, q + 2))
        self.add(P, N, UV, C, T)
    if caps:
        inner = offset_polygon(S, -r) if r > 1e-6 else S
        tris = triangulate(inner)
        for z, d in ((z0, -1), (z1, 1)):
            P = [tp((p[0], p[1], z)) for p in inner]
            N = [R @ np.array([0, 0, d])] * n
            UV = [cell_uv(cell, 0.5 + p[0] * uv_scale * 0.5, 0.5 + p[1] * uv_scale * 0.5) for p in inner]
            self.add(P, N, UV, [tint] * n, tris)


Part.extrude_round = _extrude_round


def sweep(self, path, section, cell, tint, frame=None, smooth=True, caps=True):
    """Sweep a closed 2D section (local u = binormal, v = path normal) along a 3D polyline path."""
    P0 = [np.asarray(p, float) for p in path]
    m = len(P0)
    secs = []
    up = np.array([1.0, 0, 0])
    for i in range(m):
        t = P0[min(i + 1, m - 1)] - P0[max(i - 1, 0)]
        t = unit(t)
        nrm = unit(np.cross(t, up)) if abs(np.dot(t, up)) < 0.99 else unit(np.cross(t, [0, 1.0, 0]))
        bi = np.cross(nrm, t)
        secs.append(np.array([P0[i] + bi * u + nrm * v for u, v in section]))
    self.loft(secs, cell, tint, caps=caps)


Part.sweep = sweep


def side_frame():
    """Local (x, y, z) -> model (z, y, x): a YZ side profile (given as (z, y) points) extruded across x."""
    m = np.eye(4)
    m[:3, :3] = [[0, 0, 1], [0, 1, 0], [1, 0, 0]]
    return m


def smooth_path(pts, sub=4):
    """Catmull-Rom resample of a polyline (endpoints kept)."""
    P = [np.asarray(p, float) for p in pts]
    out = []
    for i in range(len(P) - 1):
        p0 = P[max(i - 1, 0)]
        p1, p2 = P[i], P[i + 1]
        p3 = P[min(i + 2, len(P) - 1)]
        for k in range(sub):
            t = k / sub
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(P[-1])
    return out
