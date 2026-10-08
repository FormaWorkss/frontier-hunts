import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.PhoneUi;
import com.formaworks.frontierhunts.phone.client.ui.apps.Apps;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * [phone] Renders the real phone UI offline at 1920x1080 (GUI scale 3, the Frontier 640x360 layout) with sample data.
 * java MockMain <repo> <jar> <outDir> [scene...]
 */
public final class MockMain {
   static long now = 1_000_000L;

   public static void main(String[] args) throws Exception {
      String repo = args[0], jar = args[1], out = args[2];
      new File(out).mkdirs();
      String[] scenes = args.length > 3 ? java.util.Arrays.copyOfRange(args, 3, args.length) : Scenes.ALL;
      Sample.repo = repo;
      for (String scene : scenes) {
         PhoneModel m = Sample.model();
         Stub actions = new Stub();
         PhoneUi ui = Apps.create(m, actions);
         ui.layout(640, 360);
         Scenes.Setup s = Scenes.setup(scene, ui, m, actions);
         MockCanvas c = new MockCanvas(1920, 1080, 3.0, repo + "/patch", jar);
         java.awt.geom.AffineTransform base = c.g.getTransform();
         int frames = s.frames;
         for (int i = 0; i < frames; i++) {
            c.g.setTransform(base);
            c.dry = i < frames - 1;
            c.background(repo + "/patch/assets/frontierhunts/textures/gui/field_school/welcome.png", 0.0F);
            if (s.mouseX >= 0) {
               ui.mouseMoved(s.mouseX, s.mouseY);
            }
            if (s.perFrame != null) {
               s.perFrame.accept(i);
            }
            ui.tick();
            ui.render(c, 0.0F);
            now += 16L;
         }
         File f = new File(out, "phone_" + scene + ".png");
         ImageIO.write(c.img, "png", f);
         if (System.getenv("CROP") != null) {
            ImageIO.write(c.img.getSubimage(690, 0, 540, 1080), "png", new File(out, "crop_" + scene + ".png"));
         }
         System.out.println("wrote " + f);
      }
   }

   static final class Stub implements PhoneActions {
      @Override public void sound(Sfx s) { }
      @Override public long millis() { return now; }
      @Override public void close() { }
      @Override public void refresh(int what) { }
      @Override public void setFlashlight(boolean on) { }
      @Override public void setAlarm(int minuteOfDay) { }
      @Override public void alarmAnswer(boolean snooze) { }
      @Override public void settingsChanged() { }
      @Override public void darkroom(boolean on) { }
      @Override public void camHub() { }
      @Override public void camRoll(long pos) { }
      @Override public void camClear(long pos) { }
      @Override public void camWatch(long pos) { }
      @Override public void camDelete(long pos, PhoneModel.PhotoRef photo) { }
      @Override public void camSave(String label, PhoneModel.PhotoRef photo) { }
      @Override public void camPrioritise(PhoneModel.PhotoRef photo) { }
      @Override public void addPin(String name, int icon) { }
      @Override public void removePlace(String id) { }
      @Override public void navigate(String id) { }
      @Override public void mapWanted(boolean on) { }
      @Override public void contract(int op, int index, String arg) { }
      @Override public void playCall(String soundId) { }
      @Override public void invite(int game, String player) { }
      @Override public void answerInvite(long id, boolean accept) { }
      @Override public void move(long session, int[] move) { }
      @Override public void leave(long session) { }
      @Override public void rematch(long session) { }
      @Override public void flushStart() { }
      @Override public void flushSubmit(long token, int[] shots) { }
      @Override public void flushProgress(long session, int score, int tick) { }
      @Override public void gameStat(int game, int mode, int result) { }
   }
}
