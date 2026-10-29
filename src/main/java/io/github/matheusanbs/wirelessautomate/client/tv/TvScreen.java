package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.*;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.tv.TvButtons.Label;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorActions;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorLayout;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorSize;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvPixelEditor;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A tela da Tela de Sinal: o cabeçalho com o nome, as abas Tamanho, Editar e Acesso (esta só para o dono e os
 * operadores), o ✕ e o botão do tema. As abas Tamanho e Acesso têm 320 × 220 fixos; a Editar é redimensionável pela
 * borda e pela alça do canto como a tela do roteador (mínimo 400 × 240, máximo a janela menos {@link #EDITOR_MARGIN}
 * px, o tamanho lembrado na sessão pelo {@link TvEditorSize}). Esta classe só junta as partes ({@link TvScreenTabs},
 * {@link TvSizeTab}, {@link TvEditorTab}, {@link TvAccessTab}) e repassa o desenho, o mouse, a roda, o teclado e as
 * dicas à aba aberta; os ganchos do e2e ficam aqui. Fechar com mudança não salva no editor pede dois cliques no ✕
 * ("Descartar?"); o Esc faz o mesmo. Com o editor de pixel art aberto ({@link TvPixelEditor}), ele ocupa o painel
 * inteiro no lugar do título, das abas e do ✕ e recebe o mouse e o teclado (o Esc e a tecla do inventário não fecham a
 * tela); o tema e o redimensionar continuam. Sem slots: só existe aberta, e do servidor só vem a resposta ao que ela
 * pede.
 */
public class TvScreen extends AbstractContainerScreen<TvMenu> {
    /** As abas da tela. */
    public enum Tab { SIZE, EDIT, ACCESS }

    static final Tab[] TABS = Tab.values();

    /** A aba Editar cresce no máximo até a janela menos esta margem (metade de cada lado). */
    public static final int EDITOR_MARGIN = 8;
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static final Component CLOSE_GLYPH = Component.literal("✕");
    private static final List<Component> RESIZE_TIP = List.of(
            Component.translatable("gui.wirelessautomate.resize.tooltip"));

    private final TvScreenTabs tabs;
    private final TvSizeTab sizeTab;
    private final TvEditorTab editorTab;
    /** Só para quem gerencia a Tela ({@link TvMenu#manage}). */
    private final @Nullable TvAccessTab accessTab;
    private final ArmedClick closeClick = new ArmedClick();
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);
    private Tab tab = Tab.SIZE;
    private @Nullable Label discardLabel;

    public TvScreen(TvMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.tabs = new TvScreenTabs(menu.manage());
        TvScreenActions actions = new TvScreenActions(menu.containerId, false);
        this.sizeTab = new TvSizeTab(menu, actions);
        this.accessTab = menu.manage() ? new TvAccessTab(menu, actions) : null;
        this.editorTab = new TvEditorTab(menu.pos(), menu.area(), menu.networks(), menu.links(),
                new TvEditorActions(menu.containerId, false, menu.maxLayoutBytes(), menu.maxArts(),
                        menu.maxImagePixels(), menu.imageRules()));
    }

    @Override
    protected void init() {
        size();
        super.init();
        tabs.layout(font, title);
        discardLabel = TvButtons.label(font, TvScreenText.key("discard"), DISCARD_W - 6);
        sizeTab.layout(font);
        editorTab.layout(font, leftPos, topPos, imageWidth, imageHeight);
        if (accessTab != null) {
            accessTab.layout(font, leftPos, topPos);
            accessTab.createBoxes(font).forEach(this::addRenderableWidget);
            accessTab.refresh(tab == Tab.ACCESS);
        }
    }

    /** O tamanho do painel pela aba aberta; o {@code super.init} centra. */
    private void size() {
        if (tab == Tab.EDIT) {
            imageWidth = TvEditorSize.fit(TvEditorSize.wantedWidth(), TvEditorLayout.MIN_W, width - EDITOR_MARGIN);
            imageHeight = TvEditorSize.fit(TvEditorSize.wantedHeight(), TvEditorLayout.MIN_H, height - EDITOR_MARGIN);
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

    /** O editor de pixel art está aberto por cima da aba Editar. */
    private boolean pixelOpen() {
        return tab == Tab.EDIT && editorTab.pixel().isOpen();
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
        if (tab == Tab.ACCESS && accessTab != null) {
            accessTab.renderOverlay(g, font);
        }
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        if (tab == Tab.EDIT && !pixelOpen() && closeHovered(mouseX, mouseY) && !closeArmed()) {
            g.renderComponentTooltip(font, List.of(TvScreenText.key("close")), mouseX, mouseY);
            return;
        }
        if (tab == Tab.EDIT && !editorTab.probe().menuOpen() && resizeHandle.hover(mouseX, mouseY, leftPos, topPos,
                imageWidth, imageHeight) != null) {
            g.renderComponentTooltip(font, RESIZE_TIP, mouseX, mouseY);
            return;
        }
        List<Component> tip = tab == Tab.SIZE ? sizeTab.tooltip(leftPos, topPos, mouseX, mouseY) : null;
        Component editorTip = pixelOpen() ? editorTab.pixel().tooltip(mouseX, mouseY)
                : tab == Tab.EDIT ? editorTab.tooltip(mouseX, mouseY)
                : tab == Tab.ACCESS && accessTab != null ? accessTab.tooltip(mouseX, mouseY) : null;
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
        if (pixelOpen()) {
            ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
            g.fill(leftPos + 8, topPos + RULE_Y, leftPos + imageWidth - 8, topPos + RULE_Y + 1, GuiPaint.LINE);
            ResizeGrip.renderDotted(g, leftPos, topPos, imageWidth, imageHeight,
                    resizeHandle.hover(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight), GuiPaint.ACCENT);
            editorTab.pixel().render(g, mouseX, mouseY);
            return;
        }
        tabs.render(g, font, leftPos, topPos, tab, mouseX, mouseY);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        if (tab == Tab.EDIT) {
            renderClose(g, mouseX, mouseY);
        }
        g.fill(leftPos + 8, topPos + RULE_Y, leftPos + imageWidth - 8, topPos + RULE_Y + 1, GuiPaint.LINE);
        if (tab == Tab.SIZE) {
            sizeTab.render(g, leftPos, topPos, mouseX, mouseY);
        } else if (tab == Tab.ACCESS && accessTab != null) {
            accessTab.render(g, font, mouseX, mouseY);
        } else {
            ResizeHandle.Edge hover = editorTab.probe().menuOpen() ? null
                    : resizeHandle.hover(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight);
            ResizeGrip.renderDotted(g, leftPos, topPos, imageWidth, imageHeight, hover, GuiPaint.ACCENT);
            editorTab.render(g, mouseX, mouseY);
        }
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
        if (pixelOpen()) {
            if (button != 0 || !resizeHandle.begin(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight)) {
                editorTab.pixel().mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        if (tab == Tab.EDIT && (button == 1 || button == 2)) {
            return editorTab.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0) {
            if (tab == Tab.EDIT && closeHovered(mouseX, mouseY)) {
                requestClose();
                return true;
            }
            closeClick.disarm();
            if (tab == Tab.EDIT && !editorTab.probe().menuOpen()
                    && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight)) {
                return true;
            }
            Tab clicked = tabs.at(leftPos, topPos, mouseX, mouseY);
            if (clicked != null) {
                select(clicked);
                return true;
            }
            boolean used = tab == Tab.SIZE ? sizeTab.mouseClicked(leftPos, topPos, mouseX, mouseY)
                    : tab == Tab.ACCESS ? accessTab != null && accessTab.mouseClicked(mouseX, mouseY)
                    : editorTab.mouseClicked(mouseX, mouseY, button);
            if (used) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizeHandle.dragging()) {
            dragResize(mouseX, mouseY);
            return true;
        }
        if (pixelOpen()) {
            return editorTab.pixel().mouseDragged(mouseX, mouseY);
        }
        if (tab == Tab.EDIT && (button == 0 || button == 2) && editorTab.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Redimensiona em torno do centro, como o roteador: a borda arrastada segue o mouse e a oposta se move igual,
     * então o painel continua centralizado. O tamanho pedido fica lembrado e a tela é refeita no tamanho novo.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newW = imageWidth;
        int newH = imageHeight;
        if (resizeHandle.changesWidth()) {
            newW = TvEditorSize.fit(TvEditorSize.even(resizeHandle.wantedWidth(mouseX)), TvEditorLayout.MIN_W,
                    width - EDITOR_MARGIN);
        }
        if (resizeHandle.changesHeight()) {
            newH = TvEditorSize.fit(TvEditorSize.even(resizeHandle.wantedHeight(mouseY)), TvEditorLayout.MIN_H,
                    height - EDITOR_MARGIN);
        }
        if ((newW != imageWidth || newH != imageHeight) && minecraft != null) {
            TvEditorSize.remember(newW, newH);
            init(minecraft, width, height);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizeHandle.dragging()) {
            resizeHandle.end();
            return true;
        }
        if (pixelOpen()) {
            return editorTab.pixel().mouseReleased(mouseX, mouseY) || super.mouseReleased(mouseX, mouseY, button);
        }
        if ((button == 0 || button == 2) && editorTab.mouseReleased(mouseX, mouseY)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pixelOpen()) {
            return editorTab.pixel().mouseScrolled(mouseX, mouseY, scrollY) || true;
        }
        if (tab == Tab.EDIT && editorTab.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (tab == Tab.ACCESS && accessTab != null) {
            accessTab.mouseScrolled(mouseY, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Na aba Editar, o campo de texto com o foco e os atalhos do editor têm a vez primeiro. O Esc solta a escolha do
     * editor; sem escolha, fecha (com mudança, pelo "Descartar?" do ✕, que aparece na aba Editar). A tecla do
     * inventário não fecha a tela.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pixelOpen()) {
            return editorTab.pixel().keyPressed(keyCode, scanCode, modifiers) || minecraft != null
                    && minecraft.options.keyInventory.matches(keyCode, scanCode)
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (tab == Tab.EDIT && editorTab.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (tab == Tab.ACCESS && accessTab != null && accessTab.keyPressed(getFocused() instanceof EditBox box
                ? box : null, keyCode, scanCode, modifiers, () -> setFocused(null))) {
            return true;
        }
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
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return pixelOpen() && editorTab.pixel().keyReleased(keyCode) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (pixelOpen()) {
            return editorTab.pixel().charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
        }
        if (tab == Tab.EDIT && editorTab.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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
        if (chosen == Tab.EDIT) {
            editorTab.opened();
        }
        if (minecraft != null) {
            init(minecraft, width, height);
        }
    }

    public Tab currentTab() {
        return tab;
    }

    /** O centro do botão de uma aba (gancho do e2e). */
    public int[] tabCenter(Tab wanted) {
        Rect r = TvScreenTabs.rect(wanted);
        return new int[] {r.centerX(leftPos), r.centerY(topPos)};
    }

    /** A lista de acesso da aba Acesso (gancho do e2e); {@code null} para quem não gerencia a Tela. */
    public @Nullable AccessPanel access() {
        return accessTab == null ? null : accessTab.panel();
    }

    /** O centro do ✕ (gancho do e2e). */
    public int[] closeCenter() {
        return new int[] {closeX() + ThemeToggle.SIZE / 2, themeY() + ThemeToggle.SIZE / 2};
    }

    public TvEditorTab editor() {
        return editorTab;
    }

    /** O retângulo onde o JEI pode largar um item, só com a aba Editar aberta; {@code null} sem alvo. */
    public int @Nullable [] ghostBounds() {
        return tab == Tab.EDIT && !pixelOpen() ? editorTab.ghostBounds() : null;
    }

    /** O ponto da alça do canto da aba Editar (gancho do e2e). */
    public int[] gripCenter() {
        return new int[] {leftPos + imageWidth - 3, topPos + imageHeight - 3};
    }

    /** A largura e a altura do painel (gancho do e2e). */
    public int[] panelSize() {
        return new int[] {imageWidth, imageHeight};
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
