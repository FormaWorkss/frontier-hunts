"""[tracking] textures/entity/blood_trail_v4.png - 4x4 atlas of 256 px cells.
Grey cells (RGB = shading, alpha = coverage) are tinted per wound type and age by the client; cells 9 (stomach matter)
and 15 (froth bubbles) carry their own colour and only darken with age. Cells 0-3 are the v3 art converted to grey."""
import numpy as np, sys
from PIL import Image
from scipy import ndimage as ndi
C=256
V3='/tmp/claude-0/trk/jar/assets/frontierhunts/textures/entity/blood_trail_v3.png'
v3=np.asarray(Image.open(V3).convert('RGBA')).astype(float)/255
H=v3.shape[0]//2
out=np.zeros((5*C,4*C,4))
def put(i,cell):
    r,c=divmod(i,4); out[r*C:(r+1)*C,c*C:(c+1)*C]=cell
def grey(rgb_a):
    rgb=rgb_a[...,:3]; a=rgb_a[...,3]
    g=np.clip(rgb[...,0]/0.72,0,1.25)            # the painted red's shading -> 0..1.25 (highlights > 1 clip)
    g=np.clip(g,0,1)
    return np.dstack([g,g,g,a])
for i in range(4):
    r,c=divmod(i,2)
    cell=v3[r*H:(r+1)*H,c*H:(c+1)*H]
    im=Image.fromarray((cell*255).astype('uint8'),'RGBA').resize((C,C),Image.LANCZOS)
    put(i,grey(np.asarray(im).astype(float)/255))

ys,xs=np.mgrid[0:C,0:C]; X=(xs+0.5)/C*2-1; Y=(ys+0.5)/C*2-1
def blob(cx,cy,r,rng,rough=0.18,elong=1.0,rot=0.0):
    c,s=np.cos(rot),np.sin(rot); dx=X-cx; dy=Y-cy; u=(dx*c+dy*s)/elong; v=-dx*s+dy*c
    ang=np.arctan2(v,u); rr=np.hypot(u,v)
    edge=np.ones_like(ang)
    for k in (2,3,5,8):
        edge=edge+rough*0.55/k**0.6*rng.random()*np.sin(k*ang+rng.random()*6.28)
    edge=r*edge
    return np.clip((edge-rr)/(0.012+0.02*r),0,1)
def shade(a,rng,gloss=0.35,dark=0.72):
    # wet drop shading: darker body, lighter rim of the meniscus, a small specular glint
    inner=ndi.gaussian_filter(a,3)
    g=dark+0.18*np.clip(inner,0,1)+gloss*np.clip(ndi.gaussian_filter(a,1.2)-ndi.gaussian_filter(a,5),0,1)*2
    n=ndi.gaussian_filter(rng.standard_normal(a.shape),2)*0.05
    return np.clip(g+n,0,1)
def cell_of(a,g):
    return np.dstack([g,g,g,np.clip(a,0,1)])
rng=np.random.default_rng(7)

# 4 froth drip: a few round drops with foam texture
a=np.zeros((C,C))
for _ in range(5):
    a=np.maximum(a,blob(rng.normal(0,0.3),rng.normal(0,0.3),rng.uniform(0.08,0.2),rng))
for _ in range(14):
    a=np.maximum(a,blob(rng.normal(0,0.5),rng.normal(0,0.5),rng.uniform(0.015,0.04),rng,0.05))
put(4,cell_of(a,shade(a,rng,0.25,0.85)))
# 5 froth spray: fine mist of droplets, denser in a cone
a=np.zeros((C,C))
for _ in range(420):
    t=rng.normal(0,0.35); d=abs(rng.normal(0,0.55))
    a=np.maximum(a,blob(np.clip(t*(0.4+d),-0.9,0.9),np.clip(-0.7+d*1.4,-0.9,0.9),rng.uniform(0.008,0.035),rng,0.05))
put(5,cell_of(a,shade(a,rng,0.2,0.9)))
# 6 froth dense: a pooled patch with foam islands and a darker wet core
a=blob(0,0,0.36,rng,0.3)
for _ in range(14):
    a=np.maximum(a,blob(rng.normal(0,0.38),rng.normal(0,0.38),rng.uniform(0.04,0.13),rng))
n6=ndi.gaussian_filter(rng.standard_normal((C,C)),4); n6/=np.abs(n6).max()
g6=np.clip(shade(a,rng,0.3,0.62)+0.18*n6-0.12*np.clip(ndi.gaussian_filter(a,14)-0.5,0,1),0,1)
put(6,cell_of(a,g6))
# 7 bed stain: soaked, soft-edged irregular patch (blood wicked into the bedding)
n=ndi.gaussian_filter(rng.standard_normal((C,C)),12); n/=np.abs(n).max()
d=np.hypot(X/0.5,Y/0.8)
a=np.clip((1-d+0.35*n)/0.35,0,1)*0.85
spots=np.zeros((C,C))
for _ in range(12):
    spots=np.maximum(spots,blob(rng.normal(0,0.3),rng.normal(0,0.5),rng.uniform(0.03,0.08),rng))
a=np.maximum(a*0.7,spots)
put(7,cell_of(a,np.clip(0.55+0.25*n+0.2*spots,0,1)))
# 8 gut drops: few small irregular drops, thin
a=np.zeros((C,C))
for _ in range(4):
    a=np.maximum(a,blob(rng.normal(0,0.35),rng.normal(0,0.35),rng.uniform(0.04,0.1),rng,0.35))
put(8,cell_of(a*0.92,shade(a,rng,0.15,0.7)))
# 9 stomach matter flecks: coloured (browns, olive greens, pale fibre), NOT tinted
col=np.zeros((C,C,3)); a=np.zeros((C,C))
pal=np.array([[0.36,0.30,0.16],[0.42,0.40,0.18],[0.30,0.34,0.14],[0.52,0.44,0.28],[0.24,0.20,0.12],[0.60,0.56,0.40]])
for _ in range(36):
    m=blob(rng.normal(0,0.33),rng.normal(0,0.33),rng.uniform(0.012,0.04),rng,0.5,rng.uniform(1,2.6),rng.random()*3)
    k=pal[rng.integers(len(pal))]*(0.85+0.3*rng.random())
    col=col*(1-m[...,None])+k*m[...,None]; a=np.maximum(a,m)
put(9,np.dstack([np.clip(col,0,1),a]))
# 10 liver drops: round, dark, evenly sized, crown splatter
a=np.zeros((C,C))
for _ in range(3):
    cx,cy=rng.normal(0,0.25),rng.normal(0,0.25); r=rng.uniform(0.1,0.16)
    a=np.maximum(a,blob(cx,cy,r,rng,0.12))
    for k in range(9):
        t=rng.random()*6.28; a=np.maximum(a,blob(cx+np.cos(t)*r*1.5,cy+np.sin(t)*r*1.5,r*0.14,rng,0.05))
put(10,cell_of(a,shade(a,rng,0.35,0.66)))
# 11 muscle drip: teardrop drops thrown forward (direction of travel = -y)
a=np.zeros((C,C))
for _ in range(4):
    cx,cy=np.clip(rng.normal(0,0.3),-0.6,0.6),np.clip(rng.normal(0.1,0.3),-0.3,0.65)
    a=np.maximum(a,blob(cx,cy,rng.uniform(0.06,0.12),rng,0.1,1.0))
    a=np.maximum(a,blob(cx,cy-0.16,0.04,rng,0.05,0.5))
    a=np.maximum(a,blob(cx+rng.normal(0,0.03),cy-0.3,0.02,rng,0.05))
put(11,cell_of(a,shade(a,rng,0.4,0.74)))
# 12 brush smear: wiped streak, darker at the start (hair pushed through the leaves)
a=np.zeros((C,C))
for k in range(5):
    y0=rng.normal(0,0.2)
    band=np.exp(-((Y-y0-0.1*X)/rng.uniform(0.03,0.08))**2)*np.clip((0.8-np.abs(X+rng.normal(0,0.1)))/0.3,0,1)
    a=np.maximum(a,band*rng.uniform(0.6,1))
n=ndi.gaussian_filter(rng.random((C,C)),1.5)
a=np.clip(a*(0.6+0.8*n),0,1)
put(12,cell_of(a,np.clip(0.7+0.3*(X+1)/2,0,1)))
# 13 froth on brush: chest-height spray on leaves, many tiny droplets
a=np.zeros((C,C))
for _ in range(260):
    a=np.maximum(a,blob(rng.normal(0,0.4),rng.normal(0,0.3),rng.uniform(0.006,0.03),rng,0.05))
put(13,cell_of(a,shade(a,rng,0.2,0.92)))
# 14 arterial spurt: a line of drops and streaks
a=np.zeros((C,C))
for k in range(16):
    t=k/15.0; cx=-0.75+1.5*t+rng.normal(0,0.03); cy=0.25*np.sin(t*3)+rng.normal(0,0.04)
    a=np.maximum(a,blob(cx,cy,rng.uniform(0.02,0.07)*(1.3-t*0.5),rng,0.1,1.8,0.2))
put(14,cell_of(a,shade(a,rng,0.4,0.8)))
# 15 froth bubbles: coloured overlay - small bright pinkish-white bubbles with dark rims
col=np.zeros((C,C,3)); a=np.zeros((C,C))
for _ in range(170):
    cx,cy=rng.normal(0,0.33),rng.normal(0,0.33); r=rng.uniform(0.008,0.03)
    rr=np.hypot(X-cx,Y-cy)
    ring=np.clip(1-np.abs(rr-r)/(0.006+r*0.2),0,1); fill=np.clip((r-rr)/0.006,0,1)
    glint=np.clip(1-np.hypot(X-cx+r*0.35,Y-cy+r*0.35)/(r*0.35+0.003),0,1)
    m=np.maximum(ring,fill*0.55)
    c=np.array([0.98,0.78,0.80])*(0.75+0.25*fill[...,None])+glint[...,None]*0.3
    col=col*(1-m[...,None])+np.clip(c,0,1)*m[...,None]; a=np.maximum(a,m*0.9)
put(15,np.dstack([np.clip(col,0,1),a]))
def bubbles(mask,count,rmax,seed):
    rg=np.random.default_rng(seed)
    col=np.zeros((C,C,3)); al=np.zeros((C,C))
    m0=ndi.grey_erosion(mask,size=3)
    pts=np.argwhere(m0>0.6)
    if len(pts)==0: return np.dstack([col,al])
    for _ in range(count):
        py,px=pts[rg.integers(len(pts))]; cx=(px+0.5)/C*2-1; cy=(py+0.5)/C*2-1; r=rg.uniform(0.005,rmax)
        rr=np.hypot(X-cx,Y-cy)
        ring=np.clip(1-np.abs(rr-r)/(0.005+r*0.25),0,1); fill=np.clip((r-rr)/0.005,0,1)
        glint=np.clip(1-np.hypot(X-cx+r*0.35,Y-cy+r*0.35)/(r*0.35+0.003),0,1)
        m=np.maximum(ring*0.9,fill*0.45)
        c=np.array([1.0,0.80,0.82])*(0.7+0.3*fill[...,None])+glint[...,None]*0.35
        col=col*(1-m[...,None])+np.clip(c,0,1)*m[...,None]; al=np.maximum(al,m)
    return np.dstack([np.clip(col,0,1),al*np.clip(mask*1.2,0,1)])
def cellA(i):
    r,c=divmod(i,4); return out[r*C:(r+1)*C,c*C:(c+1)*C,3].copy()
put(16,bubbles(cellA(4),60,0.022,1))
put(17,bubbles(cellA(5),140,0.012,2))
put(18,bubbles(cellA(6),260,0.03,3))
put(19,bubbles(cellA(13),120,0.012,4))
o=sys.argv[1] if len(sys.argv)>1 else '/home/claude/work/tracking/patch/assets/frontierhunts/textures/entity/blood_trail_v4.png'
Image.fromarray((np.clip(out,0,1)*255).astype('uint8'),'RGBA').save(o,optimize=True)
print('wrote',o)
