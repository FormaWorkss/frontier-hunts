import com.formaworks.frontierhunts.landscape.AlpineLayout;
public class Acc {
  public static void main(String[] a){
    long seed=Long.parseLong(a[0]); int sx=Integer.parseInt(a[1]), sz=Integer.parseInt(a[2]);
    AlpineLayout L=new AlpineLayout(seed,21);
    int tot=0,wetRej=0; long t0=System.nanoTime();
    java.util.Map<Integer,Integer> hist=new java.util.TreeMap<>();
    for(int cx=-60;cx<60;cx+=6)for(int cz=-60;cz<60;cz+=6){
      int x=cx*16, z=cz*16; tot++;
      int n=5; int[] hs=new int[n*n]; int k=0,wet=0;
      for(int ix=0;ix<n;ix++)for(int iz=0;iz<n;iz++){var s=L.sample(x+(sx-1)*ix/(n-1), z+(sz-1)*iz/(n-1)); hs[k++]=s.floor(); if(s.wet())wet++;}
      java.util.Arrays.sort(hs); int r=hs[hs.length-1]-hs[0];
      if(wet>0){wetRej++;continue;}
      hist.merge(Math.min(r,30),1,Integer::sum);
    }
    System.out.printf("seed %d size %dx%d: candidates %d wet %d  %.1f ms/candidate%n",seed,sx,sz,tot,wetRej,(System.nanoTime()-t0)/1e6/tot);
    int c=0; StringBuilder b=new StringBuilder(); for(var e:hist.entrySet()){c+=e.getValue(); b.append(" <=").append(e.getKey()).append(":").append(c);} System.out.println("cumulative dry by relief:"+b);
  }
}
