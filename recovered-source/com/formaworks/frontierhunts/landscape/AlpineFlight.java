package com.formaworks.frontierhunts.landscape;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineFlight {
   private static final float VANILLA = 0.05F;
   private static final float MIN = 1.0F;
   private static final float MAX = 5.0F;
   private static final Map<UUID, Float> ENABLED = new ConcurrentHashMap<>();

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent var0) {
      var0.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("frontierfly")
                  .executes(var0x -> toggle(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), null)))
               .then(
                  Commands.literal("speed")
                     .then(
                        Commands.argument("multiplier", FloatArgumentType.floatArg(1.0F, 5.0F))
                           .executes(
                              var0x -> toggle(((CommandSourceStack)var0x.getSource()).getPlayerOrException(), FloatArgumentType.getFloat(var0x, "multiplier"))
                           )
                     )
               )
         );
   }

   private static int toggle(ServerPlayer var0, Float var1) {
      UUID var2 = var0.getUUID();
      if (var1 != null) {
         ENABLED.put(var2, var1);
         say(var0, "Creative flight set to " + String.format("%.1f", var1) + "x.", ChatFormatting.GREEN);
      } else if (ENABLED.remove(var2) != null) {
         restore(var0);
         say(var0, "Creative flight back to normal speed.", ChatFormatting.YELLOW);
      } else {
         ENABLED.put(var2, 3.0F);
         say(var0, "Creative flight is now 3x. Run /frontierfly again to turn it off.", ChatFormatting.GREEN);
      }

      apply(var0);
      return 1;
   }

   private static void say(ServerPlayer var0, String var1, ChatFormatting var2) {
      var0.sendSystemMessage(Component.literal(var1).withStyle(var2));
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         apply(var1);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ENABLED.remove(var1.getUUID());
         restore(var1);
      }
   }

   private static void apply(ServerPlayer var0) {
      Float var1 = ENABLED.get(var0.getUUID());
      boolean var2 = var0.isCreative() || var0.isSpectator();
      float var3 = var1 != null && var2 ? 0.05F * Math.clamp(var1, 1.0F, 5.0F) : 0.05F;
      Abilities var4 = var0.getAbilities();
      if (!(Math.abs(var4.getFlyingSpeed() - var3) < 1.0E-6F)) {
         var4.setFlyingSpeed(var3);
         var0.onUpdateAbilities();
      }
   }

   private static void restore(ServerPlayer var0) {
      Abilities var1 = var0.getAbilities();
      if (!(Math.abs(var1.getFlyingSpeed() - 0.05F) < 1.0E-6F)) {
         var1.setFlyingSpeed(0.05F);
         var0.onUpdateAbilities();
      }
   }
}
