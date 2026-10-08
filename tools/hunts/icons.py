#!/usr/bin/env python3
"""[hunts] Species-hunt icon atlas: original 32x32 pixel art drawn with the journal's px engine (tools/ledger/px.py:
hard-edged shapes at pixel centres, banded upper-left light, rim light / core shadow, dark separators, 1 px warm-dark
outline, the journal's limited warm palette).

python3 tools/hunts/icons.py <repo> [sheet.png]
Writes patch/assets/frontierhunts/textures/gui/journal/hunts_icons.png (256x128: 8 x 2 cells of normal icons on top,
the same icons brightened for hover below) and rewrites the NAMES array in hunts/client/HuntIcons.java.
"""
import math
import os
import re
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'ledger'))
from px import *  # noqa: F401,F403

ICONS = {}


def icon(name):
    def deco(f):
        ICONS[name] = f
        return f
    return deco


CAMO = ramp('#232a1a', '#38432a', '#55603c', '#7a8152', '#a3a571')
NIGHT = ramp('#141a2a', '#1f2a44', '#2f3f63', '#4a5f8a', '#7d93b8')
MOON = ramp('#6b5f3a', '#a99a62', '#d9cb92', '#efe4b8', '#fdf8e2')
CORN = ramp('#6a4a10', '#a0761c', '#d4a62e', '#efcc5a', '#fbe79a')
DUCKG = ramp('#0f2a22', '#18443a', '#246452', '#3a8a6a', '#6cb48e')
CHESTNUT = ramp('#3a1a10', '#5e2a18', '#874022', '#a95c34', '#c98050')
HOUND = ramp('#3a1a0e', '#62301a', '#8c4826', '#b0663a', '#d08c5a')
GROUSE = ramp('#2e2216', '#4d3a26', '#6e5636', '#93774e', '#b99c6e')
CORD = ramp('#1a3622', '#28523a', '#3a744e', '#5a9a68', '#88c08e')


# ------------------------------------------------------------------------------------------------ trail camera
@icon('camera')
def camera():
    I = Icon()
    # tree trunk behind
    I.add(rect(3, 0, 11, 32), LOG, shade=0.8, light=(-1, 0))
    for y in (5, 13, 22):
        I.line(rect(4, y, 10, y + 1), LOG, 1)
    # strap around the trunk
    I.add(rect(2, 14, 12, 17), OLIVE, shade=0.5, edge=True)
    # camera housing
    I.add(poly([(9, 7), (27, 7), (28, 9), (28, 25), (27, 27), (9, 27), (8, 25), (8, 9)]), CAMO, shade=0.8, edge=True)
    # camo blotches
    for x, y, rx, ry in ((13, 11, 2.2, 1.4), (24, 21, 2.0, 1.6), (12, 23, 1.8, 1.2), (24, 12, 1.4, 1.0)):
        I.add(ellipse(x, y, rx, ry) & poly([(9, 7), (27, 7), (28, 9), (28, 25), (27, 27), (9, 27), (8, 25), (8, 9)]), OLIVE, flat=1, rim=False, core=False)
    # IR flash panel (dark with dots)
    I.add(rect(11.5, 9.5, 24.5, 13.5), INK, flat=1, edge=True)
    for x in range(13, 24, 2):
        I.px(x, 11, (120, 40, 40))
    # lens
    I.add(circle(18, 19.5, 4.3), STEEL, shade=0.9, edge=True)
    I.add(circle(18, 19.5, 2.6), INK, flat=0)
    I.px(17, 18, (180, 205, 220))
    I.px(16, 18, (110, 140, 160))
    # little red LED
    I.px(25, 24, (220, 60, 50))
    return I


# ------------------------------------------------------------------------------------------------ call (with sound)
@icon('call')
def call():
    I = Icon()
    # wooden grunt tube on a diagonal: black mouthpiece lower left, brass band, flared bell upper right
    I.add(taper([(6, 26), (18.5, 13.5)], 3.6, 4.4), WOOD, shade=0.9, edge=True)
    I.add(taper([(18, 14), (22.5, 9.5)], 5.0, 8.2), WOOD, shade=0.9, edge=True)
    I.add(seg(17, 15, 19, 13, 5.2), BRASS, shade=0.8, edge=True)
    I.add(seg(4.6, 27.4, 7.6, 24.4, 4.0), INK, shade=0.6, edge=True)
    I.add(ellipse(23.3, 8.7, 2.6, 3.4, 45), INK, flat=1, rim=False, core=False)
    # sound waves leaving the bell
    for r in (5.2, 8.2):
        arc = ring(23.3, 8.7, r, r + 1.2) & ~circle(23.3, 8.7, 4.4)
        cone = poly([(23.3, 8.7), (32, 2.5), (32, 14), (27.5, 17)])
        I.add(arc & cone, PAPER, flat=3, rim=False, core=False)
    return I


# ------------------------------------------------------------------------------------------------ master medal
@icon('medal')
def medal():
    I = Icon()
    # ribbon tails (green and red, the hunt colours)
    I.add(poly([(9.5, 1.5), (15, 1.5), (17, 15), (12, 15)]), GREEN, shade=0.7, edge=True)
    I.add(poly([(17, 1.5), (22.5, 1.5), (20, 15), (15, 15)]), RED, shade=0.7, edge=True)
    # medal disc with a raised rim
    I.add(circle(16, 21, 9.4), GOLD, shade=0.9, edge=True)
    I.add(ring(16, 21, 7.0, 7.9), GOLD, flat=1, rim=False, core=False)
    # embossed track: heel pad and four toes, each lit on its upper left
    parts = [ellipse(16, 23.4, 3.1, 2.4)] + [ellipse(x, y, 1.25, 1.55) for x, y in ((12.5, 19.6), (14.9, 17.5), (17.1, 17.5), (19.5, 19.6))]
    for m in parts:
        I.add(m, GOLD, flat=0, rim=False, core=False)
    for x, y in ((15, 22), (12, 19), (14, 17), (17, 17), (19, 19)):
        I.px(x, y, GOLD[2])
    I.px(11, 15, GOLD[4])
    I.px(12, 14, GOLD[4])
    I.px(13, 14, GOLD[4])
    return I


# ------------------------------------------------------------------------------------------------ ground blind
@icon('blind')
def blind():
    I = Icon()
    body = poly([(16, 4), (29, 26), (3, 26)])
    I.add(body, CAMO, shade=0.8, edge=True)
    for x, y, rx, ry, a in ((12, 17, 2.4, 1.3, -20), (20, 20, 2.6, 1.4, 15), (16, 11, 1.6, 1.0, 0), (24, 24, 1.8, 1.0, -10), (8, 23, 1.8, 1.0, 10)):
        I.add(ellipse(x, y, rx, ry, a) & body, OLIVE, flat=1, rim=False, core=False)
    # shooting window
    I.add(poly([(11, 16.5), (21, 16.5), (22, 19.5), (10, 19.5)]), INK, flat=0, edge=True)
    # door seam and hub poles
    I.line(path([(16, 20), (16, 26)], 1.0), CAMO, 0)
    I.line(path([(16, 4), (16, 6)], 1.2), LOG, 2)
    # grass at the base
    for x in (3.5, 6, 9, 23, 26, 28.5):
        I.add(taper([(x, 28.5), (x + 0.8, 23.5)], 1.6, 0.6), GREEN, flat=3, rim=False, core=False)
    I.add(rect(2, 26, 30, 29), GREEN, shade=0.5)
    return I


# ------------------------------------------------------------------------------------------------ game scale
@icon('scale')
def scale():
    I = Icon()
    # top ring and chain
    I.add(ring(16, 3.5, 1.4, 2.6), STEEL, flat=2, rim=False, core=False)
    I.line(path([(16, 6), (16, 9)], 1.4), STEEL, 3)
    # dial body
    I.add(circle(16, 16, 7.4), BRASS, shade=0.9, edge=True)
    I.add(circle(16, 16, 5.4), PAPER, shade=0.4, rim=False)
    for k in range(9):
        a = math.radians(150 + k * 30)
        I.px(int(round(16 + math.cos(a) * 4.4)), int(round(16 + math.sin(a) * 4.4)), INK[2])
    # needle
    I.line(path([(16, 16), (19.6, 12.6)], 1.1), RED, 2)
    I.px(16, 16, INK[1])
    # hook
    I.line(path([(16, 23.5), (16, 26)], 1.4), STEEL, 3)
    I.add(ring(14.6, 27.4, 1.3, 2.5) & rect(0, 26.5, 32, 32), STEEL, flat=2, rim=False, core=False)
    I.add(seg(13.4, 26.8, 12.6, 25.6, 1.3), STEEL, flat=3)
    return I


# ------------------------------------------------------------------------------------------------ night
@icon('moon')
def moon():
    I = Icon()
    I.add(circle(16, 16, 13), NIGHT, shade=0.7)
    crescent = circle(15, 15, 8.5) & ~circle(19.5, 12.5, 7.6)
    I.add(crescent, MOON, shade=0.9, edge=True)
    for x, y in ((22, 8), (25, 17), (19, 24), (8, 7)):
        I.px(x, y, MOON[4])
    I.add(star(22.5, 21, 0.9, 2.4, n=4, rot=0), MOON, flat=4, rim=False, core=False)
    # dark treeline along the bottom of the night sky
    tl = poly([(3, 25), (6, 20), (8, 23), (11, 18), (14, 23), (17, 21), (20, 24), (23, 19), (26, 23), (29, 22), (29, 30), (3, 30)]) & circle(16, 16, 13)
    I.add(tl, GREEN, flat=0, rim=False, core=False)
    return I


# ------------------------------------------------------------------------------------------------ bait
@icon('bait')
def bait():
    I = Icon()
    # burlap sack slumped behind a pile of shelled corn
    sack = poly([(8, 22), (7, 13), (10, 8), (14, 6), (13, 4), (17, 3.5), (18, 6), (22, 8), (25, 13), (24, 22)])
    I.add(sack, CANVAS, shade=0.9, edge=True)
    I.line(path([(12.5, 6.4), (19, 6.4)], 1.2), LEATHER, 1)
    for y in (12, 16, 20):
        I.line(path([(9, y), (23, y + 0.4)], 0.8), CANVAS, 1)
    I.add(ellipse(16, 14, 3.4, 2.6), CANVAS, flat=1, rim=False, core=False)
    pile = ellipse(16, 25, 13.5, 5.2)
    I.add(pile, CORN, shade=0.9, edge=True)
    for x, y in ((8, 24), (11, 22), (14, 25), (17, 22), (20, 26), (23, 24), (10, 27), (16, 28), (22, 21), (13, 21), (25, 26), (6, 26)):
        I.px(x, y, CORN[4])
        I.px(x + 1, y, CORN[1])
    return I


# ------------------------------------------------------------------------------------------------ snow camo
@icon('camo')
def camo():
    I = Icon()
    body = poly([(9, 7), (12, 5), (20, 5), (23, 7), (27, 11), (28, 22), (25, 22), (24, 14), (24, 29), (8, 29), (8, 14), (7, 22), (4, 22), (5, 11)])
    I.add(body, SNOW, shade=0.8, edge=True)
    for x, y, rx, ry, a in ((13, 12, 2.6, 1.2, 20), (19, 17, 2.4, 1.3, -25), (12, 22, 2.0, 1.0, 10), (20, 25, 2.6, 1.2, 0), (6.5, 16, 1.2, 2.0, 0),
                            (25.5, 15, 1.2, 1.8, 0), (17, 9, 1.6, 0.9, 0)):
        I.add(ellipse(x, y, rx, ry, a) & body, SLATE, flat=3, rim=False, core=False)
    # hood opening and zip
    I.add(ellipse(16, 6.5, 3.2, 1.6), INK, flat=1, rim=False, core=False)
    I.line(path([(16, 8), (16, 28)], 1.0), STONE, 2)
    I.add(star(26, 5, 1.0, 2.6, n=6, rot=0), ICE, flat=3, rim=False, core=False)
    return I


# ------------------------------------------------------------------------------------------------ predator call / howl
@icon('howl')
def howl():
    I = Icon()
    # coyote face, head-on: tall ears, narrow muzzle, pale cheeks, amber eyes - the predator that answers the call
    I.add(poly([(6, 13), (7, 2), (13, 9)]), SAND, shade=0.8, edge=True)
    I.add(poly([(26, 13), (25, 2), (19, 9)]), SAND, shade=0.8, edge=True)
    I.add(poly([(7.6, 11), (8.2, 4.5), (11.8, 9)]), MUD, flat=1, rim=False, core=False)
    I.add(poly([(24.4, 11), (23.8, 4.5), (20.2, 9)]), MUD, flat=1, rim=False, core=False)
    head = poly([(5, 14), (9, 8), (16, 7), (23, 8), (27, 14), (25, 20), (20, 23), (19, 29), (13, 29), (12, 23), (7, 20)])
    I.add(head, SAND, shade=0.9, edge=True)
    I.add(poly([(7, 18), (11, 15), (14, 21), (13, 29), (12, 23), (7, 20)]) & head, BONE, flat=3, rim=False, core=False)
    I.add(poly([(25, 18), (21, 15), (18, 21), (19, 29), (20, 23), (25, 20)]) & head, BONE, flat=3, rim=False, core=False)
    I.add(poly([(13.5, 12), (18.5, 12), (19, 26), (13, 26)]) & head, SAND, flat=1, rim=False, core=False)
    # eyes and nose
    for x in (11, 20):
        I.px(x, 13, (220, 170, 50))
        I.px(x + 1, 13, INK[0])
    I.add(ellipse(16, 27.3, 2.2, 1.5), INK, flat=0)
    I.px(15, 27, INK[3])
    return I


# ------------------------------------------------------------------------------------------------ prime pelt
@icon('pelt')
def pelt():
    I = Icon()
    # willow stretcher hoop
    I.add(ring(16, 16, 12.2, 13.8), WOOD, shade=0.8)
    # pelt stretched in the hoop: head up, legs out
    p = poly([(16, 4), (19, 6), (21, 10), (26, 11), (22, 14), (23, 22), (27, 25), (21, 25), (19, 29), (16, 30), (13, 29), (11, 25), (5, 25), (9, 22),
              (10, 14), (6, 11), (11, 10), (13, 6)])
    I.add(p, FUR, shade=0.9, edge=True)
    I.add(ellipse(16, 18, 3.2, 8) & p, DEER, flat=3, rim=False, core=False)
    # lacing to the hoop
    for a in range(0, 360, 45):
        r = math.radians(a)
        x0, y0 = 16 + math.cos(r) * 12.4, 16 + math.sin(r) * 12.4
        x1, y1 = 16 + math.cos(r) * 9.5, 16 + math.sin(r) * 9.5
        I.line(seg(x0, y0, x1, y1, 0.9), BONE, 2)
    I.px(14, 8, INK[0])
    I.px(18, 8, INK[0])
    return I


# ------------------------------------------------------------------------------------------------ tracking hound
@icon('hound')
def hound():
    I = Icon()
    head = poly([(8, 9), (12, 5), (18, 5), (22, 8), (26, 14), (28, 18), (27, 21), (22, 21), (19, 19), (17, 22), (12, 22), (9, 18)])
    I.add(head, HOUND, shade=0.9, edge=True)
    # long drooping ear
    I.add(poly([(10, 9), (14, 10), (15, 18), (13, 28), (9, 29), (7, 24), (7, 15)]), HOUND, shade=0.6, edge=True, light=(-0.5, -1))
    # muzzle and nose
    I.add(poly([(20, 13), (26, 14), (28, 18), (27, 21), (22, 21), (19, 18)]), HOUND, flat=3, rim=False, core=False)
    I.add(ellipse(27, 16.5, 1.6, 1.3), INK, flat=0)
    I.line(path([(22.5, 20.5), (26.5, 20.5)], 1.0), INK, 1)
    # eye with droopy lid
    I.px(18, 11, INK[0])
    I.px(19, 11, INK[0])
    I.px(18, 10, HOUND[1])
    # collar
    I.add(poly([(11, 22), (19, 21), (20, 24), (12, 25)]), RED, shade=0.6, edge=True)
    I.px(16, 24, BRASS[4])
    return I


# ------------------------------------------------------------------------------------------------ grouse flush
@icon('flush')
def flush():
    I = Icon()
    # ruffed grouse bursting up off the ground, wings raised in a V
    lw = poly([(15, 14), (9, 4), (4, 3), (6, 9), (10, 15)])
    rw = poly([(18, 14), (24, 4), (29, 3), (27, 9), (22, 15)])
    I.add(lw, GROUSE, shade=0.9, edge=True)
    I.add(rw, GROUSE, shade=0.9, edge=True)
    for k in range(3):
        I.line(path([(5.5 + k * 1.6, 4.5 + k * 2), (9 + k * 1.4, 9 + k * 1.6)], 0.8), GROUSE, 1)
        I.line(path([(27.5 - k * 1.6, 4.5 + k * 2), (24 - k * 1.4, 9 + k * 1.6)], 0.8), GROUSE, 1)
    body = ellipse(16.5, 17, 5.2, 4.4, -15)
    I.add(body, GROUSE, shade=0.9, edge=True)
    I.add(ellipse(21.5, 12.6, 2.4, 2.3), GROUSE, shade=0.9, edge=True)
    I.add(poly([(20.2, 10.8), (21.4, 9), (22.4, 11)]), GROUSE, flat=1)
    I.add(poly([(23.6, 12.6), (25.6, 13.2), (23.8, 13.8)]), INK, flat=2)
    I.px(22, 12, INK[0])
    # black ruff on the neck, barred fanned tail
    I.add(ellipse(19.6, 14.8, 1.4, 1.0), INK, flat=1, rim=False, core=False)
    tail = poly([(12, 18), (14.5, 21), (10, 27), (5, 25)])
    I.add(tail, GROUSE, shade=0.6, edge=True)
    I.line(seg(6.6, 24.2, 11.4, 25.8, 1.0), INK, 2)
    # dust kicked up below
    for x, y in ((14, 28), (18, 29), (21, 27), (11, 30)):
        I.px(x, y, MUD[3])
    return I


# ------------------------------------------------------------------------------------------------ on the wing
@icon('wing')
def wing():
    I = Icon()
    # a drake mallard crossing left to right in flight, wings up, with speed lines behind
    for y, x0 in ((11, 1), (16, 0), (21, 2)):
        I.line(rect(x0, y, x0 + 5, y + 1), PAPER, 2)
    I.add(poly([(13, 15), (17, 3), (22, 4), (20, 15)]), STONE, shade=0.8, edge=True)
    body = ellipse(17.5, 17.5, 9.0, 3.8, -6)
    I.add(body, STONE, shade=0.9, edge=True)
    I.add(ellipse(23.6, 17.4, 3.4, 3.0) & body, CHESTNUT, flat=2, rim=False, core=False)
    I.add(poly([(8.6, 15.6), (6, 13), (6.2, 18.6), (9, 19)]), INK, flat=1, edge=True)
    I.add(circle(27.2, 14.4, 2.7), DUCKG, shade=0.9, edge=True)
    I.line(path([(25.4, 16.4), (24.4, 17.2)], 1.0), WHITE, 3)
    I.add(poly([(29.4, 14.2), (31.6, 15.2), (29.4, 15.8)]), CORN, flat=3)
    I.px(28, 14, INK[0])
    I.add(poly([(15, 18), (17, 27), (21.5, 26), (19.5, 18)]), STONE, shade=0.6, edge=True)
    I.add(rect(17, 22.5, 20.5, 24), SLATE, flat=3, rim=False, core=False)
    return I


# ------------------------------------------------------------------------------------------------ a day's limit
@icon('limit')
def limit():
    I = Icon()
    # leather game strap with three birds hanging by the neck
    I.add(rect(4, 4, 28, 7), LEATHER, shade=0.6, edge=True)
    I.add(rect(14.5, 3, 17.5, 8), BRASS, shade=0.7, edge=True)
    for k, x in enumerate((8, 16, 24)):
        I.line(path([(x, 7), (x, 10)], 1.0), LEATHER, 1)
        body = ellipse(x, 19, 3.2, 7.2)
        I.add(body, GROUSE if k != 1 else DUCKG, shade=0.9, edge=True)
        I.add(ellipse(x, 12, 1.8, 2.2), GROUSE if k != 1 else DUCKG, shade=0.8, edge=True)
        I.add(poly([(x - 2.6, 25), (x + 2.6, 25), (x + 1.6, 29.5), (x - 1.6, 29.5)]), GROUSE, shade=0.5, edge=True)
        if k == 1:
            I.add(ellipse(x, 17, 2.6, 2.0) & body, CHESTNUT, flat=2, rim=False, core=False)
    return I


# ------------------------------------------------------------------------------------------------ decoy
@icon('decoy')
def decoy():
    I = Icon()
    # water
    I.add(rect(1, 21, 31, 29), SLATE, shade=0.6)
    for y, x0, x1 in ((23, 3, 9), (25, 20, 29), (27, 6, 14)):
        I.line(rect(x0, y, x1, y + 1), ICE, 3)
    # hull floating, head to the left
    hull = poly([(6, 21), (8, 15), (13, 13.5), (22, 13.5), (26, 15), (28, 13), (28.5, 17), (26, 21.5)])
    I.add(hull, WHITE, shade=0.9, edge=True)
    I.add(poly([(23, 13), (28, 12.5), (28.5, 17), (25, 18)]) & hull, INK, flat=1, rim=False, core=False)
    I.add(poly([(6, 21), (8, 15), (12, 14), (12, 21)]) & hull, CHESTNUT, flat=2, rim=False, core=False)
    I.add(rect(14, 14.5, 19, 16), SLATE, flat=3, rim=False, core=False)
    I.add(rect(13, 13.5, 23, 14.5), STONE, flat=2, rim=False, core=False)
    # head and neck
    I.add(poly([(7, 15), (8, 10), (10, 8), (13, 8), (14, 11), (12.5, 15)]), DUCKG, shade=0.9, edge=True)
    I.line(path([(8, 14.5), (12.5, 14.5)], 1.0), WHITE, 4)
    I.add(poly([(8.2, 9.4), (3.5, 10.6), (3.6, 12), (8.6, 11.6)]), CORN, shade=0.6, edge=True)
    I.px(10, 10, INK[0])
    # anchor line
    I.line(path([(26, 22), (28, 27)], 0.9), INK, 3)
    return I


# ------------------------------------------------------------------------------------------------ hunts emblem
@icon('hunts')
def hunts():
    I = Icon()
    # brass hunting horn with a leather carrying strap
    I.add(path(bez((4, 22), (4, 6), (22, 2), (28, 12), n=20), 1.8), LEATHER, flat=2)
    curve = bez((5, 27), (8, 15), (17, 13), (25, 20), n=28)
    horn = taper(curve, 2.0, 6.4)
    I.add(horn, BRASS, shade=0.9, edge=True)
    I.add(ellipse(25.6, 20.8, 1.9, 3.6, -40), INK, flat=1, rim=False, core=False)
    I.add(ring(25.6, 20.8, 2.6, 3.7) & ~ellipse(25.6, 20.8, 1.9, 3.6, -40) & circle(25.6, 20.8, 4.2), BRASS, flat=4, rim=False, core=False)
    I.add(seg(4.2, 28.8, 5.6, 25.6, 2.4), STEEL, flat=3, edge=True)
    for t in (0.3, 0.6):
        x, y = curve[int(t * 28)]
        I.add(circle(x, y, 1.6 + t * 2.4) & horn, BRASS, flat=1, rim=False, core=False)
    return I


# ------------------------------------------------------------------------------------------------ atlas
def render_all():
    return {name: f().render() for name, f in ICONS.items()}


def write_atlas(repo, imgs):
    names = list(imgs)
    assert len(names) <= 16, len(names)
    A = Image.new('RGBA', (256, 128), (0, 0, 0, 0))
    for i, n in enumerate(names):
        x, y = (i % 8) * 32, (i // 8) * 32
        A.alpha_composite(imgs[n], (x, y))
        A.alpha_composite(brighten(imgs[n]), (x, y + 64))
    out = os.path.join(repo, 'patch/assets/frontierhunts/textures/gui/journal/hunts_icons.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    # [integ6] artqa's journal treatment: bleed colour into transparent texels (no dark fringe under bilinear /
    # mipmapped sampling) and a 2x nearest copy for sharp in-between GUI scales (see HuntIcons.draw)
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'artqa'))
    from bleed import bleed_image
    A = bleed_image(A)
    A.save(out, optimize=True)
    A.resize((512, 256), Image.NEAREST).save(out.replace('hunts_icons.png', 'hunts_icons_x2.png'), optimize=True)
    jf = os.path.join(repo, 'src/com/formaworks/frontierhunts/hunts/client/HuntIcons.java')
    if os.path.exists(jf):
        src = open(jf).read()
        lines = []
        for k in range(0, len(names), 8):
            lines.append('      ' + ', '.join('"%s"' % n for n in names[k:k + 8]) + ',')
        body = '\n'.join(lines)
        src = re.sub(r'(// GENERATED-NAMES-BEGIN[^\n]*\n).*?([ \t]*// GENERATED-NAMES-END)', lambda m: m.group(1) + body + '\n' + m.group(2), src, flags=re.S)
        open(jf, 'w').write(src)
    return out


def sheet(imgs, out):
    names = list(imgs)
    bgs = [(0xEA, 0xE3, 0xD0), (0x49, 0x37, 0x2B)]
    cell = 32 * 4 + 12
    S = Image.new('RGBA', (8 * cell, 2 * (cell + 12) * 2 + 140), (30, 26, 22, 255))
    d = ImageDraw.Draw(S)
    for b, bg in enumerate(bgs):
        oy = b * 2 * (cell + 12)
        d.rectangle([0, oy, 8 * cell, oy + 2 * (cell + 12) - 1], fill=bg)
        for i, n in enumerate(names):
            x, y = (i % 8) * cell + 6, oy + (i // 8) * (cell + 12) + 4
            S.alpha_composite(imgs[n].resize((128, 128), Image.NEAREST), (x, y))
            d.text((x, y + 129), n, fill=(60, 50, 40) if b == 0 else (230, 220, 200))
    # 1x and 2x strip on paper (what players actually see)
    oy = 4 * (cell + 12) + 8
    d.rectangle([0, oy, 8 * cell, oy + 130], fill=bgs[0])
    for i, n in enumerate(names):
        S.alpha_composite(imgs[n].resize((16, 16), Image.NEAREST), (8 + i * 20, oy + 8))
        S.alpha_composite(imgs[n], (8 + i * 36, oy + 40))
        S.alpha_composite(brighten(imgs[n]), (8 + i * 36, oy + 80))
    S.save(out)


if __name__ == '__main__':
    repo = sys.argv[1]
    imgs = render_all()
    print(write_atlas(repo, imgs), len(imgs), 'icons')
    if len(sys.argv) > 2:
        sheet(imgs, sys.argv[2])
