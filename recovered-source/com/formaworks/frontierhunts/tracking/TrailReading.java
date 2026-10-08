package com.formaworks.frontierhunts.tracking;

import java.util.Locale;

/** [tracking] What a hunter reads from a piece of sign: maker, size estimate, gait, direction and age. */
public final class TrailReading {
   private TrailReading() {
   }

   /**
    * How old the sign looks. Weather wear counts: a print half filled by snowfall reads older than it is, the way a
    * real one does.
    */
   public static String age(TrailMark m, long gameTime, long dayTime) {
      long eff = m.effectiveAge(gameTime);
      long real = m.age(gameTime);
      long madeAt = Math.floorMod(dayTime - real, 24000L);
      long nowAt = Math.floorMod(dayTime, 24000L);
      boolean madeAtNight = madeAt >= 13000L && madeAt < 23200L;
      boolean dayNow = nowAt < 12500L || nowAt >= 23200L;
      if (eff < 600L) {
         return "fresh — minutes old";
      } else if (eff < 1500L) {
         return "fresh — under an hour";
      } else if (madeAtNight && dayNow && real < 26000L && real > 1500L) {
         return eff < 20000L ? "made last night" : "last night, weathered";
      } else if (eff < 5000L) {
         return "a few hours old";
      } else if (eff < 14000L) {
         return "half a day old";
      } else if (eff < 30000L) {
         return "about a day old";
      } else {
         return "more than a day old";
      }
   }

   public static String heading(float yaw) {
      String[] dirs = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
      int i = Math.floorMod(Math.round(yaw / 45.0F), 8);
      return "heading " + dirs[i];
   }

   /** the species label before " · " in the maker description */
   public static String species(TrailMark m) {
      String ind = m.individual();
      int i = ind.indexOf(" · ");
      if (i > 0) {
         return ind.substring(0, i);
      }
      return m.print() ? PrintKind.of(m.sign()).label : "Whitetail";
   }

   public static String wetness(TrailMark m, long gameTime) {
      long a = m.age(gameTime);
      return a < 1200L ? "still wet" : a < 6000L ? "tacky, darkening" : "dried brown";
   }

   /** three HUD lines */
   public static String[] lines(TrailMark m, long gameTime, long dayTime, boolean skilled) {
      String head = species(m).toUpperCase(Locale.ROOT) + " · " + m.kind().toUpperCase(Locale.ROOT);
      String second;
      String third;
      String age = age(m, gameTime, dayTime);
      if (skilled) {
         age = age + String.format(Locale.ROOT, " (%d min)", m.age(gameTime) / 1200L);
      }
      if (m.print()) {
         PrintKind k = PrintKind.of(m.sign());
         second = k.estimate(m.scale(), m.stride(), m.activity());
         third = capital(age) + " · " + heading(m.yaw()) + " · " + m.threat();
      } else if (m.blood()) {
         BloodTrail.BloodType t = BloodTrail.BloodType.of(m.sign());
         second = m.style() == TrailMark.BED ? "Lay here bleeding · " + t.reading() : capital(t.reading());
         third = capital(age) + " · " + wetness(m, gameTime) + (m.style() == TrailMark.BRUSH ? " · on the brush" : " · " + heading(m.yaw()));
      } else {
         second = m.individual();
         third = capital(age) + " · " + m.threat();
      }
      return new String[]{head, second, third};
   }

   private static String capital(String s) {
      return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }
}
