package com.formaworks.frontierhunts.outfitter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * [outfitter] One worn outfit as a humanoid model built from {@link OutfitGeometry}. Every box hangs on a vanilla part
 * (head, body, arms, legs) at the vanilla pivots, so the outfit follows the wearer's pose exactly and nothing moves on
 * its own. Long coats carry their hem on the thighs: {@link #hemOnLegs} makes the legs render while the chest slot
 * renders (vanilla would hide them for a chest piece).
 *
 * <p>[clothing] Boxes and rigid cards can be tagged to give way to what is worn over them (table flags): 1 = under a
 * long coat's hem (leggings fringe, thigh tufts, a trousers pocket), 4 = inside a boot (shin tufts, the lower fringe, a
 * garter tie), 8 = under sewn-on mittens (forearm tufts, the end of a sleeve fringe, a coat's own fur cuff), 16 = under
 * the knee-length hide robe (the lower leggings fringe). Each tag group is its own child
 * part ({@code hide_<mask>}, posed cards nested in it) that the caller hides by setting {@link #hideUnderHem},
 * {@link #hideUnderBoot} and {@link #hideUnderMitten} right before drawing; so a garment never pokes through another
 * one worn over it, whatever the combination.
 */
public final class OutfitModel extends HumanoidModel<LivingEntity> {
   public static final int HEM = 1, MIRROR = 2, BOOT = 4, MITTEN = 8, LONG = 16, HIDE_FLAGS = HEM | BOOT | MITTEN | LONG;
   public final String outfit;
   public final boolean slim;
   final boolean hemOnLegs;
   private final ModelPart[] hideParts;
   private final int[] hideMasks;
   /** Set by the caller right before rendering (shared instance; rendering is single threaded). */
   public boolean hideUnderHem;
   /** [clothing] Set by the caller right before rendering: a boot is worn over these legs. */
   public boolean hideUnderBoot;
   /** [clothing] Set by the caller right before rendering: mittens are sewn onto the worn chest piece. */
   public boolean hideUnderMitten;
   /** [clothing] Set by the caller right before rendering: the knee-length hide robe is worn over these legs. */
   public boolean hideUnderLong;
   /**
    * [clothing] Set by the caller right before rendering: leave the body part (a trousers' waistband) out because an outer
    * chest garment covers the torso - it would only sit inside the jacket, or poke through a thin one.
    */
   public boolean hideBody;

   private OutfitModel(String outfit, boolean slim, boolean hemOnLegs, ModelPart root) {
      super(root);
      this.outfit = outfit;
      this.slim = slim;
      this.hemOnLegs = hemOnLegs;
      List<ModelPart> parts = new ArrayList<>();
      List<Integer> masks = new ArrayList<>();
      for (ModelPart p : new ModelPart[]{this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
         for (int m = 1; m <= HIDE_FLAGS; m++) {
            if (p.hasChild("hide_" + m)) {
               parts.add(p.getChild("hide_" + m));
               masks.add(m);
            }
         }
      }
      this.hideParts = parts.toArray(new ModelPart[0]);
      this.hideMasks = masks.stream().mapToInt(Integer::intValue).toArray();
   }

   static OutfitModel build(String outfit, boolean slim, boolean hemOnLegs) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeListBuilder[] main = new CubeListBuilder[6];
      List<Map<Integer, CubeListBuilder>> hidden = new ArrayList<>();
      List<List<float[]>> posed = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
         main[i] = CubeListBuilder.create();
         hidden.add(new TreeMap<>());
         posed.add(new ArrayList<>());
      }
      for (float[] r : OutfitGeometry.boxes(outfit)) {
         int part = target((int) r[0], slim);
         if (part < 0) {
            continue;
         }
         int mask = flags(r) & HIDE_FLAGS;
         boolean hasPose = r[10] != 0F || r[11] != 0F || r[12] != 0F || r[13] != 0F || r[14] != 0F || r[15] != 0F;
         if (hasPose) {
            posed.get(part).add(r);
            if (mask != 0) {
               hidden.get(part).computeIfAbsent(mask, k -> CubeListBuilder.create());
            }
         } else if (mask != 0) {
            add(hidden.get(part).computeIfAbsent(mask, k -> CubeListBuilder.create()), r);
         } else {
            add(main[part], r);
         }
      }
      String[] names = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
      PartPose[] pivots = {
         PartPose.ZERO, PartPose.ZERO, PartPose.offset(-5.0F, 2.0F, 0.0F), PartPose.offset(5.0F, 2.0F, 0.0F),
         PartPose.offset(-1.9F, 12.0F, 0.0F), PartPose.offset(1.9F, 12.0F, 0.0F)
      };
      for (int i = 0; i < 6; i++) {
         PartDefinition pd = root.addOrReplaceChild(names[i], main[i], pivots[i]);
         Map<Integer, PartDefinition> groups = new TreeMap<>();
         for (Map.Entry<Integer, CubeListBuilder> h : hidden.get(i).entrySet()) {
            groups.put(h.getKey(), pd.addOrReplaceChild("hide_" + h.getKey(), h.getValue(), PartPose.ZERO));
         }
         int n = 0;
         for (float[] r : posed.get(i)) {
            CubeListBuilder c = CubeListBuilder.create();
            add(c, r);
            int mask = flags(r) & HIDE_FLAGS;
            PartDefinition parent = mask == 0 ? pd : groups.get(mask);
            parent.addOrReplaceChild("card_" + n++, c, PartPose.offsetAndRotation(r[10], r[11], r[12], r[13], r[14], r[15]));
         }
         if (i == 0) {
            root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
         }
      }
      int[] uv = OutfitGeometry.uv(outfit);
      return new OutfitModel(outfit, slim, hemOnLegs, LayerDefinition.create(mesh, uv[0], uv[1]).bakeRoot());
   }

   private static int flags(float[] r) {
      return r.length > 16 ? (int) r[16] : 0;
   }

   /** Table part code -> model part index (head, body, right arm, left arm, right leg, left leg), -1 = other skin type. */
   private static int target(int code, boolean slim) {
      return switch (code) {
         case 0 -> 0;
         case 1 -> 1;
         case 2 -> slim ? -1 : 2;
         case 4 -> slim ? 2 : -1;
         case 5 -> slim ? -1 : 3;
         case 7 -> slim ? 3 : -1;
         case 3 -> 4;
         case 6 -> 5;
         default -> -1;
      };
   }

   private static void add(CubeListBuilder c, float[] r) {
      int flags = flags(r);
      c.texOffs((int) r[1], (int) r[2]).mirror((flags & MIRROR) != 0).addBox(r[3], r[4], r[5], r[6], r[7], r[8], new CubeDeformation(r[9]));
      c.mirror(false);
   }

   /** Copy the wearer's part poses (what HumanoidModel.copyPropertiesTo does for the parts) from any humanoid model. */
   public void follow(HumanoidModel<?> src) {
      this.head.copyFrom(src.head);
      this.hat.copyFrom(src.hat);
      this.body.copyFrom(src.body);
      this.rightArm.copyFrom(src.rightArm);
      this.leftArm.copyFrom(src.leftArm);
      this.rightLeg.copyFrom(src.rightLeg);
      this.leftLeg.copyFrom(src.leftLeg);
      this.crouching = src.crouching;
      this.young = src.young;
      this.riding = src.riding;
   }

   /** [clothing] Show or hide the tagged groups for the flags set right now (also for a single part drawn on its own). */
   public void applyHide() {
      int hide = (this.hideUnderHem ? HEM : 0) | (this.hideUnderBoot ? BOOT : 0) | (this.hideUnderMitten ? MITTEN : 0) | (this.hideUnderLong ? LONG : 0);
      for (int k = 0; k < this.hideParts.length; k++) {
         this.hideParts[k].visible = (this.hideMasks[k] & hide) == 0;
      }
   }

   /** [clothing] Clear every caller flag (a shared model drawn by a layer that sets none of them). */
   public void showAll() {
      this.hideUnderHem = false;
      this.hideUnderBoot = false;
      this.hideUnderMitten = false;
      this.hideUnderLong = false;
      this.hideBody = false;
   }

   @Override
   public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
      if (this.hemOnLegs && this.body.visible) {
         this.rightLeg.visible = true;
         this.leftLeg.visible = true;
      }
      this.applyHide();
      if (this.hideBody) {
         this.body.visible = false; // [clothing]
      }
      super.renderToBuffer(pose, buffer, light, overlay, color);
   }
}
