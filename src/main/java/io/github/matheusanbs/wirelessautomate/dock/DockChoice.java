package io.github.matheusanbs.wirelessautomate.dock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A Doca escolhida no Vinculador (componente {@code wirelessautomate:linker_dock}): a posição com a dimensão e o lado
 * clicado ({@code Direction.ordinal()}, de 0 a 5). O servidor confere a cada clique se a Doca ainda está lá.
 */
public record DockChoice(GlobalPos pos, int side) {
    public static final Codec<DockChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(DockChoice::pos),
            Codec.intRange(0, 5).fieldOf("side").forGetter(DockChoice::side)).apply(i, DockChoice::new));

    public static final StreamCodec<ByteBuf, DockChoice> STREAM_CODEC = StreamCodec.composite(
            GlobalPos.STREAM_CODEC, DockChoice::pos,
            ByteBufCodecs.VAR_INT, DockChoice::side,
            DockChoice::new);
}
