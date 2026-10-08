package com.formaworks.frontierhunts.licence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * [licence] What is written on a filled (notched) tag: the animal, when and where it was taken, its weight and who
 * took it. Also used for the hunter's record in {@link LicenceStore} and on the journal page.
 *
 * @param kind   tag kind key ("deer", "bear", ...) or a stamp key for birds
 * @param date   reserve date ("Oct 12, 6:42 AM")
 * @param place  "x, z · Biome"
 * @param kg10   live weight in tenths of a kilogram
 * @param status 0 legal (tagged), 1 violation (poached)
 */
public record FilledTag(String kind, String species, String title, int period, String date, String place, int kg10, int points, String hunter,
   int status) {
   public static final Codec<FilledTag> CODEC = RecordCodecBuilder.create(i -> i.group(
      Codec.string(0, 24).fieldOf("kind").forGetter(FilledTag::kind),
      Codec.string(0, 32).fieldOf("species").forGetter(FilledTag::species),
      Codec.string(0, 40).fieldOf("title").forGetter(FilledTag::title),
      Codec.INT.fieldOf("period").forGetter(FilledTag::period),
      Codec.string(0, 48).fieldOf("date").forGetter(FilledTag::date),
      Codec.string(0, 80).fieldOf("place").forGetter(FilledTag::place),
      Codec.INT.fieldOf("kg10").forGetter(FilledTag::kg10),
      Codec.INT.optionalFieldOf("points", 0).forGetter(FilledTag::points),
      Codec.string(0, 40).fieldOf("hunter").forGetter(FilledTag::hunter),
      Codec.INT.optionalFieldOf("status", 0).forGetter(FilledTag::status)
   ).apply(i, FilledTag::new));
   public static final StreamCodec<ByteBuf, FilledTag> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("k", this.kind);
      t.putString("s", this.species);
      t.putString("t", this.title);
      t.putInt("p", this.period);
      t.putString("d", this.date);
      t.putString("w", this.place);
      t.putInt("kg", this.kg10);
      t.putInt("pt", this.points);
      t.putString("h", this.hunter);
      t.putInt("st", this.status);
      return t;
   }

   public static FilledTag load(CompoundTag t) {
      return new FilledTag(cut(t.getString("k"), 24), cut(t.getString("s"), 32), cut(t.getString("t"), 40), t.getInt("p"), cut(t.getString("d"), 48),
         cut(t.getString("w"), 80), Math.max(0, t.getInt("kg")), Math.max(0, t.getInt("pt")), cut(t.getString("h"), 40), Math.clamp(t.getInt("st"), 0, 1));
   }

   static String cut(String s, int n) {
      return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
   }

   /** "96 kg" or "1.2 kg". */
   public String weight() {
      return this.kg10 >= 100 ? Math.round(this.kg10 / 10.0) + " kg" : String.format(java.util.Locale.ROOT, "%.1f kg", this.kg10 / 10.0);
   }

   public boolean legal() {
      return this.status == 0;
   }
}
