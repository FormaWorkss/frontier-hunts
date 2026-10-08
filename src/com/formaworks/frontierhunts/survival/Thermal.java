package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.season.SeasonState;
import com.formaworks.frontierhunts.shelter.ShelterScan;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import com.formaworks.frontierhunts.weather.WeatherKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * [survival] The environment side of body temperature: air temperature from biome, season, time of day and altitude;
 * shelter (roof + walls, caves and cellars); nearby fires and stoves; wind; ice storage for food.
 *
 * Cost: {@link #air} is a handful of arithmetic and one biome lookup. {@link #heat} scans a 13x5x13 box and
 * {@link #shelter} (the shared detector) typically 40-140 blocks; the caller runs them once a second per player.
 */
public final class Thermal {
   private Thermal() {
   }

   /**
    * [shelter] Shelter at the player's eye, from the shared detector ({@link com.formaworks.frontierhunts.shelter.Shelter},
    * the same one the client uses to fade the storm out): roof, enclosure (tent, closed room, cave), wind and
    * precipitation blocking, all 0..1, plus the sky light.
    */
   public record Shelter(float roof, float enclosure, float windBlock, float precipBlock, boolean tent, int sky) {
      public static final Shelter OPEN = new Shelter(0F, 0F, 0F, 0F, false, 15);

      public static Shelter of(ShelterScan.Result r, int sky) {
         return new Shelter(r.roof, r.enclosure, r.windBlock, r.precipBlock, r.tent && r.enclosure > 0.5F, sky);
      }

      public boolean roofed() {
         return this.precipBlock >= 0.6F;
      }

      public boolean enclosed() {
         return this.enclosure >= 0.6F;
      }

      /** 0 indoors .. 1 fully exposed to driven snow and rain. */
      public float exposure() {
         return 1F - Math.max(this.windBlock, this.precipBlock);
      }

      /** 0 out of the wind .. 1 fully exposed to it. */
      public float windExposure() {
         return 1F - this.windBlock;
      }

      /** 0..1 how little sky light reaches under the roof (caves and cellars settle toward ground temperature). */
      public float under() {
         return this.roof >= 0.5F ? 1F - this.sky / 15F : 0F;
      }
   }

   // ============================================================================================ air

   /** Air temperature at a spot before shelter, fires, wind and wetness. */
   public static float air(Level level, BlockPos pos) {
      if (level.dimensionType().ultraWarm()) {
         return 42F;
      }
      Holder<Biome> h = level.getBiome(pos);
      Biome b = h.value();
      float base = b.getBaseTemperature();
      if (!level.dimensionType().hasSkyLight()) {
         return SurvivalMath.summerTemperature(base) - 4F;
      }
      boolean seasonal = SeasonState.seasonal(b);
      boolean dry = !b.hasPrecipitation() && base >= 1.0F;
      double year = SeasonClock.yearPosition(level);
      return SurvivalMath.air(base, seasonal, SeasonState.enabled(), year, level.getDayTime(), pos.getY(), dry);
   }

   /**
    * [shelter] Still air in the player's shelter: caves settle near 11 deg C, a roof, an enclosed room and a tent hold body
    * heat (see {@link SurvivalMath#shelterAir}).
    */
   public static float sheltered(float air, Shelter s) {
      return SurvivalMath.shelterAir(air, s.under(), s.roof(), s.enclosure(), s.tent() ? 1F : 0F);
   }

   // ============================================================================================ shelter

   /** [shelter] The shared shelter detector at the player's eye (also covers sleeping in a tent). */
   public static Shelter shelter(Player p) {
      ShelterScan.Result r = com.formaworks.frontierhunts.shelter.Shelter.scan(p, p.getEyePosition(), new ShelterScan.Result());
      Level level = p.level();
      int sky = level.dimensionType().hasSkyLight() ? level.getBrightness(LightLayer.SKY, BlockPos.containing(p.getEyePosition())) : 0;
      return Shelter.of(r, sky);
   }

   // ============================================================================================ fires

   private static final ResourceLocation LODGE_STOVE = ResourceLocation.fromNamespaceAndPath("frontierhunts", "lodge_stove");
   private static final ResourceLocation CABIN_LANTERN = ResourceLocation.fromNamespaceAndPath("frontierhunts", "cabin_lantern");
   private static final ResourceLocation LIT_LODGE_TABLE = ResourceLocation.fromNamespaceAndPath("frontierhunts", "lit_lodge_table");
   private static final ResourceLocation SMOKEHOUSE = ResourceLocation.fromNamespaceAndPath("frontierhunts", "smokehouse");

   /** Warmth of one block as a heat source (deg C at the source, fading out over 5 blocks), 0 if none. */
   public static float heatOf(BlockState st) {
      Block b = st.getBlock();
      if (b instanceof CampfireBlock) {
         return st.getValue(CampfireBlock.LIT) ? (st.is(Blocks.SOUL_CAMPFIRE) ? 11F : 15F) : 0F;
      }
      if (st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE)) {
         return 12F;
      }
      if (st.is(Blocks.LAVA)) {
         return 16F;
      }
      if (st.is(Blocks.MAGMA_BLOCK)) {
         return 4F;
      }
      if (b instanceof AbstractFurnaceBlock) {
         return st.getValue(AbstractFurnaceBlock.LIT) ? 9F : 0F;
      }
      if (st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH) || st.is(Blocks.LANTERN) || st.is(Blocks.JACK_O_LANTERN)) {
         return 1.5F;
      }
      if (st.is(Blocks.SOUL_TORCH) || st.is(Blocks.SOUL_WALL_TORCH) || st.is(Blocks.SOUL_LANTERN)) {
         return 1F; // [shelter]
      }
      if (st.hasProperty(BlockStateProperties.LIT) && st.getValue(BlockStateProperties.LIT) && st.is(BlockTags.CANDLES)) {
         return 0.7F;
      }
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
      if (id.equals(LODGE_STOVE)) {
         return 16F;
      }
      if (id.equals(CABIN_LANTERN) || id.equals(LIT_LODGE_TABLE)) {
         return 1.5F; // [shelter] the cabins' lanterns
      }
      if (id.equals(SMOKEHOUSE)) {
         for (net.minecraft.world.level.block.state.properties.Property<?> pr : st.getProperties()) {
            if (pr.getName().equals("working") && pr instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp) {
               return st.getValue(bp) ? 6F : 0F;
            }
         }
         return 0F;
      }
      return 0F;
   }

   /**
    * [shelter] Radiant heat of nearby fires (deg C, capped at 30): a 13x5x13 box around the feet. Each source fades out
    * with distance over 5.5 blocks in the open and up to 9 blocks in an enclosed room ({@code enclosure} 0..1, the heat
    * stays in); a solid opaque wall between the source and the player's head lets only 30% through (a fire behind the
    * cabin wall does not warm you, the stove in the corner does).
    */
   public static float heat(Level level, BlockPos feet, BlockPos head, float enclosure) {
      float sum = 0F;
      float reach = 5.5F + 3.5F * Math.max(0F, Math.min(1F, enclosure));
      int los = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = -1; dy <= 3; dy++) {
         for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
               m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
               BlockState st = level.getBlockState(m);
               if (st.isAir()) {
                  continue;
               }
               float h = heatOf(st);
               if (h > 0F) {
                  double d = Math.sqrt(dx * dx + dz * dz + (dy - 0.5) * (dy - 0.5));
                  float f = (float) Math.max(0.0, 1.0 - d / reach);
                  if (f <= 0F) {
                     continue;
                  }
                  if (!st.is(Blocks.LAVA) && los++ < 32 && walled(level, m, head)) {
                     f *= 0.3F;
                  }
                  sum += h * f;
               }
            }
         }
      }
      return Math.min(30F, sum);
   }

   /**
    * [1.2.5] How warm a real fire keeps you, whatever the weather: sitting up close to a campfire, a fire, lava or a lit
    * stove you are warm even in a blizzard (about 27 deg C felt at arm's length, falling off over four or five blocks).
    * Returns the felt temperature the nearest such fire holds you at, or -1000 if there is none close enough. A wall
    * between you and the fire takes most of it.
    */
   public static float fireFloor(Level level, BlockPos feet, BlockPos head) {
      float best = -1000F;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = -2; dy <= 2; dy++) {
         for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
               m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
               BlockState st = level.getBlockState(m);
               if (st.isAir()) {
                  continue;
               }
               float h = heatOf(st);
               if (h < 9F) {
                  continue; // torches and lanterns light a place, they do not keep you warm
               }
               double d = Math.sqrt(dx * dx + dz * dz + (dy - 0.5) * (dy - 0.5));
               float floor = (h >= 15F ? 27F : 24F) - 6F * (float)Math.max(0.0, d - 1.2);
               if (floor <= best) {
                  continue;
               }
               if (!st.is(Blocks.LAVA) && walled(level, m.immutable(), head)) {
                  floor -= 14F;
               }
               best = Math.max(best, floor);
            }
         }
      }
      return best;
   }

   /**
    * [1.2.5] Dug in: the head below the ground all round (a hole, a trench, a snow pit). The earth around you keeps the
    * wind off and holds the cold back.
    */
   public static boolean dugIn(Level level, BlockPos head) {
      int solid = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) {
               continue;
            }
            m.set(head.getX() + dx, head.getY(), head.getZ() + dz);
            BlockState st = level.getBlockState(m);
            if (st.isCollisionShapeFullBlock(level, m) || st.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock
               && st.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 6) {
               solid++;
            }
         }
      }
      return solid >= 7;
   }

   /** Kept for callers of the old signature (open-air falloff). */
   public static float heat(Level level, BlockPos feet) {
      return heat(level, feet, feet.above(), 0F);
   }

   /** True when a solid opaque block stands between a heat source and the head (half-block steps, endpoints excluded). */
   private static boolean walled(Level level, BlockPos from, BlockPos to) {
      double x0 = from.getX() + 0.5, y0 = from.getY() + 0.5, z0 = from.getZ() + 0.5;
      double x1 = to.getX() + 0.5, y1 = to.getY() + 0.5, z1 = to.getZ() + 0.5;
      double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
      int steps = (int) Math.ceil(len * 2.0);
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int i = 1; i < steps; i++) {
         double t = i / (double) steps;
         m.set(Math.floor(x0 + (x1 - x0) * t), Math.floor(y0 + (y1 - y0) * t), Math.floor(z0 + (z1 - z0) * t));
         if (m.equals(from) || m.equals(to)) {
            continue;
         }
         BlockState st = level.getBlockState(m);
         if (st.canOcclude() && st.isSolidRender(level, m)) {
            return true;
         }
      }
      return false;
   }

   // ============================================================================================ weather

   /** Weather penalty (deg C, positive = colder) and extra wind for an exposed spot. */
   public static float weatherCold(Level level, BlockPos pos, float exposure) {
      if (exposure <= 0F || !level.isRaining()) {
         return 0F;
      }
      float c = level.isThundering() ? 4F : 2.5F;
      float bl = SeasonalWeather.severity(level, pos, WeatherKind.BLIZZARD);
      c += bl * 10F;
      return c * exposure;
   }

   public static float windSpeed(Level level, BlockPos pos) {
      float e = SeasonalWeather.windEast(level), s = SeasonalWeather.windSouth(level);
      float w = (float) Math.sqrt(e * e + s * s);
      w += SeasonalWeather.severity(level, pos, WeatherKind.BLIZZARD) * 9F;
      w += SeasonalWeather.severity(level, pos, WeatherKind.WIND_STORM) * 6F;
      w += Math.max(0F, (pos.getY() - 90) / 30F);
      return w;
   }

   /** True when it is snowing (not raining) at an open spot. */
   public static boolean snowingAt(Level level, BlockPos pos) {
      return level.isRaining() && level.canSeeSky(pos) && level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW;
   }

   // ============================================================================================ food storage

   /** Store class for food kept in a container at {@code pos}: ice/snow packed around it, cellars, the climate. */
   public static int storeAt(Level level, BlockPos pos) {
      float t = air(level, pos);
      int sky = level.getBrightness(LightLayer.SKY, pos);
      if (sky < 8) {
         t += (11F - t) * (1F - sky / 15F) * 0.85F; // cellar / indoors
         t = Math.min(t, sky <= 2 ? 9F : t);
      }
      int ice = 0;
      boolean deep = false;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (Direction d : Direction.values()) {
         m.setWithOffset(pos, d);
         BlockState st = level.getBlockState(m);
         if (st.is(Blocks.PACKED_ICE) || st.is(Blocks.BLUE_ICE)) {
            ice++;
            deep = true;
         } else if (st.is(Blocks.ICE) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW)) {
            ice++;
         }
      }
      if (ice >= 1) {
         t = Math.min(t, ice >= 2 ? (deep ? -6F : 1F) : 4F);
      }
      return SurvivalMath.storeFor(t);
   }
}
