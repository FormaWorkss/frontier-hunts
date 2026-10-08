"""[1.1.2] Renders the SnowBench scene (snow mesh + blocks) the way the game draws it without shaders: the snow
texture times the baked vertex light, blocks with Minecraft's face shading.  usage: render.py <dir> <out.png> [view]"""
import sys, os, math
import numpy as np
from PIL import Image

D, OUT = sys.argv[1], sys.argv[2]
VIEW = sys.argv[3] if len(sys.argv) > 3 else 'a'
R = os.path.join(os.path.dirname(__file__), '../..')
TEX = np.asarray(Image.open(os.path.join(R, 'patch/assets/frontierhunts/textures/block/frontier_snow.png')).convert('RGB')).astype(np.float32) / 255
W, H = 1280, 800
img = np.zeros((H, W, 3), np.float32); img[:] = [0.62, 0.74, 0.88]
zb = np.full((H, W), np.inf, np.float32)

cams = {'a': ((-14, 30, -14), (22, 8, 22)), 'b': ((52, 22, -8), (20, 8, 20)), 'c': ((20, 16, -6), (20, 8, 14)), 'd': ((6, 14, 34), (16, 8, 18)), 'e': ((36, 14.5, 4), (20, 9, 26)), 'f': ((4, 11, 4), (16, 8, 16)), 'g': ((26, 19, 24), (33, 12, 34)), 'h': ((14, 14, 22), (20, 9, 30))}
eye, at = map(np.array, cams[VIEW])
fwd = at - eye; fwd = fwd / np.linalg.norm(fwd)
right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
up = np.cross(right, fwd)
F = 1.0 / math.tan(math.radians(62) / 2)


def project(P):
    q = P - eye
    x, y, z = q @ right, q @ up, q @ fwd
    sx = W / 2 + x / z * F * H / 2
    sy = H / 2 - y / z * F * H / 2
    return sx, sy, z


def tri(P, colfn):
    sx, sy, z = project(P)
    if (z < 0.1).any():
        return
    x0, x1 = int(max(0, math.floor(sx.min()))), int(min(W - 1, math.ceil(sx.max())))
    y0, y1 = int(max(0, math.floor(sy.min()))), int(min(H - 1, math.ceil(sy.max())))
    if x0 > x1 or y0 > y1:
        return
    X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    d = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
    if abs(d) < 1e-9:
        return
    l0 = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / d
    l1 = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / d
    l2 = 1 - l0 - l1
    m = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
    if not m.any():
        return
    iz = l0 / z[0] + l1 / z[1] + l2 / z[2]
    depth = 1 / iz
    sub = zb[y0:y1 + 1, x0:x1 + 1]
    m &= depth < sub
    if not m.any():
        return
    # perspective-correct weights
    w0, w1, w2 = l0 / z[0] * depth, l1 / z[1] * depth, l2 / z[2] * depth
    c = colfn(w0[m], w1[m], w2[m])
    sub[m] = depth[m]
    img[y0:y1 + 1, x0:x1 + 1][m] = c


# ---- blocks
blocks = {}
for line in open(os.path.join(D, 'blocks.txt')):
    x, y, z, b = map(int, line.split()); blocks[(x, y, z)] = b
snowset = set()
for line in open(os.path.join(D, 'snow.txt')):
    pass
FACES = [((0, 1, 0), 1.0, [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]), ((0, -1, 0), 0.5, [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
         ((0, 0, -1), 0.8, [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)]), ((0, 0, 1), 0.8, [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]),
         ((-1, 0, 0), 0.6, [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]), ((1, 0, 0), 0.6, [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)])]
COL = {1: (0.48, 0.48, 0.5), 10: (0.45, 0.33, 0.22), 11: (0.33, 0.24, 0.15), 12: (0.62, 0.48, 0.3)}
for (x, y, z), b in blocks.items():
    for n, sh, quad in FACES:
        if (x + n[0], y + n[1], z + n[2]) in blocks:
            continue
        P = np.array([[x + a, y + c, z + e] for a, c, e in quad], float)
        base = np.array(COL[b]) * sh
        if b == 10 and n[1] == 1:
            base = np.array([0.35, 0.5, 0.25])
        for t in ((0, 1, 2), (0, 2, 3)):
            if b == 10 and n[1] == 0:
                PP = P[list(t)]
                def cf(w0, w1, w2, PP=PP, base=base):
                    yy = w0 * PP[0, 1] + w1 * PP[1, 1] + w2 * PP[2, 1]
                    g = (yy - np.floor(yy - 1e-6)) > 0.8
                    out = np.tile(base, (len(w0), 1)); out[g] = np.array([0.35, 0.5, 0.25]) * sh
                    return out
                tri(PP, cf)
            else:
                tri(P[list(t)], lambda w0, w1, w2, base=base: np.tile(base, (len(w0), 1)))

# ---- snow
n = 0
for line in open(os.path.join(D, 'snow.txt')):
    v = np.array(line.split(), float).reshape(4, 9)
    P, UV, C = v[:, 0:3], v[:, 3:5], v[:, 8].astype(np.int64)
    rgb = np.stack([(C >> 16) & 255, (C >> 8) & 255, C & 255], 1) / 255.0
    for t in ((0, 1, 2), (0, 2, 3)):
        t = list(t)
        def cf(w0, w1, w2, uv=UV[t], cc=rgb[t]):
            u = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
            vv = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
            tx = TEX[(np.clip(vv, 0, 0.9999) * 512).astype(int), (np.clip(u, 0, 0.9999) * 512).astype(int)]
            c = w0[:, None] * cc[0] + w1[:, None] * cc[1] + w2[:, None] * cc[2]
            return tx * c
        tri(P[t], cf)
    n += 1
# soft fog with distance, like the game
fog = np.clip((zb - 30) / 50, 0, 1)[..., None]
img = img * (1 - fog) + np.array([0.62, 0.74, 0.88]) * fog
Image.fromarray((np.clip(img, 0, 1) ** (1 / 1.0) * 255).astype(np.uint8)).save(OUT)
print(OUT, n, 'snow quads')
