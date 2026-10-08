"""[meshes] Quadric-error half-edge-collapse decimation for the skinned wildlife meshes (mid level of detail).

Works on the welded topology from smooth.py and keeps the coat mapping exact: a vertex always collapses onto a
neighbour that already exists (half-edge collapse), so no UV, normal or skin weight is ever invented. UV seam and
open-boundary vertices only slide along their seam / boundary onto a vertex of the same seam; collapses that would
flip or crush a triangle, break manifoldness (link condition) or move a vertex onto a differently weighted bone
region are rejected or penalised.
"""
import heapq, math
from collections import defaultdict
import numpy as np


def plane_quadrics(Q, Fw):
    n = np.cross(Q[Fw[:, 1]] - Q[Fw[:, 0]], Q[Fw[:, 2]] - Q[Fw[:, 0]])
    area = np.linalg.norm(n, axis=1)
    nu = n / np.maximum(area[:, None], 1e-30)
    d = -np.einsum('ij,ij->i', nu, Q[Fw[:, 0]])
    p = np.c_[nu, d]                                  # (F,4)
    K = np.einsum('fi,fj->fij', p, p) * (0.5 * area)[:, None, None]
    QV = np.zeros((len(Q), 4, 4))
    for k in range(3):
        np.add.at(QV, Fw[:, k], K)
    return QV


def decimate(Fc, Fw, Q, Wd, target, crisp=None, log=None):
    """Fc/Fw: (T,3) corner / welded triangles (same order). Returns reduced (Fc, Fw)."""
    Fc = [list(map(int, f)) for f in Fc]
    Fw = [list(map(int, f)) for f in Fw]
    nF = len(Fw)
    alive = [True] * nF
    vf = defaultdict(set)
    for t, f in enumerate(Fw):
        for v in f:
            vf[v].add(t)
    QV = plane_quadrics(Q, np.array(Fw))
    # edge -> faces
    ef = defaultdict(list)
    for t, f in enumerate(Fw):
        for k in range(3):
            a, b = f[k], f[(k + 1) % 3]
            ef[(min(a, b), max(a, b))].append(t)
    elen = np.array([np.linalg.norm(Q[a] - Q[b]) for a, b in ef])
    scale = float(np.median(elen)) if len(elen) else 1.0

    def corner_of(t, v):
        f = Fw[t]
        return Fc[t][f.index(v)]

    def charts(v):
        return {corner_of(t, v) for t in vf[v]}

    # constraint quadrics: seam and boundary edges get a perpendicular plane so they stay straight and in place
    seam_v = set()
    bnd_v = set()
    for (a, b), ts in ef.items():
        seam = False
        if len(ts) == 1:
            bnd_v.update((a, b)); seam = True
        elif len(ts) == 2:
            t0, t1 = ts
            if corner_of(t0, a) != corner_of(t1, a) or corner_of(t0, b) != corner_of(t1, b):
                seam = True; seam_v.update((a, b))
        else:
            bnd_v.update((a, b)); seam = True
        if seam:
            e = Q[b] - Q[a]
            for t in ts:
                f = Fw[t]
                n = np.cross(Q[f[1]] - Q[f[0]], Q[f[2]] - Q[f[0]])
                pn = np.cross(e, n)
                L = np.linalg.norm(pn)
                if L < 1e-20:
                    continue
                pn /= L
                p = np.r_[pn, -pn @ Q[a]]
                K = np.outer(p, p) * 8.0 * float(e @ e)
                QV[a] += K; QV[b] += K
    stamp = defaultdict(int)
    heap = []

    def cost(u, v):
        q = QV[u] + QV[v]
        x = np.r_[Q[v], 1.0]
        c = float(x @ q @ x)
        dw = float(np.abs(Wd[u] - Wd[v]).sum())
        c += 0.02 * dw * dw * scale * scale
        if crisp is not None and crisp[u]:
            c *= 3.0
        c += 1e-4 * float((Q[u] - Q[v]) @ (Q[u] - Q[v]))
        return c

    def push(u, v):
        heapq.heappush(heap, (cost(u, v), u, v, stamp[u], stamp[v]))

    def neighbours(v):
        s = set()
        for t in vf[v]:
            s.update(Fw[t])
        s.discard(v)
        return s

    for (a, b) in ef:
        push(a, b)
        push(b, a)

    def try_collapse(u, v):
        shared = vf[u] & vf[v]
        if not shared:
            return 0
        # link condition
        common = neighbours(u) & neighbours(v)
        opp = set()
        for t in shared:
            opp.update(x for x in Fw[t] if x != u and x != v)
        if common != opp:
            return 0
        if len(shared) not in (1, 2):
            return 0
        # seams / boundaries: u may only slide along its seam onto the same seam
        cu_map = {}
        u_on_seam = u in seam_v or u in bnd_v
        if u_on_seam:
            if not (v in seam_v or v in bnd_v):
                return 0
            edge_is_seam = len(shared) == 1 or len({corner_of(t, u) for t in shared}) > 1 or len({corner_of(t, v) for t in shared}) > 1
            if not edge_is_seam:
                return 0
            if u in bnd_v and len(shared) != 1:
                return 0
        for t in shared:
            cu, cv = corner_of(t, u), corner_of(t, v)
            if cu in cu_map and cu_map[cu] != cv:
                return 0
            cu_map[cu] = cv
        if charts(u) - set(cu_map):
            return 0
        # geometry: no flipped / crushed triangles
        pv = Q[v]
        for t in vf[u] - shared:
            f = Fw[t]
            a, b, c = (Q[x] for x in f)
            n0 = np.cross(b - a, c - a)
            pts = [pv if x == u else Q[x] for x in f]
            n1 = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            l0, l1 = np.linalg.norm(n0), np.linalg.norm(n1)
            if l1 < 1e-12 or l0 < 1e-14:
                return 0
            if float(n0 @ n1) / (l0 * l1) < 0.35:
                return 0
            # minimum angle ~ 4 deg
            e = [np.linalg.norm(pts[(i + 1) % 3] - pts[i]) for i in range(3)]
            if l1 / max(e) ** 2 < 0.07:
                return 0
        # apply
        removed = len(shared)
        for t in shared:
            alive[t] = False
            for x in Fw[t]:
                vf[x].discard(t)
        for t in list(vf[u]):
            f = Fw[t]
            k = f.index(u)
            f[k] = v
            Fc[t][k] = cu_map[Fc[t][k]]
            vf[v].add(t)
        vf[u].clear()
        QV[v] += QV[u]
        if u in seam_v:
            seam_v.add(v)
        stamp[u] += 1
        stamp[v] += 1
        for w in neighbours(v):
            stamp[w] += 1
        for w in neighbours(v):
            push(v, w)
            push(w, v)
            for z in neighbours(w):
                if z != v:
                    push(w, z)
        return removed

    faces = nF
    rejected = 0
    while faces > target and heap:
        c, u, v, su, sv = heapq.heappop(heap)
        if su != stamp[u] or sv != stamp[v] or not vf[u] or not vf[v]:
            continue
        r = try_collapse(u, v)
        if r:
            faces -= r
        else:
            rejected += 1
    keep = [t for t in range(nF) if alive[t]]
    if log:
        log('decimate %d -> %d faces (%d rejected)' % (nF, len(keep), rejected))
    return np.array([Fc[t] for t in keep], np.int64), np.array([Fw[t] for t in keep], np.int64)
