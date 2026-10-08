package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Branch-attached foliage, generated once per tree. No block-face canopy shell.
 * Geometry budgets are bounded by branch counts, not by the number of leaf blocks. */
public final class TreeCrown {
    /** [perf3] points: x, y, z of n points (flat), radii: n radii. The arrays are reused after the call returns. */
    @FunctionalInterface public interface Branch { void add(float[] points,float[] radii,int n); }
    /**
     * Receives the crown's limbs and its foliage cards (instead of double-sided quads in a list), so the
     * grower can emit a distance level of detail next to the full crown.
     */
    public interface Sink extends Branch {
        /**
         * One foliage card: centre c, unit axes u and v, full size width x height.
         * @param far this card also represents its spray in the reduced (distant) crown
         */
        void card(float[] c,float[] u,float[] v,float width,float height,boolean far);
    }
    /**
     * Share of each spray's cards the distant crown keeps (the grower scales them up around their centres
     * so the crown keeps its coverage). Selection never consumes the random stream, so the full crown is
     * identical with or without a distant one.
     */
    static final float FAR_KEEP=.30f;
    private final Random random;
    private final List<TreeShape.Quad> out;
    private final Branch branch;
    private final Sink sink;
    private boolean needles;
    private float leafBase=Float.NaN;
    private int sprays;
    private TreeCrown(long seed,List<TreeShape.Quad> out,Branch branch) {
        this.random=new Random(seed);this.out=out;this.branch=branch;this.sink=branch instanceof Sink s?s:null;
    }
    public static void build(long seed,float x,float y,float z,float height,float spread,float trunkRadius,
                             boolean conifer,int form,List<TreeShape.Quad> out,Branch branch) {
        build(seed,x,y,z,height,spread,trunkRadius,conifer,form,Float.NaN,out,branch);
    }
    /** @param leafBase height above y of the blueprint's lowest leaves (NaN: unknown). Needle tiers
     * start there, so a high-crowned pine is not dressed down to the ground with foliage that has no
     * leaf block to host it (log-hosted foliage never receives shader wind). */
    public static void build(long seed,float x,float y,float z,float height,float spread,float trunkRadius,
                             boolean conifer,int form,float leafBase,List<TreeShape.Quad> out,Branch branch) {
        var crown=new TreeCrown(seed,out,branch);
        crown.needles=conifer;
        crown.leafBase=leafBase;
        if(conifer)crown.conifer(x,y,z,height,spread,trunkRadius);
        else crown.broadleaf(x,y,z,height,spread,trunkRadius,form);
    }
    // [perf3] reused buffers: one card's centre and axes (handed to the sink, which copies what it keeps), and the
    // points of the limbs being shaped, one slot per nesting depth (a bough's points stay valid while its twigs grow)
    private final float[] cardC=new float[3],cardU=new float[3],cardV=new float[3];
    private final float[][] limbPoints=new float[3][27],limbRadii=new float[3][9];
    private final int[] limbCount=new int[3];
    private float r(float low,float high){return low+random.nextFloat()*(high-low);}
    private static float[] p(float x,float y,float z){return new float[]{x,y,z};}
    private static float[] mix(float[] a,float[] b,float t){return p(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t);}
    /** [perf3] Shapes a limb into the points buffer of {@code slot} (its nesting depth) and returns the slot. */
    private int limb(float[] a,float[] middle,float[] b,float radius,int slot) {
        float[] points=limbPoints[slot],radii=limbRadii[slot];
        // Thin twigs are short: one or two straight-ish segments read the same as four and cost
        // a third of the faces (every segment is a full ring of quads).
        int steps=needles?(radius>.06f?4:radius>.035f?2:1):(radius>.08f?8:radius>.03f?4:2);
        for(int i=0;i<=steps;i++) {
            float t=i/(float)steps,u=1-t;
            points[i*3]=u*u*a[0]+2*u*t*middle[0]+t*t*b[0];
            points[i*3+1]=u*u*a[1]+2*u*t*middle[1]+t*t*b[1];
            points[i*3+2]=u*u*a[2]+2*u*t*middle[2]+t*t*b[2];
            radii[i]=Math.max(.008f,radius*(1-.9f*t));
        }
        limbCount[slot]=steps+1;
        branch.add(points,radii,steps+1);
        return slot;
    }
    // Attach children to the actual mesh centreline, not the endpoint chord.
    private float[] along(int slot,float t) {
        float[] points=limbPoints[slot];int n=limbCount[slot];
        float index=t*(n-1);int i=Math.min(n-2,(int)index);
        float f=index-i;
        return p(points[i*3]+(points[i*3+3]-points[i*3])*f,points[i*3+1]+(points[i*3+4]-points[i*3+1])*f,points[i*3+2]+(points[i*3+5]-points[i*3+2])*f);
    }
    private void conifer(float x,float y,float z,float h,float spread,float trunkRadius) {
        float bottom=Math.max(1.1f,h*r(.18f,.26f)),top=h+1.25f;
        if(leafBase==leafBase)bottom=Math.max(bottom,Math.min(h*.7f,leafBase-.4f));
        int tiers=Math.max(6,Math.min(20,(int)((top-bottom)/.78f)));
        float turn=r(0,6.28f);
        for(int level=0;level<tiers;level++) {
            float fraction=level/(float)tiers;
            float at=bottom+(top-bottom)*fraction+r(-.18f,.18f);
            float length=spread*(float)Math.pow(1-fraction,.87)*r(.82f,1.08f);
            int count=level>tiers-3?3:5;
            turn+=2.399963f;
            for(int b=0;b<count;b++) {
                if(level>0 && level<tiers-2 && random.nextFloat()<.09f)continue;
                float angle=turn+b*6.283185f/count+r(-.28f,.28f);
                float dx=(float)Math.cos(angle),dz=(float)Math.sin(angle),reach=length*r(.78f,1.1f);
                float droop=reach*r(.12f,.24f);
                float[] a=p(x,y+at+r(-.13f,.13f),z);
                float[] end=p(x+dx*reach,y+at-droop+.15f,z+dz*reach);
                float[] bend=p(x+dx*reach*.52f,y+at-droop*.75f,z+dz*reach*.52f);
                var bough=limb(a,bend,end,Math.min(trunkRadius*.29f,.045f+reach*.026f),0);
                int fans=Math.max(2,Math.min(5,(int)(reach/.52f)));
                for(int f=0;f<fans;f++) {
                    float t=.26f+.7f*f/Math.max(1,fans-1);
                    float[] root=along(bough,t);
                    float width=(1-t)*reach*.32f+.2f;
                    for(int sign=-1;sign<=1;sign+=2) { // [perf3] (was a new int[]{-1,1} per fan)
                        float[] tip=p(root[0]+dx*.25f-dz*sign*width,root[1]-r(.12f,.38f),root[2]+dz*.25f+dx*sign*width);
                        limb(root,mix(root,tip,.55f),tip,.021f+width*.012f,1);
                        spray(tip,Math.min(.98f,.56f+reach*.1f),true,angle+r(-.3f,.3f),5);
                    }
                }
                spray(end,Math.min(.98f,.56f+reach*.08f),true,angle,6);
            }
        }
        for(int i=0;i<5;i++)spray(p(x,y+top-.8f+i*.2f,z),.36f-i*.045f,true,turn+i,4);
    }
    private void broadleaf(float x,float y,float z,float h,float spread,float trunkRadius,int form) {
        float rotation=r(0,6.28f);
        int leaders=form==1?11:4;
        for(int leader=0;leader<leaders;leader++) {
            float angle=rotation+leader*2.399963f+r(-.24f,.24f);
            float dx=(float)Math.cos(angle),dz=(float)Math.sin(angle);
            float fraction=leader/(float)leaders;
            float fork=h*(form==1?.32f+fraction*.55f:r(.22f,.43f));
            float reach=spread*(form==1?(float)Math.sin((.18f+fraction*.74f)*Math.PI)*.85f:r(.66f,.94f));
            float[] root=p(x,y+fork,z);
            float[] joint=p(x+dx*reach,y+(form==1?fork+h*r(.12f,.22f):h*r(.72f,.94f)),z+dz*reach);
            float[] bend=p(x+dx*reach*.36f,y+fork+(joint[1]-y-fork)*.68f,z+dz*reach*.36f);
            float radius=trunkRadius*(form==1?(.21f-.11f*fraction):r(.32f,.46f));
            var leaderCurve=limb(root,bend,joint,radius,0);
            int arms=form==1?4:5;
            for(int arm=0;arm<arms;arm++) {
                float t=.38f+(arm+r(.1f,.8f))/arms*.53f;
                float[] start=along(leaderCurve,t);
                float a=angle+(arm%2==0?-1:1)*r(.5f,1.05f);
                float length=reach*(1-t)*r(.6f,.95f)+.32f;
                float[] outer;
                if(form==1)outer=p(start[0]+(float)Math.cos(a)*length,start[1]+r(.12f,.55f),start[2]+(float)Math.sin(a)*length);
                else {
                    a=angle+(arm-2)*.57f+r(-.18f,.18f);
                    float vertical=-.65f+(arm+r(.1f,.9f))/5*1.5f;
                    float extent=spread*(float)Math.sqrt(1-vertical*vertical)*r(.82f,1.02f);
                    outer=p(x+(float)Math.cos(a)*extent,y+h*(.7f+vertical*.41f),z+(float)Math.sin(a)*extent);
                }
                float[] curve=mix(start,outer,.5f);curve[1]+=.18f;
                var armCurve=limb(start,curve,outer,Math.max(.016f,radius*(1-.9f*t)*.68f),1);
                float size=Math.max(.72f,Math.min(1.18f,spread*.3f));
                spray(outer,size*1.05f,false,a,form==2?12:10);
                if(form!=1) {
                    for(int twigIndex=0;twigIndex<2;twigIndex++) {
                        float[] twig=along(armCurve,.48f+twigIndex*.25f);
                        float turn=a+(twigIndex==0?-.85f:.85f);
                        float[] tip=p(twig[0]+(float)Math.cos(turn)*.5f,twig[1]+.24f,twig[2]+(float)Math.sin(turn)*.5f);
                        limb(twig,mix(twig,tip,.5f),tip,.018f,2);
                        spray(tip,size*1.05f,false,turn,10);
                    }
                }
            }
            spray(joint,Math.min(1.1f,Math.max(.68f,spread*.25f)),false,angle,10);
        }
        // A few high inner clusters break up the leader without sealing the crown's gaps.
        for(int i=0;i<6;i++) {
            float a=i*2.399963f+rotation;
            spray(p(x+(float)Math.cos(a)*spread*.22f,y+h*r(.88f,1.04f),z+(float)Math.sin(a)*spread*.22f),Math.min(1.1f,spread*.26f),false,a,7);
        }
    }
    /** Evenly spread subset of a spray's cards for the distant crown (at least one per spray). */
    static boolean keptFar(int index,int cards,int spray) {
        float offset=(spray*.618034f)%1f;
        return (int)Math.floor((index+1)*FAR_KEEP+offset)>(int)Math.floor(index*FAR_KEEP+offset)
            || cards*FAR_KEEP+offset<1f && index==0;
    }
    private void spray(float[] centre,float size,boolean needle,float heading,int cards) {
        int spray=sprays++;
        for(int i=0;i<cards;i++) {
            float azimuth=heading+i*2.399963f+r(-.4f,.4f);
            float radial=r(.08f,.65f)*size;
            // [perf3] into reused buffers (the same values, the same random draws in the same order)
            float[] c=cardC,u=cardU,v=cardV;
            c[0]=centre[0]+(float)Math.cos(azimuth)*radial;
            c[1]=centre[1]+r(-.42f,.42f)*size;
            c[2]=centre[2]+(float)Math.sin(azimuth)*radial;
            float tilt=needle?r(-.42f,.1f):r(-.9f,.9f);
            float ca=(float)Math.cos(azimuth),sa=(float)Math.sin(azimuth);
            u[0]=ca;u[1]=0;u[2]=sa;
            if(needle && i%3!=0){v[0]=-sa*.85f;v[1]=-.48f;v[2]=ca*.85f;}
            else{v[0]=-sa*(float)Math.sin(tilt);v[1]=(float)Math.cos(tilt);v[2]=ca*(float)Math.sin(tilt);}
            float width=size*r(.65f,1.06f),height=size*r(.55f,.95f);
            if(sink!=null)sink.card(c,u,v,width,height,keptFar(i,cards,spray));
            else card(c,u,v,width,height);
        }
    }
    private void card(float[] c,float[] u,float[] v,float width,float height) {
        float nx=u[1]*v[2]-u[2]*v[1],ny=u[2]*v[0]-u[0]*v[2],nz=u[0]*v[1]-u[1]*v[0];
        float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=length;ny/=length;nz/=length;
        for(int reverse=0;reverse<2;reverse++) {
            TreeShape.Quad q=new TreeShape.Quad();q.texture=2;
            q.nx=reverse==0?nx:-nx;q.ny=reverse==0?ny:-ny;q.nz=reverse==0?nz:-nz;
            for(int i=0;i<4;i++) {
                int corner=reverse==0?i:3-i;
                float a=corner==0||corner==3?-.5f:.5f,b=corner<2?-.5f:.5f;
                int o=i*8;
                q.v[o]=c[0]+u[0]*a*width+v[0]*b*height;
                q.v[o+1]=c[1]+u[1]*a*width+v[1]*b*height;
                q.v[o+2]=c[2]+u[2]*a*width+v[2]*b*height;
                q.v[o+3]=a+.5f;q.v[o+4]=.5f-b;
                q.v[o+5]=q.nx;q.v[o+6]=q.ny;q.v[o+7]=q.nz;
            }
            out.add(q);
        }
    }
}
