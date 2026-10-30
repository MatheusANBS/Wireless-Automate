package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.HEIGHT;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.NEXT_X;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.PAD;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.PREV_X;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.SLOT_X;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.TAG_W;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.TAG_X;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.on;
import static io.github.matheusanbs.wirelessautomate.client.storage.upgrade.CompactingRow.within;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.packet.CompactingStatePayload;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * O painel da Compactação, à direita do Baú: o modo global (três botões) e a lista das cadeias que têm item no Baú,
 * uma {@link CompactingRow} de 20 por cadeia. Rodinha sobre a forma alvo anda uma forma; clique nela abre o
 * {@link ChainPicker}; a rodinha no resto da lista e a barra fina rolam. As linhas, os nomes e as medidas são
 * montados quando o estado chega ({@link #sync}) ou a tela muda de tamanho, nunca no {@code render}; a lista desenha
 * só as linhas à vista. As mudanças vão ao anfitrião ({@link Actions}) e já aparecem na linha, até o estado novo do
 * servidor chegar.
 */
final class CompactingPanelView {
    /** O que o jogador mudou; o anfitrião manda ao servidor. */
    interface Actions {
        void mode(CompactingMode mode);

        void target(Item form);

        void clear(Item anyForm);
    }

    /** A linha inteira ({@link CompactingRow#END_X}), a barra de rolagem e as margens. */
    static final int WIDTH = CompactingRow.END_X + 6;
    private static final int LIST_TOP = 58;
    private static final int BAR_X = CompactingRow.END_X;
    private static final int BAR_W = 3;
    private static final int MODES_Y = 29;

    private final Font font;
    private final Actions actions;
    private final FlatButton[] modeButtons = new FlatButton[CompactingMode.ALL.length];
    private final GuiText.Fitted[] modeLabels = new GuiText.Fitted[CompactingMode.ALL.length];
    /** Recuo do texto de cada botão de modo, para centralizar sem medir a cada quadro. */
    private final int[] modeLabelX = new int[CompactingMode.ALL.length];
    private final List<CompactingRow> rows = new ArrayList<>();
    private int x;
    private int y;
    private int height;
    private int visibleRows;
    private int scroll;
    private boolean draggingBar;
    private CompactingMode mode = CompactingMode.OFF;
    private @Nullable CompactingStatePayload shown;
    private GuiText.Fitted title;
    private GuiText.Fitted globalLabel;
    private GuiText.Fitted chainsLabel;
    private GuiText.Fitted emptyLabel;
    private CompactingRow.Labels labels;
    private @Nullable ChainPicker picker;
    private int pickerRow = -1;

    CompactingPanelView(Font font, Actions actions) {
        this.font = font;
        this.actions = actions;
        GuiText.Fitted none = GuiText.fit(font, Component.empty(), 0);
        title = globalLabel = chainsLabel = emptyLabel = none;
        labels = new CompactingRow.Labels(none, none, 0, none, 0);
        for (CompactingMode each : CompactingMode.ALL) {
            int index = each.ordinal();
            modeLabels[index] = none;
            modeButtons[index] = new FlatButton(0, 0, 10, 13, UpgradePanels.tr("compacting.mode." + each.getSerializedName()),
                    (g, b, hovered) -> paintMode(g, b, hovered, each), () -> chooseMode(each));
        }
    }

    // ------------------------------------------------------------------ montagem

    /** Põe o painel com o canto interno em ({@code x}, {@code y}) e altura {@code height}, e mede os textos. */
    void layout(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
        visibleRows = Math.max(1, (height - LIST_TOP - 4) / HEIGHT);
        int inner = WIDTH - 2 * PAD;
        title = GuiText.fit(font, UpgradePanels.tr("compacting.title"), inner);
        globalLabel = GuiText.fit(font, UpgradePanels.tr("compacting.global"), inner);
        chainsLabel = GuiText.fit(font, UpgradePanels.tr("compacting.chains"), inner);
        emptyLabel = GuiText.fit(font, UpgradePanels.tr("compacting.empty"), inner);
        labels = CompactingRow.Labels.measure(font);
        layoutModes(inner);
        // Só reposiciona: as linhas (e o que o jogador já mudou nelas) ficam até o próximo estado.
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        reopenPicker(pickerRow);
    }

    /** Os três botões lado a lado, cada um com a largura do texto e a sobra repartida igual. */
    private void layoutModes(int inner) {
        int count = modeButtons.length;
        int textTotal = 0;
        for (FlatButton button : modeButtons) {
            textTotal += font.width(button.getMessage());
        }
        // Os botões dividem a borda: cada um avança a largura menos 1.
        int spare = Math.max(0, inner + count - 1 - textTotal) / count;
        int bx = x + PAD;
        for (int i = 0; i < count; i++) {
            FlatButton button = modeButtons[i];
            int w = i == count - 1 ? x + PAD + inner - bx : font.width(button.getMessage()) + spare;
            button.setWidth(w);
            button.setPosition(bx, y + MODES_Y);
            modeLabels[i] = GuiText.fit(font, button.getMessage(), w - 4);
            modeLabelX[i] = (w - font.width(modeLabels[i].shown())) / 2;
            bx += w - 1;
        }
    }

    /** Lê o estado do menu; só remonta as linhas quando chegou um estado novo. */
    void sync(@Nullable CompactingStatePayload state) {
        if (state != shown) {
            shown = state;
            rebuild();
        }
    }

    private void rebuild() {
        Item[] pickerForms = pickerRow >= 0 ? rows.get(pickerRow).forms : null;
        rows.clear();
        if (shown != null) {
            mode = shown.mode();
            for (CompactingStatePayload.Row row : shown.rows()) {
                CompactingRow built = CompactingRow.of(row, font);
                if (built != null) {
                    rows.add(built);
                }
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        reopenPicker(pickerForms == null ? -1 : rowWithForms(pickerForms));
    }

    /** A linha com exatamente essas formas, ou −1 (a cadeia saiu ou mudou com um {@code /reload}). */
    private int rowWithForms(Item[] forms) {
        for (int i = 0; i < rows.size(); i++) {
            if (Arrays.equals(rows.get(i).forms, forms)) {
                return i;
            }
        }
        return -1;
    }

    /** O seletor aberto passa para a linha {@code index} (a mesma cadeia depois de um estado novo ou do layout). */
    private void reopenPicker(int index) {
        if (picker == null) {
            return;
        }
        if (index < 0 || index < scroll || index >= scroll + visibleRows) {
            closePicker();
        } else {
            openPicker(index);
        }
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows);
    }

    // ------------------------------------------------------------------ mudanças

    private void chooseMode(CompactingMode next) {
        if (next == mode) {
            return;
        }
        mode = next;
        actions.mode(next);
        for (CompactingRow row : rows) {
            if (!row.own) {
                row.target = row.globalLevel(mode);
            }
        }
    }

    private void setTarget(CompactingRow row, int level) {
        row.target = level;
        row.own = true;
        actions.target(row.forms[level]);
    }

    private void step(CompactingRow row, int delta) {
        int next = row.stepped(delta);
        if (next >= 0) {
            setTarget(row, next);
        }
    }

    private void clear(CompactingRow row) {
        row.own = false;
        row.target = row.globalLevel(mode);
        actions.clear(row.forms[0]);
    }

    void closePicker() {
        picker = null;
        pickerRow = -1;
    }

    private void openPicker(int index) {
        CompactingRow row = rows.get(index);
        int rowY = rowY(index);
        pickerRow = index;
        picker = new ChainPicker(row.stacks, row.corners, row.target, x + SLOT_X - 3, rowY + HEIGHT, rowY,
                x + WIDTH - 2, y + height - 2);
    }

    // ------------------------------------------------------------------ geometria

    private int listTop() {
        return y + LIST_TOP;
    }

    private int rowY(int index) {
        return listTop() + (index - scroll) * HEIGHT;
    }

    /** A linha à vista sob o mouse, ou −1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + BAR_X || mouseY < listTop()) {
            return -1;
        }
        int index = scroll + (int) (mouseY - listTop()) / HEIGHT;
        return index < rows.size() && index < scroll + visibleRows ? index : -1;
    }

    private boolean onBar(double mouseX, double mouseY) {
        return within(mouseX, x + BAR_X - 1, BAR_W + 2) && within(mouseY, listTop(), visibleRows * HEIGHT);
    }

    boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x - 1 && mouseX < x + WIDTH + 2 && mouseY >= y && mouseY < y + height + 2
                || picker != null && picker.isInside(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ entrada

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker != null) {
            // Qualquer clique fecha o seletor; numa forma, ela vira o alvo.
            int level = picker.levelAt(mouseX, mouseY);
            boolean inside = picker.isInside(mouseX, mouseY);
            CompactingRow row = rows.get(pickerRow);
            closePicker();
            if (level >= 0 && level < row.forms.length && button == 0) {
                setTarget(row, level);
            }
            return inside || isInside(mouseX, mouseY);
        }
        for (FlatButton modeButton : modeButtons) {
            if (modeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (onBar(mouseX, mouseY) && button == 0) {
            draggingBar = maxScroll() > 0;
            dragBar(mouseY);
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0 || button != 0) {
            return isInside(mouseX, mouseY);
        }
        CompactingRow row = rows.get(index);
        int ry = rowY(index);
        if (on(mouseX, mouseY, x, ry, PREV_X, 10)) {
            step(row, -1);
        } else if (on(mouseX, mouseY, x, ry, NEXT_X, 10)) {
            step(row, 1);
        } else if (on(mouseX, mouseY, x, ry, SLOT_X, 18)) {
            openPicker(index);
        } else if (on(mouseX, mouseY, x, ry, TAG_X, TAG_W) && row.own) {
            clear(row);
        }
        return true;
    }

    boolean mouseDragged(double mouseY) {
        if (!draggingBar) {
            return false;
        }
        dragBar(mouseY);
        return true;
    }

    boolean mouseReleased() {
        boolean was = draggingBar;
        draggingBar = false;
        return was;
    }

    private void dragBar(double mouseY) {
        double t = (mouseY - listTop()) / Math.max(1, visibleRows * HEIGHT);
        scroll = (int) Math.round(Math.max(0, Math.min(1, t)) * maxScroll());
        closePicker();
    }

    /** Rodinha: sobre a forma alvo de uma linha anda uma forma; no resto da lista ou na barra, rola. */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        if (picker != null) {
            return picker.isInside(mouseX, mouseY) || isInside(mouseX, mouseY);
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && on(mouseX, mouseY, x, rowY(index), SLOT_X, 18)) {
            step(rows.get(index), scrollY > 0 ? 1 : -1);
            return true;
        }
        if (index >= 0 || onBar(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return isInside(mouseX, mouseY);
    }

    /** Esc fecha o seletor aberto. */
    boolean escape() {
        if (picker == null) {
            return false;
        }
        closePicker();
        return true;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, int mouseX, int mouseY, int trim) {
        // Com o seletor aberto, o que está embaixo dele não reage ao mouse.
        boolean overPicker = picker != null && picker.isInside(mouseX, mouseY);
        int mx = overPicker ? -1 : mouseX;
        int my = overPicker ? -1 : mouseY;
        GuiPaint.panel(g, x - 1, y, WIDTH + 1, height, trim);
        GuiText.draw(g, font, title, x + PAD, y + 7, GuiPaint.FG);
        GuiText.draw(g, font, globalLabel, x + PAD, y + 19, GuiPaint.MUTED);
        for (FlatButton button : modeButtons) {
            button.render(g, mx, my, 0);
        }
        GuiText.draw(g, font, chainsLabel, x + PAD, y + 47, GuiPaint.MUTED);
        if (shown != null && rows.isEmpty()) {
            GuiText.draw(g, font, emptyLabel, x + PAD, listTop() + 6, GuiPaint.MUTED);
        }
        int end = Math.min(rows.size(), scroll + visibleRows);
        int hovered = rowAt(mx, my);
        for (int i = scroll; i < end; i++) {
            rows.get(i).render(g, font, labels, x, rowY(i), i == hovered, i == pickerRow, mx, my);
        }
        renderBar(g, mouseX, mouseY);
    }

    private void renderBar(GuiGraphics g, int mouseX, int mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int top = listTop();
        int barH = visibleRows * HEIGHT;
        g.fill(x + BAR_X, top, x + BAR_X + BAR_W, top + barH, GuiPaint.INSET);
        int thumbH = Math.max(8, barH * visibleRows / rows.size());
        int thumbY = top + (barH - thumbH) * scroll / max;
        g.fill(x + BAR_X, thumbY, x + BAR_X + BAR_W, thumbY + thumbH,
                draggingBar || onBar(mouseX, mouseY) ? GuiPaint.ACCENT : GuiPaint.BEVEL_DARK);
    }

    private void paintMode(GuiGraphics g, FlatButton b, boolean hovered, CompactingMode each) {
        boolean on = each == mode;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), on ? GuiPaint.SELECTED : GuiPaint.BUTTON,
                hovered && !on ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiText.draw(g, font, modeLabels[each.ordinal()], b.getX() + modeLabelX[each.ordinal()], b.getY() + 3,
                on ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
    }

    /** Desenha o seletor aberto, por cima de tudo do painel. */
    void renderOver(GuiGraphics g, int mouseX, int mouseY) {
        if (picker != null) {
            picker.render(g, font, mouseX, mouseY);
        }
    }

    /** A dica sob o mouse (forma do seletor, forma alvo, ícone da base, etiqueta), ou {@code null}. */
    @Nullable List<Component> tooltip(double mouseX, double mouseY) {
        if (picker != null) {
            int level = picker.levelAt(mouseX, mouseY);
            CompactingRow row = rows.get(pickerRow);
            return level >= 0 && level < row.stacks.length ? List.of(row.stacks[level].getHoverName()) : null;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        CompactingRow row = rows.get(index);
        int ry = rowY(index);
        if (on(mouseX, mouseY, x, ry, SLOT_X, 18)) {
            Component name = row.target >= 0 ? row.stacks[row.target].getHoverName()
                    : UpgradePanels.tr("compacting.stopped");
            return List.of(name, UpgradePanels.tr("compacting.target.hint").withStyle(ChatFormatting.GRAY));
        }
        if (on(mouseX, mouseY, x, ry, PAD, 16)) {
            return List.of(row.stacks[0].getHoverName());
        }
        if (on(mouseX, mouseY, x, ry, TAG_X, TAG_W)) {
            return List.of(UpgradePanels.tr(row.own ? "compacting.tag.own.tooltip" : "compacting.tag.global.tooltip"));
        }
        return null;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    int rowCount() {
        return rows.size();
    }

    /** A linha da cadeia que tem {@code form}, ou −1. */
    int rowOf(Item form) {
        for (int i = 0; i < rows.size(); i++) {
            for (Item each : rows.get(i).forms) {
                if (each == form) {
                    return i;
                }
            }
        }
        return -1;
    }

    int targetOf(int index) {
        return rows.get(index).target;
    }

    /** Centro da forma alvo da linha à vista, em coordenadas da tela. */
    int[] targetCenter(int index) {
        return new int[] {x + SLOT_X + 9, rowY(index) + 10};
    }

    boolean pickerOpen() {
        return picker != null;
    }

    /** A linha do seletor aberto, ou −1. */
    int pickerRow() {
        return pickerRow;
    }

    int[] pickerCellCenter(int level) {
        return picker == null ? new int[] {-1, -1} : picker.cellCenter(level);
    }

    int[] modeCenter(CompactingMode each) {
        FlatButton button = modeButtons[each.ordinal()];
        return new int[] {button.getX() + button.getWidth() / 2, button.getY() + 6};
    }
}
