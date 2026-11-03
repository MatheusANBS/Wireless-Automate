package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineListRows.Entry;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Os ganchos do e2e na aba Máquina: o centro na tela de cada coisa clicável (ou {@code null} fora da lista) e o estado que
 * os passos conferem. Só lê as peças da {@link MachinePanel}; nada aqui muda a tela, fora a rolagem que traz uma linha à
 * vista.
 */
public final class MachineProbe {
    private final MachinePanel panel;
    private final MachineList list;
    private final MachineHeader header;
    private final TabletMenu menu;

    MachineProbe(MachinePanel panel, MachineList list, MachineHeader header, TabletMenu menu) {
        this.panel = panel;
        this.list = list;
        this.header = header;
        this.menu = menu;
    }

    /** Quantas linhas a lista tem (máquinas e "mais N"). */
    public int rowCount() {
        return panel.entries().size();
    }

    /** Centro da linha da máquina {@code index} (−1: a presa) do roteador, ou {@code null}. */
    public int @Nullable [] machineCenter(NodeKey router, int index) {
        return list.centerOf(find(router, index));
    }

    /** Centro do "… mais N" do bloco, ou {@code null}. */
    public int @Nullable [] moreCenter(ResourceLocation block) {
        List<Entry> entries = panel.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).machine() && entries.get(i).block().equals(block)) {
                return list.centerOf(i);
            }
        }
        return null;
    }

    private int find(NodeKey router, int index) {
        List<Entry> entries = panel.entries();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.machine() && router.equals(entry.router()) && (index < 0 ? entry.attached()
                    : !entry.attached() && entry.index() == index)) {
                return i;
            }
        }
        return -1;
    }

    /** A máquina escolhida é a {@code index} (−1: a presa) do roteador, e a faixa da lista está nela. */
    public boolean chosen(NodeKey router, int index) {
        int at = find(router, index);
        return at >= 0 && list.chosen() == at;
    }

    /** O servidor escolheu essa máquina, esteja ou não na lista. */
    public boolean serverChose(NodeKey router, int index) {
        MachineSelect.Choice choice = panel.choice();
        return choice != null && choice.router().equals(router) && (index < 0 ? choice.attached()
                : !choice.attached() && choice.index() == index);
    }

    /** O nome da linha escolhida na lista, ou {@code null}. */
    public @Nullable String chosenName() {
        MachineList.Line line = list.line(list.chosen());
        return line == null ? null : line.name();
    }

    /** O título do cabeçalho; vazio sem máquina escolhida. */
    public String headerTitle() {
        return header.title();
    }

    public boolean headerShown() {
        return header.present();
    }

    public @Nullable EditBox searchBox() {
        return panel.box();
    }

    public int @Nullable [] searchCenter() {
        EditBox box = panel.box();
        return box == null ? null : new int[] {box.getX() + box.getWidth() / 2, box.getY() + 4};
    }

    /** A página por máquinas chegou. */
    public boolean pageActive() {
        return menu.snapshot().machineRows().active();
    }
}
