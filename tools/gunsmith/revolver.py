"""[gunsmith] Revolver mesh (stainless, 6-inch, ventilated rib, full underlug, walnut target grips), rebuilt as
proper geometry: the original was a box frame with painted-on cylinder flutes.

Fixed by the existing animation / socket code and therefore kept exactly:
  bore axis y 0.0685, muzzle z -0.212 (FieldWeaponSockets.muzzle*), iron sight line y 0.0925 (ironY), optic rail seat
  y 0.087 on the top strap (opticY 0.090 - rail base 0.003), part ids: 0 frame/barrel/grip, 1 yoke + ejector rod
  (swings about the z line x 0, y 0.016), 3 cylinder (axis y 0.057, z -0.0462..0.002; spins and swings), 5 hammer
  (pivot y 0.078 z 0.078), 6 trigger (pivot y 0.026 z 0.026); grip hand socket y -0.092 z 0.068.
"""
import numpy as np
import gunkit as G
from gunkit import Part

STEEL = 0xF2F2F2       # bright (brushed stainless) cell tint
STEEL_D = 0xC8C8CA
DARK = 0x262628
WOOD = 0xE0D0C0
RED = 0xD03018

BORE_Y = 0.0685
CYL_Y = 0.057
CYL_R = 0.0186
CYL_Z0, CYL_Z1 = -0.0462, 0.002
TOP = 0.087           # top strap / rib top (optic rail seat)
SIGHT = 0.0925
MUZZLE = -0.212
FRAME_FRONT = -0.0535
FX = 0.0128           # frame half width


def seg(lod, close, field, distant):
    return {'close': close, 'field': field, 'distant': distant}[lod]


def barrel(p, lod):
    s = seg(lod, 40, 28, 14)
    side = G.side_frame()
    # barrel tube with crowned muzzle and bore
    p.lathe([(MUZZLE + 0.0012, 0.0), (MUZZLE + 0.0012, 0.0046), (MUZZLE, 0.0052), (MUZZLE, 0.0088), (MUZZLE + 0.0012, 0.0097),
             (FRAME_FRONT + 0.002, 0.0097)], G.BRIGHT, STEEL, seg=s, origin=(0, BORE_Y, 0), ref=(1, 0, 0))
    p.lathe([(MUZZLE + 0.0013, 0.0), (MUZZLE + 0.0013, 0.0046), (MUZZLE + 0.012, 0.0046)], G.POLY, DARK, seg=s, origin=(0, BORE_Y, 0),
            ref=(1, 0, 0))
    # full-length underlug with the ejector-rod shroud, rounded front
    lug = [(MUZZLE + 0.0005, 0.0485), (MUZZLE + 0.0005, 0.0605), (FRAME_FRONT + 0.002, 0.0605), (FRAME_FRONT + 0.002, 0.0485),
           (FRAME_FRONT - 0.004, 0.0445), (MUZZLE + 0.006, 0.0445)]
    p.extrude_round(lug, -0.0084, 0.0084, 0.0026, G.BRIGHT, STEEL, frame=side, steps=seg(lod, 3, 2, 1))
    # ejector rod end visible in the slot at the rear of the shroud (fixed part of the frame look)
    # ventilated rib: top plate + posts with dark vents between them
    p.extrude_round([(MUZZLE + 0.0005, TOP - 0.0042), (FRAME_FRONT + 0.002, TOP - 0.0042), (FRAME_FRONT + 0.002, TOP), (MUZZLE + 0.002, TOP)],
                    -0.0048, 0.0048, 0.0012, G.BRIGHT, STEEL, frame=side, steps=seg(lod, 2, 2, 1))
    posts = np.linspace(MUZZLE + 0.0085, FRAME_FRONT - 0.0065, 6)
    for i, z in enumerate(posts):
        w = 0.010 if i in (0, len(posts) - 1) else 0.006
        p.extrude_round([(z - w / 2, BORE_Y + 0.006), (z + w / 2, BORE_Y + 0.006), (z + w / 2, TOP - 0.0035), (z - w / 2, TOP - 0.0035)],
                        -0.0034, 0.0034, 0.0008, G.BRIGHT, STEEL_D, frame=side, steps=1)
    for a, b in zip(posts[:-1], posts[1:]):
        p.quad((-0.0001, BORE_Y + 0.0098, a + 0.004), (0.0001, BORE_Y + 0.0098, a + 0.004), (0.0001, TOP - 0.0044, b - 0.004),
               (-0.0001, TOP - 0.0044, b - 0.004), G.POLY, DARK, n=(1, 0, 0))
    # front sight: ramp base on the rib and blade with red insert, top at the sight line
    fz = MUZZLE + 0.016
    p.extrude_round([(fz - 0.014, TOP - 0.0005), (fz + 0.002, TOP - 0.0005), (fz + 0.002, SIGHT), (fz - 0.0015, SIGHT), (fz - 0.010, TOP + 0.0018)],
                    -0.0016, 0.0016, 0.0004, G.BRIGHT, STEEL, frame=side, steps=1)
    if lod != 'distant':
        p.extrude_round([(fz - 0.0042, SIGHT - 0.0042), (fz - 0.0012, SIGHT - 0.0042), (fz - 0.0012, SIGHT - 0.0008), (fz - 0.0030, SIGHT - 0.0008)],
                        -0.0017, 0.0017, 0.0002, G.ORANGE, RED, frame=side, steps=1)


def frame(p, lod):
    side = G.side_frame()
    st = seg(lod, 3, 2, 1)
    # front post of the frame (barrel shank), top strap, bottom strap and recoil shield around the cylinder window
    p.extrude_round([(FRAME_FRONT, 0.034), (CYL_Z0 - 0.0006, 0.034), (CYL_Z0 - 0.0006, TOP), (FRAME_FRONT, TOP)], -FX, FX, 0.0022, G.BRIGHT, STEEL,
                    frame=side, steps=st)
    p.extrude_round([(FRAME_FRONT, CYL_Y + CYL_R + 0.0012), (0.016, CYL_Y + CYL_R + 0.0012), (0.016, TOP), (FRAME_FRONT, TOP)], -0.0108, 0.0108,
                    0.0024, G.BRIGHT, STEEL, frame=side, steps=st)
    p.extrude_round([(FRAME_FRONT, 0.026), (0.016, 0.026), (0.016, CYL_Y - CYL_R - 0.0012), (FRAME_FRONT, CYL_Y - CYL_R - 0.0012)], -0.0118, 0.0118,
                    0.0022, G.BRIGHT, STEEL, frame=side, steps=st)
    # main frame behind the cylinder: recoil shield, hammer housing, round-butt grip frame, trigger guard root
    body = [(CYL_Z1 + 0.0006, 0.026), (CYL_Z1 + 0.0006, TOP), (0.046, TOP), (0.060, 0.0865), (0.071, 0.0838), (0.0835, 0.0785),
            (0.0905, 0.0705), (0.0925, 0.061), (0.0895, 0.048), (0.0835, 0.040), (0.074, 0.036), (0.060, 0.034), (0.049, 0.026),
            (0.040, 0.022), (0.022, 0.021)]
    p.extrude_round(body, -FX, FX, 0.0026, G.BRIGHT, STEEL, frame=side, steps=st, smooth_deg=40)
    # recoil shield: round boss behind the cylinder covering the cartridge heads
    p.lathe([(CYL_Z1 + 0.0004, 0.0), (CYL_Z1 + 0.0004, CYL_R - 0.0012), (CYL_Z1 + 0.0016, CYL_R - 0.0002), (CYL_Z1 + 0.0055, CYL_R - 0.0002),
             (CYL_Z1 + 0.011, FX)], G.BRIGHT, STEEL, seg=seg(lod, 40, 28, 14), origin=(0, CYL_Y, 0), ref=(1, 0, 0))
    # hammer slot (dark) in the top of the frame behind the rear sight
    p.box((-0.0036, 0.0815, 0.0465), (0.0036, TOP + 0.0001, 0.0605), G.POLY, DARK)
    # trigger guard: swept rounded bar from the front of the frame round the trigger back into the grip frame
    guard = G.smooth_path([(0, 0.027, -0.003), (0, 0.016, -0.0045), (0, 0.004, 0.0), (0, -0.004, 0.011), (0, -0.0062, 0.026),
                           (0, -0.003, 0.038), (0, 0.006, 0.046), (0, 0.018, 0.050), (0, 0.030, 0.051)], 4 if lod != 'distant' else 1)
    k = seg(lod, 10, 8, 6)
    sec = [(0.0052 * np.cos(2 * np.pi * i / k) * 1.0, 0.0024 * np.sin(2 * np.pi * i / k)) for i in range(k)]
    p.sweep(guard, sec, G.BRIGHT, STEEL)
    # adjustable rear sight on the top strap: base, blade with a square notch (shoulders at the sight line)
    rz0, rz1 = 0.024, 0.044
    p.extrude_round([(rz0, TOP - 0.0004), (rz1, TOP - 0.0004), (rz1, TOP + 0.0032), (rz0 + 0.004, TOP + 0.0032)], -0.0055, 0.0055, 0.0008, G.POLY, DARK,
                    frame=side, steps=1)
    for sx in (-1, 1):
        x0, x1 = (0.0014, 0.0062) if sx > 0 else (-0.0062, -0.0014)
        p.extrude_round([(rz0, TOP + 0.003), (rz0 + 0.004, TOP + 0.003), (rz0 + 0.004, SIGHT), (rz0, SIGHT)], x0, x1, 0.0003, G.POLY, DARK, frame=side,
                        steps=1)
    p.extrude_round([(rz0, TOP + 0.003), (rz0 + 0.004, TOP + 0.003), (rz0 + 0.004, SIGHT - 0.0032), (rz0, SIGHT - 0.0032)], -0.0015, 0.0015, 0.0002,
                    G.POLY, DARK, frame=side, steps=1)
    if lod != 'distant':
        # windage screw on the right of the sight, elevation screw on top
        p.lathe([(0, 0.0012), (0.0012, 0.0012), (0.0012, 0)], G.BRIGHT, STEEL_D, seg=10, origin=(0.0055, TOP + 0.0016, rz0 + 0.012), axis=(1, 0, 0))
        # cylinder release latch (left), side-plate screws (right), hammer-block pin
        p.extrude_round([(0.016, 0.060), (0.030, 0.060), (0.031, 0.066), (0.017, 0.067)], -FX - 0.0022, -FX + 0.0005, 0.0006, G.BRIGHT, STEEL_D,
                        frame=side, steps=1)
        for z, y in ((0.032, 0.052), (0.056, 0.070), (0.072, 0.050)):
            p.lathe([(0, 0.0021), (0.0004, 0.0021), (0.0006, 0.0014), (0.0006, 0)], G.BRIGHT, STEEL_D, seg=12, origin=(FX - 0.0001, y, z), axis=(1, 0, 0))
            p.quad((FX + 0.00062, y - 0.0003, z - 0.0018), (FX + 0.00062, y - 0.0003, z + 0.0018), (FX + 0.00062, y + 0.0003, z + 0.0018),
                   (FX + 0.00062, y + 0.0003, z - 0.0018), G.POLY, DARK, n=(1, 0, 0))
        # thumb rest / cylinder window shadow: the frame interior behind the cylinder
    p.box((-0.0118, CYL_Y - 0.016, CYL_Z0 - 0.001), (0.0118, CYL_Y + 0.016, CYL_Z0 - 0.0004), G.POLY, DARK)


def grip(p, lod):
    """Walnut target grips: lofted round-butt shape with a palm swell and checkered panels, steel medallions."""
    k = seg(lod, 28, 18, 10)
    m = seg(lod, 12, 8, 5)
    secs = []
    # grip runs from just under the frame (y 0.038) down to the butt (y -0.064), raked back
    for i in range(m + 1):
        t = i / m
        y = 0.041 - t * 0.106
        zc = 0.064 + 0.014 * t                                          # raked back ~8 deg
        depth = 0.0150 + 0.0040 * np.sin(np.pi * min(1.0, t * 1.15))   # front-back half extent (palm swell)
        width = 0.0118 + 0.0030 * np.sin(np.pi * min(1.0, t * 1.1))    # side half extent
        if t > 0.9:
            f = (t - 0.9) / 0.1
            depth *= 1 - 0.16 * f * f
            width *= 1 - 0.22 * f * f
        ring = []
        for j in range(k):
            a = 2 * np.pi * j / k
            c, s_ = np.cos(a), np.sin(a)
            # superellipse in (x, z): fuller at the sides
            x = width * np.sign(c) * abs(c) ** (2 / 2.6)
            z = depth * np.sign(s_) * abs(s_) ** (2 / 2.6)
            # finger groove relief on the front strap (z < 0 side), three soft grooves
            if s_ < -0.3 and 0.15 < t < 0.85:
                z += 0.0012 * np.sin((t - 0.15) / 0.7 * 3 * np.pi) ** 2
            ring.append((x, y, zc + z))
        secs.append(ring)
    p.loft(secs, G.CHECKER, WOOD, uv_along=0.11)
    if lod != 'distant':
        for sx in (-1, 1):
            p.lathe([(0, 0.0034), (0.0006, 0.0034), (0.0009, 0.0026), (0.0009, 0)], G.BRIGHT, STEEL_D, seg=16,
                    origin=(sx * 0.0145, 0.004, 0.072), axis=(sx, 0, 0))


def cylinder(lod):
    p = Part(3)
    heads = Part(9)
    k = seg(lod, 72, 48, 24)
    nz = seg(lod, 14, 10, 4)
    z0, z1 = CYL_Z0, CYL_Z1
    zs = np.linspace(z0, z1, nz + 1)
    fl0, fl1 = z0 + 0.0075, z1 - 0.0105        # flutes stop short of both faces
    rows = []
    for z in zs:
        ring = []
        for j in range(k):
            a = 2 * np.pi * j / k
            r = CYL_R
            # chamfer at both ends
            r -= max(0.0, 0.0012 - (z - z0)) * 0.9 + max(0.0, 0.0012 - (z1 - z)) * 0.9
            if lod != 'distant' and fl0 <= z <= fl1:
                # six flutes between the chambers (chambers at 90 deg + n*60): flute centres at 120 + n*60
                d = (np.degrees(a) - 120.0) % 60.0
                d = min(d, 60.0 - d)
                ez = min(1.0, (z - fl0) / 0.004, (fl1 - z) / 0.004)
                if d < 13.0:
                    r -= 0.0034 * np.cos(np.radians(d / 13.0 * 90)) ** 1.4 * np.sqrt(max(0.0, ez))
            ring.append((np.cos(a) * r, CYL_Y + np.sin(a) * r, z))
        rows.append(ring)
    p.loft(rows, G.BRIGHT, STEEL, uv_along=0.06, caps=False)
    # faces with the chamber mouths (front) and cartridge heads (rear)
    for zf, d in ((z0, -1), (z1, 1)):
        p.lathe([(0, 0), (0, CYL_R - 0.0012)] if d < 0 else [(0, CYL_R - 0.0012), (0, 0)], G.BRIGHT, STEEL_D, seg=k // 2,
                origin=(0, CYL_Y, zf), axis=(0, 0, 1), ref=(1, 0, 0))
        for n in range(6):
            a = np.radians(90 + 60 * n)
            cx, cy = np.cos(a) * 0.0115, CYL_Y + np.sin(a) * 0.0115
            if d < 0:
                p.lathe([(-0.0002, 0.0), (-0.0002, 0.0046)], G.POLY, DARK, seg=16, origin=(cx, cy, zf), axis=(0, 0, 1), ref=(1, 0, 0))
            else:
                # [gear21] the empty chamber (seen while the cylinder is out for a reload)...
                p.lathe([(0.0001, 0.0047), (0.0001, 0.0)], G.POLY, DARK, seg=16, origin=(cx, cy, zf), axis=(0, 0, 1), ref=(1, 0, 0))
                # ...and the cartridge heads of a loaded cylinder on their own part (9), hidden while it is reloaded
                heads.lathe([(0.0003, 0.0047), (0.0003, 0.0)], G.BRASS, 0xFFFFFF, seg=16, origin=(cx, cy, zf), axis=(0, 0, 1), ref=(1, 0, 0))
                heads.lathe([(0.0005, 0.0016), (0.0005, 0.0)], G.BRIGHT, 0xD8C8A8, seg=10, origin=(cx, cy, zf), axis=(0, 0, 1), ref=(1, 0, 0))
    # ratchet star centre (rear) and the centre pin hole (front)
    p.lathe([(z1 + 0.0004, 0.0042), (z1 + 0.0004, 0.0)], G.BRIGHT, STEEL_D, seg=6, origin=(0, CYL_Y, 0), axis=(0, 0, 1), ref=(1, 0, 0))
    return p, heads


def yoke(lod):
    """Yoke arm in front of the cylinder down to its pivot, ejector rod under the barrel (inside the lug shroud)."""
    p = Part(1)
    side = G.side_frame()
    p.extrude_round([(CYL_Z0 - 0.0058, 0.0275), (CYL_Z0 - 0.0008, 0.0275), (CYL_Z0 - 0.0008, CYL_Y + 0.004), (CYL_Z0 - 0.0058, CYL_Y + 0.004)],
                    -0.0062, 0.0062, 0.0012, G.BRIGHT, STEEL, frame=side, steps=1)
    p.lathe([(-0.135, 0.0), (-0.135, 0.0033), (CYL_Z0 - 0.004, 0.0033)], G.BRIGHT, STEEL, seg=seg(lod, 16, 12, 6), origin=(0, CYL_Y, 0), ref=(1, 0, 0))
    # knurled rod head just inside the shroud mouth
    p.lathe([(-0.1355, 0.0), (-0.1355, 0.0036), (-0.128, 0.0036)], G.BRIGHT, STEEL_D, seg=seg(lod, 16, 12, 6), origin=(0, CYL_Y, 0), ref=(1, 0, 0),
            knurl=None if lod == 'distant' else (16, 0.0003, -0.135, -0.1285))
    return p


def hammer(lod):
    p = Part(5)
    side = G.side_frame()
    # body pivots at (z 0.078, y 0.078); nose forward at the top strap, spur up and back with a checkered top
    prof = [(0.0705, 0.0735), (0.0790, 0.0718), (0.0845, 0.0760), (0.0905, 0.0905), (0.0960, 0.0985), (0.0955, 0.1006), (0.0890, 0.1004),
            (0.0815, 0.0952), (0.0725, 0.0905), (0.0660, 0.0885), (0.0655, 0.0850), (0.0690, 0.0800)]
    p.extrude_round(prof, -0.0033, 0.0033, 0.0008, G.BRIGHT, STEEL, frame=side, steps=seg(lod, 2, 2, 1), smooth_deg=45)
    if lod != 'distant':
        for i in range(5):
            z = 0.0895 + i * 0.0013
            y = 0.0994 + 0.0002 * i
            p.quad((-0.0034, y, z), (0.0034, y, z), (0.0034, y + 0.0004, z + 0.0005), (-0.0034, y + 0.0004, z + 0.0005), G.POLY, DARK, n=(0, 1, 0))
    return p


def trigger(lod):
    p = Part(6)
    k = seg(lod, 10, 8, 6)
    path = G.smooth_path([(0, 0.030, 0.0215), (0, 0.022, 0.0205), (0, 0.014, 0.0215), (0, 0.007, 0.0245), (0, 0.0025, 0.0290)], 3)
    sec = [(0.0030 * np.cos(2 * np.pi * i / k), 0.0016 * np.sin(2 * np.pi * i / k)) for i in range(k)]
    p.sweep(path, sec, G.BRIGHT, STEEL)
    return p


def build(lod):
    p0 = Part(0)
    barrel(p0, lod)
    frame(p0, lod)
    grip(p0, lod)
    cyl, heads = cylinder(lod)
    return [p0, yoke(lod), cyl, hammer(lod), trigger(lod), heads]


if __name__ == '__main__':
    import sys
    import fheq
    for lod in ('close', 'field', 'distant'):
        parts = build(lod)
        print(lod, [(p.id, p.tris()) for p in parts], sum(p.tris() for p in parts))
        if len(sys.argv) > 1:
            fheq.write(sys.argv[1] + '/revolver_' + lod + '.fheq', [p.to_fheq() for p in parts])
