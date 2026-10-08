package com.formaworks.frontierhunts.hunting.rutfight;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.Util;
import org.slf4j.Logger;

/**
 * [rutfight] Pair fits computed off the game thread (the offset search over two dense racks takes tens of ms) and
 * cached per pair of animals and preset. {@link #get} returns null until the fit is ready; fights only lock seconds
 * after they start, so the fit is always there by then.
 */
public final class FightFits {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Map<String, CompletableFuture<FightFit>> CACHE = new ConcurrentHashMap<>();

   private FightFits() {
   }

   /** Cache key of a pair for a preset (0 Vanilla, 2 Ultra / server; 1 was the retired Balanced preset); the same on server and client, so an
    * integrated server and an Ultra client share one fit. */
   public static String key(int preset, com.formaworks.frontierhunts.hunting.DeerTraits a, com.formaworks.frontierhunts.hunting.DeerTraits b) {
      return preset + "|" + a + "|" + b;
   }

   public static FightFit get(String key, Supplier<FightModel> a, Supplier<FightModel> b) {
      CompletableFuture<FightFit> f = CACHE.get(key);
      if (f == null) {
         if (CACHE.size() > 96) {
            CACHE.entrySet().removeIf(e -> e.getValue().isDone());
         }

         f = CACHE.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            try {
               return FightFit.solve(a.get(), b.get(), 0.0F);
            } catch (RuntimeException e) {
               LOG.warn("Frontier Hunts: rut fight contact fit failed for {}", k, e);
               return null;
            }
         }, Util.backgroundExecutor()));
      }

      return f.isDone() ? f.getNow(null) : null;
   }
}
