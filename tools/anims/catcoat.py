"""[anims] Big-cat coat pass: paints species markings and fur detail onto the existing scan coats, in 3D.

Each coat texel is mapped back onto the body (the mesh rasterised in UV space: bind-pose position, normal and the bone
that carries it), so markings land where they belong whatever the atlas layout, and the UVs, weights and rig stay as
they are. Per texel the pass multiplies / blends the scan colour (its own fur detail is kept):
  * all cats: a soft top-dark / belly-light countershade, baked cavity shading from the mesh's concavity (definition at
    the eyes, lips, elbows and between the legs), fine directional fur streaks (along the body, down the legs, along the
    tail) so the coat does not read as smooth plastic at 3-6 blocks;
  * cougar: warm tawny, cream (not white) muzzle and chin, dark brown-black whisker-pad edges, dark ear backs, dark
    tail tip;
  * lion: tawny body, mane darkening toward its back and underside, black tail tuft, dark ear backs, whisker spots;
  * panther: lifted from near black to a deep charcoal-brown (reads as black, keeps its form under Minecraft's flat
    lighting) with faint rosettes showing in the light;
  * cheetah: (scan tear lines kept) ringed, black-tipped tail.
The distant coat (_far, on the low body's UVs) is painted by the same 3D rules, so the look holds at range.
Sources are git master (gear.16) textures; output patch/.  usage: catcoat.py [species...] [--preview out.png]
"""
import os, sys, subprocess, io
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(REPO, 'tools', 'meshes'))
import render as R  # noqa: E402

TEX = 'patch/assets/frontierhunts/textures/entity/wildlife/real/'
BASE = '6cbba9d'


def source_tex(name):
    b = subprocess.run(['git', '-C', REPO, 'show', '%s:%s%s.png' % (BASE, TEX, name)], check=True, capture_output=True).stdout
    return np.asarray(Image.open(io.BytesIO(b)).convert('RGB')).astype(np.float32) / 255.0


def weld_normals_cavity(m):
    """per vertex: smooth normal on the welded surface and a concavity measure (+ = in a crease)"""
    P = m.pos.astype(np.float64)
    k = np.round(P / 1e-5).astype(np.int64)
    _, inv = np.unique(k, axis=0, return_inverse=True)
    inv = inv.ravel()
    n = inv.max() + 1
    Q = np.zeros((n, 3)); np.add.at(Q, inv, P); Q /= np.bincount(inv, minlength=n)[:, None]
    F = inv[m.tris]
    fn = np.cross(Q[F[:, 1]] - Q[F[:, 0]], Q[F[:, 2]] - Q[F[:, 0]])
    N = np.zeros_like(Q)
    for c in range(3):
        np.add.at(N, F[:, c], fn)
    N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-12
    # umbrella Laplacian along the normal (scale-free: over the mean edge length)
    S = np.zeros_like(Q); cnt = np.zeros(n); el = np.zeros(n)
    for a, b in ((0, 1), (1, 2), (2, 0)):
        u, v = F[:, a], F[:, b]
        np.add.at(S, u, Q[v]); np.add.at(S, v, Q[u])
        np.add.at(cnt, u, 1); np.add.at(cnt, v, 1)
        d = np.linalg.norm(Q[u] - Q[v], axis=1)
        np.add.at(el, u, d); np.add.at(el, v, d)
    L = S / np.maximum(cnt, 1)[:, None] - Q
    cav = (L * N).sum(1) / np.maximum(el / np.maximum(cnt, 1), 1e-6)
    # widen a little (two rings) so the shading is soft
    for _ in range(2):
        acc = np.zeros(n); c2 = np.zeros(n)
        for a, b in ((0, 1), (1, 2), (2, 0)):
            u, v = F[:, a], F[:, b]
            np.add.at(acc, u, cav[v]); np.add.at(acc, v, cav[u]); np.add.at(c2, u, 1); np.add.at(c2, v, 1)
        cav = 0.5 * cav + 0.5 * acc / np.maximum(c2, 1)
    return N[inv], cav[inv]


def bake(m, size):
    """per texel: position, normal, dominant bone, cavity; plus the mask of texels the body covers"""
    S = size
    pos = np.zeros((S, S, 3), np.float32); nrm = np.zeros((S, S, 3), np.float32)
    bone = np.full((S, S), -1, np.int16); cav = np.zeros((S, S), np.float32)
    strict = np.zeros((S, S), bool)
    N, C = weld_normals_cavity(m)
    dom = m.bone[np.arange(len(m.pos)), np.argmax(m.weight, 1)]
    U = m.uv * S - 0.5
    for a, b, c in m.tris:
        x0, y0 = U[a]; x1, y1 = U[b]; x2, y2 = U[c]
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-12:
            continue
        xa, xb = max(int(np.floor(min(x0, x1, x2))), 0), min(int(np.ceil(max(x0, x1, x2))), S - 1)
        ya, yb = max(int(np.floor(min(y0, y1, y2))), 0), min(int(np.ceil(max(y0, y1, y2))), S - 1)
        if xa > xb or ya > yb:
            continue
        gx, gy = np.meshgrid(np.arange(xa, xb + 1, dtype=np.float32), np.arange(ya, yb + 1, dtype=np.float32))
        w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
        w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
        w2 = 1 - w0 - w1
        e = -0.6 / max(abs(area), 1.0) ** 0.5   # a little conservative overlap so island edges are covered
        msk = (w0 >= e) & (w1 >= e) & (w2 >= e)
        inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not msk.any():
            continue
        sl = (slice(ya, yb + 1), slice(xa, xb + 1))
        W = np.stack([w0, w1, w2], -1)[msk]
        pos[sl][msk] = W @ m.pos[[a, b, c]]
        nn = W @ N[[a, b, c]]
        nrm[sl][msk] = nn / (np.linalg.norm(nn, axis=1, keepdims=True) + 1e-9)
        cav[sl][msk] = W @ C[[a, b, c]]
        bone[sl][msk] = dom[[a, b, c]][np.argmax(W, 1)]
        strict[sl] |= inside
    valid = bone >= 0
    return pos, nrm, bone, cav, valid, strict


# ------------------------------------------------------------------------------------------------ 3D noise
def vnoise(p, seed=0):
    """value noise on a 3D lattice, smooth, in [0,1]"""
    rng = np.random.default_rng(seed)
    perm = rng.permutation(4096)
    tbl = rng.random(4096).astype(np.float32)
    i = np.floor(p).astype(np.int64); f = p - i
    f = f * f * (3 - 2 * f)
    def h(dx, dy, dz):
        k = perm[(i[..., 0] + dx) & 4095]
        k = perm[(k + i[..., 1] + dy) & 4095]
        k = perm[(k + i[..., 2] + dz) & 4095]
        return tbl[k]
    x00 = h(0, 0, 0) * (1 - f[..., 0]) + h(1, 0, 0) * f[..., 0]
    x10 = h(0, 1, 0) * (1 - f[..., 0]) + h(1, 1, 0) * f[..., 0]
    x01 = h(0, 0, 1) * (1 - f[..., 0]) + h(1, 0, 1) * f[..., 0]
    x11 = h(0, 1, 1) * (1 - f[..., 0]) + h(1, 1, 1) * f[..., 0]
    y0 = x00 * (1 - f[..., 1]) + x10 * f[..., 1]
    y1 = x01 * (1 - f[..., 1]) + x11 * f[..., 1]
    return y0 * (1 - f[..., 2]) + y1 * f[..., 2]


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


class Body:
    """bone ids and head / tail geometry of one mesh"""

    def __init__(self, m):
        self.m = m
        nm = m.names
        self.id = {n: i for i, n in enumerate(nm)}
        dom = m.bone[np.arange(len(m.pos)), np.argmax(m.weight, 1)]
        hv = m.pos[dom == self.id['head']]
        self.nose_z = hv[:, 2].min()
        self.head_back = m.joint[self.id['neck']][2]
        self.head_len = self.head_back - self.nose_z
        self.head_top = hv[:, 1].max(); self.head_bot = hv[:, 1].min()
        self.nose_y = hv[hv[:, 2] < self.nose_z + 0.08 * self.head_len][:, 1].mean()
        tv = m.pos[(dom == self.id['tail1']) | (dom == self.id['tail2'])]
        j1 = m.joint[self.id['tail1']]
        d = np.linalg.norm(tv - j1, axis=1)
        self.tail_len = d.max()
        self.tail_root = j1
        self.tip = tv[np.argmax(d)]
        self.H = m.pos[:, 1].max()
        self.legs = [self.id[l + s] for l in ('fl', 'fr', 'bl', 'br') for s in ('_upper', '_lower', '_foot', '_toe')]
        self.body = [self.id[n] for n in ('pelvis', 'spine', 'chest', 'neck') if n in self.id]


def fur_dir(B, pos, bone):
    """unit fur direction per texel: back along the body, down the legs, along the tail, back over the head"""
    d = np.zeros_like(pos); d[..., 2] = 1.0
    legs = np.isin(bone, B.legs)
    d[legs] = (0.0, -1.0, 0.25)
    tail = np.isin(bone, [B.id['tail1'], B.id['tail2']])
    tv = pos[tail] - B.tail_root
    d[tail] = tv / (np.linalg.norm(tv, axis=-1, keepdims=True) + 1e-6)
    return d / (np.linalg.norm(d, axis=-1, keepdims=True) + 1e-6)


def streaks(pos, d, scale):
    """fine fur streaks: noise stretched along the fur direction"""
    # project: a coordinate frame where the fur direction is squashed (long streaks along it)
    q = pos / scale
    along = (q * d).sum(-1, keepdims=True)
    across = q - along * d
    p = across * 1.0 + along * d * 0.18
    n = vnoise(p * 1.0, 3) * 0.6 + vnoise(p * 2.1 + 7.3, 4) * 0.4
    return n


def paint(sp, m, tex, size):
    B = Body(m)
    pos, nrm, bone, cav, valid, strict = bake(m, size)
    tex = repad(tex, strict)
    tex = tex.copy()
    H = B.H
    out = tex.copy()
    hd = bone == B.id['head']
    # head-local coordinates: s = 0 at the nose tip, 1 at the back of the head
    s = (pos[..., 2] - B.nose_z) / max(B.head_len, 1e-3)
    ax = np.abs(pos[..., 0])
    # tail: 0 at the root, 1 at the tip
    tl = np.isin(bone, [B.id['tail1'], B.id['tail2']])
    tt = np.linalg.norm(pos - B.tail_root, axis=-1) / max(B.tail_len, 1e-3)
    up = nrm[..., 1]
    lum = tex.mean(-1, keepdims=True)

    def blend(col, w):
        nonlocal out
        w = np.clip(w, 0, 1)[..., None]
        if os.environ.get('COAT_DEBUG'):
            col = (1.0, 0.0, 0.0)
        # keep the scan's own fur variation: tint the target colour by the texel's relative brightness
        rel = np.clip(lum / max(float(lum[valid].mean()), 1e-3), 0.6, 1.4)
        out = out * (1 - w) + np.asarray(col, np.float32) * rel * w

    # ---- countershade: a touch darker along the back, lighter underneath (sky-lit / belly)
    body_y = pos[..., 1] / H
    shade = 1.0 + 0.10 * np.clip(-up, 0, 1) * (1 - body_y) - 0.08 * smoothstep(0.55, 0.95, body_y) * np.clip(up, 0, 1)
    # ---- cavity shading (creases darker, crests a hair lighter)
    c = np.clip(cav, -1.0, 1.5)
    shade *= 1.0 - 0.22 * smoothstep(0.05, 0.9, c) + 0.05 * smoothstep(0.1, 0.8, -c)
    # ---- fur streaks
    d = fur_dir(B, pos, bone)
    fs = streaks(pos, d, 0.012 * H / 0.9)
    shade *= 0.90 + 0.2 * fs
    out = out * shade[..., None]

    if sp == 'cougar':
        # warm the coat a little (scan tends to grey-tan)
        out = out * np.array([1.04, 1.0, 0.94], np.float32)
        # cream muzzle and chin: tone the stark scan white down to a warm cream
        white = smoothstep(0.55, 0.78, lum[..., 0]) * (hd | (bone == B.id['neck']) | (bone == B.id['chest']))
        cream = np.array([0.84, 0.74, 0.60], np.float32) * np.clip(lum / 0.85, 0.75, 1.1)
        out = out * (1 - 0.7 * white[..., None]) + cream * 0.7 * white[..., None]
        # dark whisker-pad edges: the upper lip's sides, just behind the nose
        yy = (pos[..., 1] - B.nose_y) / H
        lip = hd & (s > 0.03) & (s < 0.2) & (yy < 0.005) & (yy > -0.05)
        side = smoothstep(0.012 * H, 0.032 * H, ax)
        blend((0.17, 0.11, 0.08), 0.85 * lip * side * smoothstep(0.2, 0.13, s) * smoothstep(0.03, 0.07, s) * smoothstep(-0.05, -0.03, yy))
        # dark backs of the ears
        ears = hd & (pos[..., 1] > B.head_top - 0.07 * H) & (nrm[..., 2] > 0.15) & (ax > 0.03 * H) & (s < 0.95)
        blend((0.22, 0.16, 0.12), 0.8 * ears)
        # dark tail tip
        blend((0.17, 0.12, 0.09), 0.92 * tl * smoothstep(0.78, 0.9, tt))
    elif sp == 'lion':
        mane = np.isin(bone, [B.id['neck'], B.id['chest'], B.id['head']]) & (s > 0.55)
        # mane: deeper toward its back and underside, a tawny-gold front edge
        back = smoothstep(0.6, 1.4, s) * mane
        low = smoothstep(0.75, 0.45, pos[..., 1] / H) * mane
        out = out * (1 - 0.35 * np.clip(back + 0.6 * low, 0, 1))[..., None]
        out = out * (1 + 0.1 * (mane & (s < 0.8)))[..., None]
        blend((0.12, 0.08, 0.05), 0.95 * tl * smoothstep(0.82, 0.93, tt))   # the tuft
        ears = hd & (pos[..., 1] > B.head_top - 0.06 * H) & (nrm[..., 2] > 0.2) & (s < 0.85)
        blend((0.2, 0.14, 0.1), 0.7 * ears)
        # whisker spots: rows of small dark dots on the upper lip
        lip = hd & (s > 0.05) & (s < 0.28) & (pos[..., 1] < B.nose_y) & (pos[..., 1] > B.nose_y - 0.07 * H) & (ax > 0.015 * H)
        dots = smoothstep(0.72, 0.8, vnoise(pos * (55.0 / H), 9))
        blend((0.15, 0.1, 0.07), 0.55 * lip * dots)
    elif sp == 'panther':
        # lift from near black: a deep charcoal-brown that keeps its modelling under flat lighting
        base = np.array([0.21, 0.185, 0.17], np.float32)
        rel = np.clip(lum / max(float(lum[valid].mean()), 1e-3), 0.55, 1.6)
        eyes = smoothstep(0.28, 0.4, lum[..., 0]) * hd   # the yellow-green eyes stay as they are
        out = (base * rel * shade[..., None] + 0.25 * (out - lum)) * (1 - eyes[..., None]) + out * eyes[..., None]
        # faint rosettes (rings of darker fur), only on the body and upper legs
        q = pos * (16.0 / H)
        r1 = vnoise(q, 21); r2 = vnoise(q * 1.7 + 3.1, 22)
        ring = smoothstep(0.08, 0.0, np.abs(r1 - 0.62)) * smoothstep(0.3, 0.6, r2)
        bodyish = ~(hd | tl) & (pos[..., 1] > 0.25 * H)
        out = out * (1 - 0.28 * ring * bodyish)[..., None]
        # a slightly lighter, greyer muzzle and chin (the face reads)
        mz = hd & (s < 0.3) & (pos[..., 1] < B.nose_y)
        out = out + np.array([0.05, 0.05, 0.05], np.float32) * mz[..., None]
    elif sp == 'cheetah':
        # (the scan already carries crisp tear lines and face spots)
        # tail: dark rings over the last third, black tip
        ring = smoothstep(0.35, 0.6, np.sin(tt * 42.0)) * smoothstep(0.6, 0.7, tt)
        blend((0.07, 0.06, 0.05), 0.85 * tl * np.clip(ring + smoothstep(0.9, 0.95, tt), 0, 1))
    out = np.clip(out, 0, 1)
    return repad(out, strict)


def repad(tex, strict):
    """Seam fix: the scan coats carry their atlas background (a flat brown, or a noisy fill) right up to and a texel
    into the islands, and the game samples texels whose centres lie just outside a triangle along every UV seam: in game
    that drew thin brown cracks across the muzzle, chest and legs (the body read as patched together, not one smooth
    surface). Every texel outside the islands' interior (shrunk by one texel) takes the colour of the nearest interior
    texel instead."""
    core = ndimage.binary_erosion(strict, iterations=1, border_value=0)
    core |= strict & ~ndimage.binary_dilation(core, iterations=2)   # thin slivers keep their own texels
    idx = ndimage.distance_transform_edt(~core, return_distances=False, return_indices=True)
    return np.where(core[..., None], tex, tex[idx[0], idx[1]])


CATS = ('cougar', 'lion', 'panther', 'cheetah')
SEAMS = ('wolf', 'coyote', 'grizzly', 'black_bear', 'polar_bear', 'bison', 'boar', 'pronghorn')   # seam fix only


def run(sp, preview=None):
    for lod, name in (('ultra', sp), ('bal', sp + '_far')):
        m = R.load_mesh('%s_%s' % (sp, lod))
        tex = source_tex(name)
        size = tex.shape[0]
        if sp in CATS:
            res = paint(sp, m, tex, size)
        else:
            res = repad(tex, bake(m, size)[5])
        Image.fromarray((res * 255 + 0.5).astype(np.uint8)).save(os.path.join(REPO, TEX, name + '.png'), optimize=True)
        print('painted', name, res.shape)


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    for sp in args or CATS + SEAMS:
        run(sp)
