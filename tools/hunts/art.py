#!/usr/bin/env python3
"""[hunts] Original art for the waterfowl kit: Duck Call item texture (32x32, the mod's call style: dark outline,
banded side light, limited palette) and the Mallard Decoy block texture (32x32) + Blockbench-style block model.

python3 tools/hunts/art.py <repo> [preview.png]
"""
import json
import os
import sys

import numpy as np
from PIL import Image

OUT = (18, 19, 22)
WOOD = [(38, 24, 15), (58, 37, 22), (80, 52, 30), (106, 70, 40), (134, 92, 54), (164, 118, 74)]
BRASS = [(78, 55, 16), (122, 89, 22), (166, 124, 34), (205, 165, 66)]
BLACK = [(30, 32, 35), (44, 48, 53), (60, 66, 72), (84, 92, 100)]
CORD = [(24, 52, 34), (38, 82, 50), (58, 116, 66), (96, 156, 92)]


def shade(ramp, t):
    """t 0 (dark, right) .. 1 (lit, left): pick from the ramp in bands."""
    i = int(round(t * (len(ramp) - 1)))
    return ramp[max(0, min(len(ramp) - 1, i))]


def duck_call():
    W = 32
    img = np.zeros((W, W, 4), np.uint8)
    filled = np.zeros((W, W), bool)

    def put(x, y, c):
        if 0 <= x < W and 0 <= y < W:
            img[y, x] = (*c, 255)
            filled[y, x] = True

    cx = 15.5
    # rows: (y, half width, material)
    rows = []
    # flared bell (open end) on top
    for y, hw in ((3, 3.7), (4, 4.1), (5, 3.8), (6, 3.3)):
        rows.append((y, hw, 'bell'))
    for y in range(7, 17):
        hw = 3.1 - (y - 7) * 0.05
        rows.append((y, hw, 'wood'))
    for y in (17, 18, 19):
        rows.append((y, 3.5 if y == 18 else 3.2, 'brass'))
    for y in range(20, 27):
        hw = 2.7 - (y - 20) * 0.08
        rows.append((y, hw, 'insert'))
    for y, hw in ((27, 2.0), (28, 1.4)):
        rows.append((y, hw, 'insert'))
    for y, hw, mat in rows:
        x0 = int(np.floor(cx - hw + 0.5))
        x1 = int(np.ceil(cx + hw - 0.5))
        for x in range(x0, x1 + 1):
            t = 1.0 - (x - x0) / max(1, (x1 - x0))  # 1 at the lit left edge
            if mat == 'wood':
                # walnut barrel: banded side light, a bright edge on the lit side, a faint grain line
                c = shade(WOOD[1:5], 0.15 + 0.85 * t)
                if x == x0 + 1:
                    c = WOOD[5]
                elif x == x1 - 2 and y % 3 != 0:
                    c = WOOD[1]
            elif mat == 'bell':
                c = shade(WOOD[1:5], 0.2 + 0.8 * t)
                if y == 3 and x0 < x < x1:
                    c = (20, 13, 9)  # the open mouth of the barrel
                elif y == 4 and x0 < x < x1:
                    c = WOOD[0] if x > x0 + 1 else WOOD[2]
                elif x == x0 + 1 and y >= 5:
                    c = WOOD[5]
            elif mat == 'brass':
                c = shade(BRASS, 0.15 + 0.85 * t)
                if y == 17:
                    c = BRASS[max(0, BRASS.index(c) - 1)]
                if x == x0 + 1 and y == 18:
                    c = BRASS[3]
            else:
                c = shade(BLACK, 0.1 + 0.9 * t)
                if x == x0 + 1 and y < 27:
                    c = BLACK[3]
            put(x, y, c)
    # tone hole at the insert end
    put(15, 28, (12, 12, 14))
    # lanyard: braided green cord from the brass band, hanging in a loop to the right
    cord = [(19, 18), (20, 19), (21, 20), (22, 21), (22, 22), (23, 23), (23, 24), (23, 25), (23, 26), (22, 27), (22, 28),
            (21, 29), (20, 29), (19, 28)]
    for i, (x, y) in enumerate(cord):
        put(x, y, CORD[2] if i % 2 == 0 else CORD[1])
    # cord highlight on the lit side of the loop
    for x, y in ((22, 23), (22, 24), (22, 25)):
        put(x, y, CORD[3])
    # outline
    out = img.copy()
    for y in range(W):
        for x in range(W):
            if filled[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < W and 0 <= yy < W and filled[yy, xx]:
                    out[y, x] = (*OUT, 255)
                    break
    return Image.fromarray(out, 'RGBA')


# ------------------------------------------------------------------------------------------------ decoy
GREEN = [(18, 52, 40), (24, 78, 58), (36, 108, 74), (62, 140, 92)]
CHEST = [(70, 34, 22), (102, 52, 30), (128, 70, 40)]
GREY = [(126, 126, 120), (156, 154, 146), (186, 184, 174), (210, 208, 198)]
DARK = [(26, 24, 24), (40, 38, 36), (58, 54, 50)]
BILL = [(150, 128, 38), (196, 170, 58), (226, 204, 92)]
WHITE = (232, 230, 220)
KEEL = [(40, 34, 28), (58, 50, 40)]
VSPEC = [(54, 64, 140), (86, 98, 186)]


def decoy_texture():
    """32x32 texture, regions (px): flank 0,0-16,8 (rump at the right), back 16,0-24,16 (rump at the bottom), tail
    24,0-32,8, bill 24,8-32,16, breast 0,8-8,16, head side 8,8-16,16 (eye), plain head 0,16-8,24, keel 8,16-24,24,
    waterline 24,16-32,24."""
    rng = np.random.default_rng(7)
    T = np.zeros((32, 32, 4), np.uint8)

    def fill(x0, y0, x1, y1, ramp, grad='v', noise=0.0, flip=False):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if grad == 'v':
                    t = 1.0 - (y - y0) / max(1, y1 - y0 - 1)
                else:
                    t = 1.0 - (x - x0) / max(1, x1 - x0 - 1)
                if flip:
                    t = 1.0 - t
                t = min(1.0, max(0.0, t + (rng.random() - 0.5) * noise))
                T[y, x] = (*shade(ramp, t), 255)

    def px(x, y, c):
        T[y, x] = (*c, 255)

    # flank: pale grey sides with fine vermiculation, darker toward the waterline, black rump at the right end
    fill(0, 0, 16, 8, GREY, 'v', 0.3)
    for x in range(0, 12):
        for y in range(1, 7):
            if (x * 5 + y * 3) % 7 == 0:
                px(x, y, GREY[0])
    for x in range(12, 16):
        for y in range(0, 8):
            px(x, y, DARK[1] if (x + y) % 3 else DARK[2])
    px(12, 1, GREY[3])  # white flank patch edge in front of the rump
    px(12, 2, GREY[3])
    # folded wing: brownish top edge with the blue speculum and white bars
    for x in range(5, 12):
        px(x, 0, (104, 96, 84))
    for x in range(7, 11):
        px(x, 1, WHITE)
        px(x, 2, VSPEC[1])
        px(x, 3, VSPEC[0])
    # back (up face): grey-brown, lighter along the spine, black rump at the bottom (south)
    for y in range(0, 16):
        for x in range(16, 24):
            dx = abs(x - 19.5) / 4.0
            t = 0.75 - dx * 0.5 + (rng.random() - 0.5) * 0.15
            c = shade([(84, 76, 66), (104, 96, 84), (124, 116, 102), (146, 138, 122)], t)
            if y >= 12:
                c = DARK[1] if (x + y) % 3 else DARK[2]
            px(x, y, c)
    for x in range(17, 23):
        px(x, 0, (92, 64, 44))  # where the chestnut breast meets the back
    # tail: black with the white tail fan edge and the curled drake feathers
    fill(24, 0, 32, 8, DARK, 'v', 0.2)
    for x in range(24, 32):
        px(x, 7, GREY[3])
    px(27, 2, GREY[2])
    px(28, 2, GREY[2])
    # bill: yellow, darker nail at the tip
    fill(24, 8, 32, 16, BILL, 'v', 0.12)
    px(24, 8, DARK[1])
    px(24, 9, DARK[1])
    # breast: chestnut
    fill(0, 8, 8, 16, CHEST, 'v', 0.25)
    for x in range(0, 8):
        px(x, 8, WHITE)  # the white neck ring runs over the top of the breast
    # head side: glossy green, lighter on the crown, an eye, the white ring along the bottom
    fill(8, 8, 16, 16, GREEN, 'v', 0.25)
    for x in range(8, 16):
        px(x, 15, WHITE)
    px(10, 10, DARK[0])
    px(10, 11, DARK[0])
    px(11, 10, (230, 226, 210))  # catch light
    # plain head (front / back / crown)
    fill(0, 16, 8, 24, GREEN, 'v', 0.25)
    for x in range(0, 8):
        px(x, 23, WHITE)
    # keel: dark weathered wood
    fill(8, 16, 24, 24, KEEL, 'v', 0.4)
    # waterline: wet darker grey
    fill(24, 16, 32, 24, [(58, 58, 54), (80, 80, 74), (104, 104, 96)], 'v', 0.25)
    fill(0, 24, 32, 32, KEEL, 'v', 0.3)
    return Image.fromarray(T, 'RGBA')


def el(frm, to, faces, rot=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


def f(r, flip=False):
    u0, v0, u1, v1 = r
    uv = [u1 / 2, v0 / 2, u0 / 2, v1 / 2] if flip else [u0 / 2, v0 / 2, u1 / 2, v1 / 2]
    return {"uv": uv, "texture": "#d"}


FLANK_R = (0, 0, 16, 8)
BACK_R = (16, 0, 24, 16)
TAIL_R = (24, 0, 32, 8)
BILL_R = (24, 8, 32, 16)
BREAST_R = (0, 8, 8, 16)
HEAD_SIDE_R = (8, 8, 16, 16)
HEAD_R = (0, 16, 8, 24)
KEELR = (8, 16, 24, 24)
WATERR = (24, 16, 32, 24)


def box(side, up, down, north, south, side_flip_east=True):
    # west faces read their u toward the tail (south); east faces are mirrored so both sides match
    return {"west": f(side), "east": f(side, side_flip_east), "up": f(up), "down": f(down), "north": f(north), "south": f(south)}


def decoy_model():
    """Mallard drake decoy, head toward north (-z). The hull sinks 2 px below the block bottom into the water."""
    els = [
        # hull: keel in the water, body, rounded back
        el([5, -2, 3], [11, 0.5, 13], box(WATERR, KEELR, KEELR, WATERR, WATERR)),
        el([4.5, 0.5, 2.5], [11.5, 3.5, 13.5], box(FLANK_R, BACK_R, KEELR, BREAST_R, TAIL_R)),
        el([5, 3.5, 3.5], [11, 4.5, 12.5], box(BACK_R, BACK_R, BACK_R, BREAST_R, TAIL_R)),
        # breast swelling forward under the neck
        el([5.25, 0.75, 1.75], [10.75, 3.5, 2.5], box(BREAST_R, BREAST_R, KEELR, BREAST_R, BREAST_R)),
        # upturned tail
        el([6.5, 3, 12.5], [9.5, 4.25, 15], box(TAIL_R, TAIL_R, TAIL_R, TAIL_R, TAIL_R), {"angle": 22.5, "axis": "x", "origin": [8, 3, 13]}),
        # neck and head, the head held up and slightly forward
        el([7, 3.5, 2.5], [9, 5.5, 4.5], box(HEAD_R, HEAD_R, HEAD_R, HEAD_R, HEAD_R)),
        el([6.5, 5.25, 1.75], [9.5, 7.75, 4.75], box(HEAD_SIDE_R, HEAD_R, HEAD_R, HEAD_R, HEAD_R)),
        # bill
        el([7.25, 5.6, 0], [8.75, 6.5, 1.75], box(BILL_R, BILL_R, BILL_R, BILL_R, BILL_R)),
    ]
    return {
        "credit": "Frontier Hunts [hunts] - original mallard drake decoy",
        "ambientocclusion": False,
        "textures": {"d": "frontierhunts:block/mallard_decoy", "particle": "frontierhunts:block/mallard_decoy"},
        "elements": els,
        "display": {
            "gui": {"rotation": [25, 135, 0], "translation": [0, 3.5, 0], "scale": [1.2, 1.2, 1.2]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 3, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def write(repo, preview=None):
    A = os.path.join(repo, 'patch/assets/frontierhunts')
    os.makedirs(A + '/textures/item', exist_ok=True)
    os.makedirs(A + '/textures/block', exist_ok=True)
    os.makedirs(A + '/models/item', exist_ok=True)
    os.makedirs(A + '/models/block', exist_ok=True)
    os.makedirs(A + '/blockstates', exist_ok=True)
    call = duck_call()
    call.save(A + '/textures/item/duck_call.png')
    dec = decoy_texture()
    dec.save(A + '/textures/block/mallard_decoy.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "frontierhunts:item/duck_call"}},
              open(A + '/models/item/duck_call.json', 'w'), indent=1)
    json.dump(decoy_model(), open(A + '/models/block/mallard_decoy.json', 'w'), indent=1)
    json.dump({"parent": "frontierhunts:block/mallard_decoy"}, open(A + '/models/item/mallard_decoy.json', 'w'), indent=1)
    variants = {"facing=north": {"model": "frontierhunts:block/mallard_decoy"},
                "facing=east": {"model": "frontierhunts:block/mallard_decoy", "y": 90},
                "facing=south": {"model": "frontierhunts:block/mallard_decoy", "y": 180},
                "facing=west": {"model": "frontierhunts:block/mallard_decoy", "y": 270}}
    json.dump({"variants": variants}, open(A + '/blockstates/mallard_decoy.json', 'w'), indent=1)
    if preview:
        bg = (200, 190, 170, 255)
        S = Image.new('RGBA', (32 * 8 * 2 + 24, 32 * 8 + 16), bg)
        S.alpha_composite(call.resize((256, 256), Image.NEAREST), (8, 8))
        S.alpha_composite(dec.resize((256, 256), Image.NEAREST), (8 + 256 + 8, 8))
        S.save(preview)
    print('wrote duck_call + mallard_decoy art')


if __name__ == '__main__':
    write(sys.argv[1] if len(sys.argv) > 1 else '.', sys.argv[2] if len(sys.argv) > 2 else None)
