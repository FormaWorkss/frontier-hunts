"""[guns3] Detailed rifle magazines for the Assault Rifle (part 3, replaced in the supplied mesh) and the Extended
Magazine attachment (att_rifle_mag_extended, rebuilt whole). Original design (a generic ribbed polymer box magazine).

Fitted to the receiver's magazine opening (x +-0.0124, z -0.062 .. -0.004 at y 0.004 .. 0.03): top y 0.026 inside the
well, front z -0.060, back -0.006; the body curves forward as it goes down. The rifle also gets a flared magazine well
under the receiver (magwell), so the magazine visibly seats in a housing instead of hanging from a flat bottom.

Details: banana curve, rounded body, three grip ribs and a stippled panel on each side, front and rear spines, a
flared floor plate with a pull tab, feed lips and the top cartridge (brass case, copper bullet) - visible when the
magazine is out during a reload.
"""
import numpy as np
import gunkit as G
from gunkit import Part, rounded_rect

TOP = 0.026
BODY_T = 0xBCBCBC      # polymer body
RIB_T = 0xFFFFFF
PLATE_T = 0xB8B8BC
HALF_X = 0.0105


def _centre(y, bottom, curve):
    """z of the magazine's centre line at height y (curving forward = -z as it goes down)."""
    s = (TOP - y) / (TOP - bottom)
    return -0.033 - curve * s * s


def _depth(y, bottom):
    s = (TOP - y) / (TOP - bottom)
    return 0.0272 + 0.0035 * s   # half depth (front-back): fits the well at the top, a touch fuller below


def build(lod, extended):
    bottom = -0.166 if extended else -0.108
    curve = 0.034 if extended else 0.02
    k = {'close': 6, 'field': 3, 'distant': 1}[lod]
    stations = {'close': 14, 'field': 8, 'distant': 4}[lod]
    q = Part(3)
    # body: rounded-rect sections lofted down the curve
    secs = []
    for i in range(stations + 1):
        y = TOP - (TOP - bottom) * i / stations
        zc, d = _centre(y, bottom, curve), _depth(y, bottom)
        ring = rounded_rect(-HALF_X, zc - d, HALF_X, zc + d, 0.0035, seg=max(1, k // 2))
        secs.append([(x, y, z) for x, z in ring])
    q.loft(secs[::-1], G.POLY, BODY_T, uv_along=0.12)
    if lod != 'distant':
        # front and rear spines
        for front in (True, False):
            pts = []
            for i in range(stations + 1):
                y = TOP - 0.012 - (TOP - 0.012 - (bottom + 0.004)) * i / stations
                zc, d = _centre(y, bottom, curve), _depth(y, bottom)
                pts.append((0.0, y, zc - d - 0.0012 if front else zc + d + 0.0012))
            q.sweep(pts, [(-0.0035, -0.0012), (0.0035, -0.0012), (0.0035, 0.0012), (-0.0035, 0.0012)], G.POLY, RIB_T)
        # grip ribs and a stippled panel on each side
        n_ribs = 4 if extended else 3
        for r in range(n_ribs):
            y = bottom + 0.014 + r * 0.011
            zc, d = _centre(y, bottom, curve), _depth(y, bottom)
            for sx in (-1, 1):
                x0, x1 = (HALF_X - 0.0003, HALF_X + 0.0009) if sx > 0 else (-HALF_X - 0.0009, -HALF_X + 0.0003)
                q.box((x0, y - 0.0016, zc - d * 0.82), (x1, y + 0.0016, zc + d * 0.82), G.POLY, RIB_T)
        y0, y1 = bottom + 0.014 + n_ribs * 0.011, TOP - 0.03
        if y1 > y0 + 0.01:
            ym = (y0 + y1) / 2
            zc, d = _centre(ym, bottom, curve), _depth(ym, bottom)
            for sx in (-1, 1):
                x0, x1 = (HALF_X - 0.0002, HALF_X + 0.0004) if sx > 0 else (-HALF_X - 0.0004, -HALF_X + 0.0002)
                q.box((x0, y0, zc - d * 0.7), (x1, y1, zc + d * 0.7), G.STIPPLE, 0x8A8A8A)
    # floor plate: wider and longer than the body, with a pull tab at the front
    zc, d = _centre(bottom, bottom, curve), _depth(bottom, bottom)
    q.box((-0.0123, bottom - 0.0075, zc - d - 0.004), (0.0123, bottom + 0.0005, zc + d + 0.0035), G.RUBBER, PLATE_T, r=0.0025)
    if lod != 'distant':
        q.box((-0.006, bottom - 0.0095, zc - d - 0.006), (0.006, bottom - 0.004, zc - d - 0.001), G.RUBBER, PLATE_T)
        # feed lips and the top round
        zc, d = _centre(TOP, bottom, curve), _depth(TOP, bottom)
        for sx in (-1, 1):
            x0, x1 = (0.0045, HALF_X) if sx > 0 else (-HALF_X, -0.0045)
            q.box((x0, TOP - 0.0005, zc - d + 0.004), (x1, TOP + 0.0018, zc + d - 0.004), G.POLY, BODY_T)
        seg = 14 if lod == 'close' else 8
        k8 = 0.86  # the round lies inside the lips, tip just short of the front wall
        case = [(t * k8, r) for t, r in [(0.0, 0.0), (0.0, 0.0047), (0.0005, 0.0048), (0.036, 0.0047), (0.039, 0.0031), (0.044, 0.0028), (0.044, 0.0)]]
        bullet = [(t * k8, r) for t, r in [(0.044, 0.0), (0.044, 0.0028), (0.050, 0.0026), (0.055, 0.0017), (0.0585, 0.0004), (0.059, 0.0)]]
        origin = (0.0, TOP + 0.0034, zc + d - 0.004)
        q.lathe(case, G.BRASS, 0xFFFFFF, seg=seg, origin=origin, axis=(0, 0, -1), ref=(0, 1, 0))
        q.lathe(bullet, G.BRASS, 0xC87850, seg=seg, origin=origin, axis=(0, 0, -1), ref=(0, 1, 0))
    return [q]


def magwell(lod):
    """Flared magazine well under the AR receiver (part 0): four walls around the magazine top, wider at the mouth."""
    q = Part(0)
    y_top, y_bot = 0.006, -0.024
    zf, zb = -0.0635, -0.0025          # inner opening front / back (magazine top is -0.060 .. -0.006)
    xi = 0.0112                        # inner half width (magazine 0.0105)
    t = 0.0022
    tint = 0xFFFFFF
    # side walls (flare out at the bottom)
    for sx in (-1, 1):
        x0, x1 = (xi, xi + t) if sx > 0 else (-xi - t, -xi)
        q.box((x0, y_bot + 0.004, zf - t), (x1, y_top, zb + t), G.ANOD, tint)
        fx0, fx1 = (xi + 0.0004, xi + t + 0.0014) if sx > 0 else (-xi - t - 0.0014, -xi - 0.0004)
        q.box((fx0, y_bot, zf - t - 0.0016), (fx1, y_bot + 0.0045, zb + t + 0.0016), G.ANOD, tint)
    # front wall with the flared lip and the rear wall
    q.box((-xi - t, y_bot + 0.004, zf - t), (xi + t, y_top, zf), G.ANOD, tint)
    q.box((-xi - t - 0.0014, y_bot, zf - t - 0.0016), (xi + t + 0.0014, y_bot + 0.0045, zf - 0.0004), G.ANOD, tint)
    q.box((-xi - t, y_bot + 0.004, zb), (xi + t, y_top, zb + t), G.ANOD, tint)
    if lod != 'distant':
        # magazine release button on the right, and the dark inside of the well
        q.lathe([(0.0, 0.0032), (0.0016, 0.0032), (0.0022, 0.0022), (0.0022, 0.0)], G.ANOD, 0xC8C8C8, seg=12, origin=(xi + t, -0.006, -0.012),
                axis=(1, 0, 0), ref=(0, 1, 0))
        q.box((-xi, y_bot + 0.004, zf + 0.0001), (xi, y_top - 0.002, zf + 0.0006), G.POLY, 0x181818)
    return q


def replace_part3(parts, lod):
    """FIX for semi_auto_rifle: swap the supplied flat magazine (part 3) for the detailed one and add the magwell."""
    out = []
    for p in parts:
        if p['id'] == 3:
            continue
        if p['id'] == 0:
            new = magwell(lod).to_fheq()
            base = len(p['f'])
            p = {'id': 0, 'f': np.vstack([p['f'], new['f']]), 'c': np.concatenate([p['c'], new['c']]), 'idx': np.vstack([p['idx'], new['idx'] + base])}
        out.append(p)
    out.append(build(lod, False)[0].to_fheq())
    return out
