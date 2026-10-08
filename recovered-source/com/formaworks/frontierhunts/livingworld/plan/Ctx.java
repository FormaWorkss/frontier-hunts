package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [livingworld] Builder context: a local frame (origin + quarter-turn rotation) over the {@link Plan}. Builders work in
 * local coordinates (x right, z "south" = toward the front of the site when rot = 0) and world heights; everything is
 * converted to world coordinates (positions, facing properties, entity yaw) on the way into the plan.
 */
public final class Ctx {
   public final Plan plan = new Plan();
   public final Terrain terrain;
   public final Rnd rnd;
   public final long seed;
   public final int ox;
   public final int oz;
   public final int rot;
   public final int variant;
   public final String kind;

   public Ctx(String kind, Terrain terrain, long seed, int ox, int oz, int rot, int variant) {
      this.kind = kind;
      this.terrain = terrain;
      this.seed = seed;
      this.rnd = new Rnd(seed);
      this.ox = ox;
      this.oz = oz;
      this.rot = rot & 3;
      this.variant = variant;
   }

   // ------------------------------------------------------------------------------------------------ frame

   public int wx(int lx, int lz) {
      return switch (this.rot) {
         case 1 -> this.ox - lz;
         case 2 -> this.ox - lx;
         case 3 -> this.ox + lz;
         default -> this.ox + lx;
      };
   }

   public int wz(int lx, int lz) {
      return switch (this.rot) {
         case 1 -> this.oz + lx;
         case 2 -> this.oz - lz;
         case 3 -> this.oz - lx;
         default -> this.oz + lz;
      };
   }

   /** continuous local point -> world (cell centres map to cell centres) */
   public double wxd(double px, double pz) {
      return switch (this.rot) {
         case 1 -> this.ox + 1 - pz;
         case 2 -> this.ox + 1 - px;
         case 3 -> this.ox + pz;
         default -> this.ox + px;
      };
   }

   public double wzd(double px, double pz) {
      return switch (this.rot) {
         case 1 -> this.oz + px;
         case 2 -> this.oz + 1 - pz;
         case 3 -> this.oz + 1 - px;
         default -> this.oz + pz;
      };
   }

   public Dir d(Dir local) {
      return local.rot(this.rot);
   }

   // ------------------------------------------------------------------------------------------------ terrain

   public int floor(int lx, int lz) {
      return this.terrain.floor(this.wx(lx, lz), this.wz(lx, lz));
   }

   public int surface(int lx, int lz) {
      return this.terrain.surface(this.wx(lx, lz), this.wz(lx, lz));
   }

   public boolean wet(int lx, int lz) {
      int x = this.wx(lx, lz);
      int z = this.wz(lx, lz);
      return this.terrain.surface(x, z) > this.terrain.floor(x, z);
   }

   public String biome(int lx, int lz) {
      return this.terrain.biome(this.wx(lx, lz), this.wz(lx, lz));
   }

   /** height statistics over a local rectangle sampled every {@code step} blocks: {min, max, median, wetCount} */
   public int[] survey(int x0, int z0, int x1, int z1, int step) {
      java.util.List<Integer> hs = new java.util.ArrayList<>();
      int wet = 0;
      for (int x = x0; x <= x1; x += step) {
         for (int z = z0; z <= z1; z += step) {
            hs.add(this.floor(x, z));
            if (this.wet(x, z)) {
               wet++;
            }
         }
      }
      java.util.Collections.sort(hs);
      return new int[]{hs.get(0), hs.get(hs.size() - 1), hs.get(hs.size() / 2), wet};
   }

   // ------------------------------------------------------------------------------------------------ writes

   public Plan.Block set(int lx, int y, int lz, String spec) {
      return this.set(lx, y, lz, spec, Plan.REPLACE);
   }

   public Plan.Block set(int lx, int y, int lz, String spec, int flags) {
      return this.plan.block(this.wx(lx, lz), y, this.wz(lx, lz), Spec.rotate(spec, this.rot), flags);
   }

   public Plan.Block at(int lx, int y, int lz) {
      return this.plan.blockAt(this.wx(lx, lz), y, this.wz(lx, lz));
   }

   public boolean used(int lx, int lz) {
      return this.plan.usedColumn(this.wx(lx, lz), this.wz(lx, lz));
   }

   /** single-column decor dropped onto the live ground, never into a column the plan builds in */
   public Plan.Block drop(int lx, int yHint, int lz, String spec) {
      if (this.used(lx, lz)) {
         return null;
      }
      return this.set(lx, yHint, lz, spec, Plan.ON_GROUND);
   }

   public void air(int lx, int y, int lz) {
      this.set(lx, y, lz, "minecraft:air");
   }

   /** keep only if the cell is still free in the plan (does not overwrite plan blocks) */
   public Plan.Block decor(int lx, int y, int lz, String spec) {
      Plan.Block b = this.at(lx, y, lz);
      if (b != null && !Spec.id(b.spec).equals("minecraft:air")) {
         return null;
      }
      return this.set(lx, y, lz, spec, Plan.IF_FREE | Plan.SUPPORTED);
   }

   public Plan.Shape shape(int lx, int lz, int top, double weight) {
      return this.plan.shape(this.wx(lx, lz), this.wz(lx, lz), top, weight);
   }

   public Plan.Shape shapeAt(int lx, int lz) {
      return this.plan.shapeAt(this.wx(lx, lz), this.wz(lx, lz));
   }

   public void entity(double lx, double y, double lz, float localYaw, String snbt) {
      this.plan.entity(this.wxd(lx, lz), y, this.wzd(lx, lz), localYaw + 90.0F * this.rot, snbt);
   }

   /** plants a tree whose trunk stands at (lx, y, lz); the trunk is forced, the crown only fills free space */
   public void tree(int lx, int y, int lz, String kind, int trunk) {
      int x = this.wx(lx, lz);
      int z = this.wz(lx, lz);
      long s = Rnd.mix(this.seed, Plan.col(lx, lz) ^ kind.hashCode());
      for (Plan.Block b : Kit.treeMaker.grow(kind, s, x, y, z, trunk, this.terrain.biome(x, z))) {
         boolean trunkCell = b.x == x && b.z == z && b.y >= y && b.y < y + trunk;
         if (trunkCell || this.plan.blockAt(b.x, b.y, b.z) == null) {
            this.plan.block(b.x, b.y, b.z, b.spec, trunkCell ? Plan.REPLACE : Plan.IF_FREE);
         }
      }
      this.plan.grow(x - 6, y, z - 6);
      this.plan.grow(x + 6, y + trunk + 6, z + 6);
   }

   public long subSeed(long salt) {
      return Rnd.mix(this.seed, salt);
   }
}
