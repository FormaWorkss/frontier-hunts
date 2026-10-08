package com.formaworks.frontierhunts.outfitter.client;

import com.formaworks.frontierhunts.clothing.BaseLayer;
import com.formaworks.frontierhunts.clothing.BaseLayerService;
import com.formaworks.frontierhunts.expedition.Coverall;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.outfitter.OutfitTextures;
import com.formaworks.frontierhunts.survival.item.GarmentItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * [outfitter] Which outfit model a worn stack uses, model cache (wide and slim skins), textures and how far a chest
 * layer stands off the back (packs, quivers and the pack harness sit on top of it, never inside it).
 */
public final class Outfits {
   private static final Map<String, OutfitModel> WIDE = new HashMap<>();
   private static final Map<String, OutfitModel> SLIM = new HashMap<>();
   /** Long coats: their hem is drawn on the thighs. */
   private static final java.util.Set<String> HEM = java.util.Set.of("garment/buckskin_coat", "garment/bear_fur_coat", "garment/hide_robe");
   /** Back depth (px past the body box) of the skin's own jacket overlay and of a vanilla chestplate. */
   public static final float BARE_DEPTH = 0.25F;
   public static final float ARMOUR_DEPTH = 1.0F;

   private Outfits() {
   }

   public static OutfitModel model(String outfit, boolean slim) {
      Map<String, OutfitModel> m = slim ? SLIM : WIDE;
      OutfitModel r = m.get(outfit);
      if (r == null) {
         r = OutfitModel.build(outfit, slim, HEM.contains(outfit));
         m.put(outfit, r);
      }
      return r;
   }

   public static boolean slim(LivingEntity e) {
      return e instanceof AbstractClientPlayer p && p.getSkin().model() == PlayerSkin.Model.SLIM;
   }

   public static ResourceLocation texture(String outfit) {
      String g = OutfitGeometry.texture(outfit);
      return OutfitTextures.of(g == null ? "extra/extras" : g);
   }

   /** Outfit id for an armour-slot stack drawn by the armour layer (garments, ghillie, carbon), or null. */
   public static String armour(ItemStack stack, EquipmentSlot slot) {
      // [clothing] cached per item: the armour layer asks every frame for every worn piece (no string building per frame)
      String id = IDS.get(stack.getItem());
      if (id == null && !IDS.containsKey(stack.getItem())) {
         id = armourId(stack.getItem());
         IDS.put(stack.getItem(), id);
      }
      return id;
   }

   private static final Map<net.minecraft.world.item.Item, String> IDS = new java.util.IdentityHashMap<>();

   private static String armourId(net.minecraft.world.item.Item item) {
      if (item instanceof GarmentItem g) {
         return "garment/" + g.kind.id();
      }
      if (item instanceof GhillieSuit g) {
         return "ghillie/" + g.pattern.id + "_" + piece(g.getType());
      }
      if (item instanceof Coverall c) {
         return "coverall/" + c.style.id;
      }
      return null;
   }

   /** [clothing] Outfit of a carbon base-layer piece (worn under clothing, drawn by clothing.client.BaseLayerRender). */
   public static String base(ScentControl s) {
      return switch (s.piece) {
         case HOOD -> "carbon/hood";
         case TOP -> "carbon/jacket";
         case TROUSERS -> "carbon/trousers";
         case SUIT -> "coverall/scent_suit";
      };
   }

   /** [clothing] The base-layer top showing on the torso (no outer chest garment over it), or null. */
   public static String baseTop(LivingEntity e) {
      if (!(e instanceof Player p) || BaseLayerService.torsoCovered(e)) {
         return null;
      }
      ItemStack top = BaseLayer.worn(p)[BaseLayer.TOP];
      return top.getItem() instanceof ScentControl s ? base(s) : null;
   }

   /** [clothing] Sleeve on the first-person arm: the outer chest garment, else a carbon top / suit worn on its own. */
   public static String sleeveOutfit(LivingEntity e) {
      String o = chestOutfit(e);
      return o != null ? o : baseTop(e);
   }

   private static String piece(ArmorItem.Type t) {
      return switch (t) {
         case HELMET -> "hood";
         case CHESTPLATE -> "jacket";
         default -> "trousers";
      };
   }

   /** Outfit drawn on the arms by the worn chest item (first-person sleeves), or null. */
   public static String chestOutfit(LivingEntity e) {
      ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
      if (chest.isEmpty()) {
         return null;
      }
      if (chest.getItem() instanceof Coverall) {
         return armour(chest, EquipmentSlot.CHEST);
      }
      if (chest.getItem() instanceof ArmorItem a && a.getType() != ArmorItem.Type.CHESTPLATE) {
         return null;
      }
      return armour(chest, EquipmentSlot.CHEST);
   }

   /** A chest piece whose hem covers the thighs (hide the leggings' upper fringe). */
   public static boolean longCoat(LivingEntity e) {
      String o = chestOutfit(e);
      return o != null && HEM.contains(o);
   }

   /** How far (px) the worn chest layer stands off the back of the body box. */
   public static float backDepth(LivingEntity e) {
      ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
      if (chest.isEmpty()) {
         String b = baseTop(e); // [clothing] a carbon top / scent suit worn without an outer garment
         return b == null ? BARE_DEPTH : Math.max(BARE_DEPTH, OutfitGeometry.depth(b));
      }
      String o = chestOutfit(e);
      if (o != null) {
         return Math.max(BARE_DEPTH, OutfitGeometry.depth(o));
      }
      return ARMOUR_DEPTH;
   }
}
