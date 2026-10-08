"""Minimal NBT reader/writer (gzip structure templates). Tags are kept as (type, value) so they round-trip exactly."""
import gzip, struct, io

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)


def _rd(f, t):
    if t == BYTE: return struct.unpack('>b', f.read(1))[0]
    if t == SHORT: return struct.unpack('>h', f.read(2))[0]
    if t == INT: return struct.unpack('>i', f.read(4))[0]
    if t == LONG: return struct.unpack('>q', f.read(8))[0]
    if t == FLOAT: return struct.unpack('>f', f.read(4))[0]
    if t == DOUBLE: return struct.unpack('>d', f.read(8))[0]
    if t == BARR:
        n = struct.unpack('>i', f.read(4))[0]; return f.read(n)
    if t == STR:
        n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8')
    if t == LIST:
        et = f.read(1)[0]; n = struct.unpack('>i', f.read(4))[0]
        return (et, [_rd(f, et) for _ in range(n)])
    if t == COMP:
        d = {}
        while True:
            tt = f.read(1)[0]
            if tt == END: return d
            name = _rd(f, STR); d[name] = (tt, _rd(f, tt))
    if t == IARR:
        n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == LARR:
        n = struct.unpack('>i', f.read(4))[0]; return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError(t)


def _wr(f, t, v):
    if t == BYTE: f.write(struct.pack('>b', v))
    elif t == SHORT: f.write(struct.pack('>h', v))
    elif t == INT: f.write(struct.pack('>i', v))
    elif t == LONG: f.write(struct.pack('>q', v))
    elif t == FLOAT: f.write(struct.pack('>f', v))
    elif t == DOUBLE: f.write(struct.pack('>d', v))
    elif t == BARR: f.write(struct.pack('>i', len(v))); f.write(v)
    elif t == STR:
        b = v.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b)
    elif t == LIST:
        et, items = v; f.write(bytes([et])); f.write(struct.pack('>i', len(items)))
        for i in items: _wr(f, et, i)
    elif t == COMP:
        for k, (tt, vv) in v.items():
            f.write(bytes([tt])); _wr(f, STR, k); _wr(f, tt, vv)
        f.write(bytes([END]))
    elif t == IARR: f.write(struct.pack('>i', len(v))); f.write(struct.pack('>%di' % len(v), *v))
    elif t == LARR: f.write(struct.pack('>i', len(v))); f.write(struct.pack('>%dq' % len(v), *v))
    else: raise ValueError(t)


def load(path):
    raw = open(path, 'rb').read()
    try: raw = gzip.decompress(raw)
    except OSError: pass
    f = io.BytesIO(raw)
    t = f.read(1)[0]; name = _rd(f, STR)
    return name, _rd(f, t)


def save(path, name, root):
    f = io.BytesIO(); f.write(bytes([COMP])); _wr(f, STR, name); _wr(f, COMP, root)
    with open(path, 'wb') as o: o.write(gzip.compress(f.getvalue(), mtime=0))
