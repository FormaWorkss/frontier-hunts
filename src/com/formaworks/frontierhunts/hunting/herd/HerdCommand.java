package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3f;

/**
 * [herds] Op commands:
 * <pre>
 * /frontierhunts herd                nearest deer/elk/moose: its group, members, roles, who follows whom
 * /frontierhunts herd show           20 s of particles: a line from every animal to the one it follows, gold over leaders
 * /frontierhunts herd stats [radius] groups around you by species, kind and size
 * /frontierhunts herd startle        the nearest animal bolts from you (its group runs with it)
 * /frontierhunts herd lead &lt;x&gt; &lt;z&gt;   the nearest group's leader walks to x z (its group follows)
 * /frontierhunts herd on|off|config  force social groups on / off for testing, or back to the server config
 * </pre>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class HerdCommand {
   private static final List<Display> DISPLAYS = new ArrayList<>();
   private static final DustParticleOptions LINE = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 0.4F), 0.9F);
   private static final DustParticleOptions LEAD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.2F), 1.4F);
   private static final DustParticleOptions YOUNG = new DustParticleOptions(new Vector3f(0.45F, 0.7F, 1.0F), 0.9F);

   private record Display(ServerPlayer player, long until) {
   }

   private HerdCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("frontierhunts");
      root.then(Commands.literal("herd").requires(src -> src.hasPermission(2))
         .executes(ctx -> info(ctx.getSource()))
         .then(Commands.literal("show").executes(ctx -> show(ctx.getSource())))
         .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource(), 256))
            .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048)).executes(ctx -> stats(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
         .then(Commands.literal("startle").executes(ctx -> startle(ctx.getSource())))
         .then(Commands.literal("lead").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
            .executes(ctx -> lead(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"))))))
         .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), Boolean.TRUE)))
         .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), Boolean.FALSE)))
         .then(Commands.literal("config").executes(ctx -> toggle(ctx.getSource(), null))));
      event.getDispatcher().register(root);
   }

   static Whitetail nearest(CommandSourceStack src, double radius) {
      Vec3 at = src.getPosition();
      return src.getLevel().getEntitiesOfClass(Whitetail.class, new AABB(at, at).inflate(radius), w -> !w.downed())
         .stream().min(Comparator.comparingDouble(w -> w.distanceToSqr(at))).orElse(null);
   }

   private static int info(CommandSourceStack src) {
      Whitetail deer = nearest(src, 48.0);
      if (deer == null) {
         src.sendFailure(Component.literal("No deer, elk or moose within 48 blocks"));
         return 0;
      }

      long dist = Math.round(Math.sqrt(deer.distanceToSqr(src.getPosition())));
      src.sendSuccess(() -> Component.literal(deer.traits().description() + " (" + dist + " m):"), false);
      for (String line : HerdService.describe(deer)) {
         src.sendSuccess(() -> Component.literal(line), false);
      }

      return 1;
   }

   private static int show(CommandSourceStack src) {
      if (src.getEntity() instanceof ServerPlayer p) {
         DISPLAYS.removeIf(d -> d.player == p);
         DISPLAYS.add(new Display(p, src.getLevel().getGameTime() + 400L));
         src.sendSuccess(() -> Component.literal("Showing groups within 64 blocks for 20 s: green line = follows, blue = young following its mother, gold = leader"), false);
         return 1;
      }

      src.sendFailure(Component.literal("Run this as a player"));
      return 0;
   }

   /** Server tick: draw the follow lines for players who asked. */
   static void tickDisplays(MinecraftServer server) {
      if (DISPLAYS.isEmpty()) {
         return;
      }

      DISPLAYS.removeIf(d -> d.player.isRemoved() || d.player.level().getGameTime() > d.until);
      for (Display d : DISPLAYS) {
         ServerLevel level = d.player.serverLevel();
         if (level.getGameTime() % 10L != 0L) {
            continue;
         }

         for (Whitetail w : level.getEntitiesOfClass(Whitetail.class, d.player.getBoundingBox().inflate(64.0), x -> !x.downed())) {
            HerdMember m = w.routine().social;
            Whitetail t = m.followId == null ? null : m.followTarget(level, level.getGameTime());
            Vec3 a = w.position().add(0.0, w.getBbHeight() + 0.4, 0.0);
            if (t == null) {
               if (m.group != null && m.group.size() > 1) {
                  for (int k = 0; k < 4; k++) {
                     level.sendParticles(d.player, LEAD, true, a.x, a.y + k * 0.3, a.z, 1, 0.0, 0.0, 0.0, 0.0);
                  }
               }

               continue;
            }

            Vec3 b = t.position().add(0.0, t.getBbHeight() + 0.4, 0.0);
            int steps = (int)Math.min(40.0, a.distanceTo(b) / 0.8);
            DustParticleOptions dust = m.rank <= 0 ? YOUNG : LINE;
            for (int k = 0; k <= steps; k++) {
               Vec3 p = a.lerp(b, steps == 0 ? 0.0 : (double)k / steps);
               level.sendParticles(d.player, dust, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
         }
      }
   }

   private static int stats(CommandSourceStack src, int radius) {
      ServerLevel level = src.getLevel();
      BlockPos at = BlockPos.containing(src.getPosition());
      Map<GameSpecies, Map<HerdKind, TreeMap<Integer, Integer>>> counts = new EnumMap<>(GameSpecies.class);
      int yards = 0;
      HerdStore store = HerdStore.of(level);
      for (HerdGroup g : store.all()) {
         if (HerdStore.horizontal(g.centre, at) > (double)radius * radius) {
            continue;
         }

         counts.computeIfAbsent(g.species, k -> new EnumMap<>(HerdKind.class)).computeIfAbsent(g.kind, k -> new TreeMap<>()).merge(g.size(), 1, Integer::sum);
         if (g.parent == null && !store.children(g).isEmpty()) {
            yards++;
         }
      }

      int y = yards;
      src.sendSuccess(() -> Component.literal("Groups within " + radius + " blocks (size x count)" + (y > 0 ? " · winter yards: " + y : "")
         + " · season " + String.format(java.util.Locale.ROOT, "%.2f", HerdService.yearPos)), false);
      for (Map.Entry<GameSpecies, Map<HerdKind, TreeMap<Integer, Integer>>> e : counts.entrySet()) {
         for (Map.Entry<HerdKind, TreeMap<Integer, Integer>> k : e.getValue().entrySet()) {
            StringBuilder b = new StringBuilder("  " + e.getKey().id + " " + k.getKey().title(e.getKey()) + ":");
            for (Map.Entry<Integer, Integer> n : k.getValue().entrySet()) {
               b.append(' ').append(n.getKey()).append('x').append(n.getValue());
            }

            String line = b.toString();
            src.sendSuccess(() -> Component.literal(line), false);
         }
      }

      return 1;
   }

   private static int startle(CommandSourceStack src) {
      Whitetail deer = nearest(src, 64.0);
      if (deer == null) {
         src.sendFailure(Component.literal("No deer, elk or moose within 64 blocks"));
         return 0;
      }

      deer.alarm(src.getPosition(), 1.0F, 300);
      src.sendSuccess(() -> Component.literal("Startled a " + deer.traits().description().toLowerCase(java.util.Locale.ROOT) + "; its group should run with it"), false);
      return 1;
   }

   private static int lead(CommandSourceStack src, int x, int z) {
      Whitetail deer = nearest(src, 64.0);
      if (deer == null) {
         src.sendFailure(Component.literal("No deer, elk or moose within 64 blocks"));
         return 0;
      }

      UUID leader = HerdService.leaderOf(deer);
      Whitetail lead = leader != null && src.getLevel().getEntity(leader) instanceof Whitetail w ? w : deer;
      int y = src.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      if (!lead.routine().debugWalkTo(new BlockPos(x, y, z))) {
         src.sendFailure(Component.literal("The leader is not calm right now"));
         return 0;
      }

      src.sendSuccess(() -> Component.literal("The group's leader walks to " + x + " " + z), false);
      return 1;
   }

   private static int toggle(CommandSourceStack src, Boolean on) {
      HerdService.setOverride(on);
      src.sendSuccess(() -> Component.literal("Social groups: " + (on == null ? "server config (" + (HerdService.enabled() ? "on" : "off") + ")" : on ? "forced on" : "forced off")), true);
      return 1;
   }
}
