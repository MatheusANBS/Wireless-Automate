package io.github.matheusanbs.wirelessautomate.client.access;

import io.github.matheusanbs.wirelessautomate.menu.SuggestionsMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * O que o {@link AccessPanel} precisa de quem o usa (a aba Redes do Tablet, a aba Acesso da Tela de Sinal): a lista de
 * acesso a mostrar, o menu que recebe as sugestões e as ações, que a anfitriã manda ao servidor.
 */
public interface AccessHost {
    /** A lista de acesso a mostrar, ou {@code null} quando não há o que mostrar (nenhuma rede escolhida). */
    @Nullable AccessView access();

    /** O menu aberto, que guarda as sugestões que o servidor mandou. */
    SuggestionsMenu menu();

    /** Convida ({@code add}) ou tira um jogador: pelo id da sugestão ou, sem id, pelo nome digitado. */
    void member(@Nullable UUID id, String text, boolean add);

    /** Adiciona ou tira um time do FTB Teams, do mesmo jeito. */
    void team(@Nullable UUID id, String text, boolean add);

    /** Pede as sugestões do campo de jogador ou de time para o texto digitado. */
    void suggest(NetworkSuggestPayload.Kind kind, String text);
}
