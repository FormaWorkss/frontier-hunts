"""[wingshot] Procedural art for the game birds (all original, generated here):

  * wing atlases for the Ultra feathered wings (textures/entity/wildlife/wings/{duck,grouse}.png): every feather kind
    of WingGeometry.Part drawn as a real feather (calamus, rachis, asymmetric vanes, barbs, species pattern), top
    face on the left half, underside on the right half;
  * particle sprites (textures/particle/wingshot_*.png): species contour and flight feathers, down tufts, blood
    droplet / splats / mist;
  * the Vanilla box-model textures extended to 64x64 with the wing hands (primaries);
  * the wing-shot vignette.

usage: python3 tools/wingshot/art.py [repo root]
"""
import os, sys, math, io, zipfile, json
import numpy as np
from PIL import Image, ImageFilter

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
PATCH = os.path.join(REPO, 'patch', 'assets', 'frontierhunts')
JAR = '/home/claude/fh/merged62g8.jar'
SS = 4  # supersampling


def rng(seed):
    return np.random.default_rng(seed)


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], np.float32)


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(h, w, scale, seed):
    r = rng(seed)
    gh, gw = int(h / scale) + 3, int(w / scale) + 3
    g = r.random((gh, gw)).astype(np.float32)
    ys = np.arange(h) / scale
    xs = np.arange(w) / scale
    y0 = ys.astype(int); x0 = xs.astype(int)
    fy = smoothstep(0, 1, ys - y0)[:, None]; fx = smoothstep(0, 1, xs - x0)[None, :]
    a = g[y0][:, x0]; b = g[y0][:, x0 + 1]; c = g[y0 + 1][:, x0]; d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(h, w, scale, seed, oct=4):
    s = np.zeros((h, w), np.float32); amp = 1.0; tot = 0
    for o in range(oct):
        s += amp * value_noise(h, w, max(1.0, scale / (2 ** o)), seed + o * 17)
        tot += amp; amp *= 0.5
    return s / tot


# =====================================================================================================================
# a feather
# =====================================================================================================================

def feather(W, H, kind, pattern, seed, rachis=0.33, tip='pointed', curve=0.0, calamus=0.06):
    """RGBA float array (H, W): the feather lies along x (base at x=0, tip at x=W), leading edge at y=0.

    pattern(t, s, side, ctx) -> rgb (H*W*3) where t = 0..1 along, s = signed distance across from the rachis
    (normalised to the local vane half-width: -1 leading edge, +1 trailing edge).
    """
    w, h = W * SS, H * SS
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    t = (xx + 0.5) / w
    v = (yy + 0.5) / h
    # rachis line across the feather (slight curve)
    rc = rachis + curve * (t - 0.5) ** 2 * 0.6
    # vane half-widths (leading narrow, trailing wide) along t
    if tip == 'pointed':
        # inner (trailing) vane rounds off into a narrow tip; outer (leading) vane stays narrow and tapers last
        prof = smoothstep(calamus, calamus + 0.12, t) * np.sqrt(np.clip(1 - smoothstep(0.5, 1.0, t) ** 1.8, 0, 1))
        lead_p = smoothstep(calamus, calamus + 0.12, t) * np.sqrt(np.clip(1 - smoothstep(0.7, 1.0, t) ** 2.2, 0, 1))
    elif tip == 'round':
        prof = smoothstep(calamus, calamus + 0.1, t) * np.sqrt(np.clip(1 - smoothstep(0.74, 1.0, t) ** 2, 0, 1))
        lead_p = prof
    else:  # blunt (secondaries): square-ish end with a small emargination
        prof = smoothstep(calamus, calamus + 0.1, t) * np.sqrt(np.clip(1 - smoothstep(0.86, 1.0, t) ** 3, 0, 1))
        lead_p = prof
    lead = lead_p * rc * 0.96
    trail = prof * (1 - rc) * 0.96
    d = v - rc
    s = np.where(d < 0, d / (lead + 1e-6), d / (trail + 1e-6))
    inside = (np.abs(s) <= 1.0) & (prof > 0.02)
    # ragged edge: barbs separate a little at the trailing edge
    r = rng(seed)
    rag = fbm(h, w, 6 * SS, seed + 3, 2)
    inside &= ~((s > 0.82) & (rag > 0.72)) & ~((s < -0.9) & (rag < 0.2))
    ctx = dict(rng=r, seed=seed, h=h, w=w, rag=rag)
    col = pattern(t, s, ctx)
    # barbs: fine diagonal lines raking toward the tip
    barb_coord = (np.abs(d) * h * 1.0 - xx * 0.55) / SS
    barbs = 0.5 + 0.5 * np.sin(barb_coord * 2.2 + rag * 3)
    shade = 0.9 + 0.12 * barbs
    # vane shading: a little darker toward the edges, lighter near the rachis
    shade *= 1.0 - 0.12 * np.abs(s) ** 2
    col = col * shade[..., None]
    # rachis (shaft): pale, thin, tapering
    shaft_w = (0.035 * (1 - t) + 0.008) * 1.0
    shaft = np.abs(d) < shaft_w * 0.5
    shaft_col = ctx.get('shaft', np.array([0.78, 0.74, 0.66], np.float32))
    col = np.where(shaft[..., None], col * 0.35 + shaft_col * 0.65, col)
    # calamus (bare quill) at the base
    quill = (t < calamus + 0.02) & (np.abs(d) < 0.06 * (1 - t))
    col = np.where(quill[..., None], shaft_col * 0.9, col)
    alpha = (inside | quill | (shaft & (prof > 0.0) & (t < 0.99))).astype(np.float32)
    img = np.concatenate([np.clip(col, 0, 1), alpha[..., None]], -1)
    # downsample (area) and keep alpha crisp for the cutout
    im = Image.fromarray((img * 255).astype(np.uint8), 'RGBA')
    # premultiply to avoid dark fringes
    arr = np.asarray(im).astype(np.float32) / 255
    pm = arr.copy(); pm[..., :3] *= pm[..., 3:4]
    small = np.asarray(Image.fromarray((pm * 255).astype(np.uint8), 'RGBA').resize((W, H), Image.BOX)).astype(np.float32) / 255
    a = small[..., 3:4]
    rgb = np.where(a > 0.01, small[..., :3] / np.maximum(a, 1e-3), 0)
    a = np.where(a > 0.42, 1.0, 0.0)
    return np.concatenate([rgb, a], -1)


def mix(a, b, f):
    return a * (1 - f[..., None]) + b * f[..., None]


def tone(base, ctx, var=0.08, scale=5):
    n = fbm(ctx['h'], ctx['w'], scale * SS, ctx['seed'] + 9, 3)
    return base[None, None, :] * (1 - var + 2 * var * n[..., None])


# ------------------------------------------------------------------------------------------------ mallard patterns

def m_primary(dark, pale):
    def f(t, s, ctx):
        c = tone(hexc(dark), ctx, 0.06)
        # inner web (trailing) paler grey-brown, outer web darker; tip darker
        c = mix(c, tone(hexc(pale), ctx, 0.05), smoothstep(0.1, 0.6, s) * 0.7)
        c = mix(c, c * 0.72, smoothstep(0.75, 1.0, t))
        ctx['shaft'] = hexc('#3a332c')
        return c
    return f


def m_primary_under(t, s, ctx):
    c = tone(hexc('#b7b6b2'), ctx, 0.05)
    c = mix(c, hexc('#7d7a76')[None, None, :] * np.ones_like(c), smoothstep(0.7, 1.0, t) * 0.8)
    ctx['shaft'] = hexc('#e6e3dd')
    return c


def m_speculum(t, s, ctx):
    # iridescent blue-violet with a black subterminal band and a white tip; grey-brown base under the coverts
    base = tone(hexc('#6a6058'), ctx, 0.05)
    irid = 0.5 + 0.5 * np.sin(t * 7 + s * 2.0 + fbm(ctx['h'], ctx['w'], 6 * SS, ctx['seed'] + 2, 2) * 3)
    blue = mix(np.broadcast_to(hexc('#1c3484'), base.shape), np.broadcast_to(hexc('#46288a'), base.shape), irid * 0.6)
    blue = mix(blue, np.broadcast_to(hexc('#4f7fd8'), base.shape), smoothstep(0.55, 0.95, irid) * smoothstep(0.2, 0.7, 1 - np.abs(s)) * 0.45)
    blue = blue * (0.85 + 0.15 * tone(np.array([1, 1, 1], np.float32), ctx, 0.5, 2))
    c = mix(base, blue, smoothstep(0.3, 0.42, t))
    c = mix(c, np.broadcast_to(hexc('#0e0e12'), base.shape), smoothstep(0.78, 0.81, t))
    c = mix(c, np.broadcast_to(hexc('#f2f1ec'), base.shape), smoothstep(0.88, 0.9, t))
    ctx['shaft'] = hexc('#2a2a33')
    return c


def m_secondary_in(t, s, ctx):
    c = tone(hexc('#5f554c'), ctx, 0.06)
    c = mix(c, np.broadcast_to(hexc('#2b3f86'), c.shape), smoothstep(0.4, 0.6, t) * 0.35 * smoothstep(0.0, 0.5, 1 - np.abs(s)))
    c = mix(c, np.broadcast_to(hexc('#ece9e2'), c.shape), smoothstep(0.9, 0.93, t) * 0.8)
    ctx['shaft'] = hexc('#3a332c')
    return c


def m_secondary_under(t, s, ctx):
    c = tone(hexc('#aeaca8'), ctx, 0.05)
    c = mix(c, np.broadcast_to(hexc('#f0eee9'), c.shape), smoothstep(0.88, 0.92, t))
    ctx['shaft'] = hexc('#e6e3dd')
    return c


def m_greater(t, s, ctx):
    # grey-brown with the white bar and black tip in front of the speculum
    c = tone(hexc('#7a6e62'), ctx, 0.06)
    c = mix(c, np.broadcast_to(hexc('#f4f2ed'), c.shape), smoothstep(0.62, 0.66, t))
    c = mix(c, np.broadcast_to(hexc('#121214'), c.shape), smoothstep(0.84, 0.87, t))
    ctx['shaft'] = hexc('#4a4038')
    return c


def m_plain(colour, fringe=None, center=None):
    def f(t, s, ctx):
        c = tone(hexc(colour), ctx, 0.07)
        if center is not None:
            c = mix(c, np.broadcast_to(hexc(center), c.shape), (1 - smoothstep(0.15, 0.55, np.abs(s))) * smoothstep(0.1, 0.4, t) * 0.75)
        if fringe is not None:
            c = mix(c, np.broadcast_to(hexc(fringe), c.shape), smoothstep(0.72, 0.95, np.abs(s)) * 0.8 + smoothstep(0.86, 0.98, t) * 0.6)
        ctx['shaft'] = hexc('#4d443b')
        return c
    return f


def m_under_covert(t, s, ctx):
    c = tone(hexc('#f3f2ee'), ctx, 0.03)
    c = mix(c, np.broadcast_to(hexc('#c9c7c2'), c.shape), smoothstep(0.0, 0.4, 1 - t) * 0.3)
    ctx['shaft'] = hexc('#ffffff')
    return c


# ------------------------------------------------------------------------------------------------ grouse patterns

def g_primary(t, s, ctx):
    c = tone(hexc('#5c4a3a'), ctx, 0.06)
    # soft buff notches along the outer (leading) web, the classic barred edge of a grouse primary
    phase = (t * 9.0) % 1.0
    oval = ((phase - 0.5) / 0.28) ** 2 + ((s + 1.0) / 0.75) ** 2
    spot = (1 - smoothstep(0.7, 1.0, oval)) * smoothstep(0.12, 0.2, t) * (1 - smoothstep(0.78, 0.86, t))
    c = mix(c, np.broadcast_to(hexc('#c9a878'), c.shape), spot * 0.9)
    # faint mottling on the inner web
    verm = fbm(ctx['h'], ctx['w'], 2.0 * SS, ctx['seed'] + 5, 2)
    c = mix(c, np.broadcast_to(hexc('#7a6450'), c.shape), smoothstep(0.6, 0.8, verm) * smoothstep(0.2, 0.6, s) * 0.5)
    c = mix(c, c * 0.8, smoothstep(0.8, 1.0, t))
    ctx['shaft'] = hexc('#3c3026')
    return c


def g_primary_under(t, s, ctx):
    c = tone(hexc('#a39d94'), ctx, 0.05)
    bars = 0.5 + 0.5 * np.sin(t * math.pi * 9.0)
    c = mix(c, np.broadcast_to(hexc('#c9c2b5'), c.shape), (bars > 0.6) * (s < 0) * 0.5)
    ctx['shaft'] = hexc('#e0d8cc')
    return c


def g_secondary(t, s, ctx):
    c = tone(hexc('#6a553f'), ctx, 0.08)
    verm = fbm(ctx['h'], ctx['w'], 2.2 * SS, ctx['seed'] + 41, 2)
    c = mix(c, np.broadcast_to(hexc('#b28f62'), c.shape), smoothstep(0.55, 0.8, verm) * 0.7)
    c = mix(c, np.broadcast_to(hexc('#d8c19a'), c.shape), smoothstep(0.88, 0.94, t) * 0.85)
    ctx['shaft'] = hexc('#3f3227')
    return c


def g_covert(base, spot='#d9c49b'):
    def f(t, s, ctx):
        c = tone(hexc(base), ctx, 0.09)
        verm = fbm(ctx['h'], ctx['w'], 2.5 * SS, ctx['seed'] + 7, 2)
        c = mix(c, np.broadcast_to(hexc('#2c231c'), c.shape), smoothstep(0.62, 0.8, verm) * 0.6)
        # pale shaft streak widening to a teardrop near the tip, black spots either side
        tear = (1 - smoothstep(0.08, 0.3 + 0.25 * smoothstep(0.5, 0.85, t), np.abs(s))) * smoothstep(0.3, 0.6, t) * (1 - smoothstep(0.9, 1.0, t))
        c = mix(c, np.broadcast_to(hexc(spot), c.shape), tear)
        blk = (smoothstep(0.35, 0.5, np.abs(s)) * (1 - smoothstep(0.55, 0.7, np.abs(s)))) * smoothstep(0.6, 0.75, t) * (1 - smoothstep(0.85, 0.95, t))
        c = mix(c, np.broadcast_to(hexc('#17120e'), c.shape), blk * 0.7)
        ctx['shaft'] = hexc('#e3d2ae')
        return c
    return f


def g_under(t, s, ctx):
    c = tone(hexc('#d9d1c3'), ctx, 0.04)
    ctx['shaft'] = hexc('#f2ece0')
    return c


# =====================================================================================================================
# atlases
# =====================================================================================================================

PARTS = {  # name: (x, y, w, h)  -- must match WingGeometry.Part
    'PRIMARY_OUT': (0, 0, 128, 16), 'PRIMARY_MID': (0, 16, 128, 16), 'PRIMARY_IN': (0, 32, 128, 16),
    'SECONDARY': (0, 48, 96, 24), 'SECONDARY_IN': (0, 72, 96, 24), 'TERTIAL': (0, 96, 96, 24),
    'GREATER': (0, 120, 64, 20), 'MEDIAN': (0, 144, 48, 16), 'MARGINAL': (0, 164, 32, 14),
    'SCAPULAR': (0, 180, 96, 28), 'PRIMARY_COVERT': (0, 210, 64, 16), 'ALULA': (0, 228, 40, 12),
}


def atlas(species):
    A = np.zeros((256, 256, 4), np.float32)
    if species == 'duck':
        spec = {
            'PRIMARY_OUT': (m_primary('#3f3a35', '#6e675f'), 'pointed', 0.27, m_primary_under),
            'PRIMARY_MID': (m_primary('#4a443d', '#7a7268'), 'pointed', 0.3, m_primary_under),
            'PRIMARY_IN': (m_primary('#544c44', '#80786d'), 'round', 0.34, m_primary_under),
            'SECONDARY': (m_speculum, 'blunt', 0.38, m_secondary_under),
            'SECONDARY_IN': (m_secondary_in, 'blunt', 0.4, m_secondary_under),
            'TERTIAL': (m_plain('#6b6156', fringe='#a99a86', center='#3e3731'), 'round', 0.42, m_secondary_under),
            'GREATER': (m_greater, 'round', 0.42, m_under_covert),
            'MEDIAN': (m_plain('#6f6559', fringe='#8a7e70'), 'round', 0.45, m_under_covert),
            'MARGINAL': (m_plain('#5e554b', fringe='#7b7062'), 'round', 0.45, m_under_covert),
            'SCAPULAR': (m_plain('#4a3d31', fringe='#a7865f', center='#2f261f'), 'round', 0.45, m_under_covert),
            'PRIMARY_COVERT': (m_plain('#5a524a', fringe='#706759'), 'round', 0.38, m_under_covert),
            'ALULA': (m_plain('#45403a'), 'pointed', 0.35, m_under_covert),
        }
    else:
        spec = {
            'PRIMARY_OUT': (g_primary, 'round', 0.3, g_primary_under),
            'PRIMARY_MID': (g_primary, 'round', 0.32, g_primary_under),
            'PRIMARY_IN': (g_primary, 'round', 0.36, g_primary_under),
            'SECONDARY': (g_secondary, 'blunt', 0.4, g_primary_under),
            'SECONDARY_IN': (g_secondary, 'blunt', 0.42, g_primary_under),
            'TERTIAL': (g_covert('#6e5a44'), 'round', 0.45, g_under),
            'GREATER': (g_covert('#76604a'), 'round', 0.45, g_under),
            'MEDIAN': (g_covert('#7d5f43', '#e0c89a'), 'round', 0.45, g_under),
            'MARGINAL': (g_covert('#6b5139'), 'round', 0.45, g_under),
            'SCAPULAR': (g_covert('#6a5440', '#ead9b6'), 'round', 0.45, g_under),
            'PRIMARY_COVERT': (g_covert('#5f4c3a'), 'round', 0.4, g_under),
            'ALULA': (g_primary, 'round', 0.35, g_under),
        }
    seed = 100 if species == 'duck' else 500
    for i, (name, (x, y, w, h)) in enumerate(PARTS.items()):
        top, tip, rach, under = spec[name]
        curve = 0.25 if name.startswith('PRIMARY_') and name != 'PRIMARY_COVERT' else 0.0
        if species == 'grouse' and name.startswith('PRIMARY'):
            curve = 0.45
        A[y:y + h, x:x + w] = feather(w, h, name, top, seed + i * 13, rachis=rach, tip=tip, curve=curve)
        A[y:y + h, 128 + x:128 + x + w] = feather(w, h, name, under, seed + i * 13, rachis=rach, tip=tip, curve=curve)
    return A


def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), 'RGBA').save(path, optimize=True)


# =====================================================================================================================
# particle sprites
# =====================================================================================================================

def contour(colour_fn, seed, W=32, H=32):
    """a small body (contour) feather, curved, fluffy base (afterfeather) - drawn diagonally in a square sprite"""
    w, h = W * SS, H * SS
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    # local coords: along the diagonal (base lower-left -> tip upper-right)
    u = ((xx - w * 0.15) + (h * 0.85 - yy)) / (w * 0.7 * 1.414)
    v = ((xx - w * 0.15) - (h * 0.85 - yy)) / (w * 0.7 * 1.414)
    v = v - 0.12 * (u - 0.5) ** 2
    t = u
    half = 0.24 * np.sqrt(np.clip(1 - ((t - 0.55) / 0.48) ** 2, 0, 1))
    s = v / (half + 1e-6)
    fluff = fbm(h, w, 3 * SS, seed, 3)
    inside = (np.abs(s) < 1) & (t > 0.06) & (t < 1.02)
    downy = (t < 0.35) & (np.abs(s) < 1.25 + fluff * 0.6) & (t > 0.0)
    ctx = dict(seed=seed, h=h, w=w)
    col = colour_fn(t, s, ctx)
    downcol = col * 0.6 + np.array([0.82, 0.8, 0.78], np.float32) * 0.4
    col = np.where((downy & ~inside)[..., None] | ((t < 0.3) & inside)[..., None], mix(col, downcol, (1 - smoothstep(0.1, 0.32, t))), col)
    shaft = (np.abs(v) < 0.012) & (t > 0.02) & (t < 0.9)
    col = np.where(shaft[..., None], col * 0.5 + 0.45, col)
    barbs = 0.5 + 0.5 * np.sin((np.abs(v) * 90 - u * 40))
    col = col * (0.9 + 0.12 * barbs[..., None])
    a = (inside | downy & (fluff > 0.35) | shaft).astype(np.float32)
    return finish(np.concatenate([np.clip(col, 0, 1), a[..., None]], -1), W, H)


def finish(img, W, H, soft=False):
    img = np.nan_to_num(img)
    pm = img.copy(); pm[..., :3] *= pm[..., 3:4]
    small = np.asarray(Image.fromarray((pm * 255).astype(np.uint8), 'RGBA').resize((W, H), Image.BOX)).astype(np.float32) / 255
    a = small[..., 3:4]
    rgb = np.where(a > 0.01, small[..., :3] / np.maximum(a, 1e-3), 0)
    if not soft:
        a = np.where(a > 0.4, 1.0, 0.0)
    return np.concatenate([rgb, a], -1)


def c_plain(hx, var=0.1, bars=None, tipc=None, spot=None):
    def f(t, s, ctx):
        c = tone(hexc(hx), ctx, var, 3)
        if bars is not None:
            b = (np.sin(t * math.pi * bars[1]) > 0.3).astype(np.float32)
            c = mix(c, np.broadcast_to(hexc(bars[0]), c.shape), b * smoothstep(0.2, 0.35, t) * 0.85)
        if tipc is not None:
            c = mix(c, np.broadcast_to(hexc(tipc), c.shape), smoothstep(0.78, 0.88, t))
        if spot is not None:
            sp = (1 - smoothstep(0.2, 0.45, np.abs(s))) * smoothstep(0.55, 0.65, t) * (1 - smoothstep(0.85, 0.95, t))
            c = mix(c, np.broadcast_to(hexc(spot), c.shape), sp)
        return c
    return f


def c_vermic(base, line):
    def f(t, s, ctx):
        c = tone(hexc(base), ctx, 0.05, 3)
        # fine wavy vermiculation (drake flank / grouse rump)
        verm = 0.5 + 0.5 * np.sin(t * 70 + np.sin(s * 9) * 2.0)
        return mix(c, np.broadcast_to(hexc(line), c.shape), (verm > 0.78) * 0.7)
    return f


def c_iridescent(t, s, ctx):
    c = tone(hexc('#1d4a2c'), ctx, 0.1, 3)
    return mix(c, np.broadcast_to(hexc('#2f7a4c'), c.shape), (0.5 + 0.5 * np.sin(t * 6 + s * 3)) * 0.6)


def down_tuft(seed, base, W=32, H=32):
    w, h = W * SS, H * SS
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    cx, cy = w / 2, h / 2
    r = np.hypot(xx - cx, yy - cy) / (w / 2)
    ang = np.arctan2(yy - cy, xx - cx)
    fl = fbm(h, w, 2.5 * SS, seed, 3)
    # wispy barbs radiating from a centre
    rays = 0.5 + 0.5 * np.sin(ang * 13 + fl * 6)
    a = np.clip((1 - r) * 1.5 - 0.15, 0, 1) * (0.55 + 0.45 * rays) * (0.6 + 0.6 * fl)
    a = np.clip(a, 0, 1)
    col = hexc(base)[None, None, :] * (0.9 + 0.15 * fl[..., None])
    return finish(np.concatenate([col, a[..., None]], -1), W, H, soft=True)


def blood_drop(seed, W=16, H=16):
    w, h = W * SS, H * SS
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.hypot(xx - w / 2, yy - h / 2) / (w / 2)
    a = (r < 0.82).astype(np.float32)
    col = np.zeros((h, w, 3), np.float32) + hexc('#5c0a0a')
    hl = np.exp(-((xx - w * 0.38) ** 2 + (yy - h * 0.36) ** 2) / (2 * (w * 0.08) ** 2))
    col = col * (0.75 + 0.5 * (1 - r)[..., None]) + hl[..., None] * 0.45
    return finish(np.concatenate([np.clip(col, 0, 1), a[..., None]], -1), W, H)


def blood_splat(seed, W=32, H=32):
    w, h = W * SS, H * SS
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = rng(seed)
    cx, cy = w / 2, h / 2
    rr = np.hypot(xx - cx, yy - cy) / (w / 2)
    ang = np.arctan2(yy - cy, xx - cx)
    edge = 0.45 + 0.12 * np.sin(ang * 5 + r.random() * 6) + 0.08 * np.sin(ang * 11 + r.random() * 6)
    n = fbm(h, w, 3 * SS, seed, 3)
    a = (rr < edge + (n - 0.5) * 0.25).astype(np.float32)
    # satellite droplets along a spray direction
    d = r.random() * 6.28
    for k in range(9):
        dist = 0.5 + r.random() * 0.42
        px = cx + math.cos(d + (r.random() - 0.5) * 0.9) * dist * w / 2
        py = cy + math.sin(d + (r.random() - 0.5) * 0.9) * dist * h / 2
        rad = (0.025 + r.random() * 0.05) * w
        a = np.maximum(a, ((xx - px) ** 2 + (yy - py) ** 2 < rad ** 2).astype(np.float32))
    dark = smoothstep(0.0, 0.8, rr)
    col = mix(np.broadcast_to(hexc('#6a0c0c'), (h, w, 3)), np.broadcast_to(hexc('#3a0606'), (h, w, 3)), dark * 0.7 + n * 0.3)
    return finish(np.concatenate([col, a[..., None]], -1), W, H)


def mist(seed, W=32, H=32):
    w, h = W * SS, H * SS
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.hypot(xx - w / 2, yy - h / 2) / (w / 2)
    n = fbm(h, w, 4 * SS, seed, 4)
    a = np.clip(np.clip(1 - r, 0, 1) ** 1.4 * (0.45 + 0.9 * n) - 0.05, 0, 1) * 0.9
    col = mix(np.broadcast_to(hexc('#8a1010'), (h, w, 3)), np.broadcast_to(hexc('#4a0606'), (h, w, 3)), n)
    return finish(np.concatenate([col, a[..., None]], -1), W, H, soft=True)


def flight_feather(fn, seed, tip, W=16, H=48, rachis=0.35):
    # long flight feather drawn vertically (base at the bottom) for the particle sheet
    f = feather(H, W, 'p', fn, seed, rachis=rachis, tip=tip, curve=0.25)  # (W, H) along x
    f = np.rot90(f, 1)  # tip up
    return f


SPRITES = []  # (name, array) in particle json order -- must match BirdFx sprite indices


def sprites():
    out = []
    # duck contour feathers (0-5)
    out.append(('duck_breast', contour(c_plain('#5a3a28', 0.12, tipc='#7a5640'), 11)))
    out.append(('duck_flank', contour(c_vermic('#8b8378', '#4f4a44'), 12)))
    out.append(('duck_belly', contour(c_plain('#d9d3c8', 0.06), 13)))
    out.append(('duck_back', contour(c_plain('#4d4036', 0.1, tipc='#8a7258'), 14)))
    out.append(('duck_head', contour(c_iridescent, 15)))
    out.append(('duck_scapular', contour(c_plain('#5e544a', 0.08, spot='#8d7d6a'), 16)))
    # duck flight feathers (6-8)
    out.append(('duck_primary', flight_feather(m_primary('#3f3a35', '#6e675f'), 21, 'pointed', rachis=0.3)))
    out.append(('duck_speculum', flight_feather(m_speculum, 22, 'blunt', rachis=0.4)))
    out.append(('duck_tertial', flight_feather(m_plain('#6b6156', fringe='#a99a86', center='#3e3731'), 23, 'round', rachis=0.42)))
    # grouse contour feathers (9-14)
    out.append(('grouse_barred', contour(c_plain('#7b6047', 0.1, bars=('#3a2c20', 5)), 31)))
    out.append(('grouse_bufftip', contour(c_plain('#6a5038', 0.1, spot='#dcc7a0'), 32)))
    out.append(('grouse_belly', contour(c_plain('#e3d8c2', 0.06, bars=('#8b6e50', 4)), 33)))
    out.append(('grouse_rufous', contour(c_plain('#8a5a36', 0.12, spot='#d8bb8c'), 34)))
    out.append(('grouse_ruff', contour(c_plain('#1f1a16', 0.06), 35)))
    out.append(('grouse_rump', contour(c_vermic('#80776a', '#3c342c'), 36)))
    # grouse flight feathers (15-17)
    out.append(('grouse_primary', flight_feather(g_primary, 41, 'round', rachis=0.32)))
    out.append(('grouse_secondary', flight_feather(g_secondary, 42, 'blunt', rachis=0.4)))
    out.append(('grouse_tail', flight_feather(c_plain('#8a6b4c', 0.08, bars=('#5a4330', 7), tipc='#1a1612'), 43, 'round', rachis=0.5)))
    # down (18-20)
    out.append(('down_0', down_tuft(51, '#f2efe9')))
    out.append(('down_1', down_tuft(52, '#dcd7cf')))
    out.append(('down_2', down_tuft(53, '#c7c0b6')))
    # blood (21-26)
    out.append(('blood_drop', blood_drop(61)))
    out.append(('blood_splat_0', blood_splat(62)))
    out.append(('blood_splat_1', blood_splat(63)))
    out.append(('blood_mist_0', mist(64)))
    out.append(('blood_mist_1', mist(65)))
    out.append(('blood_mist_2', mist(66)))
    return out


# =====================================================================================================================
# Vanilla box textures: 64x64 with the wing hands
# =====================================================================================================================

def box_texture(species):
    with zipfile.ZipFile(JAR) as z:
        src = Image.open(io.BytesIO(z.read('assets/frontierhunts/textures/entity/wildlife/%s.png' % species))).convert('RGBA')
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    img.paste(src, (0, 0))
    a = np.asarray(img).astype(np.uint8).copy()
    r = rng(7 if species == 'duck' else 8)
    # box (1 x h x d) at texOffs (u, v): side faces d x h at (u, v+d) and (u+d+1, v+d); edges 1 px
    if species == 'duck':
        d, h = 6, 6
        dark, mid, light, edge = (60, 55, 50), (88, 80, 72), (120, 110, 98), (150, 140, 126)
    else:
        d, h = 5, 5
        dark, mid, light, edge = (78, 62, 46), (104, 84, 62), (150, 124, 88), (196, 170, 128)
    for u0 in (0, 16):
        v0 = 32
        for (fx, fy) in ((u0, v0 + d), (u0 + d + 1, v0 + d)):
            for yy in range(h):
                for xx in range(d):
                    # columns = chord (feathers), rows = span; primaries as stripes with pale edges, darker tips
                    stripe = (xx % 2 == 0)
                    base = mid if stripe else dark
                    if yy >= h - 2:
                        base = tuple(int(c * 0.82) for c in base)
                    if species == 'grouse' and (xx + yy) % 3 == 0 and yy < h - 1:
                        base = light
                    if xx == d - 1:
                        base = edge if species == 'duck' else light
                    jit = r.integers(-6, 7)
                    a[fy + yy, fx + xx] = (*[int(np.clip(c + jit, 0, 255)) for c in base], 255)
        # edges and caps (top, bottom, front, back strips)
        for (fx, fy, w2, h2) in ((u0 + d, v0, 1, d), (u0 + d + 1, v0, 1, d), (u0 + d, v0 + d, 1, h), (u0 + 2 * d + 1, v0 + d, 1, h)):
            for yy in range(h2):
                for xx in range(w2):
                    a[fy + yy, fx + xx] = (*dark, 255)
    return Image.fromarray(a, 'RGBA')


def vignette():
    s = 256
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    r = np.hypot((xx - s / 2) / (s / 2), (yy - s / 2) / (s / 2))
    a = smoothstep(0.45, 1.25, r) ** 1.3
    col = np.zeros((s, s, 3), np.float32) + np.array([0.05, 0.04, 0.035], np.float32)
    return np.concatenate([col, a[..., None]], -1)


def main():
    tex = os.path.join(PATCH, 'textures')
    for sp in ('duck', 'grouse'):
        save(atlas(sp), os.path.join(tex, 'entity', 'wildlife', 'wings', sp + '.png'))
        box_texture(sp).save(os.path.join(tex, 'entity', 'wildlife', sp + '.png'))
    names = []
    for name, arr in sprites():
        save(arr, os.path.join(tex, 'particle', 'wingshot_' + name + '.png'))
        names.append('frontierhunts:wingshot_' + name)
    pj = os.path.join(PATCH, 'particles', 'wingshot_fx.json')
    os.makedirs(os.path.dirname(pj), exist_ok=True)
    json.dump({'textures': names}, open(pj, 'w'), indent=2)
    save(vignette(), os.path.join(tex, 'gui', 'wingshot_vignette.png'))
    print('wrote', len(names), 'sprites')


if __name__ == '__main__':
    main()
