package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.chunk.RouterChunkLoader;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A presença de um {@link RouterBlockEntity} no mundo: entrar e sair do {@link NetworkManager}, do
 * {@link NodeIndex} e do {@link RouterChunkLoader} quando o nó carrega, descarrega ou sai, e quem avisar quando
 * o estado do bloco muda (giro, tier) ou o NBT é lido sobre um nó já no mundo. Só no servidor.
 */
final class RouterPresence {
    private final RouterBlockEntity router;
    private final RouterNeighbors neighbors;
    private final RouterRange range;
    /** O chunk descarregou: o {@code setRemoved} que vem depois não é o roteador saindo do mundo. */
    private boolean chunkUnloading;

    RouterPresence(RouterBlockEntity router, RouterNeighbors neighbors, RouterRange range) {
        this.router = router;
        this.neighbors = neighbors;
        this.range = range;
    }

    private @Nullable Level serverLevel() {
        Level level = router.getLevel();
        return level != null && !level.isClientSide ? level : null;
    }

    /** Ver {@link RouterBlockEntity#onLoad()}. */
    void loaded() {
        Level level = serverLevel();
        if (level != null) {
            neighbors.loaded(level);
            NetworkManager.get().addNode(router);
            NodeIndex.track(router);
            chunkUnloading = false;
            RouterChunkLoader.get().loaded(router);
        }
    }

    /** Ver {@link RouterBlockEntity#onChunkUnloaded()}. */
    void chunkUnloaded() {
        leaveManager();
        if (serverLevel() != null) {
            // Descarregar não tira os tickets: eles são salvos e voltam com o mundo.
            chunkUnloading = true;
            RouterChunkLoader.get().unloaded(router);
        }
    }

    /** Ver {@link RouterBlockEntity#setRemoved()}. */
    void removed() {
        leaveManager();
        if (serverLevel() != null && !chunkUnloading) {
            RouterChunkLoader.get().removed(router);
        }
    }

    private void leaveManager() {
        if (serverLevel() != null) {
            NetworkManager.get().removeNode(router);
            NodeIndex.untrack(router);
        }
    }

    /** O estado do bloco mudou ({@link RouterBlockEntity#setBlockState}); recebe o que valia antes. */
    void stateChanged(Direction oldFacing, RouterTier oldTier, int oldSpin) {
        boolean rotated = router.facing() != oldFacing;
        // Girado no próprio eixo (spin): a máquina fica no lugar, então os caches valem; como o
        // upgrade de tier, só as rotas e a tela precisam saber.
        boolean changed = rotated || router.tier() != oldTier || router.spin() != oldSpin;
        if (changed) {
            router.bumpChangeVersion();
        }
        if (rotated) {
            neighbors.rotated();
            if (serverLevel() != null && router.hasChunkUpgrade()) {
                // O chunk da máquina pode ser outro.
                RouterChunkLoader.get().update(router);
            }
        }
        // Upgrade de tier muda vazão e alcance das rotas; o giro muda o lado relativo de cada face.
        if (changed && serverLevel() != null) {
            NetworkManager.get().nodeChanged(router);
            NodeIndex.track(router);
            if (router.tier() != oldTier) {
                // O raio do tier novo decide se o upgrade de alcance vale.
                range.remotesChanged();
            }
        }
    }

    /** Depois de ler o NBT ({@link RouterBlockEntity#loadAdditional}). */
    void nbtLoaded() {
        range.pruneCaches();
        // As instâncias de RemoteMachine são outras: o índice do mixin aponta para as novas.
        range.remotesChanged();
        router.bumpChangeVersion();
        // Carga sobre um nó já no mundo (ex.: /data merge): as rotas mudam.
        if (serverLevel() != null) {
            NetworkManager.get().nodeChanged(router);
            NodeIndex.track(router);
            if (!router.isRemoved()) {
                RouterChunkLoader.get().loaded(router);
            }
        }
    }
}
