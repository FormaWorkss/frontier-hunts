package com.formaworks.frontierhunts.sticks.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.sticks.ShootingSticks;
import com.formaworks.frontierhunts.sticks.ShootingSticksEntity;
import com.formaworks.frontierhunts.sticks.SticksNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [sticks] Client side of resting on shooting sticks:
 * <ul>
 * <li>the use key on a set while holding a gun rests it (the gun's own use key would aim instead, so the gun mods never
 *     send vanilla's "use entity"; we ask the server ourselves), sneak + use folds them;</li>
 * <li>jump or any movement key steps off (the server also sees the movement keys; sneak is vanilla's dismount);</li>
 * <li>once a frame the soft arc / tilt limits and the body's place round the yoke for the local rider;</li>
 * <li>a one-line hint when the gun settles in;</li>
 * <li>the camera eases back up when a kneeling or sitting shooter stands (see {@code SticksCameraMixin}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SticksClient {
   private static boolean wasRested;
   private static boolean releaseSent;
   private static int restedTicks;

   private SticksClient() {
   }

   // ------------------------------------------------------------------------------------------- input

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void use(InputEvent.InteractionKeyMappingTriggered e) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND || p == null || p.isSpectator() || ShootingSticks.rested(p)) {
         return;
      }
      HitResult hit = mc.hitResult;
      if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof ShootingSticksEntity s) || !ShootingSticks.supports(p.getMainHandItem())) {
         return;
      }
      e.setCanceled(true);
      e.setSwingHand(false);
      PacketDistributor.sendToServer(new SticksNetwork.Use(s.getId(), p.isSecondaryUseActive()));
   }

   // ------------------------------------------------------------------------------------------- tick

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      SticksView.tick(mc);
      boolean rested = ShootingSticks.rested(p);
      if (rested) {
         restedTicks++;
         if (!wasRested) {
            releaseSent = false;
            restedTicks = 0;
            mc.gui.setOverlayMessage(Component.translatable("sticks.frontierhunts.rested", mc.options.keyShift.getTranslatedKeyMessage(),
               mc.options.keyJump.getTranslatedKeyMessage()), false);
         }
         // a jump or a step (after the first moment, so the key that walked you up doesn't count) takes you off
         boolean leave = p.input.jumping || (restedTicks > 4 && (p.input.up || p.input.down || p.input.left || p.input.right));
         if (leave && !releaseSent) {
            releaseSent = true;
            PacketDistributor.sendToServer(new SticksNetwork.Release());
         }
      }
      wasRested = rested;
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre e) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
      ShootingSticksEntity.clientPartial = pt;
      ShootingSticksEntity s = ShootingSticks.sticks(p);
      if (s != null && !mc.isPaused()) {
         s.clientFrame(p, pt);
      }
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
      wasRested = false;
      releaseSent = false;
      SticksView.reset();
      eyeAge = -1;
   }

   // ------------------------------------------------------------------------------------------- camera

   private static final int EYE_TICKS = 7;
   private static int eyeAge = -1;
   private static float eyeFrom;
   private static double lastY = Double.NaN;
   private static int sinceRested = 100;

   /**
    * Called at the end of Camera#tick: when a kneeling or sitting shooter stands up (the body jumps up to the ground
    * line in one tick), the eye starts where it was and rises over a third of a second. NaN: keep vanilla's.
    */
   public static float[] eye(Entity entity, float vanilla) {
      Minecraft mc = Minecraft.getInstance();
      if (entity == null || entity != mc.player) {
         return null;
      }
      sinceRested = ShootingSticks.rested(entity) ? 0 : Math.min(sinceRested + 1, 100);
      double y = entity.getY();
      double jump = Double.isNaN(lastY) ? 0.0 : y - lastY;
      lastY = y;
      if (sinceRested > 0 && sinceRested <= 3 && jump > 0.15 && jump < 1.0 && !HuntConfig.REDUCED_MOTION.get()) {
         eyeAge = 0;
         eyeFrom = (float)jump;
      }
      if (eyeAge < 0) {
         return null;
      }
      float before = eyeFrom * (1.0F - smoother(eyeAge / (float)EYE_TICKS));
      eyeAge++;
      float now = eyeFrom * (1.0F - smoother(eyeAge / (float)EYE_TICKS));
      if (eyeAge >= EYE_TICKS) {
         eyeAge = -1;
      }
      return new float[]{vanilla - before, vanilla - now};
   }

   private static float smoother(float t) {
      t = Math.clamp(t, 0.0F, 1.0F);
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }
}
