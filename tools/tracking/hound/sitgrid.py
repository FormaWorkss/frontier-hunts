import sys, numpy as np, houndrig as HR
from rigsim import skin
from sheet import render_parts
from PIL import Image, ImageDraw
m=HR.rigsim.load('hound','ultra')
tex=np.asarray(Image.open(HR.rigsim.PATCH+'textures/entity/wildlife/real/hound_redbone.png').convert('RGBA')).astype(np.float32)/255
import math
variants=eval(sys.argv[1])
W,H=340,300
out=Image.new('RGB',(W*len(variants),H)); d=ImageDraw.Draw(out)
base=None
for c,v in enumerate(variants):
    orig=HR.pose
    G=HR.pose(m,**v); P,N=skin(m,G); T=m['T']
    part=(P[T].reshape(-1,3),m['UV'][T].reshape(-1,2),N[T].reshape(-1,3),tex)
    if base is None: base=skin(m,HR.pose(m))[0]
    cen=(base.max(0)+base.min(0))/2; size=np.max(base.max(0)-base.min(0))
    out.paste(render_parts([part],90,3,W,H,dist=size*1.4+0.2,center=cen),(c*W,0))
    d.text((c*W+4,4),str(v)[:50],fill=(255,255,0))
out.save('/tmp/claude-0/trk/hound/grid.png')
