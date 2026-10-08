import sys,json; sys.path.insert(0,'/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad')
exec(open('/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad/build_sounds.py').read().split('# ======================================================================== antler rattling')[0])
LOG=[]
# ======================================================================== elk
B1,B2,B3='fh_elk_bugle1','fh_elk_bugle2','fh_elk_bugle3'
emit('wildlife/elk_bugle_0',clean(B2,12.70,16.35,noise=(7.0,11.5),hpf=150,red=20),-10.5,'NPS elk_bugle2 12.7-16.4s','bugle, HP150, gate',fo=0.25)
emit('wildlife/elk_bugle_1',clean(B3,8.10,10.60,noise=(5.0,7.0),hpf=150,red=18),-10.5,'NPS elk_bugle3 8.1-10.6s','bugle, HP150, gate',fo=0.25)
emit('wildlife/elk_bugle_2',clean(B1,2.75,7.10,noise=(0.2,2.0),hpf=150,red=20),-10.5,'NPS elk_bugle1 2.75-7.1s','bugle + grunts (chuckles), HP150, gate',fo=0.25)
emit('wildlife/elk_bugle_3',clean(B3,16.50,19.05,noise=(5.0,7.0),hpf=150,red=18),-10.5,'NPS elk_bugle3 16.5-19.05s','bugle, HP150, gate',fo=0.25)
EM='fh_elk_mew'; EMN=(4.4,4.9)
for i,(a,b) in enumerate([(0.12,0.75),(1.42,2.05),(2.52,3.15),(3.66,4.32)]):
    emit(f'wildlife/elk_mew_{i}',clean(EM,a,b,noise=EMN,hpf=250,red=22,k=1.8),-12.0,f'NPS elk_mew {a}-{b}s','cow mew, HP250, gate (tonal hum removed)',fo=0.08)
emit('wildlife/elk_bark_0',clean('fh_elk_bark',1.40,2.50,noise=(2.8,3.9),hpf=150,red=20),-14.0,'NPS elk_bark 1.4-2.5s','alarm bark, HP150, gate',fo=0.2)
eb=clean('fh_elk_bark',1.40,2.50,noise=(2.8,3.9),hpf=150,red=20)
emit('wildlife/elk_bark_1',resamp(eb,1.06),-14.5,'NPS elk_bark 1.4-2.5s','alarm bark pitched x1.06, HP150, gate',fo=0.2)
# ======================================================================== moose
MR='fh_moose_rut'; MRN=(19.0,23.5)
emit('wildlife/moose_call_0',clean(MR,9.70,12.60,noise=MRN,hpf=120,red=18),-12.5,'NPS moose_rut 9.7-12.6s','cow moose rut call, HP120, gate',fo=0.2)
emit('wildlife/moose_call_1',clean(MR,0.55,2.75,noise=MRN,hpf=120,red=18),-12.5,'NPS moose_rut 0.55-2.75s','cow moose rut call, HP120, gate',fo=0.2)
MB='fh_moose_bull'; MBN=(2.95,4.3)
for i,(a,b) in enumerate([(10.25,10.75),(8.50,9.25),(20.12,20.70)]):
    emit(f'wildlife/moose_grunt_{i}',clean(MB,a,b,noise=MBN,hpf=70,red=16),-13.5,f'NPS moose_bull {a}-{b}s','bull grunt, HP70, gate',fo=0.08)
emit('wildlife/moose_threat_0',clean(MB,0.75,2.42,noise=MBN,hpf=70,red=16),-12.5,'NPS moose_bull 0.75-2.4s','long bull grunt/croak, HP70, gate',fo=0.15)
# ======================================================================== wolves / coyotes
WH='fh_wolf_howl_DENA'; WHN=(7.6,8.2)
wh=clean(WH,0.15,6.2,noise=WHN,hpf=150,red=20)
emit('wildlife/wolf_howl_0',wh,-11.5,'NPS wolf_howl_DENA','lone howl, HP150, gate',fo=1.2)
WC='fh_wolf_chorus'; WCN=(0.5,3.5)
emit('wildlife/wolf_howl_1',clean(WC,4.2,10.4,noise=WCN,hpf=150,red=20),-12.0,'NPS wolf_chorus 4.2-10.4s','howl, HP150, gate',fo=1.0)
emit('wildlife/wolf_howl_2',resamp(wh,0.94),-12.0,'NPS wolf_howl_DENA','lone howl pitched x0.94 (deeper wolf), HP150, gate',fo=1.2)
emit('wildlife/wolf_chorus_0',clean(WC,15.0,25.5,noise=WCN,hpf=150,red=20),-12.5,'NPS wolf_chorus 15-25.5s','pack chorus, HP150, gate',fi=0.6,fo=1.5)
emit('wildlife/wolf_chorus_1',clean(WC,63.0,73.5,noise=WCN,hpf=150,red=20),-12.5,'NPS wolf_chorus 63-73.5s','pack chorus, HP150, gate',fi=0.6,fo=1.5)
CM='fh_coyote_moja'; CY='fh_coyote_yell'; CC='fh_coyote_chase'
emit('ambient/coyote_0',clean(CY,14.0,22.0,noise=(3.0,6.0),hpf=250,red=16),-14.0,'NPS coyote_yell 14-22s','group yip-howl, HP250, gate',fi=0.5,fo=1.2)
emit('ambient/coyote_1',clean(CM,0.0,7.5,noise=(9.5,9.9),hpf=250,lpf=4400,red=18,k=1.1),-14.0,'NPS coyote_moja 0-7.5s','yips and howls, HP250, light gate',fi=0.3,fo=1.2)
emit('ambient/coyote_2',clean(CY,36.0,44.0,noise=(3.0,6.0),hpf=250,red=16),-14.0,'NPS coyote_yell 36-44s','group yip-howl, HP250, gate',fi=0.5,fo=1.2)
FB2='fh_fawn_bleat2'; FB2N=(3.2,5.6)
def fawn(a,b,ratio=1.0,src_=FB2,nz=FB2N):
    x=clean(src_,a-0.03,b+0.06,noise=nz,hpf=300,red=22); return resamp(x,ratio) if ratio!=1 else x
fbs=[(5.88,6.60),(7.10,7.52),(8.02,8.50),(9.04,9.24),(11.34,11.70),(15.46,15.78),(16.50,16.86),(17.46,17.84),(18.46,18.84),(19.44,19.70)]
# ======================================================================== predator locator call: lone coyote howl -> fawn distress
howlA=clean(CC,0.25,2.08,noise=(7.0,8.2),hpf=300,red=22)
howlB=clean(CC,8.25,11.35,noise=(7.0,8.2),hpf=300,red=22)
def distress(seed,n=7):
    r=np.random.default_rng(seed); parts=[];t=0
    for i in range(n):
        a,b=fbs[r.integers(len(fbs))]; f=fawn(a,b,ratio=r.uniform(1.04,1.14)); parts.append((t,f,r.uniform(0.75,1.0))); t+=len(f)/SR*r.uniform(0.78,0.95)+r.uniform(0.04,0.16)
    return place(parts)
emit('equipment/predator_call',distress(11,8),-12.5,'iNat CC BY fawn_bleat2 (sofi v)','fawn distress sequence (8 real bleats, sped x1.04-1.14 and overlapped for urgency), HP300, gate',fo=0.3)
emit('equipment/predator_call_1',place([(0,howlA,1.0),(len(howlA)/SR+0.5,distress(12,6),0.85)]),-12.5,'NPS coyote_chase + iNat CC BY fawn_bleat2','short lone-coyote locator howl, then fawn distress, HP300, gate',fo=0.3)
emit('equipment/predator_call_2',distress(13,9),-12.5,'iNat CC BY fawn_bleat2 (sofi v)','fawn distress sequence (9 real bleats), HP300, gate',fo=0.3)
emit('equipment/predator_call_3',distress(14,7),-12.5,'iNat CC BY fawn_bleat2 (sofi v)','fawn distress sequence (7 real bleats), HP300, gate',fo=0.3)
# ======================================================================== duck call (hen mallard quacks)
ML='fh_mallard'; MLN=(20.0,21.0)
q1=clean(ML,28.05,30.10,noise=MLN,hpf=300,red=16)
emit('hunts/duck_call_0',q1,-12.5,'NPS mallard 28.05-30.1s','hen mallard quack series, HP300, gate',fo=0.1)
emit('hunts/duck_call_1',clean(ML,25.40,26.05,noise=MLN,hpf=300,red=16),-12.5,'NPS mallard 25.4-26.05s','hen quacks, HP300, gate',fo=0.1)
emit('hunts/duck_call_2',clean(ML,24.25,25.25,noise=MLN,hpf=300,red=16),-13.5,'NPS mallard 24.25-25.25s','soft feeding chuckle quacks, HP300, gate',fo=0.1)
json.dump(LOG,open('/tmp/claude-0/-home-claude/8b2a2acf-2d0c-53c3-b65d-0cc2b4a50330/scratchpad/log2.json','w'),indent=0)
