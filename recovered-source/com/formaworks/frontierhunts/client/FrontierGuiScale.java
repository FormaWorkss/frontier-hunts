package com.formaworks.frontierhunts.client;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * [1.2.8] Frontier Hunts windows (Handbook, journals, academy, settings, camp, workbench...) are drawn at their own GUI
 * scale, whatever the player's Minecraft GUI scale is. The layouts were made for a screen about 640 x 360 GUI pixels
 * (a 4K monitor at GUI scale 6, 1440p at 4, 1080p at 3); at a bigger GUI scale there is too little room and pages
 * overlap. While one of these windows is open the game's GUI scale is set to the largest whole scale that still leaves
 * that much room, and it goes back to the player's own setting when the window closes. The player can't change it, on
 * purpose: every screen size gets the same, tested layout.
 */
@EventBusSubscriber(modid = "frontierhunts", value = {Dist.CLIENT})
public final class FrontierGuiScale {
   /** the room every Frontier window is laid out for, in GUI pixels */
   static final int DESIGN_W = 640, DESIGN_H = 360;
   /** the Frontier windows (by class name, so windows that ship without source are included too) */
   private static final Set<String> SCREENS = Set.of(
      "com.formaworks.frontierhunts.guide.client.HandbookScreen",
      "com.formaworks.frontierhunts.guide.client.FirstHuntScreen",
      "com.formaworks.frontierhunts.guide.client.FieldSchoolScreen",
      "com.formaworks.frontierhunts.guide.client.WelcomeScreen",
      "com.formaworks.frontierhunts.client.JournalScreen",
      "com.formaworks.frontierhunts.client.ExpeditionScreen",
      "com.formaworks.frontierhunts.client.AssignmentScreen",
      "com.formaworks.frontierhunts.client.FrontierSettingsScreen",
      "com.formaworks.frontierhunts.camps.client.CampScreen",
      "com.formaworks.frontierhunts.client.CameraHubScreen",
      "com.formaworks.frontierhunts.client.TrailCameraScreen",
      "com.formaworks.frontierhunts.client.WorkbenchScreen",
      "com.formaworks.frontierhunts.benches.client.BenchScreen", // [benches] the three workbenches
      "com.formaworks.frontierhunts.client.ContractScreen",
      "com.formaworks.frontierhunts.campcook.client.DutchOvenScreen",
      "com.formaworks.frontierhunts.landscape.ride.rig.client.RigScreen",
      "com.formaworks.frontierhunts.client.AttachmentScreen",
      "com.formaworks.frontierhunts.client.StationScreen",
      "com.formaworks.frontierhunts.client.FieldGearScreen",
      "com.formaworks.frontierhunts.expedition.LensMonitorScreen",
      "com.formaworks.frontierhunts.clothing.client.LayersScreen", // [clothing]
      "com.formaworks.frontierhunts.phone.client.PhoneScreen"); // [phone] the Field Phone

   /** we changed the window's GUI scale and must put the player's back */
   private static boolean changed;

   private FrontierGuiScale() {
   }

   public static boolean ours(Screen s) {
      return s != null && SCREENS.contains(s.getClass().getName());
   }

   /** the Frontier scale for this window: the largest whole scale that leaves 640 x 360 GUI pixels (at least 1) */
   public static int scale(int fbWidth, int fbHeight) {
      return Math.max(1, Math.min(fbWidth / DESIGN_W, fbHeight / DESIGN_H));
   }

   private static int vanilla(Minecraft mc) {
      return mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.options.forceUnicodeFont().get());
   }

   /** sets the Frontier scale on the window; true when it changed */
   private static boolean apply(Minecraft mc) {
      var w = mc.getWindow();
      int s = scale(w.getWidth(), w.getHeight());
      if (w.getGuiScale() == s) {
         return false;
      }
      w.setGuiScale(s);
      changed = true;
      return true;
   }

   private static void restore(Minecraft mc) {
      if (!changed) {
         return;
      }
      changed = false;
      var w = mc.getWindow();
      int v = vanilla(mc);
      if (w.getGuiScale() != v) {
         w.setGuiScale(v);
      }
   }

   /** a Frontier window is being laid out (first opening): lay it out at the Frontier scale */
   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void init(ScreenEvent.Init.Pre e) {
      Minecraft mc = Minecraft.getInstance();
      Screen s = e.getScreen();
      if (!ours(s) || mc.screen != s) {
         return;
      }
      apply(mc);
      s.width = mc.getWindow().getGuiScaledWidth();
      s.height = mc.getWindow().getGuiScaledHeight();
   }

   /** a window opening: a Frontier one at the Frontier scale, anything else at the player's own (before it lays itself out) */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void opening(ScreenEvent.Opening e) {
      if (e.isCanceled()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (ours(e.getNewScreen())) {
         apply(mc);
      } else {
         restore(mc);
      }
   }

   /** window resized while a Frontier window is open (the game resets the scale): set ours again; and a safety net */
   @SubscribeEvent
   public static void tick(ClientTickEvent.Pre e) {
      Minecraft mc = Minecraft.getInstance();
      if (ours(mc.screen)) {
         if (apply(mc)) {
            mc.screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
         }
      } else {
         restore(mc);
      }
   }
}
