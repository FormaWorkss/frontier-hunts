package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.FieldCamera;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Post;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;
import org.slf4j.LoggerFactory;

/**
 * Field camera viewfinder and gallery.
 *
 * Capture: the shutter hides the HUD, this screen and the first-person hand, waits two frames so temporal shader
 * effects settle, then copies the main render target at the end of the frame. That target holds the final composited
 * world image (vanilla, Sodium, or an Iris shader pack after its final pass), at the framebuffer's real size, so the photo
 * matches what is on screen for any preset, GUI scale, window size, fullscreen or render scale. The chosen filter is
 * applied to the saved file. Files are written atomically by {@link FieldPhotoStore}.
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldPhotoMode extends Screen {
   private static final String[] FILTERS = new String[]{"Natural", "Monochrome", "Warm print"};
   private static final int SETTLE_FRAMES = 2;
   private static final int BLANK_RETRIES = 3;
   private static final int SHUTTER_TIMEOUT = 240;
   private static boolean settingsLoaded;
   private static int savedFilter;
   private static double savedZoom = 1.0;
   private static int maxTexture;
   private int filter;
   private double zoom = 1.0;
   private boolean shutter;
   private int shutterWait;
   private int shutterFrames;
   private int blankRetries;
   private boolean oldHud;
   private String status = "";
   private boolean gallery;
   private double galleryZoom = 1.0;
   private double galleryPanX;
   private double galleryPanY;
   private final List<FieldPhotoStore.Photo> photos = new ArrayList<>();
   private boolean folderError;
   private int photoIndex;
   private ResourceLocation shownPhoto;
   private int photoWidth;
   private int photoHeight;
   private int fileWidth;
   private int fileHeight;
   private int loadGeneration;
   private int listGeneration;
   private boolean listing;
   private boolean loading;
   private boolean autoSkip;
   private int skipped;
   private String photoNote = "";

   public FieldPhotoMode() {
      super(Component.literal("Frontier field camera"));
      loadSettings();
      this.filter = Math.floorMod(savedFilter, FILTERS.length);
      this.zoom = Mth.clamp(savedZoom, 1.0, 6.0);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
   }

   @Override
   protected void init() {
      if (maxTexture <= 0) {
         try {
            maxTexture = RenderSystem.maxSupportedTextureSize();
         } catch (Throwable e) {
            maxTexture = 8192;
         }
      }

      if (this.gallery) {
         this.addRenderableWidget(
            Button.builder(Component.literal("Previous"), b -> this.step(-1)).bounds(this.width / 2 - 118, this.height - 33, 72, 20).build()
         );
         this.addRenderableWidget(Button.builder(Component.literal("Next"), b -> this.step(1)).bounds(this.width / 2 - 37, this.height - 33, 72, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Back to camera"), b -> {
            this.gallery = false;
            this.listGeneration++;
            this.listing = false;
            this.loadGeneration++;
            this.releasePhoto();
            this.rebuild();
         }).bounds(this.width / 2 + 44, this.height - 33, 118, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose()).bounds(this.width - 66, 12, 54, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Folder"), b -> {
            Path dir = FieldPhotoStore.folder(this.minecraft);
            try {
               Files.createDirectories(dir);
               Util.getPlatform().openFile(dir.toFile());
            } catch (Exception e) {
               this.photoNote = "Photos are in " + dir;
            }
         }).bounds(this.width - 126, 12, 54, 20).build());
      } else {
         this.addRenderableWidget(Button.builder(Component.literal("Photo: " + FILTERS[this.filter]), b -> {
            this.filter = (this.filter + 1) % FILTERS.length;
            b.setMessage(Component.literal("Photo: " + FILTERS[this.filter]));
            savedFilter = this.filter;
            saveSettings();
         }).bounds(this.width / 2 - 154, this.height - 33, 146, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Take photo"), b -> this.capture()).bounds(this.width / 2 + 8, this.height - 33, 110, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Gallery"), b -> this.openGallery()).bounds(this.width / 2 + 126, this.height - 33, 78, 20).build());
         this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose()).bounds(this.width - 66, 12, 54, 20).build());
      }
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   public void capture() {
      if (this.shutter || this.minecraft == null || this.minecraft.level == null) {
         return;
      }

      if (FieldPhotoStore.pending() >= FieldPhotoStore.MAX_PENDING) {
         this.status = "Still saving earlier photos, one moment";
         return;
      }

      this.oldHud = this.minecraft.options.hideGui;
      this.minecraft.options.hideGui = true;
      this.shutter = true;
      this.shutterWait = SETTLE_FRAMES;
      this.shutterFrames = 0;
      this.blankRetries = 0;
   }

   private void endShutter() {
      if (this.shutter && this.minecraft != null) {
         this.minecraft.options.hideGui = this.oldHud;
      }

      this.shutter = false;
   }

   private void saved(FieldPhotoStore.Saved result) {
      this.status = result.ok() ? "Saved: " + result.path().getFileName() + " · Gallery to view" : "Photo could not be saved: " + result.error();
      if (this.gallery) {
         Path current = this.current();
         boolean wasPending = current != null && (current.equals(result.requested()) || current.equals(result.path()));
         this.refreshList(() -> {
            int idx = wasPending ? -1 : (current == null ? -1 : this.indexOf(current));
            if (idx < 0 && result.ok()) {
               idx = this.indexOf(result.path());
            }

            if (idx >= 0) {
               this.photoIndex = idx;
               if (wasPending || this.shownPhoto == null && !this.loading) {
                  this.showPhoto(idx);
               }
            } else if (this.shownPhoto == null && !this.loading) {
               this.showPhoto(0);
            }
         });
      }
   }

   private Path current() {
      return this.photos.isEmpty() ? null : this.photos.get(Math.floorMod(this.photoIndex, this.photos.size())).path();
   }

   private int indexOf(Path p) {
      Path key = p.toAbsolutePath().normalize();
      for (int i = 0; i < this.photos.size(); i++) {
         if (this.photos.get(i).path().toAbsolutePath().normalize().equals(key)) {
            return i;
         }
      }

      return -1;
   }

   /** Lists the photo folders on the IO pool (big or cloud-synced folders can be slow) and applies the result on the client thread. */
   private void refreshList(Runnable then) {
      int generation = ++this.listGeneration;
      this.listing = true;
      Minecraft mc = this.minecraft;
      Path root = FieldPhotoStore.folder(mc);
      Util.ioPool().execute(() -> {
         FieldPhotoStore.Listing result;
         try {
            result = FieldPhotoStore.list(root);
         } catch (Throwable e) {
            LoggerFactory.getLogger(FieldPhotoMode.class).warn("Cannot list field photographs", e);
            result = new FieldPhotoStore.Listing(List.of(), true);
         }

         FieldPhotoStore.Listing done = result;
         mc.execute(() -> {
            if (generation == this.listGeneration && this.gallery && this.minecraft != null && this.minecraft.screen == this) {
               this.listing = false;
               this.photos.clear();
               this.photos.addAll(done.photos());
               this.folderError = done.folderError();
               then.run();
            }
         });
      });
   }

   private void openGallery() {
      this.gallery = true;
      this.releasePhoto();
      this.loadGeneration++;
      this.loading = false;
      this.photoNote = "";
      this.photos.clear();
      this.photoIndex = 0;
      this.rebuild();
      this.refreshList(() -> {
         this.photoIndex = 0;
         this.autoSkip = true;
         this.skipped = 0;
         this.showPhoto(0);
      });
   }

   private void step(int dir) {
      if (!this.photos.isEmpty() && !this.listing) {
         this.autoSkip = false;
         this.showPhoto(this.photoIndex + dir);
      }
   }

   private void showPhoto(int index) {
      this.releasePhoto();
      this.loading = false;
      this.photoNote = "";
      if (this.photos.isEmpty()) {
         return;
      }

      this.photoIndex = Math.floorMod(index, this.photos.size());
      this.galleryZoom = 1.0;
      this.galleryPanX = this.galleryPanY = 0.0;
      FieldPhotoStore.Photo photo = this.photos.get(this.photoIndex);
      if (photo.pending()) {
         this.photoNote = "Developing this photograph...";
         return;
      }

      this.loading = true;
      int generation = ++this.loadGeneration;
      int max = maxTexture > 0 ? maxTexture : 8192;
      Minecraft mc = this.minecraft;
      Util.ioPool().execute(() -> {
         FieldPhotoStore.Decoded decoded;
         try {
            decoded = FieldPhotoStore.decode(photo.path(), max);
         } catch (Throwable e) {
            decoded = new FieldPhotoStore.Decoded();
            decoded.error = String.valueOf(e.getMessage());
         }

         FieldPhotoStore.Decoded result = decoded;
         mc.execute(() -> this.accept(generation, photo, result));
      });
   }

   private void accept(int generation, FieldPhotoStore.Photo photo, FieldPhotoStore.Decoded decoded) {
      if (generation != this.loadGeneration || !this.gallery || this.minecraft == null || this.minecraft.screen != this) {
         decoded.close();
         return;
      }

      this.loading = false;
      if (decoded.image == null) {
         // [bugs] a damaged file leaves this gallery session (FieldPhotoStore remembers it and logs it once), so browsing
         // never decodes it again; the file itself is left untouched
         int at = this.photos.indexOf(photo);
         if (at >= 0) {
            this.photos.remove(at);
            if (this.photoIndex > at) {
               this.photoIndex--;
            }
            if (!this.photos.isEmpty()) {
               this.photoIndex = Math.floorMod(this.photoIndex, this.photos.size());
            }
         }
         if (this.autoSkip && !this.photos.isEmpty()) {
            this.skipped++;
            this.showPhoto(at >= 0 ? this.photoIndex : this.photoIndex + 1);
            return;
         }

         this.autoSkip = false;
         this.photoNote = "Could not open " + photo.name() + " - the file was left untouched";
         return;
      }

      try {
         DynamicTexture texture = new DynamicTexture(decoded.image);
         texture.setFilter(true, false);
         this.photoWidth = decoded.image.getWidth();
         this.photoHeight = decoded.image.getHeight();
         this.fileWidth = decoded.fileWidth;
         this.fileHeight = decoded.fileHeight;
         decoded.image = null;
         this.shownPhoto = this.minecraft.getTextureManager().register("frontier_photo_gallery", texture);
      } catch (Throwable e) {
         decoded.close();
         this.photoNote = "Could not display " + photo.name();
         LoggerFactory.getLogger(FieldPhotoMode.class).warn("Cannot display field photograph {}", photo.path(), e);
         return;
      }

      if (this.autoSkip && this.skipped > 0) {
         this.photoNote = this.skipped + (this.skipped == 1 ? " newer file" : " newer files") + " could not be opened; showing the newest readable photo";
      } else if (decoded.partial) {
         this.photoNote = "Part of this file is damaged; showing what could be recovered";
      } else if (decoded.scaled) {
         this.photoNote = "Shown scaled down; the saved file keeps full size";
      }

      this.autoSkip = false;
   }

   private void releasePhoto() {
      if (this.shownPhoto != null && this.minecraft != null) {
         this.minecraft.getTextureManager().release(this.shownPhoto);
      }

      this.shownPhoto = null;
   }

   @Override
   public void tick() {
      if (this.minecraft.player == null
         || !(this.minecraft.player.getMainHandItem().getItem() instanceof FieldCamera)
            && !(this.minecraft.player.getOffhandItem().getItem() instanceof FieldCamera)) {
         this.onClose();
      }
   }

   @Override
   public void onClose() {
      this.endShutter();
      this.loadGeneration++;
      this.releasePhoto();
      savedZoom = this.zoom;
      saveSettings();
      super.onClose();
   }

   @Override
   public void removed() {
      this.endShutter();
      this.loadGeneration++;
      this.releasePhoto();
      super.removed();
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (this.gallery && key == 263) {
         this.step(-1);
         return true;
      } else if (this.gallery && key == 262) {
         this.step(1);
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   @Override
   public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      if (this.gallery && button == 0) {
         this.galleryPanX += dx;
         this.galleryPanY += dy;
         return true;
      } else if (!this.gallery && button == 0 && y > 45.0 && y < (double)(this.height - 45) && this.minecraft.player != null) {
         this.minecraft.player.setYRot(this.minecraft.player.getYRot() + (float)(dx * 0.16 / this.zoom));
         this.minecraft.player.setXRot(Mth.clamp(this.minecraft.player.getXRot() + (float)(dy * 0.16 / this.zoom), -85.0F, 85.0F));
         return true;
      } else {
         return super.mouseDragged(x, y, button, dx, dy);
      }
   }

   @Override
   public boolean mouseScrolled(double x, double y, double sx, double sy) {
      if (this.gallery) {
         this.galleryZoom = Math.clamp(this.galleryZoom * Math.pow(1.25, sy), 1.0, 12.0);
      } else {
         this.zoom = Math.clamp(this.zoom + sy * 0.25, 1.0, 6.0);
         savedZoom = this.zoom;
      }

      return true;
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      if (this.shutter) {
         return;
      }

      if (this.gallery) {
         g.fill(0, 0, this.width, this.height, -267380971);
         g.drawString(this.font, "FIELD CAMERA  /  PHOTO GALLERY", 16, 14, 14867908, false);
         if (this.shownPhoto != null) {
            int areaW = Math.max(1, this.width - 40);
            int areaH = Math.max(1, this.height - 110);
            double fit = Math.min((double)areaW / (double)this.photoWidth, (double)areaH / (double)this.photoHeight);
            int w = Math.max(1, (int)((double)this.photoWidth * fit * this.galleryZoom));
            int h = Math.max(1, (int)((double)this.photoHeight * fit * this.galleryZoom));
            g.enableScissor(12, 36, this.width - 12, this.height - 72);
            g.blit(
               this.shownPhoto,
               (int)((double)((this.width - w) / 2) + this.galleryPanX),
               (int)((double)(36 + (areaH - h) / 2) + this.galleryPanY),
               w,
               h,
               0.0F,
               0.0F,
               this.photoWidth,
               this.photoHeight,
               this.photoWidth,
               this.photoHeight
            );
            g.disableScissor();
         } else {
            g.drawCenteredString(this.font, this.centreMessage(), this.width / 2, this.height / 2, 14867908);
         }

         if (this.shownPhoto != null && !this.photoNote.isEmpty()) {
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.photoNote, this.width - 30), this.width / 2, this.height - 70, -4798287);
         }

         if (!this.photos.isEmpty()) {
            FieldPhotoStore.Photo photo = this.photos.get(Math.floorMod(this.photoIndex, this.photos.size()));
            String info = this.photoIndex + 1 + " / " + this.photos.size() + "   " + photo.name();
            if (this.shownPhoto != null) {
               info = info + "   " + this.fileWidth + "x" + this.fileHeight;
            }

            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(info, this.width - 30), this.width / 2, this.height - 57, -3880003);
         }

         g.drawString(this.font, "Wheel: zoom   Drag: pan   Arrows: browse", 14, this.height - 45, -4798287, false);
         super.render(g, mx, my, pt);
      } else {
         g.fill(0, 0, this.width, 44, -1256839903);
         g.fill(0, this.height - 46, this.width, this.height, -719968991);
         g.drawString(this.font, "FRONTIER  /  FIELD CAMERA", 16, 12, 14867908, false);
         g.drawString(this.font, String.format(Locale.ROOT, "%.2fx   ·   Drag to frame / wheel to zoom", this.zoom), 16, 27, 11912884, false);

         for (int i = 1; i <= 2; i++) {
            g.fill(this.width * i / 3, 49, this.width * i / 3 + 1, this.height - 51, 953672911);
            g.fill(12, this.height * i / 3, this.width - 12, this.height * i / 3 + 1, 953672911);
         }

         String line = this.status.isEmpty() ? FILTERS[this.filter] + " filter is applied to the saved photograph" : this.status;
         g.drawCenteredString(this.font, this.font.plainSubstrByWidth(line, this.width - 20), this.width / 2, this.height - 59, 14999496);
         super.render(g, mx, my, pt);
      }
   }

   private String centreMessage() {
      if (this.listing && this.photos.isEmpty()) {
         return "Loading photos...";
      } else if (this.photos.isEmpty()) {
         return this.folderError ? "Could not open the screenshots folder" : "No field photographs yet · take one with Take photo";
      } else if (this.loading) {
         return "Developing photograph...";
      } else {
         return this.photoNote.isEmpty() ? "Developing photograph..." : this.photoNote;
      }
   }

   @SubscribeEvent
   public static void fov(ComputeFov event) {
      if (Minecraft.getInstance().screen instanceof FieldPhotoMode screen && !screen.gallery) {
         event.setFOV(event.getFOV() / screen.zoom);
      }
   }

   /** The viewfinder shows the world without the held item, so the photo matches it exactly. */
   @SubscribeEvent
   public static void hand(RenderHandEvent event) {
      if (Minecraft.getInstance().screen instanceof FieldPhotoMode screen && !screen.gallery) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void frame(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (!(mc.screen instanceof FieldPhotoMode screen) || !screen.shutter) {
         return;
      }

      if (++screen.shutterFrames > SHUTTER_TIMEOUT) {
         screen.endShutter();
         screen.status = "The camera could not get a frame, try again";
         return;
      }

      if (screen.shutterWait > 0) {
         screen.shutterWait--;
         return;
      }

      RenderTarget target = mc.getMainRenderTarget();
      if (mc.level == null || mc.noRender || target == null || target.width <= 0 || target.height <= 0) {
         return;
      }

      NativeImage image;
      try {
         image = Screenshot.takeScreenshot(target);
      } catch (Throwable e) {
         screen.endShutter();
         screen.status = "The camera could not read this frame";
         LoggerFactory.getLogger(FieldPhotoMode.class).error("Cannot read the frame for a field photograph", e);
         return;
      }

      if (FieldPhotoStore.looksBlank(image) && screen.blankRetries++ < BLANK_RETRIES) {
         image.close();
         return;
      }

      screen.endShutter();
      screen.status = "Developing photo...";
      try {
         FieldPhotoStore.save(mc, image, screen.filter, result -> {
            if (Minecraft.getInstance().screen instanceof FieldPhotoMode open) {
               open.saved(result);
            }
         });
      } catch (Throwable e) {
         image.close();
         screen.status = "Photo could not be saved";
         LoggerFactory.getLogger(FieldPhotoMode.class).error("Cannot save field photograph", e);
      }
   }

   private static Path settingsFile() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("frontierhunts-field-camera.properties");
   }

   private static void loadSettings() {
      if (!settingsLoaded) {
         settingsLoaded = true;
         try {
            Path file = settingsFile();
            if (Files.isRegularFile(file)) {
               Properties p = new Properties();
               try (Reader r = Files.newBufferedReader(file)) {
                  p.load(r);
               }

               savedFilter = Math.floorMod(Integer.parseInt(p.getProperty("filter", "0").trim()), FILTERS.length);
               double z = Double.parseDouble(p.getProperty("zoom", "1").trim());
               savedZoom = Double.isFinite(z) ? Mth.clamp(z, 1.0, 6.0) : 1.0;
            }
         } catch (Exception e) {
            LoggerFactory.getLogger(FieldPhotoMode.class).debug("Field camera settings unreadable, using defaults", e);
         }
      }
   }

   private static void saveSettings() {
      try {
         Path file = settingsFile();
         Files.createDirectories(file.getParent());
         Properties p = new Properties();
         p.setProperty("filter", Integer.toString(savedFilter));
         p.setProperty("zoom", String.format(Locale.ROOT, "%.2f", savedZoom));
         try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "Frontier Hunts field camera");
         }
      } catch (Exception e) {
         LoggerFactory.getLogger(FieldPhotoMode.class).debug("Cannot save field camera settings", e);
      }
   }
}
