"""[guide] Offline mock of the Field School guide page, objective card and toast (layout check only, not the real renderer).

python3 tools/guide/mock_screen.py <repo> <fonts dir> <out.png> [lesson] [gui_w gui_h scale]
Mirrors the constants in FieldSchoolScreen / GuideArt / ObjectiveCard / GuideToast so text fit and label placement can be
reviewed without a game client.
"""
import json, re, sys, os
from PIL import Image, ImageDraw, ImageFont

ROOT, FONTS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
LESSON = sys.argv[4] if len(sys.argv) > 4 else 'shot'
GW, GH, SC = (int(sys.argv[5]), int(sys.argv[6]), int(sys.argv[7])) if len(sys.argv) > 7 else (854, 480, 2)
LANG = json.load(open(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/guide.json')))
P = 'guide.frontierhunts.'
PRONE = 'Z'
SCROLL = int(os.environ.get('MOCK_SCROLL', '0'))
LESSONS = ['wind', 'sign', 'glass', 'stalk', 'shot', 'trail', 'harvest', 'tips']
ART = {'wind': 'wind', 'sign': 'tracks', 'glass': 'glass', 'stalk': 'stalk', 'shot': 'vitals', 'trail': 'blood', 'harvest': 'harvest'}


def ttf(name, size):  # MC ttf "size" = pixel height of ascent..descent
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2), 'TITLE': ttf('inter_bold.ttf', 12.0)}
LH = {'SMALL': 8, 'BODY': 10, 'STRONG': 10, 'TITLE': 14}


def col(argb):
    return ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)


img = Image.new('RGBA', (GW * SC, GH * SC), (60, 80, 60, 255))
# a fake world behind
bg = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/welcome.png')).convert('RGBA').resize((GW * SC, GH * SC))
img.alpha_composite(bg)
d = ImageDraw.Draw(img, 'RGBA')


def fill(x0, y0, x1, y1, c):
    d.rectangle([x0 * SC, y0 * SC, x1 * SC - 1, y1 * SC - 1], fill=col(c))


def text(s, x, y, c, size='BODY'):
    d.text((x * SC, y * SC), s, font=F[size], fill=col(c))
    return width(s, size)


def width(s, size):
    return d.textlength(s, font=F[size]) / SC


def wrap(s, w, size):
    out = []
    for para in s.split('\n'):
        line = ''
        for word in para.split(' '):
            t = (line + ' ' + word).strip()
            if width(t, size) <= w or not line:
                line = t
            else:
                out.append(line)
                line = word
        out.append(line)
    return out


def fit(s, w, size):
    if width(s, size) <= w:
        return s
    while s and width(s + '…', size) > w:
        s = s[:-1]
    return s.rstrip() + '…'


def tr(k, *a):
    s = LANG.get(k, k)
    for v in a:
        s = s.replace('%s', str(v), 1)
    return s


# ------------------------------------------------------------------ guide screen
panelW, panelH = min(600, GW - 16), min(392, GH - 16)
left, top = (GW - panelW) // 2, (GH - panelH) // 2
side = 104 if panelW < 460 else 132
bodyX, bodyW = left + side + 20, panelW - side - 40
viewTop, viewBottom = top + 52, top + panelH - 34
fill(0, 0, GW, GH, 0xB0101716)
fill(left + 4, top + 5, left + panelW + 4, top + panelH + 5, 0x80000000)
fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, 0xFF413526)
fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xFF88704A)
fill(left, top, left + panelW, top + panelH, 0xFFEAE3D0)
fill(left, top, left + side, top + panelH, 0xFF49372B)
fill(left + side, top, left + side + 12, top + panelH, 0xFFCFC2A0)
fill(left + 9, top + 11, left + 12, top + 37, 0xFFB99859)
text(tr(P + 'screen.brand_top'), left + 17, top + 12, 0xFFEFE8D7, 'STRONG')
text(tr(P + 'screen.brand_bottom'), left + 17, top + 25, 0xFFD8BD88, 'SMALL')
rows = 8
rowH = max(14, min(26, (panelH - 48 - 62) // rows))
cur = LESSONS.index(LESSON)
for i, les in enumerate(LESSONS):
    y = top + 46 + i * rowH
    if i == cur:
        fill(left + 6, y, left + side - 6, y + rowH - 3, 0xFF5E4636)
        fill(left + 6, y, left + 8, y + rowH - 3, 0xFFB99859)
    cy = y + (rowH - 3) / 2
    done = i < 3
    d.ellipse([(left + 17 - 6.5) * SC, (cy - 6.5) * SC, (left + 17 + 6.5) * SC, (cy + 6.5) * SC], fill=col(0xFFB99859 if (done or i == 3) else 0xFF7F8A7A))
    if not done:
        d.ellipse([(left + 17 - 5.5) * SC, (cy - 5.5) * SC, (left + 17 + 5.5) * SC, (cy + 5.5) * SC], fill=col(0xFF49372B))
        text(str(i + 1), left + 17 - width(str(i + 1), 'SMALL') / 2, cy - 3.5, 0xFFB99859 if i == 3 else 0xFFC9B99A, 'SMALL')
    t = tr(P + 'lesson.%s.%s' % (les, 'short' if side - 12 < 110 else 'title'))
    text(fit(t, side - 12 - 26, 'SMALL'), left + 6 + 21, cy - 4, 0xFFF4EEDD if i == cur else 0xFFE3D9C3, 'SMALL')
text(fit(tr(P + 'screen.course', 3, 7), side - 16, 'SMALL'), left + 9, top + panelH - 40, 0xFFC9B99A, 'SMALL')
fill(left + 9, top + panelH - 31, left + side - 9, top + panelH - 29, 0x40EFE8D7)
fill(left + 9, top + panelH - 31, left + 9 + (side - 18) * 3 / 7, top + panelH - 29, 0xFFB99859)
fill(left + 6, top + panelH - 26, left + side - 6, top + panelH - 8, 0xFF2D4237)
s = tr(P + 'screen.skip_course')
text(s, left + side / 2 - width(s, 'SMALL') / 2, top + panelH - 26 + 5, 0xFFEFE8D7, 'SMALL')
# header
text(tr(P + 'screen.eyebrow', cur + 1, 7), bodyX, top + 13, 0xFF8F6E2F, 'SMALL')
pill = tr(P + 'screen.pill_current')
pw = width(pill, 'SMALL') + 10
fill(left + panelW - 16 - pw, top + 12, left + panelW - 16, top + 23, 0xFF8F6E2F)
text(pill, left + panelW - 16 - pw + 5, top + 14, 0xFFF4EEDD, 'SMALL')
text(fit(tr(P + 'lesson.%s.title' % LESSON), bodyW - pw - 6, 'TITLE'), bodyX, top + 25, 0xFF3B2E20, 'TITLE')
fill(bodyX, top + 45, left + panelW - 16, top + 46, 0xFFC5C2AE)

# labels from GuideArt.java
src = open(os.path.join(ROOT, 'src/com/formaworks/frontierhunts/guide/client/GuideArt.java')).read()
LABELS = {}
DIMS = {}
for m in re.finditer(r'(\w+)\("(\w+)", (\d+), (\d+), List\.of\((.*?)\)\),?\n', src, re.S):
    DIMS[m.group(2)] = (int(m.group(3)), int(m.group(4)))
    ls = []
    for lm in re.finditer(r'L\.(at|lead)\("(\w+)", ([\d.]+)F, ([\d.]+)F, ([\d.]+)F, ([\d.]+)F, (-?\d), ([\w.x]+)(?:, ([\d.]+)F)?\)', m.group(5)):
        ls.append(dict(lead=lm.group(1) == 'lead', id=lm.group(2), ax=float(lm.group(3)), ay=float(lm.group(4)), tx=float(lm.group(5)), ty=float(lm.group(6)),
                       align=int(lm.group(7)), maxW=float(lm.group(9) or 0.36)))
    LABELS[m.group(2)] = ls

layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
y = viewTop + 4
art = ART.get(LESSON)


def plate(name, y):
    w = bodyW
    pw_, ph_ = DIMS.get(name, (1024, 512))
    h = w * ph_ // pw_
    maxH = max(90, int((viewBottom - viewTop) * 0.62))
    aw = w
    if h > maxH:
        h = maxH
        aw = h * pw_ // ph_
    ax = bodyX + (w - aw) // 2
    fill(ax - 3, y - 2, ax + aw + 3, y + h + 2, 0x22B99859)
    pl = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/%s.png' % name)).convert('RGBA').resize((aw * SC, h * SC), Image.BILINEAR)
    img.alpha_composite(pl, (ax * SC, y * SC))
    for L in LABELS.get(name, []):
        t = tr(P + 'art.%s.%s' % (name, L['id']))
        lines = wrap(t, max(40, int(L['maxW'] * aw)), 'SMALL')
        lh = 9
        bw = max(width(l, 'SMALL') for l in lines)
        px, py = ax + L['tx'] * aw, y + L['ty'] * h
        lft = px if L['align'] < 0 else (px - bw if L['align'] > 0 else px - bw / 2)
        lft = max(ax + 2, min(ax + aw - bw - 2, lft))
        tp = max(y + 1, min(y + h - len(lines) * lh - 1, py))
        if L['lead']:
            axp, ayp = ax + L['ax'] * aw, y + L['ay'] * h
            sx = max(lft, min(lft + bw, axp))
            sy = tp - 1 if ayp < tp else (tp + len(lines) * lh if ayp > tp + len(lines) * lh else tp + lh / 2)
            if axp < lft - 2:
                sx, sy = lft - 2, tp + lh / 2
            elif axp > lft + bw + 2:
                sx, sy = lft + bw + 2, tp + lh / 2
            d.line([(sx * SC, sy * SC), (axp * SC, ayp * SC)], fill=(60, 40, 30, 190), width=max(1, int(0.75 * SC)))
            d.ellipse([(axp - 1.6) * SC, (ayp - 1.6) * SC, (axp + 1.6) * SC, (ayp + 1.6) * SC], fill=(60, 40, 30, 255))
        d.rectangle([(lft - 2) * SC, (tp - 1) * SC, (lft + bw + 2) * SC, (tp + len(lines) * lh) * SC], fill=(234, 227, 208, 153))
        yy = tp
        for l in lines:
            lw = width(l, 'SMALL')
            lx = lft if L['align'] < 0 else (lft + bw - lw if L['align'] > 0 else lft + (bw - lw) / 2)
            text(l, lx, yy, 0xFF3B2E20, 'SMALL')
            yy += lh
    return h + 10


if art:
    y += plate(art, y)
i = 1
while P + 'lesson.%s.p%d' % (LESSON, i) in LANG:
    for l in wrap(tr(P + 'lesson.%s.p%d' % (LESSON, i), PRONE), bodyW, 'BODY'):
        if y < viewBottom - 10:
            text(l, bodyX, y, 0xFF243A32, 'BODY')
        y += 12
    y += 7
    if LESSON == 'sign' and i == 2:
        y += plate('sign', y)
    i += 1
# bottom bar
by = top + panelH - 27
bw = min(84, (bodyW - 10) // 3)
for k, (label, x) in enumerate([(tr(P + 'screen.next'), bodyX + bodyW - bw), (tr(P + 'screen.prev'), bodyX + bodyW - bw * 2 - 6), (tr(P + 'screen.skip_lesson'), bodyX)]):
    w = bw if k < 2 else min(150, bodyW - bw * 2 - 16)
    fill(x, by, x + w, by + 20, 0xFF2D4237)
    text(label, x + w / 2 - width(label, 'SMALL') / 2, by + 6, 0xFFEFE8D7, 'SMALL')

# ------------------------------------------------------------------ objective card (top-left) and a toast (top-right)
W = 182
les = 'stalk'
fill(6, 6, 6 + W, 6 + 31 + 2 * 9 + 14, 0xD8161D17)
fill(6, 6, 8, 6 + 31 + 2 * 9 + 14, 0xFFB99859)
ICONS = ['wind', 'sign', 'glass', 'stalk', 'shot', 'trail', 'harvest', 'tips', 'deer', 'blizzard', 'season', 'predator', 'winded', 'spotted', 'trophy', 'journal']
ATLAS = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/icons.png')).convert('RGBA')


def icon(name, x, y, size):
    i = ICONS.index(name)
    cell = ATLAS.crop(((i % 4) * 64, (i // 4) * 64, (i % 4) * 64 + 64, (i // 4) * 64 + 64)).resize((size * SC, size * SC), Image.BILINEAR)
    img.alpha_composite(cell, (int(x * SC), int(y * SC)))


icon(les, 6 + 5, 6 + 7, 18)
text(tr(P + 'card.eyebrow', 4, 7), 33, 11, 0xFFB99859, 'SMALL')
for i in range(7):
    c = 0xFFB99859 if i < 3 else (0xFFEFE8D7 if i == 3 else 0x55EFE8D7)
    fill(6 + W - 8 - (7 - i) * 6, 13, 6 + W - 8 - (7 - i) * 6 + 4, 17, c)
text(fit(tr(P + 'lesson.%s.title' % les), W - 34, 'STRONG'), 33, 21, 0xFFEFE8D7, 'STRONG')
ty = 6 + 31 - 2
for l in wrap(tr(P + 'lesson.%s.card' % les), W - 34, 'SMALL')[:2]:
    text(l, 33, ty, 0xFFC9C3B0, 'SMALL')
    ty += 9
text(tr(P + 'card.key', 'H'), 33, ty + 3, 0xFF8E937F, 'SMALL')
text('1 / 3', 6 + W - 8 - width('1 / 3', 'SMALL'), ty + 3, 0xFFB99859, 'SMALL')
TW = 214
tx0 = GW - TW
body = wrap(tr(P + 'hint.ran.body'), TW - 40, 'SMALL')[:4]
th = max(32, 28 + len(body) * 9)
fill(tx0, 0, GW, th, 0xF0182019)
fill(tx0, 0, tx0 + 3, th, 0xFFD08A3A)
d.ellipse([(tx0 + 18 - 11.5) * SC, (16 - 11.5) * SC, (tx0 + 18 + 11.5) * SC, (16 + 11.5) * SC], fill=(208, 138, 58, 64))
icon('trail', tx0 + 8, 6, 20)
text(tr(P + 'toast.hint'), tx0 + 34, 5, 0xFFD08A3A, 'SMALL')
text(tr(P + 'hint.ran.title'), tx0 + 34, 14, 0xFFEFE8D7, 'STRONG')
yy = 26
for l in body:
    text(l, tx0 + 34, yy, 0xFFC9C3B0, 'SMALL')
    yy += 9
img.convert('RGB').save(OUT)
print('ok', OUT)
