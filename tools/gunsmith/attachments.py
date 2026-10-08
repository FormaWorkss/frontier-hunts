"""[gunsmith] Muzzle devices rebuilt as real lathes (the originals were a plain faceted tube and a mottled block).

Kept: mount face at z +0.012 (threads into the muzzle socket), suppressor front at z -0.170 and brake front at
-0.055 (FieldGunEffects moves the muzzle flash by 0.172 / 0.056), centred on the bore (local origin).
"""
import numpy as np
import gunkit as G
from gunkit import Part

FINISH = 0xDADADE
DARK = 0x18181A


def zl(p, prof, cell, tint, **kw):
    """Lathe around the bore with the profile given as (model z, r) from the mount end (+z) forwards: the lathe runs
    along -z so the profile order keeps its normals outward."""
    kw.setdefault('origin', (0, 0, 0))
    knurl = kw.pop('knurl', None)
    if knurl is not None:
        knurl = (knurl[0], knurl[1], -knurl[3], -knurl[2])
    p.lathe([(-z, r) for z, r in prof], cell, tint, axis=(0, 0, -1), ref=(1, 0, 0), knurl=knurl, **kw)


def seg(lod):
    return {'close': 40, 'field': 28, 'distant': 14}[lod]


def suppressor(lod):
    p = Part(0)
    s = seg(lod)
    R = 0.0215
    prof = [(0.012, 0.0), (0.012, 0.0115), (0.0105, 0.013), (-0.003, 0.013),          # threaded mount (rear)
            (-0.004, 0.0165), (-0.009, R - 0.0012), (-0.0105, R),                     # rear cap shoulder
            (-0.1555, R), (-0.157, R - 0.0012), (-0.1665, R - 0.0018), (-0.170, R - 0.0045),
            (-0.170, 0.0062), (-0.1685, 0.0045), (-0.1685, 0.0)]
    zl(p, prof, G.ANOD, FINISH, seg=s)
    if lod != 'distant':
        # wrench flats / knurl band on the mount, engraved seams at both caps and a dark bore
        zl(p, [(0.0098, 0.0131), (-0.0018, 0.0131)], G.ANOD, FINISH, seg=s * 2, knurl=(24, 0.0006, -0.0018, 0.0098))
        for z in (-0.019, -0.146):
            zl(p, [(z + 0.0006, R + 0.00005), (z - 0.0006, R + 0.00005)], G.POLY, DARK, seg=s)
        zl(p, [(-0.168, 0.0045), (-0.150, 0.0045)], G.POLY, DARK, seg=16)
        # front cap: three shallow flats / notches for the wrench
        for a in (30, 150, 270):
            t = np.radians(a)
            cx, cy = np.cos(t) * (R - 0.0006), np.sin(t) * (R - 0.0006)
            p.lathe([(0, 0.0022), (0.0006, 0.0022), (0.0006, 0)], G.POLY, DARK, seg=10, origin=(cx, cy, -0.161), axis=(np.cos(t), np.sin(t), 0),
                    ref=(0, 0, 1))
    return [p]


def muzzle_brake(lod):
    p = Part(0)
    s = seg(lod)
    # threaded collar and jam nut, then a flat-sided ported body
    zl(p, [(0.012, 0.0), (0.012, 0.0095), (0.0105, 0.0108), (0.004, 0.0108), (0.003, 0.0118), (-0.004, 0.0118), (-0.005, 0.0105)], G.ANOD, FINISH,
       seg=s, knurl=None if lod == 'distant' else (6, 0.0012, -0.004, 0.003))
    sec = G.superellipse(0, 0, 0.0108, 0.0128, n=3.2, k=28 if lod != 'distant' else 14)
    flat = [(min(max(x, -0.0102), 0.0102), y) for x, y in sec]      # flat sides for the ports
    p.extrude_round(flat, -0.055, -0.0045, 0.0018, G.ANOD, FINISH, steps=2 if lod != 'distant' else 1)
    # dark bore in the front face
    zl(p, [(-0.0552, 0.0048), (-0.0552, 0.0)], G.POLY, DARK, seg=16)
    if lod != 'distant':
        for sx in (-1, 1):
            x = sx * 0.01025
            for z0 in (-0.0145, -0.0275, -0.0405):
                p.quad((x, -0.0068, z0), (x, -0.0068, z0 - 0.0085), (x, 0.0068, z0 - 0.0085), (x, 0.0068, z0), G.POLY, DARK, n=(sx, 0, 0))
        # top ports (compensator) - three small slots
        for z0 in (-0.016, -0.029, -0.042):
            p.quad((-0.0035, 0.01285, z0), (0.0035, 0.01285, z0), (0.0035, 0.01285, z0 - 0.005), (-0.0035, 0.01285, z0 - 0.005), G.POLY, DARK,
                   n=(0, 1, 0))
    return [p]


def build(name, lod):
    return {'att_suppressor': suppressor, 'att_muzzle_brake': muzzle_brake}[name](lod)


def foregrip(lod):
    """[guns3] Angled foregrip on its own clamp-on rail section (so it reads as mounted on wooden forends too): a
    ribbed picatinny clamp with two cross bolts, and a swept grip with a finger ramp, stippled flanks and a thumb shelf.
    Envelope of the supplied grip kept: top y 0.012 (against the gun), down to y -0.069, z -0.06 .. 0.058."""
    import gunkit as G
    from gunkit import Part
    q = Part(0)
    side = G.side_frame()
    st = {'close': 3, 'field': 2, 'distant': 1}[lod]
    # clamp: base block with rail ribs on top (top edge is the contact face against the gun)
    q.extrude_round([(-0.050, 0.0), (0.050, 0.0), (0.050, 0.0105), (-0.050, 0.0105)], -0.0128, 0.0128, 0.0018, G.ANOD, 0xD0D0D4, frame=side, steps=st)
    if lod != 'distant':
        for i in range(9):
            z = -0.044 + i * 0.011
            q.box((-0.0132, 0.0045, z - 0.0025), (0.0132, 0.0098, z + 0.0025), G.ANOD, 0xB8B8BC)
        for z in (-0.034, 0.034):
            for sx in (-1, 1):
                q.lathe([(0.0, 0.0034), (0.0014, 0.0034), (0.0020, 0.0024), (0.0020, 0.0)], G.BLUED, 0xD8D8D8, seg=12,
                        origin=(sx * 0.0128, 0.005, z), axis=(sx, 0, 0), ref=(0, 1, 0))
    # grip body: swept angle, finger ramp at the front, sweeping back to the clamp
    body = [(-0.046, 0.0005), (0.050, 0.0005), (0.048, -0.004), (0.010, -0.050), (0.000, -0.064), (-0.012, -0.068), (-0.022, -0.066),
            (-0.026, -0.058), (-0.030, -0.030), (-0.038, -0.012), (-0.046, -0.004)]
    q.extrude_round(body, -0.0112, 0.0112, 0.0042, G.POLY, 0xE0E0E0, frame=side, steps=st, smooth_deg=40)
    if lod != 'distant':
        # stippled flanks
        for sx in (-1, 1):
            x0, x1 = (0.0109, 0.0115) if sx > 0 else (-0.0115, -0.0109)
            q.extrude_round([(-0.024, -0.054), (-0.026, -0.030), (-0.032, -0.014), (0.024, -0.010), (0.004, -0.044), (-0.008, -0.058)], x0, x1, 0.0004,
                            G.STIPPLE, 0x9A9A9A, frame=side, steps=1)
        # finger ramp ridges along the front edge
        for i in range(3):
            y = -0.020 - i * 0.012
            q.box((-0.0095, y - 0.0016, -0.033 + i * 0.002), (0.0095, y + 0.0016, -0.026 + i * 0.002), G.POLY, 0xFFFFFF, r=0.0012)
    return [q]
