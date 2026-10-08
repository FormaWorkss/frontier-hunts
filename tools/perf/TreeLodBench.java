package com.formaworks.frontierhunts.client.tree;

import java.util.*;

/** [perf] Quad counts per distance level (NEAR / FAR / IMPOSTOR). Pure harness: ports of the mod's WildTrees/AlpineTrees blueprints + regression fixtures. */
public final class TreeLodBench {
   static final class Blue implements TreeGrowth.World {
      final Map<Long,Integer> kind=new HashMap<>();final Map<Long,Integer> axis=new HashMap<>();
      final Map<Long,String> name=new HashMap<>();
      // [perf3] queries read a frozen primitive index (the boxed maps allocated a Long per query, which polluted the
      // allocation benches); built on the first query, after the fixture is complete. Same answers as the maps.
      private TreeGrowth.LongMap frozen;private final List<String> names=new ArrayList<>();
      private TreeGrowth.LongMap index(){TreeGrowth.LongMap f=frozen;if(f!=null)return f;
         synchronized(this){if(frozen!=null)return frozen;f=new TreeGrowth.LongMap(kind.size()+axis.size()+name.size());
            Set<Long> keys=new HashSet<>(kind.keySet());keys.addAll(axis.keySet());keys.addAll(name.keySet());
            for(long k:keys){Integer kd=kind.get(k),ax=axis.get(k);String n=name.get(k);int ni=-1;if(n!=null){ni=names.indexOf(n);if(ni<0){ni=names.size();names.add(n);}}
               f.put(k,(kd==null?-1L:kd)&0xFFL|((ax==null?-1L:ax)&0xFFL)<<8|((long)ni&0xFFFFL)<<16);}
            frozen=f;return f;}}
      private long rec(int x,int y,int z){return index().get(TreeGrowth.pack(x,y,z),-1L);}
      public int kind(int x,int y,int z){if(y<=0)return TreeShape.GROUND;long r=rec(x,y,z);int k=(int)(r&0xFF);return r==-1L||k==0xFF?TreeShape.AIR:k;}
      public int axis(int x,int y,int z){long r=rec(x,y,z);int a=(int)(r>>8&0xFF);return r==-1L||a==0xFF?1:a;}
      public int light(int x,int y,int z){return 15<<20;}
      private String nameAt(int x,int y,int z,String otherwise){long r=rec(x,y,z);int n=(int)(r>>16&0xFFFF);return r==-1L||n==0xFFFF?otherwise:names.get(n);}
      public boolean conifer(int x,int y,int z){return isConifer(nameAt(x,y,z,"oak_log"));}
      public Object species(int x,int y,int z){return nameAt(x,y,z,"air");}
      public int crownForm(int x,int y,int z){String p=nameAt(x,y,z,"");
         return p.contains("cherry")||p.contains("azalea")?2:p.contains("birch")||p.contains("aspen")?1:0;}
      void log(int x,int y,int z,int ax,String n){long k=TreeGrowth.pack(x,y,z);kind.put(k,TreeShape.LOG);axis.put(k,ax);name.put(k,n);}
      void leaf(int x,int y,int z,String n){long k=TreeGrowth.pack(x,y,z);if(kind.get(k)==null){kind.put(k,TreeShape.LEAVES);name.put(k,n);}}
   }
   static boolean isConifer(String path){path=path.replace("alpine","");
      return path.contains("pine")||path.contains("spruce")||path.contains("cedar")||path.contains("fir")||path.contains("hemlock")||path.contains("larch")||path.contains("juniper")||path.contains("redwood");}

   /** A tree blueprint relative to origin (0,1,0) on ground y<=0. */
   static final class G {
      final Map<Long,int[]> logs=new LinkedHashMap<>();final Set<Long> leaves=new LinkedHashSet<>();
      String wood,leaf;
      static long k(int x,int y,int z){return TreeGrowth.pack(x,y,z);}
      void log(int x,int y,int z,int ax){long p=k(x,y,z);logs.put(p,new int[]{x,y,z,ax});leaves.remove(p);}
      void leaf(int x,int y,int z){long p=k(x,y,z);if(!logs.containsKey(p)&&y>=0)leaves.add(p);}
      void blob(double cx,double cy,double cz,double rx,double ry,double rz,double rag,Random r){
         for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++)for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++)for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++){
            double a=(x-cx)/rx,b=(y-cy)/ry,c=(z-cz)/rz,d=a*a+b*b+c*c;
            if(!(d>1.08)&&(!(d>.55)||!(r.nextDouble()<rag*(d-.55)*2.2))&&y>=0)leaf(x,y,z);}
      }
      void limb(double x0,double y0,double z0,double x1,double y1,double z1){
         double dx=x1-x0,dy=y1-y0,dz=z1-z0,l=Math.sqrt(dx*dx+dy*dy+dz*dz);
         int ax=Math.abs(dy)>=Math.max(Math.abs(dx),Math.abs(dz))*.9?1:Math.abs(dx)>Math.abs(dz)?0:2;
         int n=Math.max(1,(int)Math.ceil(l*1.5));
         for(int i=0;i<=n;i++){double t=i/(double)n;log((int)Math.round(x0+dx*t),(int)Math.round(y0+dy*t),(int)Math.round(z0+dz*t),ax);}
      }
      /** keep only leaves within 6 of a log (vanilla decay distance). */
      void trim(){
         Map<Long,Integer> d=new HashMap<>();ArrayDeque<Long> q=new ArrayDeque<>();
         int[][] n6={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
         for(int[] l:logs.values())for(int[] o:n6){long p=k(l[0]+o[0],l[1]+o[1],l[2]+o[2]);if(leaves.contains(p)&&d.putIfAbsent(p,1)==null)q.add(p);}
         while(!q.isEmpty()){long p=q.poll();int nx=d.get(p)+1;if(nx>6)continue;int x=(int)(p>>38),y=(int)(p<<52>>52),z=(int)(p<<26>>38);
            for(int[] o:n6){long m=k(x+o[0],y+o[1],z+o[2]);if(leaves.contains(m)&&d.putIfAbsent(m,nx)==null)q.add(m);}}
         leaves.retainAll(d.keySet());
      }
      void into(Blue w,int ox,int oz){
         for(int[] l:logs.values())w.log(l[0]+ox,l[1]+1,l[2]+oz,l[3],wood);
         for(long p:leaves){int x=(int)(p>>38),y=(int)(p<<52>>52),z=(int)(p<<26>>38);w.leaf(x+ox,y+1,z+oz,leaf);}
      }
   }
   static float lerp(float t,float a,float b){return a+(b-a)*t;}
   // ---- AlpineTrees ports
   static G spire(Random r,float age,boolean fir,String wood,String leaf){
      G g=new G();g.wood=wood;g.leaf=leaf;
      int h=Math.round(fir?lerp(age,11,20):lerp(age,13,24))+r.nextInt(fir?3:4);
      double sp=fir?lerp(age,1.5f,2.5f)+r.nextFloat()*.3:lerp(age,2.2f,3.4f)+r.nextFloat()*.4;
      int live=fir?Math.round(lerp(age,1.5f,4.5f))+r.nextInt(2):Math.max(age>.4f?4:3,Math.round(h*lerp(age,.16f,.28f)))+r.nextInt(2);
      boolean giant=!fir&&age>.9f&&r.nextFloat()<.4f;if(giant){h+=4+r.nextInt(4);sp+=.7;}
      int top=h-2;double off=giant?.5:0;
      for(int y=0;y<=top;y++){g.log(0,y,0,1);if(giant&&y<h*.62){g.log(1,y,0,1);g.log(0,y,1,1);g.log(1,y,1,1);}}
      List<Integer> tiers=new ArrayList<>();
      for(int y=h-1;y>=live;){tiers.add(y);double u=(y-live)/(double)Math.max(1,h-live);y-=(!(u>.55)&&(!fir||!(u>.35)))?3:2;}
      for(int y:tiers){double u=(y-live)/(double)Math.max(1,h-live);double rad=Math.max(.9,sp*Math.pow(1-u,fir?.75:.85));
         int k1=3+r.nextInt(3),k2=6+r.nextInt(3);double base=fir?.18:.3,a1=base*(.5+.5*r.nextDouble()),a2=base*.45*r.nextDouble(),p1=r.nextDouble()*6.283,p2=r.nextDouble()*6.283;
         int R=(int)Math.ceil(rad*1.35)+1;
         for(int x=-R;x<=R+(giant?1:0);x++)for(int z=-R;z<=R+(giant?1:0);z++){
            double d=Math.sqrt((x-off)*(x-off)+(z-off)*(z-off)),ang=Math.atan2(z-off,x-off);
            double lim=rad*(1+a1*Math.sin(k1*ang+p1)+a2*Math.sin(k2*ang+p2))+.4+off;
            if(d>lim)continue;boolean edge=d>1.3&&d>lim*.62;
            if(edge&&y-1>=0){g.leaf(x,y-1,z);if(d>lim*.8)continue;}
            g.leaf(x,y,z);if(d<lim*.45&&rad>1.6)g.leaf(x,y+1,z);}
      }
      for(int y=Math.max(0,live-1);y<=top;y++){g.leaf(1,y,0);g.leaf(-1,y,0);g.leaf(0,y,1);g.leaf(0,y,-1);}
      for(int y=top+1;y<=h+1;y++)g.leaf(0,y,0);
      g.trim();return g;
   }
   static G pine(Random r,float age){
      G g=new G();g.wood="alpine_pine_log";g.leaf="pine_needles";
      int h=Math.round(lerp(age,14,25))+r.nextInt(4);int live=Math.round(h*lerp(age,.5f,.62f));double sp=lerp(age,1.7f,2.7f)+r.nextFloat()*.3;
      for(int y=0;y<h;y++)g.log(0,y,0,1);
      for(int y=live+r.nextInt(2);y<h-1;y+=2+(r.nextFloat()<.3f?1:0)){double u=(y-live)/(double)Math.max(1,h-live);
         double rad=sp*(u<.35?.72+.8*u:Math.pow((1-u)/.65,.55));int n=2+r.nextInt(2)+(rad>1.8?1:0);double a0=r.nextDouble()*6.283;
         for(int i=0;i<n;i++){double a=a0+i*6.283/n+(r.nextDouble()-.5)*.8,rr=rad*(.75+.35*r.nextDouble());double x=Math.cos(a)*rr,z=Math.sin(a)*rr;int lift=rr>1.6&&r.nextFloat()<.5f?1:0;
            if(rr>1.5)g.limb(Math.cos(a),y,Math.sin(a),x*.8,y+lift,z*.8);
            g.blob(x,y+lift+.3,z,1.25+r.nextDouble()*.35,.85+r.nextDouble()*.3,1.25+r.nextDouble()*.35,.45,r);}}
      g.blob(0,h,0,1.2,1.6,1.2,.3,r);g.leaf(0,h+1,0);g.trim();return g;
   }
   static G aspen(Random r,float age,String wood,String leaf){
      G g=new G();g.wood=wood;g.leaf=leaf;
      int h=Math.round(lerp(age,11,19))+r.nextInt(3);int f=Math.round(h*lerp(age,.46f,.56f));double sp=lerp(age,1.8f,2.7f)+r.nextFloat()*.3;
      for(int y=0;y<h-1;y++)g.log(0,y,0,1);
      double c=(f+h)/2.0;g.blob(0,c+.5,0,sp*.85,(h-f)/2.0+.8,sp*.85,.35,r);
      int n=2+r.nextInt(2);double a0=r.nextDouble()*6.283;
      for(int i=0;i<n;i++){double a=a0+i*6.283/n+(r.nextDouble()-.5),rr=sp*.55,y=f+1+r.nextDouble()*(h-1-f-1);
         g.limb(0,y-2,0,Math.cos(a)*rr,y,Math.sin(a)*rr);g.blob(Math.cos(a)*rr,y+.4,Math.sin(a)*rr,sp*.62,sp*.62,sp*.62,.5,r);}
      g.trim();return g;
   }
   static G birch(Random r,float age){
      G g=new G();g.wood="birch_log";g.leaf="birch_leaves";
      int stems=age>.3f&&r.nextFloat()<.45f?(r.nextFloat()<.3f?3:2):1;double a0=r.nextDouble()*6.283;
      for(int s=0;s<stems;s++){double a=a0+s*6.283/stems;int bx=s==0?0:(int)Math.round(Math.cos(a)),bz=s==0?0:(int)Math.round(Math.sin(a));
         int h=Math.round(lerp(age,10,17))+r.nextInt(3)-(s>0?2:0);int jog=stems<=1&&!(r.nextFloat()<.4f)?h:h/2;
         int jx=stems>1?(int)Math.round(Math.cos(a)):0,jz=stems>1?(int)Math.round(Math.sin(a)):0;if(s==0&&stems>1){jx=-jx;jz=-jz;}
         int x=bx,z=bz;for(int y=0;y<h-1;y++){if(y==jog){x+=jx;z+=jz;}g.log(x,y,z,1);}
         int f=Math.round(h*.5f);double sp=lerp(age,1.5f,2.2f)+r.nextFloat()*.3;
         g.blob(x,(f+h)/2.0+.3,z,sp,(h-f)/2.0+.8,sp,.45,r);
         for(int i=0;i<5;i++){double b=r.nextDouble()*6.283,rr=sp*.8;int lx=x+(int)Math.round(Math.cos(b)*rr),lz=z+(int)Math.round(Math.sin(b)*rr);
            for(int y=f;y>f-1-r.nextInt(2)&&y>1;y--)g.leaf(lx,y,lz);}}
      g.trim();return g;
   }
   static G maple(Random r,float age,String leaf){
      G g=new G();g.wood="alpine_maple_log";g.leaf=leaf;
      boolean big=age>.55f;double o=big?.5:0;int trunk=3+r.nextInt(2)+(age>.5f?1:0);int h=Math.round(lerp(age,10,18))+r.nextInt(2);
      for(int y=0;y<=trunk;y++){g.log(0,y,0,1);if(big){g.log(1,y,0,1);g.log(0,y,1,1);g.log(1,y,1,1);}}
      int n=3+r.nextInt(2)+(big?1:0);double a0=r.nextDouble()*6.283,reach=lerp(age,2,4.4f),bl=lerp(age,2,2.8f);
      for(int i=0;i<n;i++){double a=a0+i*6.283/n+(r.nextDouble()-.5)*.6,rr=reach*(.75+.35*r.nextDouble());
         double mx=o+Math.cos(a)*rr*.55,mz=o+Math.sin(a)*rr*.55,my=trunk+(h-trunk)*.45,ex=o+Math.cos(a)*rr,ez=o+Math.sin(a)*rr,ey=h-2.5-r.nextInt(3);
         g.limb(o,trunk,o,mx,my,mz);g.limb(mx,my,mz,ex,ey,ez);
         g.blob(ex,ey+.8,ez,bl*(.9+.25*r.nextDouble()),bl*.72,bl*(.9+.25*r.nextDouble()),.45,r);
         if(r.nextFloat()<.7f){double b=a+(r.nextBoolean()?.7:-.7);double sx=mx+Math.cos(b)*rr*.55,sz=mz+Math.sin(b)*rr*.55,sy=my+1+r.nextInt(2);
            g.limb(mx,my,mz,sx,sy,sz);g.blob(sx,sy+.6,sz,bl*.75,bl*.6,bl*.75,.5,r);}}
      g.blob(o,h-.8,o,bl*1.1,bl*.8,bl*1.1,.4,r);
      g.leaves.removeIf(p->(int)(p<<52>>52)<trunk);g.trim();return g;
   }
   // ---- WildTrees ports (Frontier forest_stand)
   static G wildConifer(Random r,float age,boolean pine){
      G g=new G();g.wood=pine?"pine_log":"cedar_log";g.leaf=pine?"pine_needles":"fir_needles";
      int h=pine?Math.round(lerp(age,16,42))+r.nextInt(6):Math.round(lerp(age,13,30))+r.nextInt(5);
      int live=pine?(int)(h*lerp(age,.3f,.52f)):(int)(h*lerp(age,.14f,.28f));int max=Math.round(pine?lerp(age,3,5.8f):lerp(age,2.6f,4.4f));
      for(int y=0;y<h;y++)g.log(0,y,0,1);
      int[][] dirs={{0,-1},{1,0},{0,1},{-1,0}};
      for(int y=live;y<h;y++){double up=(y-live)/(double)Math.max(1,h-live);int sp=(int)Math.round(max*Math.pow(Math.max(0,1-up),.62));
         if(sp<=0){cluster(g,0,y,0,y>=h-2?0:1,r,0);continue;}boolean whorl=(y-live)%2==0;
         for(int s=0;s<4;s++)for(int diag=0;diag<2;diag++){if((diag!=1||whorl&&sp>=3)&&(whorl||r.nextInt(3)==0)){
            int dx=dirs[s][0],dz=dirs[s][1];if(diag==1){dx+=dirs[(s+1)%4][0];dz+=dirs[(s+1)%4][1];}
            int len=Math.max(1,sp-r.nextInt(2));int px=0,pz=0,py=y;
            for(int d=1;d<=len;d++){int drop=d>=3?-1:0;if(d==len&&len>=3)drop++;
               connect(g,px,py,pz,dx*d,y,dz*d);px=dx*d;pz=dz*d;py=y;if(drop!=0)g.log(dx*d,y+drop,dz*d,1);
               if(d>=Math.max(1,len-2))cluster(g,dx*d,y+drop,dz*d,1+(d==len?0:1),r,.18);}}}
         cluster(g,0,y,0,Math.max(1,sp-1),r,.12);}
      cluster(g,0,h,0,1,r,0);g.trim();return g;
   }
   static void connect(G g,int x0,int y0,int z0,int x1,int y1,int z1){
      int steps=Math.max(Math.abs(x1-x0),Math.max(Math.abs(y1-y0),Math.abs(z1-z0)));int x=x0,y=y0,z=z0;
      for(int i=1;i<=steps;i++){int nx=x0+Math.round((x1-x0)*i/(float)steps),ny=y0+Math.round((y1-y0)*i/(float)steps),nz=z0+Math.round((z1-z0)*i/(float)steps);
         while(y!=ny){y+=Integer.signum(ny-y);if(!g.logs.containsKey(G.k(x,y,z)))g.log(x,y,z,1);}
         while(x!=nx){x+=Integer.signum(nx-x);if(!g.logs.containsKey(G.k(x,y,z)))g.log(x,y,z,0);}
         while(z!=nz){z+=Integer.signum(nz-z);if(!g.logs.containsKey(G.k(x,y,z)))g.log(x,y,z,2);}}
   }
   static void cluster(G g,int cx,int cy,int cz,int rad,Random r,double rag){rad=Math.min(rad,3);
      for(int x=-rad;x<=rad;x++)for(int z=-rad;z<=rad;z++)for(int y=-1;y<=1;y++){double e=(x*x+z*z)/(rad*rad+.5)+Math.abs(y)*.62;
         if(!(e>1.15)&&(!(e>.62)||!(r.nextDouble()<.5+rag)))g.leaf(cx+x,cy+y,cz+z);}}
   static G wildBroad(Random r,float age,String log,String leaf,int low,int high,float fork,float reachK,float droop,float dome,int limbs0){
      G g=new G();g.wood=log;g.leaf=leaf;
      int h=Math.round(lerp(age,low,high))+r.nextInt(4);int f=Math.max(2,(int)(h*(fork+r.nextFloat()*.1f)));int rise=Math.max(3,h-f);
      int leader=Math.min(h-1,f+(int)(rise*(.74f+r.nextFloat()*.14f)));for(int y=0;y<=leader;y++)g.log(0,y,0,1);
      int limbs=limbs0+r.nextInt(age>.6f?3:2);double st=r.nextDouble()*6.283;int spread=2;
      for(int i=0;i<limbs;i++){double a=st+i*6.283/limbs+r.nextGaussian()*.26;int reach=Math.max(2,Math.min(8,Math.round(reachK*(lerp(age,3,7)+r.nextInt(2)))));
         int x=0,z=0,y=f,px=0,py=f,pz=0;
         for(int d=1;d<=reach;d++){double t=d/(double)reach;x=(int)Math.round(Math.cos(a)*d);z=(int)Math.round(Math.sin(a)*d);
            y=f+(int)Math.round(rise*Math.sqrt(t)*.92-droop*rise*t*t);int ax=d<reach*.4?1:Math.abs(Math.cos(a))>=Math.abs(Math.sin(a))?0:2;
            connect(g,px,py,pz,x,y,z);px=x;py=y;pz=z;g.log(x,y,z,ax);if(!g.logs.containsKey(G.k(x,y-1,z)))g.log(x,y-1,z,1);
            if(d==Math.max(2,reach/2)&&r.nextFloat()<.75f){double b=a+(r.nextBoolean()?1:-1)*(.7+r.nextFloat()*.5);int sx0=x,sy0=y,sz0=z;
               for(int e=1;e<=1+r.nextInt(3);e++){int sx=(int)Math.round(x+Math.cos(b)*e),sz=(int)Math.round(z+Math.sin(b)*e);int sy=y+(e>1?1:0);
                  connect(g,sx0,sy0,sz0,sx,sy,sz);sx0=sx;sy0=sy;sz0=sz;g.log(sx,sy,sz,Math.abs(Math.cos(b))>=Math.abs(Math.sin(b))?0:2);spread=Math.max(spread,Math.max(Math.abs(sx),Math.abs(sz)));}}
            spread=Math.max(spread,d);}}
      double cy=f+rise*.54,rx=Math.min(spread+.8,8),ry=Math.max(2.5,Math.min(h+1-cy,rise*dome+1));int ir=(int)Math.ceil(rx),iy=(int)Math.ceil(ry);
      for(int x=-ir;x<=ir;x++)for(int z=-ir;z<=ir;z++)for(int y=(int)(cy-iy);y<=cy+iy;y++){double dx=x/rx,dz=z/rx,dy=(y-cy)/ry,d=dx*dx+dy*dy+dz*dz;
         if(!(d>1)&&(!(d>.55)||!(r.nextFloat()<(d-.55)*1.9))&&(!(y<cy-ry*.45)||!(r.nextFloat()<.45)))g.leaf(x,y,z);}
      g.trim();return g;
   }
   static G oak(Random r){G g=new G();g.wood="oak_log";g.leaf="oak_leaves";int h=4+r.nextInt(3);for(int y=0;y<h;y++)g.log(0,y,0,1);
      for(int y=h-3;y<=h;y++){int rad=y>=h-1?1:2;for(int x=-rad;x<=rad;x++)for(int z=-rad;z<=rad;z++){if(Math.abs(x)==rad&&Math.abs(z)==rad&&(y==h||r.nextInt(2)==0))continue;g.leaf(x,y,z);}}
      g.trim();return g;}
   static G cherry(Random r){G g=new G();g.wood="cherry_log";g.leaf="cherry_leaves";int h=5+r.nextInt(2);for(int y=0;y<h;y++)g.log(0,y,0,1);
      // two bent branches like vanilla cherry
      for(int s=-1;s<=1;s+=2){int x=0;for(int i=1;i<=3;i++){x+=s;g.log(x,h-2+i/2,0,0);}g.log(x,h,0,1);g.log(x,h+1,0,1);
         for(int y=h;y<=h+3;y++){int rad=y==h+3?2:y==h?3:4;for(int dx=-rad;dx<=rad;dx++)for(int dz=-rad;dz<=rad;dz++)if(dx*dx+dz*dz<=rad*rad+1&&r.nextInt(8)!=0)g.leaf(x+dx,y,dz);}}
      for(int y=h;y<=h+2;y++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)g.leaf(dx,y,dz);
      g.trim();return g;}

   record Fx(String name,Blue world,int x,int z){}
   static List<Fx> fixtures(){
      List<Fx> out=new ArrayList<>();
      String[] names={"alpine_spruce","alpine_fir","alpine_pine","alpine_aspen","alpine_birch","alpine_maple","alpine_larch","wild_pine","wild_fir","wild_aspen","wild_birch","wild_maple","oak","cherry"};
      for(String n:names)for(int seed=0;seed<4;seed++){
         Random r=new Random(seed*7919L+n.hashCode());float age=new float[]{.25f,.55f,.8f,1f}[seed];
         G g=switch(n){
            case "alpine_spruce"->spire(r,age,false,"alpine_spruce_log","spruce_boughs");
            case "alpine_fir"->spire(r,age,true,"alpine_spruce_log","fir_needles");
            case "alpine_larch"->spire(r,age,true,"alpine_pine_log","larch_needles");
            case "alpine_pine"->pine(r,age);
            case "alpine_aspen"->aspen(r,age,"aspen_log","aspen_leaves");
            case "alpine_birch"->birch(r,age);
            case "alpine_maple"->maple(r,age,"maple_leaves");
            case "wild_pine"->wildConifer(r,age,true);
            case "wild_fir"->wildConifer(r,age,false);
            case "wild_aspen"->wildBroad(r,age,"aspen_log","aspen_leaves",11,27,.46f,1f,0,.58f,3);
            case "wild_birch"->wildBroad(r,age,"birch_log","birch_leaves",13,29,.6f,.78f,0,.5f,3);
            case "wild_maple"->wildBroad(r,age,"alpine_maple_log","maple_leaves",10,24,.36f,1.3f,.1f,.72f,4);
            case "oak"->oak(r);
            default->cherry(r);
         };
         int ox=new int[]{3,-9,14,-1}[seed],oz=new int[]{5,15,-7,0}[seed];
         Blue w=new Blue();g.into(w,ox,oz);out.add(new Fx(n,w,ox,oz));
      }
      return out;
   }
   static String group(String n){
      if(n.contains("birch")||n.contains("aspen"))return "birch-like(1)";
      if(n.equals("cherry"))return "cherry(2)";
      if(n.contains("spruce")||n.contains("fir")||n.contains("pine")||n.contains("larch"))return "conifer";
      return "broadleaf(0)";
   }
   static long count(java.util.Map<Long,List<TreeShape.Quad>> cells,int[] split){
      long n=0;for(var l:cells.values())for(var q:l){n++;if(q.texture==2)split[1]++;else split[0]++;}return n;
   }
   /** Unsafe = a vertex outside the section range Sodium can encode (must stay 0). */
   static long unsafe(java.util.Map<Long,List<TreeShape.Quad>> cells){
      long bad=0;
      for(var e:cells.entrySet()){long key=e.getKey();int hx=(int)(key>>38),hy=(int)(key<<52>>52),hz=(int)(key<<26>>38);
         for(var qd:e.getValue())for(int i=0;i<4;i++)for(int ax=0;ax<3;ax++){float p=qd.v[i*8+ax]+((ax==0?hx:ax==1?hy:hz)&15);if(!Float.isFinite(p)||p<-7.5f||p>23.5f)bad++;}}
      return bad;
   }
   /** Writes every quad of some fixtures (world coordinates) for tools/perf/impostor_preview.py. */
   static void dump(String file) throws java.io.IOException{
      try(var w=new java.io.PrintWriter(file)){
         List<Fx> fx=fixtures();
         for(Fx f:fx){
            TreeGrowth.clear();
            TreeGrowth.Tree t=TreeGrowth.lookup(f.world,f.x,1,f.z,null,TreeGrowth.LOD_NEAR);
            if(t==null)continue;
            String[] names={"near","far","impostor"};
            List<java.util.Map<Long,List<TreeShape.Quad>>> levels=List.of(t.cells,t.far,t.impostor);
            for(int l=0;l<3;l++)for(var e:levels.get(l).entrySet()){long key=e.getKey();int hx=(int)(key>>38),hy=(int)(key<<52>>52),hz=(int)(key<<26>>38);
               for(var q:e.getValue()){StringBuilder b=new StringBuilder(f.name+" "+f.x+" "+f.z+" "+names[l]+" "+q.texture);
                  for(int i=0;i<4;i++)b.append(String.format(Locale.ROOT," %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f",q.v[i*8]+hx,q.v[i*8+1]+hy,q.v[i*8+2]+hz,q.v[i*8+3],q.v[i*8+4],q.v[i*8+5],q.v[i*8+6],q.v[i*8+7]));
                  w.println(b);}}
         }
      }
   }
   public static void main(String[] a) throws Exception{
      if(a.length>1&&a[0].equals("dump")){dump(a[1]);return;}
      boolean verbose=a.length>0;
      List<Fx> fx=fixtures();
      Map<String,long[]> agg=new TreeMap<>();
      long unsafe=0,missing=0,woodOnLeaves=0,foliageOnLogs=0,hosts=0;
      long tFull=0,tFar=0,tImp=0;int reps=3;
      for(Fx f:fx){
         TreeGrowth.clear();
         TreeGrowth.Tree t=TreeGrowth.lookup(f.world,f.x,1,f.z,null,TreeGrowth.LOD_NEAR);
         if(t==null){missing++;System.out.println("MISSING "+f.name);continue;}
         int[] ns=new int[2],fs=new int[2],is=new int[2];
         long near=count(t.cells,ns),far=count(t.far,fs),imp=count(t.impostor,is);
         unsafe+=unsafe(t.cells)+unsafe(t.far)+unsafe(t.impostor);
         for(var e:t.impostor.entrySet()){long key=e.getKey();int k=f.world.kind((int)(key>>38),(int)(key<<52>>52),(int)(key<<26>>38));
            for(var q:e.getValue()){if(q.texture!=2&&k==TreeShape.LEAVES)woodOnLeaves++;if(q.texture==2&&k==TreeShape.LOG)foliageOnLogs++;}
            hosts+=t.impostor.size();}
         // growth cost: full (near+far+impostor) vs impostor-only
         for(int i=0;i<reps;i++){TreeGrowth.clear();long s=System.nanoTime();TreeGrowth.lookup(f.world,f.x,1,f.z,null,TreeGrowth.LOD_NEAR);tFull+=System.nanoTime()-s;
            TreeGrowth.clear();s=System.nanoTime();TreeGrowth.lookup(f.world,f.x,1,f.z,null,TreeGrowth.LOD_FAR);tFar+=System.nanoTime()-s;
            TreeGrowth.clear();s=System.nanoTime();TreeGrowth.Tree only=TreeGrowth.lookup(f.world,f.x,1,f.z,null,TreeGrowth.LOD_IMPOSTOR);tImp+=System.nanoTime()-s;
            if(i==0&&only!=null&&count(only.impostor,new int[2])!=imp)System.out.println("IMPOSTOR-ONLY MISMATCH "+f.name);}
         String g=group(f.name);
         for(String key:new String[]{g,"ALL"}){long[] s=agg.computeIfAbsent(key,k->new long[8]);s[0]++;s[1]+=near;s[2]+=far;s[3]+=imp;s[4]=Math.max(s[4],imp);s[5]+=is[0];s[6]+=is[1];}
         if(verbose)System.out.printf(Locale.ROOT,"%-14s near=%6d far=%6d impostor=%3d (wood %d, foliage %d)%n",f.name,near,far,imp,is[0],is[1]);
      }
      System.out.println("group          trees  NEAR/tree  FAR/tree  IMPOSTOR/tree (max)  far/near  impostor/far  impostor/near");
      for(var e:agg.entrySet()){long[] s=e.getValue();
         System.out.printf(Locale.ROOT,"%-14s %5d %10d %9d %8d (%3d)  %8.1f%% %11.2f%% %12.3f%%%n",e.getKey(),s[0],s[1]/s[0],s[2]/s[0],s[3]/s[0],s[4],
            100.0*s[2]/s[1],100.0*s[3]/Math.max(1,s[2]),100.0*s[3]/s[1]);}
      System.out.printf(Locale.ROOT,"grow ms/tree (chunk-build CPU): all levels %.2f, far+impostor %.2f, impostor only %.2f%n",tFull/1e6/reps/fx.size(),tFar/1e6/reps/fx.size(),tImp/1e6/reps/fx.size());
      System.out.printf(Locale.ROOT,"impostor host blocks/tree=%.1f%n",hosts/(double)fx.size());
      System.out.println("unsafe="+unsafe+" impostorWoodOnLeaves="+woodOnLeaves+" impostorFoliageOnLogs="+foliageOnLogs+" missing="+missing);
   }
}
