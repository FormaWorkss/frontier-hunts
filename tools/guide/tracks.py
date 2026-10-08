"""[fieldbook] Track shapes for the track-ID plate. Units: centimetres, x right, y = direction of travel (up).
Sizes are typical adult prints (front foot unless noted):
whitetail 7.5 x 5.6 (heart-shaped, pointed), elk 11 x 9.5 (rounder, blunter), moose 14 x 10 (long, pointed; dewclaws
in soft ground), bison 13 x 13 (round), feral hog 6 x 5.5 (rounded tips, dewclaws wide and outside), wolf 11.5 x 9.5
and coyote 6.5 x 5 (4 toes, claws, heel pad with two rear lobes), cougar 9 x 9.5 (no claws, three-lobed rear of the
heel pad, a leading toe), black bear front 11 wide x 10 long and hind 17.5 x 9.5 (5 toes, hind like a human foot,
smallest toe on the inside), ruffed grouse 5 x 5 (3 forward toes + short hind toe), mallard 6.5 x 6.5 (webbed).
"""
import math
from fieldart import *


def _toe_half(L, W, g, sharp=1.0, heel=0.5, bulge=0.5):
    """Right half of a cloven hoof (x >= g), returns a closed list of points (front = +y)."""
    tipx = g + 0.03 * W + 0.10 * W * (1 - sharp)
    tipy = 0.5 * L
    blunt = 1 - sharp
    return [(tipx, tipy), (tipx + 0.05 * W + 0.08 * W * blunt, tipy - 0.03 * L - 0.03 * L * blunt),
            (g + 0.27 * W + 0.06 * W * blunt, 0.36 * L), (0.42 * W + 0.05 * W * bulge, 0.13 * L), (0.5 * W, -0.12 * L),
            (0.47 * W, -0.32 * L), (0.38 * W, -0.45 * L), (g + 0.13 * W + 0.06 * W * heel, -0.5 * L), (g + 0.03 * W, -0.45 * L),
            (g, -0.22 * L), (g, 0.10 * L), (g + 0.005 * W, 0.33 * L), (tipx - 0.012 * W, tipy - 0.02 * L)]


def cloven(L, W, gap=0.06, sharp=1.0, heel=0.5, bulge=0.5, splay=0.0):
    g = gap * W / 2
    r = _toe_half(L, W, g, sharp, heel, bulge)
    if splay:
        r = [(x + max(0, y) / L * splay * W, y) for x, y in r]
    l = [(-x, y) for x, y in r]
    return [r, l]


def pad(cx, cy, rx, ry, rot=0.0, n=20):
    return [(cx + rx * math.cos(t) * math.cos(rot) - ry * math.sin(t) * math.sin(rot),
             cy + rx * math.cos(t) * math.sin(rot) + ry * math.sin(t) * math.cos(rot)) for t in np.linspace(0, 2 * math.pi, n, endpoint=False)]


def teardrop(cx, cy, r, L, ang=math.pi / 2, n=22):
    """Toe pad: rounded at the back, slightly pointed at the front (ang = pointing direction)."""
    pts = []
    for t in np.linspace(0, 2 * math.pi, n, endpoint=False):
        x = math.cos(t)
        y = math.sin(t)
        rr = r * (1 + 0.28 * max(0, x) ** 2)
        px, py = x * rr * L / r * 0.5 + 0.0, y * r
        pts.append((px, py))
    c, s = math.cos(ang), math.sin(ang)
    return [(cx + x * c - y * s, cy + x * s + y * c) for x, y in pts]


def canid(L, W, narrow=1.0):
    toes = [teardrop(-0.17 * W, 0.24 * L, 0.10 * W, 0.24 * L, math.pi / 2 + 0.05), teardrop(0.17 * W, 0.24 * L, 0.10 * W, 0.24 * L, math.pi / 2 - 0.05),
            teardrop(-0.37 * W * narrow, 0.02 * L, 0.095 * W, 0.22 * L, math.pi / 2 + 0.28), teardrop(0.37 * W * narrow, 0.02 * L, 0.095 * W, 0.22 * L, math.pi / 2 - 0.28)]
    heel = [(0.0, -0.06 * L), (0.14 * W, -0.10 * L), (0.24 * W, -0.20 * L), (0.25 * W, -0.32 * L), (0.18 * W, -0.42 * L), (0.08 * W, -0.40 * L),
            (0.0, -0.35 * L), (-0.08 * W, -0.40 * L), (-0.18 * W, -0.42 * L), (-0.25 * W, -0.32 * L), (-0.24 * W, -0.20 * L), (-0.14 * W, -0.10 * L)]
    claws = [(-0.19 * W, 0.44 * L), (0.19 * W, 0.44 * L), (-0.44 * W * narrow, 0.21 * L), (0.44 * W * narrow, 0.21 * L)]
    return dict(shapes=toes + [heel], claws=claws)


def feline(L, W):
    # right foot: the leading toe is the second from the inside (left), so it sits highest
    toes = [teardrop(-0.36 * W, 0.08 * L, 0.125 * W, 0.26 * L, math.pi / 2 + 0.42), teardrop(-0.13 * W, 0.33 * L, 0.13 * W, 0.28 * L, math.pi / 2 + 0.08),
            teardrop(0.13 * W, 0.29 * L, 0.13 * W, 0.28 * L, math.pi / 2 - 0.10), teardrop(0.36 * W, 0.06 * L, 0.125 * W, 0.26 * L, math.pi / 2 - 0.42)]
    heel = [(-0.11 * W, 0.015 * L), (0.0, -0.035 * L), (0.11 * W, 0.015 * L), (0.25 * W, -0.03 * L), (0.33 * W, -0.16 * L), (0.31 * W, -0.34 * L),
            (0.21 * W, -0.46 * L), (0.11 * W, -0.405 * L), (0.0, -0.475 * L), (-0.11 * W, -0.405 * L), (-0.21 * W, -0.46 * L), (-0.31 * W, -0.34 * L),
            (-0.33 * W, -0.16 * L), (-0.25 * W, -0.03 * L)]
    return dict(shapes=toes + [heel], claws=[])


def bear_front(L, W):
    """Black bear front foot: wide metacarpal pad with an arched front edge, five toes close above it, short claws,
    and the small round carpal (heel) pad behind that often registers."""
    palm = [(-0.49 * W, -0.04 * L), (-0.40 * W, 0.10 * L), (-0.18 * W, 0.20 * L), (0.06 * W, 0.23 * L), (0.30 * W, 0.20 * L), (0.47 * W, 0.10 * L),
            (0.51 * W, -0.06 * L), (0.44 * W, -0.16 * L), (0.24 * W, -0.11 * L), (0.0, -0.08 * L), (-0.24 * W, -0.10 * L), (-0.42 * W, -0.15 * L)]
    palm = [(x, y * 1.4 - 0.03 * L) for x, y in palm]  # [artqa] the metacarpal pad is about half as deep as it is wide
    toes, claws = [], []
    # inside = left; the innermost toe is the smallest
    for (x, y, r) in [(-0.43, 0.30, 0.075), (-0.23, 0.38, 0.09), (0.0, 0.41, 0.095), (0.23, 0.39, 0.095), (0.43, 0.31, 0.09)]:
        toes.append(pad(x * W, y * L, r * W, r * W * 1.12, n=16))
        claws.append((x * W * 1.06, (y + 0.10) * L))
    heel = pad(0.30 * W, -0.50 * L, 0.075 * W, 0.08 * W)
    return dict(shapes=[palm] + toes + [heel], claws=claws)


def bear_hind(L, W):
    """Black bear hind foot: long sole like a human foot, five toes in an arc, smallest toe on the inside."""
    sole = [(-0.38 * W, 0.23 * L), (-0.14 * W, 0.28 * L), (0.14 * W, 0.285 * L), (0.38 * W, 0.24 * L), (0.48 * W, 0.12 * L), (0.46 * W, -0.06 * L),
            (0.36 * W, -0.28 * L), (0.24 * W, -0.45 * L), (0.02 * W, -0.50 * L), (-0.20 * W, -0.45 * L), (-0.30 * W, -0.24 * L), (-0.36 * W, 0.02 * L)]
    toes, claws = [], []
    for (x, y, r) in [(-0.38, 0.335, 0.085), (-0.17, 0.375, 0.10), (0.06, 0.39, 0.105), (0.28, 0.375, 0.105), (0.47, 0.31, 0.10)]:
        toes.append(pad(x * W, y * L, r * W, r * W * 1.15, n=16))
        claws.append((x * W * 1.05, (y + 0.065) * L))
    return dict(shapes=[sole] + toes, claws=claws)


def grouse(L):
    toes = []
    for ang, ln in [(math.pi / 2, 0.62 * L), (math.pi / 2 + 0.72, 0.50 * L), (math.pi / 2 - 0.72, 0.50 * L), (-math.pi / 2, 0.16 * L)]:
        toes.append([(0.0, -0.08 * L), (math.cos(ang) * ln * 0.5, -0.08 * L + math.sin(ang) * ln * 0.5), (math.cos(ang) * ln, -0.08 * L + math.sin(ang) * ln)])
    return toes


def duck(L):
    """Mallard: three forward toes curving slightly inward, webbing with scalloped (concave) trailing edges."""
    base = (0.0, -0.36 * L)
    toes = []
    for ang, ln, bend in [(math.pi / 2, 0.86 * L, 0.0), (math.pi / 2 + 0.74, 0.74 * L, -0.20), (math.pi / 2 - 0.74, 0.74 * L, 0.20)]:
        pts = []
        for t in np.linspace(0, 1, 7):
            a = ang + bend * t
            pts.append((base[0] + math.cos(a) * ln * t, base[1] + math.sin(a) * ln * t))
        toes.append(pts)
    mid, lt, rt = toes
    web = [base] + lt[1:6] + [(lt[5][0] * 0.55 + mid[5][0] * 0.45, (lt[5][1] + mid[5][1]) / 2 - 0.20 * L)] + mid[5:4:-1] + \
          [(rt[5][0] * 0.55 + mid[5][0] * 0.45, (rt[5][1] + mid[5][1]) / 2 - 0.20 * L)] + rt[5:0:-1]
    return toes, web


# ------------------------------------------------------------------------------------------------ painting
def impression(pl, shapes, cx, cy, k, rot=0.0, mud=MUD, depth=1.0, smooth=True, outline=True):
    """Paints pressed-in shapes (cm lists) at (cx, cy) px with k px/cm: damp floor, the wall toward the light in
    shadow, the far wall lit, a slightly raised rim."""
    c, s = math.cos(rot), math.sin(rot)

    def T(pts):
        return [(cx + (x * c - y * s) * k, cy - (x * s + y * c) * k) for x, y in pts]
    m = pl.mask()
    for sh in shapes:
        pl.poly(T(sh), m=m, smooth=smooth, n=6)
    off = max(1.0, 0.16 * k * depth)
    rim = pl.minus(ImageChops_offset(pl.grow(m, 1.6), int(-off * pl.S * 0.3), int(-off * pl.S * 0.3)), m)
    pl.wash(pl.blur(rim, 0.9), lighter(mud, 0.5), 0.55, edge=0)
    floor = darker(mud, 0.74)
    pl.wash(m, floor, 0.95, edge=0.2, edge_w=1.2, var=0.14)
    # concave modelling: reversed light puts the shadow on the wall nearest the light
    pl.form(m, radius=max(1.5, 0.5 * k), dark=darker(mud, 0.25), strength=0.95, hi=lighter(mud, 0.2), hi_strength=0.6, light=(0.55, 0.83))
    pl.stipple(m, int(k * k * 0.5), darker(mud, 0.45), r=(0.3, 0.6), opacity=0.35)
    if outline:
        pl.contour(m, 0.45, 1.2, color=darker(mud, 0.28), opacity=0.9, light=(0.55, 0.83))
    return m, T


def ImageChops_offset(m, dx, dy):
    from PIL import ImageChops
    return ImageChops.offset(m, dx, dy)


def mud_patch(pl, cx, cy, rx, ry, color=MUD, seed=0, opacity=0.32):
    m = pl.ellipse(cx, cy, rx, ry)
    m = pl.rough(m, 6, 'lo')
    pl.wash(m, color, opacity, edge=0.12, var=0.22, bleed=4)
    pl.stipple(pl.blur(m, 6), int(rx * ry * 0.03), darker(color, 0.6), r=(0.3, 0.7), opacity=0.25)
    return m


def claw_marks(pl, T, claws, k, toward=None, color=None):
    col = color or darker(MUD, 0.3)
    for (x, y) in claws:
        p = T([(x, y)])[0]
        ang = math.atan2(-y, x) if toward is None else toward
        L = 0.45 * k
        a = math.atan2(-(y), x)
        dx, dy = (x * 0.25) * k / max(1e-6, math.hypot(x * 0.25, 1.0)), -k / max(1e-6, math.hypot(x * 0.25, 1.0))
        q = (p[0] + dx * 0.55, p[1] + dy * 0.55)
        pl.ink([q, p], (0.4, 1.4), col, 0.9, smooth=False)


def bird_print(pl, toes, cx, cy, k, width_cm=0.38, mud=MUD, web=None):
    def T(pts):
        return [(cx + x * k, cy - y * k) for x, y in pts]
    m = pl.mask()
    if web is not None:
        pl.poly(T(web), m=m, smooth=True, n=6)
    for t in toes:
        pl.stroke_mask(T(t), (width_cm * k * 1.1, width_cm * k, width_cm * k * 0.45), m=m)
        # knuckle pads
        for p in T(t)[1:-1]:
            pl.ellipse(p[0], p[1], width_cm * k * 0.7, width_cm * k * 0.7, m=m)
    off = max(1.0, 0.12 * k)
    rim = pl.minus(ImageChops_offset(pl.grow(m, 1.4), int(-off * pl.S * 0.3), int(-off * pl.S * 0.3)), m)
    pl.wash(pl.blur(rim, 0.8), lighter(mud, 0.5), 0.55, edge=0)
    pl.wash(m, darker(mud, 0.70), 0.95, edge=0.2, edge_w=1.0)
    pl.form(m, radius=max(1.2, 0.25 * k), dark=darker(mud, 0.30), strength=0.75, hi=lighter(mud, 0.15), hi_strength=0.5, light=(0.55, 0.83))
    pl.contour(m, 0.4, 1.1, color=darker(mud, 0.28), opacity=0.9, light=(0.55, 0.83))
    if web is not None:
        # web is shallower: lighten it and redraw the toe lines over it
        wm = pl.minus(pl.poly(T(web), smooth=True, n=6), pl.grow(Plate.union(*[pl.stroke_mask(T(t), width_cm * k) for t in toes]), 0.2))
        pl.wash(wm, lighter(mud, 0.1), 0.45, edge=0)
    return m
