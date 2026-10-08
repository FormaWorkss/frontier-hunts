package com.formaworks.frontierhunts.survival.client;

import com.formaworks.frontierhunts.survival.SurvivalConfig;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** [survival] Formatting helpers for the HUD and tooltips (temperature units, in-game durations). */
final class SurvivalText {
   private SurvivalText() {
   }

   private static final String[] CACHE_C = new String[181];
   private static final String[] CACHE_F = new String[181];

   /** "-12°" (HUD, cached) or "-12°C". */
   static String temperature(float c, boolean shortForm) {
      boolean f = SurvivalConfig.units() == SurvivalConfig.Units.FAHRENHEIT;
      int v = Math.round(f ? c * 9F / 5F + 32F : c);
      if (shortForm) {
         int idx = Math.round(c) + 90;
         if (idx >= 0 && idx < 181) {
            String[] cache = f ? CACHE_F : CACHE_C;
            if (cache[idx] == null) {
               cache[idx] = (v < 0 ? "−" + -v : Integer.toString(v)) + "°";
            }
            return cache[idx];
         }
         return v + "°";
      }
      return v + (f ? "°F" : "°C");
   }

   /** In-game duration from ticks: "3 days", "20 h", "under an hour". */
   static Component duration(long ticks) {
      if (ticks >= 48000L) {
         return Component.translatable("survival.frontierhunts.time.days", String.format(Locale.ROOT, "%d", Math.round(ticks / 24000.0)));
      }
      if (ticks >= 24000L) {
         return Component.translatable("survival.frontierhunts.time.day");
      }
      if (ticks >= 1000L) {
         return Component.translatable("survival.frontierhunts.time.hours", ticks / 1000L);
      }
      return Component.translatable("survival.frontierhunts.time.soon");
   }
}
