package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [phone] The Field Phone's battery and torch, on the server. The screen costs a unit a second while it is on (a full
 * battery is an hour of screen time), the torch another while it shines from a hand. It charges by a lit campfire, a camp
 * post or a lodge stove, on a vehicle, and to full over a night's sleep. Charge is written to the stack only in whole
 * percent so the phone does not re-sync every second.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class PhoneBattery {
   /** the phone told us its screen is on until this game tick */
   private static final Map<UUID, Long> SCREEN = new HashMap<>();
   /** units not yet written to the stack (negative: drained) */
   private static final Map<UUID, Integer> PENDING = new HashMap<>();
   private static final int STEP = FieldPhoneItem.CAPACITY / 100;
   private static final int CHARGE_FIRE = 8;
   private static final int CHARGE_VEHICLE = 5;

   private PhoneBattery() {
   }

   /** OP_STATE: bit 1 the screen is on, bit 2 the torch is on. */
   static void state(ServerPlayer player, int flags) {
      long now = player.level().getGameTime();
      if ((flags & 1) != 0) {
         SCREEN.put(player.getUUID(), now + 200L);
      } else {
         SCREEN.remove(player.getUUID());
      }
      ItemStack phone = PhoneServer.phone(player);
      if (!phone.isEmpty()) {
         boolean light = (flags & 2) != 0 && FieldPhoneItem.charge(phone) > 0;
         FieldPhoneItem.setLight(phone, light);
      }
   }

   /** Is the screen of this player's phone on (for the drain)? */
   static boolean screenOn(ServerPlayer player) {
      Long until = SCREEN.get(player.getUUID());
      return until != null && until >= player.level().getGameTime();
   }

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
         return;
      }
      // the torch: the same real light as the Field Flashlight, from a phone held in a hand
      if (player.tickCount % 4 == 0) {
         for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (FieldPhoneItem.is(s) && FieldPhoneItem.light(s) && FieldPhoneItem.charge(s) > 0 && player.level() instanceof ServerLevel level) {
               com.formaworks.frontierhunts.expedition.FieldFlashlight.project(level, player.getEyePosition(), player.getLookAngle());
               break;
            }
         }
      }
      if (player.tickCount % 20 != 0) {
         return;
      }
      ItemStack phone = PhoneServer.phone(player);
      if (phone.isEmpty()) {
         PENDING.remove(player.getUUID());
         return;
      }
      int delta = 0;
      if (!player.hasInfiniteMaterials()) {
         if (screenOn(player)) {
            delta -= 1;
         }
         if (FieldPhoneItem.light(phone) && (player.getMainHandItem() == phone || player.getOffhandItem() == phone)) {
            delta -= 1;
         }
      }
      int charge = FieldPhoneItem.charge(phone);
      if (charge < FieldPhoneItem.CAPACITY) {
         delta += charger(player);
      }
      if (delta == 0) {
         return;
      }
      int pending = PENDING.getOrDefault(player.getUUID(), 0) + delta;
      int next = Math.max(0, Math.min(FieldPhoneItem.CAPACITY, charge + pending));
      if (Math.abs(pending) >= STEP || next == 0 || next == FieldPhoneItem.CAPACITY) {
         FieldPhoneItem.setCharge(phone, next);
         PENDING.remove(player.getUUID());
         if (next == 0 && charge > 0) {
            PhoneServer.toast(player, "Battery empty");
         }
      } else {
         PENDING.put(player.getUUID(), pending);
      }
   }

   /** Charge units a second the player's spot gives (0 when nothing charges the phone here). */
   public static int charger(LivingEntity player) {
      if (player.getVehicle() != null && !(player.getVehicle() instanceof LivingEntity)) {
         return CHARGE_VEHICLE;
      }
      BlockPos at = player.blockPosition();
      var level = player.level();
      for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 2, 4))) {
         if (!level.isLoaded(p)) {
            continue;
         }
         BlockState s = level.getBlockState(p);
         if (s.isAir()) {
            continue;
         }
         if (s.getBlock() instanceof CampfireBlock) {
            if (s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT)) {
               return CHARGE_FIRE;
            }
            continue;
         }
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
         if (id.getNamespace().equals(FrontierHunts.ID) && (id.getPath().equals("camp_post") || id.getPath().equals("lodge_stove"))) {
            return CHARGE_FIRE;
         }
      }
      return 0;
   }

   @SubscribeEvent
   public static void wake(PlayerWakeUpEvent e) {
      if (e.getEntity() instanceof ServerPlayer player && !e.wakeImmediately() && player.level().isDay()) {
         ItemStack phone = PhoneServer.phone(player);
         if (!phone.isEmpty()) {
            FieldPhoneItem.setCharge(phone, FieldPhoneItem.CAPACITY);
            PENDING.remove(player.getUUID());
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      SCREEN.remove(e.getEntity().getUUID());
      PENDING.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      SCREEN.clear();
      PENDING.clear();
   }
}
