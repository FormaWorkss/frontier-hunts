package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class ScentOverlay {
   private static final int REACH = 44;
   private static final int STEP = 2;

   public static boolean holdingChecker(Player var0) {
      for (InteractionHand var4 : InteractionHand.values()) {
         if (var0.getItemInHand(var4).getItem() instanceof ExpeditionGear var5 && var5.id.equals("wind_checker")) {
            return true;
         }
      }

      return false;
   }

   public static String hudSuffix(Player var0) {
      if (var0 != null && holdingChecker(var0)) {
         Level var1 = var0.level();
         double var2 = Wilderness.thermal(var1.getDayTime(), var1.isRaining(), (float)var1.getSkyDarken() / 15.0F);
         return String.format(Locale.ROOT, "  /  %s  /  scent %d%%", Wilderness.thermalNote(var2), Math.round(ScentControl.scentMultiplier(var0) * 100.0));
      } else {
         return "";
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_ENTITIES && !HuntShaderCompat.shadowPass()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null && var1.player != null && !var1.options.hideGui) {
            if (holdingChecker(var1.player)) {
               HuntNetwork.Snapshot var2 = FrontierClient.state;
               if (var2 != null) {
                  Wilderness.Wind var3 = new Wilderness.Wind((double)var2.windEast(), (double)var2.windSouth());
                  double var4 = var3.speed();
                  if (!(var4 < 0.08)) {
                     ClientLevel var6 = var1.level;
                     double var7 = Wilderness.thermal(var6.getDayTime(), var6.isRaining(), (float)var6.getSkyDarken() / 15.0F);
                     double var9 = ScentControl.scentMultiplier(var1.player);
                     boolean var11 = var6.isRaining();
                     double var12 = var3.east() / var4;
                     double var14 = var3.south() / var4;
                     Vec3 var16 = var0.getCamera().getPosition();
                     Pose var17 = var0.getPoseStack().last();
                     BufferSource var18 = var1.renderBuffers().bufferSource();
                     VertexConsumer var19 = var18.getBuffer(RenderType.lines());
                     double var20 = var1.player.getX();
                     double var22 = var1.player.getY();
                     double var24 = var1.player.getZ();

                     for (int var26 = -2; var26 <= 2; var26++) {
                        float var27 = -1.0F;
                        double var28 = 0.0;
                        double var30 = 0.0;
                        double var32 = 0.0;

                        for (byte var34 = 1; var34 <= 44; var34 += 2) {
                           double var35 = 4.0 + (double)var34 * 0.18;
                           double var37 = (double)var26 / 2.0 * var35;
                           double var39 = var12 * (double)var34 - var14 * var37;
                           double var41 = var14 * (double)var34 + var12 * var37;
                           double var43 = var20 + var39;
                           double var45 = var24 + var41;
                           double var47 = (double)var6.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(var43), (int)Math.floor(var45)) + 0.06;
                           double var49 = Wilderness.scent(var3, var39, var41, var47 - var22, var11, false, var7) * var9;
                           float var51 = (float)Math.clamp(var49 * 1.5, 0.0, 0.55);
                           if (var27 >= 0.0F && (var51 > 0.02F || var27 > 0.02F)) {
                              float var52 = (float)(var43 - var28);
                              float var53 = (float)(var47 - var30);
                              float var54 = (float)(var45 - var32);
                              float var55 = (float)Math.sqrt((double)(var52 * var52 + var53 * var53 + var54 * var54));
                              if (var55 > 1.0E-5F) {
                                 var52 /= var55;
                                 var53 /= var55;
                                 var54 /= var55;
                                 boolean var56 = var26 == -2 || var26 == 2;
                                 float var57 = var56 ? 0.58F : 0.74F;
                                 float var58 = var56 ? 0.78F : 0.88F;
                                 float var59 = var56 ? 0.52F : 0.6F;
                                 var19.addVertex(var17, (float)(var28 - var16.x), (float)(var30 - var16.y), (float)(var32 - var16.z))
                                    .setColor(var57, var58, var59, var27)
                                    .setNormal(var17, var52, var53, var54);
                                 var19.addVertex(var17, (float)(var43 - var16.x), (float)(var47 - var16.y), (float)(var45 - var16.z))
                                    .setColor(var57, var58, var59, var51)
                                    .setNormal(var17, var52, var53, var54);
                              }
                           }

                           var27 = var51;
                           var28 = var43;
                           var30 = var47;
                           var32 = var45;
                        }
                     }

                     var18.endBatch(RenderType.lines());
                  }
               }
            }
         }
      }
   }

   private ScentOverlay() {
   }
}
