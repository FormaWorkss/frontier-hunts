"""Reader for the mod's .fhsk skinned meshes (see wildlife2026/client/SkinnedMesh.java)."""
import gzip, struct, zipfile, io
import numpy as np

JAR = '/home/claude/fh/merged62g8.jar'

class Mesh:
    pass

def parse(data):
    b = gzip.decompress(data)
    o = 0
    def rd(fmt):
        nonlocal o
        v = struct.unpack_from('>' + fmt, b, o)
        o += struct.calcsize('>' + fmt)
        return v
    m = Mesh()
    magic, = rd('i')
    v2 = magic == 0x46485332
    m.bird = (rd('i')[0] & 1) != 0
    m.meta = list(rd('8f'))
    nb, = rd('h')
    m.names, m.parent, m.joint, m.rest, m.feed = [], [], [], [], []
    for i in range(nb):
        ln, = rd('h')
        m.names.append(b[o:o+ln].decode()); o += ln
        m.parent.append(rd('h')[0])
        m.joint.append(rd('3f'))
        m.rest.append(rd('3f'))
        m.feed.append(rd('3f') if v2 else (0, 0, 0))
    nv, = rd('i')
    rec = np.frombuffer(b, dtype=np.dtype([('p', '>f4', 3), ('n', '>f4', 3), ('uv', '>f4', 2), ('b', 'u1', 4), ('w', 'u1', 4)]), count=nv, offset=o)
    o += rec.nbytes
    m.pos = rec['p'].astype(np.float32); m.nrm = rec['n'].astype(np.float32); m.uv = rec['uv'].astype(np.float32)
    m.bone = rec['b'].astype(np.int32); w = rec['w'].astype(np.float32); s = w.sum(1, keepdims=True)
    w[s[:, 0] <= 0, 0] = 255; s = w.sum(1, keepdims=True); m.weight = w / s
    nt, = rd('i')
    m.tris = np.frombuffer(b, dtype='>u2', count=nt*3, offset=o).astype(np.int32).reshape(-1, 3)
    m.joint = np.array(m.joint, np.float32); m.rest = np.array(m.rest, np.float32); m.feed = np.array(m.feed, np.float32)
    return m

def load(name, jar=JAR):
    with zipfile.ZipFile(jar) as z:
        return parse(z.read('assets/frontierhunts/models/wildlife/%s.fhsk' % name))

if __name__ == '__main__':
    import sys
    for n in sys.argv[1:]:
        m = load(n)
        print(n, 'bird', m.bird, 'meta', ['%.3f' % x for x in m.meta], 'verts', len(m.pos), 'tris', len(m.tris))
        print(' bbox', m.pos.min(0), m.pos.max(0))
        for i, nm in enumerate(m.names):
            cnt = int(((m.bone == i) & (m.weight > 0.3)).any(1).sum())
            print('  %2d %-10s parent %2d joint %s rest %s feed %s verts %d' % (i, nm, m.parent[i], np.round(m.joint[i], 3), np.round(m.rest[i], 3), np.round(m.feed[i], 3), cnt))
