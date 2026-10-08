#!/usr/bin/env python3
"""[hunts] Offline layout mock of the species-hunt pages in the Hunter's Journal (text fit / overlap review, not the real
renderer): the species table with hunt pips, an open hunt card, and the Checklist's Hunts section. Mirrors the
constants in client/JournalScreen + hunts/client/HuntCardView, real Inter fonts and the real icon atlases.

python3 tools/hunts/mock_card.py <repo> <fonts dir> <out prefix> [gui_w gui_h scale] [species]
"""
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT, FONTS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
GW, GH, SC = (int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])) if len(sys.argv) > 6 else (854, 480, 2)
SPECIES = sys.argv[7] if len(sys.argv) > 7 else 'elk'
LANG = {}
for f in ('journal.json', 'hunts.json'):
    LANG.update(json.load(open(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json', f))))
ENT = {'whitetail': 'Whitetail', 'elk': 'Elk', 'moose': 'Moose', 'black_bear': 'Black Bear', 'grizzly': 'Grizzly Bear', 'polar_bear': 'Polar Bear',
       'boar': 'Wild Boar', 'bison': 'Bison', 'pronghorn': 'Pronghorn', 'coyote': 'Coyote', 'wolf': 'Gray Wolf', 'cougar': 'Cougar', 'lion': 'Lion',
       'panther': 'Black Panther', 'cheetah': 'Cheetah', 'grouse': 'Ruffed Grouse', 'duck': 'Mallard'}
for k, v in ENT.items():
    LANG['entity.frontierhunts.' + k] = v
MS = json.load(open(os.path.join(ROOT, 'docs/ws/hunts/milestones.json')))


def atlas(path, java):
    im = Image.open(os.path.join(ROOT, path)).convert('RGBA')
    names = re.findall(r'"([a-z_0-9]+)"', open(os.path.join(ROOT, java)).read().split('GENERATED-NAMES-BEGIN')[1].split('GENERATED-NAMES-END')[0])
    return im, names


J_ATLAS, J_NAMES = atlas('patch/assets/frontierhunts/textures/gui/journal/icons.png', 'src/com/formaworks/frontierhunts/journal/client/JournalIcons.java')
H_ATLAS, H_NAMES = atlas('patch/assets/frontierhunts/textures/gui/journal/hunts_icons.png', 'src/com/formaworks/frontierhunts/hunts/client/HuntIcons.java')
H_TO_J = {'reticle': 'marksmanship', 'glass': 'glass', 'antlers': 'antlers', 'boot': 'boot', 'eye': 'stalking', 'stalk': 'stalking'}


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2),
     'TITLE': ttf('inter_bold.ttf', 12.0)}
PAPER, INK, INK_BROWN, MUTED, FAINT = 0xFFEAE3D0, 0xFF243A32, 0xFF3B2E20, 0xFF6A7266, 0xFF9A9A88
GOLD, GOLD_DARK, LEATHER, TRACK, GREEN, RULE = 0xFFB99859, 0xFF8F6E2F, 0xFF49372B, 0xFFD3CCB6, 0xFF3E6E37, 0xFFC5C2AE
SIDE_TEXT, SIDE_MUTED, BUTTON = 0xFFEFE8D7, 0xFFC9B99A, 0xFF2D4237
TIER = {'scout': 0xFF5F7A52, 'take': 0xFF8F6E2F, 'technique': 0xFF3F6278, 'quality': 0xFF7D4F2A, 'master': 0xFFB99859}


def col(argb):
    return ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255 or 255)


def tr(k, *a):
    s = LANG.get(k, k)
    s = re.sub(r'%(\d)\$s', lambda m: '{%d}' % (int(m.group(1)) - 1), s)
    i = 0
    while '%s' in s:
        s = s.replace('%s', '{%d}' % i, 1)
        i += 1
    return s.format(*[str(x) for x in a]) if a else s


_tmp = ImageDraw.Draw(Image.new('RGBA', (4, 4)))


def width(s, size):
    return _tmp.textlength(s, font=F[size]) / SC


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


class Canvas:
    def __init__(self):
        self.img = Image.new('RGBA', (GW * SC, GH * SC), (52, 70, 56, 255))
        self.d = ImageDraw.Draw(self.img, 'RGBA')
        self.boxes = []

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
        self.boxes.append((x, y, x + width(s, size), y + 8, s))

    def right(self, s, x, y, c, size='BODY'):
        self.text(s, x - width(s, size), y, c, size)

    def icon(self, name, x, y, size=16, alpha=1.0, hunt=False):
        if hunt and name in H_TO_J:
            name, hunt = H_TO_J[name], False
        im, names, per = (H_ATLAS, H_NAMES, 8) if hunt else (J_ATLAS, J_NAMES, 16)
        if name not in names:
            self.rect(x + 2, y + 2, size - 4, size - 4, 2, 0xFFFF00FF)
            return
        i = names.index(name)
        cell = im.crop(((i % per) * 32, (i // per) * 32, (i % per) * 32 + 32, (i // per) * 32 + 32))
        px = size * SC
        cell = cell.resize((px, px), Image.NEAREST if px % 32 == 0 else Image.BILINEAR)
        if alpha < 1:
            cell.putalpha(cell.getchannel('A').point(lambda v: int(v * alpha)))
        self.img.alpha_composite(cell, (round(x * SC), round(y * SC)))


def notebook(c, x, y, w, h, side):
    c.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x80000000)
    c.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF413526)
    c.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF88704A)
    c.fill(x, y, x + w, y + h, PAPER)
    c.fill(x, y, x + side, y + h, LEATHER)
    for i in range(12):
        c.fill(x + side + i, y, x + side + i + 1, y + h, 0xFFB9A984 if i < 3 else 0xFFD8CDB0 if i < 8 else 0xFFE0D6BB)


# sample record: some species seen / taken, elk half done
DONE = {'hunt_elk_glass', 'hunt_elk_clean', 'hunt_whitetail_cam', 'hunt_whitetail_clean', 'hunt_whitetail_called', 'hunt_coyote_seen', 'hunt_grouse_flush',
        'hunt_grouse_take', 'hunt_duck_seen', 'hunt_duck_take', 'hunt_duck_spread', 'hunt_duck_wing', 'hunt_duck_master', 'hunt_moose_seen', 'hunt_boar_cam'}
COUNTS = {'hunt.grouse.wing': 2, 'hunt.grouse.limit': 1, 'hunt.coyote.master': 1}
LOG = {'whitetail': (14, 6, 3, '138"'), 'elk': (9, 1, 1, '286 kg'), 'moose': (1, 0, 0, '—'), 'coyote': (2, 0, 0, '—'), 'grouse': (5, 0, 2, '0.6 kg'),
       'duck': (11, 0, 6, '1.3 kg'), 'boar': (0, 2, 0, '—')}
ORDER = ['whitetail', 'elk', 'moose', 'black_bear', 'grizzly', 'polar_bear', 'boar', 'bison', 'pronghorn', 'coyote', 'wolf', 'cougar', 'lion', 'panther',
         'cheetah', 'grouse', 'duck']


class Rows:
    def __init__(self):
        self.rows = []
        self.h = 0

    def add(self, h, fn):
        self.rows.append((self.h, h, fn))
        self.h += h


def section(R, text):
    R.add(20, lambda c, x, y, w: (c.text(text.upper(), x, y + 6, INK, 'STRONG'), c.fill(x, y + 17, x + min(36, w), y + 18, 0xFFB89B6E)))


def para(R, text, bw, color, size='SMALL'):
    lines = wrap(text, bw, size)
    lh = (8 if size == 'SMALL' else 10) + 2

    def draw(c, x, y, w):
        for i, l in enumerate(lines):
            c.text(l, x, y + i * lh, color, size)
    R.add(len(lines) * lh + 3, draw)


def ms_of(sp):
    return [m for m in MS if m['species'] == sp]


def pips(c, sp, right, y):
    ms = ms_of(sp)
    if ms[-1]['id'] in DONE:
        c.icon('medal', right - 12, y - 4, 12, hunt=True)
        return
    for i, m in enumerate(ms):
        cx = right - (len(ms) - i) * 6 + 2.5
        if m['id'] in DONE:
            c.circle(cx, y + 2.5, 2.4, GOLD if i == len(ms) - 1 else GREEN)
        else:
            c.circle(cx, y + 2.5, 2.4, 0xFF9C9480)
            c.circle(cx, y + 2.5, 1.5, PAPER)


def card(R, bw):
    sp = SPECIES
    ms = ms_of(sp)
    order = ORDER
    prev, nxt = order[(order.index(sp) - 1) % len(order)], order[(order.index(sp) + 1) % len(order)]
    back = '‹ ' + tr('hunts.frontierhunts.card.back')
    bwid = width(back, 'SMALL') + 14
    ps, ns = fit('‹ ' + ENT[prev], 92, 'SMALL'), fit(ENT[nxt] + ' ›', 92, 'SMALL')
    if bwid + width(ps, 'SMALL') + width(ns, 'SMALL') + 36 > bw:
        ps, ns = '‹', '›'
    pw, nw = max(16, width(ps, 'SMALL') + 12), max(16, width(ns, 'SMALL') + 12)

    def nav(c, x, y, w):
        c.rect(x, y + 2, bwid, 15, 2, BUTTON)
        c.text(back, x + 7, y + 6, SIDE_TEXT, 'SMALL')
        nx = x + w - nw
        px = nx - 4 - pw
        c.rect(px, y + 2, pw, 15, 2, 0xFFE2D9C2)
        c.text(ps, px + (pw - width(ps, 'SMALL')) / 2, y + 6, INK, 'SMALL')
        c.rect(nx, y + 2, nw, 15, 2, 0xFFE2D9C2)
        c.text(ns, nx + (nw - width(ns, 'SMALL')) / 2, y + 6, INK, 'SMALL')
    R.add(20, nav)
    done = sum(1 for m in ms if m['id'] in DONE)
    master = ms[-1]['id'] in DONE
    name = fit(ENT[sp], bw - 52, 'TITLE')
    prog = fit(tr('hunts.frontierhunts.card.progress', done, len(ms)), bw - 52 - len(ms) * 15 - 6, 'SMALL')
    title = tr('hunts.frontierhunts.%s.title' % sp)
    chip = fit('★ ' + title if master else tr('hunts.frontierhunts.card.title_locked', title), bw - 60, 'SMALL')
    tag = fit(tr('hunts.frontierhunts.%s.tagline' % sp), bw - 52, 'SMALL')

    def head(c, x, y, w):
        c.rect(x - 2, y + 2, w + 4, 63, 3, 0x26B99859 if master else 0x14000000)
        c.fill(x - 2, y + 2, x, y + 65, TIER['master'] if master else GOLD_DARK)
        c.icon('sp_' + sp, x + 5, y + 10, 32)
        c.text(name, x + 44, y + 7, INK, 'TITLE')
        c.text(tag, x + 44, y + 23, MUTED, 'SMALL')
        for i, m in enumerate(ms):
            c.rect(x + 44 + i * 15, y + 35, 12, 6, 3, TIER[m['tier']] if m['id'] in DONE else 0xFFD3CCB6)
        c.text(prog, x + 44 + len(ms) * 15 + 4, y + 34, INK_BROWN, 'SMALL')
        cw = width(chip, 'SMALL') + 12
        c.rect(x + 44, y + 47, cw, 13, 6.5, TIER['master'] if master else 0xFFD6CEB6)
        c.text(chip, x + 50, y + 50, INK_BROWN if master else MUTED, 'SMALL')
    R.add(68, head)
    s = LOG.get(sp, (0, 0, 0, '—'))
    para(R, tr('hunts.frontierhunts.card.stats', *s), bw, MUTED)
    R.h += 2
    section(R, tr('hunts.frontierhunts.card.how'))
    para(R, tr('hunts.frontierhunts.%s.brief' % sp), bw, INK_BROWN)
    R.h += 2
    for icon, text in (('reserve', tr('hunts.frontierhunts.card.where', tr('hunts.frontierhunts.%s.where' % sp))),):
        lines = wrap(text, bw - 22, 'SMALL')

        def il(c, x, y, w, lines=lines, icon=icon):
            c.icon(icon, x, y, 12)
            for i, l in enumerate(lines):
                c.text(l, x + 18, y + 2 + i * 9, INK_BROWN, 'SMALL')
        R.add(max(16, len(lines) * 9 + 6), il)
    R.h += 4
    section(R, tr('hunts.frontierhunts.card.milestones'))
    for m in ms:
        d = m['id'] in DONE
        tier = tr('hunts.frontierhunts.tier.' + m['tier']).upper()
        tw = width(tier, 'SMALL') + 10
        t = fit(m['title'], bw - 40 - tw - 6, 'STRONG')
        hint = wrap(m['rule'], bw - 40, 'SMALL')
        rw = ['+%d XP' % m['xp']]
        if m['tokens']:
            rw.append('%d tokens' % m['tokens'])
        if m['item']:
            rw.append(('%d× ' % m['count'] if m['count'] > 1 else '') + m['item'].split(':')[1].replace('_', ' ').title())
        if m['tier'] == 'master':
            rw.append('Title: ' + title)
        rl = wrap(' · '.join(rw), bw - 40, 'SMALL')
        needs = []
        for n in m['needs']:
            needs += wrap('Needs: ' + ' or '.join(x.split(':')[1].replace('_', ' ').title() for x in n.split('|')), bw - 40, 'SMALL')
        multi = m['target'] > 1
        h = 18 + len(hint) * 9 + len(needs) * 9 + len(rl) * 9 + (10 if multi else 0) + 6

        def row(c, x, y, w, m=m, d=d, tier=tier, tw=tw, t=t, hint=hint, rl=rl, needs=needs, multi=multi, h=h):
            c.rect(x - 2, y + 1, w + 4, h - 3, 3, 0x163E6E37 if d else (0x12B99859 if m['tier'] == 'master' else 0x0E3B2E20))
            if d:
                c.rect(x + 2, y + 6, 10, 10, 2, GREEN)
            else:
                c.rect(x + 2, y + 6, 10, 10, 2, 0xFF8C8A78)
                c.rect(x + 3, y + 7, 8, 8, 1.5, PAPER)
            c.icon(MS_ICON.get(m['id'], 'reticle'), x + 16, y + 3, 16, 0.75 if d else 1, hunt=True)
            c.text(t, x + 36, y + 6, MUTED if d else INK, 'STRONG')
            c.rect(x + w - tw, y + 4, tw, 11, 5.5, TIER[m['tier']])
            c.text(tier, x + w - tw + 5, y + 6, INK_BROWN if m['tier'] == 'master' else PAPER, 'SMALL')
            yy = y + 18
            for l in hint:
                c.text(l, x + 36, yy, FAINT if d else INK_BROWN, 'SMALL')
                yy += 9
            for l in needs:
                c.text(l, x + 36, yy, MUTED, 'SMALL')
                yy += 9
            if multi:
                v = COUNTS.get('hunt.%s.%s' % (m['species'], m['id'].split('_', 2)[2]), 0)
                c.rect(x + 36, yy + 3, min(120, w - 90), 3, 1.5, TRACK)
                c.rect(x + 36, yy + 3, max(3, min(120, w - 90) * v / m['target']), 3, 1.5, GOLD)
                c.text('%d / %d' % (v, m['target']), x + 40 + min(120, w - 90), yy + 1, INK_BROWN, 'SMALL')
                yy += 10
            for l in rl:
                c.text(l, x + 36, yy, FAINT if d else GOLD_DARK, 'SMALL')
                yy += 9
        R.add(h, row)
    R.h += 2
    para(R, tr('hunts.frontierhunts.card.footer'), bw, FAINT)


MS_ICON = {}


def table(R, bw):
    para(R, tr('hunts.frontierhunts.species.intro'), bw, MUTED)
    wide = bw >= 360
    cs, cp, ct, cb, cf = 34, 40 if wide else 0, 36, 54, 74 if wide else 0
    nw = bw - cs - cp - ct - cb - cf

    def head(c, x, y, w):
        cx = x + nw
        c.text('Species', x + 22, y + 4, GOLD_DARK, 'SMALL')
        c.right('Seen', cx + cs, y + 4, GOLD_DARK, 'SMALL')
        c.fill(x, y + 14, x + w, y + 15, RULE)
    R.add(16, head)
    for i, sp in enumerate(ORDER):
        s = LOG.get(sp)

        def row(c, x, y, w, sp=sp, s=s, i=i):
            if i % 2 == 0:
                c.fill(x - 3, y, x + w + 3, y + 19, 0x0E3B2E20)
            c.icon('sp_' + sp, x + 1, y + 1, 16, 1 if s else 0.4)
            c.text(fit(ENT[sp], nw - 26 - 34, 'BODY'), x + 22, y + 6, INK if s else FAINT, 'BODY')
            pips(c, sp, x + nw - 4, y + 7)
            cx = x + nw
            c.right(str(s[0]) if s else '·', cx + cs, y + 7, MUTED, 'SMALL')
            if cp:
                c.right(str(s[1]) if s else '·', cx + cs + cp, y + 7, MUTED, 'SMALL')
            c.right(str(s[2]) if s else '·', cx + cs + cp + ct, y + 6, GREEN if s and s[2] else FAINT, 'STRONG')
            c.right(s[3] if s else '—', cx + cs + cp + ct + cb, y + 7, INK_BROWN, 'SMALL')
        R.add(19, row)


def checklist(R, bw):
    def hdr(c, x, y, w):
        c.icon('hunting', x, y + 3, 16)
        c.text('HUNTS', x + 20, y + 8, INK, 'STRONG')
        c.right('15 / 85', x + w, y + 9, MUTED, 'SMALL')
    R.add(24, hdr)
    for sp in ('whitetail', 'elk', 'grouse'):
        R.h += 3

        def sh(c, x, y, w, sp=sp):
            c.icon('sp_' + sp, x + 1, y + 1, 16)
            c.text(ENT[sp], x + 22, y + 5, INK_BROWN, 'STRONG')
            pips(c, sp, x + w - 2, y + 7)
            c.fill(x + 22, y + 16, x + w, y + 17, 0x40B89B6E)
        R.add(18, sh)
        for m in ms_of(sp):
            d = m['id'] in DONE

            def er(c, x, y, w, m=m, d=d):
                if d:
                    c.rect(x + 1, y + 5, 10, 10, 2, GREEN)
                else:
                    c.rect(x + 1, y + 5, 10, 10, 2, 0xFF8C8A78)
                    c.rect(x + 2, y + 6, 8, 8, 1.5, PAPER)
                c.icon(MS_ICON.get(m['id'], 'reticle'), x + 15, y + 2, 16, hunt=True)
                xp = '+%d' % m['xp']
                prog = 'Done' if d else ('' if m['target'] == 1 else '0 / %d' % m['target'])
                rw = width(prog, 'SMALL') + 34 + 8
                c.text(fit(m['title'], w - 40 - rw, 'BODY'), x + 36, y + 6, MUTED if d else INK, 'BODY')
                c.right(xp, x + w, y + 7, FAINT if d else GOLD_DARK, 'SMALL')
                c.right(prog, x + w - 34, y + 7, GREEN if d else INK_BROWN, 'SMALL')
            R.add(20, er)


def render(page):
    c = Canvas()
    pw, ph = min(620, GW - 12), min(400, GH - 12)
    left, top = (GW - pw) // 2, (GH - ph) // 2
    side = 100 if pw < 470 else 128
    bx, bw = left + side + 18, pw - side - 36
    vt, vb = top + 52, top + ph - 10
    c.fill(0, 0, GW, GH, 0xB0101208)
    notebook(c, left, top, pw, ph, side)
    titles = {'card': ENT[SPECIES] + ' hunt', 'table': 'Species log', 'checklist': 'Checklist'}
    subs = {'card': tr('hunts.frontierhunts.sub.card', ENT[SPECIES], 2, 5), 'table': '7 of 17 species taken · 9 seen · 6 photographed',
            'checklist': '42 of 172 done · 24%'}
    c.text(fit(titles[page], bw - 30, 'TITLE'), bx, top + 14, INK, 'TITLE')
    c.text(fit(subs[page], bw - 8, 'SMALL'), bx, top + 32, MUTED, 'SMALL')
    c.fill(bx, top + 45, left + pw - 16, top + 46, RULE)
    R = Rows()
    {'card': card, 'table': table, 'checklist': checklist}[page](R, bw)
    view = Image.new('RGBA', c.img.size, (0, 0, 0, 0))
    vc = Canvas()
    vc.img = view
    vc.d = ImageDraw.Draw(view, 'RGBA')
    for y0, h, fn in R.rows:
        fn(vc, bx, vt + y0, bw)
    crop = view.crop((round((bx - 6) * SC), round(vt * SC), round((left + pw - 12) * SC), round(vb * SC)))
    c.img.alpha_composite(crop, (round((bx - 6) * SC), round(vt * SC)))
    # full-length strip of the scrolled content beside the page
    full = Image.new('RGBA', (round((bw + 12) * SC), round((R.h + 10) * SC)), col(PAPER))
    fc = Canvas()
    fc.img = full
    fc.d = ImageDraw.Draw(full, 'RGBA')
    for y0, h, fn in R.rows:
        fn(fc, 6, 4 + y0, bw)
    # text running past the right edge of the page is a layout bug
    over = [b for b in vc.boxes if b[2] > bx + bw + 0.5]
    out = OUT + '_%s.png' % page
    c.img.save(out)
    full.save(OUT + '_%s_full.png' % page)
    print(out, 'content', R.h, 'px', 'overflow:', [(round(b[2] - bx - bw, 1), b[4]) for b in over][:6])


if __name__ == '__main__':
    import subprocess
    for m in MS:
        MS_ICON[m['id']] = m['icon']
    for page in ('table', 'card', 'checklist'):
        render(page)
