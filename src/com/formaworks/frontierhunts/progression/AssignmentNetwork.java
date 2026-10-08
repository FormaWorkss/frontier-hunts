package com.formaworks.frontierhunts.progression;

import com.formaworks.frontierhunts.FrontierHunts;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AssignmentNetwork {
   public static Consumer<AssignmentNetwork.Snapshot> receiver = var0 -> {
   };

   private static void write(FriendlyByteBuf var0, Assignment var1) {
      var0.writeUtf(((JsonElement)Assignment.CODEC.encodeStart(JsonOps.INSTANCE, var1).getOrThrow()).toString(), 2048);
   }

   private static Assignment read(FriendlyByteBuf var0) {
      return (Assignment)Assignment.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(var0.readUtf(2048))).getOrThrow();
   }

   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("1");
      var1.playToClient(AssignmentNetwork.Snapshot.TYPE, AssignmentNetwork.Snapshot.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
         AssignmentService.clientSkills = var0x.skills(); // [academy] certifications for the client-side bow hold
         receiver.accept(var0x);
      }));
      var1.playToServer(AssignmentNetwork.Request.TYPE, AssignmentNetwork.Request.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               AssignmentService.request(var2, var0x);
            }
         }));
   }

   private AssignmentNetwork() {
   }

   public static record Offer(String id, Assignment terms, int cooldown) {
   }

   public static record Request(int action, String id, int node) implements CustomPacketPayload {
      public static final int OPEN = 0;
      public static final int ACCEPT = 1;
      public static final int CLAIM = 2;
      public static final int CANCEL = 3;
      public static final int TRAIN = 4;
      public static final int RESPEC = 5;
      public static final Type<AssignmentNetwork.Request> TYPE = new Type(FrontierHunts.id("assignment_request"));
      public static final StreamCodec<FriendlyByteBuf, AssignmentNetwork.Request> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.action);
         var0.writeUtf(var1.id, 128);
         var0.writeVarInt(var1.node);
      }, var0 -> new AssignmentNetwork.Request(var0.readVarInt(), var0.readUtf(128), var0.readVarInt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Snapshot(
      boolean open,
      String dimension,
      boolean eligible,
      int xp,
      int skills,
      int tokens,
      int completed,
      int state,
      int count,
      long remaining,
      String id,
      Assignment terms,
      List<AssignmentNetwork.Offer> offers
   ) implements CustomPacketPayload {
      public static final Type<AssignmentNetwork.Snapshot> TYPE = new Type(FrontierHunts.id("assignment_snapshot"));
      public static final StreamCodec<FriendlyByteBuf, AssignmentNetwork.Snapshot> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeBoolean(var1.open);
         var0.writeUtf(var1.dimension, 256);
         var0.writeBoolean(var1.eligible);
         var0.writeVarInt(var1.xp);
         var0.writeByte(var1.skills);
         var0.writeVarInt(var1.tokens);
         var0.writeVarInt(var1.completed);
         var0.writeByte(var1.state);
         var0.writeByte(var1.count);
         var0.writeVarLong(var1.remaining);
         var0.writeUtf(var1.id, 128);
         var0.writeBoolean(var1.terms != null);
         if (var1.terms != null) {
            AssignmentNetwork.write(var0, var1.terms);
         }

         var0.writeVarInt(var1.offers.size());

         for (AssignmentNetwork.Offer var3 : var1.offers) {
            var0.writeUtf(var3.id, 128);
            AssignmentNetwork.write(var0, var3.terms);
            var0.writeVarInt(var3.cooldown);
         }
      }, var0 -> {
         boolean var1 = var0.readBoolean();
         String var2 = var0.readUtf(256);
         boolean var3 = var0.readBoolean();
         int var4 = var0.readVarInt();
         short var5 = var0.readUnsignedByte();
         int var6 = var0.readVarInt();
         int var7 = var0.readVarInt();
         short var8 = var0.readUnsignedByte();
         short var9 = var0.readUnsignedByte();
         long var10 = var0.readVarLong();
         String var12 = var0.readUtf(128);
         Assignment var13 = var0.readBoolean() ? AssignmentNetwork.read(var0) : null;
         int var14 = var0.readVarInt();
         if (var14 >= 0 && var14 <= 16) {
            ArrayList var15 = new ArrayList();

            for (int var16 = 0; var16 < var14; var16++) {
               String var17 = var0.readUtf(128);
               Assignment var18 = AssignmentNetwork.read(var0);
               int var19 = var0.readVarInt();
               if (var19 < 0 || var19 > 24000) {
                  throw new IllegalArgumentException("Invalid assignment cooldown");
               }

               var15.add(new AssignmentNetwork.Offer(var17, var18, var19));
            }

            return new AssignmentNetwork.Snapshot(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var12, var13, var15);
         } else {
            throw new IllegalArgumentException("Too many assignment offers");
         }
      });

      public Snapshot(
         boolean open,
         String dimension,
         boolean eligible,
         int xp,
         int skills,
         int tokens,
         int completed,
         int state,
         int count,
         long remaining,
         String id,
         Assignment terms,
         List<AssignmentNetwork.Offer> offers
      ) {
         offers = List.copyOf(offers);
         if (offers.size() <= 16
            && state >= 0
            && state <= 3
            && xp >= 0
            && xp <= 1000000
            && skills >= 0
            && skills <= 7
            && tokens >= 0
            && completed >= 0
            && count >= 0
            && count <= 10
            && remaining >= 0L
            && remaining <= 72000L) {
            this.open = open;
            this.dimension = dimension;
            this.eligible = eligible;
            this.xp = xp;
            this.skills = skills;
            this.tokens = tokens;
            this.completed = completed;
            this.state = state;
            this.count = count;
            this.remaining = remaining;
            this.id = id;
            this.terms = terms;
            this.offers = offers;
         } else {
            throw new IllegalArgumentException("Invalid assignment snapshot");
         }
      }

      public int points() {
         return this.xp / 100 - Integer.bitCount(this.skills);
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
