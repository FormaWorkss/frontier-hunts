import numpy as np, scipy.signal as ss, scipy.io.wavfile as wf, subprocess, os
S='/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad'
SR=44100
def load(name):
    p=name if name.startswith('/') else f'{S}/src/{name}.wav'
    sr,x=wf.read(p); x=x.astype(np.float32)
    if x.ndim>1: x=x.mean(1)
    return x
def env_db(x,hop=0.02):
    h=int(SR*hop); n=len(x)//h
    r=np.sqrt(np.mean(x[:n*h].reshape(n,h)**2,1)+1e-12)
    return 20*np.log10(r)
def segments(x,thr_above=12,hop=0.02,minlen=0.08,gap=0.15):
    e=env_db(x,hop); floor=np.percentile(e,15)
    on=e>floor+thr_above; segs=[];i=0
    while i<len(on):
        if on[i]:
            j=i
            while j<len(on) and (on[j] or (j+int(gap/hop)<len(on) and on[j:j+int(gap/hop)].any())): j+=1
            if (j-i)*hop>=minlen: segs.append((i*hop,j*hop,e[i:j].max()-floor))
            i=j
        else: i+=1
    return floor,segs

def rbj(kind,fc,Q,G=0):
    A=10**(G/40); w=2*np.pi*fc/SR; al=np.sin(w)/(2*Q); c=np.cos(w)
    if kind=='hs':
        b=[A*((A+1)+(A-1)*c+2*np.sqrt(A)*al),-2*A*((A-1)+(A+1)*c),A*((A+1)+(A-1)*c-2*np.sqrt(A)*al)]
        a=[(A+1)-(A-1)*c+2*np.sqrt(A)*al,2*((A-1)-(A+1)*c),(A+1)-(A-1)*c-2*np.sqrt(A)*al]
    else:
        b=[(1+c)/2,-(1+c),(1+c)/2]; a=[1+al,-2*c,1-al]
    return np.array(b)/a[0],np.array(a)/a[0]
def kweight(x):
    b,a=rbj('hs',1500,1/np.sqrt(2),4.0); y=ss.lfilter(b,a,x)
    b,a=rbj('hp',38,0.5); return ss.lfilter(b,a,y)
def mmax(x):
    y=kweight(x); w=int(0.4*SR); h=int(0.1*SR)
    if len(y)<w: y=np.pad(y,(0,w-len(y)))
    ms=[np.mean(y[i:i+w]**2) for i in range(0,len(y)-w+1,h)]
    return -0.691+10*np.log10(max(ms)+1e-12)
def readogg(p):
    r=subprocess.run(['ffmpeg','-v','error','-i',p,'-ac','1','-ar','44100','-f','f32le','-'],capture_output=True)
    return np.frombuffer(r.stdout,np.float32).copy()
def hp(x,fc,order=4):
    sos=ss.butter(order,fc,'highpass',fs=SR,output='sos'); return ss.sosfiltfilt(sos,x)
def lp(x,fc,order=4):
    sos=ss.butter(order,fc,'lowpass',fs=SR,output='sos'); return ss.sosfiltfilt(sos,x)
def cut(x,a,b): return x[int(a*SR):int(b*SR)].copy()
def fade(x,fi=0.01,fo=0.05):
    x=x.copy(); n=int(fi*SR); m=int(fo*SR)
    if n: x[:n]*=np.sin(np.linspace(0,np.pi/2,n))**2
    if m: x[-m:]*=np.cos(np.linspace(0,np.pi/2,m))**2
    return x
def denoise(x,noise,red_db=22,k=1.6,smooth_t=5,smooth_f=3,nfft=2048):
    """spectral gating: per-bin threshold from a noise-only clip; attenuates bins below it by up to red_db"""
    hop=nfft//4
    f,t,N=ss.stft(noise,SR,nperseg=nfft,noverlap=nfft-hop)
    mag=np.abs(N); mu=mag.mean(1); sd=mag.std(1); thr=(mu+k*sd)[:,None]
    f,t,X=ss.stft(x,SR,nperseg=nfft,noverlap=nfft-hop)
    A=np.abs(X); g=np.clip((A-thr)/(thr+1e-9),0,1)  # soft knee
    g=g**0.7
    from scipy.ndimage import uniform_filter
    g=uniform_filter(g,(smooth_f,smooth_t))
    floor=10**(-red_db/20); g=floor+(1-floor)*g
    _,y=ss.istft(X*g,SR,nperseg=nfft,noverlap=nfft-hop)
    y=y[:len(x)]
    return np.pad(y,(0,len(x)-len(y)))
def declick(x,thr=6.0,win=64):
    """replace isolated sample spikes (clicks) by interpolation"""
    d=np.abs(np.diff(x,prepend=x[0])); med=ss.medfilt(d,31)+1e-6
    bad=np.where(d>thr*med*8)[0]
    y=x.copy()
    for i in bad:
        a=max(0,i-4); b=min(len(x)-1,i+4); y[a:b]=np.linspace(y[a],y[b],b-a)
    return y
def norm(x,target,peak=-1.7):
    g=10**((target-mmax(x))/20); y=x*g
    pk=np.max(np.abs(y))
    lim=10**(peak/20)
    if pk>lim: y=softlimit(y,lim)
    return y.astype(np.float32)
def softlimit(y,lim):
    # simple look-ahead-free gain riding limiter: smooth gain envelope
    a=np.abs(y); env=ss.maximum_filter1d(a,int(0.005*SR)) if hasattr(ss,'maximum_filter1d') else a
    from scipy.ndimage import maximum_filter1d, uniform_filter1d
    env=maximum_filter1d(a,int(0.01*SR)); env=uniform_filter1d(env,int(0.004*SR))
    g=np.minimum(1,lim/np.maximum(env,1e-9)); g=uniform_filter1d(np.minimum.accumulate(g[::-1])[::-1]*0+g,int(0.002*SR))
    y=y*g; return np.clip(y,-lim,lim)
def writeogg(x,path,q=5):
    os.makedirs(os.path.dirname(path),exist_ok=True)
    tmp=path+'.raw'; x.astype(np.float32).tofile(tmp)
    subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar','44100','-ac','1','-i',tmp,'-c:a','libvorbis','-q:a',str(q),path],check=True)
    os.remove(tmp)
def resamp(x,ratio):
    """pitch/time shift by resampling (ratio>1 = higher & shorter)"""
    n=int(len(x)/ratio); return ss.resample(x,n).astype(np.float32)
def place(parts,total=None):
    """parts: list of (t_seconds, signal, gain)"""
    end=max(int(t*SR)+len(s) for t,s,g in parts)
    out=np.zeros(max(end,int((total or 0)*SR)),np.float32)
    for t,s,g in parts:
        i=int(t*SR); out[i:i+len(s)]+=s*g
    return out
