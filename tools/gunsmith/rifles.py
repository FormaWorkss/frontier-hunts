"""[gunsmith] Part replacements for the supplied field-gun meshes (the rest of each mesh is untouched).

semi_auto_stock: the "Assault Rifle" carried a flat brace-like slab for a stock. It is replaced by a collapsible
carbine stock on a round buffer tube (rounded body, cheek-weld ridge, adjustment lever, QD sling socket, ribbed
rubber butt pad). Envelope kept: comb top y ~0.084 between z 0.21 and 0.39 (the Steady Stock cheek riser clamps there,
FieldWeaponSockets.stockY/Z 0.09 / 0.29), butt face z 0.392.
"""
import numpy as np
import gunkit as G
from gunkit import Part

POLY_T = 0xFFFFFF
TUBE_Y = 0.0715


def _drop(part, keep):
    f, idx, c = part['f'], part['idx'], part['c']
    idx = idx[keep]
    used = np.unique(idx)
    remap = -np.ones(len(f), np.int64)
    remap[used] = np.arange(len(used))
    return {'id': part['id'], 'f': f[used], 'c': c[used], 'idx': remap[idx]}


def semi_auto_stock(parts, lod):
    seg = {'close': 32, 'field': 24, 'distant': 12}[lod]
    st = {'close': 3, 'field': 2, 'distant': 1}[lod]
    out = []
    for p in parts:
        if p['id'] == 0:
            Z = p['f'][p['idx']][:, :, 2]
            p = _drop(p, ~(Z.max(1) > 0.198))        # whole stock and every buffer-tube triangle reaching past z 0.198
            q = Part(0)
            side = G.side_frame()
            # buffer tube with its end cap and the castle nut ring at the receiver end
            q.lathe([(0.116, 0.0132), (0.372, 0.0132), (0.374, 0.0122), (0.374, 0.0)], G.ANOD, 0xE8E8EC, seg=seg, origin=(0, TUBE_Y, 0), ref=(1, 0, 0))
            q.lathe([(0.121, 0.0132), (0.122, 0.0156), (0.130, 0.0156), (0.131, 0.0132)], G.ANOD, 0xD0D0D4, seg=seg, origin=(0, TUBE_Y, 0), ref=(1, 0, 0),
                    knurl=None if lod == 'distant' else (8, 0.0012, 0.122, 0.130))
            # stock body: rides on the tube (comb), sweeps down to the butt
            body = [(0.226, 0.0845), (0.388, 0.0845), (0.388, -0.0265), (0.372, -0.0265), (0.300, 0.0300), (0.262, 0.0505),
                    (0.236, 0.0560), (0.222, 0.0640), (0.218, 0.0760)]
            q.extrude_round(body, -0.0168, 0.0168, 0.0045, G.POLY, POLY_T, frame=side, steps=st, smooth_deg=40)
            # cheek-weld ridge along the top and the recessed side panel (lightening cut)
            q.extrude_round([(0.236, 0.0845), (0.384, 0.0845), (0.384, 0.0905), (0.246, 0.0895)], -0.0105, 0.0105, 0.0028, G.POLY, POLY_T, frame=side,
                            steps=st)
            if lod != 'distant':
                for sx in (-1, 1):
                    x0, x1 = (0.0164, 0.0171) if sx > 0 else (-0.0171, -0.0164)
                    q.extrude_round([(0.300, 0.0400), (0.360, 0.0080), (0.372, 0.0080), (0.372, 0.0500), (0.330, 0.0560)], x0, x1, 0.0002, G.STIPPLE,
                                    0xC8C8C8, frame=side, steps=1)
                # adjustment lever under the front of the stock, QD sling socket on the left
                q.extrude_round([(0.232, 0.0520), (0.262, 0.0470), (0.264, 0.0430), (0.236, 0.0470)], -0.0045, 0.0045, 0.0012, G.POLY, 0x9A9A9E, frame=side,
                                steps=1)
                q.lathe([(0.0, 0.0055), (0.0016, 0.0055), (0.0022, 0.0040), (0.0022, 0.0)], G.ANOD, 0xD0D0D4, seg=16, origin=(-0.0168, 0.0600, 0.340),
                        axis=(-1, 0, 0), ref=(0, 1, 0))
                q.lathe([(0.0022, 0.0028), (0.0022, 0.0)], G.POLY, 0x202020, seg=12, origin=(-0.0168, 0.0600, 0.340), axis=(-1, 0, 0), ref=(0, 1, 0))
            # ribbed rubber butt pad
            q.extrude_round([(0.388, 0.0855), (0.3935, 0.0855), (0.3935, -0.0275), (0.388, -0.0275)], -0.0172, 0.0172, 0.0022, G.RUBBER, 0xFFFFFF,
                            frame=side, steps=st)
            new = q.to_fheq()
            base = len(p['f'])
            p = {'id': 0, 'f': np.vstack([p['f'], new['f']]), 'c': np.concatenate([p['c'], new['c']]), 'idx': np.vstack([p['idx'], new['idx'] + base])}
        out.append(p)
    return out
