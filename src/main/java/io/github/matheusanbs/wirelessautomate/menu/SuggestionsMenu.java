package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Um menu com campos de acesso que recebe as sugestões do servidor ({@link NetworkSuggestionsPayload}): o Tablet (o
 * acesso a uma rede) e a Tela de Sinal (o acesso à Tela). O handler do cliente entrega a resposta ao menu aberto.
 */
public interface SuggestionsMenu {
    /** Cliente: guarda a resposta nova e sobe a versão. */
    void applySuggestions(NetworkSuggestionsPayload payload);

    /** Cliente: as últimas sugestões recebidas, ou {@code null}. */
    @Nullable NetworkSuggestionsPayload suggestions();

    /** Cliente: muda a cada resposta, para o campo saber que há uma nova. */
    int suggestionsVersion();
}
