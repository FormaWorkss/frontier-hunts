package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.HuntParticles;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ExpeditionWeapon extends Item {
   public final Weapon weapon;

   public ExpeditionWeapon(Weapon var1) {
      super(new Properties().durability(750));
      this.weapon = var1;
   }

   public static CompoundTag data(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      return var1 == null ? new CompoundTag() : var1.copyTag();
   }

   public static void save(ItemStack var0, CompoundTag var1) {
      var0.set(DataComponents.CUSTOM_DATA, CustomData.of(var1));
   }

   public int capacity(ItemStack var1) {
      return this.weapon.capacity
         + (
            (!data(var1).getBoolean("extended_magazine") || !WeaponAction.supports(this.weapon, "extended_magazine"))
                  && (!data(var1).getBoolean("pistol_magazine") || this.weapon != Weapon.FIELD_PISTOL)
               ? 0
               : this.weapon.magazineBonus()
         );
   }

   public static boolean attachment(ItemStack var0, String var1) {
      return data(var0).getBoolean(var1);
   }

   public UseAnim getUseAnimation(ItemStack var1) {
      return this.weapon == Weapon.BOWFISHING_BOW && data(var1).getBoolean("bow_retrieving") ? UseAnim.NONE : (this.weapon.bow ? UseAnim.BOW : UseAnim.NONE);
   }

   public int getUseDuration(ItemStack var1, LivingEntity var2) {
      return 72000;
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (var3 != InteractionHand.MAIN_HAND) {
         return InteractionResultHolder.pass(var4);
      } else {
         if (var2.isShiftKeyDown() && var2 instanceof ServerPlayer var5 && this.fit(var5, var4)) {
            return InteractionResultHolder.success(var4);
         }

         if (this.weapon == Weapon.BOWFISHING_BOW && var2 instanceof ServerPlayer var7) {
            CompoundTag var6 = data(var4);
            var6.putBoolean("bow_retrieving", HuntProjectile.hasTether(var7));
            save(var4, var6);
         }

         var2.startUsingItem(var3);
         return InteractionResultHolder.consume(var4);
      }
   }

   public void releaseUsing(ItemStack var1, Level var2, LivingEntity var3, int var4) {
      if (this.weapon.bow && var3 instanceof ServerPlayer var5) {
         if (this.weapon == Weapon.BOWFISHING_BOW && data(var1).getBoolean("bow_retrieving")) {
            CompoundTag var7 = data(var1);
            var7.remove("bow_retrieving");
            save(var1, var7);
            return;
         }

         float var6 = Math.min(1.0F, (float)(72000 - var4) / (float)this.weapon.interval);
         if (var6 >= 0.95F) {
            var6 = 1.0F; // [archery2] a tick of network jitter at release is still a full draw (the client sight shows only at 100 %)
         }
         if (var6 > 0.2F) {
            this.shoot(var5, var6, 72000 - var4);
         }
      }
   }

   public void reload(ServerPlayer var1) {
      WeaponReload.start(var1, this);
   }

   public void inventoryTick(ItemStack var1, Level var2, Entity var3, int var4, boolean var5) {
      if (var3 instanceof ServerPlayer var6) {
         WeaponReload.migrate(var6, var1, this);
         WeaponReload.tick(var6, var1, var5, this);
         WeaponMechanics.tick(var6, var1, this, var5);
      }
   }

   private static Item ammo(String var0) {
      return var0.equals("field_arrow") ? (Item)HuntContent.FIELD_ARROW.get() : ExpeditionContent.item(var0);
   }

   public static boolean consume(ServerPlayer var0, String var1) {
      if (!var0.hasInfiniteMaterials() && !var1.isEmpty()) {
         for (ItemStack var3 : var0.getInventory().items) {
            if (var3.is(ammo(var1))) {
               var3.shrink(1);
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   public void shoot(ServerPlayer var1, float var2) {
      this.shoot(var1, var2, 0);
   }

   public void shoot(ServerPlayer var1, float var2, int var3) {
      ItemStack var4 = var1.getMainHandItem();
      if (var4.getItem() == this && var1.isAlive() && !var1.isSpectator() && !var1.isUnderWater() && var1.containerMenu == var1.inventoryMenu) {
         if (this.weapon != Weapon.BOWFISHING_BOW || !HuntProjectile.hasTether(var1)) {
            WeaponReload.interruptTube(var1, var4, this);
            CompoundTag var5 = data(var4);
            long var6 = var1.level().getGameTime();
            if (var5.getLong("next_shot") <= var6 && var5.getLong("reload_until") <= 0L) {
               ArrowSupply.Shot var8 = null;
               if (this.weapon.bow) {
                  if (this.weapon.ammo.equals("field_arrow")) {
                     var8 = ArrowSupply.take(var1);
                     if (var8 == null) {
                        var1.displayClientMessage(Component.literal("No arrows: carry some, or fill a quiver"), true);
                        return;
                     }
                  } else if (!consume(var1, this.weapon.ammo)) {
                     return;
                  }
               } else {
                  int var9 = Math.clamp((long)var5.getInt("rounds"), 0, this.capacity(var4));
                  if (var9 == 0) {
                     var1.level().playSound(null, var1.blockPosition(), EquipmentSounds.action(this.weapon, "dry"), SoundSource.PLAYERS, 0.5F, 1.0F);
                     return;
                  }

                  var5.putInt("rounds", var9 - 1);
               }

               float var15 = 0.0F;
               if (!this.weapon.bow && this.weapon.pellets == 0) {
                  long var10 = var5.getLong("shot_at");
                  var15 = Math.max(0.0F, var5.getFloat("bloom") - (var10 > 0L ? (float)(var6 - var10) * 0.05F : 9.0F));
                  var5.putFloat("bloom", Math.min(1.5F, var15 + bloomStep(this.weapon)));
               }

               var5.putLong("next_shot", var6 + (long)this.weapon.interval);
               var5.putLong("shot_at", var6);
               save(var4, var5);
               if (this.weapon == Weapon.HUNTING_SPEAR && !var1.hasInfiniteMaterials()) {
                  var4.shrink(1);
               }

               float var16 = this.weapon == Weapon.BOWFISHING_BOW
                  ? 0.1F
                  : (this.weapon.pellets > 0 ? 4.0F : (!ExpeditionAim.aiming(var1) && !var1.isUsingItem() ? 1.8F : 0.2F));
               var16 *= 1.0F - 0.08F * (float)ExpeditionService.skill(var1, 0);
               if (attachment(var4, "steady_stock")) {
                  var16 *= 0.7F;
               }

               if (attachment(var4, "angled_foregrip") && !ExpeditionAim.aiming(var1)) {
                  var16 *= 0.8F;
               }

               if (attachment(var4, "bipod") && (var1.isCrouching() || com.formaworks.frontierhunts.prone.Prone.isProne(var1))) { // [rifle] prone deploys the bipod
                  var16 *= 0.45F;
               } else if (!this.weapon.bow && com.formaworks.frontierhunts.prone.Prone.isProne(var1)) {
                  var16 *= 0.65F; // [rifle] prone without a bipod: elbows on the ground
               }
               boolean sticks = !this.weapon.bow && com.formaworks.frontierhunts.sticks.ShootingSticks.rested(var1); // [sticks]
               if (sticks) {
                  var16 *= com.formaworks.frontierhunts.sticks.ShootingSticks.SPREAD; // [sticks] forend in the yoke
               }

               if (this.weapon.bow && this.weapon != Weapon.HUNTING_SPEAR && this.weapon != Weapon.BOWFISHING_BOW) {
                  // [bows] a full, steady draw flies exactly where the sight says; short draw / fatigue tremor / moving add spread
                  var16 = com.formaworks.frontierhunts.archery.BowBallistics.spread(var2, BowHold.strain(var1, var3), var1.getKnownMovement().horizontalDistance(), !var1.onGround())
                     * (1.0F - 0.08F * (float)ExpeditionService.skill(var1, 0));
               }

               if (!this.weapon.bow) {
                  double var11 = sticks ? 0.0 : Math.hypot(var1.getX() - var1.xOld, var1.getZ() - var1.zOld); // [sticks] swivelling round the yoke isn't walking
                  float var13 = this.weapon.pellets > 0 ? 0.25F : 1.0F;
                  var16 += (float)Math.min(1.2, var11 * 4.5) * var13
                     + (var1.onGround() ? 0.0F : 1.1F * var13)
                     + var15 * (attachment(var4, "muzzle_brake") ? 0.7F : 1.0F);
                  if (var1.isCrouching() && var11 < 0.02 && this.weapon.pellets == 0) {
                     var16 *= 0.85F;
                  }
               }

               for (int var18 = 0; var18 < Math.max(1, this.weapon.pellets); var18++) {
                  HuntProjectile var12 = new HuntProjectile((EntityType<? extends HuntProjectile>)ExpeditionContent.PROJECTILE.get(), var1.level());
                  var12.assign(var1, this.weapon, var2, !var1.hasInfiniteMaterials(), var8);
                  // [1.2.5] the recurve shoots where the crosshair is: raised by the holdover for what's under it
                  float launchX = this.weapon == Weapon.RECURVE_BOW
                     ? com.formaworks.frontierhunts.archery.PointOfAim.launchXRot(var1, com.formaworks.frontierhunts.archery.BowBallistics.RECURVE, this.weapon.speed * var2)
                     : var1.getXRot();
                  var12.shootFromRotation(var1, launchX, var1.getYRot(), 0.0F, this.weapon.speed * var2, var16);
                  var1.serverLevel().addFreshEntity(var12);
               }

               boolean var19 = attachment(var4, "suppressor");
               boolean var20 = EquipmentSounds.layered(this.weapon);
               float var21 = 0.97F + var1.getRandom().nextFloat() * 0.06F;
               if (var20 && var19) {
                  var1.level()
                     .playSound(
                        null,
                        var1.getX(),
                        var1.getEyeY(),
                        var1.getZ(),
                        EquipmentSounds.action(this.weapon, "shot_suppressed"),
                        SoundSource.PLAYERS,
                        1.6F,
                        var21
                     );
               } else {
                  var1.level()
                     .playSound(
                        null,
                        var1.getX(),
                        var1.getEyeY(),
                        var1.getZ(),
                        this.weapon.bow ? (SoundEvent)HuntSounds.BOW_RELEASE.get() : EquipmentSounds.action(this.weapon, "shot"),
                        SoundSource.PLAYERS,
                        this.weapon.bow
                           ? 1.0F
                           : (
                              this.weapon == Weapon.BAIT_LAUNCHER
                                 ? 1.0F
                                 : (this.weapon == Weapon.TRANQUILIZER_RIFLE ? 1.4F : (this.weapon == Weapon.FLARE_GUN ? 3.0F : (var19 ? 1.6F : 5.0F)))
                           ),
                        this.weapon.bow ? 1.0F : var21
                     );
               }

               if (var20 && !var19) {
                  var1.level()
                     .playSound(
                        null, var1.getX(), var1.getEyeY(), var1.getZ(), EquipmentSounds.action(this.weapon, "shot_far"), SoundSource.PLAYERS, 16.0F, var21
                     );
               }

               if (!this.weapon.bow && (!this.weapon.automatic() || var6 - var1.getPersistentData().getLong("frontier_smoke_at") >= 6L)) {
                  var1.getPersistentData().putLong("frontier_smoke_at", var6);
                  Vec3 var14 = var1.getEyePosition().add(var1.getLookAngle().scale(1.2));
                  var1.serverLevel()
                     .sendParticles(
                        this.weapon == Weapon.FLARE_GUN ? (SimpleParticleType)HuntParticles.FLARE_SMOKE.get() : ParticleTypes.SMOKE,
                        var14.x,
                        var14.y,
                        var14.z,
                        1,
                        0.012,
                        0.012,
                        0.012,
                        0.006
                     );
               }

               if (!var1.hasInfiniteMaterials() && !var4.isEmpty()) {
                  var4.hurtAndBreak(1, var1, EquipmentSlot.MAINHAND);
               }
            }
         }
      }
   }

   private static float bloomStep(Weapon var0) {
      return switch (var0) {
         case SEMI_AUTO_RIFLE -> 0.35F;
         case FIELD_PISTOL -> 0.5F;
         case REVOLVER -> 0.6F;
         case LEVER_RIFLE -> 0.3F;
         default -> 0.2F;
      };
   }

   public boolean fit(ServerPlayer var1, ItemStack var2) {
      ItemStack var3 = var1.getOffhandItem();
      String var4 = BuiltInRegistries.ITEM.getKey(var3.getItem()).getPath();
      List<String> var5 = EquipmentCatalog.PARTS;
      // [rifle] supports() first and the part item via AttachmentFitting: PARTS now holds non-expedition items (ridgeline_scope)
      if (var5.contains(var4) && WeaponAction.supports(this.weapon, var4) && var3.is(com.formaworks.frontierhunts.workshop.AttachmentFitting.part(var4))) {
         CompoundTag var6 = data(var2);
         if (var6.getBoolean(var4)) {
            return false;
         } else {
            for (String var8 : var5) {
               if (AttachmentSpec.exclusive(var4, var8) && var6.getBoolean(var8)) {
                  return false;
               }
            }

            var6.putBoolean(var4, true);
            save(var2, var6);
            if (!var1.hasInfiniteMaterials()) {
               var3.shrink(1);
            }

            var1.displayClientMessage(Component.literal("Fitted " + var4.replace('_', ' ')), true);
            return true;
         }
      } else {
         return false;
      }
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(
         Component.literal(
            this.weapon.bow
               ? "Hold use to draw; release to shoot"
               : "Fire / Aim bindings · G reload · " + data(var1).getInt("rounds") + " / " + this.capacity(var1)
         )
      );
      var3.add(Component.literal("Ammo: " + this.weapon.ammo.replace('_', ' ')));
      if (!this.weapon.bow) {
         var3.add(Component.literal("Gunsmith's Bench, Fit tab: hold this gun and fit or swap its parts." /* [benches] */));
      }
   }
}
