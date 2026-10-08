"""Procedural art for the atvgrime workstream (all original, generated here).

python3 tools/gen_atvgrime_art.py [repo_root] [--preview <dir>]

Writes, under patch/assets/frontierhunts/:
  textures/entity/atv_grime/real_mud_{1..5}.png    512   mud levels baked into the atv_real.png UV layout (cutout)
  textures/entity/atv_grime/real_snow_{1..3}.png   512   snow levels, same layout
  textures/entity/atv_grime/wheel_mud_{1..5}.png   256   atv_wheel.png layout (tread / sidewall / rim)
  textures/entity/atv_grime/wheel_snow_{1..3}.png  256
  textures/entity/atv_grime/blocky_mud_low_{1..5}.png, blocky_mud_high_{1..5}.png, blocky_snow_{1..3}.png  128 (atv.png layout)
  (textures/entity/atv_grime/wet.png was removed by atv2: wetness is a vertex tint now)
  models/entity/atv_real_grime.bin                        per-triangle zone + first visible mud/snow level (draw culling)
  textures/particle/atv_clod_{0..3}.png, atv_mist_{0..3}.png, atv_drop_{0..3}.png + particles/atv_*.json
  textures/gui/atv_grime/splatter.png               1024  4x4 atlas of screen splats (mud, mud drips, snow, water)

The grime is a 3D field evaluated at each texel's model-space position (+ normal), so it is continuous across UV
seams: thresholds t in (0,1]; texture level k shows texels with t <= k/LEVELS. Low zone (tyres, wheel wells, lower
body) and high zone (spray on fenders, body, seat) are kept separate so water can wash from the bottom up.
"""
import io, json, math, os, struct, sys, zipfile
import numpy as np
from PIL import Image, ImageFilter
from scipy.spatial import cKDTree

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atvgrime_geom import (JAR, BLOCKY_WHEELS, blocky_texel_map, blocky_all_quads, load_fhvm, raster_uv, dilate_index, render)

args = [a for a in sys.argv[1:] if not a.startswith('--')]
R = os.path.abspath(args[0] if args else os.path.join(os.path.dirname(__file__), '..'))
PREVIEW = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')
TEX = os.path.join(A, 'textures', 'entity', 'atv_grime')
PT = os.path.join(A, 'textures', 'particle')
PD = os.path.join(A, 'particles')
GUI = os.path.join(A, 'textures', 'gui', 'atv_grime')
MODELS = os.path.join(A, 'models', 'entity')
for d in (TEX, PT, PD, GUI, MODELS):
    os.makedirs(d, exist_ok=True)
if PREVIEW:
    os.makedirs(PREVIEW, exist_ok=True)

MUD_LEVELS = 5
SNOW_LEVELS = 3


# ================================================================== noise
def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0x1000000)


def vnoise3(P, seed):
    P = np.asarray(P, float)
    i0 = np.floor(P).astype(np.int64)
    f = P - i0
    f = f * f * (3 - 2 * f)
    out = 0.0
    for dx in (0, 1):
        wx = f[:, 0] if dx else 1 - f[:, 0]
        for dy in (0, 1):
            wy = f[:, 1] if dy else 1 - f[:, 1]
            for dz in (0, 1):
                wz = f[:, 2] if dz else 1 - f[:, 2]
                out = out + wx * wy * wz * _hash(i0[:, 0] + dx, i0[:, 1] + dy, i0[:, 2] + dz, seed)
    return out


def fbm3(P, freq, octaves, seed, gain=0.5):
    out = 0.0; amp = 1.0; tot = 0.0
    for o in range(octaves):
        out = out + amp * vnoise3(np.asarray(P) * freq * (2.03 ** o) + o * 17.13, seed + o * 31)
        tot += amp; amp *= gain
    return out / tot


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ================================================================== grime fields
class Wheels:
    def __init__(self, centers, radius, half_width):
        self.c = np.array(centers, float); self.R = radius; self.hw = half_width

    def nearest(self, P, N):
        """distance to the nearest tyre surface, facing (normal toward that wheel), local vector to its centre."""
        best = np.full(len(P), 1e9); fac = np.zeros(len(P)); vec = np.zeros_like(P)
        for c in self.c:
            v = P - c
            rad = np.sqrt(v[:, 1] ** 2 + v[:, 2] ** 2)
            dr = np.maximum(rad - self.R, 0)
            dx = np.maximum(np.abs(v[:, 0]) - self.hw, 0)
            d = np.sqrt(dr ** 2 + dx ** 2)
            to = -v / np.maximum(np.linalg.norm(v, axis=1, keepdims=True), 1e-6)
            f = np.clip(np.sum(N * to, 1), 0, 1)
            m = d < best
            best = np.where(m, d, best); fac = np.where(m, f, fac); vec[m] = v[m]
        return best, fac, vec


def body_fields(P, N, wheels, seed, zone_pts=None, zone_nrm=None):
    """Returns zone (0 low / 1 high), t_mud, t_snow, thickness-noise for body texels."""
    h = 24.0 - P[:, 1]
    up = -N[:, 1]  # model +y is down
    dist, facing, vec = wheels.nearest(P, N)
    big = fbm3(P, 0.18, 4, seed + 1)
    mid = fbm3(P, 0.55, 3, seed + 2)
    fine = fbm3(P, 1.7, 2, seed + 3)

    # ---- zone (decided per texel here; realistic mesh overrides it per triangle)
    if zone_pts is None:
        zone_pts, zone_nrm = P, N
    zh = 24.0 - zone_pts[:, 1]
    zd, zf, _ = wheels.nearest(zone_pts, zone_nrm)
    zn = fbm3(zone_pts, 0.22, 2, seed + 9)
    low = (zh + 2.0 * (zn - 0.5) < 8.2) | ((zd < 2.6) & (zf > 0.35))
    zone = np.where(low, 0, 1)

    # ---- low zone: caked from the bottom up, wheel wells first
    t_low = 0.12 + 0.95 * smooth(3.5, 11.0, h) - 0.30 * np.exp(-dist / 2.6) * (0.4 + 0.6 * facing)
    t_low += np.where(-up > 0.6, -0.12, 0.0)  # undersides
    t_low += 0.34 * (big - 0.5) + 0.20 * (mid - 0.5) + 0.10 * (fine - 0.5)

    # ---- high zone: radial spray streaks thrown off the tyres + scattered splats
    theta = np.arctan2(vec[:, 1], vec[:, 2])
    rad = np.sqrt(vec[:, 1] ** 2 + vec[:, 2] ** 2)
    streak = vnoise3(np.stack([theta * 7.5, rad * 0.22, vec[:, 0] * 0.35], 1), seed + 4)
    streak = 0.55 * streak + 0.45 * vnoise3(np.stack([theta * 26.0, rad * 0.45, vec[:, 0] * 1.1], 1), seed + 5)
    streak = smooth(0.25, 0.85, streak)
    spray = np.exp(-dist / 3.8) * (0.3 + 0.7 * smooth(-0.2, 0.6, facing))
    # forward throw: rear of each tyre (+z of wheel centre is the back) and the footwells behind the front wheels
    behind = smooth(-1.0, 4.0, vec[:, 2]) * np.exp(-dist / 7.0) * 0.3
    t_high = 1.25 - (spray + behind) * (0.35 + 1.0 * streak) + 0.45 * smooth(9.0, 17.0, h)
    t_high += 0.22 * (mid - 0.5) + 0.16 * (fine - 0.5)

    t_mud = np.where(zone == 0, t_low, t_high)

    # ---- snow: settles on what faces the sky, sprays onto the lower sides
    t_snow = np.where(up > 0.55, 0.08 + 0.30 * (1 - up) + 0.36 * (big - 0.5) + 0.16 * (fine - 0.5),
             np.where(up > 0.15, 0.52 + 0.35 * (mid - 0.5) + 0.25 * (big - 0.5),
             np.where(up > -0.25, 1.02 + 0.3 * (mid - 0.5) - 0.55 * np.exp(-dist / 3.5) + 0.25 * smooth(6, 12, h),
                      1.2 + 0.3 * (mid - 0.5) - 0.6 * np.exp(-dist / 2.0) * facing)))
    return zone, t_mud, t_snow, dict(h=h, up=up, dist=dist, big=big, mid=mid, fine=fine, t_low=t_low, t_high=t_high)


LOW_TARGET = [0.22, 0.42, 0.62, 0.80, 0.94]
HIGH_TARGET = [0.06, 0.16, 0.29, 0.43, 0.58]
TYRE_TARGET = [0.30, 0.52, 0.70, 0.85, 0.96]
SNOW_TARGET = [0.12, 0.25, 0.40]
TYRE_SNOW_TARGET = [0.2, 0.38, 0.55]


def calibrate(t, sel, target):
    """Monotonic remap of t inside sel so that the fraction covered at level k (t <= k/len) equals target[k-1].
    Keeps the spatial order of the field (what gets dirty first) and makes every level a predictable step."""
    t = t.copy()
    idx = np.nonzero(sel)[0]
    if len(idx) == 0:
        return t
    order = np.argsort(t[idx], kind='stable')
    q = np.empty(len(idx)); q[order] = (np.arange(len(idx)) + 0.5) / len(idx)
    n = len(target)
    xs = [0.0] + list(target) + [1.0]
    ys = [0.0] + [(k + 1) / n for k in range(n)] + [1.6]
    t[idx] = np.interp(q, xs, ys)
    return t


def add_splats(P, N, t, zone, weight, rng, count, seed):
    """Scatter discrete mud drops/splats (with satellite droplets) over high-zone texels. Each splat arrives at a level."""
    hz = np.nonzero(zone == 1)[0]
    if len(hz) == 0:
        return t
    w = weight[hz] + 1e-4
    w = w / w.sum()
    tree = cKDTree(P)
    rough = fbm3(P, 2.4, 2, seed + 7)
    t = t.copy()
    centers = hz[rng.choice(len(hz), size=count, p=w)]
    for ci in centers:
        c = P[ci]
        r = float(np.exp(rng.normal(math.log(0.42), 0.55)))
        r = min(r, 2.4)
        lvl = float(np.clip(0.22 + 0.78 * rng.random() ** 0.75 - 0.2 * weight[ci], 0.15, 0.99))
        blobs = [(c, r)]
        if r > 0.7:
            n = N[ci]
            for _ in range(rng.integers(2, 7)):
                d = rng.normal(size=3); d -= n * np.dot(d, n); d /= max(np.linalg.norm(d), 1e-6)
                blobs.append((c + d * r * rng.uniform(1.3, 2.6), r * rng.uniform(0.12, 0.35)))
        for bc, br in blobs:
            ids = tree.query_ball_point(bc, br * 1.35)
            if not ids:
                continue
            ids = np.array(ids)
            dd = np.linalg.norm(P[ids] - bc, axis=1) / br + 0.55 * (rough[ids] - 0.5)
            hit = ids[dd < 1.0]
            t[hit] = np.minimum(t[hit], lvl)
    return t


def tyre_fields(L, N_local, R, seed, tread_noise_pts=None):
    """Wheel-local position L (x = axle). Tread fills first, then the sidewall from the outside in, rim last."""
    rad = np.sqrt(L[:, 1] ** 2 + L[:, 2] ** 2)
    q = np.clip(rad / R, 0, 1.0)
    side = np.abs(N_local[:, 0]) > 0.55
    Pn = L if tread_noise_pts is None else tread_noise_pts
    big = fbm3(Pn, 0.45, 3, seed + 11); fine = fbm3(Pn, 1.6, 2, seed + 12)
    t_tread = 0.10 + 0.60 * (big - 0.3) + 0.20 * (fine - 0.5)
    t_side = 0.22 + 1.0 * (1 - q) ** 1.1 + 0.35 * (big - 0.5) + 0.15 * (fine - 0.5)
    t_mud = np.where(side, t_side, t_tread)
    t_snow = np.where(side, 0.58 + 0.9 * (1 - q) + 0.3 * (big - 0.5), 0.14 + 0.8 * (big - 0.3) + 0.25 * (fine - 0.5))
    return t_mud, t_snow, big, fine


# ================================================================== colouring
def mud_rgba(t, cut, P, seed, rng_seed, flat=False):
    """Dry-crust colour (the renderer multiplies a dark wet tint on top). Thicker (covered longer) = darker, lumpier."""
    n = len(t)
    cov = t <= cut
    thick = np.clip((cut - t) / 0.38, 0, 1)
    lump = fbm3(P, 0.4 if flat else 1.1, 3, seed + 21)
    grain = fbm3(P, 4.0, 1, seed + 22)
    hue = fbm3(P, 0.12, 2, seed + 23)
    film = np.array([0.63, 0.58, 0.50]); cake = np.array([0.50, 0.44, 0.36]); clayish = np.array([0.56, 0.52, 0.47])
    base = film[None] * (1 - thick[:, None]) + cake[None] * thick[:, None]
    base = base * (1 - 0.35 * hue[:, None]) + clayish[None] * 0.35 * hue[:, None]
    if flat:
        shade = 1.0 + (lump[:, None] - 0.5) * (0.18 + 0.2 * thick[:, None])
    else:
        shade = 1.0 + (lump[:, None] - 0.5) * (0.20 + 0.32 * thick[:, None]) + (grain[:, None] - 0.5) * 0.07
    col = base * shade
    r = np.random.default_rng(rng_seed)
    u = r.random(n)
    peb = (u < (0.004 if flat else 0.004) + 0.008 * thick)
    col[peb] *= 0.62
    if not flat:
        grit = (u > 0.996 - 0.004 * thick)
        col[grit] = np.minimum(col[grit] * 1.2, 1)
    # dried cracks on thick cake (visible once the mud has dried light)
    crack_n = vnoise3(P * (0.7 if flat else 1.4), seed + 24)
    crack = (np.abs(crack_n - 0.5) < (0.025 if flat else 0.035)) & (thick > 0.55)
    col[crack] *= 0.72
    if flat:  # Minecraft-style: a handful of tones of one hue family (quantise brightness, not channels)
        lum = col.mean(1, keepdims=True)
        q = np.round(lum / 0.055) * 0.055
        col = np.clip(col * (q / np.maximum(lum, 1e-6)), 0, 1)
    rgba = np.zeros((n, 4)); rgba[:, :3] = np.clip(col, 0, 1); rgba[:, 3] = cov
    return rgba


def snow_rgba(t, cut, P, seed, flat=False):
    n = len(t)
    cov = t <= cut
    thick = np.clip((cut - t) / 0.3, 0, 1)
    sh = fbm3(P, 0.9, 3, seed + 31)
    grain = fbm3(P, 3.5, 1, seed + 33)
    sparkle = vnoise3(P * 6.0, seed + 32) > 0.93
    thin = np.array([0.74, 0.79, 0.87]); full = np.array([0.95, 0.965, 0.985])
    col = thin[None] * (1 - thick[:, None]) + full[None] * thick[:, None]
    col = col * (0.88 + 0.16 * sh[:, None] + (0 if flat else 0.06) * (grain[:, None] - 0.5))
    if not flat:
        col[sparkle & (thick > 0.3)] = 1.0
    else:
        lum = col.mean(1, keepdims=True)
        q = np.round(lum / 0.05) * 0.05
        col = np.clip(col * (q / np.maximum(lum, 1e-6)), 0, 1)
    rgba = np.zeros((n, 4)); rgba[:, :3] = np.clip(col, 0, 1); rgba[:, 3] = cov
    return rgba


def save_rgba(arr, path, palette=True):
    a8 = (np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8)
    a8[..., 3] = np.where(a8[..., 3] >= 128, 255, 0)
    a8[a8[..., 3] == 0, :3] = 0
    im = Image.fromarray(a8, 'RGBA')
    if palette:
        # palette PNG with one transparent index: tiny files, identical cutout result
        rgb = Image.fromarray(a8[..., :3], 'RGB').quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        idx = np.asarray(rgb).copy()
        idx[a8[..., 3] == 0] = 255
        pal = rgb.getpalette()[:255 * 3] + [0, 0, 0]
        pim = Image.fromarray(idx.astype(np.uint8), 'P'); pim.putpalette(pal); pim.info['transparency'] = 255
        pim.save(path, optimize=True, transparency=255)
    else:
        im.save(path, optimize=True)


def pad(img, idx_valid, iters=2):
    """Bleed colours of covered texels into their transparent neighbours inside the same UV island is NOT wanted for
    cutout (would grow the mask); we only pad the RGB of transparent texels (keeps filtering safe if ever enabled)."""
    return img


# ================================================================== realistic mesh
def realistic(preview):
    parts = load_fhvm()
    rng = np.random.default_rng(91)
    S = 1024
    piv = [p['pivot'] for p in parts]
    wheels = Wheels([piv[i] for i in range(2, 6)], 4.25, 2.05)
    # ---- body + bars into the atv_real atlas
    body_tris = np.concatenate([parts[0]['tris'], parts[1]['tris']])
    owner = np.concatenate([np.zeros(len(parts[0]['tris']), int), np.ones(len(parts[1]['tris']), int)])
    uv = body_tris[:, :, 3:5]
    idx, (pos, nrm) = raster_uv(uv, S, [body_tris[:, :, 0:3], body_tris[:, :, 5:8]])
    idx = dilate_index(idx, 2)
    # padded texels: give them their triangle's centroid data
    tri_c = body_tris[:, :, 0:3].mean(1); tri_n = body_tris[:, :, 5:8].mean(1)
    tri_n /= np.maximum(np.linalg.norm(tri_n, axis=1, keepdims=True), 1e-6)
    empty = (np.linalg.norm(nrm, axis=2) < 1e-6) & (idx >= 0)
    pos[empty] = tri_c[idx[empty]]; nrm[empty] = tri_n[idx[empty]]
    nrm /= np.maximum(np.linalg.norm(nrm, axis=2, keepdims=True), 1e-6)
    m = idx >= 0
    P = pos[m]; N = nrm[m]; T = idx[m]
    # per-triangle zone from the centroid, then broadcast to its texels
    zone_tri, _, _, _ = body_fields(tri_c, tri_n, wheels, 500)
    zone_tri = zone_tri.copy()
    zone_tri[owner == 1] = 1  # handlebars: spray only
    _, _, t_snow, info = body_fields(P, N, wheels, 500)
    zone = zone_tri[T]
    t_mud = np.where(zone == 0, info['t_low'], info['t_high'])
    weight = np.exp(-info['dist'] / 7.0) + 0.15
    t_mud = add_splats(P, N, t_mud, zone, weight, rng, 1100, 600)
    t_mud = calibrate(calibrate(t_mud, zone == 0, LOW_TARGET), zone == 1, HIGH_TARGET)
    t_snow = calibrate(t_snow, np.ones(len(t_snow), bool), SNOW_TARGET)
    for zz, nm in ((0, 'low'), (1, 'high')):
        sel = zone == zz
        print('  real', nm, 'coverage per level', [round(float((t_mud[sel] <= k / MUD_LEVELS).mean()), 2) for k in range(1, MUD_LEVELS + 1)], 'snow', [round(float((t_snow[sel] <= k / SNOW_LEVELS).mean()), 2) for k in range(1, SNOW_LEVELS + 1)])
    ys, xs = np.nonzero(m)
    out = {}
    for k in range(1, MUD_LEVELS + 1):
        img = np.zeros((S, S, 4))
        img[ys, xs] = mud_rgba(t_mud, k / MUD_LEVELS, P, 700, 7000 + k)
        save_rgba(img, os.path.join(TEX, f'real_mud_{k}.png'))
        out[f'mud{k}'] = img
    for k in range(1, SNOW_LEVELS + 1):
        img = np.zeros((S, S, 4))
        img[ys, xs] = snow_rgba(t_snow, k / SNOW_LEVELS, P, 800)
        save_rgba(img, os.path.join(TEX, f'real_snow_{k}.png'))
        out[f'snow{k}'] = img
    # ---- per-triangle culling: first level whose texture shows anything in the triangle's UV footprint
    tmap = np.full((S, S), 9.0); tmap[ys, xs] = t_mud
    smap = np.full((S, S), 9.0); smap[ys, xs] = t_snow
    def first_levels(tris_uv, tm, sm):
        mud_first = np.zeros(len(tris_uv), int); snow_first = np.zeros(len(tris_uv), int)
        for i, tuv in enumerate(tris_uv):
            x0 = max(int(math.floor(tuv[:, 0].min() * S)) - 1, 0); x1 = min(int(math.ceil(tuv[:, 0].max() * S)) + 1, S)
            y0 = max(int(math.floor(tuv[:, 1].min() * S)) - 1, 0); y1 = min(int(math.ceil(tuv[:, 1].max() * S)) + 1, S)
            mt = tm[y0:y1, x0:x1].min() if y1 > y0 and x1 > x0 else 9
            st = sm[y0:y1, x0:x1].min() if y1 > y0 and x1 > x0 else 9
            mud_first[i] = 0 if mt > 1.0 else max(1, int(math.ceil(mt * MUD_LEVELS - 1e-9)))
            snow_first[i] = 0 if st > 1.0 else max(1, int(math.ceil(st * SNOW_LEVELS - 1e-9)))
        return mud_first, snow_first
    mf, sf = first_levels(uv, tmap, smap)

    # ---- wheels: shared, tiled atlas -> 2D layout masks driven by the rasterised wheel-local radius
    WS = 256
    wt = parts[2]['tris']; wp = parts[2]['pivot']
    wl = wt[:, :, 0:3] - wp
    widx, (wpos, wn) = raster_uv(wt[:, :, 3:5], WS, [wl, wt[:, :, 5:8]])
    widx = dilate_index(widx, 2)
    wtri_c = wl.mean(1); wtri_n = wt[:, :, 5:8].mean(1)
    empty = (np.linalg.norm(wn, axis=2) < 1e-6) & (widx >= 0)
    wpos[empty] = wtri_c[widx[empty]]; wn[empty] = wtri_n[widx[empty]]
    wm = widx >= 0
    wys, wxs = np.nonzero(wm)
    # tread rows repeat around the tyre: noise in texture space (uv) keeps neighbouring lugs different
    uvp = np.stack([wxs / WS * 40.0, wys / WS * 40.0, np.zeros(len(wxs))], 1)
    side = np.abs(wn[wm][:, 0]) > 0.55
    noise_pts = np.where(side[:, None], wpos[wm] * 1.0, uvp * 0.22)
    wt_mud, wt_snow, _, _ = tyre_fields(wpos[wm], wn[wm], 4.25, 900, noise_pts)
    # grooves between the lugs (lighter lines in the base texture) pack first
    base = np.asarray(Image.open(io.BytesIO(zipfile.ZipFile(JAR).read('assets/frontierhunts/textures/entity/atv_wheel.png'))).convert('RGB').resize((WS, WS), Image.BILINEAR)).astype(float) / 255
    lum = base[wys, wxs].mean(1)
    groove = (~side) & (lum > np.percentile(lum[~side], 70) if (~side).any() else False)
    wt_mud = np.where(groove, wt_mud - 0.18, wt_mud)
    wt_mud = calibrate(wt_mud, np.ones(len(wt_mud), bool), TYRE_TARGET)
    wt_snow = calibrate(wt_snow, np.ones(len(wt_snow), bool), TYRE_SNOW_TARGET)
    wout = {}
    for k in range(1, MUD_LEVELS + 1):
        img = np.zeros((WS, WS, 4))
        img[wys, wxs] = mud_rgba(wt_mud, k / MUD_LEVELS, np.where(side[:, None], wpos[wm], uvp * 0.25), 710, 7100 + k)
        save_rgba(img, os.path.join(TEX, f'wheel_mud_{k}.png'))
        wout[f'mud{k}'] = img
    for k in range(1, SNOW_LEVELS + 1):
        img = np.zeros((WS, WS, 4))
        img[wys, wxs] = snow_rgba(wt_snow, k / SNOW_LEVELS, np.where(side[:, None], wpos[wm], uvp * 0.25), 810)
        save_rgba(img, os.path.join(TEX, f'wheel_snow_{k}.png'))
        wout[f'snow{k}'] = img
    wtm = np.full((WS, WS), 9.0); wtm[wys, wxs] = wt_mud
    wsm = np.full((WS, WS), 9.0); wsm[wys, wxs] = wt_snow
    S_body = S
    S = WS
    wmf, wsf = first_levels(wt[:, :, 3:5], wtm, wsm)
    S = S_body

    # ---- culling table
    buf = bytearray(b'FHGR') + struct.pack('>iii', 1, len(parts), MUD_LEVELS)
    n0 = len(parts[0]['tris'])
    for pi, p in enumerate(parts):
        nt = len(p['tris'])
        buf += struct.pack('>i', nt)
        if pi <= 1:
            sl = slice(0, n0) if pi == 0 else slice(n0, n0 + nt)
            z, a, b = zone_tri[sl], mf[sl], sf[sl]
        else:
            z, a, b = np.zeros(nt, int), wmf, wsf
        buf += bytes(((int(z[i]) & 1) << 7) | ((int(b[i]) & 7) << 3) | (int(a[i]) & 7) for i in range(nt))
    open(os.path.join(MODELS, 'atv_real_grime.bin'), 'wb').write(bytes(buf))
    print('realistic: body tris', len(mf), 'zone high', int(zone_tri.sum()), 'mud-first hist', np.bincount(mf, minlength=6),
          'snow-first hist', np.bincount(sf, minlength=4), 'wheel mud-first', np.bincount(wmf, minlength=6))
    return parts, out, wout, zone_tri


# ================================================================== blocky model
def blocky():
    S = 128
    pos, nrm, kind, loc, _ = blocky_texel_map(S)
    wheels = Wheels(BLOCKY_WHEELS, 4.5, 2.0)
    m = kind > 0
    ys, xs = np.nonzero(m)
    P = pos[m]; N = nrm[m]; K = kind[m]; L = loc[m]
    zone, t_mud, t_snow, info = body_fields(P, N, wheels, 300)
    zone = np.where(K == 2, 1, zone)
    tyre = (K == 3) | (K == 4)
    # tyre/hub texels: wheel-local fields (normals in local frame: rotate back is unnecessary for the x test)
    tm, ts, _, _ = tyre_fields(L, N, 4.5, 400)
    tm = np.where(K == 3, 0.55 + 0.6 * (fbm3(L, 0.8, 2, 401) - 0.3), tm)   # hub = rim, late
    ts = np.where(K == 3, 1.1, ts)
    t_mud = np.where(tyre, tm, t_mud); t_snow = np.where(tyre, ts, t_snow); zone = np.where(tyre, 0, zone)
    rng = np.random.default_rng(31)
    weight = np.exp(-info['dist'] / 7.0) + 0.15
    t_mud = add_splats(P, N, t_mud, zone, weight, rng, 70, 310)
    t_mud = calibrate(calibrate(calibrate(t_mud, (zone == 0) & ~tyre, LOW_TARGET), zone == 1, HIGH_TARGET), tyre, TYRE_TARGET)
    t_snow = calibrate(calibrate(t_snow, ~tyre, SNOW_TARGET), tyre, TYRE_SNOW_TARGET)
    for zz, nm in ((0, 'low'), (1, 'high')):
        sel = (zone == zz) & ~tyre
        print('  blocky', nm, 'coverage', [round(float((t_mud[sel] <= k / MUD_LEVELS).mean()), 2) for k in range(1, MUD_LEVELS + 1)])
    print('  blocky tyre', [round(float((t_mud[tyre] <= k / MUD_LEVELS).mean()), 2) for k in range(1, MUD_LEVELS + 1)])
    base = np.asarray(Image.open(io.BytesIO(zipfile.ZipFile(JAR).read('assets/frontierhunts/textures/entity/atv.png'))).convert('RGBA')).astype(float) / 255
    opaque = base[ys, xs, 3] > 0
    out = {}
    for zn, zname in ((0, 'low'), (1, 'high')):
        for k in range(1, MUD_LEVELS + 1):
            img = np.zeros((S, S, 4))
            rgba = mud_rgba(t_mud, k / MUD_LEVELS, P if zn else np.where(tyre[:, None], L, P), 320 + zn, 3200 + k, flat=True)
            rgba[:, 3] *= (zone == zn) & opaque
            img[ys, xs] = rgba
            save_rgba(img, os.path.join(TEX, f'blocky_mud_{zname}_{k}.png'), palette=False)
            out[f'{zname}{k}'] = img
    for k in range(1, SNOW_LEVELS + 1):
        img = np.zeros((S, S, 4))
        rgba = snow_rgba(t_snow, k / SNOW_LEVELS, P, 330, flat=True)
        rgba[:, 3] *= opaque
        img[ys, xs] = rgba
        save_rgba(img, os.path.join(TEX, f'blocky_snow_{k}.png'), palette=False)
        out[f'snow{k}'] = img
    return out


# ================================================================== wet film (tileable, translucent)
def wet_tile():
    S = 256
    rng = np.random.default_rng(55)
    img = np.zeros((S, S, 4))
    # faint dark film so wet paint/mud reads darker and slightly saturated
    yy, xx = np.mgrid[0:S, 0:S]
    film = 0.20 + 0.06 * np.sin(xx / S * 2 * np.pi * 3 + np.sin(yy / S * 2 * np.pi * 2) * 1.5)
    img[..., 0:3] = np.array([0.05, 0.06, 0.07]); img[..., 3] = film
    # beads: dark refracting rim, clear body, a sharp specular glint upper-left (light comes from above)
    for _ in range(170):
        cx, cy = rng.uniform(0, S, 2); r = float(np.exp(rng.normal(math.log(3.2), 0.45))); r = min(r, 9)
        elong = rng.uniform(1.0, 1.6)
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                x0 = int(cx + ox - r - 2); x1 = int(cx + ox + r + 3); y0 = int(cy + oy - r * elong - 2); y1 = int(cy + oy + r * elong + 3)
                if x1 < 0 or y1 < 0 or x0 >= S or y0 >= S:
                    continue
                xs = np.arange(max(x0, 0), min(x1, S)); ys_ = np.arange(max(y0, 0), min(y1, S))
                X, Y = np.meshgrid(xs + 0.5, ys_ + 0.5)
                dx = (X - cx - ox) / r; dy = (Y - cy - oy) / (r * elong)
                d = np.sqrt(dx * dx + dy * dy)
                inside = d < 1
                if not inside.any():
                    continue
                sub = img[ys_[0]:ys_[-1] + 1, xs[0]:xs[-1] + 1]
                rim = smooth(0.62, 0.98, d) * inside
                body = inside * (1 - rim)
                a_rim = 0.55 * rim; a_body = 0.10 * body
                col = np.zeros(X.shape + (3,))
                col[:] = np.array([0.03, 0.035, 0.04])
                gl = np.exp(-(((dx + 0.35) / 0.22) ** 2 + ((dy + 0.4) / 0.18) ** 2)) * inside
                lo = np.exp(-(((dx - 0.25) / 0.35) ** 2 + ((dy - 0.45) / 0.22) ** 2)) * inside * 0.5
                a = np.maximum(sub[..., 3], a_rim + a_body)
                c = sub[..., :3] * (1 - inside[..., None]) + col * inside[..., None]
                c = c * (1 - gl[..., None]) + np.array([1.0, 1.0, 1.0]) * gl[..., None]
                c = c * (1 - lo[..., None]) + np.array([0.75, 0.8, 0.85]) * lo[..., None]
                a = np.maximum(a, np.maximum(gl * 0.95, lo * 0.4))
                sub[..., :3] = np.where(inside[..., None], c, sub[..., :3]); sub[..., 3] = np.where(inside, a, sub[..., 3])
    # a few run-off trails
    for _ in range(14):
        x = rng.uniform(0, S); y = rng.uniform(0, S); L = rng.uniform(20, 70); w = rng.uniform(0.8, 1.6)
        for j in range(int(L)):
            px = int(x + math.sin(j * 0.15 + x) * 1.2) % S; py = int(y + j) % S
            for dx in range(-2, 3):
                q = abs(dx) / (w + 0.5)
                if q < 1:
                    a = (1 - q) * 0.35 * (1 - j / L * 0.6)
                    img[py, (px + dx) % S, 3] = max(img[py, (px + dx) % S, 3], a)
                    img[py, (px + dx) % S, :3] = np.array([0.04, 0.05, 0.06]) if dx else np.array([0.55, 0.6, 0.65])
    Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(TEX, 'wet.png'), optimize=True)
    return img


# ================================================================== particle sprites
def sprite_save(arr, name):
    Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(PT, name + '.png'), optimize=True)


def particles():
    rng = np.random.default_rng(77)
    # clods: lumpy irregular chunks, neutral light colour (tinted per use: brown mud, white snow), lit from above
    for i in range(4):
        S = 16
        yy, xx = np.mgrid[0:S, 0:S] + 0.5
        cx = cy = S / 2
        ang = np.arctan2(yy - cy, xx - cx); d = np.hypot(xx - cx, yy - cy)
        k = rng.integers(5, 9)
        ph = rng.uniform(0, 6.28, 3); amp = rng.uniform(0.06, 0.15, 3) * np.array([1.0, 0.7, 0.35])
        rr = (S * 0.36) * (1 + amp[0] * np.sin(ang * 3 + ph[0]) + amp[1] * np.sin(ang * 5 + ph[1]) + amp[2] * np.sin(ang * k + ph[2]))
        inside = d < rr
        P = np.stack([xx.ravel() * 0.6 + i * 9, yy.ravel() * 0.6, np.zeros(S * S)], 1)
        n = fbm3(P, 0.9, 3, 40 + i).reshape(S, S)
        light = 0.86 + 0.3 * np.clip((cy - yy) / (S * 0.4) * 0.6 + (cx - xx) / (S * 0.4) * 0.3, -1, 1) * 0.5 + (n - 0.5) * 0.45
        edge = smooth(rr - 1.2, rr, d)
        col = np.clip(light * (1 - 0.25 * edge), 0, 1)
        speck = (rng.random((S, S)) < 0.08) & inside
        col[speck] *= 0.6
        arr = np.zeros((S, S, 4)); arr[..., 0] = col; arr[..., 1] = col * 0.97; arr[..., 2] = col * 0.93; arr[..., 3] = inside
        sprite_save(arr, f'atv_clod_{i}')
    # mist puffs: soft billowing blobs
    for i in range(4):
        S = 32
        yy, xx = np.mgrid[0:S, 0:S] + 0.5
        d = np.hypot(xx - S / 2, yy - S / 2) / (S / 2)
        P = np.stack([xx.ravel() * 0.25 + i * 7, yy.ravel() * 0.25, np.full(S * S, i * 3.0)], 1)
        n = fbm3(P, 1.0, 4, 60 + i).reshape(S, S)
        a = np.clip((1 - smooth(0.25, 1.0, d + (n - 0.5) * 0.55)) * (0.55 + 0.6 * n), 0, 1) * 0.85
        col = 0.85 + 0.15 * n
        arr = np.zeros((S, S, 4)); arr[..., 0] = col; arr[..., 1] = col; arr[..., 2] = col; arr[..., 3] = a
        sprite_save(arr, f'atv_mist_{i}')
    # water drops: round bead + elongated falling drops with highlight
    for i in range(4):
        S = 16
        yy, xx = np.mgrid[0:S, 0:S] + 0.5
        el = [1.0, 1.25, 1.6, 2.0][i]
        dx = (xx - S / 2) / (S * 0.3 / el ** 0.3); dy = (yy - S / 2) / (S * 0.3 * el ** 0.5)
        d = np.sqrt(dx * dx + dy * dy)
        inside = d < 1
        a = inside * (0.5 + 0.4 * smooth(0.5, 1.0, d))
        col = np.full((S, S), 0.82)
        gl = np.exp(-(((dx + 0.35) / 0.25) ** 2 + ((dy + 0.35) / 0.25) ** 2)) * inside
        col = col * (1 - gl) + gl
        a = np.maximum(a, gl)
        arr = np.zeros((S, S, 4)); arr[..., 0] = col * 0.93; arr[..., 1] = col * 0.97; arr[..., 2] = col; arr[..., 3] = a
        sprite_save(arr, f'atv_drop_{i}')
    for name in ('clod', 'mist', 'drop'):
        json.dump({'textures': [f'frontierhunts:atv_{name}_{i}' for i in range(4)]}, open(os.path.join(PD, f'atv_{name}.json'), 'w'), indent=2)


# ================================================================== screen splatter atlas (4x4 cells of 256)
def splatter_atlas():
    C = 256; S = C * 4
    rng = np.random.default_rng(123)
    atlas = np.zeros((S, S, 4))
    yy, xx = np.mgrid[0:C, 0:C] + 0.5
    X = (xx - C / 2) / (C / 2); Y = (yy - C / 2) / (C / 2)
    ang = np.arctan2(Y, X); d = np.hypot(X, Y)

    def noise2(scale, seed, octv=4):
        P = np.stack([xx.ravel() * scale, yy.ravel() * scale, np.full(C * C, seed * 1.7)], 1)
        return fbm3(P, 1.0, octv, seed).reshape(C, C)

    def mud_cell(seed, kind):
        r = np.random.default_rng(seed)
        n = noise2(0.03, seed); nf = noise2(0.12, seed + 1, 3)
        # domain warp: nothing on a lens is a perfect geometric shape
        wx = (noise2(0.018, seed + 5) - 0.5) * 0.35; wy = (noise2(0.018, seed + 6) - 0.5) * 0.35
        Xw = X + wx; Yw = Y + wy
        aw = np.arctan2(Yw, Xw); dw = np.hypot(Xw, Yw)
        if kind == 'splat':
            # lumpy core, a few tapering spatter arms, satellite droplets flung outward
            harm = np.zeros_like(aw)
            for hk in (2, 3, 5, 7):
                harm += r.uniform(0.02, 0.07) * np.sin(aw * hk + r.uniform(0, 6.28))
            rad = 0.36 + harm + 0.06 * (n - 0.5) * 2
            m = (dw < rad).astype(float)
            for _ in range(r.integers(3, 7)):
                a0 = r.uniform(-np.pi, np.pi); L = r.uniform(0.5, 0.88); w0 = r.uniform(0.05, 0.11)
                da = np.abs(np.angle(np.exp(1j * (aw - a0 - 0.15 * np.sin(dw * 6 + a0)))))
                taper = np.clip(1 - (dw - 0.25) / (L - 0.25), 0, 1)
                m = np.maximum(m, ((da < w0 * taper ** 0.8 * (1 + 0.35 * np.sin(dw * 23 + a0))) & (dw < L)).astype(float))
                # droplet at the arm tip
                tx, ty = math.cos(a0) * (L + 0.05), math.sin(a0) * (L + 0.05)
                m = np.maximum(m, (np.hypot(Xw - tx, Yw - ty) < r.uniform(0.025, 0.05)).astype(float))
            for _ in range(r.integers(8, 18)):
                a0 = r.uniform(-np.pi, np.pi); dist = r.uniform(0.5, 0.95); rr = r.uniform(0.012, 0.045)
                cx, cy = math.cos(a0) * dist, math.sin(a0) * dist
                m = np.maximum(m, (np.hypot(X - cx, (Y - cy) * r.uniform(0.7, 1.3)) < rr).astype(float))
        else:  # drip: a smaller splat whose excess runs down the lens and beads at the end
            harm = np.zeros_like(aw)
            for hk in (2, 3, 5):
                harm += r.uniform(0.02, 0.05) * np.sin(aw * hk + r.uniform(0, 6.28))
            m = (np.hypot(Xw, (Yw + 0.45) * 1.05) < 0.22 + harm).astype(float)
            Ld = r.uniform(0.55, 0.95)
            yy_ = (Yw + 0.3) / (Ld + 0.3)
            wv = 0.075 * (1 - 0.55 * np.clip(yy_, 0, 1)) * (1 + 0.25 * np.sin(Yw * 17 + seed))
            run = (np.abs(Xw - 0.05 * np.sin(Yw * 4 + seed)) < wv) & (Yw > -0.4) & (Yw < Ld - 0.3)
            m = np.maximum(m, run.astype(float))
            ex = 0.05 * np.sin((Ld - 0.3) * 4 + seed)
            m = np.maximum(m, (np.hypot(Xw - ex, (Yw - (Ld - 0.3)) * 0.9) < 0.065).astype(float))
        m = np.array(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))).astype(float) / 255
        # thickness: thick centre (opaque), thin edges (translucent film)
        dist_in = np.array(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(9))).astype(float) / 255
        thick = smooth(0.35, 0.9, dist_in)
        alpha = smooth(0.35, 0.6, m) * (0.62 + 0.36 * thick)
        grain = noise2(0.25, seed + 3, 2)
        lump = noise2(0.07, seed + 4, 3)
        base = np.array([0.62, 0.56, 0.47])
        cake = np.array([0.47, 0.40, 0.32])
        col = base[None, None] * (1 - thick[..., None]) + cake[None, None] * thick[..., None]
        # lumps lit from the top-left
        gy, gx = np.gradient(lump)
        shade = 1.0 + np.clip(-(gx + gy) * 30, -0.18, 0.18) * thick + (grain - 0.5) * 0.10
        col = col * shade[..., None]
        speck = (r.random((C, C)) < 0.02) & (thick > 0.3)
        col[speck] *= 0.55
        out = np.zeros((C, C, 4)); out[..., :3] = np.clip(col, 0, 1); out[..., 3] = alpha
        return out

    def snow_cell(seed, kind):
        r = np.random.default_rng(seed)
        n = noise2(0.04, seed); nf = noise2(0.15, seed + 1, 3)
        if kind == 'smear':
            # wind-smeared powder: soft elongated patch with grains
            q = np.hypot(X / 0.85, Y / 0.42)
            a = (1 - smooth(0.35, 1.0, q + (n - 0.5) * 0.5)) * (0.45 + 0.4 * nf)
        else:
            # clumps of flakes / crystals
            a = np.zeros_like(d)
            for _ in range(r.integers(6, 12)):
                cx, cy = r.uniform(-0.6, 0.6, 2); rr = r.uniform(0.05, 0.18)
                a = np.maximum(a, (1 - smooth(rr * 0.6, rr, np.hypot(X - cx, Y - cy) + (nf - 0.5) * 0.08)) * r.uniform(0.6, 0.9))
        sparkle = (r.random((C, C)) < 0.012) & (a > 0.3)
        col = np.full((C, C, 3), 0.94); col[..., 2] = 0.98
        col = col * (0.9 + 0.12 * nf[..., None])
        col[sparkle] = 1.0
        out = np.zeros((C, C, 4)); out[..., :3] = np.clip(col, 0, 1); out[..., 3] = np.clip(a, 0, 1) * 0.9
        return out

    def water_cell(seed, kind):
        r = np.random.default_rng(seed)
        out = np.zeros((C, C, 4))
        if kind == 'bead':
            drops = [(r.uniform(-0.55, 0.55), r.uniform(-0.55, 0.55), r.uniform(0.08, 0.2)) for _ in range(r.integers(4, 8))]
            drops.append((0.0, 0.0, 0.34))
        else:  # run: drop with a wet trail above it
            drops = [(0.0, 0.55, 0.2)]
            trail = (np.abs(X + 0.03 * np.sin(Y * 6 + seed)) < 0.06 + 0.02 * np.sin(Y * 9)) & (Y < 0.5) & (Y > -0.95)
            out[..., :3] = np.where(trail[..., None], np.array([0.75, 0.8, 0.86]), out[..., :3])
            out[..., 3] = np.where(trail, 0.22 + 0.1 * smooth(-0.95, 0.5, Y), 0)
            edge = trail & ~((np.abs(X + 0.03 * np.sin(Y * 6 + seed)) < 0.035))
            out[..., 3] = np.where(edge, 0.38, out[..., 3])
            out[..., :3] = np.where(edge[..., None], np.array([0.25, 0.28, 0.32]), out[..., :3])
        for cx, cy, rr in drops:
            dx = (X - cx) / rr; dy = (Y - cy) / (rr * 1.12)
            dd = np.sqrt(dx * dx + dy * dy)
            inside = dd < 1
            rim = smooth(0.6, 1.0, dd) * inside
            # refraction look without distortion: dark lower-right rim, bright caustic bottom, highlight upper-left
            col = np.array([0.80, 0.84, 0.88]) * np.ones((C, C, 3))
            col = col * (1 - 0.65 * rim[..., None] * smooth(-0.6, 0.6, (dx + dy))[..., None])
            gl = np.exp(-(((dx + 0.38) / 0.16) ** 2 + ((dy + 0.42) / 0.12) ** 2))
            ca = np.exp(-(((dx - 0.1) / 0.45) ** 2 + ((dy - 0.55) / 0.2) ** 2)) * 0.6
            col = col * (1 - gl[..., None]) + gl[..., None]
            col = col * (1 - ca[..., None]) + np.array([0.95, 0.97, 1.0]) * ca[..., None]
            a = inside * (0.16 + 0.42 * rim)
            a = np.maximum(a, np.maximum(gl, ca * 0.7) * inside)
            sel = inside
            out[..., :3] = np.where(sel[..., None], col, out[..., :3]); out[..., 3] = np.where(sel, np.maximum(out[..., 3], a), out[..., 3])
        return out

    cells = []
    for i in range(4):
        cells.append(mud_cell(200 + i, 'splat'))
    for i in range(4):
        cells.append(mud_cell(300 + i, 'drip'))
    for i in range(4):
        cells.append(snow_cell(400 + i, 'smear' if i < 2 else 'clump'))
    for i in range(4):
        cells.append(water_cell(500 + i, 'bead' if i < 2 else 'run'))
    for k, c in enumerate(cells):
        cy, cx = divmod(k, 4)
        atlas[cy * C:(cy + 1) * C, cx * C:(cx + 1) * C] = c
    Image.fromarray((np.clip(atlas, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(GUI, 'splatter.png'), optimize=True)
    return atlas


# ================================================================== previews
WET_TINT = np.array([0.50, 0.45, 0.40])


def preview_realistic(parts, out, wout, zone_tri, path, mud_low, mud_high, snow, tint):
    z = zipfile.ZipFile(JAR)
    real = np.asarray(Image.open(io.BytesIO(z.read('assets/frontierhunts/textures/entity/atv_real.png'))).convert('RGBA')).astype(float) / 255
    wheel = np.asarray(Image.open(io.BytesIO(z.read('assets/frontierhunts/textures/entity/atv_wheel.png'))).convert('RGBA')).astype(float) / 255

    def samp(T, uv, mul=None):
        H, W = T.shape[:2]
        def s(l0, l1, l2):
            u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]; v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
            x = np.clip((u * W).astype(int), 0, W - 1); y = np.clip((v * H).astype(int), 0, H - 1)
            c = T[y, x].copy()
            if mul is not None:
                c[:, :3] *= mul
            return c
        return s
    tris = []
    n0 = len(parts[0]['tris'])
    for pi, p in enumerate(parts):
        for ti, tr in enumerate(p['tris']):
            isw = p['tex'] == 1
            tris.append((tr[:, :3], samp(wheel if isw else real, tr[:, 3:5]), tr[:, 5:8]))
            gi = ti if pi == 0 else n0 + ti
            lvl = mud_low if isw or zone_tri[gi] == 0 else mud_high
            if lvl > 0:
                tris.append((tr[:, :3], samp((wout if isw else out)[f'mud{lvl}'], tr[:, 3:5], tint), tr[:, 5:8]))
            if snow > 0:
                tris.append((tr[:, :3], samp((wout if isw else out)[f'snow{snow}'], tr[:, 3:5]), tr[:, 5:8]))
    return render(tris, W=560, H=420, scale=13)


def preview_blocky(bout, mud_low, mud_high, snow, tint, yaw=35):
    z = zipfile.ZipFile(JAR)
    atv = np.asarray(Image.open(io.BytesIO(z.read('assets/frontierhunts/textures/entity/atv.png'))).convert('RGBA')).astype(float) / 255

    def samp(T, uv, mul=None):
        def s(l0, l1, l2):
            u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]; v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
            x = np.clip(u.astype(int), 0, 127); y = np.clip(v.astype(int), 0, 127)
            c = T[y, x].copy()
            if mul is not None:
                c[:, :3] *= mul
            return c
        return s
    tris = []
    for P, UV, k in blocky_all_quads():
        n = np.repeat([np.cross(P[2] - P[1], P[0] - P[1])], 3, 0)
        for ids in ((0, 1, 2), (0, 2, 3)):
            ids = list(ids)
            tris.append((P[ids], samp(atv, UV[ids]), n))
            if mud_low:
                tris.append((P[ids], samp(bout[f'low{mud_low}'], UV[ids], tint), n))
            if mud_high:
                tris.append((P[ids], samp(bout[f'high{mud_high}'], UV[ids], tint), n))
            if snow:
                tris.append((P[ids], samp(bout[f'snow{snow}'], UV[ids]), n))
    return render(tris, W=560, H=420, scale=14, yaw=yaw)


def grid(images, cols):
    h, w = images[0].shape[:2]
    rows = (len(images) + cols - 1) // cols
    g = np.zeros((rows * h, cols * w, 3), np.uint8)
    for i, im in enumerate(images):
        r, c = divmod(i, cols)
        g[r * h:(r + 1) * h, c * w:(c + 1) * w] = im
    return g


if __name__ == '__main__':
    import time; _t0 = time.time()
    def lap(m): print(f'[{time.time() - _t0:6.1f}s] {m}', flush=True)
    parts, out, wout, zone_tri = realistic(PREVIEW)
    lap('realistic'); bout = blocky(); lap('blocky')
    # [atv2] wet.png (translucent film) removed: it rendered black under shader packs; wetness is a vertex tint now
    particles()
    atlas = splatter_atlas(); lap('sprites/atlas')
    if PREVIEW:
        imgs = [preview_realistic(parts, out, wout, zone_tri, None, l, h, 0, WET_TINT if wetc else None)
                for (l, h, wetc) in ((2, 0, True), (3, 1, True), (5, 3, True), (5, 5, False))]
        Image.fromarray(grid(imgs, 2)).save(os.path.join(PREVIEW, 'real_mud.png'))
        import atvgrime_geom as G
        old = G.render
        G_render = lambda tris, **k: old(tris, W=900, H=640, scale=34, yaw=-50, pitch=18, center=(-4, -14, 6))
        globals()['render'] = G_render
        Image.fromarray(preview_realistic(parts, out, wout, zone_tri, None, 4, 4, 0, WET_TINT)).save(os.path.join(PREVIEW, 'real_close_wet.png'))
        Image.fromarray(preview_realistic(parts, out, wout, zone_tri, None, 5, 5, 0, None)).save(os.path.join(PREVIEW, 'real_close_dry.png'))
        globals()['render'] = old
        imgs = [preview_realistic(parts, out, wout, zone_tri, None, 0, 0, s, None) for s in (1, 2, 3)]
        imgs.append(preview_realistic(parts, out, wout, zone_tri, None, 3, 2, 2, WET_TINT * 1.3))
        Image.fromarray(grid(imgs, 2)).save(os.path.join(PREVIEW, 'real_snow.png'))
        imgs = [preview_blocky(bout, l, h, s, t) for (l, h, s, t) in ((2, 0, 0, WET_TINT), (4, 2, 0, WET_TINT), (5, 5, 0, None), (0, 0, 3, None))]
        Image.fromarray(grid(imgs, 2)).save(os.path.join(PREVIEW, 'blocky.png'))
        bg = np.zeros((1024, 1024, 4)); bg[..., :3] = 0.45; bg[..., 3] = 1
        a = atlas[..., 3:4]
        comp = bg[..., :3] * (1 - a) + atlas[..., :3] * a
        Image.fromarray((comp * 255).astype(np.uint8)).save(os.path.join(PREVIEW, 'splatter.png'))
    print('done')
