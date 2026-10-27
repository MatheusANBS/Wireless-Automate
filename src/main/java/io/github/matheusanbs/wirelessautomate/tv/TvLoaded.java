package io.github.matheusanbs.wirelessautomate.tv;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.world.level.Level;

/**
 * As Telas de Sinal carregadas, uma lista por lado (servidor e cliente), por identidade. Entram no {@code onLoad}
 * e saem ao serem removidas ou ao descarregar o chunk; cada lado só é tocado pela própria thread. Código comum,
 * sem classes do cliente.
 */
public final class TvLoaded {
    private static final Set<TvBlockEntity> SERVER = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<TvBlockEntity> CLIENT = Collections.newSetFromMap(new IdentityHashMap<>());

    private TvLoaded() {
    }

    /** O conjunto do lado do {@code level}. */
    public static Set<TvBlockEntity> of(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    public static void add(TvBlockEntity tv) {
        if (tv.getLevel() != null) {
            of(tv.getLevel()).add(tv);
        }
    }

    public static void remove(TvBlockEntity tv) {
        if (tv.getLevel() != null) {
            of(tv.getLevel()).remove(tv);
        }
    }

    /** Na parada do servidor. */
    public static void clearServer() {
        SERVER.clear();
    }

    /** Ao sair do mundo, no cliente. */
    public static void clearClient() {
        CLIENT.clear();
    }
}
