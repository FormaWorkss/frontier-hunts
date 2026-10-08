package com.formaworks.frontierhunts.camload;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/** [camload] Which chunks a trail camera's lens cone covers (pure geometry, no world access). */
public final class CameraFootprint {
   /** horizontal lens angle, as {@code TrailcamScene.FOV_H} */
   static final double FOV_H = com.formaworks.frontierhunts.trailcam.TrailcamScene.FOV_H;
   /** a chunk carrying less than this share of the cone's ground area is not worth loading */
   static final double MIN_SHARE = 0.03;

   private CameraFootprint() {
   }

   /**
    * The chunks (packed {@link ChunkPos}) a camera keeps loaded: its own chunk first (the block entity must tick), then up
    * to {@code max - 1} more carrying the most of the lens cone's ground area out to {@code range}.
    */
   public static long[] chunks(BlockPos pos, Direction facing, double range, int max) {
      long own = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
      int fx = facing.getStepX();
      int fz = facing.getStepZ();
      if (fx == 0 && fz == 0 || max <= 1) {
         return new long[]{own};
      }
      // lens position as TrailcamScene.lens: 0.4 out of the block centre along the facing
      double lx = pos.getX() + 0.5 + fx * 0.4;
      double lz = pos.getZ() + 0.5 + fz * 0.4;
      double half = Math.toRadians(FOV_H / 2.0);
      int rays = 15;
      Long2DoubleOpenHashMap weight = new Long2DoubleOpenHashMap();
      double total = 0.0;
      for (double d = 0.5; d <= range; d += 1.0) {
         for (int i = 0; i < rays; i++) {
            double a = -half + 2.0 * half * i / (rays - 1);
            double c = Math.cos(a);
            double s = Math.sin(a);
            double x = lx + (fx * c - fz * s) * d;
            double z = lz + (fz * c + fx * s) * d;
            weight.addTo(ChunkPos.asLong(Mth.floor(x) >> 4, Mth.floor(z) >> 4), d); // a ring's area grows with distance
            total += d;
         }
      }
      weight.remove(own);
      double min = total * MIN_SHARE;
      List<Long> ranked = new ArrayList<>();
      for (long k : weight.keySet().toLongArray()) {
         if (weight.get(k) >= min) {
            ranked.add(k);
         }
      }
      ranked.sort((p, q) -> {
         int cmp = Double.compare(weight.get((long)q), weight.get((long)p));
         return cmp != 0 ? cmp : Long.compare(p, q);
      });
      int n = Math.min(max - 1, ranked.size());
      long[] out = new long[n + 1];
      out[0] = own;
      for (int i = 0; i < n; i++) {
         out[i + 1] = ranked.get(i);
      }
      return out;
   }
}
