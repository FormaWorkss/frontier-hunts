package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class LensView extends Entity {
   private UUID watcher;
   private int ticks;
   private Vec3 returnPosition;
   private float returnYaw;
   private float returnPitch;
   private boolean returnInvulnerable;
   private final Map<Integer, ServerEntity> remoteEntities = new HashMap<>();
   private final Set<ChunkPos> tickets = new HashSet<>();
   private int chunkCursor;
   private static final int RADIUS = 3;
   private static final Map<UUID, LensView> ACTIVE = new HashMap<>();
   private static final Map<UUID, LensView.ReturnStream> RETURN_STREAMS = new HashMap<>();
   private static final int LIMIT = 6000;
   private static final String RETURN = "frontier_lens_return";

   public LensView(EntityType<? extends LensView> var1, Level var2) {
      super(var1, var2);
      this.noPhysics = true;
      this.setInvisible(true);
      this.setNoGravity(true);
   }

   public static LensView open(ServerPlayer var0, CameraRegistry.Station var1) {
      RETURN_STREAMS.remove(var0.getUUID());
      ServerLevel var2 = var0.serverLevel();
      LensView var3 = (LensView)((EntityType)ExpeditionContent.LENS_VIEW.get()).create(var2);
      if (var3 == null) {
         return null;
      } else {
         var2.getChunkAt(var1.pos);
         Vec3 var4 = Vec3.atLowerCornerOf(var1.facing.getNormal());
         Vec3 var5 = Vec3.atCenterOf(var1.pos).add(var4.scale(0.38)).add(0.0, 0.1, 0.0);
         var3.moveTo(var5.x, var5.y, var5.z, var1.facing.toYRot(), 0.0F);
         var3.watcher = var0.getUUID();
         var3.returnPosition = var0.position();
         var3.returnYaw = var0.getYRot();
         var3.returnPitch = var0.getXRot();
         var3.returnInvulnerable = var0.isInvulnerable();
         CompoundTag var6 = new CompoundTag();
         var6.putDouble("x", var3.returnPosition.x);
         var6.putDouble("y", var3.returnPosition.y);
         var6.putDouble("z", var3.returnPosition.z);
         var6.putFloat("yaw", var3.returnYaw);
         var6.putFloat("pitch", var3.returnPitch);
         var6.putBoolean("invulnerable", var3.returnInvulnerable);
         var6.putString("dimension", var2.dimension().location().toString());
         var0.getPersistentData().put("frontier_lens_return", var6);
         var2.addFreshEntity(var3);
         var0.setInvulnerable(true);
         ACTIVE.put(var0.getUUID(), var3);
         var0.connection.send(new ClientboundSetChunkCacheCenterPacket(var1.pos.getX() >> 4, var1.pos.getZ() >> 4));
         var3.sendChunk(var0, new ChunkPos(var1.pos));
         PacketDistributor.sendToPlayer(
            var0, new LensView.CameraControl(true, var3.getId(), var5.x, var5.y, var5.z, var1.facing.toYRot(), 0.0F), new CustomPacketPayload[0]
         );
         return var3;
      }
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         ServerPlayer var1 = this.watcher == null ? null : this.level().getServer().getPlayerList().getPlayer(this.watcher);
         boolean var2 = var1 == null
            || !var1.isAlive()
            || var1.isSpectator()
            || var1.level() != this.level()
            || ACTIVE.get(this.watcher) != this
            || var1.position().distanceToSqr(this.returnPosition) > 9.0
            || this.ticks > 8 && var1.isShiftKeyDown()
            || ++this.ticks > 6000;
         if (!var2) {
            this.stream(var1);
         } else {
            if (var1 != null && ACTIVE.get(this.watcher) == this) {
               this.restore(var1);
            }

            this.discard();
         }
      }
   }

   public void remove(RemovalReason var1) {
      if (!this.level().isClientSide && this.watcher != null && this.level().getServer() != null) {
         ServerPlayer var2 = this.level().getServer().getPlayerList().getPlayer(this.watcher);
         if (var2 != null && ACTIVE.get(this.watcher) == this) {
            this.restore(var2);
         } else {
            ACTIVE.remove(this.watcher, this);
            this.releaseTickets();
         }
      }

      this.watcher = null;
      super.remove(var1);
   }

   public static void close(ServerPlayer var0) {
      LensView var1 = ACTIVE.get(var0.getUUID());
      if (var1 != null) {
         var1.restore(var0);
         var1.discard();
      }
   }

   private void restore(ServerPlayer var1) {
      ACTIVE.remove(var1.getUUID(), this);
      PacketDistributor.sendToPlayer(var1, new LensView.CameraControl(false, this.getId(), 0.0, 0.0, 0.0, 0.0F, 0.0F), new CustomPacketPayload[0]);
      var1.connection.send(new ClientboundSetChunkCacheCenterPacket(var1.chunkPosition().x, var1.chunkPosition().z));
      ServerLevel var2 = var1.serverLevel();
      int var3 = Math.min(16, Math.max(2, var1.getServer().getPlayerList().getViewDistance()));
      ChunkPos var4 = var1.chunkPosition();

      for (int var5 = -1; var5 <= 1; var5++) {
         for (int var6 = -1; var6 <= 1; var6++) {
            sendHomeChunk(var1, var4.x + var6, var4.z + var5);
         }
      }

      RETURN_STREAMS.put(var1.getUUID(), new LensView.ReturnStream(var4, var3));

      for (ServerEntity var8 : this.remoteEntities.values()) {
         var8.removePairing(var1);
      }

      this.remoteEntities.clear();
      this.releaseTickets();
      var1.setInvulnerable(this.returnInvulnerable);
      var1.getPersistentData().remove("frontier_lens_return");
   }

   private void releaseTickets() {
      if (this.level() instanceof ServerLevel var1) {
         for (ChunkPos var3 : this.tickets) {
            var1.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, var3, 2, var3);
         }

         this.tickets.clear();
      }
   }

   private void sendChunk(ServerPlayer var1, ChunkPos var2) {
      ServerLevel var3 = var1.serverLevel();
      if (this.tickets.add(var2)) {
         var3.getChunkSource().addRegionTicket(TicketType.UNKNOWN, var2, 2, var2);
      }

      LevelChunk var4 = var3.getChunk(var2.x, var2.z);
      var1.connection.send(new ClientboundLevelChunkWithLightPacket(var4, var3.getLightEngine(), null, null));
   }

   private static void sendHomeChunk(ServerPlayer var0, int var1, int var2) {
      ServerLevel var3 = var0.serverLevel();
      LevelChunk var4 = var3.getChunkSource().getChunkNow(var1, var2);
      if (var4 != null) {
         var0.connection.send(new ClientboundLevelChunkWithLightPacket(var4, var3.getLightEngine(), null, null));
      }
   }

   @SubscribeEvent
   public static void serverTick(Post var0) {
      Iterator var1 = RETURN_STREAMS.entrySet().iterator();

      while (var1.hasNext()) {
         Entry var2 = (Entry)var1.next();
         ServerPlayer var3 = var0.getServer().getPlayerList().getPlayer((UUID)var2.getKey());
         if (var3 != null && !ACTIVE.containsKey(var2.getKey())) {
            LensView.ReturnStream var4 = (LensView.ReturnStream)var2.getValue();
            int var5 = var4.radius * 2 + 1;
            int var6 = var5 * var5;

            for (int var7 = 0; var7 < 16 && var4.cursor < var6; var7++) {
               int var8 = var4.cursor++;
               int var9 = var8 % var5 - var4.radius;
               int var10 = var8 / var5 - var4.radius;
               sendHomeChunk(var3, var4.cx + var9, var4.cz + var10);
            }

            if (var4.cursor >= var6) {
               var1.remove();
            }
         } else {
            var1.remove();
         }
      }
   }

   private void stream(ServerPlayer var1) {
      byte var2 = 7;
      int var3 = var2 * var2;
      int var4 = this.ticks < 50 ? 2 : (this.ticks % 10 == 0 ? 1 : 0);

      for (int var5 = 0; var5 < var4; var5++) {
         int var6 = this.chunkCursor++ % var3;
         int var7 = var6 % var2 - 3;
         int var8 = var6 / var2 - 3;
         this.sendChunk(var1, new ChunkPos(this.chunkPosition().x + var7, this.chunkPosition().z + var8));
      }

      List var10 = this.level().getEntities(this, new AABB(this.blockPosition()).inflate(48.0), var1x -> var1x != var1);
      HashSet var11 = new HashSet();

      for (Entity var13 : var10) {
         var11.add(var13.getId());
         ServerEntity var9 = this.remoteEntities.get(var13.getId());
         if (var9 == null) {
            var9 = new ServerEntity(var1.serverLevel(), var13, 1, true, var1.connection::send);
            this.remoteEntities.put(var13.getId(), var9);
            var9.addPairing(var1);
         } else {
            var9.sendChanges();
         }
      }

      this.remoteEntities.entrySet().removeIf(var2x -> {
         if (var11.contains(var2x.getKey())) {
            return false;
         } else {
            var2x.getValue().removePairing(var1);
            return true;
         }
      });
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         close(var1);
         RETURN_STREAMS.remove(var1.getUUID());
      }
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         CompoundTag var6 = var1.getPersistentData();
         if (var6.contains("frontier_lens_return", 10)) {
            CompoundTag var3 = var6.getCompound("frontier_lens_return");
            ResourceKey var4 = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(var3.getString("dimension")));
            ServerLevel var5 = var1.getServer().getLevel(var4);
            if (var5 != null) {
               var1.teleportTo(var5, var3.getDouble("x"), var3.getDouble("y"), var3.getDouble("z"), var3.getFloat("yaw"), var3.getFloat("pitch"));
            }

            var1.setInvulnerable(var3.getBoolean("invulnerable"));
            var6.remove("frontier_lens_return");
         }
      }
   }

   protected void defineSynchedData(Builder var1) {
   }

   protected void readAdditionalSaveData(CompoundTag var1) {
   }

   protected void addAdditionalSaveData(CompoundTag var1) {
   }

   public boolean shouldBeSaved() {
      return false;
   }

   public boolean isPickable() {
      return false;
   }

   public boolean isAttackable() {
      return false;
   }

   public boolean canBeCollidedWith() {
      return false;
   }

   public boolean displayFireAnimation() {
      return false;
   }

   public boolean shouldRender(double var1, double var3, double var5) {
      return false;
   }

   public static record CameraControl(boolean open, int id, double x, double y, double z, float yaw, float pitch) implements CustomPacketPayload {
      public static final Type<LensView.CameraControl> TYPE = new Type(FrontierHunts.id("lens_camera_control"));
      public static final StreamCodec<RegistryFriendlyByteBuf, LensView.CameraControl> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeBoolean(var1.open);
            var0.writeVarInt(var1.id);
            var0.writeDouble(var1.x);
            var0.writeDouble(var1.y);
            var0.writeDouble(var1.z);
            var0.writeFloat(var1.yaw);
            var0.writeFloat(var1.pitch);
         },
         var0 -> new LensView.CameraControl(
               var0.readBoolean(), var0.readVarInt(), var0.readDouble(), var0.readDouble(), var0.readDouble(), var0.readFloat(), var0.readFloat()
            )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record CloseRequest() implements CustomPacketPayload {
      public static final Type<LensView.CloseRequest> TYPE = new Type(FrontierHunts.id("lens_camera_close"));
      public static final StreamCodec<FriendlyByteBuf, LensView.CloseRequest> CODEC = StreamCodec.of((var0, var1) -> {
      }, var0 -> new LensView.CloseRequest());

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void payload(RegisterPayloadHandlersEvent var0) {
         var0.registrar("1")
            .playToClient(LensView.CameraControl.TYPE, LensView.CameraControl.CODEC, (var0x, var1) -> var1.enqueueWork(() -> LensViewClient.control(var0x)));
         var0.registrar("1").playToServer(LensView.CloseRequest.TYPE, LensView.CloseRequest.CODEC, (var0x, var1) -> var1.enqueueWork(() -> {
               if (var1.player() instanceof ServerPlayer var1x) {
                  LensView.close(var1x);
               }
            }));
      }
   }

   private static final class ReturnStream {
      final int cx;
      final int cz;
      final int radius;
      int cursor;

      ReturnStream(ChunkPos var1, int var2) {
         this.cx = var1.x;
         this.cz = var1.z;
         this.radius = var2;
      }
   }
}
