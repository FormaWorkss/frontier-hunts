"""[gunsmith] Ridgeline supplied rifle mesh (.fhrm, little-endian, magic 1297238086, v2): verts 8 floats (x y z nx ny nz u v), faces 4 ints (a b c part)."""
import struct
import numpy as np


def read(path):
    b = open(path, 'rb').read()
    magic, ver, nv, nf = struct.unpack('<iiii', b[:16])
    assert magic == 1297238086 and ver == 2
    V = np.frombuffer(b[16:16 + nv * 32], '<f4').reshape(nv, 8).astype(np.float64)
    F = np.frombuffer(b[16 + nv * 32:], '<i4').reshape(nf, 4).astype(np.int64)
    return V, F
