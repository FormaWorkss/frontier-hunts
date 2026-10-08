package com.formaworks.frontierhunts.regions.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntDefinitions;
import com.formaworks.frontierhunts.client.JournalScreen;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.season.SeasonState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * [regions] What the arrival card says about a place: the translated biome name (any mod's biome: its "biome.ns.path" lang
 * key, else a tidy title-cased id), the reserve region from the mod's synced region registry (or the dimension name
 * outside the regions) and the notable game from the mod's species habitat biome tags (biome tags are synced to clients,
 * so this works the same on a dedicated server). Static parts are cached per biome; the season is added when a card fires.
 */
public final class PlaceInfo {
   /** One place. {@code region} is the trigger's region key and the card's spaced headline. */
   public record Place(String biomeId, String biome, String region, String game) {
   }

   /** Habitat tags in announcement order (big game first) and their entity lang keys. */
   private static final String[][] GAME = {
      {"moose_habitat", "moose"},
      {"elk_habitat", "elk"},
      {"whitetail_habitat", "whitetail"},
      {"wildlife2026/bison", "bison"},
      {"wildlife2026/pronghorn", "pronghorn"},
      {"wildlife2026/grizzly", "grizzly"},
      {"wildlife2026/polar_bear", "polar_bear"},
      {"wildlife2026/black_bear", "black_bear"},
      {"wildlife2026/cougar", "cougar"},
      {"wildlife2026/lion", "lion"},
      {"wildlife2026/cheetah", "cheetah"},
      {"wildlife2026/panther", "panther"},
      {"wildlife2026/wolf", "wolf"},
      {"wildlife2026/boar", "boar"},
      {"wildlife2026/coyote", "coyote"},
      {"wildlife2026/grouse", "grouse"},
      {"wildlife2026/duck", "duck"}
   };
   private static final List<TagKey<Biome>> TAGS = new ArrayList<>();
   private static final Map<ResourceKey<Biome>, Place> CACHE = new HashMap<>();
   private static String cacheLang = "";

   static {
      for (String[] g : GAME) {
         TAGS.add(TagKey.create(Registries.BIOME, FrontierHunts.id(g[0])));
      }
   }

   private PlaceInfo() {
   }

   /** Drop cached names (logout, tag or language reload). */
   public static void clear() {
      CACHE.clear();
   }

   /** The place at {@code pos}, or null while its chunk is not loaded. */
   public static Place at(ClientLevel level, BlockPos pos) {
      if (level == null || !level.hasChunkAt(pos)) {
         return null;
      }
      Holder<Biome> holder = level.getBiome(pos);
      ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
      if (key == null) {
         return null;
      }
      String lang = Minecraft.getInstance().getLanguageManager().getSelected();
      if (!lang.equals(cacheLang)) {
         CACHE.clear(); // names are translated: a language switch re-resolves them
         cacheLang = lang;
      }
      Place p = CACHE.get(key);
      if (p == null) {
         if (CACHE.size() > 512) {
            CACHE.clear();
         }
         p = resolve(level, holder, key);
         CACHE.put(key, p);
      }
      return p;
   }

   private static Place resolve(ClientLevel level, Holder<Biome> holder, ResourceKey<Biome> key) {
      ResourceLocation id = key.location();
      String region = null;
      if (level.dimension() == Level.OVERWORLD) {
         try {
            HuntDefinitions.Region r = HuntDefinitions.region(level.registryAccess(), holder);
            region = r == null ? null : r.name(); // unmatched overworld biomes give the mod's own "Uncharted country"
         } catch (RuntimeException e) {
            region = null; // region registry missing (should not happen with the mod on both sides)
         }
      }
      if (region == null || region.isBlank()) {
         region = dimensionName(level.dimension());
      }
      StringBuilder game = new StringBuilder();
      if (level.dimension() == Level.OVERWORLD) {
         int n = 0;
         for (int i = 0; i < TAGS.size() && n < 3; i++) {
            if (holder.is(TAGS.get(i))) {
               if (n++ > 0) {
                  game.append("  ·  ");
               }
               game.append(I18n.get("entity.frontierhunts." + GAME[i][1]));
            }
         }
      }
      return new Place(id.toString(), biomeName(id), region, game.toString());
   }

   /** Translated biome name for any mod's biome. */
   public static String biomeName(ResourceLocation id) {
      String key = "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.');
      if (I18n.exists(key)) {
         return I18n.get(key);
      }
      return JournalScreen.biomeName(id.toString());
   }

   public static String dimensionName(ResourceKey<Level> dim) {
      ResourceLocation id = dim.location();
      String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
      if (I18n.exists(key)) {
         return I18n.get(key);
      }
      if (dim == Level.NETHER) {
         return I18n.get("regions.frontierhunts.dim.nether");
      }
      if (dim == Level.END) {
         return I18n.get("regions.frontierhunts.dim.end");
      }
      if (dim == Level.OVERWORLD) {
         return I18n.get("regions.frontierhunts.dim.overworld");
      }
      return JournalScreen.biomeName(id.toString());
   }

   /** "Early Fall" etc., or "" when seasons are off or the level has none. */
   public static String season(ClientLevel level) {
      if (level == null || level.dimension() != Level.OVERWORLD || !SeasonState.enabled()) {
         return "";
      }
      try {
         float pr = SeasonClock.progress(level);
         String phase = pr < 0.34F ? "early" : pr < 0.67F ? "mid" : "late";
         String s = SeasonClock.season(level).name().toLowerCase(Locale.ROOT);
         return I18n.get("regions.frontierhunts.season." + phase, I18n.get("regions.frontierhunts.season." + s));
      } catch (RuntimeException e) {
         return "";
      }
   }

   /** The subtitle line: the season only ([1.1.2] the animal list was dropped from the cards). */
   public static String subtitle(ClientLevel level, Place p) {
      return season(level);
   }
}
