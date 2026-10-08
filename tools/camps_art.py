#!/usr/bin/env python3
"""Generates the camps workstream's block models, blockstates, item models and textures (original art).
Run from the repo root:  python3 tools/camps_art.py   (writes into patch/assets/frontierhunts/...)"""
import json, os, math, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'patch', 'assets', 'frontierhunts')
def out(path):
    p = os.path.join(ROOT, path); os.makedirs(os.path.dirname(p), exist_ok=True); return p

T = {
 'post': 'frontierhunts:block/board_post', 'shingle': 'frontierhunts:block/board_shingle',
 'walnut': 'frontierhunts:block/display_walnut', 'frame': 'frontierhunts:block/display_walnut_frame',
 'brass': 'frontierhunts:block/display_brass', 'felt': 'frontierhunts:block/display_felt',
 'planks': 'frontierhunts:block/trophy_board_planks', 'iron': 'frontierhunts:block/board_iron',
 'log': 'frontierhunts:block/alpine_pine_log', 'logtop': 'frontierhunts:block/alpine_pine_log_top',
 'stone': 'frontierhunts:block/mossy_stone', 'cord': 'frontierhunts:block/board_cord',
 'header': 'frontierhunts:block/trophy_board_header',
}

def faces(tex, skip=(), top=None):
    f = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        if d in skip: continue
        f[d] = {'texture': '#' + (top if top and d in ('up', 'down') else (tex or 'log'))}
    return f

def el(a, b, tex, top=None, skip=()):
    return {'from': [round(x, 3) for x in a], 'to': [round(x, 3) for x in b], 'faces': faces(tex, skip, top)}

def model(elements, textures, particle, parent=None, display=None):
    m = {'ambientocclusion': True, 'textures': dict(textures, particle=particle), 'elements': elements}
    if parent: m['parent'] = parent
    if display: m['display'] = display
    return m

def used(elements):
    keys = set()
    for e in elements:
        for f in e['faces'].values(): keys.add(f['texture'][1:])
    return {k: T[k] for k in keys}

def wrap_icon(name):
    # [items] item model: flat 32x32 icon in inventory slots (tools/items_icons.py), the 3D model everywhere else
    m = {'loader': 'neoforge:separate_transforms', 'gui_light': 'front',
         'base': {'parent': f'frontierhunts:block/{name}_inventory'},
         'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'frontierhunts:item/{name}'}}}}
    json.dump(m, open(out(f'models/item/{name}.json'), 'w'), indent=1)

# ------------------------------------------------------------------ Big-Buck Board (3 x 2, north-facing, front = -z)
# boxes in origin-block model coordinates: x -16..32, y 0..32
BOARD = []
def B(a, b, tex, top=None): BOARD.append((a, b, tex, top))
B([-15.5, 0, 8.2], [-12, 30.5, 11.8], 'post')          # left post
B([28, 0, 8.2], [31.5, 30.5, 11.8], 'post')             # right post
B([-16, 0, 7.2], [-11.5, 1.6, 12.8], 'post')            # feet
B([27.5, 0, 7.2], [32, 1.6, 12.8], 'post')
B([-12.6, 1.2, 9], [28.6, 30.6, 10.6], 'planks')        # back panel
B([-13.4, 27.4, 8.2], [29.4, 29, 10.8], 'frame')        # top rail
B([-13.4, 0.8, 8.2], [29.4, 2.2, 10.8], 'frame')        # bottom rail
B([-13.4, 10.2, 8.4], [29.4, 10.8, 9.2], 'frame')       # plate shelf rail
B([-10.5, 27.6, 7.4], [26.5, 31.2, 8.4], 'header')      # header board
B([-11, 27.2, 7.1], [27, 27.7, 8.6], 'frame')           # header trim lower
B([-11, 31.1, 7.1], [27, 31.6, 8.6], 'frame')           # header trim upper
B([-16, 30.4, 5.6], [32, 32, 13.4], 'shingle')          # roof cap
B([-8.2, 2.4, 8.5], [24.2, 9.6, 9], 'felt')             # honour-roll felt
for cx in (-8.64, -0.32, 8.0, 16.32, 24.64):             # brass name plates
    B([cx - 3.68, 11.0, 8.55], [cx + 3.68, 13.1, 9], 'brass')
    B([cx - 0.5, 13.2, 8.6], [cx + 0.5, 13.9, 9], 'iron')  # hanger peg under each mount

def clip_board():
    parts = {}
    for p in range(6):
        col, row = p % 3, p // 3
        x0, y0 = (col - 1) * 16, row * 16
        els = []
        for (a, b, tex, top) in BOARD:
            lo = [max(a[0], x0), max(a[1], y0), a[2]]
            hi = [min(b[0], x0 + 16), min(b[1], y0 + 16), b[2]]
            if lo[0] >= hi[0] - 1e-6 or lo[1] >= hi[1] - 1e-6: continue
            skip = []
            if lo[0] > a[0] + 1e-6: skip.append('west')   # cut faces are hidden inside the neighbour part
            if hi[0] < b[0] - 1e-6: skip.append('east')
            if lo[1] > a[1] + 1e-6: skip.append('down')
            if hi[1] < b[1] - 1e-6: skip.append('up')
            els.append(el([lo[0] - x0, lo[1] - y0, lo[2]], [hi[0] - x0, hi[1] - y0, hi[2]], tex, top, skip))
        parts[p] = els
    return parts

def board_files():
    parts = clip_board()
    for p, els in parts.items():
        json.dump(model(els, used(els), T['post']), open(out(f'models/block/big_buck_board_p{p}.json'), 'w'), indent=1)
    variants = {}
    for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for p in range(6):
            v = {'model': f'frontierhunts:block/big_buck_board_p{p}'}
            if rot: v['y'] = rot
            variants[f'facing={facing},part={p}'] = v
    json.dump({'variants': variants}, open(out('blockstates/big_buck_board.json'), 'w'), indent=1)
    # inventory model: whole board shrunk into one block (coordinates scaled by 1/3 around the centre)
    els = []
    for (a, b, tex, top) in BOARD:
        s = 1 / 3.0
        A = [8 + (a[0] - 8) * s, a[1] * s + 2.5, 8 + (a[2] - 9) * 1.2]
        Bb = [8 + (b[0] - 8) * s, b[1] * s + 2.5, 8 + (b[2] - 9) * 1.2]
        els.append(el(A, Bb, tex, top))
    disp = {
        # [items] sane held / ground / frame sizes (vanilla block-item scales); the slot shows the flat icon (see wrap_icon)
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    }
    json.dump(model(els, used(els), T['post'], display=disp), open(out('models/block/big_buck_board_inventory.json'), 'w'), indent=1)
    wrap_icon('big_buck_board')

# ------------------------------------------------------------------ Camp Post (2 tall, north-facing, plaque on the front)
LOWER = [
    el([2.5, 0, 3], [13.5, 2.5, 13], 'stone'),
    el([3.5, 2.5, 2.5], [12, 4.5, 12.5], 'stone'),
    el([5, 4.5, 4.5], [11.5, 6, 11.5], 'stone'),
    el([1.2, 0, 10], [4.6, 1.8, 13.8], 'stone'),
    el([11.6, 0, 1.8], [14.8, 1.5, 5.2], 'stone'),
    el([6.5, 4, 6.5], [9.5, 16, 9.5], 'log', 'logtop', skip=('up',)),
    el([1, 7.6, 5], [15, 14.6, 6.4], 'walnut'),                  # name plaque
    el([0.6, 14.4, 4.7], [15.4, 15.2, 6.6], 'frame'),
    el([0.6, 7.0, 4.7], [15.4, 7.8, 6.6], 'frame'),
    el([0.6, 7.8, 4.7], [1.4, 14.4, 6.6], 'frame'),
    el([14.6, 7.8, 4.7], [15.4, 14.4, 6.6], 'frame'),
    el([6.4, 10.2, 6.3], [9.6, 11.4, 9.7], 'cord'),              # lashing behind the plaque
]
UPPER = [
    el([6.5, 0, 6.5], [9.5, 15, 9.5], 'log', 'logtop', skip=('down',)),
    el([6.0, 15, 6.0], [10, 16, 10], 'iron'),
    el([9.5, 13.6, 7.3], [10.4, 15.2, 8.7], 'iron'),             # flag rings
    el([9.5, 2.6, 7.3], [10.4, 4.2, 8.7], 'iron'),
    el([6.3, 12.2, 6.3], [9.7, 13.2, 9.7], 'cord'),
]

def post_files():
    json.dump(model(LOWER, used(LOWER), T['log']), open(out('models/block/camp_post_lower.json'), 'w'), indent=1)
    json.dump(model(UPPER, used(UPPER), T['log']), open(out('models/block/camp_post_upper.json'), 'w'), indent=1)
    variants = {}
    for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for half in ('lower', 'upper'):
            v = {'model': f'frontierhunts:block/camp_post_{half}'}
            if rot: v['y'] = rot
            variants[f'facing={facing},half={half}'] = v
    json.dump({'variants': variants}, open(out('blockstates/camp_post.json'), 'w'), indent=1)
    # item: both halves squeezed to one block height
    els = []
    for e in LOWER:
        els.append(el([e['from'][0], e['from'][1] / 2, e['from'][2]], [e['to'][0], e['to'][1] / 2, e['to'][2]], None, skip=()))
        els[-1]['faces'] = e['faces']
    for e in UPPER:
        els.append(el([e['from'][0], 8 + e['from'][1] / 2, e['from'][2]], [e['to'][0], 8 + e['to'][1] / 2, e['to'][2]], None))
        els[-1]['faces'] = e['faces']
    # a small furled pennant so the icon reads as a flag
    els.append({'from': [10.4, 10.6, 7.6], 'to': [15.6, 15.2, 8.4], 'faces': {d: {'texture': '#flag'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}})
    tex = used([e for e in els if '#flag' not in json.dumps(e)])
    tex['flag'] = 'frontierhunts:block/camp_flag_item'
    disp = {
        # [items] sane held / ground / frame sizes (vanilla block-item scales); the slot shows the flat icon (see wrap_icon)
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    }
    json.dump(model(els, tex, T['log'], display=disp), open(out('models/block/camp_post_inventory.json'), 'w'), indent=1)
    wrap_icon('camp_post')

# ------------------------------------------------------------------ textures
def clamp(v): return max(0, min(255, int(round(v))))

def planks():
    rnd = random.Random(7)
    im = Image.new('RGBA', (16, 16))
    bases = [(92, 62, 40), (84, 56, 36), (98, 66, 43), (80, 53, 35)]
    for board in range(4):
        base = bases[board]
        x0 = board * 4
        phase = rnd.random() * 6
        for x in range(x0, x0 + 4):
            for y in range(16):
                g = math.sin(y * 0.9 + phase + (x - x0) * 1.7) * 7 + rnd.uniform(-5, 5)
                edge = -16 if x == x0 else (-6 if x == x0 + 3 else 0)
                c = [clamp(base[i] + g + edge) for i in range(3)]
                im.putpixel((x, y), (*c, 255))
        # knot
        ky = rnd.randint(3, 12)
        im.putpixel((x0 + 2, ky), (58, 38, 24, 255)); im.putpixel((x0 + 1, ky), (70, 46, 29, 255))
        # nail heads top and bottom
        if board % 2 == 0:
            im.putpixel((x0 + 1, 1), (64, 52, 44, 255))
    im.save(out('textures/block/trophy_board_planks.png'))

def header():
    # dark oiled walnut plank with a routed, lighter bevel border: the BER paints the title in gold over it
    rnd = random.Random(5)
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            g = math.sin(x * 0.55 + y * 0.12) * 4 + math.sin(x * 2.1 + y) * 2 + rnd.uniform(-3, 3)
            c = [52 + g, 33 + g * 0.7, 21 + g * 0.5]
            if y in (0, 15):
                c = [c[0] + 26, c[1] + 17, c[2] + 10]
            elif y in (1, 14):
                c = [c[0] - 10, c[1] - 7, c[2] - 5]
            im.putpixel((x, y), (*[clamp(v) for v in c], 255))
    im.save(out('textures/block/trophy_board_header.png'))

def flag():
    # grey-scale canvas: tinted to the camp colour at render time. 32 x 20, pole hem on the left.
    rnd = random.Random(11)
    W, H = 32, 20
    im = Image.new('RGBA', (W, H))
    for y in range(H):
        for x in range(W):
            weave = ((x + y) % 2) * 10 + ((x * 3 + y * 5) % 7 == 0) * -8
            v = 214 + weave + rnd.uniform(-6, 6)
            if x < 3: v -= 34 - x * 6                      # rolled hem at the pole
            if y in (1, H - 2): v -= 22                     # top/bottom stitching
            if y == 0 or y == H - 1: v -= 40
            a = 255
            if x >= W - 3:                                  # swallow-tail fly end, frayed
                notch = abs(y - (H - 1) / 2) < (x - (W - 4)) * 1.8
                if notch: a = 0
                elif rnd.random() < 0.25: a = 0
            im.putpixel((x, y), (clamp(v), clamp(v), clamp(v), a))
    im.save(out('textures/entity/camp_flag.png'))
    # emblem: a stitched cream whitetail rack over a skull, alpha only where drawn (hand-placed pixels)
    RACK = [
        "#...#..#.....#..#...#",
        "#...#..#.....#..#...#",
        "#...#..#.....#..#...#",
        ".#..#..#.....#..#..#.",
        ".#..#..#.....#..#..#.",
        "..#.#..#.....#..#.#..",
        "..###..#.....#..###..",
        "...####.#...#.####...",
        "......###...###......",
        "........##.##........",
        ".........###.........",
        "........#####........",
        ".........###.........",
        "..........#..........",
    ]
    em = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    cream = (240, 228, 196, 255); shade = (150, 138, 110, 200)
    ox, oy = 5, 3
    for y, row in enumerate(RACK):
        for x, ch in enumerate(row):
            if ch == '#' and (y + 1 >= len(RACK) or RACK[y + 1][x] != '#') and oy + y + 1 < H:
                em.putpixel((ox + x, oy + y + 1), shade)
    for y, row in enumerate(RACK):
        for x, ch in enumerate(row):
            if ch == '#':
                em.putpixel((ox + x, oy + y), cream)
    em.save(out('textures/entity/camp_flag_emblem.png'))
    # small item texture: pre-tinted forest green pennant with the emblem
    it = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    small = im.resize((16, 10), Image.NEAREST)
    sem = em.resize((16, 10), Image.NEAREST)
    for y in range(10):
        for x in range(16):
            r, g, b, a = small.getpixel((x, y))
            if a == 0: continue
            k = r / 255
            c = (clamp(58 * k * 1.1), clamp(96 * k * 1.1), clamp(52 * k * 1.1), 255)
            er, eg, eb, ea = sem.getpixel((x, y))
            if ea: c = (er, eg, eb, 255)
            it.putpixel((x, y + 3), c)
    it.save(out('textures/block/camp_flag_item.png'))

if __name__ == '__main__':
    board_files(); post_files(); planks(); header(); flag()
    print('ok')
