package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;

public final class Rut {
   private static final Rut.Window WHITETAIL = new Rut.Window(9.0, 10.0, 10.33, 10.67, 11.5);
   private static final Rut.Window ELK = new Rut.Window(7.7, 8.3, 8.63, 9.0, 9.5);
   private static final Rut.Window MOOSE = new Rut.Window(8.3, 8.8, 9.13, 9.47, 9.9);

   private static Rut.Window window(GameSpecies var0) {
      return switch (var0) {
         case ELK -> ELK;
         case MOOSE -> MOOSE;
         default -> WHITETAIL;
      };
   }

   public static double yearPosition(HuntingCalendar.Date var0) {
      int var1 = Math.max(1, var0.daysPerMonth());
      return (double)var0.month() + (double)Math.clamp((long)(var0.day() - 1), 0, var1 - 1) / (double)var1;
   }

   public static Rut.Phase phase(GameSpecies var0, HuntingCalendar.Date var1) {
      return var0 != null && var1 != null ? phaseAt(var0, yearPosition(var1)) : Rut.Phase.NONE;
   }

   private static Rut.Phase phaseAt(GameSpecies var0, double var1) {
      Rut.Window var3 = window(var0);
      if (var1 < var3.preRut || var1 >= var3.end) {
         return Rut.Phase.NONE;
      } else if (var1 < var3.seeking) {
         return Rut.Phase.PRE_RUT;
      } else if (var1 < var3.peak) {
         return Rut.Phase.SEEKING;
      } else {
         return var1 < var3.postRut ? Rut.Phase.PEAK : Rut.Phase.POST_RUT;
      }
   }

   public static double yearPosition(ServerLevel var0) {
      long var1 = Math.max(0L, com.formaworks.frontierhunts.season.SeasonClock.calendarTicks(var0)); // [season] admin calendar offset
      long var3 = 24000L * (long)Math.max(1, (Integer)HuntConfig.DAYS_PER_MONTH.get());
      long var5 = Math.floorMod(var1 / var3 + 8L, 12L);
      return (double)var5 + (double)(var1 % var3) / (double)var3;
   }

   public static Rut.Phase phase(GameSpecies var0, ServerLevel var1) {
      return var1 == null ? Rut.Phase.NONE : phaseAt(var0, yearPosition(var1));
   }

   public static float intensity(GameSpecies var0, HuntingCalendar.Date var1) {
      return var0 != null && var1 != null ? intensityAt(var0, yearPosition(var1)) : 0.0F;
   }

   private static float intensityAt(GameSpecies var0, double var1) {
      Rut.Window var3 = window(var0);
      if (!(var1 < var3.preRut) && !(var1 >= var3.end)) {
         double var4;
         if (var1 < var3.seeking) {
            var4 = 0.12 + 0.28 * span(var1, var3.preRut, var3.seeking);
         } else if (var1 < var3.peak) {
            var4 = 0.45 + 0.35 * span(var1, var3.seeking, var3.peak);
         } else if (var1 < var3.postRut) {
            var4 = 0.85 + 0.15 * arch(span(var1, var3.peak, var3.postRut));
         } else {
            var4 = 0.55 * (1.0 - span(var1, var3.postRut, var3.end));
         }

         return (float)Math.clamp(var4, 0.0, 1.0);
      } else {
         return 0.0F;
      }
   }

   public static float intensity(GameSpecies var0, ServerLevel var1) {
      return var1 == null ? 0.0F : intensityAt(var0, yearPosition(var1));
   }

   private static double span(double var0, double var2, double var4) {
      return var4 <= var2 ? 0.0 : Math.clamp((var0 - var2) / (var4 - var2), 0.0, 1.0);
   }

   private static double arch(double var0) {
      return 1.0 - Math.abs(var0 * 2.0 - 1.0);
   }

   public static float answer(Rut.Call var0, Rut.Phase var1, boolean var2, boolean var3) {
      if (var0 == null || var1 == null) {
         return 0.0F;
      } else if (!var2) {
         return var0 == Rut.Call.BLEAT ? 0.55F : (var0 == Rut.Call.GRUNT ? 0.22F : 0.0F);
      } else {
         float var4 = var3 ? 1.0F : 0.55F;

         return var4 * switch (var0) {
            case GRUNT -> {
               switch (var1) {
                  case NONE:
                     yield 0.18F;
                  case PRE_RUT:
                     yield 0.75F;
                  case SEEKING:
                     yield 1.15F;
                  case PEAK:
                     yield 0.85F;
                  case POST_RUT:
                     yield 0.6F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case BLEAT -> {
               switch (var1) {
                  case NONE:
                     yield 0.3F;
                  case PRE_RUT:
                     yield 0.65F;
                  case SEEKING:
                     yield 1.25F;
                  case PEAK:
                     yield 1.05F;
                  case POST_RUT:
                     yield 0.7F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case RATTLE -> {
               switch (var1) {
                  case NONE:
                     yield 0.05F;
                  case PRE_RUT:
                     yield 0.7F;
                  case SEEKING:
                     yield 1.4F;
                  case PEAK:
                     yield 0.45F;
                  case POST_RUT:
                     yield 0.3F;
                  default:
                     throw new MatchException(null, null);
               }
            }
            case SNORT_WHEEZE -> {
               if (var3) {
                  switch (var1) {
                     case NONE:
                        yield 0.0F;
                     case PRE_RUT:
                        yield 0.4F;
                     case SEEKING:
                        yield 1.1F;
                     case PEAK:
                        yield 0.95F;
                     case POST_RUT:
                        yield 0.2F;
                     default:
                        throw new MatchException(null, null);
                  }
               } else {
                  yield 0.0F;
               }
            }
         };
      }
   }

   public static double carry(Rut.Call var0) {
      return switch (var0) {
         case GRUNT -> 48.0;
         case BLEAT -> 44.0;
         case RATTLE -> 72.0;
         case SNORT_WHEEZE -> 40.0;
      };
   }

   public static float daylightMovement(Rut.Phase var0, boolean var1) {
      if (var1 && var0 != null) {
         return switch (var0) {
            case NONE -> 1.0F;
            case PRE_RUT -> 1.25F;
            case SEEKING -> 1.9F;
            case PEAK -> 2.4F;
            case POST_RUT -> 0.8F;
         };
      } else {
         return 1.0F;
      }
   }

   public static float wariness(Rut.Phase var0, boolean var1) {
      if (var0 == null) {
         return 1.0F;
      } else if (!var1) {
         return var0 == Rut.Phase.PEAK ? 1.12F : 1.0F;
      } else {
         return switch (var0) {
            case NONE -> 1.0F;
            case PRE_RUT -> 0.94F;
            case SEEKING -> 0.78F;
            case PEAK -> 0.62F;
            case POST_RUT -> 1.06F;
         };
      }
   }

   public static float markChance(Rut.Phase var0, boolean var1, boolean var2) {
      if (var1 && var0 != null) {
         float var3 = switch (var0) {
            case NONE -> 0.0F;
            case PRE_RUT -> 1.0F;
            case SEEKING -> 0.85F;
            case PEAK -> 0.35F;
            case POST_RUT -> 0.1F;
         };
         return var3 * (var2 ? 1.0F : 0.45F);
      } else {
         return 0.0F;
      }
   }

   public static boolean breeding(Rut.Phase var0) {
      return var0 == Rut.Phase.PEAK;
   }

   public static String summary(GameSpecies var0, HuntingCalendar.Date var1) {
      Rut.Phase var2 = phase(var0, var1);
      return !var2.active() ? var0.title + ": off season" : var0.title + ": " + var2.title.toLowerCase(Locale.ROOT);
   }

   private Rut() {
   }

   public static enum Call {
      GRUNT,
      BLEAT,
      RATTLE,
      SNORT_WHEEZE;
   }

   public static enum Phase {
      NONE("Off season", "Bucks are in bachelor groups and hold small ranges."),
      PRE_RUT("Pre-rut", "Bucks are marking: fresh rubs and opening scrapes."),
      SEEKING("Seeking", "Bucks are cruising for the first does. Rattling and grunts carry best."),
      PEAK("Peak rut", "Bucks are with does and moving at all hours, and far less careful."),
      POST_RUT("Post-rut", "Worn-down bucks are back on food. Sign goes cold.");

      public final String title;
      public final String note;

      private Phase(String nullxx, String nullxxx) {
         this.title = nullxx;
         this.note = nullxxx;
      }

      public boolean active() {
         return this != NONE;
      }
   }

   private static record Window(double preRut, double seeking, double peak, double postRut, double end) {
   }
}
