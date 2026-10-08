package com.formaworks.frontierhunts.clothing;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.Coverall;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.hunting.HuntPerception;
import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalConfig;
import com.formaworks.frontierhunts.survival.SurvivalService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * [clothing] Server side of the carbon base layer: wearing it (right-click, the Field Gear panel), the once-a-second
 * update (sweat, wet clothes, carbon charge worn down by use and restored by washing in water, a fire or a night at
 * camp), dropping it like armour on death without keepInventory, moving old carbon pieces out of the armour slots, and
 * the sync: the pieces to everyone who can see them (a piece under an outer garment is not sent to other players), the
 * scent state (sweat, wet, spray) to the wearer only.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class BaseLayerService {
   /** Charge: lost per second while worn (dry, still), extra per unit of sweat, extra while moving. ~3 h still, ~35 min sprinting soaked. */
   public static final float WEAR = 0.00009F, WEAR_SWEAT = 0.0004F, WEAR_MOVING = 0.00006F;
   /** Charge regained per second: washing in water, aired by a fire, asleep at camp. */
   public static final float WASH = 0.04F, FIRE = 0.01F, SLEEP = 0.05F;
   /** client-side receivers (set by clothing.client.BaseLayerClient) */
   public static Consumer<Sync> syncReceiver = s -> {
   };
   public static Consumer<State> stateReceiver = s -> {
   };

   private BaseLayerService() {
   }

   // ============================================================================================ coverage (shared with the renderer)

   /** Something is worn over the head (any head-slot item, or a one-piece coverall's hood). */
   public static boolean headCovered(LivingEntity e) {
      return !e.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || e.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof Coverall;
   }

   /** A garment covers the torso and arms (armour chest pieces, coveralls, coats; not an elytra). */
   public static boolean torsoCovered(LivingEntity e) {
      ItemStack c = e.getItemBySlot(EquipmentSlot.CHEST);
      return c.getItem() instanceof Coverall || c.getItem() instanceof ArmorItem a && a.getType() == ArmorItem.Type.CHESTPLATE;
   }

   /** Something covers the legs (a legs-slot garment, or a one-piece coverall). */
   public static boolean legsCovered(LivingEntity e) {
      ItemStack l = e.getItemBySlot(EquipmentSlot.LEGS);
      return l.getItem() instanceof ArmorItem || e.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof Coverall;
   }

   /** Which base-layer stacks other players can see (the rest are hidden under outer clothing). */
   static ItemStack visible(LivingEntity e, ItemStack[] s, int slot) {
      ItemStack st = s[slot];
      if (st.isEmpty() || e.isInvisible()) {
         return ItemStack.EMPTY;
      }
      return switch (slot) {
         case BaseLayer.HOOD -> headCovered(e) ? ItemStack.EMPTY : st;
         case BaseLayer.TROUSERS -> legsCovered(e) ? ItemStack.EMPTY : st;
         default -> BaseLayer.suit(s) ? (torsoCovered(e) && headCovered(e) && legsCovered(e) ? ItemStack.EMPTY : st)
            : (torsoCovered(e) ? ItemStack.EMPTY : st);
      };
   }

   // ============================================================================================ wearing

   /** Right-click with a carbon piece: wear it under the clothes; whatever was in its place goes to the hand. */
   public static void wearFromHand(ServerPlayer sp, InteractionHand hand) {
      ItemStack held = sp.getItemInHand(hand);
      if (!(held.getItem() instanceof ScentControl c) || !sp.isAlive() || sp.isSpectator()) {
         return;
      }
      List<ItemStack> off = new ArrayList<>();
      ItemStack one = held.copyWithCount(1);
      ItemStack replaced = put(sp, one, off);
      held.shrink(1);
      if (held.isEmpty()) {
         sp.setItemInHand(hand, replaced);
      } else if (!replaced.isEmpty()) {
         off.add(0, replaced);
      }
      for (ItemStack s : off) {
         give(sp, s);
      }
      equipped(sp, c);
   }

   /**
    * Put a carbon piece on (server). Returns the piece it replaced in its own slot (or empty); other pieces that had to
    * come off (hood / trousers for a one-piece suit, the suit for a hood or trousers) are added to {@code off}.
    */
   static ItemStack put(ServerPlayer sp, ItemStack piece, List<ItemStack> off) {
      BaseLayer b = BaseLayer.of(sp);
      ScentControl c = (ScentControl) piece.getItem();
      int slot = BaseLayer.slotFor(c.piece);
      ItemStack replaced = b.get(slot);
      if (c.piece == ScentControl.Piece.SUIT) {
         for (int i : new int[]{BaseLayer.HOOD, BaseLayer.TROUSERS}) {
            if (!b.get(i).isEmpty()) {
               off.add(b.get(i));
               b.set(i, ItemStack.EMPTY);
            }
         }
      } else if (slot != BaseLayer.TOP && BaseLayer.suit(b.slots)) {
         off.add(b.get(BaseLayer.TOP));
         b.set(BaseLayer.TOP, ItemStack.EMPTY);
      }
      b.set(slot, piece);
      sync(sp, b, true);
      return replaced;
   }

   static ItemStack takeOff(ServerPlayer sp, int slot) {
      BaseLayer b = BaseLayer.of(sp);
      ItemStack s = b.get(slot);
      if (!s.isEmpty()) {
         b.set(slot, ItemStack.EMPTY);
         sync(sp, b, true);
         sp.level().playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 0.9F);
      }
      return s;
   }

   private static void equipped(ServerPlayer sp, ScentControl c) {
      sp.level().playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
      sp.displayClientMessage(Component.translatable("clothing.frontierhunts.msg.on." + c.piece.id), true);
   }

   private static void give(ServerPlayer sp, ItemStack s) {
      if (!s.isEmpty() && !sp.getInventory().add(s)) {
         sp.drop(s, false);
      }
   }

   /**
    * The Field Gear panel: click a base-layer slot with the cursor stack (put on / swap / take off into the cursor), or
    * shift-click (take off into the inventory). Creative: the client sends its cursor (the creative screen keeps the
    * cursor on the client), like the pack and quiver slots.
    */
   static void click(ServerPlayer sp, Action a) {
      if (!sp.isAlive() || sp.isSpectator() || a.slot() < 0 || a.slot() >= BaseLayer.SLOTS) {
         return;
      }
      long now = sp.level().getGameTime();
      BaseLayer b = BaseLayer.of(sp);
      if (now - b.lastClick < 2L && now >= b.lastClick) {
         return; // two clicks a tick at most: a broken or hostile client cannot spam
      }
      b.lastClick = now;
      boolean creative = sp.isCreative();
      if (!creative && sp.containerMenu != sp.inventoryMenu) {
         return;
      }
      if (a.action() == Action.TAKE_OFF) {
         give(sp, takeOff(sp, a.slot()));
         return;
      }
      AbstractContainerMenu menu = sp.containerMenu;
      ItemStack cursor = creative ? a.creativeCursor().copy() : menu.getCarried();
      if (cursor.isEmpty()) {
         ItemStack s = takeOff(sp, a.slot());
         if (!s.isEmpty()) {
            if (creative) {
               give(sp, s);
            } else {
               menu.setCarried(s);
               menu.broadcastChanges();
            }
         }
         return;
      }
      if (!(cursor.getItem() instanceof ScentControl c) || BaseLayer.slotFor(c.piece) != a.slot()) {
         return;
      }
      List<ItemStack> off = new ArrayList<>();
      ItemStack replaced = put(sp, cursor.copyWithCount(1), off);
      if (creative) {
         off.add(0, replaced);
      } else {
         cursor.shrink(1);
         if (cursor.isEmpty()) {
            menu.setCarried(replaced);
         } else {
            off.add(0, replaced);
         }
         menu.broadcastChanges();
      }
      for (ItemStack s : off) {
         give(sp, s);
      }
      equipped(sp, c);
   }

   // ============================================================================================ the second tick

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer sp) || (sp.tickCount + sp.getId()) % 20 != 0) {
         return;
      }
      migrate(sp);
      BaseLayer b = BaseLayer.of(sp);
      update(sp, b);
      sync(sp, b, false);
   }

   /** Carbon pieces from before the base layer existed, still sitting in the armour slots: into the base layer. */
   public static void migrate(ServerPlayer sp) {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack s = sp.getItemBySlot(slot);
         if (s.getItem() instanceof ScentControl c) {
            sp.setItemSlot(slot, ItemStack.EMPTY);
            BaseLayer b = BaseLayer.of(sp);
            int to = BaseLayer.slotFor(c.piece);
            boolean free = b.get(to).isEmpty() && !(to != BaseLayer.TOP && BaseLayer.suit(b.slots))
               && !(c.piece == ScentControl.Piece.SUIT && (!b.get(BaseLayer.HOOD).isEmpty() || !b.get(BaseLayer.TROUSERS).isEmpty()));
            if (free) {
               b.set(to, s);
               sp.displayClientMessage(Component.translatable("clothing.frontierhunts.msg.moved"), false);
            } else {
               give(sp, s);
            }
         }
      }
   }

   static void update(ServerPlayer sp, BaseLayer b) {
      boolean water = sp.isInWater();
      boolean survival = SurvivalConfig.mode().on();
      // wet clothes: the survival meter when it runs, otherwise a simple soak/dry
      if (survival) {
         b.wet = SurvivalService.state(sp).wet;
      } else if (water) {
         b.wet = 1F;
      } else if (sp.isInWaterOrRain()) {
         b.wet = Math.min(1F, b.wet + 0.03F);
      } else {
         b.wet = Math.max(0F, b.wet - 0.008F);
      }
      double speed = HuntPerception.speed(sp.getUUID());
      boolean moving = speed > 0.1;
      // sweat: hard work, heavy clothes on the move, an overheated body; washed off in water
      if (water) {
         b.sweat = 0F;
      } else {
         float heat = survival && SurvivalConfig.temperature() ? SurvivalService.state(sp).heat : 0F;
         float ins = Clothing.outfit(sp).insulation();
         float gain = 0F;
         if (sp.isSprinting()) {
            gain += 0.045F + 0.01F * Math.max(0F, ins - 2F);
         } else if (moving) {
            gain += 0.002F + 0.004F * Math.max(0F, ins - 2.5F);
         }
         if (heat > 30F) {
            gain += 0.03F * Math.min(1F, (heat - 30F) / 40F);
         }
         float loss = (moving ? 0.006F : 0.02F) + (heat < -30F ? 0.02F : 0F);
         b.sweat = Mth.clamp(b.sweat + gain - loss, 0F, 1F);
      }
      if (b.isEmpty()) {
         return;
      }
      // carbon charge
      float d;
      if (water) {
         d = WASH;
      } else if (sp.isSleeping()) {
         d = SLEEP;
      } else if (byFire(sp)) {
         d = FIRE;
      } else {
         d = -(WEAR + WEAR_SWEAT * b.sweat + (moving ? WEAR_MOVING : 0F));
      }
      for (int i = 0; i < BaseLayer.SLOTS; i++) {
         ItemStack s = b.get(i);
         if (s.isEmpty()) {
            continue;
         }
         float c = ScentControl.charge(s);
         float n = Mth.clamp(c + d, 0F, 1F);
         // stored in 1% steps: the stack (and the sync) changes about every 2 minutes of wear, every second while washing
         int q = Math.round(n * 100F);
         if (q != Math.round(c * 100F)) {
            ItemStack copy = s.copy();
            ScentControl.setCharge(copy, q / 100F);
            b.set(i, copy);
            if (d > 0F && q == 100) {
               sp.displayClientMessage(Component.translatable(water ? "clothing.frontierhunts.msg.washed" : "clothing.frontierhunts.msg.aired"), true);
            }
         }
      }
   }

   /**
    * Within two blocks of a real fire (a lit campfire, a fire, a lit stove or furnace: Thermal.heatOf >= 9; torches and
    * lanterns do not count). 5 x 3 x 5 blocks, once a second, only while a worn carbon piece is below full charge.
    */
   static boolean byFire(ServerPlayer sp) {
      BaseLayer b = BaseLayer.of(sp);
      boolean need = false;
      for (ItemStack s : b.slots) {
         need |= !s.isEmpty() && ScentControl.charge(s) < 0.995F;
      }
      if (!need) {
         return false;
      }
      net.minecraft.core.BlockPos f = sp.blockPosition();
      net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
      for (int dy = -1; dy <= 1; dy++) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               m.set(f.getX() + dx, f.getY() + dy, f.getZ() + dz);
               if (com.formaworks.frontierhunts.survival.Thermal.heatOf(sp.level().getBlockState(m)) >= 9F) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   // ============================================================================================ death, respawn, sync

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void drops(LivingDropsEvent e) {
      if (!(e.getEntity() instanceof ServerPlayer sp) || sp.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
         return;
      }
      BaseLayer b = BaseLayer.of(sp);
      for (int i = 0; i < BaseLayer.SLOTS; i++) {
         ItemStack s = b.get(i);
         if (s.isEmpty()) {
            continue;
         }
         ItemEntity ie = new ItemEntity(sp.level(), sp.getX(), sp.getEyeY() - 0.3, sp.getZ(), s);
         ie.setDefaultPickUpDelay();
         float r = sp.getRandom().nextFloat() * 0.5F, a = sp.getRandom().nextFloat() * Mth.TWO_PI;
         ie.setDeltaMovement(-Mth.sin(a) * r, 0.2, Mth.cos(a) * r);
         e.getDrops().add(ie);
         b.set(i, ItemStack.EMPTY);
      }
      b.sweat = 0F;
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void clone(PlayerEvent.Clone e) {
      if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer sp) {
         BaseLayer b = BaseLayer.of(sp);
         b.sweat = 0F;
         b.wet = 0F;
      }
   }

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer sp) {
         migrate(sp);
         resync(sp);
      }
   }

   @SubscribeEvent
   public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer sp) {
         resync(sp);
      }
   }

   @SubscribeEvent
   public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
      if (e.getEntity() instanceof ServerPlayer sp) {
         resync(sp);
      }
   }

   @SubscribeEvent
   public static void tracking(PlayerEvent.StartTracking e) {
      if (e.getEntity() instanceof ServerPlayer to && e.getTarget() instanceof ServerPlayer target && ready(to)) {
         Sync s = others(target, BaseLayer.of(target));
         if (!s.empty()) {
            PacketDistributor.sendToPlayer(to, s);
         }
      }
   }

   /** Send everything again (after login, respawn or a dimension change the client starts from nothing). */
   public static void resync(ServerPlayer sp) {
      BaseLayer b = BaseLayer.of(sp);
      b.sentHash = Integer.MIN_VALUE;
      b.sentOthers = Integer.MIN_VALUE;
      b.sentState = Integer.MIN_VALUE;
      sync(sp, b, true);
   }

   static boolean ready(ServerPlayer p) {
      try {
         return p.connection != null && p.connection.hasChannel(Sync.TYPE);
      } catch (RuntimeException e) {
         return false; // another mod's fake player (a deployer using a carbon piece) has no real connection
      }
   }

   static void sync(ServerPlayer sp, BaseLayer b, boolean force) {
      if (!ready(sp)) {
         return;
      }
      int h = b.hash();
      if (force || h != b.sentHash) {
         b.sentHash = h;
         PacketDistributor.sendToPlayer(sp, new Sync(sp.getId(), b.get(0), b.get(1), b.get(2)));
      }
      Sync o = others(sp, b);
      int ho = o.hash();
      if (ho != b.sentOthers) {
         b.sentOthers = ho;
         PacketDistributor.sendToPlayersTrackingEntity(sp, o);
      }
      long spray = sp.getPersistentData().getLong(Scent.SPRAY_KEY);
      int st = Math.round(b.sweat * 50F) * 31 * 31 + Math.round(b.wet * 50F) * 31 + Long.hashCode(spray);
      if (force || st != b.sentState) {
         b.sentState = st;
         PacketDistributor.sendToPlayer(sp, new State(b.sweat, b.wet, spray));
      }
   }

   static Sync others(ServerPlayer sp, BaseLayer b) {
      return new Sync(sp.getId(), visible(sp, b.slots, 0), visible(sp, b.slots, 1), visible(sp, b.slots, 2));
   }

   // ============================================================================================ payloads

   /** Base layer of one player (to that player in full, to others only what is visible). */
   public record Sync(int entity, ItemStack hood, ItemStack top, ItemStack trousers) implements CustomPacketPayload {
      public static final Type<Sync> TYPE = new Type<>(FrontierHunts.id("base_layer"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of((buf, s) -> {
         buf.writeVarInt(s.entity);
         ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.hood);
         ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.top);
         ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.trousers);
      }, buf -> new Sync(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
         ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

      boolean empty() {
         return this.hood.isEmpty() && this.top.isEmpty() && this.trousers.isEmpty();
      }

      int hash() {
         int h = 7;
         for (ItemStack s : new ItemStack[]{this.hood, this.top, this.trousers}) {
            // other players only see the piece, not its charge: hash the item alone
            h = 31 * h + (s.isEmpty() ? 0 : s.getItem().hashCode());
         }
         return h;
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** The wearer's own scent state: sweat, wet clothes, scent-cover spray end (game time). */
   public record State(float sweat, float wet, long sprayUntil) implements CustomPacketPayload {
      public static final Type<State> TYPE = new Type<>(FrontierHunts.id("scent_state"));
      public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, s) -> {
         buf.writeFloat(s.sweat);
         buf.writeFloat(s.wet);
         buf.writeVarLong(s.sprayUntil);
      }, buf -> new State(buf.readFloat(), buf.readFloat(), buf.readVarLong()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Field Gear panel click on a base-layer slot. */
   public record Action(int action, int slot, ItemStack creativeCursor) implements CustomPacketPayload {
      public static final int CLICK = 0, TAKE_OFF = 1;
      public static final Type<Action> TYPE = new Type<>(FrontierHunts.id("base_layer_action"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, a) -> {
         buf.writeVarInt(a.action);
         buf.writeVarInt(a.slot);
         ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, a.creativeCursor);
      }, buf -> new Action(buf.readVarInt(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent
      public static void payloads(RegisterPayloadHandlersEvent e) {
         var r = e.registrar("1").optional();
         r.playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> syncReceiver.accept(p)));
         r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> stateReceiver.accept(p)));
         r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && (p.action() == Action.CLICK || p.action() == Action.TAKE_OFF)) {
               click(sp, p);
            }
         }));
      }
   }

   /** For tests: the player's base layer after a server-side wear (no hand involved). */
   public static ItemStack wear(ServerPlayer sp, ItemStack piece) {
      List<ItemStack> off = new ArrayList<>();
      ItemStack replaced = put(sp, piece.copyWithCount(1), off);
      for (ItemStack s : off) {
         give(sp, s);
      }
      return replaced;
   }

   /** For tests and commands: take every base-layer piece off into the inventory. */
   public static void undress(ServerPlayer sp) {
      for (int i = 0; i < BaseLayer.SLOTS; i++) {
         give(sp, takeOff(sp, i));
      }
   }
}
