package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * O nó escolhido no painel do Tablet (servidor): qual é, o alvo de edição dele (a presa, um grupo ou uma
 * máquina; trocar de nó volta à presa) e o {@link TabletPanelPayload} que vai para a tela, só quando é hora
 * e só se mudou. O {@link TabletMenu} é o {@link RouterEditHost} e delega para cá.
 */
final class TabletChosenNode {
    private final ServerPlayer viewer;
    private final int containerId;
    private @Nullable NodeKey chosen;
    private EditTargetRef targetRef = EditTargetRef.ATTACHED;
    /** Um pedido, o alvo ou uma edição pede o painel de novo na próxima oportunidade. */
    private boolean panelForced;
    private @Nullable TabletPanelPayload sentPanel;
    private int panelChangeVersion;
    private int panelDataVersion;
    private long panelTick;
    /** O último snapshot lido do nó escolhido, mostrado (só para ler) enquanto ele está descarregado. */
    private @Nullable RouterSnapshot lastKnown;

    TabletChosenNode(ServerPlayer viewer, int containerId) {
        this.viewer = viewer;
        this.containerId = containerId;
    }

    @Nullable NodeKey chosen() {
        return chosen;
    }

    EditTargetRef targetRef() {
        return targetRef;
    }

    /** Põe o alvo guardado ao reabrir o Tablet, logo depois de escolher o nó. */
    void restoreTarget(EditTargetRef target) {
        targetRef = target;
    }

    /** O nó escolhido, se há, é visível e está carregado (sem carregar chunk); senão {@code null}. */
    @Nullable RouterBlockEntity editedRouter() {
        NodeIndex.Entry entry = chosenEntry();
        return entry == null ? null : TabletNodes.loadedRouter(viewer.server, NodeIndex.get(viewer.server), entry);
    }

    /** A entrada do índice do nó escolhido, se ainda existe e o jogador a vê. */
    private NodeIndex.@Nullable Entry chosenEntry() {
        if (chosen == null) {
            return null;
        }
        MinecraftServer server = viewer.server;
        NodeIndex.Entry entry = NodeIndex.get(server).entry(chosen);
        return entry != null && TabletNodes.visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2),
                viewer.getUUID()) ? entry : null;
    }

    /** Ver {@link TabletMenu#chooseNode}. */
    boolean choose(@Nullable NodeKey key) {
        if (key != null) {
            MinecraftServer server = viewer.server;
            NodeIndex.Entry entry = NodeIndex.get(server).entry(key);
            if (entry == null || !TabletNodes.visible(entry, NetworkSavedData.get(server), viewer.hasPermissions(2),
                    viewer.getUUID())) {
                // Larga a escolha: o alvo que o cliente manda logo depois do nó não pode cair no nó anterior.
                switchTo(null);
                return false;
            }
        }
        if (!java.util.Objects.equals(key, chosen)) {
            switchTo(key);
        }
        panelForced = true;
        return true;
    }

    /** Troca o nó escolhido ({@code null}: nenhum), com o alvo de volta na presa e o painel a reenviar. */
    private void switchTo(@Nullable NodeKey key) {
        chosen = key;
        targetRef = EditTargetRef.ATTACHED;
        lastKnown = null;
        sentPanel = null;
        panelForced = true;
    }

    /** Confere o alvo no roteador: uma máquina que mudou de índice segue pela posição; um alvo que sumiu volta à presa. */
    EditTarget validTarget() {
        RouterBlockEntity router = editedRouter();
        if (router == null) {
            return targetRef.target();
        }
        EditTargetRef fresh = targetRef.refresh(router);
        if (!fresh.equals(targetRef)) {
            targetRef = fresh;
            panelForced = true;
        }
        return targetRef.target();
    }

    @Nullable EditTarget actionTarget() {
        boolean wasRemote = !targetRef.target().attached();
        EditTarget target = validTarget();
        return wasRemote && target.attached() ? null : target;
    }

    boolean selectTarget(EditTarget target, @Nullable BlockPos pos, @Nullable ResourceLocation block) {
        RouterBlockEntity router = editedRouter();
        if (router == null) {
            return false;
        }
        EditTargetRef resolved = EditTargetRef.resolve(router, target, pos, block);
        if (resolved == null) {
            return false;
        }
        validTarget();
        if (!resolved.equals(targetRef)) {
            targetRef = resolved;
            panelForced = true;
        }
        return true;
    }

    /** Uma edição pelos pacotes de face: o painel mostra o resultado na próxima oportunidade. */
    void editApplied() {
        panelForced = true;
    }

    /** Ver {@link TabletMenu#pollPanel}. */
    @Nullable TabletPanelPayload poll() {
        if (chosen == null) {
            return null;
        }
        MinecraftServer server = viewer.server;
        NodeIndex.Entry entry = chosenEntry();
        if (entry == null) {
            chosen = null;
            targetRef = EditTargetRef.ATTACHED;
            lastKnown = null;
            sentPanel = null;
            return new TabletPanelPayload(containerId, Optional.empty(), Optional.empty(), PanelAccess.READ_UNLOADED, -1, 0);
        }
        RouterBlockEntity router = TabletNodes.loadedRouter(server, NodeIndex.get(server), entry);
        long now = server.getTickCount();
        int dataVersion = NetworkSavedData.get(server).version();
        int changeVersion = router == null ? 0 : router.changeVersion();
        boolean due = panelForced || sentPanel == null || now - panelTick >= TabletMenu.SAMPLE_TICKS
                || changeVersion != panelChangeVersion || dataVersion != panelDataVersion;
        if (!due) {
            return null;
        }
        panelForced = false;
        panelTick = now;
        panelChangeVersion = changeVersion;
        panelDataVersion = dataVersion;
        PanelAccess access = PanelAccess.READ_UNLOADED;
        int distance = -1;
        int range = 0;
        if (router != null) {
            lastKnown = RouterSnapshot.capture(router, viewer, validTarget());
            access = TabletMenu.inReach(viewer, router) ? PanelAccess.EDIT : PanelAccess.READ_OUT_OF_REACH;
            range = Config.TIERS.get(router.tier()).range().get();
            if (router.getLevel() == viewer.level()) {
                distance = (int) Math.round(Math.sqrt(viewer.position().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))));
            }
        }
        TabletPanelPayload built = new TabletPanelPayload(containerId, Optional.of(chosen),
                Optional.ofNullable(lastKnown), access, distance, range);
        // Compara como o cliente recebe: só a distância mudando (o jogador andando) não reenvia.
        if (built.asSent().equals(sentPanel)) {
            return null;
        }
        sentPanel = built.asSent();
        return built;
    }
}
