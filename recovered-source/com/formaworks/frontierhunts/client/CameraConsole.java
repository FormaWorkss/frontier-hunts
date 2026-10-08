package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

final class CameraConsole {
   static BlockPos console(ScoutingNetwork.Roll var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.screen instanceof CameraHubScreen var4) {
         return var4.console();
      } else {
         return var1.screen instanceof TrailCameraScreen var2 ? var2.console() : var0.pos();
      }
   }

   private CameraConsole() {
   }
}
