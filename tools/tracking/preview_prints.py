import numpy as np, sys
from PIL import Image, ImageDraw
A=np.asarray(Image.open('/home/claude/work/tracking/patch/assets/frontierhunts/textures/entity/track_prints.png')).astype(float)/255
C=32
SURF={'snow':((0.93,0.95,0.99),(0.55,0.63,0.80),(1.0,1.0,1.0)),'mud':((0.30,0.22,0.16),(0.13,0.09,0.06),(0.46,0.37,0.28)),
      'soil':((0.42,0.30,0.20),(0.20,0.13,0.08),(0.60,0.47,0.34)),'sand':((0.86,0.80,0.62),(0.58,0.50,0.36),(0.98,0.94,0.80)),'grass':((0.36,0.55,0.22),(0.18,0.30,0.10),(0.46,0.64,0.30))}
rows=A.shape[0]//C; S=4
img=np.zeros((rows*C*S,len(SURF)*3*C*S,3))
for si,(sn,(bg,sh,rm)) in enumerate(SURF.items()):
    for r in range(rows):
        for st in range(3):
            base=np.ones((C,C,3))*np.array(bg)
            # a little ground texture
            base*=0.94+0.06*np.random.default_rng(r).random((C,C,1))
            a=A[r*C:(r+1)*C,st*2*C:(st*2+1)*C,3:4]; b=A[r*C:(r+1)*C,(st*2+1)*C:(st*2+2)*C,3:4]
            o=base*(1-a)+np.array(sh)*a
            o=o*(1-b*0.8)+np.array(rm)*b*0.8
            o=np.repeat(np.repeat(o,S,0),S,1)
            x0=(si*3+st)*C*S; img[r*C*S:(r+1)*C*S,x0:x0+C*S]=o
Image.fromarray((np.clip(img,0,1)*255).astype('uint8')).save(sys.argv[1])
