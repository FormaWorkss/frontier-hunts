"""[sticks] Mesh of the shooting sticks, built from simple solids. Units: blocks (= metres).

Parts (each a list of quads; vertex = x, y, z, u, v, nx, ny, nz):
  upper / middle / lower : one leg's three telescoping sections, leg-local frame (hinge at the origin, the leg runs
                           down -Y, +Z points away from the tripod's centre). middle / lower are drawn translated down
                           the leg by how far they are extended (see sticks_art.leg_layout / ShootingSticksRenderer).
  hub                    : the head (central body, three clevis ears, swivel post), origin at the hinge plane, Y up,
                           leg 0's ear on +Z.
  strap                  : the wrist strap hanging from the head (head frame).
  yoke                   : the padded V cradle on its swivel (origin on top of the swivel post, the gun lies along Z).
"""
import math
import struct

TW, TH = 256, 128
SEG = 0.62          # tube section length
R_UP, R_MID, R_LOW = 0.0165, 0.0135, 0.0105
R_COLLAR_UP, R_COLLAR_MID = 0.0235, 0.0195
HINGE_R = 0.03      # hinge axis distance from the head's centre
SWIVEL_TOP = 0.046  # top of the swivel post above the hinge plane
APEX = 0.057        # bottom of the V above the hinge plane (a 44 mm forend's underside sits at 0.075)

REG = {
    'upper': (0, 0, 32, 112), 'middle': (32, 0, 56, 112), 'lower': (56, 0, 76, 112),
    'collar': (76, 0, 108, 24), 'lever': (76, 24, 108, 40), 'foot': (76, 40, 108, 64), 'strap': (76, 64, 140, 80),
    'hub': (108, 0, 140, 32), 'frame': (108, 32, 140, 56), 'foam': (140, 0, 204, 64), 'pin': (204, 0, 220, 16),
    'endcap': (204, 16, 220, 32),
}


def uv(region, fu, fv):
    u0, v0, u1, v1 = REG[region]
    return ((u0 + (u1 - u0) * fu) / TW, (v0 + (v1 - v0) * fv) / TH)


def norm(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / l for c in v)


def quad(out, ps, uvs, n=None):
    if n is None:
        a = [ps[1][i] - ps[0][i] for i in range(3)]
        b = [ps[2][i] - ps[0][i] for i in range(3)]
        n = norm((a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]))
        if n == (0.0, 0.0, 0.0):
            a = [ps[3][i] - ps[0][i] for i in range(3)]
            n = norm((a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]))
    ns = n if isinstance(n[0], (tuple, list)) else [n] * 4
    out.append([(ps[i][0], ps[i][1], ps[i][2], uvs[i][0], uvs[i][1], ns[i][0], ns[i][1], ns[i][2]) for i in range(4)])


def tube_y(out, region, r_top, r_bot, y_top, y_bot, sides=10, cap_top=None, cap_bot=None, v_from=0.0, v_to=1.0, u_turns=1.0, cx=0.0, cz=0.0,
           phase=0.0):
    """Vertical tube (axis Y). Outward normals; v runs from the top of the tube to the bottom. Caps: region name or None."""
    for i in range(sides):
        a0 = 2 * math.pi * i / sides + phase
        a1 = 2 * math.pi * (i + 1) / sides + phase
        c0, s0, c1, s1 = math.cos(a0), math.sin(a0), math.cos(a1), math.sin(a1)
        p = [(cx + s0 * r_top, y_top, cz + c0 * r_top), (cx + s0 * r_bot, y_bot, cz + c0 * r_bot), (cx + s1 * r_bot, y_bot, cz + c1 * r_bot),
             (cx + s1 * r_top, y_top, cz + c1 * r_top)]
        slope = (r_bot - r_top) / max(1e-6, (y_top - y_bot))
        n0 = norm((s0, slope, c0))
        n1 = norm((s1, slope, c1))
        fu0, fu1 = i / sides * u_turns, (i + 1) / sides * u_turns
        quad(out, p, [uv(region, fu0, v_from), uv(region, fu0, v_to), uv(region, fu1, v_to), uv(region, fu1, v_from)], [n0, n0, n1, n1])
    for cap, y, r, up in ((cap_top, y_top, r_top, True), (cap_bot, y_bot, r_bot, False)):
        if cap is None:
            continue
        for i in range(sides):
            a0 = 2 * math.pi * i / sides + phase
            a1 = 2 * math.pi * (i + 1) / sides + phase
            p0 = (cx, y, cz)
            p1 = (cx + math.sin(a0) * r, y, cz + math.cos(a0) * r)
            p2 = (cx + math.sin(a1) * r, y, cz + math.cos(a1) * r)
            u0 = uv(cap, 0.5, 0.5)
            u1 = uv(cap, 0.5 + 0.5 * math.sin(a0), 0.5 + 0.5 * math.cos(a0))
            u2 = uv(cap, 0.5 + 0.5 * math.sin(a1), 0.5 + 0.5 * math.cos(a1))
            if up:
                quad(out, [p0, p2, p1, p1], [u0, u2, u1, u1], (0, 1, 0))
            else:
                quad(out, [p0, p1, p2, p2], [u0, u1, u2, u2], (0, -1, 0))


def annulus_y(out, region, r_in, r_out, y, up, sides=10, phase=0.0):
    for i in range(sides):
        a0 = 2 * math.pi * i / sides + phase
        a1 = 2 * math.pi * (i + 1) / sides + phase
        pi0 = (math.sin(a0) * r_in, y, math.cos(a0) * r_in)
        pi1 = (math.sin(a1) * r_in, y, math.cos(a1) * r_in)
        po0 = (math.sin(a0) * r_out, y, math.cos(a0) * r_out)
        po1 = (math.sin(a1) * r_out, y, math.cos(a1) * r_out)
        fu0, fu1 = i / sides, (i + 1) / sides
        uvs = [uv(region, fu0, 0.0), uv(region, fu0, 0.15), uv(region, fu1, 0.15), uv(region, fu1, 0.0)]
        if up:
            quad(out, [pi0, po0, po1, pi1], uvs, (0, 1, 0))
        else:
            quad(out, [pi0, pi1, po1, po0], [uvs[0], uvs[3], uvs[2], uvs[1]], (0, -1, 0))


def box(out, region, x0, y0, z0, x1, y1, z1, xf=None):
    """Axis-aligned box; xf optional function mapping a point to another (rotation / placement), normals follow."""
    def P(x, y, z):
        return xf((x, y, z)) if xf else (x, y, z)

    def N(n):
        if not xf:
            return n
        o = xf((0, 0, 0))
        p = xf(n)
        return norm(tuple(p[i] - o[i] for i in range(3)))

    faces = [
        ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1)),
        ((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1)),
        ((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0)),
        ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0)),
        ((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), (0, 1, 0)),
        ((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (0, -1, 0)),
    ]
    for f in faces:
        ps = [P(*f[i]) for i in range(4)]
        n = N(f[4])
        quad(out, ps, [uv(region, 0, 1), uv(region, 1, 1), uv(region, 1, 0), uv(region, 0, 0)], n)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return lambda p: (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return lambda p: (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return lambda p: (p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2])


def chain(*fs):
    def f(p):
        for g in fs:
            p = g(p)
        return p
    return f


def move(dx, dy, dz):
    return lambda p: (p[0] + dx, p[1] + dy, p[2] + dz)


# ------------------------------------------------------------------------------------------- parts

def collar(out, r_tube, r_col, y_top, y_bot, lever=True):
    """Leg-lock: a ribbed collar round the tube's end with a cam lever folded flat on the outside (+Z)."""
    tube_y(out, 'collar', r_col * 0.92, r_col, y_top, y_top - 0.008, sides=12, v_from=0.0, v_to=0.2, u_turns=2)
    tube_y(out, 'collar', r_col, r_col, y_top - 0.008, y_bot, sides=12, v_from=0.2, v_to=1.0, u_turns=2)
    annulus_y(out, 'collar', r_tube, r_col * 0.92, y_top, True, sides=12)
    annulus_y(out, 'collar', r_tube * 0.9, r_col, y_bot, False, sides=12)
    if lever:
        h = y_top - y_bot
        # the lever: a flat curved paddle hinged at the bottom, lying against the collar
        xf = chain(rot_x(math.radians(-6)), move(0, y_bot + 0.004, r_col - 0.001))
        box(out, 'lever', -0.0085, 0.0, 0.0, 0.0085, h + 0.012, 0.0075, xf)
        # the cam knuckle at the bottom
        box(out, 'pin', -0.0105, -0.004, -0.002, 0.0105, 0.006, 0.006, chain(move(0, y_bot, r_col)))


def leg_upper():
    out = []
    # knuckle that sits in the head's clevis, with its pin
    box(out, 'hub', -0.0085, -0.036, -0.0125, 0.0085, 0.012, 0.0125)
    box(out, 'pin', -0.0118, -0.006, -0.005, 0.0118, 0.004, 0.005)
    tube_y(out, 'upper', R_UP, R_UP, -0.03, -SEG + 0.004, v_from=0.04, v_to=0.995)
    collar(out, R_UP, R_COLLAR_UP, -SEG + 0.06, -SEG - 0.004)
    return out


def leg_middle():
    out = []
    tube_y(out, 'middle', R_MID, R_MID, 0.0, -SEG + 0.004, cap_top='endcap', v_from=0.0, v_to=0.995)
    collar(out, R_MID, R_COLLAR_MID, -SEG + 0.055, -SEG - 0.004)
    return out


def leg_lower():
    out = []
    tube_y(out, 'lower', R_LOW, R_LOW, 0.0, -0.585, cap_top='endcap', v_from=0.0, v_to=0.94)
    # rubber foot: a ribbed boot, then a rounded sole
    tube_y(out, 'foot', 0.0145, 0.0165, -0.585, -0.598, sides=12, v_from=0.0, v_to=0.25)
    tube_y(out, 'foot', 0.0165, 0.0165, -0.598, -0.636, sides=12, v_from=0.25, v_to=0.75)
    tube_y(out, 'foot', 0.0165, 0.0105, -0.636, -0.66, sides=12, cap_bot='foot', v_from=0.75, v_to=1.0)
    annulus_y(out, 'foot', R_LOW, 0.0145, -0.585, True, sides=12)
    return out


def hub():
    out = []
    tube_y(out, 'hub', 0.024, 0.027, 0.02, 0.008, sides=12, cap_top='hub', v_from=0.0, v_to=0.3)
    tube_y(out, 'hub', 0.027, 0.027, 0.008, -0.016, sides=12, v_from=0.3, v_to=0.8)
    tube_y(out, 'hub', 0.027, 0.019, -0.016, -0.026, sides=12, cap_bot='hub', v_from=0.8, v_to=1.0)
    for i in range(3):
        f = rot_y(2 * math.pi * i / 3)
        # clevis: two cheeks either side of the leg's knuckle, and the hinge pin through them
        box(out, 'hub', -0.0135, -0.018, 0.016, -0.0088, 0.016, HINGE_R + 0.013, f)
        box(out, 'hub', 0.0088, -0.018, 0.016, 0.0135, 0.016, HINGE_R + 0.013, f)
        box(out, 'pin', -0.0145, -0.005, HINGE_R - 0.005, 0.0145, 0.005, HINGE_R + 0.005, f)
    # swivel post and its lock knob
    tube_y(out, 'frame', 0.0105, 0.0105, SWIVEL_TOP, 0.02, sides=10, v_from=0.0, v_to=0.5)
    box(out, 'lever', -0.004, 0.03, 0.0095, 0.004, 0.038, 0.024)
    tube_y(out, 'collar', 0.0075, 0.0075, 0.0385, 0.0295, sides=8, cap_top='pin', cx=0.0, cz=0.027)
    return out


def strap():
    """A wrist loop of webbing hanging from the head between leg 1 and leg 2 (behind, towards the shooter)."""
    out = []
    w = 0.009
    pts = []
    n = 14
    for i in range(n + 1):
        t = i / n
        a = math.pi * t
        # a hanging loop: down 0.16, 0.05 wide, swung out a little
        x = 0.026 * math.cos(a)
        y = -0.012 - 0.16 * math.sin(a) ** 1.4
        z = -0.028 - 0.03 * math.sin(a)
        pts.append((x, y, z))
    for i in range(n):
        p0, p1 = pts[i], pts[i + 1]
        for side in (1, -1):
            a = (p0[0], p0[1], p0[2] - side * 0)
            q = [(p0[0], p0[1], p0[2] - w), (p1[0], p1[1], p1[2] - w), (p1[0], p1[1], p1[2] + w), (p0[0], p0[1], p0[2] + w)]
            uvs = [uv('strap', i / n, 0), uv('strap', (i + 1) / n, 0), uv('strap', (i + 1) / n, 1), uv('strap', i / n, 1)]
            if side < 0:
                q = q[::-1]
                uvs = uvs[::-1]
            quad(out, q, uvs)
    # a buckle where it meets the head
    box(out, 'pin', -0.006, -0.02, -0.032, 0.006, -0.006, -0.024)
    return out


def yoke():
    """The cradle, origin on top of the swivel post; the forend lies along Z. Arms at 42 deg from vertical."""
    out = []
    tube_y(out, 'frame', 0.0135, 0.0135, 0.008, 0.0, sides=12, cap_bot='frame', cap_top='frame')
    apex = APEX - SWIVEL_TOP  # bottom of the V above the swivel top
    box(out, 'frame', -0.014, 0.006, -0.019, 0.014, apex + 0.001, 0.019)  # the bridge under the V
    ang = math.radians(33)
    L = 0.064
    depth = 0.025
    tpad = 0.011
    prof = [(tpad, 0.0), (tpad * 0.3, 0.0), (0.0, 0.009), (0.0, L - 0.012), (tpad * 0.45, L + 0.003), (tpad, L + 0.006)]
    for sx in (-1, 1):
        # arm frame: local +Y along the arm, local +X away from the V's middle; mirrored for the left arm
        f = chain(lambda p, sx=sx: (sx * p[0], p[1], p[2]), rot_z(-sx * ang), move(0, apex, 0))
        if sx > 0:
            box(out, 'frame', tpad - 0.0005, -0.004, -0.02, tpad + 0.0035, L + 0.002, 0.02, f)
        else:
            box(out, 'frame', -(tpad + 0.0035), -0.004, -0.02, -(tpad - 0.0005), L + 0.002, 0.02, chain(rot_z(ang), move(0, apex, 0)))
        n = len(prof)
        for k in range(n - 1):
            (ax, ay), (bx, by) = prof[k], prof[k + 1]
            q = [(ax, ay, depth), (bx, by, depth), (bx, by, -depth), (ax, ay, -depth)]
            uvs = [uv('foam', 1.0, k / (n - 1)), uv('foam', 1.0, (k + 1) / (n - 1)), uv('foam', 0.0, (k + 1) / (n - 1)), uv('foam', 0.0, k / (n - 1))]
            q = [f(p) for p in q]
            if sx < 0:
                q, uvs = q[::-1], uvs[::-1]
            quad(out, q, uvs)
        for zc in (depth, -depth):
            for k in range(1, n - 1):
                tri = [(prof[0][0], prof[0][1], zc), (prof[k][0], prof[k][1], zc), (prof[k + 1][0], prof[k + 1][1], zc)]
                uvs = [uv('foam', 0.5 + 0.4 * p[0] / tpad, p[1] / (L + 0.006)) for p in tri]
                if (zc > 0) == (sx > 0):
                    tri, uvs = tri[::-1], uvs[::-1]
                tri = [f(p) for p in tri]
                quad(out, tri + [tri[2]], uvs + [uvs[2]])
    box(out, 'foam', -0.008, apex - 0.002, -depth, 0.008, apex + 0.003, depth)  # a small pad in the bottom of the V
    return out


PARTS = [('upper', leg_upper), ('middle', leg_middle), ('lower', leg_lower), ('hub', hub), ('strap', strap), ('yoke', yoke)]


def build():
    return {name: fn() for name, fn in PARTS}


def write(parts, path):
    """FHSK v1: int magic 'FHSK', int version 1, int parts; per part: UTF name, int quads, quads * 4 * 8 floats (big endian)."""
    with open(path, 'wb') as f:
        f.write(struct.pack('>iii', 0x4648534B, 1, len(parts)))
        for name, quads in parts.items():
            b = name.encode('utf-8')
            f.write(struct.pack('>H', len(b)) + b)
            f.write(struct.pack('>i', len(quads)))
            for q in quads:
                for v in q:
                    f.write(struct.pack('>8f', *v))


# ------------------------------------------------------------------------------------------- the assembly (same maths as ShootingSticksRenderer)

SPLAY = 19.0
HUB_BELOW = 0.075
HEIGHTS = {'standing': 1.445, 'kneeling': 1.025, 'sitting': 0.825}
MIN_LEG = 0.74
MAX_LEG = 1.66


def leg_length(contact):
    return (contact - HUB_BELOW) / math.cos(math.radians(SPLAY))


def leg_layout(L):
    """(middle offset, lower offset) down the leg for a leg L long (hinge to the sole)."""
    L = max(MIN_LEG, min(MAX_LEG, L))
    e = (L - MIN_LEG) / 2
    return 0.06 + e, 0.06 + e + 0.02 + e


def assemble(parts, contact, splay_deg=SPLAY, leg_len=None, yoke_yaw=0.0, yoke_tilt=0.0, folded=False):
    """World-space quads of a set standing at the origin facing +Z. Mirrors ShootingSticksRenderer.draw."""
    L = leg_len if leg_len is not None else leg_length(contact)
    hub_y = contact - HUB_BELOW
    out = []
    m_off, l_off = leg_layout(L)
    sp = math.radians(splay_deg)
    for i in range(3):
        az = 2 * math.pi * i / 3
        base = chain(rot_x(-sp), move(0, 0, HINGE_R), rot_y(az), move(0, hub_y, 0))
        for name, dy in (('upper', 0.0), ('middle', -m_off), ('lower', -l_off)):
            f = chain(move(0, dy, 0), base)
            for q in parts[name]:
                out.append(xform(q, f))
    hf = move(0, hub_y, 0)
    for name in ('hub', 'strap'):
        for q in parts[name]:
            out.append(xform(q, hf))
    yf = chain(rot_x(math.radians(yoke_tilt)), move(0, SWIVEL_TOP, 0), rot_y(math.radians(yoke_yaw)), move(0, hub_y, 0))
    for q in parts['yoke']:
        out.append(xform(q, yf))
    return out


def xform(q, f):
    o = f((0, 0, 0))
    res = []
    for v in q:
        p = f(v[:3])
        n = f(v[5:8])
        n = norm(tuple(n[i] - o[i] for i in range(3)))
        res.append((p[0], p[1], p[2], v[3], v[4], n[0], n[1], n[2]))
    return res
