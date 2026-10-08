package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Vanilla-style animation for the Frontier wildlife box models (Classic look): the same leg swing, head look and
 * idle motion as Minecraft's own quadrupeds and birds, plus poses for grazing, resting, alert and warning.
 */
public final class WildlifeModel extends HierarchicalModel<WildlifeMob> {
   private final ModelPart base;
   private final ModelPart root;
   private final ModelPart body;
   private final ModelPart head;
   private final ModelPart neck;
   private final ModelPart tail;
   private final ModelPart tailTip;
   private final ModelPart legFL;
   private final ModelPart legFR;
   private final ModelPart legBL;
   private final ModelPart legBR;
   private final ModelPart wingL;
   private final ModelPart wingR;
   /** [wingshot] the hand (outer wing) of the box birds */
   private final ModelPart tipL, tipR;
   private final boolean bird;
   private final float restDrop;
   private final boolean heavy;
   private final float headDown;

   public WildlifeModel(ModelPart base, String id) {
      this.base = base;
      this.root = base.getChild("root");
      this.body = this.root.getChild("body");
      this.neck = this.body.hasChild("neck") ? this.body.getChild("neck") : null;
      this.head = this.neck != null ? this.neck.getChild("head") : this.body.getChild("head");
      this.tail = this.body.hasChild("tail") ? this.body.getChild("tail") : null;
      this.tailTip = this.tail != null && this.tail.hasChild("tail_tip") ? this.tail.getChild("tail_tip") : null;
      float[] meta = WildlifeModels.meta(id);
      this.bird = meta[0] > 0.5F;
      this.restDrop = meta[1];
      this.heavy = meta[3] > 0.5F;
      this.headDown = meta[5];
      if (this.bird) {
         this.legFL = this.root.getChild("leg_l");
         this.legFR = this.root.getChild("leg_r");
         this.legBL = null;
         this.legBR = null;
         this.wingL = this.body.getChild("wing_l");
         this.wingR = this.body.getChild("wing_r");
         this.tipL = this.wingL.hasChild("wing_l_tip") ? this.wingL.getChild("wing_l_tip") : null; // [wingshot]
         this.tipR = this.wingR.hasChild("wing_r_tip") ? this.wingR.getChild("wing_r_tip") : null;
      } else {
         this.legFL = this.root.getChild("leg_fl");
         this.legFR = this.root.getChild("leg_fr");
         this.legBL = this.root.getChild("leg_bl");
         this.legBR = this.root.getChild("leg_br");
         this.wingL = null;
         this.wingR = null;
         this.tipL = null;
         this.tipR = null;
      }
   }

   @Override
   public ModelPart root() {
      return this.base;
   }

   @Override
   public void setupAnim(WildlifeMob e, float limbSwing, float amount, float age, float yaw, float pitch) {
      this.base.getAllParts().forEach(ModelPart::resetPose);
      int state = e.behavior();
      boolean resting = state == WildlifeMob.REST && amount < 0.05F;
      float swing = limbSwing * 0.6662F;

      // ---- head look (neck carries the yaw where there is one)
      float hy = Mth.clamp(yaw, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
      float hp = pitch * Mth.DEG_TO_RAD;
      if (this.neck != null) {
         this.neck.yRot += hy * 0.6F;
         this.head.yRot += hy * 0.4F;
      } else {
         this.head.yRot += hy;
      }
      this.head.xRot += hp + this.headDown;

      if (this.bird) {
         float a = Mth.cos(swing) * 1.4F * amount;
         this.legFL.xRot += a;
         this.legFR.xRot -= a;
         boolean flying = !e.onGround() && !e.isInWater();
         float flap = flying ? Mth.cos(age * 1.3F) * 0.9F + 0.6F : 0.0F;
         this.wingL.zRot += flap;
         this.wingR.zRot -= flap;
         if (!flying && state != WildlifeMob.REST) {
            this.head.z += Mth.sin(age * 0.35F) * 0.4F * Math.min(1.0F, amount * 3.0F); // pecking bob while walking
         }
         if (e.isInWater()) {
            this.root.y += 3.0F; // sit on the water
            this.legFL.visible = false;
            this.legFR.visible = false;
         }
      } else {
         float amp = this.heavy ? 1.0F : 1.4F;
         float a = Mth.cos(swing) * amp * amount;
         this.legFL.xRot += a;
         this.legBR.xRot += a;
         this.legFR.xRot -= a;
         this.legBL.xRot -= a;
         // a little weight shift at speed
         this.body.y += Math.abs(Mth.sin(swing)) * -0.6F * amount;
      }

      if (this.tail != null) {
         this.tail.zRot += Mth.sin(age * 0.1F) * 0.08F + Mth.cos(swing) * 0.15F * amount;
      }

      switch (state) {
         case WildlifeMob.FEED -> {
            float nibble = Mth.sin(age * 0.45F) * 0.06F;
            if (this.neck != null) {
               this.neck.xRot += 1.05F + nibble;
               this.head.xRot += 0.3F;
            } else if (this.bird) {
               this.head.xRot += 0.8F + nibble;
            } else {
               this.head.xRot += 0.95F + nibble;
               this.head.y += 1.0F;
            }
         }
         case WildlifeMob.REST -> {
            if (resting) {
               this.root.y += this.restDrop;
               if (this.bird) {
                  this.legFL.visible = false;
                  this.legFR.visible = false;
               } else {
                  this.legFL.xRot = -1.45F;
                  this.legFR.xRot = -1.45F;
                  this.legBL.xRot = 1.45F;
                  this.legBR.xRot = 1.45F;
               }
               this.head.xRot += 0.2F;
               if (this.tail != null) {
                  this.tail.xRot += 0.4F;
               }
            }
         }
         case WildlifeMob.ALERT -> {
            if (this.neck != null) {
               this.neck.xRot -= 0.2F;
            }
            this.head.xRot -= 0.25F;
            if (this.tail != null) {
               this.tail.xRot -= 0.25F;
            }
         }
         case WildlifeMob.CURIOUS -> this.head.zRot += Mth.sin(age * 0.08F) * 0.18F;
         case WildlifeMob.FLEE -> {
            if (this.tail != null) {
               this.tail.xRot -= 0.35F;
            }
         }
         case WildlifeMob.WARN -> {
            this.head.xRot += 0.3F;
            if (this.neck != null) {
               this.neck.xRot += 0.3F;
            }
         }
         default -> {
         }
      }

      if (this.tailTip != null) {
         this.tailTip.xRot += Mth.sin(age * 0.07F) * 0.15F;
      }
      com.formaworks.frontierhunts.ecology.client.HuntPose.classic(e, age, this.root, this.body, this.neck, this.head, this.tail, this.legFL, this.legFR,
         this.legBL, this.legBR, this.bird); // [ecology] hunting poses
      if (this.bird) {
         com.formaworks.frontierhunts.wingshot.client.BirdAnim.State ws = com.formaworks.frontierhunts.wingshot.client.BirdRender.current(e); // [wingshot]
         if (ws != null) {
            com.formaworks.frontierhunts.wingshot.client.BirdPose.classic(ws, age, this.body, this.head, this.wingL, this.wingR, this.tipL, this.tipR, this.legFL,
               this.legFR, this.tail);
         }
      }
   }
}
