#!/usr/bin/env python3
"""[survival] Item icons (32x32, the mod's style: soft-shaded pixel art with a dark outline) + block textures + HUD sprites.

usage: python3 tools/survival/icons.py <repo>
Everything is drawn procedurally (shapes at 8x, shaded with noise, downsampled, alpha-thresholded, outlined).
"""
import os, sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

R = sys.argv[1] if len(sys.argv) > 1 else '.'
ITEM = os.path.join(R, 'patch/assets/frontierhunts/textures/item')
BLOCK = os.path.join(R, 'patch/assets/frontierhunts/textures/block/survival')
GUI = os.path.join(R, 'patch/assets/frontierhunts/textures/gui/survival')
PREV = os.path.join(R, 'tools/survival/preview')
K = 8          # supersampling
N = 32         # icon size


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def noise(w, h, cell, seed):
    r = np.random.default_rng(seed)
    g = r.random((h // cell + 2, w // cell + 2))
    ys, xs = np.mgrid[0:h, 0:w] / cell
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


class Sprite:
    """Layers of flat shapes at K x resolution; finish() shades, downsamples and outlines."""

    def __init__(self, size=N):
        self.n = size
        self.S = size * K
        self.img = Image.new('RGBA', (self.S, self.S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def P(self, pts):
        return [(x * K, y * K) for x, y in pts]

    def poly(self, pts, c):
        self.d.polygon(self.P(pts), fill=hexc(c) if isinstance(c, str) else c)

    def ell(self, x0, y0, x1, y1, c):
        self.d.ellipse([x0 * K, y0 * K, x1 * K, y1 * K], fill=hexc(c) if isinstance(c, str) else c)

    def rect(self, x0, y0, x1, y1, c):
        self.d.rectangle([x0 * K, y0 * K, x1 * K - 1, y1 * K - 1], fill=hexc(c) if isinstance(c, str) else c)

    def line(self, pts, c, w=1.0):
        self.d.line(self.P(pts), fill=hexc(c) if isinstance(c, str) else c, width=max(1, int(w * K)))

    def layer(self):
        """Start a sub-layer to texture separately."""
        s = Sprite(self.n)
        return s

    def put(self, other, tex=None, seed=0, amt=0.25, cell=3, streak=None, light=True):
        a = np.asarray(other.img).astype(float)
        if tex is not None or amt:
            n = noise(self.S, self.S, cell * K, seed)
            if streak == 'v':
                n = n * 0.5 + noise(self.S, self.S, K // 2 + 1, seed + 1) * 0.5
            a[..., :3] *= (1 + (n[..., None] - 0.5) * amt)
        if light:
            # top-left light
            yy, xx = np.mgrid[0:self.S, 0:self.S] / self.S
            a[..., :3] *= (1.12 - 0.24 * (xx * 0.4 + yy * 0.6))[..., None]
        a = np.clip(a, 0, 255).astype(np.uint8)
        self.img.alpha_composite(Image.fromarray(a, 'RGBA'))

    def finish(self, outline='#2a1c12', out_alpha=255):
        small = self.img.resize((self.n, self.n), Image.BOX)
        a = np.asarray(small).astype(float)
        alpha = a[..., 3]
        rgb = a[..., :3] / np.maximum(alpha[..., None] / 255.0, 1e-3)
        mask = alpha > 110
        out = np.zeros((self.n, self.n, 4))
        out[mask, :3] = np.clip(rgb[mask], 0, 255)
        out[mask, 3] = 255
        # outline: transparent pixels next to the shape get a dark colour derived from the neighbour
        oc = np.array(hexc(outline)[:3], float)
        o = np.zeros_like(mask)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            o |= np.roll(np.roll(mask, dy, 0), dx, 1)
        o &= ~mask
        out[o, :3] = oc
        out[o, 3] = out_alpha
        # inner rim: darken shape pixels on the bottom/right edge, lighten top/left
        edge_br = mask & ~(np.roll(mask, -1, 0) & np.roll(mask, -1, 1))
        edge_tl = mask & ~(np.roll(mask, 1, 0) & np.roll(mask, 1, 1))
        out[edge_br, :3] *= 0.78
        out[edge_tl & ~edge_br, :3] = np.minimum(255, out[edge_tl & ~edge_br, :3] * 1.12)
        return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def sub(base):
    return Sprite(base.n)


# ------------------------------------------------------------------------------------------------ meat helpers
def steak(s, shape, flesh, fat, seed, marble=True, cooked=False):
    L = sub(s)
    L.poly(shape, fat)
    inner = [(x * 0.86 + 16 * 0.14, y * 0.86 + 16 * 0.14) for x, y in shape]
    L.poly(inner, flesh)
    if marble:
        r = np.random.default_rng(seed)
        for _ in range(7):
            x, y = r.uniform(9, 23), r.uniform(10, 22)
            L.line([(x, y), (x + r.uniform(-3, 3), y + r.uniform(-2, 2))], fat if not cooked else '#c99a62', 0.6)
    if cooked:
        for i in range(3):
            y = 11 + i * 4
            L.line([(9, y + 4), (21, y - 2)], '#4a2a16', 1.0)
    s.put(L, seed=seed, amt=0.35, cell=2)


def chunk(cx, cy, rx, ry, seed, jag=0.18, n=14):
    r = np.random.default_rng(seed)
    pts = []
    for i in range(n):
        a = i / n * math.tau
        k = 1 + r.uniform(-jag, jag)
        pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return pts


# ------------------------------------------------------------------------------------------------ icons
def icon_game_meat(cooked=False):
    s = Sprite()
    shape = chunk(16, 16.5, 11.5, 9.5, 3, 0.12)
    steak(s, shape, '#7a2a24' if cooked else '#a8312c', '#e8d2bc' if not cooked else '#c89a6a', 5, True, cooked)
    if cooked:
        L = sub(s); L.poly(shape, (90, 50, 24, 120)); s.put(L, amt=0, light=False)
    return s.finish('#2c1410')


def icon_bear(cooked=False):
    s = Sprite()
    L = sub(s)
    L.poly([(4, 13), (10, 7), (24, 6), (29, 12), (27, 22), (16, 26), (6, 23)], '#efe2c8' if not cooked else '#d9a868')
    L.poly([(6, 15), (11, 10), (24, 9), (27, 13), (25, 20), (16, 23), (8, 21)], '#6e1e1c' if not cooked else '#5c3018')
    L.line([(8, 17), (25, 14)], '#efe2c8' if not cooked else '#d9a868', 1.4)
    s.put(L, seed=11, amt=0.4, cell=2)
    return s.finish('#24100c')


def icon_fowl(cooked=False):
    s = Sprite()
    body = '#e8b8a0' if not cooked else '#c27a34'
    L = sub(s)
    L.ell(7, 9, 26, 25, body)                                # breast
    L.poly([(9, 21), (5, 27), (8, 29), (13, 23)], body)       # drumstick
    L.poly([(22, 21), (26, 28), (29, 26), (25, 19)], body)
    L.ell(4, 26, 8, 30, '#f2e8dc'); L.ell(26, 26, 30, 30, '#f2e8dc')
    s.put(L, seed=21, amt=0.3, cell=2)
    if cooked:
        G = sub(s); G.ell(10, 11, 18, 17, (255, 220, 150, 110)); s.put(G, amt=0, light=False)
    return s.finish('#3a1e12')


def icon_organ(cooked=False):
    s = Sprite()
    L = sub(s)
    liver = '#5c1a22' if not cooked else '#4a2a1a'
    heart = '#8c2028' if not cooked else '#6a3a20'
    L.poly(chunk(12, 18, 9, 7, 31, 0.1), liver)
    L.poly([(17, 9), (22, 6), (27, 9), (28, 15), (22, 25), (16, 16)], heart)
    L.line([(20, 8), (21, 13)], '#d8c0b0' if not cooked else '#b08860', 0.8)
    s.put(L, seed=33, amt=0.45, cell=2)
    return s.finish('#200a0c')


def icon_fat():
    s = Sprite()
    L = sub(s)
    L.poly(chunk(16, 17, 11, 8, 41, 0.22), '#f0e6d2')
    L.line([(8, 18), (14, 15), (20, 19), (25, 16)], '#d8a49a', 0.9)
    s.put(L, seed=42, amt=0.18, cell=2)
    return s.finish('#5a4634')


def icon_tallow():
    s = Sprite()
    L = sub(s)
    L.ell(5, 12, 27, 27, '#c8b48a')           # cake side
    L.ell(5, 9, 27, 22, '#f4ead0')            # top
    L.ell(9, 11, 18, 15, '#fffaf0')
    s.put(L, seed=43, amt=0.12, cell=3)
    return s.finish('#5a4630')


def icon_jerky():
    s = Sprite()
    L = sub(s)
    cols = ['#5a2a18', '#6e3420', '#4a2214']
    for i in range(3):
        ox, oy = i * 5.5 - 5, i * 4.5 - 4
        top, bot = [], []
        for t in np.linspace(0, 1, 10):
            x = 6 + 18 * t + ox
            y = 24 - 15 * t + oy + math.sin(t * 7 + i * 2) * 0.9
            w = 1.6 + 0.5 * math.sin(t * 11 + i)
            top.append((x - w * 0.6, y - w)); bot.append((x + w * 0.6, y + w))
        L.poly(top + bot[::-1], cols[i])
    s.put(L, seed=51, amt=0.5, cell=1)
    return s.finish('#1e0c06')


def icon_pemmican():
    s = Sprite()
    L = sub(s)
    L.poly([(5, 12), (22, 6), (28, 11), (28, 21), (11, 27), (5, 22)], '#6a4428')
    L.poly([(5, 12), (22, 6), (28, 11), (11, 17)], '#8a5a34')
    r = np.random.default_rng(7)
    for _ in range(9):
        x, y = r.uniform(8, 26), r.uniform(9, 24)
        L.ell(x, y, x + 1.6, y + 1.6, '#4a1426')
    s.put(L, seed=52, amt=0.35, cell=1)
    # hide wrap string
    T = sub(s); T.line([(14, 8), (18, 25)], '#d8c8a0', 0.9); s.put(T, amt=0, light=False)
    return s.finish('#24140a')


def icon_salt():
    s = Sprite()
    L = sub(s)
    L.ell(4, 18, 28, 28, '#7a5a3a')            # wooden dish
    L.ell(5, 17, 27, 24, '#9a7650')
    L.poly([(8, 21), (12, 12), (16, 9), (21, 12), (25, 21)], '#f2f2ee')
    s.put(L, seed=61, amt=0.25, cell=1)
    G = sub(s)
    r = np.random.default_rng(62)
    for _ in range(14):
        x, y = r.uniform(10, 23), r.uniform(11, 20)
        G.rect(x, y, x + 1, y + 1, '#ffffff')
    s.put(G, amt=0, light=False)
    return s.finish('#3a2a1a')


def icon_spoiled():
    s = Sprite()
    shape = chunk(16, 17, 11, 9, 71, 0.2)
    steak(s, shape, '#6e6a3a', '#9a9a70', 72, True)
    G = sub(s)
    r = np.random.default_rng(73)
    for _ in range(6):
        x, y = r.uniform(8, 24), r.uniform(10, 23)
        G.ell(x, y, x + 2.5, y + 2, '#4e7a2a')
    G.rect(22, 5, 24, 7, '#202020'); G.rect(8, 6, 10, 8, '#202020')
    s.put(G, amt=0.2, light=False)
    return s.finish('#1c1c0c')


def hide_shape(cx, cy, sx, sy):
    pts = [(0, -10), (3, -9), (5, -11), (7, -8), (7, -3), (10, 2), (8, 6), (5, 7), (4, 11), (0, 9), (-4, 11), (-5, 7), (-8, 6), (-10, 2), (-7, -3), (-7, -8), (-5, -11), (-3, -9)]
    return [(cx + x * sx, cy + y * sy) for x, y in pts]


def icon_heavy_hide():
    s = Sprite()
    L = sub(s); L.poly(hide_shape(16, 16, 1.4, 1.2), '#5a3e28')
    L.poly(hide_shape(16, 16, 0.9, 0.75), '#6e4a30')
    s.put(L, seed=81, amt=0.5, cell=1, streak='v')
    return s.finish('#1e120a')


def icon_fur_pelt():
    s = Sprite()
    L = sub(s)
    L.poly(hide_shape(15, 15, 1.15, 1.05), '#8a7a66')
    L.poly([(19, 24), (24, 27), (29, 29), (27, 25), (21, 21)], '#6e5e4c')   # tail
    L.poly(hide_shape(15, 14, 0.6, 0.55), '#a69680')
    s.put(L, seed=82, amt=0.55, cell=1, streak='v')
    return s.finish('#241c14')


def icon_bear_pelt():
    s = Sprite()
    L = sub(s)
    L.poly(hide_shape(16, 17, 1.45, 1.25), '#2e2018')
    L.ell(12, 1, 20, 8, '#3a2a1e')     # head
    for x in (5, 26):
        L.rect(x, 10, x + 2, 12, '#d8d0c0')
    s.put(L, seed=83, amt=0.55, cell=1, streak='v')
    return s.finish('#0c0806')


def roll(c_out, c_in, seed, fur=False):
    s = Sprite()
    L = sub(s)
    L.poly([(5, 10), (24, 6), (28, 18), (9, 24)], c_out)
    L.ell(3, 9, 11, 25, c_out)
    L.ell(5, 12, 9, 22, c_in)
    L.line([(14, 8), (18, 22)], '#3a2416', 0.8)
    s.put(L, seed=seed, amt=0.55 if fur else 0.3, cell=1, streak='v' if fur else None)
    return s.finish('#22140a')


def folded(c, c2, seed, edge='#c8a070'):
    s = Sprite()
    L = sub(s)
    L.poly([(4, 10), (22, 6), (28, 12), (26, 24), (8, 27), (4, 20)], c)
    L.poly([(4, 10), (22, 6), (28, 12), (10, 16)], c2)
    L.line([(4, 20), (8, 27), (26, 24)], edge, 1.0)
    s.put(L, seed=seed, amt=0.55, cell=1, streak='v')
    return s.finish('#1e140c')


def icon_lining():
    s = Sprite()
    L = sub(s)
    L.poly([(5, 7), (27, 7), (27, 25), (5, 25)], '#b8905e')
    L.poly([(7, 9), (25, 9), (25, 23), (7, 23)], '#a09484')
    s.put(L, seed=91, amt=0.45, cell=1, streak='v')
    T = sub(s)
    for x in range(7, 26, 3):
        T.rect(x, 7.5, x + 1.5, 8.3, '#f0e0c0'); T.rect(x, 23.7, x + 1.5, 24.5, '#f0e0c0')
    s.put(T, amt=0, light=False)
    return s.finish('#2a1c10')


def icon_mittens():
    s = Sprite()
    L = sub(s)
    for ox, c in ((0, '#a08c74'), (13, '#8e7a64')):
        L.poly([(4 + ox, 14), (6 + ox, 9), (11 + ox, 8), (14 + ox, 12), (14 + ox, 25), (5 + ox, 25)], c)
        L.poly([(4 + ox, 17), (1 + ox, 14), (0 + ox, 18), (4 + ox, 22)], c)
        L.rect(4 + ox, 23, 15 + ox, 28, '#d8ccb6')
    s.put(L, seed=92, amt=0.5, cell=1, streak='v')
    T = sub(s); T.line([(9, 9), (14, 3), (22, 8)], '#d8c8a0', 0.8); s.put(T, amt=0, light=False)
    return s.finish('#22180e')


def icon_hat():
    s = Sprite()
    L = sub(s)
    L.ell(6, 4, 26, 20, '#7a6a58')                    # crown
    L.poly([(3, 14), (29, 14), (29, 19), (3, 19)], '#a29280')   # band
    L.poly([(3, 17), (8, 17), (9, 28), (4, 27)], '#8e7e6a')     # ear flaps
    L.poly([(24, 17), (29, 17), (28, 27), (23, 28)], '#8e7e6a')
    L.poly([(8, 10), (24, 10), (25, 15), (7, 15)], '#b0a08a')  # folded visor
    s.put(L, seed=93, amt=0.5, cell=1, streak='v')
    return s.finish('#201812')


def coat(body, sleeve, seed, fringe=None, collar=None, bulk=0, beads=False, drape=None):
    s = Sprite()
    L = sub(s)
    b = bulk
    if drape:
        L.poly([(5 - b, 6), (27 + b, 6), (29 + b, 30), (3 - b, 30)], drape)
    L.poly([(9 - b, 5), (23 + b, 5), (24 + b, 27 + b * 0.5), (8 - b, 27 + b * 0.5)], body)
    L.poly([(9 - b, 5), (3 - b, 9), (1 - b, 24), (6 - b, 25), (8 - b, 13)], sleeve)
    L.poly([(23 + b, 5), (29 + b, 9), (31 + b, 24), (26 + b, 25), (24 + b, 13)], sleeve)
    if collar:
        L.poly([(9 - b, 4), (23 + b, 4), (21, 9), (16, 12), (11, 9)], collar)
    L.line([(16, 10), (16, 27)], '#2a1c12', 0.7)
    s.put(L, seed=seed, amt=0.5 if bulk else 0.3, cell=1, streak='v' if bulk else None)
    if fringe or beads:
        T = sub(s)
        if fringe:
            for x in np.arange(2 - b, 7 - b, 1.2):
                T.line([(x, 24), (x - 0.3, 29)], fringe, 0.5)
            for x in np.arange(26 + b, 31 + b, 1.2):
                T.line([(x, 24), (x + 0.3, 29)], fringe, 0.5)
            for x in np.arange(9, 24, 1.3):
                T.line([(x, 27), (x, 30)], fringe, 0.5)
        if beads:
            for i, x in enumerate(np.arange(1.5 - b, 6 - b, 1.5)):
                T.rect(x, 21, x + 1, 22, ['#b8352a', '#ece4d0', '#2b5a7a'][i % 3])
            for i, x in enumerate(np.arange(26.5 + b, 31 + b, 1.5)):
                T.rect(x, 21, x + 1, 22, ['#b8352a', '#ece4d0', '#2b5a7a'][i % 3])
        s.put(T, amt=0, light=False)
    return s.finish('#1e140c')


def icon_leggings():
    s = Sprite()
    L = sub(s)
    L.poly([(8, 4), (24, 4), (25, 29), (18, 29), (16, 12), (14, 29), (7, 29)], '#b48a5a')
    L.rect(8, 4, 24, 7, '#8a6440')
    s.put(L, seed=95, amt=0.3, cell=1)
    T = sub(s)
    for y in np.arange(9, 28, 1.3):
        T.line([(7 - (y - 9) * 0.02, y), (5, y + 0.6)], '#c8a070', 0.5)
        T.line([(25, y), (27, y + 0.6)], '#c8a070', 0.5)
    s.put(T, amt=0, light=False)
    return s.finish('#22160c')


def icon_mukluks():
    s = Sprite()
    L = sub(s)
    for ox, c in ((0, '#7a5a3c'), (13, '#6a4c32')):
        L.poly([(4 + ox, 9), (12 + ox, 9), (12 + ox, 24), (15 + ox, 26), (15 + ox, 29), (3 + ox, 29), (3 + ox, 24)], c)
        L.rect(2.5 + ox, 6, 13 + ox, 11, '#a29280')
    s.put(L, seed=96, amt=0.4, cell=1, streak='v')
    T = sub(s)
    for ox in (0, 13):
        for i, x in enumerate(np.arange(4 + ox, 12 + ox, 1.6)):
            T.rect(x, 15, x + 1.2, 16.2, ['#b8352a', '#ece4d0', '#24201c', '#d4a23a'][i % 4])
    s.put(T, amt=0, light=False)
    return s.finish('#1e140a')


def icon_rack():
    s = Sprite()
    L = sub(s)
    L.poly([(4, 4), (7, 4), (6, 30), (3, 30)], '#8a6a46')
    L.poly([(25, 4), (28, 4), (29, 30), (26, 30)], '#8a6a46')
    L.rect(3, 6, 29, 9, '#a07e54')
    s.put(L, seed=97, amt=0.3, cell=1)
    M = sub(s)
    for i, (x, ln, c) in enumerate([(9, 17, '#7a2a1c'), (13, 13, '#5a2616'), (17, 19, '#8a3022'), (21, 15, '#4a2012')]):
        M.poly([(x, 8), (x + 3, 8), (x + 2.6, 8 + ln), (x + 0.4, 8 + ln + 1)], c)
    s.put(M, seed=98, amt=0.4, cell=1)
    return s.finish('#22160c')


def icon_bedroll():
    s = Sprite()
    L = sub(s)
    L.ell(2, 8, 30, 27, '#3a2a1e')                 # outer fur
    L.ell(5, 11, 27, 24, '#4a3624')
    L.ell(9, 13, 23, 22, '#b08a5c')                # tanned inside spiral
    L.ell(12, 15, 20, 20, '#4a3624')
    s.put(L, seed=99, amt=0.5, cell=1, streak='v')
    T = sub(s)
    T.rect(7, 8, 9, 27, '#c8a070'); T.rect(23, 8, 25, 27, '#c8a070')
    s.put(T, amt=0.2, light=False)
    return s.finish('#140c06')


ICONS = {
    'game_meat': lambda: icon_game_meat(False),
    'cooked_game': lambda: icon_game_meat(True),
    'bear_meat': lambda: icon_bear(False),
    'cooked_bear_meat': lambda: icon_bear(True),
    'wild_fowl': lambda: icon_fowl(False),
    'cooked_wild_fowl': lambda: icon_fowl(True),
    'organ_meat': lambda: icon_organ(False),
    'cooked_organ_meat': lambda: icon_organ(True),
    'game_fat': icon_fat,
    'tallow': icon_tallow,
    'jerky': icon_jerky,
    'pemmican': icon_pemmican,
    'salt': icon_salt,
    'spoiled_meat': icon_spoiled,
    'heavy_hide': icon_heavy_hide,
    'fur_pelt': icon_fur_pelt,
    'bear_pelt': icon_bear_pelt,
    'tanned_heavy_hide': lambda: roll('#6a4a30', '#a07850', 84),
    'tanned_fur': lambda: folded('#9a8a76', '#b4a48e', 85),
    'bear_fur': lambda: folded('#33241a', '#4a3626', 86),
    'fur_lining': icon_lining,
    'fur_mittens': icon_mittens,
    'fur_hat': icon_hat,
    'buckskin_coat': lambda: coat('#b48a5a', '#a88054', 94, fringe='#c8a070', collar='#8a6440', beads=True),
    'bear_fur_coat': lambda: coat('#3a2a1e', '#33251a', 101, collar='#4a3626', bulk=1.5),
    'hide_robe': lambda: coat('#9c7a52', '#4a3424', 102, collar='#5a4030', drape='#4a3424'),
    'buckskin_leggings': icon_leggings,
    'fur_mukluks': icon_mukluks,
    'drying_rack': icon_rack,
    'hide_bedroll': icon_bedroll,
}


# ------------------------------------------------------------------------------------------------ block textures (16x16 at 2x = 32)
def tex_noise(w, h, base, amt, seed, cell=2, streak=False):
    n = noise(w, h, cell, seed)
    if streak:
        n = n * 0.4 + noise(w, h, 1, seed + 1) * 0.6
    c = np.array(hexc(base)[:3], float)
    a = c[None, None, :] * (1 + (n[..., None] - 0.5) * amt)
    return np.clip(a, 0, 255)


def save(arr, path, alpha=None):
    a = np.dstack([arr, np.full(arr.shape[:2], 255.0) if alpha is None else alpha])
    Image.fromarray(a.astype(np.uint8), 'RGBA').save(path)


def blocks():
    os.makedirs(BLOCK, exist_ok=True)
    W = 32
    # fur top of the bedroll: long dark bear fur strands
    fur = tex_noise(W, W, '#3e2c1e', 0.7, 201, 1, True)
    r = np.random.default_rng(202)
    for _ in range(140):
        x, y = r.integers(0, W), r.integers(0, W)
        for k in range(r.integers(2, 5)):
            fur[(y + k) % W, x] *= 1.25
    save(fur, os.path.join(BLOCK, 'bedroll_fur.png'))
    hide = tex_noise(W, W, '#a8845a', 0.3, 203, 3) * (0.9 + 0.2 * noise(W, W, 8, 206))[..., None]
    hide[:, 15:17] *= 0.82  # one sewn seam
    save(hide, os.path.join(BLOCK, 'bedroll_hide.png'))
    # spruce boughs (already coloured, no tint)
    b = tex_noise(W, W, '#2f4a2a', 0.5, 204, 1)
    r = np.random.default_rng(205)
    for _ in range(90):
        x, y = r.integers(0, W), r.integers(0, W)
        b[y, x:x + 2] = np.array(hexc('#4c6e3a')[:3]) * r.uniform(0.8, 1.2)
    for _ in range(20):
        x, y = r.integers(0, W), r.integers(0, W)
        b[y:y + 2, x] = np.array(hexc('#5a4030')[:3])
    save(b, os.path.join(BLOCK, 'bedroll_boughs.png'))
    # meat strips: raw, drying, dry
    for name, c, amt in (('meat_strip_raw', '#9c2e26', 0.45), ('meat_strip_drying', '#6e2a1c', 0.5), ('meat_strip_dry', '#4a2214', 0.6)):
        m = tex_noise(W, W, c, amt, hash(name) & 0xFFFF, 1, True)
        if name == 'meat_strip_raw':
            for y in range(3, W, 7):
                m[y, :] = np.array(hexc('#e0c8b4')[:3]) * 0.9   # fat streaks
        save(m, os.path.join(BLOCK, name + '.png'))


# ------------------------------------------------------------------------------------------------ HUD sprites (18x18 each, 9x9 on screen)
def hud():
    os.makedirs(GUI, exist_ok=True)
    sheet = Image.new('RGBA', (256, 32), (0, 0, 0, 0))

    def mk(fn):
        s = Sprite(18)
        fn(s)
        return s.finish('#101010')

    def protein(s):
        L = sub(s); L.poly(chunk(9, 9.5, 7, 5.6, 301, 0.12), '#c43a30'); L.poly(chunk(9, 9.5, 4, 3, 302, 0.1), '#e06a58')
        L.line([(3, 8), (6, 6)], '#f0dcc8', 1.0); s.put(L, amt=0.2, cell=1)

    def fat(s):
        L = sub(s); L.poly([(9, 2), (14, 9), (14.5, 12), (12, 15.5), (6, 15.5), (3.5, 12), (4, 9)], '#f2e4c0')
        L.ell(6, 8, 9, 11, '#ffffff'); s.put(L, amt=0.1, cell=1)

    def energy(s):
        L = sub(s)
        L.line([(9, 16), (9, 4)], '#c8a040', 1.1)
        for i, y in enumerate((4, 7, 10)):
            L.ell(5, y, 9, y + 3.2, '#e8c050'); L.ell(9, y, 13, y + 3.2, '#e0b444')
        L.ell(7.3, 1.5, 10.7, 5, '#f0d060')
        s.put(L, amt=0.15, cell=1)

    def thermo(color):
        def f(s):
            L = sub(s)
            L.rect(7, 1.5, 11, 12, '#e8e8e8'); L.ell(5, 10, 13, 17.5, '#e8e8e8')
            L.rect(8.2, 6 if color != '#4a8ad8' else 9, 9.8, 13, color); L.ell(6.5, 11.5, 11.5, 16.5, color)
            s.put(L, amt=0, cell=1)
        return f

    def antler(s):
        L = sub(s)
        L.line([(9, 17), (8, 9), (4, 4), (2, 1)], '#f0c860', 1.4)
        L.line([(8, 11), (11, 6), (14, 2)], '#f0c860', 1.3)
        L.line([(6, 7), (7, 3)], '#f0c860', 1.1)
        L.line([(11, 6), (15, 6)], '#f0c860', 1.0)
        s.put(L, amt=0, cell=1)

    def drop(s):
        L = sub(s); L.poly([(9, 1.5), (14, 10), (13, 14.5), (9, 16.5), (5, 14.5), (4, 10)], '#5aa0e8'); L.ell(6, 9, 8.5, 12, '#c0e0ff')
        s.put(L, amt=0, cell=1)

    def fire(s):
        L = sub(s)
        L.poly([(9, 1), (13, 7), (14, 12), (11, 16), (7, 16), (4, 12), (5, 7), (7, 9)], '#e86a20')
        L.poly([(9, 7), (11.5, 11), (10.5, 15), (7.5, 15), (6.5, 11)], '#ffd060')
        L.rect(3, 15.5, 15, 17, '#6a4428')
        s.put(L, amt=0, cell=1)

    def roof(s):
        L = sub(s); L.poly([(1, 9), (9, 2), (17, 9), (15, 9), (9, 4.5), (3, 9)], '#c89a6a'); L.rect(4, 9, 14, 16, '#8a6a46'); L.rect(7.5, 11, 10.5, 16, '#3a2a1a')
        s.put(L, amt=0, cell=1)

    def flake(s):
        L = sub(s)
        for a in range(3):
            ang = a * math.pi / 3
            dx, dy = math.cos(ang) * 7.5, math.sin(ang) * 7.5
            L.line([(9 - dx, 9 - dy), (9 + dx, 9 + dy)], '#cfe8ff', 1.2)
        s.put(L, amt=0, cell=1)

    sprites = [protein, fat, energy, thermo('#4a8ad8'), thermo('#7ab870'), thermo('#e04a30'), antler, drop, fire, roof, flake]
    for i, f in enumerate(sprites):
        sheet.alpha_composite(mk(f), (i * 18, 0))
    sheet.save(os.path.join(GUI, 'hud.png'))
    return sheet


def main():
    os.makedirs(ITEM, exist_ok=True); os.makedirs(PREV, exist_ok=True)
    names = list(ICONS)
    cols = 10
    rows = (len(names) + cols - 1) // cols
    prev = Image.new('RGBA', (cols * 36, rows * 36), (58, 58, 58, 255))
    for i, n in enumerate(names):
        im = ICONS[n]()
        im.save(os.path.join(ITEM, n + '.png'))
        prev.alpha_composite(im, ((i % cols) * 36 + 2, (i // cols) * 36 + 2))
    prev.resize((prev.width * 4, prev.height * 4), Image.NEAREST).save(os.path.join(PREV, 'icons.png'))
    blocks()
    h = hud()
    h.resize((h.width * 4, h.height * 4), Image.NEAREST).save(os.path.join(PREV, 'hud_icons.png'))
    print('ok', len(names), 'icons')


main()
