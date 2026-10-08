"""[hound3] Small numpy rasterizer for offline previews: perspective camera, per-pixel normals (smooth shading),
textured or flat colour, a real ground plane at y = 0 with a grid (so floating / sunk paws are obvious) and a soft
contact shadow under the model."""
import math, numpy as np
from PIL import Image, ImageDraw


def look(yaw, pitch):
    t, p = math.radians(yaw), math.radians(pitch)
    Ry = np.array([[math.cos(t), 0, math.sin(t)], [0, 1, 0], [-math.sin(t), 0, math.cos(t)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    return Rx @ Ry


def render(P, T, N, UV=None, tex=None, color=(0.55, 0.3, 0.2), yaw=90, pitch=8, W=420, H=320, center=None, dist=None,
           fov=32.0, ground=True, label=None):
    """P (n,3) vertices, T (m,3) triangles, N (n,3) vertex normals, UV (n,2) (v down, image space) + tex (h,w,3/4) float"""
    P = np.asarray(P, float); N = np.asarray(N, float)
    c = np.array(center if center is not None else [(P[:, 0].max() + P[:, 0].min()) / 2, (P[:, 1].max() + P[:, 1].min()) / 2,
                                                     (P[:, 2].max() + P[:, 2].min()) / 2])
    size = np.max(P.max(0) - P.min(0))
    dist = dist or size * 2.4 + 0.2
    M = look(yaw, pitch)
    f = 1 / math.tan(math.radians(fov) / 2); asp = W / H
    img = np.zeros((H, W, 3)); zb = np.full((H, W), 1e9)
    # sky / ground by ray casting the y=0 plane
    gy, gx = np.mgrid[0:H, 0:W] + 0.5
    rx = (gx / W - 0.5) * 2 * asp / f; ry = (0.5 - gy / H) * 2 / f
    dirs = np.stack([rx, ry, -np.ones_like(rx)], -1) @ M          # camera -> world (M orthonormal: inverse = transpose)
    eye = c + np.array([0, 0, dist]) @ M
    img[:] = np.array([0.62, 0.74, 0.88])
    if ground:
        t = -eye[1] / np.where(np.abs(dirs[..., 1]) < 1e-9, -1e-9, dirs[..., 1])
        hit = (t > 0)
        gp = eye + dirs * t[..., None]
        chk = ((np.floor(gp[..., 0] / 0.25) + np.floor(gp[..., 2] / 0.25)) % 2)
        gcol = np.array([0.36, 0.47, 0.30]) * (0.92 + 0.08 * chk)[..., None]
        # contact shadow from the model's footprint
        foot = P[P[:, 1] < 0.12]
        if len(foot):
            sh = np.zeros(gp.shape[:2])
            for q in foot[:: max(1, len(foot) // 300)]:
                d2 = (gp[..., 0] - q[0]) ** 2 + (gp[..., 2] - q[2]) ** 2
                sh = np.maximum(sh, np.exp(-d2 / (2 * (0.03 + q[1] * 0.4) ** 2)) * (1 - q[1] / 0.12))
            gcol = gcol * (1 - 0.45 * sh[..., None])
        img[hit] = gcol[hit]
        zb[hit] = t[hit] * (-(dirs[hit] @ M.T)[:, 2])
    Pc = (P - c) @ M.T + np.array([0, 0, -dist])
    Nc = N @ M.T
    z = -Pc[:, 2]
    sx = (Pc[:, 0] / z * f / asp * 0.5 + 0.5) * W; sy = (0.5 - Pc[:, 1] / z * f * 0.5) * H
    L0 = np.array([0.35, 0.8, 0.5]); L0 /= np.linalg.norm(L0)
    L1 = np.array([-0.5, 0.3, -0.4]); L1 /= np.linalg.norm(L1)
    if tex is not None and tex.shape[2] > 3:
        tex = tex[..., :3]
    for (a, b, cc) in T:
        if z[a] < 0.05 or z[b] < 0.05 or z[cc] < 0.05:
            continue
        xs = np.array([sx[a], sx[b], sx[cc]]); ys = np.array([sy[a], sy[b], sy[cc]])
        x0 = max(int(xs.min()), 0); x1 = min(int(xs.max()) + 1, W - 1); y0 = max(int(ys.min()), 0); y1 = min(int(ys.max()) + 1, H - 1)
        if x0 > x1 or y0 > y1:
            continue
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-12:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        l1 = ((ys[1] - ys[2]) * (X - xs[2]) + (xs[2] - xs[1]) * (Y - ys[2])) / den
        l2 = ((ys[2] - ys[0]) * (X - xs[2]) + (xs[0] - xs[2]) * (Y - ys[2])) / den
        l3 = 1 - l1 - l2
        m = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
        if not m.any():
            continue
        iz = l1 / z[a] + l2 / z[b] + l3 / z[cc]; zz = 1 / iz
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= zz < sub
        if not m.any():
            continue
        w1, w2, w3 = l1 / z[a] * zz, l2 / z[b] * zz, l3 / z[cc] * zz
        n = w1[..., None] * Nc[a] + w2[..., None] * Nc[b] + w3[..., None] * Nc[cc]
        n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
        n = np.where((n[..., 2:3] < 0), n * np.array([1, 1, 1]), n)
        lit = 0.32 + 0.62 * np.clip(n @ (M @ L0), 0, 1) + 0.18 * np.clip(n @ (M @ L1), 0, 1)
        spec = np.clip(n @ (M @ L0) * 0 + n[..., 2], 0, 1) ** 24 * 0.10
        if tex is not None and UV is not None:
            u = w1 * UV[a, 0] + w2 * UV[b, 0] + w3 * UV[cc, 0]; v = w1 * UV[a, 1] + w2 * UV[b, 1] + w3 * UV[cc, 1]
            th, tw = tex.shape[:2]
            col = tex[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
        else:
            col = np.broadcast_to(np.array(color, float), n.shape)
        rgb = col * lit[..., None] + spec[..., None]
        sub[m] = zz[m]
        img[y0:y1 + 1, x0:x1 + 1][m] = rgb[m]
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype('uint8'))
    if label:
        ImageDraw.Draw(im).text((6, 4), label, fill=(20, 20, 20))
    return im


def sheet(views, cols=None):
    cols = cols or len(views)
    w, h = views[0].size
    rows = (len(views) + cols - 1) // cols
    out = Image.new('RGB', (w * cols, h * rows), (255, 255, 255))
    for i, v in enumerate(views):
        out.paste(v, ((i % cols) * w, (i // cols) * h))
    return out
