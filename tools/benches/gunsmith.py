"""[benchart] Gunsmith's Bench: dark walnut bench with a green felt mat, a padded gun cradle holding a bolt rifle, a
scope lying on the mat, parts trays and a driver block, cleaning rods in a stand, a brass gooseneck lamp, a recurve
bow hung on pegs across a raised-panel back wall, and a bank of small brass-pulled parts drawers."""
import numpy as np

import materials as m
from benchlib import Scene, Mat
from common import frame, back_wall, TOP_Y1, LEG_X, LEG_Z
from noise import hexrgb, smoothstep, vnoise

BID = 'gunsmith_bench'


def build():
    sc = Scene(BID)
    WAL = Mat(m.end_grain_dark(m.wood('walnut', ring=2.2, contrast=1.15)), edge=0.55, seam=0.4)
    TOPW = Mat(m.end_grain_dark(m.wood('walnut', ring=2.6, contrast=1.15,
                                       wear_top=m.scratches(23, (0, 2, 32, 15), n=40, dark=0.8, light=1.18))), edge=0.6, seam=0.5)
    PANEL = Mat(m.wood('walnut', ring=2.0, contrast=1.25, bias=0.06), edge=0.45, seam=0.5)
    STOCK = Mat(m.wood('walnut', ring=1.1, contrast=1.5, bias=0.18), edge=0.6, seam=0.3, gloss=0.15)
    FELT = Mat(m.felt('#2d5a3c'), edge=0.08, seam=0.2, ao=0.9)
    FELT_EDGE = Mat(m.felt('#1f3f2a'), edge=0.1, seam=0.2)
    LEATHER = Mat(m.leather('#5a1f17', var=0.1), edge=0.35, seam=0.3)
    BLACK = Mat(m.mottled('#1d1c1c', var=0.08, scale=2.0), edge=0.7, seam=0.2)
    BLUED, BRASS, STEEL, CHROME = m.BLUED, m.BRASS, m.STEEL, m.CHROME
    SCOPE = Mat(m.mottled('#23262b', var=0.08, scale=2.5, streak=0.2), edge=0.6, seam=0.2, gloss=0.3)
    GREENCASE = Mat(m.mottled('#2c3a2c', var=0.08, scale=1.6), edge=0.5, seam=0.3)
    LABEL = Mat(m.flat('#cbbf9f', 0.04), edge=0.1, seam=0.1)
    TRAY = Mat(m.mottled('#3f4447', var=0.08, scale=2), edge=0.4, seam=0.2)

    frame(sc, WAL, WAL, TOPW)
    Y = TOP_Y1
    # ------------------------------------------------------------------ under the top
    # parts-drawer bank on the viewer's right (x 3.75..15): 2 columns x 3 rows, brass cup pulls, card labels
    sc.box([3.75, 4.5, 3.25], [15.0, 12.0, 12.0], WAL, name='drawer bank')
    cols = [(4.0, 9.25), (9.5, 14.75)]
    rows = [(4.75, 7.0), (7.25, 9.5), (9.75, 11.75)]
    for (a, b) in cols:
        for (c0, c1) in rows:
            sc.box([a, c0, 2.75], [b, c1, 3.25], PANEL, grain=0, name='parts drawer')
            mid = (a + b) / 2
            sc.box([mid - 0.75, c0 + 0.45, 2.35], [mid + 0.75, c0 + 0.95, 2.75], BRASS, name='cup pull')
            sc.box([mid - 0.75, c0 + 1.25, 2.6], [mid + 0.75, c0 + 1.85, 2.75], LABEL, name='label card',
                   paint={'north': card_label})
    # long shallow tool drawer on the left half
    sc.box([15.0, 9.75, 3.25], [28.25, 12.0, 12.0], WAL, name='drawer carcass')
    sc.box([15.25, 10.0, 2.75], [28.0, 11.75, 3.25], PANEL, name='tool drawer')
    sc.box([20.5, 10.5, 2.25], [22.75, 11.0, 2.75], BRASS, name='bar pull')
    # hard rifle case + oil can on the shelf
    sc.box([16.0, 4.5, 5.0], [28.0, 6.5, 11.5], GREENCASE, name='rifle case')
    sc.box([16.25, 6.5, 5.25], [27.75, 6.75, 11.25], BLACK, name='case lid seal')
    for x in (18.5, 25.0):
        sc.box([x, 5.25, 4.75], [x + 1.0, 6.25, 5.0], CHROME, name='case latch')
    sc.box([21.0, 6.0, 4.6], [23.0, 6.5, 5.0], BLACK, name='case handle')
    sc.cyl('y', (12.5, 9.0), 1.0, 4.5, 7.0, Mat(m.flat('#b8892a', 0.06), edge=0.4), name='oil can',
           paint={'*': oil_can})
    sc.cyl('y', (12.5, 9.0), 0.3, 7.0, 8.25, BRASS, name='oil spout')

    # ------------------------------------------------------------------ felt mat
    sc.box([2.5, Y, 2.75], [24.75, Y + 0.25, 13.75], FELT_EDGE, name='mat border')
    sc.box([3.0, Y + 0.25, 3.25], [24.25, Y + 0.3, 13.25], FELT, name='mat', paint={'up': felt_mat})

    # ------------------------------------------------------------------ gun cradle + bolt rifle
    my = Y + 0.3
    cz0, cz1 = 7.75, 9.75
    sc.box([7.0, my, cz0], [22.0, my + 0.5, cz1], BLACK, name='cradle beam')
    for x0 in (7.0, 19.0):
        sc.box([x0, my + 0.5, cz0 + 0.5], [x0 + 1.5, my + 1.0, cz1 - 0.5], BLACK, name='cradle post')
        sc.box([x0 - 0.25, my + 1.0, cz0], [x0 + 1.75, my + 1.5, cz1], LEATHER, name='cradle pad')
        sc.box([x0 - 0.25, my + 1.5, cz0], [x0 + 1.75, my + 2.0, cz0 + 0.4], LEATHER, name='pad lip')
        sc.box([x0 - 0.25, my + 1.5, cz1 - 0.4], [x0 + 1.75, my + 2.0, cz1], LEATHER, name='pad lip')
    ry = my + 1.5  # rifle rests here
    z0, z1 = cz0 + 0.4, cz1 - 0.4
    zc = (z0 + z1) / 2
    sc.box([5.0, ry, z0], [5.5, ry + 3.0, z1], BLACK, name='recoil pad')
    sc.box([5.5, ry, z0], [9.5, ry + 3.0, z1], STOCK, grain=0, name='butt')
    sc.box([9.5, ry + 0.25, z0 + 0.1], [12.0, ry + 2.25, z1 - 0.1], STOCK, grain=0, name='wrist')
    sc.box([11.75, ry - 0.75, z0 + 0.15], [13.25, ry + 1.25, z1 - 0.15], STOCK, grain=1, name='grip')
    sc.box([12.0, ry, z0], [22.0, ry + 1.75, z1], STOCK, grain=0, name='forend')
    sc.box([22.0, ry + 0.2, z0 + 0.1], [22.75, ry + 1.6, z1 - 0.1], BLACK, name='forend tip')
    sc.box([13.0, ry + 1.75, z0 + 0.15], [17.25, ry + 2.75, z1 - 0.15], BLUED, grain=0, name='receiver')
    sc.box([13.25, ry - 0.25, zc - 0.3], [15.0, ry + 0.25, zc + 0.3], BLUED, name='trigger guard')
    sc.box([13.9, ry - 0.1, zc - 0.15], [14.3, ry + 0.4, zc + 0.15], BLUED, name='trigger')
    sc.box([12.5, ry + 2.1, zc - 0.3], [17.5, ry + 2.9, zc + 0.3], m.Mat(m.mottled('#7e858c', var=0.05, scale=3, streak=0.4), edge=0.35, gloss=0.3),
           grain=0, name='bolt body')
    sc.box([13.25, ry + 2.2, z0 - 1.0], [13.75, ry + 2.6, z0 + 0.15], BLUED, name='bolt handle')
    sc.box([13.0, ry + 1.95, z0 - 1.6], [14.0, ry + 2.85, z0 - 0.9], BLUED, name='bolt knob')
    sc.cyl('x', (ry + 2.2, zc), 0.5, 17.25, 20.5, BLUED, name='barrel shank')
    sc.cyl('x', (ry + 2.2, zc), 0.35, 20.5, 27.0, BLUED, name='barrel')
    sc.box([26.25, ry + 2.5, zc - 0.15], [26.75, ry + 2.95, zc + 0.15], BLUED, name='front sight')

    # ------------------------------------------------------------------ scope lying on the mat (front)
    sy = my
    sc.cyl('x', (sy + 0.55, 4.6), 0.5, 7.5, 14.5, SCOPE, name='scope tube', notch=0.6)
    sc.cyl('x', (sy + 0.85, 4.6), 0.85, 14.5, 17.0, SCOPE, name='objective bell', notch=0.65)
    sc.cyl('x', (sy + 0.7, 4.6), 0.7, 5.5, 7.5, SCOPE, name='eyepiece', notch=0.65)
    sc.box([10.25, sy + 1.05, 4.25], [11.25, sy + 1.6, 4.95], SCOPE, name='elevation turret')
    sc.box([10.4, sy + 0.4, 3.6], [11.1, sy + 0.95, 4.1], SCOPE, name='windage turret')
    sc.box([17.0, sy + 0.25, 4.0], [17.25, sy + 1.45, 5.2], Mat(lens, edge=0.0, seam=0.0), name='objective lens')
    sc.box([5.25, sy + 0.25, 4.1], [5.5, sy + 1.15, 5.1], Mat(lens, edge=0.0, seam=0.0), name='ocular lens')
    for x in (8.5, 13.0):
        sc.box([x, sy + 0.0, 3.95], [x + 0.75, sy + 1.15, 5.25], BLUED, name='scope ring')

    # ------------------------------------------------------------------ parts trays + driver block
    for i, (x0, x1) in enumerate(((19.0, 21.75), (22.25, 24.75))):
        tr = ('y', 22.5 if i == 0 else -22.5, ((x0 + x1) / 2, my, 4.75))
        sc.box([x0, my, 3.5], [x1, my + 0.25, 6.0], TRAY, rot=tr, name='tray floor', paint={'up': parts_tray(i)})
        sc.box([x0, my + 0.25, 3.5], [x1, my + 0.75, 3.75], TRAY, rot=tr, name='tray rim')
        sc.box([x0, my + 0.25, 5.75], [x1, my + 0.75, 6.0], TRAY, rot=tr, name='tray rim')
        sc.box([x0, my + 0.25, 3.75], [x0 + 0.25, my + 0.75, 5.75], TRAY, rot=tr, name='tray rim')
        sc.box([x1 - 0.25, my + 0.25, 3.75], [x1, my + 0.75, 5.75], TRAY, rot=tr, name='tray rim')
    # driver block on the bare top (left of the mat)
    sc.box([25.5, Y, 10.5], [29.0, Y + 1.25, 12.25], STOCK, grain=0, name='driver block')
    hc = ['#7a1d18', '#1d1c1c', '#24452e', '#7a1d18', '#1d1c1c']
    for i in range(5):
        x = 25.85 + i * 0.65
        sc.box([x, Y + 1.25, 11.1], [x + 0.45, Y + 2.75 + (i % 2) * 0.35, 11.6], Mat(m.flat(hc[i], 0.08), edge=0.6), name='driver')
    # cleaning-rod stand: weighted base, three rods with jags and T handles
    sc.box([28.25, Y, 12.25], [31.25, Y + 0.75, 14.25], STOCK, grain=0, name='rod stand')
    for i, (x, h, col) in enumerate(((28.9, 24.0, '#7a1d18'), (29.75, 23.25, '#1d1c1c'), (30.6, 24.5, '#1d1c1c'))):
        sc.box([x - 0.15, Y + 0.75, 13.05], [x + 0.15, h, 13.35], STEEL if i != 1 else BRASS, name='rod')
        sc.box([x - 0.6, h, 12.95], [x + 0.6, h + 0.45, 13.45], Mat(m.flat(col, 0.08), edge=0.5), name='rod handle')

    # ------------------------------------------------------------------ brass banker's lamp (back right)
    lx, lz = 4.25, 12.0
    GSHADE = Mat(m.glass('#2f7a4a'), edge=0.35, seam=0.2, gloss=0.5)
    sc.cyl('y', (lx, lz), 1.6, Y, Y + 0.5, BRASS, name='lamp base', notch=0.6)
    sc.cyl('y', (lx, lz), 1.0, Y + 0.5, Y + 0.9, BRASS, name='lamp base step', notch=0.6)
    sc.box([lx - 0.3, Y + 0.9, lz - 0.3], [lx + 0.3, Y + 4.25, lz + 0.3], BRASS, name='lamp stem')
    sc.box([lx - 0.5, Y + 3.0, lz - 0.5], [lx + 0.5, Y + 3.4, lz + 0.5], BRASS, name='stem collar')
    sc.box([lx - 1.75, Y + 4.25, lz - 0.25], [lx + 1.75, Y + 4.6, lz + 0.25], BRASS, name='shade yoke')
    sc.cyl('x', (Y + 5.1, lz - 0.2), 1.05, lx - 2.5, lx + 2.5, GSHADE, name='lamp shade', notch=0.62,
           paint={'down': lamp_mouth})
    sc.box([lx - 2.6, Y + 4.4, lz - 1.3], [lx + 2.6, Y + 4.6, lz + 0.9], BRASS, name='shade rim')
    sc.box([lx + 1.0, Y + 1.5, lz - 0.1], [lx + 1.15, Y + 4.3, lz + 0.05], BRASS, name='pull chain')
    sc.box([lx + 0.9, Y + 1.2, lz - 0.2], [lx + 1.25, Y + 1.5, lz + 0.15], BRASS, name='chain bob')

    # ------------------------------------------------------------------ back wall: raised walnut panels + recurve bow
    back_wall(sc, WAL, PANEL, y_top=25.0, batten_y=None)
    # rails framing the panel (gives the raised-panel look)
    sc.box([2.0, 15.0, 14.75], [30.0, 15.75, 15.0], WAL, name='panel rail low')
    sc.box([2.0, 24.25, 14.75], [30.0, 25.0, 15.0], WAL, name='panel rail high')
    sc.box([15.6, 15.75, 14.75], [16.4, 24.25, 15.0], WAL, name='panel stile')
    # bow pegs
    for x in (9.0, 23.0):
        sc.box([x - 0.3, 21.0, 13.25], [x + 0.3, 21.6, 14.75], BRASS, name='bow peg')
    BOW = Mat(m.wood('handle', ring=1.0, contrast=1.3, bias=0.1), edge=0.5, seam=0.2, gloss=0.2)
    GRIP = Mat(m.leather('#2a1a12', var=0.1), edge=0.2)
    bz0, bz1 = 13.6, 14.4
    by = 23.25
    sc.box([13.5, by - 0.75, bz0], [18.5, by + 0.25, bz1], BOW, grain=0, name='bow riser')
    sc.box([14.75, by - 0.95, bz0 - 0.1], [17.0, by + 0.35, bz1 + 0.1], GRIP, name='bow grip')
    sc.box([17.75, by + 0.25, 13.8], [18.0, by + 1.25, 14.2], BOW, name='arrow shelf')
    # limbs: two segments each side sloping down at 22.5 degrees, recurved tips at 0
    for side in (-1, 1):
        xa = 18.5 if side > 0 else 13.5
        y_cur = by - 0.25
        for k, (ln, ang) in enumerate(((5.0, 22.5), (4.5, 22.5), (2.0, -22.5))):
            x_end = xa + side * ln * 0.924
            y_end = y_cur - ln * 0.383 * (1 if ang > 0 else -1)
            cx, cy = (xa + x_end) / 2, (y_cur + y_end) / 2
            th = 0.6 - 0.1 * k
            sc.box([cx - ln / 2 - 0.1, cy - th / 2, bz0 + 0.1], [cx + ln / 2 + 0.1, cy + th / 2, bz1 - 0.1], BOW, grain=0,
                   rot=('z', -ang * side, (cx, cy, 14.0)), name='bow limb')
            xa, y_cur = x_end, y_end
        if side > 0:
            tip_r = (xa, y_cur)
        else:
            tip_l = (xa, y_cur)
    # bow string between the tips
    sy_ = min(tip_l[1], tip_r[1]) - 0.1
    sc.box([tip_l[0] + 0.2, sy_ - 0.1, 13.95], [tip_r[0] - 0.2, sy_ + 0.05, 14.05],
           Mat(m.flat('#8f8064', 0.05), edge=0.0, seam=0.0, ao=0.4), grain=0, name='bow string')
    return sc


# ---------------------------------------------------------------------------------------------- paints
def card_label(c):
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    col = np.tile(hexrgb('#cfc3a2'), (len(s), 1))
    # hand-written ink line
    ink = (np.abs(t - ft * 0.55) < 0.12) & (s > fs * 0.15) & (s < fs * 0.8)
    col[ink] = hexrgb('#3a3028')
    return col


def oil_can(c):
    col = m.mottled('#b8892a', var=0.08, scale=2)(c)
    if c['face'] in ('up', 'down'):
        return col * 0.9
    k = (c['P'][:, 1] - c['el'].frm[1]) / (c['el'].to[1] - c['el'].frm[1])
    band = (k > 0.3) & (k < 0.72)
    col[band] = hexrgb('#1f4a2b')
    col[band & (np.abs(k - 0.51) < 0.07)] = hexrgb('#e2d6b0')
    return col


def felt_mat(c):
    col = m.felt('#2d5a3c')(c)
    W = c['W']
    # wear patch under the cradle, faint gun-oil stain and lint
    d = np.hypot((W[:, 0] - 12.0) / 6.0, (W[:, 2] - 8.0) / 2.5)
    col *= (0.92 + 0.08 * np.clip(d, 0, 1))[:, None]
    st = np.hypot(W[:, 0] - 21.0, W[:, 2] - 11.0)
    col *= (1 - 0.18 * smoothstep(1.2, 0.6, st))[:, None]
    return col


def lens(c):
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    u = (s / max(fs, 1e-3) - 0.5) * 2
    v = (t / max(ft, 1e-3) - 0.5) * 2
    r = np.hypot(u, v)
    col = np.tile(hexrgb('#1a2433'), (len(s), 1)) * (0.8 + 0.4 * r[:, None])
    hl = np.exp(-(((u + 0.3) ** 2 + (v + 0.35) ** 2) / 0.06))
    col += hl[:, None] * np.array([0.55, 0.65, 0.7])
    col[r > 0.85] = hexrgb('#141414')
    return col.clip(0, 1)


def lamp_mouth(c):
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    u = (s / fs - 0.5) * 2
    v = (t / ft - 0.5) * 2
    r = np.hypot(u, v)
    col = np.tile(hexrgb('#f6e2a8'), (len(s), 1)) * (1.0 - 0.25 * r[:, None])
    col[r > 0.8] = hexrgb('#9a7a3a')
    return col


def parts_tray(i):
    rng = np.random.default_rng(40 + i)
    pts = [(rng.uniform(0.3, 2.3), rng.uniform(0.3, 2.2), rng.integers(0, 3)) for _ in range(9)]

    def fn(c):
        col = m.mottled('#3f4447', var=0.06, scale=2)(c) * 0.8
        s, t = c['s'], c['t']
        for (ps, pt, k) in pts:
            d = np.hypot(s - ps, t - pt)
            hit = d < 0.28
            col[hit] = hexrgb(['#c9a24a', '#d8dde2', '#2a2a2a'][k])
        return col
    return fn


if __name__ == '__main__':
    print(len(build().els))
