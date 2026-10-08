package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.clothing.BaseLayer;
import com.formaworks.frontierhunts.clothing.BaseLayerService;
import com.formaworks.frontierhunts.clothing.Scent;
import com.formaworks.frontierhunts.survival.Clothing;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * [clothing] Carbon base layer: hood, jacket, trousers and the one-piece Carbon Scent Suit. Worn UNDER clothing in the
 * player's own base-layer slots ({@link BaseLayer}), never in the armour slots, so a ghillie, camo coveralls or furs go
 * on top. Right-click wears it (swapping what was there); the Field Gear panel next to the inventory has the slots.
 *
 * <p>Activated carbon adsorbs body odour: a full fresh set thins the scent you put downwind by about 88%. Each piece
 * carries a carbon charge (stored on the stack) that wears down while it is worn - faster when you sweat - and comes
 * back when the layer is washed in water or aired by a fire; sleeping at camp freshens it as well. One function reads
 * all of it for every animal: {@link Scent}.
 */
public final class ScentControl extends Item {
   /** Custom-data key of the carbon charge (0..1, absent = fresh). */
   public static final String CHARGE = "frontierhunts_carbon";

   public enum Piece {
      HOOD("hood", 0.20, EquipmentSlot.HEAD),
      TOP("jacket", 0.45, EquipmentSlot.CHEST),
      TROUSERS("trousers", 0.35, EquipmentSlot.LEGS),
      SUIT("suit", 1.0, EquipmentSlot.CHEST);

      public final String id;
      /** share of the body's scent this piece covers (hood + top + trousers = 1, the suit alone = 1) */
      public final double share;
      /** the body region it sits on (for warmth weighting) */
      public final EquipmentSlot region;

      Piece(String id, double share, EquipmentSlot region) {
         this.id = id;
         this.share = share;
         this.region = region;
      }

      public static Piece of(ArmorItem.Type t) {
         return switch (t) {
            case HELMET -> HOOD;
            case CHESTPLATE -> TOP;
            default -> TROUSERS;
         };
      }
   }

   public final Piece piece;

   /** Registration of the three separate pieces (unchanged call sites). */
   public ScentControl(ArmorItem.Type type) {
      this(Piece.of(type));
   }

   public ScentControl(Piece piece) {
      super(new Item.Properties().stacksTo(1));
      this.piece = piece;
   }

   // ------------------------------------------------------------------------------------------------ carbon charge

   public static float charge(ItemStack stack) {
      CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
      if (cd == null) {
         return 1F;
      }
      @SuppressWarnings("deprecation")
      CompoundTag t = cd.getUnsafe();
      if (!t.contains(CHARGE)) {
         return 1F;
      }
      float c = t.getFloat(CHARGE);
      return Float.isFinite(c) ? Math.max(0F, Math.min(1F, c)) : 1F;
   }

   public static void setCharge(ItemStack stack, float charge) {
      float c = Math.max(0F, Math.min(1F, charge));
      if (c >= 0.9995F) {
         CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(CHARGE));
      } else {
         CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putFloat(CHARGE, c));
      }
   }

   // ------------------------------------------------------------------------------------------------ the scent API

   /**
    * How much of a hunter's scent reaches the air (1 = nothing worn, about 0.12 with a full fresh carbon layer while
    * still; spray, sweat, wet clothes and the journal perks included). Every animal that smells the player reads this.
    */
   public static double scentMultiplier(Player p) {
      return Scent.factor(p);
   }

   /** Carbon pieces worn (the suit counts as all three). */
   public static int pieces(Player p) {
      ItemStack[] s = BaseLayer.worn(p);
      if (BaseLayer.suit(s)) {
         return 3;
      }
      int n = 0;
      for (ItemStack st : s) {
         if (st.getItem() instanceof ScentControl) {
            n++;
         }
      }
      return n;
   }

   // ------------------------------------------------------------------------------------------------ wearing

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         BaseLayerService.wearFromHand(sp, hand);
         return InteractionResultHolder.success(sp.getItemInHand(hand));
      }
      return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("clothing.frontierhunts.tip.base_layer").withStyle(ChatFormatting.DARK_GRAY));
      String cut = Integer.toString((int) Math.round(Scent.FULL_CUT * 100.0));
      if (this.piece == Piece.SUIT) {
         lines.add(Component.translatable("clothing.frontierhunts.tip.suit", cut).withStyle(ChatFormatting.GRAY));
      } else {
         lines.add(Component.translatable("clothing.frontierhunts.tip.piece", Math.round(this.piece.share * 100.0), cut).withStyle(ChatFormatting.GRAY));
      }
      float c = charge(stack);
      ChatFormatting col = c >= 0.7F ? ChatFormatting.GREEN : (c >= 0.35F ? ChatFormatting.YELLOW : ChatFormatting.RED);
      lines.add(Component.translatable("clothing.frontierhunts.tip.charge", Math.round(c * 100F)).withStyle(col));
      if (c < 0.7F) {
         lines.add(Component.translatable("clothing.frontierhunts.tip.restore").withStyle(ChatFormatting.DARK_AQUA));
      }
      lines.add(Component.translatable("clothing.frontierhunts.tip.wear").withStyle(ChatFormatting.DARK_GREEN));
      if (com.formaworks.frontierhunts.survival.SurvivalSync.mode(true).on()) {
         Clothing.describe(stack, this.piece.region, lines); // warmth, like every worn piece, when body temperature is on
      }
   }
}
