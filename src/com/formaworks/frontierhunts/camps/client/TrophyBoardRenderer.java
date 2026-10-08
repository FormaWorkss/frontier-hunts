package com.formaworks.frontierhunts.camps.client;

import com.formaworks.frontierhunts.camps.Fmt;
import com.formaworks.frontierhunts.camps.TrophyBoardBlock;
import com.formaworks.frontierhunts.camps.TrophyBoardBlockEntity;
import com.formaworks.frontierhunts.client.CampTrophyArt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the live season standings on a Big-Buck Board: five real trophy mounts (the same plaque/head/rack art as a
 * wall-hung trophy, so every graphics preset matches), engraved brass plates, the carved header and the honour roll.
 * Only text and mounts are dynamic; the board itself is a baked block model (shader-friendly).
 */
public final class TrophyBoardRenderer implements BlockEntityRenderer<TrophyBoardBlockEntity> {
   /** slot x (metres from the origin block centre) for ranks 1..5: podium order, best in the middle */
   private static final double[] SLOT_X = new double[]{0.0, -0.52, 0.52, -1.04, 1.04};
   private static final String[] HEADERS = new String[]{"BIG BUCK BOARD", "BULL ELK BOARD", "BULL MOOSE BOARD"};
   private static final int GOLD = 0xFFE6C579;
   private static final int ENGRAVE = 0xFF3B2A12;
   private static final int CREAM = 0xFFEDE3C8;
   private static final int CREAM_DIM = 0xFFB9B196;
   private static final double FRONT = 9.0 / 16.0;
   private final Font font;

   public TrophyBoardRenderer(BlockEntityRendererProvider.Context ctx) {
      this.font = ctx.getFont();
   }

   static float yRot(Direction facing) {
      return (facing.toYRot() + 180.0F) % 360.0F;
   }

   @Override
   public void render(TrophyBoardBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
      if (!be.getBlockState().hasProperty(TrophyBoardBlock.FACING)) {
         return;
      }
      Direction facing = be.getBlockState().getValue(TrophyBoardBlock.FACING);
      int idx = be.shownIndex();
      List<LiveCache.Entry> entries = LiveCache.board(LiveCache.Board.values()[Math.min(idx, 2)]);
      pose.pushPose();
      pose.translate(0.5, 0.0, 0.5);
      pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
      // header
      String header = HEADERS[Math.min(idx, 2)] + "  ·  SEASON " + LiveCache.season;
      WorldText.draw(pose, buf, this.font, header, 0.0, 1.842, 7.36 / 16.0 - 0.5, WorldText.fit(this.font, header, 0.0088F, 2.1), GOLD, light, 0);
      // mounts and plates
      for (int rank = 0; rank < 5; rank++) {
         double x = SLOT_X[rank];
         LiveCache.Entry e = rank < entries.size() ? entries.get(rank) : null;
         if (e != null) {
            ItemStack trophy = e.trophy();
            if (trophy != null) {
               pose.pushPose();
               double s = rank == 0 ? 0.64 : 0.58;
               pose.translate(x, rank == 0 ? 1.19 : 1.15, FRONT - 0.5 - 0.027 * s);
               pose.scale((float)s, (float)s, (float)s);
               CampTrophyArt.drawMount(trophy, pose, buf, light);
               pose.popPose();
            }
            this.plate(pose, buf, light, x, rank + 1, e);
         } else {
            WorldText.draw(pose, buf, this.font, "#" + (rank + 1) + "  open", x, 0.753, 8.53 / 16.0 - 0.5, 0.0045F, 0xFF6B5427, light, 0);
         }
      }
      // honour roll on the felt (ranks 6-10)
      double feltZ = 8.47 / 16.0 - 0.5;
      if (entries.isEmpty()) {
         WorldText.draw(pose, buf, this.font, "NO RACKS ENTERED THIS SEASON", 0.0, 0.43, feltZ, 0.0058F, GOLD, light, 0);
         WorldText.draw(pose, buf, this.font, "Recover a buck and it takes its place here", 0.0, 0.33, feltZ, 0.0048F, CREAM_DIM, light, 0);
         WorldText.draw(pose, buf, this.font, "Right-click for the full record book", 0.0, 0.25, feltZ, 0.0042F, CREAM_DIM, light, 0);
      } else {
         WorldText.draw(pose, buf, this.font, "HONOUR ROLL", 0.0, 0.54, feltZ, 0.0052F, GOLD, light, 0);
         if (entries.size() <= 5) {
            WorldText.draw(pose, buf, this.font, "Ranks six to ten are still open", 0.0, 0.36, feltZ, 0.0046F, CREAM_DIM, light, 0);
         }
         for (int i = 5; i < Math.min(10, entries.size()); i++) {
            LiveCache.Entry e = entries.get(i);
            double y = 0.465 - (i - 5) * 0.066;
            String left = (i + 1) + ".  " + WorldText.clip(this.font, e.name, 110);
            String right = Fmt.inches(e.score) + (e.pl + e.pr > 0 ? "  " + e.pl + "x" + e.pr : "") + "  ·  " + e.date;
            WorldText.draw(pose, buf, this.font, left, 0.93, y, feltZ, 0.0046F, CREAM, light, -1);
            WorldText.draw(pose, buf, this.font, right, -0.93, y, feltZ, 0.0046F, e.legendary ? GOLD : CREAM, light, 1);
         }
      }
      pose.popPose();
   }

   private void plate(PoseStack pose, MultiBufferSource buf, int light, double x, int rank, LiveCache.Entry e) {
      double z = 8.53 / 16.0 - 0.5;
      String name = "#" + rank + "  " + e.name.toUpperCase(Locale.ROOT);
      String score = Fmt.inches(e.score) + (e.pl + e.pr > 0 ? "  " + e.pl + "x" + e.pr : "") + (e.legendary ? "  ★" : "");
      WorldText.draw(pose, buf, this.font, WorldText.clip(this.font, name, 100), x, 0.797, z, 0.0041F, ENGRAVE, light, 0);
      WorldText.draw(pose, buf, this.font, score, x, 0.754, z, WorldText.fit(this.font, score, 0.0041F, 0.42), ENGRAVE, light, 0);
      WorldText.draw(pose, buf, this.font, e.date, x, 0.713, z, 0.0034F, 0xFF5A4520, light, 0);
   }

   @Override
   public int getViewDistance() {
      return 64;
   }

   @Override
   public boolean shouldRenderOffScreen(TrophyBoardBlockEntity be) {
      return true;
   }

   @Override
   public AABB getRenderBoundingBox(TrophyBoardBlockEntity be) {
      BlockPos p = be.getBlockPos();
      return new AABB(Vec3.atLowerCornerOf(p).subtract(2.0, 0.0, 2.0), Vec3.atLowerCornerOf(p).add(3.0, 2.6, 3.0));
   }
}
