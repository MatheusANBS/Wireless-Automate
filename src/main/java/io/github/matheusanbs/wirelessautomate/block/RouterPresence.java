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
    private final RouterAttached attached;
    /** O chunk descarregou: o {@code setRemoved} que vem depois não é o roteador saindo do mundo. */
    private boolean chunkUnloading;

    RouterPresence(RouterBlockEntity router, RouterNeighbors neighbors, RouterRange range, RouterAttached attached) {
        this.router = router;
        this.neighbors = neighbors;
        this.range = range;
        this.attached = attached;
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
            // A presa confere o bloco da frente (e um mundo antigo converte o rascunho).
            if (attached.sync()) {
                router.changed(NetworkManager.ALL_TYPES);
            }
            NodeIndex.track(router);
            chunkUnloading = false;
            RouterChunkLoader.get().loaded(router);
            // uma remota quebrada com o roteador descarregado (sem vigia, sem aviso) sai na carga
            range.goneStart();
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
            range.goneStop();
        }
    }

    /** O estado do bloco mudou ({@link RouterBlockEntity#setBlockState}); recebe o que valia antes. */
    void stateChanged(Direction oldFacing, RouterTier oldTier, int oldSpin) {
        boolean rotated = router.facing() != oldFacing;
        // Girado no próprio eixo (spin): a máquina e a configuração dela ficam, então só a tela precisa saber.
        boolean routes = rotated || router.tier() != oldTier;
        if (routes || router.spin() != oldSpin) {
            router.bumpChangeVersion();
        }
        if (rotated) {
            neighbors.rotated();
            // Outra máquina na frente: a presa vai para o grupo dela.
            attached.sync();
            if (serverLevel() != null && router.hasChunkUpgrade()) {
                // O chunk da máquina pode ser outro.
                RouterChunkLoader.get().update(router);
            }
        }
        // Upgrade de tier muda vazão e alcance das rotas; virar para outra face muda a máquina.
        if (routes && serverLevel() != null) {
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
            attached.sync();
            NetworkManager.get().nodeChanged(router);
            NodeIndex.track(router);
            if (!router.isRemoved()) {
                RouterChunkLoader.get().loaded(router);
            }
        }
    }
}
