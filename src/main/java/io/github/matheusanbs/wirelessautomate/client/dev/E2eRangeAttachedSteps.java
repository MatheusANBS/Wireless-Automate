package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.range.AttachedMember;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/**
 * A máquina presa dentro do grupo dela, no e2e do roteador de alcance (o roteador está num baú, com dois baús e uma
 * fornalha vinculados). Uma escrita na presa a põe no grupo dos baús como exceção: a linha dela é a primeira do
 * grupo, com "presa", a marca de exceção e o Olho ({@code range-p0-presa-marca}), e escolhida tem o ↺ e o ✎, sem o ✕
 * ({@code range-p1-presa-excecao}, também no tema escuro);
 * sem a lista, a linha Editando tem o No mundo e o Voltar, sem o Desvincular ({@code range-p2-presa-editando}, só
 * em inglês). Com uma face sem modo e com rede, o Tirar redes aparece na linha dela e tira a rede. O ↺ tira a exceção no servidor ({@code range-p3-presa-no-grupo}) e o grupo recolhido esconde a presa
 * ({@code range-p4-grupo-recolhido}). No fim, sem as remotas e com um barril no lugar do baú, o grupo dos baús fica
 * "×0" e a presa vai para o grupo do barril, que abre com ela ({@code range-p5-grupo-vazio}).
 */
final class E2eRangeAttachedSteps {
    private E2eRangeAttachedSteps() {
    }

    /** Os passos da presa no grupo, com a tela no mínimo, a lista à vista e o grupo dos baús aberto. */
    static void attachedSteps(List<Step> list, String label, String suffix) {
        String l = label + "presa: ";
        // a face Norte do baú (que olha para o Sul) é a Trás dele, em Extrair no perfil: Inserir vira exceção
        list.add(new Step(l + "uma escrita põe a presa no grupo dos baús, como exceção", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    router(server, rangeRouter).setMode(ResourceType.ITEM, Direction.NORTH, PortMode.INSERT);
                    return null;
                }), () -> {
                    RangeView view = rangeView();
                    RangeView.GroupView chests = view.group(rangeGroup(Blocks.CHEST));
                    return view.attachedGroup() == rangeGroup(Blocks.CHEST) && view.attachedException()
                            && chests != null && chests.count() == 3
                            && routerScreen().rangeRowCenter(EditTarget.ATTACHED_TARGET) != null;
                }, () -> "alcance " + rangeView()));
        list.add(rangeClipCheck(l + "marca de exceção"));
        list.add(capture("range-p0-presa-marca" + suffix));
        list.add(new Step(l + "escolher a linha da presa", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.ATTACHED_TARGET),
                () -> rangeTargetIs(EditTarget.ATTACHED_TARGET) && stepTicks >= 2
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT) != null
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.RENAME) != null
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UNLINK) == null
                        && routerScreen().rangeEyeCenter(EditTarget.ATTACHED_TARGET) != null,
                () -> "alvo " + routerScreen().getMenu().snapshot().target()));
        list.add(rangeClipCheck(l + "exceção"));
        list.add(capture("range-p1-presa-excecao" + suffix));
        if (suffix.isEmpty()) {
            E2eVariants.escuro(list, () -> {
                list.add(E2eDarkSteps.themeClick(l + "tema escuro", GuiTheme.DARK));
                list.add(rangeClipCheck(l + "exceção no escuro"));
                list.add(capture("range-p1-presa-excecao-escuro"));
                list.add(E2eDarkSteps.themeClick(l + "de volta ao tema claro", GuiTheme.LIGHT));
            });
            list.add(new Step(l + "sem a lista, a linha Editando da presa", STEP_TIMEOUT_MS,
                    () -> routerScreen().previewToggleList(),
                    () -> !routerScreen().rangeListShown() && stepTicks >= 4
                            && routerScreen().panel().revertCenter() != null
                            && routerScreen().panel().highlightCenter() != null
                            && routerScreen().panel().unlinkCenter() == null
                            && routerScreen().panel().updateCenter() == null,
                    () -> "lista " + routerScreen().rangeListShown()));
            list.add(rangeClipCheck(l + "linha Editando"));
            list.add(capture("range-p2-presa-editando"));
            list.add(new Step(l + "de volta à lista", STEP_TIMEOUT_MS, () -> routerScreen().previewToggleList(),
                    () -> routerScreen().rangeListShown(), () -> "lista " + routerScreen().rangeListShown()));
        }
        idleNetworkSteps(list, l);
        list.add(new Step(l + "o ↺ tira a exceção", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT), "o ↺ da presa"),
                () -> !rangeView().attachedException() && stepTicks >= 2
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.REVERT) == null
                        && onServer(server -> member(server) != null && !member(server).hasException()),
                () -> "alcance " + rangeView()));
        list.add(rangeClipCheck(l + "no perfil"));
        list.add(capture("range-p3-presa-no-grupo" + suffix));
        list.add(new Step(l + "o grupo recolhido esconde a presa", STEP_TIMEOUT_MS,
                () -> clickArrow(rangeGroup(Blocks.CHEST)),
                () -> routerScreen().rangeRowCenter(EditTarget.ATTACHED_TARGET) == null
                        && routerScreen().rangeRowCenter(EditTarget.group(rangeGroup(Blocks.CHEST))) != null,
                () -> "linha da presa " + java.util.Arrays.toString(
                        routerScreen().rangeRowCenter(EditTarget.ATTACHED_TARGET))));
        list.add(rangeClipCheck(l + "recolhido"));
        list.add(capture("range-p4-grupo-recolhido" + suffix));
        list.add(new Step(l + "o grupo aberto de novo", STEP_TIMEOUT_MS,
                () -> clickArrow(rangeGroup(Blocks.CHEST)),
                () -> routerScreen().rangeRowCenter(EditTarget.ATTACHED_TARGET) != null,
                () -> "linha da presa fora da vista"));
    }

    /** Uma face sem modo com rede na exceção da presa: o Tirar redes aparece na linha dela e tira a rede no servidor. */
    private static void idleNetworkSteps(List<Step> list, String l) {
        list.add(new Step(l + "uma face sem modo com rede mostra o Tirar redes", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    router(server, rangeRouter).setFaceNetwork(ResourceType.ITEM, Direction.UP, mainNetwork);
                    return null;
                }), () -> routerScreen().rangeButtonCenter(RangeRowButtons.Action.CLEAR_NETWORKS) != null,
                () -> "alcance " + rangeView()));
        list.add(new Step(l + "o Tirar redes tira a rede da presa", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.CLEAR_NETWORKS),
                        "o Tirar redes da presa"),
                () -> routerScreen().rangeButtonCenter(RangeRowButtons.Action.CLEAR_NETWORKS) == null
                        && onServer(server -> router(server, rangeRouter).faceNetwork(ResourceType.ITEM, Direction.UP)
                                == null),
                () -> "rede no servidor " + onServer(server -> router(server, rangeRouter)
                        .faceNetwork(ResourceType.ITEM, Direction.UP))));
        list.add(new Step(l + "sem o aviso do Tirar redes", STEP_TIMEOUT_MS, PanelNotices::clear,
                () -> PanelNotices.current() == null, () -> "aviso"));
    }

    /**
     * O grupo "×0": sem as remotas, o grupo dos baús fica só com a presa; com um barril no lugar do baú, a presa
     * vai para o grupo do barril e o dos baús fica, sem máquina. A tela abre no mínimo, com a lista.
     */
    static void emptyGroupSteps(List<Step> list, String label) {
        String l = label + "grupo ×0: ";
        list.add(open(l + "abrir", () -> rangeRouter));
        list.add(new Step(l + "sem as remotas e um barril no lugar do baú", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(300, 240);
            if (!routerScreen().rangeListShown()) {
                routerScreen().previewToggleList();
            }
            onServer(server -> {
                RouterBlockEntity router = router(server, rangeRouter);
                while (!router.remoteMachines().machines().isEmpty()) {
                    router.unlinkMachine(0);
                }
                // só a presa no grupo: a escrita muda o perfil, e o grupo não sai vazio quando a presa o deixa
                router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
                server.overworld().setBlockAndUpdate(rangeRouter.below(), Blocks.BARREL.defaultBlockState());
                return null;
            });
        }, () -> {
            RangeView view = rangeView();
            RangeView.GroupView chests = view.group(rangeGroup(Blocks.CHEST));
            // a presa mudou de grupo sem o alvo mudar: o grupo do barril abre com ela
            return chests != null && chests.count() == 0 && view.attachedGroup() == rangeGroup(Blocks.BARREL)
                    && routerScreen().rangeRowCenter(EditTarget.ATTACHED_TARGET) != null
                    && routerScreen().rangeListShown() && stepTicks >= 4;
        }, () -> "alcance " + rangeView()));
        list.add(rangeClipCheck(l + "lista"));
        list.add(capture("range-p5-grupo-vazio"));
        list.add(close(l + "fechar"));
    }

    /** O membro presa do roteador no servidor, ou {@code null}. */
    private static AttachedMember member(MinecraftServer server) {
        return router(server, rangeRouter).remoteMachines().attached();
    }

    /** Clica na seta do grupo {@code group} (abre ou fecha sem escolher). */
    private static void clickArrow(int group) throws StepFailure {
        int[] arrow = routerScreen().rangeArrowPoint(group);
        if (arrow == null) {
            throw new StepFailure("seta do grupo " + group + " fora da vista");
        }
        click(routerScreen(), arrow[0], arrow[1]);
    }
}
