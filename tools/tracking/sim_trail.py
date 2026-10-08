"""Top-down check of TrailClient's print layout maths: marks along a curving path, rendered like the client does."""
import numpy as np, math, sys
from PIL import Image
A=np.asarray(Image.open('/home/claude/work/tracking/patch/assets/frontierhunts/textures/entity/track_prints.png')).astype(float)/255
ART=['DEER','ELK','MOOSE','BISON','BOAR','CANID','FELINE','BEAR_FRONT','BEAR_HIND','BIRD','DUCK','RABBIT_HIND','RABBIT_FRONT','BOOT','FOX','BED']
PPM=400  # pixels per metre
W,H=1600,1000
img=np.ones((H,W,3))*np.array([0.93,0.95,0.99])
SH=np.array([0.60,0.67,0.82]); RM=np.array([1,1,1.0])
def quad(cx,cz,fx,fz,rx,rz,len_,art,stage,mirror,alpha=1.0):
    h=len_*0.625
    # sample texture over the quad's pixel bounding box (world x -> image x, world z -> image y downward)
    corners=[(cx+sx*rx*h+sf*fx*h, cz+sx*rz*h+sf*fz*h) for sx in (-1,1) for sf in (-1,1)]
    xs=[c[0] for c in corners]; zs=[c[1] for c in corners]
    x0=int(min(xs)*PPM)-1; x1=int(max(xs)*PPM)+1; z0=int(min(zs)*PPM)-1; z1=int(max(zs)*PPM)+1
    for py in range(max(z0,0),min(z1,H)):
        for px in range(max(x0,0),min(x1,W)):
            wx=px/PPM-cx; wz=py/PPM-cz
            a=(wx*rx+wz*rz)/h; b=(wx*fx+wz*fz)/h     # a: right, b: forward
            if abs(a)>1 or abs(b)>1: continue
            u=(a+1)/2; v=(1-b)/2      # v=0 at +forward (toe)
            if mirror: u=1-u
            for layer,col in ((0,SH),(1,RM)):
                cu=int((stage*2+layer+u)*32); cv=int((art+v)*32)
                cu=min(cu,(stage*2+layer)*32+31); cv=min(cv,art*32+31)
                al=A[cv,cu,3]*alpha*(0.92 if layer==0 else 0.8)
                img[py,px]=img[py,px]*(1-al)+col*al
def pr(ox,oz,fx,fz,rx,rz,x,z,len_,turn,mirror,art,stage=0,alpha=1):
    c,s=math.cos(turn),math.sin(turn)
    pfx=fx*c+rx*s; pfz=fz*c+rz*s; prx=rx*c-fx*s; prz=rz*c-fz*s
    quad(ox+rx*x+fx*z, oz+rz*x+fz*z, pfx,pfz,prx,prz,len_,art,stage,mirror,alpha)
def mark(ox,oz,yaw,layout,art,L,S,run=False,stage=0):
    y=math.radians(yaw); fx=-math.sin(y); fz=math.cos(y); rx=-math.cos(y); rz=-math.sin(y)
    if layout=='PAIRED':
        gw=max(L*0.9,0.06)
        if not run:
            pr(ox,oz,fx,fz,rx,rz,-gw,-S*0.25,L,-0.1,False,art,stage); pr(ox,oz,fx,fz,rx,rz,gw,S*0.25,L,0.1,True,art,stage)
            pr(ox,oz,fx,fz,rx,rz,-gw+L*0.08,-S*0.25+L*0.22,L*0.97,-0.05,False,art,stage,0.55); pr(ox,oz,fx,fz,rx,rz,gw-L*0.06,S*0.25+L*0.2,L*0.97,0.05,True,art,stage,0.55)
        else:
            pr(ox,oz,fx,fz,rx,rz,-gw*0.5,-L*1.4,L*1.1,-0.05,False,art,stage); pr(ox,oz,fx,fz,rx,rz,gw*0.4,-L*2.8,L*1.1,0.08,True,art,stage)
            pr(ox,oz,fx,fz,rx,rz,-gw*1.4,L*1.3,L*1.15,-0.28,False,art,stage); pr(ox,oz,fx,fz,rx,rz,gw*1.4,L*1.6,L*1.15,0.3,True,art,stage)
    elif layout=='BEAR':
        gw=L*0.65; s=S*0.25
        pr(ox,oz,fx,fz,rx,rz,-gw,-s-L*0.25,L*0.62,-0.2,False,7,stage); pr(ox,oz,fx,fz,rx,rz,-gw*0.95,-s+L*0.45,L,-0.12,False,8,stage)
        pr(ox,oz,fx,fz,rx,rz,gw,s-L*0.25,L*0.62,0.2,True,7,stage); pr(ox,oz,fx,fz,rx,rz,gw*0.95,s+L*0.45,L,0.12,True,8,stage)
    elif layout=='LINE':
        gw=L*0.35
        pr(ox,oz,fx,fz,rx,rz,-gw,-S*0.25,L,-0.05,False,art,stage); pr(ox,oz,fx,fz,rx,rz,gw,S*0.25,L,0.05,True,art,stage)
    elif layout=='BIPED':
        pr(ox,oz,fx,fz,rx,rz,-0.11,-S*0.25,L,-0.08,False,art,stage); pr(ox,oz,fx,fz,rx,rz,0.11,S*0.25,L,0.08,True,art,stage)
# deer walking north (yaw 180 = north: -z) up the left side
for i in range(4):
    mark(0.6, 2.2-i*0.4*1.1*0+ -i*1.1*0.0 + 0.0, 180, 'PAIRED',0,0.075*1.35,1.1) if False else None
def trail(x0,z0,yaw,layout,art,L,S,n,run=False,stage=0):
    y=math.radians(yaw); fx=-math.sin(y); fz=math.cos(y)
    for i in range(n):
        mark(x0+fx*S*i, z0+fz*S*i, yaw, layout, art, L, S, run, stage)
trail(0.4,2.3,180,'PAIRED',0,0.075*1.35,1.1*0.5,4)           # deer walking north (stride shortened to fit)
trail(1.1,2.3,180,'PAIRED',0,0.075*1.35,1.1*0.5,3,True)      # deer bounding north
trail(1.9,0.2,0,'LINE',5,0.115*1.35,0.6,4)                   # wolf walking south
trail(2.7,2.3,180,'BEAR',8,0.27*1.35,0.8,3)                  # grizzly north
trail(3.4,0.2,0,'BIPED',13,0.29,0.55,4)                      # boots south
Image.fromarray((np.clip(img,0,1)*255).astype('uint8')).save(sys.argv[1])
