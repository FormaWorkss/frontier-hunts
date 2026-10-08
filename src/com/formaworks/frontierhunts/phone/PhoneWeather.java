package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.weather.Climate;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import com.formaworks.frontierhunts.weather.Storm;
import com.formaworks.frontierhunts.weather.WeatherKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * [phone] Weather on the server: what the phone's forecast is made of. Everything here is read from the real weather
 * machinery, so the forecast comes true: the hour the rain starts or stops comes from the world's own weather clock,
 * the Frontier storms (blizzards, squalls, wind storms, fog...) from their real tracks and envelopes, the wind from the
 * same wilderness wind the deer smell you on. Temperature is a climate model of the place: the biome, the season, the
 * hour, the height and the weather. Further days are an honest outlook from the season's odds.
 */
public final class PhoneWeather {
   private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

   private PhoneWeather() {
   }

   /** Minute of the day (0 = midnight) for a Minecraft day time (0 = 06:00). */
   static int minute(long dayTime) {
      return (int)((Math.floorMod(dayTime, 24000L) * 1440L / 24000L + 360L) % 1440L);
   }

   static void send(ServerPlayer player, int bars) {
      ServerLevel level = player.serverLevel();
      CompoundTag t = build(level, player.blockPosition());
      t.putInt("bars", bars);
      PhoneNet.send(player, PhoneNet.K_WEATHER, t);
   }

   /** Mean air temperature of a place at a season and hour (degrees C), before weather. */
   static float temperature(Climate c, float winterness, int minuteOfDay, int y) {
      float t = Math.min(1.5F, c.rawTemp);
      float mean = -10.0F + 28.0F * t;
      // the season swing is bigger inland and up north than by the sea
      float swing = c.ocean ? 8.0F : 11.0F;
      mean += swing - 2.0F * swing * winterness;
      mean -= Math.max(0, y - 72) * 0.065F;
      float amp = c.dryness >= 0.9F ? 9.0F : (c.wet ? 3.5F : 5.5F);
      double h = minuteOfDay / 60.0;
      return mean + amp * (float)Math.cos((h - 15.0) / 24.0 * Math.PI * 2.0);
   }

   static CompoundTag build(ServerLevel level, BlockPos pos) {
      CompoundTag t = new CompoundTag();
      long now = level.getGameTime();
      long dayTime = level.getDayTime();
      int minuteNow = minute(dayTime);
      Climate climate = new Climate().sample(level, pos);
      float winter = SeasonClock.winterness(level);
      boolean snowy = climate.precip == Biome.Precipitation.SNOW;
      boolean dry = climate.precip == Biome.Precipitation.NONE;
      ServerLevelData data = level.getLevelData() instanceof ServerLevelData d ? d : null;
      boolean raining = level.isRaining();
      boolean thunder = level.isThundering();
      // ticks until the weather clock flips rain on or off (a /weather clear holds it off)
      long rainFlip = data == null ? -1L : (data.getClearWeatherTime() > 0 ? data.getClearWeatherTime() : data.getRainTime());
      long thunderFlip = data == null ? -1L : (data.getClearWeatherTime() > 0 ? data.getClearWeatherTime() : data.getThunderTime());

      // ---------------------------------------------------------------- now
      SeasonalWeather.Sample sample = SeasonalWeather.sample(level, pos);
      WeatherKind kind = sample.kind();
      float severity = sample.severity();
      float east = SeasonalWeather.windEast(level), south = SeasonalWeather.windSouth(level);
      float speed = (float)Math.sqrt(east * east + south * south);
      float kmh = speed * 3.6F * (kind == WeatherKind.WIND_STORM ? 1.0F + 1.6F * severity : 1.0F) * (kind == WeatherKind.BLIZZARD ? 1.0F + severity : 1.0F);
      // the wind blows FROM the opposite of where it goes; degrees clockwise from north (-z)
      float to = (float)Math.toDegrees(Math.atan2(east, -south));
      float from = (float)Math.floorMod((int)Math.round(to + 180.0F), 360);
      float temp = temperature(climate, winter, minuteNow, pos.getY());
      if (raining && !dry) {
         temp -= thunder ? 3.0F : 2.0F;
      }
      if (kind == WeatherKind.BLIZZARD) {
         temp -= 5.0F * severity;
      }
      float feels = temp;
      if (temp < 10.0F && kmh > 4.8F) {
         double v = Math.pow(kmh, 0.16);
         feels = (float)(13.12 + 0.6215 * temp - 11.37 * v + 0.3965 * temp * v);
      } else if (temp > 26.0F && climate.wet) {
         feels = temp + 3.0F;
      }
      String icon;
      String condition;
      if (kind != WeatherKind.NONE && severity > 0.25F) {
         icon = switch (kind) {
            case BLIZZARD -> "blizzard";
            case THUNDERSTORM -> "thunder";
            case SQUALL -> "rain";
            case DUST_STORM -> "dust";
            case FOG, FREEZING_FOG -> "fog";
            case WIND_STORM -> "wind";
            default -> "cloudy";
         };
         condition = kind.title();
      } else if (raining && !dry) {
         icon = thunder && !snowy ? "thunder" : (snowy ? "snow" : "rain");
         condition = thunder && !snowy ? "Thunderstorm" : (snowy ? "Snow" : "Rain");
      } else if (rainFlip >= 0 && rainFlip < 2500L && !raining && !dry) {
         icon = "cloudy";
         condition = "Clouding over";
      } else {
         icon = "clear";
         condition = dry ? "Clear and dry" : "Clear";
      }
      t.putString("icon", icon);
      t.putString("condition", condition);
      t.putFloat("temp", temp);
      t.putFloat("feels", feels);
      t.putFloat("wind", kmh);
      t.putFloat("from", from);
      t.putFloat("vis", SeasonalWeather.visibility(level, pos));
      t.putString("severe", kind == WeatherKind.NONE ? "" : kind.title());
      t.putFloat("severity", severity);
      t.putInt("sunrise", minute(23200L));
      t.putInt("sunset", minute(12800L));
      t.putBoolean("raining", raining && !dry);
      t.putBoolean("rainNext", !raining);
      t.putInt("rainChange", rainFlip < 0 || dry ? -1 : (int)Math.min(Integer.MAX_VALUE, rainFlip * 1440L / 24000L));
      t.putLong("updated", now);

      // ---------------------------------------------------------------- hours: the next 24
      int hour0 = minuteNow / 60;
      t.putInt("hourStart", hour0);
      float[] temps = new float[24];
      String[] icons = new String[24];
      int[] rain = new int[24];
      ListTag hourIcons = new ListTag();
      for (int i = 0; i < 24; i++) {
         long ahead = i == 0 ? 0L : (long)(i * 60 - minuteNow % 60) * 24000L / 1440L;
         int hourMinute = (hour0 + i) % 24 * 60 + 30;
         boolean wet = raining;
         if (rainFlip >= 0 && ahead >= rainFlip) {
            // the clock flips: after a flip, rain lasts at least half a day and dry spells at least half a day too
            wet = !raining;
            if (!raining && ahead - rainFlip >= 12000L) {
               wet = ahead - rainFlip < 18000L;
            }
         }
         boolean storm = wet && (thunder ? thunderFlip < 0 || ahead < thunderFlip : thunderFlip >= 0 && ahead >= thunderFlip);
         float tt = temperature(climate, winter, hourMinute, pos.getY()) - (wet && !dry ? 2.0F : 0.0F);
         String ic = dry ? "clear" : (wet ? (snowy ? "snow" : (storm ? "thunder" : "rain")) : "clear");
         int pct = dry ? 0 : (wet ? (ahead > rainFlip + 12000L && rainFlip >= 0 ? 60 : 90) : 0);
         if (!wet && !dry && rainFlip >= 0 && rainFlip > ahead && rainFlip - ahead < 2000L) {
            ic = "cloudy";
            pct = 30;
         }
         // a Frontier storm on its way over this spot
         WeatherKind sk = stormAt(level, pos, now + ahead);
         if (sk != WeatherKind.NONE) {
            ic = switch (sk) {
               case BLIZZARD -> "blizzard";
               case THUNDERSTORM -> "thunder";
               case SQUALL -> "rain";
               case DUST_STORM -> "dust";
               case FOG, FREEZING_FOG -> "fog";
               case WIND_STORM -> "wind";
               default -> ic;
            };
            if (sk.precipitation()) {
               pct = Math.max(pct, 80);
            }
         }
         temps[i] = tt;
         icons[i] = ic;
         rain[i] = pct;
         hourIcons.add(StringTag.valueOf(ic));
      }
      t.put("hourIcons", hourIcons);
      ListTag hourTemps = new ListTag();
      for (float v : temps) {
         hourTemps.add(net.minecraft.nbt.FloatTag.valueOf(v));
      }
      t.put("hourTemps", hourTemps);
      t.putIntArray("hourRain", rain);

      // ---------------------------------------------------------------- days: today and a four-day outlook
      long calTicks = SeasonClock.calendarTicks(level);
      long dayIndex = calTicks / 24000L;
      ListTag days = new ListTag();
      for (int d = 0; d < 5; d++) {
         CompoundTag day = new CompoundTag();
         long startAhead = d == 0 ? 0L : (24000L - Math.floorMod(dayTime + 6000L, 24000L)) + (d - 1) * 24000L;
         float hi = -99.0F, lo = 99.0F;
         for (int m = 0; m < 1440; m += 120) {
            float v = temperature(climate, winter, m, pos.getY());
            hi = Math.max(hi, v);
            lo = Math.min(lo, v);
         }
         int pct;
         String ic;
         String note = "";
         if (dry) {
            pct = 0;
            ic = "clear";
         } else {
            long endAhead = startAhead + 24000L;
            boolean wetStart = rainFlip >= 0 && startAhead >= rainFlip ? !raining : raining;
            boolean flips = rainFlip >= startAhead && rainFlip < endAhead;
            if (d <= 1 && (wetStart || flips && !raining)) {
               pct = 90;
               note = flips && !raining ? "from " + clock(minute(dayTime + rainFlip)) : "";
            } else if (d <= 1) {
               pct = flips ? 40 : 5;
               note = flips ? "clearing " + clock(minute(dayTime + rainFlip)) : "";
            } else {
               // beyond what the weather clock knows: the season's odds (wetter in spring and fall)
               float season = SeasonClock.progress(level);
               pct = Math.round(18.0F + 10.0F * (float)Math.sin(season * Math.PI * 4.0) + (climate.wet ? 8.0F : 0.0F));
               note = "outlook";
            }
            ic = pct >= 60 ? (snowy ? "snow" : "rain") : (pct >= 30 ? "cloudy" : "clear");
            if (wetStart || flips) {
               hi -= 2.0F;
            }
         }
         int weekday = (int)Math.floorMod(dayIndex + d + 5L, 7L);
         day.putString("name", d == 0 ? "Today" : DAYS[weekday]);
         day.putString("icon", ic);
         day.putFloat("hi", hi);
         day.putFloat("lo", lo);
         day.putInt("rain", pct);
         day.putString("note", note);
         days.add(day);
         // the season changes slowly: tomorrow is a little closer to the next season
         winter = Math.max(0.0F, Math.min(1.0F, winter + seasonTrend(level) / Math.max(1, SeasonClock.daysPerMonth(level))));
      }
      t.put("days", days);

      // ---------------------------------------------------------------- alerts: storms on their way
      ListTag alerts = new ListTag();
      for (Storm s : SeasonalWeather.storms(level)) {
         if (s.expired(now) || alerts.size() >= 4) {
            continue;
         }
         long eta = -1L;
         float strength = 0.0F;
         for (long a = 0L; a <= 24000L; a += 250L) {
            if (s.covers(now + a, pos.getX(), pos.getZ(), 0.0)) {
               float r = s.raw(now + a, pos.getX(), pos.getZ());
               if (r > 0.12F) {
                  eta = a;
                  strength = r;
                  break;
               }
            }
         }
         if (eta < 0L) {
            continue;
         }
         CompoundTag al = new CompoundTag();
         al.putString("kind", s.kind.id);
         al.putString("title", s.kind.title() + (eta == 0L ? " now" : " warning"));
         al.putString("body", alertText(s.kind, eta == 0L));
         al.putInt("eta", (int)(eta * 1440L / 24000L));
         al.putFloat("strength", Math.min(1.0F, strength));
         alerts.add(al);
      }
      t.put("alerts", alerts);

      // ---------------------------------------------------------------- the rut and the hunting forecast
      HuntingCalendar.Date date = HuntingCalendar.date(level);
      Rut.Phase phase = Rut.phase(GameSpecies.WHITETAIL, date);
      t.putString("rutTitle", phase.active() ? phase.title : "Off season");
      t.putString("rutNote", phase.active() ? phase.note : "Deer keep to their feeding and bedding routine.");
      t.putBoolean("rutActive", phase.active());
      List<String> reasons = new ArrayList<>();
      int score = huntScore(level, pos, minuteNow, phase, date, kmh, kind, severity, raining && !dry, rainFlip, reasons);
      t.putInt("huntScore", score);
      String line = (phase.active() ? phase.title + " phase" : "Off the rut") + " · " + (score >= 75 ? "excellent" : score >= 55 ? "good" : score >= 35
         ? "fair" : "slow") + " deer movement";
      t.putString("huntLine", line);
      ListTag rs = new ListTag();
      for (String r : reasons) {
         rs.add(StringTag.valueOf(r));
      }
      t.put("huntReasons", rs);
      t.putInt("moon", level.getMoonPhase());
      return t;
   }

   /** How fast winterness changes per month here (+ toward winter). */
   private static float seasonTrend(ServerLevel level) {
      double y = SeasonClock.yearPosition(level);
      float a = SeasonClock.winterness(y), b = SeasonClock.winterness(y + 1.0);
      return b - a;
   }

   private static WeatherKind stormAt(ServerLevel level, BlockPos pos, long t) {
      for (Storm s : SeasonalWeather.storms(level)) {
         if (!s.expired(t) && s.covers(t, pos.getX(), pos.getZ(), 0.0) && s.raw(t, pos.getX(), pos.getZ()) > 0.3F) {
            return s.kind;
         }
      }
      return WeatherKind.NONE;
   }

   private static String alertText(WeatherKind k, boolean now) {
      return switch (k) {
         case BLIZZARD -> now ? "Whiteout around you. Get to shelter or a fire; tracks fill in fast." : "Heavy snow and wind on the way. Deer will bed in thick cover; head for shelter early.";
         case THUNDERSTORM -> "Lightning and a heavy downpour. Stay off open ridges and out of tree stands.";
         case SQUALL -> "A short band of very heavy rain. Blood trails wash out: follow up before it hits.";
         case DUST_STORM -> "Blowing sand and orange haze. Visibility drops to a few blocks.";
         case FOG, FREEZING_FOG -> "Thick fog in the low ground. Deer move under its cover; scent hangs low.";
         case WIND_STORM -> "Gales and flying leaves. Deer bed down in thick cover and calls do not carry.";
         default -> "Rough weather on the way.";
      };
   }

   private static int huntScore(ServerLevel level, BlockPos pos, int minute, Rut.Phase phase, HuntingCalendar.Date date, float kmh, WeatherKind kind,
      float severity, boolean raining, long rainFlip, List<String> reasons) {
      float s = 45.0F;
      int sunrise = minute(23200L), sunset = minute(12800L);
      boolean dawn = minute >= sunrise - 30 && minute <= sunrise + 120;
      boolean dusk = minute >= sunset - 120 && minute <= sunset + 30;
      if (dawn || dusk) {
         s += 18.0F;
         reasons.add("Prime time: " + (dawn ? "the first two hours after sunrise (" + clock(sunrise) + ")." : "the last two hours before dark (" + clock(sunset)
            + ")."));
      } else if (minute > sunset + 30 || minute < sunrise - 30) {
         s -= 8.0F;
         reasons.add("Night: deer feed in the open, but you need a light and a licence that allows night hunting.");
      } else {
         reasons.add("Midday: deer are bedded. Next prime time at " + clock(minute < 12 * 60 ? sunset - 120 : sunrise) + ".");
      }
      float rut = Rut.intensity(GameSpecies.WHITETAIL, date);
      if (phase.active()) {
         s += 20.0F * rut;
         reasons.add("The rut is in the " + phase.title.toLowerCase(Locale.ROOT) + " phase: " + lowerFirst(phase.note));
      }
      if (kind != WeatherKind.NONE && severity > 0.35F && SeasonalWeather.beddingWeather(level, pos)) {
         s -= 25.0F;
         reasons.add(kind.title() + ": deer bed down in thick cover until it passes.");
      } else if (!raining && rainFlip >= 0 && rainFlip < 6000L) {
         s += 10.0F;
         reasons.add("Rain on the way: deer feed hard ahead of the front.");
      } else if (raining) {
         s -= 6.0F;
         reasons.add("Rain: deer move less, but it hides your sound and washes out your scent trail.");
      }
      if (kmh > 30.0F) {
         s -= 12.0F;
         reasons.add("Strong wind: deer are skittish and your calls do not carry.");
      } else if (kmh >= 6.0F) {
         s += 4.0F;
         reasons.add("A steady wind: keep it in your face and they will not smell you.");
      } else {
         reasons.add("Light, swirling wind: your scent goes everywhere. Use scent cover.");
      }
      int moon = level.getMoonPhase();
      if (moon == 0) {
         s -= 5.0F;
         reasons.add("Full moon: deer feed through the night and move less at first light.");
      } else if (moon == 4) {
         s += 5.0F;
         reasons.add("New moon: dark nights push deer movement into dawn and dusk.");
      }
      return Math.max(5, Math.min(99, Math.round(s)));
   }

   private static String lowerFirst(String s) {
      return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
   }

   static String clock(int minuteOfDay) {
      int m = Math.floorMod(minuteOfDay, 1440);
      return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
   }
}
