package com.formaworks.frontierhunts.landscape.ride;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
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
public final class WingsuitServer {
   private WingsuitServer() {
   }

   static void handle(RidePayloads.Suit var0, IPayloadContext var1) {
      var1.enqueueWork(
         () -> {
            if (var1.player() instanceof ServerPlayer var2) {
               ItemStack var6 = Wingsuit.worn(var2);
               if (!var6.isEmpty()) {
                  switch (var0.action()) {
                     case 0:
                        if (Wingsuit.canopyOpen(var2) || var2.onGround() || var2.isInWater() || var2.isPassenger()) {
                           var2.inventoryMenu.sendAllDataToRemote();
                           return;
                        }

                        Wingsuit.setCanopy(var6, true);
                        var2.stopFallFlying();
                        var2.resetFallDistance();
                        var2.level()
                           .playSound(
                              null,
                              var2.getX(),
                              var2.getY() + 1.5,
                              var2.getZ(),
                              (SoundEvent)RideContent.SND_CANOPY_OPEN.get(),
                              SoundSource.PLAYERS,
                              1.0F,
                              0.95F + var2.getRandom().nextFloat() * 0.1F
                           );
                        break;
                     case 1:
                     case 2:
                        if (!Float.isFinite(var0.value())) {
                           return;
                        }

                        float var4 = Mth.clamp(var0.value(), 0.0F, 6.0F);
                        float var5 = (var4 - 0.45F) * 11.0F;
                        if (var0.action() == 2) {
                           var5 *= 0.5F;
                        }

                        if (var5 <= 0.0F) {
                           return;
                        }

                        var2.hurt(var2.damageSources().flyIntoWall(), Math.min(60.0F, var5));
                        var2.level()
                           .playSound(
                              null,
                              var2.getX(),
                              var2.getY(),
                              var2.getZ(),
                              var5 > 6.0F ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL,
                              SoundSource.PLAYERS,
                              1.0F,
                              0.9F
                           );
                        break;
                     case 3:
                        if (!Wingsuit.canopyOpen(var2) || var2.onGround() || var2.isInWater() || var2.isPassenger()) {
                           var2.inventoryMenu.sendAllDataToRemote();
                           return;
                        }

                        Wingsuit.setCanopy(var6, false);
                        var2.resetFallDistance();
                        var2.level()
                           .playSound(
                              null, var2.getX(), var2.getY() + 1.5, var2.getZ(), (SoundEvent)RideContent.SND_GLIDER_FOLD.get(), SoundSource.PLAYERS, 0.9F, 1.3F
                           );
                  }
               }
            }
         }
      );
   }

   @SubscribeEvent
   public static void tick(Pre var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ItemStack var4 = Wingsuit.worn(var1);
         if (!var4.isEmpty() && (Wingsuit.canopyOpen(var1) || var1.isFallFlying()) && !var1.onGround()) {
            var1.setDeltaMovement(Vec3.ZERO);
         }

         if (!var4.isEmpty()) {
            if (Wingsuit.canopyOpen(var1)) {
               boolean var3 = var1.onGround();
               if (!var3
                  && !var1.isInWater()
                  && !var1.isInLava()
                  && !var1.isPassenger()
                  && !var1.getAbilities().flying
                  && !var1.isDeadOrDying()
                  && !var1.isSleeping()) {
                  var1.resetFallDistance();
                  GliderServer.allowLift(var1);
               } else {
                  Wingsuit.setCanopy(var4, false);
                  var1.resetFallDistance();
                  var1.level()
                     .playSound(
                        null,
                        var1.getX(),
                        var1.getY() + 1.0,
                        var1.getZ(),
                        (SoundEvent)RideContent.SND_GLIDER_FOLD.get(),
                        SoundSource.PLAYERS,
                        var3 ? 0.8F : 0.5F,
                        1.1F
                     );
               }
            } else if (var1.isFallFlying()) {
               var1.resetFallDistance();
            }
         }
      }
   }

   @SubscribeEvent
   public static void tidy(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         ItemStack var6 = Wingsuit.worn(var1);
         if (var1.tickCount % 10 == 0) {
            Inventory var3 = var1.getInventory();

            for (int var4 = 0; var4 < var3.getContainerSize(); var4++) {
               ItemStack var5 = var3.getItem(var4);
               if (var5 != var6 && var5.getItem() instanceof Wingsuit && Boolean.TRUE.equals(var5.get((DataComponentType)RideContent.WING_OPEN.get()))) {
                  Wingsuit.setCanopy(var5, false);
               }
            }
         }
      }
   }
}
