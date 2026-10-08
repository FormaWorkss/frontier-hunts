package com.formaworks.frontierhunts.ecology.bones;

/** [ecology] Animals whose skulls lie in bone sites. */
public enum BoneSpecies {
   WHITETAIL("whitetail"), ELK("elk"), MOOSE("moose"), BISON("bison");

   public final String id;

   BoneSpecies(String id) {
      this.id = id;
   }
}
