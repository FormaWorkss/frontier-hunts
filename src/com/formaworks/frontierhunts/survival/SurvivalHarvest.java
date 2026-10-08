package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * [survival] Lean seasons: what an animal gives depends on its condition, and its condition on the season (fattest in
 * October, leanest in March, see {@link SurvivalMath#condition}). Fat trimmings, heart and liver, and species hides for
 * clothing. Late winter also turns away a share of natural spawns of game animals (thin herds).
 *
 * Hook lines in {@code hunting/Whitetail.harvest} (marked [survival]) call {@link #meat}, {@link #hide} and
 * {@link #extras}; 2026 wildlife drops are rewritten here through LivingDropsEvent.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class SurvivalHarvest {
   private SurvivalHarvest() {
   }

   public static double year(Level level) {
      return SeasonClock.yearPosition(level);
   }

   /** Seasonal condition (1.0 when lean seasons are off). */
   public static float season(Level level) {
      return SurvivalConfig.leanSeasons() ? SurvivalMath.condition(year(level)) : 1F;
   }

   public static float condition(Whitetail w) {
      int trait = 50;
      try {
         trait = w.traits().condition();
      } catch (RuntimeException ignored) {
      }
      return SurvivalConfig.leanSeasons() ? SurvivalMath.animalCondition(year(w.level()), trait) : 1F;
   }

   // ============================================================================================ Whitetail family hooks

   /** [hook: Whitetail.harvest] venison / quarter count scaled by condition. */
   public static int meat(Whitetail w, int count) {
      if (!SurvivalConfig.mode().on()) {
         return count;
      }
      return Math.max(1, Math.round(count * SurvivalMath.meatYield(condition(w))));
   }

   /** [hook: Whitetail.harvest] elk and moose give a heavy hide (robes, bedrolls); whitetail the buckskin hide. */
   public static ItemStack hide(Whitetail w, int count) {
      if (w.species() != GameSpecies.WHITETAIL) {
         return new ItemStack(SurvivalContent.HEAVY_HIDE.get(), Math.max(1, count - 1));
      }
      return new ItemStack(HuntContent.DEER_HIDE.get(), count);
   }

   /** [hook: Whitetail.harvest] heart & liver and fat trimmings, plus a condition note to the hunter. */
   public static void extras(Whitetail w, ServerPlayer hunter) {
      if (!SurvivalConfig.mode().on()) {
         return;
      }
      float cond = condition(w);
      boolean big = w.species() != GameSpecies.WHITETAIL;
      int fat = SurvivalMath.fatYield(cond, w.massKg(), 1.0F);
      List<ItemStack> out = new ArrayList<>();
      out.add(new ItemStack(SurvivalContent.ORGAN_MEAT.get(), big ? 2 : 1));
      if (fat > 0) {
         out.add(new ItemStack(SurvivalContent.GAME_FAT.get(), fat));
      }
      for (ItemStack s : out) {
         ItemEntity ie = w.spawnAtLocation(s);
         if (ie != null) {
            ie.setTarget(hunter.getUUID());
            ie.setNoPickUpDelay();
         }
      }
      hunter.sendSystemMessage(note(cond, fat));
   }

   static Component note(float cond, int fat) {
      String key = cond >= 1.1F ? "prime" : (cond >= 0.95F ? "good" : (cond >= 0.84F ? "thin" : "lean"));
      return Component.translatable("survival.frontierhunts.harvest." + key, fat).withStyle(ChatFormatting.GRAY);
   }

   // ============================================================================================ 2026 wildlife drops

   private static int mass(WildlifeSpecies s) {
      return switch (s) {
         case BISON -> 700;
         case POLAR_BEAR -> 400;
         case GRIZZLY -> 260;
         case BLACK_BEAR -> 120;
         case BOAR -> 90;
         case LION -> 180;
         case PRONGHORN -> 50;
         default -> 40;
      };
   }

   private static boolean bear(WildlifeSpecies s) {
      return s == WildlifeSpecies.BLACK_BEAR || s == WildlifeSpecies.GRIZZLY || s == WildlifeSpecies.POLAR_BEAR;
   }

   private static boolean furbearer(WildlifeSpecies s) {
      return s == WildlifeSpecies.WOLF || s == WildlifeSpecies.COYOTE || s == WildlifeSpecies.COUGAR || s == WildlifeSpecies.PANTHER
         || s == WildlifeSpecies.LION || s == WildlifeSpecies.CHEETAH;
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void drops(LivingDropsEvent e) {
      if (!(e.getEntity() instanceof WildlifeMob m) || m.level().isClientSide) {
         return;
      }
      if (!SurvivalConfig.mode().on()) { // [recipes] hides and pelts are crafting materials: they drop with Frontier Survival off too
         com.formaworks.frontierhunts.recipes.PeltDrops.apply(e, m);
         return;
      }
      WildlifeSpecies s = m.species;
      float cond = season(m.level());
      float meat = SurvivalMath.meatYield(cond);
      for (ItemEntity ie : e.getDrops()) {
         ItemStack st = ie.getItem();
         Item to = null;
         float scale = 1F;
         if (st.is(Items.BEEF)) {
            to = bear(s) ? SurvivalContent.BEAR_MEAT.get() : SurvivalContent.GAME_MEAT.get();
            scale = meat * (s == WildlifeSpecies.BISON ? 1.5F : 1F);
         } else if (st.is(Items.PORKCHOP)) {
            to = SurvivalContent.GAME_MEAT.get();
            scale = meat;
         } else if (st.is(Items.CHICKEN)) {
            to = SurvivalContent.WILD_FOWL.get();
         } else if (st.is(HuntContent.VENISON.get())) {
            to = HuntContent.VENISON.get();
            scale = meat;
         } else if (st.is(Items.LEATHER)) {
            if (s == WildlifeSpecies.BISON) {
               to = SurvivalContent.HEAVY_HIDE.get();
            } else if (bear(s)) {
               to = SurvivalContent.BEAR_PELT.get();
               scale = 0F; // exactly one pelt
            } else if (furbearer(s)) {
               to = SurvivalContent.FUR_PELT.get();
               scale = 0F;
            } else if (s == WildlifeSpecies.PRONGHORN) {
               to = HuntContent.DEER_HIDE.get();
            }
         }
         if (to != null) {
            int n = scale <= 0F ? 1 : Math.max(1, Math.round(st.getCount() * scale));
            ie.setItem(new ItemStack(to, Math.min(n, 64)));
         }
      }
      boolean pelt = e.getDrops().stream().anyMatch(ie -> ie.getItem().is(SurvivalContent.BEAR_PELT.get()) || ie.getItem().is(SurvivalContent.FUR_PELT.get()));
      if (!pelt && (bear(s) || furbearer(s))) {
         add(e, m, new ItemStack(bear(s) ? SurvivalContent.BEAR_PELT.get() : SurvivalContent.FUR_PELT.get()));
      }
      if (s.bird || furbearer(s)) {
         return;
      }
      float fatty = bear(s) ? 2.5F : (s == WildlifeSpecies.BOAR ? 1.4F : 1F);
      int fat = SurvivalMath.fatYield(SurvivalConfig.leanSeasons() ? cond : 1F, mass(s), fatty);
      if (fat > 0) {
         add(e, m, new ItemStack(SurvivalContent.GAME_FAT.get(), Math.min(fat, 16)));
      }
      add(e, m, new ItemStack(SurvivalContent.ORGAN_MEAT.get(), mass(s) >= 200 ? 2 : 1));
   }

   private static void add(LivingDropsEvent e, Entity m, ItemStack s) {
      ItemEntity ie = new ItemEntity(m.level(), m.getX(), m.getY() + 0.3, m.getZ(), s);
      ie.setDefaultPickUpDelay();
      e.getDrops().add(ie);
   }

   // ============================================================================================ thin herds

   @SubscribeEvent
   public static void spawn(FinalizeSpawnEvent e) {
      MobSpawnType t = e.getSpawnType();
      if ((t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION) || !SurvivalConfig.leanSeasons()) {
         return;
      }
      Entity ent = e.getEntity();
      boolean game = ent instanceof Whitetail
         || ent instanceof WildlifeMob wm && !furbearer(wm.species) && !bear(wm.species);
      if (!game) {
         return;
      }
      ServerLevel sl = e.getLevel().getLevel();
      float deny = SurvivalMath.spawnDenial(year(sl));
      if (deny > 0F && e.getLevel().getRandom().nextFloat() < deny) {
         e.setSpawnCancelled(true);
      }
   }
}
