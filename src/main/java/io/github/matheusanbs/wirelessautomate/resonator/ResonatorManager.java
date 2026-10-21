package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
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
    /**
     * Na run de GameTest a grade põe os Ressonadores de testes diferentes a menos de 32 blocos uns dos outros, e a
     * interferência mudaria a geração que eles conferem: lá ela nasce desligada, e os testes dela a ligam.
     */
    private static boolean interferenceOn = !Boolean.getBoolean("wirelessautomate.gameTests");
    /** A config mudou (de qualquer thread): o próximo tick refaz a interferência de todos. */
    private static volatile boolean interferenceConfigChanged;

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

    /** O Núcleo carregou: entra no conjunto e refaz a interferência dele e dos vizinhos. */
    static void add(ResonanceCoreBlockEntity core) {
        CORES.add(core);
        BUCKETS.get(phase(core)).add(core);
        interferenceChanged(core);
    }

    /** O Núcleo descarregou ou saiu: os vizinhos deixam de contá-lo. */
    static void remove(ResonanceCoreBlockEntity core) {
        CORES.remove(core);
        BUCKETS.get(phase(core)).remove(core);
        interferenceChanged(core);
    }

    /**
     * O Núcleo montou, desmontou, carregou ou descarregou: refaz a interferência dos Núcleos da mesma dimensão no
     * raio dele (e a dele, se ainda está no conjunto). Só nesses eventos, numa varredura linear dos Núcleos
     * carregados; o lote só lê o fator guardado.
     */
    static void interferenceChanged(ResonanceCoreBlockEntity changed) {
        int radius = interferenceRadius();
        Level level = changed.getLevel();
        if (radius <= 0 || level == null) {
            return;
        }
        BlockPos at = changed.getBlockPos();
        for (ResonanceCoreBlockEntity core : CORES) {
            if (core.getLevel() == level && !core.isRemoved() && (core == changed || ResonatorInterference.counts(
                    distance(core.getBlockPos(), at), radius))) {
                refreshInterference(core, radius);
            }
        }
    }

    /** Refaz a interferência de todos os Núcleos carregados (a config mudou, ou um GameTest a ligou). */
    public static void refreshAllInterference() {
        int radius = interferenceRadius();
        for (ResonanceCoreBlockEntity core : CORES) {
            refreshInterference(core, radius);
        }
    }

    /** Só para os GameTests: liga ou desliga a interferência e refaz a de todos. */
    public static void setInterferenceForTests(boolean on) {
        interferenceOn = on;
        refreshAllInterference();
    }

    /** A config do servidor recarregou; seguro de qualquer thread. */
    public static void configChanged() {
        interferenceConfigChanged = true;
    }

    private static int interferenceRadius() {
        return interferenceOn ? Config.interferenceRadius() : 0;
    }

    /** Conta os outros Núcleos montados da mesma dimensão no raio; com raio 0 a perda é zero. */
    private static void refreshInterference(ResonanceCoreBlockEntity core, int radius) {
        ResonatorInterference.Tally tally = new ResonatorInterference.Tally(radius);
        if (radius > 0) {
            BlockPos at = core.getBlockPos();
            for (ResonanceCoreBlockEntity other : CORES) {
                if (other != core && other.getLevel() == core.getLevel() && !other.isRemoved() && other.formed()) {
                    tally.add(distance(other.getBlockPos(), at));
                }
            }
        }
        core.interference().set(ResonatorInterference.lossPermille(tally.count(), Config.interferencePenalty(),
                Config.interferenceMax()), tally.nearest());
    }

    private static int distance(BlockPos a, BlockPos b) {
        return ResonatorInterference.distance(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
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
        long started = System.nanoTime();
        if (interferenceConfigChanged) {
            interferenceConfigChanged = false;
            refreshAllInterference();
        }
        runDue(server);
        ResonatorProfile.tickEnded(System.nanoTime() - started);
    }

    private static void runDue(MinecraftServer server) {
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

    static int coreCount() {
        return CORES.size();
    }

    static int formedCount() {
        int formed = 0;
        for (ResonanceCoreBlockEntity core : CORES) {
            if (!core.isRemoved() && core.formed()) {
                formed++;
            }
        }
        return formed;
    }

    /** Parada do servidor: o próximo mundo começa sem Núcleos. */
    public static void reset() {
        CORES.clear();
        BUCKETS.forEach(Set::clear);
        interferenceConfigChanged = false;
    }
}
