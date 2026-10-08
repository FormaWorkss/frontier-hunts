package com.formaworks.frontierhunts.livingworld.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * [livingworld] Reusable pieces built from the mod's real blocks: terrain pads, tents (compact and walk-in), blinds,
 * tree stands, camp gear and props. All coordinates are local to the {@link Ctx} frame; y is absolute (first air above
 * the ground the piece stands on).
 */
public final class Kit {
   public static final String FH = "frontierhunts:";
   public static final String LOOT = "frontierhunts:chests/living/";

   private Kit() {
   }

   /** grows trees for the plan: world coords in, world-coordinate blocks out (in game: the mod's tree generator in
    * Frontier biomes, vanilla-style trees elsewhere; offline: {@link #simpleTree}) */
   public interface TreeMaker {
      List<Plan.Block> grow(String kind, long seed, int x, int y, int z, int trunk, String biome);
   }

   public static volatile TreeMaker treeMaker = (kind, seed, x, y, z, trunk, biome) -> simpleTree(x, y, z, kind, trunk, new Rnd(seed));

   /** a north-facing offset turned to face {@code f} (the mod's multi-block convention) */
   public static int[] off(int x, int z, Dir f) {
      return switch (f) {
         case EAST -> new int[]{-z, x};
         case SOUTH -> new int[]{-x, -z};
         case WEST -> new int[]{z, -x};
         default -> new int[]{x, z};
      };
   }

   /** a direction given relative to a north-facing piece, turned with the piece */
   public static Dir rel(Dir northRelative, Dir facing) {
      return northRelative.rot(facing.ordinal());
   }

   public static String f(Dir d) {
      return d.id();
   }

   // ================================================================================================ terrain

   /** exact ground: the column's ground top is set to {@code top} (first air), {@code clear} blocks above emptied */
   public static Plan.Shape ground(Ctx c, int x, int z, int top, int clear) {
      Plan.Shape s = c.shape(x, z, top, 1.0);
      s.clear = Math.max(s.clear, clear);
      s.canopy = Math.max(s.canopy, 36);
      return s;
   }

   public static void groundRect(Ctx c, int x0, int z0, int x1, int z1, int top, int clear) {
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            ground(c, x, z, top, clear);
         }
      }
   }

   /**
    * A level clearing: an ellipse (radii rx, rz) cut/filled exactly to {@code top}, blended into the natural slope over a
    * {@code feather}-block ring. Trees and brush are cleared {@code clear} blocks up inside it; overhanging crowns are
    * trimmed over the inner part of the ring.
    */
   public static void pad(Ctx c, int cx, int cz, double rx, double rz, int top, int feather, int clear) {
      int ex = (int)Math.ceil(rx) + feather + 1;
      int ez = (int)Math.ceil(rz) + feather + 1;
      double r = Math.min(rx, rz);
      for (int x = -ex; x <= ex; x++) {
         for (int z = -ez; z <= ez; z++) {
            double nx = x / Math.max(0.5, rx);
            double nz = z / Math.max(0.5, rz);
            double d = Math.sqrt(nx * nx + nz * nz);
            double out = (d - 1.0) * r;
            double w;
            if (d <= 1.0) {
               w = 1.0;
            } else if (out < feather) {
               double t = 1.0 - out / feather;
               w = t * t * (3 - 2 * t);
            } else {
               continue;
            }
            Plan.Shape s = c.shapeAt(cx + x, cz + z);
            if (s != null && s.weight >= w) {
               continue;
            }
            s = c.shape(cx + x, cz + z, top, w);
            s.clear = (int)Math.round(clear * Math.min(1.0, w * 1.4));
            s.canopy = w > 0.55 ? 36 : 0;
         }
      }
   }

   /** surface dressing of a column inside a pad (coarse dirt, path gravel ...); only where the column is shaped */
   public static void surface(Ctx c, int x, int z, String spec) {
      Plan.Shape s = c.shapeAt(x, z);
      if (s != null && s.weight >= 0.99) {
         s.surface = spec;
      }
   }

   // ================================================================================================ tents

   public static int[][] compactCells(String design) {
      return switch (design) {
         case "backpacker_dome_tent" -> Cells.BACKPACKER_DOME_TENT;
         case "hunters_canvas_tent" -> Cells.HUNTERS_CANVAS_TENT;
         default -> Cells.SOLO_RIDGE_TENT;
      };
   }

   /** compact sleeping tent (backpacker dome, hunter's canvas, solo ridge); origin = front-centre floor cell, door toward f */
   public static void compactTent(Ctx c, String design, int x, int y, int z, Dir f, boolean open) {
      int[][] cells = compactCells(design);
      int x0 = 99, x1 = -99, z0 = 99, z1 = -99, h = 0;
      java.util.Set<Long> used = new java.util.HashSet<>();
      for (int[] cell : cells) {
         x0 = Math.min(x0, cell[0]);
         x1 = Math.max(x1, cell[0]);
         z0 = Math.min(z0, cell[2]);
         z1 = Math.max(z1, cell[2]);
         h = Math.max(h, cell[1]);
         used.add(Plan.key(cell[0], cell[1], cell[2]));
      }
      for (int cx = x0; cx <= x1; cx++) {
         for (int cz = z0; cz <= z1; cz++) {
            int[] o = off(cx, cz, f);
            ground(c, x + o[0], z + o[1], y, h + 2);
            for (int cy = 0; cy <= h; cy++) {
               if (!used.contains(Plan.key(cx, cy, cz))) {
                  c.air(x + o[0], y + cy, z + o[1]);
               }
            }
         }
      }
      for (int i = 0; i < cells.length; i++) {
         int[] o = off(cells[i][0], cells[i][2], f);
         c.set(x + o[0], y + cells[i][1], z + o[1], FH + design + "[facing=" + f.id() + ",open=" + open + ",part=" + i + "]");
      }
   }

   /** the 57 cells of a walk-in camping tent, in PART order (centre origin) */
   public static List<int[]> walkInCells() {
      List<int[]> out = new ArrayList<>();
      for (int y = 0; y < 3; y++) {
         for (int z = -2; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
               if (y == 2 || Math.abs(x) == 2 || Math.abs(z) == 2) {
                  out.add(new int[]{x, y, z});
               }
            }
         }
      }
      return out;
   }

   /**
    * Walk-in tent (woodland camp, trail dome, canvas wall, bell, family cabin, pup): 5 x 5 x 3 shell around a 3 x 3 x 2
    * interior, door toward f. Furnish with {@link #inTent}.
    */
   public static void walkInTent(Ctx c, String design, int x, int y, int z, Dir f) {
      groundRect(c, x - 2, z - 2, x + 2, z + 2, y, 5);
      for (int ix = -1; ix <= 1; ix++) {
         for (int iz = -1; iz <= 1; iz++) {
            for (int iy = 0; iy < 2; iy++) {
               c.air(x + ix, y + iy, z + iz);
            }
         }
      }
      List<int[]> cells = walkInCells();
      for (int i = 0; i < cells.size(); i++) {
         int[] cell = cells.get(i);
         int[] o = off(cell[0], cell[2], f);
         c.set(x + o[0], y + cell[1], z + o[1], FH + design + "[facing=" + f.id() + ",part=" + i + ",ready=true,open=true]");
      }
   }

   /** a cell inside a walk-in tent: ix -1..1 (left..right seen from the door), iz -1 (door side) .. 1 (back wall) */
   public static int[] inTent(int x, int z, Dir f, int ix, int iz) {
      int[] o = off(-ix, iz, f);
      // tent-local north (-z) is the door; mirror x so -1 is the left side when walking in
      return new int[]{x + o[0], z + o[1]};
   }

   // ================================================================================================ hunting setups

   /** Hub ground blind (the big 5 x 5 kind, parts 25..81) with its window side toward f */
   public static void groundBlind(Ctx c, int x, int y, int z, Dir f) {
      int i = 0;
      for (int r = 1; r <= 2; r++) {
         for (int py = 0; py < 3; py++) {
            for (int pz = -r; pz <= r; pz++) {
               for (int px = -r; px <= r; px++) {
                  if (py == 2 || Math.abs(px) == r || Math.abs(pz) == r) {
                     if (r == 2) {
                        int[] o = off(px, pz, f);
                        c.set(x + o[0], y + py, z + o[1], FH + "hub_ground_blind[facing=" + f.id() + ",open=true,part=" + i + "]");
                     }
                     i++;
                  }
               }
            }
         }
      }
      groundRect(c, x - 2, z - 2, x + 2, z + 2, y, 4);
      for (int ix = -1; ix <= 1; ix++) {
         for (int iz = -1; iz <= 1; iz++) {
            c.air(x + ix, y, z + iz);
            c.air(x + ix, y + 1, z + iz);
         }
      }
   }

   /** Tower blind (elevated box blind with stairs), front toward f; feet need ground at y - 1 */
   public static void towerBlind(Ctx c, int x, int y, int z, Dir f) {
      int[][] p = Cells.TOWER_BLIND;
      java.util.Set<Long> used = new java.util.HashSet<>();
      int x0 = 99, x1 = -99, z0 = 99, z1 = -99, h = 0;
      for (int i = 68; i < p.length; i++) {
         used.add(Plan.key(p[i][0], p[i][1], p[i][2]));
         x0 = Math.min(x0, p[i][0]);
         x1 = Math.max(x1, p[i][0]);
         z0 = Math.min(z0, p[i][2]);
         z1 = Math.max(z1, p[i][2]);
         h = Math.max(h, p[i][1]);
      }
      for (int cx = x0; cx <= x1; cx++) {
         for (int cz = z0; cz <= z1; cz++) {
            int[] o = off(cx, cz, f);
            ground(c, x + o[0], z + o[1], y, h + 2);
         }
      }
      for (int i = 68; i < p.length; i++) {
         int[] o = off(p[i][0], p[i][2], f);
         c.set(x + o[0], y + p[i][1], z + o[1], FH + "tower_blind[facing=" + f.id() + ",open=false,part=" + i + "]");
      }
   }

   static int[] standParts(int h, int seats) {
      List<Integer> out = new ArrayList<>();
      for (int i = 0; i < h - 1; i++) {
         out.add(i < 3 ? i : i + 5);
      }
      out.add(3);
      for (int i = 4; i < 8; i++) {
         out.add(i);
      }
      if (seats == 2) {
         for (int i = 11; i < 19; i++) {
            out.add(i);
         }
      }
      return out.stream().mapToInt(Integer::intValue).toArray();
   }

   static int[] standOffset(int part, int h) {
      int x = 0;
      int y;
      int z;
      if (part < 4) {
         y = part == 3 ? h - 1 : part;
         z = 0;
      } else if (part < 8) {
         y = h - 2 + part - 4;
         z = 1;
      } else if (part < 11) {
         y = part - 5;
         z = 0;
      } else {
         x = part < 15 ? -1 : 1;
         y = h - 2 + (part - 11) % 4;
         z = 1;
      }
      return new int[]{x, y, z};
   }

   /**
    * A hang-on tree stand facing f (the hunter looks toward f) on a tree whose trunk is 2 blocks behind the ladder;
    * plants that tree. Returns the trunk position.
    */
   public static int[] treeStand(Ctx c, int x, int y, int z, Dir f, int h, int seats, String treeKind) {
      Dir back = f.opposite();
      int tx = x + back.dx * 2;
      int tz = z + back.dz * 2;
      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz <= 10) {
               Plan.Shape s = c.shapeAt(tx + dx, tz + dz);
               if (s == null) {
                  s = c.shape(tx + dx, tz + dz, y, Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? 1.0 : 0.6);
               }
               s.canopy = Math.max(s.canopy, 30);
               s.clear = Math.max(s.clear, 3);
            }
         }
      }
      ground(c, x, z, y, h + 3);
      ground(c, tx, tz, y, 2);
      int trunk = h + 5 + c.rnd.nextInt(4);
      c.tree(tx, y, tz, treeKind, trunk);
      for (int part : standParts(h, seats)) {
         int[] lo = standOffset(part, h);
         int[] o = off(lo[0], lo[2], f);
         c.set(x + o[0], y + lo[1], z + o[1],
            FH + "mounted_tree_stand[facing=" + f.id() + ",height=" + h + ",open=false,part=" + part + ",seats=" + seats + "]");
      }
      return new int[]{tx, tz};
   }

   /** stand-in tree for the preview and for vanilla biomes (a spruce or birch made of vanilla blocks); local coords */
   public static List<Plan.Block> simpleTree(int x, int y, int z, String kind, int trunk, Rnd r) {
      List<Plan.Block> out = new ArrayList<>();
      boolean birch = kind.contains("aspen") || kind.contains("birch");
      String log = birch ? "minecraft:birch_log[axis=y]" : "minecraft:spruce_log[axis=y]";
      String leaf = birch ? "minecraft:birch_leaves[distance=1,persistent=true,waterlogged=false]"
         : "minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]";
      for (int i = 0; i < trunk; i++) {
         out.add(new Plan.Block(x, y + i, z, log));
      }
      int crownBase = y + (birch ? trunk / 2 : 3);
      int top = y + trunk + 1;
      for (int yy = crownBase; yy <= top; yy++) {
         double t = (double)(yy - crownBase) / Math.max(1, top - crownBase);
         double rad = birch ? 2.4 - Math.abs(t - 0.55) * 2.6 : 3.2 * (1.0 - t) + 0.6;
         if (!birch && (yy - crownBase) % 2 == 1) {
            rad *= 0.7;
         }
         int ri = (int)Math.ceil(rad);
         for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
               double d = Math.sqrt(dx * dx + dz * dz);
               if (d <= rad + 0.1 && !(dx == 0 && dz == 0 && yy < y + trunk) && r.nextDouble() > 0.08) {
                  out.add(new Plan.Block(x + dx, yy, z + dz, leaf));
               }
            }
         }
      }
      return out;
   }

   /** a trail camera strapped to a tree trunk side, lens toward f; dead battery (found gear) */
   public static Plan.Block trailCamera(Ctx c, int x, int y, int z, Dir f) {
      Plan.Block b = c.set(x, y, z, FH + "trail_camera[active=false,facing=" + f.id() + "]");
      b.nbt = "{carried_charge:0}";
      return b;
   }

   // ================================================================================================ camp gear

   public static void campPost(Ctx c, int x, int y, int z, Dir f) {
      c.set(x, y, z, FH + "camp_post[facing=" + f.id() + ",half=lower]");
      c.set(x, y + 1, z, FH + "camp_post[facing=" + f.id() + ",half=upper]");
   }

   /** Big-Buck Board, front toward f, (x, z) = bottom-centre */
   public static void buckBoard(Ctx c, int x, int y, int z, Dir f) {
      Dir side = f.cw();
      for (int p = 0; p < 6; p++) {
         int col = p % 3 - 1;
         int row = p / 3;
         c.set(x + side.dx * col, y + row, z + side.dz * col, FH + "big_buck_board[facing=" + f.id() + ",part=" + p + "]");
      }
      for (int col = -1; col <= 1; col++) {
         ground(c, x + side.dx * col, z + side.dz * col, y, 3);
      }
   }

   /** two-block-wide station (contract board, smokehouse, tanning rack, workbenches ...): left part at (x, z), right part clockwise of it */
   public static void wide(Ctx c, String id, int x, int y, int z, Dir f, String extra) {
      String ex = extra == null || extra.isEmpty() ? "" : "," + extra;
      Dir side = f.cw();
      c.set(x, y, z, FH + id + "[bench_part=left,facing=" + f.id() + ex + "]");
      c.set(x + side.dx, y, z + side.dz, FH + id + "[bench_part=right,facing=" + f.id() + ex + "]");
   }

   /** bed-like two-block piece (camp cot, hide bedroll, vanilla bed): foot at (x, z), head toward {@code head} */
   public static void cot(Ctx c, String id, int x, int y, int z, Dir head) {
      String extra = id.endsWith("_bed") ? ",occupied=false" : id.endsWith("hide_bedroll") || id.endsWith("camp_cot") ? ",occupied=false" : "";
      c.set(x, y, z, id + "[facing=" + head.id() + ",part=foot" + extra + "]");
      c.set(x + head.dx, y, z + head.dz, id + "[facing=" + head.id() + ",part=head" + extra + "]");
   }

   /** meat pole with {@code load} quarters hanging (state 0 fresh, 1 aged, 2 spoiled) */
   public static void gamePole(Ctx c, int x, int y, int z, Dir along, int load, int state) {
      Plan.Block b = c.set(x, y, z, FH + "game_pole[facing=" + along.id() + ",load=" + load + "]");
      if (load > 0) {
         StringBuilder s = new StringBuilder("{hung:[");
         for (int i = 0; i < load; i++) {
            if (i > 0) {
               s.append(',');
            }
            s.append("{at:0L,state:").append(state).append('}');
         }
         b.nbt = s.append("]}").toString();
      }
   }

   /** drying rack with venison strips; stage 2 = finished jerky */
   public static void dryingRack(Ctx c, int x, int y, int z, Dir along, int load, int stage) {
      Plan.Block b = c.set(x, y, z, FH + "drying_rack[facing=" + along.id() + ",load=" + load + ",stage=" + stage + "]");
      if (load > 0) {
         StringBuilder s = new StringBuilder("{strips:[");
         float p = stage == 2 ? 1.0F : stage == 1 ? 0.6F : 0.1F;
         for (int i = 0; i < load; i++) {
            if (i > 0) {
               s.append(',');
            }
            s.append("{item:\"frontierhunts:venison\",progress:").append(p).append("f}");
         }
         b.nbt = s.append("]}").toString();
      }
   }

   /** a split-firewood stack running along {@code along}, length n, 1..2 high (decor only) */
   public static void firewood(Ctx c, int x, int y, int z, Dir along, int n, int high, Rnd r) {
      Dir face = along.cw();
      for (int i = 0; i < n; i++) {
         int px = x + along.dx * i;
         int pz = z + along.dz * i;
         ground(c, px, pz, y, 3);
         c.set(px, y, pz, FH + "stacked_firewood[facing=" + face.id() + "]");
         if (high > 1 && (i > 0 && i < n - 1 || r.chance(0.5))) {
            c.set(px, y + 1, pz, FH + "stacked_firewood[facing=" + face.id() + "]");
         }
      }
   }

   /** fire ring: campfire, a ring of river stones, coarse ground and seats on {@code seats} sides */
   public static void fireRing(Ctx c, int x, int y, int z, boolean lit, int seats, Rnd r, boolean cooking) {
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dz * dz <= 5) {
               ground(c, x + dx, z + dz, y, 4);
               surface(c, x + dx, z + dz, dx * dx + dz * dz <= 2 || r.chance(0.5) ? "minecraft:coarse_dirt" : "minecraft:rooted_dirt");
            }
         }
      }
      Plan.Block fire = c.set(x, y, z, "minecraft:campfire[facing=north,lit=" + lit + ",signal_fire=false,waterlogged=false]");
      if (lit && cooking) {
         fire.nbt = "{Items:[{Slot:0b,id:\"frontierhunts:venison\",count:1},{Slot:2b,id:\"frontierhunts:venison\",count:1}],CookingTimes:[I;0,0,0,0],CookingTotalTimes:[I;600,600,600,600]}";
      }
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
               boolean corner = dx != 0 && dz != 0;
               c.set(x + dx, y, z + dz, FH + (r.chance(0.25) ? "mossy_river_stone" : "river_stone") + "[form=" + (corner ? r.range(0, 1) : r.range(1, 3))
                  + ",waterlogged=false]");
            }
         }
      }
      Dir[] sides = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
      int start = r.nextInt(4);
      for (int i = 0; i < Math.min(4, seats); i++) {
         Dir s = sides[(start + i) & 3];
         int sx = x + s.dx * 3;
         int sz = z + s.dz * 3;
         ground(c, sx, sz, y, 3);
         // [gear20] the mod's camp seats instead of log benches and upright log stumps
         int kind = (start + i) % 4;
         Dir look = s.opposite();
         Dir side = s.cw();
         if (kind == 0) {
            c.set(sx, y, sz, FH + "camp_chair[facing=" + look.id() + "]");
            if (r.chance(0.6)) {
               ground(c, sx + side.dx, sz + side.dz, y, 3);
               c.set(sx + side.dx, y, sz + side.dz, FH + "camp_chair[facing=" + look.id() + "]");
            }
         } else if (kind == 1) {
            trailBench(c, sx, y, sz, look);
         } else if (kind == 2) {
            c.set(sx, y, sz, FH + "log_stump_seat[facing=" + look.id() + "]");
            ground(c, sx + side.dx * 2, sz + side.dz * 2, y, 3);
            c.set(sx + side.dx * 2, y, sz + side.dz * 2, FH + "log_stump_seat[facing=" + look.id() + "]");
         } else {
            c.set(sx, y, sz, FH + "deadfall_log[facing=" + s.cw().id() + "]");
         }
      }
   }

   /** [gear20] a two-seat Trail Bench, sitters looking along {@code look}; (x, z) is the left seat seen from behind */
   public static void trailBench(Ctx c, int x, int y, int z, Dir look) {
      Dir l = look.ccw();
      ground(c, x, z, y, 3);
      ground(c, x + l.dx, z + l.dz, y, 3);
      c.set(x, y, z, FH + "trail_bench[facing=" + look.id() + ",left=true,right=false]");
      c.set(x + l.dx, y, z + l.dz, FH + "trail_bench[facing=" + look.id() + ",left=false,right=true]");
   }

   /** lantern on a post: a fence post with a cabin lantern on it */
   public static void lanternPost(Ctx c, int x, int y, int z, Dir f, String fence) {
      ground(c, x, z, y, 3);
      c.set(x, y, z, fence);
      c.set(x, y + 1, z, FH + "cabin_lantern[facing=" + f.id() + "]");
   }

   public static Plan.Block chest(Ctx c, int x, int y, int z, Dir f, String loot) {
      Plan.Block b = c.set(x, y, z, "minecraft:chest[facing=" + f.id() + ",type=single,waterlogged=false]");
      b.nbt = lootNbt(c, loot, x, y, z);
      return b;
   }

   public static Plan.Block barrel(Ctx c, int x, int y, int z, String facing, String loot) {
      Plan.Block b = c.set(x, y, z, "minecraft:barrel[facing=" + facing + ",open=false]");
      if (loot != null) {
         b.nbt = lootNbt(c, loot, x, y, z);
      }
      return b;
   }

   /** the camp's stores box (the mod's lodge stores chest) with loot */
   public static Plan.Block stores(Ctx c, int x, int y, int z, Dir f, String loot) {
      Plan.Block b = c.set(x, y, z, FH + "lodge_stores[bench_part=single,facing=" + f.id() + "]");
      b.nbt = lootNbt(c, loot, x, y, z);
      return b;
   }

   static String lootNbt(Ctx c, String loot, int x, int y, int z) {
      return "{LootTable:\"" + LOOT + loot + "\",LootTableSeed:" + c.subSeed(Plan.key(x, y, z)) + "L}";
   }

   static int signRotation(Dir front) {
      return switch (front) {
         case SOUTH -> 0;
         case WEST -> 4;
         case NORTH -> 8;
         case EAST -> 12;
      };
   }

   /** standing sign, text side toward {@code front} */
   public static Plan.Block sign(Ctx c, int x, int y, int z, Dir front, String wood, String group, int lines) {
      Plan.Block b = c.set(x, y, z, "minecraft:" + wood + "_sign[rotation=" + signRotation(front) + ",waterlogged=false]");
      b.nbt = Text.sign(group, lines, "black", false);
      return b;
   }

   /** wall sign on the face of a block, text side toward {@code front} (the block behind is at -front) */
   public static Plan.Block wallSign(Ctx c, int x, int y, int z, Dir front, String wood, String group, int lines) {
      Plan.Block b = c.set(x, y, z, "minecraft:" + wood + "_wall_sign[facing=" + front.id() + ",waterlogged=false]");
      b.nbt = Text.sign(group, lines, "black", false);
      return b;
   }

   /** hanging sign under a solid block, text toward {@code front} */
   public static Plan.Block hangingSign(Ctx c, int x, int y, int z, Dir front, String wood, String group, int lines) {
      Plan.Block b = c.set(x, y, z, "minecraft:" + wood + "_hanging_sign[attached=false,rotation=" + signRotation(front) + ",waterlogged=false]");
      b.nbt = Text.sign(group, lines, "black", false);
      return b;
   }

   /** [gear20] a sign post: the mod's Trail Sign with a small standing sign carrying the text beside it (was a fence
    *  post with a log arm and a hanging sign - too bulky) */
   public static void signPost(Ctx c, int x, int y, int z, Dir front, String group, int lines) {
      ground(c, x, z, y, 4);
      c.set(x, y, z, FH + "trail_sign[facing=" + front.id() + "]");
      Dir arm = front.cw();
      ground(c, x + arm.dx, z + arm.dz, y, 3);
      sign(c, x + arm.dx, y, z + arm.dz, front, "spruce", group, lines);
   }

   /** lectern holding a written book */
   public static Plan.Block lectern(Ctx c, int x, int y, int z, Dir f, String bookSnbt) {
      Plan.Block b = c.set(x, y, z, "minecraft:lectern[facing=" + f.id() + ",has_book=true,powered=false]");
      b.nbt = "{Book:" + bookSnbt + ",Page:0}";
      return b;
   }

   // ================================================================================================ entities

   static int facing3d(Dir world) {
      return switch (world) {
         case NORTH -> 2;
         case SOUTH -> 3;
         case WEST -> 4;
         case EAST -> 5;
      };
   }

   /** item frame hanging in the cell (x, y, z) on the wall behind it, showing toward {@code face} */
   public static void itemFrame(Ctx c, int x, int y, int z, Dir face, String itemSnbt) {
      c.entity(x + 0.5, y + 0.5, z + 0.5, 0.0F,
         "{id:\"minecraft:item_frame\",Facing:" + facing3d(c.d(face)) + "b,Item:" + itemSnbt + ",ItemRotation:0b,Invisible:0b,Fixed:0b}");
   }

   /** armour stand dressed as a hunter (blaze-orange vest and cap) */
   public static void hunterStand(Ctx c, int x, int y, int z, Dir face, Rnd r) {
      String orange = "{\"minecraft:dyed_color\":{rgb:16733440}}";
      String chest = r.chance(0.5) ? "{id:\"minecraft:leather_chestplate\",count:1,components:" + orange + "}" : "{id:\"frontierhunts:blaze_camo_coveralls\",count:1}";
      String head = r.chance(0.5) ? "{id:\"minecraft:leather_helmet\",count:1,components:" + orange + "}" : "{id:\"frontierhunts:fur_hat\",count:1}";
      c.entity(x + 0.5, y, z + 0.5, face.yaw(),
         "{id:\"minecraft:armor_stand\",ShowArms:1b,ArmorItems:[{},{},"
            + chest + "," + head + "],Pose:{LeftArm:[-10f,0f,-8f],RightArm:[-12f,0f,8f]}}");
   }

   /**
    * The camp ATV: parked, a little fuel in the tank, a can carrier with a jerry can and a cargo box with a few odds
    * and ends.
    */
   public static void atv(Ctx c, double x, double y, double z, Dir face, Rnd r, boolean full) {
      float fuel = full ? (float)r.range(3.0, 6.0) : (float)r.range(0.4, 2.0);
      float can = r.chance(0.5) ? 0.0F : (float)r.range(1.0, 6.0);
      StringBuilder items = new StringBuilder();
      items.append("{Slot:27b,id:\"frontierhunts:jerry_can\",count:1,components:{\"frontierhunts:fuel_liters\":").append(can).append("f}}");
      String[][] cargo = {{"minecraft:rope", "0"}, {"frontierhunts:bait", "2"}, {"minecraft:lead", "1"}, {"frontierhunts:scent_cover", "2"},
         {"minecraft:bread", "3"}, {"frontierhunts:jerky", "3"}, {"minecraft:torch", "6"}, {"frontierhunts:field_flashlight", "1"}};
      int slot = 0;
      for (String[] it : cargo) {
         if (it[1].equals("0") || !r.chance(0.45)) {
            continue;
         }
         items.append(",{Slot:").append(slot++).append("b,id:\"").append(it[0]).append("\",count:").append(it[1]).append('}');
      }
      c.entity(x, y, z, face.yaw(), "{id:\"frontierhunts:atv\",FhFuel:" + fuel + "f,FhRig:3,Items:[" + items + "]}");
   }

   // ================================================================================================ small props

   /** chopping block: a spruce stump with an axe-split log beside it */
   public static void choppingBlock(Ctx c, int x, int y, int z, Rnd r) {
      ground(c, x, z, y, 3);
      c.set(x, y, z, "minecraft:stripped_spruce_log[axis=y]");
      Dir d = r.pick(Dir.values());
      c.decor(x + d.dx, y, z + d.dz, FH + "forest_sticks[facing=" + d.cw().id() + ",variant=" + r.nextInt(6) + "]");
   }

   /** outhouse: 1 x 1 plank booth with a door toward f and a vent cut-out */
   public static void outhouse(Ctx c, int x, int y, int z, Dir f, String planks) {
      Dir side = f.cw();
      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            ground(c, x + side.dx * a + f.dx * b, z + side.dz * a + f.dz * b, y, 4);
         }
      }
      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            int px = x + side.dx * a + f.dx * b;
            int pz = z + side.dz * a + f.dz * b;
            boolean wall = a != 0 || b != 0;
            if (!wall) {
               c.set(px, y, pz, "minecraft:spruce_trapdoor[facing=" + f.id() + ",half=bottom,open=false,powered=false,waterlogged=false]");
               c.air(px, y + 1, pz);
               continue;
            }
            for (int yy = 0; yy < 2; yy++) {
               c.set(px, y + yy, pz, (a != 0 && b != 0) ? "minecraft:stripped_spruce_log[axis=y]" : planks);
            }
         }
      }
      c.set(x + f.dx, y, z + f.dz, "minecraft:spruce_door[facing=" + f.opposite().id() + ",half=lower,hinge=left,open=false,powered=false]");
      c.set(x + f.dx, y + 1, z + f.dz, "minecraft:spruce_door[facing=" + f.opposite().id() + ",half=upper,hinge=left,open=false,powered=false]");
      c.set(x - f.dx, y + 1, z - f.dz, "minecraft:spruce_trapdoor[facing=" + f.id() + ",half=top,open=true,powered=false,waterlogged=false]");
      for (int a = -1; a <= 1; a++) {
         for (int b = -1; b <= 1; b++) {
            Dir down = f.opposite();
            String stair = b == -1 ? "minecraft:spruce_slab[type=bottom,waterlogged=false]"
               : "minecraft:spruce_stairs[facing=" + down.id() + ",half=bottom,shape=straight,waterlogged=false]";
            c.set(x + side.dx * a + f.dx * b, y + 2, z + side.dz * a + f.dz * b, b == 1 ? "minecraft:spruce_slab[type=bottom,waterlogged=false]" : stair);
         }
      }
   }

   /** hitch rail: two spruce posts with a rail at chest height between them along {@code along} (n blocks of rail),
    *  a hay bale at the far end. [gear20] was a heavy peeled-log beam on log posts */
   public static void hitchRail(Ctx c, int x, int y, int z, Dir along, int n) {
      String a = along.id(), b = along.opposite().id();
      for (int i = 0; i <= n + 1; i++) {
         int px = x + along.dx * i;
         int pz = z + along.dz * i;
         ground(c, px, pz, y, 3);
         String links = (i > 0 ? "," + b + "=true" : "") + (i <= n ? "," + a + "=true" : "");
         if (i == 0 || i == n + 1) {
            c.set(px, y, pz, "minecraft:spruce_fence");
         }
         c.set(px, y + 1, pz, "minecraft:spruce_fence[" + links.substring(1) + "]");
      }
      int hx = x + along.dx * (n + 2), hz = z + along.dz * (n + 2);
      ground(c, hx, hz, y, 3);
      c.set(hx, y, hz, "minecraft:hay_block[axis=y]");
   }

   /** a flag pole with a banner on top */
   public static void flagPole(Ctx c, int x, int y, int z, Dir face, String bannerColor, int height) {
      ground(c, x, z, y, height + 2);
      c.set(x, y, z, "minecraft:cobblestone_wall");
      for (int i = 1; i < height; i++) {
         c.set(x, y + i, z, "minecraft:spruce_fence");
      }
      c.set(x + face.dx, y + height - 1, z + face.dz, "minecraft:" + bannerColor + "_wall_banner[facing=" + face.id() + "]");
   }

   /** a storm-flattened tent: sagging canvas folds over the ground, a snapped pole, a guy-line stake */
   public static void collapsedTent(Ctx c, int x, int y, int z, Dir f, Rnd r) {
      Dir side = f.cw();
      for (int a = -1; a <= 1; a++) {
         for (int b = 0; b <= 3; b++) {
            int px = x + side.dx * a + f.dx * b;
            int pz = z + side.dz * a + f.dz * b;
            ground(c, px, pz, y, 3);
            if (b == 3 && a != 0 && r.chance(0.5)) {
               continue;
            }
            String piece = a == 0 ? (r.chance(0.6) ? "tent_ridge" : "tent_slope") : "tent_slope";
            Dir fold = a == 0 ? (r.chance(0.5) ? f : f.opposite()) : (a < 0 ? side : side.opposite());
            c.set(px, y, pz, FH + piece + "[facing=" + fold.id() + "]");
         }
      }
      // the snapped centre pole still standing at the front, a peg out to the side
      c.set(x - f.dx, y, z - f.dz, "minecraft:spruce_fence");
      ground(c, x - f.dx, z - f.dz, y, 3);
      c.drop(x - f.dx + side.dx * 2, y, z - f.dz + side.dz * 2, FH + "forest_sticks[facing=" + f.id() + ",variant=" + r.nextInt(6) + "]");
   }

   /** grass, ferns and brush re-taking a clearing (single-column decor, dropped onto the live ground) */
   public static void overgrowth(Ctx c, int cx, int cz, double rx, double rz, double density, Rnd r, Layout l) {
      for (int x = (int)-Math.ceil(rx); x <= Math.ceil(rx); x++) {
         for (int z = (int)-Math.ceil(rz); z <= Math.ceil(rz); z++) {
            if ((x / rx) * (x / rx) + (z / rz) * (z / rz) > 1.0 || l != null && l.taken(cx + x, cz + z) || !r.chance(density)) {
               continue;
            }
            int y = c.floor(cx + x, cz + z);
            double p = r.nextDouble();
            String s = p < 0.45 ? "minecraft:short_grass" : p < 0.7 ? "minecraft:fern" : p < 0.82 ? FH + "spreading_fern[size=" + r.nextInt(3) + "]"
               : p < 0.92 ? FH + "woodland_bush[size=" + r.nextInt(2) + "]" : "minecraft:sweet_berry_bush[age=" + r.range(1, 3) + "]";
            c.drop(cx + x, y, cz + z, s);
         }
      }
   }

   /** a fallen tree lying across the ground along {@code along}: a horizontal trunk with a root plate and dead branches */
   public static void fallenTree(Ctx c, int x, int y, int z, Dir along, int len, Rnd r, String wood) {
      String axis = along.dx != 0 ? "x" : "z";
      for (int i = 0; i < len; i++) {
         int px = x + along.dx * i;
         int pz = z + along.dz * i;
         c.drop(px, c.floor(px, pz), pz, "minecraft:" + wood + "_log[axis=" + axis + "]");
         if (i > len / 2 && r.chance(0.4)) {
            Dir s = r.chance(0.5) ? along.cw() : along.ccw();
            c.drop(px + s.dx, c.floor(px + s.dx, pz + s.dz), pz + s.dz, FH + "fallen_branch[facing=" + along.id() + ",snapped=true]");
         }
      }
      // root plate at the base
      Dir back = along.opposite();
      int rx = x + back.dx;
      int rz = z + back.dz;
      int ry = c.floor(rx, rz);
      c.drop(rx, ry, rz, FH + "mossy_stone");
   }

   /** a kitchen fly: a canvas tarp on four poles (w x d footprint, ridge along {@code along}) */
   public static void kitchenFly(Ctx c, int x, int y, int z, Dir along, int len, Rnd r) {
      Dir side = along.cw();
      for (int i = 0; i < len; i++) {
         for (int a = -1; a <= 1; a++) {
            int px = x + along.dx * i + side.dx * a;
            int pz = z + along.dz * i + side.dz * a;
            ground(c, px, pz, y, 4);
            boolean corner = (i == 0 || i == len - 1) && a != 0;
            if (corner) {
               c.set(px, y, pz, "minecraft:spruce_fence");
               c.set(px, y + 1, pz, "minecraft:spruce_fence");
            }
            String piece = a == 0 ? "tent_ridge[facing=" + side.id() + "]" : "tent_slope[facing=" + (a < 0 ? side.id() : side.opposite().id()) + "]";
            c.set(px, y + 2, pz, FH + piece);
         }
      }
   }

   /** a hunter's scale: two posts and a beam with a hanging chain and an iron cradle (game check / weigh station) */
   public static void weighScale(Ctx c, int x, int y, int z, Dir along) {
      for (int i = -1; i <= 1; i++) {
         ground(c, x + along.dx * i, z + along.dz * i, y, 5);
      }
      for (int h = 0; h < 3; h++) {
         c.set(x - along.dx, y + h, z - along.dz, "minecraft:stripped_spruce_log[axis=y]");
         c.set(x + along.dx, y + h, z + along.dz, "minecraft:stripped_spruce_log[axis=y]");
      }
      c.set(x, y + 3, z, "minecraft:stripped_spruce_log[axis=" + (along.dx != 0 ? "x" : "z") + "]");
      c.set(x - along.dx, y + 3, z - along.dz, "minecraft:stripped_spruce_log[axis=" + (along.dx != 0 ? "x" : "z") + "]");
      c.set(x + along.dx, y + 3, z + along.dz, "minecraft:stripped_spruce_log[axis=" + (along.dx != 0 ? "x" : "z") + "]");
      c.set(x, y + 2, z, "minecraft:chain[axis=y,waterlogged=false]");
      c.set(x, y + 1, z, "minecraft:iron_trapdoor[facing=" + along.id() + ",half=top,open=false,powered=false,waterlogged=false]");
   }

   /**
    * Food plot: a worked strip (rows of oats, turnips and carrots on farmland, a clover border of petals and grass),
    * x0..x1 by z0..z1 in local coordinates, ground at y (first air).
    */
   public static void foodPlot(Ctx c, int x0, int z0, int x1, int z1, int y, Rnd r) {
      String[] crops = {"minecraft:wheat[age=7]", "minecraft:beetroots[age=3]", "minecraft:carrots[age=7]", "minecraft:wheat[age=6]"};
      boolean alongX = Math.abs(x1 - x0) >= Math.abs(z1 - z0);
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            Plan.Shape s = ground(c, x, z, y, 6);
            boolean border = x == x0 || x == x1 || z == z0 || z == z1;
            if (border) {
               if (r.chance(0.55)) {
                  c.set(x, y, z, "minecraft:pink_petals[facing=" + r.pick(Dir.values()).id() + ",flower_amount=" + r.range(2, 4) + "]");
               } else if (r.chance(0.5)) {
                  c.set(x, y, z, "minecraft:short_grass");
               }
               continue;
            }
            int row = alongX ? z - Math.min(z0, z1) : x - Math.min(x0, x1);
            s.surface = "minecraft:farmland[moisture=7]";
            if (r.chance(0.9)) {
               c.set(x, y, z, crops[(row / 2) % crops.length]);
            }
         }
      }
   }

   /** a log post with a trail camera strapped to its face (lens toward f), height 2 */
   public static void cameraPost(Ctx c, int x, int y, int z, Dir f) {
      ground(c, x, z, y, 3);
      c.set(x, y, z, "minecraft:stripped_spruce_log[axis=y]");
      c.set(x, y + 1, z, "minecraft:stripped_spruce_log[axis=y]");
      trailCamera(c, x + f.dx, y + 1, z + f.dz, f);
   }

   /** a tripod feeder: a barrel hopper on a post over a spill of grain */
   public static void feeder(Ctx c, int x, int y, int z) {
      ground(c, x, z, y, 4);
      c.set(x, y, z, "minecraft:spruce_fence");
      c.set(x, y + 1, z, "minecraft:hopper[enabled=false,facing=down]");
      c.set(x, y + 2, z, "minecraft:barrel[facing=up,open=false]");
      for (Dir d : Dir.values()) {
         c.set(x + d.dx, y, z + d.dz, "minecraft:spruce_fence", Plan.IF_FREE | Plan.SUPPORTED);
      }
   }
}

