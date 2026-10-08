"""[artqa] Survival HUD sprites (textures/gui/survival/hud.png, 18 x 18 cells drawn at 9 GUI px): redraws the two that
read wrong at HUD size - protein (was a red ball, now a raw steak with a round bone and fat rim) and vigor (was a
yellow twig, now an antler with brow tine and three points) - in the sheet's existing pixel style (1 px dark outline,
light from the top-left). The other nine sprites are kept.

python3 tools/artqa/hud_icons.py <repo root>
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

OUT = (16, 16, 16)


def finish(col, mask, outline=OUT):
    """col: HxWx3 float, mask: HxW bool -> RGBA with bevel + 1 px outline (4-neighbour)."""
    h, w = mask.shape
    for _ in range(3):  # drop single-pixel spurs (they become outline spikes)
        n = np.zeros(mask.shape, int)
        n[1:] += mask[:-1]; n[:-1] += mask[1:]; n[:, 1:] += mask[:, :-1]; n[:, :-1] += mask[:, 1:]
        mask = mask & (n >= 2)
    a = np.zeros((h, w, 4), np.uint8)
    up = np.zeros_like(mask); up[1:] = ~mask[:-1]; up[0] = True
    lf = np.zeros_like(mask); lf[:, 1:] = ~mask[:, :-1]; lf[:, 0] = True
    dn = np.zeros_like(mask); dn[:-1] = ~mask[1:]; dn[-1] = True
    rt = np.zeros_like(mask); rt[:, :-1] = ~mask[:, 1:]; rt[:, -1] = True
    c = col.copy()
    hi = (up | lf) & ~(dn | rt) & mask
    lo = (dn | rt) & ~(up | lf) & mask
    c[hi] = c[hi] + (255 - c[hi]) * 0.25
    c[lo] = c[lo] * 0.78
    a[mask, :3] = c[mask].clip(0, 255)
    a[mask, 3] = 255
    edge = np.zeros_like(mask)
    edge[1:] |= mask[:-1]; edge[:-1] |= mask[1:]; edge[:, 1:] |= mask[:, :-1]; edge[:, :-1] |= mask[:, 1:]
    edge &= ~mask
    a[edge, :3] = outline
    a[edge, 3] = 255
    return a


def protein():
    S = 18
    m = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(m)
    d.polygon([(3, 6), (6, 3), (11, 2), (14, 4), (15, 8), (14, 12), (11, 14), (6, 15), (3, 13), (2, 9)], fill=255)
    mask = np.asarray(m) > 0
    col = np.zeros((S, S, 3), float)
    col[:] = (196, 62, 54)
    yy, xx = np.mgrid[0:S, 0:S]
    # fat rim on the lower-right edge
    fat = mask & (((xx - 8.5) * 0.7 + (yy - 8.5) * 0.7) > 4.2)
    col[fat] = (242, 226, 206)
    # marbling
    for (x, y) in ((5, 7), (6, 7), (9, 11), (10, 11), (11, 6), (7, 12)):
        if mask[y, x] and not fat[y, x]:
            col[y, x] = (226, 120, 108)
    # darker meat toward the top-left edge of the cut face
    dark = mask & ~fat & (((xx - 8.5) * 0.7 + (yy - 8.5) * 0.7) < -4.0)
    col[dark] = (150, 40, 36)
    # round bone with marrow
    for (x, y) in ((7, 8), (8, 8), (7, 9), (8, 9), (8, 7), (7, 7), (6, 8), (9, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
        col[y, x] = (244, 236, 214)
    for (x, y) in ((7, 8), (8, 9)):
        col[y, x] = (196, 150, 120)
    return finish(col, mask)


def vigor():
    S = 18
    m = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(m)
    # side view, burr at the bottom left, beam sweeping up and forward, brow tine + two points standing up off it
    beam = [(4, 15), (4, 11), (6, 9), (9, 8), (12, 7), (14, 5), (15, 2)]
    d.line(beam, fill=255, width=2)
    for t in ([(5, 10), (3, 6)], [(8, 8), (8, 3)], [(11, 7), (12, 3)]):
        d.line(t, fill=255, width=2)
    d.rectangle([3, 14, 6, 15], fill=255)  # burr
    mask = np.asarray(m) > 0
    col = np.zeros((S, S, 3), float)
    col[:] = (236, 214, 160)
    yy, xx = np.mgrid[0:S, 0:S]
    col[mask & (yy >= 11)] = (196, 160, 104)   # darker, rougher base
    col[mask & (yy <= 3)] = (250, 240, 214)     # polished tips
    return finish(col, mask, (44, 30, 14))


if __name__ == '__main__':
    root = sys.argv[1]
    p = os.path.join(root, 'patch/assets/frontierhunts/textures/gui/survival/hud.png')
    sheet = np.array(Image.open(p).convert('RGBA'))
    for idx, f in ((0, protein), (6, vigor)):
        sheet[0:18, idx * 18:idx * 18 + 18] = f()
    Image.fromarray(sheet, 'RGBA').save(p, optimize=True)
    print('wrote', p)
