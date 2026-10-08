#!/usr/bin/env python3
"""[gear21] Hound Lead icon: a redbone hound's head in profile with a red collar and the leather lead running down to its
hand loop - so it reads as "your tracking dog", not a coil of rope. 32 x 32 pixel art in the style of the mod's other
item icons (dark outline, top-left light)."""
import sys
from PIL import Image, ImageDraw

out = sys.argv[1] if len(sys.argv) > 1 else 'patch/assets/frontierhunts/textures/item/hound_lead.png'
S = 32
im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
COAT, COAT_L, COAT_D = (176, 96, 52, 255), (204, 128, 76, 255), (128, 64, 34, 255)
MUZ = (206, 150, 102, 255)
EAR, EAR_D = (112, 56, 28, 255), (84, 40, 20, 255)
NOSE = (36, 24, 20, 255)
COLLAR, COLLAR_D = (186, 40, 34, 255), (128, 26, 24, 255)
BRASS = (226, 186, 92, 255)
LEASH, LEASH_D = (120, 78, 40, 255), (86, 54, 28, 255)
# neck and head
d.polygon([(14, 15), (23, 12), (27, 25), (15, 25)], fill=COAT)
d.ellipse((10, 4, 24, 17), fill=COAT)
# muzzle and nose
d.polygon([(4, 11), (11, 9), (14, 11), (14, 17), (8, 18), (4, 16)], fill=MUZ)
d.rectangle((3, 11, 5, 13), fill=NOSE)
d.point((4, 11), fill=(90, 70, 60, 255))
d.line([(6, 16), (11, 16)], fill=COAT_D)  # mouth line
# long ear hanging down the side of the head
d.polygon([(16, 7), (21, 6), (23, 10), (23, 19), (21, 22), (18, 21), (17, 15)], fill=EAR)
d.line([(22, 11), (22, 19)], fill=EAR_D)
d.line([(21, 20), (19, 21)], fill=EAR_D)
# eye (brow above)
d.rectangle((12, 10, 13, 11), fill=(24, 16, 12, 255))
d.point((12, 10), fill=(235, 225, 210, 255))
d.line([(11, 8), (14, 8)], fill=COAT_D)
# collar with its brass ring
d.polygon([(14, 20), (24, 16), (25, 19), (15, 23)], fill=COLLAR)
d.line([(15, 23), (25, 19)], fill=COLLAR_D)
d.ellipse((23, 19, 26, 22), outline=BRASS)
# the lead: down and right to a hand loop
d.line([(25, 22), (26, 25), (27, 27)], fill=LEASH, width=2)
d.ellipse((25, 25, 31, 31), outline=LEASH, width=2)
d.point((28, 29), fill=(0, 0, 0, 0))
# light from the top left: highlight the crown and the top of the muzzle
for x, y in [(15, 6), (16, 6), (17, 5), (18, 5), (14, 7), (6, 10), (7, 10), (8, 10), (9, 9)]:
    if im.getpixel((x, y))[3]:
        d.point((x, y), fill=COAT_L if x > 10 else (226, 176, 128, 255))
for x, y in [(16, 24), (18, 24), (20, 24), (22, 24), (24, 24), (26, 24)]:
    if im.getpixel((x, y))[3]:
        d.point((x, y), fill=COAT_D)
# dark outline around everything
px = im.load()
edge = []
for y in range(S):
    for x in range(S):
        if px[x, y][3] == 0:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < S and 0 <= yy < S and px[xx, yy][3] and px[xx, yy] != (42, 26, 16, 255):
                    edge.append((x, y))
                    break
for x, y in edge:
    px[x, y] = (42, 26, 16, 255)
im.save(out)
print(out)
