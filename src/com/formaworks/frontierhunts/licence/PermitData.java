package com.formaworks.frontierhunts.licence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * [licence] Who a licence, tag or stamp was issued to and for which licence season ({@link Regulations#period}). Items
 * without it are blank forms (creative / {@code /give}) and are not valid.
 */
public record PermitData(UUID owner, String name, int period) {
   public static final Codec<PermitData> CODEC = RecordCodecBuilder.create(i -> i.group(
      UUIDUtil.CODEC.fieldOf("owner").forGetter(PermitData::owner),
      Codec.string(0, 40).fieldOf("name").forGetter(PermitData::name),
      Codec.INT.fieldOf("period").forGetter(PermitData::period)
   ).apply(i, PermitData::new));
   public static final StreamCodec<ByteBuf, PermitData> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, PermitData::owner,
      ByteBufCodecs.stringUtf8(40), PermitData::name,
      ByteBufCodecs.VAR_INT, PermitData::period,
      PermitData::new
   );

   public boolean validFor(UUID who, int now) {
      return this.owner.equals(who) && this.period == now;
   }
}
