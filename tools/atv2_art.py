#!/usr/bin/env python3
"""[atv2] Procedural art for ATV water driving (original, generated here).

python3 tools/atv2_art.py [repo_root] [--preview <dir>]

Writes, under patch/assets/frontierhunts/:
  textures/particle/atv_spray_{0..3}.png   64   spray plumes / fans thrown off the wheels and the bow wave. The sprite's
                                                 TOP is its direction of travel (the particle rolls itself to its
                                                 screen-space velocity): dense droplet head on top, thinning mist
                                                 and fine streaks trailing below.
  particles/atv_spray.json
  textures/gui/atv_grime/water.png       1024   4x4 atlas of driver-screen water (cells of 256):
      0-3  bead clusters (refractive droplets: dark lower rim, caustic, specular glint; clear centres)
      4-7  runs (a drop sliding down with its wet trail; drop at the BOTTOM of the cell)
      8-11 sheets (a wash of water sliding down the view: faint body, ripple highlights, wavy darker edge, a ragged
           lower front where it breaks into drips)
      12-15 spray specks (fine mist of tiny droplets, for spray at speed)
"""
import math, os, sys, json
import numpy as np
from PIL import Image, ImageFilter

args = [a for a in sys.argv[1:] if not a.startswith('--')]
R = os.path.abspath(args[0] if args else os.path.join(os.path.dirname(__file__), '..'))
PREVIEW = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')
PT = os.path.join(A, 'textures', 'particle')
PD = os.path.join(A, 'particles')
GUI = os.path.join(A, 'textures', 'gui', 'atv_grime')
for d in (PT, PD, GUI):
    os.makedirs(d, exist_ok=True)
if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def vnoise(shape, scale, seed, octaves=4):
    """Value-noise fbm on a grid (0..1)."""
    rng = np.random.default_rng(seed)
    h, w = shape
    out = np.zeros(shape)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        f = scale * (2 ** o)
        gh, gw = int(h / f) + 3, int(w / f) + 3
        g = rng.random((gh, gw))
        img = Image.fromarray((g * 255).astype(np.uint8)).resize((int(gw * f), int(gh * f)), Image.BICUBIC)
        a = np.asarray(img).astype(float)[:h, :w] / 255
        out += a * amp
        tot += amp
        amp *= 0.5
    return out / tot


def blur(a, r):
    return np.asarray(Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))).astype(float) / 255


def save(arr, path):
    Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(path, optimize=True)


# ================================================================== spray particle sprites
def spray_sprites():
    S = 64
    out = []
    for i in range(4):
        rng = np.random.default_rng(900 + i)
        yy, xx = np.mgrid[0:S, 0:S] + 0.5
        X = (xx - S / 2) / (S / 2)          # -1..1
        Yt = (S - yy) / S                   # 0 at the bottom, 1 at the top (direction of travel)
        spread = [0.55, 0.75, 0.95, 0.65][i]
        # fan widening toward the head; head sits in the upper third
        width = 0.10 + spread * Yt ** 0.8
        body = (1 - smooth(0.55, 1.0, np.abs(X) / np.maximum(width, 1e-3))) * smooth(0.0, 0.25, Yt) * (1 - smooth(0.82, 1.0, Yt))
        n = vnoise((S, S), 6, 40 + i, 3)
        mist = body * (0.25 + 0.5 * n) * 0.55
        # fine streaks radiating from the root (bottom centre) toward the head
        ang = np.arctan2(X, np.maximum(Yt, 0.02))
        streak = np.zeros((S, S))
        for k in range(int(rng.integers(9, 15))):
            a0 = rng.uniform(-spread * 0.9, spread * 0.9) * 0.9
            w = rng.uniform(0.012, 0.03)
            L0, L1 = rng.uniform(0.2, 0.5), rng.uniform(0.65, 0.95)
            line = np.exp(-((ang - a0) / w) ** 2) * smooth(L0, L0 + 0.15, Yt) * (1 - smooth(L1 - 0.1, L1, Yt))
            streak = np.maximum(streak, line * rng.uniform(0.5, 0.9))
        # droplets: denser near the head, a few flung beyond the edge
        drops = np.zeros((S, S))
        for k in range(int(rng.integers(26, 40))):
            ty = rng.uniform(0.35, 0.97) ** 0.6
            tx = rng.normal(0, 0.45) * (0.1 + spread * ty ** 0.8)
            r = rng.uniform(0.6, 1.6) * (1.25 if ty > 0.7 else 1.0)
            cx, cy = S / 2 + tx * S / 2, S - ty * S
            d = np.hypot(xx - cx, (yy - cy) * 0.85)
            drops = np.maximum(drops, 1 - smooth(r * 0.5, r + 0.6, d))
        alpha = np.clip(np.maximum(mist, np.maximum(streak * 0.75, drops * 0.95)), 0, 1)
        # colour: white-blue spray, droplets slightly brighter, mist a touch grey (reads against sky and water)
        col = 0.86 + 0.12 * drops + 0.06 * streak - 0.04 * (1 - n)
        arr = np.zeros((S, S, 4))
        arr[..., 0] = col * 0.94
        arr[..., 1] = col * 0.97
        arr[..., 2] = np.minimum(1, col * 1.0)
        arr[..., 3] = alpha
        save(arr, os.path.join(PT, f'atv_spray_{i}.png'))
        out.append(arr)
    json.dump({'textures': [f'frontierhunts:atv_spray_{i}' for i in range(4)]}, open(os.path.join(PD, 'atv_spray.json'), 'w'), indent=2)
    return out


# ================================================================== driver-screen water atlas
C = 256


def grid():
    yy, xx = np.mgrid[0:C, 0:C] + 0.5
    return (xx - C / 2) / (C / 2), (yy - C / 2) / (C / 2)


def droplet(out, cx, cy, rr, elong=1.12, strength=1.0):
    """One lens-like water droplet composited into out (C x C x 4). Clear centre, dark lower-right rim, bright caustic
    at the bottom, small specular glint upper-left: reads as water on glass without any refraction pass."""
    X, Y = grid()
    dx = (X - cx) / rr
    dy = (Y - cy) / (rr * elong)
    dd = np.sqrt(dx * dx + dy * dy)
    inside = dd < 1
    if not inside.any():
        return
    edge = smooth(0.92, 1.0, dd)
    rim = smooth(0.55, 1.0, dd) * (1 - edge)
    side = smooth(-0.7, 0.7, dx * 0.6 + dy)          # lower-right of the drop is the dark side
    gl = np.exp(-(((dx + 0.36) / 0.17) ** 2 + ((dy + 0.40) / 0.12) ** 2))
    ca = np.exp(-(((dx - 0.05) / 0.42) ** 2 + ((dy - 0.58) / 0.17) ** 2))
    col = np.ones(X.shape + (3,)) * np.array([0.78, 0.83, 0.88])
    col = col * (1 - 0.72 * (rim * side)[..., None])
    col = col * (1 - (0.85 * ca)[..., None]) + np.array([0.97, 0.99, 1.0]) * (0.85 * ca)[..., None]
    col = col * (1 - gl[..., None]) + gl[..., None]
    a = (0.07 + 0.45 * rim * (0.35 + 0.65 * side) + 0.55 * ca + 0.9 * gl) * (1 - edge)
    a = np.clip(a * strength, 0, 1) * inside
    sel = a > out[..., 3]
    out[..., :3] = np.where(sel[..., None], col, out[..., :3])
    out[..., 3] = np.maximum(out[..., 3], a)


def bead_cell(seed):
    r = np.random.default_rng(seed)
    out = np.zeros((C, C, 4))
    drops = [(0.0, 0.05, r.uniform(0.26, 0.34))]
    for _ in range(int(r.integers(6, 12))):
        drops.append((r.uniform(-0.7, 0.7), r.uniform(-0.7, 0.7), r.uniform(0.05, 0.16)))
    for cx, cy, rr in sorted(drops, key=lambda d: d[2]):
        droplet(out, cx, cy, rr, r.uniform(1.0, 1.2))
    return out


def run_cell(seed):
    r = np.random.default_rng(seed)
    X, Y = grid()
    out = np.zeros((C, C, 4))
    # wet trail above the drop: faint film with darker meandering edges
    wob = 0.04 * np.sin(Y * 5 + seed) + 0.02 * np.sin(Y * 13 + seed * 2)
    w = 0.05 + 0.025 * smooth(-0.95, 0.55, Y)
    t = np.abs(X - wob)
    trail = (t < w) & (Y > -0.98) & (Y < 0.6)
    fade = smooth(-0.98, 0.3, Y)
    edge = trail & (t > w * 0.55)
    out[..., :3] = np.where(trail[..., None], np.array([0.80, 0.84, 0.89]), 0)
    out[..., 3] = np.where(trail, 0.10 + 0.12 * fade, 0)
    out[..., :3] = np.where(edge[..., None], np.array([0.30, 0.33, 0.37]), out[..., :3])
    out[..., 3] = np.where(edge, 0.16 + 0.22 * fade, out[..., 3])
    # a few stranded micro-beads along the trail
    for _ in range(int(r.integers(2, 5))):
        yy = r.uniform(-0.85, 0.4)
        droplet(out, 0.04 * math.sin(yy * 5 + seed) + r.uniform(-0.05, 0.05), yy, r.uniform(0.03, 0.06), 1.1, 0.8)
    # the running drop: heavier and elongated, at the bottom of the cell
    droplet(out, 0.04 * math.sin(0.68 * 5 + seed), 0.66, r.uniform(0.16, 0.22), 1.3)
    return out


def sheet_cell(seed):
    """A wash of water sliding down the view: an organic, mostly clear film with a thicker wavy rim, thin caustic ripple
    lines, vertical flow streaks, and a ragged lower front breaking into fingers and beads."""
    r = np.random.default_rng(seed)
    X, Y = grid()
    out = np.zeros((C, C, 4))
    wx = (vnoise((C, C), 40, seed + 5, 3) - 0.5) * 0.35
    wy = (vnoise((C, C), 40, seed + 6, 3) - 0.5) * 0.25
    Xw, Yw = X + wx, Y + wy
    # rounded top, widest a little above the middle, front at the bottom
    t = (Yw + 0.85) / 1.45
    half = 0.78 * np.sqrt(np.clip(t, 0, 1)) * (1 - 0.35 * np.clip(t - 0.6, 0, 1))
    shape = (1 - smooth(half - 0.06, half, np.abs(Xw))) * smooth(-0.9, -0.78, Yw) * (1 - smooth(0.52, 0.6, Yw))
    fing = np.zeros_like(X)
    for k in range(int(r.integers(4, 7))):
        x0 = r.uniform(-0.55, 0.55)
        L = r.uniform(0.12, 0.38)
        wv = r.uniform(0.04, 0.07)
        fing = np.maximum(fing, (1 - smooth(wv * 0.7, wv, np.abs(Xw - x0 - 0.03 * np.sin(Yw * 9 + k)))) * (1 - smooth(0.55 + L - 0.06, 0.55 + L, Yw)) * smooth(0.35, 0.5, Yw))
    m = blur(np.maximum(shape, fing), 1.0)
    inner = blur(m, 6)
    rim = np.clip((m - inner) * 2.6, 0, 1) * m
    # thin caustic ripple lines: contours of a warped noise field
    n = vnoise((C, C), 24, seed + 1, 3)
    lines = np.exp(-((n - 0.5) / 0.009) ** 2) + 0.5 * np.exp(-((n - 0.62) / 0.007) ** 2)
    lines = np.clip(lines, 0, 1) * m * (1 - rim)
    # vertical flow streaks
    st = vnoise((C, C), 3, seed + 2, 2)
    st = np.asarray(Image.fromarray((st * 255).astype(np.uint8)).resize((C // 10, C)).resize((C, C), Image.BICUBIC)).astype(float) / 255
    flow = smooth(0.62, 0.85, st) * m * 0.5
    col = np.ones((C, C, 3)) * np.array([0.84, 0.88, 0.93])
    col = col * (1 - 0.6 * rim)[..., None]
    hi = np.clip(lines * 0.9 + flow, 0, 1)
    col = col * (1 - hi[..., None]) + hi[..., None]
    a = m * 0.09 + 0.34 * rim + 0.26 * lines + 0.18 * flow
    out[..., :3] = col
    out[..., 3] = np.clip(a, 0, 1)
    # beads riding at the breaking front
    for _ in range(int(r.integers(3, 6))):
        droplet(out, r.uniform(-0.5, 0.5), r.uniform(0.55, 0.85), r.uniform(0.04, 0.09), 1.2, 0.9)
    return out


def speck_cell(seed):
    r = np.random.default_rng(seed)
    out = np.zeros((C, C, 4))
    for _ in range(int(r.integers(40, 70))):
        rr = r.uniform(0.015, 0.05) * (1.8 if r.random() < 0.12 else 1.0)
        a = r.uniform(0, 2 * math.pi)
        d = abs(r.normal(0, 0.45))
        droplet(out, math.cos(a) * d, math.sin(a) * d, rr, r.uniform(1.0, 1.25), 0.85)
    # faint mist haze under the specks
    X, Y = grid()
    haze = (1 - smooth(0.2, 0.95, np.hypot(X, Y))) * 0.06 * vnoise((C, C), 30, seed, 3)
    sel = haze > out[..., 3]
    out[..., :3] = np.where(sel[..., None], np.array([0.9, 0.93, 0.96]), out[..., :3])
    out[..., 3] = np.maximum(out[..., 3], haze)
    return out


def water_atlas():
    atlas = np.zeros((C * 4, C * 4, 4))
    cells = [bead_cell(700 + i) for i in range(4)] + [run_cell(710 + i) for i in range(4)] + \
            [sheet_cell(720 + i) for i in range(4)] + [speck_cell(730 + i) for i in range(4)]
    for k, c in enumerate(cells):
        cy, cx = divmod(k, 4)
        atlas[cy * C:(cy + 1) * C, cx * C:(cx + 1) * C] = c
    save(atlas, os.path.join(GUI, 'water.png'))
    return atlas


if __name__ == '__main__':
    sprites = spray_sprites()
    atlas = water_atlas()
    if PREVIEW:
        # spray sprites over sky blue and over dark water, 4x upscaled
        rows = []
        for bgc in ((0.55, 0.7, 0.85), (0.12, 0.2, 0.22)):
            row = []
            for s in sprites:
                bg = np.ones((64, 64, 3)) * np.array(bgc)
                a = s[..., 3:4]
                row.append(bg * (1 - a) + s[..., :3] * a)
            rows.append(np.concatenate(row, 1))
        img = np.concatenate(rows, 0)
        Image.fromarray((img * 255).astype(np.uint8)).resize((img.shape[1] * 4, img.shape[0] * 4), Image.NEAREST).save(os.path.join(PREVIEW, 'spray.png'))
        # atlas over a mid-grey landscape-ish gradient
        H = atlas.shape[0]
        yy = np.linspace(0, 1, H)[:, None, None]
        bg = np.concatenate([np.ones((H, H, 1)) * 0.0, np.ones((H, H, 1)), np.ones((H, H, 1))], 2)
        bg = (np.array([0.55, 0.68, 0.82]) * (1 - yy) + np.array([0.28, 0.36, 0.22]) * yy) * np.ones((H, H, 3))
        a = atlas[..., 3:4]
        comp = bg * (1 - a) + atlas[..., :3] * a
        Image.fromarray((comp * 255).astype(np.uint8)).save(os.path.join(PREVIEW, 'water_atlas.png'))
    print('done')
