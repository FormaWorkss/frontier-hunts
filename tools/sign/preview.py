"""[sign] Offline preview of DeerSignRenderer's geometry (same formulas, ported) with a tiny z-buffered rasteriser:
a round realistic trunk (Ultra) and a block log (Classic/Balanced) each with a fresh and an old rub, and a scrape with its
licking branch. Writes docs/ws/sign/*.png. Lighting: simple lambert + ambient; textures sampled nearest (no mipmaps,
like entity textures)."""
import math, os, sys
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
ATL = np.asarray(Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/entity/deer_sign.png')).convert('RGBA')).astype(float)
AW, AH = 1024, 512
W, H = 900, 600


def rnd(seed, i):
    h = (seed * 0x9E3779B1 + i * 0x85EBCA6B) & 0xffffffff
    h ^= h >> 15; h = (h * 0x2C1B3C6D) & 0xffffffff; h ^= h >> 12; h = (h * 0x297A2D39) & 0xffffffff; h ^= h >> 15
    return (h >> 8) / float(1 << 24)


class Cam:
    def __init__(self, eye, look, fov=50):
        self.eye = np.array(eye, float); f = np.array(look, float) - self.eye; f /= np.linalg.norm(f)
        r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r); u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u; self.k = 0.5 * H / math.tan(math.radians(fov) / 2)

    def proj(self, p):
        d = np.asarray(p, float) - self.eye
        z = d @ self.f
        return np.array([W / 2 + (d @ self.r) / z * self.k, H / 2 - (d @ self.u) / z * self.k, z])


class Raster:
    def __init__(self, sky=(150, 175, 200)):
        self.img = np.zeros((H, W, 3)); self.img[:] = sky
        self.z = np.full((H, W), 1e9)

    def tri(self, cam, P, UV, tex, n, shade=1.0, cull=True):
        S = [cam.proj(p) for p in P]
        if min(s[2] for s in S) < 0.05:
            return
        (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = S
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            return
        if cull and area > 0:      # screen y down: counter-clockwise in world -> negative area here
            return
        xmin, xmax = int(max(0, math.floor(min(x0, x1, x2)))), int(min(W - 1, math.ceil(max(x0, x1, x2))))
        ymin, ymax = int(max(0, math.floor(min(y0, y1, y2)))), int(min(H - 1, math.ceil(max(y0, y1, y2))))
        if xmin > xmax or ymin > ymax:
            return
        ys, xs = np.mgrid[ymin:ymax + 1, xmin:xmax + 1] + 0.5
        w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
        w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
        w2 = 1 - w0 - w1
        m = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not m.any():
            return
        iz = w0 / z0 + w1 / z1 + w2 / z2
        z = 1 / iz
        u = (w0 * UV[0][0] / z0 + w1 * UV[1][0] / z1 + w2 * UV[2][0] / z2) * z
        v = (w0 * UV[0][1] / z0 + w1 * UV[1][1] / z1 + w2 * UV[2][1] / z2) * z
        th, tw = tex.shape[:2]
        tx = np.clip((u * tw).astype(int), 0, tw - 1); ty = np.clip((v * th).astype(int), 0, th - 1)
        c = tex[ty, tx]
        m &= c[..., 3] > 25
        zb = self.z[ymin:ymax + 1, xmin:xmax + 1]
        m &= z < zb
        L = np.array([0.45, 0.8, 0.35]); L /= np.linalg.norm(L)
        lam = 0.55 + 0.45 * max(0.0, float(np.dot(n, L)))
        col = c[..., :3] * lam * shade
        reg = self.img[ymin:ymax + 1, xmin:xmax + 1]
        reg[m] = col[m]; zb[m] = z[m]

    def quad(self, cam, p, uv, tex, n, shade=1.0, cull=True):
        # world winding p0..p3 CCW seen from n
        fn = np.cross(np.subtract(p[1], p[0]), np.subtract(p[3], p[0]))
        if np.dot(fn, n) < 0:
            p = [p[0], p[3], p[2], p[1]]; uv = [uv[0], uv[3], uv[2], uv[1]]
        self.tri(cam, [p[0], p[1], p[2]], [uv[0], uv[1], uv[2]], tex, n, shade, cull)
        self.tri(cam, [p[0], p[2], p[3]], [uv[0], uv[2], uv[3]], tex, n, shade, cull)


def atlas_uv(u, v):
    return (u / AW, v / AH)


def gen_bark(seed, w=256, h=256):
    r = np.random.default_rng(seed)
    from scipy import ndimage as ndi
    f = ndi.zoom(r.random((40, 8)), (h / 40, w / 8), order=3)[:h, :w]
    g = ndi.zoom(r.random((160, 40)), (h / 160, w / 40), order=1)[:h, :w]
    ridge = np.abs(f - 0.5) * 2
    base = np.array([96, 82, 68]) * (0.65 + 0.5 * ridge[..., None]) * (0.85 + 0.25 * g[..., None])
    return np.concatenate([np.clip(base, 0, 255), np.full((h, w, 1), 255)], 2)


def gen_grass(seed):
    r = np.random.default_rng(seed)
    from scipy import ndimage as ndi
    n = ndi.zoom(r.random((32, 32)), 8, order=3)
    g = np.array([88, 128, 58]) * (0.75 + 0.45 * n[..., None]) * (0.85 + 0.3 * r.random((256, 256, 1)))
    return np.concatenate([np.clip(g, 0, 255), np.full((256, 256, 1), 255)], 2)


def gen_oak16():
    r = np.random.default_rng(3)
    t = np.zeros((16, 16, 4)); t[..., 3] = 255
    for x in range(16):
        c = np.array([104, 82, 50]) * (0.75 + 0.4 * r.random())
        t[:, x, :3] = c * (0.85 + 0.3 * r.random((16, 1)))
    return t


FLARE = lambda h: 1 + 0.55 * math.exp(-max(0, h) / 0.45)


def draw_round_trunk(R, cam, cx, cz, r0, tex, height=4.0):
    sides = 10
    hs = np.arange(-0.3, height, 0.25)
    for i in range(len(hs) - 1):
        for k in range(sides):
            a0 = 2 * math.pi * k / sides; a1 = 2 * math.pi * (k + 1) / sides
            ra = r0 * (1 - 0.96 * max(0, hs[i]) / 12) * FLARE(hs[i]); rb = r0 * (1 - 0.96 * max(0, hs[i + 1]) / 12) * FLARE(hs[i + 1])
            p = [(cx + math.cos(a0) * ra, hs[i], cz + math.sin(a0) * ra), (cx + math.cos(a1) * ra, hs[i], cz + math.sin(a1) * ra),
                 (cx + math.cos(a1) * rb, hs[i + 1], cz + math.sin(a1) * rb), (cx + math.cos(a0) * rb, hs[i + 1], cz + math.sin(a0) * rb)]
            n = np.array([math.cos((a0 + a1) / 2), 0, math.sin((a0 + a1) / 2)])
            uv = [(k / sides, 1 - (hs[i] + 0.3) / 2 % 1), ((k + 1) / sides, 1 - (hs[i] + 0.3) / 2 % 1), ((k + 1) / sides, 1 - (hs[i + 1] + 0.3) / 2 % 1 or 0.001), (k / sides, 1 - (hs[i + 1] + 0.3) / 2 % 1 or 0.001)]
            R.quad(cam, p, uv, tex, n)
    return lambda y: (cx, cz, r0 * (1 - 0.96 * max(0, y) / 12) * FLARE(y))


def draw_ground(R, cam, tex, x0, x1, z0, z1, y=0.0):
    for x in np.arange(x0, x1, 1.0):
        for z in np.arange(z0, z1, 1.0):
            R.quad(cam, [(x, y, z), (x + 1, y, z), (x + 1, y, z + 1), (x, y, z + 1)], [(0, 0), (1, 0), (1, 1), (0, 1)], tex, np.array([0, 1, 0]))


def draw_cube_log(R, cam, bx, bz, h, tex):
    for y in range(h):
        for d, (nx, nz) in enumerate([(0, -1), (1, 0), (0, 1), (-1, 0)]):
            cx, cz = bx + 0.5, bz + 0.5
            tx, tz = -nz, nx
            fc = (cx + nx * 0.5, cz + nz * 0.5)
            p = [(fc[0] - tx * 0.5, y, fc[1] - tz * 0.5), (fc[0] + tx * 0.5, y, fc[1] + tz * 0.5),
                 (fc[0] + tx * 0.5, y + 1, fc[1] + tz * 0.5), (fc[0] - tx * 0.5, y + 1, fc[1] - tz * 0.5)]
            R.quad(cam, p, [(0, 1), (1, 1), (1, 0), (0, 0)], tex, np.array([nx, 0, nz]))


def rub(R, cam, trunk_at, round_, cx, cz, ang0, stage, seed, size, gap=0.008):
    lo = 0.26 + 0.14 * size; hi = 0.78 + 0.42 * size; w = (0.10 + 0.09 * size) * (0.85 + 0.3 * rnd(seed, 3))
    if round_:
        r = trunk_at((lo + hi) / 2)[2]; w = min(max(r * (1.05 + 0.55 * size) * (0.9 + 0.2 * rnd(seed, 8)), w), r * 2.3)
    else:
        w = min(0.62, w * 2.2 + 0.08)
    nu, nv = (9, 8) if round_ else (2, 2)

    def surf(u, y, g):
        if round_:
            c = trunk_at(y); r = c[2] + g + 0.012; a = ang0 + max(-r * 1.3, min(r * 1.3, u)) / r
            return np.array([c[0] + math.cos(a) * r, y, c[1] + math.sin(a) * r]), np.array([math.cos(a), 0, math.sin(a)])
        nx, nz = round(math.cos(ang0)), round(math.sin(ang0)); tx, tz = -nz, nx
        uu = max(-0.46, min(0.46, u))
        return np.array([cx + nx * (0.5 + g) + tx * uu, y, cz + nz * (0.5 + g) + tz * uu]), np.array([nx, 0, nz])
    P = [[surf((i / (nu - 1) - 0.5) * w, hi + (lo - hi) * j / (nv - 1), gap) for j in range(nv)] for i in range(nu)]
    for i in range(nu - 1):
        for j in range(nv - 1):
            uv = [atlas_uv(stage * 128 + i / (nu - 1) * 128, j / (nv - 1) * 256), atlas_uv(stage * 128 + (i + 1) / (nu - 1) * 128, j / (nv - 1) * 256),
                  atlas_uv(stage * 128 + (i + 1) / (nu - 1) * 128, (j + 1) / (nv - 1) * 256), atlas_uv(stage * 128 + i / (nu - 1) * 128, (j + 1) / (nv - 1) * 256)]
            R.quad(cam, [P[i][j][0], P[i + 1][j][0], P[i + 1][j + 1][0], P[i][j + 1][0]], uv, ATL / 1.0, P[i][j][1])
    # curls
    curls = (1 + int(rnd(seed, 7) * 2)) if stage >= 3 else 5 + int(rnd(seed, 7) * 4) - stage
    sk = 0.7 + 0.5 * size
    for c in range(curls):
        r1, r2 = rnd(seed, 20 + c), rnd(seed, 40 + c); top = c % 3 != 2
        fu = (r1 - 0.5) * (0.7 if top else 0.9)
        y = hi - (hi - lo) * (0.04 + 0.06 * r2) if top else lo + (hi - lo) * (0.03 + 0.05 * r2)
        a, n = surf(fu * w, y, gap + 0.002)
        ln = (0.06 + 0.09 * rnd(seed, 60 + c)) * sk; wd = (0.02 + 0.022 * rnd(seed, 80 + c)) * sk
        var = int(rnd(seed, 100 + c) * 2); tw = rnd(seed, 120 + c)
        t = np.array([-n[2], 0, n[0]]); dirY = -1 if top else -0.55
        Ls, Rs = [], []
        for k in range(5):
            s = k / 4; out = ln * (0.55 if top else 0.75) * s * s; down = ln * s; ww = wd * (1 - 0.45 * s) * 0.5; ang = (tw - 0.5) * 0.9 * s
            wv = t * math.cos(ang) + n * math.sin(ang)
            cc = a + n * out + t * (tw - 0.5) * ln * 0.3 * s + np.array([0, dirY * down, 0])
            Ls.append(cc - wv * ww); Rs.append(cc + wv * ww)
        inner = (576 if stage <= 1 else 640) + var * 32; bark = 512 + var * 32
        for k in range(4):
            uvi = [atlas_uv(inner, k * 32), atlas_uv(inner + 32, k * 32), atlas_uv(inner + 32, (k + 1) * 32), atlas_uv(inner, (k + 1) * 32)]
            uvb = [atlas_uv(bark, k * 32), atlas_uv(bark + 32, k * 32), atlas_uv(bark + 32, (k + 1) * 32), atlas_uv(bark, (k + 1) * 32)]
            R.quad(cam, [Ls[k], Rs[k], Rs[k + 1], Ls[k + 1]], uvi, ATL, n + np.array([0, .25, 0]))
            R.quad(cam, [Ls[k], Rs[k], Rs[k + 1], Ls[k + 1]], uvb, ATL, -n - np.array([0, .25, 0]))
    # shavings
    if stage <= 2:
        g = trunk_at(0.05) if round_ else (cx, cz, 0.5)
        nx, nz = math.cos(ang0), math.sin(ang0)
        tx, tz = -nz, nx; r = g[2] if round_ else 0.5
        sx, sz = g[0] + nx * (r + 0.01), g[1] + nz * (r + 0.01)
        al, ac = 0.40 * sk, max(0.5, w * 1.3) * sk; y = 0.006
        p0 = (sx - tx * ac / 2, y, sz - tz * ac / 2); p1 = (sx + tx * ac / 2, y, sz + tz * ac / 2)
        p2 = (p1[0] + nx * al, y, p1[2] + nz * al); p3 = (p0[0] + nx * al, y, p0[2] + nz * al)
        vv = 0 if stage == 0 else 128
        R.quad(cam, [p0, p1, p2, p3], [atlas_uv(704, vv), atlas_uv(832, vv), atlas_uv(832, vv + 128), atlas_uv(704, vv + 128)], ATL, np.array([0, 1, 0]))


def tube(R, cam, a, b, ra, rb, sides, region):
    a, b = np.asarray(a, float), np.asarray(b, float); d = b - a; L = np.linalg.norm(d)
    if L < 1e-4: return
    t = d / L; ref = np.array([1, 0, 0]) if abs(t[1]) > 0.9 else np.array([0, 1, 0])
    e1 = np.cross(t, ref); e1 /= np.linalg.norm(e1); e2 = np.cross(t, e1)
    u0, v0, uw, vh = region; vl = min(vh, L / 0.2 * vh)
    for k in range(sides):
        a0, a1 = 2 * math.pi * k / sides, 2 * math.pi * (k + 1) / sides
        n0 = e1 * math.cos(a0) + e2 * math.sin(a0); n1 = e1 * math.cos(a1) + e2 * math.sin(a1)
        p = [a + n0 * ra, a + n1 * ra, b + n1 * rb, b + n0 * rb]
        uv = [atlas_uv(u0 + uw * k / sides, v0), atlas_uv(u0 + uw * (k + 1) / sides, v0), atlas_uv(u0 + uw * (k + 1) / sides, v0 + vl), atlas_uv(u0 + uw * k / sides, v0 + vl)]
        nm = (n0 + n1); nm /= np.linalg.norm(nm)
        R.quad(cam, p, uv, ATL, nm)


def scrape(R, cam, cx, cz, stage, seed, size, f, trunk_at, gap=0.008):
    k = (0.86 + 0.3 * size) * (0.94 + 0.12 * rnd(seed, 1)); q = 1.5 * k
    fx, fz = f; rx, rz = -fz, fx
    p = []; uv = []
    for (dx, dz) in [(-q / 2, -q / 2), (q / 2, -q / 2), (q / 2, q / 2), (-q / 2, q / 2)]:
        p.append((cx + dx, gap, cz + dz))
        a = (dx * rx + dz * rz) / q; b = -(dx * fx + dz * fz) / q
        uv.append(atlas_uv(stage * 256 + (0.5 + a) * 256, 256 + (0.5 + b) * 256))
    R.quad(cam, p, uv, ATL, np.array([0, 1, 0]))
    # licking branch
    tipY = 1.32 + 0.26 * size
    tip = np.array([cx + fx * 0.1, tipY, cz + fz * 0.1])
    c = trunk_at(1.7)
    d = np.array([tip[0] - c[0], 0, tip[2] - c[1]]); dl = np.linalg.norm(d)
    root = np.array([c[0], 1.7, c[1]]) + d / dl * c[2] * 0.85
    mid = (root + tip) / 2; mid[1] = max(root[1], tip[1]) + 0.12 + 0.08 * np.linalg.norm(tip - root)
    pts = []; rad = []
    for i in range(7):
        t = i / 6
        pts.append((1 - t) ** 2 * root + 2 * (1 - t) * t * mid + t * t * tip); rad.append(0.042 + (0.011 - 0.042) * t ** 0.8)
    pts.append(tip + (tip - mid) * 0.04 + np.array([0, -0.09, 0])); rad.append(0.007)
    for i in range(7):
        tube(R, cam, pts[i], pts[i + 1], rad[i], rad[i + 1], 5, (832, 0, 64, 64))
    for kk in range(3):
        a = kk * 2.1 + rnd(seed, 40 + kk)
        sp = pts[7] + np.array([math.cos(a) * 0.012, -0.025 - 0.015 * rnd(seed, 50 + kk), math.sin(a) * 0.012])
        tube(R, cam, pts[7], sp, 0.004, 0.0012, 3, (896, 0, 32, 64))
    nt = 4 + int(rnd(seed, 60) * 2)
    for kk in range(nt):
        t = 0.25 + 0.55 * (kk + rnd(seed, 61 + kk)) / nt; i = min(5, int(t * 6))
        pp = pts[i]; dd = pts[i + 1] - pts[i]; dd /= np.linalg.norm(dd)
        side = (1 if kk % 2 == 0 else -1) * (0.7 + 0.4 * rnd(seed, 70 + kk))
        h = np.array([-dd[2], 0, dd[0]]); h /= np.linalg.norm(h)
        tw = dd + h * side + np.array([0, 0.35, 0]); tw /= np.linalg.norm(tw)
        tl = 0.14 + 0.12 * rnd(seed, 80 + kk); qq = pp + tw * tl
        tube(R, cam, pp, qq, 0.008, 0.004, 3, (832, 0, 64, 64))
        size_l = 0.28 + 0.1 * rnd(seed, 90 + kk); u0 = 832 if int(rnd(seed, 95 + kk) * 2) == 0 else 896
        for cc in range(2):
            ang = rnd(seed, 99 + kk) * 1.5 + cc * 1.5708
            w = h * math.cos(ang) + tw * math.sin(ang) * 0.3; w[1] = 0; w /= np.linalg.norm(w)
            fwd = tw + np.array([0, 0.5, 0]); fwd /= np.linalg.norm(fwd)
            b0 = qq - w * size_l / 2; b1 = qq + w * size_l / 2; t0 = b0 + fwd * size_l; t1 = b1 + fwd * size_l
            nrm = np.cross(b1 - b0, t0 - b0); nrm /= np.linalg.norm(nrm)
            R.quad(cam, [t0, t1, b1, b0], [atlas_uv(u0, 64), atlas_uv(u0 + 64, 64), atlas_uv(u0 + 64, 128), atlas_uv(u0, 128)], ATL, nrm)
            R.quad(cam, [t0, t1, b1, b0], [atlas_uv(u0, 64), atlas_uv(u0 + 64, 64), atlas_uv(u0 + 64, 128), atlas_uv(u0, 128)], ATL, -nrm, 0.85)


def main():
    out = os.path.join(ROOT, 'docs/ws/sign'); os.makedirs(out, exist_ok=True)
    bark = gen_bark(1); grass = gen_grass(2); oak = gen_oak16()
    # 1: Ultra round trunks: fresh (left) and weathered (right)
    R = Raster(); cam = Cam((0.5, 1.25, 3.3), (0.5, 0.65, 0))
    draw_ground(R, cam, grass, -3, 4, -3, 3)
    t1 = draw_round_trunk(R, cam, -0.4, 0, 0.30, bark)
    t2 = draw_round_trunk(R, cam, 1.5, 0, 0.30, bark)
    rub(R, cam, t1, True, -0.4, 0, math.pi / 2, 0, 11, 0.8)
    rub(R, cam, t2, True, 1.5, 0, math.pi / 2 + 0.3, 2, 12, 0.5)
    Image.fromarray(np.clip(R.img, 0, 255).astype(np.uint8)).save(os.path.join(out, 'rub_ultra.png'))
    # 2: block logs (Classic / Balanced): fresh and old
    R = Raster(); cam = Cam((0.9, 1.35, 3.6), (0.9, 0.7, 0))
    draw_ground(R, cam, grass, -3, 5, -3, 3)
    draw_cube_log(R, cam, -1, -1, 4, oak); draw_cube_log(R, cam, 1, -1, 4, oak)
    rub(R, cam, None, False, -0.5, -0.5, math.pi / 2, 1, 13, 0.7)
    rub(R, cam, None, False, 1.5, -0.5, math.pi / 2, 3, 14, 0.4)
    Image.fromarray(np.clip(R.img, 0, 255).astype(np.uint8)).save(os.path.join(out, 'rub_classic.png'))
    # 3: scrape with licking branch under a round trunk, fresh and old side by side
    R = Raster(); cam = Cam((4.6, 1.7, 1.6), (0.6, 0.8, -0.6))
    draw_ground(R, cam, grass, -4, 5, -4, 4)
    ta = draw_round_trunk(R, cam, -0.8, -1.4, 0.3, bark)
    tb = draw_round_trunk(R, cam, 2.2, -1.4, 0.3, bark)
    scrape(R, cam, -0.8, 0.1, 0, 21, 0.8, (0, -1), ta)
    scrape(R, cam, 2.2, 0.1, 3, 22, 0.5, (0, -1), tb)
    Image.fromarray(np.clip(R.img, 0, 255).astype(np.uint8)).save(os.path.join(out, 'scrape.png'))
    print('previews in', out)


if __name__ == '__main__':
    main()
