"""[sticks] Tiny software rasterizer for previews: perspective camera, z-buffer, nearest texture sampling, Minecraft's
entity lighting (two directional lights, 0.4 ambient + 0.6 diffuse), optional flat-coloured quads."""
import math

import numpy as np

L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)


class Cam:
    def __init__(self, eye, target, fov=40.0, w=640, h=480, up=(0, 1, 0)):
        self.eye = np.array(eye, float)
        f = np.array(target, float) - self.eye
        f /= np.linalg.norm(f)
        r = np.cross(f, np.array(up, float)); r /= np.linalg.norm(r)
        u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.w, self.h = w, h
        self.k = (h / 2) / math.tan(math.radians(fov) / 2)

    def project(self, p):
        d = np.asarray(p, float) - self.eye
        x, y, z = d @ self.r, d @ self.u, d @ self.f
        return self.w / 2 + x / z * self.k, self.h / 2 - y / z * self.k, z


class Canvas:
    def __init__(self, cam, bg=(0.62, 0.72, 0.80), ssaa=2):
        self.cam = cam
        self.s = ssaa
        W, H = cam.w * ssaa, cam.h * ssaa
        self.img = np.zeros((H, W, 3)) + np.array(bg)
        self.z = np.full((H, W), np.inf)

    def tri(self, P, UV, N, tex, color=None, light=1.0, alpha_cut=True, double=True):
        cam = self.cam
        s = self.s
        pr = [cam.project(p) for p in P]
        if min(p[2] for p in pr) < 0.02:
            return
        xs = np.array([p[0] * s for p in pr]); ys = np.array([p[1] * s for p in pr]); zs = np.array([p[2] for p in pr])
        x0, x1 = int(max(0, math.floor(xs.min()))), int(min(self.img.shape[1] - 1, math.ceil(xs.max())))
        y0, y1 = int(max(0, math.floor(ys.min()))), int(min(self.img.shape[0] - 1, math.ceil(ys.max())))
        if x0 > x1 or y0 > y1:
            return
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-9:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            return
        # perspective-correct interpolation
        iz = w0 / zs[0] + w1 / zs[1] + w2 / zs[2]
        z = 1 / iz
        zb = self.z[y0:y1 + 1, x0:x1 + 1]
        m &= z < zb
        if not m.any():
            return
        b0, b1, b2 = w0 / zs[0] * z, w1 / zs[1] * z, w2 / zs[2] * z
        n = np.array(N, float)
        nn = b0[..., None] * n[0] + b1[..., None] * n[1] + b2[..., None] * n[2]
        nn /= np.linalg.norm(nn, axis=-1, keepdims=True) + 1e-9
        if double:
            view = np.array(P[0]) - cam.eye
            flip = (nn @ (view / np.linalg.norm(view))) > 0
            nn[flip] *= -1
        sh = 0.4 + 0.6 * (np.clip(nn @ L0, 0, None) + np.clip(nn @ L1, 0, None))
        sh = np.minimum(sh, 1.0) * light
        if color is not None:
            c = np.zeros(z.shape + (3,)) + np.array(color)
            a = np.ones(z.shape)
        else:
            uv = np.array(UV, float)
            u = b0 * uv[0, 0] + b1 * uv[1, 0] + b2 * uv[2, 0]
            v = b0 * uv[0, 1] + b1 * uv[1, 1] + b2 * uv[2, 1]
            th, tw = tex.shape[:2]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            tc = tex[ty, tx]
            c = tc[..., :3]
            a = tc[..., 3] if tc.shape[-1] == 4 else np.ones(z.shape)
        if alpha_cut:
            m &= a > 0.1
        out = self.img[y0:y1 + 1, x0:x1 + 1]
        out[m] = (c * sh[..., None])[m]
        zb[m] = z[m]

    def quads(self, quads, tex, **kw):
        for q in quads:
            P = [v[:3] for v in q]
            UV = [v[3:5] for v in q]
            N = [v[5:8] for v in q]
            self.tri([P[0], P[1], P[2]], [UV[0], UV[1], UV[2]], [N[0], N[1], N[2]], tex, **kw)
            if P[2] != P[3]:
                self.tri([P[0], P[2], P[3]], [UV[0], UV[2], UV[3]], [N[0], N[2], N[3]], tex, **kw)

    def box(self, lo, hi, color, xf=None, light=1.0):
        x0, y0, z0 = lo; x1, y1, z1 = hi
        faces = [((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1)),
                 ((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1)),
                 ((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0)),
                 ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0)),
                 ((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), (0, 1, 0)),
                 ((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (0, -1, 0))]
        for f in faces:
            ps = [np.array(f[i], float) for i in range(4)]
            n = np.array(f[4], float)
            if xf is not None:
                o = xf(np.zeros(3))
                ps = [xf(p) for p in ps]
                n = xf(n) - o
                n /= np.linalg.norm(n)
            nl = [n] * 3
            self.tri([ps[0], ps[1], ps[2]], None, nl, None, color=color, light=light)
            self.tri([ps[0], ps[2], ps[3]], None, nl, None, color=color, light=light)

    def result(self):
        from PIL import Image
        H, W = self.img.shape[:2]
        im = Image.fromarray((np.clip(self.img, 0, 1) * 255).astype(np.uint8))
        return im.resize((W // self.s, H // self.s), Image.LANCZOS)
