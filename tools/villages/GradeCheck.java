import com.formaworks.frontierhunts.landscape.AlpineLayout;
import com.formaworks.frontierstructures.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/**
 * [1.1.3] Village grading check on the real alpine terrain: plans villages the way world generation does and measures
 * the cliffs the grading cuts into the land around them (adjacent columns more than 2 blocks apart, steeper than the
 * natural ground there, outside the buildings' own footprints) - the "craters" round villages - and writes a cut/fill
 * map per village (red = cut down, blue = filled up, black = a village-made cliff).
 * usage: GradeCheck <seed> <outdir> [count]
 */
public class GradeCheck {
  static final Map<ResourceLocation, VillagePlan.Info> INFO = new HashMap<>();
  static final Map<String, Integer> PAIRS = new TreeMap<>();
  public static void main(String[] a) throws Exception {
    long seed = a[0].matches("-?\\d+") ? Long.parseLong(a[0]) : a[0].hashCode();
    Path out = Path.of(a[1]); Files.createDirectories(out);
    int count = a.length > 2 ? Integer.parseInt(a[2]) : 120, step = 78;
    Path dir = Path.of(System.getProperty("fs.templates", "fs/resources/data/frontierstructures/structure/expedition"));
    VillagePlan.Templates T = id -> INFO.computeIfAbsent(id, k -> {
      try {return new VillagePlan.Info(NbtIo.readCompressed(dir.resolve(k.getPath().substring("expedition/".length()) + ".nbt"), NbtAccounter.unlimitedHeap()));}
      catch (Exception e) {throw new RuntimeException(e);}
    });
    AlpineLayout L = new AlpineLayout(seed, 21);
    int villages = 0, cliffs = 0, deep = 0, imgs = 0; long cutCols = 0, maxCut = 0;
    for (int i = 0; i < count; i++) {
      int cx = (i % 20 - 10) * step * 16 + 8, cz = (i / 20 - 10) * step * 16 + 8;
      TerrainFit.Natural nat = VillageHarnessNat.nat(L);
      VillagePlan p = VillagePlan.chooseIn(T, nat, net.minecraft.util.RandomSource.create(seed * 31 + i), cx, cz, 63, -64, 320, (x, y, z) -> true);
      if (p == null) continue;
      villages++;
      var g = new VillageGround(p, T);
      int m = 26, x0 = p.minX - m, z0 = p.minZ - m, w = p.maxX - p.minX + 1 + 2 * m, h = p.maxZ - p.minZ + 1 + 2 * m;
      int[] gy = new int[w * h], ny = new int[w * h], kind = new int[w * h];
      for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
        var c = g.col(x0 + x, z0 + z, nat);
        gy[x + z * w] = VillageGroundDebug.y(c); ny[x + z * w] = nat.floor(x0 + x, z0 + z); kind[x + z * w] = VillageGroundDebug.kind(c);
      }
      boolean[] cliff = new boolean[w * h];
      int vc = 0;
      for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
        int k = x + z * w;
        int cut = ny[k] - gy[k];
        if (kind[k] != 1 && cut != 0) cutCols++;
        maxCut = Math.max(maxCut, Math.abs(cut));
        for (int[] q : new int[][]{{1, 0}, {0, 1}}) {
          int xx = x + q[0], zz = z + q[1];
          if (xx >= w || zz >= h) continue;
          int j = xx + zz * w;
          if (kind[k] == 1 || kind[j] == 1) continue; // a building's own wall
          int dg = Math.abs(gy[k] - gy[j]), dn = Math.abs(ny[k] - ny[j]);
          if (dg >= 3 && dg > dn + 1) {cliff[k] = cliff[j] = true; vc++; if (dg >= 5) deep++;
            PAIRS.merge(Math.min(kind[k], kind[j]) + "/" + Math.max(kind[k], kind[j]) + (dg >= 5 ? " 5+" : ""), 1, Integer::sum);
            if (Boolean.getBoolean("gc.verbose") && vc <= 12) System.out.printf("   cliff %d,%d kind %d/%d graded %d/%d natural %d/%d%n", x0 + x, z0 + z, kind[k], kind[j], gy[k], gy[j], ny[k], ny[j]);
            if (System.getProperty("gc.probe", "").equals((x0 + x) + "," + (z0 + z))) {
              VillageGround.DEBUG = System.out::println;
              System.out.println(" A " + (x0 + x) + "," + (z0 + z)); g.col(x0 + x, z0 + z, nat);
              System.out.println(" B " + (x0 + xx) + "," + (z0 + zz)); g.col(x0 + xx, z0 + zz, nat);
              VillageGround.DEBUG = null;
            }}
        }
      }
      cliffs += vc;
      System.out.printf("%-10s at %6d %6d: %4d village-made cliff edges%n", p.type, p.cx, p.cz, vc);
      if (imgs < 4) {
        BufferedImage im = new BufferedImage(w * 3, h * 3, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
          int k = x + z * w, d = gy[k] - ny[k], rgb;
          if (cliff[k]) rgb = 0x000000;
          else if (kind[k] == 1) rgb = 0x8a6a3a;
          else if (kind[k] == 3) rgb = 0xd8d0b0;
          else if (d < 0) rgb = (Math.min(255, 120 + -d * 12) << 16) | 0x4040;
          else if (d > 0) rgb = 0x4040 << 8 | Math.min(255, 120 + d * 12);
          else rgb = 0x5a8a4a;
          for (int a2 = 0; a2 < 3; a2++) for (int b2 = 0; b2 < 3; b2++) im.setRGB(x * 3 + a2, z * 3 + b2, rgb);
        }
        ImageIO.write(im, "png", out.resolve("grade_" + imgs + "_" + p.type + ".png").toFile());
        imgs++;
      }
    }
    System.out.println("cliffs by kinds (2 green 3 road 4 edge 5 water 0 none): " + PAIRS);
    System.out.printf("villages %d  village-made cliff edges %d (%.1f per village, %d of 5+ blocks)  regraded columns %d  max cut/fill %d%n",
      villages, cliffs, cliffs / Math.max(1.0, villages), deep, cutCols, maxCut);
  }
}

class VillageHarnessNat {
  static TerrainFit.Natural nat(AlpineLayout L) {
    Map<Long, int[]> cache = new HashMap<>();
    return new TerrainFit.Natural() {
      int[] s(int x, int z) {return cache.computeIfAbsent(((long)x << 32) ^ (z & 0xFFFFFFFFL), k -> {var q = L.sample(x, z); int f = q.floor() + 1; return new int[]{f, Math.max(q.floor(), q.water()) + 1};});}
      public int floor(int x, int z) {return s(x, z)[0];}
      public int surface(int x, int z) {return s(x, z)[1];}
    };
  }
}
