package com.formaworks.frontierhunts.camps;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/** Server-side handler for camp screen requests. Every argument is untrusted: parsed, clamped and re-checked. */
public final class CampsActions {
   public static final int CLOSE = 99;

   private CampsActions() {
   }

   public static void handle(ServerPlayer p, CampsNet.Req req) {
      if (p == null || p.isRemoved()) {
         return;
      }
      CampsViews.Session s = CampsViews.session(p);
      long now = p.level().getGameTime();
      int op = req.op();
      if (op == CLOSE) {
         s.openUntil = 0L;
         return;
      }
      if (s.lastAction != Long.MIN_VALUE && now - s.lastAction < (op == CampsNet.OPEN ? 2L : 4L)) {
         return;
      }
      s.lastAction = now;
      if (op == CampsNet.OPEN || op == CampsNet.OPEN_FROM_BOARD) {
         open(p, s, req, op == CampsNet.OPEN_FROM_BOARD);
         return;
      }
      if (!p.isAlive() || p.isSpectator()) {
         return;
      }
      String err;
      try {
         err = switch (op) {
            case CampsNet.CAMP_FOUND -> CampService.found(p, req.arg(0), color(req.arg(1)), s.post);
            case CampsNet.CAMP_INVITE -> CampService.invite(p, p.server.getPlayerList().getPlayerByName(req.arg(0)));
            case CampsNet.CAMP_ACCEPT -> CampService.accept(p, req.arg(0));
            case CampsNet.CAMP_DECLINE -> CampService.decline(p, req.arg(0));
            case CampsNet.CAMP_LEAVE -> CampService.leave(p);
            case CampsNet.CAMP_KICK -> CampService.kick(p, req.arg(0));
            case CampsNet.CAMP_RESPAWN -> CampService.setRespawn(p, "1".equals(req.arg(0)));
            case CampsNet.CAMP_UPGRADE -> CampService.upgrade(p);
            case CampsNet.CAMP_EDIT -> CampService.edit(p, req.arg(0), color(req.arg(1)));
            case CampsNet.CAMP_BIND -> s.post == null ? "Stand at the camp post." : CampService.bind(p, s.post);
            case CampsNet.CAMP_ASK -> CampService.ask(p, uuid(req.arg(0)));
            case CampsNet.CAMP_DISBAND -> CampService.disband(p);
            case CampsNet.GUIDE_OFFER -> GuidedService.offer(
               p, p.server.getPlayerList().getPlayerByName(req.arg(0)), req.arg(1), num(req.arg(2), 0, 400), num(req.arg(3), 0, 1500), num(req.arg(4), 1, GuidedService.MAX_DAYS), num(req.arg(5), 0, GuidedService.MAX_FEE)
            );
            case CampsNet.GUIDE_ACCEPT -> GuidedService.accept(p, lnum(req.arg(0)));
            case CampsNet.GUIDE_DECLINE -> GuidedService.decline(p, lnum(req.arg(0)));
            case CampsNet.GUIDE_CANCEL -> GuidedService.cancel(p, lnum(req.arg(0)));
            case CampsNet.NPC_BOOK -> GuidedService.book(p, lnum(req.arg(0)));
            default -> null;
         };
      } catch (RuntimeException e) {
         err = "That didn't work.";
      }
      if (err != null) {
         p.displayClientMessage(Component.literal(err).withStyle(st -> st.withColor(Msg.RUST)), true);
         Msg.sound(p, SoundEvents.VILLAGER_NO, 0.4F, 1.1F);
      }
      CampsViews.send(p, false);
   }

   private static void open(ServerPlayer p, CampsViews.Session s, CampsNet.Req req, boolean fromBoard) {
      String tab = fromBoard ? "guided" : req.arg(0);
      switch (tab) {
         case "guided" -> s.tab = "guided";
         case "records" -> {
            s.tab = "records";
            RecordBook.Category c = RecordBook.Category.find(req.arg(1));
            if (c != null) {
               s.cat = c;
            }
            String scope = req.arg(2);
            if (scope.equals("season") || scope.equals("alltime") || scope.equals("archive")) {
               s.scope = scope;
            }
            s.archive = num(req.arg(3), 0, 64);
         }
         case "events" -> s.tab = "events";
         default -> s.tab = "camp";
      }
      boolean first = s.openUntil <= p.level().getGameTime() || fromBoard;
      CampsViews.send(p, first);
      s.openUntil = p.level().getGameTime() + 12000L;
   }

   static int color(String s) {
      return Math.floorMod(num(s, 0, 15), 16);
   }

   static int num(String s, int lo, int hi) {
      try {
         return Math.clamp(Integer.parseInt(s.trim()), lo, hi);
      } catch (Exception e) {
         return lo;
      }
   }

   static long lnum(String s) {
      try {
         return Long.parseLong(s.trim());
      } catch (Exception e) {
         return -1L;
      }
   }

   static UUID uuid(String s) {
      try {
         return UUID.fromString(s.trim());
      } catch (Exception e) {
         return new UUID(0L, 0L);
      }
   }
}
