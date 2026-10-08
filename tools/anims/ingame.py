"""[anims] In-game-distance look at the big cats: player eye 1.62 above the ground, 3 / 6 / 10 blocks away, Minecraft
lighting ('mc': the two fixed entity lights + 0.4 ambient) and sun, the level of detail the game picks at that range
(ultra close, mid further). usage: ingame.py <out.png> [species...] [--mesh-dir dir] [--tex-dir dir]"""
import sys, os
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'meshes'))
import render as R  # noqa: E402

args = sys.argv[1:]
mesh_dir = tex_dir = None
if '--mesh-dir' in args:
    i = args.index('--mesh-dir'); mesh_dir = args[i + 1]; del args[i:i + 2]
if '--tex-dir' in args:
    i = args.index('--tex-dir'); tex_dir = args[i + 1]; del args[i:i + 2]
out = args[0]
species = args[1:] or ['cougar', 'lion', 'panther', 'cheetah']
tiles = []
for sp in species:
    for dist, lod, ang in ((3.0, 'ultra', 35), (6.0, 'ultra', 60), (10.0, 'mid', 20)):
        m = R.load_mesh(os.path.join(mesh_dir, '%s_%s.fhsk' % (sp, lod)) if mesh_dir else '%s_%s' % (sp, lod))
        tex = R.load_tex(os.path.join(tex_dir, sp + '.png') if tex_dir else R.coat_for(sp))
        P = np.asarray(m.pos); lo, hi = P.min(0), P.max(0)
        c = np.array([(lo[0] + hi[0]) / 2, hi[1] * 0.55, (lo[2] + hi[2]) / 2])
        a = np.radians(ang)
        eye = np.array([np.sin(a) * dist, 1.62, -np.cos(a) * dist]) + np.array([c[0], 0, c[2]])
        fov = 70.0 * 0.45   # a 70-degree screen, cropped around the animal
        for mode in ('mc', 'sun'):
            img, _ = R.render(P, np.asarray(m.nrm), np.asarray(m.uv), np.asarray(m.tris).reshape(-1, 3), tex, eye, c,
                              size=(360, 270), fov=fov, mode=mode)
            tiles.append(R.label(R.to_img(img), '%s %s %.0fm %s' % (sp, lod, dist, mode)))
R.sheet(tiles, 6).save(out)
print('wrote', out)
