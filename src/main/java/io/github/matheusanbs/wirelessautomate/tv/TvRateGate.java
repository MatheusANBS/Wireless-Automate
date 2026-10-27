package io.github.matheusanbs.wirelessautomate.tv;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O limite de ações do editor da Tela de Sinal por jogador: no máximo {@link #PER_SECOND} (ou o limite dado) por segundo de jogo (20
 * ticks), contadas numa janela que recomeça quando o segundo passa. Lógica pura, testada por JUnit; só guarda a
 * janela de quem agiu, que {@link #forget} solta ao sair.
 */
public final class TvRateGate {
    public static final int PER_SECOND = 10;
    private static final long WINDOW_TICKS = 20;

    private final int limit;

    private final Map<UUID, long[]> windows = new HashMap<>();

    public TvRateGate() {
        this(PER_SECOND);
    }

    /** Um limite próprio, de {@code perSecond} ações por segundo. */
    public TvRateGate(int perSecond) {
        this.limit = perSecond;
    }

    /** Conta uma ação do jogador em {@code gameTime}; falso se a janela atual já está cheia. */
    public boolean allow(UUID player, long gameTime) {
        long[] window = windows.computeIfAbsent(player, id -> new long[] {gameTime, 0});
        if (gameTime < window[0] || gameTime - window[0] >= WINDOW_TICKS) {
            window[0] = gameTime;
            window[1] = 0;
        }
        if (window[1] >= limit) {
            return false;
        }
        window[1]++;
        return true;
    }

    public void forget(UUID player) {
        windows.remove(player);
    }

    public void clear() {
        windows.clear();
    }
}
