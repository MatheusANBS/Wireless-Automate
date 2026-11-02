package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O que um {@link RouterBlockEntity} sabe dos vizinhos: a máquina presa (os caches de capability dela, o bloco
 * do último aviso e a frente) e o sinal de redstone no roteador. Tudo atualizado pelos avisos do bloco, nunca
 * por tick; o sinal vai no NBT pela chave {@code powered}.
 */
final class RouterNeighbors {
    private final RouterBlockEntity router;
    private final RouterAttached attached;
    /** Caches de capability da máquina presa; apontados de novo quando o roteador gira. */
    private final MachineCaches attachedCaches;
    /** Bloco da máquina no último aviso do vizinho ({@link #machineNeighborChanged}); {@code null} = desconhecido. */
    private @Nullable Block machineBlock;
    private boolean powered;

    RouterNeighbors(RouterBlockEntity router, RouterAttached attached) {
        this.router = router;
        this.attached = attached;
        this.attachedCaches = MachineCaches.attached(router, BlockPos.ZERO);
    }

    MachineCaches attachedCaches() {
        return attachedCaches;
    }

    boolean powered() {
        return powered;
    }

    /** Ver {@link RouterBlockEntity#attachedFront()}. */
    int attachedFront() {
        Level level = router.getLevel();
        BlockPos machine = router.machinePos();
        return level == null || !level.isLoaded(machine) ? -1 : RemoteMachines.frontOf(level.getBlockState(machine));
    }

    /** Ver {@link RouterBlockEntity#machineNeighborChanged()}. */
    void machineNeighborChanged() {
        Level level = router.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(router.machinePos());
        Block block = state.getBlock();
        if (block != machineBlock) {
            machineBlock = block;
            router.machineChanged();
            if (!level.isClientSide) {
                // Bloco trocado: a presa vai para o grupo do bloco novo (ar não muda nada).
                if (attached.sync(state)) {
                    router.changed(NetworkManager.ALL_TYPES);
                }
                // Máquina nova: as portas dela (e quem espera por elas) tentam de novo, mesmo que a
                // troca não invalide nenhuma capability já consultada.
                NetworkManager.get().wake(router);
            }
        } else if (!level.isClientSide && attached.frontChanged(state)) {
            // Só o estado mudou e a máquina girou: os lados dela caem noutras faces.
            router.changed(NetworkManager.ALL_TYPES);
        }
    }

    /** Ver {@link RouterBlockEntity#updatePowered()}. */
    void updatePowered() {
        Level level = router.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = router.getBlockPos();
        boolean now = level.hasNeighborSignal(pos);
        if (now != powered) {
            powered = now;
            // Sem changeVersion: um relógio ao lado reenviaria a tela aberta a cada pulso. A tela confere o sinal
            // uma vez por segundo. Só marca o chunk: avisar os vizinhos a cada pulso de um relógio não serve a ninguém.
            level.blockEntityChanged(pos);
            if (router.redstoneTypes() != 0) {
                NetworkManager.get().poweredChanged(router);
            }
        }
    }

    /** Virado para outra face: a máquina mudou de lugar. */
    void rotated() {
        attachedCaches.retarget(router.machinePos());
        machineBlock = null;
    }

    /** Ao carregar o chunk os vizinhos podem não estar carregados; aí fica o valor salvo. */
    void loaded(Level level) {
        if (level.isAreaLoaded(router.getBlockPos(), 1)) {
            powered = level.hasNeighborSignal(router.getBlockPos());
            machineBlock = level.getBlockState(router.machinePos()).getBlock();
        }
    }

    void save(CompoundTag tag) {
        if (powered) {
            tag.putBoolean("powered", true);
        }
    }

    void load(CompoundTag tag) {
        powered = tag.getBoolean("powered");
    }
}
