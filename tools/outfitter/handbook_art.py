#!/usr/bin/env python3
"""[outfitter] Frontier Handbook: a worn leather-bound field book with an embossed antler-and-compass emblem, brass
corners, a buckled strap, gold title stamping and a red ribbon. Writes (original art, procedural):
  textures/item/handbook/{cover_front,cover_back,parts}.png  - 3D book textures (8 texels per model unit)
  models/item/frontier_handbook_3d.json                       - the held / dropped / framed book (block-element model)
  models/item/frontier_handbook.json                          - separate_transforms: gui -> 32x32 icon, else the 3D book
  textures/item/frontier_handbook.png                         - 32x32 inventory icon (rendered from the 3D book)
  textures/gui/handbook/cover_plate.png                       - the Handbook screen's sidebar cover plate (title drawn by code)
usage: python3 tools/outfitter/handbook_art.py <repo>"""
import os, sys, json, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
sys.path.insert(0, os.path.dirname(__file__))
import materials as MT

R = sys.argv[1] if len(sys.argv) > 1 else '.'
A = os.path.join(R, 'patch/assets/frontierhunts')
T = 8  # texels per model unit
SERIF = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'

LEATHER = MT.col('#4f2d1c')
GOLD = MT.col('#c8a04a')
BRASS = MT.col('#b08a3e')


def leather(w, h, seed, base=LEATHER):
    rng = np.random.default_rng(seed)
    N = max(w, h)
    n = MT.fbm_t(N, 10, seed, 4)[:h, :w]
    grain = MT.fbm_t(N, 2, seed + 1, 2)[:h, :w]
    pores = rng.random((h, w))
    rgb = base[None, None, :] * (0.82 + 0.3 * n + 0.1 * grain)[..., None]
    rgb *= (1 - 0.08 * (pores > 0.93))[..., None]
    # worn, lighter edges and corners (handled leather)
    yy = np.minimum(np.arange(h), np.arange(h)[::-1])[:, None] / max(1, h)
    xx = np.minimum(np.arange(w), np.arange(w)[::-1])[None, :] / max(1, w)
    edge = np.clip(1 - np.minimum(yy, xx) * 14, 0, 1)
    wear = MT.fbm_t(N, 6, seed + 2, 3)[:h, :w]
    rgb = rgb * (1 - 0.35 * edge[..., None] * wear[..., None]) + MT.col('#8a5a3a') * 0.35 * edge[..., None] * wear[..., None]
    # scuffs
    for _ in range(int(w * h / 900) + 2):
        x, y = rng.random() * w, rng.random() * h
        L = 3 + rng.random() * 8; a = rng.random() * math.pi
        for k in range(int(L)):
            xi, yi = int(x + math.cos(a) * k), int(y + math.sin(a) * k)
            if 0 <= xi < w and 0 <= yi < h:
                rgb[yi, xi] = rgb[yi, xi] * 0.7 + MT.col('#9a6a48') * 0.3
    return np.clip(rgb, 0, 255)


def emblem(img, cx, cy, r, color, shadow=(0, 0, 0, 0), width=None):
    """Compass rose in a ring with a pair of antlers rising from it (stamped emblem)."""
    d = ImageDraw.Draw(img)
    width = width or max(1, int(r * 0.12))
    def draw(ox, oy, c):
        d.ellipse([cx - r + ox, cy - r + oy, cx + r + ox, cy + r + oy], outline=c, width=width)
        # 8-point star
        for k in range(8):
            a = k * math.pi / 4 - math.pi / 2
            L = r * (0.86 if k % 2 == 0 else 0.5)
            wd = r * (0.2 if k % 2 == 0 else 0.13)
            tip = (cx + ox + math.cos(a) * L, cy + oy + math.sin(a) * L)
            l = (cx + ox + math.cos(a + math.pi / 2) * wd, cy + oy + math.sin(a + math.pi / 2) * wd)
            rr = (cx + ox + math.cos(a - math.pi / 2) * wd, cy + oy + math.sin(a - math.pi / 2) * wd)
            d.polygon([tip, l, (cx + ox, cy + oy), rr], fill=c)
        # antlers: two main beams sweeping up and out from the ring top, three tines each
        for s in (-1, 1):
            pts = []
            for i in range(14):
                t = i / 13
                x = cx + ox + s * (r * 0.25 + r * 0.95 * math.sin(t * 1.25))
                y = cy + oy - r * 0.85 - r * 1.25 * t + r * 0.25 * t * t
                pts.append((x, y))
            d.line(pts, fill=c, width=max(1, int(width * 1.2)), joint='curve')
            for ti, (t0, ln) in enumerate(((0.28, 0.5), (0.55, 0.55), (0.82, 0.42))):
                i0 = int(t0 * 13)
                x0, y0 = pts[i0]
                d.line([(x0, y0), (x0 - s * r * 0.08, y0 - r * ln)], fill=c, width=max(1, width))
    if shadow[3]:
        draw(max(1, r * 0.06), max(1, r * 0.06), shadow)
    draw(0, 0, color)


def stamp_text(img, text, cx, y, size, color, shadow=(30, 16, 8, 200), spacing=0):
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(SERIF, size)
    w = d.textlength(text, font=f) + spacing * (len(text) - 1)
    x = cx - w / 2
    for ch in text:
        d.text((x + 1, y + 1), ch, font=f, fill=shadow)
        d.text((x, y), ch, font=f, fill=color)
        x += d.textlength(ch, font=f) + spacing


def gold_tool(img, rect, color, w=1):
    d = ImageDraw.Draw(img)
    d.rectangle(rect, outline=color, width=w)


def to_img(rgb):
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB').convert('RGBA')


def cover_front():
    W, H = 10 * T, 14 * T
    S2 = 4  # supersample for the stamping
    base = to_img(leather(W * S2, H * S2, 11)).resize((W * S2, H * S2))
    g = tuple(int(c) for c in GOLD) + (255,)
    gd = tuple(int(c) for c in GOLD * 0.62) + (255,)
    blind = (40, 22, 14, 255)
    # blind-tooled outer frame + gold inner frame
    gold_tool(base, [3 * S2, 3 * S2, W * S2 - 4 * S2, H * S2 - 4 * S2], blind, S2)
    gold_tool(base, [6 * S2, 6 * S2, W * S2 - 7 * S2, H * S2 - 7 * S2], g, S2)
    gold_tool(base, [8 * S2, 8 * S2, W * S2 - 9 * S2, H * S2 - 9 * S2], gd, max(1, S2 // 2))
    stamp_text(base, 'FRONTIER', W * S2 / 2, 14 * S2, 8 * S2, g, spacing=S2)
    stamp_text(base, 'HANDBOOK', W * S2 / 2, 24 * S2, 7 * S2, g, spacing=S2)
    # embossed emblem: darker pressed field + gold lines
    emblem(base, W * S2 / 2, H * S2 * 0.62, 15 * S2, g, shadow=(26, 14, 8, 220), width=int(1.6 * S2))
    stamp_text(base, 'EST. IN THE FIELD', W * S2 / 2, H * S2 - 17 * S2, 4 * S2, gd, spacing=S2 // 2)
    return base.resize((W, H), Image.LANCZOS)


def cover_back():
    W, H = 10 * T, 14 * T
    S2 = 4
    base = to_img(leather(W * S2, H * S2, 12)).resize((W * S2, H * S2))
    blind = (40, 22, 14, 255)
    gold_tool(base, [3 * S2, 3 * S2, W * S2 - 4 * S2, H * S2 - 4 * S2], blind, S2)
    emblem(base, W * S2 / 2, H * S2 / 2, 9 * S2, (44, 25, 16, 255), width=S2)
    return base.resize((W, H), Image.LANCZOS)


def parts():
    """128x128 atlas (8 texels per unit): spine 0..4 x 0..14 units, pages, brass, strap, ribbon."""
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    # spine with raised bands and gold lines
    sp = to_img(leather(4 * T, 14 * T, 13, MT.col('#45271a')))
    d = ImageDraw.Draw(sp)
    for yb in (2.2, 5.2, 8.8, 11.8):
        y = int(yb * T)
        d.rectangle([0, y, 4 * T, y + 4], fill=(36, 20, 12, 255))
        d.line([0, y - 1, 4 * T, y - 1], fill=(120, 80, 50, 255))
        d.line([0, y + 6, 4 * T, y + 6], fill=tuple(int(c) for c in GOLD) + (255,))
    img.alpha_composite(sp, (0, 0))
    # page edges (fore-edge / top / bottom): cream with fine leaf lines and a little grime
    pw, ph = 4 * T, 14 * T
    rng = np.random.default_rng(3)
    pg = np.zeros((ph, pw, 3)); pg[:] = MT.col('#e8dcc0')
    lines = (np.arange(pw) % 2 == 0)[None, :]
    pg *= (1 - 0.1 * lines)[..., None]
    pg *= (0.9 + 0.1 * rng.random((ph, pw)))[..., None]
    grime = MT.fbm_t(max(ph, pw), 8, 4, 3)[:ph, :pw]
    pg = pg * (1 - 0.25 * grime[..., None]) + MT.col('#8a7458') * 0.25 * grime[..., None]
    img.alpha_composite(to_img(pg), (4 * T, 0))
    pgt = np.zeros((pw, 12 * T, 3)); pgt[:] = MT.col('#e4d6b8')
    pgt *= (1 - 0.1 * (np.arange(pw) % 2 == 0)[:, None])[..., None]
    img.alpha_composite(to_img(pgt).rotate(0), (0, 14 * T))  # 12x4 units at (0,14)  (top / bottom page edges)
    # brass: hammered brass with highlights (8,0)..(10,2)
    br = np.zeros((2 * T, 2 * T, 3)); br[:] = BRASS
    n = MT.fbm_t(2 * T, 3, 5, 3)
    br *= (0.75 + 0.5 * n)[..., None]
    br[:2] = np.minimum(255, br[:2] * 1.3); br[:, :2] = np.minimum(255, br[:, :2] * 1.2)
    img.alpha_composite(to_img(br), (8 * T, 0))
    # strap: darker oiled leather with edge creases (8,2)..(16,4)
    stp = leather(8 * T, 2 * T, 21, MT.col('#3a2014'))
    stp[1:3] *= 0.7; stp[-3:-1] *= 0.7
    img.alpha_composite(to_img(stp), (8 * T, 2 * T))
    # ribbon (14,0)..(15,2)
    rb = np.zeros((2 * T, T, 3)); rb[:] = MT.col('#9b2a24'); rb *= (0.8 + 0.2 * (np.arange(T) % 3 == 0))[None, :, None]
    img.alpha_composite(to_img(rb), (14 * T, 0))
    return img


def box(fr, to, faces, rot=None):
    e = {'from': fr, 'to': to, 'faces': faces}
    if rot:
        e['rotation'] = rot
    return e


def f(tex, uv, rotation=0):
    d = {'texture': tex, 'uv': uv}
    if rotation:
        d['rotation'] = rotation
    return d


def model3d():
    # parts atlas UVs (0..16 = 128 texels, 8 texels/unit -> 1 unit = 1 uv)
    SP = [0, 0, 4, 14]
    PG_SIDE = [4, 0, 8, 14]
    PG_TOP = [0, 14, 12, 16]
    BR = [8, 0, 10, 2]
    ST = [8, 2, 16, 4]
    RB = [14, 0, 15, 2]
    L = '#leather'
    els = []
    # boards: back z 6.6..7.1, front z 10.4..10.9 (front faces south = +z)
    edge_uv = [0.2, 0.2, 0.6, 13.8]
    els.append(box([3, 1, 6.6], [13, 15, 7.1], {
        'north': f('#back', [0, 0, 16, 16]), 'south': f('#back', [0, 0, 16, 16]),
        'east': f('#parts', [3.5, 0, 4, 14]), 'west': f('#parts', [3.5, 0, 4, 14]),
        'up': f('#parts', [0, 0, 4, 0.5]), 'down': f('#parts', [0, 0, 4, 0.5])}))
    els.append(box([3, 1, 10.4], [13, 15, 10.9], {
        'south': f('#front', [0, 0, 16, 16]), 'north': f('#back', [0, 0, 16, 16]),
        'east': f('#parts', [3.5, 0, 4, 14]), 'west': f('#parts', [3.5, 0, 4, 14]),
        'up': f('#parts', [0, 0, 4, 0.5]), 'down': f('#parts', [0, 0, 4, 0.5])}))
    # page block
    els.append(box([3.3, 1.35, 7.1], [12.7, 14.65, 10.4], {
        'east': f('#parts', PG_SIDE), 'up': f('#parts', [0, 14, 12, 15.2]), 'down': f('#parts', [0, 14, 12, 15.2]),
        'west': f('#parts', SP), 'north': f('#parts', PG_SIDE), 'south': f('#parts', PG_SIDE)}))
    # spine (rounded look: two boxes)
    els.append(box([2.5, 1, 6.9], [3.3, 15, 10.6], {
        'west': f('#parts', SP), 'up': f('#parts', [0, 0, 4, 0.5]), 'down': f('#parts', [0, 0, 4, 0.5]),
        'north': f('#parts', [0, 0, 1, 14]), 'south': f('#parts', [0, 0, 1, 14]), 'east': f('#parts', SP)}))
    els.append(box([2.2, 1.3, 7.4], [2.5, 14.7, 10.1], {
        'west': f('#parts', SP), 'north': f('#parts', [0, 0, 1, 14]), 'south': f('#parts', [0, 0, 1, 14]),
        'up': f('#parts', [0, 0, 4, 0.5]), 'down': f('#parts', [0, 0, 4, 0.5])}))
    # brass corners (fore-edge corners, both boards) - wrap the corner: a cap on the face + the edge
    for (z0, z1) in ((10.85, 11.05), (6.45, 6.65)):
        for (y0, y1) in ((1, 2.3), (13.7, 15)):
            els.append(box([11.7, y0 - 0.05, z0], [13.1, y1 + 0.05, z1], {k: f('#parts', BR) for k in ('north', 'south', 'east', 'west', 'up', 'down')}))
        for (y0, y1) in ((1, 2.0), (14.0, 15)):
            els.append(box([2.4, y0 - 0.05, z0], [3.5, y1 + 0.05, z1], {k: f('#parts', BR) for k in ('north', 'south', 'east', 'west', 'up', 'down')}))
    # strap: from the back board, round the fore-edge, across the front to a brass stud
    sy0, sy1 = 7.1, 8.9
    els.append(box([8.5, sy0, 6.35], [13.25, sy1, 6.6], {k: f('#parts', ST) for k in ('north', 'south', 'east', 'west', 'up', 'down')}))
    els.append(box([13.0, sy0, 6.35], [13.3, sy1, 11.1], {k: f('#parts', ST) for k in ('north', 'south', 'east', 'west', 'up', 'down')}))
    els.append(box([9.6, sy0, 10.9], [13.3, sy1, 11.12], {k: f('#parts', ST) for k in ('north', 'south', 'east', 'west', 'up', 'down')}))
    els.append(box([9.0, 7.35, 11.0], [10.0, 8.65, 11.35], {k: f('#parts', BR) for k in ('north', 'south', 'east', 'west', 'up', 'down')}))
    # ribbon marker
    els.append(box([7.0, -0.3, 8.6], [7.6, 1.35, 8.65], {'north': f('#parts', RB), 'south': f('#parts', RB)}))
    return els


GENERATED_DISPLAY = {
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'fixed': {'rotation': [0, 180, 0], 'scale': [1, 1, 1]},
    'gui': {'rotation': [18, -30, 0], 'translation': [0, 0, 0], 'scale': [0.95, 0.95, 0.95]},
}


def icon_from_model(els, front, back, parts):
    """Render the 3D book for the 32x32 icon (angled so cover, spine and page edges read), plus an outline."""
    import gui_audit as GA
    tex = {'#front': np.asarray(front, np.uint8), '#back': np.asarray(back, np.uint8), '#parts': np.asarray(parts, np.uint8)}
    quads = []
    for e in els:
        for P, UV, tk, tint in GA.element_quads(e):
            quads.append((P, UV, tex[tk]))
    M = GA.gui_matrix({'rotation': [14, -28, -4], 'translation': [0.3, 0, 0], 'scale': [1.05, 1.05, 1.05]})
    big = GA.raster(quads, M, 128, 'side')
    im = big.resize((32, 32), Image.LANCZOS)
    a = np.asarray(im).astype(float)
    alpha = (a[..., 3] > 100)
    rgb = a[..., :3]
    # crisp alpha + 1px dark outline like the mod's other icons
    out = np.zeros((32, 32, 4))
    out[alpha, :3] = rgb[alpha] * 1.05
    out[alpha, 3] = 255
    pad = np.pad(alpha, 1)
    ring = (~alpha) & (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:])
    out[ring] = (34, 20, 12, 255)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA'), big


def cover_plate():
    """Sidebar plate for the Handbook screen: 2x of a 124x30 GUI area -> 248x60, tooled leather, brass corners, emblem."""
    W, H = 248, 60
    S2 = 2
    base = to_img(leather(W * S2, H * S2, 31))
    d = ImageDraw.Draw(base)
    g = tuple(int(c) for c in GOLD) + (255,)
    gd = tuple(int(c) for c in GOLD * 0.6) + (255,)
    d.rectangle([5 * S2, 5 * S2, W * S2 - 6 * S2, H * S2 - 6 * S2], outline=(36, 20, 12, 255), width=2 * S2)
    d.rectangle([9 * S2, 9 * S2, W * S2 - 10 * S2, H * S2 - 10 * S2], outline=g, width=S2)
    # emblem disc on the left, embossed
    cx, cy, r = 32 * S2, H * S2 / 2 + 3 * S2, 13 * S2
    d.ellipse([cx - r - 6, cy - r - 6, cx + r + 6, cy + r + 6], fill=(58, 34, 22, 255))
    emblem(base, cx, cy, r, g, shadow=(24, 13, 8, 230), width=2 * S2)
    # brass corner caps
    br = tuple(int(c) for c in BRASS) + (255,)
    for (x, y, sx, sy) in ((0, 0, 1, 1), (W * S2, 0, -1, 1), (0, H * S2, 1, -1), (W * S2, H * S2, -1, -1)):
        pts = [(x, y), (x + sx * 16 * S2, y), (x, y + sy * 16 * S2)]
        d.polygon(pts, fill=br)
        d.line([(x + sx * 16 * S2, y), (x, y + sy * 16 * S2)], fill=(90, 66, 30, 255), width=S2)
        d.ellipse([x + sx * 5 * S2 - 3, y + sy * 5 * S2 - 3, x + sx * 5 * S2 + 3, y + sy * 5 * S2 + 3], fill=(230, 200, 120, 255))
    # strap across the right end with a buckle
    sx0 = W * S2 - 38 * S2
    d.rectangle([sx0, 0, sx0 + 14 * S2, H * S2], fill=(44, 24, 15, 255))
    d.line([sx0 + 2, 0, sx0 + 2, H * S2], fill=(80, 50, 32, 255), width=S2)
    d.line([sx0 + 14 * S2 - 3, 0, sx0 + 14 * S2 - 3, H * S2], fill=(80, 50, 32, 255), width=S2)
    d.rectangle([sx0 - 3 * S2, H * S2 / 2 - 9 * S2, sx0 + 17 * S2, H * S2 / 2 + 9 * S2], outline=br, width=3 * S2)
    d.line([sx0 + 7 * S2, H * S2 / 2 - 9 * S2, sx0 + 7 * S2, H * S2 / 2 + 9 * S2], fill=(220, 190, 110, 255), width=2 * S2)
    return base.resize((W, H), Image.LANCZOS)


def main():
    os.makedirs(os.path.join(A, 'textures/item/handbook'), exist_ok=True)
    os.makedirs(os.path.join(A, 'textures/gui/handbook'), exist_ok=True)
    front, back, pts = cover_front(), cover_back(), parts()
    front.save(os.path.join(A, 'textures/item/handbook/cover_front.png'))
    back.save(os.path.join(A, 'textures/item/handbook/cover_back.png'))
    pts.save(os.path.join(A, 'textures/item/handbook/parts.png'))
    els = model3d()
    m3 = {
        'credit': 'Frontier Hunts [outfitter] - original leather field book',
        'texture_size': [16, 16],
        'textures': {'front': 'frontierhunts:item/handbook/cover_front', 'back': 'frontierhunts:item/handbook/cover_back',
                     'parts': 'frontierhunts:item/handbook/parts', 'particle': 'frontierhunts:item/handbook/cover_front'},
        'elements': els,
        'display': GENERATED_DISPLAY,
    }
    json.dump(m3, open(os.path.join(A, 'models/item/frontier_handbook_3d.json'), 'w'), indent=1)
    top = {
        'loader': 'neoforge:separate_transforms',
        'gui_light': 'front',
        'base': {'parent': 'frontierhunts:item/frontier_handbook_3d'},
        'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'frontierhunts:item/frontier_handbook'}}},
    }
    json.dump(top, open(os.path.join(A, 'models/item/frontier_handbook.json'), 'w'), indent=1)
    _, big = icon_from_model(els, front, back, pts)
    icon = icon_pixel()
    icon.save(os.path.join(A, 'textures/item/frontier_handbook.png'))
    cover_plate().save(os.path.join(A, 'textures/gui/handbook/cover_plate.png'))
    prev = os.path.join(R, 'docs/ws/outfitter')
    os.makedirs(prev, exist_ok=True)
    sheet = Image.new('RGBA', (128 * 3 + 80 * 2 + 248 + 70, 260), (198, 198, 198, 255))
    sheet.alpha_composite(front.resize((160, 224), Image.NEAREST), (10, 10))
    sheet.alpha_composite(big, (180, 20))
    sheet.alpha_composite(icon.resize((128, 128), Image.NEAREST), (320, 20))
    sheet.alpha_composite(icon, (460, 20))
    sheet.alpha_composite(cover_plate(), (500, 150))
    sheet.save(os.path.join(prev, 'handbook_after.png'))
    print('handbook ok')



def icon_pixel():
    """Hand-laid 32x32 pixel icon: the book seen front-on with a hint of depth (spine left, page block right/bottom)."""
    K = 4
    img = Image.new('RGBA', (32 * K, 32 * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    def px(x0, y0, x1, y1, c):  # inclusive pixel rect on the 32 grid
        d.rectangle([x0 * K, y0 * K, (x1 + 1) * K - 1, (y1 + 1) * K - 1], fill=c)
    OUT = (30, 17, 10, 255)
    # page block (behind the front board): right edge + bottom edge
    px(7, 4, 27, 29, OUT)
    px(8, 5, 26, 28, (226, 214, 186, 255))
    for x in range(9, 27, 2):
        px(x, 27, x, 28, (196, 182, 152, 255))
    for y in range(6, 28, 2):
        px(26, y, 26, y, (196, 182, 152, 255))
    # back board peeking at bottom-right
    px(9, 29, 27, 29, (52, 28, 17, 255))
    # front board
    px(4, 2, 25, 27, OUT)
    lea = leather(21 * K, 25 * K, 5, MT.col('#5c3321'))
    shade = np.linspace(1.12, 0.82, 25 * K)[:, None, None] * np.linspace(1.08, 0.9, 21 * K)[None, :, None]
    img.alpha_composite(to_img(lea * shade), (5 * K, 3 * K))
    # spine edge (left)
    px(4, 2, 5, 27, (40, 22, 13, 255))
    for y in (6, 13, 20):
        px(5, y, 5, y, (200, 160, 74, 255))
    gold = (214, 172, 82, 255)
    gold_d = (150, 112, 50, 255)
    # gold frame
    d.rectangle([7 * K, 5 * K, 23 * K + K - 1, 25 * K + K - 1], outline=gold_d, width=K // 2)
    # title lines (stamped lettering suggested)
    for x in range(9, 22):
        if x % 3 != 2:
            px(x, 6, x, 6, gold)
    for x in range(10, 21):
        if x % 3 != 1:
            px(x, 8, x, 8, gold_d)
    # emblem: compass ring + star, antlers above (pixel-placed so it reads at 32 px)
    cx, cy = 15, 19
    for (x, y) in ((cx - 1, cy - 3), (cx, cy - 3), (cx + 1, cy - 3), (cx - 2, cy - 2), (cx + 2, cy - 2), (cx - 3, cy - 1), (cx + 3, cy - 1),
                   (cx - 3, cy), (cx + 3, cy), (cx - 3, cy + 1), (cx + 3, cy + 1), (cx - 2, cy + 2), (cx + 2, cy + 2), (cx - 1, cy + 3), (cx, cy + 3), (cx + 1, cy + 3)):
        px(x, y, x, y, gold)
    for (x, y) in ((cx, cy - 2), (cx, cy - 1), (cx, cy), (cx, cy + 1), (cx, cy + 2), (cx - 1, cy), (cx + 1, cy), (cx - 2, cy), (cx + 2, cy)):
        px(x, y, x, y, (238, 204, 120, 255))
    for s_ in (-1, 1):
        for (dx, dy) in ((1, -4), (2, -5), (3, -6), (3, -7), (4, -8), (2, -7), (4, -6), (5, -7)):
            px(cx + s_ * dx, cy + dy, cx + s_ * dx, cy + dy, gold)
    # strap with brass buckle
    px(17, 15, 26, 18, (40, 22, 14, 255))
    px(17, 15, 26, 15, (70, 42, 26, 255))
    px(17, 16, 19, 17, (196, 156, 70, 255))
    px(18, 16, 18, 17, (60, 36, 20, 255))
    # brass corners (fore-edge side)
    for (x, y) in ((23, 2), (23, 25)):
        px(x, y, 25, y + 2, (186, 146, 62, 255))
    px(25, 2, 25, 2, (240, 210, 140, 255)); px(25, 27, 25, 27, (120, 90, 40, 255))
    # ribbon
    px(12, 28, 12, 30, (155, 40, 34, 255)); px(12, 31, 12, 31, (30, 17, 10, 255))
    out = img.resize((32, 32), Image.BOX)
    a = np.asarray(out).copy()
    a[..., 3] = np.where(a[..., 3] > 110, 255, 0)
    return Image.fromarray(a, 'RGBA')


if __name__ == '__main__':
    main()
