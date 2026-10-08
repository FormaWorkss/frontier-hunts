#!/usr/bin/env python3
"""[phone] Stand-in trail camera frames for the offline mocks only (the game develops real renders). Not shipped."""
import math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'photos')
JAR_FONT = '/tmp/claude-0/phone-dec/inter_semibold.ttf'


def font(sz):
    try:
        return ImageFont.truetype(JAR_FONT, sz)
    except Exception:
        return ImageFont.load_default()


def deer(d, x, y, s, col, antlers=True, head_down=False):
    body = [(x - 0.55 * s, y - 0.55 * s), (x + 0.45 * s, y - 0.6 * s), (x + 0.55 * s, y - 0.3 * s), (x - 0.5 * s, y - 0.25 * s)]
    d.polygon(body, fill=col)
    for lx in (-0.45, -0.3, 0.32, 0.45):
        d.rectangle([x + lx * s, y - 0.3 * s, x + (lx + 0.07) * s, y + 0.25 * s], fill=col)
    if head_down:
        d.polygon([(x + 0.4 * s, y - 0.55 * s), (x + 0.75 * s, y - 0.1 * s), (x + 0.68 * s, y - 0.0 * s), (x + 0.36 * s, y - 0.4 * s)], fill=col)
    else:
        d.polygon([(x + 0.38 * s, y - 0.58 * s), (x + 0.6 * s, y - 1.05 * s), (x + 0.72 * s, y - 1.0 * s), (x + 0.52 * s, y - 0.5 * s)], fill=col)
        d.polygon([(x + 0.58 * s, y - 1.08 * s), (x + 0.85 * s, y - 0.98 * s), (x + 0.82 * s, y - 0.9 * s), (x + 0.6 * s, y - 0.92 * s)], fill=col)
        if antlers:
            for k in range(4):
                bx = x + (0.6 + k * 0.05) * s
                d.line([(bx, y - 1.06 * s), (bx - 0.05 * s, y - (1.3 + 0.05 * k) * s)], fill=(214, 200, 170), width=max(1, int(s * 0.03)))
            d.line([(x + 0.58 * s, y - 1.06 * s), (x + 0.8 * s, y - 1.24 * s), (x + 0.86 * s, y - 1.38 * s)], fill=(214, 200, 170), width=max(1, int(s * 0.035)))


def scene(seed, ir=False, night=False, subject='buck', label='NORTH RIDGE', when='06:12 AM', temp='38°F 3°C', frame=42):
    rnd = random.Random(seed)
    W, H = 640, 360
    img = Image.new('RGB', (W, H))
    d = ImageDraw.Draw(img)
    if ir:
        for y in range(H):
            v = int(20 + 70 * max(0, (y - 120) / 240))
            d.line([(0, y), (W, y)], fill=(v, v, v))
    else:
        for y in range(H):
            t = y / H
            col = (int(150 + 60 * t), int(170 + 40 * t), int(190 - 40 * t)) if y < 150 else (int(70 + 40 * t), int(90 + 40 * t), int(40 + 20 * t))
            d.line([(0, y), (W, y)], fill=col)
    # trunks
    for i in range(14):
        x = rnd.randint(-20, W)
        w = rnd.randint(10, 34)
        c = rnd.randint(40, 80)
        col = (c, c, c) if ir else (c + 30, c + 15, c)
        d.rectangle([x, 0, x + w, 210 + rnd.randint(-20, 30)], fill=col)
    # canopy
    for i in range(40):
        x, y = rnd.randint(0, W), rnd.randint(-30, 110)
        r = rnd.randint(30, 70)
        c = rnd.randint(30, 60)
        col = (c + 60, c + 60, c + 60) if ir else (c, c + 40, c - 10)
        d.ellipse([x - r, y - r // 2, x + r, y + r // 2], fill=col)
    # ground clutter
    for i in range(300):
        x, y = rnd.randint(0, W), rnd.randint(200, H)
        c = rnd.randint(60, 120)
        col = (c + 40, c + 40, c + 40) if ir else (c - 10, c + 10, c - 40)
        d.rectangle([x, y, x + rnd.randint(2, 6), y + rnd.randint(2, 8)], fill=col)
    if subject in ('buck', 'doe'):
        col = (200, 200, 200) if ir else (118, 86, 58)
        deer(d, 320 + rnd.randint(-120, 80), 265, 120, col, subject == 'buck', head_down=subject == 'doe')
        if ir:
            d.ellipse([440, 145, 450, 155], fill=(255, 255, 255))
    elif subject == 'bear':
        col = (40, 40, 40) if not ir else (120, 120, 120)
        d.ellipse([230, 190, 420, 290], fill=col)
        d.ellipse([390, 190, 450, 240], fill=col)
        for lx in (250, 290, 360, 395):
            d.rectangle([lx, 270, lx + 22, 320], fill=col)
    elif subject == 'player':
        d.rectangle([300, 140, 340, 180], fill=(198, 150, 120))
        d.rectangle([296, 180, 344, 250], fill=(60, 90, 50))
        d.rectangle([300, 250, 340, 320], fill=(60, 60, 80))
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    if ir:
        img = img.convert('L').convert('RGB')
    d = ImageDraw.Draw(img)
    d.rectangle([0, H - 22, W, H], fill=(0, 0, 0))
    d.text((8, H - 19), '%s   %s   10/14/01   %s   #%04d' % (label, temp, when, frame), fill=(235, 235, 235), font=font(15))
    return img


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    specs = [('buck_day', dict(seed=1, subject='buck')), ('doe_day', dict(seed=2, subject='doe', when='07:40 AM', frame=43)),
             ('buck_ir', dict(seed=3, ir=True, subject='buck', when='02:14 AM', temp='31°F -1°C', frame=41)),
             ('bear_day', dict(seed=4, subject='bear', label='CREEK BAIT', when='05:58 PM', frame=17)),
             ('player_day', dict(seed=5, subject='player', label='NORTH RIDGE', when='11:03 AM', frame=40)),
             ('empty_ir', dict(seed=6, ir=True, subject='none', when='11:47 PM', frame=39))]
    for name, kw in specs:
        scene(**kw).save(os.path.join(OUT, name + '.jpg'), quality=88)
    print('ok')
