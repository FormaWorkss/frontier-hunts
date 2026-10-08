package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.archery.SightOptics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [bows] Where a point of a first-person held item really is, for things drawn in the world that must meet it (the
 * fishing line at the rod tip, the bowfishing line at the reel).
 *
 * <p>Two things made those lines start on the wrong side of the screen:
 * <ul>
 *   <li>Vanilla's hand pass starts its pose stack with the camera rotation (positions come out world-aligned), but
 *       shader pipelines (Iris) render the hands in a pass of their own whose pose stack is plain camera space. The
 *       old code always undid a camera rotation, so with shaders on the point was rotated by the player's heading -
 *       mirrored to the far side when facing south. Which space this frame's hand pass uses is read off its base pose
 *       at RenderHandEvent.</li>
 *   <li>The hand is drawn with its own FOV (70, plus zoom) while the line is drawn with the world FOV, so the same
 *       camera-space point lands elsewhere on screen. The point is rescaled so the line meets the rod where it is
 *       drawn.</li>
 * </ul>
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class HandSpace {
   private static boolean worldAligned = true;

   private HandSpace() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void hand(RenderHandEvent event) {
      if (event.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }
      Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      Vector3f forward = event.getPoseStack().last().pose().transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
      if (forward.lengthSquared() < 1.0E-8F) {
         return;
      }
      forward.normalize();
      Vector3f look = new Vector3f(camera.getLookVector());
      // world-aligned base pose points along the camera's look; a camera-space one along -z (bob and hand lag are only
      // a few degrees, and when the two directions coincide both readings give the same answer)
      worldAligned = forward.dot(look) >= -forward.z();
   }

   /**
    * Camera-local position (x right, y up, -z forward, metres from the eye) of model point (x, y, z) under {@code pose}
    * during the first-person hand pass, rescaled from the hand FOV to the world FOV.
    */
   static Vector3f camera(PoseStack pose, float x, float y, float z) {
      Vector3f v = pose.last().pose().transformPosition(new Vector3f(x, y, z));
      if (worldAligned) {
         Minecraft.getInstance().gameRenderer.getMainCamera().rotation().conjugate(new Quaternionf()).transform(v);
      }
      float k = (float)(1.0 / SightOptics.ratio(BowSight.worldFov(), BowSight.handFov()));
      v.x *= k;
      v.y *= k;
      return v;
   }
}
