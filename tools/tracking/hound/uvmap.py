"""[hound3] UV atlas: faces are grouped into disk-shaped charts, each chart is flattened with least-squares conformal
maps (LSCM), scaled to a common texel density, rotated to its tightest box and shelf-packed into [0,1]^2.
Vertices on chart borders are duplicated (the caller keeps their normals shared, so no shading seam)."""
import math
import numpy as np
from scipy.sparse import coo_matrix
from scipy.sparse.linalg import lsqr


def components(F, faces):
    """connected components (by shared edge) of a face subset"""
    faces = list(faces)
    fset = set(faces)
    emap = {}
    for f in faces:
        for i in range(3):
            a, b = F[f][i], F[f][(i + 1) % 3]
            emap.setdefault((min(a, b), max(a, b)), []).append(f)
    seen = set(); out = []
    for f in faces:
        if f in seen:
            continue
        comp = []; stack = [f]; seen.add(f)
        while stack:
            g = stack.pop(); comp.append(g)
            for i in range(3):
                a, b = F[g][i], F[g][(i + 1) % 3]
                for h in emap[(min(a, b), max(a, b))]:
                    if h not in seen and h in fset:
                        seen.add(h); stack.append(h)
        out.append(comp)
    return out


def lscm(V, F, faces):
    verts = sorted(set(int(v) for f in faces for v in F[f]))
    idx = {v: i for i, v in enumerate(verts)}
    n = len(verts)
    if n < 3:
        return verts, np.zeros((n, 2))
    rows, cols, vals = [], [], []
    r = 0
    for f in faces:
        a, b, c = (V[F[f][k]] for k in range(3))
        e1 = b - a; e2 = c - a
        nrm = np.cross(e1, e2); A = np.linalg.norm(nrm)
        if A < 1e-14:
            continue
        x = e1 / np.linalg.norm(e1); y = np.cross(nrm / A, x)
        p = [np.zeros(2), np.array([e1 @ x, e1 @ y]), np.array([e2 @ x, e2 @ y])]
        s = 1.0 / math.sqrt(A)
        # W = [p2-p1, p0-p2, p1-p0] rotated: complex coefficients
        ws = [p[2] - p[1], p[0] - p[2], p[1] - p[0]]
        for k in range(3):
            j = idx[int(F[f][k])]
            wr, wi = ws[k][0] * s, ws[k][1] * s
            # real row r: sum(wr*u - wi*v), imag row r+1: sum(wi*u + wr*v)
            rows += [r, r, r + 1, r + 1]; cols += [j, n + j, j, n + j]; vals += [wr, -wi, wi, wr]
        r += 2
    M = coo_matrix((vals, (rows, cols)), shape=(r, 2 * n)).tocsc()
    # pin the two vertices farthest apart
    P = V[verts]
    i0 = int(np.argmax(np.linalg.norm(P - P.mean(0), axis=1)))
    i1 = int(np.argmax(np.linalg.norm(P - P[i0], axis=1)))
    d = np.linalg.norm(P[i1] - P[i0])
    pinned = {i0: (0.0, 0.0), i1: (d, 0.0)}
    free = [k for k in range(2 * n) if (k % n) not in pinned]
    rhs = np.zeros(r)
    for i, (u, v) in pinned.items():
        rhs -= M[:, i].toarray().ravel() * u + M[:, n + i].toarray().ravel() * v
    sol = lsqr(M[:, free], rhs, atol=1e-12, btol=1e-12, iter_lim=20000)[0]
    x = np.zeros(2 * n)
    x[free] = sol
    for i, (u, v) in pinned.items():
        x[i] = u; x[n + i] = v
    return verts, np.stack([x[:n], x[n:]], 1)


def chart_area_scale(V, F, faces, verts, uv):
    idx = {v: i for i, v in enumerate(verts)}
    a3 = a2 = 0.0
    for f in faces:
        p = V[F[f]]; a3 += 0.5 * np.linalg.norm(np.cross(p[1] - p[0], p[2] - p[0]))
        q = uv[[idx[int(v)] for v in F[f]]]
        a2 += 0.5 * abs((q[1, 0] - q[0, 0]) * (q[2, 1] - q[0, 1]) - (q[2, 0] - q[0, 0]) * (q[1, 1] - q[0, 1]))
    return math.sqrt(a3 / max(a2, 1e-20))


def flips(F, faces, verts, uv):
    idx = {v: i for i, v in enumerate(verts)}
    s = []
    for f in faces:
        q = uv[[idx[int(v)] for v in F[f]]]
        s.append((q[1, 0] - q[0, 0]) * (q[2, 1] - q[0, 1]) - (q[2, 0] - q[0, 0]) * (q[1, 1] - q[0, 1]))
    s = np.array(s)
    return s


def unwrap(V, F, labels, res=1024, pad=6, density=None):
    """labels: chart label per face. Returns (Vout index map, F_uv (m,3) into new vertex list, UV (k,2), src (k,))
    where src[k] is the original vertex of new vertex k."""
    F = np.asarray(F)
    charts = []
    for lab in sorted(set(labels.tolist())):
        fs = np.nonzero(labels == lab)[0]
        for comp in components(F, fs):
            if len(comp) < 2:
                verts = sorted(set(int(v) for v in F[comp[0]]))
                p = V[verts]; e1 = p[1] - p[0]; e1 /= np.linalg.norm(e1) + 1e-12
                nrm = np.cross(p[1] - p[0], p[2] - p[0]); e2 = np.cross(nrm, e1); e2 /= np.linalg.norm(e2) + 1e-12
                uv = np.stack([(p - p[0]) @ e1, (p - p[0]) @ e2], 1)
            else:
                verts, uv = lscm(V, F, comp)
                sg = flips(F, comp, verts, uv)
                if (sg < 0).sum() > len(sg) / 2:
                    uv[:, 1] = -uv[:, 1]
            sc = chart_area_scale(V, F, comp, verts, uv)
            uv = uv * sc
            # tightest box: try rotations
            best = None
            c = uv.mean(0)
            for ang in np.linspace(0, math.pi / 2, 19):
                R = np.array([[math.cos(ang), -math.sin(ang)], [math.sin(ang), math.cos(ang)]])
                q = (uv - c) @ R.T
                ext = q.max(0) - q.min(0)
                if best is None or ext[0] * ext[1] < best[0]:
                    best = (ext[0] * ext[1], q - q.min(0))
            uv = best[1]
            if uv[:, 0].max() < uv[:, 1].max():
                uv = uv[:, ::-1].copy()
                uv[:, 1] = uv[:, 1].max() - uv[:, 1]       # keep orientation (mirror would flip winding in uv: harmless)
            charts.append(dict(faces=comp, verts=verts, uv=uv, lab=lab))
    # shelf packing at a trial density, shrinking until it fits
    tot = sum((c['uv'][:, 0].max() * c['uv'][:, 1].max()) for c in charts)
    scale = 0.86 / math.sqrt(tot)
    for attempt in range(60):
        px = pad / res
        order = sorted(range(len(charts)), key=lambda i: -charts[i]['uv'][:, 1].max())
        x = y = px; shelf = 0.0; pos = {}
        okk = True
        for i in order:
            w = charts[i]['uv'][:, 0].max() * scale; h = charts[i]['uv'][:, 1].max() * scale
            if x + w + px > 1.0:
                x = px; y += shelf + px; shelf = 0.0
            if y + h + px > 1.0 or w + 2 * px > 1.0:
                okk = False
                break
            pos[i] = (x, y); x += w + px; shelf = max(shelf, h)
        if okk:
            break
        scale *= 0.97
    print('  uv charts', len(charts), 'texel density %.1f px/m' % (scale * res))
    src = []; UV = []; Fuv = np.zeros_like(F)
    for i, c in enumerate(charts):
        base = len(src)
        loc = {v: base + k for k, v in enumerate(c['verts'])}
        src += c['verts']
        UV.append(c['uv'] * scale + np.array(pos[i]))
        for f in c['faces']:
            Fuv[f] = [loc[int(v)] for v in F[f]]
    return Fuv, np.vstack(UV), np.array(src), scale * res
