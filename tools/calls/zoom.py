import sys; sys.path.insert(0,sys.argv[1]); from lib import *
import matplotlib; matplotlib.use('Agg'); import matplotlib.pyplot as plt
out=sys.argv[2]; specs=sys.argv[3:]
fig,ax=plt.subplots(len(specs),1,figsize=(22,2.8*len(specs)))
for a,sp in zip(np.atleast_1d(ax),specs):
    n,t0,t1=sp.split(':'); t0=float(t0); t1=float(t1)
    x=readogg(n) if n.endswith('.ogg') else load(n); x=cut(x,t0,t1)
    f,t,Z=ss.spectrogram(x,SR,nperseg=1024,noverlap=896)
    L=10*np.log10(Z+1e-14); a.pcolormesh(t+t0,f,L,shading='auto',vmin=L.max()-75,vmax=L.max(),cmap='magma')
    a.set_ylim(0,10000); a.set_title(sp,fontsize=9); a.set_xticks(np.arange(t0,t1,max(0.1,round((t1-t0)/40,1)))); a.tick_params(labelsize=6)
plt.tight_layout(); plt.savefig(out,dpi=55)
