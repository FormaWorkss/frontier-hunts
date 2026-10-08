"""[benchart] Small vectorised 3D value-noise / fbm helpers for procedural (solid) textures."""
import numpy as np

_RNG = np.random.default_rng(1234)
_TAB = _RNG.random(1 << 16).astype(np.float32)


def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return _TAB[(h ^ (h >> 16)) & 0xFFFF]


def vnoise(x, y, z, seed=0):
    """value noise in [0,1], smooth (quintic) interpolation; x,y,z same-shape float arrays"""
    x = np.asarray(x, np.float64)
    y = np.asarray(y, np.float64)
    z = np.asarray(z, np.float64)
    ix, iy, iz = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    ix, iy, iz = ix.astype(np.int64), iy.astype(np.int64), iz.astype(np.int64)
    ux = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
    uy = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
    uz = fz * fz * fz * (fz * (fz * 6 - 15) + 10)
    r = 0.0
    for dx in (0, 1):
        wx = ux if dx else 1 - ux
        for dy in (0, 1):
            wy = uy if dy else 1 - uy
            for dz in (0, 1):
                wz = uz if dz else 1 - uz
                r = r + wx * wy * wz * _hash(ix + dx, iy + dy, iz + dz, seed)
    return r


def fbm(x, y, z, seed=0, octaves=4, lac=2.0, gain=0.5):
    amp, tot, s = 1.0, 0.0, 0.0
    for o in range(octaves):
        s = s + amp * vnoise(x, y, z, seed + o * 101)
        tot += amp
        x, y, z = x * lac, y * lac, z * lac
        amp *= gain
    return s / tot


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def hexrgb(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def ramp(t, stops):
    """t: (n,) in 0..1; stops: list of (pos, '#rrggbb')"""
    t = np.clip(t, 0, 1)
    ps = np.array([s[0] for s in stops])
    cs = np.array([hexrgb(s[1]) for s in stops])
    out = np.empty(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, ps, cs[:, c])
    return out
