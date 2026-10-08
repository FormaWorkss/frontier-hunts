"""[fieldbook] Anatomically proportioned whitetail (buck/doe) for the Field School plates.

Model space: metres, y up, ground at y = 0, the deer FACING LEFT (head at negative x). Adult buck proportions:
shoulder height ~1.0 m, brisket ~0.47 m above the ground, chest front at x = 0, tail at x ~1.5 m, head ~0.31 m.
Landmarks used by the vitals plate (VITALS dict) are in the same space.
"""
import math
from fieldart import *

# ------------------------------------------------------------------------------------------------ outline (standing)
TORSO = [
    (0.24, 1.002), (0.40, 0.978), (0.60, 0.966), (0.80, 0.975), (0.93, 0.995), (1.03, 0.987), (1.10, 0.948),
    (1.16, 0.885), (1.183, 0.80), (1.172, 0.72), (1.135, 0.645), (1.05, 0.605), (0.93, 0.585), (0.86, 0.588),
    (0.74, 0.548), (0.58, 0.508), (0.42, 0.482), (0.30, 0.472), (0.20, 0.476), (0.12, 0.50),
    (0.05, 0.56), (0.005, 0.64), (0.0, 0.72), (0.05, 0.83), (0.14, 0.93),
]
NECK = [  # standing alert; first two and last two points are the base (do not rotate)
    (0.30, 0.995), (0.15, 1.05), (0.01, 1.135), (-0.09, 1.21), (-0.145, 1.26), (-0.17, 1.30),
    (-0.27, 1.16), (-0.245, 1.05), (-0.18, 0.93), (-0.09, 0.80), (-0.005, 0.70), (0.14, 0.68),
]
HEAD = [(-0.165, 1.319), (-0.219, 1.345), (-0.278, 1.339), (-0.354, 1.306), (-0.429, 1.265), (-0.485, 1.231), (-0.513, 1.203), (-0.511, 1.177), (-0.492, 1.16), (-0.462, 1.154), (-0.44, 1.143), (-0.408, 1.147), (-0.343, 1.155), (-0.278, 1.162), (-0.24, 1.187), (-0.202, 1.235), (-0.165, 1.278)]
EYE = (-0.294, 1.284)
NOSE_TIP = (-0.503, 1.195)
EAR_NEAR = [(-0.168, 1.318), (-0.152, 1.37), (-0.118, 1.425), (-0.07, 1.475), (-0.05, 1.482), (-0.058, 1.445), (-0.078, 1.385), (-0.11, 1.33), (-0.135, 1.305)]
EAR_FAR = [(-0.185, 1.33), (-0.19, 1.39), (-0.19, 1.45), (-0.172, 1.50), (-0.158, 1.47), (-0.152, 1.40), (-0.16, 1.335)]
TAIL = [(1.09, 0.958), (1.15, 0.945), (1.205, 0.885), (1.235, 0.80), (1.225, 0.72), (1.195, 0.705), (1.178, 0.76), (1.165, 0.85), (1.125, 0.915)]

FRONT_LEG = [  # near front leg, standing square
    (0.05, 0.60), (0.07, 0.48), (0.095, 0.40), (0.106, 0.32), (0.105, 0.29), (0.105, 0.20), (0.105, 0.115), (0.101, 0.078),
    (0.088, 0.042), (0.066, 0.010), (0.062, 0.0), (0.124, 0.0), (0.133, 0.03), (0.143, 0.056), (0.151, 0.078), (0.146, 0.12),
    (0.143, 0.20), (0.145, 0.28), (0.154, 0.315), (0.172, 0.37), (0.198, 0.44), (0.222, 0.50), (0.26, 0.55),
]
HIND_LEG = [  # near hind leg
    (0.86, 0.59), (0.92, 0.52), (0.98, 0.455), (1.035, 0.405), (1.07, 0.37), (1.08, 0.335), (1.078, 0.24), (1.072, 0.12),
    (1.067, 0.08), (1.055, 0.044), (1.034, 0.012), (1.028, 0.0), (1.09, 0.0), (1.10, 0.03), (1.11, 0.056), (1.12, 0.08),
    (1.118, 0.13), (1.12, 0.24), (1.128, 0.33), (1.146, 0.365), (1.152, 0.40), (1.142, 0.46), (1.148, 0.53), (1.165, 0.62),
    (1.175, 0.72), (1.05, 0.72), (0.95, 0.68),
]

# antlers: main beam + tines (near side), typical 8-point (4x4) frame
BEAM = [(-0.232, 1.338), (-0.212, 1.40), (-0.193, 1.47), (-0.195, 1.535), (-0.235, 1.585), (-0.30, 1.612), (-0.375, 1.612), (-0.44, 1.59), (-0.485, 1.55)]
TINES = [
    [(-0.218, 1.385), (-0.238, 1.44), (-0.258, 1.485)],              # G1 brow tine
    [(-0.198, 1.548), (-0.19, 1.62), (-0.188, 1.68), (-0.196, 1.715)],  # G2
    [(-0.30, 1.612), (-0.305, 1.67), (-0.315, 1.715)],                # G3
    [(-0.40, 1.606), (-0.41, 1.645), (-0.425, 1.672)],                # G4
]

# vitals landmarks (deer facing left)
VITALS = {
    'spine': [(-0.16, 1.24), (-0.10, 1.13), (-0.03, 1.01), (0.04, 0.90), (0.14, 0.855), (0.30, 0.86), (0.50, 0.87), (0.70, 0.885),
              (0.90, 0.905), (1.05, 0.90), (1.14, 0.86)],
    'scapula': [(0.22, 0.965), (0.34, 0.935), (0.30, 0.85), (0.16, 0.72), (0.08, 0.67), (0.05, 0.70), (0.10, 0.78), (0.17, 0.88)],
    'humerus': [(0.04, 0.69), (0.10, 0.66), (0.19, 0.55), (0.23, 0.51), (0.19, 0.49), (0.14, 0.55), (0.06, 0.63), (0.03, 0.66)],
    'lungs': [(0.10, 0.83), (0.25, 0.845), (0.42, 0.845), (0.56, 0.835), (0.615, 0.81), (0.58, 0.73), (0.51, 0.64), (0.44, 0.565),
              (0.385, 0.525), (0.345, 0.55), (0.345, 0.62), (0.30, 0.675), (0.23, 0.665), (0.19, 0.60), (0.13, 0.555), (0.08, 0.62), (0.07, 0.73)],
    'heart': [(0.225, 0.645), (0.265, 0.665), (0.315, 0.65), (0.335, 0.61), (0.325, 0.56), (0.30, 0.52), (0.28, 0.51), (0.255, 0.53),
              (0.225, 0.575), (0.215, 0.615)],
    'diaphragm': [(0.635, 0.83), (0.60, 0.73), (0.53, 0.63), (0.45, 0.55), (0.39, 0.50)],
    'liver': [(0.635, 0.80), (0.68, 0.76), (0.69, 0.68), (0.65, 0.60), (0.57, 0.53), (0.47, 0.495), (0.40, 0.49), (0.45, 0.535),
              (0.53, 0.62), (0.60, 0.72)],
    'rumen': [(0.69, 0.82), (0.80, 0.84), (0.90, 0.82), (0.95, 0.75), (0.93, 0.65), (0.85, 0.585), (0.74, 0.565), (0.68, 0.60), (0.70, 0.70)],
    'intestine': [(0.93, 0.83), (1.02, 0.85), (1.10, 0.81), (1.13, 0.72), (1.08, 0.63), (0.98, 0.60), (0.94, 0.66), (0.96, 0.76)],
    'aim': (0.285, 0.64),
    'ribs': [0.12, 0.178, 0.236, 0.294, 0.352, 0.41, 0.468, 0.526, 0.584],
}


def _rot(pts, pivot, ang):
    c, s = math.cos(ang), math.sin(ang)
    out = []
    for x, y in pts:
        dx, dy = x - pivot[0], y - pivot[1]
        out.append((pivot[0] + dx * c - dy * s, pivot[1] + dx * s + dy * c))
    return out


def pose_head(neck_ang=0.0, head_ang=0.0):
    """Returns (neck, head, ears_near, ears_far, beam, tines, eye, nose) rotated for the pose (radians, + = counter-clockwise)."""
    pivot = (0.14, 0.93)
    neck = []
    for i, p in enumerate(NECK):
        base = i in (0, 1, len(NECK) - 1, len(NECK) - 2)
        d = math.dist(p, pivot)
        w = 0.0 if base else min(1.0, max(0.0, (d - 0.10) / 0.30))
        neck.append(_rot([p], pivot, neck_ang * w)[0])
    hp = (-0.19, 1.29)

    def H(pts):
        return _rot(_rot(pts, hp, head_ang), pivot, neck_ang)
    return dict(neck=neck, head=H(HEAD), ear_near=H(EAR_NEAR), ear_far=H(EAR_FAR), beam=H(BEAM), tines=[H(t) for t in TINES],
                eye=H([EYE])[0], nose=H([NOSE_TIP])[0], hp=H([hp])[0], muzzle=H([(-0.475, 1.17), (-0.445, 1.158), (-0.43, 1.20), (-0.455, 1.228), (-0.48, 1.222)]),
                throat=H([(-0.275, 1.165), (-0.25, 1.115), (-0.215, 1.125), (-0.225, 1.17)]), chin=H([(-0.47, 1.158), (-0.40, 1.148), (-0.41, 1.165)]),
                forehead=H([(-0.22, 1.335), (-0.32, 1.305), (-0.40, 1.265), (-0.36, 1.296), (-0.28, 1.326)]))


def shift(pts, dx, dy=0.0):
    return [(x + dx, y + dy) for x, y in pts]


def paint_antler(pl, X, beam, tines, opacity=1.0, far=False, scale=1.0):
    s = X.s
    base_w = 0.034 * s
    col = darker(ANTLER, 0.82) if far else ANTLER
    m = pl.stroke_mask(X(beam), (base_w, base_w * 0.72, base_w * 0.3))
    for t in tines:
        pl.stroke_mask(X(t), (base_w * 0.62, base_w * 0.4, base_w * 0.16), m=m)
    burr = pl.ellipse(*X([beam[0]])[0], base_w * 0.75, base_w * 0.5)
    m = Plate.union(m, burr)
    pl.wash(m, col, opacity, edge=0.18, grain=0.12, grad=([(0, darker(col, 0.72)), (1, col)], (X(beam[:1])[0][0], X(beam[:1])[0][1], X(beam[-1:])[0][0], X(beam[-1:])[0][1] - 40)))
    pl.form(m, radius=2.0, dark=ANTLER_DARK, strength=0.55, hi=WHITE, hi_strength=0.35)
    # pearling near the base
    pl.strokes(pl.inter(m, pl.ellipse(*X([beam[1]])[0], base_w * 2.2, base_w * 2.6)), 26, ANTLER_DARK, length=(1.2, 2.4), width=(0.4, 0.7),
               angle=lambda x, y: math.pi / 2, opacity=0.5)
    pl.contour(m, 0.55, 1.3, opacity=0.9 * opacity)
    return m


def whitetail(pl, ox, oy, scale, facing=1, buck=True, neck_ang=0.0, head_ang=0.0, ghost=False, far_legs=True, detail=1.0,
              legs=None, opacity=1.0, hooves=None):
    """Paints a standing whitetail. ox, oy = ground point under the chest front (x = 0); scale = px per metre.
    facing=1 faces left, -1 faces right. ghost = light coat for the vitals plate (organs painted over it).
    Returns dict with the transform and masks."""
    X = Xf(ox, oy, scale, facing)
    hp = pose_head(neck_ang, head_ang)
    s = scale
    k = s / 250.0
    coat_op = 0.5 if ghost else 1.0
    op = opacity
    shade = (52, 36, 24)

    front_leg = FRONT_LEG if legs is None else legs.get('front', FRONT_LEG)
    hind_leg = HIND_LEG if legs is None else legs.get('hind', HIND_LEG)
    torso = pl.poly(X(TORSO))
    if far_legs:
        ff = shift(front_leg, 0.07)
        fh = shift(hind_leg, -0.075)
        mf = Plate.union(pl.poly(X(ff)), pl.poly(X(fh)))
        mf = pl.minus(mf, torso)
        far_col = darker(COAT_LEG, 0.62)
        if ghost:
            far_col = mix(far_col, PAPER, 0.4)
        pl.wash(mf, far_col, coat_op * op, edge=0.15, var=0.1)
        pl.form(mf, radius=3 * k, dark=(30, 22, 16), strength=0.35 * coat_op)
        hoof_far = pl.inter(mf, pl.poly(X([(-0.2, 0.046), (1.6, 0.046), (1.6, -0.05), (-0.2, -0.05)]), smooth=False))
        pl.wash(hoof_far, HOOF, 0.9 * op * coat_op)
        pl.contour(mf, 0.45, 1.1 * k + 0.2, opacity=0.75 * op * (0.8 if ghost else 1))
    # far ear and far antler sit behind the head
    me_far = pl.poly(X(hp['ear_far']))
    pl.wash(me_far, darker(COAT_SIDE, 0.75) if not ghost else mix(COAT_SIDE, PAPER, 0.4), coat_op * op, edge=0.2)
    pl.contour(me_far, 0.45, 1.0 * k + 0.2, opacity=0.8 * op)
    if buck:
        paint_antler(pl, X, shift(hp['beam'], 0.03, 0.01), [shift(t, 0.03, 0.01) for t in hp['tines']], far=True, opacity=op)

    neck = pl.poly(X(hp['neck']))
    head = pl.poly(X(hp['head']), n=10)
    mfl = pl.poly(X(front_leg))
    mhl = pl.poly(X(hind_leg))
    tail = pl.poly(X(TAIL))
    body = Plate.union(torso, neck, head, mfl, mhl, tail)

    top_y = X.p(0, 1.02)[1]
    low_y = X.p(0, 0.47)[1]
    coat = [(0.0, COAT_DORSAL), (0.30, COAT_SIDE), (0.80, COAT_LOW), (1.0, mix(COAT_LOW, BELLY, 0.2))]
    if ghost:
        coat = [(t, mix(c, PAPER, 0.45)) for t, c in coat]
    pl.wash(body, coat[0][1], coat_op * op, edge=0.14, grain=0.16, var=0.11, grad=(coat, (ox, top_y, ox, low_y)))
    # lower legs: warmer tan, darker down the front of the cannons
    lowcut = pl.poly(X([(-0.3, 0.40), (1.7, 0.40), (1.7, -0.05), (-0.3, -0.05)]), smooth=False)
    lowleg = pl.inter(Plate.union(mfl, mhl), lowcut)
    pl.wash(pl.blur(lowleg, 1.5 * k), mix(COAT_LEG, PAPER, 0.4) if ghost else COAT_LEG, 0.55 * coat_op * op, edge=0)
    # rut neck, darker saddle along the back
    if buck:
        pl.wash(pl.blur(pl.minus(neck, torso), 5 * k), COAT_DORSAL, 0.22 * coat_op * op, edge=0)
    saddle = pl.inter(pl.poly(X([(0.15, 1.05), (1.1, 1.05), (1.1, 0.90), (0.6, 0.88), (0.15, 0.90)])), torso)
    pl.wash(pl.blur(saddle, 6 * k), COAT_DORSAL, 0.35 * coat_op * op, edge=0)
    # whites: belly, inside the hind leg, throat patch, muzzle band, chin, eye ring, tail fringe
    belly = pl.poly(X([(0.22, 0.505), (0.42, 0.51), (0.60, 0.535), (0.76, 0.57), (0.88, 0.605), (0.95, 0.58), (0.80, 0.50), (0.5, 0.45), (0.22, 0.45)]))
    inner_hind = pl.poly(X([(1.13, 0.66), (1.165, 0.64), (1.15, 0.50), (1.14, 0.42), (1.12, 0.47), (1.12, 0.58)]))
    # [artqa] whites clipped to the head / body silhouette (the throat patch floated off the neck in strong neck poses)
    whites = pl.inter(Plate.union(pl.inter(belly, body), pl.poly(X(hp['throat'])), pl.poly(X(hp['muzzle'])), pl.poly(X(hp['chin']))), Plate.union(body, pl.inter(head, body)))
    pl.wash(pl.blur(whites, 1.4 * k), BELLY, (0.9 if not ghost else 0.5) * op, edge=0.04)
    # tail: brown top, white fringe
    pl.wash(tail, COAT_DORSAL, 0.75 * coat_op * op, edge=0.15)
    fringe = pl.minus(tail, pl.poly(X(_shrink(TAIL, 0.72))))
    pl.wash(pl.inter(pl.blur(fringe, 0.7 * k), pl.poly(X([(1.15, 0.95), (1.25, 0.95), (1.25, 0.65), (1.15, 0.65)]), smooth=False)), BELLY, 0.8 * op, edge=0)
    # forehead / face
    pl.wash(pl.blur(pl.poly(X(hp['forehead'])), 1.5 * k), COAT_DORSAL, 0.6 * coat_op * op, edge=0)
    if not ghost:
        # modelling: the big masses, then glazed muscle grooves
        trunk = Plate.union(torso, neck, tail)
        legs_only = pl.blur(pl.minus(Plate.union(mfl, mhl), pl.grow(torso, 2)), 2.5 * k)
        pl.form(trunk, radius=26 * k, dark=shade, strength=0.40, hi=WHITE, hi_strength=0.08)
        pl.form(head, radius=6 * k, dark=shade, strength=0.30, hi=WHITE, hi_strength=0.10, clip=pl.blur(pl.minus(head, neck), 2 * k))
        pl.form(Plate.union(mfl, mhl), radius=3.5 * k, dark=(40, 28, 20), strength=0.42, clip=legs_only)
        groove = Plate.union(
            pl.stroke_mask(X([(0.34, 0.96), (0.36, 0.84), (0.32, 0.70), (0.26, 0.58)]), (2 * k, 7 * k, 3 * k)),      # behind the shoulder
            pl.stroke_mask(X([(0.90, 0.95), (0.92, 0.80), (0.89, 0.66), (0.86, 0.60)]), (2 * k, 7 * k, 3 * k)),      # front of the ham
            pl.stroke_mask(X([(0.25, 0.53), (0.55, 0.52), (0.82, 0.60)]), (3 * k, 9 * k, 3 * k)),                    # under the barrel
            pl.stroke_mask(X([(-0.04, 0.80), (0.05, 0.70), (0.08, 0.60)]), (1 * k, 4 * k, 1 * k)))                   # neck into chest
        pl.wash(pl.inter(pl.blur(groove, 3.5 * k), body), shade, 0.22 * op, edge=0)
        hi = Plate.union(pl.stroke_mask(X([(0.16, 0.90), (0.22, 0.78), (0.20, 0.66)]), (2 * k, 8 * k, 2 * k)),
                         pl.stroke_mask(X([(1.00, 0.95), (1.07, 0.85), (1.06, 0.72)]), (2 * k, 8 * k, 2 * k)),
                         pl.stroke_mask(X([(0.45, 0.94), (0.70, 0.93)]), (2 * k, 6 * k, 2 * k)))
        pl.wash(pl.inter(pl.blur(hi, 4 * k), body), lighter(COAT_LOW, 0.5), 0.22 * op, edge=0)
        # fur
        if detail > 0:
            def fur_dir(x, y):
                my = (oy - y) / s
                if my < 0.42:
                    a = math.pi / 2
                else:
                    a = 0.32 if my > 0.72 else 0.62
                    a = a if facing == 1 else math.pi - a
                return a
            n = int(1300 * detail * k * k)
            pl.strokes(body, n, darker(COAT_DORSAL, 0.6), length=(2.0 * k, 4.6 * k), width=(0.35, 0.6), angle=fur_dir, opacity=0.30)
            pl.strokes(pl.minus(body, whites), n // 3, lighter(COAT_LOW, 0.45), length=(1.5 * k, 3.4 * k), width=(0.3, 0.5), angle=fur_dir, opacity=0.25)
        # interior pen lines (light)
        lw = max(0.45, 0.7 * k)
        pl.ink(X([(0.335, 0.93), (0.35, 0.82), (0.31, 0.68), (0.255, 0.575)]), (0.15, lw, 0.15), INK_SOFT, 0.40)   # back of shoulder
        pl.ink(X([(0.195, 0.475), (0.215, 0.525), (0.205, 0.585)]), (0.15, lw, 0.15), INK_SOFT, 0.45)              # elbow
        pl.ink(X([(0.905, 0.93), (0.915, 0.80), (0.89, 0.67)]), (0.15, lw, 0.15), INK_SOFT, 0.35)                  # front of ham
        pl.ink(X([(0.87, 0.60), (0.84, 0.65), (0.83, 0.70)]), (0.15, lw * 0.9, 0.15), INK_SOFT, 0.35)              # flank fold
        pl.ink(X([(1.13, 0.40), (1.115, 0.36)]), (0.15, lw * 0.8, 0.15), INK_SOFT, 0.35)                           # hock
        pl.ink(X([(0.106, 0.30), (0.115, 0.31), (0.145, 0.30)]), (0.15, lw * 0.6, 0.15), INK_SOFT, 0.35)          # knee
        pl.ink(X(_rot(_rot([(-0.265, 1.172), (-0.245, 1.21), (-0.20, 1.255)], (-0.20, 1.30), head_ang), (0.14, 0.93), neck_ang)), (0.15, lw * 0.9, 0.15), INK_SOFT, 0.45)
    else:
        pl.form(torso, radius=26 * k, dark=(90, 72, 56), strength=0.16)
    # hooves and dewclaws
    if hooves is None:
        hm_ = pl.inter(Plate.union(mfl, mhl), pl.poly(X([(-0.3, 0.046), (1.7, 0.046), (1.7, -0.05), (-0.3, -0.05)]), smooth=False))
        pl.wash(hm_, HOOF, (0.95 if not ghost else 0.55) * op, edge=0.1)
        dew = Plate.union(pl.ellipse(*X.p(0.148, 0.064), 0.011 * s, 0.0075 * s, rot=0.5 * facing), pl.ellipse(*X.p(1.116, 0.066), 0.011 * s, 0.0075 * s, rot=0.5 * facing))
        pl.wash(dew, HOOF, 0.9 * op * (0.6 if ghost else 1))
    else:
        hm_ = pl.inter(Plate.union(mfl, mhl), Plate.union(*[pl.poly(X(h_), smooth=False) for h_ in hooves]))
        pl.wash(hm_, HOOF, 0.95 * op, edge=0.1)
    # near ear: tan back, pale inner edge, dark rim
    me = pl.poly(X(hp['ear_near']))
    pl.wash(me, COAT_SIDE if not ghost else mix(COAT_SIDE, PAPER, 0.45), coat_op * op, edge=0.16)
    pl.wash(pl.blur(pl.poly(X(_shrink(hp['ear_near'], 0.55))), 1.2 * k), lighter(COAT_LOW, 0.5), 0.6 * coat_op * op, edge=0)
    tip = pl.inter(me, pl.ellipse(*X.p(*hp['ear_near'][3]), 0.03 * s, 0.03 * s))
    pl.wash(pl.blur(tip, 0.8 * k), darker(COAT_DORSAL, 0.6), 0.6 * coat_op * op, edge=0)
    if not ghost:
        pl.form(me, radius=3 * k, dark=shade, strength=0.3)
    pl.contour(me, 0.45, 1.1 * k + 0.2, opacity=0.9 * op)
    # silhouette contour
    pl.contour(body, 0.55 * k + 0.2, 1.8 * k + 0.25, opacity=(0.92 if not ghost else 0.7) * op)
    # face
    ex, ey = X.p(*hp['eye'])
    ring = pl.ellipse(ex, ey, 0.025 * s, 0.018 * s, rot=-0.3 * facing)
    pl.wash(pl.blur(ring, 0.5 * k), BELLY, (0.85 if not ghost else 0.5) * op, edge=0)
    eye = pl.ellipse(ex, ey, 0.016 * s, 0.011 * s, rot=-0.3 * facing)
    pl.flat(eye, (22, 16, 14), 0.95 * op)
    pl.flat(pl.ellipse(ex - 0.004 * s * facing, ey - 0.003 * s, max(0.6, 0.004 * s), max(0.6, 0.0035 * s)), WHITE, 0.85 * op)
    nx, ny = X.p(*hp['nose'])
    nose = pl.ellipse(nx + 0.004 * s * facing, ny - 0.001 * s, 0.024 * s, 0.021 * s)
    pl.flat(pl.inter(nose, body), NOSE, 0.92 * op)
    pl.ink(X(_rot(_rot([(-0.47, 1.175), (-0.445, 1.178), (-0.425, 1.172)], (-0.20, 1.30), head_ang), (0.14, 0.93), neck_ang)), (0.15, 0.6 * k + 0.1, 0.15), INK, 0.6 * op)
    if buck:
        paint_antler(pl, X, hp['beam'], hp['tines'], opacity=op)
    return dict(X=X, body=body, torso=torso, hp=hp, front=mfl, hind=mhl)


def _shrink(pts, f):
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    return [(cx + (x - cx) * f, cy + (y - cy) * f) for x, y in pts]
