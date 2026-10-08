"""[benchart] Reloading Bench: heavy honey-pine bench with a red cast-iron O-frame single-stage press (chrome ram,
sizing die, ball-knob lever), a powder measure with a clear hopper, a beam powder scale, loading blocks full of brass,
a blue shotshell press with shot and powder bottles, an arrow fletching jig with bright vanes, loose arrows, a red
flare canister, back-wall shelves of colour-coded ammo boxes and powder cans, and OD ammo cans + a crate of shells
underneath."""
import numpy as np

import materials as m
from benchlib import Scene, Mat
from common import frame, back_wall, TOP_Y1
from noise import hexrgb, smoothstep, vnoise

BID = 'reloading_bench'


def build():
    sc = Scene(BID)
    PINE = Mat(m.end_grain_dark(m.wood('pine', ring=2.4, contrast=1.1)), edge=0.3, seam=0.4)
    TOPP = Mat(m.end_grain_dark(m.wood('pine', ring=2.8, contrast=1.1,
                                       wear_top=m.scratches(37, (0, 2, 32, 15), n=50, dark=0.78,
                                                            stains=[(11.0, 9.0, 1.1, 0.35), (19.5, 11.5, 0.8, 0.3)]))),
               edge=0.3, seam=0.5)
    BOARD = Mat(m.wood('pine', ring=2.2, tone=0.1, bias=-0.06), edge=0.25, seam=0.5)
    RED = Mat(m.gradient_v(m.mottled('#8c1d16', var=0.07, scale=1.6)), edge=0.25, seam=0.25, chip=(0.16, 0.15, 0.15), gloss=0.2)
    BLUE = Mat(m.gradient_v(m.mottled('#27486e', var=0.07, scale=1.6)), edge=0.25, seam=0.25, chip=(0.16, 0.16, 0.17), gloss=0.2)
    BLACK = Mat(m.mottled('#1b1a1a', var=0.08, scale=2.0), edge=0.6, seam=0.2)
    CHROME, BRASS, STEEL = m.CHROME, m.BRASS, m.STEEL
    CASE = Mat(case_brass, edge=0.3, seam=0.1, gloss=0.3)
    COPPER = Mat(m.mottled('#b5652e', var=0.08, scale=3), edge=0.35, gloss=0.2)
    OD = Mat(m.mottled('#4b5235', var=0.07, scale=1.5), edge=0.45, seam=0.3, chip=(0.3, 0.29, 0.26))

    frame(sc, PINE, PINE, TOPP)
    Y = TOP_Y1
    # front apron with a brass-pulled drawer (keeps the family look)
    sc.box([3.75, 9.75, 3.25], [28.25, 12.0, 12.0], PINE, name='apron carcass')
    sc.box([4.0, 10.0, 2.75], [15.75, 11.75, 3.25], BOARD, grain=0, name='drawer')
    sc.box([16.25, 10.0, 2.75], [28.0, 11.75, 3.25], BOARD, grain=0, name='drawer')
    for mid in (9.875, 22.125):
        sc.box([mid - 1.0, 10.5, 2.25], [mid + 1.0, 11.0, 2.75], BRASS, name='bar pull')

    # ------------------------------------------------------------------ single-stage press (front right)
    sc.box([3.25, Y, 2.75], [7.75, Y + 0.75, 7.25], RED, name='press base')
    for bx, bz in ((3.6, 3.1), (7.0, 3.1), (3.6, 6.4), (7.0, 6.4)):
        sc.box([bx, Y + 0.75, bz], [bx + 0.5, Y + 1.0, bz + 0.5], CHROME, name='bolt')
    sc.box([4.0, Y + 0.75, 3.5], [7.0, Y + 2.5, 6.75], RED, name='press body')
    sc.box([4.0, Y + 2.5, 3.5], [4.75, Y + 6.0, 6.75], RED, name='frame side')
    sc.box([6.25, Y + 2.5, 3.5], [7.0, Y + 6.0, 6.75], RED, name='frame side')
    sc.box([4.75, Y + 2.5, 5.75], [6.25, Y + 6.0, 6.75], RED, name='frame back')
    sc.box([3.75, Y + 6.0, 3.25], [7.25, Y + 7.5, 7.0], RED, name='frame head')
    sc.box([5.1, Y + 1.5, 4.25], [5.9, Y + 4.25, 5.05], CHROME, grain=1, name='ram')
    sc.box([5.0, Y + 4.25, 4.15], [6.0, Y + 4.6, 5.15], BLACK, name='shell holder')
    sc.box([5.2, Y + 4.6, 4.35], [5.8, Y + 5.6, 4.95], CASE, name='case in press')
    sc.cyl('y', (5.5, 4.75), 0.62, Y + 5.6, Y + 6.0, BLACK, name='die mouth')
    sc.cyl('y', (5.5, 4.75), 0.62, Y + 7.5, Y + 9.75, BLACK, name='die', notch=0.6)
    sc.cyl('y', (5.5, 4.75), 0.9, Y + 7.5, Y + 8.1, Mat(m.mottled('#a0a6ab', var=0.06, scale=3), edge=0.4, gloss=0.4), name='lock ring', notch=0.6)
    sc.box([5.25, Y + 9.75, 4.5], [5.75, Y + 10.25, 5.0], CHROME, name='decap rod')
    # linkage + lever (swung forward and up), black ball knob
    sc.box([7.0, Y + 1.75, 4.0], [7.75, Y + 2.75, 6.25], RED, name='linkage')
    sc.box([7.25, Y + 2.0, 4.85], [7.75, Y + 8.25, 5.35], CHROME, grain=1, rot=('x', -45, (7.5, Y + 2.25, 5.1)), name='lever')
    kz = 5.1 - 6.0 * 0.7071
    ky = Y + 2.25 + 6.0 * 0.7071
    sc.box([7.0, ky - 0.5, max(0.0, kz - 0.5)], [8.0, ky + 0.5, kz + 0.5], BLACK, name='ball knob')

    # ------------------------------------------------------------------ powder measure on its stand
    sc.box([8.75, Y, 4.25], [12.0, Y + 0.5, 7.25], RED, name='stand foot')
    sc.box([10.0, Y + 0.5, 6.0], [10.75, Y + 4.75, 6.75], RED, grain=1, name='stand post')
    sc.box([9.75, Y + 4.5, 5.25], [11.0, Y + 5.0, 6.6], RED, name='stand arm')
    sc.cyl('x', (Y + 5.75, 5.6), 0.8, 9.6, 11.4, CHROME, name='measure drum')
    sc.box([10.15, Y + 2.75, 5.35], [10.85, Y + 5.0, 5.85], CHROME, grain=1, name='drop tube')
    sc.box([11.4, Y + 5.5, 4.0], [11.9, Y + 6.0, 5.75], BLACK, name='measure handle')
    sc.box([11.35, Y + 5.4, 3.6], [12.0, Y + 6.1, 4.25], BLACK, name='measure knob')
    sc.cyl('y', (10.5, 5.6), 0.95, Y + 6.5, Y + 10.0, Mat(m.glass('#c8d6d2', fill_level=0.5, fill_col='#2b2a28'), edge=0.15, seam=0.1, ao=0.6),
           name='hopper', notch=0.62)
    sc.cyl('y', (10.5, 5.6), 0.7, Y + 6.25, Y + 6.5, CHROME, name='hopper collar')
    sc.cyl('y', (10.5, 5.6), 1.05, Y + 10.0, Y + 10.5, BLACK, name='hopper cap', notch=0.62)
    sc.box([10.2, Y + 2.25, 5.3], [10.8, Y + 2.75, 5.9], CASE, name='case under tube')

    # ------------------------------------------------------------------ beam scale
    SCB = Mat(m.mottled('#22332a', var=0.07, scale=2.0), edge=0.5, seam=0.2, gloss=0.2)
    ALU = Mat(m.mottled('#c3c6c4', var=0.05, scale=3, streak=0.3), edge=0.25)
    sc.box([13.0, Y, 3.25], [17.75, Y + 0.75, 6.0], SCB, name='scale base', paint={'north': scale_front})
    sc.box([16.5, Y + 0.75, 4.25], [17.25, Y + 2.25, 5.0], SCB, name='scale pillar')
    sc.box([13.25, Y + 2.25, 4.45], [17.75, Y + 2.6, 4.8], ALU, grain=0, name='beam', paint={'north': beam_marks})
    sc.box([14.6, Y + 2.1, 4.35], [15.0, Y + 2.75, 4.9], ALU, name='poise')
    sc.box([15.6, Y + 2.15, 4.35], [15.9, Y + 2.7, 4.9], ALU, name='poise')
    sc.box([13.4, Y + 1.15, 4.5], [13.65, Y + 2.25, 4.75], ALU, name='pan hanger')
    sc.box([12.5, Y + 0.85, 3.75], [14.5, Y + 1.25, 5.5], BRASS, name='pan')
    sc.box([17.5, Y + 0.75, 4.35], [17.85, Y + 2.0, 4.9], ALU, name='pointer')

    # ------------------------------------------------------------------ loading blocks with cases / loaded rounds
    LB = Mat(m.wood('ash', ring=1.4, tone=0.04, bias=-0.15), edge=0.35, seam=0.3)
    LB2 = Mat(m.mottled('#2f5d3a', var=0.06, scale=2.0), edge=0.45, seam=0.2)
    sc.box([12.75, Y, 7.25], [18.25, Y + 1.0, 10.25], LB, grain=0, name='loading block')
    for r, zc in enumerate((7.85, 9.15)):
        for i in range(6):
            xc = 13.25 + i * 0.85
            if r == 1 and i == 5:
                continue
            sc.box([xc - 0.3, Y + 1.0, zc - 0.3], [xc + 0.3, Y + 2.4, zc + 0.3], CASE, name='case')
            if r == 0:
                sc.box([xc - 0.2, Y + 2.4, zc - 0.2], [xc + 0.2, Y + 2.95, zc + 0.2], COPPER, name='bullet')
    sc.box([19.25, Y, 3.0], [23.25, Y + 0.75, 5.75], LB2, grain=0, name='loading block 2')
    for r, zc in enumerate((3.65, 5.05)):
        for i in range(4):
            xc = 19.85 + i * 0.95
            sc.box([xc - 0.3, Y + 0.75, zc - 0.3], [xc + 0.3, Y + 2.15 + (0.4 if r == 0 else 0), zc + 0.3], CASE, name='case')
    # loose loaded rounds on the top
    for i, (x, z) in enumerate(((19.4, 7.0), (20.6, 7.6))):
        lr = ('y', 22.5 if i == 0 else -22.5, (x + 1.0, Y, z + 0.25))
        sc.box([x, Y, z], [x + 2.0, Y + 0.5, z + 0.5], CASE, grain=0, rot=lr, name='loose round')
        sc.box([x - 0.5, Y + 0.05, z + 0.05], [x, Y + 0.45, z + 0.45], COPPER, rot=lr, name='loose tip')

    # ------------------------------------------------------------------ shotshell press (left end, blue)
    sc.box([24.75, Y, 9.0], [30.5, Y + 0.75, 13.75], BLUE, name='shot press base')
    for x in (25.5, 29.25):
        sc.box([x, Y + 0.75, 11.75], [x + 0.6, Y + 6.75, 12.35], CHROME, grain=1, name='shot column')
    sc.box([25.0, Y + 3.5, 10.0], [30.25, Y + 4.25, 12.75], BLUE, name='tool head')
    for i, x in enumerate((25.75, 27.05, 28.35)):
        sc.box([x, Y + 0.75, 10.1], [x + 0.9, Y + 1.25, 11.0], BRASS, name='hull base')
        sc.box([x + 0.05, Y + 1.25, 10.15], [x + 0.85, Y + 3.5, 10.95], Mat(m.mottled('#a3221b', var=0.06, scale=2), edge=0.3),
               name='hull')
    sc.box([24.75, Y + 6.75, 11.5], [30.5, Y + 7.5, 12.6], BLUE, name='crossbar')
    sc.cyl('y', (26.55, 12.05), 0.95, Y + 7.5, Y + 10.5,
           Mat(m.glass('#c9d2d0', fill_level=0.68, fill_col='#6b6e70'), edge=0.15, ao=0.6), name='shot bottle', notch=0.62)
    sc.cyl('y', (26.55, 12.05), 1.0, Y + 10.5, Y + 10.9, BLACK, name='shot cap', notch=0.62)
    sc.cyl('y', (28.85, 12.05), 0.8, Y + 7.5, Y + 9.75,
           Mat(m.glass('#c9d2d0', fill_level=0.55, fill_col='#262524'), edge=0.15, ao=0.6), name='powder bottle', notch=0.62)
    sc.cyl('y', (28.85, 12.05), 0.85, Y + 9.75, Y + 10.1, BLACK, name='powder cap', notch=0.62)
    sc.box([30.5, Y + 5.0, 11.0], [31.25, Y + 5.75, 12.25], BLUE, name='shot lever pivot')
    sc.box([30.6, Y + 5.2, 11.4], [31.1, Y + 10.0, 11.9], CHROME, grain=1, rot=('x', -45, (30.85, Y + 5.4, 11.65)), name='shot lever')
    sc.box([30.35, Y + 5.4 + 4.6 * 0.7071 - 0.5, 11.65 - 4.6 * 0.7071 - 0.5], [31.35, Y + 5.4 + 4.6 * 0.7071 + 0.5, 11.65 - 4.6 * 0.7071 + 0.5],
           BLACK, name='shot lever knob')

    # ------------------------------------------------------------------ fletching jig (front left) + arrows
    FJ = Mat(m.mottled('#262729', var=0.08, scale=2), edge=0.6)
    fx, fz = 26.0, 4.5
    sc.cyl('y', (fx, fz), 1.6, Y, Y + 0.5, FJ, name='jig base', notch=0.6)
    sc.box([fx - 0.4, Y + 0.5, fz + 0.4], [fx + 0.4, Y + 5.0, fz + 1.2], FJ, grain=1, name='jig post')
    sc.box([fx - 0.6, Y + 1.75, fz - 0.3], [fx + 0.6, Y + 4.75, fz + 0.4], CHROME, grain=1, name='jig clamp')
    SHAFT = Mat(m.wood('pine', ring=1.0, tone=0.03, bias=0.1), edge=0.2)
    sc.box([fx - 0.18, Y + 0.5, fz - 0.95], [fx + 0.18, Y + 9.0, fz - 0.59], SHAFT, grain=1, name='arrow shaft')
    sc.box([fx - 0.25, Y + 9.0, fz - 1.02], [fx + 0.25, Y + 9.75, fz - 0.52], STEEL, name='field point')
    for col, (dx0, dx1, dz0, dz1) in ((('#e8c12a'), (-0.07, 0.07, -2.2, -0.95)), (('#c4302a'), (0.18, 1.3, -0.84, -0.7)),
                                       (('#efe9dc'), (-1.3, -0.18, -0.84, -0.7))):
        sc.box([fx + dx0, Y + 1.0, fz + dz0], [fx + dx1, Y + 3.25, fz + dz1], Mat(vane(col), edge=0.1, seam=0.0, ao=0.5), name='vane')
    # loose finished arrows lying along the back of the top (point to the right/-x)
    for i, (zc, cols) in enumerate(((12.4, ('#e8c12a', '#c4302a')), (13.3, ('#c4302a', '#efe9dc')))):
        sc.box([9.0, Y, zc - 0.18], [21.0, Y + 0.36, zc + 0.18], SHAFT, grain=0, name='arrow')
        sc.box([7.75, Y, zc - 0.35], [9.0, Y + 0.4, zc + 0.35], STEEL, name='broadhead', paint={'up': broadhead})
        sc.box([18.5, Y + 0.36, zc - 0.05], [20.5, Y + 1.0, zc + 0.05], Mat(vane(cols[0]), edge=0.1), name='vane')
        sc.box([18.5, Y + 0.1, zc - 0.55], [20.5, Y + 0.25, zc - 0.18], Mat(vane(cols[1]), edge=0.1), name='vane')
        sc.box([21.0, Y, zc - 0.22], [21.4, Y + 0.44, zc + 0.22], Mat(m.flat('#e1b524'), edge=0.2), name='nock')
    # flare canister (bright red, white band, black cap)
    sc.cyl('y', (23.25, 12.4), 0.85, Y, Y + 3.5, Mat(flare_can, edge=0.3, gloss=0.3), name='flare canister', notch=0.62)
    sc.cyl('y', (23.25, 12.4), 0.9, Y + 3.5, Y + 4.0, BLACK, name='flare cap', notch=0.62)

    # ------------------------------------------------------------------ back wall with two shelves of ammo
    back_wall(sc, PINE, BOARD, y_top=25.0)
    for sy in (18.75, 22.25):
        sc.box([2.0, sy, 12.75], [30.0, sy + 0.5, 15.0], PINE, grain=0, name='shelf')
        for bx in (8.0, 24.0):
            sc.box([bx, sy - 1.0, 14.25], [bx + 0.5, sy, 15.0], PINE, name='shelf bracket')
    # lower shelf (y 19.25): cartridge boxes & powder cans
    s1 = 19.25
    boxes1 = [(2.25, 4.75, 2.0, '#355a3a', '#e9e2cc'), (4.85, 7.35, 2.0, '#8c2a20', '#ece4cf'), (7.45, 9.5, 1.6, '#b98d2a', '#2a2522'),
              (13.75, 16.25, 2.25, '#2b4563', '#e9e2cc'), (16.35, 18.6, 1.75, '#355a3a', '#e9e2cc'), (18.7, 20.9, 1.75, '#355a3a', '#e9e2cc')]
    for x0, x1, h, c0, c1 in boxes1:
        sc.box([x0, s1, 13.0], [x1, s1 + h, 14.9], Mat(ammo_box(c0, c1), edge=0.3, seam=0.35), grain=0, name='ammo box')
    sc.cyl('y', (11.0, 13.95), 0.95, s1, s1 + 2.5, Mat(powder_can('#1d1d1d', '#b02a20'), edge=0.35, gloss=0.2), name='powder can', notch=0.62)
    sc.cyl('y', (11.0, 13.95), 0.45, s1 + 2.5, s1 + 2.85, BLACK, name='powder can cap')
    sc.cyl('y', (23.0, 13.95), 0.95, s1, s1 + 2.25, Mat(powder_can('#2a3b55', '#e2d8bd'), edge=0.35, gloss=0.2), name='powder can', notch=0.62)
    sc.cyl('y', (23.0, 13.95), 0.45, s1 + 2.25, s1 + 2.6, BLACK, name='powder can cap')
    sc.box([25.0, s1, 13.25], [29.75, s1 + 0.5, 14.75], Mat(m.mottled('#7d7f7f', var=0.06, scale=3), edge=0.3), grain=0, name='primer trays')
    sc.box([25.25, s1 + 0.5, 13.35], [29.5, s1 + 1.0, 14.65], Mat(m.mottled('#c9b06a', var=0.06, scale=3), edge=0.3), grain=0, name='primer trays')
    sc.box([25.0, s1 + 1.0, 13.25], [29.75, s1 + 1.5, 14.75], Mat(m.mottled('#7d7f7f', var=0.06, scale=3), edge=0.3), grain=0, name='primer trays')
    # upper shelf (y 22.75): shotshell boxes, dart tin, arrowhead tin
    s2 = 22.75
    boxes2 = [(2.25, 6.25, 2.0, '#9c2a20', '#f0e6c8'), (6.4, 10.4, 2.0, '#b98d2a', '#2a2522'), (19.0, 22.0, 1.5, '#8a6a44', '#ece4cf'),
              (22.2, 25.2, 1.5, '#2b4563', '#ece4cf')]
    for x0, x1, h, c0, c1 in boxes2:
        sc.box([x0, s2, 13.0], [x1, s2 + h, 14.9], Mat(ammo_box(c0, c1), edge=0.3, seam=0.35), grain=0, name='shell box')
    sc.box([11.0, s2, 13.25], [14.5, s2 + 1.0, 14.75], Mat(m.mottled('#8a8f92', var=0.08, scale=2), edge=0.4, gloss=0.3), name='tin')
    sc.box([15.0, s2, 13.25], [18.25, s2 + 0.75, 14.75], Mat(m.mottled('#6e5a3a', var=0.08, scale=2), edge=0.4), name='tin')
    sc.cyl('y', (27.75, 13.95), 0.9, s2, s2 + 2.0, Mat(powder_can('#3a4a2a', '#e2d8bd'), edge=0.35), name='powder can', notch=0.62)

    # ------------------------------------------------------------------ under the bench: OD ammo cans + shell crate
    sy = 4.5
    for x0 in (4.5, 10.25):
        sc.box([x0, sy, 6.0], [x0 + 5.25, sy + 3.75, 9.0], OD, grain=0, name='ammo can', paint={'north': ammo_can_side})
        sc.box([x0 - 0.15, sy + 3.75, 5.85], [x0 + 5.4, sy + 4.25, 9.15], OD, grain=0, name='ammo can lid')
        sc.box([x0 + 1.5, sy + 4.25, 7.25], [x0 + 3.75, sy + 4.5, 7.75], BLACK, name='can handle')
        sc.box([x0 + 5.25, sy + 2.25, 6.75], [x0 + 5.6, sy + 4.0, 8.25], OD, name='can latch')
    CR = Mat(m.wood('pine', ring=1.6, tone=0.1, bias=-0.18), edge=0.3, seam=0.5)
    cx0, cx1, cz0, cz1 = 17.5, 26.5, 5.5, 11.5
    sc.box([cx0, sy, cz0], [cx1, sy + 2.5, cz0 + 0.5], CR, name='crate')
    sc.box([cx0, sy, cz1 - 0.5], [cx1, sy + 2.5, cz1], CR, name='crate')
    sc.box([cx0, sy, cz0 + 0.5], [cx0 + 0.5, sy + 2.5, cz1 - 0.5], CR, name='crate')
    sc.box([cx1 - 0.5, sy, cz0 + 0.5], [cx1, sy + 2.5, cz1 - 0.5], CR, name='crate')
    sc.box([cx0 + 0.5, sy, cz0 + 0.5], [cx1 - 0.5, sy + 0.5, cz1 - 0.5], CR, name='crate floor')
    for i, (xa, xb, c0, c1) in enumerate(((18.0, 20.75, '#9c2a20', '#f0e6c8'), (20.85, 23.5, '#b98d2a', '#2a2522'),
                                          (23.6, 26.0, '#355a3a', '#e9e2cc'))):
        sc.box([xa, sy + 0.5, cz0 + 0.6], [xb, sy + 2.75 - 0.25 * i, cz1 - 0.6], Mat(shell_box_top(c0, c1), edge=0.3, seam=0.35),
               grain=2, name='shell box')
    return sc


# ---------------------------------------------------------------------------------------------- paints
def case_brass(c):
    col = m.brass(c)
    if c['face'] == 'up':
        s, t = c['s'] / c['fs'], c['t'] / c['ft']
        r = np.hypot(s - 0.5, t - 0.5)
        col[r < 0.3] = hexrgb('#2a1e10')
    return col * 1.05


def vane(col):
    b = hexrgb(col)

    def fn(c):
        s, t = c['s'], c['t']
        n = vnoise(c['P'][:, 0] * 6, c['P'][:, 1] * 6, c['P'][:, 2] * 6, seed=c['el'].seed % 97)
        barbs = 0.9 + 0.1 * np.sin((s + t) * 9.0)
        return (b[None, :] * (barbs * (0.92 + 0.12 * n))[:, None]).clip(0, 1)
    return fn


def broadhead(c):
    col = m.mottled('#9aa1a7', var=0.06, scale=3)(c)
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    # tip toward -x: the up face u runs along +x, so the point is at s=0
    w = s / fs
    edge = np.abs(t / ft - 0.5) > 0.5 * w + 0.05
    col[edge] *= 0.45
    return col


def flare_can(c):
    P, e = c['P'], c['el']
    col = m.mottled('#d0261c', var=0.06, scale=2)(c)
    if c['face'] in ('up', 'down'):
        return col
    k = (P[:, 1] - e.frm[1]) / (e.to[1] - e.frm[1])
    band = (k > 0.55) & (k < 0.72)
    col[band] = hexrgb('#f1eee6')
    col[band & (np.abs(k - 0.635) < 0.035)] = hexrgb('#1c1c1c')
    stripe = (k > 0.15) & (k < 0.2)
    col[stripe] = hexrgb('#f6c21b')
    return col * 1.08


def ammo_box(c0, c1):
    b0, b1 = hexrgb(c0), hexrgb(c1)

    def fn(c):
        P, e = c['P'], c['el']
        n = vnoise(P[:, 0] * 3, P[:, 1] * 3, P[:, 2] * 3, seed=e.seed % 97)
        col = b0[None, :] * (0.94 + 0.1 * n)[:, None]
        if c['face'] in ('north', 'south'):
            s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
            lab = (t > ft * 0.28) & (t < ft * 0.72) & (s > 0.2) & (s < fs - 0.2)
            col[lab] = b1
            txt = lab & (np.abs(t - ft * 0.5) < 0.1) & (s > fs * 0.22) & (s < fs * 0.78) & (((s * 3.1) % 1) < 0.7)
            col[txt] = b0 * 0.6
            logo = lab & (s < fs * 0.2 + 0.2) & (s > 0.3)
            col[logo] = b0 * 0.75
        elif c['face'] == 'up':
            s, fs = c['s'], c['fs']
            col[np.abs(s - fs * 0.5) < 0.12] *= 0.8  # flap seam
        return col
    return fn


def powder_can(c0, c1):
    b0, b1 = hexrgb(c0), hexrgb(c1)

    def fn(c):
        P, e = c['P'], c['el']
        n = vnoise(P[:, 0] * 3, P[:, 1] * 3, P[:, 2] * 3, seed=e.seed % 97)
        col = b0[None, :] * (0.92 + 0.12 * n)[:, None]
        if c['face'] not in ('up', 'down'):
            k = (P[:, 1] - e.frm[1]) / (e.to[1] - e.frm[1])
            lab = (k > 0.3) & (k < 0.7)
            col[lab] = b1
            col[lab & (np.abs(k - 0.5) < 0.06)] = b0 * 0.9 + 0.05
        return col
    return fn


def ammo_can_side(c):
    col = m.mottled('#4b5235', var=0.07, scale=1.5)(c)
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    ink = np.array([0.78, 0.72, 0.42])
    l1 = (np.abs(t - ft * 0.4) < 0.13) & (s > fs * 0.22) & (s < fs * 0.78) & (((s * 1.8) % 1) < 0.55)
    l2 = (np.abs(t - ft * 0.58) < 0.1) & (s > fs * 0.32) & (s < fs * 0.68) & (((s * 2.2) % 1) < 0.5)
    col[l1] = col[l1] * 0.35 + ink * 0.65
    col[l2] = col[l2] * 0.45 + ink * 0.55
    return col


def shell_box_top(c0, c1):
    """shotshell box: coloured carton, end label + brand band on the lid"""
    base = ammo_box(c0, c1)
    b1 = hexrgb(c1)

    def fn(c):
        col = base(c)
        if c['face'] == 'up':
            s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
            lab = (np.abs(t - ft * 0.5) < ft * 0.22) & (s > 0.35) & (s < fs - 0.35)
            col[lab] = b1
            txt = lab & (np.abs(t - ft * 0.5) < 0.12) & (s > fs * 0.25) & (s < fs * 0.75)
            col[txt] = hexrgb(c0) * 0.6
        return col
    return fn


def shell_tops(c):
    """rows of shotshells seen from above: brass heads with primers, packed in the crate"""
    s, t = c['s'], c['t']
    col = np.tile(hexrgb('#3a2a1c'), (len(s), 1))
    cs = (s % 1.0) - 0.5
    ct = (t % 1.0) - 0.5
    r = np.hypot(cs, ct)
    head = r < 0.44
    col[head] = m.brass(c)[head]
    col[r < 0.14] = hexrgb('#9a8a6a')
    row = np.floor(t).astype(int)
    red = (row % 3 == 1) & head & (r > 0.3)
    col[red] = hexrgb('#a3221b')
    return col


def scale_front(c):
    col = m.mottled('#22332a', var=0.07, scale=2.0)(c)
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    plate = (np.abs(t - ft * 0.5) < 0.2) & (np.abs(s - fs * 0.5) < 0.8)
    col[plate] = hexrgb('#c9b26a')
    return col


def beam_marks(c):
    col = m.mottled('#c3c6c4', var=0.05, scale=3)(c)
    s = c['s']
    col[((s * 2.5) % 1) < 0.25] *= 0.6
    return col


if __name__ == '__main__':
    print(len(build().els))
