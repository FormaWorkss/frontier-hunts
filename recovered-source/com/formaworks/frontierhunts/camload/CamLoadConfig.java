package com.formaworks.frontierhunts.camload;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [camload] Trail camera area loading options. Declared here and built into HuntConfig's SERVER spec by one hook
 * line, so they live in the world's {@code serverconfig/frontierhunts-server.toml} with everything else.
 */
public final class CamLoadConfig {
   public static ModConfigSpec.BooleanValue ENABLED;
   public static ModConfigSpec.IntValue PER_PLAYER;
   public static ModConfigSpec.IntValue TOTAL;

   private CamLoadConfig() {
   }

   /** Called from HuntConfig while it builds the SERVER spec. */
   public static void server(ModConfigSpec.Builder b) {
      b.push("trailCameras");
      ENABLED = b.comment(
            "Every placed trail camera with battery left and room on its roll keeps the few chunks its lens covers (at most 4) loaded and ticking, so animals keep moving past it and it keeps taking photos with no player nearby, also after a restart. Nothing extra spawns there. Off: a camera only records while a player is within simulation distance.")
         .define("trailCameraChunkLoading", true);
      PER_PLAYER = b.comment("At most this many of one player's trail cameras keep their area loaded (the oldest placed first).")
         .defineInRange("trailCameraChunkLoadingPerPlayer", 6, 0, 64);
      TOTAL = b.comment("At most this many trail cameras on the whole server keep their area loaded (the oldest placed first).")
         .defineInRange("trailCameraChunkLoadingTotal", 48, 0, 1024);
      b.pop();
   }

   public static boolean enabled() {
      try {
         return ENABLED == null || ENABLED.get();
      } catch (RuntimeException e) {
         return true;
      }
   }

   public static int perPlayer() {
      try {
         return PER_PLAYER == null ? 6 : PER_PLAYER.get();
      } catch (RuntimeException e) {
         return 6;
      }
   }

   public static int total() {
      try {
         return TOTAL == null ? 48 : TOTAL.get();
      } catch (RuntimeException e) {
         return 48;
      }
   }
}
