"""[guns3] Semi-auto shotgun action detail: an ejection port and a bright bolt that cycles in it.

The supplied mesh only had a small charging handle (part 2) sticking out of a plain receiver, so a shot or a reload
moved one tiny piece and the gun looked dead. Now part 2 also carries the bolt face, seen in an ejection port cut on
the right of the receiver: it snaps back with the handle on every shot and when the gun is racked after loading.
Part 0 gets the dark port opening (static); everything else is untouched.
"""
import numpy as np
import gunkit as G
from gunkit import Part

PORT = (0.0170, 0.0176, 0.050, 0.082, -0.034, 0.030)   # x0 x1 y0 y1 z0 z1 of the opening on the receiver's right side


def _append(p, q):
    new = q.to_fheq()
    base = len(p['f'])
    return {'id': p['id'], 'f': np.vstack([p['f'], new['f']]), 'c': np.concatenate([p['c'], new['c']]), 'idx': np.vstack([p['idx'], new['idx'] + base])}


def semi_auto_action(parts, lod):
    if lod == 'distant':
        return parts
    out = []
    x0, x1, y0, y1, z0, z1 = PORT
    for p in parts:
        if p['id'] == 0:
            q = Part(0)
            # dark opening, with a thin bright edge so it reads as cut metal
            q.box((x0, y0, z0), (x1, y1, z1), G.POLY, 0x101010)
            q.box((x0 + 0.0002, y1 - 0.0012, z0), (x1 + 0.0002, y1, z1), G.BRIGHT, 0x9A9A9A)
            # forend grip: three recessed grooves on each side and a raised panel edge, so it is not a plain block
            for sx in (-1, 1):
                for gy in (0.0505, 0.0565, 0.0625):
                    gx0, gx1 = (0.0199, 0.0204) if sx > 0 else (-0.0204, -0.0199)
                    q.box((gx0, gy - 0.0013, -0.385), (gx1, gy + 0.0013, -0.175), G.POLY, 0x1A1A1A)
                ex0, ex1 = (0.0199, 0.0205) if sx > 0 else (-0.0205, -0.0199)
                q.box((ex0, 0.0435, -0.392), (ex1, 0.0455, -0.168), G.POLY, 0xD0D0D0)
                q.box((ex0, 0.0705, -0.392), (ex1, 0.0725, -0.168), G.POLY, 0xD0D0D0)
            p = _append(p, q)
        elif p['id'] == 2:
            q = Part(2)
            # bolt face filling the port when closed (moves back with the handle)
            q.box((x0 + 0.0003, y0 + 0.004, z0 + 0.003), (x1 + 0.0004, y1 - 0.003, z1 - 0.004), G.BRIGHT, 0xE8E8E8, r=0.0015)
            # extractor claw and a firing-pin channel line for a bit of mechanical read
            q.box((x1 + 0.0002, y0 + 0.012, z0 + 0.004), (x1 + 0.0008, y0 + 0.016, z0 + 0.012), G.BLUED, 0xC0C0C0)
            q.box((x1 + 0.0002, (y0 + y1) / 2 - 0.0006, z0 + 0.016), (x1 + 0.0006, (y0 + y1) / 2 + 0.0006, z1 - 0.008), G.BLUED, 0x808080)
            p = _append(p, q)
        out.append(p)
    return out


# [gear21] Break actions: real bores. When the double barrel (or the flare gun) breaks open you look straight into the
# chambers; the supplied barrels had flat metal faces there and at the muzzles. Each bore gets a dark hole with a
# bright extractor rim at the breech and a dark hole with a turned crown at the muzzle (part 1, the barrels).
BORES = {
    # chambers (x list), bore axis y, breech face z, muzzle face z, bore radius, rim radius
    'double_barrel': ([-0.0118, 0.0118], 0.067, -0.0965, -0.79, 0.0086, 0.0101),
    'flare_gun': ([0.0], 0.05, -0.0345, -0.232, 0.0125, 0.0142),
}


def break_bores(name):
    xs, by, zb, zm, r, rim = BORES[name]

    def fix(parts, lod):
        out = []
        seg = {'close': 28, 'field': 16, 'distant': 8}[lod]
        for p in parts:
            if p['id'] == 1:
                q = Part(1)
                for x in xs:
                    # breech: bright extractor rim, then the dark chamber mouth (faces +z)
                    if lod != 'distant':
                        q.lathe([(0.0003, rim), (0.0003, r)], G.BRIGHT, 0xB8B8B8, seg=seg, origin=(x, by, zb), axis=(0, 0, 1), ref=(1, 0, 0))
                    q.lathe([(0.0005, r), (0.0005, 0.0)], G.POLY, 0x0C0C0C, seg=seg, origin=(x, by, zb), axis=(0, 0, 1), ref=(1, 0, 0))
                    # muzzle: dark bore (faces -z) with a thin turned crown
                    if lod != 'distant':
                        q.lathe([(-0.0003, r + 0.0012), (-0.0003, r)], G.BRIGHT, 0x9A9A9A, seg=seg, origin=(x, by, zm), axis=(0, 0, 1), ref=(1, 0, 0))
                    q.lathe([(-0.0005, 0.0), (-0.0005, r)], G.POLY, 0x0C0C0C, seg=seg, origin=(x, by, zm), axis=(0, 0, 1), ref=(1, 0, 0))
                p = _append(p, q)
            out.append(p)
        return out
    return fix
