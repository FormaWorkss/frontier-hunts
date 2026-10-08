#!/usr/bin/env python3
"""[livingworld] Reads a saved world's region files (Anvil, 1.21 chunk format) and writes the visible blocks of an area as
a voxel dump for render.py - so previews show what the server REALLY generated (terrain, trees, our sites).
usage: mca_dump.py <world dir> <x0> <z0> <x1> <z1> <out.json> [ymin] [ymax] [--entities]"""
import sys, os, struct, zlib, gzip, io, json, math
sys.path.insert(0, os.path.dirname(__file__))


def nbt_read(data):
    f = io.BytesIO(data)

    def rd(fmt):
        return struct.unpack('>' + fmt, f.read(struct.calcsize('>' + fmt)))[0]

    def rstr():
        n = rd('H'); return f.read(n).decode('utf-8', 'replace')

    def payload(t):
        if t == 1: return rd('b')
        if t == 2: return rd('h')
        if t == 3: return rd('i')
        if t == 4: return rd('q')
        if t == 5: return rd('f')
        if t == 6: return rd('d')
        if t == 7: n = rd('i'); return f.read(n)
        if t == 8: return rstr()
        if t == 9:
            et = rd('b'); n = rd('i'); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = rd('b')
                if tt == 0: return d
                k = rstr(); d[k] = payload(tt)
        if t == 11: n = rd('i'); return list(struct.unpack('>%di' % n, f.read(4 * n)))
        if t == 12: n = rd('i'); return list(struct.unpack('>%dq' % n, f.read(8 * n)))
        raise ValueError(t)
    t = rd('b'); rstr(); return payload(t)


def chunk(world, cx, cz, sub='region'):
    rx, rz = cx >> 5, cz >> 5
    p = os.path.join(world, sub, f'r.{rx}.{rz}.mca')
    if not os.path.exists(p): return None
    with open(p, 'rb') as fh:
        hdr = fh.read(4096)
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        off = (hdr[i] << 16 | hdr[i + 1] << 8 | hdr[i + 2])
        if off == 0: return None
        fh.seek(off * 4096)
        ln = struct.unpack('>i', fh.read(4))[0]
        comp = fh.read(1)[0]
        raw = fh.read(ln - 1)
    data = zlib.decompress(raw) if comp == 2 else gzip.decompress(raw) if comp == 1 else raw
    return nbt_read(data)


def state_str(p):
    s = p['Name']
    if p.get('Properties'):
        s += '[' + ','.join(f'{k}={v}' for k, v in sorted(p['Properties'].items())) + ']'
    return s


def blocks_of(ch):
    out = {}
    for sec in ch.get('sections', []):
        bs = sec.get('block_states')
        if not bs: continue
        pal = [state_str(p) for p in bs['palette']]
        y0 = sec['Y'] * 16
        if len(pal) == 1:
            if pal[0] != 'minecraft:air':
                for i in range(4096): out[(i & 15, y0 + (i >> 8), (i >> 4) & 15)] = pal[0]
            continue
        bits = max(4, (len(pal) - 1).bit_length())
        per = 64 // bits
        mask = (1 << bits) - 1
        data = bs['data']
        for i in range(4096):
            word = data[i // per] & 0xFFFFFFFFFFFFFFFF
            v = (word >> ((i % per) * bits)) & mask
            s = pal[v] if v < len(pal) else 'minecraft:air'
            if s != 'minecraft:air' and s != 'minecraft:cave_air':
                out[(i & 15, y0 + (i >> 8), (i >> 4) & 15)] = s
    return out


def main():
    a = [x for x in sys.argv[1:] if not x.startswith('--')]
    world, x0, z0, x1, z1, outp = a[0], int(a[1]), int(a[2]), int(a[3]), int(a[4]), a[5]
    ymin = int(a[6]) if len(a) > 6 else -64
    ymax = int(a[7]) if len(a) > 7 else 320
    W = {}
    ents = []
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            ch = chunk(world, cx, cz)
            if ch is None: continue
            for (lx, y, lz), s in blocks_of(ch).items():
                x, z = cx * 16 + lx, cz * 16 + lz
                if x0 <= x <= x1 and z0 <= z <= z1 and ymin <= y <= ymax:
                    W[(x, y, z)] = s
            if '--entities' in sys.argv:
                e = chunk(world, cx, cz, 'entities')
                if e:
                    for en in e.get('Entities', []):
                        px, py, pz = en['Pos']
                        if x0 <= px <= x1 + 1 and z0 <= pz <= z1 + 1:
                            ents.append([px, py, pz, en.get('id', '?'), en.get('Rotation', [0])[0]])
    full = lambda s: s is not None and not any(k in s for k in ('leaves', 'glass', 'water', 'grass[', 'short_grass', 'fern', 'flower', 'slab', 'stairs', 'fence', 'door', 'trapdoor', 'sign', 'lantern', 'campfire', 'frontierhunts:', 'frontierstructures:', 'carpet', 'chest', 'snow[', 'torch', 'bush', 'path', 'farmland', 'wall', 'chain'))
    vis = []
    for (x, y, z), s in W.items():
        if all(full(W.get(q)) for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))):
            continue
        if y < ymin + 1 and full(W.get((x, y + 1, z))):
            continue
        vis.append([x, y, z, s])
    json.dump({'title': f'{os.path.basename(world.rstrip("/"))} {x0},{z0}..{x1},{z1} (saved world)', 'blocks': vis, 'entities': ents}, open(outp, 'w'))
    print(outp, len(vis), 'blocks', len(ents), 'entities')


main()
