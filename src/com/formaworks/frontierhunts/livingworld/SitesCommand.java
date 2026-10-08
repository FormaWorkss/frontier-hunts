package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.livingworld.plan.Executor;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kinds;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [livingworld] /frontierhunts sites: list the site kinds, locate the nearest of a kind (or of every kind), place one at a
 * position for testing, and show/redo the guaranteed station near world spawn.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class SitesCommand {
   private static final SuggestionProvider<CommandSourceStack> KINDS = (c, b) -> SharedSuggestionProvider.suggest(Kinds.GENERATED, b); // [villages]
   private static final SuggestionProvider<CommandSourceStack> VARIANTS = (c, b) -> {
      Kind k = Kinds.get(StringArgumentType.getString(c, "kind"));
      return k == null ? b.buildFuture() : SharedSuggestionProvider.suggest(java.util.Arrays.asList(k.variants), b);
   };

   private SitesCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      LiteralArgumentBuilder<CommandSourceStack> sites = Commands.literal("sites").requires(s -> s.hasPermission(2))
         .executes(c -> list(c.getSource()))
         .then(Commands.literal("list").executes(c -> list(c.getSource())))
         .then(Commands.literal("locate")
            .then(Commands.literal("all").executes(c -> locateAll(c.getSource())))
            .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS).executes(c -> locate(c.getSource(), StringArgumentType.getString(c, "kind")))))
         .then(Commands.literal("place")
            .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
               .executes(c -> place(c, null, -1))
               .then(Commands.argument("variant", StringArgumentType.word()).suggests(VARIANTS)
                  .executes(c -> place(c, StringArgumentType.getString(c, "variant"), -1))
                  .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                     .executes(c -> place(c, StringArgumentType.getString(c, "variant"), IntegerArgumentType.getInteger(c, "rotation")))))))
         .then(Commands.literal("spawn")
            .executes(c -> spawnInfo(c.getSource()))
            .then(Commands.literal("redo").executes(c -> SpawnSite.redo(c.getSource()))));
      e.getDispatcher().register(Commands.literal("frontierhunts").then(sites));
   }

   private static void say(CommandSourceStack s, Component c) {
      s.sendSuccess(() -> c, false);
   }

   private static int list(CommandSourceStack s) {
      say(s, Component.literal("Frontier Hunts living-world sites (structure id frontierhunts:<kind>):").withStyle(ChatFormatting.GOLD));
      for (Kind k : Kinds.ALL.values()) {
         if (!Kinds.GENERATED.contains(k.id)) continue; // [villages] retired building kinds are not listed
         MutableComponent line = Component.literal(" " + k.id).withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("  " + String.join(", ", k.variants)).withStyle(ChatFormatting.GRAY));
         line.withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/frontierhunts sites locate " + k.id))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click: locate the nearest " + k.id))));
         say(s, line);
      }
      say(s, Component.literal("/frontierhunts sites locate <kind|all> · place <kind> [variant] [rotation] · spawn").withStyle(ChatFormatting.DARK_GRAY));
      return Kinds.GENERATED.size();
   }

   private static Holder<Structure> holder(ServerLevel level, String kind) {
      return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ResourceKey.create(Registries.STRUCTURE, LivingWorld.id(kind))).orElse(null);
   }

   private static int locate(CommandSourceStack s, String kind) {
      ServerLevel level = s.getLevel();
      Holder<Structure> h = holder(level, kind);
      if (h == null) {
         s.sendFailure(Component.literal("Unknown site kind " + kind));
         return 0;
      }
      BlockPos from = BlockPos.containing(s.getPosition());
      Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(h), from, 64, false);
      if (found == null) {
         s.sendFailure(Component.literal("No " + kind + " found nearby (not in these biomes / too far)."));
         return 0;
      }
      BlockPos p = found.getFirst();
      int dist = (int)Math.round(Math.sqrt(from.distSqr(new BlockPos(p.getX(), from.getY(), p.getZ()))));
      MutableComponent pos = Component.literal("[" + p.getX() + ", ~, " + p.getZ() + "]").withStyle(st -> st.withColor(ChatFormatting.GREEN)
         .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + p.getX() + " ~ " + p.getZ()))
         .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport"))));
      say(s, Component.literal(kind + ": ").withStyle(ChatFormatting.YELLOW).append(pos).append(Component.literal(" (" + dist + " blocks away)")));
      return dist;
   }

   private static int locateAll(CommandSourceStack s) {
      int n = 0;
      for (String k : Kinds.GENERATED) { // [villages]
         if (locate(s, k) > 0) {
            n++;
         }
      }
      return n;
   }

   private static int place(CommandContext<CommandSourceStack> c, String variantName, int rotation) {
      CommandSourceStack s = c.getSource();
      String kindId = StringArgumentType.getString(c, "kind");
      Kind k = Kinds.get(kindId);
      if (k == null) {
         s.sendFailure(Component.literal("Unknown site kind " + kindId));
         return 0;
      }
      int variant = -1;
      if (variantName != null) {
         variant = k.variant(variantName);
         if (variant < 0) {
            s.sendFailure(Component.literal("Unknown variant " + variantName + " (" + String.join(", ", k.variants) + ")"));
            return 0;
         }
      }
      ServerLevel level = s.getLevel();
      BlockPos at = BlockPos.containing(s.getPosition());
      Plan p = null;
      long seed = level.getGameTime() * 31L + at.asLong();
      Rnd r = new Rnd(seed);
      int ox = at.getX();
      int oz = at.getZ();
      // spiral outward up to ~64 blocks for ground that suits the site
      for (int attempt = 0; attempt < 28 && p == null; attempt++) {
         double ang = attempt * 2.399963;
         double rad = attempt == 0 ? 0 : 6 + attempt * 2.1;
         ox = at.getX() + (int)Math.round(Math.cos(ang) * rad);
         oz = at.getZ() + (int)Math.round(Math.sin(ang) * rad);
         McTerrain t = new McTerrain(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
         int v = variant >= 0 ? variant : k.pickVariant(r);
         p = LivingSite.plan(kindId, t, seed + attempt, ox, oz, rotation >= 0 ? rotation : r.nextInt(4), v);
      }
      if (p == null) {
         s.sendFailure(Component.literal("No ground within 64 blocks suits a " + kindId + " (too steep or wet; duck blinds need open water, glassing points a lookout). Try another spot."));
         return 0;
      }
      int written = placeNow(level, p);
      final Plan fp = p;
      say(s, Component.literal("Placed " + kindId + " (" + fp.title + ") at " + fp.cx + " " + fp.cy + " " + fp.cz + ": " + written + " blocks").withStyle(ChatFormatting.GREEN));
      return written;
   }

   /** executes a plan into an existing (loaded or generated-on-demand) area of a live level */
   static int placeNow(ServerLevel level, Plan p) {
      for (int cx = (p.minX - 2) >> 4; cx <= (p.maxX + 2) >> 4; cx++) {
         for (int cz = (p.minZ - 2) >> 4; cz <= (p.maxZ + 2) >> 4; cz++) {
            level.getChunk(cx, cz);
         }
      }
      BoundingBox box = new BoundingBox(p.minX - 2, level.getMinBuildHeight(), p.minZ - 2, p.maxX + 2, level.getMaxBuildHeight(), p.maxZ + 2);
      return Executor.run(p, new McWorld(level, box), p.minX - 2, p.minZ - 2, p.maxX + 2, p.maxZ + 2);
   }

   private static int spawnInfo(CommandSourceStack s) {
      SpawnSite.Data d = SpawnSite.Data.get(s.getServer().overworld());
      if (d.pos == null) {
         say(s, Component.literal("No hunting camp was placed near spawn in this world (" + d.note + ")."));
         return 0;
      }
      BlockPos p = d.pos;
      say(s, Component.literal("Spawn hunting camp: ").append(Component.literal("[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]")
         .withStyle(st -> st.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + p.getX() + " " + p.getY() + " " + p.getZ())))));
      return 1;
   }
}
