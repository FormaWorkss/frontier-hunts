package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.ecology.bones.BoneSites;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [ecology] Op test commands (permission 2):
 * <pre>
 * /frontierhunts ecology hunt [success|fail|howl]   the nearest predator (64 blocks) hunts the best prey near it now
 * /frontierhunts ecology kill &lt;predator&gt;          the nearest prey animal (32 blocks) is found killed by that predator
 * /frontierhunts ecology status                    running hunts, predator hunger, kills in this area today, kill sites
 * /frontierhunts ecology hunger &lt;0..3&gt;            set the nearest predator's hunger (1 = a meal's time since it ate)
 * /frontierhunts ecology age &lt;hours&gt;              kill sites within 32 blocks become that many in-game hours older
 * /frontierhunts ecology bones &lt;kind&gt; [kill]      lay a bone site here (whitetail elk moose bison pronghorn shed remains)
 * </pre>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class EcologyCommand {
   private EcologyCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> eco = Commands.literal("ecology").requires(src -> src.hasPermission(2));
      eco.then(Commands.literal("hunt")
         .executes(ctx -> hunt(ctx.getSource(), "random"))
         .then(Commands.argument("mode", StringArgumentType.word())
            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"success", "fail", "howl"}, b))
            .executes(ctx -> hunt(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))));
      eco.then(Commands.literal("kill").then(Commands.argument("predator", StringArgumentType.word())
         .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(), b))
         .executes(ctx -> kill(ctx.getSource(), StringArgumentType.getString(ctx, "predator")))));
      eco.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
      eco.then(Commands.literal("hunger").then(Commands.argument("level", FloatArgumentType.floatArg(0.0F, 3.0F))
         .executes(ctx -> hunger(ctx.getSource(), FloatArgumentType.getFloat(ctx, "level")))));
      eco.then(Commands.literal("age").then(Commands.argument("hours", FloatArgumentType.floatArg(0.0F, 240.0F))
         .executes(ctx -> age(ctx.getSource(), FloatArgumentType.getFloat(ctx, "hours")))));
      eco.then(Commands.literal("bones").then(Commands.argument("kind", StringArgumentType.word())
         .suggests((ctx, b) -> SharedSuggestionProvider.suggest(kinds(), b))
         .executes(ctx -> bones(ctx.getSource(), StringArgumentType.getString(ctx, "kind"), false))
         .then(Commands.literal("kill").executes(ctx -> bones(ctx.getSource(), StringArgumentType.getString(ctx, "kind"), true)))));
      event.getDispatcher().register(Commands.literal("frontierhunts").then(eco));
   }

   private static String[] names() {
      Predator[] v = Predator.values();
      String[] out = new String[v.length];
      for (int i = 0; i < v.length; i++) {
         out[i] = v[i].name().toLowerCase(Locale.ROOT);
      }
      return out;
   }

   private static String[] kinds() {
      BoneSites.Kind[] v = BoneSites.Kind.values();
      String[] out = new String[v.length];
      for (int i = 0; i < v.length; i++) {
         out[i] = v[i].name().toLowerCase(Locale.ROOT);
      }
      return out;
   }

   private static WildlifeMob nearestPredator(ServerLevel level, Vec3 at, double r, Predator only) {
      WildlifeMob best = null;
      double bd = Double.MAX_VALUE;
      for (WildlifeMob m : level.getEntitiesOfClass(WildlifeMob.class, new AABB(at, at).inflate(r, 16.0, r),
         m -> m.isAlive() && Predator.of(m.species) != null && (only == null || Predator.of(m.species) == only))) {
         double d = m.position().distanceToSqr(at);
         if (d < bd) {
            bd = d;
            best = m;
         }
      }
      return best;
   }

   private static int hunt(CommandSourceStack src, String mode) {
      ServerLevel level = src.getLevel();
      WildlifeMob mob = nearestPredator(level, src.getPosition(), 64.0, null);
      if (mob == null) {
         src.sendFailure(Component.literal("No predator within 64 blocks (summon one: /summon frontierhunts:wolf ~ ~ ~)"));
         return 0;
      }
      Predator kind = Predator.of(mob.species);
      Hunt old = PredationService.huntOf(mob);
      if (old != null) {
         old.end(false);
      }
      if (kind.style == Predator.Style.SCAVENGE) {
         src.sendFailure(Component.literal(kind.title() + "s only scavenge here: use /frontierhunts ecology kill wolf near prey, then wait or /frontierhunts ecology hunger 1"));
         return 0;
      }
      List<WildlifeMob> pack = new ArrayList<>();
      if (kind.social) {
         for (WildlifeMob o : level.getEntitiesOfClass(WildlifeMob.class, mob.getBoundingBox().inflate(24.0, 6.0, 24.0),
            o -> o != mob && o.species == mob.species && o.isAlive() && PredationService.huntOf(o) == null)) {
            if (pack.size() >= (kind == Predator.WOLF ? 5 : kind == Predator.LION ? 2 : 1)) {
               break;
            }
            pack.add(o);
         }
      }
      LivingEntity prey = PredationService.choose(level, mob, kind, 1 + pack.size(), true);
      if (prey == null) {
         src.sendFailure(Component.literal("No prey for a " + kind.title().toLowerCase(Locale.ROOT) + " within 64 blocks of it"));
         return 0;
      }
      Hunt h = PredationService.start(level, mob, kind, prey, pack, true, mode.equals("success"));
      if (h == null) {
         src.sendFailure(Component.literal("Could not start the hunt"));
         return 0;
      }
      h.forceFail = mode.equals("fail");
      if (mode.equals("howl") && kind == Predator.WOLF) {
         h.forceHowl();
      }
      src.sendSuccess(() -> Component.literal("Hunt: " + h.describe() + " · " + String.format(Locale.ROOT, "%.0f", Math.sqrt(mob.distanceToSqr(prey)))
         + " blocks away"), true);
      return 1;
   }

   private static int kill(CommandSourceStack src, String name) {
      Predator kind;
      try {
         kind = Predator.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         src.sendFailure(Component.literal("Unknown predator: " + name));
         return 0;
      }
      ServerLevel level = src.getLevel();
      Vec3 at = src.getPosition();
      LivingEntity best = null;
      double bd = Double.MAX_VALUE;
      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(32.0, 12.0, 32.0), e -> Prey.of(e) != null)) {
         double d = e.position().distanceToSqr(at);
         if (d < bd) {
            bd = d;
            best = e;
         }
      }
      if (best == null) {
         src.sendFailure(Component.literal("No prey animal within 32 blocks (summon a deer: /summon frontierhunts:whitetail ~3 ~ ~)"));
         return 0;
      }
      Prey info = Prey.of(best);
      int pack = kind == Predator.WOLF ? 3 : kind == Predator.LION ? 2 : 1;
      String label = info.label();
      Vec3 where = best.position();
      net.minecraft.world.entity.Entity body = Hunt.makeKill(level, kind, pack, best, info, null, level.random, true);
      if (body == null && !info.kind().small()) {
         src.sendFailure(Component.literal("That animal cannot be made a kill"));
         return 0;
      }
      src.sendSuccess(() -> Component.literal(kind.title() + " kill laid down: " + label + " at " + BlockPos.containing(where).toShortString()
         + " · use the carcass (empty hand) to read it"), true);
      return 1;
   }

   private static int status(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      Vec3 at = src.getPosition();
      long now = level.getGameTime();
      KillSiteStore store = KillSiteStore.of(level);
      src.sendSuccess(() -> Component.literal("Predation " + (EcologyConfig.predation() ? "on" : "OFF") + " · kills in this area today: "
         + store.killsToday(at, now) + "/" + EcologyConfig.killsPerAreaPerDay() + " · running hunts: " + PredationService.hunts().size()), false);
      for (Hunt h : PredationService.hunts()) {
         WildlifeMob l = h.leader();
         String where = l == null ? "" : " at " + l.blockPosition().toShortString();
         src.sendSuccess(() -> Component.literal("  " + h.describe() + where), false);
      }
      WildlifeMob mob = nearestPredator(level, at, 64.0, null);
      if (mob != null) {
         Predator kind = Predator.of(mob.species);
         float hunger = PredationService.hunger(mob, kind);
         float mood = kind.time.weight(level.getDayTime());
         src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Nearest predator: %s %.0f blocks · hunger %.0f%% (hunts above 55%%) · hunting mood now %.0f%%",
            kind.title(), Math.sqrt(mob.position().distanceToSqr(at)), hunger * 100.0F, mood * 100.0F)), false);
      }
      for (KillSiteStore.Site s : store.near(at, 64.0)) {
         Predator p = null;
         try {
            p = Predator.valueOf(s.predator);
         } catch (IllegalArgumentException ignored) {
         }
         String who = p == null ? "?" : p.title();
         src.sendSuccess(() -> Component.literal("  kill site: " + who + " · " + s.prey + " · " + KillSites.age(now - s.killedAt) + " at "
            + BlockPos.containing(s.position()).toShortString() + (s.busyUntil > now ? " · being fed on" : "")), false);
      }
      return 1;
   }

   private static int hunger(CommandSourceStack src, float h) {
      ServerLevel level = src.getLevel();
      WildlifeMob mob = nearestPredator(level, src.getPosition(), 64.0, null);
      if (mob == null) {
         src.sendFailure(Component.literal("No predator within 64 blocks"));
         return 0;
      }
      Predator kind = Predator.of(mob.species);
      long now = level.getGameTime();
      mob.getPersistentData().putLong(PredationService.FED_TAG, now - (long)(h * kind.mealDays * 24000.0));
      src.sendSuccess(() -> Component.literal(kind.title() + " hunger set to " + Math.round(h * 100.0F) + "%"), true);
      return 1;
   }

   private static int age(CommandSourceStack src, float hours) {
      ServerLevel level = src.getLevel();
      Vec3 at = src.getPosition();
      long ticks = (long)(hours * 1000.0F);
      int n = 0;
      KillSiteStore store = KillSiteStore.of(level);
      AABB box = new AABB(at, at).inflate(32.0, 12.0, 32.0);
      for (KillCarcass c : level.getEntitiesOfClass(KillCarcass.class, box)) {
         c.record(c.record().aged(ticks));
         store.addSite(c.getUUID(), c.position(), c.record().killedAt, c.record().predator, c.record().prey);
         n++;
      }
      for (Whitetail w : level.getEntitiesOfClass(Whitetail.class, box, Whitetail::downed)) {
         KillRecord r = KillSites.record(w);
         if (r != null) {
            KillRecord older = r.aged(ticks);
            KillSites.record(w, older);
            store.addSite(w.getUUID(), w.position(), older.killedAt, older.predator, older.prey);
            n++;
         }
      }
      int count = n;
      src.sendSuccess(() -> Component.literal(count + " carcass(es) aged by " + hours + " in-game hours (they turn to bones after "
         + EcologyConfig.carcassTicks() / 1000L + " hours)"), true);
      return count;
   }

   private static int bones(CommandSourceStack src, String name, boolean kill) {
      BoneSites.Kind kind = BoneSites.Kind.byName(name);
      if (kind == null) {
         src.sendFailure(Component.literal("Unknown bone site kind: " + name));
         return 0;
      }
      ServerLevel level = src.getLevel();
      BlockPos at = BlockPos.containing(src.getPosition());
      int n = BoneSites.place(level, at, kind, !kill, level.random.nextFloat() < 0.7F, level.random, 3);
      if (n == 0) {
         src.sendFailure(Component.literal("Nothing fits here: bones need natural ground (grass, dirt, podzol, sand, snow block, forest floor) with air above and no water"));
         return 0;
      }
      src.sendSuccess(() -> Component.literal("Laid a " + name + " bone site (" + n + " blocks)" + (kill ? ", fresh kill remains" : ", weathered")), true);
      return n;
   }
}
