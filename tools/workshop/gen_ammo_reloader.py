#!/usr/bin/env python3
"""[gear21] The Ammo Reloader, remade in the style of the other Frontier Hunts benches (HD wood, the field-materials
metals, tinted - the frontierhunts:field_shelter quad models) instead of 16 px blocky textures:

  left half  - a cast-iron single-stage reloading press bolted to the bench (C-frame, die and lock ring, ram, long
               handle with a black ball toward you) and a powder measure (clear hopper on a brass drum);
  right half - a wooden loading block full of brass cases, a beam scale, a case trimmer, boxes of bullets and primers;
  bench      - plank top, aprons, four legs and a lower shelf with a crate of brass.

Front (where you stand) is -z for facing=north, like the other stations. Writes ammo_reloader{,_left,_right,_inventory}.json.
usage: gen_ammo_reloader.py <out models/block dir>"""
import json
import math
import os
import sys

OUT = sys.argv[1] if len(sys.argv) > 1 else 'patch/assets/frontierhunts/models/block'
TEX = {'hardware': 'frontierhunts:material/field_materials', 'wood': 'frontierhunts:equipment/workshop/wood_v1',
       'plain': 'frontierhunts:entity/material', 'particle': 'frontierhunts:equipment/workshop/wood_v1'}
# atlas cells (u0, v0, u1, v1)
STEEL = ('hardware', (0.77, 0.02, 0.95, 0.20))
DARK = ('hardware', (0.52, 0.02, 0.70, 0.20))
WOOD = ('wood', (0.03, 0.04, 0.95, 0.91))
PLAIN = ('plain', (0.5, 0.5, 0.5, 0.5))
C_WOOD = 0xC2AA8C
C_WOOD_L = 0xEDD0AA
C_IRON = 0x5B6066      # cast iron (dark cell)
C_PAINT = 0x58844C     # the press frame's green enamel
C_STEEL = 0xC8CCD0
C_BLACK = 0x2A2A2C
C_BRASS = 0xE2B866
C_COPPER = 0xC8784C
C_GREEN = 0x4E6B3A
C_RED = 0x8E3A2E
C_PAPER = 0xE8E0CC
C_GLASS = 0xD8E8EC


class Mesh:
    def __init__(self):
        self.faces = []

    def quad(self, mat, color, pts, uv):
        self.faces.append({'m': mat[0], 'color': color, 'v': [[round(p[0], 4), round(p[1], 4), round(p[2], 4), round(t[0], 4), round(t[1], 4)]
                                                              for p, t in zip(pts, uv)]})

    def box(self, lo, hi, mat, color, sides='nsewud'):
        x0, y0, z0 = lo
        x1, y1, z1 = hi
        u0, v0, u1, v1 = mat[1]

        def uv(a, b):
            # map the face's two spans onto the cell (texel density ~ the wood's: one cell = one block)
            du = (u1 - u0) * min(1.0, a)
            dv = (v1 - v0) * min(1.0, b)
            return [(u0 + du, v0 + dv), (u0 + du, v0), (u0, v0), (u0, v0 + dv)]
        # each face CCW seen from outside
        if 'w' in sides:
            self.quad(mat, color, [(x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (x0, y0, z0)], uv(z1 - z0, y1 - y0))
        if 'e' in sides:
            self.quad(mat, color, [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], uv(z1 - z0, y1 - y0))
        if 'n' in sides:
            self.quad(mat, color, [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)], uv(x1 - x0, y1 - y0))
        if 's' in sides:
            self.quad(mat, color, [(x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (x0, y0, z1)], uv(x1 - x0, y1 - y0))
        if 'u' in sides:
            self.quad(mat, color, [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], uv(x1 - x0, z1 - z0))
        if 'd' in sides:
            self.quad(mat, color, [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)], uv(x1 - x0, z1 - z0))

    def cyl(self, cx, cz, y0, y1, r, mat, color, n=10, axis='y'):
        """an n-sided prism (vertical, or along z with axis='z' centred at (cx, y=cz) running y0..y1 in z)"""
        u0, v0, u1, v1 = mat[1]
        for i in range(n):
            a0, a1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
            if axis == 'y':
                p = [(cx + r * math.cos(a0), y0, cz + r * math.sin(a0)), (cx + r * math.cos(a0), y1, cz + r * math.sin(a0)),
                     (cx + r * math.cos(a1), y1, cz + r * math.sin(a1)), (cx + r * math.cos(a1), y0, cz + r * math.sin(a1))]
                p = [p[3], p[2], p[1], p[0]]
            else:
                p = [(cx + r * math.cos(a0), cz + r * math.sin(a0), y0), (cx + r * math.cos(a0), cz + r * math.sin(a0), y1),
                     (cx + r * math.cos(a1), cz + r * math.sin(a1), y1), (cx + r * math.cos(a1), cz + r * math.sin(a1), y0)]
            self.quad(mat, color, p, [(u0, v1), (u0, v0), (u1, v0), (u1, v1)])
        # caps
        for y, up in ((y1, True), (y0, False)):
            ring = [(cx + r * math.cos(2 * math.pi * i / n), y, cz + r * math.sin(2 * math.pi * i / n)) if axis == 'y'
                    else (cx + r * math.cos(2 * math.pi * i / n), cz + r * math.sin(2 * math.pi * i / n), y) for i in range(n)]
            c = (cx, y, cz) if axis == 'y' else (cx, cz, y)
            for i in range(n):
                a, b = ring[i], ring[(i + 1) % n]
                tri = [c, b, a, a] if up == (axis == 'y') else [c, a, b, b]
                self.quad(mat, color, tri, [(u0, v0), (u1, v0), (u1, v1), (u1, v1)])

    def shifted(self, dx, sx=1.0):
        m = Mesh()
        for f in self.faces:
            g = dict(f)
            g['v'] = [[v[0] * sx + dx, v[1], v[2], v[3], v[4]] for v in f['v']]
            m.faces.append(g)
        return m


TOP_Y0, TOP_Y1 = 0.86, 0.94


def bench(m, legs_x):
    m.box((0.0, TOP_Y0, 0.04), (1.0, TOP_Y1, 0.96), WOOD, C_WOOD_L)
    m.box((0.0, 0.78, 0.07), (1.0, TOP_Y0, 0.11), WOOD, C_WOOD, 'nsud')
    m.box((0.0, 0.78, 0.89), (1.0, TOP_Y0, 0.93), WOOD, C_WOOD, 'nsud')
    for lx in legs_x:
        for lz in (0.07, 0.85):
            m.box((lx, 0.0, lz), (lx + 0.08, TOP_Y0, lz + 0.08), WOOD, C_WOOD)
    m.box((0.0, 0.16, 0.12), (1.0, 0.2, 0.88), WOOD, C_WOOD)


def press(m, x):
    """single-stage press at bench x centre x"""
    y = TOP_Y1
    m.box((x - 0.1, y, 0.36), (x + 0.1, y + 0.025, 0.66), STEEL, C_PAINT)            # base
    for bx in (x - 0.08, x + 0.065):
        m.cyl(bx + 0.007, 0.4, y + 0.025, y + 0.035, 0.012, STEEL, C_STEEL, 6)         # bolts
        m.cyl(bx + 0.007, 0.62, y + 0.025, y + 0.035, 0.012, STEEL, C_STEEL, 6)
    m.box((x - 0.045, y + 0.02, 0.56), (x + 0.045, y + 0.5, 0.65), STEEL, C_PAINT)   # column (back of the C)
    m.box((x - 0.05, y + 0.42, 0.42), (x + 0.05, y + 0.52, 0.65), STEEL, C_PAINT)    # top arm
    m.box((x - 0.05, y + 0.02, 0.42), (x + 0.05, y + 0.13, 0.62), STEEL, C_PAINT)    # lower arm / ram guide
    m.cyl(x, 0.48, y + 0.27, y + 0.43, 0.024, STEEL, C_STEEL, 12)                   # die
    m.cyl(x, 0.48, y + 0.39, y + 0.415, 0.034, STEEL, C_BLACK, 12)                  # lock ring
    m.cyl(x, 0.48, y + 0.13, y + 0.21, 0.02, STEEL, C_STEEL, 12)                    # ram
    m.box((x - 0.02, y + 0.21, 0.46), (x + 0.02, y + 0.225, 0.5), STEEL, C_STEEL)  # shell holder
    m.cyl(x, 0.48, y + 0.225, y + 0.265, 0.01, STEEL, C_BRASS, 8)                   # a case in the holder
    # the handle: from its pivot on the right side, forward and down to a black ball
    piv = (x + 0.06, y + 0.08, 0.52)
    end = (x + 0.06, y + 0.2, 0.02)
    steps = 6
    for i in range(steps):
        t0, t1 = i / steps, (i + 1) / steps
        a = [piv[k] + (end[k] - piv[k]) * t0 for k in range(3)]
        b = [piv[k] + (end[k] - piv[k]) * t1 for k in range(3)]
        lo = [min(a[k], b[k]) for k in range(3)]
        hi = [max(a[k], b[k]) for k in range(3)]
        m.box((lo[0] - 0.008, lo[1] - 0.008, lo[2]), (hi[0] + 0.008, hi[1] + 0.008, hi[2]), STEEL, C_STEEL)
    m.box((x + 0.045, y + 0.07, 0.5), (x + 0.075, y + 0.1, 0.55), DARK, C_IRON)    # pivot boss
    m.cyl(end[0], end[2], end[1] - 0.03, end[1] + 0.03, 0.03, PLAIN, C_BLACK, 10)  # ball grip (short round)
    # powder measure on a bracket at the back right
    px = x + 0.24
    m.box((px - 0.02, y, 0.68), (px + 0.02, y + 0.3, 0.72), DARK, C_IRON)
    m.cyl(px, 0.66, y + 0.2, y + 0.29, 0.045, STEEL, C_BRASS, 12)
    m.cyl(px, 0.66, y + 0.29, y + 0.44, 0.04, PLAIN, C_GLASS, 12)
    m.cyl(px, 0.66, y + 0.44, y + 0.455, 0.044, STEEL, C_BLACK, 12)
    m.box((px + 0.04, y + 0.23, 0.65), (px + 0.11, y + 0.25, 0.67), STEEL, C_STEEL)  # metering handle


def prep(m):
    y = TOP_Y1
    # loading block with brass cases standing in it
    m.box((0.12, y, 0.3), (0.42, y + 0.05, 0.6), WOOD, C_WOOD)
    for i in range(5):
        for j in range(4):
            cx, cz = 0.15 + i * 0.06, 0.33 + j * 0.075
            if (i * 7 + j * 3) % 11 != 0:
                m.cyl(cx, cz, y + 0.05, y + 0.12, 0.011, STEEL, C_BRASS, 6)
    # beam scale
    m.box((0.52, y, 0.5), (0.78, y + 0.02, 0.62), DARK, C_BLACK)
    m.box((0.62, y + 0.02, 0.54), (0.65, y + 0.12, 0.58), DARK, C_BLACK)
    m.box((0.5, y + 0.11, 0.55), (0.8, y + 0.125, 0.57), STEEL, C_STEEL)
    m.cyl(0.53, 0.56, y + 0.07, y + 0.085, 0.035, STEEL, C_BRASS, 10)                # pan
    m.box((0.72, y + 0.125, 0.55), (0.735, y + 0.14, 0.57), STEEL, C_BRASS)          # poise
    # boxes of bullets and primers at the back, a box of shotgun shells
    m.box((0.52, y, 0.7), (0.72, y + 0.09, 0.86), PLAIN, C_GREEN)
    m.box((0.52, y + 0.09, 0.7), (0.72, y + 0.095, 0.86), PLAIN, C_PAPER, 'u')
    m.box((0.76, y, 0.72), (0.9, y + 0.05, 0.84), PLAIN, C_RED)
    m.box((0.12, y, 0.7), (0.4, y + 0.11, 0.86), PLAIN, 0x7A5A34)
    for i in range(4):
        m.cyl(0.16 + i * 0.065, 0.78, y + 0.11, y + 0.14, 0.02, STEEL, C_BRASS, 8)
    # case trimmer clamped at the front right
    m.box((0.6, y, 0.12), (0.84, y + 0.04, 0.22), DARK, C_IRON)
    m.cyl(0.72, 0.17, 0.84, 0.95, 0.012, STEEL, C_STEEL, 8, axis='z')
    m.box((0.82, y + 0.03, 0.08), (0.84, y + 0.09, 0.12), STEEL, C_STEEL)
    m.cyl(0.83, 0.07, y + 0.09, y + 0.12, 0.015, PLAIN, C_BLACK, 8)
    # crate of brass on the lower shelf
    m.box((0.2, 0.2, 0.25), (0.75, 0.42, 0.75), WOOD, C_WOOD)
    for i in range(6):
        m.cyl(0.26 + i * 0.08, 0.5, 0.42, 0.45, 0.025, STEEL, C_BRASS, 6)


def write(name, mesh):
    d = {'loader': 'frontierhunts:field_shelter', 'textures': TEX, 'shelter_faces': mesh.faces}
    json.dump(d, open(os.path.join(OUT, name + '.json'), 'w'), separators=(',', ':'))
    print(name, len(mesh.faces), 'faces')


left = Mesh()
bench(left, [0.04])
press(left, 0.42)
right = Mesh()
bench(right, [0.88])
prep(right)
single = Mesh()
bench(single, [0.04, 0.88])
press(single, 0.32)
os.makedirs(OUT, exist_ok=True)
write('ammo_reloader_left', left)
write('ammo_reloader_right', right)
write('ammo_reloader', single)
write('ammo_reloader_inventory', single)
