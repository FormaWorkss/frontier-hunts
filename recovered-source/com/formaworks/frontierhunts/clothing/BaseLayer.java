package com.formaworks.frontierhunts.clothing;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.ScentControl;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [clothing] The carbon base layer a player wears UNDER the vanilla armour slots: hood, top (jacket or the one-piece
 * Carbon Scent Suit) and trousers. Stored per player as a NeoForge data attachment (saved with the player, copied on
 * death only when keepInventory left it full - {@link BaseLayerService} drops it like armour otherwise, kept on a
 * dimension change or an End return), mirrored to the client by {@link BaseLayerService}'s own payloads.
 *
 * <p>A one-piece suit lives in {@link #TOP} and covers the head and legs too; hood and trousers slots are empty then.
 */
public final class BaseLayer {
   public static final int HOOD = 0, TOP = 1, TROUSERS = 2, SLOTS = 3;
   private static final String[] KEYS = {"hood", "top", "trousers"};

   final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
   /** 0 dry .. 1 soaked with sweat (saved; the scent function reads it) */
   public float sweat;

   // live, server only (not saved)
   int sentHash = Integer.MIN_VALUE;
   int sentState = Integer.MIN_VALUE;
   int sentOthers = Integer.MIN_VALUE;
   long lastClick = Long.MIN_VALUE;
   long factorTick = Long.MIN_VALUE;
   double factor = 1.0;
   float wet;
   int wash;

   public static final AttachmentType<BaseLayer> TYPE = AttachmentType.builder(BaseLayer::new)
      .serialize(new IAttachmentSerializer<CompoundTag, BaseLayer>() {
         @Override
         public BaseLayer read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            BaseLayer b = new BaseLayer();
            b.load(tag, provider);
            return b;
         }

         @Override
         public CompoundTag write(BaseLayer b, HolderLookup.Provider provider) {
            return b.isEmpty() && b.sweat <= 0.001F ? null : b.save(provider); // nothing worn: nothing in the player file
         }
      })
      .copyOnDeath()
      .build();

   public BaseLayer() {
   }

   /** The worn base layer of a player on the server (the client reads {@link #worn}). */
   public static BaseLayer of(Player p) {
      return p.getData(TYPE);
   }

   /**
    * The three base-layer stacks of any player on either side (server: the attachment; client: the last sync for that
    * player, all empty when nothing was received). Never null, never copied: do not modify.
    */
   public static ItemStack[] worn(Player p) {
      if (p == null) {
         return BaseLayerView.NONE;
      }
      if (p.level().isClientSide) {
         return BaseLayerView.stacks(p.getId());
      }
      return of(p).slots;
   }

   public ItemStack get(int slot) {
      return this.slots[slot];
   }

   void set(int slot, ItemStack stack) {
      this.slots[slot] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
      this.factorTick = Long.MIN_VALUE;
   }

   public boolean isEmpty() {
      return this.slots[HOOD].isEmpty() && this.slots[TOP].isEmpty() && this.slots[TROUSERS].isEmpty();
   }

   /** True when the stack in TOP is the one-piece Carbon Scent Suit. */
   public static boolean suit(ItemStack[] s) {
      return s[TOP].getItem() instanceof ScentControl c && c.piece == ScentControl.Piece.SUIT;
   }

   /** Base-layer slot a carbon piece goes in. */
   public static int slotFor(ScentControl.Piece piece) {
      return switch (piece) {
         case HOOD -> HOOD;
         case TROUSERS -> TROUSERS;
         default -> TOP;
      };
   }

   int hash() {
      int h = 1;
      for (ItemStack s : this.slots) {
         h = 31 * h + (s.isEmpty() ? 0 : ItemStack.hashItemAndComponents(s));
      }
      return h;
   }

   CompoundTag save(HolderLookup.Provider provider) {
      CompoundTag t = new CompoundTag();
      for (int i = 0; i < SLOTS; i++) {
         if (!this.slots[i].isEmpty()) {
            t.put(KEYS[i], this.slots[i].save(provider));
         }
      }
      if (this.sweat > 0.001F) {
         t.putFloat("sweat", this.sweat);
      }
      return t;
   }

   void load(CompoundTag t, HolderLookup.Provider provider) {
      for (int i = 0; i < SLOTS; i++) {
         this.slots[i] = t.contains(KEYS[i], 10) ? ItemStack.parseOptional(provider, t.getCompound(KEYS[i])) : ItemStack.EMPTY;
      }
      float s = t.getFloat("sweat");
      this.sweat = Float.isFinite(s) ? Math.max(0F, Math.min(1F, s)) : 0F;
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent
      public static void register(RegisterEvent e) {
         e.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, h -> h.register(FrontierHunts.id("base_layer"), TYPE));
      }
   }
}
