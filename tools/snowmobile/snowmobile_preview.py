#!/usr/bin/env python3
"""[1.2.5] Offline textured preview of an FHVM mesh (z-buffer rasteriser), for checking the snowmobile model."""
import math
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw


def load(path):
    d = open(path, 'rb').read()
    ver, n = struct.unpack('>ii', d[4:12])
    o = 12
    parts = []
    for _ in range(n):
        piv = struct.unpack('>3f', d[o:o + 12]); o += 12
        tex = struct.unpack('>i', d[o:o + 4])[0]; o += 4
        nt = struct.unpack('>i', d[o:o + 4])[0]; o += 4
        a = np.frombuffer(d[o:o + nt * 96], dtype='>f4').astype(np.float64).reshape(nt, 3, 8); o += nt * 96
        parts.append((piv, tex, a))
    return parts


def render(parts, texs, yaw, pitch, W=640, H=440, steer=0.0, bg=(196, 210, 226)):
    img = np.zeros((H, W, 3)) + np.array(bg) / 255.0
    zb = np.full((H, W), np.inf)
    allv = np.concatenate([p[2][:, :, :3].reshape(-1, 3) for p in parts]) / 16.0
    c = (allv.min(0) + allv.max(0)) / 2
    ext = (allv.max(0) - allv.min(0)).max()
    sc = W * 0.82 / ext
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    L = np.array([0.35, 0.85, 0.40]); L /= np.linalg.norm(L)
    T = [np.asarray(t.convert('RGBA'), np.float64) / 255.0 for t in texs]
    # draw translucent last
    order = sorted(range(len(parts)), key=lambda i: parts[i][1] == 2)
    for pi in order:
        piv, ti, a = parts[pi]
        tex = T[ti]
        th, tw = tex.shape[:2]
        P = a[:, :, :3] / 16.0
        if pi in (1, 2) and steer:
            pv = np.array(piv) / 16.0
            ca, sa = math.cos(steer), math.sin(steer)
            q = P - pv
            P = np.stack([q[..., 0] * ca + q[..., 2] * sa, q[..., 1], -q[..., 0] * sa + q[..., 2] * ca], -1) + pv
        UV = a[:, :, 3:5]
        N = a[:, :, 5:8]
        p = P - c
        x = p[..., 0] * cy - p[..., 2] * sy
        z0 = p[..., 0] * sy + p[..., 2] * cy
        y = p[..., 1] * cp - z0 * sp
        dep = -(p[..., 1] * sp + z0 * cp)
        SX = W / 2 + x * sc
        SY = H / 2 - y * sc
        for t in range(len(a)):
            xs, ys, ds = SX[t], SY[t], dep[t]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l2 = 1 - l0 - l1
            ins = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
            dd = l0 * ds[0] + l1 * ds[1] + l2 * ds[2]
            reg = zb[y0:y1 + 1, x0:x1 + 1]
            ok = ins & (dd < reg)
            if not ok.any():
                continue
            u = l0 * UV[t, 0, 0] + l1 * UV[t, 1, 0] + l2 * UV[t, 2, 0]
            v = l0 * UV[t, 0, 1] + l1 * UV[t, 1, 1] + l2 * UV[t, 2, 1]
            col = tex[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
            n = l0[..., None] * N[t, 0] + l1[..., None] * N[t, 1] + l2[..., None] * N[t, 2]
            n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
            # light in model space; view-facing two-sided
            lam = np.abs(n @ L)
            sh = 0.45 + 0.55 * lam if pi != 5 else np.ones_like(lam)
            rgb = col[..., :3] * sh[..., None]
            alpha = col[..., 3] if ti == 2 else np.ones_like(lam)
            if ti == 3:  # cutout (the snow coat)
                ok = ok & (col[..., 3] > 0.5)
            tgt = img[y0:y1 + 1, x0:x1 + 1]
            blend = rgb * alpha[..., None] + tgt * (1 - alpha[..., None])
            tgt[ok] = blend[ok]
            if ti != 2:
                reg[ok] = dd[ok]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def render_all(path, texpaths, out):
    parts = load(path)
    texs = [Image.open(t) for t in texpaths]
    views = [(0.8, 0.30, 0.0), (2.4, 0.25, 0.0), (math.pi / 2, 0.02, 0.0), (-0.6, 0.55, 0.35), (math.pi, 0.15, 0.0), (0.0, 0.2, 0.0)]
    ims = [render(parts, texs, y, p, steer=s) for y, p, s in views]
    W, H = ims[0].size
    sheet = Image.new('RGB', (W * 3, H * 2))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % 3) * W, (i // 3) * H))
    sheet.save(out)
    print('preview', out)


if __name__ == '__main__':
    render_all(sys.argv[1], sys.argv[2:-1], sys.argv[-1])
