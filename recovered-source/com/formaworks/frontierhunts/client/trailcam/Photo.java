package com.formaworks.frontierhunts.client.trailcam;

import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.trailcam.TrailcamScene;
import java.util.Locale;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

/** One trail camera photo on the client: its scene record, its developed image (if any) and display labels. */
public final class Photo {
   public enum State {
      /** the server has not sent the scene yet */
      WAITING,
      /** in the darkroom queue */
      QUEUED,
      /** being rendered right now */
      DEVELOPING,
      /** loading the developed image from the cache */
      LOADING,
      READY,
      /** needs the camera's ground loaded: waits for the uplink */
      REMOTE,
      /** could not be developed (the old composite frame is shown instead) */
      FAILED
   }

   private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
   public final long id;
   public final BlockPos camera;
   public CompoundTag scene;
   public CompoundTag legacy = new CompoundTag();
   public State state = State.WAITING;
   public String note = "";
   public DynamicTexture thumb;
   public ResourceLocation thumbLoc;
   public DynamicTexture full;
   public ResourceLocation fullLoc;
   public long usedAt;
   public int attempts;
   private String title;
   private String detail;
   private String when;

   Photo(long id, BlockPos camera) {
      this.id = id;
      this.camera = camera;
   }

   public boolean infrared() {
      return this.scene != null ? this.scene.getBoolean("ir") : this.legacy.getByte("sky") == 2;
   }

   public int frameNo() {
      return this.scene == null ? 0 : this.scene.getInt("n");
   }

   public long takenAt() {
      return this.scene != null ? this.scene.getLong("t") : this.legacy.getLong("at");
   }

   void labelsDirty() {
      this.title = null;
   }

   public String title() {
      this.labels();
      return this.title;
   }

   public String detail() {
      this.labels();
      return this.detail;
   }

   public String when() {
      this.labels();
      return this.when;
   }

   private void labels() {
      if (this.title != null) {
         return;
      }
      if (this.scene == null) {
         DeerTraits t = DeerTraits.load(this.legacy.getCompound("traits"));
         this.title = TrailcamScene.deerTitle(t);
         this.detail = TrailcamScene.deerDetail(t);
         this.when = String.format(Locale.ROOT, "%02d:00", this.legacy.getInt("hour"));
         return;
      }
      ListTag subjects = this.scene.getList("subjects", 10);
      String head = "Nothing in frame";
      String sub = "";
      StringBuilder others = new StringBuilder();
      for (int i = 0; i < subjects.size(); i++) {
         CompoundTag s = subjects.getCompound(i);
         String name = name(s);
         if (i == 0) {
            head = name;
            if (s.contains("traits", 10)) {
               sub = TrailcamScene.deerDetail(DeerTraits.load(s.getCompound("traits")));
            } else if (s.getByte("k") == 1) {
               sub = "Player";
            } else {
               sub = "";
            }
         } else if (i < 4) {
            others.append(others.length() == 0 ? "with " : ", ").append(name);
         } else if (i == 4) {
            others.append(" and more");
         }
      }
      this.title = head;
      this.detail = others.length() == 0 ? sub : (sub.isEmpty() ? others.toString() : sub + " · " + others);
      String date = "";
      if (this.scene.contains("mo")) {
         date = MONTHS[Math.floorMod(this.scene.getInt("mo"), 12)] + " " + this.scene.getInt("dy");
      }
      this.when = TrailcamScene.clock(this.scene.getLong("d")) + (date.isEmpty() ? "" : " · " + date);
   }

   static String name(CompoundTag s) {
      String lbl = s.getString("lbl");
      if (!lbl.isEmpty()) {
         return lbl;
      }
      String key = s.getString("key");
      return key.isEmpty() ? "Unknown" : I18n.get(key);
   }

   /** Frame for the old composite renderer, used as the fallback picture. */
   public ScoutingNetwork.Frame legacyFrame() {
      CompoundTag l = this.legacy;
      return new ScoutingNetwork.Frame(
         DeerTraits.load(l.getCompound("traits")),
         l.getLong("at"),
         l.getString("date"),
         l.getInt("hour"),
         l.getString("over"),
         l.getFloat("distance"),
         l.getFloat("bearing"),
         l.getFloat("body"),
         l.getByte("stance"),
         l.getByte("sky")
      );
   }

   /** True when the headline subject is a deer, elk or moose (the only thing the composite fallback can draw). */
   public boolean deerSubject() {
      if (this.scene == null) {
         return true;
      }
      ListTag subjects = this.scene.getList("subjects", 10);
      return !subjects.isEmpty() && subjects.getCompound(0).contains("traits", 10);
   }
}
