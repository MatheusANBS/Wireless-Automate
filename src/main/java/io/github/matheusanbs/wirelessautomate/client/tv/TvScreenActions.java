package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import io.github.matheusanbs.wirelessautomate.packet.TvTemplatePayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que a tela da Tela de Sinal manda ao servidor: o tamanho e o modelo. A tela não muda o bloco sozinha; o
 * servidor valida e o bloco volta pelo update tag. Na prévia de desenvolvimento ({@code preview}), nada sai.
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

    /** O Painel de rede com a rede e o tipo escolhidos. */
    void template(UUID network, ResourceType type) {
        if (!preview) {
            PacketDistributor.sendToServer(new TvTemplatePayload(containerId, Optional.of(network), type.key()));
        }
    }

    /** Sem rede: o servidor limpa o layout. */
    void clear() {
        if (!preview) {
            PacketDistributor.sendToServer(new TvTemplatePayload(containerId, Optional.empty(), ""));
        }
    }
}
