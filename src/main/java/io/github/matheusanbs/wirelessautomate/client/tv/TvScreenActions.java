package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O que a aba Tamanho manda ao servidor: o tamanho. A tela não muda o bloco sozinha; o servidor valida e o bloco
 * volta pelo update tag. Na prévia de desenvolvimento ({@code preview}), nada sai. O salvar do editor sai pelo
 * {@code TvEditorActions}.
 */
final class TvScreenActions {
    private final int containerId;
    private final boolean preview;

    TvScreenActions(int containerId, boolean preview) {
        this.containerId = containerId;
        this.preview = preview;
    }

    void size(TvArea area) {
        if (!preview) {
            PacketDistributor.sendToServer(new TvSizePayload(containerId, area.width(), area.height(),
                    area.anchorCol(), area.anchorRow()));
        }
    }
}
