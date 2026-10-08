"""[gunsmith] Rounded-edge overlay for hard, convex edges of .fheq meshes.

The supplied gun meshes are flat-shaded where panels meet, so every receiver, stock and rail edge is a razor-sharp
crease that reads as "boxy" under Minecraft's directional lighting. Re-topologising the meshes would risk the
accepted shapes, so instead each long, convex, hard edge gets a narrow two-quad fillet laid over it:

    A1 (on face 1, w from the edge) -> C (edge, lifted along the averaged normal) -> A2 (on face 2)

with vertex normals n1 -> n_avg -> n2, so the band shades like a machined radius / sanded wood round-over and catches
the light along the edge. The silhouette grows by at most LIFT (0.05 mm); nothing else in the mesh moves.
Strips copy UV and tint from the triangle they sit on (metal edges get a slight bright "worn edge" lift).
"""
import numpy as np

W_MAX = 0.0011        # fillet half-width (m) on large panels
W_MIN = 0.00025
MIN_LEN = 0.004       # skip edges shorter than 4 mm (detail bits, screws)
COS_MIN = np.cos(np.radians(150))   # skip knife-edge fins (nearly opposite normals)
COS_MAX = np.cos(np.radians(28))    # skip shallow creases (already reads smooth)
LIFT = 0.00005
SURF = 0.00002


def _cells(f):
    return np.clip((f[:, 6] * 4).astype(int), 0, 3) + 4 * np.clip((f[:, 7] * 4).astype(int), 0, 3)


METAL_CELLS = {0, 1, 5, 6, 9, 11}   # blued, parkerized, bright, brass, anodized, case-hardened


def bevel_part(p, w_max=W_MAX, min_len=MIN_LEN):
    f = p['f']
    idx = p['idx']
    c = p['c']
    P = f[:, :3]
    key = np.round(P * 2e4).astype(np.int64)
    _, wid = np.unique(key, axis=0, return_inverse=True)
    wid = wid.ravel()
    T = P[idx]
    fn = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    area2 = np.linalg.norm(fn, axis=1)
    fn = fn / np.maximum(area2, 1e-15)[:, None]
    edges = {}
    for t in range(len(idx)):
        if area2[t] < 1e-12:
            continue
        for e in range(3):
            i, j = idx[t, e], idx[t, (e + 1) % 3]
            a, b = wid[i], wid[j]
            k = (a, b) if a < b else (b, a)
            edges.setdefault(k, []).append((t, e))
    newv = []
    newc = []
    newt = []
    cells = _cells(f)
    for k, lst in edges.items():
        if len(lst) != 2:
            continue
        (t1, e1), (t2, e2) = lst
        n1, n2 = fn[t1], fn[t2]
        cs = float(n1 @ n2)
        if cs > COS_MAX or cs < COS_MIN:
            continue
        a1, b1 = idx[t1, e1], idx[t1, (e1 + 1) % 3]
        o1 = idx[t1, (e1 + 2) % 3]
        # endpoints of the edge as seen from t2 (match by weld id)
        i2 = [idx[t2, (e2 + q) % 3] for q in range(3)]
        a2 = i2[0] if wid[i2[0]] == wid[a1] else i2[1]
        b2 = i2[1] if a2 == i2[0] else i2[0]
        o2 = i2[2]
        pa, pb = P[a1], P[b1]
        L = np.linalg.norm(pb - pa)
        if L < min_len:
            continue
        # hard edge only: the triangles do not share vertex normals across it
        if f[a1, 3:6] @ f[a2, 3:6] > 0.97 and f[b1, 3:6] @ f[b2, 3:6] > 0.97:
            continue
        # convex: t2's far vertex is behind t1's plane
        if n1 @ (P[o2] - pa) > -1e-7:
            continue
        ed = (pb - pa) / L
        d1 = np.cross(n1, ed)
        if d1 @ (P[o1] - pa) < 0:
            d1 = -d1
        d2 = np.cross(n2, ed)
        if d2 @ (P[o2] - pa) < 0:
            d2 = -d2
        h1 = abs(d1 @ (P[o1] - pa))
        h2 = abs(d2 @ (P[o2] - pa))
        w = min(w_max, 0.3 * h1, 0.3 * h2, 0.12 * L)
        if w < W_MIN:
            continue
        nav = n1 + n2
        nav /= np.linalg.norm(nav)
        metal = cells[a1] in METAL_CELLS
        for (ta, tb, n, d) in ((a1, b1, n1, d1), (a2, b2, n2, d2)):
            base = len(newv)
            for (vi, pt) in ((ta, pa), (tb, pb)):
                q = f[vi].copy()
                q[:3] = P[vi] + d * w + n * SURF
                q[3:6] = n
                newv.append(q)
                newc.append(c[vi])
            for (vi, pt) in ((ta, pa), (tb, pb)):
                q = f[vi].copy()
                q[:3] = P[vi] + nav * LIFT
                q[3:6] = nav
                newv.append(q)
                cc = c[vi]
                if metal:
                    rgb = np.array([(cc >> 16) & 255, (cc >> 8) & 255, cc & 255], float) * 1.16 + 6
                    rgb = np.clip(np.round(rgb), 0, 255).astype(int)
                    cc = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]
                newc.append(cc)
            # quad base+0 (A at a), +1 (A at b), +2 (C at a), +3 (C at b)
            newt.append((base, base + 1, base + 3))
            newt.append((base, base + 3, base + 2))
    if not newv:
        return p, 0
    q = dict(p)
    q['f'] = np.vstack([f, np.array(newv)])
    q['c'] = np.concatenate([c, np.array(newc, dtype=np.int64)])
    q['idx'] = np.vstack([idx, np.array(newt, dtype=np.int64) + len(f)])
    return q, len(newt)
