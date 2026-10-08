"""[gunsmith] Firearm material atlas generator.

firearms_v2.png keeps its 4x4 layout (every .fheq gun / attachment / optic maps its UVs into these cells and multiplies a
vertex tint), so the meshes, tints and UVs are untouched - only the materials get better:

  row 0: blued steel | parkerized steel | walnut | checkered walnut
  row 1: black polymer | bright (brushed) steel | brass | ribbed rubber
  row 2: tan (FDE) polymer | anodized aluminium (scopes, rails, AR) | blaze orange | case-hardened / mottled steel
  row 3: stippled polymer | glow | white paint | birch

Ultra:   2048 px (512 px cells), detailed procedural materials, cell means kept close to the old atlas so every vertex
         tint still lands on the same colour.
Vanilla: firearms_mc.png, 1024 px, each cell is 16x16 "Minecraft texels" (64 px blocks) with a small palette and the
         vanilla-style random dither - a clean MC-styled look on the same meshes.

usage: python3 materials.py <out dir>   (writes firearms_v2.png, firearms_mc.png, materials_preview.png)
"""
import sys
import os
import numpy as np
from PIL import Image

C = 512
rng = np.random.default_rng(20261002)


# ----------------------------------------------------------------------------------------------- noise helpers
def value_noise(h, w, sy, sx, seed):
    """Smooth value noise with lattice spacing (sy, sx) pixels (bicubic-interpolated random lattice)."""
    r = np.random.default_rng(seed)
    gy, gx = int(np.ceil(h / sy)) + 3, int(np.ceil(w / sx)) + 3
    g = r.standard_normal((gy, gx)).astype(np.float32)
    im = Image.fromarray(g, mode='F').resize((int(gx * sx), int(gy * sy)), Image.BICUBIC)
    a = np.asarray(im)[int(sy):int(sy) + h, int(sx):int(sx) + w]
    return (a - a.mean()) / (a.std() + 1e-6)


def fbm(h, w, scale, octaves, seed, aniso=1.0, gain=0.5):
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        s = max(1.0, scale / (2 ** o))
        out += amp * value_noise(h, w, s, s * aniso, seed + o * 101)
        tot += amp
        amp *= gain
    return out / tot


def white(h, w, seed):
    return np.random.default_rng(seed).standard_normal((h, w)).astype(np.float32)


def blur(a, r):
    from scipy.ndimage import gaussian_filter
    return gaussian_filter(a.astype(np.float32), r)


def col(rgb):
    return np.array(rgb, np.float32)


def compose(base, *layers):
    """base RGB (3,) + sum of (field(h,w), rgb delta(3,)) -> (h,w,3)."""
    out = np.ones((C, C, 3), np.float32) * base
    for f, d in layers:
        out += f[..., None] * np.array(d, np.float32)
    return out


def match_mean(img, target, strength=1.0):
    m = img.reshape(-1, 3)[C // 10:].mean(0)
    return img + (np.array(target, np.float32) - m) * strength


# ----------------------------------------------------------------------------------------------- materials
def blued():
    pol = fbm(C, C, 2, 2, 11, aniso=60)          # fine polishing lines along u
    streak = fbm(C, C, 6, 3, 12, aniso=25)
    mott = fbm(C, C, 90, 4, 13)
    pit = white(C, C, 14)
    wear = np.clip(fbm(C, C, 26, 3, 15, aniso=7.0) - 1.6, 0, None)   # rare faint streaks of rubbed bluing
    img = compose(col((52, 57, 70)), (pol, (2.2, 2.4, 3.0)), (streak, (1.6, 1.8, 2.4)), (mott, (2.0, 1.6, 3.4)),
                  (pit, (1.0, 1.0, 1.2)), (wear, (9, 9, 8)))
    return match_mean(img, (58, 62, 72))


def parkerized():
    g1 = white(C, C, 21)
    g2 = blur(white(C, C, 22), 1.2) * 2.2
    m = fbm(C, C, 60, 3, 23)
    img = compose(col((66, 69, 66)), (g1, (4.0, 4.0, 3.8)), (g2, (3.0, 3.2, 3.0)), (m, (2.0, 2.4, 1.6)))
    return match_mean(img, (69, 71, 69))


def walnut_field(seed):
    """Walnut: grain along u (x), with warped growth rings, figure streaks, open pores and an oil sheen."""
    y, x = np.mgrid[0:C, 0:C].astype(np.float32)
    warp = fbm(C, C, 140, 4, seed, aniso=3.0) * 22 + fbm(C, C, 40, 3, seed + 1, aniso=4.0) * 5
    rings = np.sin((y + warp) * (2 * np.pi / 15.0) + fbm(C, C, 200, 2, seed + 2) * 3)
    rings = np.sign(rings) * np.abs(rings) ** 0.6
    late = np.clip(np.sin((y + warp * 1.3) * (2 * np.pi / 47.0)), 0, None) ** 3       # darker latewood bands
    figure = fbm(C, C, 50, 4, seed + 3, aniso=6.0)
    pores = np.clip(-white(C, C, seed + 4), 2.0, None) - 2.0
    pores = blur(np.repeat(pores[:, ::5], 5, axis=1)[:, :C], 0.8) * 4               # fine open pores, along the grain
    sheen = fbm(C, C, 160, 2, seed + 5, aniso=1.5)
    return rings, late, figure, pores, sheen


def walnut():
    rings, late, figure, pores, sheen = walnut_field(31)
    img = compose(col((122, 70, 38)), (rings, (8, 5.5, 3.5)), (late, (-30, -21, -12)), (figure, (14, 8, 4)),
                  (pores, (-14, -10, -6)), (sheen, (5, 4, 2.5)))
    return match_mean(img, (126, 72, 39))


def checkered():
    rings, late, figure, pores, sheen = walnut_field(41)
    base = compose(col((112, 63, 34)), (rings, (8, 5, 3)), (late, (-26, -17, -9)), (figure, (12, 7, 4)),
                   (pores, (-20, -13, -7)), (sheen, (4, 3, 2)))
    y, x = np.mgrid[0:C, 0:C].astype(np.float32)
    # two families of cut lines at +-20 deg to the grain (u): diamonds ~2.7:1 along the grain, 18 lines per inch
    th = np.radians(20)
    pitch = 13.0
    a = (-x * np.sin(th) + y * np.cos(th)) / pitch
    b = (x * np.sin(th) + y * np.cos(th)) / pitch
    fa, fb = a - np.floor(a) - 0.5, b - np.floor(b) - 0.5          # -0.5..0.5 across each groove interval
    h = np.minimum(0.5 - np.abs(fa), 0.5 - np.abs(fb)) * 2          # 0 in the cut, 1 at the diamond peak
    # facet orientation: which line family is nearer decides the facet; light from above (-v)
    useA = (0.5 - np.abs(fa)) < (0.5 - np.abs(fb))
    up = np.where(useA, -np.sign(fa), -np.sign(fb))                 # +1: facet tilted towards the light
    shade = 1.0 + 0.16 * up * np.clip(h * 3, 0, 1)
    cut = np.clip(1 - h / 0.22, 0, 1)
    img = base * (shade[..., None] * (1 - 0.6 * cut[..., None]))
    # panel border (double line) like a hand-cut checkering panel
    e = np.minimum(np.minimum(x, C - 1 - x), np.minimum(y, C - 1 - y))
    border = ((e > 14) & (e < 19)) | ((e > 24) & (e < 27))
    outside = e <= 14
    img[outside] = base[outside]
    img[border] = base[border] * 0.55
    return match_mean(img, (111, 63, 34), 0.8)


def polymer(base=(51, 52, 54), seed=51, amp=2.6):
    g = blur(white(C, C, seed), 0.7) * 1.6
    m = fbm(C, C, 80, 3, seed + 1)
    img = compose(col(base), (g, (amp, amp, amp * 1.05)), (m, (1.4, 1.4, 1.5)))
    return match_mean(img, base)


def bright():
    brush = fbm(C, C, 1.5, 2, 61, aniso=120)
    brush2 = fbm(C, C, 3, 2, 62, aniso=60)
    m = fbm(C, C, 120, 3, 63)
    img = compose(col((176, 178, 177)), (brush, (6, 6, 6)), (brush2, (4, 4, 4.2)), (m, (5, 5, 5.5)))
    return match_mean(img, (178, 180, 178))


def brass():
    brush = fbm(C, C, 2, 2, 71, aniso=80)
    tarnish = np.clip(fbm(C, C, 70, 4, 72), -1.5, 3)
    m = fbm(C, C, 160, 2, 73)
    img = compose(col((206, 164, 82)), (brush, (6, 5, 2.5)), (tarnish, (-9, -9, -6)), (m, (9, 7, 2)))
    return match_mean(img, (209, 165, 81))


def rubber():
    y, x = np.mgrid[0:C, 0:C].astype(np.float32)
    pitch = 16.0
    t = (y % pitch) / pitch
    rib = np.where(t < 0.62, np.clip(np.sin(t / 0.62 * np.pi), 0, 1) ** 0.5, -0.9)       # rounded rib, dark groove
    top = np.where(t < 0.3, 1.0, 0.0) * (t < 0.62)
    g = white(C, C, 81)
    img = compose(col((38, 38, 39)), (rib, (7, 7, 7)), (top, (5, 5, 5)), (g, (1.6, 1.6, 1.6)))
    return match_mean(img, (40, 40, 40))


def tan():
    return polymer((175, 151, 113), 91, 5.0) + fbm(C, C, 60, 3, 92)[..., None] * np.array((3, 2.6, 2), np.float32)


def anodized():
    blast = blur(white(C, C, 101), 0.6) * 1.8
    brush = fbm(C, C, 2, 2, 102, aniso=70)
    m = fbm(C, C, 110, 3, 103)
    img = compose(col((58, 60, 65)), (blast, (2.6, 2.6, 2.9)), (brush, (1.2, 1.2, 1.4)), (m, (1.4, 1.5, 1.8)))
    return match_mean(img, (60, 62, 67))


def orange():
    return polymer((232, 104, 30), 111, 5.5)


def case_hardened():
    """Colour case-hardened steel: sharp-edged swirls of slate blue, straw and plum on a grey base (domain warped)."""
    y, x = np.mgrid[0:C, 0:C].astype(np.float32)
    wx = fbm(C, C, 60, 4, 125) * 26
    wy = fbm(C, C, 60, 4, 126) * 26
    def warped(seed, s):
        n = value_noise(C + 80, C + 80, s, s, seed)
        yi = np.clip((y + wy + 40).astype(int), 0, C + 79)
        xi = np.clip((x + wx + 40).astype(int), 0, C + 79)
        return n[yi, xi]
    blue = np.tanh(warped(121, 28) * 2.2)
    straw = np.tanh(warped(122, 22) * 2.0)
    plum = np.tanh(warped(123, 34) * 1.8)
    g = white(C, C, 124)
    img = compose(col((117, 117, 117)), (blue, (-4, -1.5, 7)), (straw, (5, 3, -3.5)), (plum, (2, -2, 2)),
                  (fbm(C, C, 90, 3, 127), (5, 5, 5)), (g, (2.2, 2.2, 2.2)))
    return match_mean(img, (117, 117, 117))


def stipple():
    y, x = np.mgrid[0:C, 0:C].astype(np.float32)
    bumps = blur(np.clip(white(C, C, 131), 0.9, None) - 0.9, 1.6) * 9
    gy = np.gradient(bumps, axis=0)
    lit = -gy * 3.0 + bumps * 0.5
    img = compose(col((50, 50, 52)), (np.clip(lit, -3, 3), (6, 6, 6.2)), (white(C, C, 132), (1.4, 1.4, 1.4)))
    return match_mean(img, (52, 52, 54))


def glow(old):
    return np.asarray(Image.fromarray(old).resize((C, C), Image.BICUBIC)).astype(np.float32)


def paint():
    return compose(col((244, 244, 243)), (fbm(C, C, 50, 3, 141), (1.6, 1.6, 1.6)), (white(C, C, 142), (0.8, 0.8, 0.8)))


def birch():
    rings, late, figure, pores, sheen = walnut_field(151)
    img = compose(col((186, 148, 99)), (rings, (6, 5, 3.5)), (late, (-18, -15, -11)), (figure, (8, 6, 4)),
                  (pores * 0.5, (-12, -10, -7)), (sheen, (4, 3, 2)))
    return match_mean(img, (187, 149, 99))


# ----------------------------------------------------------------------------------------------- vanilla (MC) atlas
MCB = 16  # texels per cell


def mc_cell(kind, mean, seed):
    """16x16 Minecraft-style texel cell: small palette, per-texel dither, structural stripes where it helps."""
    r = np.random.default_rng(seed)
    m = np.array(mean, np.float32)
    t = np.zeros((MCB, MCB), np.float32)
    jit = r.integers(-1, 2, size=(MCB, MCB)).astype(np.float32)          # -1, 0, 1 dither
    if kind == 'wood':
        rows = np.array([0, 1, 1, 0, -1, 0, 1, 0, -2, 0, 1, 1, 0, -1, 0, 1], np.float32)
        t = rows[:, None] + (r.random((MCB, MCB)) < 0.12) * -1.0
        step = 0.075
    elif kind == 'check':
        y, x = np.mgrid[0:MCB, 0:MCB]
        t = np.where((x + y) % 4 == 0, -2.0, np.where((x - y) % 4 == 0, -2.0, 0.6)) + jit * 0.4
        step = 0.07
    elif kind == 'rib':
        y, x = np.mgrid[0:MCB, 0:MCB]
        t = np.where(y % 2 == 0, 1.0, -1.0) + jit * 0.3
        step = 0.12
    elif kind == 'flat':
        t = jit * 0.5
        step = 0.02
    elif kind == 'glow':
        y, x = np.mgrid[0:MCB, 0:MCB]
        t = np.clip(3 - np.hypot(x - 6.5, y - 6.5) / 2.2, -1, 3) + jit * 0.3
        step = 0.1
    else:  # metal / polymer
        t = jit + (r.random((MCB, MCB)) < 0.06) * 1.5
        step = 0.055 if kind == 'metal' else 0.035
    cell = m[None, None, :] * (1 + step * t[..., None])
    return cell


def build(out):
    old = np.asarray(Image.open('/tmp/claude-0/gunbench-base/assets/frontierhunts/textures/item/firearms_v2.png').convert('RGB'))
    glow_old = old[3 * 256:4 * 256, 1 * 256:2 * 256]
    cells = [[blued(), parkerized(), walnut(), checkered()],
             [polymer(), bright(), brass(), rubber()],
             [tan(), anodized(), orange(), case_hardened()],
             [stipple(), glow(glow_old), paint(), birch()]]
    atlas = np.zeros((4 * C, 4 * C, 3), np.float32)
    for r in range(4):
        for c in range(4):
            atlas[r * C:(r + 1) * C, c * C:(c + 1) * C] = cells[r][c]
    a8 = np.clip(atlas + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(a8, 'RGB').save(os.path.join(out, 'firearms_v2.png'), optimize=True)

    kinds = [['metal', 'metal', 'wood', 'check'], ['poly', 'metal', 'metal', 'rib'], ['poly', 'metal', 'flat', 'metal'], ['poly', 'glow', 'flat', 'wood']]
    mc = np.zeros((4 * MCB, 4 * MCB, 3), np.float32)
    for r in range(4):
        for c in range(4):
            mean = old[r * 256 + 20:r * 256 + 236, c * 256 + 20:c * 256 + 236].reshape(-1, 3).mean(0)
            mc[r * MCB:(r + 1) * MCB, c * MCB:(c + 1) * MCB] = mc_cell(kinds[r][c], mean, 500 + r * 4 + c)
    mc8 = np.clip(mc + 0.5, 0, 255).astype(np.uint8)
    Image.fromarray(mc8, 'RGB').resize((1024, 1024), Image.NEAREST).save(os.path.join(out, 'firearms_mc.png'), optimize=True)

    prev = Image.new('RGB', (1536, 768))
    prev.paste(Image.fromarray(a8).resize((768, 768), Image.LANCZOS), (0, 0))
    prev.paste(Image.fromarray(mc8).resize((768, 768), Image.NEAREST), (768, 0))
    prev.save(os.path.join(out, 'materials_preview.png'))
    for r in range(4):
        print([tuple(a8[r * C + 50:(r + 1) * C - 50, c * C + 50:(c + 1) * C - 50].reshape(-1, 3).mean(0).round().astype(int)) for c in range(4)])


if __name__ == '__main__':
    build(sys.argv[1])
