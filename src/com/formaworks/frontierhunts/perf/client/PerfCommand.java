package com.formaworks.frontierhunts.perf.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * [perf2] Client command for the hitch logger: {@code /frontierperf} (status), {@code /frontierperf on|off|config},
 * {@code /frontierperf flush} (write the current window now) and {@code /frontierperf mark <note>} (write the
 * window now with a note, e.g. "flying over the spruce forest").
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class PerfCommand {
   private PerfCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterClientCommandsEvent event) {
      event.getDispatcher().register(Commands.literal("frontierperf").executes(PerfCommand::status)
         .then(Commands.literal("on").executes(c -> {
            HitchLogger.command(Boolean.TRUE);
            return reply(c, "Frontier hitch logger on (starts within a few frames). Log: " + HitchLogger.file());
         }))
         .then(Commands.literal("off").executes(c -> {
            HitchLogger.command(Boolean.FALSE);
            return reply(c, "Frontier hitch logger off.");
         }))
         .then(Commands.literal("config").executes(c -> {
            HitchLogger.command(null);
            return reply(c, "Frontier hitch logger follows performance.hitchLogger in frontierhunts-client.toml again.");
         }))
         .then(Commands.literal("flush").executes(c -> {
            HitchLogger.flush(null);
            return reply(c, HitchLogger.enabled() ? "Writing the current window to " + HitchLogger.file() : "The hitch logger is off (/frontierperf on).");
         }))
         .then(Commands.literal("benchmark").executes(c -> reply(c, Benchmark.start(60))) // [1.1.5]
            .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 600))
               .executes(c -> reply(c, Benchmark.start(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds"))))))
         .then(Commands.literal("mark").then(Commands.argument("note", StringArgumentType.greedyString()).executes(c -> {
            HitchLogger.flush(StringArgumentType.getString(c, "note"));
            return reply(c, HitchLogger.enabled() ? "Marked." : "The hitch logger is off (/frontierperf on).");
         }))));
   }

   private static int status(CommandContext<CommandSourceStack> c) {
      return reply(c, "Frontier hitch logger " + (HitchLogger.enabled() ? "on" : "off") + ". Log: " + HitchLogger.file()
         + "  (/frontierperf on|off|config|flush|mark <note>|benchmark [seconds])");
   }

   private static int reply(CommandContext<CommandSourceStack> c, String text) {
      c.getSource().sendSuccess(() -> Component.literal(text), false);
      return 1;
   }
}
