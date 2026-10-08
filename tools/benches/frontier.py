"""[benchart] Frontier Workbench: golden oak camp/outfitter bench with leg vise, drawers, low shelf with camp gear
and a board-and-batten tool wall (handsaw, claw hammer, chisel rack, coiled rope, leather strips, lantern)."""
import math

import numpy as np

import materials as m
from benchlib import Scene, Mat
from common import frame, back_wall, TOP_Y1, LEG_X, LEG_Z
from noise import hexrgb, smoothstep, vnoise

BID = 'frontier_workbench'


def build():
    sc = Scene(BID)
    stains = [(9.0, 9.5, 1.3, 0.5), (24.5, 12.0, 0.9, 0.4)]
    OAK = Mat(m.end_grain_dark(m.wood('oak')), edge=0.4, seam=0.35)
    TOP = Mat(m.end_grain_dark(m.wood('oak', ring=2.8, wear_top=m.scratches(11, (0, 2, 32, 15), n=80, stains=stains))),
              edge=0.32, seam=0.45)
    OLD = Mat(m.wood('oldoak', ring=2.2, tone=0.1), edge=0.25, seam=0.5)
    HANDLE = Mat(m.wood('handle', ring=1.6), edge=0.3, seam=0.2)
    ASH = Mat(m.wood('ash', ring=1.8, tone=0.04), edge=0.25, seam=0.2)
    IRON, STEEL, BRASS = m.IRON, m.STEEL, m.BRASS
    ROPE = Mat(m.rope('#b8a072'), edge=0.05, seam=0.1)
    HIDE = Mat(m.leather('#a8794a', var=0.12), edge=0.1, seam=0.3)
    STRAP = Mat(m.leather('#6e4223'), edge=0.2, seam=0.2)
    CANVAS = Mat(m.canvas('#9a9068'), edge=0.12, seam=0.4)
    CANVAS2 = Mat(m.canvas('#6f7550'), edge=0.12, seam=0.4)
    WOOL = Mat(m.canvas('#8c2f24', weave=2.5), edge=0.1, seam=0.4)

    # ------------------------------------------------------------------ frame + top
    KNOB = Mat(m.wood('handle', ring=1.0, bias=0.08), edge=0.5, seam=0.2)
    DRAWER = Mat(m.wood('oak', ring=2.0, tone=0.06, bias=-0.14, contrast=1.2), edge=0.45, seam=0.3)
    frame(sc, OAK, OAK, TOP)
    # drawers: carcass front between the front legs, three drawer fronts with iron cup pulls
    dx = [(4.0, 11.75), (12.25, 19.75), (20.25, 28.0)]
    sc.box([3.75, 9.0, 3.25], [28.25, 12.0, 12.0], OAK, name='drawer carcass')
    for i, (a, b) in enumerate(dx):
        sc.box([a, 9.25, 2.75], [b, 11.75, 3.25], DRAWER, grain=0, name='drawer front')
        mid = (a + b) / 2
        sc.cyl('z', (mid, 10.5), 0.55, 2.0, 2.75, KNOB, name='knob')
        sc.box([mid - 0.4, 9.95, 1.75], [mid + 0.4, 11.05, 2.0], KNOB, name='knob face')
    # thin rail under the drawers
    sc.box([3.75, 8.5, 2.5], [28.25, 9.25, 3.25], OAK, name='drawer rail')

    # ------------------------------------------------------------------ leg vise (viewer's left end, +x)
    CHOP = Mat(m.end_grain_dark(m.wood('oak', ring=2.0, bias=-0.1)), edge=0.4, seam=0.4)
    lx0, lx1 = LEG_X[1]
    sc.box([lx0 - 1.0, 0.25, 1.0], [lx1 + 0.75, 15.5, 2.0], CHOP, grain=1, name='vise chop')
            # wooden screw hub (big turned boss) and the tommy bar through it, lying across the chop
    sc.cyl('z', (29.5, 11.25), 1.6, 0.25, 1.0, Mat(m.wood('handle', ring=1.2), edge=0.4), name='screw hub')
    sc.cyl('x', (11.25, 0.35), 0.35, 25.75, 31.25, ASH, name='tommy bar')
    sc.box([25.25, 10.75, 0.0], [25.75, 11.75, 0.85], ASH, name='tommy knob')
    sc.box([31.25, 10.75, 0.0], [31.75, 11.75, 0.85], ASH, name='tommy knob')
    # parallel guide (pin board) at the foot of the chop, running back through the leg
    sc.box([29.0, 1.25, 2.0], [30.0, 2.5, 7.0], OAK, name='parallel guide')
    sc.box([28.5, 1.5, 6.0], [30.5, 2.25, 6.5], ASH, name='guide pin')

    # ------------------------------------------------------------------ lower shelf: folded canvas, peg crate, bedroll
    sy = 4.5
    sc.box([5.0, sy, 5.5], [12.5, sy + 1.25, 12.0], CANVAS, name='canvas fold')
    sc.box([5.25, sy + 1.25, 5.75], [12.25, sy + 2.25, 11.75], CANVAS2, name='canvas fold')
    sc.box([5.5, sy + 2.25, 6.0], [12.0, sy + 3.0, 11.5], WOOL, name='blanket fold')
    sc.box([4.85, sy + 1.0, 8.5], [12.65, sy + 1.6, 9.0], STRAP, name='strap')  # strap around the stack
    # small crate of tent pegs
    cx0, cx1, cz0, cz1 = 14.0, 19.5, 6.0, 11.5
    sc.box([cx0, sy, cz0], [cx1, sy + 3.5, cz0 + 0.5], OLD, name='crate')
    sc.box([cx0, sy, cz1 - 0.5], [cx1, sy + 3.5, cz1], OLD, name='crate')
    sc.box([cx0, sy, cz0 + 0.5], [cx0 + 0.5, sy + 3.5, cz1 - 0.5], OLD, name='crate')
    sc.box([cx1 - 0.5, sy, cz0 + 0.5], [cx1, sy + 3.5, cz1 - 0.5], OLD, name='crate')
    sc.box([cx0 + 0.5, sy, cz0 + 0.5], [cx1 - 0.5, sy + 2.5, cz1 - 0.5], OLD, name='crate fill')
    rng = np.random.default_rng(4)
    for i in range(7):
        px = cx0 + 1.0 + (i % 4) * 1.1 + rng.uniform(-0.2, 0.2)
        pz = cz0 + 1.2 + (i // 4) * 2.0 + rng.uniform(-0.3, 0.3)
        h = rng.uniform(1.6, 2.6)
        sc.box([px, sy + 2.5, pz], [px + 0.5, sy + 2.5 + h, pz + 0.5], ASH if i % 3 else IRON, name='peg')
    # rolled hide bedroll tied with straps
    sc.cyl('z', (24.0, sy + 1.9), 1.9, 5.5, 12.25, HIDE, name='bedroll')
    sc.cyl('z', (24.0, sy + 1.9), 2.0, 7.0, 7.5, STRAP, name='bedroll tie')
    sc.cyl('z', (24.0, sy + 1.9), 2.0, 10.25, 10.75, STRAP, name='bedroll tie')

    # ------------------------------------------------------------------ on the top
    Y = TOP_Y1
    # No.5 jack plane: black japanned iron body, rosewood tote and knob, brass depth wheel
    JAPAN = Mat(m.mottled('#17161a', var=0.08, scale=2.0), edge=0.9, seam=0.2, gloss=0.2)
    ROSE = Mat(m.wood('handle', ring=1.1, bias=0.05), edge=0.35, seam=0.2)
    px0, pz0 = 3.5, 4.25
    sc.box([px0, Y, pz0], [px0 + 7.5, Y + 0.5, pz0 + 2.0], m.STEEL, grain=0, name='plane sole')
    sc.box([px0, Y + 0.5, pz0], [px0 + 7.5, Y + 1.5, pz0 + 0.35], JAPAN, grain=0, name='plane cheek')
    sc.box([px0, Y + 0.5, pz0 + 1.65], [px0 + 7.5, Y + 1.5, pz0 + 2.0], JAPAN, grain=0, name='plane cheek')
    sc.box([px0 + 0.25, Y + 0.5, pz0 + 0.35], [px0 + 7.25, Y + 0.75, pz0 + 1.65], JAPAN, name='plane bed')
    sc.box([px0 + 3.0, Y + 0.75, pz0 + 0.45], [px0 + 4.75, Y + 2.0, pz0 + 1.55], m.STEEL, rot=('z', -45, (px0 + 3.9, Y + 1.3, pz0 + 1.0)), name='lever cap')
    sc.box([px0 + 4.75, Y + 0.75, pz0 + 0.8], [px0 + 5.25, Y + 1.25, pz0 + 1.2], BRASS, name='depth wheel')
    sc.box([px0 + 5.25, Y + 0.75, pz0 + 0.65], [px0 + 6.25, Y + 2.75, pz0 + 1.35], ROSE, grain=1, rot=('z', 22.5, (px0 + 5.75, Y + 0.75, pz0 + 1.0)), name='tote')
    sc.box([px0 + 1.0, Y + 0.75, pz0 + 0.6], [px0 + 1.8, Y + 1.75, pz0 + 1.4], ROSE, name='knob')
    # curls of shavings next to the plane
    SHAV = Mat(m.flat('#e8cf9c', 0.08), edge=0.1, seam=0.0)
    sc.box([px0 - 1.5, Y, pz0 + 0.3], [px0 - 0.5, Y + 0.6, pz0 + 1.2], SHAV, name='shaving')
    sc.box([px0 - 1.25, Y, pz0 + 2.3], [px0 + 0.25, Y + 0.45, pz0 + 3.0], SHAV, name='shaving')
    sc.box([px0 + 1.5, Y, pz0 + 2.6], [px0 + 2.5, Y + 0.5, pz0 + 3.3], SHAV, name='shaving')
    # half-made leather possibles bag: dark chocolate leather panels being stitched, head knife, awl, thread
    LEA = Mat(m.leather('#4f2e1b', var=0.1), edge=0.35, seam=0.3)
    LEA2 = Mat(m.leather('#7b4b28', var=0.1), edge=0.3, seam=0.3)
    sc.box([12.5, Y, 6.5], [20.5, Y + 0.25, 12.5], LEA, name='leather panel')
    sc.box([13.25, Y + 0.25, 7.25], [18.0, Y + 0.5, 11.5], LEA2, name='leather panel 2', paint={'up': stitched})
    sc.box([20.5, Y, 7.5], [21.5, Y + 0.25, 11.0], LEA, name='leather lobe')
    sc.box([14.0, Y + 0.5, 4.75], [16.75, Y + 0.75, 6.0], m.STEEL, name='head knife blade')
    sc.box([14.95, Y + 0.25, 3.5], [15.8, Y + 1.0, 4.75], HANDLE, name='head knife handle')
    sc.box([18.25, Y + 0.25, 9.75], [21.75, Y + 0.5, 10.25], STRAP, name='cut strip')
    sc.box([17.5, Y, 3.5], [19.0, Y + 0.7, 4.1], HANDLE, name='awl handle')
    sc.box([19.0, Y + 0.25, 3.65], [20.5, Y + 0.45, 3.95], m.STEEL, name='awl spike')
    sc.cyl('y', (21.5, 4.5), 0.6, Y + 0.25, Y + 1.25, Mat(m.flat('#9b3a25', 0.12), edge=0.1), name='thread spool')
    sc.cyl('y', (21.5, 4.5), 0.85, Y + 1.25, Y + 1.5, ASH, name='spool flange')
    sc.cyl('y', (21.5, 4.5), 0.85, Y, Y + 0.25, ASH, name='spool flange')
    # carver's mallet lying across the top near the vise
    mr = ('y', 22.5, (23.1, Y, 6.0))
    sc.cyl('x', (Y + 0.5, 6.0), 0.4, 24.0, 27.0, ASH, rot=mr, name='mallet handle')
    sc.cyl('y', (23.1, 6.0), 1.25, Y, Y + 2.25, Mat(m.wood('oak', ring=1.4, bias=0.05), edge=0.3), notch=0.6, rot=mr, name='mallet head')
    # lantern standing on the top at the back, right of the hanging rope
    lx, lz = 27.25, 11.75
    TIN = Mat(m.mottled('#55524a', var=0.1, scale=1.5, rust='#5a3a22', rust_amt=0.15), edge=0.5, seam=0.2)
    GLOW = Mat(m.glass('#e8b24a'), edge=0.0, seam=0.0, ao=0.3)
    sc.box([lx - 1.5, Y, lz - 1.5], [lx + 1.5, Y + 0.75, lz + 1.5], TIN, name='lantern base')
    sc.box([lx - 1.1, Y + 0.75, lz - 1.1], [lx + 1.1, Y + 3.75, lz + 1.1], GLOW, name='lantern glass',
           paint={'*': lantern_glass})
    for ox in (-1, 1):
        for oz in (-1, 1):
            sc.box([lx + ox * 1.2 - 0.25, Y + 0.75, lz + oz * 1.2 - 0.25], [lx + ox * 1.2 + 0.25, Y + 3.75, lz + oz * 1.2 + 0.25],
                   TIN, name='lantern post')
    sc.box([lx - 1.5, Y + 3.75, lz - 1.5], [lx + 1.5, Y + 4.25, lz + 1.5], TIN, name='lantern cap')
    sc.box([lx - 0.9, Y + 4.25, lz - 0.9], [lx + 0.9, Y + 5.0, lz + 0.9], TIN, name='lantern hood')
    sc.box([lx - 0.4, Y + 5.0, lz - 0.4], [lx + 0.4, Y + 5.5, lz + 0.4], TIN, name='lantern chimney')
    sc.box([lx - 0.15, Y + 5.5, lz - 1.4], [lx + 0.15, Y + 6.5, lz - 1.1], IRON, name='bail')
    sc.box([lx - 0.15, Y + 5.5, lz + 1.1], [lx + 0.15, Y + 6.5, lz + 1.4], IRON, name='bail')
    sc.box([lx - 0.15, Y + 6.5, lz - 1.4], [lx + 0.15, Y + 6.8, lz + 1.4], IRON, name='bail top')

    # ------------------------------------------------------------------ back wall: old oak boards + peg rail
    back_wall(sc, OAK, OLD, y_top=25.0, batten_y=22.25, batten_mat=OAK)
    # pegs on the rail
    pegs = [7.0, 15.75, 26.0, 29.25]
    for p in pegs:
        sc.cyl('z', (p, 22.75), 0.3, 13.25, 14.5, ASH, name='peg')
    # handsaw hanging by its handle from peg 1 (teeth down, toe to the right/-x)
    SAW = Mat(saw_blade, edge=0.5, seam=0.1)
    bz0, bz1 = 14.0, 14.35
    sc.box([2.25, 17.0, bz0], [11.0, 19.25, bz1], SAW, name='saw blade')
    sc.box([4.5, 19.25, bz0], [11.0, 19.75, bz1], SAW, name='saw blade')
    sc.box([7.0, 19.75, bz0], [11.0, 20.25, bz1], SAW, name='saw blade')
    sc.box([9.25, 20.25, bz0], [11.0, 20.75, bz1], SAW, name='saw blade')
    hz0, hz1 = 13.75, 14.6
    sc.box([10.5, 16.5, hz0], [13.25, 17.25, hz1], HANDLE, grain=0, name='saw grip bottom')
    sc.box([10.5, 20.5, hz0], [13.0, 21.75, hz1], HANDLE, grain=0, name='saw grip top')
    sc.box([10.75, 17.25, hz0], [11.5, 20.5, hz1], HANDLE, grain=1, name='saw grip front')
    sc.box([12.25, 17.25, hz0], [13.25, 20.5, hz1], HANDLE, grain=1, name='saw grip back')
    sc.box([10.9, 18.0, hz0 - 0.1], [11.4, 18.5, hz1 + 0.1], BRASS, name='saw nut')
    sc.box([10.9, 19.5, hz0 - 0.1], [11.4, 20.0, hz1 + 0.1], BRASS, name='saw nut')
    # claw hammer hanging head-up from peg 2
    sc.box([15.4, 15.75, 13.75], [16.1, 22.25, 14.5], HANDLE, grain=1, name='hammer handle')
    sc.box([13.75, 21.25, 13.6], [17.25, 22.5, 14.6], IRON, grain=0, name='hammer head')
    sc.box([17.25, 21.5, 13.75], [17.75, 22.25, 14.5], STEEL, name='hammer face')
    sc.box([13.0, 20.5, 13.8], [13.75, 21.75, 14.4], IRON, name='claw')
    # chisel rack with four chisels (handles up)
    sc.box([18.0, 19.25, 12.75], [23.5, 19.75, 15.0], OAK, name='chisel rack')
    for i, (cx, w) in enumerate([(18.75, 0.75), (19.95, 0.6), (21.1, 0.5), (22.3, 0.85)]):
        sc.box([cx - 0.3, 19.75, 13.4], [cx + 0.3, 21.75 + (i % 2) * 0.4, 14.0], HANDLE, name='chisel handle')
        sc.box([cx - 0.35, 19.6, 13.35], [cx + 0.35, 19.9, 14.05], BRASS, name='ferrule')
        sc.box([cx - w / 2, 16.5 + (i % 2) * 0.75, 13.55], [cx + w / 2, 19.25, 13.85], STEEL, name='chisel blade')
    # coil of rope on peg 3 (octagonal ring built from straight bars)
    rc = (26.0, 19.25)
    R, th = 2.4, 0.75
    z0, z1 = 13.5, 14.6
    sc.box([rc[0] - 1.0, rc[1] + R - th, z0], [rc[0] + 1.0, rc[1] + R, z1], ROPE, grain=0, name='rope')
    sc.box([rc[0] - 1.0, rc[1] - R, z0], [rc[0] + 1.0, rc[1] - R + th, z1], ROPE, grain=0, name='rope')
    sc.box([rc[0] - R, rc[1] - 1.0, z0], [rc[0] - R + th, rc[1] + 1.0, z1], ROPE, grain=1, name='rope')
    sc.box([rc[0] + R - th, rc[1] - 1.0, z0], [rc[0] + R, rc[1] + 1.0, z1], ROPE, grain=1, name='rope')
    d = R * 0.707 - th / 2 + 0.05
    for sx in (-1, 1):
        for sy_ in (-1, 1):
            cxx, cyy = rc[0] + sx * d, rc[1] + sy_ * d
            ang = -45 if sx * sy_ > 0 else 45
            sc.box([cxx - 0.75, cyy - th / 2, z0 + 0.05], [cxx + 0.75, cyy + th / 2, z1 - 0.05], ROPE, grain=0,
                   rot=('z', ang, (cxx, cyy, (z0 + z1) / 2)), name='rope diag')
    # second, smaller inner turn and the hanging tail
    sc.box([rc[0] - 0.75, rc[1] + R - th - 0.5, z0 - 0.2], [rc[0] + 0.75, rc[1] + R - th, z0 + 0.4], ROPE, grain=0, name='rope turn')
    sc.box([rc[0] + 1.25, rc[1] - R - 2.0, 13.7], [rc[0] + 1.85, rc[1] - R + 0.25, 14.3], ROPE, grain=1, name='rope tail')
    # leather strips from peg 4
    for i, (ox, h) in enumerate([(-0.5, 5.0), (0.05, 6.25), (0.6, 4.25)]):
        x = 29.25 + ox
        sc.box([x - 0.25, 22.75 - h, 13.5 + i * 0.15], [x + 0.2, 22.9, 13.65 + i * 0.15], STRAP if i != 1 else HIDE, name='strip')
    # small shelf above the rail on the left: tins and a jar
    return sc


def stitched(c):
    """leather panel with a saddle-stitch line inset from its edge"""
    col = m.leather('#7b4b28', var=0.1)(c)
    s, t, fs, ft = c['s'], c['t'], c['fs'], c['ft']
    d = np.minimum(np.minimum(s, fs - s), np.minimum(t, ft - t))
    line = (np.abs(d - 0.5) < 0.1)
    along = np.where(np.minimum(s, fs - s) < np.minimum(t, ft - t), t, s)
    dash = (along * 1.6) % 1.0 < 0.6
    col[line & dash] = np.array([0.62, 0.52, 0.36])
    col[d < 0.25] *= 0.8
    return col


def lantern_glass(c):
    """warm glass with a wick-lit glow: brighter centre, chimney soot at the top"""
    P = c['P']
    e = c['el']
    if c['face'] in ('up', 'down'):
        return np.tile(hexrgb('#3a2a18'), (len(P), 1))
    h = e.to[1] - e.frm[1]
    k = (P[:, 1] - e.frm[1]) / h
    s = c['s'] / c['fs']
    glow = np.exp(-((s - 0.5) / 0.28) ** 2) * np.exp(-((k - 0.42) / 0.3) ** 2)
    base = hexrgb('#7a5a2c')
    hot = hexrgb('#ffd27a')
    col = base[None, :] * (1 - glow[:, None]) + hot[None, :] * glow[:, None]
    flame = np.exp(-((s - 0.5) / 0.09) ** 2) * np.exp(-((k - 0.38) / 0.12) ** 2)
    col = col + flame[:, None] * np.array([0.4, 0.3, 0.1])
    soot = smoothstep(0.75, 1.0, k)
    col = col * (1 - 0.45 * soot[:, None])
    streak = np.exp(-((s - 0.18) / 0.06) ** 2) * 0.25
    return (col + streak[:, None]).clip(0, 1)


def saw_blade(c):
    P = c['P']
    e = c['el']
    W = c['W']
    base = m.mottled('#7f868c', var=0.07, scale=2.5, streak=0.35, streak_axis=0)(c)
    if c['face'] in ('north', 'south'):
        y = W[:, 1]
        x = W[:, 0]
        # teeth along the bottom edge (y 17..17.6)
        tooth = ((x * 2.0) % 1.0)
        edge = 17.0 + 0.55 * tooth
        teeth = y < edge
        base[teeth & (y < 17.6)] *= 0.55
        # blued/etched patina blotches and a maker's etch
        n = vnoise(x * 0.4, y * 0.6, 1.0, seed=17)
        base *= (0.92 + 0.12 * n)[:, None]
        etch = (np.abs(x - 7.5) < 1.6) & (np.abs(y - 18.4) < 0.45)
        base[etch] *= 0.82
    return base


if __name__ == '__main__':
    sc = build()
    print(len(sc.els))
