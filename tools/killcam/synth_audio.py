import numpy as np, scipy.signal as sg, scipy.io.wavfile as wf, subprocess, os
SR=44100
rng=np.random.default_rng(7)
def t(d): return np.arange(int(SR*d))/SR
def noise(d): return rng.standard_normal(int(SR*d))
def pink(d):
    n=int(SR*d); w=rng.standard_normal(n)
    b=[0.049922035,-0.095993537,0.050612699,-0.004408786]; a=[1,-2.494956002,2.017265875,-0.522189400]
    return sg.lfilter(b,a,w)*8
def bp(x,lo,hi,o=2): s=sg.butter(o,[lo/(SR/2),hi/(SR/2)],'band',output='sos'); return sg.sosfilt(s,x)
def lp(x,f,o=2): s=sg.butter(o,f/(SR/2),'low',output='sos'); return sg.sosfilt(s,x)
def hp(x,f,o=2): s=sg.butter(o,f/(SR/2),'high',output='sos'); return sg.sosfilt(s,x)
def sweep_sine(d,f0,f1,curve=1.0):
    tt=t(d); k=(tt/d)**curve; f=f0+(f1-f0)*k
    return np.sin(2*np.pi*np.cumsum(f)/SR)
def env(d,a,tau,hold=0.0):
    tt=t(d); e=np.minimum(1,tt/max(a,1e-4)); dec=np.exp(-np.maximum(0,tt-a-hold)/tau); return e*dec
def fade(x,fi=0.005,fo=0.03):
    n=len(x); i=int(fi*SR); o=int(fo*SR)
    if i>0: x[:i]*=np.linspace(0,1,i)
    if o>0: x[-o:]*=np.linspace(1,0,o)
    return x
def sweep_lp(x,f0,f1):
    # time-varying lowpass by block processing
    out=np.zeros_like(x); n=len(x); B=512; zi=None
    for s in range(0,n,B):
        k=s/n; f=f0*(f1/f0)**k
        sos=sg.butter(2,min(f,SR/2*0.95)/(SR/2),'low',output='sos')
        if zi is None: zi=np.zeros((sos.shape[0],2))
        out[s:s+B],zi=sg.sosfilt(sos,x[s:s+B],zi=zi)
    return out
def sweep_bp(x,f0,f1,q=1.4):
    out=np.zeros_like(x); n=len(x); B=512; zi=None
    for s in range(0,n,B):
        k=s/n; f=f0*(f1/f0)**k; bw=f/q
        lo=max(20,f-bw/2); hi=min(SR/2*0.95,f+bw/2)
        sos=sg.butter(2,[lo/(SR/2),hi/(SR/2)],'band',output='sos')
        if zi is None: zi=np.zeros((sos.shape[0],2))
        out[s:s+B],zi=sg.sosfilt(sos,x[s:s+B],zi=zi)
    return out
def room(x,length=0.18,mix=0.15):
    ir=noise(length)*np.exp(-t(length)/(length/5)); ir=lp(ir,3500); ir/=np.abs(ir).sum()**0.5*8
    wet=sg.fftconvolve(x,ir)[:len(x)]
    return x*(1-mix)+wet*mix/ (np.abs(wet).max()+1e-9)*np.abs(x).max()
def norm(x,peak_db=-1.0):
    x=x-np.mean(x); return x/np.abs(x).max()*10**(peak_db/20)
def pad(x,d):
    n=int(SR*d); y=np.zeros(n); y[:min(n,len(x))]=x[:min(n,len(x))]; return y
def save(name,x):
    x=fade(x.copy())
    wf.write(name+'.wav',SR,(x*32767).astype(np.int16))
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',name+'.wav','-c:a','libvorbis','-q:a','6','-ac','1',name+'.ogg'],check=True)

# 1 slowmo_in: sub drop + collapsing air
d=1.4
sub=sweep_sine(d,150,36,0.55)*env(d,0.012,0.45)
air=sweep_lp(pink(d),5200,180)*env(d,0.02,0.32)
tone=sweep_sine(d,240,92,0.7)*env(d,0.05,0.35)*0.18
x=sub*1.0+air*0.55+tone
save('slowmo_in',norm(room(x,0.3,0.2),-1.5))

# 2 flight: slow-motion bullet tearing air, spin whirr
d=2.6; tt=t(d)
e=np.minimum(1,tt/0.25)*np.clip((d-tt)/0.9,0,1)**1.3
body=sweep_bp(noise(d),1100,520,1.3)*(1+0.32*np.sin(2*np.pi*21*tt))
rumble=lp(noise(d),130,4)*1.6
hiss=hp(noise(d),4200)*np.exp(-tt/0.6)*0.25
x=(body+rumble*0.7+hiss)*e
save('flight',norm(x,-2.0))

# 3 heartbeat: lub-dub
d=1.1; x=np.zeros(int(SR*d))
def thump(amp,f):
    dd=0.35; tt=t(dd)
    s=(np.sin(2*np.pi*f*tt)+0.45*np.sin(2*np.pi*f*1.62*tt))*np.exp(-tt/0.075)*np.minimum(1,tt/0.004)
    c=lp(noise(dd),260)*np.exp(-tt/0.012)*0.8
    return (s+c)*amp
for at,amp,f in ((0.02,1.0,52),(0.29,0.68,60)):
    y=thump(amp,f); i=int(at*SR); x[i:i+len(y)]+=y[:len(x)-i]
save('heartbeat',norm(lp(x,900),-1.5))

# 4 impact (bullet): crisp snap + meaty thump + wet slap + slow tail
d=1.0; tt=t(d)
snap=hp(noise(d),2200)*np.exp(-tt/0.004)
thumpv=sweep_sine(d,105,52,0.35)*np.exp(-tt/0.14)*np.minimum(1,tt/0.002)
slap=bp(noise(d),480,1600)*np.exp(-tt/0.055)
tail=np.sin(2*np.pi*38*tt)*np.exp(-tt/0.38)*np.minimum(1,tt/0.02)
x=snap*0.9+thumpv*1.0+slap*0.7+tail*0.55
save('impact',norm(room(x,0.22,0.18),-0.8))

# 5 impact_arrow: tight thwock
d=0.7; tt=t(d)
tr=hp(noise(d),1800)*np.exp(-tt/0.003)
mid=sweep_sine(d,190,118,0.4)*np.exp(-tt/0.085)*np.minimum(1,tt/0.0015)
low=np.sin(2*np.pi*68*tt)*np.exp(-tt/0.2)
shaft=np.sin(2*np.pi*382*tt)*np.exp(-tt/0.15)*0.14*(1+0.3*np.sin(2*np.pi*9*tt))
x=tr*0.7+mid+low*0.6+shaft
save('impact_arrow',norm(room(x,0.16,0.14),-1.0))

# 6 xray: soft crystalline shimmer
d=1.3; tt=t(d)
e=np.minimum(1,tt/0.12)*np.exp(-np.maximum(0,tt-0.2)/0.45)
x=np.zeros_like(tt)
for f,a in ((1244.5,1.0),(1864.7,0.6),(2489.0,0.4),(3729.3,0.18)):
    for det in (-1.6,0,1.9):
        x+=a*np.sin(2*np.pi*(f+det)*tt+rng.uniform(0,6.28))
x*= (0.8+0.2*np.sin(2*np.pi*6.5*tt))
padlow=np.sin(2*np.pi*110*tt)*0.5+np.sin(2*np.pi*165*tt)*0.25
x=x*e*0.25+padlow*e*0.35
save('xray',norm(room(x,0.5,0.35),-4.0))

# 7 thud: body meets ground
d=0.9; tt=t(d)
b=sweep_sine(d,64,42,0.5)*np.exp(-tt/0.17)*np.minimum(1,tt/0.004)
dirt=lp(noise(d),420,3)*np.exp(-tt/0.22)*1.4
crk=bp(noise(d),1500,4200)*np.exp(-tt/0.05)*(rng.random(len(tt))>0.985)*3
x=b+dirt*0.6+crk*0.25
save('thud',norm(room(x,0.2,0.15),-1.2))

# 8 return: reverse whoosh back to the shooter
d=0.8; tt=t(d)
e=(tt/d)**2.2*np.clip((d-tt)/0.05,0,1)
w=sweep_bp(noise(d),260,3200,1.2)*e
rise=sweep_sine(d,70,190,1.6)*e*0.35
x=w+rise
save('return',norm(x,-2.0))
print('ok')
