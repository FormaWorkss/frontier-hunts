package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.formaworks.frontierhunts.hunting.RoutineAccess;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.routine.RoutineHooks;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import com.formaworks.frontierhunts.hunting.rutfight.UltraFightModels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * [deersign] Server side of a buck making sign. Rubs and scrapes exist only where a buck has actually worked a tree or
 * the ground: he picks a small tree (rub) or a spot under a low branch at a tree (scrape) or one of the scrapes near
 * him (revisit), walks there, settles square to the trunk / branch, and works it while every client watches the same
 * synced {@link SignAct}:
 *
 * <ul>
 *   <li>RUB: smells the trunk, then 6-15 s of antler strokes; the rub block exists from the first stroke and grows
 *   with the work (an interrupted rub stays a part rub), drawn at the height band his antlers actually worked
 *   ({@link SignGeometry#contact} on his own head and rack).</li>
 *   <li>SCRAPE: smells the spot, paws it with alternating front hooves (the pawed patch grows), works the licking branch
 *   overhead with forehead, antlers and mouth (the branch tip is put exactly where his nose reaches), then
 *   rub-urinates over his hocks.</li>
 *   <li>REVISIT: the same at one of the scrapes about him; pawing freshens it.</li>
 * </ul>
 *
 * <p>All state lives here (server thread only); the goal ({@link SignGoal}) moves the deer, the synced tag carries the
 * animation. Anything that alarms the buck, calls, fights or beds him ends the work at once.</p>
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class SignWork {
   private static final int WALK = SignPlan.WALK;
   private static final int SETTLE = SignPlan.SETTLE;
   private static final int ACT = SignPlan.ACT;
   private static final Map<Whitetail, Job> JOBS = new WeakHashMap<>();
   private static final Map<Whitetail, long[]> TIMERS = new WeakHashMap<>(); // {next check, rest until}
   private static final Map<Whitetail, Object[]> ACTS = new WeakHashMap<>(); // tag -> parsed SignAct (both sides)

   private SignWork() {
   }

   /** One buck's work in progress. */
   static final class Job {
      final int kind;
      final DeerSign.Site site;
      final BlockPos revisit;
      final boolean forced;
      final int seed;
      final long started;
      int state = WALK;
      long stateAt;
      long deadline;
      double standX;
      double standZ;
      float yaw;
      Vec3 lastPos;
      long progressAt;
      CompletableFuture<FightModel> model;
      BlockPos sign;
      SignAct act;
      boolean freshened;

      Job(int kind, DeerSign.Site site, BlockPos revisit, boolean forced, int seed, long now) {
         this.kind = kind;
         this.site = site;
         this.revisit = revisit;
         this.forced = forced;
         this.seed = seed;
         this.started = now;
         this.stateAt = now;
         this.progressAt = now;
      }
   }

   // ------------------------------------------------------------------------------------------------ synced act

   /** The act this deer is performing (synced; parsed once per tag change). */
   public static SignAct act(Whitetail deer) {
      CompoundTag tag = deer.signWork();
      if (tag == null || tag.isEmpty()) {
         return SignAct.NONE;
      }
      synchronized (ACTS) {
         Object[] c = ACTS.get(deer);
         if (c != null && c[0] == tag) {
            return (SignAct)c[1];
         }
         SignAct a = SignAct.load(tag);
         ACTS.put(deer, new Object[]{tag, a});
         return a;
      }
   }

   public static boolean working(Whitetail deer) {
      synchronized (JOBS) {
         return JOBS.containsKey(deer);
      }
   }

   private static Job job(Whitetail deer) {
      synchronized (JOBS) {
         return JOBS.get(deer);
      }
   }

   // ------------------------------------------------------------------------------------------------ deciding

   /** Called from Whitetail.rutWork (every second, bucks only, not while fighting / tending). */
   public static void rutCheck(Whitetail deer, ServerLevel level) {
      long now = level.getGameTime();
      Job old = job(deer);
      if (old != null) {
         // a job whose goal never got to run (the buck bedded / fought / fled first) is dropped
         boolean stale = old.state == ACT ? old.act != null && now - old.act.start() > old.act.total() + 100L : now > old.deadline + 200L;
         if (stale) {
            abort(deer, level, "stale");
         }
         return;
      }
      long[] t;
      synchronized (TIMERS) {
         t = TIMERS.computeIfAbsent(deer, d -> new long[]{now + 100L + d.getRandom().nextInt(300), 0L});
      }
      if (now < t[0] || now < t[1]) {
         return;
      }
      RandomSource random = deer.getRandom();
      t[0] = now + SignPlan.lookInterval(random.nextFloat()); // next look in 12-22 s
      if (!calm(deer) || !deer.onGround()) {
         return;
      }
      DeerTraits traits = deer.traits();
      float mature = SignSeason.maturity(traits.ageMonths());
      double y = SignSeason.yearPosition(level);
      float rub = SignSeason.rubRate(deer.species(), y) * mature;
      float scrape = SignSeason.scrapeRate(deer.species(), y) * mature;
      if (SignPlan.chance(rub, scrape) <= 0.0F) {
         return;
      }
      BlockPos own = scrape > 0.0F ? DeerSign.findNearby(level, deer.blockPosition(), DeerSign.Kind.SCRAPE, 14) : null;
      int kind = SignPlan.decide(rub, scrape, own != null, random.nextFloat(), random.nextFloat(), random.nextFloat());
      if (kind == 0) {
         return;
      }
      if (kind == SignAct.REVISIT && start(deer, level, SignAct.REVISIT, null, own, false)) {
         return;
      }
      boolean wantScrape = kind != SignAct.RUB;
      if (!plan(deer, level, wantScrape ? SignAct.SCRAPE : SignAct.RUB, false) && !plan(deer, level, wantScrape ? SignAct.RUB : SignAct.SCRAPE, false)) {
         t[0] = now + 400L + random.nextInt(400); // nothing to mark about here
      }
   }

   /** Nothing on his mind but sign: calm, on his feet, not answering a call. */
   static boolean calm(Whitetail deer) {
      return !deer.downed() && !deer.bleeding() && !deer.sedated() && !deer.waking() && deer.behavior() == 0 && deer.alertness() <= 0.3F
         && !deer.approachingCall() && !deer.respondingToCall() && !RoutineHooks.rutBusy(deer) && deer.traits().buck() && deer.isAlive();
   }

   /** Most logs a rub tree of this buck may have: a yearling rubs saplings, a mature buck heavier stems too. */
   private static int maxLogs(Whitetail deer) {
      int age = deer.traits().ageMonths();
      return deer.species() != GameSpecies.WHITETAIL ? 12 : age >= 40 ? 14 : age >= 28 ? 9 : 6;
   }

   /** Picks a site near the buck and starts walking there. */
   public static boolean plan(Whitetail deer, ServerLevel level, int kind, boolean forced) {
      if (kind == SignAct.SCRAPE && deer.species() != GameSpecies.WHITETAIL) {
         return false;
      }
      DeerSign.Kind k = kind == SignAct.RUB ? DeerSign.Kind.RUB : DeerSign.Kind.SCRAPE;
      Vec3 ahead = deer.position().add(deer.getLookAngle().multiply(3.0, 0.0, 3.0));
      for (DeerSign.Site site : DeerSign.findSites(level, ahead, deer.getRandom(), k, 11, 8, maxLogs(deer))) {
         if (startAt(deer, level, kind, site, null, forced)) {
            return true;
         }
      }
      return false;
   }

   /** The command: this buck works a given site / scrape now (season and maturity aside). */
   public static boolean force(Whitetail deer, ServerLevel level, int kind, DeerSign.Site site, BlockPos scrape) {
      abort(deer, level, "forced");
      synchronized (TIMERS) {
         TIMERS.remove(deer);
      }
      return kind == SignAct.REVISIT ? start(deer, level, kind, null, scrape, true) : startAt(deer, level, kind, site, null, true);
   }

   private static boolean start(Whitetail deer, ServerLevel level, int kind, DeerSign.Site site, BlockPos revisit, boolean forced) {
      return startAt(deer, level, kind, site, revisit, forced);
   }

   private static boolean startAt(Whitetail deer, ServerLevel level, int kind, DeerSign.Site site, BlockPos revisit, boolean forced) {
      long now = level.getGameTime();
      BlockPos target = revisit != null ? revisit : site.cell();
      if (claimed(deer, level, target)) {
         lastWhy = "another buck is already working there";
         return false;
      }
      Job j = new Job(kind, site, revisit, forced, deer.getRandom().nextInt(), now);
      if (!stand(deer, level, j)) {
         return false;
      }
      double dist = Math.sqrt(deer.distanceToSqr(j.standX, deer.getY(), j.standZ));
      j.deadline = now + SignPlan.walkTicks(dist);
      j.lastPos = deer.position();
      // the buck's own head / rack geometry for the contact height and the branch reach, built off-thread while he walks
      DeerTraits t = deer.traits();
      j.model = CompletableFuture.supplyAsync(() -> UltraFightModels.of(t), Util.backgroundExecutor());
      synchronized (JOBS) {
         JOBS.put(deer, j);
      }
      return true;
   }

   /** Why the last stand-point search failed (for the command; server thread only). */
   public static String lastWhy = "";

   /** Another buck already walks to / works sign within 2 blocks of this spot (two bucks never stack on one tree). */
   private static boolean claimed(Whitetail deer, ServerLevel level, BlockPos target) {
      synchronized (JOBS) {
         for (Map.Entry<Whitetail, Job> e : JOBS.entrySet()) {
            Whitetail other = e.getKey();
            if (other == deer || other.isRemoved() || other.level() != level) {
               continue;
            }
            Job o = e.getValue();
            BlockPos at = o.sign != null ? o.sign : o.revisit != null ? o.revisit : o.site.cell();
            if (at.distManhattan(target) <= 2) {
               return true;
            }
         }
      }
      return false;
   }

   /** Where the buck stands to work the site, facing the trunk (rub) or the licking branch (scrape). */
   private static boolean stand(Whitetail deer, ServerLevel level, Job j) {
      double half = deer.getBbWidth() * 0.5;
      double fx;
      double fz;
      double ox;
      double oz;
      double back;
      double y;
      if (j.kind == SignAct.RUB) {
         y = j.site.cell().getY();
         Direction face = j.site.facing();
         // trunk block centre, buck on the face normal: as close as the trunk block allows (the client slides the
         // drawn body the last bit so the antlers meet the bark of whatever trunk it draws)
         ox = j.site.tree().getX() + 0.5;
         oz = j.site.tree().getZ() + 0.5;
         fx = -face.getStepX();
         fz = -face.getStepZ();
         back = 0.5 + half + 0.03;
      } else {
         BlockPos cell = j.kind == SignAct.REVISIT ? j.revisit : j.site.cell();
         BlockState st = level.getBlockState(cell);
         Direction toTree = j.kind == SignAct.REVISIT
            ? (st.getBlock() instanceof DeerSign ? st.getValue(DeerSign.FACING) : Direction.NORTH)
            : j.site.facing();
         if (j.kind == SignAct.REVISIT && !(st.getBlock() instanceof DeerSign)) {
            return false;
         }
         y = cell.getY();
         ox = cell.getX() + 0.5;
         oz = cell.getZ() + 0.5;
         fx = toTree.getStepX();
         fz = toTree.getStepZ();
         // front hooves at the middle of the scrape
         back = 0.12 + 0.14 * deer.traits().frameLength();
      }
      for (int i = 0; i < 6; i++) {
         double d = back + i * 0.12;
         double x = ox - fx * d;
         double z = oz - fz * d;
         // body room up to his withers: antlers and the top of the back push through low twigs and leaves
         AABB full = deer.getDimensions(deer.getPose()).makeBoundingBox(x, y + 0.01, z);
         AABB box = new AABB(full.minX, full.minY, full.minZ, full.maxX, full.minY + Math.min(full.getYsize(), 1.7), full.maxZ);
         BlockPos feet = BlockPos.containing(x, y + 0.2, z);
         boolean free = level.noCollision(box);
         if (free && ground(level, feet)) {
            j.standX = x;
            j.standZ = z;
            j.yaw = (float)(Mth.atan2(fz, fx) * (180.0 / Math.PI)) - 90.0F;
            return true;
         }
         lastWhy = free ? "no ground at " + feet.toShortString() : "no room for a " + String.format("%.1fx%.1f", deer.getBbWidth(), deer.getBbHeight())
            + " buck at " + feet.toShortString();
      }
      return false;
   }

   private static boolean ground(ServerLevel level, BlockPos feet) {
      for (int dy = 0; dy <= 2; dy++) {
         BlockPos b = feet.below(dy);
         if (level.getBlockState(b).isFaceSturdy(level, b, Direction.UP)) {
            return true;
         }
      }
      return false;
   }

   // ------------------------------------------------------------------------------------------------ working

   /** Should the sign goal run this tick. */
   public static boolean goalWanted(Whitetail deer) {
      Job j = job(deer);
      if (j == null) {
         return false;
      }
      if (!calm(deer) && !(j.forced && deer.alertness() <= 0.3F && !deer.downed() && !deer.bleeding() && deer.behavior() == 0)) {
         abort(deer, (ServerLevel)deer.level(), "disturbed");
         return false;
      }
      return true;
   }

   /** The goal was taken from us (a call, a fight, flight, bedding ...). */
   public static void goalStop(Whitetail deer) {
      if (deer.level() instanceof ServerLevel level) {
         abort(deer, level, "interrupted");
      }
   }

   /** Every server tick while the goal runs. */
   public static void tick(Whitetail deer) {
      if (!(deer.level() instanceof ServerLevel level)) {
         return;
      }
      Job j = job(deer);
      if (j == null) {
         return;
      }
      long now = level.getGameTime();
      switch (j.state) {
         case WALK -> walk(deer, level, j, now);
         case SETTLE -> settle(deer, level, j, now);
         default -> work(deer, level, j, now);
      }
   }

   private static void walk(Whitetail deer, ServerLevel level, Job j, long now) {
      double dx = j.standX - deer.getX();
      double dz = j.standZ - deer.getZ();
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (deer.position().distanceToSqr(j.lastPos) > 0.25) {
         j.lastPos = deer.position();
         j.progressAt = now;
      }
      int next = SignPlan.step(WALK, dist, 0.0F, true, now - j.stateAt, now - j.progressAt, now > j.deadline, siteValid(level, j), 0L, 0,
         deer.getNavigation().isDone());
      if (next == SignPlan.ABORT) {
         abort(deer, level, "gave up");
         return;
      }
      if (next == SETTLE) {
         deer.getNavigation().stop();
         j.state = SETTLE;
         j.stateAt = now;
         return;
      }
      // re-path every 1.5 s, or half a second after a path ended short (never a path search every tick)
      if ((now - j.stateAt) % 30L == 0L || deer.getNavigation().isDone() && (now - j.stateAt) % 10L == 0L) {
         deer.getNavigation().moveTo(j.standX, deer.getY(), j.standZ, RoutineAccess.walkSpeed(deer) * 0.9);
      }
   }

   /** Last steps: a slow glide onto the exact spot (the legs walk it) while turning square to the work. */
   private static void settle(Whitetail deer, ServerLevel level, Job j, long now) {
      deer.getNavigation().stop();
      double dx = j.standX - deer.getX();
      double dz = j.standZ - deer.getZ();
      double d = Math.sqrt(dx * dx + dz * dz);
      double step = Math.min(d, SignPlan.GLIDE);
      if (d > 1.0E-4) {
         // a slow walk onto the spot (collides and steps like any move; the legs animate it); under a low canopy the
         // full-height box catches the leaves: he pushes through them
         double ox = deer.getX();
         double oz = deer.getZ();
         deer.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(dx / d * step, 0.0, dz / d * step));
         if (Math.abs(deer.getX() - ox) + Math.abs(deer.getZ() - oz) < step * 0.3) {
            // only leaves / twigs above his withers may be pushed through, never a trunk, wall or fence
            double nx = ox + dx / d * step;
            double nz = oz + dz / d * step;
            AABB full = deer.getDimensions(deer.getPose()).makeBoundingBox(nx, deer.getY() + 0.01, nz);
            if (level.noCollision(new AABB(full.minX, full.minY, full.minZ, full.maxX, full.minY + Math.min(full.getYsize(), 1.7), full.maxZ))) {
               deer.setPos(nx, deer.getY(), nz);
            }
         }
         d = Math.sqrt(Math.pow(j.standX - deer.getX(), 2) + Math.pow(j.standZ - deer.getZ(), 2));
      }
      deer.setDeltaMovement(0.0, deer.getDeltaMovement().y, 0.0);
      float err = Mth.wrapDegrees(j.yaw - deer.getYRot());
      float yaw = deer.getYRot() + Mth.clamp(err, -SignPlan.TURN, SignPlan.TURN);
      face(deer, yaw);
      err = Mth.wrapDegrees(j.yaw - yaw);
      int next = SignPlan.step(SETTLE, d, err, j.model.isDone(), now - j.stateAt, 0L, false, siteValid(level, j), 0L, 0);
      if (next == SignPlan.ABORT) {
         abort(deer, level, "site gone");
      } else if (next == ACT && d > 0.3) {
         abort(deer, level, "could not reach the spot"); // settle timed out short of it: never rub thin air
      } else if (next == ACT) {
         face(deer, j.yaw);
         begin(deer, level, j, now);
      }
   }

   private static void face(Whitetail deer, float yaw) {
      deer.setYRot(yaw);
      deer.setYBodyRot(yaw);
      deer.setYHeadRot(yaw);
      deer.yRotO = yaw;
      deer.setXRot(0.0F);
   }

   /** Starts the act: lays out the phases, opens / takes the sign block and tells every client. */
   private static void begin(Whitetail deer, ServerLevel level, Job j, long now) {
      RandomSource r = deer.getRandom();
      FightModel model = j.model.isDone() && !j.model.isCompletedExceptionally() ? j.model.getNow(null) : null;
      DeerSkeleton sk = DeerSkeleton.of(deer.species());
      int[][] pt = SignPlan.phases(j.kind, deer.traits().ageMonths(), new float[]{r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()});
      int[] phases = pt[0];
      int[] ticks = pt[1];
      BlockPos signPos;
      if (j.kind == SignAct.REVISIT) {
         signPos = j.revisit;
      } else {
         if (!DeerSign.make(deer, j.site)) {
            abort(deer, level, "site taken");
            return;
         }
         signPos = j.site.cell();
      }
      SignAct act = new SignAct(j.kind, now, signPos, j.seed, phases, ticks);
      if (level.getBlockEntity(signPos) instanceof DeerSign.Mark mark) {
         BlockState state = level.getBlockState(signPos);
         if (j.kind == SignAct.RUB) {
            float[] band = rubBand(deer, model);
            mark.lo = band[0];
            mark.hi = band[1];
            mark.workFrom = now + ticks[0];
            mark.workTo = mark.workFrom + ticks[1];
            mark.cap = 1.0F;
         } else if (j.kind == SignAct.SCRAPE) {
            int paw = act.phaseStart(SignAct.PAW);
            mark.workFrom = now + paw;
            mark.workTo = mark.workFrom + act.phaseTicks(SignAct.PAW);
            mark.cap = 1.0F;
            float[] reach = model != null ? SignGeometry.lickReach(model, sk) : fallbackReach(deer);
            Direction toTree = state.getValue(DeerSign.FACING);
            double along = (j.standX - signPos.getX() - 0.5) * toTree.getStepX() + (j.standZ - signPos.getZ() - 0.5) * toTree.getStepZ();
            mark.lickF = (float)Mth.clamp(along + reach[0], -0.6, 1.2);
            // the tip joint sits a little above the nose; its chewed end hangs into his face
            mark.lickY = (float)Mth.clamp(deer.getY() - signPos.getY() + reach[1] + 0.06, 0.6, 2.4);
            mark.lickAt = now + act.phaseStart(SignAct.LICK) + act.phaseTicks(SignAct.LICK) / 2;
         }
         mark.setChanged();
         level.sendBlockUpdated(signPos, state, state, 2);
      } else if (j.kind != SignAct.REVISIT) {
         abort(deer, level, "no block entity");
         return;
      }
      j.act = act;
      j.sign = signPos;
      j.state = ACT;
      j.stateAt = now;
      deer.setSignWork(act.save());
   }

   /** The rub band (metres above the trunk base) this buck's antlers work, from his own head and rack. */
   static float[] rubBand(Whitetail deer, FightModel model) {
      float amp = strokeAmp(deer.traits());
      float hc;
      float spread;
      if (model != null && model.hasAntlers()) {
         SignGeometry.Contact c = SignGeometry.contact(model, SignGeometry.rubBase(model, deer.species(), new org.joml.Matrix4f()),
            new SignGeometry.Trunk(true, 0.0F, 6.0F, assumedRadius(deer), 0.0F, 0.05F, 3.0F), 1, new SignGeometry.Contact());
         hc = c.ok() ? c.y : fallbackHeight(deer);
         spread = c.ok() ? Math.min(0.2F, c.spread) : 0.08F;
      } else {
         hc = fallbackHeight(deer);
         spread = 0.08F;
      }
      float lo = Math.max(0.08F, hc - amp - spread * 0.5F - 0.03F);
      float hi = hc + amp + spread * 0.5F + 0.03F;
      return new float[]{lo, hi};
   }

   /** Vertical stroke half-height (entity blocks): bigger, older bucks drive longer strokes. */
   public static float strokeAmp(DeerTraits t) {
      float mature = Math.clamp((t.ageMonths() - 18) / 36.0F, 0.0F, 1.0F);
      float base = t.species() == GameSpecies.WHITETAIL ? 0.10F + 0.05F * mature : 0.10F;
      return base * Math.clamp(t.frameHeight(), 0.6F, 1.6F);
   }

   /** Server's guess at a small tree's radius for the contact height (the client measures its own trunk). */
   static float assumedRadius(Whitetail deer) {
      return deer.species() == GameSpecies.WHITETAIL ? 0.12F : 0.16F;
   }

   private static float fallbackHeight(Whitetail deer) {
      float h = deer.species() == GameSpecies.WHITETAIL ? 0.55F : 1.0F;
      return h * Math.clamp(deer.traits().frameHeight(), 0.6F, 1.6F);
   }

   private static float[] fallbackReach(Whitetail deer) {
      float s = Math.clamp(deer.traits().frameHeight(), 0.6F, 1.6F);
      return new float[]{0.5F * s, 1.12F * s};
   }

   private static boolean siteValid(ServerLevel level, Job j) {
      if (j.kind == SignAct.REVISIT) {
         return level.isLoaded(j.revisit) && level.getBlockState(j.revisit).getBlock() instanceof DeerSign s && s.kind == DeerSign.Kind.SCRAPE;
      }
      return DeerSign.stillFree(level, j.site);
   }

   private static void work(Whitetail deer, ServerLevel level, Job j, long now) {
      SignAct act = j.act;
      if (!(level.getBlockState(j.sign).getBlock() instanceof DeerSign)) {
         abort(deer, level, "sign gone");
         return;
      }
      deer.getNavigation().stop();
      // hold the spot (a herd mate's shove is walked back) and stay square to the work
      double dx = j.standX - deer.getX();
      double dz = j.standZ - deer.getZ();
      double d = Math.sqrt(dx * dx + dz * dz);
      if (d > 0.06) {
         double step = Math.min(d, 0.03);
         deer.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(dx / d * step, 0.0, dz / d * step));
      }
      deer.setDeltaMovement(0.0, deer.getDeltaMovement().y, 0.0);
      face(deer, j.yaw);
      long e = now - act.start();
      if (j.kind == SignAct.REVISIT && !j.freshened && e >= act.phaseStart(SignAct.PAW) + act.phaseTicks(SignAct.PAW) / 2) {
         j.freshened = true;
         DeerSign.visit(level, j.sign, deer);
      }
      if (SignPlan.step(ACT, 0.0, 0.0F, true, 0L, 0L, false, true, e, act.total()) == SignPlan.DONE) {
         finish(deer, level, j, now);
      }
   }

   private static void finish(Whitetail deer, ServerLevel level, Job j, long now) {
      synchronized (JOBS) {
         JOBS.remove(deer);
      }
      deer.setSignWork(new CompoundTag());
      RandomSource r = deer.getRandom();
      if (j.kind == SignAct.SCRAPE && level.getBlockEntity(j.sign) instanceof DeerSign.Mark mark) {
         mark.visits = Math.max(1, mark.visits);
         mark.setChanged();
      }
      if (r.nextFloat() < 0.25F) {
         level.playSound(null, deer.getX(), deer.getEyeY(), deer.getZ(), HuntSounds.DEER_GRUNT.get(), SoundSource.NEUTRAL, 0.7F, 0.95F + r.nextFloat() * 0.1F);
      }
      long[] t;
      synchronized (TIMERS) {
         t = TIMERS.computeIfAbsent(deer, d -> new long[2]);
      }
      // a buck lays down a line of sign: often another rub a little further on, then a rest
      t[1] = now + SignPlan.restAfter(j.kind, r.nextFloat(), r.nextFloat());
      t[0] = t[1];
   }

   /** Ends the work early (alarm, call, fight, bed, site gone, command): the sign keeps what was done. */
   public static void abort(Whitetail deer, ServerLevel level, String why) {
      Job j;
      synchronized (JOBS) {
         j = JOBS.remove(deer);
      }
      if (j == null) {
         return;
      }
      if (!deer.signWork().isEmpty()) {
         deer.setSignWork(new CompoundTag());
      }
      long now = level.getGameTime();
      // isLoaded: an abort while the chunks unload must never load the sign's chunk again (Mark.onLoad caps it then)
      if (j.state == ACT && j.sign != null && j.kind != SignAct.REVISIT && level.isLoaded(j.sign) && level.getBlockEntity(j.sign) instanceof DeerSign.Mark mark) {
         float p = mark.progress(now);
         BlockState state = level.getBlockState(j.sign);
         if (p < 0.08F) {
            level.removeBlock(j.sign, false); // never really started: no sign
         } else if (p < 1.0F) {
            mark.cap = p;
            if (j.kind == SignAct.SCRAPE && now < mark.lickAt) {
               mark.lickAt = Long.MAX_VALUE / 4; // the branch was never worked
            }
            mark.setChanged();
            level.sendBlockUpdated(j.sign, state, state, 2);
         }
      }
      long[] t;
      synchronized (TIMERS) {
         t = TIMERS.computeIfAbsent(deer, d -> new long[2]);
      }
      t[1] = now + SignPlan.restAfterAbort(deer.getRandom().nextFloat());
      t[0] = t[1];
   }

   /** The buck left the level mid-work (chunk unloaded, killed and removed, changed dimension): the sign keeps what he did. */
   @SubscribeEvent
   public static void leave(EntityLeaveLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Whitetail deer && working(deer)) {
         abort(deer, level, "left the level");
      }
   }

   /** Status line for the command. */
   public static String describe(Whitetail deer) {
      Job j = job(deer);
      long[] t;
      synchronized (TIMERS) {
         t = TIMERS.get(deer);
      }
      ServerLevel level = (ServerLevel)deer.level();
      long now = level.getGameTime();
      double y = SignSeason.yearPosition(level);
      float m = SignSeason.maturity(deer.traits().ageMonths());
      String season = String.format("rub rate %.2f, scrape rate %.2f (season x maturity %.2f)", SignSeason.rubRate(deer.species(), y) * m,
         SignSeason.scrapeRate(deer.species(), y) * m, m);
      if (j == null) {
         String rest = t == null ? "" : t[1] > now ? String.format(" · resting %d s", (t[1] - now) / 20L) : t[0] > now ? String.format(" · next look in %d s", (t[0] - now) / 20L) : "";
         return "idle" + rest + " · " + season;
      }
      String what = j.kind == SignAct.RUB ? "rub" : j.kind == SignAct.SCRAPE ? "scrape" : "revisit scrape";
      String state = switch (j.state) {
         case WALK -> String.format("walking (%.1f blocks)", Math.sqrt(deer.distanceToSqr(j.standX, deer.getY(), j.standZ)));
         case SETTLE -> "settling";
         default -> {
            int[] ph = j.act.at(now - j.act.start());
            yield "working: " + phaseName(ph[0]) + String.format(" %.1f/%.1f s", ph[1] / 20.0F, ph[2] / 20.0F);
         }
      };
      BlockPos at = j.sign != null ? j.sign : j.revisit != null ? j.revisit : j.site.cell();
      return what + " at " + at.toShortString() + " · " + state + " · " + season;
   }

   public static String phaseName(int phase) {
      return switch (phase) {
         case SignAct.SNIFF_TREE -> "smelling the tree";
         case SignAct.RUB_STROKES -> "rubbing";
         case SignAct.SNIFF_GROUND -> "smelling the spot";
         case SignAct.PAW -> "pawing";
         case SignAct.LICK -> "licking branch";
         case SignAct.URINATE -> "rub-urinating";
         default -> "-";
      };
   }

   /** Bucks with work in progress near a point (command). */
   public static List<Whitetail> workers() {
      synchronized (JOBS) {
         return new ArrayList<>(JOBS.keySet());
      }
   }
}
