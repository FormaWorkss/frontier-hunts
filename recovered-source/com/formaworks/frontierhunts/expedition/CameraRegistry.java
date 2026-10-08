package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class CameraRegistry extends SavedData {
   public static final int ROLL = 48;
   public static final int CAPACITY = 2400;
   private static final long MAX_GAP = 720000L;
   private static final int MAX_SLOTS = 720;
   public static final byte WALKING = 0;
   public static final byte FEEDING = 1;
   public static final byte ALERT = 2;
   public static final byte STANDING = 3;
   public static final byte DAY = 0;
   public static final byte GOLDEN = 1;
   public static final byte NIGHT = 2;
   public static final byte OVERCAST = 3;
   public static final byte SNOW = 4;
   private final Map<Long, CameraRegistry.Station> stations = new LinkedHashMap<>();

   public static CameraRegistry get(ServerLevel var0) {
      return (CameraRegistry)var0.getServer()
         .overworld()
         .getDataStorage()
         .computeIfAbsent(new Factory<CameraRegistry>(CameraRegistry::new, CameraRegistry::load, null), "frontierhunts_cameras");
   }

   private static long key(String var0, BlockPos var1) {
      return var1.asLong() * 31L + (long)var0.hashCode();
   }

   private static String dim(Level var0) {
      return var0.dimension().location().toString();
   }

   public CameraRegistry.Station find(Level var1, BlockPos var2) {
      return this.stations.get(key(dim(var1), var2));
   }

   public List<CameraRegistry.Station> nearby(ServerLevel var1, BlockPos var2, int var3) {
      String var4 = dim(var1);
      ArrayList<CameraRegistry.Station> var5 = new ArrayList<>();

      for (CameraRegistry.Station var7 : this.stations.values()) {
         if (var7.dimension.equals(var4)) {
            var5.add(var7);
         }
      }

      var5.sort(Comparator.comparingDouble(var1x -> var1x.pos.distSqr(var2)));
      return var5.size() <= var3 ? var5 : new ArrayList<>(var5.subList(0, var3));
   }

   public CameraRegistry.Station place(ServerLevel var1, BlockPos var2, Direction var3, UUID var4, int var5, String var6) {
      boolean var7 = var1.hasChunkAt(var2) && ((Biome)var1.getBiome(var2).value()).coldEnoughToSnow(var2);
      long var8 = key(dim(var1), var2);
      CameraRegistry.Station var10 = this.stations.get(var8);
      if (var10 == null) {
         var10 = new CameraRegistry.Station();
         var10.pos = var2.immutable();
         var10.dimension = dim(var1);
         var10.seed = var1.getRandom().nextLong();
         var10.placed = var1.getGameTime();
         this.stations.put(var8, var10);

         while (this.stations.size() > 4096) {
            long var11 = -1L;
            long var13 = Long.MAX_VALUE;

            for (Entry var16 : this.stations.entrySet()) {
               if (((CameraRegistry.Station)var16.getValue()).caught < var13) {
                  var13 = ((CameraRegistry.Station)var16.getValue()).caught;
                  var11 = (Long)var16.getKey();
               }
            }

            if (var11 == -1L) {
               break;
            }

            this.stations.remove(var11);
         }
      }

      if (!Skyline.valid(var10.view) || var10.facing != var3) {
         var10.view = Skyline.sample(var1, var2, var3);
      }

      var10.facing = var3;
      var10.owner = var4;
      if (var10.name.length() > 40) {
         var10.name = "";
      }

      var10.charge = Math.clamp((long)var5, 0, 2400);
      var10.over = var6;
      var10.snowy = var7;
      var10.caught = var1.getGameTime();
      this.setDirty();
      com.formaworks.frontierhunts.camload.CameraChunkLoader.changed(); // [camload] a new camera takes its area on the next tick
      return var10;
   }

   public CameraRegistry.Station remove(Level var1, BlockPos var2) {
      CameraRegistry.Station var3 = this.stations.remove(key(dim(var1), var2));
      if (var3 != null) {
         this.setDirty();
         com.formaworks.frontierhunts.camload.CameraChunkLoader.changed(); // [camload] release its chunks on the next tick
      }

      return var3;
   }

   /** [camload] every camera in every dimension (live view: do not modify while iterating). */
   public java.util.Collection<CameraRegistry.Station> all() {
      return java.util.Collections.unmodifiableCollection(this.stations.values());
   }

   public void touch() {
      this.setDirty();
   }

   public void record(CameraRegistry.Station var1, CameraRegistry.Capture var2) {
      var1.roll.add(var2);

      while (var1.roll.size() > 48) {
         var1.roll.remove(0);
      }

      this.setDirty();
   }

   // [trailcam] A trail camera only records what really walked past while its chunk was simulated. The old catch-up
   // invented frames from a seeded herd for the time the camera was unloaded; it now only drains the battery.
   public void catchUp(ServerLevel var1, CameraRegistry.Station var2) {
      long var3 = var1.getGameTime();
      if (var2.caught < var3) {
         long var5 = Math.max(var2.caught, var3 - 720000L);
         long var7 = var3 - var5;
         var2.caught = var3;
         this.setDirty();
         var2.charge = Math.max(0, var2.charge - (int)(var7 / 400L));
      }
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      ListTag var3 = new ListTag();

      for (CameraRegistry.Station var5 : this.stations.values()) {
         CompoundTag var6 = new CompoundTag();
         var6.putLong("pos", var5.pos.asLong());
         var6.putString("dimension", var5.dimension);
         var6.putByte("facing", (byte)var5.facing.get2DDataValue());
         var6.putString("name", var5.name);
         if (var5.owner != null) {
            var6.putUUID("owner", var5.owner);
         }

         var6.putInt("charge", var5.charge);
         var6.putLong("seed", var5.seed);
         var6.putString("over", var5.over);
         var6.putBoolean("snowy", var5.snowy);
         if (Skyline.valid(var5.view)) {
            var6.putByteArray("view", var5.view);
         }

         var6.putLong("placed", var5.placed);
         var6.putLong("caught", var5.caught);
         var6.putInt("shots", var5.shots); // [trailcam]
         var6.put("roll", saveRoll(var5.roll));
         var3.add(var6);
      }

      var1.put("cameras", var3);
      return var1;
   }

   public static ListTag saveRoll(List<CameraRegistry.Capture> var0) {
      ListTag var1 = new ListTag();

      for (CameraRegistry.Capture var3 : var0) {
         CompoundTag var4 = new CompoundTag();
         var4.put("traits", var3.traits().save());
         var4.putLong("at", var3.at());
         var4.putString("date", var3.date());
         var4.putInt("hour", var3.hour());
         var4.putString("over", var3.over());
         var4.putFloat("distance", var3.distance());
         var4.putFloat("bearing", var3.bearing());
         var4.putFloat("body", var3.bodyYaw());
         var4.putByte("stance", var3.stance());
         var4.putByte("sky", var3.sky());
         if (var3.scene() != null) {
            var4.put("scene", var3.scene()); // [trailcam] everything the client needs to develop a real photo
         }

         var1.add(var4);
      }

      return var1;
   }

   public static void loadRoll(ListTag var0, List<CameraRegistry.Capture> var1) {
      for (int var2 = 0; var2 < var0.size() && var1.size() < 48; var2++) {
         CompoundTag var3 = var0.getCompound(var2);
         // [trailcam] frames recorded before real photos (no scene, possibly invented by the old catch-up) are dropped
         if (!var3.contains("scene", 10)) {
            continue;
         }

         var1.add(
            new CameraRegistry.Capture(
               DeerTraits.load(var3.getCompound("traits")),
               var3.getLong("at"),
               var3.getString("date"),
               var3.getInt("hour"),
               var3.getString("over"),
               var3.getFloat("distance"),
               var3.getFloat("bearing"),
               var3.getFloat("body"),
               var3.getByte("stance"),
               var3.getByte("sky"),
               var3.getCompound("scene")
            )
         );
      }
   }

   /** [trailcam] highest frame number on a roll, so a camera carried to a new tree keeps counting up. */
   static int maxShot(List<CameraRegistry.Capture> var0) {
      int var1 = 0;
      for (CameraRegistry.Capture var3 : var0) {
         if (var3.scene() != null) {
            var1 = Math.max(var1, var3.scene().getInt("n"));
         }
      }

      return var1;
   }

   public static CameraRegistry load(CompoundTag var0, Provider var1) {
      CameraRegistry var2 = new CameraRegistry();

      for (Tag var4 : var0.getList("cameras", 10)) {
         CompoundTag var5 = (CompoundTag)var4;
         CameraRegistry.Station var6 = new CameraRegistry.Station();
         var6.pos = BlockPos.of(var5.getLong("pos"));
         var6.dimension = var5.getString("dimension").isEmpty() ? "minecraft:overworld" : var5.getString("dimension");
         if (ResourceLocation.tryParse(var6.dimension) != null) {
            var6.facing = Direction.from2DDataValue(var5.getByte("facing"));
            var6.name = var5.getString("name").length() > 40 ? "" : var5.getString("name");
            var6.owner = var5.hasUUID("owner") ? var5.getUUID("owner") : null;
            var6.charge = Math.clamp((long)var5.getInt("charge"), 0, 2400);
            var6.seed = var5.getLong("seed");
            var6.over = var5.getString("over").length() > 24 ? "open ground" : var5.getString("over");
            var6.snowy = var5.getBoolean("snowy");
            byte[] var7 = var5.getByteArray("view");
            var6.view = Skyline.valid(var7) ? var7 : new byte[0];
            var6.placed = Math.max(0L, var5.getLong("placed"));
            var6.caught = Math.max(0L, var5.getLong("caught"));
            loadRoll(var5.getList("roll", 10), var6.roll);
            var6.shots = Math.max(var5.getInt("shots"), maxShot(var6.roll)); // [trailcam]
            var2.stations.put(key(var6.dimension, var6.pos), var6);
         }
      }

      return var2;
   }

   public static ResourceKey<Level> dimensionKey(String var0) {
      ResourceLocation var1 = ResourceLocation.tryParse(var0);
      return var1 == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, var1);
   }

   public static record Capture(
      DeerTraits traits, long at, String date, int hour, String over, float distance, float bearing, float bodyYaw, byte stance, byte sky, CompoundTag scene
   ) {
      public Capture(
         DeerTraits traits, long at, String date, int hour, String over, float distance, float bearing, float bodyYaw, byte stance, byte sky, CompoundTag scene
      ) {
         if (traits == null) {
            traits = DeerTraits.REFERENCE;
         }

         date = date != null && date.length() <= 64 ? date : "";
         over = over != null && over.length() <= 24 ? over : "";
         hour = Math.floorMod(hour, 24);
         at = Math.max(0L, at);
         distance = clamp(distance, 0.8F, 24.0F);
         bearing = clamp(bearing, -55.0F, 55.0F);
         bodyYaw = clamp(bodyYaw, -180.0F, 180.0F);
         stance = (byte)Math.clamp((long)stance, 0, 3);
         sky = (byte)Math.clamp((long)sky, 0, 4);
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
         this.scene = scene;
      }

      public Capture(DeerTraits traits, long at, String date, int hour, String over, float distance, float bearing, float bodyYaw, byte stance, byte sky) {
         this(traits, at, date, hour, over, distance, bearing, bodyYaw, stance, sky, null);
      }

      private static float clamp(float var0, float var1, float var2) {
         return Float.isFinite(var0) ? Math.clamp(var0, var1, var2) : var1;
      }

      public String clock() {
         return String.format(Locale.ROOT, "%02d:00", this.hour);
      }

      /** [trailcam] per-camera frame number of a real photo, 0 for data-only frames. */
      public int shot() {
         return this.scene == null ? 0 : this.scene.getInt("n");
      }
   }

   public static final class Station {
      public BlockPos pos = BlockPos.ZERO;
      public String dimension = "minecraft:overworld";
      public Direction facing = Direction.NORTH;
      public String name = "";
      public UUID owner;
      public int charge = 2400;
      public long seed;
      public String over = "open ground";
      public boolean snowy;
      public byte[] view = new byte[0];
      public long placed;
      public long caught;
      public int shots; // [trailcam] frame counter burned into each photo
      public final List<CameraRegistry.Capture> roll = new ArrayList<>();

      public String label() {
         return this.name.isEmpty() ? "Camera " + this.pos.getX() + ", " + this.pos.getZ() : this.name;
      }

      public int percent() {
         return Math.round((float)this.charge * 100.0F / 2400.0F);
      }

      public long last() {
         return this.roll.isEmpty() ? 0L : this.roll.get(this.roll.size() - 1).at();
      }
   }
}
