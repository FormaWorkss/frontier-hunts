package com.formaworks.frontierhunts.hunting;

public enum DeerOrgan {
   HEART("Heart", "Vital. A heart hit drops the deer where it stands.", "Low in the chest, right behind the elbow.", 10691372, DeerAnatomy.Region.HEART, false),
   LUNGS(
      "Lungs",
      "Vital. A double-lung hit drops the animal on the spot; a single lung runs before it goes down.",
      "Fill the chest behind the shoulder, above and around the heart.",
      14981781,
      DeerAnatomy.Region.LUNG,
      false
   ),
   LIVER(
      "Liver",
      "Fatal, but not instant. Give the deer time before tracking.",
      "Just behind the diaphragm, mostly on the right side.",
      6956576,
      DeerAnatomy.Region.LIVER,
      false
   ),
   RUMEN(
      "Stomach (rumen)",
      "Gut shot. The deer runs; dark, sparse blood and a long track.",
      "Fills most of the left side of the belly.",
      10327663,
      DeerAnatomy.Region.GUT,
      false
   ),
   INTESTINES("Intestines", "Gut shot. Slow and poor blood trail.", "Coiled in the right and rear belly.", 14397080, DeerAnatomy.Region.GUT, false),
   KIDNEYS("Kidneys", "Fatal within a short run.", "High in the loin, under the spine.", 8007462, DeerAnatomy.Region.LIVER, false),
   DIAPHRAGM(
      "Diaphragm",
      "Muscle sheet dividing the chest from the belly.",
      "Slopes from the last ribs down to the brisket.",
      10769994,
      DeerAnatomy.Region.BODY,
      false
   ),
   SPINE(
      "Spine",
      "Spinal hit. The animal bolts but goes down within a short run.",
      "Runs along the back, low in the neck.",
      15261642,
      DeerAnatomy.Region.SPINE,
      true
   ),
   RIBS(
      "Ribs & sternum",
      "Thirteen pairs of ribs cage the heart and lungs.",
      "Aim between them, just behind the shoulder.",
      14932676,
      DeerAnatomy.Region.BODY,
      true
   ),
   SHOULDER(
      "Shoulder", "Heavy bone. Breaks the leg but may not reach the vitals.", "Shoulder blade and upper foreleg.", 15261642, DeerAnatomy.Region.SHOULDER, true
   ),
   LEG_BONES("Leg bones", "Crippling but rarely fatal.", "Foreleg and hind leg bones.", 15261642, DeerAnatomy.Region.LEG, true),
   PELVIS("Pelvis", "Hip hit. The deer goes down behind but may crawl.", "Hip bones joining the hind legs.", 15261642, DeerAnatomy.Region.LEG, true),
   SKULL("Skull", "Small target; misses the brain easily.", "Head bones and jaw.", 15261642, DeerAnatomy.Region.HEAD, true),
   BRAIN(
      "Brain",
      "A very small target. The animal staggers off and drops within seconds.",
      "Behind and between the eyes.",
      14462630,
      DeerAnatomy.Region.BRAIN,
      false
   ),
   AIRWAY(
      "Windpipe",
      "Neck hit. Heavy bleeding; the deer runs a short way.",
      "Along the underside of the neck into the chest.",
      14340801,
      DeerAnatomy.Region.NECK,
      false
   ),
   ESOPHAGUS("Esophagus", "Carries food from the mouth to the rumen.", "Beside the windpipe, through the chest.", 12154224, DeerAnatomy.Region.NECK, false),
   VESSELS(
      "Major blood vessels",
      "Arteries and veins. A hit bleeds out fast.",
      "Aorta under the spine; jugular and carotid in the neck.",
      12069425,
      DeerAnatomy.Region.NECK,
      false
   );

   public final String title;
   public final String description;
   public final String location;
   public final int color;
   public final DeerAnatomy.Region region;
   public final boolean bone;

   private DeerOrgan(String nullxx, String nullxxx, String nullxxxx, int nullxxxxx, DeerAnatomy.Region nullxxxxxx, boolean nullxxxxxxx) {
      this.title = nullxx;
      this.description = nullxxx;
      this.location = nullxxxx;
      this.color = nullxxxxx;
      this.region = nullxxxxxx;
      this.bone = nullxxxxxxx;
   }

   public DeerAnatomy.Region regionAt(float var1, float var2) {
      if (this == VESSELS && var2 > -0.2F && var2 < 0.08F && var1 < 0.86F) {
         return DeerAnatomy.Region.HEART;
      } else if (this == VESSELS && var2 >= 0.08F) {
         return DeerAnatomy.Region.LIVER;
      } else {
         return this == AIRWAY && var2 > -0.26F ? DeerAnatomy.Region.LUNG : this.region;
      }
   }
}
