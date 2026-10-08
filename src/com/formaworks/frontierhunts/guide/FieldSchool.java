package com.formaworks.frontierhunts.guide;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.ExpeditionNetwork;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.TrailCameraBlock;
import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.routine.RoutineHooks;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailStore;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import com.formaworks.frontierhunts.weather.WeatherKind;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [guide] Server side of the Field School: per-hunter lesson progress, the first-join starter kit, and the one-time
 * field notes. Every check is either event driven (a hook line in the system that already knows) or a staggered
 * once-a-second / once-every-two-seconds look around the player that stops once it has nothing left to look for.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class FieldSchool {
   private static final double STALK_RANGE = 20.0;
   private static final int STALK_PASSES = 3;
   private static final int BLOOD_GOAL = 2;
   private static final long WOUND_MEMORY = 24000L;
   private static final long KILL_SITE_LIFE = 24000L;
   private static final double KILL_SITE_RANGE = 24.0;
   private static final long QUIET_AFTER_JOIN = 600L;
   private static final List<String> GLASSES = List.of("binoculars", "rangefinder", "thermal_binoculars", "anatomy_binoculars", "night_vision_binoculars");

   /** Per-online-player scratch state (never saved). */
   private static final class Live {
      long lastAction = Long.MIN_VALUE;
      long lastTip = Long.MIN_VALUE;
      long lastHint = Long.MIN_VALUE;
      long quietUntil;
      int stalk;
      int windHold;
      boolean kitGiven;
      UUID wounded;
      UUID lastBlood;
      Vec3 woundedAt;
      Vec3 woundedDiedAt;
      long woundedTime;
   }

   private record KillSite(ResourceLocation dim, Vec3 pos, long time) {
   }

   private static final Map<UUID, Live> LIVE = new HashMap<>();
   private static final ArrayDeque<KillSite> KILL_SITES = new ArrayDeque<>();

   private FieldSchool() {
   }

   // ============================================================================================ state helpers

   private static Live live(ServerPlayer p) {
      return LIVE.computeIfAbsent(p.getUUID(), k -> new Live());
   }

   private static FieldSchoolData data(ServerPlayer p) {
      return FieldSchoolData.get(p.server);
   }

   /** The hunter's record (created on login). Null only for fake players and the like. */
   private static FieldSchoolData.Hunter hunter(ServerPlayer p) {
      return p.connection == null ? null : data(p).find(p.getUUID());
   }

   /** Lessons only progress while the course is on for this hunter. */
   private static FieldSchoolData.Hunter course(ServerPlayer p) {
      if (!GuideConfig.enabled()) {
         return null;
      }
      FieldSchoolData.Hunter h = hunter(p);
      return h == null || h.skipped ? null : h;
   }

   private static boolean pending(FieldSchoolData.Hunter h, Lesson l) {
      return h != null && !l.done(h.done);
   }

   public static void sendState(ServerPlayer p) {
      FieldSchoolData.Hunter h = hunter(p);
      if (h == null) {
         return;
      }
      int flags = (GuideConfig.enabled() ? GuideNetwork.F_ENABLED : 0)
         | (h.skipped ? GuideNetwork.F_SKIPPED : 0)
         | (h.welcomed ? GuideNetwork.F_WELCOMED : 0)
         | (h.graduated ? GuideNetwork.F_GRADUATED : 0)
         | (GuideConfig.tips() ? GuideNetwork.F_TIPS : 0)
         | (live(p).kitGiven ? GuideNetwork.F_KIT : 0);
      Lesson cur = Lesson.current(h.done);
      int progress = cur == null ? 0 : cur == Lesson.STALK ? Math.min(live(p).stalk, cur.goal - 1) : h.progress(cur);
      GuideNetwork.send(p, new GuideNetwork.State(h.done, flags, progress, h.tips));
   }

   private static void notice(ServerPlayer p, byte kind, int id, int arg) {
      GuideNetwork.send(p, new GuideNetwork.Notice(kind, (byte)id, arg));
   }

   /** A coaching hint for the current lesson, at most one every 20 s. */
   private static void hint(ServerPlayer p, byte id) {
      Live l = live(p);
      long now = p.serverLevel().getGameTime();
      if (l.lastHint == Long.MIN_VALUE || now < l.lastHint || now - l.lastHint >= 400L) {
         l.lastHint = now;
         notice(p, GuideNetwork.N_HINT, id, 0);
      }
   }

   /** Marks a lesson done (once), toasts it, and graduates the hunter when the seven required lessons are done. */
   public static void complete(ServerPlayer p, Lesson lesson, boolean skipped) {
      FieldSchoolData.Hunter h = course(p);
      if (h == null || lesson.done(h.done)) {
         return;
      }
      h.done |= lesson.bit();
      live(p).stalk = 0;
      notice(p, GuideNetwork.N_LESSON, lesson.ordinal(), skipped ? 1 : 0);
      if (!h.graduated && Lesson.graduated(h.done)) {
         h.graduated = true;
         notice(p, GuideNetwork.N_GRADUATED, 0, 0);
      }
      data(p).setDirty();
      sendState(p);
   }

   /** Shows a one-time field note if this hunter has not had it and is not inside the cool-down. */
   private static boolean tip(ServerPlayer p, Tip t, int arg) {
      FieldSchoolData.Hunter h = hunter(p);
      if (h == null || !GuideConfig.tips() || (h.tips & t.bit()) != 0) {
         return false;
      }
      Live l = live(p);
      long now = p.serverLevel().getGameTime();
      boolean cooled = l.lastTip == Long.MIN_VALUE || now < l.lastTip || now - l.lastTip >= GuideConfig.tipCooldownTicks();
      if (!cooled || now < l.quietUntil) {
         return false;
      }
      l.lastTip = now;
      h.tips |= t.bit();
      data(p).setDirty();
      notice(p, GuideNetwork.N_TIP, t.ordinal(), arg);
      return true;
   }

   // ============================================================================================ hook lines (shared code)

   /** [survival] one-time Frontier Survival field note (hunger, cold, spoiled meat, fall stock-up). */
   public static void survivalTip(ServerPlayer p, Tip t) {
      tip(p, t, 0);
   }

   /** [hook: TrailService.inspect] a hunter read a print, blood or other sign with the use key. */
   public static void markInspected(ServerPlayer p, TrailMark mark) {
      try {
         if (mark == null) {
            return;
         }
         if ("predator sign".equals(mark.threat())) {
            tip(p, Tip.PREDATOR, 0);
         }
         FieldSchoolData.Hunter h = course(p);
         if (h == null) {
            return;
         }
         complete(p, Lesson.SIGN, false);
         Live live = live(p);
         if (mark.blood() && pending(h, Lesson.TRAIL) && !mark.id().equals(live.lastBlood)) {
            live.lastBlood = mark.id();
            h.blood = Math.min(BLOOD_GOAL, h.blood + 1);
            data(p).setDirty();
            if (h.blood >= BLOOD_GOAL) {
               complete(p, Lesson.TRAIL, false);
            } else {
               hint(p, GuideNetwork.H_BLOOD);
               sendState(p);
            }
         }
      } catch (RuntimeException e) {
         // the guide never breaks the system it watches
      }
   }

   /** [hook: HoundCommands.track] the hunter put a hound on a line. */
   public static void houndTrack(ServerPlayer p, TrailMark mark) {
      try {
         if (mark != null && mark.blood()) {
            complete(p, Lesson.TRAIL, false);
         }
      } catch (RuntimeException ignored) {
      }
   }

   /** [hook: Whitetail.perceive] a deer just broke into alarm because of this hunter; scent = it smelled them. */
   public static void deerSpooked(Whitetail deer, Player who, boolean scent) {
      if (!(who instanceof ServerPlayer p) || p.connection == null) {
         return;
      }
      try {
         if (scent) {
            tip(p, Tip.WINDED, 0);
            com.formaworks.frontierhunts.firsthunt.FirstHunt.winded(p); // [1.2.7] first hunt: where the wind goes
         }
         FieldSchoolData.Hunter h = course(p);
         if (pending(h, Lesson.STALK) && deer.distanceToSqr(p) < 48.0 * 48.0) {
            Live l = live(p);
            boolean counting = l.stalk > 0;
            l.stalk = 0;
            hint(p, scent ? GuideNetwork.H_WINDED : GuideNetwork.H_SPOTTED);
            if (counting) {
               sendState(p);
            }
         }
      } catch (RuntimeException ignored) {
      }
   }

   /** [hook: Whitetail.harvest] the hunter finished dressing a carcass; the trophy is lying at it. */
   public static void harvested(ServerPlayer p) {
      try {
         FieldSchoolData.Hunter h = course(p);
         if (pending(h, Lesson.HARVEST) && !h.dressed) {
            h.dressed = true;
            data(p).setDirty();
            hint(p, GuideNetwork.H_DRESSED);
            sendState(p);
         }
      } catch (RuntimeException ignored) {
      }
   }

   // ============================================================================================ player decisions

   static void action(ServerPlayer p, byte action, byte arg) {
      FieldSchoolData.Hunter h = hunter(p);
      if (h == null || !p.isAlive() && action != GuideNetwork.A_SYNC) {
         return;
      }
      Live l = live(p);
      long now = p.serverLevel().getGameTime();
      if (l.lastAction != Long.MIN_VALUE && now >= l.lastAction && now - l.lastAction < 4L) {
         return;
      }
      l.lastAction = now;
      switch (action) {
         case GuideNetwork.A_BEGIN -> {
            h.welcomed = true;
            h.skipped = false;
         }
         case GuideNetwork.A_SKIP_ALL -> {
            h.welcomed = true;
            h.skipped = true;
         }
         case GuideNetwork.A_RESUME -> h.skipped = false;
         case GuideNetwork.A_SKIP_LESSON -> {
            Lesson lesson = Lesson.byId(arg);
            if (lesson != null) {
               complete(p, lesson, true);
            }
         }
         case GuideNetwork.A_READ_TIPS -> complete(p, Lesson.TIPS, false);
         case GuideNetwork.A_SYNC -> {
         }
         default -> {
            return;
         }
      }
      data(p).setDirty();
      sendState(p);
   }

   // ============================================================================================ lifecycle

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p.connection == null) {
         return;
      }
      try {
         FieldSchoolData d = data(p);
         FieldSchoolData.Hunter h = d.create(p.getUUID());
         Live l = live(p);
         l.quietUntil = p.serverLevel().getGameTime() + QUIET_AFTER_JOIN;
         // [1.2.7] the kit mark is also kept in the player's own save (written with the inventory): a world that lost its
         // last minutes in a crash can't hand the kit out a second time
         net.minecraft.nbt.CompoundTag mine = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
         if (!h.kit && mine.getBoolean("frontierhunts_kit")) {
            h.kit = true;
            d.setDirty();
         }
         if (!h.kit) {
            h.kit = true;
            d.setDirty();
            if (GuideConfig.enabled() && GuideConfig.starterKit() && !veteran(p)) {
               giveKit(p);
               l.kitGiven = true;
               mine.putBoolean("frontierhunts_kit", true);
               p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, mine);
               com.formaworks.frontierhunts.firsthunt.FirstHunt.begin(p, true); // [1.2.7] "Start your first hunt"
               com.formaworks.frontierhunts.progression.Durability.commit(p.server, "starter kit");
            }
         }
         sendState(p);
      } catch (RuntimeException ex) {
         com.mojang.logging.LogUtils.getLogger().warn("Frontier Hunts field school: login handling failed", ex);
      }
   }

   private static boolean veteran(ServerPlayer p) {
      if (p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 36000) {
         return true;
      }
      return ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID()).starter;
   }

   private static void giveKit(ServerPlayer p) {
      // [onboard] the first-join kit is the Frontier Handbook, the Field Recurve Bow and three arrows; everything else
      // is crafted or earned (the Handbook shows how). The expedition campaign starts counting without its old kit.
      com.formaworks.frontierhunts.onboard.Onboarding.giveStarterKit(p);
   }

   @SubscribeEvent
   public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         sendState(p);
      }
   }

   @SubscribeEvent
   public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         live(p).stalk = 0;
         sendState(p);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      LIVE.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      LIVE.clear();
      KILL_SITES.clear();
   }

   // ============================================================================================ things players do

   @SubscribeEvent
   public static void useItem(PlayerInteractEvent.RightClickItem e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getItemStack().getItem() instanceof ExpeditionGear gear)) {
         return;
      }
      if (gear.id.equals("wind_checker")) {
         complete(p, Lesson.WIND, false);
      } else if (GLASSES.contains(gear.id)) {
         complete(p, Lesson.GLASS, false);
      }
   }

   @SubscribeEvent
   public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
      if (e.getEntity() instanceof ServerPlayer p && e.getHand() == InteractionHand.MAIN_HAND
         && p.level().getBlockState(e.getPos()).getBlock() instanceof DeerSign) {
         complete(p, Lesson.SIGN, false);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void placed(BlockEvent.EntityPlaceEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && e.getPlacedBlock().getBlock() instanceof TrailCameraBlock) {
         complete(p, Lesson.GLASS, false);
      }
   }

   /** Lesson 5: any hit the hunter's own projectile lands on a game animal. Drop or run decides the coaching. */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void hit(LivingDamageEvent.Post e) {
      LivingEntity target = e.getEntity();
      if (!(target instanceof Whitetail) && !(target instanceof WildlifeMob)) {
         return;
      }
      if (target instanceof Whitetail && target.getHealth() <= 0.0F && predator(e.getSource().getEntity())) {
         // Whitetail.die() never fires LivingDeathEvent (the carcass stays as a downed deer), so record its kill site here
         killSite(target);
      }
      if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !(e.getSource().getDirectEntity() instanceof Projectile) || !(e.getNewDamage() > 0.0F)) {
         return;
      }
      try {
         FieldSchoolData.Hunter h = course(p);
         if (h == null) {
            return;
         }
         boolean dropped = target.getHealth() <= 0.0F || target instanceof Whitetail w && w.downed();
         if (!dropped && pending(h, Lesson.TRAIL)) {
            Live l = live(p);
            l.wounded = target.getUUID();
            l.woundedAt = target.position();
            l.woundedDiedAt = null;
            l.woundedTime = p.serverLevel().getGameTime();
         }
         if (pending(h, Lesson.SHOT)) {
            complete(p, Lesson.SHOT, false);
            live(p).lastHint = Long.MIN_VALUE; // the drop/run coaching always shows right after the lesson toast
         }
         if (pending(h, Lesson.TRAIL) || pending(h, Lesson.HARVEST)) {
            hint(p, dropped ? GuideNetwork.H_DROPPED : GuideNetwork.H_RAN);
         }
      } catch (RuntimeException ignored) {
      }
   }

   @SubscribeEvent
   public static void death(LivingDeathEvent e) {
      LivingEntity victim = e.getEntity();
      if (!(victim.level() instanceof ServerLevel level)) {
         return;
      }
      boolean game = victim instanceof Whitetail || victim instanceof WildlifeMob wm && wm.species != null && RoutineHooks.prey(wm.species);
      if (!game) {
         return;
      }
      if (predator(e.getSource().getEntity())) {
         killSite(victim);
      }
      // a wounded animal of lesson 6 that died where the hunter has not been yet
      for (Map.Entry<UUID, Live> entry : LIVE.entrySet()) {
         if (victim.getUUID().equals(entry.getValue().wounded)) {
            entry.getValue().woundedDiedAt = victim.position();
         }
      }
   }

   private static boolean predator(Entity killer) {
      return killer instanceof Wolf || killer instanceof WildlifeMob km && km.species != null && !km.species.bird && !RoutineHooks.prey(km.species);
   }

   private static void killSite(LivingEntity victim) {
      KILL_SITES.addLast(new KillSite(victim.level().dimension().location(), victim.position(), victim.level().getGameTime()));
      while (KILL_SITES.size() > 24) {
         KILL_SITES.removeFirst();
      }
   }

   // ============================================================================================ periodic looks

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || p.connection == null || !p.isAlive() || p.isSpectator()) {
         return;
      }
      int phase = (p.tickCount + p.getId()) % 40;
      if (phase != 0 && phase != 20) {
         return;
      }
      FieldSchoolData.Hunter h = hunter(p);
      if (h == null) {
         return;
      }
      try {
         ServerLevel level = p.serverLevel();
         Live l = live(p);
         FieldSchoolData.Hunter c = course(p);
         if (c != null && Lesson.current(c.done) != null) {
            lessons(p, level, c, l);
         }
         if (phase == 0 && GuideConfig.tips() && (h.tips & 63) != 63) {
            tips(p, level, h, l);
         }
      } catch (RuntimeException ignored) {
      }
   }

   private static boolean holding(ServerPlayer p, String gearId) {
      for (InteractionHand hand : InteractionHand.values()) {
         if (p.getItemInHand(hand).getItem() instanceof ExpeditionGear g && g.id.equals(gearId)) {
            return true;
         }
      }
      return false;
   }

   /** Once a second while the course has something left that needs a look around. */
   private static void lessons(ServerPlayer p, ServerLevel level, FieldSchoolData.Hunter h, Live l) {
      // 1: holding the wind checker for three seconds reads the scent cone
      if (pending(h, Lesson.WIND)) {
         l.windHold = holding(p, "wind_checker") ? l.windHold + 1 : 0;
         if (l.windHold >= 3) {
            complete(p, Lesson.WIND, false);
         }
      }
      // 4: within 20 blocks of a calm deer for three seconds
      if (pending(h, Lesson.STALK)) {
         boolean close = false;
         for (Whitetail deer : level.getEntitiesOfClass(Whitetail.class, p.getBoundingBox().inflate(STALK_RANGE, 8.0, STALK_RANGE), d -> d.isAlive() && !d.downed())) {
            if (deer.alertness() < 0.3F && deer.distanceToSqr(p) <= STALK_RANGE * STALK_RANGE && !deer.bleeding()) {
               close = true;
               break;
            }
         }
         int before = l.stalk;
         l.stalk = close && !p.isPassenger() ? l.stalk + 1 : 0;
         if (l.stalk >= STALK_PASSES) {
            complete(p, Lesson.STALK, false);
         } else if (before != l.stalk && Lesson.current(h.done) == Lesson.STALK) {
            sendState(p); // the card counts the seconds (at most three small updates per approach)
         }
      }
      // 6: walking up on the animal wounded in lesson 5 after it ran
      if (pending(h, Lesson.TRAIL) && l.wounded != null) {
         long now = level.getGameTime();
         if (now - l.woundedTime > WOUND_MEMORY || now < l.woundedTime) {
            l.wounded = null;
         } else if (l.woundedDiedAt != null) {
            if (p.position().distanceToSqr(l.woundedDiedAt) < 25.0 && l.woundedDiedAt.distanceToSqr(l.woundedAt) > 100.0) {
               complete(p, Lesson.TRAIL, false);
               l.wounded = null;
            }
         } else if (level.getEntity(l.wounded) instanceof Whitetail deer && deer.downed()
            && deer.distanceToSqr(p) < 16.0 && deer.position().distanceToSqr(l.woundedAt) > 100.0) {
            complete(p, Lesson.TRAIL, false);
            l.wounded = null;
         }
      }
      // 7: the trophy is in the pack once the carcass is dressed
      if (pending(h, Lesson.HARVEST) && h.dressed && p.getInventory().contains(s -> s.is(HuntContent.WHITETAIL_TROPHY.get())) /* [1.1.5] the real trophy carries a name and data, so match the item only */) {
         complete(p, Lesson.HARVEST, false);
      }
   }

   /** Every two seconds until all six notes were shown: the cheap first-encounter checks. */
   private static void tips(ServerPlayer p, ServerLevel level, FieldSchoolData.Hunter h, Live l) {
      long now = level.getGameTime();
      if (now < l.quietUntil || l.lastTip != Long.MIN_VALUE && now >= l.lastTip && now - l.lastTip < GuideConfig.tipCooldownTicks()) {
         return;
      }
      if ((h.tips & Tip.DEER_SPOTTED.bit()) == 0 && deerInView(p, level) && tip(p, Tip.DEER_SPOTTED, 0)) {
         return;
      }
      if ((h.tips & Tip.BLOOD.bit()) == 0) {
         for (TrailMark m : TrailStore.get(level).nearby(p.position(), 10.0, 24, now)) {
            if (m.blood() && tip(p, Tip.BLOOD, 0)) {
               return;
            }
         }
      }
      if ((h.tips & Tip.BLIZZARD.bit()) == 0 && (p.tickCount / 40) % 3 == 0
         && SeasonalWeather.severity(level, p.blockPosition(), WeatherKind.BLIZZARD) > 0.35F && tip(p, Tip.BLIZZARD, 0)) {
         return;
      }
      if ((p.tickCount / 40) % 5 == 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD && seasonsOn()) {
         int season = SeasonClock.season(level).ordinal();
         if (h.season < 0) {
            h.season = season;
            FieldSchoolData.get(p.server).setDirty();
         } else if (h.season != season) {
            if ((h.tips & Tip.SEASON.bit()) != 0 || tip(p, Tip.SEASON, season)) {
               h.season = season;
               FieldSchoolData.get(p.server).setDirty();
               return;
            }
         }
      }
      if ((h.tips & Tip.PREDATOR.bit()) == 0 && !KILL_SITES.isEmpty()) {
         ResourceLocation dim = level.dimension().location();
         Iterator<KillSite> it = KILL_SITES.iterator();
         while (it.hasNext()) {
            KillSite s = it.next();
            if (now - s.time > KILL_SITE_LIFE || now < s.time) {
               it.remove();
            } else if (s.dim.equals(dim) && s.pos.distanceToSqr(p.position()) < KILL_SITE_RANGE * KILL_SITE_RANGE && tip(p, Tip.PREDATOR, 1)) {
               return;
            }
         }
      }
   }

   private static boolean seasonsOn() {
      try {
         return HuntConfig.SEASONS == null || HuntConfig.SEASONS.get();
      } catch (Throwable t) {
         return true;
      }
   }

   private static boolean deerInView(ServerPlayer p, ServerLevel level) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getViewVector(1.0F);
      int checked = 0;
      for (Whitetail deer : level.getEntitiesOfClass(Whitetail.class, p.getBoundingBox().inflate(40.0, 16.0, 40.0), d -> d.isAlive() && !d.downed())) {
         Vec3 to = deer.getEyePosition().subtract(eye);
         double len = to.length();
         if (len < 1.0E-3 || len > 40.0 || to.dot(look) / len < 0.8) {
            continue;
         }
         if (checked++ >= 3) {
            break;
         }
         if (p.hasLineOfSight(deer)) {
            return true;
         }
      }
      return false;
   }

   // ============================================================================================ commands

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent e) {
      LiteralArgumentBuilder<CommandSourceStack> tutorial = Commands.literal("tutorial")
         .executes(ctx -> status(ctx.getSource()))
         .then(Commands.literal("reset")
            .executes(ctx -> reset(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
            .then(Commands.argument("targets", EntityArgument.players()).requires(s -> s.hasPermission(2))
               .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
         .then(Commands.literal("skip")
            .executes(ctx -> skip(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
            .then(Commands.argument("targets", EntityArgument.players()).requires(s -> s.hasPermission(2))
               .executes(ctx -> skip(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
      e.getDispatcher().register(Commands.literal("frontierhunts").then(tutorial));
   }

   private static int status(CommandSourceStack src) throws CommandSyntaxException {
      ServerPlayer p = src.getPlayerOrException();
      FieldSchoolData.Hunter h = hunter(p);
      if (h == null) {
         return 0;
      }
      Lesson cur = Lesson.current(h.done);
      Component state = !GuideConfig.enabled()
         ? Component.translatable("guide.frontierhunts.cmd.disabled")
         : h.skipped
            ? Component.translatable("guide.frontierhunts.cmd.skipped")
            : cur == null
               ? Component.translatable("guide.frontierhunts.cmd.done")
               : Component.translatable("guide.frontierhunts.cmd.current", cur.ordinal() + 1, Component.translatable(cur.lang("title")));
      src.sendSuccess(() -> Component.translatable("guide.frontierhunts.cmd.status", Lesson.requiredDone(h.done), 7, state), false);
      return 1;
   }

   private static int reset(CommandSourceStack src, Collection<ServerPlayer> targets) {
      int n = 0;
      for (ServerPlayer p : targets) {
         FieldSchoolData.Hunter h = hunter(p);
         if (h != null) {
            h.reset();
            Live l = live(p);
            l.stalk = 0;
            l.windHold = 0;
            l.wounded = null;
            l.lastTip = Long.MIN_VALUE;
            l.quietUntil = 0L;
            data(p).setDirty();
            sendState(p);
            n++;
         }
      }
      int count = n;
      src.sendSuccess(() -> Component.translatable("guide.frontierhunts.cmd.reset", count), true);
      return n;
   }

   private static int skip(CommandSourceStack src, Collection<ServerPlayer> targets) {
      int n = 0;
      for (ServerPlayer p : targets) {
         FieldSchoolData.Hunter h = hunter(p);
         if (h != null) {
            h.welcomed = true;
            h.skipped = true;
            data(p).setDirty();
            sendState(p);
            n++;
         }
      }
      int count = n;
      src.sendSuccess(() -> Component.translatable("guide.frontierhunts.cmd.skip", count), true);
      return n;
   }
}
