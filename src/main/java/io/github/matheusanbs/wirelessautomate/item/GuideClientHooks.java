package io.github.matheusanbs.wirelessautomate.item;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** Ganchos que o código de {@code client/guide} preenche; o código comum não referencia nada do cliente. */
public final class GuideClientHooks {
    /**
     * Chamado no cliente para abrir o livro-guia: o nome da página (como {@code router.md}) ou {@code null} para
     * abrir onde o jogador parou.
     */
    public static volatile Consumer<@Nullable String> open = page -> {
    };

    private GuideClientHooks() {
    }
}
