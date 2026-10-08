package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [structures2] Final material pass over a built plan so the frontier buildings read as old, weathered and local rather
 * than freshly stamped: one roof palette per site (dark oak shingles, spruce shakes with darker patches, or a mossy old
 * roof), horizontal wall logs that are partly bark-stripped, cobble footings with moss, planks partly weathered. Pure and
 * seeded (same plan in every chunk).
 */
public final class Weather {
   private Weather() {
   }

   static String swapId(String spec, String id) {
      int i = spec.indexOf('[');
      return i < 0 ? id : id + spec.substring(i);
   }

   public static void apply(Ctx c) {
      Rnd site = new Rnd(Rnd.mix(c.seed, 0x5713A7L));
      int roof = site.nextInt(3);
      c.plan.restyle(b -> {
         String id = Spec.id(b.spec);
         Rnd r = new Rnd(Rnd.mix(c.seed, Plan.key(b.x, b.y, b.z)));
         double p = r.nextDouble();
         switch (id) {
            case "frontierhunts:roof_stairs":
               return swapId(b.spec, switch (roof) {
                  case 0 -> p < 0.12 ? "minecraft:spruce_stairs" : "minecraft:dark_oak_stairs";
                  case 1 -> p < 0.18 ? "minecraft:dark_oak_stairs" : "minecraft:spruce_stairs";
                  default -> p < 0.22 ? "minecraft:mossy_cobblestone_stairs" : p < 0.3 ? "minecraft:spruce_stairs" : "minecraft:dark_oak_stairs";
               });
            case "frontierhunts:roof_slab":
               return swapId(b.spec, switch (roof) {
                  case 1 -> "minecraft:spruce_slab";
                  default -> p < 0.2 && roof == 2 ? "minecraft:mossy_cobblestone_slab" : "minecraft:dark_oak_slab";
               });
            case "minecraft:spruce_log":
               // horizontal wall logs only; posts and trees keep their bark
               return !"y".equals(Spec.get(b.spec, "axis")) && p < 0.14 ? swapId(b.spec, "minecraft:stripped_spruce_log") : b.spec;
            case "minecraft:stripped_spruce_log":
               return !"y".equals(Spec.get(b.spec, "axis")) && p < 0.1 ? swapId(b.spec, "minecraft:spruce_log") : b.spec;
            case "minecraft:cobblestone":
               return p < 0.4 ? "minecraft:mossy_cobblestone" : b.spec;
            case "frontierhunts:fieldstone":
               return p < 0.3 ? "minecraft:mossy_cobblestone" : p < 0.5 ? "minecraft:cobblestone" : b.spec;
            case "minecraft:spruce_planks":
               return p < 0.15 ? "frontierhunts:weathered_planks" : b.spec;
            default:
               return b.spec;
         }
      });
   }
}
