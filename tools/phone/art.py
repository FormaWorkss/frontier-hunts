#!/usr/bin/env python3
"""[phone] Procedural art for the Field Phone UI: app icons, glyphs, weather icons.

python3 tools/phone/art.py <repo root>

Writes patch/assets/frontierhunts/textures/gui/phone/{apps,glyphs,weather}.png. Everything is drawn here at 4x and
downsampled (Lanczos), so edges are smooth at every GUI scale. The glyph order is read from the Java enum G.java.
"""
import math
import os
import re
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
OUT = os.path.join(R, 'patch/assets/frontierhunts/textures/gui/phone')
os.makedirs(OUT, exist_ok=True)
SS = 4


def rgba(c, a=255):
    if isinstance(c, str):
        c = c.lstrip('#')
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)
    return c


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def vgrad(size, top, bottom):
    w, h = size
    img = Image.new('RGBA', size)
    d = ImageDraw.Draw(img)
    top, bottom = rgba(top), rgba(bottom)
    for y in range(h):
        d.line([(0, y), (w, y)], fill=lerp(top, bottom, y / max(1, h - 1)))
    return img


def radial(size, inner, outer, cx=0.5, cy=0.5, rad=0.7):
    w, h = size
    img = Image.new('RGBA', size)
    px = img.load()
    inner, outer = rgba(inner), rgba(outer)
    for y in range(h):
        for x in range(w):
            d = math.hypot((x / w - cx), (y / h - cy)) / rad
            px[x, y] = lerp(inner, outer, min(1.0, d))
    return img


def squircle_mask(n, k=5.0, inset=0):
    m = Image.new('L', (n, n), 0)
    px = m.load()
    r = n / 2 - inset
    c = n / 2
    for y in range(n):
        for x in range(n):
            dx = abs(x + 0.5 - c) / r
            dy = abs(y + 0.5 - c) / r
            if dx ** k + dy ** k <= 1.0:
                px[x, y] = 255
    return m


def paste_masked(base, layer, mask):
    base.paste(layer, (0, 0), ImageChops.multiply(mask, layer.split()[3]) if layer.mode == 'RGBA' else mask)


def shadow(layer, radius, offset=(0, 0), alpha=120):
    a = layer.split()[3].point(lambda v: v * alpha // 255)
    s = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    s.putalpha(a)
    s = s.filter(ImageFilter.GaussianBlur(radius))
    out = Image.new('RGBA', layer.size, (0, 0, 0, 0))
    out.paste(s, offset, s)
    return out


def over(base, layer):
    base.alpha_composite(layer)
    return base


def down(img, size):
    return img.resize((size, size), Image.LANCZOS)


# ============================================================================================ app icons
N = 192 * SS


def icon_base(top, bottom, gloss=True):
    bg = vgrad((N, N), top, bottom)
    if gloss:
        g = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        d = ImageDraw.Draw(g)
        d.ellipse([-N * 0.3, -N * 0.9, N * 1.3, N * 0.45], fill=(255, 255, 255, 18))
        bg.alpha_composite(g)
    return bg


def layer():
    return Image.new('RGBA', (N, N), (0, 0, 0, 0))


def finish(bg, art_layers):
    for l in art_layers:
        bg.alpha_composite(shadow(l, N * 0.02, (0, int(N * 0.015)), 110))
        bg.alpha_composite(l)
    mask = squircle_mask(N, 5.0)
    out = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    out.paste(bg, (0, 0), mask)
    # inner rim light
    rim = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    inner = squircle_mask(N, 5.0, inset=N * 0.012)
    edge = ImageChops.subtract(mask, inner)
    rl = Image.new('RGBA', (N, N), (255, 255, 255, 40))
    rim.paste(rl, (0, 0), edge)
    out.alpha_composite(rim)
    return out


def P(x, y):
    return (x * N, y * N)


def icon_clock():
    bg = icon_base('#2b302c', '#121513')
    l = layer()
    d = ImageDraw.Draw(l)
    c, r = N / 2, N * 0.34
    d.ellipse([c - r, c - r, c + r, c + r], fill=rgba('#f4f1e8'))
    for i in range(12):
        a = i / 12 * math.tau
        r0 = r * (0.78 if i % 3 else 0.70)
        w = N * (0.018 if i % 3 else 0.03)
        d.line([(c + math.sin(a) * r0, c - math.cos(a) * r0), (c + math.sin(a) * r * 0.9, c - math.cos(a) * r * 0.9)], fill=rgba('#2a2e2b'), width=int(w))
    def hand(a, ln, w, col):
        x, y = c + math.sin(a) * ln, c - math.cos(a) * ln
        d.line([(c, c), (x, y)], fill=col, width=int(w))
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=col)
    hand(math.tau * (10 / 12 + 8 / 720), r * 0.5, N * 0.045, rgba('#1d211e'))
    hand(math.tau * (8 / 60), r * 0.72, N * 0.03, rgba('#1d211e'))
    hand(math.tau * (38 / 60), r * 0.82, N * 0.012, rgba('#ff7a1f'))
    d.ellipse([c - N * 0.03, c - N * 0.03, c + N * 0.03, c + N * 0.03], fill=rgba('#ff7a1f'))
    return finish(bg, [l])


def sun(d, cx, cy, r, rays=True):
    if rays:
        for i in range(10):
            a = i / 10 * math.tau
            d.line([(cx + math.sin(a) * r * 1.3, cy - math.cos(a) * r * 1.3), (cx + math.sin(a) * r * 1.75, cy - math.cos(a) * r * 1.75)],
                   fill=rgba('#ffc04a'), width=int(r * 0.22))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba('#ffb02e'))
    d.ellipse([cx - r * 0.82, cy - r * 0.86, cx + r * 0.78, cy + r * 0.74], fill=rgba('#ffc84e'))


def cloud(d, cx, cy, s, col='#ffffff', shade='#d6e0ea'):
    parts = [(-0.55, 0.12, 0.42), (-0.12, -0.18, 0.55), (0.42, 0.02, 0.45), (0.0, 0.22, 0.5)]
    for dx, dy, rr in parts:
        d.ellipse([cx + (dx - rr) * s, cy + (dy - rr) * s + s * 0.08, cx + (dx + rr) * s, cy + (dy + rr) * s + s * 0.08], fill=rgba(shade))
    for dx, dy, rr in parts:
        d.ellipse([cx + (dx - rr) * s, cy + (dy - rr) * s, cx + (dx + rr) * s, cy + (dy + rr) * s], fill=rgba(col))
    d.rounded_rectangle([cx - 0.95 * s, cy + 0.05 * s, cx + 0.85 * s, cy + 0.66 * s], radius=0.3 * s, fill=rgba(col))


def icon_weather():
    bg = icon_base('#4f9be0', '#2a5fa8')
    l1 = layer()
    sun(ImageDraw.Draw(l1), N * 0.62, N * 0.38, N * 0.16)
    l2 = layer()
    cloud(ImageDraw.Draw(l2), N * 0.44, N * 0.56, N * 0.27)
    return finish(bg, [l1, l2])


def camo(size, cols, seed):
    import random
    rnd = random.Random(seed)
    img = Image.new('RGBA', size, rgba(cols[0]))
    d = ImageDraw.Draw(img)
    w, h = size
    for c in cols[1:]:
        for _ in range(14):
            x, y = rnd.uniform(0, w), rnd.uniform(0, h)
            pts = []
            for k in range(9):
                a = k / 9 * math.tau
                rr = rnd.uniform(0.06, 0.16) * w
                pts.append((x + math.cos(a) * rr * rnd.uniform(0.6, 1.3), y + math.sin(a) * rr * 0.7))
            d.polygon(pts, fill=rgba(c))
    return img.filter(ImageFilter.GaussianBlur(w * 0.004))


def icon_cams():
    bg = icon_base('#3d4a33', '#1f271b')
    # tree bark strip behind
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    d0.rectangle([N * 0.38, 0, N * 0.62, N], fill=rgba('#3a2e22'))
    for i in range(9):
        x = N * (0.39 + i * 0.026)
        d0.line([(x, 0), (x + N * 0.01, N)], fill=rgba('#2c231a'), width=int(N * 0.008))
    # strap
    d0.rectangle([N * 0.30, N * 0.42, N * 0.70, N * 0.47], fill=rgba('#1a1a17'))
    # camera body (camo)
    body = camo((N, N), ['#5b5a3a', '#3e4a2c', '#7a6a45', '#2a2f22'], 7)
    m = Image.new('L', (N, N), 0)
    ImageDraw.Draw(m).rounded_rectangle([N * 0.25, N * 0.24, N * 0.75, N * 0.80], radius=N * 0.09, fill=255)
    l1 = layer()
    l1.paste(body, (0, 0), m)
    d1 = ImageDraw.Draw(l1)
    d1.rounded_rectangle([N * 0.25, N * 0.24, N * 0.75, N * 0.80], radius=N * 0.09, outline=rgba('#1e221a'), width=int(N * 0.012))
    # lens
    cx, cy = N * 0.5, N * 0.58
    d1.ellipse([cx - N * 0.11, cy - N * 0.11, cx + N * 0.11, cy + N * 0.11], fill=rgba('#151816'))
    d1.ellipse([cx - N * 0.075, cy - N * 0.075, cx + N * 0.075, cy + N * 0.075], fill=rgba('#20303f'))
    d1.ellipse([cx - N * 0.04, cy - N * 0.05, cx + 0.0, cy - N * 0.01], fill=rgba('#7fb0d8', 200))
    # IR flash panel
    d1.rounded_rectangle([N * 0.33, N * 0.31, N * 0.67, N * 0.43], radius=N * 0.03, fill=rgba('#121412'))
    for i in range(6):
        for j in range(2):
            x = N * (0.36 + i * 0.054)
            y = N * (0.335 + j * 0.05)
            d1.ellipse([x, y, x + N * 0.03, y + N * 0.03], fill=rgba('#3b1d1d'))
    # PIR dot
    d1.ellipse([N * 0.47, N * 0.70, N * 0.53, N * 0.76], fill=rgba('#e8e4d8'))
    return finish(bg, [l0, l1])


def icon_maps():
    bg = icon_base('#d9d3b8', '#b9b28f', gloss=False)
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    # topo contours
    for k in range(9):
        r = N * (0.08 + k * 0.075)
        pts = []
        for i in range(60):
            a = i / 60 * math.tau
            wob = 1 + 0.12 * math.sin(a * 3 + k * 0.6) + 0.06 * math.sin(a * 5 - k)
            pts.append((N * 0.42 + math.cos(a) * r * wob * 1.15, N * 0.55 + math.sin(a) * r * wob * 0.85))
        d0.line(pts + [pts[0]], fill=rgba('#8a7a4a', 160 if k % 4 else 230), width=int(N * (0.006 if k % 4 else 0.011)))
    # river
    d0.line([(N * 0.0, N * 0.25), (N * 0.25, N * 0.3), (N * 0.45, N * 0.18), (N * 0.7, N * 0.25), (N, N * 0.12)], fill=rgba('#5d9fd0'), width=int(N * 0.035), joint='curve')
    # trail
    for i in range(10):
        t = i / 10
        x = N * (0.15 + t * 0.6)
        y = N * (0.85 - t * 0.45 + 0.05 * math.sin(t * 9))
        d0.ellipse([x - N * 0.012, y - N * 0.012, x + N * 0.012, y + N * 0.012], fill=rgba('#b2462e'))
    l1 = layer()
    d1 = ImageDraw.Draw(l1)
    cx, cy, r = N * 0.66, N * 0.40, N * 0.12
    d1.polygon([(cx - r * 0.82, cy + r * 0.55), (cx + r * 0.82, cy + r * 0.55), (cx, cy + r * 2.2)], fill=rgba('#e0452f'))
    d1.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba('#e0452f'))
    d1.ellipse([cx - r * 0.42, cy - r * 0.42, cx + r * 0.42, cy + r * 0.42], fill=rgba('#ffffff'))
    return finish(bg, [l0, l1])


def icon_contracts():
    bg = icon_base('#6b4a2c', '#3e2a18')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    # clipboard
    d.rounded_rectangle([N * 0.24, N * 0.2, N * 0.76, N * 0.84], radius=N * 0.05, fill=rgba('#8c6239'))
    d.rounded_rectangle([N * 0.29, N * 0.27, N * 0.71, N * 0.79], radius=N * 0.02, fill=rgba('#f1e8d2'))
    for i in range(5):
        y = N * (0.38 + i * 0.075)
        d.line([(N * 0.35, y), (N * (0.65 - (0.1 if i == 4 else 0)), y)], fill=rgba('#a49a82'), width=int(N * 0.018))
    d.rounded_rectangle([N * 0.40, N * 0.16, N * 0.60, N * 0.27], radius=N * 0.03, fill=rgba('#c9c3b5'))
    d.rounded_rectangle([N * 0.45, N * 0.13, N * 0.55, N * 0.19], radius=N * 0.02, fill=rgba('#c9c3b5'))
    # wax seal
    cx, cy, r = N * 0.64, N * 0.72, N * 0.10
    pts = []
    for i in range(24):
        a = i / 24 * math.tau
        rr = r * (1.0 if i % 2 == 0 else 0.9)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=rgba('#b3261e'))
    d.ellipse([cx - r * 0.62, cy - r * 0.62, cx + r * 0.62, cy + r * 0.62], fill=rgba('#cf3a2e'))
    d.line([(cx - r * 0.3, cy), (cx - r * 0.05, cy + r * 0.28), (cx + r * 0.35, cy - r * 0.3)], fill=rgba('#f7d6cf'), width=int(N * 0.02))
    return finish(bg, [l1])


def icon_wallet():
    bg = icon_base('#24493a', '#112a21')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    # back card (tag)
    d.rounded_rectangle([N * 0.28, N * 0.18, N * 0.80, N * 0.56], radius=N * 0.05, fill=rgba('#e8b23a'))
    d.ellipse([N * 0.68, N * 0.24, N * 0.75, N * 0.31], fill=rgba('#24493a'))
    # front card (licence)
    l2 = layer()
    d2 = ImageDraw.Draw(l2)
    d2.rounded_rectangle([N * 0.18, N * 0.38, N * 0.78, N * 0.80], radius=N * 0.05, fill=rgba('#ff7a1f'))
    d2.rounded_rectangle([N * 0.18, N * 0.38, N * 0.78, N * 0.50], radius=N * 0.05, fill=rgba('#e3631a'))
    d2.rectangle([N * 0.18, N * 0.45, N * 0.78, N * 0.50], fill=rgba('#e3631a'))
    d2.rounded_rectangle([N * 0.24, N * 0.56, N * 0.38, N * 0.72], radius=N * 0.02, fill=rgba('#fbe3cc'))
    for i in range(3):
        y = N * (0.58 + i * 0.05)
        d2.line([(N * 0.43, y), (N * (0.72 - i * 0.06), y)], fill=rgba('#fbe3cc'), width=int(N * 0.02))
    return finish(bg, [l1, l2])


def antler(d, cx, cy, s, flip, col):
    sgn = -1 if flip else 1
    main = [(0, 0), (0.18, -0.18), (0.32, -0.42), (0.36, -0.68), (0.30, -0.92)]
    pts = [(cx + sgn * x * s, cy + y * s) for x, y in main]
    d.line(pts, fill=col, width=int(s * 0.075), joint='curve')
    for (bx, by), (tx, ty) in [((0.22, -0.24), (0.12, -0.55)), ((0.32, -0.45), (0.18, -0.80)), ((0.36, -0.68), (0.24, -1.02))]:
        d.line([(cx + sgn * bx * s, cy + by * s), (cx + sgn * tx * s, cy + ty * s)], fill=col, width=int(s * 0.06))
        d.ellipse([cx + sgn * tx * s - s * 0.03, cy + ty * s - s * 0.03, cx + sgn * tx * s + s * 0.03, cy + ty * s + s * 0.03], fill=col)
    d.line([(cx + sgn * 0.06 * s, cy - 0.05 * s), (cx + sgn * 0.0 * s, cy - 0.28 * s)], fill=col, width=int(s * 0.05))


def icon_trophies():
    bg = icon_base('#5a3b22', '#2c1c10')
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    # shield plaque
    d0.polygon([P(0.3, 0.52), P(0.7, 0.52), P(0.66, 0.78), P(0.5, 0.86), P(0.34, 0.78)], fill=rgba('#8a5a32'))
    d0.polygon([P(0.34, 0.55), P(0.66, 0.55), P(0.63, 0.76), P(0.5, 0.82), P(0.37, 0.76)], fill=rgba('#a06c3e'))
    l1 = layer()
    d1 = ImageDraw.Draw(l1)
    col = rgba('#f0e6d0')
    antler(d1, N * 0.46, N * 0.56, N * 0.42, True, col)
    antler(d1, N * 0.54, N * 0.56, N * 0.42, False, col)
    d1.ellipse([N * 0.43, N * 0.52, N * 0.57, N * 0.62], fill=rgba('#d8c8a8'))
    return finish(bg, [l0, l1])


def icon_calls():
    bg = icon_base('#7a2e1e', '#3c140c')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    # grunt tube: mouthpiece + flexible tube + bell
    d.rounded_rectangle([N * 0.16, N * 0.47, N * 0.36, N * 0.57], radius=N * 0.04, fill=rgba('#2d2a26'))
    d.polygon([P(0.34, 0.46), P(0.52, 0.44), P(0.52, 0.60), P(0.34, 0.58)], fill=rgba('#a3794a'))
    for i in range(4):
        x = N * (0.37 + i * 0.04)
        d.line([(x, N * 0.455), (x, N * 0.585)], fill=rgba('#7c5a35'), width=int(N * 0.012))
    d.polygon([P(0.52, 0.44), P(0.60, 0.40), P(0.60, 0.64), P(0.52, 0.60)], fill=rgba('#2d2a26'))
    # sound waves
    for i, r in enumerate([0.12, 0.2, 0.28]):
        d.arc([N * (0.62 - r), N * (0.52 - r), N * (0.62 + r), N * (0.52 + r)], -50, 50, fill=rgba('#ffd9b8', 240 - i * 50), width=int(N * 0.03))
    return finish(bg, [l1])


def icon_flashlight():
    bg = icon_base('#22262a', '#0c0e10')
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    # beam
    beam = layer()
    db = ImageDraw.Draw(beam)
    db.polygon([P(0.56, 0.36), P(1.05, -0.05), P(1.05, 0.62)], fill=rgba('#fff2b0', 120))
    beam = beam.filter(ImageFilter.GaussianBlur(N * 0.02))
    l1 = layer()
    d1 = ImageDraw.Draw(l1)
    # torch body rotated: draw along a diagonal
    def rot(pts, cx, cy, a):
        ca, sa = math.cos(a), math.sin(a)
        return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]
    a = -0.62
    cx, cy = N * 0.42, N * 0.56
    body = [(N * 0.14, N * 0.51), (N * 0.52, N * 0.51), (N * 0.52, N * 0.61), (N * 0.14, N * 0.61)]
    head = [(N * 0.52, N * 0.48), (N * 0.66, N * 0.45), (N * 0.66, N * 0.67), (N * 0.52, N * 0.64)]
    d1.polygon(rot(body, cx, cy, a), fill=rgba('#3a3f44'))
    d1.polygon(rot(head, cx, cy, a), fill=rgba('#55606a'))
    lens = [(N * 0.655, N * 0.46), (N * 0.675, N * 0.46), (N * 0.675, N * 0.66), (N * 0.655, N * 0.66)]
    d1.polygon(rot(lens, cx, cy, a), fill=rgba('#fff4c0'))
    btn = [(N * 0.30, N * 0.495), (N * 0.36, N * 0.495), (N * 0.36, N * 0.51), (N * 0.30, N * 0.51)]
    d1.polygon(rot(btn, cx, cy, a), fill=rgba('#ff7a1f'))
    for i in range(5):
        g = [(N * (0.17 + i * 0.05), N * 0.515), (N * (0.19 + i * 0.05), N * 0.515), (N * (0.19 + i * 0.05), N * 0.605), (N * (0.17 + i * 0.05), N * 0.605)]
        d1.polygon(rot(g, cx, cy, a), fill=rgba('#2c3034'))
    bg.alpha_composite(beam)
    return finish(bg, [l1])


def gear(d, cx, cy, r, teeth, col, hole):
    pts = []
    for i in range(teeth * 4):
        a = i / (teeth * 4) * math.tau
        rr = r if (i % 4) in (0, 1) else r * 0.80
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=col)
    d.ellipse([cx - r * 0.76, cy - r * 0.76, cx + r * 0.76, cy + r * 0.76], fill=col)
    d.ellipse([cx - r * hole, cy - r * hole, cx + r * hole, cy + r * hole], fill=(0, 0, 0, 0))


def icon_settings():
    bg = icon_base('#6c7471', '#3c4240')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    gear(d, N * 0.5, N * 0.5, N * 0.3, 10, rgba('#e6e8e4'), 0.0)
    d.ellipse([N * 0.38, N * 0.38, N * 0.62, N * 0.62], fill=rgba('#59605d'))
    d.ellipse([N * 0.44, N * 0.44, N * 0.56, N * 0.56], fill=rgba('#e6e8e4'))
    return finish(bg, [l1])


def knight_shape(d, cx, cy, s, fill, outline=None, ow=0):
    pts = [(-0.30, 0.42), (0.34, 0.42), (0.30, 0.22), (0.20, 0.10), (0.30, -0.12), (0.26, -0.36), (0.10, -0.52), (-0.04, -0.56), (-0.08, -0.48),
           (-0.18, -0.44), (-0.38, -0.20), (-0.40, -0.06), (-0.30, 0.0), (-0.16, -0.06), (-0.06, -0.10), (-0.18, 0.12), (-0.28, 0.24)]
    poly = [(cx + x * s, cy + y * s) for x, y in pts]
    d.polygon(poly, fill=fill)
    if outline:
        d.line(poly + [poly[0]], fill=outline, width=ow, joint='curve')
    return poly


def icon_chess():
    bg = icon_base('#7a5532', '#3a2412')
    # board corner
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    for i in range(4):
        for j in range(4):
            if (i + j) % 2 == 0:
                d0.rectangle([N * (0.0 + i * 0.25), N * (0.68 + j * 0.25), N * (0.25 + i * 0.25), N * (0.93 + j * 0.25)], fill=rgba('#e9d8b6', 70))
    l1 = layer()
    d1 = ImageDraw.Draw(l1)
    knight_shape(d1, N * 0.5, N * 0.45, N * 0.62, rgba('#f6f0e4'))
    d1.rounded_rectangle([N * 0.27, N * 0.70, N * 0.73, N * 0.78], radius=N * 0.02, fill=rgba('#f6f0e4'))
    d1.ellipse([N * 0.47, N * 0.28, N * 0.51, N * 0.32], fill=rgba('#3a2412'))
    return finish(bg, [l0, l1])


def die(d, cx, cy, s, a, pips, col='#f8f6f0', pip='#1e2a22'):
    ca, sa = math.cos(a), math.sin(a)
    def T(x, y):
        return (cx + (x * ca - y * sa) * s, cy + (x * sa + y * ca) * s)
    # rounded square approx
    pts = []
    for i in range(40):
        t = i / 40 * math.tau
        x = math.copysign(abs(math.cos(t)) ** 0.3, math.cos(t)) * 0.5
        y = math.copysign(abs(math.sin(t)) ** 0.3, math.sin(t)) * 0.5
        pts.append(T(x, y))
    d.polygon(pts, fill=rgba(col))
    layout = {1: [(0, 0)], 2: [(-1, -1), (1, 1)], 3: [(-1, -1), (0, 0), (1, 1)], 4: [(-1, -1), (1, -1), (-1, 1), (1, 1)],
              5: [(-1, -1), (1, -1), (0, 0), (-1, 1), (1, 1)], 6: [(-1, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1)]}[pips]
    for px_, py_ in layout:
        x, y = T(px_ * 0.24, py_ * 0.24)
        r = s * 0.07
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgba(pip))


def icon_dice():
    bg = icon_base('#2f6b45', '#173a24')
    l1 = layer()
    die(ImageDraw.Draw(l1), N * 0.38, N * 0.58, N * 0.36, -0.25, 5)
    l2 = layer()
    die(ImageDraw.Draw(l2), N * 0.64, N * 0.38, N * 0.30, 0.35, 3, col='#ff8a33', pip='#ffffff')
    return finish(bg, [l1, l2])


def duck(d, cx, cy, s, wing_up=True, col='#1e2424', flip=False):
    """a flying duck in profile, heading right: body, neck and head with bill, tail, both wings"""
    sg = -1 if flip else 1
    c = rgba(col)

    def T(x, y):
        return (cx + sg * x * s, cy + y * s)
    # body: an elongated ellipse as a polygon
    body = [T(-0.36 + 0.66 * (0.5 - 0.5 * math.cos(t)), 0.075 * math.sin(t) * (1.2 if math.sin(t) > 0 else 0.9) + 0.02) for t in
            [i / 24 * math.tau for i in range(25)]]
    d.polygon(body, fill=c)
    # neck and head
    d.polygon([T(0.22, -0.02), T(0.36, -0.10), T(0.42, -0.08), T(0.30, 0.05)], fill=c)
    hx, hy = T(0.42, -0.09)
    r = 0.065 * s
    d.ellipse([hx - r, hy - r, hx + r, hy + r], fill=c)
    d.polygon([T(0.46, -0.11), T(0.58, -0.075), T(0.46, -0.05)], fill=c)
    # tail
    d.polygon([T(-0.32, 0.0), T(-0.48, -0.03), T(-0.46, 0.05), T(-0.30, 0.06)], fill=c)
    # wings
    if wing_up:
        d.polygon([T(-0.10, -0.02), T(0.10, -0.03), T(-0.02, -0.30), T(-0.18, -0.52), T(-0.22, -0.42)], fill=c)
        d.polygon([T(-0.02, -0.01), T(0.16, -0.02), T(0.10, -0.22), T(0.02, -0.40), T(-0.04, -0.30)], fill=rgba(col, 200))
    else:
        d.polygon([T(-0.10, 0.04), T(0.12, 0.04), T(0.02, 0.28), T(-0.16, 0.46), T(-0.20, 0.36)], fill=c)


def icon_flush():
    bg = icon_base('#f2a65e', '#7b4a6b')
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    # sun low
    d0.ellipse([N * 0.55, N * 0.52, N * 0.85, N * 0.82], fill=rgba('#ffd27a', 220))
    # reeds
    for i in range(14):
        x = N * (0.02 + i * 0.075)
        h = N * (0.18 + (i * 37 % 10) / 60)
        d0.line([(x, N), (x + N * 0.02, N - h)], fill=rgba('#3a2a22'), width=int(N * 0.018))
        if i % 3 == 0:
            d0.rounded_rectangle([x + N * 0.008, N - h - N * 0.06, x + N * 0.035, N - h + N * 0.02], radius=N * 0.012, fill=rgba('#3a2a22'))
    l1 = layer()
    d1 = ImageDraw.Draw(l1)
    duck(d1, N * 0.42, N * 0.40, N * 0.42, True, '#2a2224')
    duck(d1, N * 0.72, N * 0.26, N * 0.22, False, '#2a2224')
    # crosshair ring
    l2 = layer()
    d2 = ImageDraw.Draw(l2)
    cx, cy, r = N * 0.42, N * 0.40, N * 0.17
    d2.ellipse([cx - r, cy - r, cx + r, cy + r], outline=rgba('#ffffff', 230), width=int(N * 0.014))
    for a in range(4):
        ang = a * math.pi / 2
        d2.line([(cx + math.cos(ang) * r * 0.6, cy + math.sin(ang) * r * 0.6), (cx + math.cos(ang) * r * 1.3, cy + math.sin(ang) * r * 1.3)],
                fill=rgba('#ffffff', 230), width=int(N * 0.014))
    return finish(bg, [l0, l1, l2])


def icon_compass():
    bg = icon_base('#3b4a5a', '#1c2530')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    c, r = N / 2, N * 0.32
    d.ellipse([c - r, c - r, c + r, c + r], outline=rgba('#e8e4d8'), width=int(N * 0.025))
    d.polygon([(c, c - r * 0.85), (c + r * 0.18, c), (c - r * 0.18, c)], fill=rgba('#e5574b'))
    d.polygon([(c, c + r * 0.85), (c + r * 0.18, c), (c - r * 0.18, c)], fill=rgba('#e8e4d8'))
    return finish(bg, [l1])


def icon_messages():
    bg = icon_base('#4fae6a', '#1f6a3e')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    # a radio-style speech bubble with a tail, and a second smaller reply behind it
    d.rounded_rectangle([N * 0.46, N * 0.22, N * 0.84, N * 0.50], radius=N * 0.12, fill=rgba('#bfe8c8'))
    d.polygon([P(0.74, 0.46), P(0.82, 0.58), P(0.66, 0.48)], fill=rgba('#bfe8c8'))
    l2 = layer()
    d2 = ImageDraw.Draw(l2)
    d2.rounded_rectangle([N * 0.16, N * 0.36, N * 0.70, N * 0.72], radius=N * 0.16, fill=rgba('#ffffff'))
    d2.polygon([P(0.24, 0.66), P(0.16, 0.82), P(0.36, 0.70)], fill=rgba('#ffffff'))
    for i in range(3):
        cx = N * (0.31 + i * 0.12)
        d2.ellipse([cx - N * 0.035, N * 0.505, cx + N * 0.035, N * 0.575], fill=rgba('#2f8a4e'))
    return finish(bg, [l1, l2])


def icon_journal():
    bg = icon_base('#8a6a3a', '#4a3216')
    l1 = layer()
    d = ImageDraw.Draw(l1)
    # a leather field notebook: cover, page edges, elastic band, a ribbon, and a rising tally line embossed on it
    d.rounded_rectangle([N * 0.24, N * 0.16, N * 0.78, N * 0.84], radius=N * 0.05, fill=rgba('#f0e6cf'))
    d.rounded_rectangle([N * 0.20, N * 0.14, N * 0.74, N * 0.82], radius=N * 0.06, fill=rgba('#3a2a1a'))
    d.rounded_rectangle([N * 0.22, N * 0.16, N * 0.72, N * 0.80], radius=N * 0.05, fill=rgba('#5c4127'))
    d.rectangle([N * 0.62, N * 0.14, N * 0.66, N * 0.82], fill=rgba('#c9622a'))
    d.polygon([P(0.36, 0.80), P(0.42, 0.80), P(0.42, 0.92), P(0.39, 0.88), P(0.36, 0.92)], fill=rgba('#e8b23a'))
    pts = [P(0.29, 0.62), P(0.37, 0.54), P(0.44, 0.58), P(0.53, 0.40)]
    d.line(pts, fill=rgba('#e8c88a'), width=int(N * 0.035), joint='curve')
    d.polygon([P(0.50, 0.36), P(0.58, 0.36), P(0.56, 0.45)], fill=rgba('#e8c88a'))
    return finish(bg, [l1])


def icon_camera():
    """[1.4.0] the phone's own Camera: a modern camera on a warm sunset field"""
    bg = icon_base('#f2a24a', '#b4471c')
    l0 = layer()
    d0 = ImageDraw.Draw(l0)
    # low hills under the body, like a sunset meadow
    d0.ellipse([-N * 0.2, N * 0.70, N * 0.7, N * 1.2], fill=rgba('#7a2f14', 150))
    d0.ellipse([N * 0.35, N * 0.74, N * 1.3, N * 1.25], fill=rgba('#5e2410', 150))
    l1 = layer()
    d = ImageDraw.Draw(l1)
    # body, hump, shutter button
    d.rounded_rectangle([N * 0.30, N * 0.25, N * 0.50, N * 0.36], radius=N * 0.03, fill=rgba('#26221e'))
    d.rounded_rectangle([N * 0.16, N * 0.32, N * 0.84, N * 0.76], radius=N * 0.10, fill=rgba('#2f2a25'))
    d.rounded_rectangle([N * 0.16, N * 0.32, N * 0.84, N * 0.42], radius=N * 0.10, fill=rgba('#3d3630'))
    d.rounded_rectangle([N * 0.64, N * 0.27, N * 0.76, N * 0.34], radius=N * 0.02, fill=rgba('#ff7a1f'))
    # lens: rings, glass, reflections
    cx, cy = N * 0.5, N * 0.55
    for r, col in ((0.20, '#16130f'), (0.165, '#4a423a'), (0.145, '#0d0c0b'), (0.11, '#1d3a52'), (0.07, '#2a5c80')):
        d.ellipse([cx - N * r, cy - N * r, cx + N * r, cy + N * r], fill=rgba(col))
    d.ellipse([cx - N * 0.085, cy - N * 0.095, cx - N * 0.02, cy - N * 0.035], fill=rgba('#cfe8ff', 210))
    d.ellipse([cx + N * 0.04, cy + N * 0.04, cx + N * 0.07, cy + N * 0.07], fill=rgba('#cfe8ff', 120))
    # flash window
    d.rounded_rectangle([N * 0.22, N * 0.37, N * 0.32, N * 0.42], radius=N * 0.015, fill=rgba('#f4efe2'))
    return finish(bg, [l0, l1])


APPS = [icon_clock, icon_weather, icon_cams, icon_maps, icon_contracts, icon_wallet, icon_trophies, icon_calls,
        icon_flashlight, icon_settings, icon_chess, icon_dice, icon_flush, icon_compass, icon_messages, icon_journal, icon_camera]


def build_apps():
    cell = 192
    sheet = Image.new('RGBA', (cell * 8, cell * 3), (0, 0, 0, 0))
    for i, fn in enumerate(APPS):
        img = down(fn(), cell)
        sheet.alpha_composite(img, ((i % 8) * cell, (i // 8) * cell))
    sheet.save(os.path.join(OUT, 'apps.png'))


# ============================================================================================ glyphs
GC = 96
GS = GC * SS


def glyph_names():
    src = open(os.path.join(R, 'src/com/formaworks/frontierhunts/phone/client/ui/G.java')).read()
    body = src.split('public enum G {', 1)[1].split(';', 1)[0]
    return [n.strip() for n in body.replace('\n', ' ').split(',') if n.strip()]


W = (255, 255, 255, 255)
LW = GS * 0.085


def pen(d, pts, w=None, closed=False):
    w = w or LW
    if closed:
        pts = pts + [pts[0]]
    d.line(pts, fill=W, width=int(w), joint='curve')
    for p in (pts[0], pts[-1]):
        d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=W)


def Q(x, y):
    return (x * GS, y * GS)


def circ(d, cx, cy, r, fill=True, w=None):
    if fill:
        d.ellipse([(cx - r) * GS, (cy - r) * GS, (cx + r) * GS, (cy + r) * GS], fill=W)
    else:
        d.ellipse([(cx - r) * GS, (cy - r) * GS, (cx + r) * GS, (cy + r) * GS], outline=W, width=int(w or LW))


def clear(d, cx, cy, r):
    d.ellipse([(cx - r) * GS, (cy - r) * GS, (cx + r) * GS, (cy + r) * GS], fill=(0, 0, 0, 0))


def rrect(d, x0, y0, x1, y1, r, fill=True, w=None):
    if fill:
        d.rounded_rectangle([x0 * GS, y0 * GS, x1 * GS, y1 * GS], radius=r * GS, fill=W)
    else:
        d.rounded_rectangle([x0 * GS, y0 * GS, x1 * GS, y1 * GS], radius=r * GS, outline=W, width=int(w or LW))


def poly(d, pts):
    d.polygon([Q(x, y) for x, y in pts], fill=W)


def arcp(cx, cy, r, a0, a1, n=24):
    return [Q(cx + math.sin(a0 + (a1 - a0) * i / n) * r, cy - math.cos(a0 + (a1 - a0) * i / n) * r) for i in range(n + 1)]


def moon_phase(d, phase):
    # phase 0 = full, 4 = new (Minecraft: 0 full)
    c, r = 0.5, 0.32
    img = Image.new('L', (GS, GS), 0)
    dd = ImageDraw.Draw(img)
    dd.ellipse([(c - r) * GS, (c - r) * GS, (c + r) * GS, (c + r) * GS], fill=255)
    lit = Image.new('L', (GS, GS), 0)
    dl = ImageDraw.Draw(lit)
    # illuminated fraction by phase (0 full .. 4 new .. back)
    k = {0: 1.0, 1: 0.75, 2: 0.5, 3: 0.25, 4: 0.0, 5: 0.25, 6: 0.5, 7: 0.75}[phase]
    waxing = phase > 4
    if k >= 0.999:
        lit = img.copy()
    elif k > 0.001:
        # terminator ellipse
        ex = abs(1 - 2 * k) * r
        side = 1 if waxing else -1
        half = Image.new('L', (GS, GS), 0)
        dh = ImageDraw.Draw(half)
        if side > 0:
            dh.rectangle([c * GS, 0, GS, GS], fill=255)
        else:
            dh.rectangle([0, 0, c * GS, GS], fill=255)
        lit = ImageChops.multiply(img, half)
        ell = Image.new('L', (GS, GS), 0)
        ImageDraw.Draw(ell).ellipse([(c - ex) * GS, (c - r) * GS, (c + ex) * GS, (c + r) * GS], fill=255)
        if k > 0.5:
            lit = ImageChops.lighter(lit, ImageChops.multiply(img, ell))
        else:
            lit = ImageChops.subtract(lit, ell)
    # draw: faint full disc + lit part
    d.bitmap((0, 0), img.point(lambda v: v * 60 // 255), fill=W)
    return lit


def glyph(name):
    img = Image.new('RGBA', (GS, GS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    n = name
    if n == 'BACK':
        pen(d, [Q(0.62, 0.22), Q(0.36, 0.5), Q(0.62, 0.78)], LW * 1.1)
    elif n == 'FORWARD':
        pen(d, [Q(0.38, 0.22), Q(0.64, 0.5), Q(0.38, 0.78)], LW * 1.1)
    elif n == 'CLOSE':
        pen(d, [Q(0.27, 0.27), Q(0.73, 0.73)])
        pen(d, [Q(0.73, 0.27), Q(0.27, 0.73)])
    elif n == 'PLUS':
        pen(d, [Q(0.5, 0.22), Q(0.5, 0.78)])
        pen(d, [Q(0.22, 0.5), Q(0.78, 0.5)])
    elif n == 'MINUS':
        pen(d, [Q(0.22, 0.5), Q(0.78, 0.5)])
    elif n == 'CHECK':
        pen(d, [Q(0.22, 0.52), Q(0.42, 0.72), Q(0.80, 0.30)], LW * 1.1)
    elif n == 'MORE':
        for x in (0.25, 0.5, 0.75):
            circ(d, x, 0.5, 0.07)
    elif n == 'REFRESH':
        pen(d, arcp(0.5, 0.5, 0.28, 0.3, math.tau - 0.6))
        poly(d, [(0.62, 0.14), (0.78, 0.30), (0.56, 0.34)])
    elif n == 'TRASH':
        rrect(d, 0.28, 0.32, 0.72, 0.82, 0.06, False)
        pen(d, [Q(0.2, 0.26), Q(0.8, 0.26)])
        pen(d, [Q(0.4, 0.18), Q(0.6, 0.18)])
        pen(d, [Q(0.43, 0.44), Q(0.43, 0.70)], LW * 0.7)
        pen(d, [Q(0.57, 0.44), Q(0.57, 0.70)], LW * 0.7)
    elif n == 'SAVE':
        pen(d, [Q(0.5, 0.16), Q(0.5, 0.58)])
        pen(d, [Q(0.32, 0.42), Q(0.5, 0.60), Q(0.68, 0.42)])
        pen(d, [Q(0.2, 0.62), Q(0.2, 0.80), Q(0.8, 0.80), Q(0.8, 0.62)])
    elif n == 'GEAR':
        gd = ImageDraw.Draw(img)
        gear(gd, GS * 0.5, GS * 0.5, GS * 0.36, 8, W, 0.36)
    elif n == 'INFO':
        circ(d, 0.5, 0.5, 0.36, False)
        circ(d, 0.5, 0.32, 0.05)
        pen(d, [Q(0.5, 0.45), Q(0.5, 0.68)])
    elif n == 'WARN':
        tri = [(0.5, 0.12), (0.90, 0.84), (0.10, 0.84)]
        poly(d, tri)
        d.line([Q(0.5, 0.38), Q(0.5, 0.60)], fill=(0, 0, 0, 0), width=int(LW))
        clear(d, 0.5, 0.71, 0.05)
    elif n == 'LOCK':
        rrect(d, 0.24, 0.44, 0.76, 0.84, 0.08)
        pen(d, arcp(0.5, 0.44, 0.16, -math.pi / 2, math.pi / 2), LW * 0.9)
        pen(d, [Q(0.34, 0.44), Q(0.34, 0.36)], LW * 0.9)
        pen(d, [Q(0.66, 0.44), Q(0.66, 0.36)], LW * 0.9)
    elif n == 'BOLT':
        poly(d, [(0.58, 0.08), (0.24, 0.56), (0.48, 0.56), (0.40, 0.92), (0.78, 0.40), (0.54, 0.40)])
    elif n == 'FLASHLIGHT':
        poly(d, [(0.30, 0.18), (0.70, 0.18), (0.62, 0.40), (0.38, 0.40)])
        rrect(d, 0.38, 0.40, 0.62, 0.86, 0.05)
        clear(d, 0.5, 0.54, 0.05)
    elif n == 'NIGHT':
        circ(d, 0.5, 0.5, 0.32)
        clear(d, 0.64, 0.38, 0.27)
    elif n == 'BELL':
        poly(d, [(0.5, 0.16), (0.70, 0.28), (0.74, 0.62), (0.84, 0.72), (0.16, 0.72), (0.26, 0.62), (0.30, 0.28)])
        circ(d, 0.5, 0.80, 0.08)
    elif n == 'BELL_OFF':
        poly(d, [(0.5, 0.16), (0.70, 0.28), (0.74, 0.62), (0.84, 0.72), (0.16, 0.72), (0.26, 0.62), (0.30, 0.28)])
        circ(d, 0.5, 0.80, 0.08)
        d.line([Q(0.16, 0.16), Q(0.84, 0.84)], fill=(0, 0, 0, 0), width=int(LW * 2.2))
        pen(d, [Q(0.18, 0.18), Q(0.82, 0.82)])
    elif n == 'ALARM':
        circ(d, 0.5, 0.54, 0.30, False)
        pen(d, [Q(0.5, 0.38), Q(0.5, 0.55), Q(0.62, 0.62)], LW * 0.9)
        pen(d, arcp(0.23, 0.24, 0.10, -1.2, 1.2), LW * 0.9)
        pen(d, arcp(0.77, 0.24, 0.10, -1.2, 1.2), LW * 0.9)
    elif n == 'PIN':
        circ(d, 0.5, 0.40, 0.25)
        poly(d, [(0.29, 0.52), (0.71, 0.52), (0.5, 0.90)])
        clear(d, 0.5, 0.40, 0.10)
    elif n == 'FLAG':
        pen(d, [Q(0.28, 0.14), Q(0.28, 0.88)])
        poly(d, [(0.28, 0.16), (0.78, 0.16), (0.66, 0.32), (0.78, 0.48), (0.28, 0.48)])
    elif n == 'TENT':
        poly(d, [(0.5, 0.16), (0.88, 0.80), (0.12, 0.80)])
        poly_clear = [(0.5, 0.48), (0.62, 0.80), (0.38, 0.80)]
        d.polygon([Q(x, y) for x, y in poly_clear], fill=(0, 0, 0, 0))
    elif n == 'LODGE':
        poly(d, [(0.5, 0.14), (0.88, 0.46), (0.12, 0.46)])
        rrect(d, 0.20, 0.44, 0.80, 0.84, 0.02)
        d.rectangle([Q(0.42, 0.58), Q(0.58, 0.84)], fill=(0, 0, 0, 0))
        rrect(d, 0.64, 0.18, 0.74, 0.34, 0.01)
    elif n == 'BOARD':
        rrect(d, 0.16, 0.16, 0.84, 0.64, 0.04, False)
        pen(d, [Q(0.3, 0.64), Q(0.3, 0.88)])
        pen(d, [Q(0.7, 0.64), Q(0.7, 0.88)])
        rrect(d, 0.28, 0.28, 0.46, 0.50, 0.01)
        rrect(d, 0.54, 0.28, 0.72, 0.44, 0.01)
    elif n == 'RANGER':
        pts = []
        for i in range(10):
            a = i / 10 * math.tau
            rr = 0.36 if i % 2 == 0 else 0.16
            pts.append((0.5 + math.sin(a) * rr, 0.52 - math.cos(a) * rr))
        poly(d, pts)
    elif n == 'CAMERA':
        rrect(d, 0.20, 0.18, 0.80, 0.84, 0.10)
        clear(d, 0.5, 0.58, 0.16)
        circ(d, 0.5, 0.58, 0.09)
        d.rectangle([Q(0.32, 0.27), Q(0.68, 0.36)], fill=(0, 0, 0, 0))
    elif n == 'HOME':
        poly(d, [(0.5, 0.14), (0.88, 0.48), (0.12, 0.48)])
        rrect(d, 0.22, 0.46, 0.78, 0.86, 0.03)
        d.rectangle([Q(0.42, 0.62), Q(0.58, 0.86)], fill=(0, 0, 0, 0))
    elif n == 'TARGET':
        circ(d, 0.5, 0.5, 0.36, False, LW * 0.8)
        circ(d, 0.5, 0.5, 0.20, False, LW * 0.8)
        circ(d, 0.5, 0.5, 0.06)
    elif n == 'COMPASS':
        circ(d, 0.5, 0.5, 0.38, False, LW * 0.8)
        poly(d, [(0.5, 0.20), (0.60, 0.50), (0.40, 0.50)])
        p2 = [(0.5, 0.80), (0.60, 0.50), (0.40, 0.50)]
        d.polygon([Q(x, y) for x, y in p2], outline=W, width=int(LW * 0.5))
    elif n == 'ARROW':
        poly(d, [(0.5, 0.10), (0.84, 0.86), (0.5, 0.68), (0.16, 0.86)])
    elif n == 'RECENTER':
        circ(d, 0.5, 0.5, 0.26, False, LW * 0.8)
        circ(d, 0.5, 0.5, 0.08)
        for (a, b) in [((0.5, 0.06), (0.5, 0.2)), ((0.5, 0.8), (0.5, 0.94)), ((0.06, 0.5), (0.2, 0.5)), ((0.8, 0.5), (0.94, 0.5))]:
            pen(d, [Q(*a), Q(*b)], LW * 0.8)
    elif n == 'SUN':
        circ(d, 0.5, 0.5, 0.18)
        for i in range(8):
            a = i / 8 * math.tau
            pen(d, [Q(0.5 + math.sin(a) * 0.28, 0.5 - math.cos(a) * 0.28), Q(0.5 + math.sin(a) * 0.38, 0.5 - math.cos(a) * 0.38)], LW * 0.8)
    elif n == 'MOON':
        circ(d, 0.5, 0.5, 0.32)
        clear(d, 0.66, 0.38, 0.26)
    elif n == 'CLOUD':
        gd = ImageDraw.Draw(img)
        for dx, dy, rr in [(-0.16, 0.06, 0.15), (0.0, -0.06, 0.2), (0.17, 0.04, 0.16)]:
            circ(d, 0.5 + dx, 0.52 + dy, rr)
        rrect(d, 0.18, 0.52, 0.82, 0.72, 0.10)
    elif n == 'RAIN':
        for dx, dy, rr in [(-0.14, -0.04, 0.13), (0.02, -0.14, 0.17), (0.17, -0.05, 0.13)]:
            circ(d, 0.5 + dx, 0.42 + dy, rr)
        rrect(d, 0.22, 0.38, 0.80, 0.54, 0.08)
        for x in (0.34, 0.5, 0.66):
            pen(d, [Q(x, 0.64), Q(x - 0.05, 0.84)], LW * 0.75)
    elif n == 'SNOW':
        for i in range(3):
            a = i / 3 * math.pi
            pen(d, [Q(0.5 + math.cos(a) * 0.34, 0.5 + math.sin(a) * 0.34), Q(0.5 - math.cos(a) * 0.34, 0.5 - math.sin(a) * 0.34)], LW * 0.75)
        circ(d, 0.5, 0.5, 0.08)
    elif n == 'WIND':
        pen(d, [Q(0.12, 0.38), Q(0.62, 0.38)] + arcp(0.62, 0.28, 0.10, math.pi, 0.0)[::-1][:12], LW * 0.8)
        pen(d, [Q(0.12, 0.54), Q(0.78, 0.54)], LW * 0.8)
        pen(d, [Q(0.12, 0.70), Q(0.54, 0.70)] + arcp(0.54, 0.80, 0.10, 0.0, math.pi)[:12], LW * 0.8)
    elif n == 'FOG':
        for i, y in enumerate((0.32, 0.5, 0.68)):
            pen(d, [Q(0.18 + 0.06 * (i % 2), y), Q(0.82 - 0.06 * (i % 2), y)], LW * 0.8)
    elif n == 'THERMO':
        rrect(d, 0.42, 0.12, 0.58, 0.66, 0.08, False, LW * 0.7)
        circ(d, 0.5, 0.74, 0.14)
        pen(d, [Q(0.5, 0.40), Q(0.5, 0.70)], LW * 0.7)
    elif n == 'DROP':
        poly(d, [(0.5, 0.12), (0.72, 0.50), (0.28, 0.50)])
        circ(d, 0.5, 0.60, 0.22)
    elif n in ('SUNRISE', 'SUNSET'):
        pen(d, arcp(0.5, 0.66, 0.22, -math.pi / 2, math.pi / 2), LW * 0.8)
        pen(d, [Q(0.12, 0.66), Q(0.88, 0.66)], LW * 0.8)
        pen(d, [Q(0.22, 0.80), Q(0.78, 0.80)], LW * 0.6)
        if n == 'SUNRISE':
            pen(d, [Q(0.5, 0.36), Q(0.5, 0.10)], LW * 0.7)
            pen(d, [Q(0.40, 0.20), Q(0.5, 0.10), Q(0.60, 0.20)], LW * 0.7)
        else:
            pen(d, [Q(0.5, 0.10), Q(0.5, 0.36)], LW * 0.7)
            pen(d, [Q(0.40, 0.26), Q(0.5, 0.36), Q(0.60, 0.26)], LW * 0.7)
    elif n == 'EYE':
        pen(d, arcp(0.5, 0.92, 0.50, -0.72, 0.72), LW * 0.8)
        pen(d, arcp(0.5, 0.08, 0.50, math.pi - 0.72, math.pi + 0.72), LW * 0.8)
        circ(d, 0.5, 0.5, 0.11)
    elif n == 'CLIPBOARD':
        rrect(d, 0.22, 0.16, 0.78, 0.88, 0.06, False, LW * 0.8)
        rrect(d, 0.36, 0.10, 0.64, 0.24, 0.04)
        for y in (0.42, 0.56, 0.70):
            pen(d, [Q(0.34, y), Q(0.66, y)], LW * 0.6)
    elif n == 'TOKEN':
        circ(d, 0.5, 0.5, 0.36)
        clear(d, 0.5, 0.5, 0.27)
        circ(d, 0.5, 0.5, 0.20)
        clear(d, 0.5, 0.5, 0.08)
    elif n == 'TIMER':
        circ(d, 0.5, 0.56, 0.32, False, LW * 0.8)
        pen(d, [Q(0.5, 0.56), Q(0.5, 0.38)], LW * 0.8)
        pen(d, [Q(0.40, 0.12), Q(0.60, 0.12)], LW * 0.8)
        pen(d, [Q(0.5, 0.12), Q(0.5, 0.24)], LW * 0.8)
    elif n == 'ANTLER':
        gd = ImageDraw.Draw(img)
        antler(gd, GS * 0.44, GS * 0.86, GS * 0.70, True, W)
        antler(gd, GS * 0.56, GS * 0.86, GS * 0.70, False, W)
    elif n == 'STAR':
        pts = []
        for i in range(10):
            a = i / 10 * math.tau
            rr = 0.40 if i % 2 == 0 else 0.17
            pts.append((0.5 + math.sin(a) * rr, 0.54 - math.cos(a) * rr))
        poly(d, pts)
    elif n == 'TRACK':
        # a whitetail track: two cloven halves, pointed toes up, a gap between
        for sx in (-1, 1):
            pts = []
            for i in range(31):
                t = i / 30 * math.pi
                # outer edge from the toe (top) round the heel (bottom)
                x = 0.5 + sx * (0.045 + 0.17 * math.sin(t) ** 0.9 * (0.75 + 0.25 * (t / math.pi)))
                y = 0.14 + 0.70 * (1 - math.cos(t)) / 2
                pts.append((x, y))
            pts += [(0.5 + sx * 0.05, 0.80), (0.5 + sx * 0.035, 0.30)]
            poly(d, pts)
        # dewclaws
        circ(d, 0.30, 0.90, 0.035)
        circ(d, 0.70, 0.90, 0.035)
    elif n == 'FISH':
        gd = ImageDraw.Draw(img)
        gd.ellipse([GS * 0.14, GS * 0.32, GS * 0.70, GS * 0.68], fill=W)
        poly(d, [(0.64, 0.5), (0.88, 0.30), (0.88, 0.70)])
        clear(d, 0.28, 0.46, 0.04)
    elif n == 'CROSSHAIR':
        circ(d, 0.5, 0.5, 0.30, False, LW * 0.75)
        for (a, b) in [((0.5, 0.08), (0.5, 0.34)), ((0.5, 0.66), (0.5, 0.92)), ((0.08, 0.5), (0.34, 0.5)), ((0.66, 0.5), (0.92, 0.5))]:
            pen(d, [Q(*a), Q(*b)], LW * 0.75)
    elif n == 'CARD':
        rrect(d, 0.12, 0.24, 0.88, 0.76, 0.07, False, LW * 0.8)
        d.rectangle([Q(0.12, 0.36), Q(0.88, 0.46)], fill=W)
        pen(d, [Q(0.24, 0.62), Q(0.44, 0.62)], LW * 0.7)
    elif n == 'TAG':
        poly(d, [(0.18, 0.18), (0.56, 0.18), (0.86, 0.50), (0.56, 0.84), (0.18, 0.84)])
        clear(d, 0.34, 0.34, 0.07)
    elif n == 'FEATHER':
        gd = ImageDraw.Draw(img)
        gd.ellipse([GS * 0.30, GS * 0.10, GS * 0.70, GS * 0.78], fill=W)
        gd.line([Q(0.5, 0.2), Q(0.5, 0.92)], fill=(0, 0, 0, 0), width=int(LW * 0.6))
        pen(d, [Q(0.5, 0.70), Q(0.5, 0.92)], LW * 0.6)
    elif n == 'SHIELD':
        poly(d, [(0.5, 0.10), (0.84, 0.22), (0.80, 0.56), (0.5, 0.90), (0.20, 0.56), (0.16, 0.22)])
    elif n == 'KNIGHT':
        gd = ImageDraw.Draw(img)
        knight_shape(gd, GS * 0.52, GS * 0.44, GS * 0.78, W)
        rrect(d, 0.22, 0.76, 0.80, 0.88, 0.03)
    elif n == 'DIE':
        rrect(d, 0.16, 0.16, 0.84, 0.84, 0.14)
        for x, y in [(0.33, 0.33), (0.67, 0.67), (0.5, 0.5), (0.67, 0.33), (0.33, 0.67)]:
            clear(d, x, y, 0.065)
    elif n == 'DUCK':
        gd = ImageDraw.Draw(img)
        duck(gd, GS * 0.46, GS * 0.60, GS * 0.92, True, '#ffffff')
    elif n == 'CUP':
        poly(d, [(0.26, 0.16), (0.74, 0.16), (0.70, 0.46), (0.5, 0.60), (0.30, 0.46)])
        pen(d, arcp(0.26, 0.30, 0.10, math.pi, math.tau), LW * 0.7)
        pen(d, arcp(0.74, 0.30, 0.10, 0.0, math.pi), LW * 0.7)
        rrect(d, 0.44, 0.58, 0.56, 0.74, 0.0)
        rrect(d, 0.30, 0.74, 0.70, 0.86, 0.03)
    elif n == 'PERSON':
        circ(d, 0.5, 0.32, 0.16)
        gd = ImageDraw.Draw(img)
        gd.pieslice([GS * 0.18, GS * 0.54, GS * 0.82, GS * 1.18], 180, 360, fill=W)
    elif n == 'CHIP':
        rrect(d, 0.26, 0.26, 0.74, 0.74, 0.08)
        clear(d, 0.5, 0.5, 0.0)
        d.rectangle([Q(0.38, 0.38), Q(0.62, 0.62)], fill=(0, 0, 0, 0))
        for i in range(3):
            t = 0.36 + i * 0.14
            for (a, b) in [((t, 0.12), (t, 0.26)), ((t, 0.74), (t, 0.88)), ((0.12, t), (0.26, t)), ((0.74, t), (0.88, t))]:
                pen(d, [Q(*a), Q(*b)], LW * 0.55)
    elif n == 'GLOBE':
        circ(d, 0.5, 0.5, 0.36, False, LW * 0.7)
        gd = ImageDraw.Draw(img)
        gd.ellipse([Q(0.34, 0.14), Q(0.66, 0.86)], outline=W, width=int(LW * 0.6))
        pen(d, [Q(0.14, 0.5), Q(0.86, 0.5)], LW * 0.6)
        pen(d, [Q(0.2, 0.32), Q(0.8, 0.32)], LW * 0.5)
        pen(d, [Q(0.2, 0.68), Q(0.8, 0.68)], LW * 0.5)
    elif n == 'PUZZLE':
        rrect(d, 0.18, 0.30, 0.70, 0.82, 0.05)
        circ(d, 0.44, 0.24, 0.10)
        circ(d, 0.76, 0.56, 0.10)
        clear(d, 0.18, 0.56, 0.09)
    elif n == 'RESIGN':
        pen(d, [Q(0.28, 0.14), Q(0.28, 0.88)])
        poly(d, [(0.28, 0.16), (0.76, 0.16), (0.76, 0.50), (0.28, 0.50)])
    elif n == 'HANDSHAKE':
        # a draw: "=" in a ring
        circ(d, 0.5, 0.5, 0.36, False, LW * 0.75)
        pen(d, [Q(0.34, 0.42), Q(0.66, 0.42)], LW * 0.8)
        pen(d, [Q(0.34, 0.58), Q(0.66, 0.58)], LW * 0.8)
    elif n == 'UNDO':
        pen(d, [Q(0.30, 0.42)] + arcp(0.52, 0.56, 0.22, -math.pi / 2 - 0.2, math.pi / 2 + 0.6)[::1], LW * 0.85)
        poly(d, [(0.16, 0.42), (0.38, 0.24), (0.38, 0.58)])
    elif n == 'PLAY':
        poly(d, [(0.30, 0.18), (0.82, 0.5), (0.30, 0.82)])
    elif n == 'PAUSE':
        rrect(d, 0.26, 0.20, 0.42, 0.80, 0.04)
        rrect(d, 0.58, 0.20, 0.74, 0.80, 0.04)
    elif n == 'SPEAKER':
        poly(d, [(0.14, 0.38), (0.30, 0.38), (0.52, 0.18), (0.52, 0.82), (0.30, 0.62), (0.14, 0.62)])
        pen(d, arcp(0.52, 0.5, 0.16, 0.6, math.pi - 0.6), LW * 0.7)
        pen(d, arcp(0.52, 0.5, 0.30, 0.6, math.pi - 0.6), LW * 0.7)
    elif n == 'STOP':
        rrect(d, 0.24, 0.24, 0.76, 0.76, 0.06)
    elif n == 'LAYERS':
        for i, y in enumerate((0.26, 0.42, 0.58)):
            pts = [(0.5, y), (0.86, y + 0.14), (0.5, y + 0.28), (0.14, y + 0.14)]
            if i == 0:
                poly(d, pts)
            else:
                pen(d, [Q(0.14, y + 0.14), Q(0.5, y + 0.28), Q(0.86, y + 0.14)], LW * 0.7)
    elif n == 'MAP':
        pen(d, [Q(0.12, 0.24), Q(0.36, 0.14), Q(0.64, 0.24), Q(0.88, 0.14), Q(0.88, 0.76), Q(0.64, 0.86), Q(0.36, 0.76), Q(0.12, 0.86), Q(0.12, 0.24)], LW * 0.7)
        pen(d, [Q(0.36, 0.14), Q(0.36, 0.76)], LW * 0.6)
        pen(d, [Q(0.64, 0.24), Q(0.64, 0.86)], LW * 0.6)
    elif n == 'LIST':
        for y in (0.28, 0.5, 0.72):
            circ(d, 0.20, y, 0.06)
            pen(d, [Q(0.36, y), Q(0.84, y)], LW * 0.8)
    elif n == 'SIGNAL':
        for i in range(4):
            h = 0.18 + i * 0.16
            rrect(d, 0.14 + i * 0.19, 0.84 - h, 0.26 + i * 0.19, 0.84, 0.03)
    elif n == 'EDIT':
        poly(d, [(0.22, 0.70), (0.64, 0.28), (0.74, 0.38), (0.32, 0.80), (0.18, 0.84)])
        poly(d, [(0.68, 0.24), (0.74, 0.18), (0.84, 0.28), (0.78, 0.34)])
    elif n == 'EXPAND':
        pen(d, [Q(0.56, 0.18), Q(0.82, 0.18), Q(0.82, 0.44)], LW * 0.8)
        pen(d, [Q(0.44, 0.82), Q(0.18, 0.82), Q(0.18, 0.56)], LW * 0.8)
        pen(d, [Q(0.80, 0.20), Q(0.58, 0.42)], LW * 0.8)
        pen(d, [Q(0.20, 0.80), Q(0.42, 0.58)], LW * 0.8)
    elif n == 'HOURGLASS':
        pen(d, [Q(0.26, 0.14), Q(0.74, 0.14)], LW * 0.8)
        pen(d, [Q(0.26, 0.86), Q(0.74, 0.86)], LW * 0.8)
        poly(d, [(0.32, 0.18), (0.68, 0.18), (0.52, 0.50), (0.68, 0.82), (0.32, 0.82), (0.48, 0.50)])
    elif n == 'STORM':
        for dx, dy, rr in [(-0.14, -0.06, 0.13), (0.02, -0.16, 0.17), (0.17, -0.07, 0.13)]:
            circ(d, 0.5 + dx, 0.40 + dy, rr)
        rrect(d, 0.22, 0.36, 0.80, 0.50, 0.07)
        poly(d, [(0.54, 0.52), (0.36, 0.74), (0.50, 0.74), (0.42, 0.94), (0.66, 0.66), (0.52, 0.66), (0.60, 0.52)])
    elif n == 'LEAF':
        gd = ImageDraw.Draw(img)
        gd.ellipse([Q(0.22, 0.16), Q(0.78, 0.82)], fill=W)
        gd.line([Q(0.5, 0.24), Q(0.5, 0.92)], fill=(0, 0, 0, 0), width=int(LW * 0.6))
    elif n.startswith('MOON') and len(n) == 5:
        lit = moon_phase(d, int(n[4]))
        img.paste(Image.new('RGBA', (GS, GS), W), (0, 0), lit)
    elif n == 'SHELL':
        rrect(d, 0.36, 0.14, 0.64, 0.68, 0.06)
        rrect(d, 0.32, 0.66, 0.68, 0.86, 0.03)
        d.rectangle([Q(0.36, 0.60), Q(0.64, 0.66)], fill=(0, 0, 0, 0))
    elif n == 'CONTROLS':
        for i, (y, k) in enumerate([(0.3, 0.36), (0.5, 0.64), (0.7, 0.44)]):
            pen(d, [Q(0.14, y), Q(0.86, y)], LW * 0.6)
            circ(d, k, y, 0.08)
    elif n == 'BINOCULARS':
        circ(d, 0.32, 0.62, 0.18)
        circ(d, 0.68, 0.62, 0.18)
        rrect(d, 0.22, 0.24, 0.42, 0.56, 0.05)
        rrect(d, 0.58, 0.24, 0.78, 0.56, 0.05)
        rrect(d, 0.42, 0.40, 0.58, 0.52, 0.02)
        clear(d, 0.32, 0.64, 0.08)
        clear(d, 0.68, 0.64, 0.08)
    elif n == 'HORN':
        poly(d, [(0.14, 0.44), (0.40, 0.42), (0.78, 0.22), (0.86, 0.22), (0.86, 0.78), (0.78, 0.78), (0.40, 0.58), (0.14, 0.56)])
    elif n == 'WAVES':
        for i, x in enumerate((0.22, 0.38, 0.54, 0.70)):
            h = [0.18, 0.34, 0.26, 0.12][i]
            pen(d, [Q(x, 0.5 - h), Q(x, 0.5 + h)], LW * 0.9)
    elif n == 'ROUTE':
        circ(d, 0.24, 0.76, 0.09)
        circ(d, 0.76, 0.24, 0.09)
        pen(d, [Q(0.24, 0.66), Q(0.24, 0.50), Q(0.76, 0.50), Q(0.76, 0.34)], LW * 0.75)
    elif n == 'CALENDAR':
        rrect(d, 0.16, 0.22, 0.84, 0.86, 0.07, False, LW * 0.75)
        d.rectangle([Q(0.16, 0.26), Q(0.84, 0.40)], fill=W)
        pen(d, [Q(0.34, 0.14), Q(0.34, 0.28)], LW * 0.7)
        pen(d, [Q(0.66, 0.14), Q(0.66, 0.28)], LW * 0.7)
        for x in (0.32, 0.5, 0.68):
            for y in (0.54, 0.70):
                circ(d, x, y, 0.045)
    elif n == 'DOT':
        circ(d, 0.5, 0.5, 0.2)
    else:
        circ(d, 0.5, 0.5, 0.3, False)
    return img


def build_glyphs():
    names = glyph_names()
    cols = 16
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new('RGBA', (GC * cols, GC * rows), (0, 0, 0, 0))
    for i, n in enumerate(names):
        g = glyph(n).resize((GC, GC), Image.LANCZOS)
        sheet.alpha_composite(g, ((i % cols) * GC, (i // cols) * GC))
    sheet.save(os.path.join(OUT, 'glyphs.png'))
    return names


# ============================================================================================ weather icons
WN = 128 * SS


def wl():
    return Image.new('RGBA', (WN, WN), (0, 0, 0, 0))


def wsun(d, cx, cy, r):
    sun(d, cx * WN, cy * WN, r * WN)


def wmoon(img, cx, cy, r):
    m = Image.new('L', (WN, WN), 0)
    dm = ImageDraw.Draw(m)
    dm.ellipse([(cx - r) * WN, (cy - r) * WN, (cx + r) * WN, (cy + r) * WN], fill=255)
    dm.ellipse([(cx - r + r * 0.62) * WN, (cy - r - r * 0.2) * WN, (cx + r + r * 0.62) * WN, (cy + r - r * 0.2) * WN], fill=0)
    col = Image.new('RGBA', (WN, WN), rgba('#eef0f6'))
    img.paste(col, (0, 0), m)


def wcloud(d, cx, cy, s, col='#ffffff', shade='#c9d3de'):
    cloud(d, cx * WN, cy * WN, s * WN, col, shade)


def drops(d, xs, y0, col='#5aa8f0', length=0.12):
    for x in xs:
        d.line([((x) * WN, y0 * WN), ((x - 0.04) * WN, (y0 + length) * WN)], fill=rgba(col), width=int(WN * 0.035))


def flakes(d, pts, col='#ffffff', r=0.05):
    for x, y in pts:
        for i in range(3):
            a = i / 3 * math.pi
            d.line([((x + math.cos(a) * r) * WN, (y + math.sin(a) * r) * WN), ((x - math.cos(a) * r) * WN, (y - math.sin(a) * r) * WN)], fill=rgba(col),
                   width=int(WN * 0.018))


def bolt(d, x, y, s):
    pts = [(0.10, 0.0), (-0.10, 0.34), (0.04, 0.34), (-0.06, 0.66), (0.20, 0.24), (0.06, 0.24), (0.16, 0.0)]
    d.polygon([((x + px * s) * WN, (y + py * s) * WN) for px, py in pts], fill=rgba('#ffd23a'))


def weather_icon(name):
    img = wl()
    d = ImageDraw.Draw(img)
    if name == 'clear':
        wsun(d, 0.5, 0.5, 0.24)
    elif name == 'night_clear':
        wmoon(img, 0.46, 0.5, 0.28)
        d = ImageDraw.Draw(img)
        for x, y, r in [(0.78, 0.26, 0.02), (0.84, 0.48, 0.014), (0.70, 0.16, 0.012)]:
            d.ellipse([(x - r) * WN, (y - r) * WN, (x + r) * WN, (y + r) * WN], fill=rgba('#fff3c8'))
    elif name in ('partly', 'partly_night'):
        if name == 'partly':
            wsun(d, 0.62, 0.36, 0.17)
        else:
            wmoon(img, 0.62, 0.36, 0.2)
            d = ImageDraw.Draw(img)
        l = wl()
        wcloud(ImageDraw.Draw(l), 0.44, 0.58, 0.27)
        img.alpha_composite(shadow(l, WN * 0.02, (0, int(WN * 0.01)), 80))
        img.alpha_composite(l)
    elif name == 'cloudy':
        wcloud(d, 0.62, 0.40, 0.2, '#b8c2cc', '#97a2ad')
        wcloud(d, 0.44, 0.56, 0.28)
    elif name in ('rain', 'drizzle'):
        wcloud(d, 0.5, 0.40, 0.28, '#dfe5ea', '#aab5c0')
        drops(d, (0.36, 0.52, 0.68) if name == 'rain' else (0.42, 0.62), 0.66, length=0.16 if name == 'rain' else 0.08)
    elif name in ('thunder', 'storm_night'):
        wcloud(d, 0.5, 0.38, 0.28, '#9aa6b2', '#6f7a86')
        bolt(d, 0.46, 0.56, 0.42)
        drops(d, (0.30, 0.70), 0.64)
    elif name in ('snow', 'sleet'):
        wcloud(d, 0.5, 0.38, 0.28, '#eef2f6', '#bcc6d0')
        flakes(d, [(0.34, 0.74), (0.52, 0.80), (0.70, 0.72)])
        if name == 'sleet':
            drops(d, (0.44, 0.62), 0.66, length=0.08)
    elif name == 'blizzard':
        wcloud(d, 0.56, 0.32, 0.24, '#e6ebf0', '#b7c1cb')
        for i, y in enumerate((0.56, 0.68, 0.80)):
            d.line([(0.14 * WN, y * WN), ((0.86 - i * 0.1) * WN, (y - 0.04) * WN)], fill=rgba('#ffffff', 220), width=int(WN * 0.03))
        flakes(d, [(0.30, 0.62), (0.60, 0.74)], r=0.04)
    elif name == 'fog':
        wcloud(d, 0.5, 0.36, 0.24, '#d5dbe0', '#aab3bb')
        for i, y in enumerate((0.62, 0.72, 0.82)):
            d.line([((0.18 + 0.06 * (i % 2)) * WN, y * WN), ((0.82 - 0.06 * (i % 2)) * WN, y * WN)], fill=rgba('#cfd6dc'), width=int(WN * 0.04))
    elif name == 'dust':
        for i, y in enumerate((0.34, 0.50, 0.66)):
            d.line([(0.14 * WN, y * WN), (0.86 * WN, (y - 0.03) * WN)], fill=rgba('#d99a52' if i % 2 else '#c47a36'), width=int(WN * 0.05))
        for x, y in [(0.3, 0.42), (0.6, 0.58), (0.74, 0.40), (0.44, 0.74)]:
            d.ellipse([(x - 0.02) * WN, (y - 0.02) * WN, (x + 0.02) * WN, (y + 0.02) * WN], fill=rgba('#e8b070'))
    elif name == 'wind':
        col = rgba('#d6e6f2')
        w = int(WN * 0.04)
        d.line([(0.12 * WN, 0.38 * WN), (0.62 * WN, 0.38 * WN)], fill=col, width=w)
        d.arc([0.52 * WN, 0.18 * WN, 0.74 * WN, 0.40 * WN], 90, 300, fill=col, width=w)
        d.line([(0.12 * WN, 0.54 * WN), (0.80 * WN, 0.54 * WN)], fill=col, width=w)
        d.line([(0.12 * WN, 0.70 * WN), (0.54 * WN, 0.70 * WN)], fill=col, width=w)
        d.arc([0.44 * WN, 0.68 * WN, 0.66 * WN, 0.90 * WN], 60, 270, fill=col, width=w)
    elif name == 'moon':
        wmoon(img, 0.5, 0.5, 0.3)
    return img


def build_weather():
    names = ['clear', 'night_clear', 'partly', 'partly_night', 'cloudy', 'rain', 'thunder', 'snow', 'blizzard', 'fog', 'dust', 'wind', 'drizzle', 'sleet',
             'storm_night', 'moon']
    cell = 128
    sheet = Image.new('RGBA', (cell * 4, cell * 4), (0, 0, 0, 0))
    for i, n in enumerate(names):
        g = weather_icon(n).resize((cell, cell), Image.LANCZOS)
        sheet.alpha_composite(g, ((i % 4) * cell, (i // 4) * cell))
    sheet.save(os.path.join(OUT, 'weather.png'))


if __name__ == '__main__':
    what = sys.argv[2:] or ['apps', 'glyphs', 'weather']
    if 'apps' in what:
        build_apps()
    if 'glyphs' in what:
        build_glyphs()
    if 'weather' in what:
        build_weather()
    print('phone art written to', OUT)


# ============================================================================================ chess pieces
CN = 128 * SS


def piece_shape(kind):
    """white-filled silhouette mask (L) of a Staunton-style piece, drawn on a CN square"""
    m = Image.new('L', (CN, CN), 0)
    d = ImageDraw.Draw(m)
    s = CN

    def E(cx, cy, rx, ry):
        d.ellipse([(cx - rx) * s, (cy - ry) * s, (cx + rx) * s, (cy + ry) * s], fill=255)

    def R(x0, y0, x1, y1, r=0.02):
        d.rounded_rectangle([x0 * s, y0 * s, x1 * s, y1 * s], radius=r * s, fill=255)

    def Pg(pts):
        d.polygon([(x * s, y * s) for x, y in pts], fill=255)
    # common base
    R(0.22, 0.80, 0.78, 0.90, 0.03)
    R(0.27, 0.74, 0.73, 0.82, 0.02)
    if kind == 'p':
        Pg([(0.36, 0.76), (0.64, 0.76), (0.57, 0.52), (0.43, 0.52)])
        E(0.5, 0.50, 0.14, 0.04)
        E(0.5, 0.37, 0.13, 0.13)
    elif kind == 'r':
        Pg([(0.33, 0.76), (0.67, 0.76), (0.63, 0.36), (0.37, 0.36)])
        R(0.30, 0.26, 0.70, 0.38, 0.015)
        for x in (0.30, 0.45, 0.60):
            R(x, 0.16, x + 0.10, 0.28, 0.01)
    elif kind == 'b':
        Pg([(0.37, 0.76), (0.63, 0.76), (0.58, 0.58), (0.42, 0.58)])
        E(0.5, 0.57, 0.16, 0.04)
        # a tall mitre, pointed, with the slit cut through it
        pts = []
        for i in range(41):
            t = i / 40 * math.pi
            x = 0.5 + 0.15 * math.cos(t) * (1.0 if math.sin(t) < 0.999 else 1.0)
            y = 0.50 - 0.30 * math.sin(t) ** 0.8
            pts.append((x, y))
        Pg(pts + [(0.35, 0.52), (0.65, 0.52)])
        Pg([(0.42, 0.30), (0.5, 0.12), (0.58, 0.30)])
        E(0.5, 0.11, 0.035, 0.035)
        d.line([(0.56 * s, 0.24 * s), (0.44 * s, 0.38 * s)], fill=0, width=int(0.035 * s))
    elif kind == 'n':
        pts = [(0.30, 0.78), (0.72, 0.78), (0.70, 0.60), (0.64, 0.46), (0.70, 0.30), (0.64, 0.16), (0.50, 0.10), (0.44, 0.06), (0.42, 0.13),
               (0.33, 0.18), (0.22, 0.36), (0.22, 0.45), (0.30, 0.48), (0.42, 0.42), (0.46, 0.44), (0.36, 0.58), (0.30, 0.66)]
        Pg(pts)
    elif kind == 'q':
        Pg([(0.34, 0.76), (0.66, 0.76), (0.60, 0.50), (0.40, 0.50)])
        E(0.5, 0.50, 0.17, 0.04)
        Pg([(0.30, 0.46), (0.70, 0.46), (0.76, 0.20), (0.62, 0.34), (0.58, 0.14), (0.50, 0.32), (0.42, 0.14), (0.38, 0.34), (0.24, 0.20)])
        for x, y in ((0.24, 0.18), (0.42, 0.12), (0.58, 0.12), (0.76, 0.18)):
            E(x, y, 0.04, 0.04)
    elif kind == 'k':
        Pg([(0.34, 0.76), (0.66, 0.76), (0.60, 0.50), (0.40, 0.50)])
        E(0.5, 0.50, 0.17, 0.04)
        Pg([(0.30, 0.46), (0.70, 0.46), (0.68, 0.30), (0.58, 0.24), (0.42, 0.24), (0.32, 0.30)])
        R(0.47, 0.06, 0.53, 0.26, 0.01)
        R(0.40, 0.11, 0.60, 0.17, 0.01)
    return m


def piece(kind, white):
    m = piece_shape(kind)
    outline = m.filter(ImageFilter.MaxFilter(int(CN * 0.03) | 1))
    img = Image.new('RGBA', (CN, CN), (0, 0, 0, 0))
    edge = rgba('#1c1814') if white else rgba('#e9dfc8')
    img.paste(Image.new('RGBA', (CN, CN), edge), (0, 0), outline)
    # body with vertical shading
    top, bot = (rgba('#fbf6ea'), rgba('#d8ccb2')) if white else (rgba('#4a433c'), rgba('#1f1b18'))
    body = vgrad((CN, CN), top, bot)
    # side light: lighter left
    hl = Image.new('RGBA', (CN, CN), (255, 255, 255, 0))
    hd = ImageDraw.Draw(hl)
    hd.rectangle([0, 0, CN * 0.48, CN], fill=(255, 255, 255, 34 if white else 22))
    body.alpha_composite(hl.filter(ImageFilter.GaussianBlur(CN * 0.05)))
    img.paste(body, (0, 0), m)
    if kind == 'n':
        d = ImageDraw.Draw(img)
        d.ellipse([CN * 0.46, CN * 0.22, CN * 0.53, CN * 0.29], fill=edge)
    sh = shadow(img, CN * 0.02, (0, int(CN * 0.015)), 90)
    out = Image.new('RGBA', (CN, CN), (0, 0, 0, 0))
    out.alpha_composite(sh)
    out.alpha_composite(img)
    return out


def build_chess():
    cell = 128
    sheet = Image.new('RGBA', (cell * 6, cell * 2), (0, 0, 0, 0))
    for row, white in enumerate((True, False)):
        for col, k in enumerate('pnbrqk'):
            sheet.alpha_composite(piece(k, white).resize((cell, cell), Image.LANCZOS), (col * cell, row * cell))
    sheet.save(os.path.join(OUT, 'chess.png'))


# ============================================================================================ flush birds
BN = 96 * SS
BIRDS = ['MALLARD', 'TEAL', 'PHEASANT', 'GROUSE', 'DOVE', 'GOOSE', 'HEN', 'HAWK']
BCOL = {
    'MALLARD': dict(body='#8d8a86', head='#1f6b3a', wing='#6e6a64', belly='#c9c4bc', bill='#e8c33a', tail='#2a2a2a', size=1.0),
    'TEAL': dict(body='#9a958e', head='#7a3a22', wing='#5f5a52', belly='#d8d0c4', bill='#2a2a2a', tail='#3a3a3a', size=0.78, patch='#2f8a5a'),
    'PHEASANT': dict(body='#b5602a', head='#1d5a46', wing='#8a5a2a', belly='#c97a3a', bill='#d8c08a', tail='#9a6a3a', size=1.05, red='#d0302a',
                     longtail=True),
    'GROUSE': dict(body='#8a6a48', head='#7a5a3a', wing='#6a5236', belly='#b89a72', bill='#3a3028', tail='#5a4430', size=0.86, bars=True),
    'DOVE': dict(body='#a8a29a', head='#9a948e', wing='#8a847e', belly='#c8b8b0', bill='#2a2a2a', tail='#7a746e', size=0.70),
    'GOOSE': dict(body='#6a645c', head='#1a1a1a', wing='#57514a', belly='#c8c2b8', bill='#1a1a1a', tail='#1a1a1a', size=1.25, chin='#f0ece4',
                  longneck=True),
    'HEN': dict(body='#a88a62', head='#9a7c56', wing='#8a6e4a', belly='#c4aa84', bill='#c8b490', tail='#8a6a44', size=1.0, longtail=True, bars=True),
    'HAWK': dict(body='#7a5a3a', head='#6a4c30', wing='#6a4c30', belly='#e8dcc8', bill='#e8c33a', tail='#a06a3a', size=1.15, hawk=True),
}


def bird(name, frame):
    """frame 0 wings up, 1 wings down, 2 falling (tumbling, wings loose)"""
    c = BCOL[name]
    img = Image.new('RGBA', (BN, BN), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = BN * 0.42 * c['size']
    cx, cy = BN / 2, BN / 2
    rot = 0.0 if frame < 2 else 0.9

    def T(x, y):
        if rot:
            ca, sa = math.cos(rot), math.sin(rot)
            x, y = x * ca - y * sa, x * sa + y * ca
        return (cx + x * s, cy + y * s)

    def poly(pts, col):
        d.polygon([T(x, y) for x, y in pts], fill=rgba(col))

    # far wing (behind body)
    if frame == 0:
        poly([(-0.10, -0.05), (0.18, -0.06), (0.02, -0.55), (-0.22, -0.78), (-0.24, -0.62)], c['wing'])
    elif frame == 1:
        poly([(-0.10, 0.05), (0.16, 0.05), (0.06, 0.40), (-0.14, 0.62), (-0.20, 0.50)], c['wing'])
    else:
        poly([(-0.10, -0.05), (0.18, -0.06), (0.30, -0.45), (0.10, -0.50)], c['wing'])
    # tail
    if c.get('longtail'):
        poly([(-0.42, -0.02), (-0.95, -0.10), (-0.98, -0.04), (-0.42, 0.08)], c['tail'])
    elif c.get('hawk'):
        poly([(-0.40, -0.06), (-0.75, -0.14), (-0.78, 0.12), (-0.40, 0.08)], c['tail'])
        for k in range(3):
            x = -0.5 - k * 0.09
            d.line([T(x, -0.10), T(x, 0.10)], fill=rgba('#4a3220'), width=max(1, int(s * 0.04)))
    else:
        poly([(-0.40, -0.02), (-0.62, -0.06), (-0.60, 0.07), (-0.40, 0.08)], c['tail'])
    # body
    pts = []
    for i in range(40):
        t = i / 40 * math.tau
        x = -0.45 + 0.85 * (0.5 - 0.5 * math.cos(t))
        y = (0.16 if math.sin(t) > 0 else 0.12) * math.sin(t) + 0.02
        pts.append((x, y))
    poly(pts, c['body'])
    belly = [(x, max(y, 0.03)) for x, y in pts if y > 0.02]
    if len(belly) > 3:
        poly(belly, c['belly'])
    if c.get('bars'):
        for k in range(5):
            x = -0.28 + k * 0.12
            d.line([T(x, -0.08), T(x + 0.04, 0.10)], fill=rgba('#5a4430', 200), width=max(1, int(s * 0.03)))
    # neck and head
    if c.get('longneck'):
        poly([(0.30, -0.04), (0.52, -0.30), (0.62, -0.28), (0.40, 0.06)], c['head'])
        hx, hy, hr = 0.62, -0.32, 0.09
    else:
        poly([(0.26, -0.06), (0.42, -0.14), (0.48, -0.10), (0.36, 0.06)], c['head'])
        hx, hy, hr = 0.46, -0.13, 0.10
    if c.get('hawk'):
        hr = 0.11
    x0, y0 = T(hx - hr, hy - hr)
    x1, y1 = T(hx + hr, hy + hr)
    d.ellipse([min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)], fill=rgba(c['head']))
    if c.get('chin'):
        poly([(hx - 0.04, hy - 0.02), (hx + 0.05, hy + 0.02), (hx - 0.02, hy + 0.08)], c['chin'])
    if c.get('red'):
        poly([(hx - 0.02, hy - 0.06), (hx + 0.06, hy - 0.04), (hx + 0.02, hy + 0.04)], c['red'])
    # bill
    if c.get('hawk'):
        poly([(hx + 0.08, hy - 0.04), (hx + 0.18, hy + 0.02), (hx + 0.08, hy + 0.05)], c['bill'])
    else:
        poly([(hx + 0.07, hy - 0.03), (hx + 0.22, hy + 0.01), (hx + 0.07, hy + 0.04)], c['bill'])
    # eye
    ex, ey = T(hx + 0.03, hy - 0.02)
    er = max(1, s * 0.022)
    d.ellipse([ex - er, ey - er, ex + er, ey + er], fill=rgba('#101010'))
    # near wing
    if frame == 0:
        poly([(-0.06, -0.02), (0.22, -0.03), (0.10, -0.50), (-0.12, -0.70), (-0.16, -0.50)], c['wing'])
        if c.get('patch'):
            poly([(0.0, -0.20), (0.10, -0.22), (0.04, -0.36), (-0.04, -0.34)], c['patch'])
    elif frame == 1:
        poly([(-0.06, 0.06), (0.20, 0.06), (0.12, 0.42), (-0.06, 0.66), (-0.14, 0.52)], c['wing'])
        if c.get('patch'):
            poly([(0.02, 0.18), (0.12, 0.20), (0.08, 0.32), (-0.02, 0.30)], c['patch'])
    else:
        poly([(-0.06, 0.04), (0.22, 0.04), (0.40, 0.40), (0.16, 0.42)], c['wing'])
    return img


def build_flush():
    cell = 96
    sheet = Image.new('RGBA', (cell * 8, cell * 3), (0, 0, 0, 0))
    for i, n in enumerate(BIRDS):
        for fr in range(3):
            sheet.alpha_composite(bird(n, fr).resize((cell, cell), Image.LANCZOS), (i * cell, fr * cell))
    sheet.save(os.path.join(OUT, 'flush.png'))


if __name__ == '__main__' and ('chess' in sys.argv[2:] or not sys.argv[2:]):
    build_chess()
if __name__ == '__main__' and ('flush' in sys.argv[2:] or not sys.argv[2:]):
    build_flush()
