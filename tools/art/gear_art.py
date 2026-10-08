#!/usr/bin/env python3
"""[1.1.9] Real-looking art for the outfitter's gear: a 32x32 painted icon for the inventory and the creative tab, and a
3D model for the hands, the ground and item frames (neoforge:separate_transforms, as the Handbook does).

    tracking_lamp     blood-tracking light: knurled olive body, finned head, red-and-white LED behind the lens
    shooting_sticks   crossed aluminium legs, rubber V-yoke, a strap, rubber feet
    flagging_tape     a roll of blaze-orange tape on a card core with the tail hanging off
    hand_warmers      two air-activated packets, printed
    coffee_thermos    green enamel vacuum flask, steel cup-lid, handle and a label band
    estrus_lure       amber drop bottle, black cap and nozzle, doe on the label
    milkweed_pods     two split pods on a stem with silk spilling out
    trail_flag block  a twig pushed into the ground with a strip of tape knotted round it

Deterministic: writes into patch/. Run from anywhere.
"""
import math
import os
import random

from PIL import Image, ImageDraw

import range_art as R

A = R.A
FH = 'frontierhunts'
OUT = (22, 18, 16, 255)


def mat(name):
    return f'{FH}:item/gear/{name}'


# ============================================================================================ material textures (16x16)

def noise_tex(seed, base, amp=0.06, streak=None, size=16):
    r = random.Random(seed)
    im = Image.new('RGBA', (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            k = 1.0 + r.uniform(-amp, amp)
            if streak == 'v' and x % 2 == 0:
                k *= 0.9
            if streak == 'h' and y % 2 == 0:
                k *= 0.9
            px[x, y] = R.shade(base, k)
    return im


def knurl(seed, base):
    im = noise_tex(seed, base, 0.04)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                px[x, y] = R.shade(base, 0.72)
    return im


def lens():
    """the face of the lamp: a steel reflector bowl, a red LED ring and the white centre LED behind the glass"""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.6:
                c = (255, 250, 236)
            elif d < 2.6:
                c = (224, 224, 214)
            elif d < 4.2:
                c = (236, 46, 40) if (int(math.degrees(math.atan2(y - 7.5, x - 7.5)) + 360) // 45) % 2 == 0 else (150, 24, 22)
            elif d < 6.4:
                k = 0.75 + 0.25 * math.cos((d - 4.2) * 2.6)
                c = R.shade((190, 196, 200), k)[:3]
            else:
                c = (52, 54, 50)
            px[x, y] = c + (255,)
    px[5, 5] = (255, 255, 255, 255)
    px[6, 5] = (240, 240, 240, 255)
    return im


def fins():
    im = noise_tex(31, (46, 48, 44), 0.04)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if y % 3 == 0:
                px[x, y] = (24, 24, 22, 255)
            elif y % 3 == 1:
                px[x, y] = (70, 72, 66, 255)
    return im


def label_thermos():
    im = noise_tex(41, (222, 208, 160), 0.03)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 1], fill=(120, 34, 26, 255))
    d.rectangle([0, 14, 15, 15], fill=(120, 34, 26, 255))
    d.rectangle([3, 5, 12, 6], fill=(58, 46, 34, 255))
    d.rectangle([5, 9, 10, 9], fill=(98, 82, 60, 255))
    d.rectangle([2, 11, 13, 11], fill=(150, 130, 96, 255))
    return im


def label_lure():
    im = noise_tex(42, (236, 230, 214), 0.03)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 2], fill=(150, 30, 26, 255))
    d.rectangle([0, 13, 15, 15], fill=(150, 30, 26, 255))
    doe = (92, 60, 36, 255)
    # a standing doe in silhouette: body, neck, head with ears, legs
    d.rectangle([4, 7, 10, 9], fill=doe)
    d.line([(10, 7), (12, 4)], fill=doe, width=2)
    d.rectangle([12, 3, 13, 4], fill=doe)
    d.point([(12, 2), (13, 2)], fill=doe)
    for x in (4, 6, 9, 10):
        d.line([(x, 10), (x, 11)], fill=doe)
    d.point([(3, 7)], fill=doe)
    return im


def warmer_front():
    im = noise_tex(43, (232, 112, 38), 0.04)
    d = ImageDraw.Draw(im)
    px = im.load()
    d.rectangle([0, 0, 15, 0], fill=(196, 88, 28, 255))
    d.rectangle([0, 15, 15, 15], fill=(196, 88, 28, 255))
    for x in range(0, 16, 2):  # crimped seals top and bottom
        px[x, 0] = (170, 74, 24, 255)
        px[x, 15] = (170, 74, 24, 255)
    d.rectangle([0, 9, 15, 11], fill=(246, 240, 226, 255))
    d.rectangle([2, 10, 13, 10], fill=(120, 40, 26, 255))
    # the flame
    d.polygon([(7, 2), (10, 5), (9, 8), (5, 8), (4, 5)], fill=(250, 216, 70, 255))
    d.polygon([(7, 4), (8, 6), (7, 8), (6, 6)], fill=(238, 70, 38, 255))
    d.rectangle([3, 13, 12, 13], fill=(255, 196, 140, 255))
    return im


def warmer_back():
    im = noise_tex(44, (226, 214, 196), 0.05)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if (x * 7 + y * 3) % 5 == 0:
                px[x, y] = (196, 184, 164, 255)  # the iron powder inside shows through the fleece
    for x in range(0, 16, 2):
        px[x, 0] = (180, 168, 150, 255)
        px[x, 15] = (180, 168, 150, 255)
    return im


def tape_side():
    """the flat side of the roll: wound layers of orange tape, darker towards the core"""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.6:
                c = (40, 30, 22)  # the hole
            elif d < 3.6:
                c = (176, 146, 104)  # card core
            else:
                k = 0.86 + 0.14 * math.cos(d * 3.1) - 0.05 * (6.5 - min(6.5, d)) / 6.5
                c = R.shade((236, 104, 30), k)[:3]
            px[x, y] = c + (255,)
    return im


def tape_face():
    im = noise_tex(45, (240, 108, 30), 0.03, streak='h')
    px = im.load()
    for x in range(16):
        px[x, 4] = (255, 160, 90, 255)  # the wet-look sheen of the vinyl
    return im


def pod():
    im = noise_tex(46, (126, 134, 84), 0.05)
    px = im.load()
    r = random.Random(46)
    for _ in range(18):  # the soft warts on a milkweed pod
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = (164, 172, 112, 255)
        if y + 1 < 16:
            px[x, y + 1] = (96, 104, 62, 255)
    for x in range(16):
        px[x, 8] = R.shade((126, 134, 84), 0.8)
    return im


def pod_inside():
    im = noise_tex(47, (222, 214, 176), 0.04)
    px = im.load()
    r = random.Random(47)
    for _ in range(26):  # flat brown seeds packed like scales
        x, y = r.randrange(15), r.randrange(15)
        px[x, y] = (132, 92, 52, 255)
        px[x + 1, y] = (110, 74, 42, 255)
    return im


def silk():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    r = random.Random(48)
    for y in range(16):
        for x in range(16):
            if r.random() < 0.86:
                k = 0.9 + r.random() * 0.12
                px[x, y] = R.shade((246, 244, 236), k)
    return im


def stick_wood():
    return noise_tex(49, (118, 92, 62), 0.07, streak='v')


def bark_twig():
    im = noise_tex(50, (92, 70, 46), 0.08, streak='v')
    px = im.load()
    for y in range(0, 16, 5):
        px[7, y] = (60, 44, 28, 255)
    return im


def materials():
    tex = {
        'olive_alu': knurl(30, (86, 94, 66)),
        'olive_smooth': noise_tex(32, (92, 100, 70), 0.03),
        'fins': fins(),
        'lens': lens(),
        'black_rubber': noise_tex(33, (34, 34, 36), 0.05),
        'gunmetal': noise_tex(34, (66, 68, 70), 0.04, streak='h'),
        'steel': noise_tex(35, (176, 180, 182), 0.04, streak='v'),
        'alu_leg': noise_tex(36, (128, 122, 104), 0.05, streak='v'),
        'strap': noise_tex(37, (40, 44, 36), 0.05, streak='h'),
        'enamel_green': noise_tex(38, (60, 104, 66), 0.03, streak='v'),
        'label_thermos': label_thermos(),
        'amber': noise_tex(39, (150, 82, 26), 0.04),
        'label_lure': label_lure(),
        'warmer_front': warmer_front(),
        'warmer_back': warmer_back(),
        'tape_side': tape_side(),
        'tape_face': tape_face(),
        'pod': pod(),
        'pod_inside': pod_inside(),
        'silk': silk(),
        'stem': noise_tex(51, (104, 100, 64), 0.06, streak='v'),
        'stick': stick_wood(),
        'twig': bark_twig(),
    }
    for k, im in tex.items():
        R.save_png(im, 'item', 'gear', k + '.png')
    return tex


# ============================================================================================ 3D models

def el(fr, to, t, uv=None, rot=None, faces=None):
    """an element; t: texture variable for all faces or {face: var}; uv: {face: [u0,v0,u1,v1]} or 'full'"""
    e = {'from': [round(v, 3) for v in fr], 'to': [round(v, 3) for v in to], 'faces': {}}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        if faces is not None and f not in faces:
            continue
        var = t.get(f) if isinstance(t, dict) else t
        if var is None:
            continue
        face = {'texture': '#' + var}
        if uv == 'full':
            face['uv'] = [0, 0, 16, 16]
        elif uv and f in uv:
            face['uv'] = uv[f]
        e['faces'][f] = face
    if rot:
        e['rotation'] = rot
    return e


def cyl_y(cx, cz, r, y0, y1, t, caps=None, side_uv=None):
    """an upright 'cylinder': three boxes whose union is a 12-sided prism of apothem r (no rotation needed)"""
    s = r * 0.4142
    q = r * 0.7071
    out = []
    for hx, hz in ((r, s), (s, r), (q, q)):
        tt = {f: t for f in ('north', 'south', 'east', 'west')}
        if caps:
            tt['up'] = caps.get('up')
            tt['down'] = caps.get('down')
        out.append(el([cx - hx, y0, cz - hz], [cx + hx, y1, cz + hz], tt, uv=side_uv))
    return out


def cyl_z(cx, cy, r, z0, z1, t, caps=None):
    """a 'cylinder' along z"""
    s = r * 0.4142
    q = r * 0.7071
    out = []
    for hx, hy in ((r, s), (s, r), (q, q)):
        tt = {f: t for f in ('east', 'west', 'up', 'down')}
        if caps:
            tt['north'] = caps.get('north')
            tt['south'] = caps.get('south')
        out.append(el([cx - hx, cy - hy, z0], [cx + hx, cy + hy, z1], tt))
    return out


def cyl_x(cy, cz, r, x0, x1, t, caps=None, cap_uv=None):
    s = r * 0.4142
    q = r * 0.7071
    out = []
    for hy, hz in ((r, s), (s, r), (q, q)):
        tt = {f: t for f in ('north', 'south', 'up', 'down')}
        if caps:
            tt['east'] = caps.get('east')
            tt['west'] = caps.get('west')
        uv = None
        if cap_uv:
            # the caps show the whole round texture, cropped to this box's share of the disc
            uv = {f: [8 - hz / r * 8, 8 - hy / r * 8, 8 + hz / r * 8, 8 + hy / r * 8] for f in ('east', 'west')}
        out.append(el([x0, cy - hy, cz - hz], [x1, cy + hy, cz + hz], tt, uv=uv))
    return out


def upright_display(scale=0.55, gui_scale=0.8):
    return {
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [scale, scale, scale]},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [scale, scale, scale]},
        'firstperson_righthand': {'rotation': [0, 30, 0], 'translation': [0, 2, 0], 'scale': [scale * 0.9] * 3},
        'firstperson_lefthand': {'rotation': [0, -30, 0], 'translation': [0, 2, 0], 'scale': [scale * 0.9] * 3},
        'ground': {'translation': [0, 2, 0], 'scale': [0.45, 0.45, 0.45]},
        'fixed': {'rotation': [0, 180, 0], 'scale': [0.8, 0.8, 0.8]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
        'gui': {'rotation': [25, -35, 0], 'scale': [gui_scale] * 3},
    }


def write_item(name, textures, elements, display):
    textures = dict(textures)
    textures.setdefault('particle', next(iter(textures.values())))
    R.save_json({'credit': 'Frontier Hunts [outfitter] - original model', 'textures': textures, 'elements': elements, 'display': display},
                A, 'models', 'item', name + '_3d.json')
    R.save_json({'loader': 'neoforge:separate_transforms', 'gui_light': 'front', 'base': {'parent': f'{FH}:item/{name}_3d'},
                 'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{FH}:item/{name}'}}}},
                A, 'models', 'item', name + '.json')


def lamp_model():
    t = {'body': mat('olive_alu'), 'smooth': mat('olive_smooth'), 'fins': mat('fins'), 'lens': mat('lens'), 'rubber': mat('black_rubber'),
         'metal': mat('gunmetal')}
    e = []
    # head on top (the light points up the model's y; the hand transforms aim it forward)
    e += cyl_y(8, 8, 1.6, 1.0, 9.0, 'body')
    e += cyl_y(8, 8, 1.75, 0.0, 1.2, 'rubber', caps={'down': 'rubber'})  # tail cap
    e.append(el([7.3, -0.3, 7.3], [8.7, 0.0, 8.7], 'rubber'))  # the click switch
    e += cyl_y(8, 8, 1.85, 4.0, 5.0, 'smooth')  # grip ring
    e += cyl_y(8, 8, 1.9, 9.0, 11.5, 'fins')  # cooling fins flaring to the head
    e += cyl_y(8, 8, 2.4, 11.5, 14.6, 'smooth')
    e += cyl_y(8, 8, 2.55, 14.6, 15.4, 'metal', caps={'up': 'metal'})  # bezel
    e.append(el([5.8, 15.41, 5.8], [10.2, 15.42, 10.2], 'lens', uv='full', faces=('up',)))
    # pocket clip and the colour switch
    e.append(el([9.55, 5.0, 7.4], [9.9, 10.5, 8.6], 'metal'))
    e.append(el([6.5, 12.6, 5.4], [7.6, 13.4, 5.6], 'rubber'))
    e.append(el([8.4, 12.6, 5.4], [9.5, 13.4, 5.6], {'north': 'fins', 'south': 'fins', 'east': 'fins', 'west': 'fins', 'up': 'fins', 'down': 'fins'}))
    d = upright_display(0.6)
    # aim it: in the first-person hand the head points ahead, a touch up; held at the side in third person
    d['firstperson_righthand'] = {'rotation': [-80, 8, 0], 'translation': [1, 3, -2], 'scale': [0.62, 0.62, 0.62]}
    d['firstperson_lefthand'] = {'rotation': [-80, -8, 0], 'translation': [-1, 3, -2], 'scale': [0.62, 0.62, 0.62]}
    d['thirdperson_righthand'] = {'rotation': [-90, 0, 0], 'translation': [0, 1.5, -2.5], 'scale': [0.6, 0.6, 0.6]}
    d['thirdperson_lefthand'] = {'rotation': [-90, 0, 0], 'translation': [0, 1.5, -2.5], 'scale': [0.6, 0.6, 0.6]}
    d['ground'] = {'rotation': [90, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]}
    write_item('tracking_lamp', t, e, d)


def sticks_model():
    t = {'leg': mat('alu_leg'), 'rubber': mat('black_rubber'), 'strap': mat('strap')}
    e = []
    # two legs crossing just under the yoke, splayed 22.5 degrees each way
    for ang, x in ((-22.5, 8.0), (22.5, 8.0)):
        e.append(el([x - 0.5, 0.0, 7.5], [x + 0.5, 15.0, 8.5], 'leg', rot={'angle': ang, 'axis': 'z', 'origin': [8.0, 12.5, 8.0]}))
        # rubber foot
        e.append(el([x - 0.65, 0.0, 7.35], [x + 0.65, 1.2, 8.65], 'rubber', rot={'angle': ang, 'axis': 'z', 'origin': [8.0, 12.5, 8.0]}))
        # the twist lock half way down
        e.append(el([x - 0.7, 6.5, 7.3], [x + 0.7, 7.5, 8.7], 'rubber', rot={'angle': ang, 'axis': 'z', 'origin': [8.0, 12.5, 8.0]}))
    # the rubber V-yoke the rifle rests in
    e.append(el([7.2, 12.0, 7.2], [8.8, 13.6, 8.8], 'rubber'))
    e.append(el([7.4, 13.0, 7.3], [8.4, 16.0, 8.7], 'rubber', rot={'angle': 22.5, 'axis': 'z', 'origin': [8.0, 13.2, 8.0]}))
    e.append(el([7.6, 13.0, 7.3], [8.6, 16.0, 8.7], 'rubber', rot={'angle': -22.5, 'axis': 'z', 'origin': [8.0, 13.2, 8.0]}))
    # the strap that holds them together for carrying, round both legs low down
    e.append(el([4.0, 3.6, 7.2], [12.0, 4.4, 8.8], 'strap'))
    d = upright_display(0.6)
    d['firstperson_righthand'] = {'rotation': [0, 20, 10], 'translation': [0, 1, 0], 'scale': [0.6, 0.6, 0.6]}
    d['firstperson_lefthand'] = {'rotation': [0, -20, -10], 'translation': [0, 1, 0], 'scale': [0.6, 0.6, 0.6]}
    write_item('shooting_sticks', t, e, d)


def tape_model():
    t = {'face': mat('tape_face'), 'side': mat('tape_side')}
    e = cyl_x(7.0, 8.5, 5.0, 5.4, 10.6, 'face', caps={'east': 'side', 'west': 'side'}, cap_uv=True)
    # the tail of tape peeled off the front of the roll and hanging down
    e.append(el([5.5, 0.6, 3.36], [10.5, 7.0, 3.5], 'face'))
    e.append(el([5.5, -3.4, 3.36], [10.5, 0.8, 3.5], 'face', rot={'angle': 22.5, 'axis': 'x', 'origin': [8.0, 0.7, 3.5]}))
    d = upright_display(0.55)
    d['ground'] = {'translation': [0, 3, 0], 'scale': [0.45, 0.45, 0.45]}
    write_item('flagging_tape', t, e, d)


def warmers_model():
    t = {'front': mat('warmer_front'), 'back': mat('warmer_back')}
    e = []
    face = {'north': 'back', 'south': 'front', 'east': 'back', 'west': 'back', 'up': 'back', 'down': 'back'}
    e.append(el([3.0, 1.0, 9.0], [11.0, 13.0, 10.0], face, uv={'south': [0, 0, 16, 16], 'north': [0, 0, 16, 16]}))
    # the second packet behind, leaning
    e.append(el([6.0, 2.0, 7.2], [14.0, 14.0, 8.2], face, uv={'south': [0, 0, 16, 16], 'north': [0, 0, 16, 16]},
                rot={'angle': -22.5, 'axis': 'z', 'origin': [10.0, 2.0, 7.7]}))
    d = upright_display(0.5)
    d['ground'] = {'rotation': [90, 0, 0], 'translation': [0, 2, 0], 'scale': [0.45, 0.45, 0.45]}
    write_item('hand_warmers', t, e, d)


def thermos_model():
    t = {'enamel': mat('enamel_green'), 'label': mat('label_thermos'), 'steel': mat('steel'), 'rubber': mat('black_rubber')}
    e = []
    e += cyl_y(8, 8, 2.6, 0.0, 11.0, 'enamel', caps={'down': 'steel'})
    e += cyl_y(8, 8, 2.65, 4.0, 7.5, 'label', side_uv={f: [0, 0, 16, 16] for f in ('north', 'south', 'east', 'west')})
    e += cyl_y(8, 8, 2.3, 11.0, 12.0, 'steel')  # shoulder
    e += cyl_y(8, 8, 2.75, 12.0, 15.5, 'steel', caps={'up': 'steel'})  # the cup that screws on as the lid
    e += cyl_y(8, 8, 2.8, 15.0, 15.5, 'rubber')
    # the fold-out handle on the side
    e.append(el([10.6, 3.0, 7.4], [12.0, 3.8, 8.6], 'rubber'))
    e.append(el([11.4, 3.0, 7.4], [12.2, 10.0, 8.6], 'rubber'))
    e.append(el([10.6, 9.2, 7.4], [12.0, 10.0, 8.6], 'rubber'))
    write_item('coffee_thermos', t, e, upright_display(0.55))


def lure_model():
    t = {'amber': mat('amber'), 'label': mat('label_lure'), 'cap': mat('black_rubber')}
    e = []
    e.append(el([5.5, 0.0, 7.0], [10.5, 8.0, 9.0], {'north': 'amber', 'south': 'label', 'east': 'amber', 'west': 'amber', 'up': 'amber', 'down': 'amber'},
                uv={'south': [0, 0, 16, 16]}))
    e.append(el([5.8, 0.0, 6.6], [10.2, 8.0, 9.4], 'amber'))
    e.append(el([6.4, 8.0, 7.2], [9.6, 9.0, 8.8], 'amber'))  # shoulders
    e += cyl_y(8, 8, 1.3, 9.0, 11.0, 'cap', caps={'up': 'cap'})
    e += cyl_y(8, 8, 0.5, 11.0, 13.2, 'cap', caps={'up': 'cap'})  # the drip nozzle
    write_item('estrus_lure', t, e, upright_display(0.6))


def milkweed_model():
    t = {'pod': mat('pod'), 'inside': mat('pod_inside'), 'silk': mat('silk'), 'stem': mat('stem')}
    e = []
    e.append(el([7.6, 0.0, 7.6], [8.4, 6.0, 8.4], 'stem'))
    for side, ang in ((-1, 22.5), (1, -22.5)):
        x0 = 8.0 + side * 2.2
        # the pod: a long teardrop split open along the inner seam
        e.append(el([x0 - 1.4, 5.0, 6.6], [x0 + 1.4, 14.0, 9.4], {'north': 'pod', 'south': 'pod', 'east': 'pod' if side < 0 else 'inside',
                                                                  'west': 'inside' if side < 0 else 'pod', 'up': 'pod', 'down': 'pod'},
                    rot={'angle': ang, 'axis': 'z', 'origin': [8.0, 5.0, 8.0]}))
        e.append(el([x0 - 1.0, 13.8, 7.0], [x0 + 1.0, 16.0, 9.0], 'pod', rot={'angle': ang, 'axis': 'z', 'origin': [8.0, 5.0, 8.0]}))
        # silk spilling from the seam
        e.append(el([x0 - 0.6 - side * 1.0, 9.0, 6.9], [x0 + 0.6 - side * 1.0, 15.0, 9.1], 'silk', rot={'angle': ang, 'axis': 'z', 'origin': [8.0, 5.0, 8.0]}))
    e.append(el([6.6, 14.8, 6.8], [9.4, 16.6, 9.2], 'silk'))
    e.append(el([5.0, 16.0, 7.4], [7.2, 17.4, 8.8], 'silk', rot={'angle': 22.5, 'axis': 'z', 'origin': [6.0, 16.0, 8.0]}))
    e.append(el([8.8, 16.2, 7.2], [11.2, 17.8, 8.6], 'silk', rot={'angle': -22.5, 'axis': 'z', 'origin': [10.0, 16.0, 8.0]}))
    write_item('milkweed_pods', t, e, upright_display(0.55))


def trail_flag_block():
    """the flag in the world: a twig pushed into the ground, a strip of tape knotted round it and two tails hanging"""
    t = {'twig': mat('twig'), 'tape': mat('tape_face'), 'particle': mat('tape_face')}
    e = []
    e.append(el([7.5, 0.0, 7.5], [8.5, 12.0, 8.5], 'twig'))
    e.append(el([7.7, 7.0, 8.5], [8.3, 9.0, 9.6], 'twig', rot={'angle': 22.5, 'axis': 'x', 'origin': [8.0, 7.0, 8.5]}))  # a side shoot
    e.append(el([7.25, 9.6, 7.25], [8.75, 10.6, 8.75], 'tape'))  # the knot
    e.append(el([8.2, 3.2, 8.4], [9.5, 10.2, 8.55], 'tape', rot={'angle': -22.5, 'axis': 'z', 'origin': [8.2, 10.2, 8.5]}))
    e.append(el([6.6, 4.4, 7.45], [7.8, 10.2, 7.6], 'tape', rot={'angle': 22.5, 'axis': 'z', 'origin': [7.8, 10.2, 7.5]}))
    R.save_json({'parent': 'block/block', 'render_type': 'minecraft:cutout', 'textures': t, 'elements': e}, A, 'models', 'block', 'trail_flag.json')


# ============================================================================================ 32x32 icons

def cyl_shade(t):
    """brightness across a cylinder lit from the upper left (t = 0 left edge .. 1 right edge)"""
    return 0.58 + 0.55 * math.sin(math.pi * (0.12 + t * 0.8)) + (0.22 if 0.2 < t < 0.32 else 0.0)


def vcyl(px, x0, x1, y0, y1, base, hi=None):
    for x in range(x0, x1 + 1):
        t = (x - x0 + 0.5) / (x1 - x0 + 1)
        c = R.shade(base, cyl_shade(t))
        for y in range(y0, y1 + 1):
            px[x, y] = c


def outline32(im):
    src = im.load()
    out = im.copy()
    o = out.load()
    n = im.size[0]
    for y in range(n):
        for x in range(n):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < n and 0 <= ny < n and src[nx, ny][3] > 0:
                        o[x, y] = OUT
                        break
    return out


def rotate_paste(dst, src, angle, center):
    """paste a sprite drawn upright, rotated about its centre (nearest neighbour, keeps pixel art crisp)"""
    rot = src.rotate(angle, resample=Image.NEAREST, expand=True)
    dst.alpha_composite(rot, (int(center[0] - rot.size[0] / 2), int(center[1] - rot.size[1] / 2)))


def icon_lamp():
    """the light on the diagonal, head up and right, its lens glowing red with the white LED in the middle"""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    ax, ay = 0.7071, -0.7071  # along the light, tail to head
    nx, ny = 0.7071, 0.7071  # across it

    def prof(s):
        """half-width and colour along the light (s from 0 at the tail to 1 at the bezel)"""
        if s < 0.1:
            return 3.0, (34, 34, 36), 'cap'
        if s < 0.58:
            return 2.6, (86, 94, 66), 'knurl'
        if s < 0.62:
            return 3.0, (102, 110, 80), 'ring'
        if s < 0.74:
            return 2.6 + (s - 0.62) / 0.12 * 1.4, (50, 52, 48), 'fins'
        if s < 0.92:
            return 4.0, (92, 100, 70), 'head'
        return 4.4, (70, 72, 74), 'bezel'
    x0, y0, length = 5.0, 27.0, 26.0
    for y in range(32):
        for x in range(32):
            u = ((x + 0.5 - x0) * ax + (y + 0.5 - y0) * ay) / length
            v = (x + 0.5 - x0) * nx + (y + 0.5 - y0) * ny
            if not 0.0 <= u <= 1.0:
                continue
            hw, col, part = prof(u)
            if abs(v) > hw:
                continue
            t = (v + hw) / (2 * hw)
            k = cyl_shade(1.0 - t)
            if part == 'knurl' and (x + y) % 3 == 0:
                k *= 0.72
            if part == 'fins' and int(u * length) % 2 == 0:
                k *= 0.6
            px[x, y] = R.shade(col, k)
    d = ImageDraw.Draw(im)
    # the lens face, seen a little from the side: steel reflector, red LED ring, white LED
    d.ellipse([22, 2, 30, 10], fill=(150, 156, 160, 255))
    d.ellipse([23, 3, 29, 9], fill=(200, 40, 36, 255))
    d.ellipse([24.5, 4.5, 27.5, 7.5], fill=(250, 244, 232, 255))
    px[24, 4] = (255, 255, 255, 255)
    px[23, 4] = (255, 170, 160, 255)
    # pocket clip down the body
    for i in range(7):
        x, y = 13 + i, 20 - i
        px[x + 2, y + 1] = (64, 66, 68, 255)
    im = outline32(im)
    px = im.load()
    # a faint red glow off the lens
    for x, y, a in ((31, 1, 120), (30, 0, 120), (31, 0, 90), (29, 0, 60), (31, 2, 60)):
        if px[x, y][3] == 0 or px[x, y] == OUT:
            px[x, y] = (255, 110, 90, a)
    return im


def icon_sticks():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    leg, hi, dk = (140, 132, 112, 255), (186, 178, 156, 255), (92, 86, 72, 255)
    for (x0, y0, x1, y1) in ((5, 31, 19, 4), (27, 31, 13, 4)):
        d.line([(x0, y0), (x1, y1)], fill=dk, width=3)
        d.line([(x0 + (1 if x0 < x1 else -1), y0), (x1 + (1 if x0 < x1 else -1), y1)], fill=leg, width=2)
        d.line([(x0 + (1 if x0 < x1 else 0), y0 - 1), (x1 + (1 if x0 < x1 else 0), y1 + 1)], fill=hi, width=1)
    # twist locks and feet
    for (x, y) in ((11, 19), (21, 19)):
        d.rectangle([x - 1, y - 1, x + 2, y + 1], fill=(36, 36, 38, 255))
    for x in (4, 26):
        d.rectangle([x, 29, x + 3, 31], fill=(30, 30, 32, 255))
    # the rubber V-yoke
    d.polygon([(10, 1), (13, 1), (16, 6), (19, 1), (22, 1), (18, 8), (14, 8)], fill=(34, 34, 36, 255))
    d.line([(11, 1), (15, 7)], fill=(70, 70, 74, 255))
    d.rectangle([14, 8, 18, 10], fill=(46, 46, 48, 255))
    # strap
    d.line([(7, 25), (25, 25)], fill=(44, 50, 38, 255), width=2)
    d.line([(7, 24), (25, 24)], fill=(70, 78, 60, 255))
    d.rectangle([15, 23, 17, 26], fill=(150, 150, 154, 255))  # buckle
    return outline32(im)


def icon_tape():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    cx, cy = 14.0, 13.0
    for y in range(32):
        for x in range(32):
            # the roll seen three-quarters: an elliptical side over a short drum
            dx, dy = (x - cx) / 11.0, (y - cy) / 10.0
            dd = math.hypot(dx, dy)
            side = math.hypot((x - cx - 2.0) / 11.0, (y - cy - 3.0) / 10.0)
            if dd <= 1.0:
                if dd < 0.32:
                    c = (34, 26, 20) if dd < 0.24 else (178, 148, 106)
                else:
                    k = 0.88 + 0.12 * math.cos(dd * 34.0) + 0.14 * (-dx - dy) * 0.5
                    c = R.shade((238, 106, 30), k)[:3]
                px[x, y] = c + (255,)
            elif side <= 1.0:
                k = 0.72 + 0.2 * (x - cx) / 11.0
                if (y + x) % 7 == 0:
                    k += 0.18  # the vinyl's sheen
                px[x, y] = R.shade((226, 96, 26), k)
    d = ImageDraw.Draw(im)
    # the tail of tape fluttering down
    d.polygon([(22, 19), (27, 21), (26, 26), (29, 31), (24, 31), (22, 26)], fill=(232, 100, 28, 255))
    d.line([(23, 20), (24, 27)], fill=(255, 162, 96, 255))
    d.line([(27, 22), (26, 26)], fill=(186, 72, 20, 255))
    return outline32(im)


def icon_warmers():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    back = warmer_back().resize((16, 22), Image.NEAREST)
    front = warmer_front().resize((18, 24), Image.NEAREST)
    s1 = Image.new('RGBA', (18, 24), (0, 0, 0, 0))
    s1.alpha_composite(back.resize((18, 24), Image.NEAREST))
    s1 = outline32(_pad(s1, 26))
    rotate_paste(im, s1, -18, (19, 13))
    s2 = Image.new('RGBA', (18, 24), (0, 0, 0, 0))
    s2.alpha_composite(front)
    # shading: the pouch bulges in the middle
    p2 = s2.load()
    for y in range(24):
        for x in range(18):
            k = 0.82 + 0.22 * math.sin(math.pi * (x + 0.5) / 18) - (0.08 if y in (0, 23) else 0.0)
            c = p2[x, y]
            p2[x, y] = R.shade(c[:3], k)
    s2 = outline32(_pad(s2, 26))
    rotate_paste(im, s2, 8, (12, 18))
    return im


def _pad(im, n):
    out = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    out.alpha_composite(im, ((n - im.size[0]) // 2, (n - im.size[1]) // 2))
    return out


def icon_thermos():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    d = ImageDraw.Draw(im)
    vcyl(px, 9, 20, 2, 8, (180, 184, 186))  # cup-lid
    vcyl(px, 9, 20, 8, 8, (70, 72, 74))
    vcyl(px, 9, 20, 3, 3, (210, 214, 216))
    vcyl(px, 10, 19, 9, 10, (150, 154, 156))  # shoulder
    vcyl(px, 9, 20, 11, 29, (62, 108, 68))
    vcyl(px, 9, 20, 16, 22, (224, 210, 162))  # label band
    vcyl(px, 9, 20, 16, 16, (126, 36, 28))
    vcyl(px, 9, 20, 22, 22, (126, 36, 28))
    d.rectangle([12, 18, 17, 18], fill=(60, 48, 36, 255))
    d.rectangle([13, 20, 16, 20], fill=(110, 92, 70, 255))
    vcyl(px, 10, 19, 30, 30, (120, 124, 126))
    # handle
    d.rectangle([21, 12, 24, 13], fill=(34, 34, 36, 255))
    d.rectangle([23, 12, 24, 26], fill=(34, 34, 36, 255))
    d.rectangle([21, 25, 24, 26], fill=(34, 34, 36, 255))
    d.line([(23, 13), (23, 25)], fill=(66, 66, 70, 255))
    # steam curling off the cup
    for i, (x, y) in enumerate(((13, 1), (14, 0), (17, 1))):
        px[x, y] = (236, 236, 236, 120 - i * 25)
    return outline32(im)


def icon_lure():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    d = ImageDraw.Draw(im)
    vcyl(px, 9, 22, 12, 30, (156, 86, 28))  # amber bottle
    vcyl(px, 11, 20, 10, 11, (156, 86, 28))
    # the liquid line seen through the plastic
    for x in range(9, 23):
        for y in range(12, 15):
            c = px[x, y]
            px[x, y] = R.shade(c[:3], 1.25)
    lab = label_lure().resize((14, 13), Image.NEAREST)
    lp = lab.load()
    for y in range(13):
        for x in range(14):
            c = lp[x, y]
            px[9 + x, 16 + y] = R.shade(c[:3], cyl_shade((x + 0.5) / 14) * 0.85)
    vcyl(px, 11, 20, 5, 9, (40, 40, 42))  # cap
    for y in range(5, 10):
        for x in range(11, 21, 2):
            px[x, y] = R.shade((40, 40, 42), 0.75)  # ribbed
    vcyl(px, 14, 17, 1, 4, (30, 30, 32))  # nozzle
    px[15, 0] = (30, 30, 32, 255)
    # a drop on the nozzle tip
    px[17, 4] = (196, 140, 60, 255)
    return outline32(im)


def icon_milkweed():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pod, hi, dk = (122, 130, 80, 255), (166, 174, 112, 255), (82, 88, 52, 255)
    d.line([(16, 31), (16, 22)], fill=(98, 88, 56, 255), width=2)
    d.line([(16, 24), (19, 22)], fill=(98, 88, 56, 255))
    # left pod, split, cream seed-bed showing
    d.polygon([(15, 23), (8, 18), (5, 10), (7, 3), (10, 8), (14, 15)], fill=pod)
    d.polygon([(14, 21), (10, 15), (9, 9), (11, 11), (14, 17)], fill=(220, 210, 172, 255))
    # right pod
    d.polygon([(17, 23), (24, 18), (27, 10), (25, 3), (22, 8), (18, 15)], fill=pod)
    d.polygon([(18, 21), (22, 15), (23, 9), (21, 11), (18, 17)], fill=(220, 210, 172, 255))
    d.line([(6, 9), (9, 17)], fill=hi)
    d.line([(26, 9), (23, 17)], fill=dk)
    r = random.Random(7)
    px = im.load()
    for _ in range(14):  # seeds on the beds
        x, y = r.randint(10, 22), r.randint(10, 20)
        if px[x, y][:3] == (220, 210, 172):
            px[x, y] = (128, 88, 50, 255)
    for _ in range(10):  # warts on the skins
        x, y = r.randint(5, 27), r.randint(4, 21)
        if px[x, y][:3] == pod[:3]:
            px[x, y] = hi
    im = outline32(im)
    px = im.load()
    # silk bursting from the tips and drifting away (soft edges, no outline)
    for cx, cy, rr in ((7, 3, 3.2), (25, 3, 3.2), (11, 6, 2.2), (21, 6, 2.2), (16, 2, 2.0), (29, 1, 1.4), (3, 1, 1.2)):
        for y in range(32):
            for x in range(32):
                dd = math.hypot(x - cx, y - cy)
                if dd <= rr and r.random() < 0.95 - dd / (rr * 2.6):
                    edge = dd > rr * 0.7
                    px[x, y] = R.shade((236, 234, 226) if edge else (250, 248, 242), 0.92 + r.random() * 0.1)
    return im


ICONS = {'tracking_lamp': icon_lamp, 'shooting_sticks': icon_sticks, 'flagging_tape': icon_tape, 'hand_warmers': icon_warmers,
         'coffee_thermos': icon_thermos, 'estrus_lure': icon_lure, 'milkweed_pods': icon_milkweed}


def main():
    materials()
    for name, fn in ICONS.items():
        if name != 'tracking_lamp':  # [smalls] the lamp's mesh, textures and icon come from tools/smalls/lamp_art.py + lamp_preview.py
            R.save_png(fn(), 'item', name + '.png')
    sticks_model()
    tape_model()
    warmers_model()
    thermos_model()
    lure_model()
    milkweed_model()
    trail_flag_block()


if __name__ == '__main__':
    main()
