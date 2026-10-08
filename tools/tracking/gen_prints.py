"""[tracking] Procedural print atlas: textures/entity/track_prints.png
16 rows (PrintKind.Art order) x 6 columns: (crisp, soft, filled) x (shadow mask, rim mask). White RGB + alpha;
the client tints shadow / rim with the ground colour. Designed at 4x and box-downsampled to 32 px cells."""
import numpy as np, sys, os
from PIL import Image, ImageFilter
from scipy import ndimage as ndi
CELL=32; SS=4; N=CELL*SS
ROWS=['DEER','ELK','MOOSE','BISON','BOAR','CANID','FELINE','BEAR_FRONT','BEAR_HIND','BIRD','DUCK','RABBIT_HIND','RABBIT_FRONT','BOOT','FOX','BED']
ys,xs=np.mgrid[0:N,0:N]; X=(xs+0.5)/N*2-1; Y=(ys+0.5)/N*2-1   # y=-1 is the toe end (top of the cell)

def ell(cx,cy,rx,ry,rot=0.0):
    c,s=np.cos(rot),np.sin(rot); dx=X-cx; dy=Y-cy; u=(dx*c+dy*s)/rx; v=(-dx*s+dy*c)/ry
    r=np.sqrt(u*u+v*v); return (r-1)*min(rx,ry)          # approx signed distance
def poly(pts):
    P=np.array(pts,float); n=len(P); d=np.full(X.shape,1e9); inside=np.zeros(X.shape,bool)
    for i in range(n):
        a=P[i]; b=P[(i+1)%n]; ab=b-a
        t=np.clip(((X-a[0])*ab[0]+(Y-a[1])*ab[1])/(ab@ab),0,1)
        d=np.minimum(d,np.hypot(X-(a[0]+t*ab[0]),Y-(a[1]+t*ab[1])))
        cond=((a[1]>Y)!=(b[1]>Y))&(X<(b[0]-a[0])*(Y-a[1])/(b[1]-a[1]+1e-12)+a[0]); inside^=cond
    return np.where(inside,-d,d)
def cap(ax,ay,bx,by,r):
    ab=np.array([bx-ax,by-ay]); t=np.clip(((X-ax)*ab[0]+(Y-ay)*ab[1])/(ab@ab),0,1)
    return np.hypot(X-(ax+t*ab[0]),Y-(ay+t*ab[1]))-r
U=lambda *a: np.minimum.reduce(a)
def sub(a,b): return np.maximum(a,-b)
def mirror(f):   # f(sign) -> sdf of one side
    return np.minimum(f(-1),f(1))

def half_hoof(sx, w, top, L, tip=0.0, round_toe=False, bulb=0.5):
    """one toe of a cloven hoof: pointed (or rounded) tip in front, broad rounded heel bulb behind"""
    g=0.055                                   # half-gap between the two toes
    pts=[(sx*(g+0.01+tip*0.4), top)]
    for t in np.linspace(0.02,1,18):
        y=top+L*0.62*t
        x=g+(w-g)*(np.sin(np.pi/2*t)**(0.55 if round_toe else 0.85))
        pts.append((sx*x,y))
    pts.append((sx*g,top+L*0.62))
    toe=poly(pts)
    heel=ell(sx*(g+(w-g)*0.52), top+L*0.72, (w-g)*0.52, L*0.25*bulb/0.5)
    return np.minimum(toe,heel)

def shape(name, rng):
    if name=='DEER':
        s=mirror(lambda sx: half_hoof(sx,0.56,-0.84,1.62))
    elif name=='ELK':
        s=mirror(lambda sx: half_hoof(sx,0.66,-0.8,1.58,0.05,True,0.56))
    elif name=='MOOSE':
        s=mirror(lambda sx: half_hoof(sx,0.52,-0.88,1.28))
        s=U(s,ell(-0.34,0.72,0.11,0.15,0.3),ell(0.34,0.72,0.11,0.15,-0.3))
    elif name=='BISON':
        s=mirror(lambda sx: half_hoof(sx,0.8,-0.74,1.54,0.12,True,0.62))
    elif name=='BOAR':
        s=mirror(lambda sx: half_hoof(sx,0.48,-0.8,1.1,0.08,True,0.55))
        s=U(s,ell(-0.62,0.62,0.12,0.17,0.35),ell(0.62,0.62,0.12,0.17,-0.35))
    elif name in('CANID','FOX'):
        k=0.82 if name=='FOX' else 1.0
        heel=U(ell(0,0.42,0.34*k,0.26*k),ell(-0.16*k,0.55,0.16*k,0.14*k),ell(0.16*k,0.55,0.16*k,0.14*k))
        if name=='FOX': heel=sub(heel,cap(-0.3,0.46,0.3,0.46,0.035))   # the chevron bar
        toes=U(ell(-0.18,-0.34,0.17*k,0.23*k),ell(0.18,-0.34,0.17*k,0.23*k),ell(-0.5,-0.02,0.15*k,0.21*k,0.35),ell(0.5,-0.02,0.15*k,0.21*k,-0.35))
        claws=U(ell(-0.2,-0.72,0.05,0.08),ell(0.2,-0.72,0.05,0.08),ell(-0.62,-0.38,0.045,0.07,0.4),ell(0.62,-0.38,0.045,0.07,-0.4))
        s=U(heel,toes,claws)
    elif name=='FELINE':
        heel=ell(0,0.36,0.46,0.34)
        heel=sub(heel,ell(-0.17,0.72,0.07,0.1)); heel=sub(heel,ell(0.17,0.72,0.07,0.1))      # three-lobed rear edge
        heel=sub(heel,ell(0,0.0,0.08,0.06))                                                    # two-lobed leading edge
        toes=U(ell(-0.58,-0.1,0.16,0.2,0.45),ell(-0.2,-0.5,0.17,0.22,0.1),ell(0.22,-0.54,0.17,0.22,-0.1),ell(0.6,-0.16,0.16,0.2,-0.45))
        s=U(heel,toes)   # no claws: retracted
    elif name=='BEAR_FRONT':
        pad=poly([(-0.84,-0.14),(-0.5,-0.3),(0,-0.34),(0.5,-0.3),(0.84,-0.14),(0.8,0.16),(0.45,0.36),(0,0.42),(-0.45,0.36),(-0.8,0.16)])
        heel=ell(0.05,0.66,0.14,0.12)
        toes=U(*[ell(x,-0.46-0.08*(1-abs(x)),0.13,0.15) for x in (-0.76,-0.39,0.0,0.39,0.76)])
        claws=U(*[cap(x,-0.66-0.08*(1-abs(x)),x*1.08,-0.9-0.06*(1-abs(x)),0.03) for x in (-0.76,-0.39,0.0,0.39,0.76)])
        s=U(pad,heel,toes,claws)
    elif name=='BEAR_HIND':
        sole=U(ell(0.02,-0.2,0.44,0.34),ell(0,0.28,0.31,0.52),ell(0.0,0.02,0.36,0.4))
        toes=U(*[ell(x,-0.64-0.06*(1-abs(x/0.4)),0.085,0.1) for x in (-0.4,-0.2,0.0,0.2,0.4)])
        claws=U(*[cap(x,-0.76,x*1.05,-0.93,0.022) for x in (-0.4,-0.2,0.0,0.2,0.4)])
        s=U(sole,toes,claws)
    elif name=='BIRD':
        s=U(cap(0,0.3,-0.56,-0.5,0.07),cap(0,0.3,0,-0.82,0.075),cap(0,0.3,0.56,-0.5,0.07),cap(0,0.3,0,0.7,0.06),ell(0,0.28,0.14,0.14))
    elif name=='DUCK':
        web=poly([(-0.66,-0.52),(-0.3,-0.38),(0,-0.62),(0.3,-0.38),(0.66,-0.52),(0.16,0.4),(-0.16,0.4)])
        s=U(web,cap(0,0.42,-0.7,-0.58,0.07),cap(0,0.42,0,-0.84,0.075),cap(0,0.42,0.7,-0.58,0.07))
    elif name=='RABBIT_HIND':
        s=U(ell(0,0.14,0.28,0.66),ell(0,-0.52,0.24,0.22))
    elif name=='RABBIT_FRONT':
        s=U(ell(0,0.1,0.36,0.46),ell(0,-0.44,0.2,0.2))
    elif name=='BOOT':
        sole=poly([(-0.2,-0.84),(0.08,-0.86),(0.26,-0.72),(0.3,-0.2),(0.22,0.08),(-0.24,0.08),(-0.3,-0.3),(-0.3,-0.62)])
        heel=poly([(-0.24,0.3),(0.22,0.3),(0.24,0.8),(0,0.86),(-0.22,0.8)])
        s=U(sole,heel)
    elif name=='BED':
        s=ell(0,0,0.5,0.86)
    return s

def lugs():
    # boot lugs: raised blocks inside the sole that stay shallow
    L=np.zeros(X.shape)
    for gy in np.arange(-0.76,0.8,0.16):
        for gx in (-0.14,0.1):
            off=0.06 if int(round(gy*10))%2 else -0.02
            L=np.maximum(L,(np.abs(X-(gx+off))<0.07)&(np.abs(Y-gy)<0.045))
    return L

def layers(name, sdf, stage, seed):
    rng=np.random.default_rng(seed)
    soft=[0.05,0.08,0.13][stage]
    D=np.clip(-sdf/soft,0,1)**0.6                       # depression: steep walls, flat floor
    if name=='BOOT':
        D=D*(1-0.65*lugs())
    if name=='BED':
        n=ndi.gaussian_filter(rng.random(X.shape),6); n=(n-n.mean())/n.std()
        D=np.clip(-sdf/0.3,0,1)**0.8*(0.8+0.06*n)
    # ageing: walls slump, the floor fills, edges go ragged
    if stage>0:
        noise=ndi.gaussian_filter(rng.standard_normal(X.shape),SS*1.5); noise/=np.abs(noise).max()
        D=ndi.gaussian_filter(D,[0,1.1,2.3][stage]*SS)*(1+(0.05 if name=='BED' else 0.22)*stage*noise)
    D=np.clip(D*[1.0,0.8,0.55][stage],0,1)
    # pushed-up lip around the edge (snow / mud displaced by the foot)
    lip=np.exp(-((sdf-0.06)/0.05)**2)*[0.45,0.3,0.14][stage]
    lip=ndi.gaussian_filter(lip,SS*(0.5+stage))
    H=-D*0.16+lip*0.05
    gy,gx=np.gradient(H,2.0/N)
    n=np.dstack([-gx,-gy,np.ones_like(gx)]); n/=np.linalg.norm(n,axis=2,keepdims=True)
    Ld=np.array([-0.45,-0.55,0.7]); Ld/=np.linalg.norm(Ld)
    lam=(n@Ld)-Ld[2]                                      # relative to flat ground
    shadow=np.clip(-lam*2.4+0.42*D,0,1)
    rim=np.clip(lam*2.6,0,1)
    # thrown-out crumbs (soil / snow clods) near the toe
    crumbs=ndi.gaussian_filter((rng.random(X.shape)<0.003)*1.0,SS*0.55)*26*np.exp(-((sdf-0.13)/0.08)**2)*(Y<0.2)
    rim=np.clip(rim+np.clip(crumbs,0,0.6)*[1,0.5,0.0][stage],0,1)
    if name=='BED':
        rim*=0.5
    return shadow, rim

def down(a):
    return a.reshape(CELL,SS,CELL,SS).mean((1,3))

atlas=np.zeros((len(ROWS)*CELL,8*CELL,4))
atlas[...,:3]=1.0
for r,name in enumerate(ROWS):
    sdf=shape(name,None)
    for stage in range(3):
        sh,rm=layers(name,sdf,stage,r*10+stage)
        for c,m in ((stage*2,sh),(stage*2+1,rm)):
            atlas[r*CELL:(r+1)*CELL,c*CELL:(c+1)*CELL,3]=down(m)
out=sys.argv[1] if len(sys.argv)>1 else '/home/claude/work/tracking/patch/assets/frontierhunts/textures/entity/track_prints.png'
Image.fromarray((np.clip(atlas,0,1)*255).astype('uint8'),'RGBA').save(out,optimize=True)
print('wrote',out,atlas.shape)
