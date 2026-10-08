"""[ledger] Tiny pixel-art engine for the journal icons: hard-edged shapes rasterised at pixel centres on a 32x32 grid,
layered parts with banded light (upper-left key light), rim light / core shadow, dark separator lines between parts and
a 1 px warm-dark silhouette outline. No anti-aliasing anywhere, so the icons stay crisp at integer GUI scales.
"""
import math
import numpy as np
from PIL import Image

N = 32
_YY, _XX = np.mgrid[0:N, 0:N]
CX = _XX + 0.5
CY = _YY + 0.5

OUTLINE = (34, 22, 14)


def hexc(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*cols):
    """5 colours darkest -> lightest (index 2 = base)."""
    return [hexc(c) for c in cols]


# ------------------------------------------------------------------------------------------------ palette (warm, limited)
BRASS = ramp('#4a3014', '#7d5a24', '#b08a45', '#d9b566', '#f4dc98')
BRONZE = ramp('#40220f', '#6e3d1c', '#9b5f2e', '#c58450', '#e6b07a')
SILVER = ramp('#2c3034', '#555c60', '#868d8e', '#b6bcb8', '#e2e4dc')
GOLD = ramp('#4d3208', '#8a5c10', '#c99422', '#ecc24a', '#fbe68e')
LEATHER = ramp('#2a1b12', '#4a3022', '#6b4630', '#8f6646', '#b38b62')
WOOD = ramp('#33200f', '#5a381b', '#83552b', '#a8773e', '#caa063')
LOG = ramp('#2e1d10', '#4d321b', '#6f4a28', '#94683c', '#b88a55')
PAPER = ramp('#7c6c52', '#ad9d7c', '#d7cba8', '#ece2c6', '#faf4e2')
GREEN = ramp('#1b2a1a', '#2c4429', '#41633a', '#628a4c', '#90b166')
OLIVE = ramp('#2a2a14', '#47461f', '#6a6730', '#8f8a47', '#b8b169')
RED = ramp('#3b100d', '#6a1a15', '#9c2a1f', '#c74532', '#e8765a')
BLOOD = ramp('#3a0a0a', '#650f0f', '#951b17', '#c1302a', '#e05a4e')
STEEL = ramp('#22262a', '#3d4449', '#5f686d', '#8a9497', '#c3cbcb')
SLATE = ramp('#1c2a35', '#2c4558', '#456a80', '#6f98ad', '#a9cddb')
ICE = ramp('#24425a', '#3f6f8f', '#6aa0bf', '#a3cee0', '#e2f3f7')
FLAME = ramp('#5a1c0b', '#a63f10', '#e0781c', '#f6b23c', '#fde88e')
BONE = ramp('#4a3d2a', '#7c6c4f', '#b0a17d', '#d8cba5', '#f3ecd2')
DEER = ramp('#3a2414', '#6a4126', '#97633b', '#bd8a58', '#dcb383')
FUR = ramp('#22160e', '#3d2a1c', '#5a402b', '#7a5a3e', '#9c7a58')
MUD = ramp('#24180f', '#3f2b1b', '#5e4229', '#806040', '#a6835c')
CANVAS = ramp('#3a3a24', '#5c5a37', '#86804f', '#ada46c', '#d2c895')
STONE = ramp('#2a2826', '#46423d', '#686258', '#8e877a', '#b8b0a0')
BLAZE = ramp('#4d1c06', '#8c360c', '#d0581a', '#f08a36', '#fbbb6e')
INK = ramp('#141009', '#2a221a', '#3b3226', '#54493a', '#6e6250')
SKY = ramp('#2e4a62', '#4b7393', '#79a3bf', '#a9cadb', '#d8ecf2')
SAND = ramp('#4f3c22', '#7f653d', '#ae9160', '#cfb582', '#ebd8ab')
SOIL = ramp('#1a140e', '#2e241a', '#463829', '#62513d', '#7f6c54')
SILT = ramp('#26261f', '#3f4035', '#5c5d4f', '#7d7e6c', '#a2a38e')
SNOW = ramp('#5d7584', '#8ea8b5', '#c3d5dc', '#e3eef1', '#fbfeff')
WHITE = ramp('#6d6a62', '#a4a199', '#d2cfc6', '#ece9df', '#fbfaf4')


# ------------------------------------------------------------------------------------------------ shapes (bool masks)
def rect(x0, y0, x1, y1):
    return (CX >= x0) & (CX < x1) & (CY >= y0) & (CY < y1)


def ellipse(cx, cy, rx, ry, rot=0.0):
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    dx, dy = CX - cx, CY - cy
    u, v = dx * c + dy * s, -dx * s + dy * c
    return (u / rx) ** 2 + (v / ry) ** 2 <= 1.0


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def ring(cx, cy, r0, r1):
    d = np.hypot(CX - cx, CY - cy)
    return (d >= r0) & (d <= r1)


def poly(pts):
    m = np.zeros((N, N), bool)
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        if y0 == y1:
            continue
        cond = ((y0 > CY) != (y1 > CY))
        xint = (x1 - x0) * (CY - y0) / (y1 - y0 + 1e-12) + x0
        m ^= cond & (CX < xint)
    return m


def seg(x0, y0, x1, y1, w):
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    if L2 == 0:
        return np.hypot(CX - x0, CY - y0) <= w / 2
    t = np.clip(((CX - x0) * dx + (CY - y0) * dy) / L2, 0, 1)
    px, py = x0 + t * dx, y0 + t * dy
    return np.hypot(CX - px, CY - py) <= w / 2


def path(pts, w):
    m = np.zeros((N, N), bool)
    for a, b in zip(pts, pts[1:]):
        m |= seg(a[0], a[1], b[0], b[1], w)
    return m


def taper(pts, w0, w1):
    """Stroke whose width goes w0 -> w1 along the path (antler tines, flames, blades)."""
    m = np.zeros((N, N), bool)
    L = [0.0]
    for a, b in zip(pts, pts[1:]):
        L.append(L[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    tot = L[-1] or 1.0
    for i, (a, b) in enumerate(zip(pts, pts[1:])):
        steps = max(2, int((L[i + 1] - L[i]) * 3))
        for k in range(steps + 1):
            t = k / steps
            x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            f = (L[i] + (L[i + 1] - L[i]) * t) / tot
            r = (w0 + (w1 - w0) * f) / 2
            m |= np.hypot(CX - x, CY - y) <= r
    return m


def bez(p0, p1, p2, p3=None, n=16):
    out = []
    for i in range(n + 1):
        t = i / n
        if p3 is None:
            x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
            y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        else:
            x = (1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0]
            y = (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1]
        out.append((x, y))
    return out


def star(cx, cy, r0, r1, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        a = math.radians(rot + i * 180 / n)
        r = r1 if i % 2 == 0 else r0
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return poly(pts)


def rot_pts(pts, cx, cy, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def mirror(m):
    return m | m[:, ::-1]


# ------------------------------------------------------------------------------------------------ icon
class Icon:
    def __init__(self, light=(-1.0, -1.0)):
        self.layers = []
        self.pixels = []
        lx, ly = light
        n = math.hypot(lx, ly)
        self.light = (lx / n, ly / n)

    def add(self, mask, rmp, shade=0.9, rim=True, core=True, edge=False, flat=None, sep=True, band=None, light=None, concave=False):
        """A part. shade = strength of the banded gradient (0 = flat), rim/core = edge light/shadow,
        edge = draw a dark separator on the parts underneath along this part's border,
        flat = force a ramp index for every pixel (details / lines)."""
        self.layers.append(dict(mask=mask.copy(), ramp=rmp, shade=shade, rim=rim, core=core, edge=edge, flat=flat, sep=sep, band=band, light=light, concave=concave))
        return self

    def line(self, mask, rmp, idx=0):
        return self.add(mask, rmp, flat=idx, rim=False, core=False)

    def cut(self, mask):
        self.layers.append(dict(mask=mask.copy(), ramp=None))
        return self

    def px(self, x, y, col):
        self.pixels.append((x, y, col))
        return self

    def render(self, outline=OUTLINE):
        lab = -np.ones((N, N), int)
        for i, L in enumerate(self.layers):
            lab[L['mask']] = i if L['ramp'] is not None else -1
        img = np.zeros((N, N, 4), np.uint8)
        for i, L in enumerate(self.layers):
            if L['ramp'] is None:
                continue
            m = lab == i
            if not m.any():
                continue
            R = L['ramp']
            ys, xs = np.nonzero(m)
            if L['flat'] is not None:
                idx = np.full(len(xs), L['flat'])
            else:
                # banded gradient across the part along the light direction
                lx, ly = L['light'] or self.light
                bx = L['band'] if L['band'] is not None else (xs.min(), xs.max(), ys.min(), ys.max())
                cxm, cym = (bx[0] + bx[1]) / 2, (bx[2] + bx[3]) / 2
                ext = max(1.0, (abs((bx[1] - bx[0]) * lx) + abs((bx[3] - bx[2]) * ly)) / 2)
                v = ((xs - cxm) * lx + (ys - cym) * ly) / ext  # +1 toward the light
                idx = 2 + np.round(np.clip(v * L['shade'], -1, 1)).astype(int)
                full = np.ones((N + 2, N + 2), int) * -1
                full[1:-1, 1:-1] = lab
                # neighbours toward / away from the light
                up = full[ys, xs + 1]
                left = full[ys + 1, xs]
                down = full[ys + 2, xs + 1]
                right = full[ys + 1, xs + 2]
                if L['concave']:
                    idx = np.where((up != i) | (left != i), idx - 1, idx)
                    idx = np.where(((down != i) | (right != i)) & (up == i) & (left == i), idx + 1, idx)
                else:
                    if L['rim']:
                        idx = np.where((up != i) | (left != i), idx + 1, idx)
                    if L['core']:
                        idx = np.where(((down != i) | (right != i)) & (up == i) & (left == i), idx - 1, idx)
                idx = np.clip(idx, 0, 4)
            for x, y, k in zip(xs, ys, idx):
                img[y, x, :3] = R[int(k)]
                img[y, x, 3] = 255
        # dark separators on lower parts along the border of parts flagged edge=True
        full = -np.ones((N + 2, N + 2), int)
        full[1:-1, 1:-1] = lab
        for i, L in enumerate(self.layers):
            if L['ramp'] is None or not L['sep']:
                continue
            m = lab == i
            for j in range(i + 1, len(self.layers)):
                if not self.layers[j].get('edge'):
                    continue
                nb = np.zeros((N, N), bool)
                for dy, dx in ((0, 1), (2, 1), (1, 0), (1, 2)):
                    nb |= full[dy:dy + N, dx:dx + N] == j
                sel = m & nb
                img[sel, :3] = L['ramp'][0]
        alpha = img[:, :, 3] > 0
        pad = np.zeros((N + 2, N + 2), bool)
        pad[1:-1, 1:-1] = alpha
        dil = pad[0:N, 1:N + 1] | pad[2:N + 2, 1:N + 1] | pad[1:N + 1, 0:N] | pad[1:N + 1, 2:N + 2]
        out = dil & ~alpha
        img[out, :3] = outline
        img[out, 3] = 255
        for x, y, c in self.pixels:
            if c is None:
                img[y, x] = 0
            else:
                img[y, x, :3] = c[:3]
                img[y, x, 3] = c[3] if len(c) > 3 else 255
        return Image.fromarray(img, 'RGBA')


def brighten(im, v=1.16, s=1.06, warm=6):
    """Hover / selected variant: lifted value, a touch warmer; the outline stays dark."""
    a = np.asarray(im).astype(np.float32)
    rgb = a[:, :, :3] / 255.0
    mx = rgb.max(axis=2, keepdims=True)
    mn = rgb.min(axis=2, keepdims=True)
    outline = (a[:, :, 0] == OUTLINE[0]) & (a[:, :, 1] == OUTLINE[1]) & (a[:, :, 2] == OUTLINE[2])
    grey = rgb.mean(axis=2, keepdims=True)
    rgb2 = grey + (rgb - grey) * s
    rgb2 = rgb2 * v + np.array([warm, warm * 0.5, -warm * 0.3]) / 255.0
    rgb2 = np.clip(rgb2, 0, 1)
    rgb2[outline] = rgb[outline]
    out = a.copy()
    out[:, :, :3] = rgb2 * 255
    return Image.fromarray(out.astype(np.uint8), 'RGBA')
