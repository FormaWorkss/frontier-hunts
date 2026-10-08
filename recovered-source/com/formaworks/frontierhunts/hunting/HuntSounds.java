package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HuntSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "frontierhunts");
   public static final DeferredHolder<SoundEvent, SoundEvent> BOW_RELEASE = register("bow_release");
   public static final DeferredHolder<SoundEvent, SoundEvent> ARROW_IMPACT = register("arrow_impact");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_CALL = register("deer_call");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_SNORT = register("deer_snort");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_BLOW = register("deer_blow");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_STOMP = register("deer_stomp");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_GRUNT = register("deer_grunt");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_BLEAT = register("deer_bleat");
   public static final DeferredHolder<SoundEvent, SoundEvent> DEER_WHEEZE = register("deer_wheeze");
   public static final DeferredHolder<SoundEvent, SoundEvent> ELK_BUGLE = register("elk_bugle");
   public static final DeferredHolder<SoundEvent, SoundEvent> ELK_MEW = register("elk_mew");
   public static final DeferredHolder<SoundEvent, SoundEvent> ELK_BARK = register("elk_bark");
   public static final DeferredHolder<SoundEvent, SoundEvent> MOOSE_GRUNT = register("moose_grunt");
   public static final DeferredHolder<SoundEvent, SoundEvent> MOOSE_CALL = register("moose_call");
   public static final DeferredHolder<SoundEvent, SoundEvent> MOOSE_THREAT = register("moose_threat");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRANCH_SNAP = register("branch_snap");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRANCH_CRACKLE = register("branch_crackle");
   public static final DeferredHolder<SoundEvent, SoundEvent> BRUSH_RUSTLE = register("brush_rustle");
   public static final DeferredHolder<SoundEvent, SoundEvent> CASCADE_FAR = register("cascade_far");
   public static final DeferredHolder<SoundEvent, SoundEvent> CASCADE = register("cascade");
   public static final DeferredHolder<SoundEvent, SoundEvent> CASCADE_FOOT = register("cascade_foot");

   private static DeferredHolder<SoundEvent, SoundEvent> register(String var0) {
      return SOUNDS.register(var0, () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(var0)));
   }

   private HuntSounds() {
   }
}
