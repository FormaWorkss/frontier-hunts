"""[benchart] Procedural solid materials for the benches (all evaluated in model-pixel space)."""
import math

import numpy as np

from benchlib import Mat
from noise import fbm, vnoise, smoothstep, hexrgb, ramp

# ------------------------------------------------------------------------------------------------ timber
PAL = {
    # warm frontier oak: honey to tobacco
    'oak': [(0.0, '#38241a'), (0.28, '#654228'), (0.55, '#93663c'), (0.8, '#b98852'), (1.0, '#d2a66e')],
    # dark figured walnut
    'walnut': [(0.0, '#1a0e08'), (0.3, '#36200f'), (0.58, '#58351c'), (0.82, '#7a4f2c'), (1.0, '#956640')],
    # pale honey pine (reloading)
    'pine': [(0.0, '#4e3218'), (0.3, '#85592e'), (0.6, '#b4834a'), (0.85, '#cda065'), (1.0, '#dfbd86')],
    # older, greyed oak for the frontier backboard
    'oldoak': [(0.0, '#2f2218'), (0.3, '#54402c'), (0.6, '#7d6446'), (0.85, '#9c8160'), (1.0, '#b49a77')],
    'handle': [(0.0, '#2c160a'), (0.4, '#5e3416'), (0.75, '#8a5226'), (1.0, '#b07038')],
    'ash': [(0.0, '#6b5032'), (0.35, '#a0835a'), (0.7, '#c8ad80'), (1.0, '#e2cda2')],
}


def _rng(e, k=0):
    return np.random.default_rng((e.seed * 31 + k) % (2 ** 32))


def wood(pal='oak', ring=2.4, warp=1.0, tone=0.08, pores=0.12, contrast=1.0, wear_top=None, bias=0.0):
    stops = PAL[pal]

    def fn(c):
        P, e = c['P'], c['el']
        g = e.grain
        o = [i for i in range(3) if i != g]
        rng = _rng(e)
        lo, hi = e.obounds
        mid = (lo + hi) / 2
        th = rng.random() * 2 * math.pi
        R = 10 + rng.random() * 26
        p0 = mid[o[0]] + R * math.cos(th)
        q0 = mid[o[1]] + R * math.sin(th)
        a = P[:, g] + rng.random() * 100
        p, q = P[:, o[0]], P[:, o[1]]
        sd = e.seed % 991
        wn = fbm(a * 0.045, p * 0.18, q * 0.18, seed=sd, octaves=3) - 0.5
        wn2 = fbm(a * 0.15, p * 0.6, q * 0.6, seed=sd + 7, octaves=2) - 0.5
        r = np.hypot(p - p0, q - q0) + wn * 6 * warp + wn2 * 1.2 + 0.035 * (a - 50)
        ringf = (r / ring) % 1.0
        late = smoothstep(0.55, 0.86, ringf) * (1 - smoothstep(0.9, 1.0, ringf))
        streak = fbm(a * 0.03, p * 1.3, q * 1.3, seed=sd + 3, octaves=3)
        pore = vnoise(a * 0.55, p * 4.5, q * 4.5, seed=sd + 11)
        pore = smoothstep(0.72, 0.9, pore)
        t0 = (rng.random() - 0.5) * 2 * tone + bias
        t = 0.62 + t0 + (streak - 0.5) * 0.55 * contrast - late * 0.38 * contrast - pore * pores
        col = ramp(t, stops)
        if wear_top is not None and c['face'] == 'up':
            col = wear_top(c, col)
        return col
    return fn


def end_grain_dark(fn, k=0.72):
    """end-grain faces of boards are darker (open pores)"""
    def f(c):
        col = fn(c)
        if np.abs(c['n'][c['el'].grain]) > 0.5:
            col = col * k
        return col
    return f


def scratches(seed, region, n=70, dark=0.72, light=1.12, stains=()):
    """wear for up-faces of work tops: knife scratches, gouges, dents and ring/oil stains.  region: (x0,z0,x1,z1)"""
    rng = np.random.default_rng(seed)
    x0, z0, x1, z1 = region
    segs = []
    for i in range(n):
        cx, cz = rng.uniform(x0, x1), rng.uniform(z0, z1)
        ang = rng.normal(0, 0.35) + (0 if rng.random() < 0.7 else rng.uniform(0, math.pi))
        ln = rng.uniform(0.8, 5.5)
        segs.append((cx, cz, math.cos(ang) * ln, math.sin(ang) * ln, rng.uniform(0.05, 0.14), rng.random() < 0.65))
    dents = [(rng.uniform(x0, x1), rng.uniform(z0, z1), rng.uniform(0.25, 0.6)) for _ in range(14)]

    def wt(c, col):
        W = c['W']
        x, z = W[:, 0], W[:, 2]
        m = np.ones(len(x))
        for cx, cz, dx, dz, w, dk in segs:
            L2 = dx * dx + dz * dz
            tt = np.clip(((x - cx) * dx + (z - cz) * dz) / L2, 0, 1)
            d = np.hypot(x - cx - tt * dx, z - cz - tt * dz)
            hit = smoothstep(w * 1.6, w * 0.4, d)
            m *= 1 + hit * ((dark - 1) if dk else (light - 1))
        for cx, cz, r in dents:
            d = np.hypot(x - cx, z - cz)
            m *= 1 - 0.18 * smoothstep(r, r * 0.4, d)
        for cx, cz, r, k in stains:
            d = np.hypot(x - cx, z - cz)
            ringm = np.exp(-((d - r) / 0.28) ** 2) * 0.35 + smoothstep(r, r * 0.8, d) * 0.12
            m *= 1 - ringm * k
        # broad oil / handling darkening, lighter dust toward the back
        blot = fbm(x * 0.12, z * 0.12, 3.3, seed=seed % 997, octaves=3)
        m *= 0.9 + 0.2 * blot
        return col * m[:, None]
    return wt


# ------------------------------------------------------------------------------------------------ metals & others
def mottled(base, var=0.08, scale=1.2, seed=5, rust=None, rust_amt=0.0, streak_axis=None, streak=0.0):
    b = hexrgb(base)
    rc = hexrgb(rust) if rust else None

    def fn(c):
        P, e = c['P'], c['el']
        sd = (e.seed + seed) % 997
        n = fbm(P[:, 0] * scale, P[:, 1] * scale, P[:, 2] * scale, seed=sd, octaves=3)
        col = b[None, :] * (1 + (n - 0.5) * 2 * var)[:, None]
        if streak:
            g = e.grain if streak_axis is None else streak_axis
            o = [i for i in range(3) if i != g]
            s = vnoise(P[:, g] * 0.2, P[:, o[0]] * 7, P[:, o[1]] * 7, seed=sd + 1)
            col *= (1 + (s - 0.5) * streak)[:, None]
        if rc is not None and rust_amt:
            r = fbm(P[:, 0] * 0.8, P[:, 1] * 0.8, P[:, 2] * 0.8, seed=sd + 9, octaves=3)
            rm = smoothstep(1 - rust_amt, 1 - rust_amt + 0.12, r)
            col = col * (1 - rm[:, None]) + rc[None, :] * rm[:, None] * (0.8 + 0.4 * n[:, None])
        return col
    return fn


def gradient_v(base_fn, top=1.12, bottom=0.88):
    """vertical sheen on side faces: lighter toward the top of the element"""
    def fn(c):
        col = base_fn(c)
        if c['face'] in ('up', 'down'):
            return col * (top if c['face'] == 'up' else bottom)
        e = c['el']
        h = max(1e-3, e.to[1] - e.frm[1])
        k = (c['P'][:, 1] - e.frm[1]) / h
        return col * (bottom + (top - bottom) * k)[:, None]
    return fn


def chrome(c):
    P, e = c['P'], c['el']
    face = c['face']
    if face == 'up':
        base = np.full(len(P), 0.86)
    elif face == 'down':
        base = np.full(len(P), 0.28)
    else:
        # env-reflection: sky (bright) above a dark horizon band, warm floor below
        g = e.grain
        if g == 1:
            # vertical bar: across-face coordinate gives the banding
            k = c['s'] / max(c['fs'], 1e-3)
            base = 0.32 + 0.6 * np.exp(-((k - 0.3) / 0.16) ** 2) + 0.18 * np.exp(-((k - 0.82) / 0.1) ** 2)
        else:
            k = c['t'] / max(c['ft'], 1e-3)
            base = 0.9 - 0.62 * smoothstep(0.25, 0.55, k) + 0.25 * smoothstep(0.7, 1.0, k)
    n = vnoise(P[:, 0] * 3, P[:, 1] * 3, P[:, 2] * 3, seed=e.seed % 97)
    v = base * (0.95 + 0.1 * n)
    return np.stack([v * 0.97, v * 0.99, v * 1.03], 1).clip(0, 1)


def brass(c):
    P, e = c['P'], c['el']
    n = fbm(P[:, 0] * 1.4, P[:, 1] * 1.4, P[:, 2] * 1.4, seed=e.seed % 97, octaves=3)
    t = 0.55 + (n - 0.5) * 0.5
    if c['face'] == 'up':
        t = t + 0.15
    if c['face'] == 'down':
        t = t - 0.25
    return ramp(t, [(0, '#3a2a10'), (0.35, '#6f5222'), (0.62, '#9c7a38'), (0.85, '#c4a258'), (1, '#e0c88a')])


def felt(base='#2f5a3a'):
    b = hexrgb(base)

    def fn(c):
        P, e = c['P'], c['el']
        n1 = vnoise(P[:, 0] * 6, P[:, 1] * 6, P[:, 2] * 6, seed=e.seed % 97)
        n2 = fbm(P[:, 0] * 0.25, P[:, 1] * 0.25, P[:, 2] * 0.25, seed=e.seed % 97 + 4, octaves=3)
        col = b[None, :] * (0.9 + 0.12 * n1 + 0.18 * (n2 - 0.5))[:, None]
        return col
    return fn


def leather(base='#7a4a26', var=0.14, seed=0):
    b = hexrgb(base)

    def fn(c):
        P, e = c['P'], c['el']
        sd = (e.seed + seed) % 997
        n1 = vnoise(P[:, 0] * 5, P[:, 1] * 5, P[:, 2] * 5, seed=sd)
        n2 = fbm(P[:, 0] * 0.4, P[:, 1] * 0.4, P[:, 2] * 0.4, seed=sd + 5, octaves=3)
        return b[None, :] * (0.92 + 0.1 * n1 + (n2 - 0.5) * 2 * var)[:, None]
    return fn


def rope(base='#b39a6a', twist=1.4):
    b = hexrgb(base)

    def fn(c):
        P, e = c['P'], c['el']
        g = e.grain
        o = [i for i in range(3) if i != g]
        ph = P[:, g] * twist + (P[:, o[0]] + P[:, o[1]]) * 1.3
        st = 0.5 + 0.5 * np.sin(ph * math.pi)
        n = vnoise(P[:, 0] * 4, P[:, 1] * 4, P[:, 2] * 4, seed=e.seed % 97)
        return b[None, :] * (0.7 + 0.35 * st + 0.08 * n)[:, None]
    return fn


def canvas(base='#a39a78', weave=2.0):
    b = hexrgb(base)

    def fn(c):
        P, e = c['P'], c['el']
        s, t = c['s'], c['t']
        w = 0.5 + 0.25 * np.sin(s * weave * math.pi) * np.sin(t * weave * math.pi)
        n = fbm(P[:, 0] * 0.5, P[:, 1] * 0.5, P[:, 2] * 0.5, seed=e.seed % 97, octaves=3)
        return b[None, :] * (0.85 + 0.12 * w + (n - 0.5) * 0.25)[:, None]
    return fn


def flat(base, var=0.05):
    b = hexrgb(base)

    def fn(c):
        P, e = c['P'], c['el']
        n = vnoise(P[:, 0] * 2.5, P[:, 1] * 2.5, P[:, 2] * 2.5, seed=e.seed % 97)
        return b[None, :] * (1 + (n - 0.5) * 2 * var)[:, None]
    return fn


def glass(tint='#9fb8b8', fill=None, fill_level=0.0, fill_col='#2a2826'):
    """opaque 'glass' look: cool tinted body with a vertical specular streak; optional contents filled up to
    fill_level (fraction of element height) in fill_col (granular)"""
    tc = hexrgb(tint)
    fc = hexrgb(fill_col)

    def fn(c):
        P, e = c['P'], c['el']
        h = max(1e-3, e.to[1] - e.frm[1])
        k = (P[:, 1] - e.frm[1]) / h
        col = np.tile(tc, (len(P), 1)) * 0.85
        if c['face'] not in ('up', 'down'):
            ks = c['s'] / max(c['fs'], 1e-3)
            streak = np.exp(-((ks - 0.25) / 0.08) ** 2) * 0.55 + np.exp(-((ks - 0.7) / 0.05) ** 2) * 0.2
            col = col + streak[:, None]
            if fill_level > 0:
                g = vnoise(P[:, 0] * 6, P[:, 1] * 6, P[:, 2] * 6, seed=e.seed % 97)
                fm = k < fill_level
                inner = fc[None, :] * (0.8 + 0.4 * g[:, None]) * 0.85 + tc[None, :] * 0.12
                col[fm] = inner[fm] + streak[fm, None] * 0.5
        elif c['face'] == 'up' and fill_level >= 0.99:
            g = vnoise(P[:, 0] * 6, P[:, 1] * 6, P[:, 2] * 6, seed=e.seed % 97)
            col = fc[None, :] * (0.8 + 0.4 * g[:, None])
        return col.clip(0, 1)
    return fn


# ------------------------------------------------------------------------------------------------ material table
def M(fn, **kw):
    return Mat(fn, **kw)


IRON = M(mottled('#34322f', var=0.12, scale=1.5, rust='#6b3a1c', rust_amt=0.18), edge=0.55, seam=0.2)
STEEL = M(mottled('#8d939a', var=0.08, scale=2.0, streak=0.25), edge=0.35, seam=0.2)
DARKSTEEL = M(mottled('#3c4047', var=0.08, scale=2.0, streak=0.25), edge=0.6, seam=0.2)
BLUED = M(mottled('#1e232c', var=0.1, scale=2.0, streak=0.3), edge=0.9, seam=0.15, gloss=0.25)
BRASS = M(brass, edge=0.45, seam=0.15, gloss=0.25)
CHROME = M(chrome, edge=0.15, seam=0.1, gloss=0.6)
BLACK = M(mottled('#1c1b1a', var=0.08), edge=0.6, seam=0.1)
