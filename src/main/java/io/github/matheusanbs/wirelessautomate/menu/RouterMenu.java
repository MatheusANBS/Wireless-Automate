package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do roteador. Não tem slots: no cliente guarda o último {@link RouterSnapshot} e a
 * vazão atual; no servidor conhece o roteador e envia atualizações enquanto a tela estiver aberta.
 */
public class RouterMenu extends AbstractContainerMenu {
    private final @Nullable RouterBlockEntity router;
    private RouterSnapshot snapshot;
    private long[] throughput = new long[ResourceType.values().length];
    private int version;

    /** Servidor. */
    public RouterMenu(int containerId, Inventory inventory, RouterBlockEntity router, RouterSnapshot snapshot) {
        super(ModMenus.ROUTER.get(), containerId);
        this.router = router;
        this.snapshot = snapshot;
    }

    /** Cliente: o snapshot inicial vem no buffer de abertura. */
    public RouterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, RouterSnapshot.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento): sem roteador, só o snapshot. */
    public RouterMenu(int containerId, Inventory inventory, RouterSnapshot snapshot) {
        super(ModMenus.ROUTER.get(), containerId);
        this.router = null;
        this.snapshot = snapshot;
    }

    /** O roteador; só existe no servidor. */
    public @Nullable RouterBlockEntity router() {
        return router;
    }

    public RouterSnapshot snapshot() {
        return snapshot;
    }

    /** Vazão atual por {@link ResourceType#ordinal()}, por segundo (energia em FE/t). */
    public long[] throughput() {
        return throughput;
    }

    /** Muda a cada snapshot ou vazão recebidos: a tela compara para saber quando refazer os widgets. */
    public int version() {
        return version;
    }

    public void applySnapshot(RouterSnapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    public void applyThroughput(long[] throughput) {
        this.throughput = throughput;
        version++;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // TODO(contrato): no servidor, roteador ainda no mundo e jogador perto (agente do servidor).
        return true;
    }

    // TODO(contrato): broadcastChanges() envia snapshot quando o roteador muda e a vazão a cada
    //  segundo, só para este jogador e só enquanto o menu existir (agente do servidor).
}
