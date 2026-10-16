package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.H;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.W;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.X0;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * A tela do Núcleo de Ressonância, 352 × 244 de tamanho fixo: à esquerda o sinal (osciloscópio, ponteiro, lâmpada,
 * dial e modo), à direita os cristais, a redstone, a saída e a estrutura, e embaixo o inventário. O cabeçalho é o da
 * Bateria. Esta classe só junta as partes: cada uma desenha, dá a dica e trata o clique da sua área, e o que vai ao
 * servidor sai pelo {@code ResonatorScreenActions}.
 */
public class ResonatorScreen extends AbstractContainerScreen<ResonatorMenu> {
    private final ScopeView scope;
    private final TunePointer pointer;
    private final DialRow dial;
    private final CrystalSlots crystals;
    private final OutputPanel output;
    private final StructureLines structure;

    public ResonatorScreen(ResonatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.scope = new ScopeView(menu);
        this.pointer = new TunePointer(menu);
        this.dial = new DialRow(menu, new ResonatorScreenActions(menu));
        this.crystals = new CrystalSlots(menu);
        this.output = new OutputPanel(menu);
        this.structure = new StructureLines(menu);
    }

    @Override
    protected void init() {
        super.init();
        scope.layout(leftPos, topPos);
        pointer.layout(leftPos, topPos);
        dial.layout(leftPos, topPos);
        crystals.layout(leftPos, topPos);
        output.layout(leftPos, topPos);
        structure.layout(leftPos, topPos);
    }

    public int themeX() {
        return leftPos + W - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + HEAD_Y + 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        List<Component> tip = partTooltip(mouseX, mouseY);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            renderTooltip(g, mouseX, mouseY);
            return;
        }
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null && menu.getCarried().isEmpty()) {
            g.renderComponentTooltip(font, List.of(clipped), mouseX, mouseY);
        }
    }

    private @Nullable List<Component> partTooltip(int mouseX, int mouseY) {
        List<Component> tip = crystals.tooltip(mouseX, mouseY);
        if (tip == null) {
            tip = dial.tooltip(mouseX, mouseY);
        }
        if (tip == null) {
            tip = output.tooltip(mouseX, mouseY);
        }
        return tip;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        RouterTier tier = menu.tier();
        int trim = GuiPaint.tierColor(tier);
        GuiPaint.panel(g, x, y, W, H, trim);

        // cabeçalho, como o da Bateria: nome, pílula do tier e o botão do tema
        Component tierName = Component.translatable(tier.translationKey());
        int pillW = font.width(tierName) + 8;
        int headEnd = W - 4 - ThemeToggle.SIZE - 4;
        int titleW = Math.min(font.width(title), headEnd - X0 - pillW - 6);
        GuiText.draw(g, font, title, x + X0, y + HEAD_Y + 3, titleW, GuiPaint.FG);
        int pillX = x + X0 + titleW + 6;
        GuiPaint.pill(g, pillX, y + HEAD_Y + 1, pillW, 11, GuiPaint.PANEL, trim);
        GuiPaint.text(g, font, tierName, pillX + 4, y + HEAD_Y + 3, GuiPaint.FG);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);

        scope.render(g, font, mouseX, mouseY, partialTick);
        pointer.render(g, font, mouseX, mouseY, partialTick);
        dial.render(g, font, mouseX, mouseY, partialTick);
        crystals.render(g, font, mouseX, mouseY, partialTick);
        output.render(g, font, mouseX, mouseY, partialTick);
        structure.render(g, font, mouseX, mouseY, partialTick);
        for (int i = menu.inventoryStart(); i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            GuiPaint.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        super.renderSlot(g, slot);
        crystals.renderBar(g, slot);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (dial.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dial.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** O centro do botão › (+0,05 MHz); gancho do e2e. */
    public int[] dialPlusCenter() {
        return dial.buttonCenter(2);
    }

    /** O centro do botão ‹ (−0,05 MHz); gancho do e2e. */
    public int[] dialMinusCenter() {
        return dial.buttonCenter(1);
    }

    public int[] modeAutoCenter() {
        return dial.autoCenter();
    }

    public int[] modeManualCenter() {
        return dial.manualCenter();
    }

    public int[] fieldCenter() {
        return dial.fieldCenter();
    }

    public int[] themeCenter() {
        return new int[] {themeX() + ThemeToggle.SIZE / 2, themeY() + ThemeToggle.SIZE / 2};
    }

    /** O centro de um slot do Núcleo (câmara ou redstone); gancho do e2e. */
    public int[] coreSlotCenter(int index) {
        return crystals.slotCenter(index);
    }
}
