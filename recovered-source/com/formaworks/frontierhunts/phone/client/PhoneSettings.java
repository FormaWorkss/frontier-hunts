package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * [phone] The phone's own settings (units, clock, night light, sounds, notifications, the HUD compass, the alarm),
 * kept on this computer in {@code config/frontierhunts-phone.json}. A damaged file is ignored.
 */
final class PhoneSettings {
   private PhoneSettings() {
   }

   private static Path file() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("frontierhunts-phone.json");
   }

   static void load(PhoneModel m) {
      try {
         Path f = file();
         if (!Files.isRegularFile(f)) {
            return;
         }
         JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
         PhoneModel.Settings s = m.settings;
         s.celsius = bool(o, "celsius", s.celsius);
         s.clock24 = bool(o, "clock24", s.clock24);
         s.night = bool(o, "night", s.night);
         s.silent = bool(o, "silent", s.silent);
         s.vibrate = bool(o, "vibrate", s.vibrate);
         s.hudNav = bool(o, "hudNav", s.hudNav);
         s.notifyCams = bool(o, "notifyCams", s.notifyCams);
         s.notifyContracts = bool(o, "notifyContracts", s.notifyContracts);
         s.notifyWeather = bool(o, "notifyWeather", s.notifyWeather);
         s.notifyGames = bool(o, "notifyGames", s.notifyGames);
         s.volume = o.has("volume") ? Math.max(0, Math.min(100, o.get("volume").getAsInt())) : s.volume;
         m.alarmMinute = o.has("alarm") ? Math.max(-1, Math.min(1439, o.get("alarm").getAsInt())) : -1;
      } catch (Exception e) {
         // a damaged settings file: keep the defaults
      }
   }

   static void save(PhoneModel m) {
      try {
         PhoneModel.Settings s = m.settings;
         JsonObject o = new JsonObject();
         o.addProperty("celsius", s.celsius);
         o.addProperty("clock24", s.clock24);
         o.addProperty("night", s.night);
         o.addProperty("silent", s.silent);
         o.addProperty("vibrate", s.vibrate);
         o.addProperty("hudNav", s.hudNav);
         o.addProperty("notifyCams", s.notifyCams);
         o.addProperty("notifyContracts", s.notifyContracts);
         o.addProperty("notifyWeather", s.notifyWeather);
         o.addProperty("notifyGames", s.notifyGames);
         o.addProperty("volume", s.volume);
         o.addProperty("alarm", m.alarmMinute);
         Path f = file();
         Files.createDirectories(f.getParent());
         Files.writeString(f, o.toString(), StandardCharsets.UTF_8);
      } catch (Exception e) {
         // settings are a convenience: never break the phone over them
      }
   }

   private static boolean bool(JsonObject o, String k, boolean def) {
      return o.has(k) ? o.get(k).getAsBoolean() : def;
   }
}
