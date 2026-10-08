package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * [routines] Op debug commands:
 * <pre>
 * /frontierhunts pressure                 current 64x64 cell: units, behaviour level, decay
 * /frontierhunts pressure add &lt;units&gt;     add pressure here (spills into neighbours like a real shot)
 * /frontierhunts pressure set &lt;units&gt;     set this cell exactly
 * /frontierhunts pressure clear [radius]  clear this cell (and radius cells around)
 * /frontierhunts routine                  nearest deer/elk/moose: herd, anchors, trails, plan, pressure, rut
 * /frontierhunts routine show             draw that herd's anchors and game trails with particles for 20 s
 * /frontierhunts routine reset            nearest animal (and its herd mates) re-survey a new home range here
 * /frontierhunts rut estrus               does within 32 blocks come into estrus for one in-game day
 * /frontierhunts rut fight                the two nearest mature bucks/bulls (same species) square off
 * /frontierhunts rut fight lock           [rutfight] same, but straight to closing in and locking antlers
 * /frontierhunts rut fight info           [rutfight] phase and locked distance of fights within 40 blocks
 * </pre>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class RoutineCommand {
   private static final List<Display> DISPLAYS = new ArrayList<>();

   private record Display(ServerPlayer player, List<BlockPos> trail, BlockPos[] anchors, long until) {
   }

   private RoutineCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("frontierhunts");
      root.then(Commands.literal("pressure").requires(src -> src.hasPermission(2))
            .executes(ctx -> showPressure(ctx.getSource()))
            .then(Commands.literal("add").then(Commands.argument("units", FloatArgumentType.floatArg(0.0F, 200.0F)).executes(ctx -> {
               CommandSourceStack src = ctx.getSource();
               float units = FloatArgumentType.getFloat(ctx, "units");
               PressureStore.of(src.getLevel()).add(src.getLevel().getGameTime(), src.getPosition(), units);
               src.sendSuccess(() -> Component.literal("Added " + units + " pressure units here (a fifth spills into each neighbouring cell)"), true);
               return showPressure(src);
            })))
            .then(Commands.literal("set").then(Commands.argument("units", FloatArgumentType.floatArg(0.0F, 200.0F)).executes(ctx -> {
               CommandSourceStack src = ctx.getSource();
               Vec3 p = src.getPosition();
               PressureStore.of(src.getLevel()).set(src.getLevel().getGameTime(), PressureStore.cell(p.x), PressureStore.cell(p.z), FloatArgumentType.getFloat(ctx, "units"));
               return showPressure(src);
            })))
            .then(Commands.literal("clear")
               .executes(ctx -> clear(ctx.getSource(), 0))
               .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32)).executes(ctx -> clear(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
      root.then(Commands.literal("routine").requires(src -> src.hasPermission(2))
            .executes(ctx -> routine(ctx.getSource(), 0))
            .then(Commands.literal("show").executes(ctx -> routine(ctx.getSource(), 1)))
            .then(Commands.literal("reset").executes(ctx -> routine(ctx.getSource(), 2))));
      root.then(Commands.literal("rut").requires(src -> src.hasPermission(2))
            .then(Commands.literal("estrus").executes(ctx -> estrus(ctx.getSource())))
            .then(Commands.literal("fight").executes(ctx -> fight(ctx.getSource(), false))
               .then(Commands.literal("lock").executes(ctx -> fight(ctx.getSource(), true))) // [rutfight]
               .then(Commands.literal("info").executes(ctx -> fightInfo(ctx.getSource()))))); // [rutfight]
      event.getDispatcher().register(root);
   }

   private static int showPressure(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      Vec3 p = src.getPosition();
      int cx = PressureStore.cell(p.x);
      int cz = PressureStore.cell(p.z);
      PressureStore store = PressureStore.of(level);
      long now = level.getGameTime();
      float units = store.value(now, cx, cz);
      double hl = RoutineConfig.halfLifeDays();
      double quietIn = units <= 1.0F ? 0.0 : hl * Math.log(units) / Math.log(2.0);
      StringBuilder around = new StringBuilder();

      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            around.append(String.format("%5.1f", store.value(now, cx + dx, cz + dz)));
         }

         if (dz < 1) {
            around.append(" |");
         }
      }

      int level01 = Math.round(PressureStore.level01(units) * 100.0F);
      String msg = String.format(
         "Pressure cell [%d, %d] (x %d..%d, z %d..%d): %.2f units · behaviour level %d%% · halves every %.1f days · below 1 unit in %.1f days · %.1f units ever%s",
         cx, cz, cx * 64, cx * 64 + 63, cz * 64, cz * 64 + 63, units, level01, hl, quietIn, store.total(cx, cz),
         RoutineConfig.pressure() ? "" : " · (pressure is DISABLED in the server config)"
      );
      src.sendSuccess(() -> Component.literal(msg), false);
      src.sendSuccess(() -> Component.literal("3x3 cells (north row first): " + around + " · herds relocate at " + RoutineConfig.relocateAt() + " units"), false);
      return Math.round(units);
   }

   private static int clear(CommandSourceStack src, int radius) {
      Vec3 p = src.getPosition();
      int n = PressureStore.of(src.getLevel()).clear(PressureStore.cell(p.x), PressureStore.cell(p.z), radius);
      src.sendSuccess(() -> Component.literal("Cleared hunting pressure in " + n + " cell(s)"), true);
      return n;
   }

   private static Whitetail nearest(CommandSourceStack src, double range) {
      Vec3 p = src.getPosition();
      return src.getLevel()
         .getEntitiesOfClass(Whitetail.class, new net.minecraft.world.phys.AABB(p, p).inflate(range), e -> !e.downed())
         .stream()
         .min(Comparator.comparingDouble(e -> e.distanceToSqr(p)))
         .orElse(null);
   }

   private static int routine(CommandSourceStack src, int mode) {
      Whitetail deer = nearest(src, 96.0);
      if (deer == null) {
         src.sendFailure(Component.literal("No deer, elk or moose within 96 blocks"));
         return 0;
      } else {
         ServerLevel level = src.getLevel();
         DeerRoutine r = deer.routine();
         if (mode == 2) {
            HomeRange old = r.range();
            for (Whitetail e : level.getEntitiesOfClass(Whitetail.class, deer.getBoundingBox().inflate(48.0), x -> x.routine().range() == old)) {
               e.routine().resetRange(level);
            }

            if (old != null) {
               RoutineStore.of(level).remove(old.id);
            }

            src.sendSuccess(() -> Component.literal("The herd forgets its home range and surveys a new one around " + deer.blockPosition().toShortString()), true);
            return 1;
         } else {
            String who = deer.species().title + " " + (deer.traits().buck() ? deer.species().maleName : deer.species().femaleName) + " at "
               + deer.blockPosition().toShortString() + " (" + Math.round(Math.sqrt(deer.distanceToSqr(src.getPosition()))) + "m)";
            src.sendSuccess(() -> Component.literal(who), false);

            for (String line : r.describe(level)) {
               src.sendSuccess(() -> Component.literal("  " + line), false);
            }

            if (mode == 1 && src.getEntity() instanceof ServerPlayer player && r.range() != null) {
               HomeRange range = r.range();
               DISPLAYS.removeIf(d -> d.player == player);
               DISPLAYS.add(new Display(player, r.debugTrailPoints(), range.anchors.clone(), level.getGameTime() + 400L));
               src.sendSuccess(() -> Component.literal("  showing anchors (green bed, yellow feed, blue water) and trails (orange) for 20 s"), false);
            }

            return 1;
         }
      }
   }

   private static int estrus(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      Vec3 p = src.getPosition();
      int n = 0;

      for (Whitetail e : level.getEntitiesOfClass(Whitetail.class, new net.minecraft.world.phys.AABB(p, p).inflate(32.0), x -> !x.downed() && !x.traits().buck())) {
         RutEngine.forceEstrus(e, level.getGameTime() + 24000L);
         n++;
      }

      int count = n;
      src.sendSuccess(() -> Component.literal(count + " doe(s)/cow(s) in estrus for one day - bucks in the seeking or peak rut will trail and chase them (try /season month 11)"), true);
      return n;
   }

   // [rutfight] phase and locked distance of every fight nearby
   private static int fightInfo(CommandSourceStack src) {
      Vec3 p = src.getPosition();
      int n = 0;

      for (Whitetail d : src.getLevel().getEntitiesOfClass(Whitetail.class, new net.minecraft.world.phys.AABB(p, p).inflate(40.0), x -> x.rutPartner() >= 0)) {
         String info = RutEngine.fightInfo(d);
         if (info != null && d.getId() < d.rutPartner()) {
            n++;
            String line = d.species().title + " #" + d.getId() + " vs #" + d.rutPartner() + ": " + info + String.format(" - now %.2f apart, pose %d",
               d.level().getEntity(d.rutPartner()) == null ? -1.0 : Math.sqrt(d.distanceToSqr(d.level().getEntity(d.rutPartner()))), d.rutPose());
            src.sendSuccess(() -> Component.literal(line), false);
         }
      }

      if (n == 0) {
         src.sendFailure(Component.literal("No fights within 40 blocks"));
      }

      return n;
   }

   private static int fight(CommandSourceStack src, boolean direct) {
      ServerLevel level = src.getLevel();
      Vec3 p = src.getPosition();
      List<Whitetail> bucks = new ArrayList<>(level.getEntitiesOfClass(
         Whitetail.class, new net.minecraft.world.phys.AABB(p, p).inflate(40.0), x -> !x.downed() && RutEngine.mature(x) && x.behavior() == 0
      ));
      bucks.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));

      for (int i = 0; i < bucks.size(); i++) {
         for (int j = i + 1; j < bucks.size(); j++) {
            Whitetail a = bucks.get(i);
            Whitetail b = bucks.get(j);
            if (a.species() == b.species() && a.routine().rut.fight == null && b.routine().rut.fight == null) {
               RutEngine.startFight(a, b, false, direct, level, level.getGameTime());
               src.sendSuccess(() -> Component.literal("Two " + a.species().title + " " + a.species().maleName + "s square off (" + Math.round(Math.sqrt(a.distanceToSqr(b))) + "m apart)"), true);
               return 1;
            }
         }
      }

      src.sendFailure(Component.literal("Need two calm mature bucks/bulls of the same species within 40 blocks (spawn with the spawn egg, sex = buck)"));
      return 0;
   }

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post event) {
      if (!DISPLAYS.isEmpty() && event.getServer().getTickCount() % 10 == 0) {
         Iterator<Display> it = DISPLAYS.iterator();

         while (it.hasNext()) {
            Display d = it.next();
            ServerLevel level = d.player.serverLevel();
            if (d.player.isRemoved() || level.getGameTime() > d.until) {
               it.remove();
            } else {
               DustParticleOptions trail = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.1F), 1.4F);

               for (BlockPos t : d.trail) {
                  if (t.distSqr(d.player.blockPosition()) < 16384.0) {
                     level.sendParticles(d.player, trail, true, t.getX() + 0.5, t.getY() + 0.3, t.getZ() + 0.5, 2, 0.15, 0.1, 0.15, 0.0);
                  }
               }

               Vector3f[] colours = {new Vector3f(0.1F, 0.8F, 0.2F), new Vector3f(1.0F, 0.9F, 0.1F), new Vector3f(0.2F, 0.5F, 1.0F)};

               for (int i = 0; i < 3; i++) {
                  BlockPos a = d.anchors[i];
                  if (a != null) {
                     DustParticleOptions dust = new DustParticleOptions(colours[i], 2.0F);

                     for (int y = 0; y < 8; y++) {
                        level.sendParticles(d.player, dust, true, a.getX() + 0.5, a.getY() + 0.3 + y * 0.7, a.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0.0);
                     }
                  }
               }
            }
         }
      }
   }
}
