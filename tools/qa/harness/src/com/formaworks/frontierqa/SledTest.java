package com.formaworks.frontierqa;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * [1.2.2] The toboggan on real generated land: finds snowy slopes (gentle, medium, steep) around the world origin, puts
 * a sled on each pointing down the fall line and rides it with the real SledEntity code for a while, on the server
 * (its test input stands in for a rider). Reports speed, airtime, hits and every place it got stuck, with the blocks
 * around it. /fhqa sledtest <runs per slope class> <search radius>
 */
final class SledTest {
   private SledTest() {
   }

   record Spot(int x, int z, double slope, double dx, double dz, String biome) {
   }

   static int run(CommandSourceStack src, int perClass, int radius) {
      return run(src, perClass, radius, "sled");
   }

   static int run(CommandSourceStack src, int perClass, int radius, String kind) {
      traced = 0;
      ServerLevel level = src.getServer().overworld();
      ChunkGenerator gen = level.getChunkSource().getGenerator();
      RandomState rs = level.getChunkSource().randomState();
      List<List<Spot>> classes = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
      int seen = 0, snowy = 0;
      outer:
      for (int r = 0; r <= radius; r += 40) {
         int steps = Math.max(1, (int)(2 * Math.PI * r / 40));
         for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            int x = (int)(Math.cos(a) * r), z = (int)(Math.sin(a) * r);
            seen++;
            int h = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
            Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(x >> 2, h >> 2, z >> 2, rs.sampler());
            String name = b.unwrapKey().map(k -> k.location().getPath()).orElse("?");
            if (!(name.contains("snow") || name.contains("glacial") || name.contains("frozen") || name.contains("ice"))) {
               continue;
            }
            snowy++;
            double hx = (gen.getBaseHeight(x + 10, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - gen.getBaseHeight(x - 10, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs)) / 20.0;
            double hz = (gen.getBaseHeight(x, z + 10, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - gen.getBaseHeight(x, z - 10, Heightmap.Types.WORLD_SURFACE_WG, level, rs)) / 20.0;
            double g = Math.hypot(hx, hz);
            int c = g < 0.1 ? -1 : g < 0.3 ? 0 : g < 0.65 ? 1 : 2;
            if (c < 0 || classes.get(c).size() >= perClass) {
               continue;
            }
            classes.get(c).add(new Spot(x, z, g, -hx / g, -hz / g, name));
            if (classes.get(0).size() >= perClass && classes.get(1).size() >= perClass && classes.get(2).size() >= perClass) {
               break outer;
            }
         }
      }
      FrontierQa.say("sledtest searched " + seen + " points, " + snowy + " snowy; slopes found gentle=" + classes.get(0).size() + " medium="
         + classes.get(1).size() + " steep=" + classes.get(2).size());
      int runs = 0, stuckRuns = 0, totalRefused = 0;
      double sumMax = 0;
      String[] cls = {"gentle", "medium", "steep"};
      for (int c = 0; c < 3; c++) {
         for (Spot s : classes.get(c)) {
            for (int mode = 0; mode < 2; mode++) {
               Result res = ride(level, s, mode == 1, kind);
               runs++;
               sumMax += res.maxKmh;
               if (res.stuck > 0) {
                  stuckRuns++;
               }
               String how = kind.equals("snowmobile") ? (mode == 1 ? "down-weave" : "climbing  ") : (mode == 1 ? "weaving " : "straight");
               FrontierQa.say(String.format("%s %s %s at %d %d (%s, slope %.2f): %d ticks, %.0f blocks, climbed %.0f, max %.0f km/h, mean %.0f km/h, air %d (%d jumps), hits %d, stuck %d, server-resets %d, refused %d%s",
                  kind, cls[c], how, s.x, s.z, s.biome, s.slope, res.ticks, res.dist, res.climbed, res.maxKmh, res.meanKmh, res.air, res.launches, res.hits,
                  res.stuck, res.resets, res.refused, res.note));
               totalRefused += res.refused;
            }
         }
      }
      FrontierQa.say(String.format("sledtest %s runs=%d stuckRuns=%d meanMax=%.0f km/h refusedMoves=%d", kind, runs, stuckRuns, runs == 0 ? 0 : sumMax / runs,
         totalRefused));
      for (int c = 2; c >= 0; c--) {
         if (!classes.get(c).isEmpty()) {
            exploits(level, classes.get(c).get(0), kind);
            break;
         }
      }
      return 1;
   }

   static int traced;

   static final class Result {
      int ticks, air, hits, stuck, launches, resets, refused;
      double climbed;
      double dist, maxKmh, meanKmh;
      String note = "";
   }

   static void load(ServerLevel level, double x, double z) {
      int cx = ((int)Math.floor(x)) >> 4, cz = ((int)Math.floor(z)) >> 4;
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            level.getChunk(cx + dx, cz + dz);
         }
      }
   }

   static Result ride(ServerLevel level, Spot s, boolean weave, String kind) {
      boolean mobile = kind.equals("snowmobile");
      Result res = new Result();
      try {
         load(level, s.x, s.z);
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
         EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:" + kind));
         Entity e = type.create(level);
         float yaw = (float)Math.toDegrees(Math.atan2(-s.dx, s.dz));
         if (mobile && !weave) {
            yaw += 180.0F; // straight up the mountain
         }
         double y0 = Double.NaN;
         e.moveTo(s.x + 0.5, y + 0.05, s.z + 0.5, yaw, 0.0F);
         java.lang.reflect.Field in = e.getClass().getField("qaInput");
         java.lang.reflect.Method impact = e.getClass().getMethod("impact");
         java.lang.reflect.Method valid = e.getClass().getMethod("validMove", net.minecraft.world.phys.Vec3.class, net.minecraft.world.phys.Vec3.class);
         double moving = 0;
         int movingTicks = 0, still = 0;
         boolean reported = false, wasGround = true;
         int airRun = 0;
         java.util.ArrayDeque<String> trace = new java.util.ArrayDeque<>();
         for (int t = 0; t < 300; t++) {
            float side = 0.0F;
            if (weave) {
               side = (t / 25) % 2 == 0 ? 0.7F : -0.7F;
            }
            float fwd = mobile ? (weave ? 0.6F : 1.0F) : t < 12 ? 1.0F : 0.0F;
            in.set(e, new float[]{fwd, side});
            load(level, e.getX(), e.getZ());
            double px = e.getX(), pz = e.getZ();
            e.xo = px;
            e.yo = e.getY();
            e.zo = pz;
            boolean freeBefore = level.noCollision(e, e.getBoundingBox().deflate(0.0625));
            net.minecraft.world.phys.Vec3 before = e.position();
            e.tick();
            if (Double.isNaN(y0) && t == 0) {
               y0 = e.getY();
            }
            res.climbed = Math.max(res.climbed, e.getY() - y0);
            if (!(boolean)valid.invoke(e, before, e.position().subtract(before))) {
               res.refused++; // the server's move check would have refused this real move
               if (res.refused <= 2) {
                  FrontierQa.say(String.format("REFUSED %s move at %.2f %.2f %.2f by %.2f %.2f %.2f: %s | %s", kind, before.x, before.y, before.z,
                     e.getX() - before.x, e.getY() - before.y, e.getZ() - before.z, e.getClass().getField("refusedWhy").get(e), state(e)));
               }
            }
            if (freeBefore && !level.noCollision(e, e.getBoundingBox().deflate(0.0625))) {
               res.resets++; // a server would have thrown the sled back here
            }
            double d = Math.hypot(e.getX() - px, e.getZ() - pz);
            trace.addLast(String.format("t%d %.2f %.2f %.2f yaw %.0f v %.3f %s %s", t, e.getX(), e.getY(), e.getZ(), e.getYRot(), d, e.onGround() ? "G" : "AIR", state(e)));
            if (trace.size() > 40) {
               trace.removeFirst();
            }
            res.ticks++;
            res.dist += d;
            res.maxKmh = Math.max(res.maxKmh, d * 72.0);
            if (d > 0.05) {
               moving += d;
               movingTicks++;
            }
            if (!e.onGround()) {
               res.air++;
               if (wasGround) {
                  res.launches++;
               }
            }
            wasGround = e.onGround();
            airRun = e.onGround() ? 0 : airRun + 1;
            if (airRun == 40 && traced++ < 6) {
               FrontierQa.say("TRACE long flight at " + s.x + " " + s.z + ":\n   " + String.join("\n   ", trace));
            }
            if ((double)impact.invoke(e) > 0.25) {
               res.hits++;
            }
            if (Double.isNaN(e.getY()) || e.getY() < level.getMinBuildHeight()) {
               res.note = " FELL OUT";
               break;
            }
            still = d < 0.03 && t > 20 ? still + 1 : 0;
            if (still == 30) {
               res.stuck++;
               if (!reported) {
                  reported = true;
                  res.note = " | stuck at " + around(level, e) + " | " + state(e);
                  if (traced++ < 4) {
                     FrontierQa.say("TRACE before the stop at " + s.x + " " + s.z + ":\n   " + String.join("\n   ", trace));
                  }
               }
            }
            if (still > 60) {
               break; // it's stopped for good
            }
         }
         res.meanKmh = movingTicks == 0 ? 0 : moving / movingTicks * 72.0;
         e.discard();
      } catch (Exception ex) {
         res.note = " ERROR " + ex;
         FrontierQa.fail("sled ride", ex);
      }
      return res;
   }

   static String state(Entity e) {
      try {
         Object p = e.getClass().getMethod("physics").invoke(e);
         Class<?> c = p.getClass();
         return String.format("phys speed %.3f pitchSlope %.2f rollSlope %.2f ground %s why '%s'", (double)c.getMethod("speed").invoke(p),
            c.getField("pitchSlope").getDouble(p), c.getField("rollSlope").getDouble(p), c.getField("ground").get(p), c.getField("why").get(p));
      } catch (Exception ex) {
         return "phys ? " + ex;
      }
   }

   /** the blocks around a stopped sled, to see what held it */
   static String around(ServerLevel level, Entity e) {
      StringBuilder b = new StringBuilder(String.format("%.2f %.2f %.2f yaw %.0f ground %b: ", e.getX(), e.getY(), e.getZ(), e.getYRot(), e.onGround()));
      int bx = (int)Math.floor(e.getX()), by = (int)Math.floor(e.getY()), bz = (int)Math.floor(e.getZ());
      double rad = Math.toRadians(e.getYRot());
      int fx = (int)Math.round(-Math.sin(rad)), fz = (int)Math.round(Math.cos(rad));
      for (int step = -1; step <= 2; step++) {
         int x = bx + fx * step, z = bz + fz * step;
         b.append("[").append(step).append("]");
         for (int dy = 2; dy >= -2; dy--) {
            BlockState st = level.getBlockState(new BlockPos(x, by + dy, z));
            if (st.isAir()) {
               continue;
            }
            b.append(" ").append(dy).append("=").append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath());
            if (st.hasProperty(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)) {
               b.append("x").append(st.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS));
            }
         }
         b.append(";");
      }
      return b.toString();
   }

   /** [1.2.5] moves a cheating client might send: the server must refuse each, and take an honest one */
   static void exploits(ServerLevel level, Spot s, String kind) {
      try {
         load(level, s.x, s.z);
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
         Entity e = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:" + kind)).create(level);
         e.moveTo(s.x + 0.5, y + 0.05, s.z + 0.5, 0.0F, 0.0F);
         java.lang.reflect.Method valid = e.getClass().getMethod("validMove", net.minecraft.world.phys.Vec3.class, net.minecraft.world.phys.Vec3.class);
         net.minecraft.world.phys.Vec3 p = e.position();
         // into the hillside: the deepest full block within 3 blocks sideways and down
         net.minecraft.world.phys.Vec3 into = null;
         for (int dx = -3; dx <= 3 && into == null; dx++) {
            for (int dz = -3; dz <= 3 && into == null; dz++) {
               BlockPos b = BlockPos.containing(p.x + dx, p.y - 3, p.z + dz);
               if (level.getBlockState(b).isCollisionShapeFullBlock(level, b) && level.getBlockState(b.above()).isCollisionShapeFullBlock(level, b.above())
                  && level.getBlockState(b.above(2)).isCollisionShapeFullBlock(level, b.above(2))) {
                  into = new net.minecraft.world.phys.Vec3(b.getX() + 0.5 - p.x, b.getY() - p.y, b.getZ() + 0.5 - p.z);
               }
            }
         }
         Object[][] cases = {
            {"honest: a metre along the snow", new net.minecraft.world.phys.Vec3(1.0,
               level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x + 1, s.z) + 0.05 - p.y, 0.0), true},
            {"teleport 12 blocks", new net.minecraft.world.phys.Vec3(12.0, 0.0, 0.0), false},
            {"keep climbing into the sky (1 block a tick for 16 ticks)", null, false},
            {"hover level 10 blocks up (20 ticks)", null, false},
            {"into the ground (noclip)", into, false},
         };
         for (Object[] c : cases) {
            net.minecraft.world.phys.Vec3 d = (net.minecraft.world.phys.Vec3)c[1];
            net.minecraft.world.phys.Vec3 from = p;
            if (c[0].toString().startsWith("hover")) {
               boolean any = false;
               for (int i = 0; i < 20; i++) {
                  any |= !(boolean)valid.invoke(e, p.add(0.6 * i, 10.0, 0), new net.minecraft.world.phys.Vec3(0.6, 0.0, 0.0));
               }
               FrontierQa.say("EXPLOIT " + kind + " " + c[0] + ": " + (any ? "refused ok" : "accepted WRONG"));
               continue;
            }
            if (c[0].toString().startsWith("keep climbing")) {
               // a fly cheat: rise steadily; the first ticks near the ground pass, then it must be refused
               boolean any = false;
               for (int i = 0; i < 16; i++) {
                  any |= !(boolean)valid.invoke(e, p.add(0.2 * i, 1.0 * i, 0), new net.minecraft.world.phys.Vec3(0.2, 1.0, 0.0));
               }
               FrontierQa.say("EXPLOIT " + kind + " " + c[0] + ": " + (any ? "refused ok" : "accepted WRONG"));
               continue;
            }
            if (d == null) {
               FrontierQa.say("EXPLOIT " + kind + " " + c[0] + ": no solid ground found to test");
               continue;
            }
            boolean ok = (boolean)valid.invoke(e, from, d);
            boolean want = (boolean)c[2];
            FrontierQa.say("EXPLOIT " + kind + " " + c[0] + ": " + (ok ? "accepted" : "refused") + (ok == want ? " ok" : " WRONG"));
         }
         e.discard();
      } catch (Exception ex) {
         FrontierQa.fail("sled exploits", ex);
      }
   }

   /** [1.2.5] tick-by-tick trace of one ride: /fhqa sledtrace x z kind uphill(0/1) */
   static int trace(CommandSourceStack src, int x, int z, String kind, boolean uphill) {
      ServerLevel level = src.getServer().overworld();
      try {
         load(level, x, z);
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         Entity e = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:" + kind)).create(level);
         ChunkGenerator gen = level.getChunkSource().getGenerator();
         RandomState rs = level.getChunkSource().randomState();
         double hx = (gen.getBaseHeight(x + 10, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - gen.getBaseHeight(x - 10, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs)) / 20.0;
         double hz = (gen.getBaseHeight(x, z + 10, Heightmap.Types.WORLD_SURFACE_WG, level, rs) - gen.getBaseHeight(x, z - 10, Heightmap.Types.WORLD_SURFACE_WG, level, rs)) / 20.0;
         float yaw = (float)Math.toDegrees(Math.atan2(hx, -hz)) + (uphill ? 180.0F : 0.0F);
         e.moveTo(x + 0.5, y + 0.05, z + 0.5, yaw, 0.0F);
         java.lang.reflect.Field in = e.getClass().getField("qaInput");
         Object phys = e.getClass().getMethod("physics").invoke(e);
         Class<?> pc = phys.getClass();
         for (int t = 0; t < 30; t++) {
            in.set(e, new float[]{1.0F, 0.0F});
            e.tick();
            FrontierQa.say(String.format("TR t%d pos %.2f %.2f %.2f yaw %.0f v(%.3f,%.3f,%.3f) along %.3f thr %.2f drive %.2f ground %s pitch %.2f why '%s'",
               t, e.getX(), e.getY(), e.getZ(), e.getYRot(), pc.getField("vx").getDouble(phys), pc.getField("vy").getDouble(phys), pc.getField("vz").getDouble(phys),
               (double)pc.getMethod("along").invoke(phys), pc.getField("throttle").getFloat(phys), pc.getField("drive").getDouble(phys),
               pc.getField("ground").getBoolean(phys), pc.getField("pitchSlope").getDouble(phys), pc.getField("why").get(phys)));
         }
         FrontierQa.say("TR around " + around(level, e));
         e.discard();
      } catch (Exception ex) {
         FrontierQa.fail("sled trace", ex);
      }
      return 1;
   }
}
