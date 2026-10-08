package com.formaworks.frontierhunts.client.tree;

import java.util.*;

/**
 * [trees2] Offline look at every realistic-tree species and age on flat ground and on slopes, plus the
 * breaking (scar) path. Pure harness (no Minecraft): grows the trees with TreeGrowth through a fake world,
 * reports base / bulge metrics and dumps quads for tools/trees2/tree_preview.py.
 *
 * <pre>
 * bash /home/claude/fh/tools/compile.sh REPO /tmp/cc
 * javac -proc:none -d /tmp/tl -cp /tmp/cc:/home/claude/fh/orig62.jar REPO/tools/perf/TreeLodBench.java REPO/tools/trees2/TreeLook.java
 * java -cp /tmp/tl:/tmp/cc:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.TreeLook [report|dump FILE|chop]
 * </pre>
 */
public final class TreeLook {
   /** A world with a stepped height field (ground at y <= height(x, z)) and a tree's blocks. */
   static final class Hill implements TreeGrowth.World {
      final Map<Long,Integer> kind=new HashMap<>(), axis=new HashMap<>();
      final Map<Long,String> name=new HashMap<>();
      final float gx, gz; final int base;
      Hill(float gx,float gz,int base){this.gx=gx;this.gz=gz;this.base=base;}
      int pit; // a hollow this deep under the trunk column (soil built up around a trunk after it was placed)
      int height(int x,int z){return base+(int)Math.floor(x*gx+z*gz)+(pit>0&&(x!=0||z!=0)&&Math.abs(x)<=2&&Math.abs(z)<=2?pit:0);}
      public int kind(int x,int y,int z){Integer k=kind.get(TreeGrowth.pack(x,y,z));if(k!=null)return k;return y<=height(x,z)?TreeShape.GROUND:TreeShape.AIR;}
      public int axis(int x,int y,int z){return axis.getOrDefault(TreeGrowth.pack(x,y,z),1);}
      public int light(int x,int y,int z){return 15<<20;}
      public boolean conifer(int x,int y,int z){return TreeLodBench.isConifer(name.getOrDefault(TreeGrowth.pack(x,y,z),"oak_log"));}
      public Object species(int x,int y,int z){return name.getOrDefault(TreeGrowth.pack(x,y,z),"air");}
      public int crownForm(int x,int y,int z){String p=name.getOrDefault(TreeGrowth.pack(x,y,z),"");
         return p.contains("cherry")||p.contains("azalea")?2:p.contains("birch")||p.contains("aspen")?1:0;}
      void set(int x,int y,int z,int k,int ax,String n){long p=TreeGrowth.pack(x,y,z);if(k==TreeShape.AIR){kind.put(p,k);axis.remove(p);name.remove(p);return;}kind.put(p,k);axis.put(p,ax);name.put(p,n);}
   }

   record Fx(String name,int age,String ground,Hill world,int x,int y,int z){}

   /** WildTrees.buttress (old wild trees) / AlpineTrees.flare: horizontal logs beside the trunk foot. */
   static void buttress(TreeLodBench.G g,Random r,float age,boolean alpine){
      int[][] d={{1,0},{0,1},{-1,0},{0,-1}};
      for(int[] s:d){
         if(alpine){ if(r.nextFloat()<.45f)g.log(s[0],0,s[1],s[0]!=0?0:2); continue; }
         if(!(r.nextFloat()>lerp(age,.3f,.9f))){g.log(s[0],0,s[1],s[0]!=0?0:2);
            if(age>.8f&&r.nextBoolean()&&!g.logs.containsKey(TreeLodBench.G.k(s[0]*2,0,s[1]*2)))g.log(s[0]*2,0,s[1]*2,s[0]!=0?0:2);}
      }
   }
   static float lerp(float t,float a,float b){return a+(b-a)*t;}

   static final String[] SPECIES={"alpine_spruce","alpine_fir","alpine_pine","alpine_aspen","alpine_birch","alpine_maple","alpine_larch",
      "wild_pine","wild_fir","wild_aspen","wild_birch","wild_maple","oak","cherry"};
   static final float[] AGES={.25f,.55f,.8f,1f};

   static TreeLodBench.G blueprint(String n,int seed){
      Random r=new Random(seed*7919L+n.hashCode());float age=AGES[seed];
      TreeLodBench.G g=switch(n){
         case "alpine_spruce"->TreeLodBench.spire(r,age,false,"alpine_spruce_log","spruce_boughs");
         case "alpine_fir"->TreeLodBench.spire(r,age,true,"alpine_spruce_log","fir_needles");
         case "alpine_larch"->TreeLodBench.spire(r,age,true,"alpine_pine_log","larch_needles");
         case "alpine_pine"->TreeLodBench.pine(r,age);
         case "alpine_aspen"->TreeLodBench.aspen(r,age,"aspen_log","aspen_leaves");
         case "alpine_birch"->TreeLodBench.birch(r,age);
         case "alpine_maple"->TreeLodBench.maple(r,age,"maple_leaves");
         case "wild_pine"->TreeLodBench.wildConifer(r,age,true);
         case "wild_fir"->TreeLodBench.wildConifer(r,age,false);
         case "wild_aspen"->TreeLodBench.wildBroad(r,age,"aspen_log","aspen_leaves",11,27,.46f,1f,0,.58f,3);
         case "wild_birch"->TreeLodBench.wildBroad(r,age,"birch_log","birch_leaves",13,29,.6f,.78f,0,.5f,3);
         case "wild_maple"->TreeLodBench.wildBroad(r,age,"alpine_maple_log","maple_leaves",10,24,.36f,1.3f,.1f,.72f,4);
         case "oak"->TreeLodBench.oak(r);
         default->TreeLodBench.cherry(r);
      };
      // the real generators add foot logs to old trees (the bench ports leave them out)
      if(n.startsWith("wild_")&&age>(n.contains("pine")||n.contains("fir")?.6f:.5f))buttress(g,r,age,false);
      if(n.equals("alpine_spruce")&&age>.7f&&r.nextFloat()<.6f)buttress(g,r,age,true);
      return g;
   }

   /**
    * Places a blueprint like ForestStand / AlpineForest.plant: origin one above the surface at x, z; at least two of
    * the four ground blocks beside the foot solid; every log in air (else the tree is not planted: null).
    */
   static Fx place(String n,int seed,String ground,float gx,float gz){
      TreeLodBench.G g=blueprint(n,seed);
      Hill w=new Hill(gx,gz,0);
      if(ground.startsWith("pit"))w.pit=ground.charAt(3)-'0';
      int ox=0,oz=0,oy=w.height(ox,oz)+1;
      int footing=0;for(int[] s:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(w.height(ox+s[0],oz+s[1])>=oy-1)footing++;
      if(footing<2)return null;
      if(w.pit>0)for(int[] l:new ArrayList<>(g.logs.values()))if(w.kind(l[0]+ox,l[1]+oy,l[2]+oz)!=TreeShape.AIR)g.logs.remove(TreeLodBench.G.k(l[0],l[1],l[2]));
      for(int[] l:g.logs.values())if(w.kind(l[0]+ox,l[1]+oy,l[2]+oz)!=TreeShape.AIR)return null;
      for(int[] l:g.logs.values())w.set(l[0]+ox,l[1]+oy,l[2]+oz,TreeShape.LOG,l[3],g.wood);
      for(long p:g.leaves){int x=(int)(p>>38),y=(int)(p<<52>>52),z=(int)(p<<26>>38);
         if(w.kind(x+ox,y+oy,z+oz)==TreeShape.AIR)w.set(x+ox,y+oy,z+oz,TreeShape.LEAVES,1,g.leaf);}
      return new Fx(n,seed,ground,w,ox,oy,oz);
   }

   static List<Fx> fixtures(){
      List<Fx> out=new ArrayList<>();
      for(String n:SPECIES)for(int seed=0;seed<4;seed++){
         out.add(place(n,seed,"flat",0,0));
         // slopes: a gentle one (1 in 2) and a steep one (1 in 1, stepped every block) along x, and a diagonal one
         for(Object[] s:new Object[][]{{"slope50",.5f,0f},{"slope100",1f,0f},{"diag70",.5f,.5f},{"pit1",0f,0f}}){
            Fx f=place(n,seed,(String)s[0],(float)s[1],(float)s[2]);
            if(f!=null)out.add(f);
         }
      }
      return out;
   }

   static TreeGrowth.Tree grow(Fx f){
      TreeGrowth.clear();
      return TreeGrowth.lookup(f.world,f.x,f.y,f.z,null,TreeGrowth.LOD_NEAR);
   }

   // ------------------------------------------------------------------ metrics
   /** Per wood quad vertex near the foot: how far it hangs below the support level over lower (downhill) columns, and gaps. */
   static float[] baseMetrics(Fx f,TreeGrowth.Tree t){
      float skirt=0,gap=0,overhang=0;
      int y0=f.y;
      for(var e:t.cells.entrySet()){long key=e.getKey();int hx=(int)(key>>38),hy=(int)(key<<52>>52),hz=(int)(key<<26>>38);
         for(var q:e.getValue()){if(q.texture==2)continue;
            for(int i=0;i<4;i++){float x=q.v[i*8]+hx,y=q.v[i*8+1]+hy,z=q.v[i*8+2]+hz;
               if(y>y0+1.2f||Math.abs(x-f.x-.5f)>2.5f||Math.abs(z-f.z-.5f)>2.5f)continue;
               int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
               int gtop=f.world.height(bx,bz)+1; // ground surface over that column
               if(gtop<y0&&y<y0-.02f)skirt=Math.max(skirt,y0-y);        // bark hanging below the foot block over a lower column
               if(gtop<y0)overhang=Math.max(overhang,(float)Math.hypot(x-f.x-.5f,z-f.z-.5f)-.5f); // base reaching out over a drop
            }}}
      return new float[]{skirt,overhang};
   }

   /** Trunk radius profile of stem 0 from the NEAR wood quads (horizontal distance of trunk vertices from the axis). */
   static float[] profile(Fx f,TreeGrowth.Tree t,float[] heights){
      float[] r=new float[heights.length];
      float cx=f.x+.5f,cz=f.z+.5f;
      for(var e:t.cells.entrySet()){long key=e.getKey();int hx=(int)(key>>38),hy=(int)(key<<52>>52),hz=(int)(key<<26>>38);
         for(var q:e.getValue()){if(q.texture==2)continue;
            for(int i=0;i<4;i++){float x=q.v[i*8]+hx-cx,y=q.v[i*8+1]+hy-f.y,z=q.v[i*8+2]+hz-cz;float d=(float)Math.hypot(x,z);
               if(d>1.6f)continue;
               for(int k=0;k<heights.length;k++)if(Math.abs(y-heights[k])<.13f)r[k]=Math.max(r[k],d);}}}
      return r;
   }

   static void report(){
      List<Fx> fx=fixtures();
      int missing=0;long quads=0;int n=0;
      System.out.println("fixture                       quads  wood  skirt  overhang  r(-.1) r(0) r(.25) r(.5) r(1) r(2)");
      for(Fx f:fx){
         TreeGrowth.Tree t=grow(f);
         if(t==null){missing++;System.out.println("MISSING "+f.name+" "+f.age+" "+f.ground);continue;}
         int[] split=new int[2];long q=TreeLodBench.count(t.cells,split);quads+=q;n++;
         float[] b=baseMetrics(f,t);
         float[] r=profile(f,t,new float[]{-.1f,0f,.25f,.5f,1f,2f});
         System.out.printf(Locale.ROOT,"%-14s %d %-9s %6d %5d  %5.2f  %6.2f    %.2f  %.2f  %.2f  %.2f  %.2f  %.2f%n",f.name,f.age,f.ground,q,split[0],b[0],b[1],r[0],r[1],r[2],r[3],r[4],r[5]);
      }
      System.out.println("trees="+n+" missing="+missing+" mean NEAR quads="+(n==0?0:quads/n));
   }

   /** Dumps quads of every fixture (world coordinates) and its ground columns. */
   static String LEVEL="near";
   static void dump(String file,String only) throws java.io.IOException{
      try(var w=new java.io.PrintWriter(file)){
         for(Fx f:fixtures()){
            if(only!=null&&!(f.name+"/"+f.age+"/"+f.ground).matches(only))continue;
            TreeGrowth.Tree t=grow(f);if(t==null)continue;
            String id=f.name+"/"+f.age+"/"+f.ground;
            w.println("T "+id+" "+f.x+" "+f.y+" "+f.z);
            for(int x=f.x-6;x<=f.x+6;x++)for(int z=f.z-6;z<=f.z+6;z++)w.println("G "+id+" "+x+" "+z+" "+f.world.height(x,z));
            var level=LEVEL.equals("far")?t.far:LEVEL.equals("impostor")?t.impostor:t.cells;
            for(var e:level.entrySet()){long key=e.getKey();int hx=(int)(key>>38),hy=(int)(key<<52>>52),hz=(int)(key<<26>>38);
               for(var q:e.getValue()){StringBuilder b=new StringBuilder("Q "+id+" "+q.texture);
                  for(int i=0;i<4;i++)b.append(String.format(Locale.ROOT," %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f",q.v[i*8]+hx,q.v[i*8+1]+hy,q.v[i*8+2]+hz,q.v[i*8+3],q.v[i*8+4],q.v[i*8+5],q.v[i*8+6],q.v[i*8+7]));
                  w.println(b);}}
         }
      }
   }

   // ------------------------------------------------------------------ breaking (scars)
   static Map<Long,Integer> cellCounts(TreeGrowth.Tree t){Map<Long,Integer> m=new TreeMap<>();for(var e:t.cells.entrySet())m.put(e.getKey(),e.getValue().size());return m;}
   static String sig(TreeGrowth.Tree t,Set<Long> skip){
      StringBuilder b=new StringBuilder();
      for(var e:new TreeMap<>(t.cells).entrySet()){if(skip.contains(e.getKey()))continue;b.append(e.getKey()).append(':');
         for(var q:e.getValue())for(int i=0;i<32;i++)b.append(Math.round(q.v[i]*1000)).append(',');}
      return Integer.toHexString(b.toString().hashCode())+"/"+b.length();
   }
   /**
    * Chops every fixture tree (flat and slope) log by log from the foot up, the way a player fells it, then lets every
    * leaf decay. After every broken block: the cached tree must still be returned (no regrowth, no fallback to cubes);
    * a regrowth forced by eviction must give the same geometry for every block still standing; and once the ghosts are
    * forgotten (a new session) a floating remnant must still grow (cut stem) instead of turning into cubes.
    */
   static void chop(){
      int trees=0,steps=0,kept=0,lost=0,cubes=0,felled=0,sameAfterEvict=0,diffAfterEvict=0,remnants=0,remnantFail=0;
      for(Fx f:fixtures()){
         if(!f.ground.equals("flat")&&!f.ground.equals("slope100"))continue;
         TreeScars.clear();
         TreeGrowth.Tree t=grow(f);if(t==null)continue;trees++;
         String full=sig(t,Set.of());
         // logs bottom-up (the trunk first), then a few leaves
         List<Long> logs=new ArrayList<>(),leaves=new ArrayList<>();
         for(int i=0;i<t.members.length;i++)((t.expect[i]&7)==TreeShape.LOG?logs:leaves).add(t.members[i]);
         logs.sort(Comparator.comparingInt(c->(int)(c<<52>>52)));
         Set<Long> broken=new HashSet<>();
         List<Long> order=new ArrayList<>(logs.subList(0,Math.min(logs.size(),8)));
         for(int i=0;i<Math.min(12,leaves.size());i++)order.add(leaves.get(i*leaves.size()/12));
         for(long c:order){
            int x=(int)(c>>38),y=(int)(c<<52>>52),z=(int)(c<<26>>38);
            f.world.set(x,y,z,TreeShape.AIR,1,null);broken.add(c);steps++;
            // a block of the tree still standing asks for its share (the section rebuild after the break)
            long ask=-1;for(long m:t.members)if(!broken.contains(m)){ask=m;break;}
            TreeGrowth.Tree now=TreeGrowth.lookup(f.world,(int)(ask>>38),(int)(ask<<52>>52),(int)(ask<<26>>38),new Object(),TreeGrowth.LOD_NEAR);
            if(now==t)kept++;else{lost++;if(now==null)cubes++;if(TreeScars.maxGhosts>0)System.out.println("REGROWN/LOST "+f.name+" "+f.age+" "+f.ground+" after breaking "+x+","+y+","+z+" -> "+(now==null?"cubes":"new tree"));}
         }
         // eviction: grown again through the ghosts, identical for every standing block
         TreeGrowth.clear();
         long ask=-1;for(long m:t.members)if(!broken.contains(m)){ask=m;break;}
         TreeGrowth.Tree again=TreeGrowth.lookup(f.world,(int)(ask>>38),(int)(ask<<52>>52),(int)(ask<<26>>38),null,TreeGrowth.LOD_NEAR);
         if(broken.containsAll(logs)){felled++;}
         else if(again!=null&&sig(again,broken).equals(sig(grow0(f,broken,full),broken)))sameAfterEvict++;else{diffAfterEvict++;System.out.println("EVICT MISMATCH "+f.name+" "+f.age+" "+f.ground);}
         // a later session: no ghosts, the floating remainder is a cut remnant (if a crown is left)
         TreeScars.clear();TreeGrowth.clear();
         long top=-1;for(long m:logs)if(!broken.contains(m))top=m;
         if(top!=-1){TreeGrowth.Tree rem=TreeGrowth.lookup(f.world,(int)(top>>38),(int)(top<<52>>52),(int)(top<<26>>38),null,TreeGrowth.LOD_NEAR);
            if(rem!=null)remnants++;else remnantFail++;}
      }
      // fell a tree completely, replant at the stump and grow the sapling at once (bonemeal): the new tree must be
      // its own, not shaped by the felled tree's ghosts
      int replantSame=0,replantDiff=0;
      for(Fx f:fixtures()){
         if(!f.ground.equals("flat"))continue;
         TreeScars.clear();
         TreeGrowth.Tree t=grow(f);if(t==null)continue;
         for(int i=0;i<t.members.length;i++)if((t.expect[i]&7)==TreeShape.LOG){long c=t.members[i];
            f.world.set((int)(c>>38),(int)(c<<52>>52),(int)(c<<26>>38),TreeShape.AIR,1,null);
            long ask=-1;for(int j=0;j<t.members.length;j++)if((t.expect[j]&7)==TreeShape.LEAVES){ask=t.members[j];break;}
            if(ask!=-1)TreeGrowth.lookup(f.world,(int)(ask>>38),(int)(ask<<52>>52),(int)(ask<<26>>38),new Object(),TreeGrowth.LOD_NEAR);}
         TreeLodBench.G sap=blueprint("alpine_aspen",0);
         for(int[] l:sap.logs.values())f.world.set(l[0]+f.x,l[1]+f.y,l[2]+f.z,TreeShape.LOG,l[3],sap.wood);
         for(long p:sap.leaves){int x=(int)(p>>38),y=(int)(p<<52>>52),z=(int)(p<<26>>38);
            if(f.world.kind(x+f.x,y+f.y,z+f.z)==TreeShape.AIR&&!TreeScars.ghost(TreeGrowth.pack(x+f.x,y+f.y,z+f.z)))f.world.set(x+f.x,y+f.y,z+f.z,TreeShape.LEAVES,1,sap.leaf);}
         TreeGrowth.Tree young=TreeGrowth.lookup(f.world,f.x,f.y,f.z,new Object(),TreeGrowth.LOD_NEAR);
         String a=young==null?"null":sig(young,Set.of())+"/"+young.members.length;
         TreeScars.clear();TreeGrowth.clear();
         TreeGrowth.Tree fresh=TreeGrowth.lookup(f.world,f.x,f.y,f.z,null,TreeGrowth.LOD_NEAR);
         String b=fresh==null?"null":sig(fresh,Set.of())+"/"+fresh.members.length;
         if(a.equals(b))replantSame++;else{replantDiff++;System.out.println("REPLANT DIFF "+f.name+" "+f.age+" "+a+" vs "+b);}
      }
      System.out.println("replant: same="+replantSame+" different="+replantDiff);
      System.out.printf(Locale.ROOT,"chop: trees=%d breaks=%d keptCachedTree=%d regrownOrLost=%d (of which cubes %d) evictRegrowSame=%d evictMismatch=%d felledCompletely=%d remnantGrown=%d remnantCubes=%d%n",
         trees,steps,kept,lost,cubes,sameAfterEvict,diffAfterEvict,felled,remnants,remnantFail);
   }
   /** The original tree's signature for comparison (grown on a copy of the world before any break). */
   static TreeGrowth.Tree grow0(Fx f,Set<Long> broken,String full){
      Hill w=new Hill(f.world.gx,f.world.gz,f.world.base);w.kind.putAll(f.world.kind);w.axis.putAll(f.world.axis);w.name.putAll(f.world.name);
      // restore the broken blocks from the ghosts' view: regrow on the intact copy
      Fx g=place(f.name,f.age,f.ground,f.world.gx,f.world.gz);
      TreeScars.clear();TreeGrowth.clear();
      TreeGrowth.Tree t=TreeGrowth.lookup(g.world,g.x,g.y,g.z,null,TreeGrowth.LOD_NEAR);
      return t;
   }

   public static void main(String[] a) throws Exception{
      String mode=a.length>0?a[0]:"report";
      switch(mode){
         case "dump"->{if(a.length>3)LEVEL=a[3];dump(a[1],a.length>2?a[2]:null);}
         case "chop"->{if(a.length>1&&a[1].equals("noghosts"))TreeScars.maxGhosts=0;chop();}
         default->report();
      }
   }
}
