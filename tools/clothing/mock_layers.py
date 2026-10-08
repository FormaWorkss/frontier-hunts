#!/usr/bin/env python3
"""[clothing] Offline mocks of the Layers screen (clothing/client/LayersScreen) and the two-tab Field Gear panel next to
the vanilla inventory (client/FieldGearInventoryTab). Mirrors the Java layout constants and colours with the real Inter
fonts from the jar; sample state: carbon hood + jacket + trousers under a ghillie jacket, buckskin leggings, fur hat,
pack and quiver. A layout and colour check, not the renderer.

python3 tools/clothing/mock_layers.py <repo> <unzipped jar dir> <vanilla assets dir> <out dir> [scale]
"""
import os, sys
ROOT, JAR, MC, OUT = sys.argv[1:5]
SC = int(sys.argv[5]) if len(sys.argv) > 5 else 3
import tempfile
# mock_firsthunt renders its own pages when imported: point it at a throwaway folder, we only borrow its fonts and canvas
sys.argv = ['mock_firsthunt.py', ROOT, JAR, tempfile.mkdtemp(prefix='fhmock'), str(SC)]
sys.path.insert(0, os.path.join(ROOT, 'tools/onboard'))
import mock_firsthunt as M
from PIL import Image, ImageDraw

tr, width, fit = M.tr, M.width, M.fit
W, H, BAND = 528, 312, 42
BAND_C, BAND_TEXT, BAND_MUTED, BAND_GOLD = 0xFF263A30, 0xFFF3ECDB, 0xFFA9B4A2, 0xFFD8BD88
PAPER, CARD, CARD_EDGE, INK, MUTED = 0xFFEAE3D0, 0xFFF3EEDF, 0xFFD3C8AC, 0xFF243A32, 0xFF657064
RULE, GOLD, SLOT, SLOT_EDGE = 0xFFC5C2AE, 0xFFB99859, 0xFFDCD3BC, 0xFFB9AE90
GOOD, WARN, BAD = 0xFF3E6E37, 0xFFB07A1E, 0xFFA5281F
COL_W, CARD_W = (W - 28 - 20) // 3, (W - 28 - 10) // 2
ITEMS = os.path.join(JAR, 'assets/frontierhunts/textures/item')
OUTFIT_ICONS = os.path.join(ROOT, 'patch/assets/frontierhunts/textures/item')


def icon_path(name):
    for base in (OUTFIT_ICONS, ITEMS, os.path.join(OUTFIT_ICONS, 'ghillie'), os.path.join(ITEMS, 'ghillie')):
        p = os.path.join(base, name + '.png')
        if os.path.exists(p):
            return p
    return os.path.join(MC, 'assets/minecraft/textures/item/bundle.png')


def t(k, *a):
    return tr('clothing.frontierhunts.layers.' + k, *a)


def row(cv, label, name, sub, icon, x, y, bar=-1.0, ghost=False):
    cv.rect(x + 2, y + 3, 22, 22, 3, SLOT_EDGE)
    cv.rect(x + 3, y + 4, 20, 20, 2.5, CARD if icon else SLOT)
    if icon:
        cv.icon(icon_path(icon), x + 5, y + 6, 16)
        if ghost:
            cv.rect(x + 5, y + 6, 16, 16, 0, 0x99DCD3BC)
    tx = x + 30
    cv.text(label.upper(), tx, y + 2, MUTED, 'SMALL')
    cv.text(fit(name, COL_W - 32, 'BODY'), tx, y + 11, MUTED if not icon or ghost else INK, 'BODY')
    if bar >= 0:
        cv.rect(tx, y + 24, 40, 3, 1.5, 0xFFD3CBB4)
        cv.rect(tx, y + 24, max(3, 40 * bar), 3, 1.5, GOOD if bar >= 0.7 else (WARN if bar >= 0.35 else BAD))
        cv.text(fit(sub, COL_W - 32 - 46, 'SMALL'), tx + 45, y + 22, MUTED, 'SMALL')
    elif sub:
        cv.text(fit(sub, COL_W - 32, 'SMALL'), tx, y + 22, MUTED, 'SMALL')


def card(cv, x, y, w, h, head, big, lines, accent, level, dots):
    cv.rect(x, y, w, h, 5, CARD_EDGE)
    cv.rect(x + 1, y + 1, w - 2, h - 2, 4.5, CARD)
    cv.rect(x + 1, y + 1, 4, h - 2, 2, accent)
    cv.text(head.upper(), x + 14, y + 9, MUTED, 'SMALL')
    cv.text(big, x + 14, y + 20, INK, 'TITLE')
    bx, by = x + 14 + max(44, width(big, 'TITLE') + 10), y + 26
    if dots:
        n = round(level * 8)
        for i in range(8):
            cv.circle(bx + 3 + i * 9, by, 3, accent if i < n else 0xFFD3CBB4)
    else:
        bw = w - (bx - x) - 16
        cv.rect(bx, by - 2, bw, 5, 2.5, 0xFFD3CBB4)
        cv.rect(bx, by - 2, max(5, bw * min(1, level)), 5, 2.5, accent)
    ly = y + 42
    for l in lines:
        cv.text(fit(l, w - 28, 'SMALL'), x + 14, ly, INK, 'SMALL')
        ly += 11


def layers_screen(path):
    cv = M.Canvas(None)
    x, y = (M.GW - W) // 2, (M.GH - H) // 2
    cv.rect(x - 2, y - 2, W + 4, H + 4, 7, 0xFF41352A)
    cv.rect(x - 1, y - 1, W + 2, H + 2, 6.5, 0xFF88704A)
    cv.rect(x, y, W, H, 6, PAPER)
    cv.rect(x, y, W, BAND, 6, BAND_C)
    cv.rect(x, y + BAND - 8, W, 8, 0, BAND_C)
    cv.grad(x, y + BAND // 2, x + W, y + BAND, 0x00000000, 0x30000000)
    cv.rect(x, y + BAND, W, 1, 0, GOLD)
    cv.rect(x, y + BAND + 1, W, 2, 0, 0x18000000)
    cv.text(t('eyebrow'), x + 14, y + 9, BAND_GOLD, 'SMALL')
    cv.text(t('title'), x + 14, y + 20, BAND_TEXT, 'TITLE')
    cv.right(fit(t('subtitle'), W - 220, 'SMALL'), x + W - 40, y + 24, BAND_MUTED, 'SMALL')
    cv.rect(x + W - 30, y + 11, 20, 20, 4, 0x20FFFFFF)
    cv.center('×', x + W - 20, y + 16, BAND_TEXT, 'STRONG')
    rows_top = y + BAND + 30
    cols = [
        (t('outer'), [
            (t('slot.head'), 'Fur Trapper Hat', t('warmth_sub', '1.0', '60%', '40%'), 'fur_hat', -1, False),
            (t('slot.torso'), 'Ghillie Jacket', t('warmth_sub', '0.5', '15%', '10%'), 'ghillie_jacket', -1, False),
            (t('slot.legs'), 'Buckskin Leggings', t('warmth_sub', '1.0', '50%', '40%'), 'buckskin_leggings', -1, False),
            (t('slot.feet'), t('nothing'), '', None, -1, False)]),
        (t('base'), [
            (t('slot.hood'), 'Carbon Hood', t('carbon_sub', '96%', '20%'), 'carbon_hood', 0.96, False),
            (t('slot.top'), 'Carbon Jacket', t('carbon_sub', '61%', '45%'), 'carbon_jacket', 0.61, False),
            (t('slot.trousers'), 'Carbon Trousers', t('carbon_sub', '30%', '35%'), 'carbon_trousers', 0.30, False)]),
        (t('packs'), [
            (t('slot.pack'), "Hunter's Field Pack", t('pack_sub'), 'hunter_pack', -1, False),
            (t('slot.quiver'), "Hunter's Quiver", t('quiver_sub'), None, -1, False)]),
    ]
    for c, (title, rows) in enumerate(cols):
        cx = x + 14 + c * (COL_W + 10)
        cv.text(title, cx, y + BAND + 12, INK, 'STRONG')
        cv.rect(cx, y + BAND + 24, COL_W, 1, 0, RULE)
        for i, r in enumerate(rows):
            row(cv, r[0], r[1], r[2], r[3], cx, rows_top + i * 32, r[4], r[5])
    cards_top = y + BAND + 30 + 4 * 32 + 6
    card_h = y + H - 18 - cards_top
    card(cv, x + 14, cards_top, CARD_W, card_h, t('warmth'), '3.85', [
        t('comfort', '−4°C', '27°C'), t('weather', '58%', '41%'), t('dry'), t('felt', '−2°C')], GOLD, (3.85 - 1) / 7, True)
    card(cv, x + 14 + CARD_W + 10, cards_top, CARD_W, card_h, t('scent'), '34%', [
        t('carbon_full', '100%', '58%'), t('motion.still') + ' · ' + t('sweat', '0%') + ' · ' + t('wet_short', '0%'),
        t('spray', '1:12'), t('downwind', '66%')], WARN, 0.34, False)
    cv.text(fit(t('tip.charge'), W - 28, 'SMALL'), x + 14, y + H - 13, MUTED, 'SMALL')
    cv.img.save(path)
    print('wrote', path)


def field_gear_panel(path, tab):
    """Vanilla inventory (176 x 166) at GUI scale SC with the Field Gear panel and its two tabs on the left."""
    inv = Image.open(os.path.join(MC, 'assets/minecraft/textures/gui/container/inventory.png')).convert('RGBA').crop((0, 0, 176, 166))
    gw, gh = 300, 200
    img = Image.new('RGBA', (gw * SC, gh * SC), (40, 44, 48, 255))
    gl, gt = 100, 17
    img.alpha_composite(inv.resize((176 * SC, 166 * SC), Image.NEAREST), (gl * SC, gt * SC))
    d = ImageDraw.Draw(img)

    def fill(x0, y0, x1, y1, c):
        d.rectangle([x0 * SC, y0 * SC, x1 * SC - 1, y1 * SC - 1], fill=M.col(c))

    def item(name, x, y, dim=0):
        p = icon_path(name)
        im = Image.open(p).convert('RGBA').resize((16 * SC, 16 * SC), Image.NEAREST)
        img.alpha_composite(im, (x * SC, y * SC))
        if dim:
            fill(x, y, x + 16, y + 16, dim)

    def bevel(x, y, w, h):
        fill(x + 1, y, x + w - 1, y + h, 0xFF000000)
        fill(x, y + 1, x + w, y + h - 1, 0xFF000000)
        fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6)
        fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF)
        fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF)
        fill(x + 3, y + h - 3, x + w - 1, y + h - 1, 0xFF555555)
        fill(x + w - 3, y + 3, x + w - 1, y + h - 1, 0xFF555555)

    def slot(x, y):
        fill(x, y, x + 18, y + 18, 0xFF8B8B8B)
        fill(x, y, x + 17, y + 1, 0xFF373737)
        fill(x, y, x + 1, y + 17, 0xFF373737)
        fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF)
        fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF)

    PW, PH = 26, (64, 106)[tab]
    px, py = gl - PW - 2, gt + 6
    tx = px - 22 + 2
    for i in range(2):
        ty = py + 3 + i * 24
        sel = i == tab
        face = 0xFFC6C6C6 if sel else 0xFF9C9C9C
        fill(tx + 1, ty, tx + 22, ty + 22, 0xFF000000)
        fill(tx, ty + 1, tx + 22, ty + 21, 0xFF000000)
        fill(tx + 1, ty + 1, tx + 22, ty + 21, face)
        fill(tx + 1, ty + 1, tx + 22, ty + 3, 0xFFFFFFFF if sel else 0xFFC9C9C9)
        fill(tx + 1, ty + 1, tx + 3, ty + 20, 0xFFFFFFFF if sel else 0xFFC9C9C9)
        fill(tx + 3, ty + 19, tx + 22, ty + 21, 0xFF555555 if sel else 0xFF6E6E6E)
    bevel(px, py, PW, PH)
    ty = py + 3 + tab * 24
    fill(px, ty + 1, px + 3, ty + 21, 0xFFC6C6C6)
    item('carbon_jacket', tx + 3, py + 3 + 24 + 3)
    # pack tab icon: the pack is 3D in game; the mock stands in with a bundle
    item('hunter_pack', tx + 3, py + 3 + 3)
    if tab == 0:
        for i, n in enumerate(('hunter_pack', None)):
            slot(px + 4, py + 5 + i * 20)
        item('hunter_pack', px + 5, py + 6)
    else:
        for i, n in enumerate(('carbon_hood', 'carbon_jacket', 'carbon_trousers')):
            slot(px + 4, py + 5 + i * 20)
            item(n, px + 5, py + 6 + i * 20, 0 if i < 2 else 0xB08B8B8B)
        y = py + 66
        for k in range(3):
            fill(px + 6 + k, y + 1 + k * 3, px + 20 - k * 2, y + 2 + k * 3, 0xFF6F6F6F)
        s = '34%'
        d.text(((px + (PW - 15) / 2) * SC, (y + 9) * SC), s, fill=M.col(0xFF8A5C0E), font=M.F['STRONG'])
        fill(px + 4, y + 21, px + 22, y + 23, 0xFF8B8B8B)
        fill(px + 4, y + 21, px + 4 + 6, y + 23, 0xFF8A5C0E)
    lx, ly = px + 4, py + PH - 17
    fill(lx, ly, lx + 18, ly + 13, 0xFF000000)
    fill(lx + 1, ly + 1, lx + 17, ly + 12, 0xFF8B8B8B)
    fill(lx + 1, ly + 1, lx + 17, ly + 2, 0xFFB5B5B5)
    fill(lx + 1, ly + 11, lx + 17, ly + 12, 0xFF555555)
    for k, c in enumerate((0xFF3A4140, 0xFF5F6B3E, 0xFF8C6A45)):
        fill(lx + 4 + k, ly + 3 + k * 3, lx + 14 - k, ly + 5 + k * 3, c)
    img.save(path)
    print('wrote', path)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    layers_screen(os.path.join(OUT, 'layers_screen_640x360.png'))
    field_gear_panel(os.path.join(OUT, 'field_gear_panel_gear.png'), 0)
    field_gear_panel(os.path.join(OUT, 'field_gear_panel_base.png'), 1)
