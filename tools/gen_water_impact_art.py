#!/usr/bin/env python3
"""[guns3] Procedural (original) particle art for bullet/shot water impacts.

Writes patch/assets/frontierhunts/textures/particle/water_{drop,foam,mist,ring}_*.png and the particle definitions
patch/assets/frontierhunts/particles/water_{spray,foam,mist,ring}.json.

  drop  16x16: a bright bead with a hard white highlight and a cool blue rim (drawn stretched along its velocity)
  foam  32x32: a lumpy white-water clump, crisp edges, soft blue-grey shading in the folds
  mist  32x32: a soft spray cloud
  ring  64x64: a foam ring for the surface (drawn flat on the water, growing)
usage: python3 tools/gen_water_impact_art.py [repo root]
"""
import json, math, os, sys
import numpy as np
from PIL import Image

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))
TEX = os.path.join(R, 'patch/assets/frontierhunts/textures/particle')
PART = os.path.join(R, 'patch/assets/frontierhunts/particles')
os.makedirs(TEX, exist_ok=True)
os.makedirs(PART, exist_ok=True)


def noise(n, seed, octaves=4):
    rng = np.random.default_rng(seed)
    out = np.zeros((n, n))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        g = 2 ** (o + 2)
        grid = rng.random((g + 1, g + 1))
        xs = np.linspace(0, g, n, endpoint=False)
        x0 = xs.astype(int)
        t = xs - x0
        t = t * t * (3 - 2 * t)
        a = grid[np.ix_(x0, x0)]
        b = grid[np.ix_(x0, x0 + 1)]
        c = grid[np.ix_(x0 + 1, x0)]
        d = grid[np.ix_(x0 + 1, x0 + 1)]
        ty, tx = t[:, None], t[None, :]
        out += amp * ((a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty)
        total += amp
        amp *= 0.5
    return out / total


def save(name, rgba):
    img = Image.fromarray(np.clip(rgba * 255 + 0.5, 0, 255).astype(np.uint8), 'RGBA')
    img.save(os.path.join(TEX, name + '.png'))


def drop(i):
    n = 16
    y, x = np.mgrid[0:n, 0:n] + 0.5
    cx, cy = 8 + (i % 2) * 0.4, 8 - (i // 2) * 0.4
    r = np.hypot((x - cx) / 1.0, (y - cy) / 1.0) / (5.2 + 0.4 * i)
    a = np.clip(1.2 - r ** 2.2 * 1.2, 0, 1)
    a = np.where(a > 0.08, np.minimum(1.0, a * 1.3), 0.0)
    base = np.stack([0.78 + 0 * r, 0.9 + 0 * r, 1.0 + 0 * r], -1)
    rim = np.clip((r - 0.55) * 2.5, 0, 1)[..., None]
    col = base * (1 - rim * 0.35) + np.array([0.45, 0.66, 0.92]) * rim * 0.35
    hl = np.exp(-(((x - cx + 1.6) ** 2 + (y - cy + 1.8) ** 2) / 2.0))[..., None]
    col = col * (1 - hl) + hl
    return np.concatenate([np.clip(col, 0, 1), a[..., None]], -1)


def foam(i):
    n = 32
    y, x = np.mgrid[0:n, 0:n] + 0.5
    nz = noise(n, 100 + i)
    nz2 = noise(n, 200 + i, 3)
    r = np.hypot(x - 16, y - 16) / 13.5
    shape = 1.0 - r + (nz - 0.5) * 0.9
    a = np.clip((shape - 0.05) * 6, 0, 1)
    a = np.round(a * 4) / 4  # stepped edge: reads crisp at Minecraft scale
    shade = np.clip(0.72 + 0.4 * nz2 + 0.25 * (16 - y) / 16, 0.55, 1.0)[..., None]
    col = np.array([0.93, 0.97, 1.0]) * shade + np.array([0.06, 0.09, 0.12]) * (1 - shade)
    holes = (nz2 < 0.28) & (r < 0.75)
    a = np.where(holes, a * 0.8, a)
    return np.concatenate([np.clip(col, 0, 1), a[..., None]], -1)


def mist(i):
    n = 32
    y, x = np.mgrid[0:n, 0:n] + 0.5
    nz = noise(n, 300 + i, 5)
    r = np.hypot(x - 16, y - 16) / 15.5
    a = np.clip((1 - r ** 1.6) * (0.35 + 0.75 * nz), 0, 1) * 0.85
    col = np.ones((n, n, 3)) * np.array([0.92, 0.96, 1.0])
    return np.concatenate([col, a[..., None]], -1)


def ring(i):
    n = 64
    y, x = np.mgrid[0:n, 0:n] + 0.5
    r = np.hypot(x - 32, y - 32) / 30.0
    ang = np.arctan2(y - 32, x - 32)
    rng = np.random.default_rng(400 + i)
    wob = sum(rng.random() * 0.05 * np.sin(k * ang + rng.random() * 6.3) for k in (3, 5, 7, 11))
    band = np.exp(-((r - 0.82 - wob) ** 2) / (2 * 0.06 ** 2))
    inner = np.exp(-((r - 0.6 - wob * 0.5) ** 2) / (2 * 0.12 ** 2)) * 0.12
    nz = noise(n, 500 + i, 4)
    a = np.clip((band + inner) * (0.55 + 0.7 * nz), 0, 1)
    a = np.round(a * 6) / 6
    col = np.ones((n, n, 3)) * np.array([0.95, 0.98, 1.0])
    return np.concatenate([col, a[..., None]], -1)


defs = {'water_spray': ('water_drop', drop, 4), 'water_foam': ('water_foam', foam, 4),
        'water_mist': ('water_mist', mist, 4), 'water_ring': ('water_ring', ring, 2)}
for particle, (tex, fn, count) in defs.items():
    names = []
    for i in range(count):
        save(f'{tex}_{i}', fn(i))
        names.append(f'frontierhunts:{tex}_{i}')
    json.dump({'textures': names}, open(os.path.join(PART, particle + '.json'), 'w'), indent=2)
    print(particle, names)
