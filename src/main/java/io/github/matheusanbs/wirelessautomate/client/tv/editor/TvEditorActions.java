package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.TvPreviewValues;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsRequestPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que o editor manda ao servidor: salvar o rascunho inteiro pelo {@link TvLayoutPayload} (o tamanho é
 * conferido antes: acima do teto do servidor, o {@code maxLayoutBytes}, nada sai e o editor avisa) e as ações na biblioteca
 * pelo {@link TvLibraryActionPayload} (pedir, salvar, renomear e remover componentes, temas e pixel arts; a resposta
 * volta pelo {@link TvLibraryClient}). Leva também os tetos do servidor que vieram na abertura (artes na biblioteca e
 * pixels nas imagens da Tela). Na prévia de desenvolvimento ({@code preview}), nada sai e o rascunho vira a base na hora.
 */
public final class TvEditorActions {
    /** O resultado de pedir para salvar. */
    enum Saved { SENT, LOCAL, TOO_BIG }

    private final int containerId;
    private final boolean preview;
    private final int maxLayoutBytes;
    private final int maxArts;
    private final int maxImagePixels;

    public TvEditorActions(int containerId, boolean preview, int maxLayoutBytes, int maxArts, int maxImagePixels) {
        this.containerId = containerId;
        this.preview = preview;
        this.maxLayoutBytes = Math.min(maxLayoutBytes, TvLayoutPayload.MAX_BYTES);
        this.maxArts = maxArts;
        this.maxImagePixels = maxImagePixels;
    }

    int containerId() {
        return containerId;
    }

    /** O teto de bytes do layout salvo: o do servidor, que veio na abertura, e nunca mais que o do pacote. */
    int maxLayoutBytes() {
        return maxLayoutBytes;
    }

    /** O teto de pixel arts na biblioteca do jogador ({@code tv.maxArts} do servidor). */
    int maxArts() {
        return maxArts;
    }

    /** O teto de pixels somados nas imagens de uma Tela ({@code tv.maxImagePixels} do servidor). */
    int maxImagePixels() {
        return maxImagePixels;
    }

    Saved save(TvLayout layout) {
        if (TvLayoutPayload.encodedSize(layout) > maxLayoutBytes) {
            return Saved.TOO_BIG;
        }
        if (preview) {
            return Saved.LOCAL;
        }
        PacketDistributor.sendToServer(new TvLayoutPayload(containerId, layout));
        return Saved.SENT;
    }

    /** Pede os números das ligações do rascunho (só manda se a lista mudou); nada sai na prévia de desenvolvimento. */
    void previewBindings(List<TvBinding> bindings) {
        if (!preview) {
            TvPreviewValues.request(containerId, bindings);
        }
    }

    /** O editor fechou: esquece a prévia (o servidor para sozinho quando o menu fecha). */
    void stopPreview() {
        TvPreviewValues.stop();
    }

    /** Pede a biblioteca do jogador; o servidor responde com o {@code TvLibraryPayload}. */
    void requestLibrary() {
        send(TvLibraryActionPayload.open(containerId));
    }

    /** Pede os itens de um Baú ligado à Tela, para a grade da aba Dado; a resposta volta pelo {@link TvStorageClient}. */
    void requestStorageContents(GlobalPos pos, String query) {
        if (!preview) {
            PacketDistributor.sendToServer(new TvStorageContentsRequestPayload(containerId, pos, query));
        }
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

    /** Salva a pixel art na biblioteca: cria ou troca a de mesmo id. */
    void saveArt(TvPixelArt art) {
        send(TvLibraryActionPayload.saveArt(containerId, art));
    }

    private void send(TvLibraryActionPayload payload) {
        if (!preview) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
