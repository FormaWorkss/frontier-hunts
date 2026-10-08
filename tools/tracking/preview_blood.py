import numpy as np, sys
from PIL import Image
A=np.asarray(Image.open('/home/claude/work/tracking/patch/assets/frontierhunts/textures/entity/blood_trail_v4.png')).astype(float)/255
C=256
def cell(i): r,c=divmod(i,4); return A[r*C:(r+1)*C,c*C:(c+1)*C]
FRESH={'heart':(0.84,0.05,0.04),'lung':(0.92,0.24,0.26),'liver':(0.40,0.03,0.04),'gut':(0.34,0.07,0.05),'muscle':(0.80,0.07,0.05)}
DRY={'heart':(0.30,0.12,0.08),'lung':(0.42,0.20,0.17),'liver':(0.20,0.08,0.05),'gut':(0.22,0.10,0.06),'muscle':(0.30,0.12,0.08)}
CELLS={'heart':[2,1,0,14],'lung':[5,6,4,13],'liver':[10,1,10,12],'gut':[8,8,7,12],'muscle':[11,0,11,12]}
OVER={'lung':{5:17,6:18,4:16,13:19},'gut':9}
bg=np.ones((C,C,3))*np.array([0.33,0.47,0.2])
rows=[]
for t in FRESH:
    row=[]
    for age in (0.0,0.5,1.0):
        tint=np.array(FRESH[t])*(1-age)+np.array(DRY[t])*age
        for ci in CELLS[t][:2]:
            c=cell(ci); o=bg*(1-c[...,3:4])+c[...,:3]*tint*c[...,3:4]
            if t in OVER:
                ov=cell(OVER[t][ci] if isinstance(OVER[t],dict) else OVER[t]); k=(1-age*0.9) if t=='lung' else 1.0; dk=1-0.5*age
                o=o*(1-ov[...,3:4]*k)+ov[...,:3]*dk*ov[...,3:4]*k
            row.append(o)
    rows.append(np.concatenate(row,1))
img=np.concatenate(rows,0)
Image.fromarray((np.clip(img,0,1)*255).astype('uint8')).resize((img.shape[1]//2,img.shape[0]//2)).save(sys.argv[1])
