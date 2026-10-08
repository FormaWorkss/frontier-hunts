"""[meshes] Clean-up, smoothing and curvature-adaptive refinement of the Ultra skinned wildlife meshes (.fhsk).

The shipped sculpts were decimated from triangle-soup scans to ~9-10k triangles. That left:
  * crumpled "folds" (concave edges whose two triangles fold back onto each other, dihedral > 100 deg),
  * inconsistent winding in places (normals summing to ~0 -> black blotches in game),
  * 2-triangle debris slivers inside the body (zero-length normals),
  * lumpy, faceted surfaces and coarse curved silhouettes at close range.
This tool, per mesh:
  1. welds corner vertices by position (UV seams stay split for the coat, topology is shared),
  2. drops degenerate triangles and tiny debris components, makes winding consistent and outward,
  3. relaxes every concave fold until none is left (local umbrella smoothing, a few rings),
  4. Taubin (lambda|mu) smoothing that keeps volume, damped on crisp convex features (ear rims, claws, hooves, horns),
  5. refines curved / long edges (red-green split, Phong-tessellation curved midpoints, hard budget),
  6. recomputes angle-weighted normals on the welded surface with hard edges only on crisp convex creases,
  7. interpolates UVs (per seam side) and skin weights (top 4, normalised) for every new vertex,
then writes the same .fhsk v2 format (header, bones, rest/feed poses untouched).

usage: python3 smooth.py [species...] [--lod ultra|bal|both] [--dry]   (sources: git master, output: patch/)
"""
import gzip, struct, sys, os, math, subprocess, io
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from collections import defaultdict
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(REPO, 'tools', 'wingshot'))
import fhsk  # noqa: E402

REL = 'patch/assets/frontierhunts/models/wildlife/'
BASE_REV = '644f37a'
SPECIES = ['wolf', 'coyote', 'cougar', 'panther', 'lion', 'cheetah', 'grizzly', 'black_bear', 'polar_bear', 'bison',
           'boar', 'pronghorn', 'duck']   # grouse: rebuild rejected by the audit (master kept); hound: owned by hound3

# per-species knobs: smoothing iterations (ultra), refinement budget (x triangles), feature angle for hard edges
KNOBS = {
    'default': dict(taubin=3, budget=1.45, crease=72.0, split_deg=11.0),
    'bison': dict(taubin=4, budget=1.55, crease=72.0, split_deg=10.0),
    'polar_bear': dict(taubin=4, budget=1.5, crease=72.0, split_deg=10.0),
    'grizzly': dict(taubin=4, budget=1.5, crease=72.0, split_deg=10.0),
    'black_bear': dict(taubin=4, budget=1.5, crease=72.0, split_deg=10.0),
    'lion': dict(taubin=4, budget=1.5, crease=72.0, split_deg=10.0),
    'duck': dict(taubin=3, budget=1.5, crease=70.0, split_deg=11.0),
    'grouse': dict(taubin=3, budget=1.5, crease=70.0, split_deg=11.0),
}
MID_RATIO = 0.5
WEIGHTS = os.environ.get('MESH_WEIGHTS', 'keep')    # 'keep' (default: sculpt weights minus cross-part leaks), 'fix', 'solve'
FIX_ITERS = int(os.environ.get('MESH_FIX_ITERS', '4'))
OUT_DIR = os.environ.get('MESH_OUT')


def top4(W):
    W = W.copy()
    drop = np.argsort(-W, 1)[:, 4:]
    np.put_along_axis(W, drop, 0.0, 1)
    return W / np.maximum(W.sum(1, keepdims=True), 1e-12)
BAL_KNOBS = dict(taubin=2, budget=1.0, crease=75.0, split_deg=99.0)


def source_bytes(name):
    return subprocess.run(['git', '-C', REPO, 'show', '%s:%s%s.fhsk' % (BASE_REV, REL, name)], check=True,
                          capture_output=True).stdout


# ------------------------------------------------------------------------------------------------ topology helpers
def weld(P, tol=1e-5):
    k = np.round(P / tol).astype(np.int64)
    _, idx, inv = np.unique(k, axis=0, return_index=True, return_inverse=True)
    return inv.ravel(), idx


def face_normals(Q, F):
    n = np.cross(Q[F[:, 1]] - Q[F[:, 0]], Q[F[:, 2]] - Q[F[:, 0]])
    a = np.linalg.norm(n, axis=1)
    return n / (a[:, None] + 1e-30), a * 0.5


def edge_faces(F):
    E = defaultdict(list)
    for t, (a, b, c) in enumerate(F):
        E[(min(a, b), max(a, b))].append(t)
        E[(min(b, c), max(b, c))].append(t)
        E[(min(c, a), max(c, a))].append(t)
    return E


def neighbours(F, n):
    nb = [set() for _ in range(n)]
    for a, b, c in F:
        nb[a].update((b, c)); nb[b].update((a, c)); nb[c].update((a, b))
    return [np.fromiter(s, np.int64) for s in nb]


def components(F, n):
    from scipy.sparse import coo_matrix
    from scipy.sparse.csgraph import connected_components
    E = np.r_[F[:, [0, 1]], F[:, [1, 2]]]
    A = coo_matrix((np.ones(len(E)), (E[:, 0], E[:, 1])), shape=(n, n))
    return connected_components(A, directed=False)[1]


def orient(F, Q):
    """consistent winding per component (BFS over manifold edges), then outward (positive volume)."""
    F = F.copy()
    E = edge_faces(F)
    nf = len(F)
    done = np.zeros(nf, bool)
    adj = defaultdict(list)
    for e, ts in E.items():
        if len(ts) == 2:
            adj[ts[0]].append(ts[1]); adj[ts[1]].append(ts[0])

    def directed(t):
        a, b, c = F[t]
        return {(a, b), (b, c), (c, a)}
    flips = 0
    comps = []
    for s in range(nf):
        if done[s]:
            continue
        stack = [s]; done[s] = True; comp = [s]
        while stack:
            t = stack.pop()
            dt = directed(t)
            for u in adj[t]:
                if done[u]:
                    continue
                du = directed(u)
                if dt & du:          # shares a directed edge: opposite winding
                    F[u] = F[u][::-1]; flips += 1
                done[u] = True; stack.append(u); comp.append(u)
        comps.append(comp)
    outward = 0
    for comp in comps:
        f = F[comp]
        vol = np.einsum('ij,ij->i', Q[f[:, 0]], np.cross(Q[f[:, 1]], Q[f[:, 2]])).sum()
        if vol < 0:
            F[comp] = F[comp][:, ::-1]; outward += len(comp)
    return F, flips, outward


def dihedral_info(Q, F, E=None):
    """per manifold edge: (u, v, t0, t1, angle_deg, concave)"""
    fn, _ = face_normals(Q, F)
    E = E or edge_faces(F)
    out = []
    for (u, v), ts in E.items():
        if len(ts) != 2:
            continue
        t0, t1 = ts
        ang = math.degrees(math.acos(max(-1.0, min(1.0, float(fn[t0] @ fn[t1])))))
        # third vertex of t1 relative to the plane of t0: in front = concave
        w = [x for x in F[t1] if x != u and x != v]
        conc = bool(w) and float((Q[w[0]] - Q[u]) @ fn[t0]) > 1e-9
        out.append((u, v, t0, t1, ang, conc))
    return out


def vertex_normals(Q, F, angle_weighted=True):
    fn, area = face_normals(Q, F)
    N = np.zeros_like(Q)
    if angle_weighted:
        for k in range(3):
            a, b, c = F[:, k], F[:, (k + 1) % 3], F[:, (k + 2) % 3]
            e1 = Q[b] - Q[a]; e2 = Q[c] - Q[a]
            cosang = np.einsum('ij,ij->i', e1, e2) / (np.linalg.norm(e1, axis=1) * np.linalg.norm(e2, axis=1) + 1e-30)
            ang = np.arccos(np.clip(cosang, -1, 1))
            np.add.at(N, a, fn * ang[:, None])
    else:
        for k in range(3):
            np.add.at(N, F[:, k], fn * area[:, None])
    L = np.linalg.norm(N, axis=1, keepdims=True)
    return N / np.maximum(L, 1e-30)


# ------------------------------------------------------------------------------------------------ smoothing
def umbrella(Q, nb, verts, lam):
    Qn = Q.copy()
    for v in verts:
        if len(nb[v]):
            Qn[v] = Q[v] + lam * (Q[nb[v]].mean(0) - Q[v])
    return Qn


def relax_folds(Q, F, nb, fixed, max_iter=40):
    """umbrella-relax the neighbourhood of every concave fold (> 100 deg) until none is left."""
    E = edge_faces(F)
    first = None
    for it in range(max_iter):
        bad = [d for d in dihedral_info(Q, F, E) if d[5] and d[4] > 100.0]
        if first is None:
            first = len(bad)
        if not bad:
            return Q, first, 0, it
        vs = set()
        for u, v, t0, t1, ang, conc in bad:
            vs.update(F[t0]); vs.update(F[t1])
        if it > 8:   # stubborn: widen to the 1-ring (2-ring later)
            for _ in range(1 if it < 24 else 2):
                for v in list(vs):
                    vs.update(nb[v].tolist())
        vs = [v for v in vs if not fixed[v]]
        Q = umbrella(Q, nb, vs, 0.6)
    bad = [d for d in dihedral_info(Q, F, E) if d[5] and d[4] > 100.0]
    return Q, first, len(bad), max_iter


def despike(Q, nb, damp, fixed, thresh=0.45, max_iter=30):
    """relax isolated spikes and pits: vertices sitting far off their 1-ring (|umbrella| / mean ring edge > thresh)
    that are not on a crisp feature (horn tips, claws, ear rims keep their shape)."""
    count0 = None
    for it in range(max_iter):
        bad = []
        for v in range(len(Q)):
            r = nb[v]
            if len(r) < 3 or fixed[v] or damp[v] < 0.99:
                continue
            c = Q[r].mean(0)
            el = np.linalg.norm(Q[r] - Q[v], axis=1).mean()
            if np.linalg.norm(c - Q[v]) > thresh * el:
                bad.append(v)
        if count0 is None:
            count0 = len(bad)
        if not bad:
            break
        Q = umbrella(Q, nb, bad, 0.7)
    return Q, count0, len(bad)


CRISP_BONES = ('head', 'tail2', 'wing_l', 'wing_r', 'ear_l', 'ear_r')


def crisp_mask(names, Wd):
    """vertices allowed to keep crisp features: head (horns, ears, nose, beak), feet/toes (hooves, claws, bird toes),
    hound ears, tail tip, bird wings. The body, neck and limbs are always smoothed."""
    cols = [i for i, n in enumerate(names) if n in CRISP_BONES or n.endswith('_foot') or n.endswith('_toe')]
    return Wd[:, cols].sum(1) >= 0.5


def feature_damp(Q, F, nb, crisp):
    """0..1 per vertex: 1 = smooth freely, small on crisp convex features (ear rims, claws, hoof edges, horn ridges)."""
    damp = np.ones(len(Q))
    for u, v, t0, t1, ang, conc in dihedral_info(Q, F):
        if not conc and ang > 55.0 and crisp[u] and crisp[v]:
            k = 0.15 if ang > 80.0 else 0.4
            damp[u] = min(damp[u], k); damp[v] = min(damp[v], k)
    return damp


def taubin(Q, nb, iters, damp, fixed, lam=0.5, mu=-0.53):
    deg = np.array([len(x) for x in nb])
    idx = np.repeat(np.arange(len(Q)), deg)
    flat = np.concatenate([x for x in nb if len(x)]) if deg.sum() else np.zeros(0, np.int64)
    move = (damp * (~fixed))[:, None]
    for _ in range(iters):
        for f in (lam, mu):
            S = np.zeros_like(Q)
            np.add.at(S, idx, Q[flat])
            L = S / np.maximum(deg, 1)[:, None] - Q
            L[deg == 0] = 0
            Q = Q + f * move * L
    return Q


# ------------------------------------------------------------------------------------------------ refinement
def phong_mid(Q, N, a, b, alpha=0.65):
    m = 0.5 * (Q[a] + Q[b])
    pa = m - ((m - Q[a]) @ N[a]) * N[a]
    pb = m - ((m - Q[b]) @ N[b]) * N[b]
    return (1 - alpha) * m + alpha * 0.5 * (pa + pb)


def choose_splits(Q, F, N, knobs, budget_tris):
    """edges worth splitting, best first: curvature (dihedral / normal turn) x length, plus very long edges."""
    E = edge_faces(F)
    fn, _ = face_normals(Q, F)
    lens = []
    cand = []
    for (u, v), ts in E.items():
        L = float(np.linalg.norm(Q[u] - Q[v]))
        lens.append(L)
    med = float(np.median(lens))
    for (u, v), ts in E.items():
        L = float(np.linalg.norm(Q[u] - Q[v]))
        if L < 0.55 * med:
            continue
        turn = math.degrees(math.acos(max(-1.0, min(1.0, float(N[u] @ N[v])))))
        dih = 0.0
        if len(ts) == 2:
            dih = math.degrees(math.acos(max(-1.0, min(1.0, float(fn[ts[0]] @ fn[ts[1]])))))
        curv = max(turn, dih)
        if curv >= knobs['split_deg'] and curv < 75.0:       # crisp creases stay as they are (no rounding)
            cand.append((curv * L / med, u, v))
        elif L > 2.6 * med:
            cand.append((0.5 * curv * L / med + 5.0, u, v))
    cand.sort(reverse=True)
    # each split edge adds ~2 triangles (one per adjacent face); stop at the budget
    allow = max(0, int((budget_tris - len(F)) / 2))
    return {(min(u, v), max(u, v)) for _, u, v in cand[:allow]}, med


def refine(F_c, Fw, Q, N, W, UV, split):
    """Red-green split. F_c: corner (output-vertex) triangles; Fw: welded triangles (same order).
    Q/N/W: welded positions, normals, dense weights; UV per corner vertex. Returns new arrays."""
    nW = len(Q); ncv = len(UV)
    newQ, newW, newUV = [], [], []
    wmid, cmid, corner_w = {}, {}, {}

    def qpos(i):
        return Q[i] if i < nW else newQ[i - nW]

    def welded_mid(a, b):
        k = (min(a, b), max(a, b))
        if k not in wmid:
            wmid[k] = nW + len(newQ)
            newQ.append(phong_mid(Q, N, a, b))
            newW.append(0.5 * (W[a] + W[b]))
        return wmid[k]

    def corner_mid(ca, cb, wa, wb):
        k = (min(ca, cb), max(ca, cb))
        if k not in cmid:
            cmid[k] = ncv + len(newUV)
            newUV.append(0.5 * (UV[ca] + UV[cb]))
            corner_w[cmid[k]] = welded_mid(wa, wb)
        return cmid[k]

    outC, outW = [], []
    for t in range(len(Fw)):
        w = [int(x) for x in Fw[t]]; c = [int(x) for x in F_c[t]]
        s = [(min(w[i], w[(i + 1) % 3]), max(w[i], w[(i + 1) % 3])) in split for i in range(3)]
        ns = sum(s)
        if ns == 0:
            outC.append(c); outW.append(w); continue
        mc = [None] * 3; mw = [None] * 3
        for i in range(3):
            if s[i]:
                j = (i + 1) % 3
                mc[i] = corner_mid(c[i], c[j], w[i], w[j]); mw[i] = welded_mid(w[i], w[j])
        # vertex ids: 0..2 corners, 3..5 midpoints of edges 0..2
        if ns == 3:
            tris = [(0, 3, 5), (3, 1, 4), (5, 4, 2), (3, 4, 5)]
        elif ns == 1:
            i = s.index(True)
            a, b, cc = i, (i + 1) % 3, (i + 2) % 3
            tris = [(a, 3 + i, cc), (3 + i, b, cc)]
        else:
            i = s.index(False)        # edge a-b unsplit; m1 on edge b-cc, m2 on edge cc-a
            a, b, cc = i, (i + 1) % 3, (i + 2) % 3
            m1, m2 = 3 + b, 3 + cc
            wl = w + mw
            tris = [(m1, cc, m2)]
            if np.linalg.norm(qpos(wl[a]) - qpos(wl[m1])) < np.linalg.norm(qpos(wl[b]) - qpos(wl[m2])):
                tris += [(a, b, m1), (a, m1, m2)]
            else:
                tris += [(a, b, m2), (b, m1, m2)]
        cl = c + mc; wl = w + mw
        for tri in tris:
            outC.append([cl[x] for x in tri])
            outW.append([wl[x] for x in tri])
    Qall = np.concatenate([Q, np.array(newQ).reshape(-1, 3)])
    Wall = np.concatenate([W, np.array(newW).reshape(-1, W.shape[1])])
    UVall = np.concatenate([UV, np.array(newUV).reshape(-1, 2)])
    return np.array(outC, np.int64), np.array(outW, np.int64), Qall, Wall, UVall, corner_w


# ------------------------------------------------------------------------------------------------ hard-edge normals
def corner_normals(Q, Fw, crease_deg, crisp):
    """per (triangle, corner) normal: angle-weighted over the faces around the vertex that are reachable without
    crossing a crisp convex crease (> crease_deg). Returns (T,3,3)."""
    fn, _ = face_normals(Q, Fw)
    E = edge_faces(Fw)
    hard = set()
    cosc = math.cos(math.radians(crease_deg))
    for (u, v), ts in E.items():
        if len(ts) == 2 and float(fn[ts[0]] @ fn[ts[1]]) < cosc:
            w = [x for x in Fw[ts[1]] if x != u and x != v]
            conc = bool(w) and float((Q[w[0]] - Q[u]) @ fn[ts[0]]) > 1e-9
            if not conc and crisp[u] and crisp[v]:
                hard.add((u, v))
    # corner angles
    ang = np.zeros((len(Fw), 3))
    for k in range(3):
        a, b, c = Fw[:, k], Fw[:, (k + 1) % 3], Fw[:, (k + 2) % 3]
        e1 = Q[b] - Q[a]; e2 = Q[c] - Q[a]
        cs = np.einsum('ij,ij->i', e1, e2) / (np.linalg.norm(e1, axis=1) * np.linalg.norm(e2, axis=1) + 1e-30)
        ang[:, k] = np.arccos(np.clip(cs, -1, 1))
    vf = defaultdict(list)
    for t in range(len(Fw)):
        for k in range(3):
            vf[Fw[t, k]].append((t, k))
    CN = np.zeros((len(Fw), 3, 3))
    smooth_n = vertex_normals(Q, Fw)
    for v, lst in vf.items():
        touches_hard = any((min(v, x), max(v, x)) in hard for t, k in lst for x in Fw[t])
        if not touches_hard:
            for t, k in lst:
                CN[t, k] = smooth_n[v]
            continue
        # union faces around v that share a non-hard edge through v
        parent = {t: t for t, k in lst}

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]; x = parent[x]
            return x
        by_edge = defaultdict(list)
        for t, k in lst:
            for x in Fw[t]:
                if x != v:
                    by_edge[x].append(t)
        for x, ts in by_edge.items():
            if (min(v, x), max(v, x)) in hard:
                continue
            for i in range(1, len(ts)):
                parent[find(ts[i])] = find(ts[0])
        acc = defaultdict(lambda: np.zeros(3))
        for t, k in lst:
            acc[find(t)] += fn[t] * ang[t, k]
        for t, k in lst:
            n = acc[find(t)]
            CN[t, k] = n / max(np.linalg.norm(n), 1e-30)
    return CN, len(hard)


# ------------------------------------------------------------------------------------------------ skin weights
def chain(name):
    for p in ('fl', 'fr', 'bl', 'br'):
        if name.startswith(p + '_'):
            return 'leg_' + p
    if name in ('l_upper', 'l_lower', 'l_foot'):
        return 'leg_l'
    if name in ('r_upper', 'r_lower', 'r_foot'):
        return 'leg_r'
    if name.startswith('tail'):
        return 'tail'
    if name.startswith('wing_'):
        return name
    if name in ('head', 'ear_l', 'ear_r'):
        return 'head'
    if name == 'neck':
        return 'neck'
    return 'body'


def incompatible(a, b):
    """chains that must never share a vertex: different legs, legs and tail / head, wings and legs / tail"""
    if a == b:
        return False
    s = {a, b}
    legs = [c for c in s if c.startswith('leg_')]
    if len(legs) == 2:
        return True
    if legs and (s & {'tail', 'head'} or any(c.startswith('wing_') for c in s)):
        return True
    if any(c.startswith('wing_') for c in s) and (s & {'tail', 'head'} or len([c for c in s if c.startswith('wing_')]) == 2):
        return True
    if s == {'head', 'tail'}:
        return True
    return False


def bone_hops(parent):
    nb = len(parent)
    D = np.full((nb, nb), 99, np.int64)
    for b in range(nb):
        D[b, b] = 0
        if parent[b] >= 0:
            D[b, parent[b]] = D[parent[b], b] = 1
    for k in range(nb):
        D = np.minimum(D, D[:, k:k + 1] + D[k:k + 1, :])
    return D


def fix_weights(Wd, names, parent, nb_list, iters=3):
    """Remove influences that leak across the body (heat/diffusion weights bled through touching geometry: tails
    following the hind legs, manes and heads following the forelegs, one leg dragging the other) and give every
    vertex a smooth falloff among the bones it can anatomically follow: within two bones of its own, never another
    limb, never tail/head for a leg. Returns new dense weights and stats."""
    nbn = len(names)
    ch = [chain(n) for n in names]
    D = bone_hops(parent)
    allow = np.zeros((nbn, nbn), bool)
    for a in range(nbn):
        for b in range(nbn):
            allow[a, b] = D[a, b] <= 2 and not incompatible(ch[a], ch[b])
    region = np.argmax(Wd, 1)
    # mode filter: a vertex whose region disagrees with most of its ring takes the ring's region
    for _ in range(2):
        new = region.copy()
        for v in range(len(region)):
            r = nb_list[v]
            if len(r) < 3:
                continue
            vals, cnt = np.unique(region[r], return_counts=True)
            k = np.argmax(cnt)
            if vals[k] != region[v] and cnt[k] >= 0.6 * len(r):
                new[v] = vals[k]
        region = new
    leaked = float((Wd * ~allow[region]).sum(1).max())
    nleak = int(((Wd * ~allow[region]) > 0.03).any(1).sum())
    M = allow[region].astype(np.float64)
    W = Wd * M
    s = W.sum(1, keepdims=True)
    zero = s[:, 0] <= 1e-9
    W[zero] = 0
    W[zero, region[zero]] = 1
    W = W / np.maximum(W.sum(1, keepdims=True), 1e-12)
    deg = np.array([len(r) for r in nb_list])
    idx = np.repeat(np.arange(len(W)), deg)
    flat = np.concatenate([r for r in nb_list if len(r)])
    for _ in range(iters):
        S = np.zeros_like(W)
        np.add.at(S, idx, W[flat])
        A = S / np.maximum(deg, 1)[:, None]
        W = np.where(deg[:, None] > 0, 0.5 * W + 0.5 * A, W) * M
        W = W / np.maximum(W.sum(1, keepdims=True), 1e-12)
        W[W.sum(1) <= 1e-9, :] = 0
    bad = W.sum(1) <= 1e-9
    W[bad, region[bad]] = 1
    return W, dict(leaky_verts=nleak, worst_leak=round(leaked, 3))


def bone_segments(Q, names, parent, joint, Wd):
    """(a, b) segment per bone: joint to its same-chain child joint; leaves (head, tail tip, toes, wings, ears) along
    their parent's direction out to where their old vertices end."""
    nbn = len(names)
    ch = [chain(n) for n in names]
    A = np.array(joint, np.float64).reshape(nbn, 3)
    B = A.copy()
    dom = np.argmax(Wd, 1)
    order = {'pelvis': 'spine', 'spine': 'chest', 'chest': 'neck', 'neck': 'head'}
    for b in range(nbn):
        kids = [c for c in range(nbn) if parent[c] == b]
        same = [c for c in kids if ch[c] == ch[b] or names[c] == order.get(names[b])]
        if same:
            B[b] = A[same].mean(0)
            continue
        p = parent[b]
        d = A[b] - A[p] if p >= 0 else np.array([0, 0, -1.0])
        if np.linalg.norm(d) < 1e-6:
            d = np.array([0, -1.0, 0]) if ch[b].startswith('leg') else np.array([0, 0, -1.0])
        d = d / np.linalg.norm(d)
        own = Q[dom == b]
        L = float(np.percentile((own - A[b]) @ d, 90)) if len(own) > 8 else 0.0
        if names[b].startswith('wing_') and len(own) > 8:
            c = own.mean(0) - A[b]
            d = c / max(np.linalg.norm(c), 1e-6)
            L = float(np.percentile((own - A[b]) @ d, 90))
        B[b] = A[b] + d * max(L, 0.02)
    return A, B


def seg_dist(P, a, b):
    ab = b - a
    t = np.clip(((P - a) @ ab) / max(float(ab @ ab), 1e-12), 0, 1)
    C = a + t[:, None] * ab
    return np.linalg.norm(P - C, axis=1), C


def blocked(Q, Fw, p, c, skip):
    """does the segment p->c cross the surface (any triangle not incident to the vertex)?"""
    v0 = Q[Fw[:, 0]]; e1 = Q[Fw[:, 1]] - v0; e2 = Q[Fw[:, 2]] - v0
    d = c - p
    L = np.linalg.norm(d)
    if L < 1e-9:
        return False
    d = d / L
    h = np.cross(d, e2)
    a = np.einsum('ij,ij->i', e1, h)
    ok = np.abs(a) > 1e-14
    f = np.where(ok, 1.0 / np.where(ok, a, 1), 0)
    s = p - v0
    u = f * np.einsum('ij,ij->i', s, h)
    q = np.cross(s, e1)
    v = f * (q @ d)
    t = f * np.einsum('ij,ij->i', e2, q)
    hit = ok & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 1e-6) & (t < L * 0.98)
    hit &= ~skip
    return bool(hit.any())


def solve_weights(Q, Fw, names, parent, joint, Wd_old, nb_list, H, iters=8):
    """Skin weights re-solved from the skeleton: every vertex is labelled with the nearest bone it can 'see' (the line
    to the bone does not leave the body), legs only below their hip / shoulder joint, labels cleaned by a mode filter,
    then a smooth falloff by constrained diffusion over the surface among anatomically adjacent bones (never another
    limb, never tail/head for a leg). Max 4 influences, normalised."""
    nbn = len(names)
    ch = [chain(n) for n in names]
    A, B = bone_segments(Q, names, parent, joint, Wd_old)
    D = np.stack([seg_dist(Q, A[b], B[b])[0] for b in range(nbn)], 1)
    C = [seg_dist(Q, A[b], B[b])[1] for b in range(nbn)]
    # legs only below the top of the leg (the torso above the shoulder / hip follows the spine)
    top = {}
    for b in range(nbn):
        if ch[b].startswith('leg_'):
            p = b
            while parent[p] >= 0 and ch[parent[p]] == ch[b]:
                p = parent[p]
            top[b] = A[p][1]
    for b, y in top.items():
        D[Q[:, 1] > y + 0.03 * H, b] = np.inf
    vf = defaultdict(list)
    for t, f in enumerate(Fw):
        for v in f:
            vf[v].append(t)
    label = np.argmin(D, 1)
    order = np.argsort(D, 1)
    nvis = 0
    for v in range(len(Q)):
        cand = order[v, :4]
        best = cand[0]
        skip = np.zeros(len(Fw), bool)
        skip[vf[v]] = True
        for b in cand:
            if not np.isfinite(D[v, b]) or D[v, b] > 2.0 * D[v, cand[0]] + 0.02 * H:
                break
            p = Q[v] + 0.01 * (C[b][v] - Q[v])
            if not blocked(Q, Fw, p, C[b][v], skip):
                best = b
                break
            nvis += 1
        label[v] = best
    for _ in range(3):
        new = label.copy()
        for v in range(len(label)):
            r = nb_list[v]
            if len(r) < 3:
                continue
            vals, cnt = np.unique(label[r], return_counts=True)
            k = np.argmax(cnt)
            if vals[k] != label[v] and cnt[k] >= 0.6 * len(r):
                new[v] = vals[k]
        label = new
    Dh = bone_hops(parent)
    allow = np.zeros((nbn, nbn), bool)
    for a in range(nbn):
        for b in range(nbn):
            allow[a, b] = Dh[a, b] <= 1 and not incompatible(ch[a], ch[b])
    M = allow[label].astype(np.float64)
    W = np.zeros((len(Q), nbn))
    W[np.arange(len(Q)), label] = 1.0
    deg = np.array([len(r) for r in nb_list])
    idx = np.repeat(np.arange(len(W)), deg)
    flat = np.concatenate([r for r in nb_list if len(r)])
    for _ in range(iters):
        S = np.zeros_like(W)
        np.add.at(S, idx, W[flat])
        Av = S / np.maximum(deg, 1)[:, None]
        W = np.where(deg[:, None] > 0, 0.5 * W + 0.5 * Av, W) * M
        W = W / np.maximum(W.sum(1, keepdims=True), 1e-12)
        z = W.sum(1) <= 1e-9
        W[z, label[z]] = 1.0
    # top 4
    top4 = np.argsort(-W, 1)[:, 4:]
    np.put_along_axis(W, top4, 0.0, 1)
    W = W / np.maximum(W.sum(1, keepdims=True), 1e-12)
    changed = int((np.argmax(Wd_old, 1) != label).sum())
    return W, label, dict(relabelled=changed, occluded_checks=nvis)


# ------------------------------------------------------------------------------------------------ main pipeline
def process(m, knobs, log=print):
    P = m.pos.astype(np.float64); T = m.tris.astype(np.int64)
    nb_bones = len(m.names)
    inv, rep = weld(P)
    nW = inv.max() + 1
    Q = P[rep].copy()
    Wd = np.zeros((nW, nb_bones))
    for k in range(4):
        np.add.at(Wd, (inv, m.bone[:, k]), 0.0)
    # dense weights from the representative corner of each welded vertex
    for k in range(4):
        Wd[np.arange(nW), m.bone[rep, k]] += m.weight[rep, k]
    Fw = inv[T]
    UV = m.uv.astype(np.float64)
    Fc = T.copy()
    stats = dict(tris_in=len(T), verts_in=len(P))
    # -- 1. degenerate faces and debris
    _, area = face_normals(Q, Fw)
    keep = (Fw[:, 0] != Fw[:, 1]) & (Fw[:, 1] != Fw[:, 2]) & (Fw[:, 0] != Fw[:, 2]) & (area > 1e-12)
    lab = components(Fw[keep], nW)
    fl = lab[Fw[:, 0]]
    tot = area[keep].sum()
    for c in np.unique(fl[keep]):
        sel = keep & (fl == c)
        if sel.sum() < 8 and area[sel].sum() < 1e-4 * tot:
            keep &= ~sel
    stats['debris_dropped'] = int((~keep).sum())
    Fw = Fw[keep]; Fc = Fc[keep]
    # -- 2. winding
    Fw2, flips, outward = orient(Fw, Q)
    flipped = np.any(Fw2 != Fw, axis=1)
    Fc[flipped] = Fc[flipped][:, ::-1]
    Fw = Fw2
    stats['winding_fixed'] = int(flips); stats['outward_flipped'] = int(outward)
    nb = neighbours(Fw, nW)
    E = edge_faces(Fw)
    fixed = np.zeros(nW, bool)
    for (u, v), ts in E.items():
        if len(ts) != 2:
            fixed[u] = fixed[v] = True      # open / non-manifold rims stay put
    # -- 3. folds
    Q, folds0, folds1, it = relax_folds(Q, Fw, nb, fixed)
    stats['folds_before'] = folds0; stats['folds_after'] = folds1
    # -- 4. Taubin, damped on crisp features
    crisp = crisp_mask(m.names, Wd)
    damp = feature_damp(Q, Fw, nb, crisp)
    Q = taubin(Q, nb, knobs['taubin'], damp, fixed)
    Q, sp0, sp1 = despike(Q, nb, damp, fixed)
    stats['spikes_before'] = sp0; stats['spikes_after'] = sp1
    Q, _, folds2, _ = relax_folds(Q, Fw, nb, fixed, 40)
    stats['folds_after'] = folds2
    # -- 4b. skin weights: no leaks between limbs / tail / head, smooth falloff
    if knobs.get('weights', WEIGHTS) == 'keep':
        # the sculpt's own weights, untouched except influences of an anatomically incompatible part (a tail bone on
        # a hind-leg vertex, the other leg) relative to the vertex's dominant bone, which are dropped and renormalised
        ch = [chain(n) for n in m.names]
        dom = np.argmax(Wd, 1)
        bad = np.array([[incompatible(ch[a], ch[b]) for b in range(nb_bones)] for a in range(nb_bones)])
        M = ~bad[dom]
        wst = dict(cross_part_influences=int(((Wd * ~M) > 0.01).any(1).sum()))
        if os.environ.get('MESH_DROP_LEAKS') == '1':
            Wd = Wd * M
        # default: the sculpt's weights exactly (the offline pose audit showed dropping the cross-part influences, or
        # re-solving the weights, opens tears / sharp webs at the tail-hock and leg junctions in the gallop and pounce)
        Wd = top4(Wd)
    elif knobs.get('weights', WEIGHTS) == 'solve':
        Wd, label, wst = solve_weights(Q, Fw, m.names, m.parent, m.joint, Wd, nb, float(m.meta[0]))
    else:
        # the sculpt's own (heat) weights keep their soft falloff; only the leaks across limbs / tail / head are
        # removed and the boundaries diffused a little further (smoother bends, no rubber webs between parts)
        Wd, wst = fix_weights(Wd, m.names, m.parent, nb, iters=FIX_ITERS)
        Wd = top4(Wd)
    stats.update(wst)
    # -- 5. refinement (ultra) or decimation (mid level of detail)
    N = vertex_normals(Q, Fw)
    if knobs.get('decimate'):
        import decimate as dec
        Fc, Fw = dec.decimate(Fc, Fw, Q, Wd, int(knobs['decimate'] * stats['tris_in']), crisp_mask(m.names, Wd))
        stats['decimated'] = True
    else:
        budget = int(knobs['budget'] * stats['tris_in'])
        if budget > len(Fw):
            split, med = choose_splits(Q, Fw, N, knobs, budget)
            Fc, Fw, Q, Wd, UV, cw = refine(Fc, Fw, Q, N, Wd, UV, split)
            stats['edges_split'] = len(split)
    # -- 6. normals with hard creases (split output vertices where corner normals differ)
    crisp = crisp_mask(m.names, Wd)
    CN, nhard = corner_normals(Q, Fw, knobs['crease'], crisp)
    stats['hard_edges'] = nhard
    # -- 7. build output vertices: key (corner vertex, quantised normal, cut side)
    # junctions between parts that move independently (a tail resting on a hock, a leg against the other leg, a
    # bird's toes under the tail) are cut: each side gets its own vertex following only its own bones, so the parts
    # separate cleanly instead of stretching a rubber web between them
    names = m.names
    chn = [chain(n) for n in names]
    lab = np.argmax(Wd, 1)
    fchain = []
    for t in range(len(Fw)):
        acc = defaultdict(float)
        for w in Fw[t]:
            for b in np.nonzero(Wd[w] > 0)[0]:
                acc[chn[b]] += Wd[w, b]
        fchain.append(max(acc.items(), key=lambda kv: kv[1])[0])
    vchains = defaultdict(set)
    for t in range(len(Fw)):
        for w in Fw[t]:
            vchains[w].add(fchain[t])
    junction = {w for w, cs in vchains.items() if any(incompatible(a, b) for a in cs for b in cs)}
    if knobs.get('weights', WEIGHTS) == 'keep':
        junction = set()   # one surface: no cut seams that could open in motion
    stats['cut_vertices'] = len(junction)
    verts = {}
    outP, outN, outUV, outB, outWt = [], [], [], [], []
    tris = np.zeros_like(Fc)
    for t in range(len(Fc)):
        for k in range(3):
            c = int(Fc[t, k]); n = CN[t, k]; w = int(Fw[t, k])
            side = fchain[t] if w in junction else ''
            key = (c, int(round(n[0] * 64)), int(round(n[1] * 64)), int(round(n[2] * 64)), side)
            i = verts.get(key)
            if i is None:
                i = len(outP); verts[key] = i
                outP.append(Q[w]); outN.append(n); outUV.append(UV[c])
                wd = Wd[w].copy()
                if side:
                    ok = np.array([not incompatible(side, cb) for cb in chn])
                    wd = wd * ok
                    if wd.sum() <= 1e-9:
                        # this side's own bone: the strongest of the face's other corners on this chain
                        best = None
                        for w2 in Fw[t]:
                            b2 = int(np.argmax(Wd[w2] * ok))
                            if ok[b2] and Wd[w2, b2] > 0 and (best is None or Wd[w2, b2] > Wd[best[0], best[1]]):
                                best = (w2, b2)
                        wd = np.zeros_like(wd)
                        wd[best[1] if best else lab[w]] = 1.0
                top = np.argsort(-wd)[:4]
                tw = wd[top]; tw = tw / max(tw.sum(), 1e-12)
                q = np.round(tw * 255).astype(int); q[0] += 255 - q.sum()
                outB.append(top); outWt.append(np.clip(q, 0, 255))
            tris[t, k] = i
    stats['tris_out'] = len(tris); stats['verts_out'] = len(outP)
    if len(outP) > 65535:
        raise RuntimeError('too many vertices for 16-bit indices: %d' % len(outP))
    out = fhsk.Mesh()
    for a in ('bird', 'meta', 'names', 'parent', 'joint', 'rest', 'feed'):
        setattr(out, a, getattr(m, a))
    out.pos = np.array(outP, np.float32); out.nrm = np.array(outN, np.float32); out.uv = np.array(outUV, np.float32)
    out.bone = np.array(outB, np.int32); out.wq = np.array(outWt, np.int32)
    out.weight = out.wq / 255.0
    out.tris = tris.astype(np.int32)
    return out, stats


def write(path, m):
    b = bytearray()
    b += struct.pack('>ii', 0x46485332, 1 if m.bird else 0)
    b += struct.pack('>8f', *[float(x) for x in m.meta])
    b += struct.pack('>h', len(m.names))
    for i, n in enumerate(m.names):
        nm = n.encode()
        b += struct.pack('>h', len(nm)) + nm
        b += struct.pack('>h', int(m.parent[i]))
        b += struct.pack('>9f', *[float(x) for x in m.joint[i]], *[float(x) for x in m.rest[i]], *[float(x) for x in m.feed[i]])
    b += struct.pack('>i', len(m.pos))
    rec = np.zeros(len(m.pos), dtype=np.dtype([('p', '>f4', 3), ('n', '>f4', 3), ('uv', '>f4', 2), ('b', 'u1', 4), ('w', 'u1', 4)]))
    rec['p'] = m.pos; rec['n'] = m.nrm; rec['uv'] = m.uv; rec['b'] = m.bone.astype(np.uint8); rec['w'] = m.wq.astype(np.uint8)
    b += rec.tobytes()
    b += struct.pack('>i', len(m.tris))
    b += m.tris.astype('>u2').tobytes()
    with gzip.open(path, 'wb', compresslevel=9) as f:
        f.write(bytes(b))


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    lod = 'both'
    for a in sys.argv[1:]:
        if a.startswith('--lod='):
            lod = a.split('=')[1]
    dry = '--dry' in sys.argv
    for s in args or SPECIES:
        for l in (('ultra', 'mid', 'bal') if lod == 'both' else (lod,)):
            name = '%s_%s' % (s, l)
            m = fhsk.parse(source_bytes('%s_%s' % (s, 'ultra' if l == 'mid' else l)))
            knobs = dict(KNOBS['default']); knobs.update(KNOBS.get(s, {}))
            if l == 'bal':
                knobs = dict(BAL_KNOBS)
            if l == 'mid':
                knobs['decimate'] = MID_RATIO
            out, st = process(m, knobs)
            print(name, st, flush=True)
            if not dry:
                write(os.path.join(OUT_DIR or os.path.join(REPO, REL), name + '.fhsk'), out)
