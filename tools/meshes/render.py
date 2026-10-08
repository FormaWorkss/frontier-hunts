"""[meshes] Software renderer for the Ultra skinned wildlife meshes (.fhsk) and their posed dumps.

Shading modes (each view can use one):
  mc    - Minecraft's entity shader: per-VERTEX (Gouraud) two-light mix, ambient 0.4 + diffuse 0.6, coat texture
  sun   - per-pixel lambert from a low sun + sky fill (what a shader pack such as Photon does), coat texture
  clay  - per-pixel sun lighting on plain grey: shows every facet, crease and bump of the geometry itself

usage (stand-alone): python3 render.py <mesh.fhsk> <coat.png> <out.png> [mode]
The pose harness (tools/meshes/harness) writes posed meshes that pose_sheet.py renders with the same code.
"""
import gzip, struct, sys, os, math
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(REPO, 'tools', 'wingshot'))
import fhsk  # noqa: E402

PATCH = os.path.join(REPO, 'patch', 'assets', 'frontierhunts')


def mesh_path(name):
    return os.path.join(PATCH, 'models', 'wildlife', name + '.fhsk')


def load_mesh(name_or_path):
    p = name_or_path if name_or_path.endswith('.fhsk') else mesh_path(name_or_path)
    return fhsk.parse(open(p, 'rb').read())


def coat_for(species, far=False):
    sp = species
    if sp == 'hound':
        sp = 'hound_redbone'
    return os.path.join(PATCH, 'textures', 'entity', 'wildlife', 'real', sp + ('_far' if far else '') + '.png')


def load_tex(path):
    im = Image.open(path).convert('RGB')
    return np.asarray(im).astype(np.float32) / 255.0


def look_at(eye, target, up=(0, 1, 0)):
    eye = np.asarray(eye, float); target = np.asarray(target, float)
    f = target - eye; f /= np.linalg.norm(f)
    r = np.cross(f, up); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return np.stack([r, u, -f]), eye


L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)


def mc_light(N):
    d = np.maximum(0, N @ L0) + np.maximum(0, N @ L1)
    return np.minimum(1.0, 0.4 + 0.6 * d)


def render(P, N, UV, T, tex, eye, target, size=(640, 480), fov=32.0, mode='sun', sun=(0.55, 0.75, -0.35),
           img=None, zbuf=None, tint=None, vcol=None):
    """Rasterise one mesh. P/N: (n,3) model-space positions/normals, UV (n,2), T (t,3) int. Returns (img, zbuf)."""
    W, H = size
    if img is None:
        img = np.zeros((H, W, 3), np.float32) + np.array([0.80, 0.84, 0.88], np.float32)
        zbuf = np.full((H, W), np.inf, np.float32)
    R, e = look_at(eye, target)
    C = (P - e) @ R.T                     # camera space, looking down -z
    z = -C[:, 2]
    f = 0.5 * H / math.tan(math.radians(fov) / 2)
    sx = W / 2 + f * C[:, 0] / np.maximum(z, 1e-4)
    sy = H / 2 - f * C[:, 1] / np.maximum(z, 1e-4)
    sun = np.asarray(sun, float); sun /= np.linalg.norm(sun)
    Nn = N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-12)
    if mode == 'mc':
        vlight = mc_light(Nn)
    th, tw = tex.shape[:2] if tex is not None else (1, 1)
    for t in range(len(T)):
        a, b, c = T[t]
        if z[a] <= 0.05 or z[b] <= 0.05 or z[c] <= 0.05:
            continue
        x0, y0, x1, y1, x2, y2 = sx[a], sy[a], sx[b], sy[b], sx[c], sy[c]
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        xa, xb = int(max(min(x0, x1, x2), 0)), int(min(max(x0, x1, x2) + 1, W - 1))
        ya, yb = int(max(min(y0, y1, y2), 0)), int(min(max(y0, y1, y2) + 1, H - 1))
        if xa > xb or ya > yb:
            continue
        gx, gy = np.meshgrid(np.arange(xa, xb + 1) + 0.5, np.arange(ya, yb + 1) + 0.5)
        w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
        w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        # perspective-correct barycentrics
        iz = w0 / z[a] + w1 / z[b] + w2 / z[c]
        pw0, pw1, pw2 = w0 / z[a] / iz, w1 / z[b] / iz, w2 / z[c] / iz
        depth = 1.0 / iz
        sub = zbuf[ya:yb + 1, xa:xb + 1]
        m &= depth < sub
        if not m.any():
            continue
        if mode == 'mc':
            lum = (pw0 * vlight[a] + pw1 * vlight[b] + pw2 * vlight[c])[..., None]
        else:
            n = pw0[..., None] * Nn[a] + pw1[..., None] * Nn[b] + pw2[..., None] * Nn[c]
            n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-12
            # two-sided like the game's NO_CULL render type: a back face is lit with its flipped normal
            vdir = e - (pw0[..., None] * P[a] + pw1[..., None] * P[b] + pw2[..., None] * P[c])
            flip = np.sign((n * vdir).sum(-1, keepdims=True) + 1e-9)
            n = n * flip
            lam = np.maximum(0, n @ sun)
            sky = 0.5 + 0.5 * n[..., 1]
            lum = (0.25 + 0.95 * lam + 0.30 * sky)[..., None]
        if vcol is not None:
            col = pw0[..., None] * vcol[a] + pw1[..., None] * vcol[b] + pw2[..., None] * vcol[c]
        elif mode == 'clay' or tex is None:
            col = np.array([0.72, 0.70, 0.66], np.float32) if tint is None else np.asarray(tint, np.float32)
            col = np.broadcast_to(col, lum.shape[:2] + (3,))
        else:
            u = pw0 * UV[a, 0] + pw1 * UV[b, 0] + pw2 * UV[c, 0]
            v = pw0 * UV[a, 1] + pw1 * UV[b, 1] + pw2 * UV[c, 1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            col = tex[ty, tx]
        out = np.clip(col * lum, 0, 1)
        img[ya:yb + 1, xa:xb + 1][m] = out[m]
        sub[m] = depth[m]
    return img, zbuf


def views_for(P, h=None):
    """Close camera set around a mesh in model space (head toward -z)."""
    lo, hi = P.min(0), P.max(0)
    c = (lo + hi) / 2
    ext = max(hi - lo)
    d = ext * 1.85
    return {
        'side': (c + np.array([d, 0.05 * ext, 0]), c),
        '3/4': (c + np.array([d * 0.72, d * 0.32, -d * 0.72]), c),
        'front': (c + np.array([0.15 * d, 0.12 * ext, -d]), c + np.array([0, 0, -0.1 * ext])),
        'top': (c + np.array([0.25 * d, d * 1.0, 0.1 * d]), c),
        'head': (c + np.array([0.45 * d, 0.1 * ext, -0.75 * d]) * 1.0, np.array([c[0], hi[1] - 0.18 * (hi[1] - lo[1]), lo[2] + 0.12 * (hi[2] - lo[2])])),
    }


def label(im, text, xy=(6, 4)):
    d = ImageDraw.Draw(im)
    d.rectangle([xy[0] - 2, xy[1] - 1, xy[0] + 7 * len(text) + 2, xy[1] + 12], fill=(0, 0, 0))
    d.text(xy, text, fill=(255, 255, 255))
    return im


def sheet(tiles, cols):
    w, h = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (w * cols, h * rows), (40, 40, 40))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * w, (i // cols) * h))
    return out


def to_img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


def mesh_views(m, tex, modes=('sun', 'clay'), size=(480, 360), names=('side', '3/4', 'front', 'top', 'head'), P=None, N=None):
    P = m.pos if P is None else P
    N = m.nrm if N is None else N
    V = views_for(P)
    tiles = []
    for mode in modes:
        for vn in names:
            eye, tgt = V[vn]
            fov = 22.0 if vn == 'head' else 32.0
            img, _ = render(P, N, m.uv, m.tris, tex, eye, tgt, size=size, mode=mode, fov=fov)
            tiles.append(label(to_img(img), '%s %s' % (vn, mode)))
    return tiles


if __name__ == '__main__':
    mp, tp, out = sys.argv[1:4]
    mode = sys.argv[4] if len(sys.argv) > 4 else None
    m = load_mesh(mp)
    tex = load_tex(tp)
    tiles = mesh_views(m, tex, modes=(mode,) if mode else ('sun', 'clay'))
    sheet(tiles, 5).save(out)
