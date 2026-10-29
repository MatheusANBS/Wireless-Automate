package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.tv.TvPreviewValues;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageUploader;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvArtCache;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsRequestPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tudo o que o editor manda ao servidor: salvar o rascunho inteiro pelo {@link TvLayoutPayload} (o tamanho é
 * conferido antes: acima do teto do servidor, o {@code maxLayoutBytes} do layout sem os pixels, ou do teto do pacote
 * com eles, nada sai e o editor avisa) e as ações na biblioteca
 * pelo {@link TvLibraryActionPayload} (pedir, salvar, renomear e remover componentes, temas e pixel arts; a resposta
 * volta pelo {@link TvLibraryClient}), além do envio de imagens pelo {@link TvImageUploader} e de renomear e remover
 * as da biblioteca. Leva também os tetos do servidor que vieram na abertura (artes na biblioteca, pixels nas imagens
 * da Tela e as regras das imagens enviadas, {@link TvMenu.ImageRules}). Na prévia de desenvolvimento
 * ({@code preview}), nada sai e o rascunho vira a base na hora.
 */
public final class TvEditorActions {
    /** O resultado de pedir para salvar. */
    enum Saved { SENT, LOCAL, TOO_BIG }

    private final int containerId;
    private final boolean preview;
    private final int maxLayoutBytes;
    private final int maxArts;
    private final int maxImagePixels;
    private final TvMenu.ImageRules images;

    public TvEditorActions(int containerId, boolean preview, int maxLayoutBytes, int maxArts, int maxImagePixels,
            TvMenu.ImageRules images) {
        this.containerId = containerId;
        this.preview = preview;
        this.maxLayoutBytes = maxLayoutBytes;
        this.maxArts = maxArts;
        this.maxImagePixels = maxImagePixels;
        this.images = images;
        TvImageOps.pictureLimit = images.maxPerScreen();
    }

    /** As regras das imagens enviadas neste servidor: ligadas, se este jogador envia e os tetos. */
    TvMenu.ImageRules images() {
        return images;
    }

    int containerId() {
        return containerId;
    }

    /** O teto de bytes do layout como vai ao cliente, sem os pixels das artes: o do servidor, que veio na abertura. */
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

    /**
     * Salva o rascunho sem as pixel arts que nenhuma peça nem o fundo apontam ({@link TvLayout#withoutUnusedImages}),
     * para uma cópia solta não pesar nos tetos. A peça cuja arte o cliente ainda não tem vai assim mesmo: o servidor a
     * completa com a cópia da Tela. As artes mandadas ficam no {@link TvArtCache}, para o update tag da resposta, que
     * as traz só como referência, já as achar.
     */
    Saved save(TvLayout draft) {
        TvLayout layout = draft.withoutUnusedImages();
        if (TvLayoutPayload.clientSize(layout) > maxLayoutBytes
                || TvLayoutPayload.encodedSize(layout) > TvLayoutPayload.MAX_BYTES) {
            return Saved.TOO_BIG;
        }
        if (preview) {
            return Saved.LOCAL;
        }
        TvArtCache.offer(layout.images());
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

    /** Na prévia de desenvolvimento nada vai ao servidor, e nenhuma resposta vem. */
    boolean preview() {
        return preview;
    }

    /** Remove a pixel art da biblioteca (as Telas que a usam guardam a cópia delas). */
    void removeArt(UUID id) {
        send(TvLibraryActionPayload.removeArt(containerId, id));
    }

    /**
     * Envia a imagem ao servidor com o nome que ela terá na biblioteca; {@code done} roda com o resultado. A
     * biblioteca nova vem do servidor depois do aceite.
     */
    void uploadPicture(byte[] data, String name, Consumer<TvImageUploader.Outcome> done) {
        if (!preview) {
            TvImageUploader.upload(data, name, done);
        }
    }

    void renamePicture(String hash, String newName) {
        send(TvLibraryActionPayload.renamePicture(containerId, hash, newName));
    }

    /** Tira a imagem da biblioteca; ela só sai do mundo quando nada mais a usa. */
    void removePicture(String hash) {
        send(TvLibraryActionPayload.removePicture(containerId, hash));
    }

    private void send(TvLibraryActionPayload payload) {
        if (!preview) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
