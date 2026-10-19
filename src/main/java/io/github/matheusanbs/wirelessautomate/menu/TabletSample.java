package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.RateMath;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * A amostra do Tablet, uma por servidor e a mesma para todos os jogadores: a vazão de cada nó carregado, os que
 * moveram algo e os que têm destino cheio, medidos no máximo uma vez a cada {@link TabletMenu#SAMPLE_TICKS} ticks
 * por quem pedir primeiro ({@link #sampleIfDue}); e os nós do índice na ordem da Lista (máquina, nome, dimensão,
 * posição), refeita só quando o índice muda ({@link #sorted}). Com vários Tablets abertos, cada montagem só lê
 * daqui, sem medir nem ordenar de novo.
 *
 * <p>Só a thread do servidor mexe.
 */
final class TabletSample {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final Comparator<NodeIndex.Entry> LIST_ORDER = Comparator
            .comparing(NodeIndex.Entry::machine)
            .thenComparing(NodeIndex.Entry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.key().dimension().location())
            .thenComparingLong(entry -> entry.key().pos().asLong());
    private static @Nullable TabletSample instance;

    private final MinecraftServer server;
    private long sampleTick = Long.MIN_VALUE;
    /** O que cada nó tinha movido na última amostra, por {@link ResourceType#ordinal()}. */
    private final Map<NodeKey, long[]> lastMoved = new Object2ObjectOpenHashMap<>();
    /** A vazão de cada nó que moveu algo na última amostra, por tipo, na unidade do registro. */
    private final Map<NodeKey, long[]> rates = new Object2ObjectOpenHashMap<>();
    private final Set<NodeKey> active = new ObjectOpenHashSet<>();
    private final Set<NodeKey> full = new ObjectOpenHashSet<>();
    private final Set<NodeKey> seen = new ObjectOpenHashSet<>();
    private final List<NodeIndex.Entry> sorted = new ArrayList<>();
    private int sortedVersion = -1;
    /** O estado e a vazão das linhas de máquina à vista, medidos sob demanda. */
    private final TabletMachineSample machines = new TabletMachineSample();

    private TabletSample(MinecraftServer server) {
        this.server = server;
    }

    static TabletSample get(MinecraftServer server) {
        TabletSample sample = instance;
        if (sample == null || sample.server != server) {
            sample = new TabletSample(server);
            instance = sample;
        }
        return sample;
    }

    /** Ao parar o servidor. */
    static void reset() {
        instance = null;
    }

    /**
     * Mede de novo se a última amostra tem {@link TabletMenu#SAMPLE_TICKS} ticks ou mais (ou se nunca mediu): a
     * vazão desde a anterior, quem moveu e quem tem destino cheio, dos nós carregados do índice. Um nó visto pela
     * primeira vez só guarda o que já moveu. Depois de mais de duas amostras sem ninguém pedir (todos os Tablets
     * fechados), a rodada é só a linha de base: guarda o que cada nó já moveu, sem vazão nem ativos, para a primeira
     * página não mostrar a média do intervalo inteiro.
     */
    void sampleIfDue(NodeIndex index, long now) {
        if (sampleTick != Long.MIN_VALUE && now - sampleTick < TabletMenu.SAMPLE_TICKS) {
            return;
        }
        boolean baseline = sampleTick == Long.MIN_VALUE || now - sampleTick > 2L * TabletMenu.SAMPLE_TICKS;
        long elapsed = baseline ? 1 : Math.max(1, now - sampleTick);
        sampleTick = now;
        machines.beginRound(baseline);
        active.clear();
        rates.clear();
        full.clear();
        seen.clear();
        for (NodeIndex.Entry entry : index.entries()) {
            RouterBlockEntity router = entry.router();
            if (router == null) {
                continue;
            }
            NodeKey key = entry.key();
            seen.add(key);
            if (measure(key, router, elapsed, baseline)) {
                active.add(key);
            }
            if (TabletNodes.fullDestinations(router, now) > 0) {
                full.add(key);
            }
        }
        lastMoved.keySet().retainAll(seen);
    }

    /**
     * Mede a vazão do nó por tipo; devolve se ele moveu algo desde a amostra anterior. Na linha de base (ou num nó
     * novo), só guarda o que ele já moveu.
     */
    private boolean measure(NodeKey key, RouterBlockEntity router, long elapsed, boolean baseline) {
        long[] last = lastMoved.get(key);
        if (last == null || baseline) {
            if (last == null) {
                last = new long[TYPES.length];
                lastMoved.put(key, last);
            }
            for (ResourceType type : TYPES) {
                last[type.ordinal()] = router.moved(type);
            }
            return false;
        }
        boolean moved = false;
        for (ResourceType type : TabletNodes.TRANSFER_TYPES) {
            long now = router.moved(type);
            long delta = Math.max(0, now - last[type.ordinal()]);
            if (delta > 0) {
                moved = true;
                long[] nodeRates = rates.get(key);
                if (nodeRates == null) {
                    nodeRates = new long[TYPES.length];
                    rates.put(key, nodeRates);
                }
                nodeRates[type.ordinal()] = RateMath.rate(delta, elapsed, type.ratePerTick());
            }
        }
        for (ResourceType type : TYPES) {
            last[type.ordinal()] = router.moved(type);
        }
        return moved;
    }

    boolean active(NodeKey key) {
        return active.contains(key);
    }

    boolean full(NodeKey key) {
        return full.contains(key);
    }

    /** A vazão do nó por tipo na última amostra, ou {@code null} se ele não moveu nada. Só leitura. */
    long @Nullable [] rates(NodeKey key) {
        return rates.get(key);
    }

    /** O estado e a vazão das linhas de máquina, medidos só para as que alguma página pede. */
    TabletMachineSample machines() {
        return machines;
    }

    /** Os nós do índice na ordem da Lista, refeita só quando um dado salvo do índice muda. Só leitura. */
    List<NodeIndex.Entry> sorted(NodeIndex index) {
        if (sortedVersion != index.dataVersion() || sorted.size() != index.size()) {
            sorted.clear();
            sorted.addAll(index.entries());
            sorted.sort(LIST_ORDER);
            sortedVersion = index.dataVersion();
        }
        return sorted;
    }
}
