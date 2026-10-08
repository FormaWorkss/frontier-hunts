package com.formaworks.frontierhunts.landscape.ride.rig;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * [atvfuel] The ATV's rear-rack rig: an optional strap-on cargo box (27 slots) and an optional pair of jerry-can
 * cradles (2 slots). Storage lives in the ATV's existing container (slots 0-26 box, 27-28 cans) so it saves, loads and
 * drops through the same server-authoritative path as before; what is fitted is {@link #BOX}/{@link #CARRIER} in
 * {@link Atv#DATA_RIG}, which also carries the derived visual state (cans present, lid open) and the reserve litres
 * for every client.
 */
public final class AtvRig {
   public static final int BOX = 1;
   public static final int CARRIER = 2;
   public static final int CAN0 = 4;
   public static final int CAN1 = 8;
   /** Server runs with requireFuel=false. */
   public static final int FREE = 16;
   /** Someone has the cargo box open (lid animation). */
   public static final int OPEN = 32;
   private static final int FITTED = BOX | CARRIER;

   public static final int BOX_SLOTS = 27;
   public static final int CAN_SLOT = 27;
   public static final int SLOTS = 29;
   /** Litres per pour while the use key is held on the ATV (one pour every 4 ticks = 2.5 L/s). */
   static final float POUR_STEP = 0.5F;

   private AtvRig() {
   }

   // ------------------------------------------------------------------------------------------------ state

   public static int flags(Atv atv) {
      Integer f = atv.getEntityData().get(Atv.DATA_RIG);
      return f == null ? 0 : f;
   }

   public static boolean hasBox(Atv atv) {
      return (flags(atv) & BOX) != 0;
   }

   public static boolean hasCarrier(Atv atv) {
      return (flags(atv) & CARRIER) != 0;
   }

   /** Reserve litres in fitted storage as last synced (client display). */
   public static float reserveShown(int flags) {
      return (flags >>> 8 & 0xFFFF) / 10.0F;
   }

   private static void setFitted(Atv atv, int fitted) {
      atv.getEntityData().set(Atv.DATA_RIG, flags(atv) & ~FITTED | fitted & FITTED);
   }

   /** Server: litres in jerry cans the rig can reach (carrier cradles first, then the box). */
   public static float reserve(Atv atv) {
      NonNullList<ItemStack> items = atv.getItemStacks();
      float total = 0.0F;
      int f = flags(atv);
      for (int i = 0; i < items.size(); i++) {
         if (reachable(f, i) && items.get(i).getItem() instanceof JerryCanItem) {
            total += JerryCanItem.litres(items.get(i));
         }
      }
      return total;
   }

   private static boolean reachable(int flags, int slot) {
      return slot < BOX_SLOTS ? (flags & BOX) != 0 : slot < SLOTS && (flags & CARRIER) != 0;
   }

   /** Server: recompute the derived bits and push them if anything changed. */
   public static void sync(Atv atv) {
      if (atv.level().isClientSide) {
         return;
      }
      NonNullList<ItemStack> items = atv.getItemStacks();
      int f = flags(atv) & FITTED;
      if ((f & CARRIER) != 0) {
         if (items.size() > CAN_SLOT && !items.get(CAN_SLOT).isEmpty()) {
            f |= CAN0;
         }
         if (items.size() > CAN_SLOT + 1 && !items.get(CAN_SLOT + 1).isEmpty()) {
            f |= CAN1;
         }
      }
      if (!AtvFuelConfig.requireFuel()) {
         f |= FREE;
      }
      if ((f & BOX) != 0 && viewed(atv)) {
         f |= OPEN;
      }
      f |= Math.min(0xFFFF, Math.round(reserve(atv) * 10.0F)) << 8;
      if (f != flags(atv)) {
         atv.getEntityData().set(Atv.DATA_RIG, f);
      }
   }

   private static boolean viewed(Atv atv) {
      if (atv.level() instanceof ServerLevel sl) {
         for (ServerPlayer p : sl.players()) {
            if (p.containerMenu instanceof RigMenu m && m.atv == atv) {
               return true;
            }
         }
      }
      return false;
   }

   static void closeViewers(Atv atv) {
      if (atv.level() instanceof ServerLevel sl) {
         for (ServerPlayer p : sl.players()) {
            if (p.containerMenu instanceof RigMenu m && m.atv == atv) {
               p.closeContainer();
            }
         }
      }
   }

   static void serverTick(Atv atv) {
      if (atv.tickCount % 5 == 0) {
         sync(atv);
      }
   }

   // ------------------------------------------------------------------------------------------------ interaction

   /**
    * Hook at the top of {@link Atv#interact}. Returns null to fall through to the ATV's own behaviour (mounting).
    * Runs on both sides; only the server changes state.
    */
   public static InteractionResult interact(Atv atv, Player p, InteractionHand hand) {
      ItemStack held = p.getItemInHand(hand);
      boolean client = atv.level().isClientSide;
      if (held.getItem() instanceof CargoBoxItem && !hasBox(atv)) {
         if (!client) {
            attachBox(atv, p, held);
         }
         return InteractionResult.sidedSuccess(client);
      }
      if (held.getItem() instanceof CanCarrierItem && !hasCarrier(atv)) {
         if (!client) {
            attachCarrier(atv, p, held);
         }
         return InteractionResult.sidedSuccess(client);
      }
      if (p.isSecondaryUseActive()) {
         if (!client) {
            open(atv, p);
         }
         return InteractionResult.sidedSuccess(client);
      }
      if (held.getItem() instanceof JerryCanItem) {
         return pourFromHand(atv, p, held);
      }
      return null;
   }

   /** The cargo / rack menu. Also reached from the seat with the inventory key (HasCustomInventoryScreen). */
   public static void open(Atv atv, Player p) {
      if (!(p instanceof ServerPlayer sp)) {
         return;
      }
      if ((flags(atv) & FITTED) == 0) {
         p.displayClientMessage(Component.translatable("message.frontierhunts.atv_no_rig").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      Component title = Component.translatable(hasBox(atv) ? "container.frontierhunts.atv_cargo" : "container.frontierhunts.atv_rack");
      int f = flags(atv);
      sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new RigMenu(id, inv, atv), title), buf -> {
         buf.writeVarInt(atv.getId());
         buf.writeVarInt(f);
      });
      atv.gameEvent(GameEvent.CONTAINER_OPEN, p);
      sync(atv);
   }

   private static InteractionResult pourFromHand(Atv atv, Player p, ItemStack can) {
      boolean client = atv.level().isClientSide;
      float inCan = JerryCanItem.litres(can);
      double level = client ? AtvFuel.fuel(atv) : AtvFuel.exact(atv);
      double room = AtvFuel.TANK - level;
      if (inCan <= 0.0F) {
         if (!client) {
            p.displayClientMessage(Component.translatable("message.frontierhunts.jerry_can_empty").withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (room < 0.01) {
         if (!client) {
            p.displayClientMessage(Component.translatable("message.frontierhunts.atv_tank_full", String.format("%.1f", AtvFuel.TANK))
               .withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (client) {
         return InteractionResult.SUCCESS;
      }
      float amount = (float)Math.min(Math.min(POUR_STEP, inCan), room);
      AtvFuel.set(atv, level + amount);
      if (!p.hasInfiniteMaterials()) {
         JerryCanItem.setLitres(can, inCan - amount);
      }
      Vec3 neck = AtvFuel.fillerNeck(atv);
      atv.level().playSound(null, neck.x, neck.y, neck.z, RigContent.SND_POUR.get(), SoundSource.PLAYERS, 0.8F, 0.92F + atv.getRandom().nextFloat() * 0.16F);
      if (atv.level() instanceof ServerLevel sl) {
         sl.sendParticles(ParticleTypes.SPLASH, neck.x, neck.y, neck.z, 3, 0.04, 0.02, 0.04, 0.0);
      }
      p.displayClientMessage(Component.translatable("message.frontierhunts.atv_fuel_level",
         String.format("%.1f", AtvFuel.exact(atv)), String.format("%.0f", AtvFuel.TANK)).withStyle(ChatFormatting.GOLD), true);
      return InteractionResult.SUCCESS;
   }

   // ------------------------------------------------------------------------------------------------ fitting

   private static void attachBox(Atv atv, Player p, ItemStack held) {
      NonNullList<ItemStack> items = atv.getItemStacks();
      for (int i = 0; i < BOX_SLOTS; i++) {
         // defensive: nothing should live in the box slots without a box
         if (!items.get(i).isEmpty()) {
            atv.spawnAtLocation(items.get(i), 1.0F);
            items.set(i, ItemStack.EMPTY);
         }
      }
      NonNullList<ItemStack> from = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
      held.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(from);
      for (int i = 0; i < BOX_SLOTS; i++) {
         items.set(i, from.get(i));
      }
      setFitted(atv, flags(atv) | BOX);
      if (!p.hasInfiniteMaterials()) {
         held.shrink(1);
      }
      fitted(atv, p, "message.frontierhunts.atv_box_fitted");
   }

   private static void attachCarrier(Atv atv, Player p, ItemStack held) {
      NonNullList<ItemStack> items = atv.getItemStacks();
      for (int i = CAN_SLOT; i < SLOTS; i++) {
         if (!items.get(i).isEmpty()) {
            atv.spawnAtLocation(items.get(i), 1.0F);
            items.set(i, ItemStack.EMPTY);
         }
      }
      setFitted(atv, flags(atv) | CARRIER);
      if (!p.hasInfiniteMaterials()) {
         held.shrink(1);
      }
      fitted(atv, p, "message.frontierhunts.atv_carrier_fitted");
   }

   private static void fitted(Atv atv, Player p, String msg) {
      atv.level().playSound(null, atv.getX(), atv.getY() + 0.8, atv.getZ(), RigContent.SND_ATTACH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
      atv.gameEvent(GameEvent.EQUIP, p);
      p.displayClientMessage(Component.translatable(msg).withStyle(ChatFormatting.GRAY), true);
      sync(atv);
   }

   /** Box item carrying everything that may sit inside a container item; the rest goes to {@code loose}. */
   private static ItemStack packBox(NonNullList<ItemStack> items, List<ItemStack> loose) {
      List<ItemStack> keep = new ArrayList<>(BOX_SLOTS);
      boolean any = false;
      for (int i = 0; i < BOX_SLOTS; i++) {
         ItemStack s = items.get(i);
         items.set(i, ItemStack.EMPTY);
         if (s.isEmpty()) {
            keep.add(ItemStack.EMPTY);
         } else if (s.getItem().canFitInsideContainerItems()) {
            keep.add(s);
            any = true;
         } else {
            // shulker boxes, other cargo boxes: never nest container items
            keep.add(ItemStack.EMPTY);
            loose.add(s);
         }
      }
      ItemStack box = new ItemStack(RigContent.CARGO_BOX.get());
      if (any) {
         box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(keep));
      }
      return box;
   }

   static void detachBox(Atv atv, Player p) {
      if (atv.level().isClientSide || !hasBox(atv)) {
         return;
      }
      closeViewers(atv);
      List<ItemStack> loose = new ArrayList<>();
      ItemStack box = packBox(atv.getItemStacks(), loose);
      setFitted(atv, flags(atv) & ~BOX);
      give(p, box);
      for (ItemStack s : loose) {
         give(p, s);
      }
      atv.level().playSound(null, atv.getX(), atv.getY() + 0.8, atv.getZ(), RigContent.SND_ATTACH.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
      sync(atv);
   }

   static void detachCarrier(Atv atv, Player p) {
      if (atv.level().isClientSide || !hasCarrier(atv)) {
         return;
      }
      closeViewers(atv);
      NonNullList<ItemStack> items = atv.getItemStacks();
      for (int i = CAN_SLOT; i < SLOTS; i++) {
         ItemStack s = items.get(i);
         items.set(i, ItemStack.EMPTY);
         if (!s.isEmpty()) {
            give(p, s);
         }
      }
      setFitted(atv, flags(atv) & ~CARRIER);
      give(p, new ItemStack(RigContent.CAN_CARRIER.get()));
      atv.level().playSound(null, atv.getX(), atv.getY() + 0.8, atv.getZ(), RigContent.SND_ATTACH.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
      sync(atv);
   }

   private static void give(Player p, ItemStack s) {
      if (!s.isEmpty() && !p.getInventory().add(s)) {
         p.drop(s, false);
      }
   }

   /** Container rule (hoppers etc.): box slots need a box, cradle slots need the carrier and take one can each. */
   public static boolean canPlace(Atv atv, int slot, ItemStack stack) {
      if (slot < 0 || slot >= SLOTS) {
         return false;
      }
      if (slot < BOX_SLOTS) {
         return hasBox(atv);
      }
      return hasCarrier(atv) && stack.getItem() instanceof JerryCanItem && atv.getItemStacks().get(slot).isEmpty();
   }

   // ------------------------------------------------------------------------------------------------ reserve fuel

   /** Server: top the tank up from reachable cans. {@code auto} = the tank just ran dry under way. */
   public static boolean reserveRefuel(Atv atv, Player p, boolean auto) {
      double room = AtvFuel.TANK - AtvFuel.exact(atv);
      if (room < 0.05) {
         return false;
      }
      NonNullList<ItemStack> items = atv.getItemStacks();
      int f = flags(atv);
      double poured = 0.0;
      int[] order = new int[SLOTS];
      for (int i = 0; i < SLOTS; i++) {
         order[i] = (i + CAN_SLOT) % SLOTS; // cradles first, then the box
      }
      for (int slot : order) {
         if (poured >= room - 1.0E-4) {
            break;
         }
         ItemStack s = items.get(slot);
         if (!reachable(f, slot) || !(s.getItem() instanceof JerryCanItem)) {
            continue;
         }
         float l = JerryCanItem.litres(s);
         if (l <= 0.0F) {
            continue;
         }
         float take = (float)Math.min(l, room - poured);
         ItemStack after = s.copy();
         JerryCanItem.setLitres(after, l - take);
         items.set(slot, after);
         poured += take;
      }
      if (poured <= 0.0) {
         return false;
      }
      AtvFuel.set(atv, AtvFuel.exact(atv) + poured);
      Vec3 neck = AtvFuel.fillerNeck(atv);
      atv.level().playSound(null, neck.x, neck.y, neck.z, RigContent.SND_POUR.get(), SoundSource.NEUTRAL, 1.0F, 0.9F);
      if (p != null) {
         p.displayClientMessage(Component.translatable(auto ? "message.frontierhunts.atv_reserve_auto" : "message.frontierhunts.atv_reserve_poured",
            String.format("%.1f", poured)).withStyle(ChatFormatting.GOLD), true);
      }
      sync(atv);
      return true;
   }

   // ------------------------------------------------------------------------------------------------ lifecycle

   /** Replaces {@code Containers.dropContents} in {@link Atv#remove}: the box drops packed, cans and carrier loose. */
   public static void dropAll(Atv atv) {
      if (atv.level().isClientSide) {
         return;
      }
      NonNullList<ItemStack> items = atv.getItemStacks();
      List<ItemStack> out = new ArrayList<>();
      if (hasBox(atv)) {
         out.add(packBox(items, out));
      }
      for (int i = 0; i < items.size(); i++) {
         if (!items.get(i).isEmpty()) {
            out.add(items.get(i));
            items.set(i, ItemStack.EMPTY);
         }
      }
      if (hasCarrier(atv)) {
         out.add(new ItemStack(RigContent.CAN_CARRIER.get()));
      }
      setFitted(atv, 0);
      for (ItemStack s : out) {
         atv.spawnAtLocation(s, 0.6F);
      }
   }

   /** Replaces {@link Atv#destroy(DamageSource)}: the ATV item keeps its fuel; the rig drops on removal. */
   public static void destroyed(Atv atv, DamageSource source) {
      ItemStack drop = new ItemStack(RideContent.ATV_ITEM.get());
      AtvFuel.toItem(atv, drop);
      drop.set(DataComponents.CUSTOM_NAME, atv.getCustomName());
      atv.kill();
      if (atv.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
         atv.spawnAtLocation(drop);
      }
   }

   public static void save(Atv atv, CompoundTag tag) {
      tag.putInt("FhRig", flags(atv) & FITTED);
   }

   public static void load(Atv atv, CompoundTag tag) {
      int f;
      if (tag.contains("FhRig")) {
         f = tag.getInt("FhRig") & FITTED;
      } else {
         // ATVs from before the rig had built-in storage: anything in it arrives in a fitted cargo box
         f = 0;
         NonNullList<ItemStack> items = atv.getItemStacks();
         for (int i = 0; i < BOX_SLOTS && i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
               f |= BOX;
               break;
            }
         }
      }
      setFitted(atv, f);
      sync(atv);
   }
}
