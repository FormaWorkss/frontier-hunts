"""[hound3] Signed-distance primitives (numpy, vectorised over (n,3) points) used to sculpt the hound."""
import numpy as np


def _rot(axis_from, axis_to):
    a = np.asarray(axis_from, float); a /= np.linalg.norm(a)
    b = np.asarray(axis_to, float); b /= np.linalg.norm(b)
    v = np.cross(a, b); c = float(a @ b)
    if np.linalg.norm(v) < 1e-9:
        return np.eye(3) if c > 0 else np.diag([1, -1, -1.0])
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx / (1 + c)


def Rx(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def Ry(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def Rz(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Prim:
    """one sculpt primitive: distance function + the bone that owns its flesh + a blend radius"""

    def __init__(self, fn, bone, k=0.02, name='', sub=False, bbox=None):
        self.fn, self.bone, self.k, self.name, self.sub, self.bbox = fn, bone, k, name, sub, bbox

    def __call__(self, p):
        return self.fn(p)


def ellipsoid(c, r, R=None):
    c = np.asarray(c, float); r = np.asarray(r, float); R = np.eye(3) if R is None else R

    def f(p):
        q = (p - c) @ R            # into the ellipsoid's frame (R columns = its axes)
        k0 = np.linalg.norm(q / r, axis=1)
        k1 = np.linalg.norm(q / (r * r), axis=1)
        return k0 * (k0 - 1.0) / np.maximum(k1, 1e-9)
    return f


def round_cone(a, b, ra, rb):
    """capsule with different end radii (iq's sdRoundCone between two points)"""
    a = np.asarray(a, float); b = np.asarray(b, float)
    ba = b - a; l2 = ba @ ba; rr = ra - rb; a2 = l2 - rr * rr; il2 = 1.0 / l2

    def f(p):
        pa = p - a
        y = pa @ ba
        z = y - l2
        xv = pa * l2 - np.outer(y, ba)
        x2 = np.einsum('ij,ij->i', xv, xv)
        y2 = y * y * l2
        z2 = z * z * l2
        k = np.sign(rr) * rr * rr * x2
        out = (np.sqrt(x2 * a2 * il2) + y * rr) * il2 - ra
        m1 = np.sign(z) * a2 * z2 > k
        out = np.where(m1, np.sqrt(np.maximum(x2 + z2, 0)) * il2 - rb, out)
        m2 = np.sign(y) * a2 * y2 < k
        out = np.where(m2 & ~m1, np.sqrt(np.maximum(x2 + y2, 0)) * il2 - ra, out)
        return out
    return f


def round_box(c, half, r, R=None):
    c = np.asarray(c, float); half = np.asarray(half, float); R = np.eye(3) if R is None else R

    def f(p):
        q = np.abs((p - c) @ R) - half + r
        return np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(q.max(1), 0) - r
    return f


def chain(points, radii):
    """a smooth tube along a polyline (round cones, hard-unioned: they overlap at the joints exactly)"""
    fs = [round_cone(points[i], points[i + 1], radii[i], radii[i + 1]) for i in range(len(points) - 1)]

    def f(p):
        d = fs[0](p)
        for g in fs[1:]:
            d = np.minimum(d, g(p))
        return d
    return f


def smin(a, b, k):
    h = np.maximum(k - np.abs(a - b), 0.0) / k
    return np.minimum(a, b) - h * h * k * 0.25


def ssub(d, s, k):
    """smooth subtraction: carve s out of d"""
    h = np.clip(0.5 - 0.5 * (d + s) / k, 0.0, 1.0)
    return d * (1 - h) + (-s) * h + k * h * (1 - h)
