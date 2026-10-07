package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RouterSnapshotPayload;
import io.github.matheusanbs.wirelessautomate.packet.RouterThroughputPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do roteador. Não tem slots: no cliente guarda o último {@link RouterSnapshot} e a
 * vazão atual; no servidor conhece o roteador e envia atualizações enquanto a tela estiver aberta.
 *
 * <p>Sincronização (servidor): o vanilla só chama {@link #broadcastChanges()} no menu aberto de
 * cada jogador, então nada roda para roteadores sem tela aberta. A cada chamada o menu compara a
 * {@link RouterBlockEntity#changeVersion()} do roteador e a {@link NetworkSavedData#version()} das
 * redes com as que já enviou; se alguma mudou, remonta e envia o snapshot inteiro. A especificação
 * pede "só diferenças": o snapshot tem uns 100 bytes, então mandá-lo inteiro só quando algo muda
 * custa menos do que calcular diferenças. A vazão é amostrada a cada {@link #SAMPLE_TICKS} ticks
 * pelos totais que o motor soma no roteador e só é enviada quando muda.
 */
public class RouterMenu extends AbstractContainerMenu {
    /** Faixa da prioridade aceita da tela; valores fora dela são limitados. */
    public static final int MIN_PRIORITY = -999;
    public static final int MAX_PRIORITY = 999;
    /** Distância máxima (em blocos, do olho ao centro do roteador) para a tela continuar aberta. */
    public static final double MAX_DISTANCE = 8.0;
    /** Janela da amostra de vazão, em ticks. */
    public static final int SAMPLE_TICKS = 20;

    private final @Nullable RouterBlockEntity router;
    private RouterSnapshot snapshot;
    private long[] throughput = new long[ResourceType.values().length];
    private int version;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private int sentChangeVersion;
    private int sentDataVersion;
    private long sampleTick;
    private final long[] sampleTotals = new long[ResourceType.values().length];
    private final long[] sentThroughput = new long[ResourceType.values().length];

    /** Servidor. */
    public RouterMenu(int containerId, Inventory inventory, RouterBlockEntity router, RouterSnapshot snapshot) {
        super(ModMenus.ROUTER.get(), containerId);
        this.router = router;
        this.snapshot = snapshot;
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
        this.sentChangeVersion = router.changeVersion();
        if (viewer != null) {
            this.sentDataVersion = NetworkSavedData.get(viewer.server).version();
            this.sampleTick = viewer.server.getTickCount();
        }
        for (ResourceType type : ResourceType.values()) {
            sampleTotals[type.ordinal()] = router.moved(type);
        }
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
        this.viewer = null;
    }

    /**
     * Abre a tela do roteador para o jogador. O mesmo snapshot vai no buffer de abertura e no menu
     * do servidor. O título é o nome do nó ou, sem nome, o nome do bloco.
     */
    public static void open(ServerPlayer player, RouterBlockEntity router) {
        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
        Component title = router.name().isEmpty()
                ? router.getBlockState().getBlock().getName()
                : Component.literal(router.name());
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new RouterMenu(containerId, inventory, router, snapshot), title),
                buf -> RouterSnapshot.STREAM_CODEC.encode(buf, snapshot));
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

    /** No servidor: o roteador continua no mundo, na mesma dimensão e a até {@link #MAX_DISTANCE} blocos. */
    @Override
    public boolean stillValid(Player player) {
        if (router == null) {
            return true;
        }
        if (router.isRemoved() || router.getLevel() != player.level()
                || player.level().getBlockEntity(router.getBlockPos()) != router) {
            return false;
        }
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))
                <= MAX_DISTANCE * MAX_DISTANCE;
    }

    /** Envia ao jogador da tela o snapshot, se o roteador ou as redes mudaram, e a vazão, se mudou. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (router == null || viewer == null) {
            return;
        }
        RouterSnapshot changed = pollSnapshot();
        if (changed != null) {
            send(new RouterSnapshotPayload(containerId, changed));
        }
        long[] rates = pollThroughput(viewer.server.getTickCount());
        if (rates != null) {
            send(new RouterThroughputPayload(containerId, rates));
        }
    }

    private void send(CustomPacketPayload payload) {
        // Jogadores falsos (GameTests, mods de automação) não negociam os canais do mod.
        if (viewer != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /**
     * Snapshot novo se o roteador ou as redes mudaram desde o último enviado, ou {@code null}.
     * Marca o novo como enviado. Só no servidor; público para os GameTests.
     */
    public @Nullable RouterSnapshot pollSnapshot() {
        if (router == null || viewer == null || router.isRemoved()) {
            return null;
        }
        int changeVersion = router.changeVersion();
        int dataVersion = NetworkSavedData.get(viewer.server).version();
        if (changeVersion == sentChangeVersion && dataVersion == sentDataVersion) {
            return null;
        }
        sentChangeVersion = changeVersion;
        sentDataVersion = dataVersion;
        snapshot = RouterSnapshot.capture(router, viewer);
        return snapshot;
    }

    /**
     * Vazão da última janela se já passaram {@link #SAMPLE_TICKS} ticks desde a amostra anterior e
     * ela mudou em relação à última enviada, ou {@code null}. Itens e fluidos por segundo, energia
     * por tick. Só no servidor; público para os GameTests.
     */
    public long @Nullable [] pollThroughput(long now) {
        if (router == null) {
            return null;
        }
        long elapsed = now - sampleTick;
        if (elapsed < SAMPLE_TICKS) {
            return null;
        }
        boolean changed = false;
        for (ResourceType type : ResourceType.values()) {
            int i = type.ordinal();
            long total = router.moved(type);
            long delta = total - sampleTotals[i];
            sampleTotals[i] = total;
            long rate = type == ResourceType.ENERGY
                    ? (delta + elapsed / 2) / elapsed
                    : (delta * 20 + elapsed / 2) / elapsed;
            if (rate != sentThroughput[i]) {
                sentThroughput[i] = rate;
                changed = true;
            }
        }
        sampleTick = now;
        return changed ? Arrays.copyOf(sentThroughput, sentThroughput.length) : null;
    }
}
