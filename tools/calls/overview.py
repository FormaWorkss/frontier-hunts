import sys; sys.path.insert(0,sys.argv[1]); from lib import *
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
names=sys.argv[3:]; out=sys.argv[2]
fig,ax=plt.subplots(len(names),1,figsize=(22,2.6*len(names)))
for a,n in zip(np.atleast_1d(ax),names):
    x=load(n); f,t,Z=ss.spectrogram(x,SR,nperseg=1024,noverlap=512)
    a.pcolormesh(t,f,10*np.log10(Z+1e-14),shading='auto',vmin=np.percentile(10*np.log10(Z+1e-14),40),cmap='magma')
    a.set_ylim(0,8000); a.set_title(n,fontsize=9); a.set_xticks(np.arange(0,t[-1],1 if t[-1]<30 else 5))
    a.tick_params(labelsize=6)
plt.tight_layout(); plt.savefig(out,dpi=55)
