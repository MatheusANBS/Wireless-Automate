package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.*;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.tv.TvButtons.Label;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorActions;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A tela da Tela de Sinal: o cabeçalho com o nome, as abas Tamanho e Editar, o ✕ e o botão do tema. A aba Tamanho
 * tem 320 × 220 fixos; a Editar ocupa a janela menos {@link #EDITOR_MARGIN} px e acompanha o tamanho dela. Esta
 * classe só junta as partes ({@link TvSizeTab}, {@link TvEditorTab}) e repassa o desenho, o mouse, a roda, o teclado
 * e as dicas à aba aberta; os ganchos do e2e ficam aqui. Fechar com mudança não salva no editor pede dois cliques no ✕
 * ("Descartar?"); o Esc faz o mesmo. Sem slots: só existe aberta, e nada vem do servidor enquanto isso.
 */
public class TvScreen extends AbstractContainerScreen<TvMenu> {
    /** As abas da tela. */
    public enum Tab { SIZE, EDIT }

    /** A aba Editar ocupa a janela menos esta margem (metade de cada lado). */
    public static final int EDITOR_MARGIN = 8;
    private static final Component CLOSE_GLYPH = Component.literal("✕");

    private final TvSizeTab sizeTab;
    private final TvEditorTab editorTab;
    private final ArmedClick closeClick = new ArmedClick();
    private Tab tab = Tab.SIZE;
    private @Nullable GuiText.Fitted titleText;
    private @Nullable Label sizeLabel;
    private @Nullable Label editLabel;
    private @Nullable Label discardLabel;

    public TvScreen(TvMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.sizeTab = new TvSizeTab(menu, new TvScreenActions(menu.containerId, false));
        this.editorTab = new TvEditorTab(menu.pos(), menu.area(), new TvEditorActions(menu.containerId, false));
    }

    @Override
    protected void init() {
        size();
        super.init();
        titleText = GuiText.fit(font, title, TITLE.w());
        sizeLabel = TvButtons.label(font, TvScreenText.key("tab.size"), SIZE_TAB.w() - 6);
        editLabel = TvButtons.label(font, TvScreenText.key("tab.edit"), EDIT_TAB.w() - 6);
        discardLabel = TvButtons.label(font, TvScreenText.key("discard"), DISCARD_W - 6);
        sizeTab.layout(font);
        editorTab.layout(font, leftPos, topPos, imageWidth, imageHeight);
    }

    /** O tamanho do painel pela aba aberta; o {@code super.init} centra. */
    private void size() {
        if (tab == Tab.EDIT) {
            imageWidth = Math.max(1, width - EDITOR_MARGIN);
            imageHeight = Math.max(1, height - EDITOR_MARGIN);
        } else {
            imageWidth = W;
            imageHeight = H;
        }
    }

    public int themeX() {
        return leftPos + imageWidth - 8 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + THEME_Y;
    }

    private int closeX() {
        return themeX() - 4 - ThemeToggle.SIZE;
    }

    private boolean closeArmed() {
        return closeClick.armed(System.currentTimeMillis());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        editorTab.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        if (tab == Tab.EDIT && closeHovered(mouseX, mouseY) && !closeArmed()) {
            g.renderComponentTooltip(font, List.of(TvScreenText.key("close")), mouseX, mouseY);
            return;
        }
        List<Component> tip = tab == Tab.SIZE ? sizeTab.tooltip(leftPos, topPos, mouseX, mouseY) : null;
        Component editorTip = tab == Tab.EDIT ? editorTab.tooltip(mouseX, mouseY) : null;
        if (tip != null || editorTip != null) {
            g.renderComponentTooltip(font, tip != null ? tip : List.of(editorTip), mouseX, mouseY);
            return;
        }
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null) {
            g.renderComponentTooltip(font, List.of(clipped), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GuiPaint.panel(g, leftPos, topPos, imageWidth, imageHeight, GuiPaint.ACCENT);
        GuiText.draw(g, font, titleText, leftPos + TITLE.x(), topPos + TITLE.y(), GuiPaint.FG);
        renderTab(g, SIZE_TAB, sizeLabel, tab == Tab.SIZE, mouseX, mouseY);
        renderTab(g, EDIT_TAB, editLabel, tab == Tab.EDIT, mouseX, mouseY);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        if (tab == Tab.EDIT) {
            renderClose(g, mouseX, mouseY);
        }
        g.fill(leftPos + 8, topPos + RULE_Y, leftPos + imageWidth - 8, topPos + RULE_Y + 1, GuiPaint.LINE);
        if (tab == Tab.SIZE) {
            sizeTab.render(g, leftPos, topPos, mouseX, mouseY);
        } else {
            editorTab.render(g, mouseX, mouseY);
        }
    }

    private void renderTab(GuiGraphics g, Rect r, Label label, boolean active, int mouseX, int mouseY) {
        boolean hovered = r.contains(leftPos, topPos, mouseX, mouseY);
        int x = leftPos + r.x();
        int y = topPos + r.y();
        GuiPaint.tab(g, x, y, r.w(), r.h(), active, hovered);
        GuiText.draw(g, font, label.text(), x + (r.w() - label.width()) / 2, y + 3, GuiPaint.tabText(active, hovered));
    }

    /** O ✕; armado, cresce para a esquerda com o "Descartar?" em vermelho. */
    private void renderClose(GuiGraphics g, int mouseX, int mouseY) {
        int x = closeX();
        int y = themeY();
        int size = ThemeToggle.SIZE;
        if (closeArmed()) {
            int left = x - DISCARD_W;
            GuiPaint.box(g, left, y, DISCARD_W + size, size, GuiPaint.DANGER, GuiPaint.mix(GuiPaint.DANGER,
                    GuiPaint.RING, 0.3f));
            GuiText.draw(g, font, discardLabel.text(), left + 3, y + 2, GuiPaint.DARK_TEXT);
            GuiPaint.text(g, font, CLOSE_GLYPH, x + 3, y + 2, GuiPaint.DARK_TEXT);
            return;
        }
        GuiPaint.box(g, x, y, size, size, GuiPaint.BUTTON,
                closeHovered(mouseX, mouseY) ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiPaint.text(g, font, CLOSE_GLYPH, x + 3, y + 2, GuiPaint.FG);
    }

    private boolean closeHovered(double mouseX, double mouseY) {
        int left = closeArmed() ? closeX() - DISCARD_W : closeX();
        return mouseX >= left && mouseX < closeX() + ThemeToggle.SIZE && mouseY >= themeY()
                && mouseY < themeY() + ThemeToggle.SIZE;
    }

    /** O ✕ e o Esc: sem mudança fecha; com mudança, o primeiro arma o "Descartar?" e o segundo fecha. */
    private void requestClose() {
        if (!editorTab.dirty() || closeClick.click(System.currentTimeMillis())) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            if (tab == Tab.EDIT && closeHovered(mouseX, mouseY)) {
                requestClose();
                return true;
            }
            closeClick.disarm();
            if (SIZE_TAB.contains(leftPos, topPos, mouseX, mouseY)) {
                select(Tab.SIZE);
                return true;
            }
            if (EDIT_TAB.contains(leftPos, topPos, mouseX, mouseY)) {
                select(Tab.EDIT);
                return true;
            }
            boolean used = tab == Tab.SIZE ? sizeTab.mouseClicked(leftPos, topPos, mouseX, mouseY)
                    : editorTab.mouseClicked(mouseX, mouseY);
            if (used) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (tab == Tab.EDIT && button == 0 && editorTab.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && editorTab.mouseReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.EDIT && editorTab.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * O Esc solta a escolha do editor; sem escolha, fecha (com mudança, pelo "Descartar?" do ✕, que aparece na aba
     * Editar). Na aba Editar, a tecla do inventário não fecha a tela.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (tab == Tab.EDIT && editorTab.escape()) {
                return true;
            }
            if (editorTab.dirty()) {
                select(Tab.EDIT);
                requestClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (tab == Tab.EDIT && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        editorTab.release();
    }

    private void select(Tab chosen) {
        if (chosen == tab) {
            return;
        }
        tab = chosen;
        if (minecraft != null) {
            init(minecraft, width, height);
        }
    }

    public Tab currentTab() {
        return tab;
    }

    /** O centro do botão de uma aba (gancho do e2e). */
    public int[] tabCenter(Tab wanted) {
        Rect r = wanted == Tab.SIZE ? SIZE_TAB : EDIT_TAB;
        return new int[] {r.centerX(leftPos), r.centerY(topPos)};
    }

    /** O centro do ✕ (gancho do e2e). */
    public int[] closeCenter() {
        return new int[] {closeX() + ThemeToggle.SIZE / 2, themeY() + ThemeToggle.SIZE / 2};
    }

    public TvEditorTab editor() {
        return editorTab;
    }

    /** O centro do ‹ ({@code plus} falso) ou do › da Largura ou da Altura (gancho do e2e). */
    public int[] stepCenter(boolean widthRow, boolean plus) {
        return plus ? sizeTab.plusCenter(leftPos, topPos, widthRow) : sizeTab.minusCenter(leftPos, topPos, widthRow);
    }

    public int[] growCenter(TvArea.Grow grow) {
        Rect r = GROW_BUTTONS[grow.ordinal()];
        return new int[] {r.centerX(leftPos), r.centerY(topPos)};
    }

    public int[] applyCenter() {
        return new int[] {APPLY.centerX(leftPos), APPLY.centerY(topPos)};
    }

    public TvArea requested() {
        return sizeTab.requested();
    }

    public @Nullable TvArea.Grow requestedGrow() {
        return sizeTab.grow();
    }

    public TvAreaCheck.Problem problem() {
        return sizeTab.problem();
    }

    /** Pede um tamanho pelo mesmo caminho dos ‹ › (gancho do e2e). */
    public void requestSize(int width, int height) {
        sizeTab.requestForTests(width, height);
    }

    /** Valida de novo o tamanho pedido depois de mudar o mundo (gancho do e2e). */
    public void recheck() {
        sizeTab.recheckForTests();
    }
}
