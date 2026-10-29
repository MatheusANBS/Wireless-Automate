package io.github.matheusanbs.wirelessautomate.tv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.Level;

/**
 * As Telas de Sinal carregadas, uma lista por lado (servidor e cliente), por identidade. Entram no {@code onLoad}
 * e saem ao serem removidas ou ao descarregar o chunk; cada lado só é tocado pela própria thread. Código comum,
 * sem classes do cliente.
 */
public final class TvLoaded {
    private static final int PHASES = 20;
    private static final Set<TvBlockEntity> SERVER = Collections.newSetFromMap(new IdentityHashMap<>());
    /** As do servidor também em 20 baldes pela posição, para o tick de cada Tela cair numa fase. */
    private static final List<List<TvBlockEntity>> BUCKETS = buckets();
    private static final Set<TvBlockEntity> CLIENT = Collections.newSetFromMap(new IdentityHashMap<>());

    private TvLoaded() {
    }

    private static List<List<TvBlockEntity>> buckets() {
        List<List<TvBlockEntity>> list = new ArrayList<>(PHASES);
        for (int i = 0; i < PHASES; i++) {
            list.add(new ArrayList<>());
        }
        return list;
    }

    private static int phase(TvBlockEntity tv) {
        return Math.floorMod(tv.getBlockPos().hashCode(), PHASES);
    }

    /** Quantas Telas do servidor estão carregadas. */
    public static int serverCount() {
        return SERVER.size();
    }

    /** Todas as Telas carregadas do servidor; só leitura, não mexa durante a iteração. */
    public static Collection<TvBlockEntity> serverScreens() {
        return Collections.unmodifiableSet(SERVER);
    }

    /** As Telas do servidor da fase; não mexa durante a iteração. */
    public static List<TvBlockEntity> serverBucket(int phase) {
        return BUCKETS.get(phase);
    }

    /** O conjunto do lado do {@code level}. */
    public static Set<TvBlockEntity> of(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    public static void add(TvBlockEntity tv) {
        if (tv.getLevel() != null) {
            if (of(tv.getLevel()).add(tv) && !tv.getLevel().isClientSide) {
                BUCKETS.get(phase(tv)).add(tv);
            }
        }
    }

    public static void remove(TvBlockEntity tv) {
        if (tv.getLevel() != null) {
            if (of(tv.getLevel()).remove(tv) && !tv.getLevel().isClientSide) {
                BUCKETS.get(phase(tv)).remove(tv);
            }
        }
    }

    /** Na parada do servidor. */
    public static void clearServer() {
        SERVER.clear();
        for (List<TvBlockEntity> bucket : BUCKETS) {
            bucket.clear();
        }
    }

    /** Ao sair do mundo, no cliente. */
    public static void clearClient() {
        CLIENT.clear();
    }
}
