package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.rifle.RifleNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Quaternionf;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class BulletImpacts {
   private static final ArrayDeque<BulletImpacts.Mark> marks = new ArrayDeque<>();
   private static Object world;

   public static void receive(RifleNetwork.Impact var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.level.dimension().location().toString().equals(var0.dimension())) {
         if (world != var1.level) {
            marks.clear();
            world = var1.level;
         }

         Vec3 var2 = new Vec3(var0.x(), var0.y(), var0.z());
         if (Double.isFinite(var2.lengthSqr()) && !(var2.distanceToSqr(Vec3.atCenterOf(var0.block())) > 2.0)) {
            Vec3 var3 = Vec3.atLowerCornerOf(var0.face().getNormal());
            Vec3 var4 = Math.abs(var3.y) > 0.5 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 var5 = var3.cross(var4);
            float var6 = 0.036F;

            for (int var7 = 0; var7 < 8; var7++) {
               double var8 = (double)var7 * Math.PI / 4.0;
               Vec3 var10 = var2.add(var4.scale(Math.cos(var8) * (double)var6)).add(var5.scale(Math.sin(var8) * (double)var6));
               BlockHitResult var11 = var1.level
                  .clip(new ClipContext(var10.add(var3.scale(0.05)), var10.subtract(var3.scale(0.05)), Block.COLLIDER, Fluid.NONE, var1.player));
               if (var11.getType() != Type.BLOCK || !var11.getBlockPos().equals(var0.block()) || var11.getDirection() != var0.face()) {
                  var6 = 0.012F;
               }
            }

            while (marks.size() >= 192) {
               marks.removeFirst();
            }

            marks.addLast(new BulletImpacts.Mark(var0, var1.level.getGameTime() + 1200L, var6));
         }
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      ClientLevel var1 = Minecraft.getInstance().level;
      if (var1 != world) {
         marks.clear();
         world = var1;
      }

      if (var1 != null) {
         marks.removeIf(
            var1x -> var1.getGameTime() >= var1x.expires
                  || !var1.hasChunkAt(var1x.hit.block())
                  || net.minecraft.world.level.block.Block.getId(var1.getBlockState(var1x.hit.block())) != var1x.hit.state()
         );
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_ENTITIES && !marks.isEmpty()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null) {
            BufferSource var2 = var1.renderBuffers().bufferSource();
            RenderType var3 = RenderType.entityTranslucent(FieldMaterials.ATLAS);
            VertexConsumer var4 = var2.getBuffer(var3);
            PoseStack var5 = var0.getPoseStack();
            Vec3 var6 = var0.getCamera().getPosition();

            for (BulletImpacts.Mark var8 : marks) {
               RifleNetwork.Impact var9 = var8.hit;
               Vec3 var10 = new Vec3(var9.x(), var9.y(), var9.z());
               if (!(var10.distanceToSqr(var6) > 2304.0) && var0.getFrustum().isVisible(new AABB(var10, var10).inflate(0.08))) {
                  Vec3 var11 = Vec3.atLowerCornerOf(var9.face().getNormal());
                  var5.pushPose();
                  var5.translate(var10.x - var6.x + var11.x * 0.002, var10.y - var6.y + var11.y * 0.002, var10.z - var6.z + var11.z * 0.002);
                  var5.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float)var11.x, (float)var11.y, (float)var11.z));
                  int var12 = (int)(230.0F * Math.clamp((float)(var8.expires - var1.level.getGameTime()) / 200.0F, 0.0F, 1.0F));
                  int var13 = LevelRenderer.getLightColor(var1.level, var9.block().relative(var9.face()));

                  for (int var14 = 0; var14 < 2; var14++) {
                     for (int var15 = 0; var15 < 12; var15++) {
                        for (int var16 = 0; var16 < 4; var16++) {
                           double var17 = (double)(var15 + (var16 == 2 ? 1 : 0)) * Math.PI / 6.0;
                           double var19 = var16 != 0 && var16 != 3
                              ? (double)var8.radius * (var14 == 0 ? 1.0 : 0.5) * (1.0 + 0.13 * Math.sin((double)var15 * 2.7 + var9.x()))
                              : 0.0;
                           int var21 = var14 == 0 ? 98 : 24;
                           var4.addVertex(var5.last().pose(), (float)(Math.cos(var17) * var19), (float)var14 * 3.0E-4F, (float)(Math.sin(var17) * var19))
                              .setColor(var21, var21 - 3, var21 - 7, var12)
                              .setUv(0.875F, 0.125F)
                              .setOverlay(OverlayTexture.NO_OVERLAY)
                              .setLight(var13)
                              .setNormal(var5.last(), 0.0F, 1.0F, 0.0F);
                        }
                     }
                  }

                  var5.popPose();
               }
            }

            var2.endBatch(var3);
         }
      }
   }

   private BulletImpacts() {
   }

   private static record Mark(RifleNetwork.Impact hit, long expires, float radius) {
   }
}
