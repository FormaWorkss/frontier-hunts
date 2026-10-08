package com.formaworks.frontierhunts.outfitter;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * [1.1.7] Gear the outfitter's counter sells and nobody can make: each does one real job, none of them wins a hunt by
 * itself.
 * <ul>
 * <li><b>Blood-Tracking Lamp</b> (held): blood within 20 m shows up bright and true-red, even old drops and at night -
 * the filtered light real trackers use. It finds blood; it doesn't find the deer.</li>
 * <li><b>Doe Estrus Lure</b> (one bottle): set a scent wick where you stand. During the whitetail rut, bucks within 64 m
 * that catch it come to check it over the next ten minutes. Outside the rut they ignore it (the bottle isn't used).</li>
 * <li><b>Hand Warmers</b> (4 packs): ten minutes of warmth each (+6 C felt).</li>
 * <li><b>Thermos of Camp Coffee</b> (3 cups): a hot drink warms you right away and keeps the chill off for five minutes.</li>
 * <li><b>Shooting Sticks</b>: [sticks] a tripod you set up on the ground and rest a gun in (see
 * {@link com.formaworks.frontierhunts.sticks.ShootingSticks}). Made at the Frontier Workbench now, so the counter no
 * longer sells them.</li>
 * </ul>
 */
public class OutfitterItem extends Item {
   public static final String LAMP = "tracking_lamp", LURE = "estrus_lure", WARMERS = "hand_warmers", COFFEE = "coffee_thermos", STICKS = "shooting_sticks";
   /** [1.1.8] */
   public static final String MILKWEED = "milkweed_pods", TAPE = "flagging_tape";
   private final String id;

   public OutfitterItem(String id) {
      super(props(id));
      this.id = id;
   }

   private static Properties props(String id) {
      Properties p = new Properties();
      return switch (id) {
         case WARMERS -> p.durability(4);
         case MILKWEED -> p.durability(12);
         case TAPE -> p.durability(24);
         case COFFEE -> p.durability(3);
         case LAMP, STICKS -> p.stacksTo(1);
         default -> p.stacksTo(8);
      };
   }

   public String id() {
      return this.id;
   }

   // ============================================================================================ use

   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return this.id.equals(COFFEE) ? UseAnim.DRINK : UseAnim.NONE;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity e) {
      return this.id.equals(COFFEE) ? 32 : 0;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack st = player.getItemInHand(hand);
      switch (this.id) {
         case COFFEE -> {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(st);
         }
         case WARMERS -> {
            if (player instanceof ServerPlayer sp) {
               long until = level.getGameTime() + 12000L;
               sp.getPersistentData().putLong("frontierhunts_warmers", until);
               st.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
               sp.displayClientMessage(Component.literal("Hand warmers on · warm for ten minutes").withStyle(ChatFormatting.GOLD), true);
               level.playSound(null, sp.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            player.getCooldowns().addCooldown(this, 40);
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
         }
         case MILKWEED -> {
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
               Floaters.release(sl, sp.getEyePosition().add(sp.getLookAngle().scale(0.6)));
               st.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
               sl.playSound(null, sp.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 0.4F, 1.6F);
            }
            player.getCooldowns().addCooldown(this, 30);
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
         }
         case LURE -> {
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
               Rut.Phase ph = Rut.phase(GameSpecies.WHITETAIL, sl);
               if (ph == Rut.Phase.NONE || ph == Rut.Phase.POST_RUT) {
                  sp.displayClientMessage(Component.literal("Bucks ignore estrus scent outside the rut (" + ph.title + ")").withStyle(ChatFormatting.GRAY), true);
                  return InteractionResultHolder.fail(st);
               }
               Wicks.place(sl, sp, sp.position());
               if (!sp.hasInfiniteMaterials()) {
                  st.shrink(1);
               }
               sp.displayClientMessage(Component.literal("Scent wick set · bucks nearby may come to check it · watch the wind").withStyle(ChatFormatting.GOLD), true);
               sl.playSound(null, sp.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7F, 0.9F);
            }
            player.getCooldowns().addCooldown(this, 100);
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
         }
         default -> {
            return InteractionResultHolder.pass(st);
         }
      }
   }

   @Override
   public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
      if (this.id.equals(STICKS)) {
         return com.formaworks.frontierhunts.sticks.ShootingSticks.place(ctx); // [sticks] set the tripod up
      }
      if (!this.id.equals(TAPE)) {
         return net.minecraft.world.InteractionResult.PASS;
      }
      Level level = ctx.getLevel();
      BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
      net.minecraft.world.level.block.state.BlockState flag = com.formaworks.frontierhunts.range.RangeContent.flag();
      if (flag == null || !level.getBlockState(at).canBeReplaced() || !flag.canSurvive(level, at)) {
         return net.minecraft.world.InteractionResult.FAIL;
      }
      if (!level.isClientSide) {
         level.setBlock(at, flag, 3);
         level.playSound(null, at, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
         if (ctx.getPlayer() instanceof ServerPlayer sp) {
            ctx.getItemInHand().hurtAndBreak(1, sp, ctx.getHand() == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
               : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
         }
      }
      return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   public ItemStack finishUsingItem(ItemStack st, Level level, LivingEntity e) {
      if (this.id.equals(COFFEE) && e instanceof ServerPlayer sp) {
         sp.getPersistentData().putLong("frontierhunts_coffee", level.getGameTime() + 6000L);
         try {
            var s = com.formaworks.frontierhunts.survival.SurvivalService.state(sp);
            s.heat = Math.min(Math.max(s.heat, -100F) + 12F, 15F);
            com.formaworks.frontierhunts.survival.SurvivalService.forceSync(sp);
         } catch (RuntimeException ignored) {
         }
         st.hurtAndBreak(1, sp, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
         sp.displayClientMessage(Component.literal("Hot coffee · the chill is gone").withStyle(ChatFormatting.GOLD), true);
      }
      return st;
   }

   @Override
   public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      String[] lines = switch (this.id) {
         case LAMP -> new String[]{"Hold it: blood within 20 m shows bright red, even old drops and at night.", "Finds blood, not the deer."};
         case LURE -> new String[]{"Use during the whitetail rut: sets a scent wick where you stand.",
            "Bucks within 64 m may come to check it for ten minutes. Hunt it downwind."};
         case WARMERS -> new String[]{"Use: ten minutes of warmth (+6 C felt)."};
         case COFFEE -> new String[]{"Drink: warms you at once and keeps the chill off for five minutes."};
         case MILKWEED -> new String[]{"Use: a puff of milkweed fluff drifts off on the wind.", "Watch where it goes 30 m out: wind, swirls and thermals."};
         case TAPE -> new String[]{"Use on the ground: tie a strip of blaze-orange tape.", "Mark the last blood so you can find it again."};
         case STICKS -> null; // [sticks] lang lines, below
         default -> new String[0];
      };
      if (lines == null) { // [sticks] made at the Frontier Workbench, explained in the lang file
         for (int i = 1; i <= 4; i++) {
            tip.add(Component.translatable("item.frontierhunts.shooting_sticks.desc" + i).withStyle(i == 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
         }
         return;
      }
      for (String l : lines) {
         tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
      }
      tip.add(Component.translatable("item.frontierhunts.outfitter_only").withStyle(ChatFormatting.DARK_GREEN));
   }

   // ============================================================================================ effects other systems ask for

   /** survival: extra felt warmth (C) from hand warmers and coffee */
   public static float warmth(ServerPlayer p, long now) {
      float w = 0F;
      var d = p.getPersistentData();
      if (d.getLong("frontierhunts_warmers") > now) {
         w += 6F;
      }
      if (d.getLong("frontierhunts_coffee") > now) {
         w += 3F;
      }
      return w;
   }

   /**
    * [sticks] Was a flat -40% aim sway for sticks carried in the offhand; the sticks are set up on the ground and a gun
    * rests in them now (the scope sway reads {@link com.formaworks.frontierhunts.sticks.ShootingSticks} itself), so
    * carrying them changes nothing. Kept for callers that multiply by it (bows: 1).
    */
   public static float sticks(Player p) {
      return 1F;
   }

   /** {@code p} holds the tracking lamp in either hand */
   public static boolean lamp(Player p) {
      if (p == null) {
         return false;
      }
      for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
         if (s.getItem() instanceof OutfitterItem oi && oi.id.equals(LAMP)) {
            return true;
         }
      }
      return false;
   }

   // ============================================================================================ estrus scent wicks

   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Wicks {
      private Wicks() {
      }

      record Wick(String dim, Vec3 pos, long until, UUID owner, Set<UUID> came) {
      }

      private static final List<Wick> WICKS = new ArrayList<>();

      static synchronized void place(ServerLevel level, ServerPlayer p, Vec3 pos) {
         WICKS.removeIf(w -> w.owner.equals(p.getUUID()) && w.dim.equals(level.dimension().location().toString()) && w.pos.distanceToSqr(pos) < 400.0);
         WICKS.add(new Wick(level.dimension().location().toString(), pos, level.getGameTime() + 12000L, p.getUUID(), new HashSet<>()));
      }

      @SubscribeEvent
      public static void tick(LevelTickEvent.Post e) {
         if (!(e.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40L != 7L) {
            return;
         }
         synchronized (Wicks.class) {
            if (WICKS.isEmpty()) {
               return;
            }
            String dim = level.dimension().location().toString();
            long now = level.getGameTime();
            Rut.Phase ph = Rut.phase(GameSpecies.WHITETAIL, level);
            boolean rut = ph != Rut.Phase.NONE && ph != Rut.Phase.POST_RUT;
            for (Iterator<Wick> it = WICKS.iterator(); it.hasNext(); ) {
               Wick w = it.next();
               if (w.until < now || !rut) {
                  it.remove();
                  continue;
               }
               if (!w.dim.equals(dim) || !level.hasChunkAt(BlockPos.containing(w.pos))) {
                  continue;
               }
               if (level.random.nextInt(4) == 0) {
                  level.sendParticles(ParticleTypes.WHITE_ASH, w.pos.x, w.pos.y + 0.3, w.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
               }
               for (Whitetail d : level.getEntitiesOfClass(Whitetail.class, new net.minecraft.world.phys.AABB(w.pos, w.pos).inflate(64.0, 24.0, 64.0),
                  d -> d.species() == GameSpecies.WHITETAIL && !d.downed())) {
                  try {
                     if (!d.traits().buck() || w.came.contains(d.getUUID()) || level.random.nextInt(3) != 0) {
                        continue;
                     }
                  } catch (RuntimeException ex) {
                     continue;
                  }
                  w.came.add(d.getUUID());
                  d.respondToCall(w.pos);
               }
            }
         }
      }
   }

   // ============================================================================================ milkweed floaters

   /**
    * [1.1.8] A puff of milkweed fluff that rides the wind: each seed is carried by the reserve's wind, drifts down
    * slowly, and lifts on a warm afternoon (rising thermals) or sinks at dawn and dusk (cool air sliding downhill).
    * Players within 96 m see it, so it reads the air well past wind powder's few metres.
    */
   @EventBusSubscriber(modid = FrontierHunts.ID)
   public static final class Floaters {
      private Floaters() {
      }

      private static final class Seed {
         final String dim;
         double x, y, z;
         int age;
         final double jitter;

         Seed(String dim, Vec3 p, double jitter) {
            this.dim = dim;
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
            this.jitter = jitter;
         }
      }

      private static final List<Seed> SEEDS = new ArrayList<>();

      static synchronized void release(ServerLevel level, Vec3 at) {
         String dim = level.dimension().location().toString();
         for (int i = 0; i < 6 && SEEDS.size() < 240; i++) {
            SEEDS.add(new Seed(dim, at.add(level.random.nextGaussian() * 0.15, level.random.nextGaussian() * 0.1, level.random.nextGaussian() * 0.15),
               level.random.nextDouble() * 6.28));
         }
      }

      @SubscribeEvent
      public static void tick(LevelTickEvent.Post e) {
         if (!(e.getLevel() instanceof ServerLevel level)) {
            return;
         }
         synchronized (Floaters.class) {
            if (SEEDS.isEmpty()) {
               return;
            }
            String dim = level.dimension().location().toString();
            // wind in m/s -> blocks per tick; at least a breath of air so the fluff always moves a little
            double we = com.formaworks.frontierhunts.weather.SeasonalWeather.windEast(level);
            double ws = com.formaworks.frontierhunts.weather.SeasonalWeather.windSouth(level);
            long day = level.getDayTime() % 24000L;
            double thermal = day > 4000L && day < 10000L ? 0.012 : (day > 11500L && day < 13500L || day > 22500L || day < 1000L ? -0.010 : 0.0);
            List<ServerPlayer> near = level.players();
            for (Iterator<Seed> it = SEEDS.iterator(); it.hasNext(); ) {
               Seed s = it.next();
               if (!s.dim.equals(dim)) {
                  continue;
               }
               s.age++;
               double swirl = Math.sin(s.age * 0.11 + s.jitter) * 0.012;
               s.x += we / 20.0 * 0.9 + swirl;
               s.z += ws / 20.0 * 0.9 + Math.cos(s.age * 0.09 + s.jitter) * 0.012;
               s.y += -0.006 + thermal + Math.sin(s.age * 0.07 + s.jitter) * 0.004;
               BlockPos bp = BlockPos.containing(s.x, s.y, s.z);
               if (s.age > 360 || !level.hasChunkAt(bp) || !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                  it.remove();
                  continue;
               }
               if (s.age % 2 == 0) {
                  for (ServerPlayer p : near) {
                     if (p.distanceToSqr(s.x, s.y, s.z) < 96.0 * 96.0) {
                        level.sendParticles(p, ParticleTypes.WHITE_ASH, true, s.x, s.y, s.z, 2, 0.02, 0.02, 0.02, 0.0);
                     }
                  }
               }
            }
         }
      }
   }
}
