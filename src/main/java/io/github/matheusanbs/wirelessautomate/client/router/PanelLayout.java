package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.TabLayout;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A geometria do painel do nó ({@link NodePanel}): os retângulos das abas, do cubo e do resto, e as
 * áreas que respondem ao mouse ({@link Hot}), recalculadas a cada quadro pelo snapshot, pela aba e pelo
 * espaço ({@link #arrange}). Quem desenha e quem responde ao mouse lê as posições daqui.
 */
final class PanelLayout {
    static final int TAB_H = 15;
    /** Da linha das abas ao que vem embaixo dela: a linha separadora fica no meio. */
    static final int TAB_BELOW = 5;
    static final int NET_GAP = 2;
    /** Altura da célula do cubo empilhado. */
    private static final int CELL_H = 16;
    static final int ROW_H = 12;
    static final int BUTTON_H = 14;
    private static final int GAP = 2;
    private static final int MODE_GAP = 2;
    /** O ícone do modo (a 3/4) e a folga até o nome. */
    static final int MODE_TEXT_X = 16;
    private static final int PRIO_BUTTON_W = 12;
    static final int PRIO_VALUE_W = 22;
    private static final int EDIT_PAD = 5;
    static final int TARGET_PAD = 3;
    static final int NOTICE_H = 13;
    private static final PortMode[] MODES = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE};

    enum Kind { TAB, SUMMARY, FACE, TARGET, REVERT, UNLINK, MODE, NETWORK, FILTER, EDIT, PRIO_MINUS, PRIO_PLUS,
        REDSTONE, UNDO }

    /** Uma área que responde ao mouse. */
    record Hot(Kind kind, int x, int y, int w, int h, int arg) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int[] center() {
            return new int[] {x + w / 2, y + h / 2};
        }
    }

    // retângulos: largura 0 = a parte não aparece
    int tabsX;
    int tabsY;
    int tabsW;
    int netX;
    int netY;
    int netW;
    int netH;
    int bodyX;
    int bodyY;
    int bodyW;
    int bodyH;

    // o que arrange() calcula a cada quadro
    final List<Hot> hots = new ArrayList<>();
    TabLayout.Mode tabMode = TabLayout.Mode.FULL;
    int cellW;
    int cellH;
    int reasonY;
    int reasonLines;
    int targetY = -1;
    int faceY;
    boolean modeNames;
    int phraseY = -1;
    int phraseLines;
    int networkY;
    int filterY;
    int prioY;
    boolean prioLabel;
    Component revertLabel = Component.empty();
    Component redstoneLabel = Component.empty();

    // ------------------------------------------------------------------ retângulos

    /** Empilha abas, cubo e o resto no retângulo. */
    void layout(int x, int y, int w, int h) {
        tabsX = x;
        tabsY = y;
        tabsW = w;
        netX = x;
        netY = y + TAB_H + TAB_BELOW;
        netW = w;
        netH = 3 * CELL_H + 2 * NET_GAP;
        bodyX = x;
        bodyY = netY + netH + 5;
        bodyW = w;
        bodyH = y + h - bodyY;
    }

    /** Cada parte no seu retângulo: a linha das abas, o cubo e o resto. */
    void layoutSplit(int tabsX, int tabsY, int tabsW, int netX, int netY, int netW, int netH, int bodyX,
            int bodyY, int bodyW, int bodyH) {
        this.tabsX = tabsX;
        this.tabsY = tabsY;
        this.tabsW = tabsW;
        this.netX = netX;
        this.netY = netY;
        this.netW = netW;
        this.netH = netH;
        this.bodyX = bodyX;
        this.bodyY = bodyY;
        this.bodyW = bodyW;
        this.bodyH = bodyH;
    }

    /** Altura do cubo empilhado ({@link #layout(int, int, int, int)}). */
    static int stackedNetHeight() {
        return 3 * CELL_H + 2 * NET_GAP;
    }

    // ------------------------------------------------------------------ áreas do quadro

    /** Posições e áreas de clique do quadro atual, pelo snapshot, pela aba e pelo espaço. */
    void arrange(Font font, PanelModel model) {
        hots.clear();
        arrangeTabs(font, model);
        arrangeNet();
        arrangeBody(font, model);
    }

    private void arrangeTabs(Font font, PanelModel model) {
        if (tabsW <= 0) {
            return;
        }
        List<ResourceType> types = LoadedTypes.LIST;
        Component summary = model.networkSummary();
        int summaryW = font.width(summary) + 2;
        // o resumo "N redes" sai quando nem as abas só com ícone cabem ao lado dele
        int icons = types.size() * TabLayout.ICON_TAB + Math.max(0, types.size() - 1) * TabLayout.GAP;
        boolean showSummary = tabsW - summaryW - 6 >= icons;
        if (showSummary) {
            hots.add(new Hot(Kind.SUMMARY, tabsX + tabsW - summaryW, tabsY + 1, summaryW, 13, 0));
        }
        int[] full = new int[types.size()];
        int active = 0;
        for (int i = 0; i < types.size(); i++) {
            full[i] = 4 + ResourceStyle.ICON + 3 + font.width(ResourceStyle.name(types.get(i))) + 6;
            if (types.get(i) == model.type()) {
                active = i;
            }
        }
        TabLayout.Result layout = TabLayout.choose(full, active, showSummary ? tabsW - summaryW - 6 : tabsW);
        tabMode = layout.mode();
        int x = tabsX;
        for (int i = 0; i < types.size(); i++) {
            hots.add(new Hot(Kind.TAB, x, tabsY, layout.widths()[i], TAB_H, types.get(i).ordinal()));
            x += layout.widths()[i] + TabLayout.GAP;
        }
    }

    private void arrangeNet() {
        if (netW <= 0) {
            return;
        }
        cellW = (netW - (FaceNet.COLS - 1) * NET_GAP) / FaceNet.COLS;
        cellH = (netH - (FaceNet.ROWS - 1) * NET_GAP) / FaceNet.ROWS;
        for (RemoteSide side : RemoteSide.values()) {
            int[] cell = FaceNet.cell(side);
            hots.add(new Hot(Kind.FACE, netX + cell[0] * (cellW + NET_GAP), netY + cell[1] * (cellH + NET_GAP), cellW,
                    cellH, side.ordinal()));
        }
    }

    private void arrangeBody(Font font, PanelModel model) {
        if (bodyW <= 0) {
            return;
        }
        int x = bodyX;
        int w = bodyW;
        int y = bodyY;
        int bottom = bodyY + bodyH;
        Component readOnly = model.readOnly();
        reasonLines = readOnly == null ? 0 : GuiText.lineCount(font, readOnly, w, 2);
        reasonY = y;
        if (reasonLines > 0) {
            y += reasonLines * 10 + 3;
        }
        y = arrangeTarget(font, model, x, y, w);
        faceY = y;
        y += 10 + GAP;

        // o que fica sempre: Rede, Filtro e a linha da prioridade com o redstone
        int fixed = ROW_H + GAP + BUTTON_H + GAP + BUTTON_H;
        int free = bottom - y - fixed;
        int modesH = arrangeModes(font, x, y, w, free);
        y += modesH + GAP;
        free -= modesH + GAP;

        // a frase do modo, inteira, só se couber
        List<?> lines = font.split(model.phrase(), w);
        phraseY = -1;
        phraseLines = 0;
        if (lines.size() <= 2 && free >= lines.size() * 10 + 1) {
            phraseY = y;
            phraseLines = lines.size();
            y += phraseLines * 10 + 1;
        }
        arrangeControls(font, model, x, y, w);
    }

    /** A linha Editando e, numa máquina do alcance, os botões dela; devolve o topo do que vem depois. */
    private int arrangeTarget(Font font, PanelModel model, int x, int y, int w) {
        targetY = -1;
        if (model.rangeShown()) {
            targetY = y;
            int labelW = font.width(PanelModel.panelTr("target")) + 5;
            hots.add(new Hot(Kind.TARGET, x + labelW, y, w - labelW, ROW_H, 0));
            y += ROW_H + GAP;
            RangeView.MachineView machine = model.targetMachine();
            if (machine != null) {
                arrangeTargetButtons(font, x, y, w, machine.exception());
                y += ROW_H + GAP;
            }
        }
        return y;
    }

    /** Os quatro modos em uma ou duas linhas, com ou sem o nome; devolve a altura deles. */
    private int arrangeModes(Font font, int x, int y, int w, int free) {
        int maxName = 0;
        for (PortMode mode : MODES) {
            maxName = Math.max(maxName, font.width(PanelModel.modeName(mode)));
        }
        int oneRow = (w - 3 * MODE_GAP) / 4;
        int twoRows = (w - MODE_GAP) / 2;
        int modeRows;
        if (oneRow >= MODE_TEXT_X + maxName + 2) {
            modeRows = 1;
            modeNames = true;
        } else if (twoRows >= MODE_TEXT_X + maxName + 2 && free >= 2 * BUTTON_H + MODE_GAP + GAP) {
            modeRows = 2;
            modeNames = true;
        } else {
            modeRows = 1;
            modeNames = false;
        }
        int cell = modeRows == 1 ? oneRow : twoRows;
        int perRow = modeRows == 1 ? 4 : 2;
        for (int i = 0; i < MODES.length; i++) {
            hots.add(new Hot(Kind.MODE, x + (i % perRow) * (cell + MODE_GAP), y + (i / perRow) * (BUTTON_H + MODE_GAP),
                    cell, BUTTON_H, MODES[i].ordinal()));
        }
        return modeRows * BUTTON_H + (modeRows - 1) * MODE_GAP;
    }

    /** A Rede, o Filtro com Editar, a prioridade com o redstone e o Desfazer do aviso. */
    private void arrangeControls(Font font, PanelModel model, int x, int y, int w) {
        networkY = y;
        int netLabelW = font.width(PanelModel.tr("network.face")) + 5;
        hots.add(new Hot(Kind.NETWORK, x + netLabelW, y, w - netLabelW, ROW_H, 0));
        y += ROW_H + GAP;

        filterY = y;
        if (model.hasFilter()) {
            Component edit = PanelModel.tr("filter.edit");
            int editW = font.width(edit) + 2 * EDIT_PAD;
            hots.add(new Hot(Kind.EDIT, x + w - editW, y, editW, BUTTON_H, 0));
            int labelW = font.width(PanelModel.tr("filter.label")) + 5;
            hots.add(new Hot(Kind.FILTER, x + labelW, y, w - labelW - editW - 3, BUTTON_H, 0));
        }
        y += BUTTON_H + GAP;

        prioY = y;
        int labelW = font.width(PanelModel.panelTr("priority")) + 4;
        int controlsW = 2 * PRIO_BUTTON_W + PRIO_VALUE_W + 2;
        int minRedstone = font.width(PanelModel.redstoneName(RedstoneMode.HIGH)) + 16;
        prioLabel = w - labelW - controlsW - 4 >= minRedstone;
        int px = x + (prioLabel ? labelW : 0);
        hots.add(new Hot(Kind.PRIO_MINUS, px, y, PRIO_BUTTON_W, BUTTON_H, 0));
        hots.add(new Hot(Kind.PRIO_PLUS, px + PRIO_BUTTON_W + PRIO_VALUE_W + 2, y, PRIO_BUTTON_W, BUTTON_H, 0));
        int rx = px + controlsW + 4;
        int rw = x + w - rx;
        hots.add(new Hot(Kind.REDSTONE, rx, y, rw, BUTTON_H, 0));
        Component full = PanelModel.tr("redstone.narration", PanelModel.redstoneName(model.view().redstone()));
        redstoneLabel = font.width(full) <= rw - 16 ? full : PanelModel.redstoneName(model.view().redstone());

        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && notice.undo() != PanelNoticePayload.Undo.NONE) {
            Component undo = PanelModel.panelTr("notice.undo");
            int undoW = font.width(undo) + 8;
            hots.add(new Hot(Kind.UNDO, x + w - undoW, noticeY(), undoW, NOTICE_H, 0));
        }
    }

    /**
     * Voltar a igual às outras (só na exceção) e Desvincular, lado a lado: o texto da especificação, ou o
     * curto se os dois não couberem; se nem assim, o Desvincular fica com até metade.
     */
    private void arrangeTargetButtons(Font font, int x, int y, int w, boolean exception) {
        Component unlink = PanelModel.tr("range.unlink");
        int unlinkW = font.width(unlink) + 2 * TARGET_PAD;
        int bx = x;
        if (exception) {
            revertLabel = PanelModel.panelTr("target.revert");
            int revertW = font.width(revertLabel) + 2 * TARGET_PAD;
            if (revertW + GAP + unlinkW > w) {
                revertLabel = PanelModel.tr("range.revert");
                revertW = font.width(revertLabel) + 2 * TARGET_PAD;
            }
            if (revertW + GAP + unlinkW > w) {
                unlinkW = Math.min(unlinkW, (w - GAP) / 2);
                revertW = w - GAP - unlinkW;
            }
            hots.add(new Hot(Kind.REVERT, bx, y, revertW, ROW_H, 0));
            bx += revertW + GAP;
        }
        hots.add(new Hot(Kind.UNLINK, bx, y, Math.min(unlinkW, w), ROW_H, 0));
    }

    // ------------------------------------------------------------------ consultas

    /** Topo da faixa do aviso, na base do corpo. */
    int noticeY() {
        return bodyY + bodyH - NOTICE_H;
    }

    @Nullable Hot hot(Kind kind) {
        for (Hot hot : hots) {
            if (hot.kind() == kind) {
                return hot;
            }
        }
        return null;
    }

    @Nullable Hot hot(Kind kind, int arg) {
        for (Hot hot : hots) {
            if (hot.kind() == kind && hot.arg() == arg) {
                return hot;
            }
        }
        return null;
    }

    @Nullable Hot hotAt(double mx, double my) {
        // o aviso fica por cima do que estiver embaixo dele
        Hot undo = hot(Kind.UNDO);
        if (undo != null && undo.contains(mx, my)) {
            return undo;
        }
        if (netW > 0) {
            RemoteSide side = FaceNet.sideAt((int) Math.floor(mx) - netX, (int) Math.floor(my) - netY, cellW, cellH,
                    NET_GAP);
            if (side != null) {
                return hot(Kind.FACE, side.ordinal());
            }
        }
        for (Hot hot : hots) {
            if (hot.kind() != Kind.FACE && hot.contains(mx, my)) {
                return hot;
            }
        }
        return null;
    }

    /** O ponto está na faixa do aviso do painel (que fica por cima do que estiver embaixo dela). */
    boolean inNotice(double mx, double my) {
        return bodyW > 0 && PanelNotices.current() != null && mx >= bodyX && mx < bodyX + bodyW
                && my >= noticeY() && my < noticeY() + NOTICE_H;
    }
}
