import com.formaworks.frontierhunts.guide.FieldSchoolData;
import com.formaworks.frontierhunts.guide.GuideNetwork;
import com.formaworks.frontierhunts.guide.Lesson;
import com.formaworks.frontierhunts.guide.Tip;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** [guide] Offline checks for the Field School's pure logic: lesson masks, saved records, payload codecs. */
public final class GuideHarness {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "PASS " : "FAIL ") + what);
      if (!ok) fails++;
   }

   public static void main(String[] a) throws Exception {
      // lesson order and masks
      check(Lesson.count() == 8 && Lesson.TIPS.optional() && !Lesson.SHOT.optional(), "eight lessons, only the tips optional");
      check(Lesson.current(0) == Lesson.WIND, "fresh hunter starts on lesson 1");
      int m = Lesson.WIND.bit() | Lesson.GLASS.bit();
      check(Lesson.current(m) == Lesson.SIGN, "lessons count in any order; card shows the first missing one");
      check(!Lesson.graduated(Lesson.REQUIRED_MASK & ~Lesson.HARVEST.bit()), "not graduated with a required lesson missing");
      check(Lesson.graduated(Lesson.REQUIRED_MASK) && Lesson.current(Lesson.REQUIRED_MASK) == Lesson.TIPS, "graduated after 7; optional tips remain");
      check(Lesson.current(Lesson.ALL_MASK) == null, "all done");
      check(Lesson.requiredDone(Lesson.ALL_MASK) == 7, "required count ignores the optional lesson");
      check(Lesson.byId(-1) == null && Lesson.byId(8) == null && Tip.byId(Tip.values().length) == null && Tip.byId(-1) == null, "id bounds"); // [qa] survival added tips (10 now): bound by the enum size
      // saved record round trip
      FieldSchoolData.Hunter h = new FieldSchoolData.Hunter();
      h.done = Lesson.WIND.bit() | Lesson.TRAIL.bit();
      h.tips = Tip.BLIZZARD.bit();
      h.skipped = true; h.welcomed = true; h.kit = true; h.blood = 1; h.dressed = true; h.season = 2;
      Method save = FieldSchoolData.Hunter.class.getDeclaredMethod("save");
      save.setAccessible(true);
      Method load = FieldSchoolData.Hunter.class.getDeclaredMethod("load", CompoundTag.class);
      load.setAccessible(true);
      CompoundTag t = (CompoundTag)save.invoke(h);
      FieldSchoolData.Hunter r = (FieldSchoolData.Hunter)load.invoke(null, t);
      check(r.done == h.done && r.tips == h.tips && r.skipped && r.welcomed && r.kit && r.blood == 1 && r.dressed && r.season == 2, "hunter NBT round trip");
      FieldSchoolData.Hunter old = (FieldSchoolData.Hunter)load.invoke(null, new CompoundTag());
      check(old.season == -1 && !old.kit && old.done == 0, "empty/old record loads with defaults");
      CompoundTag bad = new CompoundTag();
      bad.putInt("done", -1);
      bad.putInt("blood", 999);
      FieldSchoolData.Hunter clamped = (FieldSchoolData.Hunter)load.invoke(null, bad);
      check(clamped.done == Lesson.ALL_MASK && clamped.blood == 64, "corrupt values are clamped");
      check(r.progress(Lesson.TRAIL) == Lesson.TRAIL.goal && r.progress(Lesson.HARVEST) == 1 && r.progress(Lesson.STALK) == 0, "card progress");
      r.reset();
      check(r.done == 0 && r.tips == 0 && !r.welcomed && !r.skipped && r.kit && r.season == -1, "reset keeps the kit flag only");
      // payload codecs
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      GuideNetwork.State s = new GuideNetwork.State(Lesson.SHOT.bit(), GuideNetwork.F_ENABLED | GuideNetwork.F_KIT, 1, 5);
      GuideNetwork.State.CODEC.encode(buf, s);
      check(GuideNetwork.State.CODEC.decode(buf).equals(s), "state codec");
      GuideNetwork.Notice n = new GuideNetwork.Notice(GuideNetwork.N_TIP, (byte)4, 3);
      GuideNetwork.Notice.CODEC.encode(buf, n);
      check(GuideNetwork.Notice.CODEC.decode(buf).equals(n), "notice codec");
      GuideNetwork.Action ac = new GuideNetwork.Action(GuideNetwork.A_SKIP_LESSON, (byte)7);
      GuideNetwork.Action.CODEC.encode(buf, ac);
      check(GuideNetwork.Action.CODEC.decode(buf).equals(ac), "action codec");
      buf.writeVarInt(-1); buf.writeVarInt(0); buf.writeVarInt(1000); buf.writeVarInt(0);
      GuideNetwork.State hostile = GuideNetwork.State.CODEC.decode(buf);
      check(hostile.done() == Lesson.ALL_MASK && hostile.progress() == 64, "state codec clamps hostile values");
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
