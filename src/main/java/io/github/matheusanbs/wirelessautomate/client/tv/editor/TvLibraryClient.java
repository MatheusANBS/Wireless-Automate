package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;

/**
 * A biblioteca do jogador no cliente: o último {@link TvLibraryPayload}, que o servidor manda só com o editor aberto
 * (o pedido sai ao abrir a aba Editar). Some ao fechar o editor, para não guardar memória à toa. A {@link #version}
 * sobe a cada mudança, e as partes do editor refazem a lista só quando ela muda.
 */
public final class TvLibraryClient {
    private static List<TvComponent> components = List.of();
    private static List<TvTheme> themes = List.of();
    private static int version;

    private TvLibraryClient() {
    }

    /** O gancho do payload (preenchido no {@code ClientSetup}). */
    public static void receive(TvLibraryPayload payload) {
        components = payload.components();
        themes = payload.themes();
        version++;
    }

    /** Solta a biblioteca (o editor fechou ou o jogador saiu do mundo). */
    public static void clear() {
        if (!components.isEmpty() || !themes.isEmpty()) {
            components = List.of();
            themes = List.of();
        }
        version++;
    }

    public static List<TvComponent> components() {
        return components;
    }

    public static List<TvTheme> themes() {
        return themes;
    }

    /** Sobe a cada payload recebido e a cada {@link #clear}; 0 antes do primeiro. */
    public static int version() {
        return version;
    }
}
