#!/usr/bin/env python3
"""[1.1.6] Fit a supplied illustration to a Handbook plate: centre-crop to the plate's aspect, resize with Lanczos.
usage: fit_plate.py <src image> <academy|field_school> <name>   (academy -> plate_<name>.png 1280x640;
field_school -> <name>.png 1280x640, welcome 1440x480)"""
import os
import sys

from PIL import Image

src, kind, name = sys.argv[1:4]
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
W, H = (1440, 480) if name == "welcome" else (1280, 640)
im = Image.open(src).convert('RGB')
a = W / H
w, h = im.size
if w / h > a:
    nw = round(h * a)
    im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
else:
    nh = round(w / a)
    im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
im = im.resize((W, H), Image.LANCZOS)
out = os.path.join(R, 'patch', 'assets', 'frontierhunts', 'textures', 'gui', kind, ('plate_' + name if kind == 'academy' else name) + '.png')
im.save(out, optimize=True)
print(out, im.size, os.path.getsize(out))
