package com.formaworks.frontierhunts.client.tree;
// [perf3] Bakes every quad of the 56 fixture trees (all levels) with TrunkModel.bakeWith's code (copied here: loading
// TrunkModel itself needs a bootstrapped Minecraft) as it is now (FastBake: one reused QuadBakingVertexConsumer per
// thread) and as it was (a new consumer per quad), with and without a
// shader pack, foliage tint on and off, and checks every BakedQuad is identical (vertex ints, tint, direction, sprite,
// shade, AO). Also reports bytes allocated per baked quad both ways.
// Needs the offline stub of UnitTextureAtlasSprite (its real static initialiser needs LWJGL natives), compiled from
// tools/perf/stub/ first on the classpath:
// javac -d /tmp/stub -cp <cp62> tools/perf/stub/UnitTextureAtlasSprite.java
// javac -d /tmp/b -cp <classes>:<cp62> tools/perf/TreeLodBench.java tools/perf/BakeCheck.java
// java -cp /tmp/stub:/tmp/b:<classes>:<cp62> com.formaworks.frontierhunts.client.tree.BakeCheck
import java.util.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

public final class BakeCheck {
   /** The pre-perf3 TrunkModel.bakeWith, verbatim but for the consumer. */
   static BakedQuad oldBake(TreeShape.Quad q, TextureAtlasSprite sprite, int tint) {
      float tr = tint == -1 ? 1.0F : (tint >> 16 & 255) / 255.0F;
      float tg = tint == -1 ? 1.0F : (tint >> 8 & 255) / 255.0F;
      float tb = tint == -1 ? 1.0F : (tint & 255) / 255.0F;
      QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
      b.setSprite(sprite);
      b.setDirection(Direction.getNearest(q.nx, q.ny, q.nz));
      b.setShade(false);
      b.setHasAmbientOcclusion(false);
      b.setTintIndex(-1);
      int block = (q.light >> 4) & 0xF, sky = (q.light >> 20) & 0xF;
      for (int k = 0; k < 4; k++) {
         int o = k * 8;
         float nx = q.v[o + 5], ny = q.v[o + 6], nz = q.v[o + 7];
         float shade = com.formaworks.frontierhunts.client.ShaderState.on ? 1.0F : 0.6F * nx * nx + 0.8F * nz * nz + (ny > 0 ? 1.0F : 0.5F) * ny * ny;
         if (q.texture == 2) {
            shade = 0.72F + 0.28F * shade;
            if (tint != -1) shade *= .88F;
         }
         b.addVertex(q.v[o], q.v[o + 1], q.v[o + 2]);
         b.setColor(c255(shade * tr), c255(shade * tg), c255(shade * tb), 255);
         float u = q.v[o + 3], v = q.v[o + 4];
         if (q.texture == 2) { u = Math.clamp(u, .0001F, .9999F); v = Math.clamp(v, .0001F, .9999F); }
         b.setUv(sprite.getU(u), sprite.getV(v));
         b.setUv2(block << 4, sky << 4);
         b.setNormal(nx, ny, nz);
      }
      return b.bakeQuad();
   }

   /** TrunkModel.bakeWith as of perf3 (FastBake), verbatim. */
   static BakedQuad newBake(TreeShape.Quad q, TextureAtlasSprite sprite, int tint) {
      float tr = tint == -1 ? 1.0F : (tint >> 16 & 255) / 255.0F;
      float tg = tint == -1 ? 1.0F : (tint >> 8 & 255) / 255.0F;
      float tb = tint == -1 ? 1.0F : (tint & 255) / 255.0F;
      QuadBakingVertexConsumer b = com.formaworks.frontierhunts.perf.client.FastBake.begin();
      b.setSprite(sprite);
      b.setDirection(Direction.getNearest(q.nx, q.ny, q.nz));
      b.setShade(false);
      b.setHasAmbientOcclusion(false);
      b.setTintIndex(-1);
      int block = (q.light >> 4) & 0xF, sky = (q.light >> 20) & 0xF;
      for (int k = 0; k < 4; k++) {
         int o = k * 8;
         float nx = q.v[o + 5], ny = q.v[o + 6], nz = q.v[o + 7];
         float shade = com.formaworks.frontierhunts.client.ShaderState.on ? 1.0F : 0.6F * nx * nx + 0.8F * nz * nz + (ny > 0 ? 1.0F : 0.5F) * ny * ny;
         if (q.texture == 2) {
            shade = 0.72F + 0.28F * shade;
            if (tint != -1) shade *= .88F;
         }
         b.addVertex(q.v[o], q.v[o + 1], q.v[o + 2]);
         b.setColor(c255(shade * tr), c255(shade * tg), c255(shade * tb), 255);
         float u = q.v[o + 3], v = q.v[o + 4];
         if (q.texture == 2) { u = Math.clamp(u, .0001F, .9999F); v = Math.clamp(v, .0001F, .9999F); }
         b.setUv(sprite.getU(u), sprite.getV(v));
         b.setUv2(block << 4, sky << 4);
         b.setNormal(nx, ny, nz);
      }
      return com.formaworks.frontierhunts.perf.client.FastBake.bake(b);
   }

   static int c255(float f) {
      return Math.max(0, Math.min(255, (int)(f * 255)));
   }

   static TextureAtlasSprite sprite(float u0, float u1, float v0, float v1) throws Exception {
      java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      uf.setAccessible(true);
      sun.misc.Unsafe unsafe = (sun.misc.Unsafe)uf.get(null);
      TextureAtlasSprite s = (TextureAtlasSprite)unsafe.allocateInstance(TextureAtlasSprite.class);
      for (String[] f : new String[][]{{"u0", "" + u0}, {"u1", "" + u1}, {"v0", "" + v0}, {"v1", "" + v1}}) {
         java.lang.reflect.Field fl = TextureAtlasSprite.class.getDeclaredField(f[0]);
         unsafe.putFloat(s, unsafe.objectFieldOffset(fl), Float.parseFloat(f[1]));
      }
      return s;
   }

   static boolean same(BakedQuad a, BakedQuad b) {
      return Arrays.equals(a.getVertices(), b.getVertices()) && a.getTintIndex() == b.getTintIndex() && a.getDirection() == b.getDirection()
         && a.getSprite() == b.getSprite() && a.isShade() == b.isShade() && a.hasAmbientOcclusion() == b.hasAmbientOcclusion();
   }

   public static void main(String[] args) throws Exception {
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures");
      m.setAccessible(true);
      List<?> fx = (List<?>)m.invoke(null);
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"), fxx = fx.get(0).getClass().getDeclaredField("x"), fz = fx.get(0).getClass().getDeclaredField("z");
      fw.setAccessible(true);
      fxx.setAccessible(true);
      fz.setAccessible(true);
      TextureAtlasSprite bark = sprite(0.25F, 0.28125F, 0.5F, 0.53125F), spray = sprite(0.0625F, 0.125F, 0.75F, 0.8125F);
      List<TreeShape.Quad> all = new ArrayList<>();
      for (Object f : fx) {
         TreeGrowth.clear();
         TreeGrowth.Tree t = TreeGrowth.lookup((TreeGrowth.World)fw.get(f), fxx.getInt(f), 1, fz.getInt(f), null, TreeGrowth.LOD_NEAR);
         IdentityHashMap<TreeShape.Quad, Boolean> seen = new IdentityHashMap<>();
         for (var mp : List.of(t.cells, t.far, t.impostor)) for (var l : mp.values()) for (var q : l) if (seen.put(q, true) == null) all.add(q);
      }
      com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
      long checked = 0, diff = 0;
      long newBytes = 0, oldBytes = 0;
      for (int round = 0; round < 3; round++) {
         for (boolean shaders : new boolean[]{false, true}) {
            com.formaworks.frontierhunts.client.ShaderState.on = shaders;
            for (int tint : new int[]{-1, 0x6A9E3B}) {
               BakedQuad[] fresh = new BakedQuad[all.size()], reused = new BakedQuad[all.size()];
               long s = tb.getCurrentThreadAllocatedBytes();
               for (int i = 0; i < all.size(); i++) {
                  TreeShape.Quad q = all.get(i);
                  fresh[i] = oldBake(q, q.texture == 2 ? spray : bark, q.texture == 2 ? tint : -1);
               }
               long s2 = tb.getCurrentThreadAllocatedBytes();
               for (int i = 0; i < all.size(); i++) {
                  TreeShape.Quad q = all.get(i);
                  reused[i] = q.texture == 2 ? newBake(q, spray, tint) : newBake(q, bark, -1);
                  if (i % 997 == 0) {
                     // a quad abandoned half-way (an exception between begin and bake) must not leak into the next one
                     QuadBakingVertexConsumer junk = com.formaworks.frontierhunts.perf.client.FastBake.begin();
                     junk.addVertex(9, 9, 9);
                     junk.setTintIndex(5);
                  }
               }
               long s3 = tb.getCurrentThreadAllocatedBytes();
               if (round == 2) {
                  oldBytes += s2 - s;
                  newBytes += s3 - s2;
               }
               for (int i = 0; i < all.size(); i++) {
                  checked++;
                  if (!same(fresh[i], reused[i])) diff++;
               }
            }
         }
      }
      long n = all.size() * 4L;
      System.out.printf(Locale.ROOT, "quads %d, bakes compared %d, different %d%n", all.size(), checked, diff);
      System.out.printf(Locale.ROOT, "allocated per baked quad: new consumer each %.0f B, FastBake %.0f B (the BakedQuad and its data: 176 B kept)%n",
         oldBytes / (double)n, newBytes / (double)n);
   }
}
