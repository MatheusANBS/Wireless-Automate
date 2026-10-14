package io.github.matheusanbs.wirelessautomate.client.storage;

import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.CELL;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.GRID_X;
import static io.github.matheusanbs.wirelessautomate.client.storage.StorageListLayout.GRID_Y;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * A grade da lista: as células com o tipo e a quantidade, a rolagem e a barra, e os cliques, o arrasto
 * e a rodinha sobre ela (o que cada gesto faz está na {@link StorageListScreen}). Quem manda as ações
 * ao servidor é a tela.
 */
final class StorageGrid {
    /** Quantidade no canto da célula: branco com sombra, como as pilhas do próprio jogo. */
    private static final int COUNT_TEXT = 0xFFFFFFFF;
    /** Intervalo do duplo clique, o mesmo dos slots do vanilla. */
    private static final long DOUBLE_CLICK_MS = 250;

    private final StorageListScreen screen;
    private final StorageListMenu<?> menu;
    private final ShownList list;
    private int scrollRow;
    private boolean draggingBar;
    /**
     * Shift + arrasto na grade do Baú (como o Mouse Tweaks faz nos slots, que a grade não tem):
     * os tipos já mandados para o inventário neste arrasto, para cada um ir uma vez só.
     */
    private @Nullable List<ItemStack> shiftDragTaken;
    /** O último Shift + clique com item no cursor num tipo do Baú e quando, para o Shift + duplo clique. */
    private ItemStack lastClickKey = ItemStack.EMPTY;
    private long lastClickTime;

    StorageGrid(StorageListScreen screen, StorageListMenu<?> menu, ShownList list) {
        this.screen = screen;
        this.menu = menu;
        this.list = list;
    }

    private int left() {
        return screen.getGuiLeft();
    }

    private int top() {
        return screen.getGuiTop();
    }

    void resetScroll() {
        scrollRow = 0;
    }

    void clampScroll() {
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    int maxScroll() {
        int cols = screen.cols();
        int lines = (list.size() + cols - 1) / cols;
        return Math.max(0, lines - screen.rows());
    }

    /** Centro da célula {@code index} da página atual, em coordenadas da tela. */
    int[] cellCenter(int index) {
        int cols = screen.cols();
        return new int[] {left() + GRID_X + (index % cols) * CELL + 8, top() + GRID_Y + (index / cols) * CELL + 8};
    }

    /** O tipo na célula sob o mouse, ou {@code null}. */
    @Nullable Object keyAt(double mouseX, double mouseY) {
        int cols = screen.cols();
        int col = (int) Math.floor((mouseX - left() - GRID_X + 1) / CELL);
        int row = (int) Math.floor((mouseY - top() - GRID_Y + 1) / CELL);
        if (col < 0 || col >= cols || row < 0 || row >= screen.rows()) {
            return null;
        }
        int index = (scrollRow + row) * cols + col;
        return index < list.size() ? list.get(index) : null;
    }

    /** O tipo sob o mouse como ingrediente do JEI, com o canto da célula; {@code null} fora da grade. */
    StorageListScreen.@Nullable Hovered ingredientAt(double mouseX, double mouseY) {
        Object key = keyAt(mouseX, mouseY);
        Object ingredient = switch (key) {
            case null -> null;
            case ItemStack stack -> stack.copyWithCount(1);
            case FluidStack fluid -> fluid.copyWithAmount(1_000);
            case ResourceLocation id -> Chemicals.ingredient(id);
            default -> null;
        };
        if (ingredient == null) {
            return null;
        }
        int col = (int) Math.floor((mouseX - left() - GRID_X + 1) / CELL);
        int row = (int) Math.floor((mouseY - top() - GRID_Y + 1) / CELL);
        return new StorageListScreen.Hovered(ingredient, left() + GRID_X + col * CELL, top() + GRID_Y + row * CELL);
    }

    boolean inGrid(double mouseX, double mouseY) {
        return mouseX >= left() + GRID_X - 1 && mouseX < left() + GRID_X - 1 + screen.cols() * CELL
                && mouseY >= top() + GRID_Y - 1 && mouseY < top() + GRID_Y - 1 + screen.rows() * CELL;
    }

    private boolean onBar(double mouseX, double mouseY) {
        int barX = screen.barX();
        return mouseX >= left() + barX && mouseX < left() + barX + 4
                && mouseY >= top() + GRID_Y - 1 && mouseY < top() + GRID_Y - 1 + screen.rows() * CELL;
    }

    // ------------------------------------------------------------------ entrada

    /** Clique na grade (pôr, tirar, Shift + clique e duplo clique) ou na barra de rolagem. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inGrid(mouseX, mouseY) && (button == 0 || button == 1)) {
            screen.setFocused(null);
            clickCell(keyAt(mouseX, mouseY), button);
            return true;
        }
        if (button == 0 && onBar(mouseX, mouseY) && maxScroll() > 0) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        return false;
    }

    private void clickCell(@Nullable Object key, int button) {
        boolean items = screen.items();
        // Com um item no cursor, Shift + clique num tipo do Baú manda uma pilha dele para o
        // inventário (o cursor fica como está, como no vanilla), e Shift + duplo clique, o máximo
        // do tipo que couber. Sem item no cursor, o duplo clique não faz nada a mais, para não
        // encher o inventário sem querer.
        if (items && button == 0 && key instanceof ItemStack stack && Screen.hasShiftDown()
                && !menu.getCarried().isEmpty()) {
            long now = Util.getMillis();
            boolean doubleClick = now - lastClickTime < DOUBLE_CLICK_MS
                    && ItemStack.isSameItemSameComponents(lastClickKey, stack);
            lastClickKey = doubleClick ? ItemStack.EMPTY : stack;
            lastClickTime = now;
            screen.send(doubleClick ? StorageActionPayload.Action.TAKE_ALL_TO_INVENTORY
                    : StorageActionPayload.Action.TAKE_TO_INVENTORY, stack);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            // Tanques: clique num tipo enche o recipiente com ele (ou o esvazia, se cheio).
            if (!items && button == 0 && key != null) {
                screen.send(StorageActionPayload.Action.TAKE_STACK, key);
            } else {
                screen.send(button == 0 ? StorageActionPayload.Action.INSERT_CARRIED
                        : StorageActionPayload.Action.INSERT_CARRIED_ONE, null);
            }
            return;
        }
        if (key != null && items) {
            StorageActionPayload.Action action = button == 1 ? StorageActionPayload.Action.TAKE_HALF
                    : Screen.hasShiftDown() ? StorageActionPayload.Action.TAKE_TO_INVENTORY
                    : StorageActionPayload.Action.TAKE_STACK;
            screen.send(action, key);
            if (action == StorageActionPayload.Action.TAKE_TO_INVENTORY) {
                shiftDragTaken = new ArrayList<>(List.of((ItemStack) key));
            }
        } else if (items && button == 0 && Screen.hasShiftDown()) {
            shiftDragTaken = new ArrayList<>();
        }
    }

    /** A barra arrastada, ou o Shift + arrasto pela grade do Baú. */
    boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        if (shiftDragTaken != null && button == 0) {
            if (Screen.hasShiftDown() && menu.getCarried().isEmpty() && inGrid(mouseX, mouseY)
                    && keyAt(mouseX, mouseY) instanceof ItemStack key
                    && shiftDragTaken.stream().noneMatch(taken -> ItemStack.isSameItemSameComponents(taken, key))) {
                shiftDragTaken.add(key);
                screen.send(StorageActionPayload.Action.TAKE_TO_INVENTORY, key);
            }
            return true;
        }
        return false;
    }

    boolean mouseReleased(int button) {
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        if (shiftDragTaken != null && button == 0) {
            shiftDragTaken = null;
            return true;
        }
        return false;
    }

    private void dragBar(double mouseY) {
        double t = (mouseY - top() - GRID_Y) / (screen.rows() * CELL - 2);
        scrollRow = (int) Math.round(Math.max(0, Math.min(1, t)) * maxScroll());
    }

    /**
     * Rodinha do Baú, no sentido do Mouse Tweaks: para baixo empurra um item para o outro lado
     * (da grade para o inventário, do slot para o Baú), para cima puxa um de volta. Sobre uma
     * célula vazia ou a barra, a rodinha rola a lista.
     */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollY, @Nullable Slot hoveredSlot) {
        if (screen.items() && scrollY != 0) {
            boolean push = scrollY < 0;
            if (inGrid(mouseX, mouseY) && keyAt(mouseX, mouseY) instanceof ItemStack key) {
                screen.send(push ? StorageActionPayload.Action.TAKE_ONE_TO_INVENTORY
                        : StorageActionPayload.Action.INSERT_ONE_FROM_INVENTORY, key);
                return true;
            }
            if (hoveredSlot != null && hoveredSlot.hasItem()) {
                screen.send(push ? StorageActionPayload.Action.INSERT_ONE_FROM_SLOT
                        : StorageActionPayload.Action.TAKE_ONE_TO_SLOT, null, hoveredSlot.index);
                return true;
            }
        }
        if ((inGrid(mouseX, mouseY) || onBar(mouseX, mouseY)) && scrollY != 0) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ desenho

    /** As células, os tipos com a quantidade, o destaque sob o mouse, o aviso de vazia e a barra. */
    void render(GuiGraphics g, int x, int y, int mouseX, int mouseY, int trim) {
        Font font = Minecraft.getInstance().font;
        int cols = screen.cols();
        int rows = screen.rows();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                GuiPaint.slot(g, x + GRID_X - 1 + col * CELL, y + GRID_Y - 1 + row * CELL);
            }
        }
        int first = scrollRow * cols;
        for (int i = 0; i < rows * cols && first + i < list.size(); i++) {
            Object key = list.get(first + i);
            int cx = x + GRID_X + (i % cols) * CELL;
            int cy = y + GRID_Y + (i / cols) * CELL;
            StorageKeys.renderKey(g, font, key, cx, cy, trim);
            long count = list.count(key);
            if (count > 0) {
                drawCount(g, font, count, cx, cy);
            } else {
                // acabou com a ordem travada pelo Shift: a célula fica apagada até soltar
                g.fill(RenderType.guiOverlay(), cx, cy, cx + 16, cy + 16, GuiPaint.alpha(GuiPaint.INSET, 0xC0));
            }
        }
        Object hovered = keyAt(mouseX, mouseY);
        if (hovered != null || inGrid(mouseX, mouseY) && !menu.getCarried().isEmpty()) {
            int col = (int) Math.floor((mouseX - x - GRID_X + 1) / (double) CELL);
            int row = (int) Math.floor((mouseY - y - GRID_Y + 1) / (double) CELL);
            int hx = x + GRID_X + col * CELL;
            int hy = y + GRID_Y + row * CELL;
            g.fill(hx, hy, hx + 16, hy + 16, GuiPaint.alpha(GuiPaint.FG, 0x28));
        }
        if (list.isEmpty()) {
            // Quebrado em linhas dentro da grade: na largura mínima a frase não cabe numa só.
            Component empty = menu.view().types() > 0 ? StorageListScreen.tr("no_match")
                    : StorageListScreen.tr(screen.items() ? "empty" : "empty.tank");
            List<FormattedCharSequence> lines = font.split(empty, cols * CELL - 8);
            int lineY = y + GRID_Y + (rows * CELL - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                GuiPaint.text(g, font, line, x + GRID_X + (cols * CELL - font.width(line)) / 2, lineY, GuiPaint.MUTED);
                lineY += 10;
            }
        }
        renderBar(g, x, y, mouseX, mouseY);
    }

    private void renderBar(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int rows = screen.rows();
        int barX = screen.barX();
        int barTop = y + GRID_Y - 1;
        int barH = rows * CELL;
        g.fill(x + barX, barTop, x + barX + 4, barTop + barH, GuiPaint.INSET);
        int max = maxScroll();
        if (max > 0) {
            int total = max + rows;
            int thumbH = Math.max(8, barH * rows / total);
            int thumbY = barTop + (barH - thumbH) * scrollRow / max;
            g.fill(x + barX, thumbY, x + barX + 4, thumbY + thumbH,
                    draggingBar || onBar(mouseX, mouseY) ? GuiPaint.ACCENT : GuiPaint.BEVEL_DARK);
        }
    }

    /** Quantidade abreviada no canto da célula, em meia escala, por cima do tipo (mB nos tanques). */
    private void drawCount(GuiGraphics g, Font font, long count, int cx, int cy) {
        if (count <= 1 && screen.items()) {
            return;
        }
        String text = RateFormat.abbreviate(count);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5f, 0.5f, 1f);
        int tx = (cx + 16) * 2 - font.width(text) - 1;
        int ty = (cy + 16) * 2 - 8;
        g.drawString(font, text, tx, ty, COUNT_TEXT, true);
        g.pose().popPose();
    }
}
