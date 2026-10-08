package com.formaworks.frontierhunts.optics.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * [1.1.0] Ranging past the loaded land: when Distant Horizons is installed, the rangefinder casts its beam through
 * DH's own terrain data (the far hills you see) and reads the distance to where it lands. Reached by reflection, so
 * the mod runs the same without DH. Rate-limited by the caller.
 */
final class DhRange {
   private DhRange() {
   }

   private static boolean tried, ok;
   private static Object repo, world;
   private static Method raycast, levelOf, loaded;
   private static Field success, payload, pos;
   private static Field px, py, pz;

   private static boolean init() {
      if (tried) {
         return ok;
      }
      tried = true;
      try {
         if (!ModList.get().isLoaded("distanthorizons")) {
            return false;
         }
         Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
         Class<?> repoI = Class.forName("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo");
         Class<?> worldI = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy");
         Class<?> levelI = Class.forName("com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper");
         Class<?> cacheI = Class.forName("com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache");
         Class<?> result = Class.forName("com.seibel.distanthorizons.api.objects.DhApiResult");
         Class<?> hit = Class.forName("com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult");
         Class<?> vec = Class.forName("com.seibel.distanthorizons.api.objects.math.DhApiVec3i");
         repo = delayed.getField("terrainRepo").get(null);
         world = delayed.getField("worldProxy").get(null);
         raycast = repoI.getMethod("raycast", levelI, double.class, double.class, double.class, float.class, float.class, float.class, int.class, cacheI);
         levelOf = worldI.getMethod("getSinglePlayerLevel");
         loaded = worldI.getMethod("worldLoaded");
         success = result.getField("success");
         payload = result.getField("payload");
         pos = hit.getField("pos");
         px = vec.getField("x");
         py = vec.getField("y");
         pz = vec.getField("z");
         ok = repo != null && world != null;
      } catch (Throwable t) {
         ok = false;
      }
      return ok;
   }

   /** distance along dir to the DH terrain, or NaN */
   static double range(Vec3 eye, Vec3 dir, int max) {
      if (!init()) {
         return Double.NaN;
      }
      try {
         if (!(Boolean)loaded.invoke(world)) {
            return Double.NaN;
         }
         Object level = levelOf.invoke(world);
         if (level == null) {
            return Double.NaN;
         }
         Object r = raycast.invoke(repo, level, eye.x, eye.y, eye.z, (float)dir.x, (float)dir.y, (float)dir.z, max, null);
         if (r == null || !success.getBoolean(r)) {
            return Double.NaN;
         }
         Object h = payload.get(r);
         if (h == null) {
            return Double.NaN;
         }
         Object v = pos.get(h);
         double x = px.getInt(v) + 0.5, y = py.getInt(v) + 0.5, z = pz.getInt(v) + 0.5;
         return eye.distanceTo(new Vec3(x, y, z));
      } catch (Throwable t) {
         return Double.NaN;
      }
   }
}
