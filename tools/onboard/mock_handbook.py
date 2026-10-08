"""[onboard] Offline layout mock of the Frontier Handbook (Start here + a step page) and the objective card.
Mirrors the constants of HandbookScreen / HandbookUi / ObjectiveCard; real Inter fonts from the jar, item textures from
the jar where an item has a flat texture (3D items draw a grey stand-in). Layout check only, not the real renderer.

python3 tools/onboard/mock_handbook.py <repo> <jar dir (unzipped)> <out dir> [gui_w gui_h scale]
"""
import json, os, sys, glob
from PIL import Image, ImageDraw, ImageFont

ROOT, JAR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
GW, GH, SC = (int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])) if len(sys.argv) > 6 else (427, 240, 2)
FONTS = os.path.join(JAR, 'assets/frontierhunts/font')
LANG = {}
for f in sorted(glob.glob(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/*.json'))):
    LANG.update(json.load(open(f)))
O = 'onboard.frontierhunts.'


def tr(k, *a):
    s = LANG.get(k, k)
    for x in a:
        s = s.replace('%s', str(x), 1)
    return s


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2), 'TITLE': ttf('inter_bold.ttf', 12.0)}
LH = {'SMALL': 8, 'BODY': 10, 'STRONG': 10, 'TITLE': 14}


def col(argb):
    return ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)


def width(s, size):
    return F[size].getlength(s) / SC


def fit(s, w, size):
    if width(s, size) <= w:
        return s
    while s and width(s + '…', size) > w:
        s = s[:-1]
    return s + '…'


def wrap(s, w, size):
    out, line = [], ''
    for word in s.split(' '):
        t = (line + ' ' + word).strip()
        if width(t, size) <= w or not line:
            line = t
        else:
            out.append(line)
            line = word
    if line:
        out.append(line)
    return out


class G:
    def __init__(self):
        self.img = Image.new('RGBA', (GW * SC, GH * SC), (70, 90, 70, 255))
        self.d = ImageDraw.Draw(self.img, 'RGBA')
        self.clip = None

    def fill(self, x0, y0, x1, y1, c):
        if self.clip:
            cx0, cy0, cx1, cy1 = self.clip
            x0, y0, x1, y1 = max(x0, cx0), max(y0, cy0), min(x1, cx1), min(y1, cy1)
            if x1 <= x0 or y1 <= y0:
                return
        if (c >> 24) & 255 == 255:
            self.d.rectangle([x0 * SC, y0 * SC, x1 * SC - 1, y1 * SC - 1], fill=col(c))
        else:
            ov = Image.new('RGBA', (int((x1 - x0) * SC), int((y1 - y0) * SC)), col(c))
            self.img.alpha_composite(ov, (int(x0 * SC), int(y0 * SC)))

    def text(self, s, x, y, c, size):
        if self.clip and not (self.clip[1] - 8 <= y <= self.clip[3]):
            return
        self.d.text((x * SC, y * SC), s, font=F[size], fill=col(c))

    def item(self, iid, x, y):
        if self.clip and not (self.clip[1] - 8 <= y <= self.clip[3] - 8):
            return
        ns, path = iid.split(':')
        p = os.path.join(JAR, 'assets/%s/textures/item/%s.png' % (ns, path))
        if os.path.exists(p):
            im = Image.open(p).convert('RGBA').crop((0, 0, 16, 16)).resize((16 * SC, 16 * SC), Image.NEAREST)
            self.img.alpha_composite(im, (int(x * SC), int(y * SC)))
        else:
            self.fill(x + 2, y + 2, x + 14, y + 14, 0xFF8A8478)


PAPER, INK, INK_BROWN, MUTED, GOLD, GOLD_DARK, SIDEBAR, BUTTON, RULE, GREEN = (0xFFEAE3D0, 0xFF243A32, 0xFF3B2E20, 0xFF657064, 0xFFB99859,
   0xFF8F6E2F, 0xFF49372B, 0xFF2D4237, 0xFFC5C2AE, 0xFF3E6E37)

# the Handbook's tasks (mirror of Handbook.Task, in Handbook.ORDER walking order) [onboard2] archery last, step 8
TASKS = [('table', 1, None, None, 'minecraft:crafting_table'), ('arrows', 2, None, None, 'frontierhunts:field_arrow'),
         ('bench', 3, None, None, 'frontierhunts:bow_tuning_rack'),
         ('tips', 3, None, None, 'frontierhunts:field_point'), ('wind', 4, 1, 'stalk', 'frontierhunts:wind_checker'),
         ('sign', 4, 2, 'tracking', 'frontierhunts:frontier_handbook'), ('glass', 4, 3, 'glassing', 'frontierhunts:binoculars'),
         ('stalk', 4, 4, 'stalk', 'minecraft:leather_boots'), ('shot', 5, 5, 'range', 'frontierhunts:field_bow'),
         ('trail', 5, 6, 'tracking', 'frontierhunts:hound_lead'), ('harvest', 6, 7, 'dressing', 'frontierhunts:skinning_tool'),
         ('cook', 6, None, None, 'frontierhunts:cooked_venison'), ('tent', 7, None, None, 'frontierhunts:trail_dome_tent'),
         ('furs', 7, None, None, 'frontierhunts:fur_hat'), ('rifle', 8, None, 'range', 'frontierhunts:ridgeline_rifle'),
         ('report', 8, None, None, 'frontierhunts:expedition_board'), ('archery', 8, None, 'archery', 'frontierhunts:field_bow')]
CURRICULUM = [('glassing', 'frontierhunts:binoculars'), ('stalk', 'frontierhunts:wind_checker'), ('tracking', None),
              ('dressing', 'frontierhunts:skinning_tool'), ('range', 'frontierhunts:ridgeline_rifle'), ('archery', 'frontierhunts:field_bow')]
PASSED = {'glassing'}
TAB_GAP = 7
DONE = {'table'}


def bw_(label):
    return width(fit(label, 230, 'SMALL'), 'SMALL') + 16


def flow_h(labels, avail):
    lines, used = 1, 0
    for l in labels:
        b = min(avail, bw_(l))
        if used > 0 and used + 6 + b > avail:
            lines += 1
            used = 0
        used += (6 if used else 0) + b
    return lines * 20 - 3


def flow(g, x, y, avail, btns):
    used, yy = 0, y
    for label, primary in btns:
        b = min(avail, bw_(label))
        if used > 0 and used + 6 + b > avail:
            yy += 20
            used = 0
        bx = x + used + (6 if used else 0)
        g.fill(bx, yy, bx + b, yy + 17, GOLD if primary else BUTTON)
        t = fit(label, b - 10, 'SMALL')
        g.text(t, bx + (b - width(t, 'SMALL')) / 2, yy + 4.5, 0xFF2B2014 if primary else 0xFFEFE8D7, 'SMALL')
        used += (6 if used else 0) + b


ATLAS = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/journal/icons.png')).convert('RGBA')
ICON_IX = {'notes': 5, 'campaign': 8, 'school': 15, 'assign': 23}
BOOKS = [('notes', 'journal', 'J'), ('campaign', 'expedition', 'N'), ('school', 'school', ''), ('assign', 'assignments', 'K')]
B = 'onebook.frontierhunts.'


def atlas_icon(g, name, x, y, size):
    i = ICON_IX[name]
    cell = ATLAS.crop(((i % 16) * 32, (i // 16) * 32, (i % 16) * 32 + 32, (i // 16) * 32 + 32))
    if g.clip and not (g.clip[1] <= y and y + size <= g.clip[3]):
        return
    g.img.alpha_composite(cell.resize((size * SC, size * SC), Image.NEAREST if (size * SC) % 32 == 0 else Image.LANCZOS), (int(x * SC), int(y * SC)))


def render(page, scroll=0):
    g = G()
    panelW, panelH = min(620, GW - 16), min(400, GH - 16)
    left, top = (GW - panelW) // 2, (GH - panelH) // 2
    side = 106 if panelW < 470 else 136
    bodyX, bodyW = left + side + 18, panelW - side - 36
    viewTop, viewBottom = top + 50, top + panelH - 30
    rowH = max(14, min(24, (panelH - 44 - 36 - TAB_GAP) // 10))
    g.fill(0, 0, GW, GH, 0xB0101716)
    g.fill(left - 3, top - 3, left + panelW + 3, top + panelH + 3, 0xFF413526)
    g.fill(left, top, left + panelW, top + panelH, PAPER)
    g.fill(left, top, left + side, top + panelH, SIDEBAR)
    # [outfitter] leather cover plate (HandbookCover.plate) with the stamped title on it
    px, pw = left + 5, side - 10
    plate = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/handbook/cover_plate.png')).convert('RGBA')
    g.img.alpha_composite(plate.resize((pw * SC, 33 * SC), Image.LANCZOS), (px * SC, (top + 6) * SC))
    tx = px + round(pw * 58 / 248)
    tw = px + round(pw * 205 / 248) - tx
    g.text(fit(tr(O + 'screen.brand_top'), tw, 'STRONG'), tx, top + 11, 0xFFE2C27A, 'STRONG')
    g.text(fit(tr(O + 'screen.brand_bottom'), tw, 'SMALL'), tx, top + 24, 0xFFC9AE7C, 'SMALL')
    for i in range(9):
        rx, ry, rw, rh = left + 6, top + 44 + i * rowH, side - 12, rowH - 3
        if i == page:
            g.fill(rx, ry, rx + rw, ry + rh, 0xFF5E4636)
            g.fill(rx, ry, rx + 2, ry + rh, GOLD)
        label = tr(O + 'start') if i == 0 else tr(O + 'step.%d.short' % i)
        g.text(fit(label, rw - 26, 'SMALL'), rx + 21, ry + rh / 2 - 4, 0xFFE3D9C3, 'SMALL')
        if i:
            g.text(str(i), rx + 9, ry + rh / 2 - 4, GOLD if i == 2 else 0xFFC9B99A, 'SMALL')
    # [onboard2] the Assignments tab under a rule
    rx, rw, rh = left + 6, side - 12, rowH - 3
    ry = top + 44 + 9 * rowH + TAB_GAP
    g.fill(rx + 4, ry - (TAB_GAP + 3) // 2 - 1, rx + rw - 4, ry - (TAB_GAP + 3) // 2, 0x50EFE8D7)
    g.fill(rx, ry, rx + rw, ry + rh, 0xFF5E4636 if page == 9 else 0x2CB99859)
    if page == 9:
        g.fill(rx, ry, rx + 2, ry + rh, GOLD)
    icon = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/handbook/assignments_tab.png')).convert('RGBA')
    g.img.alpha_composite(icon.resize((14 * SC, 14 * SC), Image.LANCZOS), (int((rx + 3.5) * SC), int((ry + rh / 2 - 7) * SC)))
    cnt = '%d/6' % len(PASSED)
    g.text(fit(tr(O + 'tab.assignments'), rw - 30 - width(cnt, 'SMALL'), 'SMALL'), rx + 21, ry + rh / 2 - 4, 0xFFE3D9C3, 'SMALL')
    g.text(cnt, rx + rw - 4 - width(cnt, 'SMALL'), ry + rh / 2 - 4, 0xFFE2C27A, 'SMALL')
    g.text(fit(tr(O + 'steps_done', 1, 8), side - 16, 'SMALL'), left + 9, top + panelH - 24, 0xFFC9B99A, 'SMALL')
    eyebrow = tr(O + 'start.eyebrow') if page == 0 else tr(O + 'asg.eyebrow') if page == 9 else tr(O + 'step.eyebrow', page, 8)
    g.text(eyebrow, bodyX, top + 13, GOLD_DARK, 'SMALL')
    title = tr(O + 'start.title') if page == 0 else tr(O + 'asg.title') if page == 9 else tr(O + 'step.%d.title' % page)
    g.text(fit(title, bodyW, 'TITLE'), bodyX, top + 25, INK_BROWN, 'TITLE')
    g.fill(bodyX, top + 45, left + panelW - 16, top + 46, RULE)
    g.clip = (bodyX - 4, viewTop, left + panelW - 10, viewBottom)
    y = viewTop + 4 - scroll

    def para(s, size, c, gap):
        nonlocal y
        for line in wrap(s, bodyW, size):
            g.text(line, bodyX, y, c, size)
            y += LH[size] + 2
        y += gap

    def section(t):
        nonlocal y
        g.text(t, bodyX, y + 5, GOLD_DARK, 'SMALL')
        g.fill(bodyX + width(t, 'SMALL') + 6, y + 9, bodyX + bodyW, y + 10, RULE)
        y += 18

    if page == 0:
        para(tr(O + 'start.p1'), 'BODY', INK, 6)
        t = TASKS[1]
        lines = wrap(tr(O + 'task.%s.how' % t[0]), bodyW - 46, 'BODY')
        btns = [(tr(O + 'btn.show'), True)]  # [onboard2] arrows: no course recommended
        bh = flow_h([b[0] for b in btns], bodyW - 46) + 6
        h = 12 + 16 + len(lines) * 11 + 8 + bh
        g.fill(bodyX, y, bodyX + bodyW, y + h, 0xF2213027)
        g.fill(bodyX, y, bodyX + 3, y + h, GOLD)
        g.item(t[4], bodyX + 12, y + 14)
        g.text(tr(O + 'next.eyebrow', 2, 8), bodyX + 40, y + 7, GOLD, 'SMALL')
        g.text(fit(tr(O + 'task.%s.title' % t[0]), bodyW - 48, 'STRONG'), bodyX + 40, y + 17, 0xFFF4EEDD, 'STRONG')
        yy = y + 30
        for l in lines:
            g.text(l, bodyX + 40, yy, 0xFFCFC8B4, 'BODY')
            yy += 11
        flow(g, bodyX + 40, y + h - bh, bodyW - 46, btns)
        y += h + 10
        section(tr(O + 'steps'))
        for s in range(1, 9):
            ts = [t for t in TASKS if t[1] == s]
            n = sum(1 for t in ts if t[0] in DONE)
            cnt = '%d/%d' % (n, len(ts))
            g.text(str(s), bodyX + 7, y + 5, GOLD_DARK, 'SMALL')
            g.text(fit(tr(O + 'step.%d.title' % s), bodyW - 50 - width(cnt, 'SMALL'), 'BODY'), bodyX + 22, y + 5, INK_BROWN, 'BODY')
            g.text(cnt, bodyX + bodyW - 4 - width(cnt, 'SMALL'), y + 6, GOLD_DARK, 'SMALL')
            y += 21
        y += 6
        # [onebook] the books inside this Handbook
        section(tr(B + 'books'))
        for icon, stem, key in BOOKS:
            lines = wrap(tr(B + 'book.%s.desc' % stem), bodyW - 66, 'SMALL')
            h = max(30, 18 + len(lines) * 10 + 3)
            g.fill(bodyX, y, bodyX + bodyW, y + h, 0x18000000)
            g.fill(bodyX, y, bodyX + 2, y + h, 0x80B99859)
            atlas_icon(g, icon, bodyX + 9, y + h / 2 - 8, 16)
            kw = 0
            if key:
                kw = width(key, 'SMALL') + 8
                g.fill(bodyX + bodyW - 6 - kw, y + 6, bodyX + bodyW - 6, y + 17, GOLD_DARK)
                g.text(key, bodyX + bodyW - 6 - kw + 4, y + 7.5, 0xFFF4EEDD, 'SMALL')
                kw += 6
            g.text(fit(tr(B + 'book.%s.title' % stem), bodyW - 40 - kw, 'STRONG'), bodyX + 34, y + 5, INK_BROWN, 'STRONG')
            yy = y + 17
            for l in lines:
                g.text(l, bodyX + 34, yy, INK, 'SMALL')
                yy += 10
            y += h + 4
        para(tr(B + 'books.hint'), 'SMALL', MUTED, 2)
        y += 4
        lines = wrap(tr(O + 'start.pack'), bodyW - 30, 'SMALL')
        h = len(lines) * 10 + 10
        g.fill(bodyX, y, bodyX + bodyW, y + h, 0x30B99859)
        g.item('frontierhunts:field_bow', bodyX + 6, y + (h - 16) / 2)
        yy = y + 5
        for l in lines:
            g.text(l, bodyX + 26, yy, INK_BROWN, 'SMALL')
            yy += 10
        y += h + 6
    elif page == 9:
        # [onboard2] Assignments page
        para(tr(O + 'asg.p1'), 'BODY', INK, 6)
        nxt = next(c for c, _ in CURRICULUM if c not in PASSED)
        lines = wrap(tr('academy.frontierhunts.course.%s.tagline' % nxt), bodyW - 46, 'BODY')
        btns = [(tr(O + 'asg.btn.open', 'K'), True)]
        bh = flow_h([b[0] for b in btns], bodyW - 46) + 6
        h = 12 + 16 + len(lines) * 11 + 4 + 8 + bh
        g.fill(bodyX, y, bodyX + bodyW, y + h, 0xF2213027)
        g.fill(bodyX, y, bodyX + 3, y + h, GOLD)
        g.item('frontierhunts:wind_checker', bodyX + 12, y + 14)
        g.text(tr(O + 'asg.card.eyebrow', len(PASSED), 6), bodyX + 40, y + 7, GOLD, 'SMALL')
        g.text(fit(tr(O + 'asg.card.next', tr('academy.frontierhunts.course.%s.title' % nxt)), bodyW - 48, 'STRONG'), bodyX + 40, y + 17, 0xFFF4EEDD, 'STRONG')
        yy = y + 30
        for l in lines:
            g.text(l, bodyX + 40, yy, 0xFFCFC8B4, 'BODY')
            yy += 11
        sw = max(6, (bodyW - 48 - 15) // 6)
        for i, (c, _) in enumerate(CURRICULUM):
            sx = bodyX + 40 + i * (sw + 3)
            g.fill(sx, yy + 2, sx + sw, yy + 6, GOLD if c in PASSED else 0xFF8F7A4E if c == nxt else 0x40EFE8D7)
        flow(g, bodyX + 40, y + h - bh, bodyW - 46, btns)
        y += h + 10
        section(tr(O + 'asg.courses'))
        for i, (c, icon) in enumerate(CURRICULUM):
            now = c == nxt
            g.fill(bodyX, y, bodyX + bodyW, y + 28, 0x26B99859 if now else 0x14000000)
            if now:
                g.fill(bodyX, y, bodyX + 2, y + 28, GOLD)
            g.text(str(i + 1), bodyX + 10, y + 10, GOLD_DARK, 'SMALL')
            if icon:
                g.item(icon, bodyX + 24, y + 6)
            st = 'PASSED' if c in PASSED else 'NEXT' if now else ''
            pw = 0
            if st:
                pw = width(st, 'SMALL') + 8
                g.fill(bodyX + bodyW - 6 - pw, y + 8, bodyX + bodyW - 6, y + 19, GREEN if c in PASSED else GOLD_DARK)
                g.text(st, bodyX + bodyW - 6 - pw + 4, y + 9.5, 0xFFF4EEDD, 'SMALL')
                pw += 6
            g.text(fit(tr('academy.frontierhunts.course.%s.title' % c), bodyW - 52 - pw, 'STRONG'), bodyX + 46, y + 5, INK_BROWN, 'STRONG')
            g.text(fit(tr('academy.frontierhunts.course.%s.tagline' % c), bodyW - 52, 'SMALL'), bodyX + 46, y + 17, INK, 'SMALL')
            y += 31
        y += 4
        section(tr(O + 'asg.field'))
        lines = wrap(tr(O + 'asg.field.none'), bodyW - 34, 'SMALL')
        h = max(24, len(lines) * 10 + 10)
        g.fill(bodyX, y, bodyX + bodyW, y + h, 0x30B99859)
        g.fill(bodyX, y, bodyX + 2, y + h, GOLD)
        g.img.alpha_composite(icon_img.resize((16 * SC, 16 * SC), Image.LANCZOS), (int((bodyX + 7) * SC), int((y + (h - 16) / 2) * SC))) if g.clip[1] <= y <= g.clip[3] - 16 else None
        yy = y + (h - len(lines) * 10) // 2 + 1
        for l in lines:
            g.text(l, bodyX + 28, yy, INK_BROWN, 'SMALL')
            yy += 10
        y += h + 8
        para(tr(O + 'asg.footer', 'K'), 'SMALL', MUTED, 4)
    else:
        for i in range(1, 5):
            k = O + 'step.%d.p%d' % (page, i)
            if k in LANG:
                para(tr(k, 'H'), 'BODY', INK, 6)
        section(tr(O + 'section.make'))
        # recipe cards (sizes only; 3x3 grids)
        cards = {2: [(1, 3, 'frontierhunts:primitive_arrow'), (1, 3, 'frontierhunts:field_arrow'), (3, 3, 'frontierhunts:field_bow')],
                 3: [(3, 3, 'frontierhunts:bow_tuning_rack'), (2, 1, 'frontierhunts:field_point'), (2, 1, 'frontierhunts:fixed_broadhead'), (2, 1, 'frontierhunts:field_arrow')]}.get(page, [])
        x = bodyX
        rowh = 0
        for (w, hh, iid) in cards:
            cw = 6 + w * 18 + 26 + 18 + 6 + 6
            ch = 14 + max(hh * 18, 24) + 13
            if x > bodyX and x + cw > bodyX + bodyW:
                y += rowh + 7
                x = bodyX
                rowh = 0
            g.fill(x, y, x + cw, y + ch, 0x1E8F6E2F)
            g.text(fit(iid.split(':')[1].replace('_', ' ').title(), cw - 10, 'SMALL'), x + 6, y + 4, INK_BROWN, 'SMALL')
            top_ = y + 14 + max(0, (24 - hh * 18) // 2)
            for r in range(hh):
                for c in range(w):
                    g.fill(x + 6 + c * 18, top_ + r * 18, x + 6 + c * 18 + 18, top_ + r * 18 + 18, 0xFF9C8B68)
                    g.fill(x + 7 + c * 18, top_ + r * 18 + 1, x + 6 + c * 18 + 17, top_ + r * 18 + 17, 0xFFD7CDB2)
            ax = x + 6 + w * 18 + 5
            ay = y + 14 + max(hh * 18, 24) // 2
            g.fill(ax, ay - 1, ax + 14, ay + 1, GOLD_DARK)
            g.fill(ax + 21, ay - 12, ax + 45, ay + 12, 0xFF9C8B68)
            g.fill(ax + 22, ay - 11, ax + 44, ay + 11, 0xFFE4D6AE)
            g.item(iid, ax + 25, ay - 8)
            g.text(fit(tr(O + 'station.table'), cw - 10, 'SMALL'), x + 6, y + ch - 11, MUTED, 'SMALL')
            x += cw + 8
            rowh = max(rowh, ch)
        y += rowh + 7
        section(tr(O + 'section.do'))
        for t in [t for t in TASKS if t[1] == page]:
            how = wrap(tr(O + 'task.%s.how' % t[0]), bodyW - 34, 'SMALL')
            btns = []
            if t[2]:
                btns.append((tr(O + 'btn.lesson'), False))
            if t[3]:
                btns.append((tr(O + 'btn.practice', 'The Archery Range'), True))
            if not t[2] and t[0] not in DONE:
                btns.append((tr(O + 'btn.skip_confirm'), False))
            bh = flow_h([b[0] for b in btns], bodyW - 34) + 4 if btns else 0
            h = 19 + len(how) * 10 + bh + 4
            g.fill(bodyX, y, bodyX + bodyW, y + h, 0x34B99859 if t[0] == 'arrows' else 0x18000000)
            g.fill(bodyX + 7, y + 6, bodyX + 20, y + 19, 0xFFB9AE8E)
            g.item(t[4], bodyX + bodyW - 22, y + 4)
            g.text(fit(tr(O + 'task.%s.title' % t[0]), bodyW - 54, 'STRONG'), bodyX + 26, y + 6, INK_BROWN, 'STRONG')
            yy = y + 19
            for l in how:
                g.text(l, bodyX + 26, yy, INK, 'SMALL')
                yy += 10
            if btns:
                flow(g, bodyX + 26, y + h - bh, bodyW - 34, [(b[0].replace(tr(O + 'btn.skip_confirm'), tr(O + 'btn.skip')), b[1]) for b in btns])
            y += h + 5
    g.clip = None
    # [onebook] footer: the books inside the Handbook (icon, name, key)
    by, bh, gap = top + panelH - 26, 19, 4
    bw = (bodyW - gap * 3) // 4
    for i, (icon, stem, key) in enumerate(BOOKS):
        bx = bodyX + i * (bw + gap)
        g.fill(bx, by, bx + bw, by + bh, BUTTON)
        g.fill(bx + 3, by, bx + bw - 3, by + 1, 0x50E2C27A)
        atlas_icon(g, icon, bx + 4, by + 3.5, 12)
        label = tr(B + 'book.%s.short' % stem)
        kw = width(key, 'SMALL') + 5 if key else 0
        if kw and width(label, 'SMALL') + kw > bw - 21:
            kw = 0
        g.text(fit(label, bw - 21 - kw, 'SMALL'), bx + 19, by + 5.5, 0xFFEFE8D7, 'SMALL')
        if kw:
            g.text(key, bx + bw - 4 - width(key, 'SMALL'), by + 5.5, 0xFFE2C27A, 'SMALL')
    return g.img


icon_img = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/handbook/assignments_tab.png')).convert('RGBA')
os.makedirs(OUT, exist_ok=True)
for page, scroll in ((0, 0), (0, int(sys.argv[7]) if len(sys.argv) > 7 else 230), (2, 0), (8, 0), (9, 0)):
    p = os.path.join(OUT, 'handbook_%dx%d_p%d%s.png' % (GW, GH, page, '_s%d' % scroll if scroll else ''))
    render(page, scroll).convert('RGB').save(p)
    print('wrote', p)
