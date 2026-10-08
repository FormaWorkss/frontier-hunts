package com.formaworks.frontierhunts.client.sound;

/**
 * [gui] Added to Minecraft's SoundManager and SoundEngine by FrontierSoundEngineMixin / FrontierSoundManagerMixin:
 * re-applies the Frontier group volumes to every Frontier sound that is playing right now (loops included), without
 * stopping any of them.
 */
public interface FrontierSoundVolumes {
   void frontierhunts$refreshVolumes();
}
