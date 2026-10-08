package com.formaworks.frontierhunts.landscape.tent;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent.Post;
import net.neoforged.neoforge.client.event.RenderLivingEvent.Pre;

/**
 * Draws a sleeper lying on the bed roll / cot the shelter actually shows. The shift is computed from the bed
 * block alone ({@link TentSleepPose}) minus the entity's interpolated render position, so it is right for the
 * local player (whose client-side copy sinks to the floor under gravity), for other players (who sit at the
 * vanilla bed height the server keeps) and for players who come into tracking range mid-night.
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class TentSleepRender {
   /** Never drag a body further than this; anything larger is stale data, not a bed in a tent. */
   private static final double MAX_SHIFT_SQR = 9.0;
   private static final Map<LivingEntity, Vec3> SHIFTED = new IdentityHashMap<>();

   private TentSleepRender() {
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void pre(Pre<?, ?> event) {
      LivingEntity e = event.getEntity();
      if (!e.isSleeping()) {
         return;
      }

      TentSleepPose.Pose pose = TentSleepPose.of(e);
      if (pose == null) {
         return;
      }

      float pt = event.getPartialTick();
      Vec3 shift = pose.origin()
         .subtract(Mth.lerp((double)pt, e.xOld, e.getX()), Mth.lerp((double)pt, e.yOld, e.getY()), Mth.lerp((double)pt, e.zOld, e.getZ()));
      if (shift.lengthSqr() < 1.0E-10 || shift.lengthSqr() > MAX_SHIFT_SQR) {
         return;
      }

      event.getPoseStack().translate(shift.x, shift.y, shift.z);
      SHIFTED.put(e, shift);
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST,
      receiveCanceled = true
   )
   public static void post(Post<?, ?> event) {
      Vec3 shift = SHIFTED.remove(event.getEntity());
      if (shift != null) {
         event.getPoseStack().translate(-shift.x, -shift.y, -shift.z);
      }
   }
}
