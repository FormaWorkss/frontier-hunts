"""[sticks] Procedural textures for the shooting sticks (numpy). Deterministic."""
import numpy as np

W, H = 256, 128


def rng(seed):
    return np.random.default_rng(seed)


def value_noise(w, h, cell_x, cell_y, seed, wrap_x=True):
    """Smooth value noise, tileable in x when wrap_x (tubes wrap round)."""
    r = rng(seed)
    gx = max(1, int(round(w / cell_x)))
    gy = max(1, int(round(h / cell_y))) + 1
    g = r.random((gy + 1, gx + (0 if wrap_x else 1)))
    xs = np.arange(w) / w * gx
    ys = np.arange(h) / h * (gy - 1)
    x0 = np.floor(xs).astype(int)
    y0 = np.floor(ys).astype(int)
    fx = xs - x0
    fy = ys - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    x1 = (x0 + 1) % gx if wrap_x else np.minimum(x0 + 1, gx)
    x0 = x0 % gx if wrap_x else x0
    a = g[y0][:, x0]
    b = g[y0][:, x1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]


def fbm(w, h, cx, cy, seed, octaves=4, wrap_x=True):
    out = np.zeros((h, w))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += amp * value_noise(w, h, max(1, cx / 2 ** o), max(1, cy / 2 ** o), seed + o * 101, wrap_x)
        tot += amp
        amp *= 0.5
    return out / tot


def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + b * t


def col(*rgb):
    return np.array(rgb, float) / 255.0


def scratches(w, h, seed, n, length=(3, 9), vertical=True, angle_jitter=0.12):
    """Mask of thin bright scratch lines (mostly along the tube)."""
    r = rng(seed)
    m = np.zeros((h, w))
    for _ in range(n):
        x = r.uniform(0, w)
        y = r.uniform(0, h)
        L = r.uniform(*length)
        ang = (np.pi / 2 if vertical else r.uniform(0, np.pi)) + r.normal(0, angle_jitter)
        steps = int(L * 2)
        strength = r.uniform(0.25, 0.6)
        for s in range(steps):
            px = int(x + np.cos(ang) * s * 0.5) % w
            py = int(y + np.sin(ang) * s * 0.5)
            if 0 <= py < h:
                m[py, px] = max(m[py, px], strength * (1 - abs(s / steps - 0.5)))
    return m


def blobs(w, h, seed, n, rad=(0.8, 2.6), wrap_x=True):
    r = rng(seed)
    m = np.zeros((h, w))
    yy, xx = np.mgrid[0:h, 0:w]
    for _ in range(n):
        cx, cy = r.uniform(0, w), r.uniform(0, h)
        rx = r.uniform(*rad)
        ry = rx * r.uniform(0.6, 1.8)
        dx = xx - cx
        if wrap_x:
            dx = (dx + w / 2) % w - w / 2
        d = (dx / rx) ** 2 + ((yy - cy) / ry) ** 2
        m = np.maximum(m, np.clip(1.2 - d, 0, 1) * r.uniform(0.5, 1))
    return m


# ------------------------------------------------------------------------------------------- materials

def camo(w, h, seed):
    """Hydro-dipped woodland camo on aluminium: pale bark base, grey-brown bark plates, olive leaf masses with dark
    shadow edges, and a few dark branches (Break-Up style)."""
    r = rng(seed)
    base = mix(col(150, 140, 118), col(170, 160, 136), fbm(w, h, 12, 16, seed, 3))
    bark = fbm(w, h, 6, 20, seed + 11, 4)
    img = mix(base, col(112, 98, 80), np.clip((bark - 0.5) * 5, 0, 1))
    img = mix(img, col(84, 70, 56), np.clip((bark - 0.64) * 6, 0, 1))
    leaf = fbm(w, h, 8, 10, seed + 7, 4)
    shadow = np.roll(np.clip((leaf - 0.55) * 8, 0, 1), 1, axis=0)
    img = mix(img, col(52, 46, 36), shadow * 0.65)
    img = mix(img, col(104, 106, 72), np.clip((leaf - 0.55) * 8, 0, 1))
    yy, xx = np.mgrid[0:h, 0:w]
    for _ in range(4):
        x0 = r.uniform(0, w)
        wid = r.uniform(0.6, 1.2)
        wav = r.uniform(1.0, 2.0)
        y0 = r.uniform(-20, h - 30)
        y1 = y0 + r.uniform(25, 60)
        cx = x0 + r.normal(0, 0.1) * (yy - y0) + np.sin(yy * 0.11 + x0) * wav
        dx = (xx - cx + w / 2) % w - w / 2
        mm = np.clip(1.3 - np.abs(dx) / wid, 0, 1) * ((yy > y0) & (yy < y1))
        img = mix(img, col(58, 46, 36), mm * 0.85)
    fine = fbm(w, h, 2, 3, seed + 5, 2)
    img = img * (0.93 + 0.14 * fine[..., None])
    return img


def anodized(w, h, seed, tone=(40, 41, 42)):
    base = col(*tone)
    brush = fbm(w, h, 1.2, 30, seed, 3)
    img = base[None, None, :] * (0.88 + 0.24 * brush[..., None])
    return img


def brushed_alu(w, h, seed):
    base = col(158, 160, 158)
    brush = fbm(w, h, 1.0, 40, seed, 3)
    tint = fbm(w, h, 12, 16, seed + 2, 2)
    img = base[None, None, :] * (0.84 + 0.22 * brush[..., None]) * (0.95 + 0.08 * tint[..., None])
    return img


def wear(img, seed, scratch_n, chip_n, bright=col(196, 198, 196), vertical=True, chip_rad=(0.5, 1.6)):
    h, w = img.shape[:2]
    s = scratches(w, h, seed, scratch_n, vertical=vertical)
    img = mix(img, bright, s * 0.6)
    c = blobs(w, h, seed + 1, chip_n, rad=chip_rad)
    img = mix(img, bright * 0.9, np.clip((c - 0.3) * 2.5, 0, 1) * 0.75)
    return img


def dirt(img, seed, strength, from_bottom=True, tone=col(84, 66, 46)):
    """Mud and dust, heavier towards the bottom (v = h)."""
    h, w = img.shape[:2]
    yy = np.linspace(0, 1, h)[:, None] if from_bottom else np.ones((h, 1))
    n = fbm(w, h, 4, 4, seed, 4)
    splat = blobs(w, h, seed + 9, int(10 * strength) + 2, rad=(0.6, 2.2))
    m = np.clip((yy ** 2.2) * 1.4 * strength + (n - 0.55) * 1.6, 0, 1) * np.clip(n * 1.5, 0, 1)
    m = np.maximum(m, splat * (yy ** 1.5) * strength)
    return mix(img, tone * (0.85 + 0.3 * n[..., None]), m * 0.9)


def rubber(w, h, seed, tone=(30, 30, 31)):
    n = fbm(w, h, 2.5, 2.5, seed, 3)
    img = col(*tone)[None, None, :] * (0.85 + 0.3 * n[..., None])
    return img


def foam(w, h, seed):
    """Rubber-skinned closed-cell foam: fine pores, a satin sheen worn into the middle where guns lie."""
    n = fbm(w, h, 1.5, 1.5, seed, 2)
    pores = rng(seed + 4).random((h, w)) < 0.09
    img = col(36, 36, 37)[None, None, :] * (0.86 + 0.28 * n[..., None])
    img[pores] *= 0.62
    yy, xx = np.mgrid[0:h, 0:w]
    band = np.exp(-((xx - w / 2) / (w * 0.22)) ** 2) * (0.5 + 0.5 * fbm(w, h, 3, 6, seed + 6, 2))
    img = mix(img, col(70, 70, 68), band * 0.55)
    # scuffs and a couple of cuts in the skin
    img = mix(img, col(88, 86, 80), scratches(w, h, seed + 8, 14, length=(2, 8), vertical=False) * 0.6)
    return img


def webbing(w, h, seed):
    yy, xx = np.mgrid[0:h, 0:w]
    weave = (np.sin(xx * 2.6) * np.sin(yy * 2.6) * 0.5 + 0.5)
    base = col(92, 88, 62)
    n = fbm(w, h, 6, 3, seed, 3)
    img = base[None, None, :] * (0.82 + 0.16 * weave[..., None] + 0.14 * n[..., None])
    # stitching rows near both edges
    for row in (2, h - 3):
        m = ((xx // 2) % 2 == 0) & (yy == row)
        img[m] = col(150, 140, 100)
    img = dirt(img, seed + 3, 0.5, from_bottom=False, tone=col(70, 60, 44))
    return img


def cast(w, h, seed, tone=(58, 58, 60)):
    n = fbm(w, h, 6, 6, seed, 3)
    img = col(*tone)[None, None, :] * (0.88 + 0.22 * n[..., None])
    return wear(img, seed + 1, 3, 3, bright=col(120, 120, 118), vertical=False, chip_rad=(0.4, 0.8))


def ribbed(w, h, seed, tone=(34, 35, 36), pitch=3):
    n = fbm(w, h, 2, 2, seed, 2)
    yy, xx = np.mgrid[0:h, 0:w]
    rib = (xx % pitch == 0) * 0.25
    img = col(*tone)[None, None, :] * (0.9 + 0.2 * n[..., None] + rib[..., None])
    return img
