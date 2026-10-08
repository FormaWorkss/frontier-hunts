package com.formaworks.frontierhunts.sticks;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.rifle.RifleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * [sticks] Shooting sticks: a three-legged rest you set up on the ground and lay a gun in.
 *
 * <p>Use the item on the ground to set the tripod up where you clicked (sneak while placing: kneeling height).
 * Use the placed sticks with an empty hand (or any non-gun item) to change their height: standing, kneeling, sitting.
 * Walk up holding a rifle, shotgun or handgun and use them: the forend goes into the padded yoke and you settle in
 * behind it ({@link ShootingSticksEntity} carries you like a seat, so you can't drift off it). Rested, the hold is
 * almost still - a slight breathing bob and the pulse at high power - the wind no longer pushes the muzzle, the kick
 * is smaller and the gun settles back into the yoke, the shot groups tighter. You swivel the gun about the yoke
 * through a natural arc and tilt it within limits; past the arc the turn stiffens and stops. Sneak, jump, step off
 * (any movement key) or put the gun away to come off the sticks. Sneak + use, or a punch, folds them back into the
 * item.
 *
 * <p>This class is the shared rule book (both sides, no client types): what can be rested, the geometry per height,
 * the hold factors other systems ask for, the soft arc limit, and the placement from the item.
 */
public final class ShootingSticks {
   private ShootingSticks() {
   }

   /**
    * The three working heights. {@code contact}: the yoke's cradle above the ground (blocks); {@code lower}: how far
    * the shooter's feet sit below the ground line in this pose (kneeling / sitting bodies are drawn lower);
    * {@code reach}: horizontal distance from the yoke to the shooter's body centre; {@code up / down}: how far the
    * gun tilts above / below level on the yoke (degrees).
    */
   public enum Height {
      STANDING("standing", 1.445, 0.0, 0.95, 20.0F, 24.0F),
      KNEELING("kneeling", 1.025, 0.42, 0.95, 24.0F, 20.0F),
      SITTING("sitting", 0.825, 0.62, 1.0, 28.0F, 16.0F);

      private static final Height[] VALUES = values();
      public final String key;
      public final double contact;
      public final double lower;
      public final double reach;
      public final float up;
      public final float down;

      Height(String key, double contact, double lower, double reach, float up, float down) {
         this.key = key;
         this.contact = contact;
         this.lower = lower;
         this.reach = reach;
         this.up = up;
         this.down = down;
      }

      public static Height byId(int id) {
         return VALUES[Math.floorMod(id, VALUES.length)];
      }

      public Height next() {
         return VALUES[(this.ordinal() + 1) % VALUES.length];
      }

      /** Length of each leg from the hinge to the ground (the hub sits {@link #HUB_BELOW} under the cradle). */
      public double legLength() {
         return (this.contact - HUB_BELOW) / Math.cos(Math.toRadians(SPLAY));
      }
   }

   // ------------------------------------------------------------------------------------------- geometry

   /** Legs splay this far from vertical when set up (degrees). */
   public static final double SPLAY = 19.0;
   /** The leg hinges sit this far below the bottom of the cradle's V. */
   public static final double HUB_BELOW = 0.075;
   /** Free swivel each way about the yoke (degrees) before anything nearby narrows it. */
   public static final float ARC = 40.0F;
   /** The last part of every limit (degrees) where the turn stiffens before it stops. */
   public static final float SOFT = 9.0F;
   /** Ticks to settle in behind the sticks (body moves into place, the view eases into the limits). */
   public static final int SETTLE_TICKS = 8;

   // ------------------------------------------------------------------------------------------- hold factors

   /** Scope hold sway on the sticks, as multiples of the standing hold: slow drift and breathing (x standing). */
   public static final double DRIFT = 0.05;
   public static final double BREATH = 0.2;
   /** Pulse in the reticle (only rested shooters feel it: everything else is gone). */
   public static final double PULSE = 1.0;
   /** Spread multiplier for field guns rested on the sticks (a bipod is 0.45). */
   public static final float SPREAD = 0.5F;
   /** Ridgeline cone (aimed / not aimed) on the sticks; standing aimed is 0.035, prone 0.012, bipod 0.008. */
   public static final float RIDGELINE_AIMED = 0.012F;
   public static final float RIDGELINE_HIP = 0.42F;
   /** Felt recoil on the sticks (camera kick) and the part of the muzzle climb that stays after the gun settles. */
   public static final float RECOIL = 0.55F;
   public static final float CLIMB = 0.3F;
   /** Shivering / exhaustion shake that still reaches the reticle through the rest. */
   public static final float SHAKE = 0.35F;

   // ------------------------------------------------------------------------------------------- queries

   /** A gun that sits in the yoke: the Ridgeline and every field firearm except the flare gun and bait launcher. */
   public static boolean supports(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      if (stack.getItem() instanceof RifleItem) {
         return true;
      }
      return stack.getItem() instanceof ExpeditionWeapon w && !w.weapon.bow && w.weapon != Weapon.FLARE_GUN && w.weapon != Weapon.BAIT_LAUNCHER;
   }

   /** The sticks {@code e} is resting on, or null. Both sides. */
   public static ShootingSticksEntity sticks(Entity e) {
      return e != null && e.getVehicle() instanceof ShootingSticksEntity s ? s : null;
   }

   /** {@code e} has a gun resting on shooting sticks. Both sides. */
   public static boolean rested(Entity e) {
      return e != null && e.getVehicle() instanceof ShootingSticksEntity;
   }

   /**
    * One step of the soft limit: from the offset shown last time ({@code prev}) towards the one asked for
    * ({@code want}), inside {@code [lo, hi]}. Movement towards a limit stiffens through its last {@link #SOFT} degrees
    * (the closer, the less of each turn gets through), movement back is free, and nothing ever ends up outside.
    */
   public static float soft(float prev, float want, float lo, float hi) {
      if (!Float.isFinite(prev) || !Float.isFinite(want)) {
         return Mth.clamp(Float.isFinite(want) ? want : 0.0F, lo, hi);
      }
      float delta = want - prev;
      if (delta > 0.0F && prev > hi - SOFT) {
         float room = Mth.clamp((hi - prev) / SOFT, 0.0F, 1.0F);
         delta *= room * (float)Math.sqrt(room);
      } else if (delta < 0.0F && prev < lo + SOFT) {
         float room = Mth.clamp((prev - lo) / SOFT, 0.0F, 1.0F);
         delta *= room * (float)Math.sqrt(room);
      }
      return Mth.clamp(prev + delta, lo, hi);
   }

   /** Smoothstep 0..1. */
   public static float ease(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   // ------------------------------------------------------------------------------------------- placement

   /**
    * The item used on a block: sets the tripod up on top of it, facing the way the player looks (one leg out front,
    * two either side of the shooter). Sneaking places them at kneeling height. The item is used up (kept in
    * creative) and lives inside the sticks until they are folded, so a renamed or worn pair comes back as it went.
    */
   public static InteractionResult place(UseOnContext ctx) {
      Level level = ctx.getLevel();
      Player player = ctx.getPlayer();
      if (ctx.getClickedFace() != Direction.UP) {
         if (player != null && level.isClientSide) {
            player.displayClientMessage(Component.translatable("sticks.frontierhunts.need_ground").withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      Vec3 at = ctx.getClickLocation();
      BlockPos below = ctx.getClickedPos();
      if (!ShootingSticksEntity.supported(level, below, at.y)) {
         return InteractionResult.FAIL;
      }
      Height h = player != null && player.isSecondaryUseActive() ? Height.KNEELING : Height.STANDING;
      AABB box = ShootingSticksEntity.box(at, h);
      if (!level.noCollision(box) || !level.getEntitiesOfClass(ShootingSticksEntity.class, box.inflate(0.45)).isEmpty()) {
         if (player != null && level.isClientSide) {
            player.displayClientMessage(Component.translatable("sticks.frontierhunts.no_room").withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResult.FAIL;
      }
      if (level instanceof ServerLevel sl) {
         ShootingSticksEntity s = SticksContent.STICKS.get().create(sl);
         if (s == null) {
            return InteractionResult.FAIL;
         }
         float yaw = player != null ? player.getYRot() : ctx.getRotation();
         ItemStack held = ctx.getItemInHand();
         s.setUp(at, yaw, h, held.copyWithCount(1), sl.getGameTime());
         if (!sl.addFreshEntity(s)) {
            return InteractionResult.FAIL;
         }
         if (player == null || !player.hasInfiniteMaterials()) {
            held.shrink(1);
         }
         sl.playSound(null, at.x, at.y + 0.6, at.z, SticksContent.DEPLOY.get(), SoundSource.PLAYERS, 0.85F, 0.96F + sl.random.nextFloat() * 0.08F);
         s.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_PLACE, player);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
