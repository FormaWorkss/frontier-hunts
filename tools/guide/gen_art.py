"""[fieldbook] Generates the Field School plates and icon atlas (original procedural ink-and-watercolour art).

python3 tools/guide/gen_art.py <repo root> [plate ...]
  -> patch/assets/frontierhunts/textures/gui/field_school/*.png (1024x512 plates, 1152x384 banner, 256x256 icons)
  -> docs/ws/fieldbook/*.jpg previews on guide paper (labelled_*.jpg: tools/guide/plate_preview.py)
Labels are NOT baked in: the guide draws them from the lang file at the anchors in GuideArt.java (0..1 plate
coordinates). Keep the label zones in each composition clear of busy drawing.
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fieldart import *
from whitetail import whitetail, VITALS, pose_head, shift, FRONT_LEG as FRONT_LEG_PTS, HIND_LEG as HIND_LEG_PTS
from scenery import *
import tracks as TR

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
ONLY = set(sys.argv[2:])
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school')
PREV = os.path.join(ROOT, 'docs/ws/fieldbook')
os.makedirs(OUT, exist_ok=True)
os.makedirs(PREV, exist_ok=True)
W, H = 1024, 512


def save(pl, name):
    img = pl.save(os.path.join(OUT, name + '.png'))
    on_paper(img).convert('RGB').save(os.path.join(PREV, name + '.jpg'), quality=88)
    print('wrote', name, img.size)


def want(name):
    return not ONLY or name in ONLY


# ================================================================================================ vitals
def organ(pl, X, pts, color, dark, opacity=0.92, clip=None, n=8, ink=1.0, form=6):
    m = pl.poly(X(pts), n=n)
    if clip is not None:
        m = pl.inter(m, clip)
    pl.wash(m, color, opacity, edge=0.28, edge_w=1.6, var=0.1)
    pl.form(m, radius=form, dark=dark, strength=0.45, hi=WHITE, hi_strength=0.22)
    pl.contour(m, 0.5 * ink, 1.3 * ink, color=darker(dark, 0.6), opacity=0.9)
    return m


def vitals_broadside(pl, ox, oy, s):
    d = whitetail(pl, ox, oy, s, ghost=True)
    X = d['X']
    body = d['torso']
    V = VITALS
    clip = pl.grow(body, 0)
    # gut first (deepest), then liver, lungs, heart
    rumen = organ(pl, X, V['rumen'], RUMEN, (110, 100, 60), 0.85, clip)
    pl.strokes(rumen, 160, darker(RUMEN, 0.7), length=(2, 4), width=(0.4, 0.6), opacity=0.35, angle=lambda x, y: 0.4)
    gut = organ(pl, X, V['intestine'], INTESTINE, (140, 110, 70), 0.85, clip)
    # intestine coils
    for i, (cx, cy) in enumerate([(0.98, 0.78), (1.05, 0.77), (1.08, 0.70), (1.01, 0.67), (0.97, 0.72)]):
        pl.ink(X([(cx - 0.03, cy), (cx, cy + 0.025), (cx + 0.03, cy), (cx, cy - 0.022)]), (0.2, 0.8, 0.2), darker(INTESTINE, 0.55), 0.55)
    liver = organ(pl, X, V['liver'], LIVER, (50, 12, 12), 0.92, clip)
    lungs = organ(pl, X, V['lungs'], LUNG, LUNG_DARK, 0.88, clip, form=10)
    # lobes / bronchi hint
    pl.ink(X([(0.16, 0.79), (0.26, 0.76), (0.38, 0.74), (0.50, 0.70)]), (0.2, 0.8, 0.2), darker(LUNG_DARK, 0.7), 0.5)
    pl.ink(X([(0.26, 0.76), (0.33, 0.70), (0.40, 0.61)]), (0.2, 0.7, 0.2), darker(LUNG_DARK, 0.7), 0.4)
    pl.stipple(lungs, 260, darker(LUNG_DARK, 0.85), r=(0.3, 0.6), opacity=0.35)
    heart = organ(pl, X, V['heart'], HEART, (90, 10, 14), 0.97, clip, form=4)
    pl.ink(X([(0.255, 0.655), (0.26, 0.70), (0.245, 0.75)]), (1.6, 1.3, 0.9), (150, 30, 34), 0.9)      # aorta stub
    pl.ink(X([(0.25, 0.64), (0.285, 0.58), (0.30, 0.53)]), (0.2, 0.7, 0.2), (110, 14, 18), 0.6)       # coronary groove
    # diaphragm
    pl.dashed(X(V['diaphragm']), 1.0, (120, 70, 60), 4, 3, 0.75)
    # ribs over the chest (outside the lungs): faint
    for i, x in enumerate(V['ribs']):
        f = i / (len(V['ribs']) - 1)
        bottom_y = 0.50 + 0.10 * f ** 1.6
        pts = [(x, 0.858), (x + 0.012, 0.78), (x + 0.035, 0.66), (x + 0.065 + 0.02 * f, bottom_y)]
        rm = pl.inter(pl.stroke_mask(X(pts), (1.9, 1.6, 0.8)), clip)
        pl.flat(rm, BONE, 0.30)
        pl.contour(rm, 0.2, 0.45, color=INK_SOFT, opacity=0.25)
    # spine: vertebral column (band with segment joints)
    sp = resample(spline(X(V['spine']), False, 12), 1.0)
    col = pl.stroke_mask(sp, (0.040 * s, 0.032 * s, 0.026 * s, 0.022 * s), smooth=False)
    pl.wash(col, BONE, 0.95, edge=0.25)
    pl.form(col, radius=1.5, dark=(120, 100, 70), strength=0.45)
    acc = 0.0
    for i in range(1, len(sp) - 1):
        acc += math.dist(sp[i - 1], sp[i])
        if acc >= 0.042 * s:
            acc = 0.0
            (x0, y0), (x1, y1) = sp[i - 1], sp[i + 1]
            ln = math.hypot(x1 - x0, y1 - y0) or 1
            nx, ny = -(y1 - y0) / ln, (x1 - x0) / ln
            r = 0.02 * s
            pl.ink([(sp[i][0] - nx * r, sp[i][1] - ny * r), (sp[i][0] + nx * r, sp[i][1] + ny * r)], 0.6, INK_SOFT, 0.55, smooth=False)
    pl.contour(col, 0.35, 0.7, color=INK_SOFT, opacity=0.85)
    # spinous processes at the withers
    for x, h in [(0.10, 0.07), (0.16, 0.10), (0.22, 0.11), (0.28, 0.105), (0.34, 0.09), (0.40, 0.075), (0.47, 0.06), (0.55, 0.05), (0.63, 0.045), (0.71, 0.045)]:
        y0 = 0.87
        pm = pl.stroke_mask(X([(x, y0), (x + 0.035, y0 + h)]), (2.4, 1.3), smooth=False)
        pl.flat(pm, BONE, 0.7)
        pl.contour(pm, 0.25, 0.5, color=INK_SOFT, opacity=0.5)
    # shoulder blade + upper arm, translucent over the front of the chest
    bones = Plate.union(pl.poly(X(V['scapula'])), pl.poly(X(V['humerus'])))
    pl.wash(bones, BONE, 0.62, edge=0.25)
    pl.form(bones, radius=2, dark=(120, 100, 70), strength=0.35)
    pl.contour(bones, 0.45, 1.0, color=INK_SOFT, opacity=0.85)
    # aim point + vital zone
    ax, ay = X.p(*V['aim'])
    zone = pl.ellipse(ax, ay, 0.10 * s, 0.10 * s)
    pl.flat(zone, (255, 255, 255), 0.10)
    pl.dashed([(ax + math.cos(t) * 0.10 * s, ay + math.sin(t) * 0.10 * s) for t in np.linspace(0, 2 * math.pi, 64)], 1.2, (150, 20, 20), 4, 3, 0.95, smooth=False)
    pl.ink([(ax - 14, ay), (ax - 4, ay)], 1.4, (170, 20, 20), 1.0, smooth=False)
    pl.ink([(ax + 4, ay), (ax + 14, ay)], 1.4, (170, 20, 20), 1.0, smooth=False)
    pl.ink([(ax, ay - 14), (ax, ay - 4)], 1.4, (170, 20, 20), 1.0, smooth=False)
    pl.ink([(ax, ay + 4), (ax, ay + 14)], 1.4, (170, 20, 20), 1.0, smooth=False)
    pl.flat(pl.ellipse(ax, ay, 2.2, 2.2), (190, 20, 20), 1.0)
    return dict(X=X, aim=(ax, ay))


def vitals_quartering(pl, cx, cy, s):
    """Top-down inset: deer quartering away, the arrow path from the hunter through the near ribs to the far shoulder."""
    ang = math.radians(-140)          # heading up-left, away from the hunter below
    d = deer_top(pl, cx, cy, s, ang, buck=False)
    T = d['T']
    # organs seen from above (heart/lungs) through a ghosted back
    lungs = Plate.union(pl.poly(T([(0.40, 0.03), (0.36, 0.15), (0.20, 0.19), (0.06, 0.17), (0.0, 0.08), (0.02, 0.02)])),
                        pl.poly(T([(0.40, -0.03), (0.36, -0.15), (0.20, -0.19), (0.06, -0.17), (0.0, -0.08), (0.02, -0.02)])))
    pl.wash(lungs, LUNG, 0.85, edge=0.25)
    pl.contour(lungs, 0.4, 0.9, color=darker(LUNG_DARK, 0.6), opacity=0.8)
    heart = pl.ellipse(*T([(0.27, 0.0)])[0], 0.06 * s, 0.045 * s, rot=ang)
    pl.wash(heart, HEART, 0.95)
    pl.contour(heart, 0.4, 0.8, color=(90, 10, 14), opacity=0.8)
    # path: entry behind the near ribs -> exit at the far front shoulder
    entry = T([(0.02, -0.215)])[0]     # near side faces the hunter (lower right of the deer)
    exitp = T([(0.34, 0.20)])[0]
    dx, dy = exitp[0] - entry[0], exitp[1] - entry[1]
    L = math.hypot(dx, dy)
    ux, uy = dx / L, dy / L
    start = (entry[0] - ux * 0.62 * s, entry[1] - uy * 0.62 * s)
    end = (exitp[0] + ux * 0.10 * s, exitp[1] + uy * 0.10 * s)
    pl.dashed([start, entry], 1.4, (170, 20, 20), 5, 3, 0.95, smooth=False)
    pl.arrow([entry, end], 1.6, (170, 20, 20), 8, 0.95, smooth=False)
    pl.flat(pl.ellipse(*entry, 2.6, 2.6), (190, 20, 20), 1.0)
    return dict(entry=entry, exit=exitp, start=start)


def plate_vitals():
    pl = Plate(W, H, seed=21)
    # ground shadow
    sh = pl.ellipse(305, 476, 220, 9)
    pl.wash(pl.blur(sh, 4), (90, 78, 60), 0.35, edge=0)
    v = vitals_broadside(pl, 150, 476, 252)
    # inset frame for the quartering-away diagram
    fx0, fy0, fx1, fy1 = 760, 40, 1004, 404
    frame = pl.poly([(fx0, fy0), (fx1, fy0), (fx1, fy1), (fx0, fy1)], smooth=False)
    pl.wash(frame, (226, 216, 190), 0.55, edge=0.08, grain=0.2)
    pl.ink([(fx0, fy0), (fx1, fy0), (fx1, fy1), (fx0, fy1), (fx0, fy0)], 0.9, INK_SOFT, 0.7, smooth=False)
    q = vitals_quartering(pl, 892, 180, 150)
    # the hunter (seen from above) at the bottom of the inset, facing the deer
    hx, hy = q['start']
    hunter_top(pl, hx, hy + 18, 66, math.atan2(q['entry'][1] - hy, q['entry'][0] - hx))
    save(pl, 'vitals')
    return v, q


# ================================================================================================ tracks
TRACK_K = 9.0   # px per cm: every print on the plate is drawn to the same scale


def plate_tracks():
    pl = Plate(W, H, seed=33)
    k = TRACK_K
    cw = W / 5
    rows = [112, 350]
    anchors = {}
    # faint rules between the cells
    for i in range(1, 5):
        pl.ink([(i * cw, 34), (i * cw, 200)], 0.6, INK_SOFT, 0.3, smooth=False)
        pl.ink([(i * cw, 272), (i * cw, 440)], 0.6, INK_SOFT, 0.3, smooth=False)
    for r, cy in enumerate(rows):
        for c in range(5):
            mud_cy = cy + (6 if r == 0 else 10)
            # [artqa] the bear cell holds a front AND a hind print at the common scale: a wider patch so neither spills out of it
            TR.mud_patch(pl, (c + 0.5) * cw, mud_cy, cw * (0.47 if (r, c) == (1, 2) else 0.36), 82 if r == 0 else 94, seed=r * 5 + c)

    def hoof(col, row, L, Wd, dx=0.0, dy=0.0, dew=None, **kw):
        cx, cy = (col + 0.5) * cw + dx, rows[row] + dy
        shapes = TR.cloven(L, Wd, **kw)
        if dew:
            ex, ey, rx, ry = dew
            shapes += [TR.pad(ex * Wd, ey * L, rx, ry), TR.pad(-ex * Wd, ey * L, rx, ry)]
        TR.impression(pl, shapes, cx, cy, k)
        return cx, cy

    x, y = hoof(0, 0, 7.5, 5.6, gap=0.09, sharp=1.0, heel=0.6, bulge=0.35)
    pl.arrow([(x + 52, y + 26), (x + 52, y - 30)], 1.3, INK_SOFT, 7, 0.85, smooth=False)   # direction of travel
    hoof(1, 0, 11.0, 9.5, gap=0.07, sharp=0.40, heel=0.95, bulge=0.85)
    hoof(2, 0, 14.0, 10.0, dy=-10, gap=0.09, sharp=0.95, heel=0.45, bulge=0.35, dew=(0.27, -0.66, 0.9, 1.2))
    hoof(3, 0, 13.0, 13.0, gap=0.06, sharp=0.15, heel=1.0, bulge=1.0)
    hoof(4, 0, 6.0, 5.4, dy=-8, gap=0.16, sharp=0.25, heel=0.8, bulge=0.7, splay=0.10, dew=(0.46, -0.66, 0.55, 0.75))
    # wolf (left) and coyote (right)
    wolf = TR.canid(11.5, 9.5)
    m, T = TR.impression(pl, wolf['shapes'], 0.5 * cw - 26, rows[1] - 4, k)
    TR.claw_marks(pl, T, wolf['claws'], k)
    coy = TR.canid(6.5, 5.0, narrow=0.92)
    m, T = TR.impression(pl, coy['shapes'], 0.5 * cw + 50, rows[1] + 14, k)
    TR.claw_marks(pl, T, coy['claws'], k * 0.7)
    cat = TR.feline(9.0, 9.5)
    TR.impression(pl, cat['shapes'], 1.5 * cw, rows[1], k)
    bf = TR.bear_front(10.0, 11.0)
    m, T = TR.impression(pl, bf['shapes'], 2.5 * cw - 46, rows[1] - 8, k)
    TR.claw_marks(pl, T, bf['claws'], k)
    bh = TR.bear_hind(17.5, 9.5)
    m, T = TR.impression(pl, bh['shapes'], 2.5 * cw + 50, rows[1] + 6, k)
    TR.claw_marks(pl, T, bh['claws'], k)
    TR.bird_print(pl, TR.grouse(5.0), 3.5 * cw - 20, rows[1] + 40, k, width_cm=0.5)
    TR.bird_print(pl, TR.grouse(5.0), 3.5 * cw + 20, rows[1] - 30, k, width_cm=0.5)
    toes, web = TR.duck(6.5)
    TR.bird_print(pl, toes, 4.5 * cw, rows[1] + 2, k, width_cm=0.40, web=web)
    # scale bar: 10 cm, ticks every centimetre
    x0, y0 = 16, 14
    pl.ink([(x0, y0), (x0 + 10 * k, y0)], 1.2, INK, 0.9, smooth=False)
    for i in range(11):
        h = 5 if i % 5 == 0 else 3
        pl.ink([(x0 + i * k, y0), (x0 + i * k, y0 + h)], 0.8, INK, 0.9, smooth=False)
    bar = pl.poly([(x0, y0 - 2.5), (x0 + 5 * k, y0 - 2.5), (x0 + 5 * k, y0), (x0, y0)], smooth=False)
    pl.flat(bar, INK, 0.85)
    save(pl, 'tracks')


# ================================================================================================ wind
def canopy(pl, x, y, r, seed=0, color=FOLIAGE, conifer=False):
    """Tree crown seen from above."""
    rng = random.Random(seed)
    m = pl.mask()
    if conifer:
        pts = []
        for i in range(18):
            a = i / 18 * 2 * math.pi
            rr = r * (1.0 if i % 2 == 0 else 0.72) * rng.uniform(0.9, 1.08)
            pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr))
        pl.poly(pts, m=m, smooth=False)
    else:
        for i in range(7):
            a = rng.uniform(0, 2 * math.pi)
            d = rng.uniform(0, r * 0.4)
            pl.ellipse(x + math.cos(a) * d, y + math.sin(a) * d, r * rng.uniform(0.55, 0.75), r * rng.uniform(0.55, 0.75), m=m)
    m = pl.rough(m, max(0.6, r * 0.05))
    # cast shadow down-right
    from PIL import ImageChops
    shm = ImageChops.offset(m, int(r * 0.35 * pl.S), int(r * 0.35 * pl.S))
    pl.wash(pl.blur(pl.minus(shm, m), 1.5), (70, 80, 50), 0.35, edge=0)
    pl.wash(m, color, 1.0, edge=0.3, var=0.15)
    pl.form(m, radius=r * 0.4, dark=darker(color, 0.5), strength=0.6, hi=lighter(color, 0.35), hi_strength=0.35)
    pl.stipple(m, int(r * r * 0.25), darker(color, 0.6), r=(0.4, 1.0), opacity=0.45)
    pl.contour(m, 0.35, 1.0, opacity=0.8)
    return m


def scent_plume(pl, x0, y0, x1, w0=10, spread=0.34, seed=5):
    """Top-down scent plume from (x0, y0) blowing to +x, widening downwind and thinning with distance."""
    pts_top, pts_bot = [], []
    rng = random.Random(seed)
    n = 28
    for i in range(n + 1):
        t = i / n
        x = x0 + (x1 - x0) * t
        hw = w0 + spread * (x - x0)
        wob = math.sin(t * 9 + seed) * hw * 0.08 + math.sin(t * 23) * hw * 0.04
        pts_top.append((x, y0 - hw + wob))
        pts_bot.append((x, y0 + hw + wob * 0.7))
    m = pl.poly(pts_top + list(reversed(pts_bot)), n=4)
    m = pl.rough(m, 3, 'lo')
    fade = pl.mask()
    # opacity falls off downwind: draw the cone as stacked bands
    arr = np.zeros((pl.H, pl.W), np.float32)
    xs = np.arange(pl.W, dtype=np.float32) / pl.S
    prof = np.clip(1 - (xs - x0) / (x1 - x0), 0, 1) ** 0.8
    prof[xs < x0 - 6] = 0
    arr[:] = prof[None, :]
    fade = Image.fromarray((arr * 255).astype(np.uint8), 'L')
    mm = pl.inter(pl.blur(m, 3), fade)
    pl.wash(mm, SCENT, 0.55, edge=0.15, var=0.18, grain=0.2)
    pl.wash(pl.inter(pl.blur(pl.poly(pts_top[:12] + list(reversed(pts_bot[:12])), n=4), 4), fade), SCENT_DARK, 0.25, edge=0)
    # drifting scent streamlines
    for k in range(7):
        f = (k + 0.5) / 7 * 2 - 1
        line = []
        for i in range(0, n + 1):
            t = i / n
            x = x0 + 8 + (x1 - x0 - 30) * t
            hw = w0 + spread * (x - x0)
            line.append((x, y0 + f * hw * 0.8 + math.sin(t * 7 + k * 1.7) * hw * 0.10))
        L = len(line)
        cut = int(L * (0.55 + 0.4 * rng.random()))
        pl.dashed(line[:cut], 0.9, SCENT_DARK, 5, 4, 0.55)
    return m


def sun(pl, x, y, r):
    for i in range(12):
        a = i / 12 * 2 * math.pi
        pl.ink([(x + math.cos(a) * r * 1.35, y + math.sin(a) * r * 1.35), (x + math.cos(a) * r * 1.85, y + math.sin(a) * r * 1.85)], (1.2, 0.5), (200, 140, 40), 0.85, smooth=False)
    m = pl.ellipse(x, y, r, r)
    pl.wash(m, (236, 178, 70), 1.0, edge=0.25)
    pl.form(m, radius=r * 0.4, dark=(190, 110, 40), strength=0.4, hi=WHITE, hi_strength=0.4)
    pl.contour(m, 0.4, 1.0, color=(150, 90, 30), opacity=0.8)


def moon(pl, x, y, r):
    m = pl.minus(pl.ellipse(x, y, r, r), pl.ellipse(x + r * 0.45, y - r * 0.2, r * 0.9, r * 0.9))
    pl.wash(m, (226, 222, 196), 1.0, edge=0.25)
    pl.form(m, radius=r * 0.3, dark=(150, 150, 130), strength=0.4)
    pl.contour(m, 0.4, 1.0, color=(90, 90, 80), opacity=0.85)
    for (sx, sy) in ((x - r * 2.2, y + r * 0.6), (x + r * 1.9, y - r * 0.4), (x - r * 1.2, y - r * 1.4)):
        pl.flat(pl.ellipse(sx, sy, 1.1, 1.1), (90, 96, 110), 0.8)


def slope_inset(pl, x0, y0, x1, y1, rising, seed=0):
    frame = pl.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], smooth=False)
    sky = ([(0, (232, 222, 196)), (1, (238, 230, 210))] if rising else [(0, (176, 186, 200)), (1, (214, 214, 210))])
    pl.wash(frame, sky[0][1], 0.65, edge=0.06, grain=0.2, grad=(sky, (0, y0, 0, y1)))
    w, h = x1 - x0, y1 - y0
    # hillside rising to the right, valley at the left
    hill = [(x0, y1), (x0, y0 + h * 0.86), (x0 + w * 0.25, y0 + h * 0.80), (x0 + w * 0.55, y0 + h * 0.58), (x0 + w * 0.80, y0 + h * 0.36),
            (x1, y0 + h * 0.28), (x1, y1)]
    hm = pl.poly(hill)
    hm = pl.inter(hm, frame)
    pl.wash(hm, GRASS, 0.85, edge=0.2, grad=([(0, lighter(GRASS, 0.1)), (1, darker(GRASS, 0.8))], (x0, y0 + h * 0.3, x0, y1)))
    pl.ink(spline(hill[1:-1], False, 10), (0.5, 1.3, 0.5), INK, 0.85)
    for i, (tx, f) in enumerate([(0.86, 0.36), (0.93, 0.31), (0.70, 0.45), (0.10, 0.83)]):
        gx = x0 + w * tx
        gy = y0 + h * (0.86 - (0.86 - 0.28) * max(0, (tx - 0.0)) * 0.0) if False else None
    # trees along the ridge
    def ground_y(fx):
        pts = spline(hill[1:-1], False, 10)
        X = x0 + w * fx
        best = min(pts, key=lambda p: abs(p[0] - X))
        return best[1]
    for i, fx in enumerate([0.84, 0.92, 0.74, 0.97]):
        conifer(pl, x0 + w * fx, ground_y(fx) + 2, h * (0.30 if i % 2 == 0 else 0.24), seed=seed + i)
    # hunter on the mid slope
    hx = x0 + w * 0.42
    hunter(pl, hx, ground_y(0.42) + 1, h * 0.17, facing=1, pose='stand_glass', blaze=True)
    # thermal arrows following the slope
    for j, off in enumerate([-0.08, -0.17]):
        rng_x = np.linspace(0.46, 0.90 - j * 0.04, 9) if rising else np.linspace(0.38, 0.04 + j * 0.03, 9)
        pts = [(x0 + w * fx, ground_y(fx) + h * off) for fx in rng_x]
        pl.arrow(pts, 1.7, WARM if rising else COOL, 8, 0.95)
    # source wisps at the hunter
    if rising:
        sun(pl, x0 + w * 0.12, y0 + h * 0.50, h * 0.065)
    else:
        moon(pl, x0 + w * 0.12, y0 + h * 0.50, h * 0.07)
    pl.ink([(x0, y0), (x1, y0), (x1, y1), (x0, y1), (x0, y0)], 0.9, INK_SOFT, 0.7, smooth=False)


def plate_wind():
    pl = Plate(W, H, seed=11)
    A = {}
    # ground plane (oblique view): soft meadow wash, wood edge behind, brush in front
    mx0, my0, mx1, my1 = 14, 60, 662, 498
    field = pl.poly([(mx0, 150), (mx1, 140), (mx1, my1), (mx0, my1)], smooth=False)
    field = pl.rough(pl.inter(field, pl.blur(pl.poly([(mx0 + 14, 160), (mx1 - 14, 150), (mx1 - 14, my1 - 14), (mx0 + 14, my1 - 14)], smooth=False), 10)), 3, 'lo')
    pl.wash(field, (200, 198, 156), 0.5, edge=0.08, var=0.2, bleed=2,
            grad=([(0, (176, 180, 132)), (1, (214, 206, 166))], (0, 150, 0, my1)))
    pl.strokes(field, 700, GRASS_DARK, length=(2, 4), width=(0.4, 0.6), opacity=0.22, angle=lambda x, y: -math.pi / 2 + 0.3, jitter=0.6)
    # wood line on the far side
    for i, (x, h, con) in enumerate([(34, 108, True), (70, 84, False), (104, 120, True), (148, 92, True), (196, 76, False), (430, 70, False),
                                     (470, 96, True), (520, 112, True), (566, 86, False), (612, 118, True), (646, 92, True)]):
        if con:
            conifer(pl, x, 166 - (i % 3) * 4, h, seed=i)
        else:
            broadleaf(pl, x, 166 - (i % 3) * 4, h, seed=i)
    tufts(pl, mx0 + 20, mx1 - 20, 168, 40, seed=2)
    # wind arrows in the air, blowing to the right
    for i, y in enumerate([150, 176]):
        pts = [(x, y + math.sin(x * 0.03 + i) * 2.5) for x in np.linspace(214 + i * 26, 376 + i * 20, 12)]
        pl.arrow(pts, 1.9, WIND_BLUE, 10, 0.9)
    A['wind'] = (300, 136)
    # hunter (facing into the wind, toward the upwind deer) and the scent plume lying downwind on the ground
    hx, gy = 268, 352
    def plume():
        x0, x1 = hx + 4, mx1 - 18
        pts_top, pts_bot = [], []
        n = 28
        for i in range(n + 1):
            t = i / n
            x = x0 + (x1 - x0) * t
            hw = (8 + 0.36 * (x - x0)) * 0.48           # foreshortened ground plane
            wob = math.sin(t * 9) * hw * 0.10 + math.sin(t * 23) * hw * 0.05
            pts_top.append((x, gy - 2 - hw + wob))
            pts_bot.append((x, gy - 2 + hw + wob * 0.7))
        m = pl.rough(pl.poly(pts_top + list(reversed(pts_bot)), n=4), 3, 'lo')
        xs = np.arange(pl.W, dtype=np.float32) / pl.S
        prof = np.clip(1 - (xs - x0) / (x1 - x0), 0, 1) ** 0.7
        prof[xs < x0 - 4] = 0
        fade = Image.fromarray((np.repeat(prof[None, :], pl.H, 0) * 255).astype(np.uint8), 'L')
        pl.wash(pl.inter(pl.blur(m, 3), fade), SCENT, 0.6, edge=0.15, var=0.18, grain=0.2)
        for k in range(6):
            f = (k + 0.5) / 6 * 2 - 1
            line = []
            for i in range(n + 1):
                t = i / n
                x = x0 + 10 + (x1 - x0 - 30) * t
                hw = (8 + 0.36 * (x - x0)) * 0.48
                line.append((x, gy - 2 + f * hw * 0.8 + math.sin(t * 7 + k * 1.7) * hw * 0.12))
            pl.dashed(line[:int(len(line) * (0.6 + 0.07 * k))], 0.9, SCENT_DARK, 5, 4, 0.55)
    plume()
    # deer downwind, inside the plume: head up, staring back at the hunter
    d1 = whitetail(pl, 470, 396, 92, facing=1, neck_ang=-0.05, detail=0.6)
    A['winded'] = d1['X'].p(0.55, 0.85)
    # deer upwind (left): feeding, unaware
    d2 = whitetail(pl, 140, 372, 80, facing=-1, buck=False, neck_ang=1.2, head_ang=0.5, detail=0.6)
    A['upwind'] = d2['X'].p(0.55, 0.85)
    # the hunter last (in front of the plume start)
    h = hunter(pl, hx, gy, 82, facing=-1, pose='crouch')
    bow(pl, h['X'], h['J']['hand_f'], ang=0.15, size=1.3)
    A['you'] = (hx, gy - 112)
    A['cone'] = (560, 330)
    tufts(pl, mx0 + 30, mx1 - 30, 470, 30, seed=5, h=(6, 12))
    # thermal insets
    slope_inset(pl, 680, 16, 1010, 246, True, seed=3)
    slope_inset(pl, 680, 268, 1010, 498, False, seed=8)
    A['thermal_up'] = (845, 30)
    A['thermal_down'] = (845, 282)
    save(pl, 'wind')
    return A

# ================================================================================================ rub & scrape
def leaf(pl, x, y, L, ang, col, opacity=1.0):
    """Pointed leaf hanging from (x, y) along ang (radians, screen space)."""
    c, s_ = math.cos(ang), math.sin(ang)
    w = L * 0.36
    pts = [(0, 0), (L * 0.25, w * 0.85), (L * 0.6, w), (L, 0), (L * 0.6, -w), (L * 0.25, -w * 0.85)]
    P = [(x + px * c - py * s_, y + px * s_ + py * c) for px, py in pts]
    m = pl.poly(P, n=6)
    pl.wash(m, col, opacity, edge=0.3, var=0.15)
    pl.form(m, radius=1.5, dark=darker(col, 0.6), strength=0.4)
    pl.ink([(x, y), (x + L * 0.85 * c, y + L * 0.85 * s_)], (0.5, 0.2), darker(col, 0.55), 0.7 * opacity, smooth=False)
    pl.contour(m, 0.3, 0.7, opacity=0.8 * opacity)


FALL = ((214, 160, 60), (196, 116, 52), (222, 186, 84), (170, 120, 50))


def plate_sign():
    pl = Plate(W, H, seed=44)
    A = {}
    gy = 466
    rng = random.Random(9)
    # two ground vignettes (subjects on plain paper, like a field-guide plate)
    for (x0, x1, sd) in ((40, 440, 3), (520, 1004, 4)):
        gm = pl.rough(pl.poly([(x0, gy - 6), (x1, gy - 8), (x1 - 10, gy + 30), (x0 + 10, gy + 32)]), 4, 'lo')
        gm = pl.inter(gm, pl.blur(pl.poly([(x0 + 20, gy - 20), (x1 - 20, gy - 20), (x1 - 20, gy + 40), (x0 + 20, gy + 40)], smooth=False), 12))
        pl.wash(gm, (180, 160, 114), 0.6, edge=0.12, var=0.22, bleed=1.5)
        pl.stipple(gm, 260, darker((180, 160, 114), 0.6), r=(0.3, 0.8), opacity=0.35)
        line = [(x, gy - 2 + math.sin(x * 0.05 + sd) * 1.2) for x in np.linspace(x0 + 30, x1 - 30, 30)]
        pl.ink(line, (0.2, 1.0, 1.0, 0.2), INK_SOFT, 0.6)
    leaf_litter(pl, 240, gy + 12, 180, 12, 40, seed=2, opacity=0.65, size=(2.0, 3.4))
    leaf_litter(pl, 760, gy + 12, 220, 12, 50, seed=3, opacity=0.65, size=(2.0, 3.4))
    tufts(pl, 70, 410, gy, 36, seed=1, h=(8, 18))
    tufts(pl, 540, 640, gy, 10, seed=5, h=(7, 14))
    # ---------------------------------------------------------------- left: sapling with a fresh rub (close-up, 300 px/m)
    k = 300.0
    sx = 236
    trunk_w = lambda y: (0.036 - 0.012 * min(1.0, y / 1.6)) * k
    lean = lambda y: 4 * math.sin(y * 1.8)
    left = [(sx - trunk_w(y) + lean(y), gy - y * k) for y in np.linspace(-0.02, 1.6, 20)]
    right = [(sx + trunk_w(y) + lean(y), gy - y * k) for y in np.linspace(1.6, -0.02, 20)]
    tm = pl.poly(left + right, smooth=False)
    tm = pl.inter(tm, pl.blur(pl.poly([(0, 26), (W, 26), (W, H), (0, H)], smooth=False), 10))     # fade out at the top
    pl.wash(tm, (120, 106, 90), 1.0, edge=0.2, var=0.1)
    pl.form(tm, radius=5, dark=(50, 40, 30), strength=0.55, hi=WHITE, hi_strength=0.15)
    pl.strokes(tm, 160, (74, 62, 50), length=(1.5, 3.5), width=(0.5, 0.8), angle=lambda x, y: 0.05, opacity=0.5, jitter=0.25)   # lenticels
    def edge(y0, sign, seed):
        r = random.Random(seed)
        out = []
        for x in np.linspace(sx - 13, sx + 13, 9):
            out.append((x + lean(y0), gy - y0 * k + sign * r.uniform(0, 12)))
        return out
    top = edge(0.95, -1, 3)
    bot = edge(0.26, 1, 5)
    band = pl.inter(pl.poly(top + list(reversed(bot)), smooth=False), tm)
    pl.wash(band, WOOD_PALE, 1.0, edge=0.25, var=0.12)
    pl.wash(pl.inter(pl.blur(band, 4), pl.poly([(sx - 30, gy - 0.85 * k), (sx + 30, gy - 0.85 * k), (sx + 30, gy - 0.4 * k), (sx - 30, gy - 0.4 * k)], smooth=False)),
            (200, 208, 150), 0.35, edge=0)     # fresh green cambium
    pl.form(band, radius=5, dark=(150, 128, 88), strength=0.5)
    for i in range(22):    # antler gouges along the wood
        x = sx + rng.uniform(-9, 9)
        y0 = rng.uniform(0.30, 0.88)
        L = rng.uniform(0.05, 0.16)
        pl.ink([(x + lean(y0), gy - y0 * k), (x + lean(y0 + L) + rng.uniform(-2, 2), gy - (y0 + L) * k)], (0.3, 0.9, 0.3), (150, 118, 76), 0.7, smooth=False)
    for i, (dx, L, side) in enumerate(((-11, 0.10, -1), (-4, 0.06, -1), (6, 0.12, 1), (12, 0.07, 1))):   # shredded bark hanging from the top edge
        x = sx + dx + lean(0.95)
        y0 = gy - 0.95 * k + 2
        pts = [(x, y0), (x + side * 1.5, y0 + L * k * 0.5), (x + side * 3.5, y0 + L * k)]
        m = pl.stroke_mask(pts, (2.6, 2.0, 0.9))
        pl.wash(m, (150, 128, 100), 1.0, edge=0.25)
        pl.contour(m, 0.25, 0.6, opacity=0.8)
    pl.contour(tm, 0.5, 1.5, opacity=0.9)
    pl.ink(top, (0.3, 1.0, 0.3), (110, 88, 58), 0.85)
    pl.ink(bot, (0.3, 1.0, 0.3), (110, 88, 58), 0.85)
    for i in range(26):   # bark shavings on the ground
        x = sx + rng.uniform(-46, 46)
        y = gy + rng.uniform(-1, 12)
        a = rng.uniform(-0.5, 0.5)
        m = pl.stroke_mask([(x, y), (x + math.cos(a) * 6, y + math.sin(a) * 2 - 1), (x + math.cos(a) * 9, y - 3)], (2.2, 1.5, 0.6))
        pl.wash(m, (126, 108, 88) if i % 3 else WOOD_PALE, 0.95, edge=0.2)
        pl.contour(m, 0.25, 0.5, opacity=0.7)
    for (y, side, L) in ((1.18, -1, 0.26), (1.34, 1, 0.30), (1.50, -1, 0.22)):   # a few twigs with fall leaves
        x0 = sx + lean(y)
        pts = [(x0 + side * trunk_w(y), gy - y * k), (x0 + side * L * k * 0.5, gy - (y + 0.07) * k), (x0 + side * L * k, gy - (y + 0.11) * k)]
        pl.ink(pts, (2.2, 1.3, 0.5), (96, 80, 64), 0.95)
        for j in range(7):
            t = 0.25 + j * 0.12
            px = x0 + side * L * k * t
            py = gy - (y + 0.07 * min(1, t / 0.5) + 0.04 * max(0, (t - 0.5) / 0.5)) * k
            leaf(pl, px, py, rng.uniform(12, 18), math.pi / 2 + side * rng.uniform(-0.9, 0.3) + (0.6 if j % 2 else -0.2) * side, rng.choice(FALL))
    A['rub'] = (sx + 10, gy - 0.62 * k)
    # ---------------------------------------------------------------- right: licking branch over a scrape (210 px/m)
    k = 210.0
    tx = 958
    tl = [(tx - 30 + 3 * math.sin(y * 3), gy - y * k) for y in np.linspace(-0.02, 2.4, 14)]
    tr = [(tx + 30 + 3 * math.sin(y * 3 + 1), gy - y * k) for y in np.linspace(2.4, -0.02, 14)]
    big = pl.poly([(tx - 42, gy + 4)] + tl + tr + [(tx + 44, gy + 4)], smooth=False)
    big = pl.inter(big, pl.blur(pl.poly([(0, 26), (W, 26), (W, H), (0, H)], smooth=False), 10))
    pl.wash(big, BARK, 1.0, edge=0.2, var=0.12)
    pl.form(big, radius=10, dark=BARK_DARK, strength=0.6, hi=WHITE, hi_strength=0.12)
    pl.strokes(big, 340, BARK_DARK, length=(6, 16), width=(0.6, 1.2), angle=lambda x, y: -math.pi / 2, opacity=0.55, jitter=0.12)
    pl.contour(big, 0.6, 1.6, opacity=0.9)
    br = [(tx - 26, gy - 1.98 * k), (868, gy - 1.86 * k), (790, gy - 1.68 * k), (720, gy - 1.52 * k), (660, gy - 1.42 * k)]
    bm = pl.stroke_mask(br, (11, 7.5, 5, 3, 1.4))
    pl.wash(bm, (100, 82, 64), 1.0, edge=0.2)
    pl.form(bm, radius=2, dark=(50, 40, 30), strength=0.5)
    pl.contour(bm, 0.4, 1.0, opacity=0.9)
    for (bx, by, L, a) in [(720, gy - 1.52 * k, 28, 2.15), (690, gy - 1.47 * k, 24, 1.85), (756, gy - 1.60 * k, 32, 1.72), (668, gy - 1.43 * k, 20, 1.55),
                           (820, gy - 1.74 * k, 36, 1.95)]:
        tip = (bx + math.cos(a) * L, by + math.sin(a) * L)
        pl.ink([(bx, by), ((bx + tip[0]) / 2 + 2, (by + tip[1]) / 2), tip], (1.7, 1.2, 1.0), (96, 78, 60), 0.95)
        for j in range(3):       # freshly broken, chewed tip: pale frayed fibres
            aa = a + (j - 1) * 0.55
            pl.ink([tip, (tip[0] + math.cos(aa) * 4, tip[1] + math.sin(aa) * 4)], (0.9, 0.3), (214, 200, 164), 0.95, smooth=False)
    for i in range(14):
        t = rng.uniform(0.15, 0.75)
        bx = 940 - (940 - 760) * t
        by = gy - (1.96 - (1.96 - 1.64) * t) * k
        leaf(pl, bx, by + rng.uniform(-3, 3), rng.uniform(12, 17), math.pi / 2 + rng.uniform(-0.8, 0.8), FALL[i % 4])
    A['licking'] = (700, gy - 1.46 * k)
    sc_x, sc_y = 700, gy + 8
    sm = pl.rough(pl.poly([(sc_x - 92, sc_y + 3), (sc_x - 60, sc_y - 13), (sc_x + 10, sc_y - 18), (sc_x + 76, sc_y - 12), (sc_x + 98, sc_y + 1),
                           (sc_x + 64, sc_y + 15), (sc_x - 4, sc_y + 19), (sc_x - 66, sc_y + 14)]), 1.8)
    leaf_litter(pl, sc_x + 116, sc_y - 2, 28, 10, 34, seed=11, opacity=0.95, size=(2.2, 3.8))
    leaf_litter(pl, sc_x - 108, sc_y + 2, 18, 8, 16, seed=12, opacity=0.95, size=(2.2, 3.8))
    spray = pl.minus(pl.rough(pl.ellipse(sc_x + 104, sc_y - 1, 38, 11), 2), sm)
    pl.wash(spray, (132, 100, 68), 0.5, edge=0.1)
    pl.stipple(spray, 140, SOIL_DARK, r=(0.4, 1.1), opacity=0.6)
    pl.wash(sm, mix(SOIL, PAPER, 0.08), 1.0, edge=0.35, var=0.2)
    pl.wash(pl.blur(pl.ellipse(sc_x - 16, sc_y + 2, 40, 8), 3), SOIL_DAMP, 0.65, edge=0)
    pl.stipple(sm, 300, SOIL_DARK, r=(0.4, 1.0), opacity=0.5)
    for i in range(9):
        y0 = sc_y - 11 + i * 2.8
        pl.ink([(sc_x - 62 + i * 3, y0), (sc_x + 62 - i * 2, y0 + 1)], (0.2, 0.9, 0.2), SOIL_DARK, 0.38, smooth=False)
    import tracks as TRK
    TRK.impression(pl, TRK.cloven(7.5, 5.6), sc_x + 30, sc_y + 3, 2.1, rot=0.2, mud=SOIL)
    pl.contour(sm, 0.4, 1.0, color=SOIL_DARK, opacity=0.7)
    A['scrape'] = (sc_x - 36, sc_y + 4)
    save(pl, 'sign')
    return A

# ================================================================================================ glassing & trail camera
def dawn_scene(pl, clip, x0, y0, x1, y1, deer_x=None, seed=0):
    """Painted field-edge landscape at first light inside a clip mask."""
    w, h = x1 - x0, y1 - y0
    sky = pl.inter(pl.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], smooth=False), clip)
    pl.wash(sky, (236, 200, 150), 1.0, edge=0, var=0.025, grain=0.05, grad=([(0, (168, 190, 206)), (0.45, (232, 208, 168)), (0.62, (240, 196, 140)), (1, (226, 214, 170))], (0, y0, 0, y1)))
    ridge = pl.inter(pl.poly([(x0, y0 + h * 0.48), (x0 + w * 0.3, y0 + h * 0.38), (x0 + w * 0.6, y0 + h * 0.44), (x1, y0 + h * 0.36), (x1, y1), (x0, y1)]), clip)
    pl.wash(ridge, (150, 158, 170), 0.6, edge=0.12, var=0.04, grain=0.06)
    rng = random.Random(seed)
    band = pl.inter(pl.poly([(x0, y0 + h * 0.555), (x1, y0 + h * 0.55), (x1, y0 + h * 0.64), (x0, y0 + h * 0.64)], smooth=False), clip)
    pl.wash(band, (58, 80, 66), 1.0, edge=0.2, var=0.1)
    pl.set_clip(clip)
    for i, x in enumerate(np.linspace(x0 - 6, x1 + 6, 30)):
        hh = h * rng.uniform(0.12, 0.24)
        conifer(pl, x + rng.uniform(-5, 5), y0 + h * 0.62, hh, color=(56, 80, 64), seed=i, ink=False, detail=False)
    pl.set_clip(None)
    field = pl.inter(pl.poly([(x0, y0 + h * 0.62), (x1, y0 + h * 0.60), (x1, y1), (x0, y1)], smooth=False), clip)
    pl.wash(field, (170, 168, 112), 1.0, edge=0.1, var=0.18, grad=([(0, (150, 150, 100)), (1, (192, 180, 120))], (0, y0 + h * 0.6, 0, y1)))
    pl.strokes(field, 500, (110, 110, 70), length=(2, 5), width=(0.4, 0.7), opacity=0.35, angle=lambda a, b: -math.pi / 2, jitter=0.4)
    mist = pl.inter(pl.blur(pl.poly([(x0, y0 + h * 0.6), (x1, y0 + h * 0.58), (x1, y0 + h * 0.68), (x0, y0 + h * 0.70)], smooth=False), 8), clip)
    pl.wash(mist, (240, 236, 226), 0.55, edge=0)


def binocular_view(pl, cx, cy, r):
    left = pl.ellipse(cx - r * 0.78, cy, r, r)
    right = pl.ellipse(cx + r * 0.78, cy, r, r)
    view = Plate.union(left, right)
    dawn_scene(pl, view, cx - r * 1.8, cy - r, cx + r * 1.8, cy + r, seed=4)
    # buck stepping out of the timber
    d = whitetail(pl, cx + r * 0.42, cy + r * 0.36, r * 0.36, facing=1, neck_ang=-0.1, detail=0.5)
    # glass vignette and barrel rim
    inner = Plate.union(pl.ellipse(cx - r * 0.78, cy, r * 0.80, r * 0.80), pl.ellipse(cx + r * 0.78, cy, r * 0.80, r * 0.80))
    vig = pl.minus(view, pl.blur(inner, r * 0.10))
    pl.flat(pl.blur(vig, r * 0.04), (36, 32, 30), 0.5)
    ring = pl.minus(pl.grow(view, 7), view)
    pl.wash(ring, (44, 42, 40), 1.0, edge=0.1)
    pl.form(ring, radius=3, dark=(10, 10, 10), strength=0.5, hi=WHITE, hi_strength=0.25)
    pl.contour(pl.grow(view, 7), 0.6, 1.4, opacity=0.95)
    pl.contour(view, 0.4, 0.8, color=(20, 20, 20), opacity=0.9, inside=True)
    return d


def trail_camera(pl, x, gy, k, tree_x):
    """Tree with a strapped trail camera at ~0.9 m, its detection cone angled along a trail."""
    # trunk
    tw = 0.16 * k
    top = max(2.2, (gy - 10) / k)  # [artqa] trunk runs off the top of the plate at any scale
    tl = [(tree_x - tw + 2 * math.sin(y * 4), gy - y * k) for y in np.linspace(-0.02, top, 12)]
    tr = [(tree_x + tw + 2 * math.sin(y * 4 + 1), gy - y * k) for y in np.linspace(top, -0.02, 12)]
    tm = pl.poly([(tree_x - tw - 10, gy + 3)] + tl + tr + [(tree_x + tw + 12, gy + 3)], smooth=False)
    tm = pl.inter(tm, pl.blur(pl.poly([(0, 30), (W, 30), (W, H), (0, H)], smooth=False), 12))
    pl.wash(tm, BARK, 1.0, edge=0.2, var=0.12)
    pl.form(tm, radius=8, dark=BARK_DARK, strength=0.6, hi=WHITE, hi_strength=0.12)
    pl.strokes(tm, 220, BARK_DARK, length=(5, 12), width=(0.6, 1.1), angle=lambda a, b: -math.pi / 2, opacity=0.5, jitter=0.12)
    pl.contour(tm, 0.6, 1.5, opacity=0.9)
    # strap
    cy = gy - 0.92 * k
    strap = pl.inter(pl.stroke_mask([(tree_x - tw - 2, cy - 3), (tree_x, cy - 5), (tree_x + tw + 2, cy - 3)], 5), pl.grow(tm, 1))
    pl.wash(strap, (60, 60, 54), 1.0)
    # camera body (facing left, toward the trail)
    bw, bh = 0.12 * k, 0.18 * k
    bx = tree_x - tw - bw * 0.85
    body = pl.poly([(bx, cy - bh / 2), (bx + bw, cy - bh / 2 - 2), (bx + bw + 2, cy + bh / 2), (bx + 2, cy + bh / 2 + 1)], smooth=False)
    pl.wash(body, (92, 96, 70), 1.0, edge=0.2)
    blot = pl.inter(pl.rough(Plate.union(pl.ellipse(bx + 8, cy - 10, 7, 5), pl.ellipse(bx + bw - 6, cy + 8, 8, 5), pl.ellipse(bx + 6, cy + 14, 5, 4)), 0.6), body)
    pl.wash(blot, (60, 66, 46), 0.9)
    pl.form(body, radius=3, dark=(20, 22, 16), strength=0.5, hi=WHITE, hi_strength=0.2)
    pl.contour(body, 0.5, 1.2, opacity=0.95)
    lens = pl.ellipse(bx - 1, cy - bh * 0.12, 5, 6.5)
    pl.flat(lens, (24, 26, 30), 1.0)
    pl.flat(pl.ellipse(bx - 2, cy - bh * 0.12 - 2, 1.6, 1.8), WHITE, 0.6)
    ir = pl.poly([(bx - 1, cy + bh * 0.08), (bx + 1, cy + bh * 0.08), (bx + 2, cy + bh * 0.36), (bx, cy + bh * 0.36)], smooth=False)
    pl.flat(ir, (110, 40, 40), 0.9)
    pir = pl.ellipse(bx - 0.5, cy - bh * 0.38, 2.6, 2.6)
    pl.flat(pir, (200, 200, 190), 0.9)
    return (bx - 2, cy - bh * 0.12)


def plate_glass():
    pl = Plate(W, H, seed=55)
    A = {}
    binocular_view(pl, 262, 226, 146)
    A['edge'] = (262, 452)
    # right: trail camera on a tree beside a game trail
    gy = 460
    gm = pl.rough(pl.poly([(560, gy - 8), (1004, gy - 10), (996, gy + 34), (566, gy + 36)]), 4, 'lo')
    gm = pl.inter(gm, pl.blur(pl.poly([(580, gy - 30), (990, gy - 30), (990, gy + 40), (580, gy + 40)], smooth=False), 12))
    pl.wash(gm, (176, 162, 116), 0.6, edge=0.12, var=0.22, bleed=1.5)
    trail = [(570, gy + 18), (660, gy + 6), (760, gy - 2), (860, gy - 6), (990, gy - 9)]
    pl.wash(pl.blur(pl.stroke_mask(trail, (26, 16, 10, 6)), 2), (150, 128, 92), 0.55, edge=0)
    tufts(pl, 580, 990, gy - 4, 36, seed=8, h=(7, 15))
    # [artqa] tree, camera and deer share one scale (the deer was drawn at 84 px/m against a 190 px/m tree, which put the
    # "waist high" camera well above the deer's head); camera at ~0.9 m = about the deer's back
    k = 128.0
    lens = trail_camera(pl, 0, gy, k, 952)
    # detection zone (top: the lens looks down the trail toward the deer)
    lx, ly = lens
    for a in (0.0, 0.36):
        pl.dashed([(lx, ly), (lx - 340, ly + math.tan(a) * 340)], 1.1, (170, 40, 30), 6, 4, 0.8, smooth=False)
    cone = pl.poly([(lx, ly), (lx - 340, ly), (lx - 340, ly + math.tan(0.36) * 340)], smooth=False)
    pl.wash(pl.blur(cone, 2), (220, 120, 90), 0.12, edge=0)
    d = whitetail(pl, 760, gy - 4, k, facing=-1, buck=True, neck_ang=0.05, detail=0.7)
    A['camera'] = lens
    print('camera anchor', lens[0] / W, lens[1] / H)
    save(pl, 'glass')
    return A


# ================================================================================================ stalk
def plate_stalk():
    pl = Plate(W, H, seed=66)
    A = {}
    gy = 440
    k = 112.0
    # background: soft wood edge on the right, open meadow on the left
    for i, (x, h, con) in enumerate([(800, 230, True), (850, 270, True), (905, 210, False), (950, 290, True), (995, 240, True), (760, 170, False)]):
        if con:
            conifer(pl, x, gy - 6, h, seed=i, color=mix(PINE, PAPER, 0.15))
        else:
            broadleaf(pl, x, gy - 6, h, seed=i, color=mix(FOLIAGE, PAPER, 0.1))
    ground_band(pl, 20, 1004, gy, 46, color=(170, 160, 110), seed=7, opacity=0.55)
    tufts(pl, 30, 1000, gy + 1, 80, seed=3, h=(7, 16))
    # wind blowing from the deer toward the hunter (right to left)
    for i, y in enumerate([96, 126, 156]):
        pts = [(x, y + math.sin(x * 0.025 + i) * 3) for x in np.linspace(700 - i * 30, 330 - i * 20, 14)]
        pl.arrow(pts, 1.9, WIND_BLUE, 10, 0.9)
    A['wind'] = (515, 70)
    # deer feeding, facing away, head down
    d = whitetail(pl, 690, gy, k, facing=-1, buck=True, neck_ang=1.2, head_ang=0.55, detail=0.8)
    A['deer'] = d['X'].p(-0.35, 0.75)
    # cover between: a low bush and a stump
    b1 = bush(pl, 452, gy + 2, 64, seed=4)
    b2 = bush(pl, 520, gy + 2, 42, seed=6, color=mix(FOLIAGE, GRASS, 0.4))
    A['cover'] = (470, gy - 40)
    # hunter: crouched, bow in hand, moving slowly
    h = hunter(pl, 236, gy, k, facing=1, pose='crouch')
    bow(pl, h['X'], h['J']['hand_f'], ang=-0.12, size=1.32)
    A['crouch'] = h['X'].p(0.1, 1.0)
    tufts(pl, 160, 320, gy + 2, 14, seed=9, h=(6, 12))
    save(pl, 'stalk')
    return A


# ================================================================================================ blood
def drop(pl, x, y, r, col, ang=None, tail=0.0, opacity=1.0, gloss=True, spikes=None, seed=None):
    """A blood drop as it lands: round body with a crown of short spines (more and longer on the side it travelled)."""
    rng = random.Random(seed if seed is not None else int(x * 31 + y * 17))
    m = pl.ellipse(x, y, r, r)
    n = spikes if spikes is not None else (int(6 + r * 2) if r > 1.6 else 0)
    for i in range(n):
        a = rng.uniform(0, 2 * math.pi)
        bias = 1.0
        if ang is not None:
            bias = 1.0 + tail * max(0.0, math.cos(a - ang)) * 2.2
        L = r * rng.uniform(0.25, 0.6) * bias
        px, py = x + math.cos(a) * r * 0.85, y + math.sin(a) * r * 0.85
        tx, ty = x + math.cos(a) * (r + L), y + math.sin(a) * (r + L)
        w = r * 0.32
        ImageDraw.Draw(m).polygon(pl.P([(px + math.cos(a + 1.57) * w, py + math.sin(a + 1.57) * w), (tx, ty), (px - math.cos(a + 1.57) * w, py - math.sin(a + 1.57) * w)]), fill=255)
        if rng.random() < 0.35:
            d = r + L + rng.uniform(1, r * 0.8 + 1)
            sr = rng.uniform(0.35, 0.7) * max(0.6, r * 0.25)
            pl.ellipse(x + math.cos(a) * d, y + math.sin(a) * d, sr, sr, m=m)
    pl.wash(m, col, opacity, edge=0.35, edge_w=0.8, var=0.06, grain=0.03, soft=0.15)
    if gloss and r > 2.2:
        pl.flat(pl.ellipse(x - r * 0.35, y - r * 0.38, r * 0.26, r * 0.17, rot=-0.6), (255, 240, 236), 0.6 * opacity)
    return m


def blood_swatch(pl, cx, cy, kind, seed=0):
    rng = random.Random(seed)
    rx, ry = 92, 70
    g = pl.rough(pl.ellipse(cx, cy, rx, ry), 5, 'lo')
    pl.wash(g, (194, 180, 140), 0.55, edge=0.12, var=0.2, bleed=2.5)
    pl.stipple(pl.blur(g, 4), 160, (140, 120, 90), r=(0.3, 0.8), opacity=0.4)
    leaf_litter(pl, cx, cy, rx * 0.8, ry * 0.75, 9, seed=seed, opacity=0.55, size=(6, 11),
                colors=((186, 150, 96), (168, 136, 90), (196, 170, 116)))
    tufts(pl, cx - rx * 0.7, cx + rx * 0.7, cy + ry * 0.62, 7, seed=seed + 1, h=(8, 14), color=(120, 128, 80))
    if kind == 'heart':        # heavy bright spray: big splashes, sprays and streaks
        for i in range(9):
            drop(pl, cx + rng.uniform(-56, 46), cy + rng.uniform(-30, 30), rng.uniform(3.0, 6.0), BLOOD_BRIGHT, ang=0.0, tail=0.6, seed=i)
        for i in range(70):
            r = rng.uniform(0.5, 1.4)
            pl.flat(pl.ellipse(cx + rng.uniform(-72, 72), cy + rng.uniform(-44, 44), r, r), BLOOD_BRIGHT, 0.95)
        drop(pl, cx - 10, cy + 2, 11, BLOOD_BRIGHT, seed=99)
    elif kind == 'lungs':      # bright pinkish red, frothy with fine bubbles
        for i in range(8):
            x, y, r = cx + rng.uniform(-50, 46), cy + rng.uniform(-28, 28), rng.uniform(3.4, 7.0)
            drop(pl, x, y, r, BLOOD_LUNG, seed=i)
            for b in range(int(r * 2.6)):
                a_ = rng.uniform(0, 2 * math.pi)
                d = rng.uniform(0, r * 0.75)
                bx, by = x + math.cos(a_) * d, y + math.sin(a_) * d
                br = rng.uniform(0.5, 1.3)
                bm = pl.ellipse(bx, by, br, br)
                pl.flat(bm, (252, 222, 222), 0.85)
                pl.contour(bm, 0.15, 0.3, color=(180, 70, 80), opacity=0.55)
        for i in range(36):
            r = rng.uniform(0.5, 1.3)
            pl.flat(pl.ellipse(cx + rng.uniform(-68, 68), cy + rng.uniform(-40, 40), r, r), BLOOD_LUNG, 0.95)
    elif kind == 'liver':      # dark red, steady round drops
        for i in range(8):
            drop(pl, cx - 56 + i * 16 + rng.uniform(-3, 3), cy + math.sin(i * 0.8) * 12 + rng.uniform(-3, 3), rng.uniform(3.0, 5.0), BLOOD_LIVER, seed=i)
        drop(pl, cx + 2, cy - 18, 7, BLOOD_LIVER, seed=40)
    elif kind == 'gut':        # thin, dull blood flecked with green-brown stomach matter, sparse
        for i in range(6):
            x, y = cx - 54 + i * 21 + rng.uniform(-4, 4), cy + rng.uniform(-22, 22)
            drop(pl, x, y, rng.uniform(2.0, 3.6), (128, 58, 42), opacity=0.8, seed=i, gloss=False)
        for i in range(18):
            x, y = cx + rng.uniform(-62, 62), cy + rng.uniform(-32, 32)
            m = pl.rough(pl.ellipse(x, y, rng.uniform(1.8, 3.6), rng.uniform(1.2, 2.4), rot=rng.uniform(0, 3)), 0.4)
            pl.wash(m, rng.choice((GUT_MATTER, (130, 122, 62), (100, 96, 52), (120, 100, 60))), 0.95, edge=0.3)
        for i in range(7):     # cut hair
            x, y = cx + rng.uniform(-56, 56), cy + rng.uniform(-28, 28)
            a_ = rng.uniform(0, math.pi)
            pl.ink([(x, y), (x + math.cos(a_) * 7, y + math.sin(a_) * 7)], (0.7, 0.2), (126, 100, 72), 0.9, smooth=False)
    else:                      # muscle / leg: bright drips that get smaller and further apart
        x = cx - 66
        for i in range(8):
            r = 5.0 - i * 0.55
            drop(pl, x, cy - 6 + math.sin(i * 0.9) * 8, max(1.0, r), BLOOD_BRIGHT, ang=0.0, tail=0.5, seed=i)
            x += 10 + i * 2.6
    return g


def plate_blood():
    pl = Plate(W, H, seed=77)
    A = {}
    xs = [102, 306, 512, 716, 920]
    for i, kind in enumerate(['heart', 'lungs', 'liver', 'gut', 'muscle']):
        blood_swatch(pl, xs[i], 140, kind, seed=10 + i)
    # trail diagram: hit site -> blood thinning out -> last blood (flagged) -> look ahead -> wound bed
    gy = 430
    band = pl.rough(pl.poly([(20, 360), (1004, 352), (1004, 500), (20, 500)], smooth=False), 4, 'lo')
    band = pl.inter(band, pl.blur(pl.poly([(40, 372), (984, 366), (984, 496), (40, 496)], smooth=False), 12))
    pl.wash(band, (186, 176, 126), 0.45, edge=0.1, var=0.22, bleed=2)
    pl.strokes(band, 700, (130, 130, 84), length=(3, 7), width=(0.4, 0.7), opacity=0.3, angle=lambda a, b: -math.pi / 2 + 0.2, jitter=0.5)
    path = [(48, 446), (150, 430), (250, 444), (350, 424), (450, 436), (530, 420), (640, 430), (740, 414), (830, 426), (870, 432)]
    sp = resample(spline(path, False, 12), 1.0)
    rng = random.Random(3)
    n = len(sp)
    for i in range(0, int(n * 0.56), 18):
        t = i / n
        p = sp[i]
        drop(pl, p[0] + rng.uniform(-7, 7), p[1] + rng.uniform(-4, 4), max(1.2, 3.6 - t * 3.6), BLOOD_BRIGHT, ang=0.0, tail=0.6, seed=i)
    # hit site: cut hair and a splash where it stood
    for i in range(9):
        a_ = rng.uniform(0, math.pi)
        x, y = 44 + rng.uniform(-8, 8), 446 + rng.uniform(-4, 4)
        pl.ink([(x, y), (x + math.cos(a_) * 7, y + math.sin(a_) * 3)], (0.7, 0.2), (130, 104, 76), 0.9, smooth=False)
    drop(pl, 52, 440, 5.5, BLOOD_BRIGHT, seed=5)
    # last blood: flagging tape tied to a twig
    fx, fy = sp[int(n * 0.56)]
    drop(pl, fx - 6, fy + 2, 2.0, BLOOD_BRIGHT, seed=7)
    pl.ink([(fx + 4, fy + 4), (fx + 6, fy - 40), (fx + 3, fy - 64)], (2.2, 1.5, 0.8), (96, 78, 60), 1.0)
    tape = Plate.union(pl.stroke_mask([(fx + 5, fy - 54), (fx + 16, fy - 48), (fx + 24, fy - 36), (fx + 30, fy - 22)], (5, 4.5, 4, 3)),
                       pl.stroke_mask([(fx + 5, fy - 54), (fx + 12, fy - 42), (fx + 14, fy - 28)], (4, 3.5, 3)))
    pl.wash(tape, (240, 110, 40), 1.0, edge=0.25)
    pl.form(tape, radius=1.5, dark=(150, 50, 10), strength=0.5)
    pl.contour(tape, 0.3, 0.8, opacity=0.9)
    A['flag'] = (fx + 6, fy - 36)
    # look ahead in the direction it was going
    pl.dashed(sp[int(n * 0.60):int(n * 0.93)], 1.2, INK_SOFT, 6, 6, 0.7)
    pl.arrow([sp[int(n * 0.90)], sp[int(n * 0.95)]], 1.2, INK_SOFT, 8, 0.75)
    # wound bed in cover: oval of flattened grass with blood
    bush(pl, 962, 434, 44, seed=8)
    bed = pl.rough(pl.ellipse(916, 448, 48, 13), 1.4)
    pl.wash(bed, (200, 184, 132), 0.9, edge=0.3)
    pl.strokes(bed, 120, (150, 134, 92), length=(5, 9), width=(0.4, 0.7), opacity=0.6, angle=lambda a, b: 0.15, jitter=0.25)
    for i in range(5):
        drop(pl, 902 + i * 8 + rng.uniform(-3, 3), 448 + rng.uniform(-4, 4), rng.uniform(1.6, 2.8), BLOOD_DARK, seed=60 + i)
    A['bed'] = (916, 448)
    save(pl, 'blood')
    return A

# ================================================================================================ harvest
def rotate_pts(pts, pivot, ang):
    c, s_ = math.cos(ang), math.sin(ang)
    return [(pivot[0] + (x - pivot[0]) * c - (y - pivot[1]) * s_, pivot[1] + (x - pivot[0]) * s_ + (y - pivot[1]) * c) for x, y in pts]


def rack_front(pl, cx, cy, s, opacity=1.0):
    """Whitetail rack seen from the front (8 points), bases at (cx +- 0.03 s, cy)."""
    m = pl.mask()
    for sd in (1, -1):
        beam = [(cx + sd * 0.03 * s, cy), (cx + sd * 0.10 * s, cy - 0.06 * s), (cx + sd * 0.20 * s, cy - 0.10 * s), (cx + sd * 0.26 * s, cy - 0.17 * s),
                (cx + sd * 0.25 * s, cy - 0.26 * s), (cx + sd * 0.19 * s, cy - 0.32 * s), (cx + sd * 0.12 * s, cy - 0.34 * s)]
        pl.stroke_mask(beam, (0.045 * s, 0.038 * s, 0.03 * s, 0.022 * s, 0.014 * s), m=m)
        for (bx, by, tx, ty, w) in ((0.07, -0.04, 0.06, -0.14, 0.022), (0.17, -0.095, 0.15, -0.30, 0.026), (0.245, -0.20, 0.24, -0.36, 0.022), (0.215, -0.30, 0.20, -0.40, 0.016)):
            pl.stroke_mask([(cx + sd * bx * s, cy + by * s), (cx + sd * (bx + tx) / 2 * s, cy + (by + ty) / 2 * s), (cx + sd * tx * s, cy + ty * s)], (w * s, w * s * 0.75, w * s * 0.3), m=m)
    pl.wash(m, ANTLER, opacity, edge=0.2, grad=([(0, ANTLER), (1, darker(ANTLER, 0.78))], (cx, cy - 0.4 * s, cx, cy)))
    pl.form(m, radius=2, dark=ANTLER_DARK, strength=0.55, hi=WHITE, hi_strength=0.35)
    pl.contour(m, 0.5, 1.2, opacity=0.9 * opacity)
    return m


def knife(pl, x, y, L, ang, opacity=1.0):
    """Fixed-blade skinning knife: curved blade, guard, wood handle with brass pins."""
    c, s_ = math.cos(ang), math.sin(ang)
    def T(pts):
        return [(x + px * c - py * s_, y + px * s_ + py * c) for px, py in pts]
    blade = pl.poly(T([(0, -0.05 * L), (0.30 * L, -0.07 * L), (0.46 * L, -0.03 * L), (0.52 * L, 0.03 * L), (0.40 * L, 0.06 * L), (0.10 * L, 0.06 * L), (0, 0.055 * L)]))
    pl.wash(blade, (196, 200, 204), opacity, edge=0.2, grad=([(0, (226, 230, 232)), (1, (140, 146, 150))], (*T([(0.2 * L, -0.07 * L)])[0], *T([(0.2 * L, 0.06 * L)])[0])))
    pl.ink(T([(0.04 * L, 0.0), (0.36 * L, -0.02 * L)]), (0.2, 0.6, 0.2), (120, 126, 130), 0.7)
    guard = pl.poly(T([(-0.02 * L, -0.08 * L), (0.01 * L, -0.08 * L), (0.01 * L, 0.085 * L), (-0.02 * L, 0.085 * L)]), smooth=False)
    pl.wash(guard, (170, 140, 70), opacity)
    handle = pl.poly(T([(-0.02 * L, -0.055 * L), (-0.40 * L, -0.06 * L), (-0.47 * L, -0.03 * L), (-0.47 * L, 0.04 * L), (-0.40 * L, 0.065 * L), (-0.02 * L, 0.06 * L)]))
    pl.wash(handle, STOCK, opacity, edge=0.2)
    pl.form(handle, radius=2, dark=(60, 40, 24), strength=0.5, hi=WHITE, hi_strength=0.2)
    for px in (-0.12, -0.32):
        pl.flat(pl.ellipse(*T([(px * L, 0.0)])[0], 0.016 * L, 0.016 * L), (200, 170, 80), opacity)
    m = Plate.union(blade, guard, handle)
    pl.contour(m, 0.4, 1.0, opacity=0.95 * opacity)
    return m


def plate_harvest():
    pl = Plate(W, H, seed=88)
    A = {}
    gy = 440
    ground_band(pl, 20, 640, gy - 10, 60, color=(176, 166, 116), seed=5, opacity=0.5)
    leaf_litter(pl, 330, gy + 14, 280, 16, 50, seed=4, opacity=0.6, size=(2.5, 4))
    tufts(pl, 40, 620, gy - 4, 40, seed=6, h=(7, 15))
    # downed buck lying on its side, legs out
    k = 240.0
    # [artqa] head down at the ground, near fore leg stretched forward clear of the jaw (the old pose put the hoof on the cheek
    # and floated the throat patch off the neck)
    fa, ha = -1.47, 1.40
    front = rotate_pts(FRONT_LEG_PTS, (0.20, 0.55), fa)
    hind = rotate_pts(HIND_LEG_PTS, (0.95, 0.58), ha)
    box = [(-0.4, 0.05), (1.6, 0.05), (1.6, -0.1), (-0.4, -0.1)]
    hooves = [rotate_pts(box, (0.20, 0.55), fa), rotate_pts(box, (0.95, 0.58), ha)]
    d = whitetail(pl, 190, gy + 0.47 * k, k, facing=1, neck_ang=1.2, head_ang=-0.8, legs={'front': front, 'hind': hind}, hooves=hooves, far_legs=False, detail=0.8)
    A['deer'] = d['X'].p(0.6, 0.7)
    # the hunter kneels at the hindquarters, skinning knife in hand
    hk = hunter(pl, 604, gy + 4, 190, facing=-1, pose="kneel", weapon="knife")
    hx_, hy_ = hk['X'].p(*hk['J']['hand_f'])
    knife(pl, hx_ - 22, hy_ + 2, 56, 0.35)
    A['knife'] = (hx_ - 26, hy_ + 4)
    # right: the take — antler trophy on a plaque, venison on the hide
    px, py = 830, 200
    plaque = pl.poly([(px - 62, py - 44), (px + 62, py - 44), (px + 70, py + 6), (px + 40, py + 60), (px, py + 76), (px - 40, py + 60), (px - 70, py + 6)])
    pl.wash(plaque, (120, 76, 44), 1.0, edge=0.25, var=0.12, grad=([(0, (140, 92, 54)), (1, (96, 60, 34))], (px - 60, py - 40, px + 60, py + 70)))
    pl.strokes(plaque, 160, (80, 50, 28), length=(10, 22), width=(0.4, 0.8), angle=lambda a, b: 0.08, opacity=0.4, jitter=0.08)
    pl.form(plaque, radius=6, dark=(50, 30, 16), strength=0.5, hi=WHITE, hi_strength=0.18)
    pl.contour(plaque, 0.6, 1.5, opacity=0.95)
    inner = pl.poly([(px - 50, py - 34), (px + 50, py - 34), (px + 57, py + 4), (px + 32, py + 50), (px, py + 63), (px - 32, py + 50), (px - 57, py + 4)])
    pl.contour(inner, 0.3, 0.6, color=(70, 44, 24), opacity=0.6, inside=True)
    skull = pl.poly([(px - 26, py - 30), (px + 26, py - 30), (px + 20, py), (px + 8, py + 20), (px - 8, py + 20), (px - 20, py)])
    pl.wash(skull, BONE, 1.0, edge=0.2)
    pl.form(skull, radius=3, dark=(140, 120, 90), strength=0.5)
    pl.contour(skull, 0.4, 1.0, opacity=0.9)
    rack_front(pl, px, py - 30, 270)
    # [artqa] the take: the hide laid flat hair-side up (the old rolled hide read as a sawn log), paper-wrapped venison on it
    # and a backstrap (long, dark-red loin with a silver-skin sheen; the old one read as a red mat)
    gy2 = 452
    sh = pl.ellipse(850, gy2 + 4, 160, 14)
    pl.wash(pl.blur(sh, 4), (90, 78, 60), 0.3, edge=0)
    hide = pl.rough(pl.poly([(700, gy2 + 2), (716, gy2 - 10), (690, gy2 - 22), (712, gy2 - 26), (740, gy2 - 20), (780, gy2 - 30), (850, gy2 - 32),
                             (918, gy2 - 30), (962, gy2 - 22), (990, gy2 - 30), (1004, gy2 - 22), (986, gy2 - 10), (1000, gy2 + 4), (972, gy2 + 8),
                             (950, gy2 + 4), (880, gy2 + 10), (800, gy2 + 10), (748, gy2 + 4), (726, gy2 + 12)]), 1.5)
    pl.wash(hide, COAT_SIDE, 1.0, edge=0.2, var=0.14, grad=([(0, COAT_DORSAL), (1, COAT_LOW)], (850, gy2 - 30, 850, gy2 + 10)))
    pl.strokes(hide, 520, darker(COAT_SIDE, 0.6), length=(2, 5), width=(0.4, 0.6), opacity=0.4, angle=lambda a, b: 0.15)
    edge_white = pl.minus(hide, pl.grow(hide, -3))
    pl.wash(pl.inter(pl.blur(edge_white, 1.0), pl.poly([(690, gy2 - 2), (1006, gy2 - 2), (1006, gy2 + 14), (690, gy2 + 14)], smooth=False)), BELLY, 0.75, edge=0)
    pl.form(hide, radius=5, dark=(60, 40, 26), strength=0.35, hi=WHITE, hi_strength=0.1)
    pl.contour(hide, 0.5, 1.2, opacity=0.9)
    for i, (bx, by, bw, bh) in enumerate([(800, gy2 - 30, 70, 26), (868, gy2 - 26, 58, 22), (830, gy2 - 54, 60, 24)]):
        pk = pl.poly([(bx - bw / 2, by + bh / 2), (bx - bw / 2 + 4, by - bh / 2), (bx + bw / 2, by - bh / 2 - 2), (bx + bw / 2 - 2, by + bh / 2)], smooth=False)
        pl.wash(pk, (232, 220, 196), 1.0, edge=0.2)
        pl.form(pk, radius=4, dark=(150, 130, 100), strength=0.5, hi=WHITE, hi_strength=0.2)
        pl.ink([(bx - bw / 2 + 2, by - bh / 2 + 4), (bx + bw / 2 - 2, by + bh / 2 - 6)], 0.5, (170, 150, 120), 0.6, smooth=False)
        pl.ink([(bx - 6, by - bh / 2 - 1), (bx - 6, by + bh / 2)], 1.0, (150, 60, 50), 0.9, smooth=False)        # twine
        pl.ink([(bx - bw / 2 + 2, by + 1), (bx + bw / 2 - 2, by)], 1.0, (150, 60, 50), 0.9, smooth=False)
        pl.contour(pk, 0.4, 1.0, opacity=0.9)
    loin = pl.poly([(902, gy2 - 12), (912, gy2 - 20), (950, gy2 - 22), (982, gy2 - 18), (994, gy2 - 13), (978, gy2 - 8), (940, gy2 - 6), (910, gy2 - 6)])
    pl.wash(loin, (128, 30, 30), 1.0, edge=0.3, var=0.1)
    pl.form(loin, radius=3, dark=(70, 12, 14), strength=0.5, hi=(236, 200, 196), hi_strength=0.45)
    pl.strokes(loin, 40, (90, 18, 20), length=(3, 7), width=(0.4, 0.6), opacity=0.4, angle=lambda a, b: 0.0)
    pl.contour(loin, 0.4, 1.0, color=(60, 16, 16), opacity=0.9)
    A['trophy'] = (846, 488)
    save(pl, 'harvest')
    return A


# ================================================================================================ field tips (4 vignettes, 512 x 256 each)
def vignette_ground(pl, x0, x1, gy, seed=0, color=(178, 166, 118)):
    gm = pl.rough(pl.poly([(x0, gy - 5), (x1, gy - 7), (x1 - 12, gy + 26), (x0 + 12, gy + 28)]), 4, 'lo')
    gm = pl.inter(gm, pl.blur(pl.poly([(x0 + 20, gy - 20), (x1 - 20, gy - 20), (x1 - 20, gy + 34), (x0 + 20, gy + 34)], smooth=False), 12))
    pl.wash(gm, color, 0.6, edge=0.12, var=0.22, bleed=1.5)
    pl.ink([(x, gy - 2 + math.sin(x * 0.05 + seed) * 1.0) for x in np.linspace(x0 + 30, x1 - 30, 30)], (0.2, 1.0, 1.0, 0.2), INK_SOFT, 0.6)


def wall_tent(pl, x, gy, w, h):
    """Canvas wall tent, side-on with the door flap toward the viewer's left."""
    roof = pl.poly([(x - w * 0.5, gy - h * 0.55), (x - w * 0.36, gy - h), (x + w * 0.42, gy - h), (x + w * 0.56, gy - h * 0.55)], smooth=False)
    wall = pl.poly([(x - w * 0.5, gy - h * 0.55), (x + w * 0.56, gy - h * 0.55), (x + w * 0.56, gy), (x - w * 0.5, gy)], smooth=False)
    front = pl.poly([(x - w * 0.5, gy), (x - w * 0.5, gy - h * 0.55), (x - w * 0.36, gy - h), (x - w * 0.22, gy - h * 0.55), (x - w * 0.22, gy)], smooth=False)
    pl.wash(wall, CANVAS_TAN, 1.0, edge=0.2, var=0.1)
    pl.wash(roof, lighter(CANVAS_TAN, 0.2), 1.0, edge=0.2, var=0.1)
    pl.wash(front, darker(CANVAS_TAN, 0.88), 1.0, edge=0.2)
    door = pl.poly([(x - w * 0.43, gy), (x - w * 0.36, gy - h * 0.75), (x - w * 0.29, gy)], smooth=False)
    pl.wash(door, (70, 56, 40), 0.9, edge=0.2)
    pl.wash(pl.blur(pl.poly([(x - w * 0.22, gy - h * 0.55), (x + w * 0.56, gy - h * 0.55), (x + w * 0.56, gy - h * 0.45), (x - w * 0.22, gy - h * 0.45)], smooth=False), 2), (110, 90, 60), 0.35, edge=0)
    m = Plate.union(roof, wall, front)
    for f in (0.0, 0.33, 0.66):
        xx = x - w * 0.22 + (w * 0.78) * f + w * 0.13
        pl.ink([(xx, gy - h), (xx, gy - h * 0.55)], 0.5, (150, 130, 96), 0.6, smooth=False)
    pipe = pl.poly([(x + w * 0.30, gy - h - 26), (x + w * 0.34, gy - h - 26), (x + w * 0.34, gy - h + 2), (x + w * 0.30, gy - h + 2)], smooth=False)
    pl.wash(pipe, STEEL, 1.0)
    pl.contour(Plate.union(m, pipe), 0.5, 1.3, opacity=0.9)
    for i in range(4):     # stove smoke
        pl.wash(pl.blur(pl.ellipse(x + w * 0.32 + i * 9, gy - h - 34 - i * 14, 6 + i * 3, 5 + i * 2), 3), (200, 200, 196), 0.35, edge=0)
    # guy lines and stakes
    for (ax_, ay_), (bx_, by_) in (((x - w * 0.5, gy - h * 0.55), (x - w * 0.72, gy)), ((x + w * 0.56, gy - h * 0.55), (x + w * 0.76, gy))):
        pl.ink([(ax_, ay_), (bx_, by_)], 0.5, INK_SOFT, 0.7, smooth=False)


def camp_post(pl, x, gy, h):
    cairn = pl.mask()
    for (dx, dy, rx, ry) in ((-12, -6, 13, 8), (10, -6, 12, 8), (0, -16, 12, 8), (-4, -24, 9, 6)):
        pl.ellipse(x + dx, gy + dy, rx, ry, m=cairn)
    pl.wash(cairn, ROCK, 1.0, edge=0.25, var=0.15)
    pl.form(cairn, radius=4, dark=(80, 80, 70), strength=0.5, hi=WHITE, hi_strength=0.2)
    pl.wash(pl.inter(pl.rough(pl.ellipse(x - 6, gy - 22, 10, 5), 1), cairn), (110, 140, 70), 0.8)   # moss
    pl.contour(cairn, 0.4, 1.0, opacity=0.9)
    pole = pl.poly([(x - 2.4, gy - 20), (x + 2.4, gy - 20), (x + 1.8, gy - h), (x - 1.8, gy - h)], smooth=False)
    pl.wash(pole, (150, 116, 76), 1.0)
    pl.contour(pole, 0.3, 0.8, opacity=0.9)
    flag = pl.poly([(x + 2, gy - h + 2), (x + 36, gy - h + 8), (x + 30, gy - h + 16), (x + 38, gy - h + 26), (x + 2, gy - h + 24)])
    pl.wash(flag, (60, 104, 80), 1.0, edge=0.2)
    pl.form(flag, radius=2, dark=(20, 50, 30), strength=0.5)
    pl.contour(flag, 0.3, 0.8, opacity=0.9)


def campfire(pl, x, gy):
    for i, a in enumerate((-0.5, 0.5, 0.0)):
        pl.ink([(x - math.cos(a) * 16, gy + 2 - i), (x + math.cos(a) * 16, gy - 2 - i)], 4.5, (110, 80, 52), 1.0, smooth=False)
    flame = pl.poly([(x - 10, gy - 2), (x - 6, gy - 16), (x - 2, gy - 10), (x, gy - 26), (x + 4, gy - 12), (x + 8, gy - 18), (x + 10, gy - 2)])
    pl.wash(flame, (240, 170, 60), 1.0, edge=0.2, grad=([(0, (250, 210, 90)), (1, (220, 100, 40))], (x, gy - 26, x, gy)))
    pl.contour(flame, 0.3, 0.7, color=(170, 70, 20), opacity=0.8)
    for i in range(5):
        pl.wash(pl.blur(pl.ellipse(x + 3 + i * 4, gy - 34 - i * 13, 4 + i * 2.5, 4 + i * 2), 3), (210, 206, 200), 0.3, edge=0)


def atv(pl, x, gy, s):
    """Side-view utility ATV (facing right), s = px per metre."""
    def P(pts):
        return [(x + px * s, gy - py * s) for px, py in pts]
    # wheels
    wheels = []
    for wx in (-0.62, 0.62):
        cx, cy = x + wx * s, gy - 0.30 * s
        t = pl.ellipse(cx, cy, 0.30 * s, 0.30 * s)
        pl.wash(t, (44, 42, 40), 1.0, edge=0.15)
        for k_ in range(14):
            a = k_ / 14 * 2 * math.pi
            pl.flat(pl.ellipse(cx + math.cos(a) * 0.28 * s, cy + math.sin(a) * 0.28 * s, 0.035 * s, 0.035 * s), (30, 28, 26), 1.0)
        rim = pl.ellipse(cx, cy, 0.15 * s, 0.15 * s)
        pl.wash(rim, (150, 150, 146), 1.0, edge=0.2)
        pl.form(rim, radius=2, dark=(60, 60, 60), strength=0.5, hi=WHITE, hi_strength=0.3)
        pl.flat(pl.ellipse(cx, cy, 0.04 * s, 0.04 * s), (80, 80, 80), 1.0)
        wheels.append(Plate.union(t, rim))
    # frame, body and fenders
    body = pl.poly(P([(-0.95, 0.62), (-0.86, 0.70), (-0.40, 0.66), (-0.30, 0.78), (0.20, 0.80), (0.42, 0.70), (0.90, 0.70), (1.00, 0.60),
                      (0.92, 0.50), (0.70, 0.58), (0.36, 0.50), (-0.30, 0.48), (-0.62, 0.58), (-0.88, 0.50)]))
    pl.wash(body, RED_GEAR, 1.0, edge=0.2, var=0.08)
    pl.form(body, radius=4, dark=(80, 20, 16), strength=0.5, hi=WHITE, hi_strength=0.3)
    seat = pl.poly(P([(-0.40, 0.78), (-0.32, 0.86), (0.18, 0.86), (0.24, 0.80)]))
    pl.wash(seat, (40, 38, 36), 1.0, edge=0.2)
    under = pl.poly(P([(-0.30, 0.48), (0.36, 0.50), (0.30, 0.34), (-0.26, 0.32)]), smooth=False)
    pl.wash(under, (60, 60, 58), 1.0)
    bars = pl.stroke_mask(P([(0.30, 0.80), (0.36, 0.98), (0.30, 1.04)]), 0.03 * s)
    pl.stroke_mask(P([(0.22, 1.03), (0.42, 1.05)]), 0.035 * s, m=bars)
    pl.wash(bars, (40, 40, 40), 1.0)
    racks = Plate.union(pl.stroke_mask(P([(-0.92, 0.74), (-0.48, 0.74)]), 0.025 * s), pl.stroke_mask(P([(0.50, 0.76), (0.92, 0.76)]), 0.025 * s))
    pl.wash(racks, (50, 50, 50), 1.0)
    lamp = pl.ellipse(*P([(0.96, 0.64)])[0], 0.04 * s, 0.03 * s)
    pl.flat(lamp, (240, 230, 190), 1.0)
    m = Plate.union(body, seat, under, bars, racks, *wheels)
    pl.contour(m, 0.5, 1.4, opacity=0.95)
    return m


def jerry_can(pl, x, gy, h):
    w = h * 0.70
    body = pl.poly([(x - w / 2, gy), (x - w / 2, gy - h * 0.86), (x - w / 2 + 6, gy - h), (x + w / 2, gy - h), (x + w / 2, gy)], smooth=False)
    pl.wash(body, (184, 40, 34), 1.0, edge=0.2)
    pl.form(body, radius=4, dark=(90, 16, 14), strength=0.5, hi=WHITE, hi_strength=0.3)
    pl.ink([(x - w / 2 + 4, gy - h * 0.15), (x + w / 2 - 4, gy - h * 0.85)], 1.6, (130, 24, 20), 0.9, smooth=False)
    pl.ink([(x - w / 2 + 4, gy - h * 0.85), (x + w / 2 - 4, gy - h * 0.15)], 1.6, (130, 24, 20), 0.9, smooth=False)
    handle = pl.stroke_mask([(x - 2, gy - h), (x - 2, gy - h - 8), (x + w / 2 - 4, gy - h - 8), (x + w / 2 - 4, gy - h)], 3.2, smooth=False)
    pl.wash(handle, (160, 34, 28), 1.0)
    spout = pl.poly([(x - w / 2 + 2, gy - h), (x - w / 2 - 6, gy - h - 9), (x - w / 2 - 2, gy - h - 12), (x - w / 2 + 8, gy - h - 3)], smooth=False)
    pl.wash(spout, (90, 90, 88), 1.0)
    pl.contour(Plate.union(body, handle, spout), 0.5, 1.2, opacity=0.95)


def season_strip(pl, x0, gy, step, h):
    """The year in four trees: spring blossom, summer green, fall colour, winter bare in snow."""
    rng = random.Random(4)
    for i in range(4):
        x = x0 + i * step
        tw = h * 0.05
        trunk = pl.poly([(x - tw, gy), (x + tw, gy), (x + tw * 0.6, gy - h * 0.58), (x - tw * 0.6, gy - h * 0.58)], smooth=False)
        if i == 3:
            pl.wash(pl.rough(pl.poly([(x - 60, gy - 2), (x + 60, gy - 3), (x + 54, gy + 14), (x - 56, gy + 14)]), 2), SNOW, 0.95, edge=0.2)
        pl.wash(trunk, BARK, 1.0, edge=0.2)
        pl.form(trunk, radius=2, dark=BARK_DARK, strength=0.5)
        branches = pl.mask()
        for j in range(7):
            a = -math.pi / 2 + (j - 3) * 0.32
            L = h * rng.uniform(0.28, 0.4)
            bx, by = x, gy - h * rng.uniform(0.42, 0.62)
            pl.stroke_mask([(bx, by), (bx + math.cos(a) * L * 0.6, by + math.sin(a) * L * 0.6), (bx + math.cos(a + 0.15) * L, by + math.sin(a + 0.15) * L)], (2.2, 1.2, 0.4), m=branches)
        if i == 3:
            pl.wash(branches, BARK_DARK, 1.0)
            snowcap = pl.inter(branches, ImageChops_offset_up(branches, 2 * pl.S))
            pl.flat(pl.minus(branches, ImageChops_offset_up(branches, -int(1.2 * pl.S))), WHITE, 0.9)
            pl.contour(Plate.union(trunk, branches), 0.35, 0.9, opacity=0.85)
            continue
        col = [(170, 196, 118), (78, 118, 62), (214, 124, 50)][i]
        cy = gy - h * 0.70
        r = h * 0.30
        crown = pl.rough(Plate.union(pl.ellipse(x, cy, r, r * 0.86), pl.ellipse(x - r * 0.55, cy + r * 0.25, r * 0.55, r * 0.45),
                                     pl.ellipse(x + r * 0.55, cy + r * 0.25, r * 0.55, r * 0.45)), 1.2)
        pl.wash(branches, BARK_DARK, 1.0)
        pl.wash(crown, col, 0.95 if i else 0.75, edge=0.3, var=0.16)
        pl.form(crown, radius=r * 0.35, dark=darker(col, 0.5), strength=0.5, hi=lighter(col, 0.4), hi_strength=0.3)
        pl.stipple(crown, int(r * 5), darker(col, 0.6), r=(0.4, 1.0), opacity=0.45)
        if i == 0:
            pl.stipple(crown, int(r * 3), (244, 214, 226), r=(0.8, 1.7), opacity=0.95)
        if i == 2:
            leaf_litter(pl, x, gy + 4, 40, 4, 14, seed=7, colors=((214, 124, 50), (200, 150, 60), (180, 90, 40)), size=(2.2, 3.4))
        pl.contour(Plate.union(crown, trunk), 0.4, 1.1, opacity=0.85)
    for j in range(16):   # falling snow over the winter tree
        sx_, sy_ = x0 + 3 * step - 50 + (j * 37) % 100, gy - h + (j * 53) % int(h * 0.9)
        pl.flat(pl.ellipse(sx_, sy_, 1.5, 1.5), WHITE, 0.95)
        pl.contour(pl.ellipse(sx_, sy_, 1.5, 1.5), 0.2, 0.4, color=(150, 160, 170), opacity=0.6)


def ImageChops_offset_up(m, dy):
    from PIL import ImageChops
    return ImageChops.offset(m, 0, -int(dy))


def plate_tips():
    pl = Plate(W, H, seed=99)
    # 1. camps & tents (top-left)
    vignette_ground(pl, 20, 492, 214, seed=1)
    wall_tent(pl, 210, 214, 190, 120)
    camp_post(pl, 380, 214, 120)
    campfire(pl, 446, 214)
    tufts(pl, 40, 480, 214, 20, seed=2, h=(6, 12))
    # 2. ATV & fuel (top-right)
    vignette_ground(pl, 532, 1004, 214, seed=2)
    atv(pl, 730, 214, 120)
    jerry_can(pl, 930, 214, 60)
    tufts(pl, 550, 990, 214, 18, seed=3, h=(6, 12))
    # 3. seasons (bottom-left)
    vignette_ground(pl, 20, 492, 470, seed=3)
    season_strip(pl, 86, 470, 112, 150)
    # 4. get low, get steady: prone with the rifle on its bipod (bottom-right)
    vignette_ground(pl, 532, 1004, 470, seed=4)
    h = hunter(pl, 720, 470, 150, facing=1, pose='prone', weapon='rifle')
    rifle(pl, h['X'], (0.66, 0.29), (1.70, 0.35), bipod=0.0)
    tufts(pl, 550, 990, 470, 26, seed=6, h=(6, 12))
    save(pl, 'tips')


# ================================================================================================ welcome banner
def plate_welcome():
    BW, BH = 1152, 384
    pl = Plate(BW, BH, seed=123)
    full = pl.poly([(0, 0), (BW, 0), (BW, BH), (0, BH)], smooth=False)
    pl.wash(full, (236, 200, 150), 1.0, edge=0, var=0.03, grain=0.05,
            grad=([(0, (150, 176, 196)), (0.40, (226, 206, 176)), (0.62, (244, 196, 140)), (1, (238, 184, 128))], (0, 0, 0, BH * 0.62)))
    sunm = pl.ellipse(840, 196, 120, 120)
    pl.wash(pl.blur(sunm, 40), (255, 236, 190), 0.75, edge=0)
    pl.wash(pl.ellipse(840, 200, 30, 30), (255, 244, 214), 0.95, edge=0)
    # cloud streaks
    for (x, y, w_) in ((240, 70, 220), (560, 50, 160), (980, 90, 200), (760, 120, 140)):
        pl.wash(pl.blur(pl.ellipse(x, y, w_, 8), 6), (248, 226, 200), 0.6, edge=0)
    rng = random.Random(3)
    layers = [(200, (156, 156, 178), 40, 0.55), (232, (122, 128, 152), 34, 0.8), (262, (92, 100, 120), 28, 1.1)]
    for li, (base, col, amp, tscale) in enumerate(layers):
        f = lambda x, li=li, base=base, amp=amp: base - amp * (0.5 + 0.5 * math.sin(x * 0.006 + li * 2.1)) - amp * 0.4 * math.sin(x * 0.017 + li)
        pts = [(0, BH)] + [(x, f(x)) for x in np.arange(0, BW + 8, 8)] + [(BW, BH)]
        m = pl.poly(pts, smooth=False)
        pl.wash(m, col, 1.0, edge=0.12, var=0.05, grain=0.08, grad=([(0, lighter(col, 0.08)), (1, darker(col, 0.85))], (0, base - amp, 0, base + 60)))
        for x in np.arange(-10, BW + 10, 7 + li * 2):
            if rng.random() < 0.25:
                continue
            hh = rng.uniform(12, 24) * tscale
            conifer(pl, x + rng.uniform(-3, 3), f(x) + 3 + hh * 0.08, hh, color=darker(col, 0.86), seed=int(x * 7 + li), ink=False, detail=False)
        mist = pl.blur(pl.poly([(0, base + 8), (BW, base + 2), (BW, base + 34), (0, base + 38)], smooth=False), 10)
        pl.wash(mist, (238, 222, 206), 0.5, edge=0)
    # near ridge with the buck
    fn = lambda x: 300 - 22 * math.sin(x * 0.004 + 0.7) - 8 * math.sin(x * 0.013 + 0.4)
    nm = pl.poly([(0, BH)] + [(x, fn(x)) for x in np.arange(0, BW + 8, 8)] + [(BW, BH)], smooth=False)
    pl.wash(nm, (54, 62, 58), 1.0, edge=0.2, var=0.08, grad=([(0, (70, 76, 68)), (1, (34, 40, 38))], (0, 270, 0, BH)))
    for x in list(np.arange(-10, 360, 13)) + list(np.arange(980, BW + 10, 13)):
        hh = rng.uniform(40, 96)
        conifer(pl, x + rng.uniform(-4, 4), fn(x) + 4, hh, color=(40, 52, 46), seed=int(x * 3), ink=False, detail=False)
    for x in np.arange(380, 980, 22):
        tufts(pl, x, x + 10, fn(x) + 1, 2, seed=int(x), h=(5, 11), color=(40, 50, 44))
    # buck on the rise, backlit: dark coat, warm rim light
    bx = 600
    d = whitetail(pl, bx, fn(bx + 60) + 2, 118, facing=-1, neck_ang=-0.05, detail=0.5)
    pl.wash(pl.blur(d['body'], 0.4), (46, 40, 36), 0.86, edge=0)
    rim = pl.minus(d['body'], ImageChops_shift(d['body'], int(-2.4 * pl.S), int(1.8 * pl.S)))
    pl.contour(d['body'], 0.4, 1.0, color=(30, 26, 22), opacity=0.9)
    pl.wash(pl.blur(rim, 0.5), (252, 206, 138), 0.85, edge=0)
    pl.save(os.path.join(OUT, 'welcome.png'))
    on_paper(pl.image()).convert('RGB').save(os.path.join(PREV, 'welcome.jpg'), quality=88)
    print('wrote welcome')


def ImageChops_shift(m, dx, dy):
    from PIL import ImageChops
    return ImageChops.offset(m, dx, dy)


# ================================================================================================ icon atlas (4 x 4, 64 px)
ICONS = ['wind', 'sign', 'glass', 'stalk', 'shot', 'trail', 'harvest', 'tips',
         'deer', 'blizzard', 'season', 'predator', 'winded', 'spotted', 'trophy', 'journal']


def plate_icons():
    pl = Plate(256, 256, seed=7, S=4)
    import tracks as TRK
    for i, name in enumerate(ICONS):
        cx, cy = (i % 4) * 64 + 32, (i // 4) * 64 + 32
        disc = pl.ellipse(cx, cy, 29.5, 29.5)
        pl.wash(disc, (238, 230, 210), 1.0, edge=0.12, var=0.05, grain=0.08)
        pl.form(disc, radius=10, dark=(150, 130, 100), strength=0.25)
        ring = pl.minus(disc, pl.ellipse(cx, cy, 27.2, 27.2))
        pl.flat(ring, (120, 92, 52), 1.0)
        pl.contour(disc, 0.8, 1.2, color=(60, 44, 28), opacity=0.9)
        pl.set_clip(pl.ellipse(cx, cy, 26.6, 26.6))
        if name == 'wind':
            bottle = pl.poly([(cx - 14, cy + 16), (cx - 14, cy - 2), (cx - 10, cy - 6), (cx - 6, cy - 6), (cx - 2, cy - 2), (cx - 2, cy + 16)])
            pl.wash(bottle, (220, 222, 214), 1.0, edge=0.2)
            pl.form(bottle, radius=2, dark=(120, 120, 120), strength=0.5)
            cap = pl.poly([(cx - 11, cy - 6), (cx - 9, cy - 12), (cx - 7, cy - 12), (cx - 5, cy - 6)], smooth=False)
            pl.wash(cap, BLAZE, 1.0)
            pl.contour(Plate.union(bottle, cap), 0.8, 1.4, opacity=0.95)
            for k_, y in enumerate((-16, -9, -2)):
                pts = [(x, cy + y + math.sin(x * 0.4 + k_) * 1.4) for x in np.linspace(cx - 2 + k_ * 2, cx + 18 - k_ * 2, 8)]
                pl.arrow(pts, 1.6, WIND_BLUE, 5, 0.95)
            pl.wash(pl.blur(pl.ellipse(cx - 6, cy - 16, 5, 3), 1.2), (200, 200, 196), 0.8, edge=0)
        elif name == 'sign':
            TRK.impression(pl, TRK.cloven(7.5, 5.6), cx - 7, cy + 6, 3.6, rot=0.15)
            TRK.impression(pl, TRK.cloven(7.5, 5.6), cx + 8, cy - 8, 3.6, rot=0.15)
        elif name == 'glass':
            for sd in (-1, 1):
                barrel = pl.poly([(cx + sd * 4, cy - 10), (cx + sd * 15, cy - 10), (cx + sd * 17, cy + 12), (cx + sd * 2, cy + 12)], smooth=False)
                pl.wash(barrel, (54, 60, 52), 1.0, edge=0.2)
                pl.form(barrel, radius=2, dark=(10, 10, 10), strength=0.5, hi=WHITE, hi_strength=0.3)
                lens = pl.ellipse(cx + sd * 9.5, cy + 12, 7.5, 3.2)
                pl.wash(lens, (120, 156, 180), 1.0)
                pl.contour(Plate.union(barrel, lens), 0.8, 1.3, opacity=0.95)
            bridge = pl.poly([(cx - 4, cy - 6), (cx + 4, cy - 6), (cx + 4, cy + 2), (cx - 4, cy + 2)], smooth=False)
            pl.wash(bridge, (40, 44, 40), 1.0)
            pl.contour(bridge, 0.6, 1.0, opacity=0.9)
        elif name == 'stalk':
            for k_, (x, y, a) in enumerate(((-10, 12, 0.1), (2, 2, 0.1), (-6, -10, 0.1), (8, -20, 0.1))):
                sole = pl.poly([(cx + x - 3.2, cy + y + 6), (cx + x - 3.6, cy + y - 2), (cx + x - 2, cy + y - 7), (cx + x + 2, cy + y - 7), (cx + x + 3.4, cy + y - 2), (cx + x + 2.6, cy + y + 6)])
                heel = pl.ellipse(cx + x, cy + y + 9, 2.8, 2.6)
                mm = Plate.union(sole, heel)
                pl.wash(mm, (96, 74, 50), 0.6 + 0.12 * k_, edge=0.3)
                pl.contour(mm, 0.6, 1.0, color=(60, 44, 28), opacity=0.7 + 0.1 * k_)
        elif name == 'shot':
            pl.wash(pl.ellipse(cx, cy, 13, 13), (230, 150, 150), 0.6, edge=0.25)
            heart = pl.poly([(cx, cy + 9), (cx - 8, cy), (cx - 8, cy - 5), (cx - 4, cy - 8), (cx, cy - 5), (cx + 4, cy - 8), (cx + 8, cy - 5), (cx + 8, cy)])
            pl.wash(heart, HEART, 1.0, edge=0.25)
            pl.form(heart, radius=2, dark=(80, 10, 10), strength=0.5, hi=WHITE, hi_strength=0.3)
            pl.contour(heart, 0.6, 1.0, color=(70, 10, 10), opacity=0.9)
            pl.ink([(cx + 18 * math.cos(t), cy + 18 * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 60)], 1.4, (40, 30, 24), 0.95, smooth=False)
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                pl.ink([(cx + dx * 12, cy + dy * 12), (cx + dx * 24, cy + dy * 24)], 1.6, (40, 30, 24), 0.95, smooth=False)
        elif name == 'trail':
            for (x, y, r) in ((-10, 10, 5.2), (2, 0, 4.2), (11, -10, 3.4), (-2, -16, 2.4)):
                drop(pl, cx + x, cy + y, r, BLOOD_BRIGHT, ang=-0.8, tail=0.5, seed=int(x * 7 + y))
        elif name == 'harvest':
            knife(pl, cx + 2, cy + 2, 46, -0.75)
        elif name == 'tips':
            wall_tent(pl, cx - 1, cy + 12, 30, 22)
        elif name == 'deer':
            whitetail(pl, cx + 10, cy + 74, 58, facing=1, neck_ang=-0.05, detail=0.3, far_legs=False)
        elif name == 'blizzard':
            for k_ in range(6):
                a = k_ * math.pi / 3
                tip = (cx + math.cos(a) * 20, cy + math.sin(a) * 20)
                pl.ink([(cx, cy), tip], 2.4, (70, 120, 160), 1.0, smooth=False)
                for f in (0.55, 0.8):
                    bx, by = cx + math.cos(a) * 20 * f, cy + math.sin(a) * 20 * f
                    for sd in (-1, 1):
                        pl.ink([(bx, by), (bx + math.cos(a + sd * 0.8) * 6, by + math.sin(a + sd * 0.8) * 6)], 1.8, (70, 120, 160), 1.0, smooth=False)
            pl.flat(pl.ellipse(cx, cy, 3, 3), (70, 120, 160), 1.0)
        elif name == 'season':
            pts = []
            for k_, (r, a) in enumerate([(22, -90), (12, -64), (18, -42), (11, -24), (20, 0), (9, 22), (14, 40), (6, 64), (6, 116), (14, 140), (9, 158), (20, 180), (11, 204), (18, 222), (12, 244)]):
                pts.append((cx + r * math.cos(math.radians(a)), cy - 2 + r * math.sin(math.radians(a))))
            leaf_m = pl.poly(pts, smooth=False)
            pl.wash(leaf_m, (206, 96, 40), 1.0, edge=0.3, var=0.15, grad=([(0, (226, 150, 50)), (1, (180, 60, 30))], (cx - 10, cy - 20, cx + 10, cy + 10)))
            for a in (-90, -30, -150, 30, 150):
                pl.ink([(cx, cy + 4), (cx + 16 * math.cos(math.radians(a)), cy - 2 + 16 * math.sin(math.radians(a)))], 0.7, (130, 40, 20), 0.8, smooth=False)
            pl.ink([(cx, cy + 4), (cx + 1, cy + 22)], 1.6, (110, 70, 40), 1.0, smooth=False)
            pl.contour(leaf_m, 0.6, 1.1, color=(110, 40, 20), opacity=0.9)
        elif name == 'predator':
            wolf = TRK.canid(11.5, 9.5)
            m_, T_ = TRK.impression(pl, wolf['shapes'], cx, cy + 2, 3.4)
            TRK.claw_marks(pl, T_, wolf['claws'], 3.4)
        elif name == 'winded':
            for k_ in range(3):
                pts = [(x, cy - 10 + k_ * 9 + math.sin(x * 0.35 + k_) * 3) for x in np.linspace(cx - 18, cx + 18, 12)]
                pl.ink(pts, (1.0, 2.6, 1.0), SCENT_DARK, 0.95)
            pl.flat(pl.ellipse(cx - 18, cy + 18, 0, 0), INK, 0)
        elif name == 'spotted':
            eye = pl.poly([(cx - 20, cy), (cx - 8, cy - 10), (cx + 8, cy - 10), (cx + 20, cy), (cx + 8, cy + 10), (cx - 8, cy + 10)])
            pl.wash(eye, (246, 240, 228), 1.0, edge=0.2)
            iris = pl.ellipse(cx, cy, 8, 8)
            pl.wash(iris, (120, 84, 46), 1.0, edge=0.3)
            pl.flat(pl.ellipse(cx, cy, 4, 4), (20, 16, 14), 1.0)
            pl.flat(pl.ellipse(cx - 2.5, cy - 2.5, 1.6, 1.6), WHITE, 0.95)
            pl.contour(eye, 0.8, 1.4, opacity=0.95)
        elif name == 'trophy':
            rack_front(pl, cx, cy + 15, 76)
        elif name == 'journal':
            book = pl.poly([(cx - 16, cy - 18), (cx + 14, cy - 18), (cx + 16, cy + 18), (cx - 14, cy + 18)], smooth=False)
            pl.wash(book, (92, 70, 46), 1.0, edge=0.2)
            pl.form(book, radius=3, dark=(40, 28, 18), strength=0.5, hi=WHITE, hi_strength=0.2)
            band = pl.poly([(cx + 6, cy - 18), (cx + 9, cy - 18), (cx + 11, cy + 18), (cx + 8, cy + 18)], smooth=False)
            pl.wash(band, (180, 150, 80), 1.0)
            pl.contour(book, 0.8, 1.3, opacity=0.95)
            TRK.impression(pl, TRK.cloven(7.5, 5.6), cx - 3, cy, 2.4, mud=(200, 170, 120), outline=True)
        pl.set_clip(None)
    pl.save(os.path.join(OUT, 'icons.png'))
    big = pl.image().resize((512, 512), Image.LANCZOS)
    dark = Image.new('RGBA', (512, 512), (24, 32, 25, 255))
    dark.alpha_composite(big)
    dark.convert('RGB').save(os.path.join(PREV, 'icons.jpg'), quality=90)
    print('wrote icons')


# ================================================================================================ main
if __name__ == '__main__':
    if want('icons'):
        plate_icons()
    if want('welcome'):
        plate_welcome()
    if want('tips'):
        plate_tips()
    if want('harvest'):
        print('harvest', {k: (round(v[0] / W, 3), round(v[1] / H, 3)) for k, v in plate_harvest().items()})
    if want('blood'):
        print('blood', {k: (round(v[0] / W, 3), round(v[1] / H, 3)) for k, v in plate_blood().items()})
    if want('stalk'):
        print('stalk', {k: (round(v[0] / W, 3), round(v[1] / H, 3)) for k, v in plate_stalk().items()})
    if want('glass'):
        print('glass', {k: (round(v[0] / W, 3), round(v[1] / H, 3)) for k, v in plate_glass().items()})
    if want('sign'):
        print('sign', {k: (round(v[0] / W, 3), round(v[1] / H, 3)) for k, v in plate_sign().items()})
    if want('wind'):
        print('wind', {k: (round(v[0] / W, 3), round(v[1] / H, 3)) for k, v in plate_wind().items()})
    if want('tracks'):
        plate_tracks()
    if want('vitals'):
        v, q = plate_vitals()
        print('aim', [round(v['aim'][0] / W, 3), round(v['aim'][1] / H, 3)], 'q', {k: [round(p[0] / W, 3), round(p[1] / H, 3)] for k, p in q.items()})
