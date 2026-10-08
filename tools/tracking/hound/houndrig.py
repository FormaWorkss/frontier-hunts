"""Python mirror of HoundRig.java (the in-game hound animation) for offline pose sheets."""
import sys, math, numpy as np
sys.path.insert(0,'/home/claude/fh/ultra')
import rigsim
from rigsim import R, skin
rigsim.PATCH='/home/claude/work/hound2/patch/assets/frontierhunts/'
from sheet import render_parts
from PIL import Image, ImageDraw
SIT=dict(pel=0.85,neck=-0.3,head=-0.3,thigh=0.8,shin=-1.3,foot=0.9,dy=0.415,tail=-0.45)
LEGS={'fl':['fl_upper','fl_lower','fl_foot','fl_toe'],'fr':['fr_upper','fr_lower','fr_foot','fr_toe'],'bl':['bl_upper','bl_lower','bl_foot','bl_toe'],'br':['br_upper','br_lower','br_foot','br_toe']}
def pose(m, ls=0.0, amt=0.0, age=0.0, hy=0.0, hp=0.0, sniff=0.0, sit=0.0, bay=0.0, tuck=0.0, wag=0.0, flee=0.0, water=False):
    ix={n:i for i,n in enumerate(m['names'])}; nb=len(ix)
    rot=np.array(m['tail'],float).copy(); H,legLen,belly,yawfix,feedA,stride,pitchfix,feedDrop=m['meta']
    a=min(1.0,amt*1.4); phi=ls*stride; dy=0.0
    def add(n,x=0,y=0,z=0):
        if n in ix: rot[ix[n]]+=(x,y,z)
    gal=flee*min(1,a*1.2); A=0.32*a
    PH={'fl':0,'fr':math.pi,'bl':math.pi,'br':0}; PHG={'fl':0,'fr':0.5,'bl':math.pi,'br':math.pi+0.5}
    for l in ('fl','fr','bl','br'):
        p=phi+PH[l]*(1-gal)+PHG[l]*gal; sw=math.sin(p); lift=max(0,math.cos(p)); amp=A*(1+0.45*gal); n=LEGS[l]
        add(n[0],amp*sw)
        if l[0]=='f': add(n[1],0.2*a*lift); add(n[2],-0.8*a*lift)
        else: add(n[1],-0.35*a*lift); add(n[2],0.55*a*lift)
        add(n[3],-0.5*amp*sw)
    dy-=0.012*H*a*(1-math.cos(2*phi))/2
    add('spine',0.1*gal*math.sin(phi)); add('chest',-0.06*gal*math.sin(phi)); add('head',0.04*a*math.sin(2*phi))
    add('spine',0.012*math.sin(age*0.12))
    # tail: a hound carries it up like a sabre, higher when working; wags side to side
    # [hound2] the mesh's rest tail is already carried up in a sabre curve: only working / baying lift it, slinking clamps it
    carry=0.22*sniff+0.15*bay-1.45*tuck
    add('tail1',-carry+0.08*a*math.cos(phi)*(1-sniff)); add('tail2',-0.3*carry)
    add('tail1',y=wag*0.55*math.sin(age*1.55)+0.08*math.sin(age*0.1)); add('tail2',y=wag*0.35*math.sin(age*1.55-0.9))
    hyr=max(-60,min(60,hy))*math.pi/180*(1-sniff*0.6); hpr=max(-40,min(40,hp))*math.pi/180*(1-sniff)
    add('neck',0.5*pitchfix,0.5*yawfix-0.5*hyr); add('head',-0.8*hpr+0.5*pitchfix,0.5*yawfix-0.5*hyr)
    # sniff: nose to the ground (the baked grazing pose), sweeping side to side
    if sniff>0:
        rot+=sniff*0.85*m['feed']; dy-=sniff*0.85*feedDrop
        add('neck',0,sniff*0.22*math.sin(age*0.32),0); add('head',0.05*sniff*math.sin(age*0.9),sniff*0.18*math.sin(age*0.32+0.6),0)
    # bay: head thrown up, chest out
    add('neck',0.55*bay); add('head',0.45*bay); add('chest',-0.08*bay)
    # tuck: slinking back to the owner
    add('neck',-0.25*tuck); add('head',-0.2*tuck); dy-=0.05*H*tuck
    if sit>0:
        s=sit
        add('pelvis',SIT['pel']*s)                       # body tips up at the hips
        add('neck',SIT['neck']*s); add('head',SIT['head']*s)
        for l in ('fl','fr'):
            n=LEGS[l]; add(n[0],-SIT['pel']*s)
        for l in ('bl','br'):
            n=LEGS[l]; add(n[0],SIT['thigh']*s); add(n[1],SIT['shin']*s); add(n[2],SIT['foot']*s)
        add('tail1',SIT['tail']*s); add('tail2',-0.15*s)
        dy-=SIT['dy']*H*s
    # [hound2] long leathers hang with gravity: counter the head's pitch (relative to rest) and swing with the gait
    if 'ear_l' in ix:
        rest=np.array(m['tail'],float)
        pitch=sum(rot[ix[n],0]-rest[ix[n],0] for n in ('pelvis','spine','chest','neck','head') if n in ix)
        ex=max(-1.3,min(1.3,-0.9*pitch))
        sw=0.10*a*math.sin(phi)+0.03*math.sin(age*0.11)
        add('ear_l',ex+0.5*sw,0,-(0.04+0.06*a*abs(math.sin(phi))+0.1*bay)); add('ear_r',ex-0.5*sw,0,(0.04+0.06*a*abs(math.sin(phi))+0.1*bay))
    if water:
        dy-=0.28*H
    G=[None]*nb
    for i in range(nb):
        h=m['head'][i]; L=np.eye(4); L[:3,:3]=R(*rot[i]); L[:3,3]=h-L[:3,:3]@h
        G[i]=(np.eye(4)+np.diag([0,0,0,0])) if False else None
        if m['par'][i]<0:
            T=np.eye(4); T[1,3]=dy; G[i]=T@L
        else: G[i]=G[m['par'][i]]@L
    return G
if __name__=='__main__':
    kind=sys.argv[1] if len(sys.argv)>1 else 'redbone'
    lod=sys.argv[2] if len(sys.argv)>2 else 'ultra'
    m=rigsim.load('hound',lod)
    tex=np.asarray(Image.open(rigsim.PATCH+f'textures/entity/wildlife/real/hound_{kind}{"" if lod=="ultra" else "_far"}.png').convert('RGBA')).astype(np.float32)/255
    P=[('stand',{}),('trot',dict(ls=0.9,amt=0.8)),('gallop',dict(ls=0.6,amt=1.0,flee=1)),('sniff',dict(sniff=1,wag=1,age=5)),('sniff walk',dict(sniff=1,wag=1,ls=1.2,amt=0.4,age=9)),
       ('sit',dict(sit=1,wag=0.3)),('bay',dict(bay=1,wag=0.6)),('sit bay',dict(sit=1,bay=1)),('tuck',dict(tuck=1,ls=0.5,amt=0.8))]
    W,Hh=300,230
    out=Image.new('RGB',(W*len(P),Hh*2)); d=ImageDraw.Draw(out)
    base=None
    for c,(nm,kw) in enumerate(P):
        G=pose(m,**kw); Pp,Nn=skin(m,G); T=m['T']
        part=(Pp[T].reshape(-1,3),m['UV'][T].reshape(-1,2),Nn[T].reshape(-1,3),tex)
        if base is None: base=Pp
        cen=(base.max(0)+base.min(0))/2; size=np.max(base.max(0)-base.min(0))
        out.paste(render_parts([part],90,5,W,Hh,dist=size*1.5+0.2,center=cen),(c*W,0))
        out.paste(render_parts([part],35,12,W,Hh,dist=size*1.5+0.2,center=cen),(c*W,Hh))
        d.text((c*W+4,4),nm,fill=(255,255,0))
    out.save(sys.argv[3] if len(sys.argv)>3 else '/tmp/claude-0/trk/hound/rig.png')
