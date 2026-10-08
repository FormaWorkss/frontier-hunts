package com.formaworks.frontierhunts.sign;

import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [sign] Test commands (op level 2):
 * <pre>
 * /frontierhunts sign rub|scrape     [deersign] the nearest buck (48 blocks) walks to the nearest suitable tree to you and
 *                                    rubs it / paws a scrape under it, right now (season and age aside); the sign only
 *                                    appears as he works it
 * /frontierhunts sign revisit        [deersign] the nearest buck walks to the scrape nearest you and works it again
 * /frontierhunts sign status         [deersign] what the nearest buck is doing about sign, and his season rates
 * /frontierhunts sign age &lt;days&gt;     rubs and scrapes within 24 blocks age by that many days (rain wear)
 * /frontierhunts sign fresh          rubs and scrapes within 24 blocks are freshly worked again
 * </pre>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class SignCommand {
   private SignCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent event) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("frontierhunts");
      root.then(Commands.literal("sign").requires(src -> src.hasPermission(2))
         .then(Commands.literal("rub").executes(ctx -> make(ctx.getSource(), DeerSign.Kind.RUB)))
         .then(Commands.literal("scrape").executes(ctx -> make(ctx.getSource(), DeerSign.Kind.SCRAPE)))
         .then(Commands.literal("revisit").executes(ctx -> revisit(ctx.getSource())))
         .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
         .then(Commands.literal("passage").executes(ctx -> passage(ctx.getSource()))) // [1.1.8] debug: grass sign near you
         .then(Commands.literal("fresh").executes(ctx -> age(ctx.getSource(), -1.0F)))
         .then(Commands.literal("age").then(Commands.argument("days", FloatArgumentType.floatArg(0.0F, 30.0F))
            .executes(ctx -> age(ctx.getSource(), FloatArgumentType.getFloat(ctx, "days"))))));
      // [1.1.8] /frontierhunts legend <whitetail|elk|moose> [typical|nontypical]: spawn a Legend of the Reserve where you look
      com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> legend = Commands.literal("legend").requires(src -> src.hasPermission(2));
      for (String sp : com.formaworks.frontierhunts.freak.FreakQuest.SPECIES) {
         legend.then(Commands.literal(sp).executes(ctx -> legend(ctx.getSource(), sp, null))
            .then(Commands.literal("typical").executes(ctx -> legend(ctx.getSource(), sp, false)))
            .then(Commands.literal("nontypical").executes(ctx -> legend(ctx.getSource(), sp, true))));
      }
      root.then(legend);
      event.getDispatcher().register(root);
   }

   private static int legend(CommandSourceStack src, String sp, Boolean nonTypical) {
      ServerLevel level = src.getLevel();
      net.minecraft.world.phys.Vec3 at = src.getPosition();
      if (src.getEntity() != null) {
         net.minecraft.world.phys.Vec3 eye = src.getEntity().getEyePosition();
         net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(src.getEntity().getLookAngle().scale(24.0)),
            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, src.getEntity()));
         at = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(src.getEntity().getLookAngle().scale(8.0)) : hit.getLocation();
      }
      Whitetail d = com.formaworks.frontierhunts.freak.FreakQuest.spawnLegend(level, at, sp, nonTypical, src.getPlayer());
      if (d == null) {
         src.sendFailure(Component.literal("Could not spawn the legend (the world refused the animal)"));
         return 0;
      }
      String name = com.formaworks.frontierhunts.freak.FreakQuest.legendName(sp);
      src.sendSuccess(() -> Component.literal("Spawned " + name + " (" + d.traits().description() + ")"), true);
      return 1;
   }

   private static Whitetail buck(CommandSourceStack src, boolean whitetail) {
      ServerLevel level = src.getLevel();
      List<Whitetail> bucks = level.getEntitiesOfClass(Whitetail.class, new AABB(BlockPos.containing(src.getPosition())).inflate(48.0),
         w -> w.isAlive() && !w.downed() && w.traits().buck() && (!whitetail || w.species() == com.formaworks.frontierhunts.hunting.GameSpecies.WHITETAIL));
      Whitetail buck = bucks.stream().min(Comparator.comparingDouble(w -> w.distanceToSqr(src.getPosition()))).orElse(null);
      if (buck == null) {
         src.sendFailure(Component.literal("No live " + (whitetail ? "whitetail " : "") + "buck within 48 blocks (spawn one, or try near a herd)"));
      }
      return buck;
   }

   /** [1.1.8] counts the grass sign (pushed-over and pressed-down plants) within 64 blocks */
   private static int passage(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      int pushed = 0, matted = 0;
      for (com.formaworks.frontierhunts.tracking.TrailMark m : com.formaworks.frontierhunts.tracking.TrailStore.get(level)
         .nearby(src.getPosition(), 64.0, 100000, level.getGameTime())) {
         if (m.style() == com.formaworks.frontierhunts.tracking.TrailMark.PASSAGE) {
            pushed++;
         } else if (m.style() == com.formaworks.frontierhunts.tracking.TrailMark.MATTED) {
            matted++;
         }
      }
      int p = pushed, mt = matted;
      int[] st = com.formaworks.frontierhunts.tracking.PassageSign.STATS;
      String err = com.formaworks.frontierhunts.tracking.PassageSign.lastError;
      src.sendSuccess(() -> Component.literal("Grass sign within 64 blocks: " + p + " pushed-over, " + mt + " pressed-down"
         + " (animal checks " + st[0] + ", strides " + st[1] + ", no plant " + st[2] + ", made " + st[3] + ", failed " + st[4] + ", downed " + st[5] + ", downed no plant " + st[6]
         + (err.isEmpty() ? "" : ": " + err) + ")"), false);
      return p + mt;
   }

   private static int status(CommandSourceStack src) {
      Whitetail buck = buck(src, false);
      if (buck == null) {
         return 0;
      }
      src.sendSuccess(() -> Component.literal(buck.traits().description() + ": " + com.formaworks.frontierhunts.sign.work.SignWork.describe(buck)), false);
      return 1;
   }

   private static int revisit(CommandSourceStack src) {
      Whitetail buck = buck(src, true);
      if (buck == null) {
         return 0;
      }
      ServerLevel level = src.getLevel();
      BlockPos scrape = DeerSign.findNearby(level, BlockPos.containing(src.getPosition()), DeerSign.Kind.SCRAPE, 12);
      if (scrape == null) {
         src.sendFailure(Component.literal("No scrape within 12 blocks of you"));
         return 0;
      }
      if (!com.formaworks.frontierhunts.sign.work.SignWork.force(buck, level, com.formaworks.frontierhunts.sign.work.SignAct.REVISIT, null, scrape)) {
         src.sendFailure(Component.literal("The buck has no room to stand at the scrape at " + scrape.toShortString()));
         return 0;
      }
      src.sendSuccess(() -> Component.literal(buck.traits().description() + " is walking to the scrape at " + scrape.toShortString() + " to work it"), true);
      return 1;
   }

   private static int make(CommandSourceStack src, DeerSign.Kind kind) {
      ServerLevel level = src.getLevel();
      Whitetail buck = buck(src, kind == DeerSign.Kind.SCRAPE);
      if (buck == null) {
         return 0;
      }
      List<DeerSign.Site> sites = DeerSign.findSites(level, src.getPosition(), level.getRandom(), kind, 10, 8, 16);
      DeerSign.Site site = sites.isEmpty() ? null : sites.get(0);
      if (site == null) {
         src.sendFailure(Component.literal(kind == DeerSign.Kind.RUB
            ? "No small natural tree within 10 blocks (an upright 1x1 trunk on natural ground, natural leaves on top, an open side)"
            : "No scrape spot within 10 blocks (natural ground 1-2 blocks from a tree, under its canopy, no scrape within 6)"));
         return 0;
      }
      // [deersign] no instant sign: the buck walks over and makes it (the sign appears as he works)
      int act = kind == DeerSign.Kind.RUB ? com.formaworks.frontierhunts.sign.work.SignAct.RUB : com.formaworks.frontierhunts.sign.work.SignAct.SCRAPE;
      DeerSign.Site used = null;
      for (DeerSign.Site s : sites) {
         if (com.formaworks.frontierhunts.sign.work.SignWork.force(buck, level, act, s, null)) {
            used = s;
            break;
         }
      }
      if (used == null) {
         src.sendFailure(Component.literal("No room for the buck to stand at the tree" + (sites.size() > 1 ? "s" : "") + " near you ("
            + com.formaworks.frontierhunts.sign.work.SignWork.lastWhy + ")"));
         return 0;
      }
      DeerSign.Site at = used;
      src.sendSuccess(() -> Component.literal(buck.traits().description() + " is walking to " + (kind == DeerSign.Kind.RUB ? "rub the tree" : "paw a scrape under the tree")
         + " at " + at.tree().toShortString() + " (" + String.format("%.0f", Math.sqrt(buck.distanceToSqr(at.standX(), buck.getY(), at.standZ()))) + " blocks away)"), true);
      return 1;
   }

   private static int age(CommandSourceStack src, float days) {
      ServerLevel level = src.getLevel();
      BlockPos at = BlockPos.containing(src.getPosition());
      int n = 0;
      for (BlockPos p : BlockPos.betweenClosed(at.offset(-24, -8, -24), at.offset(24, 8, 24))) {
         BlockState state = level.getBlockState(p);
         if (state.getBlock() instanceof DeerSign sign && sign.kind != DeerSign.Kind.BED && level.getBlockEntity(p) instanceof DeerSign.Mark mark) {
            if (days < 0.0F) {
               mark.freshened = level.getGameTime();
               mark.wear = 0;
            } else {
               mark.wear = Math.min(2000000, mark.wear + (int)(days * 24000.0F));
            }
            mark.setChanged();
            long age = mark.age(level.getGameTime());
            int stage = Math.clamp((long)((int)(age * 4L / (long)Math.max(1, sign.kind.lifetime))), 0, 3);
            BlockState next = state.setValue(DeerSign.AGE, stage);
            if (next != state) {
               level.setBlock(p, next, 2);
            }
            level.sendBlockUpdated(p, state, next, 2);
            n++;
         }
      }
      int count = n;
      src.sendSuccess(() -> Component.literal(count + " rub(s)/scrape(s) " + (days < 0.0F ? "freshened" : "aged " + days + " day(s)")
         + " (one past its lifetime goes at the next check, within 30 s)"), true);
      return count;
   }
}
