#!/usr/bin/env python3
"""[journal] Offline mock of the overhauled Hunter's Journal (layout / text-fit review only, not the real renderer).

python3 tools/journal/mock_journal.py <repo> <fonts dir> <out prefix> [gui_w gui_h scale]
Writes <out prefix>_<page>.png for home, checklist, skills, species, records. Mirrors the constants in
client/JournalScreen (panel, sidebar, rows) with a sample mid-game record. Item icons are drawn as small coloured tiles.
"""
import json, os, re, sys
from PIL import Image, ImageDraw, ImageFont

ROOT, FONTS, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
GW, GH, SC = (int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])) if len(sys.argv) > 6 else (854, 480, 2)
LANG = json.load(open(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/journal.json')))
P = 'journal.frontierhunts.'
ENT = {'whitetail': 'Whitetail', 'elk': 'Elk', 'moose': 'Moose', 'black_bear': 'Black Bear', 'grizzly': 'Grizzly Bear', 'polar_bear': 'Polar Bear',
       'boar': 'Wild Boar', 'bison': 'Bison', 'pronghorn': 'Pronghorn', 'coyote': 'Coyote', 'wolf': 'Gray Wolf', 'cougar': 'Cougar', 'lion': 'Lion',
       'panther': 'Black Panther', 'cheetah': 'Cheetah', 'grouse': 'Ruffed Grouse', 'duck': 'Mallard'}


ATLAS = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/journal/icons.png')).convert('RGBA')
ATLAS_NAMES = re.findall(r'"([a-z_0-9]+)"', open(os.path.join(ROOT, 'src/com/formaworks/frontierhunts/journal/client/JournalIcons.java')).read()
                         .split('GENERATED-NAMES-BEGIN')[1].split('GENERATED-NAMES-END')[0])


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2),
     'TITLE': ttf('inter_bold.ttf', 12.0)}
LH = {'SMALL': 8, 'BODY': 10, 'STRONG': 10, 'TITLE': 14}
PAPER, INK, INK_BROWN, MUTED, FAINT = 0xFFEAE3D0, 0xFF243A32, 0xFF3B2E20, 0xFF6A7266, 0xFF9A9A88
GOLD, GOLD_DARK, LEATHER, TRACK, GREEN, RULE = 0xFFB99859, 0xFF8F6E2F, 0xFF49372B, 0xFFD3CCB6, 0xFF3E6E37, 0xFFC5C2AE
SIDE_TEXT, SIDE_MUTED, BUTTON = 0xFFEFE8D7, 0xFFC9B99A, 0xFF2D4237
SKILLS = [('marksmanship', 0xB0623C), ('stalking', 0x5E7F4A), ('tracking', 0x8E3B32), ('butchery', 0x8C6A3C), ('woodcraft', 0x3F6C78)]


def col(argb):
    return ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255 or 255)


def tr(k, *a):
    s = LANG.get(P + k, LANG.get(k, k))
    s = re.sub(r'%(\d)\$s', lambda m: '{%d}' % (int(m.group(1)) - 1), s)
    i = 0
    while '%s' in s:
        s = s.replace('%s', '{%d}' % i, 1)
        i += 1
    s = s.replace('%%', '%')
    return s.format(*[str(x) for x in a]) if a else s


class Canvas:
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

    def icon(self, x, y, c=0xFF7A8A6A, name=None, size=16, alpha=1.0):
        # [ledger] real journal icon art from the atlas when a name is given
        if name and name in ATLAS_NAMES:
            i = ATLAS_NAMES.index(name)
            im = ATLAS.crop(((i % 16) * 32, (i // 16) * 32, (i % 16) * 32 + 32, (i // 16) * 32 + 32))
            px = size * SC
            im = im.resize((px, px), Image.NEAREST if px % 32 == 0 else Image.BILINEAR)
            if alpha < 1:
                a = im.getchannel('A').point(lambda v: int(v * alpha))
                im.putalpha(a)
            self.img.alpha_composite(im, (round(x * SC), round(y * SC)))
            return
        self.rect(x + 2, y + 2, 12, 12, 2, c)


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


def bar(c, x, y, w, h, f, track, fill):
    c.rect(x, y, w, h, h / 2, track)
    f = max(0, min(1, f))
    if f > 0:
        c.rect(x, y, max(h, w * f), h, h / 2, fill)


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


# ------------------------------------------------------------------------------------------------ sample record
C = {'shots': 41, 'hits': 29, 'kills': 11, 'clean_kills': 7, 'shot_sum_dm': 11 * 1120, 'longest_shot': 214, 'recoveries': 4, 'lost': 1,
     'longest_trail': 86, 'signs': 63, 'stalks': 5, 'walk_m': 18400, 'photos': 37, 'calls': 3, 'harvests': 10, 'bow_kills': 2, 'rifle_kills': 9,
     'field_s': 5 * 3600 + 1400, 'species_taken': 5, 'biomes': 7, 'seasons': 2}
XP = [1180, 420, 690, 330, 510]
BONUS = 760
NOTES = [('Oct 14, 6:42 AM', '8-point whitetail buck, 96 kg, 142 m, double lung. Scored 138". Dropped where it stood.', 0),
         ('Oct 13, 5:10 PM', 'Checklist: A 200 m shot', 1), ('Oct 13, 7:55 AM', 'Gray Wolf, 41 kg, 88 m, liver. Followed it 64 m.', 0),
         ('Oct 12, 9:02 PM', 'Learned Steady Hands (Marksmanship level 2).', 3)]


def lvl(xp):
    n = 0
    while n < 10 and xp >= 20 * (n + 1) ** 2 + 40 * (n + 1):
        n += 1
    return n


def render(page):
    c = Canvas()
    pw, ph = min(620, GW - 12), min(400, GH - 12)
    left, top = (GW - pw) // 2, (GH - ph) // 2
    side = 100 if pw < 470 else 128
    bx, bw = left + side + 18, pw - side - 36
    vt, vb = top + 52, top + ph - 10
    c.fill(0, 0, GW, GH, 0xB0101208)
    notebook(c, left, top, pw, ph, side)
    # sidebar
    c.fill(left + 9, top + 11, left + 11, top + 44, GOLD)
    c.text('FRONTIER HUNTS', left + 16, top + 12, SIDE_MUTED, 'SMALL')
    c.text(fit("Hunter's Journal", side - 26, 'STRONG'), left + 16, top + 21, SIDE_TEXT, 'STRONG')
    c.text('Woodsman', left + 16, top + 34, GOLD, 'SMALL')
    tabs = ['Home', 'Checklist', 'Skills', 'Species', 'Records', 'Field notes', 'Reserve']
    keys = ['home', 'checklist', 'skills', 'species', 'records', 'notes', 'reserve']
    tab_h = max(14, min(24, (ph - 54 - 58) // len(tabs)))
    for i, t in enumerate(tabs):
        ty = top + 54 + i * tab_h
        h = tab_h - 3
        if keys[i] == page:
            c.rect(left + 6, ty, side - 6, h, 2, PAPER)
        c.icon(left + 10, ty + (h - 16) // 2, 0xFF9C7A50, name=keys[i])
        c.text(fit(t, side - 34, 'BODY'), left + 30, ty + (h - 8) // 2, INK if keys[i] == page else SIDE_TEXT)
        if side >= 120:
            c.right(str(i + 1), left + side - 12, ty + (h - 8) // 2, 0xFF8E7B62, 'BODY')
    by = top + ph - 44
    c.rect(left + 8, by, side - 18, 17, 2, BUTTON)
    c.fill(left + 8, by, left + 10, by + 17, GOLD)
    c.icon(left + 12, by + 1, name='school')
    c.text('Field School 5/7', left + 30, by + 5, SIDE_TEXT, 'SMALL')
    c.icon(left + 10, by + 21, name='token', size=12)
    c.text('142 tokens', left + 25, by + 24, SIDE_MUTED, 'SMALL')
    # header
    titles = {'home': "Hunter's Journal", 'checklist': 'Checklist', 'skills': 'Hunter skills', 'species': 'Species log', 'records': "Hunter's record"}
    c.text(fit(titles[page], bw - 30, 'TITLE'), bx, top + 14, INK, 'TITLE')
    subs = {'home': 'Austin · the reserve keeps track of every hunt', 'checklist': '27 of 84 done · 32%', 'skills': '3,130 skill XP · skills grow by doing',
            'species': '5 of 17 species taken · 9 seen · 6 photographed', 'records': 'Every shot, trail and trophy'}
    c.text(fit(subs[page], bw - 8, 'SMALL'), bx, top + 32, MUTED, 'SMALL')
    c.fill(bx, top + 45, left + pw - 16, top + 46, RULE)
    c.rect(left + pw - 28, top + 10, 18, 18, 2, BUTTON)
    c.text('×', left + pw - 22, top + 14, SIDE_TEXT)
    # content (clipped by drawing then masking)
    content = Canvas()
    content.img = Image.new('RGBA', (GW * SC, GH * SC), (0, 0, 0, 0))
    content.d = ImageDraw.Draw(content.img, 'RGBA')
    y = vt
    k = content
    if page == 'home':
        total = sum(XP) + BONUS
        k.circle(bx + 22, y + 25, 21, GOLD)
        k.circle(bx + 22, y + 25, 19.5, LEATHER)
        k.circle(bx + 22, y + 25, 15, 0xFF5A4433)
        k.icon(bx + 6, y + 9, 0xFFB0A080, name='rank_woodsman', size=32)
        k.text(tr('home.rank', 2, 6), bx + 52, y + 3, GOLD_DARK, 'SMALL')
        k.text('Woodsman', bx + 52, y + 13, INK, 'TITLE')
        bar(k, bx + 52, y + 32, bw - 54, 5, (total - 400) / 1100, TRACK, GOLD)
        k.text(fit(tr('home.xp', f'{total:,}', f'{1500 - total:,}', 'Tracker'), bw - 60, 'SMALL'), bx + 52, y + 41, MUTED, 'SMALL')
        y += 56
        k.text(fit(tr('date', 'Fall', 'October', 14, 1, '6:42 AM'), bw - 4, 'SMALL'), bx, y + 3, MUTED, 'SMALL')
        y += 16 + 4
        colw = (bw - 4 * 8) // 5
        for i, (s, cc) in enumerate(SKILLS):
            cx = bx + i * (colw + 8)
            L = lvl(XP[i])
            k.text(fit(tr('skill.%s.name' % s), colw - 10, 'SMALL'), cx, y + 4, MUTED, 'SMALL')
            k.right(str(L), cx + colw, y + 2, INK, 'STRONG')
            a, b = 20 * L * L + 40 * L, 20 * (L + 1) ** 2 + 40 * (L + 1)
            bar(k, cx, y + 15, colw, 4, (XP[i] - a) / (b - a), TRACK, 0xFF000000 | cc)
        y += 28
        k.text('CHECKLIST', bx, y + 5, GOLD_DARK, 'SMALL')
        k.right('27 / 84 · 32%', bx + bw, y + 3, INK, 'STRONG')
        bar(k, bx, y + 17, bw, 5, 27 / 84, TRACK, GREEN)
        y += 30
        k.text('UP NEXT', bx, y + 6, INK, 'STRONG')
        k.fill(bx, y + 17, bx + 36, y + 18, 0xFFB89B6E)
        y += 20
        for (t, s, r, f), nm in zip([('Close the distance', 'Field School · lesson 4 of 7', '3/7', 3 / 7),
                           ('II · Equipment for the valley', "Mara's campaign · report 4 of 9", '0/1', 0),
                           ('A 300 m shot', 'Hunting', '214 / 300 m', 214 / 300), ('Read 25 signs', 'Hunting', '25 / 25', 1)],
                                    ['stalking', 'campaign', 'marksmanship', 'tracking']):
            rw = width(r, 'STRONG') + 8
            k.icon(bx, y + 4, name=nm)
            k.text(fit(t, bw - 26 - rw, 'STRONG'), bx + 22, y + 4, INK, 'STRONG')
            k.text(fit(s, bw - 26 - rw, 'SMALL'), bx + 22, y + 15, MUTED, 'SMALL')
            k.right(r, bx + bw, y + 5, INK_BROWN, 'STRONG')
            bar(k, bx + bw - rw + 8, y + 17, rw - 8, 2, f, TRACK, GOLD)
            y += 26
        k.text('RECENT', bx, y + 6, INK, 'STRONG')
        k.fill(bx, y + 17, bx + 36, y + 18, 0xFFB89B6E)
        y += 20
        for st, tx, kind in NOTES:
            lines = wrap(tx, bw - 14, 'BODY')
            k.circle(bx + 3.5, y + 6.5, 2.5, [0xFF8E3B32, 0xFF3E6E37, 0xFFB99859, 0xFF5E7F4A][kind])
            k.text(st, bx + 12, y + 3, GOLD_DARK, 'SMALL')
            yy = y + 13
            for l in lines:
                k.text(l, bx + 12, yy, INK)
                yy += 10
            y += 13 + len(lines) * 10 + 5
    elif page == 'checklist':
        chips = [('All 32%', True), ('Species 5/20', False), ('Hunting 9/21', False), ('Gear 8/18', False), ('World 3/10', False), ('Camp 1/8', False),
                 ('Survival 2/4', False), ('Field School 0/3', False)]
        cx = cy = 0
        for t, sel in chips:
            w = width(t, 'SMALL') + 12
            if cx > 0 and cx + w > bw:
                cx, cy = 0, cy + 16
            k.rect(bx + cx, y + cy, w, 13, 6.5, 0xFF3E5945 if sel else 0xFFE2D9C2)
            k.text(t, bx + cx + 6, y + cy + 3, SIDE_TEXT if sel else INK, 'SMALL')
            cx += w + 4
        y += cy + 18 + 6
        k.text('HUNTING', bx, y + 6, INK, 'STRONG')
        k.right('9 / 21', bx + bw, y + 7, MUTED, 'SMALL')
        bar(k, bx, y + 18, bw, 2, 9 / 21, TRACK, GOLD)
        y += 24
        rows = [('clean_1', 1, 1, True, 40), ('clean_10', 7, 10, False, 100), ('clean_50', 7, 50, False, 250), ('shot_50', 214, 50, True, 30),
                ('shot_200', 214, 200, True, 120), ('shot_300', 214, 300, False, 200), ('bow_30', 0, 1, False, 80), ('recover_10', 4, 10, False, 120),
                ('trail_75', 86, 75, True, 80), ('stalk_6', 0, 1, False, 60), ('called_kill', 0, 1, False, 60), ('trophy_160', 138, 160, False, 200)]
        for cid, v, t, done, xp in rows:
            prog = 'Done' if done else (f'{min(v, t)} / {t} m' if cid.startswith(('shot', 'trail')) else (f'{min(v,t)}" / {t}"' if cid.startswith('trophy') else f'{min(v, t)} / {t}'))
            rightw = width(prog, 'SMALL') + 34 + 8
            title = fit(tr('check.' + cid), bw - 40 - rightw, 'BODY')
            multi = t > 1 and not done
            if done:
                k.rect(bx + 1, y + 5, 10, 10, 2, GREEN)
            else:
                k.rect(bx + 1, y + 5, 10, 10, 2, 0xFF8C8A78)
                k.rect(bx + 2, y + 6, 8, 8, 1.5, PAPER)
            k.icon(bx + 15, y + 2, name={'clean': 'marksmanship', 'shot': 'marksmanship', 'bow': 'hunting', 'recov': 'blood', 'trail': 'tracking',
                                         'stalk': 'stalking', 'calle': 'antlers', 'troph': 'records'}.get(cid[:5], 'checklist'))
            k.text(title, bx + 36, y + (3 if multi else 6), MUTED if done else INK)
            if done:
                k.fill(bx + 36, y + 10, bx + 36 + width(title, 'BODY'), y + 11, 0x66243A32)
            if multi:
                bar(k, bx + 36, y + 14, min(110, bw - 40 - rightw), 2, v / t, TRACK, GOLD)
            k.right('+%d' % xp, bx + bw, y + 7, FAINT if done else GOLD_DARK, 'SMALL')
            k.right(prog, bx + bw - 34, y + 7, GREEN if done else INK_BROWN, 'SMALL')
            y += 20
    elif page == 'skills':
        for line in wrap(tr('skills.intro'), bw, 'SMALL'):
            k.text(line, bx, y, MUTED, 'SMALL')
            y += 10
        y += 3
        perks = {'marksmanship': ['steady_hands', 'quick_settle', 'controlled_breath'], 'stalking': ['soft_steps', 'low_profile', 'ghost']}
        for i, (s, cc) in enumerate(SKILLS[:2]):
            L = lvl(XP[i])
            a, b = 20 * L * L + 40 * L, 20 * (L + 1) ** 2 + 40 * (L + 1)
            y += 4
            k.rect(bx - 2, y, bw + 4, 33, 3, 0x14000000)
            k.rect(bx - 2, y, 2, 33, 1, 0xFF000000 | cc)
            k.icon(bx + 4, y + 8, name=s)
            k.text(tr('skill.%s.name' % s), bx + 26, y + 4, INK, 'STRONG')
            k.right(tr('skills.level', L), bx + bw - 4, y + 4, 0xFF000000 | cc, 'STRONG')
            bar(k, bx + 26, y + 16, bw - 32, 4, (XP[i] - a) / (b - a), TRACK, 0xFF000000 | cc)
            k.text(fit(tr('skills.xp', XP[i] - a, b - a, L + 1), bw - 30, 'SMALL'), bx + 26, y + 23, MUTED, 'SMALL')
            y += 34
            how = wrap(tr('skill.%s.how' % s), bw - 8, 'SMALL')
            for l in how:
                k.text(l, bx + 4, y + 2, MUTED, 'SMALL')
                y += 9
            y += 4
            for j, p in enumerate(perks[s]):
                lv = [2, 5, 8][j]
                un = L >= lv
                desc = wrap(tr('perk.%s.desc' % p), bw - 30, 'SMALL')
                k.circle(bx + 12, y + 7, 5.5, GOLD if un else 0xFFCFC7AF)
                k.text(tr('perk.%s.name' % p), bx + 24, y + 3, INK if un else MUTED, 'STRONG')
                pill = tr('skills.at', lv)
                pw2 = width(pill, 'SMALL') + 10
                k.rect(bx + bw - pw2, y + 1, pw2, 11, 5.5, (0xD9000000 | cc) if un else 0xFFD6CEB6)
                k.text(pill, bx + bw - pw2 + 5, y + 3, PAPER if un else MUTED, 'SMALL')
                yy = y + 14
                for l in desc:
                    k.text(l, bx + 24, yy, INK_BROWN if un else FAINT, 'SMALL')
                    yy += 9
                y += 14 + len(desc) * 9 + 4
            y += 4
    elif page == 'species':
        wide = bw >= 360
        cs, cp, ct, cb, cf = 34, 40 if wide else 0, 36, 54, 74 if wide else 0
        nw = bw - cs - cp - ct - cb - cf
        hdr = [('SEEN', cs), ('PHOTOS', cp), ('TAKEN', ct), ('BEST', cb), ('FIRST TAKEN', cf)]
        k.text('SPECIES', bx + 22, y + 4, GOLD_DARK, 'SMALL')
        x = bx + nw
        for h, w in hdr:
            if w:
                x += w
                k.right(h, x, y + 4, GOLD_DARK, 'SMALL')
        k.fill(bx, y + 14, bx + bw, y + 15, RULE)
        y += 16
        data = {'whitetail': (14, 9, 6, '138"', 'Oct 3 · Yr 1'), 'elk': (5, 2, 1, '312"', 'Oct 9 · Yr 1'), 'moose': (0, 1, 0, '—', '—'),
                'black_bear': (2, 0, 0, '—', '—'), 'wolf': (4, 3, 2, '46 kg', 'Oct 13 · Yr 1'), 'grouse': (9, 0, 1, '0.6 kg', 'Oct 11 · Yr 1')}
        for i, (sp, nm) in enumerate(ENT.items()):
            s, p, t, best, first = data.get(sp, (0, 0, 0, '—', '—'))
            known = s + p + t > 0
            if i % 2 == 0:
                k.fill(bx - 3, y, bx + bw + 3, y + 19, 0x0E3B2E20)
            k.icon(bx + 1, y + 1, 0xFF8FA070 if known else 0xFFC0BBAA, name='sp_' + sp, alpha=1.0 if known else 0.4)
            k.text(fit(nm, nw - 26, 'BODY'), bx + 22, y + 6, INK if known else FAINT)
            x = bx + nw
            for v, w, sz, colr in [(s or '·', cs, 'SMALL', MUTED), (p or '·', cp, 'SMALL', MUTED), (t or '·', ct, 'STRONG', GREEN if t else FAINT),
                                   (best, cb, 'SMALL', INK_BROWN), (first, cf, 'SMALL', MUTED)]:
                if w:
                    x += w
                    k.right(fit(str(v), w - 4, sz), x, y + 7 if sz == 'SMALL' else y + 6, colr, sz)
            y += 19
    elif page == 'records':
        def section(t):
            nonlocal y
            k.text(t.upper(), bx, y + 6, INK, 'STRONG')
            k.fill(bx, y + 17, bx + 36, y + 18, 0xFFB89B6E)
            y += 20

        def grid(pairs):
            nonlocal y
            two = bw >= 300
            cw = (bw - 16) // 2 if two else bw
            for i in range(0, len(pairs), 2 if two else 1):
                for j in range(2 if two else 1):
                    if i + j < len(pairs):
                        x = bx + j * (cw + 16)
                        k.text(fit(pairs[i + j][0], cw - 60, 'SMALL'), x, y + 4, MUTED, 'SMALL')
                        k.right(pairs[i + j][1], x + cw, y + 3, INK, 'STRONG')
                        k.fill(x, y + 14, x + cw, y + 15, 0x22243A32)
                y += 15
            y += 4
        section('Shooting')
        grid([(tr('stat.shots'), '41'), (tr('stat.hits'), '29'), (tr('stat.hit_rate'), '71%'), (tr('stat.kills'), '11'), (tr('stat.clean'), '7'),
              (tr('stat.clean_rate'), '64%'), (tr('stat.avg_shot'), '112 m'), (tr('stat.longest_shot'), '214 m'), (tr('stat.bow_rifle'), '2 / 9'),
              (tr('stat.prone'), '6')])
        section('Tracking & fieldcraft')
        grid([(tr('stat.recovered'), '4'), (tr('stat.lost'), '1'), (tr('stat.recovery_rate'), '80%'), (tr('stat.longest_trail'), '86 m'),
              (tr('stat.walked'), '18.4 km'), (tr('stat.field_time'), '5h 23m')])
        section('Season summaries')
        k.text('Fall · Year 1', bx, y + 5, INK_BROWN, 'STRONG')
        y += 16
        for l in ['Animals taken: 5 (3 clean)', 'Tracked recoveries: 2 · lost: 1', 'Shots: 14 · hits: 9 (64%)', 'Longest shot: 214 m']:
            k.text('·  ' + l, bx + 4, y + 1, INK, 'SMALL')
            y += 10
        bwid = min(132, (bw - 6) // 2)
        for i, t in enumerate(['Copy text', 'Print (1 paper)']):
            k.rect(bx + i * (bwid + 6), y + 3, bwid, 18, 2, BUTTON)
            k.text(t, bx + i * (bwid + 6) + (bwid - width(t, 'SMALL')) / 2, y + 8, SIDE_TEXT, 'SMALL')
    # clip to the viewport
    mask = Image.new('L', c.img.size, 0)
    ImageDraw.Draw(mask).rectangle([(bx - 6) * SC, vt * SC, (left + pw - 12) * SC, vb * SC], fill=255)
    clipped = Image.new('RGBA', c.img.size, (0, 0, 0, 0))
    clipped.paste(content.img, (0, 0), mask)
    c.img.alpha_composite(clipped)
    out = '%s_%s.png' % (OUT, page)
    c.img.save(out)
    print('wrote', out)


for page in ['home', 'checklist', 'skills', 'species', 'records']:
    render(page)
