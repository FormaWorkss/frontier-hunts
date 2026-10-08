#!/usr/bin/env python3
"""[1.1.8] Ranger Services (Ranger Academy dossier) icon atlas in the Hunter's Journal pixel-art style.

python3 tools/academy/dossier_icons.py <repo> [preview.png]
Writes patch/assets/frontierhunts/textures/gui/academy/icons.png: 512 x 512, 64 px cells, 8 per row, in the order of
DossierArt.Icon. Each icon is 32 x 32 pixel art (tools/ledger/px.py, the same palette and shading as the Journal
icons), drawn at 2x so the dossier's 64 px cells stay crisp. Several reuse a Journal icon; the rest are drawn here.
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'ledger'))
from px import *  # noqa
import icons as J  # noqa: the Journal icon set


def sunrise():
    """TIMED: the sun coming up over a ridge"""
    I = Icon()
    I.add(rect(2, 4, 30, 22), SKY, shade=0.8, light=(0.0, 1.0))
    I.add(circle(16, 20, 7.5), FLAME, shade=0.7, edge=True)
    for a in range(-150, -20, 26):
        r = math.radians(a)
        I.add(seg(16 + math.cos(r) * 9.5, 20 + math.sin(r) * 9.5, 16 + math.cos(r) * 12.5, 20 + math.sin(r) * 12.5, 1.6), GOLD, flat=3)
    I.add(poly([(1, 30), (1, 22), (8, 17), (13, 21), (20, 15.5), (31, 23), (31, 30)]), GREEN, shade=0.8, edge=True)
    I.add(poly([(1, 30), (1, 25), (10, 22), (18, 26), (26, 23), (31, 26), (31, 30)]), OLIVE, shade=0.6, edge=True)
    return I


def flag():
    """FLAG: a red pennant on a pole"""
    I = Icon()
    I.add(rect(6, 3, 9, 30), WOOD, shade=0.8, edge=True)
    I.add(circle(7.5, 3.5, 2.2), BRASS, flat=3, edge=True)
    I.add(poly([(9, 5), (28, 9.5), (9, 15)]), RED, shade=0.9, edge=True)
    I.line(path([(10, 9), (22, 9.6)], 1.0), RED, 4)
    I.add(ellipse(7.5, 29.5, 6, 1.6), SOIL, flat=1)
    return I


def cross():
    """CROSS: failed - a red X on a paper tag"""
    I = Icon()
    I.add(rect(5, 5, 27, 27), PAPER, shade=0.6, edge=True)
    I.add(seg(10, 10, 22, 22, 4.2), RED, shade=0.7, edge=True)
    I.add(seg(22, 10, 10, 22, 4.2), RED, shade=0.7, edge=True)
    return I


def check():
    """CHECK: passed - a green tick on a paper tag"""
    I = Icon()
    I.add(rect(5, 5, 27, 27), PAPER, shade=0.6, edge=True)
    I.add(path([(9, 16.5), (14, 21.5), (24, 9.5)], 4.2), GREEN, shade=0.7, edge=True)
    return I


def alert():
    """ALERT: a yellow warning sign"""
    I = Icon()
    I.add(poly([(16, 3), (30, 28), (2, 28)]), GOLD, shade=0.8, edge=True)
    I.add(poly([(16, 7.5), (26.5, 26), (5.5, 26)]), GOLD, shade=0.3, rim=False)
    I.add(rect(14.5, 11, 17.5, 20), INK, flat=1)
    I.add(rect(14.5, 22, 17.5, 24.5), INK, flat=1)
    return I


def grouse():
    """UPLAND: a ruffed grouse flushing, seen from the side"""
    I = Icon()
    I.add(poly([(6, 16.5), (0.8, 12.5), (0.8, 23.5), (6, 21)]), DEER, shade=0.7, edge=True)  # fanned tail
    I.line(rect(2, 13.5, 3.2, 22.8), INK, 1)  # dark tail band
    I.add(ellipse(14.5, 19, 9, 5.6, rot=-12), DEER, shade=0.9, edge=True)  # body
    I.line(path([(9, 21.5), (19, 22.5)], 1.2), BONE, 3)  # pale belly
    for x in (10, 13, 16):
        I.px(x, 20, FUR[1])
    I.add(circle(24, 13.5, 3.8), DEER, shade=0.8, edge=True)  # head
    I.add(poly([(22, 10.5), (23.5, 7.5), (25.8, 10)]), DEER, flat=2)  # crest
    I.add(ellipse(21.6, 16.2, 2.0, 1.4), FUR, flat=0)  # black ruff
    I.add(poly([(27.4, 13), (30.8, 14.2), (27.4, 15.4)]), BONE, flat=1)  # bill
    I.px(25, 12, INK[0])
    I.add(poly([(9, 16), (18, 15), (16, 4), (12.5, 2.5), (8, 6)]), DEER, shade=1.0, edge=True)  # wing raised
    for k, (x0, y0) in enumerate(((9, 7), (11, 5), (13.5, 4))):
        I.line(path([(x0, y0), (x0 + 3.5, y0 + 9)], 1.0), FUR, 1)
    return I


def mallard():
    """WATERFOWL: a drake mallard on the water"""
    I = Icon()
    I.add(rect(1, 24, 31, 30), SLATE, shade=0.6, light=(0.0, -1.0))
    I.line(path([(4, 26), (11, 26)], 1.0), ICE, 3)
    I.line(path([(19, 28), (27, 28)], 1.0), ICE, 3)
    I.add(ellipse(14, 21, 11, 5.5), STONE, shade=0.8, edge=True)  # body
    I.add(ellipse(10, 20.5, 6, 3.4), BRONZE, shade=0.7)  # chestnut breast
    I.add(poly([(20, 17), (27, 19.5), (21, 23)]), STEEL, flat=1, edge=True)  # tail
    I.add(rect(8.5, 13, 12.5, 16.5), WHITE, flat=4)  # white collar
    I.add(circle(10.5, 10, 4.6), GREEN, shade=0.9, edge=True)  # green head
    I.add(poly([(5, 10), (0.8, 11.5), (5.2, 12.6)]), GOLD, flat=3, edge=True)  # yellow bill
    I.px(9, 9, INK[0])
    I.add(rect(15, 18.5, 20, 19.5), SLATE, flat=2)  # speculum
    return I


def compose(repo):
    """the dossier's icons in DossierArt.Icon order"""
    jm = {name: f for name, f in J.ICONS.items()}
    order = [
        ('GLASSING', jm['glass']), ('STALK', jm['stalking']), ('RANGE', jm['training']), ('TRACKING', jm['blood']),
        ('DRESSING', jm['butchery']), ('HARVEST', jm['species']), ('TRACK', jm['sp_whitetail']), ('TIMED', sunrise),
        ('CLEAN', jm['marksmanship']), ('TROPHY', jm['records']), ('SUPPLY', jm['equipment']), ('CHECK', check),
        ('CLOCK', jm['clock']), ('LOCK', jm['lock']), ('MEDAL', jm['podium']), ('WIND', jm['wind']),
        ('FLAG', flag), ('CROSS', cross), ('ACADEMY', jm['assign']), ('TOKEN', jm['token']),
        ('STAR', jm['star']), ('ALERT', alert), ('BOOK', jm['ledger']), ('LEAVE', jm['next']),
        ('ARCHERY', jm['hunting']), ('UPLAND', grouse), ('WATERFOWL', mallard),
    ]
    atlas = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
    imgs = []
    for i, (name, f) in enumerate(order):
        im = f().render().resize((64, 64), Image.NEAREST)
        atlas.alpha_composite(im, ((i % 8) * 64, (i // 8) * 64))
        imgs.append((name, im))
    sys.path.insert(0, os.path.join(HERE, '..', 'artqa'))
    from bleed import bleed_image
    out = os.path.join(repo, 'patch/assets/frontierhunts/textures/gui/academy/icons.png')
    bleed_image(atlas).save(out, optimize=True)
    return out, imgs


if __name__ == '__main__':
    out, imgs = compose(sys.argv[1])
    print(out, len(imgs), 'icons')
    if len(sys.argv) > 2:
        P = Image.new('RGBA', (9 * 72, 3 * 72), (0xEA, 0xE3, 0xD0, 255))
        for i, (n, im) in enumerate(imgs):
            P.alpha_composite(im, ((i % 9) * 72 + 4, (i // 9) * 72 + 4))
        P.save(sys.argv[2])
