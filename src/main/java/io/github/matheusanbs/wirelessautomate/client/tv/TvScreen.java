package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.*;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.client.tv.TvButtons.Label;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * A tela da Tela de Sinal, 320 × 220 de tamanho fixo: o cabeçalho com o nome, as abas Tamanho e Conteúdo e o botão do
 * tema. Esta classe só junta as partes ({@link TvSizeTab}, {@link TvContentTab}) e repassa o desenho, o mouse e as
 * dicas à aba aberta; os ganchos do e2e ficam aqui. Sem slots: só existe aberta, e nada vem do servidor enquanto isso.
 */
public class TvScreen extends AbstractContainerScreen<TvMenu> {
    /** As abas da tela. */
    public enum Tab { SIZE, CONTENT }

    private final TvSizeTab sizeTab;
    private final TvContentTab contentTab;
    private Tab tab = Tab.SIZE;
    private @Nullable GuiText.Fitted titleText;
    private @Nullable Label sizeLabel;
    private @Nullable Label contentLabel;

    public TvScreen(TvMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        TvScreenActions actions = new TvScreenActions(menu.containerId, false);
        this.sizeTab = new TvSizeTab(menu, actions);
        this.contentTab = new TvContentTab(menu, actions);
    }

    @Override
    protected void init() {
        super.init();
        titleText = GuiText.fit(font, title, TITLE.w());
        sizeLabel = TvButtons.label(font, TvScreenText.key("tab.size"), SIZE_TAB.w() - 6);
        contentLabel = TvButtons.label(font, TvScreenText.key("tab.content"), CONTENT_TAB.w() - 6);
        sizeTab.layout(font);
        contentTab.layout(font);
    }

    public int themeX() {
        return leftPos + THEME_X;
    }

    public int themeY() {
        return topPos + THEME_Y;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        contentTab.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        List<Component> tip = tab == Tab.SIZE ? sizeTab.tooltip(leftPos, topPos, mouseX, mouseY) : null;
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
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
        GuiPaint.panel(g, leftPos, topPos, W, H, GuiPaint.ACCENT);
        GuiText.draw(g, font, titleText, leftPos + TITLE.x(), topPos + TITLE.y(), GuiPaint.FG);
        renderTab(g, SIZE_TAB, sizeLabel, tab == Tab.SIZE, mouseX, mouseY);
        renderTab(g, CONTENT_TAB, contentLabel, tab == Tab.CONTENT, mouseX, mouseY);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        g.fill(leftPos + 8, topPos + RULE_Y, leftPos + W - 8, topPos + RULE_Y + 1, GuiPaint.LINE);
        if (tab == Tab.SIZE) {
            sizeTab.render(g, leftPos, topPos, mouseX, mouseY);
        } else {
            contentTab.render(g, leftPos, topPos, mouseX, mouseY);
        }
    }

    private void renderTab(GuiGraphics g, Rect r, Label label, boolean active, int mouseX, int mouseY) {
        boolean hovered = r.contains(leftPos, topPos, mouseX, mouseY);
        int x = leftPos + r.x();
        int y = topPos + r.y();
        GuiPaint.tab(g, x, y, r.w(), r.h(), active, hovered);
        GuiText.draw(g, font, label.text(), x + (r.w() - label.width()) / 2, y + 3, GuiPaint.tabText(active, hovered));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            if (SIZE_TAB.contains(leftPos, topPos, mouseX, mouseY)) {
                select(Tab.SIZE);
                return true;
            }
            if (CONTENT_TAB.contains(leftPos, topPos, mouseX, mouseY)) {
                select(Tab.CONTENT);
                return true;
            }
            boolean used = tab == Tab.SIZE ? sizeTab.mouseClicked(leftPos, topPos, mouseX, mouseY)
                    : contentTab.mouseClicked(leftPos, topPos, mouseX, mouseY);
            if (used) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.CONTENT && contentTab.mouseScrolled(leftPos, topPos, mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Na aba Conteúdo, as letras vão para a busca das redes. */
    @Override
    public boolean charTyped(char c, int modifiers) {
        if (tab == Tab.CONTENT && contentTab.charTyped(c)) {
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    /** Na aba Conteúdo, as teclas vão para a busca; o Esc com a busca vazia fecha a tela. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.CONTENT && contentTab.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(Tab chosen) {
        if (tab == Tab.CONTENT && chosen != Tab.CONTENT) {
            contentTab.leave();
        }
        tab = chosen;
    }

    public Tab currentTab() {
        return tab;
    }

    /** O centro do botão de uma aba (gancho do e2e). */
    public int[] tabCenter(Tab wanted) {
        Rect r = wanted == Tab.SIZE ? SIZE_TAB : CONTENT_TAB;
        return new int[] {r.centerX(leftPos), r.centerY(topPos)};
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

    public int @Nullable [] networkCenter(UUID id) {
        return contentTab.networkCenter(leftPos, topPos, id);
    }

    public int @Nullable [] typeCenter(ResourceType type) {
        return contentTab.typeCenter(leftPos, topPos, type);
    }

    public int[] templateCenter() {
        return new int[] {TEMPLATE.centerX(leftPos), TEMPLATE.centerY(topPos)};
    }

    public int[] clearCenter() {
        return new int[] {CLEAR.centerX(leftPos), CLEAR.centerY(topPos)};
    }

    public @Nullable UUID selectedNetwork() {
        return contentTab.list().selected();
    }

    /** O texto da busca das redes e quantas ela deixa (ganchos do e2e). */
    public String networkQuery() {
        return contentTab.list().query();
    }

    public int networksShown() {
        return contentTab.list().shownCount();
    }

    public ResourceType selectedType() {
        return contentTab.type();
    }

    public @Nullable UUID currentNetwork() {
        return contentTab.current();
    }
}
