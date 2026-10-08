package com.formaworks.frontierhunts.landscape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

final class AlpineCascadeSurface {
   private final Map<AlpineCascadeSurface.Key, List<AlpineCascadeSurface.Strip>> columns = new HashMap<>();
   final List<AlpineCascadeSurface.Strip> strips = new ArrayList<>();

   AlpineCascadeSurface(List<AlpineCascadeSurface.Anchor> var1) {
      HashMap var2 = new HashMap();

      for (AlpineCascadeSurface.Anchor var4 : var1) {
         BlockPos var5 = var4.pos;
         Direction var6 = var4.face;
         boolean var7 = var6.getAxis() == Axis.X;
         int var8 = var7 ? var5.getZ() : var5.getX();
         double var9 = (double)((var7 ? var5.getX() : var5.getZ()) * (var7 ? var6.getStepX() : var6.getStepZ()))
            + (var6 != Direction.NORTH && var6 != Direction.WEST ? 0.04 : -0.96);
         var2.computeIfAbsent(new AlpineCascadeSurface.Key(var6, var8), var0 -> new HashMap<>())
            .put(new AlpineCascadeSurface.Cell(var7 ? var5.getX() : var5.getZ(), var5.getY()), var9);
      }

      for (Entry var15 : var2.entrySet()) {
         Map var16 = (Map)var15.getValue();

         while (!var16.isEmpty()) {
            TreeMap var17 = new TreeMap();
            ArrayDeque var18 = new ArrayDeque();
            AlpineCascadeSurface.Cell var19 = (AlpineCascadeSurface.Cell)var16.keySet().iterator().next();
            var18.add(var19);
            var17.put(Integer.valueOf(var19.y), (Double)var16.remove(var19));

            while (!var18.isEmpty()) {
               AlpineCascadeSurface.Cell var20 = (AlpineCascadeSurface.Cell)var18.remove();

               for (int var10 = -1; var10 <= 1; var10++) {
                  for (int var11 = -2; var11 <= 2; var11++) {
                     AlpineCascadeSurface.Cell var12 = new AlpineCascadeSurface.Cell(var20.normal + var10, var20.y + var11);
                     Double var13 = (Double)var16.remove(var12);
                     if (var13 != null) {
                        var18.add(var12);
                        var17.merge(Integer.valueOf(var12.y), var13, Math::max);
                     }
                  }
               }
            }

            this.add((AlpineCascadeSurface.Key)var15.getKey(), var17);
         }
      }
   }

   private void add(AlpineCascadeSurface.Key var1, NavigableMap<Integer, Double> var2) {
      AlpineCascadeSurface.Strip var3 = new AlpineCascadeSurface.Strip(var1, var2);
      this.strips.add(var3);
      this.columns.computeIfAbsent(var1, var0 -> new ArrayList<>()).add(var3);
   }

   double edge(AlpineCascadeSurface.Strip var1, int var2, double var3) {
      double var5 = var1.normal(var3);
      AlpineCascadeSurface.Strip var7 = this.neighbor(var1, var2);
      return var7 != null ? (var5 + var7.normal(var3)) * 0.5 : var5;
   }

   double edgeTop(AlpineCascadeSurface.Strip var1, int var2) {
      AlpineCascadeSurface.Strip var3 = this.neighbor(var1, var2);
      return var3 == null ? var1.top : (var1.top + var3.top) * 0.5;
   }

   double edgeBottom(AlpineCascadeSurface.Strip var1, int var2) {
      AlpineCascadeSurface.Strip var3 = this.neighbor(var1, var2);
      return (var3 == null ? var1.bottom : (var1.bottom + var3.bottom) * 0.5) - 1.05;
   }

   boolean hasNeighbour(AlpineCascadeSurface.Strip var1, int var2) {
      return this.neighbor(var1, var2) != null;
   }

   private AlpineCascadeSurface.Strip neighbor(AlpineCascadeSurface.Strip var1, int var2) {
      List var3 = this.columns.get(new AlpineCascadeSurface.Key(var1.key.face, var1.key.lateral + (var2 == 0 ? -1 : 1)));
      if (var3 != null) {
         for (AlpineCascadeSurface.Strip var5 : var3) {
            if (Math.abs(var1.top - var5.top) <= 2.0
               && Math.abs(var1.bottom - var5.bottom) <= 2.0
               && Math.abs(var1.normal((var1.top + var1.bottom) * 0.5) - var5.normal((var1.top + var1.bottom) * 0.5)) < 2.5) {
               return var5;
            }
         }
      }

      return null;
   }

   static record Anchor(BlockPos pos, Direction face) {
   }

   private static record Cell(int normal, int y) {
   }

   static record Key(Direction face, int lateral) {
   }

   static final class Strip {
      final AlpineCascadeSurface.Key key;
      final NavigableMap<Integer, Double> profile;
      final double bottom;
      final double top;

      Strip(AlpineCascadeSurface.Key var1, NavigableMap<Integer, Double> var2) {
         this.key = var1;
         this.profile = var2;
         this.bottom = (double)((Integer)var2.firstKey()).intValue();
         this.top = (double)((Integer)var2.lastKey()).intValue() + 1.0;
      }

      double normal(double var1) {
         double var3 = Math.floor(var1);
         double var5 = var1 - var3;
         return this.envelope(var3) * (1.0 - var5) + this.envelope(var3 + 1.0) * var5;
      }

      private double envelope(double var1) {
         double var3 = -Double.MAX_VALUE;

         for (Entry var6 : this.profile.subMap((int)Math.floor(var1) - 2, true, (int)Math.ceil(var1) + 1, true).entrySet()) {
            var3 = Math.max(var3, (Double)var6.getValue());
         }

         if (var3 == -Double.MAX_VALUE) {
            var3 = var1 < this.bottom ? this.profile.firstEntry().getValue() : this.profile.lastEntry().getValue();
         }

         double var9 = this.profile.lastEntry().getValue();
         double var7 = Math.clamp((this.top - var1) / 2.0, 0.0, 1.0);
         return var9 + (var3 - var9) * var7;
      }
   }
}
