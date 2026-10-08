"""[artqa] Before/after contact sheets for docs/ws/artqa/, simulating how the game samples each texture:
nearest (items, HUD, crisp journal icons), plain bilinear without mips (vanilla SimpleTexture + setFilter(true,false):
the old GUI art path) and trilinear over an alpha-weighted mip chain (GuiArtTexture, the new path).

python3 tools/artqa/sheets.py <before extracted jar> <after extracted jar> <out dir> <fonts dir>
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import items as IT

B, A, OUT, FONTS = sys.argv[1:5]
os.makedirs(OUT, exist_ok=True)
GUI = 'assets/frontierhunts/textures/gui/'
try:
    FONT = ImageFont.truetype(os.path.join(FONTS, 'inter_semibold.ttf'), 13)
    FONT_B = ImageFont.truetype(os.path.join(FONTS, 'inter_semibold.ttf'), 17)
except OSError:
    FONT = FONT_B = ImageFont.load_default()
PAPER = (234, 227, 208)
LEATHER = (73, 55, 43)
CARD = (28, 24, 20)


def arr(root, rel):
    return np.asarray(Image.open(os.path.join(root, rel)).convert('RGBA')).astype(np.float32) / 255.0


def bilinear(T, u0, v0, u1, v1, w, h):
    """GL_LINEAR sampling of straight-alpha texture T (H x W x 4) over texel rect (u0,v0)-(u1,v1) into w x h px."""
    th, tw = T.shape[:2]
    xs = u0 + (np.arange(w) + 0.5) / w * (u1 - u0) - 0.5
    ys = v0 + (np.arange(h) + 0.5) / h * (v1 - v0) - 0.5
    x0 = np.floor(xs).astype(int); y0 = np.floor(ys).astype(int)
    fx = (xs - x0)[None, :, None]; fy = (ys - y0)[:, None, None]
    X0 = x0.clip(0, tw - 1); X1 = (x0 + 1).clip(0, tw - 1); Y0 = y0.clip(0, th - 1); Y1 = (y0 + 1).clip(0, th - 1)
    a = T[Y0][:, X0]; b = T[Y0][:, X1]; c = T[Y1][:, X0]; d = T[Y1][:, X1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def nearest(T, u0, v0, u1, v1, w, h):
    th, tw = T.shape[:2]
    xs = (u0 + (np.arange(w) + 0.5) / w * (u1 - u0)).astype(int).clip(0, tw - 1)
    ys = (v0 + (np.arange(h) + 0.5) / h * (v1 - v0)).astype(int).clip(0, th - 1)
    return T[ys][:, xs]


def mipchain(T, levels=5):
    ch = [T]
    while len(ch) < levels and ch[-1].shape[0] % 2 == 0 and ch[-1].shape[1] % 2 == 0 and min(ch[-1].shape[:2]) // 2 >= 8:
        P = ch[-1]
        al = P[..., 3:4]
        s = lambda X: X[0::2, 0::2] + X[1::2, 0::2] + X[0::2, 1::2] + X[1::2, 1::2]
        sa = s(al)
        rgb = np.where(sa > 0, s(P[..., :3] * al) / np.maximum(sa, 1e-6), s(P[..., :3]) / 4)
        ch.append(np.concatenate([rgb, sa / 4], -1))
    return ch


LOD_BIAS = float(os.environ.get('ARTQA_LOD_BIAS', '-0.5'))  # GuiArtTexture sets GL_TEXTURE_LOD_BIAS = -0.5


def trilinear(ch, u0, v0, u1, v1, w, h):
    rho = max((u1 - u0) / w, (v1 - v0) / h)
    lod = max(0.0, math.log2(max(rho, 1e-6)) + LOD_BIAS)
    l0 = min(int(lod), len(ch) - 1); l1 = min(l0 + 1, len(ch) - 1); f = min(1.0, lod - int(lod)) if l1 != l0 else 0.0
    def at(l):
        k = 2 ** l
        return bilinear(ch[l], u0 / k, v0 / k, u1 / k, v1 / k, w, h)
    return at(l0) * (1 - f) + at(l1) * f


def over(S, bg):
    out = np.empty(S.shape[:2] + (3,), np.float32)
    a = S[..., 3:4]
    out[:] = S[..., :3] * a + np.array(bg, np.float32) / 255 * (1 - a)
    return Image.fromarray((out.clip(0, 1) * 255).round().astype(np.uint8), 'RGB')


def bleed(T):
    from bleed import bleed_array
    return bleed_array((T * 255).round().astype(np.uint8)).astype(np.float32) / 255


def label(img, text, big=False, color=(30, 26, 22)):
    d = ImageDraw.Draw(img)
    d.text((6, 4), text, fill=color, font=FONT_B if big else FONT)


def stack(rows, gap=8, bg=(40, 36, 32)):
    W = max(r.width for r in rows)
    H = sum(r.height for r in rows) + gap * (len(rows) - 1)
    S = Image.new('RGB', (W, H), bg)
    y = 0
    for r in rows:
        S.paste(r, (0, y))
        y += r.height + gap
    return S


def side(a, b, title_a='BEFORE', title_b='AFTER', bg=(40, 36, 32), gap=10):
    W = a.width + b.width + gap
    H = max(a.height, b.height) + 26
    S = Image.new('RGB', (W, H), bg)
    S.paste(a, (0, 26)); S.paste(b, (a.width + gap, 26))
    d = ImageDraw.Draw(S)
    d.text((6, 4), title_a, fill=(230, 170, 150), font=FONT_B)
    d.text((a.width + gap + 6, 4), title_b, fill=(170, 230, 160), font=FONT_B)
    return S


# ---------------------------------------------------------------------------------------------------- plates
def plate_pair(rel, w, fn):
    rows = []
    for root in (B, A):
        T = arr(root, rel)
        h = round(w * T.shape[0] / T.shape[1])
        rows.append(over(trilinear(mipchain(T), 0, 0, T.shape[1], T.shape[0], w, h), PAPER))
    side(*rows).save(os.path.join(OUT, fn), quality=90)


for n in ('clean', 'dressing', 'range', 'tracking'):
    plate_pair(GUI + 'academy/plate_%s.png' % n, 620, 'academy_%s.jpg' % n)
for n in ('harvest', 'tracks', 'glass'):
    plate_pair(GUI + 'field_school/%s.png' % n, 720, 'fieldschool_%s.jpg' % n)


# ---------------------------------------------------------------------------------------------------- sampling: plates small
def small_plate(rel, w, fn, bg=PAPER):
    T = arr(B, rel)
    h = round(w * T.shape[0] / T.shape[1])
    old = over(bilinear(T, 0, 0, T.shape[1], T.shape[0], w, h), bg)
    Ta = arr(A, rel)
    new = over(trilinear(mipchain(Ta), 0, 0, Ta.shape[1], Ta.shape[0], w, h), bg)
    s = side(old.resize((w * 2, h * 2), Image.NEAREST), new.resize((w * 2, h * 2), Image.NEAREST),
             'BEFORE: bilinear, no mips (%d px, 2x)' % w, 'AFTER: mipmapped')
    s.save(os.path.join(OUT, fn), quality=92)


small_plate(GUI + 'settings/preset_ultra.png', 232, 'sampling_preset_card.jpg')
small_plate(GUI + 'field_school/vitals.png', 300, 'sampling_fieldschool_small.jpg')


# ---------------------------------------------------------------------------------------------------- icons
def icon_row(T, cells, cell, sizes, mode, bg, atlas_x2=None):
    tiles = []
    for (cx, cy) in cells:
        for px in sizes:
            if mode == 'bilinear':
                S = bilinear(T, cx * cell, cy * cell, cx * cell + cell, cy * cell + cell, px, px)
            elif mode == 'nearest':
                S = nearest(T, cx * cell, cy * cell, cx * cell + cell, cy * cell + cell, px, px)
            elif mode == 'x2':
                c2 = cell * 2
                S = bilinear(atlas_x2, cx * c2, cy * c2, cx * c2 + c2, cy * c2 + c2, px, px)
            else:
                S = trilinear(T, cx * cell, cy * cell, cx * cell + cell, cy * cell + cell, px, px)
            tiles.append(over(S, bg))
    W = sum(t.width + 6 for t in tiles) + 6
    H = max(t.height for t in tiles) + 12
    R = Image.new('RGB', (W, H), bg)
    x = 6
    for t in tiles:
        R.paste(t, (x, 6)); x += t.width + 6
    return R


# journal icons at 16 GUI px, scale 3 (48 px): old = bilinear from 32 px art, new = bilinear from the 2x copy
JB = arr(B, GUI + 'journal/icons.png'); JA = arr(A, GUI + 'journal/icons.png'); JX = arr(A, GUI + 'journal/icons_x2.png')
cells = [(i % 16, i // 16) for i in (0, 3, 4, 10, 12, 19, 25, 36, 44, 47, 50, 57, 58, 66)]
old = icon_row(JB, cells, 32, [48], 'bilinear', LEATHER)
new = icon_row(JA, cells, 32, [48], 'x2', LEATHER, JX)
n2 = icon_row(JA, cells, 32, [32], 'nearest', LEATHER)
n4 = icon_row(JA, cells, 32, [64], 'nearest', LEATHER)
rows = [old, new, n2, n4]
cap = ['before: GUI scale 3, 16 px icon = 48 px, bilinear from 32 px art (soft)', 'after: GUI scale 3, sharp bilinear from the 2x atlas',
       'GUI scale 2 (nearest, unchanged)', 'GUI scale 4 (nearest, unchanged)']
out = []
for r, c in zip(rows, cap):
    img = Image.new('RGB', (r.width, r.height + 22), LEATHER); img.paste(r, (0, 22)); label(img, c, color=(240, 226, 200)); out.append(img)
stack(out).save(os.path.join(OUT, 'journal_icons_scale3.png'))

# dossier icons at 9 / 12 / 16 GUI px, scale 2: old bilinear no mips (aliased + black fringe), new bled + trilinear
DB = arr(B, GUI + 'academy/icons.png'); DA = mipchain(arr(A, GUI + 'academy/icons.png'))
cells = [(i % 8, i // 8) for i in range(24)]
rows = []
for bg, grp in ((PAPER, cells[:12]), ((232, 214, 170), cells[12:]), (CARD, cells[:12]), (CARD, cells[12:])):
    o = icon_row(DB, grp, 64, [18, 24, 32], 'bilinear', bg)
    nn = icon_row(DA, grp, 64, [18, 24, 32], 'tri', bg)
    rows.append(stack([o.resize((o.width * 2, o.height * 2), Image.NEAREST), nn.resize((nn.width * 2, nn.height * 2), Image.NEAREST)], gap=2))
stack(rows, gap=14).save(os.path.join(OUT, 'dossier_icons_small.png'))

# field school icons at 18-20 GUI px on the dark card, scale 2 and 1
FB = arr(B, GUI + 'field_school/icons.png'); FA = mipchain(arr(A, GUI + 'field_school/icons.png'))
cells = [(i % 4, i // 4) for i in range(16)]
o = icon_row(FB, cells, 64, [20, 40], 'bilinear', CARD)
nn = icon_row(FA, cells, 64, [20, 40], 'tri', CARD)
side(o.resize((o.width * 2, o.height * 2), Image.NEAREST), nn.resize((nn.width * 2, nn.height * 2), Image.NEAREST),
     'BEFORE (toast/card icons, GUI scale 1 and 2, 2x)', 'AFTER').save(os.path.join(OUT, 'fieldschool_icons.png'))

# survival HUD: 9 GUI px from 18 px cells, nearest, scales 2 / 3 / 4
HB = arr(B, GUI + 'survival/hud.png'); HA = arr(A, GUI + 'survival/hud.png')
rows = []
for root_T, name in ((HB, 'BEFORE'), (HA, 'AFTER')):
    r = icon_row(root_T, [(i, 0) for i in range(11)], 18, [18, 27, 36], 'nearest', (96, 120, 70))
    img = Image.new('RGB', (r.width, r.height + 22), (96, 120, 70)); img.paste(r, (0, 22)); label(img, name + ' - HUD sprites at GUI scale 2 / 3 / 4', color=(250, 250, 240))
    rows.append(img.resize((img.width * 2, img.height * 2), Image.NEAREST))
stack(rows).save(os.path.join(OUT, 'hud.png'))

# ---------------------------------------------------------------------------------------------------- items
CHANGED = ['moose_skull', 'aged_venison', 'paraglider', 'forest_litter', 'undergrowth']


def item_tiles(root, names):
    IT.setroot(root)
    IT._TEX.clear(); IT._SPR = None
    tiles = []
    for n in names:
        row = []
        for sc in (2, 3, 4):
            ic, _ = IT.render(n, 16 * sc)
            row.append(IT.slot_tile(ic, sc))
        tiles.append(row)
    return tiles


def items_sheet(names, fn):
    tb, ta = item_tiles(B, names), item_tiles(A, names)
    rowh = 18 * 4 + 30
    W = 2 * (18 * 9 + 40) + 220
    S = Image.new('RGB', (W, rowh * len(names) + 30), (0xC6, 0xC6, 0xC6))
    d = ImageDraw.Draw(S)
    d.text((220, 6), 'BEFORE', fill=(120, 30, 20), font=FONT_B)
    d.text((220 + 18 * 9 + 40, 6), 'AFTER', fill=(20, 90, 20), font=FONT_B)
    for i, n in enumerate(names):
        y = 30 + i * rowh
        d.text((8, y + 30), n, fill=(20, 20, 20), font=FONT_B)
        d.text((8, y + 52), 'GUI scale 2 / 3 / 4', fill=(70, 70, 70), font=FONT)
        for k, tiles in enumerate((tb[i], ta[i])):
            x = 220 + k * (18 * 9 + 40)
            for t in tiles:
                S.paste(t, (x, y)); x += t.width + 6
    S.save(os.path.join(OUT, fn))


items_sheet(CHANGED, 'items_changed.png')

# quiver slot icon (Curios), 16 px at scale 2/3/4 on a slot
for root, tag in ((B, 'before'), (A, 'after')):
    pass
QB = arr(B, 'assets/frontierhunts/textures/slot/quiver_slot.png'); QA = arr(A, 'assets/frontierhunts/textures/slot/quiver_slot.png')
o = icon_row(QB, [(0, 0)], 16, [32, 48, 64], 'nearest', (139, 139, 139))
nn = icon_row(QA, [(0, 0)], 16, [32, 48, 64], 'nearest', (139, 139, 139))
side(o, nn).save(os.path.join(OUT, 'quiver_slot.png'))


# all item models as they appear in the inventory (after), for the review record
def all_items(root, fn_prefix, per=60, cols=10):
    IT.setroot(root); IT._TEX.clear(); IT._SPR = None
    names = sorted(os.path.basename(p)[:-5] for p in os.listdir(os.path.join(root, 'assets/frontierhunts/models/item')) if p.endswith('.json'))
    k = 0
    for i in range(0, len(names), per):
        chunk = names[i:i + per]
        cw, ch = 18 * 3 + 100, 18 * 3 + 22
        rows = (len(chunk) + cols - 1) // cols
        S = Image.new('RGB', (cols * cw, rows * ch), (0xC6, 0xC6, 0xC6))
        d = ImageDraw.Draw(S)
        for j, n in enumerate(chunk):
            ic, kind = IT.render(n, 48)
            x, y = (j % cols) * cw + 4, (j // cols) * ch + 2
            S.paste(IT.slot_tile(ic, 3), (x, y))
            d.text((x + 58, y + 4), n[:16], fill=(20, 20, 20), font=FONT)
            d.text((x + 58, y + 20), n[16:32], fill=(20, 20, 20), font=FONT)
            d.text((x + 58, y + 36), kind.split(' ')[0] if kind else '', fill=(90, 60, 30), font=FONT)
        S.save(os.path.join(OUT, '%s_%d.png' % (fn_prefix, k)))
        k += 1


all_items(A, 'inventory_all')
print('sheets written to', OUT)
