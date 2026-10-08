#!/usr/bin/env python3
"""[polish] 32x32 inventory icons for the ecology bone finds, rendered from their own block models.

The first icons were hand-placed 16x16 sprites that ran edge to edge with no outline, so in the creative tab they looked
bigger, flatter and noisier than everything around them. These are drawn from the actual Blockbench models
(models/block/bones/*_plain.json + their Classic textures) at an icon-friendly angle with Minecraft's GUI item lighting,
supersampled 4x4, then given the mod's 1px warm outline and a 1px margin, like the other 32x32 item icons.

    python3 tools/polish/bone_icons.py [jar]      (run from the repo root; jar = a built Frontier Hunts jar for parents)
Writes patch/assets/frontierhunts/textures/item/{shed_antler,whitetail_skull,elk_skull,moose_skull,bison_skull,
scattered_bones}.png and a preview to /tmp/claude-0/bone_icons_preview.png.
"""
import glob, json, os, shutil, sys, tempfile
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import icon_preview as ip  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts', 'textures', 'item')
OUTLINE = np.array([22, 16, 12], np.float32)

# icon name -> (block model, view rotation, largest extent in icon px before the outline)
ICONS = {
    'shed_antler': ('shed_antler_plain', [40, 150, 0], 28.0),
    'whitetail_skull': ('whitetail_skull_plain', [25, 180, 0], 28.0),
    'elk_skull': ('elk_skull_plain', [30, 135, 0], 27.5),
    'moose_skull': ('moose_skull_plain', [50, 180, 0], 28.5),  # [artqa] seen from above so the palms read (edge-on they looked like elk tines)
    'bison_skull': ('bison_skull_plain', [25, 180, 0], 28.0),
    'scattered_bones': ('scattered_bones_ribs_plain', [40, 150, 0], 28.0),
}
SS = 4  # supersampling per icon pixel


def build(src, tmp, name, model, rot, extent):
    mid = 'frontierhunts:block/bones/' + model
    g = ip.fit(src, mid, rot, extent / 2.0)  # fit() works in slot px (16 per slot); the icon has 32 px
    os.makedirs(os.path.join(tmp, 'assets', 'frontierhunts', 'models', 'item'), exist_ok=True)
    json.dump({'parent': mid, 'display': {'gui': g}},
              open(os.path.join(tmp, 'assets', 'frontierhunts', 'models', 'item', '_icon_' + name + '.json'), 'w'))
    img, _ = ip.render_item(src, '_icon_' + name, 2 * SS)  # 16 slot px * 2 * SS = 32 * SS
    a = img[..., 3]
    n = 32
    A = a.reshape(n, SS, n, SS).mean(axis=(1, 3))
    C = (img[..., :3] * a[..., None]).reshape(n, SS, n, SS, 3).sum(axis=(1, 3))
    cnt = a.reshape(n, SS, n, SS).sum(axis=(1, 3))
    C = C / np.maximum(cnt, 1e-6)[..., None]
    solid = A >= 0.2
    # pixel-art finish: a touch more contrast, then a darker rim on the lower-right inside edge for depth
    C = np.clip((C - 0.5) * 1.12 + 0.5, 0, 1)
    out = np.zeros((n, n, 4), np.float32)
    out[solid, :3] = C[solid] * 255
    out[solid, 3] = 255
    pad = np.pad(solid, 1)
    for y in range(n):
        for x in range(n):
            if solid[y, x]:
                if not pad[y + 2, x + 1] or not pad[y + 1, x + 2]:
                    out[y, x, :3] *= 0.82
                continue
            if pad[y, x + 1] or pad[y + 2, x + 1] or pad[y + 1, x] or pad[y + 1, x + 2]:
                out[y, x, :3] = OUTLINE
                out[y, x, 3] = 255
    im = Image.fromarray(out.astype(np.uint8), 'RGBA')
    bb = im.getbbox()
    # centre the silhouette exactly (fit() centres the projected model, the outline/threshold can shift it by a px)
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    canvas = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    canvas.paste(im.crop(bb), ((32 - w) // 2, (32 - h) // 2))
    return canvas


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.join(ROOT, '.build', '*.jar')))[-1]
    tmp = tempfile.mkdtemp()
    try:
        src = ip.Src(tmp, os.path.join(ROOT, 'patch'), jar)
        prev = Image.new('RGBA', (len(ICONS) * 140, 140), (139, 139, 139, 255))
        for i, (name, (model, rot, ext)) in enumerate(ICONS.items()):
            icon = build(src, tmp, name, model, rot, ext)
            icon.save(os.path.join(OUT, name + '.png'))
            prev.alpha_composite(icon.resize((128, 128), Image.NEAREST), (i * 140 + 6, 6))
            print(name, icon.getbbox())
        os.makedirs('/tmp/claude-0', exist_ok=True)
        prev.save('/tmp/claude-0/bone_icons_preview.png')
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    main()
