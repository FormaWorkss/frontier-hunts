"""[artqa] Item icons that were wrong or below the set's quality, in the mod's 32x32 icon style (soft-shaded pixel art,
1 px dark warm outline, light from the top-left, transparent background):

  aged_venison   "Aged Venison Quarter" was a round salami slice; now the venison quarter's silhouette, dry-aged
                 (dark burgundy crust, yellowed fat) so it reads as the same cut, aged.
  paraglider     16x16 line drawing among 32x32 icons; now a ram-air canopy with cell stripes, risers and a pilot.
  forest_litter  used the 64x64 opaque block texture (an opaque square in the inventory); now a leaf pile.
  undergrowth    used the 64x64 bracken block texture cropped flat at the tile edges (creative tab icon of the
                 Expedition tab); now a bracken clump built from the mod's own fern icons (same plant-icon style).

python3 tools/artqa/item_icons.py <repo root> <base jar or extracted dir with the current textures>
"""
import math, os, sys, zipfile, io
import numpy as np
from PIL import Image, ImageDraw

K = 8
N = 32


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
    """Same construction as tools/survival/icons.py: flat shapes at 8x, shaded, box-downsampled, outlined."""

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

    def line(self, pts, c, w=1.0):
        self.d.line(self.P(pts), fill=hexc(c) if isinstance(c, str) else c, width=max(1, int(w * K)))

    def put(self, other, seed=0, amt=0.25, cell=3, light=True):
        a = np.asarray(other.img).astype(float)
        if amt:
            n = noise(self.S, self.S, cell * K, seed)
            a[..., :3] *= (1 + (n[..., None] - 0.5) * amt)
        if light:
            yy, xx = np.mgrid[0:self.S, 0:self.S] / self.S
            a[..., :3] *= (1.12 - 0.24 * (xx * 0.4 + yy * 0.6))[..., None]
        self.img.alpha_composite(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA'))

    def finish(self, outline='#2a1c12'):
        small = self.img.resize((self.n, self.n), Image.BOX)
        a = np.asarray(small).astype(float)
        alpha = a[..., 3]
        rgb = a[..., :3] / np.maximum(alpha[..., None] / 255.0, 1e-3)
        mask = alpha > 110
        return outline_px(rgb, mask, outline)


def outline_px(rgb, mask, outline='#2a1c12'):
    n = mask.shape[0]
    out = np.zeros((n, n, 4))
    out[mask, :3] = np.clip(rgb[mask], 0, 255)
    out[mask, 3] = 255
    oc = np.array(hexc(outline)[:3], float)
    o = np.zeros_like(mask)   # 4-neighbour dilation without wrap-around
    o[1:] |= mask[:-1]; o[:-1] |= mask[1:]; o[:, 1:] |= mask[:, :-1]; o[:, :-1] |= mask[:, 1:]
    o &= ~mask
    out[o, :3] = oc
    out[o, 3] = 255
    edge_br = mask & ~(np.roll(mask, -1, 0) & np.roll(mask, -1, 1))
    edge_tl = mask & ~(np.roll(mask, 1, 0) & np.roll(mask, 1, 1))
    out[edge_br, :3] *= 0.78
    out[edge_tl & ~edge_br, :3] = np.minimum(255, out[edge_tl & ~edge_br, :3] * 1.12)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')


# ------------------------------------------------------------------------------------------------ aged venison
def aged_venison(quarter):
    """Re-grade the venison quarter: fresh red -> dry-aged burgundy/brown crust, fat -> ivory-yellow, bone kept."""
    a = np.asarray(quarter.convert('RGBA')).astype(float)
    rgb, al = a[..., :3], a[..., 3]
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    out = rgb.copy()
    meat = (al > 0) & (r > g * 1.35) & (r > 70)
    lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
    crust = noise(32, 32, 3, 77) * 0.5 + noise(32, 32, 1, 78) * 0.5
    base = np.stack([96 + 70 * lum, 36 + 26 * lum, 30 + 18 * lum], -1)
    base *= (0.85 + 0.3 * crust)[..., None]
    out[meat] = base[meat]
    fat = (al > 0) & ~meat & (r > 170) & (g > 150) & (b > 120) & (r - b < 70)
    out[fat] = out[fat] * np.array([0.98, 0.92, 0.74])
    res = np.dstack([out.clip(0, 255), al]).astype(np.uint8)
    return Image.fromarray(res, 'RGBA')


# ------------------------------------------------------------------------------------------------ paraglider
def paraglider():
    s = Sprite()
    L = Sprite()
    # canopy: a shallow arch seen from the front-below, leading edge on top
    top = [(2 + 28 * t, 12 - 9.5 * math.sin(math.pi * t)) for t in np.linspace(0, 1, 17)]
    bot = [(5 + 22 * t, 14.5 - 7.5 * math.sin(math.pi * t)) for t in np.linspace(1, 0, 17)]
    L.poly(top + bot, '#f08a24')
    # cell stripes (alternate colours across the span)
    for i in range(8):
        if i % 2:
            continue
        t0, t1 = i / 8, (i + 1) / 8
        q = [(2 + 28 * t0, 12 - 9.5 * math.sin(math.pi * t0)), (2 + 28 * t1, 12 - 9.5 * math.sin(math.pi * t1)),
             (5 + 22 * t1, 14.5 - 7.5 * math.sin(math.pi * t1)), (5 + 22 * t0, 14.5 - 7.5 * math.sin(math.pi * t0))]
        L.poly(q, '#e8e2d4')
    # wing tips in navy
    L.poly([top[0], top[1], bot[-2], bot[-1]], '#283c6c')
    L.poly([top[-2], top[-1], bot[0], bot[1]], '#283c6c')
    s.put(L, seed=3, amt=0.12, cell=2)
    # lines: thin, drawn after the canopy so they are not outlined into a blob
    lines = Sprite()
    for x0 in (5.5, 9.5, 13.5):
        lines.line([(x0, 13.5 - 6 * math.sin(math.pi * (x0 - 5) / 22)), (15.2, 22.5)], '#5a5248', 0.5)
    for x0 in (18.5, 22.5, 26.5):
        lines.line([(x0, 13.5 - 6 * math.sin(math.pi * (x0 - 5) / 22)), (16.8, 22.5)], '#5a5248', 0.5)
    # pilot in the harness
    P = Sprite()
    P.ell(14.2, 20.4, 17.8, 24.0, '#e86a20')   # helmet / jacket blaze
    P.poly([(13.6, 23.6), (18.4, 23.6), (18.8, 27.6), (13.2, 27.6)], '#3a4a2e')   # harness pod
    s.put(P, seed=5, amt=0.1, cell=2)
    icon = s.finish()
    ln = np.asarray(lines.img.resize((N, N), Image.BOX)).astype(float)
    ic = np.asarray(icon).astype(float)
    m = (ln[..., 3] > 50) & (ic[..., 3] < 10)
    ic[m, :3] = (70, 64, 56)
    ic[m, 3] = 255
    return Image.fromarray(ic.astype(np.uint8), 'RGBA')


# ------------------------------------------------------------------------------------------------ leaf litter
def leaf(L, cx, cy, ln, ang, col):
    c, s_ = math.cos(ang), math.sin(ang)
    pts = []
    for t in np.linspace(0, math.pi * 2, 14):
        x, y = math.cos(t) * ln / 2, math.sin(t) * ln / 5.0 * (1.15 if math.cos(t) < 0 else 0.85)
        pts.append((cx + x * c - y * s_, cy + x * s_ + y * c))
    L.poly(pts, col)
    L.line([(cx - c * ln / 2, cy - s_ * ln / 2), (cx + c * ln / 2, cy + s_ * ln / 2)], tuple(int(v * 0.72) for v in hexc(col)[:3]) + (255,), 0.25)


def forest_litter():
    s = Sprite()
    r = np.random.default_rng(11)
    cols = ['#b8762e', '#8e5a26', '#c89a44', '#a8442a', '#7a6a34', '#d0a050']
    L = Sprite()
    # a low mound of leaves (back to front) with a couple of twigs
    L.poly([(3, 26), (6, 19), (12, 15), (20, 14), (26, 18), (29, 25), (24, 28), (8, 28)], '#6e4a26')
    s.put(L, seed=1, amt=0.3, cell=2)
    L2 = Sprite()
    for i in range(16):
        t = i / 16
        x = r.uniform(6, 26)
        y = 25.5 - 8 * math.sin(math.pi * (x - 3) / 26) * r.uniform(0.5, 1.0) + t * 2.5
        leaf(L2, x, y, r.uniform(8.0, 10.5), r.uniform(-0.6, 0.6) + (0 if i % 2 else math.pi / 2.5), cols[i % len(cols)])
    L2.line([(6, 20), (14, 17.5)], '#4a3020', 0.8)
    L2.line([(19, 22), (27, 20)], '#4a3020', 0.7)
    s.put(L2, seed=2, amt=0.25, cell=2)
    return s.finish()


# ------------------------------------------------------------------------------------------------ undergrowth (bracken)
def frond(L, base, ang, ln, width, col, seed, curl=0.35):
    """A bracken frond: a tapering blade with serrated (pinnate) edges along a curving rachis."""
    bx, by = base
    spine = []
    for t in np.linspace(0, 1, 16):
        a = ang + curl * t * (1 if math.cos(ang) >= 0 else -1)
        if not spine:
            spine.append((bx, by))
        else:
            px, py = spine[-1]
            spine.append((px + math.cos(a) * ln / 15, py + math.sin(a) * ln / 15))
    left, right = [], []
    for i, (x, y) in enumerate(spine):
        t = i / 15
        x2, y2 = spine[min(i + 1, 15)]
        x1, y1 = spine[max(i - 1, 0)]
        dx, dy = x2 - x1, y2 - y1
        d = math.hypot(dx, dy) or 1
        nx, ny = -dy / d, dx / d
        w = width * math.sin(math.pi * min(1.0, 0.15 + t)) * (1 - t * 0.85) * (1.0 if t > 0.12 else t / 0.12)
        w *= 1.0 if i % 2 else 0.55   # serration
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    L.poly(left + right[::-1], col)
    return spine


def undergrowth(arching, spreading):
    """Bracken clump in the plant-icon style of the mod's other fern items (no outline, like vanilla plants):
    the spreading fern, darkened and set back, under the arching fern re-tinted toward bracken's yellow-green."""
    sp = np.asarray(spreading.convert('RGBA')).astype(float)
    ar = np.asarray(arching.convert('RGBA')).astype(float)
    back = sp.copy()
    back[..., :3] *= np.array([0.70, 0.78, 0.62])
    back = np.roll(back, -1, 0)
    front = ar.copy()
    front[..., :3] = front[..., :3] * np.array([1.08, 1.04, 0.80]) + np.array([10, 6, 0])
    out = back.copy()
    a = front[..., 3:4] / 255.0
    out[..., :3] = front[..., :3] * a + back[..., :3] * (1 - a)
    out[..., 3] = np.maximum(front[..., 3], back[..., 3])
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), 'RGBA')


def read_png(src, path):
    if os.path.isdir(src):
        return Image.open(os.path.join(src, path))
    with zipfile.ZipFile(src) as z:
        return Image.open(io.BytesIO(z.read(path)))


if __name__ == '__main__':
    root, src = sys.argv[1], sys.argv[2]
    tex = os.path.join(root, 'patch/assets/frontierhunts/textures/item')
    os.makedirs(tex, exist_ok=True)
    q = read_png(src, 'assets/frontierhunts/textures/item/venison_quarter.png')
    out = {'aged_venison': aged_venison(q), 'paraglider': paraglider(), 'forest_litter': forest_litter(),
           'undergrowth': undergrowth(read_png(src, 'assets/frontierhunts/textures/item/arching_fern.png'),
                                      read_png(src, 'assets/frontierhunts/textures/item/spreading_fern.png'))}
    for n, im in out.items():
        im.save(os.path.join(tex, n + '.png'), optimize=True)
        print('wrote', n)


QUIVER = [  # Curios "quiver" empty-slot icon: outline quiver with three fletched arrows (old one: an arrow without a head)
    '................',
    '...#...#...#....',
    '..#.#.#.#.#.#...',
    '...#...#...#....',
    '...#...#...#....',
    '..###########...',
    '..#.........#...',
    '..#.........#...',
    '..###########...',
    '..#.........#...',
    '..#.........#...',
    '..#.........#...',
    '..#.........#...',
    '...#.......#....',
    '....#######.....',
    '................',
]


def quiver_slot():
    a = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(QUIVER):
        for x, ch in enumerate(row):
            if ch == '#':
                a[y, x] = (85, 85, 85, 255)
    return Image.fromarray(a, 'RGBA')


if __name__ == '__main__':
    p = os.path.join(sys.argv[1], 'patch/assets/frontierhunts/textures/slot/quiver_slot.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    quiver_slot().save(p)
    print('wrote quiver_slot')
