package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.packet.LinkerOpenListPayload;

/**
 * O handler do {@link LinkerOpenListPayload}: abre a {@link LinkerOpenScreen}. Fica em {@code client/} e só o
 * lambda do registro o chama (no cliente), então o servidor dedicado nunca carrega esta classe nem a tela.
 */
public final class LinkerOpenClient {
    private LinkerOpenClient() {
    }

    public static void show(LinkerOpenListPayload payload) {
        LinkerOpenScreen.show(payload.machine(), payload.choices());
    }
}
