package com.formaworks.frontierhunts.landscape.tent;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.camp.CampingTent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   bus = Bus.MOD
)
public final class TentContent {
   public static final String[] DESIGNS = new String[]{"backpacker_dome_tent", "hunters_canvas_tent", "solo_ridge_tent"};
   private static final Map<String, DeferredHolder<Block, CompactTent>> BLOCKS = new LinkedHashMap<>();
   private static final Map<String, DeferredHolder<Item, CompactTent.Kit>> ITEMS = new LinkedHashMap<>();
   private static final Map<UUID, TentContent.Slept> SLEEPING = new ConcurrentHashMap<>();
   private static final Map<UUID, TentContent.Slept> STAND = new ConcurrentHashMap<>();
   /** Players asleep in a bed already known not to be inside a camping tent (skips the per-tick search). */
   private static final java.util.Set<UUID> NOT_IN_TENT = ConcurrentHashMap.newKeySet();
   /** Small lifts tried when a groundsheet or mat sits on the tent floor. */
   private static final double[] FLOOR_LIFTS = new double[]{0.0, 0.0625, 0.125, 0.1875};
   private static final Pose[] POSES = new Pose[]{Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING};

   private TentContent() {
   }

   public static CompactTent block(String var0) {
      DeferredHolder<Block, CompactTent> var1 = BLOCKS.get(var0);
      return var1 == null ? null : var1.get();
   }

   public static Item kit(String var0) {
      return ITEMS.get(var0).get();
   }

   @SubscribeEvent
   public static void register(RegisterEvent var0) {
      for (String var4 : DESIGNS) {
         var0.register(
            Registries.BLOCK,
            FrontierHunts.id(var4),
            () -> new CompactTent(var4, Properties.of().strength(0.8F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.BLOCK))
         );
         var0.register(Registries.ITEM, FrontierHunts.id(var4), () -> new CompactTent.Kit(var4, new net.minecraft.world.item.Item.Properties()));
      }
   }

   /** Called by {@link CompactTent} once the player is lying in its bed cell. */
   static void sleptIn(ServerPlayer var0, CompactTent var1, BlockPos var2, Direction var3) {
      BlockPos bed = var2.offset(var1.offset(var1.shape.bed(), var3));
      boolean wasOpen = compactOpen(var0.level(), var1, var2, var3);
      if (wasOpen && !compactDoorwayBusy(var0.level(), var1, var2, var3, var0)) {
         setCompactDoor(var0.level(), var1, var2, var3, false);
      } else {
         wasOpen = false;
      }

      SLEEPING.put(var0.getUUID(), new TentContent.Slept(var1, var2, var3, bed, wasOpen));
   }

   /** Finds the camping tent whose three-by-three floor holds this bed block, if any. */
   public static BlockPos campingOrigin(Level level, BlockPos bed) {
      for (BlockPos p : BlockPos.betweenClosed(bed.offset(-3, -2, -3), bed.offset(3, 2, 3))) {
         BlockState st = level.getBlockState(p);
         if (st.getBlock() instanceof CampingTent) {
            BlockPos origin = CampingTent.origin(p, st);
            if (Math.abs(bed.getX() - origin.getX()) <= 1
               && Math.abs(bed.getZ() - origin.getZ()) <= 1
               && bed.getY() >= origin.getY()
               && bed.getY() <= origin.getY() + 1) {
               return origin.immutable();
            }
         }
      }

      return null;
   }

   private static boolean compactOpen(Level level, CompactTent tent, BlockPos base, Direction facing) {
      BlockState st = level.getBlockState(base.offset(tent.offset(0, facing)));
      return st.is(tent) && st.getValue(CompactTent.OPEN);
   }

   private static boolean compactDoorwayBusy(Level level, CompactTent tent, BlockPos base, Direction facing, LivingEntity sleeper) {
      for (int part : tent.shape.doorParts()) {
         BlockPos cell = base.offset(tent.offset(part, facing));
         VoxelShape closed = tent.shape.shapes()[part][0][facing.get2DDataValue()];
         for (AABB box : closed.toAabbs()) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.move(cell))) {
               if (e != sleeper) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static void setCompactDoor(Level level, CompactTent tent, BlockPos base, Direction facing, boolean open) {
      for (int i = 0; i < tent.shape.cells().size(); i++) {
         BlockPos cell = base.offset(tent.offset(i, facing));
         BlockState st = level.getBlockState(cell);
         if (st.is(tent) && st.getValue(CompactTent.OPEN) != open) {
            level.setBlock(cell, st.setValue(CompactTent.OPEN, open), 18);
         }
      }
   }

   private static CampingTent.Anchor anchor(Level level, BlockPos origin) {
      for (Direction d : Direction.Plane.HORIZONTAL) {
         if (level.getBlockEntity(CampingTent.anchor(origin, d)) instanceof CampingTent.Anchor a
            && a.getBlockState().getBlock() instanceof CampingTent
            && a.getBlockState().getValue(CampingTent.FACING) == d) {
            return a;
         }
      }

      return null;
   }

   private static void setCampingDoor(ServerLevel level, CampingTent.Anchor a, boolean open) {
      if (a.getBlockState().getValue(CampingTent.OPEN) == open && a.doorUntil == 0L) {
         return;
      }

      if (a.doorUntil > level.getGameTime()) {
         return;
      }

      a.targetOpen = open;
      a.doorAt = level.getGameTime();
      a.doorUntil = a.doorAt + 20L;
      a.sync();
      level.scheduleTick(a.getBlockPos(), a.getBlockState().getBlock(), 20);
   }

   /** Reopens the door zipped at bedtime, unless someone else is still asleep in the same tent. */
   private static void reopen(Level level, TentContent.Slept s) {
      if (!s.reopen() || level.isClientSide) {
         return;
      }

      for (TentContent.Slept other : SLEEPING.values()) {
         if (other != s && other.base().equals(s.base()) && other.tent() == s.tent()) {
            return;
         }
      }

      if (s.tent() != null) {
         BlockState st = level.getBlockState(s.bed());
         if (st.is(s.tent())) {
            setCompactDoor(level, s.tent(), s.base(), s.facing(), true);
         }
      } else if (level instanceof ServerLevel server) {
         CampingTent.Anchor a = anchor(level, s.campOrigin());
         if (a != null) {
            setCampingDoor(server, a, true);
         }
      }
   }

   static {
      for (String var3 : DESIGNS) {
         BLOCKS.put(var3, DeferredHolder.create(Registries.BLOCK, FrontierHunts.id(var3)));
         ITEMS.put(var3, DeferredHolder.create(Registries.ITEM, FrontierHunts.id(var3)));
      }
   }

   /**
    * One night in a shelter. For compact tents {@code tent} is set; for a bed standing inside a camping tent
    * {@code campOrigin} is set instead.
    */
   private record Slept(CompactTent tent, BlockPos base, Direction facing, BlockPos bed, boolean reopen, BlockPos campOrigin) {
      Slept(CompactTent tent, BlockPos base, Direction facing, BlockPos bed, boolean reopen) {
         this(tent, base, facing, bed, reopen, null);
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts"
   )
   public static final class Wake {
      @SubscribeEvent
      public static void wake(PlayerWakeUpEvent var0) {
         if (var0.getEntity() instanceof ServerPlayer var1) {
            TentContent.Slept var3 = TentContent.SLEEPING.remove(var1.getUUID());
            if (var3 != null) {
               TentContent.STAND.put(var1.getUUID(), var3);
            }
         }
      }

      @SubscribeEvent
      public static void logout(PlayerLoggedOutEvent var0) {
         UUID id = var0.getEntity().getUUID();
         TentContent.NOT_IN_TENT.remove(id);
         for (TentContent.Slept s : new TentContent.Slept[]{TentContent.SLEEPING.remove(id), TentContent.STAND.remove(id)}) {
            if (s != null) {
               TentContent.reopen(var0.getEntity().level(), s);
            }
         }
      }

      @SubscribeEvent
      public static void tick(Post var0) {
         if (!(var0.getEntity() instanceof ServerPlayer var1)) {
            return;
         }

         if (var1.isSleeping()) {
            if (!TentContent.SLEEPING.containsKey(var1.getUUID()) && !TentContent.NOT_IN_TENT.contains(var1.getUUID())) {
               trackCampingBed(var1);
            }

            return;
         }

         TentContent.NOT_IN_TENT.remove(var1.getUUID());

         if (TentContent.STAND.isEmpty()) {
            return;
         }

         TentContent.Slept var3 = TentContent.STAND.remove(var1.getUUID());
         if (var3 != null) {
            standInside(var1, var3);
         }
      }

      /** Beds placed inside a camping tent: zip the door for the night and remember the tent. */
      private static void trackCampingBed(ServerPlayer player) {
         BlockPos bed = player.getSleepingPos().orElse(null);
         if (bed == null || !(player.level().getBlockState(bed).getBlock() instanceof BedBlock)) {
            return;
         }

         BlockPos origin = TentContent.campingOrigin(player.level(), bed);
         if (origin == null) {
            TentContent.NOT_IN_TENT.add(player.getUUID());
            return;
         }

         CampingTent.Anchor a = TentContent.anchor(player.level(), origin);
         Direction facing = a == null ? Direction.NORTH : a.getBlockState().getValue(CampingTent.FACING);
         boolean reopen = false;
         if (a != null && a.getBlockState().getValue(CampingTent.READY) && a.getBlockState().getValue(CampingTent.OPEN) && a.doorUntil == 0L) {
            TentContent.setCampingDoor(player.serverLevel(), a, false);
            reopen = true;
         }

         TentContent.SLEEPING.put(player.getUUID(), new TentContent.Slept(null, origin, facing, bed, reopen, origin));
      }

      private static void standInside(ServerPlayer player, TentContent.Slept s) {
         Level level = player.level();
         List<Vec3> spots = new ArrayList<>();
         Vec3 bedSpot;
         if (s.tent() != null) {
            CompactTent tent = s.tent();
            BlockState bedState = level.getBlockState(s.bed());
            if (!bedState.is(tent)) {
               return;
            }

            Vec3 off = tent.sleepOffset(s.facing());
            bedSpot = new Vec3(s.bed().getX() + 0.5 + off.x, s.base().getY(), s.bed().getZ() + 0.5 + off.z);
            if (!tent.shape.sleepOnly() && !tent.shape.crouch()) {
               spots.add(tent.standUp(s.base(), s.facing()));
            }

            for (BlockPos free : tent.freeCells()) {
               if (free.getY() == 0) {
                  spots.add(Vec3.atBottomCenterOf(s.base().offset(CompactTent.rotateLocal(free, s.facing()))));
               }
            }

            spots.add(bedSpot);
            for (int i = 0; i < tent.shape.cells().size(); i++) {
               if (tent.shape.cells().get(i).getY() == 0) {
                  spots.add(Vec3.atBottomCenterOf(s.base().offset(tent.offset(i, s.facing()))));
               }
            }

            // a quarter-block grid over the floor finds crawl room on the ridge line of the low tents
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < tent.shape.cells().size(); i++) {
               BlockPos c = s.base().offset(tent.offset(i, s.facing()));
               minX = Math.min(minX, c.getX());
               maxX = Math.max(maxX, c.getX());
               minZ = Math.min(minZ, c.getZ());
               maxZ = Math.max(maxZ, c.getZ());
            }

            for (double gx = minX + 0.25; gx < maxX + 1; gx += 0.25) {
               for (double gz = minZ + 0.25; gz < maxZ + 1; gz += 0.25) {
                  spots.add(new Vec3(gx, s.base().getY(), gz));
               }
            }

            // keep the whole body inside the shelter's footprint, never half out of the doorway
            final double loX = minX + 0.3;
            final double hiX = maxX + 0.7;
            final double loZ = minZ + 0.3;
            final double hiZ = maxZ + 0.7;
            spots.removeIf(v -> v.x < loX || v.x > hiX || v.z < loZ || v.z > hiZ);
            TentContent.reopen(level, s);
         } else {
            BlockPos origin = s.campOrigin();
            if (!(level.getBlockState(origin.offset(CampingTent.offset(0, s.facing()))).getBlock() instanceof CampingTent)) {
               return;
            }

            bedSpot = Vec3.atBottomCenterOf(s.bed());
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  spots.add(Vec3.atBottomCenterOf(origin.offset(dx, 0, dz)));
               }
            }

            for (double gx = -0.65; gx <= 1.66; gx += 0.25) {
               for (double gz = -0.65; gz <= 1.66; gz += 0.25) {
                  spots.add(new Vec3(origin.getX() + gx, origin.getY(), origin.getZ() + gz));
               }
            }

            TentContent.reopen(level, s);
         }

         final Vec3 anchorSpot = bedSpot;
         spots.sort(Comparator.comparingDouble(v -> v.distanceToSqr(anchorSpot.x, v.y, anchorSpot.z)));
         // Prefer anywhere a player can stand; fall back to crouching, then crawling room (low dome and ridge tents).
         for (Pose pose : TentContent.POSES) {
            for (Vec3 spot : spots) {
               Vec3 fit = fits(player, spot, pose);
               if (fit != null) {
                  if (player.distanceToSqr(fit) > 64.0) {
                     return;
                  }

                  player.setPose(pose);
                  player.teleportTo(player.serverLevel(), fit.x, fit.y, fit.z, s.facing().toYRot(), 0.0F);
                  return;
               }
            }
         }
      }

      private static Vec3 fits(ServerPlayer player, Vec3 spot, Pose pose) {
         Level level = player.level();
         for (double lift : TentContent.FLOOR_LIFTS) {
            Vec3 at = spot.add(0.0, lift, 0.0);
            BlockPos cell = BlockPos.containing(at);
            if (!level.getFluidState(cell).isEmpty()) {
               continue;
            }

            if (level.noCollision(player, player.getDimensions(pose).makeBoundingBox(at))
               && !level.noCollision(player, new AABB(at.x - 0.25, at.y - 0.2, at.z - 0.25, at.x + 0.25, at.y + 0.01, at.z + 0.25))) {
               return at;
            }
         }

         return null;
      }
   }
}
