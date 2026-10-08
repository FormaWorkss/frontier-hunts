package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class TargetFace extends BlockEntity {
   public static final double HALF_WIDTH = 0.4375;
   public static final double HALF_HEIGHT = 0.40625;
   public static final double FACE_CENTRE_Y = 0.59375;
   public static final double FACE_INSET = 0.0625;
   public static final int MAX_HITS = 64;
   public static final int MAX_STUCK = 24;
   public static final byte ARROW = 0;
   public static final byte ARROW_HOLE = 1;
   public static final byte BULLET = 2;
   private final List<TargetFace.Hit> hits = new ArrayList<>();
   private final List<TargetFace.Hit> view = Collections.unmodifiableList(this.hits);

   public TargetFace(BlockPos var1, BlockState var2) {
      super((BlockEntityType)ExpeditionContent.TARGET_FACE.get(), var1, var2);
   }

   public List<TargetFace.Hit> hits() {
      return this.view;
   }

   public int stuckArrows() {
      int var1 = 0;

      for (TargetFace.Hit var3 : this.hits) {
         if (var3.kind() == 0) {
            var1++;
         }
      }

      return var1;
   }

   public static float[] local(BlockPos var0, Direction var1, Vec3 var2) {
      double var3 = var2.x - ((double)var0.getX() + 0.5);
      double var5 = var2.y - ((double)var0.getY() + 0.59375);
      double var7 = var2.z - ((double)var0.getZ() + 0.5);

      double var9 = switch (var1) {
         case NORTH -> -var3;
         case SOUTH -> var3;
         case WEST -> var7;
         default -> -var7;
      };
      return new float[]{(float)(var9 / 0.4375), (float)(var5 / 0.40625)};
   }

   public static Vec3 world(BlockPos var0, Direction var1, float var2, float var3, double var4) {
      double var6 = (double)var2 * 0.4375;
      double var8 = (double)var3 * 0.40625;
      double var10 = (double)var0.getX() + 0.5;
      double var12 = (double)var0.getY() + 0.59375 + var8;
      double var14 = (double)var0.getZ() + 0.5;
      double var16 = 0.4375 + var4;

      return switch (var1) {
         case NORTH -> new Vec3(var10 - var6, var12, var14 - var16);
         case SOUTH -> new Vec3(var10 + var6, var12, var14 + var16);
         case WEST -> new Vec3(var10 - var16, var12, var14 + var6);
         default -> new Vec3(var10 + var16, var12, var14 - var6);
      };
   }

   public static boolean record(Level var0, BlockPos var1, Vec3 var2, byte var3, ArrowTip var4, boolean var5) {
      return record(var0, var1, var2, var3, var4, var5, Vec3.ZERO);
   }

   public static boolean record(Level var0, BlockPos var1, Vec3 var2, byte var3, ArrowTip var4, boolean var5, Vec3 var6) {
      if (var0.isClientSide) {
         return false;
      } else {
         BlockState var7 = var0.getBlockState(var1);
         if (!(var7.getBlock() instanceof ShootingTarget)) {
            return false;
         } else if (!(var0.getBlockEntity(var1) instanceof TargetFace var8)) {
            return false;
         } else {
            Direction var14 = (Direction)var7.getValue(ShootingTarget.FACING);
            float[] var10 = local(var1, var14, var2);
            if (Math.abs(var10[0]) > 1.05F || Math.abs(var10[1]) > 1.05F) {
               return false;
            } else if (var3 == 0 && var8.stuckArrows() >= 24) {
               return false;
            } else {
               float[] var11 = lean(var14, var6);
               var8.hits
                  .add(new TargetFace.Hit(var10[0], var10[1], var3, (byte)(var4 == null ? -1 : var4.ordinal()), var5, var0.getGameTime(), var11[0], var11[1]));

               while (var8.hits.size() > 64) {
                  int var12 = -1;

                  for (int var13 = 0; var13 < var8.hits.size(); var13++) {
                     if (var8.hits.get(var13).kind() != 0) {
                        var12 = var13;
                        break;
                     }
                  }

                  if (var12 < 0) {
                     break;
                  }

                  var8.hits.remove(var12);
               }

               var8.setChanged();
               var0.sendBlockUpdated(var1, var7, var7, 3);
               return true;
            }
         }
      }
   }

   private static float[] lean(Direction var0, Vec3 var1) {
      if (var1 != null && !(var1.lengthSqr() < 1.0E-6)) {
         Vec3 var2 = var1.normalize();

         double var3 = switch (var0) {
            case NORTH -> var2.z;
            case SOUTH -> -var2.z;
            case WEST -> var2.x;
            default -> -var2.x;
         };

         double var5 = switch (var0) {
            case NORTH -> -var2.x;
            case SOUTH -> var2.x;
            case WEST -> var2.z;
            default -> -var2.z;
         };
         return var3 <= 0.05
            ? new float[]{0.0F, 0.0F}
            : new float[]{(float)Math.toDegrees(Math.atan2(-var2.y, var3)), (float)Math.toDegrees(Math.atan2(var5, var3))};
      } else {
         return new float[]{0.0F, 0.0F};
      }
   }

   public List<ItemStack> pull() {
      ArrayList var1 = new ArrayList();

      for (int var2 = 0; var2 < this.hits.size(); var2++) {
         TargetFace.Hit var3 = this.hits.get(var2);
         if (var3.kind() == 0) {
            ArrowTip var4 = var3.tip() < 0 ? ArrowTip.FIELD_POINT : ArrowTip.byOrdinal(var3.tip());
            var1.add(new ArrowSupply.Shot(var4, var3.primitive()).stack(1));
            this.hits.set(var2, new TargetFace.Hit(var3.u(), var3.v(), (byte)1, var3.tip(), var3.primitive(), var3.at(), var3.pitch(), var3.yaw()));
         }
      }

      if (!var1.isEmpty()) {
         this.setChanged();
      }

      return var1;
   }

   public Optional<ItemStack> pullNearest(float var1, float var2, double var3) {
      int var5 = -1;
      double var6 = var3 * var3;

      for (int var8 = 0; var8 < this.hits.size(); var8++) {
         TargetFace.Hit var9 = this.hits.get(var8);
         if (var9.kind() == 0) {
            double var10 = (double)(var9.u() - var1) * 0.4375;
            double var12 = (double)(var9.v() - var2) * 0.40625;
            double var14 = var10 * var10 + var12 * var12;
            if (var14 <= var6) {
               var6 = var14;
               var5 = var8;
            }
         }
      }

      if (var5 < 0) {
         return Optional.empty();
      } else {
         TargetFace.Hit var16 = this.hits.get(var5);
         ArrowTip var17 = var16.tip() < 0 ? ArrowTip.FIELD_POINT : ArrowTip.byOrdinal(var16.tip());
         this.hits.set(var5, new TargetFace.Hit(var16.u(), var16.v(), (byte)1, var16.tip(), var16.primitive(), var16.at(), var16.pitch(), var16.yaw()));
         this.setChanged();
         return Optional.of(new ArrowSupply.Shot(var17, var16.primitive()).stack(1));
      }
   }

   public void wipe() {
      this.hits.clear();
      this.setChanged();
   }

   public String report() {
      if (this.hits.isEmpty()) {
         return "Target is clean · nothing on the face yet";
      } else {
         int var1 = 0;
         double var2 = 0.0;
         double var4 = 0.0;

         for (TargetFace.Hit var7 : this.hits) {
            var1 = Math.max(var1, var7.score());
            var2 += (double)var7.u() * 0.4375;
            var4 += (double)var7.v() * 0.40625;
         }

         double var18 = var2 / (double)this.hits.size();
         double var8 = var4 / (double)this.hits.size();
         double var10 = 0.0;

         for (int var12 = 0; var12 < this.hits.size(); var12++) {
            for (int var13 = var12 + 1; var13 < this.hits.size(); var13++) {
               double var14 = (double)(this.hits.get(var12).u() - this.hits.get(var13).u()) * 0.4375;
               double var16 = (double)(this.hits.get(var12).v() - this.hits.get(var13).v()) * 0.40625;
               var10 = Math.max(var10, Math.hypot(var14, var16));
            }
         }

         String var19 = this.hits.size() < 2 ? "" : String.format(Locale.ROOT, " · centre %s, %s", offset(var8, "high", "low"), offset(var18, "right", "left"));
         return String.format(Locale.ROOT, "%d shots · best %d · group %.0f cm%s", this.hits.size(), var1, var10 * 100.0, var19);
      }
   }

   private static String offset(double var0, String var2, String var3) {
      int var4 = (int)Math.round(Math.abs(var0) * 100.0);
      return var4 < 1 ? "on" : var4 + " cm " + (var0 >= 0.0 ? var2 : var3);
   }

   public void tell(ServerPlayer var1, int var2) {
      ExpeditionService.message(var1, (var2 > 0 ? var2 + " arrow" + (var2 == 1 ? "" : "s") + " pulled · " : "") + this.report());
   }

   private ListTag save() {
      ListTag var1 = new ListTag();

      for (TargetFace.Hit var3 : this.hits) {
         CompoundTag var4 = new CompoundTag();
         var4.putFloat("u", var3.u());
         var4.putFloat("v", var3.v());
         var4.putByte("kind", var3.kind());
         var4.putByte("tip", var3.tip());
         var4.putBoolean("primitive", var3.primitive());
         var4.putLong("at", var3.at());
         var4.putFloat("pitch", var3.pitch());
         var4.putFloat("yaw", var3.yaw());
         var1.add(var4);
      }

      return var1;
   }

   private void load(ListTag var1) {
      this.hits.clear();

      for (int var2 = 0; var2 < var1.size() && this.hits.size() < 64; var2++) {
         CompoundTag var3 = var1.getCompound(var2);
         this.hits
            .add(
               new TargetFace.Hit(
                  var3.getFloat("u"),
                  var3.getFloat("v"),
                  var3.getByte("kind"),
                  var3.getByte("tip"),
                  var3.getBoolean("primitive"),
                  var3.getLong("at"),
                  var3.getFloat("pitch"),
                  var3.getFloat("yaw")
               )
            );
      }
   }

   protected void saveAdditional(CompoundTag var1, Provider var2) {
      super.saveAdditional(var1, var2);
      var1.put("hits", this.save());
   }

   protected void loadAdditional(CompoundTag var1, Provider var2) {
      super.loadAdditional(var1, var2);
      this.load(var1.getList("hits", 10));
   }

   public CompoundTag getUpdateTag(Provider var1) {
      CompoundTag var2 = super.getUpdateTag(var1);
      var2.put("hits", this.save());
      return var2;
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public static record Hit(float u, float v, byte kind, byte tip, boolean primitive, long at, float pitch, float yaw) {
      public Hit(float u, float v, byte kind, byte tip, boolean primitive, long at, float pitch, float yaw) {
         u = Float.isFinite(u) ? Math.clamp(u, -1.2F, 1.2F) : 0.0F;
         v = Float.isFinite(v) ? Math.clamp(v, -1.2F, 1.2F) : 0.0F;
         kind = (byte)Math.clamp((long)kind, 0, 2);
         tip = (byte)Math.clamp((long)tip, -1, ArrowTip.values().length - 1);
         at = Math.max(0L, at);
         pitch = Float.isFinite(pitch) ? Math.clamp(pitch, -70.0F, 70.0F) : 0.0F;
         yaw = Float.isFinite(yaw) ? Math.clamp(yaw, -70.0F, 70.0F) : 0.0F;
         this.u = u;
         this.v = v;
         this.kind = kind;
         this.tip = tip;
         this.primitive = primitive;
         this.at = at;
         this.pitch = pitch;
         this.yaw = yaw;
      }

      public Hit(float var1, float var2, byte var3, byte var4, boolean var5, long var6) {
         this(var1, var2, var3, var4, var5, var6, 0.0F, 0.0F);
      }

      public double radius() {
         return Math.hypot((double)this.u, (double)this.v);
      }

      public int score() {
         double var1 = this.radius();
         return var1 > 1.0 ? 0 : Math.max(1, 10 - (int)(var1 * 10.0));
      }

      public double metres() {
         return Math.hypot((double)this.u * 0.4375, (double)this.v * 0.40625);
      }
   }
}
