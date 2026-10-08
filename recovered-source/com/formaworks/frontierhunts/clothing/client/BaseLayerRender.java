package com.formaworks.frontierhunts.clothing.client;

import com.formaworks.frontierhunts.clothing.BaseLayer;
import com.formaworks.frontierhunts.clothing.BaseLayerService;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.outfitter.client.OutfitModel;
import com.formaworks.frontierhunts.outfitter.client.Outfits;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;

/**
 * [clothing] Draws the carbon base layer on a player, only where no outer garment covers it: the hood when nothing is on
 * the head (a coverall's own hood counts), the top / suit body and sleeves when no chest garment is worn, the trousers /
 * suit legs when no legs garment or coverall is. A covered piece is simply not drawn, so a base layer can never z-fight
 * or poke through what is worn over it, whatever the combination. Same model tables, textures, render type and pose
 * copying as the armour-slot outfits ({@link OutfitModel}): wide and slim arms, coat hems on the thighs (the trousers'
 * pocket hides under a long coat), every pose the vanilla model has, Iris treats it like the other worn layers.
 * No allocation per frame (models are cached, flags are set on the shared instance right before drawing).
 */
final class BaseLayerRender extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private final boolean slim;

   BaseLayerRender(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
      super(parent);
      this.slim = slim;
   }

   @Override
   public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float amount, float partial,
      float age, float yaw, float pitch) {
      if (p.isInvisible() || p.isSpectator()) {
         return;
      }
      ItemStack[] s = BaseLayer.worn(p);
      if (s[0].isEmpty() && s[1].isEmpty() && s[2].isEmpty()) {
         return;
      }
      boolean head = !BaseLayerService.headCovered(p);
      boolean torso = !BaseLayerService.torsoCovered(p);
      boolean legs = !BaseLayerService.legsCovered(p);
      boolean longCoat = Outfits.longCoat(p);
      int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);
      if (BaseLayer.suit(s)) {
         this.draw((ScentControl) s[BaseLayer.TOP].getItem(), head, torso, torso, legs, longCoat, pose, buffers, light, overlay);
         return;
      }
      if (head && s[BaseLayer.HOOD].getItem() instanceof ScentControl c) {
         this.draw(c, true, false, false, false, false, pose, buffers, light, overlay);
      }
      if (torso && s[BaseLayer.TOP].getItem() instanceof ScentControl c) {
         this.draw(c, false, true, true, false, false, pose, buffers, light, overlay);
      }
      if (legs && s[BaseLayer.TROUSERS].getItem() instanceof ScentControl c) {
         // the waistband only while nothing covers the torso (a carbon top over it is fine: it is the bigger shell)
         this.draw(c, false, torso, false, true, longCoat, pose, buffers, light, overlay);
      }
   }

   private void draw(ScentControl c, boolean head, boolean body, boolean arms, boolean legs, boolean underHem, PoseStack pose, MultiBufferSource buffers,
      int light, int overlay) {
      if (!head && !body && !arms && !legs) {
         return;
      }
      OutfitModel m = Outfits.model(Outfits.base(c), this.slim);
      m.follow(this.getParentModel());
      m.setAllVisible(false);
      m.head.visible = head;
      m.body.visible = body;
      m.rightArm.visible = m.leftArm.visible = arms;
      m.rightLeg.visible = m.leftLeg.visible = legs;
      m.showAll();
      m.hideUnderHem = underHem;
      m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Outfits.texture(m.outfit))), light, overlay, -1);
   }
}
