package com.formaworks.frontierhunts.hunting.herd;

import net.neoforged.neoforge.common.ModConfigSpec;

/** [herds] Server config section {@code [herds]} (built from HuntConfig) and safe reads of it. */
public final class HerdConfig {
   public static ModConfigSpec.BooleanValue HERDS;

   private HerdConfig() {
   }

   public static void server(ModConfigSpec.Builder b) {
      HERDS = b.comment(
            "Deer, elk and moose live in realistic social groups: whitetail doe family groups, bachelor bucks that split up for the rut,",
            "winter deer yards, elk cow herds with a herd bull in the rut, moose cows with their calf. Groups travel together behind",
            "their leader and flee together. Off = every animal goes its own way (the older behaviour)."
         )
         .define("socialHerds", true);
   }

   public static boolean enabled() {
      try {
         return HERDS == null || HERDS.get();
      } catch (RuntimeException e) {
         return true;
      }
   }
}
