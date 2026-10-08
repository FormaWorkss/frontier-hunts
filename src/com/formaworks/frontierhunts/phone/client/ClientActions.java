package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.trailcam.TrailcamClient;
import com.formaworks.frontierhunts.phone.PhoneCams;
import com.formaworks.frontierhunts.phone.PhoneNet;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * [phone] What the phone UI asks of the game: requests to the server ({@link PhoneNet.Ask}, never trusted there),
 * sounds, the trail camera library and darkroom, saving a photo, the alarm. Requests are throttled here too (a refresh
 * of the same thing at most twice a second), so a busy app never floods the connection.
 */
final class ClientActions implements PhoneActions {
   private static final SoundEvent[] SFX = new SoundEvent[Sfx.values().length];
   private final long[] lastRefresh = new long[16];
   private SoundInstance call;

   static {
      for (Sfx s : Sfx.values()) {
         SFX[s.ordinal()] = SoundEvent.createVariableRangeEvent(FrontierHunts.id("phone." + s.name().toLowerCase(Locale.ROOT)));
      }
   }

   static void send(PhoneNet.Ask ask) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(PhoneNet.Ask.TYPE)) {
         PacketDistributor.sendToServer(ask);
      }
   }

   private static void op(int op, long a, long b, String s, int[] v) {
      send(new PhoneNet.Ask(op, a, b, 0L, s == null ? "" : s, v, null));
   }

   private PhoneModel m() {
      return PhoneFeed.MODEL;
   }

   @Override
   public void sound(Sfx s) {
      PhoneModel.Settings st = this.m().settings;
      if (st.silent && s != Sfx.ALARM || st.volume <= 0) {
         return;
      }
      float vol = Math.max(0.0F, Math.min(1.0F, st.volume / 100.0F)) * (s == Sfx.TAP || s == Sfx.KEY ? 0.45F : 0.8F);
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SFX[s.ordinal()], 1.0F, vol));
   }

   @Override
   public long millis() {
      return Util.getMillis();
   }

   @Override
   public void close() {
      PhoneClient.closePhone();
   }

   @Override
   public void refresh(int what) {
      if (what < 0 || what >= this.lastRefresh.length) {
         return;
      }
      long now = Util.getMillis();
      if (now - this.lastRefresh[what] < 500L) {
         return;
      }
      this.lastRefresh[what] = now;
      if (what == R_CAMS) {
         this.camHub();
         return;
      }
      op(PhoneNet.OP_REFRESH, what, 0L, "", null);
   }

   @Override
   public void setFlashlight(boolean on) {
      this.m().flashlight = on;
      PhoneClient.sendState();
   }

   @Override
   public void setAlarm(int minuteOfDay) {
      this.m().alarmMinute = minuteOfDay < 0 ? -1 : Math.floorMod(minuteOfDay, 1440);
      PhoneClient.alarmSet();
      PhoneSettings.save(this.m());
   }

   @Override
   public void alarmAnswer(boolean snooze) {
      PhoneClient.alarmAnswer(snooze);
   }

   @Override
   public void settingsChanged() {
      PhoneSettings.save(this.m());
   }

   @Override
   public void darkroom(boolean on) {
      PhoneClient.darkroom(on);
   }

   // ------------------------------------------------------------------------------------------------ trail cameras

   @Override
   public void camHub() {
      op(PhoneNet.OP_CAMERA, PhoneCams.CAM_LIST, 0L, "", null);
      PhoneScreen s = PhoneClient.screen();
      if (s != null) {
         TrailcamClient.requestHub(s);
      }
   }

   @Override
   public void camRoll(long pos) {
      PhoneFeed.openGallery(pos);
      PhoneScreen s = PhoneClient.screen();
      if (s != null) {
         TrailcamClient.requestRoll(s, BlockPos.of(pos));
      }
   }

   @Override
   public void camClear(long pos) {
      op(PhoneNet.OP_CAMERA, PhoneCams.CAM_CLEAR, pos, "", null);
      PhoneScreen s = PhoneClient.screen();
      if (s != null) {
         TrailcamClient.requestRoll(s, BlockPos.of(pos));
      }
   }

   @Override
   public void camWatch(long pos) {
      op(PhoneNet.OP_CAMERA, PhoneCams.CAM_WATCH, pos, "", null);
      PhoneClient.closePhone();
   }

   @Override
   public void camDelete(long pos, PhoneModel.PhotoRef photo) {
      PhoneScreen s = PhoneClient.screen();
      if (s != null && photo instanceof PhoneFeed.PhotoView v) {
         TrailcamClient.delete(s, v.photo);
         PhoneFeed.refreshCams();
      }
   }

   @Override
   public void camSave(String label, PhoneModel.PhotoRef photo) {
      if (!(photo instanceof PhoneFeed.PhotoView v)) {
         return;
      }
      NativeImage img = TrailcamClient.copyFull(v.photo);
      if (img == null) {
         PhoneClient.toast("Still loading: try again in a moment");
         return;
      }
      String clean = label == null ? "camera" : label.replaceAll("[^A-Za-z0-9_-]+", "_");
      String name = "trailcam_" + clean + "_" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT).format(new Date()) + ".png";
      File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
      Util.ioPool().execute(() -> {
         try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
               return;
            }
            img.writeToFile(new File(dir, name));
         } catch (Exception e) {
            // the toast below already said it was saving; a failed write is not worth a crash
         } finally {
            img.close();
         }
      });
      PhoneClient.toast("Saved to screenshots");
   }

   @Override
   public void camPrioritise(PhoneModel.PhotoRef photo) {
      if (photo instanceof PhoneFeed.PhotoView v) {
         TrailcamClient.prioritise(v.photo);
      }
   }

   // ------------------------------------------------------------------------------------------------ map

   @Override
   public void addPin(String name, int icon) {
      op(PhoneNet.OP_PIN_ADD, icon, 0L, name, null);
   }

   @Override
   public void removePlace(String id) {
      op(PhoneNet.OP_PIN_REMOVE, 0L, 0L, id, null);
   }

   @Override
   public void navigate(String id) {
      this.m().map.target = id == null ? "" : id;
      PhoneClient.navigationChanged();
   }

   @Override
   public void mapWanted(boolean on) {
      PhoneMap.wanted(on);
   }

   // ------------------------------------------------------------------------------------------------ contracts and calls

   @Override
   public void contract(int op, int index, String arg) {
      op(PhoneNet.OP_CONTRACT, op, index, arg, null);
   }

   @Override
   public void playCall(String soundId) {
      Minecraft mc = Minecraft.getInstance();
      if (this.call != null) {
         mc.getSoundManager().stop(this.call);
         this.call = null;
      }
      if (soundId == null || soundId.isEmpty() || this.m().settings.volume <= 0) {
         return;
      }
      // the phone's speaker, for the hunter only: animals do not answer a recording
      this.call = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(FrontierHunts.id(soundId)), 1.0F,
         Math.max(0.05F, this.m().settings.volume / 100.0F) * 0.7F);
      mc.getSoundManager().play(this.call);
   }

   // ------------------------------------------------------------------------------------------------ games

   @Override
   public void invite(int game, String player) {
      op(PhoneNet.OP_INVITE, game, 0L, player, null);
   }

   @Override
   public void answerInvite(long id, boolean accept) {
      op(PhoneNet.OP_ANSWER, id, accept ? 1L : 0L, "", null);
   }

   @Override
   public void move(long session, int[] move) {
      op(PhoneNet.OP_MOVE, session, 0L, "", move);
   }

   @Override
   public void leave(long session) {
      op(PhoneNet.OP_LEAVE, session, 0L, "", null);
   }

   @Override
   public void rematch(long session) {
      op(PhoneNet.OP_REMATCH, session, 0L, "", null);
   }

   @Override
   public void flushStart() {
      op(PhoneNet.OP_FLUSH_START, 0L, 0L, "", null);
   }

   @Override
   public void flushSubmit(long token, int[] shots) {
      op(PhoneNet.OP_FLUSH_SUBMIT, token, 0L, "", shots);
   }

   @Override
   public void flushProgress(long session, int score, int tick) {
      op(PhoneNet.OP_FLUSH_PROGRESS, session, score, "", new int[]{tick});
   }

   @Override
   public void gameStat(int game, int mode, int result) {
      op(PhoneNet.OP_STAT, game, mode, "", new int[]{result});
   }

   // ------------------------------------------------------------------------------------------------ messages

   @Override
   public void message(String to, String text) {
      if (to == null || to.isEmpty() || text == null || text.isBlank()) {
         return;
      }
      op(PhoneNet.OP_MESSAGE, 0L, 0L, to + "\n" + (text.length() > 200 ? text.substring(0, 200) : text), null);
      this.sound(Sfx.MESSAGE_OUT);
   }

   @Override
   public void messageRead(String peer) {
      op(PhoneNet.OP_MESSAGE_READ, 0L, 0L, peer, null);
   }

   // ------------------------------------------------------------------------------------------------ [1.4.0] camera

   @Override
   public void camera(boolean selfie, String sendTo) {
      PhoneCamera.open(selfie, sendTo);
   }

   @Override
   public void photosRefresh() {
      PhoneGallery.refresh();
   }

   @Override
   public void photoDelete(PhoneModel.Shot shot) {
      PhoneGallery.delete(shot);
   }

   @Override
   public void photoSend(String to, PhoneModel.Shot shot) {
      sendPhoto(to, PhoneGallery.path(shot));
   }

   @Override
   public void photoFolder() {
      PhoneGallery.openFolder();
   }

   private static final java.util.Random IDS = new java.security.SecureRandom();

   /**
    * Texts a photo: a small JPEG (640 pixels at most, made off the game thread) goes to the server in parts, then the
    * text that shows it. The sender's copy shows at once.
    */
   static void sendPhoto(String to, java.nio.file.Path p) {
      if (to == null || to.isEmpty() || p == null) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      int max = com.formaworks.frontierhunts.phone.PhonePhotos.PART * com.formaworks.frontierhunts.phone.PhonePhotos.MAX_PARTS - 512;
      PhoneGallery.IO.execute(() -> {
         byte[] jpg = com.formaworks.frontierhunts.client.FieldPhotoStore.shareJpeg(p, 640, 640, max);
         mc.execute(() -> {
            if (jpg == null) {
               PhoneClient.toast("That photo couldn't be prepared");
               return;
            }
            if (mc.getConnection() == null || !mc.getConnection().hasChannel(PhoneNet.PhotoUp.TYPE)) {
               PhoneClient.toast("This server can't take photos");
               return;
            }
            long id;
            do {
               id = IDS.nextLong();
            } while (id == 0L);
            int part = com.formaworks.frontierhunts.phone.PhonePhotos.PART;
            int parts = (jpg.length + part - 1) / part;
            for (int i = 0; i < parts; i++) {
               byte[] slice = java.util.Arrays.copyOfRange(jpg, i * part, Math.min(jpg.length, (i + 1) * part));
               PacketDistributor.sendToServer(new PhoneNet.PhotoUp(id, i, parts, slice));
            }
            PhonePhotoCache.put(id, jpg);
            PhoneClient.actions().message(to, com.formaworks.frontierhunts.phone.PhonePhotos.token(id));
            PhoneClient.toast("Photo sent to " + to);
         });
      });
   }
}
