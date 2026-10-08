"""[1.2.9] Offline layout mock of the redesigned First Hunt page and the Handbook's START HERE + settings cards.
Mirrors the constants of FirstHuntScreen / PaperButton / HandbookScreen.firstHuntBanner / settingsRow with the real Inter
fonts from the jar. Layout and colour check only, not the real renderer.

python3 tools/onboard/mock_firsthunt.py <repo> <unzipped jar dir> <out dir> [scale]
"""
import glob, json, math, os, sys
from PIL import Image, ImageDraw, ImageFont

ROOT, JAR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SC = int(sys.argv[4]) if len(sys.argv) > 4 else 3
GW, GH = 640, 360
FONTS = os.path.join(JAR, 'assets/frontierhunts/font')
LANG = {}
for f in sorted(glob.glob(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/*.json'))):
    LANG.update(json.load(open(f)))


def tr(k, *a):
    s = LANG.get(k, k)
    for i, x in enumerate(a):
        s = s.replace('%%%d$s' % (i + 1), str(x))
    for x in a:
        s = s.replace('%s', str(x), 1)
    return s


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2),
     'TITLE': ttf('inter_bold.ttf', 12.0)}
LH = {'SMALL': 8, 'BODY': 10, 'STRONG': 10, 'TITLE': 14}

PAPER, INK, INK_BROWN, MUTED, GOLD, GOLD_DARK = 0xFFEAE3D0, 0xFF243A32, 0xFF3B2E20, 0xFF657064, 0xFFB99859, 0xFF8F6E2F
RULE, RED, GREEN = 0xFFC5C2AE, 0xFFA5281F, 0xFF3E6E37
BAND, BAND_DARK, BAND_TEXT, BAND_MUTED, BAND_GOLD = 0xFF263A30, 0xFF1C2B23, 0xFFF3ECDB, 0xFFA9B4A2, 0xFFD8BD88
CARD, CARD_EDGE = 0xFFF3EEDF, 0xFFD3C8AC


def col(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255)


class Canvas:
    def __init__(self, bg):
        self.img = Image.open(bg).convert('RGBA').resize((GW * SC, GH * SC)) if bg else Image.new('RGBA', (GW * SC, GH * SC), (70, 90, 70, 255))
        self.img.alpha_composite(Image.new('RGBA', self.img.size, (10, 14, 12, 120)))

    def layer(self):
        return Image.new('RGBA', self.img.size, (0, 0, 0, 0))

    def rect(self, x, y, w, h, r, c):
        if w <= 0 or h <= 0 or (c >> 24) == 0:
            return
        L = self.layer()
        ImageDraw.Draw(L).rounded_rectangle([x * SC, y * SC, (x + w) * SC - 1, (y + h) * SC - 1], radius=max(0, r * SC), fill=col(c))
        self.img.alpha_composite(L)

    def circle(self, cx, cy, r, c):
        self.rect(cx - r, cy - r, 2 * r, 2 * r, r, c)

    def line(self, x0, y0, x1, y1, wd, c):
        L = self.layer()
        ImageDraw.Draw(L).line([x0 * SC, y0 * SC, x1 * SC, y1 * SC], fill=col(c), width=max(1, round(wd * SC)))
        self.img.alpha_composite(L)

    def grad(self, x0, y0, x1, y1, c0, c1):
        L = self.layer()
        d = ImageDraw.Draw(L)
        for yy in range(int(y0 * SC), int(y1 * SC)):
            t = (yy - y0 * SC) / max(1, (y1 - y0) * SC)
            a = col(c0); b = col(c1)
            d.line([x0 * SC, yy, x1 * SC - 1, yy], fill=tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4)))
        self.img.alpha_composite(L)

    def text(self, s, x, y, c, size='BODY'):
        L = self.layer()
        ImageDraw.Draw(L).text((x * SC, y * SC), s, font=F[size], fill=col(c))
        self.img.alpha_composite(L)
        return width(s, size)

    def center(self, s, cx, y, c, size):
        self.text(s, cx - width(s, size) / 2, y, c, size)

    def right(self, s, rx, y, c, size):
        self.text(s, rx - width(s, size), y, c, size)

    def para(self, s, x, y, w, c, size, gap):
        yy = y
        for l in wrap(s, w, size):
            self.text(l, x, yy, c, size)
            yy += LH[size] + gap
        return yy - y

    def icon(self, path, x, y, s):
        if os.path.exists(path):
            im = Image.open(path).convert('RGBA').resize((int(s * SC), int(s * SC)), Image.NEAREST)
            self.img.alpha_composite(im, (int(x * SC), int(y * SC)))


_m = ImageDraw.Draw(Image.new('RGBA', (4, 4)))


def width(s, size):
    return _m.textlength(s, font=F[size]) / SC


def wrap(s, w, size):
    out, line = [], ''
    for word in s.split(' '):
        t = (line + ' ' + word).strip()
        if width(t, size) > w and line:
            out.append(line)
            line = word
        else:
            line = t
    if line:
        out.append(line)
    return out


def fit(s, w, size):
    if width(s, size) <= w:
        return s
    while s and width(s + '…', size) > w:
        s = s[:-1]
    return s.rstrip() + '…'


def tick(cv, cx, cy, c, k=1.0):
    for i in range(5):
        cv.rect(cx + (-3.2 + i * 0.55) * k, cy + (-0.2 + i * 0.55) * k, 1.3 * k, 1.3 * k, 0.3, c)
    for i in range(9):
        cv.rect(cx + (-0.9 + i * 0.55) * k, cy + (2.0 - i * 0.7) * k, 1.3 * k, 1.3 * k, 0.3, c)


def chevron(cv, cx, cy, r, c):
    cv.line(cx - r * 0.55, cy - r, cx + r * 0.45, cy, 1.3, c)
    cv.line(cx + r * 0.45, cy, cx - r * 0.55, cy + r, 1.3, c)


def button(cv, x, y, w, h, label, look, arrow=False, active=True):
    if look == 'GOLD':
        bg, fg, edge = (GOLD if active else 0xFFC9BC9A), (0xFF2B2014 if active else 0xFF8A8270), (0xFF8F6E2F if active else 0xFFB3A784)
    elif look == 'GREEN':
        bg, fg, edge = 0xFF2D4237, 0xFFF3ECDB, 0xFF1F2E26
    else:
        bg, fg, edge = PAPER, INK, 0xFFB5AC92
    if look != 'OUTLINE' and active:
        cv.rect(x, y + 1.5, w, h, 4, 0x30000000)
    cv.rect(x, y, w, h, 4, edge)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 3, bg)
    if look == 'GOLD' and active:
        cv.rect(x + 2, y + 1, w - 4, 1.5, 0, 0x50FFFFFF)
    aw = 10 if arrow else 0
    tw = width(label, 'STRONG') + aw
    tx, ty = x + (w - tw) / 2, y + (h - 9) / 2 + 0.5
    cv.text(label, tx, ty, fg, 'STRONG')
    if arrow:
        chevron(cv, tx + tw - 4, y + h / 2, 3, fg)


def bwidth(label, arrow, mn):
    return max(mn, width(label, 'STRONG') + 20 + (10 if arrow else 0))


def compass(cv, cx, cy, r, ang):
    cv.circle(cx, cy, r, 0xFF2D4237)
    cv.circle(cx, cy, r - 1.5, 0xFFEDE3C9)
    for i in range(8):
        a = i * math.pi / 4
        l = 2.6 if i % 2 == 0 else 1.6
        ox, oy = math.sin(a), -math.cos(a)
        cv.line(cx + ox * (r - 2.5 - l), cy + oy * (r - 2.5 - l), cx + ox * (r - 2.5), cy + oy * (r - 2.5), 0.8, 0xFF9C8B68)
    sx, sy = math.sin(ang), -math.cos(ang)
    px, py = -sy, sx
    tip = (cx + sx * (r - 3.5), cy + sy * (r - 3.5))
    tail = (cx - sx * (r - 6), cy - sy * (r - 6))
    for k in range(-6, 7):
        f = k / 6 * 3.2
        cv.line(tip[0], tip[1], cx + px * f, cy + py * f, 0.9, 0xFFC08A2E)
        cv.line(tail[0], tail[1], cx + px * f * 0.8, cy + py * f * 0.8, 0.9, 0xFF5B6B60)
    cv.circle(cx, cy, 1.6, 0xFF2B2014)


STEPS = ['permit', 'signs', 'wind', 'shot', 'track', 'harvest', 'claim']
ICON = {'permit': 'hunting_licence', 'harvest': 'skinning_tool'}


def first_hunt(st, out, rules=False):
    cv = Canvas(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/welcome.png'))
    w, h = 470, 330
    left, top = (GW - w) // 2, (GH - h) // 2
    band = 46 if rules else 82
    foot = top + h - 30
    for i in range(6, 0, -1):
        s = 7 * i / 6
        cv.rect(left - s, top - s + 7 * 0.35, w + 2 * s, h + 2 * s, 6 + s, int(34 * (1 - (i - 1) / 6)) << 24)
    cv.rect(left - 2, top - 2, w + 4, h + 4, 7, 0xFF41352A)
    cv.rect(left - 1, top - 1, w + 2, h + 2, 6.5, 0xFF88704A)
    cv.rect(left, top, w, h, 6, PAPER)
    cv.rect(left, top, w, band, 6, BAND)
    cv.rect(left, top + band - 8, w, 8, 0, BAND)
    cv.grad(left, top + band // 2, left + w, top + band, 0x00000000, 0x30000000)
    cv.rect(left, top + band, w, 1, 0, GOLD)
    cv.rect(left, top + band + 1, w, 2, 0, 0x18000000)
    cv.rect(left + 14, foot, w - 28, 1, 0, RULE)
    x, y, tw = left + 14, top + 11, w - 28
    cv.text(tr('firsthunt.frontierhunts.screen.eyebrow'), x, y, BAND_GOLD, 'SMALL')
    by = foot + 6
    right = left + w - 14
    done_w = bwidth('Done', False, 64)
    if rules:
        cv.text(tr('firsthunt.frontierhunts.rules.title'), x, y + 11, BAND_TEXT, 'TITLE')
        keys = ['before', 'get', 'tag', 'knife']
        icons = ['hunting_licence', 'deer_tag', 'deer_tag', 'skinning_tool']
        yy = top + band + 12
        hs = []
        for k in keys:
            body = tr('firsthunt.frontierhunts.rules.' + k, *(['K', 6] if k == 'get' else ['Right Button']))
            hs.append(18 + len(wrap(body, tw - 48, 'SMALL')) * 9 + 6)
        spare = max(0, (foot - 6 - yy) - sum(hs) - 6 * 3) // 4
        for i, k in enumerate(keys):
            ch = hs[i] + min(spare, 6)
            cv.rect(x, yy, tw, ch, 5, CARD_EDGE)
            cv.rect(x + 1, yy + 1, tw - 2, ch - 2, 4, CARD)
            cv.circle(x + 18, yy + 16, 11, 0xFF2D4237)
            cv.icon(os.path.join(JAR, 'assets/frontierhunts/textures/item/%s.png' % icons[i]), x + 10, yy + 8, 16)
            cv.text('%d  %s' % (i + 1, tr('firsthunt.frontierhunts.rules.%s.head' % k)), x + 38, yy + 6, INK, 'STRONG')
            body = tr('firsthunt.frontierhunts.rules.' + k, *(['K', 6] if k == 'get' else ['Right Button']))
            cv.para(body, x + 38, yy + 18, tw - 48, INK_BROWN, 'SMALL', 1)
            yy += ch + 6
        button(cv, x, by, bwidth('Back', False, 70), 20, 'Back', 'OUTLINE')
        button(cv, right - done_w, by, done_w, 20, 'Done', 'GREEN')
        cv.img.save(out)
        return
    cv.text(tr('firsthunt.frontierhunts.screen.start'), x, y + 11, BAND_TEXT, 'TITLE')
    pill = tr('firsthunt.frontierhunts.screen.reward', 40)
    pw = width(pill, 'SMALL') + 22
    cv.rect(right - pw, y + 2, pw, 14, 7, 0x40D8BD88)
    cv.circle(right - pw + 8, y + 9, 3.2, BAND_GOLD)
    cv.circle(right - pw + 8, y + 9, 1.6, BAND)
    cv.text(pill, right - pw + 15, y + 5, BAND_GOLD, 'SMALL')
    # trail
    ty = top + 54
    cell = tw / 7
    cur = st['step']
    for i in range(6):
        x0, x1 = x + cell * (i + 0.5) + 8, x + cell * (i + 1.5) - 8
        cv.rect(x0, ty - 0.75, x1 - x0, 1.5, 0.75, BAND_GOLD if st['done'] >> i & 1 else 0x40F3ECDB)
    for i in range(7):
        cx = x + cell * (i + 0.5)
        d = st['done'] >> i & 1
        if i == cur:
            cv.circle(cx, ty, 10.5, 0x48D8BD88)
            cv.circle(cx, ty, 7, BAND_GOLD)
            cv.circle(cx, ty, 5.6, BAND_DARK)
        elif d:
            cv.circle(cx, ty, 7, BAND_GOLD)
        else:
            cv.circle(cx, ty, 7, 0x50F3ECDB)
            cv.circle(cx, ty, 6, BAND)
        if d and i != cur:
            tick(cv, cx, ty, BAND_DARK)
        else:
            cv.center(str(i + 1), cx + 0.5, ty - 3.5, BAND_GOLD if i == cur else BAND_MUTED, 'SMALL')
        cv.center(tr('firsthunt.frontierhunts.step.%s.short' % STEPS[i]), cx, ty + 11, BAND_TEXT if i == cur else BAND_GOLD if d else BAND_MUTED, 'SMALL')
    # step card
    body = top + band + 12
    step = STEPS[cur]
    ch = 64
    cv.rect(x, body, tw, ch, 5, 0xFFDDCB9E)
    cv.rect(x + 1, body + 1, tw - 2, ch - 2, 4, 0xFFF1E6C8)
    cv.rect(x + 1, body + 1, 3.5, ch - 2, 1.5, GOLD)
    cv.circle(x + 24, body + 24, 14, 0xFF2D4237)
    cv.circle(x + 24, body + 24, 12.5, 0xFF3E5945)
    cv.icon(os.path.join(JAR, 'assets/frontierhunts/textures/item/%s.png' % ICON.get(step, 'deer_tag')), x + 15, body + 15, 18)
    tx = x + 46
    cv.text(tr('firsthunt.frontierhunts.screen.step_eyebrow', cur + 1, 7), tx, body + 8, GOLD_DARK, 'SMALL')
    cv.text(tr('firsthunt.frontierhunts.step.%s.title' % step), tx, body + 18, INK, 'STRONG')
    cv.para(tr('firsthunt.frontierhunts.step.%s.how' % step, 40, 6, 'K'), tx, body + 31, tw - 58, INK_BROWN, 'SMALL', 1)
    # cards
    ct = top + band + 86
    chh = foot - 8 - ct
    cw = (w - 28 - 10) // 2
    for cx0, title in ((x, tr('firsthunt.frontierhunts.screen.licence')), (x + cw + 10, tr('firsthunt.frontierhunts.screen.area'))):
        cv.rect(cx0, ct, cw, chh, 5, CARD_EDGE)
        cv.rect(cx0 + 1, ct + 1, cw - 2, chh - 2, 4, CARD)
        cv.text(title.upper(), cx0 + 10, ct + 8, GOLD_DARK, 'SMALL')
        cv.rect(cx0 + 10, ct + 18, cw - 20, 1, 0, RULE)
    ry = ct + 25
    rows = [(st['lic'], 'Hunting licence', 'valid this season' if st['lic'] else tr('firsthunt.frontierhunts.licence.academy_short', st['academy'], 6)),
            (st['tag'], 'Deer tag', tr('firsthunt.frontierhunts.licence.tag_ok') if st['tag'] else tr('firsthunt.frontierhunts.licence.tag_missing')),
            (st['open'], 'Deer season', tr('firsthunt.frontierhunts.licence.open_short') if st['open'] else tr('firsthunt.frontierhunts.licence.closed_short', 12))]
    for ok, what, state in rows:
        cx, cy = x + 16, ry + 6
        cv.circle(cx, cy, 6, 0x2A3E6E37 if ok else 0x22A5281F)
        if ok:
            tick(cv, cx + 0.3, cy, GREEN)
        else:
            cv.line(cx - 2.4, cy - 2.4, cx + 2.4, cy + 2.4, 1.3, RED)
            cv.line(cx + 2.4, cy - 2.4, cx - 2.4, cy + 2.4, 1.3, RED)
        cv.text(what, x + 27, ry + 2, INK, 'SMALL')
        cv.right(state, x + 10 + cw - 20, ry + 2, MUTED if ok else RED, 'SMALL')
        ry += 15
    todo = None
    if not st['lic']:
        todo = tr('firsthunt.frontierhunts.licence.route_academy', 'K', st['academy'], 6)
    elif not st['tag']:
        todo = tr('firsthunt.frontierhunts.licence.route_tag', 12)
    elif not st['open']:
        todo = tr('firsthunt.frontierhunts.licence.season_note', 'Sep - Jan')
    if todo:
        ry += 3
        lines = wrap(todo, cw - 28, 'SMALL')
        n = max(1, min(len(lines), (ct + chh - 6 - ry) // 9))
        cv.rect(x + 10, ry, cw - 20, n * 9 + 6, 3, 0x1EB99859)
        for i in range(n):
            cv.text(lines[i], x + 15, ry + 3 + i * 9, INK_BROWN, 'SMALL')
    wx = x + cw + 10
    compass(cv, wx + 26, ct + 42, 15, st['bearing'])
    used = cv.para(tr('firsthunt.frontierhunts.area.away', 114, 'south'), wx + 48, ct + 26, cw - 58, INK, 'STRONG', 1)
    cv.text(tr('firsthunt.frontierhunts.screen.compass'), wx + 48, ct + 27 + used, MUTED, 'SMALL')
    used = max(used + 10, 36)
    ly = ct + 25 + used + 4
    left_ = ct + chh - 28 - ly
    for i, l in enumerate(wrap(tr('firsthunt.frontierhunts.advice.1'), cw - 20, 'SMALL')):
        if (i + 1) * 9 > left_:
            break
        cv.text(l, wx + 10, ly + i * 9, INK_BROWN, 'SMALL')
    aw = min(cw - 20, bwidth('Find another area', False, 100))
    button(cv, wx + cw - 10 - aw, ct + chh - 24, aw, 16, 'Find another area', 'OUTLINE')
    rw = bwidth('Hunting rules', False, 90)
    button(cv, x, by, rw, 20, 'Hunting rules', 'OUTLINE')
    button(cv, x + rw + 6, by, bwidth('Hide steps', False, 80), 20, 'Hide steps', 'OUTLINE')
    claim = tr('firsthunt.frontierhunts.button.claim', 40)
    cwid = bwidth(claim, True, 110)
    button(cv, right - done_w - 6 - cwid, by, cwid, 20, claim, 'GOLD', True, active=cur == 6)
    button(cv, right - done_w, by, done_w, 20, 'Done', 'GREEN')
    cv.img.save(out)


def handbook_top(out, preset='Vanilla', key='F10'):
    cv = Canvas(None)
    cv.rect(0, 0, GW, GH, 0, PAPER)
    x, y, bw = 40, 40, 440
    cv.text('FRONTIER HANDBOOK', x, y - 26, GOLD_DARK, 'SMALL')
    cv.text('Your first hour', x, y - 16, INK, 'TITLE')
    ch = 70
    cv.rect(x - 3, y - 3, bw + 6, ch + 6, 8, 0x40D6A94A)
    cv.rect(x - 1, y - 1, bw + 2, ch + 2, 6, 0xFFB99859)
    cv.rect(x, y, bw, ch, 5, 0xFF263A30)
    cv.grad(x + 2, y + ch / 2, x + bw - 2, y + ch - 2, 0x00000000, 0x28000000)
    tab = tr('onboard.frontierhunts.start_here')
    tabw = width(tab, 'SMALL') + 12
    cv.rect(x + 10, y + 8, tabw, 11, 5.5, 0xFFD6A94A)
    cv.text(tab, x + 16, y + 10, 0xFF2B2014, 'SMALL')
    cv.text(tr('firsthunt.frontierhunts.banner.eyebrow'), x + 16 + tabw, y + 10, 0xFFD8BD88, 'SMALL')
    label = tr('firsthunt.frontierhunts.banner.continue')
    btnw, btnh = width(label, 'STRONG') + 30, 24
    bx, by = x + bw - 12 - btnw, y + (ch - btnh) / 2
    cv.rect(bx, by + 1.5, btnw, btnh, 5, 0x50000000)
    cv.rect(bx, by, btnw, btnh, 5, 0xFFD6A94A)
    cv.rect(bx + 2, by + 1, btnw - 4, 1.5, 0, 0x60FFFFFF)
    cv.text(label, bx + 10, by + 8, 0xFF2B2014, 'STRONG')
    chevron(cv, bx + btnw - 12, by + btnh / 2, 3.4, 0xFF2B2014)
    for i, a in enumerate((90, 180, 255)):
        chevron(cv, bx - 16 - 18 + i * 7 + 1.5, by + btnh / 2, 3, a << 24 | 0xD6A94A)
    cv.text(tr('firsthunt.frontierhunts.screen.step', 1, 7, tr('firsthunt.frontierhunts.step.permit.title')), x + 12, y + 25, 0xFFF4EEDD, 'TITLE')
    for i in range(7):
        cv.rect(x + 12 + i * 11, y + 47, 8, 4, 2, 0xFFF4EEDD if i == 0 else 0x40F4EEDD)
    cv.text(tr('firsthunt.frontierhunts.banner.sub', 40), x + 12 + 77 + 6, y + 45, 0xFFA9B4A2, 'SMALL')
    y += ch + 8
    ch = 34
    cv.rect(x, y, bw, ch, 5, 0xFF9DB0BC)
    cv.rect(x + 1, y + 1, bw - 2, ch - 2, 4, 0xFFEDF0EC)
    cv.circle(x + 18, y + ch / 2, 11, 0xFF2F5F80)
    for i, k in enumerate((15.5, 21.0, 17.5)):
        ly = y + ch / 2 - 4.5 + i * 4.5
        cv.rect(x + 12, ly - 0.6, 12, 1.2, 0.6, 0xFFD9E6EE)
        cv.circle(x + k, ly, 1.9, 0xFFFFFFFF)
    rx = x + bw - 12
    chevron(cv, rx - 2, y + ch / 2, 3.2, 0xFF2F5F80)
    rx -= 14
    kw = max(18, width(key, 'STRONG') + 10)
    kx, ky = rx - kw, y + (ch - 18) / 2
    cv.rect(kx, ky, kw, 18, 3.5, 0xFF4A5560)
    cv.rect(kx + 1, ky + 1, kw - 2, 14.5, 3, 0xFFF7F7F2)
    cv.center(key, kx + kw / 2, ky + 4.5, 0xFF2B3238, 'STRONG')
    press = tr('onboard.frontierhunts.settings.press')
    pw0 = width(press, 'SMALL')
    cv.text(press, kx - 5 - pw0, y + ch / 2 - 3.5, MUTED, 'SMALL')
    pr = kx - 5 - pw0 - 8
    pw = width(preset, 'SMALL') + 12
    van = preset == 'Vanilla'
    cv.rect(pr - pw, y + (ch - 12) / 2, pw, 12, 6, 0xFF8A8F86 if van else 0xFFD6A94A)
    cv.text(preset, pr - pw + 6, y + (ch - 12) / 2 + 2.5, 0xFFF7F7F2 if van else 0xFF2B2014, 'SMALL')
    cv.text(tr('onboard.frontierhunts.settings.title'), x + 36, y + 7, INK, 'STRONG')
    cv.text(tr('onboard.frontierhunts.settings.sub'), x + 36, y + 19, MUTED, 'SMALL')
    y += ch + 10
    # the next-step card, quiet while the first hunt is on
    h = 64
    cv.rect(x, y, bw, h, 4, 0xFFD8CFB6)
    cv.rect(x + 1, y + 1, bw - 2, h - 2, 3.5, 0xFFF1EBDB)
    cv.circle(x + 20, y + 22, 12, 0x30B99859)
    cv.text('NEXT · STEP 1 OF 8', x + 40, y + 7, GOLD_DARK, 'SMALL')
    cv.text('Make a crafting table', x + 40, y + 17, INK, 'STRONG')
    cv.para('Punch a tree for logs, turn them into planks and put four planks in a square.', x + 40, y + 30, bw - 46, INK_BROWN, 'BODY', 1)
    cv.img.save(out)


os.makedirs(OUT, exist_ok=True)
first_hunt({'step': 0, 'done': 0, 'lic': False, 'tag': False, 'open': False, 'academy': 2, 'bearing': 2.6}, os.path.join(OUT, 'firsthunt_permit.png'))
first_hunt({'step': 3, 'done': 0b111, 'lic': True, 'tag': True, 'open': True, 'academy': 6, 'bearing': -0.7}, os.path.join(OUT, 'firsthunt_shot.png'))
first_hunt({'step': 3, 'done': 0b111, 'lic': True, 'tag': True, 'open': True, 'academy': 6, 'bearing': -0.7}, os.path.join(OUT, 'firsthunt_rules.png'), rules=True)
handbook_top(os.path.join(OUT, 'handbook_top.png'))
print('ok')
