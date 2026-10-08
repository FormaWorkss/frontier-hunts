"""[1.1.0] Item icons for the rowboat and the jon boat, rendered from their own meshes (3/4 view, 32x32 with a dark
outline so they read in the hotbar).  usage: python3 gen_boat_icons.py <repo root>"""
import sys, os
import numpy as np
from PIL import Image, ImageFilter
R = sys.argv[1] if len(sys.argv) > 1 else '.'
sys.path.insert(0, os.path.join(R, 'tools/gunsmith'))
import fheq, boats

ATLAS = None
for cand in ('/tmp/claude-0/gunbench-base/assets/frontierhunts/textures/item/firearms_v2.png',
             os.path.join(R, 'patch/assets/frontierhunts/textures/item/firearms_v2.png')):
    if os.path.exists(cand):
        ATLAS = np.asarray(Image.open(cand).convert('RGB')).astype(float) / 255
        break


def rot(yaw, pitch):
    cy, sy = np.cos(np.radians(yaw)), np.sin(np.radians(yaw)); cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]); Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    return Rx @ Ry


def M4(R3, t=(0, 0, 0)):
    m = np.eye(4); m[:3, :3] = R3; m[:3, 3] = t; return m


def Ry4(d):
    c, s = np.cos(np.radians(d)), np.sin(np.radians(d)); return M4(np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]))


def render(name, mats, W=256):
    parts = fheq.read(os.path.join(R, f'patch/assets/frontierhunts/models/equipment/{name}_close.fheq'))
    T, C, U, N = [], [], [], []
    for p in parts:
        M = mats.get(p['id'], np.eye(4))
        if M is None:
            continue
        F = p['f']
        P = np.c_[F[:, :3], np.ones(len(F))] @ M.T
        NN = F[:, 3:6] @ M[:3, :3].T
        for tri in p['idx']:
            T.append(P[tri, :3]); U.append(F[tri, 6:8]); N.append(NN[tri].mean(0))
            c = p['c'][tri[0]]; C.append(np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255]) / 255)
    T = np.array(T); N = np.array(N); N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
    V = rot(-135, 38)
    P = T @ V.T
    lo, hi = P.reshape(-1, 3).min(0), P.reshape(-1, 3).max(0)
    scale = (W - 8) / max(hi[0] - lo[0], hi[1] - lo[1])
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    img = np.zeros((W, W, 4)); zb = np.full((W, W), -1e9)
    L = np.array([0.35, 0.8, 0.5]); L /= np.linalg.norm(L)
    for t in range(len(T)):
        q = P[t]
        xs = W / 2 + (q[:, 0] - cx) * scale; ys = W / 2 - (q[:, 1] - cy) * scale
        x0, x1 = int(max(0, np.floor(xs.min()))), int(min(W - 1, np.ceil(xs.max())))
        y0, y1 = int(max(0, np.floor(ys.min()))), int(min(W - 1, np.ceil(ys.max())))
        if x0 > x1 or y0 > y1: continue
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(d) < 1e-12: continue
        a = ((ys[1] - ys[2]) * (X - xs[2]) + (xs[2] - xs[1]) * (Y - ys[2])) / d
        b = ((ys[2] - ys[0]) * (X - xs[2]) + (xs[0] - xs[2]) * (Y - ys[2])) / d
        c = 1 - a - b
        m = (a >= 0) & (b >= 0) & (c >= 0)
        z = a * q[0, 2] + b * q[1, 2] + c * q[2, 2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= z > sub
        if not m.any(): continue
        n = N[t] if (N[t] @ V.T)[2] >= 0 else -N[t]
        sh = 0.55 + 0.6 * max(0.0, float(n @ L))
        if ATLAS is not None:
            uu = a * U[t][0, 0] + b * U[t][1, 0] + c * U[t][2, 0]
            vv = a * U[t][0, 1] + b * U[t][1, 1] + c * U[t][2, 1]
            tex = ATLAS[np.clip((vv * ATLAS.shape[0]).astype(int), 0, ATLAS.shape[0] - 1), np.clip((uu * ATLAS.shape[1]).astype(int), 0, ATLAS.shape[1] - 1)]
            col = tex * C[t] * sh
        else:
            col = np.broadcast_to(C[t] * sh, z.shape + (3,))
        sub[m] = z[m]
        blk = img[y0:y1 + 1, x0:x1 + 1]
        blk[m, :3] = col[m]
        blk[m, 3] = 1.0
    return img


def icon(img, out):
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA')
    small = im.resize((30, 30), Image.LANCZOS)
    a = np.asarray(small).astype(float)
    alpha = a[:, :, 3] / 255
    # crisp alpha, brighten a touch so the icon reads on the dark hotbar
    rgb = np.clip(a[:, :, :3] / np.maximum(alpha[:, :, None], 1e-3) * 1.12, 0, 255)
    solid = alpha > 0.45
    canvas = np.zeros((32, 32, 4))
    canvas[1:31, 1:31, :3] = rgb
    canvas[1:31, 1:31, 3] = np.where(solid, 255, 0)
    # one-pixel dark outline around the silhouette
    s = canvas[:, :, 3] > 0
    ring = np.zeros_like(s)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ring |= np.roll(np.roll(s, dx, 1), dy, 0)
    ring &= ~s
    canvas[ring, :3] = (28, 22, 18)
    canvas[ring, 3] = 255
    Image.fromarray(canvas.astype(np.uint8), 'RGBA').save(out)
    print(out)


oar = boats.ROW_LOCK_Z
t = boats.row_t(oar); b, k, g = boats.row_station(t)
py = g + boats.ROW_LOCK_UP
mats = {}
for pid, horn, sgn in ((1, 5, 1), (2, 6, -1)):
    base = np.eye(4); base[:3, 3] = (sgn * (b + 0.004), py, oar)
    if sgn < 0:
        base = base @ Ry4(180)
    mats[horn] = base @ Ry4(sgn * 72)
    mats[pid] = base @ Ry4(sgn * 72)
outdir = os.path.join(R, 'patch/assets/frontierhunts/textures/item')
icon(render('rowboat', {1: None, 2: None, 5: None, 6: None}), os.path.join(outdir, 'rowboat.png'))
icon(render('jon_boat', {}), os.path.join(outdir, 'jon_boat.png'))
