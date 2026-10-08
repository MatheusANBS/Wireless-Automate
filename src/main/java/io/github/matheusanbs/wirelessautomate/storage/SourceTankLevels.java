package io.github.matheusanbs.wirelessautomate.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Os Tanques de Source carregados no servidor, para o nível do bloco ({@link StorageSourceTankBlock#FILL})
 * se corrigir quando a capacidade do tier muda na config com o jogo rodando. Só a thread do servidor mexe
 * no conjunto; o aviso da config pode vir de outra thread, então só marca e o tick faz o trabalho.
 */
public final class SourceTankLevels {
    private static final Set<StorageSourceTankBlockEntity> TANKS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile boolean configChanged;

    /** A config mudou: os níveis serão recalculados no próximo tick do servidor. Seguro de qualquer thread. */
    public static void configChanged() {
        configChanged = true;
    }

    /** Chamado a cada tick do servidor: barato quando nada mudou. */
    public static void tick() {
        if (configChanged) {
            configChanged = false;
            refreshAll();
        }
    }

    /** Recalcula o nível de cada tanque carregado (o que já está certo não faz nada). Só na thread do servidor. */
    public static void refreshAll() {
        for (StorageSourceTankBlockEntity tank : new ArrayList<>(TANKS)) {
            tank.refreshFill();
        }
    }

    static void add(StorageSourceTankBlockEntity tank) {
        TANKS.add(tank);
    }

    static void remove(StorageSourceTankBlockEntity tank) {
        TANKS.remove(tank);
    }

    /** Parada do servidor: o próximo mundo começa sem tanques. */
    public static void reset() {
        TANKS.clear();
        configChanged = false;
    }

    private SourceTankLevels() {
    }
}
