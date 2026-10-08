package com.formaworks.frontierhunts.phone.client.ui;

/** [phone] Weather drawing helpers: the colour weather icons (the {@code weather} sheet, 4 x 4) and wind arrows. */
public final class Wx {
   public static final String[] ICONS = {"clear", "night_clear", "partly", "partly_night", "cloudy", "rain", "thunder", "snow", "blizzard", "fog",
      "dust", "wind", "drizzle", "sleet", "storm_night", "moon"};

   private Wx() {
   }

   public static int index(String icon) {
      for (int i = 0; i < ICONS.length; i++) {
         if (ICONS[i].equals(icon)) {
            return i;
         }
      }
      return 4;
   }

   /** The weather icon centred at (cx, cy). */
   public static void icon(Canvas c, String icon, float cx, float cy, float size) {
      int i = index(icon);
      float u = (i % 4) / 4.0F, v = (i / 4) / 4.0F;
      c.sprite("weather", cx - size / 2.0F, cy - size / 2.0F, size, size, u, v, u + 0.25F, v + 0.25F, 0xFFFFFFFF);
   }

   /** An arrow showing where the wind goes (it blows FROM {@code fromDeg}), centred, {@code r} long from the centre. */
   public static void windArrow(Canvas c, float cx, float cy, float r, float fromDeg, int color) {
      double to = Math.toRadians(fromDeg + 180.0);
      float dx = (float)Math.sin(to), dy = (float)-Math.cos(to);
      float px = -dy, py = dx;
      float tipX = cx + dx * r, tipY = cy + dy * r;
      float tailX = cx - dx * r * 0.85F, tailY = cy - dy * r * 0.85F;
      c.line(tailX, tailY, cx + dx * r * 0.3F, cy + dy * r * 0.3F, Math.max(1.2F, r * 0.2F), color);
      float hw = r * 0.48F;
      float bx = cx + dx * r * 0.2F, by = cy + dy * r * 0.2F;
      c.triangle(tipX, tipY, bx + px * hw, by + py * hw, bx - px * hw, by - py * hw, color);
   }

   /** Weather icon for a condition at an hour of day (the night versions after dark). */
   public static String forHour(String icon, int hour) {
      boolean night = hour < 6 || hour >= 20;
      if (night) {
         return switch (icon) {
            case "clear" -> "night_clear";
            case "partly" -> "partly_night";
            case "thunder" -> "storm_night";
            default -> icon;
         };
      }
      return icon;
   }
}
