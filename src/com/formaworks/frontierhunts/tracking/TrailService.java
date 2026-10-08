package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.HuntRules;
import com.formaworks.frontierhunts.HuntService;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.progression.AssignmentService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the readable trail: blood (by wound region, see {@link BloodTrail}), prints (see
 * {@link TrackPrints}), inspection and the incremental sync of nearby sign to each player.
 */
@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class TrailService {
   private static final Map<UUID, Sent> SENT = new HashMap<>();
   private static final Map<UUID, Long> REQUESTS = new HashMap<>();

   private static final class Sent {
      final String dimension;
      final Map<UUID, Integer> marks = new HashMap<>();

      Sent(String dimension) {
         this.dimension = dimension;
      }
   }

   public static boolean active(Level var0) {
      return HuntRules.active(var0) && var0.getGameRules().getBoolean(HuntRules.TRACKING);
   }

   /** Legacy hoofprint hook from the Whitetail: prints now come from {@link TrackPrints} for every animal. */
   public static boolean leave(Whitetail var0, boolean var1) {
      return var1 && blood(var0, false);
   }

   public static boolean blood(Whitetail var0, boolean var1) {
      if (var1) {
         boolean placed = blood(var0, var0.woundWorldPosition(), true);
         BloodTrail.impact(var0);
         return placed;
      }
      return BloodTrail.step(var0, false);
   }

   public static boolean blood(LivingEntity var0, Vec3 var1, boolean var2) {
      BloodTrail.BloodType t = BloodTrail.type(var0);
      if (!var2) {
         return ground(var0, var1, 0, t);
      } else {
         boolean var3 = ground(var0, var1, 1, t);

         for (int var4 = 0; var4 < 4; var4++) {
            double var5 = (double)var4 * 2.399 + (double)var0.getYRot() * 0.0174533;
            double var8 = (var4 == 0 ? 0.34 : 0.72) * (double)TrailSurfaces.bloodScale(var0);
            var3 |= ground(var0, var1.add(Math.cos(var5) * var8, 0.0, Math.sin(var5) * var8), 0, t);
         }

         var3 |= ground(var0, new Vec3(var0.getX() + 0.9 * (double)TrailSurfaces.bloodScale(var0), var1.y, var0.getZ()), 0, t);
         TrailStore var11 = TrailStore.get((ServerLevel)var0.level());
         List<TrailMark> var12 = var11.nearby(var1, 2.0, 16, var0.level().getGameTime());
         if (var12.stream().noneMatch(m -> m.animal().equals(var0.getUUID()) && m.style() == 0)) {
            var12.stream()
               .filter(m -> m.animal().equals(var0.getUUID()) && m.style() == 1)
               .findFirst()
               .ifPresent(m -> var11.add(new TrailMark(
                  UUID.randomUUID(), m.animal(), m.position().add(0.025, 0.001, 0.025), m.support(), m.yaw(), m.created(), m.rainWear(), true,
                  m.individual(), m.activity(), m.face(), 0, Math.min(0.22F, m.radius() * 0.58F), m.sign(), 0.0F, 1.0F
               ), var0.level().getGameTime()));
         }

         return var3;
      }
   }

   public static boolean dense(Whitetail var0) {
      return BloodTrail.step(var0, true);
   }

   /** one blood mark of the given style / type under {@code pos} (package API for BloodTrail / WildlifeBleeding) */
   static boolean mark(LivingEntity e, Vec3 pos, int style, BloodTrail.BloodType t) {
      return ground(e, pos, style, t);
   }

   public static void ensureImpactDrip(Whitetail var0) {
      if (var0.level() instanceof ServerLevel var1) {
         TrailStore var6 = TrailStore.get(var1);
         List<TrailMark> var3 = var6.nearby(var0.position(), 4.0, 16, var1.getGameTime());
         if (var3.stream().noneMatch(m -> m.animal().equals(var0.getUUID()) && m.blood() && m.style() == 0)) {
            BloodTrail.BloodType t = BloodTrail.type(var0);
            if (!ground(var0, var0.position(), 0, t)) {
               BlockPos var4 = var0.blockPosition().below();
               BlockState below = var1.getBlockState(var4);
               net.minecraft.world.phys.shapes.VoxelShape shape = below.getCollisionShape(var1, var4);
               if (!shape.isEmpty() && TrailSurfaces.groundSupport(below)) {
                  // [1.1.6] on the block's real top (slabs, paths, turf, snow), not always a full block up
                  double top = Math.max(shape.max(net.minecraft.core.Direction.Axis.Y), TrailSurfaces.visualTop(var1, var4, var0.getX(), var0.getZ()));
                  var6.add(new TrailMark(
                     UUID.randomUUID(), var0.getUUID(), new Vec3(var0.getX(), (double)var4.getY() + top + 0.012, var0.getZ()), var4, var0.getYRot(),
                     var1.getGameTime(), 0, true, TrackPrints.describe(var0), 0, Direction.UP, 0, 0.2F, t.ordinal(), 0.0F, 1.0F
                  ), var1.getGameTime());
               }
            }
         }
      }
   }

   public static boolean dense(LivingEntity var0, Vec3 var1) {
      return ground(var0, var1, 3, BloodTrail.type(var0));
   }

   public static void pool(Whitetail var0, int var1) {
      pool(var0, var0.woundWorldPosition(), var1);
   }

   public static void pool(LivingEntity var0, Vec3 var1, int var2) {
      BloodTrail.BloodType t = BloodTrail.type(var0);
      ground(var0, var1, 4, t);

      for (int var3 = 0; var3 < 3; var3++) {
         double var4 = (double)(var3 + var2 * 3) * 2.399 + (double)var0.getYRot() * 0.0174533;
         double var6 = (0.22 + (double)var2 * 0.15) * (double)TrailSurfaces.bloodScale(var0);
         ground(var0, var1.add(Math.cos(var4) * var6, 0.0, Math.sin(var4) * var6), 4, t);
      }
   }

   /** where a wounded animal lay: one matted bed with a stain, not a ring of drops */
   static void bed(LivingEntity e, BloodTrail.BloodType t) {
      if (!(e.level() instanceof ServerLevel level)) {
         return;
      }
      TrailStore store = TrailStore.get(level);
      for (TrailMark m : store.nearby(e.position(), 1.6, 32, level.getGameTime())) {
         if (m.blood() && m.style() == TrailMark.BED && m.animal().equals(e.getUUID())) {
            return;
         }
      }
      ground(e, e.position(), TrailMark.BED, t);
      if (e instanceof Whitetail w) {
         PassageSign.matted(level, w); // [1.1.6] the grass where it lay is pressed down
      }
   }

   private static boolean ground(LivingEntity var0, Vec3 var1, int var2, BloodTrail.BloodType type) {
      if (var0.level() instanceof ServerLevel var3
         && !var0.isInWaterOrBubble()
         && Double.isFinite(var1.lengthSqr())
         && var3.hasChunkAt(BlockPos.containing(var1))) {
         double var15 = Math.max(var1.y + 0.04, var0.getY() + 0.7);
         BlockHitResult var6 = var3.clip(
            new ClipContext(new Vec3(var1.x, var15, var1.z), new Vec3(var1.x, var0.getY() - 6.0, var1.z), Block.COLLIDER, Fluid.ANY, var0)
         );
         TrailMark var7 = TrailSurfaces.fit(var0, var6, var2, true);
         if (var7 != null && var7.face() == Direction.UP && !TrailSurfaces.groundSupport(var3.getBlockState(var7.support()))) {
            return false; // [1.1.6] never on fence and wall tops or on top of leaves (they looked suspended)
         }
         if (var7 != null && var7.face() == Direction.UP) {
            // [1.1.6] the drawn top, where it is above the collision top (mud, soul sand, soft Frontier snow ...)
            double vis = TrailSurfaces.visualTop(var3, var7.support(), var7.position().x, var7.position().z);
            if (vis > 0.0 && (double)var7.support().getY() + vis + 0.012 > var7.position().y + 0.004) {
               var7 = lift(var7, (double)var7.support().getY() + vis + 0.012);
            }
            double var8 = TrailSurfaces.snowTop(var3.getBlockState(var7.support()));
            if (var8 > 0.0) {
               var7 = lift(var7, (double)var7.support().getY() + var8 + 0.012);
            } else {
               // [tracking] a single vanilla snow layer has no collision: the ray hits the ground below it
               BlockPos up = var7.support().above();
               double layer = TrailSurfaces.snowTop(var3.getBlockState(up));
               if (layer > 0.0) {
                  var7 = var7.at(new Vec3(var7.position().x, up.getY() + layer + 0.012, var7.position().z), up);
               }
            }

            BlockPos var10 = var7.support().above();
            BlockState var11 = var3.getBlockState(var10);
            // [1.1.6] blood under grass, ferns and brush lies on the soil at the stems (only thin covers - litter, petals,
            // a snow layer - carry it on top); before, it was drawn at the height of the grass tips and hung in the air
            if (var2 != TrailMark.BED && var11.is(TrailSurfaces.FOLIAGE) && var11.getCollisionShape(var3, var10).isEmpty()
               && TrailSurfaces.foliageTop(var11) <= 0.15) {
               TrailMark var12 = TrailSurfaces.onFoliage(var0, var10, var11, var7.position(), Direction.UP, var2);
               if (var12 != null) {
                  var7 = var12;
               }
            }
            if (var2 == TrailMark.BED) {
               var7 = var7.styled(TrailMark.BED, Math.min(0.45F, 0.42F * TrailSurfaces.bloodScale(var0) + 0.06F));
            }
            var7 = var7.with(type.ordinal(), 0.0F, 1.0F);

            TrailStore var16 = TrailStore.get(var3);

            for (TrailMark var14 : var16.nearby(var7.position(), 0.19, 48, var3.getGameTime())) {
               if (var14.blood()
                  && var14.animal().equals(var0.getUUID())
                  && var14.face() == var7.face()
                  && var14.support().equals(var7.support())
                  && var14.age(var3.getGameTime()) < 240L) {
                  if (var14.style() == 4 || var14.style() == var2) {
                     return true;
                  }

                  if (var2 == 4 || var2 == 3) {
                     var16.remove(var14.id());
                  }
               }
            }

            return var16.add(var7, var3.getGameTime());
         }

         return false;
      }

      return false;
   }

   private static TrailMark lift(TrailMark var0, double var1) {
      return var0.at(new Vec3(var0.position().x, var1, var0.position().z), var0.support());
   }

   public static void brush(Whitetail var0) {
      BloodTrail.brush(var0);
   }

   public static void brush(LivingEntity var0, Vec3 var1) {
      brushAt(var0, var1, BloodTrail.type(var0));
   }

   static void brushAt(LivingEntity var0, Vec3 var1, BloodTrail.BloodType type) {
      if (var0.level() instanceof ServerLevel var2 && !var0.isInWaterOrBubble()) {
         AABB var13 = var0.getBoundingBox().inflate(0.09);
         Vec3 var4 = new Vec3(
            var0.getX(),
            Math.clamp(var1.y, var13.minY + Math.min(0.12, (double)var0.getBbHeight() * 0.25), var13.maxY - Math.min(0.08, (double)var0.getBbHeight() * 0.2)),
            var0.getZ()
         );
         int var5 = 0;

         for (Direction var7 : Plane.HORIZONTAL) {
            Vec3 var8 = switch (var7) {
               case EAST -> new Vec3(var13.maxX, var4.y, var4.z);
               case WEST -> new Vec3(var13.minX, var4.y, var4.z);
               case NORTH -> new Vec3(var4.x, var4.y, var13.minZ);
               default -> new Vec3(var4.x, var4.y, var13.maxZ);
            };
            if (!(var8.distanceToSqr(var1) > 0.5625) && var2.hasChunkAt(BlockPos.containing(var8))) {
               BlockHitResult var9 = var2.clip(new ClipContext(var4, var8, Block.COLLIDER, Fluid.NONE, var0));
               TrailMark var10 = TrailSurfaces.fit(var0, var9, 2, true);
               if (var10 != null && var10.face().getAxis() != Axis.Y) {
                  TrailMark typed = var10.with(type.ordinal(), 0.0F, 1.0F);
                  TrailStore var11 = TrailStore.get(var2);
                  if (!recentBrush(var11, var2, typed)) {
                     var11.add(typed, var2.getGameTime());
                     if (++var5 == 2) {
                        break;
                     }
                  }
               }
            }
         }

         for (Direction var15 : Plane.HORIZONTAL) {
            if (var5 >= 2) {
               break;
            }
            Vec3 var16 = switch (var15) {
               case EAST -> new Vec3(var13.maxX, var4.y, var4.z);
               case WEST -> new Vec3(var13.minX, var4.y, var4.z);
               case NORTH -> new Vec3(var4.x, var4.y, var13.minZ);
               default -> new Vec3(var4.x, var4.y, var13.maxZ);
            };
            if (!(var16.distanceToSqr(var1) > 0.5625)) {
               BlockPos var17 = BlockPos.containing(var16);
               if (var2.hasChunkAt(var17)) {
                  BlockState var18 = var2.getBlockState(var17);
                  if (!var18.is(TrailSurfaces.FOLIAGE) || !var18.getCollisionShape(var2, var17).isEmpty()) {
                     var17 = var17.below();
                     var18 = var2.getBlockState(var17);
                  }

                  if (var18.is(TrailSurfaces.FOLIAGE)
                     && var18.getCollisionShape(var2, var17).isEmpty()
                     && !((double)var17.getY() + TrailSurfaces.foliageTop(var18) < var4.y - 0.4)) {
                     // [1.1.6] blood wiped off on grass and brush runs down to the soil at the stems: a drop on the
                     // ground at the plant's base, not a smear hanging on the edge of the block (it looked suspended)
                     if (ground(var0, new Vec3(var16.x, var4.y, var16.z), 0, type)) {
                        var5++;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean recentBrush(TrailStore store, ServerLevel level, TrailMark m) {
      for (TrailMark o : store.nearby(m.position(), 1.0, 48, level.getGameTime())) {
         if (o.blood() && o.style() == 2 && o.animal().equals(m.animal()) && o.support().equals(m.support()) && o.face() == m.face()
            && o.age(level.getGameTime()) < 80L) {
            return true;
         }
      }
      return false;
   }

   public static boolean supported(Level var0, TrailMark var1) {
      if (!var0.hasChunkAt(var1.support())) {
         return false;
      } else {
         BlockState var2 = var0.getBlockState(var1.support());
         if (var2.is(TrailSurfaces.FOLIAGE) && var2.getCollisionShape(var0, var1.support()).isEmpty()) {
            return TrailSurfaces.hasFoliage(var2) && var0.getFluidState(BlockPos.containing(var1.position())).isEmpty();
         } else {
            double var3 = TrailSurfaces.snowTop(var2);
            if (var3 > 0.0 && var1.face() == Direction.UP) {
               // a fresh snow layer on top (snowfall) buries the mark: its height no longer matches
               return Math.abs(var1.position().y - ((double)var1.support().getY() + var3 + 0.012)) < 0.02 && var0.getFluidState(var1.support()).isEmpty();
            } else {
               if (var1.face() == Direction.UP && TrailSurfaces.snowTop(var0.getBlockState(var1.support().above())) > 0.0) {
                  // [tracking] snow fell on a bare-ground print or blood: covered
                  return false;
               }
               Vec3 var5 = var1.position();
               Vec3 var6 = var1.normal();
               BlockHitResult var7 = var0.clip(
                  new ClipContext(var5.add(var6.scale(0.025)), var5.add(var6.scale(-0.04)), Block.COLLIDER, Fluid.ANY, CollisionContext.empty())
               );
               return var7.getType() == Type.BLOCK
                  && var7.getDirection() == var1.face()
                  && var7.getBlockPos().equals(var1.support())
                  && var7.getLocation().distanceToSqr(var5.subtract(var6.scale(0.012))) < 6.250000000000001E-4
                  && var0.getFluidState(BlockPos.containing(var5)).isEmpty();
            }
         }
      }
   }

   /**
    * [sign] Every mark is visible to everyone with no tool: the old "crouch with the journal" scent-trace assist that
    * gated SCENT marks was removed (user request: you find sign by looking, not with a tool).
    */
   public static boolean visibleTo(Player var0, TrailMark var1) {
      return true;
   }

   public static boolean aimedAt(Player var0, TrailMark var1) {
      if (!visibleTo(var0, var1)) {
         return false;
      } else {
         double var2 = ExpeditionService.inspectRange(var0);
         Vec3 var4 = var0.getEyePosition();
         Vec3 var5 = var4.add(var0.getLookAngle().scale(var2));
         double var6 = Math.max(0.12, (double)var1.radius());
         Vec3 var8 = var1.normal();
         AABB var9 = new AABB(var1.position(), var1.position())
            .inflate(var8.x == 0.0 ? var6 : 0.035, var8.y == 0.0 ? var6 : 0.035, var8.z == 0.0 ? var6 : 0.035);
         return var4.distanceToSqr(var1.position()) <= var2 * var2
            && var9.clip(var4, var5).isPresent()
            && var0.level().clip(new ClipContext(var4, var1.position().add(var8.scale(0.025)), Block.COLLIDER, Fluid.NONE, var0)).getType() == Type.MISS;
      }
   }

   static boolean outsideReserve() {
      try {
         return com.formaworks.frontierhunts.HuntConfig.WHITETAILS_OUTSIDE_RESERVE.get();
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** Validated look-up of a mark a player is aiming at (used by inspection and by the hound's "track this"). */
   public static TrailMark aimedMark(ServerPlayer var0, UUID var1) {
      if (!var0.level().getGameRules().getBoolean(HuntRules.TRACKING) || !var0.isAlive() || var0.isSpectator()) {
         return null;
      }
      long var2 = var0.level().getGameTime();
      TrailMark var5 = TrailStore.get(var0.serverLevel()).find(var1);
      // [1.1.5] hoofprints also read in ordinary worlds where whitetails roam outside the reserve (else the Sign lesson only
      // worked on rubs and scrapes there)
      return var5 != null && (var5.blood() || active(var0.level()) || outsideReserve()) && !var5.expired(var2) && supported(var0.level(), var5) && aimedAt(var0, var5)
         ? var5
         : null;
   }

   public static boolean inspect(ServerPlayer var0, UUID var1) {
      long var2 = var0.level().getGameTime();
      Long var4 = REQUESTS.put(var0.getUUID(), var2);
      if (var4 != null && var2 >= var4 && var2 - var4 < 5L) {
         return false;
      }
      TrailMark var5 = aimedMark(var0, var1);
      if (var5 == null) {
         return false;
      }
      boolean academy = com.formaworks.frontierhunts.academy.Academy.signRead(var0, var5); // [academy] training-grounds sign reports to the course only
      if (!academy) {
         ExpeditionService.record(var0, "clue", var5.individual(), 1, 0.0);
      }
      com.formaworks.frontierhunts.guide.FieldSchool.markInspected(var0, var5); // [guide] Field School lessons 2/6 + predator note
      com.formaworks.frontierhunts.firsthunt.FirstHunt.signRead(var0, var5); // [1.2.7] first hunt: signs / tracking
      com.formaworks.frontierhunts.journal.JournalHooks.signRead(var0, var5); // [journal] sign counters, Tracking XP, Trail Sense / Bloodhound
      if (HuntRules.active(var0.level())) {
         HunterLedger.get(var0.serverLevel()).inspectClue(var0.getUUID());
         HuntService.send(var0, false);
      }
      AssignmentService.trail(var0, var5); // [academy] counts in any Overworld game (eligible() checks the level)

      if (var0.connection != null && var0.connection.hasChannel(TrailNetwork.Confirmed.TYPE)) {
         PacketDistributor.sendToPlayer(var0, new TrailNetwork.Confirmed(var5.id()), new CustomPacketPayload[0]);
      }

      return true;
   }

   @SubscribeEvent
   public static void levelTick(Post var0) {
      if (var0.getLevel() instanceof ServerLevel var1 && var1.getGameTime() % 20L == 0L) {
         TrailStore.get(var1).maintain(var1);
      }
   }

   @SubscribeEvent
   public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && var1.tickCount % 10 == 0) {
         sync(var1);
      }
   }

   /** Sign near a player, blood first, then prints and other sign, nearest first. */
   static List<TrailMark> visible(ServerPlayer var0) {
      ServerLevel var1 = var0.serverLevel();
      if (!var0.isAlive() || var0.isSpectator()) {
         return List.of();
      }
      boolean prints = active(var1);
      List<TrailMark> near = TrailStore.get(var1).nearby(var0.position(), TrailStore.VIEW_RADIUS * com.formaworks.frontierhunts.journal.HunterSkills.signRadius(var0), TrailStore.WORLD_LIMIT, var1.getGameTime()); // [journal] Keen Eye
      List<TrailMark> out = new ArrayList<>(Math.min(near.size(), TrailStore.CLIENT_LIMIT));
      for (TrailMark m : near) {
         if (m.blood() && out.size() < TrailStore.CLIENT_LIMIT && var1.hasChunkAt(m.support())) {
            out.add(m);
         }
      }
      // [1.1.6] bent and pressed grass is visible in every world (it is what you would see anyway)
      for (TrailMark m : near) {
         if (m.plantSign() && out.size() < TrailStore.CLIENT_LIMIT && var1.hasChunkAt(m.support())) {
            out.add(m);
         }
      }
      if (prints) {
         for (TrailMark m : near) {
            if (!m.blood() && !m.plantSign() && out.size() < TrailStore.CLIENT_LIMIT && var1.hasChunkAt(m.support())) {
               out.add(m);
            }
         }
      }
      return out;
   }

   /** Incremental: only new / re-weathered marks and removals travel, not the whole view every half second. */
   public static void sync(ServerPlayer var0) {
      if (var0.connection == null || !var0.connection.hasChannel(TrailNetwork.Delta.TYPE)) {
         return;
      }
      String dim = var0.serverLevel().dimension().location().toString();
      List<TrailMark> list = visible(var0);
      Sent s = SENT.get(var0.getUUID());
      boolean reset = s == null || !s.dimension.equals(dim);
      if (reset) {
         s = new Sent(dim);
         SENT.put(var0.getUUID(), s);
      }
      List<TrailMark> up = new ArrayList<>();
      Set<UUID> keep = new HashSet<>();
      for (TrailMark m : list) {
         keep.add(m.id());
         Integer w = s.marks.get(m.id());
         if (w == null || Math.abs(w - m.rainWear()) > 240) {
            if (up.size() < TrailNetwork.MAX_UPSERTS) {
               up.add(m);
            } else {
               keep.remove(m.id());
            }
         }
      }
      List<UUID> gone = new ArrayList<>();
      for (UUID id : s.marks.keySet()) {
         if (!keep.contains(id) && gone.size() < TrailNetwork.MAX_REMOVALS) {
            gone.add(id);
         }
      }
      if (!reset && up.isEmpty() && gone.isEmpty()) {
         return;
      }
      PacketDistributor.sendToPlayer(var0, new TrailNetwork.Delta(dim, reset, up, gone), new CustomPacketPayload[0]);
      for (UUID id : gone) {
         s.marks.remove(id);
      }
      for (TrailMark m : up) {
         s.marks.put(m.id(), m.rainWear());
      }
   }

   private static void forget(UUID var0) {
      SENT.remove(var0);
      REQUESTS.remove(var0);
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent var0) {
      forget(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      forget(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void dimension(PlayerChangedDimensionEvent var0) {
      forget(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent var0) {
      SENT.clear();
      REQUESTS.clear();
   }

   private TrailService() {
   }
}
