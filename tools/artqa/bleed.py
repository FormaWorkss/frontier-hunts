"""[artqa] Alpha bleeding for GUI textures that are sampled bilinearly (and mipmapped by GuiArtTexture).

Fully transparent texels written as RGB 0 (black) get averaged into the visible edge by bilinear filtering, which draws
a dark fringe around every shape (worst on light icons over the cream journal paper). This copies the nearest visible
colour into every fully transparent texel; alpha is untouched, so nothing changes where the image is opaque.

python3 tools/artqa/bleed.py <png> [<png> ...]          (in place)
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage


def bleed_array(a):
    a = a.copy()
    vis = a[..., 3] > 0
    if vis.all() or not vis.any():
        return a
    _, (iy, ix) = ndimage.distance_transform_edt(~vis, return_indices=True)
    hole = ~vis
    a[hole, :3] = a[iy[hole], ix[hole], :3]
    return a


def bleed_image(img):
    return Image.fromarray(bleed_array(np.asarray(img.convert('RGBA'))), 'RGBA')


if __name__ == '__main__':
    for p in sys.argv[1:]:
        bleed_image(Image.open(p)).save(p, optimize=True)
        print('bled', p)
