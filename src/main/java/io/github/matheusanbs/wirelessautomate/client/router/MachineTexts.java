package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineState;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.MachineView;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/**
 * Os textos e as cores de máquina e de grupo: o nome do grupo, o número e o nome da máquina, a distância, as
 * exceções e o estado. Uma fonte só para a lista da tela do roteador ({@link RangeList}), o painel do nó, o Mapa
 * e as linhas por máquina do Tablet. Só cliente.
 */
public final class MachineTexts {
    private MachineTexts() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    /** O nome do grupo: o dado pelo jogador ou o do bloco. */
    public static Component groupName(GroupView group) {
        if (!group.name().isEmpty()) {
            return Component.literal(group.name());
        }
        // pelo item, não pelo bloco: o nome de alguns blocos tem lacunas que só o item preenche
        return RouterBlock.machineName(BuiltInRegistries.BLOCK.get(group.block()));
    }

    /** O número da máquina dentro do grupo, a partir de 1 (a ordem de vínculo). */
    public static int number(RangeView view, int index) {
        MachineView machine = view.machine(index);
        if (machine == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i <= index; i++) {
            if (view.machines().get(i).group() == machine.group()) {
                n++;
            }
        }
        return n;
    }

    /**
     * O nome de uma máquina fora da lista (a pílula, as dicas e a lista do alvo): o rótulo dado pelo jogador, ou
     * "Fornalha #3". A única regra do nome, com o {@link MachineRow#rowName} da linha embaixo do grupo.
     */
    public static Component machineName(RangeView view, int index) {
        MachineView machine = view.machine(index);
        if (machine != null && !machine.label().isEmpty()) {
            return Component.literal(machine.label());
        }
        return blockName(view, index);
    }

    /** O nome de uma máquina sem rótulo: "Fornalha #3" (também a dica da caixa do rótulo vazia). */
    public static Component blockName(RangeView view, int index) {
        MachineView machine = view.machine(index);
        GroupView group = machine == null ? null : view.group(machine.group());
        Component name = group == null ? Component.literal("?") : groupName(group);
        return tr("range.machine.name", name, number(view, index));
    }

    public static Component distance(MachineView machine) {
        return machine.distance() == 1 ? tr("range.distance.one") : tr("range.distance", machine.distance());
    }

    public static Component exceptions(GroupView group) {
        return group.exceptions() == 1 ? tr("range.group.exceptions.one") : tr("range.group.exceptions", group.exceptions());
    }

    public static int stateColor(MachineState state) {
        return switch (state) {
            case LOADED -> GuiPaint.OK;
            case UNLOADED -> GuiPaint.WARN;
            case MISSING -> GuiPaint.DANGER;
            case REPLACED -> GuiPaint.BRASS;
            case OUT_OF_RANGE, OVER_LIMIT -> GuiPaint.MUTED;
        };
    }

    public static Component stateText(MachineState state) {
        return tr("range.machine.state." + state.name().toLowerCase(Locale.ROOT));
    }
}
