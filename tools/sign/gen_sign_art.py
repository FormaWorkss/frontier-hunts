"""[sign] Procedural art for buck rubs and scrapes -> textures/entity/deer_sign.png (+ deer_sign_far.png, 1/4 size).

Atlas 1024 x 512 (layout mirrored in sign/client/DeerSignRenderer):
  RUB    4 stages (fresh, drying, weathered, old)   x = i*128, y = 0,   128 x 256  (top of the cell = top of the rub)
  CURL   bark side 2 variants                         x = 512 + k*32, y = 0, 32 x 128 (anchor at the top)
  CURL   inner side, fresh 2 / old 2                  x = 576 + k*32 (fresh) / 640 + k*32 (old), y = 0, 32 x 128
  SHAV   ground shavings fresh / old                  x = 704, y = 0 / 128, 128 x 128 (trunk side at the top)
  BARK   branch bark tile                             x = 832, y = 0, 64 x 64
  WOOD   broken twig wood fresh / old                 x = 896 / 928, y = 0, 32 x 64
  LEAF   leaf sprays A / B                            x = 832 / 896, y = 64, 64 x 64
  SCRAPE 4 stages (fresh, a day or two, dry, old)    x = i*256, y = 256, 256 x 256 (v = 0 is the tree side)
Every texel is designed at 2x and box-filtered with premultiplied alpha (clean cut-out edges)."""
import os, sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

OUT = sys.argv[1] if len(sys.argv) > 1 else 'patch/assets/frontierhunts/textures/entity'
SS = 2
rng = np.random.default_rng(20261002)


# ----------------------------------------------------------------------------------------------------- noise
def vnoise(h, w, cells_y, cells_x, seed, octaves=4, persistence=0.5):
    """fBm value noise in [0,1], h x w, base lattice cells_y x cells_x (smooth bicubic upsampling)."""
    r = np.random.default_rng(seed)
    out = np.zeros((h, w)); amp = 1.0; tot = 0.0
    cy, cx = cells_y, cells_x
    for _ in range(octaves):
        g = r.random((int(cy) + 3, int(cx) + 3))
        z = ndi.zoom(g, ((h + 0.0) / (cy + 1), (w + 0.0) / (cx + 1)), order=3, mode='wrap')[:h, :w]
        if z.shape != (h, w):
            z = np.pad(z, ((0, h - z.shape[0]), (0, w - z.shape[1])), mode='edge')
        out += amp * z; tot += amp; amp *= persistence; cy *= 2; cx *= 2
    out /= tot
    lo, hi = np.percentile(out, 1), np.percentile(out, 99)
    return np.clip((out - lo) / (hi - lo + 1e-9), 0, 1)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    t = np.asarray(t)[..., None] if np.ndim(t) else t
    return a + (np.asarray(b, float) - a) * t


def col(c):
    return np.array(c, float)


def down(rgba):
    """premultiplied box filter by SS"""
    h, w, _ = rgba.shape
    a = rgba[..., 3:4] / 255.0
    pm = np.concatenate([rgba[..., :3] * a, a * 255.0], axis=2)
    pm = pm.reshape(h // SS, SS, w // SS, SS, 4).mean(axis=(1, 3))
    al = pm[..., 3:4] / 255.0
    rgb = np.where(al > 1e-4, pm[..., :3] / np.maximum(al, 1e-4), 0)
    return np.concatenate([rgb, pm[..., 3:4]], axis=2)


def bleed(rgba, it=6):
    """grow colour into transparent texels (no dark fringes when filtered / mip-less shimmer)"""
    rgb = rgba[..., :3].copy(); a = rgba[..., 3]
    known = a > 8
    for _ in range(it):
        m = known.astype(float)
        s = ndi.uniform_filter(m, 3)
        c = np.stack([ndi.uniform_filter(rgb[..., i] * m, 3) for i in range(3)], -1)
        new = (~known) & (s > 0)
        rgb[new] = c[new] / s[new][:, None]
        known = known | new
    out = rgba.copy(); out[..., :3] = rgb
    return out


def line(img_h, img_w, x0, y0, x1, y1, width):
    """distance field of a segment (pixels)"""
    ys, xs = np.mgrid[0:img_h, 0:img_w]
    ab = np.array([x1 - x0, y1 - y0], float)
    t = np.clip(((xs - x0) * ab[0] + (ys - y0) * ab[1]) / (ab @ ab + 1e-9), 0, 1)
    d = np.hypot(xs - (x0 + t * ab[0]), ys - (y0 + t * ab[1]))
    return np.clip(1 - d / width, 0, 1)


# ----------------------------------------------------------------------------------------------------- rub
RUB_STAGES = [
    # interior base, fibre dark, fibre light, phloem edge, bark lip, gouge
    dict(base=(234, 226, 186), green=(206, 214, 158), dark=(198, 182, 128), light=(250, 246, 222), edge=(196, 118, 78), lip=(74, 56, 42), gouge=(172, 140, 92), wet=0.9),
    dict(base=(226, 194, 128), green=(214, 190, 120), dark=(188, 148, 88), light=(240, 214, 160), edge=(150, 88, 52), lip=(66, 50, 38), gouge=(150, 108, 62), wet=0.25),
    dict(base=(176, 124, 74), green=(168, 122, 72), dark=(132, 88, 52), light=(198, 152, 100), edge=(104, 66, 44), lip=(58, 44, 34), gouge=(108, 72, 44), wet=0.0),
    dict(base=(132, 122, 106), green=(126, 118, 104), dark=(92, 84, 74), light=(160, 152, 138), edge=(78, 66, 54), lip=(54, 46, 40), gouge=(72, 64, 56), wet=0.0),
]


def rub(stage, seed):
    S = RUB_STAGES[stage]
    H, W = 256 * SS, 128 * SS
    ys, xs = np.mgrid[0:H, 0:W]
    u = (xs + 0.5) / W * 2 - 1          # -1..1 across
    v = (ys + 0.5) / H                  # 0 top .. 1 bottom
    # ragged outline: an elongated blaze; bark tears off in short horizontal strips, so each edge steps in and out
    prof = 0.78 * np.sin(np.pi * np.clip(v * 1.04 - 0.02, 0, 1)) ** 0.38
    n1 = vnoise(H, W, 10, 2, seed, 4)
    r0 = np.random.default_rng(seed + 50)
    steps = []
    for side in range(2):
        rows = np.zeros(H); y = 0
        while y < H:
            hgt = int(r0.integers(2, 9) * SS)
            rows[y:y + hgt] = r0.normal(0, 0.075)
            y += hgt
        rows = ndi.uniform_filter1d(rows, max(1, SS))
        steps.append(rows[:, None] * np.ones((1, W)))
    fine = vnoise(H, W, 60, 4, seed + 1, 2) - 0.5
    halfL = prof * (0.84 + 0.26 * (n1 - 0.5)) + steps[0] + 0.05 * fine
    halfR = prof * (0.84 + 0.26 * (vnoise(H, W, 10, 2, seed + 51, 4) - 0.5)) + steps[1] + 0.05 * fine
    half = np.where(u < 0, halfL, halfR)
    # torn bark tongues reaching into the top and bottom ends
    tongues = vnoise(H, W, 2, 12, seed + 2, 2)
    end = np.minimum(v, 1 - v)
    torn = end < 0.05 + 0.11 * tongues
    inside = (np.abs(u) < half) & ~torn
    dist = ndi.distance_transform_edt(inside) / SS          # px (final) inside
    out = ndi.distance_transform_edt(~inside) / SS
    # interior colour: vertical fibres + mottling
    fib = vnoise(H, W, 6, 40, seed + 3, 4, 0.55)
    fib2 = vnoise(H, W, 3, 90, seed + 4, 2, 0.6)
    mott = vnoise(H, W, 6, 4, seed + 5, 4)
    rgb = lerp(col(S['base']), col(S['green']), smooth(0.45, 0.85, mott))
    rgb = lerp(rgb, col(S['dark']), smooth(0.55, 1.0, fib) * 0.55)
    rgb = lerp(rgb, col(S['light']), smooth(0.6, 1.0, fib2) * 0.45)
    # antler gouges: bunches of two or three parallel tine strokes, short and slightly curved
    g = np.zeros((H, W))
    r = np.random.default_rng(seed + 9)
    for _ in range(r.integers(5, 8)):
        y0 = r.uniform(0.12, 0.88) * H; x0 = r.uniform(0.05, 0.45) * W
        L = r.uniform(0.35, 0.7) * W; slope = r.uniform(-0.45, 0.45); bend = r.uniform(-0.15, 0.15)
        for k in range(r.integers(2, 4)):
            off = k * r.uniform(3.0, 5.0) * SS
            xm = x0 + L * 0.5; ym = y0 + off + slope * L * 0.5 + bend * L
            wdt = r.uniform(0.7, 1.5) * SS; depth = r.uniform(0.45, 0.95)
            g = np.maximum(g, line(H, W, x0, y0 + off, xm, ym, wdt) * depth)
            g = np.maximum(g, line(H, W, xm, ym, x0 + L, y0 + off + slope * L, wdt * 0.8) * depth * 0.8)
    for _ in range(r.integers(2, 5)):          # brow-tine drags along the grain
        x0 = r.uniform(0.3, 0.7) * W; y0 = r.uniform(0.08, 0.5) * H
        g = np.maximum(g, line(H, W, x0, y0, x0 + r.uniform(-6, 6) * SS, y0 + r.uniform(0.15, 0.35) * H, r.uniform(0.7, 1.2) * SS) * 0.6)
    g = ndi.gaussian_filter(g, 0.6 * SS)
    gsh = ndi.shift(g, (1.0 * SS, 0), order=1)      # light catches the lower lip of each groove
    rgb = lerp(rgb, col(S['gouge']), np.clip(g * 1.1, 0, 1) * 0.7)
    rgb = lerp(rgb, col(S['light']), np.clip(gsh - g, 0, 1) * 0.35)
    # wet sheen on fresh wood: long soft vertical highlights
    if S['wet'] > 0:
        sheen = smooth(0.7, 1.0, vnoise(H, W, 5, 14, seed + 6, 3)) * smooth(0.3, 0.5, 1 - np.abs(u))
        rgb = lerp(rgb, col((255, 255, 240)), sheen * 0.35 * S['wet'])
    # weathering: cracks along the grain and grey staining on the old stages
    if stage >= 2:
        cr = vnoise(H, W, 3, 30, seed + 7, 3)
        crack = np.clip(1 - np.abs(cr - 0.5) * 40, 0, 1) * smooth(0.3, 0.7, vnoise(H, W, 8, 3, seed + 8, 2))
        rgb = lerp(rgb, col(S['lip']), crack * (0.55 if stage == 2 else 0.8))
    # little islands of bark left inside, near the edges
    isl = (vnoise(H, W, 20, 8, seed + 10, 3) > 0.83) & (dist < 6)
    # edges: a thin phloem line, then the torn bark lip with lifted flakes
    rgb = lerp(rgb, col(S['edge']), smooth(2.2, 0.6, dist) * inside)
    rgb = np.where(isl[..., None], lerp(col(S['lip']), col(S['edge']), 0.35), rgb)
    lipw = 0.6 + 2.6 * vnoise(H, W, 30, 3, seed + 11, 2) ** 1.5
    lip = (~inside) & (out < lipw)
    lipc = lerp(col(S['lip']), col(S['edge']), np.clip(smooth(lipw * 0.8, 0, out) * 0.5 + smooth(0.6, 0.95, vnoise(H, W, 40, 3, seed + 14, 2)) * 0.6, 0, 1))
    lipc = lipc * (0.82 + 0.3 * vnoise(H, W, 30, 10, seed + 12, 2))[..., None]
    rgb = np.where(lip[..., None], lipc, rgb)
    a = np.where(inside | lip, 255.0, 0.0)
    # torn bark strings hanging across the top/bottom ends
    strings = (np.abs(u) < half * 0.9) & torn & (end > 0.02) & (end < 0.05 + 0.11 * tongues - 0.035) & (vnoise(H, W, 2, 40, seed + 13, 2) > 0.7)
    img = np.concatenate([np.clip(rgb, 0, 255), a[..., None]], 2)
    return down(img)


def curl(kind, variant, seed):
    """bark curl strip: 'bark' outer side, 'fresh'/'old' inner side; anchor at the top, tapering ragged tip"""
    H, W = 128 * SS, 32 * SS
    ys, xs = np.mgrid[0:H, 0:W]
    u = (xs + 0.5) / W * 2 - 1; v = (ys + 0.5) / H
    n = vnoise(H, W, 10, 2, seed, 3)
    half = (0.85 - 0.55 * v ** 1.4) * (0.85 + 0.3 * (n - 0.5))
    tipcut = v < 0.93 - 0.12 * vnoise(H, W, 2, 6, seed + 1, 2)
    m = (np.abs(u) < half) & tipcut
    fib = vnoise(H, W, 4, 18, seed + 2, 3)
    if kind == 'bark':
        rgb = lerp(col((86, 66, 50)), col((60, 46, 36)), fib)
        rgb = lerp(rgb, col((120, 98, 76)), smooth(0.75, 1, vnoise(H, W, 16, 6, seed + 3, 2)) * 0.6)
    elif kind == 'fresh':
        rgb = lerp(col((236, 222, 176)), col((200, 150, 100)), smooth(0.35, 1, fib) * 0.7)
        rgb = lerp(rgb, col((214, 132, 90)), smooth(0.5, 0.95, np.abs(u) / np.maximum(half, 1e-3)) * 0.6)
    else:
        rgb = lerp(col((150, 118, 84)), col((96, 74, 56)), fib)
    rgb = rgb * (0.8 + 0.2 * (1 - np.abs(u)))[..., None]       # rolled: darker at the edges
    a = np.where(m, 255.0, 0.0)
    return down(np.concatenate([rgb, a[..., None]], 2))


def shavings(stage, seed):
    H, W = 128 * SS, 128 * SS
    img = np.zeros((H, W, 4))
    r = np.random.default_rng(seed)
    ys, xs = np.mgrid[0:H, 0:W]
    # density falls off away from the trunk (top edge, centre)
    for i in range(140):
        cy = abs(r.normal(0.0, 0.32)) * H * 0.9 + 6
        cx = (0.5 + r.normal(0, 0.24)) * W
        if not (4 < cx < W - 4 and cy < H - 4):
            continue
        L = r.uniform(3, 11) * SS; wdt = r.uniform(0.8, 2.4) * SS; ang = r.uniform(0, np.pi)
        x1 = cx + np.cos(ang) * L; y1 = cy + np.sin(ang) * L
        m = line(H, W, cx, cy, x1, y1, wdt)
        bark = r.random() < 0.45
        if stage == 0:
            c = col((84, 64, 48)) if bark else col((228, 214, 168)) * r.uniform(0.9, 1.05)
        else:
            c = col((70, 56, 44)) if bark else col((150, 120, 86)) * r.uniform(0.85, 1.05)
        sel = m > 0.35
        img[sel, :3] = c * (0.85 + 0.15 * m[sel])[:, None]
        img[sel, 3] = 255
    return down(img)


def bark_tile(seed):
    H, W = 64 * SS, 64 * SS
    f = vnoise(H, W, 3, 10, seed, 4)
    ridge = np.abs(f - 0.5) * 2
    rgb = lerp(col((92, 76, 62)), col((58, 48, 40)), smooth(0.2, 0.9, ridge))
    rgb = lerp(rgb, col((118, 110, 96)), smooth(0.8, 1, vnoise(H, W, 10, 10, seed + 1, 2)) * 0.5)
    return down(np.concatenate([rgb, np.full((H, W, 1), 255.0)], 2))


def wood(stage, seed):
    H, W = 64 * SS, 32 * SS
    f = vnoise(H, W, 4, 6, seed, 3)
    if stage == 0:
        rgb = lerp(col((236, 224, 184)), col((206, 182, 132)), f)
    else:
        rgb = lerp(col((150, 140, 124)), col((108, 98, 86)), f)
    return down(np.concatenate([rgb, np.full((H, W, 1), 255.0)], 2))


LEAF_COLS = [(150, 118, 46), (176, 104, 40), (128, 112, 52), (156, 72, 38), (110, 104, 50), (186, 142, 60)]


def leaf_sprite(img, cx, cy, L, Wd, ang, c, a_scale=1.0):
    H, W = img.shape[:2]
    ys, xs = np.mgrid[0:H, 0:W]
    ca, sa = np.cos(ang), np.sin(ang)
    lx = (xs - cx) * ca + (ys - cy) * sa; ly = -(xs - cx) * sa + (ys - cy) * ca
    t = lx / L
    halfw = Wd * np.sin(np.pi * np.clip(t, 0, 1)) ** 0.8
    m = (t >= 0) & (t <= 1) & (np.abs(ly) < halfw)
    if not m.any():
        return
    shade = 0.82 + 0.25 * (ly / np.maximum(halfw, 1e-3)) * 0.5
    rib = np.abs(ly) < 0.6 * SS
    cc = np.asarray(c, float)[None, :] * shade[m][:, None]
    cc = np.where(rib[m][:, None], cc * 0.78, cc)
    img[m, :3] = cc
    img[m, 3] = 255 * a_scale
    stem = (t < 0) & (t > -0.18) & (np.abs(ly) < 0.7 * SS)
    img[stem, :3] = (96, 74, 48); img[stem, 3] = 255


def leaves(seed):
    H, W = 64 * SS, 64 * SS
    img = np.zeros((H, W, 4))
    r = np.random.default_rng(seed)
    for i in range(7):
        ang = -np.pi / 2 + r.uniform(-1.1, 1.1)
        cx = W * 0.5 + r.normal(0, 4) * SS; cy = H * 0.86
        L = r.uniform(18, 26) * SS; wd = L * r.uniform(0.28, 0.36)
        c = np.array(LEAF_COLS[r.integers(len(LEAF_COLS))]) * r.uniform(0.9, 1.1)
        leaf_sprite(img, cx + np.cos(ang) * 3 * SS, cy + np.sin(ang) * 3 * SS, L, wd, ang, c)
    # little twig in the middle
    m = line(H, W, W * 0.5, H, W * 0.5, H * 0.55, 1.2 * SS) > 0.3
    img[m, :3] = (84, 66, 50); img[m, 3] = 255
    return down(img)


# ----------------------------------------------------------------------------------------------------- scrape
SCRAPE_STAGES = [
    dict(soil=(46, 34, 25), soil2=(66, 50, 36), grit=(110, 92, 72), berm=(84, 64, 46), litter=1.0, inleaf=0.00, soilA=1.0),
    dict(soil=(70, 54, 40), soil2=(88, 70, 52), grit=(122, 104, 84), berm=(96, 76, 56), litter=0.9, inleaf=0.05, soilA=1.0),
    dict(soil=(98, 80, 60), soil2=(112, 94, 72), grit=(140, 122, 98), berm=(108, 90, 68), litter=0.65, inleaf=0.22, soilA=0.95),
    dict(soil=(104, 88, 68), soil2=(118, 102, 80), grit=(140, 124, 102), berm=(110, 94, 72), litter=0.35, inleaf=0.6, soilA=0.75),
]


def hoof(img_mask_h, img_mask_w, cx, cy, size, ang):
    """cloven print (mask 0..1), tips toward -y rotated by ang"""
    ys, xs = np.mgrid[0:img_mask_h, 0:img_mask_w]
    ca, sa = np.cos(ang), np.sin(ang)
    lx = ((xs - cx) * ca + (ys - cy) * sa) / size; ly = (-(xs - cx) * sa + (ys - cy) * ca) / size
    m = np.zeros(xs.shape)
    for s in (-1, 1):
        dx = lx - s * 0.28
        # half-heart: pointed toe at ly=-1, round heel at ly=0.8
        w = 0.24 * np.sin(np.pi * np.clip((ly + 1) / 1.9, 0, 1)) ** 0.7
        m = np.maximum(m, ((np.abs(dx + s * 0.04 * (ly - 0.3)) < w) & (ly > -1) & (ly < 0.9)).astype(float))
    return m


def scrape(stage, seed):
    S = SCRAPE_STAGES[stage]
    N = 256 * SS
    ys, xs = np.mgrid[0:N, 0:N]
    X = (xs + 0.5) / N * 1.5 - 0.75          # metres across
    Y = (ys + 0.5) / N * 1.5 - 0.75          # metres along (-: tree side)
    r = np.random.default_rng(seed)
    a_ax, b_ax = 0.34, 0.50
    cy0 = -0.04
    n1 = vnoise(N, N, 7, 7, seed, 4); n2 = vnoise(N, N, 30, 30, seed + 1, 3)
    rr = np.sqrt((X / a_ax) ** 2 + ((Y - cy0) / b_ax) ** 2)
    th = np.arctan2(Y - cy0, X)                                   # +pi/2 = straight back
    lobes = 0.07 * np.sin(3 * th + r.uniform(0, 6)) + 0.05 * np.sin(5 * th + r.uniform(0, 6))
    back = np.exp(-((th - np.pi / 2) / 0.8) ** 2)                 # pawed strokes thrown backwards
    strokes = (vnoise(N, N, 2, 2, seed + 20, 1) * 0 + np.sin(th * 23 + r.uniform(0, 6)) * 0.5 + 0.5) ** 6
    edge = 1 + lobes + 0.18 * (n1 - 0.5) + 0.07 * (n2 - 0.5) + back * (0.08 + 0.22 * strokes)
    inside = rr < edge
    depth = np.clip(1 - rr / edge, 0, 1)
    # soil: mottled, grit, root threads, paw drags toward the back (+y)
    mott = vnoise(N, N, 16, 16, seed + 2, 4)
    rgb = lerp(col(S['soil']), col(S['soil2']), smooth(0.35, 0.9, mott))
    rgb = rgb * (0.86 + 0.18 * smooth(0, 0.7, 1 - depth))[..., None]     # darker in the deep middle
    drag = vnoise(N, N, 3, 26, seed + 3, 3)                                 # streaks along y
    grooves = np.clip(1 - np.abs(drag - 0.5) * 9, 0, 1) * smooth(0.0, 0.3, depth)
    rgb = lerp(rgb, col(S['soil']) * 0.6, grooves * (0.75 if stage < 2 else 0.4))
    rgb = lerp(rgb, col(S['grit']), (ndi.shift(grooves, (0, 2 * SS), order=1) - grooves).clip(0, 1) * 0.45)
    grit = (vnoise(N, N, 150, 150, seed + 4, 1) > 0.95) & (vnoise(N, N, 8, 8, seed + 23, 2) > 0.45)
    rgb = np.where((grit & inside)[..., None], lerp(rgb, col(S['grit']), 0.6), rgb)
    roots = np.zeros((N, N))
    for _ in range(r.integers(3, 6)):
        x0, y0 = r.uniform(-0.25, 0.25), r.uniform(-0.35, 0.3)
        ang = r.uniform(0, np.pi); L = r.uniform(0.08, 0.2)
        roots = np.maximum(roots, line(N, N, (x0 + 0.75) / 1.5 * N, (y0 + 0.75) / 1.5 * N,
                                       (x0 + np.cos(ang) * L + 0.75) / 1.5 * N, (y0 + np.sin(ang) * L + 0.75) / 1.5 * N, 0.9 * SS))
    rgb = lerp(rgb, col((150, 118, 84)) * (0.8 if stage >= 2 else 1.0), roots * 0.7)
    # tracks inside: one or two prints, toes toward the tree
    if stage <= 2:
        for k in range(r.integers(1, 3)):
            px = r.uniform(-0.12, 0.12); py = r.uniform(-0.18, 0.2)
            m = hoof(N, N, (px + 0.75) / 1.5 * N, (py + 0.75) / 1.5 * N, 0.05 / 1.5 * N, r.uniform(-0.3, 0.3))
            m = ndi.gaussian_filter(m, 0.8 * SS)
            rgb = lerp(rgb, col(S['soil']) * 0.55, np.clip(m * 1.3, 0, 1) * (0.9 if stage == 0 else 0.55))
    # berm: crumbly raised rim, heavier at the back where the earth was thrown
    rim = np.clip(1 - np.abs(rr - edge) / 0.1, 0, 1) * (0.5 + 0.5 * smooth(-0.1, 0.4, Y))
    crumbs = vnoise(N, N, 60, 60, seed + 5, 2)
    berm = rim * smooth(0.35, 0.75, crumbs + rim * 0.3)
    rgb = lerp(rgb, col(S['berm']) * (0.85 + 0.3 * crumbs[..., None]), np.clip(berm, 0, 1) * 0.9)
    # dry crust: paler flakes toward the rim as the scrape dries
    if stage >= 1:
        crust = smooth(0.55, 0.85, vnoise(N, N, 24, 24, seed + 21, 3)) * smooth(0.5, 0.0, depth)
        rgb = lerp(rgb, col(S['grit']), crust * (0.25 + 0.15 * stage))
    a = np.where(inside, 255.0, 0.0)
    if S['soilA'] < 1:                    # old scrape: grass and litter creep back over the bare earth (cut-out speckle)
        keep = vnoise(N, N, 40, 40, seed + 22, 3) < (S['soilA'] - 0.15 + depth * 0.6)
        a = np.where(keep, a, 0.0)
    elif stage == 2:
        keep = vnoise(N, N, 40, 40, seed + 22, 3) < 0.55 + depth * 2.0
        a = np.where(keep, a, 0.0)
    a = np.maximum(a, np.where(berm > 0.35, 255.0, 0.0))
    img = np.concatenate([rgb, a[..., None]], 2)
    # thrown clods beyond the rim (behind and to the sides)
    for i in range(int(60 * S['litter'])):
        ang = np.pi / 2 + r.normal(0, 0.75)
        d = r.uniform(1.02, 1.5)
        cx = np.cos(ang) * a_ax * d; cy = cy0 + np.sin(ang) * b_ax * d
        rad = r.uniform(0.006, 0.016) / 1.5 * N
        m = (xs - (cx + 0.75) / 1.5 * N) ** 2 + (ys - (cy + 0.75) / 1.5 * N) ** 2 < rad * rad
        img[m, :3] = col(S['berm']) * r.uniform(0.75, 1.1); img[m, 3] = 255
    # kicked leaf litter: a crescent pile behind the scrape, leaves scattered beyond, torn grass tufts
    for i in range(int(150 * S['litter'])):
        ang = np.pi / 2 + r.normal(0, 0.62)
        d = abs(r.normal(1.08, 0.13))
        cx = np.cos(ang) * a_ax * d + r.normal(0, 0.02); cy = cy0 + np.sin(ang) * b_ax * d
        if d < 0.98:
            continue
        c = np.array(LEAF_COLS[r.integers(len(LEAF_COLS))]) * r.uniform(0.7, 1.0) * (0.85 if stage >= 2 else 1.0)
        L = r.uniform(0.035, 0.07) / 1.5 * N
        leaf_sprite(img, (cx + 0.75) / 1.5 * N, (cy + 0.75) / 1.5 * N, L, L * r.uniform(0.28, 0.4), r.uniform(0, 2 * np.pi), c)
    for i in range(int(14 * S['litter'])):
        ang = np.pi / 2 + r.normal(0, 0.8); d = r.uniform(1.0, 1.3)
        bx = (np.cos(ang) * a_ax * d + 0.75) / 1.5 * N; by = (cy0 + np.sin(ang) * b_ax * d + 0.75) / 1.5 * N
        for j in range(r.integers(4, 8)):
            ga = r.uniform(0, 2 * np.pi); gl = r.uniform(0.03, 0.06) / 1.5 * N
            m = line(N, N, bx, by, bx + np.cos(ga) * gl, by + np.sin(ga) * gl, 0.9 * SS) > 0.3
            img[m, :3] = col((96, 120, 52)) * r.uniform(0.8, 1.15) * (0.8 if stage >= 2 else 1.0); img[m, 3] = 255
    # leaves blown back in over the days
    for i in range(int(90 * S['inleaf'])):
        cx = r.uniform(-0.9, 0.9) * a_ax; cy = cy0 + r.uniform(-0.9, 0.9) * b_ax
        if (cx / a_ax) ** 2 + ((cy - cy0) / b_ax) ** 2 > 1:
            continue
        c = np.array(LEAF_COLS[r.integers(len(LEAF_COLS))]) * r.uniform(0.7, 0.95)
        L = r.uniform(0.04, 0.07) / 1.5 * N
        leaf_sprite(img, (cx + 0.75) / 1.5 * N, (cy + 0.75) / 1.5 * N, L, L * 0.34, r.uniform(0, 2 * np.pi), c)
    return down(img)


# ----------------------------------------------------------------------------------------------------- atlas
def paste(atlas, tile, x, y):
    h, w = tile.shape[:2]
    atlas[y:y + h, x:x + w] = tile


def main():
    atlas = np.zeros((512, 1024, 4))
    for i in range(4):
        paste(atlas, bleed(rub(i, 100)), i * 128, 0)
    for k in range(2):
        paste(atlas, bleed(curl('bark', k, 200 + k)), 512 + k * 32, 0)
        paste(atlas, bleed(curl('fresh', k, 200 + k)), 576 + k * 32, 0)
        paste(atlas, bleed(curl('old', k, 200 + k)), 640 + k * 32, 0)
    paste(atlas, bleed(shavings(0, 300)), 704, 0)
    paste(atlas, bleed(shavings(1, 300)), 704, 128)
    paste(atlas, bark_tile(400), 832, 0)
    paste(atlas, wood(0, 410), 896, 0)
    paste(atlas, wood(1, 410), 928, 0)
    paste(atlas, bleed(leaves(500)), 832, 64)
    paste(atlas, bleed(leaves(501)), 896, 64)
    for i in range(4):
        paste(atlas, bleed(scrape(i, 600)), i * 256, 256)
    atlas = np.clip(atlas, 0, 255).astype(np.uint8)
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(atlas, 'RGBA').save(os.path.join(OUT, 'deer_sign.png'), optimize=True)
    # far copy: premultiplied 4x box reduction (no mipmaps on entity textures: this keeps distant signs from shimmering)
    a = atlas.astype(float); al = a[..., 3:4] / 255.0
    pm = np.concatenate([a[..., :3] * al, al * 255], 2).reshape(128, 4, 256, 4, 4).mean(axis=(1, 3))
    fa = pm[..., 3:4] / 255.0
    rgb = np.where(fa > 1e-3, pm[..., :3] / np.maximum(fa, 1e-3), 0)
    far = np.concatenate([rgb, np.where(pm[..., 3:4] > 110, 255.0, 0.0)], 2)
    far = bleed(far, 2)
    Image.fromarray(np.clip(far, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'deer_sign_far.png'), optimize=True)
    print('wrote', os.path.join(OUT, 'deer_sign.png'))


if __name__ == '__main__':
    main()
