import com.formaworks.frontierhunts.landscape.AlpineLayout;
import com.formaworks.frontierstructures.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/**
 * [villages] Offline harness for the village planner on the real Frontier (alpine) terrain: success rate and cost per
 * candidate for each village type (what /locate pays), plus raster dumps of planned villages for top-down previews.
 * usage: VillageHarness <seed> <type|all> <outdir> [grid-step-chunks] [count]
 */
public class VillageHarness {
  static final Map<ResourceLocation, VillagePlan.Info> INFO = new HashMap<>();
  public static void main(String[] a) throws Exception {
    VillagePlan.STATS = true;
    long seed = a[0].matches("-?\\d+") ? Long.parseLong(a[0]) : a[0].hashCode();
    String only = a[1]; Path out = Path.of(a[2]); Files.createDirectories(out);
    int step = a.length > 3 ? Integer.parseInt(a[3]) : 78, count = a.length > 4 ? Integer.parseInt(a[4]) : 400;
    Path dir = Path.of(System.getProperty("fs.templates", "fs/resources/data/frontierstructures/structure/expedition"));
    VillagePlan.Templates T = id -> INFO.computeIfAbsent(id, k -> {
      try {return new VillagePlan.Info(NbtIo.readCompressed(dir.resolve(k.getPath().substring("expedition/".length()) + ".nbt"), NbtAccounter.unlimitedHeap()));}
      catch (Exception e) {throw new RuntimeException(e);}
    });
    AlpineLayout L = new AlpineLayout(seed, 21);
    // "auto": the per-cell choice world generation makes (type mix, success rate, cost per cell = what /locate pays)
    if (only.equals("auto")) {
      Map<String, Integer> mix = new TreeMap<>(); int ok = 0; long t0 = System.nanoTime(), worst = 0;
      Map<String, Integer> dumped = new HashMap<>();
      for (int i = 0; i < count; i++) {
        int cx = (i % 20 - 10) * step * 16 + 8, cz = (i / 20 - 10) * step * 16 + 8;
        long s0 = System.nanoTime();
        TerrainFit.Natural nat = nat(L);
        VillagePlan p = VillagePlan.chooseIn(T, nat, net.minecraft.util.RandomSource.create(seed * 31 + i), cx, cz, 63, -64, 320, (x, y, z) -> true);
        worst = Math.max(worst, System.nanoTime() - s0);
        if (p == null) continue;
        ok++; mix.merge(p.type, 1, Integer::sum);
        int d = dumped.getOrDefault(p.type, 0);
        if (d < 3) {dump(out.resolve(p.type + "_" + d + ".json"), p, T, nat); dumped.put(p.type, d + 1);}
      }
      System.out.println("   why: " + new TreeMap<>(VillagePlan.WHY));
      System.out.printf("auto: %d/%d cells get a village (%.1f%%) %s  %.1f ms/cell (worst %.1f ms)%n", ok, count, 100.0 * ok / count, mix, (System.nanoTime() - t0) / 1e6 / count, worst / 1e6);
      return;
    }
    for (String type : only.equals("all") ? VillagePlan.TYPES : new String[]{only}) {
      int ok = 0, tot = 0, dumped = 0; long t0 = System.nanoTime(); long worst = 0;
      for (int i = 0; i < count; i++) {
        int cx = (i % 20 - 10) * step * 16 + 8, cz = (i / 20 - 10) * step * 16 + 8;
        tot++;
        long s0 = System.nanoTime();
        TerrainFit.Natural nat = nat(L);
        Random r = new Random(seed * 31 + i);
        VillagePlan p = null;
        for (int att = 0; att < VillagePlan.ATTEMPTS && p == null; att++) {
          int x = cx + (att == 0 ? 0 : r.nextInt(97) - 48), z = cz + (att == 0 ? 0 : r.nextInt(97) - 48);
          p = VillagePlan.plan(type, T, nat, r.nextLong(), x, z, 63);
        }
        long dt = System.nanoTime() - s0; worst = Math.max(worst, dt);
        if (p == null) continue;
        ok++;
        if (dumped < 3) {dump(out.resolve(type + "_" + dumped + ".json"), p, T, nat); dumped++;}
      }
      System.out.println("   why: " + new TreeMap<>(VillagePlan.WHY)); VillagePlan.WHY.clear();
      System.out.printf("%-10s ok %d/%d (%.1f%%)  %.2f ms/candidate (worst %.1f ms)%n", type, ok, tot, 100.0 * ok / tot, (System.nanoTime() - t0) / 1e6 / tot, worst / 1e6);
    }
  }

  static TerrainFit.Natural nat(AlpineLayout L) {
    Map<Long, int[]> cache = new HashMap<>();
    return new TerrainFit.Natural() {
      int[] s(int x, int z) {return cache.computeIfAbsent(((long)x << 32) ^ (z & 0xFFFFFFFFL), k -> {var q = L.sample(x, z); int f = q.floor() + 1; return new int[]{f, Math.max(q.floor(), q.water()) + 1};});}
      public int floor(int x, int z) {return s(x, z)[0];}
      public int surface(int x, int z) {return s(x, z)[1];}
    };
  }

  static void dump(Path f, VillagePlan p, VillagePlan.Templates T, TerrainFit.Natural nat) throws Exception {
    var g = new VillageGround(p, T);
    StringBuilder b = new StringBuilder();
    int x0 = p.minX - 12, x1 = p.maxX + 12, z0 = p.minZ - 12, z1 = p.maxZ + 12;
    b.append("{\"type\":\"").append(p.type).append("\",\"x0\":").append(x0).append(",\"z0\":").append(z0).append(",\"w\":").append(x1 - x0 + 1).append(",\"h\":").append(z1 - z0 + 1);
    b.append(",\"cx\":").append(p.cx).append(",\"cz\":").append(p.cz).append(",\"gy\":").append(p.greenY);
    b.append(",\"cols\":[");
    for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
      var c = g.col(x, z, nat);
      if (x != x0 || z != z0) b.append(',');
      b.append('[').append(VillageGroundDebug.kind(c)).append(',').append(VillageGroundDebug.y(c)).append(',').append(nat.floor(x, z)).append(',').append(nat.surface(x, z) > nat.floor(x, z) ? 1 : 0).append(',').append(VillageGroundDebug.lot(c)).append(']');
    }
    b.append("],\"lots\":[");
    for (int i = 0; i < p.lots.size(); i++) {var l = p.lots.get(i); if (i > 0) b.append(','); b.append("[\"").append(l.template.getPath()).append("\",\"").append(l.rotation).append("\",").append(l.ox).append(',').append(l.oz).append(',').append(l.yard).append(',').append(l.wx).append(',').append(l.wz).append(']');}
    b.append("],\"decos\":[");
    for (int i = 0; i < p.decos.size(); i++) {var d = p.decos.get(i); if (i > 0) b.append(','); b.append("[\"").append(d.kind).append("\",").append(d.x).append(',').append(d.z).append(',').append(d.y).append(',').append(d.facing).append(']');}
    b.append("]}");
    Files.writeString(f, b);
  }
}
