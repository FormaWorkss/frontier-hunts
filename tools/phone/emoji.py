#!/usr/bin/env python3
"""[1.4.0] The Field Phone's emoji: 32 original pictures on one sheet (8 x 4 cells of 64 px).

python3 tools/phone/emoji.py <repo root>  ->  patch/assets/frontierhunts/textures/gui/phone/emoji.png

The order matches Emoji.NAMES in phone/client/ui/Emoji.java. Everything is drawn at 4x and downsampled.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
OUT = os.path.join(R, 'patch/assets/frontierhunts/textures/gui/phone')
CELL = 64
SS = 4
N = CELL * SS


def C(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def P(x, y):
    return (x * N, y * N)


def box(cx, cy, r):
    return [N * (cx - r), N * (cy - r), N * (cx + r), N * (cy + r)]


def canvas():
    return Image.new('RGBA', (N, N), (0, 0, 0, 0))


def shade(img):
    """a soft drop shadow under the drawing, so it reads on light and dark bubbles"""
    a = img.split()[3].point(lambda v: v * 90 // 255)
    s = Image.new('RGBA', img.size, (0, 0, 0, 0))
    s.putalpha(a)
    s = s.filter(ImageFilter.GaussianBlur(N * 0.02))
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    out.paste(s, (0, int(N * 0.02)), s)
    out.alpha_composite(img)
    return out


def face_base():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse(box(0.5, 0.5, 0.44), fill=C('#d9902a'))
    # a warm gradient: lighter towards the top-left
    for i in range(40):
        t = i / 39
        r = 0.42 - 0.20 * t
        col = (int(0xf2 + (0xff - 0xf2) * t), int(0xb8 + (0xdc - 0xb8) * t), int(0x3a + (0x6a - 0x3a) * t), 255)
        d.ellipse(box(0.5 - 0.06 * t, 0.5 - 0.07 * t, r), fill=col)
    return img, d


def eyes(d, kind='dot', y=0.42):
    ink = C('#4a2e12')
    for x in (0.36, 0.64):
        if kind == 'dot':
            d.ellipse([N * (x - 0.045), N * (y - 0.065), N * (x + 0.045), N * (y + 0.065)], fill=ink)
        elif kind == 'happy':
            d.arc([N * (x - 0.07), N * (y - 0.04), N * (x + 0.07), N * (y + 0.08)], 200, 340, fill=ink, width=int(N * 0.035))
        elif kind == 'closed':
            d.arc([N * (x - 0.07), N * (y - 0.06), N * (x + 0.07), N * (y + 0.04)], 20, 160, fill=ink, width=int(N * 0.03))


def mouth_smile(d, w=0.18, y=0.60, open_=False):
    ink = C('#4a2e12')
    if open_:
        d.chord([N * (0.5 - w), N * (y - 0.08), N * (0.5 + w), N * (y + 0.16)], 0, 180, fill=ink)
        d.chord([N * (0.5 - w * 0.6), N * (y + 0.04), N * (0.5 + w * 0.6), N * (y + 0.16)], 0, 180, fill=C('#e2574b'))
        d.rectangle([N * (0.5 - w * 0.85), N * (y + 0.0), N * (0.5 + w * 0.85), N * (y + 0.035)], fill=C('#ffffff'))
    else:
        d.arc([N * (0.5 - w), N * (y - 0.10), N * (0.5 + w), N * (y + 0.10)], 20, 160, fill=ink, width=int(N * 0.035))


# ------------------------------------------------------------------------------------------------ faces

def e_smile():
    img, d = face_base()
    eyes(d, 'dot')
    mouth_smile(d)
    for x in (0.27, 0.73):
        d.ellipse(box(x, 0.58, 0.06), fill=C('#f08a5a', 110))
    return img


def e_grin():
    img, d = face_base()
    eyes(d, 'happy', 0.40)
    mouth_smile(d, 0.22, 0.58, open_=True)
    return img


def e_joy():
    img, d = face_base()
    eyes(d, 'closed', 0.40)
    mouth_smile(d, 0.22, 0.56, open_=True)
    # tears flying off
    for x, s in ((0.15, -1), (0.85, 1)):
        d.polygon([P(x, 0.40), P(x + s * 0.10, 0.52), P(x - s * 0.02, 0.56)], fill=C('#6ab8f0'))
        d.ellipse([N * (x - 0.06 + s * 0.02), N * 0.47, N * (x + 0.06 + s * 0.02), N * 0.59], fill=C('#6ab8f0'))
    return img


def e_wink():
    img, d = face_base()
    ink = C('#4a2e12')
    d.ellipse([N * 0.315, N * 0.355, N * 0.405, N * 0.485], fill=ink)
    d.arc([N * 0.57, N * 0.36, N * 0.71, N * 0.48], 200, 340, fill=ink, width=int(N * 0.035))
    mouth_smile(d, 0.17, 0.60)
    d.ellipse([N * 0.52, N * 0.66, N * 0.62, N * 0.76], fill=C('#e2574b'))
    return img


def e_cool():
    img, d = face_base()
    # sunglasses
    d.rounded_rectangle([N * 0.18, N * 0.34, N * 0.47, N * 0.50], radius=N * 0.06, fill=C('#1a1a1e'))
    d.rounded_rectangle([N * 0.53, N * 0.34, N * 0.82, N * 0.50], radius=N * 0.06, fill=C('#1a1a1e'))
    d.rectangle([N * 0.45, N * 0.36, N * 0.55, N * 0.39], fill=C('#1a1a1e'))
    d.line([P(0.24, 0.37), P(0.31, 0.37)], fill=C('#8aa0b8'), width=int(N * 0.02))
    d.line([P(0.59, 0.37), P(0.66, 0.37)], fill=C('#8aa0b8'), width=int(N * 0.02))
    d.arc([N * 0.36, N * 0.52, N * 0.66, N * 0.70], 20, 140, fill=C('#4a2e12'), width=int(N * 0.035))
    return img


def heart(d, cx, cy, s, col):
    d.ellipse([N * (cx - s), N * (cy - s * 0.8), N * cx + N * s * 0.08, N * (cy + s * 0.25)], fill=col)
    d.ellipse([N * cx - N * s * 0.08, N * (cy - s * 0.8), N * (cx + s), N * (cy + s * 0.25)], fill=col)
    d.polygon([P(cx - s * 0.96, cy - s * 0.1), P(cx + s * 0.96, cy - s * 0.1), P(cx, cy + s * 0.95)], fill=col)


def e_love():
    img, d = face_base()
    heart(d, 0.35, 0.41, 0.10, C('#e2344b'))
    heart(d, 0.65, 0.41, 0.10, C('#e2344b'))
    mouth_smile(d, 0.18, 0.60, open_=True)
    return img


def e_wow():
    img, d = face_base()
    ink = C('#4a2e12')
    for x in (0.36, 0.64):
        d.ellipse(box(x, 0.40, 0.07), fill=C('#ffffff'))
        d.ellipse(box(x, 0.41, 0.04), fill=ink)
    d.ellipse([N * 0.42, N * 0.56, N * 0.58, N * 0.76], fill=ink)
    d.arc([N * 0.26, N * 0.24, N * 0.42, N * 0.32], 200, 340, fill=ink, width=int(N * 0.025))
    d.arc([N * 0.58, N * 0.24, N * 0.74, N * 0.32], 200, 340, fill=ink, width=int(N * 0.025))
    return img


def e_sad():
    img, d = face_base()
    eyes(d, 'dot', 0.44)
    d.arc([N * 0.36, N * 0.62, N * 0.64, N * 0.80], 200, 340, fill=C('#4a2e12'), width=int(N * 0.035))
    d.ellipse([N * 0.62, N * 0.50, N * 0.70, N * 0.62], fill=C('#6ab8f0'))
    d.polygon([P(0.66, 0.47), P(0.62, 0.54), P(0.70, 0.54)], fill=C('#6ab8f0'))
    return img


def e_angry():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse(box(0.5, 0.5, 0.44), fill=C('#c73a22'))
    for i in range(30):
        t = i / 29
        d.ellipse(box(0.5 - 0.05 * t, 0.5 - 0.06 * t, 0.42 - 0.2 * t), fill=(int(0xe0 + 0x1a * t), int(0x5a + 0x40 * t), int(0x2a + 0x20 * t), 255))
    ink = C('#3a1408')
    d.line([P(0.24, 0.32), P(0.43, 0.40)], fill=ink, width=int(N * 0.045))
    d.line([P(0.76, 0.32), P(0.57, 0.40)], fill=ink, width=int(N * 0.045))
    for x in (0.36, 0.64):
        d.ellipse(box(x, 0.47, 0.045), fill=ink)
    d.arc([N * 0.36, N * 0.62, N * 0.64, N * 0.78], 200, 340, fill=ink, width=int(N * 0.04))
    return img


def e_think():
    img, d = face_base()
    ink = C('#4a2e12')
    d.ellipse(box(0.36, 0.40, 0.045), fill=ink)
    d.ellipse(box(0.64, 0.38, 0.045), fill=ink)
    d.line([P(0.56, 0.27), P(0.72, 0.25)], fill=ink, width=int(N * 0.03))
    d.line([P(0.40, 0.62), P(0.60, 0.58)], fill=ink, width=int(N * 0.035))
    # a hand at the chin
    d.ellipse([N * 0.30, N * 0.66, N * 0.52, N * 0.88], fill=C('#f2b84a'))
    d.rounded_rectangle([N * 0.42, N * 0.58, N * 0.50, N * 0.74], radius=N * 0.04, fill=C('#f2b84a'))
    d.arc([N * 0.30, N * 0.66, N * 0.52, N * 0.88], 180, 360, fill=C('#c87a20'), width=int(N * 0.015))
    return img


def e_sleepy():
    img, d = face_base()
    eyes(d, 'closed', 0.44)
    d.ellipse([N * 0.44, N * 0.62, N * 0.56, N * 0.72], fill=C('#4a2e12'))
    ink = C('#5a7ab0')
    for (x, y, s) in ((0.66, 0.24, 0.07), (0.78, 0.12, 0.05)):
        d.line([P(x, y), P(x + s, y), P(x, y + s), P(x + s, y + s)], fill=ink, width=int(N * 0.025))
    return img


def e_party():
    img, d = face_base()
    eyes(d, 'happy', 0.44)
    mouth_smile(d, 0.16, 0.62)
    # party hat
    d.polygon([P(0.24, 0.24), P(0.50, 0.15), P(0.28, 0.04)], fill=C('#7a5ae0'))
    d.line([P(0.27, 0.17), P(0.42, 0.13)], fill=C('#f0c64e'), width=int(N * 0.025))
    d.ellipse(box(0.28, 0.05, 0.035), fill=C('#f0c64e'))
    # confetti
    for (x, y, c) in ((0.80, 0.22, '#3fb0e0'), (0.86, 0.40, '#e2574b'), (0.14, 0.62, '#8cc071'), (0.84, 0.70, '#f0c64e')):
        d.rectangle([N * x, N * y, N * (x + 0.04), N * (y + 0.06)], fill=C(c))
    return img


# ------------------------------------------------------------------------------------------------ hands

SKIN = '#f2b84a'
SKIN_D = '#c87a20'


def e_thumbs():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([N * 0.26, N * 0.44, N * 0.70, N * 0.88], radius=N * 0.10, fill=C(SKIN))
    d.rounded_rectangle([N * 0.38, N * 0.10, N * 0.56, N * 0.54], radius=N * 0.09, fill=C(SKIN))
    for y in (0.56, 0.66, 0.76):
        d.line([P(0.50, y), P(0.70, y)], fill=C(SKIN_D), width=int(N * 0.02))
    d.rounded_rectangle([N * 0.14, N * 0.46, N * 0.27, N * 0.90], radius=N * 0.04, fill=C('#3a6ab0'))
    return img


def e_clap():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([N * 0.20, N * 0.22, N * 0.56, N * 0.84], radius=N * 0.14, fill=C(SKIN_D))
    d.rounded_rectangle([N * 0.40, N * 0.18, N * 0.78, N * 0.82], radius=N * 0.14, fill=C(SKIN))
    for (x, y) in ((0.16, 0.14), (0.86, 0.18), (0.10, 0.40)):
        d.line([P(x, y), P(x + (0.06 if x < 0.5 else -0.06), y + 0.07)], fill=C('#f0c64e'), width=int(N * 0.03))
    return img


def e_muscle():
    img = canvas()
    d = ImageDraw.Draw(img)
    # forearm up, bicep bulge
    d.rounded_rectangle([N * 0.18, N * 0.56, N * 0.70, N * 0.80], radius=N * 0.12, fill=C(SKIN))
    d.ellipse([N * 0.30, N * 0.40, N * 0.70, N * 0.78], fill=C(SKIN))
    d.rounded_rectangle([N * 0.56, N * 0.16, N * 0.80, N * 0.66], radius=N * 0.12, fill=C(SKIN))
    d.ellipse([N * 0.56, N * 0.08, N * 0.84, N * 0.30], fill=C(SKIN))
    d.arc([N * 0.34, N * 0.44, N * 0.62, N * 0.70], 200, 300, fill=C(SKIN_D), width=int(N * 0.025))
    return img


def e_wave():
    img = canvas()
    d = ImageDraw.Draw(img)
    base = canvas()
    bd = ImageDraw.Draw(base)
    bd.rounded_rectangle([N * 0.30, N * 0.44, N * 0.70, N * 0.90], radius=N * 0.14, fill=C(SKIN))
    for i, x in enumerate((0.31, 0.41, 0.51, 0.61)):
        top = 0.16 if i in (1, 2) else 0.22
        bd.rounded_rectangle([N * x, N * top, N * (x + 0.09), N * 0.56], radius=N * 0.045, fill=C(SKIN))
    bd.rounded_rectangle([N * 0.16, N * 0.50, N * 0.34, N * 0.64], radius=N * 0.06, fill=C(SKIN))
    img.alpha_composite(base.rotate(-18, resample=Image.BICUBIC, center=(N * 0.5, N * 0.7)))
    for (x0, y0, x1, y1) in ((0.80, 0.20, 0.88, 0.30), (0.84, 0.36, 0.94, 0.40), (0.12, 0.20, 0.06, 0.30)):
        d.line([P(x0, y0), P(x1, y1)], fill=C('#8aa0b8'), width=int(N * 0.025))
    return img


# ------------------------------------------------------------------------------------------------ symbols

def e_fire():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([P(0.50, 0.06), P(0.74, 0.40), P(0.82, 0.66), P(0.68, 0.88), P(0.32, 0.88), P(0.18, 0.64), P(0.28, 0.40), P(0.38, 0.48)],
              fill=C('#e2441e'))
    d.ellipse([N * 0.18, N * 0.44, N * 0.82, N * 0.92], fill=C('#e2441e'))
    d.polygon([P(0.50, 0.34), P(0.66, 0.60), P(0.62, 0.84), P(0.38, 0.84), P(0.34, 0.62)], fill=C('#f59a1e'))
    d.ellipse([N * 0.36, N * 0.60, N * 0.64, N * 0.88], fill=C('#ffd84a'))
    return img


def e_heart():
    img = canvas()
    d = ImageDraw.Draw(img)
    heart(d, 0.5, 0.48, 0.38, C('#e2344b'))
    d.ellipse([N * 0.22, N * 0.24, N * 0.36, N * 0.36], fill=C('#ff8a96'))
    return img


def e_hundred():
    img = canvas()
    d = ImageDraw.Draw(img)
    red = C('#e2344b')
    w = int(N * 0.07)
    d.line([P(0.16, 0.24), P(0.16, 0.66)], fill=red, width=w)
    for cx in (0.42, 0.72):
        d.ellipse([N * (cx - 0.13), N * 0.22, N * (cx + 0.13), N * 0.68], outline=red, width=w)
    d.line([P(0.10, 0.80), P(0.90, 0.76)], fill=red, width=int(N * 0.05))
    d.line([P(0.14, 0.90), P(0.86, 0.86)], fill=red, width=int(N * 0.04))
    return img


def e_target():
    img = canvas()
    d = ImageDraw.Draw(img)
    for r, c in ((0.42, '#f4efe2'), (0.34, '#e2344b'), (0.26, '#f4efe2'), (0.18, '#e2344b'), (0.09, '#f4efe2')):
        d.ellipse(box(0.5, 0.5, r), fill=C(c))
    # an arrow in the gold
    d.line([P(0.52, 0.48), P(0.92, 0.08)], fill=C('#5a3a1e'), width=int(N * 0.035))
    d.polygon([P(0.82, 0.06), P(0.96, 0.04), P(0.94, 0.18)], fill=C('#ff7a1f'))
    return img


def e_trophy():
    img = canvas()
    d = ImageDraw.Draw(img)
    gold, dk = C('#f0b83a'), C('#c08a1a')
    d.chord([N * 0.24, N * -0.06, N * 0.76, N * 0.62], 0, 180, fill=gold)
    d.rectangle([N * 0.24, N * 0.12, N * 0.76, N * 0.28], fill=gold)
    for x0 in (0.10, 0.70):
        d.arc([N * x0, N * 0.14, N * (x0 + 0.20), N * 0.42], 90 if x0 < 0.5 else -90, 270 if x0 < 0.5 else 90, fill=dk, width=int(N * 0.04))
    d.rectangle([N * 0.45, N * 0.58, N * 0.55, N * 0.74], fill=dk)
    d.rounded_rectangle([N * 0.30, N * 0.74, N * 0.70, N * 0.88], radius=N * 0.03, fill=C('#6a4a2a'))
    d.polygon([P(0.5, 0.20), P(0.54, 0.30), P(0.64, 0.30), P(0.56, 0.36), P(0.59, 0.46), P(0.5, 0.40), P(0.41, 0.46), P(0.44, 0.36), P(0.36, 0.30),
               P(0.46, 0.30)], fill=C('#fff3c0'))
    return img


def e_camera():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([N * 0.34, N * 0.20, N * 0.56, N * 0.32], radius=N * 0.03, fill=C('#3a3a40'))
    d.rounded_rectangle([N * 0.10, N * 0.28, N * 0.90, N * 0.80], radius=N * 0.10, fill=C('#4a4a52'))
    d.ellipse(box(0.5, 0.54, 0.21), fill=C('#1e1e22'))
    d.ellipse(box(0.5, 0.54, 0.14), fill=C('#3a6a9a'))
    d.ellipse(box(0.46, 0.50, 0.05), fill=C('#cfe8ff'))
    d.rounded_rectangle([N * 0.70, N * 0.34, N * 0.82, N * 0.40], radius=N * 0.02, fill=C('#ffd84a'))
    return img


def e_moon():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse(box(0.5, 0.5, 0.40), fill=C('#f4e2a0'))
    d.ellipse(box(0.66, 0.38, 0.34), fill=(0, 0, 0, 0))
    m = canvas()
    ImageDraw.Draw(m).ellipse(box(0.5, 0.5, 0.40), fill=C('#f4e2a0'))
    cut = canvas()
    ImageDraw.Draw(cut).ellipse(box(0.68, 0.36, 0.34), fill=(255, 255, 255, 255))
    a = m.split()[3]
    a = Image.composite(Image.new('L', (N, N), 0), a, cut.split()[3])
    m.putalpha(a)
    for (x, y, r) in ((0.30, 0.56, 0.05), (0.40, 0.74, 0.035)):
        ImageDraw.Draw(m).ellipse(box(x, y, r), fill=C('#d8c27a'))
    m.putalpha(Image.composite(Image.new('L', (N, N), 0), m.split()[3], cut.split()[3]))
    return m


def e_sun():
    img = canvas()
    d = ImageDraw.Draw(img)
    for i in range(12):
        a = i * math.pi / 6
        d.line([P(0.5 + 0.30 * math.cos(a), 0.5 + 0.30 * math.sin(a)), P(0.5 + 0.44 * math.cos(a), 0.5 + 0.44 * math.sin(a))], fill=C('#f59a1e'),
               width=int(N * 0.05))
    d.ellipse(box(0.5, 0.5, 0.26), fill=C('#ffc83a'))
    d.ellipse(box(0.44, 0.44, 0.10), fill=C('#ffe08a'))
    return img


# ------------------------------------------------------------------------------------------------ outdoors

def e_deer():
    img = canvas()
    d = ImageDraw.Draw(img)
    fur, dk = C('#a8703a'), C('#6a4420')
    # antlers
    for s in (-1, 1):
        x0 = 0.5 + s * 0.12
        d.line([P(x0, 0.30), P(x0 + s * 0.16, 0.06)], fill=C('#e8dcc0'), width=int(N * 0.035))
        d.line([P(x0 + s * 0.08, 0.18), P(x0 + s * 0.02, 0.04)], fill=C('#e8dcc0'), width=int(N * 0.03))
        d.line([P(x0 + s * 0.13, 0.11), P(x0 + s * 0.26, 0.10)], fill=C('#e8dcc0'), width=int(N * 0.03))
        # ears
        d.polygon([P(0.5 + s * 0.14, 0.36), P(0.5 + s * 0.40, 0.30), P(0.5 + s * 0.20, 0.46)], fill=fur)
    # head and muzzle
    d.ellipse([N * 0.30, N * 0.28, N * 0.70, N * 0.70], fill=fur)
    d.polygon([P(0.34, 0.56), P(0.66, 0.56), P(0.60, 0.88), P(0.40, 0.88)], fill=fur)
    d.ellipse([N * 0.38, N * 0.74, N * 0.62, N * 0.94], fill=C('#d8b088'))
    d.ellipse([N * 0.44, N * 0.78, N * 0.56, N * 0.86], fill=C('#2a1a10'))
    for x in (0.40, 0.60):
        d.ellipse(box(x, 0.50, 0.035), fill=C('#1a1008'))
    d.polygon([P(0.46, 0.34), P(0.54, 0.34), P(0.5, 0.42)], fill=C('#f4efe2'))
    return img


def e_antlers():
    img = canvas()
    d = ImageDraw.Draw(img)
    bone = C('#efe4c8')
    w = int(N * 0.05)
    for s in (-1, 1):
        base = (0.5 + s * 0.08, 0.86)
        pts = [P(*base), P(0.5 + s * 0.22, 0.62), P(0.5 + s * 0.36, 0.40), P(0.5 + s * 0.40, 0.12)]
        d.line(pts, fill=bone, width=w, joint='curve')
        for (fx, fy, tx, ty) in ((0.22, 0.62, 0.10, 0.46), (0.31, 0.48, 0.18, 0.28), (0.37, 0.30, 0.28, 0.08)):
            d.line([P(0.5 + s * fx, fy), P(0.5 + s * tx, ty)], fill=bone, width=int(N * 0.04))
        d.line([P(0.5 + s * 0.16, 0.72), P(0.5 + s * 0.04, 0.62)], fill=bone, width=int(N * 0.035))
    d.ellipse(box(0.5, 0.88, 0.10), fill=C('#8a6a4a'))
    return img


def e_track():
    img = canvas()
    d = ImageDraw.Draw(img)
    ink = C('#6a4426')
    # a paw print: the big pad and four toes
    d.ellipse([N * 0.28, N * 0.46, N * 0.72, N * 0.86], fill=ink)
    d.ellipse([N * 0.22, N * 0.58, N * 0.42, N * 0.84], fill=ink)
    d.ellipse([N * 0.58, N * 0.58, N * 0.78, N * 0.84], fill=ink)
    for (x, y) in ((0.18, 0.36), (0.38, 0.20), (0.62, 0.20), (0.82, 0.36)):
        d.ellipse([N * (x - 0.09), N * (y - 0.11), N * (x + 0.09), N * (y + 0.11)], fill=ink)
    return img


def e_duck():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse([N * 0.14, N * 0.48, N * 0.78, N * 0.86], fill=C('#8a6a4a'))
    d.polygon([P(0.14, 0.62), P(0.04, 0.52), P(0.18, 0.56)], fill=C('#5a4430'))
    d.ellipse([N * 0.48, N * 0.18, N * 0.80, N * 0.50], fill=C('#2a7a4a'))
    d.rectangle([N * 0.56, N * 0.44, N * 0.72, N * 0.56], fill=C('#2a7a4a'))
    d.line([P(0.54, 0.52), P(0.74, 0.52)], fill=C('#f4efe2'), width=int(N * 0.03))
    d.polygon([P(0.78, 0.30), P(0.96, 0.36), P(0.78, 0.40)], fill=C('#f0b83a'))
    d.ellipse(box(0.68, 0.30, 0.03), fill=C('#101010'))
    d.arc([N * 0.26, N * 0.54, N * 0.62, N * 0.80], 180, 330, fill=C('#5a4430'), width=int(N * 0.025))
    return img


def e_fish():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.ellipse([N * 0.12, N * 0.30, N * 0.74, N * 0.70], fill=C('#5a9a6a'))
    d.polygon([P(0.70, 0.50), P(0.94, 0.28), P(0.94, 0.72)], fill=C('#4a8a5a'))
    d.ellipse([N * 0.16, N * 0.44, N * 0.70, N * 0.66], fill=C('#c8d8a0'))
    for x in (0.34, 0.44, 0.54):
        d.ellipse(box(x, 0.40, 0.02), fill=C('#2a4a2a'))
    d.line([P(0.30, 0.50), P(0.62, 0.50)], fill=C('#e26a7a'), width=int(N * 0.025))
    d.ellipse(box(0.24, 0.44, 0.035), fill=C('#101010'))
    return img


def e_bear():
    img = canvas()
    d = ImageDraw.Draw(img)
    fur = C('#5a3a22')
    for x in (0.26, 0.74):
        d.ellipse(box(x, 0.24, 0.12), fill=fur)
        d.ellipse(box(x, 0.24, 0.06), fill=C('#8a6040'))
    d.ellipse([N * 0.14, N * 0.18, N * 0.86, N * 0.86], fill=fur)
    d.ellipse([N * 0.34, N * 0.52, N * 0.66, N * 0.82], fill=C('#b08860'))
    d.ellipse([N * 0.43, N * 0.56, N * 0.57, N * 0.66], fill=C('#1a1008'))
    for x in (0.36, 0.64):
        d.ellipse(box(x, 0.44, 0.04), fill=C('#1a1008'))
    return img


def e_tent():
    img = canvas()
    d = ImageDraw.Draw(img)
    d.polygon([P(0.5, 0.12), P(0.92, 0.84), P(0.08, 0.84)], fill=C('#e8742a'))
    d.polygon([P(0.5, 0.12), P(0.92, 0.84), P(0.62, 0.84)], fill=C('#c4561a'))
    d.polygon([P(0.5, 0.40), P(0.64, 0.84), P(0.36, 0.84)], fill=C('#3a2a1a'))
    d.line([P(0.04, 0.86), P(0.96, 0.86)], fill=C('#5a7a3a'), width=int(N * 0.04))
    return img


def e_campfire():
    img = canvas()
    d = ImageDraw.Draw(img)
    for (a, b) in (((0.16, 0.86), (0.84, 0.70)), ((0.16, 0.70), (0.84, 0.86))):
        d.line([P(*a), P(*b)], fill=C('#7a4a22'), width=int(N * 0.08))
    d.polygon([P(0.50, 0.08), P(0.70, 0.40), P(0.74, 0.64), P(0.60, 0.76), P(0.40, 0.76), P(0.26, 0.62), P(0.32, 0.38), P(0.42, 0.44)],
              fill=C('#e2441e'))
    d.polygon([P(0.50, 0.32), P(0.62, 0.54), P(0.58, 0.74), P(0.42, 0.74), P(0.38, 0.56)], fill=C('#f59a1e'))
    d.ellipse([N * 0.42, N * 0.56, N * 0.58, N * 0.74], fill=C('#ffd84a'))
    return img


EMOJI = [e_smile, e_grin, e_joy, e_wink, e_cool, e_love, e_wow, e_sad,
         e_angry, e_think, e_sleepy, e_party, e_thumbs, e_clap, e_muscle, e_wave,
         e_fire, e_heart, e_hundred, e_target, e_trophy, e_camera, e_moon, e_sun,
         e_deer, e_antlers, e_track, e_duck, e_fish, e_bear, e_tent, e_campfire]


def build():
    sheet = Image.new('RGBA', (CELL * 8, CELL * 4), (0, 0, 0, 0))
    for i, fn in enumerate(EMOJI):
        img = shade(fn()).resize((CELL, CELL), Image.LANCZOS)
        sheet.alpha_composite(img, ((i % 8) * CELL, (i // 8) * CELL))
    os.makedirs(OUT, exist_ok=True)
    sheet.save(os.path.join(OUT, 'emoji.png'))
    big = sheet.resize((CELL * 8 * 2, CELL * 4 * 2), Image.NEAREST)
    prev = Image.new('RGBA', big.size, (30, 36, 32, 255))
    prev.alpha_composite(big)
    prev.save(os.path.join(R, 'tools/phone/mocks/emoji_preview.png'))
    print('emoji written', len(EMOJI))


if __name__ == '__main__':
    build()
