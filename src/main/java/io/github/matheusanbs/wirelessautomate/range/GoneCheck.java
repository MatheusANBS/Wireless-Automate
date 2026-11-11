package io.github.matheusanbs.wirelessautomate.range;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A conferência das máquinas remotas que saíram do lugar (quebradas, trocadas por outro bloco) sem ser desvinculadas,
 * de um dono de remotas: o roteador de alcance e a Doca de Padrões. Nada por tick: o dono marca uma máquina suspeita
 * ({@link #suspect}) pelos avisos que já tem (o listener de invalidação do cache, o aviso do mixin e da carga do
 * chunk, um vínculo novo) e todas na carga dele ({@link #start}); a conferência roda depois, numa tarefa só na fila
 * do servidor, fora do listener (o NeoForge pede que ele não leia o mundo) e da descarga de chunk.
 *
 * <p>A regra: com o chunk carregado ({@code level.isLoaded} antes de ler), a máquina cujo bloco não é o do vínculo
 * (ar ou outro bloco; girada, com o mesmo bloco, fica) sai pelo caminho de desvincular do dono. <b>Chunk descarregado
 * não é quebra:</b> a máquina fica e não é lida. A que fica é avisada ao dono ({@link Host#stays}), que liga a vigia
 * do cache ({@link MachineCaches#watch}) se ela está sem: o listener do NeoForge só avisa uma vez depois de cada
 * consulta.
 *
 * <p>Custo: uma leitura de bloco por suspeita, só depois de um aviso; O(máquinas) na carga do dono. Só a thread do
 * servidor mexe aqui.
 */
public final class GoneCheck {
    /** O dono das máquinas: as regras dele (o bloco de cada uma, a vigia, o desvincular). */
    public interface Host {
        /** O nível das máquinas, ou nulo. */
        @Nullable Level goneLevel();

        /** Põe em {@code out} as posições ({@code BlockPos.asLong}) de todas as máquinas vinculadas. */
        void allMachines(LongArrayList out);

        /**
         * A máquina em {@code pos} tem {@code block} no lugar, com o chunk carregado: diz se ela fica (o bloco é o do
         * vínculo, ou a posição nem está mais vinculada) e, se fica e está sem vigia, liga a vigia.
         */
        boolean stays(BlockPos pos, Block block);

        /** Desvincula as máquinas em {@code positions} pelo caminho de sempre do dono, numa mudança só. */
        void unlinkGone(LongArrayList positions);
    }

    private final Host host;
    /** As suspeitas ({@code BlockPos.asLong}) para a próxima conferência; criado no primeiro aviso. */
    private @Nullable LongOpenHashSet suspects;
    /** A próxima conferência vê todas as máquinas (a carga do dono). */
    private boolean all;
    /** Uma conferência na fila do servidor, para os avisos seguintes não enfileirarem outra. */
    private boolean queued;
    /** O dono está no mundo ({@link #start} até {@link #stop}): fora disso os avisos são ignorados. */
    private boolean running;

    public GoneCheck(Host host) {
        this.host = host;
    }

    /**
     * O dono carregou: com máquinas ({@code hasMachines}), confere todas (a quebrada com ele descarregado, sem vigia,
     * sai aqui); sem nenhuma, nem enfileira.
     */
    public void start(boolean hasMachines) {
        running = true;
        if (hasMachines) {
            all = true;
            queue();
        }
    }

    /** O dono descarregou ou saiu do mundo: esquece as suspeitas e ignora os avisos até o próximo {@link #start}. */
    public void stop() {
        running = false;
        all = false;
        if (suspects != null) {
            suspects.clear();
        }
    }

    /**
     * A máquina em {@code pos} pode ter saído do lugar: entra na próxima conferência. Não lê o mundo aqui, porque o
     * aviso pode vir de dentro de um listener de capability ou de uma descarga de chunk.
     */
    public void suspect(BlockPos pos) {
        if (!running) {
            return;
        }
        if (suspects == null) {
            suspects = new LongOpenHashSet();
        }
        suspects.add(pos.asLong());
        queue();
    }

    private void queue() {
        if (queued || !(host.goneLevel() instanceof ServerLevel level)) {
            return;
        }
        queued = true;
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), this::run));
    }

    private void run() {
        queued = false;
        boolean everything = all;
        all = false;
        Level level = host.goneLevel();
        if (!running || level == null || level.isClientSide) {
            stop();
            return;
        }
        LongArrayList candidates = new LongArrayList();
        if (everything) {
            host.allMachines(candidates);
        } else if (suspects != null) {
            candidates.addAll(suspects);
        }
        if (suspects != null) {
            suspects.clear();
        }
        LongArrayList gone = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0, n = candidates.size(); i < n; i++) {
            pos.set(candidates.getLong(i));
            // antes de ler o bloco: ler um chunk descarregado o carregaria
            if (!level.isLoaded(pos) || host.stays(pos.immutable(), level.getBlockState(pos).getBlock())) {
                continue;
            }
            if (gone == null) {
                gone = new LongArrayList();
            }
            gone.add(candidates.getLong(i));
        }
        if (gone != null) {
            host.unlinkGone(gone);
        }
    }
}
