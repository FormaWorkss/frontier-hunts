package com.formaworks.frontierhunts.sled;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [1.1.8] The toboggan: its entity type and the item that places it. */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SledContent {
   public static final DeferredHolder<EntityType<?>, EntityType<SledEntity>> SLED = DeferredHolder.create(Registries.ENTITY_TYPE, FrontierHunts.id("sled"));
   public static final DeferredHolder<Item, Item> SLED_ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("sled"));
   /** [1.2.5] the snowmobile */
   public static final DeferredHolder<EntityType<?>, EntityType<SnowmobileEntity>> SNOWMOBILE = DeferredHolder.create(Registries.ENTITY_TYPE,
      FrontierHunts.id("snowmobile"));
   public static final DeferredHolder<Item, Item> SNOWMOBILE_ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("snowmobile"));

   private SledContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.ENTITY_TYPE, h -> h.register(FrontierHunts.id("sled"),
         EntityType.Builder.<SledEntity>of(SledEntity::new, MobCategory.MISC).sized(1.0F, 0.45F).clientTrackingRange(10).updateInterval(1).build("sled")));
      e.register(Registries.ITEM, h -> h.register(FrontierHunts.id("sled"), new SledItem(new Item.Properties().stacksTo(1))));
      e.register(Registries.ENTITY_TYPE, h -> h.register(FrontierHunts.id("snowmobile"),
         EntityType.Builder.<SnowmobileEntity>of(SnowmobileEntity::new, MobCategory.MISC).sized(1.3F, 1.0F).clientTrackingRange(10).updateInterval(1)
            .build("snowmobile")));
      e.register(Registries.ITEM, h -> h.register(FrontierHunts.id("snowmobile"), new SnowmobileItem(new Item.Properties().stacksTo(1))));
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() == HuntContent.GEAR_TAB && SLED_ITEM.isBound()) {
         TabPlacement.after(e, SLED_ITEM.get(), "hound_lead", "wind_checker");
      }
   }

   /** [1.2.8] the snowmobile sits with the other vehicles: after the boats and the ATV (added at normal priority) */
   @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
   public static void creativeVehicles(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() == HuntContent.GEAR_TAB && SNOWMOBILE_ITEM.isBound()) {
         TabPlacement.after(e, SNOWMOBILE_ITEM.get(), "jon_boat", "rowboat", "atv", "sled");
      }
   }

   static final class SledItem extends Item {
      SledItem(Properties p) {
         super(p);
      }

      @Override
      public InteractionResult useOn(UseOnContext ctx) {
         Level level = ctx.getLevel();
         Vec3 at = ctx.getClickLocation();
         if (!level.isClientSide) {
            SledEntity s = SLED.get().create(level);
            if (s == null) {
               return InteractionResult.FAIL;
            }
            float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0.0F;
            s.moveTo(at.x, at.y + 0.05, at.z, yaw, 0.0F);
            if (!level.noCollision(s, s.getBoundingBox())) {
               return InteractionResult.FAIL;
            }
            level.addFreshEntity(s);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
               ctx.getItemInHand().shrink(1);
            }
         }
         return InteractionResult.sidedSuccess(level.isClientSide);
      }

      @Override
      public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
         tip.add(Component.translatable("item.frontierhunts.sled.desc1").withStyle(ChatFormatting.GRAY));
         tip.add(Component.translatable("item.frontierhunts.sled.desc2").withStyle(ChatFormatting.GRAY));
      }
   }

   /** [1.2.5] places a snowmobile, carrying the fuel left in it */
   static final class SnowmobileItem extends Item {
      SnowmobileItem(Properties p) {
         super(p);
      }

      @Override
      public InteractionResult useOn(UseOnContext ctx) {
         Level level = ctx.getLevel();
         Vec3 at = ctx.getClickLocation();
         if (!level.isClientSide) {
            SnowmobileEntity s = SNOWMOBILE.get().create(level);
            if (s == null) {
               return InteractionResult.FAIL;
            }
            float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0.0F;
            s.moveTo(at.x, at.y + 0.05, at.z, yaw, 0.0F);
            if (!level.noCollision(s, s.getBoundingBox())) {
               return InteractionResult.FAIL;
            }
            s.fromItem(ctx.getItemInHand());
            level.addFreshEntity(s);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
               ctx.getItemInHand().shrink(1);
            }
         }
         return InteractionResult.sidedSuccess(level.isClientSide);
      }

      @Override
      public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
         Float f = st.get(com.formaworks.frontierhunts.landscape.ride.rig.RigContent.FUEL.get());
         if (f != null) {
            tip.add(Component.translatable("item.frontierhunts.snowmobile.fuel", String.format("%.1f", f), String.format("%.0f", SnowmobileEntity.TANK))
               .withStyle(ChatFormatting.GOLD));
         }
         tip.add(Component.translatable("item.frontierhunts.snowmobile.desc1").withStyle(ChatFormatting.GRAY));
         tip.add(Component.translatable("item.frontierhunts.snowmobile.desc2").withStyle(ChatFormatting.GRAY));
      }
   }
}
