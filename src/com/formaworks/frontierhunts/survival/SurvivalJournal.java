package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.journal.HunterSkills;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.JournalPages;
import com.formaworks.frontierhunts.journal.Perk;
import com.formaworks.frontierhunts.journal.Stat;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.survival.item.GarmentItem;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * [integ4] Frontier Survival inside the Hunter's Journal, and the journal's Woodcraft perks inside Survival.
 * <ul>
 * <li>a "Survival" journal page ({@link JournalPages}): server data = lifetime stats, seasonal yield outlook, difficulty,
 *     rules and the hunter's survival perks; the client view ({@code survival.client.SurvivalJournalView}) adds the
 *     live meters and body temperature from the synced HUD state;</li>
 * <li>the journal checklist's Survival entries driven by Survival: wear furs ({@link Stat#SURV_FURS}), put meat by
 *     ({@link Stat#SURV_PRESERVED}: {@link SurvivalApi.Stats#preserved()} or preserved meat carried) and see a winter night
 *     through ({@link Stat#SURV_WINTER_NIGHTS});</li>
 * <li>perk hooks used by {@link SurvivalService}: Thick Skin ({@link #coldMultiplier}) and Provider ({@link #gameNutrition}).
 *     Trail Legs needs no hook: it refunds vanilla exhaustion, which is what Survival's work drain reads.</li>
 * </ul>
 */
public final class SurvivalJournal {
   private static final Logger LOG = LogUtils.getLogger();
   /** player -> the day index of the winter night they were present for at dusk (-1 none) */
   private static final Map<UUID, Long> NIGHT = new HashMap<>();
   /** night: 13000..23000 day ticks; "at dusk" means seen in its first in-game 1.5 h */
   private static final long DUSK = 13000L, DAWN = 23000L, DUSK_GRACE = 1500L;

   private SurvivalJournal() {
   }

   // ============================================================================================ perk hooks

   /** Multiplier on how fast the body loses heat in the cold (Thick Skin 0.7 at full strength). */
   static float coldMultiplier(ServerPlayer p) {
      try {
         return (float)Math.max(0.3, Math.min(1.0, HunterSkills.coldExposure(p)) * com.formaworks.frontierhunts.campcook.MealBuffs.cold(p)); // [licence] Camp Warmth meal
      } catch (RuntimeException e) {
         return 1F;
      }
   }

   /** Multiplier on the protein / fat / energy wild game gives (Provider 1.25 at full strength). */
   static float gameNutrition(ServerPlayer p) {
      try {
         return (float)Math.max(1.0, Math.min(1.5, HunterSkills.nutrition(p)));
      } catch (RuntimeException e) {
         return 1F;
      }
   }

   // ============================================================================================ journal page

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void setup(FMLCommonSetupEvent e) {
         e.enqueueWork(() -> JournalPages.register("survival", "journal.frontierhunts.page.survival", "frontierhunts:fur_hat", 50, SurvivalJournal::page));
      }
   }

   /** The page's server data (a few hundred bytes). */
   static CompoundTag page(ServerPlayer p) {
      CompoundTag t = new CompoundTag();
      SurvivalConfig.Difficulty d = SurvivalConfig.difficulty();
      t.putByte("mode", (byte)d.ordinal());
      t.putBoolean("spoil", SurvivalConfig.spoilage());
      t.putBoolean("temp", SurvivalConfig.temperature());
      t.putBoolean("lean", SurvivalConfig.leanSeasons());
      t.putShort("drain", (short)Math.round(SurvivalConfig.drainScale() * 100F));
      SurvivalApi.Stats s = SurvivalApi.stats(p);
      t.putInt("meals", s.meals());
      t.putInt("game", s.gameMeals());
      t.putInt("fish", s.fishMeals());
      t.putInt("farm", s.farmMeals());
      t.putInt("spoiledEaten", s.spoiledEaten());
      t.putInt("spoiledLost", s.spoiledLost());
      t.putInt("preserved", s.preserved());
      t.putInt("frozen", s.frozenToDeath());
      t.putInt("coldNights", s.coldNights());
      t.putFloat("coldest", s.coldestBodyHeat());
      t.putLong("vigorS", s.secondsWithVigor());
      t.putLong("hungryS", s.secondsHungry());
      t.putInt("winterNights", JournalApi.counter(p, Stat.SURV_WINTER_NIGHTS));
      SurvivalApi.SeasonYield y = SurvivalApi.seasonYield(p.serverLevel());
      t.putByte("phase", (byte)y.phase());
      t.putFloat("cond", y.condition());
      t.putFloat("meat", y.meatMultiplier());
      t.putByte("deerFat", (byte)Math.min(127, y.typicalDeerFat()));
      t.putFloat("denial", y.spawnDenial());
      t.putFloat("yearPos", (float)SeasonClock.yearPosition(p.serverLevel()));
      int perks = 0;
      int mask = HunterSkills.mask(p);
      for (Perk k : new Perk[]{Perk.TRAIL_LEGS, Perk.PROVIDER, Perk.THICK_SKIN}) {
         if (k.in(mask)) {
            perks |= k.bit();
         }
      }
      t.putInt("perks", perks);
      t.putFloat("coldMul", coldMultiplier(p));
      t.putFloat("gameMul", gameNutrition(p));
      return t;
   }

   // ============================================================================================ checklist counters

   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Events {
      private Events() {
      }

      @SubscribeEvent
      public static void tick(PlayerTickEvent.Post e) {
         if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator() || (p.tickCount + (p.getId() & 63)) % 100 != 37) {
            return;
         }
         try {
            check(p);
         } catch (RuntimeException ex) {
            LOG.debug("Frontier Hunts survival: journal check failed", ex);
         }
      }

      @SubscribeEvent
      public static void died(LivingDeathEvent e) {
         if (e.getEntity() instanceof ServerPlayer p) {
            NIGHT.remove(p.getUUID());
         }
      }

      @SubscribeEvent
      public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
         NIGHT.remove(e.getEntity().getUUID());
      }

      @SubscribeEvent
      public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
         NIGHT.remove(e.getEntity().getUUID());
      }
   }

   static void check(ServerPlayer p) {
      // dressed for the cold: any hide / fur garment, or any worn piece with a fur lining or mittens sewn in
      if (JournalApi.counter(p, Stat.SURV_FURS) == 0) {
         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(slot);
            if (!s.isEmpty() && (s.getItem() instanceof GarmentItem || Clothing.lining(s) != 0)) {
               JournalApi.count(p, Stat.SURV_FURS, 1);
               break;
            }
         }
      }
      // meat put by: lifetime preserved pieces, or what the hunter carries now (smoked at the smokehouse, salt-cured, jerky, pemmican)
      int have = JournalApi.counter(p, Stat.SURV_PRESERVED);
      int preserved = Math.max(SurvivalApi.stats(p).preserved(), carriedPreserved(p));
      if (preserved > have) {
         JournalApi.count(p, Stat.SURV_PRESERVED, preserved - have);
      }
      winterNight(p);
   }

   static int carriedPreserved(ServerPlayer p) {
      int n = 0;
      var inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.isEmpty()) {
            continue;
         }
         if (s.is(SurvivalContent.JERKY.get()) || s.is(SurvivalContent.PEMMICAN.get())) {
            n += s.getCount();
         } else {
            Freshness f = Perishable.get(s);
            if (f != null && f.cure() != SurvivalMath.RAW) {
               n += s.getCount();
            }
         }
      }
      return n;
   }

   /** A winter night seen through: present (alive, in the overworld) at dusk and still alive at dawn. Sleeping counts. */
   static void winterNight(ServerPlayer p) {
      ServerLevel level = p.serverLevel();
      UUID id = p.getUUID();
      if (level.dimension() != Level.OVERWORLD) {
         NIGHT.remove(id);
         return;
      }
      long time = level.getDayTime();
      long day = Math.floorDiv(time, 24000L);
      long tod = Math.floorMod(time, 24000L);
      boolean night = tod >= DUSK && tod < DAWN;
      Long armed = NIGHT.get(id);
      if (night) {
         if (armed == null && tod < DUSK + DUSK_GRACE && SeasonClock.season(level) == SeasonClock.Season.WINTER) {
            NIGHT.put(id, day);
         } else if (armed != null && armed != day) {
            NIGHT.remove(id); // time was moved around; start over
         }
         return;
      }
      if (armed == null) {
         return;
      }
      NIGHT.remove(id);
      // not night now: either the grey before dawn of the same day index, or the next morning (also after sleeping)
      if (day == armed || day == armed + 1L) {
         JournalApi.count(p, Stat.SURV_WINTER_NIGHTS, 1);
         JournalApi.note(p, "@journal.frontierhunts.note.winter_night");
      }
   }
}
