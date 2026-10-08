package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * [survival] Data-driven nutrition table.
 *
 * Datapack files {@code data/<ns>/survival/foods/*.json}: {@code {"entries": {"minecraft:bread": {"protein": 4, "fat": 1,
 * "energy": 22, "source": "crop"}, "#c:foods/raw_meat": {..., "shelf_days": 3, "raw": true}}}}. Item entries beat tag
 * entries, which beat the built-in heuristic (food component size, item name, common tags) used for any other mod's food.
 * Fields: protein, fat, energy (meter points, a meter is 100), source (game | fish | farm | crop | forage), shelf_days
 * (0 = keeps forever), raw, dry (drying rack makes jerky), spoiled (eating it makes you sick).
 *
 * The server resolves lazily (tags must be bound) and sends the whole resolved table to clients on login and after a
 * reload, so tooltips match the server exactly.
 */
public final class NutritionTable {
   static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
   private static final Gson GSON = new Gson();
   private static volatile Map<Item, FoodValues> items = Map.of();
   private static volatile List<Map.Entry<TagKey<Item>, FoodValues>> tags = List.of();
   private static final Map<Item, FoodValues> RESOLVED = new ConcurrentHashMap<>();
   /** Client copy (set by the sync payload). */
   private static volatile Map<Item, FoodValues> client = Map.of();

   private NutritionTable() {
   }

   /** Server (or singleplayer common) lookup; null when the item is no food we know. */
   public static FoodValues get(Item item) {
      if (item == null || item == Items.AIR) {
         return null;
      }
      FoodValues v = RESOLVED.get(item);
      if (v == null) {
         v = resolve(item);
         RESOLVED.put(item, v);
      }
      return v == FoodValues.NONE ? null : v;
   }

   public static FoodValues get(ItemStack stack) {
      return stack == null || stack.isEmpty() ? null : get(stack.getItem());
   }

   /** Lookup on either side: clients use the table the server sent. */
   public static FoodValues get(ItemStack stack, boolean clientSide) {
      if (stack == null || stack.isEmpty()) {
         return null;
      }
      if (clientSide) {
         FoodValues v = client.get(stack.getItem());
         return v == FoodValues.NONE ? null : v;
      }
      return get(stack.getItem());
   }

   /** Either side without knowing which: the server's table when a client copy is present (it came from the server). */
   public static FoodValues any(Item item) {
      Map<Item, FoodValues> c = client;
      if (!c.isEmpty()) {
         FoodValues v = c.get(item);
         return v == FoodValues.NONE ? null : v;
      }
      return get(item);
   }

   private static FoodValues resolve(Item item) {
      FoodValues v = items.get(item);
      if (v != null) {
         return v;
      }
      Holder<Item> holder = item.builtInRegistryHolder();
      for (Map.Entry<TagKey<Item>, FoodValues> e : tags) {
         if (holder.is(e.getKey())) {
            return e.getValue();
         }
      }
      FoodValues h = heuristic(item);
      return h == null ? FoodValues.NONE : h;
   }

   // ============================================================================================ heuristic (other mods)

   private static final String[] GAME = {"venison", "game", "wild", "elk", "moose", "deer", "bison", "bear", "boar", "pronghorn", "jerky", "pemmican", "rabbit", "grouse", "duck", "pheasant", "quail", "goose"};
   private static final String[] MEAT = {"beef", "pork", "mutton", "lamb", "chicken", "meat", "steak", "bacon", "ham", "sausage", "turkey", "patty", "burger", "chop", "rib", "loin", "brisket", "jerky", "rabbit", "venison"};
   private static final String[] FISH = {"fish", "salmon", "cod", "tuna", "trout", "bass", "carp", "shrimp", "crab", "lobster", "clam", "squid", "eel", "sushi", "perch", "pike", "catfish"};
   private static final String[] SWEET = {"cake", "cookie", "pie", "honey", "chocolate", "candy", "sugar", "donut", "muffin", "tart", "pudding", "jam"};
   private static final String[] FORAGE = {"berry", "berries", "mushroom", "kelp", "acorn", "nut", "seed"};
   private static final TagKey<Item> RAW_MEAT = tag("c", "foods/raw_meat");
   private static final TagKey<Item> COOKED_MEAT = tag("c", "foods/cooked_meat");
   private static final TagKey<Item> RAW_FISH = tag("c", "foods/raw_fish");
   private static final TagKey<Item> COOKED_FISH = tag("c", "foods/cooked_fish");

   private static FoodValues heuristic(Item item) {
      FoodProperties food = item.components().get(DataComponents.FOOD);
      if (food == null) {
         return null;
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      String path = id.getPath().toLowerCase(Locale.ROOT);
      Holder<Item> h = item.builtInRegistryHolder();
      float n = Math.max(0.5F, food.nutrition());
      boolean fish = h.is(RAW_FISH) || h.is(COOKED_FISH) || any(path, FISH);
      boolean meat = !fish && (h.is(RAW_MEAT) || h.is(COOKED_MEAT) || any(path, MEAT));
      boolean raw = h.is(RAW_MEAT) || h.is(RAW_FISH) || path.contains("raw");
      boolean cooked = h.is(COOKED_MEAT) || h.is(COOKED_FISH) || path.contains("cooked") || path.contains("roast") || path.contains("grilled")
         || path.contains("baked") || path.contains("fried") || path.contains("smoked");
      if (!raw && !cooked && (meat || fish)) {
         raw = food.saturation() < n * 0.5F; // raw meat is low in saturation
      }
      if (fish) {
         return new FoodValues(n * 2.4F, n * 0.8F, n * 0.3F, FoodValues.Source.FISH, raw ? 2F : 3F, raw, false, false);
      }
      if (meat) {
         boolean game = any(path, GAME);
         boolean dried = path.contains("jerky") || path.contains("pemmican") || path.contains("dried");
         return new FoodValues(
            n * 2.5F, n * 1.2F, n * 0.6F, game ? FoodValues.Source.GAME : FoodValues.Source.FARM, dried ? 30F : (raw ? 3F : 5F), raw && !dried, raw && !dried, false
         );
      }
      if (any(path, SWEET)) {
         return new FoodValues(n * 0.3F, n * 0.8F, n * 2.6F, FoodValues.Source.CROP, 0F, false, false, false);
      }
      FoodValues.Source src = any(path, FORAGE) ? FoodValues.Source.FORAGE : FoodValues.Source.CROP;
      return new FoodValues(n * 0.4F, n * 0.2F, n * 2.6F, src, 0F, false, false, false);
   }

   private static boolean any(String path, String[] words) {
      for (String w : words) {
         if (path.contains(w)) {
            return true;
         }
      }
      return false;
   }

   private static TagKey<Item> tag(String ns, String path) {
      return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ns, path));
   }

   // ============================================================================================ loading

   static FoodValues parse(JsonObject o) {
      float p = num(o, "protein"), f = num(o, "fat"), e = num(o, "energy");
      FoodValues.Source src = o.has("source") ? FoodValues.Source.parse(o.get("source").getAsString()) : FoodValues.Source.CROP;
      float shelf = Math.max(0F, Math.min(600F, num(o, "shelf_days")));
      return new FoodValues(
         clamp(p), clamp(f), clamp(e), src, shelf, bool(o, "raw"), bool(o, "dry"), bool(o, "spoiled")
      );
   }

   private static float clamp(float v) {
      return Float.isFinite(v) ? Math.max(0F, Math.min(100F, v)) : 0F;
   }

   private static float num(JsonObject o, String k) {
      try {
         return o.has(k) ? o.get(k).getAsFloat() : 0F;
      } catch (RuntimeException e) {
         return 0F;
      }
   }

   private static boolean bool(JsonObject o, String k) {
      try {
         return o.has(k) && o.get(k).getAsBoolean();
      } catch (RuntimeException e) {
         return false;
      }
   }

   static final class Loader extends SimpleJsonResourceReloadListener {
      Loader() {
         super(GSON, "survival/foods");
      }

      @Override
      protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
         Map<Item, FoodValues> it = new IdentityHashMap<>();
         Map<TagKey<Item>, FoodValues> tg = new HashMap<>();
         List<TagKey<Item>> order = new ArrayList<>();
         files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(file -> {
            try {
               JsonObject root = file.getValue().getAsJsonObject();
               JsonObject entries = root.has("entries") ? root.getAsJsonObject("entries") : root;
               for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                  if (!e.getValue().isJsonObject()) {
                     continue;
                  }
                  String key = e.getKey();
                  FoodValues v = parse(e.getValue().getAsJsonObject());
                  if (key.startsWith("#")) {
                     ResourceLocation rl = ResourceLocation.tryParse(key.substring(1));
                     if (rl != null) {
                        TagKey<Item> t = TagKey.create(Registries.ITEM, rl);
                        if (!tg.containsKey(t)) {
                           order.add(t);
                        }
                        tg.put(t, v);
                     }
                  } else {
                     ResourceLocation rl = ResourceLocation.tryParse(key);
                     if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                        it.put(BuiltInRegistries.ITEM.get(rl), v);
                     }
                  }
               }
            } catch (RuntimeException ex) {
               LOG.warn("Frontier survival: bad food table {}: {}", file.getKey(), ex.toString());
            }
         });
         List<Map.Entry<TagKey<Item>, FoodValues>> tl = new ArrayList<>();
         for (TagKey<Item> t : order) {
            tl.add(Map.entry(t, tg.get(t)));
         }
         items = it;
         tags = tl;
         RESOLVED.clear();
         LOG.info("Frontier survival: {} food entries, {} tag entries", it.size(), tl.size());
      }
   }

   /** Every item the server knows nutrition for (resolved now; tags must be bound). */
   public static Map<Item, FoodValues> resolveAll() {
      Map<Item, FoodValues> out = new IdentityHashMap<>();
      for (Item item : BuiltInRegistries.ITEM) {
         FoodValues v = get(item);
         if (v != null) {
            out.put(item, v);
         }
      }
      return out;
   }

   static void setClient(Map<Item, FoodValues> table) {
      client = table;
   }

   /** Clears the client copy (disconnect). */
   public static void clearClient() {
      client = Map.of();
   }

   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Events {
      private Events() {
      }

      @SubscribeEvent
      public static void reload(AddReloadListenerEvent e) {
         e.addListener(new Loader());
      }

      @SubscribeEvent
      public static void sync(OnDatapackSyncEvent e) {
         if (e.getPlayer() == null) {
            RESOLVED.clear(); // a /reload: tags may have changed
         }
         SurvivalNetwork.sendTable(e.getPlayer()); // also fires for each joining player
      }
   }
}
