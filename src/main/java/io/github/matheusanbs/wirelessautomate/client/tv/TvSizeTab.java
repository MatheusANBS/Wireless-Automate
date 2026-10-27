package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.*;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvButtons.Label;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvLoaded;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Tamanho: Largura e Altura com ‹ › (Shift: ±4), de 1 ao teto do servidor, o canto de onde a tela cresce (↗ ↖
 * ✥; uma âncora fora dos três não acende nenhum), a prévia da parede e o Aplicar, desligado com problema. A validação
 * é a do servidor ({@code TvAreaCheck}) no mundo do cliente, refeita só quando o tamanho pedido muda, e os textos que
 * dependem dele são medidos junto.
 */
final class TvSizeTab {
    private static final int SHIFT_STEP = 4;
    private static final TvArea.Grow[] GROWS = TvArea.Grow.values();

    private final TvMenu menu;
    private final TvScreenActions actions;
    private final TvAreaPreview preview = new TvAreaPreview();
    private int width;
    private int height;
    private int anchorCol;
    private int anchorRow;
    private @Nullable TvArea.Grow grow;
    private TvAreaCheck.Problem problem = TvAreaCheck.Problem.NONE;

    private Font font;
    private GuiText.Fitted widthLabel;
    private GuiText.Fitted heightLabel;
    private GuiText.Fitted unitText;
    private TvScreenText.Wrapped growLabel;
    private Label minus;
    private Label plus;
    private final Label[] growNames = new Label[GROWS.length];
    private Label apply;
    private Label widthValue;
    private Label heightValue;
    private GuiText.Fitted message;

    TvSizeTab(TvMenu menu, TvScreenActions actions) {
        this.menu = menu;
        this.actions = actions;
        TvArea area = menu.area();
        width = area.width();
        height = area.height();
        anchorCol = area.anchorCol();
        anchorRow = area.anchorRow();
        grow = area.grow();
    }

    /** Mede os textos fixos e valida o tamanho atual (na abertura e quando a janela muda). */
    void layout(Font font) {
        this.font = font;
        widthLabel = GuiText.fit(font, TvScreenText.key("width"), WIDTH_LABEL.w());
        heightLabel = GuiText.fit(font, TvScreenText.key("height"), HEIGHT_LABEL.w());
        unitText = GuiText.fit(font, TvScreenText.key("blocks"), UNIT_W);
        growLabel = TvScreenText.wrap(font, TvScreenText.key("grow"), GROW_LABEL.w(), 2);
        minus = TvButtons.label(font, Component.literal("‹"), STEP - 2);
        plus = TvButtons.label(font, Component.literal("›"), STEP - 2);
        for (int i = 0; i < GROWS.length; i++) {
            growNames[i] = TvButtons.label(font, TvScreenText.growName(GROWS[i]), GROW_W - 8);
        }
        apply = TvButtons.label(font, TvScreenText.key("apply"), APPLY.w() - 6);
        changed();
    }

    TvArea requested() {
        return new TvArea(width, height, anchorCol, anchorRow);
    }

    TvAreaCheck.Problem problem() {
        return problem;
    }

    /** O tamanho pedido mudou: valida de novo, remonta a prévia e mede os textos que dependem dele. */
    private void changed() {
        TvArea area = requested();
        Level level = Minecraft.getInstance().level;
        BlockState state = level == null ? null : level.getBlockState(menu.pos());
        Direction facing = state != null && state.hasProperty(TvBlock.FACING) ? state.getValue(TvBlock.FACING)
                : Direction.NORTH;
        problem = level == null || state == null || !state.hasProperty(TvBlock.FACING) ? TvAreaCheck.Problem.NONE
                : TvAreaCheck.check(level, menu.pos(), facing, area, menu.maxWidth(), menu.maxHeight(),
                        TvLoaded.of(level));
        preview.update(area, problem, menu.pos(), facing);
        widthValue = TvButtons.label(font, Component.literal(Integer.toString(width)), VALUE_W - 4);
        heightValue = TvButtons.label(font, Component.literal(Integer.toString(height)), VALUE_W - 4);
        message = GuiText.fit(font, TvScreenText.areaLine(area, problem, menu.maxWidth(), menu.maxHeight()),
                MESSAGE.w());
    }

    private void resize(int newWidth, int newHeight) {
        newWidth = Math.max(1, Math.min(menu.maxWidth(), newWidth));
        newHeight = Math.max(1, Math.min(menu.maxHeight(), newHeight));
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = newWidth;
        height = newHeight;
        if (grow != null) {
            TvArea grown = TvArea.grown(width, height, grow);
            anchorCol = grown.anchorCol();
            anchorRow = grown.anchorRow();
        } else {
            anchorCol = Math.min(anchorCol, width - 1);
            anchorRow = Math.min(anchorRow, height - 1);
        }
        changed();
    }

    private void setGrow(TvArea.Grow chosen) {
        TvArea grown = TvArea.grown(width, height, chosen);
        grow = chosen;
        if (grown.anchorCol() != anchorCol || grown.anchorRow() != anchorRow) {
            anchorCol = grown.anchorCol();
            anchorRow = grown.anchorRow();
            changed();
        }
    }

    void render(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        GuiText.draw(g, font, widthLabel, left + WIDTH_LABEL.x(), top + WIDTH_LABEL.y(), GuiPaint.MUTED);
        stepper(g, left, top, WIDTH_ROW, widthValue, mouseX, mouseY);
        GuiText.draw(g, font, heightLabel, left + HEIGHT_LABEL.x(), top + HEIGHT_LABEL.y(), GuiPaint.MUTED);
        stepper(g, left, top, HEIGHT_ROW, heightValue, mouseX, mouseY);
        growLabel.draw(g, font, left + GROW_LABEL.x(), top + GROW_LABEL.y(), GuiPaint.MUTED);
        for (int i = 0; i < GROWS.length; i++) {
            Rect r = GROW_BUTTONS[i];
            TvButtons.choice(g, font, r, left, top, r.contains(left, top, mouseX, mouseY), grow == GROWS[i],
                    growNames[i]);
        }
        TvButtons.primary(g, font, APPLY, left, top, APPLY.contains(left, top, mouseX, mouseY), problem.ok(),
                GuiPaint.ACCENT, apply);
        GuiText.draw(g, font, message, left + MESSAGE.x(), top + MESSAGE.y(),
                problem.ok() ? GuiPaint.MUTED : GuiPaint.DANGER);
        preview.render(g, left, top);
    }

    private void stepper(GuiGraphics g, int left, int top, int row, Label value, int mouseX, int mouseY) {
        int y = top + row;
        TvButtons.symbol(g, font, left + MINUS_X, y, STEP, inside(left + MINUS_X, y, mouseX, mouseY), minus);
        GuiPaint.box(g, left + VALUE_X, y, VALUE_W, STEP, GuiPaint.INSET, GuiPaint.LINE);
        GuiText.draw(g, font, value.text(), left + VALUE_X + (VALUE_W - value.width()) / 2 + 1, y + 3, GuiPaint.FG);
        TvButtons.symbol(g, font, left + PLUS_X, y, STEP, inside(left + PLUS_X, y, mouseX, mouseY), plus);
        GuiText.draw(g, font, unitText, left + UNIT_X, y + 3, GuiPaint.MUTED);
    }

    private static boolean inside(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + STEP && mouseY >= y && mouseY < y + STEP;
    }

    boolean mouseClicked(int left, int top, double mouseX, double mouseY) {
        int step = Screen.hasShiftDown() ? SHIFT_STEP : 1;
        if (inside(left + MINUS_X, top + WIDTH_ROW, mouseX, mouseY)) {
            resize(width - step, height);
        } else if (inside(left + PLUS_X, top + WIDTH_ROW, mouseX, mouseY)) {
            resize(width + step, height);
        } else if (inside(left + MINUS_X, top + HEIGHT_ROW, mouseX, mouseY)) {
            resize(width, height - step);
        } else if (inside(left + PLUS_X, top + HEIGHT_ROW, mouseX, mouseY)) {
            resize(width, height + step);
        } else if (APPLY.contains(left, top, mouseX, mouseY)) {
            if (problem.ok()) {
                actions.size(requested());
            }
        } else {
            for (int i = 0; i < GROWS.length; i++) {
                if (GROW_BUTTONS[i].contains(left, top, mouseX, mouseY)) {
                    setGrow(GROWS[i]);
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /** A dica dos ‹ ›, ou {@code null}. */
    @Nullable List<Component> tooltip(int left, int top, double mouseX, double mouseY) {
        return onStep(left, top + WIDTH_ROW, mouseX, mouseY) || onStep(left, top + HEIGHT_ROW, mouseX, mouseY)
                ? List.of(TvScreenText.key("step.tooltip")) : null;
    }

    private static boolean onStep(int left, int y, double mouseX, double mouseY) {
        return inside(left + MINUS_X, y, mouseX, mouseY) || inside(left + PLUS_X, y, mouseX, mouseY);
    }

    int[] minusCenter(int left, int top, boolean widthRow) {
        return new int[] {left + MINUS_X + STEP / 2, top + (widthRow ? WIDTH_ROW : HEIGHT_ROW) + STEP / 2};
    }

    int[] plusCenter(int left, int top, boolean widthRow) {
        return new int[] {left + PLUS_X + STEP / 2, top + (widthRow ? WIDTH_ROW : HEIGHT_ROW) + STEP / 2};
    }

    /** O tamanho pedido direto, sem os cliques (gancho do e2e: o mesmo caminho dos botões). */
    void requestForTests(int newWidth, int newHeight) {
        resize(newWidth, newHeight);
    }

    /** Valida de novo o tamanho pedido (gancho do e2e, depois de mudar o mundo com a tela aberta). */
    void recheckForTests() {
        changed();
    }

    @Nullable TvArea.Grow grow() {
        return grow;
    }
}
