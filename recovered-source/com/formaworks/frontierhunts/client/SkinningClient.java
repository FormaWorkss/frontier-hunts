package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class SkinningClient {
   private static LivingEntity working;

   private static boolean active(LivingEntity var0, int var1) {
      if (var0 instanceof Whitetail var2 && var2.skinnerId() == var1 && !var2.harvested()) {
         return true;
      }

      return false;
   }

   private static float progress(float var0) {
      return working instanceof Whitetail var1 ? var1.skinningProgress(var0) : 0.0F;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      working = null;
      if (var1.player != null && var1.level != null && var1.screen == null && var1.player.getMainHandItem().is((Item)HuntContent.SKINNING_TOOL.get())) {
         Iterator var2 = var1.level
            .getEntitiesOfClass(LivingEntity.class, var1.player.getBoundingBox().inflate(5.0), var1x -> active(var1x, var1.player.getId()))
            .iterator();
         if (var2.hasNext()) {
            LivingEntity var3 = (LivingEntity)var2.next();
            working = var3;
         }
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (working != null
         && var1.player != null
         && var0.getHand() == InteractionHand.MAIN_HAND
         && var1.player.getMainHandItem().is((Item)HuntContent.SKINNING_TOOL.get())) {
         var0.setCanceled(true);
         float var2 = var1.player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
         double var3 = (double)((float)var1.level.getGameTime() + var0.getPartialTick());
         double var5 = Math.sin(var3 * 0.32);
         PoseStack var7 = var0.getPoseStack();
         var7.pushPose();
         var7.translate((double)var2 * (0.19 + var5 * 0.017), -0.4 + Math.cos(var3 * 0.32) * 0.012, -0.62 - var5 * 0.018);
         var7.mulPose(Axis.ZP.rotationDegrees(var2 * (-28.0F + (float)var5 * 7.0F)));
         var7.mulPose(Axis.XP.rotationDegrees(-18.0F));
         FieldPlayerArms.wrist(
            var7, var0.getMultiBufferSource(), var0.getPackedLight(), var1.player.getMainArm(), (double)var2 * 0.028, -0.145, 0.012, -90.0F, 0.0F, var2 * 8.0F
         );
         FieldSkinnerModel.draw(var7, var0.getMultiBufferSource(), var0.getPackedLight());
         var7.popPose();
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      working = null;
      CarcassSurface.clear();
   }

   private SkinningClient() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void gui(RegisterGuiLayersEvent var0) {
         var0.registerAboveAll(FrontierHunts.id("skinning"), (var0x, var1) -> {
            Minecraft var2 = Minecraft.getInstance();
            if (var2.player != null && var2.screen == null && !var2.options.hideGui && SkinningClient.working != null && !SkinningClient.working.isRemoved()) {
               float var3 = SkinningClient.progress(var1.getGameTimeDeltaPartialTick(false));
               int var4 = var0x.guiWidth() / 2;
               int var5 = var0x.guiHeight() - 106;
               String var6 = (double)var3 < 0.18 ? "Opening the hide" : ((double)var3 < 0.78 ? "Separating the hide" : "Finishing the harvest");
               var0x.fill(var4 - 112, var5 - 17, var4 + 112, var5 + 26, -535420901);
               var0x.drawCenteredString(var2.font, "SKINNING · " + Math.round(var3 * 100.0F) + "%", var4, var5 - 12, -1056319);
               var0x.fill(var4 - 100, var5 + 2, var4 + 100, var5 + 7, -12760260);
               var0x.fill(var4 - 100, var5 + 2, var4 - 100 + Math.round(var3 * 200.0F), var5 + 7, -4613021);
               var0x.drawCenteredString(var2.font, var6 + " · walk away to pause", var4, var5 + 12, -3489878);
            }
         });
      }
   }
}
