package com.formaworks.frontierhunts.sound;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [gui] Per-group volumes for Frontier Hunts' own sounds (client config, section "sound"). Defined inside HuntConfig's
 * client spec by one hook line so it lives in the normal frontierhunts-client.toml.
 */
public final class SoundMixConfig {
   private static final FrontierSoundCategory[] CATS = FrontierSoundCategory.values();
   public static final ModConfigSpec.IntValue[] VOLUME = new ModConfigSpec.IntValue[CATS.length];

   private SoundMixConfig() {
   }

   /** Called from HuntConfig's client builder inside push("sound"). */
   public static void client(ModConfigSpec.Builder b) {
      for (FrontierSoundCategory c : CATS) {
         VOLUME[c.ordinal()] = b.comment(c.label + " volume in percent (0-100, 0 = silent; 50 sounds about half as loud). " + c.help).defineInRange(c.key + "Volume", 100, 0, 100);
      }
   }

   public static ModConfigSpec.IntValue value(FrontierSoundCategory c) {
      return VOLUME[c.ordinal()];
   }

   /** Volume of a group in percent; 100 before the config has loaded. */
   public static int percent(FrontierSoundCategory c) {
      try {
         ModConfigSpec.IntValue v = VOLUME[c.ordinal()];
         return v == null ? 100 : Math.max(0, Math.min(100, v.get()));
      } catch (RuntimeException e) {
         return 100;
      }
   }
}
