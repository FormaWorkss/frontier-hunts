package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.client.trailcam.DarkroomHost;
import com.formaworks.frontierhunts.client.trailcam.Photo;
import com.formaworks.frontierhunts.client.trailcam.TrailcamClient;
import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/**
 * [trailcam] The trail camera's memory card: a grid of real developed photos (rendered from the lens by the
 * darkroom), a full-size viewer with arrows, save-to-screenshots and delete. Photos that are still developing show a
 * darkroom safelight placeholder. The screen is fully opaque, because the world behind it is being rendered from the
 * camera's point of view while photos develop.
 */
public final class TrailCameraScreen extends Screen implements DarkroomHost {
   private static final int VOID = 0xFF090B0A;
   private static final int PAPER = -15328488;
   private static final int INK = -1580333;
   private static final int MUTED = -6971249;
   private static final int GOLD = -4614055;
   private static final int RULE = -13945296;
   private static final int PICK = -14274518;
   private static final int CELL = -14867424;
   private static final int LIVE = -7946118;
   private static final int LOW = -3701935;
   private static final int NIGHT = -7363128;
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
   private ScoutingNetwork.Roll roll;
   private final BlockPos console;
   private int left;
   private int top;
   private int panelWidth;
   private int panelHeight;
   private int bodyTop;
   private int bodyBottom;
   private int scroll;
   private int maxScroll;
   /** -1 = grid, else index of the photo in the viewer */
   private int viewing = -1;
   private long viewingId;
   private long confirmDelete;
   private long confirmUntil;
   private long confirmClearUntil;
   private String toast = "";
   private long toastUntil;
   private boolean requested;
   private int age;

   private TrailCameraScreen(ScoutingNetwork.Roll roll, BlockPos console) {
      super(Component.literal(roll.label()));
      this.roll = roll;
      this.console = console;
   }

   public static void open(ScoutingNetwork.Roll roll, BlockPos console) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof TrailCameraScreen screen && screen.roll.pos().equals(roll.pos())) {
         screen.roll = roll;
         // the roll changed (cleared, new frames): fetch the photo list again
         TrailcamClient.requestRoll(screen, roll.pos());
         screen.rebuild();
         return;
      }
      mc.setScreen(new TrailCameraScreen(roll, console));
   }

   @Override
   public BlockPos console() {
      return this.console;
   }

   @Override
   public BlockPos uplinkCamera() {
      return this.roll.pos();
   }

   private void rebuild() {
      this.clearWidgets();
      this.init();
   }

   private void ask(int action) {
      PacketDistributor.sendToServer(new ScoutingNetwork.Request(this.console, this.roll.pos(), action));
   }

   private List<Photo> photos() {
      return TrailcamClient.roll(this.roll.pos());
   }

   @Override
   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
   }

   @Override
   protected void init() {
      if (!this.requested) {
         this.requested = true;
         TrailcamClient.requestRoll(this, this.roll.pos());
         com.formaworks.frontierhunts.camload.CamLoadNet.query(this.roll.pos()); // [camload] does it keep its area loaded?
      }
      this.panelWidth = Math.min(780, this.width - 12);
      this.panelHeight = Math.min(470, this.height - 12);
      this.left = (this.width - this.panelWidth) / 2;
      this.top = (this.height - this.panelHeight) / 2;
      this.bodyTop = this.top + 48;
      this.bodyBottom = this.top + this.panelHeight - 32;
      int y = this.top + this.panelHeight - 26;
      int right = this.left + this.panelWidth - 8;
      this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose()).bounds(right - 52, y, 52, 18).build());
      if (this.viewing >= 0) {
         int x = this.left + 8;
         this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.step(-1)).bounds(x, y, 22, 18).build());
         this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.step(1)).bounds(x + 24, y, 22, 18).build());
         this.addRenderableWidget(Button.builder(Component.literal("Save to screenshots"), b -> this.save()).bounds(x + 52, y, 118, 18).build());
         Photo p = this.current();
         boolean confirming = p != null && this.confirmDelete == p.id && Util.getMillis() < this.confirmUntil;
         this.addRenderableWidget(
            Button.builder(Component.literal(confirming ? "Confirm delete" : "Delete"), b -> this.delete()).bounds(x + 174, y, confirming ? 90 : 52, 18).build()
         );
         this.addRenderableWidget(Button.builder(Component.literal("All photos"), b -> this.grid()).bounds(right - 128, y, 72, 18).build());
      } else {
         int x = this.left + 8;
         boolean confirming = Util.getMillis() < this.confirmClearUntil;
         this.addRenderableWidget(Button.builder(Component.literal(confirming ? "Confirm clear" : "Clear roll"), b -> {
            if (Util.getMillis() < this.confirmClearUntil) {
               this.confirmClearUntil = 0L;
               this.ask(2);
            } else {
               this.confirmClearUntil = Util.getMillis() + 4000L;
            }
            this.rebuild();
         }).bounds(x, y, 80, 18).build());
         x += 84;
         if (this.roll.charge() > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("View lens"), b -> this.ask(4)).bounds(x, y, 72, 18).build());
            x += 76;
         }
         if (this.console != null && !this.console.equals(this.roll.pos())) {
            this.addRenderableWidget(
               Button.builder(Component.literal("All cameras"), b -> PacketDistributor.sendToServer(new ScoutingNetwork.Request(this.console, this.roll.pos(), 0)))
                  .bounds(x, y, 80, 18)
                  .build()
            );
         }
      }
   }

   @Override
   public void tick() {
      if (++this.age % 30 == 0 && !TrailcamClient.hasRoll(this.roll.pos())) {
         TrailcamClient.requestRoll(this, this.roll.pos()); // the first request can be rate-limited right after the hub's
      }
      long now = Util.getMillis();
      if (this.confirmUntil != 0L && now > this.confirmUntil || this.confirmClearUntil != 0L && now > this.confirmClearUntil) {
         this.confirmUntil = 0L;
         this.confirmDelete = 0L;
         this.confirmClearUntil = 0L;
         this.rebuild();
      }
      List<Photo> photos = this.photos();
      if (this.viewing >= 0) {
         // keep the viewer on the same photo when the list changes under it (new frame arrived, one deleted)
         int idx = -1;
         for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).id == this.viewingId) {
               idx = i;
               break;
            }
         }
         if (idx < 0) {
            if (photos.isEmpty()) {
               this.grid();
            } else {
               this.show(Math.min(this.viewing, photos.size() - 1));
            }
         } else {
            this.viewing = idx;
            TrailcamClient.prioritise(photos.get(idx));
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ rendering

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      // fully opaque: the world is being rendered from the trail camera while photos develop
      g.fill(0, 0, this.width, this.height, VOID);
      g.fill(this.left, this.top, this.left + this.panelWidth, this.top + this.panelHeight, PAPER);
      g.fill(this.left, this.top, this.left + this.panelWidth, this.top + 2, GOLD);
      this.header(g);
      if (this.viewing >= 0) {
         this.renderViewer(g, mouseX, mouseY);
      } else {
         this.renderGrid(g, mouseX, mouseY);
      }
      if (!this.toast.isEmpty() && Util.getMillis() < this.toastUntil) {
         int tw = this.font.width(this.toast) + 16;
         int tx = this.left + (this.panelWidth - tw) / 2;
         int ty = this.bodyBottom - 22;
         g.fill(tx, ty, tx + tw, ty + 16, 0xE0101412);
         g.fill(tx, ty, tx + 2, ty + 16, LIVE);
         g.drawString(this.font, this.toast, tx + 9, ty + 4, INK, false);
      }
      super.render(g, mouseX, mouseY, partial);
   }

   private void header(GuiGraphics g) {
      g.drawString(this.font, this.roll.label(), this.left + 10, this.top + 9, INK, false);
      int pct = Math.round(this.roll.charge() * 100.0F / Math.max(1, this.roll.capacity()));
      String battery = "Battery " + pct + "%";
      g.drawString(this.font, battery, this.left + this.panelWidth - 10 - this.font.width(battery), this.top + 9, pct < 15 ? LOW : MUTED, false);
      g.fill(this.left + 10, this.top + 20, this.left + this.panelWidth - 10, this.top + 22, -14406618);
      g.fill(this.left + 10, this.top + 20, this.left + 10 + (this.panelWidth - 20) * pct / 100, this.top + 22, pct < 15 ? LOW : -8545160);
      List<Photo> photos = this.photos();
      int developing = 0;
      for (Photo p : photos) {
         if (p.state == Photo.State.QUEUED || p.state == Photo.State.DEVELOPING || p.state == Photo.State.REMOTE || p.state == Photo.State.LOADING) {
            developing++;
         }
      }
      String count = photos.size() + (photos.size() == 1 ? " photo" : " photos");
      if (!TrailcamClient.hasRoll(this.roll.pos())) {
         count = "Reading the memory card…";
      } else if (developing > 0) {
         count += "  ·  developing " + developing;
      }
      g.drawString(this.font, count, this.left + 10, this.top + 29, developing > 0 ? GOLD : MUTED, false);
      String status = TrailcamClient.uplinkStatus();
      if (status == null) {
         status = this.roll.live() ? "ON THE NETWORK · LIVE" : "OFF-GRID · LOGGING";
         if (this.roll.live() && com.formaworks.frontierhunts.camload.CamLoadNet.keepsLoaded(this.roll.pos())) {
            status = "LIVE · KEEPS ITS AREA LOADED"; // [camload]
         }
      }
      g.drawString(this.font, status, this.left + this.panelWidth - 10 - this.font.width(status), this.top + 29, this.roll.live() ? LIVE : MUTED, false);
      g.fill(this.left + 10, this.top + 42, this.left + this.panelWidth - 10, this.top + 43, RULE);
   }

   private int columns() {
      return Mth.clamp((this.panelWidth - 20) / 168, 2, 5);
   }

   private int cellWidth() {
      int cols = this.columns();
      return (this.panelWidth - 20 - 8 * (cols - 1)) / cols;
   }

   private int cellHeight() {
      return this.cellWidth() * 9 / 16 + 26;
   }

   private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
      List<Photo> photos = this.photos();
      int cols = this.columns();
      int cw = this.cellWidth();
      int ch = this.cellHeight();
      int th = cw * 9 / 16;
      int x0 = this.left + 10;
      if (photos.isEmpty()) {
         String a = TrailcamClient.hasRoll(this.roll.pos()) ? "Nothing has walked past yet." : "Reading the memory card…";
         g.drawString(this.font, a, this.left + this.panelWidth / 2 - this.font.width(a) / 2, this.bodyTop + 40, INK, false);
         String b = "The camera photographs animals and people that move through its frame.";
         g.drawString(this.font, b, this.left + this.panelWidth / 2 - this.font.width(b) / 2, this.bodyTop + 54, MUTED, false);
         this.maxScroll = 0;
         return;
      }
      int rows = (photos.size() + cols - 1) / cols;
      this.maxScroll = Math.max(0, rows * (ch + 8) - 8 - (this.bodyBottom - this.bodyTop));
      this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll);
      g.enableScissor(this.left + 4, this.bodyTop, this.left + this.panelWidth - 4, this.bodyBottom);
      float time = Util.getMillis() / 1000.0F;
      for (int i = 0; i < photos.size(); i++) {
         int cx = x0 + i % cols * (cw + 8);
         int cy = this.bodyTop + i / cols * (ch + 8) - this.scroll;
         if (cy > this.bodyBottom || cy + ch < this.bodyTop) {
            continue;
         }
         Photo p = photos.get(i);
         boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch && mouseY >= this.bodyTop && mouseY < this.bodyBottom;
         g.fill(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, hover ? GOLD : RULE);
         g.fill(cx, cy, cx + cw, cy + ch, hover ? PICK : CELL);
         this.picture(g, p, cx, cy, cw, th, time, true);
         int ty = cy + th + 4;
         String when = p.when();
         g.drawString(this.font, when, cx + 4, ty, p.infrared() ? NIGHT : GOLD, false);
         if (p.infrared()) {
            g.drawString(this.font, "IR", cx + cw - 4 - this.font.width("IR"), ty, NIGHT, false);
         }
         g.drawString(this.font, this.font.plainSubstrByWidth(p.title(), cw - 8), cx + 4, ty + 11, INK, false);
      }
      g.disableScissor();
      if (this.maxScroll > 0) {
         int track = this.bodyBottom - this.bodyTop;
         int knob = Math.max(14, track * track / (track + this.maxScroll));
         int ky = this.bodyTop + (track - knob) * this.scroll / this.maxScroll;
         g.fill(this.left + this.panelWidth - 5, this.bodyTop, this.left + this.panelWidth - 3, this.bodyBottom, -14406618);
         g.fill(this.left + this.panelWidth - 5, ky, this.left + this.panelWidth - 3, ky + knob, GOLD);
      }
   }

   private void renderViewer(GuiGraphics g, int mouseX, int mouseY) {
      List<Photo> photos = this.photos();
      if (photos.isEmpty() || this.viewing >= photos.size()) {
         return;
      }
      Photo p = photos.get(this.viewing);
      int maxW = this.panelWidth - 20;
      int maxH = this.bodyBottom - this.bodyTop - 28;
      int pw = Math.min(maxW, maxH * 16 / 9);
      int ph = pw * 9 / 16;
      int px = this.left + (this.panelWidth - pw) / 2;
      int py = this.bodyTop;
      g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, RULE);
      this.picture(g, p, px, py, pw, ph, Util.getMillis() / 1000.0F, false);
      int ly = py + ph + 5;
      String title = p.title();
      String detail = p.detail();
      g.drawString(this.font, title, px, ly, INK, false);
      if (!detail.isEmpty()) {
         g.drawString(this.font, this.font.plainSubstrByWidth("·  " + detail, pw - this.font.width(title) - 8), px + this.font.width(title) + 6, ly, MUTED, false);
      }
      String when = p.when() + (p.frameNo() > 0 ? String.format(Locale.ROOT, "  ·  frame #%04d", p.frameNo()) : "") + (p.infrared() ? "  ·  infrared" : "");
      g.drawString(this.font, when, px, ly + 11, p.infrared() ? NIGHT : GOLD, false);
      String pos = this.viewing + 1 + " / " + photos.size();
      g.drawString(this.font, pos, px + pw - this.font.width(pos), ly + 11, MUTED, false);
      if (p.state == Photo.State.FAILED && !p.note.isEmpty()) {
         g.drawString(this.font, p.note, px + pw - this.font.width(p.note), ly, LOW, false);
      }
   }

   /** The photo itself, or the darkroom placeholder while it develops. */
   private void picture(GuiGraphics g, Photo p, int x, int y, int w, int h, float time, boolean thumb) {
      ResourceLocation tex = thumb ? p.thumbLoc : TrailcamClient.full(p);
      if (tex == null && !thumb) {
         tex = p.thumbLoc;
      }
      if (tex != null && p.state == Photo.State.READY) {
         int tw = thumb || tex == p.thumbLoc ? TrailcamClient.THUMB_W : p.full.getPixels().getWidth();
         int tht = thumb || tex == p.thumbLoc ? TrailcamClient.THUMB_H : p.full.getPixels().getHeight();
         g.blit(tex, x, y, w, h, 0.0F, 0.0F, tw, tht, tw, tht);
         return;
      }
      if (p.state == Photo.State.FAILED) {
         if (p.deerSubject()) {
            TrailcamBridge.composite(g, x, y, w, h, p.legacyFrame(), p.id, time);
         } else {
            TrailcamBridge.empty(g, x, y, w, h, (byte)(p.infrared() ? 2 : 0), p.id, "No image", time);
         }
         return;
      }
      // darkroom safelight: a deep red tray with a slow sweep while the frame develops
      g.fill(x, y, x + w, y + h, 0xFF140606);
      boolean developing = p.state == Photo.State.DEVELOPING;
      int sweep = (int)((time * 0.35F % 1.0F) * (w + h));
      for (int i = 0; i < 6; i++) {
         int sx = x + sweep - h + i * 3;
         g.fill(Math.max(x, sx), y, Math.min(x + w, sx + 2), y + h, developing ? 0x302A0A08 : 0x14200808);
      }
      g.fill(x, y + h - 2, x + w, y + h, 0xFF3A0C0A);
      String label = switch (p.state) {
         case DEVELOPING -> "DEVELOPING";
         case QUEUED -> "IN THE TRAY";
         case REMOTE -> TrailcamClient.uplinkStatus() != null ? "UPLINK" : "WAITING FOR SIGNAL";
         case LOADING -> "LOADING";
         default -> "WAITING";
      };
      int col = developing ? 0xFFE0564A : 0xFF9A3B33;
      float pulse = developing ? 0.55F + 0.45F * Mth.sin(time * 4.0F) : 1.0F;
      int a = (int)(255 * pulse) << 24;
      g.drawString(this.font, label, x + w / 2 - this.font.width(label) / 2, y + h / 2 - 4, col & 0xFFFFFF | a, false);
      if (p.state == Photo.State.REMOTE && !thumb) {
         String hint = "The ground around this camera is not loaded; it will come over the uplink.";
         g.drawString(this.font, this.font.plainSubstrByWidth(hint, w - 12), x + 6, y + h - 14, 0xFF9A3B33, false);
      }
   }

   // ------------------------------------------------------------------------------------------------ actions

   private Photo current() {
      List<Photo> photos = this.photos();
      return this.viewing >= 0 && this.viewing < photos.size() ? photos.get(this.viewing) : null;
   }

   private void show(int index) {
      List<Photo> photos = this.photos();
      if (photos.isEmpty()) {
         this.grid();
         return;
      }
      this.viewing = Mth.clamp(index, 0, photos.size() - 1);
      this.viewingId = photos.get(this.viewing).id;
      TrailcamClient.prioritise(photos.get(this.viewing));
      this.confirmDelete = 0L;
      this.rebuild();
   }

   private void grid() {
      this.viewing = -1;
      this.confirmDelete = 0L;
      this.rebuild();
   }

   private void step(int d) {
      List<Photo> photos = this.photos();
      if (this.viewing >= 0 && !photos.isEmpty()) {
         this.show(Math.floorMod(this.viewing + d, photos.size()));
      }
   }

   private void delete() {
      Photo p = this.current();
      if (p == null) {
         return;
      }
      if (this.confirmDelete == p.id && Util.getMillis() < this.confirmUntil) {
         this.confirmDelete = 0L;
         int index = this.viewing;
         TrailcamClient.delete(this, p);
         this.say("Photo deleted from the camera");
         List<Photo> photos = this.photos();
         if (photos.isEmpty()) {
            this.grid();
         } else {
            this.show(Math.min(index, photos.size() - 1));
         }
      } else {
         this.confirmDelete = p.id;
         this.confirmUntil = Util.getMillis() + 4000L;
         this.rebuild();
      }
   }

   private void save() {
      Photo p = this.current();
      if (p == null || p.state != Photo.State.READY) {
         this.say(p != null && p.state == Photo.State.FAILED ? "This frame has no image to save" : "Still developing…");
         return;
      }
      NativeImage copy = TrailcamClient.copyFull(p);
      if (copy == null) {
         this.say("Loading the full photo, try again in a moment");
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      String cam = this.roll.label().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
      String name = "trailcam-" + (cam.isEmpty() ? "camera" : cam) + "-" + String.format(Locale.ROOT, "%04d", p.frameNo()) + "-"
         + LocalDateTime.now().format(STAMP) + ".png";
      Path dir = mc.gameDirectory.toPath().resolve("screenshots");
      Path file = dir.resolve(name);
      this.say("Saving…");
      Util.ioPool().execute(() -> {
         boolean ok;
         try (NativeImage img = copy) {
            Files.createDirectories(dir);
            // [bugs] write to a temp file and rename into place, so a crash mid-write never leaves a truncated .png
            Path part = file.resolveSibling(file.getFileName() + ".part");
            try {
               img.writeToFile(part);
               try {
                  Files.move(part, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
               } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                  Files.move(part, file);
               }
            } finally {
               Files.deleteIfExists(part);
            }
            ok = true;
         } catch (Exception e) {
            LoggerFactory.getLogger(TrailCameraScreen.class).error("Could not save trail camera photo", e);
            ok = false;
         }
         boolean saved = ok;
         mc.execute(() -> {
            if (saved) {
               File f = file.toFile();
               Component link = Component.literal(f.getName())
                  .withStyle(ChatFormatting.UNDERLINE)
                  .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, f.getAbsolutePath())));
               if (mc.player != null) {
                  mc.player.displayClientMessage(Component.literal("Saved trail camera photo as ").append(link), false);
               }
               if (mc.screen == this) {
                  this.say("Saved to screenshots/" + f.getName());
               }
            } else if (mc.screen == this) {
               this.say("Could not save the photo");
            }
         });
      });
   }

   private void say(String text) {
      this.toast = text;
      this.toastUntil = Util.getMillis() + 3500L;
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (super.mouseClicked(mx, my, button)) {
         return true;
      }
      if (this.viewing < 0 && my >= this.bodyTop && my < this.bodyBottom) {
         List<Photo> photos = this.photos();
         int cols = this.columns();
         int cw = this.cellWidth();
         int ch = this.cellHeight();
         int col = (int)((mx - this.left - 10) / (cw + 8));
         int row = (int)((my - this.bodyTop + this.scroll) / (ch + 8));
         double inX = mx - this.left - 10 - col * (cw + 8);
         double inY = my - this.bodyTop + this.scroll - row * (ch + 8);
         int index = row * cols + col;
         if (col >= 0 && col < cols && inX < cw && inY < ch && index >= 0 && index < photos.size()) {
            this.show(index);
            return true;
         }
      } else if (this.viewing >= 0 && my >= this.bodyTop && my < this.bodyBottom - 28) {
         // click the left or right third of the photo to browse
         double rel = (mx - this.left) / this.panelWidth;
         if (rel < 0.33) {
            this.step(-1);
            return true;
         }
         if (rel > 0.67) {
            this.step(1);
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.viewing >= 0) {
         this.step(sy > 0 ? -1 : 1);
      } else {
         this.scroll = Mth.clamp(this.scroll - (int)(sy * (this.cellHeight() + 8) / 2), 0, this.maxScroll);
      }
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (this.viewing >= 0) {
         if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            this.step(-1);
            return true;
         }
         if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            this.step(1);
            return true;
         }
         if (key == GLFW.GLFW_KEY_DELETE) {
            this.delete();
            return true;
         }
         if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.grid();
            return true;
         }
      }
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public void removed() {
      TrailcamClient.hostClosed();
      super.removed();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
