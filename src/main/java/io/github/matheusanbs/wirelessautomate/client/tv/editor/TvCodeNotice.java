package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * O aviso curto do copiar e colar código ({@link TvLayoutCode}, {@link TvArtCode}), que a barra da base
 * ({@link TvBaseLine}) mostra por uns segundos no lugar do tamanho da Tela: {@link #SHOW_MS}, ou
 * {@link #ERROR_MS} num erro, que pode trazer a linha do código legível. Um só de cada vez: o mais novo vale.
 */
public final class TvCodeNotice {
    /** O tipo do aviso; o texto padrão de cada um, na ordem dos {@code lang}. */
    public enum Kind {
        COPIED("code.copied"), INVALID("code.invalid"), TOO_BIG("code.too_big"), LIBRARY_FULL("code.library_full"),
        MISSING_ART("code.missing_art"), BINDINGS_DROPPED("code.bindings_dropped");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    static final long SHOW_MS = 4000;
    static final long ERROR_MS = 8000;
    private static @Nullable Kind kind;
    private static Component text = Component.empty();
    private static long shownAt;
    private static int version;

    private TvCodeNotice() {
    }

    static void show(Kind what, Object... args) {
        show(what, what.key, args);
    }

    /** O erro de um código: o texto que ele trouxe ({@code code.*}), com a linha quando há. */
    static void failed(TvCodes.Read<?> read) {
        show(Kind.INVALID, read.error() == null ? Kind.INVALID.key : read.error(), read.args());
    }

    private static void show(Kind what, String key, Object... args) {
        kind = what;
        text = TvEditorText.key(key, args);
        shownAt = System.currentTimeMillis();
        version++;
    }

    /** O aviso à vista, ou {@code null} quando não há nenhum ou o último já passou. */
    public static @Nullable Kind current() {
        if (kind != null && System.currentTimeMillis() - shownAt > (kind == Kind.COPIED ? SHOW_MS : ERROR_MS)) {
            kind = null;
        }
        return kind;
    }

    static Component text() {
        return text;
    }

    /** Muda a cada aviso novo, para a barra medir o texto de novo só então. */
    static int version() {
        return version;
    }
}
