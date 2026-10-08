import sys, numpy as np, houndrig as HR
from rigsim import skin
from sheet import render_parts
from PIL import Image, ImageDraw
m=HR.rigsim.load('hound','ultra')
tex=np.asarray(Image.open(HR.rigsim.PATCH+'textures/entity/wildlife/real/hound_redbone.png').convert('RGBA')).astype(np.float32)/255
sets=eval(sys.argv[1])
W,H=300,280
out=Image.new('RGB',(W*len(sets),H)); d=ImageDraw.Draw(out)
base=skin(m,HR.pose(m))[0]; cen=(base.max(0)+base.min(0))/2; size=np.max(base.max(0)-base.min(0))
for c,sv in enumerate(sets):
    HR.SIT.update(sv)
    G=HR.pose(m,sit=1); P,N=skin(m,G); T=m['T']
    part=(P[T].reshape(-1,3),m['UV'][T].reshape(-1,2),N[T].reshape(-1,3),tex)
    out.paste(render_parts([part],90,3,W,H,dist=size*1.4+0.2,center=cen),(c*W,0))
    d.text((c*W+4,4),str(sv),fill=(255,255,0)); 
    print(c, 'min y %.3f'%P[:,1].min())
out.save('/tmp/claude-0/trk/hound/grid.png')
