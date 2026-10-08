#!/usr/bin/env python3
"""[ledger] Offline mock of the rebuilt Expedition journal (client/ExpeditionScreen) for layout / text-fit review.

python3 tools/ledger/mock_expedition.py <repo> <fonts dir> <out prefix> [gui_w gui_h scale]
Mirrors ExpeditionScreen's geometry (panel, sidebar, header Next line, rows, cards, buttons) with a sample mid-campaign
snapshot, the real Inter fonts and the journal icon atlas. Not the real renderer: wrap/fit approximate Minecraft's.
"""
import json, os, re, sys
from PIL import Image, ImageDraw, ImageFont

ROOT, FONTS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
GW, GH, SC = (int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])) if len(sys.argv) > 6 else (427, 240, 2)
LANG = json.load(open(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/ledger.json')))
P = 'expedition.frontierhunts.ui.'
ATLAS = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/journal/icons.png')).convert('RGBA')
NAMES = re.findall(r'"([a-z_0-9]+)"', open(os.path.join(ROOT, 'src/com/formaworks/frontierhunts/journal/client/JournalIcons.java')).read()
                   .split('GENERATED-NAMES-BEGIN')[1].split('GENERATED-NAMES-END')[0])


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2), 'TITLE': ttf('inter_bold.ttf', 12.0)}
LH = {'SMALL': 8, 'BODY': 10, 'STRONG': 10, 'TITLE': 14}
PAPER, INK, INK_BROWN, MUTED, FAINT = 0xFFEAE3D0, 0xFF243A32, 0xFF3B2E20, 0xFF6A7266, 0xFF9A9A88
GOLD, GOLD_DARK, LEATHER, TRACK, GREEN, RED, RULE = 0xFFB99859, 0xFF8F6E2F, 0xFF49372B, 0xFFD3CCB6, 0xFF3E6E37, 0xFFA5281F, 0xFFC5C2AE
SIDE_TEXT, SIDE_MUTED, BUTTON, BUTTON_HOVER = 0xFFEFE8D7, 0xFFC9B99A, 0xFF2D4237, 0xFF516C56


def col(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255)


def L(k, fb='', *a):
    s = LANG.get(P + k, fb)
    try:
        return s % a if a else s.replace('%%', '%')
    except TypeError:
        return s


_t = ImageDraw.Draw(Image.new('RGBA', (4, 4)))


def width(s, size):
    return _t.textlength(s, font=F[size]) / SC


def fit(s, w, size):
    if width(s, size) <= w:
        return s
    while s and width(s + '…', size) > w:
        s = s[:-1]
    return s.rstrip() + '…'


def wrap(s, w, size):
    out, line = [], ''
    for word in s.split(' '):
        t = (line + ' ' + word).strip()
        if width(t, size) <= w or not line:
            line = t
        else:
            out.append(line)
            line = word
    out.append(line)
    return out


class C:
    def __init__(self):
        self.img = Image.new('RGBA', (GW * SC, GH * SC), (52, 70, 56, 255))
        self.d = ImageDraw.Draw(self.img, 'RGBA')

    def fill(self, x0, y0, x1, y1, c):
        if x1 > x0 and y1 > y0:
            self.d.rectangle([round(x0 * SC), round(y0 * SC), round(x1 * SC) - 1, round(y1 * SC) - 1], fill=col(c))

    def rect(self, x, y, w, h, r, c):
        if w > 0 and h > 0:
            self.d.rounded_rectangle([round(x * SC), round(y * SC), round((x + w) * SC) - 1, round((y + h) * SC) - 1], radius=r * SC, fill=col(c))

    def circle(self, cx, cy, r, c):
        self.d.ellipse([(cx - r) * SC, (cy - r) * SC, (cx + r) * SC, (cy + r) * SC], fill=col(c))

    def text(self, s, x, y, c, size='BODY'):
        self.d.text((x * SC, (y - 1) * SC), s, font=F[size], fill=col(c))

    def right(self, s, x, y, c, size='BODY'):
        self.text(s, x - width(s, size), y, c, size)

    def icon(self, name, x, y, size=16, bright=False, alpha=1.0):
        if name not in NAMES:
            self.rect(x + 2, y + 2, size - 4, size - 4, 2, 0xFF7A8A6A)
            return
        i = NAMES.index(name)
        im = ATLAS.crop(((i % 16) * 32, (i // 16) * 32 + (256 if bright else 0), (i % 16) * 32 + 32, (i // 16) * 32 + 32 + (256 if bright else 0)))
        px = round(size * SC)
        im = im.resize((px, px), Image.NEAREST if px % 32 == 0 else Image.BILINEAR)
        if alpha < 1:
            im.putalpha(im.getchannel('A').point(lambda v: int(v * alpha)))
        self.img.alpha_composite(im, (round(x * SC), round(y * SC)))

    def bar(self, x, y, w, h, f, track, fill):
        self.rect(x, y, w, h, h / 2, track)
        f = max(0, min(1, f))
        if f > 0:
            self.rect(x, y, max(h, w * f), h, h / 2, fill)

    def button(self, x, y, w, h, label, icon=None, active=True, primary=False):
        bg = 0xFF8E8A78 if not active else (0xFF3E5945 if primary else BUTTON)
        self.rect(x, y + 1, w, h, 2, 0x30000000)
        self.rect(x, y, w, h, 2, bg)
        if primary and active:
            self.rect(x, y, 2, h, 1, GOLD)
        lab = fit(label, w - (8 if icon is None else 24), 'SMALL')
        tw = width(lab, 'SMALL') + (0 if icon is None else 16)
        tx = x + (w - tw) / 2
        if icon:
            self.icon(icon, tx - 2, y + (h - 16) / 2, 16, alpha=1 if active else 0.55)
            tx += 16
        self.text(lab, tx, y + (h - 7) // 2, SIDE_TEXT if active else 0xFFE4E0D2, 'SMALL')

    def card(self, x, y, w, h, accent):
        self.rect(x - 3, y, w + 6, h, 3, 0x14000000)
        self.rect(x - 3, y, 2, h, 1, accent)


def notebook(c, x, y, w, h, side):
    c.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x80000000)
    c.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF413526)
    c.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF88704A)
    c.fill(x, y, x + w, y + h, PAPER)
    c.fill(x, y, x + side, y + h, LEATHER)
    for i in range(12):
        c.fill(x + side + i, y, x + side + i + 1, y + h, 0xFFB9A984 if i < 3 else 0xFFD8CDB0 if i < 8 else 0xFFE0D6BB)
    c.fill(x + side, y, x + side + 2, y + h, 0xFFA2804B)
    for k in range(8, h - 5, 7):
        c.fill(x + 3, y + k, x + 4, y + k + 3, 0xFF9C8667)
        c.fill(x + side - 5, y + k, x + side - 4, y + k + 3, 0xFF9C8667)


TABS = ['campaign', 'contracts', 'stores', 'training', 'lodge', 'ledger', 'seasons']
ICONS = ['campaign', 'contracts', 'equipment', 'training', 'lodge', 'ledger', 'seasons']
MISSION = ("II · Equipment for the valley", "Assemble equipment at the weapons workbench.", 'craft', 0, 1, 25)
HOW = {'craft': L('how.craft'), 'harvest': L('how.harvest'), 'longshot': L('how.longshot'), 'vital': L('how.vital'), 'fish': L('how.fish'),
       'group': L('how.group')}
CONTRACTS = [("Camp provisions", "Recover 2 whitetails in 20 minutes.", 'harvest', 30, 'butchery'),
             ("Long shot", "Recover a whitetail shot from at least 75 metres.", 'longshot', 45, 'marksmanship'),
             ("Clean kill", "Drop a whitetail with a heart or lung shot in 20 minutes.", 'vital', 35, 'marksmanship'),
             ("River delivery", "Land 3 fish in 20 minutes.", 'fish', 30, 'fish'),
             ("Group hunt", "Recover a whitetail with a party member within 96 metres.", 'group', 50, 'party')]


def render(tab):
    c = C()
    pw, ph = min(620, GW - 12), min(400, GH - 12)
    left, top = (GW - pw) // 2, (GH - ph) // 2
    side = 104 if pw < 470 else 130
    bx, bw = left + side + 18, pw - side - 36
    vt, vb = top + 56, top + ph - 10
    c.fill(0, 0, GW, GH, 0xB0101208)
    notebook(c, left, top, pw, ph, side)
    # sidebar
    c.fill(left + 9, top + 11, left + 11, top + 44, GOLD)
    c.text(L('brand1'), left + 16, top + 12, SIDE_MUTED, 'SMALL')
    c.text(fit(L('brand2'), side - 26, 'STRONG'), left + 16, top + 21, SIDE_TEXT, 'STRONG')
    c.icon('token', left + 15, top + 32, 12)
    c.text(fit(L('side.tokens', '', '142'), side - 34, 'SMALL'), left + 30, top + 35, GOLD, 'SMALL')
    tab_h = max(14, min(24, (ph - 52 - 66) // 7))
    for i, t in enumerate(TABS):
        ty = top + 52 + i * tab_h
        h = tab_h - 3
        sel = t == tab
        if sel:
            c.rect(left + 6, ty, side - 6, h, 2, PAPER)
        isz = 16 if h >= 18 else 12
        c.icon(ICONS[i], left + 10, ty + (h - isz) // 2 - (1 if sel else 0), isz, bright=sel)
        c.text(fit(L('tab.' + t), side - 36, 'BODY'), left + 30, ty + (h - 8) // 2, INK if sel else SIDE_TEXT)
        if side >= 120:
            c.right(str(i + 1), left + side - 12, ty + (h - 8) // 2, FAINT if sel else 0xFF8E7B62)
    by = top + ph - 46
    c.rect(left + 8, by, side - 18, 18, 2, BUTTON)
    c.rect(left + 8, by, 2, 18, 1, GOLD)
    c.icon('school', left + 12, by + 1)
    c.text(fit(L('side.school') + ' 5/7', side - 36, 'SMALL'), left + 30, by + 6, SIDE_TEXT, 'SMALL')
    hw = (side - 22) // 2
    for k, (ic, lab) in enumerate((('notes', L('side.journal')), ('assign', L('side.assignments')))):
        x0 = left + 8 + k * (hw + 4)
        c.rect(x0, by + 22, hw, 18, 2, BUTTON)
        c.icon(ic, x0 + 2, by + 23)
        c.text(fit(lab, hw - 18, 'SMALL'), x0 + 19, by + 28, SIDE_TEXT, 'SMALL')
    # header
    c.icon(ICONS[TABS.index(tab)], bx, top + 10)
    c.text(fit(L('title.' + tab), bw - 30, 'TITLE'), bx + 22, top + 13, INK, 'TITLE')
    ny = top + 30
    c.rect(bx - 3, ny - 2, left + pw - 16 - bx + 3, 16, 3, 0x18B99859)
    c.icon('equipment', bx, ny - 1, 14)
    nl = L('next.label')
    c.text(nl, bx + 18, ny + 3, GOLD_DARK, 'STRONG')
    c.text(fit(MISSION[0] + '  ·  0 / 1', bw - 24 - width(nl, 'STRONG') - 6, 'SMALL'), bx + 18 + width(nl, 'STRONG') + 6, ny + 3, INK, 'SMALL')
    c.fill(bx, top + 49, left + pw - 16, top + 50, RULE)
    c.rect(left + pw - 28, top + 8, 18, 18, 2, BUTTON)
    c.text('×', left + pw - 22, top + 12, SIDE_TEXT)
    k = C()
    k.img = Image.new('RGBA', c.img.size, (0, 0, 0, 0))
    k.d = ImageDraw.Draw(k.img, 'RGBA')
    y = vt

    def section(icon, text):
        nonlocal y
        k.icon(icon, bx, y + 4)
        k.text(fit(text.upper(), bw - 22, 'STRONG'), bx + 20, y + 9, INK, 'STRONG')
        k.fill(bx + 20, y + 20, bx + 60, y + 21, 0xFFB89B6E)
        y += 24

    def para(text, color=MUTED, size='SMALL', indent=0):
        nonlocal y
        lines = wrap(text, bw - indent, size)
        for l in lines:
            k.text(l, bx + indent, y + 1, color, size)
            y += LH[size] + 1
        y += 4

    if tab == 'campaign':
        det = wrap(HOW['craft'], bw - 30, 'SMALL')[:3]
        barY = 30 + len(det) * 9 + 4
        h = barY + 12 + 4
        k.card(bx, y, bw, h - 2, GOLD)
        k.icon('equipment', bx + 3, y + 6)
        k.text(L('next.eyebrow'), bx + 25, y + 6, GOLD_DARK, 'SMALL')
        k.text(fit(MISSION[0], bw - 30, 'STRONG'), bx + 25, y + 16, INK, 'STRONG')
        yy = y + 29
        for l in det:
            k.text(l, bx + 25, yy, MUTED, 'SMALL')
            yy += 9
        k.bar(bx + 25, y + barY + 3, bw - 30 - width('0 / 1', 'STRONG') - 8, 5, 0, TRACK, GOLD)
        k.right('0 / 1', bx + bw - 4, y + barY + 1, INK_BROWN, 'STRONG')
        y += h + 6
        k.text(fit(L('campaign.progress', '', 2, 4, 9), bw - 100, 'SMALL'), bx, y + 5, GOLD_DARK, 'SMALL')
        px = bx + bw - 9 * 9 - 6
        for i in range(9):
            cx = px + i * 9 + (3 if i >= 3 else 0) + (3 if i >= 6 else 0) + 3.5
            if i == 3:
                k.circle(cx, y + 9, 3.6, GOLD_DARK)
                k.circle(cx, y + 9, 2.4, PAPER)
            else:
                k.circle(cx, y + 9, 3, GREEN if i < 3 else 0xFFCFC7AF)
        y += 18
        section('campaign', L('campaign.report'))
        k.text(fit(MISSION[0], bw - 80, 'STRONG'), bx, y + 2, INK_BROWN, 'STRONG')
        rw = L('campaign.reward', '', 25)
        k.icon('token', bx + bw - width(rw, 'SMALL') - 14, y, 12)
        k.right(rw, bx + bw, y + 3, GOLD_DARK, 'SMALL')
        y += 14
        para(MISSION[1], INK, 'BODY')
        section('quill', L('campaign.last_entry'))
        para('“The first recovered deer is entered in the field ledger. Its trophy preserves the details of that animal and the shot.”', MUTED, 'SMALL', 4)
        section('party', L('campaign.party'))
        for s in ['Austin · same mission', 'Mara_K · mission 3']:
            k.circle(bx + 5, y + 6, 2.5, GREEN)
            k.text(fit(s, bw - 22, 'BODY'), bx + 14, y + 2, INK)
            y += 14
        para(L('campaign.party_note'))
    elif tab == 'contracts':
        story = wrap(CONTRACTS[0][1] + ' ' + HOW['harvest'], bw - 30, 'SMALL')
        h = 30 + len(story) * 9 + 34 + 24
        k.card(bx, y, bw, h - 2, GOLD)
        k.icon('contracts', bx + 3, y + 6)
        k.text(L('contract.active'), bx + 25, y + 6, GOLD, 'SMALL')
        k.text(fit(CONTRACTS[0][0], bw - 90, 'STRONG'), bx + 25, y + 16, INK, 'STRONG')
        rw = L('contract.reward', '', 30)
        k.icon('token', bx + bw - width(rw, 'SMALL') - 18, y + 4, 12)
        k.right(rw, bx + bw - 4, y + 7, GOLD_DARK, 'SMALL')
        yy = y + 29
        for l in story:
            k.text(l, bx + 25, yy, MUTED, 'SMALL')
            yy += 9
        b2 = bw - 30 - width('1 / 2', 'STRONG') - 8
        k.bar(bx + 25, yy + 4, b2, 5, 0.5, TRACK, GOLD)
        k.right('1 / 2', bx + bw - 4, yy + 2, INK_BROWN, 'STRONG')
        yy += 13
        k.icon('clock', bx + 25, yy - 2, 12)
        k.bar(bx + 41, yy + 3, b2 - 16, 3, 0.62, TRACK, 0xFF6F98AE)
        k.right(L('contract.left', '', '12:24'), bx + bw - 4, yy + 1, MUTED, 'SMALL')
        half = (bw - 36) // 2
        k.button(bx + 25, y + h - 25, half, 19, L('btn.collect'), 'token', active=False, primary=True)
        k.button(bx + 31 + half, y + h - 25, half, 19, L('btn.abandon'))
        y += h + 6
        section('contracts', L('contract.board'))
        para(L('contract.rules'))
        for i, (t, st, ev, tok, ic) in enumerate(CONTRACTS[:3]):
            story = wrap(st + ' ' + HOW[ev], bw - 30, 'SMALL')
            h = 20 + len(story) * 9 + 26
            k.card(bx, y + 2, bw, h - 4, GOLD if i == 0 else 0xFFB9A984)
            k.icon(ic, bx + 3, y + 6)
            k.text(fit(t, bw - 100, 'STRONG'), bx + 25, y + 8, INK, 'STRONG')
            rw = L('contract.reward', '', tok)
            k.icon('token', bx + bw - width(rw, 'SMALL') - 18, y + 5, 12)
            k.right(rw, bx + bw - 4, y + 8, GOLD_DARK, 'SMALL')
            yy = y + 20
            for l in story:
                k.text(l, bx + 25, yy, MUTED, 'SMALL')
                yy += 9
            k.button(bx + 25, y + h - 25, min(140, bw - 30), 19, L('btn.current') if i == 0 else L('btn.take'), active=False)
            y += h + 2
    elif tab == 'stores':
        k.rect(bx - 3, y + 2, bw + 6, 18, 3, 0x22A5281F)
        k.icon('lock', bx, y + 3)
        k.text(fit(L('stores.no_station'), bw - 26, 'SMALL'), bx + 22, y + 8, RED, 'SMALL')
        y += 26
        for i, (n, p) in enumerate([('Rifle Round  ×12', 8), ('Shotgun Shell  ×12', 8), ('Field First-Aid Kit', 10), ('Six Power Scope', 40), ('Thermal Scope', 85),
                                    ('Fishing Drag Kit', 20), ('Extended Magazine', 35), ('Scent Cover', 8)]):
            if i % 2 == 0:
                k.fill(bx - 3, y, bx + bw + 3, y + 22, 0x0E3B2E20)
            k.rect(bx + 2, y + 5, 12, 12, 2, 0xFF7A8A6A)
            k.text(fit(n, bw - 130, 'BODY'), bx + 22, y + 8, INK)
            pxx = bx + bw - 64
            k.right(str(p), pxx - 4, y + 7, INK_BROWN, 'STRONG')
            k.icon('token', pxx - 4 - width(str(p), 'STRONG') - 14, y + 5, 12)
            k.button(bx + bw - 58, y + 2, 58, 18, L('btn.buy'), active=False)
            y += 22
    elif tab == 'seasons':
        k.card(bx, y, bw, 36, GOLD)
        k.icon('leaf', bx + 3, y + 6)
        k.text(fit('October 14 · Reserve year 1', bw - 30, 'STRONG'), bx + 25, y + 5, INK, 'STRONG')
        k.text(fit(L('seasons.season', '', 'Fall', 58, 4), bw - 30, 'SMALL'), bx + 25, y + 16, MUTED, 'SMALL')
        k.bar(bx + 25, y + 27, bw - 30, 3, 0.58, TRACK, GOLD)
        y += 42
        section('seasons', L('seasons.chart'))
        nameW = min(90, bw // 3)
        cellW = max(9, (bw - nameW - 4) // 12)
        letters = L('seasons.month_letters').split(',')
        for i in range(12):
            cx = bx + nameW + i * cellW
            k.text(letters[i], cx + (cellW - width(letters[i], 'SMALL')) / 2, y + 2, INK if i == 9 else FAINT, 'SMALL')
        y += 12
        for sp, nm, rng in [('whitetail', 'Whitetail deer', (8, 11)), ('elk', 'Elk', (8, 10)), ('moose', 'Moose', (8, 9))]:
            k.icon('sp_' + sp, bx, y + 1)
            k.text(fit(nm, nameW - 22, 'BODY'), bx + 20, y + 5, INK)
            for i in range(12):
                cx = bx + nameW + i * cellW
                op = rng[0] <= i <= rng[1]
                k.rect(cx + 1, y + 4, cellW - 2, 10, 2, (GREEN if i == 9 else 0xFF8FAF78) if op else 0x18000000)
            y += 18
        y += 4
        section('contracts', L('seasons.tags'))
        para(L('seasons.tags_text'), INK)
        for sp, nm in [('whitetail', 'Whitetail deer'), ('elk', 'Elk')]:
            k.icon('sp_' + sp, bx, y + 4)
            k.text(fit(nm, bw - 170, 'STRONG'), bx + 20, y + 4, INK, 'STRONG')
            k.text(L('seasons.open'), bx + 20, y + 14, GREEN, 'SMALL')
            k.text(L('seasons.none'), bx + 26 + width(L('seasons.open'), 'SMALL'), y + 14, MUTED, 'SMALL')
            k.button(bx + bw - 100, y + 3, 100, 18, L('btn.tags'), 'token', active=False)
            y += 24
    elif tab == 'training':
        k.icon('star', bx, y + 3)
        k.text(L('training.xp', '', '340'), bx + 22, y + 4, INK, 'STRONG')
        k.right(L('training.points', '', 1), bx + bw, y + 5, GREEN, 'SMALL')
        k.bar(bx + 22, y + 17, bw - 22, 4, 0.4, TRACK, GOLD)
        k.text(fit(L('training.next_point', '', 60), bw // 2, 'SMALL'), bx + 22, y + 23, FAINT, 'SMALL')
        y += 35
        para(L('training.intro'))
        for i, (key, ic) in enumerate([('marksman', 'marksmanship'), ('tracker', 'tracking'), ('survivalist', 'survival')]):
            desc = wrap(L('training.%s.task' % key) + ' ' + L('training.%s.perk' % key), bw - 30, 'SMALL')
            run = i == 1
            h = 22 + len(desc) * 9 + (14 if run else 0) + 26
            k.card(bx, y + 2, bw, h - 4, GOLD if run else 0xFFB9A984)
            k.icon(ic, bx + 3, y + 6)
            k.text(fit(L('training.' + key), bw - 90, 'STRONG'), bx + 25, y + 8, INK, 'STRONG')
            for j in range(3):
                k.circle(bx + bw - 31 + j * 9 + 3.5, y + 12, 3, GOLD if j < 1 else 0xFFCFC7AF)
            yy = y + 21
            for l in desc:
                k.text(l, bx + 25, yy, MUTED, 'SMALL')
                yy += 9
            if run:
                ps = '1 / 3  ·  11:42'
                k.bar(bx + 25, yy + 4, bw - 30 - width(ps, 'SMALL') - 8, 4, 1 / 3, TRACK, GOLD)
                k.right(ps, bx + bw - 4, yy + 2, INK_BROWN, 'SMALL')
            k.button(bx + 25, y + h - 25, min(150, bw - 30), 19, L('btn.underway') if run else L('btn.start'), active=False)
            y += h + 2
    mask = Image.new('L', c.img.size, 0)
    ImageDraw.Draw(mask).rectangle([(bx - 6) * SC, vt * SC, (left + pw - 12) * SC, vb * SC], fill=255)
    clipped = Image.new('RGBA', c.img.size, (0, 0, 0, 0))
    clipped.paste(k.img, (0, 0), mask)
    c.img.alpha_composite(clipped)
    out = '%s_%s.png' % (OUT, tab)
    c.img.save(out)
    print('wrote', out, 'content height', y - vt)


for t in ['campaign', 'contracts', 'stores', 'training', 'seasons']:
    render(t)
