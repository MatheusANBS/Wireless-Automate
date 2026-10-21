package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.NoticeBand;
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
 * áreas que respondem ao mouse ({@link Hot}), recalculadas pelo snapshot, pela aba e pelo espaço
 * ({@link #arrange}) uma vez por quadro e a cada evento ({@link NodePanel}). Quem desenha e quem responde ao
 * mouse lê as posições daqui.
 */
final class PanelLayout {
    static final int TAB_H = 15;
    /** Da linha das abas ao que vem embaixo dela: a linha separadora fica no meio. */
    static final int TAB_BELOW = 5;
    static final int NET_GAP = 2;
    /** Altura do quadro das faces empilhado (o cubo de três linhas de 22 px com a folga em volta). */
    static final int FRAME_H = 82;
    private static final int MIN_FRAME_H = 58;
    /** O botão de uma face e o vão entre eles. */
    static final int CELL_W = 30;
    static final int CELL_H = 22;
    static final int CELL_GAP = 2;
    /** Limpar, Copiar e Colar. */
    static final int ACTION_W = 46;
    static final int ACTION_H = 14;
    private static final int ACTION_GAP = 3;
    /** Largura do quadro a partir da qual os três botões ficam ao lado do cubo; abaixo, embaixo dele. */
    private static final int ASIDE_MIN_W = 152;
    /** Altura do quadro das faces na disposição estreita, com os botões embaixo do cubo. */
    static final int NARROW_FRAME_H = 3 + 3 * 16 + 2 * CELL_GAP + 3 + ACTION_H + 3;
    static final int ROW_H = 12;
    static final int BUTTON_H = 14;
    /** A linha do Filtro, alta para os dois slots de cartão. */
    static final int FILTER_H = 18;
    private static final int GAP = 2;
    private static final int ROW_GAP = 3;
    /** Rótulo das linhas de ajuste e o corpo mínimo que o comporta. */
    static final int LABEL_W = 58;
    private static final int LABEL_MIN_BODY = 190;
    /** Os dois slots de cartão. */
    static final int CARDS_W = 36;
    private static final int ARROW_W = 8;
    private static final int PRIO_BUTTON_W = 12;
    static final int PRIO_VALUE_W = 22;
    private static final int EDIT_PAD = 5;
    static final int TARGET_PAD = 3;
    /** O Olho do "No mundo" (5 px) e a folga até o rótulo. */
    static final int HIGHLIGHT_EYE = 8;
    /** O que a pílula com lista ({@link PanelPainter#dropButton}) gasta além do texto: margem e seta. */
    private static final int DROP_TEXT_PAD = 15;
    /** A linha da face e o espaço depois dela. */
    private static final int FACE_LINE_H = 10 + 5;
    /** As quatro linhas de ajuste, com os espaços entre elas. */
    private static final int ROWS_H = BUTTON_H + ROW_GAP + FILTER_H + ROW_GAP + BUTTON_H + ROW_GAP + BUTTON_H;
    /** Os botões da linha da máquina, da esquerda para a direita. */
    private static final Kind[] TARGET_BUTTONS = {Kind.HIGHLIGHT, Kind.REVERT, Kind.UPDATE, Kind.UNLINK};

    enum Kind { TAB, SUMMARY, FACE, CLEAR, COPY, PASTE, TARGET, RENAME, IDLE_NETWORKS, HIGHLIGHT, REVERT, UPDATE,
        UNLINK, NETWORK, FILTER, CARDS, EDIT, PRIO_MINUS, PRIO_PLUS, REDSTONE_PREV, REDSTONE_NEXT, REDSTONE, UNDO }

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
    /** Altura da faixa do aviso no quadro atual: cresce com as linhas do texto ({@link NoticeBand}). */
    private int noticeH = NoticeBand.LINE_H;
    int bodyH;
    /**
     * A altura do retângulo empilhado, ou -1 com as partes soltas. O quadro das faces encolhe, até um mínimo, quando o
     * resto das linhas (com a barra Editando, se há) não cabe: ele cede para a Prioridade não sair.
     */
    private int stackedH = -1;

    // o que arrange() calcula
    final List<Hot> hots = new ArrayList<>();
    TabLayout.Mode tabMode = TabLayout.Mode.FULL;
    int cellW;
    int cellH;
    /** Os slots de cartão existem (a tela do roteador; o Tablet não os tem). */
    boolean cardSlots;
    /** O canto do primeiro slot de cartão na tela, ou {@code -1} sem a linha do Filtro à vista. */
    int cardX = -1;
    int cardY = -1;
    int reasonY;
    int reasonLines;
    int targetY = -1;
    /**
     * A linha Editando apertada: sem o rótulo e com os botões da máquina só com o ícone, na mesma linha da pílula
     * (o corpo não cabe com a linha dos botões).
     */
    boolean compactTarget;
    /** -1: a linha da face não cabe. */
    int faceY;
    /** -1: a linha não cabe (o corpo, sem nada mais a encolher, perde as linhas de baixo em vez de sobrepor). */
    int networkY;
    int filterY;
    int redstoneY;
    int prioY;
    /** Largura do rótulo das linhas de ajuste: 0 num corpo estreito, que as deixa sem rótulo. */
    int labelW;
    /** Largura do rótulo da prioridade, que fica mesmo sem o das outras linhas. */
    int prioLabelW;
    Component revertLabel = Component.empty();
    /** O "Tirar redes" da linha Editando só com o ícone (o rótulo não cabe). */
    boolean idleIcon;
    /** O "No mundo" da linha da máquina só com o Olho (os botões não cabem com o rótulo). */
    boolean highlightIcon;
    /** O Voltar a igual só com o ↺ (nem o rótulo mais curto cabe). */
    boolean revertIcon;

    // ------------------------------------------------------------------ retângulos

    /** Empilha abas, cubo e o resto no retângulo. */
    void layout(int x, int y, int w, int h) {
        tabsX = x;
        tabsY = y;
        tabsW = w;
        netX = x;
        netY = y + TAB_H + TAB_BELOW;
        netW = w;
        stackedH = h;
        netH = Math.max(MIN_FRAME_H, Math.min(FRAME_H, h - TAB_H - TAB_BELOW - 5 - FACE_LINE_H - ROWS_H));
        bodyX = x;
        bodyY = netY + netH + 5;
        bodyW = w;
        bodyH = y + h - bodyY;
    }

    /** Cada parte no seu retângulo: a linha das abas, o cubo e o resto. */
    void layoutSplit(int tabsX, int tabsY, int tabsW, int netX, int netY, int netW, int netH, int bodyX,
            int bodyY, int bodyW, int bodyH) {
        this.stackedH = -1;
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

    /** Altura do quadro das faces empilhado e sem aperto ({@link #layout(int, int, int, int)}). */
    static int stackedNetHeight() {
        return FRAME_H;
    }

    // ------------------------------------------------------------------ áreas do quadro

    /** Posições e áreas de clique do quadro atual, pelo snapshot, pela aba e pelo espaço. */
    void arrange(Font font, PanelModel model) {
        hots.clear();
        PanelNoticePayload notice = PanelNotices.current();
        noticeH = notice == null ? NoticeBand.LINE_H
                : NoticeBand.height(font, notice, bodyW, notice.undo() != PanelNoticePayload.Undo.NONE);
        restack(model);
        arrangeTabs(font, model);
        arrangeNet();
        arrangeBody(font, model);
    }

    /** Empilhado, refaz a altura do quadro das faces e o começo do corpo pelo que o corpo vai ter. */
    private void restack(PanelModel model) {
        if (stackedH < 0) {
            return;
        }
        int target = 0;
        if (model.rangeShown()) {
            target = ROW_H + ROW_GAP + 2;
            if (!model.targetFromList() && model.targetMachine() != null) {
                target += ROW_H + GAP;
            }
        }
        netH = Math.max(MIN_FRAME_H, Math.min(FRAME_H,
                stackedH - TAB_H - TAB_BELOW - 5 - target - FACE_LINE_H - ROWS_H));
        bodyY = netY + netH + 5;
        bodyH = tabsY + stackedH - bodyY;
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

    /**
     * O quadro das faces: o cubo desdobrado em botões e, ao lado dele (ou, no quadro estreito, embaixo), Limpar, Copiar
     * e Colar.
     */
    private void arrangeNet() {
        if (netW <= 0) {
            return;
        }
        boolean aside = netW >= ASIDE_MIN_W;
        int padX = aside ? 7 : 3;
        int padY = aside ? (netH >= 70 ? 6 : 4) : 3;
        int roomW = aside ? netW - padX - 6 - ACTION_W - ACTION_GAP - padX : netW - 2 * padX;
        int roomH = aside ? netH - 2 * padY : netH - 2 * padY - ACTION_H - ACTION_GAP;
        cellW = Math.max(14, Math.min(CELL_W, (roomW - (FaceNet.COLS - 1) * CELL_GAP) / FaceNet.COLS));
        cellH = Math.max(12, Math.min(CELL_H, (roomH - (FaceNet.ROWS - 1) * CELL_GAP) / FaceNet.ROWS));
        int cubeW = FaceNet.COLS * cellW + (FaceNet.COLS - 1) * CELL_GAP;
        int cubeH = FaceNet.ROWS * cellH + (FaceNet.ROWS - 1) * CELL_GAP;
        int cubeX = netX + padX + Math.max(0, (roomW - cubeW) / 2);
        int cubeY = netY + padY + Math.max(0, (roomH - cubeH) / 2);
        for (RemoteSide side : FaceNet.SIDES) {
            int[] cell = FaceNet.cell(side);
            hots.add(new Hot(Kind.FACE, cubeX + cell[0] * (cellW + CELL_GAP), cubeY + cell[1] * (cellH + CELL_GAP),
                    cellW, cellH, side.ordinal()));
        }
        Kind[] actions = {Kind.CLEAR, Kind.COPY, Kind.PASTE};
        if (aside) {
            int ax = netX + netW - 6 - ACTION_W;
            int total = actions.length * ACTION_H + (actions.length - 1) * ACTION_GAP;
            int ay = netY + (netH - total) / 2;
            for (Kind kind : actions) {
                hots.add(new Hot(kind, ax, ay, ACTION_W, ACTION_H, 0));
                ay += ACTION_H + ACTION_GAP;
            }
        } else {
            int each = (netW - 2 * padX - (actions.length - 1) * CELL_GAP) / actions.length;
            int ax = netX + padX;
            for (Kind kind : actions) {
                hots.add(new Hot(kind, ax, netY + netH - padY - ACTION_H, each, ACTION_H, 0));
                ax += each + CELL_GAP;
            }
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
        // sem espaço para tudo: primeiro sai a linha da face, depois os botões da máquina vão para a linha
        // Editando, só com o ícone (e a linha da face volta se couber); o que ainda faltar sai de baixo
        int room = bottom - y;
        boolean targetShown = model.rangeShown();
        boolean buttonsRow = targetShown && !model.targetFromList() && model.targetMachine() != null;
        int targetH = targetShown ? ROW_H + GAP : 0;
        compactTarget = buttonsRow && targetH + ROW_H + GAP + ROWS_H > room;
        boolean face = targetH + (buttonsRow && !compactTarget ? ROW_H + GAP : 0) + FACE_LINE_H + ROWS_H <= room;
        y = arrangeTarget(font, model, x, y, w);
        faceY = -1;
        if (face) {
            faceY = y;
            y += FACE_LINE_H;
        }
        arrangeControls(font, model, x, y, w, bottom);
    }

    /**
     * A linha Editando, com o ✎ do nome (presa, grupo ou máquina, painel editável) e o "Tirar redes" na ponta quando
     * ele vale, e, numa máquina do alcance, os botões dela; devolve o topo do que vem depois. Com a lista das
     * máquinas à vista ao lado, a linha não aparece: a lista marca o alvo e tem os botões.
     */
    private int arrangeTarget(Font font, PanelModel model, int x, int y, int w) {
        targetY = -1;
        if (model.rangeShown() && model.targetFromList()) {
            return arrangeTargetBar(font, model, x, y, w);
        }
        if (model.rangeShown()) {
            targetY = y;
            int labelW = compactTarget ? 0 : font.width(PanelModel.panelTr("target")) + 5;
            int pillW = w - labelW;
            int right = x + w;
            RangeView.MachineView machine = model.targetMachine();
            if (compactTarget && machine != null) {
                int used = arrangeCompactButtons(right, y, machine.exception(),
                        machine.state() == RangeView.MachineState.REPLACED);
                pillW -= used;
                right -= used;
            }
            boolean rename = model.readOnly() == null;
            int renameW = rename ? ROW_H + GAP : 0;
            if (model.readOnly() == null && IdleNetworks.shown(model.snapshot())) {
                int used = arrangeIdleNetworks(font, model, right, y, pillW - renameW);
                pillW -= used;
                right -= used;
            }
            if (rename) {
                // o ✎ do nome da presa, do grupo ou da máquina, logo depois da pílula
                hots.add(new Hot(Kind.RENAME, right - ROW_H, y, ROW_H, ROW_H, 0));
                pillW -= renameW;
            }
            hots.add(new Hot(Kind.TARGET, x + labelW, y, pillW, ROW_H, 0));
            if (compactTarget) {
                if (model.unlinkArmed()) {
                    // armado, o Desvincular fica sozinho na linha inteira
                    int row = y;
                    hots.removeIf(h -> h.y() == row);
                    hots.add(new Hot(Kind.UNLINK, x, y, w, ROW_H, 0));
                }
                return y + ROW_H + GAP;
            }
            y += ROW_H + GAP;
            if (machine != null) {
                arrangeTargetButtons(font, x, y, w, machine.exception(),
                        machine.state() == RangeView.MachineState.REPLACED);
                Hot unlink = hot(Kind.UNLINK);
                if (model.unlinkArmed() && unlink != null) {
                    // armado, o Desvincular fica sozinho, do começo da linha até onde ele estava (o mouse
                    // continua em cima dele) ou até onde o texto armado couber inteiro, como na lista
                    int armedW = font.width(PanelModel.tr("range.unlink.armed")) + 2 * TARGET_PAD;
                    hots.removeIf(h -> h.kind() == Kind.HIGHLIGHT || h.kind() == Kind.REVERT
                            || h.kind() == Kind.UPDATE || h.kind() == Kind.UNLINK);
                    hots.add(new Hot(Kind.UNLINK, x, y, Math.min(w, Math.max(unlink.x() + unlink.w() - x, armedW)),
                            ROW_H, 0));
                }
                y += ROW_H + GAP;
            }
        }
        return y;
    }

    /**
     * A barra Editando com a lista das máquinas à vista: a pílula do alvo e, numa máquina com exceção, o Igualar
     * ("Voltar a igual às outras") na ponta; o resto (nome, Tirar redes, Atualizar, Desvincular, o Olho) fica nos
     * botões da linha escolhida da lista.
     */
    private int arrangeTargetBar(Font font, PanelModel model, int x, int y, int w) {
        targetY = y;
        int labelW = font.width(PanelModel.panelTr("target")) + 5;
        RangeView.MachineView machine = model.targetMachine();
        compactTarget = false;
        revertIcon = false;
        // a pílula precisa do ícone do bloco, do nome inteiro e da seta; se não cabe, o Igualar vira só o ↺ e, por fim,
        // o rótulo Editando sai
        int need = font.width(model.targetName(model.snapshot().target())) + DROP_TEXT_PAD + 12;
        int equalizeW = 0;
        if (machine != null && machine.exception()) {
            revertLabel = PanelModel.panelTr("target.equalize");
            equalizeW = font.width(revertLabel) + 2 * TARGET_PAD;
            if (w - labelW - equalizeW - GAP < need) {
                revertIcon = true;
                equalizeW = ROW_H;
            }
            if (w - labelW - equalizeW - GAP < need) {
                compactTarget = true;
                labelW = 0;
            }
            hots.add(new Hot(Kind.REVERT, x + w - equalizeW, y, equalizeW, ROW_H, 0));
        }
        int pillW = w - labelW - (equalizeW > 0 ? equalizeW + GAP : 0);
        if (pillW < need && labelW > 0) {
            // nem o nome do alvo cabe ao lado do rótulo: o rótulo sai
            compactTarget = true;
            pillW += labelW;
            labelW = 0;
        }
        hots.add(new Hot(Kind.TARGET, x + labelW, y, pillW, ROW_H, 0));
        return y + ROW_H + ROW_GAP + 2;
    }

    /**
     * Os botões da máquina só com o ícone, na ponta direita ({@code right}) da linha Editando apertada, na ordem da
     * linha deles; devolve o que eles tiram da pílula.
     */
    private int arrangeCompactButtons(int right, int y, boolean exception, boolean replaced) {
        highlightIcon = true;
        revertIcon = true;
        int bx = right;
        for (int i = TARGET_BUTTONS.length - 1; i >= 0; i--) {
            Kind kind = TARGET_BUTTONS[i];
            if (kind == Kind.REVERT && !exception || kind == Kind.UPDATE && !replaced) {
                continue;
            }
            bx -= ROW_H;
            hots.add(new Hot(kind, bx, y, ROW_H, ROW_H, 0));
            bx -= GAP;
        }
        return right - bx;
    }

    /**
     * O "Tirar redes" na ponta direita ({@code right}) da linha Editando: com o rótulo, se a pílula ainda
     * mostra o nome do alvo inteiro (texto e seta, como no {@link PanelPainter#dropButton}); senão, só com o
     * ícone. Devolve o que ele tira da pílula.
     */
    private int arrangeIdleNetworks(Font font, PanelModel model, int right, int y, int pillW) {
        int bw = font.width(IdleNetworks.label()) + 2 * TARGET_PAD;
        int nameW = font.width(model.targetName(model.snapshot().target()));
        idleIcon = pillW - bw - GAP - DROP_TEXT_PAD < nameW;
        if (idleIcon) {
            bw = ROW_H;
        }
        hots.add(new Hot(Kind.IDLE_NETWORKS, right - bw, y, bw, ROW_H, 0));
        return bw + GAP;
    }

    /** Rede, Filtro (com os slots de cartão e o Editar), Redstone e Prioridade, e o Desfazer do aviso. */
    private void arrangeControls(Font font, PanelModel model, int x, int y, int w, int bottom) {
        networkY = -1;
        filterY = -1;
        redstoneY = -1;
        prioY = -1;
        cardX = -1;
        cardY = -1;
        labelW = w >= LABEL_MIN_BODY ? LABEL_W : 0;
        prioLabelW = labelW > 0 ? labelW : font.width(PanelModel.panelTr("priority")) + 4;
        int fieldX = x + labelW;
        int fieldW = w - labelW;
        if (y + BUTTON_H <= bottom) {
            networkY = y;
            hots.add(new Hot(Kind.NETWORK, fieldX, y, fieldW, BUTTON_H, 0));
        }
        y += BUTTON_H + ROW_GAP;

        if (y + FILTER_H <= bottom) {
            filterY = y;
            if (model.hasFilter()) {
                Component edit = PanelModel.tr("filter.edit");
                int editW = font.width(edit) + 2 * EDIT_PAD;
                hots.add(new Hot(Kind.EDIT, x + w - editW, y + 2, editW, BUTTON_H, 0));
                int right = x + w - editW - 3;
                if (cardSlots) {
                    cardX = right - CARDS_W;
                    cardY = y;
                    hots.add(new Hot(Kind.CARDS, cardX, y, CARDS_W, FILTER_H, 0));
                    right = cardX - 3;
                }
                hots.add(new Hot(Kind.FILTER, fieldX, y + 2, Math.max(0, right - fieldX), BUTTON_H, 0));
            }
        }
        y += FILTER_H + ROW_GAP;

        if (y + BUTTON_H <= bottom) {
            redstoneY = y;
            hots.add(new Hot(Kind.REDSTONE_NEXT, x + w - ARROW_W - 1, y, ARROW_W + 1, BUTTON_H, 0));
            hots.add(new Hot(Kind.REDSTONE_PREV, x + w - 2 * ARROW_W - 2, y, ARROW_W + 1, BUTTON_H, 0));
            hots.add(new Hot(Kind.REDSTONE, fieldX, y, fieldW - 2 * ARROW_W - 2, BUTTON_H, 0));
        }
        y += BUTTON_H + ROW_GAP;

        if (y + BUTTON_H <= bottom) {
            prioY = y;
            int px = x + prioLabelW;
            hots.add(new Hot(Kind.PRIO_MINUS, px, y, PRIO_BUTTON_W, BUTTON_H, 0));
            hots.add(new Hot(Kind.PRIO_PLUS, px + PRIO_BUTTON_W + PRIO_VALUE_W, y, PRIO_BUTTON_W, BUTTON_H, 0));
        }

        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && notice.undo() != PanelNoticePayload.Undo.NONE) {
            int undoW = NoticeBand.undoWidth(font);
            hots.add(new Hot(Kind.UNDO, x + w - undoW, noticeY(), undoW, NoticeBand.LINE_H, 0));
        }
    }

    /**
     * No mundo, Voltar a igual às outras (só na exceção), Atualizar (só na trocada) e Desvincular, lado a lado: o
     * texto da especificação; se não couberem, o No mundo só com o Olho, um Voltar mais curto ("Voltar ao
     * perfil", "Voltar") e, por fim, o Voltar só com o ↺; se nem assim, cada um fica com até uma parte igual.
     */
    private void arrangeTargetButtons(Font font, int x, int y, int w, boolean exception, boolean replaced) {
        int unlinkW = font.width(PanelModel.tr("range.unlink")) + 2 * TARGET_PAD;
        int updateW = replaced ? font.width(PanelModel.tr("range.update")) + 2 * TARGET_PAD : 0;
        int highlightW = font.width(LitFooter.SHOW) + 2 * TARGET_PAD + HIGHLIGHT_EYE;
        int count = 2 + (replaced ? 1 : 0) + (exception ? 1 : 0);
        int revertW = 0;
        if (exception) {
            revertLabel = PanelModel.panelTr("target.revert");
            revertW = font.width(revertLabel) + 2 * TARGET_PAD;
        }
        highlightIcon = total(highlightW, revertW, updateW, unlinkW) > w;
        if (highlightIcon) {
            highlightW = ROW_H;
        }
        if (exception) {
            for (String shorter : new String[] {"range.revert", "range.revert.short"}) {
                if (total(highlightW, revertW, updateW, unlinkW) > w) {
                    revertLabel = PanelModel.tr(shorter);
                    revertW = font.width(revertLabel) + 2 * TARGET_PAD;
                }
            }
        }
        revertIcon = exception && total(highlightW, revertW, updateW, unlinkW) > w;
        if (revertIcon) {
            revertW = ROW_H;
        }
        if (total(highlightW, revertW, updateW, unlinkW) > w) {
            int share = (w - (count - 1) * GAP) / count;
            unlinkW = Math.min(unlinkW, share);
            updateW = Math.min(updateW, share);
            if (exception) {
                revertW = Math.max(ROW_H, w - total(highlightW, 0, updateW, unlinkW) - GAP);
            }
        }
        int bx = x;
        for (Kind kind : TARGET_BUTTONS) {
            int bw = switch (kind) {
                case HIGHLIGHT -> highlightW;
                case REVERT -> revertW;
                case UPDATE -> updateW;
                default -> unlinkW;
            };
            if (bw > 0) {
                hots.add(new Hot(kind, bx, y, Math.min(bw, x + w - bx), ROW_H, 0));
                bx += bw + GAP;
            }
        }
    }

    /** Largura dos botões da linha da máquina, com o espaço entre eles; largura 0 é o botão que não aparece. */
    private static int total(int... widths) {
        int sum = 0;
        int shown = 0;
        for (int width : widths) {
            if (width > 0) {
                sum += width;
                shown++;
            }
        }
        return sum + Math.max(0, shown - 1) * GAP;
    }

    // ------------------------------------------------------------------ consultas

    /** Topo da faixa do aviso, na base do corpo. */
    int noticeY() {
        return bodyY + bodyH - noticeH;
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
        for (Hot hot : hots) {
            if (hot.contains(mx, my)) {
                return hot;
            }
        }
        return null;
    }

    /** O ponto está na faixa do aviso do painel (que fica por cima do que estiver embaixo dela). */
    boolean inNotice(double mx, double my) {
        return bodyW > 0 && PanelNotices.current() != null && mx >= bodyX && mx < bodyX + bodyW
                && my >= noticeY() && my < noticeY() + noticeH;
    }
}
