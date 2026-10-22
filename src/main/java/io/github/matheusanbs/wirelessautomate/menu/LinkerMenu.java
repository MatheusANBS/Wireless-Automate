package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.packet.LinkerSnapshotPayload;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do Vinculador, sem slots. Vale enquanto o Vinculador continua na mão com que foi
 * aberto; as ações chegam pelo {@code LinkerActionPayload} e mexem nos componentes do item.
 *
 * <p>Sincronização (servidor): como no {@link RouterMenu}, só o menu aberto recebe
 * {@link #broadcastChanges()}. O estado é remontado quando o item (desvincular, cantos, roteador
 * escolhido) muda e, fora isso, a cada {@link #RESCAN_TICKS} ticks (as vagas do roteador, as máquinas
 * dentro da área e os chunks carregados mudam sem o item mudar); só vai ao cliente se ficou diferente do
 * último enviado.
 */
public class LinkerMenu extends AbstractContainerMenu {
    /** Intervalo da nova leitura do estado com a tela aberta. */
    public static final int RESCAN_TICKS = 20;

    private final InteractionHand hand;
    private LinkerSnapshot snapshot;
    private int version;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private LinkerSnapshot.@Nullable Outcome outcome;
    private @Nullable StackKey sentKey;
    private long lastCapture;
    private boolean dirty;

    /** O que do item muda o estado da tela. */
    private record StackKey(boolean unlink, Optional<LinkerArea> area, Optional<GlobalPos> router) {
        static StackKey of(ItemStack stack) {
            return new StackKey(LinkerItem.unlink(stack), Optional.ofNullable(LinkerItem.area(stack)),
                    Optional.ofNullable(LinkerItem.router(stack)));
        }
    }

    /** Servidor. */
    public LinkerMenu(int containerId, Inventory inventory, InteractionHand hand, LinkerSnapshot snapshot) {
        super(ModMenus.LINKER.get(), containerId);
        this.hand = hand;
        this.snapshot = snapshot;
        this.viewer = inventory.player instanceof ServerPlayer player ? player : null;
        if (viewer != null) {
            this.sentKey = StackKey.of(linker(viewer));
            this.lastCapture = viewer.server.getTickCount();
        }
    }

    /** Cliente: a mão e o estado inicial vêm no buffer de abertura. */
    public LinkerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, buf.readEnum(InteractionHand.class), LinkerSnapshot.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de desenvolvimento): só o estado. */
    public LinkerMenu(int containerId, InteractionHand hand, LinkerSnapshot snapshot) {
        super(ModMenus.LINKER.get(), containerId);
        this.hand = hand;
        this.snapshot = snapshot;
        this.viewer = null;
    }

    /** Abre a tela do Vinculador que está na mão {@code hand}. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        LinkerSnapshot snapshot = LinkerSnapshot.capture(player, player.getItemInHand(hand), null);
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new LinkerMenu(containerId, inventory, hand, snapshot),
                        Component.translatable("item.wirelessautomate.linker")),
                buf -> {
                    buf.writeEnum(hand);
                    LinkerSnapshot.STREAM_CODEC.encode(buf, snapshot);
                });
    }

    public InteractionHand hand() {
        return hand;
    }

    /** O Vinculador da tela (o item na mão com que ela abriu). */
    public ItemStack linker(Player player) {
        return player.getItemInHand(hand);
    }

    public LinkerSnapshot snapshot() {
        return snapshot;
    }

    /** Muda a cada estado recebido: a tela compara para saber quando se reposicionar. */
    public int version() {
        return version;
    }

    public void applySnapshot(LinkerSnapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** No servidor: o Vinculador continua na mão com que a tela abriu. */
    @Override
    public boolean stillValid(Player player) {
        return viewer == null || linker(player).getItem() instanceof LinkerItem;
    }

    // ------------------------------------------------------------------ servidor

    /**
     * Guarda o resultado do último Vincular (ou Desvincular) para a tela mostrar; {@code null}, ou um pedido
     * que não foi aplicado, limpa.
     */
    public void setOutcome(RangeLinks.@Nullable Result result, boolean unlink) {
        if (result == null || result.status() != RangeLinks.Status.AREA_DONE) {
            outcome = null;
            return;
        }
        outcome = new LinkerSnapshot.Outcome(unlink, result.linked(), result.already(), result.outOfRange(),
                result.overLimit(), result.protectedCount(), result.unloadedChunks());
    }

    /** Pede um estado novo no próximo {@link #broadcastChanges()} (depois de uma ação). */
    public void refresh() {
        dirty = true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        LinkerSnapshot changed = poll();
        if (changed != null && viewer != null && viewer.connection != null) {
            LinkerSnapshotPayload payload = new LinkerSnapshotPayload(containerId, changed);
            // Jogadores falsos (GameTests) não negociam os canais do mod.
            if (viewer.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    /**
     * Estado novo se algo mudou desde o último enviado, ou {@code null}. Só no servidor; público para
     * os GameTests.
     */
    public @Nullable LinkerSnapshot poll() {
        if (viewer == null) {
            return null;
        }
        ItemStack stack = linker(viewer);
        if (!(stack.getItem() instanceof LinkerItem)) {
            return null;
        }
        StackKey key = StackKey.of(stack);
        long now = viewer.server.getTickCount();
        if (!dirty && Objects.equals(key, sentKey) && now - lastCapture < RESCAN_TICKS) {
            return null;
        }
        dirty = false;
        sentKey = key;
        lastCapture = now;
        LinkerSnapshot captured = LinkerSnapshot.capture(viewer, stack, outcome);
        if (captured.equals(snapshot)) {
            return null;
        }
        snapshot = captured;
        return captured;
    }

    // ------------------------------------------------------------------ cliente

    /** Handler do {@link LinkerSnapshotPayload}: só o menu com o mesmo {@code containerId}. */
    public static void onSnapshot(LinkerSnapshotPayload payload, IPayloadContext context) {
        if (context.player() != null && context.player().containerMenu instanceof LinkerMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applySnapshot(payload.snapshot());
        }
    }
}
