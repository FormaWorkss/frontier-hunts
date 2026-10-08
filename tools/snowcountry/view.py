#!/usr/bin/env python3
"""[1.1.9] Top-down preview of snow country from SnowHarness output: hillshaded terrain, green country in its biome
colours, snow country white (blue-white for snowy pine forest), water blue.  usage: view.py <in.bin> <out.png>"""
import struct
import sys

import numpy as np
from PIL import Image

d = open(sys.argv[1], 'rb').read()
n, step = struct.unpack_from('>ii', d, 0)
dt = np.dtype([('b', '>i2'), ('g', '>f4'), ('s', '>f4'), ('w', '>i4'), ('wt', '>f4'), ('dec', 'i1')])
A = np.frombuffer(d, dt, n * n, 8).reshape(n, n)
g = A['g'].astype(float)
b = A['b'].astype(int)
water = A['w'].astype(float) > g
gy, gx = np.gradient(g)
shade = np.clip(0.8 + (-gx - gy) / step * 1.1, 0.35, 1.25)
rng = np.random.RandomState(3)
pal = rng.randint(70, 170, (40, 3)).astype(float) * np.array([0.8, 1.0, 0.7])
img = pal[np.clip(b, 0, 39)] * shade[..., None]
dec = A['dec']
img[dec == 1] = np.array([214, 226, 236]) * shade[dec == 1][..., None]
img[dec == 2] = np.array([246, 248, 252]) * shade[dec == 2][..., None]
img[b == 5] = np.array([236, 240, 248]) * shade[b == 5][..., None]
img[water] = (40, 80, 180)
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(sys.argv[2])
land = ~water
sc = (dec > 0) | (b == 5)
print('snow on %.1f%% of land; inside snow country (weight>0.9) %.1f%% white' % (100 * sc[land].mean(), 100 * sc[land & (A['wt'] > 0.9)].mean()))
