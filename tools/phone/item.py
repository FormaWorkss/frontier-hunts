#!/usr/bin/env python3
"""[phone] The Field Phone item: a rugged outdoor smartphone.

Writes (under <repo>/patch/assets/frontierhunts/):
  textures/item/phone/field_phone.png   64x64 atlas for the 3D model (front with the lock screen lit, rubber tread back,
                                        side rails, camera module, buttons)
  textures/item/field_phone.png         32x32 inventory icon
  models/item/field_phone_3d.json       the 3D model (body, corner bumpers, camera bump, buttons)
  models/item/field_phone.json          separate_transforms: flat icon in the GUI, 3D everywhere else
and a preview sheet: tools/phone/item_preview.png (several angles, software rendered).

python3 tools/phone/item.py <repo>
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')
rng = np.random.default_rng(7)

# ---------------------------------------------------------------------------------------------------- palette
OLIVE = (74, 86, 56)
OLIVE_D = (52, 61, 40)
OLIVE_L = (98, 111, 74)
RUBBER = (34, 37, 31)
RUBBER_L = (52, 56, 47)
BEZEL = (14, 15, 15)
GLASS = (10, 12, 14)
ORANGE = (232, 118, 38)
ORANGE_D = (176, 80, 22)
STEEL = (150, 154, 150)
STEEL_D = (96, 100, 98)
TAN = (196, 168, 120)

T = 4  # texels per model unit (64 px texture over 16 uv units)


def px(img, x, y, c, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*c, a)


def rect(img, x0, y0, x1, y1, c, a=255):
    img[y0:y1, x0:x1] = (*c, a)


def noisy(img, x0, y0, x1, y1, base, amp=6):
    h, w = y1 - y0, x1 - x0
    n = rng.normal(0, amp, (h, w, 1))
    col = np.clip(np.array(base, float)[None, None, :] + n, 0, 255)
    img[y0:y1, x0:x1, :3] = col.astype(np.uint8)
    img[y0:y1, x0:x1, 3] = 255


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# 3x5 digits for the lock-screen clock
DIG = {
    '6': ['111', '100', '111', '101', '111'],
    '4': ['101', '101', '111', '001', '001'],
    '2': ['111', '001', '111', '100', '111'],
    ':': ['0', '1', '0', '1', '0'],
}


def text(img, s, x, y, c):
    for ch in s:
        g = DIG[ch]
        for j, row in enumerate(g):
            for i, b in enumerate(row):
                if b == '1':
                    px(img, x + i, y + j, c)
        x += len(g[0]) + 1


def screen(img, x0, y0, w, h, clock=True):
    """The lit lock screen: a dusk sky over a ridge and a lake, the time, glare."""
    top, mid, low = (58, 74, 128), (196, 112, 92), (236, 160, 84)
    for j in range(h):
        t = j / max(1, h - 1)
        c = mix(top, mid, t / 0.55) if t < 0.55 else mix(mid, low, (t - 0.55) / 0.45)
        for i in range(w):
            px(img, x0 + i, y0 + j, c)
    # far ridge and near forest
    for i in range(w):
        r1 = int(h * 0.62 - 3 * math.sin(i * 0.55 + 1.0) - 2 * math.sin(i * 1.3))
        for j in range(r1, h):
            px(img, x0 + i, y0 + j, (96, 70, 96))
        r2 = int(h * 0.74 - (2 if (i * 7) % 3 == 0 else 0) - (i % 4 == 1))
        for j in range(r2, h):
            px(img, x0 + i, y0 + j, (44, 40, 52))
    # lake glint
    for j in range(int(h * 0.84), int(h * 0.9)):
        for i in range(w // 3, w // 3 + max(1, w // 5)):
            px(img, x0 + i, y0 + j, (210, 150, 110))
    if clock:
        text(img, '6:42', x0 + (w - 15) // 2, y0 + 6, (250, 248, 240))
        # date line and the dock
        for i in range(x0 + 5, x0 + w - 5):
            px(img, i, y0 + 13, (226, 218, 230))
        for k in range(4):
            cx = x0 + 3 + k * (w - 6) // 4
            for i in range(cx, cx + 3):
                for j in range(y0 + h - 5, y0 + h - 2):
                    px(img, i, j, [(92, 120, 70), (220, 200, 150), (150, 96, 60), (86, 150, 220)][k])
    # glare: a soft diagonal band
    for j in range(h):
        for i in range(w):
            d = abs((i - j * 0.55) - w * 0.15)
            if d < 2.2:
                c = img[y0 + j, x0 + i, :3].astype(float)
                img[y0 + j, x0 + i, :3] = np.clip(c + (1 - d / 2.2) * 34, 0, 255).astype(np.uint8)


def tread(img, x0, y0, x1, y1):
    """The back: olive rubber with a raised diamond tread."""
    noisy(img, x0, y0, x1, y1, OLIVE, 4)
    for y in range(y0, y1):
        for x in range(x0, x1):
            u, v = x - x0, y - y0
            if (u + v) % 4 == 0 or (u - v) % 4 == 0:
                c = img[y, x, :3].astype(int) - 12
                img[y, x, :3] = np.clip(c, 0, 255)
            elif (u + v) % 4 == 2 and (u - v) % 4 == 2:
                c = img[y, x, :3].astype(int) + 10
                img[y, x, :3] = np.clip(c, 0, 255)


# ---------------------------------------------------------------------------------------------------- atlas
atlas = np.zeros((64, 64, 4), np.uint8)
FW, FH = 24, 52  # body front/back: 6 x 13 units

# front (0,0)-(24,52): olive rim, black bezel, lit screen, speaker, camera dot, screws, chin logo
noisy(atlas, 0, 0, FW, FH, OLIVE_D, 3)
rect(atlas, 1, 1, FW - 1, FH - 1, BEZEL)
screen(atlas, 3, 6, FW - 6, FH - 13)
rect(atlas, 9, 3, 15, 4, (40, 42, 44))          # earpiece slot
px(atlas, 16, 3, (30, 44, 70))                    # front camera
for sx, sy in ((1, 1), (FW - 2, 1), (1, FH - 2), (FW - 2, FH - 2)):
    px(atlas, sx, sy, STEEL)
for i in range(9, 15):                           # chin: the FH mark in orange
    px(atlas, i, FH - 4, ORANGE_D if i in (9, 14) else ORANGE)
# the rim's lit top edge
for i in range(1, FW - 1):
    px(atlas, i, 0, OLIVE_L)

# back (24,0)-(48,52): tread, the tan logo plate with an antler mark, speaker holes, screws
tread(atlas, 24, 0, 48, FH)
rect(atlas, 24, 0, 48, 1, OLIVE_L)
rect(atlas, 24, FH - 1, 48, FH, OLIVE_D)
rect(atlas, 31, 24, 41, 31, TAN)
rect(atlas, 31, 30, 41, 31, (150, 124, 86))
for (x, y) in ((33, 26), (34, 27), (35, 28), (36, 28), (37, 27), (38, 26), (35, 26), (36, 25), (35, 29), (36, 29)):
    px(atlas, x, y, (70, 52, 34))
for k in range(5):
    px(atlas, 32 + k * 2, 44, RUBBER)
    px(atlas, 33 + k * 2, 46, RUBBER)
for sx, sy in ((26, 2), (45, 2), (26, 49), (45, 49)):
    px(atlas, sx, sy, STEEL)
    px(atlas, sx + 1, sy, STEEL_D)

# side rails (48,0)-(54,52): ridged black rubber
noisy(atlas, 48, 0, 54, FH, RUBBER, 3)
for y in range(0, FH, 2):
    rect(atlas, 48, y, 54, y + 1, RUBBER_L)
rect(atlas, 50, 0, 52, FH, (28, 30, 26))

# top / bottom ends (0,52)-(24,58) and (24,52)-(48,58): rubber with a port / mic
noisy(atlas, 0, 52, 48, 58, RUBBER, 3)
rect(atlas, 9, 54, 15, 56, (12, 12, 12))         # charge port (bottom)
rect(atlas, 10, 54, 14, 55, (60, 62, 60))
for k in range(3):
    px(atlas, 3 + k * 2, 55, (12, 12, 12))       # mic holes
px(atlas, 30, 55, (12, 12, 12))                   # top: headphone jack
px(atlas, 31, 55, (20, 20, 20))

# bumper corners (54,0)-(62,8): a darker grippy rubber with a highlight
noisy(atlas, 54, 0, 62, 8, (40, 46, 34), 4)
rect(atlas, 54, 0, 62, 1, (70, 80, 58))

# buttons: orange push-to-talk (54,8)-(58,12), grey volume (58,8)-(62,12)
noisy(atlas, 54, 8, 58, 12, ORANGE, 5)
rect(atlas, 54, 8, 58, 9, (250, 160, 90))
noisy(atlas, 58, 8, 62, 12, (70, 74, 72), 4)
rect(atlas, 58, 8, 62, 9, (110, 114, 110))

# camera module face (48,52)-(60,63): black glass, two lenses, flash, laser dot
rect(atlas, 48, 52, 60, 63, (16, 17, 18))
for (cx, cy, r) in ((51.5, 55.5, 2.2), (51.5, 60.0, 2.2)):
    for y in range(52, 63):
        for x in range(48, 60):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < r:
                atlas[y, x, :3] = (40, 48, 64) if d < r * 0.55 else (70, 72, 76)
                if d < r * 0.3:
                    atlas[y, x, :3] = (120, 150, 200)
rect(atlas, 55, 54, 58, 56, (240, 228, 180))     # flash
px(atlas, 56, 59, (200, 40, 40))                  # laser autofocus
for i in range(48, 60):
    px(atlas, i, 52, (60, 62, 64))
# module sides (60,52)-(64,63): dark metal
noisy(atlas, 60, 52, 64, 63, (46, 48, 50), 3)

# lanyard cord (54,12)-(58,20): orange paracord weave
for y in range(12, 20):
    for x in range(54, 58):
        atlas[y, x] = (*(ORANGE if (x + y) % 2 else ORANGE_D), 255)

tex_dir = os.path.join(A, 'textures', 'item', 'phone')
os.makedirs(tex_dir, exist_ok=True)
Image.fromarray(atlas, 'RGBA').save(os.path.join(tex_dir, 'field_phone.png'))


# ---------------------------------------------------------------------------------------------------- model
def uv(x0, y0, x1, y1):
    return [x0 / T, y0 / T, x1 / T, y1 / T]


TEX = '#phone'
FRONT = uv(0, 0, FW, FH)
BACK = uv(24, 0, 48, FH)
RAIL = uv(48, 0, 54, FH)
TOP = uv(24, 52, 48, 58)
BOTTOM = uv(0, 52, 24, 58)
BUMP = uv(54, 0, 62, 8)
ORANGE_B = uv(54, 8, 58, 12)
GREY_B = uv(58, 8, 62, 12)
CORD = uv(54, 12, 58, 20)
CAM = uv(48, 52, 60, 63)
CAM_SIDE = uv(60, 52, 64, 63)


def box(frm, to, faces, name=None, rotation=None):
    e = {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {}}
    if name:
        e['name'] = name
    for k, v in faces.items():
        e['faces'][k] = {'uv': [round(c, 4) for c in v], 'texture': TEX}
    if rotation:
        e['rotation'] = rotation
    return e


def all_faces(u, **over):
    d = {k: u for k in ('north', 'south', 'east', 'west', 'up', 'down')}
    d.update(over)
    return d


X0, X1 = 5.0, 11.0
Y0, Y1 = 1.5, 14.5
Z0, Z1 = 7.3, 8.7
els = [
    box([X0, Y0, Z0], [X1, Y1, Z1], {
        'south': FRONT, 'north': BACK, 'east': RAIL, 'west': RAIL, 'up': TOP, 'down': BOTTOM}, 'body'),
]
# corner bumpers: proud of the body on the sides and both faces, like a rugged case
B = 1.25
for (cx, cy, nm) in ((X0, Y0, 'bumper_bl'), (X1, Y0, 'bumper_br'), (X0, Y1, 'bumper_tl'), (X1, Y1, 'bumper_tr')):
    fx = cx - 0.2 if cx == X0 else cx - B + 0.0
    tx = cx + B if cx == X0 else cx + 0.2
    fy = cy - 0.2 if cy == Y0 else cy - B
    ty = cy + B if cy == Y0 else cy + 0.2
    els.append(box([fx, fy, Z0 - 0.15], [tx, ty, Z1 + 0.15], all_faces(BUMP), nm))
# camera module on the back, top left as you look at the back (the east side)
els.append(box([7.9, 11.1, Z0 - 0.35], [10.5, 13.6, Z0], {
    'north': CAM, 'east': CAM_SIDE, 'west': CAM_SIDE, 'up': CAM_SIDE, 'down': CAM_SIDE}, 'camera'))
# buttons: orange push-to-talk on the left edge, volume rocker and power on the right
els.append(box([X0 - 0.3, 9.6, 7.65], [X0, 11.6, 8.35], all_faces(ORANGE_B), 'ptt'))
els.append(box([X1, 10.2, 7.7], [X1 + 0.25, 12.4, 8.3], all_faces(GREY_B), 'volume'))
els.append(box([X1, 7.8, 7.7], [X1 + 0.25, 9.0, 8.3], all_faces(GREY_B), 'power'))
model3d = {
    'credit': 'Frontier Hunts [phone] tools/phone/item.py',
    'texture_size': [64, 64],
    'textures': {'phone': 'frontierhunts:item/phone/field_phone', 'particle': 'frontierhunts:item/phone/field_phone'},
    'elements': els,
    'display': {
        'thirdperson_righthand': {'rotation': [-20, 0, 0], 'translation': [0, 2.5, 1.5], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_lefthand': {'rotation': [-20, 0, 0], 'translation': [0, 2.5, 1.5], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [-8, 18, 0], 'translation': [-1.0, 3.6, -1.0], 'scale': [0.52, 0.52, 0.52]},
        'firstperson_lefthand': {'rotation': [-8, -18, 0], 'translation': [-1.0, 3.6, -1.0], 'scale': [0.52, 0.52, 0.52]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.45, 0.45, 0.45]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [0.8, 0.8, 0.8]},
        'gui': {'rotation': [15, -25, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
    },
}
mdir = os.path.join(A, 'models', 'item')
with open(os.path.join(mdir, 'field_phone_3d.json'), 'w') as f:
    json.dump(model3d, f, indent=1)
with open(os.path.join(mdir, 'field_phone.json'), 'w') as f:
    json.dump({
        'loader': 'neoforge:separate_transforms',
        'gui_light': 'front',
        'base': {'parent': 'frontierhunts:item/field_phone_3d'},
        'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'frontierhunts:item/field_phone'}}},
    }, f, indent=1)

# ---------------------------------------------------------------------------------------------------- 32x32 icon
icon = np.zeros((32, 32, 4), np.uint8)
x0, y0, x1, y1 = 9, 2, 23, 30   # 14 x 28 body
noisy(icon, x0, y0, x1, y1, OLIVE_D, 3)
rect(icon, x0 + 1, y0 + 1, x1 - 1, y1 - 1, BEZEL)
screen(icon, x0 + 2, y0 + 4, x1 - x0 - 4, y1 - y0 - 8, clock=False)
rect(icon, x0 + 4, y0 + 7, x1 - 4, y0 + 9, (250, 248, 240))
rect(icon, x0 + 5, y0 + 10, x1 - 5, y0 + 11, (214, 206, 222))
rect(icon, x0 + 5, y0 + 2, x1 - 5, y0 + 3, (46, 48, 50))
for i in range(x0 + 5, x1 - 5):
    px(icon, i, y1 - 2, ORANGE)
# corner bumpers (2x2, one px proud)
for (cx, cy) in ((x0 - 1, y0 - 1), (x1 - 2, y0 - 1), (x0 - 1, y1 - 2), (x1 - 2, y1 - 2)):
    rect(icon, cx, cy, cx + 3, cy + 3, (60, 70, 48))
    px(icon, cx + 1, cy + 1, (84, 96, 66))
# buttons
rect(icon, x0 - 1, y0 + 9, x0, y0 + 13, ORANGE)
rect(icon, x1, y0 + 8, x1 + 1, y0 + 13, (90, 94, 90))
# outline: darken the outer pixels of the silhouette for readability on any slot
alpha = icon[:, :, 3] > 0
edge = alpha & ~(np.roll(alpha, 1, 0) & np.roll(alpha, -1, 0) & np.roll(alpha, 1, 1) & np.roll(alpha, -1, 1))
icon[edge, :3] = (icon[edge, :3].astype(int) * 0.6).astype(np.uint8)
Image.fromarray(icon, 'RGBA').save(os.path.join(A, 'textures', 'item', 'field_phone.png'))


# ---------------------------------------------------------------------------------------------------- preview
FACES = {
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}


def render(model, yaw, pitch, size=420, span=18.0, ss=2):
    S = size * ss
    tex = atlas.astype(np.float32) / 255.0
    img = np.zeros((S, S, 3), np.float32) + np.array([0.20, 0.22, 0.20])
    zb = np.full((S, S), -1e9, np.float32)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    light = np.array([0.35, 0.75, 0.55])
    light /= np.linalg.norm(light)

    def proj(p):
        x, y, z = p[0] - 8, p[1] - 7.5, p[2] - 8
        x1 = x * cy_ + z * sy_
        z1 = -x * sy_ + z * cy_
        y2 = y * cp - z1 * sp
        z2 = y * sp + z1 * cp
        s = S / span
        return np.array([S / 2 + x1 * s, S / 2 - y2 * s, z2])

    for e in model['elements']:
        f, t = np.array(e['from'], float), np.array(e['to'], float)
        for fname, corners in FACES.items():
            fd = e['faces'].get(fname)
            if fd is None:
                continue
            pts = [np.array([f[i] if c[i] == 0 else t[i] for i in range(3)]) for c in corners]
            n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
            n /= max(1e-9, np.linalg.norm(n))
            shade = 0.5 + 0.5 * max(0.0, float(np.dot(n, light)))
            P = [proj(p) for p in pts]
            u = fd['uv']
            uvq = [(u[0], u[1]), (u[2], u[1]), (u[2], u[3]), (u[0], u[3])]
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = (P[i] for i in tri)
                ua, ub, uc = (uvq[i] for i in tri)
                minx, maxx = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(S - 1, math.ceil(max(a[0], b[0], c[0]))))
                miny, maxy = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(S - 1, math.ceil(max(a[1], b[1], c[1]))))
                if minx > maxx or miny > maxy:
                    continue
                xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
                d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(d) < 1e-9:
                    continue
                w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / d
                w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / d
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
                if not inside.any():
                    continue
                z = w0 * a[2] + w1 * b[2] + w2 * c[2]
                uu = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
                vv = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
                iy, ix = np.nonzero(inside)
                gy, gx = iy + miny, ix + minx
                zz = z[iy, ix]
                ok = zz > zb[gy, gx]
                gy, gx, zz = gy[ok], gx[ok], zz[ok]
                tx = np.clip((uu[iy, ix][ok] / 16.0 * 64).astype(int), 0, 63)
                ty = np.clip((vv[iy, ix][ok] / 16.0 * 64).astype(int), 0, 63)
                col = tex[ty, tx, :3]
                lit = shade if not (fname == 'south' and e.get('name') == 'body') else max(shade, 0.92)
                img[gy, gx] = col * lit
                zb[gy, gx] = zz
    im = Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8))
    return im.resize((size, size), Image.LANCZOS)


if __name__ == '__main__':
    views = [(0, 0), (-30, 15), (30, 10), (150, 15), (200, 5), (90, 0)]
    tiles = [render(model3d, y, p) for (y, p) in views]
    out = Image.new('RGB', (len(tiles) * 420 + 160, 420), (32, 34, 32))
    for i, t in enumerate(tiles):
        out.paste(t, (i * 420, 0))
    ic = Image.open(os.path.join(A, 'textures', 'item', 'field_phone.png')).resize((128, 128), Image.NEAREST)
    out.paste(ic, (len(tiles) * 420 + 16, 140), ic)
    p = os.path.join(R, 'tools', 'phone', 'item_preview.png')
    out.save(p)
    print('wrote', p)
