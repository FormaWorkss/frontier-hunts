"""[academy] Ranger dossier art: 768x384 painted golden-hour plates per course / assignment type and a 512x512
icon atlas (8x8 cells of 64 px). Uses the Field School watercolour engine (tools/guide/fieldart.py etc.), all original.

python3 tools/academy/gen_art.py <repo root> [name ...]
"""
import os, sys, math, random
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'guide'))
from fieldart import *
from whitetail import whitetail
from scenery import *

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
ONLY = set(sys.argv[2:])
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/academy')
PREV = os.path.join(ROOT, 'docs/ws/academy')
os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)
W, H = 768, 384
S = 2

def rect(pl, x0, y0, x1, y1):
    return pl.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], smooth=False)

def sky(pl, horizon=0.52, warm=1.0, seed=1):
    full = rect(pl, 0, 0, W, H)
    pl.wash(full, (236, 200, 150), 1.0, edge=0, var=0.03, grain=0.05,
            grad=([(0, (128, 162, 190)), (0.45, (222, 204, 176)), (0.75, mix((246, 196, 132), (232, 210, 170), 1 - warm)), (1, (238, 188, 130))], (0, 0, 0, H * horizon)))
    rng = random.Random(seed)
    for _ in range(4):
        x, y, w_ = rng.uniform(40, W - 40), rng.uniform(20, H * horizon * 0.5), rng.uniform(90, 200)
        pl.wash(pl.blur(pl.ellipse(x, y, w_, 6), 5), (250, 228, 200), 0.55, edge=0)

def sun(pl, x, y, r=26):
    pl.wash(pl.blur(pl.ellipse(x, y, r * 4, r * 4), 30), (255, 230, 180), 0.6, edge=0)
    pl.wash(pl.ellipse(x, y, r, r), (255, 242, 210), 0.95, edge=0)

def ridges(pl, base, seed=3, layers=2):
    rng = random.Random(seed)
    cols = [(150, 150, 172), (112, 120, 140)]
    for li in range(layers):
        b = base - 26 + li * 16; amp = 22 - li * 6; col = cols[li % 2]
        f = lambda x, li=li, b=b, amp=amp: b - amp * (0.5 + 0.5 * math.sin(x * 0.008 + li * 2.3 + seed)) - amp * 0.4 * math.sin(x * 0.021 + li)
        m = pl.poly([(0, H)] + [(x, f(x)) for x in range(0, W + 8, 8)] + [(W, H)], smooth=False)
        pl.wash(m, col, 1.0, edge=0.1, var=0.05)
        for x in range(-6, W + 6, 9 + li * 3):
            if rng.random() < 0.3: continue
            hh = rng.uniform(10, 20) * (1 + li * 0.4)
            conifer(pl, x + rng.uniform(-3, 3), f(x) + 3, hh, color=darker(col, 0.85), seed=x * 7 + li, ink=False, detail=False)
        pl.wash(pl.blur(rect(pl, 0, b + 4, W, b + 26), 8), (240, 222, 200), 0.45, edge=0)

def treeline(pl, gy, x0, x1, step=16, hmin=70, hmax=150, seed=5, col=PINE, ink=True):
    rng = random.Random(seed)
    x = x0
    while x < x1:
        if rng.random() < 0.8:
            conifer(pl, x + rng.uniform(-4, 4), gy, rng.uniform(hmin, hmax), color=mix(col, (230, 200, 150), rng.uniform(0.0, 0.18)), seed=int(x), ink=ink, detail=False)
        else:
            broadleaf(pl, x, gy, rng.uniform(hmin * 0.8, hmax * 0.8), color=mix(FOLIAGE, (220, 180, 90), 0.25), seed=int(x), ink=ink)
        x += step * rng.uniform(0.7, 1.3)

def ground(pl, gy, col=(176, 166, 104), seed=7, tuft=60):
    m = pl.poly([(0, gy), (W, gy - 4), (W, H), (0, H)], smooth=False)
    pl.wash(m, col, 1.0, edge=0.1, var=0.16, grad=([(0, darker(col, 0.92)), (1, lighter(col, 0.08))], (0, gy, 0, H)))
    pl.strokes(m, 900, darker(col, 0.62), length=(2, 6), width=(0.4, 0.8), opacity=0.35, angle=lambda a, b: -math.pi / 2, jitter=0.4)
    if tuft: tufts(pl, 10, W - 10, gy + 2, tuft, seed=seed, h=(6, 14))

def deer(pl, x, gy, s, facing=1, buck=True, neck=0.0, head=0.0, detail=0.6, rim=True):
    d = whitetail(pl, x, gy, s, facing=facing, buck=buck, neck_ang=neck, head_ang=head, detail=detail)
    if rim:
        pl.wash(pl.blur(pl.minus(d['body'], pl.grow(d['body'], -2)), 0.6), (252, 214, 150), 0.35, edge=0)
    return d

def rotate_pts(pts, pivot, ang):
    c, s_ = math.cos(ang), math.sin(ang)
    return [(pivot[0] + (x - pivot[0]) * c - (y - pivot[1]) * s_, pivot[1] + (x - pivot[0]) * s_ + (y - pivot[1]) * c) for x, y in pts]


def downed_buck(pl, x, gy, s, facing=1, detail=0.6):
    """[artqa] A buck lying dead on its side (head down at the ground, legs out) - same pose as the Field School harvest plate."""
    from whitetail import FRONT_LEG, HIND_LEG
    fa, ha = -1.47, 1.40
    front = rotate_pts(FRONT_LEG, (0.20, 0.55), fa)
    hind = rotate_pts(HIND_LEG, (0.95, 0.58), ha)
    box = [(-0.4, 0.05), (1.6, 0.05), (1.6, -0.1), (-0.4, -0.1)]
    hooves = [rotate_pts(box, (0.20, 0.55), fa), rotate_pts(box, (0.95, 0.58), ha)]
    return whitetail(pl, x, gy + 0.47 * s, s, facing=facing, neck_ang=1.2, head_ang=-0.8, legs={'front': front, 'hind': hind},
                     hooves=hooves, far_legs=False, detail=detail)


def game_bag(pl, x, top, h):
    """[artqa] A deer quarter hanging in a white cloth game bag (cord at the top, faint blood seep)."""
    w = h * 0.42
    bag = pl.poly([(x - w * 0.22, top + 6), (x + w * 0.22, top + 6), (x + w * 0.55, top + h * 0.35), (x + w * 0.5, top + h * 0.8),
                   (x + w * 0.1, top + h), (x - w * 0.35, top + h * 0.9), (x - w * 0.52, top + h * 0.5)])
    pl.wash(bag, (232, 226, 210), 1.0, edge=0.2, var=0.06)
    pl.wash(pl.inter(pl.blur(pl.ellipse(x + w * 0.05, top + h * 0.62, w * 0.22, h * 0.14), 4), bag), (170, 70, 60), 0.35, edge=0)
    pl.form(bag, radius=5, dark=(120, 110, 96), strength=0.5, hi=WHITE, hi_strength=0.2)
    pl.ink([(x - w * 0.22, top + 9), (x + w * 0.22, top + 9)], 1.4, (120, 100, 70), 0.9, smooth=False)
    pl.contour(bag, 0.5, 1.2)
    pl.ink([(x, top - 4), (x, top + 8)], 1.4, (90, 80, 60), 1, smooth=False)


def vignette(pl):
    full = rect(pl, 0, 0, W, H)
    inner = pl.blur(rect(pl, 40, 30, W - 40, H - 30), 40)
    pl.flat(pl.minus(full, inner), (40, 28, 18), 0.28)

def save(pl, name):
    img = pl.image()
    img = img.convert('RGBA')
    from PIL import Image
    bg = Image.new('RGBA', img.size, (234, 227, 208, 255))
    bg.alpha_composite(img)
    bg.convert('RGB').save(os.path.join(OUT, 'plate_' + name + '.png'), optimize=True)
    bg.convert('RGB').resize((384, 192)).save(os.path.join(PREV, name + '.jpg'), quality=85)
    print('wrote', name)

def want(n): return not ONLY or n in ONLY

# ---------------------------------------------------------------------------------------------- plates
def p_glassing():
    pl = Plate(W, H, seed=11, S=S); sky(pl, 0.55); sun(pl, 640, 150); ridges(pl, 205, 4)
    gy = 262; treeline(pl, gy, 330, W + 20, 18, 60, 120, seed=2, ink=False)
    ground(pl, gy, seed=3)
    # lookout tower (left)
    tx, top = 120, 150
    for lx in (tx - 34, tx + 34):
        pl.ink([(lx, H), (lx + (8 if lx < tx else -8), top)], 4.5, BARK_DARK, 1.0, smooth=False)
    pl.ink([(tx - 30, 250), (tx + 28, 200)], 2.4, BARK, 0.9, smooth=False); pl.ink([(tx + 30, 250), (tx - 28, 200)], 2.4, BARK, 0.9, smooth=False)
    deck = rect(pl, tx - 44, top - 6, tx + 44, top + 4); pl.wash(deck, BARK, 1.0); pl.contour(deck, 0.6, 1.4)
    roof = pl.poly([(tx - 52, top - 54), (tx + 52, top - 54), (tx + 44, top - 64), (tx - 44, top - 64)], smooth=False)
    pl.wash(roof, (90, 70, 52), 1.0); pl.contour(roof, 0.6, 1.4)
    for lx in (tx - 40, tx + 40): pl.ink([(lx, top - 6), (lx, top - 56)], 3.0, BARK_DARK, 1.0, smooth=False)
    pl.ink([(tx - 44, top - 22), (tx + 44, top - 22)], 2.0, BARK, 0.9, smooth=False)
    h = hunter(pl, tx + 6, top - 6, 30, facing=1, pose='stand_glass', weapon=None)
    # deer in the meadow
    deer(pl, 470, 300, 46, facing=1, buck=True, neck=-0.05)
    deer(pl, 610, 286, 34, facing=-1, buck=False, neck=1.1, head=0.5)
    deer(pl, 700, 272, 26, facing=1, buck=False, neck=0.9)
    bush(pl, 560, 300, 26, seed=4); bush(pl, 380, 318, 20, seed=8, color=mix(FOLIAGE, GRASS, 0.4))
    vignette(pl); save(pl, 'glassing')

def p_stalk():
    pl = Plate(W, H, seed=22, S=S); sky(pl, 0.5); sun(pl, 700, 120); ridges(pl, 190, 7)
    gy = 300; treeline(pl, gy - 30, 420, W + 20, 16, 70, 130, seed=6, ink=False)
    ground(pl, gy, col=(180, 168, 100), seed=4, tuft=90)
    for i, y in enumerate([70, 92, 114]):
        pts = [(x, y + math.sin(x * 0.03 + i) * 3) for x in np.linspace(560 - i * 20, 260 - i * 20, 12)]
        pl.arrow(pts, 1.8, WIND_BLUE, 9, 0.85)
    deer(pl, 590, gy, 70, facing=-1, buck=False, neck=1.15, head=0.5, detail=0.7)
    bush(pl, 360, gy + 4, 48, seed=5); bush(pl, 420, gy + 4, 30, seed=9, color=mix(FOLIAGE, GRASS, 0.4))
    h = hunter(pl, 190, gy, 82, facing=1, pose='crouch')
    bow(pl, h['X'], h['J']['hand_f'], ang=-0.12, size=1.3)
    vignette(pl); save(pl, 'stalk')

def target_face(pl, cx, cy, r):
    for rr, col in ((1.0, (236, 230, 214)), (0.82, (40, 40, 40)), (0.66, (60, 104, 168)), (0.44, (196, 60, 46)), (0.25, (232, 196, 60))):
        m = pl.ellipse(cx, cy, r * rr, r * rr); pl.wash(m, col, 1.0, edge=0.05, var=0.03)
    pl.contour(pl.ellipse(cx, cy, r, r), 0.6, 1.2)

def p_range():
    pl = Plate(W, H, seed=33, S=S); sky(pl, 0.5); sun(pl, 120, 130, 22); ridges(pl, 190, 9)
    gy = 270; treeline(pl, gy - 6, -10, W + 20, 20, 50, 100, seed=3, ink=False)
    ground(pl, gy, col=(170, 172, 100), seed=6, tuft=40)
    berm = pl.poly([(0, gy - 4), (W, gy - 8), (W, gy + 6), (0, gy + 8)]); pl.wash(berm, (120, 120, 74), 0.9)
    # targets at distance
    for x, y, r in ((300, 248, 12), (470, 240, 8), (600, 236, 5)):
        pl.ink([(x - r, y + r + 1), (x - r, y + r * 2.6)], 1.4, BARK_DARK, 1, smooth=False); pl.ink([(x + r, y + r + 1), (x + r, y + r * 2.6)], 1.4, BARK_DARK, 1, smooth=False)
        target_face(pl, x, y, r)
    # big target close, with holes
    target_face(pl, 640, 250, 58)
    for hx, hy in ((632, 246), (646, 258), (628, 262), (655, 238)):
        pl.flat(pl.ellipse(hx, hy, 2.2, 2.2), (20, 18, 16), 1.0)
    pl.ink([(600, 312), (600, 380)], 6, BARK_DARK, 1, smooth=False); pl.ink([(680, 312), (680, 380)], 6, BARK_DARK, 1, smooth=False)
    # flag streaming on a pole
    pl.ink([(400, 300), (400, 168)], 2.6, BARK_DARK, 1, smooth=False)
    flag = pl.poly([(400, 170), (452, 176 + 4), (446, 188), (400, 196)]); pl.wash(flag, (204, 60, 40), 1.0); pl.contour(flag, 0.5, 1.2)
    # prone rifleman foreground
    h = hunter(pl, 150, 345, 70, facing=1, pose='prone', weapon='rifle')
    rifle(pl, h['X'], (0.66, 0.29), (1.70, 0.35), bipod=0.0)   # [artqa] the prone shooter had no rifle
    vignette(pl); save(pl, 'range')

def blood_drops(pl, pts, col=(92, 16, 18)):   # [artqa] liver blood: dark red
    rng = random.Random(4)
    for (x, y) in pts:
        r = rng.uniform(2.2, 4.2)
        pl.wash(pl.ellipse(x, y, r * 1.3, r * 0.8), col, 0.95, edge=0.3)

def p_tracking():
    pl = Plate(W, H, seed=44, S=S)
    full = rect(pl, 0, 0, W, H)
    pl.wash(full, (150, 140, 110), 1.0, edge=0, var=0.05, grad=([(0, (196, 178, 136)), (0.5, (120, 116, 86)), (1, (98, 82, 58))], (0, 0, 0, H)))
    for i, x in enumerate([40, 150, 280, 520, 640, 730, 90, 600]):
        hh = 260 + (i * 37) % 120
        conifer(pl, x, 250 + (i % 3) * 10, hh, color=mix(PINE, (90, 100, 80), 0.3 if i > 5 else 0.0), seed=i, ink=i < 6, detail=False)
    # light shafts
    for x in (300, 420, 500):
        pl.wash(pl.blur(pl.poly([(x, 0), (x + 40, 0), (x - 60, H), (x - 120, H)], smooth=False), 16), (255, 226, 160), 0.18, edge=0)
    floor = pl.poly([(0, 250), (W, 240), (W, H), (0, H)], smooth=False)
    pl.wash(floor, (120, 92, 60), 1.0, edge=0.1, var=0.2)
    leaf_litter(pl, W / 2, 320, W / 2, 70, 300, seed=4)
    trail = [(120, 370), (220, 340), (300, 320), (380, 300), (460, 284), (540, 270), (620, 258)]
    pts = []
    for i in range(len(trail) - 1):
        for t_ in (0.2, 0.6):
            pts.append((trail[i][0] + (trail[i + 1][0] - trail[i][0]) * t_, trail[i][1] + (trail[i + 1][1] - trail[i][1]) * t_ + random.Random(i).uniform(-6, 6)))
    blood_drops(pl, pts)
    bed = pl.ellipse(560, 266, 30, 9); pl.wash(bed, (96, 76, 52), 0.8); pl.wash(pl.ellipse(566, 266, 9, 3.5), (100, 20, 22), 0.9)
    downed_buck(pl, 640, 262, 40, facing=-1, detail=0.4)   # [artqa] the trail ends at the downed buck (was standing)
    vignette(pl); save(pl, 'tracking')

def p_dressing():
    pl = Plate(W, H, seed=55, S=S); sky(pl, 0.45, 1.0, 4); sun(pl, 650, 140)
    gy = 280; treeline(pl, gy - 10, -10, W + 20, 18, 90, 170, seed=7)
    ground(pl, gy, col=(150, 126, 86), seed=2, tuft=30)
    # game pole with two quarters hanging in cloth game bags ([artqa] were featureless red ovals)
    for x in (470, 640): pl.ink([(x, gy + 6), (x, 150)], 5, BARK_DARK, 1, smooth=False)
    pl.ink([(462, 156), (648, 152)], 5, BARK, 1, smooth=False)
    for x in (522, 588):
        game_bag(pl, x, 158, 66)
    # campfire and smoke
    fx = 120  # [artqa] moved clear of the kneeling hunter
    for i in range(5): pl.ink([(fx - 12 + i * 6, gy + 8), (fx + 8 - i * 3, gy - 4)], 2.4, BARK_DARK, 1, smooth=False)
    pl.wash(pl.ellipse(fx, gy - 8, 10, 14), (240, 150, 50), 0.9); pl.wash(pl.ellipse(fx, gy - 6, 5, 8), (255, 220, 120), 0.9)
    for i in range(5): pl.wash(pl.blur(pl.ellipse(fx + 4 + i * 9, gy - 40 - i * 26, 14 + i * 4, 10 + i * 3), 6), (220, 214, 206), 0.35, edge=0)
    # hunter kneeling at the downed buck, knife in hand ([artqa] the buck was standing, alive)
    d = downed_buck(pl, 372, gy + 34, 78, facing=-1)
    h = hunter(pl, 236, gy + 36, 70, facing=1, pose="kneel", weapon=None)
    hx_, hy_ = h['X'].p(*h['J']['hand_f'])
    pl.ink([(hx_, hy_), (hx_ + 10, hy_ + 5)], (1.6, 1.2, 0.6), (200, 204, 208), 1.0, smooth=False)
    vignette(pl); save(pl, 'dressing')

def p_harvest():
    pl = Plate(W, H, seed=66, S=S); sky(pl, 0.5); sun(pl, 600, 150); ridges(pl, 200, 2)
    gy = 290; treeline(pl, gy - 20, 360, W + 20, 16, 80, 160, seed=9)
    ground(pl, gy, seed=5, tuft=80)
    deer(pl, 560, gy - 12, 54, facing=-1, buck=True, neck=-0.05)
    h = hunter(pl, 170, gy + 20, 96, facing=1, pose='crouch')
    bow(pl, h['X'], h['J']['hand_f'], ang=-0.2, size=1.3, drawn=0.8)
    vignette(pl); save(pl, 'harvest')

def p_track():
    pl = Plate(W, H, seed=77, S=S)
    full = rect(pl, 0, 0, W, H)
    pl.wash(full, (150, 122, 88), 1.0, edge=0, var=0.14, grad=([(0, (172, 146, 110)), (1, (120, 92, 64))], (0, 0, 0, H)))
    pl.stipple(full, 900, (90, 68, 46), r=(0.4, 1.2), opacity=0.5)
    import tracks as TR
    for i, (x, y, a) in enumerate([(120, 300, -0.6), (230, 250, -0.5), (340, 210, -0.55), (450, 170, -0.5), (560, 130, -0.55), (670, 95, -0.5)]):
        TR.impression(pl, TR.cloven(9.0, 6.6), x, y, 3.0, rot=a, mud=SOIL)
        TR.impression(pl, TR.cloven(8.6, 6.2), x + 46, y + 30, 3.0, rot=a, mud=SOIL)
    leaf_litter(pl, W / 2, H / 2, W / 2, H / 2, 120, seed=7)
    vignette(pl); save(pl, 'track')

def p_timed():
    pl = Plate(W, H, seed=88, S=S); sky(pl, 0.62, 1.0, 9); sun(pl, 420, 222, 34); ridges(pl, 230, 5)
    gy = 300; treeline(pl, gy - 14, -10, W + 20, 22, 60, 130, seed=3, col=(54, 60, 50), ink=False)
    ground(pl, gy, col=(150, 120, 70), seed=8, tuft=70)
    d = deer(pl, 420, gy - 2, 70, facing=-1, buck=True, neck=-0.1)
    pl.wash(pl.blur(d['body'], 0.5), (50, 40, 34), 0.8, edge=0)
    pl.wash(pl.blur(pl.minus(d['body'], pl.grow(d['body'], -3)), 0.8), (255, 210, 140), 0.85, edge=0)
    vignette(pl); save(pl, 'timed')

def p_clean():
    pl = Plate(W, H, seed=99, S=S); sky(pl, 0.5, 0.8, 2); ridges(pl, 200, 8)
    gy = 330; ground(pl, gy, seed=2, tuft=50)
    d = deer(pl, 330, gy, 190, facing=1, buck=True, detail=0.7)
    X = d['X']
    from whitetail import VITALS
    cx, cy = X.p(*VITALS['aim'])   # [artqa] was X.p(0.12, 0.92) - high on the shoulder blade, a non-vital hit in the mod
    pl.wash(pl.ellipse(cx, cy, 34, 30), (230, 90, 80), 0.35, edge=0.3)
    for r in (18, 34):
        pl.contour(pl.ellipse(cx, cy, r, r), 0.6, 1.0, color=(200, 40, 30), opacity=0.9)
    pl.ink([(cx - 50, cy), (cx + 50, cy)], 1.0, (200, 40, 30), 0.9, smooth=False); pl.ink([(cx, cy - 50), (cx, cy + 50)], 1.0, (200, 40, 30), 0.9, smooth=False)
    vignette(pl); save(pl, 'clean')

def p_trophy():
    pl = Plate(W, H, seed=111, S=S); sky(pl, 0.5, 1.0, 6); sun(pl, 180, 140, 22)
    gy = 330; treeline(pl, gy - 30, -10, W + 20, 14, 140, 260, seed=11)
    ground(pl, gy, col=(150, 132, 84), seed=3, tuft=60)
    deer(pl, 420, gy, 150, facing=-1, buck=True, neck=-0.12, detail=0.9)
    vignette(pl); save(pl, 'trophy')

def p_supply():
    pl = Plate(W, H, seed=122, S=S); sky(pl, 0.45, 0.9, 3)
    gy = 280; treeline(pl, gy - 8, -10, W + 20, 18, 100, 170, seed=4)
    ground(pl, gy, col=(150, 130, 90), seed=1, tuft=20)
    # cabin wall + contract board
    wall = rect(pl, 60, 120, 420, gy + 4); pl.wash(wall, BARK, 1.0, var=0.12)
    for y in range(126, gy, 16): pl.ink([(60, y), (420, y + 1)], 1.6, BARK_DARK, 0.9, smooth=False)
    board = rect(pl, 150, 150, 330, 250); pl.wash(board, (196, 170, 120), 1.0); pl.contour(board, 0.6, 1.6)
    for i, (x, y) in enumerate(((170, 162), (240, 170), (200, 205), (270, 210))):
        p = pl.poly([(x, y), (x + 50, y + 2), (x + 48, y + 34), (x - 2, y + 32)], smooth=False); pl.wash(p, (240, 232, 210), 1.0); pl.contour(p, 0.4, 0.8)
        for k in range(3): pl.ink([(x + 6, y + 9 + k * 7), (x + 40, y + 10 + k * 7)], 0.8, INK_SOFT, 0.7, smooth=False)
    # crate with venison parcels and a leather roll
    crate = rect(pl, 470, 230, 640, gy + 20); pl.wash(crate, (160, 124, 82), 1.0); pl.contour(crate, 0.6, 1.4)
    for x in (470, 640): pl.ink([(x, 230), (x, gy + 20)], 2, BARK_DARK, 1, smooth=False)
    for x in (500, 560): q = pl.ellipse(x, 222, 26, 14); pl.wash(q, (226, 214, 190), 1.0); pl.contour(q, 0.4, 1.0); pl.ink([(x, 208), (x, 236)], 1.2, (120, 80, 40), 0.9, smooth=False)
    roll = pl.ellipse(610, 220, 24, 12); pl.wash(roll, (170, 110, 60), 1.0); pl.contour(roll, 0.4, 1.0)
    vignette(pl); save(pl, 'supply')

def p_archery():
    """[onboard] The Archery Range: an archer at full draw on the shooting line, a 3D foam deer with arrows in the
    vitals, a bag target on its stand down range, the ladder tree stand on the left and a wind flag."""
    pl = Plate(W, H, seed=133, S=S); sky(pl, 0.5, 1.0, 5); sun(pl, 640, 120, 24); ridges(pl, 196, 6)
    gy = 278; treeline(pl, gy - 8, -10, W + 20, 18, 60, 130, seed=8, ink=False)
    ground(pl, gy, col=(172, 168, 100), seed=9, tuft=60)
    # mown lane
    lane = pl.poly([(300, gy + 4), (760, gy - 2), (760, gy + 22), (260, gy + 40)]); pl.wash(lane, (150, 158, 92), 0.5, edge=0.1)
    # bag target on a stand, far
    bx, by = 470, 236
    for lx in (bx - 14, bx + 14):
        pl.ink([(lx, by + 14), (lx + (4 if lx < bx else -4), gy + 4)], 2.2, BARK_DARK, 1.0, smooth=False)
    bag = rect(pl, bx - 16, by - 16, bx + 16, by + 16); pl.wash(bag, (226, 214, 180), 1.0, var=0.05); pl.contour(bag, 0.5, 1.2)
    target_face(pl, bx, by, 11)
    for ax, ay in ((bx - 2, by - 1), (bx + 4, by + 3)):
        pl.ink([(ax, ay), (ax - 9, ay + 2)], 0.9, (60, 44, 30), 1.0, smooth=False)
        pl.ink([(ax - 9, ay + 2), (ax - 12, ay + 0.5)], 1.6, (200, 60, 40), 0.9, smooth=False)
    # distance post
    pl.ink([(420, gy + 10), (420, gy - 22)], 2.4, BARK_DARK, 1, smooth=False)
    sign = rect(pl, 408, gy - 34, 432, gy - 22); pl.wash(sign, (232, 222, 196), 1.0); pl.contour(sign, 0.4, 1.0)
    for k in range(2): pl.ink([(413 + k * 8, gy - 30), (417 + k * 8, gy - 26)], 1.0, INK_SOFT, 0.9, smooth=False)
    # 3D foam deer target with painted vital rings and two arrows in the lungs
    d = deer(pl, 600, gy + 26, 66, facing=-1, buck=True, rim=False, detail=0.4)
    foam = pl.blur(d['body'], 0.4)
    pl.wash(foam, (196, 170, 120), 0.55, edge=0)
    X = d['X']
    from whitetail import VITALS
    cx, cy = X.p(*VITALS['aim'])   # [integ6] artqa's vitals aim point (was the shoulder blade)
    for r, col in ((15, (230, 226, 210)), (9, (196, 60, 46))):
        pl.contour(pl.ellipse(cx, cy, r, r * 0.9), 0.6, 1.2, color=col, opacity=0.95)
    for (ox, oy, ln) in ((-3, -2, 30), (4, 3, 26)):
        hx, hy = cx + ox, cy + oy
        pl.ink([(hx, hy), (hx - ln, hy - ln * 0.08)], 1.1, (52, 38, 26), 1.0, smooth=False)
        tx, ty = hx - ln, hy - ln * 0.08
        pl.ink([(tx + 6, ty), (tx, ty - 3)], 1.6, (226, 210, 160), 0.95, smooth=False)
        pl.ink([(tx + 6, ty + 0.5), (tx, ty + 3.5)], 1.6, (200, 60, 40), 0.95, smooth=False)
    # ladder tree stand on the left, backed by a pine
    conifer(pl, 70, gy + 6, 220, color=PINE, seed=4, ink=True, detail=False)
    sx, top = 120, 150
    for lx in (sx - 22, sx + 18):
        pl.ink([(lx, gy + 8), (lx + (6 if lx < sx else -6), top + 6)], 3.2, BARK_DARK, 1.0, smooth=False)
    for k in range(7):
        y_ = gy - 8 - k * 18
        pl.ink([(sx - 20 + k * 0.85, y_), (sx + 16 - k * 0.85, y_)], 1.8, BARK, 0.95, smooth=False)
    deck = rect(pl, sx - 26, top - 2, sx + 24, top + 6); pl.wash(deck, BARK, 1.0); pl.contour(deck, 0.5, 1.2)
    pl.ink([(sx - 24, top - 22), (sx + 22, top - 22)], 2.0, BARK_DARK, 0.95, smooth=False)
    for lx in (sx - 24, sx + 22): pl.ink([(lx, top), (lx, top - 22)], 2.0, BARK_DARK, 0.95, smooth=False)
    # wind flag
    pl.ink([(350, gy + 4), (350, 170)], 2.4, BARK_DARK, 1, smooth=False)
    flag = pl.poly([(350, 172), (394, 180), (388, 192), (350, 198)]); pl.wash(flag, (204, 60, 40), 1.0); pl.contour(flag, 0.5, 1.2)
    # the archer at full draw, foreground
    h = hunter(pl, 236, gy + 64, 104, facing=1, pose='stand_draw', weapon='bow', blaze=False)
    Xh, J = h['X'], h['J']
    bow(pl, Xh, J['hand_f'], ang=0.0, size=1.42, drawn=0.44)
    ax0, ay0 = J['hand_b'][0] - 0.02, J['hand_f'][1]
    pl.ink(Xh([(ax0, ay0), (J['hand_f'][0] + 0.2, ay0)]), 1.0, (52, 38, 26), 1.0, smooth=False)
    tip = J['hand_f'][0] + 0.2
    pl.flat(pl.poly(Xh([(tip, ay0 + 0.025), (tip + 0.07, ay0), (tip, ay0 - 0.025)]), smooth=False), (170, 170, 176), 1.0)
    pl.ink(Xh([(ax0 + 0.02, ay0), (ax0 + 0.10, ay0 + 0.035)]), 1.4, (200, 60, 40), 0.9, smooth=False)
    pl.ink(Xh([(ax0 + 0.02, ay0), (ax0 + 0.10, ay0 - 0.035)]), 1.4, (226, 210, 160), 0.9, smooth=False)
    vignette(pl); save(pl, 'archery')

PLATES = dict(archery=p_archery, glassing=p_glassing, stalk=p_stalk, range=p_range, tracking=p_tracking, dressing=p_dressing, harvest=p_harvest,
              track=p_track, timed=p_timed, clean=p_clean, trophy=p_trophy, supply=p_supply)
for n, f in PLATES.items():
    if want(n):
        try:
            f()
        except Exception as ex:
            import traceback; traceback.print_exc(); print('FAILED', n)
