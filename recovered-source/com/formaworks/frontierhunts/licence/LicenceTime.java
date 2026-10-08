package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.season.SeasonClock;
import net.minecraft.world.level.Level;

/** [licence] The reserve calendar as the regulations see it (works on both sides; the client uses the synced calendar). */
public final class LicenceTime {
   private LicenceTime() {
   }

   public static HuntingCalendar.Date date(Level level) {
      return HuntingCalendar.date(SeasonClock.calendarTicks(level), SeasonClock.daysPerMonth(level));
   }

   /** Current licence season. */
   public static int period(Level level) {
      return Regulations.period(date(level).serial());
   }

   /** Current reserve month (0 = January). */
   public static int month(Level level) {
      return date(level).month();
   }

   /** Reserve day number (for daily bird limits). */
   public static long day(Level level) {
      return Math.max(0L, SeasonClock.calendarTicks(level)) / 24000L;
   }

   /** Reserve days left in the current licence season, including today. */
   public static int daysLeft(Level level) {
      HuntingCalendar.Date d = date(level);
      int p = Regulations.period(d.serial());
      long monthsAfter = Regulations.firstMonth(p) + Regulations.SEASON_MONTHS - 1L - d.serial();
      return (int)(monthsAfter * d.daysPerMonth() + (d.daysPerMonth() - d.day() + 1));
   }
}
