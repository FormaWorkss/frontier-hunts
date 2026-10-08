package com.formaworks.frontierhunts.client.trailcam;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

/**
 * A photographed player (your buddy, or you). Uses the live tab-list skin when that player is online, otherwise the
 * skin recorded with the photo, so the picture looks right even after they logged off.
 */
final class TrailcamPlayer extends RemotePlayer {
   private final CompletableFuture<PlayerSkin> recorded;
   private final UUID person;

   TrailcamPlayer(ClientLevel level, GameProfile profile, CompletableFuture<PlayerSkin> recorded) {
      super(level, profile);
      this.recorded = recorded;
      this.person = profile.getId();
      // the stand-in must not share the real player's entity UUID (the local player or a buddy nearby is in the level)
      this.setUUID(UUID.randomUUID());
   }

   @Override
   protected PlayerInfo getPlayerInfo() {
      Minecraft mc = Minecraft.getInstance();
      return mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(this.person);
   }

   @Override
   public PlayerSkin getSkin() {
      PlayerInfo info = this.getPlayerInfo();
      if (info != null) {
         return info.getSkin();
      }
      if (this.recorded != null) {
         PlayerSkin s = this.recorded.getNow(null);
         if (s != null) {
            return s;
         }
      }
      return DefaultPlayerSkin.get(this.person);
   }

   /** How they were when photographed, not whatever game mode they are in now. */
   @Override
   public boolean isSpectator() {
      return false;
   }

   @Override
   public boolean isCreative() {
      return false;
   }
}
