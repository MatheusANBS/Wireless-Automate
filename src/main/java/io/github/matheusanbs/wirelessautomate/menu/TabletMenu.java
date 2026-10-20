package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletFlowsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletHeaderPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletLivePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu do Tablet de rede (sem slots). No cliente guarda o último {@link TabletSnapshot}; no
 * servidor monta o snapshot e aplica as ações da tela, validando tudo.
 *
 * <p>Sincronização (servidor): como no {@link RouterMenu}, só o menu aberto de cada jogador recebe
 * {@link #broadcastChanges()}, então nada roda com a tela fechada. O snapshot é remontado a cada
 * {@link #SAMPLE_TICKS} ticks (amostra de vazão e de estado dos nós), depois de uma ação ou
 * consulta da tela (no máximo {@link #FORCED_PER_SECOND} por segundo por jogador, o resto espera) e,
 * no máximo a cada {@link #REBUILD_TICKS} ticks, quando um dado salvo do índice de nós ou as redes
 * mudaram (chunks que carregam ou descarregam só aparecem na amostra seguinte). Uma montagem
 * ({@link TabletSnapshotBuilder}) percorre o índice uma vez (O(nós visíveis)) e sonda o motor uma vez por
 * amostra para saber quais nós têm destino cheio (as contagens dos filtros rápidos). O cabeçalho
 * (redes, grupos, facetas) e a página de nós vão em pacotes separados, cada um só se ficou
 * diferente do último enviado. Só com a aba Fluxos aberta, o {@link TabletFlowsPayload} sai a cada amostra
 * nova do amostrador, se mudou ({@link TabletFlows}).
 *
 * <p>Nó escolhido (servidor): o painel do Tablet mostra e edita um nó ({@link #chooseNode}). O menu é o
 * {@link RouterEditHost} desse nó, então os pacotes de face de sempre editam o roteador à distância
 * enquanto ele é visível, está carregado (sem carregar chunk) e ao alcance ({@link #inReach}). O
 * {@link TabletPanelPayload} (o {@link RouterSnapshot} do nó e o acesso) só sai com um nó escolhido: ao
 * escolher, quando a versão do roteador ou das redes muda, ao trocar o alvo, depois de uma edição e a cada
 * {@link #SAMPLE_TICKS} ticks (alcance e vazão), e só se ficou diferente do último enviado
 * ({@link TabletChosenNode}).
 *
 * <p>Quem vê o quê: as redes do jogador e as públicas (todas para operador nível 2); os grupos do
 * jogador (todos para operador); os nós que o jogador colocou ou que têm alguma face numa rede dele
 * (todos para operador).
 *
 * <p>As partes do servidor: a consulta ({@link TabletQuery}), a montagem ({@link TabletSnapshotBuilder}),
 * as regras de um nó ({@link TabletNodes}), o nó escolhido ({@link TabletChosenNode}), o perfil
 * ({@link TabletProfile}), as ações ({@link TabletMenuActions}), os Fluxos ({@link TabletFlows}), a árvore da Lista
 * ({@link TabletTree}), a visão por máquinas ({@link TabletMachines}) e o estado por jogador ({@link TabletPlayers}).
 */
public class TabletMenu extends AbstractContainerMenu implements RouterEditHost {
    /** Janela da amostra de vazão e estado, em ticks. */
    public static final int SAMPLE_TICKS = 20;
    /** Intervalo mínimo entre remontagens provocadas por mudanças no mundo. */
    public static final int REBUILD_TICKS = 5;
    /**
     * Remontagens pedidas pela tela (consulta, ação) por segundo e por jogador; guarda até um
     * segundo delas. Além disso, o pedido espera e é atendido assim que houver ficha ou na amostra.
     * As fichas sobrevivem a fechar e abrir o Tablet ({@link TabletPlayers}).
     */
    public static final int FORCED_PER_SECOND = 10;
    /** Colares em vários nós e desfazeres por segundo e por jogador (cada um mexe em até {@link #MAX_MOVE} nós). */
    public static final int BULK_PER_SECOND = 4;

    /**
     * O que o Tablet lembra: a aba (posição na ordem das abas da tela), a consulta, o nó escolhido e o alvo de edição
     * dele, a árvore da Lista e a visão por máquinas.
     */
    public record State(int tab, Query query, @Nullable NodeKey chosen, EditTargetRef target, TabletTree.Saved tree,
            TabletMachines.Saved machines) {
    }
    /** Teto de nós movidos por pacote. */
    public static final int MAX_MOVE = 1024;

    /** Ações da tela sobre redes e grupos ({@code TabletActionPayload}). */
    public enum Action {
        /** Texto: nome. */
        NETWORK_CREATE,
        /** Alvo: rede; texto: nome novo. */
        NETWORK_RENAME,
        /** Alvo: rede; valor: cor RGB. */
        NETWORK_COLOR,
        /** Alvo: rede; valor: 1 pública, 0 privada. */
        NETWORK_PUBLIC,
        NETWORK_REMOVE,
        /** Alvo: rede, que vira a rede ativa do jogador (a que o Vinculador usa). */
        NETWORK_USE,
        /** Texto: nome. */
        GROUP_CREATE,
        GROUP_RENAME,
        GROUP_REMOVE,
        /** Alvo: grupo; outro: rede. */
        GROUP_ADD_NETWORK,
        GROUP_REMOVE_NETWORK,
        /** Alvo: grupo; valor: 1 pausa, 0 retoma. */
        GROUP_PAUSE
    }

    private TabletSnapshot snapshot;
    private int version;
    /** Cliente: o último painel do nó escolhido recebido, ou {@code null}. */
    private @Nullable TabletPanelPayload panel;
    private int panelVersion;
    /** Cliente: os últimos Fluxos recebidos, ou {@code null}. */
    private @Nullable TabletFlowsPayload flowsPayload;
    /** Cliente: a aba e o nó escolhido que o servidor restaurou ao abrir. */
    private int restoredTab;
    private @Nullable NodeKey restoredNode;

    // Só no servidor (as partes são nulas no cliente).
    private final @Nullable ServerPlayer viewer;
    private final TabletQuery query = new TabletQuery();
    private final @Nullable TabletSnapshotBuilder builder;
    private final @Nullable TabletChosenNode node;
    private final @Nullable TabletProfile profile;
    private final @Nullable TabletMenuActions actions;
    private final @Nullable TabletFlows flows;
    private final @Nullable TabletTree tree;
    private final @Nullable TabletMachines machines;
    private int tab;
    private boolean forced;
    private @Nullable TabletSnapshot sent;
    /** Partes do último snapshot novo de {@link #pollSnapshot()} que mudaram, para o envio. */
    private boolean headerChanged;
    private boolean pageChanged;
    /** Na página mudaram só o estado e a vazão dos nós e das linhas de máquina ({@link TabletSnapshot.Page#sameExceptLive}). */
    private boolean liveOnly;

    /** Servidor. */
    public TabletMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        super(ModMenus.NETWORK_TABLET.get(), containerId);
        this.viewer = viewer;
        this.tree = new TabletTree(viewer);
        this.machines = new TabletMachines(viewer.server);
        this.builder = new TabletSnapshotBuilder(viewer, query, tree, machines);
        this.node = new TabletChosenNode(viewer, containerId);
        this.profile = new TabletProfile(viewer, containerId, query, builder);
        this.actions = new TabletMenuActions(this, viewer);
        this.flows = new TabletFlows(viewer, containerId);
        restore(TabletPlayers.saved(viewer.getUUID()));
        flows.tab(tab);
        this.snapshot = builder.build(true, actions.notice(), actions.noticeId());
        this.sent = snapshot;
    }

    /** Cliente: o snapshot inicial vem no buffer de abertura. */
    public TabletMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, TabletSnapshot.STREAM_CODEC.decode(buf), buf.readVarInt(),
                NODE_CODEC.decode(buf).orElse(null));
    }

    /** Cliente (e a captura de tela de desenvolvimento): só o snapshot. */
    public TabletMenu(int containerId, Inventory inventory, TabletSnapshot snapshot) {
        this(containerId, inventory, snapshot, 0, null);
    }

    private TabletMenu(int containerId, Inventory inventory, TabletSnapshot snapshot, int restoredTab,
            @Nullable NodeKey restoredNode) {
        super(ModMenus.NETWORK_TABLET.get(), containerId);
        this.viewer = null;
        this.builder = null;
        this.node = null;
        this.profile = null;
        this.actions = null;
        this.flows = null;
        this.tree = null;
        this.machines = null;
        this.snapshot = snapshot;
        this.restoredTab = restoredTab;
        this.restoredNode = restoredNode;
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<NodeKey>> NODE_CODEC =
            ByteBufCodecs.optional(NodeKey.STREAM_CODEC.<RegistryFriendlyByteBuf>cast());

    /**
     * Põe o estado de antes: a consulta, a aba e, se o nó ainda existe e o jogador o vê, o nó escolhido e o
     * alvo dele. Só no servidor, antes da primeira montagem.
     */
    private void restore(@Nullable State saved) {
        if (saved == null || viewer == null) {
            return;
        }
        setQuery(saved.query());
        tab = saved.tab();
        if (saved.chosen() != null && chooseNode(saved.chosen())) {
            node.restoreTarget(saved.target());
        }
        tree.restore(saved.tree());
        machines.restore(saved.machines());
    }

    /** Esquece o estado de todos os jogadores (o servidor parou). */
    public static void resetStates() {
        TabletPlayers.reset();
        TabletSample.reset();
    }

    /** O estado guardado do jogador, ou {@code null}. */
    public static @Nullable State savedState(UUID player) {
        return TabletPlayers.saved(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        IdleNetworkUndo.closed(player, this);
        if (flows != null) {
            flows.stop();
        }
        if (machines != null) {
            machines.stop();
        }
        if (viewer != null && !viewer.hasDisconnected()) {
            TabletPlayers.save(viewer.getUUID(), new State(tab, query.query(), node.chosen(), node.targetRef(),
                    tree.save(), machines.save()));
        }
    }

    /** Quantas abas a tela tem ({@code TabletScreen.Tab}); o servidor só guarda uma posição dentro delas. */
    public static final int TAB_COUNT = 5;
    /** A posição da aba Fluxos ({@code TabletScreen.Tab.FLOWS}): só com ela aberta o jogador olha o amostrador. */
    public static final int FLOWS_TAB = 2;
    /** A posição da aba Lista ({@code TabletScreen.Tab.LIST}), a que o clique numa máquina abre. */
    static final int LIST_TAB = 0;

    /** Servidor: a aba que a tela mostra. Devolve se o valor era de uma aba que existe. */
    public boolean setTab(int tab) {
        if (tab < 0 || tab >= TAB_COUNT) {
            return false;
        }
        this.tab = tab;
        if (flows != null) {
            flows.tab(tab);
        }
        return true;
    }

    /** Esquece o estado de um jogador (saiu do servidor). */
    public static void forget(UUID player) {
        TabletPlayers.forget(player);
    }

    @Override
    public FilterOrigin filterOrigin() {
        return FilterOrigin.TABLET;
    }

    /** Abre o Tablet para o jogador; o snapshot inicial vai no buffer de abertura. */
    public static void open(ServerPlayer player) {
        TabletMenu[] created = new TabletMenu[1];
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> {
            created[0] = new TabletMenu(containerId, inventory, player);
            return created[0];
        }, Component.translatable("item.wirelessautomate.network_tablet")),
                buf -> created[0].writeOpenData(buf));
    }

    /** Abre o Tablet na Lista, com o nó escolhido e o alvo de edição (o clique numa máquina). */
    public static void open(ServerPlayer player, NodeKey node, EditTargetRef target) {
        State saved = TabletPlayers.saved(player.getUUID());
        TabletPlayers.save(player.getUUID(), new State(LIST_TAB,
                saved == null ? TabletSnapshot.Query.DEFAULT : saved.query(), node, target,
                saved == null ? TabletTree.Saved.EMPTY : saved.tree(),
                saved == null ? TabletMachines.Saved.EMPTY : saved.machines()));
        open(player);
    }

    /** O que vai no buffer de abertura: o snapshot, a aba restaurada e o nó escolhido restaurado. */
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        TabletSnapshot.STREAM_CODEC.encode(buf, snapshot);
        buf.writeVarInt(tab);
        NODE_CODEC.encode(buf, Optional.ofNullable(chosenNode()));
    }

    /** O jogador tem um Tablet no inventário (mochila, barra, armadura ou mão secundária). */
    public static boolean hasTablet(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.NETWORK_TABLET.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * O Tablet que guarda o perfil: o da mão principal, o da mão secundária ou o primeiro do inventário (o
     * mesmo critério de {@link #hasTablet}); {@link ItemStack#EMPTY} sem nenhum.
     */
    public static ItemStack tabletStack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.NETWORK_TABLET.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.NETWORK_TABLET.get())) {
            return off;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.NETWORK_TABLET.get())) {
                return inventory.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * O roteador está ao alcance do Tablet: o alcance do próprio roteador pela config do tier
     * ({@code tiers.<tier>.range}; 0 = a dimensão inteira), medido do jogador; outra dimensão só se o
     * tier tem {@code crossDimension}. É a mesma regra que o motor usa para as rotas.
     */
    public static boolean inReach(ServerPlayer player, RouterBlockEntity router) {
        Config.TierValues tier = Config.TIERS.get(router.tier());
        if (router.getLevel() != player.level()) {
            return tier.crossDimension().get();
        }
        int range = tier.range().get();
        return range <= 0 || player.position().distanceToSqr(Vec3.atCenterOf(router.getBlockPos()))
                <= (double) range * range;
    }

    /** O jogador vê o roteador pelo Tablet (a regra de {@link TabletNodes#visible}); fora do índice, não. */
    public static boolean canSee(ServerPlayer player, RouterBlockEntity router) {
        Level level = router.getLevel();
        if (level == null) {
            return false;
        }
        MinecraftServer server = player.server;
        NodeIndex.Entry entry = NodeIndex.get(server).entry(NodeKey.of(level, router.getBlockPos()));
        return entry != null && TabletNodes.visible(entry, NetworkSavedData.get(server), player.hasPermissions(2),
                player.getUUID());
    }

    /** Servidor: a aba que a tela mostra. */
    public int tab() {
        return tab;
    }

    /** Cliente: a aba que o servidor restaurou ao abrir. */
    public int restoredTab() {
        return restoredTab;
    }

    /** Cliente: o nó escolhido que o servidor restaurou ao abrir, ou {@code null}. */
    public @Nullable NodeKey restoredNode() {
        return restoredNode;
    }

    public TabletSnapshot snapshot() {
        return snapshot;
    }

    /** Muda a cada snapshot recebido. */
    public int version() {
        return version;
    }

    public void applySnapshot(TabletSnapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    /** Cliente: cabeçalho novo ({@code TabletHeaderPayload}); a página que já estava fica. */
    public void applyHeader(TabletSnapshot header) {
        applySnapshot(header.withPage(snapshot.page()));
    }

    /** Cliente: página nova ({@code TabletPagePayload}). */
    public void applyPage(TabletSnapshot.Page page) {
        applySnapshot(snapshot.withPage(page));
    }

    /** Cliente: só o estado e a vazão dos nós e das linhas de máquina da página mudaram ({@code TabletLivePayload}). */
    public void applyLives(List<TabletSnapshot.Live> lives, List<TabletRows.MachineLive> machines) {
        applySnapshot(snapshot.withPage(snapshot.page().withLives(lives, machines)));
    }

    /** Cliente: o painel novo do nó escolhido ({@code TabletPanelPayload}). */
    public void applyPanel(TabletPanelPayload panel) {
        this.panel = panel;
        panelVersion++;
    }

    /** Cliente: o último painel recebido, ou {@code null} sem nó escolhido. */
    public @Nullable TabletPanelPayload panel() {
        return panel;
    }

    /** Muda a cada painel recebido. */
    public int panelVersion() {
        return panelVersion;
    }

    /** Cliente: os Fluxos novos ({@code TabletFlowsPayload}). */
    public void applyFlows(TabletFlowsPayload flows) {
        this.flowsPayload = flows;
    }

    /** Cliente: os últimos Fluxos recebidos, ou {@code null}. */
    public @Nullable TabletFlowsPayload flows() {
        return flowsPayload;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** No servidor: o jogador ainda tem um Tablet. */
    @Override
    public boolean stillValid(Player player) {
        return viewer == null || hasTablet(player);
    }

    // ------------------------------------------------------------------ sincronização

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        for (CustomPacketPayload payload : pollPayloads()) {
            send(payload);
        }
    }

    /**
     * Os pacotes que saem agora, na ordem (o cabeçalho antes da página, porque os nós apontam para as redes dele):
     * cabeçalho, página ou só o estado e a vazão dela, painel e Fluxos, cada um só se mudou, já marcados como
     * enviados. Só no servidor; público para o bench, que mede os bytes.
     */
    public List<CustomPacketPayload> pollPayloads() {
        List<CustomPacketPayload> payloads = new ArrayList<>(2);
        TabletSnapshot changed = pollSnapshot();
        if (changed != null) {
            if (headerChanged) {
                payloads.add(new TabletHeaderPayload(containerId, changed));
            }
            if (pageChanged) {
                payloads.add(liveOnly ? new TabletLivePayload(containerId, changed.page().lives(), changed.page().rowLives())
                        : new TabletPagePayload(containerId, changed.page()));
            }
        }
        TabletPanelPayload panelNow = pollPanel();
        if (panelNow != null) {
            payloads.add(panelNow);
        }
        TabletFlowsPayload flowsNow = pollFlows();
        if (flowsNow != null) {
            payloads.add(flowsNow);
        }
        return payloads;
    }

    /**
     * Os Fluxos de uma amostra nova, se a aba Fluxos está aberta e eles mudaram desde o último enviado; senão
     * {@code null}. Só no servidor; público para os GameTests.
     */
    public @Nullable TabletFlowsPayload pollFlows() {
        return flows == null ? null : flows.poll();
    }

    private void send(CustomPacketPayload payload) {
        // Jogadores falsos (GameTests) não negociam os canais do mod.
        if (viewer != null && viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    /**
     * Snapshot novo, se é hora de remontar e ele mudou desde o último enviado, ou {@code null}. Marca
     * o novo como enviado e guarda quais partes mudaram (cabeçalho, página). Só no servidor; público
     * para os GameTests.
     */
    public @Nullable TabletSnapshot pollSnapshot() {
        if (viewer == null) {
            return null;
        }
        MinecraftServer server = viewer.server;
        long now = server.getTickCount();
        boolean sample = builder.sampleDue(now);
        // Só dados salvos do índice: um chunk que carrega ou descarrega espera a próxima amostra.
        boolean worldChanged = builder.worldChanged(server);
        boolean rebuild = sample || (worldChanged && builder.rebuildAllowed(now));
        if (!rebuild && forced) {
            // Pedido da tela: no máximo FORCED_PER_SECOND por segundo por jogador; o resto espera.
            rebuild = TabletPlayers.takeForced(viewer.getUUID(), now);
        }
        if (!rebuild) {
            return null;
        }
        forced = false;
        TabletSnapshot built = builder.build(sample, actions.notice(), actions.noticeId());
        snapshot = built;
        headerChanged = sent == null || !built.sameHeader(sent);
        pageChanged = sent == null || !built.page().equals(sent.page());
        // Só o estado e a vazão dos nós mudaram (quase toda amostra): vai o pacote pequeno.
        liveOnly = pageChanged && sent != null && built.page().sameExceptLive(sent.page());
        if (!headerChanged && !pageChanged) {
            return null;
        }
        sent = built;
        return built;
    }

    /** Uma ação ou consulta da tela pede uma remontagem (dentro das fichas de {@link #FORCED_PER_SECOND}). */
    void requestRebuild() {
        forced = true;
    }

    // ------------------------------------------------------------------ nó escolhido (servidor)

    @Override
    public int containerId() {
        return containerId;
    }

    /** O nó escolhido, se há, é visível e está carregado (sem carregar chunk); senão {@code null}. */
    @Override
    public @Nullable RouterBlockEntity editedRouter() {
        return node == null ? null : node.editedRouter();
    }

    /** Nó escolhido, visível, carregado e ao alcance, e o jogador ainda com o Tablet. */
    @Override
    public boolean canEdit(ServerPlayer player) {
        RouterBlockEntity router = editedRouter();
        return router != null && stillValid(player) && inReach(player, router);
    }

    /**
     * Escolhe o nó do painel ({@code null}: nenhum). Recusa um nó que não está no índice ou que o jogador
     * não vê, e larga a escolha que havia, para o alvo pedido logo depois não valer no nó anterior. O alvo de
     * edição volta para a presa e o painel é enviado de novo. Devolve se escolheu.
     */
    public boolean chooseNode(@Nullable NodeKey key) {
        return viewer != null && node.choose(key);
    }

    /** O nó escolhido, ou {@code null}. Só no servidor. */
    public @Nullable NodeKey chosenNode() {
        return node == null ? null : node.chosen();
    }

    @Override
    public EditTargetRef targetRef() {
        return node == null ? EditTargetRef.ATTACHED : node.targetRef();
    }

    /** Confere o alvo no roteador: uma máquina que mudou de índice segue pela posição; um alvo que sumiu volta à presa. */
    @Override
    public EditTarget validTarget() {
        return node == null ? EditTargetRef.ATTACHED.target() : node.validTarget();
    }

    @Override
    public @Nullable EditTarget actionTarget() {
        return node == null ? EditTargetRef.ATTACHED.target() : node.actionTarget();
    }

    @Override
    public boolean selectTarget(EditTarget target, @Nullable BlockPos pos, @Nullable ResourceLocation block) {
        return node != null && node.selectTarget(target, pos, block);
    }

    /** Uma edição pelos pacotes de face: o painel mostra o resultado na próxima oportunidade. */
    @Override
    public void editApplied() {
        if (node != null) {
            node.editApplied();
        }
    }

    /**
     * O painel novo do nó escolhido, se é hora (pedido, versão do roteador ou das redes mudou, ou passaram
     * {@link #SAMPLE_TICKS} ticks) e ele ficou diferente do último enviado; senão {@code null}. Marca como
     * enviado. Nó carregado: o snapshot de agora, com acesso de edição ao alcance e de leitura fora dele;
     * descarregado: o último lido (vazio se nunca houve). Um nó que sumiu ou deixou de ser visível larga
     * a escolha e manda um painel sem nó. Só no servidor; público para os GameTests.
     */
    public @Nullable TabletPanelPayload pollPanel() {
        return viewer == null ? null : node.poll();
    }

    // ------------------------------------------------------------------ perfil (servidor)

    /** Servidor: o último colar ainda pode ser desfeito. */
    public boolean canUndo() {
        return profile != null && profile.canUndo();
    }

    /** Servidor: o texto do último aviso de colar ou desfazer mandado ao jogador, ou {@code null}. */
    public @Nullable Component lastNotice() {
        return profile == null ? null : profile.lastNotice();
    }

    /**
     * Uma ação do perfil, validada: o menu aberto e o Tablet no inventário, e para cada nó, na ordem, que o
     * jogador o vê, que está carregado (sem carregar chunk) e ao alcance; a regra das redes é a do
     * {@link PresetApplier}. Colar deixa a origem do perfil de fora. Devolve quantos nós a ação atingiu (1 para
     * copiar e esquecer), ou -1 se recusou.
     */
    public int profile(TabletProfilePayload.Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine,
            List<NodeKey> keys) {
        if (viewer == null || keys.size() > TabletProfilePayload.MAX_KEYS || !stillValid(viewer)) {
            return -1;
        }
        int result = profile.run(action, key, machine, keys);
        if (result >= 0) {
            forced = true;
        }
        return result;
    }

    // ------------------------------------------------------------------ ações (servidor)

    /** Uma ação na árvore da Lista ({@link TabletTree#apply}); a que vale refaz a página. Devolve se valeu. */
    public boolean tree(TabletTreePayload.Action action, NodeKey key, int group) {
        boolean done = tree != null && tree.apply(action, key, group);
        forced |= done;
        return done;
    }

    /**
     * Troca a visão da Lista ou muda um bloco na visão por máquinas ({@link TabletMachines#apply}); a que vale refaz a
     * página.
     */
    public boolean machines(TabletMachinesPayload.Action action, Optional<ResourceLocation> block) {
        boolean done = machines != null && machines.apply(action, block);
        forced |= done;
        return done;
    }

    /** A visão por máquinas; {@code null} no cliente. Público para os GameTests. */
    public @Nullable TabletMachines machinesState() {
        return machines;
    }

    /** A árvore da Lista; {@code null} no cliente. Público para os GameTests. */
    public @Nullable TabletTree treeState() {
        return tree;
    }

    /** A tela mudou a busca, as etiquetas, os filtros ou o tipo. */
    public void setQuery(Query query) {
        this.query.set(query);
        forced = true;
    }

    public Query query() {
        return query.query();
    }

    /**
     * Põe os nós numa rede: todas as faces (presa, perfis e exceções) do tipo {@code type} ou, vazio,
     * de todos os tipos carregados (as faces de um tipo cujo mod saiu ficam como estão); {@code network}
     * vazio tira da rede. Só nós visíveis ao jogador e carregados
     * (a rede é guardada no roteador); os descarregados ficam de fora e são contados no aviso. Uma face
     * numa rede que o jogador não gerencia ({@link WaNetwork#canManage}: dono ou operador; uma rede
     * pública de outro dono também não) fica como está e é contada no aviso; faces sem rede ou em rede
     * apagada vão. Devolve quantos mudaram, ou -1 se o pedido foi recusado.
     */
    public int moveNodes(List<NodeKey> keys, Optional<ResourceType> type, Optional<UUID> network) {
        return viewer == null ? -1 : actions.moveNodes(keys, type, network);
    }

    /**
     * Abre a tela do roteador à distância: nó visível, carregado e ao alcance ({@link #inReach}).
     * Devolve se abriu.
     */
    public boolean openNode(NodeKey key) {
        return viewer != null && actions.openNode(key);
    }

    /** Uma ação sobre redes ou grupos, validada. Devolve se foi aplicada. */
    public boolean apply(Action action, Optional<UUID> target, Optional<UUID> other, String text, int value) {
        if (viewer == null) {
            return false;
        }
        boolean done = actions.apply(action, target, other, text, value);
        forced = true;
        return done;
    }
}
