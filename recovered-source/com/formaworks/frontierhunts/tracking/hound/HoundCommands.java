package com.formaworks.frontierhunts.tracking.hound;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.PrintKind;
import com.formaworks.frontierhunts.tracking.ScentLedger;
import com.formaworks.frontierhunts.tracking.TrailMark;
import com.formaworks.frontierhunts.tracking.TrailService;
import com.formaworks.frontierhunts.tracking.TrailStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

/**
 * [tracking][hound3] Server side of the hound's commands: the Hound Lead (get your hound; aim it at sign to put him on
 * that line), the command wheel / call key orders (heel, stay, track, search, come) - all validated here.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class HoundCommands {
   private static final Map<UUID, Long> LAST = new HashMap<>();
   private static final Map<UUID, Long> LAST_ORDER = new HashMap<>(); // [hound3]
   private static final Map<UUID, Long> LAST_INFO = new HashMap<>(); // [hound3]
   /** how far a whistle / call carries */
   static final double EARSHOT = 160.0;
   /** a hound not seen for this long (lost in another dimension, far chunks ...) can be replaced */
   private static final long REPLACE_AFTER = 24000L;

   private HoundCommands() {
   }

   private static boolean rate(ServerPlayer p) {
      long now = p.level().getGameTime();
      Long last = LAST.put(p.getUUID(), now);
      return last == null || now < last || now - last >= 8L;
   }

   private static void say(ServerPlayer p, String text) {
      p.displayClientMessage(Component.literal(text), true);
   }

   /** Hound Lead used on nothing in particular. */
   public static void useLead(ServerPlayer p) {
      if (!rate(p)) {
         return;
      }
      HoundRegistry reg = HoundRegistry.get(p.server);
      HoundRegistry.Entry e = reg.get(p.getUUID());
      TrackingHound h = HoundRegistry.find(p.server, p.getUUID());
      long now = p.server.overworld().getGameTime();
      if (e != null && e.home) { // [hound4] back from the kennel: the same dog
         spawn(p, e.name, e.variant);
         return;
      }
      if (e == null || h == null && now - e.seen > REPLACE_AFTER) {
         spawn(p, null, -1);
         return;
      }
      if (h == null) {
         say(p, String.format("Your hound is out of earshot · last seen near %d, %d", Math.round(e.x), Math.round(e.z)));
         return;
      }
      if (h.level() != p.level() || h.distanceToSqr(p) > 160.0 * 160.0) {
         say(p, h.getName().getString() + " can't hear you from here");
         return;
      }
      // [hound3] plain use opens the command wheel on the client (nothing to do here) while he is close by; sneak-use, or a
      // plain use when he is out past where the client may not see him, calls him in
      if (!p.isShiftKeyDown() && h.distanceToSqr(p) < 48.0 * 48.0) {
         return;
      }
      p.level().playSound(null, p.getX(), p.getY() + 1.5, p.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.45F, 2.0F);
      h.come(p);
      say(p, Component.translatable("hound.frontierhunts.said.come", h.getName()));
   }

   private static void spawn(ServerPlayer p, String name, int variant) {
      ServerLevel level = p.serverLevel();
      TrackingHound h = HoundContent.HOUND.get().create(level);
      if (h == null) {
         return;
      }
      Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
      look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
      Vec3 at = p.position().add(look.scale(-1.6));
      BlockPos pos = BlockPos.containing(at);
      if (!level.noCollision(h.getType().getSpawnAABB(at.x, pos.getY(), at.z))) {
         at = p.position();
      }
      h.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180.0F, 0.0F);
      h.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
      h.setup(p);
      boolean back = name != null; // [hound4] returning from the kennel keeps his name and coat
      if (back) {
         h.restore(name, variant);
      }
      level.addFreshEntity(h);
      HoundRegistry.get(p.server).put(p.getUUID(), h);
      level.playSound(null, h.getX(), h.getY() + 0.5, h.getZ(), SoundEvents.WOLF_AMBIENT, SoundSource.NEUTRAL, 1.2F, 0.8F);
      level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, h.getX(), h.getY() + 0.4, h.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
      if (back) {
         p.displayClientMessage(Component.translatable("hound.frontierhunts.said.back", h.getName()), true);
         return;
      }
      p.sendSystemMessage(Component.translatable("hound.frontierhunts.said.new", h.getName(), Component.translatable("hound.frontierhunts.coat." + h.coat()),
         Component.keybind("key.frontierhunts.hound")));
   }

   /** Hound Lead used while aiming at a trail mark: put the hound's nose on that animal's line. */
   public static void track(ServerPlayer p, UUID markId) {
      if (!rate(p) || markId == null) {
         return;
      }
      if (!(p.getMainHandItem().is(HoundContent.LEAD.get()) || p.getOffhandItem().is(HoundContent.LEAD.get()))) {
         return;
      }
      TrailMark m = TrailService.aimedMark(p, markId);
      if (m == null) {
         return;
      }
      if (!TrailService.active(p.level())) {
         say(p, "Tracking is switched off here (gamerule frontierTracking)");
         return;
      }
      if (m.animal().equals(TrailMark.UNKNOWN)) {
         say(p, "Too old and trampled for a hound to own");
         return;
      }
      if (m.print() && (m.sign() == PrintKind.BOOT.ordinal() || m.sign() == PrintKind.HOUND.ordinal())) {
         say(p, "A tracking hound won't run a person's or a dog's line");
         return;
      }
      TrackingHound h = HoundRegistry.find(p.server, p.getUUID());
      if (h == null) {
         say(p, HoundRegistry.get(p.server).get(p.getUUID()) == null ? "You have no hound yet · use the Hound Lead" : "Your hound is out of earshot");
         return;
      }
      if (h.level() != p.level() || h.distanceToSqr(p) > 48.0 * 48.0) {
         say(p, h.getName().getString() + " needs to be with you to take the line");
         return;
      }
      h.startTrack(m);
      say(p, h.getName().getString() + " drops nose to the " + m.kind() + " · follow the bawl");
      com.formaworks.frontierhunts.guide.FieldSchool.houndTrack(p, m); // [guide] Field School lesson 6
   }

   // ------------------------------------------------------------------------------------------------ [hound3] orders

   private static boolean orderRate(ServerPlayer p) {
      long now = p.level().getGameTime();
      Long last = LAST_ORDER.put(p.getUUID(), now);
      return last == null || now < last || now - last >= 3L;
   }

   /** the owner's hound within earshot, or null (and the hunter is told why) */
   private static TrackingHound hound(ServerPlayer p, boolean tell) {
      HoundRegistry.Entry e = HoundRegistry.get(p.server).get(p.getUUID());
      TrackingHound h = HoundRegistry.find(p.server, p.getUUID());
      if (h == null) {
         if (tell) {
            p.displayClientMessage(e == null ? Component.translatable("hound.frontierhunts.said.none")
               : Component.translatable("hound.frontierhunts.said.far", Math.round(e.x), Math.round(e.z)), true);
         }
         return null;
      }
      if (h.level() != p.level() || h.distanceToSqr(p) > EARSHOT * EARSHOT) {
         if (tell) {
            p.displayClientMessage(Component.translatable("hound.frontierhunts.said.cant_hear", h.getName()), true);
         }
         return null;
      }
      return h;
   }

   /** an order from the command wheel or the call key */
   public static void order(ServerPlayer p, int order, UUID target) {
      if (order < 0 || order > HoundNet.LAST || !p.isAlive() || p.isSpectator()) {
         return;
      }
      if (order == HoundNet.INFO) {
         // the wheel opened: own rate so a quick order right after it is not swallowed
         long now = p.level().getGameTime();
         Long last = LAST_INFO.put(p.getUUID(), now);
         if (last == null || now < last || now - last >= 5L) {
            info(p);
         }
         return;
      }
      if (!orderRate(p)) {
         return;
      }
      if (order == HoundNet.DISMISS) { // [hound4]
         dismiss(p);
         return;
      }
      TrackingHound h = hound(p, true);
      if (h == null) {
         return;
      }
      switch (order) {
         case HoundNet.HEEL -> {
            whistle(p, 1.9F);
            h.sit(false);
            say(p, Component.translatable("hound.frontierhunts.said.heel", h.getName()));
         }
         case HoundNet.STAY -> {
            h.sit(true);
            say(p, Component.translatable("hound.frontierhunts.said.sit", h.getName()));
         }
         case HoundNet.STOP -> { // [hound4]
            whistle(p, 1.4F);
            h.halt();
            say(p, Component.translatable("hound.frontierhunts.said.stop", h.getName()));
         }
         case HoundNet.TRACK -> trackOrder(p, h, target);
         case HoundNet.SEARCH -> {
            if (!TrailService.active(p.level())) {
               say(p, Component.translatable("hound.frontierhunts.said.off"));
               return;
            }
            whistle(p, 1.6F);
            h.startSearch();
            say(p, Component.translatable("hound.frontierhunts.said.search", h.getName()));
         }
         case HoundNet.COME -> {
            whistle(p, 2.0F);
            h.come(p);
            say(p, Component.translatable("hound.frontierhunts.said.come", h.getName()));
         }
         default -> {
         }
      }
   }

   /**
    * [hound4] send the owner's hound home (wheel "Dismiss" after a confirm click, or /hound dismiss): a loaded hound
    * leaves at once with a puff; one in unloaded chunks is marked and leaves when his chunk loads. Either way the
    * registry keeps only "in the kennel" (name + coat) - no hound entity, no work state is left behind - and the
    * Hound Lead brings the same dog back.
    */
   public static boolean dismiss(ServerPlayer p) {
      HoundRegistry reg = HoundRegistry.get(p.server);
      HoundRegistry.Entry e = reg.get(p.getUUID());
      if (e == null) {
         say(p, Component.translatable("hound.frontierhunts.said.none"));
         return false;
      }
      if (e.home) {
         say(p, Component.translatable("hound.frontierhunts.said.already_home"));
         return false;
      }
      TrackingHound h = HoundRegistry.find(p.server, p.getUUID());
      String name;
      if (h != null) {
         name = h.getName().getString();
         h.dismiss();
      } else {
         reg.kennel(p.getUUID());
         name = "";
      }
      p.displayClientMessage(name.isEmpty() ? Component.translatable("hound.frontierhunts.said.dismissed_far")
         : Component.translatable("hound.frontierhunts.said.dismissed", name), false);
      return true;
   }

   private static void whistle(ServerPlayer p, float pitch) {
      p.level().playSound(null, p.getX(), p.getY() + 1.5, p.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.45F, pitch);
   }

   private static void say(ServerPlayer p, Component c) {
      p.displayClientMessage(c, true);
   }

   /** TRACK: the animal picked in the wheel, else the newest one this hunter hit, else the nearest fresh blood / tracks */
   static boolean trackOrder(ServerPlayer p, TrackingHound h, UUID target) {
      ServerLevel level = p.serverLevel();
      if (!TrailService.active(level)) {
         say(p, Component.translatable("hound.frontierhunts.said.off"));
         return false;
      }
      long now = level.getGameTime();
      for (HoundWounds.Hit hit : HoundWounds.recent(p)) {
         if (target != null && !hit.animal().equals(target)) {
            continue;
         }
         boolean line = level.getEntity(hit.animal()) != null || ScentLedger.get(level).last(hit.animal()) != null;
         if (!line || hit.pos().distanceToSqr(p.position()) > 192.0 * 192.0) {
            continue;
         }
         whistle(p, 1.7F);
         h.startTrack(hit.animal(), hit.time() - 40L, hit.pos(), hit.label());
         say(p, Component.translatable("hound.frontierhunts.said.track", h.getName(), hit.label()));
         return true;
      }
      if (target != null) {
         say(p, Component.translatable("hound.frontierhunts.said.cold"));
         return false;
      }
      TrailMark best = null;
      double bs = Double.MAX_VALUE;
      for (TrailMark m : TrailStore.get(level).nearby(p.position(), 32.0, 1024, now)) {
         if (m.animal().equals(TrailMark.UNKNOWN) || m.face() != Direction.UP || !(m.blood() || m.print())) {
            continue;
         }
         if (m.print() && (m.sign() == PrintKind.BOOT.ordinal() || m.sign() == PrintKind.HOUND.ordinal())) {
            continue;
         }
         double sc = Math.sqrt(m.position().distanceToSqr(p.position())) + Math.max(0L, now - m.created()) / 600.0 - (m.blood() ? 12.0 : 0.0);
         if (sc < bs) {
            bs = sc;
            best = m;
         }
      }
      if (best != null) {
         whistle(p, 1.7F);
         h.startTrack(best);
         say(p, Component.translatable("hound.frontierhunts.said.sign", h.getName(), best.kind()));
         return true;
      }
      say(p, Component.translatable("hound.frontierhunts.said.nothing"));
      return false;
   }

   /** the wheel opened: tell the client which wounded animals the hound could be put on */
   private static void info(ServerPlayer p) {
      ServerLevel level = p.serverLevel();
      long now = level.getGameTime();
      TrackingHound h = HoundRegistry.find(p.server, p.getUUID());
      List<HoundNet.Wound> list = new ArrayList<>();
      for (HoundWounds.Hit hit : HoundWounds.recent(p)) {
         if (list.size() >= 4) {
            break;
         }
         Entity e = level.getEntity(hit.animal());
         boolean dead = e instanceof Whitetail w ? w.downed() || !w.isAlive() : e instanceof LivingEntity l && !l.isAlive();
         if (e == null && ScentLedger.get(level).last(hit.animal()) == null) {
            continue;
         }
         int secs = (int)Math.min(99999L, Math.max(0L, now - hit.time()) / 20L);
         int metres = (int)Math.round(Math.sqrt(hit.pos().distanceToSqr(p.position())));
         list.add(new HoundNet.Wound(hit.animal(), hit.label(), secs, metres, dead));
      }
      HoundNet.send(p, new HoundNet.Info(h == null ? "" : h.getName().getString(), list));
   }

   /** [hound4] /hound dismiss | sit | stop | heel | come - your own hound only (no op level needed), same rules as the wheel;
    * /hound summon (op) = using the Hound Lead */
   @SubscribeEvent
   public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent ev) {
      com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root = net.minecraft.commands.Commands.literal("hound");
      String[] names = {"dismiss", "sit", "stop", "heel", "come"};
      int[] orders = {HoundNet.DISMISS, HoundNet.STAY, HoundNet.STOP, HoundNet.HEEL, HoundNet.COME};
      for (int i = 0; i < names.length; i++) {
         int o = orders[i];
         root.then(net.minecraft.commands.Commands.literal(names[i]).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            LAST_ORDER.remove(p.getUUID()); // a typed command is never swallowed by the wheel's rate limit
            order(p, o, null);
            return 1;
         }));
      }
      // op-only (cheats / testing): exactly what using a Hound Lead does - a new hound, or yours back from the kennel
      root.then(net.minecraft.commands.Commands.literal("summon").requires(src -> src.hasPermission(2)).executes(c -> {
         ServerPlayer p = c.getSource().getPlayerOrException();
         LAST.remove(p.getUUID());
         useLead(p);
         return 1;
      }));
      ev.getDispatcher().register(root);
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent e) {
      LAST.remove(e.getEntity().getUUID());
      LAST_ORDER.remove(e.getEntity().getUUID());
      LAST_INFO.remove(e.getEntity().getUUID());
   }
}
