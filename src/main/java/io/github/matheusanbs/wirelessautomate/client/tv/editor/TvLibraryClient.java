package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;

/**
 * A biblioteca do jogador no cliente: o último {@link TvLibraryPayload}, que o servidor manda só com o editor aberto
 * (o pedido sai ao abrir a aba Editar). Some ao fechar o editor, para não guardar memória à toa. Os componentes com layout salvo ({@link TvComponent#isLayout}) ficam à parte, em {@link #layouts}. A {@link #version}
 * sobe a cada mudança, e as partes do editor refazem a lista só quando ela muda.
 */
public final class TvLibraryClient {
    private static List<TvComponent> components = List.of();
    private static List<TvComponent> layouts = List.of();
    private static List<TvTheme> themes = List.of();
    private static List<TvPixelArt> arts = List.of();
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
        version++;
    }

    /** Solta a biblioteca (o editor fechou ou o jogador saiu do mundo). */
    public static void clear() {
        if (!components.isEmpty() || !layouts.isEmpty() || !themes.isEmpty() || !arts.isEmpty()) {
            components = List.of();
            layouts = List.of();
            themes = List.of();
            arts = List.of();
        }
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

    /** Sobe a cada payload recebido e a cada {@link #clear}; 0 antes do primeiro. */
    public static int version() {
        return version;
    }
}
