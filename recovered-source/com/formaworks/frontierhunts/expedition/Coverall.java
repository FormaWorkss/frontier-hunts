package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public final class Coverall extends Item implements Equipable {
   public final Coverall.Style style;

   public Coverall(Coverall.Style var1) {
      super(
         new Properties()
            .stacksTo(1)
            .durability(var1 == Coverall.Style.SCENT_SUIT ? 420 : 360)
            .attributes(
               ItemAttributeModifiers.builder()
                  .add(
                     Attributes.ARMOR,
                     new AttributeModifier(FrontierHunts.id("coverall_" + var1.id), var1 == Coverall.Style.SCENT_SUIT ? 3.0 : 2.0, Operation.ADD_VALUE),
                     EquipmentSlotGroup.CHEST
                  )
                  .build()
            )
      );
      this.style = var1;
   }

   public static Coverall.Style worn(LivingEntity var0) {
      if (var0 == null) {
         return null;
      } else {
         return var0.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof Coverall var1 ? var1.style : null;
      }
   }

   public static ItemStack wornStack(LivingEntity var0) {
      ItemStack var1 = var0.getItemBySlot(EquipmentSlot.CHEST);
      return var1.getItem() instanceof Coverall ? var1 : ItemStack.EMPTY;
   }

   public static double concealment(Player var0) {
      Coverall.Style var1 = worn(var0);
      if (var1 == null) {
         return 0.0;
      } else {
         GhillieSuit.Pattern var2 = GhillieSuit.environment(var0);
         double var3;
         if (var1 == Coverall.Style.BLAZE) {
            var3 = 0.7;
         } else if (var1 == Coverall.Style.DIGITAL) {
            var3 = 0.75;
         } else if (var1 == Coverall.Style.SCENT_SUIT) {
            var3 = var2 == GhillieSuit.Pattern.WOODLAND ? 0.5 : 0.3;
         } else {
            var3 = var1.ground == var2 ? 1.0 : 0.3;
         }

         return var1.strength * var3;
      }
   }

   public EquipmentSlot getEquipmentSlot() {
      return EquipmentSlot.CHEST;
   }

   public Holder<SoundEvent> getEquipSound() {
      return SoundEvents.ARMOR_EQUIP_LEATHER;
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      return this.swapWithEquipmentSlot(this, var1, var2, var3);
   }

   public boolean isEnchantable(ItemStack var1) {
      return true;
   }

   public int getEnchantmentValue() {
      return 10;
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.literal("One-piece · worn in the chest slot").withStyle(ChatFormatting.DARK_GRAY));
      switch (this.style) {
         case SCENT_SUIT:
            var3.add(Component.literal("Activated-carbon lining thins the scent you put downwind").withStyle(ChatFormatting.GRAY));
            var3.add(Component.literal("Sitting still: about two thirds less scent").withStyle(ChatFormatting.GRAY));
            var3.add(Component.literal("Sprinting, rain or water ruins it. Scent cover spray stacks.").withStyle(ChatFormatting.GRAY));
            var3.add(Component.literal("Open-face hood · you keep your eyes and your aim").withStyle(ChatFormatting.DARK_GREEN));
            break;
         case DIGITAL:
            var3.add(Component.literal("Blocky break-up pattern that works on most ground").withStyle(ChatFormatting.GRAY));
            break;
         case BLAZE:
            var3.add(Component.literal("Hunter orange broken up with camo").withStyle(ChatFormatting.GOLD));
            var3.add(Component.literal("Other hunters see you; deer see a dull olive shape").withStyle(ChatFormatting.GRAY));
            break;
         default:
            var3.add(Component.literal("Camouflage for " + switch (this.style.ground) {
               case WOODLAND -> this.style == Coverall.Style.AUTUMN ? "hardwoods in the fall" : "forests and wooded taiga";
               case WETLAND -> "swamps, rivers and reed beds";
               case GRASSLAND -> "plains, pasture and dry grass";
               case SNOW -> "snowy ground and cold tundra";
            }).withStyle(ChatFormatting.GRAY));
      }

      if (this.style != Coverall.Style.SCENT_SUIT) {
         var3.add(Component.literal("Lighter than a ghillie · best still and crouched").withStyle(ChatFormatting.DARK_GREEN));
      }
   }

   public static enum Style {
      SCENT_SUIT("scent_suit", "Carbon Scent Suit", null, 0.3),
      TIMBER("timber_camo_coveralls", "Timber Camo Coveralls", GhillieSuit.Pattern.WOODLAND, 0.55),
      AUTUMN("autumn_camo_coveralls", "Autumn Leaf Camo Coveralls", GhillieSuit.Pattern.WOODLAND, 0.55),
      MARSH("marsh_camo_coveralls", "Marsh Camo Coveralls", GhillieSuit.Pattern.WETLAND, 0.55),
      PRAIRIE("prairie_camo_coveralls", "Prairie Camo Coveralls", GhillieSuit.Pattern.GRASSLAND, 0.55),
      SNOW("snow_camo_coveralls", "Snow Camo Coveralls", GhillieSuit.Pattern.SNOW, 0.55),
      DIGITAL("digital_camo_coveralls", "Ridgeline Digital Camo", null, 0.45),
      BLAZE("blaze_camo_coveralls", "Blaze Orange Camo Coveralls", null, 0.4);

      public final String id;
      public final String title;
      public final GhillieSuit.Pattern ground;
      public final double strength;

      private Style(String nullxx, String nullxxx, GhillieSuit.Pattern nullxxxx, double nullxxxxx) {
         this.id = nullxx;
         this.title = nullxxx;
         this.ground = nullxxxx;
         this.strength = nullxxxxx;
      }

      public ResourceLocation texture() {
         return FrontierHunts.id("textures/entity/coverall/" + this.id + ".png");
      }
   }
}
