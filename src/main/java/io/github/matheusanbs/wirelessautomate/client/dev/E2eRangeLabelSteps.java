package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.menu.EditTargetRef;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Nomes das máquinas e dos grupos pela tela, no e2e do roteador de alcance (só em inglês): o ✎ da linha da
 * fornalha abre a caixa no lugar do nome ({@code range-7b-editando}), Enter salva o rótulo no servidor, dois
 * cliques no grupo dos baús dão nome ao grupo, e a lista mostra os nomes e as coordenadas na tela larga
 * ({@code range-7-rotulo}); sem a lista, a pílula da linha Editando mostra o rótulo com o ✎
 * ({@code range-7c-pilula-rotulo}). Depois, o Tablet aberto no nó com a fornalha como alvo dá o rótulo pelo ✎
 * da linha Editando ({@code 7j-tablet-painel-rotulo}). No fim, os nomes saem e a tela volta como estava: no
 * mínimo, com a lista e a fornalha como alvo.
 */
final class E2eRangeLabelSteps {
    private E2eRangeLabelSteps() {
    }

    private static final String FURNACE_LABEL = "Smelter 3";
    private static final String CHEST_GROUP = "Storage";
    private static final String CHEST_LABEL = "Ores";
    private static final int WIDE_W = 560;

    static void rangeLabelSteps(List<Step> list, String label, UUID playerId) {
        String l = label + "nome: ";
        list.add(new Step(l + "o ✎ da fornalha abre a caixa", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.RENAME), "o ✎ da fornalha"),
                () -> routerScreen().rangeLabelEditing() && stepTicks >= 2,
                () -> "caixa aberta " + routerScreen().rangeLabelEditing()));
        list.add(new Step(l + "digitar o rótulo", STEP_TIMEOUT_MS, () -> type(FURNACE_LABEL),
                () -> stepTicks >= 2 && routerScreen().rangeLabelEditing(), () -> "caixa"));
        list.add(rangeClipCheck(l + "caixa aberta"));
        list.add(capture("range-7b-editando"));
        list.add(new Step(l + "Enter salva o rótulo", STEP_TIMEOUT_MS,
                () -> routerScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                () -> !routerScreen().rangeLabelEditing() && FURNACE_LABEL.equals(clientLabel(rangeFurnace))
                        && onServer(server -> FURNACE_LABEL.equals(serverLabel(server, rangeFurnace))),
                () -> "rótulo na tela " + clientLabel(rangeFurnace)));

        list.add(new Step(l + "dois cliques no grupo dos baús abrem a caixa", STEP_TIMEOUT_MS, () -> {
            int[] row = routerScreen().rangeRowCenter(EditTarget.group(rangeGroup(Blocks.CHEST)));
            if (row == null) {
                throw new StepFailure("linha do grupo dos baús fora da vista");
            }
            click(routerScreen(), row[0], row[1]);
            click(routerScreen(), row[0], row[1]);
        }, () -> routerScreen().rangeLabelEditing(), () -> "caixa aberta " + routerScreen().rangeLabelEditing()));
        list.add(new Step(l + "nome do grupo e Enter", STEP_TIMEOUT_MS, () -> {
            type(CHEST_GROUP);
            routerScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> !routerScreen().rangeLabelEditing() && CHEST_GROUP.equals(clientGroupName())
                && onServer(server -> CHEST_GROUP.equals(router(server, rangeRouter).remoteMachines()
                        .group(chestGroupOnServer(server)).name())),
                () -> "grupo na tela " + clientGroupName()));

        list.add(new Step(l + "um baú com rótulo, e os baús abertos na tela larga", STEP_TIMEOUT_MS, () -> {
            onServer(server -> router(server, rangeRouter).renameTarget(machineTarget(server, firstChest(server)),
                    CHEST_LABEL));
            routerScreen().previewResize(Math.min(WIDE_W, routerScreen().width - 8), 280);
        }, () -> CHEST_LABEL.equals(clientLabel(firstChestClient())) && routerScreen().rangeListShown()
                && routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(firstChestClient()))) != null
                && routerScreen().rangeRowCenter(EditTarget.machine(rangeMachine(rangeFurnace))) != null
                && stepTicks >= 4,
                () -> "rótulo do baú " + clientLabel(firstChestClient())));
        list.add(rangeClipCheck(l + "lista com os nomes"));
        list.add(capture("range-7-rotulo"));

        list.add(new Step(l + "a fornalha de volta como alvo, sem a lista", STEP_TIMEOUT_MS, () -> {
            clickRow(EditTarget.machine(rangeMachine(rangeFurnace)));
        }, () -> rangeTargetIs(EditTarget.machine(rangeMachine(rangeFurnace))), () -> "alvo "
                + routerScreen().getMenu().snapshot().target()));
        list.add(new Step(l + "tela média sem a lista: a pílula com o rótulo", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(Math.min(470, routerScreen().width - 8), 240);
        }, () -> stepTicks >= 2, () -> "tamanho"));
        list.add(new Step(l + "esconder a lista", STEP_TIMEOUT_MS, () -> {
            if (routerScreen().rangeListShown()) {
                routerScreen().previewToggleList();
            }
        }, () -> !routerScreen().rangeListShown() && panel().renameCenter() != null && stepTicks >= 4,
                () -> "lista " + routerScreen().rangeListShown() + ", ✎ " + (panel().renameCenter() != null)));
        list.add(rangeClipCheck(l + "pílula"));
        list.add(capture("range-7c-pilula-rotulo"));
        list.add(new Step(l + "de volta ao mínimo com a lista", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(300, 240);
            if (!routerScreen().rangeListShown()) {
                routerScreen().previewToggleList();
            }
        }, () -> routerScreen().size()[0] == 300 && routerScreen().rangeListShown() && stepTicks >= 4,
                () -> "lista " + routerScreen().rangeListShown()));

        tabletLabelSteps(list, l, playerId);
    }

    /** O Tablet no nó do roteador de alcance, com a fornalha como alvo: o ✎ da linha Editando dá o rótulo. */
    private static void tabletLabelSteps(List<Step> list, String l, UUID playerId) {
        list.add(close(l + "fechar o roteador"));
        list.add(new Step(l + "Tablet aberto no nó, a fornalha sem rótulo como alvo", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETWORK_TABLET.get()));
                    RouterBlockEntity router = router(server, rangeRouter);
                    NodeIndex.placedBy(router, playerId);
                    EditTarget furnace = machineTarget(server, rangeFurnace);
                    router.renameTarget(furnace, "");
                    TabletMenu.open(player, new NodeIndex.NodeKey(Level.OVERWORLD, rangeRouter),
                            EditTargetRef.resolve(router, furnace, rangeFurnace, null));
                    return null;
                }), () -> {
                    // a tela chega um pouco depois do pedido no servidor
                    if (!(Minecraft.getInstance().screen instanceof TabletScreen)) {
                        return false;
                    }
                    NodePanel panel = tabletPanel();
                    return panel != null && panel.renameCenter() != null && stepTicks >= 4;
                }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step(l + "Tablet: o ✎ abre a caixa", STEP_TIMEOUT_MS, () -> {
            int[] c = tabletPanel().renameCenter();
            click(tabletScreen(), c[0], c[1]);
        }, () -> tabletPanel().labelEditing(), () -> "caixa aberta " + tabletPanel().labelEditing()));
        list.add(new Step(l + "Tablet: rótulo e Enter", STEP_TIMEOUT_MS, () -> {
            type(FURNACE_LABEL);
            tabletScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> !tabletPanel().labelEditing() && onServer(server ->
                FURNACE_LABEL.equals(serverLabel(server, rangeFurnace)))
                && FURNACE_LABEL.equals(tabletLabel()) && stepTicks >= 4,
                () -> "rótulo no painel " + tabletLabel()));
        // a Lista corta a segunda linha dos roteadores com "…" e a dica da linha traz o texto inteiro
        list.add(clipCheck(l + "Tablet"));
        list.add(capture("7j-tablet-painel-rotulo"));
        list.add(close(l + "fechar o Tablet"));
        list.add(new Step(l + "os nomes saem", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            RouterBlockEntity router = router(server, rangeRouter);
            router.renameTarget(machineTarget(server, rangeFurnace), "");
            router.renameTarget(machineTarget(server, firstChest(server)), "");
            router.renameTarget(EditTarget.group(chestGroupOnServer(server)), "");
            return null;
        }), () -> onServer(server -> serverLabel(server, rangeFurnace).isEmpty()
                && router(server, rangeRouter).remoteMachines().group(chestGroupOnServer(server)).name().isEmpty()),
                () -> "nomes ainda no servidor"));
        list.add(open(l + "abrir o roteador de novo", () -> rangeRouter));
        list.add(backToMinimum(l, playerId));
    }

    /** A tela do roteador aberta no mínimo, com a lista e a fornalha como alvo (como o resto do e2e espera). */
    static Step backToMinimum(String l, UUID playerId) {
        return new Step(l + "no mínimo, com a lista e a fornalha", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(300, 240);
            if (!routerScreen().rangeListShown()) {
                routerScreen().previewToggleList();
            }
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                int furnace = router(server, rangeRouter).remoteMachines().index(rangeFurnace);
                io.github.matheusanbs.wirelessautomate.packet.ModPayloads.handleSelectTarget(player,
                        io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload.machine(
                                player.containerMenu.containerId, furnace, rangeFurnace));
                return null;
            });
        }, () -> routerScreen().size()[0] == 300 && routerScreen().rangeListShown()
                && rangeTargetIs(EditTarget.machine(rangeMachine(rangeFurnace))) && stepTicks >= 4,
                () -> "alvo " + routerScreen().getMenu().snapshot().target());
    }

    // ------------------------------------------------------------------ leituras

    private static @Nullable NodePanel tabletPanel() throws StepFailure {
        TabletScreen screen = tabletScreen();
        return screen.nodePanel();
    }

    /** O rótulo da fornalha no painel do Tablet. */
    private static String tabletLabel() throws StepFailure {
        NodePanel panel = tabletPanel();
        if (panel == null) {
            return "(sem painel)";
        }
        RangeView view = panel.snapshot().range();
        for (RangeView.MachineView machine : view.machines()) {
            if (machine.pos().equals(rangeFurnace)) {
                return machine.label();
            }
        }
        return "(sem a fornalha)";
    }

    private static String clientLabel(BlockPos pos) throws StepFailure {
        RangeView.MachineView machine = rangeView().machine(rangeMachine(pos));
        return machine == null ? "" : machine.label();
    }

    private static String clientGroupName() throws StepFailure {
        RangeView.GroupView group = rangeView().group(rangeGroup(Blocks.CHEST));
        return group == null ? "" : group.name();
    }

    /** O primeiro baú vinculado, pela tela. */
    private static BlockPos firstChestClient() throws StepFailure {
        int group = rangeGroup(Blocks.CHEST);
        for (RangeView.MachineView machine : rangeView().machines()) {
            if (machine.group() == group) {
                return machine.pos();
            }
        }
        throw new StepFailure("sem baú vinculado na tela");
    }

    private static int chestGroupOnServer(MinecraftServer server) {
        RemoteMachines machines = router(server, rangeRouter).remoteMachines();
        return machines.groupOf(machines.machine(machines.index(firstChest(server)))).id();
    }

    private static BlockPos firstChest(MinecraftServer server) {
        for (var machine : router(server, rangeRouter).remoteMachines().machines()) {
            if (server.overworld().getBlockState(machine.pos()).is(Blocks.CHEST)) {
                return machine.pos();
            }
        }
        throw new IllegalStateException("sem baú vinculado");
    }

    private static EditTarget machineTarget(MinecraftServer server, BlockPos pos) {
        return EditTarget.machine(router(server, rangeRouter).remoteMachines().index(pos));
    }

    private static String serverLabel(MinecraftServer server, BlockPos pos) {
        RemoteMachines machines = router(server, rangeRouter).remoteMachines();
        var machine = machines.machine(machines.index(pos));
        return machine == null ? "?" : machine.label();
    }
}
