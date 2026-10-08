"""[benchart] Shared bench construction (the 'family'): heavy legs, side aprons, low stretchers, thick laminated top,
framed back wall.  Every bench is authored facing north: front edge of the top at z = 2, back wall at z 14.5..16."""
import numpy as np

TOP_Y0, TOP_Y1 = 12.0, 15.0
TOP_Z0, TOP_Z1 = 2.0, 15.0
LEG_X = ((1.25, 3.75), (28.25, 30.75))
LEG_Z = ((2.0, 4.5), (12.25, 14.75))


def frame(sc, wood, end, top_mat, top_planks=((2.0, 6.25), (6.25, 10.75), (10.75, 15.0)), shelf=True,
          shelf_mat=None, back_stretcher=True, top_x=(0.5, 31.5), top_y0=TOP_Y0):
    """legs, aprons, stretchers, top.  Returns nothing; elements appended to sc."""
    # legs (2.5 x 2.5 posts)
    for x0, x1 in LEG_X:
        for z0, z1 in LEG_Z:
            sc.box([x0, 0, z0], [x1, top_y0, z1], wood, name='leg', paint={'*': leg_pegs(wood, (top_y0 - 1.25, 2.75))})
    # end aprons under the top (between front and back legs) and low end stretchers
    for x0, x1 in LEG_X:
        xm0, xm1 = x0 + 0.5, x1 - 0.5
        sc.box([xm0, top_y0 - 2.5, LEG_Z[0][1]], [xm1, top_y0, LEG_Z[1][0]], wood, name='end apron')
        sc.box([xm0, 1.75, LEG_Z[0][1]], [xm1, 3.75, LEG_Z[1][0]], wood, name='end stretcher')
    # back apron
    sc.box([LEG_X[0][1], top_y0 - 2.5, LEG_Z[1][0] + 0.75], [LEG_X[1][0], top_y0, LEG_Z[1][1] - 0.5], wood, name='back apron')
    if back_stretcher:
        sc.box([LEG_X[0][1], 2.25, LEG_Z[1][0] + 0.5], [LEG_X[1][0], 4.25, LEG_Z[1][1] - 0.5], wood, name='back stretcher')
    if shelf:
        sm = shelf_mat or wood
        # shelf boards resting on the end stretchers (three boards, slightly different widths)
        zs = (LEG_Z[0][1], 7.25, 10.0, LEG_Z[1][0] + 0.5)
        for i in range(3):
            sc.box([LEG_X[0][1], 3.75, zs[i]], [LEG_X[1][0], 4.5, zs[i + 1]], sm, name='shelf board')
    # top: laminated planks running along x, captured by breadboard ends (grain along z) with pegged tenons
    bb = 1.25
    for z0, z1 in top_planks:
        sc.box([top_x[0] + bb, top_y0, z0], [top_x[1] - bb, TOP_Y1, z1], top_mat, name='top plank')
    z0, z1 = top_planks[0][0], top_planks[-1][1]
    for xa, xb in ((top_x[0], top_x[0] + bb), (top_x[1] - bb, top_x[1])):
        sc.box([xa, top_y0, z0], [xb, TOP_Y1, z1], top_mat, grain=2, name='breadboard end',
               paint={'up': pegged(top_mat, z0, z1)})


def pegged(mat, z0, z1, n=3):
    """breadboard end top face: darker drawbore pegs (end grain) evenly along the end"""
    zs = np.linspace(z0, z1, n + 2)[1:-1]

    def fn(c):
        col = mat.fn(c)
        W = c['W']
        e = c['el']
        xc = (e.obounds[0][0] + e.obounds[1][0]) / 2
        for z in zs:
            d = np.maximum(np.abs(W[:, 0] - xc), np.abs(W[:, 2] - z))
            col[d < 0.32] *= 0.55
            col[(d >= 0.32) & (d < 0.5)] *= 0.85
        return col
    return fn


def leg_pegs(mat, ys):
    """leg faces: square drawbore pegs where the aprons / stretchers are tenoned in"""
    def fn(c):
        col = mat.fn(c)
        if c['face'] in ('up', 'down'):
            return col
        s, fs = c['s'], c['fs']
        y = c['W'][:, 1]
        for yy in ys:
            d = np.maximum(np.abs(s - fs / 2), np.abs(y - yy))
            col[d < 0.3] *= 0.55
            col[(d >= 0.3) & (d < 0.45)] *= 0.85
        return col
    return fn


def back_wall(sc, wood, boards_mat, post_mat=None, y_top=25.0, cap=True, x0=0.5, x1=31.5, board_w=4.0,
              batten_y=None, batten_mat=None, back_battens=(16.25, 22.5)):
    """framed back wall standing on the rear of the top: two posts, vertical boards (z 15..16) and a cap rail"""
    pm = post_mat or wood
    sc.box([x0, TOP_Y1, 14.5], [x0 + 1.5, y_top, 16], pm, name='post')
    sc.box([x1 - 1.5, TOP_Y1, 14.5], [x1, y_top, 16], pm, name='post')
    xs = np.arange(x0 + 1.5, x1 - 1.5 + 1e-6, board_w)
    if xs[-1] < x1 - 1.5 - 1e-6:
        xs = np.append(xs, x1 - 1.5)
    for a, b in zip(xs[:-1], xs[1:]):
        sc.box([a, TOP_Y0 + 0.5, 15.0], [b, y_top, 15.75], boards_mat, grain=1, name='wall board')
    for by in back_battens:
        # board-and-batten: horizontal cleats on the back holding the boards together
        sc.box([x0 + 1.5, by, 15.75], [x1 - 1.5, by + 1.25, 16], wood, grain=0, name='back cleat')
    if cap:
        sc.box([x0 - 0.25, y_top, 14.25], [x1 + 0.25, y_top + 1.0, 16], pm, name='cap rail')
    if batten_y is not None:
        sc.box([x0 + 1.5, batten_y, 14.5], [x1 - 1.5, batten_y + 1.0, 15.25], batten_mat or pm, name='batten')
