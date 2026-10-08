package com.formaworks.frontierhunts.progression;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;

public record Assignment(
   String title,
   String briefing,
   Assignment.Objective objective,
   int target,
   int minimumMass,
   boolean chestOnly,
   int duration,
   int tokens,
   int experience,
   int cooldown,
   String hunt
) {
   public static final ResourceKey<Registry<Assignment>> REGISTRY = ResourceKey.createRegistryKey(FrontierHunts.id("assignment"));
   public static final Codec<Assignment> CODEC = RecordCodecBuilder.create(
      var0 -> var0.group(
               Codec.string(1, 80).fieldOf("title").forGetter(Assignment::title),
               Codec.string(1, 500).fieldOf("briefing").forGetter(Assignment::briefing),
               StringRepresentable.fromEnum(Assignment.Objective::values).fieldOf("objective").forGetter(Assignment::objective),
               Codec.intRange(1, 10).fieldOf("target").forGetter(Assignment::target),
               Codec.intRange(0, 180).optionalFieldOf("minimum_mass", 0).forGetter(Assignment::minimumMass),
               Codec.BOOL.optionalFieldOf("chest_only", false).forGetter(Assignment::chestOnly),
               Codec.intRange(0, 72000).optionalFieldOf("duration", 0).forGetter(Assignment::duration),
               Codec.intRange(1, 1000).fieldOf("tokens").forGetter(Assignment::tokens),
               Codec.intRange(1, 1000).fieldOf("experience").forGetter(Assignment::experience),
               Codec.intRange(20, 24000).fieldOf("cooldown").forGetter(Assignment::cooldown),
               // [hunts] optional species-hunt condition: "<species|*>:<tag>[,...]" (hunts.HuntTracker.TAGS); harvests of deer then don't count
               Codec.string(0, 200).optionalFieldOf("hunt", "").forGetter(Assignment::hunt)
            )
            .apply(var0, Assignment::new)
   );

   public String instruction() {
      if (!this.hunt.isEmpty()) {
         return com.formaworks.frontierhunts.hunts.HuntAssignments.instruction(this.hunt, this.target); // [hunts]
      }
      return switch (this.objective) {
         case TRACK -> "Inspect " + this.target + " new marks from one whitetail.";
         case HARVEST -> "Recover "
         + this.target
         + " whitetail"
         + (this.minimumMass > 0 ? " weighing at least " + this.minimumMass + " kg" : "")
         + (this.chestOnly ? " after a chest hit" : "")
         + ".";
         case DELIVERY -> "Complete " + this.target + " provision deliveries at a ranger board.";
      };
   }

   public static enum Objective implements StringRepresentable {
      TRACK,
      HARVEST,
      DELIVERY;

      public String getSerializedName() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }
}
