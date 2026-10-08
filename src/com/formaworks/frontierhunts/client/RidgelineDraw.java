package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.rifle.RifleMotion;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Pull-out flourish for the Ridgeline bolt rifle: it swings up, rolls its bolt side towards you, runs the bolt
 * once and settles into the normal hold. Runs ahead of {@link RifleClient}'s hand renderer only while the
 * flourish plays; aiming, firing or reloading hands straight back to the normal renderer after a short blend.
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class RidgelineDraw {
   private RidgelineDraw() {
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void hand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null || event.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }

      ItemStack stack = mc.player.getMainHandItem();
      if (!(stack.getItem() instanceof RifleItem)) {
         return;
      }

      float partial = event.getPartialTick();
      double now = (double)mc.level.getGameTime() + partial;
      RifleState state = RifleState.read(stack);
      float aim = RifleClient.aim(partial);
      float p = DrawAnimation.rifleProgress(mc.player, now, aim > 0.02F || state.action() != 0, state.firedAt());
      if (p >= 1.0F || aim > 0.985F || state.action() != 0) {
         return;
      }

      event.setCanceled(true);
      float[] o = DrawAnimation.riflePose(p);
      float bolt = DrawAnimation.rifleBolt(now);
      RifleState shown = bolt < 0.0F
         ? state
         : new RifleState(
            state.magazine(),
            state.chamber(),
            state.spent(),
            RifleState.CYCLE,
            DrawAnimation.rifleBoltStart(),
            state.dimension(),
            state.owner(),
            state.firedAt(),
            state.limit()
         );
      float action = bolt < 0.0F ? 0.0F : (float)Math.sin((double)RifleMotion.progress(shown, now) * Math.PI);
      float side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
      PoseStack pose = event.getPoseStack();
      pose.pushPose();
      pose.translate(side * (0.3F + o[0]), -0.315F - action * 0.04F + o[1], -0.42F + o[2]);
      pose.mulPose(Axis.XP.rotationDegrees(action * 5.0F + o[3]));
      pose.mulPose(Axis.YP.rotationDegrees(side * o[4]));
      pose.mulPose(Axis.ZP.rotationDegrees(side * (-3.0F - action * 12.0F + o[5])));
      pose.pushPose();
      if (side < 0.0F) {
         pose.scale(-1.0F, 1.0F, 1.0F);
      }

      pose.scale(1.35F, 1.35F, 1.35F);
      boolean bipod = ExpeditionWeapon.attachment(stack, "bipod");
      RidgelineModel.draw(
         pose,
         event.getMultiBufferSource(),
         event.getPackedLight(),
         shown,
         now,
         bipod,
         bipod ? FieldAttachmentHardware.bipodDeploy(mc.player, mc.player.isCrouching(), now) : 0.0F,
         RidgelineSights.sight(stack) // [rifle] fitted sight
      );
      pose.popPose();
      pose.popPose();
   }
}
