package com.formaworks.frontierhunts.season;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [1.1.0] Deep, soft snow.
 *
 * <ul>
 * <li><b>It piles up.</b> While it snows, each column builds toward its own depth instead of stopping at one layer: a
 *     few inches on open ground, more up high and in the cold, scoured thin on ridges and windward slopes, and deep
 *     drifts in the lee of anything that breaks the wind (walls, boulders, banks, trunks) - up to a block and three
 *     quarters. Past a full block the snow stacks a second layer block on top (vanilla allows snow on a full stack).</li>
 * <li><b>You sink in.</b> A snow layer only carries you part of its height (a full block of snow packs down to under
 *     half), and the layers stacked on top carry nothing, so you wade: knee deep you slow down, thigh deep you plough,
 *     in a drift you can barely move and cannot jump. Animals flounder too. (The collision is in SnowCollisionMixin.)</li>
 * <li><b>It packs into trails.</b> Walking through deep snow tramps it down a layer at a time, so a path you use
 *     becomes a trench others can follow.</li>
 * <li><b>It thaws</b> from the top in spring (SeasonalSnow).</li>
 * </ul>
 * The surface is drawn smooth (client SmoothSnowModel), so none of this looks like stairs.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class DeepSnow {
   private DeepSnow() {
   }

   public static final int MAX_LAYERS = 14;

   // ------------------------------------------------------------------------------------------------ shapes

   /** how high a layer carries you, in pixels: a little less than it shows, and a full block packs to under half */
   private static final VoxelShape[] FIRM = new VoxelShape[9];

   static {
      FIRM[0] = Shapes.empty();
      for (int l = 1; l <= 8; l++) {
         double px = l <= 2 ? (l - 1) * 2.0 : 2.0 + (l - 2) * 0.85;
         FIRM[l] = px <= 0.0 ? Shapes.empty() : Block.box(0.0, 0.0, 0.0, 16.0, px, 16.0);
      }
   }

   /** the collision of a snow layer: soft, and nothing at all for snow lying on a full block of snow */
   public static VoxelShape collision(BlockState state, BlockGetter level, BlockPos pos) {
      if (level != null && pos != null) {
         BlockState below = level.getBlockState(pos.below());
         if (below.getBlock() instanceof SnowLayerBlock && below.getValue(SnowLayerBlock.LAYERS) == 8) {
            return Shapes.empty();
         }
      }
      return FIRM[state.getValue(SnowLayerBlock.LAYERS)];
   }

   private static final VoxelShape[] SURFACE = new VoxelShape[9];

   static {
      SURFACE[0] = Shapes.empty();
      for (int l = 1; l <= 8; l++) {
         SURFACE[l] = Block.box(0.0, 0.0, 0.0, 16.0, l * 2.0, 16.0);
      }
   }

   /** [1.2.0] the snow's whole visible height (what a toboggan rides on) */
   public static VoxelShape surfaceShape(BlockState state) {
      return SURFACE[state.getValue(SnowLayerBlock.LAYERS)];
   }

   static boolean snow(BlockState s) {
      return s.getBlock() instanceof SnowLayerBlock;
   }

   static int layers(BlockState s) {
      return snow(s) ? s.getValue(SnowLayerBlock.LAYERS) : 0;
   }

   /** the top snow block of the pile containing (or just above) pos, or null */
   public static BlockPos top(BlockGetter level, BlockPos pos) {
      BlockPos.MutableBlockPos m = pos.mutable();
      if (!snow(level.getBlockState(m))) {
         m.move(0, -1, 0);
         if (!snow(level.getBlockState(m))) {
            return null;
         }
      }
      while (layers(level.getBlockState(m)) == 8 && snow(level.getBlockState(m.above()))) {
         m.move(0, 1, 0);
      }
      return m.immutable();
   }

   /** snow surface height above the block the pile starts in, and that block (for wading) */
   static double surface(BlockGetter level, BlockPos feet) {
      BlockPos t = top(level, feet);
      if (t == null) {
         t = top(level, feet.above());
         if (t == null) {
            return Double.NaN;
         }
      }
      return t.getY() + layers(level.getBlockState(t)) / 8.0;
   }

   // ------------------------------------------------------------------------------------------------ depth

   /** how deep (in layers) snow should lie in this column while it snows */
   static int target(ServerLevel level, BlockPos pos, Biome biome) {
      double depth = 3.0;
      if (level.isThundering()) {
         depth += 1.5;
      }
      depth += Mth.clamp((pos.getY() - 80) / 22.0, 0.0, 4.0);
      float temp = biome.getBaseTemperature();
      if (temp < -0.2F) {
         depth += 2.0;
      } else if (temp < 0.1F) {
         depth += 1.0;
      }
      // wind: drifts pile in the lee of whatever stands upwind; ridges and windward faces are scoured
      float we = SeasonalWeather.windEast(level), ws = SeasonalWeather.windSouth(level);
      double len = Math.sqrt(we * we + ws * ws);
      int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
      if (len > 0.5) {
         double ux = -we / len, uz = -ws / len; // toward where the wind comes from
         double shelter = 0.0;
         for (int d = 1; d <= 5; d++) {
            int x = Mth.floor(pos.getX() + 0.5 + ux * d), z = Mth.floor(pos.getZ() + 0.5 + uz * d);
            if (!level.hasChunk(x >> 4, z >> 4)) {
               break;
            }
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - ground;
            if (h >= 2) {
               shelter = Math.max(shelter, Math.min(h, 4) * 1.6 * (1.0 - (d - 1) / 5.0));
            } else if (d <= 2 && h <= -2) {
               depth -= 1.5; // exposed lip or ridge
            }
         }
         depth += shelter * Mth.clamp(len / 6.0, 0.4, 1.0);
      }
      // a conifer crown overhead holds much of it
      for (int dy = 2; dy <= 14; dy++) {
         BlockState over = level.getBlockState(pos.above(dy));
         if (over.getBlock() instanceof LeavesBlock) {
            depth -= 2.0;
            break;
         }
      }
      // natural unevenness
      long h = pos.getX() * 341873128712L ^ pos.getZ() * 132897987541L;
      depth += ((h >>> 13) & 7) / 7.0 - 0.5;
      return Mth.clamp((int)Math.round(depth), 1, MAX_LAYERS);
   }

   /** one more layer toward the column's depth; called from the precipitation sample while it snows */
   public static void accumulate(ServerLevel level, BlockPos at, Biome biome) {
      BlockPos t = top(level, at);
      if (t == null) {
         return;
      }
      int have = 0;
      for (BlockPos.MutableBlockPos m = t.mutable(); snow(level.getBlockState(m)) && have < 32; m.move(0, -1, 0)) {
         have += layers(level.getBlockState(m));
      }
      if (have >= target(level, t, biome)) {
         return;
      }
      BlockState s = level.getBlockState(t);
      int l = layers(s);
      if (l < 8) {
         BlockState grown = s.setValue(SnowLayerBlock.LAYERS, l + 1);
         level.setBlockAndUpdate(t, grown);
      } else {
         BlockPos up = t.above();
         if (level.getBlockState(up).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, up)) {
            level.setBlockAndUpdate(up, Blocks.SNOW.defaultBlockState());
         }
      }
   }

   /** spring: the top layer of the pile thaws */
   public static boolean meltTop(ServerLevel level, BlockPos at) {
      BlockPos t = top(level, at);
      if (t == null) {
         return false;
      }
      BlockState s = level.getBlockState(t);
      int l = layers(s);
      if (l > 1) {
         level.setBlockAndUpdate(t, s.setValue(SnowLayerBlock.LAYERS, l - 1));
         return true;
      }
      return false;
   }

   // ------------------------------------------------------------------------------------------------ wading

   /** depth of snow above the feet, blocks (0 when not in snow) */
   public static double wade(Entity e) {
      Level level = e.level();
      BlockPos feet = BlockPos.containing(e.getX(), e.getY() + 0.01, e.getZ());
      double top = surface(level, feet);
      if (Double.isNaN(top)) {
         return 0.0;
      }
      return Math.max(0.0, top - e.getY());
   }

   static final net.minecraft.resources.ResourceLocation BOG = FrontierHunts.id("deep_snow");

   /** slow down in snow: knee deep 0.25 -> ~80 %, thigh deep 0.6 -> ~55 %, a drift 1.2 -> ~15 %, and jumps shrink */
   static void bog(LivingEntity e, double wade) {
      double speed = 0.0, jump = 0.0, step = 0.0;
      if (e instanceof Player p && wade >= 0.05) {
         // [1.2.5] walking in snow is real work: even ankle-deep powder costs a quarter of your pace, knee deep about
         // half, a full block of drift leaves a third, and sprinting through it barely helps
         double f = 1.0 - 0.16 - wade * 0.85;
         if (p.isSprinting()) {
            f -= 0.12;
         }
         speed = Mth.clamp(f, 0.12, 1.0) - 1.0;
         jump = wade > 0.9 ? -0.6 : wade > 0.5 ? -0.4 : wade > 0.25 ? -0.25 : -0.1;
      } else if (wade >= 0.08) {
         speed = Mth.clamp(1.0 - wade * 0.7, e instanceof Player ? 0.12 : 0.4, 1.0) - 1.0;
         // [1.1.3] jumps shrink, but you can always lunge out of a drift
         jump = wade > 0.9 ? -0.6 : wade > 0.5 ? -0.35 : wade > 0.25 ? -0.15 : 0.0;
      }
      if (wade >= 0.03) {
         // [1.1.3] you sink in, so a one-block rise in snow is a hump you wade up, not a wall: step up to the snow
         // on top of it (vanilla steps 0.6; the firm snow you stand on sits below the drawn surface)
         step = 0.7;
      }
      modify(e, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, speed);
      modify(e, net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH, jump);
      modify(e, net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT, step, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
   }

   private static void modify(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
      modify(e, attr, v, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
   }

   private static void modify(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v,
      net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
      var inst = e.getAttribute(attr);
      if (inst == null) {
         return;
      }
      var cur = inst.getModifier(BOG);
      if (Math.abs(v) < 1.0E-3) {
         if (cur != null) {
            inst.removeModifier(BOG);
         }
         return;
      }
      if (cur != null && Math.abs(cur.amount() - v) < 0.01) {
         return;
      }
      inst.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BOG, v, op));
   }

   /** the local player wades on its own client (movement is client side) */
   @SubscribeEvent
   public static void player(PlayerTickEvent.Pre event) {
      Player p = event.getEntity();
      if (p.level().isClientSide && p.isLocalPlayer()) {
         double w = p.getAbilities().flying || p.isSpectator() || p.getVehicle() != null ? 0.0 : wade(p);
         bog(p, w);
         if (w > 0.2 && p.getDeltaMovement().horizontalDistanceSqr() > 0.002 && p.getRandom().nextInt(3) == 0) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
            for (int i = 0; i < 3; i++) {
               p.level().addParticle(dust, p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.7, p.getY() + w * p.getRandom().nextDouble(),
                  p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.7, 0.0, 0.05, 0.0);
            }
         }
      }
   }

   private static final java.util.Map<Player, double[]> WALKED = new java.util.WeakHashMap<>();

   /** players tramp a trail on the server: a layer at a time where they keep walking */
   @SubscribeEvent
   public static void trail(PlayerTickEvent.Post event) {
      Player p = event.getEntity();
      if (p.level() instanceof ServerLevel sl && !p.isSpectator()) {
         // [1.1.3] the server moves the player the same way the client does (step height above all), so it never
         // pulls a player back who waded up a snowy step
         double w = p.getAbilities().flying || p.getVehicle() != null ? 0.0 : wade(p);
         bog(p, w);
         // [1.2.5] ploughing through snow tires you out
         double[] last = WALKED.computeIfAbsent(p, k -> new double[]{p.getX(), p.getZ()});
         double dx = p.getX() - last[0], dz = p.getZ() - last[1];
         last[0] = p.getX();
         last[1] = p.getZ();
         if (w >= 0.05 && !p.isCreative() && dx * dx + dz * dz > 0.001 && dx * dx + dz * dz < 4.0) {
            p.causeFoodExhaustion((float)(0.006 + w * 0.012) * (p.isSprinting() ? 1.6F : 1.0F));
         }
         if (p.getVehicle() == null && (p.tickCount & 3) == 0) {
            tramp(sl, p, 0.18);
         }
      }
   }

   /** animals flounder and tramp too (server) */
   @SubscribeEvent
   public static void mob(EntityTickEvent.Pre event) {
      if (event.getEntity() instanceof LivingEntity e && !(e instanceof Player) && !e.level().isClientSide && (e.tickCount & 1) == 0) {
         double w = e.isNoGravity() || e.isPassenger() ? 0.0 : wade(e);
         bog(e, w);
         if (w > 0.25 && (e.tickCount & 7) == 0 && e.level() instanceof ServerLevel sl) {
            tramp(sl, e, 0.08);
         }
      }
   }

   private static void tramp(ServerLevel level, Entity e, double chance) {
      double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
      if (dx * dx + dz * dz < 0.0025 || level.random.nextDouble() > chance) {
         return;
      }
      BlockPos t = top(level, BlockPos.containing(e.getX(), e.getY() + 0.01, e.getZ()));
      if (t == null) {
         t = top(level, BlockPos.containing(e.getX(), e.getY() + 1.01, e.getZ()));
      }
      if (t == null) {
         return;
      }
      int total = 0;
      for (BlockPos.MutableBlockPos m = t.mutable(); snow(level.getBlockState(m)) && total < 32; m.move(0, -1, 0)) {
         total += layers(level.getBlockState(m));
      }
      if (total <= 2) {
         return; // packed down to a firm trail
      }
      BlockState s = level.getBlockState(t);
      int l = layers(s);
      level.setBlock(t, l > 1 ? s.setValue(SnowLayerBlock.LAYERS, l - 1) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
   }
}
