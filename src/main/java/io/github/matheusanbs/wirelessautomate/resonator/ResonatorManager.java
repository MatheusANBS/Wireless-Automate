package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
    /**
     * Os mesmos Núcleos, em {@link #BATCH_TICKS} baldes pela fase da posição: cada tick só percorre o balde dos que
     * têm lote nele, em vez de todos.
     */
    private static final List<Set<ResonanceCoreBlockEntity>> BUCKETS = buckets();

    private ResonatorManager() {
    }

    private static List<Set<ResonanceCoreBlockEntity>> buckets() {
        List<Set<ResonanceCoreBlockEntity>> buckets = new ArrayList<>(BATCH_TICKS);
        for (int i = 0; i < BATCH_TICKS; i++) {
            buckets.add(Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return buckets;
    }

    /** A fase do Núcleo: ele tem lote nos ticks em que {@code (gameTime + fase) % BATCH_TICKS == 0}. */
    private static int phase(ResonanceCoreBlockEntity core) {
        return Math.floorMod(core.getBlockPos().hashCode(), BATCH_TICKS);
    }

    static void add(ResonanceCoreBlockEntity core) {
        CORES.add(core);
        BUCKETS.get(phase(core)).add(core);
    }

    static void remove(ResonanceCoreBlockEntity core) {
        CORES.remove(core);
        BUCKETS.get(phase(core)).remove(core);
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
     * O Núcleo em {@code owner} soltou blocos: os Núcleos que não montavam por uma parede dele revalidam no tick
     * seguinte. Só a soltura avisa (montar não), então dois Ressonadores nunca se revalidam um ao outro sem fim.
     */
    static void wakeBlockedBy(ServerLevel level, BlockPos owner) {
        for (ResonanceCoreBlockEntity core : CORES) {
            ResonatorLayout.Broken problem = core.lastProblem();
            if (core.getLevel() == level && !core.isRemoved() && problem != null
                    && problem.problem() == ResonatorLayout.Problem.SHARED_WALL && problem.x() == owner.getX()
                    && problem.y() == owner.getY() && problem.z() == owner.getZ()) {
                level.scheduleTick(core.getBlockPos(), core.getBlockState().getBlock(), 1);
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
     * Chamado a cada tick do servidor: cada Núcleo montado roda um lote de {@link #BATCH_TICKS} ticks uma vez a cada
     * {@link #BATCH_TICKS}, num tick próprio pela posição, para os Núcleos não caírem todos no mesmo tick; só o
     * balde da fase do tick é percorrido. Num chunk sem tick de blocos (a borda da área carregada) o Núcleo não gera,
     * como uma fornalha. Os lotes rodam fora do laço do balde: o aviso do lote às Portas acorda vizinhos de outros
     * mods, que podem mudar blocos e, com isso, pôr ou tirar Núcleos do conjunto. A lista só nasce num tick com lote.
     */
    public static void tick(MinecraftServer server) {
        if (CORES.isEmpty()) {
            return;
        }
        // O relógio do overworld vale para todas as dimensões (as outras leem o mesmo tempo de jogo).
        int bucket = Math.floorMod(-server.overworld().getGameTime(), BATCH_TICKS);
        List<ResonanceCoreBlockEntity> due = null;
        for (ResonanceCoreBlockEntity core : BUCKETS.get(bucket)) {
            if (core.isRemoved() || !core.formed() || !(core.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            if (level.shouldTickBlocksAt(ChunkPos.asLong(core.getBlockPos()))) {
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
        BUCKETS.forEach(Set::clear);
    }
}
