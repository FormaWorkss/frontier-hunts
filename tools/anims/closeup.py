"""[anims] Before/after close-ups (head and 3/4, sun and Minecraft lighting) of one species' Ultra coat and body.
usage: closeup.py <species> <out.png> [before mesh dir] [before tex dir]"""
import sys, os, subprocess, io
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'meshes'))
import render as R  # noqa: E402
sp, out = sys.argv[1], sys.argv[2]
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))


def git(path):
    return subprocess.run(['git', '-C', REPO, 'show', '6cbba9d:' + path], check=True, capture_output=True).stdout


import fhsk  # noqa: E402  (render put tools/wingshot on the path)
before_m = fhsk.parse(git('patch/assets/frontierhunts/models/wildlife/%s_ultra.fhsk' % sp))
before_t = np.asarray(Image.open(io.BytesIO(git('patch/assets/frontierhunts/textures/entity/wildlife/real/%s.png' % sp))).convert('RGB')).astype(np.float32) / 255
after_m = R.load_mesh(sp + '_ultra'); after_t = R.load_tex(R.coat_for(sp))
tiles = []
for tag, m, t in (('before', before_m, before_m and before_t), ('after', after_m, after_t)):
    v = R.views_for(m.pos)
    hj = m.joint[m.names.index('head')]
    H = m.pos[:, 1].max()
    tg = np.array([0.0, hj[1] - 0.04 * H, hj[2] - 0.08 * H])
    v['head'] = (tg + np.array([0.55, 0.12, -0.55]) * H * 0.75, tg)
    v['face'] = (tg + np.array([0.12, 0.08, -0.8]) * H * 0.75, tg)
    for nm, mode in (('head', 'sun'), ('face', 'mc'), ('head', 'clay'), ('3/4', 'sun')):
        eye, tg = v[nm]
        img, _ = R.render(m.pos, m.nrm, m.uv, m.tris, t, eye, tg, size=(400, 300), mode=mode)
        tiles.append(R.label(R.to_img(img), '%s %s %s %s' % (sp, tag, nm, mode)))
R.sheet(tiles, 4).save(out)
print('wrote', out)
