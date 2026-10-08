"""[onboard2] Seat block models (Blockbench-style JSON elements), north-facing: the sitter looks to -z, backs are at +z.
Element helper + the five seats. Imported by tools/onboard2/seats.py (writes the files) and seat_check.py (clearance
check + previews)."""
import math

from seat_geom import SEATS, SLOPE, SUNK, TOWER_RAISE, hip_under

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def _wrap(a, b):
    """Shift a positional UV span into 0..16 (keeps the texel scale); squash only spans longer than 16."""
    if b - a > 16:
        return 0.0, 16.0
    k = math.floor(a / 16.0)
    a2, b2 = a - 16 * k, b - 16 * k
    if b2 > 16.0001:
        a2, b2 = a2 - (b2 - 16), 16.0
    return round(a2, 4), round(b2, 4)


def auto_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face == 'up':
        u, v = (x0, x1), (z0, z1)
    elif face == 'down':
        u, v = (x0, x1), (16 - z1, 16 - z0)
    elif face == 'north':
        u, v = (16 - x1, 16 - x0), (16 - y1, 16 - y0)
    elif face == 'south':
        u, v = (x0, x1), (16 - y1, 16 - y0)
    elif face == 'east':
        u, v = (16 - z1, 16 - z0), (16 - y1, 16 - y0)
    else:
        u, v = (z0, z1), (16 - y1, 16 - y0)
    u0, u1 = _wrap(*u)
    v0, v1 = _wrap(*v)
    return [u0, v0, u1, v1]


def box(f, t, tex, rot=None, skip=(), uv=None, tint=None, top=None, name=''):
    """One element. tex: texture key, or dict face->key. rot: (axis, angle, origin). top: texture key for up/down."""
    f = [round(c, 4) for c in f]
    t = [round(c, 4) for c in t]
    faces = {}
    for fc in FACES:
        if fc in skip:
            continue
        key = tex[fc] if isinstance(tex, dict) else (top if top and fc in ('up', 'down') else tex)
        faces[fc] = {'uv': (uv.get(fc) if isinstance(uv, dict) and fc in uv else auto_uv(fc, f, t)), 'texture': '#' + key}
    e = {'from': f, 'to': t, 'faces': faces}
    if name:
        e['name'] = name
    if rot:
        axis, ang, origin = rot
        e['rotation'] = {'angle': ang, 'axis': axis, 'origin': [round(c, 4) for c in origin]}
    return e


def shift(els, dy):
    out = []
    for e in els:
        e2 = {k: v for k, v in e.items()}
        e2['from'] = [e['from'][0], round(e['from'][1] + dy, 4), e['from'][2]]
        e2['to'] = [e['to'][0], round(e['to'][1] + dy, 4), e['to'][2]]
        if 'rotation' in e:
            r = dict(e['rotation'])
            r['origin'] = [r['origin'][0], round(r['origin'][1] + dy, 4), r['origin'][2]]
            e2['rotation'] = r
        faces = {}
        for k, fc in e['faces'].items():
            faces[k] = dict(fc)
            if k not in ('up', 'down'):
                faces[k]['uv'] = auto_uv(k, e2['from'], e2['to']) if 'keepuv' not in e else fc['uv']
        e2['faces'] = faces
        out.append(e2)
    return out


def cushion(x0, x1, z_front, z_back, bottom, seat_y, hip_z, tex, side_tex=None, lip=0.0, step=1.5, tol=0.12, cap=None):
    """A cushion/board whose top follows the thigh line: full height behind the hip, then stepped down toward the
    front edge so the sitter's thighs (which slope down 9.3 deg in the riding pose) never sink into it.
    Returns elements; the top of the slice whose front is d px before the hip is thigh_line(d) + tol."""
    els = []
    under = hip_under(seat_y)
    top_full = min(cap if cap is not None else 99, under + tol)
    # behind (and just in front of) the hip: full height
    z_cut = min(z_back, hip_z - 0.75)
    if z_cut < z_back:
        els.append(box([x0, bottom, z_cut], [x1, top_full, z_back], tex, top=None if side_tex is None else None, skip=('down',)))
    n = max(0, math.ceil((z_cut - z_front) / step - 0.25))
    st = (z_cut - z_front) / n if n else 0.0
    z = z_cut
    for i in range(n):
        z2 = z_front if i == n - 1 else z - st
        d = hip_z - z2
        topz = min(top_full, under - SLOPE * d + tol)
        if z2 <= z_front + 1e-6 and lip:
            topz -= lip
        els.append(box([x0, bottom, z2], [x1, topz, z], tex, skip=('down', 'south')))
        z = z2
    return els


# ================================================================================================ log stump seat
def stump():
    s = SEATS['log_stump_seat']
    T = {'bark': 'frontierhunts:block/seat/stump_bark', 'top': 'frontierhunts:block/seat/stump_top',
         'hide': 'frontierhunts:block/seat/hide_pad', 'particle': 'frontierhunts:block/seat/stump_bark'}
    els = []
    tex = {'north': 'bark', 'south': 'bark', 'east': 'bark', 'west': 'bark', 'up': 'top', 'down': 'top'}
    wood = 6.3
    # stepped-round stump (three overlapping slabs: rounded square silhouette), tops 0.02 apart so they never z-fight
    for (a0, a1, b0, b1, dy) in ((2, 14, 5, 11, 0.0), (5, 11, 2, 14, 0.02), (3, 13, 3, 13, 0.04)):
        els.append(box([a0, 0, b0], [a1, wood + dy, b1], tex, skip=('down',)))
    # root flare at the foot
    for (a0, a1, b0, b1) in ((1.4, 14.6, 4.6, 11.4), (4.6, 11.4, 1.4, 14.6), (2.4, 13.6, 2.4, 13.6)):
        els.append(box([a0, 0, b0], [a1, 1.2, b1], 'bark', skip=('down',)))
    # two root nubs
    els.append(box([0.6, 0, 6.5], [2.0, 0.8, 9.0], 'bark', skip=('down',)))
    els.append(box([10.5, 0, 13.8], [13.0, 0.7, 15.2], 'bark', skip=('down',)))
    # a round deer-hide pad on the sawn top (the growth rings show all round it). The stump swivels, so the pad is a low
    # dome: each ring of it sits under the thigh line at its own radius, whichever way the sitter turns
    under = hip_under(s['y'])
    for k, rho in enumerate((4.2, 3.1, 2.0)):
        top = min(under - SLOPE * rho + 0.06, under + 0.04)
        bot = wood + 0.02 if k == 0 else top - 0.6
        for j, (a, b) in enumerate(((1.0, 0.45), (0.45, 1.0), (0.72, 0.72))):
            els.append(box([8 - a * rho, bot, 8 - b * rho], [8 + a * rho, top + 0.012 * j, 8 + b * rho], 'hide', skip=('down',)))
    return {'textures': T, 'elements': els}


# ================================================================================================ camp chair
def camp_chair():
    s = SEATS['camp_chair']
    hz = 8 + s['hip']
    T = {'steel': 'frontierhunts:block/seat/steel_tube', 'canvas': 'frontierhunts:block/seat/canvas_olive',
         'rubber': 'frontierhunts:block/seat/rubber', 'particle': 'frontierhunts:block/seat/canvas_olive'}
    els = []
    rail = 5.0       # side seat rails: below the splayed thighs
    for x in (1.0, 14.0):
        # legs: the front ones end at the seat rail (the hands hang free in front), the back ones become the back posts
        els.append(box([x, 0.6, 2.6], [x + 1.0, rail + 1.0, 3.6], 'steel'))
        els.append(box([x, 0.6, 12.8], [x + 1.0, 15.6, 13.8], 'steel'))
        els.append(box([x - 0.2, 0, 2.4], [x + 1.2, 0.6, 3.8], 'rubber'))
        els.append(box([x - 0.2, 0, 12.6], [x + 1.2, 0.6, 14.0], 'rubber'))
        els.append(box([x, rail, 3.6], [x + 1.0, rail + 1.0, 12.8], 'steel'))           # side seat rail
        L = 10.4
        cz, cy = 8.2, 2.9                                                               # side X brace (+-22.5 deg)
        els.append(box([x + 0.15, cy - 0.4, cz - L / 2], [x + 0.85, cy + 0.4, cz + L / 2], 'steel', rot=('x', 22.5, [x + 0.5, cy, cz])))
        els.append(box([x + 0.15, cy - 0.4, cz - L / 2], [x + 0.85, cy + 0.4, cz + L / 2], 'steel', rot=('x', -22.5, [x + 0.5, cy, cz])))
        # armrest from the back post forward to a strut; it stops short so the forearms hang free in front of it
        ax0 = x - 0.5
        els.append(box([ax0, 10.2, 6.8], [ax0 + 2.0, 11.0, 14.0], 'canvas'))
        els.append(box([x + 0.1, rail + 1.0, 7.2], [x + 0.9, 10.2, 8.0], 'steel'))
    els.append(box([2.0, 3.8, 2.7], [14.0, 4.6, 3.5], 'steel'))                        # front cross tube
    els.append(box([2.0, 3.8, 12.9], [14.0, 4.6, 13.7], 'steel'))                      # back cross tube
    # canvas sling: hems wrapped round the rails, the seat stepped down to its front edge along the thigh line
    els.append(box([1.8, rail - 0.1, 3.5], [3.0, rail + 1.25, 12.8], 'canvas', skip=('down',)))
    els.append(box([13.0, rail - 0.1, 3.5], [14.2, rail + 1.25, 12.8], 'canvas', skip=('down',)))
    els += cushion(3.0, 13.0, 3.5, 12.6, rail + 0.4, s['y'], hz, 'canvas', step=2.6, tol=0.05)
    # upright canvas back between the posts (the sitter's back rests on it), rolled top hem over the posts
    els.append(box([2.0, 7.4, 12.4], [14.0, 15.2, 12.8], 'canvas'))
    els.append(box([1.8, 14.9, 12.2], [14.2, 15.9, 14.0], 'canvas'))
    # cup holder on the right armrest, outside and behind the hand
    els.append(box([14.6, 9.4, 8.4], [16.0, 10.2, 10.6], 'steel'))
    els.append(box([14.8, 8.4, 8.6], [15.8, 9.4, 10.4], 'canvas'))
    return {'textures': T, 'elements': els}


# ================================================================================================ trail bench
def trail_bench(left, right):
    """left/right: joined to another bench on the sitter's left (-x) / right (+x)."""
    s = SEATS['trail_bench']
    hz = 8 + s['hip']
    T = {'wood': 'frontierhunts:block/seat/weathered_pine', 'post': 'frontierhunts:block/seat/weathered_post',
         'iron': 'frontierhunts:block/seat/bolt_iron', 'particle': 'frontierhunts:block/seat/weathered_pine'}
    els = []
    x0 = 0.0 if left else 0.6
    x1 = 16.0 if right else 15.4
    # seat boards (run along x), each one stepped to the thigh line
    under = hip_under(s['y'])
    boards = ((9.4, 12.0), (6.7, 9.2), (4.0, 6.5))
    for i, (z0, z1) in enumerate(boards):
        d = hz - z0
        top = min(under + 0.05, under - SLOPE * max(0, d) + 0.05)
        els.append(box([x0, top - 1.4, z0], [x1, top, z1], 'wood', skip=() if not (left and right) else ('east', 'west')))
    # front board's rounded nose
    els.append(box([x0, 4.7, 3.5], [x1, 5.6, 4.1], 'wood'))
    # back rails
    for y0, y1 in ((9.4, 11.6), (12.4, 14.6)):
        els.append(box([x0, y0, 11.9], [x1, y1, 13.4], 'wood'))
    # ends: posts, legs, aprons, arm stubs (only at free ends)
    ends = []
    if not left:
        ends.append(1.0)
    if not right:
        ends.append(13.0)
    for ex in ends:
        els.append(box([ex, 0, 12.0], [ex + 2.0, 15.4, 14.0], 'post', top='wood'))          # back post
        els.append(box([ex, 0, 3.6], [ex + 2.0, 5.2, 5.6], 'post', top='wood'))             # front leg
        els.append(box([ex + 0.3, 1.6, 5.6], [ex + 1.7, 2.8, 12.0], 'wood'))                   # stretcher
        els.append(box([ex - 0.1, 9.9, 9.6], [ex + 2.1, 11.1, 14.2], 'wood'))                  # arm stub
        els.append(box([ex + 0.6, 10.5, 12.0], [ex + 1.4, 11.3, 11.6], 'iron', skip=('south',)))  # bolt head
    if left and right:
        # a middle section gets its own centre trestle
        els.append(box([7.0, 0, 3.8], [9.0, 5.2, 5.6], 'post', top='wood'))
        els.append(box([7.0, 0, 12.0], [9.0, 9.4, 13.6], 'post', top='wood'))
        els.append(box([7.3, 1.6, 5.6], [8.7, 2.8, 12.0], 'wood'))
    # apron under the seat (front + back), iron brackets
    els.append(box([x0 + 0.4, 4.4, 4.2], [x1 - 0.4, 5.4, 5.0], 'wood'))
    els.append(box([x0 + 0.4, 4.4, 11.4], [x1 - 0.4, 5.4, 12.2], 'wood'))
    return {'textures': T, 'elements': els}


# ================================================================================================ swivel chairs
def _swivel_top(seat_y, back_lo, back_hi, plate_y):
    """Rotating part of a swivel chair, built around the swivel axis (8, ., 8): steel seat pan, a padded camo cushion
    stepped to the thigh line with black piping, a contoured padded back on a steel stem. Fits inside the block at
    every swivel angle (all corners within 8 px of the axis)."""
    els = []
    els.append(box([3.4, plate_y, 3.4], [12.6, plate_y + 0.6, 12.6], 'steel'))           # seat pan
    cb = plate_y + 0.6
    els += cushion(3.0, 13.0, 3.0, 13.0, cb, seat_y, 8.0, 'camo', step=2.0, tol=0.05)
    # piping round the cushion's lower edge (kept under the thigh line at the front)
    pip = cb + 0.35
    els.append(box([2.8, cb - 0.1, 2.8], [13.2, pip, 3.2], 'rubber'))
    els.append(box([2.8, cb - 0.1, 12.8], [13.2, pip, 13.2], 'rubber'))
    els.append(box([2.8, cb - 0.1, 3.2], [3.2, pip, 12.8], 'rubber'))
    els.append(box([12.8, cb - 0.1, 3.2], [13.2, pip, 12.8], 'rubber'))
    # back: steel stem, shell, padded back with a rolled top and piping
    els.append(box([7.1, plate_y - 0.2, 12.4], [8.9, back_lo + 1.0, 13.4], 'steel'))
    els.append(box([7.1, plate_y - 0.2, 10.6], [8.9, plate_y + 0.4, 12.4], 'steel'))
    els.append(box([3.4, back_lo + 0.3, 12.4], [12.6, back_hi - 0.3, 12.9], 'steel', skip=('north',)))
    els.append(box([3.2, back_lo, 10.9], [12.8, back_hi - 0.5, 12.4], 'camo'))
    els.append(box([3.5, back_hi - 0.5, 11.1], [12.5, back_hi, 12.4], 'camo'))
    els.append(box([3.0, back_lo - 0.1, 11.0], [13.0, back_lo + 0.3, 12.5], 'rubber'))
    return els


def _swivel_base(top, foot_ring):
    """Star base on a centre column (the swivel chairs of blinds and shooting houses): four arms on the diagonals with
    rubber feet, a hub, the gas column and the bearing (top = bearing top = the seat pan's underside).
    foot_ring: the tower chair's footrest ring on four spokes."""
    els = []
    L = 15.6
    for ang in (45, -45):
        els.append(box([8 - L / 2, 0.7, 7.3], [8 + L / 2, 1.7, 8.7], 'steel', rot=('y', ang, [8, 1.2, 8])))
    for x, z in ((2.6, 2.6), (11.8, 2.6), (2.6, 11.8), (11.8, 11.8)):
        els.append(box([x - 0.4, 0, z - 0.4], [x + 2.0, 0.8, z + 2.0], 'rubber'))
    els.append(box([6.6, 0.5, 6.6], [9.4, 2.4, 9.4], 'steel'))           # hub
    els.append(box([6.9, 2.4, 6.9], [9.1, 3.6, 9.1], 'rubber'))          # column boot
    els.append(box([7.2, 3.6, 7.2], [8.8, top - 0.5, 8.8], 'steel'))     # gas column
    els.append(box([6.2, top - 1.1, 6.2], [9.8, top - 0.5, 9.8], 'steel'))  # tilt plate
    els.append(box([5.2, top - 0.5, 5.2], [10.8, top, 10.8], 'rubber'))  # bearing
    if foot_ring:
        y = 3.4
        for a, b in (([3.0, y, 3.0], [13.0, y + 0.7, 3.8]), ([3.0, y, 12.2], [13.0, y + 0.7, 13.0]),
                     ([3.0, y, 3.8], [3.8, y + 0.7, 12.2]), ([12.2, y, 3.8], [13.0, y + 0.7, 12.2])):
            els.append(box(a, b, 'steel'))
        els.append(box([3.8, y + 0.1, 7.6], [12.2, y + 0.6, 8.4], 'steel'))   # spokes to the column
        els.append(box([7.6, y + 0.1, 3.8], [8.4, y + 0.6, 12.2], 'steel'))
        for x, z in ((3.0, 3.0), (12.2, 3.0), (3.0, 12.2), (12.2, 12.2)):
            els.append(box([x - 0.1, y - 0.1, z - 0.1], [x + 0.9, y + 0.8, z + 0.9], 'rubber'))
    return els


def blind_chair_base():
    return _swivel_base(5.5, False)


def blind_chair_top():
    s = SEATS['blind_chair']
    return _swivel_top(s['y'], 8.6, 13.0, 5.5)


def tower_chair_base(raised):
    """Tall shooting chair: the same frame on longer legs, with a footrest ring; raised = gas lift up 2.64 px."""
    lift = TOWER_RAISE if raised else 0.0
    return _swivel_base(6.1 + lift, True)


def tower_chair_top(raised=False):
    s = SEATS['tower_chair']
    lift = TOWER_RAISE if raised else 0.0
    return _swivel_top(s['y'] + lift, 9.2 + lift, 14.4 + lift, 6.1 + lift)


SWIVEL_T = {'steel': 'frontierhunts:block/seat/steel_tube', 'camo': 'frontierhunts:block/seat/camo_pad',
            'rubber': 'frontierhunts:block/seat/rubber', 'particle': 'frontierhunts:block/seat/camo_pad'}


def blind_chair(part):
    els = {'base': blind_chair_base(), 'top': blind_chair_top(), 'full': blind_chair_base() + blind_chair_top()}[part]
    return {'textures': SWIVEL_T, 'elements': els}


def tower_chair(part, raised=False, sunk=False):
    if part == 'top':
        els = tower_chair_top(False)  # the entity renderer lifts / sinks the rotating top itself
    else:
        els = tower_chair_base(raised) + (tower_chair_top(raised) if part == 'full' else [])
        if sunk:
            els = shift(els, SUNK)
    return {'textures': SWIVEL_T, 'elements': els}
