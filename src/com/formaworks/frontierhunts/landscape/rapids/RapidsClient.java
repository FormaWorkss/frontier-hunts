package com.formaworks.frontierhunts.landscape.rapids;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.AlpineCurrent;
import com.formaworks.frontierhunts.landscape.AlpineFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** [1.1.0] Client side of the rapids: the local player's ride, the camera being thrown about, spray over the white water. */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class RapidsClient {
   private RapidsClient() {
   }

   private static float toss, tossO;

   @SubscribeEvent
   public static void player(PlayerTickEvent.Post e) {
      if (e.getEntity() instanceof LocalPlayer p) {
         Rapids.push(p);
         // [1.1.4] the river's pull, worked out by the server and applied here so it never fights your own movement
         AlpineCurrent.Pull pull = AlpineCurrent.clientPull;
         if (p.isInWater() && !pull.none() && !p.getAbilities().flying && !p.isPassenger()
            && Math.abs(p.level().getGameTime() - AlpineCurrent.clientPullAt) <= 12L) {
            AlpineCurrent.apply(p, pull);
         }
         tossO = toss;
         Rapids.Flow f = p.isInWater() ? Rapids.at(p.level(), p.getX(), p.getY(), p.getZ()) : null;
         float target = 0.0F; // [1.2.5] no rapids: the view is never thrown about in the water
         toss += (target - toss) * (target > toss ? 0.2F : 0.05F);
      }
   }

   /** rapids throw your view around (rolling, jolting); riffles a little */
   @SubscribeEvent
   public static void camera(ViewportEvent.ComputeCameraAngles e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.getCameraType().isFirstPerson() == false) {
         return;
      }
      // [1.1.5] comfort: follows the Camera shake setting (none with Reduced motion)
      float k = Mth.lerp((float)e.getPartialTick(), tossO, toss) * com.formaworks.frontierhunts.HuntConfig.shake();
      if (k < 0.01F) {
         return;
      }
      double t = (mc.player.tickCount + e.getPartialTick()) * 0.05;
      float roll = (float)(Math.sin(t * 5.1) * 7.0 + Math.sin(t * 11.3 + 1.7) * 3.0) * k;
      float pitch = (float)(Math.sin(t * 7.7 + 0.4) * 3.5 + Math.sin(t * 17.0) * 1.2) * k;
      float yaw = (float)(Math.sin(t * 3.3 + 2.1) * 4.0) * k;
      e.setRoll(e.getRoll() + roll);
      e.setPitch(e.getPitch() + pitch);
      e.setYaw(e.getYaw() + yaw);
   }

   /** spray and bursting bubbles over riffles and rapids near the camera */
   @SubscribeEvent
   public static void spray(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      Level level = mc.level;
      if (level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      // [1.1.4] lighter: fewer, closer samples (they cost a heightmap and block lookup each), and the particles
      // setting is honoured - Decreased halves the spray, Minimal turns it off
      int setting = mc.options.particles().get().getId();
      if (setting >= 0) {
         return; // [1.2.5] no whitewater spray: the rapids are gone
      }
      RandomSource r = level.random;
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
      int samples = setting == 0 ? 18 : 9;
      for (int i = 0; i < samples; i++) {
         int x = px + r.nextInt(33) - 16, z = pz + r.nextInt(33) - 16;
         if (!level.hasChunk(x >> 4, z >> 4)) {
            continue;
         }
         int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
         BlockState s = level.getBlockState(m.set(x, y, z));
         if (!(s.getBlock() instanceof AlpineFlow)) {
            continue;
         }
         int str = s.getValue(AlpineFlow.STRENGTH);
         if (str == 0 || str == 1 && r.nextInt(3) != 0) {
            continue;
         }
         double sx = x + r.nextDouble(), sz = z + r.nextDouble(), sy = y - 0.08;
         var d = s.getValue(AlpineFlow.FACING);
         for (int k = 0; k < (str == 2 ? 2 : 1); k++) {
            level.addParticle(ParticleTypes.SPLASH, sx + (r.nextDouble() - 0.5) * 0.6, sy, sz + (r.nextDouble() - 0.5) * 0.6,
               d.getStepX() * 0.08, 0.12 + r.nextDouble() * 0.12, d.getStepZ() * 0.08);
         }
         if (str == 2 && r.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, sx, sy + 0.02, sz, d.getStepX() * 0.05, 0.02, d.getStepZ() * 0.05);
         }
      }
   }
}
