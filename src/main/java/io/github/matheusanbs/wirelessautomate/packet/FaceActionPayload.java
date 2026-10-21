package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: uma ação do quadro das faces sobre o alvo de edição do painel. {@link Action#CLEAR} põe as
 * faces do tipo (ou, com {@code allTypes}, de todos) em Nenhum; {@link Action#PASTE} copia a face {@code from} para a
 * {@code to} no mesmo tipo. As faces vêm no quadro da tela, como nos outros payloads de face.
 */
public record FaceActionPayload(int containerId, Action action, ResourceType resource, Direction from, Direction to,
        boolean allTypes) implements CustomPacketPayload {
    public enum Action { CLEAR, PASTE }

    public static final Type<FaceActionPayload> TYPE = new Type<>(WirelessAutomate.id("face_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FaceActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FaceActionPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Action.class), FaceActionPayload::action,
            NeoForgeStreamCodecs.enumCodec(ResourceType.class), FaceActionPayload::resource,
            Direction.STREAM_CODEC, FaceActionPayload::from,
            Direction.STREAM_CODEC, FaceActionPayload::to,
            ByteBufCodecs.BOOL, FaceActionPayload::allTypes,
            FaceActionPayload::new);

    public static FaceActionPayload clear(int containerId, ResourceType resource, boolean allTypes) {
        return new FaceActionPayload(containerId, Action.CLEAR, resource, Direction.UP, Direction.UP, allTypes);
    }

    public static FaceActionPayload paste(int containerId, ResourceType resource, Direction from, Direction to) {
        return new FaceActionPayload(containerId, Action.PASTE, resource, from, to, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
