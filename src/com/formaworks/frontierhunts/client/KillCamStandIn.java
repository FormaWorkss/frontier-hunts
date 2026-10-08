package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only double of the shot animal. It is a real instance of the same entity type (same renderer, species, traits,
 * antlers and coat, copied from the synced data of the real animal) added to the client level under a negative id so it
 * goes through the normal entity pipeline (shaders, shadows, lighting). It never ticks on its own: the kill cam drives
 * its clock, so walking, the hit reaction and the collapse all play in true slow motion.
 */
final class KillCamStandIn {
   private static int nextId = -1_900_000_000;
   private static List<AgeKey> whitetailAges;
   private static List<Field> whitetailFields;
   private static List<Field> wildlifeFields;
   private static Field walkSpeedOld;
   private static Field walkSpeed;
   private static Field walkPosition;
   private static Field travel;
   private static Field travelOld;
   private static Field motionSpeed;
   private static Field motionSpeedOld;
   private static boolean reflected;

   final LivingEntity entity;
   final boolean deer;
   private final ClientLevel level;
   private final long g0;
   private final int baseTick;
   private final long[] ageBase;
   private int steppedTo;
   /** [archery2] The real animal's impact marks when the replay began (to tell the kill shot's new mark apart). */
   private net.minecraft.nbt.CompoundTag marksAtStart;
   private boolean dropped;
   private int dropTick;

   private record AgeKey(EntityDataAccessor<Long> key, String name) {
   }

   private KillCamStandIn(LivingEntity entity, ClientLevel level, int baseTick) {
      this.entity = entity;
      this.deer = entity instanceof Whitetail;
      this.level = level;
      this.g0 = level.getGameTime();
      this.baseTick = baseTick;
      List<AgeKey> ages = this.deer ? whitetailAges : List.of();
      this.ageBase = new long[ages.size()];
      for (int i = 0; i < ages.size(); i++) {
         this.ageBase[i] = entity.getEntityData().get(ages.get(i).key);
      }
   }

   static KillCamStandIn create(LivingEntity real, ClientLevel level) {
      reflect();
      try {
         if (!(real.getType().create(level) instanceof LivingEntity copy)) {
            return null;
         }
         copy.setId(nextId--);
         if (nextId > -1_000_000_000) {
            nextId = -1_900_000_000;
         }
         List<SynchedEntityData.DataValue<?>> values = real.getEntityData().getNonDefaultValues();
         if (values != null) {
            copy.getEntityData().assignValues(values);
         }
         copyFields(real, copy, real instanceof Whitetail ? whitetailFields : real instanceof WildlifeMob ? wildlifeFields : List.of());
         copy.moveTo(real.getX(), real.getY(), real.getZ(), real.getYRot(), real.getXRot());
         copy.setOldPosAndRot();
         copy.yBodyRot = copy.yBodyRotO = real.yBodyRot;
         copy.yHeadRot = copy.yHeadRotO = real.yHeadRot;
         copy.setOnGround(real.onGround());
         copy.tickCount = real.tickCount;
         copy.hurtTime = 0;
         copy.deathTime = 0;
         copy.noPhysics = true;
         copy.setSilent(true);
         copyWalk(real, copy);
         KillCamStandIn s = new KillCamStandIn(copy, level, real.tickCount);
         if (real instanceof Whitetail rw) {
            s.marksAtStart = rw.impactMarks() == null ? null : rw.impactMarks().copy(); // [archery2]
         }
         level.addEntity(copy);
         s.warm();
         return s;
      } catch (RuntimeException | LinkageError ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: could not build the animal double", ex);
         return null;
      }
   }

   /** Settle the renderer's smoothed animation weights so the double does not blend in from a rest pose. */
   private void warm() {
      if (this.deer) {
         Whitetail w = (Whitetail)this.entity;
         for (int i = 12; i >= 0; i--) {
            w.tickCount = this.baseTick - i;
            WhitetailRenderer.pose(w, 0.0F);
         }
         w.tickCount = this.baseTick;
      }
   }

   /** Place the double (pre-impact pose from the server snapshot). */
   void place(Vec3 pos, float bodyYaw, float headYaw, float pitch) {
      LivingEntity e = this.entity;
      e.setPos(pos.x, pos.y, pos.z);
      e.xo = e.xOld = pos.x;
      e.yo = e.yOld = pos.y;
      e.zo = e.zOld = pos.z;
      e.setYRot(bodyYaw);
      e.yRotO = bodyYaw;
      e.yBodyRot = e.yBodyRotO = bodyYaw;
      e.yHeadRot = e.yHeadRotO = headYaw;
      e.setXRot(pitch);
      e.xRotO = pitch;
   }

   /** Hit: arm the hit reaction and the drop at virtual tick {@code vTick}; copy the server's hit state from the real animal. */
   void hit(int vTick, LivingEntity real) {
      if (this.dropped) {
         return;
      }
      this.dropped = true;
      this.dropTick = vTick;
      try {
         if (this.entity instanceof Whitetail w) {
            long at = this.g0 + vTick;
            if (real instanceof Whitetail r && !r.isRemoved()) {
               copyData(r, w, "IMPACTS");
               this.arrowMark(r, false); // [archery2] the replay's own arrow is sinking in: no second shaft yet
               copyData(r, w, "FALL_SIDE");
               copyData(r, w, "FALL_SPEED");
               copyData(r, w, "HIT_KIND");
            }
            setAge(w, "HIT_AT", at);
            setAge(w, "DOWN_AT", at);
            setData(w, "GRAZING", Boolean.FALSE);
            setData(w, "DOWN", Boolean.TRUE);
            if (motionSpeed != null) {
               // freeze the stride where the bullet struck; the collapse animation takes over from here
               motionSpeed.setFloat(w, motionSpeed.getFloat(w) * 0.35F);
               motionSpeedOld.setFloat(w, motionSpeed.getFloat(w));
            }
         } else {
            this.entity.setHealth(0.0F);
            this.entity.hurtTime = 10;
            this.entity.hurtDuration = 10;
         }
      } catch (ReflectiveOperationException | RuntimeException ex) {
         LogUtils.getLogger().debug("Frontier Hunts kill cam: partial hit state copy", ex);
      }
   }

   /**
    * [archery2] The kill shot's arrow on the double: its impact mark (bone-anchored, so it rides the collapse exactly
    * where the arrow struck) is the newest mark once the server's hit has synced. While the replay's own arrow sinks in,
    * the mark's shaft is hidden (no doubled arrow); {@code show} re-copies the marks from the real animal and reveals
    * it. Returns whether the double now carries the shot's arrow mark. The real animal's tag is never modified.
    */
   boolean arrowMark(LivingEntity real, boolean show) {
      if (!(this.entity instanceof Whitetail w)) {
         return false;
      }
      try {
         net.minecraft.nbt.CompoundTag src = real instanceof Whitetail r && !r.isRemoved() ? r.impactMarks() : w.impactMarks();
         if (src == null || src.equals(this.marksAtStart)) {
            return false;
         }
         int n = Math.clamp(src.getInt("count"), 0, 5);
         if (n == 0 || !src.getBoolean("arrow" + (n - 1))) {
            return false;
         }
         net.minecraft.nbt.CompoundTag copy = src.copy();
         copy.putBoolean("arrow" + (n - 1), show);
         setData(w, "IMPACTS", copy);
         return true;
      } catch (ReflectiveOperationException | RuntimeException ex) {
         return false;
      }
   }

   boolean dropped() {
      return this.dropped;
   }

   /**
    * Drive the double to virtual time {@code v} (ticks since the kill cam began, fractional). Returns the partial tick the
    * renderer must use for it this frame.
    */
   float drive(double v) {
      int vi = (int)Math.floor(Math.max(0.0, v));
      float frac = (float)(Math.max(0.0, v) - vi);
      LivingEntity e = this.entity;
      // [bugs] bounded catch-up: after a long hitch (or an absurd virtual time) skip ahead instead of stalling the frame
      for (int guard = 0; this.steppedTo < vi && guard < 400; guard++) {
         this.steppedTo++;
         this.virtualTick();
      }
      if (this.steppedTo < vi) {
         this.steppedTo = vi;
      }
      e.tickCount = this.baseTick + vi;
      long shift = this.level.getGameTime() - this.g0 - vi;
      if (this.deer && whitetailAges != null) {
         Whitetail w = (Whitetail)e;
         for (int i = 0; i < whitetailAges.size(); i++) {
            AgeKey k = whitetailAges.get(i);
            long base = this.ageBase[i];
            if (this.dropped && (k.name.equals("HIT_AT") || k.name.equals("DOWN_AT"))) {
               base = this.g0 + this.dropTick;
            }
            if (base > 0L) {
               w.getEntityData().set(k.key, base + shift);
            }
         }
      } else if (this.dropped) {
         e.deathTime = Mth.clamp(vi - this.dropTick + 1, 1, 19);
         e.hurtTime = Math.max(0, 10 - (vi - this.dropTick));
      }
      return frac;
   }

   /** Things the entity's own tick would have advanced once per (virtual) tick. */
   private void virtualTick() {
      LivingEntity e = this.entity;
      try {
         if (e instanceof Whitetail w && travel != null) {
            float speed = motionSpeed.getFloat(w);
            travelOld.setFloat(w, travel.getFloat(w));
            travel.setFloat(w, travel.getFloat(w) + speed);
         } else if (!this.dropped) {
            e.walkAnimation.update(0.0F, 0.0F);
         }
      } catch (ReflectiveOperationException ex) {
         travel = null;
      }
   }

   /** Move with the animal's own drift, keeping the previous-position fields equal so interpolation is exact. */
   void moveTo(Vec3 pos, float bodyYaw) {
      LivingEntity e = this.entity;
      e.setPos(pos.x, pos.y, pos.z);
      e.xo = e.xOld = pos.x;
      e.yo = e.yOld = pos.y;
      e.zo = e.zOld = pos.z;
      e.yBodyRot = e.yBodyRotO = bodyYaw;
      e.setYRot(bodyYaw);
      e.yRotO = bodyYaw;
   }

   void remove() {
      if (!this.entity.isRemoved()) {
         this.level.removeEntity(this.entity.getId(), Entity.RemovalReason.DISCARDED);
      }
   }

   // ------------------------------------------------------------------ reflection helpers (mod classes keep their names)

   private static synchronized void reflect() {
      if (reflected) {
         return;
      }
      reflected = true;
      whitetailAges = new ArrayList<>();
      try {
         for (Field f : Whitetail.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == EntityDataAccessor.class && f.getName().endsWith("_AT")) {
               f.setAccessible(true);
               @SuppressWarnings("unchecked")
               EntityDataAccessor<Long> key = (EntityDataAccessor<Long>)f.get(null);
               whitetailAges.add(new AgeKey(key, f.getName()));
            }
         }
      } catch (ReflectiveOperationException | RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: deer clock keys unavailable", ex);
         whitetailAges = new ArrayList<>();
      }
      whitetailFields = stateFields(Whitetail.class);
      wildlifeFields = stateFields(WildlifeMob.class);
      try {
         travel = Whitetail.class.getDeclaredField("travel");
         travelOld = Whitetail.class.getDeclaredField("travelOld");
         motionSpeed = Whitetail.class.getDeclaredField("motionSpeed");
         motionSpeedOld = Whitetail.class.getDeclaredField("motionSpeedOld");
         travel.setAccessible(true);
         travelOld.setAccessible(true);
         motionSpeed.setAccessible(true);
         motionSpeedOld.setAccessible(true);
      } catch (ReflectiveOperationException | RuntimeException ex) {
         travel = null;
         motionSpeed = null;
      }
      try {
         Class<?> w = net.minecraft.world.entity.WalkAnimationState.class;
         walkSpeedOld = w.getDeclaredField("speedOld");
         walkSpeed = w.getDeclaredField("speed");
         walkPosition = w.getDeclaredField("position");
         walkSpeedOld.setAccessible(true);
         walkSpeed.setAccessible(true);
         walkPosition.setAccessible(true);
      } catch (ReflectiveOperationException | RuntimeException ex) {
         walkPosition = null;
      }
   }

   /** Plain per-instance animation state (numbers and small float arrays) worth carrying over to the double. */
   private static List<Field> stateFields(Class<?> c) {
      List<Field> out = new ArrayList<>();
      for (Field f : c.getDeclaredFields()) {
         int m = f.getModifiers();
         if (Modifier.isStatic(m)) {
            continue;
         }
         Class<?> t = f.getType();
         boolean simple = t == float.class || t == int.class || t == boolean.class || t == double.class || t == long.class;
         boolean array = t == float[].class;
         if (!simple && !array || Modifier.isFinal(m) && !array) {
            continue;
         }
         try {
            f.setAccessible(true);
            out.add(f);
         } catch (RuntimeException ignored) {
         }
      }
      return out;
   }

   private static void copyFields(Entity from, Entity to, List<Field> fields) {
      for (Field f : fields) {
         try {
            if (f.getType() == float[].class) {
               float[] src = (float[])f.get(from);
               float[] dst = (float[])f.get(to);
               if (src != null && dst != null && src.length == dst.length) {
                  System.arraycopy(src, 0, dst, 0, src.length);
               }
            } else {
               f.set(to, f.get(from));
            }
         } catch (ReflectiveOperationException | RuntimeException ignored) {
         }
      }
   }

   private static void copyWalk(LivingEntity from, LivingEntity to) {
      if (walkPosition == null) {
         to.walkAnimation.setSpeed(from.walkAnimation.speed());
         return;
      }
      try {
         walkSpeedOld.setFloat(to.walkAnimation, walkSpeedOld.getFloat(from.walkAnimation));
         walkSpeed.setFloat(to.walkAnimation, walkSpeed.getFloat(from.walkAnimation));
         walkPosition.setFloat(to.walkAnimation, walkPosition.getFloat(from.walkAnimation));
      } catch (ReflectiveOperationException ex) {
         to.walkAnimation.setSpeed(from.walkAnimation.speed());
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> EntityDataAccessor<T> key(String name) throws ReflectiveOperationException {
      Field f = Whitetail.class.getDeclaredField(name);
      f.setAccessible(true);
      return (EntityDataAccessor<T>)f.get(null);
   }

   private static <T> void copyData(Whitetail from, Whitetail to, String name) throws ReflectiveOperationException {
      EntityDataAccessor<T> k = key(name);
      to.getEntityData().set(k, from.getEntityData().get(k));
   }

   private static <T> void setData(Whitetail to, String name, T value) throws ReflectiveOperationException {
      EntityDataAccessor<T> k = key(name);
      to.getEntityData().set(k, value);
   }

   private static void setAge(Whitetail to, String name, long value) throws ReflectiveOperationException {
      EntityDataAccessor<Long> k = key(name);
      to.getEntityData().set(k, value);
   }
}
