package com.formaworks.frontierhunts.hunting.herd;

/** [herds] An animal's place in its group. */
public enum HerdRole {
   /** leads: picks where the group goes (the routine's bedding / feeding / water legs) */
   LEADER,
   /** an adult member: follows the leader */
   ADULT,
   /** a yearling: follows its mother when she is in the group, else the leader */
   YOUNG,
   /** elk rut: the bull holding the cow herd; trails it and keeps it together */
   HERD_BULL;

   static HerdRole byOrdinal(int i) {
      HerdRole[] v = values();
      return i >= 0 && i < v.length ? v[i] : ADULT;
   }
}
