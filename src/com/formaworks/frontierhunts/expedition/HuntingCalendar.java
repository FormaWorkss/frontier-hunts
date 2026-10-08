package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;

public final class HuntingCalendar {
   private static final String[] MONTHS = new String[]{
      "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"
   };

   public static HuntingCalendar.Date date(long var0, int var2) {
      long var3 = Math.max(0L, var0) / 24000L;
      long var5 = var3 / (long)var2 + 8L;
      return new HuntingCalendar.Date(var5, (int)(var5 % 12L), (int)(var3 % (long)var2) + 1, var2);
   }

   public static HuntingCalendar.Date date(ServerLevel var0) {
      return date(com.formaworks.frontierhunts.season.SeasonClock.calendarTicks(var0), (Integer)HuntConfig.DAYS_PER_MONTH.get()); // [season] admin calendar offset
   }

   public static String months(GameSpecies var0) {
      ArrayList var1 = new ArrayList();

      for (int var2 = 0; var2 < 12; var2++) {
         if (var0.inSeason(var2)) {
            var1.add(MONTHS[var2].substring(0, 3));
         }
      }

      return var1.size() == 12 ? "Year-round" : String.join(" · ", var1);
   }

   private HuntingCalendar() {
   }

   public static record Date(long serial, int month, int day, int daysPerMonth) {
      public String title() {
         return HuntingCalendar.MONTHS[this.month] + " " + this.day + " · Reserve year " + (this.serial / 12L + 1L);
      }

      public int season() {
         return Math.floorMod(this.month - 2, 12) / 3;
      }
   }
}
