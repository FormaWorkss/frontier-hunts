package com.formaworks.frontierhunts.landscape.ride;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class Horses {
   static final ResourceLocation GAIT = FrontierHunts.id("gait");
   static final ResourceLocation TERRAIN = FrontierHunts.id("terrain");
   static final ResourceLocation PACK = FrontierHunts.id("pack_pace");
   static final ResourceLocation CALL = FrontierHunts.id("whistle_call");
   static final double CANTER = 0.95;
   static final double GALLOP = 1.55;
   static final double BLOWN = 0.55;
   static final double GALLOP_SECONDS = 30.0;
   static final double REST_SECONDS = 22.0;
   static final double WALK_SECONDS = 40.0;
   static final double CANTER_SECONDS = 75.0;
   private static final Map<UUID, Boolean> GALLOP_HELD = new ConcurrentHashMap<>();
   private static final Map<AbstractHorse, Horses.Ride> RIDES = new WeakHashMap<>();
   private static final Map<AbstractHorse, Horses.Call> CALLS = new WeakHashMap<>();
   private static final Map<Mob, AbstractHorse> PACKS = new WeakHashMap<>();

   private Horses() {
   }

   static void gait(RidePayloads.Gait var0, IPayloadContext var1) {
      var1.enqueueWork(() -> GALLOP_HELD.put(var1.player().getUUID(), var0.gallop()));
   }

   static boolean rideable(Entity var0) {
      if (var0 instanceof AbstractHorse var1 && !(var1 instanceof Llama) && !(var1 instanceof Camel)) {
         return true;
      }

      return false;
   }

   @SubscribeEvent
   public static void playerTick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         if (var1.getVehicle() instanceof AbstractHorse var26 && rideable(var26) && var26.getControllingPassenger() == var1) {
            Horses.Ride var27 = RIDES.computeIfAbsent(var26, Horses::load);
            var27.seen = var1.serverLevel().getGameTime();
            if (!var27.placed) {
               var27.lastX = var26.getX();
               var27.lastY = var26.getY();
               var27.lastZ = var26.getZ();
               var27.placed = true;
               Arrays.fill(var27.hx, var26.getX());
               Arrays.fill(var27.hy, var26.getY());
               Arrays.fill(var27.hz, var26.getZ());
            }

            double var4 = var26.getX() - var27.lastX;
            double var6 = var26.getZ() - var27.lastZ;
            double var8 = Math.sqrt(var4 * var4 + var6 * var6);
            var27.lastX = var26.getX();
            var27.lastY = var26.getY();
            var27.lastZ = var26.getZ();
            int var10 = (var27.h + 1) % var27.hx.length;
            double var11 = Math.hypot(var26.getX() - var27.hx[var10], var26.getZ() - var27.hz[var10]);
            double var13 = var26.getY() - var27.hy[var10];
            var27.hx[var27.h] = var26.getX();
            var27.hy[var27.h] = var26.getY();
            var27.hz[var27.h] = var26.getZ();
            var27.h = (var27.h + 1) % var27.hx.length;
            double var15 = var11 > 1.2 ? Math.max(-1.5, Math.min(1.5, var13 / var11)) : 0.0;
            if (--var27.packCheck <= 0) {
               var27.packCheck = 20;
               var27.pack = hasPack(var26);
            }

            boolean var17 = var8 > 0.04;
            boolean var18 = GALLOP_HELD.getOrDefault(var1.getUUID(), false);
            int var19 = var27.blown ? 3 : (var18 && var17 && !var27.pack && !var26.isInWater() ? 2 : (var17 ? 1 : 0));

            double var20 = switch (var19) {
               case 1 -> var15 > 0.25 ? -3.5714285714285714E-4 * var15 * 3.0 : 6.666666666666666E-4 * (var8 < 0.12 ? 1.6 : 1.0);
               case 2 -> -0.0016666666666666668 * (1.0 + Math.max(0.0, var15) * 2.5);
               case 3 -> var8 < 0.03 ? 0.0022727272727272726 : 7.8125E-4;
               default -> var8 < 0.03 ? 0.0022727272727272726 : 0.00125;
            };
            if (var26.isInWater()) {
               var20 = Math.min(var20, -5.555555555555556E-4);
            }

            var27.stamina = (float)Math.max(0.0, Math.min(1.0, (double)var27.stamina + var20));
            if (var27.stamina <= 0.0F && !var27.blown) {
               var27.blown = true;
               var26.playSound(SoundEvents.HORSE_BREATHE, 1.2F, 0.8F);
               var1.displayClientMessage(Component.translatable("message.frontierhunts.horse_blown"), true);
            }

            if (var27.blown && var27.stamina >= 0.4F) {
               var27.blown = false;
            }

            var27.gait = var19;

            double var22 = switch (var19) {
               case 2 -> 1.55;
               case 3 -> 0.55;
               default -> 0.95;
            };
            double var24 = terrain(var26, var15);
            setMul(var26, Attributes.MOVEMENT_SPEED, GAIT, var27.gaitMul, var22);
            var27.gaitMul = q(var22);
            setMul(var26, Attributes.MOVEMENT_SPEED, TERRAIN, var27.terrainMul, var24);
            var27.terrainMul = q(var24);
            if (var27.stamina < 0.3F && (var19 == 2 || var27.blown) && --var27.breath <= 0) {
               var27.breath = 40 + var26.getRandom().nextInt(30);
               var26.playSound(SoundEvents.HORSE_BREATHE, 0.9F, 0.85F + var26.getRandom().nextFloat() * 0.1F);
            }

            if (var1.tickCount % 20 == 0) {
               save(var26, var27);
            }

            if (--var27.sync <= 0) {
               var27.sync = 4;
               if (var1.connection.hasChannel(RidePayloads.HorseState.TYPE)) {
                  PacketDistributor.sendToPlayer(
                     var1, new RidePayloads.HorseState(var26.getId(), var27.stamina, (byte)var19, var27.pack), new CustomPacketPayload[0]
                  );
               }
            }

            if (var26.isTamed() && var1.getUUID().equals(var26.getOwnerUUID())) {
               var1.getPersistentData().putUUID("frontierhunts_horse", var26.getUUID());
            }

            return;
         }
      }
   }

   static double terrain(AbstractHorse var0, double var1) {
      Level var3 = var0.level();
      BlockPos var4 = var0.blockPosition();
      BlockState var5 = var3.getBlockState(var4);
      BlockState var6 = var3.getBlockState(var4.below());
      double var7 = 1.0;
      if (var5.getBlock() instanceof SnowLayerBlock) {
         int var9 = (Integer)var5.getValue(SnowLayerBlock.LAYERS);
         var7 *= var9 >= 5 ? 0.62 : (var9 >= 3 ? 0.78 : 0.92);
      }

      if (var5.is(Blocks.POWDER_SNOW) || var6.is(Blocks.POWDER_SNOW)) {
         var7 *= 0.45;
      } else if (var6.is(Blocks.SNOW_BLOCK)) {
         var7 *= 0.85;
      }

      if (var6.is(Blocks.MUD) || var5.is(Blocks.MUD)) {
         var7 *= 0.68;
      } else if (var6.is(BlockTags.SAND) || var6.is(Blocks.GRAVEL)) {
         var7 *= 0.86;
      }

      if (var0.isInWater()) {
         var7 *= 0.8;
      }

      if (var1 > 0.3) {
         var7 *= 1.0 - Math.min(0.5, (var1 - 0.3) * 1.1);
      } else if (var1 < -0.5) {
         var7 *= 0.85;
      }

      return var7;
   }

   private static double q(double var0) {
      return (double)Math.round(var0 * 20.0) / 20.0;
   }

   private static void setMul(AbstractHorse var0, Holder<Attribute> var1, ResourceLocation var2, double var3, double var5) {
      double var7 = q(var5);
      AttributeInstance var9 = var0.getAttribute(var1);
      if (var9 != null) {
         if (var7 != var3 || !var9.hasModifier(var2)) {
            if (Math.abs(var7 - 1.0) < 1.0E-6) {
               var9.removeModifier(var2);
            } else {
               var9.addOrUpdateTransientModifier(new AttributeModifier(var2, var7 - 1.0, Operation.ADD_MULTIPLIED_TOTAL));
            }
         }
      }
   }

   private static void clear(AbstractHorse var0) {
      AttributeInstance var1 = var0.getAttribute(Attributes.MOVEMENT_SPEED);
      if (var1 != null) {
         var1.removeModifier(GAIT);
         var1.removeModifier(TERRAIN);
      }
   }

   private static Horses.Ride load(AbstractHorse var0) {
      Horses.Ride var1 = new Horses.Ride();
      CompoundTag var2 = var0.getPersistentData();
      if (var2.contains("frontierhunts_wind")) {
         var1.stamina = var2.getFloat("frontierhunts_wind");
         long var3 = var0.level().getGameTime() - var2.getLong("frontierhunts_wind_at");
         if (var3 > 0L) {
            var1.stamina = (float)Math.min(1.0, (double)var1.stamina + (double)var3 / 440.0);
         }
      }

      return var1;
   }

   private static void save(AbstractHorse var0, Horses.Ride var1) {
      CompoundTag var2 = var0.getPersistentData();
      var2.putFloat("frontierhunts_wind", var1.stamina);
      var2.putLong("frontierhunts_wind_at", var0.level().getGameTime());
   }

   private static boolean hasPack(AbstractHorse var0) {
      List var1 = var0.level().getEntitiesOfClass(Mob.class, var0.getBoundingBox().inflate(12.0), var1x -> var1x != var0 && var1x.getLeashHolder() == var0);

      for (Mob var3 : var1) {
         PACKS.put(var3, var0);
      }

      return !var1.isEmpty();
   }

   @SubscribeEvent
   public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post var0) {
      MinecraftServer var1 = var0.getServer();
      long var2 = var1.overworld().getGameTime();
      if (var1.getTickCount() % 10 == 0) {
         Iterator var4 = RIDES.entrySet().iterator();

         while (var4.hasNext()) {
            Entry var5 = (Entry)var4.next();
            AbstractHorse var6 = (AbstractHorse)var5.getKey();
            boolean var7 = var6.isAlive() && var6.getControllingPassenger() instanceof Player;
            if (!var7) {
               save(var6, (Horses.Ride)var5.getValue());
               clear(var6);
               var4.remove();
            }
         }

         var4 = PACKS.entrySet().iterator();

         while (var4.hasNext()) {
            Entry var17 = (Entry)var4.next();
            Mob var19 = (Mob)var17.getKey();
            AbstractHorse var21 = (AbstractHorse)var17.getValue();
            AttributeInstance var8 = var19.getAttribute(Attributes.MOVEMENT_SPEED);
            if (var19.isAlive() && var19.getLeashHolder() == var21) {
               double var9 = var21.getAttributeValue(Attributes.MOVEMENT_SPEED);
               double var11 = var8 == null ? 0.0 : var8.getBaseValue();
               if (var8 != null && var11 > 0.0) {
                  double var13 = Math.max(0.0, Math.min(2.5, var9 * 1.08 / var11 - 1.0));
                  var8.addOrUpdateTransientModifier(new AttributeModifier(PACK, (double)Math.round(var13 * 20.0) / 20.0, Operation.ADD_MULTIPLIED_BASE));
               }
            } else {
               if (var8 != null) {
                  var8.removeModifier(PACK);
               }

               var4.remove();
            }
         }
      }

      if (!CALLS.isEmpty() && var1.getTickCount() % 5 == 0) {
         Iterator var16 = CALLS.entrySet().iterator();

         while (var16.hasNext()) {
            Entry var18 = (Entry)var16.next();
            AbstractHorse var20 = (AbstractHorse)var18.getKey();
            Horses.Call var22 = (Horses.Call)var18.getValue();
            var22.ticks -= 5;
            ServerPlayer var23 = var1.getPlayerList().getPlayer(var22.player);
            boolean var24 = var22.ticks <= 0 || var23 == null || !var20.isAlive() || var20.isVehicle() || var20.isLeashed() || var23.level() != var20.level();
            double var10 = var23 == null ? 0.0 : (double)var20.distanceTo(var23);
            if (!var24 && var10 < 3.5) {
               var24 = true;
               var20.getNavigation().stop();
               var20.getLookControl().setLookAt(var23, 30.0F, 30.0F);
               var20.playSound(SoundEvents.HORSE_BREATHE, 0.8F, 1.0F);
            }

            if (var24) {
               AttributeInstance var12 = var20.getAttribute(Attributes.FOLLOW_RANGE);
               if (var12 != null) {
                  var12.removeModifier(CALL);
               }

               var16.remove();
            } else {
               if (var22.neigh > 0 && (var22.neigh -= 5) <= 0) {
                  var20.playSound(SoundEvents.HORSE_AMBIENT, 1.6F, 1.0F);
               }

               if (var22.ticks % 10 == 0) {
                  double var25 = var10 > 24.0 ? 1.9 : (var10 > 8.0 ? 1.5 : 1.1);
                  var20.getNavigation().moveTo(var23, var25);
               }
            }
         }
      }
   }

   static void whistle(ServerPlayer var0) {
      ServerLevel var1 = var0.serverLevel();
      AbstractHorse var2 = null;
      CompoundTag var3 = var0.getPersistentData();
      if (var3.hasUUID("frontierhunts_horse")
         && var1.getEntity(var3.getUUID("frontierhunts_horse")) instanceof AbstractHorse var5
         && var5.isAlive()
         && var5.distanceTo(var0) < 220.0F) {
         var2 = var5;
      }

      if (var2 == null) {
         double var10 = Double.MAX_VALUE;

         for (AbstractHorse var7 : var1.getEntitiesOfClass(
            AbstractHorse.class, var0.getBoundingBox().inflate(96.0), var1x -> var1x.isTamed() && var0.getUUID().equals(var1x.getOwnerUUID())
         )) {
            double var8 = var7.distanceToSqr(var0);
            if (var8 < var10) {
               var10 = var8;
               var2 = var7;
            }
         }
      }

      if (var2 == null) {
         var0.displayClientMessage(Component.translatable("message.frontierhunts.whistle_none"), true);
      } else if (var2.isVehicle()) {
         var0.displayClientMessage(Component.translatable("message.frontierhunts.whistle_ridden"), true);
      } else if (var2.isLeashed()) {
         var0.displayClientMessage(Component.translatable("message.frontierhunts.whistle_tied"), true);
      } else {
         AttributeInstance var11 = var2.getAttribute(Attributes.FOLLOW_RANGE);
         if (var11 != null) {
            var11.addOrUpdateTransientModifier(new AttributeModifier(CALL, 112.0, Operation.ADD_VALUE));
         }

         CALLS.put(var2, new Horses.Call(var0.getUUID(), 800));
         var2.getNavigation().moveTo(var0, 1.9);
      }
   }

   @SubscribeEvent
   public static void interact(EntityInteract var0) {
      Player var1 = var0.getEntity();
      if (!(var0.getTarget() instanceof AbstractHorse var2) || !rideable(var2) || !var1.isShiftKeyDown()) {
         return;
      }

      if (var0.getHand() == InteractionHand.MAIN_HAND) {
         Level var9 = var1.level();
         List var4 = var9.getEntitiesOfClass(Mob.class, var1.getBoundingBox().inflate(12.0), var2x -> var2x != var2 && var2x.getLeashHolder() == var1);
         if (!var4.isEmpty()) {
            if (!var9.isClientSide) {
               for (Mob var11 : var4) {
                  var11.setLeashedTo(var2, true);
                  PACKS.put(var11, var2);
               }

               var1.displayClientMessage(Component.translatable("message.frontierhunts.pack_tied", new Object[]{var4.size()}), true);
               var2.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
            }

            var0.setCanceled(true);
            var0.setCancellationResult(InteractionResult.sidedSuccess(var9.isClientSide));
         } else if (var1.getMainHandItem().isEmpty()) {
            List var5 = var9.getEntitiesOfClass(Mob.class, var2.getBoundingBox().inflate(12.0), var1x -> var1x != var2 && var1x.getLeashHolder() == var2);
            if (!var5.isEmpty()) {
               if (!var9.isClientSide) {
                  for (Mob var7 : var5) {
                     var7.setLeashedTo(var1, true);
                     PACKS.remove(var7);
                     AttributeInstance var8 = var7.getAttribute(Attributes.MOVEMENT_SPEED);
                     if (var8 != null) {
                        var8.removeModifier(PACK);
                     }
                  }

                  var1.displayClientMessage(Component.translatable("message.frontierhunts.pack_untied", new Object[]{var5.size()}), true);
               }

               var0.setCanceled(true);
               var0.setCancellationResult(InteractionResult.sidedSuccess(var9.isClientSide));
            }
         }
      }
   }

   @SubscribeEvent
   public static void mount(EntityMountEvent var0) {
      if (!var0.getLevel().isClientSide() && var0.getEntityMounting() instanceof ServerPlayer var1) {
         if (var0.isDismounting()) {
            GALLOP_HELD.remove(var1.getUUID());
            if (var0.getEntityBeingMounted() instanceof AbstractHorse var4 && var4.isTamed() && var1.getUUID().equals(var4.getOwnerUUID())) {
               var1.getPersistentData().putUUID("frontierhunts_horse", var4.getUUID());
            }
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      GALLOP_HELD.remove(var0.getEntity().getUUID());
   }

   private static final class Call {
      final UUID player;
      int ticks;
      int neigh = 12;

      Call(UUID var1, int var2) {
         this.player = var1;
         this.ticks = var2;
      }
   }

   private static final class Ride {
      float stamina = 1.0F;
      boolean blown;
      int gait;
      double lastX;
      double lastY;
      double lastZ;
      boolean placed;
      final double[] hx = new double[10];
      final double[] hy = new double[10];
      final double[] hz = new double[10];
      int h;
      double gaitMul = 1.0;
      double terrainMul = 1.0;
      int sync;
      int breath;
      int packCheck;
      boolean pack;
      long seen;
   }
}
