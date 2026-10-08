package com.formaworks.frontierhunts.firsthunt;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.routine.HomeRange;
import com.formaworks.frontierhunts.hunting.routine.RoutineStore;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.licence.LicenceConfig;
import com.formaworks.frontierhunts.licence.LicenceStatus;
import com.formaworks.frontierhunts.progression.Durability;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * [1.2.7] The new hunter's first hunt: one clear objective, shown one step at a time.
 *
 * <ol>
 * <li><b>Permit:</b> a licence (from finishing the Ranger Academy) and a bought deer tag valid now, in season;</li>
 * <li><b>Signs:</b> read a print, a bed, a rub or droppings (inspect any trail sign);</li>
 * <li><b>Wind:</b> close in on a calm whitetail with the wind blowing from the deer to you;</li>
 * <li><b>Shot:</b> put an arrow or a bullet into a whitetail;</li>
 * <li><b>Track:</b> read the blood or walk up on the downed animal;</li>
 * <li><b>Harvest:</b> field-dress it;</li>
 * <li><b>Claim:</b> collect the reward.</li>
 * </ol>
 *
 * The server owns every step. The hunter's progress lives in the player's own save ({@code PlayerPersisted}, written with
 * the inventory), so the reward and the "claimed" mark always reach the disk together: the reward can never be lost
 * or paid twice, whatever happens to the server. A beginner hunting area is picked only where whitetails are known to
 * be (live animals or established home ranges), as a broad circle, never the animal; if it goes quiet the hunter can ask
 * for another.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class FirstHunt {
   private static final Logger LOG = LogUtils.getLogger();

   public enum Step {
      PERMIT, SIGNS, WIND, SHOT, TRACK, HARVEST, CLAIM, DONE;

      public int bit() {
         return 1 << this.ordinal();
      }

      public String key() {
         return this.name().toLowerCase(java.util.Locale.ROOT);
      }
   }

   static final int ALL = (1 << Step.DONE.ordinal()) - 1;
   static final String KEY = "frontierhunts_firsthunt";
   static final String SPECIES = "whitetail";
   static final TagKey<Biome> HABITAT = TagKey.create(Registries.BIOME, FrontierHunts.id("whitetail_habitat"));
   /** what the reward pays */
   public static final int REWARD_TOKENS = 40;
   static final int REWARD_ARROWS = 8;
   /** the wind step: seconds spent in a calm deer's downwind cone, within this range */
   static final int WIND_SECONDS = 3;
   static final double WIND_RANGE = 32.0;
   static final int AREA_RADIUS = 64;

   /** advice lines for the area (lang firsthunt.frontierhunts.advice.N) */
   static final int ADVICE_MEADOW = 0, ADVICE_FOREST = 1, ADVICE_WATER = 2, ADVICE_BEDDING = 3, ADVICE_SCOUT = 4;

   /** per player, not saved: the moment-to-moment checks */
   static final class Live {
      int windSeconds;
      boolean downwind;
      final ArrayDeque<UUID> shot = new ArrayDeque<>();
      int quiet;
      long lastRequest = Long.MIN_VALUE;
      int sentHash;
      long lastWindHint = Long.MIN_VALUE;
      /** [fharea] next time the area's ground may be judged again */
      long verifyAt = Long.MIN_VALUE;
      /** [1.3.0] the area the timer above belongs to: a different area is judged at once */
      int verifyX = Integer.MIN_VALUE, verifyZ = Integer.MIN_VALUE;
   }

   private static final Map<UUID, Live> LIVE = new HashMap<>();

   private FirstHunt() {
   }

   static Live live(ServerPlayer p) {
      return LIVE.computeIfAbsent(p.getUUID(), k -> new Live());
   }

   // ============================================================================================ saved state

   /** the hunter's first-hunt record, inside the PlayerPersisted compound (kept through death, saved with the inventory) */
   static CompoundTag data(Player p) {
      CompoundTag root = p.getPersistentData();
      if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
         root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
      }
      CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
      if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) {
         persisted.put(KEY, new CompoundTag());
      }
      return persisted.getCompound(KEY);
   }

   public static Step current(int done) {
      for (Step s : Step.values()) {
         if (s != Step.DONE && (done & s.bit()) == 0) {
            return s;
         }
      }
      return Step.DONE;
   }

   static boolean started(ServerPlayer p) {
      return data(p).getBoolean("started");
   }

   static int done(ServerPlayer p) {
      return data(p).getInt("done") & ALL;
   }

   /** Marks a step done. A harvest (or a track) also settles everything before it: the hunter plainly did those. */
   static void complete(ServerPlayer p, Step s) {
      CompoundTag d = data(p);
      if (!d.getBoolean("started") || d.getBoolean("claimed")) {
         return;
      }
      int done = d.getInt("done") & ALL;
      int add = s.bit();
      if (s == Step.HARVEST) {
         add |= Step.SHOT.bit() | Step.TRACK.bit() | Step.SIGNS.bit() | Step.WIND.bit() | Step.PERMIT.bit();
      } else if (s == Step.TRACK) {
         add |= Step.SHOT.bit() | Step.SIGNS.bit() | Step.WIND.bit();
      } else if (s == Step.SHOT) {
         add |= Step.SIGNS.bit() | Step.WIND.bit(); // [1.2.8] a deer in the sights means signs and approach are behind you: the card moves on to tracking
      }
      if ((done | add) == done) {
         return;
      }
      Step before = current(done);
      done |= add;
      d.putInt("done", done);
      Step now = current(done);
      if (now != before) {
         p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.35F, 1.4F);
         JournalApi.count(p, "firsthunt." + before.key(), 1);
      }
      sync(p, true);
   }

   /** Start the first hunt (the Handbook was handed over, or the hunter chose to from the Handbook). */
   public static void begin(ServerPlayer p, boolean intro) {
      CompoundTag d = data(p);
      if (d.getBoolean("started")) {
         return;
      }
      d.putBoolean("started", true);
      d.putBoolean("intro", intro);
      d.putInt("done", 0);
      sync(p, true);
   }

   // ============================================================================================ events

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      LIVE.remove(p.getUUID());
      try {
         CompoundTag d = data(p);
         // a hunter who has the Handbook and has never harvested anything gets the guide once (worlds from before 1.2.7)
         if (!d.getBoolean("started") && !d.getBoolean("offered") && !p.isCreative() && JournalApi.counter(p, "harvests") <= 0
            && p.getInventory().contains(s -> s.is(handbook()))) {
            d.putBoolean("offered", true);
            begin(p, true);
            return;
         }
         sync(p, true);
      } catch (RuntimeException ex) {
         LOG.warn("[FrontierHunts] first hunt: login", ex);
      }
   }

   /** [fharea] biome tiers follow the tags */
   @SubscribeEvent
   public static void tags(net.neoforged.neoforge.event.TagsUpdatedEvent e) {
      BeginnerArea.clearCache();
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      LIVE.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void clone(PlayerEvent.Clone e) {
      // PlayerPersisted is carried over by NeoForge; make sure of it for this key
      CompoundTag from = e.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);
      if (!from.isEmpty() && e.getEntity() instanceof ServerPlayer p) {
         CompoundTag root = p.getPersistentData();
         if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
         }
         root.getCompound(Player.PERSISTED_NBT_TAG).put(KEY, from.copy());
      }
   }

   static Item handbook() {
      Item i = com.formaworks.frontierhunts.onboard.OnboardContent.HANDBOOK;
      return i == null ? Items.AIR : i;
   }

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + (p.getId() & 15)) % 20 != 0 || p.connection == null || !p.isAlive()) {
         return;
      }
      try {
         CompoundTag d = data(p);
         if (!d.getBoolean("started") || d.getBoolean("claimed")) {
            return;
         }
         Live l = live(p);
         Step now = current(d.getInt("done"));
         LicenceStatus.Status st = LicenceStatus.of(p, SPECIES);
         if (st.legal()) {
            complete(p, Step.PERMIT);
         }
         if (now == Step.WIND || (d.getInt("done") & Step.WIND.bit()) == 0 && now.ordinal() <= Step.SHOT.ordinal()) {
            windCheck(p, l);
         }
         int done = d.getInt("done");
         if ((done & Step.SHOT.bit()) != 0 && (done & Step.TRACK.bit()) == 0) {
            trackCheck(p, l); // in any order: a hunter who skipped the wind step still tracks their deer
         }
         if (now.ordinal() <= Step.SHOT.ordinal()) {
            areaCheck(p, d, l);
         }
         sync(p, false);
      } catch (RuntimeException ex) {
         LOG.warn("[FrontierHunts] first hunt: tick", ex);
      }
   }

   /** the wind step: in a calm whitetail's downwind cone, close, for a few seconds without spooking it */
   static void windCheck(ServerPlayer p, Live l) {
      ServerLevel level = p.serverLevel();
      Wilderness.Wind wind = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
      double ws = Math.hypot(wind.east(), wind.south());
      boolean good = false, near = false;
      for (Whitetail deer : level.getEntitiesOfClass(Whitetail.class, p.getBoundingBox().inflate(WIND_RANGE, 12.0, WIND_RANGE),
         x -> x.isAlive() && !x.downed() && x.species() == GameSpecies.WHITETAIL)) {
         double dx = p.getX() - deer.getX(), dz = p.getZ() - deer.getZ();
         double dist = Math.hypot(dx, dz);
         if (dist > WIND_RANGE) {
            continue;
         }
         near = true;
         // the wind blows toward (east, south): downwind of the deer means the deer-to-hunter line runs with it
         boolean downwind = ws < 0.6 || dist > 1.0E-3 && (dx * wind.east() + dz * wind.south()) / (dist * ws) >= 0.5;
         if (downwind && deer.alertness() < 0.35F && !deer.bleeding()) {
            good = true;
            break;
         }
      }
      l.downwind = good;
      l.windSeconds = good && !p.isPassenger() ? l.windSeconds + 1 : 0;
      if (l.windSeconds >= WIND_SECONDS) {
         complete(p, Step.WIND);
         l.windSeconds = 0;
      } else if (near && !good && level.getGameTime() - l.lastWindHint > 400L) {
         l.lastWindHint = level.getGameTime();
         p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.hint.wind", Component.translatable("firsthunt.frontierhunts.dir." + dir(wind.east(), wind.south()))).withStyle(ChatFormatting.GRAY), true);
      }
   }

   /** compass point (8) a vector points to: n, ne, e, ... */
   static String dir(double east, double south) {
      double a = Math.toDegrees(Math.atan2(east, -south)); // 0 = north, clockwise
      String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
      return names[Math.floorMod((int)Math.round(a / 45.0), 8)];
   }

   /** the tracking step: the hunter walks up on an animal they hit, once it is down */
   static void trackCheck(ServerPlayer p, Live l) {
      for (UUID id : l.shot) {
         if (p.serverLevel().getEntity(id) instanceof Whitetail deer && deer.downed() && deer.distanceToSqr(p) < 25.0) {
            complete(p, Step.TRACK);
            return;
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void hit(LivingDamageEvent.Post e) {
      LivingEntity target = e.getEntity();
      if (!(target instanceof Whitetail deer) || deer.species() != GameSpecies.WHITETAIL || !(e.getSource().getEntity() instanceof ServerPlayer p)
         || !(e.getSource().getDirectEntity() instanceof Projectile) || !(e.getNewDamage() > 0.0F)) {
         return;
      }
      try {
         if (!started(p)) {
            return;
         }
         Live l = live(p);
         l.shot.remove(deer.getUUID());
         l.shot.addFirst(deer.getUUID());
         while (l.shot.size() > 4) {
            l.shot.removeLast();
         }
         complete(p, Step.SHOT);
      } catch (RuntimeException ignored) {
      }
   }

   /** [hook: TrailService.inspect] any sign read with the use key; blood counts for the tracking step */
   public static void signRead(ServerPlayer p, TrailMark mark) {
      try {
         if (!started(p)) {
            return;
         }
         complete(p, Step.SIGNS);
         if (mark != null && mark.blood() && (done(p) & Step.SHOT.bit()) != 0) {
            complete(p, Step.TRACK);
         }
      } catch (RuntimeException ignored) {
      }
   }

   @SubscribeEvent
   public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
      if (e.getEntity() instanceof ServerPlayer p && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
         && p.level().getBlockState(e.getPos()).getBlock() instanceof com.formaworks.frontierhunts.hunting.DeerSign && started(p)) {
         complete(p, Step.SIGNS);
      }
   }

   /** [hook: Whitetail.harvest] the hunter field-dressed a deer */
   public static void harvested(ServerPlayer p, Whitetail deer) {
      try {
         if (started(p) && deer.species() == GameSpecies.WHITETAIL) {
            complete(p, Step.HARVEST);
            Durability.commit(p.server, "first hunt harvest");
         }
      } catch (RuntimeException ignored) {
      }
   }

   /** [hook: FieldSchool.deerSpooked] smelled: say where the wind is going */
   public static void winded(ServerPlayer p) {
      try {
         if (started(p) && current(done(p)) == Step.WIND) {
            ServerLevel level = p.serverLevel();
            Wilderness.Wind wind = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
            p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.hint.winded",
               Component.translatable("firsthunt.frontierhunts.dir." + dir(wind.east(), wind.south()))).withStyle(ChatFormatting.GOLD), true);
            live(p).windSeconds = 0;
         }
      } catch (RuntimeException ignored) {
      }
   }

   // ============================================================================================ the beginner area

   /** Assign or check the beginner area: never where no whitetail is known, re-checked while the hunter is there. */
   static void areaCheck(ServerPlayer p, CompoundTag d, Live l) {
      ServerLevel level = p.serverLevel();
      long now = level.getGameTime();
      boolean has = d.getBoolean("area") && d.getString("areaDim").equals(level.dimension().location().toString());
      if (!has) {
         if (now >= d.getLong("areaRetry") || now < d.getLong("areaRetry") - 2400L) {
            d.putLong("areaRetry", now + 600L);
            assignArea(p, d, false);
         }
         return;
      }
      verifyArea(p, d, l); // [fharea]
      if (!d.getBoolean("area")) {
         return;
      }
      if ((p.tickCount + (p.getId() & 63)) % 400 >= 20) {
         return; // the population check every 20 s
      }
      int ax = d.getInt("areaX"), az = d.getInt("areaZ"), r = d.getInt("areaR");
      double dist = Math.hypot(p.getX() - ax, p.getZ() - az);
      if (dist > r * 1.5) {
         return; // only judge an area the hunter is standing in (its chunks are loaded)
      }
      if (deerNear(level, ax, az, r + 48)) {
         l.quiet = 0;
         if (!d.getBoolean("areaConf")) {
            d.putBoolean("areaConf", true);
         }
         if (d.getBoolean("areaQuiet")) {
            d.putBoolean("areaQuiet", false);
         }
      } else if (++l.quiet >= 3 && !d.getBoolean("areaQuiet")) {
         d.putBoolean("areaQuiet", true); // a minute in it and no deer: offer another
         p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.area.quiet").withStyle(ChatFormatting.GOLD), true);
      }
   }

   static boolean deerNear(ServerLevel level, int x, int z, int r) {
      AABB box = new AABB(x - r, level.getMinBuildHeight(), z - r, x + r, level.getMaxBuildHeight(), z + r);
      if (!level.getEntitiesOfClass(Whitetail.class, box, d -> d.isAlive() && !d.downed() && d.species() == GameSpecies.WHITETAIL).isEmpty()) {
         return true;
      }
      long now = level.getGameTime();
      for (HomeRange h : RoutineStore.of(level).near(GameSpecies.WHITETAIL, new BlockPos(x, 64, z), r)) {
         if (h.ready() && now - h.lastSeen < 48000L) {
            return true;
         }
      }
      return false;
   }

   /**
    * Picks a beginner area where whitetails are (live animals in loaded land, established home ranges), and [fharea] only
    * an easy one: open grass or easy open woods, gentle, dry ground, a walk from the hunter that needs no climbing and no
    * swim ({@link BeginnerArea}). If deer are only known in hard country the hunter scouts instead (no marker).
    */
   static boolean assignArea(ServerPlayer p, CompoundTag d, boolean another) {
      ServerLevel level = p.serverLevel();
      boolean hadOld = d.getBoolean("area") && another;
      BeginnerArea.Choice c = BeginnerArea.choose(level, p.blockPosition(), hadOld, d.getInt("areaX"), d.getInt("areaZ"));
      if (c == null) {
         d.putBoolean("scouting", true);
         d.putBoolean("rough", BeginnerArea.lastRough);
         return false;
      }
      d.putBoolean("area", true);
      d.putBoolean("scouting", false);
      d.putBoolean("rough", false);
      d.putString("areaDim", level.dimension().location().toString());
      d.putInt("areaX", c.x());
      d.putInt("areaZ", c.z());
      d.putInt("areaR", AREA_RADIUS);
      d.putInt("advice", c.advice());
      d.putInt("areaV", c.verified() ? 2 : 1); // [fharea] 2: the ground was seen and is easy; 1: judged by its biomes, the ground is checked on arrival
      d.putBoolean("areaConf", true);
      d.putBoolean("areaQuiet", false);
      live(p).quiet = 0;
      sync(p, true);
      return true;
   }

   /**
    * [fharea] An area chosen before the ground could be seen (or one from an earlier version, chosen by older rules) is judged again: at once
    * by its biomes, and on the ground once the hunter is close enough for the land to be loaded. A rough one is moved.
    */
   static void verifyArea(ServerPlayer p, CompoundTag d, Live l) {
      ServerLevel level = p.serverLevel();
      long now = level.getGameTime();
      int ver = d.getInt("areaV");
      int ax = d.getInt("areaX"), az = d.getInt("areaZ"), r = Math.max(16, d.getInt("areaR"));
      boolean same = l.verifyX == ax && l.verifyZ == az;
      if (ver >= 2 || same && now < l.verifyAt && l.verifyAt - now < 1200L) {
         return;
      }
      l.verifyX = ax;
      l.verifyZ = az;
      double dist = Math.hypot(p.getX() - ax, p.getZ() - az);
      if (ver >= 1 && dist > r * 2.5) {
         return; // judged by its biomes already; the ground is looked at on arrival
      }
      l.verifyAt = now + 200L;
      BeginnerArea.Site s = BeginnerArea.judge(level, ax, az, r);
      if (s.ok) {
         if (s.verified()) {
            d.putInt("areaV", 2);
         } else if (ver == 0) {
            d.putInt("areaV", 1);
         }
         return;
      }
      LOG.debug("[FrontierHunts] first hunt: beginner area at {} {} moved: {}", ax, az, s.why);
      if (assignArea(p, d, true)) {
         p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.area.moved").withStyle(ChatFormatting.GOLD), true);
      } else {
         d.putBoolean("area", false); // never leave a new hunter pointed at rough country
         d.putBoolean("areaQuiet", false);
         d.putLong("areaRetry", now + 600L);
         p.displayClientMessage(Component.translatable(d.getBoolean("rough") ? "firsthunt.frontierhunts.area.rough" : "firsthunt.frontierhunts.area.none")
            .withStyle(ChatFormatting.GOLD), true);
         sync(p, true);
      }
   }

   /** the advice line for an area: the anchor it was found by, else its centre biome's name ([fharea] no chunk lookups) */
   static int adviceFor(String biomePath, int tier, int anchor) {
      if (anchor == HomeRange.BED) {
         return ADVICE_BEDDING;
      }
      if (tier == BeginnerArea.OPEN) {
         return ADVICE_MEADOW;
      }
      String path = biomePath.substring(biomePath.indexOf(':') + 1);
      if (path.contains("lake") || path.contains("bottom") || path.contains("river") || anchor == HomeRange.WATER) {
         return ADVICE_WATER;
      }
      if (path.contains("meadow") || path.contains("plains") || path.contains("glade") || path.contains("parkland") || path.contains("heath")
         || path.contains("moor") || path.contains("bench") || path.contains("savanna") || path.contains("burn")) {
         return ADVICE_MEADOW;
      }
      return ADVICE_FOREST;
   }

   // ============================================================================================ player actions

   public static void action(ServerPlayer p, byte a) {
      if (p.hasDisconnected()) {
         return; // the request arrived after the hunter left: their save is written, paying now would pay into nothing (or twice)
      }
      try {
         CompoundTag d = data(p);
         switch (a) {
            case FirstHuntNetwork.A_SYNC -> sync(p, true);
            case FirstHuntNetwork.A_START -> {
               d.putBoolean("offered", true);
               begin(p, false);
            }
            case FirstHuntNetwork.A_INTRO_SEEN -> {
               d.putBoolean("intro", false);
               sync(p, true);
            }
            case FirstHuntNetwork.A_HIDE, FirstHuntNetwork.A_SHOW -> {
               d.putBoolean("hidden", a == FirstHuntNetwork.A_HIDE);
               sync(p, true);
            }
            case FirstHuntNetwork.A_PERMIT -> permit(p, d);
            case FirstHuntNetwork.A_NEW_AREA -> {
               Live l = live(p);
               long now = p.serverLevel().getGameTime();
               if (l.lastRequest != Long.MIN_VALUE && now - l.lastRequest < 200L && now >= l.lastRequest) {
                  return;
               }
               l.lastRequest = now;
               if (!assignArea(p, d, true)) {
                  p.displayClientMessage(Component.translatable(d.getBoolean("rough") && !d.getBoolean("area") ? "firsthunt.frontierhunts.area.rough"
                     : "firsthunt.frontierhunts.area.none").withStyle(ChatFormatting.GOLD), true); // [fharea]
                  sync(p, true);
               } else {
                  p.displayClientMessage(Component.translatable("firsthunt.frontierhunts.area.new").withStyle(ChatFormatting.GREEN), true);
               }
            }
            case FirstHuntNetwork.A_CLAIM -> claim(p, d);
            default -> {
            }
         }
      } catch (RuntimeException ex) {
         LOG.warn("[FrontierHunts] first hunt: action {}", a, ex);
      }
   }

   /**
    * [1.2.9] There is no free permit any more: the licence comes only from finishing the whole Ranger Academy and a deer
    * tag only from a ranger counter. A client still asking (an old build) just gets its picture refreshed.
    */
   static void permit(ServerPlayer p, CompoundTag d) {
      sync(p, true);
   }

   /** The reward, once: the "claimed" mark and the items are in the same player save; tokens are committed straight after. */
   static void claim(ServerPlayer p, CompoundTag d) {
      if (d.getBoolean("claimed") || (d.getInt("done") & Step.HARVEST.bit()) == 0 || !d.getBoolean("started")) {
         return;
      }
      d.putBoolean("claimed", true);
      d.putInt("done", ALL);
      com.formaworks.frontierhunts.camps.Tokens.credit(p.serverLevel(), p.getUUID(), REWARD_TOKENS);
      Item arrow = BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:field_arrow"));
      if (arrow != null && arrow != Items.AIR) {
         ItemStack st = new ItemStack(arrow, REWARD_ARROWS);
         if (!p.getInventory().add(st)) {
            ItemEntity ie = p.drop(st, false);
            if (ie != null) {
               ie.setNoPickUpDelay();
            }
         }
      }
      p.inventoryMenu.broadcastChanges();
      JournalApi.count(p, "firsthunt.claimed", 1);
      JournalApi.note(p, "First hunt complete: permit, sign, wind, shot, track and harvest. Reward: " + REWARD_TOKENS + " tokens and "
         + REWARD_ARROWS + " field arrows.");
      p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
      p.sendSystemMessage(Component.translatable("firsthunt.frontierhunts.claimed", REWARD_TOKENS, REWARD_ARROWS).withStyle(ChatFormatting.GOLD));
      Durability.commit(p.server, "first hunt reward");
      sync(p, true);
   }

   // ============================================================================================ sync

   static void sync(ServerPlayer p, boolean force) {
      if (p.connection == null) {
         return;
      }
      CompoundTag d = data(p);
      int flags = 0;
      int months = 0, daysToOpen = 0, academy = 0, tagTokens = 0;
      if (d.getBoolean("started")) {
         flags |= FirstHuntNetwork.F_STARTED;
         LicenceStatus.Status st = LicenceStatus.of(p, SPECIES);
         months = st.months();
         daysToOpen = st.daysToOpen();
         flags |= st.regulated() ? FirstHuntNetwork.F_REGULATED : 0;
         flags |= st.licence() ? FirstHuntNetwork.F_LICENCE : 0;
         flags |= st.tag() ? FirstHuntNetwork.F_TAG : 0;
         flags |= st.open() ? FirstHuntNetwork.F_OPEN : 0;
         flags |= st.suspended() ? FirstHuntNetwork.F_SUSPENDED : 0;
         academy = com.formaworks.frontierhunts.licence.LicenceOffice.academyPassed(p); // [1.2.9] the licence is the whole Academy
         tagTokens = com.formaworks.frontierhunts.journal.RankPerks.price(p, com.formaworks.frontierhunts.licence.Regulations.TagKind.DEER.tokens);
      }
      flags |= d.getBoolean("hidden") ? FirstHuntNetwork.F_HIDDEN : 0;
      flags |= d.getBoolean("intro") ? FirstHuntNetwork.F_INTRO : 0;
      flags |= d.getBoolean("claimed") ? FirstHuntNetwork.F_CLAIMED : 0;
      flags |= d.getBoolean("area") ? FirstHuntNetwork.F_AREA : 0;
      flags |= d.getBoolean("areaConf") ? FirstHuntNetwork.F_AREA_CONFIRMED : 0;
      flags |= d.getBoolean("areaQuiet") ? FirstHuntNetwork.F_AREA_QUIET : 0;
      flags |= d.getBoolean("scouting") && !d.getBoolean("area") ? FirstHuntNetwork.F_SCOUTING : 0;
      flags |= d.getBoolean("scouting") && !d.getBoolean("area") && d.getBoolean("rough") ? FirstHuntNetwork.F_ROUGH : 0; // [fharea]
      flags |= d.getBoolean("area") && d.getString("areaDim").equals(p.level().dimension().location().toString()) ? FirstHuntNetwork.F_AREA_HERE : 0;
      Live l = live(p);
      flags |= l.downwind ? FirstHuntNetwork.F_DOWNWIND : 0;
      int campX = 0, campZ = 0;
      try {
         var camp = com.formaworks.frontierhunts.livingworld.SpawnSite.Data.get(p.server.overworld());
         if (camp != null && camp.pos != null && p.level() == p.server.overworld()) {
            flags |= FirstHuntNetwork.F_CAMP;
            campX = camp.pos.getX();
            campZ = camp.pos.getZ();
         }
      } catch (RuntimeException ignored) {
      }
      FirstHuntNetwork.State s = new FirstHuntNetwork.State(d.getInt("done") & ALL, flags, months, daysToOpen, d.getInt("areaX"), d.getInt("areaZ"),
         d.getInt("areaR"), d.getInt("advice"), campX, campZ, l.windSeconds, academy, tagTokens);
      int hash = s.hashCode();
      if (force || hash != l.sentHash) {
         l.sentHash = hash;
         FirstHuntNetwork.send(p, s);
      }
   }

   // ============================================================================================ command

   @SubscribeEvent
   public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
      var self = net.minecraft.commands.Commands.literal("firsthunt")
         .executes(c -> status(c.getSource().getPlayerOrException(), c.getSource()));
      for (String[] a : new String[][]{{"start", "1"}, {"hide", "3"}, {"show", "4"}, {"area", "6"}, {"claim", "7"}}) {
         byte code = Byte.parseByte(a[1]);
         self.then(net.minecraft.commands.Commands.literal(a[0]).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            action(p, code);
            return status(p, c.getSource());
         }));
      }
      // [fharea] what the beginner-area judge thinks of the hunter's area and of the ground they stand on
      self.then(net.minecraft.commands.Commands.literal("areainfo").requires(src -> src.hasPermission(2)).executes(c -> {
         ServerPlayer p = c.getSource().getPlayerOrException();
         CompoundTag d = data(p);
         String here = BeginnerArea.explain(p.serverLevel(), p.getBlockX(), p.getBlockZ());
         String area = d.getBoolean("area") ? BeginnerArea.explain(p.serverLevel(), d.getInt("areaX"), d.getInt("areaZ")) : "none";
         c.getSource().sendSuccess(() -> Component.literal("Beginner area: " + area), false);
         c.getSource().sendSuccess(() -> Component.literal("Here: " + here), false);
         return 1;
      }));
      self.then(net.minecraft.commands.Commands.literal("reset").requires(src -> src.hasPermission(2))
         .then(net.minecraft.commands.Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players()).executes(c -> {
            int n = 0;
            for (ServerPlayer p : net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "targets")) {
               CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
               root.remove(KEY);
               p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
               LIVE.remove(p.getUUID());
               begin(p, true);
               n++;
            }
            int count = n;
            c.getSource().sendSuccess(() -> Component.literal("First hunt restarted for " + count + " hunter(s)."), true);
            return n;
         })));
      e.getDispatcher().register(net.minecraft.commands.Commands.literal("frontierhunts").then(self));
   }

   static int status(ServerPlayer p, net.minecraft.commands.CommandSourceStack src) {
      CompoundTag d = data(p);
      Step s = current(d.getInt("done"));
      LicenceStatus.Status st = LicenceStatus.of(p, SPECIES);
      String line = !d.getBoolean("started") ? "First hunt: not started (/frontierhunts firsthunt start)."
         : d.getBoolean("claimed") ? "First hunt: complete, reward claimed."
         : "First hunt: step " + (s.ordinal() + 1) + " of 7 (" + s.key() + "). Licence " + (st.licence() ? "yes" : "no") + ", deer tag " + (st.tag() ? "yes" : "no")
            + ", season " + (st.open() ? "open" : "closed, opens in " + st.daysToOpen() + " days") + (st.regulated() ? "" : " (licences off)")
            + (d.getBoolean("area") ? ", area at " + d.getInt("areaX") + " " + d.getInt("areaZ") + (d.getBoolean("areaQuiet") ? " (quiet)" : "")
               + (d.getInt("areaV") >= 2 ? " (ground checked)" : " (ground checked on arrival)")
               : d.getBoolean("rough") ? ", no area: deer only in rough country" : ", no area yet")
            + ", Ranger Academy " + Integer.bitCount(com.formaworks.frontierhunts.licence.LicenceOffice.academyPassed(p)) + "/"
            + com.formaworks.frontierhunts.academy.Course.curriculum().length + " courses.";
      src.sendSuccess(() -> Component.literal(line), false);
      return s.ordinal() + 1;
   }
}
