"""[artqa] (copy of tools/guide/plate_preview.py writing to docs/ws/artqa) Renders each Field School plate with its lang labels the way GuideArt draws them (paper halo, leader lines,
Inter SMALL), at a typical on-screen size, for review: docs/ws/fieldbook/labelled_<plate>.jpg.

python3 tools/guide/plate_preview.py <repo> <fonts dir> [plate width in GUI px, default 420] [gui scale, default 2]
"""
import json, os, re, sys
from PIL import Image, ImageDraw, ImageFont

ROOT, FONTS = sys.argv[1], sys.argv[2]
OUTDIR = os.environ.get("ARTQA_OUT", os.path.join(ROOT, "docs/ws/artqa/after"))
ONLY = os.environ.get("ARTQA_ONLY", "").split()
GW = int(sys.argv[3]) if len(sys.argv) > 3 else 420
SC = int(sys.argv[4]) if len(sys.argv) > 4 else 2
LANG = json.load(open(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/guide.json'), encoding='utf-8'))
P = 'guide.frontierhunts.'
f0 = ImageFont.truetype(os.path.join(FONTS, 'inter_semibold.ttf'), 100)
a, d = f0.getmetrics()
FONT = ImageFont.truetype(os.path.join(FONTS, 'inter_semibold.ttf'), max(4, round(6.4 * SC * 100 / (a + d))))
COLORS = {'GuideUi.INK_BROWN': 0xFF3B2E20, 'GuideUi.RED': 0xFFA5281F, 'GuideUi.GREEN': 0xFF3E6E37, 'GuideUi.BLUE': 0xFF2F5F80}

src = open(os.path.join(ROOT, 'src/com/formaworks/frontierhunts/guide/client/GuideArt.java')).read()
plates = []
for m in re.finditer(r'(\w+)\("(\w+)", (\d+), (\d+), List\.of\((.*?)\)\),?\n', src, re.S):
    ls = []
    for lm in re.finditer(r'L\.(at|lead)\("(\w+)", ([\d.]+)F, ([\d.]+)F, ([\d.]+)F, ([\d.]+)F, (-?\d), ([\w.x]+)(?:, ([\d.]+)F)?\)', m.group(5)):
        col = lm.group(8)
        ls.append(dict(lead=lm.group(1) == 'lead', id=lm.group(2), ax=float(lm.group(3)), ay=float(lm.group(4)), tx=float(lm.group(5)), ty=float(lm.group(6)),
                       align=int(lm.group(7)), maxW=float(lm.group(9) or 0.36), color=COLORS.get(col, int(col, 16) if col.startswith('0x') else 0xFF3B2E20)))
    plates.append((m.group(2), int(m.group(3)), int(m.group(4)), ls))


def rgba(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, 255)


def width(s):
    return FONT.getlength(s) / SC


def wrap(s, w):
    out, line = [], ''
    for word in s.split(' '):
        t = (line + ' ' + word).strip()
        if width(t) <= w or not line:
            line = t
        else:
            out.append(line)
            line = word
    out.append(line)
    return out


for name, pw, ph, labels in plates:
    if ONLY and ONLY != [''] and name not in ONLY:
        continue
    os.makedirs(OUTDIR, exist_ok=True)
    w = GW
    h = w * ph // pw
    img = Image.new('RGBA', ((w + 8) * SC, (h + 8) * SC), (234, 227, 208, 255))
    pl = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/%s.png' % name)).convert('RGBA')
    img.alpha_composite(pl.resize((w * SC, h * SC), Image.BILINEAR), (4 * SC, 4 * SC))
    dr = ImageDraw.Draw(img, 'RGBA')
    x, y = 4, 4
    for L in labels:
        t = LANG.get(P + 'art.%s.%s' % (name, L['id']), '??' + L['id'])
        lines = wrap(t, max(40, int(L['maxW'] * w)))
        lh = 9
        bw = max(width(l) for l in lines)
        px, py = x + L['tx'] * w, y + L['ty'] * h
        lft = px if L['align'] < 0 else (px - bw if L['align'] > 0 else px - bw / 2)
        lft = max(x + 2, min(x + w - bw - 2, lft))
        tp = max(y + 1, min(y + h - len(lines) * lh - 1, py))
        if L['lead']:
            axp, ayp = x + L['ax'] * w, y + L['ay'] * h
            sx = max(lft, min(lft + bw, axp))
            sy = tp - 1 if ayp < tp else (tp + len(lines) * lh if ayp > tp + len(lines) * lh else tp + lh / 2)
            if axp < lft - 2:
                sx, sy = lft - 2, tp + lh / 2
            elif axp > lft + bw + 2:
                sx, sy = lft + bw + 2, tp + lh / 2
            c = rgba(L['color'])
            dr.line([(sx * SC, sy * SC), (axp * SC, ayp * SC)], fill=c[:3] + (192,), width=max(1, int(0.75 * SC)))
            dr.ellipse([(axp - 1.6) * SC, (ayp - 1.6) * SC, (axp + 1.6) * SC, (ayp + 1.6) * SC], fill=c)
            dr.ellipse([(axp - 0.8) * SC, (ayp - 0.8) * SC, (axp + 0.8) * SC, (ayp + 0.8) * SC], fill=(234, 227, 208, 255))
        dr.rounded_rectangle([(lft - 2) * SC, (tp - 1) * SC, (lft + bw + 2) * SC, (tp + len(lines) * lh) * SC], radius=2 * SC, fill=(234, 227, 208, 153))
        yy = tp
        for l in lines:
            lw = width(l)
            lx = lft if L['align'] < 0 else (lft + bw - lw if L['align'] > 0 else lft + (bw - lw) / 2)
            dr.text((lx * SC, yy * SC), l, font=FONT, fill=rgba(L['color']))
            yy += lh
    out = os.path.join(OUTDIR, 'fs_%s.jpg' % name)
    img.convert('RGB').save(out, quality=88)
    print('ok', out)
