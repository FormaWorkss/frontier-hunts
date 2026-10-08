"""[fharea] Offline mock of the first-hunt compass ribbon, the arrival note and the beacon column, over a painted
Minecraft-like scene at 1920x1080 (GUI scale 3, 640x360 GUI px). Mirrors the constants and the drawing order of
guide/client/FirstHuntCompass.java (and the top-left card of FirstHuntClient.renderCard) with the real Inter fonts
from the jar. Layout and colour check only, not the real renderer.

python3 tools/fharea/mock_hud.py <repo> <unzipped jar dir (assets/frontierhunts/font)> <out dir>
writes mock_hud.png (the scene) and mock_hud_states.png (ribbon facing / area behind / arrival, 2x)
"""
import json, glob, math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT, JAR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SC = 3
GW, GH = 640, 360
FONTS = os.path.join(JAR, 'assets/frontierhunts/font')
LANG = {}
for f in sorted(glob.glob(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/*.json'))):
    LANG.update(json.load(open(f)))


def tr(k, *a):
    s = LANG.get(k, k)
    for x in a:
        s = s.replace('%s', str(x), 1)
    return s


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2)}
SHIFT = {'SMALL': -1.34, 'BODY': -0.18, 'STRONG': -0.03}
_m = ImageDraw.Draw(Image.new('RGBA', (4, 4)))


def width(s, size):
    return _m.textlength(s, font=F[size]) / SC


def col(c, f=1.0):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, int(((c >> 24) & 255) * max(0.0, min(1.0, f))))


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def wrapdeg(a):
    a = (a + 180.0) % 360.0 - 180.0
    return a


# ---------------------------------------------------------------- constants (FirstHuntCompass)
RW, RH, TOP, TAPER, HALF_FOV = 176.0, 13.0, 4.0, 46.0, 80.0
GOLD, GOLD_DEEP, INK, CREAM, MUTED, BAND = 0xFFD6A94A, 0xFF9C7322, 0xFF121812, 0xFFEFE8D7, 0xFFB9B4A2, 0x9A0E130F


class Canvas:
    def __init__(self, img):
        self.img = img

    def flush(self):
        pass

    def fill(self, x0, y0, x1, y1, c, f=1.0):  # screen pixels, like GuiGraphics.fill under the pixel pose
        if x1 <= x0 or y1 <= y0:
            return
        L = Image.new('RGBA', (x1 - x0, y1 - y0), col(c, f))
        self.flush()
        self.img.alpha_composite(L, (x0, y0))

    def text(self, s, x, y, c, size, f=1.0):
        self.flush()
        w, h = int(width(s, size) * SC) + 8, 14 * SC
        L = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        ox, oy = int(x * SC), int((y + SHIFT[size] + 1.2) * SC)
        ImageDraw.Draw(L).text((x * SC - ox, (y + SHIFT[size] + 1.2) * SC - oy), s, font=F[size], fill=col(c, f))
        self.img.alpha_composite(L, (ox, oy))


def px(v):
    return int(round(v * SC))


def taper(from_centre):
    return smooth((RW / 2 - abs(from_centre)) / TAPER)


def diamond(cv, cx, cy, h, c, f):
    rows = max(1, round(h * SC))
    xc, yc = px(cx), px(cy)
    for r in range(-rows, rows + 1):
        half = rows - abs(r)
        cv.fill(xc - half, yc + r, xc + half + 1, yc + r + 1, c, f)


def arrow(cv, cx, cy, h, depth, d, c, f):
    rows = max(1, round(h * SC))
    base, yc = px(cx - d * depth / 2), px(cy)
    dd = depth * SC
    for r in range(-rows, rows + 1):
        ln = round(dd * (1 - abs(r) / (rows + 1)))
        if d > 0:
            cv.fill(base, yc + r, base + ln, yc + r + 1, c, f)
        else:
            cv.fill(base - ln, yc + r, base, yc + r + 1, c, f)


def pill(cv, x, y, w, h, c, f):
    x0, y0, x1, y1 = px(x), px(y), px(x + w), px(y + h)
    r = max(1, round(SC * 1.5))
    cv.fill(x0 + r, y0, x1 - r, y0 + 1, c, f)
    cv.fill(x0 + 1, y0 + 1, x1 - 1, y0 + r, c, f)
    cv.fill(x0, y0 + r, x1, y1 - r, c, f)
    cv.fill(x0 + 1, y1 - r, x1 - 1, y1 - 1, c, f)
    cv.fill(x0 + r, y1 - 1, x1 - r, y1, c, f)


def ring(cv, cx, cy, r, t, c, f):
    ro, ri = r * SC, (r - t) * SC
    xc, yc = px(cx), px(cy)
    n = math.ceil(ro)
    for dy in range(-n, n + 1):
        yy = dy + 0.5
        if abs(yy) > ro:
            continue
        outer = int(math.floor(math.sqrt(ro * ro - yy * yy)))
        inner = int(math.ceil(math.sqrt(ri * ri - yy * yy))) if abs(yy) < ri else -1
        if inner < 0:
            cv.fill(xc - outer, yc + dy, xc + outer, yc + dy + 1, c, f)
        else:
            cv.fill(xc - outer, yc + dy, xc - inner, yc + dy + 1, c, f)
            cv.fill(xc + inner, yc + dy, xc + outer, yc + dy + 1, c, f)


POINTS = ['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw']


def ribbon(cv, head, bearing, metres, a=1.0, since_ms=99999, edge_side=1):
    cx = GW / 2
    rel = wrapdeg(bearing - head)
    if abs(rel) < 150:
        edge_side = 1 if rel >= 0 else -1
    clamped = abs(rel) > HALF_FOV - 6
    ppd = (RW / 2 - 8) / HALF_FOV
    # band
    x0, x1, y0, y1 = px(cx - RW / 2), px(cx + RW / 2), px(TOP), px(TOP + RH)
    sl = max(1, round(SC))
    for x in range(x0, x1, sl):
        t = taper((x + sl * 0.5) / SC - cx)
        if t <= 0:
            continue
        w = min(sl, x1 - x)
        cv.fill(x, y0, x + w, y1, BAND, a * t)
        cv.fill(x, y0, x + w, y0 + 1, 0x55EFE8D7, a * t)
        cv.fill(x, y1 - 1, x + w, y1, 0x40D6A94A, a * t)
    tw = max(1, round(SC * 0.5))
    for i in range(24):
        if i % 3 == 0:
            continue
        r = wrapdeg(i * 15 - head)
        if abs(r) > HALF_FOV:
            continue
        x = cx + r * ppd
        ta = taper(x - cx) * a
        if ta < 0.03:
            continue
        tx = px(x) - tw // 2
        cv.fill(tx, px(TOP + RH - 4), tx + tw, px(TOP + RH - 1.5), 0xB0EFE8D7, ta)
    for row in range(round(2.5 * SC)):
        half = max(0, round((2.5 * SC - row) * 0.8))
        xc = px(cx)
        cv.fill(xc - half, y0 + row, xc + half + 1, y0 + row + 1, 0xE6EFE8D7, a)
    # letters (a letter under the marker steps back)
    mx = cx + edge_side * (RW / 2 - 7) if clamped else cx + rel * ppd
    for i in range(8):
        r = wrapdeg(i * 45 - head)
        if abs(r) > HALF_FOV - 2:
            continue
        x = cx + r * ppd
        ta = taper(x - cx) * a
        if not clamped:
            ta *= max(0.15, min(1.0, (abs(x - mx) - 5) / 7))
        if ta < 0.04:
            continue
        major = i % 2 == 0
        size = 'STRONG' if major else 'SMALL'
        s = tr('firsthunt.frontierhunts.hud.point.' + POINTS[i])
        c = (CREAM if i == 0 else 0xFFE2DCCB) if major else MUTED
        ty = TOP + 2.5 if major else TOP + 4.0
        cv.text(s, x - width(s, size) / 2, ty, c, size, ta * (1 if major else 0.85))
    my = TOP + RH / 2
    if clamped:
        arrow(cv, mx, my, 6.1, 6.9, edge_side, INK, a * 0.9)
        arrow(cv, mx, my, 5.0, 5.5, edge_side, GOLD, a)
    else:
        diamond(cv, mx, my, 5.2, INK, a * 0.9)
        diamond(cv, mx, my, 4.1, GOLD, a)
        diamond(cv, mx, my, 1.5, INK, a)
    dist = tr('firsthunt.frontierhunts.hud.distance', metres)
    dw = width(dist, 'SMALL')
    cap = tr('firsthunt.frontierhunts.hud.caption')
    capw = width(cap, 'SMALL')
    capa = 1.0 if abs(rel) <= 10 or since_ms < 6000 else 0.0
    GAP = 7.0
    w = dw + 8 + capa * (GAP + capw)
    ty = TOP + RH + 3
    lx = max(cx - RW / 2 + 4, min(cx + RW / 2 - 4 - w, mx - w / 2))
    pill(cv, lx, ty - 1.5, w, 10, 0xB80E130F, a)
    if capa > 0.05:
        ddx, ddy = lx + 4 + dw + GAP / 2, ty + 4.0
        cv.fill(px(ddx - 0.6), px(ddy - 0.6), px(ddx + 0.6) + 1, px(ddy + 0.6) + 1, 0xC0D6A94A, a * capa)
    cv.text(dist, lx + 4, ty + 1.5, GOLD, 'SMALL', a)
    if capa > 0.05:
        cv.text(cap, lx + 4 + dw + GAP, ty + 1.5, CREAM, 'SMALL', a * capa ** 3)


def arrival(cv, step, a=1.0):
    cx = GW / 2
    title = tr('firsthunt.frontierhunts.hud.inside')
    sub = tr('firsthunt.frontierhunts.hud.inside.' + step)
    w = max(width(title, 'STRONG'), width(sub, 'SMALL')) + 34
    y = TOP + (1 - a) * -4
    x = cx - w / 2
    pill(cv, x, y, w, 24, 0xD8161D17, a)
    cv.fill(px(x + 1), px(y + 3), px(x + 3.5), px(y + 21), GOLD, a)
    ring(cv, x + 14, y + 12, 6.0, 1.4, GOLD, a)
    diamond(cv, x + 14, y + 12, 2.2, GOLD, a)
    tx = cx - w / 2 + 26
    cv.text(title, tx, y + 3.5, CREAM, 'STRONG', a)
    cv.text(sub, tx, y + 14.5, MUTED, 'SMALL', a)


def card(cv, step_n, title, eyebrow_key='firsthunt.frontierhunts.card.eyebrow'):
    """the existing top-left card, closed, without its own arrow (the ribbon shows the way)"""
    W, x, y, h = 196, 6.0, 6.0, 31
    L = Image.new('RGBA', cv.img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(L)
    d.rounded_rectangle([x * SC, y * SC, (x + W) * SC - 1, (y + h) * SC - 1], radius=4 * SC, fill=col(0xD8161D17))
    d.rounded_rectangle([x * SC, y * SC, (x + 2.5) * SC - 1, (y + h) * SC - 1], radius=int(1.2 * SC), fill=col(0xFFD6A94A))
    cv.flush()
    cv.img.alpha_composite(L)
    eb = tr(eyebrow_key, step_n, 7) if eyebrow_key in LANG else 'FIRST HUNT · %d / 7' % step_n
    cv.text(eb, x + 27, y + 5, 0xFFD6A94A, 'SMALL')
    for i in range(7):
        c = 0xFFD6A94A if i < step_n - 1 else (0xFFEFE8D7 if i == step_n - 1 else 0x55EFE8D7)
        cv.fill(px(x + W - 8 - (7 - i) * 6), px(y + 7), px(x + W - 8 - (7 - i) * 6 + 4), px(y + 11), c)
    cv.text(title, x + 27, y + 15, 0xFFEFE8D7, 'STRONG')
    icon = os.path.join(JAR, 'assets/frontierhunts/textures/item/wind_checker.png')
    if os.path.exists(icon):
        im = Image.open(icon).convert('RGBA').resize((18 * SC, 18 * SC), Image.NEAREST)
        cv.img.alpha_composite(im, (px(x + 5), px(y + 7)))


# ---------------------------------------------------------------- the painted scene
def scene(seed=7):
    rnd = random.Random(seed)
    W, H = GW * SC, GH * SC
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    for yy in range(H):  # sky: deep blue to a warm evening haze at the horizon
        t = yy / (H * 0.55)
        t = min(1.0, t)
        c = tuple(int(a + (b - a) * t ** 1.4) for a, b in zip((92, 136, 196), (226, 214, 188)))
        d.line([(0, yy), (W, yy)], fill=c + (255,))
    # far ridge (hazy blue), mid hills (green), forest edge, meadow foreground
    def ridge(base, amp, freq, color, step=6, seed2=0):
        r2 = random.Random(seed2)
        ph = [r2.random() * 6.28 for _ in range(4)]
        pts = [(0, H)]
        for x in range(0, W + step, step):
            y = base + amp * (0.5 * math.sin(x * freq + ph[0]) + 0.3 * math.sin(x * freq * 2.3 + ph[1]) + 0.2 * math.sin(x * freq * 5.1 + ph[2]))
            pts.append((x, int(y // 6 * 6)))
        pts.append((W, H))
        d.polygon(pts, fill=color)
    ridge(H * 0.47, 60, 0.004, (152, 166, 182, 255), seed2=1)
    ridge(H * 0.53, 40, 0.006, (112, 140, 120, 255), seed2=2)
    # the beam (behind the near hills): the soft gold column over the area, ahead and a little right
    bx, by0, by1 = int(W * 0.5 + 4 * SC * 1.03), int(H * 0.56), int(H * 0.08)
    beam = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    bd = ImageDraw.Draw(beam)
    for yy in range(by1, by0):
        f = (yy - by1) / (by0 - by1)
        top = smooth((f - 0.0) / 0.6)
        for half, alpha, c in ((14, 0.17, (255, 199, 97)), (4, 0.62, (255, 230, 158))):
            for dx in range(-half, half + 1):
                w = 1 - abs(dx) / (half + 1)
                bd.point((bx + dx, yy), fill=c + (int(255 * alpha * w * top),))
    img.alpha_composite(beam.filter(ImageFilter.GaussianBlur(1.2)))
    d = ImageDraw.Draw(img)
    ridge(H * 0.60, 26, 0.008, (84, 118, 70, 255), seed2=3)
    # a forest edge: blocky tree crowns
    for i in range(70):
        x = rnd.randint(-40, W)
        yb = int(H * 0.62 + rnd.randint(-10, 30))
        s = rnd.choice([30, 36, 42, 48])
        g = rnd.randint(52, 80)
        d.rectangle([x, yb - s * 2, x + s * 2, yb], fill=(36, g, 34, 255))
        d.rectangle([x + s // 2, yb - s * 3, x + s * 3 // 2, yb - s * 2], fill=(42, g + 8, 38, 255))
        d.rectangle([x + s - 6, yb, x + s + 6, yb + s], fill=(84, 62, 40, 255))
    # meadow: pixel grass
    for yy in range(int(H * 0.68), H, 6):
        for xx in range(0, W, 6):
            v = rnd.randint(-14, 14)
            t = (yy - H * 0.68) / (H * 0.32)
            d.rectangle([xx, yy, xx + 5, yy + 5], fill=(int(92 + v - 20 * t), int(146 + v - 30 * t), int(64 + v // 2), 255))
    for i in range(400):
        x, y = rnd.randint(0, W), rnd.randint(int(H * 0.7), H)
        d.rectangle([x, y - 12, x + 2, y], fill=(70, 120, 50, 255))
    return img


def hotbar(cv):
    x0, y0 = GW / 2 - 91, GH - 22
    L = Image.new('RGBA', cv.img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(L)
    d.rectangle([px(x0), px(y0), px(x0 + 182) - 1, px(y0 + 22) - 1], fill=(20, 20, 20, 150), outline=(140, 140, 140, 220), width=SC)
    for i in range(9):
        d.rectangle([px(x0 + 1 + i * 20), px(y0 + 1), px(x0 + 21 + i * 20) - 1, px(y0 + 21) - 1], outline=(90, 90, 90, 200), width=SC)
    d.rectangle([px(x0 - 1), px(y0 - 1), px(x0 + 23) - 1, px(y0 + 23) - 1], outline=(235, 235, 235, 255), width=SC)
    cv.flush()
    cv.img.alpha_composite(L)
    bow = os.path.join(JAR, 'assets/frontierhunts/textures/item/field_bow.png')
    if os.path.exists(bow):
        im = Image.open(bow).convert('RGBA').resize((16 * SC, 16 * SC), Image.NEAREST)
        cv.img.alpha_composite(im, (px(x0 + 3), px(y0 + 3)))
    # crosshair
    c = (240, 240, 240, 230)
    d2 = ImageDraw.Draw(cv.img)
    d2.rectangle([px(GW / 2) - 1, px(GH / 2) - 13, px(GW / 2) + 1, px(GH / 2) + 13], fill=c)
    d2.rectangle([px(GW / 2) - 13, px(GH / 2) - 1, px(GW / 2) + 13, px(GH / 2) + 1], fill=c)


def main():
    os.makedirs(OUT, exist_ok=True)
    img = scene()
    cv = Canvas(img)
    hotbar(cv)
    card(cv, 2, tr('firsthunt.frontierhunts.step.signs.title') if 'firsthunt.frontierhunts.step.signs.title' in LANG else 'Find deer sign')
    # facing north-east-ish, the area ahead and a little right, 212 m
    ribbon(cv, head=46.0, bearing=50.0, metres=210, since_ms=99999)
    cv.flush()
    img.convert('RGB').save(os.path.join(OUT, 'mock_hud.png'))
    # the states, cropped and doubled
    crops = []
    for name, fn in (('ahead, facing it', lambda c: ribbon(c, head=46.0, bearing=50.0, metres=210)),
                     ('ahead, to the left', lambda c: ribbon(c, head=80.0, bearing=50.0, metres=210)),
                     ('off to the right', lambda c: ribbon(c, head=300.0, bearing=50.0, metres=145)),
                     ('behind (left)', lambda c: ribbon(c, head=200.0, bearing=50.0, metres=85, edge_side=-1)),
                     ('arrived', lambda c: arrival(c, 'signs'))):
        im = scene()
        c2 = Canvas(im)
        fn(c2)
        c2.flush()
        box = (px(GW / 2 - 110), 0, px(GW / 2 + 110), px(46))
        crops.append((name, im.crop(box).resize(((box[2] - box[0]) * 2 // 3 * 2, (box[3] - box[1]) * 2 // 3 * 2), Image.LANCZOS)))
    w = crops[0][1].width
    sheet = Image.new('RGB', (w, sum(c.height + 40 for _, c in crops)), (24, 26, 24))
    y = 0
    dd = ImageDraw.Draw(sheet)
    lab = ImageFont.truetype(os.path.join(FONTS, 'inter_semibold.ttf'), 22)
    for name, c in crops:
        dd.text((10, y + 8), name, font=lab, fill=(220, 210, 180))
        sheet.paste(c.convert('RGB'), (0, y + 40))
        y += c.height + 40
    sheet.save(os.path.join(OUT, 'mock_hud_states.png'))


main()
