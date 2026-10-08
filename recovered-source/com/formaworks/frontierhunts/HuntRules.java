package com.formaworks.frontierhunts;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;
import net.minecraft.world.level.GameRules.Key;
import net.minecraft.world.level.dimension.DimensionType;

public final class HuntRules {
   public static final ResourceKey<DimensionType> RESERVE_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, FrontierHunts.id("reserve"));
   public static final Key<BooleanValue> ENABLED = flag("frontierHunting", false, Category.MISC);
   public static final Key<BooleanValue> VANILLA_WEAPONS = flag("frontierAllowVanillaWeapons", false, Category.PLAYER);
   public static final Key<BooleanValue> MONSTERS = flag("frontierAllowVanillaMonsters", false, Category.SPAWNING);
   public static final Key<BooleanValue> TRACKING = flag("frontierTracking", true, Category.PLAYER);

   private static Key<BooleanValue> flag(String var0, boolean var1, Category var2) {
      return GameRules.register(var0, var2, BooleanValue.create(var1));
   }

   public static void bootstrap() {
   }

   public static boolean active(Level var0) {
      if (var0 instanceof ServerLevel var1 && var0.dimension().equals(Level.OVERWORLD) && var1.getServer().overworld().getGameRules().getBoolean(ENABLED)) {
         return true;
      }

      return false;
   }

   private HuntRules() {
   }
}
