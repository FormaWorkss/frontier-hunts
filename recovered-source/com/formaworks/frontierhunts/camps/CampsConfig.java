package com.formaworks.frontierhunts.camps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Server settings for camps, records and weekend events: {@code config/frontierhunts-camps.json}.
 * Created with defaults on first server start, rewritten when an admin changes a value with
 * {@code /huntevent config <key> <value>}; {@code /huntevent reload} re-reads the file after a manual edit.
 * Every value is validated/clamped, a broken file falls back to defaults (and is left untouched).
 */
public final class CampsConfig {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   // events
   public static boolean eventsEnabled = true;
   public static String timezone = "system";
   public static DayOfWeek startDay = DayOfWeek.FRIDAY;
   public static LocalTime startTime = LocalTime.of(18, 0);
   public static DayOfWeek endDay = DayOfWeek.SUNDAY;
   public static LocalTime endTime = LocalTime.of(23, 59);
   public static List<String> rotation = new ArrayList<>(List.of("BIG_BUCK", "WATERFOWL", "RUT_RALLY"));
   public static int[] prizeTokens = new int[]{150, 90, 50};
   public static String[] prizeItems = new String[]{"frontierhunts:eight_power_scope", "frontierhunts:rifle_round*24", "frontierhunts:scent_cover*4"};
   public static boolean announceLeads = true;
   // records
   public static boolean countCreative = false;
   public static boolean announceRecords = true;
   // camps
   public static int maxMembers = 12;
   public static int guideRange = 96;

   private CampsConfig() {
   }

   public static Path file() {
      return FMLPaths.CONFIGDIR.get().resolve("frontierhunts-camps.json");
   }

   public static ZoneId zone() {
      if (timezone == null || timezone.isBlank() || timezone.equalsIgnoreCase("system")) {
         return ZoneId.systemDefault();
      }
      try {
         return ZoneId.of(timezone);
      } catch (Exception e) {
         return ZoneId.systemDefault();
      }
   }

   public static synchronized void load() {
      Path f = file();
      try {
         if (!Files.exists(f)) {
            save();
            return;
         }
         JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
         JsonObject ev = obj(root, "events");
         eventsEnabled = bool(ev, "enabled", true);
         timezone = str(ev, "timezone", "system");
         startDay = day(str(ev, "startDay", "FRIDAY"), DayOfWeek.FRIDAY);
         startTime = time(str(ev, "startTime", "18:00"), LocalTime.of(18, 0));
         endDay = day(str(ev, "endDay", "SUNDAY"), DayOfWeek.SUNDAY);
         endTime = time(str(ev, "endTime", "23:59"), LocalTime.of(23, 59));
         List<String> rot = new ArrayList<>();
         if (ev.has("rotation") && ev.get("rotation").isJsonArray()) {
            for (JsonElement e : ev.getAsJsonArray("rotation")) {
               HuntEvents.Type t = HuntEvents.Type.find(e.getAsString());
               if (t != null && rot.size() < 16) {
                  rot.add(t.name());
               }
            }
         }
         rotation = rot.isEmpty() ? new ArrayList<>(List.of("BIG_BUCK", "WATERFOWL", "RUT_RALLY")) : rot;
         int[] tokens = new int[]{150, 90, 50};
         if (ev.has("prizeTokens") && ev.get("prizeTokens").isJsonArray()) {
            JsonArray a = ev.getAsJsonArray("prizeTokens");
            for (int i = 0; i < 3 && i < a.size(); i++) {
               tokens[i] = Math.clamp(a.get(i).getAsInt(), 0, 10000);
            }
         }
         prizeTokens = tokens;
         String[] items = new String[]{"", "", ""};
         if (ev.has("prizeItems") && ev.get("prizeItems").isJsonArray()) {
            JsonArray a = ev.getAsJsonArray("prizeItems");
            for (int i = 0; i < 3 && i < a.size(); i++) {
               items[i] = a.get(i).isJsonNull() ? "" : a.get(i).getAsString().trim();
            }
         }
         prizeItems = items;
         announceLeads = bool(ev, "announceLeadChanges", true);
         JsonObject rec = obj(root, "records");
         countCreative = bool(rec, "countCreative", false);
         announceRecords = bool(rec, "announceRecords", true);
         JsonObject camps = obj(root, "camps");
         maxMembers = Math.clamp(num(camps, "maxMembers", 12), 2, 32);
         guideRange = Math.clamp(num(camps, "guideRangeBlocks", 96), 16, 512);
      } catch (Exception e) {
         LOG.warn("Frontier Hunts: could not read {} ({}); using defaults for camps/events", f, e.toString());
      }
   }

   public static synchronized void save() {
      JsonObject root = new JsonObject();
      JsonObject ev = new JsonObject();
      ev.addProperty("_comment", "Weekend events run in real time. timezone: 'system' or an IANA id like America/Chicago. Days: MONDAY..SUNDAY, times HH:mm (24h).");
      ev.addProperty("enabled", eventsEnabled);
      ev.addProperty("timezone", timezone);
      ev.addProperty("startDay", startDay.name());
      ev.addProperty("startTime", startTime.toString());
      ev.addProperty("endDay", endDay.name());
      ev.addProperty("endTime", endTime.toString());
      JsonArray rot = new JsonArray();
      rotation.forEach(rot::add);
      ev.add("rotation", rot);
      JsonArray tokens = new JsonArray();
      for (int t : prizeTokens) {
         tokens.add(t);
      }
      ev.add("prizeTokens", tokens);
      JsonArray items = new JsonArray();
      for (String s : prizeItems) {
         items.add(s == null ? "" : s);
      }
      ev.add("prizeItems", items);
      ev.addProperty("announceLeadChanges", announceLeads);
      root.add("events", ev);
      JsonObject rec = new JsonObject();
      rec.addProperty("countCreative", countCreative);
      rec.addProperty("announceRecords", announceRecords);
      root.add("records", rec);
      JsonObject camps = new JsonObject();
      camps.addProperty("maxMembers", maxMembers);
      camps.addProperty("guideRangeBlocks", guideRange);
      root.add("camps", camps);
      try {
         Files.createDirectories(file().getParent());
         Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
      } catch (Exception e) {
         LOG.warn("Frontier Hunts: could not write {}: {}", file(), e.toString());
      }
   }

   /** Admin setter used by {@code /huntevent config}. Returns an error message or null on success. */
   public static synchronized String set(String key, String value) {
      String k = key.toLowerCase(Locale.ROOT);
      String v = value.trim();
      switch (k) {
         case "enabled" -> eventsEnabled = Boolean.parseBoolean(v);
         case "timezone" -> {
            if (!v.equalsIgnoreCase("system")) {
               try {
                  ZoneId.of(v);
               } catch (Exception e) {
                  return "Unknown time zone '" + v + "'. Use 'system' or an id like America/Chicago.";
               }
            }
            timezone = v;
         }
         case "startday" -> {
            DayOfWeek d = day(v, null);
            if (d == null) {
               return "Day must be MONDAY..SUNDAY";
            }
            startDay = d;
         }
         case "endday" -> {
            DayOfWeek d = day(v, null);
            if (d == null) {
               return "Day must be MONDAY..SUNDAY";
            }
            endDay = d;
         }
         case "starttime" -> {
            LocalTime t = time(v, null);
            if (t == null) {
               return "Time must be HH:mm";
            }
            startTime = t;
         }
         case "endtime" -> {
            LocalTime t = time(v, null);
            if (t == null) {
               return "Time must be HH:mm";
            }
            endTime = t;
         }
         case "rotation" -> {
            List<String> rot = new ArrayList<>();
            for (String part : v.split("[,\\s]+")) {
               HuntEvents.Type t = HuntEvents.Type.find(part);
               if (t == null) {
                  return "Unknown event '" + part + "'. Use BIG_BUCK, PREDATOR, WATERFOWL, RUT_RALLY.";
               }
               rot.add(t.name());
            }
            if (rot.isEmpty()) {
               return "Rotation needs at least one event";
            }
            rotation = rot;
         }
         case "prizetokens" -> {
            String[] parts = v.split("[,\\s]+");
            if (parts.length != 3) {
               return "Give three numbers: first,second,third";
            }
            int[] t = new int[3];
            try {
               for (int i = 0; i < 3; i++) {
                  t[i] = Math.clamp(Integer.parseInt(parts[i]), 0, 10000);
               }
            } catch (NumberFormatException e) {
               return "Give three numbers: first,second,third";
            }
            prizeTokens = t;
         }
         case "prizeitem1", "prizeitem2", "prizeitem3" -> prizeItems[k.charAt(k.length() - 1) - '1'] = v.equalsIgnoreCase("none") ? "" : v;
         case "announceleads" -> announceLeads = Boolean.parseBoolean(v);
         case "countcreative" -> countCreative = Boolean.parseBoolean(v);
         case "announcerecords" -> announceRecords = Boolean.parseBoolean(v);
         case "maxmembers" -> {
            try {
               maxMembers = Math.clamp(Integer.parseInt(v), 2, 32);
            } catch (NumberFormatException e) {
               return "Give a number 2-32";
            }
         }
         case "guiderange" -> {
            try {
               guideRange = Math.clamp(Integer.parseInt(v), 16, 512);
            } catch (NumberFormatException e) {
               return "Give a number 16-512";
            }
         }
         default -> {
            return "Unknown key. Keys: enabled timezone startDay startTime endDay endTime rotation prizeTokens prizeItem1..3 announceLeads countCreative announceRecords maxMembers guideRange";
         }
      }
      save();
      return null;
   }

   public static List<String> keys() {
      return List.of("enabled", "timezone", "startDay", "startTime", "endDay", "endTime", "rotation", "prizeTokens", "prizeItem1", "prizeItem2", "prizeItem3",
         "announceLeads", "countCreative", "announceRecords", "maxMembers", "guideRange");
   }

   public static String describe() {
      return "events " + (eventsEnabled ? "on" : "off") + " · " + title(startDay) + " " + startTime + " → " + title(endDay) + " " + endTime + " (" + zone().getId()
         + ") · rotation " + String.join(", ", rotation) + " · prizes " + prizeTokens[0] + "/" + prizeTokens[1] + "/" + prizeTokens[2] + " tokens";
   }

   public static String title(DayOfWeek d) {
      String n = d.name();
      return n.charAt(0) + n.substring(1, 3).toLowerCase(Locale.ROOT);
   }

   private static JsonObject obj(JsonObject o, String k) {
      return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
   }

   private static boolean bool(JsonObject o, String k, boolean d) {
      try {
         return o.has(k) ? o.get(k).getAsBoolean() : d;
      } catch (Exception e) {
         return d;
      }
   }

   private static int num(JsonObject o, String k, int d) {
      try {
         return o.has(k) ? o.get(k).getAsInt() : d;
      } catch (Exception e) {
         return d;
      }
   }

   private static String str(JsonObject o, String k, String d) {
      try {
         return o.has(k) ? o.get(k).getAsString() : d;
      } catch (Exception e) {
         return d;
      }
   }

   static DayOfWeek day(String s, DayOfWeek d) {
      try {
         String u = s.trim().toUpperCase(Locale.ROOT);
         for (DayOfWeek w : DayOfWeek.values()) {
            if (w.name().equals(u) || w.name().startsWith(u) && u.length() >= 3) {
               return w;
            }
         }
      } catch (Exception ignored) {
      }
      return d;
   }

   static LocalTime time(String s, LocalTime d) {
      try {
         String[] p = s.trim().split(":");
         return LocalTime.of(Math.clamp(Integer.parseInt(p[0]), 0, 23), p.length > 1 ? Math.clamp(Integer.parseInt(p[1]), 0, 59) : 0);
      } catch (Exception e) {
         return d;
      }
   }
}
