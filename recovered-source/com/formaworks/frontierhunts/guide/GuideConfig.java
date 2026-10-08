package com.formaworks.frontierhunts.guide;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [guide] Field School settings. Defined inside HuntConfig's server and client specs (one hook line each, see HuntConfig
 * "[guide]") so they live in the normal frontierhunts config files. Getters are safe before the configs load.
 */
public final class GuideConfig {
   // server (world)
   public static ModConfigSpec.BooleanValue ENABLED;
   public static ModConfigSpec.BooleanValue TIPS;
   public static ModConfigSpec.BooleanValue STARTER_KIT;
   public static ModConfigSpec.ConfigValue<List<? extends String>> STARTER_ITEMS;
   public static ModConfigSpec.IntValue TIP_COOLDOWN;
   // client
   public static ModConfigSpec.BooleanValue HUD_CARD;
   public static ModConfigSpec.BooleanValue CLIENT_TIPS;

   /** [onboard] Handbook + the mod's simplest bow + three arrows. */
   static final List<String> DEFAULT_KIT = List.of("frontierhunts:frontier_handbook", "frontierhunts:field_bow", "frontierhunts:field_arrow*3");

   private GuideConfig() {
   }

   /** Called from HuntConfig's server builder inside push("fieldSchool"). */
   public static void server(ModConfigSpec.Builder b) {
      ENABLED = b.comment("Field School: first-join welcome card, the 8-lesson new-player objective chain, its HUD card and guide pages.")
         .define("fieldSchool", true);
      TIPS = b.comment("One-time field notes the first time a hunter meets something new (first deer, blood trail, being winded, blizzard, season change, predator sign).")
         .define("contextTips", true);
      TIP_COOLDOWN = b.comment("Minimum seconds between two field notes for the same hunter.")
         .defineInRange("tipCooldownSeconds", 90, 10, 3600);
      STARTER_KIT = b.comment("Give new hunters a starter kit on their first join (veterans with more than 30 minutes played are skipped).")
         .define("starterKit", true);
      // [onboard] new key (the old starterItems / issueExpeditionEquipment are retired, so existing configs pick up the new kit)
      STARTER_ITEMS = b.comment("The first-join kit, \"namespace:item\" or \"namespace:item*count\" (at most 16 entries). Unknown ids are ignored. Everything else is crafted or earned; the Frontier Handbook shows how.")
         .defineListAllowEmpty("firstJoinItems", DEFAULT_KIT, GuideConfig::validItem);
   }

   /** Called from HuntConfig's client builder inside push("fieldSchool"). */
   public static void client(ModConfigSpec.Builder b) {
      HUD_CARD = b.comment("Show the small Field School objective card in the top-left corner while the course is running.")
         .define("objectiveCard", true);
      CLIENT_TIPS = b.comment("Show one-time field notes (hint toasts) the first time you meet something new.")
         .define("fieldNotes", true);
   }

   private static boolean validItem(Object o) {
      if (!(o instanceof String s) || s.isBlank() || s.length() > 128) {
         return false;
      }
      int star = s.indexOf('*');
      return ResourceLocation.tryParse(star < 0 ? s : s.substring(0, star)) != null;
   }

   public static boolean enabled() {
      try {
         return ENABLED == null || ENABLED.get();
      } catch (Throwable t) {
         return true;
      }
   }

   public static boolean tips() {
      try {
         return TIPS == null || TIPS.get();
      } catch (Throwable t) {
         return true;
      }
   }

   public static int tipCooldownTicks() {
      try {
         return (TIP_COOLDOWN == null ? 90 : TIP_COOLDOWN.get()) * 20;
      } catch (Throwable t) {
         return 1800;
      }
   }

   public static boolean starterKit() {
      try {
         return STARTER_KIT == null || STARTER_KIT.get();
      } catch (Throwable t) {
         return true;
      }
   }

   public static List<? extends String> starterItems() {
      try {
         return STARTER_ITEMS == null ? DEFAULT_KIT : STARTER_ITEMS.get();
      } catch (Throwable t) {
         return List.of();
      }
   }

   public static boolean hudCard() {
      try {
         return HUD_CARD == null || HUD_CARD.get();
      } catch (Throwable t) {
         return true;
      }
   }

   public static boolean clientTips() {
      try {
         return CLIENT_TIPS == null || CLIENT_TIPS.get();
      } catch (Throwable t) {
         return true;
      }
   }
}
