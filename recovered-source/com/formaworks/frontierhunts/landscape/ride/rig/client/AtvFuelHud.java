package com.formaworks.frontierhunts.landscape.ride.rig.client;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel;
import com.formaworks.frontierhunts.landscape.ride.rig.AtvRig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * [atvfuel] Fuel gauge on the ATV HUD, sitting just above the existing speed read-out (right of the hotbar centre):
 * pump glyph, E-F bar with a needle and quarter ticks, litres + reserve, amber below 15 %, flashing LOW FUEL below
 * 7 %, OUT OF GAS when dry. One soft chime when the level first drops into the low band.
 */
public final class AtvFuelHud {
   private static float shown = -1.0F;
   private static boolean warned;
   private static int atvId = -1;

   private AtvFuelHud() {
   }

   public static void render(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.options.hideGui || !(p.getVehicle() instanceof Atv atv) || atv.getControllingPassenger() != p) {
         shown = -1.0F;
         return;
      }
      if (atv.getId() != atvId) {
         atvId = atv.getId();
         shown = -1.0F;
         warned = false;
      }
      float frac = AtvFuel.fraction(atv);
      shown = shown < 0.0F ? frac : shown + (frac - shown) * 0.12F;
      boolean exempt = AtvFuel.exempt(atv);
      if (!exempt) {
         if (!warned && frac < AtvFuel.LOW && frac > 0.0F) {
            warned = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F, 0.35F));
         } else if (frac > AtvFuel.LOW + 0.03F) {
            warned = false;
         }
      }
      int w = g.guiWidth();
      int h = g.guiHeight();
      int right = w / 2 + 91;
      int y = h - 49 - (p.isCreative() ? -17 : 0) - 11; // one line above the speed text RideClient draws
      int barW = 50;
      int bx = right - barW;
      int by = y + 2;
      long t = p.tickCount;
      // pump glyph
      int ix = bx - 11;
      int ic = exempt ? 0xFFB8B8B8 : (frac <= 0.0F ? 0xFFE0503A : (frac < AtvFuel.LOW ? 0xFFF0A030 : 0xFFE0E0E0));
      g.fill(ix, by - 1, ix + 5, by + 7, 0xC0000000);
      g.fill(ix + 1, by, ix + 4, by + 6, ic);
      g.fill(ix + 2, by + 1, ix + 3, by + 2, 0xFF202020);
      g.fill(ix + 5, by + 1, ix + 7, by + 2, ic);
      g.fill(ix + 6, by + 2, ix + 7, by + 5, ic);
      // bar
      g.fill(bx - 1, by - 1, bx + barW + 1, by + 6, 0xA0000000);
      g.fill(bx, by, bx + Math.round(barW * AtvFuel.LOW), by + 5, 0x60A02818);
      int fw = Math.round(barW * Mth.clamp(shown, 0.0F, 1.0F));
      int col = shown < AtvFuel.LOW ? 0xFFD2462A : 0xFFE0A22C;
      if (fw > 0) {
         g.fill(bx, by, bx + fw, by + 5, col & 0xD0FFFFFF);
         g.fill(bx, by, bx + fw, by + 1, 0x60FFFFFF);
      }
      for (int i = 1; i < 4; i++) {
         int tx = bx + barW * i / 4;
         g.fill(tx, by + 3, tx + 1, by + 5, 0xA0FFFFFF);
      }
      int nx = bx + fw;
      g.fill(nx - 1, by - 2, nx + 1, by + 7, 0xFFFFFFFF);
      // text left of the glyph
      String txt;
      int tc;
      if (exempt) {
         txt = String.format("%.1f L", AtvFuel.fuel(atv));
         tc = 0xFFB8B8B8;
      } else if (frac <= 0.0F) {
         txt = Component.translatable("hud.frontierhunts.atv_out_of_gas").getString();
         tc = t / 6 % 2 == 0 ? 0xFFFF5A40 : 0xFFB03020;
      } else if (frac < AtvFuel.LOW * 0.5F) {
         txt = Component.translatable("hud.frontierhunts.atv_low_fuel").getString();
         tc = t / 8 % 2 == 0 ? 0xFFFFB040 : 0xFFB07020;
      } else {
         txt = String.format("%.1f L", AtvFuel.fuel(atv));
         tc = frac < AtvFuel.LOW ? 0xFFF0A030 : 0xFFE0E0E0;
      }
      float reserve = AtvRig.reserveShown(AtvRig.flags(atv));
      if (reserve > 0.0F && !exempt) {
         String r = String.format("+%.0f", reserve);
         g.drawString(mc.font, r, ix - 3 - mc.font.width(r), y + 1, 0xFFC09040, true);
         ix -= mc.font.width(r) + 4;
      }
      g.drawString(mc.font, txt, ix - 3 - mc.font.width(txt), y + 1, tc, true);
   }
}
