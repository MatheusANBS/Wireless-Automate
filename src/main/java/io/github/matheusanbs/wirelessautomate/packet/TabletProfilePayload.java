package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMarks;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: o perfil do Tablet. Copiar o perfil de um nó, esquecê-lo, colá-lo num nó ou nos
 * marcados, e desfazer o último colar. Cada ação só faz o que o campo dela pede e o
 * servidor confere tudo ({@code TabletMenu#profile}).
 *
 * @param key     COPY e PASTE_ONE: o nó
 * @param keys    PASTE_SELECTED: os nós marcados (colar na presa), no máximo {@link #MAX_KEYS}
 * @param marks   PASTE_SELECTED: as máquinas, os grupos e os blocos marcados
 */
public record TabletProfilePayload(int containerId, Action action, Optional<NodeKey> key,
        List<NodeKey> keys, TabletMarks marks) implements CustomPacketPayload {
    /** Nós marcados por pacote; uma lista maior é recusada na leitura. */
    public static final int MAX_KEYS = 256;

    /** O que o pacote pede. */
    public enum Action {
        /** Guarda no Tablet o perfil do nó {@code key}. */
        COPY,
        /** Apaga o perfil guardado. */
        FORGET,
        /** Cola o perfil no nó {@code key}. */
        PASTE_ONE,
        /** Cola nos nós {@code keys} e nos marcados {@code marks}, menos a presa da origem. */
        PASTE_SELECTED,
        /** Devolve os nós do último colar ao que eram. */
        UNDO
    }

    /** Sem marcados além dos nós. */
    public TabletProfilePayload(int containerId, Action action, Optional<NodeKey> key, List<NodeKey> keys) {
        this(containerId, action, key, keys, TabletMarks.EMPTY);
    }

    public static final Type<TabletProfilePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletProfilePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletProfilePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Action.class), TabletProfilePayload::action,
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()), TabletProfilePayload::key,
            NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list(MAX_KEYS)),
            TabletProfilePayload::keys,
            TabletMarks.STREAM_CODEC, TabletProfilePayload::marks,
            TabletProfilePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
