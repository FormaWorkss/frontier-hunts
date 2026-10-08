package com.formaworks.frontierhunts.landscape.mapsync;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class MapSyncServer {
   private static final int WINDOW = 4;
   private static final int BATCH_RAW = 163840;
   private static final int BATCH_CHUNKS = 192;
   private static final int BYTES_PER_SECOND = 655360;
   private static final ConcurrentHashMap<UUID, MapSyncServer.Session> SESSIONS = new ConcurrentHashMap<>();
   private static final Map<ResourceKey<Level>, MapSyncServer.DimFiles> DIMS = new ConcurrentHashMap<>();
   private static volatile Thread worker;
   private static volatile int generation;
   private static final Object WAKE = new Object();

   private MapSyncServer() {
   }

   private static void hello(MapSyncPayloads.Hello var0, IPayloadContext var1) {
      var1.enqueueWork(
         () -> {
            if (var1.player() instanceof ServerPlayer var2) {
               MinecraftServer var10 = var2.getServer();
               if (var10 != null) {
                  ResourceLocation var4 = ResourceLocation.tryParse(var0.dimension());
                  if (var4 != null) {
                     ResourceKey var5 = ResourceKey.create(Registries.DIMENSION, var4);
                     ServerLevel var6 = var10.getLevel(var5);
                     if (var6 != null && var2.level() == var6) {
                        MapSyncServer.Session var7 = SESSIONS.get(var2.getUUID());
                        if (var7 != null) {
                           var7.closed = true;
                        }

                        MapSyncServer.Session var8 = new MapSyncServer.Session(var2.getUUID(), var5, var0.nonce());

                        for (int var9 = 0; var9 < var0.regions().length; var9++) {
                           var8.have.put(var0.regions()[var9], var0.versions()[var9]);
                        }

                        var8.regionX = var2.getBlockX() >> 9;
                        var8.regionZ = var2.getBlockZ() >> 9;
                        DIMS.computeIfAbsent(
                           var5,
                           var2x -> new MapSyncServer.DimFiles(
                                 DimensionType.getStorageFolder(var2x, var10.getWorldPath(LevelResource.ROOT)).resolve("region"),
                                 var6.getMinBuildHeight(),
                                 var6.holderLookup(Registries.BLOCK),
                                 MapColumns.biomeIds(var6.registryAccess().registryOrThrow(Registries.BIOME))
                              )
                        );
                        SESSIONS.put(var2.getUUID(), var8);
                        ensureWorker();
                        wake();
                     }
                  }
               }
            }
         }
      );
   }

   private static void ack(MapSyncPayloads.Ack var0, IPayloadContext var1) {
      var1.enqueueWork(() -> {
         MapSyncServer.Session var2 = SESSIONS.get(var1.player().getUUID());
         if (var2 != null && var2.nonce == var0.nonce()) {
            int var3 = Math.max(0, var0.batches());
            var2.inflight.updateAndGet(var1xx -> Math.max(0, var1xx - var3));
            wake();
         }
      });
   }

   private static void wake() {
      synchronized (WAKE) {
         WAKE.notifyAll();
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (!SESSIONS.isEmpty()) {
         MinecraftServer var1 = var0.getServer();
         boolean var2 = var1.getTickCount() % 40 == 0;

         for (MapSyncServer.Session var4 : SESSIONS.values()) {
            ServerPlayer var5 = var1.getPlayerList().getPlayer(var4.player);
            if (var5 != null && !var4.closed && var5.level().dimension() == var4.levelKey) {
               if (var2) {
                  var4.regionX = var5.getBlockX() >> 9;
                  var4.regionZ = var5.getBlockZ() >> 9;
               }

               long var6 = System.currentTimeMillis() / 1000L;
               if (var6 != var4.second) {
                  var4.second = var6;
                  var4.secondBytes = 0L;
               }

               boolean var8;
               for (var8 = false; var4.inflight.get() < 4 && var4.secondBytes < 655360L; var8 = true) {
                  MapSyncPayloads.Batch var9 = var4.outbox.poll();
                  if (var9 == null) {
                     break;
                  }

                  var4.secondBytes = var4.secondBytes + (long)var9.data().length;
                  var4.inflight.incrementAndGet();
                  PacketDistributor.sendToPlayer(var5, var9, new CustomPacketPayload[0]);
               }

               if (var8) {
                  wake();
               }
            } else {
               SESSIONS.remove(var4.player, var4);
               var4.closed = true;
            }
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      MapSyncServer.Session var1 = SESSIONS.remove(var0.getEntity().getUUID());
      if (var1 != null) {
         var1.closed = true;
      }
   }

   @SubscribeEvent
   public static void stopping(ServerStoppingEvent var0) {
      generation++;

      for (MapSyncServer.Session var2 : SESSIONS.values()) {
         var2.closed = true;
      }

      SESSIONS.clear();
      DIMS.clear();
      wake();
      Thread var4 = worker;
      worker = null;
      if (var4 != null) {
         try {
            var4.join(2000L);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private static synchronized void ensureWorker() {
      if (worker == null || !worker.isAlive()) {
         int var0 = ++generation;
         Thread var1 = new Thread(() -> work(var0), "FrontierHunts map preload");
         var1.setDaemon(true);
         var1.setPriority(1);
         worker = var1;
         var1.start();
      }
   }

   private static void work(int var0) {
      while (generation == var0) {
         boolean var1 = false;

         try {
            for (MapSyncServer.Session var3 : SESSIONS.values()) {
               if (generation != var0) {
                  return;
               }

               if (!var3.closed && var3.outbox.size() + var3.inflight.get() < 6) {
                  MapSyncServer.DimFiles var4 = DIMS.get(var3.levelKey);
                  if (var4 != null && step(var3, var4)) {
                     var1 = true;
                  }
               }
            }
         } catch (Throwable var9) {
            MapSyncPayloads.LOG.warn("Frontier map preload: worker error", var9);

            try {
               Thread.sleep(5000L);
            } catch (InterruptedException var8) {
               return;
            }
         }

         if (!var1) {
            synchronized (WAKE) {
               try {
                  WAKE.wait(1000L);
               } catch (InterruptedException var6) {
                  return;
               }
            }
         }
      }
   }

   private static boolean step(MapSyncServer.Session var0, MapSyncServer.DimFiles var1) {
      if (var0.cursor == null) {
         long var2 = System.currentTimeMillis();
         if (var2 < var0.idleUntil) {
            return false;
         }

         var0.cursor = next(var0, var1);
         if (var0.cursor == null) {
            var0.idleUntil = var2 + 4000L;
            return false;
         }
      }

      MapSyncServer.Cursor var13 = var0.cursor;
      MapColumns.Out var3 = new MapColumns.Out(172032);
      int var4 = 0;

      try (RandomAccessFile var5 = new RandomAccessFile(var13.file.toFile(), "r")) {
         while (var13.next < var13.todo.length && var3.size() < 163840 && var4 < 192) {
            if (var0.closed) {
               return false;
            }

            int var6 = var13.todo[var13.next++];
            byte[] var7 = null;

            try {
               CompoundTag var8 = readChunk(var5, var13.offsets[var6]);
               if (var8 != null && var8.getInt("xPos") == (var13.rx << 5) + (var6 & 31) && var8.getInt("zPos") == (var13.rz << 5) + (var6 >> 5)) {
                  var7 = MapColumns.encode(var8, var1.blocks, var1.biomes, var1.minY, var1.stateCache);
               }
            } catch (Exception var10) {
               var7 = null;
            }

            if (var7 != null) {
               var3.varint(var6);
               var3.varint(var7.length);
               var3.bytes(var7);
               var4++;
            }
         }
      } catch (IOException var12) {
         MapSyncPayloads.LOG.debug("Frontier map preload: cannot read {}", var13.file, var12);
         var13.next = var13.todo.length;
      }

      boolean var14 = var13.next >= var13.todo.length;
      var0.outbox.add(new MapSyncPayloads.Batch(var0.dim, var0.nonce, var13.rx, var13.rz, var13.version, var14, var4, MapColumns.deflate(var3.toByteArray())));
      if (var14) {
         var0.have.put(var13.key, var13.version);
         var0.cursor = null;
      }

      return true;
   }

   private static void list(MapSyncServer.DimFiles var0) {
      long var1 = System.currentTimeMillis();
      if (var1 - var0.listed >= 30000L || var0.headers.isEmpty()) {
         var0.listed = var1;
         if (Files.isDirectory(var0.dir)) {
            try (DirectoryStream var3 = Files.newDirectoryStream(var0.dir, "r.*.*.mca")) {
               HashSet var4 = new HashSet();

               for (Path var6 : var3) {
                  String[] var7 = var6.getFileName().toString().split("\\.");
                  if (var7.length == 4) {
                     try {
                        long var8 = MapSyncPayloads.key(Integer.parseInt(var7[1]), Integer.parseInt(var7[2]));
                        var4.add(var8);
                        var0.headers.computeIfAbsent(var8, var1x -> new MapSyncServer.Header(var6));
                     } catch (NumberFormatException var11) {
                     }
                  }
               }

               var0.headers.keySet().retainAll(var4);
            } catch (IOException var13) {
               MapSyncPayloads.LOG.debug("Frontier map preload: cannot list {}", var0.dir, var13);
            }
         }
      }
   }

   private static void refresh(MapSyncServer.Header var0) {
      long var1 = System.currentTimeMillis();
      if (var1 - var0.checked >= 15000L || var0.mtime < 0L) {
         var0.checked = var1;

         try {
            long var3 = Files.getLastModifiedTime(var0.file).toMillis();
            long var5 = Files.size(var0.file);
            if (var3 == var0.mtime && var5 == var0.length) {
               return;
            }

            var0.mtime = var3;
            var0.length = var5;
            int[] var7 = new int[1024];
            int[] var8 = new int[1024];
            var0.newest = readHeader(var0.file, var7, var8) ? newest(var7, var8) : 0;
         } catch (IOException var9) {
            var0.newest = 0;
         }
      }
   }

   private static int newest(int[] var0, int[] var1) {
      int var2 = 0;

      for (int var3 = 0; var3 < 1024; var3++) {
         if (var0[var3] != 0) {
            var2 = Math.max(var2, var1[var3]);
         }
      }

      return var2;
   }

   private static boolean readHeader(Path var0, int[] var1, int[] var2) throws IOException {
      if (Files.size(var0) < 8192L) {
         return false;
      } else {
         byte[] var3 = new byte[8192];

         try (RandomAccessFile var4 = new RandomAccessFile(var0.toFile(), "r")) {
            var4.readFully(var3);
         }

         DataInputStream var9 = new DataInputStream(new ByteArrayInputStream(var3));

         for (int var5 = 0; var5 < 1024; var5++) {
            var1[var5] = var9.readInt();
         }

         for (int var10 = 0; var10 < 1024; var10++) {
            var2[var10] = var9.readInt();
         }

         return true;
      }
   }

   private static MapSyncServer.Cursor next(MapSyncServer.Session var0, MapSyncServer.DimFiles var1) {
      list(var1);
      if (var1.headers.isEmpty()) {
         return null;
      } else {
         ArrayList var2 = new ArrayList<>(var1.headers.entrySet());
         int var3 = var0.regionX;
         int var4 = var0.regionZ;
         var2.sort(Comparator.comparingLong(var2x -> {
            long var3x = (long)(MapSyncPayloads.keyX((Long)var2x.getKey()) - var3);
            long var5x = (long)(MapSyncPayloads.keyZ((Long)var2x.getKey()) - var4);
            return var3x * var3x + var5x * var5x;
         }));
         int var5 = (int)(System.currentTimeMillis() / 1000L);
         Iterator var6 = var2.iterator();

         while (true) {
            Entry var7;
            MapSyncServer.Header var8;
            int var9;
            int[] var10;
            int[] var11;
            while (true) {
               if (!var6.hasNext()) {
                  return null;
               }

               var7 = (Entry)var6.next();
               if (var0.closed) {
                  return null;
               }

               var8 = (MapSyncServer.Header)var7.getValue();
               refresh(var8);
               var9 = var0.have.getOrDefault(var7.getKey(), 0);
               if (var8.newest > var9 && var8.newest <= var5 - 2) {
                  var10 = new int[1024];
                  var11 = new int[1024];

                  try {
                     if (!readHeader(var8.file, var10, var11)) {
                        continue;
                     }
                     break;
                  } catch (IOException var16) {
                  }
               }
            }

            int var12 = newest(var10, var11);
            int var13 = 0;
            int[] var14 = new int[1024];

            for (int var15 = 0; var15 < 1024; var15++) {
               if (var10[var15] != 0 && var11[var15] > var9) {
                  var14[var13++] = var15;
               }
            }

            if (var12 > var9) {
               return new MapSyncServer.Cursor((Long)var7.getKey(), var8.file, var12, var10, Arrays.copyOf(var14, var13));
            }
         }
      }
   }

   private static CompoundTag readChunk(RandomAccessFile var0, int var1) throws IOException {
      long var2 = (long)(var1 >>> 8) * 4096L;
      int var4 = var1 & 0xFF;
      if (var2 >= 8192L && var4 != 0 && var2 + 5L <= var0.length()) {
         var0.seek(var2);
         int var5 = var0.readInt();
         if (var5 > 1 && var5 <= var4 * 4096) {
            byte var6 = var0.readByte();
            if ((var6 & 128) != 0) {
               return null;
            } else {
               byte[] var7 = new byte[var5 - 1];
               var0.readFully(var7);
               RegionFileVersion var8 = RegionFileVersion.fromId(var6);
               if (var8 == null) {
                  return null;
               } else {
                  CompoundTag var10;
                  try (DataInputStream var9 = new DataInputStream(var8.wrap(new ByteArrayInputStream(var7)))) {
                     var10 = NbtIo.read(var9, NbtAccounter.create(67108864L));
                  }

                  return var10;
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   static final class Cursor {
      final long key;
      final int rx;
      final int rz;
      final int version;
      final Path file;
      final int[] offsets;
      final int[] todo;
      int next;

      Cursor(long var1, Path var3, int var4, int[] var5, int[] var6) {
         this.key = var1;
         this.rx = MapSyncPayloads.keyX(var1);
         this.rz = MapSyncPayloads.keyZ(var1);
         this.file = var3;
         this.version = var4;
         this.offsets = var5;
         this.todo = var6;
      }
   }

   static final class DimFiles {
      final Path dir;
      final int minY;
      final HolderGetter<Block> blocks;
      final ToIntFunction<String> biomes;
      final Map<Long, MapSyncServer.Header> headers = new HashMap<>();
      long listed;
      final Map<CompoundTag, BlockState> stateCache = new HashMap<>();

      DimFiles(Path var1, int var2, HolderGetter<Block> var3, ToIntFunction<String> var4) {
         this.dir = var1;
         this.minY = var2;
         this.blocks = var3;
         this.biomes = var4;
      }
   }

   static final class Header {
      final Path file;
      long mtime = -1L;
      long length = -1L;
      long checked;
      int newest;

      Header(Path var1) {
         this.file = var1;
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void payloads(RegisterPayloadHandlersEvent var0) {
         PayloadRegistrar var1 = var0.registrar("1").optional();
         var1.playToServer(MapSyncPayloads.Hello.TYPE, MapSyncPayloads.Hello.CODEC, MapSyncServer::hello);
         var1.playToServer(MapSyncPayloads.Ack.TYPE, MapSyncPayloads.Ack.CODEC, MapSyncServer::ack);
         var1.playToClient(MapSyncPayloads.Batch.TYPE, MapSyncPayloads.Batch.CODEC, (var0x, var1x) -> MapSyncBridge.batch(var0x, var1x));
      }
   }

   static final class Session {
      final UUID player;
      final String dim;
      final ResourceKey<Level> levelKey;
      final int nonce;
      final ConcurrentHashMap<Long, Integer> have = new ConcurrentHashMap<>();
      final ConcurrentLinkedQueue<MapSyncPayloads.Batch> outbox = new ConcurrentLinkedQueue<>();
      final AtomicInteger inflight = new AtomicInteger();
      volatile int regionX;
      volatile int regionZ;
      volatile boolean closed;
      MapSyncServer.Cursor cursor;
      long idleUntil;
      long second;
      long secondBytes;

      Session(UUID var1, ResourceKey<Level> var2, int var3) {
         this.player = var1;
         this.levelKey = var2;
         this.nonce = var3;
         this.dim = var2.location().toString();
      }
   }
}
