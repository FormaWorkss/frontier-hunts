#!/usr/bin/env python3
"""[outfitter] Inventory icons for the 12 ghillie pieces (hood / jacket / trousers x woodland, grassland, wetland, snow):
32x32 pixel icons - garment silhouette filled with the pattern's own mesh camo, shaggy jute tufts breaking the
outline, light from the upper left, 1 px dark outline. The held / dropped 3D models stay as they were: each item model
becomes neoforge:separate_transforms (gui -> this icon, everything else -> <item>_3d, the old model copied unchanged).
usage: python3 tools/outfitter/ghillie_icons.py <repo>"""
import os, sys, json, zipfile, io, math
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
import materials as MT

R = sys.argv[1] if len(sys.argv) > 1 else '.'
A = os.path.join(R, 'patch/assets/frontierhunts')
JAR = zipfile.ZipFile('/home/claude/fh/merged62g8.jar')
PATS = ['woodland', 'grassland', 'wetland', 'snow']
JUTE = {'woodland': ['#5b4c34', '#4a4a2a', '#6a5a3c', '#3a3624', '#5d6236', '#47502c', '#71603f'],
        'grassland': ['#b8a274', '#a08a5e', '#c9b68a', '#8a7a52', '#d2c095'],
        'wetland': ['#6e6844', '#57573a', '#8a7a52', '#4a4a30', '#9a8a5e'],
        'snow': ['#e8ebed', '#d4d8dc', '#f4f5f6', '#b8bec4', '#c9ced2']}


def item_id(pat, piece):
    return 'ghillie_' + piece if pat == 'woodland' else 'ghillie_%s_%s' % (pat, piece)


def mesh(pat):
    p = 'assets/frontierhunts/textures/equipment/shelters/camouflage_v1.png' if pat == 'woodland' else 'assets/frontierhunts/textures/equipment/ghillie/%s.png' % pat
    im = Image.open(io.BytesIO(JAR.read(p))).convert('RGB').resize((40, 40), Image.LANCZOS)
    return np.asarray(im, float)


def silhouette(piece):
    """Boolean 32x32 mask of the garment (front view)."""
    m = Image.new('L', (32, 32), 0)
    d = ImageDraw.Draw(m)
    if piece == 'hood':
        d.polygon([(8, 27), (6, 15), (8, 7), (13, 3), (19, 3), (24, 7), (26, 15), (24, 27)], fill=255)  # cowl
        d.rectangle([5, 23, 27, 28], fill=255)  # shoulder cape
    elif piece == 'jacket':
        d.rectangle([10, 5, 21, 28], fill=255)           # torso
        d.polygon([(10, 5), (6, 6), (2, 12), (1, 26), (5, 27), (7, 15), (10, 11)], fill=255)    # right sleeve
        d.polygon([(21, 5), (25, 6), (29, 12), (30, 26), (26, 27), (24, 15), (21, 11)], fill=255)  # left sleeve
        d.rectangle([12, 3, 19, 6], fill=255)            # collar
    else:
        d.rectangle([9, 3, 22, 11], fill=255)            # waist / seat
        d.polygon([(9, 10), (15, 11), (14, 29), (7, 29)], fill=255)
        d.polygon([(16, 11), (22, 10), (24, 29), (17, 29)], fill=255)
    return np.asarray(m) > 127


def holes(piece):
    m = Image.new('L', (32, 32), 0)
    d = ImageDraw.Draw(m)
    if piece == 'hood':
        d.ellipse([11, 9, 21, 22], fill=255)  # face opening
    return np.asarray(m) > 127


def icon(pat, piece, seed):
    rng = np.random.default_rng(seed)
    sil = silhouette(piece)
    hole = holes(piece)
    body = sil & ~hole
    cam = mesh(pat)[4:36, 4:36]
    pal = [MT.col(c) for c in JUTE[pat]]
    rgb = np.zeros((32, 32, 3)); a = np.zeros((32, 32), bool)
    rgb[body] = cam[body]; a[body] = True
    # jute tufts: short strands hanging from rows, some breaking the silhouette edges
    for _ in range(140):
        x, y = rng.integers(0, 32), rng.integers(0, 32)
        if not body[y, x]:
            continue
        L = rng.integers(2, 5)
        c = pal[rng.integers(len(pal))] * (0.85 + 0.3 * rng.random())
        dx = rng.choice([-1, 0, 0, 1])
        for k in range(L):
            yy, xx = y + k, x + (dx if k == L - 1 else 0)
            if 0 <= yy < 32 and 0 <= xx < 32 and not hole[yy, xx] and (body[yy, xx] or (yy > 0 and body[yy - 1, xx] and k == L - 1)):
                rgb[yy, xx] = c * (1 - 0.12 * k); a[yy, xx] = True
    # light from the upper left, darker lower right
    yy, xx = np.mgrid[0:32, 0:32]
    shade = 1.12 - 0.012 * (xx + yy)
    rgb *= shade[..., None]
    if piece == 'hood':
        ring = hole & ~(np.pad(hole, 1)[2:, 1:-1] & np.pad(hole, 1)[:-2, 1:-1] & np.pad(hole, 1)[1:-1, 2:] & np.pad(hole, 1)[1:-1, :-2])
        rgb[hole] = MT.col('#1d1a14'); a[hole] = True  # dark face opening
        rgb[ring] = MT.col('#2c271c')
    out = np.zeros((32, 32, 4))
    out[a, :3] = np.clip(rgb[a], 0, 255); out[a, 3] = 255
    pad = np.pad(a, 1)
    ring = (~a) & (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:])
    out[ring] = (26, 22, 16, 255) if pat != 'snow' else (70, 76, 82, 255)
    return Image.fromarray(out.astype(np.uint8), 'RGBA')


def main():
    sheet = Image.new('RGBA', (12 * 72 + 8, 80), (139, 139, 139, 255))
    i = 0
    for pat in PATS:
        for piece in ('hood', 'jacket', 'trousers'):
            iid = item_id(pat, piece)
            im = icon(pat, piece, 100 + i)
            os.makedirs(os.path.join(A, 'textures/item/ghillie'), exist_ok=True)
            im.save(os.path.join(A, 'textures/item/ghillie/%s.png' % iid))
            # keep the old 3D model for hands / ground / frames
            old = json.loads(JAR.read('assets/frontierhunts/models/item/%s.json' % iid))
            json.dump(old, open(os.path.join(A, 'models/item/%s_3d.json' % iid), 'w'), separators=(',', ':'))
            top = {'loader': 'neoforge:separate_transforms', 'gui_light': 'front',
                   'base': {'parent': 'frontierhunts:item/%s_3d' % iid},
                   'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'frontierhunts:item/ghillie/%s' % iid}}}}
            json.dump(top, open(os.path.join(A, 'models/item/%s.json' % iid), 'w'), indent=1)
            sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (8 + i * 72, 8))
            i += 1
    os.makedirs(os.path.join(R, 'docs/ws/outfitter'), exist_ok=True)
    sheet.save(os.path.join(R, 'docs/ws/outfitter/ghillie_icons_after.png'))
    print('ghillie icons', i)


if __name__ == '__main__':
    main()
