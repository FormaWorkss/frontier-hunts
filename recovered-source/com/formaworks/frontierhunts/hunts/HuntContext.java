package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.expedition.HubGroundBlind;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.expedition.TowerBlind;
import com.formaworks.frontierhunts.expedition.TreeStandSeat;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.HuntBridge;
import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.tracking.hound.HoundRegistry;
import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * [hunts] Reads the field situation for a hunt event from the live world (server): weapon class, stand / blind,
 * calls, decoys, bait, glassing, hound, terrain, calendar. Everything is bounded and runs only on a hit, a kill or a
 * glassing, never per tick.
 */
public final class HuntContext {
   /** Persistent-data tags this workstream writes on animals. */
   static final String CTX = "frontierhunts_hunts";
   static final String GLASS = "frontierhunts_hunts_glass";
   static final String CALLED = "frontierhunts_hunts_call";
   static final String BAITED = "frontierhunts_hunts_bait";
   /** a call, bait visit or glassing counts for a shot within this many ticks (5 minutes / 10 minutes) */
   static final long CALL_WINDOW = 6000L;
   static final long GLASS_WINDOW = 12000L;
   /** a later hit within this long belongs to the same wound (same as the journal) */
   static final long SAME_WOUND = 6000L;

   private static final Set<String> SNOW_CAMO = Set.of("frontierhunts:ghillie_snow_hood", "frontierhunts:ghillie_snow_jacket",
      "frontierhunts:ghillie_snow_trousers", "frontierhunts:snow_camo_coveralls");

   private HuntContext() {
   }

   // ---------------------------------------------------------------------------------------------- the hunter

   public static HuntEvent.Gun weapon(Entity projectile) {
      if (projectile instanceof FieldArrow) {
         return HuntEvent.Gun.BOW;
      }
      if (projectile instanceof RifleBullet) {
         return HuntEvent.Gun.RIFLE;
      }
      if (projectile instanceof HuntProjectile hp) {
         Weapon w = hp.kind();
         if (w == null) {
            return HuntEvent.Gun.OTHER;
         }
         if (w.pellets > 0) {
            return HuntEvent.Gun.SHOTGUN;
         }
         return switch (w) {
            case RECURVE_BOW, COMPOUND_BOW, CROSSBOW, BOWFISHING_BOW -> HuntEvent.Gun.BOW;
            case LEVER_RIFLE, SEMI_AUTO_RIFLE -> HuntEvent.Gun.RIFLE;
            case REVOLVER, FIELD_PISTOL -> HuntEvent.Gun.HANDGUN;
            default -> HuntEvent.Gun.OTHER;
         };
      }
      return projectile == null ? HuntEvent.Gun.NONE : HuntEvent.Gun.OTHER;
   }

   static boolean inStand(ServerPlayer p) {
      return p.getVehicle() instanceof TreeStandSeat;
   }

   /** Inside a hub ground blind or a tower blind: at least 4 blind blocks around the hunter (7 x 5 x 7 box). */
   static boolean inBlind(ServerPlayer p) {
      ServerLevel level = p.serverLevel();
      BlockPos at = p.blockPosition();
      int n = 0;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = -1; dy <= 3; dy++) {
         for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
               m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
               Block b = level.getBlockState(m).getBlock();
               if (b instanceof HubGroundBlind || b instanceof TowerBlind) {
                  if (++n >= 4) {
                     return true;
                  }
               }
            }
         }
      }
      return false;
   }

   static boolean onFoot(ServerPlayer p) {
      Entity v = p.getVehicle();
      return v == null || v instanceof TreeStandSeat || com.formaworks.frontierhunts.seating.SeatEntity.isSeat(v); // [onboard2] seats
   }

   static boolean snowCamo(ServerPlayer p) {
      for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
         ItemStack st = p.getItemBySlot(s);
         if (!st.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (id != null && SNOW_CAMO.contains(id.toString())) {
               return true;
            }
         }
      }
      return false;
   }

   /** The hunter's tracking hound is trailing, casting for or baying this animal (or sits by it, found). */
   static boolean hound(ServerPlayer p, LivingEntity animal) {
      try {
         HoundRegistry.Entry e = HoundRegistry.get(p.server).get(p.getUUID());
         if (e == null || !(animal.level() instanceof ServerLevel level) || !(level.getEntity(e.hound) instanceof TrackingHound h) || !h.isAlive()) {
            return false;
         }
         UUID q = h.quarry();
         int mode = h.mode();
         boolean working = mode == TrackingHound.TRACK || mode == TrackingHound.CAST || mode == TrackingHound.BAY || mode == TrackingHound.FOUND;
         if (working && animal.getUUID().equals(q)) {
            return true;
         }
         // baying it right now (any line it was put on led here)
         return mode == TrackingHound.BAY && h.distanceToSqr(animal) < 14.0 * 14.0;
      } catch (RuntimeException ex) {
         return false;
      }
   }

   // ---------------------------------------------------------------------------------------------- the animal

   /** Came to this hunter's call recently: deer through the journal's call stamp, wildlife through ours. */
   static boolean called(LivingEntity animal, UUID hunter, long now) {
      CompoundTag d = animal.getPersistentData();
      return stampBy(d.getCompound(HuntBridge.CALL), hunter, now, CALL_WINDOW) || stampBy(d.getCompound(CALLED), hunter, now, CALL_WINDOW);
   }

   /** Visited this hunter's bait recently, or is close to one of their live bait piles. */
   static boolean baited(ServerLevel level, LivingEntity animal, UUID hunter, long now) {
      if (stampBy(animal.getPersistentData().getCompound(BAITED), hunter, now, CALL_WINDOW)) {
         return true;
      }
      for (HuntStore.Bait b : HuntStore.get(level.getServer()).baitsNear(level.dimension().location().toString(), animal.getX(), animal.getZ(), 24.0, now)) {
         if (b.owner.equals(hunter)) {
            return true;
         }
      }
      return false;
   }

   /** A duck decoy spread within 24 blocks, or (whitetail) the deer was working a scent decoy. */
   static boolean decoy(ServerLevel level, LivingEntity animal, ServerPlayer p, long now) {
      if (animal instanceof Whitetail && animal.getPersistentData().getLong("frontier_decoy_until") > now - CALL_WINDOW) {
         return true;
      }
      return !HuntContent.liveDecoys(level, animal.getX(), animal.getY(), animal.getZ(), 24.0, 1).isEmpty()
         || !HuntContent.liveDecoys(level, p.getX(), p.getY(), p.getZ(), 24.0, 1).isEmpty();
   }

   static boolean glassed(LivingEntity animal, UUID hunter, long now) {
      ListTag l = animal.getPersistentData().getList(GLASS, Tag.TAG_COMPOUND);
      for (int i = 0; i < l.size(); i++) {
         if (stampBy(l.getCompound(i), hunter, now, GLASS_WINDOW)) {
            return true;
         }
      }
      return false;
   }

   static boolean stampBy(CompoundTag t, UUID hunter, long now, long window) {
      return t.hasUUID("by") && t.getUUID("by").equals(hunter) && now >= t.getLong("t") && now - t.getLong("t") <= window;
   }

   /** Standing on snow (layer, block or powder snow) or in a snowy-ground biome while it snows there. */
   static boolean snow(ServerLevel level, LivingEntity a) {
      BlockPos feet = a.blockPosition();
      BlockState at = level.getBlockState(feet);
      BlockState below = level.getBlockState(feet.below());
      return at.is(Blocks.SNOW) || at.is(Blocks.POWDER_SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.POWDER_SNOW) || below.is(Blocks.SNOW);
   }

   /** Open water within 6 blocks of the animal. */
   static boolean water(ServerLevel level, LivingEntity a) {
      if (a.isInWater()) {
         return true;
      }
      BlockPos c = a.blockPosition();
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = -2; dy <= 1; dy++) {
         for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz = -6; dz <= 6; dz += 2) {
               m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
               if (level.getFluidState(m).is(Fluids.WATER)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   /** Animals of the same species within 24 blocks, counting this one. */
   static int herd(ServerLevel level, LivingEntity a) {
      String sp = species(a);
      if (sp == null) {
         return 1;
      }
      return 1 + level.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(24.0, 8.0, 24.0),
         o -> o != a && o.isAlive() && sp.equals(species(o)) && !(o instanceof Whitetail w && w.downed())).size();
   }

   static String species(LivingEntity a) {
      if (a instanceof Whitetail w) {
         return w.species().id;
      }
      return a instanceof WildlifeMob m && m.species != null ? m.species.id : null;
   }

   static boolean rut(LivingEntity a) {
      if (a instanceof Whitetail w && a.level() instanceof ServerLevel level) {
         try {
            return Rut.phase(w.species(), level).active();
         } catch (RuntimeException e) {
            return false;
         }
      }
      return false;
   }

   static int month(ServerLevel level) {
      try {
         return HuntingCalendar.date(level.getServer().overworld()).month();
      } catch (RuntimeException e) {
         return 9;
      }
   }

   static int timeOfDay(ServerLevel level) {
      return (int)Math.floorMod(level.getDayTime(), 24000L);
   }

   static long day(ServerLevel level) {
      return level.getDayTime() / 24000L;
   }
}
