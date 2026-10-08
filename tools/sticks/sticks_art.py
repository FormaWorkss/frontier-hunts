#!/usr/bin/env python3
"""[sticks] Art for the shooting sticks: the entity texture and mesh, the folded item model, the inventory icon, and
preview renders. Deterministic; writes into patch/ and tools/sticks/previews/.

    python3 tools/sticks/sticks_art.py [--previews]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mesh as M  # noqa: E402
import raster as R  # noqa: E402
import tex as T  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
A = os.path.join(REPO, 'patch', 'assets', 'frontierhunts')
PREV = os.path.join(HERE, 'previews')


# ============================================================================================ texture

def region_img(name, fn):
    u0, v0, u1, v1 = M.REG[name]
    return fn(u1 - u0, v1 - v0), (u0, v0)


def build_texture():
    img = np.zeros((M.TH, M.TW, 3)) + T.col(40, 40, 40)
    alpha = np.ones((M.TH, M.TW))

    def put(name, arr):
        u0, v0, u1, v1 = M.REG[name]
        img[v0:v1, u0:u1] = np.clip(arr, 0, 1)

    # upper: camo dip over aluminium, worn bright where hands and the collar rub, scratches down the leg
    w, h = 32, 112
    up = T.camo(w, h, 11)
    up = T.wear(up, 12, 14, 8, bright=T.col(176, 178, 172), chip_rad=(0.4, 1.1))
    yy = np.linspace(0, 1, h)[:, None]
    grip = np.exp(-((yy - 0.45) / 0.18) ** 2) * T.fbm(w, h, 3, 3, 13, 3)
    up = T.mix(up, T.col(170, 170, 164), np.clip((grip - 0.42) * 3, 0, 1) * 0.7)
    up = T.dirt(up, 14, 0.35)
    put('upper', up)
    # middle: black anodized, fine scratches showing silver, a little dust
    mid = T.anodized(24, 112, 21, (44, 45, 46))
    mid = T.wear(mid, 22, 16, 4, bright=T.col(150, 152, 150), chip_rad=(0.4, 1.0))
    mid = T.dirt(mid, 23, 0.45, tone=T.col(92, 78, 60))
    put('middle', mid)
    # lower: bare satin aluminium, scuffed, mud and grass stain towards the foot
    low = T.brushed_alu(20, 112, 31)
    low = T.wear(low, 32, 14, 2, bright=T.col(206, 208, 206), chip_rad=(0.4, 0.9))
    low = T.dirt(low, 33, 1.0)
    low = T.mix(low, T.col(70, 86, 46), np.clip((T.fbm(20, 112, 3, 5, 34, 3) - 0.62) * 4, 0, 1) * (yy ** 2) * 0.7)
    put('lower', low)
    # collars: ribbed black nylon; lever: smooth with a moulded grip patch
    put('collar', T.wear(T.ribbed(32, 24, 41), 42, 2, 2, bright=T.col(90, 90, 88), vertical=False, chip_rad=(0.4, 0.8)))
    lev = T.rubber(32, 16, 43, (52, 53, 54))
    lev[5:11, 6:26] *= 0.72
    lev[:, :2] *= 0.8
    put('lever', lev)
    # foot: black rubber caked with mud at the sole
    foot = T.rubber(32, 24, 51, (26, 26, 27))
    foot = T.dirt(foot, 52, 1.2)
    put('foot', foot)
    # webbing strap
    put('strap', T.webbing(64, 16, 61))
    # head castings and the yoke frame
    put('hub', T.cast(32, 32, 71, (56, 57, 59)))
    fr = T.rubber(32, 24, 81, (30, 31, 32))
    put('frame', T.wear(fr, 82, 3, 4, bright=T.col(110, 110, 108), vertical=False, chip_rad=(0.4, 0.8)))
    put('foam', T.foam(64, 64, 91))
    put('pin', T.cast(16, 16, 101, (120, 120, 118)))
    put('endcap', T.rubber(16, 16, 111, (22, 22, 22)))
    rgb = (np.clip(img, 0, 1) * 255).astype(np.uint8)
    out = np.dstack([rgb, (alpha * 255).astype(np.uint8)])
    return out


# ============================================================================================ item model (folded) and icon

ITEM_TEX = {
    'upper': ('upper', (0, 8, 4, 120)), 'middle': ('middle', (32, 8, 36, 120)), 'lower': ('lower', (56, 8, 60, 120)),
}


def item_textures(atlas):
    """16x16 item-atlas textures cut from the entity texture (the item model can't use an entity texture)."""
    d = os.path.join(A, 'textures', 'item', 'sticks')
    os.makedirs(d, exist_ok=True)
    im = Image.fromarray(atlas)

    def save(name, box, size=(16, 16)):
        im.crop(box).resize(size, Image.BOX).save(os.path.join(d, name + '.png'))

    save('upper', (0, 0, 32, 112), (16, 16))
    save('middle', (32, 0, 56, 112), (16, 16))
    save('lower', (56, 0, 76, 112), (16, 16))
    save('collar', (76, 0, 108, 24))
    save('foot', (76, 40, 108, 64))
    save('foam', (140, 0, 204, 64))
    save('frame', (108, 32, 140, 56))
    save('strap', (76, 64, 140, 80))


def el(frm, to, tex, rot=None, faces=('north', 'south', 'east', 'west', 'up', 'down'), uv=None):
    e = {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {}}
    for f in faces:
        fd = {'texture': '#' + tex}
        if uv:
            fd['uv'] = uv
        e['faces'][f] = fd
    if rot:
        e['rotation'] = rot
    return e


def item_model():
    """The folded sticks: three slim legs bundled side by side (sections retracted: camo upper, collar, anodized middle,
    collar, bare lower, rubber feet), the head, a compact padded V on top and the strap wrapped round. Two blocks long in
    model space (-8..24 px), drawn at half scale in the hand: about a metre, as the real thing folded."""
    els = []
    for (x, z) in ((7.0, 7.1), (9.0, 7.1), (8.0, 8.9)):
        els.append(el([x - 0.65, 2.0, z - 0.65], [x + 0.65, 20.4, z + 0.65], 'upper'))
        els.append(el([x - 0.85, 1.0, z - 0.85], [x + 0.85, 2.3, z + 0.85], 'collar'))
        els.append(el([x - 0.52, -4.0, z - 0.52], [x + 0.52, 1.0, z + 0.52], 'middle'))
        els.append(el([x - 0.72, -4.8, z - 0.72], [x + 0.72, -3.8, z + 0.72], 'collar'))
        els.append(el([x - 0.42, -6.8, z - 0.42], [x + 0.42, -4.8, z + 0.42], 'lower'))
        els.append(el([x - 0.62, -8.0, z - 0.62], [x + 0.62, -6.8, z + 0.62], 'foot'))
    els.append(el([6.0, 20.2, 6.1], [10.0, 21.8, 9.9], 'frame'))     # head
    els.append(el([7.4, 21.8, 7.4], [8.6, 22.6, 8.6], 'frame'))      # swivel
    els.append(el([6.8, 22.4, 7.0], [9.2, 23.0, 9.0], 'frame'))      # bridge
    els.append(el([5.9, 22.6, 7.1], [6.9, 25.6, 8.9], 'foam', rot={'angle': 22.5, 'axis': 'z', 'origin': [6.9, 22.6, 8.0]}))
    els.append(el([9.1, 22.6, 7.1], [10.1, 25.6, 8.9], 'foam', rot={'angle': -22.5, 'axis': 'z', 'origin': [9.1, 22.6, 8.0]}))
    els.append(el([5.6, 11.0, 5.7], [10.4, 12.2, 10.3], 'strap'))   # strap wrapped round the bundle
    model = {
        'credit': 'Frontier Hunts [sticks] - original model (tools/sticks/sticks_art.py)',
        'textures': {k: 'frontierhunts:item/sticks/' + k for k in ('upper', 'middle', 'lower', 'collar', 'foot', 'foam', 'frame', 'strap')},
        'display': {
            'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, -1.5, 1.0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, -1.5, 1.0], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_righthand': {'rotation': [0, -15, 12], 'translation': [1.2, -1.5, 0], 'scale': [0.42, 0.42, 0.42]},
            'firstperson_lefthand': {'rotation': [0, 15, -12], 'translation': [-1.2, -1.5, 0], 'scale': [0.42, 0.42, 0.42]},
            'ground': {'rotation': [0, 0, 90], 'translation': [0, 1.5, 0], 'scale': [0.32, 0.32, 0.32]},
            'fixed': {'rotation': [0, 0, -45], 'translation': [0, 0, 0], 'scale': [0.42, 0.42, 0.42]},
            'head': {'rotation': [0, 0, 0], 'translation': [0, 4, 0], 'scale': [0.5, 0.5, 0.5]},
        },
        'elements': els,
    }
    model['textures']['particle'] = 'frontierhunts:item/sticks/lower'
    with open(os.path.join(A, 'models', 'item', 'shooting_sticks_3d.json'), 'w') as f:
        json.dump(model, f, indent=1)


# ============================================================================================ previews

def ground(cv, size=2.2, y=0.0, tile=0.25):
    """A grassy ground plane (flat colour checker) for scale."""
    n = int(size / tile)
    for i in range(-n, n):
        for k in range(-n, n):
            c = (0.36, 0.48, 0.25) if (i + k) % 2 == 0 else (0.33, 0.45, 0.23)
            P = [(i * tile, y, k * tile), ((i + 1) * tile, y, k * tile), ((i + 1) * tile, y, (k + 1) * tile), (i * tile, y, (k + 1) * tile)]
            N = [(0, 1, 0)] * 3
            cv.tri([P[0], P[1], P[2]], None, N, None, color=c)
            cv.tri([P[0], P[2], P[3]], None, N, None, color=c)


def render_set(parts, tex, contact, cam, cv=None, **kw):
    cv = cv or R.Canvas(cam)
    cv.quads(M.assemble(parts, contact, **kw), tex)
    return cv


def label(im, text):
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, len(text) * 7 + 10, 16], fill=(20, 20, 20))
    d.text((5, 2), text, fill=(235, 235, 225))
    return im


def previews(parts, tex):
    os.makedirs(PREV, exist_ok=True)
    texf = tex.astype(np.float32) / 255.0
    # three heights, side by side
    tiles = []
    for name, c in M.HEIGHTS.items():
        cam = R.Cam((1.9, 1.15, 2.4), (0, c * 0.52, 0), fov=40, w=420, h=520)
        cv = R.Canvas(cam)
        ground(cv)
        render_set(parts, texf, c, cam, cv, yoke_yaw=0.0)
        tiles.append(label(cv.result(), name + '  contact %.2f m' % c))
    W = sum(t.width for t in tiles)
    out = Image.new('RGB', (W, tiles[0].height))
    x = 0
    for t in tiles:
        out.paste(t, (x, 0)); x += t.width
    out.save(os.path.join(PREV, 'placed_heights.png'))
    # close-up of the head, yoke and locks
    c = M.HEIGHTS['standing']
    cam = R.Cam((0.32, c + 0.12, 0.38), (0, c - 0.12, 0), fov=40, w=640, h=520)
    cv = R.Canvas(cam, bg=(0.55, 0.62, 0.66))
    render_set(parts, texf, c, cam, cv, yoke_yaw=25.0)
    label(cv.result(), 'head, yoke, strap').save(os.path.join(PREV, 'head_closeup.png'))
    cam = R.Cam((0.42, 0.62, 0.5), (0, 0.55, 0), fov=40, w=520, h=420)
    cv = R.Canvas(cam, bg=(0.55, 0.62, 0.66))
    render_set(parts, texf, c, cam, cv)
    label(cv.result(), 'leg locks').save(os.path.join(PREV, 'locks_closeup.png'))
    cam = R.Cam((0.35, 0.15, 0.45), (0, 0.06, 0), fov=40, w=520, h=360)
    cv = R.Canvas(cam)
    ground(cv)
    render_set(parts, texf, c, cam, cv)
    label(cv.result(), 'feet').save(os.path.join(PREV, 'feet_closeup.png'))
    # the texture itself, enlarged
    Image.fromarray(tex).resize((M.TW * 3, M.TH * 3), Image.NEAREST).save(os.path.join(PREV, 'texture.png'))


# ============================================================================================ item: preview + icon

def json_model_quads(model):
    """Quads of a block/item model JSON (elements with an optional single-axis rotation), in 0..1 block units."""
    out = []
    for e in model['elements']:
        f, t = [v / 16 for v in e['from']], [v / 16 for v in e['to']]
        xf = None
        if 'rotation' in e:
            r = e['rotation']
            o = [v / 16 for v in r['origin']]
            a = math.radians(r['angle'])
            rot = {'x': M.rot_x, 'y': M.rot_y, 'z': M.rot_z}[r['axis']](a)
            xf = M.chain(M.move(-o[0], -o[1], -o[2]), rot, M.move(*o))
        qs = []
        M.box(qs, 'upper', f[0], f[1], f[2], t[0], t[1], t[2], xf)
        tex = e['faces']['north']['texture'][1:]
        for q in qs:
            out.append((tex, q))
    return out


def item_preview_and_icon(tex):
    with open(os.path.join(A, 'models', 'item', 'shooting_sticks_3d.json')) as f:
        model = json.load(f)
    texs = {}
    for k in ('upper', 'middle', 'lower', 'collar', 'foot', 'foam', 'frame', 'strap'):
        texs[k] = np.asarray(Image.open(os.path.join(A, 'textures', 'item', 'sticks', k + '.png')).convert('RGBA')).astype(np.float32) / 255
    quads = json_model_quads(model)
    tiles = []
    for yaw in (30, 120):
        a = math.radians(yaw)
        cam = R.Cam((0.5 + 3.6 * math.sin(a), 1.2, 0.5 + 3.6 * math.cos(a)), (0.5, 0.55, 0.5), fov=40, w=300, h=420)
        cv = R.Canvas(cam, bg=(0.55, 0.6, 0.64))
        for name, q in quads:
            # box() mapped every face to the full 0..1 of the region 'upper': remap to the element's own 16x16 texture
            u0, v0, u1, v1 = M.REG['upper']
            qq = [(v[0], v[1], v[2], (v[3] * M.TW - u0) / (u1 - u0), (v[4] * M.TH - v0) / (v1 - v0), v[5], v[6], v[7]) for v in q]
            cv.quads([qq], texs[name])
        tiles.append(cv.result())
    out = Image.new('RGB', (sum(t.width for t in tiles), tiles[0].height))
    x = 0
    for t in tiles:
        out.paste(t, (x, 0)); x += t.width
    out.save(os.path.join(PREV, 'item_folded.png'))
    paint_icon()


def paint_icon():
    """32 px inventory icon, painted: two front legs and the back one, camo uppers, collars, dark middles, bare lowers,
    rubber feet, the black padded V on top and the olive strap loop; dark outline like the mod's other gear icons."""
    W = 32
    img = np.zeros((W, W, 4), np.float32)
    hub = np.array([16.0, 8.0])

    def px(x, y, c, a=1.0):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < W and 0 <= y < W:
            img[y, x, :3] = np.array(c[:3]) / 255.0
            img[y, x, 3] = a

    camo = [(156, 144, 118), (118, 104, 82), (104, 108, 72), (150, 140, 112), (88, 74, 58)]
    rng = np.random.default_rng(5)

    def leg(end, shade, width):
        d = end - hub
        n = int(np.linalg.norm(d) * 3)
        for i in range(n + 1):
            t = i / n
            p = hub + d * t
            perp = np.array([-d[1], d[0]]) / np.linalg.norm(d)
            if t < 0.46:
                c = camo[int(rng.integers(0, len(camo)))]
                w = width
            elif t < 0.52:
                c = (40, 40, 42)
                w = width + 1
            elif t < 0.76:
                c = (78, 80, 86) if (i % 5) else (112, 114, 118)
                w = width - 0.4
            elif t < 0.8:
                c = (34, 34, 36)
                w = width + 0.6
            elif t < 0.93:
                c = (178, 180, 176) if i % 3 else (140, 142, 140)
                w = max(1.0, width - 1.0)
            else:
                c = (28, 28, 30)
                w = width + 0.6
            c = tuple(int(v * shade) for v in c)
            for k in np.linspace(-(w - 1) / 2, (w - 1) / 2, max(1, int(round(w)))):
                q = p + perp * k
                px(q[0], q[1], c)

    hub[:] = (16.0, 8.0)
    leg(np.array([17.0, 27.5]), 0.55, 1.2)   # back leg, in shadow
    leg(np.array([4.0, 30.5]), 1.0, 1.8)
    leg(np.array([28.0, 30.5]), 0.88, 1.8)
    # head casting
    for x in range(14, 19):
        for y in range(7, 10):
            px(x, y, (92, 92, 96) if y == 7 else (56, 56, 60))
    # swivel and the V yoke: steel arms with the rubber pads on the inside
    px(16, 6, (40, 40, 42))
    for side in (-1, 1):
        for i in range(5):
            x = 16 + side * (1 + i * 0.85)
            y = 5 - i
            px(x, y, (24, 24, 26))
            px(x + side, y, (24, 24, 26))
            px(x - side * 0.9, y - 0.3, (74, 74, 76) if i != 2 else (96, 96, 98))
    px(15, 5, (24, 24, 26))
    px(17, 5, (24, 24, 26))
    # outline
    a = img[..., 3] > 0
    ring = np.zeros_like(a)
    ring[1:, :] |= a[:-1, :]
    ring[:-1, :] |= a[1:, :]
    ring[:, 1:] |= a[:, :-1]
    ring[:, :-1] |= a[:, 1:]
    ring &= ~a
    img[ring] = [22 / 255, 18 / 255, 16 / 255, 1.0]
    im = Image.fromarray((img * 255).astype(np.uint8))
    im.save(os.path.join(A, 'textures', 'item', 'shooting_sticks.png'))
    im.resize((256, 256), Image.NEAREST).save(os.path.join(PREV, 'icon.png'))


def main():
    tex = build_texture()
    os.makedirs(os.path.join(A, 'textures', 'entity'), exist_ok=True)
    Image.fromarray(tex).save(os.path.join(A, 'textures', 'entity', 'shooting_sticks.png'))
    parts = M.build()
    os.makedirs(os.path.join(A, 'models', 'entity'), exist_ok=True)
    M.write(parts, os.path.join(A, 'models', 'entity', 'shooting_sticks.fhsk'))
    item_textures(tex)
    item_model()
    if '--previews' in sys.argv:
        previews(parts, tex)
    os.makedirs(PREV, exist_ok=True)
    item_preview_and_icon(tex)
    print('ok: %d quads' % sum(len(v) for v in parts.values()))


if __name__ == '__main__':
    main()
