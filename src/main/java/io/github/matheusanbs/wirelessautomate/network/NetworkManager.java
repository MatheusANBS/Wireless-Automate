package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * Gerenciador central: um por servidor, processa todas as redes dentro do orçamento de tempo.
 * Os nós não fazem tick (ver docs/especificacao.md, "Arquitetura de performance").
 */
public final class NetworkManager {
    private static NetworkManager instance;

    private final Set<RouterBlockEntity> nodes = new ReferenceOpenHashSet<>();
    private final TickBudget budget = new TickBudget(500_000L);

    public static NetworkManager get() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor, para o próximo mundo começar limpo. */
    public static void reset() {
        instance = null;
    }

    public void addNode(RouterBlockEntity node) {
        nodes.add(node);
    }

    public void removeNode(RouterBlockEntity node) {
        nodes.remove(node);
    }

    public boolean contains(RouterBlockEntity node) {
        return nodes.contains(node);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public TickBudget budget() {
        return budget;
    }

    public void tick(MinecraftServer server) {
        budget.setBaseNanos((long) (Config.TICK_BUDGET_MS.get() * 1_000_000L));
        if (Config.ADAPTIVE_BUDGET.get()) {
            budget.adapt(server.getAverageTickTimeNanos() / 1_000_000.0);
        } else {
            budget.adapt(0);
        }
        budget.begin(System.nanoTime());

        // TODO(v1): pegar a próxima rota acordada, em ordem de prioridade, e mover o lote enquanto
        //  budget.hasTime(System.nanoTime()); quando acabar, salvar o cursor para o tick seguinte.

        budget.end(System.nanoTime());
    }
}
