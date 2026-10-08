#!/usr/bin/env python3
"""[gui] Wireframe PNGs of the settings-screen layout (boxes from LayoutDump rects), for eyeballing alignment.
usage: wireframe.py <classes dir> <out dir>   (run tools/gui/run.sh first; it compiles to /tmp/claude-0/gui-tools)"""
import subprocess, sys, os
from PIL import Image, ImageDraw
cls, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
COL = {'screen': (20, 22, 20), 'panel': (38, 40, 36), 'sidebar': (30, 32, 28), 'tab': (70, 74, 64), 'title': (90, 80, 50),
       'pill': (60, 110, 90), 'content': (44, 46, 42), 'reset': (90, 90, 90), 'done': (226, 140, 60), 'footleft': (60, 60, 80),
       'itemR': (56, 58, 54), 'itemL': (56, 58, 64), 'itemS': (80, 60, 40), 'itemC': (50, 50, 60), 'itemD': (50, 60, 50),
       'itemT': (60, 60, 60), 'itemI': (52, 52, 52), 'label': (200, 200, 190), 'desc': (120, 120, 110), 'control': (226, 140, 60),
       'card': (90, 90, 110), 'selector': (226, 140, 60)}
pages = ["Graphics preset", "Trees & world", "Wildlife", "Atmosphere", "Seasons", "Effects", "Sound", "Interface", "Aids", "Performance"]
for (w, h, tag) in [(427, 240, '854x480_gui2'), (285, 160, '854x480_gui3'), (214, 120, '854x480_gui4'), (960, 540, '1920x1080_gui2'), (320, 240, '640x480_gui2')]:
    for pi in (0, 1, 6):
        lines = subprocess.run(['java', '-cp', cls, 'LayoutDump', 'rects', str(w), str(h), str(pi)], capture_output=True, text=True).stdout.splitlines()
        S = 3 if w < 500 else 2
        im = Image.new('RGB', (w * S, h * S)); d = ImageDraw.Draw(im)
        for ln in lines:
            p = ln.split(' ')
            if len(p) < 5 or not p[1].lstrip('-').isdigit(): continue
            n, x, y, ww, hh = p[0], *map(int, p[1:5])
            c = COL.get(n, (255, 0, 255))
            outline = n in ('label', 'desc', 'control', 'card', 'selector', 'done', 'reset', 'pill', 'title', 'footleft')
            box = [x * S, y * S, (x + ww) * S - 1, (y + hh) * S - 1]
            if outline: d.rectangle(box, outline=c)
            else: d.rectangle(box, fill=c)
            if len(p) > 5: d.text((x * S + 3, y * S + 2), ' '.join(p[5:]), fill=(230, 230, 220))
        im.save(f'{out}/{tag}_{pi}_{pages[pi].split()[0].lower()}.png')
print('wrote', out)
