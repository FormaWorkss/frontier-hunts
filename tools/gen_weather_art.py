"""Procedural art for the seasonal-weather workstream (all original, generated here).

python3 tools/gen_weather_art.py [repo_root]
Writes particle sprites + particle definitions and the two GUI overlay textures under patch/assets/frontierhunts/.
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')
PT = os.path.join(A, 'textures', 'particle')
GUI = os.path.join(A, 'textures', 'gui', 'weather')
PD = os.path.join(A, 'particles')
for d in (PT, GUI, PD):
    os.makedirs(d, exist_ok=True)
rng = np.random.default_rng(20260927)


def save(arr, path):
    arr = np.clip(arr, 0, 1)
    Image.fromarray((arr * 255 + 0.5).astype(np.uint8), 'RGBA').save(path, optimize=True)


def periodic_noise(h, w, cells_y, cells_x, seed):
    """Tileable value noise with smooth (cosine) interpolation on a periodic lattice."""
    r = np.random.default_rng(seed)
    lat = r.random((cells_y, cells_x))
    ys = np.arange(h) / h * cells_y
    xs = np.arange(w) / w * cells_x
    y0 = np.floor(ys).astype(int); x0 = np.floor(xs).astype(int)
    fy = ys - y0; fx = xs - x0
    fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
    y1 = (y0 + 1) % cells_y; x1 = (x0 + 1) % cells_x
    a = lat[np.ix_(y0 % cells_y, x0 % cells_x)]; b = lat[np.ix_(y0 % cells_y, x1)]
    c = lat[np.ix_(y1, x0 % cells_x)]; d = lat[np.ix_(y1, x1)]
    fx = fx[None, :]; fy = fy[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(h, w, base_y, base_x, octaves, seed, gain=0.5):
    out = np.zeros((h, w)); amp = 1.0; tot = 0.0
    for o in range(octaves):
        out += amp * periodic_noise(h, w, base_y * 2 ** o, base_x * 2 ** o, seed + o * 101)
        tot += amp; amp *= gain
    return out / tot


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ streak (u across, v along; v0 = leading head)
def streak():
    w, h = 16, 64
    u = (np.arange(w) + 0.5) / w - 0.5
    v = (np.arange(h) + 0.5) / h
    across = np.exp(-(u / 0.2) ** 2)[None, :]
    along = (smooth(0.0, 0.22, v) * (1 - smooth(0.35, 1.0, v)) ** 1.3)[:, None]
    along = along / along.max()
    a = across * along
    img = np.ones((h, w, 4)); img[..., 3] = a
    save(img, os.path.join(PT, 'weather_streak.png'))


# ------------------------------------------------------------------ veils: very soft cloudy puffs
def veils():
    n = 128  # particle atlas magnifies with nearest filtering: keep enough texels that the soft edge never steps
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot((xx + 0.5) / n - 0.5, (yy + 0.5) / n - 0.5) * 2
    for i in range(4):
        f = fbm(n, n, 4, 4, 4, 300 + i * 17)
        g = fbm(n, n, 2, 2, 3, 900 + i * 31)
        warp = r + (g - 0.5) * 0.35
        mask = (1 - smooth(0.25, 1.0, warp)) ** 1.6
        dens = np.clip(0.55 + (f - 0.5) * 1.3, 0, 1)
        a = mask * dens
        a = a / max(1e-6, a.max()) * 0.95
        lum = 0.88 + 0.12 * f  # slightly brighter tops of the billows
        img = np.zeros((n, n, 4)); img[..., 0] = lum; img[..., 1] = lum; img[..., 2] = np.clip(lum + 0.015, 0, 1); img[..., 3] = a
        save(img, os.path.join(PT, f'weather_veil_{i}.png'))


# ------------------------------------------------------------------ flakes (8x8, Minecraft-style pixel crystals)
FLAKES = [
    ["........", "...#....", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "...#...."],
    ["........", "........", "...#....", "..###...", "...#....", "........", "........", "........"],
    ["........", "..#.#...", "...#....", ".#####..", "...#....", "..#.#...", "........", "........"],
    ["........", "........", "..##....", "..##....", "........", "........", "........", "........"],
]


def flakes():
    for i, rows in enumerate(FLAKES):
        img = np.zeros((8, 8, 4))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == '#':
                    img[y, x] = (1, 1, 1, 1)
        # soft 1px halo so they read at a distance
        a = img[..., 3]
        halo = np.zeros_like(a)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            halo = np.maximum(halo, np.roll(np.roll(a, dy, 0), dx, 1) * 0.35)
        img[..., :3] = 1.0
        img[..., 3] = np.maximum(a, halo)
        save(img, os.path.join(PT, f'weather_flake_{i}.png'))


# ------------------------------------------------------------------ leaves (8x8 pixel leaves, pale so they tint)
LEAVES = [
    ["........", ".....##.", "....###.", "...####.", "..####..", ".####...", ".#v##...", "#......."],
    ["...#....", "..###...", ".#####..", ".##v##..", ".#####..", "..###...", "...#....", "...#...."],
    ["........", "..##.##.", ".#######", ".###v###", "..#####.", "...###..", "....#...", "....#..."],
    ["........", "....##..", "...####.", "..#####.", "..##v##.", "..####..", "...##...", "..#....."],
    ["........", "...##...", "..####..", ".##v###.", ".######.", "..####..", "...##...", "........"],
    ["........", ".###....", "#####...", "####v#..", ".######.", "..#####.", "....###.", ".......#"],
]


def leaves():
    for i, rows in enumerate(LEAVES):
        img = np.zeros((8, 8, 4))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in '#v':
                    shade = 0.78 if ch == 'v' else 1.0
                    # darker rim on the lower-right edge for a bit of form
                    below = y + 1 < 8 and rows[y + 1][x] in '#v'
                    right = x + 1 < 8 and rows[y][x + 1] in '#v'
                    if not below or not right:
                        shade *= 0.82
                    img[y, x] = (shade, shade, shade, 1)
        save(img, os.path.join(PT, f'weather_leaf_{i}.png'))


# ------------------------------------------------------------------ GUI: tileable gust sheet (horizontal wisps)
def gust_sheet():
    h, w = 256, 256
    base = fbm(h, w, 24, 3, 5, 4242, gain=0.55)            # stretched horizontally: few cells across, many down
    fine = fbm(h, w, 64, 6, 3, 777, gain=0.6)
    wisps = smooth(0.47, 0.78, base) * (0.55 + 0.45 * fine)
    grain = fbm(h, w, 64, 32, 2, 99)
    a = np.clip(wisps * 0.9 + smooth(0.7, 0.95, grain) * 0.12, 0, 1)
    img = np.ones((h, w, 4)); img[..., 3] = a
    save(img, os.path.join(GUI, 'gust_sheet.png'))
    json.dump({"texture": {"blur": True, "clamp": False}}, open(os.path.join(GUI, 'gust_sheet.png.mcmeta'), 'w'))


# ------------------------------------------------------------------ GUI: frost creeping in from the edges
def frost():
    n = 512
    rr = np.random.default_rng(5150)
    layer = Image.new('L', (n, n), 0)
    dr = ImageDraw.Draw(layer)

    def branch(x, y, ang, length, width, depth):
        """Feathery frost fern: a spine with dense short side needles at ~60 degrees."""
        if depth > 3 or length < 3:
            return
        steps = max(2, int(length / 3))
        for s in range(steps):
            nx = x + math.cos(ang) * length / steps
            ny = y + math.sin(ang) * length / steps
            dr.line([(x, y), (nx, ny)], fill=int(170 + 80 * rr.random()), width=max(1, int(round(width))))
            if depth < 3 and rr.random() < 0.8:
                side = 1 if (s % 2 == 0) else -1
                branch(nx, ny, ang + side * (math.pi / 3) * (0.85 + 0.3 * rr.random()), length * (0.18 + 0.12 * rr.random()) * (1 - s / steps * 0.6),
                       max(1.0, width * 0.7), depth + 1)
            x, y = nx, ny
            ang += (rr.random() - 0.5) * 0.18

    # [blizzard2] toned down: shorter ferns, thinner band hugging the edges, softer contrast, clear centre
    for _ in range(150):
        edge = rr.integers(4)
        t = rr.random() * n
        if edge == 0: x, y, ang = t, 0, math.pi / 2
        elif edge == 1: x, y, ang = t, n, -math.pi / 2
        elif edge == 2: x, y, ang = 0, t, 0.0
        else: x, y, ang = n, t, math.pi
        ang += (rr.random() - 0.5) * 1.4
        branch(x, y, ang, 20 + rr.random() * 52, 1.5, 0)
    crystals = np.asarray(layer.filter(ImageFilter.GaussianBlur(0.9)), dtype=np.float64) / 255.0
    glow = np.asarray(layer.filter(ImageFilter.GaussianBlur(7)), dtype=np.float64) / 255.0
    yy, xx = np.mgrid[0:n, 0:n]
    ex = np.minimum(xx, n - 1 - xx) / n
    ey = np.minimum(yy, n - 1 - yy) / n
    edge = np.minimum(ex * 1.25, ey * 1.6)                   # wider short sides
    warp = fbm(n, n, 6, 6, 4, 31337)
    e = edge + (warp - 0.5) * 0.05
    mask = 1 - smooth(0.0, 0.14, e)
    corner = 1 - smooth(0.03, 0.26, np.hypot(ex, ey))
    mask = np.clip(mask + corner * 0.45, 0, 1)
    speck = smooth(0.6, 0.9, fbm(n, n, 64, 64, 2, 8080))
    haze = fbm(n, n, 12, 12, 4, 1234)
    a = mask * np.clip(crystals * 0.85 + glow * 0.55 + haze * 0.22 + speck * 0.12, 0, 1)
    a = np.clip(a * (0.3 + 0.7 * mask), 0, 0.85)
    # never anything in the middle of the view (crosshair, scope, the animal you are looking at)
    cx = (xx / (n - 1) - 0.5) * 2.0
    cy = (yy / (n - 1) - 0.5) * 2.0
    a = a * smooth(0.62, 0.8, np.hypot(cx, cy * 1.1))
    img = np.zeros((n, n, 4))
    img[..., 0] = 0.9 + 0.1 * crystals
    img[..., 1] = 0.94 + 0.06 * crystals
    img[..., 2] = 1.0
    img[..., 3] = a
    save(img, os.path.join(GUI, 'frost_vignette.png'))
    json.dump({"texture": {"blur": True, "clamp": True}}, open(os.path.join(GUI, 'frost_vignette.png.mcmeta'), 'w'))


def defs():
    d = {
        'weather_streak': ['frontierhunts:weather_streak'],
        'weather_veil': [f'frontierhunts:weather_veil_{i}' for i in range(4)],
        'weather_flake': [f'frontierhunts:weather_flake_{i}' for i in range(4)],
        'weather_leaf': [f'frontierhunts:weather_leaf_{i}' for i in range(6)],
    }
    for k, v in d.items():
        json.dump({"textures": v}, open(os.path.join(PD, k + '.json'), 'w'), indent=2)


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[2] == 'frost':  # [blizzard2] regenerate only the frost vignette
        frost(); sys.exit(0)
    streak(); veils(); flakes(); leaves(); gust_sheet(); frost(); defs()
    print('weather art written to', A)
