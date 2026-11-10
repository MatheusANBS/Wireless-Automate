package io.github.matheusanbs.wirelessautomate.range;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Quem o {@link RemoteIndex} avisa quando uma máquina remota vinculada muda: o roteador de alcance e qualquer outro
 * dono de remotas que não seja roteador. O aviso vem do caminho quente do mixin ({@code Level.blockEntityChanged}) e
 * da carga do chunk, sempre na thread do servidor.
 */
public interface RemoteListener {
    /** O nível do dono; as remotas registradas ficam no índice deste nível. */
    @Nullable Level listenerLevel();

    BlockPos listenerPos();

    /**
     * A máquina remota {@code machine} avisou mudança: o {@code setChanged} dela ou a carga do chunk dela. O estado do
     * bloco ({@code state}) é nulo se não deu para ler sem carregar o chunk. Barato: roda a cada {@code setChanged}.
     */
    void remoteMachineChanged(RemoteMachine machine, @Nullable BlockState state);
}
