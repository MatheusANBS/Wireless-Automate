package io.github.matheusanbs.wirelessautomate.repairer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Os Reparadores com pelo menos uma linha trabalhando: só eles andam a cada tick do servidor. Sem nenhum, o tick é uma
 * checagem de conjunto vazio. O Reparador entra ao começar uma linha (e ao carregar com uma em andamento) e sai
 * quando nenhuma linha trabalha; um reparo que terminou sem saída livre fica travado fora daqui até abrir espaço.
 */
public final class RepairerManager {
    private static final Set<RepairerBlockEntity> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Rascunho do tick: o Reparador pode sair do conjunto enquanto anda. */
    private static final List<RepairerBlockEntity> SCRATCH = new ArrayList<>();

    private RepairerManager() {
    }

    static void add(RepairerBlockEntity repairer) {
        ACTIVE.add(repairer);
    }

    static void remove(RepairerBlockEntity repairer) {
        ACTIVE.remove(repairer);
    }

    public static boolean contains(RepairerBlockEntity repairer) {
        return ACTIVE.contains(repairer);
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** Chamado a cada tick do servidor: barato quando nada trabalha. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        SCRATCH.clear();
        for (RepairerBlockEntity repairer : ACTIVE) {
            SCRATCH.add(repairer);
        }
        for (RepairerBlockEntity repairer : SCRATCH) {
            if (repairer.isRemoved() || !(repairer.getLevel() instanceof ServerLevel level)
                    || !level.shouldTickBlocksAt(ChunkPos.asLong(repairer.getBlockPos()))) {
                continue;
            }
            repairer.tick(now);
        }
        SCRATCH.clear();
    }

    /** Parada do servidor: o próximo mundo começa sem Reparadores. */
    public static void reset() {
        ACTIVE.clear();
        SCRATCH.clear();
    }
}
