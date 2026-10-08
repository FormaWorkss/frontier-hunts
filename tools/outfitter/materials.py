#!/usr/bin/env python3
"""[outfitter] Procedural fabric/fur/leather/camo materials (original art). All painters work in texel space at S texels
per model pixel and return float RGB(A) arrays in 0..255.

Camo prints are generated as seamless tiles (period TILE model px) and sampled by world-anchored print coordinates, so
the pattern runs continuously across neighbouring panels like a real printed garment."""
import math, zlib
import numpy as np

TILE = 48  # camo repeat in model px (about 3 blocks of garment)


def col(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


# ------------------------------------------------------------------------------------------------ noise (seamless)
def vnoise_t(n, cell, seed):
    """Seamless value noise on an n x n tile, cell in texels (n % cell == 0 preferred)."""
    r = np.random.default_rng(seed)
    g = max(1, int(round(n / cell)))
    grid = r.random((g, g))
    ys, xs = np.mgrid[0:n, 0:n] * (g / n)
    x0 = np.floor(xs).astype(int); y0 = np.floor(ys).astype(int)
    fx = xs - x0; fy = ys - y0
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    x1 = (x0 + 1) % g; y1 = (y0 + 1) % g; x0 %= g; y0 %= g
    a = grid[y0, x0] * (1 - fx) + grid[y0, x1] * fx
    b = grid[y1, x0] * (1 - fx) + grid[y1, x1] * fx
    return a * (1 - fy) + b * fy


def fbm_t(n, cell, seed, octaves=4, gain=0.5):
    v = np.zeros((n, n)); amp = 1.0; tot = 0.0
    for o in range(octaves):
        c = max(1, cell / (2 ** o))
        v += vnoise_t(n, c, seed + 31 * o) * amp
        tot += amp; amp *= gain
    return v / tot


def stretch_noise(n, cx, cy, seed, octaves=3):
    """Anisotropic seamless noise: cells cx wide, cy tall (texels)."""
    r = np.random.default_rng(seed)
    v = np.zeros((n, n)); amp = 1; tot = 0
    for o in range(octaves):
        gx = max(1, int(round(n / max(1, cx / 2 ** o)))); gy = max(1, int(round(n / max(1, cy / 2 ** o))))
        grid = r.random((gy, gx))
        ys = np.arange(n)[:, None] * gy / n; xs = np.arange(n)[None, :] * gx / n
        x0 = np.floor(xs).astype(int); y0 = np.floor(ys).astype(int)
        fx = xs - x0; fy = ys - y0
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
        x1 = (x0 + 1) % gx; y1 = (y0 + 1) % gy; x0 %= gx; y0 %= gy
        a = grid[y0, x0] * (1 - fx) + grid[y0, x1] * fx
        b = grid[y1, x0] * (1 - fx) + grid[y1, x1] * fx
        v += (a * (1 - fy) + b * fy) * amp; tot += amp; amp *= 0.5
    return v / tot


def smooth01(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


# ------------------------------------------------------------------------------------------------ stamping on a tile
class Tile:
    def __init__(self, n, base):
        self.n = n
        self.rgb = np.zeros((n, n, 3)); self.rgb[:] = base

    def stamp(self, cx, cy, half, fn):
        """fn(dx, dy) -> (alpha 0..1, rgb array or None, shade multiplier) evaluated on a wrapped window."""
        n = self.n
        x0 = int(math.floor(cx - half)); y0 = int(math.floor(cy - half))
        size = int(2 * half) + 2
        ys, xs = np.mgrid[y0:y0 + size, x0:x0 + size]
        dx = xs + 0.5 - cx; dy = ys + 0.5 - cy
        a, rgb = fn(dx, dy)
        if a is None:
            return
        yi = ys % n; xi = xs % n
        cur = self.rgb[yi, xi]
        out = cur * (1 - a[..., None]) + rgb * a[..., None]
        self.rgb[yi, xi] = out

    def darken(self, cx, cy, half, fn, amount):
        n = self.n
        x0 = int(math.floor(cx - half)); y0 = int(math.floor(cy - half))
        size = int(2 * half) + 2
        ys, xs = np.mgrid[y0:y0 + size, x0:x0 + size]
        dx = xs + 0.5 - cx; dy = ys + 0.5 - cy
        a = fn(dx, dy)
        yi = ys % n; xi = xs % n
        self.rgb[yi, xi] *= (1 - amount * a)[..., None]


def leaf_mask(dx, dy, ang, L, W, lobes=0, aa=1.0):
    c, s = math.cos(ang), math.sin(ang)
    u = dx * c + dy * s
    v = -dx * s + dy * c
    t = u / L  # -1..1 along the leaf
    prof = np.sqrt(np.clip(1 - t * t, 0, 1)) * (1 - 0.35 * np.clip(t, 0, 1))
    if lobes:
        prof = prof * (0.78 + 0.22 * np.abs(np.sin((t + 1) * math.pi * lobes / 2)))
    halfw = W * prof
    d = np.abs(v) - halfw
    a = np.clip(0.5 - d / aa, 0, 1) * (np.abs(t) <= 1)
    return a, u, v, t


def paint_leaves(tile, rng, count, size_px, S, palette, lobes=0, shadow=0.35, vein=0.25, aspect=0.42, ang_bias=None):
    n = tile.n
    for _ in range(count):
        cx, cy = rng.random() * n, rng.random() * n
        L = size_px * S * (0.6 + 0.6 * rng.random())
        W = L * aspect * (0.8 + 0.4 * rng.random())
        ang = rng.random() * math.pi * 2 if ang_bias is None else ang_bias + rng.normal(0, 0.5)
        base = palette[rng.integers(len(palette))] * (0.85 + 0.3 * rng.random())
        lb = lobes if (lobes and rng.random() < 0.7) else 0
        # soft drop shadow (lower right)
        so = S * 0.6
        tile.darken(cx + so, cy + so, L + 3, lambda dx, dy: leaf_mask(dx, dy, ang, L * 1.05, W * 1.15, lb, aa=S * 0.8)[0], shadow)

        def fn(dx, dy):
            a, u, v, t = leaf_mask(dx, dy, ang, L, W, lb)
            g = 0.9 + 0.2 * (0.5 - t / 2)
            side = np.where(v > 0, 0.93, 1.05)
            rib = 1 - vein * np.clip(1 - np.abs(v) / (0.35 * S), 0, 1) * (np.abs(t) < 0.95)
            rgb = base[None, None, :] * (g * side * rib)[..., None]
            return a, rgb
        tile.stamp(cx, cy, L + 2, fn)


def paint_branches(tile, rng, count, S, color, width_px=0.45, length_px=14, vertical=False):
    n = tile.n
    for _ in range(count):
        x, y = rng.random() * n, rng.random() * n
        ang = (math.pi / 2 + rng.normal(0, 0.25)) if vertical else rng.random() * math.pi * 2
        L = length_px * S * (0.5 + rng.random())
        w = width_px * S * (0.7 + 0.6 * rng.random())
        steps = int(L / (S * 0.2)) + 1
        for k in range(steps):
            ang += rng.normal(0, 0.08)
            x += math.cos(ang) * S * 0.2; y += math.sin(ang) * S * 0.2
            ww = w * (1 - 0.6 * k / steps)
            def fn(dx, dy, ww=ww):
                d = np.sqrt(dx * dx + dy * dy)
                a = np.clip(ww / 2 + 0.5 - d, 0, 1)
                return a, color[None, None, :] * (0.9 + 0.2 * (dy < 0))[..., None]
            tile.stamp(x, y, ww + 2, fn)
            if rng.random() < 0.04 and k > 2:
                paint_branches(tile, rng, 0, S, color)


# ------------------------------------------------------------------------------------------------ camo tiles
def camo_tile(style, S, seed=7):
    n = TILE * S
    rng = np.random.default_rng(seed + zlib.crc32(style.encode()) % 1000)
    if style in ('timber', 'autumn'):
        bark = col('#5b5145') if style == 'timber' else col('#5a4636')
        t = Tile(n, bark)
        sn = stretch_noise(n, 1.2 * S, 12 * S, seed + 1)
        sn2 = stretch_noise(n, 0.5 * S, 5 * S, seed + 2)
        grey = col('#8a8478') if style == 'timber' else col('#7e6a55')
        dark = col('#2e2822')
        mix = smooth01((sn - 0.35) * 2.4)[..., None]
        t.rgb = bark * (1 - mix) + grey * mix
        t.rgb *= (0.78 + 0.4 * sn2)[..., None]
        cracks = (sn2 < 0.2)[..., None]
        t.rgb = np.where(cracks, t.rgb * 0.6 + dark * 0.4, t.rgb)
        paint_branches(t, rng, 60, S, col('#3a3129'), 0.3, 7)
        if style == 'timber':
            pal = [col('#6d6a3e'), col('#565a33'), col('#7a6446'), col('#8c7b58'), col('#4b4a2c'), col('#9a8a62')]
        else:
            pal = [col('#a24f1d'), col('#c06a22'), col('#8a3a1c'), col('#b8873a'), col('#6e4a2a'), col('#9a5a26')]
        paint_leaves(t, rng, int(520 * (TILE / 48) ** 2), 1.25, S, pal, lobes=3, shadow=0.5, aspect=0.5)
        paint_leaves(t, rng, int(380 * (TILE / 48) ** 2), 0.75, S, pal, lobes=0, shadow=0.4)
        return t.rgb
    if style == 'marsh':
        t = Tile(n, col('#6b6346'))
        sn = stretch_noise(n, 3 * S, 30 * S, seed + 3)
        t.rgb *= (0.7 + 0.5 * sn)[..., None]
        pal = [col('#8c7b4f'), col('#a99567'), col('#5e5a36'), col('#45472c'), col('#b3a173'), col('#6e6440')]
        for _ in range(int(420 * (TILE / 48) ** 2)):
            cx, cy = rng.random() * n, rng.random() * n
            L = S * (2.5 + 3.5 * rng.random()); W = S * (0.22 + 0.2 * rng.random())
            ang = math.pi / 2 + rng.normal(0, 0.18)
            base = pal[rng.integers(len(pal))]
            t.darken(cx + S * 0.5, cy, L + 3, lambda dx, dy: leaf_mask(dx, dy, ang, L, W * 1.3, 0, S * 0.7)[0], 0.35)
            def fn(dx, dy, ang=ang, L=L, W=W, base=base):
                a, u, v, tt = leaf_mask(dx, dy, ang, L, W)
                rgb = base[None, None, :] * (0.92 + 0.16 * (v > 0))[..., None]
                return a, rgb
            t.stamp(cx, cy, L + 2, fn)
        # cattail heads
        for _ in range(int(24 * (TILE / 48) ** 2)):
            cx, cy = rng.random() * n, rng.random() * n
            def fn(dx, dy):
                a, u, v, tt = leaf_mask(dx, dy, math.pi / 2, S * 0.9, S * 0.3)
                return a, col('#4a3220')[None, None, :] * (0.85 + 0.3 * (v > 0))[..., None]
            t.stamp(cx, cy, S * 2.5, fn)
        return t.rgb
    if style == 'prairie':
        t = Tile(n, col('#a89470'))
        sn = fbm_t(n, 10 * S, seed + 4)
        t.rgb *= (0.8 + 0.35 * sn)[..., None]
        pal = [col('#c8b48a'), col('#b29c70'), col('#8d7a54'), col('#d6c49c'), col('#7a6c4a'), col('#9a8d60')]
        for _ in range(int(800 * (TILE / 48) ** 2)):
            cx, cy = rng.random() * n, rng.random() * n
            L = S * (1.5 + 2.5 * rng.random()); W = S * (0.13 + 0.12 * rng.random())
            ang = math.pi / 2 + rng.normal(0, 0.4)
            base = pal[rng.integers(len(pal))]
            t.darken(cx + S * 0.4, cy, L + 3, lambda dx, dy: leaf_mask(dx, dy, ang, L, W * 1.6, 0, S * 0.6)[0], 0.25)
            def fn(dx, dy, ang=ang, L=L, W=W, base=base):
                a, u, v, tt = leaf_mask(dx, dy, ang, L, W, 0, aa=0.8)
                return a, base[None, None, :] * (0.9 + 0.2 * (v > 0))[..., None]
            t.stamp(cx, cy, L + 2, fn)
        # seed heads
        for _ in range(int(40 * (TILE / 48) ** 2)):
            cx, cy = rng.random() * n, rng.random() * n
            def fn(dx, dy):
                a, u, v, tt = leaf_mask(dx, dy, math.pi / 2 + 0.3, S * 0.7, S * 0.22)
                return a, col('#e2d2a8')[None, None, :]
            t.stamp(cx, cy, S * 2, fn)
        return t.rgb
    if style == 'snow':
        t = Tile(n, col('#e9ecee'))
        sn = fbm_t(n, 14 * S, seed + 5)
        sh = smooth01((sn - 0.55) * 4)
        t.rgb = t.rgb * (1 - 0.12 * sh[..., None]) + col('#b9c1c8') * 0.0
        paint_branches(t, rng, 60, S, col('#6f7378'), 0.22, 5)
        paint_branches(t, rng, 40, S, col('#9aa0a6'), 0.2, 4)
        pal = [col('#a7adb2'), col('#8d9297'), col('#c4c9cd')]
        paint_leaves(t, rng, int(140 * (TILE / 48) ** 2), 0.9, S, pal, lobes=2, shadow=0.12, vein=0.15)
        return t.rgb
    if style == 'digital':
        # Ridgeline digital: two-scale pixel blocks (1 and 2 model px) in khaki / olive / brown / dark
        pal = [col('#a39a76'), col('#6f7048'), col('#5a4a36'), col('#2f3226')]
        big = fbm_t(TILE, 6, seed + 6, 3)
        small = fbm_t(TILE, 4, seed + 7, 2)
        idx = np.zeros((TILE, TILE), int)
        v = big * 0.7 + small * 0.3
        idx[v > 0.42] = 1; idx[v > 0.52] = 2; idx[v > 0.62] = 3
        # 2px clusters
        b2 = fbm_t(TILE // 2, 4, seed + 8, 2)
        b2 = np.kron(b2, np.ones((2, 2)))
        idx[(b2 > 0.66)] = 3; idx[(b2 < 0.3)] = 0
        img = np.array(pal)[idx]
        img *= (0.94 + 0.12 * np.random.default_rng(seed).random((TILE, TILE)))[..., None]
        return np.kron(img, np.ones((S, S, 1)))
    if style == 'blaze':
        t = Tile(n, col('#f26a1b'))
        sn = fbm_t(n, 6 * S, seed + 9)
        t.rgb *= (0.92 + 0.14 * sn)[..., None]
        pal = [col('#3b3a26'), col('#4d4630'), col('#2a2720'), col('#5c4a32')]
        paint_branches(t, rng, 50, S, col('#2d2a22'), 0.3, 6)
        paint_leaves(t, rng, int(300 * (TILE / 48) ** 2), 1.3, S, pal, lobes=3, shadow=0.25, aspect=0.5)
        paint_leaves(t, rng, int(160 * (TILE / 48) ** 2), 0.7, S, pal, lobes=0, shadow=0.2)
        return t.rgb
    if style == 'carbon':
        t = Tile(n, col('#2d3032'))
        sn = fbm_t(n, 3 * S, seed + 10, 3)
        heather = np.random.default_rng(seed).random((n, n))
        t.rgb *= (0.86 + 0.18 * sn + 0.08 * heather)[..., None]
        # ripstop grid every 2 px
        g = np.zeros((n, n))
        g[::2 * S, :] = 1; g[:, ::2 * S] = 1
        t.rgb *= (1 - 0.05 * g)[..., None]
        # faint tonal leaf print (charcoal on charcoal) so the suit is not a flat slab
        pal = [col('#363a3c'), col('#25282a'), col('#3d4143')]
        paint_leaves(t, rng, int(160 * (TILE / 48) ** 2), 1.3, S, pal, lobes=3, shadow=0.08, vein=0.06, aspect=0.5)
        return t.rgb
    raise ValueError(style)


CAMO_STYLE = {
    'timber_camo_coveralls': 'timber', 'autumn_camo_coveralls': 'autumn', 'marsh_camo_coveralls': 'marsh',
    'prairie_camo_coveralls': 'prairie', 'snow_camo_coveralls': 'snow', 'digital_camo_coveralls': 'digital',
    'blaze_camo_coveralls': 'blaze', 'scent_suit': 'carbon',
}


# ------------------------------------------------------------------------------------------------ fur / hide
def strands(h, w, rng, length, density, drift=0.15, width=1):
    img = np.zeros((h + length, w))
    n = int(w * h * density / max(1, length) * 3)
    xs = rng.integers(0, w, n); ys = rng.integers(-length, h, n)
    Ls = rng.integers(max(1, length // 2), length + 1, n)
    bs = rng.random(n) * 0.8 + 0.2
    dr = rng.normal(0, drift, n)
    for x, y, L, b, d in zip(xs, ys, Ls, bs, dr):
        k = np.arange(L)
        yy = y + k
        xx = (np.round(x + d * k).astype(int)) % w
        ok = (yy >= 0) & (yy < h + length)
        val = b * (1 - k / L * 0.55)
        for ww in range(width):
            np.maximum.at(img, (yy[ok], (xx[ok] + ww) % w), val[ok])
    return img[:h]


def fur(h, w, S, rng, kind):
    """kind: wolf, wolf_band, bear, bison."""
    if kind.startswith('wolf'):
        img = fur_clumps(h, w, S, rng, col('#55483b'), col('#8e7b64'), col('#d6c8ae'), 0.9, 1.8)
        return img * (1.06 if kind == 'wolf_band' else 1.0)
    if kind.startswith('wolfold'):
        st = strands(h, w, rng, max(6, S * 3), 0.55, 0.2)
        n = fbm_t(max(h, w), 6 * S, int(rng.integers(1e6)))[:h, :w]
        grey, brown, cream, black = col('#8f8678'), col('#6e5a45'), col('#ddd2bd'), col('#2c2621')
        mix = smooth01(n * 1.6 - 0.3)[..., None]
        rgb = grey * (1 - mix) + brown * mix
        rgb = rgb * (0.55 + 0.6 * st[..., None])
        tips = (st > 0.82)[..., None]
        rgb = np.where(tips, rgb * 0.4 + cream * 0.6, rgb)
        guard = (st > 0.6) & (rng.random((h, w)) < 0.05)
        rgb[guard] = rgb[guard] * 0.4 + black * 0.6
        if kind == 'wolf_band':
            rgb = rgb * 1.06 + 8
        return np.clip(rgb, 0, 255)
    if kind == 'bear':
        return fur_clumps(h, w, S, rng, col('#24180f'), col('#3d2a1a'), col('#86643f'), 1.2, 2.8)
    if kind == 'bear_old':
        st = strands(h, w, rng, max(8, S * 4), 0.65, 0.25)
        n = fbm_t(max(h, w), 8 * S, int(rng.integers(1e6)))[:h, :w]
        base = col('#3b2a1d') * (0.8 + 0.4 * n)[..., None]
        rgb = base * (0.5 + 0.8 * st[..., None])
        sheen = (st > 0.88)[..., None]
        rgb = np.where(sheen, rgb * 0.5 + col('#8a6a4a') * 0.5, rgb)
        return np.clip(rgb, 0, 255)
    if kind == 'bison':
        N = max(h, w)
        n1 = fbm_t(N, 1.6 * S, int(rng.integers(1e6)), 2)[:h, :w]
        n2 = fbm_t(N, 5 * S, int(rng.integers(1e6)), 3)[:h, :w]
        curls = np.abs(np.sin((n1 * 7 + n2 * 3) * math.pi))
        base = col('#4a3424') * (0.8 + 0.35 * n2)[..., None]
        rgb = base * (0.55 + 0.6 * curls[..., None])
        hi = (curls > 0.9)[..., None]
        rgb = np.where(hi, rgb * 0.6 + col('#7a5a3c') * 0.4, rgb)
        return np.clip(rgb, 0, 255)
    raise ValueError(kind)


def suede(h, w, S, rng, base='#b48a5a'):
    N = max(h, w)
    n = fbm_t(N, 5 * S, int(rng.integers(1e6)), 4)[:h, :w]
    n2 = fbm_t(N, 1.2 * S, int(rng.integers(1e6)), 2)[:h, :w]
    smoke = smooth01((fbm_t(N, 10 * S, int(rng.integers(1e6)), 3)[:h, :w] - 0.55) * 3)
    rgb = col(base)[None, None, :] * (0.86 + 0.22 * n + 0.08 * n2)[..., None]
    rgb = rgb * (1 - 0.18 * smoke[..., None])
    # nap: tiny speckle
    sp = rng.random((h, w))
    rgb *= (0.97 + 0.06 * sp)[..., None]
    return np.clip(rgb, 0, 255)


def leather(h, w, S, rng, base='#4a3322'):
    N = max(h, w)
    n = fbm_t(N, 2 * S, int(rng.integers(1e6)), 3)[:h, :w]
    rgb = col(base)[None, None, :] * (0.85 + 0.3 * n)[..., None]
    return np.clip(rgb, 0, 255)


def fabric(h, w, S, rng, base, rib=0, weave=0.06):
    N = max(h, w)
    n = fbm_t(N, 3 * S, int(rng.integers(1e6)), 3)[:h, :w]
    rgb = col(base)[None, None, :] * (0.9 + 0.16 * n + weave * rng.random((h, w)))[..., None]
    if rib:
        yy = np.arange(h)[:, None]
        rgb *= (1 - 0.18 * ((yy // max(1, S // 2)) % 2))[..., None] if rib == 1 else 1
    return np.clip(rgb, 0, 255)


def fur_clumps(h, w, S, rng, under, mid, tip, size=1.3, length=2.6, streak=0.5):
    """Clumped pelt: overlapping rows of tapered tufts (root dark -> tip light) on a dark undercoat, plus fine hair
    streaks. size/length in model px."""
    img = np.zeros((h, w, 3)); img[:] = under
    st = strands(h, w, rng, max(4, int(S * 1.6)), 0.7, 0.12)
    img = img * (0.7 + 0.6 * st[..., None])
    cw = max(2.0, size * S); cl = max(3.0, length * S)
    count = int(h * w / (cw * cl * 0.32)) + 4
    ys, xs = np.mgrid[0:h, 0:w]
    pos = [(rng.random() * (h + cl) - cl, rng.random() * (w + cw) - cw * 0.5) for _ in range(count)]
    pos.sort()  # top to bottom: lower tufts overlap the ones above
    for y0, x0 in pos:
        L = cl * (0.7 + 0.6 * rng.random()); W = cw * (0.4 + 0.3 * rng.random())
        lean = rng.normal(0, 0.18)
        t = (ys - y0) / L
        inside = (t >= 0) & (t <= 1)
        if not inside.any():
            continue
        half = W * np.clip(1 - t, 0, 1) ** 0.8
        dx = xs - (x0 + lean * (ys - y0))
        m = inside & (np.abs(dx) < half)
        if not m.any():
            continue
        k = t[m]
        shade = 0.85 + 0.3 * rng.random()
        colr = (mid * (1 - k[:, None]) + tip * k[:, None]) * shade
        colr *= (0.8 + 0.35 * st[m])[:, None]
        img[m] = img[m] * 0.25 + colr * 0.75
    return np.clip(img, 0, 255)
