package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.RateLimiter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * O que o Tablet guarda por jogador e que sobrevive a fechar e abrir o menu: onde ele deixou a tela
 * ({@link TabletMenu.State}) e as fichas das remontagens forçadas e das ações em massa do perfil. Só na
 * memória do servidor, some quando ele para.
 */
final class TabletPlayers {
    /** Fichas de remontagem forçada por jogador. */
    private static final Map<UUID, RateLimiter> FORCED_LIMITS = new HashMap<>();
    /** Fichas das ações em massa do perfil por jogador. */
    private static final Map<UUID, RateLimiter> BULK_LIMITS = new HashMap<>();
    /** Fichas das consultas de sugestão do campo de acesso por jogador. */
    private static final Map<UUID, RateLimiter> SUGGEST_LIMITS = new HashMap<>();
    /** Onde cada jogador deixou o Tablet (ao fechar o menu, inclusive para abrir o filtro de um nó): o Tablet reabre igual. */
    private static final Map<UUID, TabletMenu.State> STATES = new HashMap<>();

    private TabletPlayers() {
    }

    static void reset() {
        STATES.clear();
        FORCED_LIMITS.clear();
        BULK_LIMITS.clear();
        SUGGEST_LIMITS.clear();
    }

    static void forget(UUID player) {
        STATES.remove(player);
        FORCED_LIMITS.remove(player);
        BULK_LIMITS.remove(player);
        SUGGEST_LIMITS.remove(player);
    }

    static @Nullable TabletMenu.State saved(UUID player) {
        return STATES.get(player);
    }

    static void save(UUID player, TabletMenu.State state) {
        STATES.put(player, state);
    }

    /** Gasta uma remontagem forçada do jogador, se houver. */
    static boolean takeForced(UUID player, long now) {
        return take(FORCED_LIMITS.computeIfAbsent(player, id -> new RateLimiter(TabletMenu.FORCED_PER_SECOND)), now);
    }

    /** Gasta uma ação em massa do perfil do jogador, se houver. */
    static boolean takeBulk(UUID player, long now) {
        return take(BULK_LIMITS.computeIfAbsent(player, id -> new RateLimiter(TabletMenu.BULK_PER_SECOND)), now);
    }

    /** Gasta uma consulta de sugestão do jogador, se houver (no máximo {@link TabletMenu#SUGGEST_PER_SECOND} por segundo). */
    static boolean takeSuggest(UUID player, long now) {
        return take(SUGGEST_LIMITS.computeIfAbsent(player, id -> new RateLimiter(TabletMenu.SUGGEST_PER_SECOND)), now);
    }

    private static boolean take(RateLimiter limiter, long now) {
        if (limiter.available(now) < 1) {
            return false;
        }
        limiter.consume(1);
        return true;
    }
}
