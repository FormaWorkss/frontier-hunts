package com.formaworks.frontierhunts.livingworld.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [livingworld] A structure as a list of world-coordinate operations: terrain shaping per column, blocks (with optional
 * block-entity data), trees and entities. Built by a {@link Kind} through a {@link Ctx}; executed by {@link Executor}.
 */
public final class Plan {
   /** block placement flags */
   public static final int REPLACE = 0;
   /** only into air / plants (decor that must not cut into terrain or other blocks) */
   public static final int IF_FREE = 1;
   /** y is a hint: drop onto the live ground surface of the column (single-column decor such as fences, bones) */
   public static final int ON_GROUND = 2;
   /** only if the block below is solid ground or a plan block (no floating decor) */
   public static final int SUPPORTED = 4;

   public static final class Block {
      public final int x, y, z;
      public final String spec;
      public final int flags;
      public String nbt;

      Block(int x, int y, int z, String spec, int flags) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.spec = spec;
         this.flags = flags;
      }

      public Block(int x, int y, int z, String spec) {
         this(x, y, z, spec, REPLACE);
      }
   }

   /**
    * Terrain shaping of one column: the ground top is moved toward {@code top} (first air y) by {@code weight} (1 = exactly,
    * 0 = untouched; a feather ring blends into the natural slope). {@code fill} replaces soil when raising (null = copy the
    * natural subsoil look: dirt), {@code surface} overrides the top block (null = keep the natural top block),
    * {@code clear} blocks above the new ground are emptied of everything that is not water, and logs/leaves are removed
    * up to {@code canopy} above it (trees standing on the pad).
    */
   public static final class Shape {
      public final int x, z;
      public int top;
      public double weight;
      public String fill;
      public String surface;
      public int clear;
      public int canopy;
      public int maxFill = 14;
      public int maxCut = 10;
      /** leave water columns alone */
      public boolean dryOnly = true;

      Shape(int x, int z) {
         this.x = x;
         this.z = z;
      }
   }

   public static final class Entity {
      public final double x, y, z;
      public final float yaw;
      public final String snbt;

      Entity(double x, double y, double z, float yaw, String snbt) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.yaw = yaw;
         this.snbt = snbt;
      }
   }

   private final Map<Long, Shape> shapes = new LinkedHashMap<>();
   private final Map<Long, Block> blocks = new LinkedHashMap<>();
   private final java.util.Set<Long> columns = new java.util.HashSet<>();
   private final List<Entity> entities = new ArrayList<>();
   public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
   public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
   /** world position of the structure's centre (for /locate-style reports and the piece box centre) */
   public int cx, cy, cz;
   public String title = "";

   static long key(int x, int y, int z) {
      return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
   }

   static long col(int x, int z) {
      return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
   }

   public void grow(int x, int y, int z) {
      this.minX = Math.min(this.minX, x);
      this.maxX = Math.max(this.maxX, x);
      this.minY = Math.min(this.minY, y);
      this.maxY = Math.max(this.maxY, y);
      this.minZ = Math.min(this.minZ, z);
      this.maxZ = Math.max(this.maxZ, z);
   }

   public Block block(int x, int y, int z, String spec, int flags) {
      Block b = new Block(x, y, z, spec, flags);
      long k = key(x, y, z);
      this.blocks.remove(k);
      this.blocks.put(k, b);
      if ((flags & ON_GROUND) == 0) {
         this.columns.add(col(x, z));
      }
      this.grow(x, y, z);
      return b;
   }

   /** true when the plan builds something (other than dropped decor) in this column */
   public boolean usedColumn(int x, int z) {
      return this.columns.contains(col(x, z));
   }

   public Block blockAt(int x, int y, int z) {
      return this.blocks.get(key(x, y, z));
   }

   public void remove(int x, int y, int z) {
      this.blocks.remove(key(x, y, z));
   }

   /** shaping of a column; a later call for the same column replaces weaker shaping (higher weight wins) */
   public Shape shape(int x, int z, int top, double weight) {
      long k = col(x, z);
      Shape s = this.shapes.get(k);
      if (s == null) {
         s = new Shape(x, z);
         s.top = top;
         s.weight = weight;
         this.shapes.put(k, s);
      } else if (weight >= s.weight) {
         s.top = top;
         s.weight = weight;
      }
      this.grow(x, top - Math.max(s.maxFill, 2) - 1, z);
      this.grow(x, top + 2, z);
      return s;
   }

   public Shape shapeAt(int x, int z) {
      return this.shapes.get(col(x, z));
   }

   public void entity(double x, double y, double z, float yaw, String snbt) {
      this.entities.add(new Entity(x, y, z, yaw, snbt));
      this.grow((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
   }

   public java.util.Collection<Shape> shapes() {
      return this.shapes.values();
   }

   public java.util.Collection<Block> blocks() {
      return this.blocks.values();
   }

   public List<Entity> entities() {
      return this.entities;
   }

   public int count() {
      return this.blocks.size();
   }

   /** [structures2] material pass: f returns the new spec for a block (or the same spec); block-entity data is kept */
   public void restyle(java.util.function.Function<Block, String> f) {
      Map<Long, Block> updates = new HashMap<>();
      for (Block b : this.blocks.values()) {
         String n = f.apply(b);
         if (n != null && !n.equals(b.spec)) {
            Block nb = new Block(b.x, b.y, b.z, n, b.flags);
            nb.nbt = b.nbt;
            updates.put(key(b.x, b.y, b.z), nb);
         }
      }
      this.blocks.putAll(updates);
   }

   // ------------------------------------------------------------------------------------------------ fence connections

   private static final Set<String> SOLID_HINTS = Set.of("_planks", "_log", "_wood", "cobblestone", "stone_bricks", "_bricks", "fieldstone", "reserve_granite");

   private static boolean fenceLike(String id) {
      return id.endsWith("_fence") || id.endsWith("_wall") || id.endsWith("_pane") || id.equals("minecraft:iron_bars");
   }

   private static boolean connects(String self, Block other, Dir towards) {
      if (other == null) {
         return false;
      }
      String id = Spec.id(other.spec);
      if (id.endsWith("_fence_gate")) {
         String f = Spec.get(other.spec, "facing");
         Dir d = f == null ? null : Dir.of(f);
         // a gate connects along its wall, i.e. perpendicular to its facing
         return d != null && d != towards && d != towards.opposite();
      }
      if (fenceLike(id)) {
         boolean selfWood = self.endsWith("_fence");
         boolean otherWood = id.endsWith("_fence");
         return selfWood == otherWood || !selfWood && !otherWood;
      }
      for (String h : SOLID_HINTS) {
         if (id.endsWith(h) || id.contains(h)) {
            return !id.contains("stairs") && !id.contains("slab");
         }
      }
      return false;
   }

   /** sets north/east/south/west on fences, walls, panes and bars from the plan's own neighbours */
   public void connect() {
      Map<Long, Block> updates = new HashMap<>();
      for (Block b : this.blocks.values()) {
         String id = Spec.id(b.spec);
         if (!fenceLike(id)) {
            continue;
         }
         boolean wall = id.endsWith("_wall");
         Map<String, String> p = Spec.props(b.spec);
         for (Dir d : Dir.values()) {
            boolean c = connects(id, this.blocks.get(key(b.x + d.dx, b.y, b.z + d.dz)), d);
            p.put(d.id(), wall ? (c ? "low" : "none") : Boolean.toString(c));
         }
         if (wall) {
            p.put("up", "true");
         }
         if (!p.containsKey("waterlogged")) {
            p.put("waterlogged", "false");
         }
         Block nb = new Block(b.x, b.y, b.z, Spec.of(id, p), b.flags);
         nb.nbt = b.nbt;
         updates.put(key(b.x, b.y, b.z), nb);
      }
      this.blocks.putAll(updates);
   }
}
