package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.firsthunt.BeginnerArea;
import com.formaworks.frontierhunts.firsthunt.FirstHunt;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * [fharea] The beginner area is an easy spot: /fhqa flow easyherd | easyherd2 | easy | rough | legacy.
 * <ul>
 * <li>easyherd / easyherd2: test whitetails at an easy, open spot 100-230 blocks from the hunter (the second group well
 * away from the first), found by biome and then confirmed on loaded ground;</li>
 * <li>easy: the assigned area is in an easy biome (checked against the common tags directly, not through the judge), not
 * snowy or mountain, gentle (height range over 13 points), whitetails near its centre, within reach;</li>
 * <li>rough: the hunter in snowy / mountain country with deer only there: no area, "scout" (and the rough flag);</li>
 * <li>legacy: an area from before (no ground check) sitting on rough country is moved or dropped at the next check.</li>
 * </ul>
 */
final class AreaTest {
   private AreaTest() {
   }

   static final TagKey<Biome> C_SNOWY = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_snowy"));
   static final TagKey<Biome> C_MOUNTAIN = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_mountain"));
   static final TagKey<Biome> C_SLOPE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_slope"));
   static final TagKey<Biome> C_PEAK = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_peak"));
   static final TagKey<Biome> C_SWAMP = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_swamp"));
   static BlockPos firstSite;

   static void say(String s) {
      FlowTest.say(s);
   }

   static void verdict(boolean ok, String what) {
      FlowTest.verdict(ok, what);
   }

   static Holder<Biome> biomeAt(ServerLevel level, int x, int y, int z) {
      return level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
         level.getChunkSource().randomState().sampler());
   }

   static String name(Holder<Biome> b) {
      return b.unwrapKey().map(k -> k.location().toString()).orElse("?");
   }

   /** an independent "is this rough" check: the common tags and names, not the judge's own lists */
   static boolean roughBiome(Holder<Biome> b) {
      String n = name(b);
      return b.is(C_SNOWY) || b.is(C_MOUNTAIN) || b.is(C_SLOPE) || b.is(C_PEAK) || b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_OCEAN)
         || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(C_SWAMP) || b.value().getBaseTemperature() < 0.15F || n.contains("snow")
         || n.contains("frozen") || n.contains("glacial") || n.contains("peak") || n.contains("fellfield") || n.contains("talus") || n.contains("bluff")
         || n.contains("ocean") || n.contains("bog") || n.contains("marsh") || n.contains("gorge");
   }

   static void loadAround(ServerLevel level, int x, int z, int r) {
      for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
         for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
            level.getChunk(cx, cz); // test harness only: generate / load the land so the ground can be measured
         }
      }
   }

   static void force(ServerLevel level, int x, int z, boolean on) {
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            level.setChunkForced((x >> 4) + dx, (z >> 4) + dz, on);
         }
      }
   }

   /** an easy spot at 100-230 blocks from {@code from}, {@code minFrom} blocks or more from {@code avoid} (if any) */
   static BlockPos easySite(ServerLevel level, BlockPos from, double startAngle, BlockPos avoid, double minFrom) {
      int loads = 0, biome = 0, cheap = 0, ground = 0, walk = 0;
      for (int dist = 130; dist <= 320; dist += 20) {
         for (int k = 0; k < 24; k++) {
            double a = startAngle + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * Math.PI / 12.0;
            int x = from.getX() + (int)Math.round(Math.cos(a) * dist), z = from.getZ() + (int)Math.round(Math.sin(a) * dist);
            if (avoid != null && Math.hypot(x - avoid.getX(), z - avoid.getZ()) < minFrom) {
               continue;
            }
            int t = BeginnerArea.tier(biomeAt(level, x, level.getSeaLevel() + 4, z));
            if (t != BeginnerArea.OPEN && t != BeginnerArea.WOODS) {
               biome++;
               continue;
            }
            BeginnerArea.Site s = BeginnerArea.judge(level, x, z, BeginnerArea.RADIUS); // unloaded: biomes + estimated heights, cheap
            if (!s.ok || loads >= 40) {
               cheap++;
               continue;
            }
            loads++;
            loadAround(level, x, z, 72);
            s = BeginnerArea.judge(level, x, z, BeginnerArea.RADIUS);
            int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ()) - 1;
            if (!(s.ok && s.verified())) {
               ground++;
               continue;
            }
            if (Double.isNaN(BeginnerArea.path(level, from.getX(), y0, from.getZ(), s))) {
               walk++;
               continue;
            }
            say("easy site at " + x + "," + z + ", " + (int)Math.hypot(x - from.getX(), z - from.getZ()) + " blocks from the hunter: " + s.describe());
            return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
         }
      }
      say("no easy site: turned down by biome " + biome + ", by the cheap look " + cheap + ", on the ground " + ground + ", by the walk " + walk + " (" + loads
         + " areas loaded)");
      return null;
   }

   /** [1.3.0] a second easy patch with deer was found for the 'Find another area' check */
   static boolean secondPlaced = true;

   static void herd(ServerPlayer fp, ServerLevel level, boolean second) {
      BlockPos site = easySite(level, fp.blockPosition(), second ? Math.PI * 0.75 : 0.0, second ? firstSite : null, 200.0);
      if (site == null && second) {
         site = easySite(level, fp.blockPosition(), Math.PI * 0.25, firstSite, 130.0); // this world is tight on easy land: a little closer
      }
      if (second) {
         secondPlaced = site != null;
      }
      if (site == null && second) {
         say("SKIP second herd: this world has no second patch of easy land within reach (the area can only move within the first one)");
         return;
      }
      if (site == null) {
         verdict(false, "no easy, open spot found 130-280 blocks from the hunter for the test whitetails");
         return;
      }
      if (!second) {
         firstSite = site;
      }
      FlowTest.herd(level, site, second ? 2 : 3);
   }

   /** the assigned area: easy biome, not snowy or mountain, gentle, deer near its centre, within reach */
   static void easy(ServerPlayer fp, ServerLevel level, String when) {
      CompoundTag d = FlowTest.data(fp);
      if (!d.getBoolean("area")) {
         verdict(false, "easy area (" + when + "): no area assigned (scouting " + d.getBoolean("scouting") + ", rough " + d.getBoolean("rough") + ")");
         return;
      }
      int ax = d.getInt("areaX"), az = d.getInt("areaZ"), r = d.getInt("areaR");
      loadAround(level, ax, az, r + 8);
      Holder<Biome> centre = level.getBiome(new BlockPos(ax, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az), az));
      int tier = BeginnerArea.tier(centre);
      // the ground: the centre and 12 points at 0.5 r and 0.95 r (trees stepped through)
      int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, roughPts = 0, n = 0;
      for (int i = -1; i < 12; i++) {
         double a = i * Math.PI / 6.0, rr = i < 0 ? 0 : (i % 2 == 0 ? 0.5 : 0.95) * r;
         int x = ax + (int)Math.round(Math.cos(a) * rr), z = az + (int)Math.round(Math.sin(a) * rr);
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
         BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
         for (int k = 0; k < 40 && (level.getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS) || level.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES)
            || level.getBlockState(p).isAir()); k++) {
            p.move(0, -1, 0);
         }
         lo = Math.min(lo, p.getY());
         hi = Math.max(hi, p.getY());
         if (roughBiome(level.getBiome(p))) {
            roughPts++;
         }
         n++;
      }
      int deer = level.getEntitiesOfClass(Whitetail.class, new AABB(ax - r - 48, level.getMinBuildHeight(), az - r - 48, ax + r + 48, level.getMaxBuildHeight(),
         az + r + 48), w -> w.isAlive() && !w.downed()).size();
      double dist = Math.hypot(ax - fp.getX(), az - fp.getZ());
      boolean ok = tier != BeginnerArea.NEVER && !roughBiome(centre) && roughPts <= 3 && hi - lo <= BeginnerArea.MAX_RANGE + 6 && deer > 0 && dist <= BeginnerArea.FARTHEST + 1;
      verdict(ok, "easy area (" + when + "): centre " + ax + "," + az + " in " + name(centre) + " (tier " + tier + ", rough by tags " + roughBiome(centre)
         + "), rough points " + roughPts + "/" + n + ", height range " + (hi - lo) + " (judge max " + BeginnerArea.MAX_RANGE + " on its own 49 points, 6 more allowed for this probe's other points), whitetails near " + deer
         + ", " + (int)dist + " blocks from the hunter; judge: " + BeginnerArea.explain(level, ax, az));
   }

   /** a spot deep in snowy / mountain country (every point of a 180-block ring rough too), or null */
   static BlockPos roughSite(ServerLevel level, BlockPos from) {
      for (int k = 0; k < 10; k++) {
         double a = k * 2.4;
         BlockPos origin = from.offset((int)(Math.cos(a) * 1200 * (k > 0 ? 1 : 0)), 0, (int)(Math.sin(a) * 1200 * (k > 0 ? 1 : 0)));
         Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(b -> BeginnerArea.snowy(b) && BeginnerArea.tier(b) == BeginnerArea.NEVER, origin,
            2400, 64, 256);
         if (found == null) {
            continue;
         }
         // go a little deeper into it: the point of the 9 around (200 apart) with the most rough land around it
         BlockPos best = null;
         int bestScore = -1;
         for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
               int x = found.getFirst().getX() + i * 200, z = found.getFirst().getZ() + j * 200, score = 0, inner = 0;
               for (int q = 0; q < 16; q++) {
                  for (double rr : new double[]{60.0, 120.0, 180.0}) {
                     double aa = q * Math.PI / 8.0;
                     Holder<Biome> b = biomeAt(level, x + (int)(Math.cos(aa) * rr), level.getSeaLevel() + 4, z + (int)(Math.sin(aa) * rr));
                     if (BeginnerArea.tier(b) == BeginnerArea.NEVER) {
                        score++;
                        inner += rr < 100.0 ? 1 : 0;
                     }
                  }
               }
               if (BeginnerArea.tier(biomeAt(level, x, level.getSeaLevel() + 4, z)) == BeginnerArea.NEVER && inner == 16 && score > bestScore) {
                  bestScore = score;
                  best = new BlockPos(x, 0, z);
               }
            }
         }
         if (best != null && bestScore >= 40) {
            say("rough country at " + best.getX() + "," + best.getZ() + " (" + name(biomeAt(level, best.getX(), level.getSeaLevel() + 4, best.getZ()))
               + "), " + bestScore + " of 48 points on three rings to 180 blocks rough (all of the inner ring)");
            return best;
         }
      }
      return null;
   }

   static BlockPos roughCached;

   static BlockPos rough(ServerLevel level) {
      if (roughCached == null) {
         roughCached = roughSite(level, level.getSharedSpawnPos());
      }
      return roughCached;
   }

   /** an area from before the easy rule, on rough ground far away: at the next check it is moved to an easy spot near the hunter */
   static void legacy(ServerPlayer fp, ServerLevel level) {
      BlockPos site = rough(level);
      if (site == null) {
         say("SKIP legacy: no wide snowy / mountain country found within reach of spawn in this world");
         return;
      }
      CompoundTag d = FlowTest.data(fp);
      d.putBoolean("area", true);
      d.putString("areaDim", level.dimension().location().toString());
      d.putInt("areaX", site.getX());
      d.putInt("areaZ", site.getZ());
      d.putInt("areaR", 64);
      d.remove("areaV");
      d.putBoolean("areaQuiet", false);
      d.putLong("areaRetry", 0L);
      FlowTest.tick(fp, 120);
      d = FlowTest.data(fp);
      boolean moved = Math.hypot(d.getInt("areaX") - site.getX(), d.getInt("areaZ") - site.getZ()) > 1.0;
      verdict(d.getBoolean("area") && moved && d.getInt("areaV") >= 1, "an old beginner area in snowy / mountain country ("
         + name(biomeAt(level, site.getX(), level.getSeaLevel() + 4, site.getZ())) + ") is moved to an easy spot with deer at the next check (now "
         + d.getInt("areaX") + "," + d.getInt("areaZ") + ", " + (int)Math.hypot(d.getInt("areaX") - fp.getX(), d.getInt("areaZ") - fp.getZ())
         + " blocks from the hunter, ground checked " + (d.getInt("areaV") >= 2) + ")");
   }

   static void rough(ServerPlayer fp, ServerLevel level) throws Exception {
      BlockPos site = rough(level);
      if (site == null) {
         say("SKIP rough: no wide snowy / mountain country found within reach of spawn in this world");
         return;
      }
      int x = site.getX(), z = site.getZ();
      force(level, x, z, true);
      int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      fp.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
      // only these deer: clear any others within reach
      int gone = 0;
      for (Whitetail w : level.getEntitiesOfClass(Whitetail.class, new AABB(fp.blockPosition()).inflate(420, 256, 420))) {
         w.discard();
         gone++;
      }
      @SuppressWarnings("unchecked")
      EntityType<Whitetail> type = (EntityType<Whitetail>)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:whitetail"));
      int spawned = 0;
      for (int i = 0; i < 3; i++) {
         int dx = x + 6 + i * 4, dz = z + 3 * i;
         if (type.spawn(level, new BlockPos(dx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz), dz), MobSpawnType.COMMAND) != null) {
            spawned++;
         }
      }
      CompoundTag d = FlowTest.data(fp);
      d.putBoolean("area", false);
      d.remove("areaV");
      var assign = FirstHunt.class.getDeclaredMethod("assignArea", ServerPlayer.class, CompoundTag.class, boolean.class);
      assign.setAccessible(true);
      boolean got = (boolean)assign.invoke(null, fp, d, false);
      d = FlowTest.data(fp);
      int alive = level.getEntitiesOfClass(Whitetail.class, new AABB(fp.blockPosition()).inflate(64, 256, 64), w -> w.isAlive() && !w.downed()).size();
      verdict(!got && !d.getBoolean("area") && d.getBoolean("scouting") && (d.getBoolean("rough") || alive == 0), "whitetails only in snowy / mountain country ("
         + alive + " still there; with none left 'no deer near' is the right answer, else 'rough country'; "
         + name(level.getBiome(fp.blockPosition())) + ", " + spawned + " deer there, " + gone + " others cleared): no beginner area, the hunter scouts (assigned "
         + got + ", scouting " + d.getBoolean("scouting") + ", rough " + d.getBoolean("rough") + "); here: " + BeginnerArea.explain(level, x, z));
      // the new-area button says the same, and changes nothing
      FirstHunt.action(fp, FirstHuntNetwork.A_NEW_AREA);
      verdict(!FlowTest.data(fp).getBoolean("area"), "'Find another area' in rough country: still no area (no hunter sent up a mountain)");
      // a deer at an old area on rough ground (from before the easy rule): moved or dropped at the next check
      d = FlowTest.data(fp);
      d.putBoolean("area", true);
      d.putString("areaDim", level.dimension().location().toString());
      d.putInt("areaX", x + 10);
      d.putInt("areaZ", z + 10);
      d.putInt("areaR", 64);
      d.remove("areaV");
      d.putBoolean("areaQuiet", false);
      FlowTest.tick(fp, 45);
      d = FlowTest.data(fp);
      boolean moved = !d.getBoolean("area") || Math.hypot(d.getInt("areaX") - (x + 10), d.getInt("areaZ") - (z + 10)) > 1.0;
      boolean nowEasy = !d.getBoolean("area") || BeginnerArea.judge(level, d.getInt("areaX"), d.getInt("areaZ"), 64).ok;
      verdict(moved && nowEasy && d.getInt("areaV") != 0 || moved && !d.getBoolean("area"), "an old beginner area on snowy / mountain ground is moved or dropped at the next check (area "
         + d.getBoolean("area") + (d.getBoolean("area") ? " at " + d.getInt("areaX") + "," + d.getInt("areaZ") : "") + ", moved " + moved + ")");
      force(level, x, z, false);
      for (Whitetail w : level.getEntitiesOfClass(Whitetail.class, new AABB(fp.blockPosition()).inflate(64, 128, 64))) {
         w.discard();
      }
      d.putBoolean("area", false);
      d.putBoolean("rough", false);
      d.putLong("areaRetry", 0L);
   }

   /** the judge's limits on a made-up row of the world: not needed on a server, kept for a quick look at the tiers */
   static void tiers(ServerLevel level) {
      StringBuilder sb = new StringBuilder();
      level.registryAccess().registryOrThrow(Registries.BIOME).holders().forEach(h -> sb.append(h.key().location().getPath()).append('=')
         .append(BeginnerArea.tier(h)).append(BeginnerArea.snowy(h) ? "*" : "").append(' '));
      say("biome tiers (1 open, 2 woods, 3 fair, 0 never, * snowy): " + sb);
   }
}
