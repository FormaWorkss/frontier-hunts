package com.formaworks.frontierhunts.wingshot;

import com.formaworks.frontierhunts.wildlife2026.WildlifeContent;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [wingshot] {@code /frontierhunts birds ...} (op 2) to see the flight on demand: flush the nearest bird, send a flock
 * of mallards past you, call the nearest ducks in to your spread, start a grouse drumming, bring every bird down,
 * and a status read-out.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class BirdCommands {
   private BirdCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("frontierhunts").then(Commands.literal("birds").requires(s -> s.hasPermission(2))
         .executes(BirdCommands::status)
         .then(Commands.literal("status").executes(BirdCommands::status))
         .then(Commands.literal("flush").executes(BirdCommands::flush))
         .then(Commands.literal("pass").executes(c -> pass(c, 4))
            .then(Commands.argument("count", IntegerArgumentType.integer(1, 8)).executes(c -> pass(c, IntegerArgumentType.getInteger(c, "count")))))
         .then(Commands.literal("decoy").executes(BirdCommands::decoy))
         .then(Commands.literal("drum").executes(BirdCommands::drum))
         .then(Commands.literal("land").executes(BirdCommands::land))));
   }

   private static WildlifeMob nearest(CommandSourceStack s, WildlifeSpecies only, double r) {
      WildlifeMob best = null;
      double bd = Double.MAX_VALUE;
      for (WildlifeMob m : BirdFlight.birds(s.getLevel(), s.getPosition(), r)) {
         if (only != null && m.species != only) {
            continue;
         }
         double d = m.position().distanceToSqr(s.getPosition());
         if (d < bd) {
            bd = d;
            best = m;
         }
      }
      return best;
   }

   private static int flush(CommandContext<CommandSourceStack> c) {
      WildlifeMob m = nearest(c.getSource(), null, 64.0);
      if (m == null) {
         c.getSource().sendFailure(Component.literal("No duck or grouse within 64 blocks."));
         return 0;
      }
      boolean ok = BirdFlight.startle(m, c.getSource().getPosition());
      c.getSource().sendSuccess(() -> Component.literal(ok ? "The " + m.species.id + " is up." : "Flight is off (wingshot.realisticFlight)."), false);
      return ok ? 1 : 0;
   }

   /** A flock flying across in front of you, 18-20 blocks up: the passing shot. */
   private static int pass(CommandContext<CommandSourceStack> c, int count) {
      CommandSourceStack s = c.getSource();
      ServerLevel level = s.getLevel();
      Vec3 p = s.getPosition();
      double yaw = Math.toRadians((s.getEntity() != null ? s.getEntity().getYRot() : 0.0F) + 90.0F);
      double fx = Math.cos(yaw), fz = Math.sin(yaw);
      double rx = -fz, rz = fx;
      double start = 70.0;
      Vec3 st = null;
      for (; start >= 24.0; start -= 8.0) {
         Vec3 cand = new Vec3(p.x + fx * 14.0 - rx * start, p.y + 19.0, p.z + fz * 14.0 - rz * start);
         if (level.isLoaded(BlockPos.containing(cand))) {
            st = cand;
            break;
         }
      }
      if (st == null || !WingshotConfig.flight()) {
         s.sendFailure(Component.literal("No loaded room for a passing flock (or flight is off)."));
         return 0;
      }
      double heading = Math.atan2(rz, rx);
      double tx = p.x + fx * 14.0 + rx * 140.0, tz = p.z + fz * 14.0 + rz * 140.0;
      Flight lead = null;
      boolean echelon = level.random.nextBoolean();
      for (int i = 0; i < count; i++) {
         WildlifeMob m = WildlifeContent.TYPES.get(WildlifeSpecies.DUCK).get().create(level);
         if (m == null) {
            continue;
         }
         int side = (i & 1) == 1 ? 1 : -1, rank = (i + 1) / 2;
         double ox = -rx * 2.2 * rank + -rz * side * 1.5 * rank, oz = -rz * 2.2 * rank + rx * side * 1.5 * rank;
         m.moveTo(st.x + ox, st.y + rank * 0.2, st.z + oz, (float)Math.toDegrees(heading) - 90.0F, 0.0F);
         m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
         level.addFreshEntity(m);
         if (lead == null) {
            lead = BirdFlight.pass(m, level, heading, tx, tz);
         } else {
            BirdFlight.Ctl oc = BirdFlight.ctl(m);
            Flight f = BirdFlight.newFlight(m, level);
            f.heading = heading;
            f.speed = f.kind.cruise;
            f.follow(lead, i, echelon, 0, true);
            BirdFlight.begin(m, oc, f);
         }
      }
      int n = count;
      s.sendSuccess(() -> Component.literal(n + " mallards coming across from your left, about 19 blocks up."), false);
      return 1;
   }

   /** The nearest ducks answer as if you had blown the duck call over your spread (or the nearest open water). */
   private static int decoy(CommandContext<CommandSourceStack> c) {
      CommandSourceStack s = c.getSource();
      ServerLevel level = s.getLevel();
      Vec3 p = s.getPosition();
      List<BlockPos> decoys = com.formaworks.frontierhunts.hunts.HuntContent.liveDecoys(level, p.x, p.y, p.z, 40.0, 12);
      Vec3 land = null;
      boolean water = true;
      if (!decoys.isEmpty()) {
         double x = 0, y = 0, z = 0;
         for (BlockPos b : decoys) {
            x += b.getX() + 0.5;
            y += b.getY() + 0.9;
            z += b.getZ() + 0.5;
         }
         land = BirdLife.waterNear(level, x / decoys.size() + 2.0, z / decoys.size() - 2.0);
         if (land == null) {
            land = new Vec3(x / decoys.size(), y / decoys.size(), z / decoys.size());
         }
      } else {
         land = BirdLife.waterNear(level, p.x, p.z);
         for (int k = 1; land == null && k < 6; k++) {
            land = BirdLife.waterNear(level, p.x + Math.cos(k * 1.3) * k * 8.0, p.z + Math.sin(k * 1.3) * k * 8.0);
         }
      }
      if (land == null) {
         s.sendFailure(Component.literal("No decoys or open water near you."));
         return 0;
      }
      int n = 0;
      for (WildlifeMob m : BirdFlight.birds(level, p, 128.0)) {
         if (m.species == WildlifeSpecies.DUCK && n < 8 && BirdFlight.lure(m, land, water)) {
            n++;
         }
      }
      int fn = n;
      Vec3 fl = land;
      s.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%d ducks coming in to %.0f %.0f %.0f.", fn, fl.x, fl.y, fl.z)), false);
      return n;
   }

   private static int drum(CommandContext<CommandSourceStack> c) {
      WildlifeMob m = nearest(c.getSource(), WildlifeSpecies.GROUSE, 64.0);
      if (m == null) {
         c.getSource().sendFailure(Component.literal("No grouse within 64 blocks."));
         return 0;
      }
      BirdFlight.Ctl ctl = BirdFlight.ctl(m);
      if (ctl.f != null) {
         c.getSource().sendFailure(Component.literal("That grouse is flying."));
         return 0;
      }
      BirdLife.start(m, ctl, c.getSource().getLevel());
      ctl.drum.nextBout = 40;
      c.getSource().sendSuccess(() -> Component.literal(ctl.drum.log != null ? "The grouse is walking to a log to drum." : "No log near it: it drums on the ground."), false);
      return 1;
   }

   private static int land(CommandContext<CommandSourceStack> c) {
      int n = 0;
      for (WildlifeMob m : BirdFlight.birds(c.getSource().getLevel(), c.getSource().getPosition(), 128.0)) {
         Flight f = BirdFlight.flightOf(m);
         if (f != null && f.mission != Flight.Mission.FALL) {
            f.leader = null;
            f.settle();
            n++;
         }
      }
      int fn = n;
      c.getSource().sendSuccess(() -> Component.literal(fn + " birds coming down."), false);
      return n;
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      StringBuilder b = new StringBuilder();
      int n = 0;
      for (WildlifeMob m : BirdFlight.birds(c.getSource().getLevel(), c.getSource().getPosition(), 128.0)) {
         Flight f = BirdFlight.flightOf(m);
         BirdFlight.Ctl ctl = BirdFlight.ctl(m);
         if (n++ >= 12) {
            break;
         }
         double floor = BirdFlight.World.of(c.getSource().getLevel(), m).floor(m.getX(), m.getZ(), false);
         b.append(String.format(Locale.ROOT, "%s #%d %s phase %d", m.species.id, m.getId(), f == null ? (ctl.drum != null ? "drumming" : "down") : f.mission.name().toLowerCase(Locale.ROOT),
            m.flightPhase()));
         if (f != null) {
            b.append(String.format(Locale.ROOT, " stage %d, %.0f m up, %.1f m/s, bank %.0f deg", f.stage(), m.getY() - floor, f.speed * 20.0, Math.toDegrees(f.bank())));
         }
         b.append('\n');
      }
      String out = n == 0 ? "No ducks or grouse within 128 blocks." : b.toString().trim();
      c.getSource().sendSuccess(() -> Component.literal(out), false);
      return n;
   }
}
