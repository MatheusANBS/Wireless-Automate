package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvAccessActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O que as abas Tamanho e Acesso mandam ao servidor: o tamanho e as ações da lista de acesso. A tela não muda o bloco
 * nem a lista sozinha; o servidor valida, o bloco volta pelo update tag e a lista pelo {@code TvAccessPayload}. Na
 * prévia de desenvolvimento ({@code preview}), nada sai. O salvar do editor sai pelo {@code TvEditorActions}.
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

    /** Uma ação da lista de acesso: pelo id da sugestão ou, sem id, pelo texto digitado. */
    void access(TvAccessActionPayload.Action action, @Nullable UUID id, String text) {
        if (!preview) {
            PacketDistributor.sendToServer(new TvAccessActionPayload(containerId, action, Optional.ofNullable(id),
                    text));
        }
    }
}
