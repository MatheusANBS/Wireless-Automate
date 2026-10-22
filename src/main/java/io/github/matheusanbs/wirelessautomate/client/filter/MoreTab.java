package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BOX_Y;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.BTN_H;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterLayout.IW;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintDangerButton;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.paintTextButton;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterPaint.wrapped;
import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A aba Mais: importar e exportar o Cartão de Filtro, a explicação dos componentes e Limpar (de dois cliques). */
final class MoreTab {
    private static final long CLEAR_CONFIRM_MS = 3000;

    private final FilterScreen screen;
    private final FilterFrame frame;
    private final FilterActions actions;

    private long clearArmedUntil;
    private FlatButton importButton;
    private FlatButton exportButton;
    private FlatButton clearButton;

    MoreTab(FilterScreen screen, FilterActions actions) {
        this.screen = screen;
        this.frame = screen.frame();
        this.actions = actions;
    }

    void addWidgets() {
        importButton = screen.add(new FlatButton(0, 0, 40, BTN_H, tr("card.import"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("card.import"), false),
                () -> actions.send(Op.IMPORT_CARD, 0, 0, ""))
                .tooltip(() -> screen.view().hasCard() ? tr("card.import.tooltip") : tr("card.missing")));
        exportButton = screen.add(new FlatButton(0, 0, 40, BTN_H, tr("card.export"),
                (g, b, hovered) -> paintTextButton(g, b, hovered, tr("card.export"), false),
                () -> actions.send(Op.EXPORT_CARD, 0, 0, ""))
                .tooltip(() -> screen.view().hasCard() ? tr("card.export.tooltip") : tr("card.missing")));
        clearButton = screen.add(new FlatButton(0, 0, IW, BTN_H, tr("clear"),
                (g, b, hovered) -> paintDangerButton(g, b, hovered, clearArmed() ? tr("clear.confirm") : tr("clear"), clearArmed()),
                this::clear).tooltip(() -> clearArmed() ? tr("clear.confirm.tooltip", screen.filter().entries().size())
                        : tr("clear.tooltip")));
    }

    void refresh(int px, int y, int bottom, int half) {
        boolean more = screen.tab() == FilterScreen.Tab.MORE;
        importButton.visible = exportButton.visible = more && !screen.view().isCard();
        importButton.active = exportButton.active = screen.view().hasCard();
        importButton.setPosition(px, y + BOX_Y + 40);
        importButton.setWidth(half);
        exportButton.setPosition(px + IW - half, y + BOX_Y + 40);
        exportButton.setWidth(half);
        clearButton.visible = more;
        clearButton.active = !screen.filter().isEmpty();
        clearButton.setPosition(px, bottom - BTN_H);
    }

    private boolean clearArmed() {
        return clearArmedUntil > Util.getMillis();
    }

    /** Desarma o Limpar (trocar de aba, filtro esvaziado). */
    void disarm() {
        clearArmedUntil = 0;
    }

    /** Um clique fora do Limpar armado o desarma. */
    void disarmUnlessOver(double mouseX, double mouseY) {
        if (clearArmed() && !clearButton.isMouseOver(mouseX, mouseY)) {
            clearArmedUntil = 0;
        }
    }

    /** Captura: o Limpar fica armado por um minuto. */
    void armForPreview() {
        clearArmedUntil = Util.getMillis() + 60_000;
    }

    private void clear() {
        if (screen.filter().isEmpty()) {
            return;
        }
        if (!clearArmed()) {
            clearArmedUntil = Util.getMillis() + CLEAR_CONFIRM_MS;
            return;
        }
        clearArmedUntil = 0;
        screen.selection().clear();
        actions.send(Op.CLEAR, 0, 0, "");
    }

    void render(GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int x = frame.left() + frame.ix();
        int y = frame.top();
        int ty = y + BOX_Y + 6;
        if (!screen.view().isCard()) {
            GuiPaint.text(g, font, tr("card"), x, ty, GuiPaint.FG);
            wrapped(g, tr("card.hint"), x, ty + 11, IW, 2, GuiPaint.MUTED);
            ty = y + BOX_Y + 40 + BTN_H + 8;
        }
        wrapped(g, tr("more.hint"), x, ty, IW, Math.max(0, (y + frame.innerBottom() - BTN_H - 4 - ty) / 10),
                GuiPaint.DISABLED);
    }
}
