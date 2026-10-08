#!/usr/bin/env python3
"""[ledger] Hunter's Journal / Expedition journal icon atlas: original 32x32 pixel art, drawn procedurally.

python3 tools/ledger/icons.py <repo> [sheet.png]
Writes patch/assets/frontierhunts/textures/gui/journal/icons.png (512x512: 16 x 8 cells of normal icons in the top
half, the same icons brightened for hover/selected in the bottom half) and rewrites the NAMES array in
src/.../journal/client/JournalIcons.java between the GENERATED markers. Optional contact sheet at 1x/2x/4x on the
journal's leather, paper and toast backgrounds.
"""
import os
import re
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from px import *  # noqa
import numpy as np

ICONS = {}
UP = (0.0, -1.0)


def icon(name):
    def deco(f):
        ICONS[name] = f
        return f
    return deco


# ================================================================================================ journal tabs
@icon('home')
def home():
    I = Icon()
    I.add(rect(21, 4, 25, 13), STONE, shade=0.6)
    I.line(rect(21, 7, 25, 8), STONE, 1)
    I.line(rect(21, 10, 25, 11), STONE, 1)
    for k in range(5):
        y = 14 + k * 3
        I.add(rect(5, y, 27, y + 3), LOG, shade=1.0, light=UP)
        I.line(rect(5, y + 1, 6, y + 2), BONE, 2)
        I.line(rect(26, y + 1, 27, y + 2), BONE, 1)
    I.add(poly([(2, 15.5), (16, 3), (30, 15.5)]), RED, shade=0.8, edge=True)
    for x0, y0 in ((9, 12), (16, 8), (23, 12)):
        I.line(rect(x0 - 2, y0, x0 + 2, y0 + 1), RED, 1)
    I.add(rect(8, 20, 14, 29), WOOD, flat=1, edge=True)
    I.line(rect(12, 24, 13, 25), BRASS, 3)
    I.add(rect(17, 19, 24, 25), FLAME, shade=0.8, edge=True)
    I.line(rect(20, 19, 21, 25), LOG, 0)
    I.line(rect(17, 21, 24, 22), LOG, 0)
    I.add(rect(20.5, 3, 25.5, 5), STONE, flat=1, edge=True)
    return I


@icon('checklist')
def checklist():
    I = Icon()
    I.add(rect(5, 4, 24, 30), PAPER, shade=0.7)
    I.add(rect(11, 2, 18, 6), BRASS, shade=0.8, edge=True)
    for k, done in enumerate((True, True, False)):
        y = 9 + k * 6
        I.add(rect(8, y, 12, y + 4), PAPER, flat=4, edge=True, rim=False)
        I.line(rect(14, y + 1, 21, y + 2), INK, 3)
        I.line(rect(14, y + 3, 19, y + 4), PAPER, 1)
        if done:
            I.line(path([(8.4, y + 1.6), (10, y + 3.4), (13.4, y - 0.8)], 1.3), GREEN, 2)
    I.add(taper([(18, 29), (29, 15)], 3.6, 3.6), BRASS, shade=0.9, edge=True)
    I.add(seg(28, 16.3, 29.6, 14.3, 3.4), BLAZE, flat=2)
    I.add(poly([(16.2, 30.6), (19.6, 26.4), (20.4, 29.2)]), WOOD, flat=4, edge=True)
    I.line(rect(16, 30, 17, 31), INK, 0)
    return I


@icon('skills')
def skills():
    I = Icon()
    # rifle: stock bottom-left, muzzle top-right
    I.add(taper([(3, 28.5), (11.5, 20.5)], 5.8, 3.4), WOOD, shade=0.9)
    I.add(seg(10.5, 21.5, 29, 3, 2.2), STEEL, shade=0.6, edge=True)
    I.add(seg(12.5, 19, 18, 13.5, 3.8), STEEL, shade=0.8, edge=True)
    I.add(seg(14.5, 12.5, 20, 7, 2.0), STEEL, flat=1, edge=True)
    # arrow: nock bottom-right, head top-left
    I.add(seg(27, 27, 6, 6, 1.5), WOOD, flat=3, edge=True)
    I.add(poly([(2.5, 2.5), (8.6, 4.4), (4.4, 8.6)]), STEEL, shade=0.8, edge=True)
    I.add(poly([(24, 27.5), (28, 24), (30.5, 26.5), (30, 30), (26.5, 30.5)]), RED, shade=0.6, edge=True)
    I.line(path([(25.5, 25.5), (29.5, 29.5)], 1), RED, 1)
    I.add(star(16, 16, 2.3, 5.4), GOLD, shade=0.8, edge=True)
    return I


@icon('species')
def species():
    I = Icon()
    # antlers
    for sgn in (-1, 1):
        cx = 16
        b = bez((cx + sgn * 4, 12), (cx + sgn * 9, 9), (cx + sgn * 11, 5), (cx + sgn * 9, 1.5), n=16)
        I.add(taper(b, 2.6, 1.4), BONE, shade=0.7)
        I.add(taper([(cx + sgn * 8.4, 8.5), (cx + sgn * 6.5, 3)], 2.0, 1.2), BONE, shade=0.5)
        I.add(taper([(cx + sgn * 10.2, 6.2), (cx + sgn * 13.6, 3.4)], 1.8, 1.1), BONE, shade=0.5)
    # ears
    I.add(ellipse(7, 15, 5.2, 2.4, -20), DEER, shade=0.7)
    I.add(ellipse(25, 15, 5.2, 2.4, 20), DEER, shade=0.7)
    I.add(ellipse(7.3, 15.2, 3.4, 1.1, -20), BONE, flat=3, rim=False)
    I.add(ellipse(24.7, 15.2, 3.4, 1.1, 20), BONE, flat=3, rim=False)
    # head
    I.add(poly([(10, 12), (22, 12), (23.5, 17), (20, 27), (18.5, 30), (13.5, 30), (12, 27), (8.5, 17)]), DEER, shade=0.8, edge=True)
    I.add(poly([(12.5, 25), (19.5, 25), (18.6, 28.5), (13.4, 28.5)]), WHITE, flat=3, rim=False)
    I.add(ellipse(16, 28.4, 2.6, 1.6), INK, flat=1)
    I.line(rect(15, 28, 16, 29), INK, 4)
    for ex in (11.6, 20.4):
        I.add(ellipse(ex, 18.3, 1.5, 1.2), INK, flat=0)
    I.px(11, 17, BONE[4])
    I.px(20, 17, BONE[4])
    I.add(poly([(15, 13), (17, 13), (16.6, 22), (15.4, 22)]), DEER, flat=3, rim=False)
    return I


@icon('records')
def records():
    I = Icon()
    I.add(poly([(6, 11), (26, 11), (26, 22), (16, 30.5), (6, 22)]), WOOD, shade=0.9)
    I.add(poly([(8.5, 13), (23.5, 13), (23.5, 21), (16, 27.6), (8.5, 21)]), WOOD, flat=1, rim=False)
    I.add(rect(11, 20, 21, 23), BRASS, shade=0.8, edge=True)
    I.line(rect(13, 21, 19, 22), BRASS, 1)
    for sgn in (-1, 1):
        b = bez((16 + sgn * 2, 14), (16 + sgn * 8, 13), (16 + sgn * 12, 8), (16 + sgn * 11, 2), n=18)
        I.add(taper(b, 2.8, 1.4), BONE, shade=0.7, edge=True)
        I.add(taper([(16 + sgn * 8.6, 11.8), (16 + sgn * 7.2, 5)], 2.0, 1.1), BONE, shade=0.5, edge=True)
        I.add(taper([(16 + sgn * 11.3, 8), (16 + sgn * 14.5, 5.5)], 1.8, 1.0), BONE, shade=0.5, edge=True)
    I.add(ellipse(16, 15, 3.2, 3.6), BONE, shade=0.8, edge=True)
    I.px(15, 16, BONE[0])
    I.px(16, 16, BONE[0])
    return I


@icon('notes')
def notes():
    I = Icon()
    I.add(rect(22, 5, 26, 29), PAPER, flat=3, rim=False)
    for y in range(6, 29, 2):
        I.line(rect(22, y, 26, y + 1), PAPER, 1)
    I.add(rect(5, 3, 23, 30), LEATHER, shade=0.9, edge=True)
    I.line(rect(7, 3, 8, 30), LEATHER, 1)
    I.add(rect(10, 8, 20, 14), PAPER, shade=0.4, edge=True)
    I.line(rect(12, 10, 18, 11), INK, 3)
    I.line(rect(12, 12, 16, 13), PAPER, 1)
    I.add(rect(18, 3, 20, 30), INK, flat=2, edge=True)
    I.add(taper([(13, 30), (27.5, 17)], 3.4, 3.4), BRASS, shade=0.9, edge=True)
    I.add(seg(26.5, 18.0, 28.4, 16.2, 3.2), BLAZE, flat=2)
    I.add(poly([(10.6, 31.0), (13.8, 27.4), (14.8, 30.0)]), WOOD, flat=4, edge=True)
    return I


@icon('reserve')
def reserve():
    I = Icon()
    p1 = poly([(3, 8), (11.5, 5), (11.5, 26), (3, 29)])
    p2 = poly([(11.5, 5), (20.5, 8), (20.5, 29), (11.5, 26)])
    p3 = poly([(20.5, 8), (29, 5), (29, 26), (20.5, 29)])
    I.add(p1, PAPER, flat=3)
    I.add(p2, PAPER, flat=2)
    I.add(p3, PAPER, flat=3)
    I.add(ellipse(7, 14, 3.4, 3.0) & p1, GREEN, shade=0.6)
    I.add(ellipse(24, 22, 3.6, 3.0) & p3, GREEN, shade=0.6)
    I.add(ellipse(15, 21, 3.0, 2.4) & p2, GREEN, shade=0.6)
    river = bez((3, 22), (10, 18), (18, 26), (29, 12), n=24)
    I.line(path(river, 1.6) & (p1 | p2 | p3), SLATE, 3)
    for x, y in ((6, 26), (9, 24), (12, 21), (15, 17), (18, 14), (21, 12)):
        I.px(x, y, RED[2])
    I.line(rect(11, 5, 12, 26), PAPER, 1)
    I.line(rect(20, 8, 21, 29), PAPER, 1)
    I.add(seg(23, 11, 23, 15.5, 1.2), STEEL, flat=3)
    I.add(circle(23, 9.2, 2.6), RED, shade=0.8, edge=True)
    I.px(22, 8, RED[4])
    return I


@icon('survival')
def survival():
    I = Icon()
    ROAST = ramp('#3e180b', '#6b2c12', '#99491f', '#c06f3a', '#e0a066')
    I.add(seg(5, 13, 5, 30, 1.8), WOOD, flat=2)
    I.add(seg(27, 13, 27, 30, 1.8), WOOD, flat=2)
    I.add(seg(5, 14, 2.5, 10.5, 1.6), WOOD, flat=2)
    I.add(seg(27, 14, 29.5, 10.5, 1.6), WOOD, flat=2)
    I.add(taper([(5, 29.5), (26, 24)], 4.2, 3.4), LOG, shade=0.8, edge=True)
    I.add(taper([(27, 29.5), (6, 24)], 4.2, 3.4), LOG, shade=0.8, edge=True)
    I.line(circle(5.6, 29, 1.1), BONE, 2)
    I.line(circle(26.4, 29, 1.1), BONE, 2)
    fl = poly([(10, 25), (9.5, 20), (12.5, 16), (13.5, 18.5), (16, 13.5), (19, 18), (20.5, 15.5), (22.5, 20), (22, 25)])
    I.add(fl, FLAME, shade=0.6, rim=False, core=False)
    I.add(poly([(12, 25), (12.5, 21.5), (14.5, 19), (16, 16.5), (18, 20), (19.8, 19), (20, 25)]), FLAME, flat=3, rim=False)
    I.add(poly([(14, 25), (14.5, 22.5), (16, 20), (17.6, 23), (18, 25)]), FLAME, flat=4, rim=False)
    I.add(seg(2.5, 11, 29.5, 11, 1.4), WOOD, flat=3, edge=True)
    # roast haunch on the spit, knuckle bone to the right
    haunch = poly(bez((7, 11), (7, 4.5), (16, 4.5), (21, 9), n=10) + bez((21, 9), (22.5, 11), (21, 13), (19, 13.4), n=6)
                  + bez((19, 13.4), (14, 16.5), (8, 15), (7, 11), n=10))
    I.add(haunch, ROAST, shade=1.0, edge=True)
    I.add(ellipse(11.5, 8.2, 2.8, 1.2, -15), ROAST, flat=4, rim=False)
    I.add(seg(20, 11, 25, 9.5, 2.2), BONE, flat=3, edge=True)
    I.add(circle(25.5, 8.6, 1.5) | circle(25.8, 10.6, 1.4), BONE, flat=4, edge=True)
    return I


@icon('campaign')
def campaign():
    I = Icon()
    I.add(poly([(5, 4), (24, 4), (27, 7), (27, 29), (5, 29)]), PAPER, shade=0.7)
    I.add(poly([(24, 4), (24, 7), (27, 7)]), PAPER, flat=1, edge=True)
    I.line(rect(8, 7, 19, 8), INK, 3)
    for y in (11, 14, 17):
        I.line(rect(8, y, 24, y + 1), PAPER, 1)
    I.line(rect(8, 20, 15, 21), PAPER, 1)
    I.add(poly([(17, 24), (14.5, 31), (17.5, 29.2), (19.5, 31.5), (20, 25)]), RED, flat=1, edge=True)
    I.add(poly([(22, 24), (24, 31), (21.8, 29.6), (19.6, 31.2), (19.8, 25)]), RED, flat=2, edge=True)
    I.add(circle(20, 23, 4.2), RED, shade=0.9, edge=True)
    I.add(ring(20, 23, 1.6, 2.6), RED, flat=1, rim=False)
    I.px(18, 21, RED[4])
    return I


@icon('contracts')
def contracts():
    I = Icon()
    I.add(poly([(6, 5), (26, 5), (26, 26), (23, 28), (20, 26), (17, 29), (14, 26), (11, 28.5), (8, 26), (6, 28)]), PAPER, shade=0.8)
    I.add(rect(9, 9, 23, 13), RED, shade=0.5)
    I.line(rect(11, 10, 21, 11), PAPER, 4)
    I.line(rect(9, 16, 23, 17), PAPER, 1)
    I.line(rect(9, 19, 20, 20), PAPER, 1)
    I.add(circle(21, 22.5, 3.4), BRASS, shade=0.9, edge=True)
    I.line(ring(21, 22.5, 1.4, 2.2), BRASS, 1)
    I.add(circle(16, 4.5, 2.6), BLAZE, shade=0.9, edge=True)
    I.px(15, 3, BLAZE[4])
    return I


@icon('equipment')
def equipment():
    I = Icon()
    I.add(seg(10, 6, 9, 13, 2.2), LEATHER, flat=1)
    I.add(seg(22, 6, 23, 13, 2.2), LEATHER, flat=1)
    I.add(poly([(7, 11), (25, 11), (27, 27), (25.5, 30), (6.5, 30), (5, 27)]), CANVAS, shade=0.9)
    I.add(poly([(7.5, 10), (24.5, 10), (25, 19), (16, 21.5), (7, 19)]), CANVAS, shade=0.7, edge=True)
    I.add(rect(10, 22.5, 22, 28), CANVAS, flat=1, edge=True)
    I.add(rect(10, 22.5, 22, 24), CANVAS, flat=3, rim=False)
    for x in (11, 19):
        I.add(rect(x, 13, x + 2, 22), LEATHER, shade=0.5, edge=True)
        I.add(rect(x - 0.5, 18, x + 2.5, 20.5), BRASS, flat=3, edge=True)
    I.add(ellipse(16, 8, 6.5, 3), LEATHER, shade=0.7, edge=True)
    I.cut(ellipse(16, 8.4, 3.8, 1.4))
    return I


@icon('training')
def training():
    I = Icon()
    I.add(seg(9, 22, 6, 30, 2), WOOD, flat=2)
    I.add(seg(21, 22, 24, 30, 2), WOOD, flat=2)
    I.add(circle(15, 15, 12.5), PAPER, shade=0.6)
    I.add(ring(15, 15, 8.6, 10.6), RED, shade=0.4, rim=False, core=False)
    I.add(ring(15, 15, 4.6, 6.4), RED, shade=0.4, rim=False, core=False)
    I.add(circle(15, 15, 2.6), RED, shade=0.4)
    I.add(seg(16, 14, 27, 3, 1.4), WOOD, flat=3, edge=True)
    I.add(poly([(25, 2), (30.5, 1.5), (27, 5)]), WHITE, flat=3, edge=True)
    I.add(poly([(26.5, 0.8), (30.8, 0.6), (30.4, 5), (28, 3.4)]), RED, flat=2, edge=True)
    return I


@icon('lodge')
def lodge():
    I = Icon()
    I.add(rect(23, 3, 27, 12), STONE, shade=0.6)
    I.line(rect(23, 6, 27, 7), STONE, 1)
    for k in range(5):
        y = 15 + k * 3
        I.add(rect(3, y, 29, y + 3), LOG, shade=1.0, light=UP)
        I.line(rect(3, y + 1, 4, y + 2), BONE, 2)
        I.line(rect(28, y + 1, 29, y + 2), BONE, 1)
    I.add(poly([(0.5, 16), (16, 5), (31.5, 16)]), GREEN, shade=0.8, edge=True)
    for x0, y0 in ((8, 13), (16, 9), (24, 13)):
        I.line(rect(x0 - 2, y0, x0 + 2, y0 + 1), GREEN, 1)
    I.add(rect(12.5, 21, 19.5, 30), WOOD, flat=1, edge=True)
    I.line(rect(16, 21, 17, 30), WOOD, 0)
    for wx in (5, 22):
        I.add(rect(wx, 20, wx + 5, 25), FLAME, shade=0.8, edge=True)
        I.line(rect(wx + 2, 20, wx + 3, 25), LOG, 0)
    for sgn in (-1, 1):
        b = bez((16 + sgn * 1.2, 17), (16 + sgn * 5, 16.5), (16 + sgn * 6.5, 12.5), n=10)
        I.add(taper(b, 1.9, 1.1), BONE, shade=0.4, edge=True)
        I.add(taper([(16 + sgn * 4.4, 15.8), (16 + sgn * 4.0, 13.2)], 1.5, 1.0), BONE, shade=0.3, edge=True)
    I.add(ellipse(16, 17.8, 1.7, 1.9), BONE, flat=3, edge=True)
    return I


@icon('ledger')
def ledger():
    I = Icon()
    I.add(poly([(2, 9), (16, 11), (30, 9), (30, 27), (16, 29), (2, 27)]), LEATHER, shade=0.8)
    I.add(poly([(3.5, 7), (16, 9.5), (16, 27), (3.5, 25)]), PAPER, shade=0.6, edge=True)
    I.add(poly([(16, 9.5), (28.5, 7), (28.5, 25), (16, 27)]), PAPER, shade=0.6, edge=True)
    for k in range(4):
        y = 12 + k * 3.5
        I.line(path([(6, y - 1.2), (14, y + 0.4)], 1), INK, 3 if k == 0 else 4)
        I.line(path([(18, y + 0.4), (26, y - 1.2)], 1), INK, 4)
    I.line(rect(15.5, 9, 16.5, 27.5), PAPER, 0)
    I.add(poly([(21, 6), (24, 5.5), (24, 15), (22.5, 13.5), (21, 15)]), RED, shade=0.6, edge=True)
    return I


@icon('seasons')
def seasons():
    I = Icon()
    I.add(rect(4, 5, 28, 30), PAPER, shade=0.7)
    I.add(rect(4, 5, 28, 11), RED, shade=0.6, edge=True)
    for x in (9, 22):
        I.add(rect(x, 2, x + 2, 8), STEEL, shade=0.6, edge=True)
    leaf = [(16, 12.5), (17.5, 16.5), (21.5, 14.8), (20.4, 18.6), (24.6, 19.4), (21.4, 22.2), (22.2, 25.2), (17.2, 24.2), (16.6, 28)]
    lp = leaf + [(15.4, 28), (14.8, 24.2), (9.8, 25.2), (10.6, 22.2), (7.4, 19.4), (11.6, 18.6), (10.5, 14.8), (14.5, 16.5)]
    I.add(poly(lp), BLAZE, shade=0.9, edge=True)
    I.line(path([(16, 15), (16, 28.8)], 1), BLAZE, 0)
    I.line(path([(16, 23), (20.5, 20)], 1), BLAZE, 1)
    I.line(path([(16, 23), (11.5, 20)], 1), BLAZE, 1)
    return I


# ================================================================================================ misc UI
@icon('school')
def school():
    I = Icon()
    I.add(ellipse(16, 22, 14.5, 5.2), DEER, shade=0.9)
    I.add(ellipse(16, 21.4, 9.5, 2.6), DEER, flat=1, rim=False)
    crown = poly([(8.5, 21.5), (9.5, 12), (12.5, 6.5), (14.6, 9.4), (16, 4.6), (17.4, 9.4), (19.5, 6.5), (22.5, 12), (23.5, 21.5)])
    I.add(crown, DEER, shade=1.0, edge=True)
    I.line(path([(16, 6), (16, 11)], 1), DEER, 0)
    I.line(path([(12.8, 7.8), (13.8, 12.5)], 1), DEER, 1)
    I.line(path([(19.2, 7.8), (18.2, 12.5)], 1), DEER, 1)
    I.add(rect(8.8, 17, 23.2, 20.5), LEATHER, shade=0.6, edge=True)
    I.add(rect(18, 16.5, 21, 21), BRASS, flat=3, edge=True)
    return I


@icon('token')
def token():
    I = Icon()
    I.add(circle(16, 16, 13.5), BRASS, shade=1.0)
    I.add(ring(16, 16, 10.4, 11.5), BRASS, flat=1, rim=False)
    tree = poly([(16, 7), (20.5, 13), (18.5, 13), (22, 18), (19.5, 18), (23, 22.5), (9, 22.5), (12.5, 18), (10, 18), (13.5, 13), (11.5, 13)])
    I.add(tree, BRASS, flat=1, rim=False)
    I.add(tree & (CX < 16.5) & ~poly([(16, 9.5), (19.8, 14.5), (12.2, 14.5)]), BRASS, flat=1, rim=False)
    I.add(rect(15, 22.5, 17, 25.5), BRASS, flat=1, rim=False)
    I.add(poly([(16, 9.2), (13.2, 13), (14.8, 13), (12, 17.5)]) & tree, BRASS, flat=3, rim=False)
    return I


@icon('next')
def nextsign():
    I = Icon()
    I.add(rect(14, 6, 18, 30.5), WOOD, shade=0.8)
    I.add(poly([(3, 7), (24, 7), (29.5, 11.5), (24, 16), (3, 16)]), WOOD, shade=0.8, edge=True)
    I.line(rect(5, 11, 22, 12), WOOD, 1)
    I.add(poly([(6, 18.5), (24, 18.5), (24, 25), (6, 25), (2.5, 21.75)]), WOOD, shade=0.8, edge=True)
    I.line(rect(8, 21, 22, 22), WOOD, 1)
    I.add(circle(9, 4, 2), GREEN, shade=0.6)
    I.add(ellipse(16, 4, 3.4, 2.2), GREEN, shade=0.6)
    I.add(ellipse(7, 29.5, 4, 1.6), GREEN, shade=0.6)
    I.add(ellipse(24, 29.5, 4, 1.6), GREEN, shade=0.6)
    I.add(circle(26, 12, 1.0), BRASS, flat=3)
    return I


@icon('lock')
def lock():
    I = Icon()
    I.add(ring(16, 13, 5.2, 8.0) & (CY < 16), STEEL, shade=0.8)
    I.add(rect(7, 14, 25, 29), BRASS, shade=0.9, edge=True)
    I.add(circle(16, 20, 2.2), INK, flat=0)
    I.add(rect(15, 20, 17, 25), INK, flat=0)
    return I


@icon('star')
def starp():
    I = Icon()
    I.add(star(16, 16.5, 6.0, 14.5), GOLD, shade=1.0)
    I.add(star(16, 16.5, 2.8, 6.8), GOLD, flat=4, rim=False)
    return I


@icon('party')
def party():
    I = Icon()
    for cx, col, d in ((11, BLAZE, 0), (21, GREEN, 1)):
        I.add(ellipse(cx, 28.5, 8, 6.5) & (CY < 31), col, shade=0.8, edge=bool(d))
        I.add(circle(cx, 15.5, 4.6), DEER, shade=0.8, edge=bool(d))
        I.add(ellipse(cx, 11.6, 5.2, 3.2) & (CY < 12.6), col, shade=0.8, edge=True)
        I.add(rect(cx - 1, 11.6, cx + 7, 13), col, flat=1, edge=True)
    return I


@icon('marker')
def marker():
    I = Icon()
    I.add(ellipse(16, 27, 9, 3.4), GREEN, shade=0.7)
    I.add(poly([(16, 29), (8.5, 15), (23.5, 15)]), RED, shade=0.8, edge=True)
    I.add(circle(16, 12, 8.2), RED, shade=1.0, edge=True)
    I.add(circle(16, 12, 3.2), PAPER, shade=0.5)
    return I


@icon('clock')
def clock():
    I = Icon()
    I.add(ring(16, 4.5, 1.6, 3.0), BRASS, shade=0.6)
    I.add(circle(16, 18, 12.5), BRASS, shade=1.0)
    I.add(circle(16, 18, 10), WHITE, shade=0.4, edge=True)
    for a in range(12):
        import math as _m
        x = 16 + _m.cos(a * _m.pi / 6) * 8.4
        y = 18 + _m.sin(a * _m.pi / 6) * 8.4
        I.px(int(x), int(y), INK[2] if a % 3 else INK[0])
    I.line(path([(16, 18), (16, 11.5)], 1.4), INK, 1)
    I.line(path([(16, 18), (20.5, 20.5)], 1.4), INK, 1)
    I.px(15, 17, RED[2])
    return I


@icon('assign')
def assign():
    I = Icon()
    I.add(poly([(16, 2.5), (27, 6.5), (26, 18), (16, 29.5), (6, 18), (5, 6.5)]), BRASS, shade=1.0)
    I.add(poly([(16, 5.5), (24.2, 8.6), (23.4, 17.2), (16, 26), (8.6, 17.2), (7.8, 8.6)]), GREEN, shade=0.8, edge=True)
    I.add(star(16, 15.5, 2.6, 6.0), BRASS, shade=0.8, edge=True)
    return I


@icon('guide')
def guide():
    I = Icon()
    I.add(rect(23, 5, 27, 29), PAPER, flat=3, rim=False)
    I.add(rect(5, 3, 24, 30), GREEN, shade=0.9, edge=True)
    I.line(rect(7, 3, 8, 30), GREEN, 1)
    I.add(circle(15.5, 15, 6.2), BRASS, shade=0.9, edge=True)
    I.add(circle(15.5, 15, 4.4), GREEN, flat=1, rim=False)
    I.add(poly([(15.5, 10.8), (17, 15), (15.5, 19.2), (14, 15)]), BRASS, flat=4, rim=False)
    I.add(poly([(15.5, 15), (17, 15), (15.5, 19.2), (14, 15)]), RED, flat=3, rim=False)
    I.line(rect(9, 24, 21, 25), BRASS, 3)
    return I


# ================================================================================================ skills
def heart(cx, cy, s):
    return circle(cx - s * 0.5, cy - s * 0.2, s * 0.62) | circle(cx + s * 0.5, cy - s * 0.2, s * 0.62) | \
        poly([(cx - s * 1.08, cy), (cx + s * 1.08, cy), (cx, cy + s * 1.25)])


@icon('marksmanship')
def marksmanship():
    I = Icon()
    I.add(ring(16, 16, 10.2, 13.2), STEEL, shade=1.0)
    for m in (rect(14.5, 2, 17.5, 9.5), rect(14.5, 22.5, 17.5, 30), rect(2, 14.5, 9.5, 17.5), rect(22.5, 14.5, 30, 17.5)):
        I.add(m, STEEL, shade=0.6, edge=True)
    I.line(rect(15.5, 9, 16.5, 23), INK, 1)
    I.line(rect(9, 15.5, 23, 16.5), INK, 1)
    I.add(heart(16, 16.2, 3.6), BLOOD, shade=0.9, edge=True)
    I.px(14, 14, BLOOD[4])
    return I


def eye_shape(cx, cy, w, h):
    return ellipse(cx, cy - h * 0.9, w, h * 1.9) & ellipse(cx, cy + h * 0.9, w, h * 1.9)


@icon('stalking')
def stalking():
    I = Icon()
    I.add(eye_shape(16, 12, 11.5, 4.2), WHITE, shade=0.5)
    I.add(circle(16, 12, 4.2), WOOD, shade=0.9)
    I.add(circle(16, 12, 2.0), INK, flat=0)
    I.px(14, 10, WHITE[4])
    I.add(eye_shape(16, 12, 11.5, 4.2) & ~eye_shape(16, 12.6, 10.5, 3.6) & (CY < 12), LEATHER, flat=1)
    blades = [((5, 31), (3, 17), 3.0), ((9, 31), (10, 14), 3.2), ((13, 31), (13.5, 18.5), 2.8), ((17, 31), (19, 15.5), 3.2),
              ((21, 31), (23, 18), 3.0), ((25, 31), (28.5, 14.5), 3.2), ((29, 31), (30, 21), 2.6)]
    for k, ((x0, y0), (x1, y1), w) in enumerate(blades):
        mid = ((x0 + x1) / 2 + (1.5 if k % 2 else -1.5), (y0 + y1) / 2)
        I.add(taper(bez((x0, y0), mid, (x1, y1), n=10), w, 0.8), GREEN if k % 2 else OLIVE, shade=0.8, edge=True)
    return I


def cloven(I, cx, cy, L, W, gap=0.9, point=0.6, rot=0.0, rmp=None, dew=None, splay=0.0):
    """A cloven-hoof print pressed into the ground (concave light)."""
    for sgn in (-1, 1):
        x_in = cx + sgn * gap
        pts = [(x_in + sgn * point * 0.3, cy - L / 2), ]
        pts += bez((x_in + sgn * point, cy - L / 2), (x_in + sgn * (W * 1.15), cy - L * 0.25), (x_in + sgn * W * 1.05, cy + L * 0.42), (x_in + sgn * W * 0.45, cy + L / 2), n=10)
        pts += [(x_in, cy + L * 0.42)]
        pts = rot_pts([(x + sgn * splay * (cy - y) / L, y) for x, y in pts], cx, cy, rot)
        I.add(poly(pts), rmp, shade=0.8, concave=True, light=(0.7, 0.7), sep=False)
    if dew:
        dx, dy, r = dew
        for sgn in (-1, 1):
            p = rot_pts([(cx + sgn * dx, cy + dy)], cx, cy, rot)[0]
            I.add(ellipse(p[0], p[1], r * 0.8, r), rmp, shade=0.5, concave=True, light=(0.7, 0.7), sep=False)


def dark(r, k=1):
    return [r[0]] * k + r[:5 - k]


@icon('tracking')
def tracking():
    I = Icon()
    I.add(ellipse(16, 17, 14.5, 13.5), MUD, shade=0.7)
    cloven(I, 10.5, 21.5, 11, 3.6, rmp=dark(MUD, 2), rot=-8)
    cloven(I, 21.5, 11.5, 11, 3.6, rmp=dark(MUD, 2), rot=-8)
    I.add(circle(23, 24.5, 1.6) | poly([(21.6, 24.2), (24.4, 24.2), (23, 21.2)]), BLOOD, shade=0.6)
    I.add(circle(26.8, 20.5, 1.0), BLOOD, flat=2)
    return I


@icon('butchery')
def butchery():
    I = Icon()
    I.add(taper([(3.5, 28.5), (12, 20)], 5.0, 4.2), WOOD, shade=1.0)
    for t in (0.33, 0.66):
        x, y = 3.5 + (12 - 3.5) * t, 28.5 + (20 - 28.5) * t
        I.line(circle(x, y, 0.7), BRASS, 3)
    I.add(seg(11.5, 20.5, 14, 18, 5.4), BRASS, shade=0.9, edge=True)
    blade = [(13, 18.5), (15, 16.5)] + bez((15, 16.5), (21, 9), (26, 5), (30, 3.5), n=10) + bez((30, 3.5), (28, 9), (24, 15), (16.5, 20.5), n=10)
    I.add(poly(blade), STEEL, shade=1.0, edge=True)
    I.line(path(bez((16, 18.4), (22, 11.5), (26.5, 7.5), n=8), 1), STEEL, 4)
    I.add(circle(25.6, 10.6, 1.4), STEEL, flat=0)
    return I


@icon('woodcraft')
def woodcraft():
    I = Icon()
    I.add(ellipse(16, 27, 12.5, 3.6), LOG, shade=0.8)
    I.add(rect(4, 18, 28, 27.5), LOG, shade=0.9, edge=True)
    I.add(ellipse(16, 18, 12, 4.2), WOOD, shade=0.4, edge=True)
    I.line(ring(16, 18, 2.8, 3.6) & ellipse(16, 18, 12, 4.2) & ~ellipse(16, 18, 6, 2.0), WOOD, 1)
    I.line(ellipse(16, 18, 1.2, 0.6), WOOD, 1)
    for x in (7, 12, 20, 25):
        I.line(rect(x, 22, x + 1, 27), LOG, 1)
    I.add(taper([(6, 6), (17, 17)], 2.8, 2.6), WOOD, shade=0.8, edge=True)
    head = poly([(14, 10.5), (17, 7.5), (22.5, 9), (23.5, 14.5), (20.5, 17.5)])
    I.add(head, STEEL, shade=1.0, edge=True)
    I.line(path([(21.4, 9.6), (23.0, 14.4)], 1), STEEL, 4)
    return I


# ================================================================================================ ranks
def medal(I, metal, ribbon, ribbon2=None):
    for sgn, col in ((-1, ribbon), (1, ribbon2 or ribbon)):
        I.add(poly([(16 + sgn * 1, 20), (16 + sgn * 8, 20), (16 + sgn * 9, 31), (16 + sgn * 6, 28.5), (16 + sgn * 3.5, 31.5), (16 + sgn * 1.5, 22)]),
              col, shade=0.7)
    I.add(circle(16, 13.5, 12), metal, shade=1.0, edge=True)
    I.add(ring(16, 13.5, 9.0, 10.0), metal, flat=1, rim=False)


@icon('rank_greenhorn')
def rank_greenhorn():
    I = Icon()
    medal(I, BRONZE, GREEN)
    I.add(seg(16, 20, 16, 13, 1.4), GREEN, flat=1)
    I.add(ellipse(12.5, 12.5, 3.6, 1.9, 30), GREEN, shade=0.8, edge=True)
    I.add(ellipse(19.5, 10.8, 3.8, 2.0, -35), GREEN, shade=0.8, edge=True)
    I.add(ellipse(16, 21, 4, 1.2) & circle(16, 13.5, 8.8), BRONZE, flat=1)
    return I


@icon('rank_woodsman')
def rank_woodsman():
    I = Icon()
    medal(I, BRONZE, LEATHER)
    I.add(taper([(10.5, 19.5), (19, 8.5)], 2.0, 1.8), WOOD, shade=0.6, edge=True)
    I.add(poly([(15.5, 8.5), (18.5, 5.8), (22.5, 8), (22, 12), (19, 12.5)]), STEEL, shade=0.9, edge=True)
    return I


@icon('rank_tracker')
def rank_tracker():
    I = Icon()
    medal(I, SILVER, RED)
    I.add(circle(16, 13.5, 8.6), SILVER, flat=1, rim=False)
    cloven(I, 16, 13.5, 13, 4.4, gap=1.0, point=0.9, rmp=dark(STEEL, 1))
    return I


@icon('rank_guide')
def rank_guide():
    I = Icon()
    medal(I, SILVER, SLATE)
    I.add(star(16, 13.5, 2.2, 8.2, n=4), RED, shade=0.8, edge=True)
    I.add(star(16, 13.5, 1.6, 5.4, n=4, rot=-45), SILVER, flat=3, edge=True)
    I.add(circle(16, 13.5, 1.2), BRASS, flat=3)
    return I


@icon('rank_master')
def rank_master():
    I = Icon()
    medal(I, GOLD, RED, RED)
    for sgn in (-1, 1):
        b = bez((16 + sgn * 1.5, 18), (16 + sgn * 6.5, 17), (16 + sgn * 7.5, 11), (16 + sgn * 5.5, 6.5), n=12)
        I.add(taper(b, 2.4, 1.2), BRONZE, shade=0.6, edge=True)
        I.add(taper([(16 + sgn * 6.6, 14.6), (16 + sgn * 3.2, 11.2)], 1.7, 1.0), BRONZE, shade=0.4, edge=True)
        I.add(taper([(16 + sgn * 7.4, 10.8), (16 + sgn * 4.6, 7.2)], 1.6, 1.0), BRONZE, shade=0.4, edge=True)
    I.add(ellipse(16, 18.6, 2.0, 2.2), BRONZE, flat=2, edge=True)
    return I


@icon('rank_legend')
def rank_legend():
    I = Icon()
    medal(I, GOLD, BLAZE, RED)
    import math as _m
    for k in range(9):
        for sgn in (-1, 1):
            a = _m.radians(100 + k * 17)
            x = 16 + sgn * _m.cos(a) * -9.5
            y = 13.5 + _m.sin(a) * 9.5
            I.add(ellipse(x, y, 1.9, 1.0, sgn * (k * 17 + 10)), GREEN, flat=3 if k % 2 else 2, edge=True)
    I.add(star(16, 13.5, 3.0, 6.8), GOLD, flat=4, edge=True)
    I.px(15, 11, WHITE[4])
    return I


# ================================================================================================ checklist categories / entries
@icon('hunting')
def hunting():
    I = Icon()
    bow = bez((10, 3), (25, 9), (25, 23), (10, 29), n=20)
    I.add(taper(bow[:11], 1.8, 3.0) | taper(bow[10:], 3.0, 1.8), WOOD, shade=0.9)
    I.add(rect(20.5, 13.5, 24, 18.5), LEATHER, flat=1, edge=True)
    I.line(path([(10, 3), (10, 29)], 1), WHITE, 3)
    I.add(seg(3, 16, 27, 16, 1.4), WOOD, flat=3, edge=True)
    I.add(poly([(26, 13.5), (31, 16), (26, 18.5)]), STEEL, shade=0.8, edge=True)
    I.add(poly([(2, 13), (7, 15.4), (7, 16.6), (2, 19), (4, 16)]), RED, shade=0.6, edge=True)
    return I


@icon('world')
def world():
    I = Icon()
    disc = circle(16, 16, 14)
    I.add(disc, SKY, shade=0.8, rim=False)
    I.add(circle(22, 9, 3), FLAME, flat=4, rim=False)
    I.add(poly([(2, 24), (11, 9), (17, 18), (21, 13), (31, 26), (31, 31), (1, 31)]) & disc, STONE, shade=0.8, edge=True)
    I.add(poly([(8.6, 13), (11, 9), (13.4, 12.6), (12, 12), (10.5, 13.4)]) & disc, SNOW, flat=4, rim=False)
    I.add(poly([(19.6, 15), (21, 13), (22.6, 15.2), (21.2, 14.6)]) & disc, SNOW, flat=4, rim=False)
    I.add(poly([(1, 25), (8, 21), (16, 24), (24, 20), (31, 23), (31, 31), (1, 31)]) & disc, GREEN, shade=0.8, edge=True)
    for x, h in ((6, 7), (10, 9), (24, 8), (27.5, 6)):
        I.add(poly([(x, 27 - h), (x + 2.8, 27.5), (x - 2.8, 27.5)]) & disc, GREEN, flat=1, edge=True)
    return I


@icon('camp')
def camp():
    I = Icon()
    I.add(ellipse(16, 28.5, 14, 2.6), GREEN, shade=0.6)
    I.add(seg(16, 3, 16, 9, 1.2), WOOD, flat=1)
    I.add(poly([(16.5, 3), (23, 4.8), (16.5, 6.6)]), RED, shade=0.5, edge=True)
    I.add(poly([(16, 7), (29.5, 28), (2.5, 28)]), CANVAS, shade=0.9, edge=True)
    I.add(poly([(16, 9.5), (21.5, 28), (10.5, 28)]), INK, flat=2, edge=True)
    I.add(poly([(16, 9.5), (19, 28), (21.5, 28)]), CANVAS, flat=3, edge=True)
    I.line(path([(16, 7), (16, 28)], 1), CANVAS, 0)
    return I


@icon('blood')
def blood():
    I = Icon()
    for cx, cy, s in ((11, 20, 6.0), (22, 12.5, 4.6), (22.5, 25, 3.2)):
        I.add(circle(cx, cy, s) | poly([(cx - s * 0.97, cy - s * 0.3), (cx + s * 0.97, cy - s * 0.3), (cx, cy - s * 2.1)]), BLOOD, shade=0.9, edge=True)
        I.px(int(cx - s * 0.45), int(cy - s * 0.3), BLOOD[4])
    return I


@icon('rub')
def rub():
    I = Icon()
    I.add(ellipse(16, 28.5, 12, 2.6), MUD, shade=0.6)
    I.add(rect(13, 2, 19, 29), LOG, shade=1.0)
    I.add(poly([(13, 11), (19, 9), (19, 24), (13, 26)]), BONE, shade=0.9, edge=True)
    for y0 in (12, 16, 20):
        I.line(path([(13.5, y0 + 1.5), (18.5, y0 - 0.5)], 1), DEER, 1)
    for (x0, y0, x1, y1) in ((13, 10.5, 8, 7), (13, 25.5, 9, 27.5), (19, 8.5, 23.5, 6.5), (19, 23.5, 24, 25)):
        I.add(taper([(x0, y0), (x1, y1)], 1.8, 0.8), LOG, flat=3, edge=True)
    I.add(seg(19, 5, 26, 1.5, 1.6), LOG, flat=2)
    I.add(ellipse(27.5, 2.6, 2.4, 1.3, -30), GREEN, flat=3)
    return I


@icon('snowflake')
def snowflake():
    I = Icon()
    import math as _m
    arms = np.zeros((N, N), bool)
    for k in range(6):
        a = _m.radians(k * 60 - 90)
        ex, ey = 16 + _m.cos(a) * 13.5, 16 + _m.sin(a) * 13.5
        arms |= seg(16, 16, ex, ey, 2.4)
        for t, l in ((0.55, 4.0), (0.8, 2.8)):
            px_, py_ = 16 + _m.cos(a) * 13.5 * t, 16 + _m.sin(a) * 13.5 * t
            for d in (-1, 1):
                b = a + d * _m.radians(50)
                arms |= seg(px_, py_, px_ + _m.cos(b) * l, py_ + _m.sin(b) * l, 1.6)
    I.add(arms, ICE, shade=0.9)
    I.add(star(16, 16, 2.0, 4.0, n=6), ICE, flat=4, rim=False)
    return I


@icon('winter')
def winter():
    I = Icon()
    I.add(rect(12, 3, 20, 25), WHITE, shade=0.6)
    I.add(circle(16, 25, 5.4), WHITE, shade=0.6)
    I.add(rect(14.6, 7, 17.4, 25), SLATE, flat=1, edge=True)
    I.add(rect(14.6, 18, 17.4, 25) | circle(16, 25, 3.4), SLATE, shade=0.8, edge=True)
    for y in (7, 10, 13, 16):
        I.line(rect(18, y, 20, y + 1), INK, 2)
    for cx, cy in ((6, 8), (26, 13), (7, 19)):
        I.add(seg(cx - 2.4, cy, cx + 2.4, cy, 1.2) | seg(cx, cy - 2.4, cx, cy + 2.4, 1.2) | seg(cx - 1.7, cy - 1.7, cx + 1.7, cy + 1.7, 1.0) | seg(cx - 1.7, cy + 1.7, cx + 1.7, cy - 1.7, 1.0), ICE, flat=3)
    return I


@icon('boot')
def boot():
    I = Icon()
    shape = poly([(8, 3), (18, 3), (18.5, 15), (24, 18.5), (28.5, 21), (29.5, 26), (5, 26), (6.5, 16)])
    I.add(shape, LEATHER, shade=1.0)
    I.add(rect(5, 25, 30, 29.5), INK, shade=0.6, edge=True)
    for k in range(4):
        y = 6 + k * 3
        I.line(rect(15, y, 18, y + 1), BRASS, 3)
    I.add(rect(7.5, 3, 18.5, 6), LEATHER, flat=3, edge=True)
    I.line(path([(18.5, 15.5), (23.5, 19), (28.5, 21.5)], 1), LEATHER, 1)
    for x in range(7, 29, 3):
        I.px(x, 28, INK[4])
    return I


@icon('podium')
def podium():
    I = Icon()
    for sgn, col in ((-1, RED), (1, SLATE)):
        I.add(poly([(16 + sgn * 1, 17), (16 + sgn * 7, 17), (16 + sgn * 9.5, 31), (16 + sgn * 6.4, 28.6), (16 + sgn * 4, 31.5), (16 + sgn * 1.5, 19)]), col, shade=0.7)
    import math as _m
    pts = []
    for k in range(32):
        a = _m.radians(k * 360 / 32)
        r = 12 if k % 2 == 0 else 10.2
        pts.append((16 + _m.cos(a) * r, 13 + _m.sin(a) * r))
    I.add(poly(pts), RED, shade=0.9, edge=True)
    I.add(circle(16, 13, 7), GOLD, shade=1.0, edge=True)
    I.add(rect(15, 9, 17.2, 17), GOLD, flat=1, rim=False)
    I.add(rect(13.6, 9, 15.6, 11), GOLD, flat=1, rim=False)
    I.add(rect(13.6, 16, 18.4, 17.5), GOLD, flat=1, rim=False)
    return I


def canid(I, cx, cy, s, rmp, claws=True):
    pad = poly([(cx - 3.2 * s, cy + 1.2 * s), (cx, cy - 1.4 * s), (cx + 3.2 * s, cy + 1.2 * s), (cx + 2.2 * s, cy + 3.4 * s),
                (cx + 0.6 * s, cy + 2.8 * s), (cx, cy + 3.6 * s), (cx - 0.6 * s, cy + 2.8 * s), (cx - 2.2 * s, cy + 3.4 * s)])
    I.add(pad, rmp, shade=0.7, concave=True, light=(0.7, 0.7), sep=False)
    for dx, dy, r in ((-1.3, -4.6, 1.25), (1.3, -4.6, 1.25), (-3.6, -2.2, 1.15), (3.6, -2.2, 1.15)):
        I.add(ellipse(cx + dx * s, cy + dy * s, r * s * 0.85, r * s * 1.1), rmp, shade=0.6, concave=True, light=(0.7, 0.7), sep=False)
        if claws:
            I.add(ellipse(cx + dx * s * 1.12, cy + (dy - 1.9) * s, 0.45 * s + 0.25, 0.6 * s), rmp, flat=0, sep=False)


def felid(I, cx, cy, s, rmp, claws=False):
    pad = poly([(cx - 3.4 * s, cy + 0.6 * s), (cx - 1.6 * s, cy - 1.2 * s), (cx + 1.6 * s, cy - 1.2 * s), (cx + 3.4 * s, cy + 0.6 * s),
                (cx + 3.0 * s, cy + 3.0 * s), (cx + 1.6 * s, cy + 2.6 * s), (cx + 0.8 * s, cy + 3.4 * s), (cx - 0.2 * s, cy + 2.6 * s),
                (cx - 1.2 * s, cy + 3.4 * s), (cx - 2.0 * s, cy + 2.6 * s), (cx - 3.2 * s, cy + 3.0 * s)])
    I.add(pad, rmp, shade=0.7, concave=True, light=(0.7, 0.7), sep=False)
    for dx, dy, r in ((-0.9, -4.4, 1.2), (1.9, -3.9, 1.2), (-3.6, -2.3, 1.1), (4.0, -1.6, 1.05)):
        I.add(ellipse(cx + dx * s, cy + dy * s, r * s, r * s * 1.1), rmp, shade=0.6, concave=True, light=(0.7, 0.7), sep=False)
        if claws:
            I.add(ellipse(cx + dx * s * 1.1, cy + (dy - 1.8) * s, 0.4 * s + 0.25, 0.55 * s), rmp, flat=0, sep=False)


def bear(I, cx, cy, s, rmp, claw=0.0, fur=False):
    pad = poly([(cx - 4.0 * s, cy - 0.4 * s), (cx - 1.5 * s, cy - 1.2 * s), (cx + 1.5 * s, cy - 1.2 * s), (cx + 4.0 * s, cy - 0.4 * s),
                (cx + 3.0 * s, cy + 2.6 * s), (cx + 0.6 * s, cy + 3.0 * s), (cx - 2.6 * s, cy + 2.4 * s)])
    I.add(pad, rmp, shade=0.7, concave=True, light=(0.7, 0.7), sep=False)
    for k in range(5):
        if k == 5:
            break
        ang = -150 + k * 30
        import math as _m
        a = _m.radians(ang)
        tx, ty = cx + _m.cos(a) * 4.6 * s, cy + 0.6 * s + _m.sin(a) * 4.8 * s
        r = (0.75 if k in (0, 4) else 0.9) * s
        I.add(ellipse(tx, ty, r, r * 1.05), rmp, shade=0.5, concave=True, light=(0.7, 0.7), sep=False)
        if claw > 0:
            I.add(seg(tx + _m.cos(a) * r * 1.4, ty + _m.sin(a) * r * 1.4, tx + _m.cos(a) * (r + claw * s), ty + _m.sin(a) * (r + claw * s), 0.9), rmp, flat=0, sep=False)
    if fur:
        for k in range(10):
            import math as _m
            a = _m.radians(k * 36)
            I.px(int(cx + _m.cos(a) * 5.4 * s), int(cy + 0.8 * s + _m.sin(a) * 4.6 * s), rmp[1])


def bird(I, cx, cy, s, rmp, web=False):
    toes = [(-50, 6.0), (0, 7.0), (50, 6.0)]
    import math as _m
    m = np.zeros((N, N), bool)
    ends = []
    for ang, l in toes:
        a = _m.radians(ang - 90)
        ex, ey = cx + _m.cos(a) * l * s, cy + _m.sin(a) * l * s
        ends.append((ex, ey))
        m |= taper([(cx, cy), (ex, ey)], 1.7 * s, 1.0 * s)
    if web:
        m |= taper([(cx, cy), (cx, cy + 1.0 * s)], 1.6 * s, 1.4 * s)
    if web:
        e0, e1, e2 = ends
        def pull(a, b, k=0.42):
            mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
            return (mx + (cx - mx) * k, my + (cy - my) * k)
        web_m = poly([(cx, cy + 0.6 * s), e0, pull(e0, e1), e1, pull(e1, e2), e2])
        I.add(web_m & ~m, [rmp[3], rmp[3], rmp[3], rmp[4], rmp[4]], shade=0.5, concave=True, light=(0.7, 0.7), sep=False)
    else:
        m |= taper([(cx, cy), (cx, cy + 3.2 * s)], 1.6 * s, 1.0 * s)
    I.add(m, rmp, shade=0.6, concave=True, light=(0.7, 0.7), sep=False)


def ground(I, rmp):
    I.add(ellipse(16, 16, 14.2, 14.2), rmp, shade=0.7)


@icon('predator')
def predator():
    I = Icon()
    ground(I, MUD)
    canid(I, 14, 17.5, 1.75, dark(MUD, 2))
    I.add(circle(24.5, 25, 1.6) | poly([(23.1, 24.7), (25.9, 24.7), (24.5, 21.8)]), BLOOD, shade=0.6)
    I.add(circle(7, 26, 1.0), BLOOD, flat=2)
    return I


@icon('skull')
def skull():
    I = Icon()
    for sgn in (-1, 1):
        I.add(taper(bez((16 + sgn * 4, 8), (16 + sgn * 9, 6), (16 + sgn * 11, 1.5), n=8), 2.6, 1.4), BONE, shade=0.6)
    s = poly([(9.5, 6), (22.5, 6), (24.5, 11), (21, 15), (19, 29.5), (13, 29.5), (11, 15), (7.5, 11)])
    I.add(s, BONE, shade=1.0, edge=True)
    for sgn in (-1, 1):
        I.add(ellipse(16 + sgn * 4.6, 11.5, 2.2, 1.8), INK, flat=1)
    I.add(poly([(14.6, 17), (17.4, 17), (17, 26), (15, 26)]), BONE, flat=1, rim=False)
    I.add(ellipse(16, 28, 2.4, 1.2), BONE, flat=1, rim=False)
    return I


@icon('antlers')
def antlers():
    I = Icon()
    for sgn in (-1, 1):
        b = bez((16 + sgn * 2, 24), (16 + sgn * 11, 22), (16 + sgn * 14, 12), (16 + sgn * 10, 3), n=20)
        I.add(taper(b, 3.4, 1.6), BONE, shade=0.8, edge=True)
        for (t, l, ang) in ((0.38, 7, -12), (0.62, 7.5, 0), (0.82, 5.5, 18)):
            px_, py_ = b[int(t * 20)]
            I.add(taper([(px_, py_), (px_ - sgn * 1.2 + sgn * ang * 0.1, py_ - l)], 2.4, 1.1), BONE, shade=0.6, edge=True)
        I.add(taper([b[3], (16 + sgn * 4.5, 16.5)], 2.0, 1.0), BONE, shade=0.5, edge=True)
    I.add(ellipse(16, 25.5, 4.6, 3.4), BONE, shade=0.9, edge=True)
    I.line(ellipse(16, 24.8, 2.6, 1.2), BONE, 1)
    return I


@icon('leaf')
def leaf():
    I = Icon()
    lp = [(16, 2), (18.4, 8), (24.6, 5.4), (23, 11.4), (29.6, 12.6), (24.6, 17), (25.8, 21.6), (18.2, 20.2), (17.2, 26),
          (14.8, 26), (13.8, 20.2), (6.2, 21.6), (7.4, 17), (2.4, 12.6), (9, 11.4), (7.4, 5.4), (13.6, 8)]
    I.add(poly(lp), BLAZE, shade=1.0, edge=True)
    I.add(seg(16, 22, 16, 31, 1.6), WOOD, flat=2)
    I.line(path([(16, 5), (16, 25)], 1), BLAZE, 1)
    I.line(path([(16, 17), (23, 12)], 1), BLAZE, 1)
    I.line(path([(16, 17), (9, 12)], 1), BLAZE, 1)
    return I


@icon('quill')
def quill():
    I = Icon()
    I.add(rect(5, 21, 15, 29.5), INK, shade=0.6)
    I.add(rect(7, 18.5, 13, 21.5), INK, flat=3, edge=True)
    I.add(rect(4, 20.5, 16, 22), INK, flat=4, edge=True)
    vane = poly(bez((11, 21), (13, 12), (21, 5), (30, 1.5), n=10) + bez((30, 1.5), (27, 9), (20, 15), (12.5, 20), n=10))
    I.add(vane, WHITE, shade=1.0, edge=True)
    I.line(path(bez((11, 21), (17, 12), (23, 6.5), (29, 2.5), n=10), 1), WHITE, 1)
    for k in range(3):
        I.line(path([(15 + k * 4, 13 - k * 3.5), (17 + k * 4, 14 - k * 3.5)], 1), WHITE, 1)
    return I


# ================================================================================================ species (prints in the ground)
def species_icon(gr, draw):
    I = Icon()
    ground(I, gr)
    draw(I, dark(gr, 2))
    return I


SPECIES = {
    'whitetail': (MUD, lambda I, r: cloven(I, 16, 16, 15, 4.2, rmp=r, splay=0.4)),
    'elk': (MUD, lambda I, r: cloven(I, 16, 16, 16, 5.4, point=1.6, rmp=r)),
    'moose': (SOIL, lambda I, r: cloven(I, 16, 14, 17, 4.8, rmp=r, dew=(4.2, 10.5, 1.4), splay=0.6)),
    'pronghorn': (SAND, lambda I, r: cloven(I, 16, 16, 13, 3.8, gap=0.6, rmp=r, splay=-0.3)),
    'bison': (MUD, lambda I, r: cloven(I, 16, 16, 14, 6.4, point=2.4, gap=1.0, rmp=r)),
    'boar': (MUD, lambda I, r: cloven(I, 16, 14, 11, 4.6, point=1.8, rmp=r, dew=(6.0, 8.8, 1.5))),
    'coyote': (SAND, lambda I, r: canid(I, 16, 18, 1.6, r)),
    'wolf': (MUD, lambda I, r: canid(I, 16, 18.5, 1.95, r)),
    'cougar': (MUD, lambda I, r: felid(I, 16, 18, 1.85, r)),
    'panther': (SOIL, lambda I, r: felid(I, 16, 18, 1.75, r)),
    'cheetah': (SAND, lambda I, r: felid(I, 16, 18.5, 1.6, r, claws=True)),
    'lion': (SAND, lambda I, r: felid(I, 16, 18, 2.05, r)),
    'black_bear': (MUD, lambda I, r: bear(I, 16, 18.5, 2.0, r, claw=0.9)),
    'grizzly': (SAND, lambda I, r: bear(I, 16, 19.5, 2.0, r, claw=2.2)),
    'polar_bear': (SNOW, lambda I, r: bear(I, 16, 18.5, 2.1, r, claw=0.8, fur=True)),
    'grouse': (MUD, lambda I, r: bird(I, 16, 21, 2.0, r)),
    'duck': (SILT, lambda I, r: bird(I, 16, 20, 2.1, r, web=True)),
}
for _k, (_g, _d) in SPECIES.items():
    ICONS['sp_' + _k] = (lambda g=_g, d=_d: species_icon(g, d))



@icon('glass')
def glass():
    I = Icon()
    for cx in (9.5, 22.5):
        I.add(rect(cx - 5, 9, cx + 5, 25), INK, shade=0.9)
        I.add(rect(cx - 5, 9, cx + 5, 11), INK, flat=4, rim=False)
        I.add(ellipse(cx, 26, 6.2, 3.2), INK, shade=0.6, edge=True)
        I.add(ellipse(cx, 26, 4.4, 2.0), SLATE, shade=0.8, rim=False)
        I.px(int(cx - 2), 25, SLATE[4])
    I.add(rect(13, 12, 19, 18), STEEL, shade=0.7, edge=True)
    I.add(circle(16, 10, 2.4), STEEL, shade=0.6, edge=True)
    I.add(rect(4.5, 4, 14.5, 9.5), GREEN, shade=0.6, edge=True)
    I.add(rect(17.5, 4, 27.5, 9.5), GREEN, shade=0.6, edge=True)
    return I


@icon('wind')
def wind():
    I = Icon()
    I.add(rect(4, 11, 13, 30), WHITE, shade=0.8)
    I.add(rect(5.5, 7, 11.5, 11.5), WHITE, shade=0.6, edge=True)
    I.add(rect(6.5, 3, 10.5, 7.5), BLAZE, shade=0.6, edge=True)
    I.add(rect(4, 18, 13, 24), GREEN, shade=0.6, edge=True)
    for k, (y, l) in enumerate(((6, 13), (13, 15), (20, 12))):
        p = bez((15, y + 2), (19, y - 1), (23, y + 3), (15 + l, y), n=12)
        I.add(path(p, 1.6), SKY, flat=3)
        I.add(path(bez((15 + l - 3, y + 0.6), (15 + l + 1, y - 2.6), (15 + l - 2.5, y - 3.2), n=6), 1.4), SKY, flat=3)
    return I



@icon('fish')
def fish():
    I = Icon()
    TROUT = ramp('#26302a', '#3f5446', '#5f7d62', '#8fae84', '#c9dcb0')
    body = poly(bez((5, 17), (10, 8), (20, 8), (25, 15), n=12) + [(30, 10), (29.5, 17), (30.5, 24), (25, 18)] + bez((25, 18), (20, 25), (10, 25), (5, 17), n=12))
    I.add(body, TROUT, shade=1.0)
    I.add(poly(bez((6, 18), (11, 22.5), (19, 23), (24.5, 18), n=10)) & body, WHITE, flat=3, rim=False)
    I.add(path(bez((7, 16.5), (13, 15), (19, 15.5), (24.5, 16.5), n=10), 1.4) & body, RED, flat=3, rim=False)
    for x, y in ((12, 12), (16, 11.5), (19, 13), (14, 14), (21, 12)):
        I.px(int(x), int(y), INK[1])
    I.add(poly([(13, 9.5), (17, 5.5), (19, 9.5)]), TROUT, flat=1, edge=True)
    I.add(circle(8.6, 15.6, 1.3), INK, flat=0)
    I.px(8, 15, WHITE[4])
    I.line(path([(10.5, 13.5), (11.5, 18.5)], 1), TROUT, 0)
    return I


# ================================================================================================ [1.1.7] journal pages
@icon('soon')
def soon():
    """Coming Soon: a brass hourglass, sand running"""
    I = Icon()
    I.add(rect(7, 3, 25, 6), WOOD, shade=0.9, light=UP)
    I.add(rect(7, 26, 25, 29), WOOD, shade=0.9, light=UP)
    I.add(rect(8, 6, 10, 26), BRASS, flat=2, rim=False)
    I.add(rect(22, 6, 24, 26), BRASS, flat=2, rim=False)
    glass = poly([(11, 6.5), (21, 6.5), (21, 9), (17, 15.5), (17, 16.5), (21, 23), (21, 25.5), (11, 25.5), (11, 23), (15, 16.5), (15, 15.5), (11, 9)])
    I.add(glass, ICE, shade=0.5, edge=True)
    I.add(poly([(12.5, 8), (19.5, 8), (19.5, 9.2), (16, 13.5), (12.5, 9.2)]), SAND, flat=3, rim=False)
    I.add(poly([(11.8, 25), (20.2, 25), (20.2, 23.6), (16, 20), (11.8, 23.6)]), SAND, shade=0.6, rim=False)
    I.line(path([(16, 15), (16, 21)], 0.9), SAND, 3)
    I.px(13, 11, WHITE[4])
    return I


@icon('path')
def hunters_path():
    """Hunter's Path: a field compass"""
    I = Icon()
    I.add(circle(16, 17, 13), BRASS, shade=1.0)
    I.add(ring(16, 3.2, 1.4, 2.8), BRASS, shade=0.6)
    I.add(circle(16, 17, 10.4), WHITE, shade=0.35, edge=True)
    import math as _m
    for a in range(8):
        x = 16 + _m.cos(a * _m.pi / 4) * 8.6
        y = 17 + _m.sin(a * _m.pi / 4) * 8.6
        I.px(int(x), int(y), INK[2] if a % 2 else INK[0])
    I.add(poly([(16, 8.5), (18.6, 17), (13.4, 17)]), RED, flat=2, edge=True)
    I.add(poly([(16, 25.5), (18.6, 17), (13.4, 17)]), STEEL, flat=2, edge=True)
    I.add(circle(16, 17, 1.4), BRASS, flat=3, rim=False)
    return I


SHEET_ORDER = None


def render_all():
    out = {}
    for name, f in ICONS.items():
        out[name] = f().render()
    return out


BGS = [(0x49, 0x37, 0x2B), (0xEA, 0xE3, 0xD0), (0x2E, 0x24, 0x1B)]


def sheet(imgs, path_out, scales=(1, 2, 4), cols=8):
    names = list(imgs)
    rows = (len(names) + cols - 1) // cols
    blocks = []
    for sc in scales:
        cell = 32 * sc + 8
        w = cols * cell * len(BGS) + 16 * (len(BGS) - 1)
        h = rows * (cell + 10) + 8
        blk = Image.new('RGBA', (w, h), (30, 26, 22, 255))
        d = ImageDraw.Draw(blk)
        for bi, bg in enumerate(BGS):
            ox = bi * (cols * cell + 16)
            d.rectangle([ox, 0, ox + cols * cell - 1, h - 1], fill=bg)
            for i, n in enumerate(names):
                x = ox + (i % cols) * cell + 4
                y = (i // cols) * (cell + 10) + 4
                im = imgs[n].resize((32 * sc, 32 * sc), Image.NEAREST)
                blk.alpha_composite(im, (x, y))
                if sc == 4 and bi == 1:
                    d.text((x, y + 32 * sc + 1), n, fill=(60, 50, 40))
        blocks.append(blk)
    W = max(b.width for b in blocks)
    H = sum(b.height for b in blocks) + 10 * len(blocks)
    S = Image.new('RGBA', (W, H), (30, 26, 22, 255))
    y = 0
    for b in blocks:
        S.alpha_composite(b, (0, y))
        y += b.height + 10
    S.save(path_out)





def big(imgs, path_out, sc=4, cols=8, bg=(0xEA, 0xE3, 0xD0)):
    names = list(imgs)
    cell = 32 * sc + 12
    rows = (len(names) + cols - 1) // cols
    S = Image.new('RGBA', (cols * cell, rows * (cell + 8)), bg + (255,))
    d = ImageDraw.Draw(S)
    for i, n in enumerate(names):
        x = (i % cols) * cell + 6
        y = (i // cols) * (cell + 8) + 4
        S.alpha_composite(imgs[n].resize((32 * sc, 32 * sc), Image.NEAREST), (x, y))
        d.text((x, y + 32 * sc + 2), n, fill=(60, 50, 40))
    S.save(path_out)


def multi(imgs, path_out, names=None, scales=(1, 2), cols=12):
    """Normal + bright variants at 1x and 2x on the three journal backgrounds (what players actually see)."""
    names = names or list(imgs)
    rows = (len(names) + cols - 1) // cols
    pieces = []
    for sc in scales:
        for bright in (False, True):
            cell = 32 * sc + 4
            for bg in BGS:
                P = Image.new('RGBA', (cols * cell + 4, rows * cell + 4), bg + (255,))
                for i, n in enumerate(names):
                    im = brighten(imgs[n]) if bright else imgs[n]
                    P.alpha_composite(im.resize((32 * sc, 32 * sc), Image.NEAREST), (4 + (i % cols) * cell, 4 + (i // cols) * cell))
                pieces.append(P)
    W = max(p.width for p in pieces) * 3 + 8
    rowsP = [pieces[i:i + 3] for i in range(0, len(pieces), 3)]
    H = sum(r[0].height for r in rowsP) + 4 * len(rowsP)
    S = Image.new('RGBA', (W, H), (20, 18, 16, 255))
    y = 0
    for r in rowsP:
        x = 0
        for p in r:
            S.alpha_composite(p, (x, y))
            x += p.width + 4
        y += r[0].height + 4
    S.save(path_out)


def write_atlas(repo, imgs):
    names = list(imgs)
    assert len(names) <= 128, len(names)
    A = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    for i, n in enumerate(names):
        x, y = (i % 16) * 32, (i // 16) * 32
        A.alpha_composite(imgs[n], (x, y))
        A.alpha_composite(brighten(imgs[n]), (x, y + 256))
    out = os.path.join(repo, 'patch/assets/frontierhunts/textures/gui/journal/icons.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    # [artqa] bleed colour into transparent texels (bilinear / mipmapped sampling) and write the 2x nearest copy that
    # JournalIcons samples for sharp in-between GUI scales
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'artqa'))
    from bleed import bleed_image
    A = bleed_image(A)
    A.save(out, optimize=True)
    A.resize((1024, 1024), Image.NEAREST).save(out.replace('icons.png', 'icons_x2.png'), optimize=True)
    jf = os.path.join(repo, 'src/com/formaworks/frontierhunts/journal/client/JournalIcons.java')
    if os.path.exists(jf):
        src = open(jf).read()
        lines = []
        row = []
        for n in names:
            row.append('"%s"' % n)
            if len(row) == 8:
                lines.append('      ' + ', '.join(row) + ',')
                row = []
        if row:
            lines.append('      ' + ', '.join(row) + ',')
        body = '\n'.join(lines)
        src = re.sub(r'(// GENERATED-NAMES-BEGIN[^\n]*\n).*?([ \t]*// GENERATED-NAMES-END)', lambda m: m.group(1) + body + '\n' + m.group(2), src, flags=re.S)
        open(jf, 'w').write(src)
    return out


if __name__ == '__main__':
    repo = sys.argv[1]
    imgs = render_all()
    print(write_atlas(repo, imgs), len(imgs), 'icons')
    if len(sys.argv) > 2:
        pre = sys.argv[2]
        big(imgs, pre + '_4x.png')
        multi(imgs, pre + '_1x2x.png')
