package com.formaworks.frontierhunts.camps;

import java.util.Locale;

/** Shared text formatting (server messages and client UI use the same strings). */
public final class Fmt {
   private static final String[] MONTHS = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

   private Fmt() {
   }

   /** Antler score in inches, to the nearest eighth like a score sheet: 142 3/8" (whole numbers: 142"). */
   public static String inches(double v) {
      if (!Double.isFinite(v) || v <= 0.0) {
         return "—";
      }
      long eighths = Math.round(v * 8.0);
      long whole = eighths / 8L;
      int frac = (int)(eighths % 8L);
      if (frac == 0) {
         return whole + "\"";
      }
      int num = frac;
      int den = 8;
      while (num % 2 == 0) {
         num /= 2;
         den /= 2;
      }
      return whole + " " + num + "/" + den + "\"";
   }

   public static String kg(double v) {
      if (!Double.isFinite(v) || v <= 0.0) {
         return "—";
      }
      if (v < 10.0) {
         return String.format(Locale.ROOT, "%.1f kg", v);
      }
      return Math.round(v) + " kg";
   }

   public static String metres(double v) {
      return !Double.isFinite(v) || v <= 0.0 ? "—" : Math.round(v) + " m";
   }

   public static String date(long monthSerial, int day) {
      return MONTHS[(int)Math.floorMod(monthSerial, 12L)] + " " + Math.max(1, day) + " · Yr " + (monthSerial / 12L + 1L);
   }

   public static String month(long monthSerial) {
      return MONTHS[(int)Math.floorMod(monthSerial, 12L)];
   }

   /** Compact real-time duration: 2d 4h · 3h 12m · 8m · 40s. */
   public static String duration(long ms) {
      long s = Math.max(0L, ms / 1000L);
      long d = s / 86400L;
      long h = s % 86400L / 3600L;
      long m = s % 3600L / 60L;
      if (d > 0L) {
         return d + "d " + h + "h";
      }
      if (h > 0L) {
         return h + "h " + m + "m";
      }
      return m > 0L ? m + "m" : s + "s";
   }

   public static String ordinal(int n) {
      return switch (n) {
         case 1 -> "1st";
         case 2 -> "2nd";
         case 3 -> "3rd";
         default -> n + "th";
      };
   }
}
