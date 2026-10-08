package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.workshop.FieldRodItem;
import com.formaworks.frontierhunts.workshop.FishingGear;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ExpeditionGear extends Item {
   public static Supplier<String> PACK_KEY = () -> "O";
   public final String id;

   public ExpeditionGear(String var1) {
      super(properties(var1));
      this.id = var1;
   }

   private static Properties properties(String var0) {
      Properties var1 = new Properties();
      if (!List.of("bait", "scent_cover", "medkit") /* [sign] mock scrape kit removed */.contains(var0)) {
         var1.stacksTo(1);
      }

      return var1;
   }

   @Override
   public boolean isBarVisible(ItemStack var1) {
      return FieldElectronics.powered(var1) && FieldElectronics.charge(var1) < 1800;
   }

   @Override
   public int getBarWidth(ItemStack var1) {
      return Math.round(13.0F * (float)FieldElectronics.charge(var1) / 1800.0F);
   }

   @Override
   public int getBarColor(ItemStack var1) {
      return FieldElectronics.charge(var1) < 180 ? 13075281 : 9549714;
   }

   @Override
   public int getUseDuration(ItemStack var1, LivingEntity var2) {
      return !this.id.contains("binoculars") && !this.id.equals("rangefinder") && !this.id.equals("deer_call") ? super.getUseDuration(var1, var2) : 72000;
   }

   @Override
   public UseAnim getUseAnimation(ItemStack var1) {
      return !this.id.contains("binoculars") && !this.id.equals("rangefinder") ? super.getUseAnimation(var1) : UseAnim.SPYGLASS;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (!this.id.contains("binoculars") && !this.id.equals("rangefinder")) {
         if (this.id.equals("deer_call")) {
            if (var2.getCooldowns().isOnCooldown(this)) {
               return InteractionResultHolder.fail(var4);
            }

            var2.startUsingItem(var3);
         }

         if (var2 instanceof ServerPlayer var5 && !var5.getCooldowns().isOnCooldown(this)) {
            String var6 = this.id;
            switch (var6) {
               case "expedition_guide":
                  ExpeditionService.send(var5, true);
                  break;
               case "hunter_pack":
                  if (var5.isShiftKeyDown() && FieldGearActions.wearPack(var5, var4)) {
                     return InteractionResultHolder.consume(var4);
                  }

                  FieldGearActions.pack(var5);
                  break;
               case "tree_stand":
                  MountedTreeStand.adjust(var5, var4);
                  break;
               case "field_flashlight":
                  FieldFlashlight.toggle(var5, var4);
                  break;
               case "attachment_tool":
                  FieldGearActions.tool(var5);
                  break;
               case "glow_lure":
                  FieldGearActions.fitLure(var5, var4, this.id);
                  break;
               case "chum_bucket":
                  FishingGear.throwChum(var5, var4);
                  break;
               case "fish_finder":
                  ExpeditionService.message(
                     var5,
                     FieldElectronics.available(var4)
                        ? "Sonar on while held · battery " + Math.round((float)FieldElectronics.charge(var4) * 100.0F / 1800.0F) + "%"
                        : "Battery empty · hold it offhand and use a Field Battery Pack."
                  );
                  break;
               case "thermal_scope":
                  ItemStack var14 = var5.getOffhandItem();
                  if (var14.getItem() instanceof RifleItem && !ExpeditionWeapon.attachment(var14, "thermal_scope")) {
                     CompoundTag var17 = ExpeditionWeapon.data(var14);
                     var17.putBoolean("thermal_scope", true);
                     ExpeditionWeapon.save(var14, var17);
                     this.useOne(var5, var4, 20);
                     ExpeditionService.message(var5, "Thermal optic fitted to offhand Ridgeline");
                  }
                  break;
               case "medkit":
                  if (var5.getHealth() < var5.getMaxHealth()) {
                     var5.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
                     this.useOne(var5, var4, 200);
                  }
                  break;
               case "scent_cover":
                  var5.getPersistentData().putLong("frontier_scent_cover", var1.getGameTime() + 2400L);
                  this.useOne(var5, var4, 20);
                  ExpeditionService.message(var5, "Scent reduced for two minutes");
                  break;
               case "deer_call":
                  for (Whitetail var16 : var1.getEntitiesOfClass(Whitetail.class, var5.getBoundingBox().inflate(64.0), var0 -> !var0.downed())) {
                     var16.respondToCall(var5.position());
                  }

                  var1.playSound(null, var5.blockPosition(), HuntSounds.DEER_CALL.get(), SoundSource.PLAYERS, 2.1F, 1.0F);
                  var5.getCooldowns().addCooldown(this, 16);
                  break;
               case "predator_call":
                  attract(var5, var5.position(), true);
                  var1.playSound(null, var5.blockPosition(), EquipmentSounds.predator(), SoundSource.PLAYERS, 2.3F, 1.0F);
                  var5.getCooldowns().addCooldown(this, 180);
                  break;
               case "grunt_tube":
                  Rut.Call var12 = var5.isShiftKeyDown() ? Rut.Call.SNORT_WHEEZE : Rut.Call.GRUNT;
                  GameCalls.Result var15 = GameCalls.blow(var5, var12);
                  ExpeditionService.message(var5, var15.message(var12, GameCalls.localPhase(var5)));
                  var5.getCooldowns().addCooldown(this, 70);
                  break;
               case "bleat_call":
                  GameCalls.Result var11 = GameCalls.blow(var5, Rut.Call.BLEAT);
                  ExpeditionService.message(var5, var11.message(Rut.Call.BLEAT, GameCalls.localPhase(var5)));
                  var5.getCooldowns().addCooldown(this, 70);
                  break;
               case "rattling_antlers":
                  GameCalls.Result var10 = GameCalls.blow(var5, Rut.Call.RATTLE);
                  ExpeditionService.message(var5, var10.message(Rut.Call.RATTLE, GameCalls.localPhase(var5)));
                  var5.getCooldowns().addCooldown(this, 220);
                  break;
               case "wind_checker":
                  WindCheck.puff(var5);
                  var5.getCooldowns().addCooldown(this, 24);
                  break;
               case "bait":
                  attract(var5, var5.position(), false);
                  this.useOne(var5, var4, 100);
                  break;
               case "fishing_drag_kit":
                  ItemStack var8 = var5.getOffhandItem();
                  if (var8.getItem() instanceof FieldRodItem) {
                     CompoundTag var9 = ExpeditionWeapon.data(var8);
                     if (!var9.getBoolean("drag_kit")) {
                        var9.putBoolean("drag_kit", true);
                        ExpeditionWeapon.save(var8, var9);
                        this.useOne(var5, var4, 20);
                        ExpeditionService.message(var5, "Drag kit fitted to offhand rod");
                     }
                  }
            }
         }

         return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
      } else {
         var2.startUsingItem(var3);
         return InteractionResultHolder.consume(var4);
      }
   }

   private void useOne(ServerPlayer var1, ItemStack var2, int var3) {
      if (!var1.hasInfiniteMaterials()) {
         var2.shrink(1);
      }

      var1.getCooldowns().addCooldown(this, var3);
   }

   public static void attract(ServerPlayer var0, Vec3 var1, boolean var2) {
      for (Whitetail var4 : var0.level().getEntitiesOfClass(Whitetail.class, new AABB(var1, var1).inflate(28.0), var0x -> !var0x.downed())) {
         if (!var2) {
            var4.getNavigation().moveTo(var1.x, var1.y, var1.z, 0.7);
         }
      }

      ExpeditionService.message(var0, "Call placed · stay concealed and watch the wind");
   }

   @Override
   public InteractionResult useOn(UseOnContext var1) {
      // [sign] the mock scrape kit was removed (user request): scrapes are found at trees, not made
      if (this.id.equals("tower_blind")) {
         if (var1.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
         } else if (TowerBlind.place(var1)) {
            this.useOne((ServerPlayer)var1.getPlayer(), var1.getItemInHand(), 20);
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.FAIL;
         }
      } else if (this.id.equals("tree_stand")) {
         if (var1.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
         } else if (MountedTreeStand.place(var1)) {
            this.useOne((ServerPlayer)var1.getPlayer(), var1.getItemInHand(), 20);
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.FAIL;
         }
      } else if (this.id.equals("field_blind")) {
         if (var1.getLevel().isClientSide) {
            HubGroundBlind.predict(var1);
            return InteractionResult.SUCCESS;
         } else if (HubGroundBlind.place(var1)) {
            this.useOne((ServerPlayer)var1.getPlayer(), var1.getItemInHand(), 20);
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.FAIL;
         }
      } else if (this.id.equals("field_spotlight")) {
         if (var1.getPlayer() instanceof ServerPlayer var2) {
            BlockPos var6 = var1.getClickedPos().relative(var1.getClickedFace());
            if (var2.mayUseItemAt(var6, var1.getClickedFace(), var1.getItemInHand()) && var2.level().getBlockState(var6).canBeReplaced()) {
               var2.level()
                  .setBlock(
                     var6,
                     ExpeditionContent.SPOTLIGHT
                        .get()
                        .defaultBlockState()
                        .setValue(DirectionalBlock.FACING, var1.getClickedFace().getAxis().isHorizontal() ? var1.getClickedFace() : var2.getDirection()),
                     3
                  );
               this.useOne(var2, var1.getItemInHand(), 20);
            }
         }

         return InteractionResult.sidedSuccess(var1.getLevel().isClientSide);
      } else {
         return super.useOn(var1);
      }
   }

   @Override
   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      if (this.id.equals("sniper_magazine") || this.id.equals("pistol_magazine")) {
         var3.add(
            Component.literal(this.id.equals("sniper_magazine") ? "Ridgeline: 5 rounds total (standard: 3)" : "Field Pistol: 11 rounds total (standard: 8)")
         );
         var3.add(Component.literal("Gunsmith's Bench, Fit tab: hold the gun, then fit the magazine." /* [benches] */));
      }

      if (this.id.equals("fish_finder")) {
         var3.add(Component.literal("Hold it (either hand): sonar shows fish, range and depth."));
      }

      if (this.id.equals("landing_net")) {
         var3.add(Component.literal("Hold it offhand while playing a fish: land it sooner, further out."));
      }

      if (this.id.equals("chum_bucket")) {
         var3.add(Component.literal("Use toward water: draws fish in and speeds up bites for 90 s."));
      }

      if (this.id.equals("glow_lure")) {
         var3.add(Component.literal("Use with a rod or bowfishing bow offhand: faster night bites."));
      }

      if (this.id.equals("hunter_pack")) {
         var3.add(Component.literal("Sneak-use to wear it on your back (Back slot)."));
         var3.add(Component.literal("Worn or carried: press " + PACK_KEY.get() + " to open it anywhere."));
      }

      if (this.id.equals("bipod")) {
         var3.add(Component.literal("Fits: Ridgeline Bolt Rifle, Lever Rifle, Assault Rifle, Tranquilizer Rifle, pump and semi-auto shotguns"));
         var3.add(Component.literal("Crouch or go prone to deploy: much tighter groups and less kick")); // [rifle]
      }

      if (this.id.equals("steady_stock")) {
         var3.add(Component.literal("Adjustable cheek riser: steadier aim on long guns"));
      }

      if (FieldElectronics.powered(var1)) {
         var3.add(Component.literal("Battery " + Math.round((float)FieldElectronics.charge(var1) * 100.0F / 1800.0F) + "% · Field Battery Pack to replace"));
         if (this.id.equals("field_flashlight")) {
            var3.add(Component.literal("Sneak-use: " + FieldElectronics.modeName(var1) + " beam intensity"));
         }
      }

      if (this.id.equals("tower_blind")) {
         var3.add(Component.literal("Clear 3 x 3 interior, 4 m raised floor. Leave room for the inclined ladder. Use the frame to open or close the door."));
      }

      if (this.id.equals("field_blind")) {
         var3.add(Component.literal("Deploys a full 3 x 3 blind from a 1 x 1 spot of firm ground with 2 blocks of headroom."));
         var3.add(Component.literal("Grass, leaves and trunks inside are hidden, never broken. Use fabric to open or close."));
      }

      if (this.id.equals("tree_stand")) {
         var3.add(Component.literal(MountedTreeStand.configuration(var1)));
         var3.add(Component.literal("Use in air: height · Sneak + use in air: 1 / 2 seats"));
         var3.add(Component.literal("Click the trunk at your chosen height; ladder reaches ground. Use seat to sit; frame to move rail."));
      }

      if (this.id.equals("field_flashlight")) {
         var3.add(Component.literal("Use to switch " + (FieldFlashlight.enabled(var1) ? "off" : "on") + ". Works in either hand; stow to stop the beam."));
      }

      if (this.id.equals("attachment_tool")) {
         var3.add(Component.literal("Use with a new firearm in your offhand to recover fitted parts."));
      }

      if (this.id.equals("glow_lure")) {
         var3.add(Component.literal("Use with a rod or bowfishing bow in the offhand."));
      }

      if (this.id.equals("hunter_pack")) {
         var3.add(Component.literal("Opens your personal 27-slot field pack."));
      }

      if (this.id.equals("thermal_scope")) {
         var3.add(Component.literal("Gunsmith's Bench, Fit tab: hold the gun, then fit the optic." /* [benches] */));
      }

      if (this.id.equals("fishing_drag_kit")) {
         var3.add(Component.literal("Use with a field rod in the offhand to improve drag."));
      }

      if (this.id.equals("grunt_tube")) {
         var3.add(Component.literal("Use: buck grunt · Sneak-use: snort-wheeze challenge"));
         var3.add(Component.literal("Works from the first rubs through breeding. Mature bucks circle downwind on the way in."));
      }

      if (this.id.equals("bleat_call")) {
         var3.add(Component.literal("Doe bleat · the one call that still works outside the rut"));
      }

      if (this.id.equals("rattling_antlers")) {
         var3.add(Component.literal("Two bucks fighting · carries a long way"));
         var3.add(Component.literal("Best while bucks are cruising; wasted once they are paired with does."));
      }

      if (this.id.equals("wind_checker")) {
         var3.add(Component.literal("Use: puff of powder · shows the wind and the slope thermals"));
         var3.add(Component.literal("Hold it to see the scent cone you are putting downwind."));
      }

      if (this.id.equals("scent_cover")) { // [clothing]
         var3.add(Component.translatable("clothing.frontierhunts.tip.spray").withStyle(net.minecraft.ChatFormatting.GRAY));
         var3.add(Component.translatable("clothing.frontierhunts.tip.spray2").withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
      }

   }
}
