package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLivingEvent.Pre;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class BowfishingClient {
   private static final Map<UUID, HuntProjectile> LINES = new HashMap<>();
   private static final Map<UUID, BowfishingClient.Socket> SOCKETS = new HashMap<>();
   private static LivingEntity holder;
   private static Vec3 firstSocket;
   private static long firstTick;
   private static final ResourceLocation TEXTURE = FrontierHunts.id("textures/equipment/packs/material.png");

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      LINES.clear();
      if (var1.level == null) {
         SOCKETS.clear();
         firstSocket = null;
         holder = null;
      } else {
         SOCKETS.entrySet().removeIf(var1x -> var1.level.getGameTime() - var1x.getValue().tick() > 2L);

         for (Entity var3 : var1.level.entitiesForRendering()) {
            if (var3 instanceof HuntProjectile) {
               HuntProjectile var4 = (HuntProjectile)var3;
               if (var4.tethered() && var4.getOwner() != null) {
                  LINES.put(var4.getOwner().getUUID(), var4);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void before(Pre<?, ?> var0) {
      holder = var0.getEntity();
   }

   @SubscribeEvent
   public static void after(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> var0) {
      holder = null;
   }

   public static HuntProjectile active(LivingEntity var0) {
      return var0 == null ? null : LINES.get(var0.getUUID());
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.level != null && var1.player.getMainHandItem().is(ExpeditionContent.item("bowfishing_bow"))) {
         if (var0.getHand() == InteractionHand.MAIN_HAND) {
            var0.setCanceled(true);
            byte var2 = 1;
            PoseStack var3 = var0.getPoseStack();
            var3.pushPose();
            // [fishing2] drawing the bow brings it up to the eye (same aim-in as the hunting bows), released it
            // returns to the hip
            float drawNow = active(var1.player) == null && var1.player.isUsingItem()
               ? Math.min(1.0F, ((float)var1.player.getTicksUsingItem() + var0.getPartialTick()) / 24.0F)
               : 0.0F;
            float aim = FieldBowAim.aim(drawNow);
            double[] anchor = FieldBowPresentation.anchor(Weapon.BOWFISHING_BOW, false, drawNow, 0.0);
            var3.translate(
               Mth.lerp((double)aim, (double)var2 * 0.28, anchor[0]),
               Mth.lerp((double)aim, -0.24, anchor[1]) - (double)var0.getEquipProgress() * 0.5,
               Mth.lerp((double)aim, -1.02, anchor[2])
            );
            var3.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(aim, (float)(-var2 * 5), (float)anchor[3])));
            var3.pushPose();
            if (var2 < 0) {
               var3.scale(-1.0F, 1.0F, 1.0F);
            }

            draw(
               var1.player.getMainHandItem(),
               ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
               var3,
               var0.getMultiBufferSource(),
               var0.getPackedLight(),
               var0.getPartialTick()
            );
            var3.popPose();
            float var4 = active(var1.player) == null && var1.player.isUsingItem()
               ? Math.min(1.0F, ((float)var1.player.getTicksUsingItem() + var0.getPartialTick()) / 24.0F)
               : 0.0F;
            FieldBowHands.draw(Weapon.BOWFISHING_BOW, var4, var3, var0.getMultiBufferSource(), var0.getPackedLight());
            var3.popPose();
         }
      }
   }

   static void draw(ItemStack var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4, float var5) {
      Minecraft var6 = Minecraft.getInstance();
      LivingEntity var7 = var1.firstPerson()
         ? var6.player
         : (var1 != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && var1 != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND ? null : holder);
      HuntProjectile var8 = active((LivingEntity)var7);
      boolean var9 = var8 != null || var7 != null && var7.isUsingItem() && ExpeditionWeapon.data(var0).getBoolean("bow_retrieving");
      float var10 = !var9 && var7 != null && var7.isUsingItem() && var7.getUseItem() == var0
         ? Math.min(1.0F, ((float)var7.getTicksUsingItem() + var5) / 24.0F)
         : 0.0F;
      FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
      VertexConsumer var11 = var3.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      FieldEquipmentModel.bow(Weapon.COMPOUND_BOW, var2, var11, var4, var10);
      String var12 = var1 == ItemDisplayContext.GROUND ? "distant" : (!var1.firstPerson() && var1 != ItemDisplayContext.GUI ? "field" : "close");
      FieldWeaponMesh.drawStatic("bowfishing_reel_" + var12, TEXTURE, var2, var3, var4);
      float var13 = var8 == null ? 0.0F : var8.crank(var5);
      var2.pushPose();
      var2.translate(0.109, -0.121, -0.027);
      var2.mulPose(Axis.XP.rotation(var13));
      FieldWeaponMesh.drawStatic("bowfishing_crank_" + var12, TEXTURE, var2, var3, var4);
      var2.popPose();
      if (!var9) {
         VertexConsumer var14 = var3.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
         FieldArrowModel.nocked(var2, var14, var4, 0.015, 0.05, 0.092 + (double)var10 * 0.45, true, false);
      }

      if (var7 != null && var6.level != null) {
         if (HuntShaderCompat.shadowPass()) {
            return; // [bows] shadow-pass poses are the sun's view, not the player's
         }
         Vector3f var15 = var1.firstPerson()
            ? HandSpace.camera(var2, 0.058F, -0.096F, -0.079F) // [bows] right with or without shaders and at any FOV
            : var2.last().pose().transformPosition(new Vector3f(0.058F, -0.096F, -0.079F));
         if (var1.firstPerson()) {
            firstSocket = new Vec3((double)var15.x, (double)var15.y, (double)var15.z);
            firstTick = var6.level.getGameTime();
         } else {
            SOCKETS.put(
               var7.getUUID(),
               new BowfishingClient.Socket(
                  new Vec3((double)var15.x, (double)var15.y, (double)var15.z).add(var6.gameRenderer.getMainCamera().getPosition()), var6.level.getGameTime()
               )
            );
         }
      }
   }

   public static Vec3 socket(Entity var0, float var1) {
      Minecraft var2 = Minecraft.getInstance();
      long var3 = var2.level == null ? 0L : var2.level.getGameTime();
      if (var0 == var2.player && var2.options.getCameraType().isFirstPerson() && firstSocket != null && var3 - firstTick <= 2L) {
         Vector3f var6 = var2.gameRenderer.getMainCamera().rotation().transform(new Vector3f((float)firstSocket.x, (float)firstSocket.y, (float)firstSocket.z));
         return var2.gameRenderer.getMainCamera().getPosition().add((double)var6.x, (double)var6.y, (double)var6.z);
      } else {
         BowfishingClient.Socket var5 = SOCKETS.get(var0.getUUID());
         return var5 != null && var3 - var5.tick() <= 2L ? var5.position() : var0.getEyePosition(var1).add(var0.getLookAngle().scale(0.6)).add(0.0, -0.28, 0.0);
      }
   }

   private BowfishingClient() {
   }

   private static record Socket(Vec3 position, long tick) {
   }
}
