"""[fieldbook] Shared scenery for the Field School plates: ground, grass, trees, brush, leaves, hunters (side view)
and top-down animals. Same ink-and-wash language as fieldart.py."""
import math
from fieldart import *


# ------------------------------------------------------------------------------------------------ ground & plants
def ground_band(pl, x0, x1, y, depth=26, color=GRASS, seed=0, opacity=0.55, wave=2.5, line=True, fade=True):
    """A soft watercolour strip of ground from x0..x1 whose top edge undulates around y."""
    rng = random.Random(seed)
    top = []
    x = x0
    while x <= x1 + 1:
        top.append((x, y + math.sin(x * 0.021 + seed) * wave + math.sin(x * 0.067 + seed * 2) * wave * 0.4))
        x += 14
    poly = top + [(x1, y + depth), (x0, y + depth)]
    m = pl.poly(poly, smooth=False)
    if fade:
        m = pl.inter(m, pl.blur(pl.poly([(x0 + 18, y - 10), (x1 - 18, y - 10), (x1 - 18, y + depth), (x0 + 18, y + depth)], smooth=False), 12))
    pl.wash(m, color, opacity, edge=0.18, grad=([(0, color), (1, lighter(color, 0.5))], (0, y, 0, y + depth)), bleed=0.8)
    if line:
        pts = [p for p in top if x0 + 6 <= p[0] <= x1 - 6]
        if len(pts) > 1:
            pl.ink(pts, (0.3, 1.2, 0.3), INK_SOFT, 0.75, smooth=True)
    return top


def tufts(pl, x0, x1, y, n, h=(5, 11), color=GRASS_DARK, seed=1, opacity=0.8, ink=True):
    rng = random.Random(seed)
    m = pl.mask()
    for _ in range(n):
        x = rng.uniform(x0, x1)
        yy = y + rng.uniform(-1.0, 1.5)
        for k in range(rng.randint(3, 6)):
            a = -math.pi / 2 + rng.uniform(-0.55, 0.55)
            L = rng.uniform(*h)
            bend = rng.uniform(-0.25, 0.25)
            pts = [(x + rng.uniform(-1.2, 1.2), yy)]
            for t in (0.5, 1.0):
                aa = a + bend * t
                pts.append((pts[0][0] + math.cos(aa) * L * t, yy + math.sin(aa) * L * t))
            pl.stroke_mask(pts, (1.0, 0.7, 0.15), m=m, smooth=False)
    pl.flat(m, color, opacity)
    if ink:
        pl.flat(pl.minus(pl.grow(m, 0.0), m), INK, 0)
    return m


def leaf_litter(pl, cx, cy, rx, ry, n, seed=4, colors=((170, 120, 60), (140, 90, 48), (190, 150, 80), (120, 100, 60)), opacity=0.85, size=(3.0, 6.0)):
    rng = random.Random(seed)
    for _ in range(n):
        a = rng.uniform(0, 2 * math.pi)
        r = math.sqrt(rng.random())
        x, y = cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r
        L = rng.uniform(*size)
        rot = rng.uniform(0, math.pi)
        m = pl.ellipse(x, y, L, L * 0.45, rot=rot)
        col = rng.choice(colors)
        pl.wash(m, col, opacity * rng.uniform(0.6, 1.0), edge=0.3, soft=0.2)
        pl.ink([(x - math.cos(rot) * L * 0.9, y - math.sin(rot) * L * 0.9), (x + math.cos(rot) * L * 0.9, y + math.sin(rot) * L * 0.9)], (0.2, 0.45, 0.1), darker(col, 0.6), 0.6, smooth=False)


def conifer(pl, x, gy, h, color=PINE, seed=0, opacity=1.0, ink=True, detail=True):
    """Spruce/fir: layered drooping branch tiers, trunk at the base."""
    rng = random.Random(seed)
    w = h * 0.36
    m = pl.mask()
    tiers = max(5, int(h / 9))
    for i in range(tiers):
        t = i / (tiers - 1)
        ytop = gy - h + h * 0.88 * t * 0.92
        yb = ytop + h * 0.22 * (0.55 + 0.45 * t)
        ww = w * (0.10 + 0.9 * t ** 0.9) / 2 * rng.uniform(0.9, 1.1)
        pts = [(x, ytop - h * 0.02), (x + ww * 0.45, ytop + (yb - ytop) * 0.45), (x + ww, yb), (x + ww * 0.6, yb - (yb - ytop) * 0.12),
               (x, yb - (yb - ytop) * 0.05), (x - ww * 0.6, yb - (yb - ytop) * 0.12), (x - ww, yb), (x - ww * 0.45, ytop + (yb - ytop) * 0.45)]
        pl.poly(pts, m=m, smooth=False)
    trunk = pl.poly([(x - h * 0.022, gy), (x + h * 0.022, gy), (x + h * 0.015, gy - h * 0.16), (x - h * 0.015, gy - h * 0.16)], smooth=False)
    pl.wash(trunk, BARK_DARK, opacity)
    m = pl.rough(m, 0.6)
    pl.wash(m, color, opacity, edge=0.3, var=0.14, grad=([(0, lighter(color, 0.12)), (1, darker(color, 0.8))], (x - w / 2, gy - h, x + w / 2, gy)))
    if detail and h > 30:
        pl.strokes(m, int(h * 2.2), darker(color, 0.6), length=(2, 4), width=(0.4, 0.7), angle=lambda a, b: math.pi * 0.75 if a > x else math.pi * 0.25, opacity=0.4)
    pl.form(m, radius=max(2, h * 0.08), dark=PINE_DARK, strength=0.45)
    if ink:
        pl.contour(m, 0.4, 1.1, opacity=0.8 * opacity)
    return m


def broadleaf(pl, x, gy, h, color=FOLIAGE, seed=0, opacity=1.0, trunk_w=None, crown=1.0, ink=True):
    rng = random.Random(seed)
    tw = trunk_w or h * 0.06
    trunk = pl.poly([(x - tw * 0.7, gy), (x + tw * 0.7, gy), (x + tw * 0.45, gy - h * 0.55), (x - tw * 0.45, gy - h * 0.55)], smooth=False)
    pl.wash(trunk, BARK, opacity, edge=0.2)
    pl.form(trunk, radius=2, dark=BARK_DARK, strength=0.5)
    cm = pl.mask()
    r = h * 0.32 * crown
    cy = gy - h * 0.66
    for i in range(11):
        a = rng.uniform(0, 2 * math.pi)
        d = rng.uniform(0, r * 0.55)
        rr = r * rng.uniform(0.38, 0.6)
        pl.ellipse(x + math.cos(a) * d * 1.2, cy + math.sin(a) * d * 0.8, rr, rr * 0.85, m=cm)
    cm = pl.rough(cm, 1.2)
    pl.wash(cm, color, opacity, edge=0.3, var=0.15, grad=([(0, lighter(color, 0.15)), (1, darker(color, 0.75))], (x - r, cy - r, x + r * 0.4, cy + r)))
    pl.form(cm, radius=r * 0.35, dark=FOLIAGE_DARK, strength=0.5)
    pl.stipple(cm, int(r * 6), darker(color, 0.6), r=(0.4, 1.0), opacity=0.45)
    if ink:
        pl.contour(Plate.union(cm, trunk), 0.4, 1.2, opacity=0.85 * opacity)
    return cm


def bush(pl, x, gy, r, color=FOLIAGE, seed=0, opacity=1.0, ink=True, flat_bottom=True):
    rng = random.Random(seed)
    m = pl.mask()
    for i in range(9):
        a = math.pi * (0.05 + 0.9 * i / 8)
        rr = r * rng.uniform(0.42, 0.6)
        cx = x + math.cos(a) * r * 0.62
        cy = gy - math.sin(a) * r * 0.55 - rr * 0.25
        pl.ellipse(cx, cy, rr, rr * 0.9, m=m)
    pl.ellipse(x, gy - r * 0.4, r * 0.75, r * 0.5, m=m)
    m = pl.rough(m, max(0.8, r * 0.04))
    if flat_bottom:
        m = pl.inter(m, pl.poly([(x - r * 2, gy - r * 3), (x + r * 2, gy - r * 3), (x + r * 2, gy + 1), (x - r * 2, gy + 1)], smooth=False))
    pl.wash(m, color, opacity, edge=0.3, var=0.15, grad=([(0, lighter(color, 0.12)), (1, darker(color, 0.7))], (x, gy - r * 1.3, x, gy)))
    pl.form(m, radius=r * 0.3, dark=FOLIAGE_DARK, strength=0.45)
    pl.stipple(m, int(r * 5), darker(color, 0.6), r=(0.4, 1.0), opacity=0.45)
    pl.strokes(m, int(r * 2), lighter(color, 0.35), length=(2, 3.5), width=(0.4, 0.7), opacity=0.35, angle=lambda a, b: -math.pi / 2)
    if ink:
        pl.contour(m, 0.4, 1.3, opacity=0.85 * opacity)
    return m


def wind_lines(pl, x0, x1, y, n=3, gap=9, color=WIND_BLUE, head=7, opacity=0.9, amp=2.0, phase=0.0, width=1.4):
    for i in range(n):
        yy = y + i * gap
        xs0 = x0 + (i % 2) * 14
        pts = [(x, yy + math.sin((x + phase + i * 40) * 0.03) * amp) for x in np.linspace(xs0, x1 - (i % 2) * 6, 14)]
        pl.arrow(pts, width, color, head, opacity)


# ------------------------------------------------------------------------------------------------ hunter (side view)
def _limb(pl, X, a, b, wa, wb, m):
    pl.stroke_mask(X([a, b]), (wa * X.s, wb * X.s), m=m, smooth=False)


def hunter(pl, ox, oy, scale, facing=1, pose='crouch', weapon='bow', opacity=1.0, blaze=True):
    """Side-view hunter, facing right (facing=1) or left. Poses: crouch (stalking), stand_glass (binoculars),
    prone (rifle on bipod), kneel (at a carcass). Model space metres, y up, ground y = 0."""
    X = Xf(ox, oy, scale, -facing)  # model faces +x; Xf facing=-1 mirrors, so pass -facing to keep +x = facing right
    X.f = facing
    J = {}
    if pose == 'crouch':
        J = dict(head=(0.28, 1.26), neck=(0.22, 1.16), sh=(0.17, 1.10), hip=(-0.05, 0.66), knee_f=(0.20, 0.48), ankle_f=(0.18, 0.06),
                 knee_b=(-0.08, 0.36), ankle_b=(-0.36, 0.08), elbow_f=(0.36, 0.90), hand_f=(0.52, 0.98), elbow_b=(0.28, 0.86), hand_b=(0.40, 1.00))
    elif pose == 'stand_glass':
        J = dict(head=(0.04, 1.66), neck=(0.02, 1.53), sh=(0.0, 1.45), hip=(-0.03, 0.95), knee_f=(0.06, 0.50), ankle_f=(0.08, 0.07),
                 knee_b=(-0.08, 0.50), ankle_b=(-0.14, 0.07), elbow_f=(0.18, 1.38), hand_f=(0.16, 1.62), elbow_b=(0.14, 1.36), hand_b=(0.13, 1.60))
    elif pose == 'kneel':
        J = dict(head=(0.22, 1.08), neck=(0.17, 0.98), sh=(0.12, 0.93), hip=(-0.10, 0.52), knee_f=(0.22, 0.46), ankle_f=(0.20, 0.05),
                 knee_b=(0.05, 0.04), ankle_b=(-0.35, 0.05), elbow_f=(0.34, 0.72), hand_f=(0.52, 0.62), elbow_b=(0.26, 0.70), hand_b=(0.44, 0.60))
    elif pose == 'stand_draw':  # [onboard] archer at full draw: bow arm out front, string hand anchored under the chin
        J = dict(head=(0.05, 1.66), neck=(0.02, 1.53), sh=(0.0, 1.45), hip=(-0.04, 0.95), knee_f=(0.10, 0.50), ankle_f=(0.16, 0.07),
                 knee_b=(-0.12, 0.50), ankle_b=(-0.22, 0.07), elbow_f=(0.32, 1.46), hand_f=(0.60, 1.47), elbow_b=(-0.24, 1.50), hand_b=(0.10, 1.50))
    elif pose == 'prone':
        J = dict(head=(0.87, 0.34), neck=(0.78, 0.28), sh=(0.69, 0.24), hip=(0.05, 0.15), knee_f=(-0.42, 0.09), ankle_f=(-0.86, 0.08),
                 knee_b=(-0.40, 0.11), ankle_b=(-0.84, 0.13), elbow_f=(0.86, 0.05), hand_f=(0.98, 0.24), elbow_b=(0.76, 0.06), hand_b=(0.84, 0.25))
    far, near, body = pl.mask(), pl.mask(), pl.mask()
    # far limbs
    _limb(pl, X, J['hip'], J['knee_b'], 0.15, 0.12, far)
    _limb(pl, X, J['knee_b'], J['ankle_b'], 0.12, 0.09, far)
    _limb(pl, X, J['sh'], J['elbow_b'], 0.10, 0.085, far)
    _limb(pl, X, J['elbow_b'], J['hand_b'], 0.085, 0.07, far)
    pl.wash(far, darker(PANTS, 0.7), opacity, edge=0.2)
    sleeve_far = pl.mask()
    _limb(pl, X, J['sh'], J['elbow_b'], 0.10, 0.085, sleeve_far)
    _limb(pl, X, J['elbow_b'], J['hand_b'], 0.085, 0.07, sleeve_far)
    pl.wash(sleeve_far, darker(CAMO, 0.7), opacity, edge=0.2)
    pl.contour(far, 0.4, 1.0, opacity=0.8 * opacity)
    # boots (far)
    def boot(ank, knee, m):
        dirx = 1 if pose != 'prone' else 0
        if pose == 'prone':
            pts = [(ank[0] + 0.02, ank[1] - 0.04), (ank[0] - 0.12, ank[1] - 0.05), (ank[0] - 0.13, ank[1] + 0.06), (ank[0] + 0.03, ank[1] + 0.06)]
        else:
            pts = [(ank[0] - 0.06, ank[1] + 0.07), (ank[0] - 0.07, 0.0), (ank[0] + 0.20, 0.0), (ank[0] + 0.19, 0.04), (ank[0] + 0.06, 0.07)]
        pl.poly(X(pts), m=m, smooth=False)
    bf = pl.mask()
    boot(J['ankle_b'], J['knee_b'], bf)
    pl.wash(bf, darker(BOOT, 0.8), opacity)
    pl.contour(bf, 0.4, 1.0, opacity=0.8 * opacity)
    # legs, torso
    legs = pl.mask()
    _limb(pl, X, J['hip'], J['knee_f'], 0.17, 0.13, legs)
    _limb(pl, X, J['knee_f'], J['ankle_f'], 0.13, 0.10, legs)
    pl.wash(legs, PANTS, opacity, edge=0.2)
    pl.form(legs, radius=3, dark=(40, 30, 20), strength=0.4)
    bn = pl.mask()
    boot(J['ankle_f'], J['knee_f'], bn)
    pl.wash(bn, BOOT, opacity)
    torso = pl.mask()
    hx, hy = J['hip']
    sx, sy = J['sh']
    ax_, ay_ = sx - hx, sy - hy
    alen = math.hypot(ax_, ay_)
    ux, uy = ax_ / alen, ay_ / alen
    nx, ny = -uy, ux          # toward the back for a figure facing +x
    back_w = [(0.0, 0.115), (0.35, 0.125), (0.75, 0.13), (0.95, 0.10), (1.08, 0.04)]
    front_w = [(1.08, 0.03), (0.95, 0.10), (0.75, 0.13), (0.40, 0.115), (0.0, 0.11)]
    if pose == 'prone':
        back_w = [(t, w * 0.85) for t, w in back_w]
        front_w = [(t, w * 0.85) for t, w in front_w]
    tpts = [(hx + ux * alen * t + nx * w, hy + uy * alen * t + ny * w) for t, w in back_w] + \
           [(hx + ux * alen * t - nx * w, hy + uy * alen * t - ny * w) for t, w in front_w]
    pl.poly(X(tpts), m=torso)
    _limb(pl, X, J['sh'], J['neck'], 0.10, 0.075, torso)
    tw = 0.12
    pl.wash(torso, CAMO, opacity, edge=0.2, var=0.12)
    # camo blotches
    rng = random.Random(7)
    blot = pl.mask()
    for i in range(26):
        t = rng.random()
        px = hx + (sx - hx) * t + nx * rng.uniform(-tw, tw)
        py = hy + (sy - hy) * t + ny * rng.uniform(-tw, tw)
        pl.ellipse(*X.p(px, py), rng.uniform(0.02, 0.04) * X.s, rng.uniform(0.012, 0.025) * X.s, m=blot, rot=rng.uniform(0, 3))
    blot = pl.inter(pl.rough(blot, 0.4), torso)
    pl.wash(blot, CAMO_DARK, 0.45 * opacity, edge=0.1)
    pl.strokes(torso, int(X.s * 1.2), CAMO_LIGHT, length=(1.5, 3), width=(0.4, 0.6), opacity=0.3, angle=lambda a, b: 1.2)
    if blaze and weapon == 'rifle' and pose not in ('prone',):
        vest = pl.inter(torso, pl.poly(X([(hx + (sx - hx) * 0.25 - 0.3, hy + (sy - hy) * 0.25 - 0.3), (sx + 0.4, sy - 0.05), (sx + 0.4, sy + 0.6), (hx + (sx - hx) * 0.25 - 0.4, hy + (sy - hy) * 0.25 + 0.3)]), smooth=False))
        pl.wash(vest, BLAZE, 0.85 * opacity, edge=0.15)
    pl.form(torso, radius=5, dark=(30, 30, 20), strength=0.45)
    # head
    hdx, hdy = J['head']
    prof = [(-0.095, 0.02), (-0.08, 0.075), (-0.02, 0.112), (0.05, 0.108), (0.088, 0.07), (0.098, 0.032), (0.124, -0.004), (0.104, -0.026),
            (0.106, -0.05), (0.092, -0.078), (0.045, -0.104), (-0.03, -0.095), (-0.08, -0.045)]
    head = pl.poly(X([(hdx + x, hdy + y) for x, y in prof]))
    pl.wash(head, SKIN, opacity, edge=0.15)
    pl.form(head, radius=2, dark=(120, 80, 60), strength=0.4)
    beard = pl.inter(head, pl.poly(X([(hdx - 0.05, hdy - 0.02), (hdx + 0.12, hdy - 0.035), (hdx + 0.12, hdy - 0.15), (hdx - 0.05, hdy - 0.15)]), smooth=False))
    pl.wash(pl.blur(beard, 0.6), (110, 84, 60), 0.45 * opacity, edge=0)
    pl.wash(pl.ellipse(*X.p(hdx - 0.005, hdy - 0.005), 0.022 * X.s, 0.032 * X.s), darker(SKIN, 0.85), opacity, edge=0.3)
    cap = pl.inter(pl.ellipse(*X.p(hdx - 0.005, hdy + 0.035), 0.115 * X.s, 0.105 * X.s), pl.poly(X([(hdx - 0.3, hdy + 0.025), (hdx + 0.3, hdy + 0.045), (hdx + 0.3, hdy + 0.4), (hdx - 0.3, hdy + 0.4)]), smooth=False))
    brim = pl.poly(X([(hdx + 0.05, hdy + 0.04), (hdx + 0.19, hdy + 0.025), (hdx + 0.19, hdy + 0.045), (hdx + 0.05, hdy + 0.075)]), smooth=False)
    capm = Plate.union(cap, brim)
    pl.wash(capm, BLAZE if blaze else CAMO_DARK, opacity, edge=0.15)
    pl.form(capm, radius=2, dark=(90, 40, 10), strength=0.4)
    # near arm
    arm = pl.mask()
    _limb(pl, X, J['sh'], J['elbow_f'], 0.105, 0.09, arm)
    _limb(pl, X, J['elbow_f'], J['hand_f'], 0.09, 0.075, arm)
    pl.wash(arm, darker(CAMO, 0.9), opacity, edge=0.2)
    pl.form(arm, radius=2.5, dark=(30, 30, 20), strength=0.4)
    hand = pl.ellipse(*X.p(*J['hand_f']), 0.045 * X.s, 0.045 * X.s)
    pl.wash(hand, darker(CAMO_DARK, 0.9), opacity)    # gloves
    union = Plate.union(legs, bn, torso, head, capm, arm, hand)
    pl.contour(union, 0.45, 1.4, opacity=0.9 * opacity)
    # face detail
    ex, ey = X.p(hdx + 0.065, hdy + 0.022)
    pl.flat(pl.ellipse(ex, ey, 0.011 * X.s, 0.008 * X.s), INK, 0.85 * opacity)
    return dict(X=X, J=J, mask=union)


def bow(pl, X, grip, ang=0.0, size=1.45, opacity=1.0, drawn=0.0):
    """Recurve-style hunting bow, grip point in model space, ang tilts it from vertical."""
    gx, gy = grip
    half = size / 2
    pts = []
    for t in np.linspace(-1, 1, 13):
        y = t * half
        x = -0.06 * (1 - t * t) + (0.035 * t ** 4 if abs(t) > 0.8 else 0)
        pts.append((x, y))
    c, s = math.cos(ang), math.sin(ang)
    P = [(gx + x * c - y * s, gy + x * s + y * c) for x, y in pts]
    pl.ink(X(P), (1.0, 2.6, 1.0), (72, 50, 34), opacity)
    pl.ink(X([P[0], (gx - 0.06 * c - drawn, gy - 0.06 * s), P[-1]]), 0.45, (60, 60, 60), 0.8 * opacity, smooth=False)


def rifle(pl, X, butt, muzzle, opacity=1.0, scope=True, bipod=None):
    """Bolt rifle from butt to muzzle (model space). bipod = ground y for unfolded legs (or None)."""
    bx, by = butt
    mx, my = muzzle
    L = math.dist(butt, muzzle)
    ux, uy = (mx - bx) / L, (my - by) / L
    nx, ny = -uy, ux

    def at(t, o):
        return (bx + ux * L * t + nx * o, by + uy * L * t + ny * o)
    stock = [at(0.0, -0.075), at(0.0, 0.06), at(0.22, 0.03), at(0.30, 0.03), at(0.62, 0.025), at(0.66, 0.0), at(0.66, -0.02), at(0.36, -0.035), at(0.30, -0.07), at(0.24, -0.08), at(0.18, -0.03)]
    ms = pl.poly(X(stock), smooth=False)
    pl.wash(ms, STOCK, opacity, edge=0.2, grad=([(0, lighter(STOCK, 0.15)), (1, darker(STOCK, 0.8))], (*X.p(*at(0.3, 0.05)), *X.p(*at(0.3, -0.06)))))
    barrel = pl.stroke_mask(X([at(0.30, 0.012), at(1.0, 0.012)]), (0.028 * X.s, 0.018 * X.s), smooth=False)
    pl.wash(barrel, STEEL, opacity, edge=0.1)
    m = Plate.union(ms, barrel)
    if scope:
        sc = pl.stroke_mask(X([at(0.30, 0.075), at(0.56, 0.075)]), (0.05 * X.s, 0.042 * X.s), smooth=False)
        obj = pl.stroke_mask(X([at(0.50, 0.078), at(0.60, 0.08)]), (0.045 * X.s, 0.062 * X.s), smooth=False)
        mounts = Plate.union(pl.stroke_mask(X([at(0.36, 0.03), at(0.36, 0.06)]), 0.02 * X.s, smooth=False), pl.stroke_mask(X([at(0.50, 0.03), at(0.50, 0.06)]), 0.02 * X.s, smooth=False))
        scm = Plate.union(sc, obj, mounts)
        pl.wash(scm, (48, 50, 52), opacity, edge=0.1)
        pl.form(scm, radius=1.5, dark=(10, 10, 10), strength=0.4, hi=WHITE, hi_strength=0.25)
        m = Plate.union(m, scm)
    if bipod is not None:
        p0 = at(0.60, -0.02)
        legs = pl.mask()
        for dx in (-0.06, 0.05):
            pl.stroke_mask(X([p0, (p0[0] + dx, bipod)]), (0.016 * X.s, 0.012 * X.s), m=legs, smooth=False)
        pl.wash(legs, (60, 62, 64), opacity)
        m = Plate.union(m, legs)
    pl.contour(m, 0.35, 0.9, opacity=0.9 * opacity)
    return m


# ------------------------------------------------------------------------------------------------ top-down deer
DEER_TOP = [  # metres, +x = forward (head), +y = one side; body centre at the origin (dorsal view)
    (0.98, 0.0), (0.965, 0.024), (0.90, 0.037), (0.83, 0.054), (0.78, 0.068), (0.72, 0.062), (0.64, 0.068), (0.54, 0.083),
    (0.44, 0.11), (0.34, 0.148), (0.24, 0.172), (0.08, 0.198), (-0.08, 0.194), (-0.22, 0.165), (-0.36, 0.17), (-0.46, 0.163),
    (-0.56, 0.128), (-0.62, 0.07), (-0.645, 0.0),
]


def deer_top(pl, cx, cy, scale, ang, opacity=1.0, buck=False, coat=None, ink=True):
    """Deer seen from above, centre (cx, cy) px, heading ang (radians, screen space: 0 = right, +pi/2 = down)."""
    c, s = math.cos(ang), math.sin(ang)

    def T(pts):
        return [(cx + (x * c - y * s) * scale, cy + (x * s + y * c) * scale) for x, y in pts]
    outline = DEER_TOP + [(x, -y) for x, y in reversed(DEER_TOP[1:-1])]
    body = pl.poly(T(outline), n=10)
    ears = pl.mask()
    for sd in (1, -1):
        pl.poly(T([(0.735, 0.045 * sd), (0.70, 0.12 * sd), (0.64, 0.175 * sd), (0.615, 0.17 * sd), (0.655, 0.10 * sd), (0.70, 0.05 * sd)]), m=ears)
    col = coat or COAT_SIDE
    pl.wash(ears, darker(col, 0.85), opacity, edge=0.2)
    pl.contour(ears, 0.35, 0.9, opacity=0.85 * opacity)
    pl.wash(body, col, opacity, edge=0.18, var=0.1)
    spine = pl.stroke_mask(T([(0.86, 0.0), (0.6, 0.0), (0.3, 0.0), (0.0, 0.0), (-0.35, 0.0), (-0.60, 0.0)]), (0.05 * scale, 0.09 * scale, 0.14 * scale, 0.12 * scale))
    pl.wash(pl.inter(pl.blur(spine, 0.05 * scale), body), COAT_DORSAL, 0.8 * opacity, edge=0)
    pl.form(body, radius=0.07 * scale, dark=(50, 36, 24), strength=0.38, hi=WHITE, hi_strength=0.12)
    pl.strokes(body, int(scale * 1.4), darker(COAT_DORSAL, 0.7), length=(1.5, 3), width=(0.3, 0.5), opacity=0.25,
               angle=lambda x, y: ang + math.pi)
    tail = pl.poly(T([(-0.62, 0.035), (-0.70, 0.04), (-0.745, 0.0), (-0.70, -0.04), (-0.62, -0.035)]))
    pl.wash(tail, COAT_DORSAL, opacity)
    pl.wash(pl.minus(tail, pl.poly(T([(-0.62, 0.022), (-0.69, 0.025), (-0.725, 0.0), (-0.69, -0.025), (-0.62, -0.022)]))), BELLY, 0.85 * opacity, edge=0)
    m = Plate.union(body, tail)
    nose = pl.ellipse(*T([(0.962, 0.0)])[0], 0.02 * scale, 0.026 * scale, rot=ang)
    pl.flat(pl.inter(nose, body), NOSE, 0.9 * opacity)
    for sd in (1, -1):
        pl.flat(pl.ellipse(*T([(0.80, 0.064 * sd)])[0], 0.012 * scale, 0.008 * scale, rot=ang), (20, 16, 14), 0.9 * opacity)
    if ink:
        pl.contour(m, 0.4, 1.2, opacity=0.9 * opacity)
    if buck:
        ant = pl.mask()
        for sd in (1, -1):
            pl.stroke_mask(T([(0.755, 0.04 * sd), (0.72, 0.13 * sd), (0.76, 0.21 * sd), (0.85, 0.245 * sd), (0.94, 0.22 * sd), (0.99, 0.16 * sd)]),
                           (0.034 * scale, 0.026 * scale, 0.012 * scale), m=ant)
            for (x, y) in ((0.735, 0.10), (0.79, 0.225), (0.87, 0.24), (0.94, 0.215)):
                pl.ellipse(*T([(x, y * sd)])[0], 0.016 * scale, 0.016 * scale, m=ant)
        pl.wash(ant, ANTLER, opacity, edge=0.2)
        pl.form(ant, radius=1.2, dark=ANTLER_DARK, strength=0.5)
        pl.contour(ant, 0.35, 0.9, opacity=0.9 * opacity)
    return dict(T=T, mask=m)


def hunter_top(pl, cx, cy, scale, ang, opacity=1.0, weapon='bow'):
    """Hunter seen from above (shoulders, cap brim, arms forward with a bow), heading ang."""
    c, s = math.cos(ang), math.sin(ang)

    def T(pts):
        return [(cx + (x * c - y * s) * scale, cy + (x * s + y * c) * scale) for x, y in pts]
    sh = pl.poly(T([(0.06, 0.24), (0.10, 0.10), (0.11, -0.10), (0.06, -0.24), (-0.07, -0.25), (-0.12, -0.1), (-0.12, 0.1), (-0.07, 0.25)]))
    arms = pl.mask()
    pl.stroke_mask(T([(0.04, 0.20), (0.26, 0.14), (0.42, 0.04)]), (0.09 * scale, 0.07 * scale), m=arms)
    pl.stroke_mask(T([(0.04, -0.20), (0.22, -0.10), (0.36, 0.02)]), (0.09 * scale, 0.07 * scale), m=arms)
    body = Plate.union(sh, arms)
    pl.wash(body, CAMO, opacity, edge=0.2)
    pl.wash(pl.inter(pl.rough(pl.ellipse(*T([(-0.02, 0.05)])[0], 0.08 * scale, 0.06 * scale), 0.5), sh), CAMO_DARK, 0.6 * opacity)
    pl.form(body, radius=0.04 * scale, dark=(30, 30, 20), strength=0.35)
    head = pl.ellipse(*T([(0.0, 0.0)])[0], 0.1 * scale, 0.1 * scale)
    brim = pl.poly(T([(0.06, 0.07), (0.17, 0.05), (0.17, -0.05), (0.06, -0.07)]))
    capm = Plate.union(head, brim)
    pl.wash(capm, BLAZE, opacity, edge=0.15)
    pl.form(capm, radius=0.03 * scale, dark=(90, 40, 10), strength=0.4)
    if weapon == 'bow':
        pl.ink(T([(0.40, 0.55), (0.46, 0.25), (0.47, 0.0), (0.46, -0.25), (0.40, -0.55)]), (0.8, 2.0, 0.8), (72, 50, 34), opacity)
    pl.contour(Plate.union(body, capm), 0.4, 1.2, opacity=0.9 * opacity)
    return T
