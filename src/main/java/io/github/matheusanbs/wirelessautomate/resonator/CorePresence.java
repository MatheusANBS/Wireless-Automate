package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O Núcleo entrando e saindo do gerenciador e dos índices (membros e Portas), e o aviso aos vizinhos das Portas.
 * Só no servidor; quem chama já conferiu o nível.
 */
final class CorePresence {
    private CorePresence() {
    }

    /** Entra no gerenciador e devolve os membros ao índice; a revalidação vem pelo tick agendado. */
    static void enter(ResonanceCoreBlockEntity core, Level level) {
        ResonatorManager.add(core);
        if (core.formed()) {
            for (BlockPos member : core.members()) {
                ResonatorMembers.claimIfFree(level.dimension(), member, core.getBlockPos());
            }
        }
    }

    /** Sai do gerenciador e tira as Portas e os membros dos índices, sem mexer nos blocos nem acordar ninguém. */
    static void leave(ResonanceCoreBlockEntity core, Level level) {
        ResonatorManager.remove(core);
        for (BlockPos port : core.ports()) {
            ResonatorPorts.unregister(level.dimension(), port, core.getBlockPos());
        }
        core.setPorts(List.of());
        for (BlockPos member : core.members()) {
            ResonatorMembers.release(level.dimension(), member, core.getBlockPos());
        }
    }

    /** Avisa os vizinhos das Portas carregadas do tipo dado ({@link ResonanceCoreBlockEntity#notifyPorts}). */
    static void notifyPorts(Level level, List<BlockPos> ports, boolean energyPorts, boolean crystalPorts) {
        for (BlockPos port : ports) {
            if (!level.isLoaded(port)) {
                continue;
            }
            BlockState state = level.getBlockState(port);
            if (state.getBlock() instanceof ResonatorPartBlock part
                    && (energyPorts && part.kind() == ResonatorPartBlock.Kind.ENERGY_PORT
                            || crystalPorts && part.kind() == ResonatorPartBlock.Kind.CRYSTAL_PORT)) {
                level.updateNeighbourForOutputSignal(port, state.getBlock());
            }
        }
    }
}
