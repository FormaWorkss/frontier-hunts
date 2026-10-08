"""[benchart] Bench model builder + texture baker.

A bench is authored as one piece in model space x 0..32, y 0..26, z 0..16 (front = north = z 0).
Pipeline:  elements -> split at x=16 -> hidden-face cull -> per-face atlas allocation (32 px/block = 2 texels per
model pixel) -> procedural solid-texture bake (material albedo, exposed-edge wear, seam darkening, ray-traced ambient
occlusion against the bench itself and the ground) -> left/right/inventory block model JSON.
"""
import json
import math
import os

import numpy as np
from PIL import Image
from scipy import ndimage

from noise import fbm, vnoise, smoothstep, hexrgb, ramp  # noqa: F401

TPP = 2  # texels per model pixel (32 px per block)
FACE_NAMES = ('north', 'south', 'east', 'west', 'up', 'down')
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def face_frame(f, t, face):
    """origin, u_dir, v_dir, (fs, ft) so that (s,t) face coords map exactly onto Minecraft's UV orientation"""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face == 'north':
        return np.array([x1, y1, z0]), np.array([-1, 0, 0]), np.array([0, -1, 0]), (x1 - x0, y1 - y0)
    if face == 'south':
        return np.array([x0, y1, z1]), np.array([1, 0, 0]), np.array([0, -1, 0]), (x1 - x0, y1 - y0)
    if face == 'east':
        return np.array([x1, y1, z1]), np.array([0, 0, -1]), np.array([0, -1, 0]), (z1 - z0, y1 - y0)
    if face == 'west':
        return np.array([x0, y1, z0]), np.array([0, 0, 1]), np.array([0, -1, 0]), (z1 - z0, y1 - y0)
    if face == 'up':
        return np.array([x0, y1, z0]), np.array([1, 0, 0]), np.array([0, 0, 1]), (x1 - x0, z1 - z0)
    if face == 'down':
        return np.array([x0, y0, z1]), np.array([1, 0, 0]), np.array([0, 0, -1]), (x1 - x0, z1 - z0)
    raise ValueError(face)


def rotmat(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class El:
    _seed = 0

    def __init__(self, frm, to, mat, rot=None, faces=None, grain=None, seed=None, round_axis=None, paint=None,
                 params=None, name=''):
        self.frm = np.array(frm, float)
        self.to = np.array(to, float)
        assert (self.to >= self.frm).all(), (name, frm, to)
        self.mat = mat
        self.rot = rot  # (axis, angle, origin)
        self.faces = set(faces) if faces else set(FACE_NAMES)
        if grain is None:
            d = self.to - self.frm
            grain = int(np.argmax(d))
        self.grain = grain
        El._seed += 1
        self.seed = El._seed * 7919 if seed is None else seed
        self.round_axis = round_axis
        self.paint = paint or {}
        self.params = params or {}
        self.name = name
        self.obounds = (self.frm.copy(), self.to.copy())
        self.oid = id(self)  # bounds before any split (for solid-texture params)
        if rot:
            self.R = rotmat(rot[0], rot[1])
            self.O = np.array(rot[2], float)
        else:
            self.R = None
            self.O = None

    def copy_with(self, frm, to):
        e = El.__new__(El)
        e.__dict__.update(self.__dict__)
        e.frm = np.array(frm, float)
        e.to = np.array(to, float)
        e.faces = set(self.faces)
        return e

    def to_world(self, P):
        if self.R is None:
            return P
        return (P - self.O) @ self.R.T + self.O

    def to_local(self, P):
        if self.R is None:
            return P
        return (P - self.O) @ self.R + self.O

    def nrm_world(self, n):
        n = np.array(n, float)
        return n if self.R is None else self.R @ n

    def world_aabb(self):
        c = np.array([[self.frm[0] if i & 1 == 0 else self.to[0], self.frm[1] if i & 2 == 0 else self.to[1],
                       self.frm[2] if i & 4 == 0 else self.to[2]] for i in range(8)])
        w = self.to_world(c)
        return w.min(0), w.max(0)


# ----------------------------------------------------------------------------------------------- geometry helpers
class Scene:
    def __init__(self, name):
        self.name = name
        self.els = []

    def box(self, frm, to, mat, **kw):
        e = El(frm, to, mat, **kw)
        self.els.append(e)
        return e

    def cyl(self, axis, center, r, a0, a1, mat, notch=0.72, **kw):
        """rounded bar along axis ('x','y','z'): a chamfered-square cross-section built from a core box plus two
        thin side strips (exact union, no overlapping coplanar caps), with round shading baked in"""
        ai = 'xyz'.index(axis)
        oi = [i for i in range(3) if i != ai]
        kw = dict(kw)
        kw.setdefault('grain', ai)
        kw.setdefault('round_axis', (ai, (center[0], center[1]), r))
        out = []
        if r < 0.75:
            parts = [((r, r),)]
            spans = [(-r, r, -r, r)]
        else:
            rn = r * notch
            spans = [(-r, r, -rn, rn), (-rn, rn, rn, r), (-rn, rn, -r, -rn)]
        for (p0, p1, q0, q1) in spans:
            f = [0, 0, 0]
            t = [0, 0, 0]
            f[ai], t[ai] = a0, a1
            f[oi[0]], t[oi[0]] = center[0] + p0, center[0] + p1
            f[oi[1]], t[oi[1]] = center[1] + q0, center[1] + q1
            out.append(self.box(f, t, mat, **kw))
        return out


def split(els, at=16.0):
    left, right = [], []
    for e in els:
        lo, hi = e.world_aabb()
        if hi[0] <= at + 1e-6:
            left.append(e)
        elif lo[0] >= at - 1e-6:
            right.append(e)
        else:
            assert e.R is None or e.rot[0] == 'x', ('rotated element straddles split', e.name)
            if e.R is not None:
                raise AssertionError('rotated element straddles split: ' + e.name)
            a = e.copy_with(e.frm, [at, e.to[1], e.to[2]])
            b = e.copy_with([at, e.frm[1], e.frm[2]], e.to)
            left.append(a)
            right.append(b)
    return left, right


def inside_any(P, els, skip=None, eps=1e-4, skip_oid=None):
    """bool mask: world points inside (strictly, by eps) any element"""
    m = np.zeros(len(P), bool)
    for e in els:
        if e is skip or (skip_oid is not None and e.oid == skip_oid):
            continue
        lo, hi = e.world_aabb()
        pre = ((P > lo - eps) & (P < hi + eps)).all(1)
        if not pre.any():
            continue
        L = e.to_local(P[pre])
        inn = ((L > e.frm + eps) & (L < e.to - eps)).all(1)
        idx = np.nonzero(pre)[0]
        m[idx[inn]] = True
    return m


def cull_faces(els):
    """drop faces fully covered by other elements"""
    removed = 0
    for e in els:
        for face in list(e.faces):
            o, ud, vd, (fs, ft) = face_frame(e.frm, e.to, face)
            if fs < 1e-6 or ft < 1e-6:
                e.faces.discard(face)
                continue
            ns, nt = max(2, int(fs * 4) + 1), max(2, int(ft * 4) + 1)
            ss = np.linspace(min(0.05, fs / 4), fs - min(0.05, fs / 4), ns)
            ts = np.linspace(min(0.05, ft / 4), ft - min(0.05, ft / 4), nt)
            S, T = np.meshgrid(ss, ts)
            P = o + S.reshape(-1, 1) * ud + T.reshape(-1, 1) * vd + np.array(NORMALS[face]) * 0.06
            W = e.to_world(P)
            if inside_any(W, els, skip=e).all():
                e.faces.discard(face)
                removed += 1
    return removed


# ----------------------------------------------------------------------------------------------- ambient occlusion
def _hemi_dirs(n=40, seed=3):
    rng = np.random.default_rng(seed)
    k = int(math.sqrt(n))
    out = []
    for i in range(k):
        for j in range(k):
            u1 = (i + rng.random()) / k
            u2 = (j + rng.random()) / k
            r = math.sqrt(u1)
            th = 2 * math.pi * u2
            out.append((r * math.cos(th), r * math.sin(th), math.sqrt(max(0, 1 - u1))))
    return np.array(out)


HEMI = _hemi_dirs(36)


def ao_trace(Pw, nw, cands, maxd=5.0, seed=0):
    """Pw (n,3) world points, nw (3,) world normal; returns ambient visibility 0..1"""
    n = len(Pw)
    if n == 0:
        return np.ones(0)
    nw = nw / np.linalg.norm(nw)
    a = np.array([1, 0, 0]) if abs(nw[0]) < 0.9 else np.array([0, 1, 0])
    t1 = np.cross(nw, a)
    t1 /= np.linalg.norm(t1)
    t2 = np.cross(nw, t1)
    rng = np.random.default_rng(seed)
    phi = rng.random(n) * 2 * math.pi
    c, s = np.cos(phi)[:, None], np.sin(phi)[:, None]
    hx, hy, hz = HEMI[:, 0][None, :], HEMI[:, 1][None, :], HEMI[:, 2][None, :]
    rx = hx * c - hy * s
    ry = hx * s + hy * c
    D = rx[..., None] * t1 + ry[..., None] * t2 + hz[..., None] * nw  # (n,k,3)
    O = np.broadcast_to((Pw + nw * 0.02)[:, None, :], D.shape)
    O = O.reshape(-1, 3)
    D = D.reshape(-1, 3)
    tmin = np.full(len(D), maxd)
    for e in cands:
        if e.R is not None:
            Ol = (O - e.O) @ e.R + e.O
            Dl = D @ e.R
        else:
            Ol, Dl = O, D
        with np.errstate(divide='ignore', invalid='ignore'):
            inv = 1.0 / np.where(np.abs(Dl) < 1e-9, 1e-9, Dl)
            ta = (e.frm - Ol) * inv
            tb = (e.to - Ol) * inv
        tn = np.minimum(ta, tb).max(1)
        tf = np.maximum(ta, tb).min(1)
        hit = (tf >= np.maximum(tn, 1e-3)) & (tn < tmin)
        hit &= tn > 0.0
        tmin = np.where(hit, tn, tmin)
    # ground plane y = 0
    gd = D[:, 1] < -1e-6
    tg = np.where(gd, O[:, 1] / np.maximum(-D[:, 1], 1e-6), maxd)
    tmin = np.minimum(tmin, np.where(gd & (O[:, 1] > -0.01), tg, maxd))
    occ = np.clip(1 - tmin / maxd, 0, 1) ** 0.7
    return 1 - occ.reshape(n, -1).mean(1)


# ----------------------------------------------------------------------------------------------- materials
class Mat:
    """albedo(ctx) -> (n,3).  ctx: dict(P local pts, W world pts, s, t (face coords px), fs, ft, face, n (local
    normal), el)."""

    def __init__(self, fn, edge=0.22, seam=0.22, ao=0.85, chip=None, rim=0.0, gloss=0.0, name=''):
        self.fn = fn
        self.edge = edge
        self.seam = seam
        self.ao = ao
        self.chip = chip
        self.rim = rim
        self.gloss = gloss
        self.name = name


# ----------------------------------------------------------------------------------------------- bake
class Baker:
    def __init__(self, els, atlas_w=256):
        self.els = els
        self.W = atlas_w

    def bake(self, ao=True, verbose=True):
        faces = []
        for e in self.els:
            for f in sorted(e.faces):
                o, ud, vd, (fs, ft) = face_frame(e.frm, e.to, f)
                tw, th = max(1, int(math.ceil(fs * TPP - 1e-6))), max(1, int(math.ceil(ft * TPP - 1e-6)))
                faces.append([e, f, tw, th])
        # shelf packing (2 texel gutter all round)
        G = 1
        faces.sort(key=lambda r: (-r[3], -r[2]))
        x = y = 0
        shelf = 0
        W = self.W
        for r in faces:
            w, h = r[2] + 2 * G, r[3] + 2 * G
            if x + w > W:
                x = 0
                y += shelf
                shelf = 0
            r.append((x + G, y + G))
            x += w
            shelf = max(shelf, h)
        H = y + shelf
        size = W
        while size < H:
            size *= 2
        if size > W:
            # re-pack wider (square atlas)
            self.W = size
            return self.bake(ao, verbose)
        self.size = size
        img = np.zeros((size, size, 3), np.float32)
        filled = np.zeros((size, size), bool)
        self.uv = {}
        k = size / 16.0
        for e, f, tw, th, (px, py) in faces:
            o, ud, vd, (fs, ft) = face_frame(e.frm, e.to, f)
            col = self.bake_face(e, f, tw, th, o, ud, vd, fs, ft, ao)
            img[py:py + th, px:px + tw] = col
            filled[py:py + th, px:px + tw] = True
            self.uv[(id(e), f)] = [px / k, py / k, (px + fs * TPP) / k, (py + ft * TPP) / k]
        # bleed gutters
        idx = ndimage.distance_transform_edt(~filled, return_distances=False, return_indices=True)
        img = img[idx[0], idx[1]]
        self.img = np.clip(img, 0, 1)
        if verbose:
            print(f'  atlas {size}x{size}, {len(faces)} faces, fill {filled.mean():.2f}')
        return self.img

    def bake_face(self, e, f, tw, th, o, ud, vd, fs, ft, do_ao):
        n = np.array(NORMALS[f], float)
        # texel centres (and 2x2 supersamples for the albedo)
        sx = (np.arange(tw) + 0.5) / TPP
        sy = (np.arange(th) + 0.5) / TPP
        S, T = np.meshgrid(sx, sy)
        S = np.minimum(S, fs - 1e-3)
        T = np.minimum(T, ft - 1e-3)
        cols = 0
        for ox, oy in ((-0.25, -0.25), (0.25, -0.25), (-0.25, 0.25), (0.25, 0.25)):
            Ss = np.clip(S + ox / TPP, 0, fs)
            Ts = np.clip(T + oy / TPP, 0, ft)
            P = o + Ss.reshape(-1, 1) * ud + Ts.reshape(-1, 1) * vd
            ctx = dict(P=P, W=e.to_world(P), s=Ss.ravel(), t=Ts.ravel(), fs=fs, ft=ft, face=f, n=n, el=e, ud=ud, vd=vd)
            fn = e.paint.get(f) or e.paint.get('*')
            c = fn(ctx) if fn else e.mat.fn(ctx)
            cols = cols + c
        col = cols / 4.0
        P = o + S.reshape(-1, 1) * ud + T.reshape(-1, 1) * vd
        Wp = e.to_world(P)
        s, t = S.ravel(), T.ravel()
        mat = e.mat
        # --- edges: distance to the 4 face edges; classify exposed (wear highlight) vs seam (flush neighbour)
        d = np.stack([s, fs - s, t, ft - t], 1)
        ei = np.argmin(d, 1)
        dm = d[np.arange(len(d)), ei]
        near = dm < 0.9
        edge_k = 0.2 if e.round_axis is not None else 1.0
        if near.any() and (mat.edge or mat.seam or mat.chip is not None):
            out_dir = np.array([-ud, ud, -vd, vd])[ei]
            Pe = P.copy()
            Pe[ei == 0] -= np.outer(s[ei == 0], ud)
            Pe[ei == 1] += np.outer(fs - s[ei == 1], ud)
            Pe[ei == 2] -= np.outer(t[ei == 2], vd)
            Pe[ei == 3] += np.outer(ft - t[ei == 3], vd)
            q_flush = e.to_world(Pe + out_dir * 0.3 - n * 0.3)
            q_above = e.to_world(Pe + out_dir * 0.3 + n * 0.3)
            m = near.copy()
            flush = np.zeros(len(P), bool)
            above = np.zeros(len(P), bool)
            flush[m] = inside_any(q_flush[m], self.els, skip=e, skip_oid=e.oid)
            above[m] = inside_any(q_above[m], self.els, skip=e, skip_oid=e.oid)
            w = np.clip(1 - dm / 0.75, 0, 1)
            # irregular, worn edge
            jit = fbm(Wp[:, 0] * 0.9, Wp[:, 1] * 0.9, Wp[:, 2] * 0.9, seed=e.seed % 997, octaves=3)
            w = w * smoothstep(0.25, 0.65, jit + 0.15)
            exposed = near & ~flush & ~above
            seam = near & flush & ~above
            if mat.chip is not None:
                chipm = exposed & (jit > 0.66) & (dm < 0.4)
                col[chipm] = col[chipm] * 0.3 + np.array(mat.chip) * 0.7
            col[exposed] *= (1 + mat.edge * edge_k * w[exposed])[:, None]
            ws = np.clip(1 - dm / 0.5, 0, 1)
            col[seam] *= (1 - mat.seam * ws[seam])[:, None]
        # --- sky term: side faces a touch lighter toward the top of the part, darker toward its foot
        if f not in ('up', 'down'):
            h = e.to[1] - e.frm[1]
            if h > 0.6:
                k = (P[:, 1] - e.frm[1]) / h
                col *= (0.93 + 0.11 * k)[:, None]
        elif f == 'down':
            col *= 0.9
        # --- round shading for bars / cylinders
        if e.round_axis is not None:
            ai, (c0, c1), r = e.round_axis
            oi = [i for i in range(3) if i != ai]
            dv = np.stack([P[:, oi[0]] - c0, P[:, oi[1]] - c1], 1) / max(r, 1e-3)
            fn2 = np.array([n[oi[0]], n[oi[1]]])
            if np.abs(fn2).sum() > 0.5:  # side face of the bar
                lat = np.abs(dv @ np.array([-fn2[1], fn2[0]]))  # lateral offset across the face, 0 centre .. 1 rim
                lat = np.clip(lat, 0, 1)
                col *= (1.06 - 0.38 * lat ** 2)[:, None]
                if mat.gloss:
                    hl = np.exp(-((lat - 0.35) / 0.16) ** 2)
                    col += mat.gloss * hl[:, None] * 0.6
        # --- ambient occlusion (bench + ground)
        if do_ao:
            nw = e.nrm_world(n)
            lo, hi = Wp.min(0) - 5.2, Wp.max(0) + 5.2
            cands = []
            for o2 in self.els:
                a, b = o2.world_aabb()
                if (b > lo).all() and (a < hi).all() and o2 is not e:
                    cands.append(o2)
            vis = ao_trace(Wp, nw, cands, seed=e.seed % 10007)
            vis = vis.reshape(th, tw)
            if tw > 2 and th > 2:
                vis = ndimage.uniform_filter(vis, 3, mode='nearest') * 0.6 + vis * 0.4
            vis = vis.ravel()
            # ambient term: open faces 1.0, deep crevices ~0.3
            col *= (1 - mat.ao * (1 - vis) * 1.15).clip(0.25, 1)[:, None]
        return np.clip(col, 0, 1).reshape(th, tw, 3)


# ----------------------------------------------------------------------------------------------- export
def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def el_json(e, uvmap, tex='#atlas', dx=0.0):
    j = {'from': [r4(e.frm[0] + dx), r4(e.frm[1]), r4(e.frm[2])], 'to': [r4(e.to[0] + dx), r4(e.to[1]), r4(e.to[2])]}
    if e.rot:
        ax, ang, org = e.rot
        j['rotation'] = {'angle': ang, 'axis': ax, 'origin': [r4(org[0] + dx), r4(org[1]), r4(org[2])]}
    faces = {}
    for f in FACE_NAMES:
        if f not in e.faces:
            continue
        fj = {'uv': [r4(v) for v in uvmap[(id(e), f)]], 'texture': tex}
        if e.R is None:
            # cull against neighbouring full blocks when the face lies on the block boundary
            lo = e.frm + np.array([dx, 0, 0])
            hi = e.to + np.array([dx, 0, 0])
            inblock = hi[1] <= 16 + 1e-6
            if f == 'down' and abs(lo[1]) < 1e-6:
                fj['cullface'] = 'down'
            elif f == 'north' and abs(lo[2]) < 1e-6 and inblock:
                fj['cullface'] = 'north'
            elif f == 'south' and abs(hi[2] - 16) < 1e-6 and inblock:
                fj['cullface'] = 'south'
            elif f == 'west' and abs(lo[0]) < 1e-6 and inblock:
                fj['cullface'] = 'west'
            elif f == 'east' and abs(hi[0] - 16) < 1e-6 and inblock:
                fj['cullface'] = 'east'
        faces[f] = fj
    j['faces'] = faces
    return j


def write_bench(repo, bid, els, baker, wood_tile_fn, credit='FormaWorks / Frontier Hunts (procedural, tools/benches)'):
    A = os.path.join(repo, 'patch', 'assets', 'frontierhunts')
    mdir = os.path.join(A, 'models', 'block')
    tdir = os.path.join(A, 'textures', 'block', 'bench')
    os.makedirs(mdir, exist_ok=True)
    os.makedirs(tdir, exist_ok=True)
    Image.fromarray((baker.img * 255 + 0.5).astype(np.uint8)).save(os.path.join(tdir, f'{bid}_atlas.png'), optimize=True)
    wood_tile_fn(os.path.join(tdir, f'{bid}_wood.png'))
    textures = {'atlas': f'frontierhunts:block/bench/{bid}_atlas', 'particle': f'frontierhunts:block/bench/{bid}_wood'}
    left = [e for e in els if e.world_aabb()[1][0] <= 16 + 1e-6]
    right = [e for e in els if e not in left]

    def model(parts, dx, inv=False):
        out = []
        for e in parts:
            if not e.faces:
                continue
            j = el_json(e, baker.uv, dx=dx)
            if inv:
                for fj in j['faces'].values():
                    fj.pop('cullface', None)
            out.append(j)
        return out

    counts = {}
    for nm, parts, dx in (('left', left, 0.0), ('right', right, -16.0)):
        body = {'credit': credit, 'ambientocclusion': True, 'textures': textures, 'elements': model(parts, dx)}
        counts[nm] = len(body['elements'])
        with open(os.path.join(mdir, f'{bid}_{nm}.json'), 'w') as fh:
            json.dump(body, fh, separators=(',', ':'))
    inv = {'credit': credit, 'ambientocclusion': True, 'textures': textures, 'elements': model(left, 0, True) + model(right, 0, True)}
    counts['inventory'] = len(inv['elements'])
    with open(os.path.join(mdir, f'{bid}_inventory.json'), 'w') as fh:
        json.dump(inv, fh, separators=(',', ':'))
    return counts
