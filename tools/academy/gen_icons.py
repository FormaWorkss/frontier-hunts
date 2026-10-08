"""[academy] 512x512 icon atlas (8x8 cells of 64 px): bold original pictograms in warm cream with a dark ink outline,
drawn 4x supersampled with PIL. Order = DossierArt.Icon."""
import os, sys, math
from PIL import Image, ImageDraw, ImageFilter
ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/academy')
os.makedirs(OUT, exist_ok=True)
NAMES = ['glassing', 'stalk', 'range', 'tracking', 'dressing', 'harvest', 'track', 'timed', 'clean', 'trophy', 'supply', 'check', 'clock', 'lock', 'medal', 'wind',
         'flag', 'cross', 'academy', 'token', 'star', 'alert', 'book', 'leave', 'archery']
K = 4; C = 64 * K
CREAM = (244, 232, 204, 255); GOLD = (216, 180, 106, 255); RED = (196, 70, 52, 255); GREEN = (120, 170, 100, 255); BLUE = (120, 170, 214, 255); INK = (32, 24, 16, 255)

def cell():
    return Image.new('RGBA', (C, C), (0, 0, 0, 0))

def S(v): return v * K

def finish(im):
    # dark outline under the shape for contrast on leather and paper
    a = im.split()[3]
    o = a.filter(ImageFilter.MaxFilter(S(1) * 2 + 1))
    base = Image.new('RGBA', im.size, INK); base.putalpha(o)
    base.alpha_composite(im)
    return base.resize((64, 64), Image.LANCZOS)

def deer_head(d, cx, cy, s, col):
    d.ellipse([cx - S(6) * s, cy - S(4) * s, cx + S(6) * s, cy + S(10) * s], fill=col)
    d.polygon([(cx - S(4) * s, cy + S(6) * s), (cx + S(4) * s, cy + S(6) * s), (cx, cy + S(18) * s)], fill=col)
    for sx in (-1, 1):
        d.ellipse([cx + sx * S(7) * s - S(4) * s, cy - S(2) * s, cx + sx * S(7) * s + S(4) * s, cy + S(3) * s], fill=col)
        pts = [(cx + sx * S(4) * s, cy - S(3) * s), (cx + sx * S(9) * s, cy - S(14) * s), (cx + sx * S(14) * s, cy - S(22) * s)]
        d.line(pts, fill=col, width=int(S(2.6) * s))
        d.line([(cx + sx * S(8.5) * s, cy - S(12) * s), (cx + sx * S(4) * s, cy - S(20) * s)], fill=col, width=int(S(2.4) * s))
        d.line([(cx + sx * S(12) * s, cy - S(18) * s), (cx + sx * S(19) * s, cy - S(20) * s)], fill=col, width=int(S(2.2) * s))

def draw(name):
    im = cell(); d = ImageDraw.Draw(im); m = S(32)
    W2 = int(S(4))
    if name == 'glassing':
        for sx in (-1, 1):
            d.ellipse([m + sx * S(11) - S(9), S(22), m + sx * S(11) + S(9), S(44)], fill=CREAM)
            d.ellipse([m + sx * S(11) - S(5), S(28), m + sx * S(11) + S(5), S(38)], fill=BLUE)
        d.rectangle([m - S(4), S(26), m + S(4), S(36)], fill=CREAM)
    elif name == 'stalk':
        d.polygon([(S(10), S(52)), (S(30), S(30)), (S(38), S(22)), (S(46), S(26)), (S(40), S(36)), (S(50), S(52))], fill=CREAM)
        d.ellipse([S(36), S(12), S(48), S(24)], fill=CREAM)
        for i in range(3): d.line([(S(8), S(14 + i * 7)), (S(26), S(14 + i * 7))], fill=BLUE, width=W2 // 2 + 2)
    elif name in ('range', 'clean'):
        for r, col in ((24, CREAM), (17, RED), (10, CREAM), (4, RED)):
            d.ellipse([m - S(r), m - S(r), m + S(r), m + S(r)], fill=col)
        if name == 'clean':
            d.line([(m, S(2)), (m, S(62))], fill=CREAM, width=W2 // 2 + 2); d.line([(S(2), m), (S(62), m)], fill=CREAM, width=W2 // 2 + 2)
    elif name == 'tracking':
        for i, (x, y) in enumerate(((14, 50), (26, 40), (36, 30), (48, 18))):
            d.ellipse([S(x) - S(5), S(y) - S(4), S(x) + S(5), S(y) + S(4)], fill=RED if i % 2 == 0 else (150, 30, 30, 255))
    elif name == 'dressing':
        d.polygon([(S(12), S(46)), (S(46), S(12)), (S(52), S(14)), (S(22), S(48))], fill=(220, 224, 228, 255))
        d.polygon([(S(10), S(48)), (S(20), S(50)), (S(14), S(58)), (S(6), S(56))], fill=(150, 100, 60, 255))
    elif name == 'harvest':
        deer_head(d, m, S(30), 1.0, CREAM)
    elif name == 'track':
        for (x, y, a) in ((22, 44, 0), (40, 22, 0)):
            d.ellipse([S(x - 7), S(y - 9), S(x - 1), S(y + 8)], fill=CREAM); d.ellipse([S(x + 1), S(y - 9), S(x + 7), S(y + 8)], fill=CREAM)
    elif name == 'timed':
        d.pieslice([S(8), S(26), S(56), S(74)], 180, 360, fill=GOLD)
        d.rectangle([S(4), S(50), S(60), S(54)], fill=CREAM)
        for a in range(200, 341, 35):
            r = math.radians(a); d.line([(m + math.cos(r) * S(28), S(50) + math.sin(r) * S(28)), (m + math.cos(r) * S(36), S(50) + math.sin(r) * S(36))], fill=GOLD, width=W2)
    elif name == 'trophy':
        deer_head(d, m, S(32), 1.15, GOLD)
    elif name == 'supply':
        d.rectangle([S(10), S(24), S(54), S(54)], fill=(196, 150, 96, 255))
        d.line([(S(10), S(39)), (S(54), S(39))], fill=INK, width=W2 // 2)
        d.ellipse([S(18), S(12), S(46), S(28)], fill=CREAM)
    elif name == 'check':
        d.line([(S(12), S(34)), (S(26), S(48)), (S(52), S(16))], fill=CREAM, width=int(S(8)), joint='curve')
    elif name == 'clock':
        d.ellipse([S(8), S(8), S(56), S(56)], fill=CREAM); d.ellipse([S(14), S(14), S(50), S(50)], fill=(60, 50, 40, 255))
        d.line([(m, m), (m, S(18))], fill=CREAM, width=W2); d.line([(m, m), (S(44), S(38))], fill=CREAM, width=W2)
    elif name == 'lock':
        d.rounded_rectangle([S(14), S(28), S(50), S(56)], S(4), fill=GOLD)
        d.arc([S(20), S(10), S(44), S(40)], 180, 360, fill=GOLD, width=int(S(6)))
        d.line([(S(20), S(26)), (S(20), S(30))], fill=GOLD, width=int(S(6))); d.line([(S(44), S(26)), (S(44), S(30))], fill=GOLD, width=int(S(6)))
    elif name == 'medal':
        d.polygon([(S(20), S(4)), (S(30), S(4)), (S(34), S(26)), (S(24), S(26))], fill=RED); d.polygon([(S(34), S(4)), (S(44), S(4)), (S(40), S(26)), (S(30), S(26))], fill=(60, 100, 150, 255))
        d.ellipse([S(14), S(22), S(50), S(58)], fill=GOLD); d.ellipse([S(22), S(30), S(42), S(50)], fill=(240, 210, 140, 255))
    elif name == 'wind':
        for i, (y, l) in enumerate(((20, 40), (32, 50), (44, 34))):
            d.line([(S(8), S(y)), (S(8 + l), S(y))], fill=BLUE, width=W2 + 2)
            d.arc([S(8 + l - 8), S(y - 10), S(8 + l + 8), S(y)], 270, 450, fill=BLUE, width=W2 + 2)
    elif name == 'flag':
        d.line([(S(16), S(6)), (S(16), S(58))], fill=CREAM, width=W2 + 2)
        d.polygon([(S(18), S(8)), (S(54), S(16)), (S(18), S(32))], fill=RED)
    elif name == 'cross':
        d.line([(S(14), S(14)), (S(50), S(50))], fill=CREAM, width=int(S(8))); d.line([(S(50), S(14)), (S(14), S(50))], fill=CREAM, width=int(S(8)))
    elif name == 'academy':
        d.polygon([(m, S(4)), (S(56), S(14)), (S(52), S(40)), (m, S(60)), (S(12), S(40)), (S(8), S(14))], fill=GOLD)
        d.polygon([(m, S(12)), (S(48), S(19)), (S(45), S(38)), (m, S(52)), (S(19), S(38)), (S(16), S(19))], fill=(70, 96, 70, 255))
        deer_head(d, m, S(30), 0.6, CREAM)
    elif name == 'token':
        d.ellipse([S(8), S(8), S(56), S(56)], fill=GOLD); d.ellipse([S(16), S(16), S(48), S(48)], fill=(236, 204, 130, 255))
        d.polygon([(m, S(20)), (S(40), m), (m, S(44)), (S(24), m)], fill=GOLD)
    elif name == 'star':
        pts = []
        for i in range(10):
            r = S(28) if i % 2 == 0 else S(12); a = -math.pi / 2 + i * math.pi / 5
            pts.append((m + math.cos(a) * r, m + math.sin(a) * r))
        d.polygon(pts, fill=GOLD)
    elif name == 'alert':
        d.polygon([(m, S(6)), (S(60), S(56)), (S(4), S(56))], fill=(240, 196, 90, 255))
        d.line([(m, S(22)), (m, S(40))], fill=INK, width=W2 + 2); d.ellipse([m - S(3), S(45), m + S(3), S(51)], fill=INK)
    elif name == 'book':
        d.polygon([(S(6), S(14)), (m, S(20)), (m, S(56)), (S(6), S(50))], fill=CREAM); d.polygon([(S(58), S(14)), (m, S(20)), (m, S(56)), (S(58), S(50))], fill=(220, 206, 176, 255))
        d.line([(m, S(20)), (m, S(56))], fill=(120, 90, 60, 255), width=W2 // 2)
    elif name == 'archery':  # [onboard] recurve bow with a nocked arrow
        d.arc([S(6), S(6), S(40), S(58)], 270, 90, fill=CREAM, width=int(S(5)))
        d.line([(S(23), S(8)), (S(23), S(56))], fill=(200, 190, 170, 255), width=max(1, W2 // 3))
        d.line([(S(18), m), (S(56), m)], fill=GOLD, width=W2)
        d.polygon([(S(50), m - S(6)), (S(62), m), (S(50), m + S(6))], fill=GOLD)
        d.polygon([(S(14), m - S(6)), (S(22), m), (S(14), m + S(6)), (S(18), m)], fill=RED)
    elif name == 'leave':
        d.rectangle([S(8), S(10), S(30), S(54)], fill=CREAM)
        d.line([(S(24), m), (S(54), m)], fill=GOLD, width=W2 + 4); d.polygon([(S(44), S(20)), (S(60), m), (S(44), S(44))], fill=GOLD)
    return finish(im)

atlas = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
for i, n in enumerate(NAMES):
    atlas.paste(draw(n), ((i % 8) * 64, (i // 8) * 64))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'artqa'))
from bleed import bleed_image  # [artqa] no dark fringe under bilinear / mipmapped sampling
bleed_image(atlas).save(os.path.join(OUT, 'icons.png'), optimize=True)
prev = Image.new('RGBA', (512, 256), (73, 55, 43, 255)); prev.alpha_composite(atlas.crop((0, 0, 512, 256)))
os.makedirs(os.path.join(ROOT, 'docs/ws/academy'), exist_ok=True)
prev.convert('RGB').save(os.path.join(ROOT, 'docs/ws/academy/icons.jpg'), quality=90)
print('icons', len(NAMES))
