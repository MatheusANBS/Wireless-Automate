package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import io.github.matheusanbs.wirelessautomate.menu.CardFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.IdleNetworkUndo;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerOpenClient;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RemoteRouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterEditHost;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.IdleNetworkFace;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Registro dos pacotes do mod e os handlers. Código comum: os handlers do cliente só mexem no
 * {@link RouterMenu} e no {@link FilterMenu}, sem classes de tela.
 *
 * <p>Os handlers rodam na thread principal (o padrão do {@link PayloadRegistrar} é
 * {@code HandlerThread.MAIN}). Os de edição de face só agem se o jogador está com um {@link RouterEditHost}
 * aberto ({@code containerId} igual e {@link RouterEditHost#canEdit}: a tela do roteador ou outro menu) e
 * validam os valores; depois de aplicar, o host é avisado ({@link RouterEditHost#editApplied()}) e, na tela
 * do roteador, o {@link RouterMenu#broadcastChanges()} do próprio menu manda o snapshot novo. A tela
 * de filtro segue a mesma regra com o {@link FilterMenu} e a {@link FilterViewPayload}.
 */
public final class ModPayloads {
    /** Versão do protocolo; mude quando um payload mudar de formato. */
    public static final String VERSION = "30";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(RouterSnapshotPayload.TYPE, RouterSnapshotPayload.STREAM_CODEC,
                ModPayloads::onSnapshot);
        registrar.playToClient(RouterNetworksPayload.TYPE, RouterNetworksPayload.STREAM_CODEC,
                ModPayloads::onNetworks);
        registrar.playToClient(RouterThroughputPayload.TYPE, RouterThroughputPayload.STREAM_CODEC,
                ModPayloads::onThroughput);
        registrar.playToServer(SetFacePayload.TYPE, SetFacePayload.STREAM_CODEC,
                (payload, context) -> handleSetFace(serverPlayer(context), payload));
        registrar.playToServer(FaceActionPayload.TYPE, FaceActionPayload.STREAM_CODEC,
                (payload, context) -> handleFaceAction(serverPlayer(context), payload));
        registrar.playToServer(SetNetworkPayload.TYPE, SetNetworkPayload.STREAM_CODEC,
                (payload, context) -> handleSetNetwork(serverPlayer(context), payload));
        registrar.playToServer(RenameRouterPayload.TYPE, RenameRouterPayload.STREAM_CODEC,
                (payload, context) -> handleRename(serverPlayer(context), payload));
        registrar.playToClient(FilterViewPayload.TYPE, FilterViewPayload.STREAM_CODEC, ModPayloads::onFilterView);
        registrar.playToServer(OpenFilterPayload.TYPE, OpenFilterPayload.STREAM_CODEC,
                (payload, context) -> handleOpenFilter(serverPlayer(context), payload));
        registrar.playToServer(EditFilterPayload.TYPE, EditFilterPayload.STREAM_CODEC,
                (payload, context) -> handleEditFilter(serverPlayer(context), payload));
        registrar.playToServer(SelectFacePayload.TYPE, SelectFacePayload.STREAM_CODEC,
                (payload, context) -> handleSelectFace(serverPlayer(context), payload));
        // Roteador de alcance: alvo de edição, voltar ao perfil e desvincular pela tela.
        registrar.playToServer(SelectTargetPayload.TYPE, SelectTargetPayload.STREAM_CODEC,
                (payload, context) -> handleSelectTarget(serverPlayer(context), payload));
        registrar.playToServer(RevertMachinePayload.TYPE, RevertMachinePayload.STREAM_CODEC,
                (payload, context) -> handleRevertMachine(serverPlayer(context), payload));
        registrar.playToServer(UnlinkMachinePayload.TYPE, UnlinkMachinePayload.STREAM_CODEC,
                (payload, context) -> handleUnlinkMachine(serverPlayer(context), payload));
        registrar.playToServer(RenameTargetPayload.TYPE, RenameTargetPayload.STREAM_CODEC,
                (payload, context) -> handleRenameTarget(serverPlayer(context), payload));
        registrar.playToServer(UpdateMachinePayload.TYPE, UpdateMachinePayload.STREAM_CODEC,
                (payload, context) -> handleUpdateMachine(serverPlayer(context), payload));
        registrar.playToServer(ClearIdleNetworksPayload.TYPE, ClearIdleNetworksPayload.STREAM_CODEC,
                (payload, context) -> handleClearIdleNetworks(serverPlayer(context), payload));
        // Ingrediente fantasma do JEI (compat/jei); registrado sempre, com ou sem o JEI.
        registrar.playToServer(AddFilterEntryPayload.TYPE, AddFilterEntryPayload.STREAM_CODEC,
                (payload, context) -> handleAddFilterEntry(serverPlayer(context), payload));
        registrar.playToServer(FilterEntriesPayload.TYPE, FilterEntriesPayload.STREAM_CODEC,
                (payload, context) -> handleFilterEntries(serverPlayer(context), payload));
        registrar.playToServer(CycleConfiguratorTypePayload.TYPE, CycleConfiguratorTypePayload.STREAM_CODEC,
                (payload, context) -> handleCycleConfiguratorType(serverPlayer(context), payload));
        registrar.playToServer(ConfiguratorWheelPayload.TYPE, ConfiguratorWheelPayload.STREAM_CODEC,
                (payload, context) -> handleConfiguratorWheel(serverPlayer(context), payload));
        // Tela do Vinculador (handlers em LinkerActions e LinkerMenu).
        registrar.playToClient(LinkerSnapshotPayload.TYPE, LinkerSnapshotPayload.STREAM_CODEC, LinkerMenu::onSnapshot);
        registrar.playToServer(LinkerActionPayload.TYPE, LinkerActionPayload.STREAM_CODEC,
                (payload, context) -> LinkerActions.handle(serverPlayer(context), payload));
        registrar.playToClient(LinkerOpenListPayload.TYPE, LinkerOpenListPayload.STREAM_CODEC,
                (payload, context) -> LinkerOpenClient.show(payload));
        registrar.playToServer(LinkerOpenPickPayload.TYPE, LinkerOpenPickPayload.STREAM_CODEC,
                (payload, context) -> LinkerOpen.pick(serverPlayer(context), payload.machine(), payload.router()));
        // Telas dos armazenamentos (handlers no StorageListMenu e no StorageScalarMenu).
        registrar.playToClient(StorageEntriesPayload.TYPE, StorageEntriesPayload.STREAM_CODEC, StorageListMenu::onEntries);
        registrar.playToServer(StorageActionPayload.TYPE, StorageActionPayload.STREAM_CODEC,
                (payload, context) -> StorageListMenu.handle(serverPlayer(context), payload));
        registrar.playToClient(ScalarStatePayload.TYPE, ScalarStatePayload.STREAM_CODEC, StorageScalarMenu::onState);
        // Editor de receitas (/wa recipes): handlers no RecipeEditorMenu e no RecipeEditorActionPayload.
        registrar.playToClient(RecipeEditorStatePayload.TYPE, RecipeEditorStatePayload.STREAM_CODEC,
                RecipeEditorMenu::onState);
        registrar.playToServer(RecipeEditorActionPayload.TYPE, RecipeEditorActionPayload.STREAM_CODEC,
                (payload, context) -> RecipeEditorActionPayload.handle(serverPlayer(context), payload));
        registrar.playToClient(PanelNoticePayload.TYPE, PanelNoticePayload.STREAM_CODEC,
                (payload, context) -> PanelNotices.show(payload));
        // Tablet de rede (packet/TabletPayloads).
        TabletPayloads.register(registrar);
        // Tela do Núcleo de Ressonância (packet/ResonatorPayloads).
        ResonatorPayloads.register(registrar);
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    // Cliente.

    private static void onSnapshot(RouterSnapshotPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null) {
            menu.applySnapshotBody(payload.snapshot());
        }
    }

    private static void onNetworks(RouterNetworksPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null) {
            menu.applyNetworks(payload.networks());
        }
    }

    private static void onThroughput(RouterThroughputPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null && payload.perType().length == ResourceType.values().length) {
            menu.applyThroughput(payload.perType());
        }
    }

    private static void onFilterView(FilterViewPayload payload, IPayloadContext context) {
        if (context.player() != null && context.player().containerMenu instanceof FilterMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applyView(payload.view());
        }
    }

    private static @Nullable RouterMenu openMenu(@Nullable Player player, int containerId) {
        return player != null && player.containerMenu instanceof RouterMenu menu && menu.containerId == containerId
                ? menu
                : null;
    }

    // Servidor. Públicos para os GameTests; devolvem se aplicaram.

    /**
     * Configura uma face do alvo de edição da tela (a presa, o perfil de um grupo ou uma máquina
     * remota). Recusa químicos sem o Mekanism, limita a prioridade e ignora a ação se o alvo deixou de
     * existir ({@link RouterEditHost#actionTarget()}).
     */
    public static boolean handleSetFace(@Nullable ServerPlayer player, SetFacePayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || !LoadedTypes.contains(payload.resource())) {
            return false;
        }
        RouterBlockEntity router = host.editedRouter();
        EditTarget target = host.actionTarget();
        if (target == null) {
            return false;
        }
        int priority = Mth.clamp(payload.priority(), RouterMenu.MIN_PRIORITY, RouterMenu.MAX_PRIORITY);
        router.configureFace(target, payload.resource(), payload.face(), payload.mode(), priority, payload.redstone());
        host.editApplied();
        return true;
    }

    /**
     * Limpar (as faces do tipo, ou de todos os carregados, em Nenhum) e Colar (uma face sobre outra do mesmo tipo)
     * no alvo de edição, numa remontagem só. Devolve se algo mudou.
     */
    public static boolean handleFaceAction(@Nullable ServerPlayer player, FaceActionPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || !LoadedTypes.contains(payload.resource())) {
            return false;
        }
        RouterBlockEntity router = host.editedRouter();
        EditTarget target = host.actionTarget();
        if (target == null) {
            return false;
        }
        boolean changed = switch (payload.action()) {
            case CLEAR -> router.clearFaces(target,
                    payload.allTypes() ? LoadedTypes.LIST : List.of(payload.resource())) > 0;
            case PASTE -> router.pasteFace(target, payload.resource(), payload.from(), payload.to());
        };
        host.editApplied();
        return changed;
    }

    /** Manda o aviso do painel; jogadores falsos (GameTests, mods de automação) não negociam os canais do mod. */
    public static void sendNotice(ServerPlayer player, PanelNoticePayload notice) {
        if (player.connection != null && player.connection.hasChannel(notice)) {
            PacketDistributor.sendToPlayer(player, notice);
        }
    }

    /**
     * Troca o alvo de edição da tela. Um grupo precisa existir com o mesmo bloco; uma máquina, estar no
     * mesmo índice com a mesma posição; os dois só com o Upgrade de Alcance valendo. Um alvo inválido é
     * ignorado (o menu fica no que estava).
     */
    public static boolean handleSelectTarget(@Nullable ServerPlayer player, SelectTargetPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null) {
            return false;
        }
        boolean selected = host.selectTarget(new EditTarget(payload.kind(), payload.id()),
                payload.machinePos().orElse(null), payload.block().orElse(null));
        if (selected) {
            host.editApplied();
        }
        return selected;
    }

    /** Voltar ao perfil: tira a exceção da máquina remota {@code index}, se ela ainda está na posição {@code pos}. */
    public static boolean handleRevertMachine(@Nullable ServerPlayer player, RevertMachinePayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || !sameMachine(host.editedRouter(), payload.index(), payload.pos())) {
            return false;
        }
        boolean reverted = host.editedRouter().revertToProfile(payload.index());
        host.editApplied();
        return reverted;
    }

    /**
     * Desvincula a máquina remota {@code index}, se ela ainda está na posição {@code pos}; os cartões de
     * um grupo que esvazia caem no roteador. A máquina presa não tem índice e não sai por aqui.
     */
    public static boolean handleUnlinkMachine(@Nullable ServerPlayer player, UnlinkMachinePayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || !sameMachine(host.editedRouter(), payload.index(), payload.pos())) {
            return false;
        }
        boolean unlinked = host.editedRouter().unlinkMachine(payload.index());
        // Os índices depois dela mudaram: o alvo segue pela posição, ou volta para a presa.
        host.validTarget();
        host.editApplied();
        return unlinked;
    }

    /**
     * Atualizar: passa para o grupo do bloco novo a máquina trocada do índice (conferida pela posição) ou as
     * trocadas do grupo (conferido pelo bloco). Depois, uma máquina escolhida continua, e um grupo escolhido
     * que só trocou de bloco também ({@link EditTargetRef#refresh} o segue pelo id); um que saiu dá lugar ao
     * grupo de destino da primeira máquina atualizada.
     */
    public static boolean handleUpdateMachine(@Nullable ServerPlayer player, UpdateMachinePayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null) {
            return false;
        }
        RouterBlockEntity router = host.editedRouter();
        List<Integer> indices = updateIndices(router, payload);
        EditTargetRef before = host.targetRef();
        RemoteMachines.Regroup result = indices.isEmpty() ? RemoteMachines.Regroup.EMPTY : router.updateReplaced(indices);
        if (result.isEmpty()) {
            return false;
        }
        if (before.target().kind() == EditTarget.Kind.GROUP
                && router.remoteMachines().group(before.target().id()) == null) {
            int destination = result.destinations().values().iterator().next();
            MachineGroup group = router.remoteMachines().group(destination);
            host.selectTarget(EditTarget.group(destination), null, group == null ? null : group.block());
        } else {
            host.validTarget();
        }
        host.editApplied();
        return true;
    }

    /**
     * Os índices que o Atualizar pede: a máquina, se ainda está na posição; ou todas as do grupo, se ele
     * ainda é do bloco visto (o roteador ignora as que não estão trocadas).
     */
    private static List<Integer> updateIndices(RouterBlockEntity router, UpdateMachinePayload payload) {
        if (payload.kind() == EditTarget.Kind.MACHINE) {
            return payload.pos().isPresent() && sameMachine(router, payload.id(), payload.pos().get())
                    ? List.of(payload.id())
                    : List.of();
        }
        MachineGroup group = payload.kind() == EditTarget.Kind.GROUP && router.rangeActive()
                ? router.remoteMachines().group(payload.id())
                : null;
        if (group == null || payload.block().isEmpty() || !group.block().equals(payload.block().get())) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        List<RemoteMachine> machines = router.remoteMachines().machines();
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).group() == group.id()) {
                indices.add(i);
            }
        }
        return indices;
    }

    /**
     * "Tirar a rede das faces sem modo" no alvo de edição (um grupo ou uma máquina com exceção; na presa não
     * faz nada) ou, com {@code undo}, o Desfazer dele. Com alguma face limpa, o aviso leva o Desfazer, que só
     * vale uma vez, no mesmo menu, roteador e alvo ({@link IdleNetworkUndo}), e devolve a rede só às faces que
     * continuam sem modo e sem rede, numa rede que ainda existe; sem nenhuma face devolvida, não há aviso.
     * Devolve quantas faces mudaram, ou -1 se recusou.
     */
    public static int handleClearIdleNetworks(@Nullable ServerPlayer player, ClearIdleNetworksPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        EditTarget target = host == null ? null : host.actionTarget();
        if (target == null) {
            return -1;
        }
        RouterBlockEntity router = host.editedRouter();
        if (payload.undo()) {
            List<IdleNetworkFace> faces = IdleNetworkUndo.take(player, host);
            if (faces == null) {
                return -1;
            }
            NetworkSavedData data = NetworkSavedData.get(player.server);
            int restored = router.restoreIdleNetworks(target,
                    faces.stream().filter(face -> data.network(face.network()) != null).toList());
            host.editApplied();
            if (restored > 0) {
                sendNotice(player, PanelNoticePayload.plain(
                        Component.translatable("gui.wirelessautomate.panel.notice.idle_restored", restored)));
            }
            return restored;
        }
        IdleNetworkUndo.forget(player.getUUID());
        List<IdleNetworkFace> cleared = router.clearIdleNetworks(target);
        if (cleared.isEmpty()) {
            return 0;
        }
        IdleNetworkUndo.remember(player, host, cleared);
        host.editApplied();
        sendNotice(player, idleClearedNotice(host.containerId(), cleared.size()));
        return cleared.size();
    }

    /** O aviso do limpar: quantas faces perderam a rede, com o Desfazer pelo {@link ClearIdleNetworksPayload}. */
    public static PanelNoticePayload idleClearedNotice(int containerId, int count) {
        return new PanelNoticePayload(Component.translatable("gui.wirelessautomate.panel.notice.idle_cleared", count),
                PanelNoticePayload.Undo.RESTORE_IDLE_NETWORKS, containerId, ResourceType.ITEM, Direction.UP);
    }

    /** Com o Upgrade de Alcance valendo (como o {@code SelectTarget}), a máquina do índice está em {@code pos}. */
    private static boolean sameMachine(RouterBlockEntity router, int index, net.minecraft.core.BlockPos pos) {
        if (!router.rangeActive()) {
            return false;
        }
        RemoteMachine machine = router.remoteMachines().machine(index);
        return machine != null && machine.pos().equals(pos);
    }

    /**
     * Põe uma face do alvo de edição da tela (a presa, o perfil de um grupo ou uma máquina remota) numa
     * rede. Só aceita tipo carregado, rede que existe e que o jogador pode usar (dono ou operador nível 2,
     * a mesma regra do Configurador), ou vazio para tirar a face da rede. Ignora a ação se o alvo deixou
     * de existir ({@link RouterEditHost#actionTarget()}).
     */
    public static boolean handleSetNetwork(@Nullable ServerPlayer player, SetNetworkPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || !LoadedTypes.contains(payload.resource())) {
            return false;
        }
        RouterBlockEntity router = host.editedRouter();
        EditTarget target = host.actionTarget();
        if (target == null) {
            return false;
        }
        Optional<UUID> id = payload.network();
        if (id.isEmpty()) {
            router.setFaceNetwork(target, payload.resource(), payload.face(), null);
            host.editApplied();
            return true;
        }
        WaNetwork network = NetworkSavedData.get(player.server).network(id.get());
        if (network == null || !canUse(player, network)) {
            return false;
        }
        router.setFaceNetwork(target, payload.resource(), payload.face(), network.id());
        host.editApplied();
        return true;
    }

    /** Dá nome ao nó. Recusa nomes maiores que {@link RenameRouterPayload#MAX_LENGTH}. */
    public static boolean handleRename(@Nullable ServerPlayer player, RenameRouterPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || payload.name().strip().length() > RenameRouterPayload.MAX_LENGTH) {
            return false;
        }
        host.editedRouter().setName(payload.name());
        host.editApplied();
        return true;
    }

    /**
     * Dá nome ao grupo, rótulo à máquina remota ou nome à máquina presa. Recusa nomes maiores que
     * {@link RenameRouterPayload#MAX_LENGTH} e, em grupo e máquina, o roteador sem o Upgrade de Alcance e, numa
     * máquina, a posição que não bate com a do índice. A presa não exige o Upgrade nem posição.
     */
    public static boolean handleRenameTarget(@Nullable ServerPlayer player, RenameTargetPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || payload.name().strip().length() > RenameRouterPayload.MAX_LENGTH) {
            return false;
        }
        RouterBlockEntity router = host.editedRouter();
        boolean attached = payload.kind() == EditTarget.Kind.ATTACHED;
        if (!attached && (!router.rangeActive() || payload.kind() == EditTarget.Kind.MACHINE
                && (payload.machinePos().isEmpty()
                || !sameMachine(router, payload.id(), payload.machinePos().get())))) {
            return false;
        }
        boolean renamed = router.renameTarget(new EditTarget(payload.kind(), payload.id()), payload.name());
        if (renamed) {
            host.editApplied();
        }
        return renamed;
    }

    /**
     * A tela mudou de aba ou de face: os slots de cartão do menu passam a mostrar os daquela face e
     * tipo. Qualquer tipo é aceito (energia e químicos deixam os slots inativos).
     */
    public static boolean handleSelectFace(@Nullable ServerPlayer player, SelectFacePayload payload) {
        RouterMenu menu = openMenu(player, payload.containerId());
        if (router(player, payload.containerId()) == null || menu == null) {
            return false;
        }
        menu.select(payload.resource(), payload.face());
        return true;
    }

    /**
     * Shift + roda do mouse com o Configurador na mão principal: avança ou volta o tipo que ele cola
     * e mostra o novo na action bar. Recusa direção 0 e outra coisa na mão.
     */
    public static boolean handleCycleConfiguratorType(@Nullable ServerPlayer player,
            CycleConfiguratorTypePayload payload) {
        if (player == null || payload.direction() == 0) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ConfiguratorItem)) {
            return false;
        }
        ResourceType type = ConfiguratorItem.cycleType(stack, payload.direction());
        player.displayClientMessage(Component.translatable("item.wirelessautomate.configurator.type",
                ConfiguratorItem.typeName(type)), true);
        return true;
    }

    /**
     * Escolha na roda do Configurador: grava o tipo colado e o mostra na action bar. Recusa outra coisa
     * na mão principal e um tipo que não está carregado.
     */
    public static boolean handleConfiguratorWheel(@Nullable ServerPlayer player, ConfiguratorWheelPayload payload) {
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        ResourceType type = payload.pasteType().orElse(null);
        if (!(stack.getItem() instanceof ConfiguratorItem) || (type != null && !LoadedTypes.LIST.contains(type))) {
            return false;
        }
        ConfiguratorItem.setType(stack, type);
        player.displayClientMessage(Component.translatable("item.wirelessautomate.configurator.type",
                ConfiguratorItem.typeName(type)), true);
        return true;
    }

    // Tela de filtro.

    /**
     * Botão Editar da tela do roteador: abre o filtro da face no lugar dela. Itens, fluidos e, com o
     * Mekanism, químicos (energia não usa filtro).
     */
    public static boolean handleOpenFilter(@Nullable ServerPlayer player, OpenFilterPayload payload) {
        RouterEditHost host = host(player, payload.containerId());
        if (host == null || !RouterFaceFilterTarget.hasFilter(payload.resource())) {
            return false;
        }
        if (host.actionTarget() == null) {
            return false;
        }
        new RouterFaceFilterTarget(host.editedRouter(), payload.resource(), payload.face(), host.targetRef(),
                host.filterOrigin()).open(player);
        return true;
    }

    /** Uma edição no filtro da tela aberta. Devolve se a edição foi aceita (mesmo sem mudar nada). */
    public static boolean handleEditFilter(@Nullable ServerPlayer player, EditFilterPayload payload) {
        FilterMenu menu = player != null && player.containerMenu instanceof FilterMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || menu.target() == null || !menu.stillValid(player)) {
            return false;
        }
        FilterTarget target = menu.target();
        Filter filter = target.filter();
        switch (payload.op()) {
            case ADD_TAG -> {
                ResourceLocation tag = parseTag(payload.text());
                if (tag == null) {
                    return false;
                }
                if (target.type() == ResourceType.CHEMICAL) {
                    // Químicos não têm tags no filtro: o texto é o id de um químico.
                    if (!Chemicals.exists(tag)) {
                        return false;
                    }
                    target.setFilter(filter.withEntry(new FilterEntry.ChemicalEntry(tag, 0)));
                } else {
                    target.setFilter(filter.withEntry(new FilterEntry.TagEntry(tag, 0)));
                }
            }
            case ADD_MOD -> {
                String mod = parseMod(payload.text());
                if (mod == null) {
                    return false;
                }
                target.setFilter(filter.withEntry(new FilterEntry.ModEntry(mod, 0)));
            }
            case REMOVE -> {
                if (!validIndex(filter, payload.index())) {
                    return false;
                }
                target.setFilter(filter.withoutEntry(payload.index()));
            }
            case SET_STOCK -> {
                if (!validIndex(filter, payload.index()) || payload.value() < 0
                        || payload.value() > Integer.MAX_VALUE) {
                    return false;
                }
                target.setFilter(filter.withStock(payload.index(), payload.value()));
            }
            case SET_LIST_MODE -> {
                if (payload.value() != 0 && payload.value() != 1) {
                    return false;
                }
                target.setFilter(filter.withListMode(payload.value() == 0
                        ? Filter.ListMode.WHITELIST : Filter.ListMode.BLACKLIST));
            }
            case SET_COMPONENTS -> {
                // Componentes só fazem sentido para itens.
                if (target.type() != ResourceType.ITEM || (payload.value() != 0 && payload.value() != 1)) {
                    return false;
                }
                target.setFilter(filter.withMatchComponents(payload.value() == 1));
            }
            case CLEAR -> target.setFilter(filter.cleared());
            case IMPORT_CARD -> {
                ItemStack card = player.getMainHandItem();
                if (target instanceof CardFilterTarget || !FilterCardItem.isCard(card)) {
                    return false;
                }
                FilterCardItem.Contents contents = FilterCardItem.contents(card);
                if (contents.type() != target.type()) {
                    return false;
                }
                target.setFilter(contents.filter());
            }
            case EXPORT_CARD -> {
                ItemStack card = player.getMainHandItem();
                if (target instanceof CardFilterTarget || !FilterCardItem.isCard(card)) {
                    return false;
                }
                FilterCardItem.setContents(card, new FilterCardItem.Contents(target.type(), filter));
            }
            case BACK -> {
                if (target instanceof RouterFaceFilterTarget face) {
                    switch (face.origin()) {
                        case NEAR -> RouterMenu.open(player, face.router(), face.target());
                        case REMOTE_ROUTER -> RemoteRouterMenu.open(player, face.router(), face.target());
                        case TABLET -> TabletMenu.open(player);
                    }
                } else if (target instanceof StorageFilterTarget storage) {
                    storage.storage().open(player);
                } else {
                    player.closeContainer();
                }
            }
        }
        return true;
    }

    /**
     * Ingrediente fantasma (JEI): acrescenta um item exato a um filtro de itens, um fluido exato a
     * um de fluidos ou um químico (que exista) a um de químicos, sem o jogador ter o recurso. Normaliza para quantidade 1 e sem estoque; o
     * {@link Filter#withEntry} ignora duplicados e o teto. Devolve se a entrada foi aceita.
     */
    public static boolean handleAddFilterEntry(@Nullable ServerPlayer player, AddFilterEntryPayload payload) {
        FilterMenu menu = player != null && player.containerMenu instanceof FilterMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || menu.target() == null || !menu.stillValid(player)) {
            return false;
        }
        FilterTarget target = menu.target();
        FilterEntry entry = switch (payload.entry()) {
            case FilterEntry.ItemEntry e when target.type() == ResourceType.ITEM && !e.stack().isEmpty() ->
                    new FilterEntry.ItemEntry(e.stack(), 0);
            case FilterEntry.FluidEntry e when target.type() == ResourceType.FLUID && !e.stack().isEmpty() ->
                    new FilterEntry.FluidEntry(e.stack(), 0);
            case FilterEntry.ChemicalEntry e when target.type() == ResourceType.CHEMICAL && Chemicals.exists(e.chemical()) ->
                    new FilterEntry.ChemicalEntry(e.chemical(), 0);
            default -> null;
        };
        if (entry == null) {
            return false;
        }
        target.setFilter(target.filter().withEntry(entry));
        return true;
    }

    /**
     * Tags, mods e regras montadas na tela (inspetor, busca, montador de regra): acrescenta as
     * entradas ou troca uma ({@link FilterEntriesPayload}). Cada uma precisa valer para o tipo do
     * filtro: tag em itens e fluidos, regra só em itens, mod em todos. As novas entram sem estoque.
     * Devolve se o pacote foi aceito; uma entrada inválida recusa o pacote inteiro.
     */
    public static boolean handleFilterEntries(@Nullable ServerPlayer player, FilterEntriesPayload payload) {
        FilterMenu menu = player != null && player.containerMenu instanceof FilterMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || menu.target() == null || !menu.stillValid(player) || payload.entries().isEmpty()) {
            return false;
        }
        FilterTarget target = menu.target();
        List<FilterEntry> entries = new ArrayList<>(payload.entries().size());
        for (FilterEntry entry : payload.entries()) {
            FilterEntry valid = validRule(target.type(), entry);
            if (valid == null) {
                return false;
            }
            entries.add(valid);
        }
        Filter filter = target.filter();
        if (payload.replace() < 0) {
            target.setFilter(filter.withEntries(entries));
            return true;
        }
        if (entries.size() != 1 || !validIndex(filter, payload.replace())) {
            return false;
        }
        target.setFilter(filter.withReplaced(payload.replace(), entries.getFirst()));
        return true;
    }

    /** A entrada normalizada (sem estoque) se ela é uma tag, mod ou regra que vale para {@code type}. */
    private static @Nullable FilterEntry validRule(ResourceType type, FilterEntry entry) {
        return switch (entry) {
            case FilterEntry.TagEntry e when type == ResourceType.ITEM || type == ResourceType.FLUID -> {
                ResourceLocation tag = parseTag(e.tag().toString());
                yield tag == null ? null : new FilterEntry.TagEntry(tag, 0);
            }
            case FilterEntry.ModEntry e -> {
                String mod = parseMod(e.modId());
                yield mod == null ? null : new FilterEntry.ModEntry(mod, 0);
            }
            case FilterEntry.RuleEntry e when type == ResourceType.ITEM && e.rule().isValid() -> new FilterEntry.RuleEntry(e.rule(), 0);
            default -> null;
        };
    }

    /** {@code c:ingots} ou {@code #c:ingots}, em minúsculas; {@code null} se não for um id válido. */
    public static @Nullable ResourceLocation parseTag(String text) {
        String value = text.strip();
        if (value.startsWith("#")) {
            value = value.substring(1).strip();
        }
        if (value.isEmpty()) {
            return null;
        }
        ResourceLocation tag = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
        return tag == null || tag.getPath().isEmpty() ? null : tag;
    }

    /** {@code mekanism} ou {@code @mekanism}, em minúsculas; {@code null} se não for um namespace válido. */
    public static @Nullable String parseMod(String text) {
        String value = text.strip();
        if (value.startsWith("@")) {
            value = value.substring(1).strip();
        }
        value = value.toLowerCase(Locale.ROOT);
        return value.isEmpty() || value.length() > 64 || !ResourceLocation.isValidNamespace(value) ? null : value;
    }

    private static boolean validIndex(Filter filter, int index) {
        return index >= 0 && index < filter.entries().size();
    }

    /** Dono, operador nível 2 ou rede pública ({@link WaNetwork#canUse}). */
    public static boolean canUse(ServerPlayer player, WaNetwork network) {
        return network.canUse(player);
    }

    /**
     * O host de edição do menu aberto do jogador, se é ele que o pacote cita, aponta um roteador e ainda
     * deixa o jogador editar ({@link RouterEditHost#canEdit}).
     */
    public static @Nullable RouterEditHost host(@Nullable ServerPlayer player, int containerId) {
        if (player != null && player.containerMenu instanceof RouterEditHost host && host.containerId() == containerId
                && host.editedRouter() != null && host.canEdit(player)) {
            return host;
        }
        return null;
    }

    /** O roteador da tela aberta, se é ela que o pacote cita e ela ainda vale. */
    private static @Nullable RouterBlockEntity router(@Nullable ServerPlayer player, int containerId) {
        RouterMenu menu = openMenu(player, containerId);
        if (menu == null || menu.router() == null || !menu.stillValid(player)) {
            return null;
        }
        return menu.router();
    }

    private ModPayloads() {
    }
}
