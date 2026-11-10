package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * O dono dos {@link MachineCaches} de uma máquina: dá o nível para as consultas, diz se ainda vale (um dono removido
 * deixa os caches sem valer) e recebe o aviso de invalidação de cada cache. O roteador e qualquer outro dono de
 * máquinas remotas que não seja roteador.
 */
public interface CacheOwner {
    @Nullable Level ownerLevel();

    boolean ownerRemoved();

    /** Um cache de capability de {@code caches} foi invalidado; {@code face} é a face absoluta da máquina. */
    void machineInvalidated(MachineCaches caches, ResourceType type, Direction face);
}
