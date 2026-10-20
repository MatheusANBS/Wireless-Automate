package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Cliente → servidor: troca a Lista do Tablet aberto entre as visões por roteadores e por máquinas, ou abre, recolhe,
 * mostra todas ou poucas máquinas de um bloco ({@code block}, só nas ações de bloco) na visão por máquinas. O servidor
 * valida no {@code TabletMachines} e refaz só a página desse jogador.
 */
public record TabletMachinesPayload(int containerId, Action action, Optional<ResourceLocation> block)
        implements CustomPacketPayload {
    public static final Type<TabletMachinesPayload> TYPE = new Type<>(WirelessAutomate.id("tablet_machines"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletMachinesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TabletMachinesPayload::containerId,
            NeoForgeStreamCodecs.enumCodec(Action.class), TabletMachinesPayload::action,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), TabletMachinesPayload::block,
            TabletMachinesPayload::new);

    /** O que muda na visão. */
    public enum Action {
        ROUTERS,
        MACHINES,
        OPEN_BLOCK,
        CLOSE_BLOCK,
        SHOW_ALL,
        /** Volta o bloco a mostrar poucas máquinas. */
        SHOW_FEW
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
