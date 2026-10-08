package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.survival.item.GarmentItem;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * [survival] How warm worn clothing is. Insulation is in "layers" (the clothes you always wear count 1.0; each point
 * lowers the comfortable temperature by 5 deg C), wind and water resistance 0..1.
 *
 * Built-in values for vanilla armour and the mod's own clothing (ghillie, carbon layer, coveralls, the hide garments);
 * datapacks override or add with {@code data/<ns>/survival/clothing/*.json}: {@code {"entries": {"minecraft:leather_chestplate":
 * {"insulation": 0.8, "wind": 0.3, "water": 0.2}}}}. Anything unknown that is worn as armour gets a small default.
 *
 * Layering: a fur lining (+0.6) can be sewn into ANY worn piece (camo, ghillie, coveralls...) and fur mittens (+0.3, less
 * shiver sway) into any chest piece, so the camo stays on the outside and the fur goes underneath.
 */
public final class Clothing {
   public record Warmth(float insulation, float wind, float water) {
      public static final Warmth NONE = new Warmth(0F, 0F, 0F);
   }

   /** The whole outfit: insulation includes the 1.0 base, wind/water are coverage-weighted 0..1. */
   public record Outfit(float insulation, float wind, float water, boolean mittens) {
   }

   public static final float LINING = 0.6F;
   public static final float MITTENS = 0.3F;
   private static final Gson GSON = new Gson();
   private static volatile Map<Item, Warmth> overrides = Map.of();
   private static final Map<Item, Warmth> CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Warmth> BUILT_IN = new HashMap<>();

   static {
      w("minecraft:leather_helmet", 0.3F, 0.3F, 0.2F);
      w("minecraft:leather_chestplate", 0.8F, 0.35F, 0.25F);
      w("minecraft:leather_leggings", 0.6F, 0.3F, 0.2F);
      w("minecraft:leather_boots", 0.3F, 0.3F, 0.3F);
      w("minecraft:turtle_helmet", 0.1F, 0.1F, 0.5F);
      // ghillie: a leaf mesh over light cloth
      for (String p : new String[]{"", "grassland_", "wetland_", "snow_"}) {
         w("frontierhunts:ghillie_" + p + "hood", 0.2F, 0.1F, 0.05F);
         w("frontierhunts:ghillie_" + p + "jacket", 0.5F, 0.15F, 0.1F);
         w("frontierhunts:ghillie_" + p + "trousers", 0.3F, 0.1F, 0.05F);
      }
      // [clothing] carbon base layer (worn under everything): thin activated-carbon knit, a light base layer's warmth
      w("frontierhunts:carbon_hood", 0.15F, 0.30F, 0.20F);
      w("frontierhunts:carbon_jacket", 0.35F, 0.45F, 0.25F);
      w("frontierhunts:carbon_trousers", 0.25F, 0.40F, 0.20F);
      w("frontierhunts:scent_suit", 0.60F, 0.45F, 0.25F);
      // insulated hunting coveralls (chest slot, cover the legs too); [clothing] 1.6 -> 1.4: warm, but below hides and fur
      for (String c : new String[]{"timber", "autumn", "marsh", "prairie", "snow", "blaze"}) {
         w("frontierhunts:" + c + "_camo_coveralls", 1.4F, 0.55F, 0.45F);
      }
      w("frontierhunts:digital_camo_coveralls", 1.4F, 0.55F, 0.45F);
   }

   private static void w(String id, float ins, float wind, float water) {
      BUILT_IN.put(id, new Warmth(ins, wind, water));
   }

   private Clothing() {
   }

   public static float weight(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> 0.15F;
         case CHEST -> 0.45F;
         case LEGS -> 0.30F;
         case FEET -> 0.10F;
         default -> 0F;
      };
   }

   /** Warmth of one piece (without lining). */
   public static Warmth of(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return Warmth.NONE;
      }
      Item item = stack.getItem();
      Warmth o = overrides.get(item);
      if (o != null) {
         return o;
      }
      return CACHE.computeIfAbsent(item, Clothing::builtIn);
   }

   private static Warmth builtIn(Item item) {
      if (item instanceof GarmentItem g) {
         return new Warmth(g.kind.insulation, g.kind.wind, g.kind.water);
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      Warmth b = BUILT_IN.get(id.toString());
      if (b != null) {
         return b;
      }
      if (item instanceof ArmorItem a) {
         if (a.getMaterial().is(ArmorMaterials.CHAIN)) {
            return new Warmth(0.05F, 0.02F, 0F);
         }
         String path = id.getPath().toLowerCase(Locale.ROOT);
         if (path.contains("fur") || path.contains("wool") || path.contains("parka") || path.contains("fleece") || path.contains("winter")) {
            return new Warmth(1.0F, 0.5F, 0.3F);
         }
         if (item.builtInRegistryHolder().is(ItemTags.FREEZE_IMMUNE_WEARABLES)) {
            return new Warmth(0.5F, 0.3F, 0.2F);
         }
         float base = a.getType() == ArmorItem.Type.CHESTPLATE ? 0.25F : 0.15F;
         return new Warmth(base, 0.2F, 0.15F);
      }
      return Warmth.NONE;
   }

   public static int lining(ItemStack stack) {
      Integer v = stack.get(SurvivalContent.LINING.get());
      return v == null ? 0 : v;
   }

   /** Insulation of one worn piece including its lining. */
   public static float insulation(ItemStack stack) {
      int l = lining(stack);
      return of(stack).insulation() + ((l & 1) != 0 ? LINING : 0F) + ((l & 2) != 0 ? MITTENS : 0F);
   }

   /**
    * The whole outfit. [clothing] Insulation adds up over every worn piece (outer garments, their sewn fur linings and
    * mittens, and the carbon base layer underneath); wind and water resistance are worked out per body region (head,
    * torso, legs, feet) - layers over the same region combine as 1 - (1 - a)(1 - b) - and then weighted by how much of
    * the body the region is. A one-piece coverall or scent suit covers torso and legs.
    */
   public static Outfit outfit(LivingEntity e) {
      float ins = 1F;
      float[] wind = new float[4], water = new float[4];
      boolean mittens = false;
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack s = e.getItemBySlot(slot);
         if (s.isEmpty()) {
            continue;
         }
         Warmth w = of(s);
         ins += insulation(s);
         int r = region(slot);
         layer(wind, water, r, w);
         if (slot == EquipmentSlot.CHEST && s.getItem() instanceof com.formaworks.frontierhunts.expedition.Coverall) {
            layer(wind, water, 2, w); // one-piece: covers the legs as well
         }
         if ((lining(s) & 2) != 0) {
            mittens = true;
         }
      }
      if (e instanceof net.minecraft.world.entity.player.Player p) {
         ItemStack[] base = com.formaworks.frontierhunts.clothing.BaseLayer.worn(p);
         for (int i = 0; i < base.length; i++) {
            ItemStack s = base[i];
            if (s.isEmpty()) {
               continue;
            }
            Warmth w = of(s);
            ins += insulation(s);
            if (s.getItem() instanceof com.formaworks.frontierhunts.expedition.ScentControl c
               && c.piece == com.formaworks.frontierhunts.expedition.ScentControl.Piece.SUIT) {
               layer(wind, water, 0, w);
               layer(wind, water, 1, w);
               layer(wind, water, 2, w);
            } else {
               layer(wind, water, i == 0 ? 0 : (i == 1 ? 1 : 2), w);
            }
         }
      }
      return total(ins, wind, water, mittens);
   }

   /** [clothing] Region-weighted wind / water of layered regions (head, torso, legs, feet) - pure, for offline tables. */
   public static Outfit total(float ins, float[] wind, float[] water, boolean mittens) {
      float wd = 0F, wa = 0F;
      for (int r = 0; r < 4; r++) {
         float wt = weight(REGIONS[r]);
         wd += wind[r] * wt;
         wa += water[r] * wt;
      }
      return new Outfit(ins, Math.min(0.95F, wd), Math.min(0.95F, wa), mittens);
   }

   /** [clothing] Built-in values by item id (no registry needed; offline tables). Garments: see GarmentItem.Kind. */
   public static Warmth builtIn(String id) {
      return BUILT_IN.get(id);
   }

   private static final EquipmentSlot[] REGIONS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   private static int region(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> 0;
         case CHEST -> 1;
         case LEGS -> 2;
         default -> 3;
      };
   }

   /** [clothing] Put one more layer over region r (0 head, 1 torso, 2 legs, 3 feet): 1 - (1 - a)(1 - b). */
   public static void layer(float[] wind, float[] water, int r, Warmth w) {
      wind[r] = 1F - (1F - wind[r]) * (1F - Math.min(0.95F, w.wind()));
      water[r] = 1F - (1F - water[r]) * (1F - Math.min(0.95F, w.water()));
   }

   /** Tooltip lines (warmth dots, wind, water, lining). */
   public static void describe(ItemStack stack, EquipmentSlot slot, List<Component> lines) {
      Warmth w = of(stack);
      float ins = insulation(stack);
      if (ins <= 0F) {
         return;
      }
      int dots = Math.max(1, Math.min(6, Math.round(ins * 2F)));
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 6; i++) {
         b.append(i < dots ? '●' : '○');
      }
      lines.add(Component.translatable("survival.frontierhunts.tooltip.warmth", b.toString(), String.format(Locale.ROOT, "%.1f", ins))
         .withStyle(ChatFormatting.GOLD));
      lines.add(Component.translatable(
            "survival.frontierhunts.tooltip.weather", Math.round(w.wind() * 100F), Math.round(w.water() * 100F)
         )
         .withStyle(ChatFormatting.DARK_GRAY));
      int l = lining(stack);
      if ((l & 1) != 0) {
         lines.add(Component.translatable("survival.frontierhunts.tooltip.lined").withStyle(ChatFormatting.DARK_AQUA));
      }
      if ((l & 2) != 0) {
         lines.add(Component.translatable("survival.frontierhunts.tooltip.mittens").withStyle(ChatFormatting.DARK_AQUA));
      }
   }

   static final class Loader extends SimpleJsonResourceReloadListener {
      Loader() {
         super(GSON, "survival/clothing");
      }

      @Override
      protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
         Map<Item, Warmth> out = new HashMap<>();
         files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
            try {
               JsonObject root = file.getValue().getAsJsonObject();
               JsonObject entries = root.has("entries") ? root.getAsJsonObject("entries") : root;
               for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                  ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
                  if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl) || !e.getValue().isJsonObject()) {
                     continue;
                  }
                  JsonObject o = e.getValue().getAsJsonObject();
                  out.put(BuiltInRegistries.ITEM.get(rl), new Warmth(
                     f(o, "insulation", 0F, 6F), f(o, "wind", 0F, 0.95F), f(o, "water", 0F, 0.95F)
                  ));
               }
            } catch (RuntimeException ex) {
               NutritionTable.LOG.warn("Frontier survival: bad clothing table {}: {}", file.getKey(), ex.toString());
            }
         });
         overrides = out;
         CACHE.clear();
      }

      private static float f(JsonObject o, String k, float lo, float hi) {
         try {
            float v = o.has(k) ? o.get(k).getAsFloat() : 0F;
            return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 0F;
         } catch (RuntimeException e) {
            return 0F;
         }
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Events {
      private Events() {
      }

      @SubscribeEvent
      public static void reload(AddReloadListenerEvent e) {
         e.addListener(new Loader());
      }
   }
}
