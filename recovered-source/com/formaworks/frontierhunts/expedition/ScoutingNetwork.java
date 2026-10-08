package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ScoutingNetwork {
   public static Consumer<ScoutingNetwork.Hub> hubReceiver = var0 -> {
   };
   public static Consumer<ScoutingNetwork.Roll> rollReceiver = var0 -> {
   };
   private static final Map<UUID, Long> REQUESTS = new HashMap<>();

   private static void writeFrames(FriendlyByteBuf var0, List<ScoutingNetwork.Frame> var1) {
      var0.writeVarInt(var1.size());

      for (ScoutingNetwork.Frame var3 : var1) {
         DeerTraits var4 = var3.traits();
         var0.writeByte(var4.species().ordinal());
         var0.writeBoolean(var4.buck());
         var0.writeVarInt(var4.ageMonths());
         var0.writeVarInt(var4.frame());
         var0.writeVarInt(var4.condition());
         var0.writeVarInt(var4.rackGenes());
         var0.writeInt(var4.seed());
         var0.writeVarInt(var4.abnormal());
         var0.writeVarInt(var4.coat());
         var0.writeVarLong(var3.at());
         var0.writeUtf(var3.date(), 64);
         var0.writeByte(var3.hour());
         var0.writeUtf(var3.over(), 24);
         var0.writeFloat(var3.distance());
         var0.writeFloat(var3.bearing());
         var0.writeFloat(var3.bodyYaw());
         var0.writeByte(var3.stance());
         var0.writeByte(var3.sky());
      }
   }

   private static List<ScoutingNetwork.Frame> readFrames(FriendlyByteBuf var0, int var1) {
      int var2 = var0.readVarInt();
      if (var2 >= 0 && var2 <= var1) {
         ArrayList var3 = new ArrayList(var2);
         GameSpecies[] var4 = GameSpecies.values();

         for (int var5 = 0; var5 < var2; var5++) {
            byte var6 = var0.readByte();
            GameSpecies var7 = var6 >= 0 && var6 < var4.length ? var4[var6] : GameSpecies.WHITETAIL;
            DeerTraits var8 = new DeerTraits(
               var7,
               var0.readBoolean(),
               var0.readVarInt(),
               var0.readVarInt(),
               var0.readVarInt(),
               var0.readVarInt(),
               var0.readInt(),
               var0.readVarInt(),
               var0.readVarInt()
            );
            var3.add(
               new ScoutingNetwork.Frame(
                  var8,
                  var0.readVarLong(),
                  var0.readUtf(64),
                  var0.readByte(),
                  var0.readUtf(24),
                  var0.readFloat(),
                  var0.readFloat(),
                  var0.readFloat(),
                  var0.readByte(),
                  var0.readByte()
               )
            );
         }

         return var3;
      } else {
         throw new IllegalArgumentException("Too many camera frames");
      }
   }

   private static String season(ServerLevel var0) {
      HuntingCalendar.Date var1 = HuntingCalendar.date(var0);
      StringBuilder var2 = new StringBuilder(var1.title());

      for (GameSpecies var6 : GameSpecies.values()) {
         Rut.Phase var7 = Rut.phase(var6, var1);
         if (var7.active()) {
            var2.append(" · ").append(var6.title).append(": ").append(var7.title.toLowerCase(Locale.ROOT));
         }
      }

      return var2.length() > 200 ? var2.substring(0, 200) : var2.toString();
   }

   static boolean owns(ServerPlayer var0, CameraRegistry.Station var1) {
      return var1.owner == null || var1.owner.equals(var0.getUUID()) || var0.hasPermissions(2);
   }

   public static void sendHub(ServerPlayer var0, BlockPos var1) {
      ServerLevel var2 = var0.serverLevel();
      CameraRegistry var3 = CameraRegistry.get(var2);
      List var4 = var3.nearby(var2, var1, 48);
      ArrayList var5 = new ArrayList(var4.size());

      for (CameraRegistry.Station var7 : var4) {
         if (owns(var0, var7)) {
            if (var2.hasChunkAt(var7.pos) && !(var2.getBlockState(var7.pos).getBlock() instanceof TrailCameraBlock)) {
               var3.remove(var2, var7.pos);
            } else {
               var3.catchUp(var2, var7);
               boolean var8 = var2.hasChunkAt(var7.pos) && var7.charge > 0;
               boolean var9 = false;
               int var10 = 0;

               for (CameraRegistry.Capture var12 : var7.roll) {
                  if (var12.traits().buck() && var12.traits().trophyScore() > var10) {
                     var10 = var12.traits().trophyScore();
                     var9 = true;
                  }
               }

               var5.add(new ScoutingNetwork.Entry(var7.pos, var7.name, var7.charge, 2400, var7.roll.size(), var7.last(), var8, var9, var10, var7.over));
            }
         }
      }

      if (var0.connection != null && var0.connection.hasChannel(ScoutingNetwork.Hub.TYPE)) {
         PacketDistributor.sendToPlayer(var0, new ScoutingNetwork.Hub(var1, season(var2), var2.getGameTime(), var5), new CustomPacketPayload[0]);
      } else {
         ExpeditionService.message(var0, var5.size() + " cameras on the network");
      }
   }

   public static void sendRoll(ServerPlayer var0, CameraRegistry.Station var1) {
      ServerLevel var2 = var0.serverLevel();
      CameraRegistry var3 = CameraRegistry.get(var2);
      var3.catchUp(var2, var1);
      ArrayList var4 = new ArrayList(var1.roll.size());

      for (int var5 = var1.roll.size() - 1; var5 >= 0; var5--) {
         var4.add(ScoutingNetwork.Frame.of(var1.roll.get(var5)));
      }

      boolean var7 = var2.hasChunkAt(var1.pos) && var1.charge > 0;
      List var6 = var7 ? liveFrames(var2, var1) : List.of();
      if (var0.connection != null && var0.connection.hasChannel(ScoutingNetwork.Roll.TYPE)) {
         if (!Skyline.valid(var1.view) && var7) {
            var1.view = Skyline.sample(var2, var1.pos, var1.facing);
            var3.touch();
         }

         PacketDistributor.sendToPlayer(
            var0, new ScoutingNetwork.Roll(var1.pos, var1.name, var1.charge, 2400, season(var2), var7, var1.view, var4, var6), new CustomPacketPayload[0]
         );
      } else {
         ExpeditionService.message(var0, var1.label() + " · battery " + var1.percent() + "% · " + var4.size() + " captures");
      }
   }

   private static List<ScoutingNetwork.Frame> liveFrames(ServerLevel var0, CameraRegistry.Station var1) {
      if (!var0.hasChunkAt(var1.pos)) {
         return List.of();
      } else {
         Direction var2 = var1.facing;
         Vec3 var3 = Vec3.atCenterOf(var1.pos).add(Vec3.atLowerCornerOf(var2.getNormal()).scale(0.35));
         Vec3 var4 = Vec3.atLowerCornerOf(var2.getNormal());
         List var5 = var0.getEntitiesOfClass(Whitetail.class, new AABB(var1.pos).inflate(18.0), var0x -> var0x.isAlive() && !var0x.downed());
         if (var5.isEmpty()) {
            return List.of();
         } else {
            var5.sort(Comparator.comparingDouble(var1x -> var1x.distanceToSqr(var3)));
            ArrayList var6 = new ArrayList();
            int var7 = Math.floorMod(var0.getDayTime() / 1000L + 6L, 24);
            String var8 = HuntingCalendar.date(var0).title();

            for (Whitetail var10 : var5) {
               if (var6.size() >= 4) {
                  break;
               }

               Vec3 var11 = var10.position().add(0.0, (double)var10.getBbHeight() * 0.55, 0.0).subtract(var3);
               double var12 = var11.length();
               if (!(var12 > 18.0) && !(var12 < 0.6) && !(var4.dot(var11.normalize()) < 0.5)) {
                  float var14 = var2.toYRot();
                  float var15 = Mth.wrapDegrees((float)(Mth.atan2(-var11.x, var11.z) * (180.0 / Math.PI)) - var14);
                  int var16 = var10.alertness() > 0.4F
                     ? 2
                     : (var10.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 0 : (var10.graze(1.0F) > 0.35F ? 1 : 3));
                  int var17 = var0.isNight() ? 2 : (var7 != 5 && var7 != 6 && var7 != 18 && var7 != 19 ? (var0.isRaining() ? 3 : 0) : 1);
                  var6.add(
                     new ScoutingNetwork.Frame(
                        var10.traits().withSpecies(var10.species()),
                        var0.getGameTime(),
                        var8,
                        var7,
                        var1.over,
                        (float)var12,
                        var15,
                        Mth.wrapDegrees(var10.yBodyRot - var14),
                        var16,
                        var17
                     )
                  );
               }
            }

            return var6;
         }
      }
   }

   private static void handle(ServerPlayer var0, ScoutingNetwork.Request var1) {
      long var2 = var0.level().getGameTime();
      Long var4 = REQUESTS.get(var0.getUUID());
      if (var4 == null || var2 < var4 || var2 - var4 >= 6L) {
         REQUESTS.put(var0.getUUID(), var2);
         if (REQUESTS.size() > 256) {
            REQUESTS.entrySet().removeIf(var2x -> var2 - var2x.getValue() > 200L);
         }

         if (var0.isAlive() && !var0.isSpectator() && var0.level() instanceof ServerLevel var5) {
            BlockPos var12 = var1.console();
            if (!(var0.blockPosition().distSqr(var12) > 144.0) && var5.mayInteract(var0, var12)) {
               if (var5.hasChunkAt(var12)) {
                  Block var7 = var5.getBlockState(var12).getBlock();
                  boolean var8 = var7 instanceof CameraHub;
                  if (var8 || var7 instanceof TrailCameraBlock) {
                     if (var1.action() == 0) {
                        if (var8) {
                           sendHub(var0, var12);
                        }
                     } else {
                        BlockPos var9 = var8 ? var1.camera() : var12;
                        CameraRegistry var10 = CameraRegistry.get(var5);
                        CameraRegistry.Station var11 = var10.find(var5, var9);
                        if (var11 != null && owns(var0, var11)) {
                           switch (var1.action()) {
                              case 2:
                                 var11.roll.clear();
                                 var10.touch();
                                 sendRoll(var0, var11);
                                 break;
                              case 4:
                                 if (var11.charge <= 0) {
                                    ExpeditionService.message(var0, var11.label() + " has a flat battery.");
                                    return;
                                 }

                                 LensView.close(var0);
                                 if (LensView.open(var0, var11) != null) {
                                    var0.closeContainer();
                                 }
                                 break;
                              default:
                                 sendRoll(var0, var11);
                           }
                        } else {
                           if (var8) {
                              sendHub(var0, var12);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("2");
      var1.playToClient(ScoutingNetwork.Hub.TYPE, ScoutingNetwork.Hub.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> hubReceiver.accept(var0x)));
      var1.playToClient(ScoutingNetwork.Roll.TYPE, ScoutingNetwork.Roll.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> rollReceiver.accept(var0x)));
      var1.playToServer(ScoutingNetwork.Request.TYPE, ScoutingNetwork.Request.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               handle(var2, var0x);
            }
         }));
   }

   private ScoutingNetwork() {
   }

   public static record Entry(
      BlockPos pos, String name, int charge, int capacity, int frames, long last, boolean live, boolean bestBuck, int bestScore, String over
   ) {
      public Entry(BlockPos pos, String name, int charge, int capacity, int frames, long last, boolean live, boolean bestBuck, int bestScore, String over) {
         name = name != null && name.length() <= 40 ? name : "";
         over = over != null && over.length() <= 24 ? over : "";
         charge = Math.clamp((long)charge, 0, 100000);
         capacity = Math.max(1, capacity);
         frames = Math.clamp((long)frames, 0, 48);
         bestScore = Math.clamp((long)bestScore, 0, 10000);
         last = Math.max(0L, last);
         this.pos = pos;
         this.name = name;
         this.charge = charge;
         this.capacity = capacity;
         this.frames = frames;
         this.last = last;
         this.live = live;
         this.bestBuck = bestBuck;
         this.bestScore = bestScore;
         this.over = over;
      }

      public String label() {
         return this.name.isEmpty() ? "Camera " + this.pos.getX() + ", " + this.pos.getZ() : this.name;
      }

      public int percent() {
         return Math.round((float)this.charge * 100.0F / (float)this.capacity);
      }
   }

   public static record Frame(DeerTraits traits, long at, String date, int hour, String over, float distance, float bearing, float bodyYaw, int stance, int sky) {
      public Frame(DeerTraits traits, long at, String date, int hour, String over, float distance, float bearing, float bodyYaw, int stance, int sky) {
         if (traits == null) {
            traits = DeerTraits.REFERENCE;
         }

         date = date != null && date.length() <= 64 ? date : "";
         over = over != null && over.length() <= 24 ? over : "";
         hour = Math.floorMod(hour, 24);
         at = Math.max(0L, at);
         distance = finite(distance, 0.8F, 24.0F);
         bearing = finite(bearing, -55.0F, 55.0F);
         bodyYaw = finite(bodyYaw, -180.0F, 180.0F);
         stance = Math.clamp((long)stance, 0, 3);
         sky = Math.clamp((long)sky, 0, 4);
         this.traits = traits;
         this.at = at;
         this.date = date;
         this.hour = hour;
         this.over = over;
         this.distance = distance;
         this.bearing = bearing;
         this.bodyYaw = bodyYaw;
         this.stance = stance;
         this.sky = sky;
      }

      private static float finite(float var0, float var1, float var2) {
         return Float.isFinite(var0) ? Math.clamp(var0, var1, var2) : var1;
      }

      public String clock() {
         return String.format(Locale.ROOT, "%02d:00", this.hour);
      }

      public static ScoutingNetwork.Frame of(CameraRegistry.Capture var0) {
         return new ScoutingNetwork.Frame(
            var0.traits(), var0.at(), var0.date(), var0.hour(), var0.over(), var0.distance(), var0.bearing(), var0.bodyYaw(), var0.stance(), var0.sky()
         );
      }
   }

   public static record Hub(BlockPos console, String season, long now, List<ScoutingNetwork.Entry> cameras) implements CustomPacketPayload {
      public static final Type<ScoutingNetwork.Hub> TYPE = new Type(FrontierHunts.id("camera_hub"));
      public static final StreamCodec<FriendlyByteBuf, ScoutingNetwork.Hub> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeBlockPos(var1.console);
            var0.writeUtf(var1.season, 200);
            var0.writeVarLong(var1.now);
            var0.writeVarInt(var1.cameras.size());

            for (ScoutingNetwork.Entry var3 : var1.cameras) {
               var0.writeBlockPos(var3.pos());
               var0.writeUtf(var3.name(), 40);
               var0.writeVarInt(var3.charge());
               var0.writeVarInt(var3.capacity());
               var0.writeVarInt(var3.frames());
               var0.writeVarLong(var3.last());
               var0.writeBoolean(var3.live());
               var0.writeBoolean(var3.bestBuck());
               var0.writeVarInt(var3.bestScore());
               var0.writeUtf(var3.over(), 24);
            }
         },
         var0 -> {
            BlockPos var1 = var0.readBlockPos();
            String var2 = var0.readUtf(200);
            long var3 = var0.readVarLong();
            int var5 = var0.readVarInt();
            if (var5 >= 0 && var5 <= 64) {
               ArrayList var6 = new ArrayList(var5);

               for (int var7 = 0; var7 < var5; var7++) {
                  var6.add(
                     new ScoutingNetwork.Entry(
                        var0.readBlockPos(),
                        var0.readUtf(40),
                        var0.readVarInt(),
                        var0.readVarInt(),
                        var0.readVarInt(),
                        var0.readVarLong(),
                        var0.readBoolean(),
                        var0.readBoolean(),
                        var0.readVarInt(),
                        var0.readUtf(24)
                     )
                  );
               }

               return new ScoutingNetwork.Hub(var1, var2, var3, var6);
            } else {
               throw new IllegalArgumentException("Too many cameras");
            }
         }
      );

      public Hub(BlockPos console, String season, long now, List<ScoutingNetwork.Entry> cameras) {
         cameras = List.copyOf(cameras);
         if (cameras.size() > 64) {
            throw new IllegalArgumentException("Too many cameras");
         } else if (season.length() > 200) {
            throw new IllegalArgumentException("Season line too long");
         } else {
            this.console = console;
            this.season = season;
            this.now = now;
            this.cameras = cameras;
         }
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Request(BlockPos console, BlockPos camera, int action) implements CustomPacketPayload {
      public static final int HUB = 0;
      public static final int OPEN = 1;
      public static final int CLEAR = 2;
      public static final int LIVE = 3;
      public static final int WATCH = 4;
      public static final Type<ScoutingNetwork.Request> TYPE = new Type(FrontierHunts.id("scouting_request"));
      public static final StreamCodec<FriendlyByteBuf, ScoutingNetwork.Request> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeBlockPos(var1.console);
         var0.writeBlockPos(var1.camera);
         var0.writeVarInt(var1.action);
      }, var0 -> new ScoutingNetwork.Request(var0.readBlockPos(), var0.readBlockPos(), var0.readVarInt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Roll(
      BlockPos pos,
      String name,
      int charge,
      int capacity,
      String season,
      boolean live,
      byte[] view,
      List<ScoutingNetwork.Frame> frames,
      List<ScoutingNetwork.Frame> now
   ) implements CustomPacketPayload {
      public static final Type<ScoutingNetwork.Roll> TYPE = new Type(FrontierHunts.id("camera_roll"));
      public static final StreamCodec<FriendlyByteBuf, ScoutingNetwork.Roll> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeBlockPos(var1.pos);
         var0.writeUtf(var1.name, 40);
         var0.writeVarInt(var1.charge);
         var0.writeVarInt(var1.capacity);
         var0.writeUtf(var1.season, 200);
         var0.writeBoolean(var1.live);
         var0.writeByteArray(var1.view);
         ScoutingNetwork.writeFrames(var0, var1.frames);
         ScoutingNetwork.writeFrames(var0, var1.now);
      }, var0 -> {
         BlockPos var1 = var0.readBlockPos();
         String var2 = var0.readUtf(40);
         int var3 = var0.readVarInt();
         int var4 = var0.readVarInt();
         String var5 = var0.readUtf(200);
         boolean var6 = var0.readBoolean();
         byte[] var7 = var0.readByteArray(256);
         List var8 = ScoutingNetwork.readFrames(var0, 48);
         List var9 = ScoutingNetwork.readFrames(var0, 8);
         return new ScoutingNetwork.Roll(var1, var2, var3, var4, var5, var6, var7, var8, var9);
      });

      public Roll(
         BlockPos pos,
         String name,
         int charge,
         int capacity,
         String season,
         boolean live,
         byte[] view,
         List<ScoutingNetwork.Frame> frames,
         List<ScoutingNetwork.Frame> now
      ) {
         frames = List.copyOf(frames);
         now = List.copyOf(now);
         if (!Skyline.valid(view)) {
            view = new byte[0];
         }

         if (name == null || name.length() > 40) {
            name = "";
         }

         if (season == null || season.length() > 200) {
            season = "";
         }

         capacity = Math.max(1, capacity);
         charge = Math.clamp((long)charge, 0, capacity);
         if (frames.size() <= 48 && now.size() <= 8) {
            this.pos = pos;
            this.name = name;
            this.charge = charge;
            this.capacity = capacity;
            this.season = season;
            this.live = live;
            this.view = view;
            this.frames = frames;
            this.now = now;
         } else {
            throw new IllegalArgumentException("Invalid camera roll");
         }
      }

      public String label() {
         return this.name.isEmpty() ? "Camera " + this.pos.getX() + ", " + this.pos.getZ() : this.name;
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
