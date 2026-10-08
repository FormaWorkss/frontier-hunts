"""[hound3] Quadric-error edge-collapse decimation (Garland-Heckbert) for closed meshes, pure python + numpy.
Keeps the mesh manifold (link condition), never flips a face, refuses collapses that make slivers, and adds a small
edge-length term so the result stays evenly tessellated (it is skinned and bent at the joints)."""
import heapq, math
import numpy as np


def decimate(V, F, target, protect=None, length_w=0.02, verbose=True):
    V = [list(map(float, v)) for v in V]
    F = [list(map(int, f)) for f in F]
    nv = len(V)
    alive_f = [True] * len(F)
    vf = [set() for _ in range(nv)]
    for i, f in enumerate(F):
        for v in f:
            vf[v].add(i)
    Q = [[0.0] * 10 for _ in range(nv)]

    def plane(f):
        a, b, c = V[f[0]], V[f[1]], V[f[2]]
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        wx, wy, wz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        nx, ny, nz = uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx
        l = math.sqrt(nx * nx + ny * ny + nz * nz)
        return (nx, ny, nz, l)

    for i, f in enumerate(F):
        nx, ny, nz, l = plane(f)
        if l < 1e-15:
            continue
        area = 0.5 * l
        nx, ny, nz = nx / l, ny / l, nz / l
        d = -(nx * V[f[0]][0] + ny * V[f[0]][1] + nz * V[f[0]][2])
        q = (nx * nx, nx * ny, nx * nz, nx * d, ny * ny, ny * nz, ny * d, nz * nz, nz * d, d * d)
        for v in f:
            Qv = Q[v]
            for k in range(10):
                Qv[k] += q[k] * area
    prot = set() if protect is None else set(int(i) for i in np.nonzero(protect)[0])
    if prot:
        for v in prot:
            Q[v] = [x * 50.0 for x in Q[v]]

    def cost_pos(a, b):
        q = [Q[a][k] + Q[b][k] for k in range(10)]
        A = ((q[0], q[1], q[2]), (q[1], q[4], q[5]), (q[2], q[5], q[7]))
        rhs = (-q[3], -q[6], -q[8])
        det = (A[0][0] * (A[1][1] * A[2][2] - A[1][2] * A[2][1]) - A[0][1] * (A[1][0] * A[2][2] - A[1][2] * A[2][0])
               + A[0][2] * (A[1][0] * A[2][1] - A[1][1] * A[2][0]))
        cands = []
        mid = [(V[a][i] + V[b][i]) * 0.5 for i in range(3)]
        if abs(det) > 1e-14:
            def solve(col):
                M = [list(r) for r in A]
                for r in range(3):
                    M[r][col] = rhs[r]
                return (M[0][0] * (M[1][1] * M[2][2] - M[1][2] * M[2][1]) - M[0][1] * (M[1][0] * M[2][2] - M[1][2] * M[2][0])
                        + M[0][2] * (M[1][0] * M[2][1] - M[1][1] * M[2][0])) / det
            p = [solve(0), solve(1), solve(2)]
            # optimum must stay near the edge (otherwise spikes on thin parts)
            el = math.dist(V[a], V[b])
            if math.dist(p, mid) < 1.5 * el + 1e-9:
                cands.append(p)
        cands += [mid, V[a], V[b]]
        best = None
        for p in cands:
            x, y, z = p
            e = (q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x + q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y
                 + q[7] * z * z + 2 * q[8] * z + q[9])
            if best is None or e < best[0]:
                best = (e, p)
        el2 = (V[a][0] - V[b][0]) ** 2 + (V[a][1] - V[b][1]) ** 2 + (V[a][2] - V[b][2]) ** 2
        return max(best[0], 0.0) + length_w * el2 * el2 * 1e2, best[1]

    stamp = [0] * nv
    heap = []
    edges = set()
    for f in F:
        for i in range(3):
            a, b = f[i], f[(i + 1) % 3]
            edges.add((min(a, b), max(a, b)))
    for a, b in edges:
        c, p = cost_pos(a, b)
        heap.append((c, a, b, 0, 0, 0))
    heapq.heapify(heap)
    alive_v = [True] * nv
    nfaces = len(F)

    def quality(a, b, c):
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        wx, wy, wz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        nx, ny, nz = uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx
        ar = math.sqrt(nx * nx + ny * ny + nz * nz)
        s = (ux * ux + uy * uy + uz * uz) + (wx * wx + wy * wy + wz * wz) + ((c[0] - b[0]) ** 2 + (c[1] - b[1]) ** 2 + (c[2] - b[2]) ** 2)
        return 2 * math.sqrt(3) * ar / max(s, 1e-30), (nx, ny, nz)

    done = 0
    while nfaces > target and heap:
        c, a, b, sa, sb, tries = heapq.heappop(heap)
        if not (alive_v[a] and alive_v[b]) or stamp[a] != sa or stamp[b] != sb:
            continue
        if a in prot and b in prot:
            continue
        shared = vf[a] & vf[b]
        if len(shared) != 2:
            continue
        na = set(); nb = set()
        for fi in vf[a]:
            na.update(F[fi])
        for fi in vf[b]:
            nb.update(F[fi])
        common = (na & nb) - {a, b}
        if len(common) != 2:
            continue
        _, p = cost_pos(a, b)
        ok = True
        for fi in (vf[a] | vf[b]) - shared:
            f = F[fi]
            old = [V[v] for v in f]
            new = [p if (v == a or v == b) else V[v] for v in f]
            qo, no = quality(*old)
            qn, nn = quality(*new)
            if no[0] * nn[0] + no[1] * nn[1] + no[2] * nn[2] <= 0.15 * math.sqrt(no[0] ** 2 + no[1] ** 2 + no[2] ** 2) * math.sqrt(nn[0] ** 2 + nn[1] ** 2 + nn[2] ** 2):
                ok = False
                break
            if qn < 0.12 and qn < qo:
                ok = False
                break
        if not ok:
            # retry later with a penalty so the heap does not spin on it
            if tries < 3:
                heapq.heappush(heap, (c * 4 + 1e-12, a, b, sa, sb, tries + 1))
            continue
        for fi in shared:
            alive_f[fi] = False
            for v in F[fi]:
                vf[v].discard(fi)
            nfaces -= 1
        for fi in list(vf[b]):
            f = F[fi]
            F[fi] = [a if v == b else v for v in f]
            vf[a].add(fi)
        vf[b] = set()
        alive_v[b] = False
        V[a] = list(p)
        Q[a] = [Q[a][k] + Q[b][k] for k in range(10)]
        if b in prot:
            prot.add(a)
        stamp[a] += 1
        nbrs = set()
        for fi in vf[a]:
            nbrs.update(F[fi])
        nbrs.discard(a)
        for n in nbrs:
            stamp[n] += 1
        for n in nbrs:
            # neighbours' stamps changed: re-push all their edges touching a and their own ring edges to a
            cc, _ = cost_pos(min(a, n), max(a, n))
            x, y = min(a, n), max(a, n)
            heapq.heappush(heap, (cc, x, y, stamp[x], stamp[y], 0))
        # edges between neighbours were invalidated by the stamp bump: re-push them too
        for n in nbrs:
            ring = set()
            for fi in vf[n]:
                ring.update(F[fi])
            for m in ring:
                if m != n and m != a and m in nbrs and n < m:
                    cc, _ = cost_pos(n, m)
                    heapq.heappush(heap, (cc, n, m, stamp[n], stamp[m], 0))
                elif m != n and m != a and m not in nbrs:
                    x, y = min(n, m), max(n, m)
                    cc, _ = cost_pos(x, y)
                    heapq.heappush(heap, (cc, x, y, stamp[x], stamp[y], 0))
        done += 1
        if verbose and done % 20000 == 0:
            print('  qem', nfaces)
    keep = [i for i in range(nv) if alive_v[i]]
    remap = {v: i for i, v in enumerate(keep)}
    V2 = np.array([V[i] for i in keep])
    F2 = np.array([[remap[v] for v in F[i]] for i in range(len(F)) if alive_f[i]], int)
    return V2, F2
