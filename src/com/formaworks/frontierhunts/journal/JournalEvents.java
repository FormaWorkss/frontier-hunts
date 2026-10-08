package com.formaworks.frontierhunts.journal;

import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.camps.CampRegistry;
import com.formaworks.frontierhunts.ecology.KillSiteStore;
import com.formaworks.frontierhunts.ecology.bones.BoneBlock;
import com.formaworks.frontierhunts.ecology.bones.ShedAntlerBlock;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.guide.FieldSchoolData;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.prone.Prone;
import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.vital.ShotVitals;
import com.formaworks.frontierhunts.vital.WildlifeVitals;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import com.formaworks.frontierhunts.weather.WeatherKind;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * [journal] Game-bus wiring: login/logout, the staggered per-player field checks (walking, sightings, stalking, kill
 * sites, gear, seasons, weather), projectile shots, wildlife hits and deaths, sign/bone right-clicks, placements,
 * mounts, eating, Woodcraft perks and the {@code /frontierhunts journal} commands.
 *
 * <p>Cost per player: one position delta a second; one AABB query (40 blocks, animals only) + up to 3 LOS rays every
 * 2 s; one inventory scan + ledger reads every 5 s; kill-site list scan every 2 s. Everything else is event driven.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class JournalEvents {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Map<UUID, Live> LIVE = new HashMap<>();
   private static final Map<LivingEntity, Boolean> CALM = new WeakHashMap<>();
   private static Map<Item, String> gearItems;
   private static long serverTick;
   private static final double SIGHT = 40.0;
   private static final double STALK = 12.0;

   /** Transient per-player state (never saved). */
   static final class Live {
      double lx, ly, lz;
      boolean hasPos;
      ResourceLocation dim;
      final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
      final LinkedHashSet<UUID> stalked = new LinkedHashSet<>();
      final LinkedHashSet<UUID> readMarks = new LinkedHashSet<>();
      final LinkedHashSet<UUID> sites = new LinkedHashSet<>();
      final LinkedHashSet<Long> blocksRead = new LinkedHashSet<>();
      UUID stalkTarget;
      int stalkPasses;
      long lastStalkXp = Long.MIN_VALUE / 2;
      long lastSignXp = Long.MIN_VALUE / 2;
      long photoDay = -1L;
      int photoXp;
      long walkDay = -1L;
      int walkXp;
      int walkAcc;
      int blizzardTicks;
      int winterTicks;
      float lastExhaustion = -1.0F;
      int lastFrozen;
      long lastShotTick = Long.MIN_VALUE;
      int lastBiomes = -1;
   }

   private JournalEvents() {
   }

   static Live live(ServerPlayer p) {
      return LIVE.computeIfAbsent(p.getUUID(), k -> new Live());
   }

   private static <T> void capped(LinkedHashSet<T> set, int max) {
      while (set.size() > max) {
         Iterator<T> it = set.iterator();
         it.next();
         it.remove();
      }
   }

   // ============================================================================================ lifecycle

   @SubscribeEvent
   public static void starting(ServerStartingEvent e) {
      LIVE.clear();
      HunterSkills.clear();
      JournalService.clear();
      ProgressStore.forget();
      serverTick = 0L;
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      LIVE.clear();
      CALM.clear();
      HunterSkills.clear();
      JournalService.clear();
      ProgressStore.forget();
   }

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      try {
         HunterRecord r = ProgressStore.get(p.server).record(p.getUUID());
         JournalService.perks(p, r);
         expireWounds(p, r);
         JournalService.seasonCheck(p, r);
         if (r.rankShown < Rank.of(r.totalXp()).ordinal()) {
            r.rankShown = Rank.of(r.totalXp()).ordinal(); // no toast for ranks reached while offline (e.g. admin XP)
         }
         r.changed = true;
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts journal: login setup failed for {}", p.getGameProfile().getName(), ex);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      UUID id = e.getEntity().getUUID();
      LIVE.remove(id);
      HunterSkills.set(id, 0);
      JournalService.forget(id);
   }

   @SubscribeEvent
   public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         Live l = LIVE.get(p.getUUID());
         if (l != null) {
            l.hasPos = false;
         }
      }
   }

   @SubscribeEvent
   public static void serverTick(ServerTickEvent.Post e) {
      long t = ++serverTick;
      if (t % 20L == 0L) {
         try {
            JournalService.flush(e.getServer());
         } catch (RuntimeException ex) {
            LOG.warn("Frontier Hunts journal: sync failed", ex);
         }
      }
   }

   // ============================================================================================ per-player field checks

   @SubscribeEvent
   public static void playerTick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) {
         return;
      }
      int phase = (p.tickCount + (p.getId() & 15));
      try {
         Live l = live(p);
         int mask = HunterSkills.mask(p);
         if (phase % 10 == 0 && Perk.TRAIL_LEGS.in(mask)) {
            trailLegs(p, l);
         }
         if (phase % 4 == 0 && Perk.THICK_SKIN.in(mask)) {
            thickSkin(p, l);
         }
         if (phase % 20 != 0) {
            return;
         }
         HunterRecord r = JournalService.record(p);
         if (r == null) {
            l.hasPos = false;
            return;
         }
         ServerLevel level = p.serverLevel();
         travel(p, r, l, level);
         if (phase % 40 == 0) {
            sightings(p, r, l, level);
            killSites(p, r, l, level);
         }
         if (phase % 100 == 0) {
            gear(p, r);
            ledgers(p, r, l);
            weather(p, r, l, level);
            JournalService.seasonCheck(p, r);
            expireWounds(p, r);
         }
      } catch (RuntimeException ex) {
         LOG.debug("Frontier Hunts journal: field check failed", ex);
      }
   }

   private static void travel(ServerPlayer p, HunterRecord r, Live l, ServerLevel level) {
      ResourceLocation dim = level.dimension().location();
      double x = p.getX(), y = p.getY(), z = p.getZ();
      if (l.hasPos && dim.equals(l.dim) && level.dimension() == Level.OVERWORLD) {
         double dx = x - l.lx, dz = z - l.lz;
         double d = Math.sqrt(dx * dx + dz * dz);
         Entity v = p.getVehicle();
         if (d > 0.3 && d < 40.0) {
            if (v == null && !p.getAbilities().flying && !p.isFallFlying() && d < 12.0) {
               int m = (int)Math.round(d);
               if (m > 0) {
                  JournalService.count(p.server, r, p, Stat.WALK_M, m);
                  l.walkAcc += m;
                  if (l.walkAcc >= 100) {
                     long day = level.getGameTime() / 24000L;
                     if (l.walkDay != day) {
                        l.walkDay = day;
                        l.walkXp = 0;
                     }
                     int xp = l.walkAcc / 100;
                     l.walkAcc %= 100;
                     if (l.walkXp < 150) {
                        l.walkXp += xp;
                        JournalService.xp(p, Skill.WOODCRAFT, xp);
                     }
                  }
               }
            } else if (v != null && "frontierhunts:atv".equals(EntityTypeIds.id(v))) {
               JournalService.count(p.server, r, p, Stat.RIDE_M, (int)Math.round(d));
            }
         }
         JournalService.count(p.server, r, p, Stat.FIELD_S, 1);
      }
      l.lx = x;
      l.ly = y;
      l.lz = z;
      l.dim = dim;
      l.hasPos = true;
   }

   /** Animals in view (species "seen" log) and stalking close to an unaware animal. */
   private static void sightings(ServerPlayer p, HunterRecord r, Live l, ServerLevel level) {
      AABB box = p.getBoundingBox().inflate(SIGHT, 16.0, SIGHT);
      List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, a -> a.isAlive() && (a instanceof Whitetail || a instanceof WildlifeMob));
      if (near.isEmpty()) {
         l.stalkTarget = null;
         l.stalkPasses = 0;
         return;
      }
      int rays = 0;
      LivingEntity stalk = null;
      double stalkD = STALK * STALK;
      for (LivingEntity a : near) {
         double d2 = a.distanceToSqr(p);
         if (d2 > SIGHT * SIGHT) {
            continue;
         }
         String sp = species(a);
         if (sp == null) {
            continue;
         }
         if (!l.seen.contains(a.getUUID()) && rays < 3) {
            rays++;
            if (p.hasLineOfSight(a)) {
               l.seen.add(a.getUUID());
               capped(l.seen, 512);
               r.species(sp).seen++;
               JournalService.count(p.server, r, p, Stat.species(sp, "seen"), 1);
               JournalService.dirty(p.server, r);
               com.formaworks.frontierhunts.hunts.HuntHooks.seen(p, a, sp, Math.sqrt(d2)); // [hunts] sighting milestones
            }
         }
         if (d2 < stalkD && unaware(a) && !p.isPassenger()) {
            stalk = a;
            stalkD = d2;
         }
      }
      if (stalk == null) {
         l.stalkTarget = null;
         l.stalkPasses = 0;
         return;
      }
      if (!stalk.getUUID().equals(l.stalkTarget)) {
         l.stalkTarget = stalk.getUUID();
         l.stalkPasses = 1;
         return;
      }
      l.stalkPasses++;
      long now = level.getGameTime();
      if (l.stalkPasses >= 2 && !l.stalked.contains(stalk.getUUID()) && now - l.lastStalkXp >= 600L) {
         l.stalked.add(stalk.getUUID());
         capped(l.stalked, 128);
         l.lastStalkXp = now;
         boolean close = stalkD <= 36.0;
         JournalService.count(p.server, r, p, Stat.STALKS, 1);
         if (close) {
            JournalService.count(p.server, r, p, Stat.STALK_CLOSE, 1);
         }
         JournalService.xp(p, Skill.STALKING, close ? 25 : 12);
      }
   }

   static String species(LivingEntity a) {
      if (a instanceof Whitetail w) {
         return w.species().id;
      }
      return a instanceof WildlifeMob m && m.species != null ? m.species.id : null;
   }

   private static boolean unaware(LivingEntity a) {
      if (a instanceof Whitetail w) {
         return !w.downed() && w.alertness() < 0.3F;
      }
      if (a instanceof WildlifeMob m) {
         int b = m.behavior();
         return b != 5 && b != 6;
      }
      return false;
   }

   private static void killSites(ServerPlayer p, HunterRecord r, Live l, ServerLevel level) {
      List<KillSiteStore.Site> sites = KillSiteStore.of(level).near(p.position(), 14.0);
      for (KillSiteStore.Site s : sites) {
         if (l.sites.add(s.carcass)) {
            capped(l.sites, 64);
            JournalService.count(p.server, r, p, Stat.PREDATOR_SITES, 1);
            JournalService.xp(p, Skill.WOODCRAFT, 30);
            JournalService.note(p, "@journal.frontierhunts.note.killsite|" + s.predator + "|" + s.prey, (byte)4);
         }
      }
   }

   private static Map<Item, String> gearItems() {
      if (gearItems == null) {
         Map<Item, String> m = new IdentityHashMap<>();
         Checklist.GEAR_ITEMS.forEach((id, key) -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
               m.put(BuiltInRegistries.ITEM.get(rl), key);
            }
         });
         gearItems = m;
      }
      return gearItems;
   }

   private static void gear(ServerPlayer p, HunterRecord r) {
      Map<Item, String> m = gearItems();
      var inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (!s.isEmpty()) {
            String key = m.get(s.getItem());
            if (key != null && r.get("gear." + key) == 0) {
               JournalService.count(p.server, r, p, "gear." + key, 1);
            }
         }
      }
   }

   private static void ledgers(ServerPlayer p, HunterRecord r, Live l) {
      MinecraftServer s = p.server;
      try {
         HunterLedger.Hunter h = HunterLedger.get(p.serverLevel()).hunter(p.getUUID());
         int biomes = h.discoveries().size();
         if (l.lastBiomes >= 0 && biomes > l.lastBiomes) {
            JournalService.xp(p, Skill.WOODCRAFT, Math.min(45, 15 * (biomes - l.lastBiomes)));
         }
         l.lastBiomes = biomes;
         JournalService.count(s, r, p, Stat.BIOMES, biomes);
         JournalService.count(s, r, p, Stat.PROVISIONS, h.provisionsDelivered());
      } catch (RuntimeException ignored) {
      }
      try {
         ExpeditionLedger.Hunter h = ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID());
         JournalService.count(s, r, p, Stat.CAMPAIGN, Math.clamp(h.stage, 0, 9));
         JournalService.count(s, r, p, Stat.CONTRACTS, Math.max(0, h.completed));
      } catch (RuntimeException ignored) {
      }
      try {
         FieldSchoolData.Hunter fs = FieldSchoolData.get(s).find(p.getUUID());
         if (fs != null) {
            JournalService.count(s, r, p, Stat.FIELD_SCHOOL, Lesson.requiredDone(fs.done));
         }
      } catch (RuntimeException ignored) {
      }
      try {
         CampRegistry.Camp c = CampRegistry.get(s).campOf(p.getUUID());
         if (c != null) {
            JournalService.count(s, r, p, Stat.CAMP, 1);
            JournalService.count(s, r, p, Stat.CAMP_RANK, Math.max(0, c.tier));
         }
      } catch (RuntimeException ignored) {
      }
   }

   private static void weather(ServerPlayer p, HunterRecord r, Live l, ServerLevel level) {
      if (level.dimension() != Level.OVERWORLD) {
         return;
      }
      BlockPos head = BlockPos.containing(p.getEyePosition());
      boolean outside = level.canSeeSky(head);
      // seasons hunted through
      SeasonClock.Season season = SeasonClock.season(level);
      int bit = 1 << season.ordinal();
      int seasons = r.get("season_mask");
      if ((seasons & bit) == 0) {
         r.counters.put("season_mask", seasons | bit);
         JournalService.count(p.server, r, p, Stat.SEASONS, Integer.bitCount(seasons | bit));
         if (seasons != 0) {
            JournalService.xp(p, Skill.WOODCRAFT, 40);
         }
      }
      // winter in the field
      if (season == SeasonClock.Season.WINTER && outside) {
         l.winterTicks += 100;
         if (l.winterTicks >= 1200) {
            l.winterTicks -= 1200;
            JournalService.count(p.server, r, p, Stat.WINTER_MIN, 1);
            if (r.get(Stat.WINTER_MIN) % 10 == 0) {
               JournalService.xp(p, Skill.WOODCRAFT, 20);
            }
         }
      }
      // blizzard endured outdoors for a minute (once per reserve day)
      float sev = 0.0F;
      try {
         sev = SeasonalWeather.severity(level, p.blockPosition(), WeatherKind.BLIZZARD);
      } catch (RuntimeException ignored) {
      }
      if (sev > 0.35F && outside) {
         l.blizzardTicks += 100;
         long day = level.getGameTime() / 24000L;
         if (l.blizzardTicks >= 1200 && r.blizzardDay != day) {
            r.blizzardDay = day;
            JournalService.count(p.server, r, p, Stat.BLIZZARDS, 1);
            JournalService.xp(p, Skill.WOODCRAFT, 50);
            JournalService.note(p, "@journal.frontierhunts.note.blizzard", (byte)4);
         }
      } else {
         l.blizzardTicks = 0;
      }
   }

   static void expireWounds(ServerPlayer p, HunterRecord r) {
      if (r.wounds.isEmpty()) {
         return;
      }
      long now = p.serverLevel().getGameTime();
      Iterator<Map.Entry<UUID, HunterRecord.Wound>> it = r.wounds.entrySet().iterator();
      while (it.hasNext()) {
         HunterRecord.Wound w = it.next().getValue();
         if (now - w.time > JournalHooks.LOST_AFTER || now < w.time - 1000L) {
            it.remove();
            JournalService.count(p.server, r, p, Stat.LOST, 1);
         }
      }
   }

   // ============================================================================================ woodcraft perks

   private static void trailLegs(ServerPlayer p, Live l) {
      FoodData fd = p.getFoodData();
      float ex = fd.getExhaustionLevel();
      double dx = p.getX() - p.xOld, dz = p.getZ() - p.zOld;
      boolean moving = dx * dx + dz * dz > 0.0004 && !p.isPassenger();
      if (l.lastExhaustion >= 0.0F && ex > l.lastExhaustion && moving && p.serverLevel().canSeeSky(p.blockPosition().above())) {
         float refund = (float)((ex - l.lastExhaustion) * (1.0 - HunterSkills.hunger(p)));
         fd.setExhaustion(Math.max(0.0F, ex - refund));
         ex = fd.getExhaustionLevel();
      }
      l.lastExhaustion = ex;
   }

   private static void thickSkin(ServerPlayer p, Live l) {
      int f = p.getTicksFrozen();
      if (f > l.lastFrozen) {
         int gained = f - l.lastFrozen;
         int keep = (int)Math.round(gained * HunterSkills.freezing(p));
         p.setTicksFrozen(l.lastFrozen + keep);
         f = p.getTicksFrozen();
      }
      l.lastFrozen = f;
   }

   // ============================================================================================ shots, hits, deaths

   @SubscribeEvent
   public static void join(EntityJoinLevelEvent e) {
      if (e.getLevel().isClientSide() || e.loadedFromDisk()) {
         return;
      }
      Entity ent = e.getEntity();
      boolean shot = ent instanceof FieldArrow || ent instanceof RifleBullet
         || ent instanceof HuntProjectile hp && hp.kind() != Weapon.TRANQUILIZER_RIFLE && hp.kind() != Weapon.FLARE_GUN && hp.kind() != Weapon.BAIT_LAUNCHER
            && hp.kind() != Weapon.BOWFISHING_BOW;
      if (!shot || !(((Projectile)ent).getOwner() instanceof ServerPlayer p)) {
         return;
      }
      try {
         Live l = live(p);
         long t = p.serverLevel().getGameTime();
         if (t == l.lastShotTick) {
            return; // shotgun pellets: one trigger pull is one shot
         }
         l.lastShotTick = t;
         HunterRecord r = JournalService.record(p);
         if (r == null) {
            return;
         }
         JournalService.count(p.server, r, p, Stat.SHOTS, 1);
         if (Prone.isProne(p)) {
            JournalService.count(p.server, r, p, Stat.PRONE_SHOTS, 1);
         }
      } catch (RuntimeException ex) {
         LOG.debug("Frontier Hunts journal: shot count failed", ex);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void incoming(LivingIncomingDamageEvent e) {
      if (e.getEntity() instanceof WildlifeMob m && !m.level().isClientSide) {
         int b = m.behavior();
         CALM.put(m, b != 5 && b != 6);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void hit(LivingDamageEvent.Post e) {
      if (!(e.getEntity() instanceof WildlifeMob mob) || mob.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer p)
         || !(e.getSource().getDirectEntity() instanceof Projectile proj) || !(e.getNewDamage() > 0.0F)) {
         return;
      }
      try {
         WildlifeVitals.Hit hit = ShotVitals.lastHit(mob);
         Boolean calm = CALM.remove(mob);
         JournalHooks.wildlifeHit(p, mob, hit, proj, mob.getHealth() <= 0.0F, calm == null || calm);
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts journal: wildlife hit failed", ex);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void death(LivingDeathEvent e) {
      if (e.isCanceled() || !(e.getEntity() instanceof WildlifeMob mob) || mob.level().isClientSide) {
         return;
      }
      try {
         JournalHooks.wildlifeDeath(mob, e.getSource());
      } catch (RuntimeException ex) {
         LOG.warn("Frontier Hunts journal: wildlife death failed", ex);
      }
   }

   // ============================================================================================ world interactions

   @SubscribeEvent
   public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }
      Block b = p.level().getBlockState(e.getPos()).getBlock();
      boolean sign = b instanceof DeerSign;
      boolean bone = b instanceof BoneBlock;
      if (!sign && !bone) {
         return;
      }
      Live l = live(p);
      if (!l.blocksRead.add(e.getPos().asLong())) {
         return;
      }
      capped(l.blocksRead, 128);
      HunterRecord r = JournalService.record(p);
      if (r == null) {
         return;
      }
      if (sign) {
         // [integ4] only natural rubs/scrapes a buck made count for "Read a rub or scrape" (no kit; beds and legacy
         // hunter-opened scrapes nobody worked give the Tracking XP only)
         DeerSign ds = (DeerSign)b;
         boolean natural = ds.kind != DeerSign.Kind.BED
            && !(p.level().getBlockEntity(e.getPos()) instanceof DeerSign.Mark m && m.hunterMade && m.visits == 0);
         if (natural) {
            JournalService.count(p.server, r, p, Stat.RUBS, 1);
         }
         JournalService.xp(p, Skill.TRACKING, 6);
      } else if (b instanceof ShedAntlerBlock) {
         JournalService.count(p.server, r, p, Stat.SHEDS, 1);
         JournalService.xp(p, Skill.WOODCRAFT, 30);
         JournalService.note(p, "@journal.frontierhunts.note.shed", (byte)4);
      } else {
         boolean first = !hasNear(l, e.getPos());
         if (first) {
            JournalService.count(p.server, r, p, Stat.BONES, 1);
            JournalService.xp(p, Skill.WOODCRAFT, 40);
            JournalService.note(p, "@journal.frontierhunts.note.bones", (byte)4);
         }
      }
   }

   /** One bone site is a scatter of several blocks: only the first block read within 12 m counts. */
   private static boolean hasNear(Live l, BlockPos pos) {
      int n = 0;
      for (long k : l.blocksRead) {
         BlockPos o = BlockPos.of(k);
         if (o.distSqr(pos) < 144.0) {
            n++;
         }
      }
      return n > 1;
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void placed(BlockEvent.EntityPlaceEvent e) {
      if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      String key = null;
      if (e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
         for (BlockSnapshot s : multi.getReplacedBlockSnapshots()) {
            key = placedKey(p.level().getBlockState(s.getPos()));
            if (key != null) {
               break;
            }
         }
      } else {
         key = placedKey(e.getPlacedBlock());
      }
      if (key != null) {
         JournalService.count(p, "placed." + key, 1);
      }
   }

   private static String placedKey(BlockState st) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
      return id == null ? null : Checklist.PLACED_BLOCKS.get(id.toString());
   }

   @SubscribeEvent
   public static void mount(EntityMountEvent e) {
      if (!e.isMounting() || !(e.getEntityMounting() instanceof ServerPlayer p) || e.getEntityBeingMounted() == null) {
         return;
      }
      String id = EntityTypeIds.id(e.getEntityBeingMounted());
      if ("frontierhunts:tree_stand_seat".equals(id)) {
         JournalService.count(p, Stat.STAND_SITS, 1);
      } else if ("frontierhunts:atv".equals(id)) {
         JournalService.count(p, Stat.ATV_RIDES, 1);
      }
   }

   @SubscribeEvent
   public static void ate(LivingEntityUseItemEvent.Finish e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(e.getItem().getItem());
      if (id == null || !Checklist.GAME_FOODS.contains(id.toString()) && !survivalGame(e.getItem(), p)) { // [integ4] any wild game (Survival's food table)
         return;
      }
      JournalService.count(p, Stat.EATEN, 1);
      if (Perk.PROVIDER.in(HunterSkills.mask(p))) {
         double n = HunterSkills.nutrition(p) - 1.0; // 0.25 at full strength
         int food = (int)Math.round(8.0 * n);
         if (food > 0) {
            FoodData fd = p.getFoodData();
            fd.eat(food, 0.6F);
         }
      }
   }

   /** [integ4] Wild game by Frontier Survival's food table (bear meat, fowl, organs, jerky, pemmican, other mods' venison...). */
   private static boolean survivalGame(ItemStack stack, ServerPlayer p) {
      try {
         var v = com.formaworks.frontierhunts.survival.SurvivalApi.food(stack, p.level());
         return v != null && v.source() == com.formaworks.frontierhunts.survival.FoodValues.Source.GAME && !v.spoiled();
      } catch (RuntimeException ex) {
         return false;
      }
   }

   // ============================================================================================ commands

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent e) {
      var root = Commands.literal("journal")
         .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
         .then(Commands.literal("xp").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .then(Commands.argument("skill", StringArgumentType.word())
                  .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Skill.values()).map(s -> s.key), b))
                  .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                     .executes(ctx -> xp(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "skill"),
                        IntegerArgumentType.getInteger(ctx, "amount")))))))
         .then(Commands.literal("complete").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .then(Commands.argument("entry", StringArgumentType.word())
                  .suggests((c, b) -> SharedSuggestionProvider.suggest(Checklist.all().stream().map(Checklist.Entry::id), b))
                  .executes(ctx -> complete(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "entry"))))))
         .then(Commands.literal("summary").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .executes(ctx -> summary(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
         .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
      e.getDispatcher().register(Commands.literal("frontierhunts").then(root));
   }

   private static int status(CommandSourceStack src, ServerPlayer p) {
      HunterRecord r = ProgressStore.get(p.server).record(p.getUUID());
      int done = 0;
      for (Checklist.Entry en : Checklist.visible()) {
         if (r.done.contains(en.id())) {
            done++;
         }
      }
      StringBuilder b = new StringBuilder();
      b.append(Rank.of(r.totalXp()).title()).append(" · ").append(r.totalXp()).append(" XP · checklist ")
         .append(done).append('/').append(Checklist.visible().size()).append(" ·");
      int[] lv = r.levels();
      for (Skill s : Skill.values()) {
         b.append(' ').append(s.key, 0, 4).append(' ').append(lv[s.ordinal()]);
      }
      String msg = b.toString();
      src.sendSuccess(() -> Component.literal(msg), false);
      return 1;
   }

   private static int xp(CommandSourceStack src, Collection<ServerPlayer> targets, String skill, int amount) throws CommandSyntaxException {
      Skill s = null;
      for (Skill k : Skill.values()) {
         if (k.key.equalsIgnoreCase(skill)) {
            s = k;
         }
      }
      if (s == null) {
         src.sendFailure(Component.literal("Unknown skill " + skill));
         return 0;
      }
      for (ServerPlayer p : targets) {
         double mul = JournalConfig.xpMultiplier();
         JournalService.xp(p, s, (int)Math.max(1L, Math.round(amount / Math.max(0.1, mul))));
      }
      Skill fs = s;
      src.sendSuccess(() -> Component.literal("Gave " + amount + " " + fs.key + " XP to " + targets.size() + " hunter(s)"), true);
      return targets.size();
   }

   private static int complete(CommandSourceStack src, Collection<ServerPlayer> targets, String id) {
      Checklist.Entry en = Checklist.byId(id);
      if (en == null) {
         src.sendFailure(Component.literal("Unknown checklist entry " + id));
         return 0;
      }
      for (ServerPlayer p : targets) {
         HunterRecord r = ProgressStore.get(p.server).record(p.getUUID());
         int need = en.target() - r.get(en.counter());
         if (need > 0) {
            JournalService.count(p.server, r, p, en.counter(), Stat.MAX.contains(en.counter()) ? en.target() : need);
         }
      }
      src.sendSuccess(() -> Component.literal("Completed " + id + " for " + targets.size() + " hunter(s)"), true);
      return targets.size();
   }

   private static int summary(CommandSourceStack src, Collection<ServerPlayer> targets) {
      for (ServerPlayer p : targets) {
         HunterRecord r = ProgressStore.get(p.server).record(p.getUUID());
         JournalService.seasonCheck(p, r);
         JournalService.rollover(p, r, r.seasonKey, r.seasonKey); // close the current season now (testing)
      }
      src.sendSuccess(() -> Component.literal("Wrote a season summary for " + targets.size() + " hunter(s) (if they had any activity)"), true);
      return targets.size();
   }

   private static int reset(CommandSourceStack src, Collection<ServerPlayer> targets) {
      for (ServerPlayer p : targets) {
         ProgressStore.get(p.server).remove(p.getUUID());
         HunterRecord r = ProgressStore.get(p.server).record(p.getUUID());
         LIVE.remove(p.getUUID());
         JournalService.perks(p, r);
         JournalService.seasonCheck(p, r);
         JournalService.sendData(p);
      }
      src.sendSuccess(() -> Component.literal("Reset the Hunter's Journal of " + targets.size() + " hunter(s)"), true);
      return targets.size();
   }
}
