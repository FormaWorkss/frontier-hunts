package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class FishingGear {
   private static final Map<ServerLevel, List<FishingGear.Chum>> CHUM = new WeakHashMap<>();

   public static boolean hasNet(Player var0) {
      Item var1 = ExpeditionContent.item("landing_net");
      return var0.getOffhandItem().is(var1) || var0.getMainHandItem().is(var1);
   }

   public static boolean chummed(ServerLevel var0, Vec3 var1, double var2) {
      List var4 = CHUM.get(var0);
      if (var4 == null) {
         return false;
      } else {
         long var5 = var0.getGameTime();
         var4.removeIf(var2x -> var2x.until < var5);

         for (FishingGear.Chum var8 : var4) {
            if (var8.at.distanceToSqr(var1) < var2 * var2) {
               return true;
            }
         }

         return false;
      }
   }

   public static void throwChum(ServerPlayer var0, ItemStack var1) {
      ServerLevel var2 = var0.serverLevel();
      Vec3 var3 = var0.getEyePosition();
      Vec3 var4 = var3.add(var0.getLookAngle().scale(14.0));
      BlockHitResult var5 = var2.clip(new ClipContext(var3, var4, Block.COLLIDER, Fluid.ANY, var0));
      if (var5.getType() != Type.MISS && var2.getFluidState(var5.getBlockPos()).is(FluidTags.WATER)) {
         Vec3 var6 = var5.getLocation();
         CHUM.computeIfAbsent(var2, var0x -> new ArrayList<>()).add(new FishingGear.Chum(var6, var2.getGameTime() + 1800L));
         var2.sendParticles(ParticleTypes.SPLASH, var6.x, var6.y + 0.1, var6.z, 30, 0.6, 0.05, 0.6, 0.2);
         var2.sendParticles(ParticleTypes.BUBBLE, var6.x, var6.y - 0.4, var6.z, 24, 0.7, 0.3, 0.7, 0.02);
         var2.playSound(null, var6.x, var6.y, var6.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.7F, 0.8F);
         int var7 = var2.getEntitiesOfClass(AbstractFish.class, new AABB(var6, var6).inflate(14.0)).size();
         int var8 = Math.max(0, 4 - var7);
         Holder var9 = var2.getBiome(BlockPos.containing(var6));
         float var10 = ((Biome)var9.value()).getBaseTemperature();

         for (int var11 = 0; var11 < var8; var11++) {
            BlockPos var12 = BlockPos.containing(
               var6.x + (var2.random.nextDouble() - 0.5) * 8.0, var6.y - 1.0 - (double)var2.random.nextInt(2), var6.z + (var2.random.nextDouble() - 0.5) * 8.0
            );
            if (var2.getFluidState(var12).is(FluidTags.WATER)) {
               EntityType var13 = var10 < 0.3F
                  ? EntityType.SALMON
                  : (
                     var10 > 0.9F && var2.random.nextFloat() < 0.5F
                        ? EntityType.TROPICAL_FISH
                        : (var2.random.nextFloat() < 0.35F ? EntityType.SALMON : EntityType.COD)
                  );
               AbstractFish var14 = (AbstractFish)var13.spawn(var2, var12, MobSpawnType.EVENT);
            }
         }

         for (AbstractFish var17 : var2.getEntitiesOfClass(AbstractFish.class, new AABB(var6, var6).inflate(16.0))) {
            var17.getNavigation().moveTo(var6.x, var6.y - 1.0, var6.z, 1.0);
         }

         var0.getCooldowns().addCooldown(var1.getItem(), 40);
         if (!var0.hasInfiniteMaterials()) {
            var1.shrink(1);
            ItemStack var16 = new ItemStack(Items.BUCKET);
            if (!var0.getInventory().add(var16)) {
               var0.drop(var16, false);
            }
         }

         ExpeditionService.message(var0, "Chum in the water: fish gather here for about a minute and a half.");
      } else {
         ExpeditionService.message(var0, "Aim at open water within 14 blocks to throw chum.");
      }
   }

   private static double mean(EntityType<?> var0) {
      if (var0 == EntityType.SALMON) {
         return 3.8;
      } else if (var0 == EntityType.TROPICAL_FISH) {
         return 0.22;
      } else {
         return var0 == EntityType.PUFFERFISH ? 0.55 : 2.3;
      }
   }

   private static String species(LivingEntity var0) {
      EntityType var1 = var0.getType();
      if (var1 == EntityType.SALMON) {
         return "Salmon";
      } else if (var1 == EntityType.TROPICAL_FISH) {
         return "Tropical Fish";
      } else if (var1 == EntityType.PUFFERFISH) {
         return "Pufferfish";
      } else {
         return var1 == EntityType.COD ? "Cod" : var0.getType().getDescription().getString();
      }
   }

   public static ItemStack weigh(ServerPlayer var0, LivingEntity var1, ItemStack var2, boolean var3) {
      RandomSource var4 = var0.getRandom();
      double var5 = mean(var1.getType());
      double var7 = var5 * Math.exp(var4.nextGaussian() * 0.32 + (var3 ? 0.03 : 0.0));
      var7 = (double)Math.round(Math.min(var5 * 4.5, Math.max(var5 * 0.3, var7)) * 100.0) / 100.0;
      boolean var9 = var7 >= var5 * 1.75;
      String var10 = species(var1);
      ArrayList var11 = new ArrayList();
      var11.add(Component.literal(String.format(Locale.ROOT, "%.2f kg · %s", var7, var0.getScoreboardName())).withStyle(ChatFormatting.GRAY));
      if (var9) {
         var11.add(Component.literal("Trophy catch").withStyle(ChatFormatting.GOLD));
      }

      if (var9 || var7 >= var5 * 1.3) {
         var2.set(DataComponents.LORE, new ItemLore(var11));
         if (var9) {
            var2.set(
               DataComponents.CUSTOM_NAME, Component.literal("Trophy " + var10).withStyle(ChatFormatting.GOLD).withStyle(var0x -> var0x.withItalic(false))
            );
         }
      }

      CompoundTag var12 = var0.getPersistentData();
      String var13 = "frontier_fish_best_" + var10.toLowerCase(Locale.ROOT).replace(' ', '_');
      double var14 = var12.getDouble(var13);
      String var16 = String.format(Locale.ROOT, "%s landed · %.2f kg", var10, var7);
      if (var7 > var14) {
         var12.putDouble(var13, var7);
         if (var14 > 0.0) {
            var16 = var16 + " · new personal best!";
         }
      }

      if (var9) {
         var16 = "Trophy " + var16;
         var0.level().playSound(null, var0.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.45F, 1.4F);
      }

      var0.displayClientMessage(Component.literal(var16).withStyle(var9 ? ChatFormatting.GOLD : ChatFormatting.WHITE), true);
      return var2;
   }

   private FishingGear() {
   }

   private static record Chum(Vec3 at, long until) {
   }
}
