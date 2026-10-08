package com.formaworks.frontierhunts.regions.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.academy.Academy;
import com.formaworks.frontierhunts.academy.client.AcademyClient;
import com.formaworks.frontierhunts.client.ExpeditionClient;
import com.formaworks.frontierhunts.client.ExpeditionOptics;
import com.formaworks.frontierhunts.client.FrontierClient;
import com.formaworks.frontierhunts.client.HuntCinematics;
import com.formaworks.frontierhunts.client.KillCamClient;
import com.formaworks.frontierhunts.client.RifleClient;
import com.formaworks.frontierhunts.client.ScopeZoom;
import com.formaworks.frontierhunts.client.trailcam.Darkroom;
import com.formaworks.frontierhunts.landscape.ride.client.LaunchCinematic;
import com.formaworks.frontierhunts.regions.ArrivalTrigger;
import com.formaworks.frontierhunts.regions.RegionsConfig;
import com.mojang.brigadier.context.CommandContext;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * [regions] Arrival cards: the join card's cinematic title, now also when you walk, ride or fly into a new reserve region or
 * biome. Entirely client-side: the biome comes from the client's chunk data and the region from the mod's synced region
 * registry and biome tags, so it behaves the same in singleplayer and on a dedicated server, costs one biome lookup four
 * times a second and draws only GUI quads (no world rendering, so Distant Horizons, Xaero's maps and Iris shader packs are
 * untouched).
 *
 * <p>Joining a world or changing dimension: when the reserve's own join card plays (FrontierClient), its biome and season /
 * game lines are added underneath it; when it does not (reserve mode off, other dimensions) this shows its own region card.
 * Crossings are filtered by {@link ArrivalTrigger}; cards are held back while a menu is open, while fighting or aiming, during
 * the kill cam and other cinematics, and never shown in the Ranger Academy training grounds.</p>
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class ArrivalCards {
   private static final int SAMPLE = 5;
   private static final int SETTLE = 50;
   private static final ArrivalTrigger TRIGGER = new ArrivalTrigger();

   private static long clock;
   private static ResourceKey<Level> dimension;
   private static int settle = -1;
   private static int joinWait;
   private static boolean arrivalSeen;
   private static ArrivalCard card;
   private static float lastHealth = -1.0F;
   private static boolean hurtNow;
   private static volatile boolean tagsChanged;
   private static long combatUntil;
   private static long aimUntil;
   private static Vec3 lastPos;
   private static double speed;
   private static PlaceInfo.Place joinPlace;
   private static String joinSubtitle = "";

   private ArrivalCards() {
   }

   @EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         // below the vanilla HUD: the letterbox never covers the hotbar, health or crosshair
         e.registerBelow(VanillaGuiLayers.CROSSHAIR, FrontierHunts.id("arrival_card"), ArrivalCards::render);
      }
   }

   // ------------------------------------------------------------------------------------------------------------ tick
   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      LocalPlayer p = mc.player;
      if (level == null || p == null) {
         if (dimension != null) {
            clear(true);
         }
         return;
      }
      if (mc.isPaused()) {
         return;
      }
      clock++;
      if (tagsChanged) {
         tagsChanged = false;
         PlaceInfo.clear();
      }
      if (level.dimension() != dimension) {
         dimension = level.dimension();
         TRIGGER.reset(false);
         settle = SETTLE;
         joinWait = 0;
         arrivalSeen = false;
         joinPlace = null;
         lastPos = null;
         lastHealth = -1.0F;
         if (card != null) {
            card.abort();
         }
      }
      bookkeeping(mc, p);
      int arrival = JoinArrival.ticks();
      if (arrival > 0) {
         arrivalSeen = true;
         if (card != null) {
            card.abort(); // the reserve's own join card took the screen
         }
         if (joinPlace == null) {
            joinPlace = PlaceInfo.at(level, p.blockPosition());
            joinSubtitle = joinPlace == null ? "" : PlaceInfo.subtitle(level, joinPlace);
         }
      } else {
         joinPlace = null;
      }
      if (card != null) {
         if (!card.aborting() && interrupted(mc, p)) {
            card.abort();
         }
         card.tick();
         if (card.done()) {
            card = null;
         }
      }
      boolean training = Academy.isTrainingLevel(level);
      if (settle > 0) {
         if (mc.screen == null) {
            settle--;
         }
         if (settle == 0) {
            join(mc, level, p, training);
         }
         return;
      }
      if (clock % SAMPLE != 0) {
         return;
      }
      PlaceInfo.Place place = PlaceInfo.at(level, p.blockPosition());
      if (place == null) {
         return;
      }
      RegionsConfig.Mode mode = RegionsConfig.mode();
      if (training) {
         TRIGGER.seed(place.biomeId(), place.region(), clock, false);
         return;
      }
      boolean regions = mode != RegionsConfig.Mode.OFF;
      boolean biomes = mode == RegionsConfig.Mode.REGIONS_AND_BIOMES;
      ArrivalTrigger.Fire fire = TRIGGER.update(
         clock, place.biomeId(), place.region(), p.getX(), p.getZ(), !reasons(mc, p).isEmpty(), regions, biomes, RegionsConfig.repeatTicks()
      );
      if (fire != null) {
         show(level, p, place, fire.kind() == ArrivalTrigger.Kind.REGION ? ArrivalCard.Style.REGION : ArrivalCard.Style.BIOME);
      }
   }

   /** First look around after joining / changing dimension. */
   private static void join(Minecraft mc, ClientLevel level, LocalPlayer p, boolean training) {
      PlaceInfo.Place place = PlaceInfo.at(level, p.blockPosition());
      if (place == null) {
         settle = 10; // chunk not in yet
         return;
      }
      if (training) {
         TRIGGER.seed(place.biomeId(), place.region(), clock, false);
      } else if (arrivalSeen || JoinArrival.ticks() > 0) {
         TRIGGER.seed(place.biomeId(), place.region(), clock, true);
      } else if (RegionsConfig.mode() == RegionsConfig.Mode.OFF) {
         TRIGGER.seed(place.biomeId(), place.region(), clock, false);
      } else if (!reasons(mc, p).isEmpty()) {
         if (joinWait < 300) {
            joinWait += 5;
            settle = 5; // wait for the academy home card, a fight, aiming ... to finish (up to 15 s)
         } else {
            TRIGGER.seed(place.biomeId(), place.region(), clock, false);
         }
      } else {
         TRIGGER.seed(place.biomeId(), place.region(), clock, true);
         show(level, p, place, ArrivalCard.Style.REGION);
      }
   }

   private static void show(ClientLevel level, LocalPlayer p, PlaceInfo.Place place, ArrivalCard.Style style) {
      if (fast(p)) {
         style = ArrivalCard.Style.COMPACT;
      }
      card = new ArrivalCard(style, place.region(), place.biome(), PlaceInfo.subtitle(level, place), RegionsConfig.durationTicks());
   }

   /** Riding fast, gliding or elytra flight: the small card. */
   private static boolean fast(LocalPlayer p) {
      return p.isFallFlying() || p.isPassenger() && speed > 0.45 || speed > 0.9;
   }

   private static void bookkeeping(Minecraft mc, LocalPlayer p) {
      float hp = p.getHealth();
      hurtNow = lastHealth >= 0.0F && hp < lastHealth - 0.01F || p.hurtTime > 0;
      if (hurtNow) {
         combatUntil = clock + 200L;
      }
      lastHealth = hp;
      if (aiming(p)) {
         aimUntil = clock + 40L;
      }
      Entity root = p.getRootVehicle();
      Vec3 pos = root.position();
      if (lastPos != null) {
         double d = Math.min(8.0, Math.sqrt((pos.x - lastPos.x) * (pos.x - lastPos.x) + (pos.z - lastPos.z) * (pos.z - lastPos.z)));
         speed += (d - speed) * 0.2;
      }
      lastPos = pos;
   }

   /** Drawing a bow, raising a scope or spyglass (eating or blocking does not count). */
   private static boolean aiming(LocalPlayer p) {
      if (p.isUsingItem()) {
         UseAnim anim = p.getUseItem().getUseAnimation();
         if (anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR || anim == UseAnim.SPYGLASS) {
            return true;
         }
      }
      return safe(ExpeditionClient::aiming) || RifleClient.AIM.isDown() && safe(() -> ScopeZoom.power() > 0.0);
   }

   /** Something that should end a showing card early (taking damage, aiming, a cinematic, death). */
   private static boolean interrupted(Minecraft mc, LocalPlayer p) {
      return hurtNow || clock < aimUntil || cinematic() || !p.isAlive();
   }

   private static boolean cinematic() {
      return safe(HuntCinematics::active)
         || safe(KillCamClient::active)
         || safe(LaunchCinematic::running)
         || safe(ExpeditionOptics::active)
         || safe(Darkroom::active)
         || safe(AcademyClient::cinematicShowing);
   }

   /** Why a new card may not start right now (empty = free). */
   static List<String> reasons(Minecraft mc, LocalPlayer p) {
      List<String> r = new ArrayList<>(2);
      if (mc.screen != null) {
         r.add("menu");
      }
      if (mc.options.hideGui) {
         r.add("hud hidden");
      }
      if (clock < combatUntil) {
         r.add("combat");
      }
      if (clock < aimUntil) {
         r.add("aiming");
      }
      if (cinematic()) {
         r.add("cinematic");
      }
      if (JoinArrival.ticks() > 0) {
         r.add("join card");
      }
      if (card != null) {
         r.add("card showing");
      }
      if (!p.isAlive() || p.isSleeping()) {
         r.add("asleep/dead");
      }
      return r;
   }

   private static boolean safe(java.util.function.BooleanSupplier s) {
      try {
         return s.getAsBoolean();
      } catch (Throwable t) {
         return false;
      }
   }

   // ---------------------------------------------------------------------------------------------------------- render
   private static void render(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.hideGui || mc.screen != null || cinematic()) {
         return;
      }
      boolean reduced = reducedMotion();
      if (card != null) {
         card.render(g, mc.isPaused() ? 0.0F : dt.getGameTimeDeltaPartialTick(false), reduced);
      }
      renderJoinLines(g, mc);
   }

   /** Biome and season / game lines added under the reserve's join card, on its exact fade. */
   private static void renderJoinLines(GuiGraphics g, Minecraft mc) {
      int t = JoinArrival.ticks();
      HuntNetwork.Snapshot s = FrontierClient.state;
      PlaceInfo.Place place = joinPlace;
      if (t <= 0 || place == null || s == null || !s.active() || RegionsConfig.mode() == RegionsConfig.Mode.OFF) {
         return;
      }
      float f = Math.min(1.0F, Math.min((110 - t) / 16.0F, t / 22.0F));
      float a = Math.max(0.05F, f);
      int w = g.guiWidth();
      int h = g.guiHeight();
      ArrivalCard.line(g, mc.font, place.biome(), w / 2.0F, h / 3 + 34, w, ArrivalCard.CREAM, a * 0.95F);
      ArrivalCard.line(g, mc.font, joinSubtitle, w / 2.0F, h / 3 + 47, w, ArrivalCard.MUTED, a * 0.9F);
   }

   private static boolean reducedMotion() {
      try {
         return HuntConfig.REDUCED_MOTION.get();
      } catch (Throwable t) {
         return false;
      }
   }

   // ------------------------------------------------------------------------------------------------- lifecycle
   private static void clear(boolean cooldowns) {
      TRIGGER.reset(cooldowns);
      dimension = null;
      settle = -1;
      card = null;
      arrivalSeen = false;
      joinPlace = null;
      lastPos = null;
      lastHealth = -1.0F;
      combatUntil = 0L;
      aimUntil = 0L;
      speed = 0.0;
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
      clear(true);
      PlaceInfo.clear();
   }

   @SubscribeEvent
   public static void tags(TagsUpdatedEvent e) {
      if (e.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
         tagsChanged = true; // habitat tags may differ: re-resolve on the next tick (main thread)
      }
   }

   @SubscribeEvent
   public static void attack(InputEvent.InteractionKeyMappingTriggered e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.isAttack() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
         combatUntil = clock + 200L;
      }
   }

   // ---------------------------------------------------------------------------------------------------- command
   /** {@code /arrivalcard [show|biome|compact|status|reset|off|regions|biomes]}: test and tune arrival cards. */
   @SubscribeEvent
   public static void commands(RegisterClientCommandsEvent e) {
      var root = Commands.literal("arrivalcard").executes(ArrivalCards::status);
      root.then(Commands.literal("status").executes(ArrivalCards::status));
      root.then(Commands.literal("show").executes(c -> force(c, ArrivalCard.Style.REGION)));
      root.then(Commands.literal("biome").executes(c -> force(c, ArrivalCard.Style.BIOME)));
      root.then(Commands.literal("compact").executes(c -> force(c, ArrivalCard.Style.COMPACT)));
      root.then(Commands.literal("reset").executes(c -> {
         TRIGGER.reset(true);
         c.getSource().sendSystemMessage(Component.translatable("regions.frontierhunts.cmd.reset"));
         return 1;
      }));
      for (RegionsConfig.Mode m : RegionsConfig.Mode.values()) {
         String word = switch (m) {
            case OFF -> "off";
            case REGIONS -> "regions";
            case REGIONS_AND_BIOMES -> "biomes";
         };
         root.then(Commands.literal(word).executes(c -> {
            if (RegionsConfig.MODE != null) {
               RegionsConfig.MODE.set(m);
               try {
                  HuntConfig.CLIENT.save();
               } catch (RuntimeException ignored) {
                  // live either way
               }
            }
            return status(c);
         }));
      }
      e.getDispatcher().register(root);
   }

   private static int force(CommandContext<CommandSourceStack> c, ArrivalCard.Style style) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return 0;
      }
      PlaceInfo.Place place = PlaceInfo.at(mc.level, mc.player.blockPosition());
      if (place == null) {
         return 0;
      }
      card = new ArrivalCard(style, place.region(), place.biome(), PlaceInfo.subtitle(mc.level, place), RegionsConfig.durationTicks());
      TRIGGER.mark(style == ArrivalCard.Style.BIOME ? ArrivalTrigger.Kind.BIOME : ArrivalTrigger.Kind.REGION, place.biomeId(), place.region(), clock);
      return 1;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return 0;
      }
      PlaceInfo.Place place = PlaceInfo.at(mc.level, mc.player.blockPosition());
      List<String> why = reasons(mc, mc.player);
      if (Academy.isTrainingLevel(mc.level)) {
         why.add("training grounds");
      }
      String here = place == null ? "?" : place.biome() + " (" + place.biomeId() + ") in " + place.region() + (place.game().isEmpty() ? "" : " | " + place.game());
      c.getSource().sendSystemMessage(Component.literal("[Arrival cards] mode=" + RegionsConfig.mode().name().toLowerCase(Locale.ROOT)
         + " duration=" + RegionsConfig.durationTicks() / 20 + "s repeat=" + RegionsConfig.repeatTicks() / 1200 + "min speed=" + String.format(Locale.ROOT, "%.1f", speed * 20.0) + "m/s"));
      c.getSource().sendSystemMessage(Component.literal("here: " + here));
      c.getSource().sendSystemMessage(Component.literal(TRIGGER.describe(clock)));
      c.getSource().sendSystemMessage(Component.literal("blocked: " + (why.isEmpty() ? "no" : String.join(", ", why)) + (JoinArrival.available() ? "" : " (join-card hook unavailable)")));
      return 1;
   }

   // ------------------------------------------------------------------------------------------------- join card hook
   /** Reads FrontierClient's private join-card countdown (no edit to that class); 0 when unavailable. */
   static final class JoinArrival {
      private static Field field;
      private static boolean tried;

      private JoinArrival() {
      }

      static boolean available() {
         ticks();
         return field != null;
      }

      static int ticks() {
         if (!tried) {
            tried = true;
            try {
               Field f = FrontierClient.class.getDeclaredField("arrivalTicks");
               f.setAccessible(true);
               field = f;
            } catch (Throwable t) {
               field = null;
            }
         }
         if (field == null) {
            return 0;
         }
         try {
            return field.getInt(null);
         } catch (Throwable t) {
            return 0;
         }
      }
   }
}
