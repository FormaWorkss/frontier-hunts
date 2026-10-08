package com.formaworks.frontierhunts.killcam;

/** How long the kill cam lingers. Only presentation timing changes; nothing touches the server tick. */
public enum KillCamLength {
   SHORT(0.68F),
   NORMAL(1.0F),
   LONG(1.38F);

   public final float scale;

   KillCamLength(float scale) {
      this.scale = scale;
   }
}
