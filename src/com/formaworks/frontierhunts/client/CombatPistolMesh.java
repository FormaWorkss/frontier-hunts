package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Field pistol: a sculpted modern polymer-frame striker pistol built from {@link MeshKit} pieces on a dedicated material
 * sheet (nitride steel, polymer, stippling, TiN/bright steel). Coordinates are weapon-socket space: the muzzle sits at
 * z -0.182, the barrel axis at y 0.117, the grip slopes back and down.
 */
public final class CombatPistolMesh {
   /** Material sheet (held in its own class so offline tools can build the mesh without touching game registries). */
   static final class Tex {
      static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/material/pistol_materials.png");
   }
   /** Slide travel at full cycle; the slide-mounted optic uses the same figure. */
   public static final float SLIDE_TRAVEL = 0.042F;
   public static final double BARREL_Y = 0.117;

   private static final int NITRIDE = 0;
   private static final int POLYMER = 1;
   private static final int STIPPLE = 2;
   private static final int BRIGHT = 3;

   private static final int SLIDE_C = 0x44474C;
   private static final int FRAME_C = 0x8B7B5E;
   private static final int GRIP_C = 0x857558;
   private static final int DARK_C = 0x151618;
   private static final int TIN_C = 0xC9A24F;
   private static final int SIGHT_C = 0x1B1C1E;
   private static final int DOT_C = 0xF2FAEA;
   private static final int CONTROL_C = 0x232427;
   private static final int MAG_C = 0x5C6066;
   private static final int BRASS_C = 0xD0A040;

   private static final double GRIP_ANGLE = Math.toRadians(18.0);
   private static final double GS = Math.sin(GRIP_ANGLE);
   private static final double GC = Math.cos(GRIP_ANGLE);
   private static final double[] GRIP_O = {0.0, 0.062, 0.030};
   private static final double GRIP_LEN = 0.116;
   private static final double TRIGGER_Y = 0.058;
   private static final double TRIGGER_Z = -0.036;

   private static MeshKit.Mesh frame;
   private static MeshKit.Mesh slide;
   private static MeshKit.Mesh barrel;
   private static MeshKit.Mesh trigger;
   private static MeshKit.Mesh magazine;

   /**
    * @param slideT  0..1 slide travel
    * @param reload  0..1 magazine out
    * @param trigger 0..1 trigger pull
    */
   public static void draw(PoseStack pose, VertexConsumer out, int light, float slideT, float reload, float triggerT) {
      build();
      frame.draw(pose, out, light);
      barrel.draw(pose, out, light);
      pose.pushPose();
      pose.translate(0.0F, 0.0F, Math.clamp(slideT, 0.0F, 1.0F) * SLIDE_TRAVEL);
      slide.draw(pose, out, light);
      pose.popPose();
      pose.pushPose();
      pose.translate(0.0, TRIGGER_Y, TRIGGER_Z);
      pose.mulPose(Axis.XP.rotationDegrees(-Math.clamp(triggerT, 0.0F, 1.0F) * 14.0F));
      pose.translate(0.0, -TRIGGER_Y, -TRIGGER_Z);
      trigger.draw(pose, out, light);
      pose.popPose();
      double drop = Math.clamp(reload, 0.0F, 1.0F) * 0.12;
      pose.pushPose();
      pose.translate(0.0, -GC * drop, GS * drop);
      magazine.draw(pose, out, light);
      pose.popPose();
   }

   static synchronized void build() {
      if (frame != null) {
         return;
      }

      slide = buildSlide();
      barrel = buildBarrel();
      trigger = buildTrigger();
      magazine = buildMagazine();
      frame = buildFrame();
   }

   /** Exposed for offline mesh tools. */
   static MeshKit.Mesh[] parts() {
      build();
      return new MeshKit.Mesh[]{frame, barrel, slide, trigger, magazine};
   }

   // ------------------------------------------------------------------ slide

   private static final double SX = 0.021;
   private static final double SY0 = 0.088;
   private static final double SY1 = 0.1445;
   private static final double S_SHOULDER = 0.134;
   private static final double S_TOPX = 0.014;
   private static final double SZ_FRONT = -0.182;
   private static final double SZ_REAR = 0.046;
   private static final double PORT_Z0 = -0.044;
   private static final double PORT_Z1 = -0.002;
   private static final double PORT_Y = 0.120;
   private static final double PORT_WALL = -0.0095;

   private static double[][] slideSection() {
      return new double[][]{
         {-SX + 0.0015, SY0}, {SX - 0.0015, SY0}, {SX, SY0 + 0.0015}, {SX, S_SHOULDER}, {S_TOPX, SY1}, {-S_TOPX, SY1}, {-SX, S_SHOULDER}, {-SX, SY0 + 0.0015}
      };
   }

   private static MeshKit.Mesh buildSlide() {
      MeshKit.Builder b = new MeshKit.Builder();
      double[][] full = slideSection();
      // nose bevel, front body, port section, rear body
      b.prismZ(full, SZ_FRONT, SZ_FRONT + 0.009, 0.9, NITRIDE, SLIDE_C, true);
      b.prismZ(full, SZ_FRONT + 0.009, PORT_Z0, 1.0, NITRIDE, SLIDE_C, true);
      double[][] lower = {{-SX + 0.0015, SY0}, {SX - 0.0015, SY0}, {SX, SY0 + 0.0015}, {SX, PORT_Y}, {-SX, PORT_Y}, {-SX, SY0 + 0.0015}};
      b.prismZ(lower, PORT_Z0, PORT_Z1, 1.0, NITRIDE, SLIDE_C, false);
      double[][] wall = {{-SX, PORT_Y}, {PORT_WALL, PORT_Y}, {PORT_WALL, SY1}, {-S_TOPX, SY1}, {-SX, S_SHOULDER}};
      b.prismZ(wall, PORT_Z0, PORT_Z1, 1.0, NITRIDE, SLIDE_C, false);
      b.prismZ(full, PORT_Z1, SZ_REAR, 1.0, NITRIDE, SLIDE_C, true);
      // port ledge (the breech-side cut edge) reads darker
      b.quad(NITRIDE, MeshKit.shade(SLIDE_C, 0.7), new double[]{PORT_WALL, PORT_Y + 0.0002, PORT_Z0}, new double[]{SX, PORT_Y + 0.0002, PORT_Z0},
         new double[]{SX, PORT_Y + 0.0002, PORT_Z1}, new double[]{PORT_WALL, PORT_Y + 0.0002, PORT_Z1}, 0, 1, 0);
      // extractor
      b.box(SX - 0.0004, PORT_Y - 0.009, PORT_Z1, SX + 0.0011, PORT_Y - 0.002, PORT_Z1 + 0.017, NITRIDE, 0x2A2C30);

      // rear cocking serrations (both sides) and forward serrations
      for (int side = -1; side <= 1; side += 2) {
         double x = side * (SX + 0.0003);
         for (int i = 0; i < 8; i++) {
            double z = 0.004 + i * 0.0049;
            groove(b, x, side, SY0 + 0.004, S_SHOULDER - 0.002, z, z + 0.0024);
         }

         for (int i = 0; i < 5; i++) {
            double z = -0.160 + i * 0.0052;
            groove(b, x, side, SY0 + 0.009, S_SHOULDER - 0.004, z, z + 0.0024);
         }

         // subtle machined flat line along the slide side
         b.quad(NITRIDE, MeshKit.shade(SLIDE_C, 1.18), new double[]{x, S_SHOULDER - 0.0006, SZ_FRONT + 0.012, 0, 0.5}, new double[]{x, S_SHOULDER - 0.0006, SZ_REAR - 0.002, 1, 0.5},
            new double[]{x, S_SHOULDER + 0.0004, SZ_REAR - 0.002, 1, 0.55}, new double[]{x, S_SHOULDER + 0.0004, SZ_FRONT + 0.012, 0, 0.55}, side, 0, 0);
      }

      // rear sight: base and ears, three-dot
      b.box(-0.0165, SY1 - 0.0005, 0.026, 0.0165, SY1 + 0.0045, 0.041, NITRIDE, SIGHT_C);
      b.box(-0.0165, SY1 + 0.0045, 0.028, -0.0038, SY1 + 0.0105, 0.041, NITRIDE, SIGHT_C);
      b.box(0.0038, SY1 + 0.0045, 0.028, 0.0165, SY1 + 0.0105, 0.041, NITRIDE, SIGHT_C);
      b.diskZ(-0.0095, SY1 + 0.0068, 0.0413, 0.0019, 1, 10, BRIGHT, DOT_C);
      b.diskZ(0.0095, SY1 + 0.0068, 0.0413, 0.0019, 1, 10, BRIGHT, DOT_C);
      // front sight post with dot
      b.box(-0.0034, SY1 - 0.0005, -0.171, 0.0034, SY1 + 0.0105, -0.162, NITRIDE, SIGHT_C);
      b.diskZ(0.0, SY1 + 0.0068, -0.1617, 0.002, 1, 10, BRIGHT, DOT_C);
      // striker back plate
      b.box(-0.0105, SY0 + 0.012, SZ_REAR - 0.0004, 0.0105, SY1 - 0.012, SZ_REAR + 0.0012, POLYMER, DARK_C);
      b.diskZ(0.0, BARREL_Y, SZ_REAR + 0.0013, 0.0028, 1, 8, BRIGHT, 0x9A9C9F);
      // hidden breech interior visible through the port as the slide runs back
      b.box(-0.0175, SY0 + 0.004, -0.10, 0.0175, PORT_Y - 0.0004, 0.040, POLYMER, DARK_C);
      return b.mesh();
   }

   private static void groove(MeshKit.Builder b, double x, int side, double y0, double y1, double z0, double z1) {
      b.quad(NITRIDE, MeshKit.shade(SLIDE_C, 0.45), new double[]{x, y0, z0, 0, 0}, new double[]{x, y0, z1, 1, 0}, new double[]{x, y1, z1, 1, 1},
         new double[]{x, y1, z0, 0, 1}, side, 0, 0);
   }

   // ----------------------------------------------------------------- barrel

   private static MeshKit.Mesh buildBarrel() {
      MeshKit.Builder b = new MeshKit.Builder();
      b.cylinderZ(0.0, BARREL_Y, SZ_FRONT - 0.0004, -0.046, 0.0085, 16, BRIGHT, TIN_C);
      b.diskZ(0.0, BARREL_Y, SZ_FRONT - 0.0006, 0.0085, -1, 16, BRIGHT, MeshKit.shade(TIN_C, 0.92));
      b.diskZ(0.0, BARREL_Y, SZ_FRONT - 0.0008, 0.0058, -1, 16, BRIGHT, 0x6E5A30);
      b.diskZ(0.0, BARREL_Y, SZ_FRONT - 0.0010, 0.0044, -1, 16, POLYMER, 0x060607);
      // hood / chamber block seen through the ejection port
      b.box(-0.0092, 0.104, -0.047, 0.0092, SY1 - 0.0006, 0.0005, BRIGHT, TIN_C);
      b.diskZ(0.0, BARREL_Y, 0.0009, 0.0047, 1, 12, POLYMER, 0x0A0A0B);
      // guide rod and recoil spring cup
      b.cylinderZ(0.0, 0.098, SZ_FRONT - 0.0002, -0.100, 0.0037, 10, BRIGHT, 0x2B2D30);
      b.diskZ(0.0, 0.098, SZ_FRONT - 0.0004, 0.0037, -1, 10, BRIGHT, 0x3C3F43);
      return b.mesh();
   }

   // ------------------------------------------------------------------ frame

   private static final double FX = 0.0192;
   private static final double FY0 = 0.062;
   private static final double FY1 = 0.0885;

   private static MeshKit.Mesh buildFrame() {
      MeshKit.Builder b = new MeshKit.Builder();
      double[][] dust = {{-FX + 0.003, FY0}, {FX - 0.003, FY0}, {FX, FY0 + 0.004}, {FX, FY1}, {-FX, FY1}, {-FX, FY0 + 0.004}};
      b.prismZ(dust, -0.172, -0.160, 0.86, POLYMER, FRAME_C, true);
      b.prismZ(dust, -0.160, 0.046, 1.0, POLYMER, FRAME_C, true);
      // accessory rail slots under the dust cover
      for (int i = 0; i < 3; i++) {
         double z = -0.150 + i * 0.016;
         b.quad(POLYMER, DARK_C, new double[]{-FX + 0.003, FY0 - 0.0003, z}, new double[]{FX - 0.003, FY0 - 0.0003, z}, new double[]{FX - 0.003, FY0 - 0.0003, z + 0.0065},
            new double[]{-FX + 0.003, FY0 - 0.0003, z + 0.0065}, 0, -1, 0);
      }

      for (int side = -1; side <= 1; side += 2) {
         double x = side * (FX + 0.0003);
         // rail groove along each side of the dust cover
         b.quad(POLYMER, DARK_C, new double[]{x, FY0 + 0.0065, -0.166}, new double[]{x, FY0 + 0.0065, -0.094}, new double[]{x, FY0 + 0.0095, -0.094},
            new double[]{x, FY0 + 0.0095, -0.166}, side, 0, 0);
         // takedown lever
         b.box(side > 0 ? FX - 0.0004 : -FX - 0.0012, 0.0755, -0.072, side > 0 ? FX + 0.0012 : -FX + 0.0004, 0.0815, -0.060, POLYMER, CONTROL_C);
      }

      // slide stop lever (left) with thumb pad
      b.box(-FX - 0.0016, 0.0805, -0.046, -FX + 0.0004, 0.0855, 0.004, POLYMER, CONTROL_C);
      b.box(-FX - 0.0024, 0.0790, -0.006, -FX + 0.0004, 0.0880, 0.008, POLYMER, CONTROL_C);
      // trigger guard, squared front
      b.tubeYZ(List.of(new double[]{FY0 + 0.001, -0.089}, new double[]{0.046, -0.092}, new double[]{0.028, -0.090}, new double[]{0.017, -0.082},
         new double[]{0.0125, -0.068}, new double[]{0.0115, -0.048}, new double[]{0.0115, -0.018}, new double[]{0.0135, -0.002}, new double[]{0.021, 0.008},
         new double[]{0.031, 0.013}), 0.0072, 0.0029, POLYMER, FRAME_C);
      // trigger bar well: dark slot above the trigger
      b.box(-0.0048, FY0 - 0.0006, -0.052, 0.0048, FY0 + 0.0002, -0.020, POLYMER, DARK_C);
      // beavertail / tang, tapering to the rear
      double[][] tang = {{-0.0178, 0.064}, {0.0178, 0.064}, {0.0178, 0.079}, {0.012, 0.0835}, {-0.012, 0.0835}, {-0.0178, 0.079}};
      b.prismZ(tang, 0.075, 0.040, 0.72, POLYMER, FRAME_C, true);

      // grip, swept back 18 degrees, stippled
      b.transform(gripFrame());
      double[][] grip = gripSection(1.0);
      b.prismZ(grip, -0.012, GRIP_LEN - 0.012, 1.0, STIPPLE, GRIP_C, true);
      // flared magwell lip
      b.prismZ(gripSection(1.045), GRIP_LEN, GRIP_LEN - 0.012, 1.0, POLYMER, FRAME_C, false);
      // dark mag well opening seen from below when the magazine is out
      b.diskZ(0.0, 0.002, GRIP_LEN - 0.0006, 0.0125, 1, 12, POLYMER, DARK_C);
      // smooth front strap and backstrap bands so the stipple reads as panels
      b.box(-0.0105, -0.0252, 0.006, 0.0105, -0.0236, GRIP_LEN - 0.014, POLYMER, FRAME_C);
      b.box(-0.0095, 0.0266, 0.004, 0.0095, 0.0282, GRIP_LEN - 0.014, POLYMER, FRAME_C);
      // magazine release (left, behind the trigger guard)
      b.box(-0.0222, -0.022, 0.018, -0.0180, -0.012, 0.030, POLYMER, CONTROL_C);
      b.transform(null);
      return b.mesh();
   }

   /**
    * [gunsmith] Grip cross-section: the same envelope as before (x +-0.0221, y -0.024..0.027, flat-ish sides, fuller
    * backstrap) but traced as a 24-point rounded outline instead of a 12-point octagon, so the stippled grip reads round.
    */
   private static double[][] gripSection(double s) {
      int n = 24;
      double[][] p = new double[n][];
      for (int i = 0; i < n; i++) {
         double a = Math.PI * 2.0 * i / n - Math.PI / 2.0;
         double c = Math.cos(a);
         double sn = Math.sin(a);
         // superellipse (exponent 2.8) with a slightly fuller rear (backstrap, +y) half
         double ex = 2.0 / 2.8;
         double x = 0.0205 * Math.signum(c) * Math.pow(Math.abs(c), ex);
         double half = sn >= 0.0 ? 0.0255 : 0.0255;
         double y = 0.0015 + half * Math.signum(sn) * Math.pow(Math.abs(sn), ex);
         p[i] = new double[]{x * s * 1.08, 0.0015 + (y - 0.0015) * s};
      }
      return p;
   }

   /** Local x = across, local y = toward the backstrap, local z = down the grip. */
   private static double[] gripFrame() {
      return MeshKit.Builder.frame(GRIP_O, new double[]{1, 0, 0}, new double[]{0, GS, GC}, new double[]{0, -GC, GS});
   }

   // ---------------------------------------------------------------- trigger

   private static MeshKit.Mesh buildTrigger() {
      MeshKit.Builder b = new MeshKit.Builder();
      b.tubeYZ(List.of(new double[]{0.0615, -0.036}, new double[]{0.053, -0.0395}, new double[]{0.044, -0.0415}, new double[]{0.035, -0.040},
         new double[]{0.0275, -0.0345}), 0.0042, 0.0026, POLYMER, 0x1E1F22);
      // safety blade
      b.tubeYZ(List.of(new double[]{0.050, -0.0425}, new double[]{0.042, -0.0445}, new double[]{0.036, -0.0425}), 0.0011, 0.0012, POLYMER, 0x3A3B3E);
      return b.mesh();
   }

   // --------------------------------------------------------------- magazine

   private static MeshKit.Mesh buildMagazine() {
      MeshKit.Builder b = new MeshKit.Builder();
      b.transform(gripFrame());
      double[][] body = {{-0.0095, -0.019}, {0.0095, -0.019}, {0.0105, -0.016}, {0.0105, 0.020}, {0.008, 0.023}, {-0.008, 0.023}, {-0.0105, 0.020}, {-0.0105, -0.016}};
      b.prismZ(body, 0.002, GRIP_LEN, 1.0, BRIGHT, MAG_C, true);
      b.prismZ(gripSection(1.03), GRIP_LEN, GRIP_LEN + 0.012, 1.0, POLYMER, 0x1F2023, true);
      // top round
      b.box(-0.0048, -0.018, -0.003, 0.0048, 0.014, 0.0035, BRIGHT, BRASS_C);
      b.transform(null);
      return b.mesh();
   }

   private CombatPistolMesh() {
   }
}
