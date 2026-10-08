package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.DeerAnatomyMesh;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerOrgan;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Screen-space half of the kill cam, drawn in the GUI pass (after any shader pack has finished the world image, so it
 * looks the same with Iris on or off): colour grade (desaturate / vignette / impact flash), the X-ray shot-placement
 * view, letterbox bars and the shot card. If the grade shader or the 3D X-ray draw is unavailable it falls back to plain
 * translucent overlays.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class KillCamOverlay {
   static ShaderInstance grade;
   private static TextureTarget copy;
   private static boolean gradeBroken;
   private static boolean xrayBroken;
   private static Matrix4f view = new Matrix4f();
   private static Matrix4f projection = new Matrix4f();
   private static Vec3 cameraPos = Vec3.ZERO;
   private static boolean captured;
   private static float[] organPos;
   private static float[] organNrm;
   private static String kicker = "";
   private static String title = "";
   private static String verdict = "";
   private static String lesson = "";
   private static double cardAt = -1.0;

   private KillCamOverlay() {
   }

   static void reset() {
      cardAt = -1.0;
   }

   static void close() {
      if (copy != null) {
         copy.destroyBuffers();
         copy = null;
      }
      captured = false;
   }

   static void capture(RenderLevelStageEvent event) {
      view = new Matrix4f(event.getModelViewMatrix());
      projection = new Matrix4f(event.getProjectionMatrix());
      cameraPos = event.getCamera().getPosition();
      captured = true;
   }

   // ------------------------------------------------------------------ shot card text

   static void card(KillCamReplay r) {
      cardAt = r.total + 0.12;
      LivingEntity target = r.standIn != null ? r.standIn.entity : r.real;
      int yards = Math.round(r.shot.distance() * 1.0936133F);
      String who;
      if (target instanceof Whitetail deer) {
         DeerTraits t = deer.traits();
         String species = deer.species().title.replace(" deer", "");
         who = species + " " + deer.species().sexName(t.buck());
      } else {
         who = target == null ? "" : target.getType().getDescription().getString();
      }
      DeerAnatomy.Region region = r.region();
      DeerOrgan organ = r.organ();
      if (r.deer && region != null) {
         kicker = (who + "  ·  " + yards + " yd").toUpperCase(Locale.ROOT);
         title = Component.translatable("killcam.frontierhunts.region." + region.name().toLowerCase(Locale.ROOT)).getString().toUpperCase(Locale.ROOT);
         verdict = Component.translatable(region.vital() ? "killcam.frontierhunts.verdict.vital" : "killcam.frontierhunts.verdict.lethal").getString()
            .toUpperCase(Locale.ROOT);
         lesson = organ != null ? organ.description : "";
      } else if (region != null) {
         // [vital] wildlife heart / lung drop: species + range, then the organ
         kicker = (who + "  ·  " + yards + " yd").toUpperCase(Locale.ROOT);
         title = Component.translatable("killcam.frontierhunts.region." + region.name().toLowerCase(Locale.ROOT)).getString().toUpperCase(Locale.ROOT);
         verdict = Component.translatable(region.vital() ? "killcam.frontierhunts.verdict.vital" : "killcam.frontierhunts.verdict.lethal").getString()
            .toUpperCase(Locale.ROOT);
         lesson = "";
      } else {
         kicker = (yards + " yd").toUpperCase(Locale.ROOT);
         title = who.toUpperCase(Locale.ROOT);
         verdict = Component.translatable("killcam.frontierhunts.verdict.lethal").getString().toUpperCase(Locale.ROOT);
         lesson = "";
      }
   }

   // ------------------------------------------------------------------ GUI pass

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void pre(RenderGuiEvent.Pre event) {
      KillCamReplay r = KillCamClient.active;
      if (r == null) {
         return;
      }
      Grade g = grade(r);
      if (g.any()) {
         if (!gradeBroken && grade != null) {
            try {
               gradePass(g);
            } catch (RuntimeException ex) {
               gradeBroken = true;
               LogUtils.getLogger().warn("Frontier Hunts kill cam: colour grade unavailable, using plain overlay", ex);
            }
         }
         if (gradeBroken || grade == null) {
            flatGrade(event.getGuiGraphics(), g);
            event.getGuiGraphics().flush();
         }
      }
      float x = xrayAmount(r);
      if (x > 0.0F && captured) {
         boolean depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
         if (!xrayBroken) {
            try {
               xray(r, x);
            } catch (RuntimeException ex) {
               xrayBroken = true;
               LogUtils.getLogger().warn("Frontier Hunts kill cam: X-ray view unavailable, using the hit marker only", ex);
               restore3d();
            }
         }
         if (depthWas) {
            RenderSystem.enableDepthTest();
         } else {
            RenderSystem.disableDepthTest();
         }
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void post(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      GuiGraphics gg = event.getGuiGraphics();
      int w = gg.guiWidth();
      int h = gg.guiHeight();
      KillCamReplay r = KillCamClient.active;
      if (r != null && !mc.options.hideGui) {
         float bars = bars(r);
         if (bars > 0.0F) {
            int bh = Math.round(h * 0.115F * bars);
            gg.fill(0, 0, w, bh, 0xFF000000);
            gg.fill(0, h - bh, w, h, 0xFF000000);
         }
         if (xrayBroken && xrayAmount(r) > 0.0F) {
            flatMarker(gg, r);
         }
         card(gg, r, w, h);
      }
      float black = 0.0F;
      if (r != null && r.phase == KillCamReplay.Phase.ABORT) {
         black = (float)Math.clamp(r.tp / 0.2, 0.0, 1.0);
      }
      black = Math.max(black, KillCamClient.fadeIn());
      if (black > 0.0F) {
         gg.fill(0, 0, w, h, (int)(black * 255.0F) << 24);
      }
   }

   private static float bars(KillCamReplay r) {
      float in = (float)KillCamReplay.smooth(r.total / 0.35);
      return switch (r.phase) {
         case RETURN -> in * (float)(1.0 - KillCamReplay.smooth(r.tp / r.tReturn));
         case DONE -> 0.0F;
         default -> in;
      };
   }

   private record Grade(float desat, float dark, float tintAmount, float vignette, float flash, float contrast) {
      boolean any() {
         return this.desat > 0.002F || this.dark > 0.002F || this.tintAmount > 0.002F || this.vignette > 0.002F || this.flash > 0.002F;
      }
   }

   private static Grade grade(KillCamReplay r) {
      float base = (float)KillCamReplay.smooth(r.total / 0.35);
      float x = xrayAmount(r);
      float flash = r.flash * (r.bow ? 0.3F : 0.5F);
      float desat = 0.14F * base;
      float vignette = 0.5F * base;
      float contrast = 1.0F + 0.06F * base;
      if (r.phase == KillCamReplay.Phase.RETURN) {
         float k = 1.0F - (float)KillCamReplay.smoother(r.tp / r.tReturn);
         desat *= k;
         vignette *= k;
         contrast = 1.0F + (contrast - 1.0F) * k;
      } else if (r.phase == KillCamReplay.Phase.DONE) {
         return new Grade(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
      }
      desat = Mth.lerp(x, desat, 0.93F);
      vignette = Mth.lerp(x, vignette, 0.7F);
      return new Grade(desat, 0.5F * x, 0.32F * x, vignette, flash, contrast);
   }

   /** 0..1 strength of the X-ray moment (eases in over the first beat, out as the drop begins). */
   static float xrayAmount(KillCamReplay r) {
      if (!r.xray) {
         return 0.0F;
      }
      if (r.phase == KillCamReplay.Phase.XRAY) {
         return (float)KillCamReplay.smooth(r.tp / 0.16);
      }
      if (r.phase == KillCamReplay.Phase.DROP) {
         return 1.0F - (float)KillCamReplay.smooth(r.tp / 0.3);
      }
      return 0.0F;
   }

   private static void gradePass(Grade g) {
      Minecraft mc = Minecraft.getInstance();
      RenderTarget main = mc.getMainRenderTarget();
      if (copy == null || copy.width != main.width || copy.height != main.height) {
         if (copy == null) {
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
         } else {
            copy.resize(main.width, main.height, Minecraft.ON_OSX);
         }
      }
      GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
      GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
      GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
      main.bindWrite(true);
      boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
      int previousTexture = RenderSystem.getShaderTexture(0);
      ShaderInstance previous = RenderSystem.getShader();
      try {
         RenderSystem.disableDepthTest();
         RenderSystem.disableBlend();
         RenderSystem.setShaderTexture(0, copy.getColorTextureId());
         grade.safeGetUniform("Desat").set(g.desat);
         grade.safeGetUniform("Dark").set(g.dark);
         grade.safeGetUniform("TintAmount").set(g.tintAmount);
         grade.safeGetUniform("Tint").set(0.6F, 0.8F, 0.92F);
         grade.safeGetUniform("Vignette").set(g.vignette);
         grade.safeGetUniform("Flash").set(g.flash);
         grade.safeGetUniform("Contrast").set(g.contrast);
         RenderSystem.setShader(() -> grade);
         BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
         b.addVertex(-1.0F, -1.0F, 0.0F);
         b.addVertex(1.0F, -1.0F, 0.0F);
         b.addVertex(1.0F, 1.0F, 0.0F);
         b.addVertex(-1.0F, 1.0F, 0.0F);
         BufferUploader.drawWithShader(b.buildOrThrow());
      } finally {
         RenderSystem.setShaderTexture(0, previousTexture);
         RenderSystem.setShader(() -> previous);
         if (depth) {
            RenderSystem.enableDepthTest();
         }
         if (blend) {
            RenderSystem.enableBlend();
         }
      }
   }

   /** Fallback grade without the shader: a cool, dark wash plus edge darkening. */
   private static void flatGrade(GuiGraphics gg, Grade g) {
      int w = gg.guiWidth();
      int h = gg.guiHeight();
      float a = Math.clamp(g.dark * 0.9F + g.desat * 0.25F, 0.0F, 0.85F);
      if (a > 0.004F) {
         gg.fill(0, 0, w, h, (int)(a * 255.0F) << 24 | 0x141C22);
      }
      if (g.vignette > 0.01F) {
         int edge = Math.round(Math.min(w, h) * 0.18F);
         int c = (int)(g.vignette * 150.0F) << 24;
         gg.fillGradient(0, 0, w, edge, c, 0);
         gg.fillGradient(0, h - edge, w, h, 0, c);
      }
      if (g.flash > 0.01F) {
         gg.fill(0, 0, w, h, (int)(Math.min(1.0F, g.flash) * 200.0F) << 24 | 0xFFFFFF);
      }
   }

   // ------------------------------------------------------------------ X-ray (3D, in the GUI pass)

   private static void xray(KillCamReplay r, float amount) {
      Matrix4fStack mv = RenderSystem.getModelViewStack();
      RenderSystem.backupProjectionMatrix();
      mv.pushMatrix();
      try {
         RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
         mv.identity();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
         RenderSystem.enableDepthTest();
         RenderSystem.depthFunc(GL11.GL_LEQUAL);
         RenderSystem.disableCull();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         if (r.deer && r.standIn != null && !r.standIn.entity.isRemoved() && r.standIn.entity instanceof Whitetail deer) {
            anatomy(r, deer, amount);
         }
         channel(r, amount);
      } finally {
         mv.popMatrix();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.restoreProjectionMatrix();
         restore3d();
      }
   }

   private static void restore3d() {
      RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
      RenderSystem.depthMask(true);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableCull();
   }

   private static Matrix4f model(LivingEntity e, DeerTraits t) {
      Vec3 p = e.position().subtract(cameraPos);
      Matrix4f m = new Matrix4f(view);
      m.translate((float)p.x, (float)p.y, (float)p.z);
      m.rotateY((float)Math.toRadians(180.0F - e.yBodyRot));
      m.scale(t.frameWidth(), t.frameHeight(), t.frameLength());
      return m;
   }

   private static void anatomy(KillCamReplay r, Whitetail deer, float amount) {
      DeerTraits traits = deer.traits();
      float partial = KillCamClient.partialFor(deer);
      DeerAnimator animator = WhitetailRenderer.pose(deer, Float.isNaN(partial) ? 0.0F : partial);
      DeerAnatomyMesh mesh = DeerAnatomyMesh.of(deer.species());
      if (organPos == null || organPos.length != mesh.vertices * 3) {
         organPos = new float[mesh.vertices * 3];
         organNrm = new float[mesh.vertices * 3];
      }
      mesh.skin(animator.skin, organPos, organNrm);
      Matrix4f m = model(deer, traits);
      Vector3f light = new Vector3f(0.35F, 0.62F, 0.7F).normalize();
      DeerOrgan hit = r.organ();
      int lod = switch (HuntConfig.QUALITY.get()) {
         case CINEMATIC -> 0;
         case BALANCED -> 1;
         default -> 2;
      };
      float pulse = 0.5F + 0.5F * Mth.sin((float)r.total * 9.0F);
      // organs and bones: solid, depth tested against each other
      RenderSystem.disableBlend();
      RenderSystem.depthMask(true);
      BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      Vector3f v = new Vector3f();
      Vector3f n = new Vector3f();
      int emitted = 0;
      for (int part = 0; part < mesh.parts; part++) {
         DeerOrgan organ = mesh.partOrgan[part];
         if (organ == DeerOrgan.DIAPHRAGM) {
            continue;
         }
         boolean focus = organ == hit;
         int[] tris = mesh.lods[lod][part];
         for (int k = 0; k < tris.length; k++) {
            int vi = tris[k];
            v.set(organPos[vi * 3], organPos[vi * 3 + 1], organPos[vi * 3 + 2]);
            m.transformPosition(v);
            n.set(organNrm[vi * 3], organNrm[vi * 3 + 1], organNrm[vi * 3 + 2]);
            m.transformDirection(n);
            if (n.lengthSquared() > 1.0E-10F) {
               n.normalize();
            }
            float diff = Math.max(0.0F, n.dot(light));
            float rim = (float)Math.pow(1.0F - Math.abs(n.z), 2.0) * 0.3F;
            float shade = 0.42F + 0.58F * diff + rim;
            int c = mesh.color[vi];
            float cr = (c >> 16 & 255) / 255.0F;
            float cg = (c >> 8 & 255) / 255.0F;
            float cb = (c & 255) / 255.0F;
            if (organ.bone) {
               cr = cr * 0.55F + 0.4F;
               cg = cg * 0.55F + 0.4F;
               cb = cb * 0.55F + 0.38F;
            }
            if (focus) {
               float glow = 0.25F + 0.25F * pulse;
               cr = cr + (1.0F - cr) * glow;
               cg = cg + (0.86F - cg) * glow;
               cb = cb + (0.72F - cb) * glow;
            } else if (hit != null && !organ.bone) {
               float l = cr * 0.3F + cg * 0.55F + cb * 0.15F;
               cr = Mth.lerp(0.35F, cr, l);
               cg = Mth.lerp(0.35F, cg, l);
               cb = Mth.lerp(0.35F, cb, l);
            }
            b.addVertex(v.x, v.y, v.z).setColor(Math.min(1.0F, cr * shade), Math.min(1.0F, cg * shade), Math.min(1.0F, cb * shade), 1.0F);
            emitted++;
         }
      }
      MeshData organs = b.build();
      if (organs != null) {
         BufferUploader.drawWithShader(organs);
      }
      // translucent body shell with a fresnel edge, so the silhouette reads like an X-ray plate
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      RenderSystem.depthMask(false);
      float[][] shell = DeerDraw.skin(animator, traits, 2);
      int[] faces = DeerMeshData.of(deer.species()).faces(2, traits.buck());
      BufferBuilder s = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      float[] sp = shell[0];
      float[] sn = shell[1];
      for (int k = 0; k < faces.length; k++) {
         int vi = faces[k];
         v.set(sp[vi * 3], sp[vi * 3 + 1], sp[vi * 3 + 2]);
         m.transformPosition(v);
         n.set(sn[vi * 3], sn[vi * 3 + 1], sn[vi * 3 + 2]);
         m.transformDirection(n);
         if (n.lengthSquared() > 1.0E-10F) {
            n.normalize();
         }
         Vector3f toEye = new Vector3f(v).negate();
         if (toEye.lengthSquared() > 1.0E-10F) {
            toEye.normalize();
         }
         float facing = Math.abs(n.dot(toEye));
         float edge = (float)Math.pow(1.0F - facing, 2.2);
         float a = (0.05F + 0.55F * edge) * amount;
         s.addVertex(v.x, v.y, v.z).setColor(0.62F + 0.3F * edge, 0.82F + 0.15F * edge, 0.9F + 0.1F * edge, a);
      }
      MeshData shellMesh = s.build();
      if (shellMesh != null) {
         BufferUploader.drawWithShader(shellMesh);
      }
   }

   /** Wound channel through the body plus an entry marker; always on top. */
   private static void channel(KillCamReplay r, float amount) {
      Vec3 entry = r.impact();
      Vec3 d = r.dir();
      Vec3 exit = r.shot.exit();
      double depth = Math.max(0.35, r.bbWidth * 0.9);
      Vec3 end = exit != null ? exit : entry.add(d.scale(r.bow ? depth * 0.8 : depth));
      Vec3 lead = entry.subtract(d.scale(0.45));
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
      RenderSystem.depthMask(false);
      BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
      float pulse = 0.75F + 0.25F * Mth.sin((float)r.total * 9.0F);
      ribbon(b, lead, entry, 0.012F, 0.0F, 1.0F, 0.95F, 0.85F, 0.0F, 0.55F * amount);
      ribbon(b, entry, end, 0.03F, 0.03F, 1.0F, 0.32F, 0.18F, 0.55F * amount * pulse, 0.45F * amount * pulse);
      ribbon(b, entry, end, 0.011F, 0.011F, 1.0F, 0.85F, 0.6F, 0.95F * amount, 0.85F * amount);
      ring(b, entry, d, 0.07F + 0.015F * pulse, 0.012F, 1.0F, 0.93F, 0.82F, 0.9F * amount);
      if (exit != null) {
         ring(b, exit, d, 0.06F, 0.01F, 1.0F, 0.45F, 0.3F, 0.7F * amount);
      }
      MeshData mesh = b.build();
      if (mesh != null) {
         BufferUploader.drawWithShader(mesh);
      }
   }

   private static Vector3f toView(Vec3 world) {
      Vec3 p = world.subtract(cameraPos);
      return view.transformPosition(new Vector3f((float)p.x, (float)p.y, (float)p.z));
   }

   private static void ribbon(BufferBuilder b, Vec3 from, Vec3 to, float w0, float w1, float r, float g, float bl, float a0, float a1) {
      Vector3f p0 = toView(from);
      Vector3f p1 = toView(to);
      Vector3f dir = new Vector3f(p1).sub(p0);
      Vector3f mid = new Vector3f(p0).add(p1).mul(0.5F);
      Vector3f side = dir.cross(mid, new Vector3f());
      if (side.lengthSquared() < 1.0E-10F) {
         return;
      }
      side.normalize();
      b.addVertex(p0.x - side.x * w0, p0.y - side.y * w0, p0.z - side.z * w0).setColor(r, g, bl, a0);
      b.addVertex(p1.x - side.x * w1, p1.y - side.y * w1, p1.z - side.z * w1).setColor(r, g, bl, a1);
      b.addVertex(p1.x + side.x * w1, p1.y + side.y * w1, p1.z + side.z * w1).setColor(r, g, bl, a1);
      b.addVertex(p0.x + side.x * w0, p0.y + side.y * w0, p0.z + side.z * w0).setColor(r, g, bl, a0);
   }

   private static void ring(BufferBuilder b, Vec3 center, Vec3 axis, float radius, float width, float r, float g, float bl, float a) {
      Vector3f c = toView(center);
      int seg = 28;
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2.0 * i / seg;
         double a1 = Math.PI * 2.0 * (i + 1) / seg;
         float c0 = (float)Math.cos(a0);
         float s0 = (float)Math.sin(a0);
         float c1 = (float)Math.cos(a1);
         float s1 = (float)Math.sin(a1);
         // screen-facing ring: in view space x/y are the screen axes
         b.addVertex(c.x + c0 * radius, c.y + s0 * radius, c.z).setColor(r, g, bl, a);
         b.addVertex(c.x + c1 * radius, c.y + s1 * radius, c.z).setColor(r, g, bl, a);
         b.addVertex(c.x + c1 * (radius + width), c.y + s1 * (radius + width), c.z).setColor(r, g, bl, 0.0F);
         b.addVertex(c.x + c0 * (radius + width), c.y + s0 * (radius + width), c.z).setColor(r, g, bl, 0.0F);
         b.addVertex(c.x + c0 * (radius - width), c.y + s0 * (radius - width), c.z).setColor(r, g, bl, 0.0F);
         b.addVertex(c.x + c1 * (radius - width), c.y + s1 * (radius - width), c.z).setColor(r, g, bl, 0.0F);
         b.addVertex(c.x + c1 * radius, c.y + s1 * radius, c.z).setColor(r, g, bl, a);
         b.addVertex(c.x + c0 * radius, c.y + s0 * radius, c.z).setColor(r, g, bl, a);
      }
   }

   /** Fallback hit marker in plain 2D (projected with the captured matrices). */
   private static void flatMarker(GuiGraphics gg, KillCamReplay r) {
      Vector3f v = toView(r.impact());
      org.joml.Vector4f clip = projection.transform(new org.joml.Vector4f(v, 1.0F));
      if (clip.w <= 0.01F) {
         return;
      }
      float sx = (clip.x / clip.w * 0.5F + 0.5F) * gg.guiWidth();
      float sy = (1.0F - (clip.y / clip.w * 0.5F + 0.5F)) * gg.guiHeight();
      int x = Math.round(sx);
      int y = Math.round(sy);
      int c = 0xE0FFD0B0;
      gg.fill(x - 7, y, x - 2, y + 1, c);
      gg.fill(x + 2, y, x + 7, y + 1, c);
      gg.fill(x, y - 7, x + 1, y - 2, c);
      gg.fill(x, y + 2, x + 1, y + 7, c);
   }

   // ------------------------------------------------------------------ shot card

   private static void card(GuiGraphics gg, KillCamReplay r, int w, int h) {
      if (cardAt < 0.0) {
         return;
      }
      double age = r.total - cardAt;
      float in = (float)KillCamReplay.smooth(age / 0.35);
      float out = 1.0F;
      if (r.phase == KillCamReplay.Phase.DROP) {
         out = 1.0F - (float)KillCamReplay.smooth((r.tp - r.tDrop * 0.55) / (r.tDrop * 0.4));
      } else if (r.phase == KillCamReplay.Phase.RETURN || r.phase == KillCamReplay.Phase.DONE) {
         out = 0.0F;
      }
      float a = in * out;
      if (a <= 0.02F) {
         return;
      }
      Font font = Minecraft.getInstance().font;
      int bottom = h - Math.round(h * 0.115F) - 10;
      int x = Math.max(12, Math.round(w * 0.07F)) - Math.round((1.0F - in) * 12.0F);
      int lines = lesson.isEmpty() ? 0 : Math.min(2, font.split(Component.literal(lesson), Math.min(300, w - x - 20)).size());
      int y = bottom - 44 - lines * 10;
      int alpha = Math.round(a * 255.0F) << 24;
      gg.fill(x - 6, y - 2, x - 4, bottom, (int)(a * 200.0F) << 24 | 0xE0503C);
      gg.drawString(font, kicker, x, y, alpha | 0xC9CDD1, false);
      gg.pose().pushPose();
      gg.pose().translate(x, y + 11, 0.0F);
      gg.pose().scale(2.0F, 2.0F, 1.0F);
      gg.drawString(font, title, 0, 0, alpha | 0xFFFFFF, true);
      gg.pose().popPose();
      gg.drawString(font, verdict, x, y + 31, alpha | 0xFF6A4D, false);
      if (lines > 0) {
         List<FormattedCharSequence> wrapped = font.split(Component.literal(lesson), Math.min(300, w - x - 20));
         for (int i = 0; i < lines; i++) {
            gg.drawString(font, wrapped.get(i), x, y + 44 + i * 10, (int)(a * 220.0F) << 24 | 0xB4BAC0, false);
         }
      }
   }
}
