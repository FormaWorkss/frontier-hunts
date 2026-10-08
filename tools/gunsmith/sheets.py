"""[gunsmith] Before/after contact sheets from two GunBench `render` runs: python3 sheets.py <before dir> <after dir> <vanilla dir> <out dir>"""
import sys, os
from PIL import Image, ImageDraw

B, A, V, O = sys.argv[1:5]
os.makedirs(O, exist_ok=True)
CW, CH = 640, 300


def cell(img, i):
    return img.crop(((i % 2) * CW, (i // 2) * CH, (i % 2) * CW + CW, (i // 2) * CH + CH))


def tag(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle((0, CH - 22, 110, CH), fill=(0, 0, 0))
    d.text((6, CH - 18), text, fill=(255, 220, 120))
    return im


guns = ['lever_rifle', 'semi_auto_rifle', 'pump_shotgun', 'double_barrel', 'semi_auto_shotgun', 'revolver', 'field_pistol', 'tranquilizer_rifle',
        'flare_gun', 'bait_launcher']
for k in range(0, len(guns), 3):
    group = guns[k:k + 3]
    sheet = Image.new('RGB', (CW * 2, CH * 3 * len(group)))
    for g, name in enumerate(group):
        b = Image.open(os.path.join(B, 'gun_%s.png' % name))
        a = Image.open(os.path.join(A, 'gun_%s.png' % name))
        for r, i in enumerate((1, 5, 7)):      # 3/4 bare, kit 3/4, ADS red dot
            sheet.paste(tag(cell(b, i), 'BEFORE'), (0, (g * 3 + r) * CH))
            sheet.paste(tag(cell(a, i), 'AFTER'), (CW, (g * 3 + r) * CH))
    sheet.save(os.path.join(O, 'guns_%d.png' % (k // 3 + 1)), optimize=True)
for name, cols in (('sights', 3), ('attachments', 4), ('gun_ridgeline', 2)):
    b = Image.open(os.path.join(B, name + '.png'))
    a = Image.open(os.path.join(A, name + '.png'))
    w, h = a.size
    s = Image.new('RGB', (w * 2 + 12, max(h, b.size[1])), (20, 20, 20))
    s.paste(b, (0, 0))
    s.paste(a, (w + 12, 0))
    d = ImageDraw.Draw(s)
    d.text((10, max(h, b.size[1]) - 20), 'BEFORE', fill=(255, 220, 120))
    d.text((w + 22, max(h, b.size[1]) - 20), 'AFTER', fill=(255, 220, 120))
    s = s.resize((s.size[0] // 2, s.size[1] // 2), Image.LANCZOS)
    s.save(os.path.join(O, name + '_before_after.png'), optimize=True)
# vanilla look
vs = Image.new('RGB', (CW * 2, CH * 3))
for r, name in enumerate(('lever_rifle', 'revolver', 'semi_auto_rifle')):
    v = Image.open(os.path.join(V, 'gun_%s.png' % name))
    vs.paste(cell(v, 1), (0, r * CH))
    vs.paste(cell(v, 5), (CW, r * CH))
vs.save(os.path.join(O, 'vanilla_look.png'), optimize=True)
print('ok')
