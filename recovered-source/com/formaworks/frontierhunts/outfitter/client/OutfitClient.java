package com.formaworks.frontierhunts.outfitter.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.survival.Clothing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * [outfitter] Client wiring of the worn gear:
 * <ul>
 * <li>{@link Armour}: the armour-slot model of fur/hide garments, ghillie and carbon pieces (wide or slim arms, coat hems
 * on the thighs, leggings fringe hidden under a long coat);</li>
 * <li>carbon hood/jacket/trousers registration (their own item set, disjoint from every other client extension);</li>
 * <li>{@link Extras}: fur mittens and a fur-lined collar on any chest piece that has them sewn in;</li>
 * <li>first-person sleeves (and mittens) of the worn chest piece on the arm.</li>
 * </ul>
 */
public final class OutfitClient {
   private OutfitClient() {
   }

   /** Armour model for every outfit item (garments, ghillie, carbon). */
   public static class Armour implements IClientItemExtensions {
      @Override
      public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
         String o = Outfits.armour(stack, slot);
         if (o == null) {
            return original;
         }
         OutfitModel m = Outfits.model(o, Outfits.slim(entity));
         m.hideUnderHem = slot == EquipmentSlot.LEGS && Outfits.longCoat(entity);
         // [clothing] what is worn over this piece decides what of it shows: a trousers' waistband stays under any outer
         // chest garment, shin tufts and the lower fringe go into a boot, forearm tufts and a sleeve fringe's end under
         // sewn-on mittens (never drawn inside or through the outer layer)
         m.hideBody = slot == EquipmentSlot.LEGS && com.formaworks.frontierhunts.clothing.BaseLayerService.torsoCovered(entity);
         m.hideUnderBoot = slot == EquipmentSlot.LEGS && entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof net.minecraft.world.item.ArmorItem;
         m.hideUnderMitten = slot == EquipmentSlot.CHEST && (Clothing.lining(stack) & 2) != 0;
         m.hideUnderLong = slot == EquipmentSlot.LEGS && "garment/hide_robe".equals(Outfits.chestOutfit(entity));
         return m;
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void extensions(RegisterClientExtensionsEvent e) {
         Item[] carbon = ExpeditionContent.ITEMS.values().stream().map(h -> (Item) h.get()).filter(i -> i instanceof ScentControl).toArray(Item[]::new);
         if (carbon.length > 0) {
            e.registerItem(new Armour(), carbon);
         }
      }

      @SubscribeEvent
      public static void layers(EntityRenderersEvent.AddLayers e) {
         for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer r) {
               r.addLayer(new Extras(r, skin == PlayerSkin.Model.SLIM));
            }
         }
      }
   }

   /** Fur mittens and a fur-lined collar sewn into the worn chest piece (any chest item). */
   public static final class Extras extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
      private final boolean slim;

      public Extras(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
         super(parent);
         this.slim = slim;
      }

      @Override
      public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float amount, float partial,
         float age, float yaw, float pitch) {
         if (p.isInvisible() || p.isSpectator()) {
            return;
         }
         ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
         int lining = chest.isEmpty() ? 0 : Clothing.lining(chest);
         if (lining == 0) {
            return;
         }
         int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);
         // [clothing] the bear coat and the hide robe have their own fur ruff: a sewn-in collar would only poke through it
         String coat = Outfits.chestOutfit(p);
         if ((lining & 1) != 0 && !"garment/bear_fur_coat".equals(coat) && !"garment/hide_robe".equals(coat)) {
            draw(Outfits.model("extra/lining", this.slim), false, pose, buffers, light, overlay);
         }
         if ((lining & 2) != 0) {
            draw(Outfits.model("extra/mittens", this.slim), true, pose, buffers, light, overlay);
         }
      }

      private void draw(OutfitModel m, boolean arms, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
         m.follow(this.getParentModel());
         m.setAllVisible(false);
         if (arms) {
            m.rightArm.visible = m.leftArm.visible = true;
         } else {
            m.body.visible = true;
         }
         m.showAll(); // [clothing]
         m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Outfits.texture(m.outfit))), light, overlay, -1);
      }
   }

   /**
    * First person: the worn chest piece's sleeve (coveralls, hide coats, ghillie and carbon jackets) and sewn-on mittens
    * over the bare arm, posed exactly like vanilla poses the arm for {@code renderHand}.
    */
   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
   public static final class Arms {
      private Arms() {
      }

      @SubscribeEvent
      public static void arm(RenderArmEvent e) {
         AbstractClientPlayer p = e.getPlayer();
         if (p.isInvisible()) {
            return;
         }
         String outfit = Outfits.sleeveOutfit(p); // [clothing] or the carbon top / scent suit worn on its own
         ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
         boolean mittens = !chest.isEmpty() && (Clothing.lining(chest) & 2) != 0;
         if (outfit == null && !mittens) {
            return;
         }
         if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer r)) {
            return;
         }
         PlayerModel<AbstractClientPlayer> pm = r.getModel();
         // the same pose vanilla gives the arm in PlayerRenderer.renderHand (it runs right after this event)
         pm.attackTime = 0.0F;
         pm.crouching = false;
         pm.swimAmount = 0.0F;
         pm.setupAnim(p, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
         boolean right = e.getArm() == HumanoidArm.RIGHT;
         ModelPart from = right ? pm.rightArm : pm.leftArm;
         from.xRot = 0.0F;
         boolean slim = Outfits.slim(p);
         if (outfit != null) {
            sleeve(Outfits.model(outfit, slim), right, from, e, mittens);
         }
         if (mittens) {
            sleeve(Outfits.model("extra/mittens", slim), right, from, e, false);
         }
      }

      private static void sleeve(OutfitModel m, boolean right, ModelPart from, RenderArmEvent e, boolean mittens) {
         m.showAll(); // [clothing] forearm tufts / fringe end give way to mittens here too
         m.hideUnderMitten = mittens;
         m.applyHide();
         ModelPart to = right ? m.rightArm : m.leftArm;
         to.copyFrom(from);
         to.visible = true;
         to.render(e.getPoseStack(), e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(Outfits.texture(m.outfit))), e.getPackedLight(),
            OverlayTexture.NO_OVERLAY);
      }
   }
}
