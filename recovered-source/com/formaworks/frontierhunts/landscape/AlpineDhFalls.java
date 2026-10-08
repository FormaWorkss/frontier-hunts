package com.formaworks.frontierhunts.landscape;

import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class AlpineDhFalls {
   private static final Color[] BANDS = new Color[]{new Color(214, 224, 228), new Color(196, 210, 217), new Color(178, 196, 205), new Color(205, 217, 222)};
   private static final Map<Long, AlpineDhFalls.Entry> GROUPS = new HashMap<>();
   private static List<AlpineFallsPayload.Fall> pending;
   private static int phase;

   static void sync(List<AlpineFallsPayload.Fall> var0) {
      Minecraft var1 = Minecraft.getInstance();
      IDhApiLevelWrapper var2 = wrapper(var1);
      if (var2 != null && Delayed.customRenderObjectFactory != null) {
         pending = null;
         HashSet var3 = new HashSet();

         for (AlpineFallsPayload.Fall var5 : var0) {
            long var6 = BlockPos.asLong(var5.lipX(), var5.lipY(), var5.lipZ());
            var3.add(var6);
            if (!GROUPS.containsKey(var6) && var5.drop() >= 4) {
               GROUPS.put(var6, create(var5, var2));
            }
         }

         Iterator var8 = GROUPS.entrySet().iterator();

         while (var8.hasNext()) {
            Map.Entry var9 = (Map.Entry)var8.next();
            if (!var3.contains(var9.getKey())) {
               remove((AlpineDhFalls.Entry)var9.getValue());
               var8.remove();
            }
         }
      } else {
         pending = var0;
      }
   }

   static void clear() {
      for (AlpineDhFalls.Entry var1 : GROUPS.values()) {
         remove(var1);
      }

      GROUPS.clear();
      pending = null;
   }

   static void tick(Minecraft var0) {
      if (pending != null) {
         sync(pending);
      }

      if (!GROUPS.isEmpty() && var0.gameRenderer != null) {
         Vec3 var1 = var0.gameRenderer.getMainCamera().getPosition();
         double var2 = (double)(Math.max(3, var0.options.getEffectiveRenderDistance() - 1) * 16);
         boolean var4 = var0.level.getGameTime() % 3L == 0L;
         if (var4) {
            phase++;
         }

         for (AlpineDhFalls.Entry var6 : GROUPS.values()) {
            AlpineFallsPayload.Fall var7 = var6.fall();
            double var8 = (double)var7.lipX() + 0.5 - var1.x;
            double var10 = (double)var7.lipZ() + 0.5 - var1.z;
            boolean var12 = var8 * var8 + var10 * var10 > var2 * var2;
            if (var6.group().isActive() != var12) {
               var6.group().setActive(var12);
            }

            if (var12 && var4) {
               IDhApiRenderableBoxGroup var13 = var6.group();

               for (int var14 = 0; var14 < var6.rowOf().length; var14++) {
                  int var15 = var6.rowOf()[var14];
                  if (var15 >= 0) {
                     ((DhApiRenderableBox)var13.get(var14)).color = BANDS[Math.floorMod(var15 - phase + var6.seed()[var14], BANDS.length)];
                  }
               }

               var13.triggerBoxChange();
            }
         }
      }
   }

   private static void remove(AlpineDhFalls.Entry var0) {
      try {
         var0.wrapper().getRenderRegister().remove(var0.group().getId());
      } catch (RuntimeException var2) {
      }
   }

   private static IDhApiLevelWrapper wrapper(Minecraft var0) {
      IDhApiWorldProxy var1 = Delayed.worldProxy;
      if (var1 != null && var0.level != null && var1.worldLoaded()) {
         IDhApiLevelWrapper var2 = null;
         String var3 = var0.level.dimension().location().toString();

         for (IDhApiLevelWrapper var5 : var1.getAllLoadedLevelWrappers()) {
            Object var6 = var5.getWrappedMcObject();
            if (var6 == var0.level) {
               return var5;
            }

            if (var6 instanceof ClientLevel && var3.equals(var5.getDimensionName())) {
               var2 = var5;
            }
         }

         return var2;
      } else {
         return null;
      }
   }

   private static AlpineDhFalls.Entry create(AlpineFallsPayload.Fall var0, IDhApiLevelWrapper var1) {
      double var2 = (double)var0.towardX();
      double var4 = (double)var0.towardZ();
      double var6 = -var4;
      double var8 = var2;
      int var10 = var0.drop();
      int var11 = Math.max(3, Math.min(16, Math.round((float)var10 / 2.5F)));
      double var12 = (double)var10 / (double)var11;
      int var14 = Math.max(2, var0.width());
      int var15 = Math.max(1, Math.round((float)var14 / 1.6F));
      double var16 = (double)var14 / (double)var15;
      boolean var18 = Math.abs(var2) > 0.8;
      boolean var19 = Math.abs(var4) > 0.8;
      ArrayList var20 = new ArrayList();
      ArrayList var21 = new ArrayList();
      ArrayList var22 = new ArrayList();
      Random var23 = new Random(BlockPos.asLong(var0.lipX(), var0.lipY(), var0.lipZ()));

      for (int var24 = 0; var24 < var11; var24++) {
         double var25 = (double)var10 - (double)(var24 + 1) * var12;
         double var27 = (double)var10 - (double)var24 * var12 + 0.05;
         double var29 = 0.55 + (double)var0.run() * (((double)var24 + 0.5) / (double)var11);

         for (int var31 = 0; var31 < var15; var31++) {
            double var32 = (double)(-var14) / 2.0 + ((double)var31 + 0.5) * var16;
            double var34 = var2 * var29 + var6 * var32;
            double var36 = var4 * var29 + var8 * var32;
            double var38;
            double var40;
            if (var18) {
               var38 = 0.35;
               var40 = var16 / 2.0;
            } else if (var19) {
               var38 = var16 / 2.0;
               var40 = 0.35;
            } else {
               var38 = var40 = Math.max(0.55, var16 * 0.48);
            }

            var20.add(
               new DhApiRenderableBox(
                  new DhApiVec3d(var34 - var38, var25, var36 - var40),
                  new DhApiVec3d(var34 + var38, var27, var36 + var40),
                  BANDS[(var24 + var31) % BANDS.length],
                  EDhApiBlockMaterial.UNKNOWN
               )
            );
            var21.add(var24);
            var22.add(var23.nextInt(4));
         }
      }

      double var42 = (double)(var0.run() + 2.0F);
      double var26 = var2 * var42;
      double var28 = var4 * var42;
      double var30 = var19 ? (double)var14 / 2.0 + 1.5 : (var18 ? 2.0 : (double)var14 / 2.0 + 1.0);
      double var43 = var18 ? (double)var14 / 2.0 + 1.5 : (var19 ? 2.0 : (double)var14 / 2.0 + 1.0);
      var20.add(
         new DhApiRenderableBox(
            new DhApiVec3d(var26 - var30, 0.82, var28 - var43),
            new DhApiVec3d(var26 + var30, 1.02, var28 + var43),
            new Color(198, 210, 214),
            EDhApiBlockMaterial.UNKNOWN
         )
      );
      var21.add(-1);
      var22.add(0);
      DhApiVec3d var44 = new DhApiVec3d((double)var0.lipX() + 0.5, (double)var0.poolY(), (double)var0.lipZ() + 0.5);
      IDhApiRenderableBoxGroup var35 = Delayed.customRenderObjectFactory.createRelativePositionedGroup("frontierhunts:waterfall", var44, var20);
      var35.setShading(DhApiRenderableBoxGroupShading.getUnshaded());
      var35.setSsaoEnabled(false);
      var35.setSkyLight(15);
      var35.setActive(false);
      var1.getRenderRegister().add(var35);
      return new AlpineDhFalls.Entry(
         var0, var35, var1, var11, var21.stream().mapToInt(Integer::intValue).toArray(), var22.stream().mapToInt(Integer::intValue).toArray()
      );
   }

   private AlpineDhFalls() {
   }

   private static record Entry(AlpineFallsPayload.Fall fall, IDhApiRenderableBoxGroup group, IDhApiLevelWrapper wrapper, int rows, int[] rowOf, int[] seed) {
   }
}
