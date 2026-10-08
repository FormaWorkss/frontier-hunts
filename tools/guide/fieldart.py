"""[fieldbook] Ink-and-watercolour field-guide rendering engine for the Field School plates and icons.

Everything is painted supersampled (S x the output size) into a premultiplied float canvas and downsampled with a
premultiplied Lanczos filter plus a light unsharp mask, so ink lines come out crisp and washes stay soft.

The look (shared by every plate, so the book reads as one set):
* washes  - transparent watercolour: low-frequency pigment variation, paper granulation and darker pooled edges
* form    - soft modelling from an upper-left light (gradient of the blurred shape), never airbrushed gradients
* ink     - warm sepia contour whose weight swells on the shadow side (light 0.8 px -> shadow 2 px at output size)
* detail  - fine tapered pen strokes (fur, grass, bark) at low opacity
Backgrounds stay transparent: the guide's parchment shows through like the paper of a printed field guide.
"""
import math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

# ---------------------------------------------------------------------------------------------------------- palette
INK = (44, 33, 24)
INK_SOFT = (96, 78, 58)
SEPIA = (128, 100, 70)
PAPER = (234, 227, 208)
WHITE = (247, 243, 232)

COAT_DORSAL = (96, 76, 56)        # whitetail fall/winter coat, grey-brown
COAT_SIDE = (138, 108, 78)
COAT_LOW = (168, 138, 104)
COAT_LEG = (150, 112, 74)
BELLY = (240, 234, 220)
NOSE = (36, 30, 28)
ANTLER = (214, 196, 160)
ANTLER_DARK = (150, 126, 92)
HOOF = (52, 44, 38)

BLOOD_BRIGHT = (196, 22, 26)
BLOOD_LUNG = (226, 84, 92)
BLOOD_DARK = (104, 18, 20)
BLOOD_LIVER = (92, 22, 24)
GUT_MATTER = (112, 116, 58)

LUNG = (230, 150, 150)
LUNG_DARK = (196, 104, 108)
HEART = (176, 36, 40)
LIVER = (104, 34, 34)
RUMEN = (170, 160, 112)
INTESTINE = (196, 168, 120)
BONE = (238, 230, 210)

SKY_BLUE = (120, 156, 180)
WIND_BLUE = (52, 98, 134)
WIND_SOFT = (128, 168, 196)
SCENT = (208, 150, 70)
SCENT_DARK = (168, 104, 36)
WARM = (214, 120, 60)
COOL = (78, 118, 160)

GRASS = (128, 140, 82)
GRASS_DARK = (84, 100, 58)
FOLIAGE = (92, 118, 70)
FOLIAGE_DARK = (54, 78, 50)
PINE = (60, 88, 66)
PINE_DARK = (36, 58, 46)
BARK = (110, 88, 66)
BARK_DARK = (72, 56, 42)
WOOD_PALE = (232, 214, 170)
SOIL = (122, 92, 64)
SOIL_DARK = (84, 60, 40)
SOIL_DAMP = (96, 70, 48)
MUD = (150, 124, 92)
SNOW = (236, 240, 244)
ROCK = (150, 146, 134)

CAMO = (104, 104, 74)
CAMO_DARK = (70, 72, 50)
CAMO_LIGHT = (140, 134, 98)
PANTS = (110, 96, 72)
BLAZE = (232, 104, 36)
SKIN = (212, 168, 132)
BOOT = (74, 56, 40)
STEEL = (70, 72, 74)
STOCK = (118, 84, 56)
CANVAS_TAN = (196, 178, 136)
RED_GEAR = (170, 46, 36)

LIGHT = (-0.55, -0.83)   # light from the upper left (x right, y down)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def darker(c, f=0.8):
    return tuple(int(v * f) for v in c)


def lighter(c, f=0.3):
    return tuple(int(v + (255 - v) * f) for v in c)


# ---------------------------------------------------------------------------------------------------------- curves
def spline(points, closed=False, n=10):
    """Centripetal Catmull-Rom through the points (closed or open)."""
    pts = [tuple(map(float, p)) for p in points]
    if len(pts) < 3:
        return pts
    if closed:
        P = [pts[-1]] + pts + [pts[0], pts[1]]
    else:
        P = [(2 * pts[0][0] - pts[1][0], 2 * pts[0][1] - pts[1][1])] + pts + [(2 * pts[-1][0] - pts[-2][0], 2 * pts[-1][1] - pts[-2][1])]
    out = []

    def tj(ti, a, b):
        return ti + max(1e-6, math.dist(a, b)) ** 0.5

    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        t0 = 0.0
        t1 = tj(t0, p0, p1)
        t2 = tj(t1, p1, p2)
        t3 = tj(t2, p2, p3)
        for k in range(n):
            t = t1 + (t2 - t1) * k / n
            def lerp(a, b, ta, tb):
                if tb - ta < 1e-9:
                    return a
                return ((tb - t) / (tb - ta) * a[0] + (t - ta) / (tb - ta) * b[0], (tb - t) / (tb - ta) * a[1] + (t - ta) / (tb - ta) * b[1])
            A1 = lerp(p0, p1, t0, t1)
            A2 = lerp(p1, p2, t1, t2)
            A3 = lerp(p2, p3, t2, t3)
            B1 = lerp(A1, A2, t0, t2)
            B2 = lerp(A2, A3, t1, t3)
            out.append(lerp(B1, B2, t1, t2))
    if not closed:
        out.append(pts[-1])
    return out


def resample(seq, step):
    out = [seq[0]]
    for i in range(1, len(seq)):
        a, b = seq[i - 1], seq[i]
        d = math.dist(a, b)
        n = max(1, int(math.ceil(d / step)))
        for k in range(1, n + 1):
            t = k / n
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return out


def arclen(seq):
    return sum(math.dist(seq[i - 1], seq[i]) for i in range(1, len(seq)))


class Xf:
    """Model space (metres, y up) -> plate px. facing=-1 mirrors x (model drawn facing right becomes facing left)."""

    def __init__(self, ox, oy, s, facing=1, rot=0.0, sy=None):
        self.ox, self.oy, self.s, self.f, self.rot = ox, oy, s, facing, rot
        self.sy = s if sy is None else sy

    def __call__(self, pts):
        c, sn = math.cos(self.rot), math.sin(self.rot)
        out = []
        for x, y in pts:
            x *= self.f
            x, y = x * c - y * sn, x * sn + y * c
            out.append((self.ox + x * self.s, self.oy - y * self.sy))
        return out

    def p(self, x, y):
        return self([(x, y)])[0]


# ---------------------------------------------------------------------------------------------------------- noise
def _fbm(shape, scales, rng):
    H, W = shape
    acc = np.zeros(shape, np.float32)
    amp_total = 0.0
    for s, amp in scales:
        h = max(2, int(H / s) + 3)
        w = max(2, int(W / s) + 3)
        r = rng.standard_normal((h, w)).astype(np.float32)
        z = ndimage.zoom(r, (H / (h - 2), W / (w - 2)), order=3)[:H, :W]
        if z.shape != shape:
            z = np.pad(z, ((0, H - z.shape[0]), (0, W - z.shape[1])), mode='edge')
        acc += z * amp
        amp_total += amp
    acc /= max(1e-6, acc.std())
    return np.clip(acc / 2.5, -1, 1)


# ---------------------------------------------------------------------------------------------------------- plate
class Plate:
    def __init__(self, w, h, seed=1, S=3):
        self.w, self.h, self.S = w, h, S
        self.W, self.H = w * S, h * S
        self.C = np.zeros((self.H, self.W, 3), np.float32)  # premultiplied colour 0..1
        self.A = np.zeros((self.H, self.W), np.float32)
        self.rng = random.Random(seed)
        nrng = np.random.default_rng(seed)
        self.n_lo = _fbm((self.H, self.W), [(70 * S, 1.0), (30 * S, 0.6), (13 * S, 0.35)], nrng)
        self.n_mid = _fbm((self.H, self.W), [(6 * S, 1.0), (3 * S, 0.5)], nrng)
        self.grain = _fbm((self.H, self.W), [(0.9 * S, 1.0), (2.2 * S, 0.6)], nrng)
        self.clip = None   # optional float mask (H, W) every paint operation is multiplied by

    def set_clip(self, m):
        self.clip = None if m is None else np.asarray(m, np.float32) / 255.0

    # ------------------------------------------------------------------ masks (PIL 'L' at S resolution)
    def mask(self):
        return Image.new('L', (self.W, self.H), 0)

    def P(self, seq):
        S = self.S
        return [(x * S, y * S) for x, y in seq]

    def poly(self, seq, m=None, smooth=True, n=8, value=255):
        m = m if m is not None else self.mask()
        pts = spline(seq, True, n) if smooth else seq
        ImageDraw.Draw(m).polygon(self.P(pts), fill=value)
        return m

    def ellipse(self, cx, cy, rx, ry, m=None, rot=0.0, value=255):
        m = m if m is not None else self.mask()
        if rot == 0.0:
            S = self.S
            ImageDraw.Draw(m).ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=value)
        else:
            c, s = math.cos(rot), math.sin(rot)
            pts = [(cx + rx * math.cos(t) * c - ry * math.sin(t) * s, cy + rx * math.cos(t) * s + ry * math.sin(t) * c)
                   for t in np.linspace(0, 2 * math.pi, 72, endpoint=False)]
            ImageDraw.Draw(m).polygon(self.P(pts), fill=value)
        return m

    def stroke_mask(self, seq, widths, m=None, smooth=True, n=8, cap=True):
        """Variable-width stroke (widths: number, (w0, w1) or (w0, wmid, w1) in output px)."""
        m = m if m is not None else self.mask()
        pts = spline(seq, False, n) if (smooth and len(seq) > 2) else list(seq)
        pts = resample(pts, 0.6)
        N = len(pts)
        if isinstance(widths, (int, float)):
            wf = lambda t: widths
        else:
            ws = list(widths)
            ts = np.linspace(0, 1, len(ws))
            wf = lambda t: float(np.interp(t, ts, ws))
        d = ImageDraw.Draw(m)
        S = self.S
        L = [0.0]
        for i in range(1, N):
            L.append(L[-1] + math.dist(pts[i - 1], pts[i]))
        tot = max(1e-6, L[-1])
        prev = None
        for i in range(N):
            w = max(0.05, wf(L[i] / tot)) * S / 2
            x, y = pts[i][0] * S, pts[i][1] * S
            if prev is not None:
                px, py, pw = prev
                dx, dy = x - px, y - py
                ln = math.hypot(dx, dy) or 1
                nx, ny = -dy / ln, dx / ln
                d.polygon([(px + nx * pw, py + ny * pw), (x + nx * w, y + ny * w), (x - nx * w, y - ny * w), (px - nx * pw, py - ny * pw)], fill=255)
            if cap or (0 < i < N - 1):
                if w > 0.6:
                    d.ellipse([x - w, y - w, x + w, y + w], fill=255)
            prev = (x, y, w)
        return m

    # ------------------------------------------------------------------ mask ops
    @staticmethod
    def union(*ms):
        out = ms[0].copy()
        arr = np.asarray(out, np.uint8).copy()
        for m in ms[1:]:
            arr = np.maximum(arr, np.asarray(m, np.uint8))
        return Image.fromarray(arr, 'L')

    @staticmethod
    def minus(a, b):
        arr = np.asarray(a, np.int16) - np.asarray(b, np.int16)
        return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'L')

    @staticmethod
    def inter(a, b):
        arr = np.minimum(np.asarray(a, np.uint8), np.asarray(b, np.uint8))
        return Image.fromarray(arr, 'L')

    def blur(self, m, px):
        return m.filter(ImageFilter.GaussianBlur(px * self.S))

    def grow(self, m, px):
        r = int(round(px * self.S))
        if r <= 0:
            return m
        return m.filter(ImageFilter.MaxFilter(2 * min(r, 12) + 1)) if r <= 12 else Image.fromarray(
            (ndimage.distance_transform_edt(np.asarray(m) < 128) <= r).astype(np.uint8) * 255, 'L')

    def rough(self, m, px=2.0, freq='mid'):
        """Roughen a mask edge with noise (brush / torn-edge look). px = displacement amplitude in output px."""
        a = np.asarray(m, np.float32) / 255.0
        a = ndimage.gaussian_filter(a, px * self.S * 0.8)
        n = self.n_mid if freq == 'mid' else self.n_lo
        a = a + n * 0.22
        return Image.fromarray((np.clip((a - 0.5) * 6 + 0.5, 0, 1) * 255).astype(np.uint8), 'L')

    # ------------------------------------------------------------------ compositing
    def _bbox(self, m, pad=0):
        bb = m.getbbox()
        if bb is None:
            return None
        p = int(pad * self.S)
        return (max(0, bb[0] - p), max(0, bb[1] - p), min(self.W, bb[2] + p), min(self.H, bb[3] + p))

    def _comp(self, bb, col, alpha):
        x0, y0, x1, y1 = bb
        A = self.A[y0:y1, x0:x1]
        C = self.C[y0:y1, x0:x1]
        a = np.clip(alpha, 0, 1)
        if self.clip is not None:
            a = a * self.clip[y0:y1, x0:x1]
        C *= (1 - a)[..., None]
        C += col * a[..., None]
        A *= (1 - a)
        A += a

    def _col(self, bb, color, grad=None):
        x0, y0, x1, y1 = bb
        if grad is None:
            return np.broadcast_to(np.array(color, np.float32) / 255.0, (y1 - y0, x1 - x0, 3)).copy()
        stops, (gx0, gy0, gx1, gy1) = grad   # linear gradient between two points (output px), stops [(t, color)]
        ys, xs = np.mgrid[y0:y1, x0:x1].astype(np.float32) / self.S
        dx, dy = gx1 - gx0, gy1 - gy0
        L2 = dx * dx + dy * dy or 1.0
        t = np.clip(((xs - gx0) * dx + (ys - gy0) * dy) / L2, 0, 1)
        out = np.zeros((y1 - y0, x1 - x0, 3), np.float32)
        ts = [s[0] for s in stops]
        for c in range(3):
            out[..., c] = np.interp(t, ts, [s[1][c] / 255.0 for s in stops])
        return out

    def wash(self, m, color, opacity=1.0, edge=0.24, edge_w=1.3, var=0.09, grain=0.14, grad=None, soft=0.35, bleed=0.0):
        """Watercolour wash inside mask m: pigment variation, granulation and a darker pooled edge."""
        bb = self._bbox(m, 3 + bleed * 2)
        if bb is None:
            return
        x0, y0, x1, y1 = bb
        a = np.asarray(m.crop(bb), np.float32) / 255.0
        if bleed > 0:
            a = ndimage.gaussian_filter(a, bleed * self.S)
            a = np.clip(a + self.n_mid[y0:y1, x0:x1] * 0.18 * (a > 0.02), 0, 1)
        elif soft > 0:
            a = ndimage.gaussian_filter(a, soft * self.S)
        col = self._col(bb, color, grad)
        if edge:
            d = ndimage.distance_transform_edt(a > 0.5) / self.S
            pool = np.exp(-d / edge_w)
            val = 1 + var * self.n_lo[y0:y1, x0:x1] + var * 0.5 * self.n_mid[y0:y1, x0:x1] - edge * pool
        else:
            val = 1 + var * self.n_lo[y0:y1, x0:x1] + var * 0.5 * self.n_mid[y0:y1, x0:x1]
        col = np.clip(col * val[..., None], 0, 1)
        alpha = a * opacity * (1 - grain * (0.5 + 0.5 * self.grain[y0:y1, x0:x1]))
        self._comp(bb, col, alpha)

    def flat(self, m, color, opacity=1.0):
        bb = self._bbox(m)
        if bb is None:
            return
        a = np.asarray(m.crop(bb), np.float32) / 255.0
        self._comp(bb, self._col(bb, color), a * opacity)

    def form(self, m, radius=10.0, dark=(40, 28, 20), strength=0.35, hi=None, hi_strength=0.0, light=LIGHT, core=0.0, clip=None):
        """Soft modelling of a rounded form: shadow on the side facing away from the light, optional highlight."""
        bb = self._bbox(m, radius * 0.5)
        if bb is None:
            return
        x0, y0, x1, y1 = bb
        a = np.asarray(m.crop(bb), np.float32) / 255.0
        r = radius * self.S
        b = ndimage.gaussian_filter(a, r * 0.6)
        gy, gx = np.gradient(b)
        mag = np.sqrt(gx * gx + gy * gy) + 1e-6
        # outward normal = -grad; light dir points from the light, so lit side has normal . (-light) > 0
        nx, ny = -gx / mag, -gy / mag
        edge = np.clip(mag * r * 2.2, 0, 1)
        lit = -(nx * light[0] + ny * light[1])
        shadow = np.clip(-lit, 0, 1) * edge
        # plus a gentle overall darkening deeper toward the shadow side
        clipm = a if clip is None else a * (np.asarray(clip.crop(bb), np.float32) / 255.0)
        self._comp(bb, self._col(bb, dark), clipm * shadow * strength)
        if core:
            self._comp(bb, self._col(bb, dark), clipm * np.clip(1 - b * 1.4, 0, 1) * core)
        if hi is not None and hi_strength:
            hl = np.clip(lit, 0, 1) * edge
            self._comp(bb, self._col(bb, hi), clipm * hl * hi_strength)

    def contour(self, m, light_w=0.7, dark_w=1.9, color=INK, opacity=0.95, light=LIGHT, inside=False):
        """Ink contour around a mask with the weight swelling on the shadow side."""
        pad = dark_w + 3
        bb = self._bbox(m, pad)
        if bb is None:
            return
        x0, y0, x1, y1 = bb
        a = np.asarray(m.crop(bb), np.float32) / 255.0
        ins = a > 0.5
        dout = ndimage.distance_transform_edt(~ins) / self.S
        din = ndimage.distance_transform_edt(ins) / self.S
        b = ndimage.gaussian_filter(a, 3.0 * self.S)
        gy, gx = np.gradient(b)
        mag = np.sqrt(gx * gx + gy * gy) + 1e-9
        nx, ny = -gx / mag, -gy / mag
        lit = -(nx * light[0] + ny * light[1])   # +1 lit, -1 shadow
        w = light_w + (dark_w - light_w) * np.clip(0.5 - 0.5 * lit, 0, 1) ** 1.2
        # slight pen pressure wobble
        w = w * (1 + 0.12 * self.n_mid[y0:y1, x0:x1])
        aa = 0.6
        # band straddling the edge (3/4 outside, 1/4 inside) | inside only
        if inside:
            alpha = np.clip((w - din) / (aa * 0.5) + 0, 0, 1) * ins
        else:
            alpha = np.clip(1 - np.maximum(dout - w * 0.75, 0) / aa, 0, 1) * np.clip(1 - np.maximum(din - w * 0.25, 0) / aa, 0, 1)
        alpha *= opacity
        self._comp(bb, self._col(bb, color), alpha)

    def ink(self, seq, widths=1.2, color=INK, opacity=0.95, smooth=True, cap=True):
        m = self.stroke_mask(seq, widths, smooth=smooth, cap=cap)
        self.flat(m, color, opacity)
        return m

    def dashed(self, seq, width=1.2, color=INK, dash=6.0, gap=4.0, opacity=0.9, smooth=True):
        pts = resample(spline(seq, False, 10) if smooth and len(seq) > 2 else seq, 0.5)
        m = self.mask()
        acc, on, cur = 0.0, True, [pts[0]]
        for i in range(1, len(pts)):
            acc += math.dist(pts[i - 1], pts[i])
            if on:
                cur.append(pts[i])
            if acc >= (dash if on else gap):
                if on and len(cur) > 1:
                    self.stroke_mask(cur, width, m=m, smooth=False)
                on = not on
                acc = 0.0
                cur = [pts[i]]
        if on and len(cur) > 1:
            self.stroke_mask(cur, width, m=m, smooth=False)
        self.flat(m, color, opacity)

    def arrow(self, seq, width=1.6, color=INK, head=8.0, opacity=0.95, smooth=True):
        pts = spline(seq, False, 10) if smooth and len(seq) > 2 else list(seq)
        # shorten the shaft so it does not poke through the head
        tot = arclen(pts)
        cut = head * 0.6
        acc = 0.0
        shaft = [pts[-1]]
        for i in range(len(pts) - 1, 0, -1):
            acc += math.dist(pts[i], pts[i - 1])
            if acc >= cut:
                shaft = pts[:i]
                break
        m = self.stroke_mask(shaft if len(shaft) > 1 else pts, (width * 0.6, width, width), smooth=False)
        (xa, ya), (xb, yb) = pts[-3] if len(pts) > 2 else pts[0], pts[-1]
        ang = math.atan2(yb - ya, xb - xa)
        tip = (xb, yb)
        l = (xb - math.cos(ang - 0.36) * head, yb - math.sin(ang - 0.36) * head)
        r = (xb - math.cos(ang + 0.36) * head, yb - math.sin(ang + 0.36) * head)
        notch = (xb - math.cos(ang) * head * 0.72, yb - math.sin(ang) * head * 0.72)
        ImageDraw.Draw(m).polygon(self.P([tip, l, notch, r]), fill=255)
        self.flat(m, color, opacity)

    def strokes(self, m_clip, n, color, length=(3, 6), width=(0.5, 0.9), angle=None, opacity=0.35, jitter=0.35, curve=0.15, area=None):
        """Many short tapered pen strokes inside a clip mask; angle(x, y) -> radians (output px coords)."""
        bb = self._bbox(m_clip)
        if bb is None:
            return
        arr = np.asarray(m_clip.crop(bb))
        ys, xs = np.nonzero(arr > 127)
        if len(xs) == 0:
            return
        lay = self.mask()
        d = ImageDraw.Draw(lay)
        S = self.S
        rng = self.rng
        for _ in range(n):
            i = rng.randrange(len(xs))
            x = (xs[i] + bb[0]) / S
            y = (ys[i] + bb[1]) / S
            a = (angle(x, y) if angle else -math.pi / 2) + rng.uniform(-jitter, jitter)
            L = rng.uniform(*length)
            w = rng.uniform(*width)
            bend = rng.uniform(-curve, curve)
            pts = []
            for k in range(6):
                t = k / 5
                aa = a + bend * (t - 0.5) * 2
                pts.append((x + math.cos(aa) * L * t, y + math.sin(aa) * L * t))
            for k in range(1, 6):
                t = k / 5
                ww = max(0.3, w * (1 - abs(t - 0.4) * 1.3)) * S
                d.line([(pts[k - 1][0] * S, pts[k - 1][1] * S), (pts[k][0] * S, pts[k][1] * S)], fill=255, width=max(1, int(round(ww))))
        lay = self.inter(lay, m_clip)
        self.flat(lay, color, opacity)

    def stipple(self, m_clip, n, color, r=(0.35, 0.9), opacity=0.6):
        bb = self._bbox(m_clip)
        if bb is None:
            return
        arr = np.asarray(m_clip.crop(bb))
        ys, xs = np.nonzero(arr > 127)
        if len(xs) == 0:
            return
        lay = self.mask()
        d = ImageDraw.Draw(lay)
        S = self.S
        for _ in range(n):
            i = self.rng.randrange(len(xs))
            rr = self.rng.uniform(*r) * S
            x, y = xs[i] + bb[0], ys[i] + bb[1]
            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=255)
        self.flat(lay, color, opacity)

    def hatch(self, m_clip, spacing=3.0, angle=-0.9, width=0.5, color=INK_SOFT, opacity=0.35, wobble=0.4):
        bb = self._bbox(m_clip)
        if bb is None:
            return
        lay = self.mask()
        d = ImageDraw.Draw(lay)
        S = self.S
        x0, y0, x1, y1 = [v / S for v in bb]
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        R = math.hypot(x1 - x0, y1 - y0) / 2 + 2
        dx, dy = math.cos(angle), math.sin(angle)
        nx, ny = -dy, dx
        k = -R
        while k < R:
            px, py = cx + nx * k, cy + ny * k
            seg = []
            for t in np.linspace(-R, R, 24):
                o = self.rng.uniform(-wobble, wobble) * 0.3
                seg.append(((px + dx * t + nx * o) * S, (py + dy * t + ny * o) * S))
            d.line(seg, fill=255, width=max(1, int(round(width * S))))
            k += spacing
        lay = self.inter(lay, m_clip)
        self.flat(lay, color, opacity)

    # ------------------------------------------------------------------ output
    def image(self, sharpen=True):
        """Premultiplied Lanczos downsample to the output size, unpremultiply, light unsharp mask."""
        out_c = []
        for c in range(3):
            im = Image.fromarray(self.C[..., c], 'F').resize((self.w, self.h), Image.LANCZOS)
            out_c.append(np.asarray(im, np.float32))
        A = np.asarray(Image.fromarray(self.A, 'F').resize((self.w, self.h), Image.LANCZOS), np.float32)
        A = np.clip(A, 0, 1)
        C = np.stack(out_c, -1)
        rgb = np.where(A[..., None] > 1e-4, C / np.maximum(A[..., None], 1e-4), 0)
        rgb = np.clip(rgb, 0, 1)
        arr = np.dstack([rgb * 255, A * 255]).round().astype(np.uint8)
        img = Image.fromarray(arr, 'RGBA')
        if sharpen:
            rgbim = img.convert('RGB').filter(ImageFilter.UnsharpMask(radius=0.8, percent=45, threshold=1))
            r, g, b = rgbim.split()
            img = Image.merge('RGBA', (r, g, b, img.getchannel('A')))
        return img

    def save(self, path):
        img = self.image()
        try:  # [artqa] bleed colour into transparent texels: no dark fringe under bilinear / mipmapped sampling
            sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'artqa'))
            from bleed import bleed_image
            bleed_image(img).save(path, optimize=True)
        except ImportError:
            img.save(path, optimize=True)
        return img


def on_paper(img, color=PAPER):
    bg = Image.new('RGBA', img.size, color + (255,))
    return Image.alpha_composite(bg, img)
