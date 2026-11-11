package io.github.matheusanbs.wirelessautomate.client.dock;

import static io.github.matheusanbs.wirelessautomate.client.dock.DockLayout.*;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiText.Fitted;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.DockMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.packet.DockStatePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * A tela da Doca de Padrões, sem slots e sem opção nenhuma (mockup {@code docs/preview/doca-de-padroes-tela.html}): o
 * cabeçalho com a faixa e o Olho na cor do tier, o título, o tier e o botão de tema; o resumo "30/32 máquinas" com a
 * barra (âmbar no máximo) e o raio; e as seis linhas do {@code DockRows}, com a dica de cada uma. O estado vem do
 * servidor pelo {@link DockMenu}, só com mudança; os textos são medidos quando ele chega.
 */
public class DockScreen extends AbstractContainerScreen<DockMenu> {
    private static final RouterTier[] TIERS = RouterTier.values();
    private final DockRows rows = new DockRows();
    private @Nullable Fitted titleText;
    private @Nullable DockStatePayload summaryOf;
    private @Nullable Fitted totalText;
    private @Nullable Fitted machinesText;
    private @Nullable Fitted radiusText;
    private int totalWidth;
    private int machinesWidth;
    private int radiusWidth;
    private @Nullable RouterTier shownTier;
    private @Nullable Component tierName;
    private int titleWidth;
    private int pillWidth;

    public DockScreen(DockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        shownTier = null;
        summaryOf = null;
        rows.invalidate();
    }

    public int themeX() {
        return leftPos + THEME_X;
    }

    public int themeY() {
        return topPos + THEME_Y;
    }

    /** O centro da linha {@code index} (0 a 5, na ordem da tela), para o e2e pôr o mouse e abrir a dica. */
    public int[] rowCenter(int index) {
        return new int[] {leftPos + ROWS_X + ROWS_W / 2, topPos + rowTop(index) + ROW_H / 2};
    }

    private RouterTier tier() {
        return minecraft != null && minecraft.level != null
                ? TIERS[StorageListMenu.tierOrdinal(minecraft.level, menu.pos())]
                : RouterTier.BASIC;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null) {
            g.renderComponentTooltip(font, List.of(clipped), mouseX, mouseY);
            return;
        }
        List<FormattedCharSequence> tip = rows.tipAt(leftPos, topPos, mouseX, mouseY);
        if (tip != null) {
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        RouterTier tier = tier();
        int trim = GuiPaint.tierColor(tier);
        GuiPaint.panel(g, x, y, W, H, trim);
        header(g, tier, trim, x, y);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        DockStatePayload state = menu.state();
        summary(g, state, trim, x, y);
        rows.update(state, font);
        rows.render(g, font, x, y, mouseX, mouseY);
    }

    /** O Olho, o título e a pílula do tier; o título encolhe com reticências se a pílula não couber. */
    private void header(GuiGraphics g, RouterTier tier, int trim, int x, int y) {
        if (tier != shownTier) {
            shownTier = tier;
            tierName = Component.translatable(tier.translationKey());
            pillWidth = font.width(tierName) + 8;
            int room = THEME_X - 6 - TITLE_X - pillWidth - 6;
            titleText = GuiText.fit(font, title, room);
            titleWidth = Math.min(room, font.width(titleText.shown()));
        }
        GuiPaint.eye(g, x + EYE_X, y + HEAD_TEXT_Y + 1, trim);
        GuiText.draw(g, font, titleText, x + TITLE_X, y + HEAD_TEXT_Y, GuiPaint.FG);
        int pillX = x + TITLE_X + titleWidth + 6;
        GuiPaint.pill(g, pillX, y + PILL_Y, pillWidth, PILL_H, GuiPaint.BEVEL_LIGHT, trim);
        GuiPaint.text(g, font, tierName, pillX + 4, y + PILL_Y + 2, GuiPaint.FG);
    }

    /** "30/32 máquinas", a barra fina (na cor do tier, âmbar no máximo) e "raio 16" à direita. */
    private void summary(GuiGraphics g, @Nullable DockStatePayload state, int trim, int x, int y) {
        if (state == null) {
            return;
        }
        if (state != summaryOf) {
            summaryOf = state;
            totalText = GuiText.fit(font, Component.literal(state.total() + "/" + state.max()), ROWS_W / 4);
            totalWidth = Math.min(totalText.width(), font.width(totalText.shown()));
            machinesText = GuiText.fit(font, DockRows.key("machines"), ROWS_W / 4);
            machinesWidth = Math.min(machinesText.width(), font.width(machinesText.shown()));
            radiusText = GuiText.fit(font, DockRows.key("radius", state.radius()), ROWS_W / 4);
            radiusWidth = Math.min(radiusText.width(), font.width(radiusText.shown()));
        }
        int textY = y + SUMMARY_Y;
        GuiText.draw(g, font, totalText, x + X0, textY, GuiPaint.FG);
        int labelX = x + X0 + totalWidth + 4;
        GuiText.draw(g, font, machinesText, labelX, textY, GuiPaint.MUTED);
        int barX = labelX + machinesWidth + SUMMARY_GAP;
        int barW = barWidth(totalWidth + 4 + machinesWidth, radiusWidth);
        int barY = textY + BAR_DY;
        GuiPaint.inset(g, barX, barY, barW, BAR_H);
        int filled = barFill(barW, state.total(), state.max());
        if (filled > 0) {
            g.fill(barX, barY, barX + filled, barY + BAR_H, state.total() >= state.max() ? GuiPaint.WARN : trim);
        }
        GuiText.draw(g, font, radiusText, x + W - X0 - radiusWidth, textY, GuiPaint.MUTED);
    }
}
