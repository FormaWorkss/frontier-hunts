package com.formaworks.frontierhunts.landscape.rapids;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.AlpineFlow;
import com.formaworks.frontierhunts.landscape.AlpineRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * [1.1.0] Running water that takes hold of you. The creeks and rivers mark their runs with a current marker just above
 * the surface (direction downstream, strength 0 run / 1 riffle / 2 rapids); in that water:
 * <ul>
 * <li>a run carries you gently downstream;</li>
 * <li>a riffle pushes you along hard and churns you about;</li>
 * <li>rapids grab you: they drag you downstream fast, throw you from side to side, pull you under in the hydraulics,
 *     then spit you back up to the surface before the next one - you cannot swim against them, only across and out.</li>
 * </ul>
 * The local player is moved on its own client (movement is client side, so this is smooth); everything else in the
 * water - mobs, dropped items - is moved on the server. Boats drift with the same currents (see BoatSim).
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class Rapids {
   private Rapids() {
   }

   public record Flow(double dx, double dz, int strength) {
   }

   /** the current marker over the water at this spot (within a block or two of the surface), or null */
   public static Flow at(BlockGetter level, double x, double y, double z) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
      for (int dy = -1; dy <= 3; dy++) {
         BlockState s = level.getBlockState(m.set(bx, by + dy, bz));
         if (s.getBlock() instanceof AlpineFlow) {
            Direction d = s.getValue(AlpineFlow.FACING);
            return new Flow(d.getStepX(), d.getStepZ(), s.getValue(AlpineFlow.STRENGTH));
         }
      }
      return null;
   }

   /** for the boats: the marker's direction and strength wherever the water runs, or null */
   public static Flow boat(BlockGetter level, BlockPos pos) {
      return at(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
   }

   /** one tick of the water's grip on an entity (whichever side moves it) */
   public static void push(Entity e) {
      if (!e.isInWater() || e.noPhysics || e.isSpectator() || e instanceof VehicleEntity || e.isPassenger()) {
         return;
      }
      if (e instanceof Player p && p.getAbilities().flying) {
         return;
      }
      Flow f = at(e.level(), e.getX(), e.getY(), e.getZ());
      if (f == null) {
         return;
      }
      // [1.2.5] the rapids are gone: every stretch of running water is a plain current that carries you gently
      // downstream - no churning, no hydraulics pulling you under
      int s = 0;
      double depth = Mth.clamp(e.getFluidHeight(FluidTags.WATER) / Math.max(0.3, e.getBbHeight()), 0.2, 1.0);
      Vec3 v = e.getDeltaMovement();
      double along = v.x * f.dx() + v.z * f.dz();
      double lat = v.x * -f.dz() + v.z * f.dx();
      // [1.1.1] much stronger: run ~2.4 m/s, riffle ~5.6 m/s, rapids ~10 m/s; the water turns you to its own course
      double target = s == 0 ? 0.12 : s == 1 ? 0.28 : 0.5;
      double grip = Math.min(0.85, (0.14 + 0.16 * s) * depth);
      along += Mth.clamp((target - along) * grip, -0.12, 0.2);
      lat *= 1.0 - Math.min(0.6, (0.1 + 0.12 * s) * depth);
      double vy = v.y;
      long t = e.level().getGameTime() + (e.getId() * 37L & 1023L);
      if (s >= 1) {
         // the water churns you about: side to side, and in rapids it rolls you under and up again
         double churn = (s == 2 ? 0.11 : 0.04) * depth;
         lat += (Math.sin(t * 0.37) * 0.6 + Math.sin(t * 0.91 + 1.3) * 0.4) * churn;
      }
      if (s == 2) {
         int period = 38 + (int)(e.getId() * 13L & 31L);
         int ph = (int)(t % period);
         if (ph < 18) {
            vy = Math.min(vy, -0.16 - 0.08 * Math.sin(ph / 18.0 * Math.PI)); // a hydraulic sucks you down hard
         } else if (ph < 30) {
            vy = Math.max(vy, vy + 0.11);                                  // and boils you back up
         }
         along += Math.sin(t * 0.23 + 0.7) * 0.06;                         // surging
      }
      e.setDeltaMovement(f.dx() * along - f.dz() * lat, Mth.clamp(vy, -0.6, 0.5), f.dz() * along + f.dx() * lat);
   }

   /** the server moves everything except players (their own clients move them) */
   @SubscribeEvent
   public static void tick(EntityTickEvent.Post event) {
      Entity e = event.getEntity();
      if (!e.level().isClientSide && !(e instanceof Player)) {
         push(e);
      }
   }
}
