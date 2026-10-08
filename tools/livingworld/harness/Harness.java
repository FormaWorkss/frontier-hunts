import com.formaworks.frontierhunts.livingworld.plan.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * [livingworld] Offline harness: builds every site kind / variant over synthetic terrains (flat, slope, forest, hill,
 * lake shore, ridge), executes the plans chunk by chunk in shuffled order into a voxel world, checks that the result is
 * identical to a single-box run (determinism), checks for floating / buried pieces, and dumps visible voxels as JSON for
 * tools/livingworld/render.py.
 *   java Harness <outdir> [kind] [variant] [terrain] [seed]
 */
public class Harness {
   static final Set<String> GROUNDS = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:rooted_dirt",
      "minecraft:stone", "minecraft:andesite", "minecraft:gravel", "minecraft:sand", "minecraft:cobblestone", "frontierhunts:fieldstone", "minecraft:mud",
      "minecraft:snow_block", "minecraft:clay");

   static class Synth implements Terrain {
      final String type;
      final long seed;
      final int base = 72;
      Synth(String type, long seed) {
         this.type = type;
         this.seed = seed;
      }
      double noise(double x, double z, double f) {
         return Math.sin(x * f + this.seed % 7) * Math.cos(z * f * 1.3 + this.seed % 5) + 0.5 * Math.sin((x + z) * f * 2.1 + 1.7);
      }
      double h(int x, int z) {
         double n = this.noise(x, z, 0.09);
         return switch (this.type) {
            case "slope" -> this.base + x * 0.22 + z * 0.08 + n * 1.2;
            case "hill" -> this.base + n * 3.2 + this.noise(x, z, 0.21) * 1.5;
            case "ridge" -> this.base + 14 - Math.sqrt(x * x + z * z) * 0.35 + n * 1.0;
            case "lake" -> this.base + n * 0.8 + (z > 4 ? -Math.min(4.0, (z - 4) * 0.6) : 0.0);
            default -> this.base + n * 1.1;
         };
      }
      boolean pond(int x, int z) {
         return this.type.equals("lake") && z > 6;
      }
      public int floor(int x, int z) {
         return (int)Math.floor(this.h(x, z)) + 1;
      }
      public int surface(int x, int z) {
         return this.pond(x, z) ? Math.max(this.floor(x, z), this.base) : this.floor(x, z);
      }
   }

   static class Vox implements World {
      final Map<Long, String> m = new HashMap<>();
      final Synth t;
      final Set<Long> gen = new HashSet<>();
      final List<String> errors = new ArrayList<>();
      final List<double[]> ents = new ArrayList<>();
      final List<String> entIds = new ArrayList<>();
      Vox(Synth t) {
         this.t = t;
      }
      static long k(int x, int y, int z) {
         return ((long)(x & 0x3FFFFFF) << 38) | ((long)(z & 0x3FFFFFF) << 12) | (y & 0xFFF);
      }
      void column(int x, int z) {
         long ck = ((long)x << 32) ^ (z & 0xFFFFFFFFL);
         if (!this.gen.add(ck)) {
            return;
         }
         int f = this.t.floor(x, z);
         for (int y = f - 8; y < f; y++) {
            this.m.put(k(x, y, z), y == f - 1 ? (this.t.pond(x, z) && f <= this.t.base ? "minecraft:mud" : "minecraft:grass_block[snowy=false]") : y < f - 4 ? "minecraft:stone" : "minecraft:dirt");
         }
         if (this.t.pond(x, z)) {
            for (int y = f; y < this.t.surface(x, z); y++) {
               this.m.put(k(x, y, z), "minecraft:water[level=0]");
            }
         }
      }
      String get(int x, int y, int z) {
         this.column(x, z);
         int f = this.t.floor(x, z);
         String s = this.m.get(k(x, y, z));
         if (s == null && y < f - 8) {
            return "minecraft:stone";
         }
         return s == null ? "minecraft:air" : s;
      }
      public int kind(int x, int y, int z) {
         String id = Spec.id(this.get(x, y, z));
         if (id.equals("minecraft:air")) return AIR;
         if (id.equals("minecraft:water")) return WATER;
         if (GROUNDS.contains(id)) return GROUND;
         if (id.endsWith("_log") || id.endsWith("_wood")) return LOG;
         if (id.contains("leaves")) return LEAVES;
         if (id.equals("minecraft:short_grass") || id.equals("minecraft:fern") || id.equals("minecraft:tall_grass") || id.contains("forest_sticks")
            || id.contains("pebbles") || id.contains("fallen_branch") || id.contains("spreading_fern") || id.equals("minecraft:dandelion")) return PLANT;
         return OTHER;
      }
      public void set(int x, int y, int z, String spec) {
         this.column(x, z);
         if (Spec.id(spec).equals("minecraft:air")) {
            this.m.remove(k(x, y, z));
         } else {
            this.m.put(k(x, y, z), spec);
         }
      }
      public void copy(int x, int fromY, int z, int toY) {
         this.set(x, toY, z, this.get(x, fromY, z));
      }
      public void setAir(int x, int y, int z) {
         this.set(x, y, z, "minecraft:air");
      }
      public void nbt(int x, int y, int z, String snbt) {
         if (Spec.id(this.get(x, y, z)).equals("minecraft:air")) {
            this.errors.add("nbt on air at " + x + "," + y + "," + z);
         }
      }
      public void entity(double x, double y, double z, float yaw, String snbt) {
         this.ents.add(new double[]{x, y, z, yaw});
         int i = snbt.indexOf("id:\"");
         this.entIds.add(snbt.substring(i + 4, snbt.indexOf('"', i + 4)));
      }
      public int minY() {
         return -64;
      }
      public int maxY() {
         return 320;
      }
      /** natural trees around, so clearing and trimming can be seen */
      void forest(int x0, int z0, int x1, int z1, double density, long seed) {
         Rnd r = new Rnd(seed);
         for (int x = x0; x <= x1; x += 1) {
            for (int z = z0; z <= z1; z += 1) {
               if (r.nextDouble() < density && !this.t.pond(x, z)) {
                  int f = this.t.floor(x, z);
                  int h = r.range(7, 12);
                  for (Plan.Block b : Kit.simpleTree(x, f, z, r.chance(0.3) ? "aspen" : "pine", h, r)) {
                     if (this.kind(b.x, b.y, b.z) == AIR || this.kind(b.x, b.y, b.z) == LEAVES) {
                        this.set(b.x, b.y, b.z, b.spec);
                     }
                  }
               } else if (r.nextDouble() < 0.12 && !this.t.pond(x, z)) {
                  int f = this.t.floor(x, z);
                  if (this.kind(x, f, z) == AIR) {
                     this.set(x, f, z, r.chance(0.7) ? "minecraft:short_grass" : "minecraft:fern");
                  }
               }
            }
         }
      }
   }

   static Kind[] kinds() {
      return Kinds.ALL.values().toArray(new Kind[0]);
   }

   public static void main(String[] a) throws Exception {
      Path out = Paths.get(a[0]);
      Files.createDirectories(out);
      String only = a.length > 1 ? a[1] : null;
      String onlyVar = a.length > 2 ? a[2] : null;
      String onlyTerrain = a.length > 3 ? a[3] : null;
      long seed0 = a.length > 4 ? Long.parseLong(a[4]) : 7L;
      int fails = 0;
      StringBuilder report = new StringBuilder();
      for (Kind k : kinds()) {
         if (only != null && !only.equals("all") && !only.equals(k.id)) continue;
         for (int v = 0; v < k.variants.length; v++) {
            if (onlyVar != null && !onlyVar.equals("all") && !onlyVar.equals(k.variants[v])) continue;
            for (String terr : new String[]{"flat", "slope", "forest", "hill", "lake", "ridge"}) {
               if (onlyTerrain != null && !onlyTerrain.equals("all") && !onlyTerrain.equals(terr)) continue;
               long seed = seed0 * 1000 + v * 31 + terr.hashCode();
               Synth t = new Synth(terr.equals("forest") ? "flat" : terr, seed);
               int rot = (int)Math.floorMod(seed, 4);
               Ctx c = new Ctx(k.id, t, seed, 0, 0, rot, v);
               boolean ok;
               try {
                  ok = k.quickCheck(c) && k.build(c);
               } catch (RuntimeException ex) {
                  ex.printStackTrace();
                  fails++;
                  continue;
               }
               String name = k.id + "__" + k.variants[v] + "__" + terr;
               if (!ok) {
                  report.append(String.format("%-48s rejected site%n", name));
                  continue;
               }
               Plan p = c.plan;
               Weather.apply(c); // [structures2] same material pass as LivingSite.plan
               p.connect();
               int m = 6;
               // chunked execution in shuffled order vs one box
               Vox w1 = new Vox(t);
               Vox w2 = new Vox(t);
               if (terr.equals("forest")) {
                  w1.forest(p.minX - m, p.minZ - m, p.maxX + m, p.maxZ + m, 0.05, seed);
                  w2.forest(p.minX - m, p.minZ - m, p.maxX + m, p.maxZ + m, 0.05, seed);
               }
               Executor.run(p, w1, p.minX - m, p.minZ - m, p.maxX + m, p.maxZ + m);
               List<int[]> chunks = new ArrayList<>();
               for (int cx = Math.floorDiv(p.minX - m, 16); cx <= Math.floorDiv(p.maxX + m, 16); cx++)
                  for (int cz = Math.floorDiv(p.minZ - m, 16); cz <= Math.floorDiv(p.maxZ + m, 16); cz++) chunks.add(new int[]{cx, cz});
               Collections.shuffle(chunks, new Random(seed));
               for (int[] ch : chunks) {
                  Executor.run(p, w2, ch[0] * 16, ch[1] * 16, ch[0] * 16 + 15, ch[1] * 16 + 15);
               }
               int diff = 0;
               Set<Long> keys = new HashSet<>(w1.m.keySet());
               keys.addAll(w2.m.keySet());
               for (long key : keys) {
                  if (!Objects.equals(w1.m.get(key), w2.m.get(key))) diff++;
               }
               // checks: floating plan blocks (nothing solid below a ground-standing piece), buried doors
               List<String> problems = new ArrayList<>(w2.errors);
               int floating = 0;
               for (Plan.Block b : p.blocks()) {
                  String id = Spec.id(b.spec);
                  if (id.equals("minecraft:air") || (b.flags & Plan.ON_GROUND) != 0) continue;
                  String here = w2.get(b.x, b.y, b.z);
                  if (!here.equals(b.spec) && (b.flags & (Plan.IF_FREE | Plan.SUPPORTED)) == 0) {
                     problems.add("overwritten " + b.spec + " at " + b.x + "," + b.y + "," + b.z + " -> " + here);
                  }
                  boolean standing = id.contains("campfire") || id.contains("_tent") || id.contains("game_pole") || id.contains("drying_rack")
                     || id.contains("camp_post") && b.spec.contains("lower") || id.contains("stacked_firewood") && b.y == p.cy || id.contains("lodge_") || id.contains("chest");
                  if (standing && id.contains("_tent")) {
                     standing = p.blockAt(b.x, b.y - 1, b.z) == null;
                  }
                  if (standing) {
                     int below = w2.kind(b.x, b.y - 1, b.z);
                     String bs = w2.get(b.x, b.y - 1, b.z);
                     if (below == World.AIR || below == World.WATER || below == World.PLANT || below == World.LEAVES) {
                        if (!(bs.contains("_tent") || bs.contains("stacked_firewood"))) {
                           floating++;
                           if (floating < 6) problems.add("floating " + b.spec + " at " + b.x + "," + b.y + "," + b.z + " below=" + bs);
                        }
                     }
                  }
               }
               report.append(String.format("%-48s rot=%d blocks=%5d shapes=%5d ents=%d trees=%d box=[%d..%d,%d..%d,%d..%d] chunkdiff=%d problems=%d%n",
                  name, rot, p.count(), p.shapes().size(), p.entities().size(), 0, p.minX, p.maxX, p.minY, p.maxY, p.minZ, p.maxZ, diff, problems.size()));
               for (String s : problems.subList(0, Math.min(8, problems.size()))) report.append("      ").append(s).append('\n');
               if (diff != 0 || !problems.isEmpty()) fails++;
               dump(out.resolve(name + ".json"), w2, p, m, name + "  (rot " + rot + ", " + p.count() + " blocks)");
            }
         }
      }
      System.out.print(report);
      Files.writeString(out.resolve("report.txt"), report.toString());
      System.out.println(fails == 0 ? "ALL OK" : "FAILURES: " + fails);
   }

   static void dump(Path f, Vox w, Plan p, int m, String title) throws IOException {
      StringBuilder b = new StringBuilder("{\"title\":\"" + title + "\",\"blocks\":[");
      boolean first = true;
      int lowest = Integer.MAX_VALUE;
      int bx0 = Integer.MAX_VALUE, bx1 = Integer.MIN_VALUE, bz0 = Integer.MAX_VALUE, bz1 = Integer.MIN_VALUE;
      for (Plan.Block pb : p.blocks()) {
         lowest = Math.min(lowest, pb.y);
         bx0 = Math.min(bx0, pb.x); bx1 = Math.max(bx1, pb.x); bz0 = Math.min(bz0, pb.z); bz1 = Math.max(bz1, pb.z);
      }
      int x0 = bx0 - 5, x1 = bx1 + 5, z0 = bz0 - 5, z1 = bz1 + 5;
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            int top = Math.max(p.maxY + 12, w.t.floor(x, z) + 14);
            int bottom = Math.min(lowest - 1, w.t.floor(x, z) - 3);
            for (int y = bottom; y <= top; y++) {
               String s = w.get(x, y, z);
               if (s.equals("minecraft:air")) continue;
               boolean exposed = false;
               if (!exposed) {
                  for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                     String n = w.get(x + d[0], y + d[1], z + d[2]);
                     int kk = w.kind(x + d[0], y + d[1], z + d[2]);
                     if (kk != World.GROUND || n.contains("path")) { exposed = true; break; }
                  }
               }
               if (!exposed) continue;
               if (!first) b.append(',');
               first = false;
               b.append('[').append(x).append(',').append(y).append(',').append(z).append(",\"").append(s).append("\"]");
            }
         }
      }
      b.append("],\"entities\":[");
      for (int i = 0; i < w.ents.size(); i++) {
         double[] e = w.ents.get(i);
         if (i > 0) b.append(',');
         b.append('[').append(e[0]).append(',').append(e[1]).append(',').append(e[2]).append(",\"").append(w.entIds.get(i)).append("\",").append(e[3]).append(']');
      }
      b.append("]}");
      Files.writeString(f, b.toString());
   }
}
