"""[gunsmith] Riflescope meshes (six / eight / twelve power, thermal, 4-12x field optic), rebuilt as true lathes.

Kept from the original meshes (so ADS, glass, mounting and the Ridgeline placement are unchanged):
  optical axis y = 0.146 (field optic: 0.140), ring centres z -0.098 / +0.081 (field optic: -0.105 / +0.073),
  ring feet at y 0.096 (field optic: 0.100), eyepiece end z 0.181 (field optic 0.192), objective front z and lens
  radii as used by FieldMountedOptic.glass (objective glass at z_front + 0.008, ocular glass at 0.175 / r 0.018).
Changed: real proportions - 30 mm main tube (was ~36 mm), objective bells sized to their lens (OD ~ lens + 7 mm),
proper eyepiece with fast-focus ring and rubber eye guard, knurled magnification ring with a throw lever, capped and
knurled turrets with index lines, side-focus knob on the 8x / 12x, split rings with cross-bolt clamps.
"""
import numpy as np
import gunkit as G
from gunkit import Part

BODY = 0xE2E2E6      # anodized cell tint -> satin black
DARK = 0x9A9A9E
RING = 0xC4C4C8
WHITE_MARK = 0xD8DCC8
RUBBER_T = 0xFFFFFF
TAN_T = 0xE8DCC4


def segs(lod):
    return {'close': 48, 'field': 32, 'distant': 16}[lod]


def knurl(lod, count, depth, h0, h1):
    return (count, depth, h0, h1) if lod != 'distant' else None


def ring_mount(p, z, A, feet_y, tube_r, lod, width=0.0125):
    """Split ring around the tube with a cap seam, two cap screws per side and a base clamping the rail."""
    s = segs(lod)
    ro = tube_r + 0.0045
    p.lathe([(z - width / 2, tube_r), (z - width / 2, ro - 0.001), (z - width / 2 + 0.001, ro), (z + width / 2 - 0.001, ro),
             (z + width / 2, ro - 0.001), (z + width / 2, tube_r)], G.ANOD, RING, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    # cap seam (thin dark band at axis height on both sides) and cap screws
    for sx in (-1, 1):
        p.box((sx * ro - 0.0006 if sx > 0 else -ro - 0.0006, A - 0.0007, z - width / 2 - 0.0002),
              (ro + 0.0006 if sx > 0 else -ro + 0.0006, A + 0.0007, z + width / 2 + 0.0002), G.POLY, 0x303030)
        if lod != 'distant':
            for dz in (-0.0035, 0.0035):
                p.lathe([(0, 0), (0, 0.0016), (0.0012, 0.0016), (0.0012, 0)], G.BRIGHT, 0x6E6E70, seg=10,
                        origin=(sx * (ro - 0.0002), A + 0.0035, z + dz), axis=(sx, 0, 0), ref=(0, 1, 0))
    # base: from under the ring down to the rail, with a recoil lug block and the clamp jaw / cross-bolt nut (right)
    by0 = feet_y
    by1 = A - tube_r - 0.002
    p.box((-0.0115, by0, z - width / 2 - 0.0015), (0.0115, by1, z + width / 2 + 0.0015), G.ANOD, RING, r=0.0018,
          frame=None)
    p.box((-0.0135, by0, z - width / 2 - 0.0015), (0.0135, by0 + 0.0055, z + width / 2 + 0.0015), G.ANOD, RING, r=0.001)
    # cross bolt nut on the right
    p.lathe([(0, 0), (0, 0.0042), (0.003, 0.0042), (0.003, 0.0032), (0.0042, 0.0032), (0.0042, 0)], G.ANOD, DARK, seg=6 if lod == 'distant' else 6,
            origin=(0.0135, by0 + 0.0030, z), axis=(1, 0, 0), ref=(0, 1, 0))


def turret(p, A, z, tube_r, lod, height, radius, axis=(0, 1, 0), marks=True):
    """Capped turret: base collar, knurled cap with a flat top and an index line."""
    s = segs(lod)
    ax = np.asarray(axis, float)
    ref = (0, 0, 1) if abs(ax[2]) < 0.9 else (1, 0, 0)
    origin = np.array([0, A, z])
    base_h = tube_r - 0.002
    p.lathe([(base_h, radius + 0.0015), (tube_r + 0.004, radius + 0.0015), (tube_r + 0.0045, radius + 0.0005),
             (tube_r + 0.0045, radius)], G.ANOD, BODY, seg=s, origin=origin, axis=axis, ref=ref)
    cap0 = tube_r + 0.0045
    cap1 = tube_r + height
    p.lathe([(cap0, radius), (cap1 - 0.0015, radius), (cap1, radius - 0.0015), (cap1, 0)], G.ANOD, BODY, seg=s * 2 if lod == 'close' else s,
            origin=origin, axis=axis, ref=ref, knurl=knurl(lod, 40, 0.0006, cap0 + 0.0015, cap1 - 0.0025))
    if marks and lod != 'distant':
        # index line on the collar facing the shooter and a white zero mark on the cap
        p.box((-0.0004, 0, 0), (0.0004, 0.0035, 0.0006), G.WHITE, WHITE_MARK,
              frame=_frame(origin + ax * (tube_r + 0.001), ax, np.array([0, 0, 1.0]) if abs(ax[2]) < 0.9 else np.array([1.0, 0, 0]), radius + 0.0016))


def _frame(o, up, out, dist):
    """Frame for a small mark: local y along up, local z along out, placed dist out from o."""
    up = G.unit(up)
    out = G.unit(out - up * np.dot(out, up))
    x = np.cross(up, out)
    m = np.eye(4)
    m[:3, 0], m[:3, 1], m[:3, 2] = x, up, out
    m[:3, 3] = o + out * dist
    return m


def riflescope(name, lod):
    """Variable power scopes: six (3-9x40), eight (4-16x50), twelve (5-25x56)."""
    A = 0.146
    feet = 0.096
    TR = 0.015
    front = {'six_power_scope': -0.22, 'eight_power_scope': -0.265, 'twelve_power_scope': -0.305}[name]
    lens = {'six_power_scope': 0.023, 'eight_power_scope': 0.028, 'twelve_power_scope': 0.033}[name]
    bell = lens + 0.0035
    s = segs(lod)
    p = Part(0)
    zf = front
    shade = 0.012 if name != 'six_power_scope' else 0.006          # sunshade lip length
    taper0 = -0.135 if name == 'six_power_scope' else (-0.14 if name == 'eight_power_scope' else -0.15)
    bell_len = {'six_power_scope': 0.045, 'eight_power_scope': 0.06, 'twelve_power_scope': 0.075}[name]
    zb = zf + bell_len
    # main body lathe: recessed objective -> bell -> taper -> tube -> power ring seat -> eyepiece
    prof = [
        (zf + 0.0095, 0.0), (zf + 0.0095, lens - 0.0005),                     # dark backing behind the objective glass
        (zf + 0.002, lens - 0.0005), (zf + 0.002, lens + 0.0006),             # inner wall up to the lip
        (zf, lens + 0.0012), (zf, bell - 0.0008), (zf + 0.0008, bell),         # front lip
        (zf + shade, bell), (zf + shade + 0.0006, bell - 0.0006), (zf + shade + 0.0012, bell),   # sunshade seam
        (zb - 0.004, bell), (zb + 0.002, bell - 0.0025),
        (taper0, TR + 0.0022), (taper0 + 0.006, TR),                          # bell taper into the tube
        (0.088, TR), (0.089, TR + 0.0025),                                    # magnification ring seat
    ]
    tints = None
    p.lathe(prof, G.ANOD, BODY, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    # magnification ring (knurled) with throw lever
    p.lathe([(0.0895, TR + 0.002), (0.0905, TR + 0.0042), (0.1135, TR + 0.0042), (0.1145, TR + 0.002)], G.ANOD, BODY, seg=s * 2 if lod == 'close' else s,
            origin=(0, A, 0), ref=(1, 0, 0), knurl=knurl(lod, 72, 0.0007, 0.092, 0.112))
    p.box((-0.0018, A + TR + 0.004, 0.096), (0.0018, A + TR + 0.0105, 0.108), G.ANOD, BODY, r=0.0008)
    # eyepiece: flare, fast-focus ring, rubber eye guard; recessed ocular glass at 0.175
    eye_r = 0.0215
    p.lathe([(0.1145, TR + 0.002), (0.116, TR + 0.0025), (0.132, eye_r - 0.0015), (0.142, eye_r), (0.147, eye_r)], G.ANOD, BODY, seg=s, origin=(0, A, 0),
            ref=(1, 0, 0))
    p.lathe([(0.147, eye_r), (0.1475, eye_r + 0.0006), (0.166, eye_r + 0.0006), (0.1665, eye_r)], G.ANOD, BODY, seg=s * 2 if lod == 'close' else s,
            origin=(0, A, 0), ref=(1, 0, 0), knurl=knurl(lod, 60, 0.0005, 0.149, 0.164))
    p.lathe([(0.1665, eye_r), (0.1665, eye_r + 0.0004), (0.1795, eye_r + 0.0004), (0.181, eye_r - 0.0008), (0.181, 0.0188),
             (0.1745, 0.0186), (0.1745, 0.0)], G.RUBBER, RUBBER_T, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    # turret saddle (rounded block around the tube), elevation on top, windage on the right, focus knob (8x/12x) left
    zs = -0.008
    p.extrude(G.superellipse(0, A, TR + 0.0042, TR + 0.0042, n=3.2, k=32 if lod != 'distant' else 16), zs - 0.019, zs + 0.019, G.ANOD, BODY, smooth_deg=40)
    turret(p, A, zs, TR + 0.0035, lod, 0.0165 if name != 'six_power_scope' else 0.0135, 0.0105, axis=(0, 1, 0))
    turret(p, A, zs, TR + 0.0035, lod, 0.0125, 0.0095, axis=(1, 0, 0))
    if name != 'six_power_scope':
        turret(p, A, zs, TR + 0.0035, lod, 0.0085, 0.0135, axis=(-1, 0, 0), marks=False)
    for zr in (-0.098, 0.081):
        ring_mount(p, zr, A, feet, TR, lod)
    return [p]


def field_optic(lod):
    """The 4-12x Field Optic: a slim hunting scope (25.4 mm tube), capped turrets, classic gloss-black finish."""
    A = 0.140
    feet = 0.100
    TR = 0.0127
    s = segs(lod)
    p = Part(0)
    # glass (FieldMountedOptic.draw(boolean)): objective at z -0.263 r 0.0255, ocular at z 0.184 r 0.0185
    zf = -0.272
    lens = 0.0255
    bell = lens + 0.0035
    p.lathe([(-0.2615, 0.0), (-0.2615, lens + 0.0003), (zf + 0.002, lens + 0.0003), (zf + 0.002, lens + 0.0009), (zf, lens + 0.0014),
             (zf, bell - 0.0008), (zf + 0.0008, bell), (zf + 0.05, bell), (zf + 0.058, bell - 0.0025), (-0.17, TR + 0.0018), (-0.164, TR),
             (0.092, TR), (0.093, TR + 0.0022)], G.ANOD, BODY, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    p.lathe([(0.0935, TR + 0.002), (0.0945, TR + 0.0042), (0.1185, TR + 0.0042), (0.1195, TR + 0.002)], G.ANOD, BODY, seg=s * 2 if lod == 'close' else s,
            origin=(0, A, 0), ref=(1, 0, 0), knurl=knurl(lod, 64, 0.0006, 0.097, 0.116))
    eye_r = 0.0205
    p.lathe([(0.1195, TR + 0.002), (0.121, TR + 0.0025), (0.14, eye_r - 0.0012), (0.152, eye_r), (0.186, eye_r), (0.192, eye_r - 0.0008),
             (0.192, 0.0188), (0.1828, 0.0188), (0.1828, 0.0)], G.ANOD, BODY, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    # note: ocular glass is drawn at z 0.184 (r 0.021) - keep the eyepiece open in front of it
    zs = -0.016
    p.extrude(G.superellipse(0, A, TR + 0.0038, TR + 0.0038, n=3.0, k=28 if lod != 'distant' else 14), zs - 0.016, zs + 0.016, G.ANOD, BODY)
    turret(p, A, zs, TR + 0.003, lod, 0.0125, 0.0085, axis=(0, 1, 0), marks=False)
    turret(p, A, zs, TR + 0.003, lod, 0.0105, 0.0085, axis=(1, 0, 0), marks=False)
    for zr in (-0.105, 0.073):
        ring_mount(p, zr, A, feet, TR, lod, width=0.011)
    return [p]


def thermal(lod):
    """Thermal scope: 30 mm tube, germanium objective housing with a focus ring, tan control pad with buttons,
    battery cap, rubber eye guard."""
    A = 0.146
    feet = 0.096
    TR = 0.015
    s = segs(lod)
    p = Part(0)
    zf = -0.185
    lens = 0.027
    hous = lens + 0.0045
    p.lathe([(zf + 0.0095, 0.0), (zf + 0.0095, lens - 0.0005), (zf + 0.002, lens - 0.0005), (zf + 0.002, lens + 0.0006), (zf, lens + 0.0012),
             (zf, hous - 0.001), (zf + 0.001, hous), (zf + 0.012, hous), (zf + 0.012, hous - 0.0012), (zf + 0.034, hous - 0.0012),
             (zf + 0.034, hous), (zf + 0.05, hous), (zf + 0.056, hous - 0.004), (-0.118, TR + 0.006), (-0.110, TR + 0.006),
             (-0.106, TR), (0.088, TR), (0.089, TR + 0.0025)], G.ANOD, BODY, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    # focus ring (rubberised, knurled) on the objective housing
    p.lathe([(zf + 0.0125, hous - 0.0012), (zf + 0.0125, hous + 0.0008), (zf + 0.0335, hous + 0.0008), (zf + 0.0335, hous - 0.0012)], G.RUBBER, RUBBER_T,
            seg=s * 2 if lod == 'close' else s, origin=(0, A, 0), ref=(1, 0, 0), knurl=knurl(lod, 48, 0.0007, zf + 0.0135, zf + 0.0325))
    p.lathe([(0.0895, TR + 0.002), (0.0905, TR + 0.0042), (0.1135, TR + 0.0042), (0.1145, TR + 0.002)], G.ANOD, BODY, seg=s * 2 if lod == 'close' else s,
            origin=(0, A, 0), ref=(1, 0, 0), knurl=knurl(lod, 72, 0.0007, 0.092, 0.112))
    eye_r = 0.0215
    p.lathe([(0.1145, TR + 0.002), (0.116, TR + 0.0025), (0.132, eye_r - 0.0015), (0.142, eye_r), (0.1665, eye_r)], G.ANOD, BODY, seg=s, origin=(0, A, 0),
            ref=(1, 0, 0))
    p.lathe([(0.1665, eye_r), (0.1665, eye_r + 0.0004), (0.1795, eye_r + 0.0004), (0.181, eye_r - 0.0008), (0.181, 0.0188), (0.1745, 0.0186),
             (0.1745, 0.0)], G.RUBBER, RUBBER_T, seg=s, origin=(0, A, 0), ref=(1, 0, 0))
    # electronics housing: rounded block over the tube with the tan control pad and three buttons
    zs = -0.01
    p.extrude(G.superellipse(0, A + 0.002, TR + 0.0065, TR + 0.0075, n=3.4, k=32 if lod != 'distant' else 16), zs - 0.034, zs + 0.03, G.ANOD, BODY)
    p.box((-0.0095, A + TR + 0.008, zs - 0.026), (0.0095, A + TR + 0.0105, zs + 0.022), G.TAN, TAN_T, r=0.002)
    if lod != 'distant':
        for i, dz in enumerate((-0.016, -0.002, 0.012)):
            p.lathe([(0, 0.0036), (0.0018, 0.0036), (0.0026, 0.0028), (0.0026, 0)], G.POLY, 0x2A2A2A, seg=16,
                    origin=(0, A + TR + 0.0105, zs + dz), axis=(0, 1, 0), ref=(0, 0, 1))
    # battery cap (left side), knurled
    p.lathe([(TR + 0.006, 0.0075), (TR + 0.016, 0.0075), (TR + 0.017, 0.0065), (TR + 0.017, 0)], G.ANOD, BODY, seg=s,
            origin=(0, A, zs), axis=(-1, 0, 0), ref=(0, 1, 0), knurl=knurl(lod, 28, 0.0005, TR + 0.007, TR + 0.0155))
    for zr in (-0.098, 0.081):
        ring_mount(p, zr, A, feet, TR, lod)
    return [p]


def build(name, lod):
    if name in ('six_power_scope', 'eight_power_scope', 'twelve_power_scope'):
        return riflescope(name, lod)
    if name == 'thermal_scope':
        return thermal(lod)
    if name == 'fixed_scope':
        return field_optic(lod)
    raise KeyError(name)
