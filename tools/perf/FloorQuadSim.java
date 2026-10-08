/** [perf] Expected forest-floor dressing quads per shaded ground block (mirrors ForestFloorModel.dress probabilities). */
public final class FloorQuadSim {
   static long state;
   static float next(){long z=(state+=0x9E3779B97F4A7C15L);z=(z^z>>>30)*0xBF58476D1CE4E5B9L;z=(z^z>>>27)*0x94D049BB133111EBL;z^=z>>>31;return (z>>>40)/(float)(1L<<24);}
   static int dress(long seed,int covered,boolean conifer,boolean litter,boolean far){
      state=seed;float shade=covered/9f;int q=0;
      int decals=litter?3:covered>=6?2:covered>=3?1:next()<.55f?1:0;
      if(far&&decals>1)decals=1;
      for(int i=0;i<decals;i++){ if(conifer){ if(!(next()<.8f)) {} } else {next();} next();next();next();next();next(); q++; }
      if(next()<.06f+.16f*shade){next();next();next();next();next();q++;}
      if(far)return q;
      if(next()<.12f){next();next();next();next();q+=5;}
      if(conifer&&next()<.1f){int n=next()<.3f?2:1;for(int i=0;i<n;i++){next();next();next();q+=5;}}
      if(next()<.035f*shade){next();next();next();next();next();q+=4;}
      if(next()<.025f){for(int i=0;i<6;i++)next();q+=5;}
      return q;
   }
   public static void main(String[] a){
      for(boolean conifer:new boolean[]{false,true})for(int covered:new int[]{1,4,7,9}){
         long n=0,f=0;int N=200000;java.util.Random r=new java.util.Random(1);
         for(int i=0;i<N;i++){long s=r.nextLong();n+=dress(s,covered,conifer,false,false);f+=dress(s,covered,conifer,false,true);}
         System.out.printf("conifer=%b covered=%d near=%.2f far=%.2f (%.0f%%)%n",conifer,covered,n/(double)N,f/(double)N,100.0*f/n);
      }
   }
}
