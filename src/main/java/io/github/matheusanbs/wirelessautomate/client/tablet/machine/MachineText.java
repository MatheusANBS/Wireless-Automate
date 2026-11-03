package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import net.minecraft.network.chat.Component;

/** As chaves de texto da aba Máquina: {@code gui.wirelessautomate.tablet.machine.*}. */
final class MachineText {
    private MachineText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.tablet.machine." + key, args);
    }
}
