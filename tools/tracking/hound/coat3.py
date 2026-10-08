"""[hound3] Procedural coats baked through the UV atlas from the 3D surface (so pattern and shading never break at
chart seams): short glossy hound hair with a faint lie along the body, form shading / ambient occlusion from the
sculpt's own distance field, black nose leather, dark eyes with a catch light, dark lips, nails and pads.

  redbone   solid deep mahogany red, darker topline, lighter underline and legs, darker muzzle
  bluetick  white coat thickly ticked blue-black (mottled blue), black head and ears, black saddle and body spots,
            tan brows, cheeks, chest, under the tail and ticked tan lower legs
"""
import math
import numpy as np
from scipy.spatial import cKDTree
from scipy import ndimage as ndi


def smooth01(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


class Noise:
    def __init__(self, seed):
        r = np.random.RandomState(seed)
        self.v = r.rand(256 * 3).astype(np.float64)
        self.p = r.permutation(256)

    def _h(self, i, j, k):
        p = self.p
        return self.v[p[(p[(p[i & 255] + j) & 255] + k) & 255] + 256 * 0]

    def value(self, P):
        P = np.asarray(P, float)
        i = np.floor(P).astype(np.int64); f = P - i
        u = f * f * (3 - 2 * f)
        x, y, z = i[:, 0], i[:, 1], i[:, 2]
        out = 0
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    w = (u[:, 0] if dx else 1 - u[:, 0]) * (u[:, 1] if dy else 1 - u[:, 1]) * (u[:, 2] if dz else 1 - u[:, 2])
                    out = out + w * self._h(x + dx, y + dy, z + dz)
        return out * 2 - 1

    def fbm(self, P, oct=4):
        s = 0; a = 0.5; f = 1.0; n = 0
        for _ in range(oct):
            s = s + a * self.value(P * f + 17.3 * n); n += 1; f *= 2.03; a *= 0.5
        return s


EYES = [np.array([0.037 * x, 0.792, -0.513]) for x in (-1, 1)]
NOSE = np.array([0.0, 0.776, -0.662])


def regions(P, N):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    r = {}
    r['head'] = smooth01(-z, 0.40, 0.45) * smooth01(y, 0.66, 0.70)
    r['muzzle'] = smooth01(-z, 0.53, 0.60) * smooth01(y, 0.68, 0.70)
    r['legs'] = 1 - smooth01(y, 0.16, 0.30)
    r['feet'] = 1 - smooth01(y, 0.05, 0.09)
    r['under'] = smooth01(-N[:, 1], 0.1, 0.6) * (1 - smooth01(y, 0.42, 0.52)) * smooth01(z, -0.40, -0.36)
    r['top'] = smooth01(N[:, 1], 0.35, 0.9) * smooth01(y, 0.52, 0.58) * (1 - r['head'])
    dn = np.linalg.norm((P - NOSE) / np.array([0.028, 0.024, 0.020]), axis=1)
    r['nose'] = (1 - smooth01(dn, 0.92, 1.12)) * smooth01(-N[:, 2], -0.2, 0.2)
    de = np.min([np.linalg.norm(P - e, axis=1) for e in EYES], axis=0)
    r['eye_d'] = de
    r['lip'] = smooth01(-z, 0.50, 0.56) * (1 - smooth01(y, 0.703, 0.716)) * smooth01(y, 0.69, 0.70) * smooth01(np.abs(x), 0.012, 0.02)
    r['lip'] = np.maximum(r['lip'], smooth01(-z, 0.47, 0.50) * (1 - smooth01(np.abs(y - 0.712), 0.002, 0.005)) * smooth01(np.abs(x), 0.018, 0.024))
    r['pad'] = (1 - smooth01(y, 0.004, 0.011)) * smooth01(-N[:, 1], 0.3, 0.7)
    return r


def nails(P, J):
    m = np.zeros(len(P))
    for s in 'lr':
        for leg in 'fb':
            c = J[leg + s + '_toe']
            d = (P[:, 2] - (c[2] - 0.046)) * -1          # how far past the toe front
            m = np.maximum(m, smooth01(d, -0.004, 0.003) * (1 - smooth01(P[:, 1], 0.012, 0.022)) * (np.abs(P[:, 0] - c[0]) < 0.03))
    return m


def bake_points(P, N, ao, kind, J, rng_seed=7):
    nz = Noise(11 if kind == 'redbone' else 23)
    R = regions(P, N)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    # hair: fine streaks stretched along the lie of the coat (tail-ward on the body, down the legs)
    # [hound4] hair lies tail-ward on the body and DOWN the legs / ears: stretch the noise along that direction
    sb = nz.fbm(P * np.array([520.0, 220.0, 90.0]), 2) * 0.5 + nz.value(P * np.array([900, 400, 160])) * 0.35
    sl = nz.fbm(P * np.array([420.0, 80.0, 420.0]) + 31.0, 2) * 0.5 + nz.value(P * np.array([800, 150, 800]) + 7.0) * 0.35
    lw = np.clip(R['legs'] * 1.2, 0, 1)
    streak = sb * (1 - lw) + sl * lw
    big = nz.fbm(P * 9.0, 3)
    if kind == 'redbone':
        base = np.array([0.77, 0.335, 0.15])     # [hound4] bright rich red (was 0.52, 0.205, 0.095)
        dark = np.array([0.60, 0.235, 0.10])
        light = np.array([0.86, 0.46, 0.24])
        col = base[None] * (1 + 0.06 * big[:, None])
        col = col * (1 - R['top'][:, None] * 0.3) + dark[None] * R['top'][:, None] * 0.3
        lt = np.clip(R['under'] * 0.7 + R['legs'] * 0.25, 0, 1)
        col = col * (1 - lt[:, None]) + light[None] * lt[:, None]
        # darker muzzle toward the nose, a touch of dark on the ear tips
        col = col * (1 - 0.35 * R['muzzle'][:, None] * smooth01(-z, 0.58, 0.65)[:, None])
        gloss = 0.16
    else:
        rng = np.random.RandomState(rng_seed)
        white = np.array([0.86, 0.87, 0.88])     # [hound4] brighter (Minecraft side lighting is 0.4-0.6)
        black = np.array([0.10, 0.10, 0.115])
        tan = np.array([0.74, 0.43, 0.20])
        col = np.tile(white, (len(P), 1)) * (1 + 0.04 * big[:, None])
        # ticking: thousands of tiny round ticks (blue-black), denser on the back and sides
        pts = P[rng.choice(len(P), size=min(len(P), 16000), replace=False)]
        rad = rng.uniform(0.0014, 0.0032, len(pts))
        tree = cKDTree(pts)
        d, i = tree.query(P, k=4)
        tick = np.zeros(len(P))
        for k in range(4):
            tick = np.maximum(tick, 1 - smooth01(d[:, k] / rad[i[:, k]], 0.75, 1.1))
        under = R['under']
        tick *= 0.95 - 0.5 * under
        col = col * (1 - tick[:, None]) + black[None] * tick[:, None]
        col = col * np.array([0.93, 0.96, 1.0])[None]       # the blue cast of a ticked coat
        # big black spots on the body + the saddle
        sp = rng.choice(len(P), 26, replace=False)
        spots = np.zeros(len(P))
        body = smooth01(y, 0.30, 0.40) * smooth01(z, -0.30, -0.22) * (1 - smooth01(z, 0.36, 0.42))
        for k in sp:
            c = P[k]; r = rng.uniform(0.018, 0.04)
            dd = np.linalg.norm(P - c, axis=1) / r + 0.35 * nz.value(P * 60 + k)
            spots = np.maximum(spots, 1 - smooth01(dd, 0.85, 1.05))
        spots *= body * (1 - under)
        saddle = smooth01(y, 0.50, 0.56) * smooth01(z, -0.12, -0.04) * (1 - smooth01(z, 0.20, 0.27)) * smooth01(N[:, 1] + 0.15 * big, 0.05, 0.25)
        headblack = np.clip(R['head'] * 1.0 + smooth01(-z, 0.30, 0.40) * smooth01(y, 0.62, 0.70), 0, 1)
        blk = np.clip(np.maximum(np.maximum(spots, saddle), headblack) + 0.0, 0, 1)
        col = col * (1 - blk[:, None]) + black[None] * blk[:, None]
        # tan points: brows, cheeks / lower muzzle, a little on the chest, under the tail, ticked lower legs
        brows = np.zeros(len(P))
        for e in EYES:
            c = e + np.array([0.004 * np.sign(e[0]), 0.016, 0.006])
            brows = np.maximum(brows, 1 - smooth01(np.linalg.norm((P - c) / np.array([0.011, 0.007, 0.011]), axis=1), 0.8, 1.05))
        cheek = R['head'] * (1 - smooth01(y, 0.735, 0.76)) * smooth01(-z, 0.47, 0.52) * (1 - smooth01(-z, 0.60, 0.64)) * smooth01(np.abs(x), 0.012, 0.022)
        chest = (1 - smooth01(np.linalg.norm((P - np.array([0, 0.43, -0.32])) / np.array([0.05, 0.05, 0.04]), axis=1), 0.7, 1.0))
        undertail = (1 - smooth01(np.linalg.norm((P - np.array([0, 0.50, 0.37])) / np.array([0.04, 0.05, 0.04]), axis=1), 0.7, 1.0))
        legtan = R['legs'] * (1 - tick * 0.6)
        tn = np.clip(np.maximum.reduce([brows, cheek, chest * 0.8, undertail * 0.8, legtan * 0.85]), 0, 1)
        col = col * (1 - tn[:, None]) + tan[None] * tn[:, None] * (1 + 0.05 * big[:, None])
        gloss = 0.14
    # ear leathers: a little darker and softer (both coats)
    col = col * (1 + 0.07 * streak[:, None])
    # form shading: ambient occlusion from the distance field + a soft sheen on top of the form
    sheen = smooth01(N[:, 1], 0.45, 0.95) * gloss
    # [hound4] gentler AO (was 0.62 + 0.38 ao: crevices went black in game) + a soft baked top light that keeps
    # the muscle / form readable under Minecraft's flat entity lighting (and still reads right under shaders)
    form = 0.86 + 0.20 * smooth01(N[:, 1], -0.6, 0.9)
    col = col * (0.80 + 0.20 * ao)[:, None] * form[:, None] * (1 + sheen[:, None])
    # nose leather, lips, nails, pads
    nose = R['nose']
    nose_col = np.array([0.055, 0.045, 0.045]) * (1 + 0.25 * nz.value(P * 1400)[:, None] ** 2)
    col = col * (1 - nose[:, None]) + nose_col * nose[:, None]
    lip = R['lip'] * (0.85 if kind == 'redbone' else 1.0)
    col = col * (1 - lip[:, None]) + np.array([0.07, 0.05, 0.05])[None] * lip[:, None]
    nl = nails(P, J)
    col = col * (1 - nl[:, None]) + np.array([0.13, 0.10, 0.085])[None] * nl[:, None]
    pd = R['pad']
    col = col * (1 - pd[:, None]) + np.array([0.06, 0.05, 0.05])[None] * pd[:, None]
    # eyes: dark rim (lids), deep brown iris, black pupil and a catch light up-front
    de = R['eye_d']
    rim = 1 - smooth01(de, 0.0105, 0.0135)
    col = col * (1 - rim[:, None] * 0.85) + np.array([0.05, 0.035, 0.03])[None] * rim[:, None] * 0.85
    iris = 1 - smooth01(de, 0.0094, 0.0102)
    eye_col = np.zeros((len(P), 3))
    for e in EYES:
        sx = np.sign(e[0])
        look = np.array([0.55 * sx, 0.05, -0.83]); look /= np.linalg.norm(look)
        v = P - e
        a = (v / (np.linalg.norm(v, axis=1, keepdims=True) + 1e-9)) @ look
        pupil = smooth01(a, 0.78, 0.86)
        ic = np.array([0.26, 0.13, 0.05]) * (0.7 + 0.3 * smooth01(a, 0.2, 0.75))[:, None]
        ic = ic * (1 - pupil[:, None]) + np.array([0.02, 0.015, 0.015])[None] * pupil[:, None]
        hl_dir = np.array([0.35 * sx, 0.55, -0.76]); hl_dir /= np.linalg.norm(hl_dir)
        hl = smooth01((v / (np.linalg.norm(v, axis=1, keepdims=True) + 1e-9)) @ hl_dir, 0.93, 0.97)
        ic = ic * (1 - hl[:, None]) + np.array([0.85, 0.85, 0.82])[None] * hl[:, None]
        mine = (np.sign(P[:, 0]) == sx)[:, None]
        eye_col = np.where(mine, ic, eye_col)
    col = col * (1 - iris[:, None]) + eye_col * iris[:, None]
    return np.clip(col, 0, 1)


def raster(Fuv, UV, P, N, res, grow=0.6):
    """texel -> (triangle, barycentric) -> 3D position / normal for every covered texel"""
    tx = []; pos = []; nrm = []
    UVp = UV * res
    for t in range(len(Fuv)):
        a, b, c = Fuv[t]
        q = UVp[[a, b, c]]
        x0 = int(math.floor(q[:, 0].min() - grow)); x1 = int(math.ceil(q[:, 0].max() + grow))
        y0 = int(math.floor(q[:, 1].min() - grow)); y1 = int(math.ceil(q[:, 1].max() + grow))
        x0 = max(x0, 0); y0 = max(y0, 0); x1 = min(x1, res - 1); y1 = min(y1, res - 1)
        if x1 < x0 or y1 < y0:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        den = (q[1, 1] - q[2, 1]) * (q[0, 0] - q[2, 0]) + (q[2, 0] - q[1, 0]) * (q[0, 1] - q[2, 1])
        if abs(den) < 1e-12:
            continue
        l1 = ((q[1, 1] - q[2, 1]) * (X - q[2, 0]) + (q[2, 0] - q[1, 0]) * (Y - q[2, 1])) / den
        l2 = ((q[2, 1] - q[0, 1]) * (X - q[2, 0]) + (q[0, 0] - q[2, 0]) * (Y - q[2, 1])) / den
        l3 = 1 - l1 - l2
        # include texels whose centre is within ~grow texel of the triangle (conservative): clamp the barycentrics
        e = grow / max(1e-6, math.sqrt(abs(den)))
        m = (l1 >= -e) & (l2 >= -e) & (l3 >= -e)
        if not m.any():
            continue
        L = np.stack([l1[m], l2[m], l3[m]], 1).clip(0, None)
        L /= L.sum(1, keepdims=True)
        tx.append(np.stack([Y[m].astype(int), X[m].astype(int)], 1))
        pos.append(L @ P[[a, b, c]])
        nrm.append(L @ N[[a, b, c]])
    tx = np.vstack(tx); pos = np.vstack(pos); nrm = np.vstack(nrm)
    nrm /= np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-12
    return tx, pos, nrm


def bake(Fuv, UV, P, N, kind, J, res, field_fn, ao_scale=1.0):
    tx, pos, nrm = raster(Fuv, UV, P, N, res)
    # one sample per texel (last write wins on the rare overlaps)
    key = tx[:, 0] * res + tx[:, 1]
    _, first = np.unique(key[::-1], return_index=True)
    sel = len(key) - 1 - first
    tx, pos, nrm = tx[sel], pos[sel], nrm[sel]
    # ambient occlusion from the sculpt's distance field (5 taps along the normal)
    ao = np.ones(len(pos))
    if field_fn is not None:
        occ = np.zeros(len(pos))
        for i, hgt in enumerate((0.006, 0.014, 0.026, 0.042, 0.064)):
            d = field_fn(pos + nrm * hgt * ao_scale)
            occ += (hgt * ao_scale - np.minimum(d, hgt * ao_scale)) / (hgt * ao_scale) * (0.5 ** i)
        ao = np.clip(1 - 0.9 * occ, 0, 1)
    col = bake_points(pos, nrm, ao, kind, J)
    img = np.zeros((res, res, 3)); filled = np.zeros((res, res), bool)
    img[tx[:, 0], tx[:, 1]] = col; filled[tx[:, 0], tx[:, 1]] = True
    _, (iy, ix) = ndi.distance_transform_edt(~filled, return_indices=True)
    img = img[iy, ix]
    return img, filled
