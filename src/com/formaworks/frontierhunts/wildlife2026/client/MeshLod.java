package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.perf.client.AnimationLod;
import com.formaworks.frontierhunts.perf.client.RenderBudget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * [meshes] Levels of detail of the Ultra wildlife bodies (WildlifeRenderer):
 * <ol start="0">
 * <li>{@code <species>_ultra.fhsk}: the full smoothed sculpt, for animals within arm's reach (effective distance under
 * {@link #fullDistance()}, at most {@link #FULL_BUDGET} of them, the biggest on screen),</li>
 * <li>{@code <species>_mid.fhsk}: the same body at about half the triangles (same coat and UVs) up to the animal-detail
 * swap distance,</li>
 * <li>{@code <species>_bal.fhsk} with the small distant coat beyond.</li>
 * </ol>
 * Effective distance = blocks / animal height / zoom (see AnimationLod), so a big bison or a scoped animal keeps the full
 * body further out. Also counts frames and notices shader-pack shadow passes for the renderer.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class MeshLod {
   /** full sculpted bodies per frame (all wildlife renderers): the closest few */
   public static final RenderBudget FULL_BUDGET = new RenderBudget(3);
   // ULTRA detail: a 0.95-tall wolf has its full body within ~11 blocks, a bison within ~23; beyond, the middle level
   // (same smoothed surface and normals at about a third of the triangles) is indistinguishable at that size on screen
   private static long frame;
   private static long shadowFrame = -100;
   private static double full = 12.0;

   private MeshLod() {
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre event) {
      frame++;
      if ((frame & 31) == 1) {
         int d;
         try {
            d = HuntConfig.ANIMAL_DETAIL.get().ordinal();
         } catch (RuntimeException e) {
            d = 1;
         }
         configure(d);
      }
   }

   static void configure(int detail) {
      full = switch (detail) {
         case 0 -> 5.0;
         case 1 -> 7.0;
         case 2 -> 9.0;
         default -> 12.0;
      };
      FULL_BUDGET.limit(switch (detail) {
         case 0 -> 1;
         case 1 -> 2;
         case 2 -> 2;
         default -> 3;
      });
   }

   public static long frame() {
      return frame;
   }

   /** effective distance (blocks per block of animal height) within which the full sculpt is drawn */
   public static double fullDistance() {
      return full;
   }

   /** a shader pack's shadow pass drew wildlife in the last frames: skins are reused by two passes per frame */
   public static boolean shadowsActive() {
      return frame - shadowFrame <= 2;
   }

   public static void noteShadow() {
      shadowFrame = frame;
   }

   /** still animals re-pose at most 30 times a second (follows the "Distant animal animation" performance toggle) */
   public static boolean throttleStill() {
      return AnimationLod.interval(46.0) > 0.0F;
   }
}
