"""[benches] Offline layout mock of the bench screen (client/BenchScreen). Mirrors the layout constants and colours of
benches/client/BenchScreen.java with the real Inter fonts from the jar, real flat item textures where the item has one
(vanilla stand-ins for the 3D-rendered ones) and the real bench emblems. A layout and colour check, not the renderer.

python3 tools/benches/mock_bench.py <repo> <unzipped jar dir> <vanilla textures dir> <out dir> [scale]
"""
import glob, json, math, os, random, re, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT, JAR, MC, OUT = sys.argv[1:5]
SC = int(sys.argv[5]) if len(sys.argv) > 5 else 3
GW, GH = 640, 360
os.makedirs(OUT, exist_ok=True)
FONTS = os.path.join(JAR, 'assets/frontierhunts/font')

# ------------------------------------------------------------------ lang (jar + every merge fragment)
LANG = json.load(open(os.path.join(JAR, 'assets/frontierhunts/lang/en_us.json')))
for f in sorted(glob.glob(os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/lang/en_us.json/*.json'))):
    LANG.update({k: v for k, v in json.load(open(f)).items() if v is not None})
VLANG = json.load(open(os.path.join(MC, 'assets/minecraft/lang/en_us.json')))


def tr(k, *a):
    s = LANG.get(k) or VLANG.get(k) or k
    for x in a:
        s = s.replace('%s', str(x), 1)
    return s


def ttf(name, size):
    f = ImageFont.truetype(os.path.join(FONTS, name), 100)
    a, d = f.getmetrics()
    return ImageFont.truetype(os.path.join(FONTS, name), max(4, round(size * SC * 100 / (a + d))))


F = {'SMALL': ttf('inter_semibold.ttf', 6.4), 'BODY': ttf('inter_medium.ttf', 8.0), 'STRONG': ttf('inter_semibold.ttf', 8.2),
     'TITLE': ttf('inter_bold.ttf', 12.0)}
SHIFT = {'SMALL': -1.34, 'BODY': -0.18, 'STRONG': -0.03, 'TITLE': 2.74}
LH = {'SMALL': 8, 'BODY': 10, 'STRONG': 10, 'TITLE': 14}

# ------------------------------------------------------------------ constants (mirror BenchScreen.java)
PANEL_MAX_W, PANEL_MAX_H = 624, 344
HEAD_H, FOOT_H, PAD = 44, 22, 8
RAIL_W, DETAIL_W = 122, 198
TAB_H, TAB_GAP = 28, 3
COLS, TILE_W, TILE_H, TILE_GAP = 5, 48, 56, 4
SECTION_H = 14
GRID_TOP = 26
MAT_ROW = 18

PANEL, PANEL_RIM, OUTER = 0xFF1D1A16, 0xFF3A332A, 0xFF0A0907
INSET, INSET_EDGE = 0xFF141210, 0xFF2B261F
TEXT, MUTED, DIM = 0xFFF2E9D8, 0xFFA89D88, 0xFF8A806D
TILE, TILE_HOT, TILE_SEL, TILE_OFF = 0xFF27221B, 0xFF332C23, 0xFF3D3428, 0xFF201C17
READY, MISSING = 0xFF8FD694, 0xFFE58068
PAPER, PAPER_EDGE, PAPER_SHADE, INK, INK_MUTED, GOLD_DARK = 0xFFEEE6D2, 0xFFB7A47C, 0xFFE2D7BE, 0xFF2A2219, 0xFF6E624F, 0xFF8F6E2F
GREEN_INK, RED_INK = 0xFF3D7A39, 0xFFB23A2B
BENCH = {
    'frontier_workbench': dict(accent=0xFFD9A54E, deep=0xFF2B2215, sub='bench.frontierhunts.frontier_workbench.subtitle'),
    'gunsmith_bench': dict(accent=0xFF6DB383, deep=0xFF15241C, sub='bench.frontierhunts.gunsmith_bench.subtitle'),
    'reloading_bench': dict(accent=0xFFE0674C, deep=0xFF2A1714, sub='bench.frontierhunts.reloading_bench.subtitle'),
}


def col(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255)


def mix(a, b, t):
    ca, cb = col(a), col(b)
    r = [int(ca[i] + (cb[i] - ca[i]) * t) for i in range(4)]
    return (r[3] << 24) | (r[0] << 16) | (r[1] << 8) | r[2]


def alpha(c, a):
    return (int(a * 255) << 24) | (c & 0xFFFFFF)


_m = ImageDraw.Draw(Image.new('RGBA', (4, 4)))


def width(s, size):
    return _m.textlength(s, font=F[size]) / SC


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
        if width(t, size) > w and line:
            out.append(line)
            line = word
        else:
            line = t
    if line:
        out.append(line)
    return out


class Canvas:
    def __init__(self, bg):
        self.img = Image.open(bg).convert('RGBA').resize((GW * SC, GH * SC)) if bg and os.path.exists(bg) else Image.new('RGBA', (GW * SC, GH * SC), (60, 74, 60, 255))
        # vanilla's transparent menu background
        self.img.alpha_composite(Image.new('RGBA', self.img.size, (16, 16, 16, 150)))

    def layer(self):
        return Image.new('RGBA', self.img.size, (0, 0, 0, 0))

    def rect(self, x, y, w, h, r, c):
        if w <= 0 or h <= 0 or (c >> 24) == 0:
            return
        L = self.layer()
        ImageDraw.Draw(L).rounded_rectangle([round(x * SC), round(y * SC), round((x + w) * SC) - 1, round((y + h) * SC) - 1], radius=max(0, r * SC), fill=col(c))
        self.img.alpha_composite(L)

    def outline(self, x, y, w, h, r, c, t=1.0):
        L = self.layer()
        ImageDraw.Draw(L).rounded_rectangle([round(x * SC), round(y * SC), round((x + w) * SC) - 1, round((y + h) * SC) - 1], radius=max(0, r * SC),
                                            outline=col(c), width=max(1, round(t * SC)))
        self.img.alpha_composite(L)

    def shadow(self, x, y, w, h, r, s):
        L = self.layer()
        ImageDraw.Draw(L).rounded_rectangle([x * SC, (y + s * 0.35) * SC, (x + w) * SC, (y + h + s * 0.35) * SC], radius=r * SC, fill=(0, 0, 0, 150))
        self.img.alpha_composite(L.filter(ImageFilter.GaussianBlur(s * SC * 0.6)))

    def grad(self, x, y, w, h, c0, c1, horizontal=False):
        L = self.layer()
        d = ImageDraw.Draw(L)
        n = int((w if horizontal else h) * SC)
        for i in range(n):
            t = i / max(1, n - 1)
            c = col(mix(c0, c1, t))
            if horizontal:
                d.line([x * SC + i, y * SC, x * SC + i, (y + h) * SC - 1], fill=c)
            else:
                d.line([x * SC, y * SC + i, (x + w) * SC - 1, y * SC + i], fill=c)
        self.img.alpha_composite(L)

    def circle(self, cx, cy, r, c):
        self.rect(cx - r, cy - r, 2 * r, 2 * r, r, c)

    def line(self, x0, y0, x1, y1, wd, c):
        L = self.layer()
        ImageDraw.Draw(L).line([x0 * SC, y0 * SC, x1 * SC, y1 * SC], fill=col(c), width=max(1, round(wd * SC)))
        self.img.alpha_composite(L)

    def text(self, s, x, y, c, size='BODY'):
        L = self.layer()
        ImageDraw.Draw(L).text((x * SC, (y + SHIFT[size] + 1.6) * SC), s, font=F[size], fill=col(c), anchor='ls' if False else None)
        self.img.alpha_composite(L)
        return width(s, size)

    def center(self, s, cx, y, c, size):
        self.text(s, cx - width(s, size) / 2, y, c, size)

    def right(self, s, rx, y, c, size):
        self.text(s, rx - width(s, size), y, c, size)

    def image(self, im, x, y, s, dim=0.0):
        im = im.resize((max(1, round(s * SC)), max(1, round(s * SC))), Image.NEAREST)
        if dim > 0:
            a = im.split()[3]
            dark = Image.new('RGBA', im.size, (24, 21, 17, 255))
            im = Image.blend(im, dark, dim)
            im.putalpha(a)
        self.img.alpha_composite(im, (round(x * SC), round(y * SC)))


# ------------------------------------------------------------------ items
TEXIDX = {}
for base in (os.path.join(ROOT, 'patch/assets/frontierhunts/textures'), os.path.join(JAR, 'assets/frontierhunts/textures')):
    for f in glob.glob(base + '/**/*.png', recursive=True):
        TEXIDX.setdefault(os.path.basename(f)[:-4], f)
STAND_IN = {'rifles': 'crossbow_standby', 'shotguns': 'crossbow_standby', 'handguns': 'crossbow_standby', 'bows': 'bow', 'blades': 'iron_sword',
            'attachments': 'spyglass', 'cartridges': 'gold_nugget', 'arrows': 'arrow', 'tips': 'flint', 'special': 'firework_rocket',
            'camp': 'campfire', 'building': '../block/spruce_planks', 'range': '../block/target_side', 'hunting': 'goat_horn',
            'fishing': 'fishing_rod', 'vehicles': 'minecart', 'food': 'bread', 'clothing': 'leather_chestplate', 'misc': 'chest_minecart', 'refit': 'arrow', 'fit': 'spyglass',
            'scent': 'bundle'}  # [clothing] pack / quiver are 3D-rendered: a bundle stands in
_icons = {}


def icon(item_id, tab=None):
    key = (item_id, tab)
    if key in _icons:
        return _icons[key]
    ns, path = item_id.split(':')
    f = None
    if ns == 'minecraft':
        p = os.path.join(MC, 'assets/minecraft/textures/item/%s.png' % path)
        f = p if os.path.exists(p) else os.path.join(MC, 'assets/minecraft/textures/block/%s.png' % path)
    elif path in TEXIDX:
        f = TEXIDX[path]
    elif tab:
        f = os.path.join(MC, 'assets/minecraft/textures/item/%s.png' % STAND_IN[tab])
    im = None
    if f and os.path.exists(f):
        im = Image.open(f).convert('RGBA')
        if im.size[1] > im.size[0]:
            im = im.crop((0, 0, im.size[0], im.size[0]))
        if im.size != (16, 16):
            im = im.resize((16, 16), Image.NEAREST)
    _icons[key] = im
    return im


def name(item_id):
    ns, path = item_id.split(':')
    return LANG.get('item.%s.%s' % (ns, path)) or LANG.get('block.%s.%s' % (ns, path)) or VLANG.get('item.minecraft.' + path) or VLANG.get(
        'block.minecraft.' + path) or path.replace('_', ' ').title()


# ------------------------------------------------------------------ the catalogue (parsed from BenchCatalog.java)
SRC = open(os.path.join(ROOT, 'src/com/formaworks/frontierhunts/benches/BenchCatalog.java')).read()
TABSRC = open(os.path.join(ROOT, 'src/com/formaworks/frontierhunts/benches/BenchTab.java')).read()
TABS = []
for m in re.finditer(r'\n   ([A-Z]+)\(Bench\.([A-Z]+), "([a-z_]+)", "([a-z_:]+)", Kind\.([A-Z]+)\)', TABSRC):
    TABS.append(dict(enum=m.group(1), bench={'FRONTIER': 'frontier_workbench', 'GUNSMITH': 'gunsmith_bench', 'RELOADING': 'reloading_bench'}[m.group(2)],
                     key=m.group(3), icon=m.group(4), kind=m.group(5)))
PLACE = {}
order = 0
for m in re.finditer(r'put\(BenchTab\.([A-Z]+), "([a-z_]+)",((?:\s*"[a-z0-9_:]+",?)+)\);', SRC):
    for i in re.findall(r'"([a-z0-9_:]+)"', m.group(3)):
        full = i if ':' in i else 'frontierhunts:' + i
        PLACE[full] = (m.group(1), m.group(2), order)
        order += 1
RECIPES = json.load(open(os.path.join(ROOT, 'tools/benches/recipes.json')))
# [smalls] the heads (ArrowTip order and titles) and which head an arrow recipe puts on its shafts
TIP_ORDER = ['field_point', 'fixed_broadhead', 'mechanical_broadhead', 'cut_on_contact_broadhead', 'judo_point', 'flint_point', 'obsidian_point',
             'bone_point', 'tracer_broadhead', 'tracer_field_point', 'tracer_ice_broadhead']
TIP_TITLE = dict(zip(TIP_ORDER, ['Field Point', 'Fixed-Blade Broadhead', 'Mechanical Broadhead', 'Cut-on-Contact Broadhead', 'Judo Point',
                                 'Knapped Flint Point', 'Obsidian Point', 'Bone Point', 'Tracer Broadhead', 'Tracer Field Point',
                                 'Glacier Tracer Broadhead']))
STOCK_TIP = {'frontierhunts:field_arrow': 'fixed_broadhead', 'frontierhunts:primitive_arrow': 'flint_point'}
ENTRIES = []
for r in RECIPES:
    p = PLACE.get(r['result'])
    if p:
        e = dict(id=r['recipe'], item=r['result'], count=r['count'], tab=p[0], section=p[1], order=p[2])
        if r['result'] in STOCK_TIP:
            m = re.match(r'frontierhunts:arrows/[a-z]+_arrow_([a-z_]+)$', r['recipe'])
            e['tip'] = m.group(1) if m else STOCK_TIP[r['result']]
            e['fitted'] = bool(m)
            e['sub'] = TIP_ORDER.index(e['tip']) if m else -1
        ENTRIES.append(e)
for f in sorted(glob.glob(os.path.join(JAR, 'data/frontierhunts/recipe/clothing_table/*.json'))):
    d = json.load(open(f))
    rid = d['result']['id']
    sew = d.get('sew')
    p = ('CLOTHING', 'sewing', 50000) if sew else PLACE.get(rid)
    ENTRIES.append(dict(id='frontierhunts:clothing_table/' + os.path.basename(f)[:-5], item=rid, count=d['result'].get('count', 1), tab=p[0],
                        section=p[1], order=p[2], sew=sew))
TAB_ORDER = [t['enum'] for t in TABS]
ENTRIES.sort(key=lambda e: (TAB_ORDER.index(e['tab']), e['order'], e.get('sub', -1), e['id']))
rnd = random.Random(7)
for e in ENTRIES:
    e['ready'] = rnd.random() < 0.38


def tab_of(enum):
    return next(t for t in TABS if t['enum'] == enum)


# ------------------------------------------------------------------ widgets
def button(cv, x, y, w, h, label, look, accent, active=True, hot=False, key=None):
    if look == 'PRIMARY':
        bg = accent if active else 0xFF5A5246
        if hot and active:
            bg = mix(accent, 0xFFFFFFFF, 0.12)
        fg = 0xFF1A140E if active else 0xFF9A907E
        if active:
            cv.rect(x, y + 1.5, w, h, 4, 0x40000000)
        cv.rect(x, y, w, h, 4, mix(bg, 0xFF000000, 0.35))
        cv.rect(x + 1, y + 1, w - 2, h - 2, 3, bg)
        if active:
            cv.rect(x + 2, y + 1, w - 4, 1.5, 0, 0x55FFFFFF)
    elif look == 'PAPER':  # secondary on the paper card
        cv.rect(x, y, w, h, 4, 0xFFB5A780 if active else 0xFFCFC4A8)
        cv.rect(x + 1, y + 1, w - 2, h - 2, 3, 0xFFF6F0E1 if not hot else 0xFFFFFBF0)
        fg = INK if active else 0xFFA79C84
    else:  # DARK
        cv.rect(x, y, w, h, 4, 0xFF3E362C)
        cv.rect(x + 1, y + 1, w - 2, h - 2, 3, 0xFF2A241D if not hot else 0xFF342D24)
        fg = TEXT
    tw = width(label, 'STRONG')
    kw = width(key, 'SMALL') + 8 if key else 0
    tx = x + (w - tw - kw) / 2
    cv.text(label, tx, y + (h - 9) / 2 + 0.5, fg, 'STRONG')
    if key:
        kx = tx + tw + 5
        cv.rect(kx, y + h / 2 - 4.5, kw - 2, 9, 2, alpha(0xFF000000, 0.18) if active else 0x10000000)
        cv.text(key, kx + 3, y + h / 2 - 3.5, fg, 'SMALL')


def pill(cv, text, x, y, bg, fg, size='SMALL'):
    w = width(text, size) + 8
    cv.rect(x, y, w, 10, 5, bg)
    cv.text(text, x + 4, y + 1.5, fg, size)
    return w


def check(cv, cx, cy, c, k=1.0):
    for i in range(5):
        cv.rect(cx + (-3.2 + i * 0.55) * k, cy + (-0.2 + i * 0.55) * k, 1.3 * k, 1.3 * k, 0.3, c)
    for i in range(9):
        cv.rect(cx + (-0.9 + i * 0.55) * k, cy + (2.0 - i * 0.7) * k, 1.3 * k, 1.3 * k, 0.3, c)


def magnifier(cv, cx, cy, c):
    cv.outline(cx - 3, cy - 3, 6, 6, 3, c, 1.0)
    cv.line(cx + 1.8, cy + 1.8, cx + 4, cy + 4, 1.2, c)


# ------------------------------------------------------------------ the screen
class Layout:
    def __init__(self, W=GW, H=GH):
        self.pw, self.ph = min(PANEL_MAX_W, W - 16), min(PANEL_MAX_H, H - 16)
        self.l, self.t = (W - self.pw) // 2, (H - self.ph) // 2
        self.bodyY = self.t + HEAD_H + PAD
        self.bodyH = self.ph - HEAD_H - PAD - FOOT_H
        self.railX = self.l + PAD
        self.gridX = self.railX + RAIL_W + PAD
        self.gridW = self.pw - PAD * 4 - RAIL_W - DETAIL_W
        self.detX = self.l + self.pw - PAD - DETAIL_W
        self.wideW = self.l + self.pw - PAD - self.gridX


def frame(cv, Lo, bench):
    B = BENCH[bench]
    l, t, w, h = Lo.l, Lo.t, Lo.pw, Lo.ph
    cv.shadow(l, t, w, h, 8, 9)
    cv.rect(l - 1, t - 1, w + 2, h + 2, 8, OUTER)
    cv.rect(l, t, w, h, 7, PANEL)
    # header band: the bench's deep tone, lit from the left, with the accent rule under it
    top = mix(B['deep'], B['accent'], 0.20)
    cv.rect(l, t, w, 10, 7, top)
    cv.grad(l, t + 6, w, HEAD_H - 6, top, B['deep'])
    cv.circle(l + 26, t + HEAD_H / 2, 21, alpha(B['accent'], 0.12))
    cv.rect(l, t + HEAD_H - 1, w, 2, 0, B['accent'])
    cv.rect(l, t + HEAD_H + 1, w, 3, 0, 0x30000000)
    # emblem disc
    cv.circle(l + 26, t + HEAD_H / 2, 17, 0x33000000)
    em = Image.open(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/bench/%s_emblem.png' % bench)).convert('RGBA')
    cv.img.alpha_composite(em.resize((34 * SC, 34 * SC), Image.LANCZOS), ((l + 9) * SC, int((t + HEAD_H / 2 - 17) * SC)))
    cv.text(tr('block.frontierhunts.' + bench), l + 50, t + 9, TEXT, 'TITLE')
    cv.text(tr(B['sub']), l + 50, t + 27, mix(TEXT, B['accent'], 0.35), 'SMALL')
    # close
    cx, cy = l + w - PAD - 18, t + (HEAD_H - 18) / 2
    cv.rect(cx, cy, 18, 18, 4, 0x26000000)
    cv.line(cx + 6, cy + 6, cx + 12, cy + 12, 1.4, TEXT)
    cv.line(cx + 12, cy + 6, cx + 6, cy + 12, 1.4, TEXT)
    # footer
    fy = t + h - FOOT_H
    cv.rect(l + PAD, fy, w - 2 * PAD, 1, 0, 0xFF2E2821)
    return fy


def search_and_filter(cv, Lo, bench, query, can_only, enabled=True):
    B = BENCH[bench]
    l, t, w = Lo.l, Lo.t, Lo.pw
    cx = l + w - PAD - 18
    fw = 72
    fx = cx - 6 - fw
    sw = 150
    sx = fx - 6 - sw
    y = t + (HEAD_H - 18) / 2
    if not enabled:
        return
    cv.rect(sx, y, sw, 18, 9, 0x40000000)
    cv.outline(sx, y, sw, 18, 9, 0x30FFFFFF if not query else alpha(B['accent'], 0.9), 1)
    magnifier(cv, sx + 11, y + 8.5, MUTED if not query else TEXT)
    if query:
        tw = cv.text(query, sx + 20, y + 4.5, TEXT, 'BODY')
        cv.rect(sx + 21 + tw, y + 4, 0.8, 10, 0, TEXT)
        cv.text('×', sx + sw - 12, y + 4, MUTED, 'BODY')
    else:
        cv.text(tr('bench.frontierhunts.ui.search'), sx + 20, y + 4.5, DIM, 'BODY')
    # can-craft toggle
    on = can_only
    cv.rect(fx, y, fw, 18, 9, alpha(B['accent'], 0.85) if on else 0x40000000)
    if not on:
        cv.outline(fx, y, fw, 18, 9, 0x30FFFFFF, 1)
    kx = fx + 6
    cv.circle(kx + 5, y + 9, 5, 0xFF1A140E if on else 0x50FFFFFF)
    if on:
        check(cv, kx + 5, y + 8.5, B['accent'], 0.85)
    cv.text(tr('bench.frontierhunts.ui.can_craft'), kx + 13, y + 4.5, 0xFF1A140E if on else MUTED, 'STRONG')


def rail(cv, Lo, bench, tabs, cur, counts):
    B = BENCH[bench]
    x, y, w, h = Lo.railX, Lo.bodyY, RAIL_W, Lo.bodyH
    cv.rect(x, y, w, h, 6, INSET_EDGE)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 5, INSET)
    n = len(tabs)
    th = min(TAB_H, (h - 10 - (n - 1) * TAB_GAP) // n)
    ty = y + 5
    for t in tabs:
        sel = t['enum'] == cur
        tx = x + 5
        tw = w - 10
        if sel:
            cv.rect(tx, ty, tw, th, 5, mix(TILE_SEL, B['accent'], 0.10))
            cv.rect(tx, ty + 5, 2.5, th - 10, 1.2, B['accent'])
        ic = icon(t['icon'], t['key'])
        if ic:
            cv.image(ic, tx + 8, ty + (th - 16) / 2, 16, 0 if sel else 0.15)
        label = tr('bench.frontierhunts.tab.' + t['key'])
        c = counts.get(t['enum'])
        cw = 0
        if c is not None:
            s = c
            cw = width(s, 'SMALL') + 8
            bg = alpha(B['accent'], 0.22) if sel else 0x14FFFFFF
            cv.rect(tx + tw - 5 - cw, ty + (th - 10) / 2, cw, 10, 5, bg)
            cv.text(s, tx + tw - 5 - cw + 4, ty + (th - 10) / 2 + 1.5, TEXT if sel else MUTED, 'SMALL')
        cv.text(fit(label, tw - 32 - cw - 6, 'STRONG'), tx + 30, ty + (th - 9) / 2 + 0.5, TEXT if sel else mix(MUTED, TEXT, 0.25), 'STRONG')
        ty += th + TAB_GAP


def tile(cv, x, y, e, sel=False, hot=False, accent=0):
    ready = e['ready']
    bg = TILE_SEL if sel else (TILE_HOT if hot else (TILE if ready else TILE_OFF))
    if sel:
        cv.rect(x - 1.5, y - 1.5, TILE_W + 3, TILE_H + 3, 6.5, accent)
    elif hot:
        cv.rect(x - 1, y - 1, TILE_W + 2, TILE_H + 2, 6, 0x29FFFFFF)
    cv.rect(x, y, TILE_W, TILE_H, 5, bg)
    # icon well
    cv.rect(x + (TILE_W - 30) / 2, y + 4, 30, 28, 4, 0x22000000 if ready else 0x18000000)
    ic = icon(e['item'], tab_of(e['tab'])['key'])
    if ic:
        cv.image(ic, x + (TILE_W - 24) / 2, y + 6, 24, 0 if ready else (0.35 if sel else 0.55))
    if e['count'] > 1:
        s = '×%d' % e['count']
        cv.right(s, x + TILE_W - 5, y + 25, TEXT if ready else DIM, 'SMALL')
    if ready:
        cv.circle(x + TILE_W - 6, y + 6, 2.6, READY)
    nm = tr('bench.frontierhunts.ui.sew.' + e['sew']) if e.get('sew') else name(e['item'])
    if e.get('tip'):  # [smalls] an arrow tile names its head (the section header names the shaft) and shows it as a badge
        nm = TIP_TITLE[e['tip']]
        hi = icon('frontierhunts:' + e['tip'], 'tips')
        if hi:
            cv.image(hi, x + (TILE_W - 30) / 2 + 2, y + 6, 10, 0 if ready else (0.35 if sel else 0.55))
    lines = wrap(nm, TILE_W - 6, 'SMALL')
    if len(lines) > 2:
        lines = [lines[0], fit(' '.join(lines[1:]), TILE_W - 6, 'SMALL')]
    lines = [fit(s, TILE_W - 6, 'SMALL') for s in lines]
    ly = y + 35 if len(lines) == 2 else y + 39
    for s in lines:
        cv.center(s, x + TILE_W / 2, ly, TEXT if sel else (mix(TEXT, MUTED, 0.2) if ready else DIM), 'SMALL')
        ly += 8


def grid(cv, Lo, bench, tab, entries, sel_id, hot_id, scroll=0, query=''):
    B = BENCH[bench]
    x, y, w, h = Lo.gridX, Lo.bodyY, Lo.gridW, Lo.bodyH
    cv.rect(x, y, w, h, 6, INSET_EDGE)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 5, INSET)
    t = tab_of(tab)
    if query:
        title = tr('bench.frontierhunts.ui.results', len(entries))
        hint = tr('bench.frontierhunts.ui.results_hint')
    else:
        title = tr('bench.frontierhunts.tab.' + t['key'])
        hint = tr('bench.frontierhunts.tab.' + t['key'] + '.hint')
    cv.text(title, x + 9, y + 7, TEXT, 'STRONG')
    cv.text(fit(hint, w - 18 - width(title, 'STRONG') - 8, 'SMALL'), x + 9 + width(title, 'STRONG') + 7, y + 8.5, DIM, 'SMALL')
    gx = x + (w - (COLS * TILE_W + (COLS - 1) * TILE_GAP)) / 2
    yy = y + GRID_TOP - scroll
    bottom = y + h - 4
    sections = []
    for e in entries:
        key = e['tab'] if query else e['section']
        if not sections or sections[-1][0] != key:
            sections.append((key, []))
        sections[-1][1].append(e)
    single = len(sections) == 1 and not query
    for key, es in sections:
        if not single:
            label = tr('bench.frontierhunts.tab.' + tab_of(key)['key']) if query else tr('bench.frontierhunts.section.' + key)
            if yy + SECTION_H > y + GRID_TOP - 2 and yy < bottom:
                cv.text(label.upper(), gx + 1, yy + 3, mix(B['accent'], TEXT, 0.25), 'SMALL')
                lw = width(label.upper(), 'SMALL')
                cv.rect(gx + lw + 7, yy + 6.5, COLS * TILE_W + (COLS - 1) * TILE_GAP - lw - 8, 1, 0, 0xFF2D2720)
            yy += SECTION_H
        for i, e in enumerate(es):
            cx = gx + (i % COLS) * (TILE_W + TILE_GAP)
            cy = yy + (i // COLS) * (TILE_H + TILE_GAP)
            if cy + TILE_H <= bottom and cy >= y + GRID_TOP - 2:
                tile(cv, cx, cy, e, e['id'] == sel_id, e['id'] == hot_id, B['accent'])
        yy += ((len(es) + COLS - 1) // COLS) * (TILE_H + TILE_GAP) + 2
    total = yy + scroll - (y + GRID_TOP)
    view = bottom - (y + GRID_TOP)
    if total > view:
        bh = max(20, view * view / total)
        by = y + GRID_TOP + (view - bh) * (scroll / max(1, total - view))
        cv.rect(x + w - 6, y + GRID_TOP, 3, view, 1.5, 0x18FFFFFF)
        cv.rect(x + w - 6, by, 3, bh, 1.5, alpha(B['accent'], 0.75))


MATS = {
    'frontierhunts:field_bow': [('minecraft:stick', 3, 7), ('minecraft:string', 2, 1), ('minecraft:leather', 1, 2)],
    'frontierhunts:ridgeline_rifle': [('minecraft:iron_block', 1, 0), ('minecraft:iron_ingot', 3, 5), ('minecraft:tripwire_hook', 1, 1), ('minecraft:spruce_planks', 2, 12)],
    'frontierhunts:rifle_round': [('minecraft:copper_ingot', 1, 6), ('minecraft:gunpowder', 1, 4), ('minecraft:iron_nugget', 1, 9)],
    'frontierhunts:ghillie_jacket': [('minecraft:string', 3, 8), ('minecraft:vine', 2, 2), ('minecraft:leather', 1, 3), ('minecraft:moss_carpet', 2, 0)],
    'frontierhunts:pemmican': [('frontierhunts:jerky', 2, 5), ('frontierhunts:tallow', 1, 2), ('minecraft:sweet_berries', 1, 9)],
}


def detail(cv, Lo, bench, e, held=None):
    B = BENCH[bench]
    x, y, w, h = Lo.detX, Lo.bodyY, DETAIL_W, Lo.bodyH
    cv.rect(x, y, w, h, 6, PAPER_EDGE)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 5, PAPER)
    cv.grad(x + 1, y + 1, w - 2, 54, 0xFFF6F0E0, PAPER)
    # icon frame
    cv.rect(x + 10, y + 10, 40, 40, 5, 0xFFD9CDB1)
    cv.rect(x + 11, y + 11, 38, 38, 4, 0xFFE6DCC5)
    ic = icon(e['item'], tab_of(e['tab'])['key'])
    if ic:
        cv.image(ic, x + 14, y + 14, 32)
    nm = tr('bench.frontierhunts.ui.sew.' + e['sew']) if e.get('sew') else name(e['item'])
    if e.get('tip'):
        nm = ('Primitive Arrow' if e['item'].endswith('primitive_arrow') else 'Hunting Arrow') + ' · ' + TIP_TITLE[e['tip']]
    ly = y + 12
    lines = wrap(nm, w - 68, 'STRONG')[:2]
    for s in lines:
        cv.text(s, x + 58, ly, INK, 'STRONG')
        ly += 10
    meta = tr('bench.frontierhunts.tab.' + tab_of(e['tab'])['key']) + ' · ' + tr('bench.frontierhunts.section.' + e['section'])
    mx = x + 58
    if e['count'] > 1:
        mx += pill(cv, tr('bench.frontierhunts.ui.makes', e['count']), mx, ly + 2, alpha(B['accent'], 0.28), INK) + 4
    else:
        cv.text(fit(meta, w - 68, 'SMALL'), mx, ly + 3.5, INK_MUTED, 'SMALL')
    # description
    desc = {'frontierhunts:field_bow': 'A light recurve for your first season. Steady at short range; draws fast.',
            'frontierhunts:ridgeline_rifle': 'A bolt-action hunting rifle with a 3-9× factory scope. Five-round magazine with the extended mag.',
            'frontierhunts:rifle_round': '.270 soft-point cartridges for the field rifles. Packs of 8.',
            'frontierhunts:ghillie_jacket': 'Breaks up your outline in forest. Animals see you later; worn with hood and trousers it counts double.',
            'frontierhunts:pemmican': 'Dried meat pounded with fat and berries. Keeps for a whole season and fills you up.'}.get(e['item'], 'Shown with the item\'s own description, from its tooltip lines.')
    if e.get('tip'):
        desc = 'Head: %s. Blades open on impact: the widest wound and the heaviest blood trail.' % TIP_TITLE[e['tip']]
    dy = y + 58
    for s in wrap(desc, w - 20, 'SMALL')[:4]:
        cv.text(s, x + 10, dy, INK_MUTED, 'SMALL')
        dy += 9
    dy += 5
    cv.text(tr('bench.frontierhunts.ui.materials'), x + 10, dy, GOLD_DARK, 'SMALL')
    cv.right(tr('bench.frontierhunts.ui.have_need'), x + w - 10, dy, GOLD_DARK, 'SMALL')
    dy += 10
    cv.rect(x + 10, dy, w - 20, 1, 0, 0xFFD3C6A6)
    dy += 4
    mats = MATS.get(e['item'], [('minecraft:iron_ingot', 2, 3), ('minecraft:string', 1, 0)])
    if e.get('fitted'):
        mats = [('minecraft:stick', 1, 6), ('minecraft:feather', 1, 3), ('frontierhunts:' + e['tip'], 4, 0)]
    missing = 0
    make = False
    for item, need, have in mats:
        ok = have >= need
        mk = not ok and e.get('fitted') and item.endswith(e['tip'])  # [smalls] heads made from materials when you craft
        make |= bool(mk)
        missing += 0 if ok or mk else 1
        cv.rect(x + 8, dy - 1, w - 16, MAT_ROW, 3, 0x0A000000 if ok or mk else 0x14B23A2B)
        ic = icon(item, 'tips' if item.startswith('frontierhunts:') else None)
        if ic:
            cv.image(ic, x + 11, dy, 16)
        s = '%d / %d' % (have, need)
        pw = width(s, 'STRONG') + 10
        nm2 = TIP_TITLE.get(item.split(':')[1]) or name(item)
        cv.text(fit(nm2, w - 50 - pw, 'BODY'), x + 31, dy + 4, INK, 'BODY')
        cv.rect(x + w - 11 - pw, dy + 2.5, pw, 11, 5.5, 0x263D7A39 if ok else (0x308F6E2F if mk else 0x26B23A2B))
        cv.text(s, x + w - 11 - pw + 5, dy + 3.5, GREEN_INK if ok else (GOLD_DARK if mk else RED_INK), 'STRONG')
        dy += MAT_ROW + 2
    # status + buttons
    by = y + h - 10 - 22
    ready = e['ready'] and missing == 0
    if e.get('sew'):
        st = tr('bench.frontierhunts.ui.sew_target', held) if held else tr('bench.frontierhunts.ui.sew_hold')
        sc = GREEN_INK if held else RED_INK
    elif ready and make:
        st, sc = tr('bench.frontierhunts.ui.ready_heads', 4), GREEN_INK
    elif ready:
        st, sc = tr('bench.frontierhunts.ui.ready'), GREEN_INK
    else:
        st, sc = (tr('bench.frontierhunts.ui.missing_one') if missing == 1 else tr('bench.frontierhunts.ui.missing', missing)), RED_INK
    sy = by - 14
    # [smalls] the tab's hint over the status (mirrors BenchScreen.hintFor)
    hk = {'FOOD': 'bench.frontierhunts.ui.hint.food', 'TIPS': 'bench.frontierhunts.ui.hint.tips'}.get(e['tab'])
    if e['tab'] == 'ARROWS' and e.get('tip'):
        hk = 'bench.frontierhunts.ui.hint.arrow_fitted' if e.get('fitted') else 'bench.frontierhunts.ui.hint.arrow_stock'
    if hk:
        hl = wrap(tr(hk), w - 20, 'SMALL')[:2]
        hy = sy - 4 - 9 * len(hl)
        for s2 in hl:
            cv.text(s2, x + 10, hy, INK_MUTED, 'SMALL')
            hy += 9
    if sc == GREEN_INK:
        check(cv, x + 14, sy + 3.5, sc, 0.8)
    else:
        cv.circle(x + 14, sy + 4, 3.2, sc)
        cv.rect(x + 13.5, sy + 2, 1, 2.6, 0, PAPER)
        cv.rect(x + 13.5, sy + 5.2, 1, 1, 0, PAPER)
    cv.text(fit(st, w - 34, 'SMALL'), x + 21, sy + 0.5, sc, 'SMALL')
    if bench == 'reloading_bench':
        bw = (w - 20 - 6) * 0.62
        button(cv, x + 10, by, bw, 22, tr('bench.frontierhunts.ui.craft'), 'PRIMARY', B['accent'], ready, key='Enter')
        button(cv, x + 10 + bw + 6, by, w - 20 - bw - 6, 22, tr('bench.frontierhunts.ui.craft5'), 'PAPER', B['accent'], ready)
    else:
        label = tr('bench.frontierhunts.ui.sew') if e.get('sew') else tr('bench.frontierhunts.ui.craft')
        button(cv, x + 10, by, w - 20, 22, label, 'PRIMARY', B['accent'], ready or bool(held), key='Enter')


def footer(cv, Lo, bench, fy, left_key='bench.frontierhunts.ui.footer'):
    cv.text(tr(left_key), Lo.l + PAD + 2, fy + 7, DIM, 'SMALL')
    keys = [('Enter', tr('bench.frontierhunts.ui.key.craft')), ('↑↓←→', tr('bench.frontierhunts.ui.key.select')),
            ('Tab', tr('bench.frontierhunts.ui.key.tab')), ('Esc', tr('bench.frontierhunts.ui.key.close'))]
    x = Lo.l + Lo.pw - PAD - 2
    for k, label in reversed(keys):
        lw = width(label, 'SMALL')
        x -= lw
        cv.text(label, x, fy + 7, DIM, 'SMALL')
        kw = width(k, 'SMALL') + 8
        x -= kw + 4
        cv.rect(x, fy + 5.5, kw, 11, 3, 0x1CFFFFFF)
        cv.text(k, x + 4, fy + 7, MUTED, 'SMALL')
        x -= 12


def recipes_view(bench, tab, sel_item, out, query='', can_only=False, scroll=0, hot_item=None):
    cv = Canvas(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/welcome.png'))
    Lo = Layout()
    fy = frame(cv, Lo, bench)
    search_and_filter(cv, Lo, bench, query, can_only)
    tabs = [t for t in TABS if t['bench'] == bench and (t['enum'] != 'MISC')]
    counts = {}
    for t in tabs:
        es = [e for e in ENTRIES if e['tab'] == t['enum']]
        if t['kind'] == 'RECIPES':
            counts[t['enum']] = '%d/%d' % (sum(e['ready'] for e in es), len(es))
    rail(cv, Lo, bench, tabs, None if query else tab, counts)  # searching: no tab is selected
    if query:
        es = [e for e in ENTRIES if tab_of(e['tab'])['bench'] == bench and query.lower() in name(e['item']).lower()]
    else:
        es = [e for e in ENTRIES if e['tab'] == tab]
    if can_only:
        es = [e for e in es if e['ready']]
    sel = next((e for e in es if e['item'] == sel_item or e['item'] + '#' + e.get('tip', '') == sel_item and e.get('fitted')), es[0])
    sel['ready'] = True if sel_item in ('frontierhunts:field_bow', 'frontierhunts:rifle_round') else sel['ready']
    hot = next((e['id'] for e in es if e['item'] == hot_item), None)
    grid(cv, Lo, bench, tab, es, sel['id'], hot, scroll, query)
    detail(cv, Lo, bench, sel)
    footer(cv, Lo, bench, fy)
    cv.img.save(out)
    print('wrote', out)


# ------------------------------------------------------------------ Fit tab
SLOTS = [('optic', ['four_power_optic', 'reflex_sight', 'micro_red_dot', 'holographic_sight', 'two_power_prism', 'six_power_scope', 'eight_power_scope',
                    'twelve_power_scope', 'thermal_scope', 'ridgeline_scope']),
         ('magazine', ['sniper_magazine']), ('bipod', ['bipod'])]


def fit_view(out):
    bench = 'gunsmith_bench'
    B = BENCH[bench]
    cv = Canvas(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/welcome.png'))
    Lo = Layout()
    fy = frame(cv, Lo, bench)
    tabs = [t for t in TABS if t['bench'] == bench]
    counts = {t['enum']: '%d/%d' % (sum(e['ready'] for e in ENTRIES if e['tab'] == t['enum']), sum(1 for e in ENTRIES if e['tab'] == t['enum']))
              for t in tabs if t['kind'] == 'RECIPES'}
    rail(cv, Lo, bench, tabs, 'FIT', counts)
    x, y, w, h = Lo.gridX, Lo.bodyY, Lo.wideW, Lo.bodyH
    cv.rect(x, y, w, h, 6, INSET_EDGE)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 5, INSET)
    cv.text(tr('bench.frontierhunts.tab.fit'), x + 9, y + 7, TEXT, 'STRONG')
    cv.text(tr('bench.frontierhunts.tab.fit.hint'), x + 9 + width(tr('bench.frontierhunts.tab.fit'), 'STRONG') + 7, y + 8.5, DIM, 'SMALL')
    # hotbar picker
    hb = ['frontierhunts:ridgeline_rifle', 'frontierhunts:field_bow', 'frontierhunts:skinning_tool', None, 'frontierhunts:pump_shotgun', 'minecraft:bread',
          None, 'frontierhunts:binoculars', 'minecraft:torch']
    hx = x + w - 9 - 9 * 22 + 2
    cv.right(tr('bench.frontierhunts.fit.hotbar'), hx - 6, y + 9, DIM, 'SMALL')
    for i, it in enumerate(hb):
        sx = hx + i * 22
        weapon = it in ('frontierhunts:ridgeline_rifle', 'frontierhunts:pump_shotgun')
        cv.rect(sx, y + 4, 20, 20, 4, 0xFF2E2820 if weapon else 0xFF1E1A15)
        if i == 0:
            cv.outline(sx - 0.5, y + 3.5, 21, 21, 4.5, B['accent'], 1.5)
        if it:
            ic = icon(it, 'rifles' if 'rifle' in it or 'shotgun' in it else 'hunting')
            if ic:
                cv.image(ic, sx + 2, y + 6, 16, 0 if weapon else 0.6)
    # showcase card
    cy = y + 30
    sw = 176
    ch = h - 30 - 8
    cv.rect(x + 8, cy, sw, ch, 6, PAPER_EDGE)
    cv.rect(x + 9, cy + 1, sw - 2, ch - 2, 5, PAPER)
    cv.grad(x + 9, cy + 1, sw - 2, 90, 0xFFF7F1E2, PAPER)
    cv.text(tr('bench.frontierhunts.fit.in_hand'), x + 18, cy + 9, GOLD_DARK, 'SMALL')
    cv.text(name('frontierhunts:ridgeline_rifle'), x + 18, cy + 19, INK, 'STRONG')
    cv.rect(x + 18, cy + 34, sw - 20, 70, 6, 0xFFE3D8C0)
    ic = icon('frontierhunts:ridgeline_rifle', 'rifles')
    cv.image(ic, x + 8 + sw / 2 - 32, cy + 37, 64)
    iy = cy + 112
    for k, v in [('bench.frontierhunts.fit.slot.optic', name('frontierhunts:six_power_scope')), ('bench.frontierhunts.fit.slot.magazine', tr('bench.frontierhunts.fit.standard')),
                 ('bench.frontierhunts.fit.slot.bipod', tr('bench.frontierhunts.fit.empty'))]:
        cv.text(tr(k), x + 18, iy, INK_MUTED, 'SMALL')
        cv.right(fit(v, 100, 'SMALL'), x + 8 + sw - 10, iy, INK, 'SMALL')
        iy += 11
    cv.rect(x + 18, iy + 3, sw - 20, 1, 0, 0xFFD3C6A6)
    for s in wrap(tr('bench.frontierhunts.fit.note'), sw - 22, 'SMALL'):
        iy += 9
        cv.text(s, x + 18, iy + 2, INK_MUTED, 'SMALL')
    # slot rows (mirror BenchFitPanel.draw)
    rx = x + 8 + sw + 8
    rw = x + w - 8 - rx
    per_row = max(1, int((rw - 16 + 2) // 24))
    ry = cy
    fitted = {'optic': 'six_power_scope', 'magazine': None, 'bipod': None}
    owned = {'four_power_optic': 1, 'reflex_sight': 0, 'micro_red_dot': 2, 'holographic_sight': 0, 'two_power_prism': 0, 'six_power_scope': 0,
             'eight_power_scope': 1, 'twelve_power_scope': 0, 'thermal_scope': 0, 'ridgeline_scope': 1, 'sniper_magazine': 1, 'bipod': 0}
    for slot, parts in SLOTS:
        lines = (len(parts) + per_row - 1) // per_row
        rh = 22 + lines * 24 + 6
        cv.rect(rx, ry, rw, rh, 6, 0xFF221E18)
        cv.text(tr('bench.frontierhunts.fit.slot.' + slot).upper(), rx + 8, ry + 7, mix(B['accent'], TEXT, 0.25), 'SMALL')
        f = fitted[slot]
        fx = rx + 70
        if f:
            ic = icon('frontierhunts:' + f, 'attachments')
            cv.image(ic, fx, ry + 3, 16)
            cv.text(name('frontierhunts:' + f), fx + 20, ry + 6.5, TEXT, 'STRONG')
            button(cv, rx + rw - 8 - 58, ry + 3, 58, 16, tr('bench.frontierhunts.fit.remove'), 'DARK', B['accent'])
        else:
            cv.text(tr('bench.frontierhunts.fit.standard' if slot == 'magazine' else 'bench.frontierhunts.fit.empty'), fx, ry + 6.5, DIM, 'BODY')
        for i, p in enumerate(parts):
            cx = rx + 8 + (i % per_row) * 24
            cyy = ry + 22 + (i // per_row) * 24
            on = p == f
            n = owned.get(p, 0)
            if on:
                cv.rect(cx - 1.5, cyy - 1.5, 25, 25, 5.5, B['accent'])
                cv.rect(cx, cyy, 22, 22, 4, mix(TILE_SEL, B['accent'], 0.25))
            elif n:
                cv.rect(cx, cyy, 22, 22, 4, 0xFF2E2820)
            else:
                cv.rect(cx, cyy, 22, 22, 4, 0xFF3A3229)
                cv.rect(cx + 1, cyy + 1, 20, 20, 3, 0xFF1C1814)
            ic = icon('frontierhunts:' + p, 'attachments')
            cv.image(ic, cx + 3, cyy + 3, 16, 0 if (n or on) else 0.6)
            if n and not on:
                cv.right(str(n), cx + 20.5, cyy + 13, TEXT, 'SMALL')
            if not n and not on:
                cv.circle(cx + 22 - 4.5, cyy + 4.5, 3.5, B['accent'])
                cv.rect(cx + 22 - 6.5, cyy + 4.0, 4, 1, 0, 0xFF1A140E)
                cv.rect(cx + 22 - 5.0, cyy + 2.5, 1, 4, 0, 0xFF1A140E)
        ry += rh + 6
    # hovered chip tooltip-ish caption
    cv.text(tr('bench.frontierhunts.fit.legend'), rx + 2, y + h - 14, DIM, 'SMALL')
    footer(cv, Lo, bench, fy, 'bench.frontierhunts.ui.footer_fit')
    cv.img.save(out)
    print('wrote', out)


# ------------------------------------------------------------------ Refit tab
TIPS = ['field_point', 'fixed_broadhead', 'mechanical_broadhead', 'cut_on_contact_broadhead', 'judo_point', 'flint_point', 'obsidian_point', 'bone_point',
        'tracer_broadhead', 'tracer_field_point', 'tracer_ice_broadhead']


def refit_view(out):
    bench = 'reloading_bench'
    B = BENCH[bench]
    cv = Canvas(os.path.join(ROOT, 'patch/assets/frontierhunts/textures/gui/field_school/welcome.png'))
    Lo = Layout()
    fy = frame(cv, Lo, bench)
    tabs = [t for t in TABS if t['bench'] == bench]
    counts = {t['enum']: '%d/%d' % (sum(e['ready'] for e in ENTRIES if e['tab'] == t['enum']), sum(1 for e in ENTRIES if e['tab'] == t['enum']))
              for t in tabs if t['kind'] == 'RECIPES'}
    counts['REFIT'] = '3'
    rail(cv, Lo, bench, tabs, 'REFIT', counts)
    x, y, w, h = Lo.gridX, Lo.bodyY, Lo.gridW, Lo.bodyH
    cv.rect(x, y, w, h, 6, INSET_EDGE)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 5, INSET)
    cv.text(tr('bench.frontierhunts.refit.arrows'), x + 9, y + 7, TEXT, 'STRONG')
    cv.text(tr('bench.frontierhunts.refit.arrows_hint'), x + 9 + width(tr('bench.frontierhunts.refit.arrows'), 'STRONG') + 7, y + 8.5, DIM, 'SMALL')
    cv.text(fit(tr('bench.frontierhunts.refit.explain'), w - 18, 'SMALL'), x + 9, y + 21, mix(MUTED, TEXT, 0.25), 'SMALL')  # [smalls]
    rows = [('frontierhunts:field_arrow', 'Hunting Arrow', 'Fixed-Blade Broadhead', 23, True), ('frontierhunts:field_arrow', 'Hunting Arrow', 'Field Point', 12, False),
            ('frontierhunts:primitive_arrow', 'Primitive Arrow', 'Knapped Flint Point', 8, False), ('minecraft:arrow', 'Arrow', tr('bench.frontierhunts.refit.plain'), 30, False)]
    ry = y + GRID_TOP + 11  # [smalls] EXPLAIN_H
    for i, (it, nm, head, n, sel) in enumerate(rows):
        bg = mix(TILE_SEL, B['accent'], 0.08) if sel else (TILE_HOT if i == 2 else TILE)
        cv.rect(x + 8, ry, w - 16, 30, 5, bg)
        if sel:
            cv.outline(x + 7.5, ry - 0.5, w - 15, 31, 5.5, B['accent'], 1.5)
        ic = icon(it, 'arrows')
        cv.image(ic, x + 14, ry + 7, 16)
        cv.text(nm, x + 38, ry + 6, TEXT, 'STRONG')
        cv.text(head, x + 38, ry + 17, mix(MUTED, TEXT, 0.2), 'SMALL')
        s = '×%d' % n
        cv.right(s, x + w - 16, ry + 11, TEXT, 'STRONG')
        ry += 34
    # detail card
    dx, dw = Lo.detX, DETAIL_W
    cv.rect(dx, y, dw, h, 6, PAPER_EDGE)
    cv.rect(dx + 1, y + 1, dw - 2, h - 2, 5, PAPER)
    cv.text(tr('bench.frontierhunts.refit.new_head'), dx + 10, y + 9, GOLD_DARK, 'SMALL')
    cv.right(tr('bench.frontierhunts.refit.owned'), dx + dw - 10, y + 9, INK_MUTED, 'SMALL')
    owned = {'field_point': 4, 'fixed_broadhead': 0, 'mechanical_broadhead': 12, 'judo_point': 2, 'tracer_broadhead': 1}
    cx0, cy0 = dx + 10, y + 21
    cells = TIPS + ['stock']
    for i, tp in enumerate(cells):
        cx = cx0 + (i % 6) * 30
        cy = cy0 + (i // 6) * 30
        sel = tp == 'mechanical_broadhead'
        cv.rect(cx, cy, 27, 27, 4, 0xFF2A241C if sel else 0xFFDCD1B8)
        if sel:
            cv.outline(cx - 0.5, cy - 0.5, 28, 28, 4.5, B['accent'], 1.5)
        if tp == 'stock':
            cv.text('↺', cx + 9, cy + 8, INK, 'STRONG')
        else:
            ic = icon('frontierhunts:' + tp, 'tips')
            cv.image(ic, cx + 5.5, cy + 5.5, 16)
            if 'tracer' in tp:
                cv.rect(cx + 3, cy + 23, 21, 2, 1, {'tracer_broadhead': 0xFFFF3A1C, 'tracer_field_point': 0xFF3CFF6A, 'tracer_ice_broadhead': 0xFF46C8FF}[tp])
            n = owned.get(tp, 0)
            if n:
                cv.right(str(n), cx + 26, cy + 17, TEXT if sel else INK, 'SMALL')
            if tp == 'fixed_broadhead':
                cv.circle(cx + 4, cy + 4, 2.2, GREEN_INK)
    sy = cy0 + 62
    cv.rect(dx + 10, sy, dw - 20, 1, 0, 0xFFD3C6A6)
    sy += 6
    ic1, ic2 = icon('frontierhunts:fixed_broadhead', 'tips'), icon('frontierhunts:mechanical_broadhead', 'tips')
    cv.image(ic1, dx + 10, sy, 16)
    cv.text(fit('Fixed-Blade', 58, 'SMALL'), dx + 29, sy + 4.5, INK, 'SMALL')
    cv.text('→', dx + 86, sy + 3.5, GOLD_DARK, 'STRONG')
    cv.image(ic2, dx + 98, sy, 16)
    cv.text(fit('Mechanical', 70, 'SMALL'), dx + 117, sy + 4.5, INK, 'STRONG')
    sy += 22
    for lab, now, new, mx, c in [('Penetration', 1.0, 0.85, 1.25, 0xFF5E7E9A), ('Blood trail', 1.0, 1.35, 1.4, 0xFFA2412F), ('Damage', 1.0, 1.1, 1.1, 0xFF8F6E2F),
                                 ('Recovery', 0.85, 0.75, 1.0, 0xFF6C7F57)]:
        cv.text(lab, dx + 10, sy, INK_MUTED, 'SMALL')
        bx, bw = dx + 64, dw - 64 - 40
        cv.rect(bx, sy + 1.5, bw, 5, 2.5, 0xFFD8CCB0)
        cv.rect(bx, sy + 1.5, bw * min(1, new / mx), 5, 2.5, c)
        t = bx + bw * min(1, now / mx)
        cv.rect(t - 0.75, sy - 0.5, 1.5, 9, 0.5, INK)
        pct = round((new / now - 1) * 100)
        d = '=' if pct == 0 else ('+%d%%' % pct if pct > 0 else '%d%%' % pct)
        cv.right(d, dx + dw - 10, sy, GREEN_INK if pct > 0 else (RED_INK if pct < 0 else INK_MUTED), 'SMALL')
        sy += 12
    sy += 2
    for s in wrap('Blades open on impact: the widest wound and the heaviest blood trail, but less penetration.', dw - 20, 'SMALL')[:3]:
        cv.text(s, dx + 10, sy, INK_MUTED, 'SMALL')
        sy += 9
    by = y + h - 10 - 22
    sy2 = by - 24 - 14
    check(cv, dx + 14, sy2 + 3.5, GREEN_INK, 0.8)
    cv.text(fit(tr('bench.frontierhunts.refit.status_make', 12, 11), dw - 34, 'SMALL'), dx + 21, sy2 + 0.5, GREEN_INK, 'SMALL')
    bw = (dw - 20 - 6) / 2
    button(cv, dx + 10, by - 24, bw, 18, tr('bench.frontierhunts.refit.one'), 'PAPER', B['accent'])
    button(cv, dx + 10 + bw + 6, by - 24, bw, 18, tr('bench.frontierhunts.refit.heads_off0'), 'PAPER', B['accent'], active=False)
    button(cv, dx + 10, by, dw - 20, 22, tr('bench.frontierhunts.refit.all', 23), 'PRIMARY', B['accent'], key='Enter')
    footer(cv, Lo, bench, fy, 'bench.frontierhunts.ui.footer_refit')
    cv.img.save(out)
    print('wrote', out)


if __name__ == '__main__':
    recipes_view('frontier_workbench', 'HUNTING', 'frontierhunts:wind_checker', os.path.join(OUT, 'frontier_workbench_hunting.png'), hot_item='frontierhunts:binoculars')
    recipes_view('frontier_workbench', 'CLOTHING', 'frontierhunts:ghillie_jacket', os.path.join(OUT, 'frontier_workbench_clothing.png'))
    recipes_view('gunsmith_bench', 'BOWS', 'frontierhunts:field_bow', os.path.join(OUT, 'gunsmith_bench_bows.png'), hot_item='frontierhunts:compound_bow')
    recipes_view('gunsmith_bench', 'ATTACHMENTS', 'frontierhunts:six_power_scope', os.path.join(OUT, 'gunsmith_bench_attachments.png'))
    recipes_view('reloading_bench', 'CARTRIDGES', 'frontierhunts:rifle_round', os.path.join(OUT, 'reloading_bench_cartridges.png'))
    recipes_view('reloading_bench', 'TIPS', 'frontierhunts:mechanical_broadhead', os.path.join(OUT, 'reloading_bench_search.png'), query='broad')
    fit_view(os.path.join(OUT, 'gunsmith_bench_fit.png'))
    refit_view(os.path.join(OUT, 'reloading_bench_refit.png'))
    # [smalls] the Arrows tab with arrows made with any head, and the Food tab with its hint
    for e in ENTRIES:
        if e['tab'] in ('ARROWS', 'FOOD'):
            e['ready'] = e.get('fitted', False) or e['item'] in ('frontierhunts:field_arrow', 'frontierhunts:pemmican')
    recipes_view('reloading_bench', 'ARROWS', 'frontierhunts:field_arrow#mechanical_broadhead', os.path.join(OUT, 'reloading_bench_arrows.png'))
    recipes_view('frontier_workbench', 'FOOD', 'frontierhunts:pemmican', os.path.join(OUT, 'frontier_workbench_food.png'))
