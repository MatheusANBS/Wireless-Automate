package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A biblioteca do jogador no cliente: o último {@link TvLibraryPayload}, que o servidor manda só com o editor aberto
 * (o pedido sai ao abrir a aba Editar). Some ao fechar o editor, para não guardar memória à toa. Os componentes com
 * layout salvo ({@link TvComponent#isLayout}) ficam à parte, em {@link #layouts}, e as imagens enviadas em
 * {@link #pictures}. A {@link #version} sobe a cada mudança, e as partes do editor refazem a lista só quando ela muda.
 */
public final class TvLibraryClient {
    private static List<TvComponent> components = List.of();
    private static List<TvComponent> layouts = List.of();
    private static List<TvTheme> themes = List.of();
    private static List<TvPixelArt> arts = List.of();
    private static List<TvLibraryPicture> pictures = List.of();
    private static int version;

    private TvLibraryClient() {
    }

    /** O gancho do payload (preenchido no {@code ClientSetup}); uma resposta atrasada, sem o editor aberto, é ignorada. */
    public static void receive(TvLibraryPayload payload) {
        if (!TvEditorAck.isOpen(payload.containerId())) {
            return;
        }
        components = payload.components().stream().filter(c -> !c.isLayout()).toList();
        layouts = payload.components().stream().filter(TvComponent::isLayout).toList();
        themes = payload.themes();
        arts = payload.arts();
        pictures = payload.pictures();
        version++;
    }

    /** Solta a biblioteca (o editor fechou ou o jogador saiu do mundo). */
    public static void clear() {
        components = List.of();
        layouts = List.of();
        themes = List.of();
        arts = List.of();
        pictures = List.of();
        version++;
    }

    public static List<TvComponent> components() {
        return components;
    }

    /** Os layouts salvos do jogador (componentes com layout), na ordem de criação. */
    public static List<TvComponent> layouts() {
        return layouts;
    }

    public static List<TvTheme> themes() {
        return themes;
    }

    /** As pixel arts do jogador, na ordem de criação. */
    public static List<TvPixelArt> arts() {
        return arts;
    }

    /** As imagens enviadas do jogador (o que o armazém sabe de cada uma e o nome), na ordem de chegada. */
    public static List<TvLibraryPicture> pictures() {
        return pictures;
    }

    /** A imagem da biblioteca com o id da peça ({@code TvPicture#id}), ou {@code null}. */
    public static @Nullable TvPicture picture(UUID id) {
        for (TvLibraryPicture entry : pictures) {
            if (entry.picture().id().equals(id)) {
                return entry.picture();
            }
        }
        return null;
    }

    /** Sobe a cada payload recebido e a cada {@link #clear}; 0 antes do primeiro. */
    public static int version() {
        return version;
    }
}
