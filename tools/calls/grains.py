import sys; sys.path.insert(0,'/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad'); from lib import *
from scipy.ndimage import maximum_filter1d
def onsets(x,lo=1500,hi=9000,hop=0.005,min_gap=0.06,prom=14):
    sos=ss.butter(4,[lo,hi],'bandpass',fs=SR,output='sos'); y=ss.sosfiltfilt(sos,x)
    e=env_db(y,hop); bg=ss.medfilt(e,int(1.0/hop)//2*2+1)
    pk,props=ss.find_peaks(e-bg,height=prom,distance=int(min_gap/hop))
    return [(p*hop,(e-bg)[p],e[p]) for p in pk]
if __name__=='__main__':
    for n in ['fh_elk_fight','fh_buck_spar']:
        x=load(n); o=onsets(x); print(n,len(o)); print(' '.join('%.2f(%.0f/%.0f)'%a for a in o[:400]))
