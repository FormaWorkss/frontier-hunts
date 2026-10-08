"""[hound2] Tracking hound (redbone / bluetick coonhound) rebuilt from the CLEAN 'wolfsrc' canine.

Mesh (Ultra + Balanced): the wolf body is reshaped smoothly (every deformation is a smooth field, no per-vertex
masks with hard edges): deeper chest and tucked waist, slightly longer legs, leaner neck / head (wolf ruff, cheek fur
and mane flattened), a longer squarer hound muzzle with deep flews, the bushy wolf brush turned into a thin whip tail
carried up, and the erect wolf ears collapsed into the skull. New closed geometry adds LONG DROOPY HOUND EARS that
hang from the sides of the skull past the jaw, each on its own bone ('ear_l'/'ear_r', child of 'head') so the rig can
let them hang with gravity when the head drops or lifts. Ear UVs live in a strip at the right of the atlas (the
wolf's islands are squeezed into the left 82 %). Proportions: ~0.63 block at the withers (a big redbone).

Coats (procedural, short and glossy): redbone = solid mahogany red, subtle form shading, lighter muzzle / chest;
bluetick = blue-grey ticking with black patches (ears, head, saddle) and tan points. Dark eyes, black nose.

  python3 hound.py preview      -> /tmp/claude-0/h2/prev_*.png
  python3 hound.py export       -> patch/assets/frontierhunts/models/wildlife/hound_{ultra,bal}.fhsk + textures
Run from anywhere (adds /home/claude/fh/ultra to the path). Writes only hound_* files."""
import sys, math, os, numpy as np
sys.path.insert(0, '/home/claude/fh/ultra')
from PIL import Image, ImageFilter
from scipy import ndimage as ndi
from species import base, texel_positions, smooth01, xform
import norm

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
PATCH = ROOT + '/patch/assets/frontierhunts/'
OUT = '/tmp/claude-0/h2/'
WITHERS = 0.63        # shoulder height in blocks
USQ = 0.82            # wolf UV islands squeezed into u < USQ; ear strip in u > USQ + gap
EAR_L = 0.30          # ear leather length (wolf units, before the final scale)
EAR_W = 0.072         # ear half width
EAR_T = 0.011         # ear half thickness


def bump(x, a, b, c, d):
    """smooth plateau: 0 below a, 1 between b and c, 0 above d"""
    return smooth01(x, a, b) * (1 - smooth01(x, c, d))


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


# ----------------------------------------------------------------------------------------------- body reshape
def body_field(P, legw):
    """smooth reshaping of the wolf (wolf units, y up, head toward -z)"""
    P = P.copy()
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    body = 1 - legw
    # -- erect wolf ears sink into a smooth domed hound skull: everything above the dome folds back under it
    topc = np.interp(z, [-0.60, -0.52, -0.46, -0.40, -0.34, -0.28, -0.22], [0.80, 0.865, 0.885, 0.885, 0.87, 0.85, 0.83])
    dome = topc - 5.0 * x ** 2
    hb = bump(z, -0.62, -0.57, -0.26, -0.18) * smooth01(y, 0.72, 0.78)
    d = np.maximum(y - dome, 0)
    P[:, 1] -= hb * 1.6 * d
    P[:, 0] *= 1 - hb * 0.35 * smooth01(d, 0.0, 0.06)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    # -- mane / hackles flattened: the neck top line drops into a clean hound neck
    nk = bump(z, -0.40, -0.32, -0.10, 0.0) * smooth01(y, 0.66, 0.80)
    P[:, 1] -= 0.035 * nk * smooth01(y, 0.66, 0.86)
    # -- cheek fur, throat ruff and neck slimmed
    cheek = bump(z, -0.53, -0.47, -0.28, -0.20) * bump(y, 0.48, 0.56, 0.84, 0.90)
    neck = bump(z, -0.38, -0.30, -0.12, -0.02) * smooth01(y, 0.42, 0.52)
    P[:, 0] *= 1 - 0.30 * cheek - 0.18 * neck * (1 - cheek)
    # throat ruff (the wolf's chest bib) pulled in toward the chest
    bib = bump(z, -0.42, -0.36, -0.24, -0.16) * bump(y, 0.40, 0.46, 0.62, 0.70)
    P[:, 2] += 0.035 * bib
    # -- lighter frame
    torso = body * bump(z, -0.30, -0.20, 0.45, 0.55) * smooth01(y, 0.30, 0.40)
    P[:, 0] *= 1 - 0.08 * torso
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    # -- muzzle: longer, squarer, level top line, deep flews
    s = smooth01(-z, 0.51, 0.67)                      # 0 at the stop .. 1 at the nose
    mz = s * smooth01(y, 0.55, 0.60)
    cy = 0.70 - 0.03 * s
    top = smooth01(y - cy, 0.0, 0.08) * mz
    P[:, 1] += 0.035 * top * smooth01(-z, 0.56, 0.68)   # no tapering ski-slope: a square hound nose
    low = smooth01(cy - y, -0.01, 0.05) * smooth01(-z, 0.47, 0.58) * smooth01(y, 0.52, 0.58)
    P[:, 1] -= 0.028 * low * (1 - 0.4 * smooth01(-z, 0.62, 0.68))    # flews hang below the jaw line
    P[:, 0] *= 1 + 0.22 * mz * smooth01(-z, 0.55, 0.68)                # blunt, wide nose
    P[:, 2] -= 0.05 * smooth01(-z, 0.50, 0.66)                         # longer muzzle
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    # -- deeper chest, tucked waist
    chest = body * bump(z, -0.36, -0.22, 0.02, 0.14) * (1 - smooth01(y, 0.40, 0.52))
    P[:, 1] -= 0.04 * chest
    waist = body * bump(z, 0.10, 0.20, 0.34, 0.42) * (1 - smooth01(y, 0.42, 0.55))
    P[:, 1] += 0.03 * waist
    # -- slightly longer legs: everything above the elbows rises, the legs stretch
    P[:, 1] += 0.04 * smooth01(P[:, 1], 0.02, 0.40)
    return P


def joint_field(p):
    """same field for bone heads / joints (no leg weighting: joints sit on the bone line)"""
    P = body_field(np.array([p]), np.zeros(1))[0]
    return P


# ----------------------------------------------------------------------------------------------- remove the wolf brush
def remove_brush(M):
    """The wolf's bushy tail is sculpted against the backs of the thighs, so it can't be slimmed in place without
    webbing. Delete its triangles and close the openings with gently domed fans (the new whip tail is separate
    geometry). Wolf units."""
    names = [b['name'] for b in M['bones']]
    Q = M['Q']; J = M['J']; tv = M['tv']; cuv = M['cuv']; W = M['W']
    b0 = np.array(J['tail_base']); tip = np.array(J['tail_tip'])
    L = np.linalg.norm(tip - b0); d0 = (tip - b0) / L
    t = (Q - b0) @ d0
    r = np.linalg.norm(Q - b0 - t[:, None] * d0, axis=1)
    dl = np.full(len(Q), 9.0)
    for l in ('bl', 'br'):
        ch = [np.array(J[l + s_]) for s_ in ('_top', '_mid', '_low', '_foot', '_toe')]
        for a_, b_ in zip(ch[:-1], ch[1:]):
            ab = b_ - a_; tt = np.clip(((Q - a_) @ ab) / (ab @ ab), 0, 1)
            dl = np.minimum(dl, np.linalg.norm(Q - a_ - tt[:, None] * ab, axis=1))
    tw0 = W[:, names.index('tail1')] + W[:, names.index('tail2')]
    brush = (t > 0.035) & (r < 0.11) & (tw0 > 0.3) & (Q[:, 2] > 0.38)
    def bedges(T):
        e = np.concatenate([T[:, [0, 1]], T[:, [1, 2]], T[:, [2, 0]]])
        s_ = set(map(tuple, e))
        return [(x, y) for x, y in e if (y, x) not in s_]
    before = set(bedges(tv))
    keep = ~brush[tv].any(1)
    tv2 = tv[keep]; cuv2 = cuv[keep]
    vuv = {}
    for tri, uv in zip(tv2, cuv2):
        for kk in range(3): vuv.setdefault(int(tri[kk]), uv[kk])
    be = [e for e in bedges(tv2) if e not in before]
    nxt = {x: y for x, y in be}
    seen = set(); loops = []
    for x, y in be:
        if x in seen: continue
        loop = [x]; seen.add(x); c = nxt.get(x)
        while c is not None and c not in seen:
            loop.append(c); seen.add(c); c = nxt.get(c)
        loops.append(loop)
    Qn = [Q]; Wn = [W]; addT = []; addUV = []; nv = len(Q)
    def emit(tri):
        P_ = np.vstack(Qn)
        g = P_[tri].mean(0); n_ = np.cross(P_[tri[1]] - P_[tri[0]], P_[tri[2]] - P_[tri[0]])
        if np.linalg.norm(n_) < 1e-10: return
        if n_ @ np.array([g[0], 0.0, 0.15]) < 0: tri = [tri[0], tri[2], tri[1]]
        addT.append(tri); addUV.append([FILL_UV(P_[v]) for v in tri])
    def zipper(A_, B_):
        """triangulate between two polylines (vertex ids) running the same direction"""
        i = j = 0; P_ = np.vstack(Qn)
        while i < len(A_) - 1 or j < len(B_) - 1:
            if j == len(B_) - 1 or (i < len(A_) - 1 and P_[A_[i + 1], 1] >= P_[B_[j + 1], 1]):
                emit([A_[i], A_[i + 1], B_[j]]); i += 1
            else:
                emit([A_[i], B_[j + 1], B_[j]]); j += 1
    allb = Q[[v for l_ in loops for v in l_]]
    lo_, hi_ = allb.min(0), allb.max(0)
    def FILL_UV(p):    # planar (x, y) projection into the fill's own rect of the ear/tail strip; v up (blender)
        fu = (p[0] - lo_[0]) / max(hi_[0] - lo_[0], 1e-6); fv = (p[1] - lo_[1]) / max(hi_[1] - lo_[1], 1e-6)
        return [USQ + 0.015 + (0.995 - USQ - 0.015) * np.clip(fu, 0, 1), 1 - (0.80 + 0.05 * (1 - np.clip(fv, 0, 1)))]
    for loop in loops:
        if len(loop) < 3: continue
        # zipper fill: the opening runs down the backs of the thighs; split it at its top and bottom into a left
        # and a right chain and close it with a strip whose middle is recessed (the gap between the thighs)
        P = Q[loop]; it = int(np.argmax(P[:, 1])); ib = int(np.argmin(P[:, 1]))
        n_ = len(loop)
        c1 = [loop[(it + q) % n_] for q in range((ib - it) % n_ + 1)]
        c2 = [loop[(it - q) % n_] for q in range((it - ib) % n_ + 1)]
        if np.mean(Q[c1][:, 0]) > np.mean(Q[c2][:, 0]): c1, c2 = c2, c1
        rows = max(len(c1), len(c2))
        def res(c):
            Pc = Q[c]; d = np.r_[0, np.cumsum(np.linalg.norm(np.diff(Pc, axis=0), axis=1))]; d /= d[-1]
            tt = np.linspace(0, 1, rows)
            return np.stack([np.interp(tt, d, Pc[:, q]) for q in range(3)], -1), np.stack([np.interp(tt, d, W[c][:, q]) for q in range(W.shape[1])], -1)
        Lp, Lw = res(c1); Rp, Rw = res(c2)
        cols = []
        ytop = Q[loop][:, 1].max()
        for sc in (0.25, 0.5, 0.75):
            Pm = Lp * (1 - sc) + Rp * sc
            depth = (0.02 + 0.07 * smooth01(ytop - Pm[:, 1], 0.03, 0.2)) * math.sin(math.pi * sc)
            Pm[:, 2] -= depth
            Pm[0] = Lp[0] * (1 - sc) + Rp[0] * sc; Pm[-1] = Lp[-1] * (1 - sc) + Rp[-1] * sc
            ids = list(range(nv, nv + rows)); nv += rows
            Qn.append(Pm); Wn.append(Lw * (1 - sc) + Rw * sc); cols.append(ids)
        zipper(c1, cols[0]); zipper(cols[0], cols[1]); zipper(cols[1], cols[2]); zipper(cols[2], c2)
    M['Q'] = np.vstack(Qn); Wa = np.vstack(Wn)
    # nothing left of the wolf body may follow the tail bones any more (the new whip is separate geometry): hand
    # their share to the vertex's other bones (legs near the hocks, pelvis at the rump)
    i1, i2, ip = names.index('tail1'), names.index('tail2'), names.index('pelvis')
    Wa[:, i1] = 0; Wa[:, i2] = 0
    ssum = Wa.sum(1); Wa[ssum < 1e-6, ip] = 1.0; Wa /= Wa.sum(1, keepdims=True)
    M['W'] = Wa
    M['tv'] = np.vstack([tv2, np.array(addT, int).reshape(-1, 3)])
    M['cuv'] = np.concatenate([cuv2, np.array(addUV, float).reshape(-1, 3, 2)], 0)
    M['_fills'] = len(addT); M['_nwolf'] = len(tv2)
    print('brush removed: tris', int((~keep).sum()), 'openings', len(loops), [len(l) for l in loops])


# ----------------------------------------------------------------------------------------------- tubes (ears, tail)
def tube(M, C, S, N, a, b, uvrect, wrow, curl=None):
    """closed tube along centreline C (n+1 rings incl. the two poles), cross-section half-extents a (along S) and
    b (along N); curl(i, sw) adds an offset along N. Appends to M (positions, tris, corner uvs, weights)."""
    Q = M['Q']; base_ = len(Q); nu = len(C) - 1
    nr = M['_nr']
    V = []; Cen = []
    for i in range(1, nu):
        for j in range(nr):
            ang = 2 * np.pi * j / nr
            sw, st = np.cos(ang), np.sin(ang)
            off = S[i] * a[i] * sw + N[i] * b[i] * st
            if curl is not None: off = off + N[i] * curl(i, sw)
            V.append(C[i] + off); Cen.append(C[i])
    top = base_ + len(V); V.append(C[0]); Cen.append(C[0] + (C[1] - C[0]) * 1.5)
    bot = base_ + len(V); V.append(C[nu]); Cen.append(C[nu] + (C[nu - 1] - C[nu]) * 1.5)
    u0, v0, u1, v1 = uvrect
    def UV(i, j): return [u0 + (u1 - u0) * j / nr, 1 - (v0 + (v1 - v0) * i / nu)]   # blender v-up
    def vid(i, j): return base_ + (i - 1) * nr + (j % nr)
    T = []; U = []
    for i in range(1, nu - 1):
        for j in range(nr):
            A_, B_, C_, D_ = vid(i, j), vid(i, j + 1), vid(i + 1, j + 1), vid(i + 1, j)
            T += [[A_, B_, C_], [A_, C_, D_]]
            U += [[UV(i, j), UV(i, j + 1), UV(i + 1, j + 1)], [UV(i, j), UV(i + 1, j + 1), UV(i + 1, j)]]
    for j in range(nr):
        T += [[top, vid(1, j + 1), vid(1, j)], [bot, vid(nu - 1, j), vid(nu - 1, j + 1)]]
        U += [[UV(0, j + 0.5), UV(1, j + 1), UV(1, j)], [UV(nu, j + 0.5), UV(nu - 1, j), UV(nu - 1, j + 1)]]
    V = np.array(V); Cen = np.array(Cen); T = np.array(T); U = np.array(U, float)
    Qa = np.vstack([Q, V]); cen = np.vstack([np.zeros_like(Q), Cen])
    for k_, (x, y, z) in enumerate(T):
        n_ = np.cross(Qa[y] - Qa[x], Qa[z] - Qa[x]); g = (Qa[x] + Qa[y] + Qa[z]) / 3
        if n_ @ (g - cen[[x, y, z]].mean(0)) < 0:
            T[k_] = [x, z, y]; U[k_] = U[k_][[0, 2, 1]]
    rows = [wrow(i) for i in range(1, nu) for _ in range(nr)] + [wrow(0), wrow(nu)]
    M['Q'] = Qa; M['tv'] = np.vstack([M['tv'], T]); M['cuv'] = np.concatenate([M['cuv'], U], 0)
    M['W'] = np.vstack([M['W'], np.array(rows)])


def add_bone(M, name, parent, head, tail):
    M['bones'].append(dict(name=name, parent=parent, head=list(map(float, head)), tail=list(map(float, tail))))
    M['W'] = np.hstack([M['W'], np.zeros((len(M['W']), 1))])
    M['J'][name] = list(map(float, head))


def make_tail(M, lod):
    """thin whip tail carried up in a sabre curve (final units)"""
    names = [b['name'] for b in M['bones']]; J = M['J']; k = M['_k']
    nu = 26 if lod == 'ultra' else 10
    b0 = np.array(J['tail_base'])
    Ln = 0.46 * k
    ss = np.linspace(-0.045 * k, Ln, nu + 1)
    def ang(sv):      # direction from straight back (+z), radians upward
        return 0.30 + 0.75 * smooth01(sv, 0.25 * Ln, 0.95 * Ln)
    C = [b0 + np.array([0, -0.012 * k, -0.045 * k])]
    for i in range(1, nu + 1):
        th = ang(ss[i - 1]); C.append(C[-1] + np.array([0, math.sin(th), math.cos(th)]) * (ss[i] - ss[i - 1]))
    C = np.array(C)
    S = np.tile([1.0, 0, 0], (nu + 1, 1))
    N = np.array([[0, math.cos(ang(sv)), -math.sin(ang(sv))] for sv in ss])
    u = np.clip(ss / Ln, 0, 1)
    rad = (0.034 * (1 - 0.72 * u ** 1.1) + 0.003) * k
    rad = rad * (0.75 + 0.25 * smooth01(ss, -0.045 * k, 0.0))
    rad[0] = rad[-1] = 0.0
    i1, i2, ip = names.index('tail1'), names.index('tail2'), names.index('pelvis')
    nb = len(names)
    def wrow(i):
        w = np.zeros(M['W'].shape[1]); s_ = ss[i]
        f2 = smooth01(s_, 0.35 * Ln, 0.6 * Ln); fp = 1 - smooth01(s_, -0.03 * k, 0.03 * k)
        w[i2] = f2 * (1 - fp); w[i1] = (1 - f2) * (1 - fp); w[ip] = fp
        return w
    tube(M, C, S, N, rad, rad, (USQ + 0.015, 0.86, 0.995, 0.995), wrow)
    def at(sv): return np.array([np.interp(sv, ss, C[:, q]) for q in range(3)])
    mid = at(0.45 * Ln); end = at(Ln)
    J['tail_mid'] = mid.tolist(); J['tail_tip'] = end.tolist()
    for bn in M['bones']:
        if bn['name'] == 'tail2': bn['head'] = mid.tolist(); bn['tail'] = end.tolist()
        if bn['name'] == 'tail1': bn['tail'] = mid.tolist()


def make_ears(M, lod):
    """closed leaf-shaped leathers hanging from the skull sides past the jaw, each on its own bone (child of head)"""
    names = [b['name'] for b in M['bones']]; J = M['J']; k = M['_k']
    Q = M['Q']; W = M['W']
    hw = W[:len(Q), names.index('head')] + W[:len(Q), names.index('neck')]
    nu = 22 if lod == 'ultra' else 9
    Lr, Wd, Th = EAR_L * k, EAR_W * k, EAR_T * k
    y0 = 0.815 * k; z0 = -0.425 * k
    head_pts = Q[(hw > 0.3) & (Q[:, 1] > 0.40 * k) & (np.arange(len(Q)) < M['nbody'])]
    us = np.linspace(0, 1, nu + 1)
    yc = y0 + 0.012 * k * np.sin(np.pi * np.clip(us / 0.12, 0, 1)) - Lr * us
    zc = z0 - 0.015 * k * us + 0.012 * k * np.sin(np.pi * us)
    tipr = np.sqrt(np.clip(1 - ((us - 0.55) / 0.45) ** 2, 0, 1))
    wd = Wd * np.where(us < 0.55, 0.55 + 0.45 * np.sin(np.pi / 2 * us / 0.55), tipr) * (0.6 + 0.4 * smooth01(us, 0.0, 0.08))
    th = Th * (1 - 0.35 * us) * np.where(us < 0.55, 1.0, tipr)
    curlA = 0.012 * k
    for side, sgn in enumerate((-1, 1)):
        xin = np.zeros_like(us)
        for i in range(len(us)):
            m = (np.abs(head_pts[:, 1] - yc[i]) < 0.03 * k) & (np.abs(head_pts[:, 2] - zc[i]) < wd[i] + 0.02 * k) & (sgn * head_pts[:, 0] > 0)
            xin[i] = np.abs(head_pts[m][:, 0]).max() if m.any() else 0.0
        xin = ndi.gaussian_filter1d(ndi.maximum_filter1d(xin, 5), 1.5)
        xc = xin + 0.006 * k + th + curlA
        root = xin[0] - 0.02 * k
        xc = np.maximum(xc, 0.88 * np.maximum.accumulate(xc))     # hangs straight past the jaw, not tucked under it
        xc = root + (xc - root) * smooth01(us, 0.0, 0.16) + 0.006 * k * us
        C = np.stack([sgn * xc, yc, zc], -1)
        S = np.tile([0, 0, -1.0], (nu + 1, 1)); N = np.tile([sgn * 1.0, 0, 0], (nu + 1, 1))
        nm = 'ear_l' if sgn < 0 else 'ear_r'
        piv = [sgn * float(xin[0] + 0.004 * k), float(yc[0]), float(zc[0])]
        add_bone(M, nm, 'head', piv, [piv[0], piv[1] - Lr, piv[2]])
        bi = len(M['bones']) - 1
        def wrow(i, bi=bi):
            w = np.zeros(M['W'].shape[1]); w[bi] = 1.0; return w
        curl = lambda i, sw: -curlA * (sw ** 2) * smooth01(us[i], 0.05, 0.3)
        v0, v1 = (0.0, 0.39) if side == 0 else (0.40, 0.79)
        tube(M, C, S, N, wd, th, (USQ + 0.015, v0 + 0.005, 0.995, v1), wrow, curl)


def smooth_shards(M, iters=24):
    """Taubin smoothing over the regions where the wolf's long guard-hair shards stick out of the silhouette (crown,
    nape / old mane, cheek ruff, the backs of the thighs around the removed brush): a short hound coat is smooth."""
    from scipy.sparse import coo_matrix, diags
    Q = M['Q']; tv = M['tv']; n = len(Q)
    I = np.concatenate([tv[:, 0], tv[:, 1], tv[:, 2], tv[:, 1], tv[:, 2], tv[:, 0]])
    Jx = np.concatenate([tv[:, 1], tv[:, 2], tv[:, 0], tv[:, 0], tv[:, 1], tv[:, 2]])
    A = coo_matrix((np.ones(len(I)), (I, Jx)), shape=(n, n)).tocsr(); A.data[:] = 1.0
    deg = np.maximum(np.asarray(A.sum(1)).ravel(), 1)
    x, y, z = Q[:, 0], Q[:, 1], Q[:, 2]
    w = np.clip(bump(z, -0.62, -0.55, -0.02, 0.08) * smooth01(y, 0.66, 0.74)
                + bump(z, -0.50, -0.44, -0.22, -0.14) * bump(y, 0.45, 0.52, 0.80, 0.86) * 0.7
                + 1.6 * smooth01(z, 0.36, 0.44) * bump(y, 0.05, 0.12, 0.50, 0.58), 0, 1)
    w[np.asarray(A.sum(1)).ravel() == 0] = 0
    P = Q.copy()
    for _ in range(iters):
        for lam in (0.5, -0.53):
            Lp = (A @ P) / deg[:, None] - P
            P = P + lam * w[:, None] * Lp
    M['Q'] = P


_K = {}
def scale_k(lod, M):
    """one scale for both LODs, measured on the dense mesh's withers"""
    if 'k' not in _K:
        if lod != 'ultra':
            make('ultra')
        else:
            Q = M['Q']; wz = np.array(M['J']['chest'])[2]
            m = (np.abs(Q[:, 2] - wz) < 0.05) & (np.abs(Q[:, 0]) < 0.04)
            _K['k'] = WITHERS / Q[m][:, 1].max()
    return _K['k']


def make(lod):
    M = base('wolfsrc', lod)
    M['tex0'] = M['tex']
    remove_brush(M)
    names = [b['name'] for b in M['bones']]
    legw = np.zeros(len(M['Q']))
    for n in names:
        if n.endswith(('_lower', '_foot', '_toe')):
            legw += M['W'][:, names.index(n)]
    legw = np.clip(legw * 1.5, 0, 1)
    M['Q'] = body_field(M['Q'], legw)
    smooth_shards(M)
    for b in M['bones']:
        b['head'] = joint_field(b['head']).tolist(); b['tail'] = joint_field(b['tail']).tolist()
    M['J'] = {kk: joint_field(v).tolist() for kk, v in M['J'].items()}
    k = scale_k(lod, M)
    M['_k'] = k
    xform(M, lambda P: P * k)
    M['cuv'] = M['cuv'].copy(); M['cuv'][:M['_nwolf'], :, 0] *= USQ
    M['nbody'] = len(M['Q'])
    M['_nr'] = 20 if lod == 'ultra' else 10
    make_tail(M, lod)
    M['_nr'] = 20 if lod == 'ultra' else 9
    make_ears(M, lod)
    M['H'] = float(M['Q'][:M['nbody'], 1].max())      # top of the head (not the carried tail)
    return M


# ----------------------------------------------------------------------------------------------- coats
def wolf_layer(M, res):
    """wolf albedo re-laid into the squeezed atlas (ear strip neutral)"""
    a = M['tex0'].resize((int(round(res * USQ)), res), Image.LANCZOS)
    im = Image.new('RGB', (res, res), (128, 128, 128)); im.paste(a, (0, 0))
    return np.asarray(im).astype(float) / 255


def coat(M, kind, res):
    a = wolf_layer(M, res)
    pos, nor = texel_positions(M, res)
    empty = np.isnan(pos[..., 0])
    P = np.nan_to_num(pos, nan=0.0); N = nor
    k = M['_k']; J = M['J']
    x, y, z = P[..., 0] / k, P[..., 1] / k, P[..., 2] / k       # back in wolf units for region tests
    hd = np.array(J['head']) / k; sn = np.array(J['snout']) / k
    strip = np.zeros((res, res), bool); strip[:, int(res * USQ):] = True
    ear = strip & ~empty
    ear[int(res * 0.795):, :] = False          # rows below the ears hold the thigh fill and the tail
    L = a @ np.array([0.3, 0.59, 0.11])
    # form shading from the wolf's baked light, heavily low-passed (keeps the sculpt's shape, drops the fur noise)
    Lb = ndi.gaussian_filter(np.where(strip, np.nan_to_num(np.median(L[~strip])), L), res / 64)
    shade = np.clip(Lb / np.median(Lb[~strip & ~empty]), 0.55, 1.35) ** 0.45
    shade[strip] = 1.0
    rng = np.random.default_rng(3 if kind == 'redbone' else 5)
    up = smooth01(N[..., 1], -0.2, 0.9)
    under = smooth01(-N[..., 1], 0.1, 0.8)
    headd = np.linalg.norm(np.stack([x, y, z], -1) - hd, axis=-1)
    muz = smooth01(-z, 0.52, 0.64) * (y > 0.5)
    chestfront = bump(z, -0.45, -0.38, -0.2, -0.1) * bump(y, 0.40, 0.48, 0.66, 0.72) * smooth01(-N[..., 2], 0.2, 0.8)
    if kind == 'redbone':
        base_c = np.array([0.46, 0.155, 0.065]); deep = np.array([0.30, 0.085, 0.035]); light = np.array([0.62, 0.30, 0.14])
        col = np.broadcast_to(base_c, a.shape).copy()
        col = col * (1 - 0.30 * up[..., None]) + deep * 0.30 * up[..., None]            # darker topline
        lt = np.clip(0.45 * under * (y > 0.3) + 0.35 * chestfront + 0.30 * muz * smooth01(0.70 - y, -0.02, 0.06), 0, 0.6)
        col = col * (1 - lt[..., None]) + light * lt[..., None]
        legs = smooth01(0.30 - y, 0.0, 0.2)
        col = col * (1 - 0.15 * legs[..., None]) + light * 0.15 * legs[..., None]
        earc = deep * 0.75 + base_c * 0.25
        col[ear] = col[ear] * 0.3 + earc * 0.7
        sheen = 0.10
    else:
        white = np.array([0.80, 0.81, 0.82]); black = np.array([0.055, 0.055, 0.065]); blue = np.array([0.36, 0.40, 0.47]); tan = np.array([0.60, 0.33, 0.14])
        f = rng.random((res, res))
        dots = (f < 0.10).astype(float)
        dots = np.clip(ndi.gaussian_filter(dots, 0.7) * 3.2, 0, 1)
        tick = dots
        col = white * (1 - 0.92 * tick[..., None]) + black * 0.92 * tick[..., None]
        col = col * 0.55 + blue * 0.45                                  # the blue roan read at a distance
        # black patches: saddle, head, ears, spots, root of the tail
        noise = ndi.gaussian_filter(rng.standard_normal((res, res)), res / 90)
        noise = (noise - noise.mean()) / noise.std()
        saddle = smooth01(N[..., 1] * 1.0 + 0.35 * noise, 0.15, 0.45) * bump(z, -0.30, -0.15, 0.30, 0.45) * (y > 0.5)
        hood = smooth01(1 - headd / 0.30, 0.0, 0.25) * smooth01(y, 0.55, 0.62)
        spots = smooth01(noise, 1.4, 1.7) * (y > 0.35)
        blk = np.clip(saddle + hood + spots, 0, 1)
        blk[ear] = 1.0
        col = col * (1 - blk[..., None]) + black * blk[..., None]
        # tan points: muzzle, brows, cheeks, lower legs, under the tail
        cheek = muz * (1 - smooth01(y, 0.68, 0.74)) + bump(z, -0.53, -0.49, -0.40, -0.36) * bump(y, 0.58, 0.62, 0.70, 0.74) * 0.9
        legs = smooth01(0.24 - y, 0.0, 0.08)
        brow = np.zeros_like(y)
        for sgn in (-1, 1):
            bc = np.array([sgn * 0.045, 0.835, -0.515])
            brow += np.clip(1 - np.linalg.norm(np.stack([x, y, z], -1) - bc, axis=-1) / 0.022, 0, 1)
        tp = np.clip(cheek * (1 - 0.5 * smooth01(y, 0.70, 0.76)) + legs + smooth01(brow, 0.0, 0.4), 0, 1) * (~ear)
        col = col * (1 - 0.85 * tp[..., None]) + tan * 0.85 * tp[..., None]
        sheen = 0.06
    # short glossy hair: fine streaks + a touch of sheen on the top of the form
    fine = ndi.gaussian_filter(rng.standard_normal((res, res)), (0.6, 1.6)) * 0.05
    out = col * shade[..., None] * (1 + fine[..., None]) * (1 + sheen * smooth01(N[..., 1], 0.5, 0.95)[..., None])
    # keep the wolf's dark facial detail: lip line, nostrils, eye rims (darkest wolf pixels on the head)
    face = (headd < 0.30) & ~strip
    dark = np.clip((0.16 - L) / 0.10, 0, 1) * face
    out = out * (1 - 0.8 * dark[..., None]) + np.array([0.06, 0.04, 0.035]) * 0.8 * dark[..., None]
    # nose leather
    nose = np.clip(1 - np.linalg.norm(np.stack([x, y, z], -1) - (sn + np.array([0, 0.0, 0.0])), axis=-1) / 0.05, 0, 1)
    nose = smooth01(nose, 0.0, 0.5) * (~strip)
    out = out * (1 - nose[..., None]) + np.array([0.05, 0.04, 0.04]) * nose[..., None]
    # eyes: the wolf's amber iris texels -> deep brown with a dark pupil
    sat = a.max(-1) - a.min(-1)
    eye = (sat > 0.18) & (a[..., 0] > 0.30) & (headd < 0.16) & ~strip & (y > 0.74) & (z < -0.46)
    eye = ndi.binary_dilation(eye, iterations=1)
    out[eye] = np.array([0.20, 0.10, 0.05]) * (0.6 + 0.6 * L[eye, None])
    # ear leathers: soft darker edge, inner side a touch lighter / pinker
    if ear.any():
        out[ear] *= 0.95
    _, (iy, ix_) = ndi.distance_transform_edt(empty, return_indices=True)
    out = out[iy, ix_]                                  # bleed island colours outward (no grey seams in the mips)
    img = Image.fromarray((np.clip(out, 0, 1) * 255).astype('uint8'))
    return img.filter(ImageFilter.SMOOTH)


# ----------------------------------------------------------------------------------------------- preview
def part_of(M, tex):
    Q = M['Q']; tv = M['tv']; cuv = M['cuv']
    fn_ = np.cross(Q[tv[:, 1]] - Q[tv[:, 0]], Q[tv[:, 2]] - Q[tv[:, 0]]); VN = np.zeros_like(Q)
    for i in range(3): np.add.at(VN, tv[:, i], fn_)
    VN /= np.linalg.norm(VN, axis=1, keepdims=True) + 1e-12
    UV = cuv.reshape(-1, 2).copy(); UV[:, 1] = 1 - UV[:, 1]
    t = np.asarray(tex.convert('RGBA')).astype(np.float32) / 255
    return (Q[tv].reshape(-1, 3), UV, VN[tv].reshape(-1, 3), t)


def preview(M, tex, fn, views=((90, 5), (180, 5), (35, 12), (270, 8), (0, 10)), W=420, H=330, head=False):
    from sheet import render_parts
    p = part_of(M, tex)
    img = Image.new('RGB', (W * len(views), H))
    c = np.array(M['J']['head']) + np.array([0, -0.02, -0.06]) if head else None
    for i, (yy, pp) in enumerate(views):
        img.paste(render_parts([p], yy, pp, W, H, center=c, dist=0.55 if head else None), (i * W, 0))
    img.save(fn)


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'preview'
    os.makedirs(OUT, exist_ok=True)
    if what == 'preview':
        lod = sys.argv[2] if len(sys.argv) > 2 else 'ultra'
        M = make(lod)
        print('verts', len(M['Q']), 'tris', len(M['tv']), 'H', round(M['H'], 3), 'k', round(M['_k'], 3))
        for kind in (sys.argv[3:] or ('redbone', 'bluetick')):
            t = coat(M, kind, 1024 if lod == 'ultra' else 512)
            t.save(OUT + f'tex_{kind}_{lod}.png')
            preview(M, t, OUT + f'prev_{kind}_{lod}.png')
            preview(M, t, OUT + f'head_{kind}_{lod}.png', views=((90, 5), (180, 5), (140, 15), (215, -10)), head=True)
    elif what == 'export':
        import export as EX
        Mu = make('ultra'); Mb = make('bal')
        old = norm.HEIGHT['wolf']
        norm.HEIGHT['wolf'] = Mu['H']    # export's meta / feed pose read the height by species id; borrow the wolf's slot
        try:
            os.makedirs(PATCH + 'models/wildlife', exist_ok=True)
            os.makedirs(PATCH + 'textures/entity/wildlife/real', exist_ok=True)  # [presets] no Balanced pixel coats any more
            nv, nt, m = EX.write_fhsk(PATCH + 'models/wildlife/hound_ultra.fhsk', 'wolf', Mu)
            nv2, nt2, _ = EX.write_fhsk(PATCH + 'models/wildlife/hound_bal.fhsk', 'wolf', Mb)
            print('ultra', nv, nt, 'bal', nv2, nt2, 'meta', [round(x, 3) for x in m])
            for kind in ('redbone', 'bluetick'):
                tu = coat(Mu, kind, 1024); tb = coat(Mb, kind, 512)
                tu.convert('RGB').save(PATCH + f'textures/entity/wildlife/real/hound_{kind}.png', optimize=True)
                tb.convert('RGB').resize((256, 256), Image.LANCZOS).save(PATCH + f'textures/entity/wildlife/real/hound_{kind}_far.png', optimize=True)
        finally:
            norm.HEIGHT['wolf'] = old
