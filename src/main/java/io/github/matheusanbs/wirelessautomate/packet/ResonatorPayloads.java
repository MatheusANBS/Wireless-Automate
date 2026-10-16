package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Pacotes da tela do Núcleo de Ressonância e os handlers, registrados por {@link ModPayloads#register}. Código
 * comum: o handler do cliente só mexe no {@link ResonatorMenu}. Os do servidor só agem com o menu daquele
 * {@code containerId} aberto, ainda válido (Núcleo no lugar, a até cerca de 8 blocos, montado e com as mesmas
 * câmaras) e validam o valor.
 */
public final class ResonatorPayloads {
    private ResonatorPayloads() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ResonatorStatePayload.TYPE, ResonatorStatePayload.STREAM_CODEC, ResonatorMenu::onState);
        registrar.playToServer(ResonatorDialPayload.TYPE, ResonatorDialPayload.STREAM_CODEC,
                (payload, context) -> handleDial(serverPlayer(context), payload));
        registrar.playToServer(ResonatorModePayload.TYPE, ResonatorModePayload.STREAM_CODEC,
                (payload, context) -> handleMode(serverPlayer(context), payload));
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    /** Muda o dial do Núcleo da tela aberta; recusado no modo automático (o Sintonizador é quem anda o dial). */
    public static boolean handleDial(@Nullable ServerPlayer player, ResonatorDialPayload payload) {
        ResonanceCoreBlockEntity core = core(player, payload.containerId());
        if (core == null || core.auto() || !Double.isFinite(payload.mhz())) {
            return false;
        }
        core.setDial(payload.mhz());
        return true;
    }

    /** Liga ou desliga o modo automático do Núcleo da tela aberta; ligar sem Sintonizador é recusado. */
    public static boolean handleMode(@Nullable ServerPlayer player, ResonatorModePayload payload) {
        ResonanceCoreBlockEntity core = core(player, payload.containerId());
        return core != null && core.setAuto(payload.auto());
    }

    /** O Núcleo do {@link ResonatorMenu} aberto com esse {@code containerId}, se ainda vale e está montado. */
    private static @Nullable ResonanceCoreBlockEntity core(@Nullable ServerPlayer player, int containerId) {
        if (player == null || !(player.containerMenu instanceof ResonatorMenu menu)
                || menu.containerId != containerId || !menu.stillValid(player)) {
            return null;
        }
        ResonanceCoreBlockEntity core = menu.core();
        return core != null && core.formed() ? core : null;
    }
}
