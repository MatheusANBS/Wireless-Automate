package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.RangeRowButtons;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A máquina trocada no e2e do roteador de alcance: a fornalha vinculada vira um defumador no servidor, a
 * linha dela fica "trocada" em latão com o ⟳ ({@code range-7-trocada}); com exceção e sem a lista, a linha
 * Editando mostra Voltar, Atualizar e Desvincular ({@code range-7b-trocada-visor}) e o Desvincular armado
 * fica sozinho ({@code range-7c-visor-armado}); o Atualizar leva a máquina ao grupo do defumador
 * ({@code range-8-atualizada}). Só em inglês, um grupo inteiro trocado (o defumador vira barril) pelo
 * ⟳ da linha do grupo. No fim, a fornalha volta, no perfil, para os passos seguintes.
 */
final class E2eRangeReplaceSteps {
    private E2eRangeReplaceSteps() {
    }

    /** {@code fromList}: o Atualizar pelo ⟳ da lista; senão, pelo botão da linha Editando. */
    static void rangeReplaceSteps(List<Step> list, String label, String suffix, boolean fromList) {
        list.add(new Step(label + "trocar a fornalha por um defumador", STEP_TIMEOUT_MS,
                () -> setFurnace(Blocks.SMOKER),
                () -> furnace().state() == RangeView.MachineState.REPLACED
                        && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE) != null,
                () -> "fornalha " + furnace()));
        list.add(new Step(label + "trocada: o mouse no ⟳", STEP_TIMEOUT_MS, () -> {
            int[] update = routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE);
            if (update == null) {
                throw new StepFailure("o ⟳ da trocada fora da vista");
            }
            moveMouse(update[0], update[1]);
        }, () -> stepTicks >= 2, () -> "fornalha " + furnace()));
        list.add(hoverCapture(label + "trocada: linha e dica do ⟳", "range-7-trocada" + suffix,
                () -> furnace().state() == RangeView.MachineState.REPLACED));
        list.add(new Step(label + "trocada: Frente em Extrair vira exceção", STEP_TIMEOUT_MS,
                () -> clickMode(PortMode.EXTRACT), () -> furnace().exception(), () -> "fornalha " + furnace()));

        // sem a lista: a linha Editando com os três botões, e o Desvincular armado sozinho
        list.add(new Step(label + "trocada: o visor no lugar da lista", STEP_TIMEOUT_MS,
                () -> routerScreen().previewToggleList(),
                () -> !routerScreen().rangeListShown() && panel().revertCenter() != null
                        && panel().updateCenter() != null && panel().unlinkCenter() != null && stepTicks >= 4,
                () -> "lista " + routerScreen().rangeListShown() + ", Voltar " + (panel().revertCenter() != null)
                        + ", Atualizar " + (panel().updateCenter() != null)));
        list.add(rangeClipCheck(label + "trocada no visor"));
        list.add(capture("range-7b-trocada-visor" + suffix));
        list.add(new Step(label + "visor: o primeiro clique no Desvincular arma", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().unlinkCenter(), "Desvincular"),
                () -> stepTicks >= 4 && panel().unlinkArmed() && panel().revertCenter() == null
                        && panel().updateCenter() == null
                        && onServer(server -> router(server, rangeRouter).remoteMachines().contains(rangeFurnace)),
                () -> "armado " + panel().unlinkArmed()));
        list.add(hoverCapture(label + "visor: Desvincular armado sozinho", "range-7c-visor-armado" + suffix,
                () -> panel().unlinkArmed()));
        list.add(new Step(label + "visor: sair com o mouse desarma", STEP_TIMEOUT_MS, () -> moveMouse(0, 0),
                () -> stepTicks >= 2 && !panel().unlinkArmed() && panel().updateCenter() != null,
                () -> "armado " + panel().unlinkArmed()));

        if (fromList) {
            list.add(new Step(label + "trocada: de volta à lista", STEP_TIMEOUT_MS,
                    () -> routerScreen().previewToggleList(),
                    () -> routerScreen().rangeListShown() && stepTicks >= 2,
                    () -> "lista " + routerScreen().rangeListShown()));
        }
        list.add(new Step(label + (fromList ? "Atualizar pelo ⟳ da lista" : "Atualizar pela linha Editando"),
                STEP_TIMEOUT_MS, () -> {
                    if (fromList) {
                        clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE), "o ⟳ da trocada");
                    } else {
                        clickPanel(panel().updateCenter(), "Atualizar");
                    }
                }, () -> furnace().state() == RangeView.MachineState.LOADED && furnace().exception()
                        && groupBlockIs(furnace().group(), Blocks.SMOKER)
                        && rangeTargetIs(EditTarget.machine(rangeMachine(rangeFurnace))),
                () -> "fornalha " + furnace() + ", grupos " + rangeView().groups()));
        if (!fromList) {
            list.add(new Step(label + "atualizada: de volta à lista", STEP_TIMEOUT_MS,
                    () -> routerScreen().previewToggleList(),
                    () -> routerScreen().rangeListShown() && stepTicks >= 2,
                    () -> "lista " + routerScreen().rangeListShown()));
        }
        list.add(rangeClipCheck(label + "atualizada"));
        list.add(capture("range-8-atualizada" + suffix));
        if (fromList) {
            groupUpdateSteps(list, label);
        }
        list.add(new Step(label + "a fornalha de volta, no perfil", STEP_TIMEOUT_MS, () -> {
            setFurnace(Blocks.FURNACE);
            onServer(server -> {
                var router = router(server, rangeRouter);
                int index = router.remoteMachines().index(rangeFurnace);
                router.updateReplaced(List.of(index));
                router.revertToProfile(index);
                return null;
            });
        }, () -> furnace().state() == RangeView.MachineState.LOADED && !furnace().exception()
                && groupBlockIs(furnace().group(), Blocks.FURNACE),
                () -> "fornalha " + furnace() + ", grupos " + rangeView().groups()));
    }

    /** O grupo inteiro trocado: o defumador vira barril, e o ⟳ da linha do grupo atualiza o grupo. */
    private static void groupUpdateSteps(List<Step> list, String label) {
        list.add(new Step(label + "grupo trocado: o defumador vira barril", STEP_TIMEOUT_MS, () -> {
            setFurnace(Blocks.BARREL);
            clickRow(EditTarget.group(furnace().group()));
        }, () -> furnace().state() == RangeView.MachineState.REPLACED
                && rangeTargetIs(EditTarget.group(furnace().group()))
                && routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE) != null,
                () -> "fornalha " + furnace() + ", alvo " + routerScreen().getMenu().snapshot().target()));
        list.add(new Step(label + "grupo trocado: o mouse no ⟳ do grupo", STEP_TIMEOUT_MS, () -> {
            int[] update = routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE);
            if (update == null) {
                throw new StepFailure("o ⟳ do grupo fora da vista");
            }
            moveMouse(update[0], update[1]);
        }, () -> stepTicks >= 2, () -> "fornalha " + furnace()));
        list.add(hoverCapture(label + "grupo trocado: linha e dica do ⟳", "range-8b-grupo-trocado",
                () -> furnace().state() == RangeView.MachineState.REPLACED));
        // o grupo que só trocou de bloco continua escolhido
        list.add(new Step(label + "grupo trocado: Atualizar pelo ⟳ do grupo", STEP_TIMEOUT_MS,
                () -> clickPanel(routerScreen().rangeButtonCenter(RangeRowButtons.Action.UPDATE), "o ⟳ do grupo"),
                () -> furnace().state() == RangeView.MachineState.LOADED
                        && groupBlockIs(furnace().group(), Blocks.BARREL)
                        && rangeTargetIs(EditTarget.group(furnace().group())),
                () -> "fornalha " + furnace() + ", grupos " + rangeView().groups()));
    }

    /** Troca no servidor o bloco da fornalha vinculada, de frente para o sul como ela (o barril, como vier). */
    private static void setFurnace(Block block) throws Exception {
        BlockState state = block.defaultBlockState();
        BlockState placed = state.hasProperty(AbstractFurnaceBlock.FACING)
                ? state.setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH) : state;
        onServer(server -> server.overworld().setBlockAndUpdate(rangeFurnace, placed));
    }

    private static RangeView.MachineView furnace() throws StepFailure {
        RangeView.MachineView machine = rangeView().machine(rangeMachine(rangeFurnace));
        if (machine == null) {
            throw new StepFailure("sem a fornalha na tela");
        }
        return machine;
    }

    private static boolean groupBlockIs(int group, Block block) throws StepFailure {
        RangeView.GroupView view = rangeView().group(group);
        return view != null && view.block().equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    /**
     * Captura com o mouse onde está (a dica do botão fica à vista, e o Desvincular armado continua armado),
     * quando {@code ready} vale e nenhum texto está cortado.
     */
    static Step hoverCapture(String name, String file, Check ready) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
        }, () -> {
            if (stepTicks < 6 || GuiText.clipCount() != 0 || !ready.done()) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> GuiText.clipCount() + " texto(s) cortado(s): " + clippedTexts());
    }
}
