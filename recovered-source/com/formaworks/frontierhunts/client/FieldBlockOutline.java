package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.RenderHighlightEvent.Block;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldBlockOutline {
   private static final Map<VoxelShape, float[]> CACHE = new IdentityHashMap<>();

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      CACHE.clear();
   }

   private static float[] edges(VoxelShape var0) {
      float[] var1 = CACHE.get(var0);
      if (var1 != null) {
         return var1;
      } else {
         ArrayList var2 = new ArrayList();
         var0.forAllEdges(
            (var1x, var3x, var5, var7, var9, var11) -> var2.add(new float[]{(float)var1x, (float)var3x, (float)var5, (float)var7, (float)var9, (float)var11})
         );
         float[] var3 = new float[var2.size() * 6];

         for (int var4 = 0; var4 < var2.size(); var4++) {
            System.arraycopy(var2.get(var4), 0, var3, var4 * 6, 6);
         }

         if (CACHE.size() > 256) {
            CACHE.clear();
         }

         CACHE.put(var0, var3);
         return var3;
      }
   }

   @SubscribeEvent
   public static void highlight(Block var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null) {
         BlockPos var2 = var0.getTarget().getBlockPos();
         BlockState var3 = var1.level.getBlockState(var2);
         if (!var3.isAir() && BuiltInRegistries.BLOCK.getKey(var3.getBlock()).getNamespace().equals("frontierhunts")) {
            VoxelShape var4;
            try {
               var4 = var3.getShape(var1.level, var2, CollisionContext.of(var0.getCamera().getEntity()));
            } catch (RuntimeException var20) {
               return;
            }

            if (var4.isEmpty()) {
               var0.setCanceled(true);
            } else {
               float[] var5 = edges(var4);
               var0.setCanceled(true);
               Vec3 var6 = var0.getCamera().getPosition();
               double var7 = (double)var2.getX() - var6.x;
               double var9 = (double)var2.getY() - var6.y;
               double var11 = (double)var2.getZ() - var6.z;
               Pose var13 = var0.getPoseStack().last();
               VertexConsumer var14 = var0.getMultiBufferSource().getBuffer(RenderType.lines());

               for (byte var15 = 0; var15 < var5.length; var15 += 6) {
                  float var16 = var5[var15 + 3] - var5[var15];
                  float var17 = var5[var15 + 4] - var5[var15 + 1];
                  float var18 = var5[var15 + 5] - var5[var15 + 2];
                  float var19 = (float)Math.sqrt((double)(var16 * var16 + var17 * var17 + var18 * var18));
                  if (!(var19 < 1.0E-6F)) {
                     var16 /= var19;
                     var17 /= var19;
                     var18 /= var19;
                     var14.addVertex(
                           var13, (float)((double)var5[var15] + var7), (float)((double)var5[var15 + 1] + var9), (float)((double)var5[var15 + 2] + var11)
                        )
                        .setColor(0.0F, 0.0F, 0.0F, 0.4F)
                        .setNormal(var13, var16, var17, var18);
                     var14.addVertex(
                           var13, (float)((double)var5[var15 + 3] + var7), (float)((double)var5[var15 + 4] + var9), (float)((double)var5[var15 + 5] + var11)
                        )
                        .setColor(0.0F, 0.0F, 0.0F, 0.4F)
                        .setNormal(var13, var16, var17, var18);
                  }
               }
            }
         }
      }
   }

   private FieldBlockOutline() {
   }
}
