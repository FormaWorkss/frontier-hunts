package com.formaworks.frontierhunts.landscape.ride;

import java.lang.reflect.Field;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class GliderServer {
   private static Field floatTicks;
   private static boolean floatTicksMissing;

   private GliderServer() {
   }

   static void open(Player var0, ItemStack var1) {
      Paraglider.setOpen(var1, true);
      var0.resetFallDistance();
      var0.level()
         .playSound(
            null,
            var0.getX(),
            var0.getY() + 1.0,
            var0.getZ(),
            (SoundEvent)RideContent.SND_GLIDER_OPEN.get(),
            SoundSource.PLAYERS,
            0.9F,
            0.95F + var0.getRandom().nextFloat() * 0.1F
         );
   }

   static void fold(Player var0, ItemStack var1, boolean var2) {
      if (Paraglider.isOpen(var1)) {
         Paraglider.setOpen(var1, false);
         var0.resetFallDistance();
         if (!var2) {
            var0.level()
               .playSound(null, var0.getX(), var0.getY() + 1.0, var0.getZ(), (SoundEvent)RideContent.SND_GLIDER_FOLD.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
         }
      }
   }

   static void handle(RidePayloads.Glider var0, IPayloadContext var1) {
      var1.enqueueWork(
         () -> {
            if (var1.player() instanceof ServerPlayer var2) {
               if (Float.isFinite(var0.impact())) {
                  ItemStack var5 = Paraglider.held(var2);
                  if (!Paraglider.isOpen(var5) && Wingsuit.canopyOpen(var2)) {
                     if (var0.action() == 2) {
                        Wingsuit.setCanopy(Wingsuit.worn(var2), false);
                        var2.resetFallDistance();
                        var2.level()
                           .playSound(
                              null, var2.getX(), var2.getY() + 1.0, var2.getZ(), (SoundEvent)RideContent.SND_GLIDER_FOLD.get(), SoundSource.PLAYERS, 1.0F, 0.7F
                           );
                        var2.hurt(var2.damageSources().flyIntoWall(), Math.max(1.0F, Math.min(14.0F, var0.impact() * 6.0F)));
                     }
                  } else {
                     switch (var0.action()) {
                        case 1:
                           fold(var2, var5, false);
                           break;
                        case 2:
                           if (!Paraglider.isOpen(var5)) {
                              return;
                           }

                           fold(var2, var5, true);
                           var2.level()
                              .playSound(
                                 null,
                                 var2.getX(),
                                 var2.getY() + 1.0,
                                 var2.getZ(),
                                 (SoundEvent)RideContent.SND_GLIDER_FOLD.get(),
                                 SoundSource.PLAYERS,
                                 1.0F,
                                 0.7F
                              );
                           float var4 = Math.max(1.0F, Math.min(14.0F, var0.impact() * 6.0F));
                           var2.hurt(var2.damageSources().flyIntoWall(), var4);
                     }
                  }
               }
            }
         }
      );
   }

   static void allowLift(ServerPlayer var0) {
      if (!floatTicksMissing) {
         try {
            if (floatTicks == null) {
               floatTicks = ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
               floatTicks.setAccessible(true);
            }

            floatTicks.setInt(var0.connection, 0);
         } catch (RuntimeException | ReflectiveOperationException var2) {
            floatTicksMissing = true;
            RideContent.LOG.warn("Paraglider: could not relax the server's floating check; lift is capped to gentle sink", var2);
         }
      }
   }

   public static boolean liftAllowed() {
      return !floatTicksMissing;
   }

   @SubscribeEvent
   public static void tick(Pre var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ItemStack var4 = Paraglider.held(var1);
         boolean var3 = Paraglider.isOpen(var4);
         if (var3 && !var1.onGround()) {
            var1.setDeltaMovement(Vec3.ZERO);
         }

         if (var3) {
            if (!var1.onGround()
               && !var1.isInWater()
               && !var1.isInLava()
               && !var1.isPassenger()
               && !var1.isFallFlying()
               && !var1.getAbilities().flying
               && !var1.isDeadOrDying()
               && !var1.isSleeping()) {
               var1.resetFallDistance();
               allowLift(var1);
            } else {
               fold(var1, var4, !var1.onGround());
            }
         }
      }
   }

   @SubscribeEvent
   public static void tidy(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ItemStack var6 = Paraglider.held(var1);
         if (var1.tickCount % 10 == 0) {
            Inventory var3 = var1.getInventory();

            for (int var4 = 0; var4 < var3.getContainerSize(); var4++) {
               ItemStack var5 = var3.getItem(var4);
               if (var5 != var6 && Paraglider.isOpen(var5)) {
                  Paraglider.setOpen(var5, false);
               }
            }
         }
      }
   }
}
