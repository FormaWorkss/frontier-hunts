package com.formaworks.frontierhunts.rifle;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.TreeStandSeat;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class RifleActions {
   private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
   private static final Map<UUID, RifleActions.Aim> AIM = new HashMap<>();

   public static void aim(ServerPlayer var0, boolean var1) {
      if (!var1) {
         AIM.remove(var0.getUUID());
      } else {
         ItemStack var2 = var0.getMainHandItem();
         if (var2.getItem() instanceof RifleItem && canAim(var0, var2)) {
            AIM.put(var0.getUUID(), new RifleActions.Aim(var2, var0.level().dimension().location().toString(), var0.level().getGameTime() + 45L));
         }
      }
   }

   public static boolean stableMount(Player var0) {
      return !var0.isPassenger() || var0.getVehicle() instanceof TreeStandSeat || com.formaworks.frontierhunts.seating.SeatEntity.isSeat(var0.getVehicle()) // [onboard2] seats
         || var0.getVehicle() instanceof com.formaworks.frontierhunts.sticks.ShootingSticksEntity; // [sticks]
   }

   private static boolean canAim(ServerPlayer var0, ItemStack var1) {
      return var0.isAlive()
         && !var0.isSpectator()
         && stableMount(var0)
         && !var0.isUnderWater()
         && var0.containerMenu == var0.inventoryMenu
         && RifleState.read(var1).action() == 0;
   }

   public static boolean isAiming(ServerPlayer var0) {
      RifleActions.Aim var1 = AIM.get(var0.getUUID());
      return var1 != null
         && var1.stack() == var0.getMainHandItem()
         && canAim(var0, var1.stack())
         && var1.dimension().equals(var0.level().dimension().location().toString())
         && var0.level().getGameTime() <= var1.expires();
   }

   public static void migrate(ServerPlayer var0, ItemStack var1) {
      int var2 = RifleState.legacyExcess(var1);
      if (var2 != 0) {
         RifleState.read(var1).idle().write(var1);
         ItemStack var3 = new ItemStack((ItemLike)RifleContent.AMMO.get(), var2);
         if (!var0.getInventory().add(var3)) {
            var0.drop(var3, false);
         }

         sync(var0);
      }
   }

   public static boolean request(ServerPlayer var0, int var1) {
      if (var1 >= 0 && var1 <= 2 && var0.isAlive() && !var0.isSpectator() && var0.containerMenu == var0.inventoryMenu) {
         ItemStack var2 = var0.getMainHandItem();
         if (!(var2.getItem() instanceof RifleItem)) {
            return false;
         } else {
            long var3 = var0.level().getGameTime();
            Long var5 = LAST_REQUEST.get(var0.getUUID());
            if (var5 != null && var3 >= var5 && var3 - var5 < 3L) {
               return false;
            } else {
               LAST_REQUEST.put(var0.getUUID(), var3);
               migrate(var0, var2);
               RifleState var6 = RifleState.read(var2);
               if (var6.action() != 0 || !stableMount(var0) || var0.isUnderWater()) {
                  return false;
               } else if (var1 == 0) {
                  return fire(var0, var2, var6);
               } else if (var1 == 1 && var6.total() < RifleState.capacity(var2)) {
                  if (!var0.hasInfiniteMaterials() && ammoCount(var0) == 0) {
                     message(var0, "no_ammo");
                     return false;
                  } else {
                     begin(var0, var2, var6, 2);
                     return true;
                  }
               } else if (var1 == 1) {
                  return false;
               } else if (!var6.spent() && (var6.chamber() || var6.magazine() <= 0)) {
                  message(var0, "empty");
                  return false;
               } else {
                  begin(var0, var2, var6, 1);
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private static boolean fire(ServerPlayer var0, ItemStack var1, RifleState var2) {
      if (var0.level().getGameTime() - var2.firedAt() < 5L) {
         return false;
      } else {
         if (!var2.chamber() && var2.magazine() > 0) {
            var2 = new RifleState(var2.magazine() - 1, true, false, 0, 0L, "", "", var2.firedAt(), var2.limit());
            var2.write(var1);
         }

         if (!var2.chamber()) {
            sound(var0, (SoundEvent)RifleContent.DRY.get(), 0.35F);
            message(var0, var2.magazine() <= 0 && !var2.spent() ? "empty" : "cycle");
            return false;
         } else {
            ServerLevel var3 = var0.serverLevel();
            Vec3 var4 = var0.getEyePosition();
            Vec3 var5 = var0.getLookAngle();
            if (!Double.isFinite(var5.lengthSqr())) {
               return false;
            } else {
               Vec3 var6 = var4.add(var5.scale(0.6)).add(0.0, -0.06, 0.0);
               BlockHitResult var7 = var3.clip(new ClipContext(var4, var6, Block.COLLIDER, Fluid.NONE, var0));
               if (var7.getType() != Type.MISS) {
                  message(var0, "blocked");
                  return false;
               } else {
                  RifleBullet var8 = new RifleBullet((EntityType<? extends RifleBullet>)RifleContent.BULLET.get(), var3);
                  var8.setOwner(var0);
                  var8.setPos(var6);
                  var8.launch(var6, !var0.hasInfiniteMaterials());
                  boolean var9 = isAiming(var0);
                  // [rifle] lying prone rests the rifle on its bipod (or on the elbows without one)
                  boolean prone = com.formaworks.frontierhunts.prone.Prone.isProne(var0);
                  boolean var10 = (var0.isCrouching() || prone) && ExpeditionWeapon.attachment(var1, "bipod");
                  boolean sticks = com.formaworks.frontierhunts.sticks.ShootingSticks.rested(var0); // [sticks] forend in the yoke
                  float var11 = var9
                     ? (var10 ? 0.008F : (prone ? 0.012F : (sticks ? com.formaworks.frontierhunts.sticks.ShootingSticks.RIDGELINE_AIMED : (var0.isCrouching() ? 0.018F : 0.035F))))
                     : (var10 ? 0.4F : (prone ? 0.5F : (sticks ? com.formaworks.frontierhunts.sticks.ShootingSticks.RIDGELINE_HIP : 0.65F)));
                  var11 += Math.min(0.8F, (float)var0.getDeltaMovement().horizontalDistance() * 1.8F);
                  var8.shootFromRotation(var0, var0.getXRot() - 0.1F, var0.getYRot(), 0.0F, 38.0F, var11);
                  if (!var3.addFreshEntity(var8)) {
                     return false;
                  } else {
                     boolean var12 = var2.magazine() > 0;
                     new RifleState(var2.magazine() - (var12 ? 1 : 0), var12, !var12, 0, 0L, "", "", var3.getGameTime(), var2.limit()).write(var1);
                     if (!var0.hasInfiniteMaterials()) {
                        var1.hurtAndBreak(1, var0, EquipmentSlot.MAINHAND);
                     }

                     sound(var0, (SoundEvent)RifleContent.SHOT.get(), 1.0F);
                     sound(var0, (SoundEvent)RifleContent.SHOT_FAR.get(), 16.0F);
                     RifleNetwork.Shot var13 = new RifleNetwork.Shot(
                        var3.dimension().location().toString(), var0.getId(), var6.x, var6.y, var6.z, var0.getYRot(), var0.getXRot()
                     );

                     for (ServerPlayer var15 : var3.players()) {
                        if (var15.connection != null && var15.connection.hasChannel(RifleNetwork.Shot.TYPE) && var15.distanceToSqr(var0) < 9216.0) {
                           PacketDistributor.sendToPlayer(var15, var13, new CustomPacketPayload[0]);
                        }
                     }

                     for (Whitetail var18 : var3.getEntitiesOfClass(Whitetail.class, var0.getBoundingBox().inflate(96.0))) {
                        var18.alarm(var0.position(), 1.0F, 800);
                     }

                     var0.awardStat(Stats.ITEM_USED.get((Item)RifleContent.RIFLE.get()));
                     sync(var0);
                     return true;
                  }
               }
            }
         }
      }
   }

   private static void begin(ServerPlayer var0, ItemStack var1, RifleState var2, int var3) {
      AIM.remove(var0.getUUID());
      var0.stopUsingItem();
      new RifleState(
            var2.magazine(),
            var2.chamber(),
            var2.spent(),
            var3,
            var0.level().getGameTime(),
            var0.level().dimension().location().toString(),
            var0.getUUID().toString(),
            var2.firedAt(),
            var2.limit()
         )
         .write(var1);
      sound(var0, var3 == 2 ? (SoundEvent)RifleContent.MAG_OUT.get() : (SoundEvent)RifleContent.UNLOCK.get(), 0.55F);
      sync(var0);
   }

   public static void tick(ServerPlayer var0, ItemStack var1, boolean var2) {
      migrate(var0, var1);
      if (!isAiming(var0)) {
         AIM.remove(var0.getUUID());
      }

      RifleState var3 = RifleState.read(var1);
      if (var3.action() != 0) {
         long var4 = var0.level().getGameTime() - var3.started();
         if (var2
            && var1 == var0.getMainHandItem()
            && var0.isAlive()
            && !var0.isSpectator()
            && stableMount(var0)
            && !var0.isUnderWater()
            && var0.containerMenu == var0.inventoryMenu
            && var3.owner().equals(var0.getUUID().toString())
            && var3.dimension().equals(var0.level().dimension().location().toString())
            && var4 >= 0L
            && var4 <= (long)(var3.duration() + 2)) {
            if (var3.action() == 1 && var4 == 8L) {
               sound(var0, (SoundEvent)RifleContent.PULL.get(), 0.5F);
            }

            if (var3.action() == 1 && var4 == 14L) {
               sound(var0, (SoundEvent)RifleContent.PUSH.get(), 0.45F);
            }

            if (var3.action() == 1 && var4 == 22L) {
               sound(var0, (SoundEvent)RifleContent.LOCK.get(), 0.6F);
            }

            if (var3.action() == 2 && var4 == 40L) {
               sound(var0, (SoundEvent)RifleContent.MAG_IN.get(), 0.55F);
            }

            if (var4 >= (long)var3.duration()) {
               if (var3.action() == 2) {
                  int var6 = Math.min(RifleState.capacity(var1) - var3.total(), var0.hasInfiniteMaterials() ? RifleState.capacity(var1) : ammoCount(var0));
                  if (var6 > 0 && !var0.hasInfiniteMaterials()) {
                     consume(var0, var6);
                  }

                  int var7 = var3.total() + var6;
                  new RifleState(Math.max(0, var7 - 1), var7 > 0, false, 0, 0L, "", "", var3.firedAt(), var3.limit()).write(var1);
               } else {
                  if (var3.chamber() && !var0.hasInfiniteMaterials()) {
                     ItemStack var8 = new ItemStack((ItemLike)RifleContent.AMMO.get());
                     if (!var0.getInventory().add(var8)) {
                        var0.drop(var8, false);
                     }
                  }

                  boolean var9 = var3.magazine() > 0;
                  new RifleState(var3.magazine() - (var9 ? 1 : 0), var9, false, 0, 0L, "", "", var3.firedAt(), var3.limit()).write(var1);
               }

               sync(var0);
            }
         } else {
            var3.idle().write(var1);
            sync(var0);
         }
      }
   }

   public static int ammoCount(ServerPlayer var0) {
      int var1 = 0;

      for (ItemStack var3 : var0.getInventory().items) {
         if (var3.is((Item)RifleContent.AMMO.get())) {
            var1 += var3.getCount();
         }
      }

      return var1;
   }

   private static void consume(ServerPlayer var0, int var1) {
      for (ItemStack var3 : var0.getInventory().items) {
         if (var3.is((Item)RifleContent.AMMO.get())) {
            int var4 = Math.min(var1, var3.getCount());
            var3.shrink(var4);
            var1 -= var4;
            if (var1 == 0) {
               break;
            }
         }
      }

      if (var1 != 0) {
         throw new IllegalStateException("Rifle ammunition transaction changed on server thread");
      }
   }

   private static void sync(ServerPlayer var0) {
      var0.getInventory().setChanged();
      var0.inventoryMenu.broadcastChanges();
   }

   private static void sound(ServerPlayer var0, SoundEvent var1, float var2) {
      var0.level().playSound(null, var0.getX(), var0.getEyeY(), var0.getZ(), var1, SoundSource.PLAYERS, var2, 0.97F + var0.getRandom().nextFloat() * 0.06F);
   }

   private static void message(ServerPlayer var0, String var1) {
      var0.displayClientMessage(Component.translatable("rifle.frontierhunts." + var1), true);
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      AIM.remove(var0.getEntity().getUUID());
      LAST_REQUEST.remove(var0.getEntity().getUUID());

      for (ItemStack var2 : var0.getEntity().getInventory().items) {
         if (var2.getItem() instanceof RifleItem) {
            if (var0.getEntity() instanceof ServerPlayer var3) {
               migrate(var3, var2);
            }

            RifleState var5 = RifleState.read(var2);
            if (var5.action() != 0) {
               var5.idle().write(var2);
            }
         }
      }
   }

   @SubscribeEvent
   public static void block(LeftClickBlock var0) {
      if (var0.getEntity().getMainHandItem().getItem() instanceof RifleItem) {
         var0.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent var0) {
      LAST_REQUEST.clear();
      AIM.clear();
   }

   private RifleActions() {
   }

   private static record Aim(ItemStack stack, String dimension, long expires) {
   }
}
