import sys,json; sys.path.insert(0,'/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad'); from lib import *
from grains import onsets
OUT='/home/claude/work/calls/patch/assets/frontierhunts/sounds/'
rng=np.random.default_rng(7)
LOG=[]   # (file, source, processing)
SRC={}
def src(n):
    if n not in SRC: SRC[n]=load(n)
    return SRC[n]
def clean(n,a,b,noise=None,hpf=120,lpf=None,red=22,k=1.6,pad=0.0):
    """cut [a,b] of source n, highpass, spectral-gate denoise with a noise-only clip, optional lowpass"""
    x=src(n); seg=cut(x,max(0,a-pad),b+pad)
    seg=hp(seg,hpf)
    if noise is not None:
        nz=hp(cut(x,*noise),hpf); seg=denoise(seg,nz,red_db=red,k=k)
    if lpf: seg=lp(seg,lpf)
    return seg
def emit(rel,x,target,src_desc,proc,fi=0.008,fo=0.06):
    x=fade(x,fi,fo); x=norm(x,target)
    writeogg(x,OUT+rel+'.ogg',q=5); LOG.append((rel,src_desc,proc,round(mmax(x),1),round(len(x)/SR,2)))
    return x

# ======================================================================== antler rattling (real clacks)
EF='fh_elk_fight'; EF_N=(100.0,108.0)
ef=src(EF); efn=hp(cut(ef,*EF_N),250)
def grain(t,dur=0.24,ratio=1.0):
    g=hp(cut(ef,t-0.012,t+dur),250); g=denoise(g,efn,red_db=26,k=1.4,nfft=1024)
    g=fade(g,0.003,0.08)
    return resamp(g,ratio) if ratio!=1.0 else g
on=[o for o in onsets(ef) if o[1]>=20 and o[2]>=-33]
strong=sorted(on,key=lambda o:-o[2])
print('grains',len(on))
def cluster(a,b,ratio=1.2):
    c=hp(cut(ef,a,b),250); c=denoise(c,efn,red_db=24,k=1.4,nfft=1024); c=fade(c,0.01,0.05)
    return resamp(c,ratio)
CL=[(3.30,4.60),(73.15,74.05),(75.60,76.55),(114.15,116.35),(87.05,88.25),(89.70,90.60),(140.5,141.7),(160.6,161.9),(46.25,47.1),(64.5,64.95)]
def rattle(kind,seed):
    r=np.random.default_rng(seed); parts=[]
    if kind=='tickle':
        t=0.05
        for burst in range(3):
            for i in range(r.integers(5,9)):
                o=on[r.integers(len(on))]; parts.append((t,grain(o[0],0.16,r.uniform(1.25,1.45)),r.uniform(0.25,0.55))); t+=r.uniform(0.06,0.16)
            t+=r.uniform(0.45,0.8)
    else:
        t=0.0
        hard = kind in ('hard','brush')
        # opening smash: two strongest-type clacks
        for i in range(2 if hard else 1):
            o=strong[r.integers(0,12)]; parts.append((t,grain(o[0],0.3,r.uniform(1.12,1.25)),1.0)); t+=r.uniform(0.09,0.16)
        t+=0.05
        a,b=CL[r.integers(len(CL))]; c=cluster(a,b,r.uniform(1.15,1.3)); parts.append((t,c,0.9 if hard else 0.7)); t+=len(c)/SR*0.85
        a,b=CL[r.integers(len(CL))]; c=cluster(a,b,r.uniform(1.15,1.3)); parts.append((t,c,0.8 if hard else 0.55)); t+=len(c)/SR
        t+=r.uniform(0.3,0.5)
        for i in range(r.integers(4,8)):
            o=on[r.integers(len(on))]; parts.append((t,grain(o[0],0.18,r.uniform(1.2,1.4)),r.uniform(0.35,0.7))); t+=r.uniform(0.07,0.15)
        if kind=='brush':
            bx=clean('fh_elk_bush',9.0,10.6,noise=(45.0,46.8),hpf=200,red=18); parts.append((t+0.1,resamp(bx,1.1),0.55))
    return place(parts)
for i,(kind,seed) in enumerate([('mid',1),('hard',2),('tickle',3),('brush',4)]):
    emit(f'equipment/antler_rattle_{i}',rattle(kind,seed),-15.0,'NPS elk_fight (+elk_bush for #3)',f'{kind} rattle sequence built from real antler clacks/meshing (onset-detected grains + sparring clusters), HP250, spectral gate, pitch x1.12-1.45 (whitetail-sized antlers)')
# rut fight clash/grind
for i in range(4):
    o=strong[i*2]; emit(f'rutfight/clash_{i}',grain(o[0],0.32,1.1+0.05*i),-15.5,f'NPS elk_fight @{o[0]:.2f}s','single real antler clash, HP250, gate, pitch x1.1-1.25',fo=0.12)
for i,(a,b) in enumerate([CL[0],CL[3],CL[6]]):
    c=cluster(a,a+1.0,1.15); emit(f'rutfight/grind_{i}',c,-15.0,f'NPS elk_fight {a:.1f}-{a+1:.1f}s','real antler meshing segment 1.0s, HP250, gate, pitch x1.15')

# ======================================================================== whitetail voices
DV='fh_doe_vocal2'; DVN=(2.2,3.9)
def doe(a,b,ratio=1.0):
    x=clean(DV,a-0.04,b+0.08,noise=DVN,hpf=150,red=20); return resamp(x,ratio) if ratio!=1 else x
dvs=[(1.34,1.72),(4.22,4.56),(7.90,8.26),(10.74,11.04),(14.02,14.36),(18.14,18.46),(21.32,21.66),(25.12,25.46),(28.26,28.60),(31.84,32.18)]
FB2='fh_fawn_bleat2'; FB2N=(3.2,5.6)
def fawn(a,b,ratio=1.0,src_=FB2,nz=FB2N):
    x=clean(src_,a-0.03,b+0.06,noise=nz,hpf=300,red=22); return resamp(x,ratio) if ratio!=1 else x
fbs=[(5.88,6.60),(7.10,7.52),(8.02,8.50),(9.04,9.24),(11.34,11.70),(15.46,15.78),(16.50,16.86),(17.46,17.84),(18.46,18.84),(19.44,19.70)]
# bleat can: doe estrous bleats (1-2 bleats)
for i,(seq) in enumerate([[0,4],[2],[6,8,1]]):
    parts=[];t=0
    for j in seq:
        d=doe(*dvs[j],ratio=rng.uniform(0.94,1.0)); parts.append((t,d,1.0)); t+=len(d)/SR+rng.uniform(0.45,0.8)
    rel='equipment/bleat_call' if i==0 else f'equipment/bleat_call_{i}'
    emit(rel,place(parts),-13.5,'iNat CC0 doe_vocal2 (K. Zoebelein)','real doe bleats '+str([dvs[j] for j in seq])+', HP150, spectral gate, slight pitch drop')
emit('wildlife/whitetail_bleat_a',doe(*dvs[3]),-15.5,'iNat CC0 doe_vocal2','single doe bleat, HP150, gate')
emit('wildlife/whitetail_bleat_b',doe(*dvs[7]),-15.5,'iNat CC0 doe_vocal2','single doe bleat, HP150, gate')
emit('equipment/deer_contact_a',fawn(*fbs[1]),-16.0,'iNat CC BY fawn_bleat2 (sofi v)','fawn contact bleat, HP300, gate')
emit('equipment/deer_contact_b',fawn(*fbs[5]),-16.0,'iNat CC BY fawn_bleat2 (sofi v)','fawn contact bleat, HP300, gate')

# snorts / blows / wheeze
WS='fh_deer_wheeze_snort'; WSN=(10.6,11.9)
DS='fh_deer_snort'; DSN=(7.0,8.5)
def blow(n,a,b,nz,red=20,hpf=150): return clean(n,a-0.02,b+0.1,noise=nz,hpf=hpf,red=red)
emit('wildlife/whitetail_blow_0',blow(WS,3.30,3.76,WSN),-14.0,'iNat CC BY deer_wheeze_snort (Daughter Dad) 3.3s','real alarm blow, HP150, gate (birds removed)')
emit('wildlife/whitetail_blow_1',blow(WS,13.34,13.78,WSN),-14.0,'iNat CC BY deer_wheeze_snort 13.3s','real alarm blow, HP150, gate')
emit('wildlife/whitetail_snort_0',blow(DS,5.54,5.80,DSN,hpf=200),-14.0,'iNat CC BY deer_snort (W. J. Deml) 5.5s','real snort, HP200, gate (insect band removed)')
emit('wildlife/whitetail_snort_1',blow(DS,23.98,24.22,DSN,hpf=200),-14.0,'iNat CC BY deer_snort 24.0s','real snort, HP200, gate')
emit('wildlife/whitetail_snort_2',blow(WS,27.66,28.26,WSN),-14.0,'iNat CC BY deer_wheeze_snort 27.7s','real snort, HP150, gate')
al=place([(0,blow(WS,3.30,3.76,WSN),1.0),(0.62,blow(DS,5.54,5.80,DSN,hpf=200),0.8)])
emit('wildlife/whitetail_alarm',al,-14.5,'iNat CC BY deer_wheeze_snort + deer_snort','blow followed by a snort (two real takes), HP, gate')
bw=clean('fh_buck_aggr',0.66,1.36,noise=(1.6,2.8),hpf=120,red=18)
emit('wildlife/whitetail_wheeze_0',bw,-14.0,'iNat CC0 buck_aggr (A. Parker) 0.7s','real aggressive buck snort-wheeze, HP120, gate')
# huff source for grunts
HF=clean('fh_deer_huff',2.74,3.02,noise=(5.0,8.0),hpf=80,red=20)
json.dump(LOG,open('/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad/log1.json','w'),indent=0)
