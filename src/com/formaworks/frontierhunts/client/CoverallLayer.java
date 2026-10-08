package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.Coverall;
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
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;

/**
 * One-piece camo coveralls and the carbon scent suit (chest slot), drawn over the whole body.
 * [outfitter] Rebuilt on the shared worn-gear tables: open-face hood with a stiffened brim, zipped jacket with chest
 * pockets, elastic cuffs with tabs, cargo pockets, knee pads and ankle cuffs; the camo is printed continuously across
 * the panels at real-world scale (Vanilla preset: 1 texel per pixel, Ultra: 4). The hood is left off while a helmet is
 * worn. First-person sleeves: {@code OutfitClient.Arms}.
 */
public final class CoverallLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private final boolean slim;

   public CoverallLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
      super(parent);
      this.slim = slim;
   }

   @Override
   public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float amount, float partial,
      float age, float yaw, float pitch) {
      Coverall.Style style = Coverall.worn(p);
      if (style == null || p.isInvisible() || p.isSpectator()) {
         return;
      }
      String outfit = Outfits.armour(p.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST); // [clothing] cached id, no per-frame string
      OutfitModel m = Outfits.model(outfit, this.slim);
      m.follow(this.getParentModel());
      m.setAllVisible(true);
      m.hat.visible = false;
      m.head.visible = p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
      // [clothing] trousers / leggings in the legs slot are worn over the coverall's legs: draw theirs, not both
      boolean legsOver = p.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof net.minecraft.world.item.ArmorItem;
      m.rightLeg.visible = !legsOver;
      m.leftLeg.visible = !legsOver;
      m.showAll(); // [clothing]
      int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);
      m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Outfits.texture(outfit))), light, overlay, -1);
   }

   @EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD, value = {Dist.CLIENT})
   public static final class Setup {
      @SubscribeEvent
      public static void addLayers(AddLayers e) {
         for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer r) {
               r.addLayer(new CoverallLayer(r, skin == PlayerSkin.Model.SLIM));
            }
         }
      }
   }
}
