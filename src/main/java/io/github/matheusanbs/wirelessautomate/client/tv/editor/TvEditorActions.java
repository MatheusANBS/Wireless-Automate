package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que o editor manda ao servidor: por enquanto, salvar o rascunho inteiro pelo {@link TvLayoutPayload}. O
 * tamanho é conferido antes: acima de {@link TvLayoutPayload#MAX_BYTES} nada sai e o editor avisa. Na prévia de
 * desenvolvimento ({@code preview}), nada sai e o rascunho vira a base na hora.
 */
public final class TvEditorActions {
    /** O resultado de pedir para salvar. */
    enum Saved { SENT, LOCAL, TOO_BIG }

    private final int containerId;
    private final boolean preview;

    public TvEditorActions(int containerId, boolean preview) {
        this.containerId = containerId;
        this.preview = preview;
    }

    Saved save(TvLayout layout) {
        if (TvLayoutPayload.encodedSize(layout) > TvLayoutPayload.MAX_BYTES) {
            return Saved.TOO_BIG;
        }
        if (preview) {
            return Saved.LOCAL;
        }
        PacketDistributor.sendToServer(new TvLayoutPayload(containerId, layout));
        return Saved.SENT;
    }
}
