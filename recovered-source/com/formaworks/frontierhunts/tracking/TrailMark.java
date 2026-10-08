package com.formaworks.frontierhunts.tracking;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * One piece of readable sign: a group of prints (one stride), a blood mark, or another clue.
 * [tracking] extended with {@code sign} (PrintKind for prints, BloodType for blood), {@code stride} (metres between
 * this print group and the previous one of the same animal) and {@code scale} (print size relative to the species
 * norm). The old 10/13-argument constructors are kept so classes compiled against them keep working.
 */
public record TrailMark(
   UUID id,
   UUID animal,
   Vec3 position,
   BlockPos support,
   float yaw,
   long created,
   int rainWear,
   boolean blood,
   String individual,
   int activity,
   Direction face,
   int style,
   float radius,
   int sign,
   float stride,
   float scale
) {
   public static final UUID UNKNOWN = new UUID(0L, 0L);
   /** legacy constant (other classes may have inlined it); real lifetimes come from {@link #lifetime()} */
   public static final int LIFETIME = 6000;
   public static final int DRIP = 0;
   public static final int IMPACT = 1;
   public static final int BRUSH = 2;
   public static final int DENSE = 3;
   public static final int POOL = 4;
   public static final int DROPPINGS = 5;
   public static final int FUR = 6;
   public static final int SCRATCH = 7;
   public static final int CALL = 8;
   public static final int PAW = 9;
   public static final int TWIG = 10;
   public static final int SCENT = 11;
   /** blood: where a wounded animal lay down (matted bed with a stain) */
   public static final int BED = 12;
   /** [1.1.6] grass and brush bent aside along an animal's path (drawn by bending the plants, not as a decal) */
   public static final int PASSAGE = 13;
   /** [1.1.6] plants pressed flat where an animal died or lay (drawn by bending the plants flat) */
   public static final int MATTED = 14;
   public static final int MAX_STYLE = 14;
   public static final int PASSAGE_LIFETIME = 9000;
   /** one in-game day */
   public static final int DAY = 24000;
   public static final int PRINT_LIFETIME = 36000;
   public static final int BLOOD_LIFETIME = 24000;
   public static final int POOL_LIFETIME = 36000;
   public static final int MAX_WEAR = 200000;

   public TrailMark(UUID var1, UUID var2, Vec3 var3, BlockPos var4, float var5, long var6, int var8, boolean var9, String var10, int var11) {
      this(var1, var2, var3, var4, var5, var6, var8, var9, var10, var11, Direction.UP, 0, 0.24F, 0, 0.0F, 1.0F);
   }

   public TrailMark(
      UUID id, UUID animal, Vec3 position, BlockPos support, float yaw, long created, int rainWear, boolean blood, String individual, int activity,
      Direction face, int style, float radius
   ) {
      this(id, animal, position, support, yaw, created, rainWear, blood, individual, activity, face, style, radius, 0, 0.0F, 1.0F);
   }

   public TrailMark {
      if (id == null
         || animal == null
         || support == null
         || position == null
         || individual == null
         || !Double.isFinite(position.x)
         || !Double.isFinite(position.y)
         || !Double.isFinite(position.z)
         || Math.abs(position.x) > 3.0E7
         || Math.abs(position.z) > 3.0E7
         || Math.abs(position.y) > 2048.0
         || !Float.isFinite(yaw)
         || created < 0L
         || rainWear < 0
         || rainWear > MAX_WEAR
         || individual.length() > 80
         || activity < 0
         || activity > 2
         || face == null
         || style < 0
         || style > MAX_STYLE
         || !Float.isFinite(radius)
         || radius < 0.01F
         || radius > 0.45F
         || sign < 0
         || sign > 63
         || !Float.isFinite(stride)
         || stride < 0.0F
         || stride > 16.0F
         || !Float.isFinite(scale)
         || scale < 0.2F
         || scale > 4.0F) {
         throw new IllegalArgumentException("Invalid trail mark");
      }
      support = support.immutable();
   }

   /** true for hoof/paw/boot print groups (non-blood style 0) */
   public boolean print() {
      return !this.blood && this.style == 0;
   }

   public String kind() {
      if (this.blood) {
         return this.style == BED ? "wound bed" : "blood";
      }
      return switch (this.style) {
         case 5 -> "droppings";
         case 6 -> "shed fur";
         case 7 -> "scratch marks";
         case 8 -> "call site";
         case 9 -> "pawprints";
         case 10 -> "broken vegetation";
         case 11 -> "scent trace (tracking aid)";
         case PASSAGE -> "grass pushed aside";
         case MATTED -> "pressed-down grass";
         default -> PrintKind.of(this.sign).noun;
      };
   }

   public String threat() {
      String var1 = this.individual.toLowerCase(Locale.ROOT);
      return !var1.contains("bear") && !var1.contains("cougar") && !var1.contains("wolf") && !var1.contains("lion") && !var1.contains("panther")
            && !var1.contains("coyote")
         ? (!var1.contains("moose") && !var1.contains("boar") && !var1.contains("bison") ? "prey sign" : "territorial wildlife")
         : "predator sign";
   }

   public Vec3 normal() {
      return Vec3.atLowerCornerOf(this.face.getNormal());
   }

   public long age(long var1) {
      return Math.max(0L, var1 - this.created);
   }

   public long effectiveAge(long var1) {
      return this.age(var1) + (long)this.rainWear;
   }

   /** How long this sign stays readable in dry weather (weather wear shortens it). */
   public int lifetime() {
      if (this.blood) {
         return this.style == POOL || this.style == BED ? POOL_LIFETIME : BLOOD_LIFETIME;
      }
      if (this.style == FUR || this.style == SCRATCH) {
         return 2 * DAY; // [ecology] hair and scuffle marks at a kill site last as long as the carcass
      }
      if (this.style == PASSAGE) {
         return PASSAGE_LIFETIME; // [1.1.6] bent grass stands back up in about 7 minutes
      }
      if (this.style == MATTED) {
         return POOL_LIFETIME;
      }
      return this.style == 0 ? PRINT_LIFETIME : 6000;
   }

   /** [1.1.6] bent or pressed plants (shown by bending the plant models; never drawn as a decal) */
   public boolean plantSign() {
      return !this.blood && (this.style == PASSAGE || this.style == MATTED);
   }

   /** 0 = just made .. 1 = gone */
   public float wearFraction(long now) {
      return Math.min(1.0F, (float)this.effectiveAge(now) / (float)this.lifetime());
   }

   public boolean expired(long var1) {
      return this.effectiveAge(var1) > (long)this.lifetime();
   }

   public TrailMark weather(int var1) {
      return new TrailMark(
         this.id, this.animal, this.position, this.support, this.yaw, this.created,
         Math.min(MAX_WEAR, this.rainWear + Math.max(0, var1)),
         this.blood, this.individual, this.activity, this.face, this.style, this.radius, this.sign, this.stride, this.scale
      );
   }

   public TrailMark with(int kind, float stride, float scale) {
      return new TrailMark(
         this.id, this.animal, this.position, this.support, this.yaw, this.created, this.rainWear, this.blood, this.individual, this.activity,
         this.face, this.style, this.radius, Math.clamp(kind, 0, 63), Math.clamp(stride, 0.0F, 16.0F), Math.clamp(scale, 0.2F, 4.0F)
      );
   }

   public TrailMark at(Vec3 position, BlockPos support) {
      return new TrailMark(
         this.id, this.animal, position, support, this.yaw, this.created, this.rainWear, this.blood, this.individual, this.activity, this.face,
         this.style, this.radius, this.sign, this.stride, this.scale
      );
   }

   public TrailMark styled(int style, float radius) {
      return new TrailMark(
         this.id, this.animal, this.position, this.support, this.yaw, this.created, this.rainWear, this.blood, this.individual, this.activity,
         this.face, style, radius, this.sign, this.stride, this.scale
      );
   }

   public CompoundTag save() {
      CompoundTag var1 = new CompoundTag();
      var1.putUUID("id", this.id);
      var1.putUUID("animal", this.animal);
      var1.putDouble("x", this.position.x);
      var1.putDouble("y", this.position.y);
      var1.putDouble("z", this.position.z);
      var1.putLong("support", this.support.asLong());
      var1.putFloat("yaw", this.yaw);
      var1.putLong("created", this.created);
      var1.putInt("rain_wear", this.rainWear);
      var1.putBoolean("blood", this.blood);
      var1.putString("individual", this.individual);
      var1.putInt("activity", this.activity);
      var1.putByte("face", (byte)this.face.ordinal());
      var1.putByte("style", (byte)this.style);
      var1.putFloat("radius", this.radius);
      var1.putByte("kind", (byte)this.sign);
      var1.putFloat("stride", this.stride);
      var1.putFloat("scale", this.scale);
      return var1;
   }

   public static TrailMark load(CompoundTag var0) {
      return new TrailMark(
         var0.getUUID("id"),
         var0.getUUID("animal"),
         new Vec3(var0.getDouble("x"), var0.getDouble("y"), var0.getDouble("z")),
         BlockPos.of(var0.getLong("support")),
         var0.getFloat("yaw"),
         var0.getLong("created"),
         Math.clamp(var0.getInt("rain_wear"), 0, MAX_WEAR),
         var0.getBoolean("blood"),
         var0.getString("individual"),
         var0.getInt("activity"),
         var0.contains("face") ? readFace(var0.getByte("face")) : Direction.UP,
         var0.getByte("style"),
         var0.contains("radius") ? var0.getFloat("radius") : 0.24F,
         var0.contains("kind") ? Math.clamp(var0.getByte("kind"), 0, 63) : 0,
         var0.contains("stride") ? Math.clamp(var0.getFloat("stride"), 0.0F, 16.0F) : 0.0F,
         var0.contains("scale") ? Math.clamp(var0.getFloat("scale"), 0.2F, 4.0F) : 1.0F
      );
   }

   public static Direction readFace(int var0) {
      if (var0 >= 0 && var0 < 6) {
         return Direction.values()[var0];
      } else {
         throw new IllegalArgumentException("Invalid trail face");
      }
   }
}
