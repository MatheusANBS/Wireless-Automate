package io.github.matheusanbs.wirelessautomate.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** O que o jogador pode fazer no painel do nó escolhido no Tablet. */
public enum PanelAccess {
    /** Nó carregado e ao alcance: o painel edita. */
    EDIT,
    /** Nó descarregado: só a última configuração conhecida, para ler. */
    READ_UNLOADED,
    /** Nó carregado, mas fora do alcance do tier: só leitura. */
    READ_OUT_OF_REACH;

    public static final StreamCodec<ByteBuf, PanelAccess> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], PanelAccess::ordinal);

    public boolean editable() {
        return this == EDIT;
    }
}
