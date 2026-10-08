package com.formaworks.frontierhunts.trailcam;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;

/**
 * Trail camera settings in {@code config/frontierhunts-trailcam.properties} (kept out of HuntConfig so it merges cleanly).
 * Server-side keys drive detection; client-side keys drive how photos are developed. Written with defaults on first use.
 */
public final class TrailcamConfig {
   private static volatile Properties props;

   private TrailcamConfig() {
   }

   private static Properties props() {
      Properties p = props;
      if (p != null) {
         return p;
      }
      synchronized (TrailcamConfig.class) {
         if (props != null) {
            return props;
         }
         Properties defaults = new Properties();
         defaults.setProperty("photoWidth", "640");
         defaults.setProperty("jpegQuality", "0.9");
         defaults.setProperty("cacheLimitMB", "256");
         defaults.setProperty("dayRange", "20");
         defaults.setProperty("nightRange", "15");
         defaults.setProperty("subjectCooldownSeconds", "60");
         defaults.setProperty("triggerDelaySeconds", "3");
         defaults.setProperty("vanillaMobs", "false");
         Properties loaded = new Properties(defaults);
         try {
            Path file = FMLPaths.CONFIGDIR.get().resolve("frontierhunts-trailcam.properties");
            if (Files.isRegularFile(file)) {
               try (Reader r = Files.newBufferedReader(file)) {
                  loaded.load(r);
               }
            } else {
               Files.createDirectories(file.getParent());
               try (Writer w = Files.newBufferedWriter(file)) {
                  defaults.store(
                     w,
                     "Frontier Hunts trail cameras\n"
                        + " photoWidth: developed photo width in pixels (16:9), 320-1280 [client]\n"
                        + " jpegQuality: 0.5-1.0 quality of the photo cache [client]\n"
                        + " cacheLimitMB: size limit of the developed-photo cache per world/server [client]\n"
                        + " dayRange / nightRange: PIR trigger distance in blocks [server]\n"
                        + " subjectCooldownSeconds: the same animal/player re-triggers after this long [server]\n"
                        + " triggerDelaySeconds: recovery time between two photos of one camera [server]\n"
                        + " vanillaMobs: also photograph vanilla mobs [server]"
                  );
               }
            }
         } catch (Exception e) {
            LoggerFactory.getLogger(TrailcamConfig.class).warn("Trail camera settings unreadable, using defaults", e);
         }
         props = loaded;
         return loaded;
      }
   }

   private static double num(String key, double min, double max, double fallback) {
      try {
         double v = Double.parseDouble(props().getProperty(key, "").trim());
         return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   public static int photoWidth() {
      return ((int)num("photoWidth", 320, 1280, 640)) & ~15;
   }

   public static int photoHeight() {
      return photoWidth() * 9 / 16;
   }

   public static float jpegQuality() {
      return (float)num("jpegQuality", 0.5, 1.0, 0.9);
   }

   public static long cacheLimitBytes() {
      return (long)num("cacheLimitMB", 16, 4096, 256) << 20;
   }

   public static double dayRange() {
      return num("dayRange", 6, 32, 20);
   }

   public static double nightRange() {
      return num("nightRange", 4, 32, 15);
   }

   public static long subjectCooldownTicks() {
      return (long)(num("subjectCooldownSeconds", 5, 3600, 60) * 20.0);
   }

   public static long triggerDelayTicks() {
      return (long)(num("triggerDelaySeconds", 1, 600, 3) * 20.0);
   }

   public static boolean vanillaMobs() {
      return Boolean.parseBoolean(props().getProperty("vanillaMobs", "false").trim());
   }
}
