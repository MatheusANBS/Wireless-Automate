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
    /** O Olho do "No mundo" (5 px) e a folga até o rótulo. */
    static final int HIGHLIGHT_EYE = 8;
    /** O que a pílula com lista ({@link PanelPainter#dropButton}) gasta além do texto: margem e seta. */
    private static final int DROP_TEXT_PAD = 15;
    private static final PortMode[] MODES = {PortMode.EXTRACT, PortMode.INSERT, PortMode.BOTH, PortMode.NONE};
    /** Os botões da linha da máquina, da esquerda para a direita. */
    private static final Kind[] TARGET_BUTTONS = {Kind.HIGHLIGHT, Kind.REVERT, Kind.UPDATE, Kind.UNLINK};

    enum Kind { TAB, SUMMARY, FACE, TARGET, RENAME, IDLE_NETWORKS, HIGHLIGHT, REVERT, UPDATE, UNLINK, MODE, NETWORK,
        FILTER, EDIT, PRIO_MINUS, PRIO_PLUS, REDSTONE, UNDO }

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

    // o que arrange() calcula
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
    /** O "Tirar redes" da linha Editando só com o ícone (o rótulo não cabe). */
    boolean idleIcon;
    /** O "No mundo" da linha da máquina só com o Olho (os botões não cabem com o rótulo). */
    boolean highlightIcon;
    /** O Voltar a igual só com o ↺ (nem o rótulo mais curto cabe). */
    boolean revertIcon;
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
        PanelNoticePayload notice = PanelNotices.current();
        noticeH = notice == null ? NoticeBand.LINE_H
                : NoticeBand.height(font, notice, bodyW, notice.undo() != PanelNoticePayload.Undo.NONE);
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

    /**
     * A linha Editando, com o ✎ do nome (grupo ou máquina, painel editável) e o "Tirar redes" na ponta quando
     * ele vale, e, numa máquina do alcance, os botões dela; devolve o topo do que vem depois. Com a lista das
     * máquinas à vista ao lado, a linha não aparece: a lista marca o alvo e tem os botões.
     */
    private int arrangeTarget(Font font, PanelModel model, int x, int y, int w) {
        targetY = -1;
        if (model.rangeShown() && !model.targetFromList()) {
            targetY = y;
            int labelW = font.width(PanelModel.panelTr("target")) + 5;
            int pillW = w - labelW;
            int right = x + w;
            boolean rename = model.readOnly() == null && !model.snapshot().target().attached();
            int renameW = rename ? ROW_H + GAP : 0;
            if (model.readOnly() == null && IdleNetworks.shown(model.snapshot())) {
                int used = arrangeIdleNetworks(font, model, right, y, pillW - renameW);
                pillW -= used;
                right -= used;
            }
            if (rename) {
                // o ✎ do nome do grupo ou da máquina, logo depois da pílula
                hots.add(new Hot(Kind.RENAME, right - ROW_H, y, ROW_H, ROW_H, 0));
                pillW -= renameW;
            }
            hots.add(new Hot(Kind.TARGET, x + labelW, y, pillW, ROW_H, 0));
            y += ROW_H + GAP;
            RangeView.MachineView machine = model.targetMachine();
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
        int highlightW = font.width(LitFooter.tr("show")) + 2 * TARGET_PAD + HIGHLIGHT_EYE;
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
                && my >= noticeY() && my < noticeY() + noticeH;
    }
}
