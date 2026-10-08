package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.season.SeasonClock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** [herds] Server housekeeping: the cached calendar position (spawn plans, season rules) and forgetting old groups. */
@EventBusSubscriber(modid = "frontierhunts")
public final class HerdEvents {
   private HerdEvents() {
   }

   @SubscribeEvent
   public static void levelLoad(LevelEvent.Load event) {
      // chunk generation around spawn runs before the first server tick: give its spawn plans the right season
      if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
         refresh(level);
      }
   }

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post event) {
      MinecraftServer server = event.getServer();
      ServerLevel overworld = server.overworld();
      if (overworld == null) {
         return;
      }

      long now = overworld.getGameTime();
      if (now % 20L == 0L) {
         refresh(overworld);
      }

      if (now % 6000L == 3000L && HerdService.enabled()) {
         for (ServerLevel level : server.getAllLevels()) {
            HerdService.housekeeping(level, now);
         }
      }

      if (now % 600L == 300L && HerdService.enabled()) {
         for (ServerLevel level : server.getAllLevels()) {
            HerdService.releaseUnloaded(level, now); // no group keeps unloaded animals in memory
         }
      }

      HerdCommand.tickDisplays(server);
   }

   private static void refresh(ServerLevel level) {
      try {
         HerdService.yearPos = SeasonClock.yearPosition(level);
      } catch (RuntimeException e) {
         // the calendar's data is not ready yet (very early in start-up): keep the last value
      }
   }
}
