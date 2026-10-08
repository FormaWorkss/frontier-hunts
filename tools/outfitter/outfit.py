#!/usr/bin/env python3
"""[outfitter] Every worn garment of Frontier Hunts, from one spec: geometry (Java table OutfitGeometry), painted textures
(Ultra = 4 texels per model pixel '_hd', Vanilla = 1 texel per pixel), previews on the vanilla player in all poses.

usage: python3 tools/outfitter/outfit.py <repo> [--preview] [--only name,...]

Geometry rules (see docs/ws/outfitter.md):
* every box sits on exactly one vanilla part (head, body, right arm/leg mirrored to the left) and follows that part's
  pivot rigidly - nothing simulates, nothing swings on its own;
* coat skirts / robe hems / long fur are drawn on the LEGS (each thigh carries its piece of the hem), never hanging
  from the body, so they move with the legs in walk/sprint/sneak/swim/prone/riding instead of flapping or letting a leg
  pass through them;
* thickness: shells +0.30..0.45 px (like vanilla armour layers), heavy fur +0.65..0.95, details are separate thin boxes
  >= 0.03 px off the shell (no coplanar faces); leg shells stop 0.45 px past the body centre line;
* slim (Alex) skins get their own arm boxes (3 px wide arms) - part code 4.
"""
import os, sys, math, json, zlib
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import materials as MT

# [clothing] reference jar for the photographic ghillie materials (env FH_REF_JAR overrides)
REF_JAR = os.environ.get('FH_REF_JAR', '/home/claude/fh2/.build/FrontierHunts-1.3.0.jar')
R = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith('--') else '.'
S = 4
PART_CODE = {'head': 0, 'body': 1, 'arm': 2, 'leg': 3, 'arm_slim': 4, 'arm_l': 5, 'leg_l': 6, 'arm_slim_l': 7}
PIVOT = {'head': (0, 0, 0), 'body': (0, 0, 0), 'arm': (-5, 2, 0), 'arm_slim': (-5, 2, 0), 'leg': (-1.9, 12, 0),
         'arm_l': (-5, 2, 0), 'arm_slim_l': (-5, 2, 0), 'leg_l': (-1.9, 12, 0)}


class B:
    def __init__(self, part, x, y, z, w, h, d, g=0.0, mat='suede', deco=(), pose=None, share=None, tag=None, opts=None):
        self.part, self.x, self.y, self.z, self.w, self.h, self.d, self.g = part, x, y, z, w, h, d, g
        self.mat, self.deco, self.pose, self.share, self.tag = mat, tuple(deco), pose, share, tag
        self.opts = opts or {}
        self.uv = None
        self.left = False

    def clone(self, **kw):
        b = B(self.part, self.x, self.y, self.z, self.w, self.h, self.d, self.g, self.mat, self.deco, self.pose, self.share, self.tag, dict(self.opts))
        b.left = self.left
        for k, v in kw.items():
            setattr(b, k, v)
        return b


# ------------------------------------------------------------------------------------------------ geometry helpers
def head(g, mat, deco=(), **kw):
    return B('head', -4, -8, -4, 8, 8, 8, g, mat, deco, **kw)


def body(g, mat, deco=(), y=0.0, h=12.0, **kw):
    return B('body', -4, y, -2, 8, h, 4, g, mat, deco, **kw)


def arm(g, mat, deco=(), y=-2.0, h=12.0, **kw):
    return B('arm', -3, y, -2, 4, h, 4, g, mat, deco, **kw)


def leg_inner(g):
    """[clothing] Grown inner-side face of a leg shell (part-local x). It grows with the shell's thickness (2.31 at
    0.30 px, +0.7 per px), so two garments on the same leg never share the inner face (it was 2.35 for every shell and
    z-fought between the legs while walking), and it always clears the skin's pants overlay (2.25)."""
    return 2.31 + 0.7 * (g - 0.3)


def leg(g, mat, deco=(), y=0.0, h=12.0, **kw):
    """Right leg box; the inner side stops a little past the centre line (see leg_inner) so the two legs overlap inside
    each other and never show a gap."""
    x0 = -2.0
    x1 = leg_inner(g) - g
    return B('leg', x0, y, -2, x1 - x0, h, 4, g, mat, deco, **kw)


def card_front(part, x, y, z, w, h, mat, tilt=0.0, share=None, **kw):
    """A zero-depth card in the x-y plane (seen from front/back); tilt (rad) swings its lower edge outward (-z)."""
    if tilt:
        return B(part, -w / 2, 0, 0, w, h, 0, 0, mat, pose=(x + w / 2, y, z, -tilt, 0, 0), share=share, **kw)
    return B(part, x, y, z, w, h, 0, 0, mat, share=share, **kw)


def card_back(part, x, y, z, w, h, mat, tilt=0.0, share=None, **kw):
    if tilt:
        return B(part, -w / 2, 0, 0, w, h, 0, 0, mat, pose=(x + w / 2, y, z, tilt, 0, 0), share=share, **kw)
    return B(part, x, y, z, w, h, 0, 0, mat, share=share, **kw)


def card_side(part, x, y, z, d, h, mat, outward=-1, tilt=0.0, share=None, **kw):
    """Zero-width card in the y-z plane on a side; outward -1 = toward -x (the right side of a right limb)."""
    if tilt:
        return B(part, 0, 0, -d / 2, 0, h, d, 0, mat, pose=(x, y, z + d / 2, 0, 0, -outward * tilt), share=share, **kw)
    return B(part, x, y, z, 0, h, d, 0, mat, share=share, **kw)


def slim_arm(b):
    """Map a wide right-arm box (x in [-3, 1]) onto the slim arm (x in [-2, 1]): boxes on the arm scale with it, details
    standing out past the outer side (x <= -3) keep their size and move in by 1 px with the surface."""
    f = lambda x: 1 - (1 - x) * 0.75
    nb = b.clone(part='arm_slim')
    if b.pose:
        px, py, pz, rx, ry, rz = b.pose
        nb.pose = (px + 1 if px <= -2.9 else f(px), py, pz, rx, ry, rz)
    elif b.x + b.w <= -2.99:
        nb.x = b.x + 1
    else:
        x0, x1 = f(b.x), f(b.x + b.w)
        if b.w <= 0.6:
            nb.x = f(b.x + b.w / 2) - b.w / 2; nb.w = b.w
        else:
            nb.x, nb.w = x0, x1 - x0
    if nb.share:
        nb.share = nb.share + '_slim'
    return nb


# ------------------------------------------------------------------------------------------------ outfits
def hood(mat, g=0.62, extra=()):
    return [head(g, mat, ('hood_open', 'hood_seam') + tuple(extra), opts={'bottom_open': True}),
            B('head', -3.7, -8.5, -5.05, 7.4, 0.7, 0.5, 0.0, mat, ('binding',)),
            ]


def coverall(mat):
    o = []
    o += hood(mat)
    o += [body(0.42, mat, ('zip', 'yoke', 'waist', 'side_seam', 'handwarmer')),
          B('body', -3.55, 1.5, -2.86, 2.7, 3.0, 0.4, 0, mat, ('pocket',)),
          B('body', 0.85, 1.5, -2.86, 2.7, 3.0, 0.4, 0, mat, ('pocket',)),
          B('body', -0.32, 0.6, -2.62, 0.64, 1.1, 0.18, 0, 'metal'),
          arm(0.4, mat, ('shoulder', 'elbow')),
          arm(0.58, mat, ('rib', 'darken'), y=8.7, h=1.3),
          B('arm', -3.78, 8.75, -1.3, 0.22, 1.15, 2.6, 0, mat, ('darken', 'velcro'), tag='under_mitten'),
          leg(0.4, mat, ('knee',), h=11.0),
          # [clothing] the cargo pocket and knee pad give way to a long coat's hem (a scent suit under a fur coat); the pad
          # ends above a boot cuff
          B('leg', -2.95, 2.3, -1.6, 0.5, 3.9, 3.2, 0, mat, ('pocket_side',), tag='under_hem'),
          B('leg', -1.75, 4.0, -2.8, 3.5, 2.0, 0.36, 0, mat, ('kneepad',), tag='under_hem'),
          leg(0.46, mat, ('rib', 'darken'), y=10.0, h=1.4)]
    return o


def carbon_hood():
    return hood('carbon', extra=('carbon_logo',))


def carbon_jacket():
    m = 'carbon'
    return [body(0.45, m, ('zip_grey', 'yoke', 'side_seam', 'chest_zip')),
            body(0.62, m, ('rib', 'darken'), y=10.9, h=1.2),
            arm(0.42, m, ('shoulder', 'elbow')),
            arm(0.6, m, ('rib', 'darken'), y=8.7, h=1.3),
            B('body', -0.32, 0.4, -2.68, 0.64, 1.1, 0.2, 0, 'metal_grey')]


def carbon_trousers():
    m = 'carbon'
    return [body(0.34, m, ('waistband', 'fly'), y=9.4, h=2.6),
            leg(0.33, m, ('knee', 'side_seam_leg')),
            B('leg', -2.78, 2.6, -1.4, 0.42, 3.4, 2.8, 0, m, ('pocket_side',), tag='under_hem'),  # [clothing] under a coat hem
            leg(0.44, m, ('rib', 'darken'), y=10.5, h=1.5)]


def fur_hat():
    return [B('head', -4, -9.1, -4, 8, 3.6, 8, 0.62, 'fur_wolf', ('fur_top',)),
            B('head', -4, -7.35, -4.98, 8, 2.5, 0.9, 0.18, 'fur_wolf_band', ('brim',)),
            B('head', -4.92, -5.3, -2.7, 0.75, 4.7, 5.4, 0.12, 'fur_wolf', ('flap',)),
            B('head', 4.17, -5.3, -2.7, 0.75, 4.7, 5.4, 0.12, 'fur_wolf', ('flap',)),
            B('head', -4, -5.3, 4.06, 8, 4.2, 0.75, 0.12, 'fur_wolf', ('flap',)),
            card_side('head', -5.0, -0.7, -2.6, 5.2, 1.3, 'shag_wolf', outward=-1, share='hat_shag_s'),
            card_side('head', 5.0, -0.7, -2.6, 5.2, 1.3, 'shag_wolf', outward=1, share='hat_shag_s'),
            card_back('head', -3.9, -1.15, 4.97, 7.8, 1.2, 'shag_wolf', share='hat_shag_b'),
            B('head', -4.62, -0.6, -0.3, 0.22, 1.8, 0.22, 0, 'sinew'),
            B('head', 4.4, -0.6, -0.3, 0.22, 1.8, 0.22, 0, 'sinew')]


def hem_fringe(y, g, mat, length, share):
    """Fringe cards around a thigh hem (front, back, outer side), bottom edge at y."""
    x0, x1 = -2.0 - g - 0.02, leg_inner(g) - 0.05
    z = 2.0 + g + 0.06
    return [card_front('leg', x0, y - 0.6, -z, x1 - x0, length, mat, share=share + '_f'),
            card_back('leg', x0, y - 0.6, z, x1 - x0, length, mat, share=share + '_f'),
            card_side('leg', -2.0 - g - 0.06, y - 0.6, -2.0 - g, 4.0 + 2 * g, length, mat, outward=-1, share=share + '_s')]


def buckskin_coat():
    o = [body(0.45, 'suede', ('placket', 'side_seam', 'fringe_shadow')),
         B('body', -4, -0.3, -2, 8, 4.0, 4, 0.75, 'suede_dark', ('yoke_edge',)),
         card_front('body', -4.75, 3.15, -2.82, 9.5, 3.7, 'fringe_hdr', share='coat_yoke_fr'),
         card_back('body', -4.75, 3.15, 2.82, 9.5, 3.7, 'fringe_hdr', share='coat_yoke_fr'),
         body(0.62, 'belt', ('buckle_strap',), y=9.7, h=1.5),
         B('body', -0.85, 9.5, -2.86, 1.7, 1.9, 0.26, 0, 'iron', ('buckle',)),
         arm(0.38, 'suede', ('elbow', 'wear')),
         card_front('arm', -4.95, -0.6, 0.0, 1.55, 7.2, 'fringe_side', share='coat_sleeve_fr'),
         card_front('arm', -4.95, 6.6, 0.0, 1.55, 2.2, 'fringe_side', share='coat_sleeve_fr_l', tag='under_mitten'),  # [clothing] split: the wrist end hides under mittens
         arm(0.56, 'bead', ('beadwork',), y=8.4, h=1.6),
         leg(0.64, 'suede', ('hem_side',), y=0.0, h=3.9)]
    o += hem_fringe(3.9 + 0.64, 0.64, 'fringe', 2.5, 'coat_hem')
    return o


def buckskin_leggings():
    # [clothing] shells 0.33 (clear of the skin's 0.25 overlay); the fringe in two cards: the upper one hides under a long
    # coat, the lower one in a boot or under the knee-length hide robe; the garter tie under either
    return [body(0.33, 'suede_dark', ('lacing',), y=9.8, h=2.2),
            leg(0.33, 'suede', ('seam_outer', 'wear')),
            card_front('leg', -3.78, 0.6, 0.0, 1.45, 5.4, 'fringe_side', share='leg_fr_u', tag='under_hem'),
            card_front('leg', -3.78, 6.0, 0.0, 1.45, 5.2, 'fringe_side', share='leg_fr_l', tag='under_boot_long'),
            leg(0.45, 'bead', ('beadwork',), y=5.0, h=0.9),
            B('leg', -2.93, 5.8, -0.55, 0.22, 1.6, 0.22, 0, 'sinew', tag='under_hem_boot')]


def bear_fur_coat():
    o = [body(0.8, 'fur_bear', ('toggles', 'center_part')),
         B('body', -4.6, -1.4, -2.6, 9.2, 2.6, 5.2, 0.35, 'fur_bear_ruff', ('ruff',)),
         card_front('body', -4.9, 1.0, -3.0, 9.8, 1.4, 'shag_bear', share='bear_ruff_f'),
         card_back('body', -4.9, 1.0, 3.0, 9.8, 1.4, 'shag_bear', share='bear_ruff_f'),
         body(0.95, 'belt', ('buckle_strap',), y=9.6, h=1.6),
         B('body', -0.9, 9.4, -3.06, 1.8, 2.0, 0.26, 0, 'iron', ('buckle',)),
         arm(0.7, 'fur_bear'),
         # [clothing] the fur cuff gives way to sewn-on mittens (their own fur cuff takes its place)
         arm(0.95, 'fur_bear_band', ('cuff_fur',), y=8.0, h=2.0, tag='under_mitten'),
         card_side('arm', -3.98, 9.9, -2.95, 5.9, 1.2, 'shag_bear', outward=-1, share='bear_cuff_s', tag='under_mitten'),
         card_front('arm', -3.95, 9.9, -2.98, 4.9, 1.2, 'shag_bear', share='bear_cuff_f', tag='under_mitten'),
         card_back('arm', -3.95, 9.9, 2.98, 4.9, 1.2, 'shag_bear', share='bear_cuff_f', tag='under_mitten'),
         leg(0.86, 'fur_bear', ('hem_side',), y=0.0, h=5.0)]
    o += hem_fringe(5.0 + 0.86, 0.86, 'shag_bear', 1.5, 'bear_hem')
    return o


def hide_robe():
    o = [body(0.62, 'robe_hide', ('robe_wrap', 'painted_band')),
         B('body', -4, -0.4, -2, 8, 4.8, 4, 1.0, 'robe_hide', ('cape_edge', 'painted_cape')),
         B('body', -4.4, -1.6, -2.4, 8.8, 2.2, 4.8, 0.5, 'bison', ('ruff',)),
         card_front('body', -5.0, 4.8, -3.04, 10.0, 2.0, 'shag_bison', share='robe_cape_f'),
         card_back('body', -5.0, 4.8, 3.04, 10.0, 2.0, 'shag_bison', share='robe_cape_f'),
         body(0.8, 'rawhide', ('sash',), y=9.5, h=1.6),
         card_side('body', -4.86, 10.1, -0.9, 1.8, 3.0, 'tassel', outward=-1, share='robe_tassel'),
         arm(0.6, 'robe_hide', ('wear',)),
         arm(0.85, 'bison', ('cuff_fur',), y=8.0, h=2.0, tag='under_mitten'),  # [clothing] gives way to mittens
         leg(0.8, 'robe_hide', ('robe_leg', 'painted_hem'), y=0.0, h=7.4)]
    o += hem_fringe(7.4 + 0.8, 0.8, 'shag_bison', 1.3, 'robe_hem')
    return o


def fur_mukluks():
    return [leg(0.56, 'mukluk', ('wraps', 'sole_band'), y=7.4, h=4.6),
            # [clothing] the toe cap is a slab in front of the shaft only (z -3.15 .. -2.6, flush with the sole): it meets no
            # other garment's box, so nothing can z-fight with it
            B('leg', -1.75, 10.6, -2.95, 3.4, 1.76, 0.15, 0.2, 'mukluk', ('toe',)),
            leg(0.64, 'bead', ('beadwork',), y=9.2, h=0.8),
            leg(0.70, 'fur_wolf_band', ('cuff_fur',), y=7.0, h=1.6),  # [clothing] 6.6 -> 7.0 (clears knee pads, pockets), 0.78 -> 0.70 (under the robe's hem)
            card_front('leg', -2.85, 8.6, -2.83, 5.1, 1.1, 'shag_wolf', share='muk_shag_f'),
            card_back('leg', -2.85, 8.6, 2.83, 5.1, 1.1, 'shag_wolf', share='muk_shag_f'),
            card_side('leg', -2.83, 8.6, -2.78, 5.56, 1.1, 'shag_wolf', outward=-1, share='muk_shag_s')]


def mittens():
    # [clothing] 0.78 px from y 8.7: outside every sleeve end (bear coat 0.70), clear of the buckskin bead cuff's top
    return [arm(0.78, 'mukluk', ('mitten',), y=8.7, h=1.9),
            arm(1.0, 'fur_wolf_band', ('cuff_fur',), y=7.8, h=1.1)]


def lining_collar():
    # [clothing] 0.5 px and a little wider / deeper: clears the buckskin coat's yoke and every jacket collar
    return [B('body', -4.4, -1.25, -2.35, 8.8, 1.9, 4.7, 0.5, 'fur_wolf_band', ('ruff',))]


HARNESS_DEPTHS = [0.3, 0.5, 0.7, 0.9, 1.1]


def harness(t):
    """Pack shoulder straps, sternum strap and hip belt over a chest layer of depth t (px past the body box)."""
    s = 0.32  # strap thickness
    o = []
    for side in (-1, 1):
        xc = side * 2.35
        x0 = xc - 0.85
        # over the shoulder (top), down the front to the hip belt; behind, the straps vanish into the pack
        o.append(B('body', x0, -t - s, -2 - t - s, 1.7, s, 4 + 2 * t + 2 * s, 0, 'webbing', ('strap_top',), share='h_top_%d' % HARNESS_DEPTHS.index(t)))
        o.append(B('body', x0, -t, -2 - t - s, 1.7, 9.6 + t, s, 0, 'webbing', ('strap', 'ladder'), share='h_front_%d' % HARNESS_DEPTHS.index(t)))
    o.append(B('body', -1.5, 3.3, -2 - t - s - 0.12, 3.0, 0.55, 0.12, 0, 'webbing', ('sternum',)))
    o.append(B('body', -0.42, 3.2, -2 - t - s - 0.3, 0.84, 0.75, 0.18, 0, 'buckle_black'))
    o.append(B('body', -4, 9.4, -2, 8, 1.5, 4, t + 0.34, 'webbing', ('hipbelt',)))
    o.append(B('body', -0.7, 9.25, -2 - t - 0.34 - 0.28, 1.4, 1.8, 0.28, 0, 'buckle_black'))
    return o


GHILLIE = ['woodland', 'grassland', 'wetland', 'snow']


def ghillie_cards(piece, pat):
    """Jute/leaf tufts, rigid on their part, short enough to never pass below the part they hang from."""
    m = 'jute_' + pat
    rng = np.random.default_rng(zlib.crc32(repr((piece, pat)).encode()))
    o = []
    def sh(i):
        return 'gj_%s_%d' % (pat, i % 6)
    def shs(i):
        return 'gjs_%s_%d' % (pat, i % 4)
    if piece == 'hood':
        k = 0
        for row, y in enumerate((-8.4, -6.6, -4.8)):
            for x in np.linspace(-3.2, 3.2, 4):
                o.append(card_back('head', x - 1.3 + rng.normal(0, 0.3), y, 4.7, 2.6, 3.0, m, tilt=0.28, share=sh(k))); k += 1
            for z in np.linspace(-2.6, 3.2, 3):
                o.append(card_side('head', -4.72, y, z - 1.2, 2.4, 3.0, m, outward=-1, tilt=0.3, share=shs(k)))
                o.append(card_side('head', 4.72, y, z - 1.2, 2.4, 3.0, m, outward=1, tilt=0.3, share=shs(k + 1))); k += 2
        for x in np.linspace(-3.0, 3.0, 4):  # crown tufts lying back over the top
            o.append(B('head', -1.3, 0, 0, 2.6, 0, 3.0, 0, m, pose=(x, -8.72, -3.6 + rng.random() * 3, 0, rng.normal(0, 0.3), 0), share='gjt_%s' % pat))
        for x in np.linspace(-3.4, 3.4, 4):  # short brow fringe, above the eyes
            o.append(card_front('head', x - 1.0, -8.55, -4.75, 2.0, 1.6, m, tilt=0.15, share=sh(k))); k += 1
        o.append(card_back('head', -4.0, -3.0, 4.75, 8.0, 3.6, m, tilt=0.12, share='gjv_%s' % pat))  # veil over the neck
        return o
    if piece == 'jacket':
        k = 0
        for y in (0.2, 2.8, 5.4, 8.0):
            for x in np.linspace(-3.2, 3.2, 4):
                xx = x - 1.2 + rng.normal(0, 0.25)
                hh = round(float(np.clip(3.4 * (0.8 + 0.35 * rng.random()), 2.6, 12.2 - y)), 1)
                o.append(card_front('body', xx, y + rng.normal(0, 0.2), -2.5, 2.4, hh, m, tilt=0.16 + 0.14 * rng.random(), share=sh(k)))
                o.append(card_back('body', xx, y + rng.normal(0, 0.2), 2.5, 2.4, hh, m, tilt=0.18 + 0.14 * rng.random(), share=sh(k + 3))); k += 1
        for y in (-1.6, 1.2, 4.0, 6.6):
            t = 'under_mitten' if y > 3.0 else None  # [clothing] forearm tufts hide under sewn-on mittens
            for z in (-1.5, 0.7):
                o.append(card_side('arm', -3.48, y, z - 1.1, 2.2, 3.4, m, outward=-1, tilt=0.3, share=shs(k), tag=t)); k += 1
            o.append(card_back('arm', -2.9, y, 2.48, 2.4, 3.4, m, tilt=0.25, share=sh(k), tag=t))
            o.append(card_front('arm', -2.3, y + 0.8, -2.48, 2.4, 3.0, m, tilt=0.2, share=sh(k + 1), tag=t)); k += 1
        return o
    if piece == 'trousers':
        k = 0
        # [clothing] the thigh rows hide under a long coat's hem, the shin rows inside a boot; the row between them stops
        # above the boot cuff
        tags = {0.3: 'under_hem', 3.0: 'under_hem', 5.7: 'under_hem_boot', 8.2: 'under_boot'}
        for y in (0.3, 3.0, 5.7, 8.2):
            t = tags[y]
            for x in (-1.9, 0.2):
                hh = round(float(3.3 * (0.8 + 0.35 * rng.random())), 1)
                if y == 3.0:
                    hh = min(hh, 2.9)
                o.append(card_front('leg', x - 0.2, y + rng.normal(0, 0.2), -2.45, 2.3, hh, m, tilt=0.14 + 0.14 * rng.random(), share=sh(k), tag=t))
                o.append(card_back('leg', x - 0.2, y + rng.normal(0, 0.2), 2.45, 2.3, hh, m, tilt=0.16 + 0.14 * rng.random(), share=sh(k + 2), tag=t)); k += 1
            o.append(card_side('leg', -2.45, y, -1.2, 2.4, 2.9 if y == 3.0 else 3.3, m, outward=-1, tilt=0.3, share=shs(k), tag=t))
        return o
    return o


def ghillie(piece, pat):
    m = 'mesh_' + pat
    if piece == 'hood':
        o = hood(m)
    elif piece == 'jacket':
        o = [body(0.45, m, ('lacing_front', 'netting')), arm(0.42, m, ('netting',)), arm(0.56, m, ('rib', 'darken'), y=8.8, h=1.2)]
    else:
        o = [body(0.34, m, ('waistband',), y=9.4, h=2.6), leg(0.36, m, ('netting', 'knee')), leg(0.46, m, ('rib', 'darken'), y=10.3, h=1.4)]
    return o + ghillie_cards(piece, pat)


def all_outfits():
    """name -> (boxes, uv_size, texture group)"""
    O = {}
    for cid in MT.CAMO_STYLE:
        O['coverall/' + cid] = (coverall('camo_' + MT.CAMO_STYLE[cid]), 'coverall/' + cid)
    O['carbon/hood'] = (carbon_hood(), 'carbon/carbon')
    O['carbon/jacket'] = (carbon_jacket(), 'carbon/carbon')
    O['carbon/trousers'] = (carbon_trousers(), 'carbon/carbon')
    for p in GHILLIE:
        for piece in ('hood', 'jacket', 'trousers'):
            O['ghillie/%s_%s' % (p, piece)] = (ghillie(piece, p), 'ghillie/' + p)
    O['garment/fur_hat'] = (fur_hat(), 'garment/fur_hat')
    O['garment/buckskin_coat'] = (buckskin_coat(), 'garment/buckskin_coat')
    O['garment/buckskin_leggings'] = (buckskin_leggings(), 'garment/buckskin_leggings')
    O['garment/bear_fur_coat'] = (bear_fur_coat(), 'garment/bear_fur_coat')
    O['garment/hide_robe'] = (hide_robe(), 'garment/hide_robe')
    O['garment/fur_mukluks'] = (fur_mukluks(), 'garment/fur_mukluks')
    O['extra/mittens'] = (mittens(), 'extra/extras')
    O['extra/lining'] = (lining_collar(), 'extra/extras')
    for i, t in enumerate(HARNESS_DEPTHS):
        O['extra/harness_%d' % i] = (harness(t), 'extra/extras')
    # slim arm variants, then the left limbs: same geometry mirrored (left = True), own UV unless the box is a shared
    # card, so the print does not mirror between the two legs/arms
    for k, (boxes, grp) in O.items():
        boxes += [slim_arm(b) for b in boxes if b.part == 'arm']
        boxes += [b.clone(part=b.part + '_l', left=True) for b in boxes if b.part in ('arm', 'leg', 'arm_slim')]
    return O


# chest garments whose hem is drawn on the legs (the model shows its legs while the chest slot renders)
HEM_ON_LEGS = {'garment/buckskin_coat', 'garment/bear_fur_coat', 'garment/hide_robe'}


def chest_depth(boxes):
    """How far (px) the garment stands off the back of the body box (for packs/quivers/harness)."""
    d = 0.0
    for b in boxes:
        if b.part == 'body' and not b.pose and b.d > 0 and b.y < 8:
            d = max(d, b.z + b.d + b.g - 2.0, -(b.z - b.g) - 2.0)
    return round(d, 3)


# ------------------------------------------------------------------------------------------------ UV packing
def region(b):
    return 2 * (b.d + b.w), b.d + b.h


def pack(groups, uvw, uvh):
    """Pack all boxes of a texture group; boxes with the same share key reuse one UV region."""
    boxes = [b for bs in groups for b in bs]
    keyed = {}
    order = []
    for b in boxes:
        k = (b.share, round(b.w, 3), round(b.h, 3), round(b.d, 3), b.mat) if b.share else id(b)
        if k in keyed:
            continue
        keyed[k] = b
        order.append(b)
    order.sort(key=lambda b: (-math.ceil(region(b)[1]), -math.ceil(region(b)[0])))
    x = y = shelf = 0
    pos = {}
    for b in order:
        rw, rh = region(b)
        rw, rh = max(1, int(math.ceil(rw))), max(1, int(math.ceil(rh)))
        if x + rw > uvw:
            x, y, shelf = 0, y + shelf, 0
        if y + rh > uvh:
            return False
        pos[id(b)] = (x, y)
        x += rw
        shelf = max(shelf, rh)
    for b in boxes:
        k = (b.share, round(b.w, 3), round(b.h, 3), round(b.d, 3), b.mat) if b.share else id(b)
        b.uv = pos[id(keyed[k])]
        b.uvref = keyed[k]
    return True


# ------------------------------------------------------------------------------------------------ painting
FACE_ORDER = ('top', 'bottom', 'minx', 'front', 'maxx', 'back')


def face_rects(b):
    u, v = b.uv
    w, h, d = b.w, b.h, b.d
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'minx': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'maxx': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }


def face_points(b, face, fw, fh):
    """Part-local model coords (x, y, z arrays fh x fw) of the face's texel centres (rest pose, grown box)."""
    g = b.g
    x0, y0, z0 = b.x - g, b.y - g, b.z - g
    x1, y1, z1 = b.x + b.w + g, b.y + b.h + g, b.z + b.d + g
    s = (np.arange(fw) + 0.5)[None, :] / max(fw, 1)
    t = (np.arange(fh) + 0.5)[:, None] / max(fh, 1)
    one = np.ones((fh, fw))
    if face == 'front':
        X, Y, Z = x0 + s * (x1 - x0) * one, y0 + t * (y1 - y0) * one, z0 * one
    elif face == 'back':
        X, Y, Z = x1 - s * (x1 - x0) * one, y0 + t * (y1 - y0) * one, z1 * one
    elif face == 'minx':
        X, Y, Z = x0 * one, y0 + t * (y1 - y0) * one, z1 - s * (z1 - z0) * one
    elif face == 'maxx':
        X, Y, Z = x1 * one, y0 + t * (y1 - y0) * one, z0 + s * (z1 - z0) * one
    elif face == 'top':
        X, Y, Z = x0 + s * (x1 - x0) * one, y0 * one, z1 - t * (z1 - z0) * one
    else:
        X, Y, Z = x0 + s * (x1 - x0) * one, y1 * one, z1 - t * (z1 - z0) * one
    if b.pose:
        px, py, pz = b.pose[:3]
        X, Y, Z = X + px, Y + py, Z + pz
    return X, Y, Z


def print_coords(b, face, X, Y, Z):
    """World-anchored 2D print coordinates (model px) so camo runs on across panels."""
    p = PIVOT[b.part]
    Xw, Yw, Zw = X + p[0], Y + p[1], Z + p[2]
    if b.left:
        Xw, Zw = Xw + 23.0, Zw + 11.0
    if face == 'front':
        return Xw + 100, Yw
    if face == 'back':
        return -Xw + 140, Yw
    if face == 'minx':
        return -Zw + 60, Yw + 7
    if face == 'maxx':
        return Zw + 20, Yw + 13
    return Xw + 33, Zw + 71


_TILES = {}


def camo_sample(style, U, V):
    if style not in _TILES:
        _TILES[style] = MT.camo_tile(style, S)
    t = _TILES[style]
    n = t.shape[0]
    i = (np.floor(V * S).astype(int)) % n
    j = (np.floor(U * S).astype(int)) % n
    return t[i, j]


_MESH = {}


def mesh_sample(pat, U, V):
    """Ghillie shell: the mod's own photographic camo materials, resampled to ~22 px repeat + knotted netting."""
    if pat not in _MESH:
        import zipfile, io
        z = zipfile.ZipFile(REF_JAR)
        p = 'assets/frontierhunts/textures/equipment/shelters/camouflage_v1.png' if pat == 'woodland' else 'assets/frontierhunts/textures/equipment/ghillie/%s.png' % pat
        im = Image.open(io.BytesIO(z.read(p))).convert('RGB').resize((32 * S, 32 * S), Image.LANCZOS)
        _MESH[pat] = np.asarray(im, float)
    t = _MESH[pat]
    n = t.shape[0]
    i = (np.floor(V * S).astype(int)) % n
    j = (np.floor(U * S).astype(int)) % n
    return t[i, j]


def rng_for(b, face):
    return np.random.default_rng(zlib.crc32(repr((b.mat, b.deco, face, round(b.w, 2), round(b.h, 2), round(b.d, 2))).encode()))


def base_material(b, face, fw, fh, X, Y, Z):
    """RGBA (fh, fw, 4)."""
    m = b.mat
    rng = rng_for(b, face)
    A = np.full((fh, fw), 255.0)
    U, V = print_coords(b, face, X, Y, Z)
    if m.startswith('camo_'):
        rgb = camo_sample(m[5:], U, V)
    elif m == 'carbon':
        rgb = camo_sample('carbon', U, V)
    elif m.startswith('mesh_'):
        rgb = mesh_sample(m[5:], U, V)
    elif m in ('suede', 'suede_dark', 'mukluk', 'robe_hide', 'rawhide'):
        base = {'suede': '#a6774a', 'suede_dark': '#6f4a2b', 'mukluk': '#6e4f34', 'robe_hide': '#a9804f', 'rawhide': '#cdb487'}[m]
        rgb = MT.suede(fh, fw, S, rng, base)
    elif m in ('fur_wolf', 'fur_wolf_band'):
        rgb = MT.fur(fh, fw, S, rng, 'wolf_band' if m.endswith('band') else 'wolf')
        if face in ('top', 'bottom'):
            rgb = MT.fur(fw, fh, S, rng, 'wolf').transpose(1, 0, 2) if fw != fh else rgb
    elif m in ('fur_bear', 'fur_bear_ruff', 'fur_bear_band'):
        rgb = MT.fur(fh, fw, S, rng, 'bear')
        if m != 'fur_bear':
            rgb = rgb * 1.08
    elif m == 'bison':
        rgb = MT.fur(fh, fw, S, rng, 'bison')
    elif m == 'bead':
        rgb = MT.suede(fh, fw, S, rng, '#8a6643')
    elif m == 'belt':
        rgb = MT.leather(fh, fw, S, rng, '#4a3020')
    elif m in ('iron', 'metal', 'metal_grey'):
        base = {'iron': '#3c3a38', 'metal': '#2a2a2a', 'metal_grey': '#8a8d90'}[m]
        rgb = MT.fabric(fh, fw, S, rng, base, weave=0.1)
    elif m == 'buckle_black':
        rgb = MT.fabric(fh, fw, S, rng, '#1e1f1e', weave=0.05)
    elif m == 'sinew':
        rgb = MT.fabric(fh, fw, S, rng, '#c9b48e', weave=0.15)
    elif m == 'webbing':
        rgb = MT.fabric(fh, fw, S, rng, '#4f5038', weave=0.12)
        if fh > 2 and fw > 2:
            ax = 0 if face in ('top', 'bottom') else 1
            yy = np.arange(fh)[:, None] * np.ones((1, fw))
            xx = np.arange(fw)[None, :] * np.ones((fh, 1))
            lines = ((xx if face in ('front', 'back', 'minx', 'maxx') and fh > fw else yy) % 2 == 0)
            rgb = rgb * (1 - 0.08 * lines[..., None])
    elif m.startswith('fringe') or m.startswith('shag') or m.startswith('jute') or m == 'tassel':
        return alpha_material(b, face, fw, fh, rng)
    else:
        raise SystemExit('material ' + m)
    return np.dstack([np.clip(rgb, 0, 255), A])


def alpha_material(b, face, fw, fh, rng):
    m = b.mat
    rgb = np.zeros((fh, fw, 3)); A = np.zeros((fh, fw))
    if face in ('top', 'bottom') or fw == 0 or fh == 0:
        return np.dstack([rgb, A])
    if m.startswith('fringe'):
        base = MT.col('#d4b285')
        hdr = 0
        if m == 'fringe_hdr':
            hdr = int(round(1.2 * S))  # the stitched band the fringe is cut from
        if m == 'fringe_side':
            # card on a sleeve/leg seam: strands leave the seam (left edge) and hang down and out
            y = 0.0
            while y < fh:
                L = fw * (0.7 + 0.3 * rng.random())
                sh = 0.78 + 0.35 * rng.random()
                drop = 1.4 + 0.6 * rng.random()
                for k in range(int(L)):
                    yy = int(y + k * drop)
                    for t in range(max(1, S // 2)):
                        if 0 <= yy + t < fh:
                            rgb[yy + t, k] = base * sh * (1 - 0.25 * k / max(1, L)); A[yy + t, k] = 255
                y += max(1.5, S * 0.55)
            rgb[:, :max(1, S // 2)] = base * 0.72; A[:, :max(1, S // 2)] = 255
            return np.dstack([rgb, A])
        x = 0
        while x < fw:
            sw = max(1, S // 2)
            L = int(fh - rng.integers(0, max(1, fh // 4)))
            sh = 0.78 + 0.35 * rng.random()
            sway = rng.normal(0, 0.06)
            for yy in range(hdr, L):
                xx = int(round(x + sway * (yy - hdr)))
                if 0 <= xx < fw:
                    rgb[yy, xx:xx + sw] = base * sh * (1 - 0.2 * (yy - hdr) / max(1, L - hdr)); A[yy, xx:xx + sw] = 255
            x += sw + max(1, S // 2)
        if hdr:
            band = MT.suede(hdr, fw, S, rng, '#6f4a2b')
            rgb[:hdr] = band; A[:hdr] = 255
            rgb[hdr - 1:hdr] *= 0.7
        else:
            rgb[:max(1, S // 2)] = base * 0.7; A[:max(1, S // 2)] = 255
        return np.dstack([rgb, A])
    if m.startswith('shag') or m == 'tassel':
        kind = {'shag_wolf': 'wolf', 'shag_bear': 'bear', 'shag_bison': 'bison', 'tassel': 'wolf'}[m]
        if m == 'tassel':
            base = MT.suede(fh, fw, S, rng, '#d1b88c')
        else:
            base = MT.fur(fh, fw, S, rng, kind)
        # irregular tufts: fully opaque root band, strands of random length below
        for x in range(fw):
            L = int(fh * (0.45 + 0.55 * rng.random() ** 0.7))
            A[:L, x] = 255
        A[:max(1, S // 2)] = 255
        if m == 'tassel':
            A[:] = 0
            for x in range(0, fw, max(2, S)):
                L = int(fh * (0.6 + 0.4 * rng.random()))
                A[:L, x:x + max(1, S // 2)] = 255
            A[:S, :] = 255
        rgb = base * np.linspace(1.05, 0.8, fh)[:, None, None]
        return np.dstack([np.clip(rgb, 0, 255), A])
    if m.startswith('jute'):
        pat = m[5:]
        pal = {'woodland': ['#5b4c34', '#4a4a2a', '#6a5a3c', '#3a3624', '#5d6236', '#47502c', '#71603f'],
               'grassland': ['#b8a274', '#a08a5e', '#c9b68a', '#8a7a52', '#d2c095'],
               'wetland': ['#6e6844', '#57573a', '#8a7a52', '#4a4a30', '#9a8a5e'],
               'snow': ['#e8ebed', '#d4d8dc', '#f4f5f6', '#b8bec4', '#c9ced2']}[pat]
        pal = [MT.col(c) for c in pal]
        # jute strands from the knot row at the top, plus a few cut leaves (woodland/wetland) or grass blades
        n = int(fw / max(1, S * 0.4))
        for i in range(n):
            x = rng.random() * fw
            L = fh * (0.5 + 0.5 * rng.random())
            drift = rng.normal(0, 0.12) * S
            c = pal[rng.integers(len(pal))] * (0.85 + 0.3 * rng.random())
            wdt = max(1, int(S * (0.25 + 0.25 * rng.random())))
            for yy in range(int(L)):
                xx = int(round(x + drift * yy / max(1, fh)))
                if 0 <= xx < fw:
                    rgb[yy, xx:xx + wdt] = c * (1 - 0.3 * yy / max(1, L)); A[yy, xx:xx + wdt] = 255
        if pat in ('woodland', 'wetland', 'snow'):
            for i in range(max(1, fw // (2 * S))):
                cx, cy = rng.random() * fw, fh * (0.25 + 0.6 * rng.random())
                Lf = S * (0.9 + 0.6 * rng.random()); Wf = Lf * (0.4 if pat != 'wetland' else 0.18)
                ang = math.pi / 2 + rng.normal(0, 0.5)
                ys, xs = np.mgrid[0:fh, 0:fw]
                a, u, v, t = MT.leaf_mask(xs + 0.5 - cx, ys + 0.5 - cy, ang, Lf, Wf, 2 if pat == 'woodland' else 0)
                c = pal[rng.integers(len(pal))] * (0.9 + 0.2 * rng.random())
                sel = a > 0.5
                rgb[sel] = c * (0.9 + 0.15 * (v[sel] > 0))[:, None]; A[sel] = 255
        A[:max(1, S // 2)] = 255
        rgb[:max(1, S // 2)] = pal[0] * 0.7
        rgb *= np.linspace(1.0, 0.82, fh)[:, None, None]
        return np.dstack([rgb, A])
    raise SystemExit(m)


def stitch_line(img, mask, color, dash=None):
    """Dashed stitch along a boolean mask, dash in texels along the dominant direction."""
    img[mask, :3] = img[mask, :3] * 0.45 + color * 0.55


def decorate(b, face, img, X, Y, Z):
    """Garment details in part-local model coordinates (texel-exact at S)."""
    fh, fw = img.shape[:2]
    if fh == 0 or fw == 0:
        return img
    rgb = img[..., :3]
    A = img[..., 3]
    px = 1.0 / S  # one texel in model px
    g = b.g
    x0, x1 = b.x - g, b.x + b.w + g
    y0, y1 = b.y - g, b.y + b.h + g
    z0, z1 = b.z - g, b.z + b.d + g
    dark = lambda m, k=0.6: rgb.__setitem__(m, rgb[m] * k)
    side = face in ('front', 'back', 'minx', 'maxx')
    for dco in b.deco:
        if dco == 'hood_open' and face == 'front':
            # open face: forehead band 1.9 px, cheek panels 0.85 px; the chin is open
            hole = (np.abs(X) < 3.3) & (Y > -6.35)
            r = np.hypot(np.maximum(np.abs(X) - 2.3, 0), np.maximum(-6.35 + 1.0 - Y, 0))
            hole &= r < 1.0
            edge = (~hole) & ((np.abs(X) < 3.3 + 0.45) & (Y > -6.35 - 0.45))
            rgb[edge] = rgb[edge] * 0.62
            A[hole] = 0
        elif dco == 'hood_open' and face == 'bottom':
            A[:] = 0
        elif dco == 'hood_seam' and face in ('top', 'back'):
            m = np.abs(X) < px * 0.9
            rgb[m] *= 0.7
        elif dco == 'binding' and side:
            rgb[:] *= 0.62
        elif dco == 'zip' and face == 'front':
            tape = np.abs(X) < 0.32
            teeth = (np.abs(X) < 0.12) & ((np.floor(Y * S) % 2) == 0)
            rgb[tape] = rgb[tape] * 0.42
            rgb[teeth] = MT.col('#1e1e1c')
        elif dco == 'zip_grey' and face == 'front':
            tape = np.abs(X) < 0.32
            rgb[tape] = MT.col('#3a3d40')
            teeth = (np.abs(X) < 0.12) & ((np.floor(Y * S) % 2) == 0)
            rgb[teeth] = MT.col('#8a8f94')
        elif dco == 'chest_zip' and face == 'front':
            m = (np.abs(X - 2.0) < 0.12) & (Y > 1.6) & (Y < 5.0)
            rgb[m] = MT.col('#8a8f94')
        elif dco == 'carbon_logo' and face in ('minx', 'maxx'):
            m = (np.abs(Z) < 0.8) & (np.abs(Y + 3.2) < 0.25)
            rgb[m] = rgb[m] * 0.6 + MT.col('#6c7076') * 0.4
        elif dco == 'yoke' and side:
            m = np.abs(Y - 2.9) < px * 0.6
            rgb[m] *= 0.62
            st = (np.abs(Y - 3.25) < px * 0.5) & ((np.floor((X + Z) * S) % 3) == 0)
            rgb[st] *= 0.75
        elif dco == 'waist' and side:
            m = (Y > 7.4) & (Y < 8.4)
            rgb[m] *= 0.82
            rib = m & ((np.floor(Y * S) % 2) == 0)
            rgb[rib] *= 0.9
        elif dco == 'side_seam' and face in ('minx', 'maxx'):
            m = np.abs(Z) < px * 0.6
            rgb[m] *= 0.68
        elif dco == 'side_seam_leg' and face == 'minx':
            m = np.abs(Z) < px * 0.6
            rgb[m] *= 0.68
        elif dco == 'handwarmer' and face == 'front':
            for sx in (-1, 1):
                m = (np.abs((X - sx * 2.3) - sx * 0.45 * (Y - 9.0)) < px * 0.7) & (Y > 8.6) & (Y < 10.6)
                rgb[m] *= 0.45
        elif dco == 'pocket' and face == 'front':
            flap = Y < y0 + 1.05
            rgb[flap] *= 0.86
            edge = np.abs(Y - (y0 + 1.05)) < px * 0.7
            rgb[edge] *= 0.5
            border = (np.minimum(np.minimum(X - x0, x1 - X), np.minimum(Y - y0, y1 - Y)) < px * 1.2)
            rgb[border] *= 0.72
            st = (np.abs(np.minimum(X - x0, x1 - X) - px * 2) < px * 0.5) & (np.floor(Y * S) % 3 == 0)
            rgb[st] *= 0.7
        elif dco == 'pocket' and face in ('top', 'minx', 'maxx', 'bottom'):
            rgb[:] *= 0.75
        elif dco == 'pocket_side' and face == 'minx':
            flap = Y < y0 + 1.1
            rgb[flap] *= 0.85
            rgb[np.abs(Y - (y0 + 1.1)) < px * 0.7] *= 0.5
            border = np.minimum(np.minimum(Z - z0, z1 - Z), np.minimum(Y - y0, y1 - Y)) < px * 1.2
            rgb[border] *= 0.72
        elif dco == 'pocket_side' and face != 'minx':
            rgb[:] *= 0.75
        elif dco == 'kneepad' and side:
            rgb[:] *= 0.8
            q = ((np.floor((X - Y) * S / 3) % 2) == 0) & (face == 'front')
            rgb[q] *= 0.93
            border = np.minimum(np.minimum(X - x0, x1 - X), np.minimum(Y - y0, y1 - Y)) < px * 1.1
            rgb[border & (face == 'front')] *= 0.75
        elif dco == 'knee' and side:
            m = (np.abs(Y - 4.2) < px * 0.6) | (np.abs(Y - 7.6) < px * 0.6)
            rgb[m] *= 0.7
        elif dco == 'shoulder' and side:
            m = np.abs(Y - 0.0) < px * 0.6
            rgb[m] *= 0.68
        elif dco == 'elbow' and side:
            m = np.abs(Y - 4.6) < px * 0.6
            rgb[m] *= 0.72
        elif dco == 'rib' and side:
            r = (np.floor(Y * S) % 2) == 0
            rgb[r] *= 0.86
        elif dco == 'darken':
            rgb[:] *= 0.72
        elif dco == 'velcro' and side:
            rgb[:] *= 0.85
        elif dco == 'waistband' and side:
            loop = ((np.abs(X - 2.6) < 0.3) | (np.abs(X + 2.6) < 0.3) | (np.abs(Z) < 0.3)) & (Y < y0 + 1.3)
            rgb[loop] *= 0.7
            top = Y < y0 + 1.3
            rgb[top] *= 0.9
            rgb[np.abs(Y - (y0 + 1.3)) < px * 0.6] *= 0.65
        elif dco == 'fly' and face == 'front':
            m = (np.abs(X - 0.5) < px * 0.6) & (Y > y0 + 1.3)
            rgb[m] *= 0.6
        elif dco == 'netting' and side:
            # knotted net over the shell (2 px diamond mesh)
            u = (X + Y + Z * 0.7) * 0.5; v = (X - Y + Z * 0.7) * 0.5
            net = (np.abs(u - np.round(u)) < px * 0.7) | (np.abs(v - np.round(v)) < px * 0.7)
            rgb[net] = rgb[net] * 0.55 + MT.col('#3b3426') * 0.45 * (b.mat != 'mesh_snow') + MT.col('#c8ccd0') * 0.45 * (b.mat == 'mesh_snow')
        elif dco == 'lacing_front' and face == 'front':
            m = np.abs(X) < px * 0.7
            rgb[m] *= 0.5
            cr = (np.abs(np.abs(X) - (0.5 - np.abs(((Y * 1.0) % 1.4) - 0.7) * 0.7)) < px * 0.8) & (np.abs(X) < 0.6)
            rgb[cr] = MT.col('#3b3426')
        # ---------------------------------------------------------------- hide garments
        elif dco == 'placket' and face == 'front':
            m = np.abs(X - 0.3) < 0.45
            rgb[m] *= 0.86
            rgb[np.abs(X - 0.75) < px * 0.6] *= 0.6
            for yb in (1.6, 4.2, 6.8):
                bm = (np.hypot(X - 0.3, Y - yb) < 0.42)
                rgb[bm] = MT.col('#e2d8c2')
                rgb[(np.hypot(X - 0.3, Y - yb) < 0.42) & (np.hypot(X - 0.3, Y - yb) > 0.28)] = MT.col('#b2a68e')
        elif dco == 'edge_stitch' and side:
            d = np.minimum(Y - y0, y1 - Y)
            if face in ('front', 'back'):
                d = np.minimum(d, np.minimum(X - x0, x1 - X))
            m = (np.abs(d - 0.45) < px * 0.55) & ((np.floor((X + Y + Z) * S) % 3) != 0)
            rgb[m] *= 0.7
        elif dco == 'yoke_edge' and side:
            m = Y > y1 - 0.5
            rgb[m] *= 0.8
            st = (np.abs(Y - (y1 - 0.75)) < px * 0.5) & ((np.floor((X + Z) * S) % 3) != 0)
            rgb[st] *= 0.65
        elif dco == 'buckle_strap' and face == 'front':
            pass
        elif dco == 'buckle' and face == 'front':
            fd = np.minimum(np.minimum(X - x0, x1 - X), np.minimum(Y - y0, y1 - Y))
            frame = fd < 0.38
            rgb[~frame] = MT.col('#3a2416')
            rgb[frame] = MT.col('#8c8780') * (0.8 + 0.4 * (fd[frame] / 0.38))[:, None]
            tongue = (~frame) & (np.abs(X - (x0 + x1) / 2) < 0.12)
            rgb[tongue] = MT.col('#b5b0a8')
        elif dco == 'beadwork' and side:
            # lane-stitched seed beads: bands of white with red / blue / black stepped triangles
            Wb = max(px, 0.5)
            bi = np.floor((X + Z * 1.0 + 20) / Wb).astype(int)
            bj = np.floor((Y - y0) / Wb).astype(int)
            rows = max(1, int(round((y1 - y0) / Wb)))
            white, red, blue, black, yel = (MT.col(c) for c in ('#ece4d0', '#b3302a', '#2d5f86', '#26221e', '#d4a23a'))
            cell = np.zeros(X.shape + (3,)); cell[:] = white
            tri = (np.abs((bi % 8) - 4) + (bj % max(2, rows))) < 4
            cell[tri] = red
            cell[tri & ((bi % 8) == 4)] = blue
            cell[(bj == 0) | (bj == rows - 1)] = blue
            rgb[:] = cell * (0.88 + 0.12 * ((np.floor(Y * S) + np.floor((X + Z) * S)) % 2 == 0))[..., None]
        elif dco == 'fringe_shadow' and face in ('front', 'back'):
            m = (Y > 4.3) & (Y < 7.0)
            stripes = (np.floor(X * S) % 3) != 0
            k = np.where(stripes, 0.62, 0.78)
            rgb[m] = rgb[m] * k[m][:, None]
        elif dco == 'wear' and side:
            n = MT.fbm_t(max(fh, fw), 3 * S, 5, 3)[:fh, :fw]
            rgb[:] *= (0.9 + 0.2 * n)[..., None]
        elif dco == 'seam_outer' and face == 'minx':
            m = np.abs(Z) < px * 0.6
            rgb[m] *= 0.62
        elif dco == 'hem_side' and side:
            m = Y > y1 - 0.35
            rgb[m] *= 0.75
        elif dco == 'lacing' and face == 'front':
            for xs in (-0.5, 0.5):
                m = (np.abs(X - xs) < px * 0.6)
                rgb[m] *= 0.6
            cr = (np.abs(np.abs(X) - np.abs(((Y - y0) % 0.9) - 0.45)) < px * 0.7) & (np.abs(X) < 0.5)
            rgb[cr] = MT.col('#e0cfa8')
        elif dco == 'toggles' and face == 'front':
            m = np.abs(X - 0.0) < px * 0.6
            rgb[m] *= 0.35
            for yb in (1.6, 4.4, 7.2):
                tg = (np.abs(X) < 0.7) & (np.abs(Y - yb) < 0.22)
                rgb[tg] = MT.col('#d9cfb8') * (0.85 + 0.15 * (Y[tg] < yb))[:, None]
                lp = (np.abs(X - 0.75) < 0.12) & (np.abs(Y - yb) < 0.3)
                rgb[lp] = MT.col('#3a2a1c')
        elif dco == 'center_part' and face == 'back':
            m = np.abs(X) < px * 0.6
            rgb[m] *= 0.7
        elif dco == 'ruff' and side:
            m = Y > y1 - 0.4
            rgb[m] *= 0.85
        elif dco == 'cuff_fur' and side:
            rgb[:] *= np.linspace(1.05, 0.88, fh)[:, None, None]
        elif dco == 'robe_wrap' and face == 'front':
            # the robe crosses right over left; fur lining shows along the overlap edge
            edge = X - (0.9 - 0.12 * Y)
            fur = (edge > 0) & (edge < 0.55)
            fz = MT.fur(fh, fw, S, rng_for(b, 'wrapfur'), 'bison')
            rgb[fur] = fz[fur]
            rgb[np.abs(edge - 0.55) < px * 0.6] *= 0.6
        elif dco in ('painted_band', 'painted_cape', 'painted_hem') and side:
            yb0, yb1 = {'painted_band': (5.8, 8.6), 'painted_cape': (1.4, 3.6), 'painted_hem': (3.8, 6.0)}[dco]
            band = (Y > yb0) & (Y < yb1)
            if dco == 'painted_band' and face != 'back':
                band &= False
            if band.any():
                ochre, red, black, blue = (MT.col(c) for c in ('#c08a2c', '#8c2f22', '#2a201a', '#3f5f72'))
                t = (Y - yb0) / (yb1 - yb0)
                ux = (X + Z) if face in ('front', 'back') else (Z + X)
                ph = (ux * 0.9) % 2.0
                tri = np.abs(ph - 1.0) < (1 - t)
                paint = np.where(tri[..., None], red, ochre)
                paint = np.where(((np.abs(t - 0.0) < 0.1) | (np.abs(t - 1.0) < 0.1))[..., None], black, paint)
                dots = (np.abs(ph - 1.0) < 0.12) & (np.abs(t - 0.5) < 0.12)
                paint = np.where(dots[..., None], blue, paint)
                wear = MT.fbm_t(max(fh, fw), 2 * S, 99, 2)[:fh, :fw]
                k = (0.72 + 0.28 * wear)[..., None]
                rgb[band] = (rgb * (1 - k) + paint * k)[band]
        elif dco == 'cape_edge' and side:
            m = Y > y1 - 0.85
            fz = MT.fur(fh, fw, S, rng_for(b, 'capefur'), 'bison')
            rgb[m] = fz[m]
        elif dco == 'robe_leg' and side:
            m = Y > y1 - 0.8
            fz = MT.fur(fh, fw, S, rng_for(b, 'legfur'), 'bison')
            rgb[m] = fz[m]
        elif dco == 'sash' and side:
            # finger-woven trade-wool sash: red ground, blue/yellow chevrons ('arrow' pattern)
            red, blue, yel, wh = (MT.col(c) for c in ('#8e2a22', '#2f4f7a', '#d0a43a', '#e2dccb'))
            u = (X + Z) * 1.6
            v = (Y - y0) / max(1e-3, (y1 - y0))
            ch = np.abs(((u % 2.0) - 1.0)) - np.abs(v - 0.5)
            cl = np.where((ch > 0.35)[..., None], blue, np.where((ch > 0.2)[..., None], yel, red))
            cl = np.where((np.abs(ch - 0.28) < 0.04)[..., None], wh, cl)
            rgb[:] = cl * (0.9 + 0.12 * ((np.floor(Y * S) % 2) == 0))[..., None]
        elif dco == 'wraps' and side:
            for yw in (7.4, 9.6):
                m = np.abs(Y - yw - 0.25 * np.sin((X + Z) * 1.4)) < 0.18
                rgb[m] = MT.col('#d6c39a') * 0.9
        elif dco == 'sole_band' and side:
            m = Y > y1 - 0.75
            rgb[m] = rgb[m] * 0.45
            rgb[np.abs(Y - (y1 - 0.75)) < px * 0.6] = MT.col('#c9b48e') * 0.7
        elif dco == 'sole_band' and face == 'bottom':
            rgb[:] *= 0.4
        elif dco == 'toe' and side:
            m = Y > y1 - 0.6
            rgb[m] *= 0.5
            puck = (face == 'front') & (np.abs(X) < 1.2) & (np.abs(Y - (y0 + 0.9)) < px * 0.6)
            rgb[puck] = MT.col('#d6c39a')
        elif dco == 'toe' and face == 'top':
            m = (np.abs(X) < 1.0) & (np.abs(Z + 2.4) < 0.25)
            rgb[m] = MT.col('#b3302a')
        elif dco == 'mitten' and side:
            thumb = (face == 'front') & (X > 0.0) & (Y > 9.0)
            rgb[thumb] *= 0.85
            rgb[(face == 'front') & (np.abs(X - 0.0) < px * 0.6) & (Y > 9.0)] *= 0.6
        elif dco == 'mitten' and face == 'bottom':
            rgb[:] *= 0.8
        elif dco == 'brim' and side:
            rgb[:] *= 1.04
        elif dco == 'fur_top' and face == 'top':
            pass
        elif dco == 'flap' and side:
            m = Y > y1 - 0.35
            rgb[m] *= 0.8
        # ---------------------------------------------------------------- harness
        elif dco == 'strap' and side:
            edge = np.minimum(X - x0, x1 - X) < px * 1.0
            rgb[edge] *= 0.75
        elif dco == 'ladder' and face == 'front':
            for yb in (5.2, 6.4, 7.6):
                m = (np.abs(Y - yb) < 0.18) & (np.abs(X - (x0 + x1) / 2) < 0.7)
                rgb[m] = MT.col('#1e1f1e')
        elif dco == 'sternum' and side:
            rgb[:] *= 0.9
        elif dco == 'hipbelt' and side:
            pad = (Y > y0 + 0.3) & (Y < y1 - 0.3)
            rgb[pad & ((np.floor((X + Z) * S / 2) % 2) == 0)] *= 0.93
            rgb[np.abs(Y - (y0 + 0.3)) < px * 0.6] *= 0.7
            rgb[np.abs(Y - (y1 - 0.3)) < px * 0.6] *= 0.7
    img[..., :3] = np.clip(rgb, 0, 255)
    img[..., 3] = A
    return img


def shade(b, face, img):
    """Subtle baked occlusion so separate layers read in Minecraft's flat light."""
    fh, fw = img.shape[:2]
    if fh < 3 or fw < 3 or b.mat.startswith(('fringe', 'shag', 'jute', 'tassel')):
        return img
    rgb = img[..., :3]
    if face == 'bottom':
        rgb *= 0.72
    elif face == 'top':
        rgb *= 1.05
    yy = np.minimum(np.arange(fh), np.arange(fh)[::-1])[:, None]
    xx = np.minimum(np.arange(fw), np.arange(fw)[::-1])[None, :]
    e = np.minimum(np.minimum(yy, xx) / (0.9 * S), 1.0)
    rgb *= (0.93 + 0.07 * e)[..., None]
    img[..., :3] = np.clip(rgb, 0, 255)
    return img


def paint_group(boxes, uvw, uvh):
    img = np.zeros((uvh * S, uvw * S, 4))
    done = set()
    for b in boxes:
        if b.uvref is not b:
            continue
        for face, (fu, fv, fw_, fh_) in face_rects(b).items():
            px0, py0 = int(round(fu * S)), int(round(fv * S))
            pw, ph = int(round(fw_ * S)), int(round(fh_ * S))
            if pw <= 0 or ph <= 0:
                continue
            X, Y, Z = face_points(b, face, pw, ph)
            m = base_material(b, face, pw, ph, X, Y, Z)
            m = decorate(b, face, m, X, Y, Z)
            m = shade(b, face, m)
            img[py0:py0 + ph, px0:px0 + pw] = m
    return img


def vanilla_texture(hd):
    """1 texel per model pixel, alpha-aware box filter + gentle palette reduction (Minecraft look)."""
    h, w = hd.shape[:2]
    a = hd[..., 3:4] / 255.0
    pre = hd[..., :3] * a
    def red(x):
        return x.reshape(h // S, S, w // S, S, -1).mean(axis=(1, 3))
    A = red(a)
    rgb = red(pre) / np.maximum(A, 1e-6)
    alpha = (A[..., 0] > 0.42) * 255.0
    lum = rgb.mean(axis=2, keepdims=True)
    rgb = lum + (rgb - lum) * 1.15
    img = Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8), 'RGBA')
    q = img.convert('RGB').quantize(colors=48, method=Image.MEDIANCUT, dither=Image.NONE).convert('RGB')
    out = np.dstack([np.asarray(q), alpha.astype(np.uint8)])
    return Image.fromarray(out, 'RGBA')


# ------------------------------------------------------------------------------------------------ Java
def java(O, groups_uv):
    L = []
    L.append('package com.formaworks.frontierhunts.outfitter.client;\n')
    L.append('/**\n * [outfitter] GENERATED by tools/outfitter/outfit.py - do not edit by hand. Worn-gear box tables:\n'
             ' * {part, u, v, x, y, z, w, h, d, grow, px, py, pz, rx, ry, rz, flags}; parts 0 head, 1 body, 2 right arm (wide skins),\n'
             ' * 3 right leg, 4 right arm (slim skins), 5 left arm (wide), 6 left leg, 7 left arm (slim). A non-zero pose (px..rz)\n'
             ' * makes the box its own child part (a rigid card at an angle). Flags: 1 = hidden while a long coat covers the thighs,\n'
             ' * 2 = mirrored UV (left limbs), 4 = hidden inside a boot, 8 = hidden under sewn-on mittens, 16 = hidden under the\n'
             ' * knee-length hide robe.\n */')
    L.append('final class OutfitGeometry {\n   private OutfitGeometry() {\n   }\n')
    L.append('   /** {uv width, uv height} of the texture each outfit uses. */')
    L.append('   static int[] uv(String outfit) {\n      return switch (outfit) {')
    for k, (boxes, grp) in O.items():
        L.append('         case "%s" -> new int[]{%d, %d};' % (k, groups_uv[grp][0], groups_uv[grp][1]))
    L.append('         default -> new int[]{64, 64};\n      };\n   }\n')
    L.append('   /** Texture group (file name under textures/entity/outfitter/). */')
    L.append('   static String texture(String outfit) {\n      return switch (outfit) {')
    for k, (boxes, grp) in O.items():
        L.append('         case "%s" -> "%s";' % (k, grp))
    L.append('         default -> null;\n      };\n   }\n')
    L.append('   /** Depth (px) a chest outfit stands off the back of the body box. */')
    L.append('   static float depth(String outfit) {\n      return switch (outfit) {')
    for k, (boxes, grp) in O.items():
        if k.startswith(('garment/', 'coverall/', 'carbon/jacket', 'ghillie/')) and k.split('/')[-1] not in ('fur_hat', 'buckskin_leggings', 'fur_mukluks'):
            if 'hood' in k or 'trousers' in k:
                continue
            L.append('         case "%s" -> %sF;' % (k, chest_depth(boxes)))
    L.append('         default -> 0.0F;\n      };\n   }\n')
    L.append('   private static final java.util.Map<String, float[][]> CACHE = new java.util.HashMap<>();\n')
    L.append('   static float[][] boxes(String outfit) {\n      return CACHE.computeIfAbsent(outfit, k -> parse(rows(k)));\n   }\n')
    L.append('   private static float[][] parse(String s) {\n      if (s == null || s.isEmpty()) {\n         return new float[0][];\n      }\n'
             '      String[] rows = s.split(";");\n      float[][] out = new float[rows.length][];\n      for (int i = 0; i < rows.length; i++) {\n'
             '         String[] v = rows[i].trim().split(" ");\n         out[i] = new float[v.length];\n         for (int j = 0; j < v.length; j++) {\n'
             '            out[i][j] = Float.parseFloat(v[j]);\n         }\n      }\n      return out;\n   }\n')
    L.append('   private static String rows(String outfit) {\n      return switch (outfit) {')
    for k, (boxes, grp) in O.items():
        rows = []
        for b in boxes:
            pose = b.pose or (0, 0, 0, 0, 0, 0)
            x = b.x
            if b.left:
                x = -(b.x + b.w)
                pose = (-pose[0], pose[1], pose[2], pose[3], -pose[4], -pose[5])
            t = b.tag or ''
            # [clothing] 1 under a long coat's hem, 2 mirrored UV, 4 inside a boot, 8 under sewn-on mittens, 16 under the knee-length robe
            flag = (1 if 'hem' in t else 0) | (2 if b.left else 0) | (4 if 'boot' in t else 0) | (8 if 'mitten' in t else 0) | (16 if 'long' in t else 0)
            vals = [PART_CODE[b.part], b.uv[0], b.uv[1], x, b.y, b.z, b.w, b.h, b.d, b.g] + list(pose) + [flag]
            rows.append(' '.join(('%d' % v) if isinstance(v, int) else ('%.5g' % v) for v in vals))
        body = ';'.join(rows)
        chunks = [body[i:i + 3000] for i in range(0, len(body), 3000)]
        L.append('         case "%s" -> %s;' % (k, '\n            + '.join('"%s"' % c for c in chunks)))
    L.append('         default -> "";\n      };\n   }\n}\n')
    return '\n'.join(L)


def build(only=None):
    O = all_outfits()
    groups = {}
    for k, (boxes, grp) in O.items():
        groups.setdefault(grp, []).append(boxes)
    groups_uv = {}
    for grp, lists in groups.items():
        for size in ((64, 64), (128, 64), (128, 128), (256, 128), (256, 256)):
            if pack(lists, *size):
                groups_uv[grp] = size
                break
        else:
            raise SystemExit('UV overflow ' + grp)
    return O, groups, groups_uv


def main():
    only = None
    if '--only' in sys.argv:
        only = sys.argv[sys.argv.index('--only') + 1].split(',')
    O, groups, groups_uv = build()
    out_tex = os.path.join(R, 'patch/assets/frontierhunts/textures/entity/outfitter')
    for grp, lists in groups.items():
        if only and not any(grp.startswith(o) for o in only):
            continue
        uvw, uvh = groups_uv[grp]
        boxes = [b for bs in lists for b in bs]
        hd = paint_group(boxes, uvw, uvh)
        p = os.path.join(out_tex, grp)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        Image.fromarray(hd.astype(np.uint8), 'RGBA').save(p + '_hd.png', optimize=True)
        vanilla_texture(hd).save(p + '.png', optimize=True)
        print('tex', grp, groups_uv[grp])
    jp = os.path.join(R, 'src/com/formaworks/frontierhunts/outfitter/client/OutfitGeometry.java')
    os.makedirs(os.path.dirname(jp), exist_ok=True)
    open(jp, 'w').write(java(O, groups_uv))
    json.dump({k: {'texture': g, 'uv': groups_uv[g], 'depth': chest_depth(b)} for k, (b, g) in O.items()},
              open(os.path.join(os.path.dirname(__file__), 'outfits.json'), 'w'), indent=1)
    print('java ok', len(O))


if __name__ == '__main__':
    main()
