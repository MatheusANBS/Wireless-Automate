package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import net.minecraft.network.chat.Component;

/** Os textos da aba Redes: as chaves {@code gui.wirelessautomate.tablet.*} e o plural. */
final class NetworksText {
    private NetworksText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.tablet." + key, args);
    }

    /** {@code key} com o número, ou {@code key.one} quando é 1. */
    static Component plural(String key, long count) {
        return tr(count == 1 ? key + ".one" : key, count);
    }
}
