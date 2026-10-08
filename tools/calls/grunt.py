import sys,json; sys.path.insert(0,'/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad')
from lib import *
HUFF=hp(cut(load('fh_deer_huff'),2.74,3.02),80); HN=hp(cut(load('fh_deer_huff'),5.0,8.0),80)
HUFF=denoise(HUFF,HN,red_db=20)
AG=hp(cut(load('fh_buck_aggr'),0.80,1.20),80); AG=denoise(AG,hp(cut(load('fh_buck_aggr'),1.6,2.8),80),red_db=18)
# real-breath spectral envelope (cepstrally smoothed) used to colour the voiced source
def envelope(x,n=4096):
    X=np.abs(np.fft.rfft(x[:n]*np.hanning(min(n,len(x))) if len(x)>=n else np.pad(x,(0,n-len(x)))*np.hanning(n)))
    c=np.fft.irfft(np.log(X+1e-9)); c[40:-40]=0; return np.exp(np.fft.rfft(c).real)
ENV=envelope(np.concatenate([HUFF,AG]))
def colour(x,env,amt=0.6):
    n=len(x); N=1<<int(np.ceil(np.log2(n+4096))); X=np.fft.rfft(x,N)
    e=np.interp(np.linspace(0,1,len(X)),np.linspace(0,1,len(env)),env); e=(e/e.max())**amt
    return np.fft.irfft(X*e,N)[:n]
def reson(x,f,bw,g=1.0):
    r=np.exp(-np.pi*bw/SR); th=2*np.pi*f/SR
    a=[1,-2*r*np.cos(th),r*r]; b=[(1-r*r)*0.5,0,-(1-r*r)*0.5]
    return ss.lfilter(b,a,x)*g
def breath(n,rng,src=None):
    src=src if src is not None else np.concatenate([HUFF,AG])
    out=np.zeros(n); g=int(0.06*SR); t=0
    while t<n:
        s=rng.integers(0,len(src)-g); w=src[s:s+g]*np.hanning(g); out[t:t+g]+=w[:max(0,min(g,n-t))] if t+g<=n else w[:n-t]; t+=g//2
    return out
def grunt(dur,f0s,f0e,seed,fry=0.25,tube=1.0,breathy=0.25,click=False):
    rng=np.random.default_rng(seed); n=int(dur*SR); t=np.arange(n)/SR
    u=t/dur
    f0=f0s+(f0e-f0s)*u**1.2
    f0*=1+0.025*np.convolve(rng.standard_normal(n),np.ones(800)/800,'same')*8   # slow jitter
    # pulse train with jitter/shimmer; vocal fry (long irregular periods) towards the end
    src=np.zeros(n); ph=0.0; i=0
    pulse_len=int(0.004*SR); pk=np.exp(-np.arange(pulse_len)/(0.0009*SR))*np.sin(np.pi*np.arange(pulse_len)/pulse_len)
    while i<n:
        fr = u[i]>1-fry
        T=SR/f0[i]*(rng.uniform(1.4,2.6) if fr and rng.random()<0.5 else rng.uniform(0.97,1.03))
        amp=rng.uniform(0.8,1.0)*(rng.uniform(0.4,1.0) if fr else 1)
        j=min(n,i+pulse_len); src[i:j]+=pk[:j-i]*amp; i+=max(1,int(T))
    src=np.diff(src,prepend=0)  # glottal flow derivative
    v=reson(src,230,90,1.0)+reson(src,520,140,0.9)+reson(src,1150,220,0.35)+reson(src,2400,350,0.12)+reson(src,3600,500,0.05)
    v=colour(v,ENV,0.5)
    # tube/reed: a closed-open plastic tube resonance + slight buzz saturation
    if tube>0: v=v+tube*0.6*reson(v,390,60)+tube*0.25*reson(v,1170,120)
    v=v/np.max(np.abs(v)+1e-9)
    v=np.tanh(1.3*v)/np.tanh(1.3)
    v=lp(v,1800,2)
    b=breath(n,rng); b=lp(hp(b,180),3200,2); b=b/np.max(np.abs(b)+1e-9)
    # pitch-synchronous breath (turbulence on each pulse) + aspiration
    am=np.abs(ss.hilbert(lp(src,300))); am/=am.max()+1e-9
    sig=v+breathy*(0.6*b*am+0.4*b)
    env=np.minimum(1,t/0.03)*np.minimum(1,(dur-t)/0.07)**0.8*(0.85+0.15*np.sin(np.pi*u))
    out=sig*env
    if click:  # tending-grunt "click": a short burst of tight pulses at the end
        c=np.zeros(int(0.06*SR)); 
        for k in range(4): c[int(k*0.011*SR):int(k*0.011*SR)+pulse_len]+=pk*rng.uniform(0.6,1)
        c=reson(np.diff(c,prepend=0),900,300)+reson(np.diff(c,prepend=0),2200,500,0.4); c/=np.abs(c).max()+1e-9
        out=np.concatenate([out,c*0.5])
    return lp(hp(out,70),5000,2).astype(np.float32)
