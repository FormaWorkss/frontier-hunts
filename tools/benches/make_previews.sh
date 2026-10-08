#!/bin/sh
# [benchart] regenerate every preview PNG into tools/benches/previews/ (and a copy in /tmp/claude-0/benchart-previews/)
set -e
R=$(cd "$(dirname "$0")/../.." && pwd)
P=$R/tools/benches/previews
mkdir -p $P
for b in frontier_workbench gunsmith_bench reloading_bench; do
  python3 $R/tools/benches/preview_bench.py $R $b $P/${b}_views.png --views front,fl,fr,back
  python3 $R/tools/benches/preview_bench.py $R $b $P/${b}_closeup.png --views=-20:22 --w 900 --h 700 --dist 40 --cols 1
  python3 $R/tools/benches/preview_bench.py $R $b $P/${b}_gui.png --gui
done
python3 - "$P" <<'PY'
import sys
from PIL import Image
P = sys.argv[1]
ims = [Image.open(f'{P}/{b}_gui.png') for b in ('frontier_workbench', 'gunsmith_bench', 'reloading_bench')]
w, h = ims[0].size
o = Image.new('RGB', (w, h * 3))
for i, im in enumerate(ims):
    o.paste(im, (0, i * h))
o.save(f'{P}/inventory_icons.png')
o.resize((w * 3, h * 9), Image.NEAREST).save(f'{P}/inventory_icons_x3.png')
PY
rm -f $P/*_gui.png
python3 $R/tools/benches/emblems.py $R $P/emblems.png
python3 $R/tools/benches/lineup.py $R $P/lineup.png
mkdir -p /tmp/claude-0/benchart-previews
cp $P/*.png /tmp/claude-0/benchart-previews/
ls -la $P
