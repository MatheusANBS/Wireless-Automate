package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerPlan;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Servidor → cliente, só ao jogador que clicou, uma vez por análise: a proposta do Afinador do Configurador para o
 * cliente pintar nas faces da máquina. Lista vazia apaga a proposta mostrada (aplicada, clique no ar, nada achado, a
 * análise falhou).
 *
 * @param machine a máquina, na dimensão do jogador
 * @param faces   as faces com modo (Insere, Extrai ou Armazém; o Nenhum não vai), no máximo {@link #MAX_FACES}
 * @param ticks   por quantos ticks a proposta vale a partir da chegada
 */
public record ConfiguratorProposalPayload(BlockPos machine, List<Face> faces, int ticks) implements CustomPacketPayload {
    /** Cada face com cada tipo, uma vez. */
    public static final int MAX_FACES = TunerPlan.FACES * ResourceType.values().length;

    public static final Type<ConfiguratorProposalPayload> TYPE = new Type<>(WirelessAutomate.id("configurator_proposal"));

    /** O modo proposto para um tipo numa face absoluta ({@code Direction.ordinal()}). */
    public record Face(int face, ResourceType type, PortMode mode) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Face> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Face::face,
                NeoForgeStreamCodecs.enumCodec(ResourceType.class), Face::type,
                NeoForgeStreamCodecs.enumCodec(PortMode.class), Face::mode,
                Face::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorProposalPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConfiguratorProposalPayload::machine,
                    Face.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FACES)), ConfiguratorProposalPayload::faces,
                    ByteBufCodecs.VAR_INT, ConfiguratorProposalPayload::ticks,
                    ConfiguratorProposalPayload::new);

    /** Apaga a proposta mostrada. */
    public static ConfiguratorProposalPayload clear(BlockPos machine) {
        return new ConfiguratorProposalPayload(machine, List.of(), 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
