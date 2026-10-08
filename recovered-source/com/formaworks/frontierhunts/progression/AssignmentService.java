package com.formaworks.frontierhunts.progression;

import com.formaworks.frontierhunts.HuntRules;
import com.formaworks.frontierhunts.HuntService;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.tracking.TrailMark;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AssignmentService {
   private static final Map<UUID, Long> REQUESTS = new HashMap<>();
   private static final Map<UUID, AssignmentNetwork.Snapshot> SENT = new HashMap<>();

   /** [academy] Certification bits of the local player (client copy, for shared code such as the bow hold). */
   public static volatile int clientSkills;

   /**
    * [academy] Assignments count in any Overworld survival game (the reserve rule is no longer required: harvests,
    * blood-trail reading and deliveries all work in a normal world too). Never in the training grounds.
    */
   public static boolean eligible(Player var0) {
      return var0.isAlive() && !var0.isCreative() && !var0.isSpectator() && fieldLevel(var0.level());
   }

   /** [academy] Where field assignments, deliveries and their evidence count: the Overworld (incl. the reserve). */
   public static boolean fieldLevel(net.minecraft.world.level.Level var0) {
      return var0 instanceof net.minecraft.server.level.ServerLevel && (var0.dimension() == net.minecraft.world.level.Level.OVERWORLD || HuntRules.active(var0));
   }

   public static AssignmentProgress progress(ServerPlayer var0) {
      return HunterLedger.get(var0.serverLevel()).hunter(var0.getUUID()).assignments();
   }

   public static void request(ServerPlayer var0, AssignmentNetwork.Request var1) {
      if (var0.hasDisconnected()) {
         return; // [1.2.7] a claim queued behind the logout must not pay a player who's already been saved
      }
      if (var1.action() >= 0 && var1.action() <= 5 && var0.isAlive() && !var0.isSpectator()) {
         long var2 = var0.level().getGameTime();
         Long var4 = REQUESTS.put(var0.getUUID(), var2);
         if (var4 == null || var2 < var4 || var2 - var4 >= 5L) {
            if (var1.action() == 0) {
               send(var0, true);
               com.formaworks.frontierhunts.academy.TrainingService.sendState(var0); // [academy] courses for the dossier
            } else if (var1.action() == 5) {
               send(var0, false); // [academy] respec retired: certifications are earned at the academy
            } else if (!eligible(var0)) {
               send(var0, false);
            } else {
               HunterLedger var5 = HunterLedger.get(var0.serverLevel());
               AssignmentProgress var6 = progress(var0);
               switch (var1.action()) {
                  case 1:
                     ResourceLocation var7 = ResourceLocation.tryParse(var1.id());
                     if (var7 != null) {
                        Assignment var8 = (Assignment)var0.registryAccess().registryOrThrow(Assignment.REGISTRY).get(var7);
                        if (var8 != null) {
                           var6.accept(var7, var8, var2);
                        }
                     }
                     break;
                  case 2:
                     var5.claimAssignment(var0.getUUID(), var2);
                     com.formaworks.frontierhunts.progression.Durability.commit(var0.server, "assignment paid"); // [1.2.7]
                     break;
                  case 3:
                     var6.cancel(var2);
                     break;
                  case 4:
                     var6.train(var1.node());
                     break;
                  case 5:
                     var5.respec(var0.getUUID());
               }

               var5.setDirty();
               send(var0, false);
               HuntService.send(var0, false);
            }
         }
      }
   }

   public static void trail(ServerPlayer var0, TrailMark var1) {
      if (eligible(var0) && !var1.animal().equals(TrailMark.UNKNOWN)) {
         record(var0, Assignment.Objective.TRACK, var1.id(), var1.animal(), var1.created(), 0, false);
      }
   }

   public static void delivery(ServerPlayer var0) {
      if (eligible(var0)) {
         record(var0, Assignment.Objective.DELIVERY, UUID.randomUUID(), var0.getUUID(), var0.level().getGameTime(), 0, false);
      }
   }

   public static void harvest(ServerPlayer var0, UUID var1, long var2, int var4, boolean var5) {
      if (eligible(var0) && var2 >= 0L && !huntTerms(var0)) { // [hunts] species-hunt assignments count hunt events only
         record(var0, Assignment.Objective.HARVEST, var1, var1, var2, var4, var5);
      }
   }

   /** [hunts] the running assignment is a species-hunt one (has a "hunt" condition) */
   private static boolean huntTerms(ServerPlayer var0) {
      Assignment t = progress(var0).terms();
      return t != null && !t.hunt().isEmpty();
   }

   /** [hunts] a species-hunt event (hunts.HuntHooks) with its condition tags: counts for a matching "hunt" assignment. */
   public static void hunt(ServerPlayer var0, UUID animal, java.util.Set<String> tags) {
      Assignment t = progress(var0).terms();
      if (t != null && !t.hunt().isEmpty() && animal != null && eligible(var0)
         && com.formaworks.frontierhunts.hunts.HuntTracker.accepts(t.hunt(), tags)) {
         record(var0, Assignment.Objective.HARVEST, animal, animal, var0.level().getGameTime(), 0, true);
      }
   }

   private static void record(ServerPlayer var0, Assignment.Objective var1, UUID var2, UUID var3, long var4, int var6, boolean var7) {
      HunterLedger var8 = HunterLedger.get(var0.serverLevel());
      AssignmentProgress var9 = progress(var0);
      AssignmentProgress.State var10 = var9.state();
      boolean var11 = var9.record(var1, var2, var3, var4, var6, var7, var0.level().getGameTime());
      if (var11 || var9.state() != var10) {
         var8.setDirty();
         send(var0, false);
      }
   }

   /** Field dressing certification: 6.4 s instead of 8 (anywhere, survival or not). */
   public static int dressingTicks(ServerPlayer var0) {
      return var0 != null && progress(var0).trained(1) ? 64 : 80;
   }

   /**
    * [academy] Steady hold certification: the extra spread from a tiring full-draw hold x0.65. Called from the shared
    * BowHold (server: the hunter's record; client: the synced copy), so the bow's sway and its real spread agree.
    */
   public static float fatigueMultiplier(Player var0) {
      if (var0 instanceof ServerPlayer var1) {
         return progress(var1).trained(0) ? 0.65F : 1.0F;
      }
      return var0 != null && var0.level().isClientSide && (clientSkills & 1) != 0 ? 0.65F : 1.0F;
   }

   public static AssignmentNetwork.Snapshot snapshot(ServerPlayer var0, boolean var1) {
      AssignmentProgress var2 = progress(var0);
      long var3 = var0.level().getGameTime();
      Registry<Assignment> var5 = var0.registryAccess().registryOrThrow(Assignment.REGISTRY);
      List<AssignmentNetwork.Offer> var6 = var5.entrySet()
         .stream()
         .sorted(Comparator.comparing((java.util.Map.Entry<ResourceKey<Assignment>, Assignment> var0x) -> var0x.getKey().location()))
         .limit(16L)
         .map(
            var3x -> new AssignmentNetwork.Offer(
                  var3x.getKey().location().toString(),
                  var3x.getValue(),
                  (int)Math.min(24000L, var2.cooldown(var3x.getKey().location(), var3))
               )
         )
         .toList();
      return new AssignmentNetwork.Snapshot(
         var1,
         var0.level().dimension().location().toString(),
         eligible(var0),
         var2.experience(),
         var2.skills(),
         HunterLedger.get(var0.serverLevel()).hunter(var0.getUUID()).tokens(),
         var2.completed(),
         var2.state().ordinal(),
         var2.count(),
         var2.deadline() == 0L ? 0L : (long)Math.clamp(var2.deadline() - var3, 0, 72000),
         var2.id() == null ? "" : var2.id().toString(),
         var2.terms(),
         var6
      );
   }

   public static void send(ServerPlayer var0, boolean var1) {
      if (var0.connection != null && var0.connection.hasChannel(AssignmentNetwork.Snapshot.TYPE)) {
         AssignmentNetwork.Snapshot var2 = snapshot(var0, var1);
         if (var1 || !var2.equals(SENT.get(var0.getUUID()))) {
            PacketDistributor.sendToPlayer(var0, var2, new CustomPacketPayload[0]);
            SENT.put(var0.getUUID(), var2);
         }
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1 && var1.tickCount % 20 == 0) {
         if (progress(var1).expire(var1.level().getGameTime())) {
            HunterLedger.get(var1.serverLevel()).setDirty();
         }

         send(var1, false);
      }
   }

   private static void forget(UUID var0) {
      REQUESTS.remove(var0);
      SENT.remove(var0);
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent var0) {
      forget(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      forget(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void dimension(PlayerChangedDimensionEvent var0) {
      forget(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent var0) {
      REQUESTS.clear();
      SENT.clear();
   }

   private AssignmentService() {
   }
}
