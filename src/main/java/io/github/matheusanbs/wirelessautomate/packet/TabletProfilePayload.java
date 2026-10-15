package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: o perfil do Tablet. Copiar o perfil de um nó, esquecê-lo, colá-lo num nó, no grupo
 * de uma máquina ou nos marcados, e desfazer o último colar. Cada ação só faz o que o campo dela pede e o
 * servidor confere tudo ({@code TabletMenu#profile}).
 *
 * @param key     COPY e PASTE_ONE: o nó
 * @param machine PASTE_GROUP: o bloco da máquina; o servidor reavalia a consulta atual do jogador
 * @param keys    PASTE_SELECTED: os nós marcados, no máximo {@link #MAX_KEYS}
 */
public record TabletProfilePayload(int containerId, Action action, Optional<NodeKey> key,
        Optional<ResourceLocation> machine, List<NodeKey> keys) implements CustomPacketPayload {
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
        /** Cola em todos os nós da máquina {@code machine} que casam com a consulta atual, menos a origem. */
        PASTE_GROUP,
        /** Cola nos nós {@code keys}, menos a origem. */
        PASTE_SELECTED,
        /** Devolve os nós do último colar ao que eram. */
        UNDO
    }

    public static final Type<TabletProfilePayload> TYPE = new Type<>(WirelessAutomate.id("tablet_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletProfilePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletProfilePayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Action.class), TabletProfilePayload::action,
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()), TabletProfilePayload::key,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), TabletProfilePayload::machine,
            NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast().apply(ByteBufCodecs.list(MAX_KEYS)),
            TabletProfilePayload::keys,
            TabletProfilePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
