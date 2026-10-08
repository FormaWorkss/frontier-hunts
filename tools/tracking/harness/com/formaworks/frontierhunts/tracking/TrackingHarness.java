package com.formaworks.frontierhunts.tracking;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Offline checks of the tracking data layer (run: see docs/ws/tracking.md). */
public final class TrackingHarness {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "PASS " : "FAIL ") + what);
      if (!ok) {
         fails++;
      }
   }

   public static void main(String[] a) {
      UUID an = UUID.randomUUID();
      TrailMark m = new TrailMark(UUID.randomUUID(), an, new Vec3(10.5, 64.137, -3.25), new BlockPos(10, 64, -4), 135.0F, 1000L, 20, false,
         "Whitetail · large buck", 1, Direction.UP, 0, 0.3F, PrintKind.ELK.ordinal(), 1.62F, 1.12F);
      TrailMark r = TrailMark.load(m.save());
      check(r.equals(m), "NBT round trip keeps kind / stride / scale");
      CompoundTag old = m.save();
      old.remove("kind");
      old.remove("stride");
      old.remove("scale");
      TrailMark o = TrailMark.load(old);
      check(o.sign() == 0 && o.stride() == 0.0F && o.scale() == 1.0F, "pre-tracking saves load with defaults");
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      TrailNetwork.write(buf, m);
      check(TrailNetwork.read(buf).equals(m) && buf.readableBytes() == 0, "network codec round trip");
      check(m.lifetime() == TrailMark.PRINT_LIFETIME && !m.expired(1000L + 30000L) && m.expired(1000L + 36100L), "print lifetime ~1.5 days");
      TrailMark blood = new TrailMark(UUID.randomUUID(), an, new Vec3(0, 64.012, 0), new BlockPos(0, 63, 0), 0.0F, 0L, 0, true, "x", 0, Direction.UP, 3, 0.3F,
         BloodTrail.BloodType.LUNG.ordinal(), 0.0F, 1.0F);
      check(blood.weather(20000).expired(5000L), "weather wear ages blood");
      check(blood.kind().equals("blood") && m.kind().equals("hoofprints"), "kind nouns");
      // age reading
      check(TrailReading.age(m, 1000L + 300L, 6000L).startsWith("fresh"), "minutes old reads fresh");
      check(TrailReading.age(m, 1000L + 3000L, 6000L).equals("a few hours old"), "3000 ticks reads a few hours");
      // made at 20000 (night), now 6000 next morning: 10000 ticks later
      check(TrailReading.age(m, 1000L + 10000L, 30000L).equals("made last night"), "night print read next morning");
      check(TrailReading.age(m, 1000L + 26000L, 32000L).equals("about a day old"), "a day old");
      check(TrailReading.heading(180.0F).equals("heading north") && TrailReading.heading(-90.0F).equals("heading east"), "compass from yaw");
      // estimates
      check(PrintKind.DEER.estimate(1.2F, 1.2F, 1).contains("buck"), "big deer print reads as a buck: " + PrintKind.DEER.estimate(1.2F, 1.2F, 1));
      check(PrintKind.DEER.estimate(0.7F, 0.9F, 1).contains("fawn"), "small deer print reads as a fawn");
      check(PrintKind.DEER.estimate(1.0F, 3.2F, 2).contains("running"), "running gait read");
      // wound regions
      check(BloodTrail.fromRegion("DOUBLE_LUNG") == BloodTrail.BloodType.LUNG && BloodTrail.fromRegion("LEG") == BloodTrail.BloodType.MUSCLE
         && BloodTrail.fromRegion("GUT") == BloodTrail.BloodType.GUT && BloodTrail.fromRegion("unrecorded") == BloodTrail.BloodType.GENERIC, "region mapping");
      check(BloodTrail.fleeMemory("GUT", 3600) < BloodTrail.fleeMemory("HEART", 3600), "gut-shot animals stop running sooner");
      int runs = 0;
      net.minecraft.util.RandomSource rs = net.minecraft.util.RandomSource.create(4L);
      for (int i = 0; i < 1000; i++) {
         if (BloodTrail.deathRun("HEART", 0.6F, rs)) {
            runs++;
         }
      }
      check(runs > 550 && runs < 750 && !BloodTrail.deathRun("HEART", 0.05F, rs) && !BloodTrail.deathRun("LIVER", 1.0F, rs), "heart death-run odds " + runs);
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
      if (fails > 0) {
         System.exit(1);
      }
   }
}
