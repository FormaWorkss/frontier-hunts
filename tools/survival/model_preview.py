#!/usr/bin/env python3
"""[survival] Rough isometric preview of the block models (axis-aligned elements, auto UV, painter's order).
usage: python3 tools/survival/model_preview.py <repo>  -> tools/survival/preview/blocks.png"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw

R = sys.argv[1] if len(sys.argv) > 1 else '.'
A = os.path.join(R, 'patch/assets/frontierhunts')
Z = 14


def tex(ref):
    if ref.startswith('frontierhunts:'):
        p = os.path.join(A, 'textures', ref.split(':', 1)[1] + '.png')
        if os.path.exists(p):
            return Image.open(p).convert('RGBA').resize((16, 16), Image.BOX)
    return Image.new('RGBA', (16, 16), (150, 118, 82, 255))  # vanilla stripped spruce stand-in


def iso(x, y, z, ox, oy):
    return ox + (x - z) * Z * 0.87, oy + (x + z) * Z * 0.5 - y * Z


def draw_model(canvas, models, ox, oy):
    quads = []
    for m in models:
        t = m['textures']
        for e in m['elements']:
            (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
            for face, f in e['faces'].items():
                ref = f['texture']
                while ref.startswith('#'):
                    ref = t[ref[1:]]
                if face == 'up':
                    pts = [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]; sh = 1.0; uv = (x0, z0, x1, z1)
                elif face == 'south':
                    pts = [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]; sh = 0.8; uv = (x0, 16 - y1, x1, 16 - y0)
                elif face == 'east':
                    pts = [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]; sh = 0.62; uv = (16 - z1, 16 - y1, 16 - z0, 16 - y0)
                else:
                    continue
                depth = sum(p[0] + p[2] + p[1] * 0.01 for p in pts) / 4
                quads.append((depth, pts, ref, sh, uv))
    for depth, pts, ref, sh, uv in sorted(quads, key=lambda q: q[0]):
        im = tex(ref)
        u0, v0, u1, v1 = [max(0, min(16, int(round(c)))) for c in uv]
        crop = im.crop((u0, v0, max(u0 + 1, u1), max(v0 + 1, v1))).resize((32, 32), Image.NEAREST)
        a = np.asarray(crop).astype(float); a[..., :3] *= sh
        crop = Image.fromarray(a.clip(0, 255).astype(np.uint8))
        P = [iso(*p, ox, oy) for p in pts]
        # affine map from crop square to the parallelogram P0,P1,P3
        (x0, y0), (x1, y1), _, (x3, y3) = P
        M = np.array([[x1 - x0, x3 - x0, x0], [y1 - y0, y3 - y0, y0], [0, 0, 1]], float) @ np.diag([1 / 32, 1 / 32, 1])
        inv = np.linalg.inv(M)
        layer = crop.transform(canvas.size, Image.AFFINE, data=inv[:2].flatten().tolist(), resample=Image.NEAREST)
        mask = Image.new('L', canvas.size, 0)
        ImageDraw.Draw(mask).polygon(P, fill=255)
        canvas.paste(layer, (0, 0), Image.composite(layer.split()[3], mask, mask).point(lambda v: 255 if v > 0 else 0))


def load(name):
    return json.load(open(os.path.join(A, 'models/block/survival', name + '.json')))


def shift(m, dz):
    m = json.loads(json.dumps(m))
    for e in m['elements']:
        e['from'][2] += dz; e['to'][2] += dz
    return m


c = Image.new('RGBA', (900, 420), (60, 64, 70, 255))
draw_model(c, [shift(load('hide_bedroll_head'), 0), shift(load('hide_bedroll_foot'), 16)], 220, 120)
rack = [load('drying_rack')] + [load(f'drying_rack_strip{i}_{s}') for i, s in ((1, 0), (2, 1), (3, 2), (4, 0))]
draw_model(c, rack, 620, 160)
os.makedirs(os.path.join(R, 'tools/survival/preview'), exist_ok=True)
c.save(os.path.join(R, 'tools/survival/preview/blocks.png'))
print('ok')
