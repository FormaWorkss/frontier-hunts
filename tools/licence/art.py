#!/usr/bin/env python3
"""[licence] Original art for hunting licences / tags and camp cooking, drawn procedurally (no external assets).

usage: python3 tools/licence/art.py <repo>
  item icons (32x32, the mod's style: soft-shaded pixel art with a dark outline, see tools/survival/icons.py),
  the Dutch oven block texture (16x16 per face strip), mob effect icons (18x18), GUI slot hints, and preview sheets
  in docs/ws/licence/.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw

R = sys.argv[1] if len(sys.argv) > 1 else '.'
A = os.path.join(R, 'patch/assets/frontierhunts/textures')
ITEM = os.path.join(A, 'item')
PREV = os.path.join(R, 'docs/ws/licence')
K = 8
N = 32


def hexc(h, a=255):
    if isinstance(h, tuple):
        return h if len(h) == 4 else h + (a,)
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
    def __init__(self, size=N):
        self.n = size
        self.S = size * K
        self.img = Image.new('RGBA', (self.S, self.S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def P(self, pts):
        return [(x * K, y * K) for x, y in pts]

    def poly(self, pts, c):
        self.d.polygon(self.P(pts), fill=hexc(c))

    def ell(self, x0, y0, x1, y1, c):
        self.d.ellipse([x0 * K, y0 * K, x1 * K, y1 * K], fill=hexc(c))

    def rect(self, x0, y0, x1, y1, c):
        self.d.rectangle([x0 * K, y0 * K, x1 * K - 1, y1 * K - 1], fill=hexc(c))

    def rrect(self, x0, y0, x1, y1, r, c):
        self.d.rounded_rectangle([x0 * K, y0 * K, x1 * K - 1, y1 * K - 1], radius=r * K, fill=hexc(c))

    def line(self, pts, c, w=1.0):
        self.d.line(self.P(pts), fill=hexc(c), width=max(1, int(w * K)), joint='curve')

    def arc(self, box, a0, a1, c, w=1.0):
        self.d.arc([v * K for v in box], a0, a1, fill=hexc(c), width=max(1, int(w * K)))

    def put(self, other, seed=0, amt=0.25, cell=3, light=True):
        a = np.asarray(other.img).astype(float)
        if amt:
            n = noise(self.S, self.S, max(1, int(cell * K)), seed)
            a[..., :3] *= (1 + (n[..., None] - 0.5) * amt)
        if light:
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
        oc = np.array(hexc(outline)[:3], float)
        o = np.zeros_like(mask)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            o |= np.roll(np.roll(mask, dy, 0), dx, 1)
        o &= ~mask
        out[o, :3] = oc
        out[o, 3] = out_alpha
        edge_br = mask & ~(np.roll(mask, -1, 0) & np.roll(mask, -1, 1))
        edge_tl = mask & ~(np.roll(mask, 1, 0) & np.roll(mask, 1, 1))
        out[edge_br, :3] *= 0.78
        out[edge_tl & ~edge_br, :3] = np.minimum(255, out[edge_tl & ~edge_br, :3] * 1.12)
        return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


def sub(s):
    return Sprite(s.n)


def chunk(cx, cy, rx, ry, seed, jag=0.18, n=14):
    r = np.random.default_rng(seed)
    pts = []
    for i in range(n):
        a = i / n * math.tau
        k = 1 + r.uniform(-jag, jag)
        pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
    return pts


def shade(c, f):
    c = hexc(c)
    return tuple(int(max(0, min(255, v * f))) for v in c[:3]) + (c[3],)


# ================================================================================================ licence & tags
def icon_licence():
    s = Sprite()
    L = sub(s)
    # paper card, slightly turned, folded in thirds
    card = [(3, 8), (27, 5), (29, 24), (5, 27)]
    L.poly(card, '#efe6cc')
    L.poly([(3, 8), (27, 5), (27.6, 10.5), (3.6, 13.4)], '#2f5a3a')              # green header band
    L.line([(11.5, 6.8), (13.2, 26)], '#d6c9a6', 0.6)                            # fold lines
    L.line([(19.8, 5.9), (21.4, 25)], '#d6c9a6', 0.6)
    s.put(L, seed=101, amt=0.12, cell=2)
    T = sub(s)
    for i, y in enumerate((15.5, 18.5, 21.5)):                                   # typed lines
        T.line([(6.5, y + 0.3), (11 - (i == 2) * 2, y - 0.2)], '#6a6250', 0.7)
        T.line([(14, y - 0.4), (19 - i, y - 1.0)], '#6a6250', 0.7)
    T.line([(6, 9.6), (15, 8.6)], '#e8dcae', 0.8)                                # header lettering
    T.line([(6, 11.4), (11, 10.9)], '#cfd8c0', 0.6)
    T.ell(20.5, 16, 27, 22.6, '#a8322a')                                        # wax-red stamp
    T.ell(21.6, 17.1, 25.9, 21.5, '#c8463a')
    T.line([(22.4, 19.6), (23.5, 20.6), (25.2, 18.2)], '#f0d0b0', 0.6)           # tick in the stamp
    T.line([(6, 24.4), (9, 23.4), (10.5, 24.6), (13, 23.6)], '#2a3a6a', 0.6)     # signature
    s.put(T, amt=0, light=False)
    return s.finish('#3a2e1e')


PRINTS = {
    'deer': ('hoof', 0.8), 'elk': ('hoof', 1.0), 'moose': ('hoof', 1.15), 'pronghorn': ('hoof', 0.7), 'bison': ('hoof_round', 1.05),
    'bear': ('bear', 1.0), 'cat': ('cat', 1.0), 'wolf': ('canid', 1.0),
}
TAG_COLORS = {'deer': '#e8b23a', 'elk': '#d8663a', 'moose': '#7a9a4a', 'pronghorn': '#e0d2a0', 'bison': '#8a5a3a', 'bear': '#3a6ab0',
              'cat': '#b04040', 'wolf': '#9aa4ae'}


def track(L, kind, cx, cy, sc, c):
    if kind in ('hoof', 'hoof_round'):
        w = 1.5 * sc
        round_ = kind == 'hoof_round'
        for side in (-1, 1):
            x0 = cx + side * 0.35 * sc
            pts = [(x0, cy - 2.6 * sc), (x0 + side * w, cy - (1.2 if not round_ else 1.8) * sc), (x0 + side * w * 1.05, cy + 1.4 * sc),
                   (x0 + side * 0.5 * sc, cy + 2.4 * sc), (x0, cy + 1.6 * sc)]
            L.poly(pts, c)
    elif kind == 'bear':
        L.ell(cx - 2.6 * sc, cy - 0.6 * sc, cx + 2.6 * sc, cy + 2.6 * sc, c)
        for i in range(5):
            a = math.radians(-150 + i * 30)
            x, y = cx + math.cos(a) * 3.0 * sc, cy - 0.6 * sc + math.sin(a) * 2.2 * sc
            L.ell(x - 0.75 * sc, y - 0.75 * sc, x + 0.75 * sc, y + 0.75 * sc, c)
    else:
        L.ell(cx - 1.7 * sc, cy, cx + 1.7 * sc, cy + 2.6 * sc, c)
        for i in range(4):
            a = math.radians(-140 + i * 33.3)
            r = 2.9 * sc if kind == 'cat' else 3.2 * sc
            x, y = cx + math.cos(a) * r * 0.85, cy + 0.6 * sc + math.sin(a) * r
            L.ell(x - 0.75 * sc, y - 0.9 * sc, x + 0.75 * sc, y + 0.9 * sc, c)
            if kind == 'canid':
                L.line([(x, y - 0.9 * sc), (x + math.cos(a) * 0.9, y - 1.8 * sc)], c, 0.4)


def tag_body(s, kind, filled=False, tilt=0.0):
    col = TAG_COLORS[kind]
    L = sub(s)
    # locking strap (zip tail) looping from the head
    if filled:
        L.arc((4, 1, 16, 13), 160, 400, '#efe8d8', 1.2)            # closed loop around a leg
    else:
        L.line([(16, 6), (21, 3), (27, 2.5), (29.5, 4)], '#efe8d8', 1.3)
        L.line([(27, 2.5), (29.5, 4)], '#c8c0b0', 1.3)
    s.put(L, seed=7, amt=0.05, cell=2)
    B = sub(s)
    body = [(9, 7), (20, 5.5), (24, 9), (25, 27), (12, 29), (8, 25)]
    if tilt:
        cx, cy = 16, 17
        ca, sa = math.cos(tilt), math.sin(tilt)
        body = [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in body]
    B.poly(body, col)
    s.put(B, seed=hash(kind) % 1000, amt=0.12, cell=2)
    D = sub(s)
    # eyelet (brass grommet) at the head
    D.ell(12.6, 7.6, 17.4, 12.4, '#b8903a')
    D.ell(13.9, 8.9, 16.1, 11.1, '#2a1c12')
    # embossed text lines
    dark = shade(col, 0.62)
    D.line([(11, 14.4), (20.5, 13.6)], dark, 0.7)
    # month / day notch grid along the bottom edge
    for i in range(6):
        x = 11.8 + i * 2.1
        y = 26.6 - i * 0.18
        if filled and i in (2, 4):
            D.rect(x, y - 1.2, x + 1.2, y + 0.3, '#2a1c12')         # punched notches
        else:
            D.rect(x, y - 1.0, x + 1.0, y + 0.2, dark)
    track(D, PRINTS[kind][0], 17.4, 20.0, 1.25 * PRINTS[kind][1], shade(col, 0.32))
    if filled:
        D.line([(19.5, 23.6), (21.2, 25.2), (24.2, 21.4)], '#1e3a8a', 0.7)    # pen check on the tag
    s.put(D, amt=0, light=False)


def icon_tag(kind, filled=False):
    s = Sprite()
    tag_body(s, kind, filled)
    return s.finish('#1e160e')


def icon_filled_generic():
    s = Sprite()
    tag_body(s, 'wolf', True)
    return s.finish('#1e160e')


def icon_stamp(kind):
    s = Sprite()
    L = sub(s)
    bg = '#3a7a8a' if kind == 'waterfowl' else '#b0743a'
    # perforated paper stamp
    L.rect(5, 6, 27, 27, '#f4ecd8')
    for i in range(12):
        t = 5 + i * 2.0
        for (x, y) in ((t, 6), (t, 27), (5, t + 1), (27, t + 1)):
            L.ell(x - 0.7, y - 0.7, x + 0.7, y + 0.7, (0, 0, 0, 0))
    s.put(L, seed=12, amt=0.06, cell=2)
    # erase perforation holes properly (transparent)
    arr = np.asarray(s.img).copy()
    for i in range(12):
        t = 5 + i * 2.0
        for (x, y) in ((t, 6), (t, 27), (5, t + 1), (27, t + 1)):
            yy, xx = np.ogrid[0:s.S, 0:s.S]
            arr[((xx - x * K) ** 2 + (yy - y * K) ** 2) < (0.75 * K) ** 2] = 0
    s.img = Image.fromarray(arr, 'RGBA')
    s.d = ImageDraw.Draw(s.img)
    P = sub(s)
    P.rect(7, 8, 25, 25, bg)
    P.rect(7, 21, 25, 25, shade(bg, 0.7))               # water / ground band
    s.put(P, seed=13, amt=0.18, cell=2)
    B = sub(s)
    ink = '#1c1a16'
    if kind == 'waterfowl':
        # a flying mallard silhouette over water
        B.ell(11, 13, 21, 17, ink)
        B.ell(19, 11, 23.5, 14.5, ink)
        B.poly([(23, 12.6), (25.5, 13.2), (23, 13.8)], '#d8a020')
        B.poly([(13, 14), (9, 9), (17, 13.5)], ink)
        B.poly([(16, 14), (20, 9.5), (19.5, 14)], ink)
        B.poly([(11.5, 15), (8.5, 16.6), (11.5, 16.4)], ink)
        for x in (9, 14, 19):
            B.line([(x, 23), (x + 3, 22.6)], '#e8f0f0', 0.5)
    else:
        # a ruffed grouse standing, fan tail
        B.ell(12, 13, 21, 20, ink)
        B.ell(19, 10.5, 23, 14.5, ink)
        B.poly([(12.5, 16), (8, 11), (9, 17.5)], ink)
        B.poly([(22.8, 12.2), (24.6, 12.8), (22.8, 13.4)], '#c8a060')
        B.line([(15, 20), (14.5, 22.5)], ink, 0.6)
        B.line([(18, 20), (18.5, 22.5)], ink, 0.6)
    B.line([(8, 9.5), (13, 9.3)], '#f4ecd8', 0.5)        # value lettering
    s.put(B, amt=0, light=False)
    return s.finish('#3a2e1e')


# ================================================================================================ dishes
def bowl(s, fill_col, seed, rim='#6a4a2c', wood='#8a6038'):
    L = sub(s)
    L.ell(3, 13, 29, 30, shade(wood, 0.8))       # outside of the bowl
    L.rect(3, 13, 29, 21, (0, 0, 0, 0))
    L.poly([(3, 18), (29, 18), (26, 27), (21, 29.5), (11, 29.5), (6, 27)], wood)
    L.ell(3, 12, 29, 22, rim)                      # rim
    L.ell(4.3, 13.2, 27.7, 20.8, shade(fill_col, 0.62))   # shadowed inner wall
    L.ell(5.0, 13.9, 27.0, 20.6, fill_col)          # contents surface
    s.put(L, seed=seed, amt=0.22, cell=1.5)
    H = sub(s)
    H.arc((4, 12.3, 28, 21.7), 200, 300, (255, 230, 190, 120), 0.6)   # rim catch-light
    H.ell(18, 14.6, 22, 15.8, (255, 255, 255, 70))                   # sheen on the broth
    s.put(H, amt=0, light=False)
    G = sub(s)
    for x in (8, 14, 20):
        G.line([(x, 24), (x + 2.5, 27.5)], shade(wood, 0.75), 0.4)   # wood grain
    s.put(G, amt=0, light=False)


def scatter(s, seed, n, box, cols, size=(1.4, 2.4), shape='ell'):
    G = sub(s)
    r = np.random.default_rng(seed)
    x0, y0, x1, y1 = box
    for i in range(n):
        x, y = r.uniform(x0, x1), r.uniform(y0, y1)
        w = r.uniform(*size)
        c = cols[i % len(cols)]
        if shape == 'ell':
            G.ell(x, y, x + w, y + w * 0.75, c)
        else:
            G.rect(x, y, x + w, y + w * 0.8, c)
    s.put(G, seed=seed + 1, amt=0.2, cell=1, light=False)


def plate(s, seed, enamel='#e8e4da', rim='#2a4a7a'):
    L = sub(s)
    L.ell(2, 11, 30, 30, rim)                     # blue-rimmed camp enamelware
    L.ell(3, 12, 29, 29, enamel)
    L.ell(6.5, 14.5, 25.5, 26.5, shade(enamel, 0.93))
    s.put(L, seed=seed, amt=0.06, cell=2)
    G = sub(s)
    G.ell(22, 25, 24, 26.5, '#3a3a3a')            # chipped enamel spot
    G.ell(6, 16, 26, 27.5, (60, 50, 40, 70))       # soft shadow where the food sits
    s.put(G, amt=0, light=False)


def steam(s):
    G = sub(s)
    for x in (11, 17, 22):
        G.line([(x, 10), (x - 1, 7.5), (x + 0.6, 5), (x - 0.4, 2.5)], (255, 255, 255, 120), 0.8)
    s.put(G, amt=0, light=False)


def icon_venison_stew():
    s = Sprite()
    bowl(s, '#6a3418', 201)
    scatter(s, 202, 7, (7, 14, 23, 18.5), ['#4a2010', '#5a2a14'], (2.0, 3.2), 'rect')      # meat
    scatter(s, 203, 4, (7, 14, 24, 19), ['#e07a20'], (1.4, 2.0))                          # carrot
    scatter(s, 204, 4, (8, 14, 24, 19), ['#e8d8a0'], (1.6, 2.2), 'rect')                  # potato
    scatter(s, 205, 2, (9, 14, 22, 18), ['#9a7a5a'], (1.8, 2.4))                          # mushroom
    steam(s)
    return s.finish('#22140a')


def icon_venison_chili():
    s = Sprite()
    bowl(s, '#8a2414', 211)
    scatter(s, 212, 9, (7, 14, 24, 19), ['#5a1a0e', '#6a2412'], (1.4, 2.2))
    scatter(s, 213, 5, (8, 14, 24, 18.6), ['#5a1030'], (1.2, 1.8))                         # beet
    scatter(s, 214, 3, (10, 14, 21, 17.5), ['#f0e0b0'], (1.0, 1.5), 'rect')
    steam(s)
    return s.finish('#22100a')


def icon_fowl_wild_rice():
    s = Sprite()
    bowl(s, '#d8c28a', 221)
    scatter(s, 222, 22, (6, 13.5, 25, 19.5), ['#f0e4c0', '#b89a5a', '#e8d8a8'], (0.7, 1.1))  # grains
    scatter(s, 223, 4, (9, 14, 22, 18), ['#c27a34', '#a8622a'], (2.4, 3.4), 'rect')         # fowl
    scatter(s, 224, 2, (9, 14, 22, 18), ['#8a6a4a'], (1.6, 2.2))
    steam(s)
    return s.finish('#22140a')


def icon_fish_chowder():
    s = Sprite()
    bowl(s, '#e8dcc0', 231)
    scatter(s, 232, 6, (7, 14, 24, 19), ['#f4c8a0', '#e8a878'], (1.6, 2.6))                # salmon / cod flakes
    scatter(s, 233, 4, (8, 14, 24, 19), ['#f0e4a0'], (1.4, 2.0), 'rect')                   # potato
    scatter(s, 234, 5, (8, 14, 24, 18), ['#e8b830'], (0.6, 0.9))                           # fat eyes
    G = sub(s)
    G.line([(10, 16), (13, 15)], '#4a7a3a', 0.5)                                           # herb
    s.put(G, amt=0, light=False)
    steam(s)
    return s.finish('#22140a')


def icon_bear_pot_roast():
    s = Sprite()
    plate(s, 241)
    L = sub(s)
    L.poly(chunk(15, 18.5, 8.5, 5.5, 242, 0.14), '#4a2412')
    L.poly(chunk(15, 17.5, 7, 4, 243, 0.12), '#6a3418')
    s.put(L, seed=244, amt=0.45, cell=1)
    scatter(s, 245, 3, (20, 17, 26, 23), ['#e8d8a0'], (2.4, 3.0), 'ell')
    scatter(s, 246, 3, (6, 20, 12, 25), ['#e07a20'], (1.6, 2.4), 'rect')
    G = sub(s)
    G.line([(10, 16), (19, 15)], '#e8c890', 0.6)                                           # fat cap glisten
    s.put(G, amt=0, light=False)
    return s.finish('#1e1208')


def icon_backstrap_mushrooms():
    s = Sprite()
    plate(s, 251)
    for i, (x, y) in enumerate(((8, 15), (13.5, 17), (19, 15.5))):
        L = sub(s)
        L.ell(x, y, x + 6, y + 6, '#6a2e1c')
        L.ell(x + 1, y + 1, x + 5, y + 5, '#b8524a')            # pink centre, seared edge
        L.ell(x + 2, y + 2, x + 4, y + 4, '#d0706a')
        s.put(L, seed=252 + i, amt=0.25, cell=1)
    scatter(s, 256, 5, (7, 21, 24, 25), ['#8a6a4a', '#a8865a'], (1.8, 2.8))                # mushrooms
    G = sub(s)
    G.line([(8, 23), (24, 21.5)], (120, 70, 30, 150), 0.8)                                 # pan sauce
    s.put(G, amt=0, light=False)
    return s.finish('#1e1208')


def icon_fowl_berry_roast():
    s = Sprite()
    plate(s, 261)
    L = sub(s)
    L.ell(8, 14, 21, 24, '#c27a34')                     # roast breast
    L.poly([(19, 19), (25, 24.5), (27, 22.5), (21, 17)], '#b06a2a')   # leg
    L.ell(25.4, 22, 28, 24.8, '#f2e8dc')
    s.put(L, seed=262, amt=0.3, cell=1.5)
    G = sub(s)
    G.ell(10, 15.5, 16, 19, (255, 220, 150, 110))
    for x, y in ((9, 21.5), (12, 23.5), (15.5, 23.2), (18, 21.6), (13, 20.4)):
        G.ell(x, y, x + 1.8, y + 1.8, '#9a1428')        # berries
        G.ell(x + 0.3, y + 0.3, x + 0.8, y + 0.8, '#e06070')
    G.line([(9, 24.5), (19, 24)], '#7a1020', 0.9)       # sauce
    s.put(G, amt=0, light=False)
    return s.finish('#1e1208')


def icon_heart_liver_fry():
    s = Sprite()
    plate(s, 271)
    for i, (x, y, c) in enumerate(((7, 15, '#4a2418'), (12.5, 17.5, '#5a2a1a'), (18, 15, '#4a2a1c'), (15, 21, '#3a1c12'))):
        L = sub(s)
        L.poly(chunk(x + 3.2, y + 2.2, 3.6, 2.3, 272 + i, 0.15), c)
        s.put(L, seed=276 + i, amt=0.4, cell=1)
    scatter(s, 280, 4, (7, 21, 24, 25), ['#8a6a4a'], (1.6, 2.2))
    G = sub(s)
    G.line([(9, 17), (12, 16.5)], '#c09060', 0.5)
    s.put(G, amt=0, light=False)
    return s.finish('#1e1208')


def icon_hunters_breakfast():
    s = Sprite()
    plate(s, 291)
    E = sub(s)
    for x, y in ((6.5, 14), (13, 13.5)):
        E.poly(chunk(x + 3.4, y + 3.3, 4.3, 3.5, int(x * 10), 0.18), '#c08a4a')     # crisp brown lace
        E.poly(chunk(x + 3.4, y + 3, 3.8, 3.0, int(x * 10), 0.18), '#fff3d6')       # egg whites
        E.ell(x + 2.2, y + 1.8, x + 4.6, y + 4.2, '#f0a818')                         # yolks
    s.put(E, seed=292, amt=0.08, cell=2)
    L = sub(s)
    L.poly([(18, 15), (26, 14), (27, 17.5), (19, 19)], '#6a2a18')                  # steak / chop
    L.poly([(19, 15.6), (25.6, 14.8), (26.2, 16.8), (19.6, 18)], '#8a3a24')
    s.put(L, seed=293, amt=0.3, cell=1)
    scatter(s, 294, 6, (8, 20, 22, 25), ['#d8a050', '#c08a3a'], (1.6, 2.4), 'rect')  # fried potatoes
    return s.finish('#1e1208')


def icon_bannock():
    s = Sprite()
    L = sub(s)
    L.ell(3, 9, 29, 27, '#a8702e')
    L.ell(4.2, 9.5, 27.8, 24.6, '#d8a050')
    s.put(L, seed=301, amt=0.35, cell=1.5)
    G = sub(s)
    G.line([(16, 11), (16, 23)], '#a8702e', 0.8)        # scored quarters
    G.line([(6, 17), (26, 17)], '#a8702e', 0.8)
    for x, y in ((9, 13), (21, 12.5), (10, 20), (22, 20), (14, 15)):
        G.ell(x, y, x + 1.6, y + 1.2, '#8a5420')        # toasted spots
    G.ell(7, 11, 13, 13.5, (255, 240, 200, 100))
    s.put(G, amt=0, light=False)
    return s.finish('#2a180a')


def icon_honey_pemmican():
    s = Sprite()
    L = sub(s)
    L.poly([(5, 12), (22, 6), (28, 11), (28, 21), (11, 27), (5, 22)], '#6a4428')
    L.poly([(5, 12), (22, 6), (28, 11), (11, 17)], '#9a6a30')
    s.put(L, seed=311, amt=0.35, cell=1)
    G = sub(s)
    G.poly([(8, 12.5), (21, 8), (25, 11), (12, 15.5)], (240, 170, 40, 170))   # honey glaze
    G.ell(14, 10, 16, 11.5, (255, 230, 150, 200))
    r = np.random.default_rng(312)
    for _ in range(6):
        x, y = r.uniform(9, 26), r.uniform(15, 24)
        G.ell(x, y, x + 1.4, y + 1.4, '#3a2412')
    G.line([(14, 8), (18, 25)], '#d8c8a0', 0.9)      # hide string
    s.put(G, amt=0, light=False)
    return s.finish('#24140a')


def sausages(s, col, dark, seed, smoked=False):
    for i, (x0, y0, x1, y1) in enumerate(((4, 20, 26, 12), (6, 25, 28, 17))):
        L = sub(s)
        n = 12
        pts_a, pts_b = [], []
        for k in range(n + 1):
            t = k / n
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t + math.sin(t * math.pi) * -1.6
            w = 2.6 * (0.75 + 0.25 * math.sin(t * math.pi))
            pts_a.append((x, y - w)); pts_b.append((x, y + w))
        L.poly(pts_a + pts_b[::-1], col)
        s.put(L, seed=seed + i, amt=0.35 if smoked else 0.25, cell=1)
        G = sub(s)
        G.line([(x0 + (x1 - x0) * 0.2, y0 + (y1 - y0) * 0.2 - 1.6), (x0 + (x1 - x0) * 0.75, y0 + (y1 - y0) * 0.75 - 2.4)],
               (255, 255, 255, 70 if not smoked else 50), 0.6)
        for t in (0.0, 1.0):
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            G.ell(x - 0.9, y - 0.9, x + 0.9, y + 0.9, dark)              # twisted casing ends
        s.put(G, amt=0, light=False)
    G = sub(s)
    G.line([(26, 12), (27.5, 9.5), (29, 10)], '#d8c8a0', 0.6)          # string
    s.put(G, amt=0, light=False)


def icon_smoked_sausage():
    s = Sprite()
    sausages(s, '#7a3a1c', '#3a1a0a', 321, True)
    return s.finish('#1e0e06')


def icon_raw_sausage():
    s = Sprite()
    sausages(s, '#c8706a', '#8a4440', 331)
    scatter(s, 333, 8, (8, 13, 24, 22), ['#f0d8d0'], (0.6, 0.9))
    return s.finish('#3a1814')


# ================================================================================================ Dutch oven block texture
def oven_texture():
    """16x16 cast iron: seasoned black iron with a dull sheen, rim highlights; a row of glowing coals and bail wire."""
    S = 16
    img = np.zeros((S, S, 4), float)
    n = noise(S * 4, S * 4, 3, 401)[::4, ::4]
    n2 = noise(S * 4, S * 4, 1, 402)[::4, ::4]
    base = np.array([46, 44, 42], float)
    img[..., :3] = base * (0.86 + 0.26 * n[..., None]) * (0.94 + 0.12 * n2[..., None])
    img[..., 3] = 255
    # rows 0-9: pot wall (vertical sheen, darker toward the bottom), 10 rim highlight, 11 lid edge, 12-13 coals, 14-15 iron
    for x in range(S):
        img[0:10, x, :3] *= 1.0 + 0.32 * math.exp(-((x - 4.5) / 2.0) ** 2)
    for y in range(10):
        img[y, :, :3] *= 1.08 - 0.03 * y
    img[10, :, :3] = img[10, :, :3] * 1.55
    img[11, :, :3] = img[11, :, :3] * 1.2
    r = np.random.default_rng(403)
    for x in range(S):
        for y in (12, 13):
            h = r.random()
            if h > 0.72:
                img[y, x, :3] = (255, 170 + 50 * h, 70)
            elif h > 0.3:
                img[y, x, :3] = (200 + 40 * h, 70 + 40 * h, 24)
            else:
                img[y, x, :3] = (58, 30, 22)
    img[14, :, :3] = (128, 126, 120)
    img[15, :, :3] = (78, 76, 72)
    for _ in range(4):
        x, y = r.integers(0, S), r.integers(0, 10)
        img[y, x, :3] = (64, 48, 40)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')


# ================================================================================================ mob effect icons
def effect_icon(kind, col):
    s = Sprite(18)
    L = sub(s)
    c = hexc(col)
    if kind == 'warmth':      # campfire flame
        L.poly([(9, 2), (13.5, 8), (13, 13), (9, 15.5), (5, 13), (4.5, 8.5), (7, 6)], col)
        L.poly([(9, 7), (11.2, 10.5), (10.6, 13.6), (9, 14.6), (7.2, 13.4), (7.2, 10.2)], '#ffd870')
        L.line([(3, 16), (15, 15)], '#6a4a2c', 1.4)
    elif kind == 'stamina':   # boot print + motion
        L.poly([(7, 3), (12, 3), (12.5, 9), (14.5, 12), (14.5, 15), (6.5, 15), (6.5, 10)], col)
        for y in (6, 9, 12):
            L.line([(1.5, y), (4.5, y)], col, 0.9)
    elif kind == 'steady':    # reticle
        L.ell(2.5, 2.5, 15.5, 15.5, col)
        L.ell(4.2, 4.2, 13.8, 13.8, (0, 0, 0, 0))
        L.rect(8.3, 1, 9.7, 7, col); L.rect(8.3, 11, 9.7, 17, col)
        L.rect(1, 8.3, 7, 9.7, col); L.rect(11, 8.3, 17, 9.7, col)
    elif kind == 'hearty':    # heart
        L.ell(2.5, 3.5, 9.5, 10.5, col); L.ell(8.5, 3.5, 15.5, 10.5, col)
        L.poly([(2.8, 8.4), (15.2, 8.4), (9, 15.5)], col)
        L.ell(4.5, 5, 6.5, 7, '#ffb0b0')
    elif kind == 'keen':      # eye over a track
        L.ell(2, 4, 16, 11, col)
        L.ell(6.5, 5.2, 11.5, 9.8, '#f4f0e0')
        L.ell(7.8, 6.2, 10.2, 8.8, '#1c1a16')
        for x in (5, 9, 13):
            L.ell(x - 1, 13, x + 1, 16, col)
    elif kind == 'quiet':     # feather
        L.poly([(14.5, 2), (16, 4), (8, 13.5), (4, 15), (5, 11)], col)
        L.line([(15, 3), (3, 16)], '#f4f0e0', 0.7)
    elif kind == 'masked':    # smoke wisps
        for i, x in enumerate((5, 9, 13)):
            L.line([(x, 16), (x - 1.5, 12), (x + 1, 8), (x - 0.5, 4), (x + 0.8, 1.5)], col, 1.4)
    s.put(L, seed=hash(kind) % 999, amt=0.1, cell=1)
    return s.finish('#1a1410')


def slot_hints():
    s = Sprite(16)
    img = Image.new('RGBA', (32, 16), (0, 0, 0, 0))
    b = Sprite(16)
    L = sub(b)
    L.ell(1.5, 6.5, 14.5, 14.5, (55, 55, 55, 255))
    L.rect(1.5, 6.5, 14.5, 10, (0, 0, 0, 0))
    L.ell(1.5, 5.5, 14.5, 10.5, (70, 70, 70, 255))
    L.ell(2.8, 6.4, 13.2, 9.6, (55, 55, 55, 255))
    b.put(L, amt=0, light=False)
    hint = b.img.resize((16, 16), Image.BOX)
    a = np.asarray(hint).copy()
    a[..., 3] = (a[..., 3] > 100) * 90
    img.alpha_composite(Image.fromarray(a, 'RGBA'), (0, 0))
    c = Sprite(16)
    L = sub(c)
    for (x, y) in ((3, 9), (8, 8), (12, 10), (5, 12), (10, 12.5)):
        L.ell(x - 2.4, y - 2, x + 2.4, y + 2, (55, 55, 55, 255))
    c.put(L, amt=0, light=False)
    hint = c.img.resize((16, 16), Image.BOX)
    a = np.asarray(hint).copy()
    a[..., 3] = (a[..., 3] > 100) * 90
    img.alpha_composite(Image.fromarray(a, 'RGBA'), (16, 0))
    return img


# ================================================================================================ main
def main():
    os.makedirs(ITEM, exist_ok=True)
    os.makedirs(PREV, exist_ok=True)
    icons = {'hunting_licence': icon_licence, 'upland_stamp': lambda: icon_stamp('upland'),
             'waterfowl_stamp': lambda: icon_stamp('waterfowl'), 'filled_tag': icon_filled_generic}
    for k in TAG_COLORS:
        if k == 'wolf':
            continue  # wolves are predators: no tag (the colour/print only dress the generic filled tag)
        icons[f'{k}_tag'] = (lambda k=k: icon_tag(k))
        icons[f'filled_tag_{k}'] = (lambda k=k: icon_tag(k, True))
    icons.update({'venison_stew': icon_venison_stew, 'venison_chili': icon_venison_chili, 'bear_pot_roast': icon_bear_pot_roast,
                  'backstrap_mushrooms': icon_backstrap_mushrooms, 'fowl_berry_roast': icon_fowl_berry_roast,
                  'fowl_wild_rice': icon_fowl_wild_rice, 'heart_liver_fry': icon_heart_liver_fry,
                  'hunters_breakfast': icon_hunters_breakfast, 'fish_chowder': icon_fish_chowder, 'camp_bannock': icon_bannock,
                  'honey_pemmican': icon_honey_pemmican, 'smoked_game_sausage': icon_smoked_sausage,
                  'raw_game_sausage': icon_raw_sausage})
    names = list(icons)
    cols = 8
    rows = (len(names) + cols - 1) // cols
    prev = Image.new('RGBA', (cols * 36, rows * 36), (139, 139, 139, 255))
    for i, n in enumerate(names):
        im = icons[n]()
        im.save(os.path.join(ITEM, n + '.png'))
        prev.alpha_composite(im, ((i % cols) * 36 + 2, (i // cols) * 36 + 2))
    prev.resize((prev.width * 4, prev.height * 4), Image.NEAREST).save(os.path.join(PREV, 'icons.png'))
    os.makedirs(os.path.join(A, 'block'), exist_ok=True)
    oven_texture().save(os.path.join(A, 'block/dutch_oven.png'))
    os.makedirs(os.path.join(A, 'mob_effect'), exist_ok=True)
    cols_e = {'warmth': '#e07a3a', 'stamina': '#d8b04a', 'steady': '#6a8ab0', 'hearty': '#c0404a', 'keen': '#5aa05a',
              'quiet': '#b0a0d0', 'masked': '#a8a89a'}
    eprev = Image.new('RGBA', (len(cols_e) * 20, 20), (90, 90, 90, 255))
    for i, (k, c) in enumerate(cols_e.items()):
        im = effect_icon(k, c)
        im.save(os.path.join(A, f'mob_effect/meal_{k}.png'))
        eprev.alpha_composite(im, (i * 20 + 1, 1))
    eprev.resize((eprev.width * 6, eprev.height * 6), Image.NEAREST).save(os.path.join(PREV, 'effects.png'))
    os.makedirs(os.path.join(A, 'gui/campcook'), exist_ok=True)
    slot_hints().save(os.path.join(A, 'gui/campcook/slot_hints.png'))
    print('ok', len(names), 'icons')


main()
