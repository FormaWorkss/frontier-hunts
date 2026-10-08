"""[onboard] Frontier Handbook item icon (16x16 pixel art in the mod's item style: dark outline, banded upper-left light,
noise). python3 tools/onboard/item_icon.py <repo root>"""
import os, sys, random
from PIL import Image
ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
P = {
    'o': (26, 18, 14), 'D': (78, 30, 24), 'C': (112, 44, 32), 'L': (142, 62, 42), 'H': (170, 86, 58),
    'g': (150, 108, 44), 'G': (214, 172, 82), 'Y': (244, 214, 130),
    'p': (228, 216, 184), 'q': (196, 180, 144), 's': (150, 134, 104),
    'r': (52, 108, 62), 'R': (78, 142, 86), 'b': (60, 44, 30),
}
ART = [
    "................",
    "..oooooooooooo..",
    ".oDLHHHHHHHHHLo.",
    ".oDLGGGGGGGGGLoo",
    ".oDLGHHHHHHHGLpo",
    ".oDLGHHHYYYHGLpo",
    ".oDCGHHHHYYHGCpo",
    ".obCGHHHYHYHGCqo",
    ".oDCGHHYHHHHGCpo",
    ".oDCGpYHHHHHGCqo",
    ".oDCGgpHHHHHGCpo",
    ".obCGGGGGGGGGCqo",
    ".oDCCCCCCCrCCCso",
    ".ooooooooorRoooo",
    "..oqpqpqpqrRqso.",
    "...oooooooorooo.",
]
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
rng = random.Random(5)
for y, row in enumerate(ART):
    for x, ch in enumerate(row):
        if ch == '.':
            continue
        r, g, b = P[ch]
        if ch in 'CLHD':  # leather grain
            n = rng.randint(-7, 7)
            r, g, b = max(0, r + n), max(0, g + n // 2), max(0, b + n // 2)
        img.putpixel((x, y), (r, g, b, 255))
out = os.path.join(ROOT, 'patch/assets/frontierhunts/textures/item/frontier_handbook.png')
os.makedirs(os.path.dirname(out), exist_ok=True)
img.save(out)
prev = os.path.join(ROOT, 'docs/ws/onboard')
os.makedirs(prev, exist_ok=True)
bg = Image.new('RGBA', (16 * 16, 16 * 16), (120, 120, 120, 255))
bg.alpha_composite(img.resize((256, 256), Image.NEAREST))
bg.save(os.path.join(prev, 'handbook_icon_16x.png'))
print('wrote', out)
