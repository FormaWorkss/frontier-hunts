"""[hound3] Pose sheets of the in-game hound: the REAL HoundRig.java (via rigview.sh / HoundRigDump) poses the REAL
exported .fhsk, skinned here exactly like HoundRenderer.draw and drawn with the coat on a ground plane.

  python3 rigview.py [ultra|bal] [redbone|bluetick] [pose-prefix|all]   -> /tmp/claude-0/h3/poses_<...>.png
"""
import sys, os, gzip, struct, subprocess, math
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import r3

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
PATCH = ROOT + '/patch/assets/frontierhunts/'
OUT = '/tmp/claude-0/h4/'


def load(path):
    b = gzip.open(path).read(); o = [0]

    def rd(fmt):
        v = struct.unpack_from(fmt, b, o[0]); o[0] += struct.calcsize(fmt); return v
    magic, flags = rd('>ii'); meta = rd('>8f'); nb, = rd('>h')
    names = []; par = []; joint = []
    for i in range(nb):
        n, = rd('>h'); names.append(b[o[0]:o[0] + n].decode()); o[0] += n
        p, = rd('>h'); par.append(p); h = rd('>9f'); joint.append(h[:3])
    nv, = rd('>i')
    raw = np.frombuffer(b, dtype=np.dtype([('f', '>f4', 8), ('bi', 'u1', 4), ('bw', 'u1', 4)]), count=nv, offset=o[0])
    o[0] += nv * 40
    nt, = rd('>i')
    T = np.frombuffer(b, dtype='>u2', count=nt * 3, offset=o[0]).reshape(-1, 3).astype(int)
    f = raw['f'].astype(float)
    bw = raw['bw'].astype(float); bw /= np.maximum(bw.sum(1, keepdims=True), 1e-9)
    return dict(meta=meta, names=names, par=par, joint=np.array(joint), P=f[:, :3], N=f[:, 3:6], UV=f[:, 6:8],
                BI=raw['bi'].astype(int), BW=bw, T=T)


def dump(path, poses):
    txt = '\n'.join(name + ' ' + ' '.join('%s=%s' % (k, v) for k, v in kw.items()) for name, kw in poses) + '\n'
    r = subprocess.run([HERE + '/rigview.sh', path], input=txt, capture_output=True, text=True)
    out = {}
    for line in r.stdout.splitlines():
        t = line.split()
        if len(t) < 13:
            print(line); continue
        out[t[0]] = np.array(list(map(float, t[1:]))).reshape(-1, 3, 4)
    if not out:
        print(r.stdout, r.stderr)
    return out


def skin(m, Ms):
    P = np.zeros_like(m['P']); N = np.zeros_like(m['N'])
    for k in range(4):
        Mk = Ms[m['BI'][:, k]]
        w = m['BW'][:, k:k + 1]
        P += w * (np.einsum('nij,nj->ni', Mk[:, :, :3], m['P']) + Mk[:, :, 3])
        N += w * np.einsum('nij,nj->ni', Mk[:, :, :3], m['N'])
    N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-12
    return P, N


def cyc(prefix, speed, gait, n=4, **kw):
    return [('%s_%d' % (prefix, i), dict(speed=speed, phase=i / n, **{gait: 1}, **kw)) for i in range(n)]


POSES = ([('stand', {})] + cyc('walk', 0.09, 'walk') + cyc('trot', 0.18, 'trot') + cyc('gallop', 0.32, 'gallop') +
         [('sniff', dict(sniff=1, wag=1, age=4)), ('sniffwalk', dict(sniff=1, wag=1, speed=0.1, phase=0.3, walk=1, sweep=0.7)),
          ('bay', dict(bay=1, wag=1, age=3)), ('point', dict(point=1)), ('sit', dict(sit=1)), ('sitbay', dict(sit=1, bay=1)),
          ('lie', dict(lie=1)), ('tuck', dict(tuck=1, speed=0.1, phase=0.4, walk=1)), ('look', dict(headYaw=50, headPitch=-10)),
          ('swim', dict(water='true', age=3))])


def main():
    lod = sys.argv[1] if len(sys.argv) > 1 else 'ultra'
    kind = sys.argv[2] if len(sys.argv) > 2 else 'redbone'
    sel = sys.argv[3] if len(sys.argv) > 3 else 'all'
    yaws = (90, 145) if len(sys.argv) <= 4 else tuple(int(x) for x in sys.argv[4].split(','))
    path = PATCH + 'models/wildlife/hound_%s.fhsk' % lod
    m = load(path)
    tex = np.asarray(Image.open(PATCH + 'textures/entity/wildlife/real/hound_%s%s.png' % (kind, '' if lod == 'ultra' else '_far')).convert('RGB')).astype(float) / 255
    poses = POSES if sel == 'all' else [p for p in POSES if any(p[0].startswith(s) for s in sel.split(','))]
    mats = dump(path, poses)
    views = []
    for name, _ in poses:
        P, N = skin(m, mats[name])
        lo = P[:, 1].min()
        for i, y in enumerate(yaws):
            views.append(r3.render(P, m['T'], N, m['UV'], tex, yaw=y, pitch=4 if y == 90 else 12, W=340, H=260,
                                   center=[0, 0.40, 0], dist=1.9, label=('%s  low %.3f' % (name, lo)) if i == 0 else None))
    r3.sheet(views, 6).save(OUT + 'poses_%s_%s_%s.png' % (lod, kind, sel.replace(',', '+')))
    print('wrote', OUT + 'poses_%s_%s_%s.png' % (lod, kind, sel.replace(',', '+')))


if __name__ == '__main__':
    main()
