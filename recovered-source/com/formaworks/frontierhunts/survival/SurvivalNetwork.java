package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * [survival] Two server-to-client payloads: the player's own survival state (about 20 bytes, sent when it changes, at most
 * once a second, plus a keep-alive every 5 s) and the resolved nutrition table (login and datapack reload).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SurvivalNetwork {
   private SurvivalNetwork() {
   }

   /** Snapshot for the HUD. Flags: see the F_ constants. */
   public record State(
      int mode, int flags, float protein, float fat, float energy, float heat, float felt, float insulation, float wet, float trend, int warn
   ) implements CustomPacketPayload {
      public static final int F_SPOILAGE = 1, F_TEMPERATURE = 2, F_VIGOR = 4, F_SHELTER = 8, F_INDOORS = 16, F_FIRE = 32, F_MITTENS = 64, F_EXEMPT = 128;
      /** [shelter] Inside a tent (flags are a var-int now). */
      public static final int F_TENT = 256;
      public static final State OFF = new State(0, 0, 80F, 80F, 80F, 0F, 15F, 1F, 0F, 0F, 0);
      public static final Type<State> TYPE = new Type<>(FrontierHunts.id("survival_state"));
      public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
         (buf, s) -> {
            buf.writeByte(s.mode);
            buf.writeVarInt(s.flags); // [shelter] was a byte; F_TENT needs a ninth bit
            buf.writeByte(Math.round(clamp(s.protein, 0F, 100F) * 2F));
            buf.writeByte(Math.round(clamp(s.fat, 0F, 100F) * 2F));
            buf.writeByte(Math.round(clamp(s.energy, 0F, 100F) * 2F));
            buf.writeShort(Math.round(clamp(s.heat, -100F, 100F) * 100F));
            buf.writeShort(Math.round(clamp(s.felt, -90F, 90F) * 100F));
            buf.writeByte(Math.round(clamp(s.insulation, 0F, 25F) * 10F));
            buf.writeByte(Math.round(clamp(s.wet, 0F, 1F) * 100F));
            buf.writeByte(Math.round(clamp(s.trend, -12F, 12F) * 10F));
            buf.writeVarInt(s.warn);
         },
         buf -> new State(
            buf.readUnsignedByte(),
            buf.readVarInt(),
            buf.readUnsignedByte() / 2F,
            buf.readUnsignedByte() / 2F,
            buf.readUnsignedByte() / 2F,
            buf.readShort() / 100F,
            buf.readShort() / 100F,
            buf.readUnsignedByte() / 10F,
            buf.readUnsignedByte() / 100F,
            buf.readByte() / 10F,
            buf.readVarInt()
         )
      );

      public boolean has(int f) {
         return (this.flags & f) != 0;
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record Table(Map<Item, FoodValues> foods) implements CustomPacketPayload {
      public static final Type<Table> TYPE = new Type<>(FrontierHunts.id("survival_foods"));
      public static final StreamCodec<FriendlyByteBuf, Table> CODEC = StreamCodec.of(
         (buf, t) -> {
            buf.writeVarInt(t.foods.size());
            t.foods.forEach((item, v) -> {
               buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(item));
               v.write(buf);
            });
         },
         buf -> {
            int n = Math.min(buf.readVarInt(), 20000);
            Map<Item, FoodValues> m = new IdentityHashMap<>();
            for (int i = 0; i < n; i++) {
               ResourceLocation id = buf.readResourceLocation();
               FoodValues v = FoodValues.read(buf);
               if (BuiltInRegistries.ITEM.containsKey(id)) {
                  m.put(BuiltInRegistries.ITEM.get(id), v);
               }
            }
            return new Table(m);
         }
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Latest state on the client (common holder: no client classes involved). */
   private static volatile State client = State.OFF;

   public static State clientState() {
      return client;
   }

   public static void clearClient() {
      client = State.OFF;
      SurvivalSync.clearClient();
      NutritionTable.clearClient();
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent e) {
      PayloadRegistrar r = e.registrar("1").optional();
      r.playToClient(State.TYPE, State.CODEC, (s, ctx) -> ctx.enqueueWork(() -> {
         client = s;
         SurvivalSync.setClient(s.mode, s.has(State.F_SPOILAGE), s.has(State.F_TEMPERATURE));
      }));
      r.playToClient(Table.TYPE, Table.CODEC, (t, ctx) -> ctx.enqueueWork(() -> NutritionTable.setClient(t.foods)));
   }

   public static void send(ServerPlayer p, State s) {
      if (p.connection != null) {
         PacketDistributor.sendToPlayer(p, s);
      }
   }

   /** Sends the resolved table to one player, or to everyone when {@code p} is null. */
   public static void sendTable(ServerPlayer p) {
      Table t = new Table(NutritionTable.resolveAll());
      if (p != null) {
         if (p.connection != null) {
            PacketDistributor.sendToPlayer(p, t);
         }
      } else {
         PacketDistributor.sendToAllPlayers(t);
      }
   }

   static float clamp(float v, float lo, float hi) {
      return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 0F;
   }
}
