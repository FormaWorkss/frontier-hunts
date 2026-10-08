package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.BloodTrail;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailStore;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [academy] Course 5 · Field Dressing. A ranger camp at the edge of the timber: lean-to, game pole, a fire. A buck
 * lies where it dropped. Read the blood beside it (pink and frothy - a lung shot), then field-dress it with the
 * Contour Skinning Knife (the mod's own skinning, kept in place; in training it yields nothing).
 */
final class DressingKit extends CourseKit {
   private static final double DEER_X = 0.5, DEER_Z = -6.5;

   DressingKit() {
      super(Course.DRESSING);
   }

   static final class State {
      UUID deer;
      UUID pool;
   }

   @Override
   int version() {
      return 1;
   }

   @Override
   float[] arrival() {
      return new float[]{0.5F, 0.0F, 5.5F, 180.0F, 18.0F};
   }

   @Override
   void build(Plot p) {
      Scenery sc = new Scenery(p).keepClear(-5, -11, 5, 9).keepClear(-18, -18, -7, -6).keepClear(5, -16, 13, -8).keepClear(-8, -5, -3, 1);
      BlockState log = Plot.block("pine_log", Blocks.SPRUCE_LOG.defaultBlockState());
      BlockState planks = Plot.block("pine_planks", Blocks.SPRUCE_PLANKS.defaultBlockState());
      BlockState roof = Plot.block("roof_stairs", Blocks.SPRUCE_STAIRS.defaultBlockState());
      BlockState stone = Plot.block("fieldstone", Blocks.COBBLESTONE.defaultBlockState());
      // camp ground
      for (int x = -18; x <= 14; x++) {
         for (int z = -18; z <= 8; z++) {
            double n = Scenery.noise(x * 3, z * 3);
            if (n < 0.5) {
               p.ground(x, z, n < 0.22 ? Blocks.COARSE_DIRT.defaultBlockState() : Plot.block("forest_duff", Blocks.PODZOL.defaultBlockState()));
            }
         }
      }
      // lean-to: log back wall and sides, open to the east, shingle roof sloping west
      for (int z = -16; z <= -8; z++) {
         for (int y = 0; y <= 2; y++) {
            p.put(-17, y, z, Plot.axis(log, Direction.Axis.Z));
         }
      }
      for (int x = -16; x <= -10; x++) {
         for (int y = 0; y <= 1; y++) {
            p.put(x, y, -16, Plot.axis(log, Direction.Axis.X));
            p.put(x, y, -8, Plot.axis(log, Direction.Axis.X));
         }
      }
      for (int z = -16; z <= -8; z++) {
         for (int x = -17; x <= -9; x++) {
            int y = x <= -15 ? 3 : (x <= -12 ? 3 : 2);
            p.put(x, y, z, x == -9 ? Plot.block("roof_slab", Blocks.SPRUCE_SLAB.defaultBlockState()) : (x <= -14
               ? Plot.facing(roof, Direction.EAST).setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM) : planks));
         }
      }
      for (int z = -15; z <= -9; z++) {
         for (int x = -16; x <= -10; x++) {
            p.ground(x, z, planks);
         }
      }
      p.put(-15, 0, -10, Plot.block("stacked_firewood", Blocks.BARREL.defaultBlockState()));
      p.put(-16, 0, -10, Plot.block("stacked_firewood", Blocks.BARREL.defaultBlockState()));
      p.put(-12, 0, -15, Plot.block("lodge_table", Blocks.SPRUCE_SLAB.defaultBlockState()));
      p.put(-11, 1, -15, Plot.block("cabin_lantern", Blocks.LANTERN.defaultBlockState()));
      // fire ring
      int fx = -5, fz = -2;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            p.ground(fx + dx, fz + dz, stone);
         }
      }
      p.put(fx, 0, fz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
      p.put(fx - 2, 0, fz, Plot.block("deadfall_log", Blocks.SPRUCE_LOG.defaultBlockState()));
      p.put(fx + 2, 0, fz + 1, Plot.block("lodge_chair", Blocks.AIR.defaultBlockState()));
      // game pole: two posts and a cross beam
      for (int y = 0; y <= 3; y++) {
         p.put(6, y, -12, log);
         p.put(12, y, -12, log);
      }
      for (int x = 6; x <= 12; x++) {
         p.put(x, 4, -12, Plot.axis(log, Direction.Axis.X));
      }
      p.put(9, 0, -10, Plot.block("stacked_firewood", Blocks.BARREL.defaultBlockState()));
      // timber around the camp
      sc.forestFloor(-22, -26, 22, 14, 0.4F, 0.12F);
      sc.treeBelt(-22, -24, 22, -24, 4, 0.8F);
      sc.treeBelt(-22, -20, -22, 10, 5, 0.75F);
      sc.treeBelt(22, -20, 22, 10, 5, 0.75F);
      for (int i = 0; i < 10; i++) {
         sc.tree(p.rnd(-20, 20), p.rnd(-22, 12), 0.7F);
      }
      for (int i = 0; i < 8; i++) {
         sc.clump(p.rnd(-20, 20), p.rnd(-22, 12), 1);
      }
      p.boundary(-24, 24, -28, 14, 4);
   }

   @Override
   void begin(Session s, ServerLevel level) {
      State st = new State();
      s.state = st;
      Whitetail d = deer(s, level, DEER_X, DEER_Z, 70.0F, false, traits(true, 62, 74, 96, level.random.nextInt(), 52));
      if (d != null) {
         st.deer = d.getUUID();
         down(level, d);
         for (TrailMark m : TrailStore.get(level).nearby(d.position(), 3.0, 64, level.getGameTime())) {
            if (m.animal().equals(d.getUUID())) {
               s.marks.add(m.id());
            }
         }
         int lung = BloodTrail.BloodType.LUNG.ordinal();
         TrailMark pool = blood(s, level, DEER_X - 1.3, DEER_Z + 1.4, TrailMark.POOL, 0.4F, lung, d.getUUID(), describe(d), level.getGameTime() - 600L, 70.0F);
         blood(s, level, DEER_X + 1.8, DEER_Z + 2.6, TrailMark.DENSE, 0.3F, lung, d.getUUID(), describe(d), level.getGameTime() - 640L, 70.0F);
         st.pool = pool == null ? null : pool.id();
      }
      label(s, level, 0.5, 2.0, -9.5, "academy.frontierhunts.sign.dressing", "FIELD DRESSING · knife in hand, use the carcass", 0.5F, 0x99201810,
         "vertical");
   }

   @Override
   void inspected(Session s, ServerLevel level, ServerPlayer p, com.formaworks.frontierhunts.tracking.TrailMark m) {
      if (s.marks.contains(m.id()) && !s.done(0)) {
         s.set(0, 1);
         TrainingService.tick(s, p, 0, "academy.frontierhunts.dressing.read");
      }
   }

   @Override
   void dressed(Session s, ServerLevel level, ServerPlayer p, Whitetail deer) {
      State st = (State)s.state;
      if (st != null && deer.getUUID().equals(st.deer) && !s.done(1)) {
         s.set(1, 1);
         TrainingService.tick(s, p, 1, "academy.frontierhunts.dressing.done", Integer.toString(deer.massKg()));
      }
   }

   @Override
   void tick(Session s, ServerLevel level, ServerPlayer p) {
   }
}
