package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Os Núcleos de Ressonância carregados no servidor, para o lote e para as mudanças de bloco da estrutura.
 * Só a thread do servidor mexe no conjunto.
 */
public final class ResonatorManager {
    /** Distância de Chebyshev máxima entre um bloco da casca e o Núcleo (a caixa tem 5 de lado). */
    private static final int REACH = ResonatorLayout.SIZE - 1;
    static final int BATCH_TICKS = 20;
    private static final Set<ResonanceCoreBlockEntity> CORES = Collections.newSetFromMap(new IdentityHashMap<>());

    private ResonatorManager() {
    }

    static void add(ResonanceCoreBlockEntity core) {
        CORES.add(core);
    }

    static void remove(ResonanceCoreBlockEntity core) {
        CORES.remove(core);
    }

    /**
     * Um bloco do Ressonador entrou ou saiu em {@code pos}: os Núcleos carregados que podem usá-lo revalidam no
     * tick seguinte, pelo tick agendado do bloco do Núcleo. Agendar junta as mudanças do mesmo tick (uma explosão
     * revalida uma vez) e não grava estados dentro do {@code setBlock} que avisou. E o tick agendado só roda com os
     * chunks vizinhos do Núcleo carregados: as posições a até 4 blocos estão todas lá, sem desmontagem falsa por
     * uma posição descarregada.
     */
    public static void structureChanged(ServerLevel level, BlockPos pos) {
        for (ResonanceCoreBlockEntity core : CORES) {
            BlockPos at = core.getBlockPos();
            if (core.getLevel() == level && !core.isRemoved() && Math.abs(at.getX() - pos.getX()) <= REACH
                    && Math.abs(at.getY() - pos.getY()) <= REACH && Math.abs(at.getZ() - pos.getZ()) <= REACH) {
                level.scheduleTick(at, core.getBlockState().getBlock(), 1);
            }
        }
    }

    /**
     * O gancho do {@code onPlace} e do {@code onRemove} dos blocos do Ressonador. Só o estado mudando no mesmo
     * bloco (a própria montagem regravando a casca) não conta, senão cada regravação pediria outra revalidação.
     */
    static void blockChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
        if (!before.is(after.getBlock()) && level instanceof ServerLevel server) {
            structureChanged(server, pos);
        }
    }

    /**
     * Chamado a cada tick do servidor: cada Núcleo montado e carregado roda um lote de {@link #BATCH_TICKS} ticks
     * uma vez a cada {@link #BATCH_TICKS}, num tick próprio pela posição, para os Núcleos não caírem todos no
     * mesmo tick. Os lotes rodam fora do laço do conjunto: o aviso do lote às Portas acorda vizinhos de outros
     * mods, que podem mudar blocos e, com isso, pôr ou tirar Núcleos do conjunto. A lista só nasce num tick com lote.
     */
    public static void tick(MinecraftServer server) {
        if (CORES.isEmpty()) {
            return;
        }
        List<ResonanceCoreBlockEntity> due = null;
        for (ResonanceCoreBlockEntity core : CORES) {
            if (core.isRemoved() || !core.formed() || !(core.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos pos = core.getBlockPos();
            if (level.isLoaded(pos)
                    && (level.getGameTime() + Math.floorMod(pos.hashCode(), BATCH_TICKS)) % BATCH_TICKS == 0) {
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(core);
            }
        }
        if (due == null) {
            return;
        }
        for (ResonanceCoreBlockEntity core : due) {
            if (!core.isRemoved() && core.formed() && core.getLevel() instanceof ServerLevel level) {
                ResonatorBatch.run(level, core, BATCH_TICKS);
            }
        }
    }

    /** Parada do servidor: o próximo mundo começa sem Núcleos. */
    public static void reset() {
        CORES.clear();
    }
}
