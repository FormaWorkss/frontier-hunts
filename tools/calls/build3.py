import sys,json; sys.path.insert(0,'/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad')
exec(open('/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad/build_sounds.py').read().split('# ======================================================================== antler rattling')[0])
from grunt import grunt
LOG=[]
P='synthesised buck grunt: glottal pulse train (f0 contour, jitter/shimmer, vocal-fry tail), formant resonators, spectral envelope + pitch-synchronous breath taken from the real deer_huff/buck_aggr recordings, reed-tube resonance, soft saturation'
def seq(spec,seed):
    parts=[];t=0
    for k,(d,a,b,gap,cl) in enumerate(spec):
        g=grunt(d,a,b,seed*10+k,click=cl); parts.append((t,g,1.0)); t+=len(g)/SR+gap
    return place(parts)
V=[ [(0.48,128,92,0,False)],                                                  # single contact grunt
    [(0.36,124,100,0.32,False),(0.55,130,88,0,False)],                         # double
    [(0.17,120,105,0.22,True),(0.16,122,104,0.2,True),(0.19,118,100,0.24,True),(0.22,120,96,0,True)],  # tending grunts with clicks
    [(0.30,118,98,0.38,False),(0.32,124,96,0.42,False),(0.70,128,84,0,False)] ] # series, last long w/ fry
for i,s in enumerate(V):
    rel='equipment/grunt_tube' if i==0 else f'equipment/grunt_tube_{i}'
    emit(rel,seq(s,i+1),-15.0,'built (no free recording) - timbre from iNat deer_huff (C. Sites-Bowen, CC BY) & buck_aggr (CC0)',P+f'; {len(s)} grunt(s)',fo=0.04)
# (whitetail_grunt_* keep their real CC0 grunt-call recordings - not rebuilt)
json.dump(LOG,open('/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad/log3.json','w'),indent=0)
