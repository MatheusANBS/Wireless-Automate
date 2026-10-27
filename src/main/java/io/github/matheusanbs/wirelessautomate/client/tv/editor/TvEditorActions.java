package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que o editor manda ao servidor: salvar o rascunho inteiro pelo {@link TvLayoutPayload} (o tamanho é
 * conferido antes: acima de {@link TvLayoutPayload#MAX_BYTES} nada sai e o editor avisa) e as ações na biblioteca
 * pelo {@link TvLibraryActionPayload} (pedir, salvar, renomear e remover componentes e temas; a resposta volta pelo
 * {@link TvLibraryClient}). Na prévia de desenvolvimento ({@code preview}), nada sai e o rascunho vira a base na hora.
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

    /** Pede a biblioteca do jogador; o servidor responde com o {@code TvLibraryPayload}. */
    void requestLibrary() {
        send(TvLibraryActionPayload.open(containerId));
    }

    void saveComponent(TvComponent component) {
        send(TvLibraryActionPayload.saveComponent(containerId, component));
    }

    void saveTheme(TvTheme theme) {
        send(TvLibraryActionPayload.saveTheme(containerId, theme));
    }

    void renameComponent(String name, String newName) {
        send(TvLibraryActionPayload.rename(containerId, TvLibraryActionPayload.Kind.COMPONENT, name, newName));
    }

    void removeComponent(String name) {
        send(TvLibraryActionPayload.remove(containerId, TvLibraryActionPayload.Kind.COMPONENT, name));
    }

    void removeTheme(String name) {
        send(TvLibraryActionPayload.remove(containerId, TvLibraryActionPayload.Kind.THEME, name));
    }

    private void send(TvLibraryActionPayload payload) {
        if (!preview) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
