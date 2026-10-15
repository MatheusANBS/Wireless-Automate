package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorDialPayload;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorModePayload;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que a tela do Núcleo manda ao servidor. Nada muda aqui: a tela espera o estado seguinte, que o servidor
 * manda depois de validar. Sem conexão (a captura de desenvolvimento), nada sai.
 */
final class ResonatorScreenActions {
    private final ResonatorMenu menu;

    ResonatorScreenActions(ResonatorMenu menu) {
        this.menu = menu;
    }

    void setDial(double mhz) {
        send(new ResonatorDialPayload(menu.containerId, Tuning.clampDial(mhz)));
    }

    void setAuto(boolean auto) {
        send(new ResonatorModePayload(menu.containerId, auto));
    }

    private static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
