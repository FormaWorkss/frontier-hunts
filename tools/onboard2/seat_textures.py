#!/usr/bin/env python3
"""[onboard2] Procedural textures for the seats. Every material is painted twice from the same layout:
  * Vanilla  16 x 16 (assets/frontierhunts/textures/block/seat/<m>.png): a clean pixel-art look, few tones, like
    vanilla planks/logs/wool.
  * Ultra    64 x 64 (resourcepacks/realistic_world/assets/frontierhunts/textures/block/seat/<m>.png): the same
    material with real grain, fibres, wear and stitching (the Ultra preset turns that pack on).
UVs are in 0..16 model units either way, so one set of block models serves both.
"""
import math
import os

import numpy as np
from PIL import Image

RNG = np.random.default_rng


def _noise(n, scale, seed, octaves=4):
    """Tileable value noise in [0,1], n x n."""
    rng = RNG(seed)
    out = np.zeros((n, n))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        cells = max(1, int(scale * (2 ** o)))
        g = rng.random((cells, cells))
        x = np.arange(n) * cells / n
        i0 = np.floor(x).astype(int) % cells
        i1 = (i0 + 1) % cells
        f = x - np.floor(x)
        f = f * f * (3 - 2 * f)
        a = g[i0][:, i0] * (1 - f)[None, :] + g[i0][:, i1] * f[None, :]
        b = g[i1][:, i0] * (1 - f)[None, :] + g[i1][:, i1] * f[None, :]
        out += amp * (a * (1 - f)[:, None] + b * f[:, None])
        tot += amp
        amp *= 0.5
    return out / tot


def _quant(img, palette):
    """Snap an RGB float image to the nearest palette colour (vanilla look)."""
    pal = np.array(palette, float) / 255.0
    d = ((img[..., None, :] - pal[None, None]) ** 2).sum(-1)
    return pal[d.argmin(-1)]


def _rgb(c):
    return np.array(c, float) / 255.0


def _save(arr, path, alpha=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    a = np.clip(arr, 0, 1)
    if alpha is None:
        alpha = np.ones(a.shape[:2])
    rgba = np.dstack([a, alpha])
    Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), 'RGBA').save(path)


def _lerp(c0, c1, t):
    return c0[None, None] * (1 - t[..., None]) + c1[None, None] * t[..., None]


# ------------------------------------------------------------------------------------------------ materials
# Each painter returns an RGB float array n x n (n = 16 or 64); 'v' = vanilla flag (simplify / quantise).

def weathered_pine(n, v):
    """Weathered pine boards along u (x): silver-brown, long grain, a few knots, darker gaps every 4 units."""
    y, x = np.mgrid[0:n, 0:n] / n
    grain = _noise(n, 3, 11, 5)
    streak = 0.5 + 0.5 * np.sin((y * 16 + grain * 3.2) * math.pi * 1.6)
    base = _lerp(_rgb((126, 112, 94)), _rgb((168, 154, 132)), 0.35 + 0.45 * grain)
    img = base * (0.86 + 0.14 * streak[..., None])
    # a darker edge on the board sides (each board is its own element: the gaps are real geometry)
    edge = np.minimum(y, 1 - y) < (1.0 / 16 if v else 0.6 / 16)
    img[edge] *= 0.8
    # knots
    for kx, ky in ((0.27, 0.36), (0.73, 0.86)):
        r = np.hypot((x - kx) * 1.6, (y - ky) * 3.0)
        img *= (1 - 0.35 * np.exp(-(r / 0.05) ** 2))[..., None]
    if v:
        return _quant(img, [(92, 80, 66), (120, 106, 88), (141, 127, 106), (161, 147, 125), (181, 168, 146)])
    fine = _noise(n, 24, 12, 2)
    return img * (0.94 + 0.08 * fine[..., None])


def weathered_post(n, v):
    """The same weathered pine with the grain running up the post (v)."""
    return np.transpose(weathered_pine(n, v), (1, 0, 2))[:, ::-1]


def stump_bark(n, v):
    """Pine bark running vertically (v = height): deep fissures, plates, a little lichen."""
    y, x = np.mgrid[0:n, 0:n] / n
    plates = _noise(n, 4, 21, 4)
    fiss = np.abs(np.sin((x * 7 + plates * 1.8) * math.pi))
    img = _lerp(_rgb((54, 38, 26)), _rgb((112, 82, 56)), np.clip(fiss * 1.15 - 0.1, 0, 1))
    hl = _noise(n, 9, 22, 2)
    img = img * (0.85 + 0.25 * hl[..., None])
    lichen = _noise(n, 6, 23, 3) > 0.74
    img[lichen] = img[lichen] * 0.55 + _rgb((128, 146, 104)) * 0.45
    if v:
        return _quant(img, [(46, 33, 24), (66, 47, 32), (88, 64, 44), (110, 82, 57), (121, 132, 96)])
    return img


def stump_top(n, v):
    """Sawn end: growth rings around the centre, pale sapwood, bark rim, a radial check, chainsaw arcs."""
    y, x = np.mgrid[0:n, 0:n] / n + 0.5 / n
    r = np.hypot(x - 0.5, y - 0.5) * 2  # 0 centre .. 1 at the edge midpoints
    wob = _noise(n, 3, 31, 3) * 0.05
    rings = 0.5 + 0.5 * np.cos((r + wob) * (9 if v else 22) * math.pi)
    heart = _lerp(_rgb((150, 104, 62)), _rgb((196, 150, 96)), rings)
    sap = _lerp(_rgb((206, 170, 118)), _rgb((222, 192, 142)), rings)
    t = np.clip((r - 0.45) / 0.25, 0, 1)
    img = heart * (1 - t[..., None]) + sap * t[..., None]
    bark = r > 0.86
    img[bark] = _rgb((70, 50, 34))
    img[(r > 0.80) & ~bark] *= 0.8
    # radial check (crack) from the centre
    ang = np.arctan2(y - 0.5, x - 0.5)
    crack = (np.abs(ang - 0.7) < (0.06 if v else 0.025)) & (r > 0.05) & (r < 0.75)
    img[crack] *= 0.45
    if not v:
        saw = 0.5 + 0.5 * np.sin((np.hypot(x - 1.4, y + 0.3) * 60))
        img *= (0.95 + 0.07 * saw[..., None])
        img *= (0.93 + 0.1 * _noise(n, 20, 32, 2)[..., None])
    else:
        img = _quant(img, [(70, 50, 34), (140, 96, 58), (168, 122, 76), (196, 152, 98), (214, 182, 132), (228, 200, 152)])
    return img


def hide_pad(n, v):
    """Deer-hide pad: tan hair with direction streaks, darker belly edge band, whip-stitched edge (u/v borders)."""
    y, x = np.mgrid[0:n, 0:n] / n
    hair = _noise(n, 5, 41, 4)
    streak = _noise(n, 30, 42, 1)
    img = _lerp(_rgb((104, 64, 38)), _rgb((150, 98, 60)), hair)
    img *= (0.9 + 0.15 * streak[..., None])
    edge = np.minimum(np.minimum(x, 1 - x), np.minimum(y, 1 - y))
    img[edge < (1.5 / 16)] *= 0.72
    stitch = (edge < (1.0 / 16)) & (((x + y) * (8 if v else 16)) % 1 < 0.5)
    img[stitch] = _rgb((226, 210, 178))
    if v:
        return _quant(img, [(74, 46, 28), (98, 62, 38), (122, 80, 50), (146, 96, 60), (226, 210, 178)])
    return img


def canvas_olive(n, v):
    """Camp-chair canvas: olive duck weave, slightly darker piping band at the edges."""
    y, x = np.mgrid[0:n, 0:n] / n
    if v:
        img = np.zeros((n, n, 3)) + _rgb((92, 98, 62))
        chk = ((np.arange(n)[:, None] + np.arange(n)[None, :]) % 2) == 0
        img[chk] = _rgb((96, 102, 65))
        mott = _noise(n, 3, 51, 2) > 0.62
        img[mott] = _rgb((84, 90, 56))
    else:
        weave = (np.sin(x * n * math.pi) * np.sin(y * n * math.pi))
        img = _lerp(_rgb((84, 90, 56)), _rgb((110, 117, 76)), 0.5 + 0.35 * weave)
        img *= (0.92 + 0.12 * _noise(n, 4, 52, 3)[..., None])
    edge = np.minimum(np.minimum(x, 1 - x), np.minimum(y, 1 - y))
    img[edge < (1.0 / 16)] *= 0.78
    return img


def steel_tube(n, v):
    """Powder-coated dark green steel with a soft highlight line (reads as a round tube on any face)."""
    y, x = np.mgrid[0:n, 0:n] / n
    base = np.zeros((n, n, 3)) + _rgb((44, 52, 44))
    band = np.exp(-((((x * 16) % 2) - 0.7) / (0.45 if not v else 0.6)) ** 2)
    img = base * (0.85 + 0.5 * band[..., None])
    if v:
        return _quant(img, [(34, 40, 34), (46, 54, 46), (64, 74, 63), (84, 96, 82)])
    return img * (0.95 + 0.08 * _noise(n, 16, 61, 2)[..., None])


def camo_pad(n, v):
    """Padded blind-chair cushion: bark-and-leaf camo (the Timber pattern's colours) with quilting seams every 4 units."""
    y, x = np.mgrid[0:n, 0:n] / n
    a = _noise(n, 3, 71, 3)
    b = _noise(n, 5, 72, 3)
    img = np.zeros((n, n, 3)) + _rgb((92, 84, 64))
    img[a > 0.55] = _rgb((62, 52, 38))
    img[(b > 0.6) & (a <= 0.55)] = _rgb((112, 118, 78))
    img[(b < 0.28)] = _rgb((136, 122, 92))
    leaf = (_noise(n, 9, 73, 2) > 0.72) & (a > 0.4)
    img[leaf] = _rgb((70, 84, 52))
    seam = (((x * 16) % 4) < (0.6 if not v else 1.0)) | (((y * 16) % 4) < (0.6 if not v else 1.0))
    img[seam] *= 0.7
    if not v:
        puff = np.sin(((x * 16) % 4) / 4 * math.pi) * np.sin(((y * 16) % 4) / 4 * math.pi)
        img *= (0.86 + 0.18 * puff[..., None])
        img *= (0.94 + 0.1 * _noise(n, 24, 74, 2)[..., None])
    return img


def rubber(n, v):
    """Black rubber / moulded plastic (feet, swivel bearing, caps)."""
    img = np.zeros((n, n, 3)) + _rgb((34, 34, 36))
    img *= (0.9 + 0.2 * _noise(n, 6, 81, 2)[..., None])
    if v:
        return _quant(img, [(26, 26, 28), (36, 36, 38), (48, 48, 50)])
    return img


def bolt_iron(n, v):
    """Dark forged iron (bench brackets, bolts)."""
    img = np.zeros((n, n, 3)) + _rgb((58, 56, 54))
    img *= (0.85 + 0.3 * _noise(n, 5, 91, 3)[..., None])
    if v:
        return _quant(img, [(42, 41, 40), (56, 54, 52), (72, 70, 66), (92, 88, 82)])
    return img


MATERIALS = {
    'weathered_pine': weathered_pine, 'weathered_post': weathered_post, 'stump_bark': stump_bark, 'stump_top': stump_top, 'hide_pad': hide_pad,
    'canvas_olive': canvas_olive, 'steel_tube': steel_tube, 'camo_pad': camo_pad, 'rubber': rubber, 'bolt_iron': bolt_iron,
}


def write_all(repo):
    van = os.path.join(repo, 'patch/assets/frontierhunts/textures/block/seat')
    ult = os.path.join(repo, 'patch/resourcepacks/realistic_world/assets/frontierhunts/textures/block/seat')
    for name, fn in MATERIALS.items():
        _save(fn(16, True), os.path.join(van, name + '.png'))
        _save(fn(64, False), os.path.join(ult, name + '.png'))
    return list(MATERIALS)


if __name__ == '__main__':
    import sys
    print(write_all(sys.argv[1] if len(sys.argv) > 1 else '.'))
