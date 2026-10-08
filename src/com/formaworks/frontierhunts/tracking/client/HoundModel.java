package com.formaworks.frontierhunts.tracking.client;

import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * [tracking][hound2][hound3] Vanilla-preset (Minecraft style) box model of the tracking hound, rebuilt in a true hound
 * stance: deep chest and tucked loin on a level back, a neck carried up so the head sits above the topline, square
 * skull with a long muzzle, hanging flews and long leathers, two-piece legs - straight forelegs with the feet under
 * the withers, hind legs angled at stifle and hock with the rear pastern vertical - and a sabre tail carried up.
 * Animation mirrors the sculpted rig's inputs: walk (lateral sequence) / trot (diagonal) / gallop (rotary) from the
 * stride phase, nose-down sniffing, baying, point, sit, lie, slink. 64x64 textures (tools/tracking/hound/classic3.py).
 */
public final class HoundModel extends HierarchicalModel<TrackingHound> {
   private final ModelPart base, root, body, neck, head, earL, earR, tail, tailTip;
   private final ModelPart[] up = new ModelPart[4], low = new ModelPart[4], foot = new ModelPart[2];
   /** animation inputs, set by the renderer before setupAnim */
   public float sniff, sit, bay, tuck, wag, lie, point, walk, trot, gallop, phase, speed;
   /** leg order FL, FR, BL, BR */
   private static final float[] OFF_WALK = {0.25F, 0.75F, 0.0F, 0.5F}, OFF_TROT = {0.25F, 0.75F, 0.75F, 0.25F}, OFF_GALLOP = {0.2F, 0.1F, 0.7F, 0.8F};
   /** rest angles of the hind leg chain: thigh forward, gaskin back, rear pastern plumb */
   static final float THIGH = -0.45F, GASKIN = 1.0F, META = -0.55F;

   public HoundModel(ModelPart base) {
      this.base = base;
      this.root = base.getChild("root");
      this.body = this.root.getChild("body");
      this.neck = this.body.getChild("neck");
      this.head = this.neck.getChild("head");
      this.earL = this.head.getChild("ear_l");
      this.earR = this.head.getChild("ear_r");
      this.tail = this.body.getChild("tail");
      this.tailTip = this.tail.getChild("tail_tip");
      String[] n = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
      for (int i = 0; i < 4; i++) {
         this.up[i] = this.root.getChild(n[i]);
         this.low[i] = this.up[i].getChild("lower");
      }
      this.foot[0] = this.low[2].getChild("foot");
      this.foot[1] = this.low[3].getChild("foot");
   }

   public static LayerDefinition create() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.ZERO);
      // trunk: pivot at the middle of the back, 14 px up = withers ~10 px above the ground
      PartDefinition body = root.addOrReplaceChild(
         "body",
         CubeListBuilder.create()
            .texOffs(0, 0).addBox(-3.0F, -0.5F, -6.5F, 6.0F, 6.0F, 7.0F)      // chest: deep, to the elbows
            .texOffs(0, 13).addBox(-2.5F, 0.0F, 0.5F, 5.0F, 4.0F, 3.0F)       // loin, tucked up
            .texOffs(16, 13).addBox(-3.0F, -0.3F, 3.5F, 6.0F, 4.8F, 3.5F),    // croup and hams
         PartPose.offset(0.0F, 14.0F, 0.0F)
      );
      // neck carried up and forward from the withers
      PartDefinition neck = body.addOrReplaceChild(
         "neck", CubeListBuilder.create().texOffs(26, 0).addBox(-2.0F, -4.5F, -2.0F, 4.0F, 6.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 1.8F, -5.2F, 0.85F, 0.0F, 0.0F)
      );
      PartDefinition head = neck.addOrReplaceChild(
         "head",
         CubeListBuilder.create()
            .texOffs(42, 0).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 4.5F, 5.0F)     // domed skull
            .texOffs(42, 10).addBox(-1.5F, -2.2F, -7.5F, 3.0F, 3.0F, 4.5F)    // long square muzzle
            .texOffs(56, 18).addBox(-1.0F, -2.4F, -7.9F, 2.0F, 1.2F, 1.0F)    // nose leather
            .texOffs(42, 18).addBox(-1.7F, 0.4F, -7.3F, 3.4F, 1.4F, 3.6F),    // hanging flews / lower jaw
         PartPose.offsetAndRotation(0.0F, -4.5F, 0.2F, -0.85F, 0.0F, 0.0F)
      );
      head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(0, 40).addBox(-1.0F, 0.0F, -1.5F, 1.0F, 7.0F, 3.0F), PartPose.offset(-2.5F, -2.4F, -0.8F));
      head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(8, 40).addBox(0.0F, 0.0F, -1.5F, 1.0F, 7.0F, 3.0F), PartPose.offset(2.5F, -2.4F, -0.8F));
      // sabre tail carried up
      PartDefinition tail = body.addOrReplaceChild(
         "tail", CubeListBuilder.create().texOffs(56, 24).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 0.6F, 6.6F, -0.75F, 0.0F, 0.0F)
      );
      tail.addOrReplaceChild(
         "tail_tip", CubeListBuilder.create().texOffs(60, 24).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -4.8F, 0.0F, 0.45F, 0.0F, 0.0F)
      );
      // forelegs: straight, the feet under the withers (shoulder 7.5 px above the ground)
      for (int s = 0; s < 2; s++) {
         float x = s == 0 ? -1.9F : 1.9F;
         PartDefinition leg = root.addOrReplaceChild(
            s == 0 ? "leg_fl" : "leg_fr", CubeListBuilder.create().texOffs(0, 24).addBox(-1.0F, -0.5F, -1.25F, 2.0F, 4.5F, 2.5F), PartPose.offset(x, 16.5F, -4.4F)
         );
         leg.addOrReplaceChild(
            "lower",
            CubeListBuilder.create().texOffs(10, 24).addBox(-0.9F, 0.0F, -0.9F, 1.8F, 3.5F, 1.8F).texOffs(18, 24).addBox(-1.1F, 2.5F, -1.9F, 2.2F, 1.0F, 2.8F),
            PartPose.offset(0.0F, 4.0F, 0.0F)
         );
      }
      // hind legs: thigh forward to the stifle, gaskin back to the hock, rear pastern plumb (hip 8 px up)
      for (int s = 0; s < 2; s++) {
         float x = s == 0 ? -1.9F : 1.9F;
         PartDefinition leg = root.addOrReplaceChild(
            s == 0 ? "leg_bl" : "leg_br",
            CubeListBuilder.create().texOffs(0, 31).addBox(-1.4F, -1.0F, -1.6F, 2.8F, 4.6F, 3.4F),
            PartPose.offsetAndRotation(x, 16.0F, 4.6F, THIGH, 0.0F, 0.0F)
         );
         PartDefinition gaskin = leg.addOrReplaceChild(
            "lower", CubeListBuilder.create().texOffs(14, 31).addBox(-0.9F, 0.0F, -0.9F, 1.8F, 3.6F, 1.8F), PartPose.offsetAndRotation(0.0F, 3.6F, 0.0F, GASKIN, 0.0F, 0.0F)
         );
         gaskin.addOrReplaceChild(
            "foot",
            CubeListBuilder.create().texOffs(22, 31).addBox(-0.8F, 0.0F, -0.8F, 1.6F, 2.2F, 1.6F).texOffs(30, 31).addBox(-1.1F, 1.2F, -2.0F, 2.2F, 1.0F, 2.8F),
            PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, META, 0.0F, 0.0F)
         );
      }
      return LayerDefinition.create(mesh, 64, 64);
   }

   @Override
   public ModelPart root() {
      return this.base;
   }

   private static float circ(float[] a, float[] b, float[] c, int l, float wa, float wb, float wc) {
      float x = 0, y = 0;
      x += wa * Mth.cos(a[l] * Mth.TWO_PI) + wb * Mth.cos(b[l] * Mth.TWO_PI) + wc * Mth.cos(c[l] * Mth.TWO_PI);
      y += wa * Mth.sin(a[l] * Mth.TWO_PI) + wb * Mth.sin(b[l] * Mth.TWO_PI) + wc * Mth.sin(c[l] * Mth.TWO_PI);
      return (float)Math.atan2(y, x) / Mth.TWO_PI;
   }

   @Override
   public void setupAnim(TrackingHound e, float limbSwing, float amount, float age, float yaw, float pitch) {
      this.base.getAllParts().forEach(ModelPart::resetPose);
      float still = Math.max(this.sit, this.lie);
      float gw = Mth.clamp(this.walk + this.trot + this.gallop, 0.0F, 1.0F);
      float mv = gw * Mth.clamp(this.speed / 0.03F, 0.0F, 1.0F) * (1.0F - still);
      float gal = this.gallop * mv;
      float cyc = this.phase * Mth.TWO_PI;
      float duty = (0.64F * this.walk + 0.46F * this.trot + 0.3F * this.gallop) / Math.max(gw, 1.0E-3F);
      // [hound4] swing just far enough that the planted paw covers the body's advance (leg ~0.42 m hip/shoulder to
      // ground at the 0.9 render scale): the phase runs at speed / HoundRig.strideFor, so the feet don't skate
      float reach = HoundRig.strideFor(this.walk, this.trot, this.gallop, this.speed) * duty;
      float swingA = (float)Math.asin(Mth.clamp(reach / 0.84F, 0.0F, 0.8F));
      for (int l = 0; l < 4; l++) {
         boolean front = l < 2;
         float p = this.phase + circ(OFF_WALK, OFF_TROT, OFF_GALLOP, l, this.walk + 1.0E-4F, this.trot, this.gallop);
         p -= Mth.floor(p);
         float sw, fold;
         if (p < duty) {
            sw = 1.0F - 2.0F * p / duty;     // planted: swings from forward to back
            fold = 0.0F;
         } else {
            float q = (p - duty) / (1.0F - duty);
            sw = -1.0F + 2.0F * q * q * (3.0F - 2.0F * q);
            fold = Mth.sin(q * Mth.PI);
         }
         // xRot < 0 swings the foot forward
         this.up[l].xRot += -swingA * sw * mv;
         if (front) {
            this.low[l].xRot += 1.3F * fold * mv;
         } else {
            this.low[l].xRot += 0.5F * fold * mv;
            this.foot[l - 2].xRot -= 0.4F * fold * mv;
         }
      }
      this.body.y += -0.35F * mv * (0.5F + 0.5F * Mth.cos(2.0F * cyc)) * (1.0F - this.gallop) - 0.8F * gal * Mth.sin(cyc + 1.1F);
      this.body.xRot += 0.08F * gal * Mth.sin(cyc - 0.3F);
      // head look
      float look = (1.0F - this.sniff) * (1.0F - this.bay * 0.7F);
      this.head.yRot += Mth.clamp(yaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD * look;
      this.head.xRot += pitch * Mth.DEG_TO_RAD * look;
      // [hound3] (+xRot tips the neck forward / the nose down) neck reaches forward in the trot and gallop
      this.neck.xRot += 0.2F * this.trot * mv + 0.35F * gal;
      this.head.xRot -= 0.1F * this.trot * mv + 0.2F * gal;
      // sniff: neck down to the ground, nose sweeping
      this.neck.xRot += 1.25F * this.sniff;
      this.head.xRot -= 0.35F * this.sniff;
      this.neck.yRot += 0.3F * this.sniff * Mth.sin(age * 0.32F);
      // bay: head thrown up
      this.neck.xRot -= 0.5F * this.bay;
      this.head.xRot -= 0.7F * this.bay;
      // point: head level and forward, left foreleg lifted
      this.neck.xRot += 0.3F * this.point;
      this.head.xRot -= 0.3F * this.point;
      this.up[0].xRot -= 0.5F * this.point;
      this.low[0].xRot += 1.6F * this.point;
      // slink
      this.neck.xRot += 0.5F * this.tuck;
      this.head.xRot -= 0.2F * this.tuck;
      this.body.y += 1.0F * this.tuck;
      // tail: sabre carried up, wagging; streams in the gallop, straight back on point, clamped when slinking
      float wag = this.wag * 0.5F * Mth.sin(age * 1.45F) + 0.05F * Mth.sin(age * 0.09F);
      this.tail.yRot += wag * (1.0F - this.point);
      this.tailTip.yRot += wag * 0.6F * (1.0F - this.point);
      this.tail.xRot += 0.25F * this.sniff - 0.4F * gal - 0.8F * this.point - 1.9F * this.tuck;
      this.tailTip.xRot -= 0.4F * this.point;
      // sit: the trunk tips up on the hips, rump to the ground, forelegs straight, hocks down
      if (this.sit > 0.0F) {
         float s = this.sit;
         this.body.xRot -= 0.62F * s;
         this.body.y += 2.4F * s;
         this.neck.xRot += 0.55F * s;
         this.up[0].y += 0.4F * s;
         this.up[1].y += 0.4F * s;
         this.up[0].z += 0.8F * s;
         this.up[1].z += 0.8F * s;
         for (int l = 2; l < 4; l++) {
            this.up[l].y += 4.6F * s;
            this.up[l].z -= 0.6F * s;
            this.up[l].xRot += -1.0F * s;
            this.low[l].xRot += 1.04F * s;
            this.foot[l - 2].xRot += -1.61F * s;
         }
         this.tail.xRot += -0.25F * s;
         this.tailTip.xRot -= 0.45F * s;
      }
      // lie: sphinx down, chest on the ground, forelegs out in front, hinds folded
      if (this.lie > 0.0F) {
         float s = this.lie;
         this.body.y += 4.6F * s;
         this.neck.xRot -= 0.1F * s;
         for (int l = 0; l < 2; l++) {
            this.up[l].y += 5.6F * s;
            this.up[l].xRot += -1.45F * s;
            this.low[l].xRot += -0.1F * s;
         }
         for (int l = 2; l < 4; l++) {
            this.up[l].y += 5.4F * s;
            this.up[l].xRot += -1.0F * s;
            this.low[l].xRot += 1.1F * s;
            this.foot[l - 2].xRot += -1.67F * s;
         }
         this.tail.xRot += -0.8F * s;
         this.tailTip.xRot -= 0.45F * s;
      }
      // the long leathers hang with gravity (undo the head + neck + body pitch) and flop with the gait
      float hang = this.head.xRot + this.neck.xRot + this.body.xRot;
      float flop = Mth.sin(2.0F * cyc) * 0.12F * mv + Mth.sin(age * 0.1F) * 0.03F;
      this.earL.xRot -= hang * 0.95F;
      this.earR.xRot -= hang * 0.95F;
      this.earL.zRot += 0.05F + flop + 0.12F * this.bay + 0.15F * gal;
      this.earR.zRot -= 0.05F + flop + 0.12F * this.bay + 0.15F * gal;
   }
}
