package com.formaworks.frontierhunts.freak;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.camps.Tokens;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunts.HuntEvent;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.Skill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [1.1.6] The freaks: one whitetail, one elk and one moose so far beyond the rest of the herd - body and antlers - that
 * nobody on the reserve has taken one. They are never a lucky spawn. Each is the end of a quest, and every step is
 * something a real hunter does to get on a once-in-a-lifetime animal:
 * <ol>
 * <li><b>Prove yourself</b> - finish that species' master hunt (Hunts page).</li>
 * <li><b>Hear the rumour</b> - buy the ranger's game report (Woodsman rank, licence counter). The ranger tells you about
 * the animal.</li>
 * <li><b>Find his sign</b> - whitetail: three trail-camera photos of bucks; elk: glass three bulls from 200 m or more;
 * moose: two trail-camera photos of bulls.</li>
 * <li><b>Hunt his country in the rut</b> - legally take a mature male of that species during its rut with a clean
 * heart or lung shot (whitetail 8+ points, elk 10+, moose 14+). That puts you where he lives, when he moves.</li>
 * <li><b>The old one</b> - from then on, on a dawn or dusk in the rut, with an unfilled tag for the species in your
 * pocket, the biggest male in the country around you (90-220 m off, never in sight) is him. He shows up at most once
 * a day. Take him legally (tagged, in season) to finish the quest. Poach him and the warden takes the antlers and the
 * quest drops back a step.</li>
 * </ol>
 * Progress lives in journal counters ({@code freak.<species>.stage}, {@code .sign}) so it is saved with the journal and
 * shows on the Hunter's Path and the checklist.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class FreakQuest {
   private FreakQuest() {
   }

   public static final String[] SPECIES = {"whitetail", "elk", "moose"};
   public static final int DONE = 5;
   static final String FLAG = "frontierhunts_freak";
   private static final Map<UUID, Long> LAST_APPEARANCE = new HashMap<>();

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void setup(FMLCommonSetupEvent e) {
         e.enqueueWork(() -> {
            Checklist.register("freak_whitetail", Checklist.Category.HUNTING, "freak.whitetail.stage", DONE, 600, "frontierhunts:whitetail_trophy");
            Checklist.register("freak_elk", Checklist.Category.HUNTING, "freak.elk.stage", DONE, 700, "frontierhunts:whitetail_trophy");
            Checklist.register("freak_moose", Checklist.Category.HUNTING, "freak.moose.stage", DONE, 700, "frontierhunts:whitetail_trophy");
         });
      }
   }

   // ============================================================================================ state

   public static int stage(ServerPlayer p, String sp) {
      return Math.clamp(JournalApi.counter(p, "freak." + sp + ".stage"), 0, DONE);
   }

   static void setStage(ServerPlayer p, String sp, int stage) {
      int cur = JournalApi.counter(p, "freak." + sp + ".stage");
      if (stage != cur) {
         JournalApi.count(p, "freak." + sp + ".stage", stage - cur);
      }
   }

   static int sign(ServerPlayer p, String sp) {
      return JournalApi.counter(p, "freak." + sp + ".sign");
   }

   static int signNeeded(String sp) {
      return sp.equals("moose") ? 2 : 3;
   }

   static int pointsNeeded(String sp) {
      return sp.equals("elk") ? 10 : sp.equals("moose") ? 14 : 8;
   }

   static GameSpecies species(String sp) {
      return sp.equals("elk") ? GameSpecies.ELK : sp.equals("moose") ? GameSpecies.MOOSE : GameSpecies.WHITETAIL;
   }

   static String male(String sp) {
      return sp.equals("whitetail") ? "buck" : "bull";
   }

   static String title(String sp) {
      return switch (sp) {
         case "elk" -> "the Ghost Bull";
         case "moose" -> "the Bog King";
         default -> "the Old Ridge Buck";
      };
   }

   /** what to do next for this species, in a line (Hunter's Path, journal notes) */
   public static String next(ServerPlayer p, String sp) {
      int st = stage(p, sp);
      String name = sp.equals("whitetail") ? "whitetail" : sp;
      return switch (st) {
         case 0 -> "Finish the " + name + " master hunt (Journal > Hunts) to hear about " + title(sp) + ".";
         case 1 -> "Buy the ranger's game report at a licence counter (Woodsman rank). Ask about " + title(sp) + ".";
         case 2 -> switch (sp) {
            case "elk" -> "Find his sign: glass bulls from 200 m or more (" + sign(p, sp) + "/" + signNeeded(sp) + ").";
            case "moose" -> "Find his sign: trail-camera photos of bulls (" + sign(p, sp) + "/" + signNeeded(sp) + ").";
            default -> "Find his sign: trail-camera photos of bucks (" + sign(p, sp) + "/" + signNeeded(sp) + ").";
         };
         case 3 -> "Hunt his country in the rut: take a " + pointsNeeded(sp) + "+ point " + male(sp) + " legally with a heart or lung shot during the rut.";
         case 4 -> "He is out there. Dawn or dusk in the rut, an unfilled " + name + " tag in your pocket - the biggest " + male(sp)
            + " around you is " + title(sp) + ".";
         default -> "You took " + title(sp) + ".";
      };
   }

   // ============================================================================================ progress

   /** HuntHooks.fire: every field event of an online hunter */
   public static void event(ServerPlayer p, HuntEvent e) {
      try {
         String sp = e.species;
         if (!sp.equals("whitetail") && !sp.equals("elk") && !sp.equals("moose")) {
            return;
         }
         int st = stage(p, sp);
         boolean male = (e.flags & HuntEvent.MALE) != 0;
         if (st == 2 && male) {
            boolean counts = switch (sp) {
               case "elk" -> e.kind == HuntEvent.Kind.GLASS && e.distance >= 200.0;
               default -> e.kind == HuntEvent.Kind.PHOTO;
            };
            if (counts) {
               JournalApi.count(p, "freak." + sp + ".sign", 1);
               if (sign(p, sp) >= signNeeded(sp)) {
                  advance(p, sp, 3, "You have his sign. Rub lines and beds this size don't come from an ordinary " + male(sp)
                     + ". Hunt this country in the rut.");
               } else {
                  p.displayClientMessage(Component.literal("Sign of " + title(sp) + ": " + sign(p, sp) + "/" + signNeeded(sp)).withStyle(ChatFormatting.GOLD), true);
               }
            }
         } else if (st == 3 && e.kind == HuntEvent.Kind.TAKE && male && (e.flags & (HuntEvent.RUT | HuntEvent.CLEAN)) == (HuntEvent.RUT | HuntEvent.CLEAN)
            && e.points >= pointsNeeded(sp)) {
            advance(p, sp, 4, "Dressing this " + male(sp) + " you find older, bigger tracks across his. " + title(sp)
               + " uses this country. Come back at dawn or dusk in the rut, with a tag.");
         }
      } catch (RuntimeException ignored) {
      }
   }

   /** GameReport.deliver: the ranger's rumour (stage 1 -> 2); returns lines to add to the report */
   public static List<String> rumours(ServerPlayer p) {
      List<String> out = new ArrayList<>();
      for (String sp : SPECIES) {
         int st = stage(p, sp);
         if (st == 1) {
            advance(p, sp, 2, rumour(sp));
            out.add(rumour(sp));
         } else if (st >= 2 && st < DONE) {
            out.add("Still no one has taken " + title(sp) + ".");
         }
      }
      return out;
   }

   private static String rumour(String sp) {
      return switch (sp) {
         case "elk" -> "Rumour: a bull the guides call the Ghost Bull - pale, huge, and gone before anyone gets a shot. Glass the high parks from far off.";
         case "moose" -> "Rumour: the Bog King, a bull moose bigger than a draft horse, wallows somewhere in the north. Put cameras on the willow bottoms.";
         default -> "Rumour: an old buck has been on the ridges for years - the biggest track anyone has seen. Put out trail cameras.";
      };
   }

   private static void advance(ServerPlayer p, String sp, int stage, String text) {
      setStage(p, sp, stage);
      p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
      JournalApi.note(p, text);
      p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.35F, 0.8F);
   }

   // ============================================================================================ the animal

   /** stage 0 -> 1 when the master hunt is done; stage 4: the old one shows up */
   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + (p.getId() & 127)) % 200 != 77 || !p.isAlive() || p.isSpectator()) {
         return;
      }
      try {
         for (String sp : SPECIES) {
            int st = stage(p, sp);
            if (st == 0 && JournalApi.counter(p, "hunt." + sp + ".master") >= 1) {
               advance(p, sp, 1, "Word at the lodge: there's a " + male(sp) + " nobody has taken - " + title(sp)
                  + ". The ranger knows more (game report, licence counter).");
            } else if (st == 4) {
               appear(p, sp);
            }
         }
      } catch (RuntimeException ignored) {
      }
   }

   static boolean dawnOrDusk(ServerLevel level) {
      long t = Math.floorMod(level.getDayTime(), 24000L);
      return t >= 22800L || t <= 1800L || t >= 11000L && t <= 13800L;
   }

   private static void appear(ServerPlayer p, String sp) {
      ServerLevel level = p.serverLevel();
      GameSpecies species = species(sp);
      Rut.Phase phase = Rut.phase(species, level);
      if (phase == Rut.Phase.NONE || phase == Rut.Phase.POST_RUT || !dawnOrDusk(level) || !hasTag(p, sp)) {
         return;
      }
      long day = level.getDayTime() / 24000L;
      Long last = LAST_APPEARANCE.get(p.getUUID());
      if (last != null && last == day * 4 + species.ordinal()) {
         return;
      }
      List<Whitetail> around = level.getEntitiesOfClass(Whitetail.class, p.getBoundingBox().inflate(220.0, 64.0, 220.0),
         d -> d.isAlive() && !d.downed() && d.species() == species);
      for (Whitetail d : around) {
         if (d.getPersistentData().getBoolean(FLAG)) {
            return; // he is already out there
         }
      }
      Whitetail pick = null;
      double best = -1;
      for (Whitetail d : around) {
         double dist = d.distanceTo(p);
         if (dist < 90.0 || dist > 220.0 || !d.traits().buck() || p.hasLineOfSight(d)) {
            continue;
         }
         double size = d.traits().frame() + d.traits().rackGenes();
         if (size > best) {
            best = size;
            pick = d;
         }
      }
      if (pick == null) {
         return;
      }
      LAST_APPEARANCE.put(p.getUUID(), day * 4 + species.ordinal());
      pick.setTraits(freakTraits(species, level.random.nextInt(), level.random.nextFloat()));
      pick.setPersistenceRequired();
      CompoundTag tag = pick.getPersistentData();
      tag.putBoolean(FLAG, true);
      tag.putUUID(FLAG + "_for", p.getUUID());
      p.sendSystemMessage(Component.literal("Fresh sign - tracks bigger than any you have seen, still crisp. " + capital(title(sp)) + " is close.")
         .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
   }

   private static String capital(String s) {
      return Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   /** a once-in-a-lifetime male: prime age, the biggest frame and antler genes the herd can carry */
   public static DeerTraits freakTraits(GameSpecies sp, int seed, float roll) {
      boolean nonTypical = sp == GameSpecies.WHITETAIL && roll < 0.5F;
      int age = 78 + Math.floorMod(seed, 18);
      int frame = DeerTraits.FREAK_FRAME - Math.floorMod(seed >>> 8, 10);
      int genes = DeerTraits.FREAK_GENES - Math.floorMod(seed >>> 16, 10);
      int abnormal = nonTypical ? 22 + Math.floorMod(seed >>> 4, 13) : 0;
      return new DeerTraits(sp, true, age, frame, 100, genes, seed == 0 ? 1 : seed, abnormal, Math.floorMod(seed >>> 20, 100));
   }

   private static boolean hasTag(ServerPlayer p, String sp) {
      try {
         com.formaworks.frontierhunts.licence.Regulations.TagKind k = sp.equals("elk") ? com.formaworks.frontierhunts.licence.Regulations.TagKind.ELK
            : sp.equals("moose") ? com.formaworks.frontierhunts.licence.Regulations.TagKind.MOOSE : com.formaworks.frontierhunts.licence.Regulations.TagKind.DEER;
         if (!com.formaworks.frontierhunts.licence.LicenceConfig.mode().on()) {
            return true; // no regulations on this server: the quest still needs dawn/dusk in the rut
         }
         return com.formaworks.frontierhunts.licence.Tagging.carries(p, k);
      } catch (RuntimeException e) {
         return true;
      }
   }

   /** [1.1.8] the legend's name for a species id (whitetail / elk / moose) */
   public static String legendName(String sp) {
      return capital(title(sp));
   }

   /**
    * [1.1.8] Spawns a Legend of the Reserve (op command, for testing and for server events): a prime male with the
    * biggest frame and rack the species carries, flagged as the legend. {@code nonTypical} null = the usual 50/50 for
    * whitetail. Harvesting it only advances the quest of the player it was spawned for (if any).
    */
   public static Whitetail spawnLegend(ServerLevel level, net.minecraft.world.phys.Vec3 at, String sp, Boolean nonTypical, ServerPlayer forPlayer) {
      GameSpecies species = species(sp);
      net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(FrontierHunts.id(species.id));
      if (!(type.create(level) instanceof Whitetail d)) {
         return null;
      }
      float roll = nonTypical == null ? level.random.nextFloat() : nonTypical ? 0.0F : 1.0F;
      int seed = level.random.nextInt();
      d.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
      d.setTraits(freakTraits(species, seed == 0 ? 7 : seed, roll));
      d.setPersistenceRequired();
      CompoundTag tag = d.getPersistentData();
      tag.putBoolean(FLAG, true);
      if (forPlayer != null) {
         tag.putUUID(FLAG + "_for", forPlayer.getUUID());
      }
      // [1.2.0] a legend is big (a bull moose stands well over two metres): find him room on the ground near the spot - on
      // the surface, clear of trees and banks - instead of giving up when the exact point is tight
      d.refreshDimensions();
      net.minecraft.core.BlockPos base = net.minecraft.core.BlockPos.containing(at);
      net.minecraft.world.phys.Vec3 spot = null;
      search:
      for (int r = 0; r <= 8; r++) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                  continue;
               }
               int x = base.getX() + dx, z = base.getZ() + dz;
               int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
               if (Math.abs(y - base.getY()) > 12) {
                  continue;
               }
               for (int up = 0; up <= 2; up++) {
                  d.moveTo(x + 0.5, y + up, z + 0.5, d.getYRot(), 0.0F);
                  if (level.noCollision(d) && !level.containsAnyLiquid(d.getBoundingBox())) {
                     spot = d.position();
                     break search;
                  }
               }
            }
         }
      }
      if (spot == null) {
         // nowhere clear: stand him on the ground where you looked anyway
         int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
         d.moveTo(base.getX() + 0.5, y, base.getZ() + 0.5, d.getYRot(), 0.0F);
      }
      if (!level.addFreshEntity(d)) {
         return null;
      }
      return d;
   }

   public static boolean isFreak(Whitetail d) {
      return d.getPersistentData().getBoolean(FLAG);
   }

   /** Whitetail.harvest: a freak was field-dressed */
   public static void harvested(ServerPlayer p, Whitetail d, boolean poached) {
      if (!isFreak(d)) {
         return;
      }
      String sp = d.species().id;
      int st = stage(p, sp);
      if (poached) {
         if (st == 4) {
            setStage(p, sp, 3);
         }
         p.sendSystemMessage(Component.literal("The warden takes the antlers of " + title(sp)
            + ". Taken without a tag, he counts for nothing - and the story ends here for this season.").withStyle(ChatFormatting.RED));
         return;
      }
      if (st >= DONE) {
         return;
      }
      setStage(p, sp, DONE);
      JournalApi.count(p, "freak.taken", 1);
      // [1.2.0] the reward of a lifetime (LegendRewards): tokens, a legendary trophy, a standing discount, the advancement,
      // and with the third, Master of the Reserve
      LegendRewards.taken(p, sp, title(sp), d.traits().massKg(), d.traits().buck() ? d.traits().totalPoints() : 0);
   }
}
