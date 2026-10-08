#!/usr/bin/env python3
"""[benchart] 64x64 painted emblems for the bench screens (transparent background, readable at 32 px).

python3 tools/benches/emblems.py <repo> [preview.png]
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

S = 256  # paint at 4x, downsample to 64


def hexc(h, a=1.0):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)] + [a])


class Canvas:
    def __init__(self):
        self.rgba = np.zeros((S, S, 4), np.float32)

    def mask(self, pts=None, ellipse=None, width=None, line=None):
        im = Image.new('L', (S, S), 0)
        d = ImageDraw.Draw(im)
        if pts is not None:
            d.polygon([tuple(p) for p in pts], fill=255)
        if ellipse is not None:
            d.ellipse(ellipse, fill=255)
        if line is not None:
            d.line([tuple(p) for p in line], fill=255, width=int(width), joint='curve')
        return np.asarray(im).astype(np.float32) / 255

    def fill(self, m, c0, c1=None, p0=(0, 0), p1=(0, S), noise=0.0, seed=0):
        ys, xs = np.mgrid[0:S, 0:S].astype(np.float32)
        if c1 is None:
            col = np.broadcast_to(hexc(c0), (S, S, 4)).copy()
        else:
            d = np.array(p1, float) - np.array(p0, float)
            t = ((xs - p0[0]) * d[0] + (ys - p0[1]) * d[1]) / max(1e-6, (d ** 2).sum())
            t = np.clip(t, 0, 1)[..., None]
            col = hexc(c0) * (1 - t) + hexc(c1) * t
        if noise:
            rng = np.random.default_rng(seed)
            n = ndimage.gaussian_filter(rng.random((S, S)).astype(np.float32), 2.0)
            n = (n - n.mean()) / (n.std() + 1e-6)
            col[..., :3] *= (1 + noise * n)[..., None]
        a = m[..., None] * col[..., 3:4]
        self.rgba[..., :3] = self.rgba[..., :3] * (1 - a) + col[..., :3] * a
        self.rgba[..., 3:4] = self.rgba[..., 3:4] * (1 - a) + a

    def grain(self, m, c0, c1, angle, freq=0.09, seed=1):
        """wood: streaky bands along `angle`"""
        ys, xs = np.mgrid[0:S, 0:S].astype(np.float32)
        a = math.radians(angle)
        across = -xs * math.sin(a) + ys * math.cos(a)
        along = xs * math.cos(a) + ys * math.sin(a)
        rng = np.random.default_rng(seed)
        n = ndimage.gaussian_filter(rng.random((S, S)).astype(np.float32), 6)
        n = (n - n.min()) / (n.max() - n.min() + 1e-6)
        t = 0.5 + 0.5 * np.sin(across * freq * 2 * math.pi + n * 6 + along * 0.004)
        t = np.clip(t ** 1.6, 0, 1)[..., None]
        col = hexc(c0) * (1 - t) + hexc(c1) * t
        a_ = m[..., None]
        self.rgba[..., :3] = self.rgba[..., :3] * (1 - a_) + col[..., :3] * a_
        self.rgba[..., 3:4] = self.rgba[..., 3:4] * (1 - a_) + a_

    def out(self):
        im = Image.fromarray((np.clip(self.rgba, 0, 1) * 255).astype(np.uint8), 'RGBA')
        small = im.resize((64, 64), Image.LANCZOS)
        arr = np.asarray(small).astype(np.float32) / 255
        a = arr[..., 3]
        # dark rim (1 px) for legibility on any header colour
        rim = ndimage.grey_dilation(a, size=(3, 3))
        rim = np.clip(rim - a, 0, 1)
        outc = np.array([0.09, 0.06, 0.04])
        rgb = arr[..., :3] * a[..., None] + outc * rim[..., None] * 0.9
        alpha = np.clip(a + rim * 0.9, 0, 1)
        rgb = np.where(alpha[..., None] > 0, rgb / np.maximum(alpha[..., None], 1e-6), 0)
        # soft drop shadow below/right
        sh = ndimage.shift(alpha, (1.5, 1.0), order=1)
        sh = ndimage.gaussian_filter(sh, 0.8) * 0.45
        fa = alpha + sh * (1 - alpha)
        frgb = (rgb * alpha[..., None] + np.zeros(3) * (sh * (1 - alpha))[..., None]) / np.maximum(fa[..., None], 1e-6)
        o = np.dstack([frgb, fa])
        return Image.fromarray((np.clip(o, 0, 1) * 255).astype(np.uint8), 'RGBA')


def rect(cx, cy, w, h, ang):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    pts = []
    for dx, dy in ((-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)):
        pts.append((cx + dx * c - dy * s, cy + dx * s + dy * c))
    return pts


def frame_xy(origin, ang):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)

    def f(u, v):
        return (origin[0] + u * c - v * s, origin[1] + u * s + v * c)
    return f


# ---------------------------------------------------------------------------------------------- frontier: saw + hammer
def frontier():
    cv = Canvas()
    # hand saw: handle lower left, toe upper right
    ang = -42
    f = frame_xy((66, 196), ang)
    L = 190
    blade = [f(18, -36), f(L, -12), f(L, 6), f(18, 20)]
    cv.fill(cv.mask(blade), '#e3e7ea', '#6f777e', p0=f(0, -30), p1=f(0, 22), noise=0.05, seed=3)
    # teeth along the lower edge (v = +)
    teeth = []
    n = 26
    for i in range(n + 1):
        u = 22 + i * (L - 24) / n
        v = 20 - (u - 18) / (L - 18) * 14
        teeth.append(f(u, v))
        if i < n:
            u2 = u + (L - 24) / n / 2
            v2 = 20 - (u2 - 18) / (L - 18) * 14
            teeth.append(f(u2, v2 + 7))
    teeth += [f(L, -2), f(22, -2)]
    cv.fill(cv.mask(teeth), '#9aa2a8', '#4d545a', p0=f(0, 0), p1=f(0, 24))
    # back edge highlight
    cv.fill(cv.mask(line=[f(20, -34), f(L - 2, -11)], width=4), '#f4f7f9')
    # wooden closed handle with hand hole
    hd = [f(-30, -48), f(26, -46), f(32, 30), f(-8, 40), f(-40, 26)]
    cv.grain(cv.mask(hd), '#4a2612', '#a05c2a', ang + 90, freq=0.05, seed=4)
    hole = [f(-20, -30), f(12, -30), f(14, 14), f(-12, 20), f(-24, 8)]
    cv.fill(cv.mask(hole), '#2a160b', None)
    cv.rgba[cv.mask(hole) > 0.5] = 0
    for (u, v) in ((18, -28), (18, 4)):
        cx, cy = f(u, v)
        cv.fill(cv.mask(ellipse=(cx - 6, cy - 6, cx + 6, cy + 6)), '#f2d27a', '#8a6420', p0=(cx - 6, cy - 6), p1=(cx + 6, cy + 6))
    # claw hammer crossing: handle lower right, head upper left
    g = frame_xy((206, 214), -128)
    hl = [g(0, -9), g(150, -7), g(150, 7), g(0, 10)]
    cv.grain(cv.mask(hl), '#6b3a17', '#c88a4a', -128, freq=0.07, seed=7)
    cv.fill(cv.mask(line=[g(4, -6), g(146, -4)], width=4), '#e8b277')
    grip = [g(0, -10), g(48, -9), g(48, 9), g(0, 11)]
    cv.fill(cv.mask(grip), '#3a2414', '#5e3a20', p0=g(0, -10), p1=g(0, 10), noise=0.1, seed=9)
    # head: perpendicular to the handle at u=150
    head = [g(132, -40), g(170, -40), g(170, 30), g(160, 46), g(146, 34), g(132, 30)]
    cv.fill(cv.mask(head), '#6d737a', '#24272b', p0=g(132, -40), p1=g(170, 40), noise=0.06, seed=11)
    face = [g(134, -52), g(168, -52), g(168, -38), g(134, -38)]
    cv.fill(cv.mask(face), '#cfd4d8', '#7a8086', p0=g(134, -52), p1=g(168, -38))
    claw = [g(136, 30), g(166, 30), g(150, 62), g(142, 60)]
    cv.fill(cv.mask(claw), '#4a4f55', '#1c1e21', p0=g(136, 30), p1=g(150, 62))
    cv.fill(cv.mask(line=[g(133, -36), g(133, 26)], width=3), '#9ca3a9')
    return cv.out()


# ---------------------------------------------------------------------------------------------- gunsmith: bow + scope
def gunsmith():
    cv = Canvas()
    # recurve bow on the left, vertical, belly to the right
    pts = []
    for i in range(41):
        t = i / 40
        y = 18 + t * 220
        k = (t - 0.5) * 2
        x = 92 - 52 * (1 - k * k) + (18 * (abs(k) - 0.82) / 0.18 if abs(k) > 0.82 else 0)
        pts.append((x, y))
    tip_t, tip_b = pts[0], pts[-1]
    cv.fill(cv.mask(line=[(tip_t[0] + 2, tip_t[1] + 2), (tip_b[0] + 2, tip_b[1] - 2)], width=3), '#d9cfb6')
    for w, c0, c1 in ((15, '#3a1d0c', '#3a1d0c'), (11, '#6b3816', '#c07a3c')):
        cv.fill(cv.mask(line=pts, width=w), c0, c1, p0=(30, 0), p1=(110, 0))
    cv.fill(cv.mask(line=pts[14:27], width=17), '#2a1a12', '#4a2e1e', p0=(30, 0), p1=(60, 0), noise=0.15, seed=2)
    cv.fill(cv.mask(line=[(p[0] - 3, p[1]) for p in pts[2:13]], width=3), '#e6a868')
    cv.fill(cv.mask(line=[(p[0] - 3, p[1]) for p in pts[28:39]], width=3), '#e6a868')
    # scope across, slightly rising to the right
    f = frame_xy((70, 168), -24)
    body = [f(0, -13), f(64, -13), f(80, -10), f(150, -10), f(168, -22), f(196, -22), f(196, 22), f(168, 22), f(150, 10),
            f(80, 10), f(64, 13), f(0, 13)]
    cv.fill(cv.mask(body), '#5a6068', '#121418', p0=f(0, -22), p1=f(0, 22), noise=0.05, seed=5)
    cv.fill(cv.mask(line=[f(4, -9), f(60, -9)], width=3), '#9aa3ad')
    cv.fill(cv.mask(line=[f(84, -7), f(148, -7)], width=3), '#9aa3ad')
    cv.fill(cv.mask(line=[f(170, -18), f(192, -18)], width=3), '#9aa3ad')
    # turrets
    tur = [f(104, -10), f(124, -10), f(124, -30), f(104, -30)]
    cv.fill(cv.mask(tur), '#4a5058', '#16181b', p0=f(104, -30), p1=f(124, -10))
    tur2 = [f(108, 10), f(122, 10), f(122, 24), f(108, 24)]
    cv.fill(cv.mask(tur2), '#3a3f45', '#16181b')
    # brass rings
    for u in (90, 136):
        r = [f(u, -14), f(u + 8, -14), f(u + 8, 14), f(u, 14)]
        cv.fill(cv.mask(r), '#f2d27a', '#7a5618', p0=f(u, -14), p1=f(u + 8, 14))
    # objective lens glint
    lens = [f(194, -18), f(200, -18), f(200, 18), f(194, 18)]
    cv.fill(cv.mask(lens), '#7fc3e6', '#1b3a5c', p0=f(0, -18), p1=f(0, 18))
    return cv.out()


# ---------------------------------------------------------------------------------------------- reloading: press + cartridge
def reloading():
    cv = Canvas()
    red0, red1 = '#d0392c', '#5a0f0b'
    # base
    cv.fill(cv.mask([(36, 214), (164, 214), (170, 236), (30, 236)]), '#a8251c', '#3a0a07', p0=(0, 214), p1=(0, 236))
    # O-frame: outer body with window
    outer = [(62, 70), (138, 70), (146, 84), (146, 214), (54, 214), (54, 84)]
    cv.fill(cv.mask(outer), red0, red1, p0=(54, 0), p1=(146, 0), noise=0.05, seed=3)
    win = [(80, 104), (120, 104), (120, 170), (80, 170)]
    cv.fill(cv.mask(win), '#2a0907', '#4a120d', p0=(0, 104), p1=(0, 170))
    cv.fill(cv.mask(line=[(58, 86), (58, 210)], width=4), '#f07a6a')
    # chrome ram + shell holder + case in the window
    cv.fill(cv.mask([(92, 150), (108, 150), (108, 200), (92, 200)]), '#f4f6f8', '#6a7076', p0=(92, 0), p1=(108, 0))
    cv.fill(cv.mask([(88, 142), (112, 142), (112, 152), (88, 152)]), '#2a2a2a', '#0e0e0e')
    cv.fill(cv.mask([(93, 116), (107, 116), (107, 142), (93, 142)]), '#f2d27a', '#8a6420', p0=(93, 0), p1=(107, 0))
    # die on top with lock ring
    cv.fill(cv.mask([(88, 22), (112, 22), (112, 70), (88, 70)]), '#5a5e64', '#121416', p0=(88, 0), p1=(112, 0))
    cv.fill(cv.mask([(80, 52), (120, 52), (120, 66), (80, 66)]), '#e4e8ec', '#6a7076', p0=(80, 0), p1=(120, 0))
    cv.fill(cv.mask([(96, 10), (104, 10), (104, 24), (96, 24)]), '#d0d4d8')
    # lever: from the side linkage up to the right with a black ball
    cv.fill(cv.mask(line=[(140, 168), (214, 70)], width=11), '#f4f6f8', '#7a8086', p0=(140, 0), p1=(214, 0))
    cv.fill(cv.mask(ellipse=(196, 46, 232, 82)), '#5a5a5a', '#050505', p0=(196, 46), p1=(232, 82))
    cv.fill(cv.mask(ellipse=(204, 54, 214, 64)), '#9a9a9a')
    cv.fill(cv.mask([(138, 156), (160, 156), (160, 182), (138, 182)]), '#b0261d', '#4a0c08')
    # big cartridge front right
    cx = 196
    case = [(cx - 17, 236), (cx + 17, 236), (cx + 17, 150), (cx + 10, 136), (cx + 10, 122), (cx - 10, 122), (cx - 10, 136),
            (cx - 17, 150)]
    cv.fill(cv.mask(case), '#f8e49a', '#7a5414', p0=(cx - 17, 0), p1=(cx + 17, 0), noise=0.04, seed=7)
    cv.fill(cv.mask([(cx - 19, 226), (cx + 19, 226), (cx + 19, 238), (cx - 19, 238)]), '#e8c86a', '#6a4a12', p0=(cx - 19, 0), p1=(cx + 19, 0))
    bullet = [(cx - 10, 122), (cx + 10, 122), (cx + 9, 100), (cx + 3, 84), (cx, 80), (cx - 3, 84), (cx - 9, 100)]
    cv.fill(cv.mask(bullet), '#f0a070', '#7a3a14', p0=(cx - 10, 0), p1=(cx + 10, 0))
    cv.fill(cv.mask(line=[(cx - 9, 228), (cx - 9, 152)], width=4), '#fff6c8')
    return cv.out()


def main():
    repo = os.path.abspath(sys.argv[1])
    d = os.path.join(repo, 'patch', 'assets', 'frontierhunts', 'textures', 'gui', 'bench')
    os.makedirs(d, exist_ok=True)
    ims = {}
    for bid, fn in (('frontier_workbench', frontier), ('gunsmith_bench', gunsmith), ('reloading_bench', reloading)):
        im = fn()
        im.save(os.path.join(d, f'{bid}_emblem.png'), optimize=True)
        ims[bid] = im
    if len(sys.argv) > 2:
        # preview sheet: each emblem at 64 and 32 on a dark and a parchment header colour, then 4x zoom
        sheet = Image.new('RGBA', (3 * 112, 2 * 72), (0, 0, 0, 255))
        for i, im in enumerate(ims.values()):
            for j, bg in enumerate(((40, 34, 28, 255), (214, 196, 160, 255))):
                tile = Image.new('RGBA', (112, 72), bg)
                tile.alpha_composite(im, (4, 4))
                tile.alpha_composite(im.resize((32, 32), Image.LANCZOS), (74, 20))
                sheet.paste(tile, (i * 112, j * 72))
        sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST).save(sys.argv[2])
        print('wrote', sys.argv[2])


if __name__ == '__main__':
    main()
