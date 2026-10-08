#!/usr/bin/env python3
"""[1.1.7] Art, models, blockstates and loot for the outfitter's counter exclusives and the range targets.

Deterministic: run from anywhere, writes into patch/. Box geometry matches the shapes and hit zones in
src/com/formaworks/frontierhunts/range/RangeTargetBlock.java (model pixels, target facing north).

    python3 tools/art/range_art.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts')
D = os.path.join(ROOT, 'patch', 'data', 'frontierhunts')
FH = 'frontierhunts'
OUT = (24, 20, 18, 255)


def save_png(im, *parts):
    p = os.path.join(A, 'textures', *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p, optimize=True)


def save_json(obj, base, *parts):
    p = os.path.join(base, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', newline='\n') as f:
        json.dump(obj, f, indent=1)
        f.write('\n')


def clampc(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, k):
    return clampc((c[0] * k, c[1] * k, c[2] * k, 255))


# ============================================================================================ block textures

def painted_steel(seed, base, size=16):
    """steel plate in target paint: mottled coat, chips down to bare steel, grey lead splashes from old hits"""
    r = random.Random(seed)
    im = Image.new('RGBA', (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            k = 1.0 + r.uniform(-0.05, 0.05) - 0.03 * (y / size)
            px[x, y] = shade(base + (255,), k)
    for _ in range(size // 3):  # paint chips
        x, y = r.randrange(size), r.randrange(size)
        px[x, y] = (78, 80, 84, 255)
        if r.random() < 0.5 and x + 1 < size:
            px[x + 1, y] = (96, 98, 100, 255)
    for _ in range(size // 8 + 1):  # lead splashes: a grey star
        cx, cy = r.randrange(2, size - 2), r.randrange(2, size - 2)
        px[cx, cy] = (120, 122, 126, 255)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if r.random() < 0.8:
                px[cx + dx, cy + dy] = (158, 160, 164, 255)
    return im


def dark_steel(seed):
    r = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            v = 58 + r.randint(-6, 6)
            px[x, y] = (v, v + 2, v + 5, 255)
    for _ in range(9):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = (112 + r.randint(-10, 10), 66, 38, 255)
    return im


def foam(seed, base, size=32, rings=False):
    r = random.Random(seed)
    im = Image.new('RGBA', (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            k = 1.0 + r.uniform(-0.07, 0.07)
            if r.random() < 0.04:
                k -= 0.12  # foam pores
            px[x, y] = shade(base + (255,), k)
    if rings:
        # scoring rings on the chest side, drawn at 4 texels per model pixel: the 11 x 6 face maps to uv 0..11 x 0..6,
        # ring centre at uv (6, 3.25) (model z 11, y 10.75), radii 2.4 / 1.5 / 0.6 px (8, 10 and 12 rings)
        im = im.resize((64, 64), Image.NEAREST)
        px = im.load()
        cx, cy = 24.0, 13.0
        line = (58, 40, 26, 255)
        for y in range(64):
            for x in range(64):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < 9.6:
                    c = px[x, y]
                    px[x, y] = clampc((c[0] * 1.12, c[1] * 1.12, c[2] * 1.1, 255))
                if abs(d - 9.6) < 0.62 or abs(d - 6.0) < 0.55 or abs(d - 2.4) < 0.5:
                    px[x, y] = line
    return im


DIGITS = {
    '0': ['111', '101', '101', '101', '111'], '1': ['010', '110', '010', '010', '111'], '2': ['111', '001', '111', '100', '111'],
    '3': ['111', '001', '011', '001', '111'], '4': ['101', '101', '111', '001', '001'], '5': ['111', '100', '111', '001', '111'],
    '6': ['111', '100', '111', '101', '111'], '7': ['111', '001', '010', '010', '010'], '8': ['111', '101', '111', '101', '111'],
    '9': ['111', '101', '111', '001', '111'],
}


def marker_board(n):
    """26 x 16 texels used (uv 0..13 x 0..8 at 2 texels per unit): painted board, orange top band, black numerals"""
    r = random.Random(n)
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    for y in range(32):
        for x in range(32):
            v = 232 + r.randint(-6, 4)
            px[x, y] = (v, v - 4, v - 14, 255)
    for y in range(0, 2):
        for x in range(32):
            px[x, y] = (226, 104, 30, 255)
    s = str(n)
    w = len(s) * 6 + (len(s) - 1) * 2
    x0, y0 = (26 - w) // 2, 4
    for i, ch in enumerate(s):
        for gy, row in enumerate(DIGITS[ch]):
            for gx, bit in enumerate(row):
                if bit == '1':
                    for sy in range(2):
                        for sx in range(2):
                            px[x0 + i * 8 + gx * 2 + sx, y0 + gy * 2 + sy] = (26, 26, 28, 255)
    return im


# ============================================================================================ item sprites

def outline(im):
    """1-px dark outline around the sprite, the house style of the mod's item art"""
    src = im.load()
    out = im.copy()
    o = out.load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src[nx, ny][3] > 0:
                        o[x, y] = OUT
                        break
    return out


def sprite(draw_fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    draw_fn(d, im.load())
    return outline(im)


def lamp(d, px):
    body, dark = (78, 88, 62, 255), (56, 62, 46, 255)
    d.line([(2, 13), (8, 7)], fill=body, width=3)
    d.line([(2, 12), (7, 7)], fill=(104, 116, 84, 255), width=1)
    d.ellipse([7, 1, 14, 8], fill=dark)
    d.ellipse([9, 2, 13, 6], fill=(206, 40, 44, 255))
    px[10, 3] = (255, 176, 160, 255)
    px[11, 3] = (240, 110, 100, 255)
    px[5, 10] = (228, 128, 36, 255)  # switch
    px[1, 14] = (40, 42, 36, 255)


def sticks(d, px):
    wood, hi = (122, 86, 52, 255), (156, 114, 72, 255)
    d.line([(2, 15), (9, 3)], fill=wood, width=1)
    d.line([(3, 15), (10, 3)], fill=hi, width=1)
    d.line([(13, 15), (6, 3)], fill=wood, width=1)
    d.line([(12, 15), (5, 3)], fill=hi, width=1)
    for x, y in ((5, 1), (6, 2), (7, 3), (8, 3), (9, 2), (10, 1)):
        px[x, y] = (38, 38, 40, 255)
    px[7, 4] = (60, 60, 62, 255)
    px[8, 4] = (60, 60, 62, 255)
    for x in (2, 3, 12, 13):
        px[x, 15] = (44, 44, 46, 255)


def lure(d, px):
    d.rectangle([5, 6, 10, 14], fill=(146, 84, 30, 255))
    d.rectangle([6, 6, 6, 13], fill=(184, 118, 52, 255))
    d.rectangle([5, 9, 10, 12], fill=(232, 220, 190, 255))
    for x, y in ((7, 10), (8, 10), (9, 10), (7, 11), (9, 11), (9, 9)):
        px[x, y] = (110, 70, 40, 255)  # a little doe on the label
    d.rectangle([6, 3, 9, 5], fill=(36, 36, 38, 255))
    d.rectangle([7, 1, 8, 2], fill=(226, 226, 222, 255))


def warmers(d, px):
    d.rectangle([5, 2, 14, 10], fill=(186, 84, 30, 255))
    d.rectangle([2, 5, 11, 13], fill=(228, 112, 40, 255))
    d.rectangle([2, 8, 11, 9], fill=(244, 236, 220, 255))
    for x, y, c in ((6, 6, (250, 214, 70)), (7, 6, (250, 214, 70)), (6, 5, (238, 70, 40)), (7, 7, (238, 70, 40)), (5, 6, (238, 70, 40))):
        px[x, y] = c + (255,)
    for x in range(3, 11, 2):
        px[x, 12] = (200, 92, 30, 255)


def thermos(d, px):
    green, hi, dk = (66, 112, 70, 255), (102, 150, 102, 255), (46, 80, 50, 255)
    d.rectangle([5, 4, 10, 14], fill=green)
    d.rectangle([6, 5, 6, 13], fill=hi)
    d.rectangle([10, 5, 10, 13], fill=dk)
    d.rectangle([5, 1, 10, 3], fill=(150, 156, 152, 255))
    d.rectangle([5, 3, 10, 3], fill=(96, 100, 98, 255))
    d.rectangle([11, 6, 12, 11], fill=dk)
    px[11, 7] = (0, 0, 0, 0)
    px[11, 8] = (0, 0, 0, 0)
    px[11, 9] = (0, 0, 0, 0)
    px[11, 10] = (0, 0, 0, 0)
    d.rectangle([5, 8, 10, 9], fill=(214, 200, 150, 255))


def milkweed(d, px):
    """two split milkweed pods on a stem, white silk spilling from the open seams"""
    pod, hi, dk = (118, 126, 76, 255), (160, 168, 104, 255), (84, 90, 52, 255)
    d.line([(8, 15), (8, 11)], fill=(96, 84, 54, 255))
    d.polygon([(7, 11), (3, 8), (2, 4), (4, 1), (6, 5), (8, 9)], fill=pod)
    d.polygon([(9, 11), (13, 8), (14, 4), (12, 1), (10, 5), (8, 9)], fill=pod)
    d.line([(3, 5), (5, 8)], fill=hi)
    d.line([(13, 5), (12, 8)], fill=dk)
    for x, y in ((4, 2), (5, 3), (5, 1), (6, 2), (11, 2), (11, 1), (12, 3), (10, 3), (3, 0), (13, 0), (7, 1), (9, 1), (8, 0)):
        px[x, y] = (248, 248, 242, 255)
    for x, y in ((6, 4), (10, 4), (2, 1), (14, 1)):
        px[x, y] = (218, 218, 210, 255)


def tape(d, px):
    """a roll of blaze-orange flagging tape with a loose tail"""
    d.ellipse([2, 3, 12, 13], fill=(232, 98, 26, 255))
    d.ellipse([4, 5, 10, 11], fill=(255, 150, 70, 255))
    d.ellipse([5.5, 6.5, 8.5, 9.5], fill=(0, 0, 0, 0))
    d.line([(11, 9), (13, 12), (12, 14), (14, 15)], fill=(232, 98, 26, 255), width=2)


def flag_tex():
    """trail flag block texture: a twig and a knotted strip of orange tape (cross model)"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.line([(8, 15), (8, 4)], fill=(96, 70, 44, 255), width=1)
    d.line([(7, 15), (7, 9)], fill=(70, 50, 30, 255), width=1)
    d.polygon([(8, 4), (13, 5), (12, 7), (14, 9), (9, 7)], fill=(240, 104, 28, 255))
    d.polygon([(8, 4), (4, 6), (5, 8), (3, 11), (8, 7)], fill=(214, 88, 22, 255))
    px = im.load()
    px[8, 5] = (255, 160, 90, 255)
    return im


# ============================================================================================ models

def tex(name):
    return f'{FH}:block/range/{name}'


def el(fr, to, t, faces=None, rot=None, uv=None):
    """an element; t is a texture variable (without #) for all faces, or a dict face -> var"""
    e = {'from': [round(v, 4) for v in fr], 'to': [round(v, 4) for v in to], 'faces': {}}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        if faces is not None and f not in faces:
            continue
        var = t[f] if isinstance(t, dict) else t
        if var is None:
            continue
        face = {'texture': '#' + var}
        if uv and f in uv:
            face['uv'] = uv[f]
        e['faces'][f] = face
    if rot:
        e['rotation'] = rot
    return e


def box(b, t, **kw):
    return el(b[:3], b[3:], t, **kw)


def model(name, textures, elements, parent='block/block'):
    m = {'parent': parent, 'textures': textures, 'elements': elements}
    save_json(m, A, 'models', 'block', name + '.json')


ITEM_DISPLAY = {
    'gui': {'rotation': [22, 160, 0], 'scale': [0.62, 0.62, 0.62]},
    'ground': {'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'scale': [0.6, 0.6, 0.6]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.4, 0.4, 0.4]},
}


def rot_el(e, angle, origin, axis='x'):
    e = dict(e)
    e['rotation'] = {'angle': angle, 'axis': axis, 'origin': origin}
    return e


def gong():
    t = {'log': 'minecraft:block/spruce_log', 'log_top': 'minecraft:block/spruce_log_top', 'bar': 'minecraft:block/stripped_spruce_log',
         'steel': tex('steel_white'), 'chain': tex('steel_dark'), 'particle': tex('steel_white')}
    log = {'north': 'log', 'south': 'log', 'east': 'log', 'west': 'log', 'up': 'log_top', 'down': 'log_top'}
    frame = [box([1, 0, 7, 3, 14, 9], log), box([13, 0, 7, 15, 14, 9], log), box([0, 14, 6.5, 16, 16, 9.5], 'bar')]
    plate = [box([5.5, 10, 7.75, 6.5, 14, 8.25], 'chain'), box([9.5, 10, 7.75, 10.5, 14, 8.25], 'chain'),
             box([5.5, 1.5, 7.5, 10.5, 10.5, 8.5], 'steel'), box([4, 2.5, 7.5, 12, 9.5, 8.5], 'steel'),
             box([3.5, 4, 7.5, 12.5, 8, 8.5], 'steel'), box([6.5, 1, 7.5, 9.5, 11, 8.5], 'steel'),
             box([3, 5, 7.5, 13, 7, 8.5], 'steel')]
    model('steel_gong', t, frame + plate)
    model('steel_gong_swing', t, frame + [rot_el(e, -22.5, [8, 14, 8]) for e in plate])


POPPER_PLATE = [[5.5, 2, 10.5, 4], [4.5, 4, 11.5, 9], [5.5, 9, 10.5, 10], [6.75, 10, 9.25, 11.5], [5.75, 11.5, 10.25, 15],
                [5, 12.25, 11, 14.25]]


def popper():
    t = {'steel': tex('steel_orange'), 'dark': tex('steel_dark'), 'particle': tex('steel_orange')}
    base = [box([3, 0, 4, 13, 1.5, 12], 'dark'), box([5, 1.5, 2.5, 11, 2.5, 4.5], 'dark')]
    up = [box([x1, h1, 3, x2, h2, 4], 'steel') for x1, h1, x2, h2 in POPPER_PLATE]
    down = [box([x1, 2.5, 3 + (h1 - 2), x2, 3.5, 3 + (h2 - 2)], 'steel') for x1, h1, x2, h2 in POPPER_PLATE]
    model('steel_popper', t, base + up)
    model('steel_popper_down', t, base + down)


def silhouette():
    t = {'steel': tex('steel_orange'), 'vitals': tex('steel_white'), 'dark': tex('steel_dark'), 'particle': tex('steel_orange')}
    els = [box([2, 0, 6.5, 14, 1, 9.5], 'dark'), box([4, 1, 7.75, 5, 2, 8.25], 'dark'), box([10.5, 1, 7.75, 11.5, 2, 8.25], 'dark')]
    for b in ([3.5, 2, 7.6, 4.5, 6.5, 8.4], [5.25, 2, 7.6, 6.25, 6.5, 8.4], [10, 2, 7.6, 11, 6.5, 8.4], [11.5, 2, 7.6, 12.5, 6.5, 8.4],
              [3, 6, 7.6, 12.5, 10, 8.4], [2.5, 7, 7.6, 3, 9.5, 8.4], [11.25, 9, 7.6, 13.5, 12.5, 8.4], [12, 11.5, 7.6, 15.5, 13.5, 8.4],
              [11.75, 13.5, 7.6, 12.75, 14.75, 8.4], [12.5, 13.5, 7.65, 13, 16, 8.35], [13, 15, 7.65, 14.5, 15.5, 8.35],
              [13.75, 15.5, 7.65, 14.25, 16, 8.35]):
        els.append(box(b, 'steel'))
    els.append(box([9.5, 6.75, 7.5, 11.75, 9.25, 8.5], 'vitals'))
    model('steel_deer_target', t, els)


def spinner():
    t = {'steel': tex('steel_yellow'), 'dark': tex('steel_dark'), 'particle': tex('steel_yellow')}
    frame = [box([1, 0, 5, 15, 1, 11], 'dark'), box([1, 1, 7.25, 2.5, 11, 8.75], 'dark'), box([13.5, 1, 7.25, 15, 11, 8.75], 'dark'),
             box([2.5, 9.5, 7.6, 13.5, 10.5, 8.4], 'dark')]
    paddle = [([7.5, 4, 7.65, 8.5, 15, 8.35], 'dark'), ([5.5, 1.5, 7.5, 10.5, 6.5, 8.5], 'steel'), ([5, 2, 7.5, 11, 6, 8.5], 'steel'),
              ([6.5, 13, 7.5, 9.5, 15.5, 8.5], 'steel')]

    def flip(b):
        return [b[0], 20 - b[4], b[2], b[3], 20 - b[1], b[5]]

    p0 = [box(b, v) for b, v in paddle]
    p2 = [box(flip(b), v) for b, v in paddle]
    model('steel_spinner_0', t, frame + p0)
    model('steel_spinner_1', t, frame + [rot_el(e, -45, [8, 10, 8]) for e in p0])
    model('steel_spinner_2', t, frame + p2)
    model('steel_spinner_3', t, frame + [rot_el(e, -45, [8, 10, 8]) for e in p2])


FOAM_SIDE_UV = {'west': [0, 0, 11, 6], 'east': [11, 0, 0, 6]}


def foam_front():
    tan, w, dk = 'foam', 'white', 'dark'
    body = el([5, 8, 5], [11, 14, 16], {'north': tan, 'south': tan, 'up': tan, 'down': tan, 'east': 'side', 'west': 'side'}, uv=FOAM_SIDE_UV)
    neck = {'north': w, 'south': tan, 'east': tan, 'west': tan, 'up': tan, 'down': w}
    return [body,
            box([5.5, 7, 6, 10.5, 8, 13], {'north': tan, 'south': tan, 'east': tan, 'west': tan, 'up': tan, 'down': w}),
            box([5.5, 1, 6, 7, 8, 7.5], tan), box([9, 1, 6, 10.5, 8, 7.5], tan),
            box([5.5, 0, 6, 7, 1, 7.5], dk), box([9, 0, 6, 10.5, 1, 7.5], dk),
            box([6, 12, 1.5, 10, 18.5, 6], neck),
            box([6.25, 17, -1.5, 9.75, 20, 3.5], tan),
            box([6.75, 17.25, -4, 9.25, 19, -1.5], tan), box([7, 18, -4.25, 9, 19, -4], dk),
            box([4.5, 19.5, 1.5, 6.5, 21, 2.5], tan), box([9.5, 19.5, 1.5, 11.5, 21, 2.5], tan),
            box([6.25, 20, 1, 7, 23, 1.75], dk), box([9, 20, 1, 9.75, 23, 1.75], dk),
            box([6.25, 22.5, -1.5, 7, 23.25, 1], dk), box([9, 22.5, -1.5, 9.75, 23.25, 1], dk),
            box([6.25, 23.25, 0, 7, 24.5, 0.75], dk), box([9, 23.25, 0, 9.75, 24.5, 0.75], dk),
            box([6.25, 23.25, -1.5, 7, 24, -0.75], dk), box([9, 23.25, -1.5, 9.75, 24, -0.75], dk)]


def foam_back(dz=0.0):
    tan, w, dk = 'foam', 'white', 'dark'

    def b(x1, y1, z1, x2, y2, z2):
        return [x1, y1, z1 + dz, x2, y2, z2 + dz]

    return [box(b(5, 8, 0, 11, 14, 9), tan), box(b(5.25, 8.5, 9, 10.75, 13.75, 11.5), tan),
            box(b(5, 6.5, 6, 11, 10, 11), {'north': tan, 'south': tan, 'east': tan, 'west': tan, 'up': tan, 'down': w}),
            box(b(5.5, 1, 8, 7, 6.5, 9.5), tan), box(b(9, 1, 8, 10.5, 6.5, 9.5), tan),
            box(b(5.5, 0, 8, 7, 1, 9.5), dk), box(b(9, 0, 8, 10.5, 1, 9.5), dk),
            box(b(7.25, 12, 11.5, 8.75, 14, 12.75), {'north': tan, 'south': w, 'east': tan, 'west': tan, 'up': tan, 'down': w})]


def foam_deer():
    t = {'foam': tex('foam_tan'), 'side': tex('foam_side'), 'white': tex('foam_white'), 'dark': tex('foam_dark'), 'particle': tex('foam_tan')}
    model('foam_deer_target_front', t, foam_front())
    model('foam_deer_target_back', t, foam_back())
    # the item shows the whole deer, shrunk to fit the slot (both halves, the back half moved behind the front)
    els = foam_front() + foam_back(16.0)
    m = {'parent': 'block/block', 'textures': t, 'elements': els, 'display': {
        'gui': {'rotation': [20, 240, 0], 'translation': [0.5, -1.5, 0], 'scale': [0.42, 0.42, 0.42]},
        'ground': {'translation': [0, 3, 0], 'scale': [0.2, 0.2, 0.2]},
        'fixed': {'rotation': [0, 90, 0], 'scale': [0.42, 0.42, 0.42]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.25, 0.25, 0.25]},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.25, 0.25, 0.25]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.28, 0.28, 0.28]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'scale': [0.28, 0.28, 0.28]}}}
    save_json(m, A, 'models', 'item', 'foam_deer_target.json')


MARKER_DIST = [25, 50, 75, 100, 150, 200, 300, 400, 500]


def marker():
    t = {'log': 'minecraft:block/spruce_log', 'log_top': 'minecraft:block/spruce_log_top', 'plank': 'minecraft:block/spruce_planks',
         'cap': 'minecraft:block/stripped_spruce_log', 'particle': 'minecraft:block/spruce_planks'}
    log = {'north': 'log', 'south': 'log', 'east': 'log', 'west': 'log', 'up': 'log_top', 'down': 'log_top'}
    board = el([1.5, 7, 7.25], [14.5, 15, 8.75], {'north': 'num', 'south': 'num', 'east': 'plank', 'west': 'plank', 'up': 'plank', 'down': 'plank'},
               uv={'north': [0, 0, 13, 8], 'south': [0, 0, 13, 8]})
    els = [box([7, 0, 7, 9, 7, 9], log), board, box([1, 15, 7, 15, 16, 9], 'cap')]
    save_json({'parent': 'block/block', 'textures': t, 'elements': els}, A, 'models', 'block', 'range_marker_base.json')
    for n in MARKER_DIST:
        save_json({'parent': f'{FH}:block/range_marker_base', 'textures': {'num': tex(f'marker_{n}')}}, A, 'models', 'block', f'range_marker_{n}.json')


# ============================================================================================ blockstates, items, loot

FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def blockstate(name, props, model_for):
    """props: list of (name, values); model_for(dict) -> model name"""
    variants = {}

    def rec(i, cur):
        if i == len(props):
            for f, y in FACINGS.items():
                key = ','.join([f'facing={f}'] + [f'{k}={v}' for k, v in cur])
                v = {'model': f'{FH}:block/' + model_for(dict(cur))}
                if y:
                    v['y'] = y
                variants[key] = v
            return
        k, vals = props[i]
        for v in vals:
            rec(i + 1, cur + [(k, v)])

    rec(0, [])
    save_json({'variants': variants}, A, 'blockstates', name + '.json')


def block_item(name, model_name):
    save_json({'parent': f'{FH}:block/{model_name}', 'display': ITEM_DISPLAY}, A, 'models', 'item', name + '.json')


def loot(name):
    save_json({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{FH}:{name}'}],
                                                    'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
               'random_sequence': f'{FH}:blocks/{name}'}, D, 'loot_table', 'blocks', name + '.json')


def main():
    save_png(painted_steel(11, (228, 226, 218)), 'block', 'range', 'steel_white.png')
    save_png(painted_steel(12, (230, 108, 30)), 'block', 'range', 'steel_orange.png')
    save_png(painted_steel(13, (236, 194, 42)), 'block', 'range', 'steel_yellow.png')
    save_png(dark_steel(14), 'block', 'range', 'steel_dark.png')
    save_png(foam(15, (152, 112, 74)), 'block', 'range', 'foam_tan.png')
    save_png(foam(15, (152, 112, 74), rings=True), 'block', 'range', 'foam_side.png')
    save_png(foam(16, (232, 226, 214), size=16), 'block', 'range', 'foam_white.png')
    save_png(foam(17, (54, 42, 32), size=16), 'block', 'range', 'foam_dark.png')
    for n in MARKER_DIST:
        save_png(marker_board(n), 'block', 'range', f'marker_{n}.png')
    save_png(flag_tex(), 'block', 'range', 'trail_flag.png')
    # [1.1.9] the trail flag's model and the gear's icons and models now come from gear_art.py
    save_json({'variants': {'': {'model': f'{FH}:block/trail_flag'}}}, A, 'blockstates', 'trail_flag.json')
    save_json({'type': 'minecraft:block', 'pools': [], 'random_sequence': f'{FH}:blocks/trail_flag'}, D, 'loot_table', 'blocks', 'trail_flag.json')

    gong()
    popper()
    silhouette()
    spinner()
    foam_deer()
    marker()
    blockstate('steel_gong', [('swing', ['false', 'true'])], lambda c: 'steel_gong_swing' if c['swing'] == 'true' else 'steel_gong')
    blockstate('steel_popper', [('down', ['false', 'true'])], lambda c: 'steel_popper_down' if c['down'] == 'true' else 'steel_popper')
    blockstate('steel_deer_target', [], lambda c: 'steel_deer_target')
    blockstate('steel_spinner', [('spin', ['0', '1', '2', '3'])], lambda c: 'steel_spinner_' + c['spin'])
    blockstate('foam_deer_target', [('half', ['front', 'back'])], lambda c: 'foam_deer_target_' + c['half'])
    blockstate('range_marker', [('dist', [str(i) for i in range(len(MARKER_DIST))])], lambda c: f'range_marker_{MARKER_DIST[int(c["dist"])]}')
    block_item('steel_gong', 'steel_gong')
    block_item('steel_popper', 'steel_popper')
    block_item('steel_deer_target', 'steel_deer_target')
    block_item('steel_spinner', 'steel_spinner_0')
    block_item('range_marker', 'range_marker_100')
    for n in ('steel_gong', 'steel_popper', 'steel_deer_target', 'steel_spinner', 'foam_deer_target', 'range_marker'):
        loot(n)


if __name__ == '__main__':
    main()
