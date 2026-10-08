package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.killcam.KillCamLength;
import com.formaworks.frontierhunts.killcam.KillCamMode;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-side {@code /killcam} command: shows and changes the kill cam settings (same values as the config file /
 * settings screen). {@code /killcam}, {@code /killcam off|lethal|trophy}, {@code /killcam xray on|off},
 * {@code /killcam length short|normal|long}.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class KillCamCommand {
   private KillCamCommand() {
   }

   @SubscribeEvent
   public static void register(RegisterClientCommandsEvent event) {
      var root = Commands.literal("killcam").executes(KillCamCommand::show);
      for (KillCamMode m : KillCamMode.values()) {
         root.then(Commands.literal(m.name().toLowerCase(Locale.ROOT)).executes(c -> {
            HuntConfig.KILLCAM.set(m);
            return saved(c);
         }));
      }
      root.then(Commands.literal("xray").then(Commands.literal("on").executes(c -> {
         HuntConfig.KILLCAM_XRAY.set(true);
         return saved(c);
      })).then(Commands.literal("off").executes(c -> {
         HuntConfig.KILLCAM_XRAY.set(false);
         return saved(c);
      })));
      var length = Commands.literal("length");
      for (KillCamLength l : KillCamLength.values()) {
         length.then(Commands.literal(l.name().toLowerCase(Locale.ROOT)).executes(c -> {
            HuntConfig.KILLCAM_LENGTH.set(l);
            return saved(c);
         }));
      }
      root.then(length);
      event.getDispatcher().register(root);
   }

   private static int saved(CommandContext<CommandSourceStack> c) {
      try {
         HuntConfig.CLIENT.save();
      } catch (RuntimeException ignored) {
         // value is live either way; saving is best effort
      }
      return show(c);
   }

   private static int show(CommandContext<CommandSourceStack> c) {
      Component msg = Component.translatable(
         "commands.frontierhunts.killcam.status",
         Component.translatable("killcam.frontierhunts.mode." + HuntConfig.KILLCAM.get().name().toLowerCase(Locale.ROOT)),
         Component.translatable(HuntConfig.KILLCAM_XRAY.get() ? "options.on" : "options.off"),
         Component.translatable("killcam.frontierhunts.length." + HuntConfig.KILLCAM_LENGTH.get().name().toLowerCase(Locale.ROOT))
      );
      c.getSource().sendSuccess(() -> msg, false);
      if (HuntConfig.REDUCED_MOTION.get()) {
         c.getSource().sendSuccess(() -> Component.translatable("commands.frontierhunts.killcam.reduced_motion"), false);
      }
      return 1;
   }
}
