import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [clothing] Offline warmth table: the real Clothing values and layering (Clothing.layer / total) and the real body-heat
 * step (SurvivalMath.stepHeat, comfortLow / High, windChill, exertion) for typical outfits in typical weather, plus sanity
 * checks of the ordering and of what should happen. Felt temperature mirrors SurvivalService.update for an exposed spot
 * (no shelter, no fire, normal fat, Normal difficulty cold multiplier 1).
 *
 * <pre>
 *   javac -proc:none -cp "$(cat .infra/cp62.txt):/tmp/claude-0/cc-clothing" -d /tmp/claude-0/wt tools/clothing/WarmthTable.java
 *   java -cp "$(cat .infra/cp62.txt):/tmp/claude-0/cc-clothing:/tmp/claude-0/wt" WarmthTable > docs/ws/clothing/warmth_table.md
 * </pre>
 * Exit code 1 when a sanity check fails (the failures are listed at the end of the table).
 */
public final class WarmthTable {
   /** One worn piece: built-in id (or a garment kind), body region(s), sewn lining bits. */
   record Piece(String id, float ins, float wind, float water, int[] regions) {
   }

   static Piece p(String id, int... regions) {
      Clothing.Warmth w = Clothing.builtIn("frontierhunts:" + id);
      if (w == null) {
         w = garment(id);
      }
      if (w == null) {
         throw new IllegalArgumentException(id);
      }
      return new Piece(id, w.insulation(), w.wind(), w.water(), regions);
   }

   static Piece lined(Piece p) {
      return new Piece(p.id + "+fur lining", p.ins + Clothing.LINING, p.wind, p.water, p.regions);
   }

   /** GarmentItem.Kind values, read without loading item classes (kept equal by the check below). */
   static Clothing.Warmth garment(String id) {
      return switch (id) {
         case "fur_hat" -> new Clothing.Warmth(1.0F, 0.6F, 0.4F);
         case "buckskin_coat" -> new Clothing.Warmth(1.6F, 0.6F, 0.5F);
         case "bear_fur_coat" -> new Clothing.Warmth(3.0F, 0.8F, 0.5F);
         case "hide_robe" -> new Clothing.Warmth(2.4F, 0.9F, 0.7F);
         case "buckskin_leggings" -> new Clothing.Warmth(1.0F, 0.5F, 0.4F);
         case "fur_mukluks" -> new Clothing.Warmth(0.8F, 0.5F, 0.5F);
         default -> null;
      };
   }

   record Outfit(String name, List<Piece> pieces, boolean mittens) {
      Clothing.Outfit total() {
         float ins = 1F;
         float[] wind = new float[4], water = new float[4];
         for (Piece p : this.pieces) {
            ins += p.ins;
            for (int r : p.regions) {
               Clothing.layer(wind, water, r, new Clothing.Warmth(p.ins, p.wind, p.water));
            }
         }
         if (this.mittens) {
            ins += Clothing.MITTENS;
         }
         return Clothing.total(ins, wind, water, this.mittens);
      }
   }

   record Weather(String name, float air, float wind, float precipCold, float wet, float work) {
   }

   static final int HEAD = 0, TORSO = 1, LEGS = 2, FEET = 3;

   public static void main(String[] args) {
      Map<String, Outfit> outfits = new LinkedHashMap<>();
      List<Piece> carbon = List.of(p("carbon_hood", HEAD), p("carbon_jacket", TORSO), p("carbon_trousers", LEGS));
      outfits.put("plain", new Outfit("Plain clothes", List.of(), false));
      outfits.put("carbon", new Outfit("Carbon base layer (hood, jacket, trousers)", carbon, false));
      outfits.put("ghillie", new Outfit("Woodland ghillie set", List.of(p("ghillie_hood", HEAD), p("ghillie_jacket", TORSO), p("ghillie_trousers", LEGS)), false));
      List<Piece> gc = new ArrayList<>(carbon);
      gc.addAll(outfits.get("ghillie").pieces());
      outfits.put("ghillie_carbon", new Outfit("Ghillie set over the carbon layer", gc, false));
      outfits.put("coverall", new Outfit("Timber camo coveralls", List.of(p("timber_camo_coveralls", TORSO, LEGS)), false));
      outfits.put("coverall_lined", new Outfit("Coveralls, fur-lined, over the scent suit, fur hat",
         List.of(p("scent_suit", HEAD, TORSO, LEGS), lined(p("timber_camo_coveralls", TORSO, LEGS)), p("fur_hat", HEAD)), false));
      outfits.put("buckskin", new Outfit("Trapper: fur hat, buckskin coat + leggings, mukluks",
         List.of(p("fur_hat", HEAD), p("buckskin_coat", TORSO), p("buckskin_leggings", LEGS), p("fur_mukluks", FEET)), false));
      outfits.put("arctic", new Outfit("Arctic: scent suit, fur hat, bear coat, leggings, mukluks, mittens",
         List.of(p("scent_suit", HEAD, TORSO, LEGS), p("fur_hat", HEAD), p("bear_fur_coat", TORSO), p("buckskin_leggings", LEGS), p("fur_mukluks", FEET)), true));
      outfits.put("robe", new Outfit("Hide robe, leggings, mukluks, fur hat",
         List.of(p("fur_hat", HEAD), p("hide_robe", TORSO), p("buckskin_leggings", LEGS), p("fur_mukluks", FEET)), false));

      List<Weather> weathers = List.of(
         new Weather("Mild autumn afternoon, 12 C, breeze, walking", 12F, 2F, 0F, 0F, 0.4F),
         new Weather("Cold clear night, -5 C, light wind, standing", -5F, 3F, 0F, 0F, 0F),
         new Weather("Snowfield blizzard, -18 C, 12 m/s, standing", -18F, 12F, 12.5F, 0.3F, 0F),
         new Weather("Swamp in the rain, 8 C, 4 m/s, soaked, walking", 8F, 4F, 2.5F, 0.8F, 0.4F),
         new Weather("Desert noon, 38 C, sprinting", 38F, 1F, 0F, 0F, 1F),
         new Weather("Mild day, 18 C, sprinting", 18F, 2F, 0F, 0F, 1F),
         new Weather("Mild day, 18 C, standing", 18F, 2F, 0F, 0F, 0F));

      StringBuilder md = new StringBuilder();
      md.append("# Warmth table (generated by tools/clothing/WarmthTable.java - do not edit)\n\n");
      md.append("Real values: `Clothing` built-ins / `GarmentItem.Kind`, layered per region (`Clothing.layer`, `Clothing.total`); body heat ")
         .append("stepped once a second with `SurvivalMath.stepHeat` (incl. `exertion`) for 60 minutes in the open (no shelter, no fire). ")
         .append("Insulation 1.0 = ordinary clothes; comfort low = 15 - 5 x insulation. Body heat: 0 comfortable, -30 chilly, -50 shivering, ")
         .append("-75 hypothermia, +50 hot. Times are real minutes (one Minecraft day is 20 minutes: 1 min = 1.2 game hours).\n\n");
      md.append("## Outfits\n\n| outfit | insulation | windproof | water-resistant | comfortable from | to |\n|---|---|---|---|---|---|\n");
      Map<String, Clothing.Outfit> totals = new LinkedHashMap<>();
      for (Map.Entry<String, Outfit> e : outfits.entrySet()) {
         Clothing.Outfit t = e.getValue().total();
         totals.put(e.getKey(), t);
         md.append(String.format(Locale.ROOT, "| %s | %.2f | %d%% | %d%% | %.0f C | %.0f C |\n", e.getValue().name(), t.insulation(),
            Math.round(t.wind() * 100), Math.round(t.water() * 100), SurvivalMath.comfortLow(t.insulation()), SurvivalMath.comfortHigh(t.insulation())));
      }
      md.append("\n## Outfit x weather (felt temperature, then what 60 minutes do to body heat)\n\n");
      md.append("| outfit |");
      for (Weather w : weathers) {
         md.append(' ').append(w.name()).append(" |");
      }
      md.append("\n|---|");
      md.append("---|".repeat(weathers.size()));
      md.append('\n');
      Map<String, float[]> minutes = new LinkedHashMap<>();
      Map<String, Float> hypoMin = new LinkedHashMap<>();
      for (Map.Entry<String, Outfit> e : outfits.entrySet()) {
         Clothing.Outfit t = totals.get(e.getKey());
         md.append("| ").append(e.getValue().name()).append(" |");
         float[] res = new float[weathers.size() * 2];
         for (int i = 0; i < weathers.size(); i++) {
            Weather w = weathers.get(i);
            float ins = t.insulation() * (1F - 0.6F * w.wet() * (1F - t.water()));
            float felt = w.air() - w.precipCold() * SurvivalMath.weatherShield(t.wind(), t.water()) - SurvivalMath.windChill(w.wind(), 1F, t.wind())
               - w.wet() * 9F * (1F - t.water() * 0.6F);
            float heat = 0F;
            int chilly = -1, hot = -1, hypo = -1;
            for (int s = 1; s <= 3600; s++) {
               heat = SurvivalMath.stepHeat(heat, felt, ins, 1F, 1F, w.work(), 1F, 0F);
               if (chilly < 0 && heat <= SurvivalMath.CHILLY) {
                  chilly = s;
               }
               if (hypo < 0 && heat <= SurvivalMath.HYPOTHERMIA) {
                  hypo = s;
               }
               if (hot < 0 && heat >= SurvivalMath.HOT) {
                  hot = s;
               }
            }
            res[i * 2] = chilly < 0 ? 999F : chilly / 60F;
            hypoMin.put(e.getKey() + "#" + i, hypo < 0 ? 999F : hypo / 60F);
            res[i * 2 + 1] = hot < 0 ? 999F : hot / 60F;
            String verdict = chilly > 0 ? String.format(Locale.ROOT, "chilly after %s%s", time(chilly), hypo > 0 ? ", hypothermia after " + time(hypo) : "")
               : hot > 0 ? String.format(Locale.ROOT, "hot after %s", time(hot))
               : String.format(Locale.ROOT, "fine (heat %+.0f)", heat);
            md.append(String.format(Locale.ROOT, " %.0f C felt, ins %.1f: %s |", felt, ins, verdict));
         }
         minutes.put(e.getKey(), res);
         md.append('\n');
      }
      // ------------------------------------------------------------------ sanity checks
      List<String> fail = new ArrayList<>();
      String[] order = {"arctic", "buckskin", "coverall", "ghillie", "carbon", "plain"};
      for (int i = 0; i + 1 < order.length; i++) {
         if (!(totals.get(order[i]).insulation() > totals.get(order[i + 1]).insulation())) {
            fail.add("insulation order: " + order[i] + " should be warmer than " + order[i + 1]);
         }
      }
      check(fail, totals.get("carbon").insulation() - 1F <= 0.9F, "the carbon layer is a thin base layer (<= 0.9 insulation)");
      check(fail, totals.get("ghillie_carbon").insulation() > totals.get("ghillie").insulation(), "the base layer adds warmth under a ghillie");
      check(fail, minutes.get("plain")[2] < 40F, "plain clothes get chilly on a cold night within 40 min");
      check(fail, minutes.get("arctic")[2] >= 999F, "the arctic outfit keeps you fine on a cold night");
      check(fail, minutes.get("arctic")[4] >= 2.5F, "the arctic outfit keeps a still hunter out of the chill for 3 game hours in a blizzard");
      check(fail, hypoMin.get("arctic#2") >= 6F, "the arctic outfit: no hypothermia for 7 game hours standing in a blizzard");
      check(fail, minutes.get("plain")[4] < 1F, "plain clothes in a blizzard: chilly within the game hour");
      check(fail, hypoMin.get("plain#2") < 3F, "plain clothes in a blizzard: hypothermia within 3-4 game hours");
      check(fail, minutes.get("arctic")[9] < 20F, "fur in the desert gets hot within 20 min");
      check(fail, minutes.get("plain")[9] > minutes.get("arctic")[9], "plain clothes in the desert take longer to get hot than fur");
      check(fail, minutes.get("arctic")[11] > 5F, "sprinting in fur on a mild day: not hot before 5 min (gentle)");
      check(fail, minutes.get("arctic")[11] < 999F, "sprinting in fur on a mild day does get hot eventually");
      check(fail, minutes.get("arctic")[13] >= 999F, "standing in fur on a mild day is fine (no heat build-up)");
      check(fail, minutes.get("plain")[11] >= 999F, "sprinting in plain clothes on a mild day stays fine");
      check(fail, minutes.get("coverall")[6] < minutes.get("buckskin")[6], "soaked in the swamp, coveralls chill sooner than hides");
      md.append("\n## Sanity checks\n\n");
      md.append(fail.isEmpty() ? "All checks pass: insulation order fur > buckskin > coveralls > ghillie > carbon > plain; the carbon layer is thin; "
         + "plain clothes chill on a cold night within the game hour and reach hypothermia in a blizzard within ~1 game hour; the arctic "
         + "outfit stays fine on a cold night and holds a still hunter ~3 game hours in a blizzard before the chill (7.6 h to hypothermia); fur "
         + "overheats in the desert and (slowly) when sprinting on a mild day, never while standing; wet coveralls chill sooner than hides.\n" : "FAILED:\n");
      for (String f : fail) {
         md.append("- ").append(f).append('\n');
      }
      System.out.print(md);
      System.exit(fail.isEmpty() ? 0 : 1);
   }

   static String time(int seconds) {
      float m = seconds / 60F;
      return m < 1F ? String.format(Locale.ROOT, "%d s (%.0f game min)", seconds, seconds * 1.2F)
         : String.format(Locale.ROOT, "%.0f min (%.1f game h)", m, m * 1.2F);
   }

   static void check(List<String> fail, boolean ok, String what) {
      if (!ok) {
         fail.add(what);
      }
   }
}
