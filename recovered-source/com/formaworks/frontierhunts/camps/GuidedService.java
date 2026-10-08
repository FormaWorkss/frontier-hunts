package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.camp.CampContent;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Guided-hunt contracts between players (escrowed fee) and outfitter contracts posted on the Contract Board. */
public final class GuidedService {
   public static final int MAX_FEE = 500;
   public static final int MAX_DAYS = 10;

   private GuidedService() {
   }

   /** Offer lapse timer: plain game ticks (5 real minutes). */
   static long now(MinecraftServer server) {
      return server.overworld().getGameTime();
   }

   /** Contract deadlines run on the reserve calendar (game time + the /season offset), in reserve days of 24000 ticks. */
   static long cal(MinecraftServer server) {
      return SeasonClock.calendarTicks(server.overworld());
   }

   /** Reserve day index used for the outfitter board (changes at each new in-game day). */
   public static long reserveDay(MinecraftServer server) {
      return SeasonClock.calendarTicks(server.overworld()) / 24000L;
   }

   static String name(ServerPlayer p) {
      return p.getGameProfile().getName();
   }

   // ------------------------------------------------------------------ player-guided contracts
   public static String offer(ServerPlayer guide, ServerPlayer client, String speciesId, int minScore, int minWeight, int days, int fee) {
      MinecraftServer server = guide.server;
      if (client == null || client == guide) {
         return "Pick another hunter who is online to guide.";
      }
      Quarry q = Quarry.find(speciesId);
      if (q == null) {
         return "Unknown species.";
      }
      if (days < 1 || days > MAX_DAYS) {
         return "Time limit must be 1-" + MAX_DAYS + " reserve days.";
      }
      if (fee < 0 || fee > MAX_FEE) {
         return "The fee must be 0-" + MAX_FEE + " tokens.";
      }
      minScore = q.rack() ? Math.clamp(minScore, 0, 400) : 0;
      minWeight = Math.clamp(minWeight, 0, 1500);
      GuidedHunts g = GuidedHunts.get(server);
      int asGuide = 0;
      for (GuidedHunts.Hunt h : g.open) {
         if (guide.getUUID().equals(h.guide)) {
            asGuide++;
            if (client.getUUID().equals(h.client)) {
               return "You already have a contract open with " + name(client) + ".";
            }
         }
      }
      if (asGuide >= 3) {
         return "A guide can run three contracts at a time.";
      }
      if (g.activeFor(client.getUUID()) != null) {
         return name(client) + " is already on a guided hunt.";
      }
      GuidedHunts.Hunt h = new GuidedHunts.Hunt();
      h.id = g.nextId++;
      h.guide = guide.getUUID();
      h.guideName = name(guide);
      h.client = client.getUUID();
      h.clientName = name(client);
      h.species = q.id;
      h.minScore = minScore;
      h.minWeight = minWeight;
      h.days = days;
      h.fee = fee;
      h.created = now(server);
      h.offerUntil = h.created + GuidedHunts.OFFER_TICKS;
      g.open.add(h);
      g.setDirty();
      Msg.tell(
         client,
         Msg.tag("Guided hunt", Msg.MOSS)
            .append(Msg.text(h.guideName + " offers to guide you: " + h.terms() + " within " + days + " reserve day" + (days == 1 ? "" : "s") + " for " + fee + " tokens (held in escrow until the hunt ends). ", Msg.PAPER))
            .append(Msg.button("Accept", "/guide accept " + h.id, "Pay " + fee + " tokens into escrow and start the hunt", Msg.MOSS))
            .append(Component.literal(" "))
            .append(Msg.button("Decline", "/guide decline " + h.id, "Turn the offer down", Msg.RUST))
      );
      Msg.sound(client, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.0F);
      Msg.bar(guide, "Offer sent to " + h.clientName + " · it stands for 5 minutes");
      CampsViews.refreshIfOpen(client);
      return null;
   }

   public static String accept(ServerPlayer client, long id) {
      MinecraftServer server = client.server;
      GuidedHunts g = GuidedHunts.get(server);
      GuidedHunts.Hunt h = g.byId(id);
      if (h == null || h.npc || h.state != GuidedHunts.State.OFFERED || !client.getUUID().equals(h.client)) {
         return "That offer is no longer open.";
      }
      if (g.activeFor(client.getUUID()) != null) {
         return "Finish or call off your current guided hunt first.";
      }
      if (!Tokens.spend(client.serverLevel(), client.getUUID(), h.fee)) {
         return "You need " + h.fee + " tokens for the guide's fee.";
      }
      h.escrow = h.fee;
      h.state = GuidedHunts.State.ACTIVE;
      h.deadline = cal(server) + h.days * 24000L;
      g.setDirty();
      MutableComponent m = Msg.tag("Guided hunt", Msg.MOSS)
         .append(Msg.text("Hunt on: " + h.clientName + " with guide " + h.guideName + " · " + h.terms() + " · " + h.days + " reserve days. Stay within " + CampsConfig.guideRange + " m of each other for the shot.", Msg.PAPER));
      Msg.tell(client, m);
      ServerPlayer guide = server.getPlayerList().getPlayer(h.guide);
      if (guide != null) {
         Msg.tell(guide, m);
         Msg.sound(guide, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.1F);
         CampsViews.refreshIfOpen(guide);
      }
      return null;
   }

   public static String decline(ServerPlayer client, long id) {
      GuidedHunts g = GuidedHunts.get(client.server);
      GuidedHunts.Hunt h = g.byId(id);
      if (h == null || h.state != GuidedHunts.State.OFFERED || !client.getUUID().equals(h.client)) {
         return "That offer is no longer open.";
      }
      g.close(h, GuidedHunts.State.DECLINED, "Declined by " + h.clientName, now(client.server));
      ServerPlayer guide = client.server.getPlayerList().getPlayer(h.guide);
      if (guide != null) {
         Msg.bar(guide, h.clientName + " declined your guided hunt.");
         CampsViews.refreshIfOpen(guide);
      }
      return null;
   }

   public static String cancel(ServerPlayer p, long id) {
      MinecraftServer server = p.server;
      GuidedHunts g = GuidedHunts.get(server);
      GuidedHunts.Hunt h = g.byId(id);
      if (h == null) {
         return "No such contract.";
      }
      boolean isGuide = p.getUUID().equals(h.guide);
      boolean isClient = p.getUUID().equals(h.client);
      if (!isGuide && !isClient) {
         return "That contract is not yours.";
      }
      long t = now(server);
      if (h.state == GuidedHunts.State.OFFERED) {
         if (isClient) {
            return decline(p, id);
         }
         g.close(h, GuidedHunts.State.CANCELLED, "Withdrawn by the guide", t);
         notifyOther(server, h, p, h.guideName + " withdrew the guided-hunt offer.");
         return null;
      }
      if (h.state != GuidedHunts.State.ACTIVE) {
         return "That contract is already closed.";
      }
      if (h.npc) {
         g.close(h, GuidedHunts.State.CANCELLED, "Called off · deposit kept by " + h.outfitter, t);
         Msg.bar(p, "Contract called off. " + h.outfitter + " keeps the " + h.deposit + "-token deposit.");
         return null;
      }
      if (isGuide) {
         Tokens.credit(p.serverLevel(), h.client, h.escrow);
         g.close(h, GuidedHunts.State.CANCELLED, "Released by the guide · fee refunded", t);
         notifyOther(server, h, p, h.guideName + " released you from the guided hunt. Your " + h.escrow + " tokens were refunded.");
         Msg.bar(p, "Hunt released · " + h.clientName + " was refunded.");
      } else {
         int dayRate = h.escrow / 4;
         Tokens.credit(p.serverLevel(), h.client, h.escrow - dayRate);
         Tokens.credit(p.serverLevel(), h.guide, dayRate);
         g.close(h, GuidedHunts.State.CANCELLED, "Called off by the client · guide kept " + dayRate + " tokens", t);
         notifyOther(server, h, p, h.clientName + " called off the hunt. You keep a " + dayRate + "-token day rate.");
         Msg.bar(p, "Hunt called off · " + (h.escrow - dayRate) + " tokens refunded, " + dayRate + " paid to your guide.");
      }
      return null;
   }

   private static void notifyOther(MinecraftServer server, GuidedHunts.Hunt h, ServerPlayer actor, String text) {
      UUID other = actor.getUUID().equals(h.guide) ? h.client : h.guide;
      ServerPlayer op = other == null ? null : server.getPlayerList().getPlayer(other);
      if (op != null) {
         Msg.tell(op, Msg.tag("Guided hunt", Msg.MOSS).append(Msg.text(text, Msg.PAPER)));
         CampsViews.refreshIfOpen(op);
      }
   }

   // ------------------------------------------------------------------ outfitter contracts (NPC, posted on the Contract Board)
   public static boolean nearContractBoard(ServerPlayer p) {
      BlockPos c = p.blockPosition();
      for (BlockPos at : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6))) {
         if (p.level().getBlockState(at).is(CampContent.CONTRACT_BOARD.get())) {
            return true;
         }
      }
      return false;
   }

   public static List<GuidedHunts.Hunt> todaysOutfitters(MinecraftServer server) {
      return GuidedHunts.outfitterOffers(server.overworld().getSeed(), reserveDay(server));
   }

   public static String book(ServerPlayer p, long key) {
      MinecraftServer server = p.server;
      if (!nearContractBoard(p)) {
         return "Outfitter contracts are booked at a Contract Board.";
      }
      GuidedHunts g = GuidedHunts.get(server);
      GuidedHunts.Hunt offer = null;
      for (GuidedHunts.Hunt o : todaysOutfitters(server)) {
         if (o.npcKey == key) {
            offer = o;
         }
      }
      if (offer == null) {
         return "That posting has come down. Check today's board.";
      }
      if (g.isBooked(p.getUUID(), key)) {
         return "You already took that contract.";
      }
      if (g.activeFor(p.getUUID()) != null) {
         return "Finish or call off your current guided hunt first.";
      }
      if (!Tokens.spend(p.serverLevel(), p.getUUID(), offer.deposit)) {
         return "The booking deposit is " + offer.deposit + " tokens.";
      }
      offer.id = g.nextId++;
      offer.client = p.getUUID();
      offer.clientName = name(p);
      offer.escrow = offer.deposit;
      offer.state = GuidedHunts.State.ACTIVE;
      offer.created = now(server);
      offer.deadline = cal(server) + offer.days * 24000L;
      g.open.add(offer);
      g.markBooked(p.getUUID(), key);
      g.setDirty();
      Msg.tell(
         p,
         Msg.tag(offer.outfitter, Msg.MOSS)
            .append(Msg.text("Booked: " + offer.terms() + " within " + offer.days + " reserve days. Purse " + offer.reward + " tokens plus your " + offer.deposit + "-token deposit back.", Msg.PAPER))
      );
      Msg.sound(p, SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
      return null;
   }

   // ------------------------------------------------------------------ harvest check
   public static void onHarvest(MinecraftServer server, HarvestRecord r) {
      GuidedHunts g = GuidedHunts.get(server);
      GuidedHunts.Hunt h = g.activeFor(r.hunter);
      if (h == null) {
         return;
      }
      long t = now(server);
      ServerPlayer client = server.getPlayerList().getPlayer(r.hunter);
      if (cal(server) > h.deadline) {
         return;
      }
      if (r.quarry() != h.quarry()) {
         return;
      }
      if (!h.qualifies(r)) {
         Msg.bar(client, "Short of the contract · needs " + h.terms());
         return;
      }
      if (h.npc) {
         int purse = h.reward + h.escrow;
         Tokens.credit(server.overworld(), r.hunter, purse);
         g.close(h, GuidedHunts.State.SUCCESS, r.headline() + " · paid " + purse, t);
         if (client != null) {
            Msg.tell(client, Msg.tag(h.outfitter, Msg.BRASS).append(Msg.text("Contract filled with a " + r.headline() + ". " + h.guideName + " pays " + h.reward + " tokens and returns your deposit.", Msg.BRASS)));
            Msg.title(client, Msg.text("CONTRACT FILLED", Msg.BRASS), Msg.text(h.outfitter + " · +" + purse + " tokens", Msg.PAPER), 8, 50, 20);
            Msg.sound(client, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.1F);
         }
         return;
      }
      ServerPlayer guide = server.getPlayerList().getPlayer(h.guide);
      double range = CampsConfig.guideRange;
      boolean near = guide != null
         && guide.isAlive()
         && !guide.isSpectator()
         && guide.level().dimension().location().toString().equals(r.dim)
         && guide.distanceToSqr(r.x + 0.5, r.y + 0.5, r.z + 0.5) <= range * range * 1.5;
      if (client != null && guide != null && guide.level() == client.level()) {
         near |= guide.distanceToSqr(client) <= range * range;
      }
      if (!near) {
         if (client != null) {
            Msg.tell(client, Msg.tag("Guided hunt", Msg.MOSS).append(Msg.text("That animal qualifies, but " + h.guideName + " wasn't within " + (int)range + " m. The contract stays open.", Msg.RUST)));
         }
         return;
      }
      Tokens.credit(server.overworld(), h.guide, h.escrow);
      r.guide = h.guideName;
      g.close(h, GuidedHunts.State.SUCCESS, r.headline() + " · " + h.escrow + " tokens to " + h.guideName, t);
      MutableComponent m = Msg.tag("Guided hunt", Msg.BRASS).append(Msg.text(h.clientName + " filled the contract with a " + r.headline() + ". " + h.guideName + " earns " + h.escrow + " tokens.", Msg.BRASS));
      for (ServerPlayer sp : new ServerPlayer[]{client, guide}) {
         if (sp != null) {
            Msg.tell(sp, m);
            Msg.title(sp, Msg.text("SUCCESSFUL HUNT", Msg.BRASS), Msg.text(r.headline(), Msg.PAPER), 8, 50, 20);
            Msg.sound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.1F);
            CampsViews.refreshIfOpen(sp);
            com.formaworks.frontierhunts.journal.JournalHooks.guidedHunt(sp, sp == guide); // [journal] guided hunt
         }
      }
   }

   // ------------------------------------------------------------------ expiry
   public static void tick(MinecraftServer server, long tick) {
      if (tick % 40L != 0L) {
         return;
      }
      GuidedHunts g = GuidedHunts.get(server);
      long t = now(server);
      for (GuidedHunts.Hunt h : new ArrayList<>(g.open)) {
         if (h.state == GuidedHunts.State.OFFERED && t > h.offerUntil) {
            g.close(h, GuidedHunts.State.EXPIRED, "Offer lapsed", t);
            ServerPlayer guide = server.getPlayerList().getPlayer(h.guide);
            Msg.bar(guide, "Your offer to " + h.clientName + " lapsed.");
         } else if (h.state == GuidedHunts.State.ACTIVE && cal(server) > h.deadline) {
            ServerPlayer client = server.getPlayerList().getPlayer(h.client);
            if (h.npc) {
               g.close(h, GuidedHunts.State.EXPIRED, "Time ran out · deposit kept", t);
               if (client != null) {
                  Msg.tell(client, Msg.tag(h.outfitter, Msg.RUST).append(Msg.text("Time ran out on the " + h.terms() + " contract. The deposit is forfeit.", Msg.PAPER)));
               }
            } else {
               Tokens.credit(server.overworld(), h.client, h.escrow);
               g.close(h, GuidedHunts.State.EXPIRED, "Time ran out · " + h.escrow + " tokens refunded", t);
               MutableComponent m = Msg.tag("Guided hunt", Msg.RUST).append(Msg.text("Time ran out on " + h.clientName + "'s hunt with " + h.guideName + ". The " + h.escrow + "-token fee went back to the client.", Msg.PAPER));
               Msg.tell(client, m);
               Msg.tell(server.getPlayerList().getPlayer(h.guide), m);
            }
         }
      }
   }

   // ------------------------------------------------------------------ commands
   public static void commands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         Commands.literal("guide")
            .executes(ctx -> {
               CampsViews.sendGuided(ctx.getSource().getPlayerOrException(), true);
               return 1;
            })
            .then(Commands.literal("offer").then(Commands.argument("client", EntityArgument.player()).then(Commands.argument("species", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Quarry.values()).map(q -> q.id), b))
               .then(Commands.argument("minScore", IntegerArgumentType.integer(0, 400)).then(Commands.argument("minWeight", IntegerArgumentType.integer(0, 1500)).then(Commands.argument("days", IntegerArgumentType.integer(1, MAX_DAYS)).then(Commands.argument("fee", IntegerArgumentType.integer(0, MAX_FEE)).executes(ctx -> run(ctx, p -> offer(p, EntityArgument.getPlayer(ctx, "client"), StringArgumentType.getString(ctx, "species"), IntegerArgumentType.getInteger(ctx, "minScore"), IntegerArgumentType.getInteger(ctx, "minWeight"), IntegerArgumentType.getInteger(ctx, "days"), IntegerArgumentType.getInteger(ctx, "fee")))))))))))
            .then(Commands.literal("accept").then(Commands.argument("id", LongArgumentType.longArg(1L)).executes(ctx -> run(ctx, p -> accept(p, LongArgumentType.getLong(ctx, "id"))))))
            .then(Commands.literal("decline").then(Commands.argument("id", LongArgumentType.longArg(1L)).executes(ctx -> run(ctx, p -> decline(p, LongArgumentType.getLong(ctx, "id"))))))
            .then(Commands.literal("cancel").then(Commands.argument("id", LongArgumentType.longArg(1L)).executes(ctx -> run(ctx, p -> cancel(p, LongArgumentType.getLong(ctx, "id"))))))
            .then(Commands.literal("book").then(Commands.argument("slot", IntegerArgumentType.integer(1, 3)).executes(ctx -> run(ctx, p -> book(p, reserveDay(p.server) * 8L + IntegerArgumentType.getInteger(ctx, "slot") - 1)))))
            .then(Commands.literal("status").executes(ctx -> {
               ServerPlayer p = ctx.getSource().getPlayerOrException();
               List<GuidedHunts.Hunt> l = GuidedHunts.get(p.server).involving(p.getUUID());
               if (l.isEmpty()) {
                  Msg.tell(p, Msg.text("No open guided hunts. Outfitters post contracts on every Contract Board.", Msg.PAPER));
               }
               long t = now(p.server);
               for (GuidedHunts.Hunt h : l) {
                  String who = h.npc ? h.outfitter : (p.getUUID().equals(h.guide) ? "client " + h.clientName : "guide " + h.guideName);
                  String left = h.state == GuidedHunts.State.ACTIVE ? String.format(java.util.Locale.ROOT, "%.1f reserve days left", Math.max(0L, h.deadline - cal(p.server)) / 24000.0) : "offer";
                  Msg.tell(p, Msg.text("#" + h.id + " · " + h.terms() + " · " + who + " · " + left, Msg.PAPER));
               }
               return 1;
            }))
      );
   }

   interface Action {
      String run(ServerPlayer p) throws CommandSyntaxException;
   }

   static int run(CommandContext<CommandSourceStack> ctx, GuidedService.Action a) throws CommandSyntaxException {
      ServerPlayer p = ctx.getSource().getPlayerOrException();
      String err = a.run(p);
      if (err != null) {
         ctx.getSource().sendFailure(Component.literal(err));
         return 0;
      }
      CampsViews.refreshIfOpen(p);
      return 1;
   }
}
