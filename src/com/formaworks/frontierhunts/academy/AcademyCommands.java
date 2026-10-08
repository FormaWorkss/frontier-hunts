package com.formaworks.frontierhunts.academy;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [academy] {@code /frontierhunts academy ...}: status, begin / leave a course (for yourself), and op tools to bring a
 * hunter home, finish the objectives (testing) and reset course records.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class AcademyCommands {
   private AcademyCommands() {
   }

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("academy")
         .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
         .then(Commands.literal("begin")
            .then(Commands.argument("course", StringArgumentType.word())
               .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Course.values()).map(c -> c.key), b))
               .executes(ctx -> begin(ctx.getSource(), StringArgumentType.getString(ctx, "course")))))
         .then(Commands.literal("leave").executes(ctx -> {
            TrainingService.leave(ctx.getSource().getPlayerOrException());
            return 1;
         }))
         .then(Commands.literal("return").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .executes(ctx -> back(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))))
         .then(Commands.literal("complete").requires(s -> s.hasPermission(2))
            .executes(ctx -> {
               TrainingService.complete(ctx.getSource().getPlayerOrException());
               ctx.getSource().sendSuccess(() -> Component.literal("All objectives of the running course are done."), false);
               return 1;
            }))
         .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
            .then(Commands.argument("targets", EntityArgument.players())
               .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
      e.getDispatcher().register(Commands.literal("frontierhunts").then(root));
   }

   private static int status(CommandSourceStack src, ServerPlayer p) {
      String s = "Ranger Academy · " + TrainingService.status(p) + " · " + TrainingService.sessions() + " hunter(s) training";
      src.sendSuccess(() -> Component.literal(s), false);
      return 1;
   }

   private static int begin(CommandSourceStack src, String key) throws CommandSyntaxException {
      ServerPlayer p = src.getPlayerOrException();
      Course c = Course.byKey(key);
      if (c == null) {
         src.sendFailure(Component.literal("Unknown course " + key + " (glassing, stalk, tracking, dressing, range, archery)"));
         return 0;
      }
      TrainingService.request(p, c);
      return 1;
   }

   private static int back(CommandSourceStack src, Collection<ServerPlayer> targets) {
      int n = 0;
      for (ServerPlayer p : targets) {
         n += TrainingService.forceReturn(p);
      }
      int fn = n;
      src.sendSuccess(() -> Component.literal("Brought " + fn + " hunter(s) home from the training grounds"), true);
      return n;
   }

   private static int reset(CommandSourceStack src, Collection<ServerPlayer> targets) {
      TrainingStore store = TrainingStore.get(src.getServer());
      for (ServerPlayer p : targets) {
         store.reset(p.getUUID());
         TrainingService.sendState(p);
      }
      src.sendSuccess(() -> Component.literal("Reset the academy records of " + targets.size() + " hunter(s)"), true);
      return targets.size();
   }
}
