"""[gunsmith] .fheq equipment mesh I/O (big-endian; magic 'FHEQ' 1179141457, version 1).
Per part: id, nVerts, nTris; vertex = x y z nx ny nz u v (float32) + int RGB tint; then 3*nTris int indices."""
import struct
import numpy as np

MAGIC = 1179141457
VDT = np.dtype([('f', '>f4', 8), ('c', '>i4')])


def read(path):
    b = open(path, 'rb').read()
    magic, ver, n = struct.unpack('>iii', b[:12])
    assert magic == MAGIC and ver == 1, path
    o = 12
    parts = []
    for _ in range(n):
        pid, nv, nt = struct.unpack('>iii', b[o:o + 12]); o += 12
        V = np.frombuffer(b[o:o + nv * 36], dtype=VDT); o += nv * 36
        idx = np.frombuffer(b[o:o + nt * 12], dtype='>i4').reshape(nt, 3).astype(np.int64); o += nt * 12
        parts.append({'id': pid, 'f': V['f'].astype(np.float64), 'c': V['c'].astype(np.int64), 'idx': idx})
    assert o == len(b), path
    return parts


def write(path, parts):
    out = [struct.pack('>iii', MAGIC, 1, len(parts))]
    for p in parts:
        f = np.asarray(p['f'], dtype=np.float64); c = np.asarray(p['c'], dtype=np.int64); idx = np.asarray(p['idx'], dtype=np.int64)
        assert np.isfinite(f).all()
        assert 1 <= len(f) <= 100000 and 1 <= len(idx) <= 40000 and 0 <= p['id'] <= 15
        assert idx.min() >= 0 and idx.max() < len(f)
        out.append(struct.pack('>iii', p['id'], len(f), len(idx)))
        V = np.zeros(len(f), dtype=VDT); V['f'] = f.astype('>f4'); V['c'] = (c & 0xFFFFFF).astype('>i4')
        out.append(V.tobytes())
        out.append(idx.astype('>i4').tobytes())
    open(path, 'wb').write(b''.join(out))
