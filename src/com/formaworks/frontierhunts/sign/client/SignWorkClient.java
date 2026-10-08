package com.formaworks.frontierhunts.sign.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.FrontierGraphics;
import com.formaworks.frontierhunts.client.McAnimalPose;
import com.formaworks.frontierhunts.client.rutfight.BoxFightModels;
import com.formaworks.frontierhunts.client.tree.SignTrunkProbe;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import com.formaworks.frontierhunts.hunting.rutfight.UltraFightModels;
import com.formaworks.frontierhunts.sign.work.SignAct;
import com.formaworks.frontierhunts.sign.work.SignGeometry;
import com.formaworks.frontierhunts.sign.work.SignMotion;
import com.formaworks.frontierhunts.sign.work.SignPose;
import com.formaworks.frontierhunts.sign.work.SignShapes;
import com.formaworks.frontierhunts.sign.work.SignSounds;
import com.formaworks.frontierhunts.sign.work.SignWork;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [deersign] Client side of a buck making sign. Per frame: the exact head target (Ultra: the neck reach puts the rack on
 * the bark / the nose on the licking branch; Vanilla: the box head's pitch, yaw and drop) and the body slide that makes
 * the antlers of the model being drawn meet the bark of the trunk being drawn (a round Ultra stem or a block log) at the
 * height the rub is drawn. Per tick: the sounds and particles of every stroke, deterministic from the synced
 * {@link SignAct}, so every player hears and sees the same strokes in time with the animation.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class SignWorkClient {
   private static final Map<Integer, Worker> WORKERS = new HashMap<>();
   private static final Map<Integer, float[]> EVENTS = new HashMap<>();
   private static final Map<DeerTraits, CompletableFuture<FightModel>> ULTRA = new HashMap<>();
   private static final EnumMap<GameSpecies, Float> DROP_PER_PX = new EnumMap<>(GameSpecies.class);
   private static long tidyAt;

   private SignWorkClient() {
   }

   /** Render state of one working buck. */
   static final class Worker {
      SignAct act = SignAct.NONE;
      float weight;
      float last = Float.NaN;
      /** vertical correction of the head (entity blocks) so the antlers meet the bark at the rub's height */
      float corrY;
      /** drawn body slide (entity blocks): forward and sideways */
      float slideZ;
      float slideX;
      float slideW;
      /** where the contact should be this frame (entity y), NaN when not rubbing */
      float wantY = Float.NaN;
      float press;
      /** licking: where the nose should be, entity space */
      final Vector3f lickWant = new Vector3f(Float.NaN, 0.0F, 0.0F);
      final Vector3f lickCorr = new Vector3f();
      final Matrix4f target = new Matrix4f();
      long seen;
      long frameNs;
      boolean vanilla;
      FightModel model;
   }

   static boolean vanilla() {
      return FrontierGraphics.vanillaAnimals();
   }

   /** The model of the preset being drawn; Ultra builds off-thread (null until ready). */
   static FightModel model(DeerTraits t, boolean vanilla) {
      if (vanilla) {
         return BoxFightModels.of(t, true);
      }
      CompletableFuture<FightModel> f = ULTRA.get(t);
      if (f == null) {
         if (ULTRA.size() > 32) {
            ULTRA.clear();
         }
         f = CompletableFuture.supplyAsync(() -> UltraFightModels.of(t), Util.backgroundExecutor());
         ULTRA.put(t, f);
      }
      return f.isDone() && !f.isCompletedExceptionally() ? f.getNow(null) : null;
   }

   private static float approach(float a, float b, float rate, float dt) {
      return a + (b - a) * (1.0F - (float)Math.exp(-rate * dt));
   }

   // ------------------------------------------------------------------------------------------------ per frame

   /** Called from WhitetailRenderer.pose() after animatorInput (and the rut-fight head target). */
   public static void prepare(Whitetail e, DeerAnimator.Input in, float partial) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return;
      }
      tidy(level.getGameTime());
      SignAct act = SignWork.act(e);
      int id = e.getId();
      Worker w = WORKERS.get(id);
      boolean active = in.signPhase != 0 && act.active();
      if (!active && w == null) {
         return;
      }
      if (w == null) {
         w = new Worker();
         WORKERS.put(id, w);
      }
      float dt = Float.isNaN(w.last) ? 0.0F : Math.max(0.0F, Math.min(0.25F, in.time - w.last));
      w.last = in.time;
      w.seen = level.getGameTime();
      if (active) {
         w.act = act;
      }
      w.weight = active ? 1.0F : approach(w.weight, 0.0F, 5.0F, dt);
      if (!active) {
         if (w.weight < 0.01F || !w.act.active() || in.downed || in.bedded) {
            WORKERS.remove(id);
            return;
         }
         // fading out of the last phase (work done or broken off)
         float tick = w.act.total() - 1;
         int[] ph = w.act.at(tick);
         in.signPhase = ph[0];
         in.signAge = ph[1] / 20.0F;
         in.signDur = ph[2] / 20.0F;
         in.signSeed = w.act.seed();
         in.signW = w.weight;
         SignPose.apply(in);
      }
      if (rutFighting(in)) {
         return;
      }
      w.vanilla = vanilla();
      DeerTraits traits = e.traits();
      w.model = model(traits, w.vanilla);
      SignAct a = w.act;
      float tickNow = active ? (float)(level.getGameTime() - a.start()) + partial : a.total() - 1;
      switch (in.signPhase) {
         case SignAct.SNIFF_TREE, SignAct.RUB_STROKES -> rub(e, in, w, a, tickNow, level);
         case SignAct.LICK -> lick(e, in, w, a, tickNow, level, partial);
         default -> {
            w.wantY = Float.NaN;
            w.lickWant.x = Float.NaN;
         }
      }
   }

   private static boolean rutFighting(DeerAnimator.Input in) {
      return in.fightHead != null;
   }

   private static void rub(Whitetail e, DeerAnimator.Input in, Worker w, SignAct a, float tick, ClientLevel level) {
      w.lickWant.x = Float.NaN;
      DeerSign.Mark mark = level.getBlockEntity(a.sign()) instanceof DeerSign.Mark m ? m : null;
      float[] mo = new float[6];
      boolean strokes = in.signPhase == SignAct.RUB_STROKES;
      if (strokes) {
         SignMotion.rub(a.seed(), in.signAge, in.signDur, mo);
      } else {
         // smelling the tree: head low to the trunk, nose working up and down a little
         mo[SignMotion.V] = -0.35F + (float)Math.sin(in.signAge * 5.0F) * 0.08F;
         mo[SignMotion.PRESS] = 0.45F * SignMotion.smooth(0.0F, 0.6F, in.signAge);
      }
      float amp = SignWork.strokeAmp(e.traits());
      float centre = mark != null && mark.worked() ? (mark.lo + mark.hi) * 0.5F : 0.55F * e.traits().frameHeight();
      // the rub band is measured from the trunk base; the buck's feet may sit a little higher / lower (snow, a slab of ground)
      float base = mark != null && mark.tree != null ? mark.tree.getY() : a.sign().getY();
      w.wantY = (float)(base - e.getY()) + centre + mo[SignMotion.V] * amp;
      w.press = mo[SignMotion.PRESS];
      float wt = in.signW;
      float ts = SignGeometry.twistScale(e.species());
      if (w.vanilla) {
         // box head: the rigid head nods about its neck pivot so the rack tips follow the stroke (height correction
         // and stroke both as nod), and turns side to side
         float nod = Mth.clamp(-w.corrY * SignGeometry.BOX_NOD_PER_BLOCK, -1.0F, 1.0F);
         in.signPitch = nod * wt;
         in.signYaw = (mo[SignMotion.YAW] + mo[SignMotion.ROLL] * 0.5F) * ts * wt;
      } else if (w.model != null && w.model.hasAntlers()) {
         float up = (w.corrY + mo[SignMotion.V] * amp) / Math.max(0.1F, w.model.sy);
         SignGeometry.rubTarget(w.model, e.species(), up, mo[SignMotion.ROLL] * ts, mo[SignMotion.YAW] * ts, mo[SignMotion.V] * SignGeometry.strokeNod(e.species()), w.target);
         in.fightHead = w.target;
         in.fightReach = wt * (strokes ? 1.0F : 0.8F);
      }
   }

   private static void lick(Whitetail e, DeerAnimator.Input in, Worker w, SignAct a, float tick, ClientLevel level, float partial) {
      w.wantY = Float.NaN;
      DeerSign.Mark mark = level.getBlockEntity(a.sign()) instanceof DeerSign.Mark m ? m : null;
      BlockState st = level.getBlockState(a.sign());
      if (mark == null || !(st.getBlock() instanceof DeerSign)) {
         return;
      }
      float[] mo = new float[5];
      SignMotion.lick(a.seed(), in.signAge, in.signDur, mo);
      float wt = in.signW * SignMotion.envelope(in.signAge, in.signDur, 0.6F, 0.5F);
      if (w.vanilla) {
         in.signPitch = -(0.5F + mo[SignMotion.L_PITCH]) * wt;
         in.signYaw = mo[SignMotion.L_YAW] * wt;
         float perPx = dropPerPx(e.species(), e.traits());
         in.signDrop = perPx > 1.0E-4F ? Mth.clamp(-(0.08F + mo[SignMotion.L_UP]) / perPx, -3.0F, 0.0F) * wt : 0.0F;
         return;
      }
      if (w.model == null) {
         return;
      }
      DeerSkeleton sk = DeerSkeleton.of(e.species());
      // nose to the branch's chewed end, wherever this buck actually stands
      float[] tip = SignShapes.lickTip(mark, a.sign(), st.getValue(DeerSign.FACING));
      Vec3 p = e.getPosition(partial);
      float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
      float dx = (float)(tip[0] - p.x);
      float dz = (float)(tip[2] - p.z);
      float ex = dx * Mth.cos(yaw) + dz * Mth.sin(yaw);
      float ez = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
      float ey = (float)(tip[1] - 0.07 - p.y);
      Matrix4f base = SignGeometry.lickBase(sk, new Matrix4f());
      Vector3f nose = base.transformPosition(new Vector3f(SignGeometry.nose(w.model, sk)));
      Vector3f noseE = w.model.toEntity(nose, new Vector3f());
      // model-space shift that brings the nose to the tip (bounded: a long way off means the buck was shoved)
      Vector3f shift = new Vector3f(ex - noseE.x, ey - noseE.y, ez - noseE.z);
      if (shift.length() > 0.3F) {
         shift.normalize(0.3F);
      }
      Vector3f sm = w.model.toModel(shift, new Vector3f());
      SignGeometry.lickTarget(w.model, sk, mo[SignMotion.L_YAW], mo[SignMotion.L_PITCH], mo[SignMotion.L_ROLL], mo[SignMotion.L_UP] / Math.max(0.1F, w.model.sy),
         w.target);
      w.target.m30(w.target.m30() + sm.x).m31(w.target.m31() + sm.y).m32(w.target.m32() + sm.z);
      in.fightHead = w.target;
      in.fightReach = wt;
   }

   /** Entity blocks the Vanilla box head moves down per pixel of head drop. */
   private static float dropPerPx(GameSpecies s, DeerTraits t) {
      Float k = DROP_PER_PX.get(s);
      if (k == null) {
         DeerAnimator.Input in = new DeerAnimator.Input();
         in.buck = true;
         in.fightLower = 1.0F;
         float y0 = McAnimalPose.still(s, in, 1.0F).head.m31();
         in.signDrop = 1.0F;
         float y1 = McAnimalPose.still(s, in, 1.0F).head.m31();
         k = Math.max(0.0F, y0 - y1);
         DROP_PER_PX.put(s, k);
      }
      return k * t.frameHeight();
   }

   /**
    * Called from WhitetailRenderer right after the body yaw is applied (before the scale): slides the drawn body so the
    * antlers of this model rest on the bark of this trunk, and measures where they touch for the next frame's height
    * correction. {@code head} is the head matrix this frame was posed with (rig model matrix or the box head).
    */
   public static void slide(Whitetail e, PoseStack stack, Matrix4f head) {
      Worker w = WORKERS.get(e.getId());
      if (w == null || head == null) {
         return;
      }
      ClientLevel level = Minecraft.getInstance().level;
      float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
      long ns = System.nanoTime();
      float dt = w.frameNs == 0L ? 0.0F : Math.min(0.1F, (ns - w.frameNs) / 1.0E9F);
      w.frameNs = ns;
      boolean rubbing = !Float.isNaN(w.wantY) && w.model != null && w.model.hasAntlers() && level != null;
      float wantZ = 0.0F;
      float wantX = 0.0F;
      if (rubbing) {
         SignGeometry.Trunk trunk = trunk(e, w, level, partial);
         if (trunk != null) {
            wantX = Mth.clamp(trunk.cx(), -0.35F, 0.35F);
            SignGeometry.Trunk centred = new SignGeometry.Trunk(trunk.round(), trunk.cx() - wantX, trunk.cz(), trunk.r(), trunk.angle(), trunk.y0(), trunk.y1());
            SignGeometry.Contact c = SignGeometry.contact(w.model, head, centred, w.vanilla ? 1 : 2, new SignGeometry.Contact());
            if (c.ok()) {
               // antlers pressed ~1 cm into the bark while working, eased off the bark between bouts
               wantZ = Mth.clamp(c.slide + 0.012F - (1.0F - w.press) * 0.07F, -0.6F, 0.7F);
               if (w.press > 0.3F) {
                  float lim = w.vanilla ? 1.0F / SignGeometry.BOX_NOD_PER_BLOCK : 0.45F;
                  w.corrY = Mth.clamp(w.corrY + (w.wantY - c.y) * 0.3F, -lim, lim);
               }
            }
         }
      }
      float target = rubbing ? 1.0F : 0.0F;
      w.slideW = approach(w.slideW, target * w.weight, 8.0F, dt);
      if (rubbing) {
         w.slideZ = approach(w.slideZ, wantZ, 14.0F, dt);
         w.slideX = approach(w.slideX, wantX, 10.0F, dt);
      }
      float sz = w.slideZ * w.slideW;
      float sx = w.slideX * w.slideW;
      if (Math.abs(sz) + Math.abs(sx) > 1.0E-4F) {
         // after the body yaw: local -z is forward, local -x is the entity's +x
         stack.translate(-sx, 0.0F, -sz);
      }
   }

   /** The trunk the buck rubs, in his entity space; null when unknown. */
   private static SignGeometry.Trunk trunk(Whitetail e, Worker w, ClientLevel level, float partial) {
      if (!(level.getBlockEntity(w.act.sign()) instanceof DeerSign.Mark mark)) {
         return null;
      }
      BlockState st = level.getBlockState(w.act.sign());
      if (!(st.getBlock() instanceof DeerSign)) {
         return null;
      }
      Direction face = st.getValue(DeerSign.FACING);
      BlockPos log = w.act.sign().relative(face.getOpposite());
      BlockState ls = level.getBlockState(log);
      if (!ls.is(BlockTags.LOGS)) {
         return null;
      }
      Vec3 p = e.getPosition(partial);
      float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
      float base = mark.tree != null ? mark.tree.getY() : log.getY();
      float[] stem = w.vanilla ? null : SignTrunkProbe.stemAt(log, ls);
      float cx;
      float cz;
      float r;
      boolean round = stem != null;
      if (round) {
         cx = stem[0];
         cz = stem[2];
         float h = w.wantY + (float)(p.y - base);
         r = stem[3] * SignTrunkProbe.flare(h);
      } else {
         cx = log.getX() + 0.5F;
         cz = log.getZ() + 0.5F;
         r = 0.5F;
      }
      float dx = (float)(cx - p.x);
      float dz = (float)(cz - p.z);
      float ex = dx * Mth.cos(yaw) + dz * Mth.sin(yaw);
      float ez = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
      // a block's faces are world-aligned: turned by minus the body yaw in entity space
      float y0 = (float)(base - p.y) + 0.02F;
      return new SignGeometry.Trunk(round, ex, ez, r, round ? 0.0F : -yaw, y0, y0 + 3.0F);
   }

   /** Drops render state of bucks no longer drawn. */
   private static void tidy(long now) {
      if (now - tidyAt > 100L || now < tidyAt) {
         tidyAt = now;
         WORKERS.values().removeIf(w -> now - w.seen > 100L || now < w.seen);
         EVENTS.keySet().removeIf(id -> !WORKERS.containsKey(id) && EVENTS.size() > 64);
      }
   }

   // ------------------------------------------------------------------------------------------------ sounds and particles

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null || mc.player == null || mc.isPaused()) {
         return;
      }
      Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
      AABB box = new AABB(cam, cam).inflate(40.0);
      for (Whitetail e : level.getEntitiesOfClass(Whitetail.class, box, d -> !d.signWork().isEmpty())) {
         SignAct a = SignWork.act(e);
         if (!a.active()) {
            continue;
         }
         try {
            events(e, a, level, mc);
         } catch (RuntimeException ex) {
            // cosmetic: never break the tick over one deer
         }
      }
   }

   private static float budget() {
      try {
         return switch (HuntConfig.QUALITY.get()) {
            case PERFORMANCE -> 0.5F;
            case BALANCED -> 1.0F;
            case CINEMATIC -> 1.5F;
         };
      } catch (RuntimeException ex) {
         return 1.0F;
      }
   }

   private static void events(Whitetail e, SignAct a, ClientLevel level, Minecraft mc) {
      long now = level.getGameTime();
      float tick = now - a.start();
      int[] ph = a.at(tick);
      float[] last = EVENTS.computeIfAbsent(e.getId(), k -> new float[]{-1.0F, -1.0F, Float.NaN, 0.0F});
      float before = last[3];
      last[3] = tick;
      if (last[2] != a.start()) {
         last[0] = -1.0F;
         last[1] = -1.0F;
         last[2] = a.start();
      }
      float age = ph[1] / 20.0F;
      float dur = ph[2] / 20.0F;
      int seed = a.seed();
      float budget = budget();
      float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
      float fx = -Mth.sin(yaw);
      float fz = Mth.cos(yaw);
      var rand = level.random;
      switch (ph[0]) {
         case SignAct.RUB_STROKES -> {
            float[] mo = new float[6];
            SignMotion.rub(seed, age, dur, mo);
            float stroke = mo[SignMotion.STROKE];
            if (stroke != last[0] && last[1] == SignAct.RUB_STROKES) {
               float amp = Math.max(0.35F, mo[SignMotion.AMP]);
               Vec3 at = rubPoint(e, a, level, mo[SignMotion.V]);
               if (at != null) {
                  play(level, at, SignSounds.RUB, 0.55F + 0.35F * amp, 0.9F + rand.nextFloat() * 0.2F);
                  BlockState log = trunkState(level, a);
                  BlockState pale = log != null ? stripped(log) : null;
                  int n = Math.round((2 + rand.nextInt(3)) * budget * amp);
                  for (int i = 0; i < n; i++) {
                     BlockState chip = pale != null && rand.nextFloat() < 0.45F ? pale : log;
                     if (chip == null) {
                        break;
                     }
                     double vx = -fx * (0.04 + rand.nextFloat() * 0.06) + (rand.nextFloat() - 0.5) * 0.08;
                     double vz = -fz * (0.04 + rand.nextFloat() * 0.06) + (rand.nextFloat() - 0.5) * 0.08;
                     level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, chip), at.x + (rand.nextFloat() - 0.5) * 0.12, at.y + (rand.nextFloat() - 0.5) * 0.1,
                        at.z + (rand.nextFloat() - 0.5) * 0.12, vx, 0.02 + rand.nextFloat() * 0.05, vz);
                  }
               }
            }
            last[0] = stroke;
         }
         case SignAct.PAW -> {
            float[] mo = new float[4];
            SignMotion.paw(seed, age, dur, mo);
            float stroke = mo[SignMotion.PAW_STROKE];
            if (stroke != last[0] && last[1] == SignAct.PAW && mo[SignMotion.PAW_AMP] > 0.2F) {
               float side = mo[SignMotion.FOOT] < 0.5F ? 1.0F : -1.0F;
               float half = 0.11F * e.traits().frameWidth();
               // the hoof as it starts dragging back through the litter (entity +x = right of the buck's heading)
               double hx = e.getX() + fx * 0.32F * e.traits().frameLength() + fz * side * half;
               double hz = e.getZ() + fz * 0.32F * e.traits().frameLength() - fx * side * half;
               BlockPos below = BlockPos.containing(hx, e.getY() - 0.2, hz);
               BlockState ground = level.getBlockState(below);
               if (ground.isAir()) {
                  ground = level.getBlockState(below.below());
                  below = below.below();
               }
               play(level, new Vec3(hx, e.getY() + 0.05, hz), SignSounds.PAW, 0.7F, 0.9F + rand.nextFloat() * 0.2F);
               if (!ground.isAir()) {
                  var step = ground.getSoundType(level, below, e).getStepSound();
                  level.playLocalSound(hx, e.getY(), hz, step, SoundSource.NEUTRAL, 0.18F, 0.8F + rand.nextFloat() * 0.15F, false);
               }
               BlockState litter = litter(level, a.sign());
               int n = Math.round((4 + rand.nextInt(4)) * budget);
               for (int i = 0; i < n; i++) {
                  BlockState s = litter != null && rand.nextFloat() < 0.35F ? litter : ground;
                  if (s.isAir()) {
                     continue;
                  }
                  // soil and litter flung back between the front legs
                  double vx = -fx * (0.08 + rand.nextFloat() * 0.12) + (rand.nextFloat() - 0.5) * 0.08;
                  double vz = -fz * (0.08 + rand.nextFloat() * 0.12) + (rand.nextFloat() - 0.5) * 0.08;
                  level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s), hx + (rand.nextFloat() - 0.5) * 0.15, e.getY() + 0.05,
                     hz + (rand.nextFloat() - 0.5) * 0.15, vx, 0.08 + rand.nextFloat() * 0.12, vz);
               }
            }
            last[0] = stroke;
         }
         case SignAct.LICK -> {
            float[] mo = new float[5];
            SignMotion.lick(seed, age, dur, mo);
            float ev = mo[SignMotion.L_EVENT];
            if (level.getBlockEntity(a.sign()) instanceof DeerSign.Mark mark && level.getBlockState(a.sign()).getBlock() instanceof DeerSign) {
               float[] tip = SignShapes.lickTip(mark, a.sign(), level.getBlockState(a.sign()).getValue(DeerSign.FACING));
               Vec3 at = new Vec3(tip[0], tip[1] - 0.05, tip[2]);
               if (ev != last[0] && last[1] == SignAct.LICK) {
                  play(level, at, SignSounds.BRANCH, 0.45F + rand.nextFloat() * 0.2F, 0.9F + rand.nextFloat() * 0.25F);
                  BlockState leaves = litter(level, a.sign());
                  if (leaves != null && rand.nextFloat() < 0.7F * budget) {
                     level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, leaves), at.x + (rand.nextFloat() - 0.5) * 0.2, at.y + 0.05,
                        at.z + (rand.nextFloat() - 0.5) * 0.2, 0.0, -0.02, 0.0);
                  }
               }
               float snapAt = mark.lickAt - a.start();
               if (mark.lickAt > 0L && before < snapAt && tick >= snapAt) {
                  play(level, at, SignSounds.SNAP, 0.6F, 0.95F + rand.nextFloat() * 0.1F);
               }
            }
            last[0] = ev;
         }
         case SignAct.URINATE -> {
            if (last[1] != SignAct.URINATE) {
               play(level, e.position(), SignSounds.TRICKLE, 0.35F, 1.0F);
            }
            float env = SignMotion.envelope(age, dur, 0.8F, 0.6F);
            if (env > 0.5F && rand.nextFloat() < 0.35F * budget) {
               // a drip or two running down the hocks
               float back = 0.30F * e.traits().frameLength();
               level.addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, e.getX() - fx * back + (rand.nextFloat() - 0.5) * 0.08,
                  e.getY() + 0.32F * e.traits().frameHeight(), e.getZ() - fz * back + (rand.nextFloat() - 0.5) * 0.08, 0.0, 0.0, 0.0);
            }
         }
         default -> {
         }
      }
      last[1] = ph[0];
   }

   private static void play(ClientLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
      level.playLocalSound(at.x, at.y, at.z, s, SoundSource.NEUTRAL, vol, pitch, false);
   }

   /** Where the antlers bite the bark this tick (world), from the trunk the client draws. */
   private static Vec3 rubPoint(Whitetail e, SignAct a, ClientLevel level, float v) {
      if (!(level.getBlockEntity(a.sign()) instanceof DeerSign.Mark mark)) {
         return null;
      }
      BlockState st = level.getBlockState(a.sign());
      if (!(st.getBlock() instanceof DeerSign)) {
         return null;
      }
      Direction face = st.getValue(DeerSign.FACING);
      BlockPos log = a.sign().relative(face.getOpposite());
      float base = mark.tree != null ? mark.tree.getY() : log.getY();
      float centre = mark.worked() ? (mark.lo + mark.hi) * 0.5F : 0.6F;
      float y = base + centre + v * SignWork.strokeAmp(e.traits());
      float[] stem = vanilla() ? null : SignTrunkProbe.stemAt(log, level.getBlockState(log));
      double cx = stem != null ? stem[0] : log.getX() + 0.5;
      double cz = stem != null ? stem[2] : log.getZ() + 0.5;
      double r = stem != null ? stem[3] * SignTrunkProbe.flare(y - base) : 0.5;
      return new Vec3(cx + face.getStepX() * (r + 0.03), y, cz + face.getStepZ() * (r + 0.03));
   }

   private static BlockState trunkState(ClientLevel level, SignAct a) {
      BlockState st = level.getBlockState(a.sign());
      if (!(st.getBlock() instanceof DeerSign)) {
         return null;
      }
      BlockState log = level.getBlockState(a.sign().relative(st.getValue(DeerSign.FACING).getOpposite()));
      return log.is(BlockTags.LOGS) ? log : null;
   }

   /** The pale inner wood of a log (its stripped form when there is one). */
   private static BlockState stripped(BlockState log) {
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
      ResourceLocation sid = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "stripped_" + id.getPath());
      Block b = BuiltInRegistries.BLOCK.getOptional(sid).orElse(null);
      if (b == null) {
         return null;
      }
      BlockState s = b.defaultBlockState();
      return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : s;
   }

   /** Leaves overhead (litter / the licking branch's leaves), or null. */
   private static BlockState litter(ClientLevel level, BlockPos cell) {
      for (int dy = 2; dy <= 7; dy++) {
         BlockState s = level.getBlockState(cell.above(dy));
         if (s.is(BlockTags.LEAVES)) {
            return s;
         }
      }
      return null;
   }
}
