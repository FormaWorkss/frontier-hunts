#!/usr/bin/env python3
"""[onboard2] Frontier Handbook sidebar icon for the Assignments tab: a ranger dossier (leather clipboard, cream sheet
with ticked lines, the Ranger shield badge clipped to it) in the academy icon style (flat cream/gold, dark outline).
python3 tools/onboard2/tab_icon.py <repo>  ->  patch/assets/frontierhunts/textures/gui/handbook/assignments_tab.png (64x64)"""
import os, sys
from PIL import Image, ImageDraw

R = sys.argv[1] if len(sys.argv) > 1 else '.'
S = 8  # supersample
N = 64 * S
OUT = os.path.join(R, 'patch/assets/frontierhunts/textures/gui/handbook/assignments_tab.png')
INK = (38, 28, 22, 255)
CREAM = (240, 230, 206, 255)
CREAM_D = (214, 200, 168, 255)
LEATHER = (150, 96, 58, 255)
LEATHER_D = (112, 70, 42, 255)
GOLD = (214, 178, 102, 255)
GOLD_D = (160, 124, 58, 255)
GREEN = (62, 110, 64, 255)
RED = (190, 72, 52, 255)


def p(v):
    return int(round(v * S))


im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
o = p(2.2)  # outline width
# clipboard board (leather), slightly rotated look via offset shadow
d.rounded_rectangle([p(9), p(8), p(49), p(60)], radius=p(5), fill=INK)
d.rounded_rectangle([p(9) + o, p(8) + o, p(49) - o, p(60) - o], radius=p(4), fill=LEATHER)
d.rounded_rectangle([p(11), p(52), p(47), p(58)], radius=p(3), fill=LEATHER_D)
# sheet
d.rectangle([p(13), p(13), p(45), p(55)], fill=INK)
d.rectangle([p(13) + o, p(13) + o, p(45) - o, p(55) - o], fill=CREAM)
d.rectangle([p(13) + o, p(49), p(45) - o, p(55) - o], fill=CREAM_D)
# task lines with ticks
for i, (y, done) in enumerate(((24, True), (33, True), (42, False))):
    bx = 17
    d.rectangle([p(bx), p(y - 3), p(bx + 6), p(y + 3)], fill=INK)
    d.rectangle([p(bx) + p(1.2), p(y - 3) + p(1.2), p(bx + 6) - p(1.2), p(y + 3) - p(1.2)], fill=CREAM)
    if done:
        d.line([(p(bx + 1.2), p(y)), (p(bx + 3), p(y + 2.2)), (p(bx + 7.5), p(y - 4))], fill=GREEN, width=p(2.0), joint='curve')
    d.rounded_rectangle([p(bx + 9), p(y - 1.2), p(bx + (22 if i != 1 else 18)), p(y + 1.2)], radius=p(1), fill=(120, 108, 90, 255))
# clip (brass)
d.rounded_rectangle([p(21), p(4), p(37), p(14)], radius=p(3), fill=INK)
d.rounded_rectangle([p(21) + o, p(4) + o, p(37) - o, p(14) - o], radius=p(2), fill=GOLD)
d.ellipse([p(27), p(5.5), p(31), p(9.5)], fill=INK)
d.rectangle([p(23), p(11), p(35), p(12.2)], fill=GOLD_D)
# ranger shield badge, lower right, overlapping the board edge
cx, cy = 46, 44
shield = [(cx - 12, cy - 13), (cx + 12, cy - 13), (cx + 12, cy + 1), (cx, cy + 15), (cx - 12, cy + 1)]
d.polygon([(p(x), p(y)) for x, y in shield], fill=INK)
inner = [(cx - 9.6, cy - 10.6), (cx + 9.6, cy - 10.6), (cx + 9.6, cy + 0.2), (cx, cy + 11.8), (cx - 9.6, cy + 0.2)]
d.polygon([(p(x), p(y)) for x, y in inner], fill=GOLD)
inner2 = [(cx - 7, cy - 8), (cx + 7, cy - 8), (cx + 7, cy - 0.6), (cx, cy + 8.4), (cx - 7, cy - 0.6)]
d.polygon([(p(x), p(y)) for x, y in inner2], fill=GREEN)
# star on the badge
import math
pts = []
for k in range(10):
    r = 5.2 if k % 2 == 0 else 2.2
    a = -math.pi / 2 + k * math.pi / 5
    pts.append((p(cx + r * math.cos(a)), p(cy - 1.5 + r * math.sin(a))))
d.polygon(pts, fill=CREAM)
im = im.resize((64, 64), Image.LANCZOS)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
print('wrote', OUT)
