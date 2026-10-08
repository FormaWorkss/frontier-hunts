package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [phone] The server side of the Field Phone: every {@link PhoneNet.Ask} lands here. Checks first (a living player who
 * carries a charged phone, or stands at a Camera Base Station for camera requests; a bar of signal for anything that
 * goes over the network; a request budget per player), then hands the op to the app's server part.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class PhoneServer {
   static final Logger LOG = LoggerFactory.getLogger("Frontier Hunts phone");
   /** request budget: a bucket of 24 that refills at 12 a second */
   private static final float BUCKET = 24.0F;
   private static final float REFILL_PER_TICK = 0.6F;
   private static final Map<UUID, Budget> BUDGETS = new HashMap<>();
   private static final Map<UUID, Station> STATIONS = new HashMap<>();

   private static final class Budget {
      float tokens = BUCKET;
      long tick;
   }

   /** A retired Camera Base Station the player opened (its cameras work while they stand by it). */
   record Station(String dim, BlockPos pos) {
   }

   private PhoneServer() {
   }

   // ------------------------------------------------------------------------------------------------ checks

   /** The phone the player carries (main hand, off hand, then the inventory), or empty. */
   public static ItemStack phone(ServerPlayer player) {
      Inventory inv = player.getInventory();
      ItemStack best = ItemStack.EMPTY;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (FieldPhoneItem.is(s)) {
            if (FieldPhoneItem.charge(s) > 0) {
               return s;
            }
            best = s;
         }
      }
      return best;
   }

   public static boolean hasCharged(ServerPlayer player) {
      ItemStack s = phone(player);
      return !s.isEmpty() && FieldPhoneItem.charge(s) > 0;
   }

   public static int signal(ServerPlayer player) {
      return PhoneSignal.bars(player.level(), player.getX(), player.getY(), player.getZ());
   }

   /** Is the player using a retired base station right now (in reach, still standing)? */
   public static boolean atStation(ServerPlayer player) {
      return station(player) != null;
   }

   /** Forget the budget and station of a player (QA). */
   public static void reset(ServerPlayer player) {
      BUDGETS.remove(player.getUUID());
      STATIONS.remove(player.getUUID());
   }

   /** The base station the player is using right now, or null. */
   static Station station(ServerPlayer player) {
      Station s = STATIONS.get(player.getUUID());
      if (s == null) {
         return null;
      }
      if (!s.dim.equals(player.level().dimension().location().toString()) || player.blockPosition().distSqr(s.pos) > 10.0 * 10.0
         || !player.level().hasChunkAt(s.pos) || !OldBaseStation.is(player.level().getBlockState(s.pos))) {
         STATIONS.remove(player.getUUID());
         return null;
      }
      return s;
   }

   /** May this player reach their trail cameras now (a charged phone with signal, or at a base station)? */
   public static boolean mayUseCameras(ServerPlayer player) {
      if (!player.isAlive() || player.isSpectator()) {
         return false;
      }
      return station(player) != null || hasCharged(player) && signal(player) > 0;
   }

   private static boolean spend(ServerPlayer player, float cost) {
      long now = player.level().getGameTime();
      Budget b = BUDGETS.computeIfAbsent(player.getUUID(), k -> new Budget());
      if (b.tick != 0L && now > b.tick) {
         b.tokens = Math.min(BUCKET, b.tokens + (now - b.tick) * REFILL_PER_TICK);
      } else if (now < b.tick) {
         b.tokens = BUCKET;
      }
      b.tick = now;
      if (b.tokens < cost) {
         return false;
      }
      b.tokens -= cost;
      return true;
   }

   /** A short message on the phone (a refused action says why). */
   public static void toast(ServerPlayer player, String text) {
      CompoundTag t = new CompoundTag();
      t.putString("text", text.length() > 160 ? text.substring(0, 160) : text);
      PhoneNet.send(player, PhoneNet.K_TOAST, t);
   }

   // ------------------------------------------------------------------------------------------------ dispatch

   public static void handle(ServerPlayer player, PhoneNet.Ask ask) {
      if (!(player.level() instanceof ServerLevel) || player.hasDisconnected()) {
         return;
      }
      int op = ask.op();
      if (op < 0 || op >= PhoneNet.OP_COUNT) {
         return;
      }
      float cost = switch (op) {
         case PhoneNet.OP_FLUSH_PROGRESS, PhoneNet.OP_STATE, PhoneNet.OP_POSE -> 0.5F;
         case PhoneNet.OP_MESSAGE, PhoneNet.OP_INVITE, PhoneNet.OP_REMATCH -> 4.0F;
         default -> 1.0F;
      };
      if (!spend(player, cost)) {
         return;
      }
      try {
         if (op == PhoneNet.OP_STATION) {
            openedStation(player, BlockPos.of(ask.a()));
            return;
         }
         boolean alive = player.isAlive() && !player.isSpectator();
         boolean phone = alive && hasCharged(player);
         if (op == PhoneNet.OP_CAMERA || op == PhoneNet.OP_REFRESH && ask.a() == PhoneNet.R_CAMS) {
            if (alive && (phone || station(player) != null)) {
               PhoneCams.handle(player, ask);
            }
            return;
         }
         if (op == PhoneNet.OP_STATE) {
            PhoneBattery.state(player, (int)ask.a());
            return;
         }
         if (!phone) {
            return;
         }
         int bars = signal(player);
         switch (op) {
            case PhoneNet.OP_REFRESH -> refresh(player, (int)ask.a(), bars);
            case PhoneNet.OP_CONTRACT -> {
               if (needSignal(player, bars)) {
                  PhoneContracts.op(player, (int)ask.a(), (int)ask.b(), ask.s());
               }
            }
            case PhoneNet.OP_PIN_ADD -> PhonePlaces.addPin(player, ask.s(), (int)ask.a());
            case PhoneNet.OP_PIN_REMOVE -> PhonePlaces.removePin(player, ask.s());
            case PhoneNet.OP_INVITE, PhoneNet.OP_ANSWER, PhoneNet.OP_MOVE, PhoneNet.OP_LEAVE, PhoneNet.OP_FLUSH_START, PhoneNet.OP_FLUSH_SUBMIT,
               PhoneNet.OP_FLUSH_PROGRESS, PhoneNet.OP_STAT, PhoneNet.OP_REMATCH -> {
               // a game already running continues without signal (a move is relayed when it comes back); new ones need a bar
               boolean starts = op == PhoneNet.OP_INVITE || op == PhoneNet.OP_ANSWER && ask.b() != 0L || op == PhoneNet.OP_FLUSH_START
                  || op == PhoneNet.OP_REMATCH;
               if (!starts || needSignal(player, bars)) {
                  PhoneGames.handle(player, ask);
               }
            }
            case PhoneNet.OP_MESSAGE -> {
               if (needSignal(player, bars)) {
                  PhoneMessages.send(player, ask.s());
               }
            }
            case PhoneNet.OP_MESSAGE_READ -> PhoneMessages.read(player, ask.s());
            case PhoneNet.OP_PHOTO_GET -> {
               if (needSignal(player, bars)) {
                  PhonePhotos.get(player, ask.a());
               }
            }
            case PhoneNet.OP_POSE -> PhonePhotos.pose(player, (int)ask.a());
            default -> {
            }
         }
      } catch (RuntimeException e) {
         LOG.debug("Field Phone request {} from {} failed", op, player.getScoreboardName(), e);
      }
   }

   private static boolean needSignal(ServerPlayer player, int bars) {
      if (bars > 0) {
         return true;
      }
      toast(player, "No signal here");
      return false;
   }

   private static void refresh(ServerPlayer player, int what, int bars) {
      switch (what) {
         case PhoneNet.R_WEATHER -> PhoneWeather.send(player, bars);
         case PhoneNet.R_PLACES -> PhonePlaces.send(player);
         case PhoneNet.R_WALLET -> PhoneWallet.send(player);
         case PhoneNet.R_CONTRACTS, PhoneNet.R_BOARD -> PhoneContracts.send(player);
         case PhoneNet.R_GAMES -> PhoneGames.sendState(player);
         case PhoneNet.R_MESSAGES -> PhoneMessages.sendAll(player);
         default -> {
         }
      }
   }

   public static void openedStation(ServerPlayer player, BlockPos pos) {
      if (!player.isAlive() || player.isSpectator() || player.blockPosition().distSqr(pos) > 8.0 * 8.0 || !player.level().hasChunkAt(pos)
         || !OldBaseStation.is(player.level().getBlockState(pos)) || !player.level().mayInteract(player, pos)) {
         return;
      }
      STATIONS.put(player.getUUID(), new Station(player.level().dimension().location().toString(), pos.immutable()));
      PhoneCams.sendList(player);
   }

   /** A hunter took an animal: remember where for the Maps app's "Last harvest" waypoint. */
   @SubscribeEvent
   public static void harvest(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
      if (!(e.getSource().getEntity() instanceof ServerPlayer player) || player instanceof net.neoforged.neoforge.common.util.FakePlayer
         || !(e.getEntity() instanceof net.minecraft.world.entity.animal.Animal animal) || e.getEntity().level().isClientSide()) {
         return;
      }
      net.minecraft.resources.ResourceLocation type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
      if (!FrontierHunts.ID.equals(type.getNamespace())) {
         return;
      }
      try {
         PhoneStore store = PhoneStore.get(player.server);
         PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
         h.killPos = animal.blockPosition().asLong();
         h.killDim = animal.level().dimension().location().toString();
         // the id, not the translated name: a dedicated server has no mod language loaded
         String path = type.getPath().replace('_', ' ');
         String what = path.isEmpty() ? "Animal" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
         h.killWhat = (what.length() > 40 ? what.substring(0, 40) : what) + " · day " + (animal.level().getDayTime() / 24000L + 1L);
         store.setDirty();
      } catch (RuntimeException ex) {
         LOG.debug("Field Phone: could not note a harvest", ex);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      UUID id = e.getEntity().getUUID();
      BUDGETS.remove(id);
      STATIONS.remove(id);
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      BUDGETS.clear();
      STATIONS.clear();
   }
}
