package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.journal.HuntBridge;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [hunts] {@code /frontierhunts hunts ...}: your hunt progress; for ops: test a milestone (feeds the simplest event that
 * meets it through the real tracker - rewards, toast, journal), read the field situation around the animal you look
 * at, blow a call or set bait without the item, reset a hunter's hunt progress.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class HuntCommands {
   private HuntCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("frontierhunts").then(Commands.literal("hunts")
         .executes(c -> status(c, c.getSource().getPlayerOrException()))
         .then(Commands.literal("test").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .then(Commands.argument("milestone", StringArgumentType.word())
                  .suggests((c, b) -> SharedSuggestionProvider.suggest(HuntBook.milestones().stream().map(HuntBook.Milestone::id), b))
                  .executes(HuntCommands::test))))
         .then(Commands.literal("context").requires(s -> s.hasPermission(2)).executes(HuntCommands::context))
         .then(Commands.literal("call").requires(s -> s.hasPermission(2))
            .then(Commands.literal("predator").executes(c -> call(c, Lures.Kind.PREDATOR)))
            .then(Commands.literal("duck").executes(c -> call(c, Lures.Kind.DUCK))))
         .then(Commands.literal("bait").requires(s -> s.hasPermission(2)).executes(HuntCommands::bait))
         .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players()).executes(HuntCommands::reset)))));
   }

   private static int status(CommandContext<CommandSourceStack> c, ServerPlayer p) {
      HunterRecord r = HuntBridge.find(p.server, p.getUUID());
      int total = 0, done = 0, mastered = 0;
      StringBuilder b = new StringBuilder();
      for (HuntBook.Hunt h : HuntBook.hunts()) {
         int d = 0;
         for (HuntBook.Milestone m : h.milestones()) {
            total++;
            if (r != null && r.done.contains(m.id())) {
               d++;
               done++;
            }
         }
         if (r != null && r.done.contains(h.master().id())) {
            mastered++;
         }
         b.append(h.species()).append(' ').append(d).append('/').append(h.milestones().size()).append("  ");
      }
      int fd = done, ft = total, fm = mastered;
      c.getSource().sendSuccess(() -> Component.translatable("hunts.frontierhunts.cmd.status", fd, ft, fm, HuntBook.hunts().size()), false);
      String line = b.toString().trim();
      c.getSource().sendSuccess(() -> Component.literal(line), false);
      return done;
   }

   private static int test(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
      HuntBook.Milestone m = HuntBook.byId(StringArgumentType.getString(c, "milestone"));
      if (m == null) {
         c.getSource().sendFailure(Component.translatable("hunts.frontierhunts.cmd.unknown"));
         return 0;
      }
      int n = 0;
      for (ServerPlayer p : targets) {
         long day = HuntContext.day(p.serverLevel());
         for (int i = 0; i < m.target(); i++) {
            HuntEvent e = HuntExamples.example(m, day);
            if (e == null) {
               continue;
            }
            HuntHooks.fire(p.server, p.getUUID(), p, e, java.util.UUID.randomUUID());
         }
         n++;
      }
      int fn = n;
      c.getSource().sendSuccess(() -> Component.translatable("hunts.frontierhunts.cmd.tested", m.id(), fn), true);
      return n;
   }

   /** What the hunts would read right now about the animal you look at (and about you). */
   private static int context(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      ServerPlayer p = c.getSource().getPlayerOrException();
      ServerLevel level = p.serverLevel();
      Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
      LivingEntity best = null;
      double bd = Double.MAX_VALUE;
      for (Entity en : level.getEntities(p, new AABB(eye, eye.add(look.scale(96.0))).inflate(3.0), x -> x instanceof LivingEntity && HuntContext.species((LivingEntity)x) != null)) {
         var hit = en.getBoundingBox().inflate(0.5).clip(eye, eye.add(look.scale(96.0)));
         if (hit.isPresent() && hit.get().distanceTo(eye) < bd) {
            bd = hit.get().distanceTo(eye);
            best = (LivingEntity)en;
         }
      }
      long now = level.getGameTime();
      StringBuilder b = new StringBuilder("You: ");
      b.append(HuntContext.inStand(p) ? "stand " : "").append(HuntContext.inBlind(p) ? "blind " : "").append(HuntContext.onFoot(p) ? "on-foot " : "riding ")
         .append(HuntContext.snowCamo(p) ? "snow-camo " : "");
      b.append("· month ").append(HuntContext.month(level) + 1).append(" · time ").append(HuntContext.timeOfDay(level));
      if (best != null) {
         LivingEntity a = best;
         b.append(" | ").append(HuntContext.species(a)).append(String.format(Locale.ROOT, " %.0fm: ", bd));
         b.append(HuntContext.called(a, p.getUUID(), now) ? "called " : "").append(HuntContext.glassed(a, p.getUUID(), now) ? "glassed " : "")
            .append(HuntContext.baited(level, a, p.getUUID(), now) ? "bait " : "").append(HuntContext.decoy(level, a, p, now) ? "decoy " : "")
            .append(HuntContext.hound(p, a) ? "hound " : "").append(HuntContext.rut(a) ? "rut " : "").append(HuntContext.snow(level, a) ? "snow " : "")
            .append(HuntContext.water(level, a) ? "water " : "").append("herd ").append(HuntContext.herd(level, a));
      } else {
         b.append(" | no animal in view");
      }
      String s = b.toString();
      c.getSource().sendSuccess(() -> Component.literal(s), false);
      return 1;
   }

   private static int call(CommandContext<CommandSourceStack> c, Lures.Kind kind) throws CommandSyntaxException {
      ServerPlayer p = c.getSource().getPlayerOrException();
      HuntEvents.blow(p, kind);
      c.getSource().sendSuccess(() -> Component.translatable("hunts.frontierhunts.cmd.call", kind.name().toLowerCase(Locale.ROOT)), false);
      return 1;
   }

   private static int bait(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      ServerPlayer p = c.getSource().getPlayerOrException();
      HuntStore.get(p.server).bait(p.getUUID(), p.serverLevel().dimension().location().toString(), p.blockPosition(), p.serverLevel().getGameTime() + 48000L, 4);
      c.getSource().sendSuccess(() -> Component.translatable("hunts.frontierhunts.msg.bait"), false);
      return 1;
   }

   private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      int n = 0;
      for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
         HunterRecord r = HuntBridge.find(p.server, p.getUUID());
         if (r != null) {
            r.counters.keySet().removeIf(k -> k.startsWith("hunt.") || k.startsWith("hunt~"));
            r.done.removeIf(id -> id.startsWith("hunt_"));
            r.counters.put(HuntHooks.MIGRATED, 1); // a reset does not carry old progress over again
            HuntBridge.dirty(p.server, r);
         }
         HuntStore.get(p.server).clearBaits(p.getUUID());
         n++;
      }
      int fn = n;
      c.getSource().sendSuccess(() -> Component.translatable("hunts.frontierhunts.cmd.reset", fn), true);
      return n;
   }

   static List<String> ids() {
      return HuntBook.milestones().stream().map(HuntBook.Milestone::id).toList();
   }
}
