package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A edição do nome de uma linha da lista das máquinas ({@link RangeList}): a caixa ({@link LabelEditor}) no lugar
 * do nome do grupo ou da máquina, aberta pelo ✎ ou por dois cliques no nome. A lista avisa onde a linha em edição
 * está a cada quadro ({@link #place}) e a caixa é desenhada no fim ({@link #endFrame}): se a linha saiu da vista,
 * o nome é salvo; se a máquina sumiu ou o índice passou a outra máquina, a edição fecha sem mandar nada.
 */
final class RowRename {
    /** Dois cliques no nome da mesma linha dentro deste prazo abrem a edição. */
    private static final long DOUBLE_CLICK_MS = 400;

    private final LabelEditor editor;
    private @Nullable EditTarget lastClick;
    private long lastClickAt;
    /** A linha em edição foi posta neste quadro (está inteira à vista). */
    private boolean placed;

    RowRename(LabelEditor.Commit commit) {
        this.editor = new LabelEditor(commit);
    }

    LabelEditor editor() {
        return editor;
    }

    /** Onde o texto do nome da linha começa, a partir da borda da lista. */
    static int nameX(RangeList.Row row) {
        return switch (row.kind()) {
            case GROUP -> RangeList.GROUP_TEXT_X;
            case ATTACHED -> RangeList.ATTACHED_TEXT_X;
            case MACHINE -> MachineRow.TEXT_X;
        };
    }

    /** O nome da linha de {@code target} está em edição (a linha fica sem os botões, para a caixa caber). */
    boolean editing(EditTarget target) {
        return target.equals(editor.target());
    }

    /**
     * Um clique na linha {@code row} ({@code onName}: no nome dela): devolve se é o segundo de dois cliques no
     * nome da mesma linha. O primeiro segue como um clique comum (escolhe, ou abre e fecha o grupo escolhido).
     */
    boolean doubleClick(RangeList.Row row, boolean onName) {
        long now = Util.getMillis();
        boolean second = onName && row.target().equals(lastClick) && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClick = onName && !second ? row.target() : null;
        lastClickAt = now;
        return second;
    }

    /**
     * Abre a caixa no nome da linha {@code row}, com o nome dado agora e o nome padrão como dica; na presa, o nome
     * e a dica são os dados em {@code attachedLabel} e {@code attachedDefault}.
     */
    void start(RangeList.Row row, RangeView view, String attachedLabel, Component attachedDefault) {
        if (row.kind() == RangeList.Kind.ATTACHED) {
            editor.start(row.target(), null, attachedLabel, attachedDefault);
        } else if (row.kind() == RangeList.Kind.GROUP) {
            RangeView.GroupView group = view.group(row.group());
            if (group != null) {
                editor.start(row.target(), null, group.name(), MachineTexts.groupName(group));
            }
        } else if (row.kind() == RangeList.Kind.MACHINE) {
            RangeView.MachineView machine = view.machine(row.machine());
            if (machine != null) {
                editor.start(row.target(), machine.pos(), machine.label(), MachineTexts.blockName(view, row.machine()));
            }
        }
    }

    void beginFrame() {
        placed = false;
    }

    /** A linha {@code row}, inteira à vista com o topo em {@code ry}, numa lista de {@code listX} a {@code right}. */
    void place(RangeList.Row row, int listX, int ry, int right) {
        if (editing(row.target())) {
            int left = listX + nameX(row) - 3;
            editor.place(left, ry + 1, right - 1 - left);
            placed = true;
        }
    }

    /** A caixa no fim do quadro da lista, ou o fim da edição se a linha sumiu ou saiu da vista. */
    void endFrame(RangeView view, GuiGraphics g, int mouseX, int mouseY) {
        EditTarget target = editor.target();
        if (target == null) {
            return;
        }
        if (gone(view, target)) {
            editor.finish(false);
        } else if (!placed) {
            editor.finish(true);
        } else {
            editor.render(g, mouseX, mouseY);
        }
    }

    /** O grupo sumiu, ou a máquina do índice não é mais a da edição. */
    private boolean gone(RangeView view, EditTarget target) {
        if (target.kind() == EditTarget.Kind.ATTACHED) {
            return false;
        }
        if (target.kind() == EditTarget.Kind.GROUP) {
            return view.group(target.id()) == null;
        }
        RangeView.MachineView machine = view.machine(target.id());
        return machine == null || !machine.pos().equals(editor.machinePos());
    }
}
