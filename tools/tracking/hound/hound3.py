"""[hound3] Tracking hound (redbone / bluetick coonhound) sculpted from scratch as a signed-distance field.

Why from scratch: the hound2 body was a reshaped wolf scan whose rest pose stood crouched (hind feet far back,
hocks low, head carried below the topline), so it "stood weird" whatever the rig did. This one is built in a correct
coonhound stance: level back, straight vertical forelegs with the feet under the withers, hind legs angled at the
stifle and hock with the rear pastern vertical under the point of buttock, neck carried up at ~45 degrees, head
above the topline, long leathers hanging beside the jaw, sabre tail carried up.

Pipeline (all here, numpy + scipy + scikit-image):
  primitives (sdf.py)  ->  marching cubes on a 3 mm grid  ->  Taubin smoothing  ->  QEM decimation (qem.py)
  -> smooth area-weighted vertex normals (shared across UV seams: no shading seams)
  -> skin weights from the primitives themselves (each primitive belongs to one bone; weights blend exactly where
     the smooth unions blend)  ->  UV charts (uvmap.py, LSCM)  ->  procedural coat baked from 3D position (coat3.py)
  -> .fhsk for the game (bones + joints, zero rest rotations: the mesh IS the rest pose).

Units: blocks (= metres), y up, head toward -z, paws on y = 0, dog's right side = +x.

  python3 hound3.py shape            quick shaded previews of the raw sculpt  -> /tmp/claude-0/h3/
  python3 hound3.py export           meshes + coats into patch/  (+ previews)
"""
import sys, os, math, json, time
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sdf import Prim, ellipsoid, round_cone, round_box, chain, smin, ssub, Rx, Ry, Rz, _rot

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
PATCH = ROOT + '/patch/assets/frontierhunts/'
OUT = '/tmp/claude-0/h4/'

# ------------------------------------------------------------------------------------------------ skeleton
# joint (pivot) of every bone, parents first. Left = -x.
def joints():
    J = {
        'pelvis': (0, 0.47, 0.25),
        'spine': (0, 0.52, 0.06),
        'chest': (0, 0.52, -0.12),
        'neck': (0, 0.545, -0.265),
        'head': (0, 0.77, -0.425),
        'tail1': (0, 0.575, 0.345),
        'tail2': (0, 0.655, 0.462),
        'tail3': (0, 0.775, 0.535),
    }
    for s, x in (('l', -1), ('r', 1)):
        J['f' + s + '_upper'] = (0.072 * x, 0.43, -0.27)     # shoulder joint (point of shoulder)
        J['f' + s + '_lower'] = (0.076 * x, 0.30, -0.212)    # elbow
        J['f' + s + '_foot'] = (0.068 * x, 0.092, -0.232)    # wrist (carpus)
        J['f' + s + '_toe'] = (0.066 * x, 0.036, -0.248)     # pastern -> paw
        J['b' + s + '_upper'] = (0.076 * x, 0.462, 0.262)    # hip
        J['b' + s + '_lower'] = (0.086 * x, 0.292, 0.205)    # stifle
        J['b' + s + '_foot'] = (0.074 * x, 0.132, 0.352)     # hock
        J['b' + s + '_toe'] = (0.07 * x, 0.036, 0.338)       # rear pastern -> paw
        J['ear_' + s] = (0.056 * x, 0.792, -0.452)
    return {k: np.array(v, float) for k, v in J.items()}


PARENT = {'pelvis': None, 'spine': 'pelvis', 'chest': 'spine', 'neck': 'chest', 'head': 'neck', 'ear_l': 'head', 'ear_r': 'head',
          'tail1': 'pelvis', 'tail2': 'tail1', 'tail3': 'tail2'}
for _s in 'lr':
    PARENT.update({'f%s_upper' % _s: 'chest', 'f%s_lower' % _s: 'f%s_upper' % _s, 'f%s_foot' % _s: 'f%s_lower' % _s, 'f%s_toe' % _s: 'f%s_foot' % _s,
                   'b%s_upper' % _s: 'pelvis', 'b%s_lower' % _s: 'b%s_upper' % _s, 'b%s_foot' % _s: 'b%s_lower' % _s, 'b%s_toe' % _s: 'b%s_foot' % _s})
ORDER = ['pelvis', 'spine', 'chest', 'neck', 'head', 'ear_l', 'ear_r', 'tail1', 'tail2', 'tail3',
         'fl_upper', 'fl_lower', 'fl_foot', 'fl_toe', 'fr_upper', 'fr_lower', 'fr_foot', 'fr_toe',
         'bl_upper', 'bl_lower', 'bl_foot', 'bl_toe', 'br_upper', 'br_lower', 'br_foot', 'br_toe']


def tilt(axis, deg):
    return {'x': Rx, 'y': Ry, 'z': Rz}[axis](math.radians(deg))


# ------------------------------------------------------------------------------------------------ the sculpt
def prims():
    J = joints()
    P = []
    add = lambda fn, bone, k=0.02, name='', sub=False: P.append(Prim(fn, bone, k, name, sub))
    # ---- trunk: deep narrow chest to the elbows, tucked loin, level back, rounded croup
    add(ellipsoid((0, 0.435, -0.105), (0.120, 0.155, 0.192), tilt('x', -6)), 'chest', 0.03, 'ribs')  # [hound4] broader
    add(ellipsoid((0, 0.415, -0.250), (0.082, 0.110, 0.080)), 'chest', 0.04, 'forechest')
    add(round_cone((0, 0.548, -0.215), (0, 0.548, 0.06), 0.058, 0.060), 'chest', 0.04, 'back_front')
    add(round_cone((0, 0.548, 0.06), (0, 0.540, 0.27), 0.060, 0.062), 'spine', 0.04, 'back_rear')
    add(round_cone((0, 0.495, -0.02), (0, 0.505, 0.185), 0.094, 0.084), 'spine', 0.05, 'loin')
    add(ellipsoid((0, 0.505, 0.268), (0.100, 0.100, 0.110)), 'pelvis', 0.04, 'croup')
    add(ellipsoid((0, 0.56, 0.33), (0.045, 0.04, 0.05)), 'pelvis', 0.03, 'tailhead')
    # shoulder blades + upper arm muscle (laid back shoulder)
    for x in (-1, 1):
        s = 'r' if x > 0 else 'l'
        add(ellipsoid((0.055 * x, 0.505, -0.205), (0.032, 0.105, 0.058), tilt('x', -28) @ tilt('z', 8 * x)), 'chest', 0.03, 'scapula_' + s)
        add(ellipsoid((0.066 * x, 0.395, -0.235), (0.046, 0.098, 0.066), tilt('x', 18)), 'f%s_upper' % s, 0.03, 'arm_' + s)
        # thigh: big hound ham, leaning forward to the stifle
        add(ellipsoid((0.068 * x, 0.385, 0.268), (0.056, 0.122, 0.098), tilt('x', 22)), 'b%s_upper' % s, 0.035, 'thigh_' + s)
        add(ellipsoid((0.064 * x, 0.295, 0.300), (0.042, 0.085, 0.056), tilt('x', -20)), 'b%s_lower' % s, 0.03, 'gaskin_' + s)
    # ---- neck, throat (a little loose skin), head
    add(round_cone((0, 0.505, -0.245), (0, 0.748, -0.418), 0.092, 0.060), 'neck', 0.05, 'neck')
    add(ellipsoid((0, 0.63, -0.385), (0.050, 0.072, 0.052), tilt('x', 35)), 'neck', 0.04, 'throat')
    add(ellipsoid((0, 0.787, -0.468), (0.058, 0.062, 0.080)), 'head', 0.03, 'skull')
    add(ellipsoid((0, 0.815, -0.432), (0.026, 0.026, 0.03)), 'head', 0.03, 'occiput')
    for x in (-1, 1):
        add(ellipsoid((0.033 * x, 0.750, -0.487), (0.036, 0.042, 0.050)), 'head', 0.025, 'cheek')
        add(ellipsoid((0.030 * x, 0.808, -0.510), (0.018, 0.012, 0.022)), 'head', 0.015, 'brow')
    add(round_box((0, 0.756, -0.590), (0.038, 0.036, 0.074), 0.026, tilt('x', 4)), 'head', 0.03, 'muzzle')
    for x in (-1, 1):
        add(ellipsoid((0.028 * x, 0.719, -0.602), (0.019, 0.034, 0.056), tilt('x', 6)), 'head', 0.02, 'flews')
    add(ellipsoid((0, 0.776, -0.662), (0.030, 0.023, 0.018)), 'head', 0.010, 'nose')
    # carve: nostrils, eye sockets (then the eyeballs), the mouth line
    for x in (-1, 1):
        add(ellipsoid((0.012 * x, 0.776, -0.676), (0.006, 0.005, 0.006)), 'head', 0.004, 'nostril', True)
        add(ellipsoid((0.040 * x, 0.793, -0.512), (0.013, 0.011, 0.012)), 'head', 0.006, 'socket', True)
    for x in (-1, 1):
        add(ellipsoid((0.037 * x, 0.792, -0.513), (0.0105, 0.0095, 0.0095)), 'head', 0.003, 'eye')
    # lower jaw: narrower than the muzzle, tucked under the hanging flews (the lip line reads as their shadow)
    add(round_box((0, 0.722, -0.552), (0.026, 0.012, 0.055), 0.010, tilt('x', -4)), 'head', 0.014, 'jaw')
    # ---- legs
    for x in (-1, 1):
        s = 'r' if x > 0 else 'l'
        f = 'f' + s
        add(round_cone(J[f + '_upper'], J[f + '_lower'], 0.046, 0.038), f + '_upper', 0.03, 'humerus')
        add(ellipsoid(J[f + '_lower'] + np.array([0, 0.0, 0.022]), (0.025, 0.028, 0.025)), f + '_lower', 0.02, 'elbow_pt')
        add(round_cone(J[f + '_lower'], J[f + '_foot'], 0.037, 0.0245), f + '_lower', 0.025, 'forearm')
        add(round_cone(J[f + '_foot'], J[f + '_toe'], 0.0235, 0.022), f + '_foot', 0.015, 'pastern')
        add(ellipsoid(J[f + '_foot'] + np.array([0, -0.004, 0.016]), (0.012, 0.012, 0.009)), f + '_foot', 0.01, 'carpal_pad')
        paw(add, J[f + '_toe'], x, f + '_toe', 1.13)
        b = 'b' + s
        add(round_cone(J[b + '_upper'], J[b + '_lower'], 0.056, 0.042), b + '_upper', 0.03, 'femur')
        add(round_cone(J[b + '_lower'], J[b + '_foot'], 0.039, 0.0235), b + '_lower', 0.03, 'tibia')
        add(ellipsoid(J[b + '_foot'] + np.array([0, 0.004, 0.012]), (0.017, 0.021, 0.015)), b + '_foot', 0.012, 'hock_pt')
        add(round_cone(J[b + '_foot'], J[b + '_toe'], 0.0225, 0.021), b + '_foot', 0.015, 'metatarsus')
        paw(add, J[b + '_toe'], x, b + '_toe', 1.09)
    # ---- tail: a sabre carried up, thick at the root, fine at the tip
    T = [np.array(p) for p in [(0, 0.565, 0.325), (0, 0.598, 0.395), (0, 0.648, 0.455), (0, 0.715, 0.505), (0, 0.785, 0.538), (0, 0.852, 0.552)]]
    R = [0.032, 0.027, 0.021, 0.016, 0.012, 0.0072]
    add(round_cone(T[0], T[1], R[0], R[1]), 'tail1', 0.03, 'tail')
    add(round_cone(T[1], T[2], R[1], R[2]), 'tail1', 0.01, 'tail')
    add(round_cone(T[2], T[3], R[2], R[3]), 'tail2', 0.01, 'tail')
    add(round_cone(T[3], T[4], R[3], R[4]), 'tail2', 0.01, 'tail')
    add(round_cone(T[4], T[5], R[4], R[5]), 'tail3', 0.008, 'tail')
    return P


def paw(add, at, x, bone, s):
    """a tight cat-like hound foot: four toes in an arc, the big metacarpal pad behind, short nails"""
    c = np.array([at[0], 0.0, at[2]])
    add(ellipsoid(c + np.array([0, 0.024, -0.012]) * s, np.array([0.025, 0.022, 0.032]) * s), bone, 0.015, 'paw')
    for i, (dx, dz, sz) in enumerate(((-0.016, -0.036, 0.85), (-0.006, -0.044, 1.0), (0.006, -0.044, 1.0), (0.016, -0.036, 0.85))):
        add(ellipsoid(c + np.array([dx * x, 0.013, dz]) * s, np.array([0.0095, 0.012, 0.0125]) * s * sz), bone, 0.008, 'toe')
    add(ellipsoid(c + np.array([0, 0.012, 0.004]) * s, np.array([0.016, 0.012, 0.014]) * s), bone, 0.01, 'pad')


# ------------------------------------------------------------------------------------------------ ears
def ear_field(p, x):
    """long hound leather: hangs from the side of the skull past the jaw, the front edge folded in a little,
    the lower half curling toward the cheek. A warped thin ellipsoid (local frame: u down the ear, w across, t thick)."""
    J = joints()
    base = J['ear_' + ('r' if x > 0 else 'l')] + np.array([0.0, 0.004, 0.002])
    q = p - base
    # local axes: down the ear (hanging close against the cheek), across (front -> back), outward
    down = np.array([0.055 * x, -1.0, -0.06]); down /= np.linalg.norm(down)
    out = np.array([x, 0.1, 0.0]); out -= down * (out @ down); out /= np.linalg.norm(out)
    across = np.cross(down, out) * x
    u = q @ down; w = q @ across; t = q @ out
    L, Wd = 0.205, 0.052
    s = np.clip(u / L, 0, 1)
    # curl: the lower ear wraps the face (mid-surface out, edges in), the leading edge rolls in
    c = 0.024 * s ** 1.5 * (1 - 0.8 * (w / Wd) ** 2) - 0.018 * s ** 1.5 - 0.010 * np.clip(-w / Wd, 0, 1) ** 2 * s
    t = t - c
    width = Wd * np.where(s < 0.6, 0.55 + 0.45 * np.sin(np.pi / 2 * s / 0.6), np.sqrt(np.clip(1 - ((s - 0.6) / 0.42) ** 2, 0, 1)))
    width = np.maximum(width, 1e-4)
    thick = 0.0065 * (1.15 - 0.4 * s)
    # ellipse-ish cross section, rounded top and tip
    du = np.maximum(-u, 0) + np.maximum(u - L, 0)
    e = np.sqrt((w / width) ** 2 + (t / thick) ** 2)
    d = (e - 1) * np.minimum(width, thick)
    return np.maximum(d, du - 0.003)


# ------------------------------------------------------------------------------------------------ field + mesh
def field(P, prs, pts):
    d = np.full(len(pts), 1.0)
    for pr in prs:
        if pr.sub:
            continue
        d = smin(d, pr(pts), pr.k)
    for pr in prs:
        if pr.sub:
            d = ssub(d, pr(pts), pr.k)
    return d


def grid_field(prs, h=0.003, lo=(-0.16, -0.004, -0.70), hi=(0.16, 0.90, 0.60), extra=None):
    """evaluate the union on a grid; every primitive only inside its own (coarse-sampled) bounding box"""
    lo = np.array(lo); hi = np.array(hi)
    n = np.ceil((hi - lo) / h).astype(int) + 1
    xs = [lo[i] + h * np.arange(n[i]) for i in range(3)]
    D = np.full(tuple(n), 0.05, np.float32)
    # coarse bbox per primitive
    cg = np.stack(np.meshgrid(*[np.arange(lo[i], hi[i] + 0.01, 0.01) for i in range(3)], indexing='ij'), -1).reshape(-1, 3)
    adds = [p for p in prs if not p.sub]; subs = [p for p in prs if p.sub]
    for group, mode in ((adds, 'add'), (subs, 'sub')):
        for pr in group:
            dc = pr(cg)
            m = dc < pr.k + 0.02
            if not m.any():
                continue
            a = cg[m].min(0) - 0.02; b = cg[m].max(0) + 0.02
            i0 = [max(0, int((a[i] - lo[i]) / h)) for i in range(3)]
            i1 = [min(n[i], int((b[i] - lo[i]) / h) + 2) for i in range(3)]
            sub = np.stack(np.meshgrid(xs[0][i0[0]:i1[0]], xs[1][i0[1]:i1[1]], xs[2][i0[2]:i1[2]], indexing='ij'), -1)
            shp = sub.shape[:3]
            dv = pr(sub.reshape(-1, 3)).reshape(shp).astype(np.float32)
            cur = D[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]]
            if mode == 'add':
                D[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = smin(cur, dv, pr.k)
            else:
                D[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = ssub(cur, dv, pr.k)
    if extra is not None:
        for fn in extra:
            g = np.stack(np.meshgrid(*xs, indexing='ij'), -1).reshape(-1, 3)
            D = np.minimum(D, fn(g).reshape(D.shape).astype(np.float32))
    return D, lo, h


def ear_grid(x, h=0.0016):
    J = joints()
    b = J['ear_' + ('r' if x > 0 else 'l')]
    lo = b + np.array([-0.06 if x < 0 else -0.03, -0.24, -0.07]); hi = b + np.array([0.03 if x < 0 else 0.06, 0.02, 0.06])
    n = np.ceil((hi - lo) / h).astype(int) + 1
    xs = [lo[i] + h * np.arange(n[i]) for i in range(3)]
    g = np.stack(np.meshgrid(*xs, indexing='ij'), -1)
    D = ear_field(g.reshape(-1, 3), x).reshape(g.shape[:3]).astype(np.float32)
    return D, lo, h


def mc(D, lo, h):
    from skimage.measure import marching_cubes
    V, F, _, _ = marching_cubes(D, 0.0, spacing=(h, h, h))
    V = V + lo
    F = F[:, ::-1].copy()          # outward winding (counter-clockwise seen from outside)
    return V, F


def taubin(V, F, iters=10, lam=0.5, mu=-0.53, mask=None):
    from scipy.sparse import coo_matrix, diags
    n = len(V)
    I = np.concatenate([F[:, 0], F[:, 1], F[:, 2], F[:, 1], F[:, 2], F[:, 0]])
    Jx = np.concatenate([F[:, 1], F[:, 2], F[:, 0], F[:, 0], F[:, 1], F[:, 2]])
    A = coo_matrix((np.ones(len(I)), (I, Jx)), shape=(n, n)).tocsr()
    A.data[:] = 1.0
    deg = np.asarray(A.sum(1)).ravel()
    Wm = diags(1.0 / np.maximum(deg, 1)) @ A
    w = np.ones((n, 1)) if mask is None else mask[:, None]
    for _ in range(iters):
        V = V + lam * w * (Wm @ V - V)
        V = V + mu * w * (Wm @ V - V)
    return V


def vnormals(V, F):
    fn = np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]])
    N = np.zeros_like(V)
    for i in range(3):
        np.add.at(N, F[:, i], fn)
    return N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-12)


def raw(h=0.003):
    t = time.time()
    prs = prims()
    D, lo, hh = grid_field(prs, h)
    V, F = mc(D, lo, hh)
    parts = [(V, F)]
    for x in (-1, 1):
        De, loe, he = ear_grid(x)
        parts.append(mc(De, loe, he))
    print('mc', [len(p[1]) for p in parts], round(time.time() - t, 1), 's')
    return prs, parts


def build(h=0.0035, target=12000, ear_target=900, parts=None, prs=None):
    """the final closed meshes: body + two ears, smoothed and decimated; part id per vertex (0 body, 1 ear_l, 2 ear_r)"""
    import qem
    if parts is None:
        prs, parts = raw(h)
    Vs, Fs, ids = [], [], []
    off = 0
    for i, (V, F) in enumerate(parts):
        V = taubin(V, F, 8 if i == 0 else 4)
        V, F = qem.decimate(V, F, target if i == 0 else ear_target, length_w=0.0002, verbose=False)
        Vs.append(V); Fs.append(F + off); ids.append(np.full(len(V), i)); off += len(V)
    return prs, np.vstack(Vs), np.vstack(Fs), np.concatenate(ids)


# ------------------------------------------------------------------------------------------------ skinning
def skin(prs, V, ids, sigma=0.0065):
    """weights from the sculpt: every primitive belongs to one bone; a vertex takes the bones of the primitives it
    lies on, blended smoothly over the same distance the smooth unions blend over"""
    J = joints()
    W = np.zeros((len(V), len(ORDER)))
    body = ids == 0
    P = V[body]
    adds = [p for p in prs if not p.sub]
    D = np.stack([p(P) for p in adds], 1)
    dmin = D.min(1, keepdims=True)
    E = np.exp(-(D - dmin) / sigma)
    for k, p in enumerate(adds):
        W[np.nonzero(body)[0], ORDER.index(p.bone)] += E[:, k]
    for i, nm in ((1, 'ear_l'), (2, 'ear_r')):
        m = ids == i
        if not m.any():
            continue
        u = J[nm][1] - V[m][:, 1]           # how far down the leather
        f = smooth01(u, 0.0, 0.035)
        W[np.nonzero(m)[0], ORDER.index(nm)] = f
        W[np.nonzero(m)[0], ORDER.index('head')] = 1 - f
    # top 4, normalised
    idx = np.argsort(-W, 1)[:, :4]
    w4 = np.take_along_axis(W, idx, 1)
    w4[w4 < 0.01 * w4[:, :1]] = 0
    w4 /= w4.sum(1, keepdims=True)
    return idx, w4


def smooth01(x, a, b):
    t = np.clip((np.asarray(x) - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def chart_labels(V, F, idx, w4, ids):
    """disk-like chart per face: region (by dominant bone) x side"""
    groups = {}
    for b in ORDER:
        if b in ('pelvis', 'spine', 'chest', 'neck'):
            groups[ORDER.index(b)] = 'body'
        elif b.startswith('tail'):
            groups[ORDER.index(b)] = 'tail'
        elif b == 'head':
            groups[ORDER.index(b)] = 'head'
        elif b.startswith('ear'):
            groups[ORDER.index(b)] = b
        else:
            groups[ORDER.index(b)] = b[:2]
    N = vnormals(V, F)
    lab = []
    fn = np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]]); fn /= np.linalg.norm(fn, axis=1, keepdims=True) + 1e-12
    cen = V[F].mean(1)
    for t, f in enumerate(F):
        if ids[f[0]] > 0:
            side = 'o' if fn[t, 0] * (1 if ids[f[0]] == 2 else -1) > 0 else 'i'
            lab.append(('ear_r' if ids[f[0]] == 2 else 'ear_l') + side)
            continue
        score = {}
        for v in f:
            for k in range(4):
                g = groups[idx[v, k]]
                score[g] = score.get(g, 0) + w4[v, k]
        g = max(score, key=score.get)
        if g in ('body', 'head', 'tail'):
            lab.append(g + ('L' if cen[t, 0] < 0 else 'R'))
        else:
            if cen[t, 1] < 0.02 and fn[t, 1] < -0.5:
                lab.append(g + 'sole')
            else:
                lab.append(g + ('F' if fn[t, 2] < 0 else 'B'))
    names = sorted(set(lab))
    return np.array([names.index(l) for l in lab])


def full_field(prs):
    def f(p):
        d = field(None, prs, p)
        for x in (-1, 1):
            d = np.minimum(d, ear_field(p, x))
        return d
    return f


# ------------------------------------------------------------------------------------------------ export
def write_fhsk(path, V, N, UV, Fuv, src, idx, w4, meta):
    import struct, gzip
    J = joints()
    out = bytearray()
    out += struct.pack('>ii', 0x46485332, 0)
    out += struct.pack('>8f', *meta)
    out += struct.pack('>h', len(ORDER))
    for b in ORDER:
        nm = b.encode(); out += struct.pack('>h', len(nm)) + nm
        out += struct.pack('>h', ORDER.index(PARENT[b]) if PARENT[b] else -1)
        out += struct.pack('>9f', *J[b], 0, 0, 0, 0, 0, 0)
    out += struct.pack('>i', len(src))
    for k, v in enumerate(src):
        q = np.round(w4[v] * 255).astype(int); q[0] += 255 - q.sum()
        out += struct.pack('>8f', *V[v], *N[v], UV[k, 0], UV[k, 1]) + bytes(idx[v].astype(np.uint8)) + bytes(np.clip(q, 0, 255).astype(np.uint8))
    out += struct.pack('>i', len(Fuv))
    for a, b, c in Fuv:
        out += struct.pack('>HHH', int(a), int(b), int(c))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb', compresslevel=9) as f:
        f.write(bytes(out))
    return len(src), len(Fuv)


def meta_of(V):
    J = joints()
    H = 0.62                                  # withers
    legLen = float(J['fl_upper'][1])
    stride = 0.75 / legLen
    return [H, legLen, 0.30, 0.0, 0.0, float(stride), 0.0, 0.0]


def export(h=0.0035):
    import coat3
    from PIL import Image
    os.makedirs(OUT, exist_ok=True)
    prs, parts = raw(h)
    ff = full_field(prs)
    J = joints()
    res = {}
    for lod, tgt, et, tres in (('ultra', 15000, 1100, 1024), ('far', 2600, 120, 256)):
        t0 = time.time()
        _, V, F, ids = build(h, tgt, et, parts=parts, prs=prs)
        N = vnormals(V, F)
        idx, w4 = skin(prs, V, ids)
        lab = chart_labels(V, F, idx, w4, ids)
        import uvmap
        Fuv, UV, src, dens = uvmap.unwrap(V, F, lab, res=tres * 2, pad=6 if tres > 512 else 4)
        res[lod] = dict(V=V, F=F, N=N, ids=ids, idx=idx, w4=w4, Fuv=Fuv, UV=UV, src=src)
        nv, nt = write_fhsk(PATCH + 'models/wildlife/hound_%s.fhsk' % ('ultra' if lod == 'ultra' else 'bal'), V, N, UV, Fuv, src, idx, w4, meta_of(V))
        print(lod, 'verts', nv, 'tris', nt, round(time.time() - t0, 1), 's')
        for kind in ('redbone', 'bluetick'):
            bres = tres * 2                 # bake at 2x, downsample (anti-aliased ticks, edges)
            img, _ = coat3.bake(Fuv, UV, V[src], N[src], kind, J, bres, ff)
            im = Image.fromarray((np.clip(img, 0, 1) * 255).astype('uint8')).resize((tres, tres), Image.LANCZOS)
            name = 'hound_%s%s.png' % (kind, '' if lod == 'ultra' else '_far')
            im.save(PATCH + 'textures/entity/wildlife/real/' + name, optimize=True)
            im.save(OUT + name)
    np.savez(OUT + 'export.npz', **{k + '_' + lod: v for lod, d in res.items() for k, v in d.items()})
    return res


def recoat():
    """[hound4] re-bake only the coats onto the last export (export.npz): for coat tweaks without re-meshing"""
    import coat3
    from PIL import Image
    d = np.load(OUT + 'export.npz')
    ff = full_field(prims())
    J = joints()
    for lod, tres in (('ultra', 1024), ('far', 256)):
        V, N, Fuv, UV, src = d['V_' + lod], d['N_' + lod], d['Fuv_' + lod], d['UV_' + lod], d['src_' + lod]
        for kind in ('redbone', 'bluetick'):
            img, _ = coat3.bake(Fuv, UV, V[src], N[src], kind, J, tres * 2, ff)
            im = Image.fromarray((np.clip(img, 0, 1) * 255).astype('uint8')).resize((tres, tres), Image.LANCZOS)
            name = 'hound_%s%s.png' % (kind, '' if lod == 'ultra' else '_far')
            im.save(PATCH + 'textures/entity/wildlife/real/' + name, optimize=True)
            im.save(OUT + name)
            print('baked', name)


def shape_preview(h=0.005, target=12000):
    import r3
    os.makedirs(OUT, exist_ok=True)
    prs, V, F, ids = build(h, target, 600)
    np.savez(OUT + 'shape.npz', V=V, F=F, ids=ids)
    N = vnormals(V, F)
    views = [r3.render(V, F, N, yaw=y, pitch=p, W=460, H=360, label=l) for (y, p, l) in
             ((90, 4, 'side R'), (0, 6, 'front'), (180, 6, 'rear'), (40, 14, '3/4'))]
    hc = [0, 0.75, -0.53]
    views += [r3.render(V, F, N, yaw=y, pitch=p, W=460, H=360, center=hc, dist=0.55, label=l) for (y, p, l) in
              ((90, 3, 'head side'), (0, 4, 'head front'), (35, 10, 'head 3/4'), (150, 15, 'head back'))]
    r3.sheet(views, 4).save(OUT + 'shape.png')
    print('wrote', OUT + 'shape.png', len(F), 'tris')


def textured_preview(lod='ultra'):
    import r3
    from PIL import Image
    d = np.load(OUT + 'export.npz')
    V, N, Fuv, UV, src = d['V_' + lod], d['N_' + lod], d['Fuv_' + lod], d['UV_' + lod], d['src_' + lod]
    for kind in ('redbone', 'bluetick'):
        tex = np.asarray(Image.open(OUT + 'hound_%s%s.png' % (kind, '' if lod == 'ultra' else '_far')).convert('RGB')).astype(float) / 255
        P = V[src]; NN = N[src]
        vs = [r3.render(P, Fuv, NN, UV, tex, yaw=y, pitch=p, W=460, H=380, center=[0, 0.42, 0], dist=2.0, label=l)
              for (y, p, l) in ((90, 3, 'side'), (180, 5, 'front'), (0, 5, 'rear'), (140, 12, 'front 3/4'))]
        vs += [r3.render(P, Fuv, NN, UV, tex, yaw=y, pitch=p, W=460, H=380, center=[0, 0.74, -0.52], dist=0.5, label=l)
               for (y, p, l) in ((180, 3, 'face'), (150, 8, 'face 3/4'), (90, 0, 'head side'), (230, 25, 'head above'))]
        r3.sheet(vs, 4).save(OUT + 'tex_%s_%s.png' % (kind, lod))


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'shape'
    if what == 'shape':
        shape_preview()
    elif what == 'export':
        export()
        textured_preview('ultra')
        textured_preview('far')
    elif what == 'coat':
        recoat()
    elif what == 'tex':
        textured_preview(sys.argv[2] if len(sys.argv) > 2 else 'ultra')
