package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.rifle.RifleActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class FieldElectronics {
   public static final int CAPACITY = 1800;

   public static boolean powered(ItemStack var0) {
      return var0.getItem() instanceof ExpeditionGear var1
            && (
               var1.id.equals("field_flashlight")
                  || var1.id.equals("thermal_binoculars")
                  || var1.id.equals("night_vision_binoculars")
                  || var1.id.equals("fish_finder")
            )
         || ExpeditionWeapon.attachment(var0, "thermal_scope");
   }

   public static int charge(ItemStack var0) {
      CompoundTag var1 = ExpeditionWeapon.data(var0);
      return var1.contains("field_charge") ? Math.clamp((long)var1.getInt("field_charge"), 0, 1800) : 1800;
   }

   public static boolean available(ItemStack var0) {
      return charge(var0) > 0;
   }

   public static int mode(ItemStack var0) {
      CompoundTag var1 = ExpeditionWeapon.data(var0);
      return var1.contains("beam_mode") ? Math.clamp((long)var1.getInt("beam_mode"), 0, 2) : 1;
   }

   public static String modeName(ItemStack var0) {
      return new String[]{"ECO", "FIELD", "HIGH"}[mode(var0)];
   }

   public static float intensity(ItemStack var0) {
      return new float[]{0.62F, 1.0F, 1.28F}[mode(var0)];
   }

   public static void cycle(ServerPlayer var0, ItemStack var1) {
      CompoundTag var2 = ExpeditionWeapon.data(var1);
      var2.putInt("beam_mode", (mode(var1) + 1) % 3);
      ExpeditionWeapon.save(var1, var2);
      var0.getCooldowns().addCooldown(var1.getItem(), 6);
      ExpeditionService.message(var0, "Beam: " + modeName(var1) + " · battery " + Math.round((float)charge(var1) * 100.0F / 1800.0F) + "%");
   }

   public static void drain(ItemStack var0, int var1) {
      if (var1 > 0) {
         CompoundTag var2 = ExpeditionWeapon.data(var0);
         var2.putInt("field_charge", Math.max(0, charge(var0) - var1));
         if (var2.getInt("field_charge") == 0) {
            var2.putBoolean("light_on", false);
         }

         ExpeditionWeapon.save(var0, var2);
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         boolean var12 = var1.isAlive()
            && var1.isUsingItem()
            && var1.getUseItem().getItem() instanceof ExpeditionGear var3
            && var3.id.equals("night_vision_binoculars")
            && available(var1.getUseItem());
         updateVision(var1, var12);
         if (var1.tickCount % 20 == 0 && var1.isAlive() && !var1.hasInfiniteMaterials()) {
            for (InteractionHand var6 : InteractionHand.values()) {
               ItemStack var7 = var1.getItemInHand(var6);
               if (powered(var7)) {
                  int var8 = 0;
                  if (FieldFlashlight.enabled(var7)) {
                     var8 = mode(var7) == 2 ? 2 : (mode(var7) == 0 ? (var1.tickCount % 40 == 0 ? 1 : 0) : 1);
                  } else if (var1.isUsingItem()
                     && var1.getUseItem() == var7
                     && var7.getItem() instanceof ExpeditionGear var9
                     && (var9.id.equals("thermal_binoculars") || var9.id.equals("night_vision_binoculars"))) {
                     var8 = 2;
                  } else {
                     label115: {
                        if (var7.getItem() instanceof ExpeditionGear var10 && var10.id.equals("fish_finder")) {
                           var8 = 1;
                           break label115;
                        }

                        if (var6 == InteractionHand.MAIN_HAND
                           && ExpeditionWeapon.attachment(var7, "thermal_scope")
                           && (ExpeditionAim.aiming(var1) || RifleActions.isAiming(var1))) {
                           var8 = 2;
                        }
                     }
                  }

                  int var15 = charge(var7);
                  if (var8 > 0 && var15 > 0) {
                     drain(var7, var8);
                     if (!available(var7)) {
                        ExpeditionService.message(var1, "Battery empty · hold this equipment offhand and use a Field Battery Pack.");
                     }
                  }
               }
            }
         }
      }
   }

   private static void updateVision(ServerPlayer var0, boolean var1) {
      CompoundTag var2 = var0.getPersistentData();
      long var3 = var2.getLong("frontier_nv_until") - var0.level().getGameTime();
      MobEffectInstance var5 = var0.getEffect(MobEffects.NIGHT_VISION);
      boolean var6 = var3 > 0L
         && var5 != null
         && var5.getAmplifier() == 0
         && var5.isAmbient()
         && !var5.isVisible()
         && Math.abs((long)var5.getDuration() - var3) <= 2L;
      if (!var1) {
         if (var6) {
            var0.removeEffect(MobEffects.NIGHT_VISION);
         }

         var2.remove("frontier_nv_until");
      } else if (var5 != null && !var6) {
         var2.remove("frontier_nv_until");
      } else {
         if (var0.tickCount % 20 == 0 || var5 == null) {
            var0.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 240, 0, true, false, false));
            var2.putLong("frontier_nv_until", var0.level().getGameTime() + 240L);
         }
      }
   }

   private FieldElectronics() {
   }

   public static final class Battery extends Item {
      public Battery() {
         super(new Properties().stacksTo(16));
      }

      public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
         ItemStack var4 = var2.getItemInHand(var3);
         ItemStack var5 = var2.getItemInHand(var3 == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
         if (FieldElectronics.powered(var5) && FieldElectronics.charge(var5) < 1800) {
            if (var2 instanceof ServerPlayer var6) {
               CompoundTag var7 = ExpeditionWeapon.data(var5);
               var7.putInt("field_charge", 1800);
               ExpeditionWeapon.save(var5, var7);
               if (!var6.hasInfiniteMaterials()) {
                  var4.shrink(1);
               }

               var6.getCooldowns().addCooldown(this, 10);
               ExpeditionService.message(var6, "Fresh battery installed · 100%");
            }

            return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
         } else {
            return InteractionResultHolder.pass(var4);
         }
      }
   }
}
