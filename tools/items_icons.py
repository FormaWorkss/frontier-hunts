#!/usr/bin/env python3
"""[items] Original 32x32 inventory icons for the Camp Post, the Big-Buck Board and the Hound Lead.

Drawn procedurally in the style of the mod's other flat item icons (32x32, 1px warm near-black outline,
light from the top-left, 4-5 step colour ramps). Run from the repo root:  python3 tools/items_icons.py
Writes patch/assets/frontierhunts/textures/item/{camp_post,big_buck_board,hound_lead}.png
"""
import math, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'patch', 'assets', 'frontierhunts', 'textures', 'item')

# colour ramps, dark -> light (sampled to sit with the blocks' own textures)
POST = [(52, 34, 21), (78, 53, 32), (104, 72, 44), (132, 94, 58), (160, 120, 76)]
WALNUT = [(38, 23, 14), (60, 38, 23), (84, 54, 33), (108, 71, 44), (134, 92, 58)]
SHINGLE = [(46, 33, 22), (70, 52, 34), (96, 73, 49), (122, 96, 65), (148, 120, 82)]
FELT = [(14, 30, 20), (22, 44, 30), (32, 61, 42), (44, 80, 56), (58, 98, 69)]
BRASS = [(96, 68, 25), (148, 110, 42), (198, 158, 70), (230, 194, 102), (250, 226, 150)]
STONE = [(42, 46, 40), (60, 66, 56), (80, 86, 72), (102, 108, 90), (126, 132, 110)]
MOSS = [(34, 56, 28), (50, 80, 38), (70, 104, 50), (92, 128, 62), (114, 150, 76)]
IRON = [(24, 24, 28), (38, 38, 44), (56, 56, 64), (80, 80, 90), (112, 112, 122)]
FLAG = [(28, 50, 27), (42, 72, 38), (58, 96, 52), (78, 122, 68), (100, 146, 86)]
CREAM = [(120, 110, 86), (168, 156, 124), (206, 194, 160), (232, 224, 196), (246, 242, 224)]
BONE = [(92, 76, 55), (136, 116, 86), (180, 160, 124), (214, 198, 164), (238, 228, 200)]
LEATHER = [(46, 25, 13), (76, 43, 23), (106, 63, 35), (136, 87, 51), (166, 114, 72)]
CORD = [(64, 22, 19), (96, 36, 30), (128, 50, 42), (156, 70, 58), (180, 94, 78)]
OUTLINE = (22, 16, 12, 255)


class Icon:
    def __init__(self, seed):
        self.px = {}
        self.rng = random.Random(seed)

    def p(self, x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            self.px[(x, y)] = tuple(c[:3]) + (255,)

    def box(self, x0, y0, x1, y1, ramp, base=2, bevel=True, grain=None, grain_amt=0.18):
        """inclusive box; bevel = highlight top/left, shadow bottom/right; grain 'v'/'h' streaks"""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                i = base
                if grain == 'v' and self._streak(x, 0, grain_amt):
                    i -= 1
                if grain == 'h' and self._streak(0, y + x // 7 * 7, grain_amt):
                    i -= 1
                if grain == 'n' and self.rng.random() < grain_amt:
                    i += self.rng.choice((-1, 1))
                if bevel:
                    if y == y0 or x == x0:
                        i = base + 1
                    if y == y1 or x == x1:
                        i = base - 1
                self.p(x, y, ramp[max(0, min(len(ramp) - 1, i))])

    def _streak(self, a, b, amt):
        h = (a * 73856093 ^ b * 19349663) & 0xffff
        return (h / 65535.0) < amt

    def save(self, name):
        im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            im.putpixel((x, y), c)
        # 1px outline around the silhouette (4-neighbour), like the mod's other icons
        out = []
        for y in range(32):
            for x in range(32):
                if (x, y) in self.px:
                    continue
                if any((x + dx, y + dy) in self.px for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    out.append((x, y))
        for x, y in out:
            im.putpixel((x, y), OUTLINE)
        os.makedirs(ROOT, exist_ok=True)
        im.save(os.path.join(ROOT, name + '.png'))
        return im


def camp_post():
    ic = Icon(11)
    # flag first (behind the post): green pennant with a cream antler emblem, gently waving
    for x in range(17, 30):
        w = round(math.sin((x - 17) / 2.6) * 1.0)
        top, bot = 4 + w, 13 + w
        if x >= 27:  # swallowtail
            for y in range(top, bot + 1):
                if abs(y - (top + bot) / 2) > (x - 26) * 1.4 - 0.2:
                    ic.p(x, y, FLAG[2 if y < (top + bot) / 2 else 1])
            continue
        slope = math.cos((x - 17) / 2.6)
        for y in range(top, bot + 1):
            i = 2 + (1 if slope > 0.35 else (-1 if slope < -0.35 else 0))
            if y == top:
                i += 1
            if y == bot:
                i -= 1
            ic.p(x, y, FLAG[max(0, min(4, i))])
    # emblem: small cream diamond badge with a dark heart
    for dy in range(-2, 3):
        for dx in range(-2 + abs(dy), 3 - abs(dy)):
            x, y = 22 + dx, 8 + dy
            w = round(math.sin((x - 17) / 2.6) * 1.0)
            c = CREAM[3 if dy < 0 or (dy == 0 and dx < 0) else 2]
            if dx == 0 and dy == 0:
                c = FLAG[1]
            ic.p(x, y + w, c)
    # stone cairn
    ic.box(4, 26, 11, 29, STONE, 2, grain='n', grain_amt=0.25)
    ic.box(19, 26, 26, 29, STONE, 1, grain='n', grain_amt=0.25)
    ic.box(11, 25, 20, 29, STONE, 2, grain='n', grain_amt=0.25)
    ic.box(9, 23, 21, 26, STONE, 3, grain='n', grain_amt=0.2)
    for x, y in ((9, 23), (10, 23), (11, 23), (12, 23), (18, 23), (19, 23), (4, 26), (5, 26), (6, 26), (20, 25), (24, 26), (25, 26), (12, 25)):
        ic.p(x, y, MOSS[3 if y == 23 else 2])
    for x, y in ((13, 23), (17, 23), (7, 26), (11, 24)):
        ic.p(x, y, MOSS[1])
    # round log post: lit left of centre
    for y in range(4, 24):
        for x, i in zip(range(12, 17), (1, 3, 2, 2, 1)):
            j = i - (1 if ic._streak(x, y // 3, 0.22) else 0)
            ic.p(x, y, POST[max(0, j)])
    # iron cap and flag rings
    ic.box(11, 2, 17, 4, IRON, 2)
    ic.p(11, 2, IRON[4]); ic.p(12, 2, IRON[4])
    for y in (5, 12):
        ic.p(17, y, IRON[3]); ic.p(18, y, IRON[2])
    # cord lashing
    for x in range(12, 17):
        ic.p(x, 10, CORD[3 if x < 14 else 2]); ic.p(x, 11, CORD[1])
    # walnut name plaque with frame, nails and a carved line of lettering
    ic.box(3, 14, 25, 21, WALNUT, 1, bevel=True)
    ic.box(4, 15, 24, 20, WALNUT, 3, bevel=False, grain='h', grain_amt=0.22)
    for x in range(4, 25):
        ic.p(x, 15, WALNUT[4])
    for x in range(6, 23):
        if (x - 6) % 4 != 3:
            ic.p(x, 17, WALNUT[1] if (x * 7) % 3 else WALNUT[0])
    for x in range(8, 20):
        if (x - 8) % 3 != 2:
            ic.p(x, 19, WALNUT[2])
    for x, y in ((4, 15), (24, 15), (4, 20), (24, 20)):
        ic.p(x, y, BRASS[3])
    return ic.save('camp_post')


def big_buck_board():
    ic = Icon(23)
    # posts
    for x0 in (3, 25):
        ic.box(x0, 8, x0 + 3, 28, POST, 2, grain='v', grain_amt=0.25)
        ic.box(x0 - 1, 27, x0 + 4, 29, POST, 1)
    # shingled roof (trapezoid) with a dark eave line
    rows = {3: (6, 25), 4: (5, 26), 5: (4, 27), 6: (3, 28), 7: (2, 29), 8: (1, 30)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            seam = (x + (y % 2) * 2) % 4 == 0
            i = 3 if y <= 4 else 2
            if seam:
                i -= 1
            if x == a:
                i += 1
            ic.p(x, y, SHINGLE[max(0, min(4, i))])
    for x in range(1, 31):
        ic.p(x, 9, SHINGLE[0])
    # header beam with a small brass title plate
    ic.box(4, 10, 27, 12, WALNUT, 2, grain='h')
    ic.box(11, 10, 20, 11, BRASS, 2)
    for x in range(12, 20, 2):
        ic.p(x, 11, BRASS[1])
    # felt panel in a walnut frame
    ic.box(4, 13, 27, 26, WALNUT, 2)
    ic.box(5, 14, 26, 25, FELT, 2, bevel=False, grain='n', grain_amt=0.12)
    for x in range(5, 27):
        ic.p(x, 14, FELT[0])
    for y in range(14, 26):
        ic.p(5, y, FELT[1])
    # mounted rack on a walnut shield
    ic.box(13, 19, 18, 22, WALNUT, 3)
    ic.p(13, 22, FELT[2]); ic.p(18, 22, FELT[2])
    ic.p(14, 23, WALNUT[1]); ic.p(15, 23, WALNUT[2]); ic.p(16, 23, WALNUT[2]); ic.p(17, 23, WALNUT[1])
    left_beam = [(14, 18), (13, 18), (12, 18), (11, 17), (10, 17), (9, 16), (8, 15), (8, 14), (8, 13)]
    left_tines = [(12, 17), (12, 16), (12, 15), (10, 16), (10, 15), (10, 14), (13, 17), (7, 13)]
    for pts, lvl in ((left_beam, 3), (left_tines, 4)):
        for x, y in pts:
            ic.p(x, y, BONE[lvl])
            ic.p(31 - x, y, BONE[lvl - 1])
    # nameplates under the rack
    for a in (7, 21):
        ic.box(a, 22, a + 3, 23, BRASS, 2)

    return ic.save('big_buck_board')


def hound_lead():
    ic = Icon(5)

    def ring(cx, cy, rx, ry, th):
        for y in range(32):
            for x in range(32):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = math.hypot(dx, dy)
                if abs(d - 1) * (rx + ry) / 2 <= th / 2:
                    ang = math.atan2(dy, dx)
                    light = -math.sin(ang) * 0.6 - math.cos(ang) * 0.25
                    edge = (d - 1) * (rx + ry) / 2  # outer edge > 0
                    i = 2 + (1 if light > 0.25 else (-1 if light < -0.35 else 0)) + (1 if edge < -0.4 else 0)
                    ic.p(x, y, LEATHER[max(0, min(4, i))])

    ring(12.5, 10.5, 9.5, 6.0, 2.2)
    ring(14.5, 13.5, 9.5, 6.0, 2.2)
    ring(16.5, 16.5, 9.5, 6.0, 2.2)
    # stitching highlights on the front coil
    for x in range(11, 23, 3):
        ic.p(x, 22, LEATHER[4])
    # tail strap running to the brass snap hook
    tail = [(24, 21), (24, 22), (25, 23), (25, 24), (25, 25), (25, 26)]
    for x, y in tail:
        ic.p(x, y, LEATHER[3]); ic.p(x + 1, y, LEATHER[1])
    # snap hook: brass swivel + clip
    ic.box(24, 27, 27, 28, BRASS, 2)
    for x, y in ((25, 29), (26, 29), (24, 29), (24, 30), (25, 30)):
        ic.p(x, y, BRASS[3] if x < 26 else BRASS[1])
    ic.p(27, 29, IRON[3]); ic.p(27, 30, IRON[2]); ic.p(26, 30, IRON[2])
    # handle loop on the top-left, folded over the coil
    for x, y in ((3, 6), (3, 5), (4, 4), (5, 3), (6, 3), (7, 4), (7, 5), (6, 6)):
        ic.p(x, y, LEATHER[3] if y < 5 else LEATHER[2])
    return ic.save('hound_lead')


if __name__ == '__main__':
    camp_post(); big_buck_board(); hound_lead()
    print('ok')
